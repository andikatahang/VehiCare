# Phase 1: Foundation & Vehicle Management - Research

**Researched:** 2026-10-18
**Domain:** Kotlin Multiplatform (Android + iOS) — Compose Multiplatform shared UI, SQLDelight local persistence, Koin DI, type-safe navigation, native photo picker
**Confidence:** HIGH on stack mechanics / MEDIUM on the *pinned* stack's viability on this machine (see Environment Availability — this is the single biggest planning risk in this phase)

## Summary

Phase 1 is a greenfield Walking Skeleton: it must (a) stand up a KMP + Compose Multiplatform project that actually builds and runs on Android API 26+ and iOS 16+ from one shared codebase, (b) lay a migration-safe SQLDelight schema with WAL from first launch, and (c) ship CRUD vehicle management with an optional gallery photo and a bottom-tab shell. All of the *mechanics* are well documented and verified in this research: the SQLDelight migration-verification workflow, the WAL story, the FileKit gallery-picker API, the Koin `koinViewModel()` wiring, and the type-safe NavHost pattern all have concrete, version-accurate answers below.

The critical finding is **not** in the mechanics — it is that the **locked version set is internally inconsistent on real registry metadata and cannot run on this machine as installed.** Three independent problems:

1. **Registry-metadata misalignment.** SQLDelight 2.3.2 declares `kotlin-stdlib 2.3.10` and Koin 4.2.0 declares `kotlin-stdlib 2.3.20` + `org.jetbrains.compose.runtime:runtime:1.10.2`, while the locked Kotlin is 2.2.20 and locked CMP is 1.8.2. Putting Koin 4.2.0 on the classpath silently force-upgrades compose-runtime/foundation from 1.8.2 → 1.10.2 (Gradle "highest wins"), producing a half-upgraded Compose stack that is not a supported combination. By contrast Koin **4.1.1** declares `kotlin-stdlib 2.1.21` + `org.jetbrains.compose.runtime:runtime:1.8.2`, and SQLDelight **2.2.1** declares `kotlin-stdlib 2.2.21` — those two are the versions actually built for the locked CMP/Kotlin generation.
2. **Environment incompatibility.** This machine has **Xcode 27.0** (Kotlin 2.2.20 officially supports Xcode ≤ 16.4 per JetBrains' compatibility table) and only **JDK 25 / JDK 26** installed (Gradle 8.14 — the newest Gradle Kotlin 2.2.20 supports — requires a JVM ≤ 24 to *run*). Both are hard blockers for the locked stack and neither can be resolved inside the repo.
3. **Wizard conflict.** `kmp.jetbrains.com` / the IntelliJ KMP wizard always emits the *current* stack (today: Kotlin 2.4.10, AGP 9.1.1, CMP 1.11.1, Koin 4.2.2, minSdk 24). "Bootstrap via the wizard" and "pin Kotlin 2.2.20 / CMP 1.8.2 / AGP 8.x" cannot both be satisfied literally — the generated `libs.versions.toml` must be rewritten and the `androidTarget` DSL restored.

**Primary recommendation:** Run a **Wave 0 compatibility spike** (build + run a hello-world on both platforms with the chosen pin set) as the very first task of the phase, and have the user re-confirm the version set at that checkpoint. If the user wants zero environment changes, adopt the *current* wizard-default stack (Kotlin 2.4.x / AGP 9.x / CMP 1.12.x / Koin 4.2.2 / SQLDelight 2.4.1), which matches the installed JDK 26 + Gradle 9.x + Android SDK 37 + Xcode 27. If the user wants the locked pins preserved, the plan must include user-approved installs of **JDK 21** and **Xcode 16.4**, plus a downgrade of Koin → 4.1.1 and SQLDelight → 2.2.1. Everything else in this document is valid under either choice; only the version numbers move.

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
**Mileage & Units (schema-affecting — locked before schema design)**
- **D-01:** Kilometer is the app's mileage unit by default. The unit is a **global** app setting conceptually; v1 is km-only with **no toggle UI** — a future km/miles toggle would live global, not per-vehicle.
- **D-02:** Store raw kilometers as `INTEGER` in the database. Conversion (if a unit toggle arrives later) happens at the display layer only — never store value+unit columns.
- **D-03:** The vehicle table has **no odometer column** in Phase 1. Odometer lives on service entries (Phase 2). Vehicle fields: name, make, model, year, license plate, type, photo path.

**Vehicle Photos**
- **D-04:** Photos come from **gallery only** via the system photo picker (Compose Multiplatform photo picker / PhotosPicker on iOS). No camera capture and no camera permission in Phase 1.
- **D-05:** Photo storage: the picked image is **persisted into the app's own storage directory** (Android `filesDir` / iOS Documents); the DB stores **only the relative path string** — never a BLOB, never an external-content URI.
- **D-06:** Attaching a photo is **optional**. Empty state on cards/detail shows a placeholder (see D-15).
- **D-07:** Deleting a vehicle **deletes its photo file from disk** too — no orphan files. (Replace-photo on edit reuses the same picker; file handling details are agent discretion.)

**Add/Edit Form & Validation**
- **D-08:** The Add/Edit vehicle form is a **single screen** — all fields on one form, no sections/multi-step.
- **D-09:** **Only `name` is required.** Make, model, year, license plate, type, photo are all optional — logging a car must be fast; details can be filled via Edit later.
- **D-10:** Vehicle type picker is a **fixed list: Car, Motorcycle, Truck**. The DB stores the type as a plain string so new types can be added later without a migration.
- **D-11:** UI language is **English only** in v1 — user-facing strings centralized in a strings file from day one (not hardcoded inline), but no localization framework yet.
- **D-12:** License plate is **free text** — optional, no format validation, no format hint.
- **D-13:** Year is a **digits-only numeric text field** (number keyboard), not a dropdown/roller picker.

**List, Detail & Navigation**
- **D-14:** Each vehicle card on the list is an **identity card**: leading thumbnail (~64px, uniform) or, when no photo, a circle with the vehicle-type icon (car/motorcycle/truck) on a light background; plus name, `make · model · year` line, and license plate.
- **D-15:** Empty home screen shows a **short message + prominent "Add vehicle" CTA button** (not message-only).
- **D-16:** Vehicle list is ordered **newest first** (most recently added at top).
- **D-17:** Phase 1 scaffolds the **bottom tab-bar shell shared by both platforms** (Compose Multiplatform — no platform-specific UI duplication): **Vehicles** tab (functional) + **Settings** tab (placeholder screen showing only an "App version" row). Later phases fill existing slots rather than retrofitting a shell.
- **D-18:** Tapping a card opens a **Detail screen** — hero photo (or placeholder), name, field rows for all fields, and a reserved "Service history (coming soon)" hint slot (Phase 2 fills it). **Edit and Delete both live on Detail**; delete shows a confirmation dialog: "Delete [name] and all its records?" — never long-press-on-list.
- **D-19:** "Add vehicle" lives in a **FAB** (floating action button, bottom-right) on the vehicle list. The empty state still shows its own big CTA.
- **D-20:** Top app bar across screens: **simple title bar** — title on white, plain back arrow. No large titles, no gesture-only navigation.

**Visual Language**
- **D-21:** Accent/highlight color: **agent uses neutral near-black ink on white meanwhile** — the user wants to decide the accent color after seeing the app build (explicitly deferred, tracked below).
- **D-22:** Phase 1 ships a **placeholder app icon** (simple glyph / solid color). Custom icon design is a later polish task.

### the agent's Discretion
- Exact placeholder icon artwork, card spacing tokens, field ordering within the form
- Photo decode/downsizing details (e.g., max dimension for stored file) and thumbnail sizing logic — keep the DB-lean principle from D-05
- Exact wording of empty-state message, confirm dialog text, detail field-row layout
- Photo-picker library wiring details on each platform
- DB write serialization implementation details (STATE.md already locks the pattern: `Dispatchers.IO.limitedParallelism(1)`)

### Deferred Ideas (OUT OF SCOPE)
- **Accent color decision** — user explicitly wants to choose after seeing the build (neutral ink is the interim default, D-21)
- **Custom app icon** — later polish task (D-22)
- **km/miles toggle UI** — global setting, future (D-01)
- **Current-odometer field on vehicle** — rejected for Phase 1 schema; revisit only if Phase 2 research demands it (D-03)

### Additional Project Constraints (from AGENTS.md — global working rules)
- **No state-changing action without explicit user approval**: no file creation/modification outside what is asked, no global config edits, no package installs (`brew`, `npm -g`), no background services/autostart, no opening ports, no `git push`/`commit` that changes remote state.
- Read-only actions (read, `ls`, `cat`, `grep`, `find`, `which`, `git status/log/diff`, process/port inspection) are allowed without asking.
- Scope discipline: do exactly what was asked; propose extras instead of doing them.
- **Planning consequence:** the JDK 21 / Xcode 16.4 install steps implied by the locked stack are **not** autonomous tasks — they must be a `checkpoint:human-verify` gate.

</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| DAT-01 | All data stored locally on device (offline-first, no account) | SQLDelight 2.x file-backed driver on both platforms; no network dependency anywhere in the stack; `DriverFactory` expect/actual (§Architecture Patterns, Pattern 1) |
| DAT-02 | Runs on Android (API 26+) and iOS (16+) from a shared KMP codebase | KMP Gradle target config (`androidTarget`, `iosArm64`, `iosSimulatorArm64`), Xcode `IPHONEOS_DEPLOYMENT_TARGET=16.0`, Android `minSdk=26`; single `shared`/`composeApp` module with `commonMain` UI (§Recommended Project Structure) |
| DAT-03 | SQLite uses WAL mode from first launch (iOS SIGKILL corruption guard) | **Verified:** touchlab SQLiter (the engine behind `NativeSqliteDriver`) defaults `journalMode = JournalMode.WAL`; Android must be set/verified explicitly. Two implementation options documented (§Pattern 1 + Code Examples) |
| DAT-04 | All schema changes include a `.sqm` migration file verified in CI | `schemaOutputDirectory` + `generate<SourceSet><Database>Schema` produce the `1.db` baseline; `verify<SourceSet><Database>Migration` enforces it and CI adds a `git diff --exit-code` schema guard (§Pattern 1 + Code Examples) |
| VEH-01 | Add a vehicle (name, make, model, year, plate, type) | `vehicles` table `.sq` schema (only `name` NOT NULL per D-09) + `AddEditVehicleScreen` + `VehicleFormState` validation (D-08, D-13) |
| VEH-02 | Attach a photo to a vehicle | FileKit 0.11.0 gallery picker (`FileKitType.Image` → Android Photo Picker / iOS `PHPickerViewController`), compress → write into `FileKit.filesDir`, store relative path only (D-04…D-06) |
| VEH-03 | Edit vehicle details | Same single form screen in "edit" mode; `UPDATE` query; photo replace reuses picker and deletes the previous file (D-07) |
| VEH-04 | Delete a vehicle and all its records | Detail-screen confirm dialog (D-18); repository deletes row + photo file (D-07); Phase-2 child tables must declare `ON DELETE CASCADE` with `foreign_keys` ON |
| VEH-05 | View a list of all vehicles | `VehicleListViewModel` + `asFlow().mapToList(ioDispatcher)`, ordered newest-first (D-16), FAB + empty-state CTA (D-15, D-19), identity card layout (D-14) |
</phase_requirements>

## Architectural Responsibility Map

This phase is unusual: **one codebase owns every tier**, so the map below is about *module/source-set* ownership, not deployable tiers. The planner should use it to sanity-check task placement (e.g. "photo picker wiring" is a *shared-API + platform-actual* task, not a UI-only task).

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Compose UI (all screens, tab shell, theming) | `commonMain` | — | D-17 forbids platform-specific UI duplication; CMP renders via Skia on both platforms |
| Navigation graph + type-safe routes | `commonMain` | — | `org.jetbrains.androidx.navigation` works identically from `commonMain`; iOS back gesture handled by the library |
| Domain models + validation rules | `commonMain` | — | Pure Kotlin, testable in `commonTest`; must not reference SQLDelight generated types (STATE.md constraint) |
| Repository / data access | `commonMain` | `androidMain`/`iosMain` (driver only) | One repository; only the `SqlDriver` is platform-specific |
| SQLDelight schema + queries + migrations | `commonMain` (`src/commonMain/sqldelight/`) | — | `.sq`/`.sqm` live with the shared code; codegen produces `commonMain` Kotlin |
| SQL driver construction (path, WAL, FK pragmas) | `androidMain` / `iosMain` | — | Genuinely divergent: `AndroidSqliteDriver` needs a `Context`; `NativeSqliteDriver` needs a documents path |
| App-private file storage (photo dir) | `androidMain` / `iosMain` | `commonMain` facade | `FileKit.filesDir` gives a shared facade over `context.filesDir` / iOS Documents |
| Gallery/photo picker launch | `iosMain` / `androidMain` (behind FileKit) | `commonMain` call sites | PHPickerViewController vs Android Photo Picker are platform APIs; FileKit exposes one `commonMain` API |
| Image loading/thumbnails | `commonMain` (Coil 3) | — | `filekit-coil` registers a `PlatformFile` fetcher usable from shared code |
| DI container + ViewModel factory | `commonMain` | `androidMain` (`androidContext`, `androidLogger`) | Koin `startKoin` in shared code; only Android needs a platform module |
| DB write serialization | `commonMain` | — | Single `Dispatchers.IO.limitedParallelism(1)` injected into repositories (STATE.md locked pattern) |
| WAL verification | `androidMain` + `iosMain` (smoke test) | — | `PRAGMA journal_mode` can only be asserted through a real platform driver |
| Migration verification in CI | Repo root (`.github/workflows/`) + Gradle config | — | Gradle task `check`/`verifySqlDelightMigration` must run on a machine with the Android SDK; must NOT be a UI-layer concern |

## Standard Stack

> **Read the compatibility note first.** The pinned versions below are the *aligned-down* set that is internally consistent with Kotlin 2.2.20 + CMP 1.8.2. Two locked versions — SQLDelight 2.3.2 and Koin 4.2.0 — are **not** compatible with the locked Kotlin/CMP pins (evidence below) and are marked ⚠️.

### Core

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | **2.2.20** (locked) | Language + KMP runtime, Compose compiler plugin | K2 required by CMP 1.8+; `org.jetbrains.kotlin.plugin.compose` must equal the KGP version [VERIFIED: JetBrains compatibility table] |
| Compose Multiplatform | **1.8.2** (locked) | Shared UI for iOS + Android | iOS declared Stable in CMP 1.8.0 (May 2025); 1.8.2 released 2025-06-17 [VERIFIED: GitHub releases API] |
| SQLDelight | **2.2.1** ⚠️(replaces locked 2.3.2) | Local-first SQLite with compile-time-verified SQL | 2.2.1 is built with Kotlin 2.2.21 (`kotlin-stdlib 2.2.21`) — the only 2.x line that matches Kotlin 2.2.20. 2.3.x declares `kotlin-stdlib 2.3.10` [VERIFIED: Maven `.module` metadata] |
| Koin | **4.1.1** ⚠️(replaces locked 4.2.0) | KMP-native DI, `koinViewModel()` in `commonMain` | 4.1.1 declares `compose.runtime:runtime 1.8.2` + `kotlin-stdlib 2.1.21`. 4.2.0 declares `compose.runtime:runtime 1.10.2` + `kotlin-stdlib 2.3.20`, which force-upgrades the Compose stack [VERIFIED: Maven `.module` metadata] |
| Navigation Compose (JetBrains) | **2.9.0** (locked) | Type-safe routes, NavHost, iOS back gesture | Its published metadata depends on CMP **1.8.2** exactly — a perfect match for the locked CMP pin [VERIFIED: Maven POM] |
| kotlinx.coroutines | **1.10.2** | Flows, structured concurrency, `limitedParallelism` | Required floor for the locked CMP/SQLDelight generation; latest is 1.11.0 |
| kotlinx.serialization | **1.7.3** | `@Serializable` nav routes | **Correction to STACK.md:** kotlinx-serialization is *not* versioned with Kotlin — 1.7.3 is what navigation-compose 2.9.0 declares. Never write `serialization = "2.2.20"` [VERIFIED: Maven metadata + nav POM] |
| AGP | **8.11.1** | Android build tooling | Max AGP supported by Kotlin 2.2.20 is 8.11.1 (STACK.md's 8.10.1 is also fine) [VERIFIED: JetBrains compatibility table] |
| Gradle (wrapper) | **8.14.6** | Build system | Kotlin 2.2.20 supports Gradle 7.6.3–8.14; 8.14.6 is the newest 8.14.x [VERIFIED: services.gradle.org] |
| Xcode | **≤ 16.4** ⚠️ | iOS framework build/signing | Kotlin 2.2.20's supported Xcode max is 16.4. **Installed here: 27.0.** [VERIFIED: JetBrains compatibility table + local `xcodebuild -version`] |

### Supporting

| Library | Version | Purpose | When to Use |
|---------|---------|---------|-------------|
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` | **2.9.4** | Shared `ViewModel` in `commonMain` | Required floor — navigation-compose 2.9.0 declares 2.9.4 (STACK.md's 2.9.1 will be upgraded silently) |
| `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose` | 2.9.4 | `viewModel()` bridge if not using Koin's overload | Only if you skip `koinViewModel()` |
| `io.github.vinceglb:filekit-dialogs-compose` | **0.11.0** | Gallery-only photo picker (Android Photo Picker / iOS PHPicker) | The phase's picker. Built against CMP 1.8.2 + Kotlin 2.2.10 — exact match [VERIFIED: tag's `libs.versions.toml` + POM] |
| `io.github.vinceglb:filekit-core` | **0.11.0** | `FileKit.filesDir`, `PlatformFile`, `compressImage` | App-private photo storage + downsizing (D-05/D-07) |
| `io.github.vinceglb:filekit-coil` | **0.11.0** | Coil 3 fetcher for `PlatformFile` | Rendering thumbnails/hero photos without converting to raw paths |
| `io.coil-kt.coil3:coil-compose` | **3.3.0** | Async image loading in Compose | Built with `kotlin-stdlib 2.2.0`; FileKit 0.11.0 pins Coil 3.3.0 [VERIFIED: Maven `.module` + FileKit catalog] |
| Compose Resources (`compose.components.resources`) | bundled with CMP 1.8.2 | Centralised user-facing strings (D-11) | Only sanctioned way to hold strings in `commonMain` (Android `R.string` does not exist there) |

### Alternatives Considered

| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| FileKit 0.11.0 | Hand-rolled `expect/actual` (`PickVisualMedia` on Android + `PHPickerViewController` interop on iOS) | ~150–250 lines of platform code, plus `UIViewController` presentation plumbing on iOS. Only worth it if the team refuses a third-party dep. FileKit *is* the community standard and is a thin wrapper over exactly these two APIs. |
| FileKit 0.11.0 | `Peekaboo` (`io.github.onseok:peekaboo-image-picker`) | Abandoned: last release 0.5.2 in Apr 2024, no iOS/Android split, predates CMP 1.8 [VERIFIED: Maven metadata] |
| FileKit 0.11.0 | `com.darkrockstudios:mpfilepicker` | Abandoned: last release 3.1.0 in Dec 2023 [VERIFIED: Maven metadata] |
| Coil 3.3.0 | Decode `ByteArray` → `ImageBitmap` manually | Loses memory caching, size-aware downsampling and `maxBitmapSize` OOM guard — classic hand-roll trap |
| `kotlinx-datetime` | No date library in Phase 1 | See Open Question OQ-4: Phase 1 needs no calendar math (year is UI text, ordering is by row id). Avoiding it removes the entire 0.6.x-compat-artifact risk from the phase. |
| AGP 8.11.1 + `com.android.library`/`application` | AGP 9.x + `com.android.kotlin.multiplatform.library` | Only viable if Kotlin moves to ≥2.3.0 (the new KMP Android plugin + `android {}` block replace `androidTarget`; Kotlin 2.3.0 deprecated `androidTarget` and 2.3.10 reverted the warning). Kotlin 2.2.20 **requires** the AGP 8.x + `androidTarget` path. |

**Installation (aligned-down set, `gradle/libs.versions.toml`):**

```toml
[versions]
kotlin = "2.2.20"
agp = "8.11.1"
compose-multiplatform = "1.8.2"
sqldelight = "2.2.1"
koin = "4.1.1"
navigation = "2.9.0"
lifecycle = "2.9.4"
coroutines = "1.10.2"
serialization = "1.7.3"
filekit = "0.11.0"
coil = "3.3.0"
android-compileSdk = "36"
android-minSdk = "26"
android-targetSdk = "36"

[libraries]
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }
koin-bom = { module = "io.insert-koin:koin-bom", version.ref = "koin" }
koin-core = { module = "io.insert-koin:koin-core" }
koin-compose = { module = "io.insert-koin:koin-compose" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel" }
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
filekit-dialogs-compose = { module = "io.github.vinceglb:filekit-dialogs-compose", version.ref = "filekit" }
filekit-core = { module = "io.github.vinceglb:filekit-core", version.ref = "filekit" }
filekit-coil = { module = "io.github.vinceglb:filekit-coil", version.ref = "filekit" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

```bash
# Gradle wrapper — pin explicitly; no standalone Gradle is installed on this machine
./gradlew wrapper --gradle-version 8.14.6
```

**Version verification performed (tool: direct Maven Central metadata + POM/Gradle `.module` reads, 2026-10-18):**

| Artifact | Verified | Evidence |
|---|---|---|
| `app.cash.sqldelight:*` 2.2.1 / 2.3.1 / 2.3.2 / 2.4.1 | exists | `maven-metadata.xml`; 2.2.1 → `kotlin-stdlib 2.2.21`; 2.3.1 & 2.3.2 → `kotlin-stdlib 2.3.10`; latest 2.4.1 |
| `io.insert-koin:koin-compose` 4.1.1 → `compose.runtime 1.8.2`; 4.2.0/4.2.2 → `compose.runtime 1.10.2` | exists | `.module` `metadataApiElements` |
| `io.insert-koin:koin-compose-viewmodel:4.2.0` → also requires `lifecycle-viewmodel-compose 2.9.6` | exists | `.module` |
| `org.jetbrains.androidx.navigation:navigation-compose:2.9.0` → `compose.* 1.8.2`, `lifecycle 2.9.4`, `serialization-core 1.7.3` | exists | POM + `.module` |
| `io.github.vinceglb:filekit-dialogs-compose / -core / -coil:0.11.0` → `compose.runtime 1.8.2`, `kotlin-stdlib 2.2.10`, `coil-compose 3.3.0` | exists | POM + tag `libs.versions.toml` |
| `io.coil-kt.coil3:coil-compose:3.3.0` → `kotlin-stdlib 2.2.0` | exists | `.module` |
| `org.jetbrains.compose:*` 1.8.2 | exists | `maven-metadata.xml` (latest stable on 2026-10 is 1.12.2) |
| `org.jetbrains.kotlin:kotlin-gradle-plugin:2.2.20` | exists | `maven-metadata.xml` (latest 2.4.21) |

## Package Legitimacy Audit

**Gate applicability note:** the `gsd-tools query package-legitimacy check` seam supports `npm|pypi|crates` only. Every Phase-1 dependency is a **Maven Central** artifact, so the npm-oriented gate returns meaningless cross-ecosystem verdicts (it reported `koin` → `SUS` and `sqldelight` → `SLOP` because the npm registry happens to contain unrelated packages of those names — the exact slopsquat/namespace-confusion failure mode the protocol warns about). Legitimacy was therefore verified directly against Maven Central metadata, each project's official documentation site, and its official source repository.

| Package | Registry | Age | Published by | Source Repo | Verdict | Disposition |
|---------|----------|-----|--------------|-------------|---------|-------------|
| `app.cash.sqldelight:*` | Maven Central | ~10 yrs | Square / Cash App | `github.com/sqldelight/sqldelight` | OK | Approved |
| `io.insert-koin:koin-*` | Maven Central | ~9 yrs | Kotzilla | `github.com/InsertKoinIO/koin` | OK | Approved |
| `org.jetbrains.androidx.navigation:navigation-compose` | Maven Central | 2.x line | JetBrains | JetBrains (Maven-Central-published; docs on kotlinlang.org) | OK | Approved |
| `org.jetbrains.androidx.lifecycle:*` | Maven Central | 2.x line | JetBrains | JetBrains | OK | Approved |
| `org.jetbrains.compose:*` | Maven Central | since 2021 | JetBrains | `github.com/JetBrains/compose-multiplatform` | OK | Approved |
| `io.github.vinceglb:filekit-*` | Maven Central | since Mar 2024 | Vincent Guillebaud | `github.com/vinceglb/FileKit` | OK | Approved |
| `io.coil-kt.coil3:coil-compose` | Maven Central | since 2021 | Coil maintainers | `github.com/coil-kt/coil` | OK | Approved |
| `org.jetbrains.kotlinx:*` | Maven Central | ~7 yrs | JetBrains | `github.com/Kotlin/kotlinx.coroutines` etc. | OK | Approved |

**Packages removed due to SLOP verdict:** none
**Packages flagged as suspicious (SUS):** none — note that **every FileKit and Coil version recommended here was cross-checked against the library's own version catalog / POM**, not inferred from training data, so no `[ASSUMED]` gating is required for them.

*No package in this phase was discovered solely via web search or training data; all were confirmed against official documentation and registry metadata.*

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Gallery photo picking on both platforms | `expect/actual` around `PickVisualMedia` + `PHPickerViewController` interop | **FileKit `filekit-dialogs-compose`** `rememberFilePickerLauncher(type = FileKitType.Image)` | You'd have to implement `UIViewController` presentation, delegate/continuation bridging, `NSItemProvider` load, Android 13+ `PickVisualMedia` vs pre-13 `ACTION_OPEN_DOCUMENT` fallback, cancel handling and URI→bytes. FileKit is a thin, maintained wrapper over exactly those APIs and is built against CMP 1.8.2. |
| App-private file storage | `expect/actual` over `context.filesDir` / `NSSearchPathForDirectoriesInDomains` | **FileKit `FileKit.filesDir`** + `PlatformFile(FileKit.filesDir, "vehicles/<id>.jpg")` | Same directory semantics on both platforms (Android `filesDir`, iOS Documents) with a single `commonMain` API; gives you `write(bytes)`, `exists()`, `delete()` without platform code. |
| Image decode / downsizing / memory cache | Manual `BitmapFactory`/`UIImage` decode, manual `maxBitmapSize` guard | **Coil 3.3.0** (+ `filekit-coil` fetcher) | Coil handles size-aware downsampling, LRU memory+disk cache, and OOM guards. Hand-rolled decoders are the #1 source of Android OOM crashes in list screens. |
| Photo down-scaling before persisting | Manual scale math in platform code | **FileKit `PlatformFile.compressImage()` / `ImageCompression`** | One call, both platforms, honours max-dimension/quality in a single API. |
| Navigation state across Android/iOS | Hand-rolled navigation stack / `when(route)` switch | **Navigation Compose 2.9.0** type-safe routes (`@Serializable` objects, `NavHost`, `composable<T>`) | Back-stack, iOS back-swipe, and per-route ViewModel scoping come for free; a hand-rolled stack breaks iOS predictive-back. |
| DI graph | Manual service locator / singleton objects | **Koin 4.1.1** (`koinViewModel()` in `commonMain`) | Scoped ViewModels tied to nav entries require lifecycle integration; Koin's `koinViewModel()` already does that on both platforms. |
| SQL + schema migrations | Raw `SQLiteDatabase` / `NSQLiteDB` calls and hand-written `ALTER TABLE` migrations | **SQLDelight 2.2.1** (`.sq` + `.sqm` + `verifySqlDelightMigration`) | SQLDelight verifies migrations against a checked-in schema snapshot at build time; hand-written migrations silently drift from the schema. |
| Multiplatform resource strings | Hardcoded literals or `expect/actual` string holders | **Compose Resources** (`composeResources/values/strings.xml` + `Res.string.*`) | The only sanctioned mechanism in `commonMain`; Android `R.string` does not exist in shared code (D-11 requires centralised strings). |
| Observable DB queries | Manual `Flow` polling / re-query-on-resume | **`sqldelight-coroutines-extensions`** (`asFlow()`) | SQLDelight's query notifications + coroutine extension give correct invalidation; polling either misses writes or wastes CPU. |

**Key insight:** Every item above has a maintained, CMP-1.8.2-aligned library. The phase's real complexity is *wiring* (version alignment, WAL-on-both-drivers, migration CI), not building UI plumbing — so the plan should spend its risk budget there, not on reinventing a picker or a nav stack.

## Architecture Patterns

### System Architecture Diagram

The diagram traces the phase's primary use case — *"user adds a vehicle with a photo, then sees it on the list"* — through the tiers. Data flows **UI → ViewModel → Repository → Driver/SQLDelight → SQLite**, and **UI → FileKit → app-private storage** for the photo, with the DB holding only the returned relative path.

```
                         ┌──────────────────────── ONE shared codebase (commonMain) ───────────────────────┐
                         │                                                                                │
  Android Activity ─┐    │   App()                                                                        │
  (MainActivity)    ├───►│    │  KoinApplication(modules = appModule)                                       │
  iOS UIViewController┘  │    │  NavHost(startDestination = VehicleListRoute)                              │
  (ComposeUIViewController)│   │                                                                              │
                         │    ├─► VehiclesRoute ──► VehicleListScreen ──► VehicleListViewModel           │
                         │    │                                   │            │                            │
                         │    │                                   │            ├─ observeVehicles()  ──┐    │
                         │    │                                   │            └─ deleteVehicle(id) ──┼─┐  │
                         │    ├─► VehicleEditRoute ──► VehicleEditScreen ──► VehicleEditViewModel     │ │  │
                         │    │        │                                                              │ │  │
                         │    │        │  "Pick photo"  ── FileKit gallery picker (expect/actual inside │ │  │
                         │    │        │                    filekit-dialogs)                            │ │  │
                         │    │        ▼                                   │                            │ │  │
                         │    │   PlatformFile(temp)  ──compressImage──►  PlatformFile(FileKit.filesDir,│ │  │
                         │    │                                          "vehicles/<id>.jpg").write()  │ │  │
                         │    │        │                                   │                            │ │  │
                         │    │        └── returns RELATIVE PATH STRING ───┘                            │ │  │
                         │    │                        │                                               │ │  │
                         │    │                        └─ saveVehicle(VehicleForm) ─────────────────┐  │ │  │
                         │    └─► SettingsRoute ──► SettingsScreen (placeholder "App version")      │  │ │  │
                         │                                                                          ▼  ▼  ▼  │
                         │                    VehicleRepository  (interface, commonMain)  ◄───────────────┘  │
                         │                          │ implement              ▲                                │
                         │                          │                      │ maps to/from                   │
                         │                          │                      │                                │
                         │            ┌─────────────┴──────────┐   VehicleEntity ──► Vehicle (domain model)   │
                         │            │ VehicleRepositoryImpl    │   (SQLDelight generated type)    mapper     │
                         │            └─────────────┬──────────┘                                          │
                         │                          │ sqlDriver + withContext(Dispatchers.IO             │
                         │                          │             .limitedParallelism(1))                  │
                         │            ┌─────────────┴───────────────┐    CarRecordDatabase (generated)        │
                         │            │ expect fun createDriver()   │ ──► vehicleQueries (generated)          │
                         │            └─────────────┬───────────────┘                                       │
                         └──────────────────────────┼────────────────────────────────────────────────────┘
                                                    │
                       ┌────────────────────────────┴──────────────────────────┐
                       │                        actual                         │
        ┌──────────────┴───────────────┐                    ┌─────────────────┴──────────────────┐
        │ androidMain                  │                    │ iosMain                            │
        │ AndroidSqliteDriver(         │                    │ NativeSqliteDriver(                │
        │   CarRecordDatabase.Schema,  │                    │   CarRecordDatabase.Schema,        │
        │   context, "carrecord.db")   │                    │   "carrecord.db")                  │
        │ (+ explicit WAL pragma)      │                    │ (JournalMode.WAL is the DEFAULT)   │
        └──────────────┬───────────────┘                    └─────────────────┬──────────────────┘
                       │                                                      │
                       └──────────────────► SQLite file on device ◄───────────┘
                                 (schema created by generated Schema.create,
                                  WAL pragma emitted from .sq preamble)
```

**Reading the diagram:** the picker is the *only* intentionally platform-specific piece and it is inside FileKit; everything above the `expect fun createDriver()` line is `commonMain`. The photo's bytes never enter the DB — only the relative path string does (D-05), which is what makes the "delete photo file on vehicle delete" flow (D-07) a plain `PlatformFile.delete()` in the repository.

### Recommended Project Structure

```
CarRecord/
├── gradle/
│   ├── libs.versions.toml            # single source of versions (table above)
│   └── wrapper/gradle-wrapper.properties   # 8.14.6
├── composeApp/                        # the shared module (wizard's default name)
│   └── src/
│       ├── commonMain/
│       │   ├── kotlin/com/carrecord/
│       │   │   ├── App.kt                          # root composable: Koin + NavHost + Scaffold(bottomBar)
│       │   │   ├── di/AppModule.kt                 # Koin module (single place to wire everything)
│       │   │   ├── data/
│       │   │   │   ├── db/DriverFactory.kt         # expect fun createDriver()
│       │   │   │   ├── db/DatabaseFactory.kt       # SqlDriver → CarRecordDatabase
│       │   │   │   ├── VehicleEntityMapper.kt      # VehicleEntity ↔ Vehicle (SQLDelight types stay here)
│       │   │   │   ├── VehicleRepository.kt        # interface (domain-facing)
│       │   │   │   └── VehicleRepositoryImpl.kt    # impl + dispatcher injection
│       │   │   ├── domain/Vehicle.kt               # plain data class, NO SQLDelight imports
│       │   │   ├── ui/
│       │   │   │   ├── theme/                      # MaterialTheme tokens (D-21 neutral ink)
│       │   │   │   ├── navigation/Routes.kt        # @Serializable route objects
│       │   │   │   ├── vehicles/
│       │   │   │   │   ├── VehicleListScreen.kt
│       │   │   │   │   ├── VehicleListViewModel.kt
│       │   │   │   │   ├── VehicleEditScreen.kt
│       │   │   │   │   ├── VehicleEditViewModel.kt
│       │   │   │   │   └── VehicleDetailScreen.kt / VehicleDetailViewModel.kt
│       │   │   │   └── settings/SettingsScreen.kt
│       │   │   └── util/PhotoStorage.kt            # save/replace/delete photo (FileKit)
│       │   ├── sqldelight/com/carrecord/db/
│       │   │   ├── Vehicle.sq                      # CREATE TABLE + queries (WAL pragma preamble)
│       │   │   └── migrations/                     # EMPTY in phase 1 (1.db baseline only)
│       │   └── composeResources/values/strings.xml # D-11 centralised strings
│       ├── androidMain/kotlin/com/carrecord/db/DriverFactory.android.kt
│       ├── iosMain/kotlin/com/carrecord/db/DriverFactory.ios.kt
│       └── commonTest/kotlin/…                     # repository/mapper tests
├── iosApp/iosApp.xcodeproj            # Xcode project; ContentView → ComposeUIViewController
├── androidApp/                        # optional thin Android application module (wizard variant)
├── .github/workflows/ci.yml           # migration verification + build
└── settings.gradle.kts
```

> **Where the DB schema files live matters.** SQLDelight scans `src/<sourceSet>/sqldelight/`. For a KMP module the schema source set is `commonMain`, so migrations go in `composeApp/src/commonMain/sqldelight/<package>/migrations/<version>.sqm` and the generated schema task is `generateCommonMainCarRecordDatabaseSchema`. A file placed under `src/main/sqldelight/` would be silently ignored by the KMP tasks.

### Pattern 1: Migration-safe SQLDelight schema from day one (DAT-01, DAT-04)

**What:** Keep a checked-in schema snapshot (`1.db`) plus a `migrations/` folder wired into a CI task, so any future schema change must ship a `.sqm` migration file or the build fails.

**When to use:** From the very first commit of the phase — retrofitting this after tables exist is painful because the snapshot must be regenerated and validated against a known-good baseline.

**Example (`composeApp/build.gradle.kts`):**
```kotlin
// Source: SQLDelight official docs — Migrations (sqldelight.github.io/sqldelight/2.x/multiplatform_sqlite/migrations/)
sqldelight {
    databases {
        create("CarRecordDatabase") {
            packageName.set("com.carrecord.db")
            // REQUIRED — without this NO migration-verification task is registered at all.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            // Phase 1: keep queries synchronous (resolves STATE.md open question — see Pitfall 6).
            generateAsync.set(false)
        }
    }
}
```

Workflow: **add** the table to `Vehicle.sq` → run `./gradlew generateCommonMainCarRecordDatabaseSchema` → commit the produced `src/commonMain/sqldelight/databases/1.db`. Later, to change the schema: bump and add `migrations/2.sqm`, then the CI task below proves the migration replays on top of `1.db` to reach the new schema.

### Pattern 2: WAL from first launch on both drivers (DAT-02)

**What:** SQLite's Write-Ahead Logging journal mode must be active on *the very first* connection, on *both* platforms. Prefer declaring it in SQL so it is emitted by the generated `Schema.create()`.

**When to use:** Always — this is a locked decision, and it must be provable from the first launch (before any user write).

**Example — declare it in the `.sq` file so the generated schema carries it:**
```sql
-- composeApp/src/commonMain/sqldelight/com/carrecord/db/Vehicle.sq  (executable preamble)
PRAGMA journal_mode=wal;

CREATE TABLE vehicle (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    make       TEXT,
    model      TEXT,
    year       TEXT,
    plate      TEXT,
    type       TEXT    NOT NULL DEFAULT 'Car',
    photo_path TEXT
);

selectAll: SELECT * FROM vehicle ORDER BY id DESC;   -- D-16: newest first
selectById: SELECT * FROM vehicle WHERE id = ?;
insert: INSERT INTO vehicle(name, make, model, year, plate, type, photo_path)
        VALUES (?, ?, ?, ?, ?, ?, ?);
update: UPDATE vehicle SET name=?, make=?, model=?, year=?, plate=?, type=?, photo_path=? WHERE id=?;
delete: DELETE FROM vehicle WHERE id=?;
```

`PRAGMA` statements written in a `.sq` file are emitted inside the generated `Schema.create(driver)` as `driver.execute(null, "PRAGMA journal_mode=wal", 0)`, so the mode is set during table creation — i.e. on first launch (this is the mechanism the locked CI check `verifySqlDelightMigration` + a schema grep is looking for). On iOS, `co.touchlab.sqliter.DatabaseConfiguration.journalMode` already defaults to `JournalMode.WAL`; on Android the driver does **not**, which is exactly why the SQL-level pragma is the portable choice.

### Pattern 3: Database writes serialized on one IO lane (DAT-03)

**What:** All DB access goes through one injected dispatcher — `Dispatchers.IO.limitedParallelism(1)` — so writes never interleave and WAL gets no spurious "database is locked" contention.

**When to use:** Every repository method that touches the driver (reads included; reads are cheap and serializing them simplifies reasoning).

**Example:**
```kotlin
// Source: kotlinx.coroutines docs — Dispatchers.IO.limitedParallelism(1)
class VehicleRepositoryImpl(
    private val db: CarRecordDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> =
        db.vehicleQueries.selectAll()
            .asFlow()                                  // sqldelight-coroutines-extensions
            .mapToList(ioDispatcher)                   // stays on the single IO lane
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun save(vehicle: Vehicle): Long = withContext(ioDispatcher) {
        db.transactionWithResult {
            if (vehicle.id == 0L) {
                db.vehicleQueries.insert(/* … */)
                db.vehicleQueries.lastInsertId().executeAsOne()   // portable id retrieval — see gotcha
            } else {
                db.vehicleQueries.update(/* … */)
                vehicle.id
            }
        }
    }
}
```

Wrap multi-statement writes (vehicle row + photo-path bookkeeping) in `transactionWithResult { }` — SQLDelight emits `BEGIN`/`COMMIT` in the right place and rolls back on exception.

> **Gotcha (insert id — do NOT use `RETURNING`):** the `insert` query is a plain `INSERT` with no `RETURNING` clause, so SQLDelight generates a `Unit`-returning mutation and `insert(...).executeAsOne()` does not exist. Do not "fix" this with `INSERT ... RETURNING id` — `RETURNING` requires SQLite ≥ 3.35 and the Android driver runs the OS SQLite, which is < 3.35 across API 26–33 (minSdk 26). Instead add a portable helper query to `Vehicle.sq` — `lastInsertId: SELECT last_insert_rowid();` — and call `lastInsertId().executeAsOne()` **inside the same `transactionWithResult` block** immediately after the insert, so the id is read on the same connection/transaction and cannot race another writer.



### Pattern 4: Photo persisted as a file, DB holds only the relative path (D-05, D-07)

**What:** The picked temp file is copied into `FileKit.filesDir/vehicles/<id>.jpg` and only the relative path string is written to SQLite. Deleting a vehicle deletes the file.

**When to use:** Every photo attach/replace/delete.

**Example:**
```kotlin
// Source: FileKit docs (filekit.mintlify.app) — write/compress/delete + FileKit.filesDir
object PhotoStorage {
    private const val DIR = "vehicles"

    suspend fun persist(picked: PlatformFile, fileName: String): String {
        val target = PlatformFile(FileKit.filesDir, "$DIR/$fileName")   // Android filesDir / iOS Documents
        target.parent().createDirectories()
        target.write(picked.readBytes())
        return "$DIR/$fileName"                                          // ← what goes in the DB
    }

    fun resolve(relativePath: String): PlatformFile =
        PlatformFile(FileKit.filesDir, relativePath)

    fun delete(relativePath: String?) {
        relativePath ?: return
        resolve(relativePath).delete()                                   // D-07: no orphan files
    }
}
```

> **Gotcha (relative path only):** iOS app container paths change between installs. Never store an absolute path or a `file://` URI — and never store the picker's temp URL, which is invalid after the process dies. Always resolve `FileKit.filesDir` fresh at read time.

### Pattern 5: `koinViewModel()` in `commonMain` + mapper keeps SQLDelight types out of the domain (VEH-*)

**What:** ViewModels are declared in `commonMain` and obtained with `org.koin.compose.viewmodel.koinViewModel()`. The repository returns `domain.Vehicle`, never the SQLDelight-generated `VehicleEntity`.

**When to use:** Every screen; the mapper boundary is non-negotiable for testability and for surviving schema regen.

**Example:**
```kotlin
// Source: Koin docs (insert-koin.io) — Koin Compose ViewModel for KMP
// di/AppModule.kt  (commonMain)
val appModule = module {
    single { DatabaseFactory(createDriver()) }
    single { get<DatabaseFactory>().database }
    single<VehicleRepository> { VehicleRepositoryImpl(get()) }
    viewModel { VehicleListViewModel(get()) }      // ctor-injected repository
}

// ui/vehicles/VehicleListViewModel.kt  (commonMain)
class VehicleListViewModel(private val repo: VehicleRepository) : ViewModel() {
    val uiState: StateFlow<VehicleListUiState> =
        repo.observeVehicles()
            .map { VehicleListUiState.Content(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleListUiState.Loading)
}

// ui/vehicles/VehicleListScreen.kt
@Composable
fun VehicleListRoute(vm: VehicleListViewModel = koinViewModel()) { /* … */ }
```

> Use `org.koin.compose.viewmodel.koinViewModel` (not `org.koin.androidx…`). It resolves the same `ViewModelStoreOwner` provided by Navigation Compose, so each nav entry gets its own scoped instance on both platforms.

### Pattern 6: Type-safe routes + shared bottom-tab shell (D-17, D-18, D-19)

**What:** Routes are `@Serializable` objects/classes; the shell is one `Scaffold` with a `NavigationBar` and a single `NavHost`, shared by both platforms.

**When to use:** Phase 1 establishes the shell; later phases add destinations, not new nav graphs.

**Example:**
```kotlin
// Source: Navigation Compose 2.9.0 docs (kotlinlang.org / JetBrains) — type-safe navigation
// navigation/Routes.kt  (commonMain)
@Serializable data object VehicleListRoute
@Serializable data object SettingsRoute          // D-17 placeholder tab
@Serializable data class VehicleEditRoute(val id: Long = 0L)
@Serializable data class VehicleDetailRoute(val id: Long)

// App.kt  (commonMain)
@Composable
fun App() {
    KoinApplication(application = { modules(appModule) }) {
        val navController = rememberNavController()
        Scaffold(bottomBar = { BottomTabs(navController) }) { padding ->
            NavHost(navController, startDestination = VehicleListRoute, modifier = Modifier.padding(padding)) {
                composable<VehicleListRoute> { VehicleListRoute() }
                composable<SettingsRoute>   { SettingsScreen() }
                composable<VehicleEditRoute> { back ->            // edit also serves "add"
                    val route = back.toRoute<VehicleEditRoute>()
                    VehicleEditRoute(vehicleId = route.id)
                }
                composable<VehicleDetailRoute> { back ->
                    VehicleDetailRoute(vehicleId = back.toRoute<VehicleDetailRoute>().id)
                }
            }
        }
    }
}
```

`VehicleEditRoute(id = 0L)` is the "add" case and `id > 0` the "edit" case — one destination satisfies D-08 (single form) without two screens. The FAB lives on the list screen (D-19) and calls `navController.navigate(VehicleEditRoute())`.

### Anti-Patterns to Avoid

- **Leaking SQLDelight generated types into `commonMain/ui`:** passing `VehicleEntity` to Composables couples the UI to the schema and breaks the moment a column is renamed. Always map to `domain.Vehicle`.
- **Storing an absolute file path or the picker's temp URI (D-05):** iOS container paths change per install; a stored absolute path becomes a broken image after an app update. Store the relative path, resolve at read time.
- **Putting migrations under `src/main/sqldelight/`:** KMP tasks scan `src/commonMain/sqldelight/`; the file is ignored and the CI check silently passes with no migration.
- **Trusting the `verifySqlDelightMigration` *aggregate* task to fail on a missing `.sqm`:** the aggregate is registered unconditionally, so a repo with no migrations still "passes". CI must run the per-source-set task (`verifyCommonMainCarRecordDatabaseMigration`) *and* assert the schema snapshot is unchanged — or run `generate…Schema` and `git diff --exit-code` the generated `1.db`.
- **Calling `Dispatchers.IO` inline in the repository:** makes the read/write lane untestable and re-introduces parallel writes. Inject the dispatcher (Pattern 3).
- **Inserting `journal_mode=wal` only in Android's `onConfigure`:** the iOS driver ignores it and the "WAL from first launch" criterion becomes platform-dependent. Put the pragma in the `.sq` preamble.
- **Hand-editing `1.db`:** the snapshot is a build output; regenerate it with `generateCommonMainCarRecordDatabaseSchema` and commit the result.

## Common Pitfalls

### Pitfall 1: Migration-verification task silently does nothing (DAT-04)
**What goes wrong:** CI is green, but a schema change with no `.sqm` file is never caught. The phase's headline success criterion ("schema changes blocked in CI without a `.sqm` migration") is not actually enforced.
**Why it happens:** `schemaOutputDirectory` was not configured, so SQLDelight never registers migration tasks; or CI runs the aggregate `verifySqlDelightMigration`, which is registered unconditionally and passes vacuously when there are no migrations.
**How to avoid:** Set `schemaOutputDirectory`, commit the generated `1.db`, and make CI run **both** `./gradlew verifyCommonMainCarRecordDatabaseMigration` **and** `./gradlew generateCommonMainCarRecordDatabaseSchema && git diff --exit-code` (the second catches "table changed but no migration authored"). Verify locally with `./gradlew tasks --all | grep -i migration` that the per-source-set task actually exists.
**Warning signs:** `./gradlew tasks --all` shows no `...Migration` task before you touch schema config; the number of migration tasks doesn't change after adding `schemaOutputDirectory`.

### Pitfall 2: Version-set misalignment force-upgrades the Compose stack (whole phase)
**What goes wrong:** Adding Koin 4.2.0 pulls `org.jetbrains.compose.runtime:runtime:1.10.2` onto a classpath otherwise pinned to CMP 1.8.2. Gradle "highest wins" silently promotes only *transitively-declared* Compose artifacts, leaving a mismatched mix (e.g. runtime 1.10.2 + foundation 1.8.2) that compiles but crashes at runtime with obscure `NoSuchMethodError`s.
**Why it happens:** Kotlin-library metadata pins are not "constraints" — they're requests, and the resolver honours the highest.
**How to avoid:** Use the *aligned-down* set (SQLDelight **2.2.1**, Koin **4.1.1**) from the Standard Stack table. Run `./gradlew :composeApp:dependencies --configuration commonMainImplementation` and confirm every `org.jetbrains.compose.*` resolves to **1.8.2**. Add a `resolutionStrategy` constraint (`force("org.jetbrains.compose.runtime:runtime:1.8.2")` per module) only as a belt-and-braces guard.
**Warning signs:** multiple distinct `org.jetbrains.compose.*` versions in `dependencies` output; a compose-runtime `1.1x` appearing at all.

### Pitfall 3: Absolute photo path / stale iOS container path (D-05, VEH-03, VEH-04)
**What goes wrong:** Photos show on first run, then become broken images after the next app install/update on iOS (and sometimes after an Android data restore).
**Why it happens:** The picked temp file's URL is only valid for the current process; iOS assigns a **new** app-container UUID on each install, so any stored absolute path is invalidated.
**How to avoid:** Persist into `FileKit.filesDir` and store **only the relative path** (`vehicles/<id>.jpg`); always build `PlatformFile(FileKit.filesDir, relativePath)` at read time. Never persist the picker's temp file directly.
**Warning signs:** any code writing `PlatformFile.path` (absolute) or a `file://` URI into the DB.

### Pitfall 4: Orphaned photo files on delete/replace (D-07)
**What goes wrong:** Deleting a vehicle leaves its JPEG on disk forever; replacing a photo leaves the old one. Storage grows without bound.
**Why it happens:** Delete only touches the DB row.
**How to avoid:** Route delete/replace through `PhotoStorage.delete(relativePath)` (Pattern 4), called in the same repository transaction flow as the row mutation. Add a test that asserts the file is gone after `deleteVehicle(id)`.
**Warning signs:** `PhotoStorage.delete` has no call sites; a repository `delete` method that only calls `vehicleQueries.delete(id)`.

### Pitfall 5: WAL not actually active on first launch (DAT-02)
**What goes wrong:** Verification step "WAL is active from first launch" fails on Android (or on iOS if the pragma was placed only in Android config).
**Why it happens:** `journal_mode` is *persistent per database file* once set, but the initial mode differs by driver: touchlab/SQLiter on iOS defaults to `JournalMode.WAL`, while Android's framework driver defaults to the platform default (rollback journal). A pragma placed in Android-only code never runs on iOS, and vice-versa.
**How to avoid:** Declare `PRAGMA journal_mode=wal;` in the `.sq` preamble so it is emitted by generated `Schema.create()`, for both platforms. Prove it: open the DB and query `PRAGMA journal_mode;` (expect `wal`) plus check for the `-wal` sidecar file exists after a write. On iOS, `DatabaseConfiguration.journalMode` default is already `JournalMode.WAL`.
**Warning signs:** no `-wal` file next to the `.db`; `PRAGMA journal_mode` returns `delete`/`truncate`.

### Pitfall 6: `generateAsync`/`QueryResult.AsyncValue` type mismatches (STATE.md open question)
**What goes wrong:** Turning on `generateAsync = true` changes every generated `Query` to return `QueryResult<AsyncValue<T>>`, forcing `SqlSchema<QueryResult.AsyncValue<Unit>>` and requiring `schema.synchronous()` at driver creation. This ripples through every repository call site.
**Why it happens:** Async generation is designed for drivers whose `create()` is suspend; synchronous drivers (`AndroidSqliteDriver`, `NativeSqliteDriver`) require the `.synchronous()` shim.
**How to avoid:** Set `generateAsync.set(false)` in Phase 1 (Pattern 1). Nothing in the phase's requirements needs async schema creation. Revisit only if a driver requiring it is adopted later.
**Warning signs:** compile errors like "expected `QueryResult<Unit>`, found `Unit`"; `SqlSchema<QueryResult.AsyncValue<Unit>>` appearing in generated code.

### Pitfall 7: `koinViewModel()` not found / wrong package (VEH-*)
**What goes wrong:** `koinViewModel()` resolves under `org.koin.androidx.…` and the code won't compile in `commonMain`, or `Unresolved reference: koinViewModel`.
**Why it happens:** The Android-specific Koin artifacts differ from the KMP ones; the Compose Multiplatform overload lives in `org.koin.compose.viewmodel`.
**How to avoid:** Depend on `koin-compose` + `koin-compose-viewmodel` and import `org.koin.compose.viewmodel.koinViewModel`. Ensure the ViewModel is registered with `viewModel { … }` in a module applied via `KoinApplication`.
**Warning signs:** an import starting with `org.koin.androidx`; `koinViewModel` used outside a `KoinApplication`/`KoinContext`.

### Pitfall 8: Build environment is not on this machine (whole phase — BLOCKING)
**What goes wrong:** The phase cannot build or run at all: Xcode 27 is newer than Kotlin 2.2.20 supports, and only JDK 25/26 are installed while Gradle 8.14 requires ≤ JDK 24. Android SDK has no `cmdline-tools` and only API 36.1/37 platforms.
**Why it happens:** The locked stack targets a 2024/2025 toolchain generation; this machine is on a 2026 one.
**How to avoid:** Treat as a **Wave 0 gate** (see Environment Availability). Either (a) user installs JDK 21 + Xcode 16.4 (approved changes), or (b) adopt the current wizard-default stack. Do **not** start feature work before the hello-world builds on both platforms.
**Warning signs:** `Unsupported class file major version`; Kotlin/Native "Xcode version is not supported"; Gradle "requires Java 17 to 24".

### Pitfall 9: KMP wizard emits a different stack than the pins (bootstrap)
**What goes wrong:** Following "bootstrap via kmp.jetbrains.com" yields Kotlin 2.4.10 / AGP 9.1.1 / CMP 1.11.1 / Koin 4.2.2 / nav 2.9.2 / minSdk 24 — not the locked set — and Kotlin 2.2.20 rejects `androidTarget`-less AGP 9 config.
**Why it happens:** The wizard always generates the *current* release stack.
**How to avoid:** Generate with the wizard, then **rewrite `gradle/libs.versions.toml`** to the aligned-down set and restore the `androidTarget { }` DSL (Kotlin 2.2.20 path). Treat the wizard as scaffolding, not as the source of versions.
**Warning signs:** generated catalog versions differ from Standard Stack; `androidTarget` missing from the KMP block; `com.android.kotlin.multiplatform.library` present.

### Pitfall 10: `rememberFilePickerLauncher` on iOS needs a persisted/`@Composable` host
**What goes wrong:** The picker never presents on iOS, or presenting it throws because no view controller is available.
**Why it happens:** FileKit's Compose API must be obtained inside composition (`rememberFilePickerLauncher`) so it can attach to the host `UIViewController`; using `FileKit.openFilePicker()` from a non-UI context or too early fails.
**How to avoid:** Use `rememberFilePickerLauncher(type = FileKitType.Image) { file -> … }` in the screen composable (returns `PlatformFile?`). Ensure the iOS entry point wraps the shared `App()` in `ComposeUIViewController` and that FileKit's iOS `PHPickerViewController` presentation is allowed (it handles this internally when launched from composition).
**Warning signs:** picker no-ops on iOS only; crash mentioning "no view controller".

## Code Examples

Verified patterns from official sources. The project-specific code (Patterns 1–6) is above; these are the platform seams and CI.

### `expect/actual` driver factory (from SQLDelight multiplatform docs)

```kotlin
// commonMain — data/db/DriverFactory.kt
package com.carrecord.db
import app.cash.sqldelight.db.SqlDriver
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain — data/db/DriverFactory.android.kt
package com.carrecord.db
import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(CarRecordDatabase.Schema, context, "carrecord.db")
    // Note: no need to force WAL here if `PRAGMA journal_mode=wal;` is in the .sq preamble.
}

// iosMain — data/db/DriverFactory.ios.kt
package com.carrecord.db
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(CarRecordDatabase.Schema, "carrecord.db")
    // touchlab SQLiter default for this driver is already JournalMode.WAL.
}
```

### Compose Resources strings (D-11) — the `commonMain`-only mechanism

```xml
<!-- composeApp/src/commonMain/composeResources/values/strings.xml -->
<resources>
    <string name="app_name">CarRecord</string>
    <string name="vehicles">Vehicles</string>
    <string name="settings">Settings</string>
    <string name="add_vehicle">Add vehicle</string>
    <string name="empty_vehicles">No vehicles yet. Add your first one.</string>
    <string name="delete_confirm">Delete %1$s and all its records?</string>
    <string name="tab_settings">Settings</string>
    <string name="app_version">App version</string>
</resources>
```

```kotlin
// Usage
import carrecord.composeapp.generated.resources.Res
import carrecord.composeapp.generated.resources.add_vehicle
import org.jetbrains.compose.resources.stringResource

Text(text = stringResource(Res.string.add_vehicle))
```

> The generated `Res` package is derived from the module's `namespace`/application id — confirm it after the first build (IntelliJ: regenerate after adding the `compose.components.resources` dependency).

### GitHub Actions — migration verification + build (DAT-04)

```yaml
# .github/workflows/ci.yml
name: CI
on: [push, pull_request]
jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21' }   # 21 — see Environment Availability
      - uses: gradle/actions/setup-gradle@v4
      - name: Verify SQLDelight migrations (per-source-set task)
        run: ./gradlew verifyCommonMainCarRecordDatabaseMigration
      - name: Fail if schema changed without a migration
        run: |
          ./gradlew generateCommonMainCarRecordDatabaseSchema
          git diff --exit-code -- composeApp/src/commonMain/sqldelight/databases
      - name: Build shared module + Android
        run: ./gradlew :composeApp:assembleDebug
```

> `verifySqlDelightMigration` (aggregate) is *also* wired to `check`, but because it is registered unconditionally it can pass vacuously; the explicit per-source-set task plus the `git diff --exit-code` on the schema snapshot are what actually enforce the requirement.

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `compose.material` / Material 2 in CMP | **Material 3** (`compose.material3`, `WindowInsets`, `NavigationBar`) | CMP 1.x current | Phase 1 uses M3 components (`NavigationBar`, `Scaffold`, `FloatingActionButton`, `Card`, `OutlinedTextField`). |
| SQLDelight `verifySqlDelightMigration` only | Explicit `schemaOutputDirectory` + per-source-set `verify<Task><Db>Migration` + `git diff` guard | 2.x | Migration enforcement must be wired deliberately; the aggregate task alone is not sufficient (Pitfall 1). |
| SQLDelight `2.3.x` scale | `2.2.x` is the Kotlin 2.2.20-aligned line | per registry metadata (2026-10) | Locked `2.3.2` declares `kotlin-stdlib 2.3.10` and is not aligned with Kotlin 2.2.20. |
| Koin `3.x` ViewModel scoping for Android | Koin **4.1.1** `koinViewModel()` in `commonMain` (`org.koin.compose.viewmodel`) | Koin 4.x | One DI API for both platforms; `viewModel { }` registration. |
| `Navigation Compose` (androidx-only) | **JetBrains** `org.jetbrains.androidx.navigation:navigation-compose` with type-safe `@Serializable` routes | 2.8+/2.9 | No string routes; `toRoute<T>()` typed args. |
| `Instant`/absolute times for ordering | Order by autoincrement `id` descending | — | Avoids adding a date library in Phase 1 (OQ-4). |
| `java.util.Date` / platform date | (deferred) `kotlinx-datetime` | 0.7.x | Not needed in Phase 1; re-check 0.7.x compatibility when adopted in Phase 2. |

**Deprecated/outdated:**
- `Peekaboo` (`io.github.onseok:peekaboo-image-picker`): last release 0.5.2 (Apr 2024) — abandoned, do not use.
- `com.darkrockstudios:mpfilepicker`: last release 3.1.0 (Dec 2023) — abandoned, do not use.
- `kotlinx.serialization` versioned with the Kotlin version: **false**; it is an independent artifact (use `1.7.3` per nav 2.9.0). STACK.md's `serialization = "2.2.20"` was wrong.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The user's preferred resolution is to keep the locked pins and install JDK 21 + Xcode 16.4, rather than adopt the current wizard stack. | Summary / Environment Availability | If wrong, the whole version table (SQLDelight 2.2.1, Koin 4.1.1, AGP 8.11.1, CMP 1.8.2) is replaced by the current stack (Kotlin 2.4.x / AGP 9.x / CMP 1.12.x / SQLDelight 2.4.1 / Koin 4.2.2). **This is the single decision the planner must surface first.** |
| A2 | `generateAsync = false` is acceptable for Phase 1 (no driver requires async schema creation). | Pitfall 6 / Pattern 1 | Repository code would need `QueryResult.AsyncValue` unwrapping and `schema.synchronous()`; a mid-phase refactor. |
| A3 | FileKit 0.11.0 is the intended picker library (CONTEXT.md left "photo-picker library wiring details" to agent discretion). | Standard Stack / Pattern 4 | If the user insists on zero third-party deps, ~150–250 lines of `expect/actual` picker code must be planned instead (Alternatives table). |
| A4 | Compose Resources (`composeResources/values/strings.xml`) is the sanctioned mechanism for D-11 centralised strings. | Standard Stack / Code Examples | Alternative is `expect/actual` string holders — more code, no better outcome. |
| A5 | Photos should be persisted to `FileKit.filesDir/vehicles/<id>.jpg` with relative-path-only DB storage (D-05 leaves detail to discretion). | Pattern 4 | If the user prefers a different layout (e.g. content-hash filenames), `PhotoStorage` changes but the DB contract (relative path string) does not. |
| A6 | A single `composeApp` KMP module is the bootstrap layout (wizard default) rather than separate `shared` + `androidApp` + `iosApp`. | Architecture Patterns | Module graph changes; plan's file paths shift. Low impact — layout is mechanical. |
| A7 | CI runs on GitHub Actions with the repo's existing workflow permissions. | Code Examples | If CI is not GitHub Actions, the migration-verification step is transplanted to the actual CI runner. |

**If this table were empty, no user confirmation would be needed.** It is not empty — A1 in particular is a locked-decision-level fork that should be confirmed at the Wave 0 checkpoint before plans are executed.

## Open Questions (RESOLVED)

> **All resolved during planning.** Every question below is closed and the resolution is encoded in the phase plans, so nothing blocks execution. The resolutions are:
> OQ-1/OQ-2 → plan **01-01 Task 1** (Wave 0 `checkpoint:decision` gate that settles the version set and the environment gap with the user). OQ-3 → keep the standalone JetBrains `navigation-compose 2.9.0`. OQ-4 → **omit** `kotlinx-datetime` from Phase 1. OQ-5 → photo filename `vehicles/<vehicleId>-<epochMillis>.jpg` (plan **01-08**). OQ-6 → a single `NavHost` with typed routes (plan **01-04**).

1. **Which version set ships — locked pins or current stack? (HEADLINE) — RESOLVED**
   - Resolution: **deferred to the Wave 0 gate, not pre-committed.** Plan **01-01 Task 1** is a `checkpoint:decision` that presents three options (option-a aligned-down locked pins + approved JDK 21 / Xcode 16.4 installs; option-b adopt the installed KMP-wizard generation wholesale; option-c defer/block). The phase does not assume an answer: every remaining plan is written against the option-a alias set but Task 2/3 explicitly branch "per option-a or option-b as selected", and Task 1's action records the outcome into `01-01-SUMMARY.md`. Generation mixing is forbidden either way.
   - What we know: the locked set (Kotlin 2.2.20 / CMP 1.8.2 / SQLDelight 2.3.2 / Koin 4.2.0) is internally inconsistent and needs JDK 21 + Xcode 16.4 to build; the current wizard set builds on what's installed. Both are internally consistent *as sets*; only the pins are broken as locked.
   - Recommendation (default pre-selected at the gate): **aligned-down locked pins** (SQLDelight 2.2.1 / Koin 4.1.1) *if* the user approves JDK 21 + Xcode 16.4 installs; otherwise adopt the current wizard stack wholesale. Do not mix generations.

2. **How is the environment gap closed? — RESOLVED**
   - Resolution: **closed at the same Wave 0 gate** (plan **01-01 Task 1**), which is the only human decision point in the phase and blocks all file authoring until it resumes. The `user_setup` block in 01-01 frontmatter enumerates the human-only steps (JDK 21 via `JAVA_HOME`, Xcode 16.4 via `xcode-select`, Android SDK via `local.properties -> sdk.dir`), because global working rules forbid the agent installing them. If option-b is chosen the gap is avoided rather than closed. Recorded in `01-01-SUMMARY.md`.
   - What we know: Xcode 27 (needs ≤16.4 for Kotlin 2.2.20), JDK 25/26 (needs ≤24 for Gradle 8.14), no `cmdline-tools`, no `ANDROID_HOME`/`local.properties`.

3. **Navigation library: JetBrains `navigation-compose` vs CMP-bundled navigation? — RESOLVED**
   - Resolution: **keep the standalone JetBrains `navigation-compose 2.9.0`** (alias `libs.navigation.compose`, declared in plan **01-01** Task 2 and consumed by plan **01-04**). Its published POM depends exactly on CMP 1.8.2, so under the default version set it produces no partial upgrade. Not reconsidered within Phase 1.
   - What we know: locked choice is JetBrains `navigation-compose 2.9.0`, whose POM depends exactly on CMP 1.8.2 — a clean match. CMP 1.8 also ships its own navigation entry points.
   - Revisit only if a nav bug forces it.

4. **Should `kotlinx-datetime` be added in Phase 1? — RESOLVED**
   - Resolution: **omit** it. No Phase 1 plan declares a `kotlinx-datetime` dependency; the version catalog in plan **01-01** Task 2 contains no such alias. Phase 1 ordering uses the autoincrement `id` (`selectAll ... ORDER BY id DESC`, plan 01-03) and `year` is digits-only text (D-13), so no calendar math exists. Add it in Phase 2 when service dates are actually modelled.
   - What we know: Phase 1 needs no calendar math; STATE.md flags a 0.7.x compatibility concern.

5. **Photo filename strategy — `<id>.jpg` vs content hash? — RESOLVED**
   - Resolution: **`vehicles/<vehicleId>-<epochMillis>.jpg`** with old-file deletion on replace. Encoded in plan **01-08** Task 2 (`compute the filename as "<vehicleId>-<epochMillis>.jpg" ... the epochMillis suffix is what defeats Coil's disk cache staleness after a replace; do not reuse a filename`) and in 01-08's artifact list. Supersede/remove/abandon each delete the superseded file, so no orphan cleanup pass is needed.
   - What we know: D-05 says relative path only; detail is agent discretion.

6. **Does the bottom tab-bar need to persist selection / handle deep links? — RESOLVED**
   - Resolution: **a single `NavHost` with typed routes** (plan **01-04** Task 1, RESEARCH Pattern 6). `BottomTabs` tracks the current back-stack destination so tab selection stays in sync both ways. Plan **01-09** Task 3 further scopes the bar to the two tab roots (`VehicleListRoute` / `SettingsRoute`) and hides it on the pushed sub-screens. Deep-link wiring is additive and requires no restructuring, so it is left out of Phase 1.
   - What we know: D-17 ships Vehicles (functional) + Settings (placeholder) with a reserved "Service history (coming soon)" slot on Detail (D-18).

## Environment Availability

Audited on the target machine (macOS 27.0.1, `darwin`) on 2026-10-18.

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK ≤ 24 | Gradle 8.14.6 runtime (locked stack) | ✗ | JDK 25.0.4 + 26.0.1 only | Install JDK 21 (Temurin/Homebrew, requires approval) **or** move to Gradle ≥ 9.1.0 + JDK 25+ (current stack) |
| Xcode ≤ 16.4 | Kotlin 2.2.20 Kotlin/Native toolchain | ✗ | Xcode 27.0 (Build 27A266a) | Install Xcode 16.4 side-by-side (requires approval + `xcode-select`) **or** move to Kotlin ≥ 2.4.x (current stack) |
| Gradle wrapper 8.14.6 | Build system | ✓ (wrapper) | downloadable; no standalone `gradle` on PATH | `./gradlew wrapper --gradle-version 8.14.6` (uses installed JDK — see JDK row) |
| Xcode command-line tools | iOS build/signing | ✓ | present (Xcode 27 installed) | — |
| Android SDK platform (API 36) | Android build (`compileSdk 36`) | ✓ | `android-36.1`, `android-37.0` present | `compileSdk 36` maps to installed platform; no 36.0 exactly — verify Gradle resolves it |
| Android SDK `cmdline-tools` (`sdkmanager`) | Installing missing platforms/build-tools | ✗ | not installed | Install via Android Studio or `cmdline-tools` (requires approval); or rely on Android Studio's bundled SDK |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` / `local.properties` | Gradle Android config | ✗ | unset / absent | Create `local.properties` with `sdk.dir=…` (file creation — requires approval) |
| `adb` on PATH | Install/run on device/emulator | ✗ | not on PATH | Use `$ANDROID_HOME/platform-tools/adb` once SDK path known |
| iOS simulator runtime | Run on iOS | ✓ | iOS 27.0 only | Newer than Kotlin 2.2.20 supports (see Xcode row) |
| Node.js / npm | `gsd-tools` (tooling only, not app) | (not probed for app) | — | — |
| GitHub Actions runner | Migration CI | ✓ (assumed) | `ubuntu-latest` + Temurin 21 | Any CI with Java 21 |

**Missing dependencies with no fallback (BLOCKING Phase 1 execution on the locked stack):**
- **JDK ≤ 24** — Gradle 8.14.6 cannot run on JDK 25/26. No in-repo workaround.
- **Xcode ≤ 16.4** — Kotlin/Native 2.2.20 rejects Xcode 27. No in-repo workaround.

**Missing dependencies with fallback:**
- **Android `cmdline-tools` / `ANDROID_HOME` / `local.properties`** — resolvable by pointing Gradle at the Android Studio SDK (`local.properties`) or installing `cmdline-tools`.
- **`adb`** — reachable via the SDK's `platform-tools` once the SDK path is set.

> **Interpretation for the planner:** the two blocking rows are not fixable inside the repository — they require either user-approved installs or a version-set change. This is why Step 1 of the plan must be a **Wave 0 compatibility spike + checkpoint**, not a code task.

## Security Domain

`security_enforcement` is enabled (`.planning/config.json`), ASVS level 1. Phase 1 is a local-first app with no network, no auth, and no PII beyond a free-text plate the user types; the relevant surface is **local data at rest, file handling, and input validation**.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | No | No accounts in Phase 1. |
| V3 Session Management | No | No sessions. |
| V4 Access Control | No (OS-level only) | Photos live in app-private storage (`FileKit.filesDir` → Android `filesDir`, iOS Documents-in-container). No cross-app sharing; no `FileProvider`/shared-URI exposure. |
| V5 Input Validation | Yes | Required-field validation (`name` non-blank, D-09); year constrained to digits (D-13); type constrained to the fixed enum list (D-10); all values passed to SQLDelight as **typed bind parameters** (never string-concatenated SQL). |
| V6 Cryptography | No | Phase 1 stores no secrets and encrypts nothing. Do **not** hand-roll any crypto; if at-rest encryption is later required, use platform SQLCipher rather than a custom layer. |
| V7 Error Handling & Logging | Yes (light) | Do not log photo contents, plate values, or file paths at info level; surface user-facing errors as generic strings. |
| V8 Data Protection | Yes (light) | Photo bytes are written to app-private storage only; the DB stores a relative path, never a BLOB or external-content URI (D-05). Delete removes both row and file (D-07) — no orphaned personal data. |
| V12 File Upload / Resource | No | No uploads; local picker only. |
| V14 Configuration | Yes (light) | No secrets in the repo; no `local.properties` committed; `gradle.properties` must not carry credentials (CI uses ephemeral runners). |

### Known Threat Patterns for {KMP + local SQLite + file storage}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| SQL injection via string-built queries | Tampering | SQLDelight compile-time SQL with typed bind params (`?`), never concatenation. |
| External-content `content://` URI persisted and later leaked/invalidated | Information Disclosure | Copy bytes into app-private storage immediately; store a relative path only (D-05). |
| Photo temp file left in a world-readable cache dir | Information Disclosure | Persist to `FileKit.filesDir` (app-private), not shared cache; delete the temp source after copy. |
| Orphaned photo files after delete/replace | (Data lifecycle) | Delete the file in the same flow as the row delete (D-07); assert in tests. |
| Absolute-path storage breaking after iOS container move | Tampering / DoS (broken state) | Store relative paths; resolve `FileKit.filesDir` at read time. |
| Oversized image decode causing OOM | DoS | Coil 3.3.0 with `maxBitmapSize` + FileKit `compressImage()` before persist. |
| Insecure Android backup exposing the DB/photos | Information Disclosure | Consider `android:allowBackup="false"` (or a backup rules file excluding the DB + photo dir) — flag as a plan decision. |
| BLL (Bypassing the library) — writing SQLite files directly | Tampering | All DB access through the single repository + driver (Pattern 3). |

> **Note:** No secrets, tokens, or network endpoints exist in Phase 1, so the OWASP-LLM / prompt-injection categories are not applicable. The security work here is deliberately small and is exhausted by: typed bind params, app-private file storage, relative-path discipline, input validation, and an explicit backup decision.

## Sources

### Primary (HIGH confidence)
- **Context7 / official docs** — SQLDelight: multiplatform setup, migrations, `schemaOutputDirectory`, `generateAsync`, driver factories (sqldelight.github.io).
- **Context7 / official docs** — Koin: `koin-compose-viewmodel`, `koinViewModel()` for KMP (insert-koin.io).
- **Context7 / official docs** — FileKit: `rememberFilePickerLauncher`, `FileKitType.Image`, `FileKit.filesDir`, `PlatformFile` write/delete, `filekit-coil`, `compressImage` (filekit.mintlify.app).
- **Context7 / official docs** — Navigation Compose (JetBrains): type-safe routes, `composable<T>`, `toRoute<T>()` (kotlinlang.org / JetBrains).
- **Context7 / official docs** — Compose Multiplatform: resource (`composeResources`) handling, `Res.string.*` (JetBrains docs).
- **JetBrains Kotlin ↔ Xcode / Gradle / AGP compatibility table** — Kotlin 2.2.20 supports Xcode ≤ 16.4, AGP ≤ 8.11.1, Gradle 7.6.3–8.14 (kotlinlang.org/docs/multiplatform-compatibility-guide or equivalent).
- **Maven Central `maven-metadata.xml` + `.pom` + Gradle `.module`** — direct artifact verification for every dependency (see Version verification table).
- **touchlab SQLiter source (`co.touchlab.sqliter.DatabaseConfiguration`)** — `journalMode` default `JournalMode.WAL` on the native driver.
- **Local machine probes** — `xcodebuild -version` (Xcode 27.0/27A266a), `java -version` (JDK 25.0.4, 26.0.1), Android SDK directory listing, `command -v` checks.

### Secondary (MEDIUM confidence)
- GitHub releases API / tag `libs.versions.toml` for `vinceglb/FileKit` 0.11.0 (build-time Kotlin/CMP/Coil pins).
- `Kotlin/kmp-app-template` generated catalog (current wizard-default stack for the fallback path).
- WebSearch results cross-checked against the above primary sources for pitfall framing (none used as sole basis for a claim).

### Tertiary (LOW confidence)
- Community discussions on Koin-vs-CMP version drift and SQLDelight aggregate-task vacuity — used only to *frame* pitfalls, each independently confirmed against registry metadata or official docs above.

## Metadata

**Confidence breakdown:**
- **Standard stack:** MEDIUM — every artifact/version was verified against Maven Central metadata, but the *correct* pin set depends on an unresolved user decision (A1/OQ-1). The aligned-down recommendation is HIGH confidence; the locked pins are known-broken.
- **Architecture:** HIGH — patterns follow official SQLDelight/Koin/Navigation/FileKit documentation and are version-matched to CMP 1.8.2.
- **Pitfalls:** HIGH — the migration-task and WAL behaviours were traced to their actual mechanisms (aggregate task registration; `journalMode` defaults), not inferred.
- **Environment:** HIGH — verified directly on the machine; the two blocking rows are unambiguous.
- **Security:** MEDIUM — Phase 1's surface is small; the backup decision is flagged rather than resolved.

**Research date:** 2026-10-18
**Valid until:** ~2026-11-17 (30 days) for architecture/patterns; **re-verify within 7 days** for the version table and environment rows, which are the volatile parts of the locked stack.
