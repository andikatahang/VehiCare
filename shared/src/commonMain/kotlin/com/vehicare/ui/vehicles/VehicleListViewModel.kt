package com.vehicare.ui.vehicles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.ReminderRepository
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderOrdering
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.ReminderStatus
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleReminderGroup
import com.vehicare.domain.VehicleStats
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
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

    /** Rows arrived; each is an urgency-ordered dashboard row (D-57, DSH-01). */
    data class Content(val items: List<VehicleDashboardRow>) : VehicleListUiState

    /** The observation flow failed; [message] carries the raw cause for the recovery path. */
    data class Error(val message: String?) : VehicleListUiState
}

/**
 * One vehicle on the home dashboard (D-57/D-58/D-59, DSH-01 amended 2026-10-23).
 *
 * [top] is the vehicle's most urgent **enabled** rule with its evaluation, or `null` when the
 * vehicle has no enabled rule (none at all, or all turned off).
 *
 * [otherDueCount] counts the vehicle's **other** enabled rules whose state is due soon, overdue
 * or due now — the displayed rule ([top]) is never counted again, so the suffix is absent at zero
 * and `+0 due` can never render.
 *
 * [lastServiceEpochMs] is the vehicle's latest service date from the same stats read that feeds
 * the evaluation join below, `null` when the vehicle has no service entry — the card's status
 * line is the only consumer (DSH-01/C-30).
 */
data class VehicleDashboardRow(
    val vehicle: Vehicle,
    val top: ReminderRow?,
    val otherDueCount: Int,
    val lastServiceEpochMs: Long?,
)

/**
 * Counts [rows] whose rule is enabled, is not [displayedRuleId], and whose evaluation state is
 * [DueState.DueSoon], [DueState.Overdue] or [DueState.DueNow] (D-59).
 *
 * [DueState.NoMileageData] and [DueState.Upcoming] never count, and a disabled row can never be
 * due — it has no evaluation at all.
 */
internal fun dashboardDueCount(rows: List<ReminderRow>, displayedRuleId: Long?): Int =
    rows.count { row ->
        row.rule.enabled &&
            row.rule.id != displayedRuleId &&
            when (row.evaluation?.state) {
                is DueState.DueSoon, is DueState.Overdue, DueState.DueNow -> true
                else -> false
            }
    }

/**
 * Reads the vehicle list and every reminder flow through four flat reads, joins them through the
 * engine's one evaluation entry point once, orders the result with the shared ordering helper,
 * and maps each sorted group into a [VehicleDashboardRow] (D-57, DSH-01).
 *
 * Everything below the repository boundary is invisible here by design: no driver, no query, no
 * generated type above the data layer (T-01-22), and no per-vehicle read anywhere (RESEARCH U6,
 * PITFALLS § SQL 3).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleListViewModel(
    private val repo: VehicleRepository,
    private val reminderRepo: ReminderRepository,
) : ViewModel() {

    /**
     * The count behind `flatMapLatest`: bumping it cancels the previous read and restarts the
     * source query, which is exactly what the "Try again" control needs after an error.
     */
    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<VehicleListUiState> =
        refresh
            .flatMapLatest {
                combine(
                    repo.observeVehicles(),
                    reminderRepo.observeAllRules(),
                    reminderRepo.observeTypeBaselines(),
                    reminderRepo.observeVehicleStats(),
                ) { vehicles, rules, baselines, stats ->
                    if (vehicles.isEmpty()) {
                        VehicleListUiState.Empty
                    } else {
                        VehicleListUiState.Content(
                            buildDashboard(vehicles, rules, baselines, stats),
                        )
                    }
                }
                    // Terminal for this observation only (CR-02 pattern, mirroring
                    // RemindersViewModel): catching here, inside flatMapLatest, leaves the
                    // outer `refresh` collection alive, so a later retry() bump re-arms all
                    // four reads instead of leaving a permanently completed flow.
                    .catch { emit(VehicleListUiState.Error(message = it.message)) }
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VehicleListUiState.Loading,
            )

    /** Restart the underlying observation after the error surface (the Try again control). */
    fun retry() {
        refresh.value++
    }

    private fun buildDashboard(
        vehicles: List<Vehicle>,
        rules: List<ReminderRule>,
        baselines: List<TypeBaseline>,
        stats: List<VehicleStats>,
    ): List<VehicleDashboardRow> {
        val vehiclesById = vehicles.associateBy { it.id }
        val statsById = stats.associateBy { it.vehicleId }

        val allRows = ReminderStatus.evaluateAll(rules, baselines, stats, ServiceDates.today())
        val rowsByVehicle = allRows.groupBy { it.rule.vehicleId }

        val groups = vehicles.map { vehicle ->
            VehicleReminderGroup(
                vehicleId = vehicle.id,
                vehicleName = vehicle.name,
                rows = rowsByVehicle[vehicle.id].orEmpty(),
            )
        }

        return ReminderOrdering.sortVehicles(groups).map { group ->
            val top = ReminderOrdering.topRule(group.rows)
            VehicleDashboardRow(
                vehicle = vehiclesById.getValue(group.vehicleId),
                top = top,
                otherDueCount = dashboardDueCount(group.rows, top?.rule?.id),
                lastServiceEpochMs = statsById[group.vehicleId]?.lastServiceEpochMs,
            )
        }
    }
}
