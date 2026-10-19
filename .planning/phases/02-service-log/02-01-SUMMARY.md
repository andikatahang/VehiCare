---
phase: 02-service-log
plan: 01
subsystem: database
tags: [sqldelight, sqlite, migration, repository, kotlin-multiplatform, coroutines, ios-native-driver]
requires:
  - phase: 01-foundation-vehicle-management
    provides: schema v1 (vehicle table), committed 1.db baseline + CI migration guards, serialized IO lane pattern, generated-row mapper boundary, real-driver iOS test discipline
provides:
  - schema v2 (service_entry / service_part / service_photo + 4 indices) in ServiceEntry.sq and byte-identical migrations/1.sqm with regenerated committed 2.db
  - the 21 named queries every later 02 plan consumes, including the four vehicle-cascade statements
  - ServiceEntry / ServicePart / ServiceSummary / HistorySort / ServiceTypePresets domain types
  - ServiceEntryEntityMapper (entry/part/photo rows, SelectSummary, SelectLearnedTypes, ServiceEntryBindParameters)
  - ServiceEntryRepository (+impl) with every read/write shape the UI plans need
affects:
  - 02-service-log plans 02-03…02-06 (form, detail/delete, history, summary row)
  - Phase 3 reminders (key on the stable stored service-type string, D-40)
tech-stack:
  added: []
  patterns:
    - parent + ordered children written in one transactionWithResult with lastInsertId() inside the transaction
    - edit fully replaces child rows so no orphan parts/photos survive
    - read photo paths in the transaction → delete children → delete parent → delete files after commit
    - both history orders end with id DESC so equal keys never reorder ambiguously
    - learned types = one cross-vehicle GROUP BY lower(service_type) query + case-insensitive preset filter in Kotlin
key-files:
  created:
    - shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq
    - shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm
    - shared/src/commonMain/sqldelight/databases/2.db
    - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/ServicePart.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceSummary.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/HistorySort.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceTypePresets.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt
    - shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/domain/ServiceTypeVocabularyTest.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt
  modified: []
key-decisions:
  - "Generated row types are Service_entry / Service_part / Service_photo (SQLDelight derives the class name from the table name); the mapper aliases them as ServiceEntryEntity / ServicePartEntity / ServicePhotoEntity, mirroring the Phase 1 Vehicle→VehicleEntity decision."
  - "observePreviousMileage derives the entry's own stored date from selectById first, then binds the self-excluding query — the interface stays (vehicleId, entryId) with no date parameter leaking to the UI."
  - "ServiceTypePresets.filterLearned only filters; it never rewrites stored labels (D-40). Learned-types query stays cross-vehicle (D-25)."
  - "The photoDeleter default remains a no-op in this plan: DI wiring to PhotoStorage::delete is explicitly plan 02-03's change, while tests inject a recording deleter."
patterns-established:
  - "Three-artifact schema change: .sq DDL + byte-identical 1.sqm + regenerated 2.db committed together; both CI guards green locally."
  - "Child-table save/replace inside one transactionWithResult on the injected Dispatchers.IO.limitedParallelism(1) lane."
requirements-completed: [SVC-01, SVC-02, SVC-03, SVC-05, SVC-06, SVC-07]
duration: 11min
completed: 2026-10-19
---

# Phase 2 Plan 1: Service-Log Data Core Summary

**Schema v2 (entry + ordered parts/photos, migration-guarded) with a serialized-lane repository whose transactional save, child replacement, deterministic history orders, learned-type vocabulary and read-rows-then-delete-files delete are proven on a real iOS-simulator database.**

## Performance

- **Duration:** ~11 min
- **Started:** 2026-10-19T13:51Z (session start)
- **Completed:** 2026-10-19T14:02Z
- **Tasks:** 3
- **Files modified:** 14 created (plus this SUMMARY.md)

## Accomplishments

- Shipped schema v2 — `service_entry`, ordered `service_part` / `service_photo`, four uniquely named indices — in `ServiceEntry.sq` and a byte-identical `migrations/1.sqm`, with the committed `2.db` baseline regenerated and `1.db` untouched. `verifyCommonMainVehiCareDatabaseMigration` and the regenerate-then-`git diff --exit-code` guard both pass locally; no CI change was added.
- Added the 21 named queries every later plan consumes, including the four `*ForVehicle` cascade statements so plan 02-04 consumes rather than authors them.
- Added the pure domain vocabulary: five domain types with zero SQLDelight imports, the six D-23 presets in locked order, and the case-insensitive learned filter (D-24/D-25/D-40).
- Built `ServiceEntryRepositoryImpl` on the existing serialized lane: transactional add/edit with `lastInsertId()` inside the transaction, full child replacement on edit, both deterministic history orders, composed entry reads, mileage prefill/previous reads, and a delete that hands photo paths to the deleter only after the rows are durable.
- Proved every shape on a real `NativeSqliteDriver`: 8 repository tests, 7 mapper tests and 4 vocabulary tests (also executed on the Android host target via `testAndroidHostTest`).

## Task Commits

Each task was committed atomically:

1. **Task 1: Ship schema v2 — ServiceEntry.sq, migrations/1.sqm, regenerated 2.db** — `800b521` (feat)
2. **Task 2: Domain models, presets vocabulary, mapper + unit tests** — `1b77267` (feat)
3. **Task 3: ServiceEntryRepository interface + implementation + real-driver iOS tests** — `77690e4` (feat)

**Plan metadata:** committed separately as the docs commit that carries this SUMMARY and the state updates.

## Files Created/Modified

- `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq` — v2 DDL (3 tables, 4 indices) + all 21 named queries; preamble comment, no `PRAGMA` (C-1)
- `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm` — v1 → v2 delta, byte-identical DDL
- `shared/src/commonMain/sqldelight/databases/2.db` — regenerated committed baseline (generated, never hand-edited)
- `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt` — entry model, `id = 0L` = not persisted
- `shared/src/commonMain/kotlin/com/vehicare/domain/ServicePart.kt` — part name + quantity (default 1)
- `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceSummary.kt` — count + last service date aggregate
- `shared/src/commonMain/kotlin/com/vehicare/domain/HistorySort.kt` — `DATE | MILEAGE`, never persisted (D-34)
- `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceTypePresets.kt` — six D-23 labels + `filterLearned`
- `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt` — the only generated-type meeting point + `ServiceEntryBindParameters`
- `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt` — eight-member storage contract
- `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt` — serialized lane, transactions, delete ordering
- `shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt` — 7 mapper cases
- `shared/src/commonTest/kotlin/com/vehicare/domain/ServiceTypeVocabularyTest.kt` — 4 preset/filter cases
- `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt` — 8 real-driver cases

## Decisions Made

- Generated row classes are `Service_entry` / `Service_part` / `Service_photo`; aliases in the mapper keep the planned `*Entity` names and the generated-type boundary exactly where Phase 1 put it.
- `observePreviousMileage(vehicleId, entryId)` reads the entry's own stored date through `selectById` and then binds `selectPreviousMileage` with it; the interface keeps the date out of the UI and the self-exclusion (`id !=` + `id <` tie-break) stays in SQL.
- The `photoDeleter` stays a no-op default here because the plan assigns the `PhotoStorage::delete` wiring to 02-03's DI change; the delete test injects a recording deleter, so ordering is already proven.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Generated row type names differ from the plan's assumed names**
- **Found during:** Task 2 (mapper + tests)
- **Issue:** SQLDelight derives row class names from the table name, producing `com.vehicare.db.Service_entry` / `Service_part` / `Service_photo` — the plan's assumed `ServiceEntry` class does not exist.
- **Fix:** Aliased the real generated names (`import com.vehicare.db.Service_entry as ServiceEntryEntity`, plus part/photo) in the mapper and constructed the real types in tests. This is the same class-name derivation that produced the Phase 1 `VehicleEntity` alias.
- **Files modified:** `data/ServiceEntryEntityMapper.kt`, `commonTest/.../ServiceEntryEntityMapperTest.kt`
- **Verification:** `:shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test` green.
- **Committed in:** `1b77267` (Task 2 commit)

**2. [Rule 1 - Bug] A doc comment containing the literal `RETURNING` broke the plan's own verification grep**
- **Found during:** Task 3 (repository shape verification)
- **Issue:** The explanatory comment "`RETURNING` is unavailable at minSdk 26" matched the plan's required negative grep `! grep -q 'RETURNING'`.
- **Fix:** Reworded the comment to describe `last_insert_rowid()` without the banned token; no code change.
- **Files modified:** `data/ServiceEntryRepositoryImpl.kt`
- **Verification:** `REPO_SHAPE_OK` verification chain exits 0; both test tasks re-run green.
- **Committed in:** `77690e4` (Task 3 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 bug)
**Impact on plan:** Both are name/verification-reality corrections with no scope change; every planned behaviour and acceptance criterion is implemented as written.

## Issues Encountered

- Two compile-time fixes while authoring the iOS test: the cinterop opt-in import needed to be `kotlinx.cinterop.ExperimentalForeignApi` (the `kotlin.` form does not resolve), and `assertEquals` needed an explicit `listOf<String?>` element type to compare against the `MutableList<String?>` of recorded deleter paths. Both fixed before the first green run; no production code affected.

## Known Stubs

- `ServiceEntryRepositoryImpl`'s `photoDeleter` default is a no-op. This is the intentional seam the plan assigns to **plan 02-03's DI change** (`PhotoStorage::delete` wiring); this plan's delete test injects a recording deleter and proves paths are handed over only after the rows are gone, so the ordering contract is real, not stubbed UI data. No other stub exists in this plan.

## Threat Surface Scan

No security-relevant surface beyond the plan's `<threat_model>`: no network, no auth path, no new file access beyond the existing `PhotoStorage` seam, and all SQL is compile-time typed with named binds (no string-built SQL).

## Next Phase Readiness

- `ServiceEntryRepository` is ready for 02-02 (dates/format utils + strings) and for the form/detail/history slices in 02-03…02-06; no signature changes should be needed.
- The vehicle-cascade queries are already in `ServiceEntry.sq`, so 02-04 only extends `VehicleRepositoryImpl.delete`.
- Deferred to phase UAT (recorded in the plan): the manual Android v1→v2 in-place upgrade check on the `Medium_Phone` AVD — CI has no emulator job.

## Self-Check: PASSED

- FOUND: shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq
- FOUND: shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm
- FOUND: shared/src/commonMain/sqldelight/databases/2.db
- FOUND: shared/src/commonMain/sqldelight/databases/1.db
- FOUND: shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/domain/ServicePart.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/domain/ServiceSummary.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/domain/HistorySort.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/domain/ServiceTypePresets.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt
- FOUND: shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt
- FOUND: shared/src/commonTest/kotlin/com/vehicare/domain/ServiceTypeVocabularyTest.kt
- FOUND: shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt
- FOUND commit: 800b521
- FOUND commit: 1b77267
- FOUND commit: 77690e4

---
*Phase: 02-service-log*
*Completed: 2026-10-19*
