---
phase: 01-foundation-vehicle-management
plan: 06
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, material3, coil3, filekit, vehicle-list, identity-card, accessibility, empty-state, fab]

# Dependency graph
requires:
  - phase: 01-01
    provides: module scaffold, version catalog (Coil 3.6.3, FileKit 0.16.0, material-icons-core 1.7.3), composeResources wiring
  - phase: 01-02
    provides: theme tokens (Paper/Mist/Ink/Slate/Hairline/Danger), VehiCareTypography, strings.xml, the three vehicle-type vectors, generated Res package
  - phase: 01-03
    provides: domain Vehicle/VehicleType, repository with the mapper boundary, SQLDelight WAL database
  - phase: 01-04
    provides: @Serializable routes, App shell (one KoinApplication + one NavHost + BottomTabs), four-state VehicleListViewModel, koinViewModel() pattern
provides:
  - VehicleTypeCircle (64dp Mist circle) + LicensePlateChip (8dp Mist chip) reusable primitives
  - VehicleType.labelRes() / VehicleType.iconRes() shared UI-layer mapping
  - VehicleCard identity row (D-14) with a blank-segment-aware meta line and merged card semantics
  - EmptyState (D-15) with the first-add heading, body copy and filled CTA
  - Final four-state VehicleListScreen with the D-19 FAB (16px/8px insets) and the 3xl bottom content band
  - PhotoStorage.resolve — the resolve-only relative-path filesystem seam (01-08 completes the object)
  - Coil singleton ImageLoader registered with FileKit PlatformFile support at the single app entry
affects: [01-07, 01-08, 01-09, phase-2-service-log, phase-3-reminders]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "UI-layer type→resource mapping keeps Compose Resources out of the domain enum"
    - "Blank-segment-aware joining helper as a pure internal function with a commonTest"
    - "Vehicle card as one merged semantics node whose only gesture is a plain click"
    - "Filesystem seam authored as resolve-only ahead of the plan that completes it"
    - "FileKit PlatformFile registered with Coil's process singleton once, in App()"
    - "FAB placed by explicit Box alignment so declared insets are exact"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleTypeCircle.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/LicensePlateChip.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleCard.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt
    - shared/src/commonMain/kotlin/com/vehicare/util/PhotoStorage.kt
    - shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/components/VehicleMetaLineTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt

key-decisions:
  - "labelRes()/iconRes() live in the UI layer — the domain enum stays plain Kotlin with no Compose Resources dependency"
  - "The type glyph loads with vectorResource (not painterResource): same drawable resource type, matches the shipped BottomTabs and UI-SPEC's verified cross-platform vector path"
  - "The meta line is a pure internal helper (vehicleMetaLine) unit-tested in commonTest, because the acceptance criteria demand a unit-level check"
  - "LicensePlateChip announces 'License plate {plate}' so the merged card reads it as a labelled value (UI-SPEC Card semantics)"
  - "The FAB is placed by a Box overlay with explicit 16px/8px insets — the M3 Scaffold FAB slot cannot express the declared 8px bottom inset"
  - "Coil's PlatformFile support is registered once in App(), not per screen, so a Detail-first composition still gets a loader that understands the photo model"
  - "EmptyState centers its block (plan text) rather than reading the 64px empty-state top offset literally"

patterns-established:
  - "Reusable list primitives live in ui/vehicles/components and are imported by screens, never copied"
  - "Photo models are built only via PhotoStorage.resolve(storedRelativePath); no screen constructs a file object itself"
  - "New screen callbacks are injected from App.kt; screens never hold a NavHostController"

requirements-completed: [VEH-05]

# Metrics
duration: 10min
completed: 2026-10-19
---

# Phase 1 Plan 06: Identity-Card Vehicle List Summary

**D-14 identity cards with photo/type-circle leading visual, separator-correct meta line and plate chip, a D-15 empty state and a D-19 FAB live in every state — the home screen VEH-05 was promised**

## Performance

- **Duration:** 10 min
- **Started:** 2026-10-19T04:49:35Z
- **Completed:** 2026-10-19T04:59:01Z
- **Tasks:** 3/3
- **Files modified:** 8 source files (6 created incl. one test, 2 modified)

## Accomplishments

- `VehicleCard` reads as an identity card: a uniform 64dp leading visual (photo clipped to a 12dp rounded square through `PhotoStorage.resolve`, or the Mist type circle), the name in Title/Ink, a `make · model · year` line in Body/Slate that drops blank segments *and* their separators, and the Mist plate chip only when a plate exists
- The card is announced as one accessibility node (merged descendants: type name, vehicle name, meta line, "License plate {plate}") and its only gesture is a plain M3 `Card` click — no long-press is expressible anywhere on the list (D-18, T-01-37)
- `EmptyState` guides first use: 32px below a 64dp Mist car circle comes the heading, the body copy and a filled accent CTA, with the FAB still present beside it (D-15 + D-19)
- `VehicleListScreen` renders all four matrix states for real — centered progress (announcing itself) on Loading, `EmptyState` on Empty, a newest-first `LazyColumn` with 16px gutters/32px top/64px bottom band on Content, and error copy plus an outlined "Try again" on Error — with the FAB outside the state `when` so it is present in every one
- `VehicleListRoute` now takes `onAddVehicle`/`onOpenVehicle`; `App.kt` wires them to `VehicleEditRoute()` and `VehicleDetailRoute(id)`, reaching Add from the FAB and the empty state, and Detail from a card tap
- The two primitives other plans depend on exist with their recorded names: `VehicleTypeCircle`, `LicensePlateChip`, `VehicleType.labelRes()`, `VehicleType.iconRes()`, and the resolve-only `PhotoStorage.resolve`
- The meta-line rule is enforced by `VehicleMetaLineTest`: all 8 present/absent combinations, blank-after-trim equality with null, and no leading/trailing/doubled separator across 216 mixed combinations

## Task Commits

Each task was committed atomically:

1. **Task 1: Author the two card primitives and the shared vehicle-type mapping** - `e112c66` (feat)
2. **Task 2: Author the identity card with a blank-segment-aware meta line** - `05c0c8e` (feat)
3. **Task 3: Author the empty state and the final four-state list screen, and wire the FAB** - `9e9ac6e` (feat)

**Plan metadata:** (docs commit recorded below)

## Files Created/Modified

- `shared/.../ui/vehicles/components/VehicleTypeCircle.kt` — the 64dp Mist circle + `labelRes()`/`iconRes()` mappings (plan 01-07's picker reuses the labels)
- `shared/.../ui/vehicles/components/LicensePlateChip.kt` — the 8dp-radius Mist chip in Label/Ink, refusing a blank plate outright
- `shared/.../ui/vehicles/components/VehicleCard.kt` — the D-14 row, the `vehicleMetaLine` helper and the card's merged semantics
- `shared/.../ui/vehicles/components/EmptyState.kt` — the D-15 block (visual, heading, body, filled CTA)
- `shared/.../ui/vehicles/VehicleListScreen.kt` — rewritten: all four states, FAB in every state, callbacks injected
- `shared/.../util/PhotoStorage.kt` — the resolve-only seam: `PlatformFile(FileKit.filesDir, relativePath)` resolved fresh on every call
- `shared/.../App.kt` — the list entry now passes the two callbacks; the Coil singleton loader is registered with FileKit's epoch-safe fetcher
- `shared/src/commonTest/.../VehicleMetaLineTest.kt` — the unit-level check the acceptance criteria asked for

## Decisions Made

- **`vectorResource`, not `painterResource`** for the type glyph: same `DrawableResource` argument, but it matches the shipped `BottomTabs` and UI-SPEC's verified cross-platform vector path. The declared `iconRes(): DrawableResource` interface is unchanged.
- **FAB via a Box overlay** rather than the `Scaffold` FAB slot: the M3 slot owns its own end/bottom padding and cannot produce the declared `sm` 8px bottom inset. The overlay makes both insets exact (16px right, 8px bottom) and keeps the FAB outside the state `when`.
- **Coil registration lives in `App()`**: `setSingletonImageLoaderFactory { ImageLoader.Builder(it).components { addPlatformFileSupport() }.build() }` runs above every screen, so the list card, plan 01-08's form photo and plan 01-09's Detail hero all load photos — including when the nav stack is restored straight onto Detail. Registering it inside the card would have left those surfaces with Coil's default loader, which knows nothing about `PlatformFile`.
- **The chip carries the plate label semantics**: UI-SPEC requires the merged card to announce "License plate {plate}", and `cd_license_plate` exists for exactly this; clearing and re-setting the chip's semantics avoids double-speaking the raw value.
- **Empty state centering** (plan text) over the raw `3xl` top-offset reading, which would contradict it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Registered FileKit's PlatformFile support with Coil in `App.kt`**
- **Found during:** Task 2 (VehicleCard implementation)
- **Issue:** The plan named the `filekit-coil` fetcher but no step in the phase registered it. A `PlatformFile` model with no registered mapper/fetcher fails in Coil, so every photo thumbnail (and later the form preview and the Detail hero) would silently render nothing.
- **Fix:** one `setSingletonImageLoaderFactory { … addPlatformFileSupport() … }` call at the top of `App()`, above `VehiCareTheme` — the single rendering path (D-17), before any image request anywhere. Nothing else in `App.kt` was touched: the KoinApplication, the Scaffold/BottomTabs wiring and the other three `composable<…>` entries are unchanged.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/App.kt`
- **Verification:** `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` both exit 0; `App.kt` still holds exactly one `KoinApplication`, one `NavHost` and four `composable<…>` entries.
- **Committed in:** `9e9ac6e` (Task 3 commit)

**2. [Rule 2 - Missing Critical] Labelled the plate chip's semantics**
- **Found during:** Task 2 (VehicleCard accessibility)
- **Issue:** The plan's Task 2 action requires the merged card to announce "License plate {plate}", but a bare `Text` inside a merged node announces only the raw value — the UI-SPEC Card-semantics row would have failed.
- **Fix:** `LicensePlateChip` clears and sets its own semantics with the `cd_license_plate` template, so card and (later) Detail both read the labelled value once.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/LicensePlateChip.kt`
- **Verification:** `:androidApp:assembleDebug` + `:shared:compileKotlinIosSimulatorArm64` exit 0; no user-facing literal added (copy stays in `Res.string.*`).
- **Committed in:** `e112c66` (Task 1 commit) — the chip was authored with its semantics in the same pass

### Planned-shape additions (executor follow-through, not auto-fixes)

- **`shared/src/commonTest/kotlin/com/vehicare/ui/vehicles/components/VehicleMetaLineTest.kt`** — Task 2's acceptance criteria require "a unit-level check on the joining helper"; the test is that check and runs on both targets (`testAndroidHostTest`, `iosSimulatorArm64Test`).
- **`vectorResource` vs the plan's `painterResource`** (see Decisions) — same interface, project-consistent mechanism.

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical) + 2 planned-shape additions executed inline
**Impact on plan:** Both auto-fixes close gaps that would have shipped broken UI or a failed accessibility contract; neither changes a declared interface, so plans 01-07/01-08/01-09 are unaffected.

## Issues Encountered

- **The first unit-test expectation was wrong, the test caught it:** `vehicleMetaLine("  ", " Corolla ", "")` was first asserted as `Toyota · Corolla`; a whitespace-only segment trims to blank and drops out entirely, so the correct value is `Corolla`. The expectation was fixed and the suite is green — evidence the check is real rather than vacuous.
- **Two plan verify commands are self-defeating when run literally:** the `NO_LONG_PRESS` and `NO_ABSOLUTE_PATH` gates pipe `grep -Ec` into `grep -v ':0$'`, but a bare count never carries the `file:count` colon form, so the violation branch prints even at zero matches. Both were re-run in the `grep -rEc <file>` form (which the `DOMAIN_ONLY` gate already uses) and print `NO_LONG_PRESS_OK` / `NO_ABSOLUTE_PATH`. A verifier running the literal snippets should not read the first branch as a real finding.
- Environment note: `pwd` printed the case-insensitive path while `git rev-parse --show-toplevel` and the toolchain resolve to `/Users/andika/Proyek Aplikasi/VehiCare`; no file outside the repo was touched and the user's uncommitted `iosApp/iosApp.xcodeproj/project.pbxproj` change stays untouched.

## Known Stubs

Deliberate minimalities with owners — none block this plan's goal:

- `PhotoStorage` is resolve-only **by design**: plan 01-08 (wave 6) adds `persist`, `delete` and the downscale step to this same object — an extension, never a second storage seam.
- Inherited from plan 01-04 and untouched here: the Add/Edit form is still the name-only slice (01-07) and `VehicleDetailScreen` is still a placeholder (01-09).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- VEH-05 is satisfied in its final form: newest-first list, identity cards, guiding empty state, FAB to Add from every state, card tap to Detail.
- `VehicleTypeCircle`/`labelRes()` are ready for 01-07's type picker; `LicensePlateChip` and the card primitives are ready for 01-09's Detail screen; `PhotoStorage.resolve` is the seam 01-08 extends.
- The human check for this plan (two vehicles differing in blank optional fields, no dangling separators, last card clear of the FAB) is deferred to the end-of-phase `/gsd-verify-work` pass per `human_verify_mode: end-of-phase`.
- No blockers. `:androidApp:assembleDebug` + `:shared:compileKotlinIosSimulatorArm64` + both test tasks exit 0 after the final commit.

---
*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-19*

## Self-Check: PASSED

All 8 created/modified source files exist on disk and all 3 task commits (`e112c66`, `05c0c8e`, `9e9ac6e`) are present in git history.
