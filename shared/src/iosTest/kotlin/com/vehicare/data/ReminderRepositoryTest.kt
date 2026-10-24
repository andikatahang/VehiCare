package com.vehicare.data

import app.cash.sqldelight.db.SqlDriver
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleStats
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Exercises [ReminderRepositoryImpl] against a real
 * [app.cash.sqldelight.driver.native.NativeSqliteDriver] on a scratch database name, never the
 * app's real `vehicare.db`.
 *
 * Everything the screens and the delivery plan rely on is proven here on the driver both platforms
 * ship: the round trip with both bookkeeping markers intact, the D-49 creation-date rule on edit,
 * the once-per-cycle claim (D-51), the separate armed marker (D-53), the D-47 baseline pairing
 * produced by the real join, D-49's per-vehicle fallback stats, and the change hook that carries
 * the affected rule ids to the notification scheduler.
 *
 * Vehicles and service entries are seeded through [VehicleRepositoryImpl] and
 * [ServiceEntryRepositoryImpl] so the baselines are produced by the real queries rather than by
 * hand-written SQL.
 */
class ReminderRepositoryTest {

    private val databaseName = "vehicare-reminder-repo-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
    private lateinit var driver: SqlDriver
    private lateinit var database: VehiCareDatabase
    private lateinit var repository: ReminderRepositoryImpl
    private lateinit var vehicleRepository: VehicleRepositoryImpl
    private lateinit var serviceRepository: ServiceEntryRepositoryImpl

    @BeforeTest
    fun setUp() {
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
        repository = ReminderRepositoryImpl(database)
        vehicleRepository = VehicleRepositoryImpl(database)
        serviceRepository = ServiceEntryRepositoryImpl(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
        deleteTestDatabaseFiles(databaseName)
    }

    @Test
    fun saveInsertsARuleThatRoundTripsEveryFieldWithBothMarkersNull() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val expected = rule(
            vehicleId = vehicleId,
            intervalKm = 5_000L,
            intervalMonths = 6L,
            leadKm = 500L,
            leadDays = 30L,
            enabled = false,
        )

        val id = repository.save(expected)

        assertTrue(id > 0, "insert must return the generated id, was $id")
        val stored = repository.observeRule(id).first()
        assertEquals(expected.copy(id = id), stored)
        assertNull(stored?.notifiedCycleKey, "a new rule has never been notified in this cycle")
        assertNull(stored?.armedCycleKey, "a new rule has never been armed with the OS")
        assertEquals(listOf(id), repository.observeRules(vehicleId).first().map { it.id })
        assertEquals(listOf(id), repository.observeAllRules().first().map { it.id })
        assertNull(repository.observeRule(999L).first())
    }

    @Test
    fun saveOnAnExistingIdKeepsTheCreationDateAndClearsBothMarkers() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val originalCreatedAt = 1_700_000_000_000L
        val id = repository.save(rule(vehicleId, createdAtEpochMs = originalCreatedAt))

        assertTrue(repository.markNotified(id, "cycle-1"), "the first claim of a cycle must win")
        repository.markArmed(id, "cycle-1")
        assertEquals("cycle-1", repository.observeRule(id).first()?.notifiedCycleKey)
        assertEquals("cycle-1", repository.observeRule(id).first()?.armedCycleKey)

        // The edited rule carries a different creation date on purpose: the repository must ignore
        // it, because it is the time baseline for a type that has no entry yet (D-49).
        repository.save(
            rule(vehicleId, id = id, intervalKm = 7_500L, createdAtEpochMs = 1_900_000_000_000L),
        )

        val edited = repository.observeRule(id).first()
        assertEquals(7_500L, edited?.intervalKm)
        assertEquals(
            originalCreatedAt,
            edited?.createdAtEpochMs,
            "an edit must never move the time baseline (D-49)",
        )
        assertNull(edited?.notifiedCycleKey, "an edit starts a new cycle")
        assertNull(edited?.armedCycleKey, "an edit must re-arm the rule (D-51/D-53)")
    }

    @Test
    fun setEnabledClearsBothMarkersAndPersistsTheFlag() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val id = repository.save(rule(vehicleId))
        assertTrue(repository.markNotified(id, "cycle-1"))
        repository.markArmed(id, "cycle-1")

        repository.setEnabled(id, false)

        val disabled = repository.observeRule(id).first()
        assertEquals(false, disabled?.enabled)
        assertNull(disabled?.notifiedCycleKey, "disabling clears the claim")
        assertNull(disabled?.armedCycleKey, "disabling clears the armed marker")
        assertEquals(5_000L, disabled?.intervalKm, "disabling keeps the intervals (D-43)")

        repository.setEnabled(id, true)
        assertEquals(true, repository.observeRule(id).first()?.enabled)

        // A missing rule is a no-op, not a crash.
        repository.setEnabled(999L, false)
        assertNull(repository.observeRule(999L).first())
    }

    @Test
    fun markNotifiedClaimsOncePerCycleKeyAndReclaimsOnAChangedKey() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val id = repository.save(rule(vehicleId))
        val firstKey = "b1000:2000|i5000:-1|l-1:-1"
        val secondKey = "b900:2000|i5000:-1|l-1:-1"

        assertTrue(repository.markNotified(id, firstKey), "the first claim of a cycle must win")
        assertFalse(repository.markNotified(id, firstKey), "a repeat claim of the same cycle must lose")
        assertEquals(firstKey, repository.observeRule(id).first()?.notifiedCycleKey)

        assertTrue(repository.markNotified(id, secondKey), "a moved baseline re-arms the rule (D-48)")
        assertFalse(repository.markNotified(id, secondKey))
        assertEquals(secondKey, repository.observeRule(id).first()?.notifiedCycleKey)

        assertFalse(repository.markNotified(999L, firstKey), "a missing rule can never win a claim")
    }

    @Test
    fun markArmedWritesOnlyTheArmedMarkerIdempotently() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val id = repository.save(rule(vehicleId))

        repository.markArmed(id, "k1")
        assertEquals("k1", repository.observeRule(id).first()?.armedCycleKey)
        assertNull(
            repository.observeRule(id).first()?.notifiedCycleKey,
            "arming is bookkeeping and must never write the claim (T-03-17)",
        )

        repository.markArmed(id, "k1")
        assertEquals("k1", repository.observeRule(id).first()?.armedCycleKey, "a repeat is idempotent")

        assertTrue(repository.markNotified(id, "k1"))
        repository.markArmed(id, "k2")

        val row = repository.observeRule(id).first()
        assertEquals("k2", row?.armedCycleKey)
        assertEquals("k1", row?.notifiedCycleKey, "arming must never touch the claim")
    }

    @Test
    fun deleteRemovesOnlyThatRule() = runTest {
        val vehicleA = vehicleRepository.save(Vehicle(name = "A"))
        val vehicleB = vehicleRepository.save(Vehicle(name = "B"))
        val kept = repository.save(rule(vehicleA, serviceType = "Oil change"))
        val removed = repository.save(rule(vehicleA, serviceType = "Battery"))
        val untouched = repository.save(rule(vehicleB, serviceType = "Oil change"))

        repository.delete(removed)

        assertNull(repository.observeRule(removed).first())
        assertEquals(listOf(kept), repository.observeRules(vehicleA).first().map { it.id })
        assertEquals(listOf(untouched), repository.observeRules(vehicleB).first().map { it.id })

        // A missing rule is a no-op, not a crash.
        repository.delete(999L)
    }

    @Test
    fun observeTypeBaselinesPairTheLatestDateWithThatRowsMileage() = runTest {
        val vehicleA = vehicleRepository.save(Vehicle(name = "A"))
        val vehicleB = vehicleRepository.save(Vehicle(name = "B"))
        // Two entries spell the same type differently: one baseline, taken from the newer row.
        serviceRepository.save(entry(vehicleA, dateEpochMs = 1_000L, mileageKm = 500L, types = listOf("Oil change")))
        serviceRepository.save(entry(vehicleA, dateEpochMs = 3_000L, mileageKm = 900L, types = listOf("oil change")))
        // Two types on one vehicle, both carried by the same newest entry.
        serviceRepository.save(entry(vehicleA, dateEpochMs = 2_000L, mileageKm = 700L, types = listOf("Battery")))
        serviceRepository.save(
            entry(vehicleA, dateEpochMs = 4_000L, mileageKm = 1_200L, types = listOf("Battery", "Tire rotation")),
        )
        serviceRepository.save(entry(vehicleB, dateEpochMs = 5_000L, mileageKm = 2_000L, types = listOf("Battery")))

        val baselines = repository.observeTypeBaselines().first()

        assertEquals(4, baselines.size, "one row per (vehicle, lowercased label), was $baselines")
        val oilChange = baselines.single { it.vehicleId == vehicleA && it.typeLabel.lowercase() == "oil change" }
        assertEquals(3_000L, oilChange.baselineDateEpochMs)
        assertEquals(900L, oilChange.baselineKm, "date and odometer must come from the same entry (D-47)")
        val battery = baselines.single { it.vehicleId == vehicleA && it.typeLabel.lowercase() == "battery" }
        assertEquals(4_000L, battery.baselineDateEpochMs)
        assertEquals(1_200L, battery.baselineKm)
        val tireRotation = baselines.single { it.typeLabel.lowercase() == "tire rotation" }
        assertEquals(vehicleA, tireRotation.vehicleId)
        assertEquals(4_000L, tireRotation.baselineDateEpochMs)
        assertEquals(1_200L, tireRotation.baselineKm)
        val otherVehicle = baselines.single { it.vehicleId == vehicleB }
        assertEquals(5_000L, otherVehicle.baselineDateEpochMs)
        assertEquals(2_000L, otherVehicle.baselineKm, "another vehicle's baseline is never applied")
    }

    @Test
    fun observeVehicleStatsReturnsTheLatestPairPerVehicleAndNoRowWithoutEntries() = runTest {
        val withEntries = vehicleRepository.save(Vehicle(name = "A"))
        val withoutEntries = vehicleRepository.save(Vehicle(name = "B"))
        serviceRepository.save(entry(withEntries, dateEpochMs = 1_000L, mileageKm = 500L))
        serviceRepository.save(entry(withEntries, dateEpochMs = 3_000L, mileageKm = 900L))
        serviceRepository.save(entry(withEntries, dateEpochMs = 2_000L, mileageKm = 700L))

        val stats = repository.observeVehicleStats().first()

        assertEquals(1, stats.size, "a vehicle with no entries has no stats row, was $stats")
        assertEquals(
            VehicleStats(vehicleId = withEntries, lastServiceEpochMs = 3_000L, lastMileageKm = 900L),
            stats.single(),
        )
        assertTrue(stats.none { it.vehicleId == withoutEntries })
    }

    @Test
    fun onRulesChangedReceivesTheAffectedRuleIdsForEditsAndNeverForTheClaim() = runTest {
        val vehicleId = vehicleRepository.save(Vehicle(name = "A"))
        val calls = mutableListOf<Pair<Long, List<Long>>>()
        val hooked = ReminderRepositoryImpl(
            database = database,
            onRulesChanged = { id, ruleIds -> calls += id to ruleIds },
        )

        val ruleId = hooked.save(rule(vehicleId))
        assertEquals(
            listOf(vehicleId to emptyList<Long>()),
            calls,
            "an insert cancels nothing: there was no notification for this rule yet",
        )

        hooked.save(rule(vehicleId, id = ruleId, intervalKm = 7_500L))
        assertEquals(vehicleId to listOf(ruleId), calls.last(), "an edit invalidates its own notification")

        hooked.setEnabled(ruleId, false)
        assertEquals(vehicleId to listOf(ruleId), calls.last(), "a toggle invalidates it in both directions")

        val afterEdits = calls.size
        assertTrue(hooked.markNotified(ruleId, "k1"))
        hooked.markArmed(ruleId, "k1")
        assertEquals(
            afterEdits,
            calls.size,
            "the claim and the armed marker are bookkeeping, not user edits — re-scheduling on every " +
                "claim would be a wakeup loop",
        )

        hooked.delete(ruleId)
        assertEquals(vehicleId to listOf(ruleId), calls.last())
        assertEquals(afterEdits + 1, calls.size)
    }

    private fun rule(
        vehicleId: Long,
        id: Long = 0L,
        serviceType: String = "Oil change",
        intervalKm: Long? = 5_000L,
        intervalMonths: Long? = null,
        leadKm: Long? = null,
        leadDays: Long? = null,
        enabled: Boolean = true,
        createdAtEpochMs: Long = 1_700_000_000_000L,
    ): ReminderRule = ReminderRule(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        leadKm = leadKm,
        leadDays = leadDays,
        enabled = enabled,
        createdAtEpochMs = createdAtEpochMs,
        notifiedCycleKey = null,
        armedCycleKey = null,
    )

    private fun entry(
        vehicleId: Long,
        dateEpochMs: Long,
        mileageKm: Long,
        types: List<String> = listOf("Oil change"),
    ): ServiceEntry = ServiceEntry(
        vehicleId = vehicleId,
        dateEpochMs = dateEpochMs,
        mileageKm = mileageKm,
        serviceTypes = types,
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
