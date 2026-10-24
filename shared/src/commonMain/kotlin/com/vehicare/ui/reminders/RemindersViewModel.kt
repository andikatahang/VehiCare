package com.vehicare.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.settings.SettingsProvider
import com.vehicare.domain.repository.ReminderRepository
import com.vehicare.domain.ReminderOrdering
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.ReminderStatus
import com.vehicare.notifications.FirstReminderAskSignal
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The Reminders screen's state matrix (UI-SPEC States & Interaction Contract).
 *
 * The same four states every Phase 1/2 list uses, plus one flag on [Content] so a failed switch
 * write has somewhere honest to report itself without inventing a fifth state.
 */
sealed interface RemindersUiState {

    /** The first frame while the observation flow is still connecting — never a blank screen. */
    data object Loading : RemindersUiState

    /** The vehicle has no reminder rules yet — the state that carries the Add CTA. */
    data object Empty : RemindersUiState

    /**
     * Rows arrived, already ordered most urgent first.
     *
     * [rows] carries **domain values plus their evaluation and no formatted text**: the due state is
     * a [com.vehicare.domain.DueState] and the interval is a pair of nullable counts. The screen
     * resolves every sentence through `ReminderCopy`, which is what keeps this ViewModel testable
     * without a resource container and keeps the copy in exactly one place (C-28/C-40).
     *
     * [toggleFailed] is a one-shot flag: it is set when a switch write did not reach the database and
     * cleared by [RemindersViewModel.acknowledgeToggleFailure] once the Snackbar has been shown.
     */
    data class Content(
        val rows: List<ReminderRow>,
        val toggleFailed: Boolean = false,
    ) : RemindersUiState

    /** The observation flow failed; [message] carries the raw cause for the recovery path. */
    data class Error(val message: String?) : RemindersUiState
}

/**
 * Reads one vehicle's reminder rules and evaluates them for display (D-41/D-43).
 *
 * Three **flat** reads — this vehicle's rules, every type baseline, every vehicle's stats — are
 * joined by the pure engine in one call per emission; there is deliberately no per-rule query
 * (RESEARCH U6 Pattern 3). The engine's `evaluateAll` is the single join, and `ReminderOrdering`
 * is the single order, so this list and the dashboard cannot disagree about which baseline a rule
 * uses or which rule is more urgent.
 *
 * **The switch is one tap with no confirmation and no optimistic local state (D-43).** [toggle]
 * writes through the repository and never edits [rows]: the row's new value arrives from the
 * observation flow, so a failed write cannot leave the UI showing a state the database rejected
 * (T-03-19). Failure only sets [RemindersUiState.Content.toggleFailed], which the screen reports and
 * acknowledges.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModel(
    private val vehicleId: Long,
    private val repo: ReminderRepository,
) : ViewModel() {

    /**
     * The count behind `flatMapLatest`: bumping it cancels the previous read and restarts the three
     * queries, which is exactly what the "Try again" control needs after an error.
     */
    private val refresh = MutableStateFlow(0)

    /**
     * One-shot "the last switch write failed" flag. It lives outside the observation flow because it
     * describes a user action, not stored data, and it is merged into [RemindersUiState.Content] so
     * the screen reads one state object.
     */
    private val toggleFailed = MutableStateFlow(false)

    /**
     * One-shot "a first add just published an ask for this vehicle" flag — exactly like
     * [toggleFailed]. It is not part of [RemindersUiState] because it is an event, not a data
     * state, and folding it into the matrix would make [RemindersUiState.Content] equality depend
     * on a transient signal.
     *
     * The single collector below iterates [FirstReminderAskSignal.pending]: a non-null value that
     * matches this ViewModel's [vehicleId] is consumed (so it is never delivered twice) and raises
     * the flag; a non-matching vehicle's id is left untouched on the bus, so another vehicle's
     * Reminders screen can still consume it. The bus is a process-wide object, so no constructor
     * parameter changes and `AppModule` stays untouched.
     */
    private val _pendingPermissionAsk = MutableStateFlow(false)
    val pendingPermissionAsk: StateFlow<Boolean> = _pendingPermissionAsk.asStateFlow()

    init {
        viewModelScope.launch {
            FirstReminderAskSignal.pending.collect { pendingVehicleId ->
                if (pendingVehicleId != null && pendingVehicleId == vehicleId) {
                    FirstReminderAskSignal.consume()
                    _pendingPermissionAsk.value = true
                }
            }
        }
    }

    /** Lets the screen retire the one-shot ask flag once it has reacted to it. */
    fun consumePermissionAsk() {
        _pendingPermissionAsk.value = false
    }

    val uiState: StateFlow<RemindersUiState> =
        combine(
            observeRemindersUiState(repo = repo, vehicleId = vehicleId, refresh = refresh),
            toggleFailed,
        ) { state, failed ->
            if (state is RemindersUiState.Content) state.copy(toggleFailed = failed) else state
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = RemindersUiState.Loading,
            )

    /**
     * Turns a rule on or off in place (D-43). One tap, no confirmation, no local mutation.
     *
     * The write is fire-and-forget from the UI's point of view: success is observable only as the
     * row's new value arriving from the database, which is what makes a failed write impossible to
     * mistake for a successful one. A cancellation is rethrown so a destroyed screen does not report
     * a spurious failure.
     */
    fun toggle(ruleId: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                repo.setEnabled(ruleId, enabled)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                toggleFailed.value = true
            }
        }
    }

    /** Retires the failure flag once its Snackbar has been shown. */
    fun acknowledgeToggleFailure() {
        toggleFailed.value = false
    }

    /** Restart the underlying observation after the error surface (the Try again control). */
    fun retry() {
        refresh.value++
    }
}

/**
 * Assembles the reminders state flow.
 *
 * The error handler sits **inside** the per-observation flow, so a failure ends only that read and
 * leaves the outer `flatMapLatest` alive: bumping [refresh] re-runs the three queries and recovers
 * the screen (the CR-02 pattern the service history screen established).
 *
 * Kept top-level and repository-injected so the ordering, the empty/content mapping and the retry
 * path are testable without a ViewModel or a main dispatcher.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun observeRemindersUiState(
    repo: ReminderRepository,
    vehicleId: Long,
    refresh: Flow<Int>,
): Flow<RemindersUiState> =
    refresh.flatMapLatest {
        combine(
            repo.observeRules(vehicleId),
            repo.observeTypeBaselines(),
            repo.observeVehicleStats(),
            SettingsProvider.observeIntervalMonthsOrDefault(),
        ) { rules, baselines, stats, globalMonths ->
            val rows = ReminderOrdering.sortRules(
                ReminderStatus.evaluateAll(
                    rules = rules,
                    baselines = baselines,
                    stats = stats,
                    today = ServiceDates.today(),
                    // D-1: the same effective interval the delivery path evaluates with
                    // (261024-u9g) — the list never disagrees with the notifications.
                    globalIntervalMonths = globalMonths,
                ),
            )
            if (rows.isEmpty()) RemindersUiState.Empty else RemindersUiState.Content(rows)
        }
            // Terminal for this observation only: a `refresh` bump re-arms all three queries.
            .catch { emit(RemindersUiState.Error(message = it.message)) }
    }
