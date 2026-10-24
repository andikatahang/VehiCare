package com.vehicare.data

import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.VehicleStats
import kotlinx.coroutines.flow.Flow

/**
 * The only reminder storage contract the screens, the notification worker and the foreground sweep
 * may use.
 *
 * No generated SQLDelight type appears in these signatures on purpose: consumers observe domain
 * models and never touch a driver, a query or a row type (the `VehicleRepository` /
 * `ServiceEntryRepository` convention).
 */
interface ReminderRepository {

    /**
     * One vehicle's rules in stable id order (D-41). Emits again whenever `reminder_rule` changes.
     *
     * Ordering for display is **not** applied here: the most-urgent-first order is computed in
     * Kotlin by `ReminderOrdering`, because it spans the km and time axes and cannot be expressed
     * portably in SQL.
     */
    fun observeRules(vehicleId: Long): Flow<List<ReminderRule>>

    /**
     * Every rule in the database, grouped by vehicle then id.
     *
     * This is a flat whole-database read on purpose: the dashboard needs every vehicle's rules, and
     * the Reminders screen filters its own vehicle in Kotlin. One aggregated read per list load,
     * never one query per vehicle (RESEARCH U6 Pattern 3 — no N+1).
     */
    fun observeAllRules(): Flow<List<ReminderRule>>

    /** One rule, or `null` when it no longer exists. Emits again whenever its row changes. */
    fun observeRule(ruleId: Long): Flow<ReminderRule?>

    /**
     * Every (vehicle, service type) baseline in the database (D-47).
     *
     * Flat and whole-database for the same reason as [observeAllRules]: the dashboard and the
     * Reminders screen both need the full set, and `ReminderStatus.evaluateAll` indexes it by
     * vehicle. Each row pairs the newest entry's date with **that same row's** odometer.
     */
    fun observeTypeBaselines(): Flow<List<TypeBaseline>>

    /**
     * Every vehicle's newest service date and that same entry's odometer (D-49's fallback).
     *
     * A vehicle with no service entry at all has no row here — the app then says "no mileage data
     * yet" rather than inventing an odometer value.
     */
    fun observeVehicleStats(): Flow<List<VehicleStats>>

    /**
     * Inserts when [ReminderRule.id] is `0L`, updates in place otherwise, and returns the rule's id.
     *
     * The two bookkeeping markers are **ignored on purpose**: `insert` writes `NULL` for both, and
     * `update` clears both in SQL. Only the once-per-cycle claim ([markNotified]) and the
     * scheduler-success write ([markArmed]) may touch them, so a form save can never accidentally
     * mark a cycle as delivered (D-51/D-53).
     *
     * An update never rewrites `created_at_epoch_ms`: it is the time baseline of a rule whose type
     * has no service entry yet (D-49), so moving it on every edit would silently move the due date.
     *
     * Every mutation that can invalidate a notification is reported to the repository's change hook
     * after the transaction commits (see `ReminderRepositoryImpl`).
     */
    suspend fun save(rule: ReminderRule): Long

    /**
     * Turns a rule on or off without touching its intervals (D-43). A missing rule is a no-op.
     *
     * The SQL clears both markers, so re-enabling re-arms the rule and the next evaluation speaks
     * again. The affected rule id is reported to the change hook in both directions, because a
     * stale pending request or a stale delivered banner is possible either way.
     */
    suspend fun setEnabled(ruleId: Long, enabled: Boolean)

    /**
     * Claims the current due cycle for [ruleId] and reports **whether this call won it** (D-51).
     *
     * This is one conditional `UPDATE`; its affected-row count *is* the decision. `true` means the
     * caller may post a notification, `false` means the cycle was already claimed and the caller
     * must stay silent. Two evaluations of the same cycle therefore cannot both notify.
     *
     * It must never be replaced by a read-then-write: a read followed by a write is not atomic, and
     * the whole anti-feature this guards (re-nagging on every launch) comes back the moment it is.
     * It deliberately does not report to the change hook — the claim is bookkeeping, not a user
     * edit, and re-scheduling on every claim would be a wakeup loop.
     */
    suspend fun markNotified(ruleId: Long, cycleKey: String): Boolean

    /**
     * Records that the platform scheduler **accepted a request** for [cycleKey] (D-53 bookkeeping).
     *
     * It is not a claim and must never gate a post: a pre-scheduled iOS request fires with no app
     * code running, so the app cannot write the claim at delivery and needs this second fact to
     * tell "the OS already had this cycle" from "this cycle was never armed". A later sweep reads
     * it for that distinction only; a failed schedule leaves the cycle unarmed so the next sweep
     * retries. It returns nothing and never touches the claim marker.
     */
    suspend fun markArmed(ruleId: Long, cycleKey: String)

    /**
     * Deletes one rule. A missing rule is a no-op, not a crash — the Detail row can outlive a
     * deletion made from another surface.
     *
     * The deleted rule id is reported to the change hook so a delivered or pending notification for
     * it can be cancelled.
     */
    suspend fun delete(ruleId: Long)
}
