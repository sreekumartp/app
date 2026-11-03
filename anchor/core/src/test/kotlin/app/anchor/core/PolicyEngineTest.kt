package app.anchor.core

import app.anchor.core.exam.ExamPlanner
import app.anchor.core.focus.FocusMode
import app.anchor.core.focus.FocusSession
import app.anchor.core.model.AnchorConfig
import app.anchor.core.model.ExamPlan
import app.anchor.core.model.ExamType
import app.anchor.core.model.Schedule
import app.anchor.core.model.Surface
import app.anchor.core.model.TaggedApp
import app.anchor.core.policy.BlockReason
import app.anchor.core.policy.Decision
import app.anchor.core.policy.LaunchContext
import app.anchor.core.policy.PolicyEngine
import app.anchor.core.policy.RuntimeState
import app.anchor.core.policy.ScheduleEvaluator
import app.anchor.core.predict.InterventionLevel
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class PolicyEngineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val insta = "com.instagram.android"
    private val yt = "com.google.android.youtube"
    private val engine = PolicyEngine(alwaysAllowed = setOf("app.anchor", "com.android.dialer"), strictGuarded = setOf("com.android.settings"))

    // Tuesday 3 Nov 2026
    private fun at(h: Int, m: Int = 0, day: Int = 3) =
        ZonedDateTime.of(2026, 11, day, h, m, 0, 0, zone).toInstant().toEpochMilli()

    private val config = AnchorConfig(
        apps = listOf(
            TaggedApp(insta, "Instagram", dailyLimitMinutes = 30, blockedSurfaces = setOf(Surface.INSTAGRAM_REELS)),
            TaggedApp(yt, "YouTube", blockedSurfaces = setOf(Surface.YOUTUBE_SHORTS)),
        ),
        schedules = listOf(Schedule("night", "Night social", days = setOf(1, 2, 3, 4, 5), startMinute = 21 * 60, endMinute = 7 * 60)),
    )

    private fun ctx(pkg: String, now: Long, used: Map<String, Long> = emptyMap(), score: Double? = null) =
        LaunchContext(pkg, now, zone, used, score)

    @Test fun untaggedAppsAreAllowed() {
        assertEquals(Decision.Allow, engine.decide(config, RuntimeState(), ctx("com.whatsapp", at(22))))
    }

    @Test fun overnightScheduleBlocksUntilMorning() {
        val d = engine.decide(config, RuntimeState(), ctx(insta, at(22, 40)))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.SCHEDULE, d.reason)
        assertEquals(at(7, 0, day = 4), d.untilMs)
        // Saturday 06:00 is still inside Friday night's window.
        assertIs<Decision.Block>(engine.decide(config, RuntimeState(), ctx(insta, at(6, 0, day = 7))))
        // Sunday 06:00: Saturday is not a scheduled day.
        assertEquals(Decision.Allow, engine.decide(config, RuntimeState(), ctx(insta, at(6, 0, day = 8))))
    }

    @Test fun dailyLimit() {
        val d = engine.decide(config, RuntimeState(), ctx(insta, at(12), used = mapOf(insta to 31 * 60_000L)))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.DAILY_LIMIT, d.reason)
        assertEquals(Decision.Allow, engine.decide(config, RuntimeState(), ctx(insta, at(12), used = mapOf(insta to 10 * 60_000L))))
        assertEquals(20, engine.remainingLimitMinutes(config, insta, mapOf(insta to 10 * 60_000L)))
    }

    @Test fun focusSessionBlocksEverythingExceptWhitelist() {
        val session = FocusSession("s", FocusMode.Countdown(50), startedAtMs = at(10), allowedPackages = setOf("com.notes"))
        val state = RuntimeState(focus = session)
        val d = engine.decide(config, state, ctx("com.whatsapp", at(10, 10)))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.FOCUS, d.reason)
        assertEquals(at(10, 50), d.untilMs)
        assertEquals(Decision.Allow, engine.decide(config, state, ctx("com.notes", at(10, 10))))
        assertEquals(Decision.Allow, engine.decide(config, state, ctx("com.android.dialer", at(10, 10))))
        assertEquals(Decision.Allow, engine.decide(config, state, ctx("com.whatsapp", at(10, 51))))
    }

    @Test fun fuseAllowsThenBlocksForCooldown() {
        val state = RuntimeState(fuses = mapOf(insta to at(12, 10)))
        assertEquals(Decision.AllowWithFuse(at(12, 10)), engine.decide(config, state, ctx(insta, at(12, 5))))
        val d = engine.decide(config, state, ctx(insta, at(12, 15)))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.FUSE_EXPIRED, d.reason)
        assertEquals(Decision.Allow, engine.decide(config, state, ctx(insta, at(12, 41))))
    }

    @Test fun habitScoreLadder() {
        assertEquals(Decision.Allow, engine.decide(config, RuntimeState(), ctx(yt, at(12), score = 0.2)))
        val gate = engine.decide(config, RuntimeState(), ctx(yt, at(12), score = 0.6))
        assertIs<Decision.Friction>(gate)
        assertEquals(InterventionLevel.BREATHING_GATE, gate.level)
        val prompt = engine.decide(config, RuntimeState(), ctx(yt, at(12), score = 0.9))
        assertIs<Decision.Friction>(prompt)
        assertEquals(InterventionLevel.INTENT_PROMPT, prompt.level)
        // A stated purpose grants a pass.
        val pass = RuntimeState(passes = mapOf(yt to at(12, 20)))
        assertEquals(Decision.Allow, engine.decide(config, pass, ctx(yt, at(12), score = 0.9)))
    }

    @Test fun passSurfacesAddToUserRules() {
        val state = RuntimeState(passes = mapOf(insta to at(12, 20)), passSurfaces = mapOf(insta to setOf(Surface.INSTAGRAM_EXPLORE)))
        assertEquals(setOf(Surface.INSTAGRAM_REELS, Surface.INSTAGRAM_EXPLORE), engine.blockedSurfaces(config, state, insta, at(12)))
        assertEquals(setOf(Surface.INSTAGRAM_REELS), engine.blockedSurfaces(config, state, insta, at(12, 30)))
    }

    @Test fun doomscrollFuseAppliesToUntaggedApps() {
        val state = RuntimeState(fuses = mapOf("com.reddit" to at(12, 5)))
        assertEquals(Decision.AllowWithFuse(at(12, 5)), engine.decide(config, state, ctx("com.reddit", at(12))))
        assertIs<Decision.Block>(engine.decide(config, state, ctx("com.reddit", at(12, 10))))
    }

    @Test fun settingsBlockedOnlyInStrictSessions() {
        val normal = RuntimeState(focus = FocusSession("s", FocusMode.Countdown(50), startedAtMs = at(10)))
        assertEquals(Decision.Allow, engine.decide(config, normal, ctx("com.android.settings", at(10, 5))))
        val strict = RuntimeState(focus = FocusSession("s", FocusMode.Pomodoro(25, 5, 2), startedAtMs = at(10), strict = true))
        // Blocked even during the Pomodoro break, until the planned end.
        val d = engine.decide(config, strict, ctx("com.android.settings", at(10, 27)))
        assertIs<Decision.Block>(d)
        assertEquals(at(10, 55), d.untilMs)
        assertEquals(Decision.Allow, engine.decide(config, strict, ctx("com.android.settings", at(10, 56))))
    }

    @Test fun instantBlock() {
        val d = engine.decide(config, RuntimeState(instantBlockUntilMs = at(13)), ctx(yt, at(12)))
        assertIs<Decision.Block>(d)
        assertEquals(BlockReason.INSTANT, d.reason)
    }

    @Test fun examMockDayLockdownAndLeisureBudget() {
        val today = LocalDate.of(2026, 11, 3).toEpochDay()
        val exam = ExamPlan(ExamType.CLAT, examEpochDay = today + 30, mockTestEpochDays = setOf(today), baseLeisureMinutes = 90)
        val cfg = config.copy(exam = exam, schedules = emptyList())
        val lock = engine.decide(cfg, RuntimeState(), ctx(yt, at(10)))
        assertIs<Decision.Block>(lock)
        assertEquals(BlockReason.EXAM_LOCKDOWN, lock.reason)
        val allowance = ExamPlanner.targetFor(exam, today).leisureAllowanceMinutes
        val over = engine.decide(cfg, RuntimeState(), ctx(yt, at(15), used = mapOf(yt to allowance * 60_000L)))
        assertIs<Decision.Block>(over)
        assertEquals(BlockReason.LEISURE_BUDGET, over.reason)
    }

    @Test fun scheduleNextBoundary() {
        val s = config.schedules
        assertEquals(at(21), ScheduleEvaluator.nextBoundary(s, at(12), zone))
        assertEquals(at(7, 0, day = 4), ScheduleEvaluator.nextBoundary(s, at(22), zone))
        assertNull(ScheduleEvaluator.activeUntil(s.first().copy(enabled = false), at(22), zone))
    }

    @Test fun prunedDropsExpiredState() {
        val st = RuntimeState(instantBlockUntilMs = at(11), fuses = mapOf(insta to at(11, 45)), passes = mapOf(yt to at(11)))
            .pruned(at(12), engine.fuseCooldownMs)
        assertNull(st.instantBlockUntilMs)
        assertEquals(emptyMap(), st.passes)
        assertEquals(1, st.fuses.size) // still cooling down
    }
}
