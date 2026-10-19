package com.vehicare.ui.vehicles

import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleType

/**
 * The Add/Edit form's field content, exactly as the user typed it.
 *
 * Every optional field is a `String` (not a nullable) because that is what a text field holds;
 * [VehicleFormValidation.toVehicle] is the single place where "empty text" becomes `null` for the
 * domain model. [year] stays text on purpose — it is a digits-only field, never a number (D-13) —
 * and [photoPath] is carried here so the photo block plan 01-08 appends to this form needs no
 * rework.
 */
data class VehicleFormValues(
    val name: String = "",
    val make: String = "",
    val model: String = "",
    val year: String = "",
    val plate: String = "",
    val type: VehicleType = VehicleType.Car,
    val photoPath: String? = null,
)

/**
 * The form's error identity — an enum, never copy.
 *
 * The user-facing message stays the `error_name_required` entry of
 * `composeResources/values/strings.xml`, resolved in the screen (D-11): a validation object that
 * returned English text would smuggle copy out of `strings.xml` and break the one-copy-source rule.
 */
enum class VehicleFormError { NAME_REQUIRED }

/**
 * Every Add/Edit form rule, as pure functions: no Compose, no Compose Resources, no database.
 *
 * Keeping the rules here — away from the ViewModel and the screen — means the submit gate, the
 * digits-only Year filter and the blank-to-null mapping are decided in exactly one place and are
 * unit-testable on both targets without a UI harness.
 */
object VehicleFormValidation {

    /** The submit gate (D-09): only the name must be non-blank after trimming. */
    fun isSubmittable(values: VehicleFormValues): Boolean = values.name.isNotBlank()

    /** The error the form is currently in, or `null` when the name is acceptable. */
    fun errorFor(values: VehicleFormValues): VehicleFormError? =
        if (isSubmittable(values)) null else VehicleFormError.NAME_REQUIRED

    /**
     * Keeps only the digit characters of [raw], at most the first four (D-13).
     *
     * There is deliberately no range check: "0" and "9999" both survive, because D-13 declares the
     * Year field a digits-only text field and nothing more. Values are stored as bound text, never
     * interpolated (T-01-42).
     */
    fun sanitizeYear(raw: String): String = raw.filter { it.isDigit() }.take(4)

    /**
     * Maps the form's text into the domain model that crosses the repository boundary (T-01-40).
     *
     * Each optional field is trimmed and a blank one becomes `null`, never `""` — the Detail screen
     * renders `—` for null and would render an empty string as a blank line instead. [id] is passed
     * straight through, so `0L` stays the insert case and an edit keeps its original row id.
     */
    fun toVehicle(id: Long, values: VehicleFormValues): Vehicle = Vehicle(
        id = id,
        // Passed through untrimmed on purpose: `isSubmittable` is the single gate, so no second
        // normalisation path exists that could disagree with it.
        name = values.name,
        make = values.make.trimToNull(),
        model = values.model.trimToNull(),
        year = values.year.trimToNull(),
        plate = values.plate.trimToNull(),
        type = values.type,
        photoPath = values.photoPath,
    )

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }
}
