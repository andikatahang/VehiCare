package com.vehicare.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.domain.repository.ServiceEntryRepository
import com.vehicare.domain.ServiceEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The read-only entry detail destination's state matrix (UI-SPEC §States & Interaction Contract → Entry detail rows).
 */
sealed interface ServiceEntryDetailUiState {

    /** The first frame while the observation flow is still connecting — never a blank screen. */
    data object Loading : ServiceEntryDetailUiState

    /**
     * The row this destination was opened for does not exist (it was deleted while the screen was
     * open, or the read failed). Rendered as the not-available surface with a way back.
     */
    data object NotFound : ServiceEntryDetailUiState

    /** The row is present; [entry] is the domain model straight from the repository boundary. */
    data class Content(val entry: ServiceEntry) : ServiceEntryDetailUiState
}

/**
 * Reads one service entry through the repository's observation flow and maps it into the state
 * matrix above.
 *
 * **Streaming rather than a one-shot fetch is the whole point.** The detail screen subscribes to
 * the row, so when the user edits the entry and pops back from the form the repository notifies
 * and the screen re-renders the new values with no navigation callback doing anything, and a row
 * that disappears becomes [ServiceEntryDetailUiState.NotFound] by itself. Neither case needs a
 * "does it still exist?" guard in the screen, and none belongs there.
 *
 * The composed read (entry + parts + photos) is the repository's business, so no query, driver or
 * generated type appears here.
 *
 * @param entryId the id the typed route carried, bound as a typed query parameter.
 */
class ServiceEntryDetailViewModel(
    private val repo: ServiceEntryRepository,
    private val entryId: Long,
) : ViewModel() {

    val uiState: StateFlow<ServiceEntryDetailUiState> =
        repo.observeEntry(entryId)
            .map { entry ->
                if (entry == null) ServiceEntryDetailUiState.NotFound
                else ServiceEntryDetailUiState.Content(entry)
            }
            .catch {
                // Same rule as the vehicle detail: a row that cannot be read surfaces as the
                // same not-available state as a missing one — the contract has three states, and no
                // exception detail is logged.
                emit(ServiceEntryDetailUiState.NotFound)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = ServiceEntryDetailUiState.Loading,
            )

    private val _deleting = MutableStateFlow(false)

    /**
     * True while a deletion is running; the screen disables the destructive action on it.
     *
     * It is also the single-flight guard: [delete] reads the current value and returns early while
     * another deletion is in progress, so a second confirm can never enqueue a second repository
     * call.
     */
    val deleting: StateFlow<Boolean> = _deleting

    private val _deleteFailed = MutableStateFlow(false)

    /**
     * Set when a deletion did not happen, cleared by [onDeleteErrorShown]. While it is true the
     * screen shows the delete-error copy — the entry is still stored and still on screen.
     */
    val deleteFailed: StateFlow<Boolean> = _deleteFailed

    /**
     * Deletes this entry **and its part and photo rows and their photo files** (SVC-07), then hands
     * the outcome back to the screen.
     *
     * **Irreversible: a deleted entry never comes back.** The confirmation dialog is the only path
     * that reaches this method, and there is deliberately no recovery surface behind it.
     *
     * Nothing is removed from the UI here and no optimistic state exists: the repository's flow is
     * what makes the row disappear once the row really is gone, so a failure needs no rollback —
     * [deleteFailed] simply reports it and the entry stays visible. No entry data (notes, cost,
     * photo path) is ever logged.
     *
     * @param onDeleted invoked **only after** `repo.delete` returned without throwing, so the
     *   screen returns to the history only for a deletion that really happened.
     */
    fun delete(onDeleted: () -> Unit) {
        if (deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                repo.delete(entryId)
                _deleting.value = false
                onDeleted()
            } catch (cancellation: CancellationException) {
                // The scope was torn down; that is not a failed deletion to report.
                throw cancellation
            } catch (_: Exception) {
                _deleting.value = false
                _deleteFailed.value = true
            }
        }
    }

    /**
     * Lets the screen retire [deleteFailed] once its message has been shown.
     *
     * It retires the message and nothing else: the entry is not retried, not cleared and not
     * navigated away from — retrying is the user's next tap on the destructive action, which runs
     * through the confirmation dialog again.
     */
    fun onDeleteErrorShown() {
        _deleteFailed.value = false
    }
}