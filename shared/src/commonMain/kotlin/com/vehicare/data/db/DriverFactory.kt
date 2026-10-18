package com.vehicare.data.db

import app.cash.sqldelight.db.SqlDriver

/**
 * Builds the platform [SqlDriver] that backs [com.vehicare.db.VehiCareDatabase].
 *
 * This is the only genuinely platform-divergent piece of the data layer: the Android driver
 * needs an application `Context`, the iOS driver resolves the app container itself.
 *
 * No `journal_mode` configuration lives on this seam — WAL is declared in the `Vehicle.sq`
 * preamble so the generated schema emits the pragma identically on every driver, instead of
 * letting the criterion become platform-dependent.
 */
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * The canonical on-device database file name for this platform.
 *
 * Declared per platform so every actual driver ships the same name, while tests can point
 * [createDriverFactory] at a scratch database instead of the app's real one.
 */
expect val defaultDatabaseName: String

/**
 * Platform entry point for constructing a [DriverFactory].
 *
 * @param platformContext the Android application `Context`. Ignored on iOS. Typed [Any] on
 *   purpose: no Android-only type may leak into shared code.
 * @param databaseName the database file name; defaults to [defaultDatabaseName]. Tests pass a
 *   unique name so they never touch the app's real database.
 */
expect fun createDriverFactory(
    platformContext: Any? = null,
    databaseName: String = defaultDatabaseName,
): DriverFactory
