package com.vehicare.domain

/**
 * One reminder rule: "every N km and/or every M months, measured from the last service of this
 * type" (D-46/D-47).
 *
 * [intervalKm] and [intervalMonths] are both nullable and at least one is set: a single rule may
 * carry either interval or both, and it becomes due when *either* is reached — the user's mental
 * model is "whichever comes first", not two separate reminders (D-46). [leadKm] and [leadDays] are
 * the optional per-rule advance-notice window (D-50); `null` means "notify exactly at due".
 *
 * [serviceType] is the stored service-type label a rule keys on. It always matches a label the
 * service form produced (D-44) and is never rewritten here (D-40).
 *
 * [createdAtEpochMs] is UTC start-of-day millis of the civil date the rule was created, the same
 * convention as `service_entry.service_date_epoch_ms`. It is a *time baseline*, not a timestamp:
 * D-49 makes it the baseline for a rule whose type has no entry yet.
 *
 * The two markers are bookkeeping and are never user-visible:
 *  * [notifiedCycleKey] is D-51's **claim**. A rule notifies once per due cycle, and the claim is
 *    written by an atomic compare-and-set whose affected-row count gates delivery. A `null` value
 *    means the current cycle has not been notified.
 *  * [armedCycleKey] is D-53's **proof that the OS scheduler accepted a request** for that cycle.
 *    It gates nothing and must never be read as a claim: a pre-scheduled iOS notification fires
 *    with no app code running, so the app cannot write the claim at delivery time and needs this
 *    second fact to tell "the OS already had this cycle" from "this cycle was never armed".
 */
data class ReminderRule(
    val id: Long,
    val vehicleId: Long,
    val serviceType: String,
    val intervalKm: Long?,
    val intervalMonths: Long?,
    val leadKm: Long?,
    val leadDays: Long?,
    val enabled: Boolean,
    val createdAtEpochMs: Long,
    val notifiedCycleKey: String?,
    val armedCycleKey: String?,
)

/**
 * The baseline a rule measures its interval from, for one (vehicle, service type) pair (D-47).
 *
 * Both numbers come from the **same** service entry — the newest one carrying [typeLabel] — because
 * a rule may not measure its mileage from one entry's odometer and its time from another entry's
 * date. Produced by `ServiceEntry.sq`'s `selectTypeBaselines`.
 *
 * [typeLabel] is the newest entry's stored spelling; matching is case-insensitive but the stored
 * value is never normalized or rewritten (D-40).
 */
data class TypeBaseline(
    val vehicleId: Long,
    val typeLabel: String,
    val baselineDateEpochMs: Long,
    val baselineKm: Long,
)

/**
 * A vehicle's newest service entry in one row: the date and that entry's odometer (D-49).
 *
 * Both are `null` exactly when the vehicle has no service entries at all. The mileage is used as
 * the km baseline for a rule whose own type has no entry yet, so the UI can show a real number
 * instead of inventing an odometer value. Produced by `ServiceEntry.sq`'s `selectVehicleStats`.
 */
data class VehicleStats(
    val vehicleId: Long,
    val lastServiceEpochMs: Long?,
    val lastMileageKm: Long?,
)
