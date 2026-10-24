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
import kotlin.test.assertTrue

/**
 * Proves the schema upgrades on a real
 * [app.cash.sqldelight.driver.native.NativeSqliteDriver], never a mock and never the repository:
 * these answer "does an upgraded database have the rows and tables it should?" and nothing else.
 *
 * v2 → v3: the scratch driver creates the current schema, then the v3 machinery is removed with raw
 * SQL so the only way a type row can reappear is `2.sqm`. Two legacy rows with different labels are
 * inserted before the upgrade, so the backfill has to give each of them its **own** stored
 * `service_type` verbatim at position 0 (D-40) — a shared or normalized label would fail here.
 *
 * v3 → v4: `reminder_rule` and its index are dropped first, so the only way they can reappear is
 * `3.sqm`. Both bookkeeping markers must survive, because D-51's claim and D-53's armed proof are
 * the whole once-per-cycle guarantee.
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
    fun schemaVersionIsFour() {
        assertEquals(4L, VehiCareDatabase.Schema.version)
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

    /**
     * Proves the v3 → v4 upgrade creates the `reminder_rule` table and its index on a real
     * [app.cash.sqldelight.driver.native.NativeSqliteDriver]. The scratch driver created the v4
     * schema, so the table and index are dropped first: the only way they can reappear is
     * `3.sqm`, and a `Schema.create()` side effect cannot satisfy this test.
     */
    @Test
    fun migratingFromThreeCreatesReminderRule() {
        // Index first: dropping the table would take the index with it and make this statement fail.
        driver.execute(null, "DROP INDEX reminder_rule_vehicle", 0).value
        driver.execute(null, "DROP TABLE reminder_rule", 0).value
        assertEquals(0L, sqliteMasterCount("table", "reminder_rule"))

        VehiCareDatabase.Schema.migrate(driver, 3, 4).value

        assertEquals(1L, sqliteMasterCount("table", "reminder_rule"))
        assertEquals(1L, sqliteMasterCount("index", "reminder_rule_vehicle"))

        // Both bookkeeping markers must survive the migration: D-51's claim and D-53's armed proof.
        val columns = tableColumns("reminder_rule")
        assertTrue("notified_cycle_key" in columns, "notified_cycle_key missing: $columns")
        assertTrue("armed_cycle_key" in columns, "armed_cycle_key missing: $columns")

        // A freshly migrated table has no rules, and the generated query agrees.
        assertEquals(0, database.reminderRuleQueries.selectAll().executeAsList().size)
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

    /** How many `sqlite_master` rows of [type] carry exactly [name]. */
    private fun sqliteMasterCount(type: String, name: String): Long = driver.executeQuery(
        identifier = null,
        sql = "SELECT COUNT(*) FROM sqlite_master WHERE type = ? AND name = ?",
        mapper = { cursor ->
            cursor.next()
            QueryResult.Value(cursor.getLong(0) ?: 0L)
        },
        parameters = 2,
        binders = {
            bindString(0, type)
            bindString(1, name)
        },
    ).value

    /** The declared column names of [table], in `PRAGMA table_info` order. */
    private fun tableColumns(table: String): List<String> = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA table_info($table)",
        mapper = { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) {
                names += cursor.getString(1) ?: error("PRAGMA table_info name is NOT NULL")
            }
            QueryResult.Value(names)
        },
        parameters = 0,
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
