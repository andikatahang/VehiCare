---
phase: 03-reminders-dashboard
plan: 03
subsystem: database
tags: [sqldelight, kotlin-multiplatform, reminders, compose-resources, notification-identity, cascade-delete]

# Dependency graph
requires:
  - phase: 03-01
    provides: v4 reminder_rule schema, the write/read queries and the domain models this repository maps
  - phase: 03-02
    provides: DueState and ReminderOrdering, the sealed states ReminderCopy resolves to sentences
provides:
  - ReminderRepository/ReminderRepositoryImpl over the v4 schema (rules, baselines, vehicle stats)
  - atomic once-per-cycle claim via one conditional UPDATE, plus separate idempotent markArmed bookkeeping
  - D-64 vehicle-delete cascade that hands the deleted rule ids to the notification hook
  - the complete Phase 3 string block in strings.xml
  - ReminderCopy, the single resource-bound due-state/interval sentence resolver
  - ReminderNotificationId, the strict "v<vehicleId>:r<ruleId>" encode/parse contract
  - RupiahFormat.formatKmValue (grouped digits, no unit)
affects: [03-04, 03-05, 03-06, 03-07, 03-08]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Once-per-cycle claim: a single conditional UPDATE whose affected-row count is the only notify decision; no read-then-write path"
    - "Pure spec layer + suspend resolver + composable twin, so sentence selection is unit-testable without a ResourceContainer"
    - "Strict string tap payload instead of platform-boxed numbers (no NSDictionary/NSNumber bridging)"
    - "Change hook receives rule-id lists after the transaction commits, so the scheduler can cancel precisely"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/data/ReminderRuleEntityMapper.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ReminderRepository.kt
    - shared/src/commonMain/kotlin/com/vehicare/data/ReminderRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/vehicare/util/ReminderCopy.kt
    - shared/src/commonMain/kotlin/com/vehicare/util/ReminderNotificationId.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/ReminderRepositoryTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/util/ReminderNotificationIdTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
    - shared/src/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq
    - shared/src/commonMain/composeResources/values/strings.xml
    - shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt
    - shared/src/iosTest/kotlin/com/vehicare/data/VehicleRepositoryTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/util/RupiahFormatTest.kt

key-decisions:
  - "The claim is one conditional UPDATE; markArmed is separate idempotent bookkeeping that never gates a post."
  - "Baseline and vehicle-stat mappers return non-null domain values because GROUP BY guarantees a row; no 0L sentinel is substituted."
  - "ReminderCopy splits pure sentence selection (dueSpec/intervalSpec) from the suspend and composable resolvers so the choice is assertable in commonTest."
  - "A malformed notification identifier returns null and produces no navigation; only the exact v<id>:r<id> shape is accepted."
  - "Every notification-invalidating mutation reports a rule-id list to the hook after commit, never a bare vehicle id."

patterns-established:
  - "Claim-by-affected-rows: delivery depends on affected == 1L, never on an in-memory set."
  - "Copy has one producer: no other file builds a due sentence, a number+unit or a date string."

requirements-completed: [REM-01, REM-02, REM-03]

# Metrics
duration: 7min
completed: 2026-10-24
---

# Phase 3 Plan 03: Reminder Persistence, Copy Resolver & Notification Identity Summary

**Reminder rules persist behind an atomic once-per-cycle claim, vehicle deletion cascades to their rules, and one resource-bound resolver plus a strict `v<id>:r<id>` payload make every due sentence and tap deterministic.**

## Performance

- **Duration:** 7 min (first artifact write to final task commit)
- **Started:** 2026-10-24T09:02:54+07:00
- **Completed:** 2026-10-24T09:09:21+07:00
- **Tasks:** 3
- **Files modified:** 14

## Accomplishments

- `ReminderRepository`/`ReminderRepositoryImpl` expose rules, per-type baselines and vehicle stats as Flows over the v4 schema, with insert/update/setEnabled clearing both markers and never rewriting `created_at_epoch_ms`.
- The once-per-cycle claim is a single conditional `UPDATE`; its affected-row count decides delivery, so two evaluations of the same cycle cannot both notify. `markArmed` stays separate idempotent bookkeeping and never touches `notified_cycle_key`.
- Vehicle deletion deletes its reminder rules inside the existing transaction (D-64), collecting the rule ids before the delete so the post-commit hook can cancel exactly those notifications.
- The complete Phase 3 string block is appended verbatim (four plurals included), and `ReminderCopy` is the only producer of due-state, interval and status sentences for screens and both platforms' notification bodies.
- `ReminderNotificationId` round-trips large ids and rejects every malformed shape, so a tap can never navigate to a wrong or negative vehicle id.

## Task Commits

Each task was committed atomically:

1. **Task 1: Persist reminder rules with the atomic once-per-cycle claim** - `eb4938d` (feat)
2. **Task 2: Delete a vehicle's reminder rules in the existing cascade** - `0c7260d` (feat)
3. **Task 3: Ship the Phase 3 strings, the one sentence resolver and the notification id contract** - `8c7c6c9` (feat)

**Plan metadata:** this commit (docs: complete plan)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/data/ReminderRuleEntityMapper.kt` — generated-row → domain mapping for rules, type baselines and vehicle stats; the only file that sees SQLDelight row types.
- `shared/src/commonMain/kotlin/com/vehicare/data/ReminderRepository.kt` — the reminder storage contract (observe, save, setEnabled, markNotified, markArmed, delete) with the marker semantics documented.
- `shared/src/commonMain/kotlin/com/vehicare/data/ReminderRepositoryImpl.kt` — SQLDelight implementation on the serialized IO lane; claim via `reminderRuleQueries.markNotified`, armed via `markArmed`, hook calls after commit.
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` — collects the vehicle's rule ids, deletes rules before the vehicle row in the same transaction, then calls `onVehicleDeleted(id, ruleIds)`.
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` — binds `ReminderRepositoryImpl` to `ReminderRepository`.
- `shared/src/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq` — adds the additive `lastInsertId` query (`SELECT last_insert_rowid()`); no schema change.
- `shared/src/commonMain/composeResources/values/strings.xml` — the full Phase 3 string block appended before `</resources>`, verbatim from UI-SPEC lines 423–523.
- `shared/src/commonMain/kotlin/com/vehicare/util/ReminderCopy.kt` — `dueSpec`/`intervalSpec` pure decisions, suspend resolvers and composable twins; the single sentence producer (C-28/C-40).
- `shared/src/commonMain/kotlin/com/vehicare/util/ReminderNotificationId.kt` — strict `"v<vehicleId>:r<ruleId>"` encode and `vehicleIdOf` parse; malformed → null.
- `shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt` — adds `formatKmValue` (grouped digits, no unit); `formatMileage`/`formatRupiah`/`parseRupiahDigits` unchanged.
- `shared/src/iosTest/kotlin/com/vehicare/data/ReminderRepositoryTest.kt` — 9 real-driver tests for round-trip, re-arm, claim true→false→true, armed bookkeeping and baseline pairing.
- `shared/src/iosTest/kotlin/com/vehicare/data/VehicleRepositoryTest.kt` — adds the cascade test proving the deleted vehicle's rules are gone while another vehicle's survive.
- `shared/src/commonTest/kotlin/com/vehicare/util/ReminderNotificationIdTest.kt` — 4 tests: exact encoding, round-trip incl. `Long.MAX_VALUE`, and rejection of every listed malformed shape plus uppercase/negative/overflow/trailing variants.
- `shared/src/commonTest/kotlin/com/vehicare/util/RupiahFormatTest.kt` — adds `formatKmValue` grouping cases and asserts it carries no `" km"` while `formatMileage` still does.

## Decisions Made

- **Claim and armed are two different facts.** `markNotified` is the only gate on delivery; `markArmed` records that the platform accepted a scheduling request and is never consulted to suppress a post. Keeping them in separate UPDATEs is what makes the iOS pre-scheduled-request case explainable.
- **No sentinel for grouped baselines.** `selectTypeBaselines`/`selectVehicleStats` generate non-null epoch columns under `GROUP BY`, so the mapper returns `TypeBaseline`/`VehicleStats` directly; substituting `0L` would read as 1 Jan 1970 and make rules instantly overdue.
- **Copy has three layers by design.** `SentenceSpec`/`IntervalSpec` hold the choice; `dueSentence`/`intervalSentence`/`statusSentence` resolve it for the worker; `dueSentenceText`/`intervalSentenceText`/`statusSentenceText` mirror it for screens. The two resolvers are the only duplicated lines and are mechanically identical.
- **Identifier parsing is exact-shape only.** One `v` prefix, exactly one `:r`, digit-only positive halves, no trailing characters — anything else is `null`, and callers treat `null` as no navigation (T-03-14).
- **Hook payloads are rule-id lists.** Both `onRulesChanged(vehicleId, ruleIds)` and `onVehicleDeleted(id, ruleIds)` hand over the exact ids a mutation invalidated, so plan 07 can cancel without re-reading the database.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Added the `lastInsertId` query to `ReminderRule.sq`**
- **Found during:** Task 1 (persistence layer)
- **Issue:** Plan 01 shipped the reminder write queries but no way to read back the id of a freshly inserted rule. `RETURNING` is unavailable at minSdk 26, so `ReminderRepository.save` could not return the new id as its contract requires.
- **Fix:** Added an additive `lastInsertId: SELECT last_insert_rowid();` query with a comment explaining the minSdk constraint. A query is not part of the schema, so `databases/4.db` and `migrations/3.sqm` are untouched.
- **Files modified:** `shared/src/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq`
- **Verification:** `ReminderRepositoryTest` inserts and receives a positive id on the real driver; `git diff 461398a..HEAD -- shared/src/commonMain/sqldelight` shows only this file (+8 lines); `ServiceEntryMigrationTest` still passes in the full iOS suite.
- **Committed in:** `eb4938d` (Task 1 commit)

**2. [Rule 1 - Bug] Corrected the baseline/vehicle-stat nullability assumption**
- **Found during:** Task 1 (persistence layer)
- **Issue:** The plan assumed `SelectTypeBaselines.baseline_date_epoch_ms` and `SelectVehicleStats.last_service_epoch_ms` were nullable and called for a `toTypeBaselineOrNull()` mapper. SQLDelight 2.4.1 generates both as non-null `Long` under `GROUP BY` (verified against the generated `SelectTypeBaselines.kt`/`SelectVehicleStats.kt`), so a nullable mapper would not compile.
- **Fix:** Implemented the non-null `toDomain()` mappers with KDoc recording the generated signature and explicitly stating that **no `0L` sentinel** is substituted — a sentinel would read as 1 Jan 1970 and make every rule instantly overdue.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/data/ReminderRuleEntityMapper.kt`
- **Verification:** `ReminderRepositoryTest` baseline/vehicle-stat assertions pass on the iOS simulator; Android and iOS compilation succeed.
- **Committed in:** `eb4938d` (Task 1 commit)

**3. [Rule 1 - Bug] Passed the quantity as the plural format argument**
- **Found during:** Task 3 (copy resolver)
- **Issue:** The plan's `SentenceSpec.Plural(resource, quantity)` and `getPluralString(Res.plurals.reminder_months_phrase, months.toInt())` omitted the format argument, so `%1$d` in the plural strings would render literally instead of the number.
- **Fix:** Every plural branch passes the quantity as the format argument: `args = listOf(days)` for the day plurals, and `getPluralString(Res.plurals.reminder_months_phrase, months.toInt(), months.toInt())` for the month phrase.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/util/ReminderCopy.kt`
- **Verification:** `strings.xml` keeps `%1$d` in all four Phase 3 plurals; the Android util suite and iOS `ReminderNotificationIdTest` compile and pass; grep gates for `Res.string|Res.plurals` (18 references) pass.
- **Committed in:** `8c7c6c9` (Task 3 commit)

**4. [Rule 1 - Bug] Ran insert + `last_insert_rowid()` in one transaction in the test seeding**
- **Found during:** Task 1 (repository tests)
- **Issue:** The native driver pools connections, so a raw insert followed by a separate `lastInsertId` transaction could read `0` on a different connection.
- **Fix:** Seeded fixtures with one `transactionWithResult { insert; lastInsertId }` so the id is read on the same connection and transaction as the insert.
- **Files modified:** `shared/src/iosTest/kotlin/com/vehicare/data/ReminderRepositoryTest.kt`
- **Verification:** All 9 repository tests pass repeatedly on `iosSimulatorArm64Test`.
- **Committed in:** `eb4938d` (Task 1 commit)

---

**Total deviations:** 4 auto-fixed (1 blocking, 3 bugs)
**Impact on plan:** All four were required for correctness: without them the insert id is unavailable, the mapper does not compile, plural sentences print `%1$d`, and the driver tests read id 0. No schema version change, no new dependency, no scope creep.

## Issues Encountered

- The plan's nullability assumption for grouped aggregates was wrong for SQLDelight 2.4.1; resolved as deviation 2 rather than weakening the mapper with a nullable return.
- The real-driver repository tests live in `iosTest` (the project's existing convention for SQLDelight native-driver tests), so the Android host suite cannot run them; they ran on `iosSimulatorArm64Test`. The Android suite still compiles the shared implementation and runs the pure-helper tests (132 tests total).
- No authentication gates, package installs, architectural changes or checkpoints were encountered.
- `.planning/STATE.md` and `.planning/ROADMAP.md` were intentionally left untouched — the orchestrator owns those files.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plans 04–06 can consume `ReminderRepository` (rules/baselines/stats), `ReminderCopy` (every sentence) and `RupiahFormat.formatKmValue` without touching SQLDelight types.
- Plan 07 can schedule/cancel through the change hooks and parse taps with `ReminderNotificationId.vehicleIdOf`, treating `null` as no navigation.
- The v4 schema baseline and migration guard are unchanged; no dependency or build-file change was made in this plan.

## Self-Check: PASSED

- All 7 created source/test files exist on disk.
- Commits `eb4938d`, `0c7260d` and `8c7c6c9` exist in `git log`.
- Full iOS suite: 194 tests, 0 failures, 0 errors. Full Android host suite: 132 tests, 0 failures, 0 errors. `compileKotlinIosSimulatorArm64` succeeds.

---
*Phase: 03-reminders-dashboard*
*Completed: 2026-10-24*
