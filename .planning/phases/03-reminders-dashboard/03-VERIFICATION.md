---
phase: 03-reminders-dashboard
verified: 2026-10-24T06:21:20Z
status: human_needed
score: 2/4 must-haves verified
behavior_unverified: 2
overrides_applied: 0
mode: mvp
behavior_unverified_items:
  - truth: "SC2 — User receives a local push notification when a service reminder becomes due"
    test: "On a real Android emulator (API 34) and a booted iOS simulator: install the debug build, grant notifications, add a vehicle, log a service entry, add a mileage reminder whose interval is already exceeded, then background and reopen the app."
    expected: "Exactly one OS notification titled `{vehicle} · {service type}` with the overdue sentence as its body. Backgrounding and reopening twice more produces no second notification. Logging a newer entry of the same type and reopening produces a new notification."
    why_human: "The final hop is platform code with no test coverage and no device observation — `NotificationManagerCompat.notify` on Android and `UNUserNotificationCenter.addNotificationRequest` on iOS. Static analysis proves the orchestrator decides to post; it cannot prove the OS renders anything. No executor in this phase had an emulator or simulator attached."
  - truth: "SC3 — Android evaluates overdue mileage reminders via a background WorkManager task; iOS evaluates on foreground via triggerImmediateIfOverdue(); both platforms deliver correctly"
    test: "Android: confirm the daily periodic worker is registered (`adb shell dumpsys jobscheduler | grep vehicare`) and that an ON_RESUME one-shot worker produces a notification within ~1 minute. iOS: confirm a foreground banner appears while the app is in the foreground (the `willPresent` delegate contract), and that tapping a notification from both a warm and a killed app opens that vehicle's Detail screen."
    expected: "Android: the periodic work is enqueued and the worker posts. iOS: a banner is visible in the foreground (its absence means the delegate is not retained), and both tap paths navigate to the correct vehicle; a malformed identifier navigates nowhere."
    why_human: "WorkManager scheduling, the retained `UNUserNotificationCenterDelegate`, and OS notification-tap intent routing are all runtime OS behaviors invisible to grep and unreachable from a unit test. The delegate-retention failure mode in particular is silent at compile time."
human_verification:
  - test: "Notification delivery end-to-end on both platforms (Android emulator API 34 + booted iOS simulator). Follow 03-07-PLAN.md `<human-check>` groups A-E: 19 numbered steps covering one-notification-per-cycle then silence, baseline-moved re-notification, tap routing from cold and warm start, iOS foreground delegate retention, time-based pre-scheduling, and dual-axis cross-path exclusivity."
    expected: "Each numbered step matches the expectation recorded in the plan. Where a step cannot be performed (e.g. simulator clock override), record which step and what was observed instead."
    why_human: "Requires a real OS notification center. Covers SC2 and SC3."
  - test: "Notification permission flow on both platforms. Follow 03-08-PLAN.md `<human-check>` groups A-D: 15 numbered steps from clean installs covering no-ask-on-cold-launch, the ask appearing only after the first successful save, `Not now` spending the automatic ask, the denied-variant copy and its settings deep link, and both surfaces disappearing on return from system settings without a relaunch."
    expected: "Each numbered step matches the expectation recorded in the plan. In particular step 6 and step 13: enabling notifications in system settings removes the home banner and the Reminders inline row immediately on return, with no relaunch."
    why_human: "The OS permission prompt is one-time and irreversible per install, and cannot be automated. Covers SC2's permission-timing half as experienced by a user."
  - test: "Visual verification of the Reminders screen, the reminder editor, the Vehicle Detail Reminders summary row, the home dashboard status line, and the permission surface against 03-UI-SPEC.md."
    expected: "Urgency ordering is visible, each card's status line is one line with `maxLines = 1` truncating gracefully, overdue/due-now sentences render in `Danger` and others in `Slate`, the summary row sits 24dp below Service history, and no card height changes when a status line is added."
    why_human: "Visual appearance, truncation behavior, spacing and color are not assertable by grep, and the project has no Compose UI test target."
  - test: "Dashboard copy format on the home screen for the three DSH-01 cases: a vehicle with a service entry and an active reminder, a vehicle without a service entry, and a vehicle with no enabled rule."
    expected: "`Last service {date} · {due sentence}` for the first, `{type} · {sentence}` for the second, and `Last service {date} · No active reminders` (date segment omitted entirely with no entry) for the third; ` · +N due` appended only when another enabled rule is due or overdue."
    why_human: "`vehicleStatusLine` is a private `@Composable` that resolves Compose string resources; its assembled output has no unit-test coverage. The data reaching it is verified, the rendered sentence is not."
warnings:
  - concern: "4 behavioral tests in ReminderDeliveryTest.kt are suppressed on BOTH targets by an unconditional @Ignore in commonTest, even though they pass on iosSimulatorArm64Test."
    evidence: "Verifier probe: stripping the 4 @Ignore annotations and running :shared:iosSimulatorArm64Test yields 318 tests, 0 skipped, 0 failures — all 4 pass. The executor's own KDoc predicted this ('is expected to pass on a real resource environment (iosSimulatorArm64Test ...)') but 03-07 ran with no simulator available and placed the ignore in commonTest, which suppresses it on the iOS target too."
    impact: "Not a correctness defect — the logic is proven correct as of this commit by the probe. But in the committed tree these are the ONLY tests that assert a notification is ever actually posted or scheduled: every passing assertion on triggeredCalls/scheduledCalls/markArmedCalls is negative (isEmpty). A future regression in evaluateAndDeliver's claim-then-post or schedule-then-markArmed ordering would not be caught by the standing gate."
    recommendation: "Replace the unconditional @Ignore with a target-scoped ignore (an expect/actual @IgnoreJvm-style annotation, or move the 4 scenarios to iosTest/) so the iOS target keeps the coverage it already passes. Adding Robolectric is NOT required to recover this."
  - concern: "REQUIREMENTS.md already marks REM-03 and REM-04 as [x] Complete, ahead of any device observation."
    evidence: ".planning/REQUIREMENTS.md lines for REM-03/REM-04 are checked and the traceability table reads 'Complete'."
    impact: "Traceability overstates confidence for the two requirements whose delivery half is unobserved. Cosmetic, but it is the artifact a reader trusts."
    recommendation: "Leave REM-01, REM-02 and DSH-01 as Complete; consider annotating REM-03/REM-04 as pending device verification until the human checks above are recorded."
---

# Phase 3: Reminders & Dashboard Verification Report

**Phase Goal (MVP mode, User Story):** As a vehicle owner, I want to see which service is due and be reminded when it is, so that I never miss maintenance.
**Verified:** 2026-10-24T06:21:20Z
**Status:** human_needed
**Re-verification:** No — initial verification
**User Story format:** valid (`As a …, I want to …, so that ….`)

## User Flow Coverage (MVP mode)

The goal's `[outcome]` clause is "so that I never miss maintenance." That outcome has two halves: **seeing** what is due, and **being reminded** when it is. The first half is fully verified in the codebase; the second half is implemented and wired end to end but its final OS hop has never been observed on a real platform.

| # | Flow step | Expected | Evidence in codebase | Status |
|---|-----------|----------|---------------------|--------|
| 1 | Owner opens home | Every vehicle listed, most urgent first | `VehicleListViewModel.buildDashboard` → `ReminderOrdering.sortVehicles`; 13 passing tests in `VehicleListViewModelTest` incl. `anOverdueVehicleSortsBeforeAnUpcomingOneRegardlessOfInputOrder` | VERIFIED |
| 2 | Owner reads one card | Last service date + next due service on one line | `vehicleStatusLine` composes `service_summary_last` + `ReminderCopy.statusSentenceText`; `lastServiceEpochMs` flows from the real `selectVehicleStats` SQL | VERIFIED (rendered copy → human) |
| 3 | Owner opens a vehicle → Reminders | Rules listed urgency-first, disabled last | `RemindersViewModel` → `ReminderStatus.evaluateAll` + `ReminderOrdering.sortRules`; nav wired `VehicleDetail → RemindersRoute` in `App.kt:181` | VERIFIED |
| 4 | Owner adds a reminder | One service type + km and/or months interval saves and appears | `ReminderEditViewModel.save` → `ReminderFormValidation.toRule` → `repo.save`; 24 passing tests incl. `aSuccessfulSaveCallsOnSavedExactlyOnceWithThePreservedCreatedAtAndNullMarkers`; schema carries both `interval_km` and `interval_months` | VERIFIED |
| 5 | App asks for notification permission | Only after that first save, never on launch | `requestPermission` has exactly 2 call sites, both inside a dialog `onConfirm`; cold launch runs only `coordinator.refresh()` (a read); `NotificationPermissionPolicyTest` pins the truth table | VERIFIED (static) / human for the OS prompt |
| 6 | Service becomes due | The owner is notified once, on the right day | `ReminderDelivery.evaluateAndDeliver` claims then posts; Android periodic worker enqueued at process start; iOS ON_RESUME sweep | **PRESENT_BEHAVIOR_UNVERIFIED** |
| 7 | Owner taps the notification | That vehicle's Detail screen opens | `ReminderNotificationId.encode/parse` + `NotificationTapBus.consume` in `App.kt:115` | **PRESENT_BEHAVIOR_UNVERIFIED** |

Steps 1-5 are complete. Steps 6-7 are the "being reminded" half and are the whole content of the human-verification list.

## Goal Achievement

### Observable Truths (ROADMAP Success Criteria — the contract)

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can set a mileage-based reminder and a time-based reminder for any service type on any vehicle | ✓ VERIFIED | `reminder_rule` carries both nullable `interval_km` and `interval_months` with "at least one set" semantics. Submit gate `ReminderFormValidation.isSubmittable` proven by 20 passing cases (`kmOnly`, `monthsOnly`, both, zero-interval blocked, two-type blocked). Editor reachable: `Reminders` FAB → `ReminderEditRoute(vehicleId, 0L)` and rule tap → `ReminderEditRoute(vehicleId, ruleId)` (`App.kt:208,211`). Round-trip proven on a real SQLDelight driver by `ReminderRepositoryTest.saveInsertsARuleThatRoundTripsEveryFieldWithBothMarkersNull`. Edit-seeding invariant (`aSecondEmissionAfterTheFirstDoesNotOverwriteAnEditedField`) passes. |
| 2 | User receives a local push notification when a reminder becomes due — permission requested only on first reminder creation | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | **Permission-timing half: verified.** `requestPermission` call sites are exactly `VehicleListScreen.kt:221` and `RemindersScreen.kt:282`, both inside a dialog `onConfirm`; nothing requests on cold launch (`LaunchedEffect(Unit) { coordinator.refresh() }` only reads). `shouldAutoExplain`/`isAskable` pinned by `NotificationPermissionPolicyTest`; the post-save hand-off pinned by `FirstReminderAskSignalTest` (9 cases) and `aSuccessfulAddPublishesTheVehicleIdExactlyOnce` / `aSuccessfulUpdatePublishesNothing` / `aFailedAddLeavesTheSignalNull`. **Delivery half: unobserved.** `NotificationManagerCompat.notify` and `UNUserNotificationCenter` calls have no test coverage and no device observation. |
| 3 | Android: overdue mileage reminders evaluated by a background WorkManager task; iOS: evaluated on foreground via `triggerImmediateIfOverdue()`; both deliver correctly | ⚠️ PRESENT_BEHAVIOR_UNVERIFIED | **Wiring present and correct.** `VehiCareApp.onCreate` → `enqueueDailyReminderEvaluation` → `WorkManager.enqueueUniquePeriodicWork(PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS), KEEP)`. `ReminderWorker` reads the process-wide DB via `ReminderDeliveryFactory` → `DatabaseProvider.require()` (never a second driver). iOS `triggerImmediateIfOverdue` implemented in `NotificationScheduler.ios.kt:105` and invoked from `ReminderDelivery.kt:150`. **Orchestrator logic behaviorally proven** by the verifier probe (see Warnings): claim-then-post and schedule-then-`markArmed` both pass on `iosSimulatorArm64Test`. **Not proven:** that WorkManager actually fires, that the iOS delegate is retained well enough to show a foreground banner, and that tap routing reaches Detail. |
| 4 | Home screen dashboard shows all vehicles with last service date and next due service at a glance | ✓ VERIFIED | Four flat reads combined in `VehicleListViewModel` (never one query per vehicle), one `ReminderStatus.evaluateAll` join, ordered by `ReminderOrdering.sortVehicles`. `lastServiceEpochMs` traces to the real query `selectVehicleStats: SELECT vehicle_id, MAX(service_date_epoch_ms) … GROUP BY vehicle_id`. Card consumes `row.vehicle/top/otherDueCount/lastServiceEpochMs`. 13 passing tests cover all three DSH-01 copy cases, the `+N due` counter, disabled rules, and the no-entry null path. All four list states render with the FAB outside the `when`. |

**Score:** 2/4 truths verified (2 present, behavior-unverified)

### Plan-Level Truths

All 53 plan-declared truths were checked. Grouped by plan:

| Plan | Truths | Verified | Notes |
|------|--------|----------|-------|
| 03-01 | 4 | 4 | Schema v4 + `3.sqm`; `verifyCommonMainVehiCareDatabaseMigration` re-run by verifier → BUILD SUCCESSFUL. Single DB instance proven by the two `DatabaseProvider.initialize` sites + the one `DatabaseProvider.require()` Koin binding. |
| 03-02 | 6 | 6 | `ReminderStatusTest` + `ReminderOrderingTest` cover both axes, the exact-zero boundary, lead windows, the no-mileage fallback, cycle-key change-detection and every tie-break. |
| 03-03 | 9 | 9 | `markNotifiedClaimsOncePerCycleKeyAndReclaimsOnAChangedKey` and `markArmedWritesOnlyTheArmedMarkerIdempotently` run against a **real driver** in `iosTest`. Cascade proven by `deleteRulesForVehicle` inside the vehicle-delete transaction. |
| 03-04 | 5 | 5 | Single-select chip transition returns the *same List instance* on a no-op tap; all four list states present with a persistent FAB. |
| 03-05 | 6 | 6 | Editor seeding, submit gate, preserved `createdAt` with both markers nulled, guarded delete, and the Detail summary row as the only entry point to Reminders. |
| 03-06 | 6 | 6 | Dashboard built from four flat reads; single `maxLines = 1` status line; urgency ordering supersedes newest-first. |
| 03-07 | 9 | 7 | 2 truths (one-notification-per-cycle across both iOS paths as *delivered*; foreground banner visibility) are the behavior-unverified pair rolled into SC2/SC3. The disjoint-path and axis-selection arithmetic IS proven by 16 passing pure tests. |
| 03-08 | 8 | 8 | Both threat-model grep gates clean: `permissionState()` appears nowhere under `ui/` or in `App.kt` (T-03-34), and `NotificationPermissionRequester` nowhere under `ui/`, `di/` or `App.kt` (T-03-39). All 10 of the plan's own automated gates re-run by verifier and green. |

### Required Artifacts

62 artifacts declared across 8 plans. 59 passed the automated three-level check outright; 3 flagged a missing `contains:` pattern and were manually resolved as **cosmetic grep misses, not gaps**:

| Artifact | Expected pattern | Status | Details |
|----------|-----------------|--------|---------|
| `shared/src/commonMain/kotlin/com/vehicare/data/ReminderRuleEntityMapper.kt` | `internal fun Reminder_rule.toDomain` | ✓ VERIFIED | Implemented as `internal fun ReminderRuleEntity.toDomain()` with `import com.vehicare.db.Reminder_rule as ReminderRuleEntity` at line 3. Same receiver type via an import alias. |
| `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt` | `VehicleDashboardRow` | ✓ VERIFIED | The type is never named because it arrives through inference on `state.items`; the screen consumes all four of its fields (`row.vehicle`, `row.top`, `row.otherDueCount`, `row.lastServiceEpochMs`). Substantively wired. |
| `androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt` | `enqueueUniquePeriodicWork` | ✓ VERIFIED | Deliberately extracted to `enqueueDailyReminderEvaluation(Context)` in `NotificationScheduler.android.kt:37-41`, which calls `enqueueUniquePeriodicWork(..., KEEP, PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS))`. Documented rationale: keeps `androidx.work` `implementation`-scoped to `:shared` so `:androidApp` never names WorkManager. Sound decision. |

All other artifacts: exist, substantive, wired. No stubs. No orphans.

### Key Link Verification

29 key links declared. **All 29 verified WIRED by manual grep.**

> **Tooling note:** `gsd-tools query verify.key-links` reported only 9/29 verified. Every one of the 20 "failures" had a `\\.` sequence in its pattern and every pattern *without* an escaped dot passed — a regex double-escaping bug in the tool, not a wiring gap. Each of the 20 was re-checked by hand and found present. Future verifications of this project should not trust that query's negative results.

Representative confirmations: `DatabaseProvider.initialize` at `VehiCareApp.kt:31` and `MainViewController.kt:21`; `DatabaseProvider.require()` at `AppModule.kt:43`; `reminderRuleQueries.markNotified` at `ReminderRepositoryImpl.kt:141` and `markArmed` at `:157`; `lower(t.label)` at `ServiceEntry.sq:118`; `NotificationBridge.shared.install()` at `AppDelegate.swift:9`; `NotificationTapBus.consume()` at `App.kt:115`; `permissionCoordinator.refresh()` inside the `ON_RESUME` `LifecycleEventEffect` at `App.kt:106`; `scheduler.permissionState()` at `NotificationPermissionRequester.android.kt:34,45`.

### Data-Flow Trace (Level 4)

| Artifact | Data variable | Source | Produces real data | Status |
|----------|--------------|--------|--------------------|--------|
| `VehicleCard` | `lastServiceEpochMs` | `selectVehicleStats` — `MAX(service_date_epoch_ms) … GROUP BY vehicle_id` | Yes | ✓ FLOWING |
| `VehicleCard` | `top` (ReminderRow) | `observeAllRules` + `observeTypeBaselines` + `observeVehicleStats` → `ReminderStatus.evaluateAll` | Yes | ✓ FLOWING |
| `ReminderCard` | `row` | `RemindersViewModel` three-flow join → `evaluateAll` → `sortRules` | Yes | ✓ FLOWING |
| `ReminderSummaryRow` | `summary` | `ReminderSummaryViewModel` → `evaluateAll` + `topRule` | Yes | ✓ FLOWING |
| `ReminderStatusRow` | `askable` | `NotificationPermissionCoordinator.state` ← `scheduler.permissionState()` + `store` | Yes | ✓ FLOWING |
| `ReminderEditScreen` | `values` | `ReminderEditViewModel` seeded from `observeRule` | Yes | ✓ FLOWING |

No HOLLOW props, no hardcoded empty call-site values, no static fallbacks standing in for a query.

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Android unit suite | `./gradlew :shared:testAndroidHostTest` | 256 tests, 0 failures, 4 skipped | ✓ PASS |
| iOS unit suite | `./gradlew :shared:iosSimulatorArm64Test` | 318 tests, 0 failures, 4 skipped | ✓ PASS |
| Android app assembles | `./gradlew :androidApp:assembleDebug` | BUILD SUCCESSFUL | ✓ PASS |
| Schema migration integrity (SC for 03-01) | `./gradlew :shared:verifyCommonMainVehiCareDatabaseMigration` | BUILD SUCCESSFUL | ✓ PASS |
| **Probe: do the 4 `@Ignore`d delivery tests pass on iOS?** | strip `@Ignore` → `./gradlew :shared:iosSimulatorArm64Test` | **318 tests, 0 skipped, 0 failures — all 4 pass** | ✓ PASS |
| 03-08 structural gates (7 greps) | `grep -c` on both screens + `ReminderStatusRow` | all = 1 as required | ✓ PASS |
| Threat gate T-03-34 | `grep -rn "permissionState()" ui/ App.kt` | 0 hits (required) | ✓ PASS |
| Threat gate T-03-39 | `grep -rn "NotificationPermissionRequester" ui/ di/ App.kt` | 0 hits (required) | ✓ PASS |
| OS notification actually posts | — | requires emulator/simulator | ? SKIP → human |
| WorkManager actually fires | — | requires emulator | ? SKIP → human |
| iOS foreground banner visible | — | requires simulator | ? SKIP → human |

The verifier's probe test file modification was reverted; `git diff` against `HEAD` for `ReminderDeliveryTest.kt` is empty and its 4 `@Ignore` annotations are restored.

### Probe Execution

No `scripts/*/tests/probe-*.sh` exist in this project and no plan declares one. Phase verification criteria are expressed as Gradle tasks and grep gates, all of which were executed above.

| Probe | Command | Result | Status |
|-------|---------|--------|--------|
| (none declared) | — | — | N/A |

### Requirements Coverage

| Requirement | Source plans | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| REM-01 | 03-01, 03-02, 03-03, 03-04, 03-05 | Mileage-based reminder (trigger after X km from last service) | ✓ SATISFIED | `interval_km` + `lead_km` columns; distance axis in `ReminderStatus`; baseline from `selectTypeBaselines` case-insensitively matched; `sanitizeKm` + submit gate tested |
| REM-02 | 03-01, 03-02, 03-03, 03-04, 03-05 | Time-based reminder (trigger after X months from last service) | ✓ SATISFIED | `interval_months` + `lead_days`; month arithmetic with 31 Jan → 28 Feb and leap-day clamping pinned by `ReminderScheduleTest`; `createdAt` fallback baseline for a type with no entry |
| REM-03 | 03-03, 03-07, 03-08 | Local push notification when a reminder becomes due | ? NEEDS HUMAN | Full chain implemented and wired; once-per-cycle claim proven on a real driver; orchestrator post decision proven by probe. The OS post itself is unobserved. |
| REM-04 | 03-07 | On iOS, mileage-based reminders evaluated on app foreground | ? NEEDS HUMAN | `triggerImmediateIfOverdue` implemented; iOS sweep invoked on `ON_RESUME`; `immediateAxes`/`timePathOwnedRuleIds` disjointness proven by 16 pure tests. Foreground execution on a simulator is unobserved. |
| DSH-01 | 03-06, 03-08 | Overview of all vehicles with last service date and next due service on home | ✓ SATISFIED | Level-4 data flow confirmed; 13 ViewModel tests; all three copy cases covered |

**Orphan check:** REQUIREMENTS.md maps exactly REM-01, REM-02, REM-03, REM-04, DSH-01 to Phase 3. All 5 are claimed in at least one PLAN's `requirements` frontmatter. **No orphaned requirements.**

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `shared/src/commonTest/.../ReminderDeliveryTest.kt` | 361, 386, 412, 446 | `@Ignore` suppressing 4 behavioral tests on both targets although they pass on iOS | ⚠️ Warning | The only positive assertions that a notification is posted or scheduled are disabled. See Warnings. |

**Debt markers:** zero. `grep` for `TBD`, `FIXME`, `XXX`, `TODO`, `HACK`, `PLACEHOLDER` across `shared/src`, `androidApp/src` and `iosApp` returns **no matches**. The debt-marker gate passes cleanly.

**Stub scan:** the `placeholder` matches that do appear are UI vocabulary (a dash placeholder in `FieldRow`, the photo-field placeholder, the type-circle hero placeholder) or the deliberate Phase 1 `D-17` Settings slot and `D-22` launcher-icon artwork. None is a Phase 3 stub. No empty handlers, no `console.log`-equivalent-only implementations, no static returns standing in for a query.

### Gaps Summary

**No gaps.** No truth failed, no artifact is missing or a stub, no key link is unwired, and no blocker anti-pattern or unreferenced debt marker exists. The phase's logic is implemented to an unusually high standard: the pure engine, the ordering, the once-per-cycle claim, the form validation, the permission policy and the dashboard assembly are all covered by passing tests, several of them running against a real SQLDelight driver.

What stops this phase from reading `passed` is not a defect but an absence of observation. The goal's second half — "be reminded when it is" — terminates in two OS APIs (`NotificationManagerCompat.notify`, `UNUserNotificationCenter`) plus WorkManager scheduling and a retained iOS notification delegate. None of that was ever run on a real Android emulator or a booted iOS simulator by any executor in this phase, and none of it is reachable from a unit test. 03-07's SUMMARY is explicitly honest about this and claims no observed delivery; that honesty is confirmed, not contradicted, by this verification.

One finding is worth the implementer's attention and is recorded as a Warning rather than a gap: the 4 `@Ignore`d tests **pass on `iosSimulatorArm64Test`** — the verifier ran them. 03-07 could not discover this because it had no simulator, and its unconditional `commonTest` `@Ignore` therefore discards coverage the project can already afford. Recovering it needs a target-scoped ignore, not the Robolectric dependency the SUMMARY proposed.

---

_Verified: 2026-10-24T06:21:20Z_
_Verifier: Claude (gsd-verifier)_
