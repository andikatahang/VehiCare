package com.vehicare.data.db

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.db.VehiCareDatabase

/**
 * Owns the process-wide [VehiCareDatabase] instance.
 *
 * SQLDelight drivers are not safe to instantiate more than once against the same file, so the
 * Koin graph builds the driver exactly once and hands it here. Nothing else in the app may
 * construct one — every consumer goes through the repository instead.
 */
class DatabaseFactory(driver: SqlDriver) {
    val database: VehiCareDatabase = VehiCareDatabase(driver)
}
