---
phase: 02-service-log
plan: 05
subsystem: service-history
tags: [kotlin-multiplatform, compose-multiplatform, service-history, sort-control, keyed-list, shared-empty-state]
requires:
  - phase: 02-service-log
    plan: 01
    provides: schema v2, ServiceEntryRepository.observeHistory(vehicleId, sort), HistorySort domain type
  - phase: 02-service-log
    plan: 02
    provides: RupiahFormat, the full Phase 2 string set (sort_date, sort_mileage, empty_service_history_*, loading_service_history, error_load_history)
  - phase: 02-service-log
    plan: 03
    provides: ServiceEntryEditRoute (add case entryId = 0L)
  - phase: 02-service-log
    plan: 04
    provides: serviceMetaLine helper, ServiceEntryDetailRoute
provides:
  - EmptyState (shared, parameterized, ui/components/)
  - ServiceHistoryViewModel (flatMapLatest sort switch, VM-only sort state, retry)
  - ServiceHistoryScreen (four-state matrix, keyed LazyColumn, FAB overlay)
  - ServiceHistoryRow (Mist card, serviceMetaLine reuse, trailing cost)
  - HistorySortControl (two-segment Date|Mileage, 48dp)
  - ServiceHistoryRoute(vehicleId) destination + Koin factory
affects:
  - VehicleListScreen (imports the moved EmptyState, passes vehicle icon + three strings)
  - Routes.kt, App.kt, AppModule.kt (new destination wiring)
  - 02-06 (vehicle Detail summary row will navigate to ServiceHistoryRoute)
tech-stack:
  added: []
  patterns:
    - shared parameterized empty state serving two entities (no copy drift)
    - ViewModel-only sort selection re-subscribing through flatMapLatest
    - keyed LazyColumn so a re-sort keeps row state bound to the entry
    - FAB as a Box overlay outside the state `when` (present in every state)
key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/components/EmptyState.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/HistorySortControl.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
  deleted:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt
key-decisions:
  - "EmptyState moved to ui/components/ and parameterized (icon, heading, body, actionLabel, onAction); geometry, roles and palette unchanged; the vehicle call site passes ic_vehicle_car + its three existing keys"
  - "ServiceHistoryViewModel holds the sort in ViewModel state only (default DATE, never persisted, D-34) and re-subscribes via _sort.flatMapLatest { refresh.flatMapLatest { repo.observeHistory(vehicleId, mode) } }"
  - "ServiceHistoryScreen paints exactly four states (Loading/Empty/Content/Error) and the FAB sits outside the when so the add path survives every state (D-36, C-5)"
  - "Content renders HistorySortControl above one LazyColumn with items(entries, key = { it.id }) and a 64dp bottom band; no nested vertical scrolling (T-02-05-01)"
  - "ServiceHistoryRow imports serviceMetaLine from ServiceEntryDetailScreen — zero copies; trailing cost is RupiahFormat or the em dash"
  - "HistorySortControl uses VehicleTypePicker's exact palette (Paper/Ink/Hairline/Mist/Slate) with disabled variants, no icons and no direction arrow (D-34)"
  - "App.kt registers composable<ServiceHistoryRoute> with onAddService -> ServiceEntryEditRoute(vehicleId, 0L) and onOpenEntry -> ServiceEntryDetailRoute(entryId); isTabRoot unchanged (D-20)"
patterns-established:
  - "One EmptyState serves the vehicle list and the service history screen; callers own every word and the visual"
  - "Sort preference is ViewModel state, never storage — asserted by a grep gate that forbids preference APIs in the ViewModel"
  - "History rows are keyed by entry id so switching Date|Mileage reorders data with the row, never state"
requirements-completed: [SVC-01, SVC-05]
duration: 32min
completed: 2026-10-20
---

# Phase 02 Plan 05: Service History Screen Summary

**One-liner:** Sortable, keyed service-history destination with a four-state matrix, an in-memory Date|Mileage switch, a shared parameterized empty state, and an add FAB that survives every state.

## Performance

- **Duration:** ~32 min (12:28 → 13:00 local)
- **Tasks:** 3
- **Files modified:** 10 (5 created + 4 modified + 1 deleted + this SUMMARY.md)
- **Note:** the autonomous executor was interrupted mid-Task-2 at the user's request; the remaining work was completed inline from the frozen tree.

## Accomplishments

- **Shared `EmptyState`** moved from `ui/vehicles/components/` to `ui/components/` and parameterized as `EmptyState(icon: ImageVector, heading: String, body: String, actionLabel: String, onAction: () -> Unit, modifier)`. Geometry, roles and palette are byte-for-byte the previous component: 64dp `Mist` circle, 24dp icon, `titleLarge`/`Ink` heading, `bodyLarge`/`Slate` body, filled `Button`, 24dp column padding. The KDoc now states the two-entity scope (D-15, D-36).
- **`VehicleListScreen`** imports the moved component and passes `vectorResource(Res.drawable.ic_vehicle_car)` plus its three existing string keys — visually unchanged.
- **`ServiceHistoryViewModel`** — `@OptIn(ExperimentalCoroutinesApi::class)`; `_sort` (default `HistorySort.DATE`) and `refresh` compose through nested `flatMapLatest` into `repo.observeHistory(vehicleId, mode)`; `map` collapses an empty emission to `Empty`, `catch` maps failures to `Error(message)`, `stateIn(WhileSubscribed(5_000), Loading)`. `onSortSelected` writes VM state only; `retry()` bumps `refresh`. No preference API anywhere.
- **`ServiceHistoryScreen`** — `ServiceHistoryRoute(vehicleId, onAddService, onOpenEntry, onBack, viewModel = koinViewModel(parameters = { parametersOf(vehicleId) }))`; `Scaffold` + `TopAppBar` (title + back arrow). Four states: labelled `CircularProgressIndicator` (`loading_service_history`), shared `EmptyState` with `Icons.Filled.Build`, sort control + keyed `LazyColumn`, and error copy + `action_try_again`. The FAB is a `Box` overlay outside the `when`, present in every state.
- **`ServiceHistoryRow`** — `Mist` card, 12dp radius, 72dp minimum, merged semantics; title `titleMedium`/`Ink` (one line + ellipsis), meta `serviceMetaLine(...)` `bodyLarge`/`Slate`, trailing `RupiahFormat.formatRupiah` or `value_empty` in `Slate`.
- **`HistorySortControl`** — `SingleChoiceSegmentedButtonRow` with two `SegmentedButton`s, `itemShape(index = 0|1, count = 2)`, 48dp minimum, `VehicleTypePicker`'s exact palette including disabled variants, no icons, no direction arrow.
- **Wiring** — `Routes.kt` adds `ServiceHistoryRoute(vehicleId)`; `App.kt` registers `composable<ServiceHistoryRoute>` with alias import and the two existing destinations; `AppModule.kt` adds the `(vehicleId: Long)` factory. `isTabRoot` stays a two-entry list and no entry point into this screen is introduced here (02-06 owns it).

## Task Commits

1. **Task 1: Move and parameterize the shared empty state and update the vehicle list's call site** — `4e1e6c1` (feat)
2. **Task 2 + Task 3 components: history ViewModel, screen, row and sort control** — `6d975b8` (feat)
3. **Task 3 wiring: route, shell destination and DI factory** — `408e291` (feat)

**Plan metadata:** committed separately as the docs commit that carries this SUMMARY and the state updates.

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/components/EmptyState.kt` (moved from `ui/vehicles/components/`, parameterized)
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryScreen.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/HistorySortControl.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt` (import + parameterized call site)
- `shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` (`ServiceHistoryRoute`)
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` (destination + two callbacks)
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` (`ServiceHistoryViewModel` factory)
- Deleted: `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] The plan's Android verify command names a task that does not exist**
- **Found during:** first compile gate
- **Issue:** `./gradlew :shared:assembleDebug` fails with "task 'assembleDebug' not found in project ':shared'". `:shared` is a KMP library (`com.android.kotlin.multiplatform.library`) and has no `assembleDebug`; this is the same stale-task pattern recorded in STATE.md's version-set knock-on effects.
- **Fix:** used the module's real gates — `:androidApp:assembleDebug` (compiles shared for Android) and `:shared:compileKotlinIosSimulatorArm64`.
- **Verification:** both exit 0.
- **Committed in:** n/a (verification-only change; recorded here so later plans stop copying the stale task).

**2. [Rule 1 - Bug] Raw `stringResource("…")` literals do not compile**
- **Found during:** audit of the frozen Task-2/Task-3 files
- **Issue:** `ServiceHistoryRow` and `HistorySortControl` called `stringResource("value_empty")`, `stringResource("sort_date")`, `stringResource("sort_mileage")`. Compose Resources' API takes a generated `StringResource`, not a `String`.
- **Fix:** `stringResource(Res.string.value_empty)` / `sort_date` / `sort_mileage`, with `Res` and the three key imports added.
- **Files modified:** `ServiceHistoryRow.kt`, `HistorySortControl.kt`
- **Verification:** `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` exit 0; a sweep for `stringResource("` in the three files returns nothing.
- **Committed in:** `6d975b8`

**3. [Rule 1 - Bug] Row column would starve the trailing cost**
- **Found during:** audit of `ServiceHistoryRow`
- **Issue:** the text `Column` used `Modifier.fillMaxWidth()` inside the `Row`, which takes all remaining width and squeezes the trailing cost out of the card.
- **Fix:** `Modifier.weight(1f)` exactly as the plan's action text specifies.
- **Files modified:** `ServiceHistoryRow.kt`
- **Verification:** compile gates exit 0 (layout is a runtime property; the deferred simulator check covers it).
- **Committed in:** `6d975b8`

**4. [Rule 1 - Bug] Unused import in the screen**
- **Found during:** audit of `ServiceHistoryScreen`
- **Issue:** `androidx.compose.ui.unit.sp` imported but unused.
- **Fix:** removed.
- **Files modified:** `ServiceHistoryScreen.kt`
- **Verification:** compile gates exit 0.
- **Committed in:** `6d975b8`

**5. [Rule 3 - Blocking] Executor interruption mid-Task-2**
- **Found during:** takeover after the user stopped the autonomous executor
- **Issue:** Task 1 was committed (`4e1e6c1`); Task 2's files and Task 3's two components were on disk uncommitted, with the three defects above; Task 3's wiring was absent and no SUMMARY existed.
- **Fix:** audited the frozen files, repaired the defects, completed the wiring, ran every gate, and committed in two logical commits.
- **Verification:** all gates below.
- **Committed in:** `6d975b8`, `408e291`

**Total deviations:** 5 (3 blocking/bug repairs, 1 cleanup, 1 process note). Every acceptance criterion implemented as written.

## Auth Gates

None — no authentication required for this plan.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: keyed_rows | ServiceHistoryScreen.kt | `items(entries, key = { it.id })` is the mitigation for T-02-05-01 — a re-sort must move the entry's data with its row |
| threat_flag: sort_not_persisted | ServiceHistoryViewModel.kt | The sort lives in VM state only; a grep gate forbids any preference/storage API in the file (T-02-05-04) |
| threat_flag: shared_empty_state_copy | EmptyState.kt | The component holds no string key; callers pass their own copy, preventing vocabulary drift between the two entities (T-02-05-02) |

## Known Stubs

None. The destination is fully wired; its entry point (the vehicle Detail summary row) is deliberately owned by 02-06, so no stopgap control was introduced.

## Threat Surface Scan

No security-relevant surface beyond the plan's `<threat_model>`:
- No network, no auth path, no new file access; this screen renders no photos and resolves no paths.
- The history read is `observeHistory` only — entry rows, no parts and no photos — so query count stays constant in history length (T-02-05-03).
- `Error` carries only the flow's message and renders fixed copy; no entry field is logged (T-02-05-05).
- No new dependency and no package install (T-02-05-SC).

## Self-Check: PASSED

- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/components/EmptyState.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryScreen.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/components/HistorySortControl.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/App.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt (modified)
- CONFIRMED ABSENT: shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt (moved)
- FOUND commit: 4e1e6c1
- FOUND commit: 6d975b8
- FOUND commit: 408e291
- VERIFIED: `:androidApp:assembleDebug` exits 0
- VERIFIED: `:shared:compileKotlinIosSimulatorArm64` exits 0
- VERIFIED: `:shared:testAndroidHostTest` exits 0
- VERIFIED: `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` exits 0
- VERIFIED: all grep gates from the plan's verification section pass (VM shape, sort-not-persisted, list shape, states, no gestures, meta reuse, sort control, wiring, entry points)

---

*Phase: 02-service-log*
*Completed: 2026-10-20*
