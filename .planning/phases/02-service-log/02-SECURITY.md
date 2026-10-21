---
phase: 2
slug: service-log
status: verified
threats_open: 0
asvs_level: 1
created: 2026-10-21
---

# Phase 2 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.

**Scope:** retroactive verification of the 50 threats declared across the eight phase-2
`<threat_model>` blocks (`02-01-PLAN.md` … `02-08-PLAN.md`) against the implemented code at
`a22c3c2`. 41 `mitigate` threats were verified by code inspection plus test evidence; 9 `accept`
threats are recorded below. No new threat hunt was performed.

**Path convention:** source paths are relative to `shared/src/` unless stated otherwise.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| UI input → SQL layer | Every user-entered value crosses into SQLite; untrusted for injection purposes even in a local app. | Free text, digits, labels |
| DB rows ↔ filesystem | `service_photo.photo_path` references files outside the database; delete must keep both sides consistent. | Relative photo paths, personal images |
| `.sq` schema ↔ `1.sqm`/`2.sqm` ↔ committed `*.db` | Three artifact sets the build compares; drift silently disables the migration guarantee. | Schema DDL, migration SQL |
| Build machine → Maven Central | One third-party artifact (`kotlinx-datetime`) is downloaded and executed by the build. | Supply-chain code |
| Stored epoch millis ↔ civil dates | A wrong conversion policy silently corrupts every displayed/stored date. | Service dates |
| Gallery picker → app-private storage | Picked bytes cross into `PhotoStorage`; a generated file name and relative path leave it. | Image bytes, file path |
| User tap → irreversible data loss | The destructive button plus its dialog are the only gate before permanent row **and** file removal. | Entry/vehicle identity |
| Parent row → child rows | Foreign keys are declared but never enabled; only explicit SQL prevents orphans. | Entries, parts, photos, types |
| History data → vehicle Detail | An aggregate read runs on a screen that otherwise reads only the vehicle row. | Entry count, newest date |
| Custom-dialog text → chip list → stored types | A confirmed label must become both a selection and a rendered chip, and must survive storage deduplicated. | User label |
| Docs → implementation | A stale UI-SPEC rule or an unannotated D-26 would authorize a regression to single-select. | Design authorization |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-02-01-01 | Tampering | `ServiceEntryRepositoryImpl` queries | mitigate | 21 compile-time named queries with `?` binds only (`commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq:54-149`); every call passes named arguments (`commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt:124-197`). Grep: no `rawQuery`/`executeQuery`/string-built SQL in `data/` or `ui/`. | closed |
| T-02-01-02 | Tampering | `ServiceEntry.sq` vs `1.sqm` drift | mitigate | Normalized DDL diff of the three v2 tables + four indices is empty; `1.sqm:5-35` mirrors `ServiceEntry.sq:6-52`; committed `databases/2.db` holds exactly those tables; `schemaOutputDirectory` enables `verifyCommonMainVehiCareDatabaseMigration` (`shared/build.gradle.kts:101-109`, exit 0 recorded in 02-01/02-05/02-06/02-08 SUMMARY). | closed |
| T-02-01-03 | Information Disclosure | orphaned child rows/files after delete | mitigate | Paths selected inside the transaction, rows deleted in order, files handed to the deleter only after commit (`ServiceEntryRepositoryImpl.kt:184-197`). Tests: `deleteRemovesEntryAndChildrenAndHandsEveryPhotoPathToTheDeleter` (ServiceEntryRepositoryTest:253) and `deleteEntryRemovesRowsAndPhotoFiles` (ServiceEntryDeletionTest:105, asserts row + file absence). | closed |
| T-02-01-04 | Denial of Service | concurrent driver access on iOS | mitigate | One injected lane `Dispatchers.IO.limitedParallelism(1)` (`ServiceEntryRepositoryImpl.kt:39`) used by **all eight** methods — `observeHistory`/`Entry`/`Summary`/`LearnedTypes`/`LatestMileage` via `mapToList/mapToOne(ioDispatcher)` (:48-103), `observePreviousMileage`/`save`/`delete` via `withContext(ioDispatcher)` (:105-197). Reads ride the lane too. | closed |
| T-02-01-05 | Repudiation | lost write atomicity | mitigate | Whole save is one `transactionWithResult` including `lastInsertId()` and every child insert (`ServiceEntryRepositoryImpl.kt:124-182`); edit replaces children at :153-156. Test `saveWithAnExistingIdReplacesChildrenAndKeepsTheId` (ServiceEntryRepositoryTest:78). | closed |
| T-02-01-SC | Tampering | package installs | accept | No install in 02-01; dependency lands in 02-02 under the Package Legitimacy Audit. | closed |
| T-02-02-01 | Tampering | kotlinx-datetime install | mitigate | Pinned exactly `0.8.0` (`gradle/libs.versions.toml:20`) through the plain module `org.jetbrains.kotlinx:kotlinx-datetime` (:56), consumed at `shared/build.gradle.kts:69`; no `-0.6.x-compat` variant present. | closed |
| T-02-02-02 | Tampering | date conversion (zone mixing) | mitigate | Single seam: `today()` system zone, `toStorage`/`fromStorage` UTC start-of-day (`commonMain/kotlin/com/vehicare/util/ServiceDates.kt:35-51`). No `dayOfMonth`/`monthNumber` anywhere (grep). Tests `aCivilDateRoundTripsThroughStorageUnchanged`, `storageIsUtcStartOfDay`, `displayReadsStoredMillisBackAsTheSameCivilDate` (ServiceDatesTest:18,37,56). | closed |
| T-02-02-03 | Repudiation | numeric formatting drift | mitigate | One pure-Kotlin formatter behind one private `groupDigits` (`commonMain/kotlin/com/vehicare/util/RupiahFormat.kt:20-43`); no `Locale`/`NumberFormat`/`DecimalFormat`/`String.format` (grep). Tests `rupiahUsesDotGroupingAndNoDecimals`, `mileageUsesTheSameDotGroupingAsRupiah` (RupiahFormatTest:14,24). | closed |
| T-02-02-04 | Denial of Service | malformed `strings.xml` | mitigate | `xmllint` parses `commonMain/composeResources/values/strings.xml` clean; every format arg is positional (`%1$s`, `%1$d`) — no bare `%s`/`%d`; UI build proven by `:androidApp:assembleDebug` exit 0 (02-02-SUMMARY Verification). | closed |
| T-02-03-01 | Tampering | free-text inputs | mitigate | Caps defined at `ui/service/ServiceEntryFormValidation.kt:60-64` and enforced at every input boundary: notes 500 (`ServiceEntryEditScreen.kt:389`), workshop 120 (:420), mechanic 120 (:434), part name 120 (`components/PartsEditor.kt:82`), custom label 60 (`components/ServiceTypeDialog.kt:69`); blank part names dropped and text trimmed in `toServiceEntry` (:99-112). Downstream binds typed (T-02-01-01). | closed |
| T-02-03-02 | Tampering | photo path traversal | mitigate | File name generated from numeric ids/stamp only (`ui/service/ServiceEntryEditViewModel.kt:388-393`), stored under `service_entries/` (`PhotoStorage.kt:33,60-66`), resolved via `PlatformFile(FileKit.filesDir, relativePath)` (`PhotoStorage.kt:74-75`). Only production call sites pass generated names (`ServiceEntryEditViewModel.kt:291`; vehicle path unchanged at `VehicleEditViewModel.kt:229`). | closed |
| T-02-03-03 | Denial of Service | unbounded inputs | mitigate | `sanitizeMileage` ≤ 9 digits and `sanitizeCost` ≤ 12 (ServiceEntryFormValidation.kt:77,82, applied at EditScreen:283,364); 5-photo ceiling (`components/ServicePhotoStrip.kt:174`); single-flight import (`ServiceEntryEditViewModel.kt:285`); downscale 1280 px / JPEG 80 (`PhotoStorage.kt:41-44,98-106`). Tests `sanitizeMileageKeepsOnlyDigitsUpToNine`, `sanitizeCostKeepsOnlyDigitsUpToTwelve`. | closed |
| T-02-03-04 | Information Disclosure | orphaned session photo files | mitigate | Two explicit sets: `supersededPaths` (row-owned, ServiceEntryEditViewModel.kt:122) and `persistedButUnsavedPaths` (session-owned, :129). Removal deletes session-owned immediately / queues row-owned (:317-332); `onCleared` deletes only session-owned (:379-382); successful save deletes superseded only after commit (:396-400); save failure re-adds session files and leaves superseded untouched (:262-269). | closed |
| T-02-03-05 | Elevation of Privilege | gallery permission surface | accept | Gallery-only picker `FileKitType.Image` (`components/ServicePhotoStrip.kt:83`); no camera entry point and no runtime permission added (grep: no `camera`/`CAMERA`/`READ_MEDIA`/`NSPhotoLibrary` in `shared/src` or `androidApp/src`). | closed |
| T-02-03-06 | Repudiation | silent loss of in-progress edit | mitigate | `initialLoadApplied` gate accepts only the first emission (`ServiceEntryEditViewModel.kt:111,171`); late emissions return early before touching state. | closed |
| T-02-04-01 | Tampering | `VehicleRepositoryImpl.delete` cascade ordering | mitigate | Children before parent in one transaction: photos → parts → types → entries → vehicle (`VehicleRepositoryImpl.kt:77-88`). Test `vehicleCascadeRemovesAllEntriesAndTheirPhotosAndVehiclePhoto` (ServiceEntryDeletionTest:350) asserts zero entry/part/photo/type rows for the doomed vehicle (:411-438). | closed |
| T-02-04-02 | Information Disclosure | entry photo files surviving a delete | mitigate | Paths collected in the transaction (`VehicleRepositoryImpl.kt:77-87`) and deleted only after it returns (:89); same pattern for an entry (`ServiceEntryRepositoryImpl.kt:184-196`). Test asserts vehicle + both entry files non-existent and the survivor's file present (:441-452); entry case at :137-144. | closed |
| T-02-04-03 | Repudiation | double-confirm enqueuing a second delete | mitigate | `if (deleting.value) return` at top of `delete` (`ServiceEntryDetailViewModel.kt:107`); destructive button disabled while true (`ServiceEntryDetailScreen.kt:192,296`). Test `deleteDropsSecondCallWhileFirstInFlight` proves exactly one repository call and no second callback (ServiceEntryDeletionTest:248). | closed |
| T-02-04-04 | Elevation of Privilege | delete path bypassing confirmation | mitigate | Dialog KDoc names both ViewModels as the only destructive path (`ui/components/ConfirmDeleteDialog.kt:21-25`); `onDismissRequest = onDismiss` (:60) so back/outside/Escape cancel. Grep: the only `viewModel.delete` call sites are inside each screen's dialog `onConfirm` (`ServiceEntryDetailScreen.kt:206`, `VehicleDetailScreen.kt:232`). | closed |
| T-02-04-05 | Denial of Service | partially applied delete | mitigate | All row removal in one transaction (`ServiceEntryRepositoryImpl.kt:184-194`); files only after commit (:195-196). Failure path clears the in-flight flag, sets the flag, never calls `onDeleted`, never optimistically removes UI (`ServiceEntryDetailViewModel.kt:117-120`); test `aFailedDeleteKeepsTheEntryAndReportsFailure` (ServiceEntryDeletionTest:296). | closed |
| T-02-04-06 | Information Disclosure | entry data leaking into logs/errors | mitigate | `deleteFailed` is a `Boolean` (`ServiceEntryDetailViewModel.kt:83-89`) rendered as fixed copy (`ServiceEntryDetailScreen.kt:105,114`); grep finds no logging calls anywhere in `shared/src` (`println`/`Log`/`NSLog`/`Napier`/`Timber`). | closed |
| T-02-04-SC | Tampering | npm/pip/cargo installs | accept | No package/dependency added; build files changed only in commit `1de60f7` (02-02). | closed |
| T-02-05-01 | Tampering | list rows after a re-sort | mitigate | `items(state.entries, key = { entry -> entry.id })` (`ui/service/ServiceHistoryScreen.kt:141`), so identity moves with the entry, not the position. | closed |
| T-02-05-02 | Tampering | shared `EmptyState` copy regression | mitigate | Component takes `heading`/`body`/`actionLabel` as parameters and holds no string key (`ui/components/EmptyState.kt:38-45`); vehicle call site passes the original three keys (`VehicleListScreen.kt:92-98`), history passes its own (`ServiceHistoryScreen.kt:114-120`). | closed |
| T-02-05-03 | Denial of Service | per-entry reads on history path | mitigate | `observeHistory` composes entry rows with **one** vehicle-scoped type query and loads no parts/photos (`ServiceEntryRepositoryImpl.kt:43-60`); test `observeEntryAndHistoryHydrateEveryEntriesOrderedTypes` (ServiceEntryRepositoryTest:210). Query count constant in history length. | closed |
| T-02-05-04 | Repudiation | sort preference silently persisted | mitigate | Sort is `MutableStateFlow` in the VM only (`ServiceHistoryViewModel.kt:48-49,71-73`); grep finds no `Settings`/`Preferences`/`DataStore`/persistence/`rememberSaveable` use in the file (only KDoc mentions). | closed |
| T-02-05-05 | Information Disclosure | entry data leaking into logs/error copy | mitigate | `Error` carries only the flow's message (`ServiceHistoryViewModel.kt:104`); screen renders fixed `error_load_history` (`ServiceHistoryScreen.kt:157-163`); no entry field logged (no logging calls in source). | closed |
| T-02-05-SC | Tampering | npm/pip/cargo installs | accept | No package/dependency added; build files unchanged in this plan. | closed |
| T-02-06-01 | Denial of Service | per-entry reads on vehicle Detail | mitigate | Detail's only service read is `observeSummary` (`ServiceSummaryViewModel.kt:30-40`), backed by one aggregate `COUNT(*)` + `MAX()` query (`ServiceEntry.sq:88-90`); test `observeSummaryCountsEntriesAndReturnsTheMaxDate` (ServiceEntryRepositoryTest:124). | closed |
| T-02-06-02 | Tampering | stale aggregate rendering wrong sentence | mitigate | `null` renders the title alone (`ui/service/components/ServiceSummaryRow.kt:76`); zero renders `service_summary_none` (:100-102); positive count with null date returns just the count phrase (:108). | closed |
| T-02-06-03 | Repudiation | dead placeholder copy shipped | mitigate | `ComingSoonSlot` and `detail_service_history_coming_soon` are absent tree-wide (grep across `shared/src`, `androidApp`, `strings.xml`); file/call-site/string retired in the 02-06 commits per SUMMARY. | closed |
| T-02-06-04 | Information Disclosure | entry data leaking through summary path | mitigate | Only `entryCount` and `lastServiceEpochMs` cross the boundary (`domain/ServiceSummary.kt:10-13`); no logging calls; no message carries data (`ServiceSummaryViewModel.kt:18-23`). | closed |
| T-02-06-SC | Tampering | npm/pip/cargo installs | accept | No package/dependency added; build files unchanged in this plan. | closed |
| T-02-07-01 | Tampering | `2.sqm` vs `ServiceEntry.sq` DDL drift | mitigate | `service_entry_type` DDL in `2.sqm:10-15` is structurally identical to `ServiceEntry.sq:42-47` (normalized diff empty); committed `databases/3.db` contains that exact table; migration verify gate enabled via `schemaOutputDirectory` and reported exit 0 in 02-07/02-08 SUMMARY. | closed |
| T-02-07-02 | Tampering | legacy entries losing their type on upgrade | mitigate | Backfill `INSERT … SELECT id, 0, service_type FROM service_entry` (`migrations/2.sqm:17-18`); `migratingFromTwoBackfillsTheMirrorLabelAtPositionZero` (ServiceEntryMigrationTest:52) drops the table, inserts two legacy labels, migrates 2→3 and asserts row-for-row plus position 0. | closed |
| T-02-07-03 | Tampering | mirror/child divergence | mitigate | Mirror written only from `serviceTypes.first()` in `toBindParameters` (`data/ServiceEntryEntityMapper.kt:72-83`), called once per save (`ServiceEntryRepositoryImpl.kt:126,132,145`); tests `saveWritesOneOrderedTypeRowPerLabelAndMirrorsPositionZero` (:184) and `editingAnEntryReplacesItsTypeRows` (:197) assert child list **and** `selectById().service_type`. | closed |
| T-02-07-04 | Tampering | orphaned type rows after edit/delete | mitigate | Edit deletes types (:155) then re-inserts in the same transaction (:158-164); entry delete removes types (:191) before the parent (:192); vehicle cascade removes types (`VehicleRepositoryImpl.kt:84`) before entries (:85). Asserted by `editingAnEntryReplacesItsTypeRows`, `deletingAnEntryRemovesItsTypeRows` (ServiceEntryRepositoryTest:197,240) and the cascade test (:431-438). | closed |
| T-02-07-05 | Information Disclosure | erased vehicle leaving type text | mitigate | `deleteTypesForVehicle` runs inside the same cascade transaction (`VehicleRepositoryImpl.kt:84`); cascade test asserts the survivor's type rows are the only ones left (`selectTypes(survivorEntryId) == ["Brake service"]`, :447-451). | closed |
| T-02-07-06 | Tampering | duplicate learned chips from case variants | mitigate | `GROUP BY lower(label)` in `selectLearnedTypes` (`ServiceEntry.sq:96-100`); test `learnedTypesReadTheChildTableNewestFirstAndCollapseCaseVariants` seeds `Wheel alignment`/`wheel alignment` and expects one chip (ServiceEntryRepositoryTest:228-237). | closed |
| T-02-07-07 | Denial of Service | N+1 type reads on history path | mitigate | History hydrates through one `selectTypesForVehicle(vehicleId)` combined with the entry list (`ServiceEntryRepositoryImpl.kt:50-58`); no per-entry query exists. Test `observeEntryAndHistoryHydrateEveryEntriesOrderedTypes` (:210). | closed |
| T-02-07-08 | Elevation of Privilege | foreign keys trusted to cascade | accept | FKs declared for intent only; no `PRAGMA foreign_keys=ON` anywhere (grep hits only the explanatory comment at `ServiceEntry.sq:39`). Every delete is explicit SQL. | closed |
| T-02-07-SC | Tampering | npm/pip/cargo installs | accept | No package installed; uses the committed SQLDelight toolchain; build files unchanged in this plan. | closed |
| T-02-08-01 | Tampering | custom label selected but not renderable (GAP 1) | mitigate | `confirmCustomServiceType` returns selection **and** learned list (`ui/service/ServiceTypeSelection.kt:36-63`); `withConfirmedCustomType` writes both in one `copy` (:84-96); ViewModel delegates (:222-225); picker unions selected labels into options (`ServiceTypePicker.kt:74-81`). iosTest `customTypeConfirmationSelectsItAndMakesItARenderableChip` asserts both lists and a single renderable chip (ServiceEntryEditViewModelCustomTypeTest:65). | closed |
| T-02-08-02 | Tampering | case-variant duplicates | mitigate | Three layers: confirm collapses to preset/learned casing (`ServiceTypeSelection.kt:45-54`), toggle matches case-insensitively (:71-77), `toServiceEntry` applies `distinctBy { it.lowercase() }` (`ServiceEntryFormValidation.kt:121`). Tests `confirmingAPresetCaseVariantSelectsThePresetCasingAndAddsNoChip`, `confirmingALearnedCaseVariantSelectsTheStoredCasingAndKeepsOneChip`, `togglingAppendsThenRemovesCaseInsensitively`, `toServiceEntryKeepsSelectionOrderAndCollapsesCaseDuplicates`. | closed |
| T-02-08-03 | Tampering | saving an entry with zero types | mitigate | `isSubmittable` requires date + non-blank mileage + **non-empty** type list (`ServiceEntryFormValidation.kt:67-68`); Save button is driven by it (`ServiceEntryEditScreen.kt:463`) and `save()` re-checks the gate (`ServiceEntryEditViewModel.kt:244`). Test `isSubmittableRequiresDateMileageAndType` (ServiceEntryFormValidationTest:20). | closed |
| T-02-08-04 | Denial of Service | unbounded chip list growth | accept | Custom labels capped at 60 chars at the dialog boundary (`ServiceTypeSelection` consumer: `ServiceTypeDialog.kt:69`; constant `MAX_CUSTOM_TYPE = 60`); vocabulary is per user, no shared/server state. | closed |
| T-02-08-05 | Tampering | duplicated display separators | mitigate | `serviceTypeLine` defined once with the single `" · "` join (`ServiceEntryDetailScreen.kt:407`); history row imports and calls it without redefining (`ServiceHistoryRow.kt:27,56`); gate counts 1 definition and 1 `joinToString(" · ")`; `ServiceTypeLineTest` covers the separator. | closed |
| T-02-08-06 | Repudiation | design contract disagreeing with shipped behavior | mitigate | UI-SPEC states multi-select and supersedes the one-of rule (`02-UI-SPEC.md:193,286`); D-26 in `02-CONTEXT.md:22` preserves the original sentence **and** the "Superseded 2026-10-21" note; plan gates confirm `exactly one type is selected` no longer appears in the UI-SPEC (verified absent). | closed |
| T-02-08-SC | Tampering | npm/pip/cargo installs | accept | No package/dependency added; build files unchanged in this plan. | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-02-01 | T-02-01-SC | 02-01 adds no package; the phase's single dependency is introduced in 02-02 under the Package Legitimacy Audit verdict OK. | Phase 2 threat model (02-01-PLAN) | 2026-10-19 |
| AR-02-02 | T-02-03-05 | Gallery-only picker (D-04) — no camera entry point, no new runtime permission; the OS photo picker is the control. | Phase 2 threat model (02-03-PLAN) | 2026-10-19 |
| AR-02-03 | T-02-04-SC | 02-04 installs no package and adds no dependency; no new supply-chain surface. | Phase 2 threat model (02-04-PLAN) | 2026-10-19 |
| AR-02-04 | T-02-05-SC | 02-05 installs no package; only the already-audited `kotlinx-datetime` is in scope. | Phase 2 threat model (02-05-PLAN) | 2026-10-19 |
| AR-02-05 | T-02-06-SC | 02-06 installs no package and adds no dependency. | Phase 2 threat model (02-06-PLAN) | 2026-10-19 |
| AR-02-06 | T-02-07-08 | SQLite foreign keys are declared for intent but never enabled; all deletes are explicit SQL and are individually asserted. | Phase 2 threat model (02-07-PLAN) | 2026-10-21 |
| AR-02-07 | T-02-07-SC | 02-07 uses the committed SQLDelight toolchain only; no package install, no dependency change. | Phase 2 threat model (02-07-PLAN) | 2026-10-21 |
| AR-02-08 | T-02-08-04 | Chip-list growth is bounded by the user's own vocabulary; custom labels are capped at 60 chars; there is no shared or server state to exhaust. | Phase 2 threat model (02-08-PLAN) | 2026-10-21 |
| AR-02-09 | T-02-08-SC | 02-08 installs no package and adds no dependency. | Phase 2 threat model (02-08-PLAN) | 2026-10-21 |

*Accepted risks do not resurface in future audit runs.*

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-21 | 50 | 50 | 0 | gsd-security-auditor (OpenCode) |

### Audit Notes

- **Register origin:** all eight `PLAN.md` files carried a parseable `<threat_model>` authored at
  plan time (`register_authored_at_plan_time = true`); the compiled register matches the plans
  exactly (41 mitigate, 9 accept, 0 transfer).
- **Threat flags:** 11 `threat_flag` rows exist across `02-03`…`02-06` SUMMARY files; `02-07` and
  `02-08` declare "None". All 11 map to existing threat IDs (`photo_lifecycle`→T-02-03-04,
  `custom_type_collapse`→T-02-08-02, `delete_cascade`→T-02-04-01,
  `single_flight_delete`→T-02-04-03, `confirmation_gate_bypass`→T-02-04-04,
  `keyed_rows`→T-02-05-01, `sort_not_persisted`→T-02-05-04,
  `shared_empty_state_copy`→T-02-05-02, `aggregate_only_read`→T-02-06-01,
  `null_is_unresolved`→T-02-06-02, `dead_copy_retirement`→T-02-06-03).
  **Unregistered flags: 0.**
- **Review cross-reference (not threat findings):** `02-REVIEW.md` records four warning-level
  findings. None removes a declared control: WR-01 (prefill overwrite) leaves a confirmed label
  renderable through the picker's union guard required by T-02-08-01; WR-02 (ASCII-only SQL
  `lower()`) does not affect the declared `GROUP BY lower(label)` control for T-02-07-06; WR-03
  (stale-id save writes orphan type rows) is a path outside the register's edit/delete controls
  and remains a documented review item; WR-04 (tie order) does not bear on any register row.
  They are carried as review debt, not as open threats.
- **Verification method:** every `mitigate` row above cites the enforcing line(s) and, where the
  plan promised a test, the asserting test name. No implementation file was modified.
- **Working-tree state:** `shared/src/commonMain/kotlin/com/vehicare/App.kt` carries a pre-existing
  uncommitted one-character KDoc typo (`rende r`), already recorded as IN-08 in `02-REVIEW.md`.
  It is comment-only and no audited control depends on it; the audit left it untouched.
- Phase 2 is not yet marked complete (one human UAT re-test outstanding). That affects completion
  status, not this audit; the code under audit is at `a22c3c2` plus that comment-only edit.

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-21
