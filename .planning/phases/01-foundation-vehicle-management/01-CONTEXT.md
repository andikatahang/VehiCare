# Phase 1: Foundation & Vehicle Management - Context

**Gathered:** 2026-10-18
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 1 delivers the runnable Kotlin Multiplatform skeleton (Compose Multiplatform shared UI, Android API 26+ / iOS 16+) plus full vehicle management: add a vehicle (name, make, model, year, license plate, type, optional photo), view a vehicle list (home screen), view vehicle detail, edit, and delete — backed by SQLDelight with WAL mode from first launch and migration-verified CI. Service logging, reminders, and the dashboard are later phases.

</domain>

<decisions>
## Implementation Decisions

### Mileage & Units (schema-affecting — locked before schema design)
- **D-01:** Kilometer is the app's mileage unit by default. The unit is a **global** app setting conceptually; v1 is km-only with **no toggle UI** — a future km/miles toggle would live global, not per-vehicle.
- **D-02:** Store raw kilometers as `INTEGER` in the database. Conversion (if a unit toggle arrives later) happens at the display layer only — never store value+unit columns.
- **D-03:** The vehicle table has **no odometer column** in Phase 1. Odometer lives on service entries (Phase 2). Vehicle fields: name, make, model, year, license plate, type, photo path.

### Vehicle Photos
- **D-04:** Photos come from **gallery only** via the system photo picker (Compose Multiplatform photo picker / PhotosPicker on iOS). No camera capture and no camera permission in Phase 1.
- **D-05:** Photo storage: the picked image is **persisted into the app's own storage directory** (Android `filesDir` / iOS Documents); the DB stores **only the relative path string** — never a BLOB, never an external-content URI.
- **D-06:** Attaching a photo is **optional**. Empty state on cards/detail shows a placeholder (see D-15).
- **D-07:** Deleting a vehicle **deletes its photo file from disk** too — no orphan files. (Replace-photo on edit reuses the same picker; file handling details are agent discretion.)

### Add/Edit Form & Validation
- **D-08:** The Add/Edit vehicle form is a **single screen** — all fields on one form, no sections/multi-step.
- **D-09:** **Only `name` is required.** Make, model, year, license plate, type, photo are all optional — logging a car must be fast; details can be filled via Edit later.
- **D-10:** Vehicle type picker is a **fixed list: Car, Motorcycle, Truck**. The DB stores the type as a plain string so new types can be added later without a migration.
- **D-11:** UI language is **English only** in v1 — user-facing strings centralized in a strings file from day one (not hardcoded inline), but no localization framework yet.
- **D-12:** License plate is **free text** — optional, no format validation, no format hint.
- **D-13:** Year is a **digits-only numeric text field** (number keyboard), not a dropdown/roller picker.

### List, Detail & Navigation
- **D-14:** Each vehicle card on the list is an **identity card**: leading thumbnail (~64px, uniform) or, when no photo, a circle with the vehicle-type icon (car/motorcycle/truck) on a light background; plus name, `make · model · year` line, and license plate.
- **D-15:** Empty home screen shows a **short message + prominent "Add vehicle" CTA button** (not message-only).
- **D-16:** Vehicle list is ordered **newest first** (most recently added at top).
- **D-17:** Phase 1 scaffolds the **bottom tab-bar shell shared by both platforms** (Compose Multiplatform — no platform-specific UI duplication): **Vehicles** tab (functional) + **Settings** tab (placeholder screen showing only an "App version" row). Later phases fill existing slots rather than retrofitting a shell.
- **D-18:** Tapping a card opens a **Detail screen** — hero photo (or placeholder), name, field rows for all fields, and a reserved "Service history (coming soon)" hint slot (Phase 2 fills it). **Edit and Delete both live on Detail**; delete shows a confirmation dialog: "Delete [name] and all its records?" — never long-press-on-list.
- **D-19:** "Add vehicle" lives in a **FAB** (floating action button, bottom-right) on the vehicle list. The empty state still shows its own big CTA.
- **D-20:** Top app bar across screens: **simple title bar** — title on white, plain back arrow. No large titles, no gesture-only navigation.

### Visual Language
- **D-21:** Accent/highlight color: **agent uses neutral near-black ink on white meanwhile** — the user wants to decide the accent color after seeing the app build (explicitly deferred, tracked below).
- **D-22:** Phase 1 ships a **placeholder app icon** (simple glyph / solid color). Custom icon design is a later polish task.

### the agent's Discretion
- Exact placeholder icon artwork, card spacing tokens, field ordering within the form
- Photo decode/downsizing details (e.g., max dimension for stored file) and thumbnail sizing logic — keep the DB-lean principle from D-05
- Exact wording of empty-state message, confirm dialog text, detail field-row layout
- Photo-picker library wiring details on each platform
- DB write serialization implementation details (STATE.md already locks the pattern: `Dispatchers.IO.limitedParallelism(1)`)

### Deferred Ideas (explicitly parked)
- **Accent color decision** — user explicitly wants to choose after seeing the build (neutral ink is the interim default, D-21)
- **Custom app icon** — later polish task (D-22)
- **km/miles toggle UI** — global setting, future (D-01)
- **Current-odometer field on vehicle** — rejected for Phase 1 schema; revisit only if Phase 2 research demands it (D-03)

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project Definition
- `.planning/PROJECT.md` — core value, constraints (KMP only, local-first, minimal light), Context section (tech stack: KMP + Compose Multiplatform + SQLDelight, notifications via expect/actual)
- `.planning/REQUIREMENTS.md` — VEH-01…VEH-05, DAT-01…DAT-04 definitions; out-of-scope table
- `.planning/ROADMAP.md` — Phase 1 goal, success criteria, and "Architecture Notes (from Research)" (versions, WAL pragma, migration CI targets, serialized DB writes)

### Decisions & Research Context
- `.planning/STATE.md` — Key Decisions table (SQLDelight 2.3.2, Koin 4.2.0, JetBrains Navigation Compose 2.9.0, WAL from day 1, migration CI from Phase 1, serialized writes) and Architecture Constraints (UI composables never touch DB directly; domain models never reference SQLDelight generated types — use mappers; Firebase/Android-only SDKs stay in `androidMain`)

No external specs/ADRs exist — requirements are fully captured above and in the decisions section.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- None — greenfield. Repository contains no source code yet; Phase 1 scaffolds the entire project (Walking Skeleton mode: project setup + routing + one real DB read/write + one real UI interaction).

### Established Patterns
- Stack is locked from init research (STATE.md Key Decisions): Kotlin 2.2.20, Compose Multiplatform 1.8.2, SQLDelight 2.3.2, Koin 4.2.0, JetBrains Navigation Compose 2.9.0
- Bootstrap via kmp.jetbrains.com for correct Gradle version catalog and source set wiring
- DB concurrency pattern: all writes serialized on `Dispatchers.IO.limitedParallelism(1)` to avoid iOS SIGABRT from concurrent NativeSqliteDriver access
- WAL mode: `PRAGMA journal_mode=WAL;` on the iOS NativeSqliteDriver from first launch
- Migration CI: run `generateMainCarRecordSchema` before first release commit; `verifySqlDelightMigration` blocks schema changes without a `.sqm` migration

### Integration Points
- Vehicle Repository → SQLDelight database (schema generated from `.sq` files); UI composables consume domain models via Koin-injected ViewModels, never the DB directly
- Detail screen's "Service history" hint slot is the Phase 2 integration point
- Tab shell's Vehicles tab is the home screen; later phases add content to the Settings-adjacent slots

</code_context>

<specifics>
## Specific Ideas

- The list card must carry identity at a glance: name + make·model·year + plate + uniform leading visual (photo or type-icon circle)
- Empty state must actively guide first use: message + prominent CTA, not a silent background
- Detail screen is deliberately designed as the future home of service history ("coming soon" slot) so Phase 2 slots in without restructuring
- Delete must never be accidental or hidden: confirm dialog on the Detail screen

</specifics>

<deferred>
## Deferred Ideas

- Accent color choice — user will decide after seeing the built app; neutral ink meanwhile
- Custom app icon design — future polish task
- Miles support / unit toggle UI — future global setting
- Per-vehicle unit setting — considered and rejected (global wins)
- Manual drag reorder of vehicle list — considered, not selected

None of these block Phase 1 scope.

</deferred>

---

*Phase: 01-foundation-vehicle-management*
*Context gathered: 2026-10-18*
