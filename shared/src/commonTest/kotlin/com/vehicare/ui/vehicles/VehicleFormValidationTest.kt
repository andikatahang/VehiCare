package com.vehicare.ui.vehicles

import com.vehicare.domain.VehicleType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every declared Add/Edit form rule, pinned at the unit level: the name-only submit gate (D-09),
 * the digits-only four-character Year rule (D-13, deliberately without any range check) and the
 * blank-becomes-null mapping the Detail screen's em-dash rendering depends on.
 */
class VehicleFormValidationTest {

    @Test
    fun blankNamesAreNotSubmittable() {
        assertFalse(VehicleFormValidation.isSubmittable(VehicleFormValues(name = "")))
        assertFalse(VehicleFormValidation.isSubmittable(VehicleFormValues(name = "   ")))
        assertFalse(VehicleFormValidation.isSubmittable(VehicleFormValues(name = "\t\n")))
    }

    @Test
    fun nonBlankNamesAreSubmittableEvenWithSurroundingWhitespace() {
        assertTrue(VehicleFormValidation.isSubmittable(VehicleFormValues(name = "Corolla")))
        assertTrue(VehicleFormValidation.isSubmittable(VehicleFormValues(name = "  Corolla  ")))
    }

    @Test
    fun theOnlyErrorIsTheRequiredName() {
        assertEquals(
            expected = VehicleFormError.NAME_REQUIRED,
            actual = VehicleFormValidation.errorFor(VehicleFormValues(name = "")),
        )
        assertEquals(
            expected = VehicleFormError.NAME_REQUIRED,
            actual = VehicleFormValidation.errorFor(VehicleFormValues(name = "   ")),
        )
        assertNull(VehicleFormValidation.errorFor(VehicleFormValues(name = "Corolla")))
    }

    @Test
    fun aBlankNameIsNotHiddenByAnEmptyOptionalField() {
        // The optional fields can never make the form un-submittable — only the name gates it.
        val everythingElseBlank = VehicleFormValues(
            name = "Corolla",
            make = "",
            model = " ",
            year = "",
            plate = "",
        )
        assertTrue(VehicleFormValidation.isSubmittable(everythingElseBlank))
        assertNull(VehicleFormValidation.errorFor(everythingElseBlank))
    }

    @Test
    fun sanitizeYearKeepsTheFirstFourDigitsAndNothingElse() {
        assertEquals("", VehicleFormValidation.sanitizeYear(""))
        assertEquals("20", VehicleFormValidation.sanitizeYear("20a"))
        assertEquals("", VehicleFormValidation.sanitizeYear("abc"))
        assertEquals("2024", VehicleFormValidation.sanitizeYear("20246"))
        assertEquals("1998", VehicleFormValidation.sanitizeYear("1998"))
        // Leading zeros are digits too — the field is text, never a number.
        assertEquals("007", VehicleFormValidation.sanitizeYear("007"))
    }

    @Test
    fun sanitizeYearImposesNoRangeRule() {
        // D-13: "no range validation — deliberately". A plausible-year window would delete these.
        assertEquals("0", VehicleFormValidation.sanitizeYear("0"))
        assertEquals("9999", VehicleFormValidation.sanitizeYear("9999"))
        assertEquals("20", VehicleFormValidation.sanitizeYear("2 0"))
    }

    @Test
    fun toVehicleKeepsTheIdItIsHanded() {
        // 0L is the add case (the repository inserts); any other id is an in-place update.
        assertEquals(0L, VehicleFormValidation.toVehicle(0L, VehicleFormValues(name = "Corolla")).id)
        assertEquals(7L, VehicleFormValidation.toVehicle(7L, VehicleFormValues(name = "Corolla")).id)
    }

    @Test
    fun blankOptionalFieldsBecomeNullAndNonBlankOnesAreTrimmed() {
        val vehicle = VehicleFormValidation.toVehicle(
            id = 0L,
            values = VehicleFormValues(
                name = "Corolla",
                make = "  Toyota ",
                model = "",
                year = " ",
                plate = "\t",
            ),
        )

        assertEquals("Toyota", vehicle.make)
        assertNull(vehicle.model)
        assertNull(vehicle.year)
        assertNull(vehicle.plate)
    }

    @Test
    fun everyFieldSurvivesTheHappyPathMapping() {
        val vehicle = VehicleFormValidation.toVehicle(
            id = 12L,
            values = VehicleFormValues(
                name = "Betsy",
                make = "Ford",
                model = "F-150",
                year = "2019",
                plate = "B 1234 XYZ",
                type = VehicleType.Truck,
                photoPath = "photos/12.jpg",
            ),
        )

        assertEquals("Betsy", vehicle.name)
        assertEquals("Ford", vehicle.make)
        assertEquals("F-150", vehicle.model)
        assertEquals("2019", vehicle.year)
        assertEquals("B 1234 XYZ", vehicle.plate)
        assertEquals(VehicleType.Truck, vehicle.type)
        assertEquals("photos/12.jpg", vehicle.photoPath)
    }

    @Test
    fun typeDefaultsToTheSchemaDefaultAndIsAlwaysPreserved() {
        assertEquals(VehicleType.Car, VehicleFormValues().type)
        assertEquals(VehicleType.Car, VehicleFormValidation.toVehicle(0L, VehicleFormValues(name = "x")).type)
        assertEquals(
            VehicleType.Motorcycle,
            VehicleFormValidation.toVehicle(
                0L,
                VehicleFormValues(name = "x", type = VehicleType.Motorcycle),
            ).type,
        )
    }

    @Test
    fun photoPathIsCarriedThroughUntouched() {
        // A photo picked before the save must not be dropped by the mapper (T-01-44).
        assertEquals(
            "photos/keep-me.jpg",
            VehicleFormValidation.toVehicle(
                4L,
                VehicleFormValues(name = "x", photoPath = "photos/keep-me.jpg"),
            ).photoPath,
        )
        assertNull(VehicleFormValidation.toVehicle(0L, VehicleFormValues(name = "x")).photoPath)
    }

    @Test
    fun theNameGateLivesInIsSubmittableNotInTheMapper() {
        // `toVehicle` deliberately passes the name through untrimmed: the gate is a single,
        // testable rule in `isSubmittable`, so a hidden second normalisation path cannot disagree.
        val vehicle = VehicleFormValidation.toVehicle(0L, VehicleFormValues(name = "  "))
        assertTrue(vehicle.name.trim().isEmpty())
        assertFalse(VehicleFormValidation.isSubmittable(VehicleFormValues(name = "  ")))
    }
}
