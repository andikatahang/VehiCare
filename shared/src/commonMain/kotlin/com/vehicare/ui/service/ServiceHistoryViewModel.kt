package com.vehicare.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.ServiceEntryRepository
import com.vehicare.domain.HistorySort
import com.vehicare.domain.ServiceEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Service history screen's state matrix (UI-SPEC States & Interaction Contract).
 */
sealed interface ServiceHistoryUiState {

    /** The first frame while the observation flow is still connecting — never a blank screen. */
    data object Loading : ServiceHistoryUiState

    /** The vehicle has no service entries yet. */
    data object Empty : ServiceHistoryUiState

    /** Rows arrived; each is a domain entry straight from the repository boundary. */
    data class Content(val entries: List<ServiceEntry>) : ServiceHistoryUiState

    /** The observation flow failed; [message] carries the raw cause for the recovery path. */
    data class Error(val message: String?) : ServiceHistoryUiState
}

/**
 * Reads a vehicle's service history through the repository's observation flow with a sort switch.
 * The sort selection lives in ViewModel state only (never persisted, D-34) and re-subscribes
 * through `flatMapLatest` so a sort change cancels the previous read and restarts with the new order.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ServiceHistoryViewModel(
    private val repo: ServiceEntryRepository,
    private val vehicleId: Long,
) : ViewModel() {

    /** Current sort selection — held in VM state only, never written to any store (D-34). */
    private val _sort = MutableStateFlow(HistorySort.DATE)
    val sort: StateFlow<HistorySort> = _sort

    /**
     * The count behind `flatMapLatest`: bumping it cancels the previous read and restarts the
     * source query, which is exactly what the "Try again" control needs after an error.
     */
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<ServiceHistoryUiState> =
        observeHistoryUiState(
            repo = repo,
            vehicleId = vehicleId,
            sort = _sort,
            refresh = refresh,
        )
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ServiceHistoryUiState.Loading,
            )

    /** Update the sort selection — ViewModel state only, never persisted (D-34). */
    fun onSortSelected(value: HistorySort) {
        _sort.value = value
    }

    /** Restart the underlying observation after the error surface (the Try again control). */
    fun retry() {
        refresh.value++
    }
}

/**
 * Assembles the history state flow.
 *
 * The error handler sits **inside** the inner observation, so a failure ends only that read and
 * leaves the outer ``flatMapLatest`` alive: bumping [refresh] re-runs the query and recovers the
 * screen (CR-02). Kept top-level and repository-injected so the retry path is testable without a
 * ViewModel or a main dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeHistoryUiState(
    repo: ServiceEntryRepository,
    vehicleId: Long,
    sort: Flow<HistorySort>,
    refresh: Flow<Int>,
): Flow<ServiceHistoryUiState> =
    sort.flatMapLatest { mode ->
        refresh.flatMapLatest {
            repo.observeHistory(vehicleId, mode)
                .map { entries ->
                    if (entries.isEmpty()) ServiceHistoryUiState.Empty
                    else ServiceHistoryUiState.Content(entries)
                }
                // Terminal for this observation only: a `refresh` bump re-arms the query.
                .catch { emit(ServiceHistoryUiState.Error(message = it.message)) }
        }
    }
