---
phase: 02-service-log
verified: 2026-10-20T06:52:00Z
status: human_needed
score: 39/39 must-haves verified
overrides_applied: 0
requirements_verified: 7/7
requirements:
  SVC-01: satisfied
  SVC-02: satisfied
  SVC-03: satisfied
  SVC-04: satisfied
  SVC-05: satisfied
  SVC-06: satisfied
  SVC-07: satisfied
human_verification:
  - test: "Time the 30-second path on both platforms: open a vehicle's history, tap Add service, accept today's date, type a mileage, tap a preset type chip, optionally add cost/notes, tap Save. Repeat once using Add custom… for the type."
    expected: "The entry is saved and the history shows it immediately; the whole interaction stays under 30 seconds on a normal device; a custom type is stored verbatim and appears as a learned chip on the next add."
    why_human: "Wall-clock timing, keyboard/date-picker feel and chip tap targets are runtime UX properties."
  - test: "Add two parts (name + quantity), a workshop and a mechanic to an entry, save, then open its detail; edit the entry and confirm the values are prefilled."
    expected: "Parts render as 'name × qty' in the locked detail order between Cost and Workshop; Workshop and Mechanic render as label/value rows; quantities are digit-only and default to 1; values survive edit round-trip."
    why_human: "Navigation and form-state round-trip behaviour; the repository paths are covered by tests, the user flow is not."
  - test: "On a real device, attach two photos from the gallery (confirm no camera option), remove one before saving, pick another, save, force-quit and relaunch; then edit the entry, remove a saved photo and save."
    expected: "Picks are additive up to 5 with thumbnails; each photo renders from app-private storage after restart; removing a session photo deletes it immediately, removing a saved photo deletes its file only after the save succeeds; abandoning a form with an unsaved picked photo leaves no orphan file."
    why_human: "System photo picker, Coil rendering and on-disk file lifecycle require a device; the ordering guarantees are proven by tests but the picker flow is not."
  - test: "Open an entry, tap Edit, change date/mileage/type/cost/notes, then set the mileage lower than the previous entry's mileage and save."
    expected: "The form is prefilled exactly once (typing is never overwritten); the lower-mileage warning appears verbatim in the mileage supporting slot and Save stays enabled; after save the detail and the history both reflect the new values without a manual refresh."
    why_human: "Live form behaviour, warning placement and cross-screen streaming refresh."
  - test: "Open a vehicle's Service history and exercise both orders: default opening, tap Mileage, tap Date, leave and re-enter; also open from zero entries."
    expected: "Opens newest-date-first; Mileage re-sorts instantly to highest-mileage-first; the control resets to Date on every visit and the choice is never persisted; the Add service FAB is present in Loading, Empty, Content and Error; tapping a row opens that entry; there is no swipe or long-press action."
    why_human: "Interactive sort state, FAB overlay across states and gesture absence."
  - test: "Delete one entry, and separately delete a vehicle that has entries: observe the dialog copy, cancel once, then confirm."
    expected: "Cancel removes nothing and leaves the screen intact; confirming the entry delete returns to history with a 'Service entry deleted.' snackbar and the entry's photos are gone from storage; deleting a vehicle removes all of its entries, their part/photo rows and every entry photo file, followed by the 'Vehicle deleted.' snackbar."
    why_human: "Dialog UX, snackbar timing, back-stack behaviour and real file removal."
  - test: "On the Medium_Phone AVD, install the current Phase 1 build, add a vehicle, then install this build in place (do not uninstall). On iOS use a fresh install. Add an entry, force-quit, relaunch."
    expected: "The v1→v2 migration creates service_entry/service_part/service_photo and all four indices without losing the vehicle; a fresh install creates the same schema; the entry and its photos survive the restart on both platforms."
    why_human: "The in-place upgrade check was explicitly deferred by plan 02-01 (CI has no emulator job); process-restart persistence is a runtime property."
  - test: "Visual and accessibility pass against UI-SPEC with TalkBack (Android) and VoiceOver (iOS): form labels/required markers, selected type-chip palette, entry-detail loading announcement, date-field label/action, photo remove labels, FAB and summary-row copy."
    expected: "Required fields read as required (Service date included), selected chips use the Mist/Paper/Ink treatment rather than M3's default lavender, the entry-detail loading state announces 'Loading this service entry', every icon-only control has a spoken label, and spacing/copy match the UI-SPEC contract."
    why_human: "Visual palette, screen-reader announcements and geometry cannot be verified statically; three open info-level review findings (IN-01, IN-06, IN-07) are specifically in scope for this check."
---

# Phase 2: Service Log Verification Report

**Phase Goal:** Users can log, view, and manage the complete history of service events for any vehicle.
**Mode:** mvp (ROADMAP) — the MVP user story used below is the sanctioned restatement carried verbatim in the 02-02 and 02-06 plans: *"As a vehicle owner, I want to log every service event — date, mileage, type, cost, notes, parts, workshop, and photos — and browse, edit, or delete any vehicle's full history, so that I have a trustworthy record of all maintenance performed."*
**Verified:** 2026-10-20T06:52:00Z — **initial verification** (no previous `02-VERIFICATION.md` exists)
**Status:** human_needed
**Score:** 39/39 must-haves verified

> **Goal-form discrepancy (MVP mode):** the ROADMAP `Goal:` line is outcome-form, not user-story form, and `gsd-tools` is not installed on this machine, so `user-story.validate` could not be run. Both 02-02 and 02-06 carry the same "Roadmap fidelity note": the story faithfully restates ROADMAP SC 1–5 and REQUIREMENTS SVC-01…SVC-07, with no invented scope. This follows the same documented precedent as `01-VERIFICATION.md`. If the canonical story should live in ROADMAP.md, run `/gsd-mvp-phase 2`.

> **Method note:** SUMMARY claims were not used as evidence. Every truth below was checked against the working tree at HEAD `c8f9cc2` (the only commit after the last source change is docs-only). The current test-result artifacts (13:40:39–13:40:49 +07:00) postdate every source file mtime (newest source checks return empty for files newer than the run): iOS **96 tests / 0 failures / 0 errors**, Android host **57 / 0 / 0**. The SQLDelight migration guard was re-run in this session (`BUILD SUCCESSFUL`, task UP-TO-DATE, exit 0). No source file was modified.

## User Flow Coverage (MVP)

| # | User-story step | Expected | Evidence in codebase | Status |
|---|-----------------|----------|----------------------|--------|
| 1 | Open a vehicle and reach its service history | Summary row opens the history; empty state at zero entries, otherwise newest-date-first list | `VehicleDetailScreen.kt:321-323` (`ServiceSummaryRow` → `onOpenServiceHistory`); `App.kt:130` → `ServiceHistoryRoute`; `ServiceHistoryScreen.kt` four-state `when`; `EmptyState` | Code-complete — runtime check H-5 |
| 2 | Log a service event in the 30-second path | Today's date pre-set, mileage pre-filled, one-tap type chip, cost/notes optional, Save last | `ServiceEntryEditViewModel.kt:83-96,130-155`; `ServiceEntryEditScreen.kt:263-461`; `ServiceEntryFormValidation.kt:63-65` | Code-complete — runtime check H-1 |
| 3 | Add parts and workshop/mechanic | Inline parts rows (name + quantity, default 1); workshop/mechanic fields; detail renders them in the locked order | `PartsEditor.kt`; `ServiceEntryEditScreen.kt` workshop/mechanic fields; `ServiceEntryRepositoryImpl.kt:103-150`; `ServiceEntryDetailScreen.kt:253-300` | Code-complete — runtime check H-2 |
| 4 | Attach one or more receipt photos | Additive gallery picks up to 5; previews; app-owned copies; files die only per the two-set rule | `ServicePhotoStrip.kt:110-190`; `ServiceEntryEditViewModel.kt:270-330,375,392`; `PhotoStorage.kt:33,60`; `ServiceEntryDeletionTest` | Code-complete — runtime check H-3 |
| 5 | Edit any existing entry | Same form prefilled once; save updates the row and fully replaces children; detail/history stream the change | `ServiceEntryEditViewModel.kt:161-199,236-275`; `ServiceEntryRepositoryImpl.kt:116-138`; `App.kt:143-151` | Code-complete — runtime check H-4 |
| 6 | Delete any existing entry (and a vehicle with its log) | Dialog is the only delete path; confirm removes children then files; snackbar; vehicle delete cascades entries, rows and files | `ServiceEntryDetailScreen.kt:190-210`; `ServiceEntryDetailViewModel.kt:95-120`; `VehicleRepositoryImpl.kt:77-88`; `ServiceEntryDeletionTest` 8/8 | Code-complete — runtime check H-6 |
| 7 | Outcome: a trustworthy offline record | All data local; no network; schema survives restart and v1→v2 upgrade | No network dependencies; SQLite + private files; migration guard green; repository tests on a real driver | Code-complete — runtime check H-7 |

## Goal Achievement

### Observable Truths

| # | Truth | Source | Status | Evidence |
|---|-------|--------|--------|----------|
| 1 | User can create a service entry with date, mileage, service type (preset or custom), cost and notes in under 30 seconds (ROADMAP SC1) | ROADMAP SC1 | ✓ VERIFIED | `ServiceEntryEditScreen.kt:263-461` field order Date → Mileage → Type → Cost → Notes → Workshop/Mechanic → Parts → Photos → Save; `isSubmittable` gates on date+mileage+type; presets `ServiceTypePresets.ALL`; persistence via `repo.save`; timing only is human (H-1) |
| 2 | User can add parts replaced/repaired (name, quantity) and workshop/mechanic details (SC2) | ROADMAP SC2 | ✓ VERIFIED | `PartsEditor.kt` rows default quantity "1", add/remove; workshop/mechanic fields + detail rows; `RepositoryTest.saveAddsAnEntryAndWritesChildrenWithSequentialPositions` (8/8 suite) |
| 3 | User can attach one or more photos to a service entry (SC3) | ROADMAP SC3 | ✓ VERIFIED | `ServicePhotoStrip` additive up to 5, gallery-only via `FileKitType.Image`; `PhotoStorage.SERVICE_ENTRY_DIRECTORY`; `service_photo` rows written in the save transaction; deletion tests prove file lifecycle |
| 4 | User can view the full service history for a vehicle sorted by date or mileage (SC4) | ROADMAP SC4 | ✓ VERIFIED | `HistorySortControl` (Date | Mileage, both descending); `ServiceHistoryViewModel` `flatMapLatest`; `.sq` `selectForVehicleByDate/Mileage`; tests `changingSortReQueriesWithTheNewOrder`, `historyOrdersAreDeterministicIncludingTheIdDescendingTieBreak` |
| 5 | User can edit or delete any existing service entry (SC5) | ROADMAP SC5 | ✓ VERIFIED | Edit route `entryId > 0` loads once and updates; delete is dialog-gated; `ServiceEntryDeletionTest` 8/8 incl. single-flight, failure-keeps-entry and vehicle cascade |
| 6 | A fresh install (v2) and a v1 upgrade both produce the three service tables, and the CI migration guard stays green | 02-01 | ✓ VERIFIED | Committed `2.db` contains `service_entry`, `service_part`, `service_photo`, `vehicle` + 4 indices (sqlite3); `1.sqm` CREATE TABLE/INDEX statements identical to `ServiceEntry.sq`; `./gradlew verifyCommonMainVehiCareDatabaseMigration` → BUILD SUCCESSFUL exit 0; CI runs the same task (`.github/workflows/ci.yml:32`) |
| 7 | Saving an entry with parts and photos and reading it back is lossless and atomic | 02-01 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.save` wraps entry + children in `transactionWithResult` (lines 103-150); repo test asserts parts/photos and positions round-trip; no partial-child path exists outside the transaction |
| 8 | Editing an entry fully replaces its part and photo rows (no orphan children); both history orders are deterministic | 02-01 | ✓ VERIFIED | Lines 131-132 delete children inside the update transaction; `.sq` orders include `id DESC` tie-break; tests `saveWithAnExistingIdReplacesChildrenAndKeepsTheId`, `historyOrders…` |
| 9 | Deleting an entry removes child rows in the transaction and yields photo paths for file deletion only after rows are durably gone | 02-01 | ✓ VERIFIED | Lines 155-165: paths collected inside `transactionWithResult`, `photoDeleter` called after it returns; test `deleteRemovesEntryAndChildrenAndHandsEveryPhotoPathToTheDeleter` |
| 10 | Learned service types are cross-vehicle, newest-used first, with preset matches collapsed case-insensitively | 02-01 | ✓ VERIFIED | `selectLearnedTypes` (`MAX(id) GROUP BY lower(service_type)`, `ORDER BY id DESC`); `observeLearnedTypes` maps through `ServiceTypePresets.filterLearned`; test `learnedTypesAreCrossVehicleNewestFirstAndPresetCaseDuplicatesCollapse` |
| 11 | The build resolves kotlinx-datetime 0.8.0 (plain, not compat) from the single catalog | 02-02 | ✓ VERIFIED | `gradle/libs.versions.toml:20,56` one pin; `shared/build.gradle.kts:69` `libs.kotlinx.datetime` |
| 12 | A civil date round-trips: today from the system zone, storage UTC start-of-day, display the same civil date | 02-02 | ✓ VERIFIED | `ServiceDates.kt:35-51` (`today`, `toStorage` = `atStartOfDayIn(TimeZone.UTC)`, `fromStorage`); `ServiceDatesTest` 4/4 on both targets |
| 13 | The display formats are `19 Oct 2026`, `123.456 km`, `Rp 450.000` | 02-02 | ✓ VERIFIED | `ServiceDates.display`; `RupiahFormat.formatMileage/formatRupiah`; `ServiceDatesTest` 4/4 and `RupiahFormatTest` 5/5 |
| 14 | Every Phase 2 string exists in the centralized resource set and resolves through `Res.string`/`Res.plurals` — no inline UI literal | 02-02 | ✓ VERIFIED | `strings.xml` carries the full Phase 2 set (incl. plurals `service_summary_entries`); service screens/components use 17/20/9/… `Res.*` references; the only interpolated literals are data joins (`name × qty`, `label: value`), not copy |
| 15 | Add form opens with today's date, mileage pre-filled from the last entry; submit enables only when date + mileage + type are present | 02-03 | ✓ VERIFIED | `ServiceEntryEditViewModel.kt:83-96` synchronous date seed; `130-155` async prefill merges only into blank fields; `isSubmittable` + button `enabled` at screen line 463 |
| 16 | One tap selects a type chip; Add custom… opens a one-field dialog; a case-insensitive preset match selects the existing chip without rewriting storage | 02-03 | ✓ VERIFIED | `ServiceTypePicker.kt` FlowRow chips; `ServiceTypeDialog.kt` one field + 60-char cap; `ServiceEntryEditViewModel.onCustomTypeConfirmed:210-230` preset → learned → new |
| 17 | Parts and up to 5 gallery-picked photos are edited inline on one scrolling screen, with submit last | 02-03 | ✓ VERIFIED | `ServiceEntryEditScreen.kt:406-470` PartsEditor → PhotoStrip → Button; `ServicePhotoStrip.kt:174` add control only below 5 and no picker beyond; single `Column` scroll container |
| 18 | A mileage lower than the baseline shows the verbatim warning in the supporting slot and never blocks save | 02-03 | ✓ VERIFIED | Screen lines 274, 312-321 (`warn_mileage_lower`); `isSubmittable` ignores baseline; warning branch has no effect on `enabled` |
| 19 | Save writes entry + parts + photos in one transaction; edit mode loads the existing entry once and updates it | 02-03 | ✓ VERIFIED | ViewModel `initialLoadApplied` guard (161-199); screen save → ViewModel `save` (236-275); repository transaction (103-150) |
| 20 | Photo files follow the two-set rule: session-owned die on abandon/removal, row-owned die only after a successful save | 02-03 | ✓ VERIFIED | `ServiceEntryEditViewModel.kt:127-129` sets; `onPhotoRemoved:310-330`; `markPhotosSaved:392`; `onCleared:375` deletes only `persistedButUnsavedPaths`; WR-05 hold on submitted photos (248-262) |
| 21 | The Koin graph binds `single<ServiceEntryRepository>` with `PhotoStorage::delete` before any service ViewModel resolves | 02-03 | ✓ VERIFIED | `AppModule.kt:47-49`; ViewModel factories 58-64; repository is the only binding, impl default `{}` is test-only |
| 22 | Both forms read identically: vehicle error line and service supporting lines use the declared `Label` role | 02-03 | ✓ VERIFIED | `VehicleEditScreen.kt:246-248` `labelLarge`; `ServiceEntryEditScreen.kt:306,316,326` `labelLarge`; `Type.kt:45-46` declares Label = `labelLarge` |
| 23 | The destructive action opens a confirmation dialog; nothing is removed before confirm, and the dialog confirm is the only path to `delete` | 02-04 | ✓ VERIFIED | `ServiceEntryDetailScreen.kt:190-210`; `ConfirmDeleteDialog` `onDismissRequest = onDismiss`; `viewModel.delete` appears only inside `onConfirm`; destructive button disabled while deleting |
| 24 | Entry detail paints every state: centred indicator while loading, not-available copy with a way back, locked read-only order when present | 02-04 | ✓ VERIFIED | `ServiceEntryDetailScreen.kt:155-231` (Loading / NotFound / Content); content order Cost → Parts → Workshop → Mechanic → Notes → Photos → Delete (235-300) |
| 25 | Deleting an entry removes children in one transaction and files only after rows are durable; failure leaves the entry stored and on screen with a message | 02-04 | ✓ VERIFIED | Repo 155-165; ViewModel 106-120 catches and sets `deleteFailed` without clearing state; screen `LaunchedEffect` shows `error_delete_entry`; tests `aFailedDeleteKeepsTheEntryAndReportsFailure`, `deleteDropsSecondCallWhileFirstInFlight` |
| 26 | Deleting a vehicle removes every service entry, their part/photo rows and every entry photo file in the same transaction — no orphan rows or files | 02-04 | ✓ VERIFIED | `VehicleRepositoryImpl.kt:77-88`: collect paths → `deleteEntriesForVehicle` → child deletes → vehicle delete → file deleters after commit; test `vehicleCascadeRemovesAllEntriesAndTheirPhotosAndVehiclePhoto` |
| 27 | One dialog implementation serves both entities; vehicle keeps its exact copy; entry passes entry copy; Cancel stays `Ink` | 02-04 | ✓ VERIFIED | Shared `ui/components/ConfirmDeleteDialog.kt` used by both detail screens; parameterized title/message/confirmLabel; Cancel `TextButton` `contentColor = Ink` (line 94) |
| 28 | One label/value row serves both Detail screens: `FieldRow` lives in `ui/components/`, the vehicle Detail imports it, the entry Detail renders it | 02-04 | ✓ VERIFIED | `ui/components/FieldRow.kt`; `VehicleDetailScreen.kt:369-398` and `ServiceEntryDetailScreen.kt:253-268` both call it; no duplicate implementation remains |
| 29 | History opens newest-date-first; one tap on Mileage re-sorts instantly; the choice defaults to Date on every visit and is never persisted | 02-05 | ✓ VERIFIED | `ServiceHistoryViewModel.kt:48` `MutableStateFlow(HistorySort.DATE)`; `observeHistoryUiState` `flatMapLatest` over sort (96-104); `HistorySort` has no persistence seam; test `changingSortReQueriesWithTheNewOrder` |
| 30 | Every history state paints something: labelled loading, shared empty state with working CTA, sort control + keyed rows, load-error copy with Try again | 02-05 | ✓ VERIFIED | `ServiceHistoryScreen.kt:97-175`; loading label `loading_service_history`; `EmptyState` with `onAddService`; `error_load_history` + `viewModel::retry`; test `retryAfterFailureRestartsTheObservation` |
| 31 | The Add service FAB is present in every state and opens the add form; the empty-state CTA does the same | 02-05 | ✓ VERIFIED | `ServiceHistoryScreen.kt:176-190` FAB after the `when`; `App.kt:170-182` both callbacks navigate to `ServiceEntryEditRoute(vehicleId, 0L)` |
| 32 | Tapping a row opens that entry's detail; there is no long-press, swipe or other gesture on the list | 02-05 | ✓ VERIFIED | `ServiceHistoryRow.kt:43-50` single `Card(onClick)`; no `combinedClickable`/swipe modifiers in the history tree |
| 33 | Rows are bound to their entries by stable keys | 02-05 | ✓ VERIFIED | `ServiceHistoryScreen.kt:141` `items(state.entries, key = { entry -> entry.id })` |
| 34 | The empty state is one shared component; vehicle list and history pass their own icon/copy; the vehicle list rendering is unchanged | 02-05 | ✓ VERIFIED | `ui/components/EmptyState.kt`; `VehicleListScreen.kt:92-97` passes the same vehicle icon/heading/body/action as before; history passes `Icons.Filled.Build` + service copy |
| 35 | The vehicle Detail slot is now a tappable Service history row reading `N entries · Last service 19 Oct 2026`, or `No entries yet` at zero | 02-06 | ✓ VERIFIED | `ServiceSummaryRow.kt:100-114`; `service_summary_none` line 114; plural `service_summary_entries` + `service_summary_last` (strings 113-118); `ServiceDates.display` |
| 36 | The row is navigation only: it opens history, never the add form, never lists entries, and stays tappable at zero entries | 02-06 | ✓ VERIFIED | `VehicleDetailScreen.kt:321-323` `onClick = onOpenServiceHistory`; `App.kt:130` navigates to `ServiceHistoryRoute`; no add callback or list inside the row; `HISTORY` route handles zero entries |
| 37 | While the aggregate resolves, the row renders its title alone at a stable 72dp minimum height — never a spinner | 02-06 | ✓ VERIFIED | `ServiceSummaryRow.kt:62` `heightIn(min = 72.dp)`; meta text only `if (summary != null)` (line 77); `ServiceSummaryViewModel` starts `null` and maps failure to `null` |
| 38 | The four spaces of the retired placeholder are exactly where the row sits: the 48px breaks above and below are unchanged | 02-06 | ✓ VERIFIED | `VehicleDetailScreen.kt:317-326` `Spacer(48.dp)` → `ServiceSummaryRow` → `Spacer(48.dp)` |
| 39 | The coming-soon placeholder and its string are gone, and nothing references either | 02-06 | ✓ VERIFIED | `ComingSoonSlot.kt` absent; repo-wide grep for `ComingSoon`/`coming_soon` outside planning docs returns nothing; `detail_service_history` is present at strings line 77 |

**Score:** 39/39 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq` | 3 tables, 4 indices, all named queries incl. `selectLearnedTypes` | ✓ VERIFIED | All present; queries used by the repository |
| `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm` | v1→v2 delta with DDL identical to `ServiceEntry.sq` | ✓ VERIFIED | CREATE TABLE/INDEX statements identical; guard task exit 0 |
| `shared/src/commonMain/sqldelight/databases/2.db` | Regenerated committed v2 baseline, `1.db` retained | ✓ VERIFIED | Both tracked; sqlite3 shows 4 tables + 4 indices |
| `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt` | Transactional save, delete ordering, orders, learned types, summary, mileage | ✓ VERIFIED | 167 lines; `limitedParallelism(1)`; `transactionWithResult`; `photoDeleter` after commit |
| `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceTypePresets.kt` | Six locked presets + case-insensitive learned filter | ✓ VERIFIED | `ALL` + `filterLearned` |
| `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt` | Real-driver proof of add/edit/orders/summary/learned/delete | ✓ VERIFIED | 8 tests, all pass on iOS simulator |
| `shared/src/commonMain/kotlin/com/vehicare/util/ServiceDates.kt` | Single date seam: today/toStorage/fromStorage/display | ✓ VERIFIED | UTC start-of-day storage; system-zone today |
| `shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt` | Grouping + rupiah + mileage + digit parse | ✓ VERIFIED | Dot-grouped output |
| `shared/src/commonMain/composeResources/values/strings.xml` | Complete Phase 2 string set; coming-soon key absent | ✓ VERIFIED | Includes plurals and all screen/component keys |
| `gradle/libs.versions.toml` | kotlinx-datetime 0.8.0 pinned once | ✓ VERIFIED | Lines 20, 56 |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt` | Add prefill, edit load-once, saving/importing flags, two-set photos, failure flags | ✓ VERIFIED | Init blocks + two-path save + `onCleared` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt` | `isSubmittable`, sanitizers, `MAX_*`, `toServiceEntry` | ✓ VERIFIED | Pure object; 10/10 tests |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt` | Single scrolling form in locked field order | ✓ VERIFIED | `imePadding`; submit last |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt` | 80dp strip, add/remove, decorative images, read-only mode | ✓ VERIFIED | 16:9 empty block; 48dp remove target; max 5 |
| `shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt` | Directory parameter + service-entry directory | ✓ VERIFIED | `SERVICE_ENTRY_DIRECTORY = "service_entries"` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/components/ConfirmDeleteDialog.kt` | Shared parameterized delete gate | ✓ VERIFIED | Used by both detail screens; Cancel Ink |
| `shared/src/commonMain/kotlin/com/vehicare/ui/components/FieldRow.kt` | Shared label/value row | ✓ VERIFIED | Used by both detail screens |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailViewModel.kt` | Streaming detail + single-flight delete + one-shot failure | ✓ VERIFIED | `observeEntry`; `delete` guard |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt` | Locked read-only anatomy + `serviceMetaLine` | ✓ VERIFIED | All states; dialog-only delete |
| `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` | D-39 cascade: children → vehicle → files | ✓ VERIFIED | `deleteEntriesForVehicle` in transaction |
| `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt` | Real-driver delete state machine + cascade | ✓ VERIFIED | 8 tests, all pass |
| `shared/src/commonMain/kotlin/com/vehicare/ui/components/EmptyState.kt` | Shared parameterized empty state | ✓ VERIFIED | Vehicle list + history use it |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt` | Streaming history + sort + retry | ✓ VERIFIED | `flatMapLatest`; retry inside inner flow |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryScreen.kt` | Sort control + keyed list + FAB + full state matrix | ✓ VERIFIED | Four states; FAB outside `when` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt` | Type title, date·mileage meta, trailing cost | ✓ VERIFIED | Uses shared `serviceMetaLine` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/HistorySortControl.kt` | Two-segment Date | Mileage, descending | ✓ VERIFIED | `SingleChoiceSegmentedButtonRow` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt` | Tappable summary, title + aggregate, chevron, 72dp | ✓ VERIFIED | `heightIn(min = 72.dp)`; null = title-only |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceSummaryViewModel.kt` | One aggregate read, null while unresolved | ✓ VERIFIED | `stateIn(initialValue = null)`; `catch { emit(null) }` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` | Summary row at placeholder position + history callback | ✓ VERIFIED | 48dp breaks preserved |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ServiceEntryRepositoryImpl.kt` | `ServiceEntry.sq` | generated `serviceEntryQueries.*` | ✓ WIRED | All reads/writes bind through generated queries |
| `1.sqm` | `2.db` | schema generation + committed baseline | ✓ WIRED | Guard task exit 0; baseline contains v2 schema |
| `ServiceEntryRepositoryImpl.kt` | `PhotoStorage.delete` | injected `photoDeleter` after commit | ✓ WIRED | `AppModule.kt:48` passes `PhotoStorage::delete` |
| `ServiceDates.kt` | kotlinx-datetime | `LocalDate`/`TimeZone`/`atStartOfDayIn` imports | ✓ WIRED | Lines 4-5, 35-51 |
| `shared/build.gradle.kts` | `libs.versions.toml` | `libs.kotlinx.datetime` | ✓ WIRED | Line 69 |
| `ServiceEntryEditScreen.kt` | `ServiceEntryEditViewModel.kt` | `koinViewModel(parametersOf(vehicleId, entryId))` | ✓ WIRED | Line 103 |
| `ServiceEntryEditViewModel.kt` | `PhotoStorage.persist` | `SERVICE_ENTRY_DIRECTORY` | ✓ WIRED | Line 286 |
| `ServiceEntryEditViewModel.kt` | `ServiceEntryRepository.save` | `viewModelScope` + `repo.save` | ✓ WIRED | Line 253; CancellationException rethrow |
| `App.kt` | `ServiceEntryEditRoute` | `composable<ServiceEntryEditRoute>` + `toRoute` | ✓ WIRED | Lines 143-151 |
| `AppModule.kt` | `ServiceEntryRepositoryImpl.kt` | `single<ServiceEntryRepository>` | ✓ WIRED | Lines 47-49 |
| `ServiceEntryDetailScreen.kt` | `ConfirmDeleteDialog.kt` | only delete call site | ✓ WIRED | Lines 197-210 |
| `ServiceEntryDetailScreen.kt` | `ServiceEntryDetailViewModel.kt` | `koinViewModel(parametersOf(entryId))` | ✓ WIRED | Line 97 |
| `VehicleRepositoryImpl.kt` | `ServiceEntry.sq` | cascade statements in transaction | ✓ WIRED | Lines 77-88 |
| `ServiceEntryDetailScreen.kt` | `ui/components/FieldRow.kt` | shared import + render | ✓ WIRED | Lines 253-268 |
| `App.kt` | `ServiceEntryDetailRoute` | `composable<...>` + snackbar | ✓ WIRED | Lines 152-169 |
| `ServiceHistoryViewModel.kt` | `ServiceEntryRepository.observeHistory` | `flatMapLatest` over sort | ✓ WIRED | Lines 96-104 |
| `ServiceHistoryRow.kt` | `ServiceEntryDetailScreen.kt` | shared `serviceMetaLine` | ✓ WIRED | Import + line 72 |
| `ServiceHistoryScreen.kt` | `ui/components/EmptyState.kt` | empty branch | ✓ WIRED | Lines 114-122 |
| `App.kt` | `ServiceHistoryRoute` | `composable<ServiceHistoryRoute>` | ✓ WIRED | Lines 170-182 |
| `VehicleDetailScreen.kt` | `ServiceSummaryRow.kt` | Content branch at former placeholder band | ✓ WIRED | Lines 321-323 |
| `App.kt` | `ServiceHistoryRoute` | `onOpenServiceHistory` | ✓ WIRED | Lines 130, 177 |
| `ServiceSummaryViewModel.kt` | `ServiceEntryRepository.observeSummary` | single aggregate StateFlow | ✓ WIRED | Lines 31-39 |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ServiceHistoryScreen` | `state.entries` | `observeHistoryUiState` → `repo.observeHistory` → `selectForVehicleByDate/Mileage` | Yes — real rows from SQLite, re-emitted on table change | ✓ FLOWING |
| `ServiceEntryDetailScreen` | `state` (Content) | `repo.observeEntry` → `selectById` + `selectParts` + `selectPhotos` composed in the mapper | Yes — full entry, parts and photo paths | ✓ FLOWING |
| `ServiceEntryEditScreen` | `values` | Add: sync date + `observeLatestMileage`/`observeLearnedTypes`; Edit: `observeEntry` + `toFormValues` + `observePreviousMileage` | Yes — prefill and edit values from the row store | ✓ FLOWING |
| `ServiceSummaryRow` | `summary` | `ServiceSummaryViewModel` → `repo.observeSummary` → `selectSummary` COUNT/MAX | Yes — aggregate from entries; `null` only while unresolved (title-only render) | ✓ FLOWING |
| `ServicePhotoStrip` | `photoPaths` | Edit form state / entry detail state → `PhotoStorage.resolve(path)` → Coil `AsyncImage` | Yes — app-private files, no placeholder fallback in the data path | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| iOS shared test suite (current tree) | aggregate `shared/build/test-results/iosSimulatorArm64Test/*.xml` after a run that postdates every source mtime | 96 tests, 0 failures, 0 errors | ✓ PASS |
| Android host test suite (current tree) | aggregate `shared/build/test-results/testAndroidHostTest/*.xml` after the same run | 57 tests, 0 failures, 0 errors | ✓ PASS |
| v1→v2 migration guard | `./gradlew verifyCommonMainVehiCareDatabaseMigration` | `BUILD SUCCESSFUL`, task UP-TO-DATE, `EXIT=0` | ✓ PASS |
| v2 baseline schema | `sqlite3 …/databases/2.db ".tables"` + `sqlite_master` indices | `service_entry`, `service_part`, `service_photo`, `vehicle`; 4 indices | ✓ PASS |
| Migration DDL parity | normalized `CREATE TABLE`/`CREATE INDEX` diff between `ServiceEntry.sq` and `1.sqm` | Identical statements | ✓ PASS |
| Android app builds | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` | 15,847,402 bytes, 13:40:39 (+07), same run as tests | ✓ PASS |
| Source freshness vs test evidence | `find shared/src -name '*.kt' -newermt '2026-10-20 13:40:49'` | Empty — no source newer than the recorded run | ✓ PASS |

### Probe Execution

No probes are declared in the Phase 2 plans and none exist under `scripts/*/tests/probe-*.sh`; the phase's runnable gate is the SQLDelight migration task, covered above.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| SVC-01 | 02-01, 02-03, 02-05 | Create an entry with date, mileage, type, cost, notes | ✓ SATISFIED | Form fields + `isSubmittable` + transactional save; truth 1/15; runtime H-1 |
| SVC-02 | 02-01, 02-03 | Add parts (name, quantity) | ✓ SATISFIED | `PartsEditor` + child rows in save transaction; repo test; runtime H-2 |
| SVC-03 | 02-01, 02-03 | Attach one or more photos | ✓ SATISFIED | Additive strip ≤5 + `PhotoStorage` + `service_photo` rows + file deletion tests; runtime H-3 |
| SVC-04 | 02-03 | Record workshop and mechanic | ✓ SATISFIED | Two form fields + two detail `FieldRow`s; runtime H-2 |
| SVC-05 | 02-01, 02-02, 02-05, 02-06 | View full history sorted by date or mileage | ✓ SATISFIED | Sort control + two deterministic orders + summary row + date formatting; runtime H-5 |
| SVC-06 | 02-01, 02-03, 02-04 | Edit an existing entry | ✓ SATISFIED | Load-once edit + update/replace children + detail Edit action; runtime H-4 |
| SVC-07 | 02-01, 02-04 | Delete a service entry | ✓ SATISFIED | Dialog gate + child/file ordering + single-flight + failure retention; 8/8 deletion tests; runtime H-6 |

**Orphaned requirements:** none. All seven SVC requirements mapped to Phase 2 are claimed by at least one plan and satisfied.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ServiceEntryDetailScreen.kt` | 162 | `CircularProgressIndicator()` has no `contentDescription`; `loading_service_entry` is imported but unused, though UI-SPEC specifies the label | ℹ️ Info (IN-01) | One transient state lacks screen-reader context; no must-have impact; covered by H-8 |
| `ServiceDateField.kt` | 84 | Date label omits the required `*` marker even though the date is required | ℹ️ Info (IN-07) | Field reads optional; no must-have impact; covered by H-8 |
| `ServiceTypePicker.kt` | 116-140 | `FilterChip` uses M3 default colors instead of the specified Mist/Paper/Ink treatment | ℹ️ Info (IN-06) | Selected chip is off-palette; no must-have impact; covered by H-8 |
| `ServicePhotoStrip.kt` | 122 | `items(photoPaths)` lacks a key and re-derives `index` with `indexOf` | ℹ️ Info (IN-03) | At most 5 paths; duplicates are not produced by the picker; no observed correctness impact |
| `ServiceDateField.kt` | 118-130 | Date picker passes Material millis directly instead of through the `ServiceDates` seam | ℹ️ Info (IN-04) | Material's value is already UTC start-of-day, consistent with `ServiceDates`; tests cover the seam |
| `02-REVIEW.md` (whole phase) | — | Review status `issues_found` with seven info-level findings (IN-01…IN-07) left open by agreement | ℹ️ Info | None falsifies a must-have; the user-visible three are in H-8 |

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` markers exist in any Phase 2 source file. No stub returns, empty handlers, console logging or placeholder copy were found. `01-VERIFICATION.md` (status `human_needed`) and the open findings in `01-REVIEW.md` are **inherited Phase 1 risk, not Phase 2 gaps** — Phase 1 UAT is recorded as 6/6 passed in `01-UAT.md`.

### Human Verification Required

#### 1. 30-second create path (H-1)

**Test:** On Android and iOS, open a vehicle's history, tap Add service, accept today's date, enter mileage, tap a preset type chip, optionally enter cost/notes, and save. Repeat once using Add custom… for the type.
**Expected:** The entry appears in history immediately; the interaction stays under 30 seconds; a custom type is stored verbatim and appears as a learned chip on the next add.
**Why human:** Wall-clock timing, keyboard/date-picker feel and chip tap targets are runtime UX properties.

#### 2. Parts and workshop/mechanic round-trip (H-2)

**Test:** Add two parts with quantities plus workshop and mechanic to an entry, save, open detail, then edit and confirm prefill.
**Expected:** Parts render as `name × qty` between Cost and Workshop; workshop/mechanic render as label/value rows; quantities are digit-only with default 1; values survive edit.
**Why human:** Form-state and navigation round-trip behaviour.

#### 3. Real-device photo lifecycle (H-3)

**Test:** Pick two gallery photos (confirm no camera), remove one before save, pick another, save, force-quit, relaunch; then edit and remove a saved photo.
**Expected:** Picks are additive up to 5; images render after restart; session-owned files die immediately on removal/abandon; row-owned files die only after a successful save.
**Why human:** System picker, Coil rendering and on-disk file lifecycle.

#### 4. Edit flow and lower-mileage warning (H-4)

**Test:** Edit an entry, change fields, set mileage below the previous entry's mileage, save.
**Expected:** The form is prefilled once; the verbatim warning appears in the mileage supporting slot and Save stays enabled; detail and history reflect the change without manual refresh.
**Why human:** Live form behaviour, warning placement and cross-screen streaming.

#### 5. History sort and navigation states (H-5)

**Test:** Open history at zero and with entries; tap Mileage, then Date; leave and re-enter; tap a row; inspect the FAB in every state.
**Expected:** Date is the default on every visit and is never persisted; Mileage re-sorts instantly; FAB is present in Loading, Empty, Content and Error; row tap opens detail; no swipe/long-press.
**Why human:** Interactive sort state and FAB overlay across states.

#### 6. Delete flows and cascade (H-6)

**Test:** Delete one entry (cancel once, then confirm); separately delete a vehicle that has entries.
**Expected:** Cancel removes nothing; entry delete returns to history with the snackbar and removes its photo files; vehicle delete removes all entries, child rows and entry photo files plus the vehicle photo, then shows the snackbar.
**Why human:** Dialog UX, snackbar timing, back-stack and real file removal.

#### 7. v1→v2 in-place upgrade and restart persistence (H-7)

**Test:** On the Medium_Phone AVD install the Phase 1 build, add a vehicle, install this build in place; on iOS do a fresh install. Add an entry and force-quit/relaunch.
**Expected:** Migration creates the three service tables and four indices without losing the vehicle; fresh install matches; entry and photos survive restart.
**Why human:** The manual upgrade check was explicitly deferred by plan 02-01 because CI has no emulator job; restart persistence is runtime.

#### 8. Visual and accessibility parity (H-8)

**Test:** UI-SPEC pass with TalkBack/VoiceOver: required markers (including Service date), selected chip palette, entry-detail loading announcement, date-field label/action, photo-remove labels, FAB and summary-row copy.
**Expected:** Service date reads as required, chips use Mist/Paper/Ink, the loading state announces “Loading this service entry”, every icon-only control has a spoken label, and copy/geometry match UI-SPEC.
**Why human:** Visual palette, screen-reader output and geometry cannot be verified statically; open info findings IN-01/IN-06/IN-07 are in scope.

### Gaps Summary

No must-have gap was found. All 39 truths (5 ROADMAP success criteria plus 34 plan must-have truths) are verified against the working tree, all 29 required artifacts exist with substantive implementations, all 22 key links are wired, and the data-flow trace shows real row-store data reaching every dynamic surface. The seven SVC requirements are satisfied with no orphaned IDs.

The phase cannot be marked `passed` because its remaining acceptance is runtime-only: the 30-second timing, the real photo picker and file lifecycle, the in-place v1→v2 upgrade deferred by plan 02-01, interactive sort/delete flows, and the visual/accessibility pass. Three open info-level review findings (IN-01 loading label, IN-06 chip palette, IN-07 date required marker) are UI-SPEC deviations that do not falsify a must-have; they are deliberately surfaced in H-8 for the UAT/UI-review pass rather than counted as gaps.

---

_Verified: 2026-10-20T06:52:00Z_
_Verifier: the agent (gsd-verifier)_
