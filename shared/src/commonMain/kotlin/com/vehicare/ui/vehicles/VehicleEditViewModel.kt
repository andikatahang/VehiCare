package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.Vehicle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * The shared Add/Edit form's state matrix (UI-SPEC States & Interaction Contract).
 *
 * `vehicleId == 0L` starts in [Ready] with empty values — the add case, "Car" preselected by the
 * model default (C-5, matching `type TEXT NOT NULL DEFAULT 'Car'`). An edit starts in [Loading] and
 * resolves to [Ready] or [NotFound]; it never renders a blank form for a row that is gone.
 */
sealed interface VehicleEditUiState {

    /** The edit destination is fetching the row it was asked for. Never a blank screen. */
    data object Loading : VehicleEditUiState

    /**
     * @param values      the raw field content, exactly as typed
     * @param error       the rule currently violated, or `null` when the name is acceptable
     * @param saving      a save is in flight — the double-submit guard is checked on this state
     * @param nameEdited  the name has been typed then possibly cleared on purpose; until it flips,
     *   the required-field message stays hidden (UI-SPEC Add/Edit `Invalid` row)
     * @param saveFailed  one-shot failure flag the screen consumes to show the save-error Snackbar
     */
    data class Ready(
        val values: VehicleFormValues,
        val error: VehicleFormError?,
        val saving: Boolean,
        val nameEdited: Boolean,
        val saveFailed: Boolean = false,
    ) : VehicleEditUiState

    /** The row this destination was opened for no longer exists. */
    data object NotFound : VehicleEditUiState
}

/**
 * Owns the Add/Edit form: it loads a row for the edit case, mutates the field values through one
 * entry point re-validated on every change, and saves through [VehicleRepository] with a guard that
 * makes a duplicate insert impossible.
 *
 * The repository is the only storage seam touched here — no driver, no query, no generated type can
 * appear above the data layer (T-01-40). No field value is ever logged (T-01-43): a failure surfaces
 * as the one-shot [VehicleEditUiState.Ready.saveFailed] flag the screen turns into user-facing copy.
 *
 * @param vehicleId `0L` means add; any other id is loaded for edit and saved in place.
 */
class VehicleEditViewModel(
    private val repo: VehicleRepository,
    private val vehicleId: Long,
) : ViewModel() {

    private val isAdd: Boolean = vehicleId == 0L

    private val _uiState = MutableStateFlow<VehicleEditUiState>(
        if (isAdd) {
            VehicleEditUiState.Ready(
                values = VehicleFormValues(),
                error = null,
                saving = false,
                nameEdited = false,
            )
        } else {
            // Nothing may render a blank form before the row has been read (T-01-44).
            VehicleEditUiState.Loading
        },
    )

    val uiState: StateFlow<VehicleEditUiState> = _uiState

    /**
     * True once the edit path has consumed the row's first emission.
     *
     * The edit path accepts **only the first** emission and ignores every later one, so a repository
     * change (or the write the save itself triggers) can never overwrite a field the user has
     * already edited. That is the cheapest rule that is provably free of the silent-data-loss bug
     * the optional fields — and the photo path especially — are exposed to (T-01-44).
     */
    private var initialLoadApplied: Boolean = false

    init {
        if (!isAdd) {
            viewModelScope.launch {
                repo.observeVehicle(vehicleId)
                    .catch {
                        // The contract has no fourth state: a row that cannot be read is surfaced as
                        // the same not-available surface as a deleted one — never a blank form. No
                        // exception detail is logged (T-01-43).
                        _uiState.value = VehicleEditUiState.NotFound
                    }
                    .collect { vehicle ->
                        if (initialLoadApplied) return@collect
                        initialLoadApplied = true
                        _uiState.value = if (vehicle == null) {
                            VehicleEditUiState.NotFound
                        } else {
                            VehicleEditUiState.Ready(
                                values = vehicle.toFormValues(),
                                error = null,
                                saving = false,
                                nameEdited = false,
                            )
                        }
                    }
            }
        }
    }

    /**
     * The single mutator for every field. [update] receives the current values and returns the next
     * ones (the screen passes `{ it.copy(make = ...) }`), so no field can be written through a path
     * that skips re-validation.
     */
    fun onFieldChange(update: (VehicleFormValues) -> VehicleFormValues) {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        val values = update(current.values)
        val nameEdited = current.nameEdited || values.name != current.values.name
        _uiState.value = current.copy(
            values = values,
            // The required-field message appears only after the user has typed then cleared the
            // name; before that the empty name is simply not submittable (UI-SPEC invalid row).
            error = if (nameEdited) VehicleFormValidation.errorFor(values) else null,
            nameEdited = nameEdited,
        )
    }

    /**
     * Persists the current form. A successful save calls [onSaved] and nothing else: the list and
     * the Detail screen are Flow-driven and show the new state by themselves — there is no
     * celebratory state to render (UI-SPEC §Motion) and no optimistically-updated local copy that
     * could present a failed save as a success (T-01-45).
     */
    fun save(onSaved: () -> Unit) {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        // The double-submit guard, checked on the live state: a second tap cannot enqueue a second
        // insert (T-01-41). The name gate is re-checked here so the state machine — not only the
        // disabled button — owns the "only a name-bearing form submits" rule (D-09).
        if (current.saving || !VehicleFormValidation.isSubmittable(current.values)) return

        _uiState.value = current.copy(saving = true, saveFailed = false)
        val submitted = current.values
        viewModelScope.launch {
            try {
                repo.save(VehicleFormValidation.toVehicle(vehicleId, submitted))
                settleSaving(saveFailed = false)
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Values stay exactly as they were: the user's input survives a failed save and the
                // screen reports the failure instead of discarding it (T-01-45).
                settleSaving(saveFailed = true)
            }
        }
    }

    /** Lets the screen retire the save-failure flag (after the Snackbar) without losing the form. */
    fun onSaveErrorShown() {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        if (current.saveFailed) _uiState.value = current.copy(saveFailed = false)
    }

    private fun settleSaving(saveFailed: Boolean) {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = saveFailed)
    }
}

/** The stored row as the editable text the form holds (null optionals become empty fields). */
private fun Vehicle.toFormValues(): VehicleFormValues = VehicleFormValues(
    name = name,
    make = make.orEmpty(),
    model = model.orEmpty(),
    year = year.orEmpty(),
    plate = plate.orEmpty(),
    type = type,
    photoPath = photoPath,
)
