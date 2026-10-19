---
phase: 01-foundation-vehicle-management
plan: 09
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, material3, coil3, detail-screen, flow-driven-state, accessibility, em-dash, navigation-scoping]

# Dependency graph
requires:
  - phase: 01-02
    provides: theme tokens (Paper/Mist/Ink/Slate/Hairline/Danger), VehiCareTypography with the Display role on headlineSmall, generated Res package with every detail string
  - phase: 01-03
    provides: domain `Vehicle`, `VehicleRepository.observeVehicle(id)` returning `Flow<Vehicle?>` with null for an absent row
  - phase: 01-04
    provides: @Serializable `VehicleDetailRoute(id)`, the App shell (one KoinApplication + one NavHost + four composable entries), the placeholder Detail destination
  - phase: 01-06
    provides: `VehicleTypeCircle`, `LicensePlateChip`, `VehicleType.labelRes()`, `PhotoStorage.resolve`, the Coil singleton loader registered in App()
  - phase: 01-07
    provides: the one Add/Edit form and its route-id Koin registration mechanism, plus the NotFound-on-read-failure precedent
  - phase: 01-08
    provides: the complete `PhotoStorage` seam (persist/resolve/delete on relative paths)
provides:
  - "`FieldRow` — the two-column label/value row with the em-dash convention, a suppressible 1dp Hairline divider and a trailing slot"
  - "`ComingSoonSlot` — the inert Phase 2 service-history placeholder"
  - "`VehicleDetailUiState` + `VehicleDetailViewModel` — a Flow-driven Loading/NotFound/Content matrix that refreshes itself with no manual reload"
  - "`delete`/`deleting`/`deleteFailed`/`onDeleteErrorShown` declared and explicitly owned by plan 01-10"
  - "The D-18 Detail screen: 4:3 hero (photo or type-circle fallback), Display-size name, five locked field rows, the reserved slot, the recessive destructive action"
  - "BottomTabs scoped to the two tab roots instead of every destination"
affects: [01-10, phase-2-service-log, phase-3-reminders]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Detail state streams from the repository with `WhileSubscribed(5_000)` — a pushed sub-screen that observes its own row needs no refresh callback and no existence guard"
    - "A sealed state's three renderings each own a non-blank frame; a title bar falls back rather than rendering empty while the row is still being read"
    - "Absent optional values render a placeholder instead of collapsing, so a record's screen has the same shape however sparse it is"
    - "Destructive surfaces are authored ahead of the plan that earns them: the button renders, the ViewModel entry point exists, both are explicit stubs until the owning plan lands"
    - "Bottom-bar visibility derived from the current destination with `hasRoute` rather than from the destination that happened to compose the shell"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/FieldRow.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ComingSoonSlot.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailViewModel.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt

key-decisions:
  - "The plate row shows the chip when a plate exists and the em dash when it does not — the plan's literal `trailing = { LicensePlateChip(vehicle.plate) }` would have thrown on every plate-less vehicle, which D-09/D-12 explicitly allow"
  - "No-photo hero fallback is a Mist 4:3 block with a centred 64dp VehicleTypeCircle, reusing a declared component instead of inventing an unlisted placeholder size (locked resolution, recorded below)"
  - "BottomTabs renders only when the current destination is VehicleListRoute or SettingsRoute (locked resolution of the UI-SPEC destination-table ambiguity)"
  - "`uiState` maps a null emission to NotFound and a read failure to the same NotFound, mirroring the Add/Edit precedent — the contract has three states, so no fourth was invented"
  - "The detail entry reads its id with `toRoute<VehicleDetailRoute>().id` and passes `navigate(VehicleEditRoute(detailRoute.id))` for Edit, so the form's route-id mechanism is unchanged from plan 01-07"
  - "The destructive action is an OutlinedButton in the palette's error tone with a decorative leading glyph: worded, ≥48dp and visually the lowest-weight element on the screen"
  - "A 4:3 hero and a Display-size name are separated by `md` 16px and the field block follows at `lg` 24px, all from the seven declared spacing tokens"

patterns-established:
  - "Rendering-only components in `ui/vehicles/components` own their geometry (FieldRow's divider, ComingSoonSlot's container) so screens compose without restating it"
  - "Every state a streaming screen can be in renders inside the Scaffold's content lambda, so scaffold chrome (title bar, back arrow) is present in all of them"

requirements-completed: [VEH-03]

# Metrics
duration: 4min
completed: 2026-10-19
---

# Phase 1 Plan 09: Vehicle Detail Screen Summary

**The D-18 Detail screen — hero photo through the single filesystem seam, the vehicle name as the app's largest text, five always-present field rows with the em-dash convention and the plate chip, an inert Phase 2 service-history slot, and a state stream that shows an edit or a deletion without any manual refresh — with the tab bar now scoped to the two roots**

## Performance

- **Duration:** 4 min
- **Started:** 2026-10-19T07:07:45Z
- **Completed:** 2026-10-19T07:11:59Z
- **Tasks:** 3/3
- **Files modified:** 6 (3 created, 3 modified)

## Accomplishments

- `FieldRow` is the one Detail row primitive: label in `Label`/`Slate`, value in `Body`/`Ink` right-aligned, an `sm` 8px gap between them and a 1dp `Hairline` rule that only draws between rows (`showDivider = false` on the last one, so a block can never end on a dangling rule). A null, empty or whitespace-only value renders the em dash in the recessive tone rather than collapsing the row — the whole point of C-2 — and the `trailing` slot suppresses the value slot entirely, so a row carries either the placeholder or the plate pill and never both.
- `ComingSoonSlot` places the Phase 2 integration point now: `Mist`, 12dp radius, `md` 16px padding, one line of `Label`/`Slate` copy, and deliberately nothing else — no glyph, no chevron, no button, no navigation, because a tappable affordance would promise a capability Phase 1 does not ship. Its KDoc names it as the Phase 2 integration point so no later phase mistakes it for an unfinished feature.
- `VehicleDetailViewModel` streams instead of fetching once: `repo.observeVehicle(vehicleId)` maps a null emission to `NotFound` and a present row to `Content(vehicle)`, with `WhileSubscribed(5_000)` and an initial `Loading`. That single choice delivers both halves of the plan's truth — an edit made on the form appears when the user returns with no refresh callback doing anything, and a row that vanishes becomes the not-available surface by itself, so no race-prone existence check was added to the screen.
- The delete surface exists as an explicit stub with its owner written down: `deleting`/`deleteFailed` are typed `StateFlow<Boolean>` constants set to `false`, `delete(onDeleted)` and `onDeleteErrorShown()` are no-ops whose KDoc names plan 01-10, and `delete` calls nothing on the repository (`NO_DELETE_LEAK_OK`). The screen was therefore laid out once and will not be rewritten twice.
- `AppModule` registers `viewModel { (vehicleId: Long) -> VehicleDetailViewModel(get(), vehicleId) }` — the exact mechanism plan 01-07 used for the form, so both route-id ViewModels are built one way (`DI_OK`).
- `VehicleDetailScreen` renders all three states with real content: a centred progress indicator, the not-available copy with an outlined way back, and the D-18 body in the locked order — 4:3 hero, the name at `Display` (the single largest text in the app), the five field rows Make → Model → Year → License plate → Type, a `2xl` break, `ComingSoonSlot`, a `2xl` break, then the destructive action last.
- The hero photo loads `PhotoStorage.resolve(relativePath)` through Coil with `ContentScale.Crop` inside the declared 4:3 frame, described as "Photo of {name}" with the `title_vehicle_fallback` fallback; the no-photo case renders a `Mist` 4:3 block with a centred 64dp `VehicleTypeCircle`, so the type-circle language is identical on the list and on this screen.
- The app bar is a D-20 simple title bar whose title falls back rather than rendering empty, with `Icons.AutoMirrored.Filled.ArrowBack` and exactly one Edit **text** action; `GESTURE_OK` confirms no deprecated non-mirrored arrow, no `combinedClickable` and no `onLongClick` anywhere in the file.
- `App.kt` now passes `onEdit`/`onBack` to the detail entry and scopes `BottomTabs` to `VehicleListRoute`/`SettingsRoute`, so the two pushed sub-screens — each with its own title bar and back arrow — no longer carry a tab bar that contradicts the UI-SPEC destination table. The shell still holds exactly one `KoinApplication`, one `NavHost` and four `composable<…>` entries.
- Every gate passes: `EM_DASH_OK`, `DIVIDER_CONTROL_OK`, `SLOT_COPY_OK`, `SLOT_INERT_OK`, `DETAIL_STATES_OK`, `STREAMING_OK`, `STUB_MARKED_OK`, `DI_OK`, `BOUNDARY_OK`, `DETAIL_CONTENT_OK`, `HERO_OK`, `DETAIL_NAV_OK`, `GESTURE_OK`, `TABS_PRESENT_OK`, `ROUTE_KNOWN`, `NO_DELETE_LEAK_OK`, plus `FieldRow(` call sites **exactly 5**, zero matches for the whole-`ui/vehicles` spec sweep, and no inline copy in any of the four composable-owning files.
- No regression on the earlier plans: `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`, `:shared:testAndroidHostTest` (21 tests) and `:shared:iosSimulatorArm64Test` (34 tests incl. 7/7 `PhotoStorageTest`) all exit 0.

## Task Commits

Each task was committed atomically:

1. **Task 1: Author the field row and the reserved service-history slot** - `3c2fd09` (feat)
2. **Task 2: Author the Flow-driven detail ViewModel** - `4b825a9` (feat)
3. **Task 3: Author the Detail screen and fix the tab-bar scoping in the app shell** - `7d6016a` (feat)

**Plan metadata:** (docs commit recorded below)

## Files Created/Modified

- `shared/.../ui/vehicles/components/FieldRow.kt` (new) - the two-column row, the em-dash rule, the suppressible 1dp `Hairline` divider and the `trailing` slot for the plate pill
- `shared/.../ui/vehicles/components/ComingSoonSlot.kt` (new) - the reserved `Mist`/12dp/`md` service-history placeholder, inert by construction
- `shared/.../ui/vehicles/VehicleDetailViewModel.kt` (new) - `VehicleDetailUiState` (Loading/NotFound/Content) and the streaming `uiState`, plus the four plan-01-10 members declared as stubs
- `shared/.../ui/vehicles/VehicleDetailScreen.kt` (rewritten) - `VehicleDetailRoute(vehicleId, onEdit, onBack)` replacing the plan 01-04 placeholder, the three-state body and the `DetailFieldBlock`
- `shared/.../di/AppModule.kt` (modified) - the `VehicleDetailViewModel` route-id registration
- `shared/.../App.kt` (modified) - the detail entry gains `onEdit`/`onBack`, and the bottom bar becomes destination-scoped

## Decisions Made

- **The placeholders are per-row, not per-screen.** Every optional field keeps its row and renders the em dash; the screen has one shape for a fully-filled vehicle and a name-only one, which is what C-2 asks for and what makes a later fill-in appear in place rather than shifting the layout.
- **The plate row shows the chip when there is a plate and the em dash when there is not.** The `trailing` slot is built only for a non-blank plate, so the row still renders for a plate-less vehicle (D-09/D-12 allow one) instead of throwing inside the chip's non-blank guard. This is the reconciliation of the plan's must-have truth ("optional fields that were never filled render an em dash") with its plate rule ("the chip, not plain text") — the chip replaces plain text *when a plate exists*, not unconditionally.
- **The no-photo hero fallback reuses the declared component** (locked resolution, recorded as the plan required): a `Mist` 4:3 block with a centred 64dp `VehicleTypeCircle`. Inventing a new hero-placeholder size would have added a dimension UI-SPEC's tables do not sanction, and reusing the circle keeps D-14's icon language consistent between list and detail.
- **`BottomTabs` renders only on the two tab roots** (locked resolution of the UI-SPEC destination-table ambiguity, recorded as the plan required): the destination table lists the shared bar on `VehicleListRoute` only, while plans 01-04…01-08 had it on the app-level `Scaffold` for every destination. It is now derived from `currentBackStackEntryAsState()` with `hasRoute(VehicleListRoute::class) || hasRoute(SettingsRoute::class)`, so Detail and the Add/Edit form get a full-height content area. Rationale: those two are pushed sub-screens with their own simple title bar and back arrow, and a tab bar on a sub-screen contradicts both the destination table and the M3/iOS convention. The plan 01-04 tab-sync behaviour is untouched for the two roots.
- **A read failure maps to `NotFound`, not to a fourth state.** Mirrors plan 01-07's recorded rule: the UI-SPEC contract declares three states for this screen, so an unreadable row surfaces as the same not-available message with the same way back, and no exception detail is logged.
- **The destructive action is an outlined, full-width, error-toned button** with a decorative `Icons.Filled.Delete` glyph and the worded label. UI-SPEC puts deletion at the lowest visual weight but requires it to be plain-text-labelled and ≥48dp; an outlined button at the end of the content satisfies both, and the glyph carries no description because the words already do.
- **The Edit action is a `TextButton` with an explicit 48dp minimum height.** UI-SPEC bans an icon there (the simple title bar stays clean) and separately requires every interactive element to be at least 48dp — M3's `TextButton` default minimum is below that.
- **Hero-to-name and name-to-fields gaps are `md` 16px and `lg` 24px.** UI-SPEC's visual-hierarchy row puts the name "directly under the hero photo", so the tight `md` gap binds the name to its photo while the field block opens at the section-level `lg`.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] A read failure would have left the screen on `Loading` forever**
- **Found during:** Task 2 (detail ViewModel)
- **Issue:** The plan specified only the `null` → `NotFound` and non-null → `Content` mappings, with no failure path. A repository read error would propagate out of the flow, cancel collection, and leave the screen showing the progress indicator permanently — a screen that is neither blank nor truthful, and the one state the UI-SPEC state contract forbids by omission.
- **Fix:** Added `.catch { emit(VehicleDetailUiState.NotFound) }` so an unreadable row surfaces as the same not-available message as a deleted one. This mirrors the rule plan 01-07 already recorded in STATE.md, and no exception detail is logged (T-01-53).
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailViewModel.kt`
- **Verification:** `STREAMING_OK`, `DETAIL_STATES_OK`, `BOUNDARY_OK`; `:androidApp:assembleDebug` exit 0.
- **Committed in:** `4b825a9` (Task 2)

**2. [Rule 1 - Bug] The plan's literal plate row would have thrown on every plate-less vehicle**
- **Found during:** Task 3 (field block)
- **Issue:** The plan's action says "The plate row passes `trailing = { LicensePlateChip(vehicle.plate) }`". `LicensePlateChip` deliberately `require`s a non-blank plate (plan 01-06, C-3), and D-09/D-12 make the plate optional — so for any vehicle saved without one, composing the plate row would have thrown an `IllegalArgumentException` during composition and taken down the screen. The plan's own must-have truth ("optional fields that were never filled render an em dash") and its acceptance criterion ("the plate row uses the trailing chip and not an em dash") cannot both hold literally for a plate-less vehicle.
- **Fix:** The trailing slot is built only for a non-blank plate; with no plate the row falls back to the standard em dash, so the row is always present, the chip appears whenever there is a plate, and the component's non-blank guard is never tripped.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt`
- **Verification:** `DETAIL_CONTENT_OK`, `HERO_OK`, `FieldRow(` = 5; `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` exit 0.
- **Committed in:** `7d6016a` (Task 3)

**3. [Rule 2 - Missing Critical] The Edit action needed an explicit 48dp minimum height**
- **Found during:** Task 3 (app bar)
- **Issue:** UI-SPEC's accessibility contract requires every interactive element to be at least 48dp, and M3's `TextButton` default minimum height sits below that. The plan asked for the Edit text action without naming a size, so the app bar's only action would have shipped under the declared touch-target floor.
- **Fix:** `Modifier.defaultMinSize(minHeight = 48.dp)` on the Edit `TextButton`, the same treatment the form's photo buttons already carry.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt`
- **Verification:** `:androidApp:assembleDebug` exit 0; `GESTURE_OK`.
- **Committed in:** `7d6016a` (Task 3)

### Notes (no code impact)

- **The plan's version context is stale, as the state file warns.** The UI-SPEC's registry-safety paragraph names Coil 3.3.0 and FileKit 0.11.0 while the project pins Coil 3.6.3 and FileKit 0.16.0 (`gradle/libs.versions.toml`). No version was changed: the hero uses only `coil3.compose.AsyncImage` and `PhotoStorage.resolve`, both already shipping since plan 01-06.
- **Two plan verify snippets cannot print their success marker literally.** `grep -Ec … | grep -v ':0$' && echo … || echo …` emits a bare `0` when the count is zero, so `SLOT_INERT_OK`, `GESTURE_OK` and `BOUNDARY_OK` print the count on the first line and the marker on the second. Re-run in the plain `grep -Ec <file>` form all three are clean zeroes, and this is the same behaviour plans 01-06/01-07/01-08 recorded.

**Total deviations:** 3 auto-fixed (1 bug, 2 missing-critical) + 2 notes
**Impact on plan:** Every fix closes a path the plan's own stated truths required — a screen that cannot get stuck on Loading, a plate-less vehicle that cannot crash its own detail screen, and a touch target that respects the declared floor. No scope creep: no new screen, no new state, no new dependency, no schema or route change.

## Issues Encountered

- **`grep -c 'FieldRow('` needed the file authored to satisfy it.** The plan's gate requires exactly 5 occurrences, so the plate row could not be split into a separate composable or expressed as an if/else pair of rows (either would have made it 6), and no KDoc line may contain `FieldRow(`. The block was authored as one private `DetailFieldBlock` with exactly five call sites, measured at 5.
- **No new strings were needed.** Every string this plan renders already existed in `composeResources/values/strings.xml` from plan 01-02 — verified with an explicit per-string existence check before the SUMMARY (`STRINGS_OK`) rather than assumed.
- Environment note: `pwd` reports the case-insensitive path while `git rev-parse --show-toplevel` resolves to `/Users/andika/Proyek Aplikasi/VehiCare`; no file outside the repo was touched and the user's uncommitted `iosApp/iosApp.xcodeproj/project.pbxproj` change was never staged, modified or reverted.

## Known Stubs

Deliberate minimalities with named owners — none block this plan's goal:

- **The destructive button is present but inert.** It renders `Delete vehicle` with the delete glyph, and its click calls `VehicleDetailViewModel.delete(onDeleted = onBack)`, which is an explicit no-op stub documented as plan 01-10's in its own KDoc. `deleting`/`deleteFailed` are typed `StateFlow<Boolean>` constants `false`, and `onDeleteErrorShown()` is a no-op, likewise marked. Plan 01-10 adds the confirmation dialog, the implementation and the photo-file assertion; it turns this on in exactly one place (T-01-56).
- **`ComingSoonSlot` renders no service-history content by design.** It is the reserved Phase 2 integration point (D-18), not an unfinished feature: Phase 2 replaces or wraps the slot without restyling the screen.
- **`DetailFieldBlock` is private to `VehicleDetailScreen.kt`.** Not a stub, but worth recording: plan 01-10 will add its confirmation dialog and Snackbar host inside the same `Scaffold`, and the field block is not exposed for reuse.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **VEH-03's read side is complete**: a user can open any vehicle and see its full detail, and the screen refreshes itself after an edit or an external deletion.
- **Plan 01-10 has a settled surface to extend.** It owns the destructive flow and has: the button already rendered and wired to `VehicleDetailViewModel.delete`, the typed `deleting`/`deleteFailed` flows, `onDeleteErrorShown()`, an `onDeleted` callback already bound to the pop-back, and a `Scaffold` that needs only a `snackbarHost` added. The `REQUIREMENTS`-level `dialog_delete_title`/`dialog_delete_message`/`snackbar_vehicle_deleted`/`error_delete_vehicle` strings already exist.
- **Phase 2 has its anchor**: `ComingSoonSlot` marks where service history goes, placed so the next phase slots content into an existing structure instead of restyling the Detail screen (D-18).
- **The tab-bar scoping is a deliberate, recorded resolution**, not an accident — a later phase adding a third tab root must add its route to the `isTabRoot` check in `App.kt`.
- The plan's human check (open a fully-filled vehicle and a name-only one; the sparse one shows `—` per absent field rather than collapsing; the plate renders as a chip; the name is visibly the largest text; Edit from Detail opens the form and saving returns to Detail with the new values already shown) is deferred to the end-of-phase `/gsd-verify-work` pass per `human_verify_mode: end-of-phase`.
- No blockers. `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and both test tasks exit 0 after the final commit.

---

*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-19*

## Self-Check: PASSED

- All six created/modified source files exist at their committed paths, and all three task commits (`3c2fd09`, `4b825a9`, `7d6016a`) resolve in the git log.
