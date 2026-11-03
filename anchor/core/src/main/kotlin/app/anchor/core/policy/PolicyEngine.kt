package app.anchor.core.policy

import app.anchor.core.exam.ExamDayKind
import app.anchor.core.exam.ExamPlanner
import app.anchor.core.focus.FocusSession
import app.anchor.core.model.AnchorConfig
import app.anchor.core.model.Surface
import app.anchor.core.predict.ActionLadder
import app.anchor.core.predict.InterventionLevel
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId

enum class BlockReason(val title: String) {
    FOCUS("Focus session in progress"),
    INSTANT("Blocked for now"),
    SCHEDULE("Scheduled block"),
    EXAM_LOCKDOWN("Mock-test lockdown"),
    DAILY_LIMIT("Daily limit reached"),
    LEISURE_BUDGET("Today's leisure allowance is used up"),
    FUSE_EXPIRED("Your fuse ran out"),
}

sealed interface Decision {
    data object Allow : Decision

    /** Allowed while a fuse burns; the overlay shows the remaining time. */
    data class AllowWithFuse(val fuseEndsAtMs: Long) : Decision

    /** IN-01 action ladder: friction drawn by our own overlay, never by accessibility actions. */
    data class Friction(val level: InterventionLevel, val breathingSeconds: Int, val score: Double) : Decision

    /** FR-04: full-app block overlay showing the reason and time left. */
    data class Block(val reason: BlockReason, val untilMs: Long?, val detail: String? = null) : Decision
}

/** Short-lived state owned by the guard service (persisted so it survives restarts). */
@Serializable
data class RuntimeState(
    val focus: FocusSession? = null,
    /** FR-04 "block now": tagged apps are blocked until this time. */
    val instantBlockUntilMs: Long? = null,
    /** Journey B fuses: package → time the fuse runs out. */
    val fuses: Map<String, Long> = emptyMap(),
    /** Package → time until which the user may use it after stating a purpose. */
    val passes: Map<String, Long> = emptyMap(),
    /**
     * Package → surfaces additionally blocked while a pass is active, e.g. "message a friend" on
     * Instagram opens DMs with Reels blocked even if Reels is not normally gated.
     */
    val passSurfaces: Map<String, Set<Surface>> = emptyMap(),
) {
    fun pruned(nowMs: Long, fuseCooldownMs: Long): RuntimeState = copy(
        focus = focus?.takeIf { it.endedAtMs == null },
        instantBlockUntilMs = instantBlockUntilMs?.takeIf { it > nowMs },
        fuses = fuses.filterValues { it + fuseCooldownMs > nowMs },
        passes = passes.filterValues { it > nowMs },
        passSurfaces = passSurfaces.filterKeys { k -> (passes[k] ?: 0) > nowMs },
    )
}

data class LaunchContext(
    val packageName: String,
    val nowMs: Long,
    val zone: ZoneId,
    /** Foreground time today per package. */
    val usedTodayMs: Map<String, Long>,
    /** IN-01 habitual-open probability for this launch, or null when not scored. */
    val habitScore: Double? = null,
)

/**
 * The single place that decides allow / add friction / block (section 6). Observers feed it;
 * models only provide scores; the engine's decision drives the overlay and accessibility actions.
 */
class PolicyEngine(
    /** Our own app, the dialer, launcher and system UI are never blocked. */
    private val alwaysAllowed: Set<String>,
    /** Settings and the package installer: allowed, except during a Strict session (FR-07). */
    private val strictGuarded: Set<String> = emptySet(),
    private val ladder: ActionLadder = ActionLadder(),
    /** After a fuse runs out the app stays blocked for this long. */
    val fuseCooldownMs: Long = 30 * 60_000L,
) {

    fun decide(config: AnchorConfig, state: RuntimeState, ctx: LaunchContext): Decision {
        val pkg = ctx.packageName
        val now = ctx.nowMs
        if (pkg in alwaysAllowed) return Decision.Allow

        val session = state.focus?.takeIf { it.endedAtMs == null && !it.isFinished(now) }
        if (pkg in strictGuarded) {
            return if (session?.strict == true) Decision.Block(BlockReason.FOCUS, session.plannedEndMs(), STRICT_DETAIL)
            else Decision.Allow
        }

        // FR-06: during the work phase of a focus session only whitelisted apps open.
        if (session != null) {
            val snap = session.snapshot(now)
            if (snap.isBlocking && pkg !in session.allowedPackages && pkg !in config.focusWhitelist) {
                return Decision.Block(BlockReason.FOCUS, snap.phaseRemainingMs?.let { now + it }, if (session.strict) STRICT_DETAIL else null)
            }
        }

        // IN-03: doomscroll fuses apply to apps the user never tagged, too.
        val app = config.app(pkg) ?: return fuseDecision(state, pkg, now) ?: Decision.Allow
        val tagged = config.taggedPackages

        state.instantBlockUntilMs?.takeIf { it > now }?.let {
            return Decision.Block(BlockReason.INSTANT, it)
        }

        config.schedules
            .filter { it.appliesTo(pkg, tagged) }
            .mapNotNull { s -> ScheduleEvaluator.activeUntil(s, now, ctx.zone)?.let { s to it } }
            .maxByOrNull { it.second }
            ?.let { (s, until) -> return Decision.Block(BlockReason.SCHEDULE, until, s.name) }

        val today = Instant.ofEpochMilli(now).atZone(ctx.zone).toLocalDate()
        val midnight = today.plusDays(1).atStartOfDay(ctx.zone).toInstant().toEpochMilli()
        val examTarget = config.exam?.let { ExamPlanner.targetFor(it, today.toEpochDay()) }

        if (examTarget?.kind == ExamDayKind.MOCK_TEST) {
            val start = ScheduleEvaluator.atMinute(today, examTarget.lockdownStartMinute!!, ctx.zone)
            val end = ScheduleEvaluator.atMinute(today, examTarget.lockdownEndMinute!!, ctx.zone)
            if (now in start until end) return Decision.Block(BlockReason.EXAM_LOCKDOWN, end, config.exam.displayName)
        }

        app.dailyLimitMinutes?.let { limit ->
            if ((ctx.usedTodayMs[pkg] ?: 0) >= limit * 60_000L) {
                return Decision.Block(BlockReason.DAILY_LIMIT, midnight, "$limit min a day")
            }
        }

        if (examTarget != null && examTarget.kind != ExamDayKind.AFTER_EXAM) {
            val used = tagged.sumOf { ctx.usedTodayMs[it] ?: 0 }
            if (used >= examTarget.leisureAllowanceMinutes * 60_000L) {
                return Decision.Block(BlockReason.LEISURE_BUDGET, midnight, "${examTarget.leisureAllowanceMinutes} min today")
            }
        }

        fuseDecision(state, pkg, now)?.let { return it }

        if ((state.passes[pkg] ?: 0) > now) return Decision.Allow

        val score = ctx.habitScore
        if (app.frictionEnabled && config.interventions.predictionEnabled && score != null) {
            return when (val level = ladder.levelFor(score)) {
                InterventionLevel.NONE -> Decision.Allow
                InterventionLevel.BREATHING_GATE -> Decision.Friction(level, ladder.breathingSeconds(score, config.interventions.breathingSeconds), score)
                InterventionLevel.INTENT_PROMPT -> Decision.Friction(level, config.interventions.breathingSeconds, score)
            }
        }
        return Decision.Allow
    }

    private fun fuseDecision(state: RuntimeState, pkg: String, now: Long): Decision? {
        val ends = state.fuses[pkg] ?: return null
        return when {
            now < ends -> Decision.AllowWithFuse(ends)
            now < ends + fuseCooldownMs -> Decision.Block(BlockReason.FUSE_EXPIRED, ends + fuseCooldownMs)
            else -> null
        }
    }

    /** Surfaces to gate in [pkg] right now: the user's rules plus any pass-specific ones. */
    fun blockedSurfaces(config: AnchorConfig, state: RuntimeState, pkg: String, nowMs: Long): Set<Surface> {
        val base = config.app(pkg)?.blockedSurfaces.orEmpty()
        val pass = if ((state.passes[pkg] ?: 0) > nowMs) state.passSurfaces[pkg].orEmpty() else emptySet()
        return base + pass
    }

    /** Minutes left today under [pkg]'s limit, for the dashboard and block screen. */
    companion object {
        const val STRICT_DETAIL = "Strict mode: end early with your anchor tag"
    }

    fun remainingLimitMinutes(config: AnchorConfig, pkg: String, usedTodayMs: Map<String, Long>): Long? {
        val limit = config.app(pkg)?.dailyLimitMinutes ?: return null
        return (limit - (usedTodayMs[pkg] ?: 0) / 60_000).coerceAtLeast(0)
    }
}
