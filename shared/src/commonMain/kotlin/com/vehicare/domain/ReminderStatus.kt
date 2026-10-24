package com.vehicare.domain

import com.vehicare.util.ServiceDates
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil
import kotlinx.datetime.plus

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
 * **`DueState` is a pure type declaration on purpose.** The decision that produces a [DueState]
 * lives below in [ReminderStatus] (still free of Compose, resources and platform types), and the
 * wording lives in the resource-bound copy layer — that split is what keeps the state machine
 * unit-testable in `commonTest` (no `Res`, no Compose, no `getString`) while the sentences stay
 * centralized (D-11). Do not add a formatted string or a resource reference to a state member.
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

/**
 * One evaluation of one rule: the due state, the axis that decided it, the cross-axis urgency key
 * and the cycle key that gates notification delivery.
 *
 * [axis] is the axis that decided [state] — the axis with the greater urgency (C-28), or the only
 * evaluable one. It is `null` **only** for [DueState.NoMileageData], because that state has no
 * axis to name. [DueState.DueNow] carries whichever axis produced it, so the ordering tie-break
 * "distance before time" is well defined at the boundary too.
 *
 * [urgency] is `elapsed / interval` on that axis, in that axis's own unit: `0.0` at the baseline,
 * exactly `1.0` at due, greater than `1.0` when overdue, and `-1.0` for [DueState.NoMileageData]
 * (which has no elapsed quantity at all). One comparable number is what makes "3 000 km late" and
 * "14 days late" orderable against each other without a second table (C-29). The ordering layer
 * consumes [urgency] and [axis]; the copy layer consumes [state] only.
 *
 * [cycleKey] is the once-per-cycle identity of this rule's current cycle (D-51): see
 * [ReminderStatus.cycleKey] for exactly what it covers and the three re-arm cases it guarantees.
 */
data class ReminderEvaluation(
    val state: DueState,
    val axis: ReminderAxis?,
    val urgency: Double,
    val cycleKey: String,
)

/**
 * One rule paired with its evaluation — the unit both lists are built from.
 *
 * [evaluation] is `null` exactly when the rule is disabled: a disabled rule has no due state
 * (D-43), and "Turned off" is a UI descriptor rather than a sixth [DueState]. Every enabled rule
 * has an evaluation; the only way an enabled rule avoids one is [ReminderStatus.evaluateAll], which
 * is the single place a rule meets its baseline and odometer.
 */
data class ReminderRow(
    val rule: ReminderRule,
    val evaluation: ReminderEvaluation?,
)

/**
 * The phase's whole calculation core (D-63): what is due, on which axis, by how much, in which
 * order, and under which cycle key.
 *
 * **One answer, six surfaces.** The home card's status line, the reminder row's status line, the
 * Detail summary row, the Android worker and both platforms' notification bodies all render "when
 * is this due". If any of them calculated or formatted on its own they would eventually disagree,
 * and a reminder that disagrees with itself is indistinguishable from a broken reminder (C-28). So
 * they all call [evaluateAll] and read [ReminderEvaluation.state]; the wording lives in the
 * resource-bound copy layer.
 *
 * **Pure, and clock-free on purpose.** Nothing here reads the current time, touches Compose,
 * SQLDelight, a resource or a platform type: "today" is a parameter, so the time axis is testable
 * with fixed dates and the same inputs always produce the same answer (RESEARCH §Anti-Patterns —
 * reading the system clock inside this object is the phase's named anti-pattern).
 */
object ReminderStatus {

    /**
     * Evaluates one enabled rule against its baselines.
     *
     * [typeBaseline] is the newest service entry of this rule's own type (D-47) — when it exists,
     * **both** the mileage and the date baseline come from that one row, because a rule may not
     * measure its distance from one entry's odometer and its time from another entry's date.
     * [currentMileageKm] is the vehicle's latest entry mileage of any type, used as D-49's
     * distance fallback; `null` means the vehicle has no service entry at all.
     *
     * The resolved baselines are:
     *  * distance — `typeBaseline.baselineKm`, otherwise [currentMileageKm];
     *  * time — `typeBaseline.baselineDateEpochMs`, otherwise [ReminderRule.createdAtEpochMs]
     *    (UTC start-of-day millis, so a rule created at 23:50 and one created at 00:10 the same
     *    day share a baseline — plan 01's storage convention).
     *
     * A distance-only rule with no odometer at all returns [DueState.NoMileageData] and never an
     * invented figure (D-49). A dual-axis rule is never `NoMileageData`: its distance component is
     * simply not evaluated and the state names the time axis.
     *
     * @throws IllegalArgumentException when the rule could not have been produced by the editor's
     *   gate (C-35): disabled, both intervals absent, an interval below 1, more than 1200 months,
     *   or a lead below 1. A violating row is corrupt data and must fail loudly in tests rather
     *   than render a wrong sentence (T-03-05).
     */
    fun evaluate(
        rule: ReminderRule,
        typeBaseline: TypeBaseline?,
        currentMileageKm: Long?,
        today: LocalDate,
        globalIntervalMonths: Long? = null,
    ): ReminderEvaluation {
        require(rule.enabled) {
            "A disabled rule has no due state (D-43): rule ${rule.id} must not be evaluated."
        }
        require(rule.intervalKm != null || rule.intervalMonths != null) {
            "A rule needs at least one interval (D-46): rule ${rule.id} has neither km nor months."
        }
        // The effective months — the app-level global setting when it is set (261024-u9g, D-1),
        // else the rule's own value — is what every time-axis decision below computes from.
        val effectiveMonths = globalIntervalMonths ?: rule.intervalMonths
        require(rule.intervalKm == null || rule.intervalKm >= 1L) {
            "intervalKm must be >= 1 when set (C-34): rule ${rule.id} has ${rule.intervalKm}."
        }
        require(effectiveMonths == null || effectiveMonths in 1L..MAX_INTERVAL_MONTHS) {
            "intervalMonths must be in 1..$MAX_INTERVAL_MONTHS (C-34): rule ${rule.id} has " +
                "$effectiveMonths."
        }
        require(rule.leadKm == null || rule.leadKm >= 1L) {
            "leadKm must be >= 1 when set (C-34: blank means no lead): rule ${rule.id} has " +
                "${rule.leadKm}."
        }
        require(rule.leadDays == null || rule.leadDays >= 1L) {
            "leadDays must be >= 1 when set (C-34: blank means no lead): rule ${rule.id} has " +
                "${rule.leadDays}."
        }

        val kmBaseline: Long? = typeBaseline?.baselineKm ?: currentMileageKm
        val dateBaselineEpochMs: Long = typeBaseline?.baselineDateEpochMs ?: rule.createdAtEpochMs
        val key = cycleKey(rule, kmBaseline, dateBaselineEpochMs, effectiveMonths = effectiveMonths)

        val distance = distanceReading(rule.intervalKm, kmBaseline, currentMileageKm)
        val time = timeReading(effectiveMonths, dateBaselineEpochMs, today)

        // C-29's tie-break (a) applied at the source: an exact tie names the distance axis.
        if (distance != null && (time == null || distance.progress >= time.progress)) {
            return ReminderEvaluation(
                state = distanceState(rule.leadKm, distance.kmLeft),
                axis = ReminderAxis.DISTANCE,
                urgency = distance.progress,
                cycleKey = key,
            )
        }
        if (time != null) {
            return ReminderEvaluation(
                state = timeState(rule.leadDays, time.daysLeft, time.dueDate),
                axis = ReminderAxis.TIME,
                urgency = time.progress,
                cycleKey = key,
            )
        }
        // Neither axis has an odometer to measure from: the distance-only rule on a vehicle with
        // no service entry at all (D-49). No invented odometer, no fabricated due figure.
        return ReminderEvaluation(
            state = DueState.NoMileageData,
            axis = null,
            urgency = NO_MILEAGE_URGENCY,
            cycleKey = key,
        )
    }

    /**
     * The **one** place a rule meets its baseline and its odometer — every surface (Reminders list,
     * Detail summary row, dashboard, Android worker) must call this rather than re-implementing the
     * lookup, because a second implementation is exactly how one screen ends up measuring from a
     * different service entry than another.
     *
     * [baselines] are grouped by vehicle and matched by service type; [stats] are indexed by
     * vehicle. Type matching is `lowercase()` on both sides — locale-invariant and case-insensitive,
     * because the stored spelling is never normalized or rewritten (D-40/D-47). A baseline
     * belonging to another vehicle is never applied.
     *
     * Enabled rules are evaluated; disabled rules come back with a `null` [ReminderRow.evaluation]
     * and are never passed to [evaluate], so a disabled rule cannot fail a precondition it would
     * otherwise satisfy.
     */
    fun evaluateAll(
        rules: List<ReminderRule>,
        baselines: List<TypeBaseline>,
        stats: List<VehicleStats>,
        today: LocalDate,
        globalIntervalMonths: Long? = null,
    ): List<ReminderRow> {
        if (rules.isEmpty()) return emptyList()

        val baselinesByVehicle = baselines.groupBy { it.vehicleId }
        val statsByVehicle = stats.associateBy { it.vehicleId }

        return rules.map { rule ->
            val evaluation = if (!rule.enabled) {
                null
            } else {
                val typeBaseline = baselinesByVehicle[rule.vehicleId]
                    .orEmpty()
                    .firstOrNull { it.typeLabel.lowercase() == rule.serviceType.lowercase() }
                evaluate(
                    rule = rule,
                    typeBaseline = typeBaseline,
                    currentMileageKm = statsByVehicle[rule.vehicleId]?.lastMileageKm,
                    today = today,
                    globalIntervalMonths = globalIntervalMonths,
                )
            }
            ReminderRow(rule = rule, evaluation = evaluation)
        }
    }

    /**
     * The once-per-cycle identity of a rule's current cycle (D-51, D-48, D-47).
     *
     * Pass the **resolved** baselines — the same two values [evaluate] used, i.e.
     * `typeBaseline.baselineKm` / [currentMileageKm] and
     * `typeBaseline.baselineDateEpochMs` / [ReminderRule.createdAtEpochMs] — not `typeBaseline`
     * itself. That is deliberate: a rule whose type has no entry yet measures from the vehicle's
     * latest mileage under D-49's fallback, so its key must move when that mileage moves.
     *
     * The key covers exactly the inputs that define a cycle, which gives the three properties the
     * delivery path depends on:
     *  1. logging a service of the rule's type moves the baseline → new key → the rule re-arms
     *     (D-48);
     *  2. editing either interval or either lead → new key → the rule re-arms (D-51), even though
     *     no baseline moved;
     *  3. two evaluations against the same baseline compute the **same** key, so the conditional
     *     `markNotified` claim affects zero rows and no second notification is posted (D-51).
     *
     * The worker claims delivery on this exact string, so it must stay deterministic: no
     * timestamps, no ordering, no formatting that depends on a locale (T-03-07).
     *
     * [effectiveMonths] (261024-u9g, D-1) is the resolved interval to key on — the app-level global
     * setting when set, else the rule's own; callers that did not resolve one may omit it and the
     * key falls back to `rule.intervalMonths`. When the global setting is set it replaces
     * `intervalMonths` for both the due arithmetic and this key, so changing the setting naturally
     * starts a new cycle and re-arms every previously notified row.
     */
    fun cycleKey(
        rule: ReminderRule,
        kmBaseline: Long?,
        dateBaselineEpochMs: Long,
        effectiveMonths: Long? = null,
    ): String =
        "b" + (kmBaseline ?: -1L) +
            ":" + dateBaselineEpochMs +
            "|i" + (rule.intervalKm ?: -1L) +
            ":" + (effectiveMonths ?: rule.intervalMonths ?: -1L) +
            "|l" + (rule.leadKm ?: -1L) +
            ":" + (rule.leadDays ?: -1L)

    /** The editor caps month input at three digits; this only guards a corrupt row (C-34). */
    private const val MAX_INTERVAL_MONTHS = 1200L

    /** [ReminderEvaluation.urgency] for [DueState.NoMileageData]: there is no elapsed quantity. */
    private const val NO_MILEAGE_URGENCY = -1.0

    /** The distance axis, resolved: how far is left and how far through the interval we are. */
    private class DistanceReading(val kmLeft: Long, val progress: Double)

    /** The time axis, resolved: how many days are left, the due date and the progress. */
    private class TimeReading(val daysLeft: Long, val dueDate: LocalDate, val progress: Double)

    /**
     * The distance axis, or `null` when it does not exist (`intervalKm == null`) or cannot be
     * measured (`kmBaseline`/`currentMileageKm` absent — D-49).
     */
    private fun distanceReading(
        intervalKm: Long?,
        kmBaseline: Long?,
        currentMileageKm: Long?,
    ): DistanceReading? {
        if (intervalKm == null || kmBaseline == null || currentMileageKm == null) return null
        val dueKm = kmBaseline + intervalKm
        return DistanceReading(
            kmLeft = dueKm - currentMileageKm,
            progress = (currentMileageKm - kmBaseline).toDouble() / intervalKm.toDouble(),
        )
    }

    /**
     * The time axis. Always evaluable when [intervalMonths] is set, because D-49's fallback
     * (the rule's creation date) is never absent.
     *
     * Month arithmetic goes through `LocalDate.plus(months, DateTimeUnit.MONTH)`, which is
     * calendar-correct and clamps a 31st to the last valid day (31 Jan + 1 month = 28 Feb); day
     * counts go through `daysUntil`. Never a 30-day month, never 180 days for 6 months — that is
     * how the time axis drifts across month lengths and leap years (T-03-06).
     */
    private fun timeReading(
        intervalMonths: Long?,
        dateBaselineEpochMs: Long,
        today: LocalDate,
    ): TimeReading? {
        if (intervalMonths == null) return null
        val baselineDate = ServiceDates.fromStorage(dateBaselineEpochMs)
        val dueDate = baselineDate.plus(intervalMonths.toInt(), DateTimeUnit.MONTH)
        val intervalDays = baselineDate.daysUntil(dueDate).toLong()
        val daysLeft = today.daysUntil(dueDate).toLong()
        return TimeReading(
            daysLeft = daysLeft,
            dueDate = dueDate,
            progress = (intervalDays - daysLeft).toDouble() / intervalDays.toDouble(),
        )
    }

    /**
     * The state on the distance axis. The check order is binding (C-28): overdue first, then the
     * exact-zero boundary as [DueState.DueNow], **then** the lead window — so the boundary can
     * never be worded as "due soon" — and only then [DueState.Upcoming]. A `null` lead means there
     * is no Due-soon band at all: the rule travels Upcoming → Overdue (D-50).
     */
    private fun distanceState(leadKm: Long?, kmLeft: Long): DueState = when {
        kmLeft < 0L -> DueState.Overdue(kmOver = -kmLeft, daysOver = null)
        kmLeft == 0L -> DueState.DueNow
        leadKm != null && kmLeft <= leadKm -> DueState.DueSoon(kmLeft = kmLeft, daysLeft = null)
        else -> DueState.Upcoming(kmLeft = kmLeft, dueDate = null)
    }

    /** The time-axis twin of [distanceState]; identical check order and identical lead semantics. */
    private fun timeState(leadDays: Long?, daysLeft: Long, dueDate: LocalDate): DueState = when {
        daysLeft < 0L -> DueState.Overdue(kmOver = null, daysOver = -daysLeft)
        daysLeft == 0L -> DueState.DueNow
        leadDays != null && daysLeft <= leadDays -> DueState.DueSoon(kmLeft = null, daysLeft = daysLeft)
        else -> DueState.Upcoming(kmLeft = null, dueDate = dueDate)
    }
}

