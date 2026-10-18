# Phase 1: Foundation & Vehicle Management - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-18
**Phase:** 01-foundation-vehicle-management
**Areas discussed:** Mileage units, Vehicle photos, Add/Edit form & validation, Vehicle list & empty state, Navigation shell, Detail & edit entry, Photo card look, License plate input, Year field input, Settings tab content, App icon, Accent color, Add button placement, Top bar style

---

## Mileage Units

| Option | Description | Selected |
|--------|-------------|----------|
| Kilometer | Most natural for Indonesian users; SEA competitor apps default km | ✓ |
| Miles | For US/UK users | |
| Choose at first launch | km/miles pick on first launch, stored as global preference | |

| Option | Selected |
|--------|----------|
| Global setting (one toggle applies to all vehicles) | ✓ |
| Per-vehicle unit field | |
| km only, no toggle ever | |

| Option | Selected |
|--------|----------|
| Store raw km INTEGER, convert on display | ✓ |
| Store value + unit column per entry | |

| Option | Selected |
|--------|----------|
| No odometer column on vehicle in Phase 1 | ✓ |
| Add current_odometer_km now (one migration cost) | |

**User's choice:** Kilometer · global setting · store km, convert on display · no odometer column
**Notes:** Research flagged "decide before Phase 1 schema is finalized" — resolved. Per-vehicle unit rejected; global wins.

---

## Vehicle Photos

| Option | Selected |
|--------|----------|
| Gallery only (system photo picker) — no camera permission code in Phase 1 | ✓ |
| Camera + gallery | |

| Option | Selected |
|--------|----------|
| File path in DB (persist in app storage dir, DB stores relative path) | ✓ |
| BLOB in database | |

| Option | Selected |
|--------|----------|
| Optional — placeholder when empty | ✓ |
| Required | |

| Option | Selected |
|--------|----------|
| Delete file too (no orphans) | ✓ |
| Keep file on disk | |

**User's choice:** Gallery only · file path in DB · optional · delete file with vehicle
**Notes:** Storage question was interrupted once by a session gap; re-presented as plain text and answered `1` (file path in DB).

---

## Add/Edit Form & Validation

| Option | Selected |
|--------|----------|
| Single screen, all fields | ✓ |
| Sectioned / multi-step | |

| Option | Selected |
|--------|----------|
| Only name required | ✓ |
| Strict: most fields required | |

| Option | Selected |
|--------|----------|
| Fixed list: Car, Motorcycle, Truck | ✓ |
| Longer list + Other | |

| Option | Selected |
|--------|----------|
| English only | ✓ |
| Indonesian only | |
| Bilingual (id + en) | |

**User's choice:** Single screen · name required only · 3-type fixed list · English only
**Notes:** Type stored as string so future types need no migration. Strings centralized in a strings file from day one.

---

## Vehicle List & Empty State

| Option | Selected |
|--------|----------|
| Identity card (photo/placeholder, name, make·model·year, plate) | ✓ |
| Compact rows (name + plate) | |

| Option | Selected |
|--------|----------|
| Message + CTA | ✓ |
| Message only | |

| Option | Selected |
|--------|----------|
| Newest first | ✓ |
| Alphabetical | |
| Manual drag | |

| Option | Selected |
|--------|----------|
| Confirm dialog ("Delete [name] and all its records?") | ✓ |
| Instant + undo | |

**User's choice:** Identity card · message+CTA · newest first · confirm dialog
**Notes:** None.

---

## Navigation Shell

| Option | Selected |
|--------|----------|
| Tab bar now (scaffold once, later phases fill slots) | ✓ |
| Single flow, tabs later | |

| Option | Selected |
|--------|----------|
| Vehicles + Settings placeholder | ✓ |
| Vehicles only | |
| Vehicles + 2 placeholders (Service Log, Reminders) | |

| Option | Selected |
|--------|----------|
| Shared bottom bar both platforms | ✓ |
| iOS-adapted tabs | |

**User's choice:** Tab bar now · Vehicles + Settings · shared bottom bar both platforms
**Notes:** Shared bar honors success criterion "no platform-specific UI duplication".

---

## Detail & Edit Entry

| Option | Selected |
|--------|----------|
| Detail screen (scaffold for Phase 2 history) | ✓ |
| Straight to edit form | |

| Option | Selected |
|--------|----------|
| Delete on Detail with confirm | ✓ |
| Long-press on list | |

| Option | Selected |
|--------|----------|
| Hero + field rows (+ service-history hint slot) | ✓ |
| Enlarged card | |

**User's choice:** Detail screen · delete on Detail · hero + field rows
**Notes:** Detail is the Phase 2 integration point.

---

## Photo Card Look

| Option | Selected |
|--------|----------|
| Leading thumbnail (~64px) | ✓ |
| Banner crop on card | |

| Option | Selected |
|--------|----------|
| Type icon circle placeholder | ✓ |
| Generic placeholder | |

**User's choice:** Leading thumbnail · type-icon circle
**Notes:** Hero image reserved for Detail screen.

---

## License Plate Input

| Option | Selected |
|--------|----------|
| Free text | ✓ |
| Free text + format hint | |
| Validated Indonesian format | |

**User's choice:** Free text, optional, no validation
**Notes:** None.

---

## Year Field Input

| Option | Selected |
|--------|----------|
| Digits-only text field | ✓ |
| Dropdown/roller picker | |

**User's choice:** Digits-only text field
**Notes:** Fast for classic-car owners; avoids long scroll lists.

---

## Settings Tab Content

| Option | Selected |
|--------|----------|
| Version row only (bare placeholder) | ✓ |
| Version + disabled km/miles toggle row | |

**User's choice:** Version row only
**Notes:** Unit toggle home comes when the toggle itself ships.

---

## App Icon

| Option | Selected |
|--------|----------|
| Placeholder now; custom icon later | ✓ |
| Custom icon in Phase 1 | |

**User's choice:** Placeholder now
**Notes:** Keeps Phase 1 code-focused.

---

## Accent Color

| Option | Selected |
|--------|----------|
| Neutral ink | |
| Single accent blue | |
| Decide later (ink now) | ✓ |

**User's choice:** Decide later — agent uses neutral near-black ink on white meanwhile
**Notes:** Explicitly deferred until the user sees the built app.

---

## Add Button Placement

| Option | Selected |
|--------|----------|
| FAB bottom-right | ✓ |
| Top bar button | |

**User's choice:** FAB; empty state keeps its own CTA
**Notes:** None.

---

## Top Bar Style

| Option | Selected |
|--------|----------|
| Simple title bar | ✓ |
| Large title | |
| No bar (gesture) | |

**User's choice:** Simple title bar, white, plain back arrow
**Notes:** None.

---

## the agent's Discretion

- Placeholder icon artwork, card spacing tokens, form field ordering
- Photo decode/downsizing details and thumbnail logic (keeping DB lean)
- Exact empty-state/dialog wording, detail field-row layout
- Photo-picker platform wiring, DB write serialization implementation

## Deferred Ideas

- Accent color decision (explicit user deferral)
- Custom app icon design
- km/miles toggle UI (global setting, future)
- Manual drag reorder of vehicle list (considered, not selected)
