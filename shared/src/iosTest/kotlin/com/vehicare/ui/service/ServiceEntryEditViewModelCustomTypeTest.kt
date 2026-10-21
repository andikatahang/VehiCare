package com.vehicare.ui.service

import com.vehicare.data.ServiceEntryRepository
import com.vehicare.domain.HistorySort
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServiceSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
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

/**
 * The UAT gap 1 regression, driven through the real [ServiceEntryEditViewModel] entry point that the
 * UAT exercised — `onCustomTypeConfirmed` — rather than through the pure helper alone.
 *
 * The bug: the old three-branch implementation wrote only `values.serviceType`, while
 * `ServiceTypePicker` renders `presets + learned`. A brand-new label therefore had no chip and the
 * confirmation looked like it did nothing. This test fails against that implementation: it asserts
 * the confirmed label lands in **both** `values.serviceTypes` and `learnedTypes`, i.e. that the
 * picker's rendered option set now contains the selected label.
 *
 * The harness mirrors `data/ServiceEntryDeletionTest.kt`: `viewModelScope` needs a
 * `Dispatchers.setMain` test dispatcher, so the ViewModel-level proof lives in `iosTest` while the
 * pure transition is covered by `commonTest/…/ServiceEntryEditViewModelStateTest.kt` on both targets.
 *
 * State only — no timing assertions and no storage calls; persistence is the repository layer's job.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceEntryEditViewModelCustomTypeTest {

    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(mainDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Add mode: `entryId == 0L`, so the init prefill is the only coroutine already in flight. */
    private fun newViewModel(): ServiceEntryEditViewModel = ServiceEntryEditViewModel(
        repo = InertServiceEntryRepository(),
        vehicleId = 1L,
        entryId = 0L,
    )

    private fun ServiceEntryEditViewModel.ready(): ServiceEntryEditUiState.Ready =
        uiState.value as ServiceEntryEditUiState.Ready

    @Test
    fun customTypeConfirmationSelectsItAndMakesItARenderableChip() = runTest {
        val viewModel = newViewModel()
        // Let the add-mode prefill land first, so it cannot overwrite the learned list afterwards.
        advanceUntilIdle()

        viewModel.onCustomTypeConfirmed("Wheel alignment")

        val state = viewModel.ready()
        assertEquals(listOf("Wheel alignment"), state.values.serviceTypes)
        assertEquals(listOf("Wheel alignment"), state.learnedTypes)

        // The exact UAT gap 1 assertion: the picker's presets + learned option set contains the
        // selected label, so it has a chip to render as selected.
        val renderedOptions = com.vehicare.domain.ServiceTypePresets.ALL + state.learnedTypes
        assertEquals(1, renderedOptions.count { it == "Wheel alignment" })
    }

    @Test
    fun presetCaseVariantSelectsThePresetCasingWithoutAddingAChip() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onCustomTypeConfirmed("oil change")

        val state = viewModel.ready()
        assertEquals(listOf("Oil change"), state.values.serviceTypes)
        assertEquals(emptyList(), state.learnedTypes)
    }

    @Test
    fun confirmingTheSameLabelTwiceDoesNotDuplicateEitherList() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onCustomTypeConfirmed("Wheel alignment")
        viewModel.onCustomTypeConfirmed("Wheel alignment")

        val state = viewModel.ready()
        assertEquals(listOf("Wheel alignment"), state.values.serviceTypes)
        assertEquals(listOf("Wheel alignment"), state.learnedTypes)
    }

    @Test
    fun togglingAppendsThenRemovesCaseInsensitively() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onTypeToggled("Battery")
        assertEquals(listOf("Battery"), viewModel.ready().values.serviceTypes)

        viewModel.onTypeToggled("battery")
        assertEquals(emptyList(), viewModel.ready().values.serviceTypes)
    }

    @Test
    fun severalTogglesKeepTapOrderAsTheDisplayOrder() = runTest {
        val viewModel = newViewModel()
        advanceUntilIdle()

        viewModel.onTypeToggled("Brake service")
        viewModel.onTypeToggled("Oil change")
        viewModel.onCustomTypeConfirmed("Wheel alignment")

        assertEquals(
            listOf("Brake service", "Oil change", "Wheel alignment"),
            viewModel.ready().values.serviceTypes,
        )
    }
}

/**
 * The minimum repository the add-mode ViewModel touches: the prefill reads, and an entry flow that
 * never emits (add mode does not collect it). Every write is inert — persistence is out of scope here.
 */
private class InertServiceEntryRepository : ServiceEntryRepository {

    private val learned = MutableStateFlow<List<String>>(emptyList())
    private val entry = MutableStateFlow<ServiceEntry?>(null)

    override fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>> = emptyFlow()

    override fun observeEntry(id: Long): Flow<ServiceEntry?> = entry

    override fun observeSummary(vehicleId: Long): Flow<ServiceSummary> = emptyFlow()

    override fun observeLearnedTypes(): Flow<List<String>> = learned

    override fun observeLatestMileage(vehicleId: Long): Flow<Long?> = flowOf(null)

    override fun observePreviousMileage(vehicleId: Long, entryId: Long): Flow<Long?> = flowOf(null)

    override suspend fun save(entry: ServiceEntry): Long = 0L

    override suspend fun delete(id: Long) = Unit
}
