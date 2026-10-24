package com.vehicare

import android.app.Application
import com.vehicare.data.db.DatabaseFactory
import com.vehicare.data.db.DatabaseProvider
import com.vehicare.data.db.createDriverFactory
import com.vehicare.data.db.defaultDatabaseName
import com.vehicare.notifications.enqueueDailyReminderEvaluation

/**
 * The Android construction point for the process-wide database (D-63) and the daily reminder
 * evaluation (D-52).
 *
 * `Application.onCreate` runs before any Activity, so the holder is installed before Compose starts
 * and before the Koin graph is ever built — `appModule`'s `DatabaseProvider.require()` depends on
 * exactly that ordering. `MainActivity` stays the launcher and holds no Compose UI of its own (D-17);
 * this class exists only to own process-scoped startup work.
 *
 * [enqueueDailyReminderEvaluation] relies on WorkManager's default initialisation: its own
 * `androidx.startup` provider runs before `Application.onCreate`, so no `Configuration.Provider`
 * and no manifest surgery is needed (RESEARCH U1). `androidx.work` stays an
 * `implementation`-scoped dependency of `:shared` only, so this class calls the wrapper rather
 * than naming `WorkManager` itself.
 */
class VehiCareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Exactly once per process: `initialize` rejects a second call, because replacing the
        // driver would silently split change notification from the UI's queries.
        DatabaseProvider.initialize(
            DatabaseFactory(createDriverFactory(this, defaultDatabaseName).createDriver()),
        )
        // The notification channel is created idempotently from the Android notify path
        // (`AndroidNotificationScheduler.triggerImmediateIfOverdue`), not here: its name is a
        // Compose Resource that only resolves through the suspend `getString`, which `onCreate`
        // cannot call (recorded deviation from plan 03-01's comment, sanctioned by RESEARCH
        // Pitfall 6).
        enqueueDailyReminderEvaluation(this)
    }
}
