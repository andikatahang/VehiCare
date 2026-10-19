---
phase: 01-foundation-vehicle-management
reviewed: 2026-10-19T08:02:41Z
depth: standard
files_reviewed: 65
files_reviewed_list:
  - .github/workflows/ci.yml
  - androidApp/build.gradle.kts
  - androidApp/src/main/AndroidManifest.xml
  - androidApp/src/main/kotlin/com/vehicare/MainActivity.kt
  - androidApp/src/main/res/drawable/ic_launcher_foreground.xml
  - androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher.xml
  - androidApp/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml
  - androidApp/src/main/res/values/ic_launcher_background.xml
  - androidApp/src/main/res/values/strings.xml
  - gradle.properties
  - gradle/libs.versions.toml
  - iosApp/Configuration/Config.xcconfig
  - iosApp/iosApp.xcodeproj/project.pbxproj
  - shared/build.gradle.kts
  - shared/src/androidMain/kotlin/com/vehicare/AppVersion.android.kt
  - shared/src/androidMain/kotlin/com/vehicare/data/db/DriverFactory.android.kt
  - shared/src/androidMain/kotlin/com/vehicare/di/PlatformModule.android.kt
  - shared/src/commonMain/composeResources/drawable/compose-multiplatform.xml
  - shared/src/commonMain/composeResources/drawable/ic_vehicle_car.xml
  - shared/src/commonMain/composeResources/drawable/ic_vehicle_motorcycle.xml
  - shared/src/commonMain/composeResources/drawable/ic_vehicle_truck.xml
  - shared/src/commonMain/kotlin/com/vehicare/App.kt
  - shared/src/commonMain/kotlin/com/vehicare/AppVersion.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/VehicleEntityMapper.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepository.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseFactory.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/db/DriverFactory.kt
  - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/Vehicle.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/VehicleType.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/BottomTabs.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/settings/SettingsScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/theme/Color.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/theme/Theme.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/theme/Type.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleFormValidation.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ComingSoonSlot.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ConfirmDeleteDialog.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/FieldRow.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/LicensePlateChip.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/PhotoField.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleCard.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypeCircle.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypePicker.kt
  - shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt
  - shared/src/commonMain/sqldelight/com/vehicare/db/Vehicle.sq
  - shared/src/commonTest/kotlin/com/vehicare/SharedCommonTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/data/VehicleEntityMapperTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/VehicleFormValidationTest.kt
  - shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt
  - shared/src/iosMain/kotlin/com/vehicare/data/db/DriverFactory.ios.kt
  - shared/src/iosMain/kotlin/com/vehicare/Platform.ios.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/VehicleRepositoryTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/db/WalModeTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/ui/vehicles/VehicleDeletionTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt
findings:
  critical: 1
  warning: 3
  info: 6
  total: 10
status: issues_found
---

# Phase 01: Code Review Report

**Reviewed:** 2026-10-19T08:02:41Z
**Depth:** standard
**Files Reviewed:** 65
**Status:** issues_found

## Summary

Adversarial review of the phase's changed source files at depth `standard`: the data layer
(`VehicleRepositoryImpl`, SQLDelight schema/drivers, mapper), the three ViewModels and their screens, the
shared components, the photo-storage seam, platform entry points, build/CI configuration and the
phase's tests. The D-05/D-16/WAL invariants, the single-lane `Dispatchers.IO.limitedParallelism(1)`
thought process, the delete-then-delete-photo ordering, and the double-submit guard all hold up under
tracing. One defect is user-visible and breaks a promised recovery path (**CR-01**: "Try again" cannot
recover the list), plus three robustness gaps (IME insets on API < 30, photo-file litter on a failed
write, a vanished-row edit reporting success) and six informational items.

Scope notes:

- The two binary artifacts in the diff (`shared/src/commonMain/sqldelight/databases/1.db`,
  `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png`) contain no reviewable source
  and were noted, not reviewed.
- `iosApp/iosApp.xcodeproj/project.pbxproj` was reviewed at its **committed** revision only; the
  user's uncommitted working-tree re-serialization is out of scope.
- No structural pre-pass was supplied for this run, so there is no `## Structural Findings (fallow)`
  section.
- Already-recorded deviation (`material3 = "1.12.0-alpha03"`, see `01-VERSIONS-OPTION-B.md`) and
  in-test-only concerns were deliberately not re-flagged.

## Narrative Findings (AI reviewer)

The findings below are the AI reviewer's direct code review of the 65 files listed in the frontmatter
and are classified Critical / Warning / Info. All findings cite file:line and carry a concrete fix.

## Critical Issues

### CR-01: "Try again" cannot recover the list — `.catch` after `flatMapLatest` completes the whole assembled flow

**Severity:** Critical (incorrect behavior — the error screen's only recovery control is inert)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListViewModel.kt:51-70`

**Issue:** `uiState` is built as `refresh.flatMapLatest { repo.observeVehicles()… }.catch { emit(Error) }.stateIn(WhileSubscribed(5_000), …)`.
`Flow.catch` is terminal: when the upstream throws, it emits the fallback (`VehicleListUiState.Error`) and
then **completes the flow**. With `WhileSubscribed`, the completed upstream stays attached as long as the
list screen has subscribers, so nothing re-collects the chain — and `retry()` (lines 68-70) only bumps
`refresh`, which is no longer being collected. The result: the exact moment the user sees the Error state
and taps **Try again** (`VehicleListScreen.kt:112-130`, `onClick = viewModel::retry` at line 126), the
button does nothing. Recovery only happens incidentally later — when the screen's subscribers drop to zero
(navigate away/back), the 5 s `WhileSubscribed` stop elapses, and `stateIn` re-collects the cold chain from
`refresh`'s current value. A promised contract (UI-SPEC's Error state recovery path, the explicit
"Restart the underlying observation" doc comment at line 67) is therefore broken, and there is no test
covering `retry()` anywhere in `commonTest`/`iosTest`.

**Fix:** Move `.catch` inside the `flatMapLatest` body so each (re)armed observation has its own handler and
the outer chain survives an error, letting `retry()` restart the query immediately:

```kotlin
val uiState: StateFlow<VehicleListUiState> =
    refresh
        .flatMapLatest {
            repo.observeVehicles()
                .map { vehicles ->
                    if (vehicles.isEmpty()) VehicleListUiState.Empty
                    else VehicleListUiState.Content(vehicles)
                }
                // Terminal for this observation only: the outer flow stays alive so a
                // `refresh` bump (retry) re-runs the query while the screen is subscribed.
                .catch { emit(VehicleListUiState.Error(message = it.message)) }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = VehicleListUiState.Loading,
        )
```

Add a `commonTest` case that drives `repo.observeVehicles()` to throw on first collection, asserts
`Error`, calls `retry()` and asserts the second collection (returning rows) reaches `Content`.

## Warnings

### WR-01: `Modifier.imePadding()` is a no-op on API 26–29 — no `windowSoftInputMode="adjustResize"`

**Severity:** Warning (edge case with user-visible impact on the supported API floor)
**File:** `androidApp/src/main/AndroidManifest.xml:11-13` (and the consumer at `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt:216`)

**Issue:** The app is edge-to-edge (`MainActivity.kt:19` calls `enableEdgeToEdge()`, i.e.
`decorFitsSystemWindows = false`) and the Add/Edit form relies on `Modifier.imePadding()` to keep the
submit button and fields above the software keyboard (comment at line 214-215 says exactly this). On
API levels below 30, IME window insets are only dispatched to the view hierarchy when the activity is
declared with `android:windowSoftInputMode="adjustResize"`. `minSdk` is 26 (`gradle/libs.versions.toml:4`),
and the `<activity>` element declares no `windowSoftInputMode`, so on API 26–29 the keyboard overlays the
form (the submit button in `VehicleEditScreen.kt:320-342` and lower fields can be covered with no way to
scroll them into view). The phase's automated evidence (build + host/simulator tests) cannot catch this —
it needs a device/emulator run on API ≤ 29 or the declaration itself.

**Fix:**

```xml
<activity
    android:exported="true"
    android:name=".MainActivity"
    android:windowSoftInputMode="adjustResize">
```

(Alternatively declare it on `<application>`; the activity-level declaration is the documented requirement
for Compose IME insets on API < 30.)

### WR-02: A failed/partial photo write leaves an unreferenced file that nothing can ever clean up

**Severity:** Warning (silent storage litter; grows with every failed import)
**File:** `shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt:55-61` (caller: `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditViewModel.kt:219-257`)

**Issue:** `persist()` writes straight to the final name: `resolve(relative).write(compress(source))`.
If that suspend `write` fails partway (I/O error, disk full, or a `CancellationException` delivered at
that suspension point after bytes have landed), the caller's handler
(`VehicleEditViewModel.kt:230-238`) only sets `photoError = true` and returns — the partial file stays on
disk. Nothing can remove it afterwards: the file name was never recorded in the form state, so
`persistedButUnsaved`/`values.photoPath` never reference it, and `onCleared()`
(`VehicleEditViewModel.kt:294-300`) only deletes the currently-held path. Because file names use a
monotonic stamp (`nextPhotoFileName()`, lines 307-309) the name is never reused, so each occurrence is
permanent litter. (`only-saved-session copies are protected` logic elsewhere is correct — this is purely
the failed-write path.)

**Fix:** Make the write self-cleaning inside the seam it belongs to — best-effort delete on any
non-normal exit, rethrowing cancellation:

```kotlin
suspend fun persist(picked: PlatformFile, fileName: String): String {
    val relative = "$DIRECTORY/$fileName"
    val source = picked.readBytes()
    resolve(DIRECTORY).createDirectories()
    try {
        resolve(relative).write(compress(source))
    } catch (cancelled: CancellationException) {
        delete(relative)   // best-effort; non-suspend, safe on cancellation
        throw cancelled
    } catch (failure: Exception) {
        delete(relative)   // removes the partial/truncated copy
        throw failure
    }
    return relative
}
```

(For full atomicity, write to `"$relative.tmp"` and rename after; the delete-on-failure version is the
minimal change that closes the litter leak.)

### WR-03: Editing a row that no longer exists reports "saved"

**Severity:** Warning (false success on update; low reachability in Phase 1 but a real path)
**File:** `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt:59-71`

**Issue:** The update branch issues `database.vehicleQueries.update(...)` and unconditionally returns
`vehicle.id`, without checking that a row was actually updated. If the target row was deleted between the
edit screen loading it and the save tap (e.g. the vehicle is deleted elsewhere and the user returns to a
still-live Edit destination), the update affects 0 rows but `save()` "succeeds" →
`VehicleEditViewModel.save` (lines 186-199) calls `markPhotoSaved()` and `onSaved()`, the user is navigated
away, and the edit silently vanishes. The load path has a `NotFound` state for exactly this condition
(`VehicleEditViewModel.kt:127-145`); the save path has no equivalent.

**Fix:** Verify existence inside the same transaction and surface the condition as a failure (the
ViewModel's `catch (failure: Exception)` at lines 194-198 already turns it into the retry-able
save-failure Snackbar):

```kotlin
} else {
    database.vehicleQueries.update(
        /* …as today… */
        id = vehicle.id,
    )
    check(database.vehicleQueries.selectById(vehicle.id).executeAsOneOrNull() != null) {
        "Vehicle ${vehicle.id} no longer exists"
    }
    vehicle.id
}
```

(`UPDATE … RETURNING id` in `Vehicle.sq` and asserting `executeAsOneOrNull() != null` is an equivalent
alternative.)

## Info

### IN-01: Unused version-catalog entries

**Severity:** Info
**File:** `gradle/libs.versions.toml:17,29,30,34`

**Issue:** `junit` (version + library), `androidx-core-ktx` and `androidx-appcompat` are declared but
referenced by no module — a grep for `libs.junit`, `libs.androidx.core.ktx` and `libs.androidx.appcompat`
across `build.gradle.kts`, `settings.gradle.kts`, `androidApp/build.gradle.kts` and
`shared/build.gradle.kts` finds nothing (`libs.kotlin.testJunit` and `libs.androidx.testExt.junit` are the
ones actually used).
**Fix:** Delete the four entries, or wire them where intended.

### IN-02: `PhotoStorage.resolve()`/`delete()` do not contain the path they are given

**Severity:** Info (defense-in-depth; no reachable caller today)
**File:** `shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt:69-87`

**Issue:** Both functions accept any string and hand it to `PlatformFile(FileKit.filesDir, relativePath)`.
A value containing `..` segments (e.g. from a tampered/impossible `photo_path` DB value, or a future
caller that forgets the relative-only contract documented at lines 43-50) would resolve outside the
`vehicles/` directory, and `delete()` would remove it. Today every caller passes either a self-generated
name or a DB-held value written by this same object, so this is not exploitable — but it is the seam that
the whole design says must stay contained.
**Fix:** Add a containment guard, e.g. in `resolve`:

```kotlin
fun resolve(relativePath: String): PlatformFile {
    require(!relativePath.contains("..")) { "photo paths must stay inside app storage" }
    return PlatformFile(FileKit.filesDir, relativePath)
}
```

### IN-03: The photo-import failure is rendered twice and the inline message self-destructs with the Snackbar

**Severity:** Info (UX inconsistency, no data impact)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt:112-117` and `:314`; `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/PhotoField.kt:150`

**Issue:** One flag (`state.photoError`) drives both the inline error text inside `PhotoField`
(`error = state.photoError`) and the `LaunchedEffect`-driven Snackbar, and the effect calls
`onPhotoImportFailed()` immediately after `showSnackbar` returns — so the inline message is cleared at the
same moment the Snackbar dismisses. The user sees the same sentence twice simultaneously, then has no
persistent cue left. (The ViewModel's one-shot-flag design is sound; the screen just consumes the flag for
two renderers.)
**Fix:** Pick one channel — e.g. drop the Snackbar path and keep the inline `PhotoField` error until the
next pick/edit, or keep the Snackbar and stop passing `error` into `PhotoField`.

### IN-04: `sanitizeYear` accepts non-ASCII Unicode digits

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleFormValidation.kt:57`

**Issue:** `raw.filter { it.isDigit() }.take(4)` uses the Unicode-aware `Char.isDigit()` (category Nd), so
strings like `"٢٠٢٤"` (Arabic-Indic) or `"२०२४"` (Devanagari) pass the "digits only" rule and are stored
verbatim. D-13's intent is a plain year field; the tests only cover ASCII digits.
**Fix:** If ASCII-only is the intent: `raw.filter { it in '0'..'9' }.take(4)`.

### IN-05: The schema-drift guard cannot see untracked regeneration output

**Severity:** Info
**File:** `.github/workflows/ci.yml:36-39`

**Issue:** `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` only fails on changes to
**tracked** files. A regeneration run that leaves the database directory with new/untracked content (e.g.
a future bumped schema version producing a new `.db` baseline, or a deleted-then-recreated baseline)
produces no diff and the guard passes silently. The load-bearing case (drift of the tracked `1.db`) is
correctly caught — this is a blind spot, not a hole in the main control.
**Fix:** Also fail on dirty/untracked state in that path:

```yaml
run: |
  ./gradlew generateCommonMainVehiCareDatabaseSchema
  git diff --exit-code -- shared/src/commonMain/sqldelight/databases
  test -z "$(git status --porcelain -- shared/src/commonMain/sqldelight/databases)"
```

### IN-06: Deprecated `getPackageInfo(String, Int)` on API 33+

**Severity:** Info
**File:** `shared/src/androidMain/kotlin/com/vehicare/AppVersion.android.kt:17`

**Issue:** With `compileSdk = 37`, `packageManager.getPackageInfo(context.packageName, 0)` is the
deprecated (API 33+) overload and emits a build-time deprecation warning; the flagged replacement is
`PackageInfoFlags.of(0)`.
**Fix:**

```kotlin
val info = if (Build.VERSION.SDK_INT >= 33) {
    context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
} else {
    @Suppress("DEPRECATION")
    context.packageManager.getPackageInfo(context.packageName, 0)
}
info.versionName.orEmpty()
```

---

_Reviewed: 2026-10-19T08:02:41Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
