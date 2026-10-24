---
phase: 03-reminders-dashboard
plan: 02
subsystem: domain
tags: [kotlin, kmp, commonMain, pure-functions, state-machine, ordering, kotlinx-datetime, commonTest]

# Dependency graph
requires:
  - phase: 03-reminders-dashboard
    plan: 01
    provides: ReminderRule/TypeBaseline/VehicleStats models, the ReminderAxis + five-member DueState vocabulary, selectTypeBaselines/selectVehicleStats semantics
  - phase: 01-foundation-vehicle-management
    provides: ServiceDates.fromStorage/toStorage UTC storage convention, the commonTest + kotlin.test precedent
provides:
  - ReminderEvaluation(state, axis, urgency, cycleKey) and ReminderRow(rule, evaluation)
  - ReminderStatus.evaluate — the only due-state decision, on both axes, with C-29's distance-first tie
  - ReminderStatus.evaluateAll — the only join of rules to type baselines and vehicle odometer
  - ReminderStatus.cycleKey — the D-51 once-per-cycle identity over the resolved baselines
  - ReminderOrdering.sortRules/topRule/sortVehicles and VehicleReminderGroup
  - 46 new commonTest assertions (31 status + 15 ordering) running on Android host and iOS simulator
affects: [03-03 copy layer, 03-04 reminders screen, 03-05 detail summary row, 03-06 dashboard, 03-07 worker + notifications]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One pure object per decision: the due state, the axis choice, the urgency ratio and the cycle key live in ReminderStatus; the four buckets and the tie-break chain live in ReminderOrdering"
    - "Clock-free evaluation: 'today' is a parameter and is never read inside the domain object, so the time axis is testable with fixed dates"
    - "Loud corruption: require() rejects every row the editor's C-35 gate could not have produced, so a bad row fails in tests instead of rendering a wrong sentence"
    - "Total comparator chains: every sort key ends in a unique id, so sortedWith (stable) cannot reshuffle two identical snapshots"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/domain/ReminderOrdering.kt
    - shared/src/commonTest/kotlin/com/vehicare/domain/ReminderStatusTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/domain/ReminderOrderingTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt

key-decisions:
  - "ReminderStatus.evaluate takes typeBaseline and currentMileageKm as separate parameters instead of a pre-merged Baseline: the two D-49 fallbacks then resolve in exactly one place, and cycleKey can hash the resolved pair"
  - "cycleKey hashes the resolved baselines, never typeBaseline itself — a km-only rule with no entry of its type must re-arm when the vehicle's latest mileage moves (D-49 under D-47)"
  - "An exact urgency tie names the DISTANCE axis at the source, so the state and the ordering agree about the boundary without the ordering layer re-deciding"
  - "Unevaluable (NoMileageData) rows are ranked last inside the Upcoming bucket by an explicit comparator key in addition to their -1.0 urgency, because the engine's progress is not structurally guaranteed to be >= 0"
  - "ReminderRow lives in ReminderStatus.kt, so the evaluation and the row it produces are declared together and no consumer can construct one without the other"

patterns-established:
  - "Due-state check order is binding and shared by both axes: overdue → exact-zero DueNow → lead window → upcoming"
  - "Bucket rank 0 covers Overdue and DueNow together; NoMileageData is filed under Upcoming rather than given a fifth bucket"
  - "Ordering sorts in Kotlin, never SQL: urgency spans km and days, and the tie-break chain would make an unindexable query"

requirements-completed: [REM-01, REM-02]

# Metrics
duration: 10min
completed: 2026-10-24
---

# Phase 3 Plan 02: Due-State Engine & Ordering Summary

**One pure `commonMain` state machine that decides due state, deciding axis, urgency and cycle key for both intervals — plus the four-bucket ordering every Phase 3 surface shares.**

## Performance

- **Duration:** ~10 min
- **Started:** 2026-10-24T01:52:00Z (08:52 WIB)
- **Completed:** 2026-10-24T02:00:00Z (09:00 WIB)
- **Tasks:** 2 of 2
- **Files modified:** 4 (3 created, 1 modified)

## Accomplishments

- `ReminderStatus.evaluate` is now the only place in the app that decides "when is this due": both axes, C-29's distance-first exact tie, the `DueNow` boundary ahead of the lead check, and D-50's honest "no lead means no Due-soon band". The time axis uses `LocalDate.plus(months, DateTimeUnit.MONTH)` and `daysUntil`, so 31 Jan + 1 month is 28 Feb and six months is 181 days in 2026 — never a 30-day month.
- D-49's honesty rule is mechanical rather than aspirational: a distance-only rule with no odometer returns `NoMileageData` with `axis = null` and `urgency = -1.0`, while a dual-axis rule in the same state reports its time axis. No odometer figure is ever invented.
- `evaluateAll` is the single join of rules to `(vehicle, type)` baselines and per-vehicle odometer, matching type labels case-insensitively and never applying another vehicle's baseline. Every later surface (list, summary row, dashboard, worker) consumes it instead of re-deriving the lookup.
- `cycleKey` covers the resolved km baseline, the resolved date baseline, both intervals and both leads, giving D-51's three properties: a service re-arms, an edit re-arms, and the same baseline yields the same key so the conditional `markNotified` claim affects zero rows.
- `ReminderOrdering` ships the four buckets (overdue+due-now / due-soon / upcoming+unevaluable / disabled) and the full tie-break chain (urgency desc → distance before time → rule id → lowercased vehicle name → vehicle id), with D-16's newest-first order preserved inside the fourth group.
- 46 new `commonTest` assertions (31 status + 15 ordering) run green on both the Android host and the iOS simulator — the suite is the phase's cheapest and widest net, and it needs no emulator, no resources and no clock.

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement the due-state machine, its urgency and its cycle key** — `975ab5e` (feat)
2. **Task 2: Order rules and dashboard vehicles by bucket, urgency and the locked tie-break chain** — `4ac3dbd` (feat)

**Plan metadata:** `docs(03-02): complete due-state engine & ordering plan` (this SUMMARY; STATE.md and ROADMAP.md are the orchestrator's to write).

_No TDD tasks in this plan; `tdd_mode` is false for the phase._

## Verification

Every gate below was run and its observed result recorded. The plan lists the Gradle commands as separate lines, and each was run as a separate `./gradlew` invocation (the 03-01 note about combined-invocation task-graph failures still applies).

| Gate | Command | Observed |
|---|---|---|
| Task 1, Android host | `./gradlew :shared:testAndroidHostTest --tests "com.vehicare.domain.ReminderStatusTest"` | **PASS** — 31 tests, 0 failures |
| Task 1, iOS simulator | `./gradlew :shared:iosSimulatorArm64Test --tests "com.vehicare.domain.ReminderStatusTest"` | **PASS** — `BUILD SUCCESSFUL` |
| Task 1, no clock / no flow | `test "$(grep -c 'Clock\.\|Instant\.\|System\.currentTimeMillis\|asFlow' shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt)" = "0"` | **PASS** after rewording two KDoc lines — see Deviations §1. The object reads no clock; the first draft *named* `Clock.System.now()` in prose explaining why it is forbidden |
| Task 2, Android host | `./gradlew :shared:testAndroidHostTest --tests "com.vehicare.domain.ReminderOrderingTest"` | **PASS** — 15 tests, 0 failures |
| Task 2, iOS simulator | `./gradlew :shared:iosSimulatorArm64Test --tests "com.vehicare.domain.ReminderOrderingTest"` | **PASS** — `BUILD SUCCESSFUL` |
| Task 2, no resource/Compose/SQL | `test "$(grep -c 'Res\.string\|compose\|ORDER BY' shared/src/commonMain/kotlin/com/vehicare/domain/ReminderOrdering.kt)" = "0"` | **PASS** after rewording one KDoc line — see Deviations §1 |
| Full Android host suite | `./gradlew :shared:testAndroidHostTest` | **PASS** — 126 tests, 0 failures, 0 errors (was 80 before this plan) |
| Full iOS simulator suite | `./gradlew :shared:iosSimulatorArm64Test` | **PASS** — 178 tests, 0 failures, 0 errors (was 132 before this plan) |
| iOS compile | `./gradlew :shared:compileKotlinIosSimulatorArm64` | **PASS** — `BUILD SUCCESSFUL` |

Additional evidence beyond the plan's gates:

- `grep -nE '^import' shared/src/commonMain/kotlin/com/vehicare/domain/ReminderOrdering.kt` → none at all (pure Kotlin, no imports).
- `ReminderStatus.kt` imports only `com.vehicare.util.ServiceDates`, `kotlinx.datetime.{DateTimeUnit, LocalDate, daysUntil, plus}` — nothing from `com.vehicare.data`, `com.vehicare.di`, `com.vehicare.ui` or a platform package.
- Both test files exceed the plan's `min_lines` (496 and 259 vs. 120 and 90).
- No file deletions in either task commit (`git diff --diff-filter=D --name-only HEAD~2 HEAD` is empty).

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt` — plan 01's `ReminderAxis`/`DueState` vocabulary untouched; added `ReminderEvaluation`, `ReminderRow` and `object ReminderStatus` (`evaluate`, `evaluateAll`, `cycleKey`) with private axis readers and the two state builders.
- `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderOrdering.kt` — `VehicleReminderGroup` plus `object ReminderOrdering` (`sortRules`, `topRule`, `sortVehicles`), the bucket constants and the two comparator chains.
- `shared/src/commonTest/kotlin/com/vehicare/domain/ReminderStatusTest.kt` — 31 cases: both axes, the boundary, both leads, both D-49 fallbacks, urgency, the `require` gates, cycle-key stability and every re-arm case, and the six `evaluateAll` join cases.
- `shared/src/commonTest/kotlin/com/vehicare/domain/ReminderOrderingTest.kt` — 15 cases: the four buckets, every tie-break in isolation, the full vehicle chain in one near-tie, purity and determinism.

## Decisions Made

- **`evaluate` takes `typeBaseline` and `currentMileageKm` separately, not a merged `Baseline`.** Research sketched a single `Baseline?`; keeping the two inputs apart is what lets the two D-49 fallbacks (km → vehicle's latest mileage, date → rule creation) resolve in one place and lets `cycleKey` hash the *resolved* pair rather than the input shape.
- **The exact-urgency tie names `DISTANCE` inside the engine.** C-29's tie-break (a) is an ordering rule, but applying it only in the comparator would let the sentence name TIME while the ordering assumed DISTANCE. Deciding it at the source keeps one answer.
- **`NoMileageData` is filed under the Upcoming bucket, not a fifth bucket.** A fifth bucket would let "vehicle whose only rule is unevaluable" look like "vehicle with no reminder", which is a different card sentence (UI-SPEC §dashboard ordering).
- **`topRule` computes the group's rule once per group in `sortVehicles`,** not once per comparison — the comparator would otherwise re-sort each vehicle's rows O(n log n) times.

## Deviations from Plan

### 1. [Plan gate] Two KDoc comments were reworded so the plan's own grep gates can pass

- **Found during:** Task 1 and Task 2 verification.
- **Issue:** Both gates are `grep -c '…' = 0` over the implementation file. The first drafts *documented the prohibition* using the forbidden tokens — `Clock.System.now()` in `ReminderStatus`'s "clock-free on purpose" KDoc, and `ORDER BY` in `ReminderOrdering`'s "sort in Kotlin, not SQL" KDoc — so each gate reported `count=1` while the code itself had zero clock reads and zero SQL.
- **Fix:** Reworded the prose ("reads the current time", "a SQL sort clause"). No code, no behaviour, no API change. This is the same class of defect 03-01 reported and chose to record rather than satisfy; here the gate could be made genuinely green at zero cost, which is the better outcome.
- **Files modified:** the two domain files (part of their task commits).
- **Verification:** both gates now report `count=0` and echo `GATE_PASS`.

### 2. [Implementation refinement] Unevaluable rows are ranked last inside the Upcoming bucket explicitly

- **Found during:** Task 2 design.
- **Issue:** The plan ranks `NoMileageData` last inside bucket 2 "by giving it an urgency of `-1.0` … while every evaluable upcoming rule is `>= 0.0`". That invariant holds for normal data but is not structural: `progress = (currentMileageKm - kmBaseline) / intervalKm` goes negative if an odometer is ever logged below its baseline, which would sort a corrupt-but-evaluable row behind an unevaluable one.
- **Fix:** `compareWithinBucket` checks `state == NoMileageData` first, so unevaluable always trails evaluable rows inside bucket 2; urgency remains the next key, unchanged. Exactly four buckets still exist, and no observable behaviour changes for valid data.
- **Files modified:** `ReminderOrdering.kt` (Task 2 commit).
- **Verification:** `noMileageDataLandsAfterEveryEvaluableUpcomingRowAndBeforeDisabledRows` asserts the contract.

---

**Total deviations:** 0 auto-fixed bugs; 1 plan-gate rewording and 1 documented implementation refinement.
**Impact on plan:** No scope creep, no unplanned files, no API divergence from what plans 03-03…03-07 expect (`evaluateAll(rules, baselines, stats, today)`, `ReminderRow`, `ReminderEvaluation`, `sortRules`/`topRule`/`sortVehicles`, `VehicleReminderGroup(vehicleId, vehicleName, rows)` — all verified against those plans' `read_first` sections).

## Issues Encountered

- **Three of my own test assertions failed on the first run** (`halfwayThroughADistanceInterval…`, `sixMonthsAreCalendarMonths…`, `aTypeLabelDifferingOnlyInCase…`). All three were fixture mistakes, not engine bugs: with no type baseline the km baseline *is* the current mileage (so `kmLeft` is always a full interval — D-49), and 30 Jun → 1 Jul leaves 1 day, not 180. Fixed the fixtures; the engine was already correct. Recorded so the two fix-up runs in the working tree are not mistaken for churn.
- **`DueState.Overdue(daysOver = 1)` did not compile** — both payloads are named parameters. One fixture fixed before the first successful Task 2 run.
- **No Gradle task-graph problem this time.** Each gate was run as its own invocation, as 03-01 recommended.

## User Setup Required

None — no dependency, no build-file change, no resource, no permission. This plan is pure Kotlin plus `commonTest`.

## Known Stubs

None. The engine and the ordering are complete for their scope; nothing is hardcoded, placeholder or mock-fed. They have no production consumer **yet** — that is the phase's plan order, not a stub: plans 03-04/03-05/03-06 call `evaluateAll` + `sortRules`/`topRule`/`sortVehicles`, and plan 03-07's worker claims delivery on `cycleKey`.

## Next Phase Readiness

- **Ready for 03-03 (repository + copy layer):** `ReminderEvaluation.state` is the only thing the copy layer needs from the engine, so `ReminderCopy` can be written and tested against a stable vocabulary. `cycleKey` is final, so `ReminderRepositoryImpl.markNotifiedIfCycleChanged` can be written against its exact string.
- **Ready for 03-04/03-05/03-06 (UI):** `ReminderStatus.evaluateAll` and `ReminderOrdering` are on disk with the exact signatures those plans' `read_first` sections name. Both lists sort in Kotlin, so a ViewModel can call them inside a `combine` on every emission — neither function mutates its input.
- **Ready for 03-07 (worker):** `ReminderEvaluation.axis` distinguishes which delivery path owns a due row on iOS, and `ReminderEvaluation.cycleKey` is deterministic across evaluations of the same baseline — the two facts the three-case time path needs.
- **Watch item:** `ReminderStatus.evaluate` *throws* on a corrupt row by design (T-03-05). The worker and the ViewModels must therefore treat an `IllegalArgumentException` from a stored rule as an error state rather than letting it escape a `Flow` transform — `evaluateAll` does not catch it.

---

*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*

## Self-Check: PASSED

- All four files listed in "Files Created/Modified" exist on disk.
- Both task commits exist in history: `975ab5e` (Task 1), `4ac3dbd` (Task 2).
- No file deletions in either commit (`git diff --diff-filter=D --name-only HEAD~2 HEAD` empty).
- No untracked files left behind (`git status --short` shows only the pre-existing `.planning/STATE.md` modification, which predates this plan and was not touched).
- `.planning/STATE.md` and `.planning/ROADMAP.md` were **not** modified by this executor.
