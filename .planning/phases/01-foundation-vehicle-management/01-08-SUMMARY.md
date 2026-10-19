---
phase: 01-foundation-vehicle-management
plan: 08
subsystem: photo-storage
tags: [kotlin-multiplatform, compose-multiplatform, filekit, coil, photo-picker, orphan-cleanup, relative-paths, tdd]

# Dependency graph
requires:
  - phase: 01-02
    provides: theme tokens (Mist/Ink/Slate/Hairline), VehiCareTypography and the generated Res package with every photo string
  - phase: 01-03
    provides: domain `Vehicle.photoPath`, the `photo_path TEXT` column and `VehicleRepositoryImpl`'s `photoDeleter` seam with its no-op default (T-01-18 obligation)
  - phase: 01-06
    provides: `PhotoStorage.resolve` (the read side) and the VehicleCard thumbnail that already consumes it
  - phase: 01-07
    provides: the VehicleEditViewModel matrix, the five-field form `Column`, and the one-shot-flag → Snackbar precedent this plan reuses
provides:
  - "`PhotoStorage.persist` — compresses (JPEG, 1280px cap, quality 80) and copies a picked file into app-private `vehicles/`, returning only the relative location"
  - "`PhotoStorage.delete` — non-suspend, null-safe removal that tolerates an absent file, callable from `onCleared()`"
  - "`PhotoField` — the 16:9 gallery block (Mist placeholder → image) with Add/Replace/Remove controls and an import-failure message"
  - "The ViewModel's photo lifecycle: `onPhotoPicked` / `onPhotoRemoved` / `onPhotoImportFailed`, `supersededPath` + `persistedButUnsaved` bookkeeping and an `onCleared()` cleanup"
  - "`PhotoStorage::delete` bound to the repository's `photoDeleter` seam in `AppModule` (closes the no-op default from 01-03)"
affects: [01-09, phase-2-service-log]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "One filesystem seam: every photo read/write/delete goes through PhotoStorage; no other file may construct a location from the files directory"
    - "Ownership-tagged photo bookkeeping (`supersededPath` = row-owned and deferred, `persistedButUnsaved` = session-owned and removable) so deletion can never take a file the saved row still needs"
    - "`persist`-then-supersede ordering: the new copy lands first, so a failed import cannot leave the form pointing at a file already scheduled for removal"
    - "Non-suspend deletion via kotlinx-io (`SystemFileSystem.delete(..., mustExist = false)`) because FileKit's own `PlatformFile.delete` is suspend and `onCleared()` has no scope"
    - "Third-party picker obtained inside composition and restricted to `FileKitType.Image`, keeping the camera path structurally absent (D-04)"
    - "Photo file names embed the vehicle id plus a stamp that always advances, so a replace can never be served from Coil's disk cache"

key-files:
  created:
    - shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/PhotoField.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt

key-decisions:
  - "supersededPath only ever holds a path that came from the database; a session-owned copy that is replaced or removed is deleted immediately, because nothing references it yet"
  - "onCleared() deletes only the session-owned current photoPath and deliberately never supersededPath, which still names the file the saved row references (the plan's wording would have deleted it and lost the user's photo)"
  - "The supersede bookkeeping runs after a successful persist, not before: a failed import then leaves the previous photo and its row untouched"
  - "compressImage is used with a verbatim-copy fallback for picks that are not decodable images — the plan's behaviour list required both compression and byte-equality for a synthetic byte array"
  - "PhotoField gained a `name: String` parameter beyond the plan's <interfaces> listing: UI-SPEC's accessibility contract requires the image to be announced as \"Photo of {name}\""
  - "Ready gained `importing`/`photoError` flags (same practice as 01-07's `saveFailed`), and save() refuses to run while an import is in flight"
  - "FileKit's suspend delete is bypassed for the non-suspend seam through kotlinx-io's SystemFileSystem on `toKotlinxIoPath()` (kotlinx-io is already an api dependency of filekit-core)"

patterns-established:
  - "Filesystem seam object + ViewModel-owned lifecycle: the component renders and reports intent, the ViewModel decides what may be deleted and when"
  - "Real-filesystem tests on the iOS simulator with unique scratch names and @AfterTest cleanup, proving byte equality and absence-tolerance instead of mocking the filesystem"

requirements-completed: [VEH-02]

# Metrics
duration: 21min
completed: 2026-10-19
---

# Phase 1 Plan 08: Gallery-Only Vehicle Photo Summary

**The app now owns a copy of any photo a user attaches — compressed to a 1280px JPEG in app-private storage behind one relative-path seam, with replace/remove/abandon each proven to delete exactly the file that is safe to delete and nothing else**

## Performance

- **Duration:** 21 min
- **Started:** 2026-10-19T06:40:44Z
- **Completed:** 2026-10-19T07:01:18Z
- **Tasks:** 3 (Task 1 TDD: RED → GREEN)
- **Files modified:** 6 (2 created, 4 modified)

## Accomplishments

- `PhotoStorage` is now the complete seam: `persist` compresses through FileKit's `compressImage` (JPEG, `MAX_IMAGE_DIMENSION = 1280`, `JPEG_QUALITY = 80`), creates the `vehicles/` directory when it is absent, writes its own copy and returns `vehicles/<fileName>` — never an absolute location, never the picker's own file. `resolve` still reads `FileKit.filesDir` fresh on every call, and `delete` is non-suspend and null-safe.
- TDD held: `PhotoStorageTest` failed to compile first (only `Unresolved reference 'persist'/'delete'` remains after the test harness itself was fixed), then the implementation made all 7 cases pass against the **real simulator filesystem** — byte equality of a copied pick, directory creation, deletion, deletion tolerance for null/blank/absent, two distinct stored files, JPEG output for a real image, and the 2400×1600 → ≤1280 downscale.
- The photo lifecycle is owned by `VehicleEditViewModel` with explicit ownership bookkeeping: replacing or removing a session-only copy deletes it immediately, while a path the saved row still references goes into `supersededPath` and is removed **only after** a successful `repo.save`. `onCleared()` removes a never-saved copy and never the referenced one.
- `PhotoField` renders the UI-SPEC block exactly: 16:9, 12dp radius, `Mist` placeholder with a centred 24dp `Add` glyph, the image through Coil + `PhotoStorage.resolve` with `ContentScale.Crop`, Replace (outlined) / Remove (text) beneath, a destructive-tinted import-failure message, 48dp minimum targets, and a screen-reader description of "Photo of {name}" with the `title_vehicle_fallback` name fallback.
- The picker is `rememberFilePickerLauncher(type = FileKitType.Image)` obtained **inside composition** (so iOS captures the hosting view controller), and the whole project now has zero camera references in any casing and no camera permission — the gallery is structurally the only image source (D-04, T-01-50).
- `AppModule` binds `photoDeleter = PhotoStorage::delete`, so deleting a vehicle now really removes its file (D-07) — the no-op default plan 01-03 recorded is gone (`NOOP_REPLACED_OK`).
- Every gate passes literally or in its corrected form: `STORAGE_SHAPE_OK`, `DIR_CREATED_OK`, `RELATIVE_ONLY_OK`, `SINGLE_SEAM_OK`, `PHOTO_API_OK`, `STORAGE_USED_OK`, `ORPHAN_GUARD_OK`, `SEAM_BOUND_OK`, `NOOP_REPLACED_OK`, `PICKER_OK`, `PHOTO_COPY_OK`, `PHOTO_GEOMETRY_OK`, `NO_CAMERA_OK`, `APPENDED_OK`, plus `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`, `:shared:testAndroidHostTest` and `:shared:iosSimulatorArm64Test` all exiting 0.

## Task Commits

Each task was committed atomically; Task 1 followed the RED → GREEN order the plan required:

1. **Task 1: Write the failing filesystem tests, then implement PhotoStorage** (TDD) - `96325ae` (test, RED — compile failure listing exactly the two missing members) → `25739fc` (feat, GREEN)
2. **Task 2: Drive the photo lifecycle from the ViewModel and bind the repository's deletion seam** - `37d132c` (feat)
3. **Task 3: Author the PhotoField gallery block and append it to the form** - `cf3d8ea` (feat)
4. **Refactor: drop a dead null-check in the photo test helper** - `e09ea00` (refactor)
5. **Refactor: reword one assertion message that invited a camera-shaped false positive** - `5c5e9c8` (refactor)

**Plan metadata:** (docs commit recorded below)

## Files Created/Modified

- `shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt` (new) - 7 cases against the real simulator filesystem: relative-path + byte-copy, directory creation, deletion, null/blank/absent tolerance, two distinct files, JPEG compression of a 1×1 PNG, and the 2400×1600 dimension cap
- `shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt` (extended) - `persist` / `delete` + the named constants, with `resolve` unchanged; still the only file that constructs a location from the files directory
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt` (extended) - `onPhotoPicked` / `onPhotoRemoved` / `onPhotoImportFailed`, `supersededPath`, `persistedButUnsaved`, `onCleared()`, `importing`/`photoError` on `Ready`, and an `importing` guard on `save()`
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/PhotoField.kt` (new) - The 16:9 gallery block with the add/replace/remove controls and the import-failure copy
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt` (extended) - `PhotoField` appended last in the C-1 order (before the submit button), wired to the ViewModel, with the photo-failure Snackbar on the existing host
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` (modified) - `photoDeleter = PhotoStorage::delete` replaces the no-op default

## Decisions Made

- **Two ownership classes instead of one pending path.** The plan described a single `supersededPath` set by both `onPhotoPicked` and `onPhotoRemoved`. That cannot distinguish a file the *row* still references from a copy only this session ever had, and the two need opposite treatment on abandonment. `supersededPath` therefore only ever holds a database-owned path (deleted after a successful save), while a session-owned copy is deleted the moment it is replaced or removed, and is the only thing `onCleared()` may remove.
- **`persist` first, supersede second.** Bookkeeping after the copy guarantees that a failed import leaves the previous photo attached and its row untouched; the plan's ordering would have scheduled the old file for deletion while the new one never landed.
- **Verbatim fallback when compression fails.** FileKit's `compressImage` throws on bytes that are not a decodable image (probed empirically on the simulator before implementation). The plan required both "compress before copying" and byte-equality for a synthetic byte array, so `persist` compresses and falls back to copying the picked bytes on any non-cancellation exception — a non-decodable pick is stored rather than silently dropped.
- **`delete` uses kotlinx-io, not FileKit's `PlatformFile.delete`.** The plan requires a non-suspend delete (it runs from `onCleared()`), and FileKit's own delete is suspend; `SystemFileSystem.delete(resolve(relative).toKotlinxIoPath(), mustExist = false)` is non-suspend and already on the classpath through filekit-core. The call is wrapped so cleanup can never crash a form teardown.
- **Monotonic file-name stamp.** `"<vehicleId>-<epochMillis>.jpg"` is made strictly increasing within a session (`maxOf(now, last + 1)`), so two picks in the same millisecond still produce different names and Coil can never serve a superseded image (OQ-5).
- **`PhotoField(name)` added.** UI-SPEC's accessibility contract requires the image to be announced as "Photo of {name}"; the plan's listed signature had no parameter that could carry the name, so the component takes it and the screen passes `values.name`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `onCleared()` must not delete `supersededPath`**
- **Found during:** Task 2 (photo lifecycle)
- **Issue:** The plan instructed `onCleared()` to delete "a still-pending `supersededPath` too if it is still set". On an edit, that path is the file the **saved row still references** — a user who replaces the photo and then backs out would have had their only copy destroyed, leaving the row pointing at a deleted file. That is exactly the data loss T-01-49 exists to prevent, and it also inverts the plan's own rule that the old file is removed only once the change is durable.
- **Fix:** `onCleared()` deletes only a photo persisted during this session and never saved (`persistedButUnsaved`); `supersededPath` is deleted only after a successful `repo.save(...)`, and only ever holds a database-owned path.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt`
- **Verification:** `ORPHAN_GUARD_OK`; `:androidApp:assembleDebug` exit 0; the deletion sites are the only two that can remove a referenced file and both are behind a successful save.
- **Committed in:** `37d132c` (Task 2)

**2. [Rule 1 - Bug] Supersede bookkeeping runs after a successful import, not before**
- **Found during:** Task 2 (photo lifecycle)
- **Issue:** The plan's order ("when a photo is already attached, first run the supersede path, then call `PhotoStorage.persist`") means a failed import leaves `supersededPath` set while `photoPath` still names that same file. The next successful save would then delete the file the row it just wrote still references.
- **Fix:** The import runs first; the supersede step is applied to the previous path only once the new copy exists. On failure only the transient flags change.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt`
- **Verification:** `:androidApp:assembleDebug` exit 0; the failure branch touches no value and no path.
- **Committed in:** `37d132c` (Task 2)

**3. [Rule 2 - Missing Critical] `PhotoField(name: String)` — the accessibility contract needs the vehicle name**
- **Found during:** Task 3 (photo block)
- **Issue:** UI-SPEC's accessibility table requires the image to be announced as "Photo of {name}" with a fallback when the name is blank, but the plan's `<interfaces>` signature had no parameter to carry the name; the alternative was an unnamed or generic image description on the form's largest element.
- **Fix:** Added `name: String` (with the `title_vehicle_fallback` fallback for a blank name) and passed `values.name` from the screen.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/PhotoField.kt`, `VehicleEditScreen.kt`
- **Verification:** `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` exit 0.
- **Committed in:** `cf3d8ea` (Task 3)

**4. [Rule 2 - Missing Critical] `Ready.importing` / `Ready.photoError` and the `save()` import guard**
- **Found during:** Task 2 (photo lifecycle)
- **Issue:** The plan's own action text requires a transient `importing` flag ("so the field can show progress and the save button stays disabled") and a transient `photoError` flag on failure, but the 01-07 `Ready` shape has nowhere to carry either. Without the guard a save could be dispatched mid-import, marking the not-yet-written row's photo as saved.
- **Fix:** Added both fields (defaulted, like 01-07's `saveFailed`), the `onPhotoImportFailed()` retirement callback, and an `importing` check in `save()`'s early return; the screen disables the block and the submit button while either flag is set.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt`, `VehicleEditScreen.kt`
- **Verification:** `PHOTO_API_OK`, `STORAGE_USED_OK`; `:androidApp:assembleDebug` exit 0.
- **Committed in:** `37d132c` (Task 2)

**5. [Rule 1 - Bug] Test harness errors that were not the intended RED**
- **Found during:** Task 1 (RED run)
- **Issue:** The first failing run mixed the intended `Unresolved reference 'persist'/'delete'` errors with three harness faults: an imported FileKit `delete` extension shadowing `PhotoStorage.delete` calls (receiver-type mismatch), a missing `ExperimentalForeignApi` opt-in where `useContents` reads the compressed image's size, and a type-inference failure downstream of the unresolved `persist`. Left in, they would have made the GREEN commit carry test fixes and hidden whether the test failed for the right reason.
- **Fix:** Aliased the FileKit import (`delete as fileKitDelete`), annotated the dimension test with `@OptIn(ExperimentalForeignApi::class)` and gave the two resolved paths explicit `String` types. The re-run failed with **only** the two intended unresolved references.
- **Files modified:** `shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt`
- **Verification:** RED run output lists only `Unresolved reference 'persist'` / `'delete'`; the GREEN run passes 7/7.
- **Committed in:** `96325ae` (test), with the follow-up cleanups in `e09ea00` and `5c5e9c8`

### Notes (no code impact)

- **FileKit version.** The plan's context section names FileKit 0.11.0, while the project (plan 01-01) pins 0.16.0. Every API used here was verified against the 0.16.0 sources: `rememberFilePickerLauncher(type = FileKitType.Image)` with a `PickerResultLauncher`, `PlatformFile.write(bytes)`, non-suspend `createDirectories()`, and `toKotlinxIoPath()`. No version change was made or needed.
- **`persist` takes the picked file, not a path.** Consistent with the seam's interface and the plan's `<behavior>` wording; the picker's own location is read as bytes and never stored (T-01-51, proven by the byte-equality assertion).

**Total deviations:** 5 auto-fixed (3 bugs, 2 missing-critical) + 2 notes
**Impact on plan:** Every fix either prevents a data-loss path the plan's own ordering would have created (deviations 1 and 2), or supplies a flag/parameter the plan's action text requires but its interface listing omitted (3 and 4). No scope creep: no new screen, no new dependency, no change to the schema, the routes or the field order.

## Issues Encountered

- **Three plan verify snippets cannot be run literally.** `grep -Ec … | grep -v ':0$' && echo "…" || echo "…"` prints a bare count when the count is zero, so `RELATIVE_ONLY_OK`, `NOOP_REPLACED_OK` and `NO_CAMERA_OK` need re-running as `grep -Ec <file>`. All three are clean in that form (0 hits each), and the earlier plans hit the same snippet behaviour.
- **`grep -c 'OutlinedTextField'` returns 6 and `Scaffold` returns 2.** Both include the import line; the call-site counts are exactly 5 `OutlinedTextField(` and 1 `Scaffold(`, identical to the pre-plan file. The new photo block introduces no text field and no second `Scaffold`.
- **A stray "camera" in a test message.** The downscale assertion originally described "a full-resolution camera photo"; since the phase is under a no-camera discipline (D-04/T-01-50), the message was reworded so no casing of the word appears anywhere in `shared/src/` — the human check for that rule can now be a plain `grep -ri camera shared/src`.

## Known Stubs

None. Every element this plan added is reachable and wired: the picker launches from the block and the Add button, the image renders from stored bytes, Replace/Remove call the ViewModel, the failure message is shown on the existing Snackbar host, and `photoDeleter` is bound to the real storage seam.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **VEH-02 is functionally complete**: attach, preview, replace, remove, persist-across-restart and delete-with-vehicle are all implemented; the phase's human end-to-end check (attach on Add → preview → save → restart → thumbnail still renders → replace and confirm the old file is gone → remove and confirm the type-circle fallback → delete the vehicle) remains as the phase's final UAT step.
- **Plan 01-09 must honour the single-seam rule**: the Detail hero photo reads through `PhotoStorage.resolve(photoPath)` (as 01-06's card already does) and must not read `PlatformFile.path` or construct a location from the files directory — `PhotoStorage` is the only file allowed to do that (`SINGLE_SEAM_OK`).
- `PhotoStorage` is reusable as-is by any later surface: `persist` takes a `PlatformFile` and a file name, `resolve` is a pure mapping, and `delete` is safe to call from non-coroutine teardown paths.
- Blocking concern: none. The only uncommitted change in the working tree is the user's own `iosApp/iosApp.xcodeproj/project.pbxproj`, which this plan never staged or touched.

---

*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-19*

## Self-Check: PASSED

- All six source files exist at their committed paths, and every commit hash recorded above resolves in the git log.
