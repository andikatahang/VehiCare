package com.vehicare.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.vehicare.db.VehiCareDatabase
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.VehicleStats
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * SQLDelight-backed [ReminderRepository] with every database access serialized onto one lane.
 *
 * Doing all I/O on `Dispatchers.IO.limitedParallelism(1)` is the documented guard against
 * concurrent `NativeSqliteDriver` access aborting the iOS process; the lane is injected rather
 * than referenced inline so it stays single and testable. Reads ride the lane too, not just writes
 * — the notification worker and the UI observe the same driver (D-63).
 *
 * The SQL that matters lives in `ReminderRule.sq`; this class never spells a column name. In
 * particular the once-per-cycle claim is one conditional `UPDATE` and the armed marker is its own
 * separate `UPDATE`, so neither rule can drift into Kotlin.
 *
 * @param onRulesChanged the seam for cancelling notifications that a user edit just invalidated.
 *   It receives `(vehicleId, cancelledRuleIds)`: the rules whose delivered or pending notification
 *   is now stale, so the platform scheduler can cancel them before re-evaluating. Plan 07 binds it
 *   in `AppModule` to `scheduler.cancel(ruleIds)` followed by `evaluateNow()`. It is invoked
 *   **outside** the write transaction, so it can never hold the write lock.
 */
internal class ReminderRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val onRulesChanged: suspend (Long, List<Long>) -> Unit = { _, _ -> },
) : ReminderRepository {

    override fun observeRules(vehicleId: Long): Flow<List<ReminderRule>> =
        database.reminderRuleQueries.selectForVehicle(vehicleId)
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeAllRules(): Flow<List<ReminderRule>> =
        database.reminderRuleQueries.selectAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeRule(ruleId: Long): Flow<ReminderRule?> =
        database.reminderRuleQueries.selectById(ruleId)
            .asFlow()
            .mapToOneOrNull(ioDispatcher)
            .map { row -> row?.toDomain() }

    override fun observeTypeBaselines(): Flow<List<TypeBaseline>> =
        database.serviceEntryQueries.selectTypeBaselines()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override fun observeVehicleStats(): Flow<List<VehicleStats>> =
        database.serviceEntryQueries.selectVehicleStats()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(rule: ReminderRule): Long {
        val (id, cancelledRuleIds) = withContext(ioDispatcher) {
            database.transactionWithResult {
                if (rule.id == 0L) {
                    database.reminderRuleQueries.insert(
                        vehicle_id = rule.vehicleId,
                        service_type = rule.serviceType,
                        interval_km = rule.intervalKm,
                        interval_months = rule.intervalMonths,
                        lead_km = rule.leadKm,
                        lead_days = rule.leadDays,
                        enabled = if (rule.enabled) 1L else 0L,
                        created_at_epoch_ms = rule.createdAtEpochMs,
                    )
                    // Same connection, same transaction: no separate connection can race the id.
                    // The id is fetched back via last_insert_rowid() — the minSdk 26-safe way.
                    database.reminderRuleQueries.lastInsertId().executeAsOne() to emptyList<Long>()
                } else {
                    // `created_at_epoch_ms` is deliberately absent: it is the time baseline of a
                    // rule whose type has no entry yet (D-49), and rewriting it on every edit would
                    // silently move the due date. Both markers are cleared by this same statement.
                    database.reminderRuleQueries.update(
                        service_type = rule.serviceType,
                        interval_km = rule.intervalKm,
                        interval_months = rule.intervalMonths,
                        lead_km = rule.leadKm,
                        lead_days = rule.leadDays,
                        enabled = if (rule.enabled) 1L else 0L,
                        id = rule.id,
                    )
                    rule.id to listOf(rule.id)
                }
            }
        }
        // After the commit: the hook may cancel notifications and re-evaluate, which reads the
        // database. Running it inside the transaction would hold the write lock for that work.
        onRulesChanged(rule.vehicleId, cancelledRuleIds)
        return id
    }

    override suspend fun setEnabled(ruleId: Long, enabled: Boolean) {
        val vehicleId = withContext(ioDispatcher) {
            // Qualified with `reminderRuleQueries.` on purpose: an unqualified `setEnabled(...)`
            // here would recurse into this very function.
            val row = database.reminderRuleQueries.selectById(ruleId).executeAsOneOrNull()
            if (row != null) {
                database.reminderRuleQueries.setEnabled(
                    enabled = if (enabled) 1L else 0L,
                    id = ruleId,
                )
            }
            row?.vehicle_id
        }
        // Both directions can leave a stale request or banner behind, so the id is reported either
        // way. A missing rule is a no-op, not a crash.
        if (vehicleId != null) onRulesChanged(vehicleId, listOf(ruleId))
    }

    /**
     * The whole of D-51, in one statement: a compare-and-set, never a read-then-write.
     *
     * `true` means this call won the cycle and may post; `false` means the cycle was already
     * claimed and this caller must stay silent. Two evaluations of the same cycle therefore cannot
     * both notify. The change hook is deliberately not called — the claim is bookkeeping, not a
     * user edit, and re-scheduling on every claim would be a wakeup loop.
     */
    override suspend fun markNotified(ruleId: Long, cycleKey: String): Boolean =
        withContext(ioDispatcher) {
            // One conditional UPDATE, compared on its affected-row count. Positional arguments in
            // `.sq` order: the value to write, the row id, then the value the WHERE compares.
            database.reminderRuleQueries.markNotified(cycleKey, ruleId, cycleKey).value == 1L
        }

    /**
     * Bookkeeping only (D-53): the delivery plan calls this **after** the platform scheduler
     * accepted a request for [cycleKey], so a later sweep can distinguish "the OS already had this
     * cycle" from "this cycle was never armed".
     *
     * It returns nothing, writes only the armed marker, is never called by the claim path and is
     * never read as a claim: a pre-scheduled iOS notification fires with no app code running, so
     * this fact cannot substitute for the delivery decision.
     */
    override suspend fun markArmed(ruleId: Long, cycleKey: String) {
        withContext(ioDispatcher) {
            // Its affected-row count is ignored: the caller already knows whether the scheduler
            // accepted the request.
            database.reminderRuleQueries.markArmed(cycleKey, ruleId, cycleKey)
        }
    }

    override suspend fun delete(ruleId: Long) {
        val vehicleId = withContext(ioDispatcher) {
            database.transactionWithResult {
                val id = database.reminderRuleQueries.selectById(ruleId)
                    .executeAsOneOrNull()
                    ?.vehicle_id
                database.reminderRuleQueries.delete(ruleId)
                id
            }
        }
        if (vehicleId != null) onRulesChanged(vehicleId, listOf(ruleId))
    }
}
