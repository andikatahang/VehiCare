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
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(VehiCareDatabase.Schema, context, databaseName)
}

actual fun createDriverFactory(platformContext: Any?, databaseName: String): DriverFactory =
    DriverFactory(
        context = platformContext as? Context ?: error(
            "Android driver creation requires an application Context. " +
                "Load the Android platform module so the Koin graph can supply one.",
        ),
        databaseName = databaseName,
    )
