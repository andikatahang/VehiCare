package com.vehicare.ui.vehicles

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.VehicleRepository
import com.vehicare.data.VehicleRepositoryImpl
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.Vehicle
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
 * Proves the whole delete path against a real database and the real simulator filesystem — never a
 * mock: removing a vehicle removes its **row** *and* its **photo file**, deleting one vehicle
 * leaves every other vehicle (and its photo) untouched, and an unknown id is a harmless no-op.
 *
 * That second half is the obligation plan 01-03 left open when it introduced the repository's
 * photo-deletion seam: a `delete()` that only dropped the row would leave the user's personal
 * image on disk forever (D-07, RESEARCH Pitfall 4). The assertion here is therefore on the file's
 * existence, not on the fact that some callback was wired.
 *
 * The ViewModel half then proves the state machine around that call: exactly one repository call
 * per accepted tap, success reported only after the repository returned, a second tap while the
 * first is still running dropped, and a failure that reports itself while leaving the vehicle
 * stored. The ViewModel runs on `Dispatchers.setMain`-installed test dispatcher, so the ordering
 * is deterministic rather than timed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleDeletionTest {

    private val mainDispatcher = StandardTestDispatcher()
    private val databaseName = "vehicare-deletion-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private val runId = Random.nextLong(0, Long.MAX_VALUE)
    private var counter = 0

    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase
    private lateinit var repository: VehicleRepository
    private val storedPaths = mutableListOf<String>()
    private val sourceFiles = mutableListOf<PlatformFile>()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
        // The exact wiring the app's Koin graph uses: the row is removed inside the transaction and
        // the stored relative location is handed to the real storage seam, so the file goes too.
        repository = VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)
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

    @Test
    fun deleteRemovesTheRowAndTheVehiclePhotoFile() = runTest {
        val relativePath = persistPhoto(seed = 1)
        val id = repository.save(Vehicle(name = "Photo owner", photoPath = relativePath))
        assertTrue(PhotoStorage.resolve(relativePath).exists(), "precondition: the photo file exists")

        repository.delete(id)

        assertNull(repository.observeVehicle(id).first(), "the deleted row must not be observable")
        assertTrue(
            repository.observeVehicles().first().none { it.id == id },
            "the deleted vehicle must be absent from the list flow",
        )
        assertFalse(
            PhotoStorage.resolve(relativePath).exists(),
            "deleting a vehicle must remove its photo file too (D-07, Pitfall 4)",
        )
    }

    @Test
    fun deleteRemovesAVehicleWithoutAPhoto() = runTest {
        val id = repository.save(Vehicle(name = "No photo"))

        repository.delete(id)

        assertNull(repository.observeVehicle(id).first())
        assertTrue(repository.observeVehicles().first().isEmpty())
    }

    @Test
    fun deleteOfAnUnknownIdIsIdempotent() = runTest {
        val id = repository.save(Vehicle(name = "Survivor"))

        repository.delete(999_999L)
        repository.delete(999_999L)

        assertEquals(listOf(id), repository.observeVehicles().first().map { it.id })
    }

    @Test
    fun deleteLeavesAnotherVehiclesRowAndPhotoUntouched() = runTest {
        val survivorPath = persistPhoto(seed = 2)
        val doomedPath = persistPhoto(seed = 3)
        val survivor = repository.save(Vehicle(name = "Survivor", photoPath = survivorPath))
        val doomed = repository.save(Vehicle(name = "Doomed", photoPath = doomedPath))

        repository.delete(doomed)

        assertNotNull(repository.observeVehicle(survivor).first(), "the other row must remain")
        assertTrue(
            PhotoStorage.resolve(survivorPath).exists(),
            "deleting one vehicle must never take another vehicle's photo file",
        )
    }

    @Test
    fun deleteCallsTheRepositoryOnceAndReportsSuccessOnlyAfterItReturned() = runTest {
        val relativePath = persistPhoto(seed = 4)
        val id = repository.save(Vehicle(name = "Deleted with photo", photoPath = relativePath))
        val recording = RecordingVehicleRepository(repository)
        val viewModel = VehicleDetailViewModel(recording, id)
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
                    database.vehicleQueries.selectById(id).executeAsOneOrNull() == null
                fileGoneWhenReported = !PhotoStorage.resolve(relativePath).exists()
                reported.complete(Unit)
            },
        )
        assertTrue(viewModel.deleting.value, "a delete in flight must be visible while it runs")

        reported.await()

        assertEquals(listOf(id), recording.deleteCalls, "the repository must be called exactly once")
        assertEquals(1, onDeletedCalls, "the success callback must fire exactly once")
        assertEquals(true, rowGoneWhenReported, "the row must be gone before onDeleted runs")
        assertEquals(true, fileGoneWhenReported, "the photo file must be gone before onDeleted runs")
        assertFalse(viewModel.deleting.value, "the in-flight flag must clear once the call returned")
        assertFalse(viewModel.deleteFailed.value, "a successful delete must not report a failure")
        assertNull(repository.observeVehicle(id).first())
    }

    @Test
    fun deleteDropsASecondCallWhileTheFirstIsStillInFlight() = runTest {
        val id = repository.save(Vehicle(name = "In flight"))
        val gate = CompletableDeferred<Unit>()
        val recording = RecordingVehicleRepository(repository, gate = gate)
        val viewModel = VehicleDetailViewModel(recording, id)
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
            listOf(id),
            recording.deleteCalls,
            "the in-flight guard must drop the second call before it reaches the repository",
        )

        gate.complete(Unit)
        firstReported.await()

        assertEquals(1, onDeletedCalls, "only the accepted call may report success")
        assertFalse(secondCallbackRan, "the dropped call must never report anything")
        assertFalse(viewModel.deleting.value)
        assertNull(repository.observeVehicle(id).first())
    }

    @Test
    fun aFailedDeleteKeepsTheVehicleAndReportsTheFailure() = runTest {
        val id = repository.save(Vehicle(name = "Still here"))
        val failing = RecordingVehicleRepository(repository, failure = IllegalStateException("boom"))
        val viewModel = VehicleDetailViewModel(failing, id)
        var onDeletedCalls = 0

        viewModel.delete(onDeleted = { onDeletedCalls++ })
        advanceUntilIdle()

        assertFalse(viewModel.deleting.value, "the in-flight flag must clear after a failure")
        assertTrue(viewModel.deleteFailed.value, "a failed delete must be reported to the screen")
        assertEquals(0, onDeletedCalls, "a failed delete must never report success")
        assertNotNull(
            repository.observeVehicle(id).first(),
            "a failed delete must leave the vehicle stored and observable",
        )
    }

    @Test
    fun onDeleteErrorShownRetiresTheFailureFlagAndLeavesTheVehicleUntouched() = runTest {
        val id = repository.save(Vehicle(name = "Retired message"))
        val failing = RecordingVehicleRepository(repository, failure = IllegalStateException("boom"))
        val viewModel = VehicleDetailViewModel(failing, id)

        viewModel.delete(onDeleted = { fail("onDeleted must not run for a failed delete") })
        advanceUntilIdle()
        assertTrue(viewModel.deleteFailed.value, "precondition: the failure flag is set")

        viewModel.onDeleteErrorShown()

        assertFalse(viewModel.deleteFailed.value, "retiring the message must clear the flag")
        assertFalse(viewModel.deleting.value)
        assertNotNull(repository.observeVehicle(id).first(), "retiring a message must not touch data")
    }

    /** Removes one scratch source file without needing a coroutine (FileKit's own delete is suspend). */
    private fun deleteSourceFile(file: PlatformFile) {
        runCatching { SystemFileSystem.delete(file.toKotlinxIoPath(), mustExist = false) }
    }

    private fun nextFileName(): String = "01-10-$runId-${counter++}.jpg"

    /** Copies synthetic (deliberately non-decodable) bytes through the real storage seam. */
    private suspend fun persistPhoto(seed: Int): String {
        val source = FileKit.cacheDir / "01-10-source-$runId-${counter++}"
        source.write(ByteArray(96) { index -> (index * seed).toByte() })
        sourceFiles += source
        val stored = PhotoStorage.persist(source, nextFileName())
        storedPaths += stored
        return stored
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
private class RecordingVehicleRepository(
    private val delegate: VehicleRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
) : VehicleRepository by delegate {

    val deleteCalls = mutableListOf<Long>()

    override suspend fun delete(id: Long) {
        deleteCalls += id
        gate?.await()
        failure?.let { throw it }
        delegate.delete(id)
    }
}
