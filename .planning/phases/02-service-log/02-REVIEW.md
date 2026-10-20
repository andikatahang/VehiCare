---
phase: 02-service-log
reviewed: 2026-10-20T06:22:47Z
depth: standard
files_reviewed: 51
files_reviewed_list:
  - .github/workflows/ci.yml
  - shared/build.gradle.kts
  - shared/src/commonMain/composeResources/values/strings.xml
  - shared/src/commonMain/kotlin/com/vehicare/App.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/HistorySort.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/ServicePart.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceSummary.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceTypePresets.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/components/ConfirmDeleteDialog.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/components/EmptyState.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/components/FieldRow.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceSummaryViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/HistorySortControl.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/PartsEditor.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceDateField.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypeDialog.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt
  - shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt
  - shared/src/commonMain/kotlin/com/vehicare/util/ServiceDates.kt
  - shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq
  - shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm
  - shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/domain/ServiceTypeVocabularyTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryFormValidationTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceMetaLineTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/util/RupiahFormatTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/util/ServiceDatesTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt
findings:
  critical: 2
  warning: 11
  info: 7
  total: 20
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-10-20T06:22:47Z
**Depth:** standard
**Files Reviewed:** 51
**Status:** issues_found

## Summary

Adversarial review of the phase's changed source files at depth `standard`: the SQL schema and the
`1.sqm` migration, the repository (composed reads, transaction writes, delete ordering, the D-39
vehicle cascade), the four service destinations and their three ViewModels, the new/shared
components, the photo-storage seam, the date/rupiah utilities, the Koin graph, the routes, and the
phase's tests.

The data layer holds up under tracing: `selectSummary`/aggregate null handling, both history orders
with `id DESC` tie-breaks, the `selectPreviousMileage` gate, the learned-type `MAX(id) GROUP BY
lower(service_type)` query, the "replace children inside one transaction" update, the
collect-paths-then-delete-files cascade, and the single-lane dispatcher are all correct. The
`1.sqm` DDL reproduces the `ServiceEntry.sq` schema and CI runs
`verifyCommonMainVehiCareDatabaseMigration`, so migration drift is guarded.

The defects are concentrated in the one stateful area the automated gates do not exercise — the
Add/Edit form ViewModel — and in the UI-SPEC conformance of the new components:

- **CR-01** is a photo-bookkeeping mis-transliteration from Phase 1's single-photo *replace* flow.
  In this phase's additive 5-photo strip it queues **every existing photo** for file deletion on the
  next pick, which permanently destroys user photos in edit mode and on the second photo of an add.
- **CR-02** is the Phase 1 `retry()`-is-inert defect, recoded in the new history ViewModel: the
  Error screen's only recovery control does nothing.
- The Warnings cover the unreachable mileage-error condition, two edit-form state-machine failure
  paths, the wrong save-error copy plus the missing "Try again" Snackbar action, an abandonment
  race that can delete files a save is committing, four accessibility/touch-target/input-cap
  violations, the hand-rolled chip wrapping, and the missing photo empty-state block.

Neither Critical is covered by a test: there is no `ServiceEntryEditViewModel`,
`ServiceHistoryViewModel`, or screen-level test anywhere in `commonTest`/`iosTest` (only
repository, mapper, validation, utility and vocabulary tests exist).

Scope notes:

- No structural pre-pass was supplied for this run, so there is no `## Structural Findings (fallow)`
  section.
- The binary `shared/src/commonMain/sqldelight/databases/2.db` was checked for existence/schema
  version only, not reviewed as source.
- Accepted Phase 1 debt is deliberately **not** re-reported: `01-REVIEW.md` CR-01 (retry),
  WR-01 (`imePadding`), WR-02 (failed photo-write litter), WR-03 (vanished-row update) and IN-02
  (`PhotoStorage` containment). CR-02 below is new Phase 2 code, not a re-report of Phase 1's CR-01;
  the analogous zero-row-update behavior in `ServiceEntryRepositoryImpl.save()` is part of the
  accepted Phase 1 pattern and is likewise not re-flagged.
- Build/test gates had already passed (`assembleDebug`, iOS compile, host and simulator tests), so
  this review targets runtime behavior and contract conformance rather than compilation.

## Narrative Findings (AI reviewer)

The findings below are the AI reviewer's direct code review of the 51 files listed in the frontmatter
and are classified Critical / Warning / Info. All findings cite file:line and carry a concrete fix.

## Critical Issues

### CR-01: Picking a photo queues every existing photo for deletion — irreversible photo loss

**Severity:** Critical (data loss — DB rows keep paths whose files are deleted)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt:273-290` (deletion at `:373-378`)

**Issue:** After a successful import, `onPhotoPicked` takes **all** current photo paths and routes
each one as if it had just been removed:

```kotlin
val previousPaths = latest.values.photoPaths
// For each removed path (should be at most one in current UX), route it
previousPaths.forEach { previous ->
    if (persistedButUnsavedPaths.contains(previous)) {
        PhotoStorage.delete(previous)          // session copy deleted immediately
        persistedButUnsavedPaths.remove(previous)
    } else {
        supersededPaths.add(previous)          // DB-owned copy queued for deletion on save
    }
}
...
values = latest.values.copy(photoPaths = latest.values.photoPaths + relativePath)
```

The comment ("should be at most one in current UX") is a leftover from Phase 1's *single-photo
replace* flow. Phase 2's strip is **additive** (up to 5 photos, `ServicePhotoStrip.kt:158`), so
"all previous paths" is not "the replaced path":

- **Edit mode:** an entry with one or more photos. The user adds one more. Every existing path is
  moved into `supersededPaths`, but the form still lists them (line 288) and
  `ServiceEntryRepositoryImpl.save()` re-inserts them as `service_photo` rows
  (`ServiceEntryRepositoryImpl.kt:143-148`). On success, `markPhotosSaved()` (line 376) deletes
  those files. The saved row now references deleted files: the user's original photos are
  **permanently gone** and the detail screen renders empty thumbnails.
- **Add mode:** the first pick lands in `persistedButUnsavedPaths`. The second pick finds it there,
  deletes its file immediately (line 279) and removes it from the set, while line 288 keeps the
  path in `values.photoPaths`. Saving inserts a row pointing at the deleted file — the first photo
  is lost before the entry even exists.

There is no `ServiceEntryEditViewModel` test, so nothing exercises the bookkeeping.

**Fix:** this screen has no replace semantics; after a successful persist, only record the new file.

```kotlin
val latest = _uiState.value as? ServiceEntryEditUiState.Ready ?: return@launch
persistedButUnsavedPaths.add(relativePath)
_uiState.value = latest.copy(
    values = latest.values.copy(photoPaths = latest.values.photoPaths + relativePath),
    importing = false,
)
```

Delete lines 274-285. If a real single-photo *replace* control is ever added, it must target the
replaced index exactly as `onPhotoRemoved` (lines 298-313) already does — never iterate the whole
list. Add VM tests for: (a) pick with pre-existing DB paths leaves `supersededPaths` empty and the
files on disk; (b) two picks in add mode leave both files present after the second pick.

### CR-02: "Try again" is inert on the Service history screen — `.catch` completes the assembled flow

**Severity:** Critical (the Error state's only recovery control does nothing)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt:56-71` (retry at `:79-81`; button at `ServiceHistoryScreen.kt:164-169`)

**Issue:** `uiState` is `_sort.flatMapLatest { refresh.flatMapLatest { repo.observeHistory(...) } }`,
then `.map { ... }.catch { emit(Error) }.stateIn(...)`. `Flow.catch` is terminal: when
`observeHistory` throws, the exception propagates through both `flatMapLatest` operators, `.catch`
emits `Error` and the whole chain **completes**. `refresh` is no longer collected, so `retry()`
(`refresh.value++`) has no collector to restart. The user sees the Error state, taps the only button
on it, and nothing happens; recovery only occurs incidentally after leaving the screen and letting
the `WhileSubscribed(5_000)` stop elapse so `stateIn` re-collects the cold chain.

This is the same defect class as `01-REVIEW.md` CR-01, but in new Phase 2 code and with no test
covering `retry()`.

**Fix:** keep the error handler inside the inner observation so the outer chain survives and
`refresh` can re-arm the query:

```kotlin
val uiState: StateFlow<ServiceHistoryUiState> =
    _sort.flatMapLatest { mode ->
        refresh.flatMapLatest {
            repo.observeHistory(vehicleId, mode)
                .map { entries ->
                    if (entries.isEmpty()) ServiceHistoryUiState.Empty
                    else ServiceHistoryUiState.Content(entries)
                }
                // Terminal for this observation only: a `refresh` bump (retry) re-runs it.
                .catch { emit(ServiceHistoryUiState.Error(message = it.message)) }
        }
    }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ServiceHistoryUiState.Loading,
    )
```

Add a `commonTest` that throws on the first collection, asserts `Error`, calls `retry()` and asserts
the second collection reaches `Content`.

## Warnings

### WR-01: The mileage error condition can never be true — blank mileage gives no feedback

**Severity:** Warning (dead condition; user-visible missing validation)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt:260` (rendering at `:281-298`)

**Issue:**

```kotlin
val values = state.values
...
val mileageError = state.mileageBaseline != null && values.mileage.isBlank() &&
    values.mileage != (state as? ServiceEntryEditUiState.Ready)?.values?.mileage
```

`state` is already a non-null `Ready` and `values` **is** `state.values`, so the last comparison is
always false. The whole expression is therefore always false: `isError` is never set and
`error_mileage_required` is unreachable. Clearing the mileage leaves a field that looks valid while
the submit button silently stays disabled (`ServiceEntryFormValidation.isSubmittable`,
`ServiceEntryFormValidation.kt:56-57`). The additional `mileageBaseline != null` gate would also
suppress the message on a vehicle's first entry.

`ServiceEntryFormValidation.errorFor` (`ServiceEntryFormValidation.kt:60-61`) exists for this but is
called from nowhere (contrast `VehicleEditViewModel.kt:164`), and it is **not** a drop-in fix: it
returns `MILEAGE_REQUIRED` whenever the form is not submittable, so wiring it directly would show an
error on an untouched form before a service type is chosen. UI-SPEC's "Add/Edit entry — Invalid"
state requires the message only after the user clears a previously non-empty required text value.

Secondary contract gaps in the same block: C-13 requires the supporting line to show the formatted
mileage (`123.456 km`) when no warning applies — nothing is rendered (the `value_mileage_km` string
at `strings.xml:102` is unused); and C-27 requires every supporting line to use `labelLarge`, which
this same commit added to the vehicle form (`VehicleEditScreen.kt:248`) but not here.

**Fix:** track "the user cleared a previously non-empty value" and render the three states with the
declared style:

```kotlin
// screen-level: flip to true only when a non-blank value is cleared
var mileageCleared by remember { mutableStateOf(false) }
// in onValueChange: mileageCleared = raw.isBlank() && values.mileage.isNotBlank()
val mileageError = mileageCleared && values.mileage.isBlank()
val mileagePreview = values.mileage.toLongOrNull()?.let(RupiahFormat::formatMileage)

supportingText = when {
    mileageError -> { { Text(stringResource(Res.string.error_mileage_required),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error) } }
    mileageWarning -> { { Text(stringResource(Res.string.warn_mileage_lower),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.error) } }
    mileagePreview != null -> { { Text(mileagePreview, color = Slate,
        style = MaterialTheme.typography.labelLarge) } }
    else -> null
}
```

Then either delete `errorFor` or make it match this rule (and add a test for the cleared case).

### WR-02: Edit-load error handling can wipe a filled form and can strand the screen on Loading

**Severity:** Warning (in-memory data loss on a late error; unhandled failure path)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt:155-176`

**Issue:**

```kotlin
repo.observeEntry(entryId)
    .catch { _uiState.value = ServiceEntryEditUiState.NotFound }
    .collect { entry -> ... }
```

Two problems:

1. The `.catch` is not gated by `initialLoadApplied`. Any error emitted by the composed flow *after*
   the form is populated replaces the user's filled `Ready` state with the NotFound screen, silently
   discarding every field they typed (the VM keeps no draft).
2. Exceptions thrown **inside** the `collect` body — `repo.observePreviousMileage(...).first()`
   (line 166) and `repo.observeLearnedTypes().first()` (line 167) — are not upstream of `.catch`,
   so they are not handled at all. They kill the `viewModelScope.launch`, and the screen stays in
   `Loading` forever with no error, no NotFound surface, and no retry.

**Fix:** gate the catch on the first emission and handle the seeding failures explicitly:

```kotlin
viewModelScope.launch {
    repo.observeEntry(entryId)
        .catch {
            if (!initialLoadApplied) {
                initialLoadApplied = true
                _uiState.value = ServiceEntryEditUiState.NotFound
            }
        }
        .collect { entry ->
            if (initialLoadApplied) return@collect
            initialLoadApplied = true
            if (entry == null) {
                _uiState.value = ServiceEntryEditUiState.NotFound
                return@collect
            }
            _uiState.value = try {
                ServiceEntryEditUiState.Ready(
                    values = entry.toFormValues(),
                    mileageBaseline = repo.observePreviousMileage(vehicleId, entryId).first(),
                    learnedTypes = repo.observeLearnedTypes().first(),
                )
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ServiceEntryEditUiState.NotFound   // or a dedicated load-error state
            }
        }
}
```

### WR-03: Add-mode seeding overwrites whatever the user typed and has no failure path

**Severity:** Warning (input loss on a slow seed; form can stay unseeded with Save disabled)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt:128-152` (display fallback at `ServiceEntryEditScreen.kt:249-255`)

**Issue:** Add mode starts in `Ready(ServiceEntryFormValues())`, i.e. `dateEpochMs = null`, while
the screen renders today's date anyway:

```kotlin
epochMs = values.dateEpochMs ?: ServiceDates.toStorage(ServiceDates.today())
```

The form is interactive during the seed, and when the coroutine completes it assigns a **fresh**
`Ready` built from scratch (lines 137-151), replacing the whole `values` object and resetting
`saveFailed`/`saving`/`importing`. Anything typed in the meantime is discarded. Worse, the seed has
no failure handling: if `observeLatestMileage(...).first()` or `observeLearnedTypes().first()`
throws, the launch dies and the state stays at the unseeded one — the date field still *shows*
today, but `dateEpochMs` is null, so Save is disabled with no explanation, and the mileage prefill
and learned chips never arrive.

**Fix:** set the date synchronously (it is pure and cheap) so the gate matches the display, then
merge the asynchronous pieces into the current state instead of replacing it:

```kotlin
private val _uiState = MutableStateFlow<ServiceEntryEditUiState>(
    if (isAdd) ServiceEntryEditUiState.Ready(
        values = ServiceEntryFormValues(dateEpochMs = ServiceDates.toStorage(ServiceDates.today())),
    ) else ServiceEntryEditUiState.Loading,
)

init {
    if (isAdd) viewModelScope.launch {
        try {
            val latest = repo.observeLatestMileage(vehicleId).first()
            val learned = repo.observeLearnedTypes().first()
            val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return@launch
            _uiState.value = current.copy(
                values = current.values.copy(
                    mileage = current.values.mileage.ifBlank { latest?.toString().orEmpty() },
                ),
                mileageBaseline = latest,
                learnedTypes = learned,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Leave the form usable; a failed seed must not disable Save.
        }
    } else { /* edit path */ }
}
```

### WR-04: Save failure shows the vehicle copy and omits the required "Try again" action

**Severity:** Warning (wrong user-facing copy; promised recovery action missing)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt:106` and `:114-119`

**Issue:** The service-entry form resolves the **vehicle** save-error string:

```kotlin
val saveErrorText = stringResource(Res.string.error_save_vehicle)   // "Couldn't save this vehicle…"
```

The phase added the correct `error_save_entry` (`strings.xml:125`, "Couldn't save this service
entry…") and never uses it. The user is told a vehicle failed to save on a service-entry form.

Additionally, UI-SPEC's "Add/Edit entry — Save failed" state (line 267) requires the Snackbar to
carry a **"Try again" action that re-invokes submit**. `showSnackbar(saveErrorText)` has no
`actionLabel` and no result handling, so the copy's "then try again" has no in-context control and
the user must find the submit button again.

**Fix:**

```kotlin
val saveErrorText = stringResource(Res.string.error_save_entry)
val retryLabel = stringResource(Res.string.action_try_again)

LaunchedEffect(saveFailed) {
    if (saveFailed) {
        val result = snackbarHostState.showSnackbar(
            message = saveErrorText,
            actionLabel = retryLabel,
        )
        viewModel.onSaveErrorShown()
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.save(onSaved = onSaved)
        }
    }
}
```

(`onSaved` is already a parameter of `ServiceEntryEditRoute`.)

### WR-05: Leaving the form during a save can delete files the same save commits

**Severity:** Warning (data-loss race on back-during-save; narrow window)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt:357-360` (save at `:229-241`)

**Issue:** `onCleared()` unconditionally deletes every `persistedButUnsavedPaths` entry, and it runs
during teardown while the save coroutine may still be in flight (`viewModelScope` is cancelled by
the same `clear()`, and a transaction that has already committed is not rolled back by
cancellation). The form's top-bar back arrow stays enabled while `saving`
(`ServiceEntryEditScreen.kt:143-150`). If the user leaves during the save's IO window, the files are
deleted while `ServiceEntryRepositoryImpl.save()` either has already committed the same paths as
`service_photo` rows or is about to. The row then references deleted files — the same
broken-image/lost-photo outcome as CR-01, on a narrower window. `onCleared` has no way to know
whether the write committed.

**Fix:** take the submitted paths out of the "abandonable" set for the duration of the save and hand
them back only on failure:

```kotlin
val submittedPhotos = persistedButUnsavedPaths.toSet()
persistedButUnsavedPaths.clear()      // no longer abandonable while the write is in flight
viewModelScope.launch {
    try {
        repo.save(ServiceEntryFormValidation.toServiceEntry(entryId, vehicleId, submitted))
        markPhotosSaved()
        clearSaving()
        onSaved()
    } catch (cancelled: CancellationException) {
        // The write may or may not have committed: keep the files rather than risk deleting
        // ones the row now references. A rare orphan file is the safer failure.
        throw cancelled
    } catch (_: Exception) {
        persistedButUnsavedPaths.addAll(submittedPhotos)
        markSaveFailed()
    }
}
```

Also consider disabling the back affordance (or ignoring it) while `saving`.

### WR-06: The photo remove control has a 16dp hit target and no accessible label

**Severity:** Warning (accessibility + UI-SPEC C-18 touch-target violation on the only thumbnail control)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt:128-152`

**Issue:** Three defects in one control:

1. `removeContentDesc` is computed at line 112 ("Remove photo {index}") but never applied: the
   `Icon` passes `contentDescription = null` (line 148) and the enclosing clickable `Box` has no
   `semantics`. TalkBack exposes an unlabeled clickable node; UI-SPEC §Content descriptions requires
   "Remove photo {index}".
2. The modifier chain is `.size(24.dp).padding(4.dp) … .defaultMinSize(48.dp, 48.dp).clickable`.
   `size()` fixes the incoming constraints at 24dp, `padding()` shrinks them to 16dp, and
   `defaultMinSize` cannot grow a non-zero incoming minimum. The clickable region is
   **16dp × 16dp**, against the 48dp minimum in UI-SPEC §Touch targets and C-18.
3. Because `padding(4.dp)` precedes `background`/`drawBehind`, the `Paper` circle renders 16dp
   while `drawCircle(radius = 12.dp)` draws a 24dp ring outside those bounds (unclipped), so the
   visual is neither the specified 24dp circle nor a 16dp glyph disc.

**Fix:** put the touch target on the outer node and the visuals on an inner one:

```kotlin
Box(
    modifier = Modifier
        .size(48.dp)                                  // 48dp target first
        .clickable(enabled = enabled && !importing) { onRemove?.invoke(index) }
        .semantics { contentDescription = removeContentDesc },
    contentAlignment = Alignment.Center,
) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .background(Paper, RoundedCornerShape(50.dp))
            .drawBehind {
                drawCircle(Hairline, radius = 12.dp.toPx(), style = Stroke(1.dp.toPx()))
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = null,               // the parent carries the label
            modifier = Modifier.size(16.dp),
            tint = Slate,
        )
    }
}
```

### WR-07: The UI-SPEC input caps are not enforced anywhere

**Severity:** Warning (contract violation; unbounded user text reaches storage)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypeDialog.kt:67`, `ServiceEntryEditScreen.kt:353`, `:381`, `:391`, `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/PartsEditor.kt:79`

**Issue:** UI-SPEC C-17 and the form table require: custom service type ≤ 60 chars, notes ≤ 500,
workshop ≤ 120, mechanic ≤ 120, part name ≤ 120. None is enforced — every field forwards raw input:

- `ServiceTypeDialog.kt:67` → `onValueChange = { text = it }` (the KDoc at lines 35-37 claims
  "max 60 chars").
- `ServiceEntryEditScreen.kt:353` → `onFieldChange { it.copy(notes = notes) }`.
- `ServiceEntryEditScreen.kt:381` / `:391` → workshop / mechanic forwarded unchanged.
- `PartsEditor.kt:79` → part name forwarded unchanged.

Only mileage (9 digits) and cost (12 digits) are capped, via `ServiceEntryFormValidation`. Values
land in unbounded `TEXT` columns with no DB `CHECK`, and an over-long custom type becomes a chip
label (see WR-09). `error_add_photo`-style copy aside, nothing tells the user input was limited
because it never is.

**Fix:** cap at the input boundary, preferably through constants in `ServiceEntryFormValidation` so
they are testable in one place:

```kotlin
// ServiceEntryFormValidation
const val MAX_NOTES = 500
const val MAX_WORKSHOP = 120
const val MAX_MECHANIC = 120
const val MAX_PART_NAME = 120
const val MAX_CUSTOM_TYPE = 60
```

then `onValueChange = { text = it.take(MAX_CUSTOM_TYPE) }`,
`it.copy(notes = notes.take(MAX_NOTES))`, `take(MAX_WORKSHOP)` / `take(MAX_MECHANIC)` /
`take(MAX_PART_NAME)`. Add a unit test per cap.

### WR-08: Part quantity is not digits-filtered — non-numeric input silently saves as 1

**Severity:** Warning (silent data corruption; docs claim filtering that does not exist)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/PartsEditor.kt:88-98` (VM at `ServiceEntryEditViewModel.kt:323-330`, fallback at `ServiceEntryFormValidation.kt:94`)

**Issue:** The quantity field passes raw text (`onValueChange = { qty -> onPartChange(index, row.name, qty) }`)
and `onPartChange` stores it as-is. Both `PartsEditor` (line 49, "digits only (numeric keyboard,
**filtered in ViewModel**)") and `ServiceEntryFormValidation` (line 29, "quantity digits-only
(validated in ViewModel)") document filtering that does not exist. `KeyboardType.Number` is only a
keyboard hint: hardware keyboards, paste, and some IMEs bypass it. At save,
`toIntOrNull()?.coerceAtLeast(1) ?: 1` turns "2x", "two", "1 2" or "-3" into **quantity 1** with no
feedback, silently changing what the user entered.

**Fix:** filter where the docs say it happens (allow blank so the user can retype; blank already
falls back to 1 on save):

```kotlin
fun onPartChange(index: Int, name: String, quantity: String) {
    val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
    val parts = current.values.parts.toMutableList()
    if (index in parts.indices) {
        parts[index] = PartRow(name, quantity.filter { it.isDigit() }.take(4))
        _uiState.value = current.copy(values = current.values.copy(parts = parts))
    }
}
```

Add a test that `"2x"` becomes quantity 2 (not 1) through `toServiceEntry`.

### WR-09: Service-type chips are wrapped by a hardcoded 360px width estimate

**Severity:** Warning (layout correctness: clipped/overflowing chips at other widths or font scales)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt:66-108`

**Issue:** The chip group hand-rolls row packing during composition:

```kotlin
val textWidth = option.length * 8f + 32f
val maxWidth = 360f // Approximate screen width minus padding
```

The estimate ignores the actual row width, M3 chip paddings, font scale, and the selected chip's
16dp check icon (only `+24f`, and only if the chip is already selected when the row is packed). On a
320dp phone a row overflows and the trailing chips are clipped off-screen with no horizontal scroll
to reach them; on a tablet it breaks far too early; with `fontScale > 1` every estimate is wrong. A
60-char custom label (the cap WR-07 is meant to permit) is estimated at ~512px and overflows any
row by itself. The UI-SPEC's component inventory explicitly names `FlowRow` for this (D-26, C-15).
The code comment ("For now, use a Column of Rows with manual wrapping") marks it as unfinished.

**Fix:** replace the manual packing with the pinned `FlowRow`:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceTypePicker(...) {
    val allOptions = buildList { addAll(presets); addAll(learned) }
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allOptions.forEach { option ->
                renderChip(option, option == selected, enabled, onSelect)
            }
        }
        // "Add custom…" row unchanged
    }
}
```

This also removes the mutable lists built during composition.

### WR-10: The date field is tapped through an unlabeled clickable overlay

**Severity:** Warning (accessibility: the field's only interaction is invisible to screen readers)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceDateField.kt:95-103`

**Issue:** The field is made tappable by stacking a transparent, content-less `Box` with
`.clickable { showPicker = true }` over the `OutlinedTextField`. The overlay has no role, no
`onClickLabel`, and no content description, so the accessibility tree exposes an unlabeled clickable
node covering the date — TalkBack cannot announce "Service date" or what the tap does, and the
field's own semantics are obscured by the overlay. UI-SPEC §Interaction rules make tapping the field
the only way to open the picker, so this control is the sole path to setting a date. There is no
minimum touch-target issue (the overlay is 56dp), only a labeling/role one.

**Fix:** give the overlay the field's semantics and a role, or make the field itself the clickable
node:

```kotlin
val setDateLabel = stringResource(Res.string.action_set_date)
val fieldLabel = stringResource(Res.string.field_service_date)
Box(
    modifier = Modifier
        .fillMaxWidth()
        .defaultMinSize(minHeight = 56.dp)
        .clickable(role = Role.Button, onClickLabel = setDateLabel) { showPicker = true }
        .semantics { contentDescription = "$fieldLabel: $displayText" },
)
```

### WR-11: The zero-photo state is a tiny tile instead of the specified 16:9 add block

**Severity:** Warning (the Add form's only photo affordance deviates from the binding empty-state anatomy)
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt:158-179`

**Issue:** With zero photos the strip renders the same 80dp add tile it uses between thumbnails.
UI-SPEC (Component Dimensions → empty photo block; C-18; Interaction rules line 288) requires the
Phase 1 empty anatomy: a **full-content-width 16:9 `Mist` block** with a centred 24dp `Add` glyph
and the "Add photo" affordance; the component's own KDoc (line 66) claims exactly that block. The
implementation never renders it. In practice the primary UAT path — attach a receipt to a new entry
— starts from a small unlabeled square in a horizontal row rather than the large visible drop zone
the design specifies.

**Fix:** branch on the empty interactive case before the `LazyRow`:

```kotlin
if (!readOnly && photoPaths.isEmpty()) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Mist)
            .clickable(enabled = enabled && !importing) { picker?.launch() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.action_add_photo),
            modifier = Modifier.size(24.dp), tint = Slate)
    }
} else {
    LazyRow { /* thumbnails; add tile only while photoPaths.size in 1..4 */ }
}
```

Render "Add photo" as the visible label if the spec's text affordance is required alongside the
block.

## Info

### IN-01: Entry-detail loading indicator has no content description

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt:156-163` (unused import at `:75`)

**Issue:** UI-SPEC's "Entry detail — Loading" state requires `contentDescription = "Loading this
service entry"`; the screen renders a bare `CircularProgressIndicator()`. The string exists
(`strings.xml:111`), is imported at line 75, and is never used — the semantics call was simply
forgotten. `ServiceHistoryScreen.kt:98-105` does it correctly for the history screen, so the two
loading surfaces are inconsistent.

**Fix:**

```kotlin
val loadingLabel = stringResource(Res.string.loading_service_entry)
Box(...) {
    CircularProgressIndicator(modifier = Modifier.semantics { contentDescription = loadingLabel })
}
```

### IN-02: Unused imports and two same-package imports

**Severity:** Info
**File:** see list below

**Issue:** Dead imports across the new files (verified against code, not KDoc mentions):

- `ServiceEntryEditScreen.kt:40-41` — `FocusRequester`, `focusRequester` (autofocus was correctly
  dropped; the imports were not); `:49` — same-package `ServiceEntryFormValidation`.
- `ServiceEntryEditViewModel.kt:8` — same-package `ServiceEntryFormValidation`.
- `ServiceEntryDetailScreen.kt:3, 41, 42, 45, 51, 53, 75` — `background`, `contentDescription`,
  `semantics`, `sp`, `Hairline`, `Mist`, `loading_service_entry`.
- `ServicePhotoStrip.kt:7, 16, 24, 26, 28-31, 36, 39, 41, 44` — `Row`, `rememberScrollState`,
  `OutlinedButton`, `TextButton`, `getValue`, `mutableStateOf`, `remember`, `setValue`, `Color`,
  `semantics`, `sp`, `Ink` (no local state exists in this file).
- `PartsEditor.kt:8, 13, 15, 23-26, 31, 33, 34, 36, 37` — `padding`, `Add`, `ButtonDefaults`,
  `getValue`, `mutableStateOf`, `remember`, `setValue`, `sp`, `ServiceEntryFormValidation`,
  `Hairline`, `Mist`, `Paper`.
- `ServiceTypePicker.kt:24, 26, 28, 29, 30` — `sp`, `Hairline`, `Mist`, `Paper`, `Slate`.
- `ServiceDateField.kt:5, 12, 18, 30, 32, 33, 35, 36, 43` — `Column`, `AlertDialog`,
  `MaterialTheme`, `KeyboardType`, `sp`, `Hairline`, `Mist`, `Paper`, `cd_back`.
- `ServiceTypeDialog.kt:22` — `sp`.

**Fix:** remove them; enable an unused-import/ktlint check in CI so this class of drift is caught
mechanically.

### IN-03: The photo strip's `items` lacks a key and re-derives the index with `indexOf`

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt:110-112`

**Issue:** `items(photoPaths) { path -> val index = photoPaths.indexOf(path) … }`. Without a key,
reordering/replacing the strip's backing list can reuse the wrong item state (the history list
already follows the "keys are mandatory" rule at `ServiceHistoryScreen.kt:141`). `indexOf` is also
wrong for duplicate paths: both items would report the first index, so removing the second removes
the first. Duplicates are unlikely today but the bookkeeping in CR-01 shows how quickly that
invariant can be broken.

**Fix:**

```kotlin
itemsIndexed(photoPaths, key = { _, path -> path }) { index, path ->
    ...
}
```

### IN-04: The date picker bypasses the `ServiceDates` storage seam

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceDateField.kt:141`

**Issue:** `onDateSelected(pickerState.selectedDateMillis!!)` forwards M3's raw millis, while
`ServiceDates`'s KDoc declares that nothing else constructs a storage timestamp and
`ServiceDates.toStorage` is the single conversion site. The encodings coincide today (M3 returns UTC
start-of-day), so behavior is correct, but a change to either side silently desynchronizes the
stored date.

**Fix:** normalize through the seam before emitting:

```kotlin
pickerState.selectedDateMillis?.let { onDateSelected(ServiceDates.toStorage(ServiceDates.fromStorage(it))) }
```

### IN-05: `observeHistory` returns entries whose children are silently empty

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt:38-46` (mapper defaults at `ServiceEntryEntityMapper.kt:20-30`)

**Issue:** `observeHistory` maps rows with `ServiceEntryEntity.toDomain()`, which leaves
`parts = emptyList()` and `photoPaths = emptyList()` via the domain defaults. The history row reads
neither, so there is no visible bug today; but the repository's contract says `ServiceEntry` is "the
only shape the UI layer ever sees", and an empty list is indistinguishable from "this entry has no
parts/photos". A future consumer (count badge, preview strip) would silently render nothing.

**Fix:** document the partial shape on `observeHistory`/`ServiceEntry` (e.g. "children are not
loaded in history rows"), or introduce a dedicated list-projection type so the absence is typed
rather than implied.

### IN-06: Selected chips use Material's off-palette default colours

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt:143-175`

**Issue:** `renderChip` passes no `colors`, so the selected `FilterChip` renders M3's default
`secondaryContainer` tint (off-palette lavender) and unselected chips use the default surface
variant. UI-SPEC §Color item 10 and C-15 require the selected chip's border + label to be `Ink` and
unselected chips to be `Mist`/`Slate`/`Hairline` — the spec explicitly notes that M3's default
selected container is off-palette and must be overridden "exactly as `VehicleTypePicker` does".
`HistorySortControl.kt:29-42` does precisely that for its segments, so the chip group is the
outlier; the unused `Hairline`/`Mist`/`Paper`/`Slate` imports (IN-02) are the leftover evidence
that the palette parameters were planned and dropped.

**Fix:**

```kotlin
colors = FilterChipDefaults.filterChipColors(
    containerColor = Mist,
    labelColor = Slate,
    selectedContainerColor = Paper,
    selectedLabelColor = Ink,
),
border = FilterChipDefaults.filterChipBorder(
    enabled = enabled,
    selected = isSelected,
    borderColor = Hairline,
    selectedBorderColor = Ink,
),
```

### IN-07: The required marker is missing on the service date field

**Severity:** Info
**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceDateField.kt:75-92` (caller comment at `ServiceEntryEditScreen.kt:248`)

**Issue:** UI-SPEC's form table marks Service date as required (`*`), and the edit screen's comment
even says `// 1. Service date (required *)` — but `ServiceDateField` renders only
`field_service_date` in its label. Mileage and service type both render `field_required_marker`. The
field reads optional while `isSubmittable` requires a non-null date.

**Fix:** build the label with the marker, mirroring the mileage/type fields:

```kotlin
label = {
    Text(buildAnnotatedString {
        append(stringResource(Res.string.field_service_date))
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
            append(stringResource(Res.string.field_required_marker))
        }
    })
}
```

---

_Reviewed: 2026-10-20T06:22:47Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
