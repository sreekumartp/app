package app.anchor.core.model

import kotlinx.serialization.Serializable

/** Journey A step 1: what the user is optimising for. Drives defaults, not hard rules. */
@Serializable
enum class Goal(val label: String) {
    EXAM("Prepare for an exam"),
    DEEP_WORK("Protect deep work"),
    BALANCE("General balance"),
}

/**
 * In-app surfaces that can be gated without blocking the whole app (FR-05).
 * Each surface belongs to one package; detection rules live in the signed rule pack.
 */
@Serializable
enum class Surface(val packageName: String, val label: String) {
    YOUTUBE_SHORTS("com.google.android.youtube", "Shorts"),
    INSTAGRAM_REELS("com.instagram.android", "Reels"),
    INSTAGRAM_EXPLORE("com.instagram.android", "Explore"),
    FACEBOOK_WATCH("com.facebook.katana", "Watch");

    companion object {
        fun forPackage(pkg: String): List<Surface> = entries.filter { it.packageName == pkg }
    }
}

/** An app the user tagged as distracting during onboarding or from the Rules screen. */
@Serializable
data class TaggedApp(
    val packageName: String,
    val label: String,
    /** FR-02: per-app daily limit in minutes; null means no limit. */
    val dailyLimitMinutes: Int? = null,
    /** FR-05: surfaces inside this app to gate; the rest of the app stays usable. */
    val blockedSurfaces: Set<Surface> = emptySet(),
    /** If false the app is tracked but never receives friction (e.g. a messaging app with only surface rules). */
    val frictionEnabled: Boolean = true,
)

/**
 * FR-03: a recurring block window, e.g. social apps 21:00–07:00 on weekdays.
 * [days] uses ISO day numbers (1 = Monday … 7 = Sunday) and refers to the day the window *starts*.
 * Windows that cross midnight (start > end) continue into the following morning.
 */
@Serializable
data class Schedule(
    val id: String,
    val name: String,
    val days: Set<Int>,
    val startMinute: Int,
    val endMinute: Int,
    /** Packages the schedule blocks. Empty means every tagged app. */
    val packages: Set<String> = emptySet(),
    val enabled: Boolean = true,
) {
    init {
        require(startMinute in 0 until MINUTES_PER_DAY) { "startMinute out of range" }
        require(endMinute in 0..MINUTES_PER_DAY) { "endMinute out of range" }
        require(days.all { it in 1..7 }) { "days must be ISO 1..7" }
    }

    val crossesMidnight: Boolean get() = endMinute <= startMinute

    fun appliesTo(pkg: String, tagged: Collection<String>): Boolean =
        if (packages.isEmpty()) pkg in tagged else pkg in packages

    companion object {
        const val MINUTES_PER_DAY = 24 * 60
    }
}

@Serializable
data class InterventionSettings(
    /** IN-01: score launches and add friction to likely habitual opens. */
    val predictionEnabled: Boolean = true,
    /** IN-03: dim the screen and start a fuse after rapid swiping. */
    val doomscrollEnabled: Boolean = true,
    val doomscrollSwipes: Int = 15,
    /** Journey B: "just browsing" starts a fuse of this length. */
    val browsingFuseMinutes: Int = 10,
    val breathingSeconds: Int = 5,
)

@Serializable
enum class ExamType(val label: String) { CLAT("CLAT"), JEE("JEE"), NEET("NEET"), UPSC("UPSC"), CUSTOM("Custom") }

/** IN-06: the exam the user is preparing for. Dates are epoch days (LocalDate.toEpochDay()). */
@Serializable
data class ExamPlan(
    val type: ExamType,
    val customName: String? = null,
    val examEpochDay: Long,
    val mockTestEpochDays: Set<Long> = emptySet(),
    /** Daily focus target and leisure allowance at the start of the plan. */
    val baseFocusMinutes: Int = 240,
    val baseLeisureMinutes: Int = 90,
    /** Lockdown window applied on mock-test days. */
    val mockStartMinute: Int = 9 * 60,
    val mockEndMinute: Int = 13 * 60,
) {
    val displayName: String get() = if (type == ExamType.CUSTOM) customName ?: "Exam" else type.label
}

/** Everything the user configures. Persisted as JSON on device only (NFR-06). */
@Serializable
data class AnchorConfig(
    val onboarded: Boolean = false,
    /** DPDP working position: MVP is 18+ only, confirmed at signup. */
    val ageConfirmed18: Boolean = false,
    val goal: Goal = Goal.BALANCE,
    val apps: List<TaggedApp> = emptyList(),
    val schedules: List<Schedule> = emptyList(),
    /** FR-06: apps allowed during a focus session (dialer and our own app are always allowed). */
    val focusWhitelist: Set<String> = emptySet(),
    val interventions: InterventionSettings = InterventionSettings(),
    val exam: ExamPlan? = null,
    /** Journey A step 2: average daily distracting minutes over the 7 days before install. */
    val baselineDailyDistractingMinutes: Int? = null,
    val baselineCapturedEpochDay: Long? = null,
    /** IN-04: true once an NFC tag or printed QR code has been registered as the physical anchor. */
    val anchorRegistered: Boolean = false,
) {
    val taggedPackages: Set<String> get() = apps.mapTo(mutableSetOf()) { it.packageName }
    fun app(pkg: String): TaggedApp? = apps.firstOrNull { it.packageName == pkg }
}
