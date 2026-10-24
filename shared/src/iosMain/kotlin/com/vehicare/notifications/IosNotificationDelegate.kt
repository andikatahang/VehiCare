package com.vehicare.notifications

import com.vehicare.util.ReminderNotificationId
import kotlinx.cinterop.ExperimentalForeignApi
import platform.darwin.NSObject
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptionList
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNNotificationDefaultActionIdentifier
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol

/**
 * The suffix an immediate delivery appends to [ReminderNotificationId.encode]'s payload, so it
 * never collides with a pending scheduled request for the same rule.
 */
internal const val IMMEDIATE_ID_SUFFIX: String = ":now"

/**
 * The retained delegate that makes foreground notifications visible and routes taps (RESEARCH
 * Pitfall 2/3).
 *
 * This class alone is not enough — see [NotificationBridge] for why the *instance* must be a
 * process-wide `val` rather than a stack-local object.
 */
@OptIn(ExperimentalForeignApi::class)
internal class IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    /**
     * Every iOS mileage reminder is posted while the app is in the foreground, and without an
     * explicit presentation option iOS shows **nothing** (RESEARCH Pitfall 3) — this override is
     * not cosmetic, it is the delivery of REM-04.
     */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList)
    }

    /**
     * The suffix strip is what lets an immediate delivery reuse the same identifier payload as its
     * pending sibling without weakening [ReminderNotificationId.vehicleIdOf]'s strict contract
     * (D-55): a tap either opens the right vehicle or does nothing.
     */
    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        if (didReceiveNotificationResponse.actionIdentifier == UNNotificationDefaultActionIdentifier) {
            val identifier = didReceiveNotificationResponse.notification.request.identifier
                .removeSuffix(IMMEDIATE_ID_SUFFIX)
            ReminderNotificationId.vehicleIdOf(identifier)?.let(NotificationTapBus::publish)
        }
        withCompletionHandler()
    }
}

/**
 * The one predicate the scheduler uses to clear only this app's own requests, matching both the
 * scheduled identifier shape and the immediate [IMMEDIATE_ID_SUFFIX]-suffixed shape.
 */
internal fun isOwnIdentifier(identifier: String): Boolean =
    ReminderNotificationId.vehicleIdOf(identifier.removeSuffix(IMMEDIATE_ID_SUFFIX)) != null
