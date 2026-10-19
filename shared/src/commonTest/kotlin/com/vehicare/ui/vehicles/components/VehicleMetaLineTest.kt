package com.vehicare.ui.vehicles.components

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class VehicleMetaLineTest {

    @Test
    fun everyCombinationJoinsExactlyThePresentSegments() {
        val cases: List<Pair<Triple<String?, String?, String?>, String>> = listOf(
            Triple("Toyota", "Corolla", "2020") to "Toyota · Corolla · 2020",
            Triple("Toyota", "Corolla", null) to "Toyota · Corolla",
            Triple("Toyota", null, "2020") to "Toyota · 2020",
            Triple(null, "Corolla", "2020") to "Corolla · 2020",
            Triple("Toyota", null, null) to "Toyota",
            Triple(null, "Corolla", null) to "Corolla",
            Triple(null, null, "2020") to "2020",
            Triple(null, null, null) to "",
        )

        for ((segments, expected) in cases) {
            val (make, model, year) = segments
            assertEquals(
                expected = expected,
                actual = vehicleMetaLine(make, model, year),
                message = "make=$make model=$model year=$year",
            )
        }
    }

    @Test
    fun treatsBlankAfterTrimExactlyLikeNull() {
        assertEquals("Corolla", vehicleMetaLine("  ", " Corolla ", ""))
        assertEquals("", vehicleMetaLine(" ", "  ", "\t"))
    }

    @Test
    fun neverLeavesALeadingTrailingOrDoubledSeparator() {
        val segments = listOf("Toyota", null, " ", "", "2020", " Corolla ")

        for (make in segments) {
            for (model in segments) {
                for (year in segments) {
                    val line = vehicleMetaLine(make, model, year)
                    assertFalse(line.startsWith("·"), "leading separator in '$line'")
                    assertFalse(line.endsWith("·"), "trailing separator in '$line'")
                    assertFalse(line.contains("· ·"), "doubled separator in '$line'")
                }
            }
        }
    }
}
