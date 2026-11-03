package app.anchor.core.model

/** Sensible starting rules so onboarding stays under four minutes (Journey A). */
object Defaults {

    /** Apps pre-selected on the tagging screen when they are installed. */
    val likelyDistracting: Map<String, String> = linkedMapOf(
        "com.instagram.android" to "Instagram",
        "com.google.android.youtube" to "YouTube",
        "com.facebook.katana" to "Facebook",
        "com.snapchat.android" to "Snapchat",
        "com.twitter.android" to "X",
        "com.reddit.frontpage" to "Reddit",
        "in.mohalla.sharechat" to "ShareChat",
        "in.mohalla.video" to "Moj",
        "com.eterno.shortvideos" to "Josh",
        "com.zhiliaoapp.musically" to "TikTok",
        "in.startv.hotstar" to "JioHotstar",
        "com.netflix.mediaclient" to "Netflix",
        "com.amazon.avod.thirdpartyclient" to "Prime Video",
        "com.mxtech.videoplayer.ad" to "MX Player",
        "com.pinterest" to "Pinterest",
    )

    /** Packages whose short-form surfaces are gated by default when the app is tagged. */
    fun defaultSurfaces(pkg: String): Set<Surface> = Surface.forPackage(pkg).toSet()

    fun tagged(pkg: String, label: String, goal: Goal): TaggedApp = TaggedApp(
        packageName = pkg,
        label = label,
        dailyLimitMinutes = when (goal) {
            Goal.EXAM -> 30
            Goal.DEEP_WORK -> 45
            Goal.BALANCE -> 60
        },
        blockedSurfaces = defaultSurfaces(pkg),
    )

    fun schedulesFor(goal: Goal): List<Schedule> = when (goal) {
        Goal.EXAM -> listOf(
            Schedule("night", "Sleep protection", days = (1..7).toSet(), startMinute = 23 * 60, endMinute = 6 * 60),
        )
        Goal.DEEP_WORK -> listOf(
            Schedule("deep-am", "Morning deep work", days = (1..5).toSet(), startMinute = 9 * 60 + 30, endMinute = 11 * 60 + 30),
            Schedule("deep-pm", "Afternoon deep work", days = (1..5).toSet(), startMinute = 14 * 60, endMinute = 15 * 60 + 30),
        )
        Goal.BALANCE -> listOf(
            Schedule("night", "Evening wind-down", days = (1..7).toSet(), startMinute = 22 * 60 + 30, endMinute = 7 * 60),
        )
    }
}
