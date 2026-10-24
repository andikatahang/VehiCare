package com.vehicare.ui.vehicles

import com.vehicare.data.ReminderRepository
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderEvaluation
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleStats
import com.vehicare.domain.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Tests for [VehicleListViewModel] — the four flat reads, the single evaluation join, the
 * urgency-ordered dashboard and [dashboardDueCount] (D-57, DSH-01).
 *
 * Both repositories are hand-written fakes mirroring `RemindersViewModelTest`'s style: rules,
 * baselines and stats are the same flat shapes the real `ReminderRepository` exposes, so the
 * ViewModel's own combine/join/order pipeline is exercised rather than mocked.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleListViewModelTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun noVehiclesIsEmpty() = runTest {
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(emptyList())),
            FakeReminderRepository(),
        )
        val states = collect(viewModel)

        assertEquals(VehicleListUiState.Empty, states.last())
    }

    @Test
    fun oneVehicleWithNoRulesHasNoTopAndNoOtherDueButCarriesItsStats() = runTest {
        val vehicle = vehicle(id = 1L)
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(vehicle))),
            FakeReminderRepository(
                stats = listOf(stats(vehicleId = 1L, lastServiceEpochMs = SERVICE_EPOCH_MS)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        val row = content.items.single()
        assertEquals(1L, row.vehicle.id)
        assertNull(row.top)
        assertEquals(0, row.otherDueCount)
        assertEquals(SERVICE_EPOCH_MS, row.lastServiceEpochMs)
    }

    @Test
    fun aVehicleWithNoServiceEntryHasNullLastServiceEpoch() = runTest {
        val vehicle = vehicle(id = 1L)
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(vehicle))),
            FakeReminderRepository(stats = emptyList()),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        assertNull(content.items.single().lastServiceEpochMs)
    }

    @Test
    fun anOverdueVehicleSortsBeforeAnUpcomingOneRegardlessOfInputOrder() = runTest {
        val overdueVehicle = vehicle(id = 1L, name = "Older")
        val upcomingVehicle = vehicle(id = 2L, name = "Newer")
        val overdueRule = rule(id = 10L, vehicleId = 1L, intervalKm = 1_000L)
        val upcomingRule = rule(id = 20L, vehicleId = 2L, intervalKm = 10_000L)
        val viewModel = VehicleListViewModel(
            // Input order is deliberately upcoming-then-overdue to prove the ViewModel's own
            // ordering, not the fake's input order, decides the result.
            FakeVehicleRepository(vehicles = flowOf(listOf(upcomingVehicle, overdueVehicle))),
            FakeReminderRepository(
                rules = flowOf(listOf(upcomingRule, overdueRule)),
                baselines = listOf(baseline(vehicleId = 1L, km = 10_000L)),
                stats = listOf(
                    stats(vehicleId = 1L, lastMileageKm = 15_000L),
                    stats(vehicleId = 2L, lastMileageKm = 1_000L),
                ),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        assertEquals(listOf(1L, 2L), content.items.map { it.vehicle.id })
        assertIs<DueState.Overdue>(content.items.first().top?.evaluation?.state)
        assertIs<DueState.Upcoming>(content.items.last().top?.evaluation?.state)
    }

    @Test
    fun anEnabledUpcomingRuleSortsBeforeAVehicleWithNoRule() = runTest {
        val upcomingVehicle = vehicle(id = 1L)
        val noRuleVehicle = vehicle(id = 2L)
        val upcomingRule = rule(id = 10L, vehicleId = 1L, intervalKm = 10_000L)
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(noRuleVehicle, upcomingVehicle))),
            FakeReminderRepository(
                rules = flowOf(listOf(upcomingRule)),
                stats = listOf(stats(vehicleId = 1L, lastMileageKm = 1_000L)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        assertEquals(listOf(1L, 2L), content.items.map { it.vehicle.id })
    }

    @Test
    fun aDisabledRuleProducesNoTopWhileTheRuleStillExists() = runTest {
        val target = vehicle(id = 1L)
        val disabledRule = rule(id = 10L, vehicleId = 1L, intervalKm = 1_000L, enabled = false)
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(target))),
            FakeReminderRepository(rules = flowOf(listOf(disabledRule))),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        assertNull(content.items.single().top, "a disabled rule has no due state (D-43)")
    }

    @Test
    fun twoDueRulesOnOneVehicleCountOneOtherDueAndTheMoreUrgentIsTop() = runTest {
        val target = vehicle(id = 1L)
        // Overdue by 4,000 km — the most urgent, so it is the displayed rule. The vehicle's
        // current mileage is shared by every rule below (one VehicleStats row per vehicle), so
        // each rule needs its own service-type baseline to land on a different due state.
        val mostUrgent = rule(id = 10L, vehicleId = 1L, serviceType = "Oil change", intervalKm = 1_000L)
        // Due soon with a lead window — the vehicle's other due rule.
        val dueSoon = rule(
            id = 20L,
            vehicleId = 1L,
            serviceType = "Tire rotation",
            intervalKm = 10_000L,
            leadKm = 500L,
        )
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(target))),
            FakeReminderRepository(
                rules = flowOf(listOf(mostUrgent, dueSoon)),
                baselines = listOf(
                    baseline(vehicleId = 1L, typeLabel = "Oil change", km = 10_000L),
                    baseline(vehicleId = 1L, typeLabel = "Tire rotation", km = 5_300L),
                ),
                stats = listOf(stats(vehicleId = 1L, lastMileageKm = 15_000L)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        val row = content.items.single()
        assertEquals(10L, row.top?.rule?.id)
        assertIs<DueState.Overdue>(row.top?.evaluation?.state)
        assertEquals(1, row.otherDueCount)
    }

    @Test
    fun aThirdDueRuleRaisesOtherDueCountToTwo() = runTest {
        val target = vehicle(id = 1L)
        val mostUrgent = rule(id = 10L, vehicleId = 1L, serviceType = "Oil change", intervalKm = 1_000L)
        val dueSoonA = rule(
            id = 20L,
            vehicleId = 1L,
            serviceType = "Tire rotation",
            intervalKm = 10_000L,
            leadKm = 500L,
        )
        val dueSoonB = rule(
            id = 30L,
            vehicleId = 1L,
            serviceType = "Brake pads",
            intervalKm = 10_000L,
            leadKm = 500L,
        )
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(target))),
            FakeReminderRepository(
                rules = flowOf(listOf(mostUrgent, dueSoonA, dueSoonB)),
                baselines = listOf(
                    baseline(vehicleId = 1L, typeLabel = "Oil change", km = 10_000L),
                    baseline(vehicleId = 1L, typeLabel = "Tire rotation", km = 5_300L),
                    baseline(vehicleId = 1L, typeLabel = "Brake pads", km = 5_200L),
                ),
                stats = listOf(stats(vehicleId = 1L, lastMileageKm = 15_000L)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        val row = content.items.single()
        assertEquals(10L, row.top?.rule?.id)
        assertEquals(2, row.otherDueCount)
    }

    @Test
    fun anUnevaluableRuleNeverCountsAsOtherDue() = runTest {
        val target = vehicle(id = 1L)
        val mostUrgent = rule(id = 10L, vehicleId = 1L, intervalKm = 1_000L)
        // A second, distance-only rule with no odometer anywhere (no baseline, no stats) — it
        // evaluates to NoMileageData and must never count.
        val unevaluable = rule(id = 20L, vehicleId = 1L, intervalKm = 5_000L)
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(target))),
            FakeReminderRepository(
                rules = flowOf(listOf(mostUrgent, unevaluable)),
                stats = emptyList(),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        val row = content.items.single()
        assertEquals(DueState.NoMileageData, row.top?.evaluation?.state)
        assertEquals(0, row.otherDueCount)
    }

    @Test
    fun vehiclesWithNoActiveRuleKeepNewestFirstOrderAmongThemselves() = runTest {
        val older = vehicle(id = 1L)
        val newer = vehicle(id = 2L)
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flowOf(listOf(older, newer))),
            FakeReminderRepository(),
        )
        val states = collect(viewModel)

        val content = assertIs<VehicleListUiState.Content>(states.last())
        assertEquals(listOf(2L, 1L), content.items.map { it.vehicle.id })
    }

    @Test
    fun dashboardDueCountIgnoresTheDisplayedRuleAndDisabledRows() {
        val displayed = reminderRow(ruleId = 1L, state = DueState.Overdue(kmOver = 100L, daysOver = null))
        val otherDue = reminderRow(ruleId = 2L, state = DueState.DueNow)
        val disabledDue = reminderRow(
            ruleId = 3L,
            state = DueState.Overdue(kmOver = 50L, daysOver = null),
            enabled = false,
        )
        val upcoming = reminderRow(ruleId = 4L, state = DueState.Upcoming(kmLeft = 10L, dueDate = null))
        val noMileage = reminderRow(ruleId = 5L, state = DueState.NoMileageData)

        val count = dashboardDueCount(
            rows = listOf(displayed, otherDue, disabledDue, upcoming, noMileage),
            displayedRuleId = 1L,
        )

        assertEquals(1, count, "only the enabled, non-displayed, due-or-overdue row counts")
    }

    @Test
    fun aFailingVehicleFlowProducesError() = runTest {
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flow { throw IllegalStateException("boom") }),
            FakeReminderRepository(),
        )
        val states = collect(viewModel)

        val error = assertIs<VehicleListUiState.Error>(states.last())
        assertEquals("boom", error.message)
    }

    @Test
    fun retryReCollectsAndRecoversAfterAnError() = runTest {
        var attempts = 0
        val flakyVehicles = flow {
            attempts++
            if (attempts == 1) throw IllegalStateException("boom")
            emit(listOf(vehicle(id = 1L)))
        }
        val viewModel = VehicleListViewModel(
            FakeVehicleRepository(vehicles = flakyVehicles),
            FakeReminderRepository(),
        )
        val states = collect(viewModel)

        assertIs<VehicleListUiState.Error>(states.last())
        assertEquals(1, attempts)

        viewModel.retry()
        advanceUntilIdle()

        val content = assertIs<VehicleListUiState.Content>(states.last())
        assertEquals(listOf(1L), content.items.map { it.vehicle.id })
        assertEquals(2, attempts, "retry must re-run the four flat reads")
    }

    /**
     * Collects [viewModel]'s state on the test scheduler and returns the emission log.
     *
     * `WhileSubscribed` means the upstream only starts with a live collector, so every test needs
     * one; the job is cancelled by the caller's test scope when the body ends.
     */
    private fun TestScope.collect(
        viewModel: VehicleListViewModel,
    ): MutableList<VehicleListUiState> {
        val states = mutableListOf<VehicleListUiState>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        advanceUntilIdle()
        return states
    }

    private companion object {
        const val SERVICE_EPOCH_MS = 1_700_000_000_000L
        const val BASELINE_EPOCH_MS = 1_700_000_000_000L

        fun vehicle(id: Long, name: String = "Vehicle $id"): Vehicle = Vehicle(
            id = id,
            name = name,
            type = VehicleType.Car,
        )

        fun rule(
            id: Long,
            vehicleId: Long,
            serviceType: String = "Oil change",
            intervalKm: Long? = 1_000L,
            intervalMonths: Long? = null,
            leadKm: Long? = null,
            leadDays: Long? = null,
            enabled: Boolean = true,
            createdAtEpochMs: Long = BASELINE_EPOCH_MS,
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

        fun baseline(vehicleId: Long, km: Long, typeLabel: String = "Oil change"): TypeBaseline =
            TypeBaseline(
                vehicleId = vehicleId,
                typeLabel = typeLabel,
                baselineDateEpochMs = BASELINE_EPOCH_MS,
                baselineKm = km,
            )

        fun stats(
            vehicleId: Long,
            lastServiceEpochMs: Long? = SERVICE_EPOCH_MS,
            lastMileageKm: Long? = null,
        ): VehicleStats = VehicleStats(
            vehicleId = vehicleId,
            lastServiceEpochMs = lastServiceEpochMs,
            lastMileageKm = lastMileageKm,
        )

        fun reminderRow(ruleId: Long, state: DueState, enabled: Boolean = true): ReminderRow =
            ReminderRow(
                rule = rule(id = ruleId, vehicleId = 1L, enabled = enabled),
                evaluation = if (enabled) {
                    ReminderEvaluation(state = state, axis = null, urgency = 0.0, cycleKey = "k$ruleId")
                } else {
                    null
                },
            )
    }
}

/**
 * A hand-written [VehicleRepository] whose observation flow is a constructor argument. Every
 * write the dashboard never performs throws, so a ViewModel that started saving or deleting a
 * vehicle would fail loudly instead of silently passing.
 */
private class FakeVehicleRepository(
    private val vehicles: Flow<List<Vehicle>> = flowOf(emptyList()),
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> = vehicles

    override fun observeVehicle(id: Long): Flow<Vehicle?> = flowOf(null)

    override suspend fun save(vehicle: Vehicle): Long =
        error("the dashboard never saves a vehicle")

    override suspend fun delete(id: Long) =
        error("the dashboard never deletes a vehicle")
}

/**
 * A hand-written [ReminderRepository] whose three observation flows are constructor arguments —
 * the same shape `RemindersViewModelTest`'s fake uses, so the dashboard's own combine/join/order
 * pipeline is exercised rather than mocked.
 */
private class FakeReminderRepository(
    private val rules: Flow<List<ReminderRule>> = flowOf(emptyList()),
    private val baselines: List<TypeBaseline> = emptyList(),
    private val stats: List<VehicleStats> = emptyList(),
) : ReminderRepository {

    override fun observeRules(vehicleId: Long): Flow<List<ReminderRule>> = rules

    override fun observeAllRules(): Flow<List<ReminderRule>> = rules

    override fun observeRule(ruleId: Long): Flow<ReminderRule?> = flowOf(null)

    override fun observeTypeBaselines(): Flow<List<TypeBaseline>> = flowOf(baselines)

    override fun observeVehicleStats(): Flow<List<VehicleStats>> = flowOf(stats)

    override suspend fun save(rule: ReminderRule): Long =
        error("the dashboard never saves a rule")

    override suspend fun setEnabled(ruleId: Long, enabled: Boolean) =
        error("the dashboard never toggles a rule")

    override suspend fun markNotified(ruleId: Long, cycleKey: String): Boolean =
        error("the dashboard never claims a cycle")

    override suspend fun markArmed(ruleId: Long, cycleKey: String) =
        error("the dashboard never arms a cycle")

    override suspend fun delete(ruleId: Long) =
        error("the dashboard never deletes a rule")
}
