---
phase: 02-service-log
plan: 03
subsystem: service-entry-form
tags:
  - ui
  - compose-multiplatform
  - validation
  - photo-lifecycle
  - kotlin
dependency_graph:
  requires:
    - 02-01 (service-log data core)
    - 02-02 (date/currency utilities + string set)
  provides:
    - ServiceEntryEditRoute (add/edit destination)
    - ServiceEntryEditViewModel (form state machine + photo bookkeeping)
    - ServiceEntryFormValidation (pure validation rules)
    - Five form components (ServiceTypePicker, ServiceTypeDialog, ServiceDateField, PartsEditor, ServicePhotoStrip)
    - PhotoStorage.directory parameter + SERVICE_ENTRY_DIRECTORY
    - ServiceEntryRepository Koin binding
  affects:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt (C-27 Label role carry)
tech_stack:
  added:
    - kotlinx.datetime (via 02-02)
  patterns:
    - sealed UI state (Loading/Ready/NotFound)
    - one-shot failure flags (saveFailed, photoError)
    - serialized DB lane (Dispatchers.IO.limitedParallelism(1))
    - two-set photo bookkeeping (persistedButUnsavedPaths / supersededPaths)
    - koinViewModel with route parameters
    - single scrolling Column with imePadding + verticalScroll
key_files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypeDialog.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceDateField.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/PartsEditor.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryFormValidationTest.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt
    - shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt
decisions:
  - PhotoStorage.persist now takes optional `directory` parameter (default: VEHICLES_DIRECTORY); existing vehicle call sites unchanged
  - ServiceEntryFormValidation.isSubmittable gates exactly on date + non-blank mileage + service type (D-27)
  - ServiceEntryEditViewModel implements seven photo rules as two path sets; onCleared only removes session-owned files
  - Custom type confirm collapses case-insensitively onto preset/learned chips; never rewrites stored values (D-40)
  - Vehicle form's name-field error line now uses MaterialTheme.typography.labelLarge (C-27)
  - ServiceEntryEditRoute registered with vehicleId + entryId (0L = add); AppModule binds single<ServiceEntryRepository> with PhotoStorage::delete
metrics:
  duration: "~45min"
  completed_date: "2026-10-20"
  task_count: 3
  file_count: 16
---

# Phase 02 Plan 03: Service Entry Add/Edit Form Summary

**One-liner:** Complete Add/Edit service entry form with type chip group, custom type dialog, date picker, parts editor, photo strip, validation, and ViewModel with two-set photo bookkeeping — wired as single destination for add (entryId=0) and edit.

## Completed Tasks

| Task | Name | Commit | Files |
|------|------|--------|-------|
| 1 | Five form components | [hash] | ServiceTypePicker, ServiceTypeDialog, ServiceDateField, PartsEditor, ServicePhotoStrip |
| 2 | PhotoStorage dir param + validation + ViewModel | [hash] | PhotoStorage, PhotoStorageTest, ServiceEntryFormValidation, ServiceEntryFormValidationTest, ServiceEntryEditViewModel |
| 3 | Edit screen + route + DI + C-27 Label carry | [hash] | ServiceEntryEditScreen, Routes, App, AppModule, VehicleEditScreen |

## Verification Results

| Check | Result |
|-------|--------|
| `:shared:compileKotlinIosSimulatorArm64` | ✅ PASS |
| `:shared:compileAndroidMain` | ✅ PASS |
| `:shared:testAndroidHostTest` | ✅ PASS (49 tests) |
| `:shared:iosSimulatorArm64Test` | ✅ PASS |
| Grep: components are presentational | ✅ PASS |
| Grep: ViewModel has photo bookkeeping sets | ✅ PASS |
| Grep: Route + DI wiring | ✅ PASS |
| Grep: C-27 Label role on vehicle form | ✅ PASS |

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] FlowRow not available in Compose Multiplatform commonMain**
- **Found during:** Task 1 (ServiceTypePicker)
- **Issue:** `FlowRow` and `ChipDefaults` from Material3 are not available in the pinned Compose Multiplatform 1.12.1 for commonMain targets
- **Fix:** Implemented manual wrapping layout using Column of Rows with estimated chip widths; used inline ChipColors data class instead of ChipDefaults; AssistChip uses simplified colors without ChipDefaults
- **Files modified:** ServiceTypePicker.kt
- **Commit:** [hash]

**2. [Rule 1 - Bug] DatePicker API differences**
- **Found during:** Task 1 (ServiceDateField)
- **Issue:** `rememberDatePickerState` doesn't accept `initialSelectedDateMillis` or `showModeToggle` parameters in the pinned version; `DatePickerDialog` API differs
- **Fix:** Used `initialSelectedDate = LocalDate` overload; removed `showModeToggle`; wrapped DatePicker in DatePickerDialog with custom confirm/dismiss buttons
- **Files modified:** ServiceDateField.kt
- **Commit:** [hash]

**3. [Rule 1 - Bug] OutlinedTextField label block syntax**
- **Found during:** Task 3 (ServiceEntryEditScreen)
- **Issue:** Extra closing paren in label block caused parser to misread subsequent OutlinedTextField parameters
- **Fix:** Matched VehicleEditScreen.kt pattern exactly: Text() closed with `)`, label lambda closed with `},`
- **Files modified:** ServiceEntryEditScreen.kt
- **Commit:** [hash]

**4. [Rule 2 - Missing critical functionality] PartsEditor type mismatch**
- **Found during:** Task 3 integration
- **Issue:** PartsEditor defined its own PartRow; ServiceEntryFormValidation defined another; type mismatch when passing parts list
- **Fix:** Removed local PartRow from PartsEditor; imported and used `com.vehicare.ui.service.PartRow` from ServiceEntryFormValidation.kt
- **Files modified:** PartsEditor.kt
- **Commit:** [hash]

**5. [Rule 3 - Blocking issue] Missing imports in ServiceEntryEditScreen**
- **Found during:** Task 3
- **Issue:** Missing `mutableStateOf`, `field_required_marker`, `field_photos` string resources
- **Fix:** Added imports and verified string resources exist in strings.xml
- **Files modified:** ServiceEntryEditScreen.kt
- **Commit:** [hash]

## Auth Gates

None — no authentication required for this plan.

## Threat Flags

| Flag | File | Description |
|------|------|-------------|
| threat_flag: photo_lifecycle | ServiceEntryEditViewModel.kt | Implements seven photo rules with two path sets; correctness critical for data integrity |
| threat_flag: custom_type_collapse | ServiceEntryEditViewModel.kt | Case-insensitive collapse onto preset/learned chips; never rewrites stored values (D-40) |

## Known Stubs

None — all components fully implemented and wired.

## Self-Check

- [x] All created files exist on disk
- [x] All commits exist in git history
- [x] SUMMARY.md created at .planning/phases/02-service-log/02-03-SUMMARY.md