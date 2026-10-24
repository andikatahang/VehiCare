package com.vehicare.domain

import com.vehicare.util.ServiceDates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The ordering contract (D-57, C-29): four buckets, and a tie-break chain that is total.
 *
 * The fixtures are hand-built [ReminderEvaluation]s — this suite is about order, not arithmetic —
 * except where the assertion is about a real evaluated value. Every rule carries plan 01's two
 * bookkeeping markers as `null`: ordering never reads them.
 */
class ReminderOrderingTest {

    // ---- fixtures -------------------------------------------------------------------------

    private fun rule(
        id: Long,
        vehicleId: Long = 1L,
        serviceType: String = "Oil change",
    ): ReminderRule = ReminderRule(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = 5_000,
        intervalMonths = null,
        leadKm = null,
        leadDays = null,
        enabled = true,
        createdAtEpochMs = ServiceDates.toStorage(LocalDate(2026, 1, 1)),
        notifiedCycleKey = null,
        armedCycleKey = null,
    )

    private fun row(
        id: Long,
        state: DueState?,
        axis: ReminderAxis? = ReminderAxis.DISTANCE,
        urgency: Double = 1.0,
        vehicleId: Long = 1L,
        serviceType: String = "Oil change",
    ): ReminderRow = ReminderRow(
        rule = rule(id = id, vehicleId = vehicleId, serviceType = serviceType),
        evaluation = state?.let {
            ReminderEvaluation(state = it, axis = axis, urgency = urgency, cycleKey = "k$id")
        },
    )

    private fun group(vehicleId: Long, name: String, vararg rows: ReminderRow): VehicleReminderGroup =
        VehicleReminderGroup(vehicleId = vehicleId, vehicleName = name, rows = rows.toList())

    private fun ids(rows: List<ReminderRow>): List<Long> = rows.map { it.rule.id }

    private fun vehicleIds(groups: List<VehicleReminderGroup>): List<Long> = groups.map { it.vehicleId }

    // ---- the four buckets -----------------------------------------------------------------

    @Test
    fun overdueThenDueSoonThenUpcomingThenDisabled() {
        val rows = listOf(
            row(4, state = null),
            row(3, DueState.Upcoming(kmLeft = 2_000, dueDate = null), urgency = 0.4),
            row(2, DueState.DueSoon(kmLeft = 500, daysLeft = null), urgency = 0.9),
            row(1, DueState.Overdue(kmOver = 300, daysOver = null), urgency = 1.06),
        )

        assertEquals(listOf(1L, 2L, 3L, 4L), ids(ReminderOrdering.sortRules(rows)))
    }

    @Test
    fun dueNowSortsWithOverdue() {
        val rows = listOf(
            row(3, DueState.Upcoming(kmLeft = 100, dueDate = null), urgency = 0.98),
            row(2, DueState.DueNow, urgency = 1.0),
            row(1, DueState.Overdue(kmOver = 3_000, daysOver = null), urgency = 1.6),
        )

        // Both are due, and the more-overdue fraction still ranks ahead of the boundary.
        assertEquals(listOf(1L, 2L, 3L), ids(ReminderOrdering.sortRules(rows)))
    }

    @Test
    fun aMoreOverdueFractionRanksBeforeALessOverdueOneAcrossAxes() {
        val rows = listOf(
            // 14 days over on a 30-day interval: 1.466…
            row(1, DueState.Overdue(kmOver = null, daysOver = 14), axis = ReminderAxis.TIME, urgency = 44.0 / 30.0),
            // 3 000 km over on a 5 000 km interval: 1.6
            row(2, DueState.Overdue(kmOver = 3_000, daysOver = null), urgency = 1.6),
        )

        assertEquals(listOf(2L, 1L), ids(ReminderOrdering.sortRules(rows)))
    }

    // ---- the tie-break chain --------------------------------------------------------------

    @Test
    fun theDistanceAxisWinsAnExactUrgencyTie() {
        val rows = listOf(
            row(1, DueState.DueNow, axis = ReminderAxis.TIME, urgency = 1.0),
            row(2, DueState.DueNow, axis = ReminderAxis.DISTANCE, urgency = 1.0),
        )

        // Lower id loses to the axis rule: C-29's tie-break (a) comes before (b).
        assertEquals(listOf(2L, 1L), ids(ReminderOrdering.sortRules(rows)))
    }

    @Test
    fun theLowerRuleIdBreaksASameUrgencySameAxisTie() {
        val rows = listOf(
            row(9, DueState.Overdue(kmOver = 500, daysOver = null), urgency = 1.1),
            row(3, DueState.Overdue(kmOver = 500, daysOver = null), urgency = 1.1),
            row(7, DueState.Overdue(kmOver = 500, daysOver = null), urgency = 1.1),
        )

        assertEquals(listOf(3L, 7L, 9L), ids(ReminderOrdering.sortRules(rows)))
    }

    // ---- unevaluable and disabled ---------------------------------------------------------

    @Test
    fun noMileageDataLandsAfterEveryEvaluableUpcomingRowAndBeforeDisabledRows() {
        val rows = listOf(
            row(1, state = null),
            row(2, DueState.NoMileageData, axis = null, urgency = -1.0),
            row(3, DueState.Upcoming(kmLeft = 4_000, dueDate = null), urgency = 0.2),
            row(4, DueState.Upcoming(kmLeft = 10, dueDate = null), urgency = 0.998),
        )

        assertEquals(listOf(4L, 3L, 2L, 1L), ids(ReminderOrdering.sortRules(rows)))
    }

    @Test
    fun disabledRowsAreNewestFirst() {
        val rows = listOf(
            row(2, state = null),
            row(9, state = null),
            row(4, state = null),
        )

        assertEquals(listOf(9L, 4L, 2L), ids(ReminderOrdering.sortRules(rows)))
    }

    // ---- topRule --------------------------------------------------------------------------

    @Test
    fun topRuleSkipsDisabledRows() {
        val rows = listOf(
            row(9, state = null),
            row(2, DueState.Upcoming(kmLeft = 1_000, dueDate = null), urgency = 0.8),
            row(5, DueState.DueSoon(kmLeft = 100, daysLeft = null), urgency = 0.98),
        )

        assertEquals(5L, ReminderOrdering.topRule(rows)?.rule?.id)
    }

    @Test
    fun topRuleIsNullWhenEveryRowIsDisabled() {
        val rows = listOf(row(1, state = null), row(2, state = null))

        assertNull(ReminderOrdering.topRule(rows))
        assertNull(ReminderOrdering.topRule(emptyList()))
    }

    // ---- vehicles -------------------------------------------------------------------------

    @Test
    fun vehiclesWithAnActiveReminderComeBeforeVehiclesWithoutOne() {
        val groups = listOf(
            group(1, "Only turned off", row(1, state = null, vehicleId = 1)),
            group(2, "Overdue", row(2, DueState.Overdue(kmOver = 300, daysOver = null), vehicleId = 2)),
        )

        assertEquals(listOf(2L, 1L), vehicleIds(ReminderOrdering.sortVehicles(groups)))
    }

    @Test
    fun vehiclesWithNoEnabledRuleAreNewestFirst() {
        val groups = listOf(
            group(3, "Older", row(1, state = null, vehicleId = 3)),
            group(8, "Newer", row(2, state = null, vehicleId = 8)),
            group(5, "Middle", row(3, state = null, vehicleId = 5)),
        )

        // D-16's newest-first order, preserved inside the fourth group by D-57.
        assertEquals(listOf(8L, 5L, 3L), vehicleIds(ReminderOrdering.sortVehicles(groups)))
    }

    @Test
    fun theFullVehicleChainResolvesANearTie() {
        val groups = listOf(
            // Same urgency, same axis, same rule id as g2 but a name that sorts last.
            group(1, "Zeta", row(3, DueState.Overdue(kmOver = 100, daysOver = null), urgency = 1.5, vehicleId = 1)),
            // Same as g1's rule id, but "astra" < "zeta" case-insensitively.
            group(2, "astra", row(3, DueState.Overdue(kmOver = 100, daysOver = null), urgency = 1.5, vehicleId = 2)),
            // Lower top-rule id wins before any name comparison.
            group(3, "Astra", row(2, DueState.Overdue(kmOver = 100, daysOver = null), urgency = 1.5, vehicleId = 3)),
            // Identical to g3 in every key above the vehicle id.
            group(4, "Astra", row(2, DueState.Overdue(kmOver = 100, daysOver = null), urgency = 1.5, vehicleId = 4)),
            // The time axis loses an exact-urgency tie to g3/g4's distance axis.
            group(5, "Astra", row(2, DueState.Overdue(kmOver = null, daysOver = 1), axis = ReminderAxis.TIME, urgency = 1.5, vehicleId = 5)),
            // The highest urgency wins the whole list.
            group(6, "Astra", row(2, DueState.Overdue(kmOver = 200, daysOver = null), urgency = 1.6, vehicleId = 6)),
        )

        assertEquals(
            listOf(6L, 3L, 4L, 2L, 1L, 5L),
            vehicleIds(ReminderOrdering.sortVehicles(groups)),
        )
    }

    @Test
    fun aVehicleGroupIsOrderedByItsMostUrgentEnabledRuleNotItsFirstRow() {
        val groups = listOf(
            group(
                1,
                "Has one overdue rule buried behind an upcoming one",
                row(1, DueState.Upcoming(kmLeft = 4_000, dueDate = null), urgency = 0.2, vehicleId = 1),
                row(2, DueState.Overdue(kmOver = 300, daysOver = null), urgency = 1.06, vehicleId = 1),
            ),
            group(2, "Only due soon", row(3, DueState.DueSoon(kmLeft = 100, daysLeft = null), urgency = 0.98, vehicleId = 2)),
        )

        assertEquals(listOf(1L, 2L), vehicleIds(ReminderOrdering.sortVehicles(groups)))
    }

    // ---- purity and determinism -----------------------------------------------------------

    @Test
    fun sortingTheSameSnapshotTwiceYieldsTheSameOrder() {
        val rows = listOf(
            row(4, DueState.Overdue(kmOver = 100, daysOver = null), urgency = 1.2),
            row(2, DueState.DueSoon(kmLeft = 100, daysLeft = null), urgency = 0.98),
            row(7, DueState.Upcoming(kmLeft = 100, dueDate = null), urgency = 0.5),
            row(1, state = null),
            row(9, DueState.Overdue(kmOver = null, daysOver = 3), axis = ReminderAxis.TIME, urgency = 1.1),
        )

        assertEquals(ids(ReminderOrdering.sortRules(rows)), ids(ReminderOrdering.sortRules(rows)))
    }

    @Test
    fun sortRulesDoesNotMutateItsInput() {
        val rows = mutableListOf(
            row(2, DueState.Upcoming(kmLeft = 100, dueDate = null), urgency = 0.5),
            row(1, DueState.Overdue(kmOver = 100, daysOver = null), urgency = 1.2),
        )
        val original = ids(rows)

        val sorted = ReminderOrdering.sortRules(rows)

        assertEquals(original, ids(rows), "the input list must keep its order")
        assertTrue(sorted !== rows, "sorting must return a new list")
        assertEquals(listOf(1L, 2L), ids(sorted))
    }
}
