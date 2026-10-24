package com.vehicare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.vehicare.di.platformModule
import com.vehicare.notifications.EXTRA_NOTIFICATION_ID
import com.vehicare.notifications.NotificationTapBus
import com.vehicare.util.ReminderNotificationId

/**
 * The Android host: nothing but the launcher activity that mounts the shared UI.
 *
 * It holds no Compose UI of its own — `setContent { App(...) }` is the whole contract (D-17).
 * Its only job beyond the earlier plans is supplying the platform module, built once per
 * composition from the real application `Context`, and routing a notification tap into
 * [NotificationTapBus].
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleNotificationTap(intent)

        setContent {
            App(remember { platformModule(applicationContext) })
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Single-top: a running app receives the tap here, not in `onCreate`.
        setIntent(intent)
        handleNotificationTap(intent)
    }

    /**
     * The activity is `exported="true"` (it is the launcher), so any app can start it with a
     * forged extra — the payload is therefore parsed strictly and only a positive vehicle id
     * navigates. [NotificationTapBus.publish] is replay-1 so a cold start is not lost (D-55).
     */
    private fun handleNotificationTap(intent: Intent?) {
        val identifier = intent?.getStringExtra(EXTRA_NOTIFICATION_ID) ?: return
        ReminderNotificationId.vehicleIdOf(identifier)?.let(NotificationTapBus::publish)
    }
}
