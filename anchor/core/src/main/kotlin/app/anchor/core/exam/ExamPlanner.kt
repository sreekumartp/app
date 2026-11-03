package app.anchor.core.exam

import app.anchor.core.model.ExamPlan
import kotlin.math.roundToInt

enum class ExamDayKind { RAMP, TAPER, MOCK_TEST, EXAM_DAY, AFTER_EXAM }

data class DailyExamTarget(
    val epochDay: Long,
    val daysLeft: Long,
    val kind: ExamDayKind,
    val focusTargetMinutes: Int,
    /** Total minutes allowed across all tagged apps today. */
    val leisureAllowanceMinutes: Int,
    /** Full lockdown window (minutes of day) on mock-test days. */
    val lockdownStartMinute: Int? = null,
    val lockdownEndMinute: Int? = null,
)

/**
 * IN-06 Exam-aware intensity. Plans backward from the exam date: focus targets rise and leisure
 * allowances tighten across the final [RAMP_DAYS], with a lighter taper in the last 3 days.
 */
object ExamPlanner {
    const val RAMP_DAYS = 60
    const val TAPER_DAYS = 3
    private const val FOCUS_PEAK_FACTOR = 1.5
    private const val LEISURE_FLOOR_FACTOR = 0.33
    private const val MAX_FOCUS = 600
    private const val MIN_LEISURE = 20

    fun targetFor(plan: ExamPlan, epochDay: Long): DailyExamTarget {
        val daysLeft = plan.examEpochDay - epochDay
        val base = plan.baseFocusMinutes
        val leisure = plan.baseLeisureMinutes
        val peakFocus = minOf((base * FOCUS_PEAK_FACTOR).roundToInt(), maxOf(MAX_FOCUS, base))
        val floorLeisure = maxOf((leisure * LEISURE_FLOOR_FACTOR).roundToInt(), minOf(MIN_LEISURE, leisure))

        if (daysLeft < 0) return DailyExamTarget(epochDay, daysLeft, ExamDayKind.AFTER_EXAM, 0, leisure)
        if (daysLeft == 0L) return DailyExamTarget(epochDay, 0, ExamDayKind.EXAM_DAY, 0, leisure)

        if (daysLeft <= TAPER_DAYS) {
            // Lighter revision days: keep habits, protect sleep, loosen slightly from the peak.
            return DailyExamTarget(
                epochDay, daysLeft, ExamDayKind.TAPER,
                focusTargetMinutes = (peakFocus * 0.6).roundToInt(),
                leisureAllowanceMinutes = ((floorLeisure + leisure) / 2.0).roundToInt(),
            )
        }

        val progress = (1.0 - daysLeft.toDouble() / RAMP_DAYS).coerceIn(0.0, 1.0)
        val focus = (base + (peakFocus - base) * progress).roundToInt()
        val allowance = (leisure - (leisure - floorLeisure) * progress).roundToInt()

        return if (epochDay in plan.mockTestEpochDays) {
            DailyExamTarget(
                epochDay, daysLeft, ExamDayKind.MOCK_TEST, focus, allowance,
                lockdownStartMinute = plan.mockStartMinute,
                lockdownEndMinute = plan.mockEndMinute,
            )
        } else {
            DailyExamTarget(epochDay, daysLeft, ExamDayKind.RAMP, focus, allowance)
        }
    }

    /** Upcoming days for the plan screen. */
    fun outlook(plan: ExamPlan, fromEpochDay: Long, days: Int): List<DailyExamTarget> =
        (0 until days).map { targetFor(plan, fromEpochDay + it) }
            .takeWhile { it.kind != ExamDayKind.AFTER_EXAM }
}
