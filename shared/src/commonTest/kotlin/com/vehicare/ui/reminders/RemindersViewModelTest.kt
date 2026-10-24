package com.vehicare.ui.reminders

import com.vehicare.data.ReminderRepository
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderAxis
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.VehicleStats
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [RemindersViewModel] — the four-state matrix, the urgency order and the optimistic-free
 * switch.
 *
 * The repository is a hand-written fake: rules, baselines and stats are three flat `Flow`s, exactly
 * the shape the real implementation exposes, so the ViewModel's join is exercised rather than
 * mocked. `viewModelScope` needs a `Dispatchers.setMain` test dispatcher, which is why the harness
 * mirrors `iosTest/…/ServiceEntryEditViewModelCustomTypeTest.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

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
    fun noRulesIsEmpty() = runTest {
        val viewModel = RemindersViewModel(VEHICLE_ID, FakeReminderRepository())
        val states = collect(viewModel)

        assertEquals(RemindersUiState.Empty, states.last())
    }

    @Test
    fun anOverdueRuleSortsBeforeAnUpcomingOne() = runTest {
        val overdue = rule(id = 1L, intervalKm = 1_000L)
        val upcoming = rule(id = 2L, intervalKm = 10_000L)
        val viewModel = RemindersViewModel(
            VEHICLE_ID,
            FakeReminderRepository(
                rules = flowOf(listOf(upcoming, overdue)),
                baselines = listOf(baseline(km = 10_000L)),
                stats = listOf(stats(mileage = 15_000L)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<RemindersUiState.Content>(states.last())
        assertEquals(listOf(1L, 2L), content.rows.map { it.rule.id })
        assertEquals(
            DueState.Overdue(kmOver = 4_000L, daysOver = null),
            content.rows.first().evaluation?.state,
        )
        assertEquals(
            DueState.Upcoming(kmLeft = 5_000L, dueDate = null),
            content.rows.last().evaluation?.state,
        )
    }

    @Test
    fun aDisabledRuleSortsAfterEveryEnabledRuleAndHasNoEvaluation() = runTest {
        val disabled = rule(id = 3L, intervalKm = 1_000L, enabled = false)
        val upcoming = rule(id = 2L, intervalKm = 10_000L)
        val overdue = rule(id = 1L, intervalKm = 1_000L)
        val viewModel = RemindersViewModel(
            VEHICLE_ID,
            FakeReminderRepository(
                rules = flowOf(listOf(disabled, overdue, upcoming)),
                baselines = listOf(baseline(km = 10_000L)),
                stats = listOf(stats(mileage = 15_000L)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<RemindersUiState.Content>(states.last())
        assertEquals(listOf(1L, 2L, 3L), content.rows.map { it.rule.id })
        assertNull(content.rows.last().evaluation, "a disabled rule has no due state (D-43)")
        assertFalse(content.toggleFailed)
    }

    @Test
    fun aTypeWithNoEntryFallsBackToTheVehiclesMileageAndTheRulesCreationDate() = runTest {
        val today = ServiceDates.today()
        val distanceOnly = rule(id = 1L, intervalKm = 10_000L)
        val timeOnly = rule(
            id = 2L,
            intervalKm = null,
            intervalMonths = 1L,
            createdAtEpochMs = ServiceDates.toStorage(today),
        )
        val viewModel = RemindersViewModel(
            VEHICLE_ID,
            FakeReminderRepository(
                rules = flowOf(listOf(distanceOnly, timeOnly)),
                // No type baseline at all: both rules must use D-49's fallbacks.
                baselines = emptyList(),
                stats = listOf(stats(mileage = 50_000L)),
            ),
        )
        val states = collect(viewModel)

        val content = assertIs<RemindersUiState.Content>(states.last())
        val rows = content.rows.associateBy { it.rule.id }

        // Distance: the baseline is the vehicle's latest mileage, so a full interval is left.
        val distanceEvaluation = rows.getValue(1L).evaluation
        assertEquals(
            DueState.Upcoming(kmLeft = 10_000L, dueDate = null),
            distanceEvaluation?.state,
        )
        assertEquals(ReminderAxis.DISTANCE, distanceEvaluation?.axis)

        // Time: the baseline is the rule's creation date, so one calendar month from today is due.
        val timeEvaluation = rows.getValue(2L).evaluation
        assertEquals(
            DueState.Upcoming(kmLeft = null, dueDate = today.plus(1, DateTimeUnit.MONTH)),
            timeEvaluation?.state,
        )
        assertEquals(ReminderAxis.TIME, timeEvaluation?.axis)
    }

    @Test
    fun toggleForwardsTheRuleIdAndTheNewValue() = runTest {
        val repo = FakeReminderRepository(rules = MutableStateFlow(listOf(rule(id = 5L))))
        val viewModel = RemindersViewModel(VEHICLE_ID, repo)
        collect(viewModel)

        viewModel.toggle(ruleId = 5L, enabled = false)
        advanceUntilIdle()

        assertEquals(listOf(5L to false), repo.setEnabledCalls)
    }

    @Test
    fun aFailedToggleSetsTheFlagAndAcknowledgingClearsIt() = runTest {
        val repo = FakeReminderRepository(
            rules = MutableStateFlow(listOf(rule(id = 5L))),
            failSetEnabled = true,
        )
        val viewModel = RemindersViewModel(VEHICLE_ID, repo)
        val states = collect(viewModel)

        viewModel.toggle(ruleId = 5L, enabled = false)
        advanceUntilIdle()

        val failed = assertIs<RemindersUiState.Content>(states.last())
        assertTrue(failed.toggleFailed, "a write that never landed must be reported (T-03-19)")
        // The row itself still comes from the observation flow — no optimistic local state.
        assertTrue(failed.rows.single().rule.enabled)

        viewModel.acknowledgeToggleFailure()
        advanceUntilIdle()

        val acknowledged = assertIs<RemindersUiState.Content>(states.last())
        assertFalse(acknowledged.toggleFailed)
    }

    @Test
    fun aFailingObservationProducesError() = runTest {
        val viewModel = RemindersViewModel(
            VEHICLE_ID,
            FakeReminderRepository(rules = flow { throw IllegalStateException("boom") }),
        )
        val states = collect(viewModel)

        val error = assertIs<RemindersUiState.Error>(states.last())
        assertEquals("boom", error.message)
    }

    @Test
    fun retryReCollectsAfterAnError() = runTest {
        var attempts = 0
        val flakyRules = flow {
            attempts++
            if (attempts == 1) throw IllegalStateException("boom")
            emit(listOf(rule(id = 1L)))
        }
        val viewModel = RemindersViewModel(
            VEHICLE_ID,
            FakeReminderRepository(rules = flakyRules),
        )
        val states = collect(viewModel)

        assertIs<RemindersUiState.Error>(states.last())
        assertEquals(1, attempts)

        viewModel.retry()
        advanceUntilIdle()

        val content = assertIs<RemindersUiState.Content>(states.last())
        assertEquals(listOf(1L), content.rows.map { it.rule.id })
        assertEquals(2, attempts, "retry must re-run the three flat reads")
    }

    /**
     * Collects [viewModel]'s state on the test scheduler and returns the emission log.
     *
     * `WhileSubscribed` means the upstream only starts with a live collector, so every test needs
     * one; the job is cancelled by the caller's test scope when the body ends.
     */
    private fun TestScope.collect(
        viewModel: RemindersViewModel,
    ): MutableList<RemindersUiState> {
        val states = mutableListOf<RemindersUiState>()
        // `backgroundScope` is cancelled when the test body ends, so the never-completing
        // StateFlow collection does not trip `runTest`'s uncompleted-coroutine check.
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect { states += it }
        }
        advanceUntilIdle()
        return states
    }

    private companion object {
        const val VEHICLE_ID = 7L
        const val BASELINE_EPOCH_MS = 1_700_000_000_000L

        fun rule(
            id: Long,
            intervalKm: Long? = 1_000L,
            intervalMonths: Long? = null,
            enabled: Boolean = true,
            createdAtEpochMs: Long = BASELINE_EPOCH_MS,
        ): ReminderRule = ReminderRule(
            id = id,
            vehicleId = VEHICLE_ID,
            serviceType = "Oil change",
            intervalKm = intervalKm,
            intervalMonths = intervalMonths,
            leadKm = null,
            leadDays = null,
            enabled = enabled,
            createdAtEpochMs = createdAtEpochMs,
            notifiedCycleKey = null,
            armedCycleKey = null,
        )

        fun baseline(km: Long): TypeBaseline = TypeBaseline(
            vehicleId = VEHICLE_ID,
            typeLabel = "Oil change",
            baselineDateEpochMs = BASELINE_EPOCH_MS,
            baselineKm = km,
        )

        fun stats(mileage: Long): VehicleStats = VehicleStats(
            vehicleId = VEHICLE_ID,
            lastServiceEpochMs = BASELINE_EPOCH_MS,
            lastMileageKm = mileage,
        )
    }
}

/**
 * A hand-written [ReminderRepository] whose three observation flows are constructor arguments.
 *
 * Every write the reminders list never performs throws, so a ViewModel that started calling `save`,
 * `markNotified`, `markArmed` or `delete` would fail loudly instead of silently passing.
 */
private class FakeReminderRepository(
    private val rules: Flow<List<ReminderRule>> = flowOf(emptyList()),
    private val baselines: List<TypeBaseline> = emptyList(),
    private val stats: List<VehicleStats> = emptyList(),
    private val failSetEnabled: Boolean = false,
) : ReminderRepository {

    val setEnabledCalls = mutableListOf<Pair<Long, Boolean>>()

    override fun observeRules(vehicleId: Long): Flow<List<ReminderRule>> = rules

    override fun observeAllRules(): Flow<List<ReminderRule>> = rules

    override fun observeRule(ruleId: Long): Flow<ReminderRule?> = flowOf(null)

    override fun observeTypeBaselines(): Flow<List<TypeBaseline>> = flowOf(baselines)

    override fun observeVehicleStats(): Flow<List<VehicleStats>> = flowOf(stats)

    override suspend fun save(rule: ReminderRule): Long =
        error("the reminders list never saves a rule")

    override suspend fun setEnabled(ruleId: Long, enabled: Boolean) {
        setEnabledCalls += ruleId to enabled
        if (failSetEnabled) throw IllegalStateException("disk full")
    }

    override suspend fun markNotified(ruleId: Long, cycleKey: String): Boolean =
        error("the reminders list never claims a cycle")

    override suspend fun markArmed(ruleId: Long, cycleKey: String) =
        error("the reminders list never arms a cycle")

    override suspend fun delete(ruleId: Long) =
        error("the reminders list never deletes a rule")
}
