package app.anchor.guard

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import app.anchor.core.exam.ExamDayKind
import app.anchor.core.exam.ExamPlanner
import app.anchor.core.policy.ScheduleEvaluator
import app.anchor.data.Permissions
import app.anchor.graph
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * FR-03: wakes the app at the next schedule boundary (and at focus / block / lockdown ends) with
 * an exact alarm, so enforcement starts on time even if an OEM killed the service.
 */
object AlarmScheduler {
    private const val REQUEST = 42

    fun reschedule(context: Context) {
        val g = context.graph
        val config = g.config.value
        if (config == null) {
            // Cold process (boot, clock change): wait for DataStore, then schedule.
            g.scope.launch { g.config.filterNotNull().first(); reschedule(context) }
            return
        }
        val state = g.guard.state.value
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val candidates = buildList<Long> {
            ScheduleEvaluator.nextBoundary(config.schedules, now, zone)?.let(::add)
            state.focus?.plannedEndMs()?.let(::add)
            state.instantBlockUntilMs?.let(::add)
            config.exam?.let { plan ->
                val today = LocalDate.now(zone)
                for (d in 0L..1L) {
                    val t = ExamPlanner.targetFor(plan, today.plusDays(d).toEpochDay())
                    if (t.kind == ExamDayKind.MOCK_TEST) {
                        add(ScheduleEvaluator.atMinute(today.plusDays(d), t.lockdownStartMinute!!, zone))
                        add(ScheduleEvaluator.atMinute(today.plusDays(d), t.lockdownEndMinute!!, zone))
                    }
                }
            }
        }.filter { it > now }

        val am = context.getSystemService(AlarmManager::class.java)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST, Intent(context, ScheduleAlarmReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val next = candidates.minOrNull()
        if (next == null) {
            am.cancel(pi)
            return
        }
        if (Permissions.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, pi)
        }
    }
}
