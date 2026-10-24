package com.vehicare.data.settings

import kotlinx.coroutines.flow.Flow
import platform.Foundation.NSUserDefaults

/**
 * The iOS actual: `NSUserDefaults.standardUserDefaults` — no context needed, exactly like
 * `NotificationPermissionStore.ios.kt` and plan 03-07's iOS module.
 *
 * Same key boundary as the Android actual: a stored `Long`; clearing (`setIntervalMonths(null)`)
 * removes the key, and presence (`objectForKey != nil`) distinguishes "never set" from any stored
 * value so D-1's per-rule fallback resumes exactly when the key is gone.
 */
private class IosSettingsStore(private val defaults: NSUserDefaults) : SettingsStore {

    private val observable = ObservableSettingsValue(readFromDefaults())

    override fun readIntervalMonths(): Long? = observable.current

    override fun setIntervalMonths(months: Long?) {
        if (months == null) {
            defaults.removeObjectForKey(KEY_INTERVAL_MONTHS)
        } else {
            defaults.setInteger(value = months, forKey = KEY_INTERVAL_MONTHS)
        }
        observable.set(months)
    }

    override fun observeIntervalMonths(): Flow<Long?> = observable.flow

    private fun readFromDefaults(): Long? =
        if (defaults.objectForKey(KEY_INTERVAL_MONTHS) != null) {
            defaults.integerForKey(KEY_INTERVAL_MONTHS)
        } else {
            null
        }
}

private const val KEY_INTERVAL_MONTHS = "vehicare.settings.reminder_interval_months"

actual fun createSettingsStore(platformContext: Any?): SettingsStore =
    IosSettingsStore(NSUserDefaults.standardUserDefaults)
