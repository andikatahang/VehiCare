package com.vehicare.notifications

import com.vehicare.data.ReminderRepository
import com.vehicare.data.VehicleRepository
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderAxis
import com.vehicare.domain.ReminderEvaluation
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.ReminderRule
import com.vehicare.domain.ReminderStatus
import com.vehicare.domain.TypeBaseline
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleStats
import com.vehicare.util.ServiceDates
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Ignore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [immediateCandidates]/[timePathOwnedRuleIds]/[capScheduled]'s partition contract (D-51/D-53) and
 * [ReminderDelivery]'s claim-then-post orchestration: the permission gate, the three time-path
 * cases keyed by `armedCycleKey`, and the never-both-paths invariant for a dual-axis rule.
 */
class ReminderDeliveryTest {

    // ---- fixtures -------------------------------------------------------------------------

    private fun rule(
        id: Long = 1L,
        vehicleId: Long = 1L,
        serviceType: String = "Brake service",
        intervalKm: Long? = null,
        intervalMonths: Long? = 1L,
        leadKm: Long? = null,
        leadDays: Long? = null,
        enabled: Boolean = true,
        createdAt: LocalDate = LocalDate(2026, 1, 1),
        notifiedCycleKey: String? = null,
        armedCycleKey: String? = null,
    ): ReminderRule = ReminderRule(
        id = id,
        vehicleId = vehicleId,
        serviceType = serviceType,
        intervalKm = intervalKm,
        intervalMonths = intervalMonths,
        leadKm = leadKm,
        leadDays = leadDays,
        enabled = enabled,
        createdAtEpochMs = ServiceDates.toStorage(createdAt),
        notifiedCycleKey = notifiedCycleKey,
        armedCycleKey = armedCycleKey,
    )

    private fun timeCycleKeyOf(rule: ReminderRule): String =
        ReminderStatus.cycleKey(rule, kmBaseline = null, dateBaselineEpochMs = rule.createdAtEpochMs)

    private fun row(
        rule: ReminderRule,
        state: DueState,
        axis: ReminderAxis?,
        cycleKey: String = "key${rule.id}",
    ): ReminderRow = ReminderRow(
        rule = rule,
        evaluation = ReminderEvaluation(state = state, axis = axis, urgency = 1.0, cycleKey = cycleKey),
    )

    private fun vehicle(id: Long = 1L, name: String = "Toyota Corolla"): Vehicle =
        Vehicle(id = id, name = name)

    // ---- immediateCandidates ------------------------------------------------------------------

    @Test
    fun immediateCandidatesKeepsNotificationWindowStatesAndDropsOthers() {
        val axes = setOf(ReminderAxis.DISTANCE, ReminderAxis.TIME)
        val upcoming = row(rule(id = 1L), DueState.Upcoming(kmLeft = 500L, dueDate = null), ReminderAxis.DISTANCE)
        val dueSoon = row(rule(id = 2L), DueState.DueSoon(kmLeft = 100L, daysLeft = null), ReminderAxis.DISTANCE)
        val dueNow = row(rule(id = 3L), DueState.DueNow, ReminderAxis.DISTANCE)
        val overdue = row(rule(id = 4L), DueState.Overdue(kmOver = 50L, daysOver = null), ReminderAxis.DISTANCE)
        val noMileage = row(rule(id = 5L), DueState.NoMileageData, null)

        val kept = immediateCandidates(listOf(upcoming, dueSoon, dueNow, overdue, noMileage), axes)

        assertEquals(setOf(2L, 3L, 4L), kept.map { it.rule.id }.toSet())
    }

    @Test
    fun aDualAxisRowDecidedByTheTimeAxisIsDroppedFromADistanceOnlyImmediatePath() {
        val dualAxisTimeDecided = row(
            rule(id = 1L, intervalKm = 1_000L, intervalMonths = 1L),
            DueState.Overdue(kmOver = null, daysOver = 5L),
            ReminderAxis.TIME,
        )
        val timeOnly = row(
            rule(id = 2L, intervalKm = null, intervalMonths = 1L),
            DueState.DueNow,
            ReminderAxis.TIME,
        )

        val kept = immediateCandidates(listOf(dualAxisTimeDecided, timeOnly), axes = setOf(ReminderAxis.DISTANCE))

        assertTrue(kept.isEmpty(), "a time-decided row must not be posted by the mileage-only path")
    }

    @Test
    fun aDualAxisRowDecidedByTheDistanceAxisIsKeptOnADistanceOnlyImmediatePath() {
        val dualAxisDistanceDecided = row(
            rule(id = 1L, intervalKm = 1_000L, intervalMonths = 1L),
            DueState.Overdue(kmOver = 200L, daysOver = null),
            ReminderAxis.DISTANCE,
        )

        val kept = immediateCandidates(listOf(dualAxisDistanceDecided), axes = setOf(ReminderAxis.DISTANCE))

        assertEquals(listOf(1L), kept.map { it.rule.id })
    }

    @Test
    fun theMirrorCaseHoldsForATimeOnlyImmediatePath() {
        val distanceDecided = row(
            rule(id = 1L, intervalKm = 1_000L, intervalMonths = 1L),
            DueState.Overdue(kmOver = 200L, daysOver = null),
            ReminderAxis.DISTANCE,
        )
        val timeDecided = row(
            rule(id = 2L, intervalKm = 1_000L, intervalMonths = 1L),
            DueState.Overdue(kmOver = null, daysOver = 5L),
            ReminderAxis.TIME,
        )

        val kept = immediateCandidates(listOf(distanceDecided, timeDecided), axes = setOf(ReminderAxis.TIME))

        assertEquals(listOf(2L), kept.map { it.rule.id })
    }

    @Test
    fun timePathOwnedExcludesARowFromImmediateCandidates() {
        val timeOwned = row(rule(id = 1L), DueState.DueNow, ReminderAxis.TIME)

        val kept = immediateCandidates(
            rows = listOf(timeOwned),
            axes = setOf(ReminderAxis.DISTANCE, ReminderAxis.TIME),
            timePathOwned = setOf(1L),
        )

        assertTrue(kept.isEmpty())
    }

    @Test
    fun aDisabledRowAndARowWithNoEvaluationAreDropped() {
        val disabled = ReminderRow(
            rule = rule(id = 1L, enabled = false),
            evaluation = null,
        )
        val noEvaluation = ReminderRow(rule = rule(id = 2L), evaluation = null)

        val kept = immediateCandidates(
            listOf(disabled, noEvaluation),
            axes = setOf(ReminderAxis.DISTANCE, ReminderAxis.TIME),
        )

        assertTrue(kept.isEmpty())
    }

    // ---- timePathOwnedRuleIds -----------------------------------------------------------------

    @Test
    fun timePathOwnedRuleIdsIsEmptyWhenBothAxesAreImmediate() {
        val today = LocalDate(2026, 6, 1)
        val row = row(rule(id = 1L, createdAt = LocalDate(2026, 1, 1)), DueState.DueNow, ReminderAxis.TIME)

        val owned = timePathOwnedRuleIds(
            rows = listOf(row),
            baselines = emptyList(),
            today = today,
            immediateAxes = setOf(ReminderAxis.DISTANCE, ReminderAxis.TIME),
        )

        assertTrue(owned.isEmpty())
    }

    @Test
    fun timePathOwnedRuleIdsIncludesTodayOrPastNotificationDatesAndExcludesTomorrow() {
        // due = 2026-02-01 (createdAt + 1 month), no lead -> notificationDate == due.
        val todayOrPast = rule(id = 1L, createdAt = LocalDate(2026, 1, 1))
        // due = 2026-02-02 -> notificationDate is one day after `today`.
        val tomorrow = rule(id = 2L, createdAt = LocalDate(2026, 1, 2))
        val today = LocalDate(2026, 2, 1)

        val owned = timePathOwnedRuleIds(
            rows = listOf(
                row(todayOrPast, DueState.DueNow, ReminderAxis.TIME),
                row(tomorrow, DueState.Upcoming(kmLeft = null, dueDate = null), ReminderAxis.TIME),
            ),
            baselines = emptyList(),
            today = today,
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        )

        assertEquals(setOf(1L), owned)
    }

    @Test
    fun timePathOwnedRuleIdsIncludesALeadWindowThatOpensTodayEvenThoughTheDueDateIsFuture() {
        // due = 2026-02-08 (createdAt + 1 month), leadDays = 7 -> notificationDate = 2026-02-01.
        val leadRule = rule(id = 1L, createdAt = LocalDate(2026, 1, 8), leadDays = 7L)
        val today = LocalDate(2026, 2, 1)

        val owned = timePathOwnedRuleIds(
            rows = listOf(row(leadRule, DueState.Upcoming(kmLeft = null, dueDate = null), ReminderAxis.TIME)),
            baselines = emptyList(),
            today = today,
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        )

        assertEquals(setOf(1L), owned)
    }

    @Test
    fun timePathOwnedRuleIdsExcludesDisabledRulesAndRulesWithNoTimeInterval() {
        val disabled = rule(id = 1L, enabled = false, createdAt = LocalDate(2026, 1, 1))
        val noTimeInterval = rule(id = 2L, intervalKm = 1_000L, intervalMonths = null)
        val today = LocalDate(2026, 6, 1)

        val owned = timePathOwnedRuleIds(
            rows = listOf(
                ReminderRow(rule = disabled, evaluation = null),
                row(noTimeInterval, DueState.Overdue(kmOver = 10L, daysOver = null), ReminderAxis.DISTANCE),
            ),
            baselines = emptyList(),
            today = today,
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        )

        assertTrue(owned.isEmpty())
    }

    // ---- capScheduled -------------------------------------------------------------------------

    @Test
    fun capScheduledSortsByNotificationDateThenRuleIdAndTakesTheSixtyFourEarliest() {
        val base = LocalDate(2026, 1, 1)
        val reminders = (1..70).map { i ->
            ScheduledReminder(
                vehicleId = 1L,
                ruleId = (71 - i).toLong(),
                title = "t",
                body = "b",
                notificationDate = base.plus(DatePeriod(days = i)),
            )
        }

        val capped = capScheduled(reminders)

        assertEquals(REMINDER_PENDING_LIMIT, capped.size)
        assertEquals(reminders.sortedBy { it.notificationDate }.take(REMINDER_PENDING_LIMIT).map { it.ruleId }, capped.map { it.ruleId })
    }

    // ---- the cross-path partition invariant (D-51) --------------------------------------------

    @Test
    fun theImmediatePathAndTheTimePathShareNoRuleId() {
        val today = LocalDate(2026, 2, 1)
        val distanceDue = rule(id = 1L, intervalKm = 1_000L, intervalMonths = null)
        val timeDue = rule(id = 2L, intervalKm = null, intervalMonths = 1L, createdAt = LocalDate(2026, 1, 1))
        val timeOnlyFuture = rule(id = 3L, intervalKm = null, intervalMonths = 1L, createdAt = LocalDate(2026, 6, 1))
        val distanceOnly = rule(id = 4L, intervalKm = 500L, intervalMonths = null)

        val rows = listOf(
            row(distanceDue, DueState.Overdue(kmOver = 10L, daysOver = null), ReminderAxis.DISTANCE),
            row(timeDue, DueState.DueNow, ReminderAxis.TIME),
            row(timeOnlyFuture, DueState.Upcoming(kmLeft = null, dueDate = null), ReminderAxis.TIME),
            row(distanceOnly, DueState.DueSoon(kmLeft = 50L, daysLeft = null), ReminderAxis.DISTANCE),
        )
        val axes = setOf(ReminderAxis.DISTANCE)

        val timePathOwned = timePathOwnedRuleIds(rows, baselines = emptyList(), today = today, immediateAxes = axes)
        val immediateIds = immediateCandidates(rows, axes, timePathOwned).map { it.rule.id }.toSet()

        assertTrue(immediateIds.intersect(timePathOwned).isEmpty())
        assertEquals(setOf(1L, 4L), immediateIds)
        assertEquals(setOf(2L), timePathOwned)
    }

    // ---- isNotificationWindow ------------------------------------------------------------------

    @Test
    fun isNotificationWindowIsExhaustiveOverAllFiveStates() {
        assertFalse(DueState.Upcoming(kmLeft = null, dueDate = null).isNotificationWindow())
        assertTrue(DueState.DueSoon(kmLeft = null, daysLeft = null).isNotificationWindow())
        assertTrue(DueState.DueNow.isNotificationWindow())
        assertTrue(DueState.Overdue(kmOver = null, daysOver = null).isNotificationWindow())
        assertFalse(DueState.NoMileageData.isNotificationWindow())
    }

    // ---- the orchestrator -----------------------------------------------------------------

    @Test
    fun aDeniedPermissionReturnsBeforeAnyClaimOrSchedule() = runTest {
        val overdueRule = rule(id = 1L, createdAt = LocalDate(2024, 1, 1))
        val reminderRepository = FakeReminderRepository(MutableStateFlow(listOf(overdueRule)))
        val vehicleRepository = FakeVehicleRepository(listOf(vehicle()))
        val scheduler = FakeNotificationScheduler(permission = NotificationPermission.DENIED)

        ReminderDelivery(
            vehicleRepository,
            reminderRepository,
            scheduler,
            today = { LocalDate(2026, 6, 1) },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()

        assertTrue(reminderRepository.markNotifiedCalls.isEmpty())
        assertTrue(reminderRepository.markArmedCalls.isEmpty())
        assertTrue(scheduler.scheduledCalls.isEmpty())
        assertTrue(scheduler.triggeredCalls.isEmpty())
    }

    @Test
    fun anArmedCycleStrictlyPastItsNotificationInstantIsClaimedWithoutPosting() = runTest {
        val baseRule = rule(id = 1L, createdAt = LocalDate(2026, 1, 1))
        val cycleKey = timeCycleKeyOf(baseRule)
        val armedRule = baseRule.copy(armedCycleKey = cycleKey)
        val reminderRepository = FakeReminderRepository(MutableStateFlow(listOf(armedRule)))
        val vehicleRepository = FakeVehicleRepository(listOf(vehicle()))
        val scheduler = FakeNotificationScheduler()

        ReminderDelivery(
            vehicleRepository,
            reminderRepository,
            scheduler,
            today = { LocalDate(2026, 3, 1) },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()

        assertEquals(listOf(1L to cycleKey), reminderRepository.markNotifiedCalls)
        assertTrue(scheduler.triggeredCalls.isEmpty())
    }

    /**
     * **Environment-blocked, not logic-blocked** (deferred item, Task 1): this scenario calls
     * [ReminderCopy.notificationTitle]/[ReminderCopy.dueSentence] through the real
     * `evaluateAndDeliver` path, and Compose Resources' `getString` resolves the system locale via
     * `android.content.res.Resources.getSystem()` — a real Android framework call that the plain
     * JVM `android.jar` stub throws `RuntimeException("not mocked")` for outside Robolectric. No
     * Robolectric dependency exists in this project yet, and adding one is a new build dependency
     * outside this plan's audited package set (RESEARCH's Package Legitimacy Audit covers only
     * `androidx.work:work-runtime`), so it is deliberately not added here without sign-off. The
     * same scenario is proven by [ReminderScheduleTest] and by the pure-function tests above
     * (`immediateCandidates`/`timePathOwnedRuleIds`/`capScheduled`), and is expected to pass on a
     * real resource environment (`iosSimulatorArm64Test`, or an instrumented `androidDeviceTest`).
     */
    @Ignore
    @Test
    fun theSameCycleWithNoArmedMarkerIsClaimedAndPosted() = runTest {
        val neverArmedRule = rule(id = 1L, createdAt = LocalDate(2026, 1, 1))
        val cycleKey = timeCycleKeyOf(neverArmedRule)
        val reminderRepository = FakeReminderRepository(MutableStateFlow(listOf(neverArmedRule)))
        val vehicleRepository = FakeVehicleRepository(listOf(vehicle()))
        val scheduler = FakeNotificationScheduler()

        ReminderDelivery(
            vehicleRepository,
            reminderRepository,
            scheduler,
            today = { LocalDate(2026, 3, 1) },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()

        assertEquals(listOf(1L to cycleKey), reminderRepository.markNotifiedCalls)
        assertEquals(1, scheduler.triggeredCalls.size)
        val posted = scheduler.triggeredCalls.single().single()
        assertEquals("Toyota Corolla · Brake service", posted.title)
        assertEquals("Overdue by 28 days", posted.body)
    }

    /** Environment-blocked, not logic-blocked — see the KDoc above [theSameCycleWithNoArmedMarkerIsClaimedAndPosted]. */
    @Ignore
    @Test
    fun aDueTodayArmedCycleIsScheduledWithTodayAndNeverClaimed() = runTest {
        val baseRule = rule(id = 1L, createdAt = LocalDate(2026, 1, 1))
        val cycleKey = timeCycleKeyOf(baseRule)
        val armedRule = baseRule.copy(armedCycleKey = cycleKey)
        val reminderRepository = FakeReminderRepository(MutableStateFlow(listOf(armedRule)))
        val vehicleRepository = FakeVehicleRepository(listOf(vehicle()))
        val scheduler = FakeNotificationScheduler()
        val today = LocalDate(2026, 2, 1)

        ReminderDelivery(
            vehicleRepository,
            reminderRepository,
            scheduler,
            today = { today },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()

        assertTrue(reminderRepository.markNotifiedCalls.isEmpty())
        val scheduled = scheduler.scheduledCalls.single().single()
        assertEquals(today, scheduled.notificationDate)
        assertEquals("Due now", scheduled.body)
    }

    /** Environment-blocked, not logic-blocked — see the KDoc above [theSameCycleWithNoArmedMarkerIsClaimedAndPosted]. */
    @Ignore
    @Test
    fun aFutureCycleIsScheduledAndMarkedArmedOnlyAfterScheduleSucceeds() = runTest {
        val futureRule = rule(id = 1L, createdAt = LocalDate(2026, 1, 1))
        val cycleKey = timeCycleKeyOf(futureRule)
        val reminderRepository = FakeReminderRepository(MutableStateFlow(listOf(futureRule)))
        val vehicleRepository = FakeVehicleRepository(listOf(vehicle()))
        val scheduler = FakeNotificationScheduler()

        ReminderDelivery(
            vehicleRepository,
            reminderRepository,
            scheduler,
            today = { LocalDate(2026, 1, 10) },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()

        assertEquals(1, scheduler.scheduledCalls.size)
        assertEquals(listOf(1L to cycleKey), reminderRepository.markArmedCalls)

        // When the scheduler fails, the cycle must stay unarmed so a later sweep retries.
        val failingScheduler = FakeNotificationScheduler(scheduleShouldFail = true)
        val retryRepository = FakeReminderRepository(MutableStateFlow(listOf(futureRule)))
        ReminderDelivery(
            vehicleRepository,
            retryRepository,
            failingScheduler,
            today = { LocalDate(2026, 1, 10) },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()
        assertTrue(retryRepository.markArmedCalls.isEmpty())
    }

    /** Environment-blocked, not logic-blocked — see the KDoc above [theSameCycleWithNoArmedMarkerIsClaimedAndPosted]. */
    @Ignore
    @Test
    fun aLeadSevenRuleSchedulesOnDueMinusSevenWithADueSoonBody() = runTest {
        // due = 2026-02-08 (createdAt + 1 month), leadDays = 7 -> notificationDate = 2026-02-01.
        val leadRule = rule(id = 1L, createdAt = LocalDate(2026, 1, 8), leadDays = 7L)
        val reminderRepository = FakeReminderRepository(MutableStateFlow(listOf(leadRule)))
        val vehicleRepository = FakeVehicleRepository(listOf(vehicle()))
        val scheduler = FakeNotificationScheduler()

        ReminderDelivery(
            vehicleRepository,
            reminderRepository,
            scheduler,
            today = { LocalDate(2026, 1, 1) },
            immediateAxes = setOf(ReminderAxis.DISTANCE),
        ).evaluateAndDeliver()

        val scheduled = scheduler.scheduledCalls.single().single()
        assertEquals(LocalDate(2026, 2, 1), scheduled.notificationDate)
        assertEquals("Due soon — 7 days left", scheduled.body)
    }
}

// ---- fakes ------------------------------------------------------------------------------------

/** A hand-written [VehicleRepository] whose observation flow is a constructor argument. */
private class FakeVehicleRepository(
    private val vehicles: List<Vehicle>,
) : VehicleRepository {
    override fun observeVehicles(): Flow<List<Vehicle>> = flowOf(vehicles)
    override fun observeVehicle(id: Long): Flow<Vehicle?> = flowOf(vehicles.firstOrNull { it.id == id })
    override suspend fun save(vehicle: Vehicle): Long = error("the sweep never saves a vehicle")
    override suspend fun delete(id: Long) = error("the sweep never deletes a vehicle")
}

/**
 * A hand-written [ReminderRepository] that actually applies [markNotified]/[markArmed] to its
 * backing rule list, so the orchestrator's compare-and-set semantics are exercised rather than
 * mocked, and records every call for assertion.
 */
private class FakeReminderRepository(
    private val rulesFlow: MutableStateFlow<List<ReminderRule>>,
    private val baselines: List<TypeBaseline> = emptyList(),
    private val stats: List<VehicleStats> = emptyList(),
) : ReminderRepository {

    val markNotifiedCalls = mutableListOf<Pair<Long, String>>()
    val markArmedCalls = mutableListOf<Pair<Long, String>>()

    override fun observeRules(vehicleId: Long): Flow<List<ReminderRule>> =
        flowOf(rulesFlow.value.filter { it.vehicleId == vehicleId })

    override fun observeAllRules(): Flow<List<ReminderRule>> = flowOf(rulesFlow.value)

    override fun observeRule(ruleId: Long): Flow<ReminderRule?> =
        flowOf(rulesFlow.value.firstOrNull { it.id == ruleId })

    override fun observeTypeBaselines(): Flow<List<TypeBaseline>> = flowOf(baselines)

    override fun observeVehicleStats(): Flow<List<VehicleStats>> = flowOf(stats)

    override suspend fun save(rule: ReminderRule): Long = error("the sweep never saves a rule")

    override suspend fun setEnabled(ruleId: Long, enabled: Boolean) =
        error("the sweep never toggles a rule")

    override suspend fun markNotified(ruleId: Long, cycleKey: String): Boolean {
        markNotifiedCalls += ruleId to cycleKey
        val current = rulesFlow.value.first { it.id == ruleId }
        if (current.notifiedCycleKey == cycleKey) return false
        rulesFlow.value = rulesFlow.value.map {
            if (it.id == ruleId) it.copy(notifiedCycleKey = cycleKey) else it
        }
        return true
    }

    override suspend fun markArmed(ruleId: Long, cycleKey: String) {
        markArmedCalls += ruleId to cycleKey
        rulesFlow.value = rulesFlow.value.map {
            if (it.id == ruleId) it.copy(armedCycleKey = cycleKey) else it
        }
    }

    override suspend fun delete(ruleId: Long) = error("the sweep never deletes a rule")
}

/** A hand-written [NotificationScheduler] that records every call for assertion. */
private class FakeNotificationScheduler(
    private val permission: NotificationPermission = NotificationPermission.GRANTED,
    private val scheduleShouldFail: Boolean = false,
) : NotificationScheduler {

    val scheduledCalls = mutableListOf<List<ScheduledReminder>>()
    val triggeredCalls = mutableListOf<List<DueReminder>>()
    val cancelCalls = mutableListOf<List<Long>>()

    override suspend fun permissionState(): NotificationPermission = permission

    override suspend fun scheduleTimeBased(due: List<ScheduledReminder>) {
        scheduledCalls += due
        if (scheduleShouldFail) error("scheduleTimeBased failed")
    }

    override suspend fun triggerImmediateIfOverdue(due: List<DueReminder>) {
        triggeredCalls += due
    }

    override suspend fun cancel(ruleIds: List<Long>) {
        cancelCalls += ruleIds
    }

    override fun evaluateNow() {
        // Not exercised by these tests: the orchestrator never calls back into itself.
    }

    override suspend fun openNotificationSettings() {
        error("the sweep never opens settings")
    }
}
