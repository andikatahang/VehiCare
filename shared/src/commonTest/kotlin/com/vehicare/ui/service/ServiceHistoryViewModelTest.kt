package com.vehicare.ui.service

import com.vehicare.domain.repository.ServiceEntryRepository
import com.vehicare.domain.HistorySort
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServiceSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests for [observeHistoryUiState] — the history screen's observation assembly.
 *
 * CR-02 regression: the error handler must sit inside the per-query flow, so a failed read ends
 * only that query and a `refresh` bump starts a new one. If it moves back outside the
 * `flatMapLatest` chain, the first failure completes the whole assembly and `retry()` goes inert.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceHistoryViewModelTest {

    @Test
    fun retryAfterFailureRestartsTheObservation() = runTest {
        var attempts = 0
        val repo = FakeServiceEntryRepository(
            history = {
                flow {
                    attempts++
                    if (attempts == 1) throw IllegalStateException("boom")
                    emit(listOf(ENTRY))
                }
            },
        )
        val sort = MutableStateFlow(HistorySort.DATE)
        val refresh = MutableStateFlow(0)
        val states = mutableListOf<ServiceHistoryUiState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            observeHistoryUiState(repo, ENTRY.vehicleId, sort, refresh).collect { states += it }
        }
        advanceUntilIdle()

        assertIs<ServiceHistoryUiState.Error>(states.last(), "the first read must surface the error")
        assertEquals(1, attempts)

        refresh.value++
        advanceUntilIdle()

        assertIs<ServiceHistoryUiState.Content>(states.last(), "retry must re-run the query")
        assertEquals(2, attempts)
        assertEquals(listOf(ENTRY), (states.last() as ServiceHistoryUiState.Content).entries)
        job.cancel()
    }

    @Test
    fun emptyResultIsEmptyNotContent() = runTest {
        val repo = FakeServiceEntryRepository(history = { flow { emit(emptyList()) } })
        val states = mutableListOf<ServiceHistoryUiState>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            observeHistoryUiState(
                repo = repo,
                vehicleId = ENTRY.vehicleId,
                sort = MutableStateFlow(HistorySort.DATE),
                refresh = MutableStateFlow(0),
            ).collect { states += it }
        }
        advanceUntilIdle()

        assertEquals(ServiceHistoryUiState.Empty, states.last())
        job.cancel()
    }

    @Test
    fun changingSortReQueriesWithTheNewOrder() = runTest {
        val sort = MutableStateFlow(HistorySort.DATE)
        val requested = mutableListOf<HistorySort>()
        val repo = FakeServiceEntryRepository(
            history = {
                flow {
                    requested += sort.value
                    emit(listOf(ENTRY))
                }
            },
        )

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            observeHistoryUiState(
                repo = repo,
                vehicleId = ENTRY.vehicleId,
                sort = sort,
                refresh = MutableStateFlow(0),
            ).collect { }
        }
        advanceUntilIdle()
        sort.value = HistorySort.MILEAGE
        advanceUntilIdle()

        assertEquals(listOf(HistorySort.DATE, HistorySort.MILEAGE), requested)
        assertTrue(requested.size == 2, "each sort selection issues exactly one query")
        job.cancel()
    }

    private companion object {
        val ENTRY = ServiceEntry(
            id = 1L,
            vehicleId = 7L,
            dateEpochMs = 1_700_000_000_000L,
            mileageKm = 42_000L,
            serviceTypes = listOf("Oil change"),
        )
    }
}

private class FakeServiceEntryRepository(
    private val history: () -> Flow<List<ServiceEntry>>,
) : ServiceEntryRepository {

    override fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>> =
        history()

    override fun observeEntry(id: Long): Flow<ServiceEntry?> = emptyFlow()

    override fun observeSummary(vehicleId: Long): Flow<ServiceSummary> = emptyFlow()

    override fun observeLearnedTypes(): Flow<List<String>> = emptyFlow()

    override fun observeLatestMileage(vehicleId: Long): Flow<Long?> = emptyFlow()

    override fun observePreviousMileage(vehicleId: Long, entryId: Long): Flow<Long?> = emptyFlow()

    override suspend fun save(entry: ServiceEntry): Long = 0L

    override suspend fun delete(id: Long) = Unit
}
