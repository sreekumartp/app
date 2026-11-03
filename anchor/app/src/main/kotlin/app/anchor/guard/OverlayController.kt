package app.anchor.guard

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import app.anchor.core.policy.BlockReason
import app.anchor.core.predict.InterventionLevel
import app.anchor.ui.theme.AnchorTheme

sealed interface MainOverlay {
    val pkg: String

    data class Block(
        override val pkg: String,
        val appLabel: String,
        val reason: BlockReason,
        val untilMs: Long?,
        val detail: String?,
        val focusSession: Boolean,
    ) : MainOverlay

    data class Friction(
        override val pkg: String,
        val appLabel: String,
        val level: InterventionLevel,
        val breathingSeconds: Int,
        val purposes: List<Purpose>,
    ) : MainOverlay
}

interface OverlayActions {
    fun goHome()
    fun openAnchor()
    fun continueAfterGate(pkg: String)
    fun backOut(pkg: String)
    fun statePurpose(pkg: String, purpose: Purpose)
    fun justBrowsing(pkg: String)
    fun markIntentional(pkg: String)
}

/**
 * Draws every intervention in Anchor's own TYPE_APPLICATION_OVERLAY windows (section 8: ML-driven
 * interventions act through our overlay, never accessibility actions). Three windows:
 * a full-screen interactive one, a touch-through fuse pill, and a touch-through dim layer whose
 * alpha stays ≤ 0.8 so Android 12+ still passes touches to the app underneath.
 */
class OverlayController(private val context: Context, private val actions: OverlayActions) {
    private val wm = context.getSystemService(WindowManager::class.java)
    private val owner = OverlayLifecycleOwner()

    private val mainState = mutableStateOf<MainOverlay?>(null)
    private val fuseState = mutableStateOf<Long?>(null)
    private var mainView: ComposeView? = null
    private var fuseView: ComposeView? = null
    private var dimView: View? = null
    private var dimLevel = 0f

    val main: MainOverlay? get() = mainState.value

    private val canDraw get() = Settings.canDrawOverlays(context)

    fun showMain(o: MainOverlay) {
        if (!canDraw) return
        // Friction already on screen for this app: keep the user's progress through it.
        val cur = mainState.value
        if (cur is MainOverlay.Friction && o is MainOverlay.Friction && cur.pkg == o.pkg) return
        mainState.value = o
        if (mainView == null && canDraw) {
            mainView = compose { OverlayContent(mainState.value, actions) }.also {
                add(it, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = true))
            }
        }
    }

    fun hideMain() {
        mainState.value = null
        mainView?.let(::remove)
        mainView = null
    }

    fun showFuse(endsAtMs: Long) {
        fuseState.value = endsAtMs
        if (fuseView == null && canDraw) {
            fuseView = compose { fuseState.value?.let { FusePill(it) } }.also {
                val p = params(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT, touchable = false).apply {
                    gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                    y = (48 * context.resources.displayMetrics.density).toInt()
                    alpha = PILL_ALPHA
                }
                add(it, p)
            }
        }
    }

    fun hideFuse() {
        fuseState.value = null
        fuseView?.let(::remove)
        fuseView = null
    }

    /** IN-03 progressive dim. */
    fun setDim(level: Float) {
        val a = level.coerceIn(0f, MAX_PASS_THROUGH_ALPHA)
        if (a <= 0f) return clearDim()
        if (!canDraw) return
        val v = dimView ?: View(context).apply { setBackgroundColor(Color.BLACK) }.also {
            dimView = it
            add(it, params(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT, touchable = false).apply { alpha = a })
        }
        if (a != dimLevel) {
            dimLevel = a
            (v.layoutParams as? WindowManager.LayoutParams)?.let { p ->
                p.alpha = a
                runCatching { wm.updateViewLayout(v, p) }
            }
        }
    }

    fun clearDim() {
        dimView?.let(::remove)
        dimView = null
        dimLevel = 0f
    }

    fun hideAll() {
        hideMain()
        hideFuse()
        clearDim()
    }

    fun destroy() {
        hideAll()
        owner.destroy()
    }

    private fun compose(content: @androidx.compose.runtime.Composable () -> Unit): ComposeView =
        ComposeView(context).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setContent { AnchorTheme { content() } }
        }

    private fun params(w: Int, h: Int, touchable: Boolean): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!touchable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        return WindowManager.LayoutParams(w, h, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, flags, PixelFormat.TRANSLUCENT).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
    }

    private fun add(v: View, p: WindowManager.LayoutParams) {
        try {
            wm.addView(v, p)
        } catch (e: Exception) {
            Log.w("Anchor", "Overlay add failed", e)
        }
    }

    private fun remove(v: View) {
        runCatching { wm.removeView(v) }
    }

    private companion object {
        // Android 12+ passes touches through overlays only while their combined opacity is
        // ≤ 0.8. The dim and the pill can overlap: 1 - (1 - 0.6)(1 - 0.5) = 0.8.
        const val MAX_PASS_THROUGH_ALPHA = 0.6f
        const val PILL_ALPHA = 0.5f
    }
}

/** ComposeView outside an Activity needs a lifecycle and saved-state owner. */
private class OverlayLifecycleOwner : SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    init {
        savedState.performAttach()
        savedState.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun destroy() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
