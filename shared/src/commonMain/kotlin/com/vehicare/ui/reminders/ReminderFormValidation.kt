package com.vehicare.ui.reminders

import com.vehicare.domain.ReminderRule

/**
 * The Add/Edit reminder form's field content, exactly as the user typed it.
 *
 * Every numeric field is a `String` (not a nullable and not a `Long`) because that is what a text
 * field holds; [ReminderFormValidation.toRule] is the single place where "empty text" becomes `null`
 * for the domain model. Numeric fields hold raw digits only — [ReminderFormValidation.sanitizeKm] and
 * [ReminderFormValidation.sanitizeMonths] are the only writers, so no grouping separator or unit ever
 * enters the value.
 *
 * [serviceTypes] is the single-select chip group's selection, in tap order. It is a list rather than
 * a `String` so the picker's existing `selected: List<String>` contract is reused unchanged; the
 * submit gate (C-35) requires **exactly one** entry.
 *
 * [enabled] is the "Reminder on" switch and defaults to `true` on add (UI-SPEC §Reminder editor).
 */
data class ReminderFormValues(
    val serviceTypes: List<String> = emptyList(),
    val intervalKm: String = "",
    val intervalMonths: String = "",
    val leadKm: String = "",
    val leadDays: String = "",
    val enabled: Boolean = true,
)

/**
 * The identity of one invalid numeric field — an enum, never copy.
 *
 * The user-facing message is one string for all four fields (C-34: one rule, one message), resolved
 * in the screen as `error_interval_min`; keeping the identity here means the screen only has to ask
 * "is this field in the set?".
 */
enum class ReminderFormField {
    INTERVAL_KM,
    INTERVAL_MONTHS,
    LEAD_KM,
    LEAD_DAYS,
}

/**
 * Every Add/Edit reminder form rule, as pure functions: no Compose, no Compose Resources, no
 * database, no clock.
 *
 * Keeping the rules here — away from the ViewModel and the screen — means the submit gate, the digit
 * sanitizers and the blank-to-null mapping are decided in exactly one place and are unit-testable on
 * both targets without a UI harness (the `ServiceEntryFormValidation` precedent).
 *
 * **One rule, one message (C-34):** a field that is **non-blank** must parse to a value `>= 1`; a
 * blank field means "this axis is not set" (or "no advance notice") and is never an error. The two
 * advance-notice fields follow the same rule, because "notify exactly at due" is expressed by leaving
 * them empty rather than by typing a zero.
 */
object ReminderFormValidation {

    /**
     * The distance input cap (UI-SPEC §Reminder editor: max 6 digits).
     *
     * Six digits is also what keeps a kilometre interval inside any sane odometer range, so the value
     * can never overflow the domain's `Long` when it is parsed.
     */
    const val MAX_KM_DIGITS = 6

    /**
     * The month input cap (UI-SPEC §Reminder editor: max 3 digits).
     *
     * Three digits caps the parsed value at 999, which is what keeps `intervalMonths` inside the
     * engine's `1..1200` precondition without the form having to know that number (T-03-17).
     */
    const val MAX_MONTH_DIGITS = 3

    /** The distance field's input filter: digits only, at most [MAX_KM_DIGITS]. */
    fun sanitizeKm(raw: String): String = raw.filter { it.isDigit() }.take(MAX_KM_DIGITS)

    /** The time field's input filter: digits only, at most [MAX_MONTH_DIGITS]. */
    fun sanitizeMonths(raw: String): String = raw.filter { it.isDigit() }.take(MAX_MONTH_DIGITS)

    /**
     * The fields that are currently invalid: exactly those that are **non-blank** and do not parse to
     * a value `>= 1` (C-34).
     *
     * A blank field is never in the set — that is the whole rule, and it is why the empty interval is
     * reported by the submit gate rather than by an error line under a field the user has not typed
     * in yet. Values are trimmed first, so a field of spaces counts as blank. Both advance-notice
     * fields use the same rule: a typed `0` is invalid because "no advance notice" is the empty case.
     *
     * A sanitizer already strips non-digits at the input boundary; a non-digit reaching this object
     * (a paste into an unsanitized caller, a test fixture) is still treated as invalid rather than
     * silently dropped.
     */
    fun errors(values: ReminderFormValues): Set<ReminderFormField> = buildSet {
        if (isInvalidCount(values.intervalKm)) add(ReminderFormField.INTERVAL_KM)
        if (isInvalidCount(values.intervalMonths)) add(ReminderFormField.INTERVAL_MONTHS)
        if (isInvalidCount(values.leadKm)) add(ReminderFormField.LEAD_KM)
        if (isInvalidCount(values.leadDays)) add(ReminderFormField.LEAD_DAYS)
    }

    /**
     * The submit gate (C-35): open only with **exactly one** service type selected, **at least one**
     * non-blank interval, and no invalid field.
     *
     * The interval clause is deliberately "at least one non-blank", not "at least one valid": a form
     * whose only interval is `"0"` has a non-blank interval and an error, so it stays closed — which
     * is exactly what [errors] is for. The chip group carries no error line; its `*` marker and this
     * disabled button carry the requirement (Phase 2 precedent).
     */
    fun isSubmittable(values: ReminderFormValues): Boolean =
        values.serviceTypes.size == 1 &&
            (values.intervalKm.isNotBlank() || values.intervalMonths.isNotBlank()) &&
            errors(values).isEmpty()

    /**
     * Maps the form's text into the domain model that crosses the repository boundary.
     *
     * Each optional field is trimmed and a blank one becomes `null`, never `""` and never `0` — the
     * engine reads `null` as "this axis is not set", while a stored `0` would violate its `>= 1`
     * precondition and fail loudly (T-03-05). A non-blank value passes through verbatim, so a
     * corrupt `0` is not silently repaired here.
     *
     * Both bookkeeping markers are written as `null` on purpose: only the once-per-cycle claim
     * (`markNotified`) and the scheduler-success write (`markArmed`) may touch them, so a form save
     * can never accidentally mark a cycle as delivered (D-51/D-53). [id] is passed straight through,
     * so `0L` stays the insert case and an edit keeps its original row id; [createdAtEpochMs] is
     * preserved by the caller because it is the time baseline of a rule whose type has no entry yet
     * (D-49).
     *
     * @throws IllegalArgumentException when the selection is not exactly one non-blank type. The
     *   gate makes that unreachable, so a violating call is a programming error and must not be
     *   papered over with an empty `serviceType`.
     */
    fun toRule(
        id: Long,
        vehicleId: Long,
        createdAtEpochMs: Long,
        values: ReminderFormValues,
    ): ReminderRule {
        val serviceType: String = requireNotNull(values.serviceTypes.singleOrNull()?.trim()) {
            "toRule needs exactly one selected service type (C-35): ${values.serviceTypes}."
        }
        require(serviceType.isNotEmpty()) {
            "toRule needs a non-blank service type (C-35): ${values.serviceTypes}."
        }

        return ReminderRule(
            id = id,
            vehicleId = vehicleId,
            serviceType = serviceType,
            intervalKm = values.intervalKm.toOptionalCount(),
            intervalMonths = values.intervalMonths.toOptionalCount(),
            leadKm = values.leadKm.toOptionalCount(),
            leadDays = values.leadDays.toOptionalCount(),
            enabled = values.enabled,
            createdAtEpochMs = createdAtEpochMs,
            notifiedCycleKey = null,
            armedCycleKey = null,
        )
    }

    /** `true` when [raw] is non-blank and does not parse to a value `>= 1` (C-34). */
    private fun isInvalidCount(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return false
        val parsed = trimmed.toLongOrNull() ?: return true
        return parsed < 1L
    }

    /** Blank → `null`; anything else → the parsed value (or `null` when it does not parse). */
    private fun String.toOptionalCount(): Long? =
        trim().takeIf { it.isNotEmpty() }?.toLongOrNull()
}
