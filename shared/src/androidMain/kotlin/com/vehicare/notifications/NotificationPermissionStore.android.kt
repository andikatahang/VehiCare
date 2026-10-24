package com.vehicare.notifications

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private const val PREFS_NAME = "notification_permission"
private const val KEY_SYSTEM_PROMPT_REQUESTED = "system_prompt_requested"
private const val KEY_AUTO_EXPLANATION_SHOWN = "auto_explanation_shown"

/**
 * The Android actual: `SharedPreferences` over the **application** context, never an activity —
 * the bits outlive every screen.
 */
private class AndroidNotificationPermissionStore(
    private val prefs: SharedPreferences,
) : NotificationPermissionStore {

    override fun hasRequestedSystemPrompt(): Boolean =
        prefs.getBoolean(KEY_SYSTEM_PROMPT_REQUESTED, false)

    override fun markSystemPromptRequested() {
        prefs.edit().putBoolean(KEY_SYSTEM_PROMPT_REQUESTED, true).apply()
    }

    override fun hasAutoExplained(): Boolean =
        prefs.getBoolean(KEY_AUTO_EXPLANATION_SHOWN, false)

    override fun markAutoExplained() {
        prefs.edit().putBoolean(KEY_AUTO_EXPLANATION_SHOWN, true).apply()
    }
}

@Composable
internal actual fun rememberNotificationPermissionStore(): NotificationPermissionStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        AndroidNotificationPermissionStore(
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
        )
    }
}
