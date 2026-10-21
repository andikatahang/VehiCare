package com.vehicare.ui.service

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for [serviceTypeLine], the single source of the service-type line on the entry detail
 * focal block and the history row.
 *
 * The locked rules: one type renders verbatim (so a single-type entry is byte-identical to what
 * shipped before multi-select), two or more are joined by the middle dot ` · ` (U+00B7, single
 * spaces), and the stored list order is the display order.
 */
class ServiceTypeLineTest {

    @Test
    fun oneTypeRendersVerbatim() {
        assertEquals("Oil change", serviceTypeLine(listOf("Oil change")))
    }

    @Test
    fun twoTypesAreJoinedByTheLockedSeparator() {
        assertEquals(
            "Oil change · Tire rotation",
            serviceTypeLine(listOf("Oil change", "Tire rotation")),
        )
    }

    @Test
    fun threeTypesPreserveTheStoredOrder() {
        assertEquals(
            "Brake service · Oil change · Inspection",
            serviceTypeLine(listOf("Brake service", "Oil change", "Inspection")),
        )
    }

    @Test
    fun splittingOnTheSeparatorYieldsExactlyOnePartPerType() {
        val types = listOf("Battery", "Filters", "Wheel alignment")
        val parts = serviceTypeLine(types).split(" · ")

        assertEquals(types.size, parts.size)
        assertEquals(types, parts)
    }

    @Test
    fun separatorIsTheMiddleDotWithSingleSpaces() {
        val line = serviceTypeLine(listOf("Battery", "Filters"))

        // U+00B7, not a hyphen, bullet, pipe or a comma-and-space.
        assertTrue(line.contains('\u00B7'), "the separator must be the middle dot U+00B7")
        assertEquals(2, line.split('\u00B7').size, "exactly one middle dot for two types")
        assertEquals("Battery \u00B7 Filters", line)
    }

    @Test
    fun anEmptyListRendersAnEmptyString() {
        // Defensive: the domain guarantees at least one type on a saved entry, but the helper is a
        // pure formatting function and must never throw.
        assertEquals("", serviceTypeLine(emptyList()))
    }
}
