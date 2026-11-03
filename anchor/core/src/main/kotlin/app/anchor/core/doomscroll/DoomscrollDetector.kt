package app.anchor.core.doomscroll

/**
 * IN-03 Doomscroll fuse. Fed with scroll events (from the observe-only accessibility service) for
 * any app, tagged or not. A burst of scroll events within [swipeMergeMs] counts as one swipe; a
 * swipe that follows the previous one within [rapidDwellMs] (little dwell time on the item) keeps
 * the streak going. After [triggerSwipes] rapid swipes the screen dims progressively and a fuse
 * should start. Only our overlay acts on this — never an accessibility action (Play policy).
 */
class DoomscrollDetector(
    var triggerSwipes: Int = 15,
    private val swipeMergeMs: Long = 350,
    private val rapidDwellMs: Long = 6_000,
    /** Swipes past the trigger over which the dim ramps to [maxDim]. */
    private val dimRampSwipes: Int = 20,
    private val maxDim: Float = 0.7f,
) {
    data class State(
        val packageName: String?,
        val rapidSwipes: Int,
        val triggered: Boolean,
        /** 0 = no dim, up to maxDim. */
        val dimLevel: Float,
    )

    private var pkg: String? = null
    private var lastEventMs = Long.MIN_VALUE
    private var lastSwipeMs = Long.MIN_VALUE
    private var swipes = 0

    val state: State get() = stateOf()

    fun onScroll(packageName: String, timestampMs: Long): State {
        if (packageName != pkg) reset(packageName)
        val sinceEvent = timestampMs - lastEventMs
        lastEventMs = timestampMs
        if (lastSwipeMs != Long.MIN_VALUE && sinceEvent in 0 until swipeMergeMs) {
            return stateOf() // same physical swipe, still flinging
        }
        swipes = if (lastSwipeMs != Long.MIN_VALUE && timestampMs - lastSwipeMs <= rapidDwellMs) swipes + 1 else 1
        lastSwipeMs = timestampMs
        return stateOf()
    }

    /** Called when the foreground app changes or the screen turns off. */
    fun reset(newPackage: String? = null) {
        pkg = newPackage
        swipes = 0
        lastEventMs = Long.MIN_VALUE
        lastSwipeMs = Long.MIN_VALUE
    }

    /** Dwelling on one item for a while ends the streak. */
    fun onTick(nowMs: Long): State {
        if (lastSwipeMs != Long.MIN_VALUE && nowMs - lastSwipeMs > rapidDwellMs) swipes = 0
        return stateOf()
    }

    private fun stateOf(): State {
        val triggered = swipes >= triggerSwipes
        val dim = if (!triggered) 0f else {
            val over = (swipes - triggerSwipes + 1).toFloat() / dimRampSwipes
            (over * maxDim).coerceIn(0.1f, maxDim)
        }
        return State(pkg, swipes, triggered, dim)
    }
}
