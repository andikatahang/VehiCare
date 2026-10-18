package com.vehicare.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.vehicare.db.VehiCareDatabase
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/**
 * Proves WAL mode from first launch (DAT-03) against a real
 * [app.cash.sqldelight.driver.native.NativeSqliteDriver] on a scratch database name —
 * never the app's real `vehicare.db`. All three WAL facts are asserted in launch order:
 * the journal mode is `wal` before any user-facing write, the first insert leaves a
 * `-wal` sidecar on disk, and a second connection to the same file still reports `wal`.
 */
class WalModeTest {

    private val databaseName = "wal-probe-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase

    @BeforeTest
    fun setUp() {
        driver = createDriverFactory(
            platformContext = null,
            databaseName = databaseName,
        ).createDriver()
        database = VehiCareDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        deleteTestDatabaseFiles(databaseName)
    }

    @Test
    fun walIsActiveFromFirstLaunch() {
        // 1. First launch: the journal mode is `wal` before any user-facing write. The
        //    mechanism is SQLiter's JournalMode.WAL default at connection open — this read
        //    is the proof that the default is actually in effect on this driver.
        assertEquals(
            expected = JOURNAL_MODE_WAL,
            actual = journalMode(driver),
            message = "the iOS driver must open its first connection in WAL mode",
        )

        // 2. One insert, then the -wal sidecar must exist next to the database file.
        database.vehicleQueries.insert(
            name = "Probe",
            make = null,
            model = null,
            year = null,
            plate = null,
            type = "Car",
            photo_path = null,
        )
        assertTrue(
            walSidecarExists(databaseName),
            message = "expected a -wal sidecar beside $databaseName after the first insert",
        )

        // 3. WAL mode is persistent per database file: a second connection reports `wal` too.
        val secondDriver = createDriverFactory(
            platformContext = null,
            databaseName = databaseName,
        ).createDriver()
        try {
            assertEquals(
                expected = JOURNAL_MODE_WAL,
                actual = journalMode(secondDriver),
                message = "WAL must persist for a second connection on the same database file",
            )
        } finally {
            secondDriver.close()
        }
    }

    /**
     * Reads `PRAGMA journal_mode` through the row-safe query path (the path generated
     * queries use) and returns the single result value, lowercase and trimmed.
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

    /** SQLiter resolves a bare database name to `<ApplicationSupport>/databases/<name>`. */
    private fun walSidecarExists(name: String): Boolean =
        fileExistsAt(databaseDirectoryPath().let { dir -> "$dir/$name-wal" })

    @OptIn(ExperimentalForeignApi::class)
    private fun databaseDirectoryPath(): String {
        val supportDirectory = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: error("No application support directory available")
        return "$supportDirectory/databases"
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun fileExistsAt(path: String): Boolean =
        NSFileManager.defaultManager().fileExistsAtPath(path)

    /** Removes the scratch database and every SQLite sidecar from SQLiter's databases dir. */
    @OptIn(ExperimentalForeignApi::class)
    private fun deleteTestDatabaseFiles(name: String) {
        val fileManager = NSFileManager.defaultManager()
        val directory = databaseDirectoryPath()
        listOf(name, "$name-wal", "$name-shm", "$name-journal").forEach { fileName ->
            fileManager.removeItemAtPath("$directory/$fileName", null)
        }
    }

    private companion object {
        private const val JOURNAL_MODE_PRAGMA: String = "PRAGMA journal_mode"
        private const val JOURNAL_MODE_WAL: String = "wal"
    }
}
