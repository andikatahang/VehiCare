package com.vehicare.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.domain.repository.ReminderRepository
import com.vehicare.domain.ReminderRule
import com.vehicare.notifications.FirstReminderAskSignal
import com.vehicare.ui.service.SelectionMode
import com.vehicare.ui.service.confirmCustomServiceType
import com.vehicare.ui.service.selectServiceType
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The Add/Edit reminder form's state matrix (UI-SPEC §Reminder editor).
 *
 * `ruleId == 0L` starts in [Ready] with every field empty and nothing preselected; an edit starts
 * in [Loading] and resolves to [Ready] or [NotFound]. There is no fourth state and no blank or
 * half-populated form (the Phase 1/2 precedent).
 */
sealed interface ReminderEditUiState {

    /** The edit destination is reading the rule it was asked for. Never a blank screen. */
    data object Loading : ReminderEditUiState

    /**
     * @param values       the raw field content, exactly as typed — no parsed numbers, no
     *                     formatted sentence, so this class stays free of Compose and resources.
     * @param learnedTypes the custom labels the picker renders after the presets
     * @param saving       a save is in flight — the double-submit guard is checked on this state
     * @param saveFailed   one-shot failure flag the screen consumes to show the save-error Snackbar
     * @param deleting     a delete is in flight — the single-flight guard is checked on this state
     * @param deleteFailed one-shot failure flag the screen consumes to show the delete-error
     *                     Snackbar
     */
    data class Ready(
        val values: ReminderFormValues,
        val learnedTypes: List<String> = emptyList(),
        val saving: Boolean = false,
        val saveFailed: Boolean = false,
        val deleting: Boolean = false,
        val deleteFailed: Boolean = false,
    ) : ReminderEditUiState

    /**
     * The row this destination was opened for no longer exists, or belongs to a different vehicle
     * than the route's `vehicleId` named (T-03-22).
     */
    data object NotFound : ReminderEditUiState
}

/**
 * Owns the single add/edit reminder form (UI-SPEC §Reminder editor, D-08/D-42): it loads a row for
 * the edit case, mutates the field values through one entry point re-validated on every change, and
 * saves or deletes through [ReminderRepository] with the same guards the Phase 1/2 forms use.
 *
 * [learnedTypes] is a **flow**, not `ServiceEntryRepository`, on purpose: the editor needs exactly
 * one read from the entry side and taking the whole repository would force every test to fake an
 * interface with a dozen unrelated members. `AppModule` supplies
 * `get<ServiceEntryRepository>().observeLearnedTypes()`.
 *
 * Every rule for a field's content lives in [ReminderFormValidation] — this ViewModel never
 * restates a sanitizer, the submit gate or the domain mapping.
 *
 * @param vehicleId the vehicle this rule belongs to — the authority a loaded rule is checked
 *   against (T-03-22), never the loaded row's own `vehicleId`.
 * @param ruleId `0L` means add; any other id is loaded for edit and saved in place.
 */
class ReminderEditViewModel(
    private val repo: ReminderRepository,
    private val learnedTypes: Flow<List<String>>,
    private val vehicleId: Long,
    private val ruleId: Long,
) : ViewModel() {

    private val isAdd: Boolean = ruleId == 0L

    /**
     * The add case's synchronous stamp, or the stored rule's own stamp once the edit path has
     * loaded it. An edit never rewrites this on save — an update must preserve the row's original
     * creation date (D-49/D-51/D-53).
     */
    private var createdAtEpochMs: Long = ServiceDates.toStorage(ServiceDates.today())

    /**
     * True once the edit path has consumed the row's first emission. The edit path accepts **only
     * the first** emission and ignores every later one, so a repository change (or the write the
     * save itself triggers) can never overwrite a field the user has already edited.
     */
    private var initialLoadApplied: Boolean = false

    private val _uiState = MutableStateFlow<ReminderEditUiState>(
        if (isAdd) {
            ReminderEditUiState.Ready(values = ReminderFormValues())
        } else {
            ReminderEditUiState.Loading
        },
    )

    val uiState: StateFlow<ReminderEditUiState> = _uiState

    init {
        if (isAdd) {
            // Add mode: a missing or failed learned list must never disable Save — the presets
            // still render — so a failure here is swallowed rather than surfaced.
            viewModelScope.launch {
                try {
                    val learned = learnedTypes.first()
                    val current = _uiState.value as? ReminderEditUiState.Ready ?: return@launch
                    _uiState.value = current.copy(learnedTypes = learned)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A failed prefill must never disable Save.
                }
            }
        } else {
            // Edit mode: load the row once. A failure before the first emission is NotFound; a
            // late failure leaves the user's filled form untouched (the Phase 2 rule).
            viewModelScope.launch {
                repo.observeRule(ruleId)
                    .catch {
                        if (!initialLoadApplied) {
                            initialLoadApplied = true
                            _uiState.value = ReminderEditUiState.NotFound
                        }
                    }
                    .collect { rule ->
                        if (initialLoadApplied) return@collect
                        initialLoadApplied = true
                        _uiState.value = resolveLoadedRule(rule)
                    }
            }
        }
    }

    /**
     * Builds the next state from the edit path's first emission.
     *
     * A `null` row or one whose `vehicleId` disagrees with the route's authority resolves to
     * [ReminderEditUiState.NotFound] (T-03-22) — a stale or crafted rule id can never edit another
     * vehicle's rule. Seeding failing inside the collector (the learned-types read) also fails
     * closed to [ReminderEditUiState.NotFound] rather than leaving the screen on [Loading] forever.
     */
    private suspend fun resolveLoadedRule(rule: ReminderRule?): ReminderEditUiState {
        if (rule == null || rule.vehicleId != vehicleId) {
            return ReminderEditUiState.NotFound
        }
        return try {
            createdAtEpochMs = rule.createdAtEpochMs
            val learned = learnedTypes.first()
            ReminderEditUiState.Ready(
                values = ReminderFormValues(
                    serviceTypes = listOf(rule.serviceType),
                    intervalKm = rule.intervalKm?.toString().orEmpty(),
                    // No months field anymore (261024-u9g, D-3): the legacy stored value is
                    // intentionally NOT rewritten here — an edit rebuilds the form from the
                    // km axis only, and a save writes intervalMonths = null (D-3's fallback).
                    leadKm = rule.leadKm?.toString().orEmpty(),
                    leadDays = rule.leadDays?.toString().orEmpty(),
                    enabled = rule.enabled,
                ),
                learnedTypes = learned,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ReminderEditUiState.NotFound
        }
    }

    /**
     * The single text entry point. Dispatches on [field] to [ReminderFormValidation.sanitizeKm] for
     * the two distance fields and [ReminderFormValidation.sanitizeMonths] for the "days before"
     * field, then writes one `copy`. No composable may sanitize on its own.
     */
    fun onFieldChange(field: ReminderFormField, raw: String) {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        val sanitized = when (field) {
            ReminderFormField.INTERVAL_KM, ReminderFormField.LEAD_KM ->
                ReminderFormValidation.sanitizeKm(raw)

            ReminderFormField.LEAD_DAYS ->
                ReminderFormValidation.sanitizeMonths(raw)
        }
        val values = when (field) {
            ReminderFormField.INTERVAL_KM -> current.values.copy(intervalKm = sanitized)
            ReminderFormField.LEAD_KM -> current.values.copy(leadKm = sanitized)
            ReminderFormField.LEAD_DAYS -> current.values.copy(leadDays = sanitized)
        }
        _uiState.value = current.copy(values = values)
    }

    /**
     * Single-select chip tap (C-32): a later tap on another chip **moves** the selection, and the
     * already-selected chip's tap is a no-op — this group never deselects.
     */
    fun onTypeSelected(label: String) {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        val next = selectServiceType(label, current.values.serviceTypes, SelectionMode.Single)
        _uiState.value = current.copy(values = current.values.copy(serviceTypes = next))
    }

    /**
     * Custom-type confirmation (D-40). Passing an empty current selection is the single-select
     * rule: the confirmed label **replaces** the selection instead of appending, while
     * [confirmCustomServiceType] still resolves a case-insensitive match to the preset's or
     * learned label's own casing. Writes both the next selection and the next learned-chip list in
     * one `copy`, so a brand-new label always has a chip to render.
     */
    fun onCustomTypeConfirmed(label: String) {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        val result = confirmCustomServiceType(
            label = label,
            selected = emptyList(),
            learned = current.learnedTypes,
        )
        _uiState.value = current.copy(
            values = current.values.copy(serviceTypes = result.selected),
            learnedTypes = result.learned,
        )
    }

    /** The "Reminder on" switch. */
    fun onEnabledChange(enabled: Boolean) {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        _uiState.value = current.copy(values = current.values.copy(enabled = enabled))
    }

    /**
     * Persists the current form. A successful save calls [onSaved] and nothing else: the list and
     * the Detail screen are Flow-driven and show the new state by themselves.
     *
     * Never touches `notifiedCycleKey`: the factory leaves it null and only the claim writes it
     * (D-51/D-53). [FirstReminderAskSignal.publish] runs **after** [repo.save] returns and
     * **before** [onSaved] — publish only after the write landed (D-54 — a failed save must
     * never ask), publish before the pop (the Reminders screen must find it already pending) —
     * and **only when [isAdd] is true**: the amended D-54 (2026-10-23) says "the first successful
     * add", so an update never touches this bus. Whether a dialog then actually appears is still
     * the policy's call: the signal only says "a reminder was just added", never "ask now".
     */
    fun save(onSaved: () -> Unit) {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        if (current.saving || current.deleting ||
            !ReminderFormValidation.isSubmittable(current.values)
        ) {
            return
        }

        _uiState.value = current.copy(saving = true, saveFailed = false)
        val submitted = current.values
        viewModelScope.launch {
            try {
                val rule = ReminderFormValidation.toRule(
                    id = ruleId,
                    vehicleId = vehicleId,
                    createdAtEpochMs = createdAtEpochMs,
                    values = submitted,
                )
                repo.save(rule)
                clearSaving()
                if (isAdd) FirstReminderAskSignal.publish(vehicleId)
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                markSaveFailed()
            }
        }
    }

    /**
     * Deletes the loaded rule. Refused in add mode and while another save or delete is already in
     * flight — the single-flight guard that makes a second confirm while a delete is in flight
     * impossible.
     */
    fun delete(onDeleted: () -> Unit) {
        if (isAdd) return
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        if (current.deleting || current.saving) return

        _uiState.value = current.copy(deleting = true, deleteFailed = false)
        viewModelScope.launch {
            try {
                repo.delete(ruleId)
                onDeleted()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                markDeleteFailed()
            }
        }
    }

    /** Lets the screen retire the save-failure flag (after the Snackbar) without losing the form. */
    fun onSaveErrorShown() {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        if (current.saveFailed) _uiState.value = current.copy(saveFailed = false)
    }

    /** Lets the screen retire the delete-failure flag (after the Snackbar) without losing the form. */
    fun onDeleteErrorShown() {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        if (current.deleteFailed) _uiState.value = current.copy(deleteFailed = false)
    }

    private fun clearSaving() {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = false)
    }

    private fun markSaveFailed() {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        _uiState.value = current.copy(saving = false, saveFailed = true)
    }

    private fun markDeleteFailed() {
        val current = _uiState.value as? ReminderEditUiState.Ready ?: return
        _uiState.value = current.copy(deleting = false, deleteFailed = true)
    }
}
