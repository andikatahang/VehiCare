package com.vehicare.util

/**
 * The app's one numeric formatting seam for money and mileage.
 *
 * Costs are whole Rupiah with dot thousands separators and no decimals (D-29), and mileages use
 * the same dot grouping (UI-SPEC §Typography). Both formats call the single private [groupDigits]
 * helper so they cannot drift apart — never format a number anywhere else in the app.
 *
 * No locale API is used on purpose: one pure-Kotlin implementation renders identically on Android
 * and iOS, where a platform formatter could pick a comma separator or insert a non-breaking space
 * on one platform only (T-02-02-03).
 */
object RupiahFormat {

    /** Longest accepted cost, in digits: 12 digits is ≤ Rp 999.999.999.999, so no Long overflow. */
    private const val MAX_DIGITS = 12

    /** `450_000` → `"Rp 450.000"`. Whole Rupiah only, dot thousands separators (D-29). */
    fun formatRupiah(amount: Long): String = "Rp " + groupDigits(amount)

    /** `123_456` → `"123.456 km"`. Same dot grouping as [formatRupiah]. */
    fun formatMileage(km: Long): String = groupDigits(km) + " km"

    /**
     * Keeps only the digit characters of [raw], at most the first [MAX_DIGITS], and parses the
     * result; a blank/no-digit input becomes `null` (the cost is optional).
     *
     * This mirrors `VehicleFormValidation.sanitizeYear`'s `it.isDigit()` filter: the text field
     * never accepts separators or a sign, and the cap keeps every parsed value inside `Long` range.
     */
    fun parseRupiahDigits(raw: String): Long? =
        raw.filter { it.isDigit() }.take(MAX_DIGITS).takeIf { it.isNotEmpty() }?.toLongOrNull()

    /**
     * Groups the decimal digits of [value] in threes with a dot, e.g. `999999999999` →
     * `"999.999.999.999"`. A leading minus is kept in front of the grouped digits.
     */
    private fun groupDigits(value: Long): String {
        val digits = value.toString().removePrefix("-")
        val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
        return if (value < 0) "-$grouped" else grouped
    }
}
