package com.vehicare.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

private const val KEY_SYSTEM_PROMPT_REQUESTED = "vehicare.notification.system_prompt_requested"
private const val KEY_AUTO_EXPLANATION_SHOWN = "vehicare.notification.auto_explanation_shown"

/**
 * The iOS actual: `NSUserDefaults.standardUserDefaults` — no context needed, exactly like plan
 * 03-07's iOS module.
 */
private class IosNotificationPermissionStore(
    private val defaults: NSUserDefaults,
) : NotificationPermissionStore {

    override fun hasRequestedSystemPrompt(): Boolean =
        defaults.boolForKey(KEY_SYSTEM_PROMPT_REQUESTED)

    override fun markSystemPromptRequested() {
        defaults.setBool(true, forKey = KEY_SYSTEM_PROMPT_REQUESTED)
    }

    override fun hasAutoExplained(): Boolean =
        defaults.boolForKey(KEY_AUTO_EXPLANATION_SHOWN)

    override fun markAutoExplained() {
        defaults.setBool(true, forKey = KEY_AUTO_EXPLANATION_SHOWN)
    }
}

@Composable
internal actual fun rememberNotificationPermissionStore(): NotificationPermissionStore =
    remember { IosNotificationPermissionStore(NSUserDefaults.standardUserDefaults) }
