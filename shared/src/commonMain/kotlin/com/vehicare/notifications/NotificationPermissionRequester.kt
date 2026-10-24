package com.vehicare.notifications

import androidx.compose.runtime.Composable

/**
 * The plain request seam both platform actuals share.
 *
 * Deliberately **not** an `expect class`: only the *factory* needs a platform
 * (`rememberLauncherForActivityResult` on Android, `UNUserNotificationCenter` on iOS), so this
 * class carries no platform type of its own — it exists purely so the Android launcher and the
 * iOS completion callback share one return type from [rememberNotificationPermissionRequester].
 *
 * The callback always carries [NotificationScheduler.permissionState] — the raw OS boolean is
 * never forwarded. Android's app-level notification switch can report "disabled" even while the
 * runtime permission is granted (`areNotificationsEnabled()` is the second switch
 * [NotificationScheduler] already folds in), so forwarding the platform's own boolean would be a
 * lie the UI could show.
 */
internal class NotificationPermissionRequester internal constructor(
    private val onRequest: (onResult: (NotificationPermission) -> Unit) -> Unit,
) {

    /** Starts (or re-starts) the platform's ask. [onResult] always carries the scheduler's own read. */
    fun request(onResult: (NotificationPermission) -> Unit) = onRequest(onResult)
}

/**
 * Builds the platform's [NotificationPermissionRequester].
 *
 * This is the only `expect`/`actual` pair this plan adds — both actuals are the only files in the
 * app that touch a launcher or `requestAuthorizationWithOptions`.
 */
@Composable
internal expect fun rememberNotificationPermissionRequester(
    scheduler: NotificationScheduler,
): NotificationPermissionRequester
