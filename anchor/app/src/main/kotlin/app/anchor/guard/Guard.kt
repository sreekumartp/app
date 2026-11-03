package app.anchor.guard

import android.content.Context
import app.anchor.core.focus.FocusMode
import app.anchor.core.focus.FocusSession
import app.anchor.core.model.Surface
import app.anchor.core.policy.RuntimeState
import app.anchor.core.predict.HabitModel
import app.anchor.core.predict.LaunchFeatures
import app.anchor.core.predict.LaunchOutcome
import app.anchor.data.SecureStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.util.UUID

/**
 * Process-wide owner of [RuntimeState] and the on-device habit model. The UI calls the actions;
 * [GuardService] observes the state and enforces it. Every change is persisted immediately, so a
 * killed process resumes a Strict session exactly where it was.
 */
class Guard(private val context: Context, private val secure: SecureStore) {
    private val _state = MutableStateFlow(secure.loadRuntime())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    @Volatile
    var model: HabitModel = secure.loadModel()
        private set

    private fun mutate(f: (RuntimeState) -> RuntimeState) {
        _state.update(f)
        secure.saveRuntime(_state.value)
        GuardBus.poke()
    }

    // --- FR-06 focus sessions -------------------------------------------------------------

    fun startFocus(mode: FocusMode, strict: Boolean, allowed: Set<String>): FocusSession {
        val session = FocusSession(UUID.randomUUID().toString(), mode, System.currentTimeMillis(), strict, allowed)
        mutate { it.copy(focus = session) }
        GuardService.start(context)
        AlarmScheduler.reschedule(context)
        return session
    }

    /**
     * Ends the running session. A Strict session that would end early only ends with the
     * physical anchor (IN-04); returns false when refused.
     */
    fun endFocus(viaAnchor: Boolean, nowMs: Long = System.currentTimeMillis()): Boolean {
        val s = _state.value.focus ?: return true
        if (s.strict && s.wouldEndEarly(nowMs) && !viaAnchor) return false
        recordFocus(s.end(nowMs), nowMs)
        mutate { it.copy(focus = null) }
        return true
    }

    /** Called by the service tick: closes a session that ran to completion. */
    fun completeFocusIfDone(nowMs: Long) {
        val s = _state.value.focus ?: return
        if (s.isFinished(nowMs)) {
            recordFocus(s, nowMs)
            mutate { it.copy(focus = null) }
        }
    }

    private fun recordFocus(s: FocusSession, nowMs: Long) {
        val minutes = (s.snapshot(nowMs).focusedMs / 60_000).toInt()
        if (minutes > 0) secure.bumpStats(LocalDate.now().toEpochDay()) { it.copy(focusMinutes = it.focusMinutes + minutes) }
    }

    // --- FR-04 instant block --------------------------------------------------------------

    fun blockNow(minutes: Int) {
        mutate { it.copy(instantBlockUntilMs = System.currentTimeMillis() + minutes * 60_000L) }
        GuardService.start(context)
        AlarmScheduler.reschedule(context)
    }

    fun cancelInstantBlock() = mutate { it.copy(instantBlockUntilMs = null) }

    // --- Journey B fuses and passes -------------------------------------------------------

    fun startFuse(pkg: String, minutes: Int) =
        mutate { it.copy(fuses = it.fuses + (pkg to System.currentTimeMillis() + minutes * 60_000L)) }

    fun grantPass(pkg: String, minutes: Int, extraSurfaces: Set<Surface> = emptySet()) = mutate {
        val until = System.currentTimeMillis() + minutes * 60_000L
        it.copy(
            passes = it.passes + (pkg to until),
            passSurfaces = if (extraSurfaces.isEmpty()) it.passSurfaces - pkg else it.passSurfaces + (pkg to extraSurfaces),
        )
    }

    fun prune(nowMs: Long, fuseCooldownMs: Long) {
        val pruned = _state.value.pruned(nowMs, fuseCooldownMs)
        if (pruned != _state.value) mutate { it.pruned(nowMs, fuseCooldownMs) }
    }

    // --- IN-01 on-device learning ---------------------------------------------------------

    fun learn(features: LaunchFeatures, outcome: LaunchOutcome) {
        model = model.learn(features, outcome.isHabitual())
        secure.saveModel(model)
    }

    fun resetModel() {
        model = HabitModel()
        secure.saveModel(model)
    }
}
