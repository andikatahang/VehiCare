# Phase 1: Foundation & Vehicle Management - Pattern Map

**Mapped:** 2026-10-18
**Files analyzed:** 46 planned files (39 source/config + 7 generated/transient)
**Analogs found:** 0 / 46 — **GREENFIELD REPOSITORY**

---

## Greenfield Notice (read first)

The repository at `/Users/andika/Belajar-Coding/VibeCoding#1` contains **only** `.git/` and `.planning/`. Verified by directory listing on 2026-10-18. There is:

- **No source code** — no `composeApp/`, no `build.gradle.kts`, no `.kt`/`.swift`/`.sq`/`.xml` files anywhere in the tree.
- **No first-party analogs.** This is stated explicitly in `01-CONTEXT.md` §code_context: *"None — greenfield. Repository contains no source code yet; Phase 1 scaffolds the entire project."*
- **No `AGENTS.md`** in the working directory (only the global `~/.config/opencode/AGENTS.md` working rules apply — no state-changing actions without approval; see §Governance).
- **No project skills** — neither `.claude/skills/` nor `.agents/skills/` exists.

**Therefore: no code excerpts from existing files are reproduced in this document, because none exist.** Every entry below is marked `NONE (greenfield) — convention sourced from …` and cites one of three authoritative sources:

| Source tag | File | Role in this document |
|------------|------|-----------------------|
| `RES §<section>` | `.planning/phases/01-foundation-vehicle-management/01-RESEARCH.md` | Version-accurate, official-docs-derived conventions and code sketches. **Primary pattern source.** |
| `UISPEC §<section>` | `.planning/phases/01-foundation-vehicle-management/01-UI-SPEC.md` | Binding visual/interaction contract (approved) for all `commonMain` UI files. |
| `D-NN` | `01-CONTEXT.md` §Implementation Decisions | Locked user decisions. |
| `STATE §<section>` | `.planning/STATE.md` | Architecture Constraints + Key Decisions. |
| `ROADMAP §<section>` | `.planning/ROADMAP.md` | Phase goal, success criteria, Architecture Notes. |

The code blocks in §Pattern Assignments are **RESEARCH.md-authored sketches** (verified against official SQLDelight / Koin / Navigation Compose / FileKit / CMP documentation — see RES §Sources), reproduced with their source line ranges so the planner can copy them into plan actions. They are **not** excerpts from existing project files.

> **Planner note:** Because there are no analogs, the planner's "copy the pattern from X" actions must instead say "implement per RES §Pattern N (lines …)" and "satisfy UISPEC §<section>". The value of this document is the *inventory + routing table*, not code archaeology.

---

## File Classification

### Group A — Build & configuration (repo root)

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `gradle/libs.versions.toml` | config | transform (build-time) | NONE (greenfield) — RES §Standard Stack (lines 156–197) | none |
| `gradle/wrapper/gradle-wrapper.properties` | config | transform (build-time) | NONE (greenfield) — RES §Standard Stack (line 201), §Environment Availability | none |
| `settings.gradle.kts` | config | transform (build-time) | NONE (greenfield) — RES §Recommended Project Structure (line 355) | none |
| `build.gradle.kts` (root) | config | transform (build-time) | NONE (greenfield) — RES §Standard Stack §plugins | none |
| `gradle.properties` | config | transform (build-time) | NONE (greenfield) — RES §Security Domain V14 (no credentials) | none |
| `composeApp/build.gradle.kts` | config | transform (build-time) | NONE (greenfield) — RES §Pattern 1 (lines 366–380) | none |
| `local.properties` | config (local, **not committed**) | transform (build-time) | NONE (greenfield) — RES §Environment Availability | none |
| `.github/workflows/ci.yml` | config (CI) | batch | NONE (greenfield) — RES §Code Examples (lines 690–710) | none |

### Group B — Shared-module entry & platform hosts

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `composeApp/src/commonMain/kotlin/com/carrecord/App.kt` | component (root composable) | request-response | NONE (greenfield) — RES §Pattern 6 (lines 521–549); UISPEC §Screens | none |
| `composeApp/src/androidMain/kotlin/com/carrecord/MainActivity.kt` *(or `androidApp/…` wizard variant)* | platform-actual (UI host) | request-response | NONE (greenfield) — RES §Architecture Patterns (system diagram, lines 262–263) | none |
| `composeApp/src/iosMain/kotlin/com/carrecord/MainViewController.kt` | platform-actual (UI host) | request-response | NONE (greenfield) — RES §Architecture Patterns; §Anti-Patterns (line 622) | none |
| `iosApp/iosApp/iOSApp.swift` + `ContentView.swift` | platform-actual (Swift host) | request-response | NONE (greenfield) — RES §Recommended Project Structure (line 352) | none |
| `composeApp/src/androidMain/AndroidManifest.xml` | config | transform (build-time) | NONE (greenfield) — RES §Security Domain (line 833, `allowBackup` decision) | none |

### Group C — DI

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `composeApp/src/commonMain/kotlin/com/carrecord/di/AppModule.kt` | provider (DI module) | config/wiring | NONE (greenfield) — RES §Pattern 5 (lines 489–498) | none |
| `composeApp/src/androidMain/kotlin/com/carrecord/di/PlatformModule.android.kt` | provider (DI module, platform) | config/wiring | NONE (greenfield) — RES §Architectural Responsibility Map (line 107) | none |

### Group D — Data layer

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `composeApp/src/commonMain/sqldelight/com/carrecord/db/Vehicle.sq` | migration/schema | CRUD | NONE (greenfield) — RES §Pattern 2 (lines 391–412) | none |
| `composeApp/src/commonMain/sqldelight/databases/1.db` | DB (generated snapshot, committed) | CRUD | NONE (greenfield) — RES §Pattern 1 (line 382) | none |
| `composeApp/src/commonMain/sqldelight/com/carrecord/db/migrations/` | migration (dir, **empty Phase 1**) | CRUD | NONE (greenfield) — RES §Pattern 1; §Recommended Project Structure (line 347) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/data/db/DriverFactory.kt` | platform-expect (DB) | request-response | NONE (greenfield) — RES §Code Examples (lines 632–637) | none |
| `composeApp/src/androidMain/kotlin/com/carrecord/data/db/DriverFactory.android.kt` | platform-actual (DB) | request-response | NONE (greenfield) — RES §Code Examples (lines 639–648) | none |
| `composeApp/src/iosMain/kotlin/com/carrecord/data/db/DriverFactory.ios.kt` | platform-actual (DB) | request-response | NONE (greenfield) — RES §Code Examples (lines 650–658) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/data/db/DatabaseFactory.kt` | service (DB factory) | config/wiring | NONE (greenfield) — RES §Recommended Project Structure (line 329) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/data/VehicleEntityMapper.kt` | utility (mapper) | transform | NONE (greenfield) — STATE §Architecture Constraints; RES §Anti-Patterns (line 555) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/data/VehicleRepository.kt` | repository (interface) | CRUD + streaming | NONE (greenfield) — RES §Pattern 3 (lines 425–446) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/data/VehicleRepositoryImpl.kt` | repository (impl) | CRUD + streaming | NONE (greenfield) — RES §Pattern 3 (lines 423–447) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/domain/Vehicle.kt` | model (domain) | transform | NONE (greenfield) — STATE §Architecture Constraints; RES §Anti-Patterns (line 555) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/domain/VehicleType.kt` | model (domain enum) | transform | NONE (greenfield) — D-10; UISPEC §C-5 | none |

### Group E — Utilities & resources

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `composeApp/src/commonMain/kotlin/com/carrecord/util/PhotoStorage.kt` | utility | file-I/O | NONE (greenfield) — RES §Pattern 4 (lines 458–478) | none |
| `composeApp/src/commonMain/composeResources/values/strings.xml` | config (i18n resource) | transform | NONE (greenfield) — D-11; UISPEC §Copywriting Contract (lines 259–342) | none |
| `composeApp/src/commonMain/composeResources/drawable/ic_vehicle_car.xml` | resource (vector drawable) | transform | NONE (greenfield) — UISPEC §Icon Contract (lines 42–52), §C-7 | none |
| `composeApp/src/commonMain/composeResources/drawable/ic_vehicle_motorcycle.xml` | resource (vector drawable) | transform | NONE (greenfield) — UISPEC §Icon Contract, §C-7 | none |
| `composeApp/src/commonMain/composeResources/drawable/ic_vehicle_truck.xml` | resource (vector drawable) | transform | NONE (greenfield) — UISPEC §Icon Contract, §C-7 | none |

### Group F — UI: theme & navigation

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `composeApp/src/commonMain/kotlin/com/carrecord/ui/theme/Color.kt` | utility (theme token) | transform | NONE (greenfield) — UISPEC §Platform & Theme Constraints (lines 65–83) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/ui/theme/Theme.kt` | component (theme) | request-response | NONE (greenfield) — UISPEC §Platform & Theme Constraints (light-only, no dynamic color) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/ui/theme/Type.kt` | utility (theme token) | transform | NONE (greenfield) — UISPEC §Typography (lines 129–143) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/ui/navigation/Routes.kt` | route | config/wiring | NONE (greenfield) — RES §Pattern 6 (lines 524–527) | none |
| `composeApp/src/commonMain/kotlin/com/carrecord/ui/navigation/BottomTabs.kt` | component | request-response | NONE (greenfield) — D-17; UISPEC §Screens (line 201) | none |

### Group G — UI: vehicle screens & components

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `…/ui/vehicles/VehicleListScreen.kt` | component (screen) | request-response | NONE (greenfield) — VEH-05; UISPEC §Screens (line 197), §States (lines 228–231) | none |
| `…/ui/vehicles/VehicleListViewModel.kt` | service (ViewModel) | streaming | NONE (greenfield) — RES §Pattern 5 (lines 500–504) | none |
| `…/ui/vehicles/VehicleEditScreen.kt` | component (screen) | request-response | NONE (greenfield) — VEH-01/03; UISPEC §Screens (line 199), §Copywriting (line 211) | none |
| `…/ui/vehicles/VehicleEditViewModel.kt` | service (ViewModel) | request-response | NONE (greenfield) — RES §Pattern 5 (lines 500–504) | none |
| `…/ui/vehicles/VehicleDetailScreen.kt` | component (screen) | request-response | NONE (greenfield) — VEH-04; UISPEC §Screens (line 198), §States (lines 232–233) | none |
| `…/ui/vehicles/VehicleDetailViewModel.kt` | service (ViewModel) | streaming | NONE (greenfield) — RES §Pattern 5 | none |
| `…/ui/vehicles/components/VehicleCard.kt` | component | request-response | NONE (greenfield) — D-14; UISPEC §VehicleCard layout (lines 203–207) | none |
| `…/ui/vehicles/components/VehicleTypeCircle.kt` | component | request-response | NONE (greenfield) — D-14; UISPEC §Component Dimensions (lines 110–112) | none |
| `…/ui/vehicles/components/LicensePlateChip.kt` | component | request-response | NONE (greenfield) — UISPEC §Component Dimensions (line 121) | none |
| `…/ui/vehicles/components/EmptyState.kt` | component | request-response | NONE (greenfield) — D-15; UISPEC §States (line 229) | none |
| `…/ui/vehicles/components/FieldRow.kt` | component | request-response | NONE (greenfield) — UISPEC §Detail field rows (line 209) | none |
| `…/ui/vehicles/components/ComingSoonSlot.kt` | component | request-response | NONE (greenfield) — D-18; UISPEC §Detail field rows | none |
| `…/ui/vehicles/components/PhotoField.kt` | component | file-I/O + request-response | NONE (greenfield) — D-04/D-06; UISPEC §Screens (line 218); RES §Pitfall 10 | none |
| `…/ui/vehicles/components/ConfirmDeleteDialog.kt` | component | request-response | NONE (greenfield) — D-18; UISPEC §Confirm dialog (line 249) | none |

### Group H — UI: settings

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `…/ui/settings/SettingsScreen.kt` | component (screen) | request-response | NONE (greenfield) — D-17; UISPEC §Screens (line 200) | none |

### Group I — Tests

| Intended Path | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| `composeApp/src/commonTest/kotlin/…/VehicleRepositoryTest.kt` | test | CRUD | NONE (greenfield) — VEH-04; RES §Pattern 3, §Pitfall 4 | none |
| `composeApp/src/commonTest/kotlin/…/VehicleEntityMapperTest.kt` | test | transform | NONE (greenfield) — STATE §Architecture Constraints | none |
| `composeApp/src/commonTest/kotlin/…/VehicleFormValidationTest.kt` | test | transform | NONE (greenfield) — D-09, D-13; UISPEC §States (line 235) | none |
| `composeApp/src/androidInstrumentedTest/…/WalModeTest.kt` | test | request-response | NONE (greenfield) — DAT-03; RES §Pitfall 5 | none |
| `composeApp/src/iosTest/…/WalModeTest.kt` | test | request-response | NONE (greenfield) — DAT-03; RES §Pitfall 5 | none |

### Group J — Wave 0 (transient, not shipped)

| Item | Role | Data Flow | Closest Analog | Match |
|---|---|---|---|---|
| Wave-0 hello-world spike build (both platforms) | config / test | transform | NONE (greenfield) — RES §Environment Availability (lines 777–803); §Pitfall 8 | none |

---

## Pattern Assignments

Every assignment below carries the explicit marker **`ANALOG: NONE (greenfield)`** and names the governing convention. Code is reproduced from RESEARCH.md with its authoritative line range.

### Group A — Build & configuration

**ANALOG: NONE (greenfield) — convention sourced from RES §Standard Stack**

#### `gradle/libs.versions.toml`

**Governing convention:** RES §Standard Stack, "Installation (aligned-down set)" lines 156–197. **Two locked versions are flagged incompatible and must be resolved at the Wave 0 checkpoint before this file is authored** (see §Governance Conflict 1):

- `sqldelight = "2.2.1"` (replaces STATE-locked 2.3.2)
- `koin = "4.1.1"` (replaces STATE-locked 4.2.0)

```toml
[versions]
kotlin = "2.2.20"
agp = "8.11.1"
compose-multiplatform = "1.8.2"
sqldelight = "2.2.1"          # ⚠️ RES replacement for locked 2.3.2
koin = "4.1.1"                # ⚠️ RES replacement for locked 4.2.0
navigation = "2.9.0"
lifecycle = "2.9.4"
coroutines = "1.10.2"
serialization = "1.7.3"
filekit = "0.11.0"
coil = "3.3.0"
android-compileSdk = "36"
android-minSdk = "26"
android-targetSdk = "36"
```
*(RES lines 156–171; `[libraries]` 173–187 and `[plugins]` 189–197 follow — copy verbatim.)*

**Hard rules (RES, non-negotiable):**
- `serialization` is **`1.7.3`**, NOT `"2.2.20"` — kotlinx-serialization is not versioned with Kotlin (RES line 126, §Deprecated/outdated line 729).
- `composeCompiler` plugin version must equal `kotlin` (`org.jetbrains.kotlin.plugin.compose`) (RES line 120).
- Verify after first sync: `./gradlew :composeApp:dependencies --configuration commonMainImplementation` shows **every** `org.jetbrains.compose.*` at `1.8.2` (RES §Pitfall 2, line 574).

#### `composeApp/build.gradle.kts`

**Governing convention:** RES §Pattern 1 lines 366–380. `schemaOutputDirectory` is **REQUIRED** — without it no migration-verification task is registered at all.

```kotlin
// Source: SQLDelight official docs — Migrations (RES lines 368–379)
sqldelight {
    databases {
        create("CarRecordDatabase") {
            packageName.set("com.carrecord.db")
            // REQUIRED — without this NO migration-verification task is registered at all.
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            // Phase 1: keep queries synchronous (resolves STATE open question — RES Pitfall 6).
            generateAsync.set(false)
        }
    }
}
```

Also required on this module (RES §Standard Stack, §Architecture Patterns):
- KMP targets: `androidTarget { }` (Kotlin 2.2.20 path — **not** `com.android.kotlin.multiplatform.library`; RES §Alternatives line 152, §Pitfall 9 line 617), `iosArm64`, `iosSimulatorArm64`.
- Explicit `implementation("org.jetbrains.compose.material:material-icons-core:1.7.3")` — **pinned**, do NOT use the `compose.materialIconsCore` alias (UISPEC §Icon Contract line 39). `material-icons-extended` is **not** used.
- `compose.components.resources` dependency for D-11 strings (RES §Standard Stack line 141).

#### `gradle/wrapper/gradle-wrapper.properties`

**Governing convention:** RES §Standard Stack line 201 — pin `8.14.6` via `./gradlew wrapper --gradle-version 8.14.6`; no standalone Gradle is installed on this machine (RES §Environment Availability line 785).

#### `.github/workflows/ci.yml`

**Governing convention:** RES §Code Examples lines 690–710. The headline success criterion (ROADMAP SC-5, DAT-04) is enforced by the **per-source-set** task **plus** a `git diff --exit-code` schema guard — the aggregate `verifySqlDelightMigration` passes vacuously (RES §Pitfall 1, line 567).

```yaml
      - name: Verify SQLDelight migrations (per-source-set task)
        run: ./gradlew verifyCommonMainCarRecordDatabaseMigration
      - name: Fail if schema changed without a migration
        run: |
          ./gradlew generateCommonMainCarRecordDatabaseSchema
          git diff --exit-code -- composeApp/src/commonMain/sqldelight/databases
      - name: Build shared module + Android
        run: ./gradlew :composeApp:assembleDebug
```
*(Full file with Java 21 setup: RES lines 690–710.)*

> **Naming correction to propagate:** ROADMAP §Architecture Notes line 90 says `generateMainCarRecordSchema`. The KMP-correct task name is **`generateCommonMainCarRecordDatabaseSchema`** / **`verifyCommonMainCarRecordDatabaseMigration`** (RES line 358, §Pitfall 1 line 568). Use the RES names.

#### `local.properties` / `gradle.properties` / `AndroidManifest.xml`

- `local.properties`: set `sdk.dir=…` (RES §Environment Availability line 789). File creation requires user approval per global AGENTS.md rules (RES §User Constraints line 69).
- `gradle.properties`: must not carry credentials (RES §Security Domain V14, line 821).
- `AndroidManifest.xml`: open plan decision — `android:allowBackup="false"` or a backup-rules file excluding the DB + photo dir (RES §Security Domain line 833). Flag as a decision, do not silently choose.

---

### Group B — Entry points & platform hosts

**ANALOG: NONE (greenfield) — convention sourced from RES §Pattern 6 + §Architecture Patterns**

#### `composeApp/src/commonMain/kotlin/com/carrecord/App.kt`

**Governing convention:** RES §Pattern 6 lines 521–549. The root composable owns `KoinApplication` + a single `NavHost` + `Scaffold(bottomBar = BottomTabs)` — never two nav graphs, never platform UI forks (D-17).

```kotlin
// Source: Navigation Compose 2.9.0 docs (RES lines 529–549)
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

**Binding additions:** theme is applied here or in `Theme.kt` as `MaterialTheme(colorScheme = CarRecordLightColors)` **unconditionally** — `isSystemInDarkTheme()` is never consulted, dynamic color disabled (UISPEC §Platform & Theme Constraints lines 60–61, §C-8).

#### Platform hosts

**Governing convention:** RES system diagram lines 262–263 + §Anti-Patterns line 622.

- Android: a thin `MainActivity` → `setContent { App() }`.
- iOS: `MainViewController.kt` → `ComposeUIViewController { App() }`; FileKit's `PHPickerViewController` presentation requires the picker to be created inside composition (RES §Pitfall 10, lines 621–623).
- Swift: `iosApp/iosApp/iOSApp.swift` + `ContentView.swift` wrapping the Kotlin `MainViewController` (RES §Recommended Project Structure line 352).
- iOS deployment target `IPHONEOS_DEPLOYMENT_TARGET=16.0`; Android `minSdk=26` (ROADMAP SC-1, DAT-02; RES §Standard Stack line 170).

---

### Group C — DI

**ANALOG: NONE (greenfield) — convention sourced from RES §Pattern 5**

#### `composeApp/src/commonMain/kotlin/com/carrecord/di/AppModule.kt`

```kotlin
// Source: Koin docs — Koin Compose ViewModel for KMP (RES lines 493–497)
val appModule = module {
    single { DatabaseFactory(createDriver()) }
    single { get<DatabaseFactory>().database }
    single<VehicleRepository> { VehicleRepositoryImpl(get()) }
    viewModel { VehicleListViewModel(get()) }      // ctor-injected repository
}
```

**Binding rules:**
- ViewModels are declared in `commonMain` and obtained with `org.koin.compose.viewmodel.koinViewModel` — **not** `org.koin.androidx.*` (RES §Pattern 5 note line 512, §Pitfall 7 lines 601–605).
- Only Android needs a platform module for `androidContext` / `androidLogger` (RES §Architectural Responsibility Map line 107).
- The repository binds behind the `VehicleRepository` **interface** — UI depends on the abstraction (STATE §Architecture Constraints line 77).

---

### Group D — Data layer

**ANALOG: NONE (greenfield) — convention sourced from RES §Pattern 2 / §Pattern 3 / §Code Examples**

#### `composeApp/src/commonMain/sqldelight/com/carrecord/db/Vehicle.sq`

**Governing convention:** RES §Pattern 2 lines 391–412. Pragma lives in the `.sq` preamble so the generated `Schema.create()` emits it on **both** platforms (DAT-02, DAT-03, RES §Pitfall 5).

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
lastInsertId: SELECT last_insert_rowid();            -- portable insert id (NO `RETURNING` — needs SQLite ≥ 3.35)
```

**Schema ↔ decision trace:**

| Schema element | Decision | Note |
|---|---|---|
| No odometer column | D-03 | Odometer is Phase 2 (service entries) |
| `year TEXT` (not INTEGER) | D-13 | Digits-only text field; no range validation |
| `plate TEXT` nullable, free text | D-12 | No format validation, no format hint |
| `type TEXT NOT NULL DEFAULT 'Car'` | D-10, UISPEC §C-5 | Plain string → new types need **no migration** |
| `photo_path TEXT` = **relative path only** | D-05 | Never a BLOB, never `content://`/absolute URI |
| `ORDER BY id DESC` | D-16 | Newest first; also removes any need for a date library (RES OQ-4) |
| Only `name` NOT NULL | D-09 | Fast add; all else optional |
| `lastInsertId: SELECT last_insert_rowid()` | DAT-01 (insert id) | Portable id retrieval; `insert` is a plain `INSERT` so `executeAsOne()` on it does not exist and `RETURNING` is unavailable at minSdk 26 |

> **Location rule:** schema files MUST live in `composeApp/src/commonMain/sqldelight/<package>/`. A file under `src/main/sqldelight/` is **silently ignored** by the KMP tasks and the CI check passes vacuously (RES line 358, §Anti-Patterns line 557).

#### `composeApp/src/commonMain/sqldelight/databases/1.db`

Generated by `./gradlew generateCommonMainCarRecordDatabaseSchema`, then **committed**. Never hand-edit — it is a build output (RES §Pattern 1 line 382, §Anti-Patterns line 561). `migrations/` stays **empty in Phase 1** (RES §Recommended Project Structure line 347).

#### `data/db/DriverFactory.kt` (+ `.android.kt` / `.ios.kt`)

**Governing convention:** RES §Code Examples lines 632–658 (from SQLDelight multiplatform docs). Genuinely divergent — `AndroidSqliteDriver` needs a `Context`; `NativeSqliteDriver` needs no context (RES §Architectural Responsibility Map line 103).

```kotlin
// commonMain
expect class DriverFactory { fun createDriver(): SqlDriver }

// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(CarRecordDatabase.Schema, context, "carrecord.db")
    // Note: no need to force WAL here if `PRAGMA journal_mode=wal;` is in the .sq preamble.
}

// iosMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(CarRecordDatabase.Schema, "carrecord.db")
    // touchlab SQLiter default for this driver is already JournalMode.WAL.
}
```

**Anti-pattern to avoid:** setting `journal_mode` only in Android's `onConfigure` — the criterion becomes platform-dependent (RES §Anti-Patterns line 560).

#### `data/VehicleRepositoryImpl.kt` + `data/VehicleRepository.kt`

**Governing convention:** RES §Pattern 3 lines 423–447. One injected dispatcher — `Dispatchers.IO.limitedParallelism(1)` (STATE Key Decisions line 73; prevents iOS `SIGABRT` from concurrent `NativeSqliteDriver` access).

```kotlin
// Source: kotlinx.coroutines docs — Dispatchers.IO.limitedParallelism(1) (RES lines 425–446)
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
                db.vehicleQueries.lastInsertId().executeAsOne()   // portable id — never RETURNING
            } else {
                db.vehicleQueries.update(/* … */)
                vehicle.id
            }
        }
    }
}
```

**Binding rules:**
- **Never** call `Dispatchers.IO` inline — inject it (RES §Anti-Patterns line 559, §Pattern 3).
- **Insert id is read with `lastInsertId: SELECT last_insert_rowid();` inside the same `transactionWithResult` block** — the `insert` query is a plain `INSERT` (no `RETURNING`), so `insert(...).executeAsOne()` does not exist. `INSERT ... RETURNING` is forbidden: it needs SQLite ≥ 3.35 and the Android driver uses the OS SQLite (< 3.35 across API 26–33 at minSdk 26). See RES §Pattern 3 gotcha.
- Multi-statement writes wrap in `transactionWithResult { }` (RES line 449).
- Row delete + photo-file delete go through the **same** repository flow so no orphan files remain (D-07, RES §Pitfall 4 line 586, §Security Domain line 830).
- `delete` MUST call `PhotoStorage.delete(relativePath)` — a `delete` that only calls `vehicleQueries.delete(id)` is the named warning sign (RES §Pitfall 4 line 587).
- All SQL values are SQLDelight **typed bind parameters**, never string-concatenated (RES §Security Domain V5 line 816, threat table line 827).

#### `data/VehicleEntityMapper.kt`

**Governing convention:** STATE §Architecture Constraints line 78 — *"Domain models: never reference SQLDelight generated types (use mappers)"*; RES §Anti-Patterns line 555.

`VehicleEntity ↔ Vehicle` conversion lives **here only**. The repository returns `domain.Vehicle`; no composable ever receives `VehicleEntity`. Rationale: survives schema regeneration and keeps `commonMain/ui` decoupled from the schema.

#### `domain/Vehicle.kt`, `domain/VehicleType.kt`

Plain data class + enum, **zero SQLDelight imports** (STATE line 78; RES §Architectural Responsibility Map line 100 — "Domain models + validation rules: `commonMain`, testable in `commonTest`").

`VehicleType` is the fixed 3-value list `Car | Motorcycle | Truck` (D-10) mapped to a **plain string** at the DB boundary so new types need no migration (RES §Pattern 2 note). UI default is `Car`, matching the schema default (UISPEC §C-5 line 413).

---

### Group E — Utilities & resources

**ANALOG: NONE (greenfield) — convention sourced from RES §Pattern 4 + D-11**

#### `composeApp/src/commonMain/kotlin/com/carrecord/util/PhotoStorage.kt`

**Governing convention:** RES §Pattern 4 lines 458–478 (from FileKit docs). The photo's bytes never enter the DB — only the **relative path string** does (D-05).

```kotlin
// Source: FileKit docs — write/compress/delete + FileKit.filesDir (RES lines 460–477)
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

**Binding rules:**
- **Never** store an absolute path, a `file://` URI, or the picker's temp URL — iOS assigns a new container UUID per install, so absolute paths break after update (RES §Pitfall 3 lines 577–581). Always resolve `FileKit.filesDir` fresh at read time.
- Downscale before persisting via FileKit `compressImage()` (RES §Don't Hand-Roll line 244).
- Filename strategy recommended: `<vehicleId>-<epochMillis>.jpg`, old file deleted on replace — avoids Coil cache staleness while satisfying D-07 (RES OQ-5 line 770).
- Don't hand-roll storage: use `FileKit.filesDir` not `expect/actual` over `filesDir`/`NSSearchPathForDirectoriesInDomains` (RES §Don't Hand-Roll line 242).

#### `composeResources/values/strings.xml` (+ 3 vector drawables)

**Governing convention:** D-11 + RES §Code Examples lines 663–684 + UISPEC §Copywriting Contract lines 259–342. **The complete, implementation-ready string set is authored in UISPEC lines 271–342 — copy it verbatim; no string may be hardcoded inline in a composable.**

```kotlin
// Usage — RES lines 679–683
import carrecord.composeapp.generated.resources.Res
import carrecord.composeapp.generated.resources.add_vehicle
import org.jetbrains.compose.resources.stringResource

Text(text = stringResource(Res.string.add_vehicle))
```

> Confirm the generated `Res` package name after the first build — it is derived from the module `namespace`/application id (RES line 686).

Drawables: 3 vehicle-type vectors (`ic_vehicle_car`, `ic_vehicle_motorcycle`, `ic_vehicle_truck`), 24dp artboard, `fillColor="#FF1C1C1E"`, loaded with `vectorResource(...)` — required because `DirectionsCar`/`TwoWheeler`/`LocalShipping` do **not** exist in `material-icons-core` (UISPEC §Icon Contract lines 40–52, §C-7).

---

### Group F — UI: theme & navigation

**ANALOG: NONE (greenfield) — convention sourced from UISPEC (binding contract) + RES §Pattern 6**

#### `ui/theme/Color.kt` + `Theme.kt` + `Type.kt`

**Governing convention:** UISPEC §Platform & Theme Constraints lines 65–83 (implement **exactly**).

```kotlin
// UISPEC lines 68–83
val Paper    = Color(0xFFFFFFFF) // dominant surface
val Mist     = Color(0xFFF4F4F5) // secondary surface
val Ink      = Color(0xFF1C1C1E) // accent fill + all primary text/icons
val Slate    = Color(0xFF5C5C61) // secondary text + inactive icons
val Hairline = Color(0xFFE4E4E7) // outlines / dividers
val Danger   = Color(0xFFB3261E) // destructive only

val CarRecordLightColors = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    secondary = Ink, onSecondary = Paper,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Mist, onSurfaceVariant = Slate,
    outline = Hairline, error = Danger, onError = Paper,
)
```

**Binding rules:** light only, dynamic color disabled, `Icons.AutoMirrored.Filled.ArrowBack` (never the deprecated `Icons.Filled.ArrowBack`). Typography overrides M3 slots in one `CarRecordTypography` object — declared sizes are exactly {14, 16, 20, 24sp}, weights exactly {400, 500}; do not rely on M3 defaults (UISPEC §Typography line 143). The accent value **equals** the ink token because the real accent is user-deferred (D-21, UISPEC lines 171).

#### `ui/navigation/Routes.kt`

**Governing convention:** RES §Pattern 6 lines 524–527 — type-safe `@Serializable` routes; do not invent new ones.

```kotlin
// RES lines 524–527
@Serializable data object VehicleListRoute
@Serializable data object SettingsRoute          // D-17 placeholder tab
@Serializable data class VehicleEditRoute(val id: Long = 0L)
@Serializable data class VehicleDetailRoute(val id: Long)
```

`VehicleEditRoute(id = 0L)` is the **add** case and `id > 0` the **edit** case — one destination satisfies D-08 without two screens (RES line 551).

---

### Group G/H — Screens & components

**ANALOG: NONE (greenfield) — convention sourced from UISPEC §Screens / §States / §Interaction rules + RES §Pattern 5**

#### ViewModels (`VehicleListViewModel`, `VehicleEditViewModel`, `VehicleDetailViewModel`)

```kotlin
// Source: Koin docs (RES lines 500–504)
class VehicleListViewModel(private val repo: VehicleRepository) : ViewModel() {
    val uiState: StateFlow<VehicleListUiState> =
        repo.observeVehicles()
            .map { VehicleListUiState.Content(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleListUiState.Loading)
}
```

Obtained in the screen with `koinViewModel()` from `org.koin.compose.viewmodel` (RES line 509). **UI-state must model the UISPEC §States matrix explicitly** (Loading / Empty / Content / Error for the list; Loading / Not-found for Detail; Idle / Invalid / Saving / Save-failed for Add-Edit) — *"No state may render a blank white area"* (UISPEC line 224).

#### Screens & components

**Governing convention:** UISPEC §Screens & Component Inventory lines 193–218, §States lines 224–253, §Interaction rules lines 240–253. These are **binding** — treat UISPEC §Decisions C-1…C-9 (lines 407–417) as locked even though CONTEXT.md listed them as agent discretion.

Cross-cutting UI rules to apply to all screens/components:
- One `Scaffold` + `TopAppBar` per screen, simple title bar, plain back arrow (D-20).
- All spacing from the 7 declared tokens only — `4/8/16/24/32/48/64` (UISPEC §Spacing Scale lines 87–101).
- All type from the 4 declared sizes (UISPEC §Typography).
- Every interactive element ≥ 48dp; icon-only controls carry `contentDescription` (UISPEC §Accessibility lines 350–359).
- No `long-press` on the list, ever (D-18, UISPEC line 244).
- `photoPath` null/blank → `Mist` 64dp type-icon circle (D-14, UISPEC lines 110–112).

---

### Group I — Tests

**ANALOG: NONE (greenfield) — convention sourced from RES §Pitfall 4/5 + VEH requirements**

- `VehicleRepositoryTest` — assert **delete removes the photo file** (RES §Pitfall 4 line 586: *"Add a test that asserts the file is gone after `deleteVehicle(id)`"*).
- `VehicleEntityMapperTest` — round-trip `Vehicle ↔ VehicleEntity` (STATE §Architecture Constraints).
- `VehicleFormValidationTest` — `name` non-blank after trim gates submit; year digits-only ≤4 chars (D-09, D-13; UISPEC line 235).
- Per-platform `WalModeTest` — open the DB and query `PRAGMA journal_mode;` expecting `wal`, plus assert the `-wal` sidecar exists after a write (RES §Pitfall 5 line 592). This is the DAT-03 / ROADMAP SC-5 proof.

---

### Group J — Wave 0 (must run first)

**ANALOG: NONE (greenfield) — convention sourced from RES §Environment Availability**

**A `checkpoint:human-verify` gate, not a code task** (RES §User Constraints line 72). Build + run a hello-world on both platforms with the chosen pin set and have the user re-confirm the version set. Two blocking environment gaps (RES §Environment Availability lines 795–797): JDK ≤ 24 (only 25/26 installed) and Xcode ≤ 16.4 (27.0 installed). Neither is fixable inside the repo. Installing JDK 21 / Xcode 16.4 requires **explicit user approval** under the global working rules (RES §User Constraints line 69) — the plan must present a single yes/no gate (lock pins + installs **vs** adopt the current wizard stack wholesale) and must not mix generations (RES OQ-1 line 750).

---

## Shared Patterns

These cross-cutting concerns apply to multiple files. Each cites its canonical source.

### 1. Serialized DB access (single IO lane)
**Source:** STATE §Key Decisions line 73; RES §Pattern 3.
**Apply to:** every `VehicleRepository` method (reads included).
```kotlin
private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
```
Never call `Dispatchers.IO` inline (RES §Anti-Patterns line 559).

### 2. Mapper boundary — no generated types above the data layer
**Source:** STATE §Architecture Constraints line 78; RES §Anti-Patterns line 555.
**Apply to:** `VehicleRepository*`, every ViewModel, every composable, `Vehicle*Screen`.
Domain model in, domain model out. `VehicleEntity` never crosses into `commonMain/ui`.

### 3. Relative-path-only photo persistence
**Source:** D-05, D-07; RES §Pattern 4, §Pitfall 3.
**Apply to:** `PhotoStorage`, `VehicleRepositoryImpl`, `PhotoField`, `VehicleCard`, `VehicleDetailScreen`.
DB stores `vehicles/<name>.jpg`; resolve `FileKit.filesDir` at read time; delete the file whenever the row is deleted or the photo replaced.

### 4. WAL from first launch (SQL-level, both platforms)
**Source:** DAT-02/03; RES §Pattern 2, §Pitfall 5.
**Apply to:** `Vehicle.sq` (single point of truth) — **not** the platform driver factories.
`PRAGMA journal_mode=wal;` in the `.sq` preamble so generated `Schema.create()` emits it identically on Android and iOS. Never Android-`onConfigure`-only.

### 5. Migration enforcement
**Source:** DAT-04; RES §Pattern 1, §Pitfall 1.
**Apply to:** `composeApp/build.gradle.kts`, `.github/workflows/ci.yml`.
`schemaOutputDirectory` must be set; CI runs the per-source-set task **and** `git diff --exit-code` on the committed `1.db`.

### 6. Centralized strings
**Source:** D-11; UISPEC §Copywriting Contract.
**Apply to:** every composable.
All user-facing text via `stringResource(Res.string.…)`. No inline literals anywhere.

### 7. Typed bind parameters (no string-built SQL)
**Source:** RES §Security Domain V5 / threat table.
**Apply to:** every SQLDelight query + every repository call site.

---

## No Analog Found

**Every planned file falls in this table** — the repository is greenfield, so no file has an existing first-party analog.

| Scope | Files | Role | Data Flow | Reason | Substitute convention |
|---|---|---|---|---|---|
| Build & config | Group A (8 files) | config | transform / batch | No build files exist | RES §Standard Stack, §Pattern 1, §Code Examples (CI) |
| Entry & hosts | Group B (5 files) | platform-actual | request-response | No source exists | RES §Pattern 6, §Architecture Patterns |
| DI | Group C (2 files) | provider | config/wiring | No source exists | RES §Pattern 5 |
| Data layer | Group D (12 files) | repository / mapper / DB / model | CRUD / streaming / transform | No source exists | RES §Pattern 2, §Pattern 3, §Code Examples; STATE constraints |
| Utilities & resources | Group E (5 files) | utility / resource | file-I/O / transform | No source exists | RES §Pattern 4; D-11; UISPEC |
| Theme & navigation | Group F (5 files) | utility / component / route | transform / request-response | No source exists | UISPEC §Platform & Theme, §Typography; RES §Pattern 6 |
| Screens & components | Group G (14 files) | component / ViewModel | request-response / streaming / file-I/O | No source exists | UISPEC §Screens, §States, §Interaction; RES §Pattern 5 |
| Settings | Group H (1 file) | component | request-response | No source exists | D-17; UISPEC §Screens |
| Tests | Group I (5 files) | test | CRUD / transform | No test suite exists | RES §Pitfall 4, §Pitfall 5; VEH/DAT requirements |
| Wave 0 | Group J (transient) | config / test | transform | Nothing builds yet | RES §Environment Availability, §Pitfall 8 |

---

## Governance Conflicts to Surface Before Planning

The planner must not silently pick a side here — each is a locked-vs-researched divergence.

### Conflict 1 — Version set (HEADLINE, blocks Group A)
STATE §Key Decisions and ROADMAP §Architecture Notes lock **SQLDelight 2.3.2 + Koin 4.2.0**. RES §Standard Stack documents both as **internally inconsistent** with Kotlin 2.2.20 / CMP 1.8.2 (registry metadata: SQLDelight 2.3.x declares `kotlin-stdlib 2.3.10`; Koin 4.2.0 declares `compose.runtime 1.10.2`, silently force-upgrading the Compose stack — RES §Pitfall 2, lines 571–575) and recommends **SQLDelight 2.2.1 + Koin 4.1.1**. RES §Assumptions A1 line 735 flags this as *"the single decision the planner must surface first."*

### Conflict 2 — Environment (blocks Wave 0)
JDK 25/26 only (Gradle 8.14 needs ≤24) and Xcode 27.0 (Kotlin 2.2.20 supports ≤16.4). RES lines 795–797: *"not fixable inside the repository."* Requires user-approved installs **or** adopting the current wizard-default stack (Kotlin 2.4.x / AGP 9.x / CMP 1.12.x / SQLDelight 2.4.1 / Koin 4.2.2 — RES §Summary line 17).

### Conflict 3 — Task naming
ROADMAP line 90 says `generateMainCarRecordSchema`; the KMP-correct name is `generateCommonMainCarRecordDatabaseSchema` (RES line 358). `verifySqlDelightMigration` (aggregate) passes vacuously — CI must use `verifyCommonMainCarRecordDatabaseMigration` + the `git diff` guard (RES §Pitfall 1).

### Conflict 4 — `generateAsync` (STATE Open Question 5)
STATE line 88 lists this as unresolved. RES §Pitfall 6 lines 595–599 resolves it: set `generateAsync.set(false)` in Phase 1.

### Conflict 5 — New dependencies not in STATE
RES introduces **FileKit 0.11.0** and **Coil 3.3.0** (§Assumptions A3 line 737 — CONTEXT.md left picker wiring to agent discretion) and **`material-icons-core:1.7.3`** pinned (UISPEC §Icon Contract — CMP 1.8.2 does not declare it transitively). All three clear RES's Package Legitimacy Audit (Maven Central verified). Planner should surface them as approved-if-the-version-fork-is-approved.

---

## Metadata

**Analog search scope:** whole repository — `/Users/andika/Belajar-Coding/VibeCoding#1` (recursive `ls -la`; contents = `.git/`, `.planning/` only). Also probed for `AGENTS.md`, `.claude/skills/`, `.agents/skills/` — **none exist**.
**Files scanned:** 0 source files (none exist). 68 KB RESEARCH.md, 34 KB UI-SPEC.md, CONTEXT.md, STATE.md, ROADMAP.md, DISCUSSION-LOG.md consumed in full.
**Planned files classified:** 46 (8 build/config · 5 entry/hosts · 2 DI · 12 data · 5 util/resource · 5 theme/nav · 15 screens/components · 5 tests — some files overlap groups).
**Analog matches:** 0 exact · 0 role-match · 0 partial · **46 NONE (greenfield)**.
**Pattern extraction date:** 2026-10-18
**Authoritative convention sources:** `01-RESEARCH.md` (primary — version-verified patterns), `01-UI-SPEC.md` (binding UI contract, status: approved), `01-CONTEXT.md` D-01…D-22, `STATE.md` Architecture Constraints, `ROADMAP.md` Architecture Notes.
**Volatility note:** the version table and environment rows expire in ~7 days (RES line 869) — re-verify at the Wave 0 checkpoint.
