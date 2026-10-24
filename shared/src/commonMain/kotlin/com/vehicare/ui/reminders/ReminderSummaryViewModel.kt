package com.vehicare.ui.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vehicare.data.ReminderRepository
import com.vehicare.domain.ReminderOrdering
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.ReminderStatus
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The vehicle Detail's Reminders summary row content (D-45): the total rule count and the
 * vehicle's most urgent **enabled** rule.
 *
 * [ruleCount] counts **every** rule, including disabled ones, so the row can tell "no rules at
 * all" from "rules exist but every one is off" — the two cases the meta line renders differently.
 * [top] is `null` exactly when no enabled rule exists; otherwise it is the same row
 * [ReminderOrdering.topRule] would hand the Reminders list and the dashboard, so all three
 * surfaces agree on which rule is "the" rule.
 */
data class ReminderSummary(
    val ruleCount: Int,
    val top: ReminderRow?,
)

/**
 * The vehicle Detail's only reminder read: **one aggregate** per vehicle, joined by the one shared
 * engine ([ReminderStatus.evaluateAll]) and the one shared order ([ReminderOrdering.topRule]), so
 * this row can never disagree with the Reminders list or the dashboard about which rule is most
 * urgent (C-28/D-63).
 *
 * Three **flat** reads — this vehicle's rules, every type baseline, every vehicle's stats — mirror
 * [RemindersViewModel]'s join; there is deliberately no per-rule query (RESEARCH U6 Pattern 3).
 *
 * [summary] starts at `null`, which means "not resolved yet (or unreadable)" and is what makes the
 * row render its title alone (the [ServiceSummaryViewModel] precedent). A read failure also
 * resolves to `null` rather than a fourth surface: the row stays tappable either way.
 */
class ReminderSummaryViewModel(
    repo: ReminderRepository,
    vehicleId: Long,
) : ViewModel() {

    val summary: StateFlow<ReminderSummary?> =
        combine(
            repo.observeRules(vehicleId),
            repo.observeTypeBaselines(),
            repo.observeVehicleStats(),
        ) { rules, baselines, stats ->
            val rows = ReminderStatus.evaluateAll(
                rules = rules,
                baselines = baselines,
                stats = stats,
                today = ServiceDates.today(),
            )
            ReminderSummary(
                ruleCount = rules.size,
                top = ReminderOrdering.topRule(rows),
            )
        }
            // Widen to nullable first: an unreadable aggregate resolves to `null` (= the row shows
            // its title alone), which a non-null flow cannot emit.
            .map<ReminderSummary, ReminderSummary?> { it }
            .catch { emit(null) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = null,
            )
}
