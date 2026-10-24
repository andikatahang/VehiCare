package com.vehicare.domain

import kotlinx.datetime.LocalDate

/**
 * Which of a rule's two intervals decided its due state (D-46, "whichever is reached first").
 *
 * The state is decided by the axis with the greater urgency, and the sentence names **that** axis —
 * so the axis is part of the answer, not an implementation detail the caller has to re-derive.
 */
enum class ReminderAxis {
    /** `interval_km` — measured from the odometer of the last entry of the rule's type (D-47). */
    DISTANCE,

    /** `interval_months` — measured from that same entry's date, or the rule's creation date (D-49). */
    TIME,
}

/**
 * The due-state vocabulary every surface speaks (C-28): the home card's status line, the reminder
 * row's status line, the Detail summary row's meta sentence and both platforms' notification body
 * all resolve to one of these five states, so Android and iOS copy cannot drift.
 *
 * **This file is a pure type declaration on purpose.** The decision that produces a [DueState]
 * lives in the same package's evaluation logic, and the wording lives in the resource-bound copy
 * layer — that split is what keeps the state machine unit-testable in `commonTest` (no `Res`, no
 * Compose, no `getString`) while the sentences stay centralized (D-11). Do not add a function, a
 * `when`, a formatted string or a resource reference here.
 *
 * Each member carries **numbers only**, never formatted text: a `Long` kilometre count, a day count
 * or a [LocalDate] is what the copy layer formats through `RupiahFormat`/`ServiceDates`, so a
 * second formatting implementation cannot appear on one platform only (T-02-02-03).
 */
sealed interface DueState {

    /**
     * The rule is not yet inside its lead window. One of the two payloads is `null`, depending on
     * the axis: [kmLeft] for [ReminderAxis.DISTANCE], [dueDate] for [ReminderAxis.TIME].
     *
     * Becomes `Due in 3.400 km` / `Due on 12 Mar 2027`.
     */
    data class Upcoming(
        val kmLeft: Long?,
        val dueDate: LocalDate?,
    ) : DueState

    /**
     * The rule has entered its optional advance-notice window but is not due yet (D-50). This state
     * only exists when the rule carries a lead — a rule with no lead travels [Upcoming] →
     * [Overdue], because "notify exactly at due" is the honest behaviour of an empty lead.
     *
     * Becomes `Due soon — 900 km left` / `Due soon — 20 days left`.
     */
    data class DueSoon(
        val kmLeft: Long?,
        val daysLeft: Long?,
    ) : DueState

    /**
     * The exact-zero boundary: the interval has just been reached (D-46). Carries no number because
     * there is nothing left to count.
     *
     * Becomes `Due now`.
     *
     * **This is the overdue bucket, not an "upcoming" one.** It renders the enforced `Danger` tone
     * (D-60) and is never a "0 days left" or "0 km left" upcoming sentence — a rule *is* due the
     * moment its interval is reached, so inventing a fourth state here would be a lie.
     */
    data object DueNow : DueState

    /**
     * The interval was passed. One of the two payloads is `null`, depending on the axis, and the
     * value is strictly positive: the exact-zero case is [DueNow], never `Overdue(0)`.
     *
     * Becomes `Overdue by 300 km` / `Overdue by 12 days`.
     */
    data class Overdue(
        val kmOver: Long?,
        val daysOver: Long?,
    ) : DueState

    /**
     * A distance-only rule whose vehicle has no service entry at all, so there is no odometer to
     * measure from and no honest figure to show (D-49).
     *
     * Becomes `No mileage data yet` — never an extrapolated or invented odometer value. The time
     * axis is always evaluable, so this state can only be produced by the distance axis.
     */
    data object NoMileageData : DueState
}
