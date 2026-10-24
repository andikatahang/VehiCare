package com.vehicare.ui.settings

import com.vehicare.data.settings.SettingsStore
import com.vehicare.notifications.NotificationScheduler
import com.vehicare.notifications.NotificationPermission
import com.vehicare.notifications.ScheduledReminder
import com.vehicare.notifications.DueReminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Settings screen's save contract (261024-u9g, D-1): a valid month value writes the store and
 * fires exactly one re-engine sweep; a blank value re-opens the per-rule fallback; an invalid value
 * exposes the error state, writes nothing, and never fires the sweep.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ---- fakes ------------------------------------------------------------------------

    private class FakeSettingsStore(
        initial: Long? = null,
    ) : SettingsStore {
        private val state = MutableStateFlow(initial)
        val writes = mutableListOf<Long?>()

        override fun readIntervalMonths(): Long? = state.value
        override fun setIntervalMonths(months: Long?) {
            writes += months
            state.value = months
        }
        override fun observeIntervalMonths(): Flow<Long?> = state
    }

    private class FakeScheduler : NotificationScheduler {
        val evaluateNowCalls = mutableListOf<Unit>()
        override suspend fun permissionState() = NotificationPermission.GRANTED
        override suspend fun scheduleTimeBased(due: List<ScheduledReminder>) = Unit
        override suspend fun triggerImmediateIfOverdue(due: List<DueReminder>) = Unit
        override suspend fun cancel(ruleIds: List<Long>) = Unit
        override fun evaluateNow() {
            evaluateNowCalls += Unit
        }
        override suspend fun openNotificationSettings() = Unit
    }

    private fun viewModel(store: SettingsStore = FakeSettingsStore(), scheduler: FakeScheduler = FakeScheduler()) =
        SettingsViewModel(store, scheduler) to scheduler

    // ---- loading ------------------------------------------------------------------------

    /**
     * Collects the ViewModel's [flow] in the test's `backgroundScope` (the RemindersViewModelTest
     * pattern): a `stateIn(WhileSubscribed)` flow emits only into an active collector, which is
     * exactly what a subscription means.
     */
    private fun <T> TestScope.backgroundCollect(flow: Flow<T>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            flow.collect {}
        }
    }

    @Test
    fun aNeverSetStoreLoadsABlankFieldWithNoError() = runTest {
        val (vm, _) = viewModel(FakeSettingsStore(null))
        backgroundCollect(vm.intervalText)
        runCurrent()

        assertEquals("", vm.intervalText.value)
        assertFalse(vm.intervalError.value)
        assertFalse(vm.savedFlash.value, "nothing has been saved yet")
    }

    @Test
    fun aSetStoreLoadsItsValue() = runTest {
        val (vm, _) = viewModel(FakeSettingsStore(6L))
        backgroundCollect(vm.intervalText)
        runCurrent()

        assertEquals("6", vm.intervalText.value)
    }

    @Test
    fun onIntervalChangeKeepsDigitsOnly() {
        val (vm, _) = viewModel()

        assertEquals("123", vm.onIntervalChange("1x2,3 months"))
        assertEquals("", vm.onIntervalChange("months"))
    }

    // ---- saving -----------------------------------------------------------------------------

    @Test
    fun aValidValueWritesTheStoreAndFiresTheSweepOnce() = runTest {
        val store = FakeSettingsStore()
        val (vm, scheduler) = viewModel(store)
        runCurrent()

        vm.save("6")
        runCurrent()

        assertEquals(listOf<Long?>(6L), store.writes)
        assertEquals(1, scheduler.evaluateNowCalls.size, "the re-arm sweep fires exactly once")
        assertTrue(vm.savedFlash.value)
        assertFalse(vm.intervalError.value)
    }

    @Test
    fun theBoundaryValuesAreAccepted() = runTest {
        val store = FakeSettingsStore()
        val (vm, scheduler) = viewModel(store)
        runCurrent()

        vm.save("1")
        runCurrent()
        vm.save("1200")
        runCurrent()

        assertEquals(listOf<Long?>(1L, 1200L), store.writes)
        assertEquals(2, scheduler.evaluateNowCalls.size)
    }

    @Test
    fun aBlankSaveClearsTheSettingAndFiresTheSweep() = runTest {
        val store = FakeSettingsStore(6L)
        val (vm, scheduler) = viewModel(store)
        runCurrent()

        vm.save("")
        runCurrent()

        assertEquals(listOf<Long?>(null), store.writes, "blank restores the per-rule fallback (D-1)")
        assertEquals(null, store.readIntervalMonths())
        assertEquals(1, scheduler.evaluateNowCalls.size)
        assertTrue(vm.savedFlash.value)
    }

    @Test
    fun anInvalidValueWritesNothingAndNeverFiresTheSweep() = runTest {
        val store = FakeSettingsStore(6L)
        val (vm, scheduler) = viewModel(store)
        runCurrent()

        vm.save("0")
        runCurrent()

        assertTrue(store.writes.isEmpty(), "invalid saves never write")
        assertEquals(0, scheduler.evaluateNowCalls.size)
        assertTrue(vm.intervalError.value)
        assertFalse(vm.savedFlash.value)
    }

    @Test
    fun anOutOfRangeValueIsRejectedAtTheUpperBound() = runTest {
        val store = FakeSettingsStore()
        val (vm, scheduler) = viewModel(store)
        runCurrent()

        vm.save("1201")
        runCurrent()

        assertTrue(store.writes.isEmpty())
        assertEquals(0, scheduler.evaluateNowCalls.size)
        assertTrue(vm.intervalError.value)
    }

    @Test
    fun anUnparsableValueIsAnErrorRatherThanASilentClear() = runTest {
        val store = FakeSettingsStore(6L)
        val (vm, scheduler) = viewModel(store)
        runCurrent()

        vm.save("abc")
        runCurrent()

        assertTrue(store.writes.isEmpty(), "'abc' must never behave like a clear")
        assertEquals(0, scheduler.evaluateNowCalls.size)
        assertTrue(vm.intervalError.value)
    }
}
