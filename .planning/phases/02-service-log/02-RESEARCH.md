# Phase 2: Service Log - Research

**Researched:** 2026-10-19
**Domain:** Kotlin Multiplatform service-log feature — SQLDelight migration + relational child tables, multi-photo filesystem storage through the FileKit seam, derived service-type vocabulary, Compose Multiplatform form/history/detail UI on the Phase 1 design system
**Confidence:** HIGH for stack, schema, storage, and date conventions; MEDIUM for the remaining discretionary UX details (each flagged inline, all bounded by D-23…D-40)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Service Types
- **D-23:** The preset service types are exactly six, in this order: **Oil change · Tire rotation · Brake service · Inspection · Battery · Filters**. Stored as plain strings following the D-10 pattern, so adding a preset later needs no migration.
- **D-24:** Custom service types are free-text labels. Any distinct label already present on a logged entry becomes a **suggestion chip** on later entries, ranking newest-used first, after the six presets. The suggestion list is **derived from stored entries** — no managed type table and no Settings surface for types.
- **D-25:** Learned custom types are **shared across all vehicles** (one vocabulary for the whole app), not scoped to the vehicle that first used them.
- **D-26:** The service type picker is a **wrapping chip group**: presets first, then learned custom types, then an "Add custom…" chip that opens a one-field prompt. Exactly one type is selected and **no type is preselected** — Save stays disabled until one is chosen. Selecting is one tap.

#### Entry Form & Fast Capture
- **D-27:** Required to save an entry: **service date, service type, mileage**. Optional: cost, notes, parts, workshop, mechanic, photos.
- **D-28:** The mileage field **pre-fills with the most recent entry's mileage** for that vehicle (most recent by service date). Entering a lower value shows an inline warning ("Lower than the last recorded mileage — check this") but **does not block Save** — an odometer replacement legitimately resets the series.
- **D-29:** Cost is captured in **Rupiah, whole rupiah only (no decimals)**, displayed with dot thousands separators (e.g. `Rp 450.000`). It is stored as an **integer** (rupiah), never a floating-point value. Cost is optional.
- **D-30:** The entry form is a **single scrolling screen** (D-08 pattern) with the essentials first: **date → mileage → type → cost**, then notes, parts, workshop/mechanic and photos. The submit button sits at the bottom of the same column. There is no multi-step flow and no collapsed "more details" section.
- **D-31:** The fast path must be: open form (date = today) → tap a type chip → accept the pre-filled mileage → Save. This is the concrete reading of ROADMAP SC-1 ("under 30 seconds").

#### History & Entry View
- **D-32:** The full service history lives on a **dedicated Service history screen**. The vehicle Detail's `ComingSoonSlot` becomes a compact, tappable summary row (entry count + last service date) that opens it. The summary row does not duplicate the list.
- **D-33:** History rows use the app's established card language: **service type as the title**, a meta line **`date · mileage`**, and **cost trailing**. Tapping a row opens the entry detail screen.
- **D-34:** Sorting is a **two-segment control `Date | Mileage`** at the top of the history screen. Both directions are fixed descending (newest date / highest mileage). **Date is the default** and the choice is **not persisted** across visits.
- **D-35:** Tapping a history row opens a **read-only Service entry detail screen**: type + date + mileage as the focal block, then cost, parts, workshop/mechanic, notes and photos. **Edit** is a text action in the title bar; **Delete** is the last element behind a confirmation dialog — the same shape as the vehicle Detail screen (D-18). Delete copy is entry-specific ("Delete service entry", "Delete this entry? This can't be undone.").
- **D-36:** The history screen owns the primary **"Add service" FAB**. Its empty state (vehicle with no entries) shows the standard message + an "Add service" CTA; the Detail summary row is navigation only.

#### Cross-Cutting (requirements, not re-discussed)
- **D-37:** Entries carry **one or more photos** (SVC-03). Photos remain **gallery-only** (carry D-04), persist through the single `PhotoStorage` seam, and the database stores **relative paths only** (D-05). Deleting an entry deletes its photo files (D-07). The exact storage shape (child table vs. another representation) is agent discretion — a child table keyed by entry id is the expected default.
- **D-38:** All new schema ships in a **`1.sqm` migration** with a regenerated `2.db` baseline; the CI guard (`generateCommonMainVehiCareDatabaseSchema` + `git diff --exit-code`) must stay green (DAT-04). The new table is `service_entry` keyed by `vehicle_id`.
- **D-39:** Deleting a vehicle must delete **its service entries and their photo files** as well as the vehicle photo (VEH-04, "all its records"). The existing vehicle delete path must be extended, not bypassed.
- **D-40:** Service type is a **stable plain string** — there is no rename operation and no type registry. Phase 3 reminder rules will key on that same string, so the stored value must never be silently rewritten.

### the agent's Discretion

These were not discussed; decide them during research/planning, bounded by the requirements and the Phase 1 decisions:

- **Date input:** material3 date picker, defaulting to today; backdating is allowed (research rates it onboarding-critical) and no date-range restriction is required.
- **Parts capture:** repeating inline rows inside the form (part name + quantity), quantity defaulting to 1 and numeric; add-row / remove-row controls. SVC-02 requires name + quantity, so no free-text-only parts blob.
- **Workshop/mechanic:** two optional free-text fields (SVC-04 names both); no suggestion/autocomplete behaviour unless research shows it is cheap.
- **Notes:** optional multi-line free text.
- **Photo count / UX:** maximum count, thumbnail-strip layout, add/remove affordances and whether a full-screen viewer ships — decide against SVC-03 and Phase 1's photo patterns; do not add camera capture.
- **Entry detail delete copy** wording beyond the two locked strings above.
- **History empty-state wording** and the summary-row wording on Detail.
- **Sort tie-breaking** (e.g. equal dates ⇒ id descending) and the exact SQL for the two orders.
- **How the learned-type list is queried** (e.g. distinct types ordered by most recent use) and whether a custom label that matches a preset case-insensitively is collapsed onto that preset.

### Deferred Ideas (OUT OF SCOPE)

- **Search / filter history** — research rates it P2; not part of SVC-05. Own future phase.
- **Cost totals / spending analytics** — v2 (COST-01/COST-02); Phase 2 shows per-entry cost only.
- **PDF export of service history** — v2 (EXP-01).
- **Managed service-type list in Settings** (add/rename/delete) — considered and rejected in favour of learned suggestions. Revisit only if the learned list becomes unwieldy.
- **Extended preset list (transmission, coolant, spark plugs, alignment)** — not selected now; new presets can be added later without a migration (D-23/D-10 pattern).
- **Reminders per service type** — Phase 3; reminder rules will key on the same stored type string (D-40).

None of these block Phase 2 scope.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| SVC-01 | User can create a service entry with date, mileage, service type, cost, and notes | Schema (Pattern 1), form single-column order with date→mileage→type→cost first (D-30), UTC start-of-day date convention (Pattern 4), mileage prefill + warning (D-28), integer-Rupiah cost (Pattern 7), parts/photos optional fields; single `ServiceEntryEditRoute` destination for add and edit |
| SVC-02 | User can add parts replaced or repaired to a service entry (part name, quantity) | `service_part` child table with `position` (Pattern 1), optional rows dropped on save (D-27 "parts optional"), quantity default 1 / min 1, inline add/remove rows |
| SVC-03 | User can attach one or more photos (receipts, work done) to a service entry | `service_photo` child table + `PhotoStorage` directory parameter (Patterns 1/5), max 5 photos, thumbnail strip with per-photo remove, no viewer (discretion call), relative paths only (D-37/D-05), files deleted with entry (D-07) |
| SVC-04 | User can record the workshop name and mechanic for a service entry | Two optional free-text columns `workshop` / `mechanic` on `service_entry`; `FieldRow` reuse on detail |
| SVC-05 | User can view the full service history for a vehicle, sorted by date or mileage | Dedicated history screen (D-32), two SQL orders with deterministic tie-breaks (Pattern 6), `Date \| Mileage` segmented control defaulting Date and not persisted (D-34), `LazyColumn` keys + fixed header, vehicle-Detail summary row fed by one aggregate query (no N+1, PITFALLS SQL 3) |
| SVC-06 | User can edit an existing service entry | Same destination as add (`entryId == 0L` add / `> 0` edit, mirrors D-08), update path replaces child rows inside one transaction (Pattern 2), photo bookkeeping generalized per path (Pattern 5), mileage warning compared against the previous entry in Date order (discretion: tie-break id descending) |
| SVC-07 | User can delete a service entry | Detail screen delete-last + generalized confirm dialog with the locked copy (D-35), single-flight ViewModel guard, read photo paths → delete rows → delete files ordering (mirrors `VehicleRepositoryImpl.delete`), shell-hosted snackbar "Service entry deleted." because the source screen pops |
</phase_requirements>

## Summary

Phase 2 turns the reserved "Service history (coming soon)" slot on the vehicle Detail screen into a complete per-vehicle service log: create/view/edit/delete entries with parts, photos, workshop/mechanic, and a sortable full history (SVC-01…SVC-07). Research focused on where this feature could break the Phase 1 architecture rather than on what to build — the feature set and interaction shapes are already locked by D-23…D-40, so the job was to find the load-bearing conventions (schema + migration, storage shape, date semantics, list/scroll rules) and verify them against the actual pinned library versions.

The feature fits the existing architecture with **exactly one new dependency**: `org.jetbrains.kotlinx:kotlinx-datetime:0.8.0`. The schema is a new `service_entry` table plus two ordered child tables (`service_part`, `service_photo`) shipped in `migrations/1.sqm` with a regenerated `2.db` baseline; SQLite foreign-key enforcement is **off** in this app (no `PRAGMA foreign_keys` anywhere in `shared/src`), so every cascade — entry delete, and vehicle delete via the extended `VehicleRepositoryImpl.delete` — must be explicit deletes inside the existing `transactionWithResult` on the serialized IO lane. Dates are stored as **UTC start-of-day epoch millis**; "today" is read from the **system time zone** — that asymmetry is intentional and is the single most likely off-by-one bug if a plan gets it wrong. Learned service types need no new table: one `GROUP BY lower(service_type)` query (verified empirically against sqlite3 3.54.0) returns the newest row per distinct type, filtered against the six presets in Kotlin.

The riskiest details are the ones with no compiler help: the `1.sqm`/`2.db` migration contract in CI, multi-photo lifecycle bookkeeping (generalizing Phase 1's `supersededPath` / `persistedButUnsaved` rule per path), iOS keyboard/IME behavior on the single scrolling form, and `LazyColumn` keys under re-sorting. Everything else rides on verified Phase 1 patterns (mapper boundary, serialized IO, one-shot failure flags, typed routes, Koin `viewModel { (id: Long) -> … }` factories). The material3 version is a pinned alpha (`1.12.0-alpha03`); the date-picker and `FlowRow` APIs were verified against source, not assumed.

**Primary recommendation:** Build the service log as `service_entry` + ordered `service_part` / `service_photo` child tables shipped in `1.sqm` (regenerate and commit `2.db`), persist through one `ServiceEntryRepositoryImpl` on the existing serialized IO lane with explicit child deletes, store dates as UTC start-of-day millis via a `ServiceDates` util over kotlinx-datetime 0.8.0, and build the three Compose screens from Phase 1 tokens/components — no other stack additions.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Entry form state, validation, 30-second fast path | Client (Compose commonMain) | Presentation (ViewModel) | Interaction design + gating (D-26/D-27/D-31); Save enablement is UI state |
| Civil date ↔ epoch-millis normalization | Shared domain util (`ServiceDates`) | ViewModel | One conversion policy prevents mixed-zone bugs in form, history, and detail |
| Learned type vocabulary | Data (SQL over `service_entry`) | Client (preset filtering, chips) | Derived from stored entries (D-24); no registry, no service |
| Entry persistence incl. parts + photos | Data (`ServiceEntryRepositoryImpl` + SQLDelight transaction) | Filesystem util (file writes) | Multi-write atomicity per PITFALLS SQL 4; rows and files have different durability rules |
| Photo files | Filesystem util (`PhotoStorage`) | Data (relative path only) | Single-seam rule (D-37/D-05); DB never stores absolute locations |
| History reads + sorting | Data (SQL `ORDER BY`, deterministic tie-breaks) | Client (segmented control state) | Sorting is deterministic and testable in SQL; the UI only selects the order |
| Rupiah formatting | Client util (pure Kotlin) | — | Display-only concern; invariant format (`Rp 450.000`), integer storage (D-29) |
| Delete flows (entry / cascade on vehicle) | Data (explicit deletes in one transaction) | Filesystem util (file deletion after commit) | Read-paths → delete-rows → delete-files ordering, mirrors Phase 1 vehicle delete |
| Vehicle-Detail summary row | Client (row) | Data (one aggregate query) | Count + last date in a single query to keep the Detail read free of N+1 (PITFALLS SQL 3) |

## Standard Stack

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.4.20 | Language/toolchain | Version set in force (STATE); `kotlin.time.Clock`/`Instant` already used by `VehicleEditViewModel` |
| Compose Multiplatform | 1.12.1 | Shared UI (form, history, detail) | Phase 1 foundation; one UI for both platforms |
| Material3 | 1.12.0-alpha03 | DatePicker/Dialog, FilterChip, Scaffold/FAB, SegmentedButton | Pinned catalog version; date-picker + chip APIs verified against `compose-multiplatform-core` source |
| SQLDelight | 2.4.1 | Schema, `1.sqm` migration, typed queries | Phase 1 convention; CI migration guard (DAT-04) |
| SQLDelight coroutines-extensions | 2.4.1 | `asFlow().mapToList(ioDispatcher)` reactive reads | Serialized-lane reads pattern |
| kotlinx-coroutines | 1.11.0 | IO lane, Flow, `stateIn` | `Dispatchers.IO.limitedParallelism(1)` DB lane |
| Koin | 4.2.2 | Repository + ViewModel wiring | `appModule` convention; route ids via `viewModel { (id: Long) -> … }` |
| Navigation Compose | 2.9.2 | Typed `@Serializable` routes | Single `NavHost` in `App.kt` |
| kotlinx-serialization | 1.11.0 | Route serialization | Existing `Routes.kt` pattern |
| **kotlinx-datetime (NEW)** | **0.8.0** | Civil date ⇄ epoch millis, `todayIn`, `MonthNames` | JetBrains-official (org `kotlinx`, Apache-2.0); exactly the D-28/D-33 date conversions; verified on Maven Central, published **2026-05-07** |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| FileKit | 0.16.0 | Gallery pick + JPEG 1280px compression | Already inside `PhotoStorage`; multi-photo reuses it unchanged |
| Coil | 3.6.3 | Thumbnail decode from `PhotoStorage.resolve(path)` | History/detail thumbnail strip (`VehicleCard` precedent) |
| material-icons-core | 1.7.3 | `Add`, `Close`, `DateRange`, `KeyboardArrowRight`, `Warning` | Chip add, photo remove, date field, chevrons, mileage warning — all verified present |
| Compose Foundation `FlowRow` | 1.12.1 (via CMP) | Wrapping service-type chip group (D-26) | `@ExperimentalLayoutApi`; verified present in `foundation-layout` |
| `kotlin.time.Clock` / `kotlin.time.Instant` (stdlib) | 2.4.20 | Monotonic photo-stamp clock; `Instant` for date conversions | Do **not** add the deprecated `kotlinx.datetime.Instant`/`Clock` typealiases |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| kotlinx-datetime 0.8.0 | Hand-rolled epoch-day math + month-name arrays | The math for start-of-day is simple, but month names, civil-date arithmetic, and future Phase 3 reminder windows are not; kotlinx-datetime is 1 dependency, no transitive runtime deps beyond stdlib/serialization-core |
| `service_photo` child table | Delimited path string on `service_entry` | Delimiters need escaping, ordering is implicit, and cascade deletes become string surgery; child table rows match the existing mapper/transaction pattern |
| `kotlinx-collections-immutable` | Read-only `List` + stable data classes + `LazyColumn` keys | PITFALLS CMP 3 is satisfied without the dependency; Phase 1 already ships read-only lists |
| `LocalDate.Format { … }` DSL | Manual `"${day} ${MonthNames…} ${year}"` | Both verified; the manual string has fewer moving parts for a single fixed format |
| Material3 common `DatePicker` | Platform-native pickers (Android `DatePickerDialog`, iOS `UIDatePicker`) | Would fork UI + state and break the single-screen-form convention; common picker verified on both targets |

**Installation:**

```toml
# gradle/libs.versions.toml
[versions]
kotlinx-datetime = "0.8.0"

[libraries]
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "kotlinx-datetime" }
```

```kotlin
// shared/build.gradle.kts — commonMain.dependencies
implementation(libs.kotlinx.datetime)
```

**Version verification (run 2026-10-19):**

| Command | Result |
|---------|--------|
| `curl repo1.maven.org/.../kotlinx-datetime/maven-metadata.xml` | `0.8.0` present in `<versions>`; note `<latest>`/`<release>` point at `0.8.0-0.6.x-compat` (a compat variant — use plain `0.8.0`) |
| `curl -sI .../0.8.0/kotlinx-datetime-0.8.0.pom` | `last-modified: Thu, 07 May 2026` (published 2026-05-07) |
| `gradle/libs.versions.toml` | All other versions above are the repo's pinned values (Phase 1 locked) |

## Package Legitimacy Audit

> `gsd-tools query package-legitimacy check` supports npm/pypi/crates only; the Maven ecosystem was verified manually against repo1.maven.org metadata and the official Kotlin org repository. Result is equivalent to an `OK` verdict.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `org.jetbrains.kotlinx:kotlinx-datetime` | Maven Central | Library since 2019; `0.8.0` published 2026-05-07 (~5 months) | Central does not publish counts; maintained by JetBrains (same `kotlinx` group as kotlinx-coroutines) | [github.com/Kotlin/kotlinx-datetime](https://github.com/Kotlin/kotlinx-datetime) – Apache-2.0, signed artifacts (`.asc` present) | OK | Approved |

**Packages removed due to [SLOP] verdict:** none
**Packages flagged as suspicious [SUS]:** none

*No other package is added by this phase — the remaining stack entries are already locked in the version catalog. The seam's `package-legitimacy check` does not cover Maven; the manual verification above (official group ID, official repo, license, artifact signatures, publish date) is the equivalent gate.*

## Architecture Patterns

### System Architecture Diagram

```
 Gallery pick (FileKit, gallery-only — D-04)
      │ bytes
      ▼
 PhotoStorage.persist(picked, name, "service_entries")  ──►  app-private file
      │ relative path (only this crosses the seam — D-05/D-37)
      ▼
 [Service entry form] ──Save──► [ServiceEntryEditViewModel] ──► [ServiceEntryRepositoryImpl.save]
   date = today (system zone)        Ready / Saving / saveFailed        withContext(IO.limitedParallelism(1))
   mileage prefill + warning ←──┐                                          │  transactionWithResult {
   type chips (FlowRow)         │                                          │    insert/update service_entry ─ lastInsertId()
   cost digits + Rp preview     │                                          │    replace service_part rows (position)
   notes / parts / workshop     │                                          │    replace service_photo rows (relative paths, position)
   photo strip (max 5)          │                                          │  }
                                │                                          ▼
                                │                            VehiCareDatabase (SQLite + WAL, schema v2 via 1.sqm)
                                │                                          │ invalidations
                                └────── query.asFlow().mapToList(io) ◄─────┘
                                                    │
 [History screen] ◄── observeHistory(vehicleId, sort) ── [ServiceEntryRepository]
   Date | Mileage segmented control (VM state, not persisted)   ├── observeSummary(vehicleId) ─► [Vehicle Detail summary row]
   LazyColumn(keys) + fixed header                              ├── observeLearnedTypes()
   FAB "Add service" ──► form (entryId = 0)                     └── observeEntry(id) = entry + parts + photos
        ▲                                                               │
        │                                                               ▼
        └──────────── Edit (title action) ────────────────── [Entry detail, read-only]
                                                                       │
                                                          Delete (last element + confirm — D-35)
                                                                       │
                                                          tx: read photo paths → delete parts/photos/entry
                                                          then PhotoStorage.delete(paths)  ──► snackbar on the shell

 [Vehicle delete] ──► VehicleRepositoryImpl.delete(id)  (extended — D-39)
        tx: read vehicle photo + ALL service photo paths → delete service photos/parts/entries → delete vehicle
        then delete every collected file
```

### Recommended Project Structure

```
shared/src/commonMain/
├── kotlin/com/vehicare/
│   ├── data/
│   │   ├── ServiceEntryRepository.kt            # interface, domain types only
│   │   ├── ServiceEntryRepositoryImpl.kt        # serialized IO lane + transactions
│   │   ├── ServiceEntryEntityMapper.kt          # generated row ↔ domain boundary
│   │   └── VehicleRepositoryImpl.kt             # EXTENDED: delete cascade (D-39)
│   ├── domain/
│   │   ├── ServiceEntry.kt                      # id, vehicleId, dateEpochMs, mileageKm,
│   │   │                                        #   serviceType, costRupiah?, notes?,
│   │   │                                        #   workshop?, mechanic?, parts, photoPaths
│   │   ├── ServicePart.kt                       # name, quantity (≥ 1)
│   │   └── HistorySort.kt                       # DATE | MILEAGE
│   ├── ui/service/
│   │   ├── ServiceEntryEditScreen.kt            # add (entryId = 0) + edit — one destination
│   │   ├── ServiceEntryEditViewModel.kt
│   │   ├── ServiceHistoryScreen.kt / ServiceHistoryViewModel.kt
│   │   ├── ServiceEntryDetailScreen.kt / ServiceEntryDetailViewModel.kt
│   │   └── components/
│   │       ├── ServiceTypePicker.kt             # FlowRow chips + "Add custom…" (D-26)
│   │       ├── PartsEditor.kt                   # inline rows (SVC-02)
│   │       ├── ServicePhotoStrip.kt             # thumbnails + add/remove (SVC-03)
│   │       ├── ServiceHistoryRow.kt             # VehicleCard card language (D-33)
│   │       └── HistorySortControl.kt            # Date | Mileage (D-34)
│   ├── ui/components/                           # MOVE the two generalized components here:
│   │   ├── ConfirmDeleteDialog.kt               #   parameterized copy, same guarantees (D-35)
│   │   └── EmptyState.kt                        #   parameterized message + CTA (D-36)
│   ├── util/
│   │   ├── PhotoStorage.kt                      # EXTENDED: directory parameter (D-37)
│   │   ├── ServiceDates.kt                      # date policy (new)
│   │   └── RupiahFormat.kt                      # pure formatter (D-29)
│   └── ui/navigation/Routes.kt                  # EXTENDED: three typed routes
└── sqldelight/com/vehicare/db/
    ├── ServiceEntry.sq                          # current schema + all queries
    └── migrations/1.sqm                         # v1 → v2 delta (D-38)

shared/src/commonTest/…        # mapper, ServiceDates, Rupiah, learned-filter unit tests
shared/src/iosTest/…           # real-driver repository / cascade / multi-photo tests
shared/src/androidDeviceTest/  # optional local migration + WAL checks (CI has no emulator job)
```

*Note:* `ConfirmDeleteDialog` and `EmptyState` currently live in `ui/vehicles/components/` with vehicle-specific copy. Prescription: **move both to `ui/components/` and parameterize them**, updating the two vehicle call sites in the same plan that first needs each — a generalized *sibling* that duplicates the guarantees is the anti-pattern (CONTEXT "Reusable Assets").

### Pattern 1: Migration-first schema change (D-38)

**What:** Add the three tables to the current-schema `.sq` file *and* the identical DDL to `migrations/1.sqm`, then regenerate the committed baseline.
**When to use:** Every schema change; this is the only path that keeps DAT-04 green.

```sql
-- shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm
-- Migrates v1 (vehicle only) → v2 (service log). File name = version being upgraded FROM.
CREATE TABLE service_entry ( … );   -- full DDL in Code Examples
CREATE TABLE service_part ( … );
CREATE TABLE service_photo ( … );
CREATE INDEX …;
```

Steps:
1. Author/refresh `ServiceEntry.sq` (current schema + queries) and `migrations/1.sqm` (same DDL).
2. Run `./gradlew generateCommonMainVehiCareDatabaseSchema` → writes `2.db` next to the existing `1.db`.
3. Commit `.sq`, `1.sqm`, and **`2.db`**. **Never delete `1.db`** — the verify task applies `1.sqm` to it; deleting it silently unverifies the migration.
4. CI guard must stay green: `verifyCommonMainVehiCareDatabaseMigration`, then `generateCommonMainVehiCareDatabaseSchema` + `git diff --exit-code -- shared/src/commonMain/sqldelight/databases` [CITED: .github/workflows/ci.yml].

**Why not `deriveSchemaFromMigrations`:** the repo's `.db` baselines are generated from `.sq` files (current setting); switching strategies is out of scope and would move the verification goalposts.

### Pattern 2: Parent + ordered children write in one transaction (PITFALLS SQL 4)

**What:** One `transactionWithResult` on the serialized lane writes the entry row, then its parts and photos with explicit `position` values; edit replaces all children.
**When to use:** Every save. `RETURNING` is unavailable at minSdk 26, so the new id comes from `lastInsertId()` **inside the same transaction** (Phase 1 precedent).

```kotlin
// Sketch — full example in Code Examples
database.transactionWithResult {
    val id = if (entry.id == 0L) {
        queries.insertEntry(/* binds */)
        queries.lastInsertId().executeAsOne()          // same connection, same transaction
    } else {
        queries.updateEntry(/* binds */)
        queries.deletePartsForEntry(entry.id)          // full replace keeps positions simple
        queries.deletePhotosForEntry(entry.id)
        entry.id
    }
    entry.parts.forEachIndexed { i, p -> queries.insertPart(id, p.name, p.quantity, i.toLong()) }
    entry.photoPaths.forEachIndexed { i, path -> queries.insertPhoto(id, path, i.toLong()) }
    id
}
```

### Pattern 3: Derived type vocabulary — no registry, no rewrites (D-24/D-25/D-40)

**What:** One cross-vehicle SQL query returns the newest entry per distinct type (case-insensitive), newest-used first; Kotlin filters out the six presets. Stored strings are never modified.
**Verified:** run against a scratch DB with sqlite3 3.54.0 — rows `Battery|5, Oil change|4, Tire rotation|3` for a mixed-case fixture; the returned label is the newest row's original casing.

```sql
selectLearnedTypes:
SELECT service_type, id AS last_used_id
FROM service_entry
WHERE id IN (SELECT MAX(id) FROM service_entry GROUP BY lower(service_type))
ORDER BY id DESC;
```

```kotlin
val learned = rows.map { it.service_type }
    .filterNot { name -> ServiceTypePresets.ALL.any { it.equals(name, ignoreCase = true) } }
```

**Collapse rule (discretion):** a custom label that equals a preset case-insensitively collapses onto the preset chip — it is filtered out of the learned chips and never rewritten in the database (D-40).

### Pattern 4: Dates — store UTC start-of-day, read "today" in the system zone

**What:** `service_date_epoch_ms` holds the **UTC start-of-day** millis of the chosen civil date. The *default* date ("today") is derived from the **system time zone**, then converted to UTC start-of-day. Display and edit read the millis back as a UTC civil date.
**Why this asymmetry:** "today" must match the user's wall calendar (a UTC-derived "today" shows yesterday for UTC+7 users before 07:00 local); storage must be zone-independent so a device time-zone change cannot shuffle history dates.

```kotlin
// util/ServiceDates.kt — full example in Code Examples
fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
fun toStorage(day: LocalDate): Long = day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()
fun fromStorage(epochMs: Long): LocalDate =
    Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date
fun display(day: LocalDate): String =
    "${day.day} ${MonthNames.ENGLISH_ABBREVIATED.names[day.month.number - 1]} ${day.year}"  // "19 Oct 2026"
```

*0.8.0 API notes (verified against tag `v0.8.0` sources):* `Instant` is `kotlin.time.Instant`; `dayOfMonth` → **`day`** and `monthNumber` → **`month.number`** are the current properties (`dayOfMonth`/`monthNumber` log WARNING deprecations); `MonthNames` exposes a 12-element `names: List<String>`. The Context7 docs for this library track `master`, which describes a **newer `TimeZoneContext` API that does not exist in 0.8.0** — do not copy it.

*Material3 date picker (verified against `compose-multiplatform-core` common source):* `rememberDatePickerState(initialSelectedDateMillis: Long?)`; `DatePickerState.selectedDateMillis` is already **UTC start-of-day** for the picked civil date; `DatePickerDialog(onDismissRequest, confirmButton, dismissButton, content)`; all of it is `@ExperimentalMaterial3Api`. The Android-only `LocalDate`-typed overloads are `@RequiresApi(26)` — the millis-based common API is the portable path (minSdk is 26, but common code has no `LocalDate` overload anyway).

### Pattern 5: Multi-photo lifecycle — the Phase 1 rule, generalized per path (D-07/D-37)

**What:** Phase 1's single-photo bookkeeping becomes two **sets** in the edit ViewModel. Vocabulary is already established in `VehicleEditViewModel` — extend it, don't invent a new one:

| Set | Ownership | Delete when |
|-----|-----------|-------------|
| `persistedButUnsavedPaths: MutableSet<String>` | Session | The pick is removed *or* the form is torn down without a durable save (`onCleared`) |
| `supersededPaths: MutableSet<String>` | Database row | Only after a **successful save** — until then the saved row still references each file |

Rules the plan must state task-by-task:
1. **Import:** persist first; only after the copy exists add the new path and route the removed old path (session-owned → delete now; row-owned → `supersededPaths`). One import at a time (`importing || saving` guard, mirroring Phase 1).
2. **Remove:** if the path is session-owned, delete the file immediately; otherwise add to `supersededPaths`.
3. **Save success:** clear `persistedButUnsavedPaths` (the row now owns them) and delete `supersededPaths` files.
4. **Save failure:** touch nothing — the form, files, and `supersededPaths` survive for retry.
5. **Abandon (`onCleared`):** delete only `persistedButUnsavedPaths`; never `supersededPaths`.
6. **Entry delete (repository):** read paths in the transaction, delete rows, delete files after — same ordering as `VehicleRepositoryImpl.delete`.
7. **Naming:** mirror `nextPhotoFileName()` — `"$entryId-$stamp.jpg"` with a monotonic stamp `maxOf(Clock.System.now().toEpochMilliseconds(), lastStamp + 1)` so a replaced photo is a genuinely new file and no image cache can serve the old one. Directory: `service_entries`.

**Discretion calls (record in plans):** maximum **5 photos** per entry (bounds form height and strip scroll; SVC-03 has no maximum); **thumbnail strip with per-photo remove**, no full-screen viewer (consistent with the minimal v1 camera-less photo patterns; revisit if receipts prove unreadable at thumbnail size); no camera capture (D-04 carry-forward).

### Pattern 6: History list — deterministic SQL orders, keys, no nested scroll (D-33/D-34, PITFALLS CMP 1/2)

**What:** Sort selection lives in a `MutableStateFlow<HistorySort>` in the ViewModel (default `DATE`, **not persisted**), `flatMapLatest` switches between two queries. Both orders end with `id DESC` so equal keys never reorder ambiguously across emissions.

```sql
selectForVehicleByDate:
SELECT * FROM service_entry WHERE vehicle_id = ?
ORDER BY service_date_epoch_ms DESC, id DESC;

selectForVehicleByMileage:
SELECT * FROM service_entry WHERE vehicle_id = ?
ORDER BY mileage_km DESC, service_date_epoch_ms DESC, id DESC;
```

- `LazyColumn` with `items(entries, key = { it.id })` — **mandatory**, sorting reorders rows; keys keep item state and animations pinned.
- The `Date | Mileage` control is a fixed element above the list (header content of the same `LazyColumn` is fine; a `Column { control; LazyColumn }` is simpler). Never a `LazyColumn` inside `verticalScroll`.
- Row grammar mirrors `VehicleCard`: title = service type, meta = `"19 Oct 2026 · 123.456 km"`, cost trailing `"Rp 450.000"` (em-dash placeholder rules apply to absent cost).
- Explicit Loading / Empty / Content / Error states; the empty state gets the "Add service" CTA (D-36).

### Pattern 7: Integer Rupiah — raw digits in, formatted preview out (D-29, D-13)

**What:** The field holds **raw digits** (digits-only keyboard/filter, Phase 1 convention); a formatted preview (`Rp 450.000`) renders from the parsed `Long`; storage is `INTEGER` rupiah.
**Cap:** limit input to 12 digits (≤ Rp 999.999.999.999) so parsing cannot overflow `Long`. Empty → `null`.
**Don't hand-roll per platform:** locale APIs would fork (`java.text.NumberFormat` vs `NSNumberFormatter`) and could render different separators; one pure-Kotlin formatter with unit tests is the single implementation.

### Pattern 8: Read-only detail + delete-last (D-35)

**What:** Detail renders the `FieldRow` grammar already used on the vehicle Detail: focal block (type + date + mileage), then cost, parts, workshop/mechanic, notes, photos — optional values via the em-dash placeholder.
**Delete:** last element on the screen, opens the generalized `ConfirmDeleteDialog` with the locked copy ("Delete service entry" / "Delete this entry? This can't be undone."), single-flight guard in the ViewModel, and the success snackbar is hosted on the **shell** because the source screen is popped (Phase 1 precedent).
**Mileage warning baseline on edit (discretion):** compare against the immediately previous entry in the Date order (id tie-break) excluding self; for add it is the prefill value. One query:

```sql
selectPreviousMileage:
SELECT mileage_km FROM service_entry
WHERE vehicle_id = ? AND id != ?
  AND (service_date_epoch_ms < ? OR (service_date_epoch_ms = ? AND id < ?))
ORDER BY service_date_epoch_ms DESC, id DESC
LIMIT 1;
```

### Pattern 9: Tests mirror Phase 1's three layers

**What:** `commonTest` (runs in CI through `testAndroidHostTest` and `iosSimulatorArm64Test`): mapper round-trip, `ServiceDates` round-trip + display string, `formatRupiah` table, learned-type filter (preset collapse). `iosTest`: real-driver repository tests (save add/edit, child replacement, delete order, vehicle cascade file deletion) mirroring `VehicleRepositoryTest` / `VehicleDeletionTest`, plus a multi-directory `PhotoStorageTest`. `androidDeviceTest`: optional local WAL/migration checks — CI has **no emulator job**, so device-level migration proof is a local pre-verification step.

### Anti-Patterns to Avoid

- **Storing formatted dates or `now()` timestamps** — only UTC start-of-day millis of the civil date; never derive display from the system zone.
- **Float/double cost** — integer rupiah only (D-29).
- **Type registry table, rename operation, or "normalizing" a learned label onto a preset's casing** — stored strings are stable (D-40).
- **A second photo-storage object or absolute paths** — one seam, relative paths only (D-05/D-37).
- **Relying on `ON DELETE CASCADE`** — `PRAGMA foreign_keys` is never enabled in this app; FK clauses are documentation only.
- **Deleting a superseded file before the save commits** — destroys the user's only copy of an image the row still references.
- **`LazyColumn` inside `Column + verticalScroll`** or a wrapping scroll container on the form (PITFALLS CMP 2).
- **Missing `key` on `LazyColumn.items`** — sorting makes duplicate-key/state bugs visible (PITFALLS CMP 1).
- **Long-press/swipe delete or a second delete dialog variant** — single locked confirm interaction (D-18/D-35).
- **Preselecting a type or blocking Save on a lower mileage** — violates D-26/D-28.
- **N+1 reads** — history rows need no parts/photos; the Detail summary uses one aggregate query.

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Civil date → epoch millis, month names | Epoch-day arithmetic + month-name arrays | kotlinx-datetime `atStartOfDayIn` / `todayIn` / `MonthNames` | Time-zone and calendar edge cases; official library |
| Date input UI | Custom calendar grid | Material3 `DatePicker` + `DatePickerDialog` (common) | Accessible, both platforms, already verified in commonMain |
| Wrapping chip layout | Manual line-break math in a `Row` | `FlowRow` (`@ExperimentalLayoutApi`) | Measurement/overflow handled by the framework |
| Image pick + downscale | Platform bitmap code | FileKit `compressImage` inside the existing `PhotoStorage` | Platform encoders, already tested by Phase 1 |
| Confirm-dialog semantics | A second dialog variant or ad-hoc `AlertDialog` | Generalized `ConfirmDeleteDialog` (same guarantees, parameterized copy) | Locked dismiss-path guarantees |
| Empty-state layout | Ad-hoc column per screen | Generalized `EmptyState` | Copy/CTA rules and tokens stay consistent |
| Currency formatting | Locale APIs (`java.text` / `NSNumberFormatter`) | One pure-Kotlin `formatRupiah` util + tests | Invariant output; one implementation, no platform fork |
| Insert ids | `INSERT … RETURNING` | `lastInsertId()` inside the transaction | `RETURNING` unavailable at minSdk 26 |
| Undo/backout of failed saves | Bespoke rollback logic | SQLDelight `transactionWithResult` | Atomicity for entry + children |

**Key insight:** in this codebase the "framework" is already in place — the value is in reusing the seam/transaction/mapper patterns exactly, because the Phase 1 tests already encode their guarantees.

## Common Pitfalls

### Pitfall 1: UTC/system-zone off-by-one on service dates
**What goes wrong:** An entry saved "today" shows as yesterday (or tomorrow) in history, or a device zone change shuffles dates.
**Why it happens:** Mixing `Instant.now()`-style timestamps with civil dates; deriving "today" in UTC; formatting stored millis in the system zone.
**How to avoid:** `ServiceDates` is the only conversion site: `today()` from `TimeZone.currentSystemDefault()`, `toStorage`/`fromStorage` always via `TimeZone.UTC`.
**Warning signs:** a test that passes only in one zone; dates that differ between the form and the history row.

### Pitfall 2: Migration left unverified (D-38/DAT-04)
**What goes wrong:** CI's `git diff --exit-code` fails, or worse, `1.sqm` is never applied to `1.db` because the baseline was deleted.
**Why it happens:** Regenerating `2.db` and deleting `1.db` "to keep the folder clean"; forgetting to commit the regenerated baseline; editing generated schema files by hand.
**How to avoid:** author `.sq` + `1.sqm`, regenerate, commit **both baselines**; never hand-edit `*.db`; run the per-source-set verify task, not the aggregate.
**Warning signs:** `verifyCommonMainVehiCareDatabaseMigration` green while `generate…Schema` leaves a diff.

### Pitfall 3: Foreign keys are declared but not enforced
**What goes wrong:** Deleting an entry or vehicle leaves orphan child rows; a future `ON DELETE CASCADE` expectation silently never fires.
**Why it happens:** `PRAGMA foreign_keys` is never set (verified: no occurrence in `shared/src`), so SQLite ignores FK clauses by default.
**How to avoid:** every delete path is an explicit ordered delete: photos/parts → entries → vehicle (or children → entry), all inside one transaction; read paths first when files must be deleted.
**Warning signs:** orphaned `service_photo` rows after a delete test.

### Pitfall 4: N+1 reads on the history/summary paths (PITFALLS SQL 3)
**What goes wrong:** History rows or the Detail summary trigger one query per entry (or per photo), and the list stutters on large histories.
**Why it happens:** Loading full entries (with parts/photos) for the list; recomputing "last service" per recomposition.
**How to avoid:** the history query returns entry rows only; the summary is one aggregate query (`COUNT + MAX(service_date_epoch_ms)`); parts/photos load only on detail/edit.
**Warning signs:** a repository method named like `observeEntriesWithEverything()` feeding the list.

### Pitfall 5: Multi-photo lifecycle leaks or pre-emptive deletes (D-07/D-37)
**What goes wrong:** A removed-but-saved photo is deleted before the save commits (data loss), or session-persisted photos leak on abandon, or entry/vehicle deletes leave files behind.
**Why it happens:** single-photo mental model (`supersededPath` string) copied to a list; deleting files inside the transaction; forgetting `onCleared`.
**How to avoid:** the per-path set rules in Pattern 5, exercised by tests (abandon, failed save, successful replace, entry delete, vehicle cascade).
**Warning signs:** tests that only assert DB rows; no test for "file deleted after successful save".

### Pitfall 6: Sorting reorders rows without stable keys (PITFALLS CMP 1)
**What goes wrong:** Toggling Date ↔ Mileage visually reorders but item state/animations jump; edits appear to affect the wrong row.
**Why it happens:** `items(list) { }` without `key = { it.id }`.
**How to avoid:** always `items(entries, key = { it.id })`; keep one `LazyColumn`.
**Warning signs:** duplicated or flickering rows when switching sort.

### Pitfall 7: Nested scrolling on form or history (PITFALLS CMP 2)
**What goes wrong:** The form scrolls twice/jerks, the date picker or chip row traps gestures, lists render with zero height.
**Why it happens:** `LazyColumn` inside `Column(verticalScroll)`; wrapping the whole form in nested scroll containers.
**How to avoid:** form = one `Column(Modifier.verticalScroll(rememberScrollState()))` (parts/photos are plain rows, not lazy); history = one `LazyColumn` with the sort control as a fixed header.
**Warning signs:** infinite-height or unreachable last element.

### Pitfall 8: iOS keyboard/IME hiding fields (PITFALLS CMP 4)
**What goes wrong:** On iOS, the keyboard covers the submit button or lower fields; the form cannot be scrolled clear.
**Why it happens:** CMP iOS IME insets differ from Android.
**How to avoid:** `imePadding()` on the scrolling form container; keep the submit button as the last column element; verify manually on a simulator with the keyboard open.
**Warning signs:** only tested on Android; submit unreachable on iOS.

### Pitfall 9: One-shot flags replay (Phase 1 convention)
**What goes wrong:** The save-failure or photo-error snackbar fires on every recomposition.
**Why it happens:** Boolean left `true` in state instead of retired by the screen.
**How to avoid:** follow the `saveFailed`/`photoError` retire pattern (`onSaveErrorShown`, `onPhotoImportFailed`).
**Warning signs:** snackbar storms after rotation/recomposition.

### Pitfall 10: Learned-type query surprises
**What goes wrong:** Duplicate chips differing only by case; a preset-looking chip appears twice; the list is vehicle-scoped by mistake.
**Why it happens:** `GROUP BY service_type` (case-sensitive) instead of `lower(service_type)`; forgetting the preset filter; filtering by `vehicle_id`.
**How to avoid:** exact query in Pattern 3 (verified), Kotlin filter `equals(ignoreCase = true)`, cross-vehicle by design (D-25).
**Warning signs:** "Oil change" and "oil change" chips side by side.

### Pitfall 11: Migration compile-time mismatch between `.sq` and `1.sqm`
**What goes wrong:** The verify task fails because the `.sq` schema and the migration result differ (column order/type/index name).
**Why it happens:** Editing one file and not the other; index names colliding between tables.
**How to avoid:** copy the exact DDL into both files; name indices uniquely (`service_part_entry`, `service_photo_entry`).
**Warning signs:** verification error naming a table/column.

### Pitfall 12: Delete snackbar hosted on a popped screen
**What goes wrong:** "Service entry deleted." never appears because the screen navigated away.
**Why it happens:** Snackbar shown from the detail screen's own scaffold.
**How to avoid:** host it on the shell (Phase 1 delete precedent); the ViewModel exposes a one-shot flag the shell retires.
**Warning signs:** the message only shows when the user navigates back.

## Code Examples

Verified patterns from official sources and the repo's own conventions:

### Schema migration — `1.sqm` (and the identical DDL in `ServiceEntry.sq`)

```sql
-- shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm
-- source: SQLDelight migration naming docs + DAT-04 (D-38)
CREATE TABLE service_entry (
    id                    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    vehicle_id            INTEGER NOT NULL REFERENCES vehicle(id),
    service_date_epoch_ms INTEGER NOT NULL,   -- UTC start-of-day millis of the civil date
    mileage_km            INTEGER NOT NULL,   -- unit in the name (PITFALLS SQL 2)
    service_type          TEXT    NOT NULL,   -- stable plain string (D-10/D-40)
    cost_rupiah           INTEGER,            -- whole rupiah, never floating point (D-29)
    notes                 TEXT,
    workshop              TEXT,
    mechanic              TEXT
);

CREATE TABLE service_part (
    id       INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    entry_id INTEGER NOT NULL REFERENCES service_entry(id),
    name     TEXT    NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    position INTEGER NOT NULL
);

CREATE TABLE service_photo (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    entry_id   INTEGER NOT NULL REFERENCES service_entry(id),
    photo_path TEXT    NOT NULL,   -- relative path only (D-05/D-37)
    position   INTEGER NOT NULL
);

CREATE INDEX service_entry_vehicle_date    ON service_entry(vehicle_id, service_date_epoch_ms DESC, id DESC);
CREATE INDEX service_entry_vehicle_mileage ON service_entry(vehicle_id, mileage_km DESC);
CREATE INDEX service_part_entry            ON service_part(entry_id, position);
CREATE INDEX service_photo_entry           ON service_photo(entry_id, position);
```

*AUTOINCREMENT matters here:* learned-type recency uses `MAX(id)`, and AUTOINCREMENT guarantees ids are never reused after a delete. Foreign-key clauses document intent only — enforcement is off (see Pitfall 3).

### Queries — `ServiceEntry.sq` (excerpt)

```sql
selectForVehicleByDate:
SELECT * FROM service_entry WHERE vehicle_id = ?
ORDER BY service_date_epoch_ms DESC, id DESC;

selectForVehicleByMileage:
SELECT * FROM service_entry WHERE vehicle_id = ?
ORDER BY mileage_km DESC, service_date_epoch_ms DESC, id DESC;

selectById:
SELECT * FROM service_entry WHERE id = ?;

selectParts:
SELECT * FROM service_part WHERE entry_id = ? ORDER BY position;

selectPhotos:
SELECT * FROM service_photo WHERE entry_id = ? ORDER BY position;

selectLatestMileage:
SELECT mileage_km FROM service_entry WHERE vehicle_id = ?
ORDER BY service_date_epoch_ms DESC, id DESC LIMIT 1;

selectPreviousMileage:
SELECT mileage_km FROM service_entry WHERE vehicle_id = ? AND id != ?
  AND (service_date_epoch_ms < ? OR (service_date_epoch_ms = ? AND id < ?))
ORDER BY service_date_epoch_ms DESC, id DESC LIMIT 1;

selectSummary:
SELECT COUNT(*) AS entry_count, MAX(service_date_epoch_ms) AS last_service_epoch_ms
FROM service_entry WHERE vehicle_id = ?;

selectLearnedTypes:
SELECT service_type, id AS last_used_id
FROM service_entry
WHERE id IN (SELECT MAX(id) FROM service_entry GROUP BY lower(service_type))
ORDER BY id DESC;

insertEntry:
INSERT INTO service_entry(vehicle_id, service_date_epoch_ms, mileage_km, service_type, cost_rupiah, notes, workshop, mechanic)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

updateEntry:
UPDATE service_entry SET service_date_epoch_ms=?, mileage_km=?, service_type=?, cost_rupiah=?, notes=?, workshop=?, mechanic=?
WHERE id=?;

insertPart:
INSERT INTO service_part(entry_id, name, quantity, position) VALUES (?, ?, ?, ?);

insertPhoto:
INSERT INTO service_photo(entry_id, photo_path, position) VALUES (?, ?, ?);

deletePartsForEntry:
DELETE FROM service_part WHERE entry_id = ?;

deletePhotosForEntry:
DELETE FROM service_photo WHERE entry_id = ?;

deleteEntry:
DELETE FROM service_entry WHERE id = ?;

-- Vehicle cascade (D-39) — order matters: collect paths before deleting rows
selectPhotoPathsForVehicle:
SELECT photo_path FROM service_photo
WHERE entry_id IN (SELECT id FROM service_entry WHERE vehicle_id = ?);

deletePhotosForVehicle:
DELETE FROM service_photo WHERE entry_id IN (SELECT id FROM service_entry WHERE vehicle_id = ?);

deletePartsForVehicle:
DELETE FROM service_part WHERE entry_id IN (SELECT id FROM service_entry WHERE vehicle_id = ?);

deleteEntriesForVehicle:
DELETE FROM service_entry WHERE vehicle_id = ?;

lastInsertId:
SELECT last_insert_rowid();
```

### Date policy — `util/ServiceDates.kt`

```kotlin
// source: kotlinx-datetime v0.8.0 README + API dump + source (tag v0.8.0)
package com.vehicare.util

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.MonthNames
import kotlinx.datetime.number
import kotlinx.datetime.todayIn
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The single date-conversion site: "today" follows the user's wall clock
 * (system zone), storage is zone-independent (UTC start of day), display
 * reads the stored millis back as a UTC civil date.
 */
object ServiceDates {

    fun today(): LocalDate =
        Clock.System.todayIn(TimeZone.currentSystemDefault())

    fun toStorage(day: LocalDate): Long =
        day.atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

    fun fromStorage(epochMs: Long): LocalDate =
        Instant.fromEpochMilliseconds(epochMs).toLocalDateTime(TimeZone.UTC).date

    /** "19 Oct 2026" — the app's one display format for service dates. */
    fun display(day: LocalDate): String =
        "${day.day} ${MonthNames.ENGLISH_ABBREVIATED.names[day.month.number - 1]} ${day.year}"

    fun display(epochMs: Long): String = display(fromStorage(epochMs))
}
```

### Repository — save (add + edit) and delete in transactions

```kotlin
// source: mirrors VehicleRepositoryImpl (serialized lane + transactionWithResult + lastInsertId)
internal class ServiceEntryRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val photoDeleter: (String?) -> Unit = {},
) : ServiceEntryRepository {

    override fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>> {
        val query = when (sort) {
            HistorySort.DATE -> database.serviceEntryQueries.selectForVehicleByDate(vehicleId)
            HistorySort.MILEAGE -> database.serviceEntryQueries.selectForVehicleByMileage(vehicleId)
        }
        return query.asFlow().mapToList(ioDispatcher).map { rows -> rows.map { it.toDomain() } }
    }

    override suspend fun save(entry: ServiceEntry): Long = withContext(ioDispatcher) {
        database.transactionWithResult {
            val id = if (entry.id == 0L) {
                database.serviceEntryQueries.insertEntry(
                    vehicle_id = entry.vehicleId,
                    service_date_epoch_ms = entry.dateEpochMs,
                    mileage_km = entry.mileageKm,
                    service_type = entry.serviceType,
                    cost_rupiah = entry.costRupiah,
                    notes = entry.notes,
                    workshop = entry.workshop,
                    mechanic = entry.mechanic,
                )
                database.serviceEntryQueries.lastInsertId().executeAsOne() // same tx (minSdk 26)
            } else {
                database.serviceEntryQueries.updateEntry(
                    service_date_epoch_ms = entry.dateEpochMs,
                    mileage_km = entry.mileageKm,
                    service_type = entry.serviceType,
                    cost_rupiah = entry.costRupiah,
                    notes = entry.notes,
                    workshop = entry.workshop,
                    mechanic = entry.mechanic,
                    id = entry.id,
                )
                database.serviceEntryQueries.deletePartsForEntry(entry.id)   // full replace
                database.serviceEntryQueries.deletePhotosForEntry(entry.id)
                entry.id
            }
            entry.parts.forEachIndexed { index, part ->
                database.serviceEntryQueries.insertPart(
                    entry_id = id, name = part.name, quantity = part.quantity, position = index.toLong(),
                )
            }
            entry.photoPaths.forEachIndexed { index, path ->
                database.serviceEntryQueries.insertPhoto(
                    entry_id = id, photo_path = path, position = index.toLong(),
                )
            }
            id
        }
    }

    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val photoPaths = database.transactionWithResult {
            val paths = database.serviceEntryQueries.selectPhotos(id).executeAsList().map { it.photo_path }
            database.serviceEntryQueries.deletePartsForEntry(id)
            database.serviceEntryQueries.deletePhotosForEntry(id)
            database.serviceEntryQueries.deleteEntry(id)
            paths
        }
        photoPaths.forEach(photoDeleter)   // files only after the rows are durably gone
    }
}
```

*Generated parameter names follow the SQL column names (SQLDelight 2.x), matching the `snake_case` bind calls above. Custom query result types are generated per query (`SelectLearnedTypes`, `SelectSummary`); domain mappers stay the only place generated types meet the domain (`ServiceEntryEntityMapper`).*

### Vehicle-delete cascade — extend the existing path (D-39)

```kotlin
// VehicleRepositoryImpl.delete — replaces the current single-photo body
override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
    val photoPaths = database.transactionWithResult {
        val vehiclePhoto = database.vehicleQueries.selectById(id).executeAsOneOrNull()?.photo_path
        val servicePhotos = database.serviceEntryQueries
            .selectPhotoPathsForVehicle(id)
            .executeAsList()
        database.serviceEntryQueries.deletePhotosForVehicle(id)   // children first
        database.serviceEntryQueries.deletePartsForVehicle(id)
        database.serviceEntryQueries.deleteEntriesForVehicle(id)
        database.vehicleQueries.delete(id)
        listOfNotNull(vehiclePhoto) + servicePhotos
    }
    photoPaths.forEach(photoDeleter)
}
```

### Learned types — query rows → chips (D-24/D-25, preset collapse)

```kotlin
val learnedTypes: List<String> = database.serviceEntryQueries
    .selectLearnedTypes()
    .executeAsList()
    .map { it.service_type }
    .filterNot { name -> ServiceTypePresets.ALL.any { preset -> preset.equals(name, ignoreCase = true) } }
```

### Rupiah — `util/RupiahFormat.kt` (D-29)

```kotlin
private const val MAX_COST_DIGITS = 12   // ≤ Rp 999.999.999.999, no Long overflow

fun parseRupiahDigits(raw: String): Long? =
    raw.filter { it.isDigit() }.take(MAX_COST_DIGITS).takeIf { it.isNotEmpty() }?.toLongOrNull()

fun formatRupiah(amount: Long): String =
    "Rp " + amount.toString().reversed().chunked(3).joinToString(".").reversed()
// 450000 -> "Rp 450.000"
```

### History sort — ViewModel state, not persisted (D-34)

```kotlin
private val sort = MutableStateFlow(HistorySort.DATE)

val state: StateFlow<ServiceHistoryUiState> =
    sort.flatMapLatest { mode -> repository.observeHistory(vehicleId, mode) }
        .map { entries -> ServiceHistoryUiState.Content(entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServiceHistoryUiState.Loading)

fun onSortSelected(value: HistorySort) { sort.value = value }
// If the pinned coroutines version still requires it, add @OptIn(ExperimentalCoroutinesApi::class)
// at the ViewModel for flatMapLatest.
```

### Wrapping chip group — `FlowRow` (D-26)

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ServiceTypePicker(
    presets: List<String>,
    learned: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onAddCustom: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        (presets + learned).forEach { type ->
            FilterChip(selected = type == selected, onClick = { onSelect(type) }, label = { Text(type) })
        }
        AssistChip(
            onClick = onAddCustom,
            label = { Text(stringResource(Res.string.service_type_add_custom)) },
            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
        )
    }
}
```

### Date picker dialog — common API, UTC millis (D-33/date input)

```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ServiceDatePickerDialog(
    initialDateEpochMs: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialDateEpochMs)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = { datePickerState.selectedDateMillis?.let(onConfirm) },
                enabled = datePickerState.selectedDateMillis != null,
            ) { Text(stringResource(Res.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    ) {
        DatePicker(state = datePickerState)
    }
}
```

### PhotoStorage — directory parameter (D-37)

```kotlin
object PhotoStorage {
    private const val VEHICLES_DIRECTORY = "vehicles"
    const val SERVICE_ENTRY_DIRECTORY = "service_entries"   // callers pass this explicitly

    suspend fun persist(
        picked: PlatformFile,
        fileName: String,
        directory: String = VEHICLES_DIRECTORY,   // vehicle call sites unchanged
    ): String {
        val relative = "$directory/$fileName"
        // …existing compression + write logic unchanged…
        return relative
    }
}
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `kotlinx.datetime.Instant` / `Clock` | `kotlin.time.Instant` / `Clock` (stdlib) | kotlinx-datetime 0.7.0 (2025) | Import `kotlin.time.*`; old typealiases deprecated; the repo already uses `kotlin.time.Clock` |
| `LocalDate.dayOfMonth` / `monthNumber` | `LocalDate.day` / `Month.number` | kotlinx-datetime 0.7.0–0.8.0 | Old names log WARNING deprecations; use the new properties |
| `INSERT … RETURNING` | `last_insert_rowid()` in-transaction | SQLite 3.35 / minSdk 26 floor | Already the repo's convention; keep it for `service_entry` |
| Platform date-picker overloads (`LocalDate` variants) | Millis-based common `DatePickerState` (UTC start-of-day) | M3 1.2+ / CMP | Portable common code; no expect/actual needed |
| Expecting `ON DELETE CASCADE` to fire | Explicit ordered deletes in transactions | SQLite default (FKs off) | Cascade correctness lives in repository code + tests |

**Deprecated/outdated — do not use in this phase:**
- `kotlinx.datetime.Instant` / `kotlinx.datetime.Clock` imports (deprecated).
- `LocalDate.dayOfMonth` / `LocalDate.monthNumber` (deprecated WARNING in 0.8.0).
- The `TimeZoneContext` API seen in kotlinx-datetime's `master` README — **it does not exist in 0.8.0**; Context7's unversioned docs track master and will mislead.
- The aggregate `verify…Migration` Gradle task in CI — the repo comment (T-01-…) marks the per-source-set task as the load-bearing guard; never substitute.
- `kotlinx-collections-immutable` — not needed; read-only lists + `LazyColumn` keys satisfy PITFALLS CMP 3.

## Assumptions Log

> Claims tagged `[ASSUMED]` in this research. The planner and discuss-phase use this section to identify decisions that need user confirmation before execution.

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The material3 common `DatePickerDialog` behaves equivalently on iOS and Android (rendering, dismissal, confirm/dismiss callbacks) — verified only from source, not on device | Pattern 4 / Code Examples | Dialog UX broken on one platform until UAT catches it; mitigation: manual iOS-simulator check in verification (no plan changes needed) |
| A2 | 5 photos per entry is a comfortable maximum and a thumbnail strip without a full-screen viewer is sufficient | Pattern 5 (discretion) | Users hit the cap or can't read receipts; both are constants/scope items that can change without schema impact |
| A3 | `imePadding()` alone clears the iOS keyboard for this specific form (subtitle content, submit at the end) | Pitfall 8 | Submit button unreachable on iOS until fixed; mitigation: explicit manual keyboard-open check |
| A4 | The learned-type list stays short enough to render as a flat chip group without truncation or a "more" affordance | Pattern 3 | Long lists push "Add custom…" far down the form; the deferred "managed types" idea re-opens |
| A5 | Recency for learned types = creation order (`id`), not last-edit order — editing an old entry to a new type does not bump it | Pattern 3 | Rare "my new type isn't first" surprises; fixing needs an `updated_at` column later |
| A6 | 12 input digits is a comfortable cost ceiling (≤ Rp 999.999.999.999) | Pattern 7 (discretion) | An unusually expensive service cost is rejected at input; constant is trivial to raise |

**If this table were empty:** it is not — A1–A6 are the discretionary/untested spots; everything else in this research was verified or cited.

## Open Questions (RESOLVED)

> **All resolved during planning.** Every question below is closed and its resolution is encoded in the phase plans or explicitly deferred to phase verification, so nothing blocks execution. The resolutions are:
> OQ-1 → `02-UI-SPEC.md` landed and is the binding copy/layout contract; plan **02-02** authors the string set from its §Copywriting Contract, and every consuming plan treats the UI-SPEC as the contract. OQ-2 → **deferred to phase verification**: device-level Android migration coverage is a manual, local follow-up (the instruction is recorded in plan **02-01**'s verification section), not a plan task; CI gains no emulator job. OQ-3 → creation-order recency ships as researched (`MAX(id)`, decision A5); no schema addition.

1. **Phase 2 UI-SPEC is pending (config `ui_phase: true`). — RESOLVED**
   - Resolution: **landed.** `02-UI-SPEC.md` fixes copy, layout and the state/accessibility contracts before any plan authors `strings.xml`; plan **02-02** carries the §Copywriting Contract keys, and the D-28/D-35 strings appear there verbatim. The consuming plans (02-03/02-04/02-05/02-06) cite the UI-SPEC as the contract — this research's wording is the fallback only.
   - What we know: 01-UI-SPEC tokens/rules carry forward; D-28 and D-35 lock two strings verbatim; this research recommends the rest (FAB "Add service", sort labels "Date"/"Mileage", snackbar "Service entry deleted.", empty state "No service entries yet" + CTA, summary row "N entries · Last service <date>").
   - What's unclear: final copy for empty state / summary row, thumbnail sizing, whether history rows show an em-dash for a null cost or omit the trailing value.
   - Recommendation: let the Phase 2 UI-SPEC fix copy/layout before plan tasks author `strings.xml`; plans should treat this research's wording as the fallback, not the contract.

2. **Android device-test coverage for the migration is local-only. — RESOLVED (deferred to phase verification)**
   - Resolution: **deferred to phase verification, not a plan task.** No plan authors or extends `androidDeviceTest`; the `1.sqm` path stays proven compile-time (`verifyCommonMainVehiCareDatabaseMigration`) and by committed-baseline diff in CI, which has **no emulator job**. The manual follow-up instruction is recorded in plan **02-01**'s `<verification>` section: on the `Medium_Phone` AVD, install a Phase-1 build, create a vehicle, then install the Phase-2 build over it and confirm the v1 database migrates in place (vehicle intact, entry saves). A CI emulator job stays a later-phase question (cost/complexity).
   - What we know: CI runs host tests + iOS simulator tests; there is **no emulator job** (verified in `ci.yml`). The `1.sqm` path is proven compile-time (verify task) and by baseline diff, not by running the migration on an Android SQLite.
   - What's unclear: whether a device-level migration test is worth adding now.
   - Recommendation: add/extend `androidDeviceTest` coverage for "open v1 db → migrate → service tables exist" and run `:shared:connectedAndroidDeviceTest` locally with the `Medium_Phone` AVD before phase verification; defer a CI emulator job to a later phase (cost/complexity).

3. **Learned-type recency on edit (A5). — RESOLVED**
   - Resolution: **creation-order recency ships** — `selectLearnedTypes` groups on `lower(service_type)` and ranks by `MAX(id)` (plan **02-01**), matching decision A5: editing an old entry to a new type does not bump it, and no `updated_at` column is added. Revisit only if UAT shows surprise; the change would be additive.
   - What we know: `MAX(id)` ranks by creation; D-24 says "newest-used first".
   - What's unclear: whether "used" should include "edited an old entry to this type".
   - Recommendation: ship creation-order recency (simple, no schema addition); revisit only if UAT shows surprise.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|-------------|-----------|---------|----------|
| JDK | Gradle builds/tests | ✓ | 26 local; CI pins Temurin 21 | CI's 21 is the contract; local 26 verified by Phase 1 |
| Gradle wrapper | All builds | ✓ | 9.5.1 | — |
| Xcode + iOS simulators | `linkDebugFrameworkIosSimulatorArm64`, `iosSimulatorArm64Test`, manual UAT | ✓ | Xcode 27.0 / Swift 6.4; 5 simulators | CI `macos-latest` image |
| Android SDK + emulator + AVD | `androidDeviceTest`, manual UAT | ✓ | adb 1.0.41, emulator 37.1.11.0, AVD `Medium_Phone`, platforms android-36.1 / 37.0 | CI has no emulator (see Open Q 2); host tests still run |
| sqlite3 CLI | Migration/query debugging | ✓ | 3.54.0 | — |
| kotlinx-datetime 0.8.0 | Date conversions | ✓ Maven Central reachable | 0.8.0 (published 2026-05-07) | None needed; first build downloads it |

**Missing dependencies with no fallback:** none — the phase is fully executable with what is installed.
**Missing dependencies with fallback:** none blocking; the only gap is CI-side Android device testing (fallback: local emulator run, Open Q 2).

## Security Domain

`security_enforcement: true`, ASVS level 1, block on high — controls below are the applicable set for a local-first, single-user app.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No accounts; all data local (DAT-01) |
| V3 Session Management | No | No sessions/server |
| V4 Access Control | No (OS-level only) | App-private storage; `allowBackup=false` keeps entries/photos out of device/cloud backups |
| V5 Input Validation | **Yes** | Digits-only numeric fields (D-13), required date/type/mileage gating (D-27), quantity ≥ 1, cost ≤ 12 digits, ≤ 5 photos, free-text length caps (recommend ≤ 500 for notes, ≤ 120 for part/workshop/mechanic), SQLDelight typed bind parameters only |
| V6 Cryptography | No | No secrets, no crypto; never hand-roll |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection | Tampering | SQLDelight compile-time queries + typed binds; no string-built SQL anywhere |
| Path traversal via photo paths | Tampering | Paths are app-generated (`service_entries/<name>.jpg`); user input never becomes a path; `PhotoStorage.resolve` anchors to `FileKit.filesDir` |
| Malformed image input | DoS / Tampering | FileKit decode failure already falls back to storing bytes verbatim inside `PhotoStorage` (Phase 1) |
| Orphaned personal data (files or child rows) | Information disclosure | Explicit delete cascades + per-path file bookkeeping; tests assert files are gone after entry/vehicle delete |
| Data exfiltration via backup/sync | Information disclosure | `allowBackup=false`; no network permissions/analytics dependencies |
| Log leakage | Information disclosure | No logging of entry contents/photos; no third-party SDKs |
| Resource exhaustion via huge inputs | DoS | Photo cap (5), digit caps, text length caps, image downscale to 1280 px |

## Sources

### Primary (HIGH confidence)
- [kotlinx-datetime v0.8.0 tag](https://github.com/Kotlin/kotlinx-datetime/tree/v0.8.0) — `README.md` (todayIn/atStartOfDayIn usage, `kotlin.time.Instant`), `core/api/kotlinx-datetime.api` (API surface), `core/common/src/LocalDate.kt` (`day`/`dayOfMonth` deprecation), `core/common/src/Month.kt` (`Month.number`), `core/common/src/format/YearMonthFormat.kt` (`MonthNames.names`) — read 2026-10-19
- [Maven Central — kotlinx-datetime 0.8.0](https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-datetime/0.8.0/) — artifact listing + POM `last-modified: 2026-05-07`
- Repository code (read 2026-10-19): `shared/src/commonMain/sqldelight/com/vehicare/db/Vehicle.sq`, `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt`, `…/ui/vehicles/VehicleEditViewModel.kt`, `…/util/PhotoStorage.kt`, `…/data/db/DriverFactory*.kt`, `.github/workflows/ci.yml`, `gradle/libs.versions.toml`, `shared/build.gradle.kts`
- `compose-multiplatform-core` material3 common source — `DatePickerState.selectedDateMillis` (UTC start-of-day), `rememberDatePickerState(initialSelectedDateMillis)`, `DatePickerDialog` expect signature; foundation `FlowRow` (verified in the prior session against the pinned 1.12.x sources)
- sqlite3 3.54.0 scratch test (run 2026-10-19) — learned-type `GROUP BY lower(service_type)` + `MAX(id)` semantics with mixed-case labels

### Secondary (MEDIUM confidence)
- [SQLDelight migrations docs](https://sqldelight.github.io/sqldelight/latest/android_sqlite/migrations) — `.sqm` naming = version being upgraded from; versioning starts at 1 (via Context7 `/websites/sqldelight_github_io_sqldelight`)
- [SQLDelight Gradle docs](https://sqldelight.github.io/sqldelight/latest/jvm_sqlite/gradle) — `schemaOutputDirectory` drives `.db` baselines and migration verification; `verifyMigrations = true` (Context7)
- `.planning/research/PITFALLS.md` (SQL 2/3/4, CMP 1–4) and `.planning/research/ARCHITECTURE.md` (write flow) — project research artifacts, binding via CONTEXT canonical refs

### Tertiary (LOW confidence)
- None material to this phase. (Context7's unversioned kotlinx-datetime docs were rejected: they track `master` and describe a `TimeZoneContext` API absent from 0.8.0.)

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — every version read from the repo's own catalog; kotlinx-datetime verified on Maven Central and its API verified against the pinned v0.8.0 tag sources
- Architecture: HIGH — schema, queries, and cascade shapes ride verified Phase 1 patterns; the learned-type query was verified empirically; MEDIUM only where noted as discretion (photo max/viewer, copy wording)
- Pitfalls: HIGH for date/storage/migration/lifecycle rules (verified conversions + repo evidence); MEDIUM for iOS IME/dialog behavior (source-verified, device-UAT pending)

**Validation Architecture:** omitted per `.planning/config.json` (`workflow.nyquist_validation: false`, explicit); the phase's test layering is captured in Pattern 9 and the device-test gap is Open Question 2.

**Research date:** 2026-10-19
**Valid until:** 2026-11-18 (30 days) — shorten to ~14 days if material3 (`1.12.0-alpha03`) or CMP is bumped, since alpha APIs can shift between catalog updates.

