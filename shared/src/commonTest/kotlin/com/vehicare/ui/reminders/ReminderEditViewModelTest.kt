package com.vehicare.ui.reminders

import com.vehicare.data.ReminderRepository
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.VehicleStats
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for [ReminderEditViewModel]: add/edit seeding, the one-shot load guard, the single
 * mutator, the submit gate, save and the guarded delete.
 *
 * The repository is a hand-written fake implementing all ten [ReminderRepository] members — the
 * `RemindersViewModelTest` precedent — so a ViewModel that started calling a member it should not
 * (e.g. `markNotified`) fails loudly instead of silently passing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReminderEditViewModelTest {

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
    fun addStartsReadyWithEmptyIntervalsEmptySelectionAndEnabledTrue() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(ReminderFormValues(), ready.values)
        assertTrue(ready.values.enabled)
        assertTrue(ready.values.serviceTypes.isEmpty())
        assertTrue(ready.values.intervalKm.isEmpty())
        assertTrue(ready.values.intervalMonths.isEmpty())
    }

    @Test
    fun addSeedsLearnedTypesFromTheFlow() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(listOf("Headlight bulb")),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("Headlight bulb"), ready.learnedTypes)
    }

    @Test
    fun editSeedsEveryFieldFromAStoredDualIntervalRule() = runTest {
        val rule = rule(
            id = RULE_ID,
            intervalKm = 5_000L,
            intervalMonths = 6L,
            leadKm = 500L,
            leadDays = 14L,
            enabled = true,
        )
        val repo = FakeReminderEditRepository(rules = MutableStateFlow(mapOf(RULE_ID to rule)))
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("Oil change"), ready.values.serviceTypes)
        assertEquals("5000", ready.values.intervalKm)
        assertEquals("6", ready.values.intervalMonths)
        assertEquals("500", ready.values.leadKm)
        assertEquals("14", ready.values.leadDays)
        assertTrue(ready.values.enabled)
    }

    @Test
    fun editSeedsADisabledRuleWithEnabledFalse() = runTest {
        val rule = rule(id = RULE_ID, enabled = false)
        val repo = FakeReminderEditRepository(rules = MutableStateFlow(mapOf(RULE_ID to rule)))
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertFalse(ready.values.enabled)
    }

    @Test
    fun aNullEmissionIsNotFound() = runTest {
        val repo = FakeReminderEditRepository(rules = MutableStateFlow(emptyMap()))
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        assertEquals(ReminderEditUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun aRuleBelongingToAnotherVehicleIsNotFound() = runTest {
        val rule = rule(id = RULE_ID, vehicleId = VEHICLE_ID + 1L)
        val repo = FakeReminderEditRepository(rules = MutableStateFlow(mapOf(RULE_ID to rule)))
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        assertEquals(ReminderEditUiState.NotFound, viewModel.uiState.value)
    }

    @Test
    fun aSecondEmissionAfterTheFirstDoesNotOverwriteAnEditedField() = runTest {
        val rule = rule(id = RULE_ID, intervalKm = 5_000L)
        val rulesFlow = MutableStateFlow(mapOf(RULE_ID to rule))
        val repo = FakeReminderEditRepository(rules = rulesFlow)
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        viewModel.onFieldChange(ReminderFormField.INTERVAL_KM, "9999")
        advanceUntilIdle()

        // A later emission on the same rule (e.g. a concurrent write) must never overwrite the
        // user's edit — the one-shot load guard rejects everything after the first.
        rulesFlow.value = mapOf(RULE_ID to rule.copy(intervalKm = 1_000L))
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals("9999", ready.values.intervalKm)
    }

    @Test
    fun onFieldChangeStripsNonDigitsAndCapsDigitCounts() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onFieldChange(ReminderFormField.INTERVAL_KM, "12a34bc5678901")
        viewModel.onFieldChange(ReminderFormField.INTERVAL_MONTHS, "1a2b3c4d5")
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals("123456", ready.values.intervalKm)
        assertEquals("123", ready.values.intervalMonths)
    }

    @Test
    fun onTypeSelectedReplacesRatherThanAppends() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onTypeSelected("Oil change")
        advanceUntilIdle()
        viewModel.onTypeSelected("Brake service")
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("Brake service"), ready.values.serviceTypes)
    }

    @Test
    fun onTypeSelectedOnTheSameChipIsANoOp() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onTypeSelected("Oil change")
        advanceUntilIdle()
        viewModel.onTypeSelected("Oil change")
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("Oil change"), ready.values.serviceTypes)
    }

    @Test
    fun onCustomTypeConfirmedReplacesAndAddsANewChip() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onTypeSelected("Oil change")
        advanceUntilIdle()
        viewModel.onCustomTypeConfirmed("Headlight bulb")
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("Headlight bulb"), ready.values.serviceTypes)
        assertEquals(listOf("Headlight bulb"), ready.learnedTypes)
    }

    @Test
    fun onCustomTypeConfirmedWithAnExistingPresetKeepsItsOwnCasing() = runTest {
        val viewModel = ReminderEditViewModel(
            repo = FakeReminderEditRepository(),
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onCustomTypeConfirmed("oil change")
        advanceUntilIdle()

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertEquals(listOf("Oil change"), ready.values.serviceTypes)
        assertTrue(ready.learnedTypes.isEmpty(), "a preset match must not add a learned chip")
    }

    @Test
    fun theSubmitGateBlocksAZeroIntervalForm() = runTest {
        val values = ReminderFormValues(serviceTypes = listOf("Oil change"), intervalKm = "0")
        assertFalse(ReminderFormValidation.isSubmittable(values))
    }

    @Test
    fun theSubmitGateBlocksATwoTypeForm() = runTest {
        val values = ReminderFormValues(
            serviceTypes = listOf("Oil change", "Brake service"),
            intervalKm = "5000",
        )
        assertFalse(ReminderFormValidation.isSubmittable(values))
    }

    @Test
    fun theSubmitGateBlocksAnInFlightSave() = runTest {
        val repo = FakeReminderEditRepository(saveDelaysForever = true)
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onTypeSelected("Oil change")
        viewModel.onFieldChange(ReminderFormField.INTERVAL_KM, "5000")
        advanceUntilIdle()

        var savedCount = 0
        viewModel.save(onSaved = { savedCount++ })
        advanceUntilIdle()
        viewModel.save(onSaved = { savedCount++ })
        advanceUntilIdle()

        assertEquals(1, repo.saveCalls.size, "a second save while one is in flight must be refused")
        assertEquals(0, savedCount, "onSaved is never invoked while the save never resolves")
    }

    @Test
    fun aSuccessfulSaveCallsOnSavedExactlyOnceWithThePreservedCreatedAtAndNullMarkers() = runTest {
        val rule = rule(id = RULE_ID, createdAtEpochMs = ORIGINAL_CREATED_AT)
        val repo = FakeReminderEditRepository(rules = MutableStateFlow(mapOf(RULE_ID to rule)))
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        var savedCount = 0
        viewModel.save(onSaved = { savedCount++ })
        advanceUntilIdle()

        assertEquals(1, savedCount)
        assertEquals(1, repo.saveCalls.size)
        val saved = repo.saveCalls.single()
        assertEquals(ORIGINAL_CREATED_AT, saved.createdAtEpochMs)
        assertNull(saved.notifiedCycleKey)
        assertNull(saved.armedCycleKey)

        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertFalse(ready.saving)
        assertFalse(ready.saveFailed)
    }

    @Test
    fun aFailedSaveLeavesTheFormAndCallsNothing() = runTest {
        val repo = FakeReminderEditRepository(failSave = true)
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        viewModel.onTypeSelected("Oil change")
        viewModel.onFieldChange(ReminderFormField.INTERVAL_KM, "5000")
        advanceUntilIdle()

        var savedCount = 0
        viewModel.save(onSaved = { savedCount++ })
        advanceUntilIdle()

        assertEquals(0, savedCount)
        val ready = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertFalse(ready.saving)
        assertTrue(ready.saveFailed)
        assertEquals("5000", ready.values.intervalKm)

        viewModel.onSaveErrorShown()
        val cleared = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertFalse(cleared.saveFailed)
    }

    @Test
    fun deleteIsRefusedOnAdd() = runTest {
        val repo = FakeReminderEditRepository()
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = 0L,
        )
        advanceUntilIdle()

        var deletedCount = 0
        viewModel.delete(onDeleted = { deletedCount++ })
        advanceUntilIdle()

        assertEquals(0, deletedCount)
        assertEquals(0, repo.deleteCalls.size)
    }

    @Test
    fun deleteIsRefusedOnASecondCallWhileInFlight() = runTest {
        val rule = rule(id = RULE_ID)
        val repo = FakeReminderEditRepository(
            rules = MutableStateFlow(mapOf(RULE_ID to rule)),
            deleteDelaysForever = true,
        )
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        var deletedCount = 0
        viewModel.delete(onDeleted = { deletedCount++ })
        advanceUntilIdle()
        viewModel.delete(onDeleted = { deletedCount++ })
        advanceUntilIdle()

        assertEquals(1, repo.deleteCalls.size)
        assertEquals(0, deletedCount)
    }

    @Test
    fun aFailedDeleteSetsDeleteFailedWhichOnDeleteErrorShownClears() = runTest {
        val rule = rule(id = RULE_ID)
        val repo = FakeReminderEditRepository(
            rules = MutableStateFlow(mapOf(RULE_ID to rule)),
            failDelete = true,
        )
        val viewModel = ReminderEditViewModel(
            repo = repo,
            learnedTypes = flowOf(emptyList()),
            vehicleId = VEHICLE_ID,
            ruleId = RULE_ID,
        )
        advanceUntilIdle()

        var deletedCount = 0
        viewModel.delete(onDeleted = { deletedCount++ })
        advanceUntilIdle()

        assertEquals(0, deletedCount)
        val failed = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertTrue(failed.deleteFailed)
        assertFalse(failed.deleting)

        viewModel.onDeleteErrorShown()
        val cleared = assertIs<ReminderEditUiState.Ready>(viewModel.uiState.value)
        assertFalse(cleared.deleteFailed)
    }

    private companion object {
        const val VEHICLE_ID = 7L
        const val RULE_ID = 42L
        const val ORIGINAL_CREATED_AT = 1_700_000_000_000L

        fun rule(
            id: Long,
            vehicleId: Long = VEHICLE_ID,
            intervalKm: Long? = 5_000L,
            intervalMonths: Long? = null,
            leadKm: Long? = null,
            leadDays: Long? = null,
            enabled: Boolean = true,
            createdAtEpochMs: Long = ORIGINAL_CREATED_AT,
        ): ReminderRule = ReminderRule(
            id = id,
            vehicleId = vehicleId,
            serviceType = "Oil change",
            intervalKm = intervalKm,
            intervalMonths = intervalMonths,
            leadKm = leadKm,
            leadDays = leadDays,
            enabled = enabled,
            createdAtEpochMs = createdAtEpochMs,
            notifiedCycleKey = "stale-cycle-key",
            armedCycleKey = "stale-armed-key",
        )
    }
}

/**
 * A hand-written [ReminderRepository] fake. [rules] is keyed by rule id, mirroring
 * [ReminderRepository.observeRule]'s per-id read; a missing key emits `null`.
 *
 * Every member the editor never calls ([observeRules], [observeAllRules],
 * [observeTypeBaselines], [observeVehicleStats], [setEnabled], [markNotified], [markArmed]) is a
 * stub that fails loudly, so a ViewModel that started calling one of them would fail the test
 * instead of passing silently.
 */
private class FakeReminderEditRepository(
    private val rules: MutableStateFlow<Map<Long, ReminderRule>> = MutableStateFlow(emptyMap()),
    private val failSave: Boolean = false,
    private val saveDelaysForever: Boolean = false,
    private val failDelete: Boolean = false,
    private val deleteDelaysForever: Boolean = false,
) : ReminderRepository {

    val saveCalls = mutableListOf<ReminderRule>()
    val deleteCalls = mutableListOf<Long>()

    override fun observeRules(vehicleId: Long): Flow<List<ReminderRule>> =
        error("the editor never observes a vehicle's full rule list")

    override fun observeAllRules(): Flow<List<ReminderRule>> =
        error("the editor never observes the whole-database rule list")

    override fun observeRule(ruleId: Long): Flow<ReminderRule?> =
        MutableStateFlowMapper(rules, ruleId)

    override fun observeTypeBaselines(): Flow<List<TypeBaseline>> =
        error("the editor never observes type baselines")

    override fun observeVehicleStats(): Flow<List<VehicleStats>> =
        error("the editor never observes vehicle stats")

    override suspend fun save(rule: ReminderRule): Long {
        saveCalls += rule
        if (saveDelaysForever) {
            kotlinx.coroutines.awaitCancellation()
        }
        if (failSave) throw IllegalStateException("disk full")
        return rule.id
    }

    override suspend fun setEnabled(ruleId: Long, enabled: Boolean) =
        error("the editor never toggles a rule directly")

    override suspend fun markNotified(ruleId: Long, cycleKey: String): Boolean =
        error("the editor never claims a cycle")

    override suspend fun markArmed(ruleId: Long, cycleKey: String) =
        error("the editor never arms a cycle")

    override suspend fun delete(ruleId: Long) {
        deleteCalls += ruleId
        if (deleteDelaysForever) {
            kotlinx.coroutines.awaitCancellation()
        }
        if (failDelete) throw IllegalStateException("disk full")
    }
}

/** Adapts a `Map<Long, ReminderRule>` state flow into a per-id `Flow<ReminderRule?>`. */
private class MutableStateFlowMapper(
    private val source: MutableStateFlow<Map<Long, ReminderRule>>,
    private val id: Long,
) : Flow<ReminderRule?> {
    override suspend fun collect(collector: kotlinx.coroutines.flow.FlowCollector<ReminderRule?>) {
        source.collect { map -> collector.emit(map[id]) }
    }
}
