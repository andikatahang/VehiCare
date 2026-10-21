---
phase: 02-service-log
reviewed: 2026-10-21T10:27:43Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq
  - shared/src/commonMain/sqldelight/com/vehicare/db/migrations/2.sqm
  - shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryEntityMapper.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepository.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceHistoryRow.kt
  - shared/src/commonTest/kotlin/com/vehicare/data/ServiceEntryEntityMapperTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryFormValidationTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceHistoryViewModelTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeSelectionTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelStateTest.kt
  - shared/src/commonTest/kotlin/com/vehicare/ui/service/ServiceTypeLineTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryRepositoryTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryDeletionTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt
  - shared/src/iosTest/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModelCustomTypeTest.kt
findings:
  critical: 0
  warning: 4
  info: 8
  total: 12
status: issues_found
---

# Phase 2: Code Review Report (gap-closure scope, 02-07 + 02-08)

**Reviewed:** 2026-10-21T10:27:43Z
**Depth:** standard
**Files Reviewed:** 24
**Scope:** `b112cc0..HEAD` — schema v3 (`service_entry_type` + `2.sqm`) and multi-select (`ServiceTypeSelection`, picker, joined type line)
**Status:** issues_found

## Summary

This review supersedes the 2026-10-20 report and covers only the 24 gap-closure files. No
**Critical** finding stands up to a reachability trace. The migration, the mirror invariant, the
GAP 1 render contract and the multi-select gate all hold; four **Warning**-level robustness and
determinism defects remain, concentrated in `observeLearnedTypes`/`save()` and in the add-mode
prefill.

What I proved rather than assumed:

1. **Migration atomicity.** `2.sqm`'s DDL + backfill are generated into
   `VehiCareDatabaseImpl.migrateInternal` (`if (oldVersion <= 2 && newVersion > 2)`), and both
   drivers run that inside a transaction: SQLiter's `NativeDatabaseConnection.migrateIfNeeded`
   wraps `upgrade(...) + setVersion(...)` in `withTransaction` (sqliter-driver 1.3.3 source,
   verified), and `AndroidSqliteDriver` delegates to the platform `SQLiteOpenHelper`, which wraps
   `onUpgrade` + `setVersion` in one transaction. A half-applied upgrade (table created, rows not
   backfilled, version unstamped) cannot survive, so "exactly once" holds. The backfill is also
   fail-closed on a re-run (composite-PK conflict), not silently duplicating.
2. **Position-0 mirror consistency.** All four write paths keep it: insert
   (`insertEntry` + `insertEntryType` in one transaction, `ServiceEntryRepositoryImpl.kt:128-164`),
   update (`updateEntry` + `deleteTypesForEntry` + re-insert, `:142-164`), entry delete
   (`deleteTypesForEntry` before `deleteEntry`, `:191-192`), vehicle cascade
   (`deleteTypesForVehicle` before `deleteEntriesForVehicle`, `VehicleRepositoryImpl.kt:84-85`).
   `toBindParameters()` reads `serviceTypes.first()` before any write, so an empty list aborts
   before the insert rather than writing an empty mirror. No path writes one without the other —
   except WR-03, which writes children without a parent.
3. **GAP 1 render contract.** Proof chain: dialog confirm → `ServiceEntryEditScreen.kt:214-217` →
   `ServiceEntryEditViewModel.kt:222-225` → `ServiceTypeSelection.kt:84-96` (writes
   `values.serviceTypes` **and** `learnedTypes` in one `copy`) → `ServiceEntryEditScreen.kt:350-357`
   → `ServiceTypePicker.kt:74-81` (`presets + learned + every selected label not already present`)
   → `:94` (`isSelected`). A confirmed label is either prepended to `learned` or already matched by
   a preset/learned chip, and the union is an independent second guard, so *selected ⇒ rendered*
   holds even if `learnedTypes` goes stale. It does go stale — see WR-01 — but the union covers the
   rendering, not the chip-list persistence.
4. **Multi-select gate.** Create and edit both gate on `ServiceEntryFormValidation.isSubmittable`
   (`:67-68`: date non-null, mileage non-blank, `serviceTypes.isNotEmpty()`), enforced twice — on
   the button (`ServiceEntryEditScreen.kt:463`) and again at the top of `save()`
   (`ServiceEntryEditViewModel.kt:244`), so the Snackbar's "Try again" path cannot bypass it after
   the last chip is removed. Deselecting the last chip disabling Save with no error line matches
   UI-SPEC line 265 explicitly ("the service type has **no** error line"), so the missing inline
   error is *not* a defect.
5. **Display degradation.** One type renders verbatim (`joinToString` on a single element returns it
   unchanged), many join on ` · ` (U+00B7 + single spaces); detail caps at 2 lines
   (`ServiceEntryDetailScreen.kt:240-246`) and the history row at 1 (`ServiceHistoryRow.kt:55-61`),
   both with `TextOverflow.Ellipsis`, matching UI-SPEC 228/242. The join is a constant, not a
   locale-formatted list, so there is no `Locale` dependency; bidi reordering of mixed-direction
   labels is inherent to the Unicode algorithm and correct for a single-paragraph `Text` — no defect
   found. See IN-06 for the one real weakness (an unescaped separator vs free-text labels).
6. **KMP / coroutine discipline.** No `android.*` or Android-only Compose import in any reviewed
   `commonMain` file; `ServiceEntryRepositoryImpl` keeps every read and write on the injected
   single-parallelism lane (`withContext(ioDispatcher)` around both `transactionWithResult` blocks,
   `mapToList/mapToOne(ioDispatcher)` on every flow), and the ViewModel never touches storage from
   `Main`.

No structural pre-pass (`<structural_findings>`) was supplied with this review, so there is no
`## Structural Findings (fallow)` section.

---

## Narrative Findings (AI reviewer)

### WR-01: The add-mode prefill overwrites `learnedTypes`, so a custom type confirmed before it lands loses its chip

**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt:142-151`
**Issue:** The init prefill snapshots `Ready` *after* both `first()` reads and then writes
`learnedTypes = learned` wholesale (`:150`), discarding anything `onCustomTypeConfirmed` added in the
meantime. The window is real: the confirmation is seconds-fast for the user but the prefill is two
DB round-trips, so a cold start or a slow first query loses the race. Consequence: the confirmed
label survives in `values.serviceTypes`, so `ServiceTypePicker`'s union keeps it rendered — but once
the user deselects it, the chip is gone from the list entirely and cannot be re-selected without
retyping it. That is the same class of symptom as GAP 1, one step later.
Evidence that the race was known rather than fixed: the regression test works around it instead of
covering it — `ServiceEntryEditViewModelCustomTypeTest.kt:67` says
`// Let the add-mode prefill land first, so it cannot overwrite the learned list afterwards.`
**Fix:** Merge instead of replace, keeping the session labels first and collapsing the same way the
picker compares (case-insensitively):

```kotlin
_uiState.value = current.copy(
    values = current.values.copy(
        mileage = current.values.mileage.ifBlank { latestMileage?.toString().orEmpty() },
    ),
    mileageBaseline = latestMileage,
    learnedTypes = (current.learnedTypes + learned).distinctBy { it.lowercase() },
)
```

### WR-02: Case-insensitive learned-type collapse is ASCII-only in SQL and Unicode-aware in Kotlin

**File:** `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq:96-100` (with `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt:94-98`)
**Issue:** `GROUP BY lower(label)` relies on SQLite's `lower()`, which folds **ASCII only**. Every
other boundary in this feature folds Unicode: `ServiceEntryFormValidation.kt:121`
(`distinctBy { it.lowercase() }`), `ServiceTypeSelection.kt:45-50` and `:72-74`
(`equals(..., ignoreCase = true)`). Labels are free text, so a non-ASCII pair is not hypothetical.
Reproduced on SQLite 3.54.0 with two rows `Épsilon` / `épsilon`: `GROUP BY lower(label)` returns
**two** rows, so `observeLearnedTypes()` (which only filters presets, `ServiceTypePresets.kt:31`)
emits two chips — while `toggleServiceType` treats them as one label, so tapping the second chip
silently *deselects* the first. The Kotlin layer is stricter than the storage boundary, and the
storage boundary is the one the review contract calls out.
**Fix:** Make the boundary Unicode-consistent in Kotlin, where the rest of the collapse already
lives:

```kotlin
override fun observeLearnedTypes(): Flow<List<String>> =
    database.serviceEntryQueries.selectLearnedTypes()
        .asFlow()
        .mapToList(ioDispatcher)
        .map { rows ->
            ServiceTypePresets.filterLearned(rows.map { it.toServiceType() })
                .distinctBy { it.lowercase() }   // match the form/picker fold, not SQLite's ASCII lower()
        }
```

### WR-03: `save()` reports success for a row that no longer exists and writes orphan type rows

**File:** `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt:142-164`
(with `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq:96-100`)
**Issue:** The update branch discards `updateEntry(...)`'s result (SQLDelight 2.4.1 mutators return
`QueryResult<Long>` = rows affected, verified in the generated `ServiceEntryQueries.kt`), then
unconditionally deletes and re-inserts parts/photos/**types** under `entry.id` and returns that id.
With a stale id the parent update affects 0 rows while children are written anyway — foreign keys
are never enabled (documented at `ServiceEntry.sq:38-39`), so nothing rejects them. The ViewModel
treats the returned id as success, navigates, and the user lands on `detail_entry_not_found`. The
orphan `service_entry_type` rows are then permanent: `selectLearnedTypes` has **no join to
`service_entry`**, so every one becomes a learned chip on all vehicles (`VehicleRepositoryImpl`'s
cascade cannot reach a row whose parent never existed). `selectTypesForVehicle` is scoped through
the parent, so history and detail stay clean — which is exactly why this is invisible.
Reachability: no in-app path leaves a stale edit screen alive (popping the destination clears the
ViewModel), so this is a robustness/data-integrity gap rather than a user-facing crash — hence
Warning, not Critical.
**Fix:** Fail closed on a 0-row update, and scope the learned query through the parent:

```kotlin
val updated = database.serviceEntryQueries.updateEntry(
    /* … unchanged arguments … */
    id = entry.id,
).value
check(updated > 0L) { "service entry ${entry.id} no longer exists" }
```

```sql
selectLearnedTypes:
SELECT label, MAX(entry_id) AS last_used_id
FROM service_entry_type
WHERE entry_id IN (SELECT id FROM service_entry)
GROUP BY lower(label)
ORDER BY last_used_id DESC;
```

### WR-04: Learned-chip order is unspecified when two labels were last used in the same entry

**File:** `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq:100`
**Issue:** `ORDER BY last_used_id DESC` has no tie-break, and ties are the common case — every label
that first appeared together in one multi-select entry shares `MAX(entry_id)` (multi-select makes
this *more* frequent than it was in v2). Reproduced on SQLite 3.54.0 with entry 1 holding
`(0,'Zeta'), (1,'Alpha')`: the output is `Zeta` before `Alpha`, i.e. `(entry_id, position)` scan
order — an artifact of the grouping strategy, not of the `ORDER BY`. Any plan/version change
reorders the chips, and the repository contract (`ServiceEntryRepository.kt:35-37`, "newest-used
first") says nothing about ties. The existing test cannot catch it:
`ServiceEntryRepositoryTest.kt:228-237` has exactly one tie and its loser (`Wheel alignment`) is
filtered out as a preset, so the assertion passes regardless of tie order.
**Fix:** Give the sort a deterministic second key (do **not** add a second `min()`/`max()` aggregate —
SQLite's bare-column rule for `MAX()` only applies when exactly one min/max aggregate exists):

```sql
ORDER BY last_used_id DESC, lower(label) ASC
```

Then add a two-labels-in-one-entry case to `learnedTypesReadTheChildTableNewestFirstAndCollapseCaseVariants`.

### IN-01: `errorFor` / `ServiceEntryFormError` are dead and semantically wrong

**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt:47, 71-72`
**Issue:** `errorFor` maps *every* non-submittable state — blank mileage, null date, **and** an empty
type selection — to the single enum member `MILEAGE_REQUIRED`. No production caller uses it (only
`ServiceEntryFormValidationTest.kt:51-62`), and the screen implements its own `mileageCleared` gate
(`ServiceEntryEditScreen.kt:277`) precisely because this helper cannot express the UI-SPEC rule. The
next caller inherits a "Enter the mileage." message for a missing service type, which UI-SPEC line
265 forbids.
**Fix:** Delete `errorFor` and `ServiceEntryFormError`, or make the result a set of failing required
fields (`Set<ServiceEntryRequiredField>`) so a caller cannot print the wrong sentence.

### IN-02: Dead parameter `onTypeDialogDismiss` on `ServiceEntryEditForm`

**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt:243` (declared), `:208` (passed)
**Issue:** The parameter is passed but never referenced in the form body — the dialog is hosted by
`ServiceEntryEditRoute` (`:212-220`), which owns `showTypeDialog` and closes it itself. The unused
parameter suggests a dismissed-elsewhere path that does not exist.
**Fix:** Drop the parameter from the signature and the call site.

### IN-03: Unused imports, and the picker's documented palette is never applied

**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt:26, 28, 30, 31, 32`; `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt:3, 41, 42, 46, 52, 54, 76`
**Issue:** Unused (import-only) symbols: `ServiceTypePicker` — `sp`, `Hairline`, `Mist`, `Paper`,
`Slate`; `ServiceEntryDetailScreen` — `background`, `contentDescription`, `semantics`, `sp`,
`Hairline`, `Mist`, `loading_service_entry`. (This is the previous report's IN-02 still present in
files 02-07/02-08 rewrote; the KDoc at `ServiceTypePicker.kt:56-57` mentions `Ink`/`Paper`/
`Mist`/`Slate`/`Hairline`, which is what made the imports look used.) The token imports being dead
is the symptom of a real gap: no `colors = FilterChipDefaults.filterChipColors(...)` /
`AssistChipDefaults.assistChipColors(...)` is passed anywhere in `renderChip` (`:141-166`) or the
`AssistChip` (`:109-129`), so both render Material's stock surface colours while the KDoc states
"Ink border/label on Paper … unselected chips are Mist/Slate/Hairline". The prose and the code
disagree, so the next reader cannot tell which one is the contract.
**Fix:** Remove the dead imports, then either apply the documented colours or correct the KDoc — do
not leave them contradicting each other.

### IN-04: Both hydration fallbacks silently substitute the mirror for missing child rows

**File:** `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt:57` and `:82`
**Issue:** `byEntry[row.id] ?: listOf(row.service_type)` (history) and
`typeLabels.ifEmpty { listOf(row.service_type) }` (entry) make "the child table lost this entry's
rows" indistinguishable from "this entry has one type". That is good for a legacy row and bad for
diagnosis: combined with WR-03 the fallback renders a plausible single-type entry instead of
surfacing a broken write, and it weakens `observeEntryAndHistoryHydrateEveryEntriesOrderedTypes`
(a multi-type entry whose rows vanished would still return one label). The mapper's KDoc
(`ServiceEntryEntityMapper.kt:25-26`) documents this as "pre-hydration fallback only", but the
repository contract (`ServiceEntryRepository.kt:16-20, 22-26`) never says the reader may substitute.
**Fix:** State the fallback in the repository KDoc (and in the `.sq` header next to the mirror
comment), so the substitution is a documented contract instead of an implicit one.

### IN-05: Combined reads can emit a fresh parent with a stale child list

**File:** `shared/src/commonMain/kotlin/com/vehicare/data/ServiceEntryRepositoryImpl.kt:48-59`, `:63-85`
**Issue:** Parent rows and `service_entry_type` rows are separate `asFlow()` queries joined with
`combine`. A save notifies both, so there is at least one emission pairing *new* `entries` with
*old* `types` (and the reverse) before the second query re-runs; `combine` publishes each step. The
window is tiny because both queries ride the same single-parallelism lane, but the transient value
is a wrong `serviceTypes` list (mirror-only for a new multi-type entry), not merely a stale one.
The same shape pre-dates this diff for parts/photos.
**Fix:** Either accept and document it, or read the parent+children in one transaction per emission
(`withContext(ioDispatcher) { transactionWithResult { … } }` inside a `flow { }` that is invalidated
by a single notification) so a combined emission is always self-consistent.

### IN-06: The ` · ` separator is unescaped, and labels are free text

**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt:407`
**Issue:** `serviceTypeLine` joins on ` · ` with no escaping or rejection, while a custom label is
arbitrary user text (`ServiceTypeDialog` caps length only) and legacy `service_type` values from the
v2 backfill are equally arbitrary. A label typed as `Wheel · alignment` renders
`Wheel · alignment · Battery`, which is indistinguishable from three types — including in the
history row where the 1-line ellipsis truncates the evidence. `ServiceTypeLineTest.kt:39-45`
asserts "splitting on the separator yields exactly one part per type", a property that only holds
for labels that do not contain the separator.
**Fix:** Reject/replace the separator in `confirmCustomServiceType` (`ServiceTypeSelection.kt:42-47`
is the single boundary), or drop the round-trip assertion in the test and document the ambiguity.

### IN-07: Custom-label normalization is length-only at the single boundary

**File:** `shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceTypeSelection.kt:42-47`
**Issue:** `confirmCustomServiceType` trims and rejects blank, but performs no interior-whitespace
or control-character normalization; `MAX_CUSTOM_TYPE` is enforced earlier by
`ServiceTypeDialog.kt:69` (`take(60)`) and never here, so any other caller of the public
`onCustomTypeConfirmed` bypasses the cap. A pasted multi-line value survives into storage and from
there into a chipped label and the joined detail/history line, defeating the `maxLines` contract.
(UI-level newline suppression in `singleLine` text fields is not a guarantee for the paste path,
which is why this belongs at the boundary rather than in the widget.)
**Fix:** Normalize once where the comment already claims the decision is made:

```kotlin
val trimmed = label.filterNot { it.isISOControl() }.replace(Regex("\\s+"), " ").trim()
    .take(ServiceEntryFormValidation.MAX_CUSTOM_TYPE)
```

### IN-08: Uncommitted stray edit in the working tree (`App.kt`)

**File:** `shared/src/commonMain/kotlin/com/vehicare/App.kt:53` (uncommitted; outside the reviewed file list)
**Issue:** The working tree has one unrelated modification — "both platform hosts rende r" — a
typo introduced into a KDoc comment. It is not part of `b112cc0..HEAD`, so it will ride along with
whatever the next commit touches.
**Fix:** `git checkout -- shared/src/commonMain/kotlin/com/vehicare/App.kt` before committing this
review artifact.

---

_Reviewed: 2026-10-21T10:27:43Z_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
