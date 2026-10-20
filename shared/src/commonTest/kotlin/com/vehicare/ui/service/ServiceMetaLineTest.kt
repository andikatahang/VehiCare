package com.vehicare.ui.service

import com.vehicare.util.ServiceDates
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.datetime.LocalDate

class ServiceMetaLineTest {

    @Test
    fun metaLineUsesLockedFormat() {
        // 19 Oct 2026 at UTC start-of-day
        val epochMs = ServiceDates.toStorage(LocalDate(2026, 10, 19))
        val result = serviceMetaLine(epochMs, 123_456)

        assertEquals(
            expected = "19 Oct 2026 · 123.456 km",
            actual = result,
        )
    }

    @Test
    fun zeroMileageRendersZeroKm() {
        val epochMs = ServiceDates.toStorage(LocalDate(2026, 10, 19))
        val result = serviceMetaLine(epochMs, 0)

        assertEquals(
            expected = "19 Oct 2026 · 0 km",
            actual = result,
        )
    }

    @Test
    fun separatorIsMiddleDotWithSingleSpaces() {
        val epochMs = ServiceDates.toStorage(LocalDate(2026, 10, 19))
        val result = serviceMetaLine(epochMs, 1)

        // The separator must be the literal middle dot with single spaces either side
        assertEquals(
            expected = "19 Oct 2026 · 1 km",
            actual = result,
        )
        // Additional assertion that it's exactly one middle dot with single spaces
        val parts = result.split(" · ")
        assertEquals(2, parts.size, "Separator ' · ' splits into exactly two parts")
    }
}