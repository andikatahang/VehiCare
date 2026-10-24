package com.vehicare.data.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.Flow

private const val PREFS_NAME = "vehicare_settings"
private const val KEY_INTERVAL_MONTHS = "reminder_interval_months"

/**
 * The Android actual: `SharedPreferences` over the **application** context, never an activity —
 * exactly like `NotificationPermissionStore.android.kt`, because the setting outlives every
 * screen. Reads are synchronous; writes go through `apply()`.
 *
 * The key is stored as a `Long`; clearing (`setIntervalMonths(null)`) removes the key, so a later
 * read reports "never set" and D-1's per-rule fallback resumes. The presence check (`contains`)
 * distinguishes "never set" from any stored value without a magic sentinel — only `setIntervalMonths`
 * ever writes this key.
 */
private class AndroidSettingsStore(private val prefs: SharedPreferences) : SettingsStore {

    private val observable = ObservableSettingsValue(readFromPrefs())

    override fun readIntervalMonths(): Long? = observable.current

    override fun setIntervalMonths(months: Long?) {
        if (months == null) {
            prefs.edit().remove(KEY_INTERVAL_MONTHS).apply()
        } else {
            prefs.edit().putLong(KEY_INTERVAL_MONTHS, months).apply()
        }
        observable.set(months)
    }

    override fun observeIntervalMonths(): Flow<Long?> = observable.flow

    private fun readFromPrefs(): Long? =
        if (prefs.contains(KEY_INTERVAL_MONTHS)) prefs.getLong(KEY_INTERVAL_MONTHS, -1L) else null
}

/**
 * Builds the Android store from the **application** context (`VehiCareApp.onCreate` supplies it):
 * a SharedPreferences file outlives activity lifecycles and the daily reminder worker context.
 */
actual fun createSettingsStore(platformContext: Any?): SettingsStore {
    val context = platformContext as? Context ?: error("createSettingsStore needs an Android Context (the application one)")
    return AndroidSettingsStore(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE),
    )
}
