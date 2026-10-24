---
phase: 03-reminders-dashboard
plan: 05
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, koin, navigation-compose, reminders]

# Dependency graph
requires:
  - phase: 03-reminders-dashboard (plan 01-04)
    provides: schema v4 + ReminderRule domain model, ReminderStatus/ReminderOrdering engine, ReminderRepository, ReminderCopy, ReminderFormValidation, RemindersScreen/RemindersViewModel, RemindersRoute (declared but unreachable)
provides:
  - ReminderEditViewModel: add/edit form state machine with one-shot load guard, cross-vehicle rule-id rejection (T-03-22), submit gate delegated to ReminderFormValidation, single-flight save/delete
  - ReminderEditScreen (ReminderEditRoute composable): the six-group locked-order editor — service type, intervals, advance notice, reminder-on toggle, submit, delete
  - ReminderSummaryViewModel/ReminderSummaryRow: Vehicle Detail's Reminders aggregate row, mirroring ServiceSummaryRow
  - Both reminder destinations (RemindersRoute, ReminderEditRoute) registered in App.kt as pushed sub-screens; Detail's onOpenReminders entry point wired
  - AppModule ViewModel factories for ReminderEditViewModel and ReminderSummaryViewModel
affects: [03-06 (dashboard card status line), 03-07 (notification scheduler hooks into save/delete), 03-08 (permission-ask hook goes into ReminderEditViewModel.save)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ViewModel state machine mirrors ServiceEntryEditViewModel: sealed Loading/Ready/NotFound states, initialLoadApplied one-shot guard, saving/deleting single-flight flags, CancellationException always rethrown"
    - "Route-authority check on edit load: a loaded rule whose vehicleId disagrees with the route's vehicleId resolves to NotFound rather than rendering another vehicle's data (T-03-22)"
    - "Single-select chip mode reuses Phase 2's SelectionMode.Single/selectServiceType/confirmCustomServiceType internal functions rather than re-implementing chip logic"
    - "Summary row ViewModels (ServiceSummaryViewModel, ReminderSummaryViewModel) share the nullable-StateFlow 'unresolved vs resolved-empty' contract with .catch { emit(null) } + stateIn(WhileSubscribed(5s))"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditViewModel.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderEditViewModelTest.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderSummaryViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/ReminderSummaryRow.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt

key-decisions:
  - "FakeReminderRepository in ReminderEditViewModelTest renamed from RemindersViewModelTest's identical-name fake to FakeReminderEditRepository — two top-level private classes with the same name in the same package (different files) fail Kotlin's redeclaration check despite file-private visibility"
  - "NotFound's 'back' button label reuses the existing cd_back resource ('Back') rather than adding a new string — no reminder-specific back-action string exists in the Phase 3 block and the plan's success criteria forbid new strings"

requirements-completed: [REM-01, REM-02]

# Metrics
duration: ~40min
completed: 2026-10-24
status: complete
---

# Phase 3 Plan 05: Reminder editor and Vehicle Detail summary row Summary

**Single-screen add/edit reminder form (ReminderEditViewModel + ReminderEditScreen) with cross-vehicle rule-id rejection, plus the Reminders summary row that makes the Phase 3 Reminders screen reachable from Vehicle Detail.**

## Performance

- **Duration:** ~40 min (estimated — start time not captured at session start)
- **Completed:** 2026-10-24
- **Tasks:** 3
- **Files modified:** 9 (5 created, 4 modified)

## Accomplishments

- A reminder rule can now be created from nothing, read back, corrected and deleted end to end:
  Detail → Reminders → editor → save/delete → back, with the delete message hosted by the shell
- `ReminderEditViewModel` loads an edit row exactly once and rejects a rule belonging to another
  vehicle as `NotFound` (T-03-22) — a stale or crafted route parameter can never edit another
  vehicle's rule
- The editor renders all six UI-SPEC groups in locked order, delegating every validation rule and
  every chip transition to plan 04's `ReminderFormValidation` and Phase 2's `ServiceTypeSelection`
  — no rule is re-implemented in this screen
- Vehicle Detail now carries both summary rows (Service history, Reminders) in the locked order
  and spacing, and the Reminders row stays tappable at zero rules so the Reminders empty state's
  CTA is always reachable
- Both new destinations are registered once in `App.kt` as pushed sub-screens — neither is a tab
  root — and `RemindersRoute`, left unreachable by plan 04, is now wired to the Detail entry point

## Task Commits

Each task was committed atomically:

1. **Task 1: Build the editor state machine with the one-shot load guard and the guarded write paths** - `6e9be78` (feat)
2. **Task 2: Render the editor in the locked field order with its error, delete and failure surfaces** - `be3c29f` (feat)
3. **Task 3: Put the summary row on Detail and wire both reminder destinations into the one graph** - `e9f986a` (feat)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditViewModel.kt` - Add/edit form state machine: `Loading`/`Ready`/`NotFound`, one-shot load guard, cross-vehicle rejection, single-flight save/delete
- `shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderEditViewModelTest.kt` - 20 cases covering add/edit seeding, the load guard, sanitizers, the submit gate, save and delete, both green on Android host and iOS simulator
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditScreen.kt` - `ReminderEditRoute` composable: six locked groups, per-field error line, save/delete failure snackbars, `ReminderToggleRow`
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderSummaryViewModel.kt` - `ReminderSummary(ruleCount, top)` built from the same `ReminderStatus.evaluateAll` + `ReminderOrdering.topRule` join the Reminders list uses
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/ReminderSummaryRow.kt` - Detail's Reminders row, mirroring `ServiceSummaryRow`'s container/typography/icon exactly
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` - `onOpenReminders` parameter; `ReminderSummaryRow` inserted 24dp below `ServiceSummaryRow` inside the unchanged 48dp breaks
- `shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` - `ReminderEditRoute(vehicleId, ruleId = 0L)`
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` - `composable<RemindersRoute>` and `composable<ReminderEditRoute>` registered as pushed sub-screens; Detail's `onOpenReminders` wired; shell hosts `snackbar_reminder_deleted`
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` - `ReminderEditViewModel`/`ReminderSummaryViewModel` Koin factories; the editor's learned-chip source is `get<ServiceEntryRepository>().observeLearnedTypes()`, not a second reminder read

## Decisions Made

- `FakeReminderRepository` in the new test file collided (Kotlin redeclaration error) with the identically-named top-level private fake already in `RemindersViewModelTest.kt` in the same package — renamed to `FakeReminderEditRepository`. Top-level `private` classes are file-scoped for *access*, but the compiler's name-conflict check still operates at package granularity for class declarations.
- The `NotFound` state's "back" button reuses the existing `cd_back` resource text ("Back") instead of adding a new reminder-specific back-action string — no such string exists in the Phase 3 block, and the plan's success criteria ("every new string is one of the Phase 3 resources... no new... value appears") rule out adding one for this.

## Deviations from Plan

None - plan executed exactly as written. Both minor decisions above were implementation choices within the plan's own constraints (naming a colliding test fixture, choosing an existing string resource), not scope changes.

## Issues Encountered

- The worktree had no `local.properties` (gitignored, not copied into the worktree checkout), so the first Gradle invocation failed with "SDK location not found." Created `local.properties` pointing at the same `sdk.dir` the main checkout uses — this file is gitignored and was not committed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The reminder rule lifecycle (create/read/edit/delete) is fully usable end to end as of this plan; plan 06 (dashboard card status line) and plan 07 (notification scheduler) can build on a stable `ReminderEditViewModel.save`/`delete` entry point.
- `ReminderEditViewModel.save()` deliberately has no permission call — plan 08's notification-permission hook is expected to land inside that method, and its absence here is intentional, not an omission.
- Full regression check: 186 Android-host tests and 248 iOS-simulator tests green (0 failures) after all three tasks, plus a clean `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64`.

---
*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*

## Self-Check: PASSED

All 9 created/modified source files and the SUMMARY.md itself verified present on disk; all 3 task commits (`6e9be78`, `be3c29f`, `e9f986a`) verified present in `git log --all`.
