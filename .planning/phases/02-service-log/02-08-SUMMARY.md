---
phase: 02-service-log
plan: 08
subsystem: ui
tags: [compose-multiplatform, kotlin, multi-select, chip-group, pure-transition, uat-gap-closure]

# Dependency graph
requires:
  - phase: 02-service-log
    plan: 02-07
    provides: "schema v3 `service_entry_type`, ordered `ServiceEntry.serviceTypes`, hydrating reads, transactional writes and cascade deletes"
  - phase: 02-service-log
    plan: 02-03
    provides: "the Add/Edit form, the chip picker, the form validation seam and the custom-type dialog"
  - phase: 02-service-log
    plan: 02-05
    provides: "`serviceMetaLine` and the history/row display conventions this plan's `serviceTypeLine` mirrors"
provides:
  - "`ServiceTypeSelection`: one pure confirm transition and one pure toggle transition, shared by the ViewModel and both test targets"
  - "list-shaped form values (`serviceTypes: List<String>`) with an at-least-one-type submit gate and a case-insensitive dedupe at the storage boundary"
  - "a multi-select `ServiceTypePicker` with a selected-label union so a selected value always has a chip"
  - "`serviceTypeLine` — the single type-line helper for the detail focal block and the history row"
  - "a UI-SPEC and D-26 record that describe the approved multi-select contract"
affects: [02-service-log UAT, phase-3 dashboard, any future surface that lists service types]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Pure-transition rule holder: a Compose-free file of top-level functions plus a small data class, unit-tested on both targets (extends `ServiceEntryFormValidation`)"
    - "One transition returns every list a render depends on, so selection and rendered options cannot drift"
    - "Selected-label union in a presentational picker: a selected value is always renderable, even if the option source is momentarily stale"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeSelectionTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelStateTest.kt
    - shared/src/iosTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelCustomTypeTest.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeLineTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryFormValidationTest.kt
    - .planning/phases/02-service-log/02-UI-SPEC.md

key-decisions:
  - "GAP 1 is fixed by one pure transition, not by a patch at the call site — confirmCustomServiceType returns the next selection and the next learned list together"
  - "The picker unions selected labels into its options as a second, defensive guard; it is explicitly documented as not the primary fix"
  - "A confirmed custom label is prepended to the learned list so its chip renders immediately after the presets"
  - "D-40 case-insensitive collapse is applied at three layers: confirm (preset casing, then stored casing), toggle (remove ignoring case), and toServiceEntry (distinctBy lowercase)"
  - "The ViewModel-level GAP 1 proof lives in iosTest because viewModelScope needs a Dispatchers.setMain dispatcher; the pure transition is proven on both targets in commonTest"
  - "The D-26 supersede note in 02-CONTEXT.md was already applied at plan time; this plan verified it instead of duplicating it"

patterns-established:
  - "Render-state assertions: a test for a selection bug must assert the label is in the rendered option set, not only in the selection field"
  - "One author, one helper: serviceTypeLine is defined beside serviceMetaLine and imported by the history row, never re-implemented"

requirements-completed: [SVC-01, SVC-05, SVC-06]

# Metrics
duration: 30min
completed: 2026-10-21
---

# Phase 02 Plan 08: Multi-select service types and the joined type line Summary

**Multi-select service-type chips driven by one pure confirm transition that writes both the selection and the rendered chip list, plus a single `serviceTypeLine` helper joining several types with ` · ` on the detail and history surfaces**

## Performance

- **Duration:** ~30 min
- **Started:** 2026-10-21T09:52:00Z (approximate — the session inherited a partially applied Task 1 edit)
- **Completed:** 2026-10-21T10:18:00Z
- **Tasks:** 2 / 2
- **Files modified:** 13 (5 created, 8 modified)

## Accomplishments

- **UAT gap 1 is structurally closed.** `onCustomTypeConfirmed` no longer has three branches writing a single field. It delegates to `confirmCustomServiceType`, which returns the next selected list **and** the next learned-chip list, and `withConfirmedCustomType` writes both in one `copy`. The picker's options (`presets + learned`) therefore always contain the just-confirmed label.
- **UAT gap 2's UI half ships.** The chip group is multi-select: tapping toggles, any number may be selected, deselecting the last one leaves Save disabled, and no type is preselected on add. Order is tap order and is the stored display order.
- **The form is list-shaped.** `ServiceEntryFormValues.serviceTypes: List<String>`, the gate requires at least one, and `toServiceEntry` dedupes case-insensitively as the last boundary before storage.
- **Multi-type entries are legible.** `serviceTypeLine` is defined exactly once (beside `serviceMetaLine`) and imported by `ServiceHistoryRow`; one type renders byte-identically to before, several join with ` · `, the detail title caps at 2 lines and the history row at 1, both ellipsised.
- **Contract and implementation agree.** The UI-SPEC's chip rule, dialog sentence, submission gate, row anatomy, focal block, Idle state and interaction rule all describe multi-select; the D-26 record keeps its original "Exactly one type is selected" sentence beside the 2026-10-21 supersede note.
- **The regression is proven at the call site the UAT exercised.** The new iosTest constructs the real `ServiceEntryEditViewModel` on a `Dispatchers.setMain(StandardTestDispatcher())` harness and asserts the confirmed label lands in both `values.serviceTypes` and `learnedTypes`.

## Task Commits

Each task was committed atomically:

1. **Task 1: The pure selection transitions, list-shaped form values, the multi-select picker, and the GAP 1 fix** — `df5b28d` (feat)
2. **Task 2: The joined type line on detail and history, plus the UI-SPEC and D-26 multi-select corrections** — `5220019` (feat)

**Plan metadata:** see the `docs(02-08)` commit that carries this file, STATE.md and ROADMAP.md.

## Files Created/Modified

**Created**

- `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt` — `ServiceTypeSelectionState`, `confirmCustomServiceType`, `toggleServiceType`, and the two `Ready` extensions (`withConfirmedCustomType`, `withToggledType`). No Compose, no repository, no dispatcher.
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeSelectionTest.kt` — 10 cases over the pure transitions, including the exact gap-1 invariant.
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelStateTest.kt` — 5 cases applying the transitions to a `Ready` state; asserts the confirmed label is in both lists and therefore in `presets + learned`.
- `shared/src/iosTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelCustomTypeTest.kt` — 5 cases driving the real ViewModel through `onCustomTypeConfirmed` and `onTypeToggled` on a `setMain` test dispatcher, with an inert repository fake.
- `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeLineTest.kt` — 6 cases: verbatim single type, the locked separator, order, exactness, the U+00B7 code point, and the defensive empty list.

**Modified**

- `ServiceEntryFormValidation.kt` — `serviceTypes: List<String>` replaces the nullable single type; `isSubmittable` requires a non-empty list; `toServiceEntry` dedupes with `distinctBy { it.lowercase() }`.
- `ServiceEntryEditViewModel.kt` — the three-branch `onCustomTypeConfirmed` became a one-line delegation; new `onTypeToggled`; `toFormValues` maps the whole list instead of `firstOrNull()`; the now-unused `ServiceTypePresets` import is gone.
- `ServiceEntryEditScreen.kt` — `ServiceEntryEditForm` takes `onTypeToggled` (wired to `viewModel::onTypeToggled`); the chip group passes `selected = values.serviceTypes` and `onToggle`.
- `components/ServiceTypePicker.kt` — list-shaped `selected`, `onToggle`, per-chip case-insensitive `isSelected`, and a `presets + learned + selected` union.
- `ServiceEntryDetailScreen.kt` — `serviceTypeLine` beside `serviceMetaLine`; the focal block uses it at `headlineSmall` with `maxLines = 2` and ellipsis; the compile bridge is gone.
- `components/ServiceHistoryRow.kt` — imports `serviceTypeLine`, keeps `maxLines = 1` with ellipsis; the compile bridge is gone.
- `ServiceEntryFormValidationTest.kt` — every construction moved to `serviceTypes = listOf(...)`, the missing-type case became `emptyList()`, and a new case proves `["Battery","battery","Oil change"]` collapses to `["Battery","Oil change"]`.
- `.planning/phases/02-service-log/02-UI-SPEC.md` — the seven rules listed in the plan rewritten for multi-select and the joined line; the summary row's `N entries · Last service <date>` rule untouched.

## Decisions Made

- **The gap-1 fix is a transition, not a patch.** Returning both lists from one function is what makes the two impossible to drift; the picker union is documented as defensive only, so nobody later mistakes it for the fix.
- **Prepend, not append, the new learned label.** The picker renders `presets + learned`, so prepending puts the freshly confirmed chip immediately after the presets where the user is looking.
- **The ViewModel proof lives in `iosTest`.** `viewModelScope` needs a `Dispatchers.setMain` dispatcher; the repo already establishes that harness in `ServiceEntryDeletionTest`, and the pure transition is covered on both platforms by `commonTest`.
- **`D-26` was verified, not re-edited.** The 2026-10-21 supersede note was already present from plan time; the plan's grep gate (both the supersede sentence and the preserved original) passes.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Renamed the iosTest repository fake to avoid a redeclaration error**
- **Found during:** Task 1, first run of `:shared:iosSimulatorArm64Test`
- **Issue:** `ServiceHistoryViewModelTest.kt` already declares a `private class FakeServiceEntryRepository` in the same `com.vehicare.ui.service` package. Kotlin rejects two top-level classes with the same package-qualified name, so the iOS test compile failed with `Redeclaration`.
- **Fix:** Renamed the new fake to `InertServiceEntryRepository` and updated its single construction site.
- **Files modified:** `shared/src/iosTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelCustomTypeTest.kt`
- **Verification:** `:shared:iosSimulatorArm64Test --tests 'com.vehicare.ui.service.ServiceEntryEditViewModelCustomTypeTest'` exits 0 (5 tests).
- **Committed in:** `df5b28d`

**2. [Rule 3 - Blocking] Reworded a KDoc line that tripped the plan's own single-select sweep**
- **Found during:** Task 1, running the `! grep -rEn 'values\.serviceType([^s]|$)'` gate
- **Issue:** The new `ServiceTypeSelection.kt` KDoc described the old bug with the literal token `` `values.serviceType` ``, which the gate's regex matches (the character after the token is a backtick, not `s`). A correct implementation would have failed its own gate.
- **Fix:** Reworded to "wrote only the selection field (one string)" — no code change, same meaning.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt`
- **Verification:** Gate prints `NO_SINGLE_SELECT_LEFTOVER`.
- **Committed in:** `df5b28d`

**3. [Rule 1 - Bug] Added the missing trailing newline to `ServiceEntryDetailScreen.kt`**
- **Found during:** Task 2, reviewing the diff before commit
- **Issue:** The file ended without a final newline (pre-existing). The new `serviceTypeLine` became the file's last line, so the defect would have spread to new code.
- **Fix:** Appended a newline.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt`
- **Verification:** `od -c` on the tail shows the closing `\n`; all gates and builds re-run green.
- **Committed in:** `5220019`

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug)
**Impact on plan:** All three were mechanical; no scope change, no interface change, and every plan-specified gate still passes. No Rule 4 (architectural) decisions were needed.

## Issues Encountered

- **The working tree arrived with Task 1 partially applied.** `ServiceTypeSelection.kt` existed untracked and `ServiceEntryFormValidation.kt` had the list-shaped values and gate but still mapped `values.serviceType ?: ""`, so the tree did not compile. Both were consistent with the plan's specification, so they were completed rather than reverted; the final code matches the plan line for line.
- **A self-inflicted malformed KDoc block** (an `@param` list interrupted by a paragraph) was introduced while annotating `ServiceEntryEditUiState.Ready` and caught before any build; fixed in place with no commit noise.
- **`App.kt` carries an unrelated comment typo** ("rende r") that is not part of this plan and was neither committed nor reverted. Recorded in `deferred-items.md`.

## Known Stubs

None. Both 02-07 compile bridges (`serviceTypes.firstOrNull().orEmpty()`) are deleted and the plan's `COMPILE_BRIDGES_REMOVED` gate passes. The one intentional empty case — `serviceTypeLine(emptyList())` returning `""` — is a defensive guard, not a stub: the domain requires at least one type on a saved entry and the submit gate enforces it.

## Threat Flags

None. The plan's threat register (T-02-08-01…06, SC) covers every surface this plan touched, and each mitigation is present: the single transition writes both lists (`-01`), three layers collapse case duplicates (`-02`), the non-empty gate blocks a typeless save (`-03`), `serviceTypeLine` has one definition and one separator literal (`-05`), and the UI-SPEC plus D-26 record no longer authorize single-select (`-06`).

## Verification

All commands run from the repo root; each exit status was captured before any output was tailed.

| Gate | Command | Exit |
|------|---------|------|
| iOS compile | `./gradlew :shared:compileKotlinIosSimulatorArm64 -q` | 0 |
| Android app | `./gradlew :androidApp:assembleDebug -q` | 0 |
| Android host tests | `./gradlew :shared:testAndroidHostTest -q` | 0 |
| iOS targeted (Task 1) | `./gradlew :shared:iosSimulatorArm64Test --tests ServiceTypeSelectionTest --tests ServiceEntryEditViewModelStateTest --tests ServiceEntryEditViewModelCustomTypeTest -q` | 0 |
| iOS targeted (Task 2) | `./gradlew :shared:iosSimulatorArm64Test --tests ServiceTypeLineTest -q` | 0 |
| iOS full suite | `./gradlew :shared:iosSimulatorArm64Test -q` | 0 |
| Migration guard | `./gradlew :shared:verifyCommonMainVehiCareDatabaseMigration -q` | 0 |

Test totals from the JUnit XML: **131 iOS-simulator tests, 80 Android-host tests, 0 failures, 0 errors, 0 skipped.** New/changed classes: `ServiceTypeSelectionTest` 10, `ServiceEntryEditViewModelStateTest` 5, `ServiceEntryEditViewModelCustomTypeTest` 5, `ServiceTypeLineTest` 6, `ServiceEntryFormValidationTest` 11.

Every grep gate in both tasks passed — including `VM_LEVEL_GAP1_TEST_PRESENT`, `PICKER_MULTI_SELECT_OK`, `NO_SINGLE_SELECT_LEFTOVER`, `ONE_HELPER_ONE_SEPARATOR`, `DISPLAY_RULE_WIRED`, `COMPILE_BRIDGES_REMOVED`, `UI_SPEC_UPDATED`, `SUMMARY_ROW_UNCHANGED` and `D26_SUPERSEDE_RECORDED`.

## User Setup Required

None — no external service configuration, no new dependency, no permission change.

## Next Phase Readiness

- Both UAT gaps now have implementation plus automated proof; the remaining gate is the **human** one: re-run UAT test 1 on a device (three preset chips stay checked; a custom type appears selected immediately; deselect-all leaves Save disabled; the detail title and the history row show the joined line with the agreed line caps).
- Phase 2's five Gradle gates are green, so nothing in the build blocks the UAT re-run.
- `02-UAT.md` still records the failed gap; it should be updated only after the human re-run, not before.
- Carried debt unchanged: `01-VERIFICATION.md` remains `human_needed` (acknowledged), and `02-SECURITY.md` does not exist yet (`/gsd-secure-phase 2` optional).

---
*Phase: 02-service-log*
*Completed: 2026-10-21*
