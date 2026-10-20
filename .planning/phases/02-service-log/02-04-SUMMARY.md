---
phase: 02-service-log
plan: 04
subsystem: service-entry-detail-delete
tags: [kotlin-multiplatform, compose-multiplatform, sqldelight, koin, delete-cascade, ios-test]
requires:
  - phase: 02-service-log
    plan: 01
    provides: schema v2, ServiceEntryRepository, cascade queries, domain types
  - phase: 02-service-log
    plan: 02
    provides: ServiceDates, RupiahFormat, string set, kotlinx-datetime
  - phase: 02-service-log
    plan: 03
    provides: ServiceEntryEditRoute, ServiceEntryEditViewModel, ServicePhotoStrip, PhotoStorage directory param, DI binding
provides:
  - ServiceEntryDetailRoute (read-only destination)
  - ServiceEntryDetailViewModel (streaming state + single-flight delete)
  - ConfirmDeleteDialog (parameterized, shared by vehicle + entry)
  - FieldRow (moved to ui/components/, C-23)
  - VehicleRepositoryImpl.delete cascade (D-39)
  - ServiceEntryDeletionTest (iosTest, real DB + filesystem)
affects:
  - VehicleDetailScreen (imports moved components, parameterized dialog)
  - Routes.kt, App.kt, AppModule.kt (new destination wiring)
  - 02-05 (history screen will import serviceMetaLine)
tech-stack:
  added: []
  patterns:
    - shared parameterized confirmation dialog (single-path rule)
    - moved shared FieldRow component (C-23)
    - ViewModel streaming state with one-shot failure flag
    - child-first delete cascade inside transaction, files after commit
    - shell-hosted success snackbar for popped screens
key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/components/ConfirmDeleteDialog.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/components/FieldRow.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceMetaLineTest.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
  deleted:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ConfirmDeleteDialog.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/FieldRow.kt
key-decisions:
  - "ConfirmDeleteDialog parameterized with title/message/confirmLabel (caller-resolved); vehicleName param removed; action_cancel stays internal; KDoc single-path warning now names both entities"
  - "FieldRow moved to ui/components/ unchanged (body + KDoc); VehicleDetailScreen import updated; zero ui.vehicles.components.FieldRow refs remain (C-23)"
  - "VehicleRepositoryImpl.delete cascade: select vehicle photo + service photo paths → delete photos → parts → entries → vehicle in one transaction → forEach photoDeleter after; no RETURNING, serialized lane unchanged (D-39)"
  - "ServiceEntryDetailViewModel mirrors VehicleDetailViewModel: observeEntry → Loading/NotFound/Content, single-flight delete, one-shot deleteFailed, CancellationException rethrown first"
  - "ServiceEntryDetailScreen locked anatomy: focal block (Display type + serviceMetaLine), lg break, Cost divider, Parts omitted when empty, Workshop divider, Mechanic divider, stacked Notes no divider, Photos omitted when empty (read-only strip), 2xl break, destructive OutlinedButton"
  - "serviceMetaLine internal helper in ServiceEntryDetailScreen (single source for date·mileage); 02-05 will import it"
  - "App.kt registers ServiceEntryDetailRoute with shell snackbar (snackbar_entry_deleted), Edit navigates to ServiceEntryEditRoute"
  - "ServiceEntryDeletionTest proves: rows + files, isolation, idempotence, ViewModel ordering (success after repo returns), single-flight, failure retention, vehicle cascade (vehicle + entry photos all gone, survivor untouched)"
patterns-established:
  - "One ConfirmDeleteDialog serves both vehicle and entry deletion — cancel is Ink, confirm is Danger, onDismissRequest = onDismiss, 48dp buttons"
  - "One FieldRow serves both Detail screens — moved per C-23, zero copies"
  - "serviceMetaLine is the single date·mileage format source for entry detail and history row"
  - "Vehicle cascade delete is data-layer only; UI dialog sentence already covers 'all its records' (D-39)"
  - "Deletion test pattern: RecordingRepository decorator + StandardTestDispatcher + file assertions on real simulator filesystem"
requirements-completed: [SVC-06, SVC-07]
duration: 45min
completed: 2026-10-20
---

# Phase 02 Plan 04: Service Entry Detail & Delete Summary

**One-liner:** Read-only Service entry detail destination with locked anatomy, shared ConfirmDeleteDialog for both entities, D-39 vehicle cascade delete, and real-driver iOS test proving rows + files move together.

## Performance

- **Duration:** ~45 min
- **Started:** 2026-10-20T12:00Z (session start)
- **Completed:** 2026-10-20T12:45Z
- **Tasks:** 3
- **Files modified:** 15 (6 created + 5 modified + 2 deleted + this SUMMARY.md)

## Accomplishments

- **Shared ConfirmDeleteDialog** moved from `ui/vehicles/components/` to `ui/components/` and parameterized with `title`, `message`, `confirmLabel` (caller-resolved via `stringResource`). The dialog's guarantees unchanged: `onDismissRequest = onDismiss`, `Paper` container, 48dp buttons, `Ink` Cancel, `Danger` confirm, KDoc single-path warning now names both vehicle and entry deletion.
- **Shared FieldRow** moved to `ui/components/` unchanged (C-23). `VehicleDetailScreen` import updated; zero `ui.vehicles.components.FieldRow` references remain in `commonMain`.
- **Vehicle cascade (D-39)** implemented in `VehicleRepositoryImpl.delete`: inside one `transactionWithResult`, reads vehicle photo + all service entry photo paths, deletes child-first (photos → parts → entries → vehicle), returns collected paths, calls `photoDeleter` once per path after transaction commits. No `RETURNING`, serialized `IO.limitedParallelism(1)` lane unchanged.
- **ServiceEntryDetailViewModel** — faithful port of `VehicleDetailViewModel`: streaming `observeEntry` → `Loading`/`NotFound`/`Content`, single-flight `delete` with `deleting` guard, one-shot `deleteFailed` retired by `onDeleteErrorShown`, `CancellationException` rethrown first.
- **ServiceEntryDetailScreen** — locked read-only anatomy per UI-SPEC: focal block (Display service type + `serviceMetaLine` date·mileage), `lg` 24px break, field run (Cost divider, Parts omitted when empty, Workshop divider, Mechanic divider, stacked Notes no divider), Photos omitted when empty (read-only `ServicePhotoStrip`), `2xl` 48px break, destructive `OutlinedButton` (Delete service entry). Confirmation gate routes only through `ConfirmDeleteDialog` with entry copy (`dialog_delete_entry_title`/`message`/`action_delete_service_entry`).
- **serviceMetaLine** internal helper (`dateEpochMs`, `mileageKm` → `"19 Oct 2026 · 123.456 km"`) — single source for entry detail and future history row (02-05 imports it). Unit tested in `ServiceMetaLineTest` (commonTest) asserting literal format, zero mileage, middle-dot separator.
- **Destination wiring**: `Routes.kt` adds `ServiceEntryDetailRoute(entryId)`, `App.kt` registers `composable<ServiceEntryDetailRoute>` with alias import, shell-hosted `snackbar_entry_deleted` success message, `Edit` → `ServiceEntryEditRoute(vehicleId, entryId)`. `AppModule.kt` adds `ServiceEntryDetailViewModel` factory `(entryId: Long)`.
- **ServiceEntryDeletionTest** (iosTest, 7 cases): proves repository deletes entry + part/photo rows + photo files; isolation (sibling entry untouched); idempotence (unknown id no-op); ViewModel ordering (success only after repo returns, row + files gone inside `onDeleted`); single-flight (second tap dropped); failure (entry stays observable, `deleteFailed` set, `onDeleteErrorShown` retires flag); vehicle cascade D-39 (vehicle + 2 entries + photos + parts + all 3 photo files gone, survivor untouched).
- All verification gates pass: Android compile, iOS compile, host tests, iOS simulator tests, grep gates for moved dialog/FieldRow, parameterized dialog, cascade queries, schema baseline untouched.

## Task Commits

Each task was committed atomically:

1. **Task 1: Move shared dialog and FieldRow to ui/components/, add D-39 vehicle cascade** — `1dec26c` (feat)
2. **Task 2: Add ServiceEntryDetailViewModel, ServiceEntryDetailScreen, and serviceMetaLine test** — `9c11f90` (feat)
3. **Task 3: Register entry detail route, wire DI, and add ServiceEntryDeletionTest** — `666cfef` (feat)

**Plan metadata:** committed separately as the docs commit that carries this SUMMARY and the state updates.

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/components/ConfirmDeleteDialog.kt` (moved from `ui/vehicles/components/`, parameterized)
- `shared/src/commonMain/kotlin/com/vehicare/ui/components/FieldRow.kt` (moved from `ui/vehicles/components/`, body unchanged — C-23)
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailViewModel.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt`
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceMetaLineTest.kt`
- `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` (imports updated, parameterized dialog call)
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` (D-39 cascade in `delete`)
- `shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` (ServiceEntryDetailRoute)
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` (destination + shell snackbar + Edit wiring)
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` (ServiceEntryDetailViewModel factory)
- Deleted: `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ConfirmDeleteDialog.kt`, `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/FieldRow.kt`

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Dispatchers.IO internal access in VehicleRepositoryImpl**
- **Found during:** Task 1 (iOS compilation)
- **Issue:** `Dispatchers.IO` is internal in the pinned kotlinx.coroutines version; direct reference fails compilation on Kotlin/Native targets.
- **Fix:** Added explicit `import kotlinx.coroutines.IO` (matching the pattern in `ServiceEntryRepositoryImpl.kt`).
- **Files modified:** `VehicleRepositoryImpl.kt`
- **Verification:** `:shared:compileKotlinIosSimulatorArm64` exits 0.
- **Committed in:** `1dec26c` (Task 1 commit)

**2. [Rule 1 - Bug] Missing string resource imports in VehicleDetailScreen**
- **Found during:** Task 1 (iOS compilation)
- **Issue:** The parameterized dialog call uses `dialog_delete_title` and `dialog_delete_message` which were not imported.
- **Fix:** Added imports for `dialog_delete_title` and `dialog_delete_message` from `vehicare.shared.generated.resources.Res`.
- **Files modified:** `VehicleDetailScreen.kt`
- **Verification:** `:shared:compileKotlinIosSimulatorArm64` exits 0.
- **Committed in:** `1dec26c` (Task 1 commit)

**3. [Rule 1 - Bug] ConfirmDeleteDialog missing Res import**
- **Found during:** Task 1 (iOS compilation)
- **Issue:** The moved dialog uses `stringResource(Res.string.action_cancel)` but didn't import `Res` or `action_cancel`.
- **Fix:** Added imports for `Res` and `action_cancel`.
- **Files modified:** `ConfirmDeleteDialog.kt`
- **Verification:** `:shared:compileKotlinIosSimulatorArm64` exits 0.
- **Committed in:** `1dec26c` (Task 1 commit)

**4. [Rule 1 - Bug] ServiceEntryDetailScreen semantics modifier misuse**
- **Found during:** Task 2 (iOS compilation)
- **Issue:** Attempted to use `semantics { contentDescription = ... }` on `CircularProgressIndicator` but the API doesn't support composable invocations in that context.
- **Fix:** Removed the semantics modifier (the `VehicleDetailScreen` analog also omits it; content description is not load-bearing for this phase).
- **Files modified:** `ServiceEntryDetailScreen.kt`
- **Verification:** `:shared:compileKotlinIosSimulatorArm64` exits 0.
- **Committed in:** `9c11f90` (Task 2 commit)

**5. [Rule 1 - Bug] Test query access via wrong database property**
- **Found during:** Task 3 (iOS test compilation)
- **Issue:** Test referenced `database.servicePartQueries` and `database.servicePhotoQueries` but the generated database exposes these queries under `database.serviceEntryQueries.selectParts` and `selectPhotos`.
- **Fix:** Updated all test assertions to use `database.serviceEntryQueries.selectParts(id)` and `selectPhotos(id)`.
- **Files modified:** `ServiceEntryDeletionTest.kt`
- **Verification:** `:shared:compileTestKotlinIosSimulatorArm64` and `:shared:iosSimulatorArm64Test` exit 0.
- **Committed in:** `666cfef` (Task 3 commit)

**6. [Rule 1 - Bug] Vehicle entity update in test used non-existent copy parameters**
- **Found during:** Task 3 (iOS test compilation)
- **Issue:** Test attempted `vehicle.copy(photoPath = ...)` and `type.toDb()` but `Vehicle` data class doesn't have those in its copy signature; `type` is already a `VehicleType` enum.
- **Fix:** Fetch the generated vehicle entity row and use `database.vehicleQueries.update` with all named parameters including the new `photo_path`.
- **Files modified:** `ServiceEntryDeletionTest.kt`
- **Verification:** `:shared:compileTestKotlinIosSimulatorArm64` and `:shared:iosSimulatorArm64Test` exit 0.
- **Committed in:** `666cfef` (Task 3 commit)

**Total deviations:** 6 auto-fixed (all bugs, no scope changes; every acceptance criterion implemented as written).

## Auth Gates

None — no authentication required for this plan.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: delete_cascade | VehicleRepositoryImpl.kt | D-39 cascade deletes vehicle + all service entries + parts + photos + files in one transaction; correctness critical for data integrity |
| threat_flag: single_flight_delete | ServiceEntryDetailViewModel.kt | Single-flight guard prevents double-delete enqueuing; confirmed by test |
| threat_flag: confirmation_gate_bypass | ConfirmDeleteDialog.kt | KDoc warns this dialog is the only path to destructive ViewModel calls for both entities; bypass would violate T-02-04-04 |

## Known Stubs

None — all components fully implemented and wired. The `photoDeleter` seam in repositories is now wired to `PhotoStorage::delete` in both `VehicleRepositoryImpl` and `ServiceEntryRepositoryImpl` via `AppModule`.

## Threat Surface Scan

No security-relevant surface beyond the plan's `<threat_model>`:
- No network, no auth path, no new file access beyond existing `PhotoStorage` seam
- All SQL is compile-time typed with named binds (no string-built SQL)
- The D-39 cascade is implemented exactly as specified in the threat model's mitigation for T-02-04-01
- File deletion only after transaction commit mitigates T-02-04-02
- Single-flight guard in ViewModel mitigates T-02-04-03
- Confirmation dialog as only call site mitigates T-02-04-04
- Transaction atomicity + file deletion after commit mitigates T-02-04-05
- `deleteFailed` is boolean, no entry data logged, mitigates T-02-04-06

## Self-Check: PASSED

- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/components/ConfirmDeleteDialog.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/components/FieldRow.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailViewModel.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt
- FOUND: shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceMetaLineTest.kt
- FOUND: shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/App.kt (modified)
- FOUND: shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt (modified)
- FOUND commit: 1dec26c
- FOUND commit: 9c11f90
- FOUND commit: 666cfef
- VERIFIED: `:shared:compileKotlinIosSimulatorArm64` exits 0
- VERIFIED: `:shared:compileAndroidMain` exits 0
- VERIFIED: `:shared:testAndroidHostTest` exits 0
- VERIFIED: `:shared:iosSimulatorArm64Test` exits 0
- VERIFIED: `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` exits 0
- VERIFIED: All grep gates from plan verification section pass

---

*Phase: 02-service-log*
*Completed: 2026-10-20*