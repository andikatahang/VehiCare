---
phase: 03-reminders-dashboard
plan: 04
subsystem: ui
tags: [compose-multiplatform, kotlin-multiplatform, reminders, state-machine, accessibility, chips]

# Dependency graph
requires:
  - phase: 03-02
    provides: ReminderStatus.evaluateAll + DueState, ReminderOrdering.sortRules — the pure engine this ViewModel joins and orders through
  - phase: 03-03
    provides: ReminderRepository (three flat flows + setEnabled), ReminderCopy (interval/status sentence resolvers), the Phase 3 string block
provides:
  - SelectionMode (Multiple/Single) + selectServiceType, and ServiceTypePicker's new defaulted selectionMode parameter
  - ReminderFormValidation — sanitizers, errors(), isSubmittable(), toRule() (C-32/C-34/C-35), pure and dependency-free
  - RemindersViewModel/RemindersUiState (Loading/Empty/Content/Error) plus the top-level testable observeRemindersUiState flow builder
  - ReminderCard — presentational rule row (type/interval/status text + independent Switch)
  - RemindersScreen's RemindersRoute composable (Scaffold, FAB visible in every state, keyed LazyColumn)
  - RemindersRoute @Serializable nav-destination type in Routes.kt (not yet wired into App.kt — plan 05's job)
  - RemindersViewModel's Koin factory registration in AppModule
affects: [03-05, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure chip-transition delegation: a new SelectionMode wraps the existing toggleServiceType rather than branching inside it, so the multi-select call site stays byte-identical"
    - "Top-level testable flow builder: observeRemindersUiState(repo, id, refresh) lives outside the ViewModel class, mirroring ReminderCopy's suspend/composable split from 03-03"
    - "Two accessibility stops per row: the clickable lives on the merging text column, the Switch stays its own semantics node"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderFormValidation.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/RemindersViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/ReminderCard.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/RemindersScreen.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderFormValidationTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/reminders/RemindersViewModelTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeSelectionTest.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt

key-decisions:
  - "selectServiceType returns the same List instance on a Single-mode no-op tap (identity, not just equality), so the group can never transiently empty and callers can skip a redundant copy (T-03-18)."
  - "ServiceTypePicker's onClick lambda also suppresses the call when the tapped chip is already selected in Single mode — belt-and-suspenders with the pure transition's own no-op, since it also silences the ripple."
  - "observeRemindersUiState is a top-level, repository-injected function, not a ViewModel method, so the three-flow join, the empty/content mapping and the retry path are unit-testable without a ViewModel or a main-dispatcher rule."
  - "toggleFailed is merged into the state from a separate MutableStateFlow via an outer combine, so a switch-write failure never cancels or restarts the three underlying observation queries."
  - "ReminderFormValidation.toRule throws IllegalArgumentException on anything but exactly one selected type — the submit gate makes that unreachable from the UI, so a violation is a programming error, never silently repaired."

patterns-established:
  - "Pure chip-transition delegation: extend selection behaviour by wrapping the existing transition, never by branching inside it."
  - "Top-level testable flow builder alongside the ViewModel class, for state machines that join multiple repository flows."

requirements-completed: [REM-01, REM-02]

# Metrics
duration: ~14min (reconstructed — see Issues Encountered)
completed: 2026-10-24
status: complete
---

# Phase 3 Plan 04: Reminders Screen, Single-Select Chips & Form Rules Summary

**The Reminders screen renders an urgency-ordered rule list with an in-place on/off switch and an always-visible FAB across all four states, backed by a new single-select chip mode and a pure numeric form-validation object the editor (plan 05) will reuse untouched.**

> **This SUMMARY was reconstructed, not written live.** The three task commits below were already on `main` with no `03-04-SUMMARY.md` and no STATE.md/ROADMAP.md update — an executor run that completed all tasks but was interrupted before closing out the plan. Recovery path **"close out manually"**: every commit was inspected against the plan's tasks, every `must_haves.artifact`/`key_links` symbol was grep-verified against the actual committed code, the full Android host suite and the full iOS simulator suite were re-run from scratch, and the source of all three new/changed production files was read in full. See **Issues Encountered** below.

## Performance

- **Duration:** ~14 min (span between the first and last task commit; see Issues Encountered — the true start time predates the first commit and was not recorded)
- **Started:** 2026-10-24T09:13:45+07:00 (first task commit)
- **Completed:** 2026-10-24T09:27:25+07:00 (last task commit)
- **Tasks:** 3
- **Files modified:** 11 (6 created, 5 modified)

## Accomplishments

- `SelectionMode` (`Multiple`/`Single`) + `selectServiceType` extend the existing chip transition with **zero** changed call sites; `ServiceTypePicker`'s new `selectionMode` parameter defaults to `Multiple`, so the service-entry form is untouched.
- `ReminderFormValidation` is a pure, dependency-free object — sanitizers, `errors()`, `isSubmittable()`, `toRule()` — enforcing C-32/C-34/C-35 exactly as specified, with 21 unit tests on both targets.
- `RemindersViewModel` joins three flat reads (`observeRules`, `observeTypeBaselines`, `observeVehicleStats`) through `ReminderStatus.evaluateAll` and `ReminderOrdering.sortRules` exactly once each (grep-confirmed), and `toggle()` writes through the repository with zero optimistic local state — a failed write only flips `toggleFailed`.
- `RemindersScreen` renders all four states (`Loading`/`Empty`/`Content`/`Error`) with the FAB outside the `when` block (always visible), and `ReminderCard` carries exactly one `clickable` (on the merging text column) with the `Switch` as its own, unmerged accessibility node.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add the single-select chip mode and the reminder form rules** - `3719357` (feat)
2. **Task 2: Build the reminders list state machine and register the destination type** - `02be4d2` (feat)
3. **Task 3: Render the rule row and the Reminders destination in all four states** - `af528ca` (feat)

**Plan metadata:** this commit (docs: complete plan — written retroactively, see above)

_Note: all three commits were already present on `main` before this SUMMARY was written; no new production code was added during close-out._

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderFormValidation.kt` — pure form rules: `ReminderFormValues`, `ReminderFormField`, `ReminderFormValidation` (sanitizers, `errors`, `isSubmittable`, `toRule`).
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/RemindersViewModel.kt` — `RemindersUiState` (4 states) + `RemindersViewModel` (`toggle`, `acknowledgeToggleFailure`, `retry`) + top-level `observeRemindersUiState`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/components/ReminderCard.kt` — presentational rule row: type/interval/status text column (clickable, merged) + independent palette-locked `Switch`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/RemindersScreen.kt` — `RemindersRoute` composable: `Scaffold` + `TopAppBar`, `when(state)` content, Box-overlaid FAB, failed-toggle `Snackbar`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt` — adds `SelectionMode` and `selectServiceType`; `toggleServiceType`/`confirmCustomServiceType` unchanged.
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt` — adds the defaulted `selectionMode` parameter and the single-select no-op guard in `renderChip`'s `onClick`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` — adds `@Serializable data class RemindersRoute(val vehicleId: Long)`.
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` — adds the `RemindersViewModel` factory (`viewModel { (vehicleId: Long) -> ... }`).
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeSelectionTest.kt` — adds single-mode no-op/replace cases; multi-select cases unchanged.
- `shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderFormValidationTest.kt` — 21 tests: blank/zero/one boundaries per field, sanitizer caps, gate combinations, `toRule` mapping.
- `shared/src/commonTest/kotlin/com/vehicare/ui/reminders/RemindersViewModelTest.kt` — 8 tests via a hand-written `FakeReminderRepository` (all 10 `ReminderRepository` members implemented): empty/ordered/disabled/no-baseline states, toggle forwarding, failure + acknowledge, error + retry.

## Decisions Made

See `key-decisions` in the frontmatter. In short: the no-op chip tap is identity-preserving (not just value-equal), the failure flag is composed in from outside the observation flow so retries are never disturbed by a switch-write error, and `observeRemindersUiState` is deliberately a free function so the join/ordering/retry logic is testable without instantiating the ViewModel.

## Deviations from Plan

None — every acceptance criterion in `03-04-PLAN.md` holds as committed (verified below). The ViewModel's internal composition (a top-level `observeRemindersUiState` function; `toggleFailed` merged via a second `combine` rather than mutated in place) is an implementation-level choice within the task's own freedom, not a deviation from an explicit instruction, and it produces the exact state-shape and failure semantics the plan specifies.

## Issues Encountered

**This plan's execution was interrupted before close-out.** All three task commits (`3719357`, `02be4d2`, `af528ca`) were already on `main`, each matching one plan task by name and by the files it touched, but `03-04-SUMMARY.md` did not exist and `STATE.md`/`ROADMAP.md` still reflected pre-wave-3 progress. This is the documented "commits exist, SUMMARY missing" anomaly — the orchestrator stopped before dispatching a new executor (which would have risked redoing finished work) and closed the plan out manually instead:

- Read all three task-commit diffs (`git show --stat` + full diffs) and compared them line-by-line against the plan's three task `<action>` blocks and `<acceptance_criteria>`.
- Grep-verified every `must_haves.artifact` symbol and `key_links` pattern from the plan frontmatter against the current working tree — all present. One apparent mismatch (`grep -c 'clickable' ReminderCard.kt` = 2, plan expects 1) was confirmed to be the `import androidx.compose.foundation.clickable` line plus the single real `.clickable(...)` call, not a second interactive element — the file was read in full to confirm exactly one click target exists.
- Re-ran `./gradlew :shared:testAndroidHostTest` (full suite, not filtered): **166 tests, 0 failures, 0 errors.**
- Re-ran `./gradlew :shared:iosSimulatorArm64Test` filtered to the plan's three named classes, then again unfiltered for the full suite: **228 tests, 0 failures, 0 errors** (includes `ReminderFormValidationTest`: 21, `RemindersViewModelTest`: 8, `ServiceTypeSelectionTest`: 15, all passing).
- `compileKotlinIosSimulatorArm64` succeeds (task's own verify requirement).
- Confirmed `App.kt` has zero references to `RemindersRoute`, matching the plan's explicit deferral of destination registration to plan 05.

Because the original executor's live session is gone, the true start time (first file write) is not recorded; the duration above is reconstructed from commit timestamps only, which understates the real elapsed time.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 05 can wire `RemindersRoute` — both the composable in `ui/reminders/RemindersScreen.kt` and the `@Serializable` type in `ui/navigation/Routes.kt` — into `App.kt` alongside the Vehicle Detail entry point and the editor destination. The two share the name `RemindersRoute` in different packages, so `App.kt` will need an import alias, the same pattern already established for other `*Route`-named composables in this codebase.
- Plan 05's editor can consume `SelectionMode.Single` and `ReminderFormValidation` directly; both are complete, pure and already unit-tested on both targets.
- `RemindersViewModel`/`RemindersScreen` are ready for plan 08's permission-row insertion — a comment already marks the spot in the `LazyColumn`, with no placeholder composable in the way.
- The v4 schema, `ReminderRepository` and `ReminderCopy` are unchanged; no dependency or build-file change was made in this plan.

## Self-Check: PASSED

- All 11 files from the plan's `files_modified` list exist on disk; the 6 new files and the 5 modified files all carry the expected symbols (`SelectionMode`, the defaulted `selectionMode` parameter, `RemindersUiState`, `fun RemindersRoute`, `data class RemindersRoute`, the AppModule factory).
- Commits `3719357`, `02be4d2`, `af528ca` exist in `git log`, one per task, in task order, with file sets matching each task's declared scope.
- Full Android host suite: 166 tests, 0 failures, 0 errors. Full iOS simulator suite: 228 tests, 0 failures, 0 errors. `compileKotlinIosSimulatorArm64` succeeds.
- `App.kt` carries zero references to `RemindersRoute`, matching the plan's explicit deferral to plan 05.

---
*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*
