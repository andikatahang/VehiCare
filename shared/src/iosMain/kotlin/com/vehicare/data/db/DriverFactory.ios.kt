package com.vehicare.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.vehicare.db.VehiCareDatabase

actual val defaultDatabaseName: String = "vehicare.db"

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory internal constructor(
    private val databaseName: String,
) {
    actual fun createDriver(): SqlDriver {
        // SQLiter (the engine behind NativeSqliteDriver) opens every connection with
        // `DatabaseConfiguration.journalMode = JournalMode.WAL` by default, so write-ahead
        // logging is on without running `PRAGMA journal_mode=wal` through the driver.
        // `SqlDriver.execute()` on native steps a statement that must return no rows, and
        // the journal_mode pragma does return a row — that combination crashes the driver.
        return NativeSqliteDriver(VehiCareDatabase.Schema, databaseName)
    }
}

@Suppress("UNUSED_PARAMETER")
actual fun createDriverFactory(platformContext: Any?, databaseName: String): DriverFactory =
    DriverFactory(databaseName)
