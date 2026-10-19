package com.vehicare.util

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Both dot-grouped numeric formats (D-29, UI-SPEC §Typography) plus the digits-only cost parser
 * that mirrors `sanitizeYear`'s filter with a 12-digit cap.
 */
class RupiahFormatTest {

    @Test
    fun rupiahUsesDotGroupingAndNoDecimals() {
        assertEquals("Rp 450.000", RupiahFormat.formatRupiah(450_000))
        assertEquals("Rp 0", RupiahFormat.formatRupiah(0))
        assertEquals("Rp 999", RupiahFormat.formatRupiah(999))
        assertEquals("Rp 1.000", RupiahFormat.formatRupiah(1_000))
        assertEquals("Rp 45.000", RupiahFormat.formatRupiah(45_000))
        assertEquals("Rp 999.999.999.999", RupiahFormat.formatRupiah(999_999_999_999))
    }

    @Test
    fun mileageUsesTheSameDotGroupingAsRupiah() {
        // Both formats route through the one grouping helper, so their separators cannot drift.
        assertEquals("123.456 km", RupiahFormat.formatMileage(123_456))
        assertEquals("0 km", RupiahFormat.formatMileage(0))
        assertEquals("999 km", RupiahFormat.formatMileage(999))
        assertEquals("1.000 km", RupiahFormat.formatMileage(1_000))
    }

    @Test
    fun parseKeepsOnlyDigits() {
        assertEquals(123L, RupiahFormat.parseRupiahDigits("12a3"))
        assertEquals(450_000L, RupiahFormat.parseRupiahDigits("Rp 450.000"))
        assertEquals(7L, RupiahFormat.parseRupiahDigits(" 0 0 7 "))
    }

    @Test
    fun parseTreatsBlankAndDigitlessInputAsNoCost() {
        assertNull(RupiahFormat.parseRupiahDigits(""))
        assertNull(RupiahFormat.parseRupiahDigits("   "))
        assertNull(RupiahFormat.parseRupiahDigits("Rp"))
        assertNull(RupiahFormat.parseRupiahDigits("abc"))
    }

    @Test
    fun parseCapsAtTwelveDigitsSoLongCannotOverflow() {
        // 13 digits: the 13th is dropped, the first 12 survive (≤ Rp 999.999.999.999).
        assertEquals(123_456_789_012L, RupiahFormat.parseRupiahDigits("1234567890123"))
        assertEquals(999_999_999_999L, RupiahFormat.parseRupiahDigits("999999999999999999"))
        assertEquals(123_456_789_012L, RupiahFormat.parseRupiahDigits("1 2 3 4 5 6 7 8 9 0 1 2 3"))
    }
}
