---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Executing Phase 01
last_updated: "2026-10-18T16:18:00.000Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 10
  completed_plans: 2
  percent: 20
---

# STATE: VehiCare

**Project:** VehiCare
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

---

## Current Position

Phase: 01 (foundation-vehicle-management) — EXECUTING
Plan: 3 of 10

| Field | Value |
|-------|-------|
| **Active Phase** | Phase 1: Foundation & Vehicle Management |
| **Active Plan** | 01-03 (01-01 and 01-02 complete and verified) |
| **Status** | Wave 2 in progress; plans 03–10 aligned to the adopted generation |
| **Progress** | ██░░░░░░░░ 20% |

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
| Plans completed | 2 / 10 |
| Requirements covered | 1 / 21 |
| Sessions | 4 |

Per-plan: `Phase 1 / 01` — ~28min, 3 tasks, 63 files · `Phase 1 / 02` — 17min, 3 tasks, 15 files.

---

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
- [ ] Set up CI before the first release commit (`generateCommonMainVehiCareDatabaseSchema` + `verifyCommonMainVehiCareDatabaseMigration`)
- [x] Bootstrap the project — done by the user in Android Studio at `~/Proyek Aplikasi/VehiCare`
- [x] Decide waves 2–8 → align first; alignment pass completed 2026-10-18
- [x] Resolve the `androidDeviceTest` source-set wiring → verified empirically (see `01-01-SUMMARY.md` D-7)
- [x] Author the D-22 placeholder launcher icon → adaptive-only, 0 rasters (see D-6)
- [x] Wave 2 / plan 01-02 (shared UI foundation) → verified by an Android APK build, a Kotlin/Native framework link and a real `xcodebuild` simulator build

### Blockers

None. Wave 1 (plan 01-01) and Wave 2's plan 01-02 are complete and verified by executed builds on both platforms; the next executable plan is **01-03**. Runtime launch on a device/emulator remains for `/gsd-verify-work` (`workflow.human_verify_mode` = end-of-phase).

---

## Session Continuity

**Last session:** 2026-10-18T16:15:50.323Z
**Next action:** Execute plan **01-03** — WAL `.sq` schema, committed `1.db` baseline, domain models, driver factories, DI module, repository + mapper with tests. No pre-work remains.
