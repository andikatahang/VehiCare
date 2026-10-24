package com.vehicare.domain

import com.vehicare.util.ServiceDates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The calculation core's contract (C-28, D-46…D-51, D-63): both axes, the exact-zero boundary,
 * both lead windows, both D-49 fallbacks, the urgency key and the cycle key.
 *
 * Every fixture is a fixed civil date built through [ServiceDates.toStorage] — `ServiceDates.today()`
 * never appears, because a suite that reads the clock proves only what day it ran on. "Today" is
 * always a parameter, exactly as production calls it.
 */
class ReminderStatusTest {

    // ---- fixtures -------------------------------------------------------------------------

    private fun rule(
        id: Long = 1L,
        vehicleId: Long = 1L,
        serviceType: String = "Oil change",
        intervalKm: Long? = null,
        intervalMonths: Long? = null,
        leadKm: Long? = null,
        leadDays: Long? = null,
        enabled: Boolean = true,
        createdAt: LocalDate = LocalDate(2026, 1, 1),
    ): ReminderRule = ReminderRule(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        leadKm = leadKm,
        leadDays = leadDays,
        enabled = enabled,
        createdAtEpochMs = ServiceDates.toStorage(createdAt),
        // Plan 01's two markers: these suites test pure evaluation, never bookkeeping.
        notifiedCycleKey = null,
        armedCycleKey = null,
    )

    private fun baseline(
        vehicleId: Long = 1L,
        typeLabel: String = "Oil change",
        date: LocalDate = LocalDate(2026, 1, 1),
        km: Long = 10_000L,
    ): TypeBaseline = TypeBaseline(
        vehicleId = vehicleId,
        typeLabel = typeLabel,
        baselineDateEpochMs = ServiceDates.toStorage(date),
        baselineKm = km,
    )

    private fun stats(
        vehicleId: Long = 1L,
        lastMileageKm: Long? = 10_000L,
        lastService: LocalDate? = null,
    ): VehicleStats = VehicleStats(
        vehicleId = vehicleId,
        lastServiceEpochMs = lastService?.let { ServiceDates.toStorage(it) },
        lastMileageKm = lastMileageKm,
    )

    private val today = LocalDate(2026, 6, 1)

    // ---- the distance axis ----------------------------------------------------------------

    @Test
    fun atTheBaselineTheWholeIntervalIsLeft() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalKm = 5_000),
            typeBaseline = null,
            currentMileageKm = 10_000,
            today = today,
        )

        assertEquals(DueState.Upcoming(kmLeft = 5_000, dueDate = null), evaluation.state)
        assertEquals(ReminderAxis.DISTANCE, evaluation.axis)
        assertEquals(0.0, evaluation.urgency, 0.0)
    }

    @Test
    fun halfwayThroughADistanceIntervalIsUpcomingWithHalfTheIntervalLeft() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalKm = 5_000),
            typeBaseline = baseline(km = 10_000),
            currentMileageKm = 12_500,
            today = today,
        )

        assertEquals(DueState.Upcoming(kmLeft = 2_500, dueDate = null), evaluation.state)
        assertEquals(0.5, evaluation.urgency, 0.0)
    }

    @Test
    fun aTypeBaselinePinsTheOdometerAndTheDateToTheSameEntry() {
        val pinned = baseline(date = LocalDate(2026, 1, 1), km = 10_000)
        val dual = rule(intervalKm = 5_000, intervalMonths = 6)

        // The latest entry *is* the baseline, so the whole km interval is still ahead...
        val onTheEntryDay = ReminderStatus.evaluate(dual, pinned, currentMileageKm = 10_000, today = LocalDate(2026, 1, 1))
        assertEquals(DueState.Upcoming(kmLeft = 5_000, dueDate = null), onTheEntryDay.state)
        assertEquals(ReminderAxis.DISTANCE, onTheEntryDay.axis)

        // ...and the time axis counts from that entry's date, not from the rule's creation date.
        val later = ReminderStatus.evaluate(dual, pinned, currentMileageKm = 10_000, today = LocalDate(2026, 2, 1))
        assertEquals(DueState.Upcoming(kmLeft = null, dueDate = LocalDate(2026, 7, 1)), later.state)
        assertEquals(ReminderAxis.TIME, later.axis)
    }

    // ---- the time axis --------------------------------------------------------------------

    @Test
    fun timeOnlyRulesCountCalendarMonthsFromTheCreationDate() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalMonths = 1, createdAt = LocalDate(2026, 1, 31)),
            typeBaseline = null,
            currentMileageKm = null,
            today = LocalDate(2026, 2, 1),
        )

        // 31 Jan + 1 month clamps to 28 Feb (T-03-06): 28 days in the interval, 27 left.
        assertEquals(DueState.Upcoming(kmLeft = null, dueDate = LocalDate(2026, 2, 28)), evaluation.state)
        assertEquals(ReminderAxis.TIME, evaluation.axis)
        assertEquals(1.0 / 28.0, evaluation.urgency, 1e-12)
    }

    @Test
    fun theMonthClampMakesTheThirtyFirstDueOnTheTwentyEighth() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalMonths = 1, createdAt = LocalDate(2026, 1, 31)),
            typeBaseline = null,
            currentMileageKm = null,
            today = LocalDate(2026, 2, 28),
        )

        assertEquals(DueState.DueNow, evaluation.state)
        assertEquals(1.0, evaluation.urgency, 0.0)
    }

    @Test
    fun sixMonthsAreCalendarMonthsNotOneHundredAndEightyDays() {
        // 1 Jan + 6 months is 1 Jul — 181 days in 2026, not 180.
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalMonths = 6, createdAt = LocalDate(2026, 1, 1)),
            typeBaseline = null,
            currentMileageKm = null,
            today = LocalDate(2026, 6, 30),
        )

        assertEquals(DueState.Upcoming(kmLeft = null, dueDate = LocalDate(2026, 7, 1)), evaluation.state)
        assertEquals(180.0 / 181.0, evaluation.urgency, 1e-12)
    }

    // ---- axis choice ----------------------------------------------------------------------

    @Test
    fun theAxisWithTheGreaterProgressDecidesTheState() {
        val pinned = baseline(date = LocalDate(2026, 1, 1), km = 10_000)
        val dual = rule(intervalKm = 5_000, intervalMonths = 6)

        val distanceAhead = ReminderStatus.evaluate(dual, pinned, currentMileageKm = 14_000, today = LocalDate(2026, 2, 1))
        assertEquals(ReminderAxis.DISTANCE, distanceAhead.axis)
        assertEquals(DueState.Upcoming(kmLeft = 1_000, dueDate = null), distanceAhead.state)

        val timeAhead = ReminderStatus.evaluate(dual, pinned, currentMileageKm = 10_500, today = LocalDate(2026, 6, 1))
        assertEquals(ReminderAxis.TIME, timeAhead.axis)
        assertEquals(DueState.Upcoming(kmLeft = null, dueDate = LocalDate(2026, 7, 1)), timeAhead.state)
    }

    @Test
    fun anExactTieBetweenTheAxesNamesTheDistanceAxis() {
        // 2 500 km of 5 000 is 0.5; 15 of the 30 days from 1 Apr to 1 May is also exactly 0.5.
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalKm = 5_000, intervalMonths = 1),
            typeBaseline = baseline(date = LocalDate(2026, 4, 1), km = 10_000),
            currentMileageKm = 12_500,
            today = LocalDate(2026, 4, 16),
        )

        assertEquals(ReminderAxis.DISTANCE, evaluation.axis)
        assertEquals(DueState.Upcoming(kmLeft = 2_500, dueDate = null), evaluation.state)
        assertEquals(0.5, evaluation.urgency, 0.0)
    }

    // ---- the exact-zero boundary (C-28) ---------------------------------------------------

    @Test
    fun theExactZeroBoundaryIsDueNowOnTheDistanceAxis() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalKm = 5_000),
            typeBaseline = baseline(km = 10_000),
            currentMileageKm = 15_000,
            today = today,
        )

        assertIs<DueState.DueNow>(evaluation.state)
        assertEquals(ReminderAxis.DISTANCE, evaluation.axis)
        assertEquals(1.0, evaluation.urgency, 0.0)
    }

    @Test
    fun theExactZeroBoundaryIsDueNowOnTheTimeAxis() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalMonths = 6, createdAt = LocalDate(2026, 1, 1)),
            typeBaseline = null,
            currentMileageKm = null,
            today = LocalDate(2026, 7, 1),
        )

        assertIs<DueState.DueNow>(evaluation.state)
        assertEquals(ReminderAxis.TIME, evaluation.axis)
        assertEquals(1.0, evaluation.urgency, 0.0)
    }

    // ---- lead windows (D-50) --------------------------------------------------------------

    @Test
    fun leadKmProducesDueSoonInsideTheWindowAndUpcomingOutsideIt() {
        val lead = rule(intervalKm = 5_000, leadKm = 500)
        val pinned = baseline(km = 10_000)

        val inside = ReminderStatus.evaluate(lead, pinned, currentMileageKm = 14_500, today = today)
        assertEquals(DueState.DueSoon(kmLeft = 500, daysLeft = null), inside.state)

        val outside = ReminderStatus.evaluate(lead, pinned, currentMileageKm = 14_499, today = today)
        assertEquals(DueState.Upcoming(kmLeft = 501, dueDate = null), outside.state)
    }

    @Test
    fun leadDaysProducesDueSoonInsideTheWindowAndUpcomingOutsideIt() {
        val lead = rule(intervalMonths = 6, leadDays = 20, createdAt = LocalDate(2026, 1, 1))

        val inside = ReminderStatus.evaluate(lead, null, null, LocalDate(2026, 6, 11))
        assertEquals(DueState.DueSoon(kmLeft = null, daysLeft = 20), inside.state)

        val outside = ReminderStatus.evaluate(lead, null, null, LocalDate(2026, 6, 10))
        assertEquals(DueState.Upcoming(kmLeft = null, dueDate = LocalDate(2026, 7, 1)), outside.state)
    }

    @Test
    fun aRuleWithNoLeadTravelsFromUpcomingStraightToOverdue() {
        val noLead = rule(intervalKm = 5_000)
        val pinned = baseline(km = 10_000)

        val lastUpcoming = ReminderStatus.evaluate(noLead, pinned, currentMileageKm = 14_999, today = today)
        assertEquals(DueState.Upcoming(kmLeft = 1, dueDate = null), lastUpcoming.state)

        val boundary = ReminderStatus.evaluate(noLead, pinned, currentMileageKm = 15_000, today = today)
        assertEquals(DueState.DueNow, boundary.state)

        val firstOverdue = ReminderStatus.evaluate(noLead, pinned, currentMileageKm = 15_001, today = today)
        assertEquals(DueState.Overdue(kmOver = 1, daysOver = null), firstOverdue.state)
    }

    // ---- D-49: never invent an odometer ---------------------------------------------------

    @Test
    fun aDistanceOnlyRuleWithNoMileageDataIsNoMileageData() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalKm = 5_000),
            typeBaseline = null,
            currentMileageKm = null,
            today = today,
        )

        assertEquals(DueState.NoMileageData, evaluation.state)
        assertNull(evaluation.axis)
        assertEquals(-1.0, evaluation.urgency, 0.0)
    }

    @Test
    fun aDualAxisRuleWithoutMileageDataStillReportsItsTimeAxis() {
        val evaluation = ReminderStatus.evaluate(
            rule = rule(intervalKm = 5_000, intervalMonths = 6, createdAt = LocalDate(2026, 1, 1)),
            typeBaseline = null,
            currentMileageKm = null,
            today = LocalDate(2026, 2, 1),
        )

        assertEquals(ReminderAxis.TIME, evaluation.axis)
        assertEquals(DueState.Upcoming(kmLeft = null, dueDate = LocalDate(2026, 7, 1)), evaluation.state)
    }

    // ---- urgency --------------------------------------------------------------------------

    @Test
    fun urgencyIsExactlyOneAtDueAndGreaterThanOneWhenOverdue() {
        val pinned = baseline(km = 10_000)
        val overdue = ReminderStatus.evaluate(rule(intervalKm = 5_000), pinned, 16_000, today)

        assertEquals(DueState.Overdue(kmOver = 1_000, daysOver = null), overdue.state)
        assertEquals(1.2, overdue.urgency, 1e-12)
        assertTrue(overdue.urgency > 1.0)
    }

    // ---- the editor gate's preconditions (C-34/C-35, T-03-05) -----------------------------

    @Test
    fun aDisabledRuleIsNeverEvaluated() {
        assertFailsWith<IllegalArgumentException> {
            ReminderStatus.evaluate(rule(intervalKm = 5_000, enabled = false), null, 10_000, today)
        }
    }

    @Test
    fun aRuleWithNeitherIntervalIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            ReminderStatus.evaluate(rule(), null, 10_000, today)
        }
    }

    @Test
    fun nonPositiveOrOversizedIntervalsAreRejected() {
        listOf(
            rule(intervalKm = 0),
            rule(intervalKm = -1),
            rule(intervalMonths = 0),
            rule(intervalMonths = -6),
            rule(intervalMonths = 1_201),
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>("expected ${invalid.intervalKm}/${invalid.intervalMonths} to be rejected") {
                ReminderStatus.evaluate(invalid, null, 10_000, today)
            }
        }
    }

    @Test
    fun nonPositiveLeadsAreRejected() {
        listOf(
            rule(intervalKm = 5_000, leadKm = 0),
            rule(intervalKm = 5_000, leadKm = -500),
            rule(intervalKm = 5_000, leadDays = 0),
            rule(intervalKm = 5_000, leadDays = -20),
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException>("expected lead ${invalid.leadKm}/${invalid.leadDays} to be rejected") {
                ReminderStatus.evaluate(invalid, null, 10_000, today)
            }
        }
    }

    // ---- the cycle key (D-51/D-48/D-47) ---------------------------------------------------

    @Test
    fun theCycleKeyIsStableForTheSameInputs() {
        val pinned = baseline(date = LocalDate(2026, 1, 1), km = 10_000)
        val dual = rule(intervalKm = 5_000, intervalMonths = 6, leadKm = 500, leadDays = 20)

        val first = ReminderStatus.evaluate(dual, pinned, currentMileageKm = 12_000, today = today)
        val second = ReminderStatus.evaluate(dual, pinned, currentMileageKm = 12_000, today = LocalDate(2027, 1, 1))

        assertEquals(first.cycleKey, second.cycleKey)
    }

    @Test
    fun aServiceOfTheRulesTypeMovesTheBaselineAndReArmsTheCycle() {
        val oilChange = rule(intervalKm = 5_000)
        val before = ReminderStatus.evaluate(oilChange, baseline(km = 10_000), 12_000, today)
        val after = ReminderStatus.evaluate(oilChange, baseline(km = 12_000), 12_000, today)
        val movedDate = ReminderStatus.evaluate(
            oilChange,
            baseline(date = LocalDate(2026, 3, 1), km = 10_000),
            12_000,
            today,
        )

        assertTrue(before.cycleKey != after.cycleKey, "a moved km baseline must produce a new cycle key")
        assertTrue(before.cycleKey != movedDate.cycleKey, "a moved date baseline must produce a new cycle key")
    }

    @Test
    fun editingAnIntervalOrALeadReArmsTheCycle() {
        val pinned = baseline(km = 10_000)
        val base = ReminderStatus.cycleKey(rule(intervalKm = 5_000, intervalMonths = 6), 10_000, pinned.baselineDateEpochMs)

        val keys = listOf(
            ReminderStatus.cycleKey(rule(intervalKm = 3_000, intervalMonths = 6), 10_000, pinned.baselineDateEpochMs),
            ReminderStatus.cycleKey(rule(intervalKm = 5_000, intervalMonths = 12), 10_000, pinned.baselineDateEpochMs),
            ReminderStatus.cycleKey(rule(intervalKm = 5_000, intervalMonths = 6, leadKm = 500), 10_000, pinned.baselineDateEpochMs),
            ReminderStatus.cycleKey(rule(intervalKm = 5_000, intervalMonths = 6, leadDays = 20), 10_000, pinned.baselineDateEpochMs),
        )

        keys.forEach { edited -> assertTrue(base != edited, "an edited interval/lead must re-arm: $edited") }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun aKmOnlyRuleReArmsWhenTheVehiclesLatestMileageMoves() {
        // D-49's fallback: no entry of the rule's type, so the resolved km baseline is the
        // vehicle's latest mileage. Hashing the resolved baseline (not `typeBaseline`) is what
        // keeps such a rule from staying silent forever after its first notification.
        val oilChange = rule(intervalKm = 5_000)
        val first = ReminderStatus.evaluate(oilChange, typeBaseline = null, currentMileageKm = 10_000, today = today)
        val second = ReminderStatus.evaluate(oilChange, typeBaseline = null, currentMileageKm = 10_500, today = today)

        assertTrue(first.cycleKey != second.cycleKey)
        assertEquals(first.cycleKey, ReminderStatus.evaluate(oilChange, null, 10_000, today).cycleKey)
    }

    @Test
    fun theCycleKeyNamesEveryInputItCovers() {
        val key = ReminderStatus.cycleKey(
            rule = rule(intervalKm = 5_000, intervalMonths = 6, leadKm = 500, leadDays = 20),
            kmBaseline = 10_000,
            dateBaselineEpochMs = ServiceDates.toStorage(LocalDate(2026, 1, 1)),
        )

        assertEquals("b10000:1767225600000|i5000:6|l500:20", key)
    }

    // ---- evaluateAll: the one join of rules to baselines and odometer ---------------------

    @Test
    fun aRuleWhoseTypeHasAnEntryTakesThatEntrysKmAndDate() {
        val rows = ReminderStatus.evaluateAll(
            rules = listOf(rule(intervalKm = 5_000, intervalMonths = 6)),
            baselines = listOf(baseline(date = LocalDate(2026, 3, 1), km = 20_000)),
            stats = listOf(stats(lastMileageKm = 20_000)),
            today = LocalDate(2026, 3, 1),
        )

        val evaluation = rows.single().evaluation!!
        assertEquals(DueState.Upcoming(kmLeft = 5_000, dueDate = null), evaluation.state)
        assertEquals(ReminderAxis.DISTANCE, evaluation.axis)
    }

    @Test
    fun aRuleWithNoEntryOfItsTypeFallsBackToTheLatestMileageAndItsCreationDate() {
        val rows = ReminderStatus.evaluateAll(
            rules = listOf(rule(intervalKm = 5_000, intervalMonths = 6, createdAt = LocalDate(2026, 1, 1))),
            baselines = listOf(baseline(typeLabel = "Tire rotation", date = LocalDate(2026, 5, 1), km = 30_000)),
            stats = listOf(stats(lastMileageKm = 12_000)),
            today = LocalDate(2026, 1, 1),
        )

        val evaluation = rows.single().evaluation!!
        // km: 12 000 baseline + 5 000 = 17 000 due; time: 1 Jan + 6 months = 1 Jul.
        assertEquals(DueState.Upcoming(kmLeft = 5_000, dueDate = null), evaluation.state)
    }

    @Test
    fun aTypeLabelDifferingOnlyInCaseStillMatchesItsBaseline() {
        val rows = ReminderStatus.evaluateAll(
            rules = listOf(rule(serviceType = "oil change", intervalKm = 5_000)),
            baselines = listOf(baseline(typeLabel = "Oil Change", km = 40_000)),
            stats = listOf(stats(lastMileageKm = 1_000)),
            today = today,
        )

        // The type baseline (40 000) wins over the vehicle's latest mileage (1 000): had the
        // label not matched, the D-49 fallback would have reported 5 000 km left instead of 44 000.
        assertEquals(DueState.Upcoming(kmLeft = 44_000, dueDate = null), rows.single().evaluation!!.state)
    }

    @Test
    fun aDisabledRuleComesBackWithoutAnEvaluationAndWithoutAFailure() {
        val rows = ReminderStatus.evaluateAll(
            rules = listOf(rule(intervalKm = 5_000, enabled = false), rule(id = 2, intervalKm = 5_000)),
            baselines = emptyList(),
            stats = listOf(stats(lastMileageKm = 10_000)),
            today = today,
        )

        assertEquals(2, rows.size)
        assertNull(rows[0].evaluation)
        assertEquals(false, rows[0].rule.enabled)
        assertTrue(rows[1].evaluation != null)
    }

    @Test
    fun aBaselineForAnotherVehicleIsNeverApplied() {
        val rows = ReminderStatus.evaluateAll(
            rules = listOf(rule(vehicleId = 1L, intervalKm = 5_000)),
            baselines = listOf(baseline(vehicleId = 99L, km = 40_000)),
            stats = listOf(stats(vehicleId = 99L, lastMileageKm = 40_000)),
            today = today,
        )

        // Vehicle 1 has no entry at all, so there is no odometer to measure from (D-49).
        assertEquals(DueState.NoMileageData, rows.single().evaluation!!.state)
    }

    @Test
    fun anEmptyRuleListProducesAnEmptyRowList() {
        val rows = ReminderStatus.evaluateAll(emptyList(), emptyList(), emptyList(), today)

        assertTrue(rows.isEmpty())
    }
}
