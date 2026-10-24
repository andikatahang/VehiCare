package com.vehicare.notifications

import androidx.compose.runtime.Composable

/**
 * The persisted first-ask bit set — two historical facts, never cleared.
 *
 * [hasRequestedSystemPrompt] / [markSystemPromptRequested]: "the user has already been put in
 * front of the OS prompt (or, below API 33, the notification settings screen) once". Written
 * **before** the platform call, so a process death during the sheet cannot produce a second ask.
 * Android reports no `NOT_DETERMINED` at all, so this bit is the only thing that distinguishes
 * "never asked" from "denied" on that platform.
 *
 * [hasAutoExplained] / [markAutoExplained]: "the automatic post-save explanation dialog has
 * already been presented once on this install". Written **when the dialog is shown**, not when
 * it is dismissed, so even a process death on the dialog cannot buy a second automatic ask (D-54,
 * amended 2026-10-23).
 *
 * Neither bit is ever cleared: both are historical facts, not permission states — the two names
 * are kept visibly distinct because conflating them is exactly the bug the amendment fixes.
 * Uninstalling the app clears both, which is correct: a fresh install legitimately gets one fresh
 * ask.
 */
internal interface NotificationPermissionStore {

    fun hasRequestedSystemPrompt(): Boolean

    fun markSystemPromptRequested()

    fun hasAutoExplained(): Boolean

    fun markAutoExplained()
}

/** Platform entry point for the store, resolved once per composition by the coordinator factory. */
@Composable
internal expect fun rememberNotificationPermissionStore(): NotificationPermissionStore
