package app.anchor.guard

import app.anchor.core.model.Surface

/** A stated reason for opening an app; grants a pass with extra surfaces blocked (Journey B). */
data class Purpose(val label: String, val minutes: Int, val extraBlockedSurfaces: Set<Surface> = emptySet())

object Purposes {
    fun forPackage(pkg: String): List<Purpose> = when (pkg) {
        "com.instagram.android" -> listOf(
            Purpose("Message a friend", 15, setOf(Surface.INSTAGRAM_REELS, Surface.INSTAGRAM_EXPLORE)),
            Purpose("Post or check my account", 10, setOf(Surface.INSTAGRAM_REELS, Surface.INSTAGRAM_EXPLORE)),
        )
        "com.google.android.youtube" -> listOf(
            Purpose("Watch a lecture or something specific", 45, setOf(Surface.YOUTUBE_SHORTS)),
        )
        "com.facebook.katana" -> listOf(
            Purpose("Messages, groups or events", 15, setOf(Surface.FACEBOOK_WATCH)),
        )
        else -> listOf(Purpose("Something specific", 10))
    }
}
