package com.vehicare.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.vehicare.db.VehiCareDatabase

actual val defaultDatabaseName: String = "vehicare.db"

@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
actual class DriverFactory internal constructor(
    private val databaseName: String,
) {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(VehiCareDatabase.Schema, databaseName)
}

@Suppress("UNUSED_PARAMETER")
actual fun createDriverFactory(platformContext: Any?, databaseName: String): DriverFactory =
    DriverFactory(databaseName)
