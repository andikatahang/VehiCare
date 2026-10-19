---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Ready to execute
last_updated: "2026-10-19T06:33:28.959Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 10
  completed_plans: 7
  percent: 0
---

# STATE: VehiCare

**Project:** VehiCare
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

---

## Current Position

Phase: 01 (foundation-vehicle-management) — EXECUTING
Plan: 8 of 10

| Field | Value |
|-------|-------|
| **Active Phase** | Phase 1: Foundation & Vehicle Management |
| **Active Plan** | 01-08 (01-01–01-07 complete and verified) |
| **Status** | Wave 5 done — plan 01-07 (real Add/Edit form: pure rules, Loading/Ready/NotFound state machine, type picker, five-field screen) executed; next executable plan is 01-08 (wave 6, the photo block on this same screen) |
| **Progress** | ███████░░░ 70% |

```
Phase 1 [In Progress] → Phase 2 [Not Started] → Phase 3 [Not Started]
   ↑
   Current
```

---

## Phase Summary

| Phase | Name | Status |
|-------|------|--------|
| 1 | Foundation & Vehicle Management | In progress |
| 2 | Service Log | Not started |
| 3 | Reminders & Dashboard | Not started |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases completed | 0 / 3 |
| Plans completed | 7 / 10 |
| Requirements covered | 7 / 21 |
| Sessions | 7 |

Per-plan: `Phase 1 / 01` — ~28min, 3 tasks, 63 files · `Phase 1 / 02` — 17min, 3 tasks, 15 files · `Phase 1 / 03` — 20min, 3 tasks, 17 files · `Phase 1 / 04` — 24min, 3 tasks, 17 files · `Phase 1 / 05` — 11min, 3 tasks, 3 files · `Phase 1 / 06` — 10min, 3 tasks, 8 files · `Phase 1 / 07` — 21min, 3 tasks, 6 files.

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

- [ ] Answer open questions 1–4, 6 and 7 before the Phase 1 schema is finalized
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

### Blockers

None. Plans 01-01–01-07 are complete and verified by executed builds and tests: 01-07 shipped the real five-field Add/Edit form (pure `VehicleFormValidation`, the `Loading`/`Ready`/`NotFound` state machine with a double-submit guard and a one-shot save-failure flag, the Car · Motorcycle · Truck picker, and the final form screen) with `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64` and both test tasks green, 12/12 rule cases passing (see `01-07-SUMMARY.md`). The next executable plan is **01-08**. Runtime launch on a device/emulator remains for `/gsd-verify-work` (`workflow.human_verify_mode` = end-of-phase).

---

## Session Continuity

**Last session:** 2026-10-19T06:33:28.956Z
**Next action:** Execute plan **01-08** (wave 6) — extend the Add/Edit screen with the photo block (pick/capture, preview, replace/remove) wired through `VehicleFormValues.photoPath`, and complete `PhotoStorage`. Depends on 01-07, which is complete and left the form as the single Add/Edit screen it will extend.

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
