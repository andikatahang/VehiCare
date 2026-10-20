---
status: testing
phase: 02-service-log
source: [02-VERIFICATION.md]
started: 2026-10-20T07:05:00Z
updated: 2026-10-20T07:05:00Z
---

## Current Test

number: 1
name: 30-second create path
expected: |
  The entry is saved and the history shows it immediately; the whole interaction stays under
  30 seconds on a normal device; a custom type is stored verbatim and appears as a learned chip
  on the next add.
awaiting: user response

## Tests

### 1. 30-second create path
expected: Open a vehicle's history, tap Add service, accept today's date, type a mileage, tap a preset type chip, optionally add cost/notes, tap Save. Repeat once using "Add custom…" for the type. The entry is saved and the history shows it immediately; the whole interaction stays under 30 seconds on a normal device; a custom type is stored verbatim and appears as a learned chip on the next add.
result: [pending]

### 2. Parts + workshop/mechanic round-trip
expected: Add two parts (name + quantity), a workshop and a mechanic to an entry, save, then open its detail; edit the entry and confirm the values are prefilled. Parts render as "name × qty" in the locked detail order between Cost and Workshop; Workshop and Mechanic render as label/value rows; quantities are digit-only and default to 1; values survive edit round-trip.
result: [pending]

### 3. Real-device photo lifecycle
expected: On a real device, attach two photos from the gallery (confirm no camera option), remove one before saving, pick another, save, force-quit and relaunch; then edit the entry, remove a saved photo and save. Picks are additive up to 5 with thumbnails; each photo renders from app-private storage after restart; removing a session photo deletes it immediately, removing a saved photo deletes its file only after the save succeeds; abandoning a form with an unsaved picked photo leaves no orphan file.
result: [pending]

### 4. Edit flow + lower-mileage warning
expected: Open an entry, tap Edit, change date/mileage/type/cost/notes, then set the mileage lower than the previous entry's mileage and save. The form is prefilled exactly once (typing is never overwritten); the lower-mileage warning appears verbatim in the mileage supporting slot and Save stays enabled; after save the detail and the history both reflect the new values without a manual refresh.
result: [pending]

### 5. History sort + navigation states
expected: Open a vehicle's Service history and exercise both orders: default opening, tap Mileage, tap Date, leave and re-enter; also open from zero entries. Opens newest-date-first; Mileage re-sorts instantly to highest-mileage-first; the control resets to Date on every visit and the choice is never persisted; the Add service FAB is present in Loading, Empty, Content and Error; tapping a row opens that entry; there is no swipe or long-press action.
result: [pending]

### 6. Delete flows + cascade
expected: Delete one entry, and separately delete a vehicle that has entries: observe the dialog copy, cancel once, then confirm. Cancel removes nothing and leaves the screen intact; confirming the entry delete returns to history with a "Service entry deleted." snackbar and the entry's photos are gone from storage; deleting a vehicle removes all of its entries, their part/photo rows and every entry photo file, followed by the "Vehicle deleted." snackbar.
result: [pending]

### 7. v1→v2 in-place upgrade + restart persistence
expected: On the Medium_Phone AVD, install the current Phase 1 build, add a vehicle, then install this build in place (do not uninstall). On iOS use a fresh install. Add an entry, force-quit, relaunch. The v1→v2 migration creates service_entry/service_part/service_photo and all four indices without losing the vehicle; a fresh install creates the same schema; the entry and its photos survive the restart on both platforms.
result: [pending]

### 8. Visual/accessibility parity
expected: Visual and accessibility pass against UI-SPEC with TalkBack (Android) and VoiceOver (iOS): form labels/required markers, selected type-chip palette, entry-detail loading announcement, date-field label/action, photo remove labels, FAB and summary-row copy. Required fields read as required (Service date included), selected chips use the Mist/Paper/Ink treatment rather than M3's default lavender, the entry-detail loading state announces "Loading this service entry", every icon-only control has a spoken label, and spacing/copy match the UI-SPEC contract. Note: info-level review findings IN-01, IN-06, IN-07 are in scope for this check.
result: [pending]

## Summary

total: 8
passed: 0
issues: 0
pending: 8
skipped: 0
blocked: 0

## Gaps
