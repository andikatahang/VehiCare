package com.vehicare.notifications

import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.util.ServiceDates
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * The pure current-occurrence/notification-date arithmetic and baseline resolution (D-46/D-47/D-50),
 * pinned on both targets with no clock read: every fixture is a fixed [LocalDate] built through
 * [ServiceDates.toStorage].
 */
class ReminderScheduleTest {

    private fun rule(
        id: Long = 1L,
        vehicleId: Long = 1L,
        serviceType: String = "Brake service",
        intervalMonths: Long? = 12L,
        leadDays: Long? = null,
        enabled: Boolean = true,
        createdAt: LocalDate = LocalDate(2026, 1, 1),
    ): ReminderRule = ReminderRule(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = null,
        intervalMonths = intervalMonths,
        leadKm = null,
        leadDays = leadDays,
        enabled = enabled,
        createdAtEpochMs = ServiceDates.toStorage(createdAt),
        notifiedCycleKey = null,
        armedCycleKey = null,
    )

    private fun baseline(
        vehicleId: Long = 1L,
        typeLabel: String = "Brake service",
        date: LocalDate,
    ): TypeBaseline = TypeBaseline(
        vehicleId = vehicleId,
        typeLabel = typeLabel,
        baselineDateEpochMs = ServiceDates.toStorage(date),
        baselineKm = 10_000L,
    )

    // ---- currentOccurrence ------------------------------------------------------------------

    @Test
    fun currentOccurrenceIsBaselinePlusIntervalEvenWhenPast() {
        val createdAt = LocalDate(2024, 1, 1)
        val occurrence = ReminderSchedule.currentOccurrence(
            rule(intervalMonths = 12L, createdAt = createdAt),
            baselines = emptyList(),
        )
        // No rolling: this is deep in the past, and it must stay there.
        assertEquals(LocalDate(2025, 1, 1), occurrence)
    }

    @Test
    fun currentOccurrenceIsNullWithNoTimeInterval() {
        assertNull(ReminderSchedule.currentOccurrence(rule(intervalMonths = null), baselines = emptyList()))
    }

    @Test
    fun currentOccurrenceIsNullForADisabledRule() {
        assertNull(
            ReminderSchedule.currentOccurrence(rule(enabled = false), baselines = emptyList()),
        )
    }

    @Test
    fun currentOccurrenceFailsOnAZeroInterval() {
        assertFailsWith<IllegalArgumentException> {
            ReminderSchedule.currentOccurrence(rule(intervalMonths = 0L), baselines = emptyList())
        }
    }

    // ---- notificationDate --------------------------------------------------------------------

    @Test
    fun notificationDateEqualsOccurrenceWithNoLead() {
        val createdAt = LocalDate(2026, 1, 1)
        val due = ReminderSchedule.currentOccurrence(rule(createdAt = createdAt), emptyList())
        val notifyAt = ReminderSchedule.notificationDate(rule(createdAt = createdAt, leadDays = null), emptyList())
        assertEquals(due, notifyAt)
    }

    @Test
    fun notificationDateSubtractsSevenCalendarDays() {
        val createdAt = LocalDate(2025, 1, 1)
        // due = 2026-01-01; notify = 2025-12-25
        val notifyAt = ReminderSchedule.notificationDate(
            rule(createdAt = createdAt, leadDays = 7L),
            emptyList(),
        )
        assertEquals(LocalDate(2025, 12, 25), notifyAt)
    }

    @Test
    fun notificationDateCrossesAMonthBoundary() {
        // due = 2027-03-31 (12 months from 2026-03-31); notify = due - 7 = 2027-03-24.
        val createdAt = LocalDate(2026, 3, 31)
        val notifyAt = ReminderSchedule.notificationDate(
            rule(createdAt = createdAt, leadDays = 7L),
            emptyList(),
        )
        assertEquals(LocalDate(2027, 3, 24), notifyAt)
    }

    @Test
    fun notificationDateIsNullWithNoTimeInterval() {
        assertNull(
            ReminderSchedule.notificationDate(rule(intervalMonths = null), baselines = emptyList()),
        )
    }

    @Test
    fun notificationDateIsNullForADisabledRule() {
        assertNull(
            ReminderSchedule.notificationDate(rule(enabled = false), baselines = emptyList()),
        )
    }

    @Test
    fun clampsThirtyFirstJanuaryPlusOneMonthToTwentyEighthFebruary() {
        val occurrence = ReminderSchedule.currentOccurrence(
            rule(intervalMonths = 1L, createdAt = LocalDate(2026, 1, 31)),
            baselines = emptyList(),
        )
        assertEquals(LocalDate(2026, 2, 28), occurrence)
    }

    @Test
    fun clampsLeapDayPlusTwelveMonthsToTwentyEighthFebruary() {
        // 2028 is a leap year; 2029 is not.
        val occurrence = ReminderSchedule.currentOccurrence(
            rule(intervalMonths = 12L, createdAt = LocalDate(2028, 2, 29)),
            baselines = emptyList(),
        )
        assertEquals(LocalDate(2029, 2, 28), occurrence)
    }

    // ---- timeBaselineOf ------------------------------------------------------------------------

    @Test
    fun timeBaselineOfPicksACaseDifferingLabelForTheSameVehicle() {
        val baselines = listOf(baseline(vehicleId = 1L, typeLabel = "BRAKE SERVICE", date = LocalDate(2025, 6, 1)))
        val resolved = ReminderSchedule.timeBaselineOf(rule(vehicleId = 1L, serviceType = "Brake service"), baselines)
        assertEquals(LocalDate(2025, 6, 1), resolved)
    }

    @Test
    fun timeBaselineOfIgnoresAnotherVehiclesBaseline() {
        val baselines = listOf(baseline(vehicleId = 2L, date = LocalDate(2025, 6, 1)))
        val createdAt = LocalDate(2026, 1, 1)
        val resolved = ReminderSchedule.timeBaselineOf(rule(vehicleId = 1L, createdAt = createdAt), baselines)
        assertEquals(createdAt, resolved)
    }

    @Test
    fun timeBaselineOfFallsBackToCreatedAt() {
        val createdAt = LocalDate(2026, 1, 1)
        val resolved = ReminderSchedule.timeBaselineOf(rule(createdAt = createdAt), baselines = emptyList())
        assertEquals(createdAt, resolved)
    }

    // ---- the global interval setting (261024-u9g, D-1) -----------------------------------------

    @Test
    fun aGlobalIntervalGivesAMonthsNullRuleAnOccurrence() {
        val occurrence = ReminderSchedule.currentOccurrence(
            rule(intervalMonths = null, createdAt = LocalDate(2026, 3, 15)),
            baselines = emptyList(),
            globalIntervalMonths = 6L,
        )

        assertEquals(LocalDate(2026, 9, 15), occurrence)
    }

    @Test
    fun theGlobalIntervalOverridesTheRuleOwnMonths() {
        // The rule says 12 months; the global 3-month setting wins (override, not min/max).
        val occurrence = ReminderSchedule.currentOccurrence(
            rule(intervalMonths = 12L, createdAt = LocalDate(2026, 3, 15)),
            baselines = emptyList(),
            globalIntervalMonths = 3L,
        )

        assertEquals(LocalDate(2026, 6, 15), occurrence)
    }

    @Test
    fun aNullGlobalIntervalKeepsThePerRuleBehavior() {
        val createdAt = LocalDate(2026, 3, 15)
        val occurrence = ReminderSchedule.currentOccurrence(
            rule(intervalMonths = 12L, createdAt = createdAt),
            baselines = emptyList(),
            globalIntervalMonths = null,
        )

        assertEquals(LocalDate(2027, 3, 15), occurrence)
    }

    @Test
    fun notificationDateHonorsTheGlobalInterval() {
        val notifyAt = ReminderSchedule.notificationDate(
            rule(intervalMonths = null, leadDays = 7L, createdAt = LocalDate(2026, 3, 15)),
            baselines = emptyList(),
            globalIntervalMonths = 6L,
        )

        // due = March + 6 = September 15; the lead counts back from the global occurrence.
        assertEquals(LocalDate(2026, 9, 8), notifyAt)
    }
}
