---
status: testing
phase: 02-service-log
source: [02-VERIFICATION.md]
started: 2026-10-20T07:05:00Z
updated: 2026-10-21T10:58:00Z
---

## Current Test

number: 1
name: 30-second create path — gap re-test (custom chip + multi-select)
expected: |
  Open a vehicle's history → Add service → "Add custom…", type a label, confirm: the label appears
  immediately as a selected chip carrying the Check glyph, and can be deselected and re-selected
  without disappearing. Tap several preset chips in a chosen order, deselect the middle one,
  re-select it, save. The saved entry's detail shows the labels joined by " · " in tap order on at
  most two lines; the history row shows the same line on one line with ellipsis.
awaiting: user response

## Tests

### 1. 30-second create path
expected: Open a vehicle's history, tap Add service, accept today's date, type a mileage, tap a preset type chip, optionally add cost/notes, tap Save. Repeat once using "Add custom…" for the type. The entry is saved and the history shows it immediately; the whole interaction stays under 30 seconds on a normal device; a custom type is stored verbatim and appears as a learned chip on the next add.
result: pending
retest: true
fixed_by: [02-07-PLAN.md, 02-08-PLAN.md]
previous_result: issue
previous_reported: "+ Add Type" tidak berfungsi semestinya, saya menambahkan tapi tidak tertampil. Saya juga mau input type bisa dipilih beberapa
previous_severity: major
note: Fixed in 02-07 (schema v3, ordered serviceTypes) and 02-08 (pure custom-confirm transition, multi-select chips, joined type line). Re-run required — the ViewModel/state chain is proven by executable tests, but the Compose recomposition → rendered pixel has no screen-level test.

### 2. Parts + workshop/mechanic round-trip
expected: Add two parts (name + quantity), a workshop and a mechanic to an entry, save, then open its detail; edit the entry and confirm the values are prefilled. Parts render as "name × qty" in the locked detail order between Cost and Workshop; Workshop and Mechanic render as label/value rows; quantities are digit-only and default to 1; values survive edit round-trip.
result: pass

### 3. Real-device photo lifecycle
expected: On a real device, attach two photos from the gallery (confirm no camera option), remove one before saving, pick another, save, force-quit and relaunch; then edit the entry, remove a saved photo and save. Picks are additive up to 5 with thumbnails; each photo renders from app-private storage after restart; removing a session photo deletes it immediately, removing a saved photo deletes its file only after the save succeeds; abandoning a form with an unsaved picked photo leaves no orphan file.
result: pass

### 4. Edit flow + lower-mileage warning
expected: Open an entry, tap Edit, change date/mileage/type/cost/notes, then set the mileage lower than the previous entry's mileage and save. The form is prefilled exactly once (typing is never overwritten); the lower-mileage warning appears verbatim in the mileage supporting slot and Save stays enabled; after save the detail and the history both reflect the new values without a manual refresh.
result: pass

### 5. History sort + navigation states
expected: Open a vehicle's Service history and exercise both orders: default opening, tap Mileage, tap Date, leave and re-enter; also open from zero entries. Opens newest-date-first; Mileage re-sorts instantly to highest-mileage-first; the control resets to Date on every visit and the choice is never persisted; the Add service FAB is present in Loading, Empty, Content and Error; tapping a row opens that entry; there is no swipe or long-press action.
result: pass

### 6. Delete flows + cascade
expected: Delete one entry, and separately delete a vehicle that has entries: observe the dialog copy, cancel once, then confirm. Cancel removes nothing and leaves the screen intact; confirming the entry delete returns to history with a "Service entry deleted." snackbar and the entry's photos are gone from storage; deleting a vehicle removes all of its entries, their part/photo rows and every entry photo file, followed by the "Vehicle deleted." snackbar.
result: pass

### 7. v1→v2 in-place upgrade + restart persistence
expected: On the Medium_Phone AVD, install the current Phase 1 build, add a vehicle, then install this build in place (do not uninstall). On iOS use a fresh install. Add an entry, force-quit, relaunch. The v1→v2 migration creates service_entry/service_part/service_photo and all four indices without losing the vehicle; a fresh install creates the same schema; the entry and its photos survive the restart on both platforms.
result: pass

### 8. Visual/accessibility parity
expected: Visual and accessibility pass against UI-SPEC with TalkBack (Android) and VoiceOver (iOS): form labels/required markers, selected type-chip palette, entry-detail loading announcement, date-field label/action, photo remove labels, FAB and summary-row copy. Required fields read as required (Service date included), selected chips use the Mist/Paper/Ink treatment rather than M3's default lavender, the entry-detail loading state announces "Loading this service entry", every icon-only control has a spoken label, and spacing/copy match the UI-SPEC contract. Note: info-level review findings IN-01, IN-06, IN-07 are in scope for this check.
result: pass

## Summary

total: 8
passed: 7
issues: 0
pending: 1
skipped: 0
blocked: 0

## Gaps

- truth: "A custom service type confirmed in the Add custom… dialog appears as the selected chip immediately"
  status: resolved
  resolved_by: 02-08-PLAN.md
  retest: pending
  reason: "User reported: '+ Add Type' tidak berfungsi semestinya, saya menambahkan tapi tidak tertampil."
  severity: major
  test: 1
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditViewModel.kt"
      lines: "210-230"
      note: "onCustomTypeConfirmed sets values.serviceType for a new label but leaves learnedTypes unchanged."
    - path: "shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryEditScreen.kt"
      lines: "348-356"
      note: "ServiceTypePicker receives learned = state.learnedTypes, so the new label is not in the rendered options."
    - path: "shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt"
      lines: "62-79"
      note: "allOptions = presets + learned; a selected value outside that list has no chip to render."
  missing:
    - "Add the confirmed new label to the Ready state's learnedTypes (case-insensitive dedupe) before/while selecting it."
    - "Cover the behavior with a ViewModel state test."
  root_cause: "The new custom label is stored only in values.serviceType; the picker renders presets + learnedTypes, so the selection has no chip and is invisible. Confirmed by code trace at HEAD a16c46c."

- truth: "A service entry can carry more than one service type, and the form lets the user toggle several presets/custom types"
  status: resolved
  resolved_by: [02-07-PLAN.md, 02-08-PLAN.md]
  retest: pending
  reason: "User requested: 'Saya juga mau input type bisa dipilih beberapa.'"
  severity: major
  test: 1
  artifacts:
    - path: "shared/src/commonMain/kotlin/com/vehicare/domain/ServiceEntry.kt"
      lines: "18-30"
      note: "serviceType is a single non-null String."
    - path: "shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq"
      note: "service_entry.service_type is a single TEXT NOT NULL column."
    - path: "shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryFormValidation.kt"
      lines: "58-65"
      note: "isSubmittable/toServiceEntry assume one serviceType value."
    - path: "shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceTypePicker.kt"
      note: "Single selected value; chips are one-of."
    - path: "shared/src/commonMain/kotlin/com/vehicare/ui/service/ServiceEntryDetailScreen.kt"
      note: "Renders one type line."
  missing:
    - "Schema v3 with a normalized service_entry_type table (entry_id, position, label) plus migration 2.sqm that backfills existing service_type values."
    - "Domain model and repository/mapper changes for a list of types (learned types sourced from the new table)."
    - "Multi-select affordance in the picker (toggle chips) and form validation requiring at least one type."
    - "Detail, history row, and vehicle summary rendering for multiple types."
    - "Tests: migration backfill, mapper round-trip, learned-type dedupe, multi-select validation."
  root_cause: "Out of the current phase's scope: the data model, schema, and UI were designed around a single serviceType (SVC-01/D-27). Implementing it is a product-scope expansion requiring a schema migration and cross-screen changes."
