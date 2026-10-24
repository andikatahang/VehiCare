package com.vehicare.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.vehicare.util.ReminderCopy
import com.vehicare.util.ReminderNotificationId

/** Unique one-shot work name for a foreground/edit-triggered re-evaluation. */
internal const val REMINDER_EVAL_NOW_WORK = "reminder-eval-now"

/** Unique periodic work name for the daily background evaluation (D-52). */
internal const val REMINDER_EVAL_PERIODIC_WORK = "reminder-eval-periodic"

/**
 * The Android [NotificationScheduler]: permission state via platform APIs only, notification
 * posting through the one [REMINDER_CHANNEL_ID] channel, and `evaluateNow` via a unique
 * [ReminderWorker] enqueue.
 *
 * No `androidx.core` dependency is introduced: every check below uses platform `NotificationManager`
 * / `checkSelfPermission` APIs already on the classpath.
 */
internal class AndroidNotificationScheduler internal constructor(
    private val context: Context,
) : NotificationScheduler {

    override suspend fun permissionState(): NotificationPermission {
        val manager = context.getSystemService(NotificationManager::class.java)
        val enabled = manager.areNotificationsEnabled()
        val runtimeGranted = if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        // Never NOT_DETERMINED: Android keeps no "never asked" state, so a consumer needing the
        // first-run distinction must combine this with its own record (plan 08's store).
        return if (enabled && runtimeGranted) NotificationPermission.GRANTED else NotificationPermission.DENIED
    }

    override suspend fun scheduleTimeBased(due: List<ScheduledReminder>) {
        // Documented no-op (D-52): the worker evaluates both axes daily, so pre-scheduling here
        // would double-deliver.
    }

    override suspend fun triggerImmediateIfOverdue(due: List<DueReminder>) {
        if (due.isEmpty()) return
        val channelName = ReminderCopy.notificationChannelName()
        ensureChannel(context, channelName)
        val manager = context.getSystemService(NotificationManager::class.java)
        due.forEach { item ->
            // The UI-SPEC declares no notification asset and `:shared` cannot reach `:androidApp`'s
            // launcher drawable, so the framework's monochrome reminder icon is used; replacing it
            // is a UI-SPEC decision, not an executor's.
            val notification = Notification.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(item.title)
                .setContentText(item.body)
                .setStyle(Notification.BigTextStyle().bigText(item.body))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .apply { contentIntent(item)?.let(::setContentIntent) }
                .build()
            manager.notify(item.ruleId.toInt(), notification)
        }
    }

    override suspend fun cancel(ruleIds: List<Long>) {
        // Android's notification id is exactly `ruleId.toInt()` (the same value
        // `triggerImmediateIfOverdue` posts under), so no identifier parsing is needed; there are
        // no pending requests to clear because `scheduleTimeBased` is a no-op on this platform.
        val manager = context.getSystemService(NotificationManager::class.java)
        ruleIds.forEach { ruleId -> manager.cancel(ruleId.toInt()) }
    }

    override fun evaluateNow() {
        // REPLACE because a newer request is strictly fresher; unique because a foreground storm
        // must not queue N evaluations.
        WorkManager.getInstance(context).enqueueUniqueWork(
            REMINDER_EVAL_NOW_WORK,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ReminderWorker>().build(),
        )
    }

    override suspend fun openNotificationSettings() {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }.getOrElse {
            val fallback = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    /**
     * `:shared` cannot name `MainActivity`, so the launcher intent plus the extra is the only
     * contract. The immutable `PendingIntent` flag below is mandatory and the request code is the
     * rule id so two rules never overwrite each other's intent. `null` when the launch intent
     * cannot resolve — a notification that cannot be tapped is skipped rather than posted.
     */
    private fun contentIntent(item: DueReminder): PendingIntent? {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.apply {
                putExtra(EXTRA_NOTIFICATION_ID, ReminderNotificationId.encode(item.vehicleId, item.ruleId))
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            ?: return null
        return PendingIntent.getActivity(
            context,
            item.ruleId.toInt(),
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

/**
 * Same `Any?` contract and the same reasoning as the database driver factory's Android actual:
 * `commonMain` cannot name `Context`, so the cast happens here, at the one platform boundary that
 * needs it.
 */
actual fun createNotificationScheduler(platformContext: Any?): NotificationScheduler =
    AndroidNotificationScheduler(
        context = platformContext as? Context ?: error(
            "Android notification scheduling requires an application Context. " +
                "Load the Android platform module so the Koin graph can supply one.",
        ),
    )
