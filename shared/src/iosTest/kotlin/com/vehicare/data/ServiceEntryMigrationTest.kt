package com.vehicare.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Proves the v2 → v3 schema upgrade on a real
 * [app.cash.sqldelight.driver.native.NativeSqliteDriver], never a mock and never the repository:
 * this answers "does an upgraded database have the type rows?" and nothing else.
 *
 * The scratch driver creates the v3 schema, then the v3 machinery is removed with raw SQL so the
 * only way a type row can reappear is `2.sqm`. Two legacy rows with different labels are inserted
 * before the upgrade, so the backfill has to give each of them its **own** stored `service_type`
 * verbatim at position 0 (D-40) — a shared or normalized label would fail here.
 */
class ServiceEntryMigrationTest {

    private val databaseName = "vehicare-service-entry-migration-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase

    @BeforeTest
    fun setUp() {
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        deleteTestDatabaseFiles(databaseName)
    }

    @Test
    fun schemaVersionIsThree() {
        assertEquals(3L, VehiCareDatabase.Schema.version)
    }

    @Test
    fun migratingFromTwoBackfillsTheMirrorLabelAtPositionZero() {
        // The driver created the v3 schema. Drop the v3 table so the only path back to type rows
        // is the migration itself — a `Schema.create()` side effect cannot satisfy this test.
        driver.execute(null, "DROP TABLE service_entry_type", 0).value

        val vehicleId = insertVehicle()
        // Both inserts share one transaction so `last_insert_rowid()` reads back on the same
        // connection that wrote the row — outside a transaction the driver may route the read
        // to another connection, where `last_insert_rowid()` is 0.
        val (firstId, secondId) = database.transactionWithResult {
            insertLegacyEntry(vehicleId, "Wheel alignment") to
                insertLegacyEntry(vehicleId, "Tire rotation")
        }

        // Precondition: the legacy rows really are stored before the upgrade runs.
        assertEquals(2, database.serviceEntryQueries.selectForVehicleByDate(vehicleId).executeAsList().size)

        VehiCareDatabase.Schema.migrate(driver, 2, 3).value

        // Exactly one row per pre-v3 entry, holding that entry's stored label verbatim at 0.
        assertEquals(
            listOf("Wheel alignment"),
            database.serviceEntryQueries.selectTypes(firstId).executeAsList(),
        )
        assertEquals(
            listOf("Tire rotation"),
            database.serviceEntryQueries.selectTypes(secondId).executeAsList(),
        )
        assertEquals(listOf(0L), storedPositions(firstId))
        assertEquals(listOf(0L), storedPositions(secondId))

        // The legacy mirror column is untouched by the upgrade: it is still the stored value.
        assertEquals(
            "Wheel alignment",
            database.serviceEntryQueries.selectById(firstId).executeAsOne().service_type,
        )
    }

    /** The raw `position` values of one entry's type rows — proof of the backfill's position 0. */
    private fun storedPositions(entryId: Long): List<Long> = driver.executeQuery(
        identifier = null,
        sql = "SELECT position FROM service_entry_type WHERE entry_id = ? ORDER BY position",
        mapper = { cursor ->
            val positions = mutableListOf<Long>()
            while (cursor.next().value) {
                positions += cursor.getLong(0) ?: error("service_entry_type.position is NOT NULL")
            }
            QueryResult.Value(positions)
        },
        parameters = 1,
        binders = { bindLong(0, entryId) },
    ).value

    private fun insertVehicle(): Long = database.transactionWithResult {
        database.vehicleQueries.insert(
            name = "Upgrade test",
            make = null,
            model = null,
            year = null,
            plate = null,
            type = "Car",
            photo_path = null,
        )
        database.vehicleQueries.lastInsertId().executeAsOne()
    }

    private fun insertLegacyEntry(vehicleId: Long, label: String): Long {
        database.serviceEntryQueries.insertEntry(
            vehicle_id = vehicleId,
            service_date_epoch_ms = 1_768_867_200_000L,
            mileage_km = 10_000L,
            service_type = label,
            cost_rupiah = null,
            notes = null,
            workshop = null,
            mechanic = null,
        )
        return database.serviceEntryQueries.lastInsertId().executeAsOne()
    }

    /**
     * SQLiter resolves a bare database name to
     * `<NSApplicationSupportDirectory>/databases/<name>` (see its `DatabaseFileContext`), so the
     * scratch database and its sidecars are removed from exactly that directory.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun deleteTestDatabaseFiles(name: String) {
        val supportDirectory = NSSearchPathForDirectoriesInDomains(
            NSApplicationSupportDirectory,
            NSUserDomainMask,
            true,
        ).firstOrNull() as? String ?: return
        val fileManager = NSFileManager.defaultManager()
        listOf(name, "$name-wal", "$name-shm", "$name-journal").forEach { fileName ->
            fileManager.removeItemAtPath("$supportDirectory/databases/$fileName", null)
        }
    }
}
