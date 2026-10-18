---
phase: 01-foundation-vehicle-management
plan: 01
subsystem: build-foundation
tags: [gradle, kotlin-multiplatform, agp, sqlalchemy, sqldelight, compose-multiplatform, ios, version-catalog]
provides:
  - A single Gradle build (Gradle 9.5.1 wrapper) with two modules — `:shared` (KMP library, 3 targets) and `:androidApp` (Android application host)
  - `gradle/libs.versions.toml` as the sole version source of truth, carrying all aliases that plans 02–10 reference
  - SQLDelight `VehiCareDatabase` config wired with `schemaOutputDirectory` so the migration-guard task pair exists
  - Android manifest with `allowBackup="false"`, one LAUNCHER activity, zero permissions
  - An iOS Xcode host project (`iosApp/`) with deployment target 16.0
affects: [01-02, 01-03, 01-04, 01-05, 01-06, 01-07, 01-08, 01-09, 01-10]
tech-stack:
  added: [Gradle 9.5.1, Kotlin 2.4.20, AGP 9.1.1, Compose Multiplatform 1.12.1, SQLDelight 2.4.1, Koin 4.2.2, Navigation 2.9.2, Lifecycle 2.11.0, coroutines 1.11.0, serialization 1.11.0, FileKit 0.16.0, Coil 3.6.3, material-icons-core 1.7.3]
  patterns: [version-catalog-as-single-source-of-truth, two-module KMP split (library + app host), schemaOutputDirectory migration guard, generateAsync=false, configuration-cache enabled]
key-files:
  created:
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
  modified:
    - build.gradle.kts
    - gradle/libs.versions.toml
    - shared/build.gradle.kts
    - androidApp/src/main/AndroidManifest.xml
    - androidApp/build.gradle.kts
    - iosApp/iosApp.xcodeproj/project.pbxproj
    - .planning/** (identifier rename CarRecord→VehiCare)
key-decisions:
  - "Wave 0 gate resolved as option-b (adopt current-generation stack)"
  - "New dependencies approved: FileKit, Coil, material-icons-core"
  - "allowBackup = false"
  - "SQLDelight generateAsync = false"
  - "Then superseded by explicit user direction: the project was relocated to VehiCare and the wizard-supplied version set was kept instead of the contract's pins"
  - "Module stays `:shared` (framework `Shared`), NOT `:composeApp` (framework `ComposeApp`) — planning documents were adapted, Xcode project untouched"
duration: ~28min wall-clock (21:49 → 22:17 +0700), including the blocking user gate
completed: 2026-10-18
---

# Phase 1: Foundation & Vehicle Management — Plan 01 Summary

**Wave 0 gate settled as `option-b`, then the whole foundation was re-based onto the user-supplied VehiCare Kotlin Multiplatform project and proven to build — Android APK produced and iOS simulator target compiled.**

## Performance
- **Duration:** ~28min wall-clock (includes the blocking Task 1 gate wait)
- **Tasks:** 3 completed
- **Files modified:** 36 untracked new project files + 27 modified planning documents

## Resolved at the Wave 0 gate (`option-b`)

The gate asked which version generation to adopt. **The user selected `option-b`** — adopt the current-generation stack wholesale rather than aligning the build down to the STATE-locked legacy pins.

Three secondary confirmations, all accepted at the recommended defaults:

| Confirmation | Answer |
|---|---|
| New dependencies (FileKit, Coil, `material-icons-core:1.7.3`) | **Approved** |
| `android:allowBackup` | **`false`** |
| SQLDelight `generateAsync` | **`false`** |

Per the plan's option-b instruction, the version records in `STATE.md` and `ROADMAP.md` were refreshed for the adopted generation (commit `a1fd40b`) before any build file was authored, and `.planning/phases/01-foundation-vehicle-management/01-VERSIONS-OPTION-B.md` was produced as the researched version contract.

## Then: an approved re-base (Rule 4 + explicit user direction)

This is the part that diverges from the written plan, and it was **explicitly approved by the user** before any file was touched.

The user built the Kotlin Multiplatform project themselves in Android Studio and directed that the project now lives at `~/Proyek Aplikasi/VehiCare`, named **VehiCare**, and that its **existing Android + iOS configuration be used as the base**. Two decisions were taken by explicit question:

1. **Version set:** keep the wizard-supplied versions (Kotlin 2.4.20 / AGP 9.1.1 / CMP 1.12.1 / material3 1.12.0-alpha03) rather than re-pinning to the `option-b` contract values (2.4.21 / 9.3.1 / 1.12.2 / material3 1.9.0).
2. **Module name:** keep `:shared` and framework `Shared`; adapt the planning documents instead of renaming the module (which would have required editing `iosApp/iosApp.xcodeproj/project.pbxproj`).
3. **Full rename approved:** `CarRecord` → `VehiCare`, `com.carrecord` → `com.vehicare`, `:composeApp` → `:shared`, framework `ComposeApp` → `Shared`, `CarRecordDatabase` → `VehiCareDatabase`, `com.carrecord.db` → `com.vehicare.db`.

The repo (`.git` + `.planning/`) was moved from `~/Proyek Aplikasi/VibeCoding#1` into `~/Proyek Aplikasi/VehiCare` so the project is self-contained. **13 commits, HEAD `a1fd40b`, 27 tracked files, `git fsck` clean** after the move.

### Version set actually in use (supersedes `01-VERSIONS-OPTION-B.md` §version pins)

| Component | Contract (`option-b`) | **In use (adopted)** | Verified by |
|---|---|---|---|
| Gradle wrapper | 9.5.1 | **9.5.1** | `./gradlew --version` |
| Kotlin | 2.4.21 | **2.4.20** | build + iOS compile |
| AGP | 9.3.1 | **9.1.1** | `:androidApp:assembleDebug` |
| Compose Multiplatform | 1.12.2 | **1.12.1** | build |
| material3 | 1.9.0 (stable) | **1.12.0-alpha03** | build |
| SQLDelight | 2.4.1 | **2.4.1** | task list |
| Koin / Navigation / Lifecycle | 4.2.2 / 2.9.2 / 2.11.0 | **4.2.2 / 2.9.2 / 2.11.0** | build |
| coroutines / serialization | 1.11.0 / 1.11.0 | **1.11.0 / 1.11.0** | build |
| FileKit / Coil | 0.16.0 / 3.6.3 | **0.16.0 / 3.6.3** | build |
| material-icons-core | 1.7.3 | **1.7.3** | build |
| minSdk | 26 | **26** | raised from the wizard's 24 |
| compileSdk / targetSdk | 37 | **37** | unchanged |
| iOS deployment target | 16.0 | **16.0** | lowered from the wizard's 18.2 |

Every row in the "in use" column is now backed by an executed build, which is stronger evidence than the contract's research-based claims. The contract file is retained as research evidence but **its version pins are no longer in force**.

## Accomplishments
- **Both SQLDelight migration-guard tasks are registered** — `generateCommonMainVehiCareDatabaseSchema` and `verifyCommonMainVehiCareDatabaseMigration`. This is DAT-04's headline guard: without `schemaOutputDirectory` Gradle registers no migration task at all and the requirement is silently unenforced (RESEARCH Pitfall 1).
- **`./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL**, `androidApp-debug.apk` (14 MB). All phase-1 dependency families resolve and compile: SQLDelight, Koin, Navigation, FileKit, Coil, material-icons-core.
- **`./gradlew :shared:compileKotlinIosSimulatorArm64` → BUILD SUCCESSFUL** with klib output at `shared/build/classes/kotlin/iosSimulatorArm64/main/klib/`. This **retires the phase's biggest open risk**: Xcode 27.0 (Build 27A266a) works with this Kotlin/Native generation — the JetBrains support table only covered Xcode ≤ 26, so this was unverified until now.
- **Package renamed** `com.example.vehicare` → `com.vehicare` across 24 source/build files and 7 source-tree directories, with zero leftover references.
- **Planning documents renamed** across all 27 files: 1,726 old identifier tokens (`carrecord`/`CarRecord`/`composeApp`/`ComposeApp`) reduced to **0**.
- Security posture confirmed: manifest declares **0 permissions**, no camera, no storage-read; `allowBackup="false"`; `gradle.properties` carries no credentials; `.gitignore` covers `local.properties`, `.gradle/`, `.kotlin/`, `.idea/`.
- Wrapper 9.5.1 verified (`distributionSha256Sum` present) with `org.gradle.configuration-cache=true`.

## Task Commits
1. **Task 1: Resolve the version-set and environment fork (gate)** — resolved by user answer, recorded in `a1fd40b` (version records) and in this SUMMARY
2. **Task 2: Author the Gradle root config** — project supplied by the user; aligned in the plan-01 commit
3. **Task 3: Author the shared KMP module build config** — project supplied by the user; aligned in the plan-01 commit

## Verification results

| # | Check (plan's `verify` intent) | Result |
|---|---|---|
| 1 | `./gradlew help` exits 0 | **PASS** |
| 2 | `:shared:tasks --all` lists both migration tasks | **PASS** (verified twice) |
| 3 | `grep -c 'Migration'` in task list | **PASS** (2) |
| 4 | `:androidApp:assembleDebug` succeeds | **PASS** (APK 14 MB) |
| 5 | `:shared:compileKotlinIosSimulatorArm64` succeeds | **PASS** |
| 6 | `LOCAL_PROPS_OK` (`sdk.dir=`, gitignored) | **PASS** |
| 7 | `.gitignore` covers `local.properties`/`.gradle`/`.kotlin`/`.idea` | **PASS** |
| 8 | `gradle.properties` has no credentials + has `android.useAndroidX=true` | **PASS** |
| 9 | Manifest: `allowBackup="false"`, 1 LAUNCHER activity, 0 permissions | **PASS** |
| 10 | `settings.gradle.kts` has `rootProject.name = "VehiCare"` + `include(":shared")` | **PASS** |
| 11 | Root `build.gradle.kts` declares all 7 plugins `apply false` | **PASS** |
| 12 | `shared/build.gradle.kts` has `generateAsync.set(false)`, `iosArm64()`, `iosSimulatorArm64()` | **PASS** |
| 13 | `material-icons-core` at 1.7.3, no `materialIconsCore`, no `material-icons-extended` | **PASS** (via catalog alias — see deviation D-3) |
| 14 | Single Compose stack | **PASS by intent — see deviation D-2** |
| 15 | `minSdk = 26` literal in `shared/build.gradle.kts` | **PASS by intent — see deviation D-4** |
| 16 | `shared/src/androidMain/AndroidManifest.xml` + D-22 launcher icon | **NOT MET — see deviation D-5/D-6** |

## Decisions & Deviations

**D-1 — Two-module split instead of one module (Rule 4, user-approved).** The plan assumed a single `:composeApp` module that was both the KMP library and the Android application host. The user's project splits `:shared` (KMP library via `com.android.kotlin.multiplatform.library`) from `:androidApp` (`com.android.application`). Consequences: the plan's `:composeApp:assembleDebug` becomes `:androidApp:assembleDebug` (executed — passes); the manifest and the app-level Android DSL (`applicationId`, `targetSdk`, `versionCode`) live in `:androidApp`; `:shared` keeps only `namespace`/`compileSdk`/`minSdk`. The plan's Task 2 note "pick one and stay consistent" anticipated this choice.

**D-2 — The "exactly ONE distinct `org.jetbrains.compose.*` version" criterion is unsatisfiable as literally written.** Running the plan's literal command on `:shared:dependencies --configuration commonMainImplementation` returns three values: `1.12.1`, `1.12.0`, `1.7.3`. This is a **pre-existing plan defect, not a regression** — the same criterion also appears in the plan's own acceptance criteria alongside a *mandated* `material-icons-core:1.7.3` pin, so it could never have passed under the contract either (`1.8.2` CMP + `1.9.0` material3 + `1.7.3` icons = three values). The criterion's *intent* — RESEARCH Pitfall 2's half-upgraded-Compose-stack failure — **is** satisfied: every CMP-versioned artifact resolves to a single version:

```
org.jetbrains.compose.components:components-resources:1.12.1
org.jetbrains.compose.foundation:foundation:1.12.1
org.jetbrains.compose.runtime:runtime:1.12.1
org.jetbrains.compose.ui:ui:1.12.1
org.jetbrains.compose.ui:ui-tooling-preview:1.12.1
org.jetbrains.compose.material3:material3:1.12.0-alpha03   <- independently-versioned track (wizard's choice)
org.jetbrains.compose.material:material-icons-core:1.7.3  <- deliberately frozen legacy artifact
```
`material3`'s transitive requests for `androidx.compose.*:1.12.0-alpha03` are upgraded by Gradle conflict resolution to the `1.12.1` line, so the **resolved Android classpath is uniform at the CMP version**; `:androidApp:checkDebugDuplicateClasses` also passed. **Recommend correcting this criterion in the plan** so it reads "all CMP-versioned artifacts resolve to one version".

**D-3 — `material-icons-core` is pinned through the catalog, not as a literal coordinate.** The plan required a literal coordinate in `shared/build.gradle.kts` because the deprecated `compose.materialIconsCore` accessor resolves against the CMP version and does not resolve at 1.8.2. Using a dedicated catalog alias with its own `material-icons = "1.7.3"` version ref achieves exactly the same decoupling **and** honours the plan's own T-01-01 mitigation ("pin every version through the single catalog"). `shared/build.gradle.kts` contains no `materialIconsCore` and no `material-icons-extended`.

**D-4 — `minSdk` is 26 via the catalog, not a literal.** `shared/build.gradle.kts` reads `minSdk = libs.versions.android.minSdk.get().toInt()` and `gradle/libs.versions.toml` carries `android-minSdk = "26"`. A literal grep for `minSdk = 26` in `shared/build.gradle.kts` will not match; the value is enforced and was **raised from the wizard's 24** as part of this plan.

**D-5 — The manifest requested at `shared/src/androidMain/AndroidManifest.xml` lives at `androidApp/src/main/AndroidManifest.xml` instead.** Under the approved two-module split `:shared` is a library and needs no manifest; the application manifest, `MainActivity`, and launcher icons belong to the app host. All of the requirement's *substance* was verified against the real manifest: `allowBackup="false"`, exactly one activity with the `LAUNCHER` intent filter, and **zero** `android.permission` declarations (no CAMERA, no READ_EXTERNAL_STORAGE, no READ_MEDIA_IMAGES).

**D-6 — D-22's placeholder launcher icon was NOT re-authored (the one unmet acceptance criterion).** The plan specifies three hand-authored files: an `<adaptive-icon>` at `shared/.../mipmap-anydpi-v26/ic_launcher.xml`, a single-colour foreground vector using the `Ink` value `#FF1C1C1E`, and a **`<color>` resource** at `values/ic_launcher_background.xml` — explicitly "no `mipmap-*` raster variant and no `.webp`". What actually exists (from the wizard) is: `mipmap-anydpi-v26/ic_launcher.xml` + `ic_launcher_round.xml` (both valid `<adaptive-icon>`), `drawable/ic_launcher_background.xml` as a **vector drawable**, `drawable-v24/ic_launcher_foreground.xml` (the default Android robot glyph, not the ink-on-white mark), **plus raster fallbacks** `mipmap-{mdpi,hdpi,xxxhdpi}/ic_launcher*.png`. So the icon *resolves* and the app builds, but the D-21/D-22 artwork spec is not implemented. **Not changed unilaterally** because the user directed that VehiCare's existing Android configuration be used as the base. Cheapest fix: author the three specified files and delete the raster PNGs.

**D-7 — `androidInstrumentedTest` dependencies were not added.** The plan's Task 3 lists `androidx.test.ext:junit` + `androidx.test:runner` on an `androidInstrumentedTest` source set. The AGP KMP library plugin creates the device-test source set dynamically via `withDeviceTestBuilder`, so there is **no Kotlin DSL accessor** for it — `androidDeviceTest.dependencies { }` fails script compilation with `Unresolved reference 'androidDeviceTest'`. This was removed to keep the build green. **This is a blocker to resolve in plan 01-05** (it owns the WAL instrumented test). The catalog is already prepared: `androidx-testExt-junit` 1.3.0, `androidx-testRunner` **1.7.0** (verified present on Google Maven), `androidx-espresso-core` 3.7.0.

**D-8 — `koin-android` was not added.** Confirmed available at 4.2.2 but unused by this plan. Flagged for plan 01-06 (DI wiring).

**D-9 — Wizard sample sources are still present.** `shared/src/commonMain/kotlin/com/vehicare/{App.kt,Greeting.kt,GreetingUtil.kt,Platform.kt}`, `Platform.android.kt`, `Platform.ios.kt`, `MainViewController.kt`, `androidApp/.../MainActivity.kt`, and three sample test files. The plan said "Do NOT create any Kotlin source files in this task" — these were created by the wizard, not by this plan, and they are what makes the iOS/Android compile proofs meaningful. They are not referenced by any plan and will be superseded by real code in 01-02/01-04. **Not deleted** — deleting files needs explicit approval.

## Required before executing the remaining plans (option-b carry-forward)

Per the plan's own option-b instruction, this must be re-verified against the **adopted** generation before plans 02–10 run:

- **Every RESEARCH version reference is stale.** `01-RESEARCH.md` and `01-PATTERNS.md` still show `android-compileSdk = "36"`, `android-targetSdk = "36"`, `serialization = "1.7.3"`, and the old Kotlin/AGP/CMP numbers. `01-01-PLAN.md` line 193 still instructs `serialization = "1.7.3"` and `"36"` SDK values. **The authoritative source of truth is now `gradle/libs.versions.toml` plus the table in this SUMMARY.**
- **The module DSL form differs.** The plans reference `:composeApp` in ~166 places, now renamed to `:shared`, but the *build shape* they assume (one module that is both library and app host) is wrong: commands must target `:shared` for module tasks and `:androidApp` for app/APK tasks.
- **Plan 01-05's task names in particular are generation-specific.** Both names were re-verified against the adopted SQLDelight 2.4.1 and are correct as renamed: `generateCommonMainVehiCareDatabaseSchema` / `verifyCommonMainVehiCareDatabaseMigration`.
- **Open item D-7** (instrumented-test source set) blocks 01-05 and needs a real solution, not a guess.

## Next Phase Readiness
- Build foundation is green and proven on both platforms — plans 02–10 have a working base.
- All version-catalog aliases the later plans reference exist and resolve.
- The migration-guard task pair is armed *before* any `.sq` file exists, which is exactly the ordering DAT-04 requires.
- **Blocking decision pending with the user:** whether to align the remaining plans (waves 2–8) to the adopted generation before executing them, and whether to author the D-22 placeholder icon.
