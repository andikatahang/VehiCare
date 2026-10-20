package com.vehicare.ui.service

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.ServiceEntryRepository
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServiceTypePresets
import com.vehicare.ui.service.ServiceEntryFormValidation
import com.vehicare.util.PhotoStorage
import com.vehicare.util.ServiceDates
import io.github.vinceglb.filekit.PlatformFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * The Add/Edit service entry form's state matrix (UI-SPEC States & Interaction Contract).
 *
 * `entryId == 0L` starts in [Ready] with today's date, latest mileage prefilled, nothing preselected.
 * An edit starts in [Loading] and resolves to [Ready] or [NotFound]; it never renders a blank form
 * for a row that is gone.
 */
sealed interface ServiceEntryEditUiState {

    /** The edit destination is fetching the row it was asked for. Never a blank screen. */
    data object Loading : ServiceEntryEditUiState

    /**
     * @param values          the raw field content, exactly as typed
     * @param saveFailed      one-shot failure flag the screen consumes to show the save-error Snackbar
     * @param saving          a save is in flight — the double-submit guard is checked on this state
     * @param importing       a picked photo is being copied into app storage right now — the picker and
     *                        the submit button stay disabled while that runs
     * @param photoError      one-shot failure flag the screen consumes to show the photo-import message
     * @param mileageBaseline the baseline mileage for the warning (null if no baseline available)
     * @param learnedTypes    the learned custom types from the repository
     */
    data class Ready(
        val values: ServiceEntryFormValues,
        val saveFailed: Boolean = false,
        val saving: Boolean = false,
        val importing: Boolean = false,
        val photoError: Boolean = false,
        val mileageBaseline: Long? = null,
        val learnedTypes: List<String> = emptyList(),
    ) : ServiceEntryEditUiState

    /** The row this destination was opened for no longer exists. */
    data object NotFound : ServiceEntryEditUiState
}

/**
 * Owns the Add/Edit service entry form: it loads a row for the edit case, mutates the field values
 * through one entry point re-validated on every change, and saves through [ServiceEntryRepository]
 * with a guard that makes a duplicate insert impossible.
 *
 * The repository is the only storage seam touched here — no driver, no query, no generated type can
 * appear above the data layer. No field value is ever logged: a failure surfaces as the one-shot
 * [ServiceEntryEditUiState.Ready.saveFailed] flag the screen turns into user-facing copy.
 *
 * The photo lifecycle lives here as well: [onPhotoPicked] copies the picked file into app-private
 * storage and this form only ever holds its **relative** location, while the bookkeeping sets below
 * decide which file may be deleted and when.
 *
 * @param vehicleId the vehicle this entry belongs to
 * @param entryId `0L` means add; any other id is loaded for edit and saved in place.
 */
class ServiceEntryEditViewModel(
    private val repo: ServiceEntryRepository,
    private val vehicleId: Long,
    private val entryId: Long,
) : ViewModel() {

    private val isAdd: Boolean = entryId == 0L

    private val _uiState = MutableStateFlow<ServiceEntryEditUiState>(
        if (isAdd) {
            ServiceEntryEditUiState.Ready(
                // The date is seeded synchronously: it is pure and cheap, and it keeps the submit
                // gate in agreement with the date the field already displays (WR-03).
                values = ServiceEntryFormValues(
                    dateEpochMs = ServiceDates.toStorage(ServiceDates.today()),
                ),
                saveFailed = false,
                saving = false,
                importing = false,
                photoError = false,
                mileageBaseline = null,
                learnedTypes = emptyList(),
            )
        } else {
            ServiceEntryEditUiState.Loading
        },
    )

    val uiState: StateFlow<ServiceEntryEditUiState> = _uiState

    /**
     * True once the edit path has consumed the row's first emission.
     *
     * The edit path accepts **only the first** emission and ignores every later one, so a repository
     * change (or the write the save itself triggers) can never overwrite a field the user has
     * already edited.
     */
    private var initialLoadApplied: Boolean = false

    /**
     * Paths of photos that the *saved row* still references after this form replaced or removed them,
     * and which may therefore only be deleted once a save has made that change durable.
     *
     * They only ever hold locations that came from the database. Files persisted during this
     * session are unreferenced the moment they are replaced, so those are removed straight away
     * instead. A failed save leaves this set alone, so the row and its files keep agreeing, and
     * the abandonment path deliberately never touches them.
     */
    private val supersededPaths: MutableSet<String> = mutableSetOf()

    /**
     * Paths of photos copied into app storage during this session that no row has saved yet:
     * the only files [onCleared] is allowed to remove, and the reason a successful save clears
     * this flag.
     */
    private val persistedButUnsavedPaths: MutableSet<String> = mutableSetOf()

    /** Last stamp [nextPhotoFileName] handed out, so two picks in one millisecond differ. */
    private var lastPhotoStamp: Long = 0L

    init {
        if (isAdd) {
            // Add mode: the date was seeded synchronously; the repository-backed prefill merges
            // into whatever the user has typed in the meantime instead of replacing it (WR-03).
            viewModelScope.launch {
                try {
                    val latestMileage = repo.observeLatestMileage(vehicleId).first()
                    val learned = repo.observeLearnedTypes().first()
                    val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return@launch
                    _uiState.value = current.copy(
                        values = current.values.copy(
                            mileage = current.values.mileage.ifBlank {
                                latestMileage?.toString().orEmpty()
                            },
                        ),
                        mileageBaseline = latestMileage,
                        learnedTypes = learned,
                    )
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed prefill must never disable Save: the form stays usable with the
                    // synchronously seeded date and no baseline.
                }
            }
        } else {
            // Edit mode: load the row once. A failure before the first emission is NotFound; a
            // late failure leaves the user's filled form untouched (WR-02).
            viewModelScope.launch {
                repo.observeEntry(entryId)
                    .catch {
                        if (!initialLoadApplied) {
                            initialLoadApplied = true
                            _uiState.value = ServiceEntryEditUiState.NotFound
                        }
                    }
                    .collect { entry ->
                        if (initialLoadApplied) return@collect
                        initialLoadApplied = true
                        _uiState.value = if (entry == null) {
                            ServiceEntryEditUiState.NotFound
                        } else {
                            try {
                                ServiceEntryEditUiState.Ready(
                                    values = entry.toFormValues(),
                                    mileageBaseline = repo
                                        .observePreviousMileage(vehicleId, entryId)
                                        .first(),
                                    learnedTypes = repo.observeLearnedTypes().first(),
                                )
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (_: Exception) {
                                // Seeding failed inside the collector: fail closed rather than
                                // leave the screen on Loading forever.
                                ServiceEntryEditUiState.NotFound
                            }
                        }
                    }
            }
        }
    }

    /**
     * The single mutator for every field. [update] receives the current values and returns the next
     * ones, so no field can be written through a path that skips re-validation.
     */
    fun onFieldChange(update: (ServiceEntryFormValues) -> ServiceEntryFormValues) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        val values = update(current.values)
        _uiState.value = current.copy(values = values)
    }

    /**
     * Called when the user confirms a custom type from the dialog.
     * Performs case-insensitive collapse against presets and learned types — a match selects the
     * existing chip and never rewrites stored values (D-40).
     */
    fun onCustomTypeConfirmed(trimmedLabel: String) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        val lower = trimmedLabel.lowercase()

        // Check presets first
        val presetMatch = ServiceTypePresets.ALL.firstOrNull { it.lowercase() == lower }
        if (presetMatch != null) {
            _uiState.value = current.copy(values = current.values.copy(serviceType = presetMatch))
            return
        }

        // Check learned types
        val learnedMatch = current.learnedTypes.firstOrNull { it.lowercase() == lower }
        if (learnedMatch != null) {
            _uiState.value = current.copy(values = current.values.copy(serviceType = learnedMatch))
            return
        }

        // New custom label becomes the selection
        _uiState.value = current.copy(values = current.values.copy(serviceType = trimmedLabel))
    }

    /**
     * Persists the current form. A successful save calls [onSaved] and nothing else: the list and
     * the Detail screen are Flow-driven and show the new state by themselves.
     */
    fun save(onSaved: () -> Unit) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        // Double-submit guard: reject while saving, importing, or not submittable
        if (current.saving || current.importing || !ServiceEntryFormValidation.isSubmittable(current.values)) {
            return
        }

        _uiState.value = current.copy(saving = true, saveFailed = false)
        val submitted = current.values
        // While the write is in flight these files are no longer abandonable: `onCleared` must not
        // delete paths the transaction may already have committed (WR-05). They return to the
        // abandonable set only if the save fails.
        val submittedPhotos = persistedButUnsavedPaths.toSet()
        persistedButUnsavedPaths.clear()
        viewModelScope.launch {
            try {
                val entry = ServiceEntryFormValidation.toServiceEntry(entryId, vehicleId, submitted)
                repo.save(entry)
                markPhotosSaved()
                clearSaving()
                onSaved()
            } catch (cancelled: CancellationException) {
                // The write may or may not have committed: keep the files rather than risk
                // deleting ones the row now references. A rare orphan file is the safer failure.
                throw cancelled
            } catch (_: Exception) {
                persistedButUnsavedPaths.addAll(submittedPhotos)
                markSaveFailed()
            }
        }
    }

    /** Lets the screen retire the save-failure flag (after the Snackbar) without losing the form. */
    fun onSaveErrorShown() {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        if (current.saveFailed) _uiState.value = current.copy(saveFailed = false)
    }

    /**
     * Imports a picked gallery image: copies it into app storage under `service_entries` directory
     * and holds the returned **relative** location in the form. Single-flight import guard.
     */
    fun onPhotoPicked(picked: PlatformFile) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        if (current.importing || current.saving) return
        val fileName = nextPhotoFileName()

        _uiState.value = current.copy(importing = true, photoError = false)
        viewModelScope.launch {
            val relativePath = try {
                PhotoStorage.persist(picked, fileName, PhotoStorage.SERVICE_ENTRY_DIRECTORY)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                val latest = _uiState.value as? ServiceEntryEditUiState.Ready
                if (latest != null) {
                    _uiState.value = latest.copy(importing = false, photoError = true)
                }
                return@launch
            }

            val latest = _uiState.value as? ServiceEntryEditUiState.Ready ?: return@launch
            // The strip is additive (up to 5 photos): existing paths are not replacements and
            // must neither be deleted nor queued for deletion when a new photo is added (CR-01).
            persistedButUnsavedPaths.add(relativePath)
            _uiState.value = latest.copy(
                values = latest.values.copy(photoPaths = latest.values.photoPaths + relativePath),
                importing = false,
            )
        }
    }

    /**
     * Removes a photo from the form at [index]. Routes the file per bookkeeping:
     * session-owned → delete now; row-owned → queue in supersededPaths.
     */
    fun onPhotoRemoved(index: Int) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        val paths = current.values.photoPaths
        if (index !in paths.indices) return
        val removed = paths[index]
        val newPaths = paths.toMutableList().apply { removeAt(index) }

        if (persistedButUnsavedPaths.remove(removed)) {
            // Session-owned copy, nothing references it: delete immediately.
            PhotoStorage.delete(removed)
        } else {
            // Database-owned: queue for deletion after successful save.
            supersededPaths.add(removed)
        }
        _uiState.value = current.copy(values = current.values.copy(photoPaths = newPaths))
    }

    /** Adds a new empty part row. */
    fun onAddPart() {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        val newParts = current.values.parts + PartRow()
        _uiState.value = current.copy(values = current.values.copy(parts = newParts))
    }

    /** Updates a part row at [index]. Quantity stays digits-only (WR-08). */
    fun onPartChange(index: Int, name: String, quantity: String) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        val parts = current.values.parts.toMutableList()
        if (index in parts.indices) {
            parts[index] = PartRow(
                name = name,
                quantity = ServiceEntryFormValidation.sanitizeQuantity(quantity),
            )
            _uiState.value = current.copy(values = current.values.copy(parts = parts))
        }
    }

    /** Removes a part row at [index] immediately (unsaved form edit, no confirmation). */
    fun onRemovePart(index: Int) {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        val parts = current.values.parts.toMutableList()
        if (index in parts.indices) {
            parts.removeAt(index)
            _uiState.value = current.copy(values = current.values.copy(parts = parts))
        }
    }

    /** Lets the screen retire the photo-import flag once its message has been shown. */
    fun onPhotoImportFailed() {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        if (current.photoError) _uiState.value = current.copy(photoError = false)
    }

    /**
     * The abandon case: photos copied into app storage before any row referenced them are litter
     * once the form is torn down without a successful save, and this is the only place that can
     * still remove them.
     *
     * [supersededPaths] is deliberately not touched here — they always name files the saved row
     * still references, and removing them because a form was closed would destroy the user's only
     * copy. [PhotoStorage.delete] is non-suspend precisely so this call is legal from here.
     */
    override fun onCleared() {
        persistedButUnsavedPaths.forEach { PhotoStorage.delete(it) }
        super.onCleared()
    }

    /**
     * A name no earlier pick in this session can collide with: the entry id (or vehicle id for add)
     * plus a stamp that always advances.
     */
    private fun nextPhotoFileName(): String {
        val stamp = maxOf(Clock.System.now().toEpochMilliseconds(), lastPhotoStamp + 1L)
        lastPhotoStamp = stamp
        val prefix = if (isAdd) vehicleId else entryId
        return "$prefix-$stamp.jpg"
    }

    /** Successful save: clear session-owned flags and delete superseded paths. */
    private fun markPhotosSaved() {
        persistedButUnsavedPaths.clear()
        supersededPaths.forEach { PhotoStorage.delete(it) }
        supersededPaths.clear()
    }

    private fun clearSaving() {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = false)
    }

    private fun markSaveFailed() {
        val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = true)
    }
}

/** Extension to convert domain entry to form values. */
private fun ServiceEntry.toFormValues(): ServiceEntryFormValues = ServiceEntryFormValues(
    dateEpochMs = dateEpochMs,
    mileage = mileageKm.toString(),
    serviceType = serviceType,
    cost = costRupiah?.toString() ?: "",
    notes = notes.orEmpty(),
    workshop = workshop.orEmpty(),
    mechanic = mechanic.orEmpty(),
    parts = parts.map { PartRow(it.name, it.quantity.toString()) },
    photoPaths = photoPaths,
)