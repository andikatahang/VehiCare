package com.vehicare.data.db

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.vehicare.db.VehiCareDatabase

actual val defaultDatabaseName: String = "vehicare.db"

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory internal constructor(
    private val context: Context,
    private val databaseName: String,
) {
    actual fun createDriver(): SqlDriver {
        val driver = AndroidSqliteDriver(VehiCareDatabase.Schema, context, databaseName)
        // Enable write-ahead logging via the row-safe query path. `PRAGMA journal_mode=wal`
        // answers with a result row, so it cannot go through SqlDriver.execute(), which maps
        // to a statement step that expects no rows (that path throws on native/iOS).
        driver.executeQuery(
            identifier = null,
            sql = PRAGMA_JOURNAL_MODE_WAL,
            mapper = { cursor -> cursor.next() },
            parameters = 0,
        )
        return driver
    }

    private companion object {
        private const val PRAGMA_JOURNAL_MODE_WAL: String = "PRAGMA journal_mode=wal"
    }
}

actual fun createDriverFactory(platformContext: Any?, databaseName: String): DriverFactory =
    DriverFactory(
        context = platformContext as? Context ?: error(
            "Android driver creation requires an application Context. " +
                "Load the Android platform module so the Koin graph can supply one.",
        ),
        databaseName = databaseName,
    )
