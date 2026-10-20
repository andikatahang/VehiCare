---
phase: 02-service-log
fixed: 2026-10-20T06:52:00Z
status: all_fixed
findings_in_scope: 13
fixed_count: 13
skipped: 0
iteration: 1
scope: critical_and_warning
commits:
  - 6123471
  - ec3b2bc
  - c607826
  - bf2906e
  - 8d5b795
---

# Code Review Fix Report — Phase 02 (Service Log)

Findings from `02-REVIEW.md` classified Critical or Warning were fixed. Info-level
observations (IN-01…IN-07) were intentionally left out of scope.

## Critical

| ID | Finding | Fix | Commit |
|----|---------|-----|--------|
| CR-01 | Picking a photo deleted or queued every existing photo for deletion | Removed the replacement loop in `onPhotoPicked`; the strip is additive (up to 5 photos), so already-listed paths are neither deleted nor superseded | `ec3b2bc` |
| CR-02 | `catch` sat outside the `flatMapLatest` chain, making retry inert | Moved map/catch inside the per-query flow and extracted `observeHistoryUiState` for testing | `c607826` |

## Warning

| ID | Finding | Fix | Commit |
|----|---------|-----|--------|
| WR-01 | Mileage error condition compared a value to itself | Track "cleared by the user" in the form, show the required error only then, and render the formatted preview | `bf2906e` |
| WR-02 | Edit-load failure could wipe a filled form or strand Loading | Gate the catch on the first load and resolve seeding failures to `NotFound` | `ec3b2bc` |
| WR-03 | Add-mode seeding overwrote typed input and had no failure path | Seed the date synchronously; merge the async prefill only into blank fields; catch prefill failures | `ec3b2bc` |
| WR-04 | Save failure used vehicle copy and had no retry | Use `error_save_entry` and a `SnackbarResult.ActionPerformed` re-submit | `bf2906e` |
| WR-05 | Leaving the form during a save could delete committed files | Hold submitted photos out of the abandonable set while saving, restoring them only on failure | `ec3b2bc` |
| WR-06 | Photo remove control had a 16dp target and no label | 48dp target around the 24dp visual plus a `contentDescription` semantics node | `8d5b795` |
| WR-07 | UI-SPEC input caps were unenforced | Added `MAX_*` constants and applied them at all five inputs | `6123471`, `bf2906e` |
| WR-08 | Part quantity accepted non-digits, silently saving as 1 | Added `sanitizeQuantity` and routed the ViewModel through it | `6123471`, `ec3b2bc` |
| WR-09 | Chips wrapped by a hardcoded 360px estimate | Replaced with `FlowRow` | `8d5b795` |
| WR-10 | Date field overlay was an unlabeled clickable | Added button role, click label, and value `contentDescription` | `8d5b795` |
| WR-11 | Zero-photo state was a tiny tile | Render the specified full-width 16:9 Mist add block | `8d5b795` |

## Verification

```
./gradlew :androidApp:assembleDebug \
          :shared:compileKotlinIosSimulatorArm64 \
          :shared:testAndroidHostTest \
          :shared:iosSimulatorArm64Test \
          :shared:verifyCommonMainVehiCareDatabaseMigration
BUILD SUCCESSFUL
```
