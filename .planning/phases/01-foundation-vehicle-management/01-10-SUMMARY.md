---
phase: 01-foundation-vehicle-management
plan: 10
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, material3, confirm-dialog, snackbar, single-flight, irreversible-deletion, photo-cleanup, real-filesystem-test, kotlin-coroutines-test, app-shell]

# Dependency graph
requires:
  - phase: 01-02
    provides: theme tokens (Paper/Mist/Ink/Slate/Hairline/Danger), the type scale with the Display role, and every delete string — dialog_delete_title, dialog_delete_message, action_delete_vehicle, action_cancel, snackbar_vehicle_deleted, error_delete_vehicle
  - phase: 01-03
    provides: "domain `Vehicle` and `VehicleRepository.delete(id)` — plus the open obligation this plan discharges: a delete must not orphan the photo file"
  - phase: 01-04
    provides: @Serializable `VehicleDetailRoute`, the App shell (one KoinApplication + one NavHost + four composable entries), and the list destination the post-delete pop returns to
  - phase: 01-06
    provides: the Flow-driven vehicle list (a deleted row is absent at the source of truth on return), `PhotoStorage.resolve`, and the Koin singleton loader
  - phase: 01-07
    provides: the route-id Koin registration mechanism for pushed ViewModels and the one-shot-message-retirement precedent
  - phase: 01-08
    provides: the complete `PhotoStorage` seam (persist/resolve/delete) and the `AppModule` binding `VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)` that this plan's test now proves
  - phase: 01-09
    provides: `VehicleDetailViewModel` streaming states, the declared-and-owned stubs `delete`/`deleting`/`deleteFailed`/`onDeleteErrorShown`, the rendered destructive OutlinedButton wired to a no-op, and a Scaffold awaiting its snackbarHost
provides:
  - "`ConfirmDeleteDialog` — the M3 AlertDialog with the locked copy, a Paper container and the only danger-tinted control in the app"
  - "`VehicleDetailViewModel.delete` made real — single-flight, non-optimistic, failure-preserving; `onDeleted` fires only after the repository returned"
  - "The screen's delete gate and failure path: one dialog-gated `delete` call site, an in-flight disable, and `error_delete_vehicle` retired one-shot after the message"
  - "The shell-hosted success message: `snackbar_vehicle_deleted` is shown on the shell's scope before the pop, so it survives the popped Detail screen"
  - "`VehicleDetailRoute(vehicleId, onEdit, onBack, onDeleted)` — a completed deletion is a distinct signal from plain back navigation"
  - "`VehicleDeletionTest` — 8 real-database/real-filesystem cases proving the row *and* the photo file are gone, another vehicle's file survives, duplicate confirms are dropped, and a failure keeps the vehicle stored"
affects: [phase-2-service-log, phase-3-reminders]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Irreversible actions carry four gates: one confirmation dialog, one call site, a single-flight guard plus its disabled-button half, and no recovery surface anywhere"
    - "Deletion is non-optimistic — nothing leaves the UI locally; the repository's Flow removes the row only once the row is really gone, so a failure needs no rollback"
    - "A message that must outlive the screen that caused it is hosted on the shell (SnackbarHostState + rememberCoroutineScope in App()) and launched before the pop"
    - "One-shot failure flags retire on the blocking message call's dismiss path (`showSnackbar` returns → `onDeleteErrorShown()`), so a recomposition can never replay them"
    - "Real-filesystem tests assert on file existence, not on callback wiring, and the repository under test is bound exactly as the production Koin graph binds it"
    - "ViewModel ordering is asserted deterministically on `Dispatchers.setMain(StandardTestDispatcher)` with `CompletableDeferred` gates — 'still in flight' is opened and closed by the test, never by sleeping"

key-files:
  created:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ConfirmDeleteDialog.kt
    - shared/src/iosTest/kotlin/com/vehicare/ui/vehicles/VehicleDeletionTest.kt
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailViewModel.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt
    - shared/src/commonMain/kotlin/com/vehicare/App.kt

key-decisions:
  - "The dialog pins `containerColor = Paper` and the title to `titleLarge`: M3's defaults would inject an off-palette `surfaceContainerHigh` tint and a Display-sized heading that outranks the vehicle name on the screen behind it"
  - "Confirm is the only danger-tinted control (`colorScheme.error`); Cancel takes `Ink` on the leading side — the safe option must not look destructive, and the destructive one must not look safe"
  - "`onDeleted` was added to `VehicleDetailRoute(vehicleId, onEdit, onBack, onDeleted)` — the plan's interfaces block omitted it, but Task 3's action and key_links require the shell to distinguish a completed deletion from a plain back navigation"
  - "The success Snackbar is launched on the shell scope *before* `navController.popBackStack()`, so `Vehicle deleted.` appears over the list instead of vanishing with the popped Detail screen"
  - "`CancellationException` is rethrown from `delete()` rather than reported as a failure — a torn-down scope is not a failed deletion"
  - "The failure path removes nothing: no optimistic state exists, so `deleteFailed` only reports and the vehicle stays visible and stored"
  - "Confirm closes the dialog before starting the delete call, so the dialog is never left on screen describing an action already decided"
  - "`VehicleDeletionTest` binds `VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)` — the identical binding `AppModule` registers — so the row+file assertion exercises the production seam, not a test-only wiring"
  - "ViewModel delete-order assertions run on `Dispatchers.setMain(StandardTestDispatcher())` with `CompletableDeferred` gates, making the success-order, in-flight and single-flight facts deterministic rather than timed"

patterns-established:
  - "One destructive entry point, one confirmation gate: any future call site of `delete` must go through `ConfirmDeleteDialog` — later authors are told this in the dialog's own KDoc rather than in a phase document"
  - "Shell-level hosting for messages that outlive a destination: the shell owns the SnackbarHostState and the scope; the destination only launches the message and leaves"

requirements-completed: [VEH-04]

# Metrics
duration: 12min
completed: 2026-10-19
---

# Phase 1 Plan 10: Confirmed Vehicle Deletion Summary

**A deletion that destroys for real: the dialog-gated, single-flight delete removes the vehicle's row and its photo file through the real storage seam, returns the user to the list under a shell-hosted "Vehicle deleted." message, and never reports a deletion that did not happen — proven by 8 real-database/real-filesystem cases**

## Performance

- **Duration:** 12 min
- **Started:** 2026-10-19T07:24:25Z
- **Completed:** 2026-10-19T07:36:25Z
- **Tasks:** 3/3
- **Files modified:** 5 (2 created, 3 modified)

## Accomplishments

- `VehicleDeletionTest` is the proof this plan budgeted for: eight cases against a real database and the real simulator filesystem, never a mock. Four repository-level: deleting a photo-owning vehicle removes the row, removes it from the list flow, and removes the photo file (`PhotoStorage.resolve(relativePath).exists()` is asserted false — on the file itself, not on a callback being wired); a photo-less vehicle deletes cleanly; an unknown id is an idempotent no-op; and one vehicle's deletion never touches another vehicle's row or file (T-01-58, closing T-01-18's open obligation from plan 01-03).
- Four ViewModel-level cases follow, deterministic on `Dispatchers.setMain(StandardTestDispatcher())`: exactly one repository call per accepted tap with success reported only after `repo.delete` returned — both reads (row gone, file gone) happen *inside* the callback and are asserted there; a second confirm while the first is still in flight is dropped before it reaches the repository (a `CompletableDeferred` gate holds the first call open — no sleeping); a failure clears the in-flight flag, reports itself and leaves the vehicle stored and observable; `onDeleteErrorShown()` retires the message and touches nothing. The repository under test is `VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)` — the identical binding `AppModule` registers.
- `delete` is real now: guarded single-flight (`if (deleting.value) return`), then `repo.delete(vehicleId)` in `viewModelScope`, clearing `deleting` and invoking `onDeleted` **only after** the repository returned — and only on success. `CancellationException` is rethrown rather than reported as a failure; any other exception clears `deleting`, raises `deleteFailed` and changes nothing else. No optimistic state exists, so there is nothing to roll back (T-01-59, T-01-60). `onDeleteErrorShown()` retires the one-shot flag.
- `ConfirmDeleteDialog` is the single gate: an M3 `AlertDialog` over the locked copy (`dialog_delete_title`, `dialog_delete_message` formatted with the vehicle's display name, `action_delete_vehicle`, `action_cancel`), pinned to the app palette (Paper container; `titleLarge` heading so the name behind the dialog stays the largest text), with both buttons worded and at least 48dp. Confirm is the only danger-tinted control in the app; Cancel is `Ink` and sits on the leading side — the safe option cannot look destructive (T-01-61). Its KDoc states outright that it is the only path to `delete`.
- The screen is gated three ways: the destructive button now only opens the dialog; the file's **single** `delete(` call site is that dialog's confirm action; and the button is disabled while `deleting` — the visible half of the single-flight guard. The dialog closes before the call starts, so it is never left on screen describing an action already decided.
- The failure path is wired end to end: `deleteFailed` drives a `LaunchedEffect` that shows `error_delete_vehicle` in the screen's own Snackbar and then calls `onDeleteErrorShown()` after `showSnackbar` returns — the one-shot retirement mirrors the plan-01-07 precedent, so a recomposition can never replay the message and nothing is removed from the UI locally.
- `App.kt` hosts the success message on the shell: one `SnackbarHostState` and one `rememberCoroutineScope()` live in `App()`, and the detail entry's `onDeleted` launches `snackbar_vehicle_deleted` on that scope **then** pops back — so the message outlives the popped Detail screen and lands over the Flow-driven list, whose deleted card is already gone (T-01-62). The shell still holds exactly one `KoinApplication`, one `NavHost`, four `composable<…>` entries, and one `Scaffold`.
- Every declared gate passes: `:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` exit 0; both test tasks green (`:shared:testAndroidHostTest` 21 tests, `:shared:iosSimulatorArm64Test` 42 tests incl. 8/8 `VehicleDeletionTest`); the sweeps are all clean — the recovery word (`undo|Undo`) in commonMain = 0, dark-theme identifiers = 0, camera = 0, long-press/`combinedClickable` = 0, SQLDelight in `ui/vehicles` = 0, colour literals in the dialog = 0, `delete(` call sites in the screen = exactly 1, `Scaffold` call sites = 1 in each of the two files.
- RED-phase discipline held and is recorded: the repository cases passed on their *first* run because plan 01-08's seam is genuinely wired — a first-run pass during RED is a stop signal, so the assertion's sensitivity was proven empirically with a throwaway probe file (never committed) showing the photo file **survives** under a no-op deleter. The RED gate itself held where it should: all four ViewModel cases failed against the 01-09 no-op stub until the GREEN commit.

## Task Commits

Each task was committed atomically:

1. **Task 1: Write the failing deletion test, then implement the real delete path** (tdd) - `2a09fab` (test/RED), `353f7e4` (feat/GREEN)
2. **Task 2: Author the confirmation dialog with the locked copy** - `c9515bf` (feat)
3. **Task 3: Gate the destructive button behind the dialog and hand the outcome back to the shell** - `9d55729` (feat), `865e78e` (refactor)

**Plan metadata:** `docs(01-10): complete confirmed vehicle deletion plan` — the single docs commit carrying this SUMMARY, STATE.md, ROADMAP.md and REQUIREMENTS.md

## Files Created/Modified

- `shared/.../ui/vehicles/components/ConfirmDeleteDialog.kt` (new) - the D-18 confirm dialog: Paper container, `titleLarge` heading, the locked sentence naming the vehicle, the only danger-tinted control, ≥48dp worded buttons, dismiss ≡ cancel
- `shared/src/iosTest/kotlin/.../ui/vehicles/VehicleDeletionTest.kt` (new) - 8 real-database/real-filesystem cases (row + photo file gone, a neighbour untouched, idempotent unknown id, single-flight, success-only-after-return, failure preserved, message retired), plus the `RecordingVehicleRepository` delegation with recording/gate/failure seams
- `shared/.../ui/vehicles/VehicleDetailViewModel.kt` (rewritten delete surface) - real `delete`: single-flight guard, per-call ordering, `CancellationException` rethrown, `deleteFailed` on real failure only; `onDeleted` only after the repository returned
- `shared/.../ui/vehicles/VehicleDetailScreen.kt` (modified) - dialog gating (`showDeleteDialog`), the screen's only `delete(` call site, in-flight disable, the failure Snackbar + one-shot retirement, `snackbarHost` added, `onDeleted` parameter
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` (modified) - shell `SnackbarHostState` + `rememberCoroutineScope()`, and the detail entry's `onDeleted` launching the success message before the pop

## Decisions Made

- **The dialog is palette-pinned and keeps the app's hierarchy.** `containerColor = Paper` and a `titleLarge` heading, because M3's defaults would have injected `surfaceContainerHigh` (outside the declared palette, C-8) and a Display-sized title that would have outranked the vehicle name on the screen behind the dialog.
- **Confirm is the only danger-tinted control; Cancel is Ink and leading.** A red Cancel would read as the destructive choice and the safe option must not look like the dangerous one (T-01-61); both buttons are worded, not icon-only, and ≥48dp.
- **The success message is hosted by the shell, not by the screen that causes it.** The Detail screen is popped the moment the delete succeeds, so a Snackbar hosted there would vanish with it; `onDeleted` launches `showSnackbar` on the shell's scope first and pops second, and the list is Flow-driven so the card is already gone (T-01-62).
- **`onDeleted` joins the route's signature** (recorded as a deviation below): without it the shell cannot tell a completed deletion from a plain back navigation, and Task 3's own key_links require exactly that distinction.
- **`CancellationException` is rethrown in `delete()`.** A torn-down scope is not a failed deletion and must not raise the failure message; every other exception is a real failure and is reported without hiding the vehicle (T-01-60).
- **The test binds the production seam, not a stand-in.** `VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)` is the identical binding `AppModule` registers, so the file's disappearance proves the shipped wiring (D-07); the ViewModel cases then layer deterministic ordering on top with `Dispatchers.setMain` + `CompletableDeferred` gates instead of sleeps.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `VehicleDetailRoute` had no `onDeleted` parameter to receive the completed deletion**
- **Found during:** Task 3 (gating and shell handoff)
- **Issue:** The plan's interfaces block carried the 01-09 signature `VehicleDetailRoute(vehicleId, onEdit, onBack)`, but Task 3's own action text and the plan's key_links require the App.kt detail entry to distinguish a completed deletion from a plain back navigation (`onDeleted` shows the shell Snackbar then pops back). With the literal signature there was no event on which the shell could ever show the success message.
- **Fix:** Added `onDeleted: () -> Unit` to the route and wired the App.kt detail entry to launch the shell Snackbar then pop.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt`, `shared/src/commonMain/kotlin/com/vehicare/App.kt`
- **Verification:** `delete(` call sites in the screen = 1; `:androidApp:assembleDebug` exit 0.
- **Committed in:** `9d55729` (Task 3)

**2. [Rule 3 - Blocking] `@AfterTest` teardown cannot call suspend cleanup helpers**
- **Found during:** Task 1 (test authoring and teardown)
- **Issue:** The test's scratch cleanup must delete container files, but FileKit's `PlatformFile.delete()` is suspend and kotlin.test's `@AfterTest fun` is not — the teardown as first authored could not call it without an unsafe blocking bridge.
- **Fix:** Scratch source files are removed with the non-suspending `SystemFileSystem.delete(file.toKotlinxIoPath(), mustExist = false)` (wrapped in `runCatching`), stored photo files with the already non-suspend `PhotoStorage.delete`, and database files with `NSFileManager.removeItemAtPath` against the SQLiter-resolved `<NSApplicationSupportDirectory>/databases/` directory (plus `-wal`/`-shm`/`-journal` sidecars).
- **Files modified:** `shared/src/iosTest/kotlin/com/vehicare/ui/vehicles/VehicleDeletionTest.kt`
- **Verification:** `:shared:iosSimulatorArm64Test` green; no scratch artifacts left after the run.
- **Committed in:** `2a09fab` (Task 1)

**3. [Rule 1 - Bug] The irreversibility KDoc tripped the phase-wide recovery-word sweep**
- **Found during:** Task 3 verification (post-commit sweep over commonMain)
- **Issue:** The GREEN commit's KDoc said "there is no undo and no trash". The phase's verification greps `commonMain` for the recovery word literally and matches comments too, so the phase gate failed on its own documentation — the same class of trap as the C-8 `isSystemInDarkTheme` comment already recorded in STATE.
- **Fix:** Reworded to "a deleted vehicle never comes back … the capability does not exist" — the intent (an author looking for a recovery surface should know none exists) kept, the banned surface no longer named.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailViewModel.kt`
- **Verification:** `undo|Undo` sweep over commonMain = 0 matches; all test tasks still green.
- **Committed in:** `865e78e` (dedicated refactor commit)

### Notes (no code impact)

- **RED investigation, recorded for the gate audit:** the four repository-level cases passed on their first run — expected, because plan 01-08's `AppModule` binding already wires `photoDeleter = PhotoStorage::delete`. Since a first-run pass during RED is a stop signal, the assertion's sensitivity was checked empirically with a throwaway probe file (never committed): with a no-op default deleter the photo file survives, and the probe run was `BUILD SUCCESSFUL` — i.e. the committed assertion fails when the seam is unwired. The RED gate proper held: all four ViewModel cases failed against the 01-09 no-op stub until GREEN.
- **One pre-existing sweep match remains and is out of scope:** a `\bSave\b` match in `VehicleEditScreen.kt:99` (a plan-01-07 comment). The file is untouched by this plan; both files this plan authored are clean.
- **`grep -c 'Scaffold'` counts 2 per file** (import line + call); the call sites are exactly 1 in `VehicleDetailScreen.kt` and 1 in `App.kt` — the same behaviour plans 01-06/07/08/09 recorded. Re-run in call-site form, both are clean.
- The plan's human end-to-end check is deferred to the end-of-phase `/gsd-verify-work` pass per `human_verify_mode: end-of-phase`.
- Environment note: `pwd` reports the case-insensitive path while `git rev-parse --show-toplevel` resolves to `/Users/andika/Proyek Aplikasi/VehiCare`; the user's uncommitted `iosApp/iosApp.xcodeproj/project.pbxproj` change was never staged, modified or reverted.

---

**Total deviations:** 3 auto-fixed (2 blocking, 1 bug) + 4 notes
**Impact on plan:** Every fix is demanded by the plan's own key_links or acceptance criteria — a shell that can hear the success event, a teardown that compiles and runs, and a sweep the plan's gates require to be clean. No scope creep: no new screen, no new state, no new dependency, no schema or route change beyond the one parameter the shell handoff needs.

## Issues Encountered

- **The RED gate needed an investigation, not a skip.** Because the repository-level assertions passed immediately (plan 01-08's binding is real), the TDD fail-fast rule fired; rather than deleting the assertions or weakening them, sensitivity was verified with a throwaway probe (`no-op deleter → file survives`, `BUILD SUCCESSFUL`) and the finding recorded here. The probe file was deleted after use and never committed.
- **The `@AfterTest` teardown is where the file-cleanup subtlety lives.** kotlin.test offers no suspend teardown hook, and FileKit's delete is suspend, so the test deliberately uses kotlinx-io's non-suspending delete for scratch sources (wrapped in `runCatching`) alongside `PhotoStorage.delete` for stored paths.
- **`grep -c 'Scaffold'` is 2 per file** (import + call) — the plan's counting form; the call-site count is what matters and is exactly 1 in each file, same caveat as plans 01-06/07/08/09.
- Environment note: as in every plan this phase, `pwd` and `git rev-parse --show-toplevel` disagree in letter-case only; no file outside the repo was touched and the user's uncommitted `project.pbxproj` change was preserved untouched throughout.

## Known Stubs

One deliberate minimality remains, with a named owner — none block this plan's goal:

- **`ComingSoonSlot` still renders no service-history content by design.** It is the reserved Phase 2 integration point (D-18), not an unfinished feature: Phase 2 replaces or wraps the slot without restyling the Detail screen. This plan does not touch it.
- The converse is worth recording: **every stub plan 01-09 declared for this plan is now resolved.** `delete`/`deleting`/`deleteFailed`/`onDeleteErrorShown` are no longer constants or no-ops — `VehicleDetailViewModel.kt` now contains no stub markers, and the screen's destructive button has a real, gated path.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- **VEH-04 is complete.** The last capability of Phase 1 is real: a vehicle can be deleted from its Detail screen behind an explicit confirmation, and the deletion removes the row *and* its photo file, reports success on the shell, reports failure without hiding the vehicle, and cannot be double-fired.
- **Phase 1's implementation is finished** (plans 01-01…01-10 all executed). The plan's human check — create a vehicle with a photo, open its Detail, delete it, confirm: land on the list with "Vehicle deleted." and the card absent; repeat with Cancel and observe nothing is removed; the photo file is gone from the app's storage — is deferred to the end-of-phase `/gsd-verify-work` pass per `human_verify_mode: end-of-phase`.
- **Phase 2 has its anchor unchanged**: `ComingSoonSlot` marks where service history goes (D-18).
- **The delete flow's safety story is documentary as well as structural**: the dialog's KDoc names itself the only path to `delete`, and the phase sweeps keep the recovery word, gesture and DB-import bans at zero.
- No blockers. `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and both test tasks exit 0 after the final commit.

---

*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-19*

## Self-Check: PASSED

- All five created/modified source files exist at their committed paths (`ConfirmDeleteDialog.kt`, `VehicleDeletionTest.kt`, `VehicleDetailViewModel.kt`, `VehicleDetailScreen.kt`, `App.kt`), and all five plan commits (`2a09fab`, `353f7e4`, `c9515bf`, `9d55729`, `865e78e`) resolve in the git log.
