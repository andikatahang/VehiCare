package com.vehicare.domain

/**
 * One vehicle on the dashboard: its identity plus every rule it owns, evaluated.
 *
 * This is the unit of ordering, so it carries exactly the two vehicle fields the tie-break chain
 * needs. There is deliberately no `createdAt`: "newest-first" is `id DESC` (the `vehicle` table has
 * no `created_at` column), which is also the order `selectAllVehicles` returns.
 *
 * [rows] may contain disabled rules — they are the whole of the last bucket — and may be empty: a
 * vehicle with no rule at all is still a dashboard row, it just has no active reminder.
 */
data class VehicleReminderGroup(
    val vehicleId: Long,
    val vehicleName: String,
    val rows: List<ReminderRow>,
)

/**
 * The phase's ordering contract (D-57, C-29): four fixed buckets and a tie-break chain that is
 * total, so the same snapshot always produces the same order.
 *
 * **Both lists sort here, never in SQL.** `urgency = elapsed / interval` spans two different units
 * (kilometres and days) and cannot be expressed portably in a SQL sort clause, and the tie-break
 * chain would make the query unindexable for a handful of rows (RESEARCH U6). Sorting in memory is
 * free at this scale (T-03-08).
 *
 * **Pure and non-mutating.** `sortedWith` returns a new list and no function here writes to its
 * argument, so a ViewModel can call them inside a `combine` on every emission without copying
 * first or worrying about a shared list being reordered under another collector.
 *
 * **The group a vehicle falls into is decided by its most urgent enabled rule** — the same rule its
 * card displays ([topRule]). A vehicle whose only rule is disabled is therefore *not* "overdue by
 * association": it has no active reminder, exactly like a vehicle with no rules at all.
 */
object ReminderOrdering {

    /** `Overdue` **and** `DueNow`: the boundary is due, so it shares the overdue tone and bucket. */
    private const val BUCKET_DUE = 0

    /** `DueSoon` — only reachable when the rule carries a lead window (D-50). */
    private const val BUCKET_DUE_SOON = 1

    /**
     * `Upcoming` **and** `NoMileageData`. Unevaluable is filed under Upcoming on purpose: a fifth
     * bucket would let a vehicle whose only rule is unevaluable be mistaken for a vehicle with no
     * reminder, which is a different sentence on the card (D-49, UI-SPEC §dashboard ordering).
     */
    private const val BUCKET_UPCOMING = 2

    /** No enabled rule at all — `evaluation == null`. D-16's newest-first order survives here. */
    private const val BUCKET_NO_ACTIVE_REMINDER = 3

    /** `DISTANCE` before `TIME` at equal urgency — C-29's tie-break (a). */
    private const val AXIS_DISTANCE = 0
    private const val AXIS_TIME = 1
    private const val AXIS_NONE = 2

    /**
     * The Reminders screen's row order, also used by the dashboard to pick a vehicle's top rule:
     * overdue/due-now → due-soon → upcoming (unevaluable last) → disabled, and inside the first
     * three buckets by urgency descending, then the distance axis before the time axis at equal
     * urgency, then the lower rule id.
     *
     * Disabled rows are ordered by id **descending** (newest-first) and never interleave with
     * enabled rows, because they are the whole of the fourth bucket.
     *
     * The chain stops at the rule id: ids are unique, so the order is total on this list and
     * `sortedWith` (stable) cannot reshuffle two identical snapshots (PITFALLS §CMP re-sorting).
     */
    fun sortRules(rows: List<ReminderRow>): List<ReminderRow> =
        rows.sortedWith(Comparator { first, second -> compareRules(first, second) })

    /**
     * The vehicle's displayed rule: the first enabled row in [sortRules] order, or `null` when the
     * vehicle has no enabled rule at all.
     *
     * This is the rule the home card's status line names and the rule whose bucket decides the
     * vehicle's group — one rule, one answer, both surfaces.
     */
    fun topRule(rows: List<ReminderRow>): ReminderRow? =
        sortRules(rows).firstOrNull { it.evaluation != null }

    /**
     * The dashboard order (D-57, superseding D-16): vehicles with an active reminder first, ordered
     * by their top rule's bucket, then urgency descending, then the distance axis before the time
     * axis, then the lower top-rule id, then the vehicle name (lowercased, locale-invariant) and
     * finally the lower vehicle id.
     *
     * A vehicle with no enabled rule is bucket 3 and is ordered **newest-first by `vehicleId`
     * descending** — that is D-16's order preserved inside the fourth group rather than deleted,
     * not a fourth re-use of the urgency chain.
     *
     * Each group's top rule is computed once per group, not once per comparison.
     */
    fun sortVehicles(groups: List<VehicleReminderGroup>): List<VehicleReminderGroup> {
        val withTopRule = groups.map { group -> group to topRule(group.rows) }
        return withTopRule
            .sortedWith(Comparator { first, second ->
                compareVehicles(
                    firstGroup = first.first,
                    firstTop = first.second,
                    secondGroup = second.first,
                    secondTop = second.second,
                )
            })
            .map { it.first }
    }

    private fun compareRules(first: ReminderRow, second: ReminderRow): Int {
        val firstBucket = bucketOf(first)
        val secondBucket = bucketOf(second)
        if (firstBucket != secondBucket) return firstBucket.compareTo(secondBucket)

        if (firstBucket == BUCKET_NO_ACTIVE_REMINDER) {
            // Newest-first inside the fourth bucket (D-16, preserved by D-57).
            return second.rule.id.compareTo(first.rule.id)
        }

        val firstEvaluation = first.evaluation
        val secondEvaluation = second.evaluation
        // Unreachable: a null evaluation *is* bucket 3, handled above.
        if (firstEvaluation == null || secondEvaluation == null) return 0

        return compareWithinBucket(first, firstEvaluation, second, secondEvaluation)
    }

    private fun compareVehicles(
        firstGroup: VehicleReminderGroup,
        firstTop: ReminderRow?,
        secondGroup: VehicleReminderGroup,
        secondTop: ReminderRow?,
    ): Int {
        if (firstTop == null && secondTop == null) {
            return secondGroup.vehicleId.compareTo(firstGroup.vehicleId)
        }
        if (firstTop == null) return 1
        if (secondTop == null) return -1

        val firstBucket = bucketOf(firstTop)
        val secondBucket = bucketOf(secondTop)
        if (firstBucket != secondBucket) return firstBucket.compareTo(secondBucket)

        val firstEvaluation = firstTop.evaluation
        val secondEvaluation = secondTop.evaluation
        if (firstEvaluation == null || secondEvaluation == null) return 0

        val byRule = compareWithinBucket(firstTop, firstEvaluation, secondTop, secondEvaluation)
        if (byRule != 0) return byRule

        val byName = firstGroup.vehicleName.lowercase().compareTo(secondGroup.vehicleName.lowercase())
        if (byName != 0) return byName

        return firstGroup.vehicleId.compareTo(secondGroup.vehicleId)
    }

    /**
     * The shared part of both chains for buckets 0-2: unevaluable last, urgency descending, distance
     * before time, lower rule id.
     */
    private fun compareWithinBucket(
        first: ReminderRow,
        firstEvaluation: ReminderEvaluation,
        second: ReminderRow,
        secondEvaluation: ReminderEvaluation,
    ): Int {
        // UI-SPEC: unevaluable rules rank at the end of the Upcoming group. The engine's -1.0
        // urgency already achieves that for any non-negative progress; the explicit key keeps the
        // guarantee even if an odometer is logged below its baseline.
        val firstUnevaluable = firstEvaluation.state == DueState.NoMileageData
        val secondUnevaluable = secondEvaluation.state == DueState.NoMileageData
        if (firstUnevaluable != secondUnevaluable) return if (firstUnevaluable) 1 else -1

        val byUrgency = secondEvaluation.urgency.compareTo(firstEvaluation.urgency)
        if (byUrgency != 0) return byUrgency

        val byAxis = axisRank(firstEvaluation.axis).compareTo(axisRank(secondEvaluation.axis))
        if (byAxis != 0) return byAxis

        return first.rule.id.compareTo(second.rule.id)
    }

    private fun bucketOf(row: ReminderRow): Int = when (row.evaluation?.state) {
        null -> BUCKET_NO_ACTIVE_REMINDER
        DueState.DueNow -> BUCKET_DUE
        is DueState.Overdue -> BUCKET_DUE
        is DueState.DueSoon -> BUCKET_DUE_SOON
        is DueState.Upcoming -> BUCKET_UPCOMING
        DueState.NoMileageData -> BUCKET_UPCOMING
    }

    private fun axisRank(axis: ReminderAxis?): Int = when (axis) {
        ReminderAxis.DISTANCE -> AXIS_DISTANCE
        ReminderAxis.TIME -> AXIS_TIME
        null -> AXIS_NONE
    }
}
