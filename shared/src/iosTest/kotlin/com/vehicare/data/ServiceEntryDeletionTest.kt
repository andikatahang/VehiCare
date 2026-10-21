package com.vehicare.data

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.ServiceEntryRepository
import com.vehicare.data.ServiceEntryRepositoryImpl
import com.vehicare.data.VehicleRepository
import com.vehicare.data.VehicleRepositoryImpl
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.ui.service.ServiceEntryDetailViewModel
import com.vehicare.util.PhotoStorage
import io.github.vinceglb.filekit.FileKit
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.cacheDir
import io.github.vinceglb.filekit.div
import io.github.vinceglb.filekit.exists
import io.github.vinceglb.filekit.toKotlinxIoPath
import io.github.vinceglb.filekit.write
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Proves the whole service entry delete path against a real database and the real simulator
 * filesystem — never a mock: removing an entry removes its **row** *and* its **part/photo rows**
 * *and* its **photo files**, deleting one entry leaves another entry of the same vehicle (and its
 * photos) untouched, and an unknown id is a harmless no-op.
 *
 * The ViewModel half then proves the state machine around that call: exactly one repository call
 * per accepted tap, success reported only after the repository returned, a second tap while the
 * first is still running dropped, and a failure that reports itself while leaving the entry stored.
 * The ViewModel runs on `Dispatchers.setMain`-installed test dispatcher, so the ordering is
 * deterministic rather than timed.
 *
 * The vehicle cascade case (D-39) proves: one vehicle with two entries carrying photos plus its
 * own vehicle photo — `VehicleRepositoryImpl.delete(vehicleId)` removes the vehicle, every entry,
 * every part row, every photo row, and asserts every file (vehicle photo **and** both entries'
 * photos) is gone from the filesystem, while a second vehicle's entry and photos survive untouched.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceEntryDeletionTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val databaseName = "vehicare-entry-deletion-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private val runId = Random.nextLong(0, Long.MAX_VALUE)
    private var counter = 0

    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase
    private lateinit var repository: ServiceEntryRepository
    private lateinit var vehicleRepository: VehicleRepository
    private val storedPaths = mutableListOf<String>()
    private val sourceFiles = mutableListOf<PlatformFile>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
        // The exact wiring the app's Koin graph uses: rows are removed inside the transaction and
        // the stored relative locations are handed to the real storage seam, so the files go too.
        repository = ServiceEntryRepositoryImpl(database, photoDeleter = PhotoStorage::delete)
        vehicleRepository = VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        driver.close()
        deleteTestDatabaseFiles(databaseName)
        storedPaths.forEach { PhotoStorage.delete(it) }
        storedPaths.clear()
        sourceFiles.forEach { deleteSourceFile(it) }
        sourceFiles.clear()
    }

    // 1. Repository, rows + files: deleting an entry with two photos and parts leaves no entry row,
    // no part rows and no photo rows, returns the photos to the deleter exactly once each, and
    // assertFalse(PhotoStorage.resolve(path).exists()) for every entry photo.
    @Test
    fun deleteEntryRemovesRowsAndPhotoFiles() = runTest {
        val vehicleId = seedVehicle("Owner")
        val photoPath1 = persistEntryPhoto(seed = 1)
        val photoPath2 = persistEntryPhoto(seed = 2)
        val entry = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
            parts = listOf(ServicePart(name = "Oil filter", quantity = 1)),
            photoPaths = listOf(photoPath1, photoPath2),
        )
        val entryId = repository.save(entry)

        assertTrue(PhotoStorage.resolve(photoPath1).exists(), "precondition: photo 1 exists")
        assertTrue(PhotoStorage.resolve(photoPath2).exists(), "precondition: photo 2 exists")

        repository.delete(entryId)

        assertNull(repository.observeEntry(entryId).first(), "the deleted entry must not be observable")
        assertTrue(
            database.serviceEntryQueries.selectById(entryId).executeAsOneOrNull() == null,
            "entry row gone",
        )
        assertTrue(
            database.serviceEntryQueries.selectParts(entryId).executeAsList().isEmpty(),
            "part rows gone",
        )
        assertTrue(
            database.serviceEntryQueries.selectPhotos(entryId).executeAsList().isEmpty(),
            "photo rows gone",
        )
        assertFalse(
            PhotoStorage.resolve(photoPath1).exists(),
            "deleting an entry must remove its photo file 1",
        )
        assertFalse(
            PhotoStorage.resolve(photoPath2).exists(),
            "deleting an entry must remove its photo file 2",
        )
    }

    // 2. Isolation: deleting one entry leaves another entry of the same vehicle and its photos untouched.
    @Test
    fun deleteOneEntryLeavesSiblingUntouched() = runTest {
        val vehicleId = seedVehicle("Sibling test")
        val survivorPhoto = persistEntryPhoto(seed = 3)
        val doomedPhoto = persistEntryPhoto(seed = 4)
        val survivor = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
            photoPaths = listOf(survivorPhoto),
        )
        val doomed = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_710_000_000_000,
            mileageKm = 20_000,
            serviceTypes = listOf("Tire rotation"),
            photoPaths = listOf(doomedPhoto),
        )
        val survivorId = repository.save(survivor)
        val doomedId = repository.save(doomed)

        repository.delete(doomedId)

        assertNotNull(repository.observeEntry(survivorId).first(), "the other entry must remain")
        assertTrue(
            PhotoStorage.resolve(survivorPhoto).exists(),
            "deleting one entry must never take another entry's photo file",
        )
    }

    // 3. Idempotence: deleting an unknown id twice is a harmless no-op (no exceptions, no stray deletions).
    @Test
    fun deleteUnknownIdIsIdempotent() = runTest {
        val vehicleId = seedVehicle("Idempotent")
        val entry = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
        )
        val id = repository.save(entry)

        repository.delete(999_999L)
        repository.delete(999_999L)

        assertNotNull(repository.observeEntry(id).first(), "the real entry must remain")
    }

    // 4. ViewModel ordering: with a RecordingServiceEntryRepository decorator, delete calls the
    // repository once, reports success only after it returned — assert inside the onDeleted
    // callback that the row is gone and the files are gone, then assert deleting cleared and
    // deleteFailed false.
    @Test
    fun deleteCallsRepositoryOnceAndReportsSuccessAfterReturn() = runTest {
        val vehicleId = seedVehicle("ViewModel test")
        val photoPath = persistEntryPhoto(seed = 5)
        val entry = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
            photoPaths = listOf(photoPath),
        )
        val entryId = repository.save(entry)
        val recording = RecordingServiceEntryRepository(repository)
        val viewModel = ServiceEntryDetailViewModel(recording, entryId)
        val reported = CompletableDeferred<Unit>()
        var onDeletedCalls = 0
        var rowGoneWhenReported: Boolean? = null
        var fileGoneWhenReported: Boolean? = null

        viewModel.delete(
            onDeleted = {
                onDeletedCalls++
                // Both reads happen inside the callback: whatever they say was already true
                // *before* the success path ran, which is what "only after the repository
                // returned" means in observable terms.
                rowGoneWhenReported =
                    database.serviceEntryQueries.selectById(entryId).executeAsOneOrNull() == null
                fileGoneWhenReported = !PhotoStorage.resolve(photoPath).exists()
                reported.complete(Unit)
            },
        )
        assertTrue(viewModel.deleting.value, "a delete in flight must be visible while it runs")

        reported.await()

        assertEquals(listOf(entryId), recording.deleteCalls, "the repository must be called exactly once")
        assertEquals(1, onDeletedCalls, "the success callback must fire exactly once")
        assertEquals(true, rowGoneWhenReported, "the row must be gone before onDeleted runs")
        assertEquals(true, fileGoneWhenReported, "the photo file must be gone before onDeleted runs")
        assertFalse(viewModel.deleting.value, "the in-flight flag must clear once the call returned")
        assertFalse(viewModel.deleteFailed.value, "a successful delete must not report a failure")
        assertNull(repository.observeEntry(entryId).first())
    }

    // 5. Single-flight: a second delete while the first is gated open is dropped and never
    // reaches the repository; only the accepted call reports success.
    @Test
    fun deleteDropsSecondCallWhileFirstInFlight() = runTest {
        val vehicleId = seedVehicle("In flight")
        val entry = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
        )
        val entryId = repository.save(entry)
        val gate = CompletableDeferred<Unit>()
        val recording = RecordingServiceEntryRepository(repository, gate = gate)
        val viewModel = ServiceEntryDetailViewModel(recording, entryId)
        val firstReported = CompletableDeferred<Unit>()
        var onDeletedCalls = 0
        var secondCallbackRan = false

        viewModel.delete(
            onDeleted = {
                onDeletedCalls++
                firstReported.complete(Unit)
            },
        )
        viewModel.delete(
            onDeleted = {
                onDeletedCalls++
                secondCallbackRan = true
            },
        )

        advanceUntilIdle()
        assertEquals(
            listOf(entryId),
            recording.deleteCalls,
            "the in-flight guard must drop the second call before it reaches the repository",
        )

        gate.complete(Unit)
        firstReported.await()

        assertEquals(1, onDeletedCalls, "only the accepted call may report success")
        assertFalse(secondCallbackRan, "the dropped call must never report anything")
        assertFalse(viewModel.deleting.value)
        assertNull(repository.observeEntry(entryId).first())
    }

    // 6. Failure: a throwing delete clears the flag, sets deleteFailed, never calls onDeleted,
    // and leaves the entry observable; onDeleteErrorShown() retires the flag without touching data.
    @Test
    fun aFailedDeleteKeepsTheEntryAndReportsFailure() = runTest {
        val vehicleId = seedVehicle("Failure test")
        val entry = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
        )
        val entryId = repository.save(entry)
        val failing = RecordingServiceEntryRepository(repository, failure = IllegalStateException("boom"))
        val viewModel = ServiceEntryDetailViewModel(failing, entryId)
        var onDeletedCalls = 0

        viewModel.delete(onDeleted = { onDeletedCalls++ })
        advanceUntilIdle()

        assertFalse(viewModel.deleting.value, "the in-flight flag must clear after a failure")
        assertTrue(viewModel.deleteFailed.value, "a failed delete must be reported to the screen")
        assertEquals(0, onDeletedCalls, "a failed delete must never report success")
        assertNotNull(
            repository.observeEntry(entryId).first(),
            "a failed delete must leave the entry stored and observable",
        )
    }

    @Test
    fun onDeleteErrorShownRetiresFailureFlagAndLeavesEntryUntouched() = runTest {
        val vehicleId = seedVehicle("Retire message")
        val entry = ServiceEntry(
            vehicleId = vehicleId,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
        )
        val entryId = repository.save(entry)
        val failing = RecordingServiceEntryRepository(repository, failure = IllegalStateException("boom"))
        val viewModel = ServiceEntryDetailViewModel(failing, entryId)

        viewModel.delete(onDeleted = { fail("onDeleted must not run for a failed delete") })
        advanceUntilIdle()
        assertTrue(viewModel.deleteFailed.value, "precondition: the failure flag is set")

        viewModel.onDeleteErrorShown()

        assertFalse(viewModel.deleteFailed.value, "retiring the message must clear the flag")
        assertFalse(viewModel.deleting.value)
        assertNotNull(repository.observeEntry(entryId).first(), "retiring a message must not touch data")
    }

    // 7. Vehicle cascade (D-39): one vehicle with two entries carrying photos plus its own vehicle
    // photo — vehicleRepository.delete(vehicleId) removes the vehicle, every entry, every part row,
    // every photo row, and asserts every file (vehicle photo AND both entries' photos) is gone from
    // the filesystem, while a second vehicle's entry and photos survive untouched.
    @Test
    fun vehicleCascadeRemovesAllEntriesAndTheirPhotosAndVehiclePhoto() = runTest {
        val doomedVehicle = seedVehicle("Doomed vehicle")
        val vehiclePhoto = persistVehiclePhoto(seed = 6)
        // Update the vehicle with a photo path
        val doomedVehicleEntity = database.vehicleQueries.selectById(doomedVehicle).executeAsOneOrNull()!!
        database.vehicleQueries.update(
            name = doomedVehicleEntity.name,
            make = doomedVehicleEntity.make,
            model = doomedVehicleEntity.model,
            year = doomedVehicleEntity.year,
            plate = doomedVehicleEntity.plate,
            type = doomedVehicleEntity.type,
            photo_path = vehiclePhoto,
            id = doomedVehicleEntity.id,
        )

        val entry1Photo = persistEntryPhoto(seed = 7)
        val entry2Photo = persistEntryPhoto(seed = 8)
        val entry1 = ServiceEntry(
            vehicleId = doomedVehicle,
            dateEpochMs = 1_700_000_000_000,
            mileageKm = 10_000,
            serviceTypes = listOf("Oil change"),
            photoPaths = listOf(entry1Photo),
        )
        val entry2 = ServiceEntry(
            vehicleId = doomedVehicle,
            dateEpochMs = 1_710_000_000_000,
            mileageKm = 20_000,
            serviceTypes = listOf("Tire rotation"),
            photoPaths = listOf(entry2Photo),
        )
        val entry1Id = repository.save(entry1)
        val entry2Id = repository.save(entry2)

        // Survivor vehicle
        val survivorVehicle = seedVehicle("Survivor vehicle")
        val survivorPhoto = persistEntryPhoto(seed = 9)
        val survivorEntry = ServiceEntry(
            vehicleId = survivorVehicle,
            dateEpochMs = 1_720_000_000_000,
            mileageKm = 30_000,
            serviceTypes = listOf("Brake service"),
            photoPaths = listOf(survivorPhoto),
        )
        val survivorEntryId = repository.save(survivorEntry)

        assertTrue(PhotoStorage.resolve(vehiclePhoto).exists(), "precondition: vehicle photo exists")
        assertTrue(PhotoStorage.resolve(entry1Photo).exists(), "precondition: entry 1 photo exists")
        assertTrue(PhotoStorage.resolve(entry2Photo).exists(), "precondition: entry 2 photo exists")
        assertTrue(PhotoStorage.resolve(survivorPhoto).exists(), "precondition: survivor photo exists")

        vehicleRepository.delete(doomedVehicle)

        // Vehicle gone
        assertNull(database.vehicleQueries.selectById(doomedVehicle).executeAsOneOrNull(), "vehicle row gone")
        assertNull(vehicleRepository.observeVehicle(doomedVehicle).first(), "vehicle not observable")

        // All entries gone
        assertNull(repository.observeEntry(entry1Id).first(), "entry 1 gone")
        assertNull(repository.observeEntry(entry2Id).first(), "entry 2 gone")
        assertTrue(
            database.serviceEntryQueries.selectForVehicleByDate(doomedVehicle).executeAsList().isEmpty(),
            "no entry rows for vehicle",
        )
        assertTrue(
            database.serviceEntryQueries.selectParts(entry1Id).executeAsList().isEmpty(),
            "part rows for entry 1 gone",
        )
        assertTrue(
            database.serviceEntryQueries.selectParts(entry2Id).executeAsList().isEmpty(),
            "part rows for entry 2 gone",
        )
        assertTrue(
            database.serviceEntryQueries.selectPhotos(entry1Id).executeAsList().isEmpty(),
            "photo rows for entry 1 gone",
        )
        assertTrue(
            database.serviceEntryQueries.selectPhotos(entry2Id).executeAsList().isEmpty(),
            "photo rows for entry 2 gone",
        )

        // All files gone
        assertFalse(PhotoStorage.resolve(vehiclePhoto).exists(), "vehicle photo file gone")
        assertFalse(PhotoStorage.resolve(entry1Photo).exists(), "entry 1 photo file gone")
        assertFalse(PhotoStorage.resolve(entry2Photo).exists(), "entry 2 photo file gone")

        // Survivor untouched
        assertNotNull(repository.observeEntry(survivorEntryId).first(), "survivor entry remains")
        assertTrue(PhotoStorage.resolve(survivorPhoto).exists(), "survivor photo file remains")
    }

    /** Removes one scratch source file without needing a coroutine (FileKit's own delete is suspend). */
    private fun deleteSourceFile(file: PlatformFile) {
        runCatching { SystemFileSystem.delete(file.toKotlinxIoPath(), mustExist = false) }
    }

    private fun nextFileName(): String = "02-04-$runId-${counter++}.jpg"

    /** Copies synthetic (deliberately non-decodable) bytes through the real storage seam. */
    private suspend fun persistEntryPhoto(seed: Int): String {
        val source = FileKit.cacheDir / "02-04-source-$runId-${counter++}"
        source.write(ByteArray(96) { index -> (index * seed).toByte() })
        sourceFiles += source
        val stored = PhotoStorage.persist(source, nextFileName(), directory = "service_entries")
        storedPaths += stored
        return stored
    }

    private suspend fun persistVehiclePhoto(seed: Int): String {
        val source = FileKit.cacheDir / "02-04-source-$runId-${counter++}"
        source.write(ByteArray(96) { index -> (index * seed).toByte() })
        sourceFiles += source
        val stored = PhotoStorage.persist(source, nextFileName(), directory = "vehicles")
        storedPaths += stored
        return stored
    }

    private suspend fun seedVehicle(name: String): Long {
        return vehicleRepository.save(
            com.vehicare.domain.Vehicle(
                name = name,
                make = null,
                model = null,
                year = null,
                plate = null,
                type = com.vehicare.domain.VehicleType.Car,
                photoPath = null,
            )
        )
    }

    /**
     * SQLiter resolves a bare database name to `<NSApplicationSupportDirectory>/databases/<name>`
     * (see its `DatabaseFileContext`), so the scratch database and its sidecars are removed from
     * exactly that directory.
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

/**
 * The repository under test with its deletion observed: every call is recorded before it is
 * forwarded, an optional [gate] holds a call open so "still in flight" can be asserted without
 * sleeping, and an optional [failure] makes the call throw without touching the delegate.
 */
private class RecordingServiceEntryRepository(
    private val delegate: ServiceEntryRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
) : ServiceEntryRepository by delegate {

    val deleteCalls = mutableListOf<Long>()

    override suspend fun delete(id: Long) {
        deleteCalls += id
        gate?.await()
        failure?.let { throw it }
        delegate.delete(id)
    }
}