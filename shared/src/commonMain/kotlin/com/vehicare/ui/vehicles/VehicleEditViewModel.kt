package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.Vehicle
import com.vehicare.util.PhotoStorage
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlin.time.Clock

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
     * @param importing   a picked photo is being copied into app storage right now — the picker and
     *   the submit button stay disabled while that runs
     * @param photoError  one-shot failure flag the screen consumes to show the photo-import message
     */
    data class Ready(
        val values: VehicleFormValues,
        val error: VehicleFormError?,
        val saving: Boolean,
        val nameEdited: Boolean,
        val saveFailed: Boolean = false,
        val importing: Boolean = false,
        val photoError: Boolean = false,
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
 * The photo lifecycle lives here as well (VEH-02): [onPhotoPicked] copies the picked file into
 * app-private storage and this form only ever holds its **relative** location (D-05), while the two
 * bookkeeping fields below decide which file may be deleted and when — the one part of this screen
 * that can lose data if it is got wrong (T-01-49).
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

    /**
     * The stored file the *saved row* still references after this form replaced or removed its
     * photo, and which may therefore only be deleted once a save has made that change durable.
     *
     * It only ever holds a location that came from the database. A file persisted during this
     * session is unreferenced the moment it is replaced, so that one is removed straight away
     * instead. A failed save leaves this field alone, so the row and its file keep agreeing, and
     * the abandonment path deliberately never touches it (T-01-49).
     */
    private var supersededPath: String? = null

    /**
     * True while the held `photoPath` points at a copy this session persisted that no row has saved
     * yet: the only file [onCleared] is allowed to remove, and the reason a successful save clears
     * this flag.
     */
    private var persistedButUnsaved: Boolean = false

    /** Last stamp [nextPhotoFileName] handed out, so two picks in one millisecond differ. */
    private var lastPhotoStamp: Long = 0L

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
        if (current.saving || current.importing || !VehicleFormValidation.isSubmittable(current.values)) {
            return
        }

        _uiState.value = current.copy(saving = true, saveFailed = false)
        val submitted = current.values
        viewModelScope.launch {
            try {
                repo.save(VehicleFormValidation.toVehicle(vehicleId, submitted))
                markPhotoSaved()
                clearSaving()
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                // Values stay exactly as they were: the user's input survives a failed save and the
                // screen reports the failure instead of discarding it (T-01-45).
                markSaveFailed()
            }
        }
    }

    /** Lets the screen retire the save-failure flag (after the Snackbar) without losing the form. */
    fun onSaveErrorShown() {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        if (current.saveFailed) _uiState.value = current.copy(saveFailed = false)
    }

    /**
     * Imports a picked gallery image: copies it into app storage and holds the returned **relative**
     * location in the form. The picker's own file is read from and then never referenced again —
     * its location goes stale as soon as the process dies (D-05, T-01-51).
     *
     * A failure flags [VehicleEditUiState.Ready.photoError] and changes nothing else: every field,
     * including a photo that was already attached, survives an import that did not work.
     *
     * The supersede bookkeeping runs only once the new copy exists, so a failed import can never
     * leave the form pointing at a file that was already scheduled for removal.
     */
    fun onPhotoPicked(picked: PlatformFile) {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        // One import at a time: a second one could otherwise supersede a copy that is still being
        // written, and a pick during a save would be recorded against the wrong row.
        if (current.importing || current.saving) return
        val fileName = nextPhotoFileName()

        _uiState.value = current.copy(importing = true, photoError = false)
        viewModelScope.launch {
            val relativePath = try {
                PhotoStorage.persist(picked, fileName)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = _uiState.value as? VehicleEditUiState.Ready
                if (latest != null) {
                    _uiState.value = latest.copy(importing = false, photoError = true)
                }
                return@launch
            }

            val latest = _uiState.value as? VehicleEditUiState.Ready ?: return@launch
            val previous = latest.values.photoPath
            if (previous != null) {
                if (persistedButUnsaved) {
                    // Copied this session and never saved, so no row references it: remove it now.
                    PhotoStorage.delete(previous)
                } else {
                    // The saved row still references it, so only a successful save may remove it.
                    supersededPath = previous
                }
            }
            persistedButUnsaved = true
            _uiState.value = latest.copy(
                values = latest.values.copy(photoPath = relativePath),
                importing = false,
            )
        }
    }

    /**
     * Detaches the photo from the form. Nothing the saved row references is removed here: on an edit
     * the previous file is only queued in [supersededPath] and goes once a save is durable, while a
     * copy made during this session — which nothing references — is removed straight away.
     */
    fun onPhotoRemoved() {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        val previous = current.values.photoPath ?: return
        if (persistedButUnsaved) {
            PhotoStorage.delete(previous)
            persistedButUnsaved = false
        } else {
            supersededPath = previous
        }
        _uiState.value = current.copy(values = current.values.copy(photoPath = null))
    }

    /**
     * Lets the screen retire the photo-import flag once its message has been shown, so the message
     * does not re-fire on every recomposition.
     */
    fun onPhotoImportFailed() {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        if (current.photoError) _uiState.value = current.copy(photoError = false)
    }

    /**
     * The abandon case: a photo copied into app storage before any row referenced it is litter once
     * the form is torn down without a successful save, and this is the only place that can still
     * remove it.
     *
     * [supersededPath] is deliberately not touched here — it always names a file the saved row
     * still references, and removing it because a form was closed would destroy the user's only
     * copy. [PhotoStorage.delete] is non-suspend precisely so this call is legal from here.
     */
    override fun onCleared() {
        if (persistedButUnsaved) {
            val held = (_uiState.value as? VehicleEditUiState.Ready)?.values?.photoPath
            PhotoStorage.delete(held)
        }
        super.onCleared()
    }

    /**
     * A name no earlier pick in this session can collide with: the vehicle id plus a stamp that
     * always advances, so a replaced photo is a genuinely new file and no image cache can serve the
     * superseded one (RESEARCH OQ-5).
     */
    private fun nextPhotoFileName(): String {
        val stamp = maxOf(Clock.System.now().toEpochMilliseconds(), lastPhotoStamp + 1L)
        lastPhotoStamp = stamp
        return "$vehicleId-$stamp.jpg"
    }

    /**
     * The row now references the held photo, so a teardown may no longer remove it — and the file
     * the row referenced before this session's change is now unreferenced and is removed.
     */
    private fun markPhotoSaved() {
        persistedButUnsaved = false
        val stale = supersededPath ?: return
        supersededPath = null
        PhotoStorage.delete(stale)
    }

    private fun clearSaving() {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = false)
    }

    private fun markSaveFailed() {
        val current = _uiState.value as? VehicleEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = true)
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
