package app.anchor.data

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import app.anchor.core.usage.DayUsage
import app.anchor.core.usage.UsageAggregator
import app.anchor.core.usage.UsageEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId

/** FR-01: reads UsageStatsManager.queryEvents and converts to platform-neutral events. */
class UsageRepository(private val context: Context) {
    private val usm = context.getSystemService(UsageStatsManager::class.java)

    fun readEvents(startMs: Long, endMs: Long): List<UsageEvent> {
        val out = ArrayList<UsageEvent>(1024)
        val events = usm?.queryEvents(startMs, endMs) ?: return out
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val type = mapType(e.eventType) ?: continue
            out += UsageEvent(e.timeStamp, e.packageName ?: "", type)
        }
        return out
    }

    /** Per-day usage for the last [days] days including today. */
    suspend fun loadDays(days: Int, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, DayUsage> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val start = LocalDate.now(zone).minusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli()
        UsageAggregator.aggregate(readEvents(start, now), zone, now, ignorePackages = setOf(context.packageName))
    }

    /** Today's foreground time per package (blocking call; used by the guard every few seconds). */
    fun todayUsageMs(zone: ZoneId = ZoneId.systemDefault()): Map<String, Long> {
        val now = System.currentTimeMillis()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val today = LocalDate.now(zone)
        return UsageAggregator.aggregate(readEvents(start, now), zone, now)[today]?.perAppMs.orEmpty()
    }

    /** Fallback foreground detection when the accessibility service is off. */
    fun currentForeground(): String? {
        val now = System.currentTimeMillis()
        val events = usm?.queryEvents(now - 15_000, now) ?: return null
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) last = e.packageName
        }
        return last
    }

    /** Most recent unlock in the last [lookbackMs], from KEYGUARD_HIDDEN (API 28+). */
    fun lastUnlockMs(lookbackMs: Long = 6 * 3_600_000L): Long? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val now = System.currentTimeMillis()
        return readEvents(now - lookbackMs, now).lastOrNull { it.type == UsageEvent.Type.UNLOCK }?.timestampMs
    }

    private fun mapType(t: Int): UsageEvent.Type? = when {
        t == UsageEvents.Event.MOVE_TO_FOREGROUND -> UsageEvent.Type.FOREGROUND
        t == UsageEvents.Event.MOVE_TO_BACKGROUND -> UsageEvent.Type.BACKGROUND
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && t == UsageEvents.Event.KEYGUARD_HIDDEN -> UsageEvent.Type.UNLOCK
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && t == UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEvent.Type.SCREEN_OFF
        else -> null
    }
}
