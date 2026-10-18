package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.Vehicle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The Vehicles tab's state matrix (UI-SPEC States & Interaction Contract), expressible from day
 * one so later plans can only refine what each state renders, never invent new states.
 */
sealed interface VehicleListUiState {

    /** The first frame while the observation flow is still connecting — never a blank screen. */
    data object Loading : VehicleListUiState

    /** The table reads empty (rows arrive newest-first per D-16, so this is the add-first cue). */
    data object Empty : VehicleListUiState

    /** Rows arrived; each is a domain vehicle straight from the repository boundary. */
    data class Content(val items: List<Vehicle>) : VehicleListUiState

    /** The observation flow failed; [message] carries the raw cause for the recovery path. */
    data class Error(val message: String?) : VehicleListUiState
}

/**
 * Reads the vehicle list through the repository's observation flow — rows arrive already
 * newest-first (D-16), so this ViewModel only maps the table into the state matrix above.
 *
 * Everything below the repository boundary is invisible here by design: no driver, no query,
 * no generated type above the data layer (T-01-22).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleListViewModel(private val repo: VehicleRepository) : ViewModel() {

    /**
     * The count behind `flatMapLatest`: bumping it cancels the previous read and restarts the
     * source query, which is exactly what the "Try again" control needs after an error.
     */
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<VehicleListUiState> =
        refresh
            .flatMapLatest {
                repo.observeVehicles()
                    .map { vehicles ->
                        if (vehicles.isEmpty()) VehicleListUiState.Empty
                        else VehicleListUiState.Content(vehicles)
                    }
            }
            .catch { emit(VehicleListUiState.Error(message = it.message)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VehicleListUiState.Loading,
            )

    /** Restart the underlying observation after the error surface (the Try again control). */
    fun retry() {
        refresh.value++
    }
}
