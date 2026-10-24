package com.vehicare.ui.reminders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ReminderFormValidation] — C-34's one rule/one message and C-35's submit gate.
 *
 * The rules are asserted without a UI harness on purpose: the same object is the only place the
 * editor decides "is this field wrong" and "may the user submit", so a regression here is visible
 * before any screen is composed.
 */
class ReminderFormValidationTest {

    // --- C-34: blank is never an error ---------------------------------------------------------

    @Test
    fun aCompletelyBlankFormHasNoFieldErrors() {
        val errors = ReminderFormValidation.errors(ReminderFormValues())

        assertEquals(emptySet(), errors, "blank means 'not set', not 'invalid'")
    }

    @Test
    fun whitespaceOnlyFieldsCountAsBlank() {
        val values = ReminderFormValues(
            intervalKm = "   ",
            leadKm = " ",
            leadDays = "\n",
        )

        assertEquals(emptySet(), ReminderFormValidation.errors(values))
    }

    // --- C-34: every non-blank field below 1 is an error ----------------------------------------

    @Test
    fun zeroIsAnErrorInEveryNumericField() {
        val errors = ReminderFormValidation.errors(
            ReminderFormValues(
                intervalKm = "0",
                leadKm = "0",
                leadDays = "0",
            ),
        )

        assertEquals(
            setOf(
                ReminderFormField.INTERVAL_KM,
                ReminderFormField.LEAD_KM,
                ReminderFormField.LEAD_DAYS,
            ),
            errors,
        )
    }

    @Test
    fun oneIsValidInEveryNumericField() {
        val values = ReminderFormValues(
            intervalKm = "1",
            leadKm = "1",
            leadDays = "1",
        )

        assertEquals(emptySet(), ReminderFormValidation.errors(values))
    }

    @Test
    fun onlyTheInvalidFieldIsReported() {
        val errors = ReminderFormValidation.errors(
            ReminderFormValues(intervalKm = "5000", leadDays = "0"),
        )

        assertEquals(setOf(ReminderFormField.LEAD_DAYS), errors)
    }

    @Test
    fun aNonNumericValueIsAnErrorRatherThanSilentlyDropped() {
        val errors = ReminderFormValidation.errors(
            ReminderFormValues(intervalKm = "12a", leadDays = "abc"),
        )

        assertEquals(
            setOf(ReminderFormField.INTERVAL_KM, ReminderFormField.LEAD_DAYS),
            errors,
        )
    }

    // --- Sanitizers -----------------------------------------------------------------------------

    @Test
    fun sanitizeKmKeepsDigitsOnlyAndCapsAtSix() {
        assertEquals("5000", ReminderFormValidation.sanitizeKm("5.000 km"))
        assertEquals("123456", ReminderFormValidation.sanitizeKm("1234567890"))
        assertEquals("", ReminderFormValidation.sanitizeKm("km"))
        assertEquals(ReminderFormValidation.MAX_KM_DIGITS, ReminderFormValidation.sanitizeKm("9".repeat(20)).length)
    }

    @Test
    fun sanitizeMonthsKeepsDigitsOnlyAndCapsAtThree() {
        // Since 261024-u9g (D-3) this sanitizer serves the "days before" field only.
        assertEquals("6", ReminderFormValidation.sanitizeMonths("6 days"))
        assertEquals("120", ReminderFormValidation.sanitizeMonths("1200"))
        assertEquals("", ReminderFormValidation.sanitizeMonths("days"))
        assertEquals(ReminderFormValidation.MAX_MONTH_DIGITS, ReminderFormValidation.sanitizeMonths("9".repeat(20)).length)
    }

    @Test
    fun theDayCapKeepsTheValueInsideAnySaneLeadWindow() {
        val capped = ReminderFormValidation.sanitizeMonths("999999")

        assertTrue(
            capped.toLong() in 1L..1200L,
            "the 3-digit cap keeps a days lead inside a sane range",
        )
    }

    // --- C-35: the submit gate ------------------------------------------------------------------

    @Test
    fun theGateIsClosedWithNoServiceType() {
        val values = ReminderFormValues(intervalKm = "5000")

        assertFalse(ReminderFormValidation.isSubmittable(values))
    }

    @Test
    fun theGateIsClosedWithTwoServiceTypes() {
        val values = ReminderFormValues(
            serviceTypes = listOf("Oil change", "Brake service"),
            intervalKm = "5000",
        )

        assertFalse(ReminderFormValidation.isSubmittable(values))
    }

    @Test
    fun theGateIsClosedWithNoIntervalAtAll() {
        val values = ReminderFormValues(serviceTypes = listOf("Oil change"))

        assertFalse(ReminderFormValidation.isSubmittable(values))
    }

    @Test
    fun theGateIsClosedWhenTheOnlyIntervalIsInvalid() {
        val values = ReminderFormValues(
            serviceTypes = listOf("Oil change"),
            intervalKm = "0",
        )

        assertFalse(
            ReminderFormValidation.isSubmittable(values),
            "a non-blank interval with an error must not open the gate",
        )
    }

    @Test
    fun theGateIsOpenWithOneTypeAndAValidKmInterval() {
        val kmOnly = ReminderFormValues(serviceTypes = listOf("Oil change"), intervalKm = "5000")

        assertTrue(
            ReminderFormValidation.isSubmittable(kmOnly),
            "the km axis is the editor's only interval since 261024-u9g (D-3)",
        )
    }

    @Test
    fun theGateIsClosedWithoutAnyKmInterval() {
        val blankKm = ReminderFormValues(serviceTypes = listOf("Oil change"), intervalKm = "")

        assertFalse(
            ReminderFormValidation.isSubmittable(blankKm),
            "a rule needs at least one interval; with months gone, km is mandatory (C-35/D-3)",
        )
    }

    @Test
    fun theGateStaysOpenWithKmAndBothLeadsSet() {
        val values = ReminderFormValues(
            serviceTypes = listOf("Brake service"),
            intervalKm = "5000",
            leadKm = "500",
            leadDays = "14",
        )

        assertTrue(ReminderFormValidation.isSubmittable(values))
    }

    @Test
    fun theGateIsClosedWhenAnOptionalLeadIsInvalid() {
        val values = ReminderFormValues(
            serviceTypes = listOf("Brake service"),
            intervalKm = "5000",
            leadDays = "0",
        )

        assertFalse(
            ReminderFormValidation.isSubmittable(values),
            "a typed advance notice of 0 is invalid; the empty field is the 'no lead' case",
        )
    }

    // --- toRule: the single blank-to-null boundary ----------------------------------------------

    @Test
    fun toRuleMapsEveryBlankFieldToNull() {
        val rule = ReminderFormValidation.toRule(
            id = 0L,
            vehicleId = 7L,
            createdAtEpochMs = 1_700_000_000_000L,
            values = ReminderFormValues(serviceTypes = listOf("Oil change"), intervalKm = "5000"),
        )

        assertNull(rule.intervalMonths)
        assertNull(rule.leadKm)
        assertNull(rule.leadDays)
    }

    @Test
    fun toRuleKeepsKmAndLeadsAndAlwaysWritesANullMonthsInterval() {
        val rule = ReminderFormValidation.toRule(
            id = 0L,
            vehicleId = 7L,
            createdAtEpochMs = 1_700_000_000_000L,
            values = ReminderFormValues(
                serviceTypes = listOf("Brake service"),
                intervalKm = "5000",
                leadKm = "500",
                leadDays = "14",
            ),
        )

        assertEquals(5_000L, rule.intervalKm)
        assertNull(rule.intervalMonths, "the editor no longer writes a per-rule months value (D-3)")
        assertEquals(500L, rule.leadKm)
        assertEquals(14L, rule.leadDays)
    }

    @Test
    fun toRulePreservesTheIdentityFields() {
        val rule = ReminderFormValidation.toRule(
            id = 42L,
            vehicleId = 7L,
            createdAtEpochMs = 1_700_000_000_000L,
            values = ReminderFormValues(serviceTypes = listOf("  Oil change  "), intervalKm = "5000"),
        )

        assertEquals(42L, rule.id)
        assertEquals(7L, rule.vehicleId)
        assertEquals(1_700_000_000_000L, rule.createdAtEpochMs)
        assertEquals("Oil change", rule.serviceType, "the selected label is trimmed, never rewritten")
    }

    @Test
    fun toRuleDefaultsEnabledToTrueAndNeverWritesEitherMarker() {
        val rule = ReminderFormValidation.toRule(
            id = 0L,
            vehicleId = 7L,
            createdAtEpochMs = 1_700_000_000_000L,
            values = ReminderFormValues(serviceTypes = listOf("Filters"), intervalKm = "5000"),
        )

        assertTrue(rule.enabled)
        assertNull(rule.notifiedCycleKey, "only markNotified may claim a cycle (D-51)")
        assertNull(rule.armedCycleKey, "only markArmed may record a scheduling request (D-53)")
    }

    @Test
    fun toRulePassesADisabledSwitchStraightThrough() {
        val rule = ReminderFormValidation.toRule(
            id = 3L,
            vehicleId = 7L,
            createdAtEpochMs = 1_700_000_000_000L,
            values = ReminderFormValues(
                serviceTypes = listOf("Filters"),
                intervalKm = "5000",
                enabled = false,
            ),
        )

        assertFalse(rule.enabled)
    }
}
