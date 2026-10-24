# Phase 3: Reminders & Dashboard - Pattern Map

**Mapped:** 2026-10-22
**Files analyzed:** 56 (39 source, 8 UI components, 7 tests, 2 generated/baseline artifacts)
**Analogs found:** 52 / 56 (39 exact · 12 role-match · 1 partial · 4 none)

**Path shorthand used below:** `…/commonMain/` = `shared/src/commonMain/`, `…/androidMain/` = `shared/src/androidMain/`, `…/iosMain/` = `shared/src/iosMain/`, `…/commonTest/` = `shared/src/commonTest/`, `…/iosTest/` = `shared/src/iosTest/`.

**This is NOT a greenfield phase.** Phase 1 (vehicles) and Phase 2 (service log) are shipped, green, and contain a first-party analog for almost every Phase 3 file. The excerpts below are **actual code from this working tree** with line numbers. Where `03-RESEARCH.md` or `03-UI-SPEC.md` describes something the shipped code does not do, the shipped code wins and the divergence is called out in §Corrections. The one genuinely new subsystem — notification delivery — has no analog at all and is mapped to the repo's existing `expect`/`actual` structural precedent instead.

---

## Corrections That Override Upstream Docs (read before planning)

| # | Upstream says | Shipped code says | Where |
|---|---|---|---|
| C-1 | `ReminderRepositoryImpl.kt  # impl + mapper` (`03-RESEARCH.md:252`) | Phase 1 and Phase 2 both ship a **separate `*EntityMapper.kt`** per table (`VehicleEntityMapper.kt`, `ServiceEntryEntityMapper.kt`), with the bind-parameter class in the same file. Phase 3 must add `data/ReminderRuleEntityMapper.kt`; putting `toDomain()` inside the repository would be the first deviation from the convention. | `data/ServiceEntryEntityMapper.kt:12-83`, `data/VehicleEntityMapper.kt` |
| C-2 | The research tree lists `DatabaseProvider.android.kt` and `DatabaseProvider.ios.kt` (`03-RESEARCH.md:256, 264`) | The research's own Pattern 1 code says **`object DatabaseProvider` — plain, no expect/actual**. There is nothing platform-specific in a `var holder: DatabaseFactory?`. Create **one** `commonMain` file and **no** actuals. The platform entry points are `VehiCareApp.onCreate` (Android) and `MainViewController` (iOS). | `03-RESEARCH.md:308-322`; `data/db/DatabaseFactory.kt:6-15` |
| C-3 | `AppModule`'s `getOrNull<DriverFactory>() ?: createDriverFactory()` fallback is merely "unused" after the holder lands | It **throws on Android**. `createDriverFactory(platformContext = null)` casts `null as? Context` and calls `error(...)`. Leaving it in place after removing the `DriverFactory` binding would turn a missing holder into a crash on first repository resolution. Deleting lines 34-37 is mandatory. | `di/AppModule.kt:34-37`; `data/db/DriverFactory.android.kt:34-41` |
| C-4 | `platformModule` keeps its `single<DriverFactory>` binding | Nothing consumes `DriverFactory` from Koin once `AppModule` stops constructing the database, so the Android binding at line 23 becomes dead. Remove it and keep `single<Context>` (the notification seam needs the context). Keeping a dead binding that calls `createDriverFactory` risks a **second** driver if it is ever resolved. | `di/PlatformModule.android.kt:21-24`; `data/db/DatabaseFactory.kt:9-11` |
| C-5 | `ReminderStatus` lives in `util/` (`03-UI-SPEC.md:203`) | `03-RESEARCH.md:246` puts it in `domain/`, and the shipped split agrees with research: **calculations** are `domain/` (`ServiceSummary`, `HistorySort`), **formatting/conversion seams** are `util/` (`ServiceDates`, `RupiahFormat`). `ReminderStatus` is a calculation. `ReminderInterval` (sentence assembly) stays `util/`-style. | `domain/ServiceSummary.kt:1-13`; `util/ServiceDates.kt:32-51` |
| C-6 | The permissions dialog is called `PermissionExplanationDialog` (`03-RESEARCH.md:255`) | The UI-SPEC's component inventory is the binding contract and names it **`NotificationsPermissionDialog`**. Use the UI-SPEC name; the same applies to `ReminderStatusRow` (the on-screen permission row) vs. the research's generic "status row". | `03-UI-SPEC.md:197-199, 243-246` |
| C-7 | `ServiceTypePicker` is reusable as-is for the single-select rule form | It has **no selection-mode parameter** today — it is multi-select by contract (`onToggle`, `selected: List<String>`). Phase 3 must add `selectionMode: SelectionMode = Multiple` (and a single-select callback path) to the existing component. The only current call site uses named arguments, so a defaulted parameter is call-site-safe. Do **not** fork a second picker. | `ui/service/components/ServiceTypePicker.kt:63-99`; `ui/service/ServiceEntryEditScreen.kt:350-357` |
| C-8 | `VehicleCard` ordering is "newest-first (D-16)" | D-57 supersedes D-16 **for the dashboard only**. The repository query `Vehicle.sq:17-18` (`ORDER BY id DESC`) is correct and stays; ordering moves into the ViewModel/aggregation layer. The stale comment at `VehicleListScreen.kt:105-106` must be rewritten, not the SQL. | `sqldelight/com/vehicare/db/Vehicle.sq:17-18`; `ui/vehicles/VehicleListScreen.kt:102-121` |
| C-9 | The migration test is untouched | `ServiceEntryMigrationTest.kt:48` asserts `assertEquals(3L, VehiCareDatabase.Schema.version)`. Phase 3 ships schema v4, so this **must** become `4L` or `iosSimulatorArm64Test` goes red. This is a required modification, not an optional cleanup. | `iosTest/…/data/ServiceEntryMigrationTest.kt:46-49` |
| C-10 | `ConfirmDeleteDialog` lives under `ui/vehicles/components/` (`03-CONTEXT.md` D-18 wording) | It was **moved** in Phase 2 to `ui/components/`, along with `EmptyState` and `FieldRow`. Reminder delete must import `com.vehicare.ui.components.ConfirmDeleteDialog`; nothing needs to move again. | `ui/components/ConfirmDeleteDialog.kt:1-16`; `ui/components/EmptyState.kt:1-23` |
| C-11 | `AndroidManifest.xml` already has whatever Phase 3 needs | It currently declares **no `<uses-permission>` element at all** — `POST_NOTIFICATIONS` is the first permission this app will ever request, and `android:name` / `launchMode` are absent. | `androidApp/src/main/AndroidManifest.xml:1-22` |

---

## File Classification

### Group A — Schema & migration

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/commonMain/sqldelight/com/vehicare/db/ReminderRule.sq` | migration/schema | CRUD | `…/db/ServiceEntry.sq` | exact |
| `…/commonMain/sqldelight/com/vehicare/db/migrations/3.sqm` | migration | CRUD | `…/db/migrations/2.sqm` | exact |
| `…/commonMain/sqldelight/databases/4.db` | DB baseline (generated, committed) | CRUD | `…/databases/3.db` | exact |

### Group B — Domain (commonMain, no generated types, no Compose)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/commonMain/kotlin/com/vehicare/domain/ReminderRule.kt` | model (domain) | transform | `domain/ServiceEntry.kt` / `domain/ServiceSummary.kt` | exact |
| `…/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt` | utility (pure calculation) | transform | `util/ServiceDates.kt` (pure object) + `domain/ServiceSummary.kt` (result type) | role-match |
| `…/commonMain/kotlin/com/vehicare/domain/ReminderInterval.kt` | utility (sentence assembly) | transform | `util/RupiahFormat.kt` (pure object + private helper) | role-match |

### Group C — Data layer

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/commonMain/kotlin/com/vehicare/data/ReminderRepository.kt` | repository (interface) | CRUD + streaming | `data/ServiceEntryRepository.kt` | exact |
| `…/commonMain/kotlin/com/vehicare/data/ReminderRepositoryImpl.kt` | repository (impl) | CRUD + streaming | `data/ServiceEntryRepositoryImpl.kt` | exact |
| `…/commonMain/kotlin/com/vehicare/data/ReminderRuleEntityMapper.kt` **(see C-1)** | utility (mapper) | transform | `data/ServiceEntryEntityMapper.kt` | exact |
| `…/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` **(MODIFIED — D-64 cascade)** | repository (impl) | CRUD | itself (`delete`, lines 76-90) | exact |

### Group D — Common platform seams & the process-wide DB holder

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/commonMain/kotlin/com/vehicare/data/db/DatabaseProvider.kt` **(plain object; no actuals — C-2)** | provider | config/wiring | `data/db/DatabaseFactory.kt` (process-wide invariant) + `data/db/DriverFactory.kt` (factory-function shape) | role-match |
| `…/commonMain/kotlin/com/vehicare/notifications/NotificationScheduler.kt` | service (platform seam) | event-driven | `data/db/DriverFactory.kt` (`expect class` + `expect fun create…`) | exact |
| `…/commonMain/kotlin/com/vehicare/notifications/NotificationPermission.kt` | service (platform seam) | request-response | `data/db/DriverFactory.kt` (same shape, plus a `@Composable expect`) | role-match |

### Group E — `androidMain` (Android-only SDKs; never `commonMain`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/androidMain/kotlin/com/vehicare/notifications/NotificationScheduler.android.kt` | service (platform impl) | event-driven | `data/db/DriverFactory.android.kt` | exact |
| `…/androidMain/kotlin/com/vehicare/notifications/NotificationPermission.android.kt` | service (platform impl) | request-response | `data/db/DriverFactory.android.kt` | role-match |
| `…/androidMain/kotlin/com/vehicare/work/ReminderWorker.kt` | worker | batch + event-driven | **none** → structural precedent `data/ServiceEntryRepositoryImpl.kt` (constructor-injected DB) | none |
| `…/androidMain/kotlin/com/vehicare/notifications/ReminderNotifications.kt` (channel + builder; implied by U1 "channel helper in `:shared` androidMain") | utility | event-driven | `data/db/DriverFactory.android.kt` (create-once side effect, private companion constants) | role-match |

### Group F — `iosMain`

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/iosMain/kotlin/com/vehicare/notifications/NotificationScheduler.ios.kt` | service (platform impl) | event-driven | `data/db/DriverFactory.ios.kt` | exact |
| `…/iosMain/kotlin/com/vehicare/notifications/NotificationPermission.ios.kt` | service (platform impl) | request-response | `data/db/DriverFactory.ios.kt` (ignores the context, `@Suppress("UNUSED_PARAMETER")`) | role-match |
| `…/iosMain/kotlin/com/vehicare/notifications/IosNotificationDelegate.kt` | provider (delegate) | event-driven | **none** → structural precedent `MainViewController.kt` (platform entry point) | none |
| `…/iosMain/kotlin/com/vehicare/di/PlatformModule.ios.kt` (implied by RESEARCH Pattern 2) | provider (DI) | config/wiring | `di/PlatformModule.android.kt` | exact |
| `…/iosMain/kotlin/com/vehicare/MainViewController.kt` **(MODIFIED — holder init + module)** | component (host) | config/wiring | itself | exact |

### Group G — Wiring, shell & resources

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/commonMain/kotlin/com/vehicare/di/AppModule.kt` **(MODIFIED — holder, repo, 2 VMs; fallback deleted)** | provider (DI) | config/wiring | itself | exact |
| `…/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` **(MODIFIED — 2 routes)** | route | config/wiring | itself | exact |
| `…/commonMain/kotlin/com/vehicare/App.kt` **(MODIFIED — 2 composables, deep-link collector, delete snackbar)** | component (shell) | request-response | itself | exact |
| `…/commonMain/composeResources/values/strings.xml` **(MODIFIED — append Phase 3 block before `</resources>`, line 138)** | config (i18n) | transform | itself | exact |
| `gradle/libs.versions.toml` **(MODIFIED — `androidx-work = "2.12.0"` + library)** | config | build-time | itself | exact |
| `shared/build.gradle.kts` **(MODIFIED — `androidMain` implementation of work-runtime)** | config | build-time | itself (`androidMain.dependencies`, lines 45-51) | exact |
| `androidApp/src/main/AndroidManifest.xml` **(MODIFIED — POST_NOTIFICATIONS, `android:name`, `launchMode="singleTop"`; see C-11)** | config | config/wiring | itself | exact |
| `androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt` | provider (Application) | config/wiring | `MainActivity.kt` (host shell) + `PlatformModule.android.kt` | role-match |
| `androidApp/src/main/kotlin/com/vehicare/MainActivity.kt` **(MODIFIED — `onNewIntent` + cold-start extra)** | component (host) | request-response | itself | exact |
| `iosApp/iosApp/iOSApp.swift` **(MODIFIED — `@UIApplicationDelegateAdaptor`)** | config | config/wiring | itself | exact |
| `iosApp/iosApp/AppDelegate.swift` | provider (delegate) | event-driven | **none** → structural precedent `iOSApp.swift` (10-line host) | none |

### Group H — Reminders UI (new, `ui/reminders/`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/ui/reminders/RemindersScreen.kt` | component (screen) | streaming | `ui/service/ServiceHistoryScreen.kt` | exact |
| `…/ui/reminders/RemindersViewModel.kt` | service (ViewModel) | streaming + toggle/delete | `ui/service/ServiceHistoryViewModel.kt` | exact |
| `…/ui/reminders/ReminderEditScreen.kt` | component (screen) | request-response | `ui/service/ServiceEntryEditScreen.kt` | exact |
| `…/ui/reminders/ReminderEditViewModel.kt` | service (ViewModel) | request-response | `ui/service/ServiceEntryEditViewModel.kt` | exact |
| `…/ui/reminders/ReminderFormValidation.kt` | utility (pure validation object) | transform | `ui/service/ServiceEntryFormValidation.kt` | exact |
| `…/ui/reminders/components/ReminderCard.kt` | component | request-response | `ui/service/components/ServiceSummaryRow.kt` + `ui/vehicles/components/VehicleCard.kt` | role-match |
| `…/ui/reminders/components/ReminderSummaryRow.kt` | component | request-response | `ui/service/components/ServiceSummaryRow.kt` | exact |
| `…/ui/reminders/components/ReminderStatusRow.kt` | component | request-response | `ui/service/components/ServiceSummaryRow.kt` (row anatomy) | role-match |
| `…/ui/reminders/components/ReminderToggleRow.kt` | component | request-response | `ui/components/FieldRow.kt` (label/value row) + M3 `Switch` | none |
| `…/ui/reminders/components/NotificationsPermissionDialog.kt` | component | request-response | `ui/components/ConfirmDeleteDialog.kt` | role-match |
| `…/ui/reminders/components/PermissionBanner.kt` | component | request-response | `ui/service/components/ServiceSummaryRow.kt` (row anatomy) | partial |

### Group I — Modified existing UI

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/ui/vehicles/components/VehicleCard.kt` **(MODIFIED — one status line, D-58/D-59)** | component | request-response | itself (`Column`, lines 91-109) | exact |
| `…/ui/vehicles/VehicleListScreen.kt` **(MODIFIED — banner first item, ordering text)** | component (screen) | streaming | itself | exact |
| `…/ui/vehicles/VehicleListViewModel.kt` **(MODIFIED — dashboard aggregation, D-57)** | service (ViewModel) | streaming | itself + `ServiceHistoryViewModel` | exact |
| `…/ui/vehicles/VehicleDetailScreen.kt` **(MODIFIED — `ReminderSummaryRow` + `onOpenReminders`)** | component (screen) | streaming | itself (lines 313-325) | exact |
| `…/ui/service/components/ServiceTypePicker.kt` **(MODIFIED — `selectionMode: SelectionMode = Multiple`, see C-7)** | component | request-response | itself | exact |

### Group J — Tests

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/commonTest/kotlin/com/vehicare/ui/reminders/ReminderFormValidationTest.kt` | test | transform | `commonTest/…/ui/service/ServiceEntryFormValidationTest.kt` | exact |
| `…/commonTest/kotlin/com/vehicare/ui/reminders/ReminderStatusTest.kt` | test | transform | `commonTest/…/ui/service/ServiceMetaLineTest.kt` + `ServiceDatesTest.kt` (pure output tests) | role-match |
| `…/commonTest/kotlin/com/vehicare/data/ReminderRuleEntityMapperTest.kt` | test | transform | `commonTest/…/data/ServiceEntryEntityMapperTest.kt` | exact |
| `…/iosTest/kotlin/com/vehicare/data/ReminderMigrationTest.kt` | test | CRUD | `iosTest/…/data/ServiceEntryMigrationTest.kt` | exact |
| `…/iosTest/kotlin/com/vehicare/data/ReminderRepositoryTest.kt` | test | CRUD | `iosTest/…/data/ServiceEntryRepositoryTest.kt` | exact |
| `…/iosTest/kotlin/com/vehicare/data/ReminderDeletionTest.kt` (rule delete + vehicle cascade) | test | CRUD | `iosTest/…/data/ServiceEntryDeletionTest.kt` | exact |
| `…/iosTest/kotlin/com/vehicare/data/ServiceEntryMigrationTest.kt` **(MODIFIED — `4L`, see C-9)** | test | CRUD | itself (line 48) | exact |

---

### Where the new files sit relative to `DriverFactory` / `platformModule`

This is the phase's highest-risk placement question, so it is stated before the excerpts.

```
            ┌──────────────────────────── commonMain ────────────────────────────┐
            │ DatabaseProvider (object)        NotificationScheduler (interface) │
            │   holder: DatabaseFactory?         NotificationPermission (expect)  │
            │   initialize(factory) / require()                                   │
            └───────────────▲──────────────────────────▲──────────────────────────┘
                            │                          │
   Android                  │                          │                       iOS
   VehiCareApp.onCreate ────┘                          └──── MainViewController()
     createDriverFactory(this, defaultDatabaseName)          createDriverFactory()
     DatabaseFactory(driver)                                 DatabaseFactory(driver)
     createNotificationChannel(this)                         installNotificationDelegate()
     platformModule(this) passed to App(...)                 iosPlatformModule passed to App(...)
                            │                          │
   ReminderWorker (androidMain) reads DatabaseProvider.require()
   — it has NO Koin container, and must never construct a driver
```

- `DatabaseProvider` is the **only** new construction point other than the two hosts. `AppModule` stops building the database and becomes `single<VehiCareDatabase> { DatabaseProvider.require() }` (`di/AppModule.kt:34-38` is replaced, not extended).
- `platformModule` keeps supplying `Context`; its `DriverFactory` binding is deleted (C-4). `platformModule` gains `single<NotificationScheduler> { createNotificationScheduler(get<Context>()) }`; `iosPlatformModule` is the two-line iOS twin and is passed from `MainViewController` into `App(...)`, exactly as `MainActivity` passes `platformModule` today (`MainActivity.kt:22-24`).
- The worker is the **only** non-Compose consumer of the database. It resolves it through `DatabaseProvider.require()` and receives nothing through Koin (there is no global Koin; `startKoin` appears nowhere in the tree).

---

## Pattern Assignments

### Group A — Schema & migration

#### `ReminderRule.sq` (migration/schema, CRUD)

**Analog:** `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq`

**File preamble — copy this convention verbatim; never add a PRAGMA** (`ServiceEntry.sq:1-4`):
```sql
-- WAL is deliberately NOT set in this file: every unlabeled statement here is replayed inside
-- the generated Schema.create() through driver.execute(), and `PRAGMA journal_mode=wal` returns
-- a row — which the SQLiter non-query path rejects, aborting the first launch on iOS. The driver
-- factories establish WAL on the first connection instead (see com.vehicare.data.db.DriverFactory).
```

**DDL + query naming** (`ServiceEntry.sq:6-16, 102-129, 148-149`) — `INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT`, units in column names, camelCase named queries, and the `lastInsertId` query:
```sql
CREATE TABLE service_entry (
    id                    INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    vehicle_id            INTEGER NOT NULL REFERENCES vehicle(id),
    service_date_epoch_ms INTEGER NOT NULL,   -- UTC start-of-day millis of the civil date
    mileage_km            INTEGER NOT NULL,   -- unit in the name (PITFALLS SQL 2)
    ...
);

insertEntry:
INSERT INTO service_entry(vehicle_id, service_date_epoch_ms, mileage_km, service_type, cost_rupiah, notes, workshop, mechanic)
VALUES (?, ?, ?, ?, ?, ?, ?, ?);

deleteEntry:
DELETE FROM service_entry WHERE id = ?;

lastInsertId:
SELECT last_insert_rowid();
```

**Phase 3 DDL** — verified text at `03-RESEARCH.md:630-644` (one table + one index). Copy verbatim; do not re-derive:
```sql
CREATE TABLE reminder_rule (
    id                  INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    vehicle_id          INTEGER NOT NULL REFERENCES vehicle(id),
    service_type        TEXT    NOT NULL,
    interval_km         INTEGER,
    interval_months     INTEGER,
    lead_km             INTEGER,
    lead_days           INTEGER,
    enabled             INTEGER NOT NULL DEFAULT 1,
    created_at_epoch_ms INTEGER NOT NULL,
    notified_cycle_key  TEXT
);

CREATE INDEX reminder_rule_vehicle ON reminder_rule(vehicle_id);
```

**Queries to include** (shape only — the planner owns names): per-vehicle `selectAll`, `selectById`, `insert`, `update`, `delete`, `deleteRulesForVehicle`, `lastInsertId`, plus the two flat baseline reads. The baseline join must use the `service_entry_type` child table, not the `service_entry.service_type` mirror (`ServiceEntry.sq:38-47` documents why the mirror is not authoritative), and it must rely on the single-`MAX()` bare-column behaviour already used by `selectLearnedTypes` (`ServiceEntry.sq:92-100`):
```sql
selectLearnedTypes:
SELECT label, MAX(entry_id) AS last_used_id
FROM service_entry_type
GROUP BY lower(label)
ORDER BY last_used_id DESC;
```

**Non-negotiable details:**
- DDL in `ReminderRule.sq` and `migrations/3.sqm` must be **structurally identical**; `verifyCommonMainVehiCareDatabaseMigration` compares them (`2.sqm:3-5`).
- Index name must be globally unique: `reminder_rule_vehicle`.
- `enabled` is `INTEGER`, mapped with `!= 0L` in the mapper — the repo has **no** `ColumnAdapter` and adding one for a single boolean is a second mechanism for no gain.
- `notified_cycle_key` is nullable and the "notify once" update must be **conditional** (`UPDATE … WHERE notified_cycle_key IS NULL OR notified_cycle_key <> ?`) so the affected-row count is the atomic decision (`03-RESEARCH.md:655-670`).

#### `migrations/3.sqm` (migration, CRUD)

**Analog:** `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/2.sqm`

**The header convention is load-bearing** (`2.sqm:1-8`):
```sql
-- Migrates v2 (single service_type column) → v3 (normalized service types). File name = the
-- version being upgraded FROM.
-- The DDL below must stay structurally identical to the CREATE TABLE statement for
-- service_entry_type in ServiceEntry.sq — verifyCommonMainVehiCareDatabaseMigration compares
-- the two, and a drifted column order or constraint fails the build.
```

Phase 3 differs from Phase 2 in one way: **no backfill**. The migration is the `CREATE TABLE` + `CREATE INDEX` only (additive, so no `ALTER TABLE` and none of the SQLite 3.18 rewrite hazards). `shared/src/commonMain/sqldelight/databases/` currently holds `1.db`, `2.db`, `3.db`, so the migration is `3.sqm` and the regenerated baseline is `4.db`.

#### `databases/4.db` (generated baseline)

**Analog:** `databases/3.db` — a build output that is **committed**. Regenerate, commit, never hand-edit:
```bash
./gradlew generateCommonMainVehiCareDatabaseSchema   # writes 4.db
./gradlew verifyCommonMainVehiCareDatabaseMigration
git add shared/src/commonMain/sqldelight/databases/4.db
```
CI already enforces both (`ci.yml:31-39`): `verifyCommonMainVehiCareDatabaseMigration`, then `generate…` + `git diff --exit-code -- …/databases`.

---

### Group B — Domain

#### `domain/ReminderRule.kt` (model, transform)

**Analog:** `domain/ServiceSummary.kt` / `domain/ServiceEntry.kt`

A plain `data class` with no generated type and a KDoc that states the invariant (`ServiceSummary.kt:1-13`):
```kotlin
package com.vehicare.domain

/**
 * The aggregate a vehicle's service log is summarised by (D-32).
 *
 * Comes from one SQL aggregate (`COUNT(*)` + `MAX(service_date_epoch_ms)`, PITFALLS SQL 3), so the
 * vehicle Detail summary row never triggers an N+1 read. [lastServiceEpochMs] is `null` exactly
 * when [entryCount] is 0.
 */
data class ServiceSummary(
    val entryCount: Int,
    val lastServiceEpochMs: Long?,
)
```
`ReminderRule` carries `id`, `vehicleId`, `serviceType`, nullable `intervalKm` / `intervalMonths` / `leadKm` / `leadDays`, `enabled: Boolean`, `createdAtEpochMs`, `notifiedCycleKey: String?`. Booleans become `Boolean` **at this boundary** (the mapper does `enabled != 0L`); no generated `Long` leaks upward.

#### `domain/ReminderStatus.kt` (pure calculation, transform)

**Analog:** `util/ServiceDates.kt` for shape; `domain/ServiceSummary.kt` for a result type.

The repo's pure-object convention (`ServiceDates.kt:32-51`) — an `object`, no Compose, no resources, no database:
```kotlin
object ServiceDates {

    /** The user's current wall-clock date in the device's zone. */
    fun today(): LocalDate =
        Clock.System.todayIn(TimeZone.currentSystemDefault())

    /** "19 Oct 2026" — the app's one display format for service dates (D-33). */
    fun display(day: LocalDate): String =
        "${day.day} ${MonthNames.ENGLISH_ABBREVIATED.names[day.month.number - 1]} ${day.year}"
}
```
Differences that must be deliberate:
- **`now` is a parameter**, never `Clock.System.now()` inside. Research calls mutating "today" from anything but an injected clock an anti-pattern (`03-RESEARCH.md:428`); `ServiceDates.today()` is allowed to read the clock because it *is* the clock seam, `ReminderStatus` is not.
- It returns a **structured** result (state + driving axis + remaining/elapsed amount + baseline), not a finished sentence. The screen resolves `Res.string.reminder_*`, the notification builder resolves the same resources through `getString`. This keeps `commonMain` free of Compose Resources while satisfying C-28's "one vocabulary".
- It also produces the cross-axis `urgency = elapsed / interval` key and the deterministic tie-break comparison (D-57). The ordering decision lives here, not in the ViewModel, so the dashboard and the Reminders list cannot disagree.
- A disabled rule returns the `Turned off` descriptor, **not** a due state (D-43); a distance-only rule with no entry at all returns the unevaluable state, never an invented odometer (D-49).

#### `domain/ReminderInterval.kt` (sentence assembly, transform)

**Analog:** `util/RupiahFormat.kt:14-43`

Pure object, private helper, no locale API, documented reason (`RupiahFormat.kt:3-13`):
```kotlin
/**
 * The app's one numeric formatting seam for money and mileage.
 *
 * Costs are whole Rupiah with dot thousands separators and no decimals (D-29), and mileages use
 * the same dot grouping (UI-SPEC §Typography). Both formats call the single private [groupDigits]
 * helper so they cannot drift apart — never format a number anywhere else in the app.
 *
 * No locale API is used on purpose: one pure-Kotlin implementation renders identically on Android
 * and iOS, where a platform formatter could pick a comma separator or insert a non-breaking space
 * on one platform only (T-02-02-03).
 */
object RupiahFormat {
    fun formatRupiah(amount: Long): String = "Rp " + groupDigits(amount)
    fun formatMileage(km: Long): String = groupDigits(km) + " km"
}
```
`ReminderInterval` chooses among `reminder_interval_km` / `_months` / `_both` and must reuse `RupiahFormat.formatMileage` for the number — never a second grouping implementation. The `·` separator, where used, is a **code constant** (U+00B7 with single spaces), the `vehicleMetaLine` precedent (`VehicleCard.kt:37-40`).

---

### Group C — Data layer

#### `data/ReminderRepository.kt` (repository interface, CRUD + streaming)

**Analog:** `data/ServiceEntryRepository.kt:1-67`

The contract convention — no generated type in any signature, each KDoc names the guarantee (`ServiceEntryRepository.kt:8-32`):
```kotlin
/**
 * The only service-log storage contract the UI layer may use.
 *
 * No generated SQLDelight type appears in these signatures on purpose: consumers observe domain
 * models and never touch a driver, a query, or a row type.
 */
interface ServiceEntryRepository {

    /**
     * The vehicle's full service history in the requested [sort] order (D-33/D-34). Both orders
     * are deterministic: equal keys break on `id DESC`. Emits again whenever the tables change.
     */
    fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>>

    /** … */
    suspend fun save(entry: ServiceEntry): Long
    suspend fun delete(id: Long)
}
```
Phase 3 methods: `observeRules(vehicleId): Flow<List<ReminderRule>>`, `observeRule(vehicleId, ruleId): Flow<ReminderRule?>`, `observeDashboard(): Flow<List<VehicleDashboard>>` (one flat-query combine, no N+1), `save(rule): Long`, `setEnabled(id, enabled)`, `delete(id)`, and the once-per-cycle bookkeeping call used by the worker (`markNotifiedIfCycleChanged(...): Boolean` — returns whether this caller won the race).

#### `data/ReminderRepositoryImpl.kt` (repository impl, CRUD + streaming)

**Analog:** `data/ServiceEntryRepositoryImpl.kt`

**Constructor + serialized lane** (`ServiceEntryRepositoryImpl.kt:37-41`):
```kotlin
internal class ServiceEntryRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val photoDeleter: suspend (String?) -> Unit = {},
) : ServiceEntryRepository {
```
The lane is injected, not referenced inline, and **reads ride it too** (`:23-26`). The same `limitedParallelism(1)` guard is mandatory here: the worker and the UI both touch `NativeSqliteDriver`.

**Flow mapping + combine** (`:43-60`, `:88-92`):
```kotlin
override fun observeHistory(vehicleId: Long, sort: HistorySort): Flow<List<ServiceEntry>> {
    val query = when (sort) { … }
    val entries = query.asFlow().mapToList(ioDispatcher)
    val types = database.serviceEntryQueries.selectTypesForVehicle(vehicleId)
        .asFlow()
        .mapToList(ioDispatcher)
    return combine(entries, types) { rows, typeRows ->
        val byEntry = typeRows.groupBy({ it.entry_id }, { it.label })
        rows.map { row -> row.toDomain().copy(serviceTypes = byEntry[row.id] ?: listOf(row.service_type)) }
    }
}

override fun observeSummary(vehicleId: Long): Flow<ServiceSummary> =
    database.serviceEntryQueries.selectSummary(vehicleId)
        .asFlow()
        .mapToOne(ioDispatcher)
        .map { row -> row.toDomain() }
```
The dashboard is the same technique at a larger arity: `combine(vehicles, rules, typeBaselines, vehicleStats)` → one domain list. Do **not** write a `LEFT JOIN`; minSdk 26's SQLite 3.18 has no window functions (`ServiceEntry.sq:92-95` documents the constraint) and a correlated subquery multiplies nullable columns.

**Write: one transaction, id from `lastInsertId()`** (`:124-141`):
```kotlin
override suspend fun save(entry: ServiceEntry): Long = withContext(ioDispatcher) {
    database.transactionWithResult {
        val params = entry.toBindParameters()
        val id = if (entry.id == 0L) {
            database.serviceEntryQueries.insertEntry(…)
            // Same connection, same transaction: no separate connection can race the id.
            // The id is fetched back via last_insert_rowid() — the minSdk 26-safe way.
            database.serviceEntryQueries.lastInsertId().executeAsOne()
        } else {
            database.serviceEntryQueries.updateEntry(…)
            entry.id
        }
        …
        id
    }
}
```
`enabled` toggling and `notified_cycle_key` updates are single-statement `UPDATE`s inside `withContext(ioDispatcher)`; the conditional update's **affected-row count** is the once-per-cycle latch, never an in-memory set.

#### `data/ReminderRuleEntityMapper.kt` (mapper, transform) — **see C-1**

**Analog:** `data/ServiceEntryEntityMapper.kt`

**Alias imports keep generated row types visibly boxed in `data/`** (`:3-10`):
```kotlin
import com.vehicare.db.SelectLearnedTypes
import com.vehicare.db.SelectSummary
import com.vehicare.db.Service_entry as ServiceEntryEntity
import com.vehicare.db.Service_part as ServicePartEntity
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.domain.ServiceSummary
```

**`toDomain()` + bind-parameter class** (`:20-32`, `:61-83`):
```kotlin
internal fun ServiceEntryEntity.toDomain(): ServiceEntry = ServiceEntry(
    id = id,
    vehicleId = vehicle_id,
    dateEpochMs = service_date_epoch_ms,
    mileageKm = mileage_km,
    serviceTypes = listOf(service_type),
    costRupiah = cost_rupiah,
    …
)

/**
 * The eight non-id bind values of a `service_entry` row, in `ServiceEntry.sq` query order.
 */
internal class ServiceEntryBindParameters(
    val vehicleId: Long,
    val dateEpochMs: Long,
    …
)

internal fun ServiceEntry.toBindParameters(): ServiceEntryBindParameters = ServiceEntryBindParameters(…)
```
Phase 3 maps `enabled != 0L` here (the only place the `Long` becomes `Boolean`) and mirrors every nullable column 1:1.

#### `data/VehicleRepositoryImpl.kt` (MODIFIED — D-64 cascade)

**Analog:** itself, `delete` (`:76-90`):
```kotlin
override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
    val photoPaths = database.transactionWithResult {
        val vehiclePhoto = database.vehicleQueries.selectById(id).executeAsOneOrNull()?.photo_path
        val servicePhotos = database.serviceEntryQueries
            .selectPhotoPathsForVehicle(id)
            .executeAsList()
        database.serviceEntryQueries.deletePhotosForVehicle(id)   // children first
        database.serviceEntryQueries.deletePartsForVehicle(id)
        database.serviceEntryQueries.deleteTypesForVehicle(id)
        database.serviceEntryQueries.deleteEntriesForVehicle(id)
        database.vehicleQueries.delete(id)
        listOfNotNull(vehiclePhoto) + servicePhotos
    }
    photoPaths.forEach { path -> photoDeleter(path) }
}
```
Phase 3 inserts **one** line before `vehicleQueries.delete(id)`: `database.reminderRuleQueries.deleteRulesForVehicle(id)`. Do not add `ON DELETE CASCADE` — foreign keys are never enabled (`ServiceEntry.sq:38-39`), so a cascade clause would be decorative, exactly as the existing comment says.

---

### Group D — Common platform seams

#### `data/db/DatabaseProvider.kt` (provider, wiring)

**Analog:** `data/db/DatabaseFactory.kt` (the invariant) + `PlatformModule.android.kt` (the wiring).

`DatabaseFactory.kt:6-15` already states the rule this object enforces:
```kotlin
/**
 * Owns the process-wide [VehiCareDatabase] instance.
 *
 * SQLDelight drivers are not safe to instantiate more than once against the same file, so the
 * Koin graph builds the driver exactly once and hands it here. Nothing else in the app may
 * construct one — every consumer goes through the repository instead.
 */
class DatabaseFactory(driver: SqlDriver) {
    val database: VehiCareDatabase = VehiCareDatabase(driver)
}
```
`DatabaseProvider` becomes the new owner (research's verified code, `03-RESEARCH.md:308-322`):
```kotlin
object DatabaseProvider {
    private var holder: DatabaseFactory? = null

    /** Called exactly once per process, from each platform's entry point, before Compose starts. */
    fun initialize(factory: DatabaseFactory) {
        check(holder == null) { "DatabaseProvider already initialised" }
        holder = factory
    }

    fun require(): VehiCareDatabase =
        holder?.database ?: error("DatabaseProvider.initialize() was never called")
}
```
`check(holder == null)` is the guard against the phase's #1 failure: the worker constructing a second database and silently breaking `asFlow()` notification (research calls this out as the single most consequential constraint).

#### `notifications/NotificationScheduler.kt` (platform seam, event-driven)

**Analog:** `data/db/DriverFactory.kt`

The established `expect`/`actual` shape (`DriverFactory.kt:16-40`):
```kotlin
@Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

/**
 * Platform entry point for constructing a [DriverFactory].
 *
 * @param platformContext the Android application `Context`. Ignored on iOS. Typed [Any] on
 *   purpose: no Android-only type may leak into shared code.
 */
expect fun createDriverFactory(
    platformContext: Any? = null,
    databaseName: String = defaultDatabaseName,
): DriverFactory
```
Phase 3's fourth seam copies the *factory* exactly, but makes the seam itself an interface:
```kotlin
interface NotificationScheduler {
    suspend fun permissionState(): NotificationPermission
    suspend fun scheduleTimeBased(due: List<ScheduledReminder>)
    suspend fun triggerImmediateIfOverdue(due: List<DueReminder>)
    suspend fun cancel(ruleIds: List<Long>)
    fun evaluateNow()
    suspend fun openNotificationSettings()
}

expect fun createNotificationScheduler(platformContext: Any? = null): NotificationScheduler
```
Two rules from the analog: (a) all platform types stay behind `Any?`, (b) the platform factory casts and errors with an actionable message — never a silent fallback (see `DriverFactory.android.kt:34-41`). The interface (rather than an `expect class`) is what lets `commonTest` supply a fake scheduler and assert the permission gate and the three time-path cases without a device; the implementations are `internal class AndroidNotificationScheduler` / `internal class IosNotificationScheduler`. `evaluateNow()` is deliberately **not** suspend: the foreground sweep must not be a coroutine a composition can cancel between the claim and the post.

#### `notifications/NotificationPermission.kt` (platform seam, request-response)

**Analog:** `DriverFactory.kt` for the class/factory shape; `MainViewController.kt` for the composition entry.

Android's runtime prompt is only constructible from a composition (`rememberLauncherForActivityResult`), iOS's is a plain suspend call, so this seam needs **both** a plain class and a `@Composable expect`:
```kotlin
internal class NotificationPermissionRequester internal constructor(
    private val request: (onResult: (NotificationPermission) -> Unit) -> Unit,
) {
    fun request(onResult: (NotificationPermission) -> Unit)
}

@Composable
internal expect fun rememberNotificationPermissionRequester(
    scheduler: NotificationScheduler,
): NotificationPermissionRequester
```
This is a second *composition-scoped* seam, not a second delivery seam — keeping it separate is what lets `NotificationScheduler` stay Compose-free and therefore callable from the worker (`03-RESEARCH.md:352-366`).

---

### Group E — `androidMain`

#### `notifications/NotificationScheduler.android.kt` (platform impl, event-driven)

**Analog:** `data/db/DriverFactory.android.kt:34-41`
```kotlin
actual fun createDriverFactory(platformContext: Any?, databaseName: String): DriverFactory =
    DriverFactory(
        context = platformContext as? Context ?: error(
            "Android driver creation requires an application Context. " +
                "Load the Android platform module so the Koin graph can supply one.",
        ),
        databaseName = databaseName,
    )
```
Copy the cast + `error(...)` shape. The scheduler actual holds the `Context` and delegates to `ReminderNotifications`; on Android `scheduleTimeBased` is a refresh/no-op and `triggerImmediateIfOverdue` enqueues one unique one-shot `WorkRequest` (D-52 — no `AlarmManager`, no `SCHEDULE_EXACT_ALARM`, no `BOOT_COMPLETED`).

#### `notifications/NotificationPermission.android.kt` (platform impl, request-response)

**Analog:** `DriverFactory.android.kt` (context cast) + `MainActivity.kt` (the only Compose host).

`state()` reads `context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)` — minSdk is 26, so no compat shim. `@Composable actual fun rememberNotificationPermissionRequester()` uses `rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())` and returns a requester with a completion callback; the suspend `state()` is re-read on every `Lifecycle.Event.ON_RESUME` (permission status is not reactive — D-61).

#### `work/ReminderWorker.kt` (worker, batch + event-driven) — **NO ANALOG**

There is no `Worker`, `Service`, or `BroadcastReceiver` anywhere in the tree. Structural precedent only:

- Constructor-injected collaborator + serialized I/O lane from `ServiceEntryRepositoryImpl.kt:37-41`.
- `DatabaseProvider.require()` instead of Koin (the worker has no container).
- All calculation comes from `ReminderStatus`; the worker only reads, decides "notify?", calls `markNotifiedIfCycleChanged`, and posts (`03-RESEARCH.md:428-431`).

```kotlin
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val db = DatabaseProvider.require()          // never construct a driver here
        // evaluate → conditional UPDATE (affected rows == 1 wins) → notify
        return Result.success()
    }
}
```
Scheduling is `enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)` — one periodic job for the whole app, never one per rule (`03-RESEARCH.md:426`).

#### `notifications/ReminderNotifications.kt` (utility, event-driven)

**Analog:** `DriverFactory.android.kt:15-31` for the create-once side-effect + private companion constants:
```kotlin
actual fun createDriver(): SqlDriver {
    val driver = AndroidSqliteDriver(VehiCareDatabase.Schema, context, databaseName)
    driver.executeQuery(
        identifier = null,
        sql = PRAGMA_JOURNAL_MODE_WAL,
        mapper = { cursor -> cursor.next() },
        parameters = 0,
    )
    return driver
}

private companion object {
    private const val PRAGMA_JOURNAL_MODE_WAL: String = "PRAGMA journal_mode=wal"
}
```
Phase 3's helper: `createNotificationChannel(context)` (called from `VehiCareApp.onCreate`, **before** any notification — a missing channel silently drops on Android 8+), `buildNotification(...)` using `notification_title` + the `ReminderStatus` sentence, and a per-rule notification id (`ruleId`, never a constant). Channel name from `Res.string.notification_channel_name` via `getString`; `getString` is suspend on CMP 1.12.1, hence the worker's `suspend doWork()`.

---

### Group F — `iosMain`

#### `notifications/NotificationScheduler.ios.kt` / `NotificationPermission.ios.kt`

**Analog:** `data/db/DriverFactory.ios.kt:23-25`
```kotlin
@Suppress("UNUSED_PARAMETER")
actual fun createDriverFactory(platformContext: Any?, databaseName: String): DriverFactory =
    DriverFactory(databaseName)
```
iOS ignores the context and needs no cast. Both files are `@OptIn(ExperimentalForeignApi::class)` because every `UNUserNotificationCenter` call is ObjC interop. Time-based rules are pre-scheduled with `UNCalendarNotificationTrigger` (next occurrence only — iOS caps pending notifications at 64); mileage rules run through `triggerImmediateIfOverdue()` on foreground.

#### `notifications/IosNotificationDelegate.kt` (delegate, event-driven) — **NO ANALOG**

No `AppDelegate`, no `UNUserNotificationCenterDelegate` exists. Structural precedent: `MainViewController.kt:1-11`, the platform entry point that is the *only* place iOS wiring happens:
```kotlin
/**
 * The iOS host: wraps the shared [App] composable in a `UIViewController` for SwiftUI to present.
 *
 * No platform-specific Compose theme or UIKit styling is applied here — that would fork the UI (D-17).
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
```
`IosNotificationDelegate.install()` must run **before** Compose/Koin initialization (research Pitfall 5), store the vehicle id as the notification **identifier** (`"vehicle:<id>"`, not `userInfo` — avoids `NSDictionary`/`NSNumber` bridging), and publish taps into a one-slot `MutableStateFlow<Long?>` that `App.kt` collects.

#### `di/PlatformModule.ios.kt` (DI, wiring)

**Analog:** `di/PlatformModule.android.kt:21-24`
```kotlin
fun platformModule(androidContext: Context): Module = module {
    single<Context> { androidContext.applicationContext }
    single<DriverFactory> { createDriverFactory(get<Context>(), defaultDatabaseName) }
}
```
Phase 3 iOS twin (no context parameter):
```kotlin
fun iosPlatformModule(): Module = module {
    single<NotificationScheduler> { createNotificationScheduler() }
}
```
Android's version gains `single<NotificationScheduler> { createNotificationScheduler(get<Context>()) }` and **loses** the `DriverFactory` line (C-4).

#### `MainViewController.kt` (MODIFIED — host, wiring)

**Analog:** itself (`:11`). Becomes the iOS construction point, in the same order Android uses:
```kotlin
fun MainViewController(): UIViewController {
    DatabaseProvider.initialize(DatabaseFactory(createDriverFactory().createDriver()))
    return ComposeUIViewController { App(iosPlatformModule()) }
}
```
`App(platformModule)` already accepts a `Module` parameter (`App.kt:61`) — no new plumbing.

---

### Group G — Wiring, shell & resources

#### `di/AppModule.kt` (MODIFIED)

**Analog:** itself (`:33-65`). Replace the database block and add the two repositories/VMs:
```kotlin
val appModule = module {
    single {
        val driverFactory = getOrNull<DriverFactory>() ?: createDriverFactory()   // DELETE (C-3)
        DatabaseFactory(driverFactory.createDriver())                            // DELETE
    }
    single<VehiCareDatabase> { get<DatabaseFactory>().database }                  // → DatabaseProvider.require()

    single<VehicleRepository> { VehicleRepositoryImpl(get(), photoDeleter = PhotoStorage::delete) }
    single<ServiceEntryRepository> { ServiceEntryRepositoryImpl(get(), photoDeleter = PhotoStorage::delete) }
    // Phase 3:
    single<ReminderRepository> { ReminderRepositoryImpl(get()) }

    viewModel { VehicleListViewModel(get()) }                                     // signature may change
    viewModel { (vehicleId: Long) -> ReminderSummaryViewModel(get(), vehicleId) }
    viewModel { (vehicleId: Long) -> RemindersViewModel(get(), vehicleId) }
    viewModel { (vehicleId: Long, ruleId: Long) -> ReminderEditViewModel(get(), vehicleId, ruleId) }
}
```
Typed route parameters stay destructured at the call site, exactly as `ServiceEntryEditViewModel` does (`AppModule.kt:57-58`).

#### `ui/navigation/Routes.kt` (MODIFIED)

**Analog:** itself (`:27-37`) — `@Serializable` data classes, `0L` = add:
```kotlin
/** The shared Add/Edit service entry form. `entryId == 0L` means "add"; any `entryId > 0` means "edit [entryId]". */
@Serializable
data class ServiceEntryEditRoute(val vehicleId: Long, val entryId: Long = 0L)
```
Phase 3 adds `RemindersRoute(vehicleId)` and `ReminderEditRoute(vehicleId, ruleId = 0L)` — same shapes, same default-means-add convention.

#### `App.kt` (MODIFIED)

**Analog:** itself. Route registration + shell snackbar (`:97-107`, `:118-142`, `:170-184`):
```kotlin
NavHost(navController, startDestination = VehicleListRoute, modifier = Modifier.padding(innerPadding)) {
    composable<VehicleListRoute> {
        VehicleListScreen(
            onAddVehicle = { navController.navigate(VehicleEditRoute()) },
            onOpenVehicle = { id -> navController.navigate(VehicleDetailRoute(id)) },
        )
    }
    composable<VehicleDetailRoute> { backStackEntry ->
        val detailRoute = backStackEntry.toRoute<VehicleDetailRoute>()
        val deletedMessage = stringResource(Res.string.snackbar_vehicle_deleted)
        VehicleDetailScreen(
            vehicleId = detailRoute.id,
            onOpenServiceHistory = { navController.navigate(ServiceHistoryRoute(detailRoute.id)) },
            onDeleted = {
                scope.launch { snackbarHostState.showSnackbar(deletedMessage) }
                navController.popBackStack()
            },
        )
    }
}
```
Phase 3 adds two `composable<>` blocks, one `onOpenReminders` callback on Detail, one shell-scope "Reminder deleted." message, and a `LaunchedEffect` that collects the notification-tap `Flow<Long?>` and navigates to `VehicleDetailRoute(id)` (a tap while already on that vehicle must be a no-op).

#### `composeResources/values/strings.xml` (MODIFIED)

**Analog:** itself. Phase 2's block starts at `:70` and the file ends at `:138`:
```xml
<!-- Phase 2: service log (UI-SPEC §Copywriting Contract) -->
<string name="title_service_history">Service history</string>
…
<plurals name="service_summary_entries">
    <item quantity="one">%1$d entry</item>
    <item quantity="other">%1$d entries</item>
</plurals>
</resources>
```
Append the Phase 3 block **before** `</resources>` with the same section comments. The complete implementation-ready set is `03-UI-SPEC.md:445-521` (43 entries, including 4 plurals). Reuse `field_service_type`, `action_cancel`, `action_try_again`, `field_required_marker`, `cd_back`, `title_vehicle_fallback` — do not re-author them. Preset service-type labels stay code constants (`ServiceTypePresets.ALL`), not resources.

#### `gradle/libs.versions.toml` + `shared/build.gradle.kts` (MODIFIED)

**Analog:** themselves. There is **no** `work` entry today (versions block `:1-25`, libraries `:27-62`), and `androidMain.dependencies` is a five-line block (`shared/build.gradle.kts:45-51`):
```kotlin
androidMain.dependencies {
    implementation(libs.compose.uiToolingPreview)
    implementation(libs.compose.uiTooling)
    implementation(libs.androidx.activity.compose)
    implementation(libs.sqldelight.android.driver)
    implementation(libs.filekit.dialogs.compose)
}
```
Add `androidx-work = "2.12.0"` + `androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "androidx-work" }`, then one `implementation` line in `androidMain`. **Do not** add `work-runtime-ktx` — it has been empty since 2.9.0 (`03-RESEARCH.md:120-124`). The sqldelight block (`:98-106`) needs no change; it already points at `databases/`.

#### `androidApp/src/main/AndroidManifest.xml` (MODIFIED)

**Analog:** itself (`:1-22`) — and the key fact is what is **absent**: no `<uses-permission>` at all.
```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity android:exported="true" android:name=".MainActivity">
```
Phase 3 adds `<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>` (the first permission in the app), `android:name=".VehiCareApp"` on `<application>`, and `android:launchMode="singleTop"` on the activity (so a notification tap reuses the instance and lands in `onNewIntent`). No `SCHEDULE_EXACT_ALARM`, no receiver (D-52).

#### `androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt` (Application, wiring)

**Analog:** `MainActivity.kt:17-26` for the host-shell role (`role-match`):
```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(remember { platformModule(applicationContext) })
        }
    }
}
```
`VehiCareApp` is the same "supply the platform once" idea at process scope:
```kotlin
class VehiCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)                                  // before any notification
        DatabaseProvider.initialize(
            DatabaseFactory(createDriverFactory(this, defaultDatabaseName).createDriver()),
        )
    }
}
```
This ordering (channel → database → Compose) is the whole reason the class exists; do not move initialization into `MainActivity`.

#### `androidApp/src/main/kotlin/com/vehicare/MainActivity.kt` (MODIFIED)

**Analog:** itself (`:17-26`). Add:
- cold start: read `intent.getLongExtra("vehicleId", -1L)` and push into the shared tap flow;
- warm start: `override fun onNewIntent(intent: Intent)` + `setIntent(intent)` + the same push;
- **do not** construct another database or module — `platformModule(applicationContext)` stays as-is.

#### `iosApp/iosApp/iOSApp.swift` (MODIFIED) / `AppDelegate.swift` (NEW) — **AppDelegate has NO ANALOG**

**Analog:** `iOSApp.swift:1-10` is the whole current host:
```swift
@main
struct iOSApp: App {
    var body: some Scene {
        WindowGroup { ContentView() }
    }
}
```
Phase 3 adds `@UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate` and a new `AppDelegate.swift` that calls `IosNotificationDelegateKt.install()` (Kotlin) in `application(_:didFinishLaunchingWithOptions:)` **before** the Compose view is created. `ContentView.swift:5-11` stays untouched — it already calls `MainViewControllerKt.MainViewController()`.

---

### Group H — Reminders UI

#### `ui/reminders/RemindersScreen.kt` (screen, streaming)

**Analog:** `ui/service/ServiceHistoryScreen.kt`

**Scaffold + title bar + state matrix + FAB overlay** (`:59-96`, `:175-186`):
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHistoryRoute(
    vehicleId: Long,
    onAddService: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ServiceHistoryViewModel = koinViewModel(parameters = { parametersOf(vehicleId) }),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.title_service_history), style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (state) { … }

            // FAB overlay outside the when so the add control is present in every state (C-5, D-36)
            FloatingActionButton(
                onClick = onAddService,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(Res.string.cd_add_service))
            }
        }
    }
}
```
**Keyed list with the 64dp bottom band** (`:131-147`):
```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 64.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
) {
    items(state.entries, key = { entry -> entry.id }) { entry ->
        ServiceHistoryRow(entry = entry, onClick = { onOpenEntry(entry.id) })
    }
}
```
Phase 3 differences: the permission status row is the **first** `item` (Content state only), rows are `ReminderCard` keyed by `rule.id`, the FAB label is `action_add_reminder` / `cd_add_reminder`, empty state uses `EmptyState`, and the screen hosts the `NotificationsPermissionDialog`. Error/loading/empty copy comes from the new strings.

#### `ui/reminders/RemindersViewModel.kt` (ViewModel, streaming)

**Analog:** `ui/service/ServiceHistoryViewModel.kt`

**Sealed state + refresh + error-inside-the-inner-flow** (`:21-34`, `:89-106`):
```kotlin
sealed interface ServiceHistoryUiState {
    data object Loading : ServiceHistoryUiState
    data object Empty : ServiceHistoryUiState
    data class Content(val entries: List<ServiceEntry>) : ServiceHistoryUiState
    data class Error(val message: String?) : ServiceHistoryUiState
}

internal fun observeHistoryUiState(…): Flow<ServiceHistoryUiState> =
    sort.flatMapLatest { mode ->
        refresh.flatMapLatest {
            repo.observeHistory(vehicleId, mode)
                .map { entries -> if (entries.isEmpty()) Empty else Content(entries) }
                .catch { emit(Error(message = it.message)) }   // terminal for this observation only
        }
    }
```
Phase 3 keeps the same four states, plus a `togglingIds: Set<Long>` (or per-row busy flag) for optimistic-free switch feedback. `setEnabled` and `delete` go through the repository; the list re-emits from SQLDelight, so **no manual list mutation** on success. Delete failure sets a one-shot flag retired by `onDeleteErrorShown()` (same pattern as `ServiceEntryDetailViewModel:83-133`).

#### `ui/reminders/ReminderEditScreen.kt` (screen, request-response)

**Analog:** `ui/service/ServiceEntryEditScreen.kt`

**Route wrapper: state `when` + one-shot Snackbar effects** (`:96-129`, `:143-210`):
```kotlin
@Composable
fun ServiceEntryEditRoute(
    vehicleId: Long,
    entryId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ServiceEntryEditViewModel = koinViewModel(parameters = { parametersOf(vehicleId, entryId) }),
) {
    val state = viewModel.uiState.collectAsState().value
    val isAdd = entryId == 0L
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            val result = snackbarHostState.showSnackbar(saveErrorText, actionLabel = retryLabel)
            viewModel.onSaveErrorShown()
            if (result == SnackbarResult.ActionPerformed) viewModel.save(onSaved = onSaved)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { … }) }, snackbarHost = { SnackbarHost(snackbarHostState) }) { innerPadding ->
        when (val current = state) {
            ServiceEntryEditUiState.Loading -> …
            ServiceEntryEditUiState.NotFound -> …
            is ServiceEntryEditUiState.Ready -> ServiceEntryEditForm(…)
        }
    }
}
```

**Form column: `imePadding()` + one scroll, 24dp between groups, submit last** (`:255-263`, `:461-479`):
```kotlin
Column(
    modifier = Modifier
        .padding(contentPadding)
        .imePadding()
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
        .padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(24.dp),
) {
    …
    Button(
        onClick = onSave,
        enabled = ServiceEntryFormValidation.isSubmittable(values) && !saving && !importing,
        modifier = Modifier.fillMaxWidth(),
    ) { … }
}
```
Phase 3 form: `ServiceTypePicker(selectionMode = Single)`, then two interval `OutlinedTextField`s (numeric keyboard, `sanitize…`), then the two advance-notice fields, then `ReminderToggleRow`, then submit; edit mode adds the destructive `OutlinedButton` **below a 48dp break**, guarded by `ConfirmDeleteDialog`. The required-marker pattern is `buildAnnotatedString` + `withStyle` (`:290-298`), and the inline error uses `supportingText` with `labelLarge` (`:303-312`).

#### `ui/reminders/ReminderEditViewModel.kt` (ViewModel, request-response)

**Analog:** `ui/service/ServiceEntryEditViewModel.kt`

**`isAdd` + synchronous seeding + one-shot flags** (`:74-111`):
```kotlin
class ServiceEntryEditViewModel(
    private val repo: ServiceEntryRepository,
    private val vehicleId: Long,
    private val entryId: Long,
) : ViewModel() {

    private val isAdd: Boolean = entryId == 0L
    private val _uiState = MutableStateFlow<ServiceEntryEditUiState>(
        if (isAdd) ServiceEntryEditUiState.Ready(values = ServiceEntryFormValues(dateEpochMs = ServiceDates.toStorage(ServiceDates.today())))
        else ServiceEntryEditUiState.Loading,
    )
    val uiState: StateFlow<ServiceEntryEditUiState> = _uiState
    private var initialLoadApplied: Boolean = false
```

**Single mutator + double-submit guard + failure flag retire** (`:197-209`, `:241-277`):
```kotlin
fun onFieldChange(update: (ServiceEntryFormValues) -> ServiceEntryFormValues) {
    val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
    _uiState.value = current.copy(values = update(current.values))
}

fun save(onSaved: () -> Unit) {
    val current = _uiState.value as? ServiceEntryEditUiState.Ready ?: return
    if (current.saving || !ServiceEntryFormValidation.isSubmittable(current.values)) return
    _uiState.value = current.copy(saving = true, saveFailed = false)
    viewModelScope.launch {
        try { repo.save(…); clearSaving(); onSaved() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { markSaveFailed() }
    }
}

fun onSaveErrorShown() { … }
```
Phase 3 additions: (a) **after a successful add** (the first rule or any later one), publish the replay-1 first-ask signal; the Reminders screen consumes it and asks the policy whether to show the explanation dialog — only the first add per install actually opens it (D-54 amended 2026-10-23: never at launch, never at screen entry, never on an edit); (b) the repository's own `onRulesChanged` hook does the notification work — the Koin binding calls `NotificationScheduler.cancel(ruleIds)` then `evaluateNow()`, so the ViewModel never touches the scheduler and an edited rule cannot leave a stale trigger (Pitfall 5); (c) the edit path accepts only the first repository emission, exactly as `:104-111` documents.

#### `ui/reminders/ReminderFormValidation.kt` (pure object, transform)

**Analog:** `ui/service/ServiceEntryFormValidation.kt`

**Values data class + error enum + pure object** (`:18-72`):
```kotlin
data class ServiceEntryFormValues(
    val dateEpochMs: Long? = null,
    val mileage: String = "",
    …
)

enum class ServiceEntryFormError { MILEAGE_REQUIRED }

/**
 * Every Add/Edit service entry form rule, as pure functions: no Compose, no Compose Resources,
 * no database.
 */
object ServiceEntryFormValidation {
    fun isSubmittable(values: ServiceEntryFormValues): Boolean =
        values.dateEpochMs != null && values.mileage.isNotBlank() && values.serviceTypes.isNotEmpty()

    fun errorFor(values: ServiceEntryFormValues): ServiceEntryFormError? =
        if (isSubmittable(values)) null else ServiceEntryFormError.MILEAGE_REQUIRED

    fun sanitizeMileage(raw: String): String = raw.filter { it.isDigit() }.take(9)
}
```
Phase 3 rules (C-34): `serviceType` non-blank; **at least one** of `intervalKm` / `intervalMonths` non-blank; every non-blank numeric field parses to `≥ 1`; lead values are optional but `≥ 1` when present; `errorFor` returns one error id for all four numeric fields (`error_interval_min`). `toReminderRule(id, vehicleId, values, createdAtEpochMs)` is the single blank→null boundary, mirroring `toServiceEntry` (`:99-129`).

#### `ui/reminders/components/ReminderCard.kt` (component, request-response)

**Analog:** `ui/service/components/ServiceSummaryRow.kt` for the card anatomy; `ui/vehicles/components/VehicleCard.kt` for the `Card` wrapper.

**Row anatomy** (`ServiceSummaryRow.kt:55-91`):
```kotlin
Row(
    modifier = modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp))
        .background(Mist)
        .clickable(onClick = onClick)
        .semantics(mergeDescendants = true) {}
        .heightIn(min = 72.dp)
        .padding(16.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = …, style = MaterialTheme.typography.titleMedium, color = Ink)
        Text(text = …, style = MaterialTheme.typography.bodyLarge, color = Slate)
    }
    Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null, modifier = Modifier.size(24.dp), tint = Slate)
}
```
**Deliberate divergence (UI-SPEC accessibility):** `ReminderCard` must **not** merge the whole row and must **not** put `clickable` on the outer container, because the row contains a live `Switch` — merging would make the switch untoggleable. Put `clickable` + `semantics(mergeDescendants = true)` on the **text `Column`**, and leave the `Switch` as its own node with `cd_reminder_switch`. This is the one place where copying `ServiceSummaryRow` verbatim would be wrong; the reason is recorded in `03-UI-SPEC.md:530-534`.

#### `ui/reminders/components/ReminderSummaryRow.kt` (component, request-response)

**Analog:** `ui/service/components/ServiceSummaryRow.kt:49-113` — **copy essentially verbatim**, changing only the strings and the meta computation:
```kotlin
@Composable
fun ServiceSummaryRow(summary: ServiceSummary?, onClick: () -> Unit, modifier: Modifier = Modifier) { … }

@Composable
private fun summaryMetaLine(summary: ServiceSummary): String {
    if (summary.entryCount == 0) return stringResource(Res.string.service_summary_none)
    val countPhrase = pluralStringResource(Res.plurals.service_summary_entries, summary.entryCount, summary.entryCount)
    val latest = summary.lastServiceEpochMs ?: return countPhrase
    return countPhrase + " · " + stringResource(Res.string.service_summary_last, ServiceDates.display(latest))
}
```
Reminder meta line: `pluralStringResource(reminder_summary_count, n, n)` + the most urgent due sentence (or `reminder_summary_all_off`). It stays tappable at zero rules, exactly as the service row stays tappable at zero entries (`:40-47`).

#### `ui/reminders/components/ReminderStatusRow.kt` + `PermissionBanner.kt` (components, request-response)

**Analog:** `ServiceSummaryRow.kt:55-66` for the tappable full-width `Mist` row; no banner exists in the app.

Both are the same anatomy as the summary row with a shorter `heightIn` (UI-SPEC: ≥ 56dp, tappable across the full width, `mergeDescendants = true`, decorative chevron). `PermissionBanner` is the home-list item (`role-match`/partial — the first banner in the app), `ReminderStatusRow` is the same content as the first item of the Reminders list. Keep one shared private row body if both are needed; do not duplicate the palette values.

#### `ui/reminders/components/ReminderToggleRow.kt` (component) — **NO ANALOG**

There is no `Switch` anywhere in the tree. Nearest structural precedent is `ui/components/FieldRow.kt:55-88` — a label-leading / value-trailing row with an 8dp gap and a `Hairline` divider:
```kotlin
Row(
    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalAlignment = Alignment.CenterVertically,
) {
    Text(text = label, style = MaterialTheme.typography.labelLarge, color = Slate)
    Text(text = present ?: stringResource(Res.string.value_empty), style = MaterialTheme.typography.bodyLarge, color = if (present == null) Slate else Ink, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
}
```
Compose it as `FieldRow`'s row + M3 `Switch(checked, onCheckedChange)`; `Switch` already supplies `minimumInteractiveComponentSize()`, so the 48dp rule is satisfied without extra padding. Label uses `field_reminder_on`.

#### `ui/reminders/components/NotificationsPermissionDialog.kt` (component, request-response)

**Analog:** `ui/components/ConfirmDeleteDialog.kt:52-99`

**Dialog anatomy** (`:59-99`):
```kotlin
AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Paper,                       // never Material's container tint (C-8)
    title = { Text(text = title, style = MaterialTheme.typography.titleLarge) },
    text = { Text(text = message, style = MaterialTheme.typography.bodyLarge) },
    confirmButton = {
        TextButton(
            onClick = onConfirm,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(text = confirmLabel) }
    },
    dismissButton = {
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.textButtonColors(contentColor = Ink),
        ) { Text(text = stringResource(Res.string.action_cancel)) }
    },
)
```
Phase 3 differences: this is **not** destructive, so both buttons use `Ink` (no `Danger`), labels are `action_turn_on` / `action_not_now` (not `action_cancel`), and `onDismiss` = "Not now" (non-blocking, D-54). Same `Paper` container, same 48dp minimums, same `onDismissRequest = onDismiss`.

---

### Group I — Modified existing UI

#### `ui/vehicles/components/VehicleCard.kt` (MODIFIED — one status line)

**Analog:** itself. The text column is the insertion point (`:91-109`):
```kotlin
Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(text = vehicle.name, style = MaterialTheme.typography.titleMedium, color = Ink)
    val metaLine = vehicleMetaLine(vehicle.make, vehicle.model, vehicle.year)
    if (metaLine.isNotEmpty()) {
        Text(text = metaLine, style = MaterialTheme.typography.bodyLarge, color = Slate)
    }
    val plate = vehicle.plate
    if (!plate.isNullOrBlank()) {
        LicensePlateChip(plate = plate)
    }
}
```
Append the status line **after** the plate block, inside the same `Column` (so the existing 8dp stack and 88dp minimum height are untouched):
```kotlin
Text(
    text = statusLine,                       // "{type} · {due sentence}" + " · +N due"
    style = MaterialTheme.typography.bodyLarge,
    color = if (isOverdue) MaterialTheme.colorScheme.error else Slate,
    maxLines = 1,
    overflow = TextOverflow.Ellipsis,
)
```
`maxLines = 1` is load-bearing (D-59: uniform card height). Do **not** add a `Badge`, icon or container fill. When there is no enabled rule, render `vehicle_no_active_reminders` in `Slate`. The card already has `semantics(mergeDescendants = true)` (`:67`), so the line is announced as part of the card.

#### `ui/vehicles/VehicleListScreen.kt` (MODIFIED)

**Analog:** itself (`:102-121`). The banner is a normal list item **before** the cards:
```kotlin
is VehicleListUiState.Content -> {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(state.items, key = { vehicle -> vehicle.id }) { vehicle ->
            VehicleCard(vehicle = vehicle, onClick = { onOpenVehicle(vehicle.id) })
        }
    }
}
```
Phase 3 inserts `item(key = "permission-banner") { PermissionBanner(...) }` as the first item, **Content state only**, and rewrites the stale `// Rows arrive newest-first (D-16)` comment at `:105` (C-8). The `items` key is already `vehicle.id` — keep it; the list now reorders by urgency, and keys are what stop card state from being recycled across vehicles.

#### `ui/vehicles/VehicleListViewModel.kt` (MODIFIED)

**Analog:** itself (`:42-70`) — keep the state matrix and the refresh/`catch` shape exactly:
```kotlin
val uiState: StateFlow<VehicleListUiState> =
    refresh
        .flatMapLatest {
            repo.observeVehicles()
                .map { vehicles -> if (vehicles.isEmpty()) Empty else Content(vehicles) }
        }
        .catch { emit(VehicleListUiState.Error(message = it.message)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VehicleListUiState.Loading)
```
Only the source changes: the dashboard aggregation (one `Flow<List<VehicleDashboard>>` from the four flat queries) and the urgency ordering. `Content` carries the dashboard rows, and the screen passes each row's `Vehicle` to `VehicleCard` plus the computed status line. The ordering and tie-breaks must come from `ReminderStatus`, never be re-implemented here.

#### `ui/vehicles/VehicleDetailScreen.kt` (MODIFIED)

**Analog:** itself. The reserved summary band (`:313-325`):
```kotlin
DetailFieldBlock(vehicle = vehicle, modifier = Modifier.padding(top = 24.dp))

Spacer(modifier = Modifier.height(48.dp))
ServiceSummaryRow(summary = summary, onClick = onOpenServiceHistory)
Spacer(modifier = Modifier.height(48.dp))
```
Phase 3 puts `ReminderSummaryRow` **after** the service row, inside its own 48dp breaks (D-45: 24px apart, both inside the existing rhythm), and adds `onOpenReminders: () -> Unit` to the route signature (`:102-108`) wired from `App.kt`. Nothing else on this screen changes. The reminder summary read is a second `koinViewModel` with the same route id, mirroring the existing `ServiceSummaryViewModel` usage at `:109-117`.

#### `ui/service/components/ServiceTypePicker.kt` (MODIFIED — C-7)

**Analog:** itself (`:63-99`). The public signature today:
```kotlin
@Composable
fun ServiceTypePicker(
    presets: List<String> = ServiceTypePresets.ALL,
    learned: List<String> = emptyList(),
    selected: List<String> = emptyList(),
    onToggle: (String) -> Unit,
    onAddCustom: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
)
```
Add `selectionMode: SelectionMode = SelectionMode.Multiple` and a `onSelect: (String) -> Unit = onToggle`-style single-select path (or branch inside `renderChip`). The existing call site uses named arguments (`ServiceEntryEditScreen.kt:350-357`), so the default preserves Phase 2 behaviour with no edit at that call site. The chip geometry, 48dp minimum, `Check` glyph and `FlowRow` (`:86-99`, `:141-166`) are reused unchanged — the UI-SPEC explicitly keeps M3's `Role.Checkbox` semantics for the single-select group and documents why (`03-UI-SPEC.md:287`).

---

### Group J — Tests

#### `commonTest/…/ui/reminders/ReminderFormValidationTest.kt` (test, transform)

**Analog:** `commonTest/…/ui/service/ServiceEntryFormValidationTest.kt:17-189`

Pure-object test style — no harness, one behaviour per test, comments naming the rule (`:20-47`, `:181-188`):
```kotlin
class ServiceEntryFormValidationTest {
    @Test
    fun isSubmittableRequiresDateMileageAndType() {
        val valid = ServiceEntryFormValues(dateEpochMs = 1_000_000_000_000L, mileage = "12345", serviceTypes = listOf("Oil change"))
        assertTrue(ServiceEntryFormValidation.isSubmittable(valid))
        assertFalse(ServiceEntryFormValidation.isSubmittable(valid.copy(mileage = "")))
        assertFalse(ServiceEntryFormValidation.isSubmittable(valid.copy(serviceTypes = emptyList())))
    }

    @Test
    fun inputCapsMatchTheUiSpec() {
        assertEquals(60, ServiceEntryFormValidation.MAX_CUSTOM_TYPE)
    }
}
```
Phase 3 must cover: both-empty intervals rejected; each axis alone accepted; both set accepted; `0`, negative and non-numeric input rejected; lead values optional but `≥ 1`; blank→null mapping; `errorFor` returns the single interval error. Every case is pure `commonTest`, so it runs on both targets.

#### `commonTest/…/ui/reminders/ReminderStatusTest.kt` (test, transform)

**Analog:** `commonTest/…/ui/service/ServiceMetaLineTest.kt` + `ServiceDatesTest.kt` — both test a pure function's exact output with no UI.

Cover the four states × two axes, the **exact-zero boundary** (`Due now`), the unevaluable distance-only case, the disabled case (`Turned off`), the lead-window band, and the cross-axis ordering/tie-breaks. Inject a fixed `now` and fixed baselines — never `Clock.System.now()`.

#### `commonTest/…/data/ReminderRuleEntityMapperTest.kt` (test, transform)

**Analog:** `commonTest/…/data/ServiceEntryEntityMapperTest.kt:15-155`

Construct the generated row directly, assert the domain object (`:17-45`), assert absent optionals stay null (`:47-69`), and assert the bind-parameter order (`:96-120`):
```kotlin
@Test
fun mapsEveryColumnOneToOne() {
    val entity = ServiceEntryEntity(id = 7L, vehicle_id = 3L, …)
    assertEquals(ServiceEntry(id = 7L, vehicleId = 3L, …), entity.toDomain())
}
```
Phase 3 adds the `enabled` mapping explicitly: `0L → false`, `1L → true`, and any non-zero `Long → true`.

#### `iosTest/…/data/ReminderMigrationTest.kt` (test, CRUD)

**Analog:** `iosTest/…/data/ServiceEntryMigrationTest.kt:28-149`

**Scratch driver + teardown** (`:30-44`):
```kotlin
private val databaseName = "vehicare-service-entry-migration-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"
private lateinit var driver: SqlDriver
private lateinit var database: VehiCareDatabase

@BeforeTest
fun setUp() {
    driver = createDriverFactory(databaseName = databaseName).createDriver()
    database = VehiCareDatabase(driver)
}

@AfterTest
fun tearDown() {
    driver.close()
    deleteTestDatabaseFiles(databaseName)
}
```
**Version assert + the drop-then-migrate trick** (`:46-69`):
```kotlin
@Test
fun schemaVersionIsThree() {
    assertEquals(3L, VehiCareDatabase.Schema.version)          // Phase 3: this test moves to 4L
}

@Test
fun migratingFromTwoBackfillsTheMirrorLabelAtPositionZero() {
    driver.execute(null, "DROP TABLE service_entry_type", 0).value
    …
    VehiCareDatabase.Schema.migrate(driver, 2, 3).value
}
```
Phase 3's test: assert `Schema.version == 4L`; `DROP TABLE reminder_rule`; insert legacy data; `Schema.migrate(driver, 3, 4)`; assert the table exists and is empty (no backfill), and that re-running is not attempted. Keep the `NSApplicationSupportDirectory` cleanup helper verbatim (`:137-148`).

#### `iosTest/…/data/ReminderRepositoryTest.kt` (test, CRUD)

**Analog:** `iosTest/…/data/ServiceEntryRepositoryTest.kt` — real driver, real repository, no mocks.

The setup is identical to `ServiceEntryDeletionTest.kt:79-88`:
```kotlin
@BeforeTest
fun setUp() {
    Dispatchers.setMain(mainDispatcher)
    driver = createDriverFactory(databaseName = databaseName).createDriver()
    database = VehiCareDatabase(driver)
    repository = ServiceEntryRepositoryImpl(database, photoDeleter = PhotoStorage::delete)
    vehicleRepository = VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)
}
```
Phase 3 cases: insert→observe round trip; edit keeps id; `setEnabled` round trip; ordering of rules; baseline resolution through `service_entry_type` (including the case where the rule's type is **not** position 0 of the latest entry); no-entry fallback (`created_at_epoch_ms`); conditional `markNotifiedIfCycleChanged` returns `true` once and `false` on repeat; a rule edit re-arms the cycle.

#### `iosTest/…/data/ReminderDeletionTest.kt` (test, CRUD)

**Analog:** `iosTest/…/data/ServiceEntryDeletionTest.kt`

**Recording decorator** (`:514-533`):
```kotlin
private class RecordingServiceEntryRepository(
    private val delegate: ServiceEntryRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
) : ServiceEntryRepository by delegate {

    val deleteCalls = mutableListOf<Long>()

    override suspend fun delete(id: Long) {
        deleteCalls += id
        gate?.await()
        failure?.let { throw it }
        delegate.delete(id)
    }
}
```
Cases: delete rule removes only that rule; unknown id is a no-op; ViewModel reports success only after the repository returned (assert inside the `onDeleted` callback, the trick at `:220-243`); second delete while in flight is dropped; failure leaves the rule stored and sets the one-shot flag; **vehicle cascade** removes the vehicle's rules along with entries/parts/photos (`:349-453` is the exact template) while another vehicle's rules survive.

#### `iosTest/…/data/ServiceEntryMigrationTest.kt` (MODIFIED — C-9)

Change exactly one assertion (`:46-49`):
```kotlin
@Test
fun schemaVersionIsThree() {
    assertEquals(3L, VehiCareDatabase.Schema.version)
}
```
→ `assertEquals(4L, …)` and rename the test to `schemaVersionIsFour`. Do not weaken it; this is the only guard that catches a forgotten `3.sqm` before CI does.

---

## Shared Patterns

### 1. Serialized DB lane — never `Dispatchers.IO` inline
**Source:** `data/ServiceEntryRepositoryImpl.kt:37-41` (and `VehicleRepositoryImpl.kt:27-31`)
**Apply to:** every `ReminderRepositoryImpl` method, **including** the `asFlow()` reads, and every write path the worker calls.
```kotlin
private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
```
Rationale in the KDoc: concurrent `NativeSqliteDriver` access aborts the iOS process.

### 2. Mapper boundary — no generated type above `data/`
**Source:** `data/ServiceEntryEntityMapper.kt:5-18`; `data/ServiceEntryRepository.kt:8-13`
**Apply to:** repository, mapper, every ViewModel, every composable. The alias-import trick (`import com.vehicare.db.Service_entry as ServiceEntryEntity`) keeps row types visibly boxed.

### 3. One transaction, id from `lastInsertId()`
**Source:** `data/ServiceEntryRepositoryImpl.kt:124-141`
**Apply to:** `ReminderRepository.save`. `RETURNING` is unavailable at minSdk 26; every table already exposes `lastInsertId: SELECT last_insert_rowid();`.

### 4. Children before parents; files only after rows are durable
**Source:** `data/VehicleRepositoryImpl.kt:76-90`; `data/ServiceEntryRepositoryImpl.kt:184-197`
**Apply to:** rule delete, vehicle cascade. Foreign keys are never enabled (`ServiceEntry.sq:38-39`), so every child delete is explicit and ordered.

### 5. One database per process, reached through `DatabaseProvider`
**Source:** `data/db/DatabaseFactory.kt:6-15`; `di/AppModule.kt:34-37`; `data/db/DriverFactory.android.kt:34-41`
**Apply to:** `VehiCareApp`, `MainViewController`, `AppModule`, `ReminderWorker`. The worker must never construct a driver; `AppModule` must never fall back to `createDriverFactory()`.

### 6. `expect`/`actual` factory with `platformContext: Any?` (the seam itself may be an interface)
**Source:** `data/db/DriverFactory.kt:16-40`; `DriverFactory.android.kt:34-41`; `DriverFactory.ios.kt:23-25`
**Apply to:** `createNotificationScheduler` (factory), `NotificationPermission`, and the permission-requester factory. `NotificationScheduler` itself is a `commonMain` interface implemented by `AndroidNotificationScheduler`/`IosNotificationScheduler`, so `commonTest` can fake it. No Android type crosses into `commonMain`; the Android factory casts and errors with an actionable message; the iOS factory ignores the parameter.

### 7. One-shot failure flags retire with their message
**Source:** `ui/service/ServiceEntryEditScreen.kt:118-129`; `ui/service/ServiceEntryEditViewModel.kt:273-277`; `ui/service/ServiceEntryDetailViewModel.kt:83-133`
**Apply to:** reminder save failure, delete failure, photo-free. `LaunchedEffect(flag)` shows the Snackbar, then calls `on…Shown()` in the same block.

### 8. State matrices that never paint blank
**Source:** `VehicleListViewModel.kt:20-33`; `ServiceHistoryViewModel.kt:21-34`; `ServiceEntryEditViewModel.kt:26-56`
**Apply to:** `RemindersViewModel` (Loading/Empty/Content/Error) and `ReminderEditViewModel` (Loading/NotFound/Ready). No new state names; refine what each renders only.

### 9. Navigation injected, graph owned by `App.kt`
**Source:** `ServiceHistoryScreen.kt:52-66`; `VehicleListScreen.kt:48-63`; `App.kt:97-184`
**Apply to:** `RemindersScreen`, `ReminderEditScreen`, `VehicleDetailScreen`. No composable holds a `NavController`; every destination is a callback parameter.

### 10. FAB as a `Box` overlay outside the state `when`
**Source:** `ServiceHistoryScreen.kt:175-186`; `VehicleListScreen.kt:148-160`
**Apply to:** the Reminders screen's "Add reminder" control. Never the `Scaffold` FAB slot; the overlay is what keeps it present in every state.

### 11. Strings only via `vehicare.shared.generated.resources.Res`
**Source:** `ServiceSummaryRow.kt:29-35, 103-107`; every UI file
**Apply to:** every new composable and both notification builders. No inline literal — including the `·` separator, which is a **code constant**, and preset type labels, which are code constants. Day counts use `pluralStringResource`.

### 12. Locked formatters, one implementation each
**Source:** `util/ServiceDates.kt:46-51` (`display`); `util/RupiahFormat.kt:19-23` (`formatMileage`)
**Apply to:** card status line, rule row, summary row, both notification bodies. `ReminderStatus` returns numbers; the surface formats through these two helpers only.

### 13. Pure validation object + `commonTest`
**Source:** `ui/service/ServiceEntryFormValidation.kt:49-132`; `commonTest/…/ServiceEntryFormValidationTest.kt`
**Apply to:** `ReminderFormValidation`. No Compose, no resources, no database; `isSubmittable` / `errorFor` / `sanitize…` / `toDomain`.

### 14. Deletion always runs through `ConfirmDeleteDialog`
**Source:** `ui/components/ConfirmDeleteDialog.kt:19-26`; `ui/vehicles/VehicleDetailScreen.kt:223-236`
**Apply to:** reminder delete. Import from `ui/components/` (C-10); the dialog's confirm is the **only** call site of the ViewModel's delete; no swipe, no long-press.

### 15. Statement-level UI state, single mutator, double-submit guard
**Source:** `ui/service/ServiceEntryEditViewModel.kt:197-209, 241-271`
**Apply to:** `ReminderEditViewModel`. One `onFieldChange(update)` entry point; `save()` rejects while `saving` or not submittable.

### 16. Snackbar ownership: screen for forms, shell for popped screens
**Source:** `ServiceEntryEditScreen.kt:143-163` (screen host); `App.kt:120-140` (shell scope before pop)
**Apply to:** save/delete failures on the editor (screen host) and "Reminder deleted." (shell host, launched before `popBackStack`).

### 17. Migration guard is already wired — keep it green
**Source:** `migrations/2.sqm:1-8`; `ci.yml:31-39`; `shared/build.gradle.kts:98-106`
**Apply to:** `ReminderRule.sq` + `3.sqm` + `4.db`. Never hand-edit a `.db`; never delete an older baseline; never put a `PRAGMA` in `.sq`/`.sqm`.

### 18. Palette tokens, 48dp targets, decorative glyphs
**Source:** `ui/theme/Color.kt:6-11, 19-26`; `ServiceTypePicker.kt:141-166`; `ConfirmDeleteDialog.kt:80-99`
**Apply to:** every new control. `Ink`/`Slate`/`Mist`/`Hairline`/`Paper`/`Danger` only; `Danger` only for overdue/destructive; every interactive element ≥ 48dp; every decorative icon `contentDescription = null`.

### 19. Keyed lazy lists with a 64dp bottom band
**Source:** `ServiceHistoryScreen.kt:131-147`; `VehicleListScreen.kt:107-120`
**Apply to:** the home dashboard and the Reminders list. `key = { it.id }` is mandatory because both lists reorder as due states change.

### 20. `@OptIn(ExperimentalCoroutinesApi::class)` for `flatMapLatest`
**Source:** `VehicleListViewModel.kt:42`; `ServiceHistoryViewModel.kt:41`
**Apply to:** `RemindersViewModel` and the modified `VehicleListViewModel`. The pinned coroutines requires the opt-in at class level.

---

## No Analog Found

| File | Role | Data Flow | Reason | Substitute convention |
|---|---|---|---|---|
| `…/androidMain/…/work/ReminderWorker.kt` | worker | batch + event-driven | No `Worker`, `Service`, or `BroadcastReceiver` exists anywhere in the tree; `androidx.work` is not even in the version catalog yet | `03-RESEARCH.md` §U1 + Pattern 1/2: `CoroutineWorker`, `DatabaseProvider.require()`, one unique periodic work, conditional-UPDATE latch. Structural precedent: `ServiceEntryRepositoryImpl.kt:37-41` (constructor injection + serialized lane) |
| `…/iosMain/…/notifications/IosNotificationDelegate.kt` | provider (delegate) | event-driven | No `UNUserNotificationCenterDelegate` and no Kotlin-side iOS delegate exists | `03-RESEARCH.md` §U3 + Pitfall 5: install before Compose, identifier payload `"vehicle:<id>"`, one-slot `MutableStateFlow<Long?>`. Structural precedent: `MainViewController.kt` as the single iOS entry point |
| `iosApp/iosApp/AppDelegate.swift` | provider (delegate) | event-driven | `iOSApp.swift` is 10 lines with no delegate, no `@UIApplicationDelegateAdaptor` | `03-RESEARCH.md` §U3: add the adaptor to `iOSApp.swift`, call the Kotlin `install()` in `didFinishLaunchingWithOptions` |
| `…/ui/reminders/components/ReminderToggleRow.kt` | component | request-response | No `Switch` exists anywhere in the app; every current control is a chip, button, field or card | `ui/components/FieldRow.kt:55-88` row layout + M3 `Switch` (which supplies its own 48dp interactive size). UI-SPEC §Rule row anatomy + §Accessibility "two nodes per rule row" |

**Near-misses that are intentionally *not* "no analog":** the notification delivery seam and its Android/iOS implementations have no *notification* precedent, but they have an exact **structural** precedent in `DriverFactory.kt` / `DriverFactory.android.kt` / `DriverFactory.ios.kt` (an `expect fun create…(platformContext: Any?)` with a context-casting factory, plus a platform interface the factory returns). Copy that structure rather than inventing a new one. `PermissionBanner.kt` is a `partial` match: the app has tappable `Mist` rows (`ServiceSummaryRow`) but no banner — reuse the row anatomy, do not invent a new visual language.

---

## Metadata

**Analog search scope:** whole repository — `shared/src/commonMain/kotlin/com/vehicare/{App.kt,data,domain,di,ui,util}`, `shared/src/commonMain/sqldelight/{com/vehicare/db,databases}`, `shared/src/commonMain/composeResources/values`, `shared/src/{androidMain,iosMain,commonTest,iosTest,androidDeviceTest}`, `androidApp/src/main`, `iosApp/iosApp`, `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `.github/workflows/ci.yml`
**Files scanned:** 34 source/config files read in full or in targeted ranges (all Phase 1/2 implementation files cited above, both platform hosts, the SQLDelight schema/migration/baseline inventory, and the build/CI config)
**Analog matches:** 39 exact · 12 role-match · 1 partial · 4 none (52 / 56 mapped)
**Pattern extraction date:** 2026-10-22
**Authoritative inputs:** `03-CONTEXT.md` (D-41…D-65 + carry-forwards), `03-RESEARCH.md` (file tree, Pattern 1/2, schema, U1–U6), `03-UI-SPEC.md` (destination/component inventory, due-state grammar, complete string set), and the shipped Phase 1/2 source.
**Planner note:** every "copy the pattern from X" action should cite the **file + line range** in this document — those line numbers refer to code that exists in this working tree, not to research sketches. Where this document's §Corrections disagrees with `03-RESEARCH.md` or `03-UI-SPEC.md`, the corrections win; they were derived by reading the shipped code. The four `none` entries above are the only places where a plan task must follow a research sketch instead of an existing file, and each names the structural precedent to imitate.
