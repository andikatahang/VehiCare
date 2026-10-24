package com.vehicare.notifications

import kotlinx.cinterop.ExperimentalForeignApi
import platform.UserNotifications.UNUserNotificationCenter

/**
 * The process-wide install point Swift calls before Compose starts.
 *
 * `UNUserNotificationCenter.delegate` is **weak**, so a delegate created on the stack is collected
 * before the first callback and the app silently shows no foreground notification and routes no
 * taps (RESEARCH Pitfall 2). [delegate] is a `val` on this process-wide object, so the reference
 * survives for the whole process lifetime. Swift must call [install] from
 * `didFinishLaunchingWithOptions`, i.e. before SwiftUI builds the Compose controller, or the
 * launch response arrives with no delegate attached.
 */
@OptIn(ExperimentalForeignApi::class)
object NotificationBridge {

    private val center = UNUserNotificationCenter.currentNotificationCenter()
    private val delegate = IosNotificationDelegate()

    fun install() {
        center.delegate = delegate
    }
}
