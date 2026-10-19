---
phase: 01-foundation-vehicle-management
plan: 07
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, material3, vehicle-form, add-edit, state-machine, validation, tdd, segmented-button]

# Dependency graph
requires:
  - phase: 01-02
    provides: theme tokens (Paper/Mist/Ink/Slate/Hairline/Danger), VehiCareTypography, generated Res package with the form copy strings
  - phase: 01-03
    provides: domain Vehicle/VehicleType, VehicleRepository (observeVehicle/save) with the typed-parameter write path (T-01-40)
  - phase: 01-04
    provides: @Serializable routes, App shell (one KoinApplication + one NavHost + four composable entries), koinViewModel() pattern
  - phase: 01-05
    provides: VehicleType.labelRes() single copy source for the three type names (D-11)
  - phase: 01-06
    provides: VehicleEditRoute wiring already passing vehicleId + onDone, koinViewModel(parameters) precedent
provides:
  - VehicleFormValues + VehicleFormValidation — pure, Compose-free form rules (submittability, name error, year sanitizing, form→Vehicle mapping)
  - VehicleEditViewModel(repo, vehicleId) with a Loading/Ready/NotFound matrix, one onFieldChange mutator, a save() double-submit guard and a one-shot saveFailed flag
  - VehicleTypePicker — single-select Car · Motorcycle · Truck segmented control with Car preselected
  - Final five-field VehicleEditScreen (name, make, model, year, plate, type) with inline name validation, a digits-only numeric Year field and a Saving state
  - Koin route-id registration `viewModel { (vehicleId: Long) -> VehicleEditViewModel(get(), vehicleId) }`
affects: [01-08, 01-09, phase-2-service-log, phase-3-reminders]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Form rules as a pure Compose-free object so every rule is testable in commonTest"
    - "One state machine owning validation: onFieldChange re-validates; the screen renders, it never decides"
    - "Edit path accepts only the repository's first emission — a reload can never clobber in-flight user edits (T-01-44)"
    - "Save failure kept as a one-shot Boolean flag the screen consumes, so no exception detail or field value is ever logged (T-01-43)"
    - "Third-party Material control re-coloured from the app palette instead of M3 defaults (C-8)"
    - "Form scrolls and insets for the IME, so the submit control is never hidden under the keyboard"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleFormValidation.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/VehicleFormValidationTest.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypePicker.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt

key-decisions:
  - "Edit accepts only the first repository emission: the cheapest rule that is provably free of silent data loss on the optional/photo fields (T-01-44)"
  - "A read failure on load resolves to NotFound rather than a blank form — the UI-SPEC contract has no fourth state"
  - "save() re-checks isSubmittable, so the state machine owns the name gate and not only the disabled button (D-09, defence in depth)"
  - "Added Ready.saveFailed: Boolean = false plus VehicleTypePicker(enabled: Boolean = true) — required by the plan's own action text, beyond its <interfaces> listing"
  - "VehicleTypePicker uses SingleChoiceSegmentedButtonRow with palette-owned colours: M3's default selected container reads an out-of-palette lavender secondaryContainer"
  - "App.kt needed no edit: plan 01-06 already passes vehicleId + onDone to the edit entry (verified one KoinApplication, one NavHost, four composable<> entries)"

patterns-established:
  - "Pure-rules object + thin ViewModel + declarative screen: the validation rules are covered by commonTest, the ViewModel holds the state matrix, the screen holds no logic"
  - "One-shot UI events carried as a Boolean flag on the state, retired by an onXShown() callback"

requirements-completed: [VEH-01, VEH-03]

# Metrics
duration: 21min
completed: 2026-10-19
---

# Phase 1 Plan 07: Add/Edit Vehicle Form Summary

**Real five-field Add/Edit form (name · make · model · year · plate · type) with pure validated rules, a Loading/Ready/NotFound state machine that cannot double-submit or lose input, and a palette-styled Car · Motorcycle · Truck segmented picker**

## Performance

- **Duration:** 21 min
- **Started:** 2026-10-19T06:08:13Z
- **Completed:** 2026-10-19T06:29:37Z
- **Tasks:** 3
- **Files modified:** 6

## Accomplishments

- Replaced the name-only 01-04 form with the real five-field form: `VehicleFormValues` carries name, make, model, year, plate, `type` and `photoPath`, and `toVehicle` writes blanks back as `null` for exactly the blank field — untouched fields (and the photo path) are preserved (T-01-44).
- `VehicleFormValidation` is pure and Compose-free (`PURE_OK`): it returns the error enum rather than copy, so all rules are unit-tested in `commonTest` — submittability (`isNotBlank` after trim), the name-required error, `sanitizeYear` (digits only, `take(4)`, no range rule) and the form→`Vehicle` mapping.
- `VehicleEditViewModel(repo, vehicleId)` owns the whole matrix: `Loading` → `Ready`/`NotFound` on the edit path (never a blank form for a row that is gone), an add path starting from the model's `Car` default, a single `onFieldChange` mutator that re-validates on every keystroke, a `save(onSaved)` that returns early while `saving` or unsubmittable, and a one-shot `saveFailed` flag the screen turns into `Res.string.error_save_vehicle`.
- The Year field is `KeyboardType.Number` with `sanitizeYear` applied on every keystroke (D-13) and the plate field is free text with no hint or validation (D-12).
- `VehicleTypePicker` renders the fixed three-item Car · Motorcycle · Truck list in locked order with Car preselected, at least 48dp tall, in app-palette colours, with selection carried by icon and `Role.RadioButton` semantics rather than colour alone — and reuses `VehicleType.labelRes()` so the type names have one copy source (D-11).
- All three surfaces compile and run: `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`, `:shared:testAndroidHostTest` all green (`VehicleFormValidationTest` 12/12).

## Task Commits

Each task was committed atomically:

1. **Task 1: Write the failing form-rule tests, then implement the pure validation rules** (TDD) - `d61ae15` (test, RED) → `4327f6d` (feat, GREEN)
2. **Task 2: Author the Add/Edit ViewModel with load-for-edit, saving and failure states** - `356d850` (feat)
3. **Task 3: Author the type picker and the final five-field form, then wire the edit destination** - `2deffce` (feat)
4. **Refactor: split the in-flight save transition into explicit helpers** - `b1d7a7c` (refactor)

**Plan metadata:** (docs commit recorded below)

## Files Created/Modified

- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleFormValidation.kt` (new) - Pure rules: `VehicleFormValues`, `errorFor`, `isSubmittable`, `sanitizeYear`, `toVehicle`
- `shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/VehicleFormValidationTest.kt` (new) - 12 cases covering every declared rule, including the photo/type preservation case for T-01-44
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt` (rewritten) - `Loading`/`Ready`/`NotFound` matrix, `onFieldChange`, `save(onSaved)`, `onSaveErrorShown`
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypePicker.kt` (new) - Segmented single-select type control
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt` (rewritten) - The final five-field form with inline name error, saving indicator, Snackbar and back arrow
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` (modified) - Route-id registration for `VehicleEditViewModel`

## Decisions Made

- **Edit consumes only the first emission.** The repository is a Flow; a later emission (including the write the save itself causes) must not overwrite a field the user is mid-edit on. `initialLoadApplied` latches on the first value and every subsequent emission is ignored — the cheapest rule that provably cannot lose data (T-01-44).
- **Read failure maps to `NotFound`.** The UI-SPEC contract declares three states; adding a fourth `Error` state would be a UI-SPEC change, so an unreadable row surfaces as the same "not available" surface as a deleted one, with no exception detail logged (T-01-43).
- **The name gate lives in the state machine too.** `save()` re-checks `VehicleFormValidation.isSubmittable` before dispatching, so a programmatic call cannot insert a nameless row even if the button's disabled state were bypassed (D-09).
- **`Ready.saveFailed` and `VehicleTypePicker(enabled)` were added beyond the plan's `<interfaces>` block.** The plan's own Task 3 acceptance criteria require a Snackbar on save failure and a picker that refuses input while saving; both need a field/parameter the interface listing omitted. Added with defaults so no existing construction site breaks.
- **`App.kt` was deliberately not touched.** The plan listed it as modified, but 01-06's wiring already passes `vehicleId` and `onDone` to the edit entry; re-editing it would have been a no-op. Verified the file still holds exactly one `KoinApplication`, one `NavHost` and four `composable<...>` entries (`DONE_WIRED_OK`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Save-failure flag added to `Ready` so a failed save is reportable**
- **Found during:** Task 2 (Add/Edit ViewModel)
- **Issue:** The plan's Task 3 acceptance criteria require `Res.string.error_save_vehicle` to surface via Snackbar when a save fails, but the `<interfaces>` block's `Ready(values, error, saving, nameEdited)` has nowhere to carry that event. Without it a failed save is indistinguishable from success — exactly the repudiation risk T-01-45 exists to close.
- **Fix:** Added `saveFailed: Boolean = false` to `Ready` plus an `onSaveErrorShown()` callback that retires the flag after the Snackbar, and `markSaveFailed()` keeping the user's values intact.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt`, `VehicleEditScreen.kt`
- **Verification:** `saveFailed` is set only in the failure branch, never on success; the flag is one-shot. `:shared:testAndroidHostTest` green.
- **Committed in:** `356d850` (Task 2), `2deffce` (Task 3), `b1d7a7c` (refactor)

**2. [Rule 2 - Missing Critical] `VehicleTypePicker(enabled)` so the picker refuses input while a save is in flight**
- **Found during:** Task 3 (type picker and final form)
- **Issue:** The plan's Task 3 acceptance criteria state that while saving, inputs are disabled; the picker's listed signature had no way to be disabled, which would have left type mutable mid-save and let a second distinct value reach the write path (T-01-41's UI-side guard).
- **Fix:** Added `enabled: Boolean = true` and threaded it into each `SegmentedButton`, plus explicit disabled colours from the palette.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypePicker.kt`, `VehicleEditScreen.kt`
- **Verification:** `PICKER_OK`; `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` exit 0.
- **Committed in:** `2deffce` (Task 3)

**3. [Rule 2 - Missing Critical] Form scrolls and insets for the on-screen keyboard**
- **Found during:** Task 3 (final form)
- **Issue:** Android runs edge-to-edge in this app; with five stacked fields plus the picker and the submit row, opening the keyboard covered the submit control, making the form unfinishable on a phone — a correctness failure of the plan's stated truth that "a user can add a vehicle with name, make, model, year, license plate and type from one form".
- **Fix:** Added `verticalScroll(rememberScrollState())` to the form column and `imePadding()` to the screen container.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt`
- **Verification:** `:androidApp:assembleDebug` exits 0; unchanged layout at rest (scroll content shorter than the viewport).
- **Committed in:** `2deffce` (Task 3)

**4. [Rule 3 - Blocking] Interim edit screen body kept Task 2 compiling**
- **Found during:** Task 2 (Add/Edit ViewModel)
- **Issue:** Task 2 replaces `VehicleEditViewModel`'s constructor and state shape, so the 01-04 screen stopped compiling the moment the ViewModel landed — `:androidApp:assembleDebug` would fail between Task 2 and Task 3.
- **Fix:** Task 2 carried a minimal body that renders the new state matrix; Task 3 replaced it with the real five-field form in the same file.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt`
- **Verification:** `:androidApp:assembleDebug` exit 0 after Task 2; the Task 3 commit supersedes the interim body.
- **Committed in:** `356d850` (Task 2)

**5. [Rule 3 - Blocking] Padding control re-coloured from the app palette**
- **Found during:** Task 3 (type picker)
- **Issue:** `SingleChoiceSegmentedButtonRow`'s M3 defaults fill the selected segment with `secondaryContainer`, which in this app's scheme resolves to an out-of-palette lavender — a visible violation of C-8's ink-on-paper language, and a colour-only selection cue with no contrast guarantee.
- **Fix:** Passed an explicit `SegmentedButtonDefaults.colors(...)` built from `Paper`/`Ink`/`Mist`/`Slate`/`Hairline` for both enabled and disabled states.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypePicker.kt`
- **Verification:** `PICKER_OK`; no M3-default container colour remains in the file.
- **Committed in:** `2deffce` (Task 3)

**6. [Rule 1 - Bug] A KDoc literal defeated the plan's own purity gate**
- **Found during:** Task 1 (pure rules)
- **Issue:** The `PURE_OK` gate greps the whole file — comments included — for `Res\.string`. A KDoc line naming `Res.string.error_name_required` to explain the rule made the file report as impure. A gate that a comment can trip is a false signal.
- **Fix:** Reworded the KDoc to describe the message without naming the resource symbol.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleFormValidation.kt`
- **Verification:** `PURE_OK` now prints with a zero count.
- **Committed in:** `4327f6d` (Task 1)

---

**Total deviations:** 6 auto-fixed (1 bug, 5 blocking/missing-critical)
**Impact on plan:** Every fix was required for the plan's own stated truths — a reportable save failure, an input surface that cannot be mutated mid-save, a form the keyboard cannot hide, a compiling build between tasks and a palette-conformant control. No scope creep: no new screen, no new state beyond the one-shot flag, no new dependency.

## Issues Encountered

- **Two plan verify snippets cannot be run literally.** `grep -Ec … | grep -v ':0$' && echo "VIOLATION" || echo "OK"` prints a bare count when the count is zero, so the snippet emits a digit instead of the expected `BOUNDARY_OK` / `BACK_ARROW_OK` marker. Re-run in `grep -rEc <file>` form both are clean: zero `Dispatchers.IO` / `app.cash.sqldelight` / `Icons.Filled.ArrowBack` matches across `ui/`.
- **`grep -c 'OutlinedTextField'` returns 6, not 5.** The sixth match is the import line; the real field count is five, confirmed with `grep -c 'OutlinedTextField('`.
- **The picker's `type_car|type_motorcycle|type_truck` count is 1, not 3.** The picker deliberately does not re-declare the strings — it calls `VehicleType.labelRes()` from 01-05, so the three names have a single copy source (D-11). The one match is the KDoc line listing the three names.

## Known Stubs

- `VehicleFormValues.photoPath` is carried through the form, the validation object and `toVehicle` (so an edit preserves an existing photo path) but no photo UI reads or writes it yet. Intentional: plan **01-08** adds the photo block to this same screen. The field is not user-reachable in this plan, and `toVehicle` writes it back unchanged, so no path can clear a stored photo.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The Add/Edit form is complete and is the **same** screen 01-08 extends with the photo block — no second Add/Edit screen will be authored (`VehicleEditScreen.kt` holds the single form).
- `VehicleEditViewModel`'s edit path already loads and preserves `type` and `photoPath`; 01-08 only needs to add the picker/capture UI and thread the resulting path through `onFieldChange`.
- `VehicleTypePicker` is reusable and palette-conformant if a later surface needs the type control.
- Blocking concern: none. The `STATE.md` phase counter advanced past this plan; the phase's human end-to-end check (add-only-name, add-with-every-field-and-Truck, edit-with-preserved-photo, `20x2` → `202`) remains as the phase's final UAT step.

---

*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-19*

## Self-Check: PASSED
