package app.anchor.core.focus

import kotlinx.serialization.Serializable

/** FR-06 timer modes. */
@Serializable
sealed interface FocusMode {
    @Serializable
    data object Stopwatch : FocusMode

    @Serializable
    data class Countdown(val minutes: Int) : FocusMode {
        init { require(minutes > 0) }
    }

    @Serializable
    data class Pomodoro(
        val workMinutes: Int = 25,
        val breakMinutes: Int = 5,
        val cycles: Int = 4,
    ) : FocusMode {
        init { require(workMinutes > 0 && breakMinutes >= 0 && cycles > 0) }
    }
}

enum class FocusPhase { WORK, BREAK, DONE }

data class FocusSnapshot(
    val phase: FocusPhase,
    /** Milliseconds elapsed since the session started (wall time, includes breaks). */
    val elapsedMs: Long,
    /** Milliseconds left in the current phase; null for a stopwatch. */
    val phaseRemainingMs: Long?,
    /** 1-based Pomodoro cycle, or 1 for other modes. */
    val cycle: Int,
    /** Work milliseconds accumulated so far (breaks excluded). */
    val focusedMs: Long,
) {
    val isBlocking: Boolean get() = phase == FocusPhase.WORK
}

/**
 * A running focus session. Pure state: callers pass the current time so the session survives
 * service restarts (it is persisted and re-evaluated, never ticked).
 */
@Serializable
data class FocusSession(
    val id: String,
    val mode: FocusMode,
    val startedAtMs: Long,
    /** FR-07 / Journey C: Strict sessions can only end early with the NFC anchor (IN-04). */
    val strict: Boolean = false,
    val allowedPackages: Set<String> = emptySet(),
    val endedAtMs: Long? = null,
    val endedEarly: Boolean = false,
) {
    fun snapshot(nowMs: Long): FocusSnapshot {
        val end = endedAtMs
        val t = (minOf(nowMs, end ?: nowMs) - startedAtMs).coerceAtLeast(0)
        val snap = when (mode) {
            FocusMode.Stopwatch -> FocusSnapshot(FocusPhase.WORK, t, null, 1, t)
            is FocusMode.Countdown -> {
                val total = mode.minutes * MINUTE
                if (t >= total) FocusSnapshot(FocusPhase.DONE, t, 0, 1, total)
                else FocusSnapshot(FocusPhase.WORK, t, total - t, 1, t)
            }
            is FocusMode.Pomodoro -> pomodoro(mode, t)
        }
        return if (end != null) snap.copy(phase = FocusPhase.DONE, phaseRemainingMs = 0) else snap
    }

    /** When the session is planned to finish; null for an open-ended stopwatch. */
    fun plannedEndMs(): Long? = when (mode) {
        FocusMode.Stopwatch -> null
        is FocusMode.Countdown -> startedAtMs + mode.minutes * MINUTE
        is FocusMode.Pomodoro -> startedAtMs + (mode.cycles * mode.workMinutes + (mode.cycles - 1) * mode.breakMinutes) * MINUTE
    }

    fun isFinished(nowMs: Long): Boolean = snapshot(nowMs).phase == FocusPhase.DONE

    /** Whether leaving now counts as an early exit that Strict mode must guard. */
    fun wouldEndEarly(nowMs: Long): Boolean = mode != FocusMode.Stopwatch && !isFinished(nowMs)

    fun end(nowMs: Long): FocusSession =
        copy(endedAtMs = nowMs, endedEarly = wouldEndEarly(nowMs))

    private fun pomodoro(p: FocusMode.Pomodoro, t: Long): FocusSnapshot {
        val work = p.workMinutes * MINUTE
        val brk = p.breakMinutes * MINUTE
        // The last cycle has no trailing break.
        val total = p.cycles * work + (p.cycles - 1) * brk
        if (t >= total) return FocusSnapshot(FocusPhase.DONE, t, 0, p.cycles, p.cycles * work)
        val cycleLen = work + brk
        val cycleIndex = (t / cycleLen).toInt()
        val inCycle = t - cycleIndex * cycleLen
        val focusedBefore = cycleIndex * work
        return if (inCycle < work) {
            FocusSnapshot(FocusPhase.WORK, t, work - inCycle, cycleIndex + 1, focusedBefore + inCycle)
        } else {
            FocusSnapshot(FocusPhase.BREAK, t, cycleLen - inCycle, cycleIndex + 1, focusedBefore + work)
        }
    }

    companion object {
        const val MINUTE = 60_000L
    }
}
