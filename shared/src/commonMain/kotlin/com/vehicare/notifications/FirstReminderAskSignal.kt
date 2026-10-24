package com.vehicare.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The replay-1 save hand-off between the reminder editor and the Reminders screen (D-54, amended
 * 2026-10-23), mirroring [NotificationTapBus]'s replay-1 idiom for the same reason: the editor
 * publishes then pops, and the Reminders screen's ViewModel can be cleared (if the user backs out
 * while the save is in flight) before a replay-0 flow's emission would ever reach it. Replay-1
 * preserves the ask for the next matching Reminders screen; [consume] is mandatory or a stale ask
 * would reopen the dialog on every later visit.
 *
 * The payload is the vehicle id so a Reminders screen open for a *different* vehicle can never
 * consume a signal meant for another one. Only a successful **add** publishes — the amended D-54
 * is "the first successful add", so an edit never touches this bus — and this object carries no
 * permission decision of its own: it says only "a reminder was just added", never "ask now".
 * `RemindersScreen` asks [NotificationPermissionPolicy] for that.
 */
internal object FirstReminderAskSignal {

    private val _pending = MutableStateFlow<Long?>(null)

    /** The vehicle id whose first add just saved, or `null` when there is nothing pending. */
    val pending: StateFlow<Long?> = _pending.asStateFlow()

    /** Publishes [vehicleId] for the matching Reminders screen to consume. A non-positive id is ignored. */
    fun publish(vehicleId: Long) {
        if (vehicleId <= 0L) return
        _pending.value = vehicleId
    }

    /** Clears the pending ask. Mandatory as soon as a matching screen has reacted to it. */
    fun consume() {
        _pending.value = null
    }
}
