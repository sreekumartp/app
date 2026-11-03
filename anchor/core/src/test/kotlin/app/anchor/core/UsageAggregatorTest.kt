package app.anchor.core

import app.anchor.core.usage.UsageAggregator
import app.anchor.core.usage.UsageEvent
import app.anchor.core.usage.UsageEvent.Type.BACKGROUND
import app.anchor.core.usage.UsageEvent.Type.FOREGROUND
import app.anchor.core.usage.UsageEvent.Type.SCREEN_OFF
import app.anchor.core.usage.UsageEvent.Type.UNLOCK
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class UsageAggregatorTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: Int, h: Int, m: Int = 0) = ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun aggregatesPerDayWithMidnightSplit() {
        val events = listOf(
            UsageEvent(at(2, 7, 5), "", UNLOCK),
            UsageEvent(at(2, 7, 6), "insta", FOREGROUND),
            UsageEvent(at(2, 7, 16), "insta", BACKGROUND),
            UsageEvent(at(2, 23, 50), "", UNLOCK),
            UsageEvent(at(2, 23, 50), "yt", FOREGROUND),
            UsageEvent(at(3, 0, 20), "", SCREEN_OFF),
            UsageEvent(at(3, 8, 0), "", UNLOCK),
            UsageEvent(at(3, 8, 1), "insta", FOREGROUND),
        )
        val days = UsageAggregator.aggregate(events, zone, nowMs = at(3, 8, 31))
        val d2 = days.getValue(LocalDate.of(2026, 11, 2))
        assertEquals(10, d2.minutes("insta"))
        assertEquals(10, d2.minutes("yt"))
        assertEquals(2, d2.unlocks)
        assertEquals(LocalTime.of(7, 5), d2.firstPickup)
        val d3 = days.getValue(LocalDate.of(2026, 11, 3))
        assertEquals(20, d3.minutes("yt"))
        assertEquals(30, d3.minutes("insta"))
        assertEquals(1, d3.launches["insta"])

        val baseline = UsageAggregator.baselineDailyMinutes(days.values, setOf("insta", "yt"), LocalDate.of(2026, 11, 3))
        assertEquals(20, baseline)
        assertEquals(0.5, UsageAggregator.reductionVsBaseline(10.0, baseline))
    }
}
