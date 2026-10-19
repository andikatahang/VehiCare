package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.Vehicle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
     * True while a deletion is running.
     *
     * **Owned by plan 01-10**, which implements the confirmed-delete flow. It is declared here, as
     * a constant `false`, so the Detail screen can be laid out once and wired to the full delete
     * surface when that plan lands — rather than being rewritten twice.
     */
    val deleting: StateFlow<Boolean> = _deleting

    private val _deleteFailed = MutableStateFlow(false)

    /**
     * One-shot flag the screen consumes to show the delete-error copy.
     *
     * **Owned by plan 01-10**, alongside the confirmation dialog and the failure path; constant
     * `false` until then.
     */
    val deleteFailed: StateFlow<Boolean> = _deleteFailed

    /**
     * Deletes this vehicle and its photo file.
     *
     * **Deliberately a no-op stub in this plan.** Plan 01-10 owns the whole destructive flow — the
     * confirmation dialog, the failure handling and the photo-file assertion that make deletion
     * safe — and it is the only place that turns this on. A half-wired destructive action would be
     * worse than an explicit stub, so this method mutates nothing and calls nothing on [repo].
     *
     * @param onDeleted invoked after a successful deletion, so the screen can return to the list.
     */
    fun delete(onDeleted: () -> Unit) {
        // Stub: plan 01-10 implements the deletion. `onDeleted` is intentionally unused here.
    }

    /**
     * Lets the screen retire [deleteFailed] once its message has been shown.
     *
     * **Owned by plan 01-10**; a no-op until the flag can ever be set.
     */
    fun onDeleteErrorShown() {
        // Stub: plan 01-10 implements the deletion. Nothing sets the flag yet.
    }
}
