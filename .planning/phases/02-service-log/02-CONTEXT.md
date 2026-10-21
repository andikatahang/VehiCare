# Phase 2: Service Log - Context

**Gathered:** 2026-10-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 2 delivers per-vehicle service entry management: create, view, edit and delete service entries (date, mileage, service type, cost, notes), parts replaced or repaired (part name + quantity), workshop and mechanic, one or more photos, and a full per-vehicle service history that can be sorted by date or mileage. The reserved "Service history (coming soon)" slot on the vehicle Detail screen becomes the entry point to the history.

Reminders, notifications, the home dashboard, cost analytics/totals, search/filter, export, fuel logs and cloud sync are **out of scope** — they belong to later phases or v2.

</domain>

<decisions>
## Implementation Decisions

### Service Types
- **D-23:** The preset service types are exactly six, in this order: **Oil change · Tire rotation · Brake service · Inspection · Battery · Filters**. Stored as plain strings following the D-10 pattern, so adding a preset later needs no migration.
- **D-24:** Custom service types are free-text labels. Any distinct label already present on a logged entry becomes a **suggestion chip** on later entries, ranking newest-used first, after the six presets. The suggestion list is **derived from stored entries** — no managed type table and no Settings surface for types.
- **D-25:** Learned custom types are **shared across all vehicles** (one vocabulary for the whole app), not scoped to the vehicle that first used them.
- **D-26:** The service type picker is a **wrapping chip group**: presets first, then learned custom types, then an "Add custom…" chip that opens a one-field prompt. Exactly one type is selected and **no type is preselected** — Save stays disabled until one is chosen. Selecting is one tap. **Superseded 2026-10-21 (user decision, UAT gap 2):** one **or more** types may be selected, so "exactly one type is selected" no longer governs; the chip order (presets → learned → Add custom…), the no-preselection rule, one-tap selection and the rest of this decision remain in force — see `02-UAT.md` gap 2 and `02-08-PLAN.md`.

### Entry Form & Fast Capture
- **D-27:** Required to save an entry: **service date, service type, mileage**. Optional: cost, notes, parts, workshop, mechanic, photos.
- **D-28:** The mileage field **pre-fills with the most recent entry's mileage** for that vehicle (most recent by service date). Entering a lower value shows an inline warning ("Lower than the last recorded mileage — check this") but **does not block Save** — an odometer replacement legitimately resets the series.
- **D-29:** Cost is captured in **Rupiah, whole rupiah only (no decimals)**, displayed with dot thousands separators (e.g. `Rp 450.000`). It is stored as an **integer** (rupiah), never a floating-point value. Cost is optional.
- **D-30:** The entry form is a **single scrolling screen** (D-08 pattern) with the essentials first: **date → mileage → type → cost**, then notes, parts, workshop/mechanic and photos. The submit button sits at the bottom of the same column. There is no multi-step flow and no collapsed "more details" section.
- **D-31:** The fast path must be: open form (date = today) → tap a type chip → accept the pre-filled mileage → Save. This is the concrete reading of ROADMAP SC-1 ("under 30 seconds").

### History & Entry View
- **D-32:** The full service history lives on a **dedicated Service history screen**. The vehicle Detail's `ComingSoonSlot` becomes a compact, tappable summary row (entry count + last service date) that opens it. The summary row does not duplicate the list.
- **D-33:** History rows use the app's established card language: **service type as the title**, a meta line **`date · mileage`**, and **cost trailing**. Tapping a row opens the entry detail screen.
- **D-34:** Sorting is a **two-segment control `Date | Mileage`** at the top of the history screen. Both directions are fixed descending (newest date / highest mileage). **Date is the default** and the choice is **not persisted** across visits.
- **D-35:** Tapping a history row opens a **read-only Service entry detail screen**: type + date + mileage as the focal block, then cost, parts, workshop/mechanic, notes and photos. **Edit** is a text action in the title bar; **Delete** is the last element behind a confirmation dialog — the same shape as the vehicle Detail screen (D-18). Delete copy is entry-specific ("Delete service entry", "Delete this entry? This can't be undone.").
- **D-36:** The history screen owns the primary **"Add service" FAB**. Its empty state (vehicle with no entries) shows the standard message + an "Add service" CTA; the Detail summary row is navigation only.

### Cross-Cutting (requirements, not re-discussed)
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

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project definition & scope
- `.planning/PROJECT.md` — core value, constraints (KMP only, local-first, minimal light), tech stack, Key Decisions table
- `.planning/REQUIREMENTS.md` — SVC-01…SVC-07 definitions, DAT-01…DAT-04, v2 list and Out of Scope table (cost analytics, search, export are **not** Phase 2)
- `.planning/ROADMAP.md` § Phase 2 — goal, success criteria, architecture notes; phase boundary is fixed
- `.planning/STATE.md` — Key Decisions, Version Set in force, Architecture Constraints, Open Questions (Q1 km/miles already resolved by D-01/D-02)

### Prior phase decisions & design contract
- `.planning/phases/01-foundation-vehicle-management/01-CONTEXT.md` — **D-01…D-22 are binding carry-forwards**: km/integer mileage (D-01/D-02), odometer on entries (D-03), gallery-only photos (D-04), relative-path storage (D-05), delete removes files (D-07), single-screen forms (D-08), minimal required fields (D-09), plain-string type storage with fallback (D-10), centralized English strings (D-11), digits-only numeric fields (D-13), confirm-dialog deletion and the Detail integration slot (D-18), simple title bar (D-20), deferred accent (D-21)
- `.planning/phases/01-foundation-vehicle-management/01-UI-SPEC.md` — binding design system: spacing scale 4/8/16/24/32/48/64 only, four type sizes (14/16/20/24sp) and two weights (400/500), light-only palette (`Paper`/`Mist`/`Ink`/`Slate`/`Hairline`/`Danger`), state matrix rules ("no state may render a blank white area"), copywriting and accessibility contracts. Phase 2 gets its own UI-SPEC, but this one defines the tokens and rules that must carry forward.
- `.planning/phases/01-foundation-vehicle-management/01-PATTERNS.md` — pattern map and binding conventions (serialized IO lane, mapper boundary, relative-path photos, migration enforcement, typed bind parameters)

### Research
- `.planning/research/PITFALLS.md` § SQL 2 — store dates and mileage with an explicit unit convention; document units in column names
- `.planning/research/PITFALLS.md` § SQL 3 — no N+1 query when a vehicle needs its latest service; use one JOIN
- `.planning/research/PITFALLS.md` § SQL 4 — wrap multi-write operations (entry + parts + photos) in a transaction
- `.planning/research/PITFALLS.md` § CMP 1 — always pass `key` to `LazyColumn.items` (sorting reorders rows)
- `.planning/research/PITFALLS.md` § CMP 2 — never nest a `LazyColumn` inside `Column + verticalScroll`
- `.planning/research/PITFALLS.md` § CMP 3 — unstable data classes / lists cause recomposition storms; consider `ImmutableList`
- `.planning/research/PITFALLS.md` § CMP 4 — iOS keyboard/IME handling differs; use `imePadding()` on the scrolling form
- `.planning/research/FEATURES.md` § Table Stakes — preset + custom service types, quick entry, service history, backdating
- `.planning/research/FEATURES.md` § Anti-Features — cost analytics, search/filter and export are deliberately not Phase 2
- `.planning/research/ARCHITECTURE.md` § Data Flow → Write flow — the expected AddService path (screen → ViewModel → repository → SQLDelight → Flow emission)

No external specs or ADRs exist — requirements and decisions above are complete.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt` — the single photo-filesystem seam (`persist` / `resolve` / `delete`, JPEG 1280px q80, relative paths). Currently one directory (`vehicles/`) and one path per entity; service entries need a multi-photo shape but must go through this seam, not a second storage object.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/FieldRow.kt` — label/value row with em-dash placeholder and divider; directly reusable for the entry detail screen.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ConfirmDeleteDialog.kt` — the locked confirm interaction (neutral Cancel, danger Confirm, every dismiss path cancels). Copy is vehicle-specific, so reuse needs a generalized sibling — do not duplicate the guarantees or add a second delete path that bypasses a dialog.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt` — message + CTA block; vehicle-list copy is hardcoded, so a parameterized/generic variant is needed for the history empty state.
- `shared/src/commonMain/kotlin/com/vehicare/ui/theme/Color.kt` and `Type.kt` — `Paper`/`Mist`/`Ink`/`Slate`/`Hairline`/`Danger` and the four-size type scale; reuse, invent no colors or sizes.
- `shared/src/commonMain/composeResources/values/strings.xml` — the centralized string set; every new string goes here (`Res.string.*`), never inline (D-11).
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleCard.kt` — the title + meta-line card language the history row should mirror (D-33).

### Established Patterns
- **Repository boundary:** interface + impl, domain models only, a mapper between SQLDelight rows and domain objects; UI never sees generated types or a driver.
- **Serialized DB lane:** every read and write goes through `Dispatchers.IO.limitedParallelism(1)`; never call `Dispatchers.IO` inline.
- **Writes:** `withContext(ioDispatcher) { database.transactionWithResult { … } }`; insert ids via `lastInsertId()` — `RETURNING` is unavailable at minSdk 26.
- **Reactive reads:** `query.asFlow().mapToList(ioDispatcher)` → `Flow` → ViewModel `stateIn(WhileSubscribed(5_000))`; screens render explicit Loading / Empty / Content / Error states with no blank screen.
- **One-shot failure flags:** a `saveFailed`/`photoError` boolean in the UI state is retired after the snackbar shows, so recomposition cannot replay it.
- **Photo lifecycle bookkeeping:** `supersededPath` (database-owned, deleted only after a durable save) vs. `persistedButUnsaved` (session-owned, deleted on abandon) — multi-photo entries need an equivalent, equally explicit rule per removed photo.
- **Delete flows:** single-flight guard in the ViewModel, confirmation dialog as the only call site, success snackbar hosted on the shell because the source screen is popped.
- **Routes:** typed `@Serializable` route classes; `App.kt` is the only nav graph; bottom tabs render only on the tab roots (`isTabRoot`), pushed screens carry their own title bar.
- **DI:** `appModule` registers repositories and ViewModels; route parameters are passed via `parametersOf(id)` and destructured in the `viewModel { (id: Long) -> … }` factory.

### Integration Points
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` — the `ComingSoonSlot()` call site (inside `VehicleDetailContent`) is where the history summary row replaces the placeholder.
- `shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` — add the history, entry detail and entry edit routes here (typed, `@Serializable`).
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` — add `composable<…>` entries; keep the single `NavHost` and the existing bottom-bar scoping.
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` — register `ServiceEntryRepository` and the new ViewModels (route ids via constructor parameters).
- `shared/src/commonMain/sqldelight/com/vehicare/db/Vehicle.sq` — unchanged; new `service_entry` (and photo) tables go in new `.sq` file(s) plus `migrations/1.sqm`, then regenerate the committed baseline.
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` — `delete(id)` must be extended so a vehicle deletion also removes its service entries and their photo files (D-39).
- `shared/src/commonTest/kotlin/com/vehicare/data/VehicleEntityMapperTest.kt` and `shared/src/iosTest/kotlin/com/vehicare/util/PhotoStorageTest.kt` — the existing test patterns for mapper round-trips and real-filesystem photo assertions.

</code_context>

<specifics>
## Specific Ideas

- **The 30-second entry path is the product:** date already today, one tap for the type, mileage already correct from the last entry, one Save. Everything else is optional detail below the fold.
- **Rupiah formatting is part of the visual language:** `Rp 450.000` with dot separators, no decimals, integer storage.
- **History reads like the vehicle list:** type as title, `date · mileage` as meta, cost at the trailing edge — same card grammar as `VehicleCard`.
- **Sorting is one visible choice, not a settings menu:** a `Date | Mileage` segmented control, descending both ways, defaulting to Date.
- **Deleting an entry follows the vehicle pattern exactly:** destructive action last, behind a confirm dialog, no long-press, no swipe-to-delete.

</specifics>

<deferred>
## Deferred Ideas

- **Search / filter history** — research rates it P2; not part of SVC-05. Own future phase.
- **Cost totals / spending analytics** — v2 (COST-01/COST-02); Phase 2 shows per-entry cost only.
- **PDF export of service history** — v2 (EXP-01).
- **Managed service-type list in Settings** (add/rename/delete) — considered and rejected in favour of learned suggestions. Revisit only if the learned list becomes unwieldy.
- **Extended preset list (transmission, coolant, spark plugs, alignment)** — not selected now; new presets can be added later without a migration (D-23/D-10 pattern).
- **Reminders per service type** — Phase 3; reminder rules will key on the same stored type string (D-40).

None of these block Phase 2 scope.

</deferred>

---

*Phase: 02-service-log*
*Context gathered: 2026-10-19*
