package com.vehicare.data.db

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.vehicare.db.VehiCareDatabase
import java.io.File
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith

/**
 * Proves WAL mode from first launch (DAT-03) against a real [AndroidSqliteDriver] under
 * instrumentation, on a scratch database name — never the app's real `vehicare.db`.
 *
 * Android does NOT default to WAL, so this platform is the likelier source of a false
 * pass and all three facts are asserted in launch order: the journal mode is `wal`
 * before any user-facing write, the first insert leaves a `-wal` sidecar on disk, and
 * a second connection to the same file still reports `wal`.
 */
@RunWith(AndroidJUnit4::class)
class WalModeTest {

    private val databaseName = "wal-probe-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private lateinit var context: Context
    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase

    @BeforeTest
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        driver = createDriverFactory(
            platformContext = context,
            databaseName = databaseName,
        ).createDriver()
        database = VehiCareDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        context.deleteDatabase(databaseName)
    }

    @Test
    fun walIsActiveFromFirstLaunch() {
        // 1. First launch: the journal mode is `wal` before any user-facing write. The
        //    mechanism is the row-safe `PRAGMA journal_mode=wal` the DriverFactory runs at
        //    construction — this read-back is the proof that it worked.
        assertEquals(
            expected = JOURNAL_MODE_WAL,
            actual = journalMode(driver),
            message = "the Android driver must open its first connection in WAL mode",
        )

        // 2. One insert, then the -wal sidecar must exist next to the database file the
        //    driver created. The sidecar path is resolved from the context, never hardcoded.
        database.vehicleQueries.insert(
            name = "Probe",
            make = null,
            model = null,
            year = null,
            plate = null,
            type = "Car",
            photo_path = null,
        )
        val walSidecar = File(
            context.getDatabasePath(databaseName).path + WAL_SUFFIX,
        )
        assertTrue(
            walSidecar.isFile,
            message = "expected a -wal sidecar at ${walSidecar.path} after the first insert",
        )

        // 3. Persistence: open a second connection that never runs the WAL pragma (a bare
        //    driver, not the factory — the factory would re-assert the mode and make this
        //    check vacuous). The database file header must already say WAL on its own.
        val bareDriver = AndroidSqliteDriver(VehiCareDatabase.Schema, context, databaseName)
        try {
            assertEquals(
                expected = JOURNAL_MODE_WAL,
                actual = journalMode(bareDriver),
                message = "WAL must persist for a second connection on the same database file",
            )
        } finally {
            bareDriver.close()
        }
    }

    /**
     * Reads `PRAGMA journal_mode` through the row-safe query path (the same path the
     * DriverFactory uses for the WAL pragma) and returns the single value, lowercase.
     */
    private fun journalMode(driver: SqlDriver): String =
        driver.executeQuery(
            identifier = null,
            sql = JOURNAL_MODE_PRAGMA,
            mapper = { cursor ->
                check(cursor.next().value) { "PRAGMA journal_mode returned no row" }
                val raw = cursor.getString(0) ?: error("PRAGMA journal_mode returned NULL")
                QueryResult.Value(raw.trim().lowercase())
            },
            parameters = 0,
        ).value

    private companion object {
        private const val JOURNAL_MODE_PRAGMA: String = "PRAGMA journal_mode"
        private const val JOURNAL_MODE_WAL: String = "wal"
        private const val WAL_SUFFIX: String = "-wal"
    }
}
