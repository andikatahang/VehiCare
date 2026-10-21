package com.vehicare.ui.service

import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.util.RupiahFormat

/**
 * The Add/Edit service entry form's field content, exactly as the user typed it.
 *
 * Every text field is a `String` (not a nullable) because that is what a text field holds;
 * [ServiceEntryFormValidation.toServiceEntry] is the single place where "empty text" becomes `null`
 * for the domain model. Numeric fields hold raw digits only — formatting is applied only for display.
 *
 * [serviceTypes] is the multi-select chip group's selection, **in tap order** — that order is the
 * stored display order. It may be empty while the user is editing; the submit gate is what requires
 * at least one type.
 */
data class ServiceEntryFormValues(
    val dateEpochMs: Long? = null,
    val mileage: String = "",
    val serviceTypes: List<String> = emptyList(),
    val cost: String = "",
    val notes: String = "",
    val workshop: String = "",
    val mechanic: String = "",
    val parts: List<PartRow> = emptyList(),
    val photoPaths: List<String> = emptyList(),
)

/**
 * One part row as the user types it — the ViewModel owns the list.
 *
 * Both fields are text: name free, quantity digits-only (validated in ViewModel).
 * Quantity defaults to "1" and is clamped to ≥ 1 on save.
 */
data class PartRow(
    val name: String = "",
    val quantity: String = "1",
)

/**
 * The form's error identity — an enum, never copy.
 *
 * The user-facing message stays the `error_mileage_required` entry of
 * `composeResources/values/strings.xml`, resolved in the screen (D-11).
 */
enum class ServiceEntryFormError { MILEAGE_REQUIRED }

/**
 * Every Add/Edit service entry form rule, as pure functions: no Compose, no Compose Resources,
 * no database.
 *
 * Keeping the rules here — away from the ViewModel and the screen — means the submit gate, the
 * digit sanitizers and the blank-to-null mapping are decided in exactly one place and are
 * unit-testable on both targets without a UI harness.
 */
object ServiceEntryFormValidation {

    /** UI-SPEC C-17 input caps, enforced at the input boundary (WR-07). */
    const val MAX_CUSTOM_TYPE = 60
    const val MAX_NOTES = 500
    const val MAX_WORKSHOP = 120
    const val MAX_MECHANIC = 120
    const val MAX_PART_NAME = 120

    /** The submit gate (D-27): date, non-blank mileage, and at least one service type. */
    fun isSubmittable(values: ServiceEntryFormValues): Boolean =
        values.dateEpochMs != null && values.mileage.isNotBlank() && values.serviceTypes.isNotEmpty()

    /** The error the form is currently in, or `null` when the required fields are acceptable. */
    fun errorFor(values: ServiceEntryFormValues): ServiceEntryFormError? =
        if (isSubmittable(values)) null else ServiceEntryFormError.MILEAGE_REQUIRED

    /**
     * Keeps only the digit characters of [raw], at most the first nine (mileage ≤ 9 digits).
     */
    fun sanitizeMileage(raw: String): String = raw.filter { it.isDigit() }.take(9)

    /**
     * Keeps only the digit characters of [raw], at most the first twelve (cost ≤ 12 digits).
     */
    fun sanitizeCost(raw: String): String = raw.filter { it.isDigit() }.take(12)

    /**
     * Keeps only the digit characters of [raw], at most four. Blank is allowed so the user can
     * retype; [toServiceEntry] already falls back to 1 for a blank quantity (WR-08).
     */
    fun sanitizeQuantity(raw: String): String = raw.filter { it.isDigit() }.take(4)

    /**
     * Maps the form's text into the domain model that crosses the repository boundary.
     *
     * Each optional field is trimmed and a blank one becomes `null`, never `""` — the Detail screen
     * renders `—` for null and would render an empty string as a blank line instead. [id] is passed
     * straight through, so `0L` stays the insert case and an edit keeps its original row id.
     *
     * Quantities are clamped to ≥ 1. Parts with a blank name are dropped (SVC-02 rows need a name).
     */
    fun toServiceEntry(id: Long, vehicleId: Long, values: ServiceEntryFormValues): ServiceEntry {
        val date = values.dateEpochMs ?: 0L
        val mileageKm = values.mileage.trimToNull()?.toLongOrNull() ?: 0L
        val costRupiah = values.cost.trimToNull()?.let { RupiahFormat.parseRupiahDigits(it) }
        val notes = values.notes.trimToNull()
        val workshop = values.workshop.trimToNull()
        val mechanic = values.mechanic.trimToNull()

        val parts = values.parts
            .mapNotNull { part ->
                val name = part.name.trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val quantity = part.quantity.trimToNull()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                ServicePart(name = name, quantity = quantity)
            }

        return ServiceEntry(
            id = id,
            vehicleId = vehicleId,
            dateEpochMs = date,
            mileageKm = mileageKm,
            // Boundary guard (T-02-08-02): the chip transitions already collapse case-insensitive
            // duplicates, but the mapping is the last place before storage, so it dedupes too.
            serviceTypes = values.serviceTypes.distinctBy { it.lowercase() },
            costRupiah = costRupiah,
            notes = notes,
            workshop = workshop,
            mechanic = mechanic,
            parts = parts,
            photoPaths = values.photoPaths,
        )
    }

    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }
}