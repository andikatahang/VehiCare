package com.vehicare

import android.app.Application
import com.vehicare.data.db.DatabaseFactory
import com.vehicare.data.db.DatabaseProvider
import com.vehicare.data.db.createDriverFactory
import com.vehicare.data.db.defaultDatabaseName

/**
 * The Android construction point for the process-wide database (D-63).
 *
 * `Application.onCreate` runs before any Activity, so the holder is installed before Compose starts
 * and before the Koin graph is ever built — `appModule`'s `DatabaseProvider.require()` depends on
 * exactly that ordering. `MainActivity` stays the launcher and holds no Compose UI of its own (D-17);
 * this class exists only to own process-scoped startup work.
 *
 * No `WorkManager.getInstance(this)` here: WorkManager initialises itself through its own
 * `androidx.startup` provider, and plan 07 owns scheduling.
 */
class VehiCareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Exactly once per process: `initialize` rejects a second call, because replacing the
        // driver would silently split change notification from the UI's queries.
        DatabaseProvider.initialize(
            DatabaseFactory(createDriverFactory(this, defaultDatabaseName).createDriver()),
        )
        // Plan 07 creates the notification channel here: Android 8+ silently drops a notification
        // whose channel does not exist, so channel creation must precede the first post.
    }
}
