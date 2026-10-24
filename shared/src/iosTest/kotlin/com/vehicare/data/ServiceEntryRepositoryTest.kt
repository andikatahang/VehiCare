package com.vehicare.data

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.db.createDriverFactory
import com.vehicare.data.repository.ServiceEntryRepositoryImpl
import com.vehicare.data.repository.VehicleRepositoryImpl
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.HistorySort
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.domain.ServiceSummary
import com.vehicare.domain.Vehicle
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
 * Exercises [ServiceEntryRepositoryImpl] against a real
 * [app.cash.sqldelight.driver.native.NativeSqliteDriver] on a scratch database name, never the
 * app's real `vehicare.db`. Every behaviour the UI plans rely on — add/edit, child replacement,
 * both history orders, summary, composition, mileage reads, learned types and delete — is proven
 * here on the driver both platforms actually ship.
 */
class ServiceEntryRepositoryTest {

    private val databaseName = "vehicare-service-entry-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase
    private lateinit var repository: ServiceEntryRepositoryImpl
    private lateinit var vehicleRepository: VehicleRepositoryImpl

    @BeforeTest
    fun setUp() {
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
        repository = ServiceEntryRepositoryImpl(database)
        vehicleRepository = VehicleRepositoryImpl(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        deleteTestDatabaseFiles(databaseName)
    }

    @Test
    fun saveAddsAnEntryAndWritesChildrenWithSequentialPositions() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))

        val id = repository.save(
            entry(
                vehicleId = vehicleId,
                parts = listOf(ServicePart(name = "Oil filter", quantity = 2), ServicePart(name = "Air filter")),
                photoPaths = listOf("service_entries/a.jpg", "service_entries/b.jpg"),
            ),
        )

        assertTrue(id > 0, "insert must return the generated id, was $id")
        val parts = database.serviceEntryQueries.selectParts(id).executeAsList()
        assertEquals(listOf(0L, 1L), parts.map { it.position })
        assertEquals(listOf("Oil filter", "Air filter"), parts.map { it.name })
        assertEquals(listOf(2L, 1L), parts.map { it.quantity })
        val photos = database.serviceEntryQueries.selectPhotos(id).executeAsList()
        assertEquals(listOf(0L, 1L), photos.map { it.position })
        assertEquals(listOf("service_entries/a.jpg", "service_entries/b.jpg"), photos.map { it.photo_path })
    }

    @Test
    fun saveWithAnExistingIdReplacesChildrenAndKeepsTheId() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val id = repository.save(
            entry(
                vehicleId = vehicleId,
                parts = listOf(ServicePart(name = "Oil filter"), ServicePart(name = "Air filter")),
                photoPaths = listOf("service_entries/a.jpg", "service_entries/b.jpg"),
            ),
        )

        repository.save(
            entry(vehicleId = vehicleId).copy(
                id = id,
                mileageKm = 999L,
                parts = listOf(ServicePart(name = "Cabin filter", quantity = 3)),
                photoPaths = emptyList(),
            ),
        )

        val parts = database.serviceEntryQueries.selectParts(id).executeAsList()
        assertEquals(1, parts.size, "edit must fully replace part rows — no orphans")
        assertEquals("Cabin filter", parts.single().name)
        assertEquals(3L, parts.single().quantity)
        assertEquals(0L, parts.single().position)
        assertTrue(database.serviceEntryQueries.selectPhotos(id).executeAsList().isEmpty())
        val rows = repository.observeHistory(vehicleId, HistorySort.DATE).first()
        assertEquals(1, rows.size)
        assertEquals(id, rows.single().id)
        assertEquals(999L, rows.single().mileageKm)
    }

    @Test
    fun historyOrdersAreDeterministicIncludingTheIdDescendingTieBreak() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val oldest = repository.save(entry(vehicleId, dateEpochMs = 1_000L, mileageKm = 500L))
        val tieNewer = repository.save(entry(vehicleId, dateEpochMs = 2_000L, mileageKm = 300L))
        val tieOldest = repository.save(entry(vehicleId, dateEpochMs = 2_000L, mileageKm = 100L))

        val byDate = repository.observeHistory(vehicleId, HistorySort.DATE).first().map { it.id }
        val byMileage = repository.observeHistory(vehicleId, HistorySort.MILEAGE).first().map { it.id }

        assertEquals(listOf(tieOldest, tieNewer, oldest), byDate)
        assertEquals(listOf(oldest, tieNewer, tieOldest), byMileage)
    }

    @Test
    fun observeSummaryCountsEntriesAndReturnsTheMaxDate() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        assertEquals(ServiceSummary(entryCount = 0, lastServiceEpochMs = null), repository.observeSummary(vehicleId).first())

        repository.save(entry(vehicleId, dateEpochMs = 1_000L, mileageKm = 500L))
        repository.save(entry(vehicleId, dateEpochMs = 3_000L, mileageKm = 600L))
        repository.save(entry(vehicleId, dateEpochMs = 2_000L, mileageKm = 700L))

        assertEquals(ServiceSummary(entryCount = 3, lastServiceEpochMs = 3_000L), repository.observeSummary(vehicleId).first())
    }

    @Test
    fun observeEntryComposesEntryPartsAndPhotos() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val expected = entry(
            vehicleId = vehicleId,
            costRupiah = 450_000L,
            notes = "Synthetic 5W-30",
            workshop = "AutoPro",
            mechanic = "Budi",
            parts = listOf(ServicePart(name = "Oil filter", quantity = 2)),
            photoPaths = listOf("service_entries/a.jpg"),
        )

        val id = repository.save(expected)

        assertEquals(expected.copy(id = id), repository.observeEntry(id).first())
        assertNull(repository.observeEntry(999L).first())
    }

    @Test
    fun latestMileageIgnoresNothingAndPreviousMileageExcludesSelf() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val first = repository.save(entry(vehicleId, dateEpochMs = 1_000L, mileageKm = 500L))
        val second = repository.save(entry(vehicleId, dateEpochMs = 2_000L, mileageKm = 700L))
        val third = repository.save(entry(vehicleId, dateEpochMs = 2_000L, mileageKm = 800L))

        assertEquals(800L, repository.observeLatestMileage(vehicleId).first())
        assertEquals(700L, repository.observePreviousMileage(vehicleId, third).first())
        assertEquals(500L, repository.observePreviousMileage(vehicleId, second).first())
        assertNull(repository.observePreviousMileage(vehicleId, first).first())
        assertNull(repository.observeLatestMileage(vehicleId + 1).first())
    }

    @Test
    fun learnedTypesAreCrossVehicleNewestFirstAndPresetCaseDuplicatesCollapse() = runTest {
        val vehicleA = vehicleRepository.save(Vehicle(name = "A"))
        val vehicleB = vehicleRepository.save(Vehicle(name = "B"))
        repository.save(entry(vehicleA, serviceType = "Battery"))
        repository.save(entry(vehicleB, serviceType = "Wheel alignment"))
        val presetVariant = repository.save(entry(vehicleA, serviceType = "oil change"))
        repository.save(entry(vehicleB, serviceType = "OIL CHANGE"))

        // "Wheel alignment" was learned on another vehicle (D-25); both preset-case variants are
        // filtered out of the chips and the stored strings are never rewritten (D-40).
        assertEquals(listOf("Wheel alignment"), repository.observeLearnedTypes().first())
        assertEquals(listOf("oil change"), repository.observeEntry(presetVariant).first()?.serviceTypes)
    }

    @Test
    fun saveWritesOneOrderedTypeRowPerLabelAndMirrorsPositionZero() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val types = listOf("Oil change", "Tire rotation", "Wheel alignment")

        val id = repository.save(entry(vehicleId, serviceTypes = types))

        val stored = database.serviceEntryQueries.selectTypes(id).executeAsList()
        assertEquals(types, stored, "one child row per label, in selection order")
        assertEquals(3, stored.size)
        assertEquals("Oil change", database.serviceEntryQueries.selectById(id).executeAsOne().service_type)
    }

    @Test
    fun editingAnEntryReplacesItsTypeRows() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val id = repository.save(
            entry(vehicleId, serviceTypes = listOf("Oil change", "Tire rotation", "Wheel alignment")),
        )

        repository.save(entry(vehicleId, serviceTypes = listOf("Battery")).copy(id = id))

        assertEquals(listOf("Battery"), database.serviceEntryQueries.selectTypes(id).executeAsList())
        assertEquals("Battery", database.serviceEntryQueries.selectById(id).executeAsOne().service_type)
    }

    @Test
    fun observeEntryAndHistoryHydrateEveryEntriesOrderedTypes() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val first = repository.save(
            entry(vehicleId, dateEpochMs = 1_000L, serviceTypes = listOf("Brake service", "Inspection")),
        )
        val second = repository.save(
            entry(vehicleId, dateEpochMs = 2_000L, serviceTypes = listOf("Battery")),
        )

        assertEquals(listOf("Brake service", "Inspection"), repository.observeEntry(first).first()?.serviceTypes)
        assertEquals(listOf("Battery"), repository.observeEntry(second).first()?.serviceTypes)

        val history = repository.observeHistory(vehicleId, HistorySort.DATE).first().associateBy { it.id }
        assertEquals(listOf("Brake service", "Inspection"), history.getValue(first).serviceTypes)
        assertEquals(listOf("Battery"), history.getValue(second).serviceTypes, "each entry keeps its own list")
    }

    @Test
    fun learnedTypesReadTheChildTableNewestFirstAndCollapseCaseVariants() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        repository.save(entry(vehicleId, serviceTypes = listOf("Wheel alignment", "Battery")))
        repository.save(entry(vehicleId, serviceTypes = listOf("wheel alignment")))
        repository.save(entry(vehicleId, serviceTypes = listOf("Detailing")))

        // Newest entry first; the case variant collapses onto one chip carrying the stored casing
        // of the newest row; the preset "Battery" is filtered out; no stored label is rewritten.
        assertEquals(listOf("Detailing", "wheel alignment"), repository.observeLearnedTypes().first())
    }

    @Test
    fun deletingAnEntryRemovesItsTypeRows() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val id = repository.save(entry(vehicleId, serviceTypes = listOf("Oil change", "Tire rotation")))

        repository.delete(id)

        assertTrue(
            database.serviceEntryQueries.selectTypes(id).executeAsList().isEmpty(),
            "type rows must not survive the entry",
        )
    }

    @Test
    fun deleteRemovesEntryAndChildrenAndHandsEveryPhotoPathToTheDeleter() = runTest {        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val deletedPaths = mutableListOf<String?>()
        val deletingRepository = ServiceEntryRepositoryImpl(
            database = database,
            photoDeleter = { deletedPaths += it },
        )
        val id = deletingRepository.save(
            entry(
                vehicleId = vehicleId,
                parts = listOf(ServicePart(name = "Oil filter")),
                photoPaths = listOf("service_entries/x.jpg", "service_entries/y.jpg"),
            ),
        )

        deletingRepository.delete(id)

        assertEquals(listOf<String?>("service_entries/x.jpg", "service_entries/y.jpg"), deletedPaths)
        assertNull(database.serviceEntryQueries.selectById(id).executeAsOneOrNull())
        assertTrue(database.serviceEntryQueries.selectParts(id).executeAsList().isEmpty())
        assertTrue(database.serviceEntryQueries.selectPhotos(id).executeAsList().isEmpty())
    }

    private fun entry(
        vehicleId: Long,
        dateEpochMs: Long = 1_768_867_200_000L,
        mileageKm: Long = 123_456L,
        serviceType: String = "Oil change",
        serviceTypes: List<String> = listOf(serviceType),
        costRupiah: Long? = null,
        notes: String? = null,
        workshop: String? = null,
        mechanic: String? = null,
        parts: List<ServicePart> = emptyList(),
        photoPaths: List<String> = emptyList(),
    ): ServiceEntry = ServiceEntry(
        vehicleId = vehicleId,
        dateEpochMs = dateEpochMs,
        mileageKm = mileageKm,
        serviceTypes = serviceTypes,
        costRupiah = costRupiah,
        notes = notes,
        workshop = workshop,
        mechanic = mechanic,
        parts = parts,
        photoPaths = photoPaths,
    )

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
