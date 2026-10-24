---
phase: 03-reminders-dashboard
plan: 01
subsystem: database
tags: [sqldelight, sqlite, migration, kmp, koin, process-global-state]

# Dependency graph
requires:
  - phase: 01-foundation-vehicle-management
    provides: VehicleRepository/VehicleRepositoryImpl, DriverFactory expect/actual seam, Koin appModule, the 1.db/2.db/3.db schema baselines
  - phase: 02-service-log
    provides: service_entry + service_entry_type tables, selectLearnedTypes single-MAX idiom, 2.sqm migration precedent, ServiceEntryMigrationTest real-driver template
provides:
  - reminder_rule table (11 columns) + reminder_rule_vehicle index as schema v4
  - migrations/3.sqm additive v3 -> v4 migration and the regenerated 4.db baseline
  - markNotified compare-and-set claim (D-51) and markArmed bookkeeping marker (D-53)
  - selectTypeBaselines / selectVehicleStats flat reads for (vehicle, type) baselines and per-vehicle stats
  - DatabaseProvider process-wide holder with initialize/require
  - ReminderRule, TypeBaseline, VehicleStats domain models
  - ReminderAxis + five-member DueState vocabulary
affects: [03-02 due-state engine, 03-03 repository + mapper, 03-04 reminders screen, 03-05 detail row, 03-06 dashboard, 03-07 notifications/worker, 03-08 permission gate]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Process-wide DB holder (DatabaseProvider) constructed only at the platform entry point; Koin reads it instead of building a driver"
    - "Compare-and-set claim: the affected-row count of one conditional UPDATE is the once-per-cycle latch, never an in-memory set"
    - "Single-MAX bare-column idiom for pairing the newest date with that same row's odometer"
    - "Additive-only migrations: CREATE TABLE + CREATE INDEX, no ALTER TABLE, so the SQLite 3.18 rewrite hazards never apply"

key-files:
  created:
    - shared/src/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq
    - shared/src/commonMain/sqldelight/com/vehicare/db/migrations/3.sqm
    - shared/src/commonMain/sqldelight/databases/4.db
    - shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseProvider.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/ReminderRule.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt
    - androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt
  modified:
    - shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
    - shared/src/androidMain/kotlin/com/vehicare/di/PlatformModule.android.kt
    - shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt
    - androidApp/src/main/AndroidManifest.xml
    - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt

key-decisions:
  - "DatabaseProvider is a plain commonMain object with no expect/actual: nothing in it is platform-specific, so two actuals would only be two things to drift (03-PATTERNS C-2)"
  - "AppModule's getOrNull<DriverFactory>() ?: createDriverFactory() fallback was deleted rather than left dormant: on Android it casts null and calls error(), so keeping it would turn a missing holder into a crash on first repository resolution (C-3)"
  - "The dead single<DriverFactory> binding was removed from platformModule, which now registers only Context: a dead binding that still calls createDriverFactory is a second driver waiting to be resolved (C-4)"
  - "ReminderRule.sq ships exactly the plan's query set (no lastInsertId); the write path that needs it belongs to plan 03, and adding a query later does not disturb the schema baseline"
  - "ReminderStatus.kt is deliberately a pure type declaration — no evaluate, no urgency, no cycleKey — so the copy layer (plan 03) can depend on the vocabulary at wave 2 without depending on the engine (plan 02)"

patterns-established:
  - "One driver per process: DatabaseProvider.initialize is called exactly once per platform, check() rejects a second call, require() errors with an actionable message"
  - "Cycle-key claim vs armed proof: notified_cycle_key gates delivery, armed_cycle_key only records that the OS accepted a request; neither is ever read as the other"
  - "Baseline pairing: a rule's mileage and date always come from the same service entry row, via the single-MAX bare-column behaviour"

requirements-completed: [REM-01, REM-02]

# Metrics
duration: 20min
completed: 2026-10-24
---

# Phase 3 Plan 01: Reminder Schema & Process-Wide Database Summary

**Schema v4 with a `reminder_rule` table, its compare-and-set notification claim and `armed_cycle_key` marker, plus a `DatabaseProvider` holder that makes the Android `CoroutineWorker` and the Compose UI share one SQLDelight driver.**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-10-24T01:38:00Z (08:38 WIB)
- **Completed:** 2026-10-24T01:52:00Z (08:52 WIB)
- **Tasks:** 2 of 2
- **Files modified:** 13 (7 created, 6 modified)

## Accomplishments

- `reminder_rule` ships as schema v4: eleven columns, `reminder_rule_vehicle` index, and a query set whose `markNotified` is a single conditional UPDATE — the affected-row count *is* D-51's once-per-cycle guarantee, so two concurrent evaluations cannot both notify.
- `migrations/3.sqm` is additive (CREATE TABLE + CREATE INDEX only), structurally identical to the `.sq` DDL, and `4.db` was regenerated by Gradle and committed — both CI guards pass.
- `selectTypeBaselines` returns each (vehicle, lowercased type) pair's newest date **and that same row's odometer**, using the single-MAX bare-column behaviour already proven by `selectLearnedTypes`; `selectVehicleStats` gives D-49 its per-vehicle fallback without inventing an odometer value.
- Exactly one `VehiCareDatabase` is now constructed per process, at `VehiCareApp.onCreate` (Android) and `MainViewController` (iOS). The Koin graph reads `DatabaseProvider.require()`, and the `createDriverFactory()` fallback that would have thrown on Android is gone.
- The Phase 3 domain vocabulary landed as pure Kotlin: `ReminderRule`/`TypeBaseline`/`VehicleStats`, and `ReminderAxis` + a five-member `DueState` with no function, no `when` and no resource reference.

## Task Commits

Each task was committed atomically:

1. **Task 1: Ship the reminder_rule schema, its migration and the two baseline reads** — `1248b03` (feat)
2. **Task 2: Construct the database once per process and delete the Koin driver fallback** — `09c53dd` (feat)

**Plan metadata:** `docs(03-01): complete reminder schema & process-wide database plan` (this SUMMARY + nothing else — STATE.md and ROADMAP.md are the orchestrator's to write).

_No TDD tasks in this plan; `tdd_mode` is false for the phase._

## Verification

Every gate below was run and its observed result recorded. Commands are the plan's, run as separate `./gradlew` invocations.

| Gate | Command | Observed |
|---|---|---|
| Migration guard (DAT-04) | `./gradlew verifyCommonMainVehiCareDatabaseMigration` | **PASS** — `BUILD SUCCESSFUL` |
| Baseline drift guard | `./gradlew generateCommonMainVehiCareDatabaseSchema && git diff --exit-code -- shared/src/commonMain/sqldelight/databases` | **PASS** — `diff_exit=0`, `4.db` regenerated identically |
| Baseline is tracked | `git ls-files --error-unmatch shared/src/commonMain/sqldelight/databases/4.db` | **PASS** — path echoed |
| Migration tests (real native driver) | `./gradlew :shared:iosSimulatorArm64Test --tests "com.vehicare.data.ServiceEntryMigrationTest"` | **PASS** — 3 tests, 0 failures (`schemaVersionIsFour`, `migratingFromTwoBackfillsTheMirrorLabelAtPositionZero`, `migratingFromThreeCreatesReminderRule`) |
| Android build | `./gradlew :androidApp:assembleDebug` | **PASS** — `BUILD SUCCESSFUL` |
| Android host tests | `./gradlew :shared:testAndroidHostTest` | **PASS** — 80 tests, 0 failures, 0 errors |
| iOS simulator suite | `./gradlew :shared:iosSimulatorArm64Test` | **PASS** — 132 tests, 0 failures, 0 errors |
| No driver construction left in `commonMain` | `test -z "$(grep -rn 'createDriverFactory' shared/src/commonMain/kotlin)"` | **FAILED AS WRITTEN** — see Deviations §1. Intent-correct variant (excluding the `expect` declaration file): **PASS** |
| One `initialize` per platform | `test "$(grep -rn 'DatabaseProvider.initialize' androidApp/src/main/kotlin shared/src/iosMain/kotlin \| grep -v '^\s*//' \| wc -l \| tr -d ' ')" = "2"` | **PASS** — exactly 2 (one per platform) |

Additional evidence gathered beyond the plan's gates:

- Merged debug manifest contains `android:name="com.vehicare.VehiCareApp"`.
- `grep 'DriverFactory' shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` → none.
- `single<…>` in `PlatformModule.android.kt` → only `single<Context>`.
- No `DatabaseProvider.android.kt` / `.ios.kt` exist; only the `commonMain` file.
- `grep -nE 'fun |when *\(|Res\.' shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt` → none.

## Files Created/Modified

- `shared/src/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq` — the `reminder_rule` table, its index, and the eleven named queries including the `markNotified` claim and the `markArmed` bookkeeping marker.
- `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/3.sqm` — additive v3 → v4 migration (table + index, no backfill, no PRAGMA).
- `shared/src/commonMain/sqldelight/databases/4.db` — regenerated schema baseline, committed as a build output.
- `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq` — appended `selectTypeBaselines` and `selectVehicleStats`.
- `shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseProvider.kt` — the process-wide holder; `initialize` guards with `check`, `require` errors with an actionable message.
- `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderRule.kt` — `ReminderRule`, `TypeBaseline`, `VehicleStats` as pure data classes.
- `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt` — `ReminderAxis` and the five-member `DueState`; types only.
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` — database binding is now `DatabaseProvider.require()`; driver construction and its imports removed; every repository and ViewModel binding unchanged.
- `shared/src/androidMain/kotlin/com/vehicare/di/PlatformModule.android.kt` — registers only `Context`; dead `DriverFactory` binding and imports removed.
- `androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt` — `Application.onCreate` installs the holder before Compose starts; comment reserves the channel creation for plan 07.
- `androidApp/src/main/AndroidManifest.xml` — added `android:name=".VehiCareApp"`; nothing else (permission and `launchMode` stay in plan 07).
- `shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt` — installs the holder before building the controller; still a plain function, still no `iosPlatformModule`.
- `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt` — `schemaVersionIsThree` → `schemaVersionIsFour` (`4L`), plus `migratingFromThreeCreatesReminderRule` and two private raw-SQL helpers (`sqliteMasterCount`, `tableColumns`).

## Decisions Made

- **`ReminderStatus.kt` stayed a pure type declaration.** The plan forbids an `evaluate`/`urgency`/`cycleKey` here, and the temptation to add a convenience constructor was resisted: the copy layer (plan 03) has to depend on the vocabulary at wave 2 without pulling in the engine (plan 02).
- **The migration test drops the table and index before migrating.** `Schema.migrate(driver, 3, 4)` replays `CREATE TABLE reminder_rule`, so on a driver that already created the v4 schema the call would fail with "table already exists". Dropping first is what makes the test *prove* the migration rather than merely pass — the same technique the existing v2 → v3 test uses for `service_entry_type`.
- **`lastInsertId` was not added to `ReminderRule.sq`.** The plan names an exact query set and this plan has no writer; plan 03 adds it when `ReminderRepositoryImpl.save` needs the id, and adding a query later leaves the schema baseline untouched.

## Deviations from Plan

### Plan-gate defect (no code change made to satisfy it)

**1. [Plan gate] `test -z "$(grep -rn 'createDriverFactory' shared/src/commonMain/kotlin)"` cannot pass as written**

- **Found during:** Task 2 verification.
- **Issue:** The exact command returns two hits, both in `shared/src/commonMain/kotlin/com/vehicare/data/db/DriverFactory.kt`: line 37 is the `expect fun createDriverFactory(...)` **declaration** — which by definition must live in `commonMain`, it is the platform seam itself — and line 25 is a KDoc reference to it. Deleting either would remove the seam, not the risk.
- **What the gate was meant to catch:** a *call site* — a second construction path in shared code, which is exactly the T-03-01 threat ("the Koin fallback that could construct a second driver is deleted"). That risk is genuinely gone: `AppModule.kt` has no `DriverFactory` reference at all (verified by grep), and no `commonMain` file outside `DriverFactory.kt` mentions `createDriverFactory`.
- **Fix:** No code change. The plan's command was run and its real (failing) output recorded above; an intent-correct variant — `grep -rn 'createDriverFactory' shared/src/commonMain/kotlin | grep -v 'data/db/DriverFactory.kt'` — was run and is empty.
- **Impact:** The plan text should be corrected in a future revision of this plan (or the gate moved to `grep -rn 'createDriverFactory(' … | grep -v 'expect fun'`). The threat T-03-01 mitigation itself is satisfied.

### Other deviations

None. No bugs, no missing critical functionality, no blocking issues, no architectural change was needed — the plan's file list, query set and wiring all compiled and passed on the first attempt.

---

**Total deviations:** 0 auto-fixed; 1 plan-gate defect reported (no code change).
**Impact on plan:** No scope creep, no unplanned files. One verification command in the plan is unsatisfiable and should be reworded.

## Issues Encountered

- **Gradle task-ordering artifact when all five verification tasks are passed in one invocation.** `./gradlew verify… generate… :shared:testAndroidHostTest :shared:iosSimulatorArm64Test :androidApp:assembleDebug` fails with an implicit-dependency validation error between `generateCommonMainVehiCareDatabaseSchema` and `generateCommonMainVehiCareDatabaseInterface`. This is a Gradle 9.5.1 task-graph complaint, not a code or schema problem: the plan lists the commands as separate lines, and run as separate invocations every one of them succeeds. Recorded here so a future verifier does not read the combined-invocation failure as a real regression.
- **`4.db` is untracked until staged**, so the plan's `git diff --exit-code -- …/databases` guard is vacuous on the first run of a new baseline (an untracked file produces no diff). The guard only becomes load-bearing on the *second* run, after the file is committed — which is what CI does. The tracked check (`git ls-files --error-unmatch`) was used to close that gap, and the guard was re-run post-commit with `diff_exit=0`.

## Known Stubs

| Stub | File | Reason / resolving plan |
|---|---|---|
| `DueState` has no evaluation logic — nothing yet turns a rule + baseline into a `DueState` | `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt` | Intentional and required by this plan ("Do not add an `evaluate` … plan 02 owns every decision"). Plan 03-02 adds the engine to this same file. |
| `reminder_rule` has no reader or writer — no repository, no mapper, no ViewModel | `shared/src/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq` | Intentional: this plan ships the schema and the holder only. Plan 03-03 adds `ReminderRuleEntityMapper` + `ReminderRepository`, plan 03-04/05 the screens. |

Neither stub blocks this plan's goal (a v4 schema plus one database per process); both are explicitly scheduled in the phase's artifact table.

## User Setup Required

None — no external service configuration, no new dependency, no permission declared. The phase's only new artifact (`androidx.work:work-runtime:2.12.0`) is gated in plan 07.

## Next Phase Readiness

- **Ready for plan 03-02 (due-state engine):** `ReminderRule`, `TypeBaseline`, `VehicleStats` and the `DueState`/`ReminderAxis` vocabulary are on disk and compile on both platforms, so the engine can be written and unit-tested in `commonTest` with no UI or database.
- **Ready for plan 03-03 (repository):** the schema is v4 with both markers, and `ServiceEntry.sq` already returns baselines in domain terms. `ReminderRepositoryImpl` should add `lastInsertId` to `ReminderRule.sq` for its `save` path — an additive query that does not disturb `4.db`.
- **Ready for plan 03-07 (notifications/worker):** `DatabaseProvider.require()` is the worker's entry point and needs no Koin container. `VehiCareApp.onCreate` is the agreed channel-creation site (comment left in place).
- **Watch item:** `armed_cycle_key` must never be read as a claim. Plan 03-02's engine and plan 03-07's worker both need the three-case time path from RESEARCH §"Why a second marker"; the column alone does not enforce it.

---

*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*

## Self-Check: PASSED

- All 13 files listed in "Files Created/Modified" exist on disk (14/14 including this SUMMARY).
- Both task commits exist in history: `1248b03` (Task 1), `09c53dd` (Task 2).
- No file deletions in either commit (`git diff --diff-filter=D --name-only HEAD~1 HEAD` empty for both).
- No untracked files left behind (`git status --short | grep '^??'` empty after Task 2).
- `.planning/STATE.md` and `.planning/ROADMAP.md` were **not** modified by this executor (STATE.md's pre-existing modification predates the first commit; ROADMAP.md is clean).
