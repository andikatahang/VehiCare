package com.vehicare.data

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
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
