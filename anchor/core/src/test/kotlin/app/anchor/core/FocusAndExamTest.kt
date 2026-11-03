package app.anchor.core

import app.anchor.core.exam.ExamDayKind
import app.anchor.core.exam.ExamPlanner
import app.anchor.core.focus.FocusMode
import app.anchor.core.focus.FocusPhase
import app.anchor.core.focus.FocusSession
import app.anchor.core.model.ExamPlan
import app.anchor.core.model.ExamType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocusAndExamTest {
    private val m = FocusSession.MINUTE

    @Test fun countdown() {
        val s = FocusSession("a", FocusMode.Countdown(50), startedAtMs = 0, strict = true)
        assertEquals(FocusPhase.WORK, s.snapshot(10 * m).phase)
        assertEquals(40 * m, s.snapshot(10 * m).phaseRemainingMs)
        assertTrue(s.wouldEndEarly(10 * m))
        assertEquals(FocusPhase.DONE, s.snapshot(50 * m).phase)
        assertFalse(s.wouldEndEarly(51 * m))
        val ended = s.end(20 * m)
        assertTrue(ended.endedEarly)
        assertEquals(FocusPhase.DONE, ended.snapshot(30 * m).phase)
        assertEquals(20 * m, ended.snapshot(30 * m).focusedMs)
    }

    @Test fun pomodoroCycles() {
        val s = FocusSession("p", FocusMode.Pomodoro(25, 5, 3), startedAtMs = 0)
        assertEquals(FocusPhase.WORK, s.snapshot(10 * m).phase)
        val brk = s.snapshot(27 * m)
        assertEquals(FocusPhase.BREAK, brk.phase)
        assertEquals(3 * m, brk.phaseRemainingMs)
        assertEquals(25 * m, brk.focusedMs)
        val second = s.snapshot(31 * m)
        assertEquals(2, second.cycle)
        assertEquals(FocusPhase.WORK, second.phase)
        // 3 work + 2 breaks = 85 min
        assertEquals(FocusPhase.WORK, s.snapshot(84 * m).phase)
        assertEquals(FocusPhase.DONE, s.snapshot(85 * m).phase)
        assertEquals(75 * m, s.snapshot(90 * m).focusedMs)
    }

    @Test fun stopwatchNeverEndsEarly() {
        val s = FocusSession("w", FocusMode.Stopwatch, startedAtMs = 0)
        assertFalse(s.wouldEndEarly(500 * m))
        assertEquals(500 * m, s.snapshot(500 * m).focusedMs)
    }

    @Test fun examRampTaperAndMock() {
        val plan = ExamPlan(ExamType.JEE, examEpochDay = 1000, mockTestEpochDays = setOf(980), baseFocusMinutes = 240, baseLeisureMinutes = 90)
        val far = ExamPlanner.targetFor(plan, 900)
        assertEquals(ExamDayKind.RAMP, far.kind)
        assertEquals(240, far.focusTargetMinutes)
        assertEquals(90, far.leisureAllowanceMinutes)
        val near = ExamPlanner.targetFor(plan, 990)
        assertTrue(near.focusTargetMinutes > far.focusTargetMinutes)
        assertTrue(near.leisureAllowanceMinutes < far.leisureAllowanceMinutes)
        val taper = ExamPlanner.targetFor(plan, 998)
        assertEquals(ExamDayKind.TAPER, taper.kind)
        assertTrue(taper.focusTargetMinutes < ExamPlanner.targetFor(plan, 996).focusTargetMinutes)
        val mock = ExamPlanner.targetFor(plan, 980)
        assertEquals(ExamDayKind.MOCK_TEST, mock.kind)
        assertEquals(9 * 60, mock.lockdownStartMinute)
        assertEquals(ExamDayKind.EXAM_DAY, ExamPlanner.targetFor(plan, 1000).kind)
        assertEquals(ExamDayKind.AFTER_EXAM, ExamPlanner.targetFor(plan, 1001).kind)
        assertEquals(11, ExamPlanner.outlook(plan, 990, 30).size)
    }
}
