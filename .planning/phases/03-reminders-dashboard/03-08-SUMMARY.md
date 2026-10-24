---
phase: 03-reminders-dashboard
plan: 08
subsystem: ui
tags: [notifications, permissions, compose-multiplatform, expect-actual, sharedpreferences, nsuserdefaults, stateflow, unusernotificationcenter, post-notifications]

# Dependency graph
requires:
  - phase: 03-reminders-dashboard
    provides: "plan 03-07's `NotificationScheduler.permissionState()`, `openNotificationSettings()` and the fire-and-forget `evaluateNow()` this plan re-enters after a grant"
  - phase: 03-reminders-dashboard
    provides: "plan 03-04's `RemindersScreen`/`RemindersViewModel` (the dialog host and the four-state matrix), plan 03-05's `ReminderEditViewModel.save` hook point, plan 03-06's `VehicleDashboardRow.top` (the fleet's enabled-rule predicate)"
  - phase: 03-reminders-dashboard
    provides: "plan 03-03's seven sanctioned permission strings (none added here)"
provides:
  - "One platform permission request seam (`NotificationPermissionRequester` + both `@Composable` actuals) — the only code in the app that touches an Android launcher or iOS `requestAuthorizationWithOptions`"
  - "Two persisted, never-cleared bits (`hasRequestedSystemPrompt`, `hasAutoExplained`) over SharedPreferences/NSUserDefaults"
  - "The pure `NotificationPermissionPolicy` askability gate — `isAskable` / `shouldAutoExplain` / `shouldShowSurface`, exhaustively tested"
  - "One process-wide `NotificationPermissionCoordinator` + `LocalNotificationPermissionCoordinator`, the sole reader of the platform permission state outside the scheduler"
  - "`FirstReminderAskSignal` — the replay-1 add→Reminders hand-off"
  - "`ReminderStatusRow` — one surface component rendered in two placements (home banner, Reminders inline row)"
  - "`NotificationsPermissionDialog` — the fixed-copy explanation that precedes the OS prompt"
affects: [phase-verification, uat, future-settings-screen, future-notification-work]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Plain class + `@Composable expect fun` factory (not `expect class`) when only the factory needs a platform"
    - "Platform callback reports `scheduler.permissionState()`, never the raw OS boolean"
    - "`null` coordinator state means 'not read yet'; every surface hides while it is null"
    - "Two separate persisted facts (ask-ever-shown vs automatic-ask-spent) instead of one conflated flag"
    - "Replay-1 `MutableStateFlow` singleton as a cross-screen one-shot event bus (the `NotificationTapBus` idiom)"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationPermissionRequester.kt
    - shared/src/androidMain/kotlin/com/vehicare/notifications/NotificationPermissionRequester.android.kt
    - shared/src/iosMain/kotlin/com/vehicare/notifications/NotificationPermissionRequester.ios.kt
    - shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationPermissionStore.kt
    - shared/src/androidMain/kotlin/com/vehicare/notifications/NotificationPermissionStore.android.kt
    - shared/src/iosMain/kotlin/com/vehicare/notifications/NotificationPermissionStore.ios.kt
    - shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationPermissionCoordinator.kt
    - shared/src/commonMain/kotlin/com/vehicare/notifications/FirstReminderAskSignal.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/ReminderStatusRow.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/NotificationsPermissionDialog.kt
    - shared/src/commonTest/kotlin/com/vehicare/notifications/NotificationPermissionPolicyTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/notifications/FirstReminderAskSignalTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/RemindersViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/RemindersScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderEditViewModelTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/reminders/RemindersViewModelTest.kt

key-decisions:
  - "The automatic ask and the manual ask are two separate gates: `shouldAutoExplain = !hasAutoExplained && isAskable` spends the automatic dialog once per install, while `isAskable` keeps a surface tap alive — so `Not now` ends the nagging without trapping the user"
  - "`hasAutoExplained` is written when the dialog is *presented*, not when it is dismissed, so a process death on the dialog cannot buy a second automatic ask"
  - "`hasRequestedSystemPrompt` is written *before* the platform call, so a process death during the OS sheet cannot produce a second ask"
  - "Both platform actuals report `scheduler.permissionState()` rather than the launcher/completion boolean — Android's app-level notification switch makes the raw boolean a lie"
  - "RemindersViewModelTest now tracks every ViewModel it builds and cancels its `viewModelScope` in tearDown: the new `init` collector on the process-wide `FirstReminderAskSignal` singleton otherwise outlives the test and races the next test's own collector for the same emission"
  - "`NotificationsPermissionDialog`'s KDoc avoids the literal words Repository/ViewModel/NavController because the plan's grep gate is literal and matches comments (the `Theme.kt` precedent from Phase 1)"

patterns-established:
  - "One permission surface, two placements: a single component plus one shared policy, so the home banner and the Reminders row cannot disagree"
  - "Coordinator-owned platform reads: a grep gate forbids `permissionState()` under `ui/` and in `App.kt`"
  - "Foreground re-read rides the single existing `LifecycleEventEffect(ON_RESUME)` — a second effect would double-enqueue Android delivery"

requirements-completed: [REM-03, DSH-01]

# Metrics
duration: ~100min (including a provider-usage-limit interruption between Task 2's verification and its commit)
completed: 2026-10-24
status: complete
---

# Phase 3 Plan 08: Notification Permission Ask Summary

**The first saved reminder now earns exactly one in-app explanation before the OS prompt — two persisted bits make the automatic ask one-per-install while a tap on the one "notifications are off" surface (home banner + Reminders row) stays the manual way back, and denial leads straight to system settings.**

## Performance

- **Duration:** ~100 min wall clock (interrupted by a provider usage limit between Task 2's verification and its commit; no work lost)
- **Started:** 2026-10-24T04:20:00Z (approx.)
- **Completed:** 2026-10-24T06:07:15Z
- **Tasks:** 3 of 3
- **Files modified:** 19 (12 created, 7 modified)

## Accomplishments

- **The ask exists only after a successful add.** `ReminderEditViewModel.save` publishes `FirstReminderAskSignal` after `repo.save` returns and before the pop, and **only** when `ruleId == 0L`. A failed save, a non-submittable form and every edit publish nothing — which is the amended D-54 wording ("the first successful add"), and the case that would have failed before 2026-10-23.
- **Two gates, deliberately separate.** `isAskable` answers "may the OS still be asked at all?" and never changes on its own; `shouldAutoExplain` (`!hasAutoExplained && isAskable`) answers the narrower "may the app open this dialog by itself one more time?". `Not now` therefore ends the automatic ask permanently while the surface's tap still opens the dialog. All three policy functions are exhaustive `when`s over the enum's three members, with every truth-table row asserted (10 cases).
- **Android's missing `NOT_DETERMINED` is bridged honestly.** `hasRequestedSystemPrompt` is written before the platform call and never cleared, so `DENIED`-before-the-first-ask (the only thing Android can report pre-prompt) is still askable, and a real post-prompt denial is not.
- **One surface, two placements, one policy (D-61).** `ReminderStatusRow` renders as the first `Content` item of both the Vehicles tab (gated on `top != null` across the fleet) and the Reminders list (gated on `rule.enabled`). Only the body copy differs between askable and denied; the title never changes. Neither surface can render in Loading, Empty or Error, and neither has a dismiss control.
- **A grant re-enters delivery.** The request callback calls plan 03-07's fire-and-forget `evaluateNow()` exactly once, only on `GRANTED`, and before `onSettled()` — so the cycle the pre-dialog sweep skipped (it refuses to write a marker without permission) is armed and delivered without the caller awaiting it.
- **Nothing schedules, delivers or claims here.** The only scheduler members used anywhere in the new permission files are `permissionState()`, `openNotificationSettings()` and `evaluateNow()`; a grep gate proves no `markNotified`/`scheduleTimeBased`/`triggerImmediateIfOverdue`/`evaluateAndDeliver` appears in any of them.

## Task Commits

Each task was committed atomically:

1. **Task 1: Build the platform permission seam, the first-ask store and the pure askability policy** — `57833b5` (feat)
2. **Task 2: Wire the ask end to end — save, signal, explanation dialog, OS prompt** — `41f010b` (feat)
3. **Task 3: Render the one permission surface in both placements** — `d884ed2` (feat)

**Plan metadata:** committed separately with this SUMMARY (worktree mode — `STATE.md`/`ROADMAP.md` are the orchestrator's to write after the wave merges).

## Files Created/Modified

**Created**

- `shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationPermissionRequester.kt` — the plain request seam plus the `@Composable expect fun` factory; deliberately not an `expect class` because only the factory needs a platform
- `shared/src/androidMain/.../NotificationPermissionRequester.android.kt` — `rememberLauncherForActivityResult(RequestPermission())` on API 33+, `openNotificationSettings()` + a state re-read below 33 (no runtime notification permission exists there, so no prompt can)
- `shared/src/iosMain/.../NotificationPermissionRequester.ios.kt` — the one-time `requestAuthorizationWithOptions(Alert or Sound)`, hopped back onto the composition's scope; no badge option (the UI-SPEC forbids a badge count)
- `shared/src/commonMain/.../NotificationPermissionStore.kt` — the four-member interface (two bits) and its `@Composable expect fun`
- `shared/src/androidMain/.../NotificationPermissionStore.android.kt` — `SharedPreferences("notification_permission")` over the **application** context, keys `system_prompt_requested` / `auto_explanation_shown`
- `shared/src/iosMain/.../NotificationPermissionStore.ios.kt` — `NSUserDefaults.standardUserDefaults`, keys `vehicare.notification.system_prompt_requested` / `…auto_explanation_shown`
- `shared/src/commonMain/.../NotificationPermissionCoordinator.kt` — `NotificationPermissionUiState`, the pure `NotificationPermissionPolicy`, the coordinator (`refresh`/`requestPermission`/`markAutoExplained`/`openSettings`, single-flight guarded), `LocalNotificationPermissionCoordinator` and `rememberNotificationPermissionCoordinator`
- `shared/src/commonMain/.../FirstReminderAskSignal.kt` — replay-1 vehicle-id bus; non-positive ids ignored, `consume()` mandatory
- `shared/src/commonMain/.../ui/reminders/components/ReminderStatusRow.kt` — the one permission surface (Mist, 12dp radius, 16dp padding, 56dp min height, 8dp row gap, two 24dp decorative glyphs, `labelLarge`/Ink title, `bodyLarge`/Slate body)
- `shared/src/commonMain/.../ui/reminders/components/NotificationsPermissionDialog.kt` — fixed-copy `AlertDialog`, `Paper` container, two 48dp `Ink` buttons, zero string parameters
- `shared/src/commonTest/.../NotificationPermissionPolicyTest.kt` — 10 cases covering every row of all three truth tables
- `shared/src/commonTest/.../FirstReminderAskSignalTest.kt` — 6 cases (publish, zero/negative guard, replacement, consume, non-latching replay)

**Modified**

- `shared/src/commonMain/kotlin/com/vehicare/App.kt` — one `rememberNotificationPermissionCoordinator`, `permissionCoordinator.refresh()` added as a second statement inside the **existing** `ON_RESUME` effect, and one `CompositionLocalProvider` around the `Scaffold`/`NavHost`
- `.../ui/reminders/ReminderEditViewModel.kt` — the add-only publish between `repo.save` and `onSaved`
- `.../ui/reminders/RemindersViewModel.kt` — `pendingPermissionAsk` one-shot flag, `consumePermissionAsk()`, and the `init` collector that consumes only its own vehicle's signal
- `.../ui/reminders/RemindersScreen.kt` — the dialog host (waits for the first read, consumes in every path, spends the bit as the dialog opens) plus the inline status row as the first `Content` item
- `.../ui/vehicles/VehicleListScreen.kt` — the home banner as the first `Content` item and its own host for the shared dialog
- `shared/src/commonTest/.../ReminderEditViewModelTest.kt` — 4 new cases (successful add publishes once, update publishes nothing, failed add and non-submittable add leave the bus null) + signal reset in setup/teardown
- `shared/src/commonTest/.../RemindersViewModelTest.kt` — 4 new cases (own-vehicle flip + consume, cross-vehicle no-op with the bus intact, flag clear, replay-1 proof), plus the `createViewModel` tracking helper and scope cancellation described under Issues Encountered

## Decisions Made

- **Two bits, not one.** Keeping "the user has been put in front of a prompt" apart from "the automatic explanation has been shown" is what lets the surface stay visible after a denial while the automatic dialog never returns. Conflating them is exactly the bug the 2026-10-23 amendment fixes.
- **`hasAutoExplained` is spent at presentation.** `RemindersScreen` calls `coordinator.markAutoExplained()` *before* setting `showPermissionDialog = true`, so `Not now` needs no extra write and a crash on the dialog cannot re-earn the ask.
- **The dialog waits for the first read rather than dropping the ask.** The `LaunchedEffect(pendingAsk, permissionUi)` returns **without consuming** while the coordinator's state is still `null`, and re-runs when the read lands. Consuming early would silently lose the one ask on a slow first read.
- **Delegated-property snapshots before policy calls.** `permissionUi` comes from `collectAsState()` (a delegated val, so not smart-castable); both screens snapshot it into a local `ui` and compute `askable` once, which also satisfies the plan's "the condition and the callback cannot disagree".
- **`Icons.Filled.KeyboardArrowRight` kept despite its deprecation warning.** The plan names it explicitly and `ServiceSummaryRow`/`ReminderSummaryRow` already use it; switching only this one file to the AutoMirrored variant would make the three chevrons inconsistent. Warning only, no error.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test-only ViewModel scope leak made four new `RemindersViewModelTest` cases fail**

- **Found during:** Task 2 (RemindersViewModel tests)
- **Issue:** The new `init`-block collector lives on `viewModelScope`, which the Android framework cancels via `onCleared()` in production — but a `RemindersViewModel(...)` built directly in a test has no owner, so its collector stayed registered on the **process-wide** `FirstReminderAskSignal` singleton after the test ended. Earlier tests' orphaned collectors (all using the same `VEHICLE_ID`) then received later tests' emissions and called `consume()` first, so the test's own ViewModel never saw the signal (3 assertion failures). A fourth test crashed in `tearDown` with a `DispatchException`: an orphaned collector was resumed after `Dispatchers.resetMain()` had already restored the unusable JVM Main dispatcher.
- **Fix:** Added a `createViewModel(...)` helper that records each ViewModel's `viewModelScope`, routed all 11 constructions in the file through it, and made `tearDown` cancel every tracked scope **before** touching `Dispatchers.Main` again. Production code was not changed — the leak does not exist there.
- **Files modified:** `shared/src/commonTest/kotlin/com/vehicare/ui/reminders/RemindersViewModelTest.kt`
- **Verification:** The 4 failing cases pass; full suites green on both targets (256 Android host, 318 iOS simulator, 0 failures).
- **Committed in:** `41f010b` (Task 2 commit)

**2. [Rule 3 - Blocking] Reworded `NotificationsPermissionDialog`'s KDoc to clear a literal grep gate**

- **Found during:** Task 2 (grep gate run)
- **Issue:** The plan's gate is `test -z "$(grep -n 'Repository\|ViewModel\|NavController' NotificationsPermissionDialog.kt)"`, which is literal and matches comments. The KDoc sentence "holds no repository, no ViewModel and no navigation" tripped it — the same trap `Theme.kt` hit in Phase 1 with `isSystemInDarkTheme`.
- **Fix:** Reworded to "holds no data layer or screen-state owner and no destination graph reference", preserving the meaning. The dialog's code had none of the three to begin with.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/NotificationsPermissionDialog.kt`
- **Verification:** The gate now exits 1 (no match).
- **Committed in:** `41f010b` (Task 2 commit)

**3. [Rule 3 - Blocking] Recreated the gitignored `local.properties`**

- **Found during:** Task 1 (first Gradle run)
- **Issue:** Fresh worktree checkout had no `local.properties`; Gradle failed with "SDK location not found" (the same issue every prior executor in this phase hit).
- **Fix:** Wrote `sdk.dir=/Users/andika/Library/Android/sdk`. Confirmed `.gitignore:7` covers it; **not committed**.
- **Files modified:** `local.properties` (untracked, gitignored)
- **Verification:** All Gradle gates run.
- **Committed in:** n/a (intentionally uncommitted)

---

**Total deviations:** 3 auto-fixed (1 bug, 2 blocking)
**Impact on plan:** No scope creep and no production-behaviour change. Deviation 1 is test hygiene for a leak the production lifecycle does not have; 2 is a comment rewording; 3 is local build setup. Every plan interface, gate and acceptance criterion was implemented as written.

## Issues Encountered

- **Provider usage limit interrupted the run** between Task 2's verification and its commit. No work was lost: the worktree still held Task 2's edits, every Task 2 `<verify>` gate was re-run from scratch on resume (not assumed), and the commit then proceeded normally.
- **The `RemindersViewModelTest` failures were initially misdiagnosed** as a `StandardTestDispatcher` scheduler-linking problem. The decisive evidence was the fourth failure's stack trace pointing at `tearDown`'s own `FirstReminderAskSignal.consume()` — proving a still-live collector was being resumed after `resetMain()`, i.e. an orphaned-subscriber problem rather than a dispatcher one. Documented above as deviation 1.

## Verification Evidence

**Full plan gate — all green:**

| Gate | Result |
|------|--------|
| `./gradlew :shared:testAndroidHostTest` | **256 tests, 0 failures**, 4 skipped (the pre-existing `@Ignore`d `ReminderDeliveryTest` cases from 03-07, pending the Robolectric sign-off) |
| `./gradlew :shared:iosSimulatorArm64Test` | **318 tests, 0 failures, 0 errors**, same 4 skipped |
| `./gradlew :shared:compileKotlinIosSimulatorArm64` | SUCCESS — the only proof both `@Composable` expect/actual pairs match |
| `./gradlew :androidApp:assembleDebug` | SUCCESS |

**Grep gates — all green:** exactly one `enum class NotificationPermission` and one `interface NotificationScheduler` under `shared/src` (neither declared here); zero `permissionState()` under `commonMain/.../ui` or in `App.kt`; zero `NotificationPermissionRequester` references under `ui/`, `di/` or `App.kt`; exactly one each of `FirstReminderAskSignal.publish` (non-comment), `LifecycleEventEffect(ON_RESUME)`, `permissionCoordinator.refresh()`, `LocalNotificationPermissionCoordinator provides`, and one `ReminderStatusRow(` / `NotificationsPermissionDialog(` / keyed item per screen; zero `Repository`/`ViewModel`/`NavController` in the dialog; zero `UNAuthorizationOptionBadge`; zero scheduling symbols in the four new permission files; `heightIn(min = 56.dp)` present once.

**No new dependency, string, colour, spacing value or icon:** `git diff` against the base shows `strings.xml` untouched (the seven sanctioned keys already existed from 03-03) and no change to `shared/build.gradle.kts` or `gradle/libs.versions.toml`.

## Outstanding Manual Verification (Honesty Gate — plan Task 3 step D.15)

**None of the 15 `<human-check>` steps could be performed.** This worktree has **no Android emulator or device attached** (`adb devices` lists none) and **no booted iOS simulator** (`xcrun simctl list devices booted` shows none under iOS 27.0). Every step requires installing the app and observing an OS permission sheet or a system-settings round trip, which cannot be automated and cannot be faked.

Specifically outstanding, with nothing observed in their place:

- **A.1–A.8 (Android 13+ emulator):** no ask on cold launch or on entering any screen; the dialog only after the first add; `Not now` keeps the rule and silences later adds *and* edits; deny → denied copy → settings deep link; the banner above the first card with no close button; both surfaces vanishing on return from system settings **without a relaunch** (the ON_RESUME refresh); no surface once every rule is off; no dialog on a cold launch in an askable state.
- **B.9–B.13 (iOS 17+ simulator):** the one-time prompt spent only by `Turn on`; allow → both surfaces gone with no relaunch; deny → denied copy + Settings deep link and no dialog ever again; `Not now` → no automatic dialog on a later edit or a second add while `notDetermined`, with the inline row as the manual way back.
- **C.14 (both):** no banner and no row when permission is granted, and none when no rule is enabled.

The static half of each of those claims **is** proven — the policy's truth tables are asserted row by row in `NotificationPermissionPolicyTest`, the signal's semantics in `FirstReminderAskSignalTest`, the publish/consume behaviour in the two ViewModel suites, and the single-effect/single-provider/single-surface structure by the grep gates — but **no OS permission sheet has been observed on a real OS.** This joins (and is the same class of debt as) 03-07's still-open manual delivery observation and should be surfaced as a human-verification item at phase-level verification (`/gsd-verify-work`).

## User Setup Required

None — no external service configuration. Note that the local, gitignored `local.properties` (Android SDK path) is required in any fresh checkout for Gradle to run; it is intentionally not committed.

## Next Phase Readiness

- **Phase 3 plan work is complete: 8 of 8 plans executed.** `REM-01`…`REM-04` and `DSH-01` are all delivered.
- **Ready for phase verification.** The automated gate is green on both targets.
- **Two carried human-verification items for `/gsd-verify-work`:** this plan's 15-step permission checklist (above) and 03-07's outstanding notification-delivery observation. Both need a real emulator/simulator.
- **Unchanged pre-existing debt:** the 4 `@Ignore`d `ReminderDeliveryTest` cases await the Robolectric sign-off decision; `01-VERIFICATION.md` is still `human_needed`; `01-REVIEW.md` / `02-REVIEW.md` findings remain open.
- **No new dependency was introduced**, so the phase's audited version set is untouched.

## Self-Check: PASSED

- All 13 files this summary claims to have created exist on disk (verified with `test -f`).
- All 4 commits exist on `worktree-agent-a2edeb37495f723f4`: `57833b5`, `41f010b`, `d884ed2`, `09b9227` — on top of the untouched base `6cf1744`.
- No commit in this plan deleted a tracked file (`git diff --diff-filter=D` empty for each).
- No untracked files left behind; `local.properties` is gitignored and intentionally uncommitted.
- `STATE.md` and `ROADMAP.md` were **not** modified (worktree mode — the orchestrator owns them).

---
*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*
