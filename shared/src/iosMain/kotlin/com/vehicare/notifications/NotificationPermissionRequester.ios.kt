package com.vehicare.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.launch
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The iOS actual: the one-time `requestAuthorizationWithOptions` ask, hopped back onto the
 * composition's own scope because the completion handler runs off the main queue.
 *
 * Only alert and sound are requested — the UI-SPEC forbids a badge count, so asking for the
 * badge authorization would request a capability the app never uses.
 */
@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun rememberNotificationPermissionRequester(
    scheduler: NotificationScheduler,
): NotificationPermissionRequester {
    val scope = rememberCoroutineScope()
    return remember(scheduler, scope) {
        NotificationPermissionRequester { onResult ->
            UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
                UNAuthorizationOptionAlert or UNAuthorizationOptionSound,
            ) { _, _ ->
                scope.launch { onResult(scheduler.permissionState()) }
            }
        }
    }
}
