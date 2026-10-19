# Phase 2: Service Log - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-10-19
**Phase:** 2-service-log
**Areas discussed:** Service type taxonomy, Entry form & fast capture, History layout & sorting

---

## Service Type Taxonomy

### Which preset service types should the picker offer out of the box?

| Option | Description | Selected |
|--------|-------------|----------|
| Core six | Oil change · Tire rotation · Brake service · Inspection · Battery · Filters — covers what research says users expect, without crowding the picker | ✓ |
| Extended ten | Adds Transmission service · Coolant flush · Spark plugs · Wheel alignment — more coverage, longer picker | |
| Custom list | Ship only what the user actually uses | |

**User's choice:** Core six
**Notes:** None beyond the selection.

### How should custom (non-preset) service types behave after the first time you use one?

| Option | Description | Selected |
|--------|-------------|----------|
| Learned suggestions | A custom label typed once appears as a chip on later entries; list derived from logged entries, nothing to manage; plain-string storage | ✓ |
| Free text, no memory | A custom type is only a label on the entry that used it; retype every time | |
| Managed list in Settings | Add/rename/delete screen in Settings | |

**User's choice:** Learned suggestions
**Notes:** No managed-type screen.

### Should learned custom types be shared across all vehicles, or scoped to the vehicle that used them?

| Option | Description | Selected |
|--------|-------------|----------|
| Shared across vehicles | One vocabulary for the whole app; a custom type learned on one car is offered on every car | ✓ |
| Per vehicle | Custom types suggested only for the vehicle that used them | |

**User's choice:** Shared across vehicles
**Notes:** Presets remain global in either case; the choice makes custom types global too.

### How should the service type picker be presented in the entry form?

| Option | Description | Selected |
|--------|-------------|----------|
| Wrapping chips | Presets + learned customs as tappable chips; "Add custom…" chip opens a one-field prompt; matches the vehicle-type picker language | ✓ |
| Dropdown menu | A single dropdown field; compact but hides options and still needs a dialog for custom entry | |
| Bottom sheet | Full-height scrollable sheet with search; heavier than needed for 6 presets | |

**User's choice:** Wrapping chips
**Notes:** None beyond the selection.

---

## Entry Form & Fast Capture

### Which fields must be filled to save a service entry?

| Option | Description | Selected |
|--------|-------------|----------|
| Date + type + mileage | Mileage anchors the Phase 3 reminder series; date defaults to today, type is one tap, mileage pre-fills | ✓ |
| Date + type only | Fastest possible entry; an entry without mileage cannot anchor a mileage reminder | |
| Date only | Type falls back to a generic label; vague history and nothing for reminders to attach to | |

**User's choice:** Date + type + mileage
**Notes:** Cost, notes, parts, workshop/mechanic and photos stay optional.

### How should the mileage field behave when creating an entry?

| Option | Description | Selected |
|--------|-------------|----------|
| Prefill + warn, allow | Start at the most recent entry's mileage; a lower value shows an inline warning but can still be saved (odometer replacement is legitimate) | ✓ |
| Prefill + block lower | Blocks Save until corrected; a replaced/clustered odometer becomes unfixable without editing history first | |
| Prefill, no validation | Least friction, but a typo silently corrupts the mileage series | |

**User's choice:** Prefill + warn, allow
**Notes:** Warning is inline; not a dialog, not a blocker.

### How should cost be captured and displayed?

| Option | Description | Selected |
|--------|-------------|----------|
| Rupiah, no decimals | `Rp 450.000` with thousands separators; whole rupiah; stored as an integer | ✓ |
| US dollars, 2 decimals | `$450.00`; stored as integer cents | |
| No symbol, numeric only | Currency-neutral grouped number, integer minor units | |

**User's choice:** Rupiah, no decimals
**Notes:** Money is stored as an integer, never floating point.

### How should the entry form be laid out?

| Option | Description | Selected |
|--------|-------------|----------|
| Single screen, essentials first | One scrolling form: date · mileage · type · cost, then notes, parts, workshop/mechanic, photos; submit at the bottom (D-08 pattern) | ✓ |
| Two steps | Essentials screen + optional "Add details" screen; adds a destination and a half-saved state | |
| Collapsible details | One screen with a collapsed "More details" section; adds a tap and hides SVC-02/03/04 | |

**User's choice:** Single screen, essentials first
**Notes:** The form ends with the submit button, as in the Phase 1 vehicle form.

**Follow-up check:** the user chose "Next area" rather than another round of questions. Parts-row mechanics, date picker/backdating, workshop/mechanic fields and notes therefore became explicit agent discretion in CONTEXT.md.

---

## History Layout & Sorting

### Where should the full service history for a vehicle live?

| Option | Description | Selected |
|--------|-------------|----------|
| Dedicated screen | Detail's slot becomes a compact summary row (count + last date) that opens a Service history screen owning the sort control and the "Add service" FAB | ✓ |
| In-place on Detail | Replace the slot with the full list; one screen per vehicle, but Detail becomes a long scroll | |
| Preview + full screen | 2–3 recent entries inline plus "View all"; richest but duplicates row rendering | |

**User's choice:** Dedicated screen
**Notes:** Detail stays short; the history list gets room to grow.

### What should each history row show?

| Option | Description | Selected |
|--------|-------------|----------|
| Type title + meta line | Service type as title; `date · mileage` meta; cost trailing — mirrors the VehicleCard language | ✓ |
| Timeline rail | Vertical date rail with entries attached; new component, costs horizontal space | |
| Table-like columns | Type / date / mileage / cost aligned; scannable but cramped on a phone | |

**User's choice:** Type title + meta line
**Notes:** Row tap opens the entry detail.

### How should the date/mileage sort be chosen, and does it persist?

| Option | Description | Selected |
|--------|-------------|----------|
| Segmented Date \| Mileage | Two-segment control; both descending; Date default; exactly what SVC-05 asks with the fewest controls | ✓ |
| Sort menu, 4 options | Date newest/oldest, mileage high/low; more control, more to parse | |
| Segmented + remembered | Same control but persists the last choice; extra persisted state | |

**User's choice:** Segmented Date | Mileage
**Notes:** Not persisted across visits.

### What happens when you tap a history row?

| Option | Description | Selected |
|--------|-------------|----------|
| Entry detail screen | Read-only entry detail; Edit in the title bar; Delete at the bottom behind a confirm dialog (D-18 pattern) | ✓ |
| Straight to edit form | Fewer surfaces, but no read-only view and delete lives inside an edit screen | |
| Expandable rows | Expand in place; expansion state and delete-inside-a-list are awkward on mobile | |

**User's choice:** Entry detail screen
**Notes:** Photos and parts get a proper home on the detail screen.

**Follow-up check:** the user chose "I'm ready for context" — no additional gray areas.

---

## the agent's Discretion

- **Date input:** M3 date picker, defaults to today; backdating allowed; no range restriction.
- **Parts capture:** inline repeating rows (name + quantity, quantity default 1, numeric), add/remove controls.
- **Workshop/mechanic:** two optional free-text fields, no autocomplete.
- **Notes:** optional multi-line free text.
- **Photos (area not selected):** one-or-many per entry, gallery-only, relative paths via `PhotoStorage`, files deleted with the entry; storage shape (expected: child table), maximum count, thumbnail layout and whether a full-screen viewer ships are discretionary.
- **Entry detail delete copy** beyond the two locked strings.
- **Empty-state and summary-row wording.**
- **Sort tie-breaking and exact ordering SQL.**
- **Learned-type query shape** and case-insensitive de-duplication against presets.

## Deferred Ideas

- Search / filter history (research P2; own phase)
- Cost totals / spending analytics (v2, COST-01/02)
- PDF export (v2, EXP-01)
- Managed service-type list in Settings (rejected in favour of learned suggestions)
- Extended preset list — addable later without a migration
- Reminders per service type (Phase 3)
