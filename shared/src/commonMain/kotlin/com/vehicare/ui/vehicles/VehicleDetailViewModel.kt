package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Detail destination's state matrix (UI-SPEC §States & Interaction Contract → Detail rows).
 */
sealed interface VehicleDetailUiState {

    /** The first frame while the observation flow is still connecting — never a blank screen. */
    data object Loading : VehicleDetailUiState

    /**
     * The row this destination was opened for does not exist (it was deleted while the screen was
     * open, or the read failed). Rendered as the not-available surface with a way back.
     */
    data object NotFound : VehicleDetailUiState

    /** The row is present; [vehicle] is the domain model straight from the repository boundary. */
    data class Content(val vehicle: Vehicle) : VehicleDetailUiState
}

/**
 * Reads one vehicle through the repository's observation flow and maps it into the state matrix
 * above.
 *
 * **Streaming rather than a one-shot fetch is the whole point.** The detail screen subscribes to
 * the row, so when the user edits the vehicle and pops back from the form the repository notifies
 * and the screen re-renders the new values with no navigation callback doing anything, and a row
 * that disappears becomes [VehicleDetailUiState.NotFound] by itself. Neither case needs a
 * "does it still exist?" guard in the screen, and none belongs there (T-01-55).
 *
 * Everything below the repository boundary is invisible here by design: no driver, no query, no
 * generated type and no dispatcher appears above the data layer.
 *
 * @param vehicleId the id the typed route carried, bound as a typed query parameter.
 */
class VehicleDetailViewModel(
    private val repo: VehicleRepository,
    private val vehicleId: Long,
) : ViewModel() {

    val uiState: StateFlow<VehicleDetailUiState> =
        repo.observeVehicle(vehicleId)
            .map { vehicle ->
                if (vehicle == null) VehicleDetailUiState.NotFound
                else VehicleDetailUiState.Content(vehicle)
            }
            .catch {
                // Same rule as the Add/Edit load path: a row that cannot be read surfaces as the
                // same not-available state as a missing one — the contract has three states, and no
                // exception detail is logged.
                emit(VehicleDetailUiState.NotFound)
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VehicleDetailUiState.Loading,
            )

    private val _deleting = MutableStateFlow(false)

    /**
     * True while a deletion is running; the screen disables the destructive action on it.
     *
     * It is also the single-flight guard: [delete] reads the current value and returns early while
     * another deletion is in progress, so a second confirm can never enqueue a second repository
     * call (T-01-59).
     */
    val deleting: StateFlow<Boolean> = _deleting

    private val _deleteFailed = MutableStateFlow(false)

    /**
     * Set when a deletion did not happen, cleared by [onDeleteErrorShown]. While it is true the
     * screen shows the delete-error copy — the vehicle is still stored and still on screen.
     */
    val deleteFailed: StateFlow<Boolean> = _deleteFailed

    /**
     * Deletes this vehicle **and the photo file the repository holds for it** (`D-07`), then hands
     * the outcome back to the screen.
     *
     * **Irreversible in Phase 1: a deleted vehicle never comes back.** The confirmation dialog is
     * the only path that reaches this method, and there is deliberately no recovery surface behind
     * it — an author looking for a way to bring a deleted vehicle back should know the capability
     * does not exist rather than assume one is hiding somewhere.
     *
     * Nothing is removed from the UI here and no optimistic state exists: the repository's flow is
     * what makes the row disappear once the row really is gone, so a failure needs no rollback —
     * [deleteFailed] simply reports it and the vehicle stays visible (T-01-60). No vehicle data
     * (name, plate, photo location) is ever logged (ASVS V7).
     *
     * @param onDeleted invoked **only after** `repo.delete` returned without throwing, so the
     *   screen returns to the list only for a deletion that really happened.
     */
    fun delete(onDeleted: () -> Unit) {
        if (deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                repo.delete(vehicleId)
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
     * It retires the message and nothing else: the vehicle is not retried, not cleared and not
     * navigated away from — retrying is the user's next tap on the destructive action, which runs
     * through the confirmation dialog again.
     */
    fun onDeleteErrorShown() {
        _deleteFailed.value = false
    }
}
