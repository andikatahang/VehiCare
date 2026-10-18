---
phase: 01-foundation-vehicle-management
plan: 03
subsystem: database
tags: [sqldelight, sqlite, wal, koin, coroutines, kotlin-multiplatform, driver-factory]

# Dependency graph
requires:
  - phase: 01-foundation-vehicle-management
    provides: 01-01 KMP scaffold (SQLDelight config with schemaOutputDirectory, Koin and coroutines deps, expect/actual setup)
provides:
  - "Vehicle.sq: vehicle table, five CRUD queries, lastInsertId id helper (no RETURNING, no mileage, no BLOB)"
  - "Committed 1.db schema baseline + empty migrations/ dir (DAT-04 anchor)"
  - "Domain models Vehicle/VehicleType, generated-type-free"
  - "expect/actual DriverFactory + DatabaseFactory with WAL-on-first-connection behavior"
  - "Koin appModule + platformModule(androidContext) graph: DriverFactory -> DatabaseFactory -> VehiCareDatabase -> VehicleRepository"
  - "VehicleEntityMapper boundary + serialized-IO VehicleRepositoryImpl proven on a real iOS driver"
affects: [01-04, 01-05, 01-06, 01-07, 01-08, 01-09]

# Tech tracking
tech-stack:
  added: [SQLDelight 2.4.1 runtime + coroutines-extensions, SQLiter (transitively, via sqldelight-native-driver)]
  patterns:
    - "Mapper boundary: generated row types never leave the data layer"
    - "Single IO lane: Dispatchers.IO.limitedParallelism(1), injected (T-01-15)"
    - "expect/actual DriverFactory with platformContext: Any? seam"
    - "WAL established at driver construction, not in the .sq preamble"
    - "photoDeleter injection seam for row+file deletion (Pitfall-4)"

key-files:
  created:
    - shared/src/commonMain/sqldelight/com/vehicare/db/Vehicle.sq
    - shared/src/commonMain/sqldelight/databases/1.db
    - shared/src/commonMain/kotlin/com/vehicare/domain/Vehicle.kt
    - shared/src/commonMain/kotlin/com/vehicare/domain/VehicleType.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/db/DriverFactory.kt
    - shared/src/androidMain/kotlin/com/vehicare/data/db/DriverFactory.android.kt
    - shared/src/iosMain/kotlin/com/vehicare/data/db/DriverFactory.ios.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseFactory.kt
    - shared/src/androidMain/kotlin/com/vehicare/di/PlatformModule.android.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/VehicleEntityMapper.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepository.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
    - shared/src/commonTest/kotlin/com/vehicare/data/VehicleEntityMapperTest.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/VehicleRepositoryTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
    - .gitignore

key-decisions:
  - "WAL is NOT a PRAGMA in the .sq preamble: the generated Schema.create() replays it through driver.execute(), which crashes the iOS driver (proven). Android sets it via row-safe executeQuery at construction; iOS relies on SQLiter's JournalMode.WAL default."
  - "Generated row type is com.vehicare.db.Vehicle (not VehicleEntity) - imported with an 'as VehicleEntity' alias in the mapper and tests"
  - "Koin 4.2.2 has no androidContext()/androidLogger() module DSL - platformModule(context) factory registers single<Context> explicitly"
  - "photoDeleter defaults to a no-op seam; plan 01-08 replaces it with PhotoStorage::delete"
  - "Repository binding landed in the Task 3 commit (compile-gate ordering: the impl type did not exist at Task 2 time)"

patterns-established:
  - "WAL on first connection: Android factory runs the pragma via executeQuery AFTER constructing the driver; iOS gets it from SQLiter's DatabaseConfiguration default"
  - "Save semantics: id == 0L inserts (returns lastInsertId().executeAsOne() from inside the same transaction), otherwise updates in place"
  - "Delete semantics: read photo_path inside the transaction, delete the row, call photoDeleter(photoPath) after the transaction (lambda is non-suspend)"

requirements-completed: [DAT-01, DAT-03, DAT-04]

# Metrics
duration: 20min
completed: 2026-10-18
---

# Phase 1 Plan 3: WAL schema, driver factories and the serialized-IO repository Summary

**WAL SQLDelight schema with a committed 1.db baseline, Koin-wired platform driver factories (with a crash-proven WAL mechanism fix), and a mapper-bounded VehicleRepository proven end-to-end on a real iOS driver**

## Performance

- **Duration:** 20 min
- **Started:** 2026-10-18T16:26:50Z (plan 01-03 execution start)
- **Completed:** 2026-10-18T16:46:32Z
- **Tasks:** 3/3
- **Files modified:** 17

## Accomplishments

- `Vehicle.sq` with the 8-column vehicle table, five typed CRUD queries and the `lastInsertId` helper; `1.db` baseline committed and reproducible (`generate` produces zero diff); `migrations/` committed empty (0 `.sqm`)
- expect/actual `DriverFactory` + `DatabaseFactory` + Koin graph; Android drives real `AndroidSqliteDriver`, iOS real `NativeSqliteDriver`
- WAL enabled on the first connection on both platforms — after the plan's original mechanism was disproven by an iOS crash and replaced (see Deviations)
- `VehicleRepositoryImpl` on one `Dispatchers.IO.limitedParallelism(1)` lane: insert → observe → in-place update → read-by-id → delete, all 5 tests green against a real `NativeSqliteDriver` (iosSimulatorArm64Test), mapper 4/4 green on both targets

## Task Commits

Each task was committed atomically:

1. **Task 1: Author the WAL schema, domain models and checked-in schema baseline** — `934ea49` (feat), plus `0a4e7ac` (chore: gitignore SQLite WAL sidecars, required by the baseline re-generate check)
2. **Task 2: Driver factories, DatabaseFactory and the Koin graph** — `b850f1f` (feat)
3. **Task 3: Mapper + serialized-IO repository (TDD)** — `7681ce5` (test, RED) → `5e9f9ad` (fix: WAL mechanism, unplanned) → `7306790` (feat, GREEN)

_TDD note: the WAL fix commit sits between RED and GREEN because the RED suite's iOS half could not pass while driver creation crashed._

## Files Created/Modified

- `shared/src/commonMain/sqldelight/com/vehicare/db/Vehicle.sq` — vehicle table + 5 CRUD + `lastInsertId`; pragma replaced by an explanatory comment (WAL moved to driver construction)
- `shared/src/commonMain/sqldelight/databases/1.db` — generated schema baseline for `verifyCommonMainVehiCareDatabaseMigration` (DAT-04)
- `shared/src/commonMain/kotlin/com/vehicare/domain/Vehicle.kt`, `VehicleType.kt` — generated-type-free domain models with `fromDb`/`toDb` (unknown type string falls back to `Car`)
- `shared/src/commonMain/kotlin/com/vehicare/data/db/DriverFactory.kt` + `.android.kt` + `.ios.kt` — expect/actual factory; Android sets WAL via row-safe `executeQuery`, iOS relies on SQLiter's `JournalMode.WAL` default
- `shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseFactory.kt` — `DatabaseFactory(driver: SqlDriver)` per the plan's fixed `<interfaces>` contract
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` — driver → database → repository graph
- `shared/src/androidMain/kotlin/com/vehicare/di/PlatformModule.android.kt` — `platformModule(context)`: registers `single<Context>` + the Android `DriverFactory`
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleEntityMapper.kt` — the only production file importing the generated row type (`toDomain`, `toBindParameters`)
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepository.kt` / `VehicleRepositoryImpl.kt` — interface (zero SQLDelight imports) + serialized-IO implementation
- `shared/src/commonTest/kotlin/com/vehicare/data/VehicleEntityMapperTest.kt` — 4 mapper tests
- `shared/src/iosTest/kotlin/com/vehicare/data/VehicleRepositoryTest.kt` — 5 real-driver repository tests
- `.gitignore` — `*.db-wal`, `*.db-shm`

## Decisions Made

- **WAL mechanism moved out of the `.sq` preamble** — the plan's prescribed mechanism (`PRAGMA journal_mode=wal;` as line 1, emitted inside `Schema.create()`) is *disproven*: see Deviations #1. Android executes the identical SQL via the row-safe query path; iOS relies on SQLiter's built-in WAL default (verified in SQLiter sources).
- **Generated row type is `com.vehicare.db.Vehicle`,** not `VehicleEntity` as the plan text assumed — mapper/tests use `import com.vehicare.db.Vehicle as VehicleEntity` so the intended names survive.
- **Koin 4.2.2 has no `androidContext()`/`androidLogger()` module DSL** (verified via `javap` on `koin-android-4.2.2.aar`; they exist only as `KoinApplication` extensions). Resolution is exact-type-only, so `platformModule(context)` registers `single<Context>` + a `DriverFactory` built from it; `AppModule` does `getOrNull<DriverFactory>() ?: createDriverFactory()` and deliberately omits `androidLogger`.
- **`photoDeleter` no-op seam** — `delete()` reads `photo_path` inside the transaction, deletes the row, then calls `photoDeleter(photoPath)` after the transaction block (the lambda is non-suspend, so the file call must sit outside it). Plan 01-08 replaces the `{}` default with `PhotoStorage::delete`.
- **Repository binding landed in the Task 3 commit,** not Task 2, because `VehicleRepositoryImpl` did not exist until Task 3 (compile-gate ordering).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] The plan's WAL mechanism crashes driver creation on iOS — replaced with per-platform first-connection WAL**

- **Found during:** Task 3 (RED run — all 5 `VehicleRepositoryTest` cases failed identically in `setUp` on `iosSimulatorArm64Test`)
- **Issue:** With `PRAGMA journal_mode=wal;` as the `.sq` preamble, SQLDelight emits `driver.execute(null, "PRAGMA journal_mode=wal", 0)` inside the generated `Schema.create()` (observed at line 44 of `VehiCareDatabaseImpl.kt`). On `NativeSqliteDriver`, `execute` maps to SQLiter's `ActualSqliteStatement.executeNonQuery()`, which throws when `sqlite3_step` reports a statement that returns rows: `co.touchlab.sqliter.interop.SQLiteException: Queries can be performed using SQLiteDatabase query or rawQuery methods only.` `journal_mode=wal` *always* returns a row, so the iOS driver could never be created. The mechanism can not work on native as researched; no code change short of removing the pragma from `create()` fixes it.
- **Fix:** (a) pragma removed from `Vehicle.sq` (replaced by an explanatory comment); (b) `DriverFactory.android.kt` enables WAL immediately after `AndroidSqliteDriver` construction via `driver.executeQuery(identifier = null, sql = "PRAGMA journal_mode=wal", mapper = { cursor -> cursor.next() }, parameters = 0)` — the row is actually stepped/consumed; (c) `DriverFactory.ios.kt` documents that SQLiter opens every connection with `DatabaseConfiguration.journalMode = JournalMode.WAL` by default (verified in SQLiter sources), so WAL is on without executing SQL.
- **Files modified:** `Vehicle.sq`, `DriverFactory.kt` (KDoc), `DriverFactory.android.kt`, `DriverFactory.ios.kt`, `databases/1.db` (regenerated)
- **Verification:** generated `create()` now contains 0 `PRAGMA` occurrences; `iosSimulatorArm64Test` went 0/5 → 5/5; Android runtime path is not exercisable in this environment (no Robolectric/instrumented runner) — deferred to plan 01-05's instrumented Android test, which builds a real driver via `createDriverFactory(...)` and asserts WAL.
- **Committed in:** `5e9f9ad`

**2. [Rule 1 - Bug] `@OptIn(ExperimentalForeignApi::class)` for the iosTest cleanup helper**

- **Found during:** Task 3 (compiling the RED suite)
- **Issue:** `NSFileManager.removeItemAtPath` (used to wipe the scratch database dir between tests) is behind `kotlinx.cinterop.ExperimentalForeignApi`; without the opt-in the test source set does not compile.
- **Fix:** added the annotation + import to `deleteTestDatabaseFiles`; folded into the RED commit by amend (`03ed9ab` → `7681ce5`) before any dependent commit existed.
- **Files modified:** `shared/src/iosTest/kotlin/com/vehicare/data/VehicleRepositoryTest.kt`
- **Verification:** `:shared:compileTestKotlinIosSimulatorArm64` succeeds; suite green.
- **Committed in:** `7681ce5` (amended)

**3. [Rule 3 - Blocking] Task 2's `DatabaseFactory` signature corrected to the plan's fixed contract**

- **Found during:** Task 2 verification
- **Issue:** the first Task 2 write of `DatabaseFactory` did not take `driver: SqlDriver` per the `<interfaces>` block; downstream binding could not match the fixed contract.
- **Fix:** corrected to `DatabaseFactory(driver: SqlDriver)` and amended into the Task 2 commit (`bfe870a` → `b850f1f`) before any later commit.
- **Verification:** `./gradlew :androidApp:assembleDebug :shared:compileKotlinIosSimulatorArm64` green at `b850f1f`.
- **Committed in:** `b850f1f` (amended)

### Retracted acceptance criteria (mechanism replaced by proven-equivalent evidence)

These plan criteria targeted the disproven WAL mechanism and are retracted with the evidence below:

1. **Task 1 verify `WAL_PRAGMA_OK`** and acceptance "`Vehicle.sq` line 1 is `PRAGMA journal_mode=wal;`" (also the `must_haves` artifact `contains: "PRAGMA journal_mode=wal"`): retracted — `grep -q 'PRAGMA journal_mode=wal' Vehicle.sq` now intentionally fails; the pragma lives in `DriverFactory.android.kt` and the iOS default.
2. **Task 2 verify `grep -c 'PRAGMA\|journal_mode\|onConfigure' DriverFactory.android.kt` == 0** and acceptance "ZERO occurrences": retracted — the file now has 3 mentions (2 code, 1 comment) by design.
3. **Plan-level "`grep -rn 'journal_mode\|PRAGMA' shared/src/androidMain shared/src/iosMain` returns zero matches (WAL is SQL-only)":** retracted — 5 matches (3 android, 2 iOS comments).
4. **Must-have truth wording "declared in SQL rather than in platform config":** partially retracted — Android still executes the identical SQL pragma (just through the row-safe path, at construction); iOS cannot execute it at all and uses SQLiter's platform default.

**The underlying truth still holds — "SQLite runs in WAL journal mode from the very first connection on both platforms":** Android sets it before the driver is ever handed to `DatabaseFactory`; iOS gets it from `DatabaseConfiguration.journalMode = WAL` at open. Replacement evidence/guards: generated `create()` has zero pragma replays; RED-to-GREEN 0/5 → 5/5 on the real iOS driver; plan 01-05's iOS real-driver WAL test + Android instrumented test are the runtime guards.

**Baseline journal-mode note:** regenerated `1.db` is a `delete`-mode file (the pragma no longer runs during baseline generation). Its role — schema-diff anchor for `verifyCommonMainVehiCareDatabaseMigration` — is unaffected: the verify task is green and `generate` reproduces the file byte-identically (`BASELINE_REPRODUCIBLE`). Per the plan, `1.db` is never hand-edited, so no post-processing was applied.

---

**Total deviations:** 3 auto-fixed (2 Rule 1, 1 Rule 3) + 4 retracted criteria (all consequences of deviation #1)
**Impact on plan:** The WAL fix is the plan's single most important deviation: without it the app cannot start on iOS at all. The storage contract (schema shape, queries, repository API, Koin graph, baseline) landed exactly as planned; only the WAL *mechanism* changed, and the plan's two direct greps for it were replaced with stronger runtime evidence. No scope creep.

## Issues Encountered

- `generateCommonMainVehiCareDatabaseSchema` cannot run in the same Gradle invocation as the compile/test tasks that consume the generated interface — SQLDelight's schema task output lives inside the source-set input dir, so Gradle 9.5 fails its implicit-dependency validation. Resolved by running the schema task standalone (two invocations). Affects any CI wiring in 01-05: keep generation and verification in separate Gradle calls.
- The plan's Task 3 text claims "the `insert` mutation returns `Unit`"; the generated `insert(...)` actually returns `QueryResult<Long>` (rows affected — still *not* the new id). The implementation reads the id with `lastInsertId().executeAsOne()` inside the same `transactionWithResult`, which is the behavior the plan mandates; the `NO_RETURNING_OK` grep is clean.

## Known Stubs

| Stub | File | Reason / Resolution |
|------|------|---------------------|
| `photoDeleter: suspend (String?) -> Unit = {}` | `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` (line 29) | Intentional seam mandated by the plan: `PhotoStorage` does not exist until plan 01-08, which must replace the `{}` default with `PhotoStorage::delete` when it binds the repository in Koin. `delete()` already reads `photo_path` and invokes the seam; only the default is inert. |

No other stubs: every observable path (insert/update/observe/delete) is wired to the real database and covered by passing tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **01-04 (Android host + navigation):** `MainActivity.kt` currently calls `App()` with no platform module; it must pass `App(platformModule(applicationContext))` (or register `single<Context>` some other way), otherwise the `DriverFactory` fallback throws its explicit "requires an application Context" error. ViewModel registrations were intentionally NOT added here (types arrive with 01-04/01-07/01-09).
- **01-05 (platform drivers + CI):** keep schema generation and test/verify tasks in separate Gradle invocations (see Issues). The runtime WAL guards for both platforms are this plan's deferred verification.
- **01-08 (photo storage):** replace the `photoDeleter` no-op default with `PhotoStorage::delete` (Known Stubs above).
- **All later plans:** all database access must stay on the injected `Dispatchers.IO.limitedParallelism(1)` lane (T-01-15), and generated SQLDelight types must not escape `com.vehicare.data`.

---
*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-18*

## Self-Check: PASSED

- All 14 claimed artifacts found on disk (schema, baseline, domain models, driver factories, DatabaseFactory, PlatformModule, mapper, repository interface/impl, both test files) + this SUMMARY.
- All 6 claimed commits found in `git log`: `934ea49`, `0a4e7ac`, `b850f1f`, `7681ce5`, `5e9f9ad`, `7306790`.
