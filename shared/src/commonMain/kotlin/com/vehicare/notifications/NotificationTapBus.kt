package com.vehicare.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The replay-1 tap hand-off between the platform hosts and the shared navigation graph.
 *
 * Replay-1 by construction (the backing [MutableStateFlow] always holds the latest value, unlike a
 * no-replay `SharedFlow`): a cold start publishes from `MainActivity.onCreate` *before* Compose
 * collects, and a `SharedFlow` with no replay would drop exactly the tap that launched the app.
 * [consume] is mandatory after navigating or the shell would re-navigate on every recomposition.
 */
object NotificationTapBus {

    private val _pending = MutableStateFlow<Long?>(null)

    /** The vehicle id a notification tap wants to open, or `null` when there is nothing pending. */
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    /** Publishes [vehicleId] for the shell to consume. A non-positive id is ignored. */
    fun publish(vehicleId: Long) {
        if (vehicleId <= 0L) return
        _pending.value = vehicleId
    }

    /** Clears the pending tap. Must be called right after navigating on it. */
    fun consume() {
        _pending.value = null
    }
}
