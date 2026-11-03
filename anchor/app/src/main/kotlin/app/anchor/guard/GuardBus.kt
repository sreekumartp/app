package app.anchor.guard

import app.anchor.core.model.Surface
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-process channel between the observe-only accessibility service and the guard service. */
object GuardBus {
    sealed interface Event {
        data class Foreground(val packageName: String, val atMs: Long) : Event
        data class Scroll(val packageName: String, val atMs: Long) : Event
        data class SurfaceBlocked(val packageName: String, val surface: Surface) : Event
        /** Runtime state or config changed; re-evaluate now. */
        data object Poke : Event
    }

    val events = MutableSharedFlow<Event>(extraBufferCapacity = 128, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Surfaces to gate per package, published by the guard service for the accessibility service. */
    val blockedSurfaces = MutableStateFlow<Map<String, Set<Surface>>>(emptyMap())

    @Volatile
    var accessibility: AnchorAccessibilityService? = null

    fun emit(e: Event) { events.tryEmit(e) }

    fun poke() = emit(Event.Poke)
}
