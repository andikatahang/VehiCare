---
phase: 02-service-log
verified: 2026-10-21T10:57:46Z
status: human_needed
score: 52/52 must-haves verified
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
re_verification:
  previous_status: human_needed
  previous_score: 39/39
  gaps_closed:
    - "A custom service type confirmed in the Add custom… dialog appears as the selected chip immediately (UAT gap 1)"
    - "A service entry can carry more than one service type, and the form lets the user toggle several presets/custom types (UAT gap 2)"
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "On a device/simulator, re-run UAT test 1's two paths: (a) open a vehicle's history → Add service → tap Add custom…, type e.g. Battery replacement, confirm; (b) tap three preset chips in a chosen order, deselect the middle one, re-select it, save, then open the entry's detail and the history row."
    expected: "The custom label appears immediately as a selected chip carrying the Check glyph and can be deselected/re-selected without disappearing; the selection permits several types; the saved entry's detail shows the labels joined by ' · ' in tap order on at most two lines and the history row shows the same line on one line with ellipsis."
    why_human: "The ViewModel → state transition for both gaps is proven by executable tests, but the final Compose recomposition → rendered pixel (chip visible, selected styling, joined title/ellipsis) is inspection-only. UAT test 1 was answered before plans 02-07/02-08 existed, so its pass/fail record does not cover the fixes."
---

# Phase 2: Service Log Verification Report

**Phase Goal:** Users can log, view, and manage the complete history of service events for any vehicle.
**Mode:** mvp (ROADMAP) — the user story used below is the one carried verbatim in the 02-02 and 02-06 plans: *"As a vehicle owner, I want to log every service event — date, mileage, type, cost, notes, parts, workshop, and photos — and browse, edit, or delete any vehicle's full history, so that I have a trustworthy record of all maintenance performed."*
**Verified:** 2026-10-21T10:57:46Z — **re-verification** after the gap-closure cycle (plans 02-07 + 02-08)
**Status:** human_needed
**Score:** 52/52 must-haves verified

> **Method note (re-verification).** The previous report (39 truths: 5 ROADMAP SCs + plans 02-01…02-06) is superseded. This report re-checks the 39 previously verified truths against HEAD `97f4fab` and adds the 13 truths from plans 02-07 (7) and 02-08 (6) — 52 total. SUMMARY claims were not used as evidence: every row cites the file and symbol checked in the working tree. The claim-bearing test results were regenerated in this session with `--rerun` (both targets were UP-TO-DATE first): iOS **131 tests / 0 failures / 0 errors**, Android host **80 / 0 / 0**, migration guard **BUILD SUCCESSFUL exit 0**. The working tree contains exactly one uncommitted change, the comment-only `App.kt` typo logged in `deferred-items.md`; no Phase 2 source file was modified by this verification.

> **Goal-form discrepancy (MVP mode).** The ROADMAP `Goal:` line is outcome-form, not user-story form, and `gsd-tools` is not installed on this machine, so `user-story.validate` could not run. Both 02-02 and 02-06 record the same fidelity note: the story restates ROADMAP SC 1–5 and SVC-01…SVC-07 with no invented scope, following the `01-VERIFICATION.md` precedent. To move the canonical story into ROADMAP.md, run `/gsd-mvp-phase 2`.

## User Flow Coverage (MVP)

| # | User-story step | Expected | Evidence in codebase | Status |
|---|-----------------|----------|----------------------|--------|
| 1 | Open a vehicle and reach its service history | Summary row opens the history; empty state at zero entries, otherwise newest-date-first list | `VehicleDetailScreen.kt:321-323` (`ServiceSummaryRow` → `onOpenServiceHistory`); `App.kt:130` → `ServiceHistoryRoute`; four-state `when` in `ServiceHistoryScreen.kt`; shared `EmptyState` | Code-complete — previously UAT-confirmed (test 5) |
| 2 | Log a service event in the 30-second path | Today pre-set, mileage pre-filled, one-tap type chips (multi-select), cost/notes optional, Save last | `ServiceEntryEditViewModel.kt:134-158`; `ServiceEntryEditScreen.kt:263-470`; `ServiceEntryFormValidation.kt:67-68` | Code-complete — **re-check owed (human item)**; UAT test 1 predates the fixes |
| 3 | Add parts and workshop/mechanic | Inline parts rows (name + quantity, default 1); workshop/mechanic fields; detail renders the locked order | `PartsEditor.kt`; `ServiceEntryEditScreen.kt` workshop/mechanic fields; `ServiceEntryRepositoryImpl.kt:165-179`; `ServiceEntryDetailScreen.kt:253-300` | Code-complete — UAT test 2 passed |
| 4 | Attach one or more receipt photos | Additive gallery picks up to 5; previews; app-owned copies; two-set file rule | `ServicePhotoStrip.kt:110-190`; `ServiceEntryEditViewModel.kt:283-332,379,396`; `PhotoStorage.kt:33,60`; `ServiceEntryDeletionTest` 8/8 | Code-complete — UAT test 3 passed |
| 5 | Edit any existing entry | Same form prefilled once; save updates the row and fully replaces children (parts, photos, types) | `ServiceEntryEditViewModel.kt:159-195,241-275`; `ServiceEntryRepositoryImpl.kt:142-181`; `App.kt:143-151` | Code-complete — UAT test 4 passed |
| 6 | Delete any existing entry (and a vehicle with its log) | Dialog is the only delete path; confirm removes children then files; vehicle delete cascades entries, type/part/photo rows and files | `ServiceEntryDetailScreen.kt:190-210`; `ServiceEntryDetailViewModel.kt:95-120`; `VehicleRepositoryImpl.kt:77-92`; `ServiceEntryDeletionTest` 8/8 | Code-complete — UAT test 6 passed |
| 7 | Outcome: a trustworthy offline record | All data local; no network; schema survives restart and every upgrade path (v1→v2→v3) | No network dependency; SQLite + private files; `1.sqm` / `2.sqm`; migration guard green; real-driver repository tests | Code-complete — UAT test 7 passed |

## Goal Achievement

### Observable Truths

| # | Truth | Source | Status | Evidence |
|---|-------|--------|--------|----------|
| 1 | User can create a service entry with date, mileage, service type (preset or custom), cost and notes in under 30 seconds (ROADMAP SC1) | ROADMAP SC1 | ✓ VERIFIED | `ServiceEntryEditScreen.kt:263-470` field order Date → Mileage → Type → Cost → Notes → Workshop/Mechanic → Parts → Photos → Save; `ServiceEntryFormValidation.kt:67-68` gates on date + mileage + non-empty type list; presets `ServiceTypePresets.ALL`; persistence via `repo.save` (`ServiceEntryEditViewModel.kt:255-258`); timing only is human |
| 2 | User can add parts replaced/repaired (name, quantity) and workshop/mechanic details (SC2) | ROADMAP SC2 | ✓ VERIFIED | `PartsEditor.kt` rows default quantity "1", add/remove; workshop/mechanic fields + detail `FieldRow`s; `ServiceEntryRepositoryImpl.kt:165-172` writes parts in the transaction; `ServiceEntryRepositoryTest.saveAddsAnEntryAndWritesChildrenWithSequentialPositions` |
| 3 | User can attach one or more photos to a service entry (SC3) | ROADMAP SC3 | ✓ VERIFIED | `ServicePhotoStrip` additive up to 5, gallery-only via `FileKitType.Image`; `PhotoStorage.SERVICE_ENTRY_DIRECTORY`; `service_photo` rows written in the save transaction (repo `:173-179`); deletion tests prove file lifecycle |
| 4 | User can view the full service history for a vehicle sorted by date or mileage (SC4) | ROADMAP SC4 | ✓ VERIFIED | `HistorySortControl` (Date \| Mileage, both descending); `ServiceHistoryViewModel` `flatMapLatest`; `.sq:54-60` `selectForVehicleByDate/Mileage`; tests `changingSortReQueriesWithTheNewOrder`, `historyOrdersAreDeterministicIncludingTheIdDescendingTieBreak` |
| 5 | User can edit or delete any existing service entry (SC5) | ROADMAP SC5 | ✓ VERIFIED | Edit route `entryId > 0` loads once and updates; delete is dialog-gated; `ServiceEntryDeletionTest` 8/8 incl. single-flight, failure-keeps-entry and vehicle cascade |
| 6 | A fresh install (v3) and every upgrade path (v1→v2→v3) produce the three service tables, and the CI migration guard stays green | 02-01 (extended by 02-07) | ✓ VERIFIED | `ServiceEntry.sq:6-52` DDL; `1.sqm` (v1→v2) and `2.sqm` (v2→v3) both committed; `2.db` and `3.db` tracked; `sqlite3 3.db ".tables"` → `service_entry, service_entry_type, service_part, service_photo, vehicle`; `:shared:verifyCommonMainVehiCareDatabaseMigration --rerun` → BUILD SUCCESSFUL exit 0; `ServiceEntryMigrationTest.schemaVersionIsThree` |
| 7 | Saving an entry with parts and photos and reading it back is lossless and atomic — a failed write leaves no partial rows | 02-01 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:124-192` wraps entry + parts + photos (+ types) in one `transactionWithResult`; repo test asserts children and positions round-trip; no child write exists outside the transaction |
| 8 | Editing an entry fully replaces its part, photo and type rows (no orphan children); both history orders are deterministic | 02-01 (extended by 02-07) | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:153-155` deletes parts/photos/types inside the update transaction, then re-inserts; `.sq:54-60` orders include `id DESC` tie-break; tests `saveWithAnExistingIdReplacesChildrenAndKeepsTheId`, `editingAnEntryReplacesItsTypeRows`, `historyOrdersAreDeterministicIncludingTheIdDescendingTieBreak` |
| 9 | Deleting an entry removes its child rows in the transaction and yields its photo paths for file deletion only after the rows are durably gone | 02-01 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:186-198`: paths collected inside `transactionWithResult`, `photoDeleter` called after it returns; types deleted at `:191`; test `deleteRemovesEntryAndChildrenAndHandsEveryPhotoPathToTheDeleter` |
| 10 | Learned service types are derived across all vehicles, newest-used first, with preset-matching labels collapsed case-insensitively | 02-01 (re-based on `service_entry_type` by 02-07) | ✓ VERIFIED | `ServiceEntry.sq:96-100` `GROUP BY lower(label)` + `ORDER BY last_used_id DESC`; `ServiceEntryRepositoryImpl.kt:94-98` maps through `ServiceTypePresets.filterLearned`; tests `learnedTypesAreCrossVehicleNewestFirstAndPresetCaseDuplicatesCollapse`, `learnedTypesReadTheChildTableNewestFirstAndCollapseCaseVariants`. Caveat: SQLite `lower()` is ASCII-only — WARNING WR-02 |
| 11 | The build resolves kotlinx-datetime 0.8.0 (plain, not the `-0.6.x-compat` variant) from the single version catalog | 02-02 | ✓ VERIFIED | `gradle/libs.versions.toml:20,56` one pin; `shared/build.gradle.kts:69` `libs.kotlinx.datetime` |
| 12 | A civil date round-trips through storage: today from the system zone, storage UTC start-of-day millis, display reads back the same civil date | 02-02 | ✓ VERIFIED | `ServiceDates.kt:35-51` (`today`, `toStorage` = `atStartOfDayIn(TimeZone.UTC)`, `fromStorage`); `ServiceDatesTest` 4/4 on both targets |
| 13 | The display formats are `19 Oct 2026`, `123.456 km`, `Rp 450.000` | 02-02 | ✓ VERIFIED | `ServiceDates.display`; `RupiahFormat.formatMileage/formatRupiah`; `ServiceDatesTest` 4/4, `RupiahFormatTest` 5/5 |
| 14 | Every Phase 2 string exists in the centralized resource set and resolves through `Res.string`/`Res.plurals` — no inline UI literal | 02-02 | ✓ VERIFIED | `strings.xml` carries the full Phase 2 set incl. plurals `service_summary_entries`; all service screens/components resolve copy through `Res.*`; the only interpolated literals are data joins (`name × qty`, `label: value`), not copy |
| 15 | The Add service form opens with today's date already set and mileage pre-filled from the vehicle's last entry; submit enables only once date + mileage + type are present | 02-03 | ✓ VERIFIED | `ServiceEntryEditViewModel.kt:83-96` synchronous date seed; `:134-158` prefill merges only into blank fields; `ServiceEntryFormValidation.kt:67-68` + button `enabled` at screen `:463` |
| 16 | One tap on a type chip toggles it; "Add custom…" opens a one-field dialog whose confirmed label becomes the selection, and a case-insensitive preset match selects the existing chip without rewriting anything | 02-03 (D-26 single-select clause superseded 2026-10-21 by 02-08) | ✓ VERIFIED | `ServiceTypePicker.kt:86-99` FlowRow chips; `ServiceTypeDialog.kt` one field + 60-char cap; `ServiceEntryEditViewModel.kt:222-225` → `ServiceTypeSelection.kt:36-63` preset → learned → new; `toggleServiceType` implements the now-multi-select contract (truth 48) |
| 17 | Parts (name + quantity, default 1) and up to 5 gallery-picked photos are edited inline in the same single scrolling screen; the submit button is the last element | 02-03 | ✓ VERIFIED | `ServiceEntryEditScreen.kt:406-470` PartsEditor → PhotoStrip → Button; `ServicePhotoStrip.kt:174` add control only below 5; single `Column` scroll container; `imePadding` |
| 18 | A mileage lower than the baseline shows the verbatim warning in the field's supporting slot and never blocks save | 02-03 | ✓ VERIFIED | Screen lines 274, 312-321 (`warn_mileage_lower`); `isSubmittable` ignores the baseline; the warning branch has no effect on `enabled` |
| 19 | Save writes entry + parts + photos in one transaction via the repository; the same destination in edit mode loads an existing entry once and updates it | 02-03 | ✓ VERIFIED | ViewModel `initialLoadApplied` guard `:159-195`; save `:241-275`; repository transaction `:124-192` |
| 20 | Photo files follow the two-set rule: session-owned files die on abandon/removal, row-owned files die only after a successful save | 02-03 | ✓ VERIFIED | `ServiceEntryEditViewModel.kt:129` sets; `onPhotoRemoved:317-332`; `markPhotosSaved:396`; `onCleared:379` deletes only `persistedButUnsavedPaths`; WR-05 hold on submitted photos `:253-267` |
| 21 | The Koin graph itself binds the repository — `single<ServiceEntryRepository>` over the impl with `PhotoStorage::delete` — before any service ViewModel resolves through `get()` | 02-03 | ✓ VERIFIED | `AppModule.kt:47-49`; ViewModel factories `:58-64`; repository is the only binding, impl default `{}` is test-only |
| 22 | Both forms read identically: the vehicle form's error line and the service form's supporting lines use the declared `Label` role | 02-03 | ✓ VERIFIED | `VehicleEditScreen.kt:246-248` `labelLarge`; `ServiceEntryEditScreen.kt:306,316,326` `labelLarge`; `Type.kt:45-46` declares Label = `labelLarge` |
| 23 | Tapping the destructive action opens a confirmation dialog carrying the entry copy; nothing is removed before the confirm, and the dialog's confirm is the only path to the ViewModel's delete | 02-04 | ✓ VERIFIED | `ServiceEntryDetailScreen.kt:190-210`; `ConfirmDeleteDialog` `onDismissRequest = onDismiss`; `viewModel.delete` appears only inside `onConfirm`; destructive button disabled while deleting |
| 24 | Entry detail paints every state: centred indicator while loading, not-available copy with a way back, locked read-only order when present | 02-04 | ✓ VERIFIED | `ServiceEntryDetailScreen.kt:155-231` (Loading / NotFound / Content); order Cost → Parts → Workshop → Mechanic → Notes → Photos → Delete (`:235-300`) |
| 25 | Deleting an entry removes part/photo rows in one transaction and files only after rows are durable; a failure leaves the entry stored and on screen with a message | 02-04 | ✓ VERIFIED | Repo `:186-198`; ViewModel `:106-120` catches and sets `deleteFailed` without clearing state; screen `LaunchedEffect` shows `error_delete_entry`; tests `aFailedDeleteKeepsTheEntryAndReportsFailure`, `deleteDropsSecondCallWhileFirstInFlight` |
| 26 | Deleting a vehicle removes every service entry, all of their child rows (types, parts, photos) and every entry photo file in the same transaction — no orphan rows, no orphan files | 02-04 (extended by 02-07) | ✓ VERIFIED | `VehicleRepositoryImpl.kt:77-92`: collect paths → `deletePhotos/Parts/Types/EntriesForVehicle` → vehicle delete → file deleters after commit; test `vehicleCascadeRemovesAllEntriesAndTheirPhotosAndVehiclePhoto` asserts type rows gone and the survivor keeps `["Brake service"]` |
| 27 | One dialog implementation serves both entities: the vehicle screen keeps its exact copy, the entry screen passes entry copy, and Cancel stays `Ink` | 02-04 | ✓ VERIFIED | Shared `ui/components/ConfirmDeleteDialog.kt` used by both detail screens; parameterized title/message/confirmLabel; Cancel `TextButton` `contentColor = Ink` (`:94`) |
| 28 | One label/value row serves both Detail screens: `FieldRow` lives in `ui/components/`, the vehicle Detail imports it, and the entry Detail renders it rather than a copy | 02-04 | ✓ VERIFIED | `ui/components/FieldRow.kt`; `VehicleDetailScreen.kt:369-398` and `ServiceEntryDetailScreen.kt:253-268` both call it; no duplicate implementation remains |
| 29 | History opens newest-date-first; one tap on Mileage re-sorts instantly; the choice defaults to Date on every visit and is never persisted | 02-05 | ✓ VERIFIED | `ServiceHistoryViewModel.kt:48` `MutableStateFlow(HistorySort.DATE)`; `observeHistoryUiState` `flatMapLatest` `:96-104`; no persistence seam; test `changingSortReQueriesWithTheNewOrder` |
| 30 | Every history state paints something: labelled loading, shared empty state with a working CTA, sort control + keyed rows, load-error copy with Try again | 02-05 | ✓ VERIFIED | `ServiceHistoryScreen.kt:97-175`; loading label `loading_service_history`; `EmptyState` with `onAddService`; `error_load_history` + `viewModel::retry`; test `retryAfterFailureRestartsTheObservation` |
| 31 | The Add service FAB is present in every state and opens the add form; the empty-state CTA does the same | 02-05 | ✓ VERIFIED | `ServiceHistoryScreen.kt:176-190` FAB after the `when`; `App.kt:170-182` both callbacks navigate to `ServiceEntryEditRoute(vehicleId, 0L)` |
| 32 | Tapping a row opens that entry's detail; there is no long-press, swipe or other gesture on the list | 02-05 | ✓ VERIFIED | `ServiceHistoryRow.kt:43-50` single `Card(onClick)`; no `combinedClickable`/swipe modifiers in the history tree |
| 33 | Rows are bound to their entries by stable keys | 02-05 | ✓ VERIFIED | `ServiceHistoryScreen.kt:141` `items(state.entries, key = { entry -> entry.id })` |
| 34 | The empty state is one shared component; the vehicle list and history pass their own icon and copy, and the vehicle list's rendering is unchanged | 02-05 | ✓ VERIFIED | `ui/components/EmptyState.kt`; `VehicleListScreen.kt:92-97` passes the same vehicle icon/heading/body/action as before; history passes `Icons.Filled.Build` + service copy |
| 35 | The vehicle Detail's reserved slot is now a tappable Service history row reading `N entries · Last service 19 Oct 2026`, and `No entries yet` when the vehicle has none | 02-06 | ✓ VERIFIED | `ServiceSummaryRow.kt:100-114`; `service_summary_none` `:114`; plural `service_summary_entries` + `service_summary_last` (strings 113-118); `ServiceDates.display` |
| 36 | The row is navigation only: it opens the history screen, never the add form, never lists entries, and stays tappable at zero entries | 02-06 | ✓ VERIFIED | `VehicleDetailScreen.kt:321-323` `onClick = onOpenServiceHistory`; `App.kt:130` navigates to `ServiceHistoryRoute`; no add callback or list inside the row |
| 37 | While the aggregate resolves, the row renders its title alone at a stable 72dp minimum height — never a spinner | 02-06 | ✓ VERIFIED | `ServiceSummaryRow.kt:62` `heightIn(min = 72.dp)`; meta text only `if (summary != null)` (`:77`); `ServiceSummaryViewModel` starts `null` and maps failure to `null` |
| 38 | The four spaces of the retired placeholder are exactly where the new row sits: the 48px break above and below is unchanged | 02-06 | ✓ VERIFIED | `VehicleDetailScreen.kt:317-326` `Spacer(48.dp)` → `ServiceSummaryRow` → `Spacer(48.dp)` |
| 39 | The coming-soon placeholder is gone from the source tree together with its string, and nothing references either | 02-06 | ✓ VERIFIED | `ComingSoonSlot.kt` absent; repo-wide grep for `ComingSoon`/`coming_soon` outside planning docs returns nothing; `detail_service_history` present at strings line 77 |
| 40 | Every stored entry exposes an ordered, non-empty list of service types; a v2 row's `service_type` is backfilled into position 0 by `2.sqm`, so no entry loses its type on upgrade (UAT gap 2) | 02-07 | ✓ VERIFIED | `ServiceEntry.kt:28` `serviceTypes: List<String>` (non-empty contract in KDoc `:14`); `2.sqm:17-18` `INSERT INTO service_entry_type(entry_id, position, label) SELECT id, 0, service_type FROM service_entry`; `ServiceEntryMigrationTest.migratingFromTwoBackfillsTheMirrorLabelAtPositionZero` drops v3, stores two legacy rows, runs `Schema.migrate(driver, 2, 3)` and asserts `[0]` with the verbatim label |
| 41 | A saved entry writes one `service_entry_type` row per label, in selection order, and the legacy `service_entry.service_type` column stays an exact mirror of position 0 — never a second source of truth | 02-07 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:158-164` `forEachIndexed { index, label -> insertEntryType(entry_id, position = index, label) }`; `ServiceEntryEntityMapper.kt:78` `serviceTypeMirror = serviceTypes.first()` bound to the NOT NULL column (`:65`); tests `saveWritesOneOrderedTypeRowPerLabelAndMirrorsPositionZero`, `ServiceEntryEntityMapperTest` mirror case |
| 42 | Editing an entry replaces its type rows exactly like parts and photos: no orphan rows and no stale labels survive an edit | 02-07 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:155` `deleteTypesForEntry` inside the update transaction, re-inserted from the new list at `:158-164`; test `editingAnEntryReplacesItsTypeRows` |
| 43 | Deleting an entry removes its type rows; deleting a vehicle removes every type row of every one of its entries, and another vehicle's type rows survive untouched | 02-07 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:191` delete in the entry transaction; `VehicleRepositoryImpl.kt:84` `deleteTypesForVehicle` before `deleteEntriesForVehicle`; tests `deletingAnEntryRemovesItsTypeRows`, `vehicleCascadeRemovesAllEntriesAndTheirPhotosAndVehiclePhoto` |
| 44 | The chip group's learned labels come from `service_entry_type`, grouped case-insensitively and ordered newest-used first, and no stored label is ever rewritten (D-40) | 02-07 | ✓ VERIFIED | `ServiceEntry.sq:96-100` reads the child table, `GROUP BY lower(label)`, `ORDER BY last_used_id DESC`; `ServiceTypeSelection.kt:45-59` matches presets/learned case-insensitively and preserves stored casing; `ServiceTypePresets.filterLearned`; tests `learnedTypesReadTheChildTableNewestFirstAndCollapseCaseVariants`. Caveats: ASCII-only SQL fold (WR-02) and no tie-break (WR-04) — see Anti-Patterns |
| 45 | A fresh install (v3) and a v2 upgrade both produce the same `service_entry_type` table; the migration guard and the regenerated baseline stay green | 02-07 | ✓ VERIFIED | `ServiceEntry.sq:42-47` DDL is structurally identical to `2.sqm:10-15`; `3.db` committed and `sqlite3 ".schema service_entry_type"` matches; guard task exit 0 |
| 46 | Reading an entry or a history page costs a constant number of queries per list, not one query per entry (history hydrates all types in one vehicle-scoped query) | 02-07 | ✓ VERIFIED | `ServiceEntryRepositoryImpl.kt:50` one `selectTypesForVehicle(vehicleId)` per history emission, grouped by `entry_id`; `:63-72` fixed four-query hydrate for a single entry; no per-row query inside a loop |
| 47 | Confirming a custom type through "Add custom…" immediately renders it as a selected chip: the ViewModel transition adds the confirmed label to the chip list as well as to the selection, with case-insensitive dedupe — covered by a ViewModel-level iosTest plus the pure-transition state test on both platforms (UAT gap 1) | 02-08 | ✓ VERIFIED | `ServiceEntryEditScreen.kt:216` `onCustomTypeConfirmed(trimmed)` → `ServiceEntryEditViewModel.kt:222-225` → `ServiceTypeSelection.kt:84-96` `copy(values.serviceTypes = …, learnedTypes = …)`; picker union `ServiceTypePicker.kt:74-81`, `isSelected` `:94`, `FilterChip` `:141-166`; tests `ServiceEntryEditViewModelCustomTypeTest.customTypeConfirmationSelectsItAndMakesItARenderableChip` (iosTest, real ViewModel on `Dispatchers.setMain`), `ServiceEntryEditViewModelStateTest.confirmedCustomLabelEntersBothTheSelectionAndTheRenderedChipList` (commonTest) |
| 48 | The chip group is multi-select: tapping a chip toggles it, any number of types may be selected, and any selected chip may be deselected; Save stays disabled while the selection is empty | 02-08 | ✓ VERIFIED | `ServiceTypeSelection.kt:71-78` `toggleServiceType` (case-insensitive append/remove); `ServiceTypePicker.kt:143` `onClick = onToggle`; `ServiceEntryFormValidation.kt:67-68` `serviceTypes.isNotEmpty()`; screen button `enabled` `:463`; tests `togglingAppendsThenRemovesCaseInsensitively`, `severalTogglesKeepTapOrderAsTheDisplayOrder`, `togglingChangesTheSelectionOnly`, `togglingSeveralChipsKeepsTapOrder` |
| 49 | The form requires at least one service type; the saved entry preserves the order the user selected and carries no case-insensitive duplicates | 02-08 | ✓ VERIFIED | `ServiceEntryFormValidation.kt:68` gate; `:121` `serviceTypes = values.serviceTypes.distinctBy { it.lowercase() }`; `toggleServiceType`/`confirmCustomServiceType` append in tap order and dedupe case-insensitively; `ServiceEntryFormValidationTest` case `["Battery","battery","Oil change"] → ["Battery","Oil change"]` |
| 50 | An entry with one type renders exactly as before; an entry with two or more renders them joined by the locked middle-dot separator, with the detail title capped at two lines and the history row at one line plus ellipsis | 02-08 | ✓ VERIFIED | `ServiceEntryDetailScreen.kt:407` `serviceTypeLine(types) = types.joinToString(" · ")`; detail `:241-244` (`maxLines = 2`); `ServiceHistoryRow.kt:56-59` (`maxLines = 1`); `ServiceTypeLineTest` 6/6 incl. U+00B7 code point and single-type verbatim |
| 51 | The UI-SPEC's single-select rule, its "candidate learned label" sentence and its submission-gate wording are rewritten to the approved multi-select contract, so contract and implementation agree | 02-08 | ✓ VERIFIED | `02-UI-SPEC.md:191` heading "(D-26, multi-select 2026-10-21)"; `:221` gate "**at least one service type** is selected"; `:228` joined-title rule; `02-CONTEXT.md:22` records D-26 superseded; picker KDoc `:38-45` matches |
| 52 | The vehicle summary row is untouched — its meta line is `N entries · Last service <date>` and never names a type (D-32) | 02-08 | ✓ VERIFIED | `ServiceSummaryRow.kt:100-114` meta built only from `service_summary_entries` plural + `service_summary_last` + `ServiceDates.display`; no type/label read anywhere in `ServiceSummaryViewModel.kt`; `VehicleDetailScreen.kt:321-323` unchanged surface |

**Score:** 52/52 truths verified

### GAP 1 / GAP 2 evidence trace (user action → rendered pixel)

| Link in the chain | Proven by | Status |
|-------------------|-----------|--------|
| GAP 1 — AssistChip tap → `onAddCustom` → dialog shown | `ServiceTypePicker.kt:109-110`; `ServiceEntryEditScreen.kt:205,213` — inspection | Code path present, not executable-tested |
| GAP 1 — dialog confirm → `viewModel.onCustomTypeConfirmed` | `ServiceEntryEditScreen.kt:216` — inspection | Code path present |
| GAP 1 — ViewModel → selection **and** learned chip list in one `copy` | **Executable**: `ServiceEntryEditViewModelCustomTypeTest.customTypeConfirmationSelectsItAndMakesItARenderableChip` (iosTest, 5/5), `ServiceEntryEditViewModelStateTest.confirmedCustomLabelEntersBothTheSelectionAndTheRenderedChipList` (iosTest + androidHost, 5/5 each), `ServiceTypeSelectionTest` (10/10 both targets) | ✓ PROVEN |
| GAP 1 — state → picker options → selected `FilterChip` → visible `Check` glyph | `ServiceTypePicker.kt:74-81,94,141-166` — inspection only (screen-level Compose test does not exist) | ⚠️ inspection — **human item** |
| GAP 2 — chip toggles multiple values | **Executable**: `toggleServiceType` tests (`ServiceTypeSelectionTest` 10/10; `ServiceEntryEditViewModelCustomTypeTest` 5/5) | ✓ PROVEN |
| GAP 2 — multi-type write/read/replace/delete/cascade | **Executable**: `ServiceEntryRepositoryTest` 13/13 (incl. `saveWritesOneOrderedTypeRowPerLabelAndMirrorsPositionZero`), `ServiceEntryDeletionTest` 8/8, `ServiceEntryMigrationTest` 2/2 | ✓ PROVEN |
| GAP 2 — joined title → detail 2 lines / history 1 line + ellipsis | **Executable**: `ServiceTypeLineTest` 6/6 for the string; `maxLines`/`TextOverflow` rendering is inspection (`ServiceEntryDetailScreen.kt:241-246`, `ServiceHistoryRow.kt:56-61`) | ⚠️ string proven, pixels human |
| GAP 2 — v2 database upgrade keeps every type | **Executable**: `ServiceEntryMigrationTest.migratingFromTwoBackfillsTheMirrorLabelAtPositionZero` (real driver, `Schema.migrate(driver, 2, 3)`) | ✓ PROVEN |

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq` | v3 DDL (4 service tables), 4 indices, all named queries incl. `service_entry_type` reads/writes | ✓ VERIFIED | `service_entry_type` at `:42-47`; queries `:54-125`; repository binds every statement |
| `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm` | v1→v2 delta with DDL identical to the v2 form | ✓ VERIFIED | CREATE TABLE/INDEX statements identical; guard exit 0 |
| `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/2.sqm` | v2→v3: create table + backfill position 0 | ✓ VERIFIED | `:10-15` DDL, `:17-18` backfill `SELECT id, 0, service_type` |
| `shared/src/commonMain/sqldelight/databases/2.db` | Committed v2 baseline, `1.db` retained | ✓ VERIFIED | Both tracked; sqlite3 shows the v2 tables + 4 indices |
| `shared/src/commonMain/sqldelight/databases/3.db` | Committed v3 baseline | ✓ VERIFIED | `.tables` → 5 tables incl. `service_entry_type`; schema matches `2.sqm` |
| `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt` | Transactional save incl. types, delete ordering, two orders, learned types, summary, mileage, batched hydrate | ✓ VERIFIED | `:50` vehicle-scoped type hydrate; `:158-164` type insert; `:153-155` type replace; `:191` type delete |
| `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceTypePresets.kt` | Six locked presets + case-insensitive learned filter | ✓ VERIFIED | `ALL` + `filterLearned` |
| `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt` | Ordered, non-empty `serviceTypes` on the only UI model | ✓ VERIFIED | `:28`; validity documented `:14` |
| `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt` | Position-0 mirror bind + documented child-row fallback | ✓ VERIFIED | `:65,78` mirror; `:27` fallback |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt` | Pure confirm + toggle transitions shared by ViewModel and tests | ✓ VERIFIED | `confirmCustomServiceType:36-63`, `toggleServiceType:71-78`, `withConfirmedCustomType:84-96` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt` | Multi-select chip group with selected-label union | ✓ VERIFIED | `allOptions:74-81`; `onToggle:143`; 48dp min; selected `Check` glyph |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt` | List-shaped values, at-least-one gate, deduped save mapping | ✓ VERIFIED | `:21` list; `:67-68` gate; `:121` dedupe |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt` | Add prefill, edit load-once, multi-select mutators, two-set photos | ✓ VERIFIED | `:222-225`, `:232-235`; prefill `:134-158` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt` | Single scrolling form, dialog wiring, picker props | ✓ VERIFIED | `:213-221` dialog; `:350-357` picker; submit last |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt` | Locked read-only anatomy + `serviceTypeLine` and 2-line cap | ✓ VERIFIED | `:241-246`; helper `:407` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt` | One-line joined type title | ✓ VERIFIED | `:56-59` |
| `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` | Cascade covering types, entries, parts, photos, files | ✓ VERIFIED | `:82-85` children-first; files after commit |
| `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt` | Real-driver v2→v3 backfill + schema version | ✓ VERIFIED | 2/2 pass |
| `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt` | Real-driver add/edit/replace/orders/hydrate/learned/delete | ✓ VERIFIED | 13/13 pass |
| `shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt` | Delete state machine, file removals, vehicle cascade incl. types | ✓ VERIFIED | 8/8 pass |
| `shared/src/iosTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelCustomTypeTest.kt` | ViewModel-level GAP 1 + multi-select regression | ✓ VERIFIED | 5/5 pass on a `setMain` dispatcher |
| `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelStateTest.kt` | Pure GAP 1 invariant on both targets | ✓ VERIFIED | 5/5 iOS + 5/5 Android host |
| `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeSelectionTest.kt` | Pure confirm/toggle transitions | ✓ VERIFIED | 10/10 on both targets |
| `shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeLineTest.kt` | Joined-type display contract | ✓ VERIFIED | 6/6 on both targets |
| `.planning/phases/02-service-log/02-UI-SPEC.md` | Rewritten multi-select contract | ✓ VERIFIED | `:191` heading, `:221` gate, `:228` joined-title rule |
| `shared/src/commonMain/kotlin/com/vehicare/util/ServiceDates.kt` | Single date seam | ✓ VERIFIED | UTC start-of-day storage; system-zone today |
| `shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt` | Grouping + rupiah + mileage + digit parse | ✓ VERIFIED | Dot-grouped output |
| `shared/src/commonMain/composeResources/values/strings.xml` | Complete Phase 2 string set; coming-soon key absent | ✓ VERIFIED | Includes plurals and all screen/component keys |
| `gradle/libs.versions.toml` | kotlinx-datetime 0.8.0 pinned once | ✓ VERIFIED | Lines 20, 56 |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServicePhotoStrip.kt` | 80dp strip, add/remove, decorative images | ✓ VERIFIED | 16:9 empty block; 48dp remove target; max 5 |
| `shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt` | Directory parameter + service-entry directory | ✓ VERIFIED | `SERVICE_ENTRY_DIRECTORY = "service_entries"` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/components/ConfirmDeleteDialog.kt` | Shared parameterized delete gate | ✓ VERIFIED | Used by both detail screens; Cancel Ink |
| `shared/src/commonMain/kotlin/com/vehicare/ui/components/FieldRow.kt` | Shared label/value row | ✓ VERIFIED | Used by both detail screens |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailViewModel.kt` | Streaming detail + single-flight delete | ✓ VERIFIED | `observeEntry`; `delete` guard |
| `shared/src/commonMain/kotlin/com/vehicare/ui/components/EmptyState.kt` | Shared parameterized empty state | ✓ VERIFIED | Vehicle list + history use it |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryViewModel.kt` | Streaming history + sort + retry | ✓ VERIFIED | `flatMapLatest` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceHistoryScreen.kt` | Sort control + keyed list + FAB + state matrix | ✓ VERIFIED | Four states; FAB outside `when` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/HistorySortControl.kt` | Two-segment Date \| Mileage | ✓ VERIFIED | `SingleChoiceSegmentedButtonRow` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt` | Tappable summary, 72dp, null = title-only | ✓ VERIFIED | `heightIn(min = 72.dp)` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceSummaryViewModel.kt` | One aggregate read, null while unresolved | ✓ VERIFIED | `stateIn(initialValue = null)` |
| `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` | Summary row at placeholder position + callback | ✓ VERIFIED | 48dp breaks preserved |

All declared artifact paths exist (39 path-existence checks passed); every artifact that carries behaviour is substantive and wired, not a stub.

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `ServiceEntryRepositoryImpl.kt` | `ServiceEntry.sq` | generated `serviceEntryQueries.*` | ✓ WIRED | All reads/writes bind through generated queries |
| `ServiceEntryRepositoryImpl.kt` | `service_entry_type` | `insertEntryType` per index inside the save transaction | ✓ WIRED | `:158-164` |
| `ServiceEntryRepositoryImpl.kt` | `service_entry_type` | `selectTypes` combined into `observeEntry` | ✓ WIRED | `:72` |
| `ServiceEntryRepositoryImpl.kt` | `service_entry_type` | `selectTypesForVehicle` into `observeHistory` (one query per page) | ✓ WIRED | `:50` |
| `ServiceEntryRepositoryImpl.kt` | `service_entry_type` | `selectLearnedTypes` grouped by `lower(label)` | ✓ WIRED | `:94-98` |
| `VehicleRepositoryImpl.kt` | `service_entry_type` | `deleteTypesForVehicle` before `deleteEntriesForVehicle` | ✓ WIRED | `:84-85` |
| `ServiceEntry.kt` | `ServiceEntryEntityMapper.kt` | `serviceTypes.first()` bound to the NOT NULL mirror | ✓ WIRED | `:78` |
| `ServiceEntryRepositoryImpl.kt` | `PhotoStorage.delete` | injected `photoDeleter` after commit | ✓ WIRED | `AppModule.kt:48` |
| `1.sqm` | `2.db` | schema generation + committed baseline | ✓ WIRED | Guard exit 0 |
| `2.sqm` | `3.db` | schema generation + committed baseline | ✓ WIRED | Guard exit 0; DDL parity |
| `ServiceTypeSelection.kt` | `ServiceEntryEditViewModel.kt` | `withConfirmedCustomType` / `withToggledType` delegation | ✓ WIRED | `:224`, `:234` |
| `ServiceEntryEditViewModel.kt` | `ServiceTypeSelection.kt` | pure transition call | ✓ WIRED | `:87-95` |
| `ServiceEntryEditScreen.kt` | `ServiceTypePicker` | `selected = values.serviceTypes`, `onToggle = onTypeToggled` | ✓ WIRED | `:350-357` |
| `ServiceEntryEditScreen.kt` | `ServiceEntryEditViewModel.kt` | `koinViewModel(parametersOf(vehicleId, entryId))` | ✓ WIRED | `:103` |
| `ServiceEntryEditViewModel.kt` | `PhotoStorage.persist` | `SERVICE_ENTRY_DIRECTORY` | ✓ WIRED | `:291` |
| `ServiceEntryEditViewModel.kt` | `ServiceEntryRepository.save` | `viewModelScope` + `repo.save` | ✓ WIRED | `:255-258` |
| `App.kt` | `ServiceEntryEditRoute` | `composable<ServiceEntryEditRoute>` + `toRoute` | ✓ WIRED | `:143-151` |
| `AppModule.kt` | `ServiceEntryRepositoryImpl.kt` | `single<ServiceEntryRepository>` | ✓ WIRED | `:47-49` |
| `ServiceEntryDetailScreen.kt` | `ConfirmDeleteDialog.kt` | only delete call site | ✓ WIRED | `:197-210` |
| `ServiceEntryDetailScreen.kt` | `ServiceEntryDetailViewModel.kt` | `koinViewModel(parametersOf(entryId))` | ✓ WIRED | `:97` |
| `VehicleRepositoryImpl.kt` | `ServiceEntry.sq` | cascade statements in transaction | ✓ WIRED | `:82-85` |
| `ServiceEntryDetailScreen.kt` | `ui/components/FieldRow.kt` | shared import + render | ✓ WIRED | `:253-268` |
| `ServiceHistoryRow.kt` | `ServiceEntryDetailScreen.kt` | shared `serviceTypeLine` import (never re-implemented) | ✓ WIRED | import `:27`, use `:56` |
| `ServiceHistoryViewModel.kt` | `ServiceEntryRepository.observeHistory` | `flatMapLatest` over sort | ✓ WIRED | `:96-104` |
| `ServiceHistoryRow.kt` | `ServiceEntryDetailScreen.kt` | shared `serviceMetaLine` | ✓ WIRED | import `:26`, use `:63` |
| `ServiceHistoryScreen.kt` | `ui/components/EmptyState.kt` | empty branch | ✓ WIRED | `:114-122` |
| `App.kt` | `ServiceHistoryRoute` | `composable<ServiceHistoryRoute>` | ✓ WIRED | `:170-182` |
| `VehicleDetailScreen.kt` | `ServiceSummaryRow.kt` | Content branch at former placeholder band | ✓ WIRED | `:321-323` |
| `ServiceSummaryViewModel.kt` | `ServiceEntryRepository.observeSummary` | single aggregate StateFlow | ✓ WIRED | `:31-39` |
| `02-UI-SPEC.md` | implementation | multi-select + joined-title wording matches code | ✓ WIRED | Cross-read `:191,221,228` vs picker/validation/detail |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ServiceHistoryScreen` | `state.entries` (incl. `serviceTypes`) | `observeHistoryUiState` → `observeHistory` → `selectForVehicleByDate/Mileage` + `selectTypesForVehicle` | Yes — real rows from SQLite, re-emitted on either table change | ✓ FLOWING |
| `ServiceEntryDetailScreen` | `state` (Content) | `observeEntry` → `selectById` + `selectParts` + `selectPhotos` + `selectTypes` | Yes — full entry with ordered types | ✓ FLOWING |
| `ServiceEntryEditScreen` | `values.serviceTypes` / `learnedTypes` | Add: `observeLearnedTypes` + synchronous date seed; Edit: `observeEntry.toFormValues`; confirm/toggle transitions | Yes — learned labels from `service_entry_type`, selection from user input | ✓ FLOWING |
| `ServiceTypePicker` | `allOptions`, `isSelected` | `presets + learned + unmatched selected` built from the state passed at `:350-357` | Yes — every selected label is guaranteed an option chip | ✓ FLOWING |
| `ServiceSummaryRow` | `summary` | `ServiceSummaryViewModel` → `observeSummary` → `selectSummary` COUNT/MAX | Yes — aggregate from entries; `null` only while unresolved | ✓ FLOWING |
| `ServicePhotoStrip` | `photoPaths` | Edit/detail state → `PhotoStorage.resolve` → Coil `AsyncImage` | Yes — app-private files | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| iOS shared test suite (current tree) | `./gradlew :shared:iosSimulatorArm64Test --rerun` then aggregate `shared/build/test-results/iosSimulatorArm64Test/*.xml` | 23 classes, **131 tests, 0 failures, 0 errors, 0 skipped** | ✓ PASS |
| Android host test suite (current tree) | `./gradlew :shared:testAndroidHostTest --rerun` then aggregate the XML | 15 classes, **80 tests, 0 failures, 0 errors, 0 skipped** | ✓ PASS |
| GAP 1 ViewModel regression | `ServiceEntryEditViewModelCustomTypeTest` (part of the iOS run above) | 5/5 pass, incl. `customTypeConfirmationSelectsItAndMakesItARenderableChip` | ✓ PASS |
| GAP 1 pure invariant (both platforms) | `ServiceEntryEditViewModelStateTest` | 5/5 iOS + 5/5 Android host | ✓ PASS |
| GAP 2 transition tests | `ServiceTypeSelectionTest`, `ServiceTypeLineTest` | 10/10 + 6/6 on both targets | ✓ PASS |
| GAP 2 data/migration tests | `ServiceEntryRepositoryTest` 13/13, `ServiceEntryDeletionTest` 8/8, `ServiceEntryMigrationTest` 2/2 | All pass in the iOS run | ✓ PASS |
| Migration guard | `./gradlew :shared:verifyCommonMainVehiCareDatabaseMigration --rerun` | `BUILD SUCCESSFUL`, exit 0 | ✓ PASS |
| v3 baseline schema | `sqlite3 …/databases/3.db ".tables"` + `.schema service_entry_type` | `service_entry, service_entry_type, service_part, service_photo, vehicle`; DDL matches `2.sqm` | ✓ PASS |
| Migration DDL parity | normalized `CREATE TABLE` diff between `ServiceEntry.sq` and `2.sqm` | Identical column order, constraints and PK | ✓ PASS |

### Probe Execution

No probes are declared in any Phase 2 plan and no `scripts/*/tests/probe-*.sh` exists in the repository. The phase's runnable gate is the SQLDelight migration task, executed above.

### Requirements Coverage

| Requirement | Source Plan(s) | Description | Status | Evidence |
|-------------|----------------|-------------|--------|----------|
| SVC-01 | 02-01, 02-02, 02-03, 02-05, 02-07, 02-08 | Create an entry with date, mileage, type, cost, notes | ✓ SATISFIED | Form fields + gate + transactional save; multi-type list (truths 1, 15, 16, 48, 49); runtime human item |
| SVC-02 | 02-01, 02-03 | Add parts (name, quantity) | ✓ SATISFIED | `PartsEditor` + child rows in the save transaction; repo test; UAT test 2 passed |
| SVC-03 | 02-01, 02-03 | Attach one or more photos | ✓ SATISFIED | Additive strip ≤5 + `PhotoStorage` + `service_photo` rows + file deletion tests; UAT test 3 passed |
| SVC-04 | 02-03 | Record workshop and mechanic | ✓ SATISFIED | Two form fields + two detail `FieldRow`s; UAT test 2 passed |
| SVC-05 | 02-01, 02-02, 02-05, 02-06, 02-07, 02-08 | View full history sorted by date or mileage | ✓ SATISFIED | Sort control + two deterministic orders + batched type hydrate + joined title + summary row; UAT test 5 passed |
| SVC-06 | 02-01, 02-03, 02-04, 02-07, 02-08 | Edit an existing entry | ✓ SATISFIED | Load-once edit + full child replacement incl. types; UAT test 4 passed |
| SVC-07 | 02-01, 02-04, 02-07 | Delete a service entry | ✓ SATISFIED | Dialog gate + child/file ordering + type row removal + single-flight + failure retention; 8/8 deletion tests; UAT test 6 passed |

**Orphaned requirements:** none. Every SVC requirement mapped to Phase 2 is claimed by at least one plan and has implementation evidence.

### Anti-Patterns Found

No `TBD`/`FIXME`/`XXX`/`TODO`/`HACK` markers exist in any Phase 2 source file. No stub returns, empty handlers, console logging or placeholder copy were found. Review findings IN-01, IN-02, IN-04, IN-05, IN-06 and IN-07 are dead-code or contract-comment issues with no user-visible or must-have impact; they are not repeated below.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ServiceEntryEditViewModel.kt` | 142-151 | Add-mode prefill writes `learnedTypes = learned` wholesale, racing a custom confirmation made during the two DB round-trips (WR-01) | ⚠️ Warning | If the race is lost, the chip still renders via the picker's defensive union, but once deselected it disappears from the chip list until the next learned refresh. Does **not** falsify 02-08 truth 1 (selected ⇒ rendered holds); a real narrow-window UX defect |
| `ServiceEntry.sq` | 96-100 | `GROUP BY lower(label)` is ASCII-only, while Kotlin compares with `lowercase()`/`ignoreCase` (WR-02) | ⚠️ Warning | A non-ASCII pair such as `Épsilon`/`épsilon` renders as two chips while toggling treats them as one. Must-have truth 44 holds for the ASCII vocabulary the app produces; documented boundary inconsistency |
| `ServiceEntryRepositoryImpl.kt` | 142-164 | Update branch ignores the parent update's affected-row count, then writes child rows under a stale id (WR-03) | ⚠️ Warning | Reachable only when another actor deletes the row mid-edit (single-user app); would emit orphan type rows that also pollute `selectLearnedTypes`. Not triggered by any current flow or test |
| `ServiceEntry.sq` | 100 | `ORDER BY last_used_id DESC` has no tie-break, so labels first used in the same entry order by scan order (WR-04) | ⚠️ Warning | Chip order is deterministic per build but unspecified as a contract; multi-select makes ties more frequent. No must-have defines tie order |
| `ServiceEntryDetailScreen.kt` | 162 | `CircularProgressIndicator()` has no `contentDescription`; `contentDescription` and `loading_service_entry` are imported but unused although UI-SPEC specifies the label | ℹ️ Info (IN-03) | One transient state lacks screen-reader context; no must-have impact; UAT test 8 is the only check that covers it |
| `ServiceDateField.kt` | 84 | Date label omits the required `*` marker even though the date is required | ℹ️ Info (carry-over from the prior report) | Field reads optional; no must-have impact |
| `ServiceTypePicker.kt` | 141-166 | KDoc documents Mist/Paper/Ink chips but `FilterChip`/`AssistChip` pass no `colors`; `secondaryContainer` is not overridden in `Color.kt` | ℹ️ Info (IN-03) | Selected chips use M3 default lavender, off the documented palette; accepted by UAT test 8's pass, but note that test ran 2026-10-21T04:11Z, before 02-08 (17:15 +07) |
| `ServicePhotoStrip.kt` | 120-121 | `items(photoPaths)` lacks a key and re-derives `index` with `indexOf` | ℹ️ Info (carry-over from the prior report) | At most 5 paths; duplicates are not produced by the picker; no observed correctness impact |
| `ServiceDateField.kt` | 143-152 | Date picker passes Material millis directly instead of through the `ServiceDates` seam | ℹ️ Info (carry-over from the prior report) | Material's `selectedDateMillis` is already UTC start-of-day, consistent with `ServiceDates.toStorage`; the seam is tested |
| `App.kt` | 53 | KDoc typo "rende r" in an uncommitted working-tree change (IN-08, `deferred-items.md`) | ℹ️ Info | Comment-only; no behavioural effect; housekeeping for the file's owner — not a phase failure |

### Human Verification Required

#### 1. Re-run UAT test 1 against the gap-closure build (GAP 1 + GAP 2)

**Test:** On a device/simulator, open a vehicle's history → **Add service**. Tap **Add custom…**, enter `Battery replacement`, confirm. Then tap three preset chips in a chosen order, deselect the middle one, and re-select it. Save. Open the entry's detail, go back to the history, and re-open the add form once to observe the learned chip.

**Expected:** The custom label appears immediately as a **selected** chip with the `Check` glyph, and can be deselected and re-selected without vanishing; several chips can be selected at once and Save stays disabled only while none is selected; the saved detail shows the labels joined by ` · ` in tap order on at most two lines, and the history row shows the same string on one line with ellipsis; the custom label appears as a learned chip on the next add.

**Why human:** Both ViewModel → state transitions are proven by executable tests (`ServiceEntryEditViewModelCustomTypeTest` 5/5, `ServiceEntryEditViewModelStateTest` 5/5 both platforms, `ServiceTypeSelectionTest` 10/10), and the storage/read path by `ServiceEntryRepositoryTest` 13/13, `ServiceEntryMigrationTest` 2/2 and `ServiceTypeLineTest` 6/6. The remaining link — state → Compose recomposition → visible selected chip / joined pixels — has no screen-level test and cannot be proven by grep. UAT test 1 was answered before plans 02-07/02-08 existed, so its `issue` record does not evaluate the fixes; it must be re-run, not re-read.

### Gaps Summary

No must-have is missing, stubbed, or unwired. Both UAT gaps now have code fixes with executable proofs up to the ViewModel/state and data layers, plus a migration proof for the schema change. The 39 truths verified in the previous report remain green against HEAD `97f4fab` — no regressions. Four warning-level review findings (WR-01…WR-04) are recorded above; none falsifies a must-have, and all are narrow edge cases outside the ROADMAP contract. The only outstanding item is the runtime re-confirmation of the two fixes on a device, which is inherently human: **status `human_needed`, score 52/52**.

---

_Verified: 2026-10-21T10:57:46Z_
_Verifier: the agent (gsd-verifier)_
