package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * The shared Add/Edit form's state — in THIS plan exactly one field, `name`: `make`, `model`,
 * `year` and `plate` stay null and `type` defaults to [VehicleType.Car] (the schema default).
 * Plans 01-07/01-08 own the full field set, validation, type picker and photo handling.
 */
sealed interface VehicleEditUiState {

    /** Before the first keystroke lands in the form. */
    data object Idle : VehicleEditUiState

    /**
     * @param name       the raw field content (trimmed only at save time)
     * @param isSaving   a save is in flight — the submit control must be disabled (T-01-23)
     * @param saveFailed the last save attempt failed; the form stays populated
     */
    data class Ready(
        val name: String,
        val isSaving: Boolean,
        val saveFailed: Boolean,
    ) : VehicleEditUiState
}

/**
 * Reads from the repository and pushes a name-only vehicle through [VehicleRepository.save].
 *
 * Domain objects in, domain objects out: the SQLDelight table stays invisible above the data
 * layer (T-01-22); a failed insert cannot enqueue duplicate rows because submit is guarded and
 * disabled while [isSaving] (T-01-23).
 */
class VehicleEditViewModel(private val repo: VehicleRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<VehicleEditUiState>(VehicleEditUiState.Idle)
    val uiState: StateFlow<VehicleEditUiState> = _uiState

    /** New input clears the failure marker; an in-flight save cannot be un-marked by typing. */
    fun onNameChange(value: String) {
        val current = _uiState.value
        val stillSaving = (current as? VehicleEditUiState.Ready)?.isSaving == true
        _uiState.value = VehicleEditUiState.Ready(
            name = value,
            isSaving = stillSaving,
            saveFailed = false,
        )
    }

    /**
     * Persists the add-path vehicle (`id = 0L`) and returns the new row's id.
     *
     * @return the persisted id on success; `0L` when the form was empty, a save is already in
     *   flight, or the repository failed (failure keeps the form populated and marks
     *   [VehicleEditUiState.Ready.saveFailed]). [CancellationException] always propagates so
     *   the caller's coroutine can die correctly without being mistaken for a save failure.
     */
    suspend fun save(): Long {
        val state = _uiState.value
        if (state !is VehicleEditUiState.Ready) return 0L
        if (state.isSaving) return 0L

        _uiState.value = state.copy(isSaving = true, saveFailed = false)
        try {
            val id = repo.save(
                Vehicle(
                    id = 0L,
                    name = state.name.trim(),
                    make = null,
                    model = null,
                    year = null,
                    plate = null,
                    type = VehicleType.Car,
                    photoPath = null,
                ),
            )
            _uiState.value = VehicleEditUiState.Ready(
                name = state.name,
                isSaving = false,
                saveFailed = false,
            )
            return id
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            _uiState.value = VehicleEditUiState.Ready(
                name = state.name,
                isSaving = false,
                saveFailed = true,
            )
            return 0L
        }
    }
}
