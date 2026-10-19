---
phase: 02-service-log
plan: 02
subsystem: shared-utilities
tags: [kotlinx-datetime, kotlin-multiplatform, date-conversion, rupiah-formatting, compose-resources, i18n]
requires:
  - phase: 02-service-log
    plan: 01
    provides: phase context and the dependency-free domain/util surface this plan's seam plugs into
  - phase: 01-foundation-vehicle-management
    provides: version catalog discipline, pure-object utility pattern (VehicleFormValidation), centralized string-resource set (D-11), numeric display conventions
provides:
  - kotlinx-datetime 0.8.0 pinned once in gradle/libs.versions.toml and consumed through the kotlinx.datetime catalog alias
  - ServiceDates — the single date seam (today/toStorage/fromStorage/display) with the system-zone-vs-UTC asymmetry documented
  - RupiahFormat — one dot-grouping helper behind formatRupiah, formatMileage and parseRupiahDigits (12-digit cap)
  - the complete Phase 2 string set in composeResources/values/strings.xml (47 strings + service_summary_entries plural)
affects:
  - 02-service-log plans 02-03…02-06 (form, detail/delete, history, summary row all format through this seam)
  - Phase 3 reminders (reuse ServiceDates for due-date arithmetic)
tech-stack:
  added:
    - org.jetbrains.kotlinx:kotlinx-datetime 0.8.0 (plain artifact, not the -0.6.x-compat variant)
  patterns:
    - single-seam stateless object for a cross-cutting conversion (mirrors PhotoStorage)
    - one private grouping helper behind both numeric formats so they cannot drift
    - digits-only parsing capped before toLongOrNull so no input can overflow Long
    - centralized Phase 2 string contract appended in one change; no inline literals in later UI plans
key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/util/ServiceDates.kt
    - shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt
    - shared/src/commonTest/kotlin/com/vehicare/util/ServiceDatesTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/util/RupiahFormatTest.kt
  modified:
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
    - shared/src/commonMain/composeResources/values/strings.xml
key-decisions:
  - "today() reads the device's system zone while toStorage/fromStorage pin UTC start-of-day; the asymmetry is stated in the object KDoc and covered by fixed-date tests (RESEARCH Pitfall 1)."
  - "Both display formats route through one private groupDigits helper and no locale API is imported, so Android and iOS cannot render different separators (D-29, T-02-02-03)."
  - "parseRupiahDigits mirrors sanitizeYear's it.isDigit() filter and caps at 12 digits before parsing, keeping every accepted cost inside Long range."
  - "The Phase 2 string block is appended exactly as UI-SPEC authored it; detail_service_history_coming_soon stays until ComingSoonSlot.kt is deleted in plan 02-05."
patterns-established:
  - "Date conversion seam: every civil-date ⇄ epoch-millis conversion goes through ServiceDates; storage is always UTC start-of-day."
  - "Numeric display seam: every Rupiah/mileage render goes through RupiahFormat with dot grouping and whole-rupiah values."
requirements-completed: [SVC-01, SVC-05]
duration: 15min
completed: 2026-10-19
---

# Phase 2 Plan 2: Service Log Shared Utilities Summary

**kotlinx-datetime 0.8.0 wired with a single UTC-pinned date seam and locale-free Rupiah/km formatters, plus the complete 48-key Phase 2 string contract in the centralized resource set.**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-10-19T14:04Z (session start)
- **Completed:** 2026-10-19T14:19Z
- **Tasks:** 2
- **Files modified:** 7 (4 created + 3 modified) plus this SUMMARY.md

## Accomplishments

- Pinned `kotlinx-datetime = "0.8.0"` (plain artifact) once in the version catalog and added `implementation(libs.kotlinx.datetime)` to `commonMain`; the dependency resolves on both the Android host and the Kotlin/Native simulator target.
- Implemented `ServiceDates` as the phase's single date-conversion seam: `today()` follows the device wall clock, while `toStorage`/`fromStorage` pin UTC start-of-day, and `display` produces the locked `19 Oct 2026` format via `day` / `month.number` and `MonthNames.ENGLISH_ABBREVIATED`.
- Implemented `RupiahFormat` with one private grouping helper behind `formatRupiah` (`Rp 450.000`), `formatMileage` (`123.456 km`) and `parseRupiahDigits` (digits-only, 12-digit cap, blank → null); no locale API appears anywhere.
- Appended all 47 Phase 2 strings plus the `service_summary_entries` plural to `strings.xml`, byte-for-byte matching UI-SPEC §Copywriting Contract (verified key-by-key and value-by-value with a script); the coming-soon key remains for plan 02-05's atomic retirement.
- Unit-tested both utilities in `commonTest`: five round-trip dates including a leap day, the exact display strings, the UTC epoch anchor, both number formats, parser digit filtering, blank handling and the cap.

## Task Commits

Each task was committed atomically:

1. **Task 1: Wire kotlinx-datetime and implement ServiceDates + RupiahFormat with unit tests** — `1de60f7` (feat)
2. **Task 2: Append the complete Phase 2 string set to strings.xml** — `c08933b` (feat)

**Plan metadata:** the docs commit carrying this SUMMARY and the state updates.

## Files Created/Modified

- `gradle/libs.versions.toml` — `kotlinx-datetime = "0.8.0"` version + `kotlinx-datetime` library alias (plain, no compat variant)
- `shared/build.gradle.kts` — `implementation(libs.kotlinx.datetime)` in `commonMain`, in catalog-alphabetical position
- `shared/src/commonMain/kotlin/com/vehicare/util/ServiceDates.kt` — the single date seam; system-zone `today()`, UTC storage, one display format
- `shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt` — shared grouping helper + Rupiah/mileage formatters + capped digits-only parser
- `shared/src/commonTest/kotlin/com/vehicare/util/ServiceDatesTest.kt` — round trips, UTC anchor, locked display format
- `shared/src/commonTest/kotlin/com/vehicare/util/RupiahFormatTest.kt` — both formats, digit filtering, blank/no-cost, 12-digit cap
- `shared/src/commonMain/composeResources/values/strings.xml` — full Phase 2 titles/actions/fields/values/states/errors/dialogs/accessibility set + plural

## Decisions Made

- `today()` = system zone, storage/display = UTC start-of-day. Documented in the object KDoc as an intentional asymmetry and pinned by fixed-date tests; no clock mock is used because it would only prove the mock.
- One private `groupDigits` helper serves both `formatRupiah` and `formatMileage`; a leading minus is preserved in front of the grouped digits, though costs and mileages are non-negative by construction.
- `parseRupiahDigits` filters `it.isDigit()`, takes the first 12 digits, then parses — the same filter style as `sanitizeYear` with a cap that rules out `Long` overflow.
- `detail_service_history` is added now, while `detail_service_history_coming_soon` is deliberately left in place: retiring it must ride the same commit that deletes `ComingSoonSlot.kt` (plan 02-05), or the build would break for a plan that has not run.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] The plan's `:shared:assembleDebug` verification task does not exist**
- **Found during:** Task 2 (string-set verification)
- **Issue:** `:shared` is a KMP library module; `./gradlew :shared:tasks --all` lists `assembleAndroidMain`, `assembleAndroidHostTest`, `compileAndroidMain` and the resource tasks, but no `assembleDebug`. The plan's Task 2 verify command and acceptance criterion name that non-existent task.
- **Fix:** Verified the same intent (resource set parses, generated `Res` accessors build) with `./gradlew :shared:generateResourceAccessorsForCommonMain --rerun-tasks` (exit 0; generated accessors confirmed to contain `Res.string.title_service_history`, `Res.string.warn_mileage_lower`, `Res.string.cd_remove_part` and `Res.plurals.service_summary_entries`), plus `./gradlew :shared:compileAndroidMain` (exit 0) and the whole-app `./gradlew :androidApp:assembleDebug` (exit 0). No production code changed.
- **Files modified:** None (verification commands only)
- **Verification:** All three replacement commands exited 0; the plan's string greps, deferred-retirement grep and vocabulary grep all pass.
- **Committed in:** `c08933b` (Task 2 commit; content unchanged by this deviation)

---

**Total deviations:** 1 auto-fixed (1 blocking)
**Impact on plan:** Verification-only substitution caused by a stale task path already documented in STATE.md; every acceptance criterion is proven by an executed command, with no scope or code change.

## Issues Encountered

- The first substitute attempt, `./gradlew :shared:assembleAndroidMain`, failed after ~5 minutes because Gradle tried to download Android lint 32.1.1 artifacts from `dl.google.com` and the connection timed out — unrelated to this plan's changes. The resource-generation, `compileAndroidMain` and `:androidApp:assembleDebug` tasks do not need those artifacts and all succeeded. No source change was needed.

## Known Stubs

None. `ServiceDates` and `RupiahFormat` are complete implementations, and every appended string is consumed by later plans in this phase; no placeholder data or TODO remains.

## Threat Surface Scan

No security-relevant surface beyond the plan's `<threat_model>`: the only new artifact is the audited `org.jetbrains.kotlinx:kotlinx-datetime` 0.8.0 pinned plain in the catalog, there is no network/auth/file-access path, and the string set is static. Date-zone mixing (T-02-02-02) and formatter drift (T-02-02-03) are both mitigated by the single seams and their tests.

## Next Phase Readiness

- Plans 02-03…02-06 can reference every Phase 2 string as `Res.string.*` / `Res.plurals.service_summary_entries` from `vehicare.shared.generated.resources`; no later plan needs to add a string.
- `ServiceDates.display`/`toStorage` and `RupiahFormat.formatRupiah`/`formatMileage`/`parseRupiahDigits` are ready for the form, history rows, summary row and detail screen.
- The deferred retirement of `detail_service_history_coming_soon` is explicitly owned by plan 02-05 alongside `ComingSoonSlot.kt` deletion.
- No blockers.

## Self-Check: PASSED

- FOUND: gradle/libs.versions.toml
- FOUND: shared/build.gradle.kts
- FOUND: shared/src/commonMain/kotlin/com/vehicare/util/ServiceDates.kt
- FOUND: shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt
- FOUND: shared/src/commonTest/kotlin/com/vehicare/util/ServiceDatesTest.kt
- FOUND: shared/src/commonTest/kotlin/com/vehicare/util/RupiahFormatTest.kt
- FOUND: shared/src/commonMain/composeResources/values/strings.xml
- FOUND commit: 1de60f7
- FOUND commit: c08933b

---
*Phase: 02-service-log*
*Completed: 2026-10-19*
