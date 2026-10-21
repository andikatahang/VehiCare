---
phase: 02-service-log
plan: 07
subsystem: service-log-schema-and-repository
tags: [kotlin-multiplatform, sqldelight, schema-v3, migration-backfill, normalized-child-table, gap-closure]
gap_closure: true
requires:
  - phase: 02-service-log
    plan: 01
    provides: ServiceEntryRepository + mapper boundary, 1.db/2.db baselines, migration verify + regenerate-diff guards
  - phase: 02-service-log
    plan: 03
    provides: ServiceEntryFormValues/toServiceEntry seam and the edit ViewModel's toFormValues
provides:
  - schema v3 with the normalized service_entry_type child table
  - 2.sqm v2-to-v3 backfill (one label per legacy entry at position 0, verbatim)
  - regenerated and committed 3.db baseline
  - ServiceEntry.serviceTypes ordered non-empty list on the only UI-visible model
  - position-0 mirror sync (serviceTypeMirror), batched hydration, transactional replace, entry and vehicle deletes
  - real-driver ServiceEntryMigrationTest plus multi-type repository/deletion/mapper tests
affects:
  - 02-08 (multi-select form, chip group, serviceTypeLine display — replaces the two interim bridges)
  - ServiceEntryEditScreen / ServiceTypePicker (still single-select by design)
tech-stack:
  added: []
  patterns:
    - "position-0 mirror column beside a normalized child table: minSdk 26 cannot DROP COLUMN and a table rebuild would rewrite sqlite_master and fail the byte-identical baseline guard"
    - "one vehicle-scoped child query per history page combined with the entry list — never one query per row"
    - "explicit child-row deletes before the parent, because SQLite foreign keys are declared but never enabled"
key-files:
  created:
    - shared/src/commonMain/sqldelight/com/vehicare/db/migrations/2.sqm
    - shared/src/commonMain/sqldelight/databases/3.db
    - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt
  modified:
    - shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq
    - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt
    - shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryFormValidationTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceHistoryViewModelTest.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt
key-decisions:
  - "service_entry.service_type survives as a NOT NULL mirror of position 0 — Android API 26 cannot DROP COLUMN, and a table rebuild rewrites sqlite_master in a way the byte-identical baseline guard rejects"
  - "The composite primary key (entry_id, position) is the only index on service_entry_type; an extra CREATE INDEX would duplicate the autoindex"
  - "observeHistory hydrates types with one selectTypesForVehicle query combined with the entry list, so a page costs a constant number of queries"
  - "selectLearnedTypes reads the child table with GROUP BY lower(label) and a single MAX() aggregate, which keeps bare-column semantics well defined without window functions"
  - "No init { require(...) } on ServiceEntry: save already fails closed inside its transaction, and a constructor precondition would crash on a legacy or partially hydrated row"
  - "The form, picker and both display surfaces stay single-select in this plan; detail and history carry a one-line firstOrNull() bridge that 02-08 replaces with serviceTypeLine(...)"
requirements-completed: [SVC-01, SVC-05, SVC-06, SVC-07]
duration: 10min
completed: 2026-10-21
---

# Phase 2 Plan 07: Multi-Type Service Entries — Schema v3 and Repository Summary

**Schema v3 with a normalized `service_entry_type` table, a `2.sqm` backfill that preserves every pre-v3 entry's type at position 0, and ordered multi-type reads/writes proven on the real iOS SQLite driver**

## Performance

- **Duration:** 10 min
- **Started:** 2026-10-21T09:34:24Z
- **Completed:** 2026-10-21T09:44:37Z
- **Tasks:** 3
- **Files modified:** 18 (3 created, 15 modified)

## Accomplishments

- Schema moved to v3: `service_entry_type(entry_id, position, label)` with the composite primary key as its only index, created additively so every existing query and column stayed untouched.
- `2.sqm` upgrades a v2 database by creating that table and backfilling `(id, 0, service_type)` for every stored entry — the migration test drops the v3 table, stores two legacy rows and re-runs `Schema.migrate(driver, 2, 3)`, proving each entry regains exactly one type row at position 0 with its own label verbatim.
- `ServiceEntry.serviceTypes: List<String>` replaced the single field; the mapper keeps a `listOf(service_type)` fallback and writes the position-0 label only to the legacy mirror column (`serviceTypeMirror`).
- Reads hydrate in batches: `observeEntry` adds a fourth combined flow, `observeHistory` uses one vehicle-scoped `selectTypesForVehicle` query (no N+1). Writes insert one ordered row per label and replace them on edit; entry delete and the D-39 vehicle cascade remove them before the parent.
- `selectLearnedTypes` now reads the child table grouped case-insensitively (`GROUP BY lower(label)`), ordered newest-used first — a test with `Wheel alignment` / `wheel alignment` across entries returns one chip and never rewrites the stored label (D-40).
- Regenerated `3.db` from the Gradle task (never hand-edited); `1.db` and `2.db` stayed byte-identical, and both migration guards are green.

## Task Commits

Each task was committed atomically:

1. **Task 1: Add schema v3 — table, queries, v2→v3 backfill, regenerated 3.db** - `73c6b70` (feat)
2. **Task 2: Move domain, mapper, repository, cascade and read surfaces to ordered serviceTypes** - `f4c8626` (feat)
3. **Task 3: Prove the v2→v3 backfill and multi-type data behavior on a real driver** - `9d66c49` (test)

**Plan metadata:** `_pending_` (docs: complete plan)

## Files Created/Modified

- `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq` — `service_entry_type` DDL (with the mirror rationale documented in-file), five new queries, rewritten `selectLearnedTypes`.
- `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/2.sqm` — v2→v3 create + backfill, DDL structurally identical to the `.sq`.
- `shared/src/commonMain/sqldelight/databases/3.db` — generated baseline for v3.
- `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt` — `serviceTypes: List<String>` plus the ordering/verbatim/mirror contract in KDoc.
- `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt` — `serviceTypes = listOf(service_type)` fallback, `label` from the learned-types row, `serviceTypeMirror = serviceTypes.first()`.
- `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt` — `save` KDoc now names the type rows.
- `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt` — batched hydration, ordered insert, replace-on-edit, delete-before-parent.
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` — `deleteTypesForVehicle` in the D-39 cascade.
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt` — interim `serviceTypes = listOf(serviceType)` bridge (form still single-select).
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt` — `toFormValues` reads `serviceTypes.firstOrNull()`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt`, `.../components/ServiceHistoryRow.kt` — documented temporary `firstOrNull().orEmpty()` compile bridges.
- `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt` — new real-driver migration proof (2 tests).
- `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt` — four new multi-type tests + helper gains a `serviceTypes` list parameter.
- `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt` — cascade asserts both entries' type rows are gone and the survivor keeps its own.
- `shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt` — bare-row fallback test, `serviceTypeMirror`, `SelectLearnedTypes(label = …)`.
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryFormValidationTest.kt` — one assertion reads `entry.serviceTypes`.
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceHistoryViewModelTest.kt` — construction uses `serviceTypes`.

## Decisions Made

- **Mirror column kept.** `service_entry.service_type` stays as a NOT NULL mirror of position 0: Android API 26 ships SQLite 3.18 (no `ALTER TABLE … DROP COLUMN`), and a rebuild via `ALTER TABLE … RENAME` rewrites its `sqlite_master` SQL with a quoted name, which fails the byte-identical baseline guard this project relies on.
- **Composite PK only.** No extra `CREATE INDEX` on `service_entry_type`: `WHERE entry_id = ?` is a prefix lookup and the autoindex already orders `(entry_id, position)`.
- **Batched history hydration.** `selectTypesForVehicle(vehicleId)` combined with the entry list keeps a page at a constant query count (threat T-02-07-07).
- **Learned-type SQL.** `GROUP BY lower(label)` with a single `MAX(entry_id)` aggregate: the bare `label` is well defined (SQLite takes bare columns from the max row), and the old `WHERE id IN (SELECT MAX(id) …)` shape would duplicate a label once one entry carries several.
- **No defensive `init { require(...) }`.** `save` fails closed inside its transaction; a constructor precondition would crash on a legacy or partially hydrated row.
- **Scope held.** Form, picker and both display surfaces stay single-select; the two one-line bridges are marked in code as 02-08's replacement point — no display rule was invented here.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Migration test read entry ids as 0 outside a transaction**
- **Found during:** Task 3 (real-driver migration test, first run)
- **Issue:** `insertEntry` followed by `lastInsertId()` outside a transaction returned an id that did not match the written row, so `selectTypes(id)` was empty and the backfill assertion failed. `last_insert_rowid()` is only guaranteed on the connection that performed the insert; the repository always reads it inside `transactionWithResult`, the new test did not.
- **Fix:** Wrapped the vehicle insert and both legacy-entry inserts in `database.transactionWithResult { … }` — the same pattern the production repositories use — and added a precondition assertion that both legacy rows are stored before the upgrade runs.
- **Files modified:** `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt`
- **Verification:** `./gradlew :shared:iosSimulatorArm64Test --tests 'com.vehicare.data.ServiceEntryMigrationTest'` → 2 tests, 0 failures; full `:shared:iosSimulatorArm64Test` → 104 tests, 0 failures.
- **Committed in:** `9d66c49` (part of Task 3 commit)

---

**Total deviations:** 1 auto-fixed (1 bug, test-only)
**Impact on plan:** No production behavior changed and no scope added; the fix made the new test exercise the intended rows instead of silently asserting against id 0.

## Issues Encountered

- **Line-number drift in the plan's edit list.** The 11 `ServiceEntry(...)` sites in `ServiceEntryDeletionTest.kt` sit at 113/157/164/187/209/254/302/328/372/379/392, not 109/153/… as the plan recorded. The file contents matched, so all 11 were renamed by content, not by line.
- **Task 2 compile-surge check.** The update-branch and insert-branch mirror binds are textually identical, so the first `service_type = params.serviceType` edit matched twice; the insert branch was resolved with a wider anchor. No behavioral impact.
- **Ad-hoc pipeline masking exit codes.** One early gate run appended `&& echo …` after `| tail`, which printed the OK marker on a failing test. Every later gate was run with an explicit `echo "EXIT=$?"`, and the final gate set was re-run from a clean shell.

## Known Stubs

Two intentional, in-plan stubs — both explicitly owned by plan 02-08 and required for the tree to compile before the UI half lands. They do **not** block this plan's goal (the data half):

| Stub | File | Reason |
|------|------|--------|
| `entry.serviceTypes.firstOrNull().orEmpty()` with a `// temporary compile bridge` comment | `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt:238-240` | 02-08 replaces it with the locked `serviceTypeLine(...)` display rule |
| Same bridge | `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt:54-56` | Same; history uses a 1-line cap while detail allows 2 |
| `toServiceEntry` maps `listOf(values.serviceType ?: "")`; `toFormValues` reads `serviceTypes.firstOrNull()` | `ServiceEntryFormValidation.kt:118`, `ServiceEntryEditViewModel.kt:412` | The form values class and chip picker stay single-select until 02-08 Task 1 |

## Threat Flags

None — the three new trust-boundary surfaces this plan introduces (v2→v3 backfill, mirror/child dual write, cascade of type rows) are exactly the ones already enumerated as T-02-07-01…07, and each mitigation is implemented and asserted:

| Threat ID | Mitigation in place | Proof |
|-----------|--------------------|-------|
| T-02-07-01 (DDL drift) | Single authored DDL copied structurally into `2.sqm` | `verifyCommonMainVehiCareDatabaseMigration` + regenerate-then-`git diff --exit-code` both exit 0 |
| T-02-07-02 (lost types on upgrade) | `INSERT … SELECT id, 0, service_type` | `ServiceEntryMigrationTest` (two distinct labels, row-for-row) |
| T-02-07-03 (mirror divergence) | Mirror written from one place (`toBindParameters`) | assert after insert and after edit |
| T-02-07-04 (orphan rows) | Delete-then-reinsert in the same transaction; delete before parent | `editingAnEntryReplacesItsTypeRows`, `deletingAnEntryRemovesItsTypeRows` |
| T-02-07-05 (erased vehicle leaves text) | `deleteTypesForVehicle` inside the cascade transaction | cascade test asserts survivor-only rows |
| T-02-07-06 (duplicate chips) | `GROUP BY lower(label)` | `learnedTypesReadTheChildTableNewestFirstAndCollapseCaseVariants` |
| T-02-07-07 (N+1 reads) | One `selectTypesForVehicle` per history page | grep gate + `observeEntryAndHistoryHydrateEveryEntriesOrderedTypes` |

## User Setup Required

None — no external service configuration required. No package was installed and no dependency was added.

## Next Phase Readiness

- **02-08 can start immediately.** It consumes a stable contract: `ServiceEntry.serviceTypes` (ordered, non-empty), the two bridge functions to replace, and `service_entry_type` as the single source of truth for learned chips.
- Manual UAT still owed for this plan (deferred to phase UAT per the plan): install a v2 build with one entry, upgrade in place, confirm the entry keeps its original type, then save an entry with several types and confirm it survives a force-quit/relaunch.
- Gate results at completion: `:androidApp:assembleDebug` ✅, `:shared:compileKotlinIosSimulatorArm64` ✅, `:shared:testAndroidHostTest` ✅ (58 tests), `:shared:iosSimulatorArm64Test` ✅ (104 tests, 0 failures), `:shared:verifyCommonMainVehiCareDatabaseMigration` ✅, regenerate-then-diff ✅.

---

*Phase: 02-service-log*
*Completed: 2026-10-21*

## Self-Check: PASSED

```
FOUND: ServiceEntryMigrationTest.kt
FOUND: 2.sqm
FOUND: 3.db
FOUND: SUMMARY.md
FOUND: 73c6b70
FOUND: f4c8626
FOUND: 9d66c49
```
