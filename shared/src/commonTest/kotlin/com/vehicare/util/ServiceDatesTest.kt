package com.vehicare.util

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The date seam's two load-bearing facts (RESEARCH Pitfall 1): a civil date round-trips through
 * storage unchanged, and the one display format is exactly `19 Oct 2026`.
 *
 * Note on zones: `today()` intentionally reads the **system** zone (the date on the user's phone)
 * while `toStorage`/`fromStorage` pin **UTC** start-of-day. The asymmetry is the contract, not a
 * bug — a test that mocked the clock would only prove the mock, so these cases use fixed dates.
 */
class ServiceDatesTest {

    @Test
    fun aCivilDateRoundTripsThroughStorageUnchanged() {
        val dates = listOf(
            LocalDate(2026, 10, 19),
            LocalDate(2024, 2, 29), // leap day — the case a hand-rolled day-number table gets wrong
            LocalDate(2026, 1, 1),
            LocalDate(1999, 12, 31),
            LocalDate(2031, 7, 4),
        )

        dates.forEach { date ->
            assertEquals(
                expected = date,
                actual = ServiceDates.fromStorage(ServiceDates.toStorage(date)),
                message = "round trip failed for $date",
            )
        }
    }

    @Test
    fun storageIsUtcStartOfDay() {
        // The Unix epoch is 1970-01-01T00:00Z by definition, so this pins the UTC policy.
        assertEquals(LocalDate(1970, 1, 1), ServiceDates.fromStorage(0L))
        assertEquals(0L, ServiceDates.toStorage(LocalDate(1970, 1, 1)))

        // 2026-10-19T00:00:00Z — a hand-checked value, not a device-zone-dependent one.
        assertEquals(ServiceDates.toStorage(LocalDate(2026, 10, 19)), 1_792_368_000_000L)
    }

    @Test
    fun displayUsesTheLockedFormat() {
        // The app's one display format (UI-SPEC §Typography): day, abbreviated month, year.
        assertEquals("19 Oct 2026", ServiceDates.display(LocalDate(2026, 10, 19)))
        assertEquals("1 Jan 2026", ServiceDates.display(LocalDate(2026, 1, 1)))
        assertEquals("29 Feb 2024", ServiceDates.display(LocalDate(2024, 2, 29)))
        assertEquals("31 Dec 1999", ServiceDates.display(LocalDate(1999, 12, 31)))
    }

    @Test
    fun displayReadsStoredMillisBackAsTheSameCivilDate() {
        val date = LocalDate(2026, 10, 19)
        assertEquals("19 Oct 2026", ServiceDates.display(ServiceDates.toStorage(date)))
    }
}
