package com.vehicare.ui.service

import com.vehicare.domain.ServicePart
import com.vehicare.util.RupiahFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Unit tests for [ServiceEntryFormValidation] — pure validation rules without UI.
 *
 * Every rule is exercised on both Android and iOS via `commonTest`, ensuring identical behaviour
 * on both targets.
 */
class ServiceEntryFormValidationTest {

    @Test
    fun isSubmittableRequiresDateMileageAndType() {
        // All present
        val valid = ServiceEntryFormValues(
            dateEpochMs = 1_000_000_000_000L,
            mileage = "12345",
            serviceType = "Oil change",
        )
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid))

        // Missing date
        assertFalse(ServiceEntryFormValidation.isSubmittable(valid.copy(dateEpochMs = null)))

        // Missing mileage (blank)
        assertFalse(ServiceEntryFormValidation.isSubmittable(valid.copy(mileage = "")))

        // Mileage whitespace only
        assertFalse(ServiceEntryFormValidation.isSubmittable(valid.copy(mileage = "   ")))

        // Missing service type
        assertFalse(ServiceEntryFormValidation.isSubmittable(valid.copy(serviceType = null)))

        // Cost, notes, workshop, mechanic, parts, photos are optional — their presence doesn't affect the gate
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid.copy(cost = "50000")))
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid.copy(notes = "Some notes")))
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid.copy(workshop = "Shop")))
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid.copy(mechanic = "John")))
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid.copy(parts = listOf(PartRow("Filter", "1")))))
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid.copy(photoPaths = listOf("vehicles/1.jpg"))))
    }

    @Test
    fun errorForReturnsErrorOnlyWhenNotSubmittable() {
        val valid = ServiceEntryFormValues(
            dateEpochMs = 1_000_000_000_000L,
            mileage = "12345",
            serviceType = "Oil change",
        )

        assertNull(ServiceEntryFormValidation.errorFor(valid))

        val invalid = valid.copy(mileage = "")
        assertEquals(ServiceEntryFormError.MILEAGE_REQUIRED, ServiceEntryFormValidation.errorFor(invalid))
    }

    @Test
    fun sanitizeMileageKeepsOnlyDigitsUpToNine() {
        assertEquals("12345", ServiceEntryFormValidation.sanitizeMileage("12345"))
        assertEquals("123", ServiceEntryFormValidation.sanitizeMileage("1a2b3c"))
        assertEquals("123456789", ServiceEntryFormValidation.sanitizeMileage("1234567890123"))
        assertEquals("", ServiceEntryFormValidation.sanitizeMileage("abc"))
    }

    @Test
    fun sanitizeCostKeepsOnlyDigitsUpToTwelve() {
        assertEquals("12345", ServiceEntryFormValidation.sanitizeCost("12345"))
        assertEquals("123", ServiceEntryFormValidation.sanitizeCost("1a2b3c"))
        assertEquals("123456789012", ServiceEntryFormValidation.sanitizeCost("123456789012345"))
        assertEquals("", ServiceEntryFormValidation.sanitizeCost("abc"))
    }

    @Test
    fun toServiceEntryMapsBlanksToNullAndClampsQuantities() {
        val values = ServiceEntryFormValues(
            dateEpochMs = 1_000_000_000_000L,
            mileage = "12345",
            serviceType = "Oil change",
            cost = "500000",
            notes = "  ",
            workshop = "Main Shop",
            mechanic = "  ",
            parts = listOf(
                PartRow("Oil filter", "2"),
                PartRow("", "5"),          // blank name → dropped
                PartRow("Washer", "0"),    // quantity 0 → clamped to 1
                PartRow("Bolt", ""),       // blank quantity → defaults to 1
            ),
            photoPaths = listOf("service_entries/1.jpg", "service_entries/2.jpg"),
        )

        val entry = ServiceEntryFormValidation.toServiceEntry(42L, 100L, values)

        assertEquals(42L, entry.id)
        assertEquals(100L, entry.vehicleId)
        assertEquals(1_000_000_000_000L, entry.dateEpochMs)
        assertEquals(12345L, entry.mileageKm)
        assertEquals(listOf("Oil change"), entry.serviceTypes)
        assertEquals(500000L, entry.costRupiah)
        assertNull(entry.notes) // blank → null
        assertEquals("Main Shop", entry.workshop)
        assertNull(entry.mechanic) // blank → null
        assertEquals(3, entry.parts.size)
        assertEquals(ServicePart("Oil filter", 2), entry.parts[0])
        assertEquals(ServicePart("Washer", 1), entry.parts[1]) // clamped
        assertEquals(ServicePart("Bolt", 1), entry.parts[2]) // defaulted
        assertEquals(listOf("service_entries/1.jpg", "service_entries/2.jpg"), entry.photoPaths)
    }

    @Test
    fun toServiceEntryHandlesMissingOptionalCost() {
        val values = ServiceEntryFormValues(
            dateEpochMs = 1_000_000_000_000L,
            mileage = "12345",
            serviceType = "Oil change",
            cost = "",
        )

        val entry = ServiceEntryFormValidation.toServiceEntry(1L, 1L, values)
        assertNull(entry.costRupiah)
    }

    @Test
    fun toServiceEntryIgnoresPartsWithBlankNames() {
        val values = ServiceEntryFormValues(
            dateEpochMs = 1_000_000_000_000L,
            mileage = "12345",
            serviceType = "Oil change",
            parts = listOf(
                PartRow("Valid", "1"),
                PartRow("", "2"),
                PartRow("  ", "3"),
            ),
        )

        val entry = ServiceEntryFormValidation.toServiceEntry(1L, 1L, values)
        assertEquals(1, entry.parts.size)
        assertEquals("Valid", entry.parts[0].name)
    }

    @Test
    fun parseRupiahDigitsKeepsOnlyDigitsUpToTwelve() {
        assertEquals(12345L, RupiahFormat.parseRupiahDigits("12345"))
        assertEquals(123L, RupiahFormat.parseRupiahDigits("1a2b3c"))
        assertEquals(123456789012L, RupiahFormat.parseRupiahDigits("123456789012345"))
        assertNull(RupiahFormat.parseRupiahDigits(""))
        assertNull(RupiahFormat.parseRupiahDigits("abc"))
    }

    @Test
    fun sanitizeQuantityKeepsOnlyDigitsUpToFour() {
        assertEquals("2", ServiceEntryFormValidation.sanitizeQuantity("2x"))
        assertEquals("12", ServiceEntryFormValidation.sanitizeQuantity("1 2"))
        assertEquals("1234", ServiceEntryFormValidation.sanitizeQuantity("12345"))
        assertEquals("", ServiceEntryFormValidation.sanitizeQuantity("two"))
    }

    @Test
    fun inputCapsMatchTheUiSpec() {
        // UI-SPEC C-17: these are the enforced input boundaries.
        assertEquals(60, ServiceEntryFormValidation.MAX_CUSTOM_TYPE)
        assertEquals(500, ServiceEntryFormValidation.MAX_NOTES)
        assertEquals(120, ServiceEntryFormValidation.MAX_WORKSHOP)
        assertEquals(120, ServiceEntryFormValidation.MAX_MECHANIC)
        assertEquals(120, ServiceEntryFormValidation.MAX_PART_NAME)
    }
}