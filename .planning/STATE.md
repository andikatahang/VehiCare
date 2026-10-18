---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Ready to execute
last_updated: "2026-10-18T15:23:00.000Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 10
  completed_plans: 1
  percent: 10
---

# STATE: VehiCare

**Project:** VehiCare
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

---

## Current Position

Phase: 01 (foundation-vehicle-management) — EXECUTING
Plan: 2 of 10

| Field | Value |
|-------|-------|
| **Active Phase** | Phase 1: Foundation & Vehicle Management |
| **Active Plan** | 01-01 complete — 01-02 next (pending user decision on waves 2–8) |
| **Status** | Plan 01-01 verified green on both platforms |
| **Progress** | █░░░░░░░░░ 10% |

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
| Plans completed | 1 / 10 |
| Requirements covered | 0 / 21 |
| Session count | 2 |

Per-plan metrics:

| Plan | Duration | Tasks | Files |
|------|----------|-------|-------|
| Phase 1 / 01 | ~28min | 3 | 63 |

---

## Accumulated Context

### Key Decisions

| Decision | Rationale | Phase |
|----------|-----------|-------|
| SQLDelight 2.4.1 over Room KMP | Community consensus for KMP; 5+ years iOS parity; Room KMP has known iOS gaps | Phase 1 |
| Koin 4.2.2 for DI | KMP-native, no kapt, `koinViewModel()` works in commonMain | Phase 1 |
| JetBrains Navigation Compose 2.9.2 | Production-stable, type-safe routes, native iOS back gesture | Phase 1 |
| Wave 0 gate → **option-b** (current-generation stack) | The previously locked pins were internally inconsistent and could not build on this machine as installed. See `### Version Set` below. Supersedes every version in the three rows above. | Phase 1 |
| **Wizard version set adopted over the option-b pins** | User built the KMP project in Android Studio and directed that its existing Android + iOS configuration be used as the base. Every adopted version is now backed by an executed build — stronger evidence than the contract research. | Phase 1 |
| **Module stays `:shared` / framework `Shared`** | Renaming to `:composeApp` would have required editing `iosApp.xcodeproj/project.pbxproj`. Adapting the planning documents was purely mechanical and left the Xcode project untouched. | Phase 1 |
| WAL mode from Day 1 | Prevents SQLite corruption from iOS SIGKILL (0x8badf0d) | Phase 1 |
| Migration CI from Phase 1 | Any schema change without .sqm crashes existing users on update | Phase 1 |
| iOS mileage reminders: foreground-check | iOS cannot evaluate mileage in background; check on app foreground via `triggerImmediateIfOverdue()` | Phase 3 |
| Notification permission gated to first reminder | iOS one-time prompt denied on cold launch = reminders permanently broken | Phase 3 |
| `Dispatchers.IO.limitedParallelism(1)` for DB writes | Prevents iOS `SIGABRT` from concurrent NativeSqliteDriver access | Phase 1 |

### Version Set

The Wave 0 gate was answered **option-b — adopt the current-generation stack wholesale**. The user then **overrode four of its pins** in favour of the version set already shipping in the VehiCare project. The table below is the set **in force**; the "contract said" column records the superseded pin.

| Component | In force | Contract said |
|-----------|----------|---------------|
| Kotlin / KGP | **2.4.20** | 2.4.21 |
| Gradle (wrapper) | **9.5.1** | 9.5.1 |
| Android Gradle Plugin | **9.1.1** | 9.3.1 |
| Compose Multiplatform | **1.12.1** | 1.12.2 |
| material3 | **1.12.0-alpha03** | 1.9.0 |
| SQLDelight | 2.4.1 | 2.4.1 |
| Koin | 4.2.2 | 4.2.2 |
| JetBrains Navigation Compose | 2.9.2 | 2.9.2 |
| JetBrains Lifecycle ViewModel | 2.11.0 | 2.11.0 |
| kotlinx-coroutines | 1.11.0 | 1.11.0 |
| kotlinx-serialization | 1.11.0 | 1.11.0 |
| FileKit | 0.16.0 | 0.16.0 |
| Coil | 3.6.3 | 3.6.3 |
| androidx activity-compose | 1.13.0 | 1.13.0 |
| Compose material-icons-core | 1.7.3 (frozen — last version ever published) | 1.7.3 |
| Android SDK | compileSdk/targetSdk 37, minSdk **26** (raised from the wizard's 24) | same |
| iOS deployment target | **16.0** (lowered from the wizard's 18.2) | 16.0 |

**Authoritative source of truth is `gradle/libs.versions.toml`**, plus the "Version set actually in use" table in `01-01-SUMMARY.md`. `01-VERSIONS-OPTION-B.md` is retained as research evidence only; its pins are marked superseded.

**Knock-on effects — must be honoured before later plans execute:**

- **Module layout is two modules:** `:shared` (KMP library via `com.android.kotlin.multiplatform.library`) + `:androidApp` (`com.android.application`). AGP 9 has no KMP-application plugin, so the single-module premise in `SKELETON.md` is void.
- Android sources, `AndroidManifest.xml` and launcher resources live in `androidApp/src/main/…`. Library namespace is `com.vehicare.shared`; app namespace + applicationId are `com.vehicare`.
- Gradle task paths: `:androidApp:assembleDebug` for the APK. `:shared` has **no** `assembleDebug` (it is a library — use `:shared:assembleAndroidMain`). Unchanged on `:shared`: `:shared:compileKotlinIosSimulatorArm64` and both SQLDelight migration tasks.
- The `compose.*` Gradle accessors are deprecated in CMP 1.12.1 — dependencies use explicit version-catalog coordinates (`compose-runtime`, `compose-foundation`, `compose-ui`, `compose-material3`, `compose-components-resources`); `compose.materialIconsCore` no longer exists, and `material-icons-core` is pinned at 1.7.3 through its own catalog alias.
- `compileSdk = libs.versions.android.compileSdk.get().toInt()` (plain int form) is sufficient here — verified by a successful build against the installed `android-37.0`. The block form is **not** required.
- **All version literals in `01-RESEARCH.md`, `01-PATTERNS.md` and `01-01-PLAN.md` are stale** (`"36"` SDKs, `serialization = "1.7.3"`, old Kotlin/AGP/CMP numbers). Re-read the catalog before executing any later plan.

### Architecture Constraints

- UI composables: never touch DB directly
- Domain models: never reference SQLDelight generated types (use mappers)
- Firebase and Android-only SDKs: `androidMain` only — never `commonMain`
- Mileage reminder logic: `commonMain`; platform delivery: `expect/actual`

### Open Questions (from Research)

1. Mileage units (km vs miles) — global or per-vehicle setting? Affects DB schema — decide before Phase 1 schema is finalized
2. iOS mileage reminder UX — silent foreground check or only on explicit mileage log?
3. Reminder advance notice — global or per-reminder `lead_miles`/`lead_days` column?
4. `kotlinx-datetime` 0.7.x — confirm no dependency expects 0.6.x compat artifact
5. ~~SQLDelight `generateAsync = true` — verify compatibility before data layer build~~ — **RESOLVED** at the Wave 0 gate (2026-10-18): `generateAsync = false` for Phase 1.
6. Monetization model — one-time purchase or free?
7. Android notification delivery — WorkManager vs AlarmManager (SCHEDULE_EXACT_ALARM)?

### Todos

- [ ] Answer open questions before Phase 1 schema finalization
- [x] ~~Bootstrap project at kmp.jetbrains.com~~ — done by the user in Android Studio; project is `~/Proyek Aplikasi/VehiCare`
- [ ] Set up CI before first release commit (`generateCommonMainVehiCareDatabaseSchema` + `verifyCommonMainVehiCareDatabaseMigration`)
- [ ] **Decide how waves 2–8 proceed**: align the remaining plans to the adopted generation first, or execute them as-is and route around stale references
- [ ] Resolve the `androidDeviceTest` source-set wiring so plan 01-05's WAL instrumented test can run
- [ ] Author the D-22 placeholder launcher icon (currently the wizard's default Android robot + raster fallbacks)

### Blockers

None currently. Known 01-05 blocker: the AGP KMP library plugin creates the device-test source set dynamically, so `androidDeviceTest.dependencies { }` fails Kotlin DSL compilation (`Unresolved reference 'androidDeviceTest'`). Catalog entries are already prepared (`androidx-testExt-junit` 1.3.0, `androidx-testRunner` 1.7.0, `androidx-espresso-core` 3.7.0).

---

## Session Continuity

**Last session:** 2026-10-18T15:23:00.000Z
**Next action:** Present the waves 2–8 decision to the user; plan 01-01 is complete and verified, so the next executable plan is 01-02.

---
*State initialized: 2026-10-18*
*Last updated: 2026-10-18 after plan 01-01 execution*
