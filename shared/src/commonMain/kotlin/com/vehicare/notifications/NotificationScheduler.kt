package com.vehicare.notifications

import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.TypeBaseline
import com.vehicare.util.ServiceDates
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * What the OS has told this app about its ability to post a notification.
 *
 * iOS can report all three values (`UNAuthorizationStatusNotDetermined`/`Denied`/`Authorized` and
 * its provisional/ephemeral siblings). Android has no "never asked" state — `POST_NOTIFICATIONS`
 * is either granted or not, and the OS never remembers whether the app has asked before — so the
 * Android actual reports only [DENIED]/[GRANTED]. A consumer that needs the first-run distinction
 * (plan 08's `NotificationPermissionStore`) must combine this with its own record of having asked;
 * nothing here fakes a platform state that does not exist.
 */
enum class NotificationPermission { NOT_DETERMINED, DENIED, GRANTED }

/**
 * A fully resolved, pre-scheduled reminder: everything the OS needs to pre-arm a time-based
 * notification trigger, with no resource access required inside the platform implementation.
 *
 * [notificationDate] is the instant the OS should speak — the cycle's due date minus `lead_days`,
 * strictly in the future for an ordinary arming, and **today** only for an already-armed due-day
 * request that may still fire. [body] is the sentence that is true at that instant, so the
 * implementation never re-derives a state from [DueState].
 */
data class ScheduledReminder(
    val vehicleId: Long,
    val ruleId: Long,
    val title: String,
    val body: String,
    val notificationDate: LocalDate,
)

/**
 * An overdue (or due-now/due-soon) reminder ready for immediate delivery.
 *
 * [state] exists so a platform can decide on `DISTANCE`/`TIME` suspension or sound without
 * re-deriving anything from [body].
 */
data class DueReminder(
    val vehicleId: Long,
    val ruleId: Long,
    val title: String,
    val body: String,
    val state: DueState,
)

/**
 * The delivery seam: the one platform-specific contract both [ReminderDelivery] (the orchestrator)
 * and the UI call. This is deliberately an interface rather than an `expect class`: `commonTest`
 * implements it to fake permission state, scheduling and posting, which is how the permission
 * gate and the three time-path cases are asserted without a device.
 */
interface NotificationScheduler {

    /** The OS's current notification permission for this app. */
    suspend fun permissionState(): NotificationPermission

    /**
     * Declarative: clear this app's own pending identifiers, then re-add exactly [due].
     *
     * Android's actual is a documented **no-op** — its worker evaluates both axes daily (D-52), so
     * pre-scheduling here would double-deliver.
     */
    suspend fun scheduleTimeBased(due: List<ScheduledReminder>)

    /** Posts one notification per item in [due], visible immediately. */
    suspend fun triggerImmediateIfOverdue(due: List<DueReminder>)

    /**
     * Removes both the pending and the delivered notification for every rule id in [ruleIds].
     *
     * The repository hooks call this before re-evaluating, so a toggled-off, deleted or
     * vehicle-deleted rule cannot leave a stale banner behind (UI-SPEC interaction rules, D-64).
     * iOS matches its own identifiers by the `:r<ruleId>` segment (scheduled) and the
     * `:r<ruleId>:now` suffix (immediate), so no vehicle id is needed; Android cancels
     * `ruleId.toInt()`.
     */
    suspend fun cancel(ruleIds: List<Long>)

    /**
     * Fire-and-forget: triggers a fresh evaluate-and-deliver pass.
     *
     * Deliberately **not** `suspend`. The foreground sweep must not be a coroutine a composition
     * can cancel between the claim and the post — a cancelled job there would silence a rule for
     * its whole cycle (the mirror image of D-51). Android enqueues a unique one-shot worker; iOS
     * launches the sweep on its own app-lifetime scope. Callers never await delivery.
     */
    fun evaluateNow()

    /**
     * Opens the OS's own notification settings for this app.
     *
     * The UI-SPEC requires "denied → straight to the OS app settings", and only the platform can
     * do that; keeping it here avoids a second expect/actual.
     */
    suspend fun openNotificationSettings()
}

/**
 * Builds the platform's [NotificationScheduler]. [platformContext] carries the one platform value
 * the implementation needs — an Android `Context`, ignored on iOS — the same `Any?` contract the
 * database driver factory's own `expect fun` uses, for the same reason: `commonMain` cannot name a
 * platform type.
 */
expect fun createNotificationScheduler(platformContext: Any? = null): NotificationScheduler

/** The local hour a scheduled reminder fires at (used by the iOS actual). */
internal const val REMINDER_NOTIFICATION_HOUR = 9

/**
 * iOS keeps at most 64 pending notification requests; the orchestrator caps the scheduled list to
 * the 64 earliest notification instants instead of letting the OS silently drop the overflow
 * (RESEARCH PITFALLS §Notif 2, UI-SPEC Verification Note #8).
 */
internal const val REMINDER_PENDING_LIMIT = 64

/**
 * Whether [this] is a state worth announcing. `DueSoon`/`DueNow`/`Overdue` are — the rule has
 * entered its lead window, reached its boundary, or passed it. `Upcoming` is not: there is
 * nothing to announce yet. `NoMileageData` is not: there is no figure to state (D-49).
 */
internal fun DueState.isNotificationWindow(): Boolean = when (this) {
    is DueState.DueSoon -> true
    DueState.DueNow -> true
    is DueState.Overdue -> true
    is DueState.Upcoming -> false
    DueState.NoMileageData -> false
}

/**
 * The pure schedule arithmetic both delivery paths and `commonTest` rely on: what a cycle's due
 * date is, and when the OS should be told to speak about it. No clock read lives here — "today" is
 * always a parameter of the caller, never read from this object.
 */
object ReminderSchedule {

    /**
     * The time baseline [rule] measures its interval from (D-47/D-49): the newest same-vehicle
     * [TypeBaseline] whose `typeLabel` equals [ReminderRule.serviceType] case-insensitively,
     * otherwise [ReminderRule.createdAtEpochMs].
     *
     * This must stay identical to the baseline [com.vehicare.domain.ReminderStatus.evaluate]
     * applies — it is used only to compute an occurrence, never to decide a due state (which stays
     * `evaluateAll`'s monopoly).
     */
    fun timeBaselineOf(rule: ReminderRule, baselines: List<TypeBaseline>): LocalDate {
        val typeBaseline = baselines.firstOrNull {
            it.vehicleId == rule.vehicleId && it.typeLabel.lowercase() == rule.serviceType.lowercase()
        }
        return ServiceDates.fromStorage(typeBaseline?.baselineDateEpochMs ?: rule.createdAtEpochMs)
    }

    /**
     * This cycle's due date, or `null` when [rule] has no time interval or is disabled.
     *
     * The interval is the **effective** one (261024-u9g, D-1): [globalIntervalMonths] when the
     * app-level setting is set, else [ReminderRule.intervalMonths] — so a months-null rule gains a
     * time axis the moment the global value exists.
     *
     * **No rolling**: the result stays in the past after the occurrence passes, because that is
     * exactly the fact the orchestrator needs to close the cycle. This is the only function allowed
     * to answer "what is this cycle's due date?" (the boundary includes today).
     */
    fun currentOccurrence(
        rule: ReminderRule,
        baselines: List<TypeBaseline>,
        globalIntervalMonths: Long? = null,
    ): LocalDate? {
        // The interval is the effective one: the app-level global setting when set (261024-u9g,
        // D-1), else the rule's own — a months-null rule gains a time axis from the global value.
        val intervalMonths = globalIntervalMonths ?: rule.intervalMonths ?: return null
        if (!rule.enabled) return null
        require(intervalMonths > 0) {
            "intervalMonths must be > 0 (D-46): rule ${rule.id} has $intervalMonths."
        }
        return timeBaselineOf(rule, baselines).plus(intervalMonths.toInt(), DateTimeUnit.MONTH)
    }

    /**
     * The instant the OS should speak — [currentOccurrence] minus `lead_days` — or `null` when
     * [currentOccurrence] is `null`.
     *
     * This is the time path's ownership boundary, **not** the due date; D-50's lead window would be
     * dead on iOS if the trigger stayed on the due date. A null lead returns the due date itself
     * (D-50's "notify exactly at due"); a lead of 7 returns the day the in-app `DueSoon` band
     * opens. Never multiplies months by 30: `kotlinx.datetime`'s month arithmetic clamps 31 Jan + 1
     * month to 28 Feb, and day subtraction must not reintroduce that drift.
     */
    fun notificationDate(
        rule: ReminderRule,
        baselines: List<TypeBaseline>,
        globalIntervalMonths: Long? = null,
    ): LocalDate? {
        val due = currentOccurrence(rule, baselines, globalIntervalMonths) ?: return null
        return due.minus(rule.leadDays ?: 0L, DateTimeUnit.DAY)
    }
}
