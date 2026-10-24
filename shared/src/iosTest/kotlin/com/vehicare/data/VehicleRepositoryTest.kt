package com.vehicare.data

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.db.createDriverFactory
import com.vehicare.data.repository.ServiceEntryRepositoryImpl
import com.vehicare.data.repository.VehicleRepositoryImpl
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises the repository against a real [app.cash.sqldelight.driver.native.NativeSqliteDriver]
 * on a scratch database name, never the app's real `vehicare.db`.
 */
class VehicleRepositoryTest {

    private val databaseName = "vehicare-repo-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase
    private lateinit var repository: VehicleRepositoryImpl

    @BeforeTest
    fun setUp() {
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
        repository = VehicleRepositoryImpl(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        deleteTestDatabaseFiles(databaseName)
    }

    @Test
    fun saveInsertsAndReturnsTheNewId() = runTest {
        val id = repository.save(Vehicle(name = "A"))

        assertTrue(id > 0, "insert must return the generated id, was $id")
        val rows = repository.observeVehicles().first()
        assertEquals(1, rows.size)
        assertEquals(id, rows.single().id)
        assertEquals("A", rows.single().name)
        assertEquals(VehicleType.Car, rows.single().type)
    }

    @Test
    fun saveWithAnExistingIdUpdatesInPlace() = runTest {
        val id = repository.save(Vehicle(name = "A"))

        repository.save(Vehicle(id = id, name = "B"))

        val rows = repository.observeVehicles().first()
        assertEquals(1, rows.size)
        assertEquals("B", rows.single().name)
    }

    @Test
    fun observeVehiclesReturnsNewestFirst() = runTest {
        val first = repository.save(Vehicle(name = "First"))
        val second = repository.save(Vehicle(name = "Second"))

        val ids = repository.observeVehicles().first().map { it.id }

        assertEquals(listOf(second, first), ids)
    }

    @Test
    fun observeVehicleReturnsNullForUnknownId() = runTest {
        assertNull(repository.observeVehicle(999L).first())
    }

    @Test
    fun deleteRemovesTheRowAndHandsThePhotoPathToTheDeleter() = runTest {
        val id = repository.save(Vehicle(name = "A", photoPath = "photos/a.jpg"))
        val deletedPaths = mutableListOf<String?>()
        val repositoryWithDeleter = VehicleRepositoryImpl(
            database = database,
            photoDeleter = { deletedPaths += it },
        )

        repositoryWithDeleter.delete(id)

        assertNull(repository.observeVehicle(id).first())
        assertTrue(repository.observeVehicles().first().isEmpty())
        assertEquals(listOf<String?>("photos/a.jpg"), deletedPaths)
    }

    @Test
    fun deletingAVehicleAlsoDeletesItsReminderRules() = runTest {
        val deletedId = repository.save(Vehicle(name = "A"))
        val keptId = repository.save(Vehicle(name = "B"))
        val serviceRepository = ServiceEntryRepositoryImpl(database)
        serviceRepository.save(
            ServiceEntry(vehicleId = deletedId, dateEpochMs = 1_000L, mileageKm = 500L, serviceTypes = listOf("Oil change")),
        )
        val firstRule = insertRule(deletedId, "Oil change")
        val secondRule = insertRule(deletedId, "Battery")
        val otherVehicleRule = insertRule(keptId, "Oil change")
        val deletions = mutableListOf<Pair<Long, List<Long>>>()
        val deletingRepository = VehicleRepositoryImpl(
            database = database,
            onVehicleDeleted = { vehicleId, ruleIds -> deletions += vehicleId to ruleIds },
        )

        deletingRepository.delete(deletedId)

        assertTrue(
            database.reminderRuleQueries.selectForVehicle(deletedId).executeAsList().isEmpty(),
            "D-64: a vehicle's reminder rules must not outlive it",
        )
        assertEquals(
            listOf(otherVehicleRule),
            database.reminderRuleQueries.selectForVehicle(keptId).executeAsList().map { it.id },
            "another vehicle's rules must survive the cascade",
        )
        assertTrue(
            database.serviceEntryQueries.selectForVehicleByDate(deletedId).executeAsList().isEmpty(),
            "the existing service-entry cascade is unchanged",
        )
        assertEquals(1, deletions.size, "the hook fires exactly once per delete")
        assertEquals(deletedId, deletions.single().first)
        assertEquals(
            setOf(firstRule, secondRule),
            deletions.single().second.toSet(),
            "every cascaded rule id must reach the notification hook",
        )
    }

    /**
     * Seeds a rule through the real query set so the cascade deletes real rows.
     *
     * Insert and id read share one transaction: `last_insert_rowid()` is per-connection, and the
     * native driver hands out pooled connections, so reading it outside the insert's transaction
     * can observe a different connection and return 0 (the same reason every repository save wraps
     * the pair).
     */
    private fun insertRule(vehicleId: Long, serviceType: String): Long =
        database.transactionWithResult {
            database.reminderRuleQueries.insert(
                vehicle_id = vehicleId,
                service_type = serviceType,
                interval_km = 5_000L,
                interval_months = null,
                lead_km = null,
                lead_days = null,
                enabled = 1L,
                created_at_epoch_ms = 1_700_000_000_000L,
            )
            database.reminderRuleQueries.lastInsertId().executeAsOne()
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
