---
phase: 03-reminders-dashboard
plan: 06
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, koin, viewmodel, dashboard, dsh-01]

# Dependency graph
requires:
  - phase: 03-reminders-dashboard (plan 02)
    provides: ReminderStatus.evaluateAll, ReminderOrdering.sortVehicles/topRule (the pure engine)
  - phase: 03-reminders-dashboard (plan 03)
    provides: ReminderRepository (observeAllRules/observeTypeBaselines/observeVehicleStats), ReminderCopy, ServiceDates
provides:
  - "VehicleDashboardRow: the urgency-ordered dashboard snapshot (vehicle, top enabled rule, otherDueCount, lastServiceEpochMs)"
  - "VehicleListViewModel reading four flat flows (vehicles + rules + baselines + stats) through one evaluateAll join and one sortVehicles order"
  - "The home card's one DSH-01 status line: Last service {date} · {due sentence} (+N due), falling back to {type} · {sentence}, or No active reminders"
affects: [03-07, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "catch{} placed INSIDE flatMapLatest's transform (attached to the combine() flow), never after flatMapLatest — placing it after terminates the whole refresh-driven collector on the first error and retry() can never recover (mirrors RemindersViewModel's CR-02 pattern)"
    - "Dashboard ordering/top-rule selection delegates entirely to ReminderOrdering; the ViewModel contains no comparator of its own"

key-files:
  created:
    - shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/VehicleListViewModelTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleCard.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt

key-decisions:
  - "catch{} moved inside flatMapLatest's combine() block instead of chained after flatMapLatest, so a refresh() bump after an error re-arms all four reads (discovered via a failing retry test, fixed to match RemindersViewModel's existing pattern)"
  - "VehicleCard's three new parameters (top, otherDueCount, lastServiceEpochMs) have no defaults — the single call site makes a missing status a compile error, not a silently blank line"
  - "The '·' status-line separator is a private top-level code constant (STATUS_LINE_SEPARATOR), never a resource, matching the plan's explicit instruction"

patterns-established:
  - "Dashboard rows are built once per emission via buildDashboard(): associateBy for vehicles/stats, evaluateAll once, groupBy vehicleId, sortVehicles, then map to VehicleDashboardRow — no per-vehicle query anywhere"

requirements-completed: [DSH-01]

# Metrics
duration: ~35min
completed: 2026-10-24
status: complete
---

# Phase 3 Plan 06: Home Dashboard (Urgency Order + Status Line) Summary

**Home vehicle list reordered by urgency (overdue → due soon → upcoming → no active reminder) via `ReminderOrdering.sortVehicles`, with each card's status line now showing `Last service {date} · {due sentence}` (+N due) from four flat reads joined once through `ReminderStatus.evaluateAll`.**

## Performance

- **Duration:** ~35 min
- **Started:** 2026-10-24T02:53:00Z
- **Completed:** 2026-10-24T03:28:28Z
- **Tasks:** 2
- **Files modified:** 4 (+1 created)

## Accomplishments

- `VehicleListViewModel` now combines `observeVehicles()`, `observeAllRules()`, `observeTypeBaselines()` and `observeVehicleStats()` into one `VehicleDashboardRow` list per emission, with exactly one `ReminderStatus.evaluateAll` call and one `ReminderOrdering.sortVehicles` call — no per-vehicle query anywhere (RESEARCH U6, PITFALLS § SQL 3).
- Added `internal fun dashboardDueCount(rows, displayedRuleId)` — a pure, unit-testable function that counts the vehicle's other enabled due-soon/overdue/due-now rules, excluding the displayed rule, disabled rows, `Upcoming` and `NoMileageData`.
- `VehicleCard` renders one new `maxLines = 1` status line as the last child of the existing text column, inside the card's merged semantics: date-led when a service entry exists, falling back to the service type otherwise, with the `+N due` suffix and `Danger`/`Slate` tone rule (D-58/D-59/D-60).
- `VehicleListScreen` passes the three new dashboard fields through, keyed by `row.vehicle.id`, with the four-state matrix, FAB and padding untouched.
- `AppModule` wires `VehicleListViewModel(get(), get())` with both `VehicleRepository` and `ReminderRepository`.
- 13 new tests in `VehicleListViewModelTest` cover Empty/Content/Error, urgency ordering across vehicles, `otherDueCount` at 0/1/2, unevaluable-rule exclusion, disabled-rule exclusion, newest-first ties in the no-active-reminder bucket, and retry recovery after a failure.

## Task Commits

Each task was committed atomically:

1. **Task 1: Assemble the urgency-ordered dashboard snapshot from four flat reads** - `dd493ae` (feat)
2. **Task 2: Render the one status line on the card and pass the dashboard rows through the list** - `1b34e7c` (feat)

**Plan metadata:** (this commit, docs: complete plan)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListViewModel.kt` - `VehicleDashboardRow`, `dashboardDueCount`, the four-flow `combine`/`evaluateAll`/`sortVehicles` pipeline, `catch` moved inside `flatMapLatest`
- `shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/VehicleListViewModelTest.kt` - new test file, 13 tests, hand-written `FakeVehicleRepository`/`FakeReminderRepository`
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleCard.kt` - `top`/`otherDueCount`/`lastServiceEpochMs` params, `vehicleStatusLine()` composable, `STATUS_LINE_SEPARATOR` constant
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt` - `items(state.items, key = { row -> row.vehicle.id })`, passes the three new fields to `VehicleCard`
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` - `VehicleListViewModel(get(), get())`

## Decisions Made

- **`catch{}` relocated inside `flatMapLatest`'s `combine()` block.** The plan's own `<read_first>` pointed at `RemindersViewModel`'s "CR-02 pattern" but the action text's described shape (`refresh.flatMapLatest { combine(...) }.catch { ... }.stateIn(...)`) actually places `catch` *after* `flatMapLatest`. Written literally, that structure terminates the entire `refresh`-driven collector the first time any of the four flows throws, so a later `retry()` bump has nothing left to react to — the "retry re-collects and recovers" test caught this immediately (Rule 1: auto-fixed bug). Moving `catch` to wrap only the inner `combine(...)` flow (matching `RemindersViewModel.observeRemindersUiState`'s actual code, not just its prose description) fixes it: the exception terminates only that one emission's inner flow, and the outer `flatMapLatest` on `refresh` stays alive to restart on the next bump.
- **No defaults on `VehicleCard`'s three new parameters**, per the plan's explicit instruction — the single call site in `VehicleListScreen` makes a missing status a compile error.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `catch` placement inside `flatMapLatest` instead of after it**
- **Found during:** Task 1 (automated test `retryReCollectsAndRecoversAfterAnError`)
- **Issue:** The plan's action text implies the error handler sits after `flatMapLatest` (standard `catch`-then-`stateIn` chaining); that shape permanently ends the `refresh` collection on the first error, so `retry()` becomes a no-op forever after one failure.
- **Fix:** Moved `.catch { emit(VehicleListUiState.Error(...)) }` to attach directly to the `combine(...)` flow *inside* the `flatMapLatest` lambda, matching the already-shipped `RemindersViewModel.observeRemindersUiState` implementation (not just the plan's shorthand description of it).
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListViewModel.kt`
- **Verification:** `retryReCollectsAndRecoversAfterAnError` passes; full Android host suite (199 tests) and iOS simulator suite (261 tests) both green.
- **Committed in:** `dd493ae` (Task 1 commit)

### Verify-script discrepancies (not implementation bugs)

Two of Task 2's grep gates ask for an exact count of `1` for `vehicle_due_counter` and `service_summary_last` in `VehicleCard.kt`. Both resources are used exactly once at the call site, but Kotlin's Compose Resources accessors are extension properties that require an explicit per-name `import` statement (the same convention already used by every other file in this codebase, e.g. `ReminderCopy.kt`, `ServiceSummaryRow.kt`) — so the identifier necessarily appears on two lines: the `import` and the usage. `grep -c` counts matching *lines*, giving `2` for both, not the gate's literal `1`. This is a pre-existing pattern in the codebase, not something introduced here, and there is no way to satisfy the literal grep count without either (a) breaking the codebase's established per-resource import convention, or (b) duplicating the resource call to avoid importing it (which would be strictly worse). All of the *substantive* criteria these gates were checking for — "the date segment appears only through `service_summary_last` + `ServiceDates.display`" and "`+N due` renders only via `vehicle_due_counter`" — are satisfied: each resource is referenced in exactly one place in the actual logic. `maxLines = 1` and `ServiceDates.display` both already resolve to the plan's literal `1` (`ServiceDates` the class is imported, not `ServiceDates.display` the call, so the import line doesn't double-count).

---

**Total deviations:** 1 auto-fixed (1 bug fix). 2 unavoidable verify-script literal-count discrepancies documented above (no code change possible without violating codebase convention).
**Impact on plan:** The `catch` relocation was necessary for correctness — without it, "Try again" on the home list would silently stop working for the rest of the app session after any single transient error. No scope creep; both fixes stay inside the plan's own two files.

## Issues Encountered

None beyond the deviation documented above.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The home dashboard is fully wired: urgency order, status line, and the four-flow read shape are all in place and tested on both Android and iOS.
- Plan 08's permission banner has its insertion point marked with a comment in `VehicleListScreen.kt` (first `LazyColumn` item of the `Content` state) — no placeholder was added, per the plan's explicit instruction not to shift every user's list by 16dp before the feature exists.
- Plan 07 (notifications) and plan 08 (permission ask) do not depend on this plan's internals beyond the existing `ReminderRepository` contract, which is unchanged.

---
*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*

## Self-Check: PASSED

All 6 created/modified files found on disk; both task commits (`dd493ae`, `1b34e7c`) found in `git log`.
