---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Ready to plan
stopped_at: Phase 3 context gathered
last_updated: "2026-10-21T16:38:22.998Z"
progress:
  total_phases: 3
  completed_phases: 2
  total_plans: 18
  completed_plans: 18
  percent: 67
---

# STATE: VehiCare

**Project:** VehiCare
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

---

## Current Position

Phase: 3
Plan: Not started

| Field | Value |
|-------|-------|
| **Active Phase** | Phase 3: Reminders & Dashboard (ready to plan) |
| **Active Plan** | Not started |
| **Status** | **Phase 2 complete 2026-10-21.** UAT 8/8 passed, including the gap-closure re-test (custom chip renders immediately, multi-select toggles). Security audit threat-secure: 50/50 threats closed (`02-SECURITY.md`, `threats_open: 0`). Verification `status: passed` (52/52 must-haves, 7/7 requirements). Phase 3 has no CONTEXT.md yet. |
| **Progress** | `[███████░░░] 2/3 phases (67%)` |

```
Phase 1 [Complete] → Phase 2 [Complete] → Phase 3 [Not Started]
                                             ↑
                                          Current
```

---

## Phase Summary

| Phase | Name | Status |
|-------|------|--------|
| 1 | Foundation & Vehicle Management | Complete (2026-10-19) |
| 2 | Service Log | Complete (2026-10-21) |
| 3 | Reminders & Dashboard | Not started |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases completed | 2 / 3 |
| Plans completed | 18 / 18 |
| Requirements covered | 17 / 21 |
| Sessions | 14 |

Per-plan: `Phase 1 / 01` — ~28min, 3 tasks, 63 files · `Phase 1 / 02` — 17min, 3 tasks, 15 files · `Phase 1 / 03` — 20min, 3 tasks, 17 files · `Phase 1 / 04` — 24min, 3 tasks, 17 files · `Phase 1 / 05` — 11min, 3 tasks, 3 files · `Phase 1 / 06` — 10min, 3 tasks, 8 files · `Phase 1 / 07` — 21min, 3 tasks, 6 files · `Phase 1 / 08` — 21min, 3 tasks, 6 files · `Phase 1 / 09` — 4min, 3 tasks, 6 files · `Phase 1 / 10` — 12min, 3 tasks, 5 files · `Phase 2 / 01` — 11min, 3 tasks, 14 files · `Phase 2 / 02` — 15min, 2 tasks, 7 files · `Phase 2 / 03` — 45min, 3 tasks, 16 files.

---
| Phase 2 P4 | 45min | 3 tasks | 15 files |
| Phase 2 P5 | 32min | 3 tasks | 10 files |
| Phase 2 P6 | 15min | 2 tasks | 7 files |
| Phase 2 P7 | 10min | 3 tasks | 18 files |
| Phase 02 P08 | 30min | 2 tasks | 13 files |

## Accumulated Context

### Key Decisions

| Decision | Rationale |
|----------|-----------|
| SQLDelight 2.4.1 over Room KMP | KMP community consensus; Room KMP still has iOS gaps |
| Koin 4.2.2 for DI | KMP-native, no kapt, `koinViewModel()` works in `commonMain` |
| JetBrains Navigation Compose 2.9.2 | Type-safe routes, native iOS back gesture |
| Wave 0 gate → **option-b**, then the wizard version set adopted over its pins | The originally locked pins were internally inconsistent and could not build here; the user then directed that the VehiCare project's own working set be kept. Every adopted pin is now backed by an executed build — stronger evidence than the contract research. |
| Module stays `:shared` / framework `Shared` | Renaming to `:composeApp` would have forced edits to `iosApp.xcodeproj/project.pbxproj`; adapting the planning documents was purely mechanical |
| Align plans 01-02…01-10 **before** executing them | The unexecuted plans named 47 non-existent Gradle tasks, 3 stale version literals and 12 paths owned by `:androidApp`. Fixing once is far cheaper than every executor rediscovering the same stale reference. |
| Generated `Res` package is `vehicare.shared.generated.resources` | Build-derived, confirmed by generating the accessors: all 49 `Res.string.*` and the 3 `Res.drawable.ic_vehicle_*` live there. Every later UI plan imports from that package — the `composeapp` name in RESEARCH was wrong. |
| Deferred brand accent stays the single `Ink` token (D-21) | `primary`/`secondary` = `Ink`, so one value swap re-tints all six reserved accent surfaces with no layout change when the user picks a real colour. |
| iOS identity lives in `project.pbxproj` | `PRODUCT_BUNDLE_IDENTIFIER = com.vehicare` and `FRAMEWORK_SEARCH_PATHS = $(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`; no `OTHER_LDFLAGS` needed (Swift autolinks `Shared`). Verified by a real `xcodebuild` run. |
| Never name `isSystemInDarkTheme`/`dynamic*ColorScheme` in comments | The light-only acceptance grep is literal and matches comments — `Theme.kt` tripped its own criterion via a doc comment before being reworded (UI-SPEC C-8). |
| iOS D-22 icon = one opaque 1024px PNG | Generated at the Android adaptive-icon proportions; Xcode derives every size, so no per-size rasters are committed. Keeps both platforms' placeholders visually consistent. |
| D-22 icon authored; `androidDeviceTest` wired | The icon was 01-01's only unmet acceptance criterion; the device-test wiring is a hard prerequisite for 01-05's WAL test |
| WAL mode from Day 1 | Prevents SQLite corruption from iOS SIGKILL (0x8badf0d) |
| Migration CI from Phase 1 | Any schema change without a `.sqm` crashes existing users on update |
| `Dispatchers.IO.limitedParallelism(1)` for DB writes | Prevents iOS `SIGABRT` from concurrent `NativeSqliteDriver` access |
| iOS mileage reminders: foreground-check | iOS cannot evaluate mileage in background; use `triggerImmediateIfOverdue()` |
| Notification permission gated to first reminder | A one-time iOS prompt denied on cold launch breaks reminders permanently |
| Two must-have literal wordings overridden by the developer (G-1 Compose single-version; G-2 WAL "declared in SQL") | Both protected intents independently re-verified against the codebase; recorded in `01-VERIFICATION.md` with `accepted_by` |
| WAL established at driver construction (Android row-safe pragma; iOS SQLiter `JournalMode.WAL` default) | The `.sq`-preamble mechanism crashes iOS driver creation; capability proven by real-driver `WalModeTest` on both platforms |
| Baseline-diff CI guard is the load-bearing migration control | The aggregate verify task passed vacuously on a probe column; `generateCommonMainVehiCareDatabaseSchema` + `git diff --exit-code` on the committed `1.db` caught the drift |

### Version Set (in force)

The Wave 0 gate answered **option-b**, then the user overrode four of its pins in favour of the set already shipping in the VehiCare project:

| Component | In force | Contract said |
|-----------|----------|---------------|
| Kotlin / KGP | **2.4.20** | 2.4.21 |
| Android Gradle Plugin | **9.1.1** | 9.3.1 |
| Compose Multiplatform | **1.12.1** | 1.12.2 |
| material3 | **1.12.0-alpha03** | 1.9.0 |

Unchanged from the contract: Gradle 9.5.1 · SQLDelight 2.4.1 · Koin 4.2.2 · Navigation 2.9.2 · Lifecycle 2.11.0 · coroutines 1.11.0 · serialization 1.11.0 · FileKit 0.16.0 · Coil 3.6.3 · activity-compose 1.13.0 · material-icons-core 1.7.3 (frozen — the last version ever published) · compileSdk/targetSdk 37 · minSdk **26** (raised from the wizard's 24) · iOS deployment target **16.0** (lowered from 18.2).

**Authoritative source of truth is `gradle/libs.versions.toml`**, plus the "Version set actually in use" table in `01-01-SUMMARY.md`. `01-VERSIONS-OPTION-B.md` is retained as research evidence only.

**Knock-on effects:**

- **Two modules:** `:shared` (KMP library via `com.android.kotlin.multiplatform.library`) + `:androidApp` (`com.android.application`). AGP 9 has no KMP-application plugin.
- Android sources, `AndroidManifest.xml` and launcher resources live in `androidApp/src/main/…`; library namespace is `com.vehicare.shared`, app namespace + applicationId are `com.vehicare`.
- Task paths: the APK is `:androidApp:assembleDebug`; `:shared` has **no** `assembleDebug` (it is a library). `:shared` owns `compileKotlinIosSimulatorArm64`, `compileAndroidMain`, `testAndroidHostTest`, `compileAndroidDeviceTest`, `connectedAndroidDeviceTest`, and both SQLDelight migration tasks.
- The `compose.*` Gradle accessors are deprecated in CMP 1.12.1 — dependencies use explicit catalog coordinates; `compose.materialIconsCore` no longer exists, and `material-icons-core` is pinned at 1.7.3 through its own alias.
- `compileSdk = libs.versions.android.compileSdk.get().toInt()` (plain int form) works — verified against the installed `android-37.0`; the block form is **not** required.
- `01-RESEARCH.md`, `.planning/research/*` and `01-VERSIONS-OPTION-B.md` carry deliberately stale version claims as dated evidence. **Re-read the catalog, not the research, before executing any plan.**
- Compose Resources live in `shared/src/commonMain/composeResources/**`; string/vector accessors are produced by `:shared:generateResourceAccessorsForCommonMain` (running only `generateComposeResClass` leaves the accessor file stale).

### Architecture Constraints

- UI composables never touch the DB directly
- Domain models never reference SQLDelight generated types (use mappers)
- Firebase and Android-only SDKs: `androidMain` only — never `commonMain`
- Mileage reminder logic in `commonMain`; platform delivery via `expect/actual`
- One rendering path only: `com.vehicare.App()` in `commonMain`; hosts (`MainActivity`, `MainViewController`) mount it and hold no UI of their own (D-17)
- Every user-facing string comes from `Res.string.*`; never inline a literal in a composable (D-11)
- Theme is light-only and applied unconditionally; no dark branch, no dynamic color (UI-SPEC C-8)

### Open Questions (from Research)

1. Mileage units (km vs miles) — global or per-vehicle? Affects the schema.
2. iOS mileage reminder UX — silent foreground check, or only on an explicit mileage log?
3. Reminder advance notice — global, or a per-reminder `lead_miles`/`lead_days` column?
4. `kotlinx-datetime` 0.7.x — confirm nothing expects the 0.6.x compat artifact.
5. ~~SQLDelight `generateAsync = true`~~ — **RESOLVED** at the Wave 0 gate: `generateAsync = false` for Phase 1.
6. Monetization model — one-time purchase or free?
7. Android notification delivery — WorkManager vs AlarmManager (`SCHEDULE_EXACT_ALARM`)?

### Todos

- [ ] Answer open questions 1–4, 6 and 7 when their phase needs them (Q1 km/miles and Q3 advance-notice shape the Phase 2 schema)
- [x] Wave 1 / plan 02-01 (service-log data core) → verified by both CI guards locally (`verify…Migration` + regenerate-then-diff clean), 8 real-driver iOS repository tests, and 11 mapper/vocabulary host tests (see `02-01-SUMMARY.md`)
- [ ] Optional (debt): re-verify Phase 1 to flip `01-VERIFICATION.md` → `passed`; fix `01-REVIEW.md` findings (CR-01, WR-01…03) before shipping
- [x] Set up CI before the first release commit (`generateCommonMainVehiCareDatabaseSchema` + `verifyCommonMainVehiCareDatabaseMigration`) → done in plan 01-05 (`.github/workflows/ci.yml`, probe-proven guard)
- [x] Bootstrap the project — done by the user in Android Studio at `~/Proyek Aplikasi/VehiCare`
- [x] Decide waves 2–8 → align first; alignment pass completed 2026-10-18
- [x] Resolve the `androidDeviceTest` source-set wiring → verified empirically (see `01-01-SUMMARY.md` D-7)
- [x] Author the D-22 placeholder launcher icon → adaptive-only, 0 rasters (see D-6)
- [x] Wave 2 / plan 01-02 (shared UI foundation) → verified by an Android APK build, a Kotlin/Native framework link and a real `xcodebuild` simulator build
- [x] Wave 2 / plan 01-03 (WAL schema, driver factories, serialized-IO repository) → verified by real-driver iOS tests, mapper tests on both targets, and a reproducible `1.db` baseline; the WAL mechanism was corrected after the `.sq` preamble proved to crash iOS driver creation (see `01-03-SUMMARY.md`)
- [x] Wave 3 / plan 01-05 (CI migration guard + WAL proofs) → verified by the guard-fire probe (diff guard exit 1, verify task vacuous) and `WalModeTest` green on the iOS driver and on an API 28 emulator (see `01-05-SUMMARY.md`)
- [x] Wave 4 / plan 01-06 (D-14 identity-card vehicle list) → verified by an Android APK build, a Kotlin/Native framework link and meta-line unit tests on both targets (see `01-06-SUMMARY.md`)
- [x] Wave 5 / plan 01-07 (real Add/Edit form) → verified by 12 rule cases in `VehicleFormValidationTest` on both targets, plus an Android APK build and a Kotlin/Native framework link (see `01-07-SUMMARY.md`)
- [x] Wave 6 / plan 01-08 (gallery-only vehicle photo) → verified by 7 real-filesystem `PhotoStorageTest` cases on the iOS simulator, plus an Android APK build, a Kotlin/Native framework link and both test tasks green (see `01-08-SUMMARY.md`)
- [x] Wave 7 / plan 01-09 (D-18 vehicle Detail screen) → verified by an Android APK build, a Kotlin/Native framework link, both test tasks green (21 Android-host + 34 iOS-simulator tests, incl. 7/7 `PhotoStorageTest`), exactly 5 `FieldRow(` call sites, and a zero-match sweep of the whole `ui/vehicles` tree for the banned icon/gesture/DB patterns (see `01-09-SUMMARY.md`)
- [x] Wave 8 / plan 01-10 (confirmed vehicle deletion) → verified by an Android APK build, a Kotlin/Native framework link, both test tasks green (21 Android-host + 42 iOS-simulator tests, incl. 8/8 `VehicleDeletionTest`), exactly 1 `delete(` call site in the Detail screen, and a zero-match sweep of commonMain for the recovery word, dark-theme identifiers, camera and long-press patterns (see `01-10-SUMMARY.md`)

### Blockers

No blockers. **Phase 2 closed 2026-10-21** — UAT 8/8 passed (`02-UAT.md`), security audit threat-secure (`02-SECURITY.md`, 50/50 threats closed, `threats_open: 0`), verification `status: passed` (52/52 must-haves). **Phase 1 closed 2026-10-19** — UAT 6/6 passed (`01-UAT.md`), security audit threat-secure (`01-SECURITY.md`, 72/72 threats closed, `threats_open: 0`). **Carried-forward debt (acknowledged at completion):** `01-VERIFICATION.md` remains `status: human_needed` — all six human checks passed via UAT; the stale field was acknowledged, not re-verified — gates requiring `status: passed` stay blocked until a re-verification (canonical fix; review via `/gsd-audit-uat`). Open code-review findings (`01-REVIEW.md`: CR-01, WR-01…03, IN-01…06) are tracked separately for the review-fix path.

---

## Session Continuity

**Last session:** 2026-10-21T16:38:22.993Z
**Stopped at:** Phase 3 context gathered
**Resume file:** .planning/phases/03-reminders-dashboard/03-CONTEXT.md
**Next action:** Plan Phase 3 (Reminders & Dashboard). It has no CONTEXT.md, so run `/gsd-discuss-phase 3` first. Stale `.planning/phases/02-service-log/.continue-here.md` still exists on disk and was intentionally not deleted without owner approval. Debt: `01-VERIFICATION.md` remains `human_needed` (acknowledged; `/gsd-audit-uat` or re-verify to clear); `01-REVIEW.md` CR-01/WR-01…03/IN-01…06 open; `02-REVIEW.md` WR-01…WR-04 open; `App.kt` KDoc typo uncommitted.

## Decisions

- [Phase 01]: WAL is established at driver construction, not in the .sq preamble — The plan's preamble mechanism crashes iOS driver creation: Schema.create() replays the pragma through driver.execute(), and SQLiter's non-query path rejects the row-returning PRAGMA journal_mode=wal. Android runs the same SQL via the row-safe executeQuery path; iOS relies on SQLiter's JournalMode.WAL default (verified in sources). Guards: plan 01-05 real-driver tests.
- [Phase 01]: Generated row type is com.vehicare.db.Vehicle, aliased VehicleEntity in the mapper — SQLDelight derives the class name from the table name; the plan's assumed VehicleEntity name does not exist. An import alias keeps the mapper and tests reading as planned.
- [Phase 01]: Koin platform module is a platformModule(context) factory registering single<Context> — Koin 4.2.2 has no androidContext()/androidLogger() module DSL (verified via javap on koin-android-4.2.2.aar); resolution is exact-type-only, so the Context must be registered explicitly.
- [Phase 01]: delete() funnels photo removal through a photoDeleter seam, no-op until 01-08 — PhotoStorage does not exist yet; the seam keeps the row+file deletion order (read photo_path in transaction, delete row, delete file after) testable and lets plan 01-08 swap in PhotoStorage::delete.
- [Phase ?]: App takes the Android-only platformModule as an optional Module parameter (default: empty module); MainActivity builds it from the real application Context
- [Phase ?]: koin-core added to androidApp dependencies because App(platformModule: Module) exposes org.koin.core.module.Module to the host (Rule 3 blocking fix)
- [Phase ?]: Android appVersionName reads the version via the Koin-registered Context (KoinPlatformTools.defaultContext().get()) — no hidden APIs, no buildConfig in :shared
- [Phase ?]: Screen composables are alias-imported as *Screen in App.kt so they coexist with same-named @Serializable route types
- [Phase 01]: Baseline-diff CI guard is the load-bearing DAT-04 control — Per-source-set verifyCommonMainVehiCareDatabaseMigration passed vacuously (exit 0) on a probe column with no .sqm; only generate...Schema && git diff --exit-code on the committed 1.db baseline detected drift (exit 1). CI keeps both guards.
- [Phase 01]: Temurin 21 for CI — The RESEARCH Environment row fixing the GitHub Actions runner is ubuntu-latest + Temurin 21 (Any CI with Java 21); it supports Gradle 9.5.1 / AGP 9.1.1 / Kotlin 2.4.20 even though the Wave 0 gate landed option-b
- [Phase 01]: Android WAL persistence assert uses a bare second driver — A second connection routed through DriverFactory re-runs the WAL pragma and can never fail, so the persistence claim uses a bare AndroidSqliteDriver that never executes the pragma (plan forbids weakening the Android test)
- [Phase 01]: labelRes()/iconRes() live in the UI layer — the domain enum stays plain Kotlin with no Compose Resources dependency
- [Phase 01]: Type glyph loads with vectorResource (not painterResource) to match the shipped BottomTabs and the verified UI-SPEC vector path; iconRes() interface unchanged
- [Phase 01]: Meta line is a pure internal vehicleMetaLine helper unit-tested in commonTest per the acceptance criteria
- [Phase 01]: LicensePlateChip announces 'License plate {plate}' so the merged card reads it as a labelled value
- [Phase 01]: FAB placed via Box overlay with explicit 16px/8px insets — the M3 Scaffold FAB slot cannot express the declared 8px bottom inset
- [Phase 01]: Coil PlatformFile support registered once in App() so a Detail-first composition also gets a FileKit-aware loader
- [Phase 01]: EmptyState centers its block (plan text) rather than reading the 64px top-offset literally
- [Phase 01]: Edit form consumes only the repository's first emission — Cheapest rule provably free of silent data loss on optional/photo fields when a reload races an in-flight edit (T-01-44).
- [Phase 01]: A read failure on load maps to NotFound, not a blank form — The UI-SPEC state contract declares three states; an unreadable row surfaces as the same not-available surface, with no exception detail logged (T-01-43).
- [Phase 01]: save() re-checks isSubmittable, not only the disabled button — The state machine owns the name gate so a programmatic call cannot insert a nameless row (D-09, defence in depth).
- [Phase 01]: Ready.saveFailed and VehicleTypePicker(enabled) added beyond the plan's interfaces block — The plan's own acceptance criteria require a save-failure Snackbar and inputs disabled while saving; both are defaulted so no call site breaks.
- [Phase 01]: VehicleTypePicker re-colours SingleChoiceSegmentedButtonRow from the app palette — M3's default selected container resolves to an out-of-palette lavender secondaryContainer (C-8); selection is also carried by icon and Role.RadioButton semantics, never colour alone.
- [Phase 01]: App.kt needed no edit for this plan — 01-06 already passes vehicleId and onDone to the edit entry; verified one KoinApplication, one NavHost and four composable<> entries.
- [Phase 01]: supersededPath holds only database-owned paths; a session-owned copy is deleted the moment it is replaced or removed — A single pending path cannot distinguish a file the saved row still references from a copy only this session had, and the two need opposite treatment on abandonment (T-01-49)
- [Phase 01]: onCleared() never deletes supersededPath — On an edit that path is the file the saved row still references; deleting it would destroy the user's only copy and leave the row dangling — only a never-saved session copy is removed
- [Phase 01]: PhotoStorage.persist runs before the supersede bookkeeping — Bookkeeping first would schedule the old file for deletion while a failed import leaves the new copy unwritten and photoPath still naming the old file
- [Phase 01]: persist compresses via FileKit compressImage (JPEG, 1280px cap, quality 80) with a verbatim-copy fallback — The plan required both compression and byte-equality for a synthetic array; compressImage throws on non-decodable bytes, so a non-image pick is stored rather than silently dropped
- [Phase 01]: delete is non-suspend via kotlinx-io SystemFileSystem.delete(..., mustExist = false) — FileKit's own delete is suspend and onCleared() has no scope; kotlinx-io is already on the classpath through filekit-core
- [Phase 01]: The plate row renders the chip when a plate exists and the em dash when it does not — The plan's literal trailing = { LicensePlateChip(vehicle.plate) } trips the chip's non-blank require() on every plate-less vehicle, which D-09/D-12 explicitly allow; the row must still appear (C-2)
- [Phase 01]: BottomTabs renders only on VehicleListRoute/SettingsRoute, scoped by hasRoute on the current destination — The UI-SPEC destination table lists the shared bar on the list route only; Detail and the Add/Edit form are pushed sub-screens with their own title bar and back arrow, and plan 01-04's tab-sync behaviour is untouched for the two roots
- [Phase 01]: Detail no-photo hero = Mist 4:3 block with a centred 64dp VehicleTypeCircle — Reuses a declared component instead of inventing an unlisted hero-placeholder size, and keeps D-14's type-circle language identical between list and detail
- [Phase 01]: Detail uiState streams via observeVehicle with WhileSubscribed(5s); a read failure maps to NotFound — An edit made on the form appears on return with no refresh callback, and a vanished row becomes the not-available surface by itself — so no race-prone existence guard lives in the screen (T-01-55); mirrors plan 01-07's no-fourth-state rule
- [Phase 01]: The destructive button is rendered but wired to a no-op delete stub owned by plan 01-10 — Lays out the Detail screen once instead of rewriting it twice; a half-wired destructive action is worse than an explicit stub, so plan 01-10 is the one place that turns it on with its confirmation and photo-file assertion (T-01-56)
- [Phase 01]: ConfirmDeleteDialog pins containerColor to Paper and the heading to titleLarge — M3's defaults would inject an off-palette surfaceContainerHigh tint and a Display-sized heading that outranks the vehicle name on the screen behind the dialog (C-8, D-18)
- [Phase 01]: Confirm is the only danger-tinted control; Cancel takes Ink on the leading side — The safe option must not read as destructive and the destructive one must not read as safe; both buttons are worded and at least 48dp, and every dismiss path is cancellation (T-01-61)
- [Phase 01]: onDeleted was added to VehicleDetailRoute(vehicleId, onEdit, onBack, onDeleted) — The plan's interfaces block omitted it, but its own key_links require the shell to distinguish a completed deletion from a plain back navigation (Rule 3 deviation, committed in 9d55729)
- [Phase 01]: The post-delete success Snackbar is shown on the shell's scope before navController.popBackStack() — The Detail screen is popped the instant the delete succeeds, so the message is hosted by App() and launched first — and the Flow-driven list already lacks the deleted card (T-01-62)
- [Phase 01]: delete() rethrows CancellationException and reports failure without optimism — A torn-down scope is not a failed deletion; a real failure keeps the vehicle visible and stored, and deleteFailed is retired one-shot after showSnackbar returns — no local removal ever needs a rollback (T-01-60)
- [Phase 01]: VehicleDeletionTest binds VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete) — The identical AppModule binding, so the real-filesystem assertion proves the shipped seam (D-07); ordering facts run on Dispatchers.setMain(StandardTestDispatcher) with CompletableDeferred gates, never sleeps
- [Phase 02]: Generated row types are Service_entry / Service_part / Service_photo; the mapper aliases them as *Entity — SQLDelight derives the class name from the table name (same derivation as Phase 1's VehicleEntity alias); the block-comment plan name ServiceEntry does not exist, so aliases keep the boundary intact
- [Phase 02]: observePreviousMileage reads the entry's stored date first, then binds the self-excluding query — Keeps (vehicleId, entryId) as the interface while leaving the id != / id < tie-break in SQL; no date parameter leaks to the UI
- [Phase 02]: photoDeleter stays a no-op until plan 02-03 wires PhotoStorage::delete — The plan assigns DI wiring to 02-03; delete ordering is already proven with an injected recording deleter in iosTest
- [Phase 02]: ServiceDates keeps today() on the system zone while storage/display pin UTC start-of-day — The user's wall-clock date and a zone-independent stored date are different requirements; one seam documents and tests the asymmetry (RESEARCH Pitfall 1), preventing cross-phase off-by-one imports.
- [Phase 02]: RupiahFormat routes both Rp and km formats through one private groupDigits helper with no locale API — D-29 requires identical dot grouping on Android and iOS; a single pure-Kotlin helper plus the 12-digit parse cap prevents platform drift and Long overflow (T-02-02-03).
- [Phase 02]: The full Phase 2 string block was appended in one change; detail_service_history_coming_soon retirement waits for plan 02-05 — Every later UI plan compiles against Res.string keys with no string edits; the old key must stay until ComingSoonSlot.kt is deleted in the same commit, or the build breaks for an unexecuted plan.
- [Phase 02]: PhotoStorage.persist now takes optional `directory` parameter (default: VEHICLES_DIRECTORY); existing vehicle call sites unchanged — plan 02-03
- [Phase 02]: ServiceEntryFormValidation.isSubmittable gates exactly on date + non-blank mileage + service type (D-27) — plan 02-03
- [Phase 02]: ServiceEntryEditViewModel implements seven photo rules as two path sets; onCleared only removes session-owned files — plan 02-03
- [Phase 02]: Custom type confirm collapses case-insensitively onto preset/learned chips; never rewrites stored values (D-40) — plan 02-03
- [Phase 02]: Vehicle form's name-field error line now uses MaterialTheme.typography.labelLarge (C-27) — plan 02-03
- [Phase 02]: ServiceEntryEditRoute registered with vehicleId + entryId (0L = add); AppModule binds single<ServiceEntryRepository> with PhotoStorage::delete — plan 02-03
- [Phase 2]: ConfirmDeleteDialog parameterized with title/message/confirmLabel (caller-resolved); vehicleName param removed; action_cancel stays internal; KDoc single-path warning now names both entities
- [Phase 2]: FieldRow moved to ui/components/ unchanged (body + KDoc); VehicleDetailScreen import updated; zero ui.vehicles.components.FieldRow refs remain (C-23)
- [Phase 2]: VehicleRepositoryImpl.delete cascade: select vehicle photo + service photo paths -> delete photos -> parts -> entries -> vehicle in one transaction -> forEach photoDeleter after; no RETURNING, serialized lane unchanged (D-39)
- [Phase 2]: ServiceEntryDetailViewModel mirrors VehicleDetailViewModel: observeEntry -> Loading/NotFound/Content, single-flight delete, one-shot deleteFailed, CancellationException rethrown first
- [Phase 2]: ServiceEntryDetailScreen locked anatomy per UI-SPEC: focal block (Display type + serviceMetaLine), lg break, field run (Cost divider, Parts omitted when empty, Workshop divider, Mechanic divider, stacked Notes no divider), Photos omitted when empty (read-only strip), 2xl break, destructive OutlinedButton
- [Phase 2]: serviceMetaLine internal helper (dateEpochMs, mileageKm -> '19 Oct 2026 · 123.456 km') - single source for entry detail and future history row (02-05 imports it); unit tested in ServiceMetaLineTest
- [Phase 2]: ServiceEntryDeletionTest proves: rows+files, isolation, idempotence, ViewModel ordering (success after repo returns), single-flight, failure retention, vehicle cascade D-39 (vehicle+entry photos all gone, survivor untouched)
- [Phase 02]: service_entry.service_type survives in schema v3 as a position-0 mirror of service_entry_type — minSdk 26 ships SQLite 3.18 without DROP COLUMN, and rebuilding the table via RENAME rewrites sqlite_master (quoted name) which fails the byte-identical baseline guard; the child table is the source of truth and toBindParameters writes the mirror from serviceTypes.first() only
- [Phase 02]: observeHistory hydrates service types with one vehicle-scoped selectTypesForVehicle query — A history page must cost a constant number of queries, not one per entry (T-02-07-07); entry rows and type rows are combined and grouped by entry_id, which preserves the query's (entry_id, position) order
- [Phase 02]: selectLearnedTypes reads the child table with GROUP BY lower(label) and a single MAX(entry_id) aggregate — One row per case-insensitive label with bare-column semantics well defined and no window functions; the old WHERE id IN (SELECT MAX(id)) shape would emit a label twice once one entry carries several
- [Phase 02]: 02-07 keeps the form and both display surfaces single-select; 02-08 owns the multi-select UI and serviceTypeLine — The plan ships the storage/repository half only, with two documented firstOrNull() compile bridges in detail and history, so no second display rule exists to delete later
- [Phase 02]: GAP 1 is fixed by one pure transition, not a call-site patch: confirmCustomServiceType returns both the next selection and the next learned chip list, and withConfirmedCustomType writes both in one copy — the picker's selected-label union is defensive only — The UAT bug survived 39/39 static must-haves because a selection outside presets+learned had no chip to render; one transition makes the two lists unable to drift, and the iosTest asserts both
- [Phase 02]: The form and both display surfaces are multi-select with serviceTypeLine as the single join: one type renders verbatim, several join with the locked middle dot, detail caps at 2 lines and the history row at 1 — D-26's one-of rule was superseded by the user's 2026-10-21 decision; keeping one helper and one separator literal prevents two display rules from diverging as more surfaces list types
