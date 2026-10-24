package com.vehicare.notifications

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch

/**
 * The Android actual: the runtime `POST_NOTIFICATIONS` prompt on API 33+, the settings jump
 * below it.
 *
 * Below API 33 there is no runtime notification permission at all — a prompt cannot exist — so
 * the only switch left is the app's own notification settings screen. The honest response is to
 * open it and report the state read back afterward, never to fake a `DENIED`/`GRANTED` from the
 * launcher's own boolean result.
 */
@Composable
internal actual fun rememberNotificationPermissionRequester(
    scheduler: NotificationScheduler,
): NotificationPermissionRequester {
    val scope = rememberCoroutineScope()
    var pendingCallback by remember { mutableStateOf<((NotificationPermission) -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val callback = pendingCallback
        pendingCallback = null
        scope.launch { callback?.invoke(scheduler.permissionState()) }
    }

    return remember(scheduler, launcher) {
        NotificationPermissionRequester { onResult ->
            if (Build.VERSION.SDK_INT >= 33) {
                pendingCallback = onResult
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                scope.launch {
                    scheduler.openNotificationSettings()
                    onResult(scheduler.permissionState())
                }
            }
        }
    }
}
