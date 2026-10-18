---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: Executing Phase 01
last_updated: "2026-10-18T14:15:29.206Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 10
  completed_plans: 0
  percent: 0
---

# STATE: CarRecord

**Project:** CarRecord
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

---

## Current Position

Phase: 01 (foundation-vehicle-management) — EXECUTING
Plan: 1 of 10
| Field | Value |
|-------|-------|
| **Active Phase** | Phase 1: Foundation & Vehicle Management |
| **Active Plan** | None (not yet planned) |
| **Status** | Ready to plan |
| **Progress** | ░░░░░░░░░░ 0% |

```
Phase 1 [Not Started] → Phase 2 [Not Started] → Phase 3 [Not Started]
   ↑
   Current
```

---

## Phase Summary

| Phase | Name | Status |
|-------|------|--------|
| 1 | Foundation & Vehicle Management | Not started |
| 2 | Service Log | Not started |
| 3 | Reminders & Dashboard | Not started |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases completed | 0 / 3 |
| Plans completed | 0 / ? |
| Requirements covered | 0 / 21 |
| Session count | 1 |

---

## Accumulated Context

### Key Decisions

| Decision | Rationale | Phase |
|----------|-----------|-------|
| SQLDelight 2.4.1 over Room KMP | Community consensus for KMP; 5+ years iOS parity; Room KMP has known iOS gaps | Phase 1 |
| Koin 4.2.2 for DI | KMP-native, no kapt, `koinViewModel()` works in commonMain | Phase 1 |
| JetBrains Navigation Compose 2.9.2 | Production-stable, type-safe routes, native iOS back gesture | Phase 1 |
| Wave 0 gate → **option-b** (current-generation stack) | The previously locked pins were internally inconsistent and could not build on this machine as installed. See `### Version Set` below. Supersedes every version in the three rows above. | Phase 1 |
| WAL mode from Day 1 | Prevents SQLite corruption from iOS SIGKILL (0x8badf00d) | Phase 1 |
| Migration CI from Phase 1 | Any schema change without .sqm crashes existing users on update | Phase 1 |
| iOS mileage reminders: foreground-check | iOS cannot evaluate mileage in background; check on app foreground via `triggerImmediateIfOverdue()` | Phase 3 |
| Notification permission gated to first reminder | iOS one-time prompt denied on cold launch = reminders permanently broken | Phase 3 |
| `Dispatchers.IO.limitedParallelism(1)` for DB writes | Prevents iOS `SIGABRT` from concurrent NativeSqliteDriver access | Phase 1 |

### Version Set (resolved at the Wave 0 gate, 2026-10-18)

The user selected **option-b — adopt the current-generation stack wholesale** at plan 01-01's Wave 0 gate (Task 1). This supersedes every earlier pin, which was internally inconsistent: SQLDelight 2.3.x declares `kotlin-stdlib 2.3.10`, and Koin 4.2.0 declares `org.jetbrains.compose.runtime:runtime:1.10.2`, which Gradle "highest wins" promotes onto a classpath otherwise pinned to CMP 1.8.2 — a half-upgraded Compose stack that compiles but fails at runtime. Independently, the installed toolchain (JDK 25/26, Xcode 27.0) cannot run that generation.

| Component | Version |
|-----------|---------|
| Kotlin / KGP | 2.4.21 |
| Gradle (wrapper) | 9.5.1 |
| Android Gradle Plugin | 9.3.1 |
| Compose Multiplatform | 1.12.2 |
| SQLDelight | 2.4.1 |
| Koin | 4.2.2 |
| JetBrains Navigation Compose | 2.9.2 |
| JetBrains Lifecycle ViewModel | 2.11.0 |
| kotlinx-coroutines | 1.11.0 |
| kotlinx-serialization | 1.11.0 |
| FileKit | 0.16.0 |
| Coil | 3.6.3 |
| androidx activity-compose | 1.13.0 |
| Compose material-icons-core | 1.7.3 (frozen — last version ever published) |
| Android SDK | compileSdk/targetSdk 37, minSdk 26 |
| iOS deployment target | 16.0 |

Full contract with per-pin evidence and the copy-pasteable build files: `.planning/phases/01-foundation-vehicle-management/01-VERSIONS-OPTION-B.md`.

**Knock-on effects — must be honoured before later plans execute:**

- **Module layout is now two modules:** `:composeApp` (KMP library via `com.android.kotlin.multiplatform.library`) + `:androidApp` (`com.android.application`). AGP 9 has no KMP-application plugin, so the single-module `:composeApp` premise in `SKELETON.md` is void.
- Android sources, `AndroidManifest.xml` and launcher resources move to `androidApp/src/main/…`. Library namespace is `com.carrecord.shared`; app namespace + applicationId stay `com.carrecord`.
- Gradle task paths move: `:composeApp:assembleDebug` → `:androidApp:assembleDebug`. Unchanged: `:composeApp:compileKotlinIosSimulatorArm64` and both SQLDelight tasks (still on `:composeApp`).
- The `compose.*` Gradle accessors are deprecated in CMP 1.12.2 — dependencies use explicit version-catalog coordinates (`compose-runtime`, `compose-foundation`, `compose-ui`, `compose-material3`, `compose-components-resources`); `compose.materialIconsCore` no longer exists.
- `compileSdk` uses the block form `compileSdk { version = release(37) { minorApiLevel = 0 } }` because only `android-37.0` is installed and the KMP `androidLibrary` extension has no `compileSdkMinor` property.

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
- [ ] Bootstrap project at kmp.jetbrains.com
- [ ] Set up CI before first release commit (`generateCommonMainCarRecordDatabaseSchema` + `verifyCommonMainCarRecordDatabaseMigration`)

### Blockers

None.

---

## Session Continuity

**Last session:** 2026-10-18T11:51:57.832Z
**Next action:** Run `/gsd-plan-phase 1` to create PLAN.md for Phase 1

---
*State initialized: 2026-10-18*
*Last updated: 2026-10-18 after roadmap creation*
