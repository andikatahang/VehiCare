package com.vehicare.notifications

import com.vehicare.data.ReminderRepository
import com.vehicare.data.ReminderRepositoryImpl
import com.vehicare.data.VehicleRepository
import com.vehicare.data.VehicleRepositoryImpl
import com.vehicare.data.db.DatabaseProvider
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderAxis
import com.vehicare.domain.ReminderOrdering
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.ReminderStatus
import com.vehicare.domain.TypeBaseline
import com.vehicare.util.ReminderCopy
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * The one orchestrator both platforms and the worker call: evaluate every rule, decide which
 * cycle belongs to which delivery path, and deliver at most once per cycle (D-51/D-53).
 *
 * **Claim-then-post, never post-then-claim — with two time-path exceptions that are both claims
 * about what the OS already did.** [reminderRepository]'s `markNotified` is reached only through
 * [claimCycle], the single claim site shared by all three delivery cases: the immediate path
 * claims before building a [DueReminder] and posts only on `true`; the time path cannot claim at
 * delivery (iOS fires a pre-scheduled request with no app code running), so it claims on the
 * orchestrator's behalf in exactly two cases: an armed cycle whose notification instant is
 * strictly past (claim without posting — the OS already had its chance) and a never-armed cycle
 * whose window is already open (claim **then** post, because a claim-only would silence the
 * entire cycle).
 *
 * **Permission gates the claim, not just the post.** [evaluateAndDeliver] reads
 * [NotificationScheduler.permissionState] before anything else and returns on anything other than
 * `GRANTED`, leaving both markers untouched — a claim without a possible post would silence the
 * whole cycle.
 *
 * Two persisted facts make the claim honest on both sides of the OS boundary:
 * `notified_cycle_key` (the claim) and `armedCycleKey` (the proof an OS request existed for that
 * cycle). A row whose `armedCycleKey` does not equal its current `cycleKey` was never armed in
 * this cycle, which is exactly the never-armed case above.
 */
internal class ReminderDelivery(
    private val vehicleRepository: VehicleRepository,
    private val reminderRepository: ReminderRepository,
    private val scheduler: NotificationScheduler,
    private val today: () -> LocalDate = ServiceDates::today,
    private val immediateAxes: Set<ReminderAxis> = setOf(ReminderAxis.DISTANCE, ReminderAxis.TIME),
) {

    suspend fun evaluateAndDeliver() {
        if (scheduler.permissionState() != NotificationPermission.GRANTED) return

        val vehicles = vehicleRepository.observeVehicles().first()
        val rules = reminderRepository.observeAllRules().first()
        val baselines = reminderRepository.observeTypeBaselines().first()
        val stats = reminderRepository.observeVehicleStats().first()
        val vehiclesById = vehicles.associateBy { it.id }

        val today = today()
        val rows = ReminderStatus.evaluateAll(rules, baselines, stats, today)

        val timePathOwned = timePathOwnedRuleIds(rows, baselines, today, immediateAxes)
        val candidateRows = ReminderOrdering.sortRules(
            immediateCandidates(rows, immediateAxes, timePathOwned),
        )
        val candidateIds = candidateRows.map { it.rule.id }.toSet()
        val missedTime = mutableListOf<ReminderRow>()

        // Skip the whole schedule step when the immediate path already owns the time axis
        // (Android, D-52): the code must never claim without posting there, and `scheduleTimeBased`
        // is a documented Android no-op.
        if (ReminderAxis.TIME !in immediateAxes) {
            val scheduled = mutableListOf<ScheduledReminder>()
            val scheduledCycleKeys = mutableMapOf<Long, String>()

            val timeEligible = ReminderOrdering.sortRules(rows).filter { row ->
                row.evaluation != null && row.rule.intervalMonths != null
            }

            for (row in timeEligible) {
                val rule = row.rule
                val evaluation = checkNotNull(row.evaluation)
                // The immediate path will claim this cycle; its earlier-armed trigger must be
                // cleared, which the declarative `scheduleTimeBased` list does by omission.
                if (rule.id in candidateIds) continue
                // Already claimed earlier in this cycle.
                if (rule.notifiedCycleKey == evaluation.cycleKey) continue
                val vehicle = vehiclesById[rule.vehicleId] ?: continue

                val due = ReminderSchedule.currentOccurrence(rule, baselines) ?: continue
                val notifyAt = ReminderSchedule.notificationDate(rule, baselines) ?: continue
                val armed = rule.armedCycleKey == evaluation.cycleKey

                when {
                    // Future instant: the sentence that will be true when the request fires.
                    notifyAt > today -> {
                        scheduled += scheduledReminder(rule, vehicle.name, notifyAt, due)
                        scheduledCycleKeys[rule.id] = evaluation.cycleKey
                    }
                    // Armed and strictly past: the OS had its chance, so the marker only records
                    // the delivered cycle and stops future sweeps from re-arming it.
                    armed && notifyAt < today -> claimCycle(rule.id, evaluation.cycleKey)
                    // Armed and due today: the 09:00 trigger may still fire; retain the request
                    // unclaimed and excluded from the immediate path by `timePathOwned`.
                    armed && notifyAt == today -> {
                        scheduled += scheduledReminder(rule, vehicle.name, today, due)
                        scheduledCycleKeys[rule.id] = evaluation.cycleKey
                    }
                    // Never armed (notifyAt <= today, armedCycleKey != cycleKey): a claim-only
                    // would silence the whole cycle, so it is delivered immediately instead.
                    else -> missedTime += row
                }
            }

            val capped = capScheduled(scheduled)
            runCatching { scheduler.scheduleTimeBased(capped) }
                .onSuccess {
                    capped.forEach { item ->
                        scheduledCycleKeys[item.ruleId]?.let { key ->
                            reminderRepository.markArmed(item.ruleId, key)
                        }
                    }
                }
        }

        // Disjoint by construction: `candidateRows` excludes every id in `timePathOwned`, and
        // `missedTime` is a subset of it.
        val immediateRows = candidateRows + missedTime
        withContext(NonCancellable) {
            val toPost = mutableListOf<DueReminder>()
            for (row in immediateRows) {
                val rule = row.rule
                val evaluation = checkNotNull(row.evaluation)
                val claimed = claimCycle(rule.id, evaluation.cycleKey)
                if (!claimed) continue
                val vehicle = vehiclesById[rule.vehicleId] ?: continue
                toPost += DueReminder(
                    vehicleId = rule.vehicleId,
                    ruleId = rule.id,
                    title = ReminderCopy.notificationTitle(vehicle.name, rule.serviceType),
                    body = ReminderCopy.dueSentence(evaluation.state),
                    state = evaluation.state,
                )
            }
            if (toPost.isNotEmpty()) scheduler.triggerImmediateIfOverdue(toPost)
        }
    }

    /**
     * The sentence that will be true when the OS speaks at [notifyAt]: "Due now" on the due day,
     * "Due soon — N days left" inside D-50's lead window — never a sentence true only today.
     */
    private suspend fun scheduledReminder(
        rule: ReminderRule,
        vehicleName: String,
        notifyAt: LocalDate,
        due: LocalDate,
    ): ScheduledReminder {
        val daysUntilDue = notifyAt.daysUntil(due).toLong()
        val state = if (daysUntilDue == 0L) {
            DueState.DueNow
        } else {
            DueState.DueSoon(kmLeft = null, daysLeft = daysUntilDue)
        }
        return ScheduledReminder(
            vehicleId = rule.vehicleId,
            ruleId = rule.id,
            title = ReminderCopy.notificationTitle(vehicleName, rule.serviceType),
            body = ReminderCopy.dueSentence(state),
            notificationDate = notifyAt,
        )
    }

    /**
     * The **single** `markNotified` call site, shared by the immediate path, the never-armed time
     * path and the armed-past time path (D-51). `true` means this call owns the cycle, `false`
     * means another evaluation already notified it — only the first two post after a `true`; the
     * armed-past case deliberately does not.
     */
    private suspend fun claimCycle(ruleId: Long, cycleKey: String): Boolean =
        reminderRepository.markNotified(ruleId, cycleKey)
}

/**
 * Keeps a row when it is enabled, evaluated, inside its notification window
 * ([isNotificationWindow]), decided by an axis in [axes], and not owned by the time path.
 *
 * The test is the **deciding axis** ([com.vehicare.domain.ReminderEvaluation.axis]), never the
 * presence of an interval — a dual-axis rule decided by the time axis must not be posted by the
 * mileage sweep on iOS. [axes] expresses D-52 (Android: both) and D-53 (iOS: mileage on
 * foreground, time in the OS scheduler) without a platform branch; [timePathOwned] is empty on
 * Android.
 */
internal fun immediateCandidates(
    rows: List<ReminderRow>,
    axes: Set<ReminderAxis>,
    timePathOwned: Set<Long> = emptySet(),
): List<ReminderRow> = rows.filter { row ->
    val evaluation = row.evaluation
    row.rule.enabled &&
        evaluation != null &&
        evaluation.state.isNotificationWindow() &&
        evaluation.axis in axes &&
        row.rule.id !in timePathOwned
}

/**
 * The ids the time path owns in this cycle: empty when `TIME` is in [immediateAxes] (Android's
 * worker delivers the time axis there), otherwise the ids of enabled rows with a time interval
 * whose [ReminderSchedule.notificationDate] is today or past (inclusive — the notification
 * boundary belongs to the OS request, not to the sweep).
 *
 * The boundary is the **notification instant**, not the due date, so D-50's lead window is owned
 * by the OS from the day it opens; the OS cannot write `notified_cycle_key` when a pre-scheduled
 * request fires, so this set is how the app discovers that the time path owns the cycle.
 */
internal fun timePathOwnedRuleIds(
    rows: List<ReminderRow>,
    baselines: List<TypeBaseline>,
    today: LocalDate,
    immediateAxes: Set<ReminderAxis>,
): Set<Long> {
    if (ReminderAxis.TIME in immediateAxes) return emptySet()
    return rows
        .filter { row -> row.rule.enabled && row.rule.intervalMonths != null }
        .mapNotNull { row ->
            val notifyAt = ReminderSchedule.notificationDate(row.rule, baselines) ?: return@mapNotNull null
            row.rule.id.takeIf { notifyAt <= today }
        }
        .toSet()
}

/**
 * iOS keeps at most [REMINDER_PENDING_LIMIT] pending requests and silently drops the overflow;
 * taking the earliest instants is deterministic, and the dropped rules are left unclaimed and
 * unarmed so a later sweep can arm them when a slot frees.
 */
internal fun capScheduled(reminders: List<ScheduledReminder>): List<ScheduledReminder> =
    reminders
        .sortedWith(compareBy({ it.notificationDate }, { it.ruleId }))
        .take(REMINDER_PENDING_LIMIT)

/**
 * The only delivery construction point outside Koin: both delivery paths (the Android worker, the
 * iOS sweep) build through here, so the two paths cannot diverge.
 *
 * Builds no driver — [DatabaseProvider.require] is the only database access, because a second
 * [com.vehicare.db.VehiCareDatabase] would split change notification from the UI's queries
 * (03-RESEARCH Pattern 1 / Pitfall 1). The `onRulesChanged` hook is deliberately empty: the Koin
 * graph's repository cancels and re-arms on user edits, while the delivery-time repository must
 * not re-trigger the very sweep that is running (a wakeup loop).
 *
 * [immediateAxes] defaults to both axes because Android evaluates both rule kinds in its worker
 * (D-52); the iOS sweep passes `setOf(ReminderAxis.DISTANCE)` explicitly because time-based rules
 * ride the OS scheduler there (D-53).
 */
internal object ReminderDeliveryFactory {

    fun create(
        scheduler: NotificationScheduler,
        immediateAxes: Set<ReminderAxis> = setOf(ReminderAxis.DISTANCE, ReminderAxis.TIME),
    ): ReminderDelivery {
        val database = DatabaseProvider.require()
        val vehicleRepository = VehicleRepositoryImpl(database)
        val reminderRepository = ReminderRepositoryImpl(database, onRulesChanged = { _, _ -> })
        return ReminderDelivery(
            vehicleRepository = vehicleRepository,
            reminderRepository = reminderRepository,
            scheduler = scheduler,
            immediateAxes = immediateAxes,
        )
    }
}
