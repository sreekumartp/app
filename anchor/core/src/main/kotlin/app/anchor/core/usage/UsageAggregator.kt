package app.anchor.core.usage

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Platform-neutral view of android.app.usage.UsageEvents.Event. */
data class UsageEvent(val timestampMs: Long, val packageName: String, val type: Type) {
    enum class Type { FOREGROUND, BACKGROUND, UNLOCK, SCREEN_OFF }
}

data class DayUsage(
    val date: LocalDate,
    /** Foreground milliseconds per package. */
    val perAppMs: Map<String, Long>,
    val unlocks: Int,
    /** First unlock (or first foreground event) of the day, local time. */
    val firstPickup: LocalTime?,
    /** Number of times each package came to the foreground. */
    val launches: Map<String, Int>,
) {
    fun minutes(pkg: String): Long = (perAppMs[pkg] ?: 0) / 60_000
    fun totalMs(packages: Collection<String>): Long = packages.sumOf { perAppMs[it] ?: 0 }
}

/**
 * FR-01: turns raw usage events into per-day dashboards. Sessions that span midnight are split
 * across days; an app still in the foreground at [nowMs] counts up to now.
 */
object UsageAggregator {

    fun aggregate(
        events: List<UsageEvent>,
        zone: ZoneId,
        nowMs: Long,
        ignorePackages: Set<String> = emptySet(),
    ): Map<LocalDate, DayUsage> {
        val perDayApp = HashMap<LocalDate, HashMap<String, Long>>()
        val unlocks = HashMap<LocalDate, Int>()
        val firstPickup = HashMap<LocalDate, LocalTime>()
        val launches = HashMap<LocalDate, HashMap<String, Int>>()

        var current: String? = null
        var since = 0L

        fun close(end: Long) {
            val pkg = current ?: return
            if (pkg !in ignorePackages) addSpan(perDayApp, pkg, since, end, zone)
            current = null
        }

        for (e in events.sortedBy { it.timestampMs }) {
            val date = Instant.ofEpochMilli(e.timestampMs).atZone(zone).toLocalDate()
            when (e.type) {
                UsageEvent.Type.FOREGROUND -> {
                    if (current != e.packageName) {
                        close(e.timestampMs)
                        current = e.packageName
                        since = e.timestampMs
                        if (e.packageName !in ignorePackages) {
                            launches.getOrPut(date) { HashMap() }.merge(e.packageName, 1, Int::plus)
                            firstPickup.putIfAbsent(date, localTime(e.timestampMs, zone))
                        }
                    }
                }
                UsageEvent.Type.BACKGROUND -> if (current == e.packageName) close(e.timestampMs)
                UsageEvent.Type.SCREEN_OFF -> close(e.timestampMs)
                UsageEvent.Type.UNLOCK -> {
                    unlocks.merge(date, 1, Int::plus)
                    val t = localTime(e.timestampMs, zone)
                    val prev = firstPickup[date]
                    if (prev == null || t < prev) firstPickup[date] = t
                }
            }
        }
        close(nowMs)

        val dates = perDayApp.keys + unlocks.keys + launches.keys
        return dates.associateWith { d ->
            DayUsage(
                date = d,
                perAppMs = perDayApp[d].orEmpty(),
                unlocks = unlocks[d] ?: 0,
                firstPickup = firstPickup[d],
                launches = launches[d].orEmpty(),
            )
        }
    }

    /**
     * Journey A step 2: a 7-day baseline from history, so there is no waiting week.
     * Averages over days that had any recorded usage; returns null with no history.
     */
    fun baselineDailyMinutes(days: Collection<DayUsage>, tagged: Set<String>, today: LocalDate): Int? {
        val window = days.filter { it.date < today && it.date >= today.minusDays(7) && it.perAppMs.isNotEmpty() }
        if (window.isEmpty()) return null
        return (window.sumOf { it.totalMs(tagged) } / window.size / 60_000).toInt()
    }

    /** KPI: share of distracting-time reduction vs baseline (positive = less time). */
    fun reductionVsBaseline(currentDailyMinutes: Double, baselineMinutes: Int?): Double? =
        if (baselineMinutes == null || baselineMinutes <= 0) null
        else 1.0 - currentDailyMinutes / baselineMinutes

    private fun addSpan(acc: HashMap<LocalDate, HashMap<String, Long>>, pkg: String, start: Long, end: Long, zone: ZoneId) {
        var s = start
        while (s < end) {
            val date = Instant.ofEpochMilli(s).atZone(zone).toLocalDate()
            val nextMidnight = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val e = minOf(end, nextMidnight)
            acc.getOrPut(date) { HashMap() }.merge(pkg, e - s, Long::plus)
            s = e
        }
    }

    private fun localTime(ms: Long, zone: ZoneId): LocalTime =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalTime().withNano(0)
}
