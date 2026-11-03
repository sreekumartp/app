package app.anchor.guard

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import app.anchor.core.surface.SurfaceMatcher
import app.anchor.core.surface.UiNode
import app.anchor.graph

/**
 * Observe-only accessibility service (sections 6 and 8).
 *
 * - Reports which app is in the foreground and scroll events to the guard service in-process.
 * - Reads the node tree only for tagged apps that have surface rules, to match the user's
 *   FR-05 rules (Shorts, Reels, Explore, Watch). Nothing is stored or uploaded.
 * - Its only actions are Back / Home for rules the user set. Prediction and doomscroll
 *   interventions are drawn by the overlay instead.
 */
class AnchorAccessibilityService : AccessibilityService() {

    private val matcher by lazy { SurfaceMatcher(graph.rulePack) }
    private var imePackages: Set<String> = emptySet()
    private var lastCheckMs = 0L
    private var lastBackMs = 0L
    private var backsInWindow = 0
    private var screenArea = 1.0

    override fun onServiceConnected() {
        super.onServiceConnected()
        GuardBus.accessibility = this
        imePackages = getSystemService(InputMethodManager::class.java)?.enabledInputMethodList?.map { it.packageName }?.toSet().orEmpty()
        val dm = resources.displayMetrics
        screenArea = (dm.widthPixels.toDouble() * dm.heightPixels).coerceAtLeast(1.0)
        GuardService.start(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        // NFR-02: filter by package and type before doing any work.
        when (event.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                if (isRealForeground(pkg, event.className?.toString())) {
                    GuardBus.emit(GuardBus.Event.Foreground(pkg, System.currentTimeMillis()))
                }
                checkSurfaces(pkg, force = true)
            }
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                GuardBus.emit(GuardBus.Event.Scroll(pkg, System.currentTimeMillis()))
                checkSurfaces(pkg, force = false)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> checkSurfaces(pkg, force = false)
        }
    }

    /** Our own overlay windows, the keyboard and the notification shade are not app switches. */
    private fun isRealForeground(pkg: String, className: String?): Boolean = when {
        pkg == packageName -> className?.startsWith("$packageName.ui.") == true || className?.startsWith("$packageName.anchor.") == true
        pkg in imePackages -> false
        pkg == "com.android.systemui" -> false
        else -> true
    }

    private fun checkSurfaces(pkg: String, force: Boolean) {
        if (GuardBus.blockScreenPackage == pkg) return // the block screen already covers it
        val wanted = GuardBus.blockedSurfaces.value[pkg] ?: return
        if (!matcher.hasRulesFor(pkg)) return
        val now = SystemClock.uptimeMillis()
        if (!force && now - lastCheckMs < CHECK_THROTTLE_MS) return
        lastCheckMs = now

        val root = rootInActiveWindow ?: return
        if (root.packageName?.toString() != pkg) return
        val match = matcher.match(pkg, NodeAdapter(root, screenArea), wanted) ?: return
        act(now)
        GuardBus.emit(GuardBus.Event.SurfaceBlocked(pkg, match.surface))
    }

    /** Back out of the surface; if Back keeps landing on it (deep link, app restart), go Home. */
    private fun act(now: Long) {
        if (now - lastBackMs < BACK_DEBOUNCE_MS) return
        backsInWindow = if (now - lastBackMs < BACK_LOOP_WINDOW_MS) backsInWindow + 1 else 1
        lastBackMs = now
        performGlobalAction(if (backsInWindow >= 3) GLOBAL_ACTION_HOME else GLOBAL_ACTION_BACK)
    }

    fun activePackage(): String? = rootInActiveWindow?.packageName?.toString()

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (GuardBus.accessibility === this) GuardBus.accessibility = null
        super.onDestroy()
    }

    private companion object {
        const val CHECK_THROTTLE_MS = 150L
        const val BACK_DEBOUNCE_MS = 700L
        const val BACK_LOOP_WINDOW_MS = 4_000L
    }
}

/** Adapts AccessibilityNodeInfo to the core [UiNode]; off-screen nodes are skipped. */
private class NodeAdapter(private val node: AccessibilityNodeInfo, private val screenArea: Double) : UiNode {
    override val viewId: String? get() = node.viewIdResourceName
    override val contentDescription: String? get() = node.contentDescription?.toString()
    override val className: String? get() = node.className?.toString()
    override val isSelected: Boolean get() = node.isSelected
    override val screenFraction: Double
        get() {
            val r = Rect()
            node.getBoundsInScreen(r)
            return (r.width().toDouble() * r.height()) / screenArea
        }
    override val childCount: Int get() = node.childCount
    override fun child(index: Int): UiNode? =
        node.getChild(index)?.takeIf { it.isVisibleToUser }?.let { NodeAdapter(it, screenArea) }
}
