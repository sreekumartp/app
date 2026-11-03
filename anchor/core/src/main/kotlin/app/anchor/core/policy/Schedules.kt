package app.anchor.core.policy

import app.anchor.core.model.Schedule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

object ScheduleEvaluator {

    /** If [schedule] is active at [nowMs], returns the epoch ms at which the current window ends. */
    fun activeUntil(schedule: Schedule, nowMs: Long, zone: ZoneId): Long? {
        if (!schedule.enabled || schedule.days.isEmpty()) return null
        val now = Instant.ofEpochMilli(nowMs).atZone(zone)
        val today = now.toLocalDate()
        // A window that started today or (for overnight windows) yesterday.
        for (startDay in listOf(today, today.minusDays(1))) {
            if (startDay.dayOfWeek.value !in schedule.days) continue
            val (start, end) = window(schedule, startDay, zone)
            if (nowMs in start until end) return end
        }
        return null
    }

    /** Next start or end of any enabled schedule strictly after [nowMs], for AlarmManager (FR-03). */
    fun nextBoundary(schedules: Collection<Schedule>, nowMs: Long, zone: ZoneId): Long? {
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        var best: Long? = null
        for (s in schedules) {
            if (!s.enabled) continue
            for (offset in -1L..8L) {
                val day = today.plusDays(offset)
                if (day.dayOfWeek.value !in s.days) continue
                val (start, end) = window(s, day, zone)
                for (t in longArrayOf(start, end)) {
                    if (t > nowMs && (best == null || t < best)) best = t
                }
            }
        }
        return best
    }

    /** Minutes-of-day window on a local date; uses zone rules so DST days stay correct. */
    fun window(s: Schedule, startDay: LocalDate, zone: ZoneId): Pair<Long, Long> {
        val start = atMinute(startDay, s.startMinute, zone)
        val endDay = if (s.crossesMidnight) startDay.plusDays(1) else startDay
        val end = atMinute(endDay, s.endMinute, zone)
        return start to end
    }

    fun atMinute(day: LocalDate, minuteOfDay: Int, zone: ZoneId): Long =
        if (minuteOfDay >= Schedule.MINUTES_PER_DAY) day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        else ZonedDateTime.of(day.atStartOfDay(), zone).plusMinutes(minuteOfDay.toLong()).toInstant().toEpochMilli()
}
