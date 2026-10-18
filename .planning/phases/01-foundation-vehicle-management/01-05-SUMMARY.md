---
phase: 01-foundation-vehicle-management
plan: 05
subsystem: ci-and-data-safety
tags: [github-actions, sqldelight, migration-guard, wal, instrumentation, kotlin-native-tests, dat-03, dat-04]

# Dependency graph
requires:
  - phase: 01-foundation-vehicle-management
    provides: 01-01 androidDeviceTest source-set recipe (D-7) + registered migration-guard task pair; 01-03 committed 1.db baseline, migrations/ anchor, driver-construction WAL mechanism
provides:
  - ".github/workflows/ci.yml — per-source-set migration verification + committed-baseline git-diff guard + both platforms built and tested from one commit (DAT-04, DAT-02)"
  - "Real-driver WAL proof on each platform: iosSimulatorArm64Test + instrumented connectedAndroidDeviceTest assert journal_mode==wal before any write, -wal sidecar after the first insert, and mode persistence on a second connection (DAT-03)"
  - "Recorded evidence: the schema-drift guard fires (exit 1) while the per-source-set verify task alone passes vacuously — RESEARCH Pitfall 1 demonstrated, not assumed"
affects: [01-06, 01-07, 01-08, 01-09, 01-10, phase-2-service-log, phase-3-reminders]

# Tech tracking
tech-stack:
  added: [GitHub Actions (checkout/setup-java/setup-gradle v4 only), Temurin 21 (runner JDK)]
  patterns:
    - "DAT-04 gate = per-source-set verifyCommonMainVehiCareDatabaseMigration + generate…Schema && git diff --exit-code on the committed baseline"
    - "WAL runtime proof = same three assertions per platform, in launch order (mode-before-write, sidecar-after-insert, persistence-on-second-connection)"
    - "Persistence assert on Android uses a bare second driver that never runs the pragma — cannot re-assert the mode it observes"
    - "Least-privilege CI: permissions contents: read, zero secrets"

key-files:
  created:
    - .github/workflows/ci.yml
    - shared/src/iosTest/kotlin/com/vehicare/data/db/WalModeTest.kt
    - shared/src/androidDeviceTest/kotlin/com/vehicare/data/db/WalModeTest.kt
  modified: []
key-decisions:
  - "git diff --exit-code on the regenerated baseline is the load-bearing DAT-04 control — the per-source-set verify task alone passed vacuously (exit 0) on a probe column with no .sqm (observed)"
  - "CI Java = Temurin 21 — the RESEARCH Environment row that fixes the GitHub Actions runner; supports Gradle 9.5.1 / AGP 9.1.1 / Kotlin 2.4.20"
  - "Android persistence assert uses a bare AndroidSqliteDriver (no pragma) so the second-connection check cannot masquerade as a re-set mode"
  - "Workflow gets permissions: contents: read (Rule 2 hardening, T-01-32 adjacent)"
  - "KMP task names used everywhere (generateCommonMainVehiCareDatabaseSchema / verifyCommonMainVehiCareDatabaseMigration), correcting ROADMAP's generateMainVehiCareSchema / verifySqlDelightMigration wording"

requirements-completed: [DAT-03, DAT-04]

# Metrics
duration: 11 min
completed: 2026-10-18
---

# Phase 1 Plan 05: WAL proof tests + migration-verified CI Summary

**CI now blocks schema drift with a probe-proven guard (aggregate-task vacuity observed, not assumed), and WAL-from-first-launch is proven by real-driver tests on the iOS driver and on a real Android emulator — both platforms green from one commit.**

## Performance

- **Duration:** 11 min
- **Started:** 2026-10-18T17:48:26Z
- **Completed:** 2026-10-18T17:59:50Z
- **Tasks:** 3/3 (Task 3 is evidence-only — deliberately no commit)
- **Files created:** 3 source/CI files

## Accomplishments

- `migration-guard` CI job: `verifyCommonMainVehiCareDatabaseMigration` **plus** `generateCommonMainVehiCareDatabaseSchema` + `git diff --exit-code -- shared/src/commonMain/sqldelight/databases`. The aggregate task appears **zero** times in the workflow
- `android` job (ubuntu, needs migration-guard): `:androidApp:assembleDebug` + `:shared:testAndroidHostTest`; `ios` job (macos-latest, needs migration-guard): Xcode selection + `:shared:linkDebugFrameworkIosSimulatorArm64` + `:shared:iosSimulatorArm64Test` — DAT-02's single-shared-codebase claim is now evidenced from CI rather than a dev machine
- `WalModeTest` on both platforms asserting the same three facts in launch order: `journal_mode == wal` **before any insert**, `-wal` sidecar exists after the first insert, second connection still reports `wal`
- Both tests use scratch names (`wal-probe-<random>.db`), never the app's `vehicare.db`, and delete the database plus sidecars afterwards
- Guard-fire experiment (Task 3) executed and recorded below, tree restored clean and re-verified (`GUARD_PASSES_ON_CLEAN_TREE`)

## Task Commits

1. **Task 1: CI workflow with real migration enforcement** — `32b8ddf` (chore)
2. **Task 2: Per-platform WAL proof tests** — `d41d059` (test)
3. **Task 3: Probe-and-revert guard-fire evidence** — no commit (plan: "Do not commit any probe change"; deliverable is this section)

## Task 3 — Guard-Fire Evidence (probe-and-revert, tree restored)

| # | Step | Observed |
|---|------|----------|
| 1 | Pre-state | `git status --porcelain -- shared/src/commonMain/sqldelight` → empty (clean); `verifyCommonMainVehiCareDatabaseMigration` → **exit 0** (success) |
| 2 | Probe added | `probe TEXT` column appended to the `vehicle` CREATE TABLE in `Vehicle.sq`; **no** `.sqm` file |
| 3 | `generateCommonMainVehiCareDatabaseSchema` then `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` | **exit 1 — the guard FIRES.** Diff hunk: `diff --git a/shared/src/commonMain/sqldelight/databases/1.db … index 7e78e97..7697516 … Binary files … differ` (the regenerated baseline picked up the probe column) |
| 4 | `verifyCommonMainVehiCareDatabaseMigration` with the probe in place | **exit 0 — PASSES vacuously.** This is RESEARCH Pitfall 1 demonstrated concretely: the per-source-set task verifies the migration sequence only and has nothing to do when no `.sqm` exists. **The `git diff` committed-baseline guard is therefore the load-bearing DAT-04 control.** |
| 5 | Restore | `git checkout --` (targeted: the two paths this task touched) → `git status --porcelain -- shared/src/commonMain/sqldelight` empty (`TREE_CLEAN`); `grep -c 'probe' Vehicle.sq` = **0** |
| 6 | Re-verify | verify task → exit 0; generate + `git diff --exit-code` → `GUARD_PASSES_ON_CLEAN_TREE`; no probe change ever committed |

## Verification Results

| # | Check | Result |
|---|-------|--------|
| 1 | ci.yml contains `verifyCommonMainVehiCareDatabaseMigration` | **PASS** (`GUARD_WIRED`) |
| 2 | ci.yml contains `generateCommonMainVehiCareDatabaseSchema` **and** `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` | **PASS** (`DIFF_GUARD_WIRED`) |
| 3 | Zero occurrences of the aggregate task name | **PASS** (grep count 0 — the explaining comment was reworded to keep the literal count at zero) |
| 4 | Android job runs `:androidApp:assembleDebug` + `:shared:testAndroidHostTest`; iOS job on `macos-latest` runs `:shared:linkDebugFrameworkIosSimulatorArm64` + `:shared:iosSimulatorArm64Test` | **PASS** (`JOBS_OK`, `ANDROID_HOST_TESTS_OK`, `IOS_LINK_OK`) |
| 5 | ci.yml contains no `secrets.` reference and no credential | **PASS** (grep count 0; only first-party actions pinned to major tags) |
| 6 | Java version matches the gate choice | **PASS** (`temurin` + `'21'` in all three jobs) |
| 7 | `./gradlew :shared:iosSimulatorArm64Test` exits 0 with `WalModeTest` passing | **PASS** — test-results XML: `walIsActiveFromFirstLaunch` 1 test / 0 failures / 0 errors, fresh timestamp 17:54:40Z |
| 8 | `./gradlew :shared:connectedAndroidDeviceTest` passes with API 26+ device attached | **PASS** — API 28 `Medium_Phone` AVD booted headless for the run; connected XML: 6 tests / 0 failures / 0 errors / 0 skipped incl. `com.vehicare.data.db.WalModeTest.walIsActiveFromFirstLaunch` |
| 9 | Both files assert `journal_mode == wal` before any insert, `-wal` sidecar after insert, second connection wal | **PASS** (assertion order in both files; `journal_mode` grep 4× ios / 5× android) |
| 10 | Test-scoped DB name + cleanup; never the app's `vehicare.db`; no hardcoded absolute path | **PASS** (`wal-probe-<random>.db`; `NO_HARDCODED_PATHS`; iOS removes db+`-wal/-shm/-journal`, Android `context.deleteDatabase`) |
| 11 | Guard-fire evidence with observed exit codes | **PASS** (Task 3 table above) |
| 12 | Restored-tree checks | **PASS** (`TREE_CLEAN`, probe grep 0, `GUARD_PASSES_ON_CLEAN_TREE`) |

## Decisions Made

- **CI Java = Temurin 21.** The Wave 0 gate landed option-b (Gradle ≥ 9.1.0 + JDK 25+ locally), and the RESEARCH Environment Availability row that literally fixes "GitHub Actions runner" is `ubuntu-latest + Temurin 21` ("Any CI with Java 21"). Temurin 21 runs Gradle 9.5.1, AGP 9.1.1 and Kotlin 2.4.20, and is the only Java row the research commits to for CI. Local execution continued on the installed JDK 26 for the Gradle runs.
- **Android persistence assert uses a bare `AndroidSqliteDriver`.** Re-running `PRAGMA journal_mode=wal` on a second connection can never fail, so routing the second connection through `DriverFactory` (which re-asserts the mode) would weaken the Android test into a smoke check — the plan explicitly forbids that. The second connection is a plain driver that never executes the pragma, proving the WAL marker persists in the database file header itself. On iOS the factory is the mechanism (SQLiter's `JournalMode.WAL` default), so a factory-built second connection is the honest probe there.
- **KMP task names used everywhere** (`generateCommonMainVehiCareDatabaseSchema`, `verifyCommonMainVehiCareDatabaseMigration`), correcting ROADMAP.md Architecture Notes' `generateMainVehiCareSchema` / `verifySqlDelightMigration` wording as the plan directs.
- **Emulator lifecycle:** the `Medium_Phone` (API 28) AVD was booted headless for exactly the instrumented run and killed immediately after (`adb emu kill`) — no resident background services.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Security] Added `permissions: contents: read` to the workflow**

- **Found during:** Task 1
- **Issue:** Without an explicit permissions block, the workflow inherits the repository's default GITHUB_TOKEN scope; a pull-target pipeline with build tools only needs read access. The threat register's melt-away for T-01-32 ("no secrets, no credential") is stronger under least privilege.
- **Fix:** top-level `permissions: { contents: read }`. No other trust-boundary change.
- **Files modified:** .github/workflows/ci.yml
- **Verification:** grep `permissions` present; `secrets.` count remains 0
- **Committed in:** `32b8ddf`

**2. [Documented strengthening, not a defect] Android second connection is a bare driver (see Decisions Made)**

- **Found during:** Task 2 authoring
- **Rationale:** routing the persistence assert through the factory would make it unfalsifiable; the plan's own "do not weaken the Android test" rule settles it.
- **Committed in:** `d41d059`

**Total deviations:** 1 auto-fixed (Rule 2) + 1 documented strengthening. **Impact:** none on plan scope; both make the enforcement they touch harder to fake.

## Issues Encountered

- None blocking. Device-test note: the connected run executes the whole `androidDeviceTest` suite (6 tests incl. pre-existing `SharedCommonTest` + `VehicleEntityMapperTest` — all green).

## Known Stubs

None — the workflow and both WAL tests are fully wired; Task 3's probe column was temporary and never committed.

## User Setup Required

None - no external service configuration required (workflow contains no secrets; CI relies on runner defaults).

## Next Phase Readiness

- **CI is armed:** any push/PR that mutates `Vehicle.sq` without an accompanying `.sqm` fails the `git diff` guard; both platforms build and test in the same pipeline.
- **01-06 (list polish + add FAB) and later plans** attach to the unchanged shell/routes; nothing in this plan alters app surfaces.
- **Phases 2+ schema work:** add `.sqm` migrations in `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/` (currently `.gitkeep`-anchored empty) and regenerate/commit `1.db` — CI now blocks the missed-migration case (probe-proven).
- **For WAL regressions:** `WalModeTest` on either platform is the tripwire; CI runs the iOS half of it on macOS runners.

---
*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-18*

## Self-Check: PASSED

- All 4 claimed artifacts on disk: `.github/workflows/ci.yml`, both `WalModeTest.kt` files (iosTest + androidDeviceTest), this SUMMARY.
- Task commits verified in `git log`: `32b8ddf` (chore, Task 1), `d41d059` (test, Task 2); Task 3 intentionally commitless (probe never committed; git log unchanged by it).
- Working tree clean except planning artifacts that the docs commit captures.
