---
phase: 02-service-log
plan: 06
subsystem: vehicle-detail-service-summary
tags: [kotlin-multiplatform, compose-multiplatform, aggregate-query, navigation, placeholder-retirement]
requires:
  - phase: 02-service-log
    plan: 01
    provides: ServiceEntryRepository.observeSummary(vehicleId) + ServiceSummary domain type
  - phase: 02-service-log
    plan: 02
    provides: detail_service_history, service_summary_last, service_summary_none and the service_summary_entries plural
  - phase: 02-service-log
    plan: 05
    provides: ServiceHistoryRoute destination
provides:
  - ServiceSummaryViewModel (single aggregate read, null while unresolved)
  - ServiceSummaryRow (tappable summary row, navigation only)
  - VehicleDetailScreen onOpenServiceHistory parameter
  - ComingSoonSlot retirement
affects:
  - VehicleDetailScreen (summary row at the former placeholder band, injected history callback)
  - App.kt (onOpenServiceHistory -> ServiceHistoryRoute)
  - AppModule.kt (ServiceSummaryViewModel factory)
  - strings.xml (detail_service_history_coming_soon retired)
tech-stack:
  added: []
  patterns:
    - one aggregate read per vehicle on the Detail screen (no N+1)
    - null-as-unresolved rendering: title alone, never a spinner inside a compact row
    - placeholder retirement in one change (file + call site + string)
key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceSummaryViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
    - shared/src/commonMain/composeResources/values/strings.xml
  deleted:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ComingSoonSlot.kt
key-decisions:
  - "ServiceSummaryViewModel reads exactly one aggregate (COUNT + MAX(date)) and starts at null; a catch also resolves to null, so an unreadable summary renders the title alone rather than a fourth surface"
  - "ServiceSummaryRow renders the title always and the meta line only once resolved: service_summary_none at zero entries, otherwise the plural count joined by ' · ' to service_summary_last(ServiceDates.display(latest))"
  - "A positive count with a null date degrades to the count phrase alone — never a broken sentence (T-02-06-02)"
  - "The row is navigation only and tappable at zero entries: no add control, no entry list, no photo count (D-32/D-36)"
  - "VehicleDetailScreen takes onOpenServiceHistory and resolves the summary by parametersOf(vehicleId); it reads no service tables itself"
  - "ComingSoonSlot.kt and detail_service_history_coming_soon were retired in the same change; the 48px breaks above and below are untouched"
patterns-established:
  - "Detail-screen aggregate: one query, nullable unresolved state, no per-entry read path"
  - "Placeholder retirement: delete the component, remove its only string and swap its only call site in one commit; a tree-wide grep proves no reference survives"
requirements-completed: [SVC-05]
duration: 15min
completed: 2026-10-20
---

# Phase 02 Plan 06: Vehicle Detail Service Summary Row Summary

**One-liner:** The vehicle Detail's reserved band now carries a one-query summary row — `N entries · Last service <date>` or `No entries yet` — that opens the full history, and the Phase 1 coming-soon placeholder is gone.

## Performance

- **Duration:** ~15 min
- **Tasks:** 2
- **Files modified:** 7 (2 created + 4 modified + 1 deleted + this SUMMARY.md)

## Accomplishments

- **`ServiceSummaryViewModel`** — `summary: StateFlow<ServiceSummary?>` from `repo.observeSummary(vehicleId)`, widened to nullable, `catch { emit(null) }`, `stateIn(WhileSubscribed(5_000), initialValue = null)`. This is the Detail screen's only service read: one aggregate (count + newest date), so Detail cost stays constant in history length (Pitfall 4 / T-02-06-01).
- **`ServiceSummaryRow`** — `Mist` container at 12dp radius, `heightIn(min = 72.dp)`, clipped ripple, merged semantics, 16dp padding, `Slate` 24dp `KeyboardArrowRight`. Title `detail_service_history` always renders; the meta line renders only once the summary resolves. Zero entries → `service_summary_none`; populated → `pluralStringResource(service_summary_entries, count, count) + " · " + stringResource(service_summary_last, ServiceDates.display(latest))`. Navigation only and tappable at zero entries.
- **Vehicle Detail wiring** — `onOpenServiceHistory: () -> Unit` added to the screen and threaded into `VehicleDetailContent`; the summary is resolved with `koinViewModel<ServiceSummaryViewModel>(parameters = { parametersOf(vehicleId) })`; `ServiceSummaryRow(summary = summary, onClick = onOpenServiceHistory)` sits at exactly the former `ComingSoonSlot()` position with both `Spacer(48.dp)` breaks untouched. The screen's KDoc now describes the summary row instead of the reserved slot.
- **Navigation** — `App.kt` passes `onOpenServiceHistory = { navController.navigate(ServiceHistoryRoute(detailRoute.id)) }` on the existing `composable<VehicleDetailRoute>`; no new destination and the shell snackbar host are unchanged.
- **Placeholder retirement** — `ComingSoonSlot.kt` deleted and `detail_service_history_coming_soon` removed from `strings.xml` in the same change; `detail_service_history` and the three summary keys remain. A tree-wide grep for `ComingSoonSlot|detail_service_history_coming_soon` returns nothing.
- **The placeholder's own prediction held.** Its KDoc said: *"When Phase 2 lands it replaces or wraps this slot — nothing outside this file needs to change."* It held: exactly one call site and one string had to move, and the file itself was deleted.

## Task Commits

1. **Task 1: The aggregate ViewModel and the tappable summary row, registered in DI** — `8a37674` (feat)
2. **Task 2: Install the row in the vehicle Detail, inject the history navigation, and delete the placeholder and its string** — `ed66c8f` (feat)

**Plan metadata:** committed separately as the docs commit that carries this SUMMARY and the state updates.

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceSummaryViewModel.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` (callback + summary resolution + row at the placeholder band + KDoc)
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` (`onOpenServiceHistory` navigation)
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` (`ServiceSummaryViewModel` factory)
- `shared/src/commonMain/composeResources/values/strings.xml` (`detail_service_history_coming_soon` retired)
- Deleted: `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ComingSoonSlot.kt`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] The plan's Android verify command names a task that does not exist**
- **Found during:** first compile gate
- **Issue:** `./gradlew :shared:assembleDebug` fails — `:shared` is a KMP library and has no `assembleDebug` (same stale task recorded in 02-05's SUMMARY and in STATE.md's version-set knock-on effects).
- **Fix:** used the real gates — `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`, `:shared:testAndroidHostTest`.
- **Verification:** all exit 0.
- **Committed in:** n/a (verification-only; recorded so 02-06 is the last plan to inherit the stale command).

**2. [Rule 1 - Bug] `emit(null)` on a non-null flow does not compile**
- **Found during:** Task 1 compile
- **Issue:** `repo.observeSummary(vehicleId)` is `Flow<ServiceSummary>`, so `.catch { emit(null) }` fails with `NULL_FOR_NONNULL_TYPE`.
- **Fix:** widen the type before the catch: `.map<ServiceSummary, ServiceSummary?> { it }.catch { emit(null) }`.
- **Files modified:** `ServiceSummaryViewModel.kt`
- **Verification:** `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` exit 0.
- **Committed in:** `8a37674`

**3. [Rule 1 - Bug] A stale comment kept the retired symbol in the tree**
- **Found during:** Task 2 grep gate (`PLACEHOLDER_RETIRED_OK` failed)
- **Issue:** a `strings.xml` comment still read "the existing coming-soon key stays until ComingSoonSlot.kt is deleted (plan 02-05)", so the tree-wide gate for `ComingSoonSlot` still matched.
- **Fix:** removed the now-obsolete comment; the key itself had already been retired.
- **Files modified:** `strings.xml`
- **Verification:** the placeholder gate passes; no reference to either symbol remains under `shared/src/`.
- **Committed in:** `ed66c8f`

**Total deviations:** 3 (1 blocking task-path correction, 2 bug/cleanup repairs). Every acceptance criterion implemented as written.

## Auth Gates

None — no authentication required for this plan.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: aggregate_only_read | ServiceSummaryViewModel.kt | The Detail screen's only service read is one aggregate; a per-entry read here would reintroduce the N+1 the plan forbids (T-02-06-01) |
| threat_flag: null_is_unresolved | ServiceSummaryRow.kt | `null` renders the title alone; the row never guesses a sentence from a missing aggregate (T-02-06-02) |
| threat_flag: dead_copy_retirement | ComingSoonSlot.kt / strings.xml | File, call site and string were retired together; a surviving reference would ship dead copy or break the build (T-02-06-03) |

## Known Stubs

None. The phase's last integration point is now real: every SVC requirement is reachable through shipped UI.

## Threat Surface Scan

No security-relevant surface beyond the plan's `<threat_model>`:
- Only a count and a date cross the summary boundary; no entry field is logged (T-02-06-04).
- `observeSummary` is the 02-01 aggregate query — count + `MAX(service_date_epoch_ms)` — so query count is constant in history length.
- No new dependency, no package install, no new file access (T-02-06-SC).
- The schema baseline and the migration guard are untouched.

## Self-Check: PASSED

- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceSummaryViewModel.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/App.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt (modified)
- FOUND: shared/src/commonMain/composeResources/values/strings.xml (modified)
- CONFIRMED ABSENT: shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ComingSoonSlot.kt (deleted)
- FOUND commit: 8a37674
- FOUND commit: ed66c8f
- VERIFIED: `:androidApp:assembleDebug` exits 0
- VERIFIED: `:shared:compileKotlinIosSimulatorArm64` exits 0
- VERIFIED: `:shared:testAndroidHostTest` exits 0
- VERIFIED: `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` exits 0
- VERIFIED: all grep gates from the plan's verification section pass (summary VM, summary row, DI, placeholder retired tree-wide, detail wired, nav, strings)

---

*Phase: 02-service-log*
*Completed: 2026-10-20*
