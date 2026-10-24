package com.vehicare.notifications

import com.vehicare.domain.ReminderAxis
import com.vehicare.util.ReminderNotificationId
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.datetime.number
import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateComponents
import platform.Foundation.NSURL
import platform.Foundation.timeIntervalSinceNow as secondsFromNow
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString
import platform.UserNotifications.UNAuthorizationStatus
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNCalendarNotificationTrigger
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The iOS [NotificationScheduler]: a pre-scheduled `UNCalendarNotificationTrigger` per time-based
 * rule, an immediate `null`-trigger request for overdue rules, and `evaluateNow` on the scheduler's
 * own app-lifetime scope.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosNotificationScheduler internal constructor() : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    /**
     * Non-suspend by contract ([NotificationScheduler.evaluateNow]): a composition cannot cancel
     * this scope between the claim and the post.
     */
    private val sweepScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun permissionState(): NotificationPermission =
        suspendCancellableCoroutine { continuation ->
            center.getNotificationSettingsWithCompletionHandler { settings ->
                continuation.resumeWith(Result.success(settings!!.authorizationStatus.toPermission()))
            }
        }

    /**
     * Declarative: clear this app's own pending requests, then re-add exactly [due]. A deleted,
     * disabled or claimed rule cannot leave a stale trigger behind this way, and the strictly
     * future-instant guard below is the platform half of D-51 (the cycle claim itself lives in
     * `ReminderDelivery`).
     */
    override suspend fun scheduleTimeBased(due: List<ScheduledReminder>) {
        val ownPendingIds = suspendCancellableCoroutine<List<String>> { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                val identifiers = requests.orEmpty()
                    .filterIsInstance<UNNotificationRequest>()
                    .map { it.identifier }
                    .filter(::isOwnIdentifier)
                continuation.resumeWith(Result.success(identifiers))
            }
        }
        if (ownPendingIds.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(ownPendingIds)
        }

        due.forEach { item ->
            val content = UNMutableNotificationContent().apply {
                setTitle(item.title)
                setBody(item.body)
                setSound(UNNotificationSound.defaultSound())
            }
            val components = NSDateComponents().apply {
                year = item.notificationDate.year.toLong()
                month = item.notificationDate.month.number.toLong()
                day = item.notificationDate.day.toLong()
                hour = REMINDER_NOTIFICATION_HOUR.toLong()
                minute = 0L
            }
            // The orchestrator passes `notificationDate = today` only for an already-armed cycle
            // that may still fire; this guard re-adds the request only while 09:00 is still ahead,
            // so an already-fired trigger can never be resurrected.
            val fireDate = NSCalendar.currentCalendar.dateFromComponents(components)
            if (fireDate == null || fireDate.secondsFromNow <= 0.0) return@forEach

            val trigger = UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(
                dateComponents = components,
                repeats = false,
            )
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = ReminderNotificationId.encode(item.vehicleId, item.ruleId),
                content = content,
                trigger = trigger,
            )
            center.addNotificationRequest(request) { /* no crash, no retry loop */ }
        }
    }

    override suspend fun triggerImmediateIfOverdue(due: List<DueReminder>) {
        due.forEach { item ->
            val content = UNMutableNotificationContent().apply {
                setTitle(item.title)
                setBody(item.body)
                setSound(UNNotificationSound.defaultSound())
            }
            // A `null` trigger means "deliver now", which is only visible because of the
            // delegate's `willPresent`. The suffix keeps a pending trigger for the same rule from
            // replacing — or being replaced by — this immediate request.
            val request = UNNotificationRequest.requestWithIdentifier(
                identifier = ReminderNotificationId.encode(item.vehicleId, item.ruleId) + IMMEDIATE_ID_SUFFIX,
                content = content,
                trigger = null,
            )
            center.addNotificationRequest(request) { /* no crash, no retry loop */ }
        }
    }

    /**
     * The identifier is `v<vehicleId>:r<ruleId>` and an immediate delivery appends [IMMEDIATE_ID_SUFFIX],
     * so the rule segment is the only key needed. [isOwnIdentifier] keeps this app from ever
     * removing another app's requests, and clearing delivered notifications is what makes a
     * toggled-off or deleted rule disappear from Notification Center.
     */
    override suspend fun cancel(ruleIds: List<Long>) {
        if (ruleIds.isEmpty()) return
        val ruleSuffixes = ruleIds.map { ruleId -> ":r$ruleId" }

        val pendingToRemove = suspendCancellableCoroutine<List<String>> { continuation ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                val identifiers = requests.orEmpty()
                    .filterIsInstance<UNNotificationRequest>()
                    .map { it.identifier }
                    .filter { identifier -> isOwnIdentifier(identifier) && matchesAnyRuleSuffix(identifier, ruleSuffixes) }
                continuation.resumeWith(Result.success(identifiers))
            }
        }
        if (pendingToRemove.isNotEmpty()) {
            center.removePendingNotificationRequestsWithIdentifiers(pendingToRemove)
        }

        val deliveredToRemove = suspendCancellableCoroutine<List<String>> { continuation ->
            center.getDeliveredNotificationsWithCompletionHandler { notifications ->
                val identifiers = notifications.orEmpty()
                    .filterIsInstance<platform.UserNotifications.UNNotification>()
                    .map { it.request.identifier }
                    .filter { identifier -> isOwnIdentifier(identifier) && matchesAnyRuleSuffix(identifier, ruleSuffixes) }
                continuation.resumeWith(Result.success(identifiers))
            }
        }
        if (deliveredToRemove.isNotEmpty()) {
            center.removeDeliveredNotificationsWithIdentifiers(deliveredToRemove)
        }
    }

    private fun matchesAnyRuleSuffix(identifier: String, ruleSuffixes: List<String>): Boolean =
        ruleSuffixes.any { suffix -> identifier.endsWith(suffix) || identifier.endsWith(suffix + IMMEDIATE_ID_SUFFIX) }

    /**
     * Non-suspend by contract so a composition cannot cancel it between the claim and the post,
     * and `DISTANCE`-only because time-based rules ride the OS scheduler (D-53) while mileage is
     * manual.
     */
    override fun evaluateNow() {
        sweepScope.launch {
            runCatching {
                ReminderDeliveryFactory
                    .create(this@IosNotificationScheduler, immediateAxes = setOf(ReminderAxis.DISTANCE))
                    .evaluateAndDeliver()
            }
        }
    }

    override suspend fun openNotificationSettings() {
        val url = NSURL(string = UIApplicationOpenSettingsURLString)
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}

private fun UNAuthorizationStatus.toPermission(): NotificationPermission = when (this) {
    UNAuthorizationStatusAuthorized,
    UNAuthorizationStatusProvisional,
    UNAuthorizationStatusEphemeral,
    -> NotificationPermission.GRANTED
    UNAuthorizationStatusDenied -> NotificationPermission.DENIED
    // `UNAuthorizationStatus` is a `Long` typealias, not a Kotlin enum, so this must stay an
    // `else`: it covers `NotDetermined` and any future status value.
    else -> NotificationPermission.NOT_DETERMINED
}

@Suppress("UNUSED_PARAMETER")
actual fun createNotificationScheduler(platformContext: Any?): NotificationScheduler =
    IosNotificationScheduler()
