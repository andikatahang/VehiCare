package com.vehicare.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** The one notification channel this app posts to. */
internal const val REMINDER_CHANNEL_ID = "reminders"

/**
 * The `Intent` extra `MainActivity` reads to route a notification tap. Public because
 * `:androidApp`'s `MainActivity` reads it and `:shared` cannot reference the host's activity
 * class.
 */
const val EXTRA_NOTIFICATION_ID = "com.vehicare.extra.NOTIFICATION_ID"

/**
 * Creates [REMINDER_CHANNEL_ID] if it does not already exist.
 *
 * Idempotent by platform semantics (`createNotificationChannel` is a no-op for an existing
 * channel id), so it is safe to call from every notify path. It is called from the notify path
 * rather than `Application.onCreate` because [name] is a Compose Resource that only resolves
 * through the suspend `getString`, which `onCreate` cannot call (recorded deviation from plan
 * 03-01's comment, sanctioned by RESEARCH Pitfall 6).
 */
internal fun ensureChannel(context: Context, name: String) {
    val channel = NotificationChannel(
        REMINDER_CHANNEL_ID,
        name,
        NotificationManager.IMPORTANCE_DEFAULT,
    ).apply { description = null }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
}
