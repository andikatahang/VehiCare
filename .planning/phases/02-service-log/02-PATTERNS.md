# Phase 2: Service Log - Pattern Map

**Mapped:** 2026-10-19
**Files analyzed:** 46 (39 source + 6 generated/config-adjacent + 1 deletion)
**Analogs found:** 45 / 46 (33 exact · 11 role-match · 1 partial · 1 none)

**This is NOT Phase 1.** The vehicle vertical slice is fully implemented and green in this repository. Every Phase 2 file has a real first-party analog — Phase 1's vehicle slice — and the excerpts below are **actual code from this repo** with line numbers, not research sketches. Where RESEARCH.md's sketch and the shipped code disagree, the shipped code wins and the divergence is called out in §Corrections That Override Upstream Docs.

---

## Corrections That Override Upstream Docs (read before planning)

These are places where the planning inputs (01-PATTERNS.md, 02-RESEARCH.md) describe something the shipped Phase 1 code does **not** do. Plan tasks must follow the shipped code.

| # | Upstream says | Shipped code says | Where |
|---|---|---|---|
| C-1 | `PRAGMA journal_mode=wal;` goes in the `.sq` preamble (01-PATTERNS Shared Pattern 4) | **Deliberately removed.** `Vehicle.sq` opens with a comment explaining why (SQLiter's non-query path rejects row-returning statements) and the WAL pragma runs in the platform `DriverFactory` through the row-safe `executeQuery` path. `ServiceEntry.sq` must contain **DDL + queries only, no PRAGMA line.** | `Vehicle.sq:1-4`, `DriverFactory.android.kt:15-31`, `DriverFactory.kt:11-14` |
| C-2 | Generated resources package is `vehicare.composeapp.generated.resources` (01-PATTERNS line 477, flagged "confirm after first build") | It is **`vehicare.shared.generated.resources`** | `VehicleEditScreen.kt:52-69`, every UI file |
| C-3 | `photoDeleter: (String?) -> Unit` (02-RESEARCH line 686) | Actual type is **`suspend (String?) -> Unit = {}`**; `AppModule` wires `PhotoStorage::delete` (a non-suspend function is a valid argument for a suspend function type) | `VehicleRepositoryImpl.kt:29`, `AppModule.kt:36` |
| C-4 | `flatMapLatest` used freely | The pinned coroutines requires **`@OptIn(ExperimentalCoroutinesApi::class)`** at class level | `VehicleListViewModel.kt:42` |
| C-5 | "Add service" FAB via the M3 `Scaffold` FAB slot | The shipped FAB is a **`Box` overlay** outside the state `when`, `align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp)` — this is the technique the history screen must copy (UI-SPEC §Component Dimensions states the same) | `VehicleListScreen.kt:61-65, 138-148` |
| C-6 | `databases/` holds one baseline | It holds `1.db` (**committed**) plus gitignored `1.db-wal` / `1.db-shm` sidecars; `migrations/.gitkeep` already exists. CI's guard is already in `ci.yml` — Phase 2 adds no CI step, it just has to stay green | `git ls-files`, `.gitignore:24-25`, `ci.yml:31-39` |

---

## File Classification

### Group A — Schema & migration

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `shared/src/commonMain/sqldelight/com/vehicare/db/ServiceEntry.sq` | migration/schema | CRUD | `…/db/Vehicle.sq` | exact |
| `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm` | migration | CRUD | *(none — no migration exists yet)* → RES §Pattern 1 | none |
| `shared/src/commonMain/sqldelight/databases/2.db` | DB baseline (generated, committed) | CRUD | `…/databases/1.db` | exact |

### Group B — Data layer & domain

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/kotlin/com/vehicare/data/ServiceEntryRepository.kt` | repository (interface) | CRUD + streaming | `data/VehicleRepository.kt` | exact |
| `…/data/ServiceEntryRepositoryImpl.kt` | repository (impl) | CRUD + streaming + file-I/O | `data/VehicleRepositoryImpl.kt` | exact |
| `…/data/ServiceEntryEntityMapper.kt` | utility (mapper) | transform | `data/VehicleEntityMapper.kt` | exact |
| `…/data/VehicleRepositoryImpl.kt` **(MODIFIED — D-39 cascade)** | repository (impl) | CRUD + file-I/O | itself (`delete`, lines 75-83) | exact |
| `…/domain/ServiceEntry.kt` | model (domain) | transform | `domain/Vehicle.kt` | exact |
| `…/domain/ServicePart.kt` | model (domain) | transform | `domain/Vehicle.kt` | role-match |
| `…/domain/HistorySort.kt` | model (enum) | transform | `domain/VehicleType.kt` | role-match |
| `…/domain/ServiceTypePresets.kt` *(implied by D-23 + UI-SPEC §Service-type chip group — constants, not strings)* | model (constants) | transform | `domain/VehicleType.kt` | role-match |

### Group C — Utilities

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/util/ServiceDates.kt` | utility | transform | `util/PhotoStorage.kt` (object shape) + RES §Date policy | role-match |
| `…/util/RupiahFormat.kt` | utility | transform | `ui/vehicles/VehicleFormValidation.kt` (pure object + private helper + unit test) | role-match |
| `…/util/PhotoStorage.kt` **(MODIFIED — directory parameter, D-37)** | utility | file-I/O | itself (`persist`, lines 55-61) | exact |

### Group D — Wiring, shell & resources

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/di/AppModule.kt` **(MODIFIED)** | provider (DI) | config/wiring | itself | exact |
| `…/ui/navigation/Routes.kt` **(MODIFIED)** | route | config/wiring | itself | exact |
| `…/App.kt` **(MODIFIED — 3 composables + shell snackbar)** | component (shell) | request-response | itself | exact |
| `…/composeResources/values/strings.xml` **(MODIFIED)** | config (i18n) | transform | itself | exact |
| `gradle/libs.versions.toml` **(MODIFIED — kotlinx-datetime 0.8.0)** | config | build-time | itself | exact |
| `shared/build.gradle.kts` **(MODIFIED — `implementation(libs.kotlinx.datetime)`)** | config | build-time | itself | exact |

### Group E — Screens & ViewModels (new)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/ui/service/ServiceHistoryScreen.kt` | component (screen) | streaming | `ui/vehicles/VehicleListScreen.kt` | role-match |
| `…/ui/service/ServiceHistoryViewModel.kt` | service (ViewModel) | streaming + sort switch | `ui/vehicles/VehicleListViewModel.kt` | role-match |
| `…/ui/service/ServiceEntryEditScreen.kt` | component (screen) | request-response | `ui/vehicles/VehicleEditScreen.kt` | exact |
| `…/ui/service/ServiceEntryEditViewModel.kt` | service (ViewModel) | request-response + file-I/O | `ui/vehicles/VehicleEditViewModel.kt` | exact |
| `…/ui/service/ServiceEntryDetailScreen.kt` | component (screen) | streaming | `ui/vehicles/VehicleDetailScreen.kt` | exact |
| `…/ui/service/ServiceEntryDetailViewModel.kt` | service (ViewModel) | streaming | `ui/vehicles/VehicleDetailViewModel.kt` | exact |

### Group F — Service components (new, `ui/service/components/`)

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `ServiceTypePicker.kt` | component | request-response | `ui/vehicles/components/VehicleTypePicker.kt` + RES FlowRow sketch | role-match |
| `PartsEditor.kt` | component | request-response | `ui/vehicles/components/PhotoField.kt` (rows + add/remove controls) | partial |
| `ServicePhotoStrip.kt` | component | file-I/O + request-response | `ui/vehicles/components/PhotoField.kt` | role-match |
| `ServiceHistoryRow.kt` | component | request-response | `ui/vehicles/components/VehicleCard.kt` | exact |
| `HistorySortControl.kt` | component | request-response | `ui/vehicles/components/VehicleTypePicker.kt` | exact |
| `ServiceDateField.kt` (+ `ServiceDatePickerDialog`) | component | request-response | `VehicleEditScreen.kt` field block + RES date-picker sketch | role-match |
| `ServiceTypeDialog.kt` | component | request-response | `ui/vehicles/components/ConfirmDeleteDialog.kt` (AlertDialog anatomy) | role-match |
| `ServiceSummaryRow.kt` | component | request-response | `ui/vehicles/components/ComingSoonSlot.kt` + `VehicleCard.kt` | role-match |

### Group G — Moved & parameterized shared components

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/ui/components/ConfirmDeleteDialog.kt` **(MOVED from `ui/vehicles/components/`, parameterized)** | component | request-response | `ui/vehicles/components/ConfirmDeleteDialog.kt` | exact (move) |
| `…/ui/components/EmptyState.kt` **(MOVED from `ui/vehicles/components/`, parameterized)** | component | request-response | `ui/vehicles/components/EmptyState.kt` | exact (move) |

### Group H — Modified / retired vehicle surfaces

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `…/ui/vehicles/VehicleDetailScreen.kt` **(MODIFIED — summary row replaces `ComingSoonSlot`)** | component (screen) | streaming | itself | exact |
| `…/ui/vehicles/components/ComingSoonSlot.kt` **(DELETED — UI-SPEC §Vehicle detail)** | component | — | — | — |

### Group I — Tests

| New/Modified File | Role | Data Flow | Closest Analog | Match Quality |
|---|---|---|---|---|
| `shared/src/commonTest/…/data/ServiceEntryEntityMapperTest.kt` | test | transform | `commonTest/…/data/VehicleEntityMapperTest.kt` | exact |
| `shared/src/commonTest/…/util/ServiceDatesTest.kt` | test | transform | `commonTest/…/ui/vehicles/VehicleFormValidationTest.kt` | exact |
| `shared/src/commonTest/…/util/RupiahFormatTest.kt` | test | transform | `commonTest/…/ui/vehicles/VehicleFormValidationTest.kt` | exact |
| `shared/src/commonTest/…/domain/ServiceTypeVocabularyTest.kt` (learned-type filter + preset collapse) | test | transform | `commonTest/…/ui/vehicles/components/VehicleMetaLineTest.kt` | exact |
| `shared/src/iosTest/…/data/ServiceEntryRepositoryTest.kt` | test | CRUD | `iosTest/…/data/VehicleRepositoryTest.kt` | exact |
| `shared/src/iosTest/…/data/ServiceEntryDeletionTest.kt` (+ vehicle cascade) | test | CRUD + file-I/O | `iosTest/…/ui/vehicles/VehicleDeletionTest.kt` | exact |
| `shared/src/iosTest/…/util/PhotoStorageTest.kt` **(EXTENDED — `service_entries` directory)** | test | file-I/O | itself | exact |
| `shared/src/androidDeviceTest/…/data/db/ServiceMigrationTest.kt` *(optional, research Open Q 2)* | test | CRUD | `androidDeviceTest/…/data/db/WalModeTest.kt` | role-match |

---

## Pattern Assignments

### Group A — Schema & migration

#### `ServiceEntry.sq` (migration/schema, CRUD)

**Analog:** `shared/src/commonMain/sqldelight/com/vehicare/db/Vehicle.sq`

**File preamble — copy this convention, not a PRAGMA** (`Vehicle.sq:1-4`):
```sql
-- WAL is deliberately NOT set in this file: every unlabeled statement here is replayed inside
-- the generated Schema.create() through driver.execute(), and `PRAGMA journal_mode=wal` returns
-- a row — which the SQLiter non-query path rejects, aborting the first launch on iOS. The driver
-- factories establish WAL on the first connection instead (see com.vehicare.data.db.DriverFactory).
```

**DDL + query shape** (`Vehicle.sq:6-33`) — the three conventions to carry: `INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT`, bare `SELECT *` queries named in camelCase, and the `lastInsertId` query:
```sql
CREATE TABLE vehicle (
    id         INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
    name       TEXT    NOT NULL,
    ...
);

selectAll:
SELECT * FROM vehicle ORDER BY id DESC;

selectById:
SELECT * FROM vehicle WHERE id = ?;

insert:
INSERT INTO vehicle(name, make, model, year, plate, type, photo_path)
VALUES (?, ?, ?, ?, ?, ?, ?);

delete:
DELETE FROM vehicle WHERE id=?;

lastInsertId: SELECT last_insert_rowid();
```

**Phase 2 DDL + queries** — full verified text is in `02-RESEARCH.md` §Code Examples lines 521-637 (three tables `service_entry` / `service_part` / `service_photo`, four indices, and the 16 named queries including `selectLearnedTypes`, `selectSummary`, `selectPreviousMileage` and the four vehicle-cascade statements). Copy it verbatim; do not re-derive it.

**Non-negotiable details:**
- All three tables go in **both** `ServiceEntry.sq` (current schema) and `migrations/1.sqm` with **identical DDL** — any column-order/index-name drift fails `verifyCommonMainVehiCareDatabaseMigration` (RES Pitfall 11).
- Index names must be unique across the schema (`service_part_entry`, `service_photo_entry`).
- Units live in column names: `service_date_epoch_ms`, `mileage_km`, `cost_rupiah` (PITFALLS SQL 2).
- `AUTOINCREMENT` is load-bearing: learned-type recency uses `MAX(id)`.

#### `migrations/1.sqm` (migration, CRUD) — **NO ANALOG**

There is no existing migration anywhere in the repo (`migrations/` holds only `.gitkeep`). Substitute convention: `02-RESEARCH.md` §Pattern 1 (lines 263-283) + §Code Examples (lines 519-557). Author it **together with** the `.sq` schema, then:
```bash
./gradlew generateCommonMainVehiCareDatabaseSchema   # writes 2.db, leaves 1.db alone
git add 2.db 1.sqm ServiceEntry.sq                   # 1.db stays committed, never deleted
```
Never hand-edit `*.db`. Existing guard (already in CI, no change needed) — `ci.yml:31-39`:
```yaml
      - name: Verify SQLDelight migrations (per-source-set task)
        run: ./gradlew verifyCommonMainVehiCareDatabaseMigration
      - name: Fail if schema changed without a migration
        run: |
          ./gradlew generateCommonMainVehiCareDatabaseSchema
          git diff --exit-code -- shared/src/commonMain/sqldelight/databases
```

#### `databases/2.db` (generated baseline)

**Analog:** `databases/1.db` — a build output that is **committed** (`git ls-files` confirms; its `-wal`/`-shm` sidecars are gitignored via `.gitignore:24-25`). Regenerate, commit, never edit.

---

### Group B — Data layer & domain

#### `data/ServiceEntryRepository.kt` (repository interface, CRUD + streaming)

**Analog:** `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepository.kt`

**Interface convention** (`VehicleRepository.kt:6-29`) — generated types never appear in signatures, and each KDoc names the behaviour it guarantees:
```kotlin
/**
 * The only storage contract the UI layer may use.
 *
 * No generated SQLDelight type appears in these signatures on purpose: consumers observe domain
 * models and never touch a driver, a query, or a row type.
 */
interface VehicleRepository {

    /** All vehicles, newest first (D-16). Emits again whenever the table changes. */
    fun observeVehicles(): Flow<List<Vehicle>>

    /** The vehicle with [id], or `null` once it does not exist. Emits on every change. */
    fun observeVehicle(id: Long): Flow<Vehicle?>

    ...
    suspend fun save(vehicle: Vehicle): Long

    /** Deletes the row with [id] and its photo file (D-07). */
    suspend fun delete(id: Long)
}
```

**Phase 2 shape** (from RES §Architecture Patterns, lines 199-202 — the split that keeps PITFALLS SQL 3 satisfied): `observeHistory(vehicleId, sort)`, `observeEntry(id)`, `observeSummary(vehicleId)`, `observeLearnedTypes()`, `observeLatestMileage(vehicleId)` / `observePreviousMileage(vehicleId, entryId)`, `save(entry): Long`, `delete(id)`.

#### `data/ServiceEntryRepositoryImpl.kt` (repository impl, CRUD + streaming + file-I/O)

**Analog:** `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` — **the single most important analog in this phase.**

**Class shape, serialized lane and injected deleter** (`VehicleRepositoryImpl.kt:26-36`):
```kotlin
internal class VehicleRepositoryImpl(
    private val database: VehiCareDatabase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
    private val photoDeleter: suspend (String?) -> Unit = {},
) : VehicleRepository {

    override fun observeVehicles(): Flow<List<Vehicle>> =
        database.vehicleQueries.selectAll()
            .asFlow()
            .mapToList(ioDispatcher)
            .map { rows -> rows.map { it.toDomain() } }
```

**Insert-vs-update write in one transaction, id via `lastInsertId()`** (`VehicleRepositoryImpl.kt:44-73`):
```kotlin
    override suspend fun save(vehicle: Vehicle): Long = withContext(ioDispatcher) {
        database.transactionWithResult {
            val params = vehicle.toBindParameters()
            if (vehicle.id == 0L) {
                database.vehicleQueries.insert(
                    name = params.name, make = params.make, model = params.model,
                    year = params.year, plate = params.plate, type = params.type,
                    photo_path = params.photoPath,
                )
                // Same connection, same transaction: no separate connection can race the id.
                database.vehicleQueries.lastInsertId().executeAsOne()
            } else {
                database.vehicleQueries.update(/* named binds … */, id = vehicle.id)
                vehicle.id
            }
        }
    }
```
> Note the **named bind arguments** (`name = …`, `photo_path = …`) — SQLDelight generates parameters from column names, and the repo uses named form everywhere. `ServiceEntryRepositoryImpl.save` binds `vehicle_id =`, `service_date_epoch_ms =`, etc. (`02-RESEARCH.md` lines 697-738 is already written in this style and matches).

**Delete ordering — rows in the transaction, files after** (`VehicleRepositoryImpl.kt:75-83`):
```kotlin
    override suspend fun delete(id: Long): Unit = withContext(ioDispatcher) {
        val photoPath = database.transactionWithResult {
            val currentPhotoPath =
                database.vehicleQueries.selectById(id).executeAsOneOrNull()?.photo_path
            database.vehicleQueries.delete(id)
            currentPhotoPath
        }
        photoDeleter(photoPath)
    }
```
Phase 2 generalizes this to a **list** of paths: read `selectPhotos(id)`, delete parts → photos → entry, return the paths, then `paths.forEach(photoDeleter)` after the transaction (`02-RESEARCH.md` lines 740-750).

#### `data/ServiceEntryEntityMapper.kt` (mapper, transform)

**Analog:** `shared/src/commonMain/kotlin/com/vehicare/data/VehicleEntityMapper.kt`

**Exact conventions to copy** (`VehicleEntityMapper.kt:1-48`):
```kotlin
import com.vehicare.db.Vehicle as VehicleEntity      // generated row aliased
import com.vehicare.domain.Vehicle
import com.vehicare.domain.VehicleType

internal fun VehicleEntity.toDomain(): Vehicle = Vehicle(
    id = id, name = name, make = make, model = model, year = year, plate = plate,
    type = VehicleType.fromDb(type),
    photoPath = photo_path,                            // snake_case column → camelCase property
)

/** The seven non-id bind values of a vehicle row, in `Vehicle.sq` query order. */
internal class VehicleBindParameters(/* … */)

internal fun Vehicle.toBindParameters(): VehicleBindParameters = VehicleBindParameters(/* … */)
```
Both the mapper **and** a `XBindParameters` class live here; `internal` visibility throughout. The service mapper adds `ServicePartEntity.toDomain()` / `ServicePhotoEntity.toDomain()` and a `finalize()`-free approach (SQLDelight custom result types like `SelectLearnedTypes` / `SelectSummary` are mapped here too, never above the data layer).

#### `data/VehicleRepositoryImpl.kt` — MODIFIED (D-39 cascade)

**Analog:** itself, `VehicleRepositoryImpl.kt:75-83` (shown above). The extended body is `02-RESEARCH.md` lines 757-773: inside the existing `transactionWithResult`, read the vehicle photo **and** all service photo paths **before** deleting the children, then delete `service_photo` → `service_part` → `service_entry` → `vehicle`, then `photoDeleter` every collected path. The comment at `AppModule.kt:33-34` ("Deleting a vehicle removes its photo file through the real storage seam (D-07)") is the contract this extension is completing, not replacing.

#### `domain/ServiceEntry.kt`, `domain/ServicePart.kt` (models)

**Analog:** `shared/src/commonMain/kotlin/com/vehicare/domain/Vehicle.kt`
```kotlin
data class Vehicle(
    val id: Long = 0L,
    val name: String,
    val make: String? = null,
    ...
    val type: VehicleType = VehicleType.Car,
    val photoPath: String? = null,
)
```
Zero SQLDelight imports; `id = 0L` means "not persisted yet" and the repository treats it as an insert (`Vehicle.kt:11-12`). `ServiceEntry` follows the same default-everything-optional shape: `id = 0L, vehicleId, dateEpochMs, mileageKm, serviceType, costRupiah: Long? = null, notes: String? = null, workshop: String? = null, mechanic: String? = null, parts: List<ServicePart> = emptyList(), photoPaths: List<String> = emptyList()`.

#### `domain/HistorySort.kt`, `domain/ServiceTypePresets.kt` (enum + constants)

**Analog:** `shared/src/commonMain/kotlin/com/vehicare/domain/VehicleType.kt`
```kotlin
enum class VehicleType {
    Car, Motorcycle, Truck;

    /** The value persisted in `vehicle.type`. */
    fun toDb(): String = name

    companion object {
        /** Parses a persisted `vehicle.type` value; anything unrecognised falls back to [Car]. */
        fun fromDb(value: String): VehicleType = entries.firstOrNull { it.name == value } ?: Car
    }
}
```
- `HistorySort` is a plain two-value enum (`DATE`, `MILEAGE`) — no `toDb`/`fromDb` needed, it never touches storage (D-34: the choice is not persisted).
- `ServiceTypePresets` is the D-23/UI-SPEC-mandated **code constant list** (six labels in locked order), *not* string resources — UI-SPEC line 196 gives the reason (the stored value and the displayed label are the same token, D-40). Model it as an `object` with `val ALL: List<String>` in `domain/`; the case-insensitive preset filter from RES §Pattern 3 (lines 321-326) lives next to it and is unit-tested.

---

### Group C — Utilities

#### `util/ServiceDates.kt` (utility, transform)

**Analog (shape):** `util/PhotoStorage.kt` — a stateless `object` with a file-level KDoc naming it the single seam. **Logic:** `02-RESEARCH.md` §Date policy, lines 641-677 (verified against kotlinx-datetime v0.8.0 source).

Copy the KDoc discipline from `PhotoStorage.kt:15-26` ("This object is the only place in the app that touches photo files… Do not create a second storage object anywhere") — `ServiceDates` is the same kind of single seam for date conversion, and the asymmetry (today = system zone, storage = UTC start-of-day) must be documented at the object, not at each call site.

**Binding API notes:** use `kotlin.time.Clock` / `kotlin.time.Instant` (the repo already uses `kotlin.time.Clock` at `VehicleEditViewModel.kt:14, 308`); use `LocalDate.day` / `Month.number` (**not** `dayOfMonth` / `monthNumber`); do **not** copy the `TimeZoneContext` API from Context7 (it is not in 0.8.0).

#### `util/RupiahFormat.kt` (utility, transform)

**Analog (shape):** `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleFormValidation.kt` — the repo's pure-function pattern:
```kotlin
object VehicleFormValidation {
    fun isSubmittable(values: VehicleFormValues): Boolean = values.name.isNotBlank()
    fun sanitizeYear(raw: String): String = raw.filter { it.isDigit() }.take(4)
    private fun String.trimToNull(): String? = trim().takeIf { it.isNotEmpty() }
}
```
No Compose, no resources, no DB imports; every rule a pure function; unit-tested directly (`VehicleFormValidationTest.kt:15-66` asserts `sanitizeYear("20246") == "2024"`, blank handling, etc.).

**Phase 2 note:** UI-SPEC locks **two** dot-separated numeric formats — cost `Rp 450.000` and mileage `123.456 km`. Put one shared digit-grouping helper in this util and expose `formatRupiah(Long)` and `formatMileage(Long)` (or `groupDigits`) so the two formats cannot drift. `02-RESEARCH.md` lines 785-796 supplies the implementation and the 12-digit cap; `parseRupiahDigits` mirrors `sanitizeYear`'s digits-only filter exactly (`it.isDigit()` — the same filter, different cap).

#### `util/PhotoStorage.kt` — MODIFIED (directory parameter, D-37)

**Analog:** itself.
```kotlin
    private const val DIRECTORY = "vehicles"          // ← becomes the default parameter value

    suspend fun persist(picked: PlatformFile, fileName: String): String {
        val relative = "$DIRECTORY/$fileName"
        val source = picked.readBytes()
        resolve(DIRECTORY).createDirectories()
        resolve(relative).write(compress(source))
        return relative
    }

    fun resolve(relativePath: String): PlatformFile =
        PlatformFile(FileKit.filesDir, relativePath)

    fun delete(relativePath: String?) {
        val relative = relativePath?.takeIf { it.isNotBlank() } ?: return
        runCatching { SystemFileSystem.delete(path = resolve(relative).toKotlinxIoPath(), mustExist = false) }
    }
```
(`PhotoStorage.kt:27-87`.) Add `directory: String = VEHICLES_DIRECTORY` to `persist` only — **existing vehicle call sites must not change** (RES lines 872-887). Keep `delete` **non-suspend** (its KDoc at `PhotoStorage.kt:72-78` explains why: the ViewModel calls it from `onCleared`, where no scope exists) — that is exactly why entry photo bookkeeping works from `ServiceEntryEditViewModel.onCleared()` too. `resolve`/`delete` already take a full relative path, so they need no change at all.

---

### Group D — Wiring, shell & resources

#### `di/AppModule.kt` — MODIFIED

**Analog:** itself (`AppModule.kt:27-45`):
```kotlin
val appModule = module {
    single {
        val driverFactory = getOrNull<DriverFactory>() ?: createDriverFactory()
        DatabaseFactory(driverFactory.createDriver())
    }
    single<VehiCareDatabase> { get<DatabaseFactory>().database }
    single<VehicleRepository> {
        VehicleRepositoryImpl(get(), photoDeleter = PhotoStorage::delete)
    }
    viewModel { VehicleListViewModel(get()) }
    viewModel { (vehicleId: Long) -> VehicleEditViewModel(get(), vehicleId) }
    viewModel { (vehicleId: Long) -> VehicleDetailViewModel(get(), vehicleId) }
}
```
Phase 2 adds one `single<ServiceEntryRepository>` (with the same `photoDeleter = PhotoStorage::delete` wiring — note `get()`-positional plus the named deleter) and three route-id ViewModels in the identical `viewModel { (id: Long) -> … }` form. The delete-cascade change to `VehicleRepositoryImpl` needs no new registration.

#### `ui/navigation/Routes.kt` — MODIFIED

**Analog:** itself (`Routes.kt:12-25`):
```kotlin
@Serializable
data object VehicleListRoute

@Serializable
data object SettingsRoute

/** The shared Add/Edit form. `id == 0L` means "add"; any `id > 0` means "edit [id]". */
@Serializable
data class VehicleEditRoute(val id: Long = 0L)

@Serializable
data class VehicleDetailRoute(val id: Long)
```
Add `ServiceHistoryRoute(vehicleId: Long)`, `ServiceEntryEditRoute(vehicleId: Long, entryId: Long = 0L)`, `ServiceEntryDetailRoute(entryId: Long)` — same file, same KDoc style explaining the `0L` add convention.

#### `App.kt` — MODIFIED

**Analog:** itself. Two sections are load-bearing:

*Nav graph* (`App.kt:90-131`) — the `composable<…> { backStackEntry -> val route = backStackEntry.toRoute<…>() … }` destructuring and the `onBack = { navController.popBackStack() }` convention; note the import-aliasing trick at `App.kt:25-27` (`VehicleDetailRoute` route type vs. `VehicleDetailRoute` composable) that new imports must join.

*Shell-hosted delete snackbar* (`App.kt:78-88` and `125-128`):
```kotlin
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()
            ...
                            onDeleted = {
                                scope.launch { snackbarHostState.showSnackbar(deletedMessage) }
                                navController.popBackStack()
                            },
```
The entry-delete success path ("Service entry deleted.") reuses **exactly this** mechanism on the `ServiceEntryDetailRoute` composable — the message is launched on the shell's scope *before* the pop, so the popped screen cannot take it away. `isTabRoot` (`App.kt:74-76`) must stay a two-entry list: all three Phase 2 destinations are pushed sub-screens.

#### `strings.xml` — MODIFIED

**Analog:** itself (`strings.xml:1-70`). Conventions: `<!-- section -->` comments, escaped apostrophes (`your\'ve` → `couldn\'t`), `%1$s` / `%1$d` positional args, em dash kept as a literal in `value_empty`.

The **complete Phase 2 string set is authored and implementation-ready in `02-UI-SPEC.md` lines 324-397** — copy it verbatim. Reuse (do not re-author): `action_cancel`, `action_edit`, `action_try_again`, `action_add_photo`, `action_remove_photo`, `error_add_photo`, `field_required_marker`, `value_empty`, `cd_back`, `title_vehicle_fallback`. Retire `detail_service_history_coming_soon` in the same change that deletes `ComingSoonSlot.kt`.

#### `libs.versions.toml` + `shared/build.gradle.kts` — MODIFIED

**Analog:** themselves. Catalog style is `kebab-case` keys with a `[versions]` ref (`libs.versions.toml:13-24`); module deps are listed alphabetically in `commonMain.dependencies` (`shared/build.gradle.kts:52-73`). Add `kotlinx-datetime = "0.8.0"` + the library entry and `implementation(libs.kotlinx.datetime)` exactly as `02-RESEARCH.md` lines 141-153 specify (use plain `0.8.0`, **not** the `0.8.0-0.6.x-compat` variant). No other build change: `schemaOutputDirectory` is already set (`shared/build.gradle.kts:97-105`) and CI already carries both migration guards.

---

### Group E — Screens & ViewModels

#### `ui/service/ServiceHistoryScreen.kt` (component, streaming)

**Analog:** `ui/vehicles/VehicleListScreen.kt`

**Screen contract** (`VehicleListScreen.kt:42-65`) — navigation injected, never owned:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListRoute(
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long) -> Unit,
    viewModel: VehicleListViewModel = koinViewModel(),
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(Res.string.title_vehicles)) }) },
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
```

**FAB overlay — copy this, not an M3 FAB slot** (`VehicleListScreen.kt:136-148`):
```kotlin
            // Outside the state `when` on purpose: the add control is present while loading, on an
            // empty list, over the cards and after a failed read.
            FloatingActionButton(
                onClick = onAddVehicle,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp),
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = stringResource(Res.string.cd_add_vehicle))
            }
```

**List + keys + padding** (`VehicleListScreen.kt:91-109`) — reuse the exact `contentPadding(16/16/32/64)` shape and mandatory `key =`:
```kotlin
                        items(state.items, key = { vehicle -> vehicle.id }) { vehicle ->
                            VehicleCard(vehicle = vehicle, onClick = { onOpenVehicle(vehicle.id) })
                        }
```
Phase 2 structure per UI-SPEC §Service history: `Column { HistorySortControl; LazyColumn }` (sort control renders **only** in Content state) — never a `LazyColumn` inside a `verticalScroll` (PITFALLS CMP 2). Error/Loading rows copy `VehicleListScreen.kt:112-133` (centred copy + "Try again" `OutlinedButton`, and the `semantics { contentDescription = loadingLabel }` on the indicator at lines 68-77).

#### `ui/service/ServiceHistoryViewModel.kt` (ViewModel, streaming + sort switch)

**Analog:** `ui/vehicles/VehicleListViewModel.kt` (the `flatMapLatest` restart shape) combined with RES §Code Examples lines 798-811 (the sort state).

`VehicleListViewModel.kt:42-70` is the exact skeleton to extend — including the required opt-in:
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class VehicleListViewModel(private val repo: VehicleRepository) : ViewModel() {

    private val refresh = MutableStateFlow(0)

    val uiState: StateFlow<VehicleListUiState> =
        refresh
            .flatMapLatest { repo.observeVehicles().map { … } }
            .catch { emit(VehicleListUiState.Error(message = it.message)) }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = VehicleListUiState.Loading,
            )

    fun retry() { refresh.value++ }
}
```
Phase 2 swaps the `refresh` source for `MutableStateFlow(HistorySort.DATE)` and calls `repo.observeHistory(vehicleId, mode)` inside `flatMapLatest`; the state matrix mirrors `VehicleListUiState` (Loading / Empty / Content / Error) with `Error` carrying the raw message and `retry()` kept intact. The sort selection is VM state only — **never** persisted (D-34).

#### `ui/service/ServiceEntryEditScreen.kt` (screen, request-response)

**Analog:** `ui/vehicles/VehicleEditScreen.kt` — the closest single file in the repo.

**One-shot flag retire pattern** (`VehicleEditScreen.kt:99-117`):
```kotlin
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(saveErrorText)
            viewModel.onSaveErrorShown()
        }
    }
```
Copy both blocks (save-failure and photo-import-failure) verbatim in shape; the flag must retire after the message or recomposition replays it (Phase 1 convention, PITFALLS-adjacent Pitfall 9).

**State rendering** (`VehicleEditScreen.kt:141-181`): `when (val current = state) { Loading -> …; NotFound -> …; is Ready -> Form(...) }` — every branch paints something.

**Form column — `imePadding()` + one `verticalScroll`, no autofocus, submit last** (`VehicleEditScreen.kt:211-221, 320-342`):
```kotlin
    Column(
        modifier = Modifier
            .padding(contentPadding)
            .imePadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) { … }
```
**Divergence to honour (D-31):** the vehicle form autofocuses Name (`VehicleEditScreen.kt:204-209`); the service form must **not** autofocus anything (UI-SPEC line 202).

**Field label + required marker + error slot** (`VehicleEditScreen.kt:222-253`) — the `buildAnnotatedString` + `field_required_marker` label and `isError`/`supportingText` mechanism are reused for Mileage's error and the mileage warning (the warning reuses the same slot in `Danger`).

**Submit button with in-flight indicator** (`VehicleEditScreen.kt:320-342`) — `enabled = isSubmittable && !saving` plus the `CircularProgressIndicator(size 20.dp, color = LocalContentColor.current, strokeWidth = 2.dp)` swap.

#### `ui/service/ServiceEntryEditViewModel.kt` (ViewModel, request-response + file-I/O)

**Analog:** `ui/vehicles/VehicleEditViewModel.kt` — the photo-lifecycle rules are stated in prose there; Phase 2 must generalize them per-path (RES §Pattern 5).

**State matrix + one-shot flags** (`VehicleEditViewModel.kt:23-51`): sealed interface with `Loading` / `Ready(values, error, saving, nameEdited, saveFailed, importing, photoError)` / `NotFound`, each field KDoc'd.

**Save guard + failure handling** (`VehicleEditViewModel.kt:175-206`):
```kotlin
        if (current.saving || current.importing || !VehicleFormValidation.isSubmittable(current.values)) return
        _uiState.value = current.copy(saving = true, saveFailed = false)
        val submitted = current.values
        viewModelScope.launch {
            try {
                repo.save(VehicleFormValidation.toVehicle(vehicleId, submitted))
                markPhotoSaved()
                clearSaving()
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                markSaveFailed()
            }
        }
```
Cancellation is always rethrown first — same in every async block in the codebase.

**Photo bookkeeping fields** (`VehicleEditViewModel.kt:102-121`) — the exact vocabulary to generalize into per-path sets:
```kotlin
    private var supersededPath: String? = null      // → supersededPaths: MutableSet<String>
    private var persistedButUnsaved: Boolean = false // → persistedButUnsavedPaths: MutableSet<String>
    private var lastPhotoStamp: Long = 0L
```

**Import ordering** (`VehicleEditViewModel.kt:219-256`) — persist first, then route the previous file; and **abandon** (`294-300`) which touches only session-owned files:
```kotlin
    override fun onCleared() {
        if (persistedButUnsaved) {
            val held = (_uiState.value as? VehicleEditUiState.Ready)?.values?.photoPath
            PhotoStorage.delete(held)
        }
        super.onCleared()
    }
```
**Naming** (`VehicleEditViewModel.kt:307-311`):
```kotlin
    private fun nextPhotoFileName(): String {
        val stamp = maxOf(Clock.System.now().toEpochMilliseconds(), lastPhotoStamp + 1L)
        lastPhotoStamp = stamp
        return "$vehicleId-$stamp.jpg"
    }
```
For entries the name is `"$entryId-$stamp.jpg"`, directory `service_entries` (RES line 363). The seven per-path rules are enumerated in `02-RESEARCH.md` §Pattern 5 lines 349-363 — they are the task list for this file.

**Edit-load rule** (`VehicleEditViewModel.kt:100, 123-148`): `initialLoadApplied` accepts only the first emission so a later repository change cannot overwrite in-progress user input. The service edit must reproduce this (child rows make it more important, not less).

**Form values + validation split** — follow `VehicleFormValidation.kt:15-79`: a `data class ServiceEntryFormValues` (all text fields `String`, `costRupiah` held as digits text, `parts` as a list of raw rows, `photoPaths` as `List<String>`) and an `object ServiceEntryFormValidation` with `isSubmittable` (date + type + non-blank mileage — D-27), `sanitizeMileage` (digits, ≤ 9), `errorFor`, and `toServiceEntry(id, vehicleId, values)` mapping blank → `null` exactly like `trimToNull`.

#### `ui/service/ServiceEntryDetailScreen.kt` (screen, streaming)

**Analog:** `ui/vehicles/VehicleDetailScreen.kt`

**Title bar with Edit text action** (`VehicleDetailScreen.kt:139-163`):
```kotlin
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(Res.string.cd_back))
                    }
                },
                actions = {
                    TextButton(onClick = onEdit, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                        Text(text = stringResource(Res.string.action_edit))
                    }
                },
            )
```

**Confirm gate — the dialog's confirm is the only call site of delete** (`VehicleDetailScreen.kt:134-137, 209-222`):
```kotlin
    var showDeleteDialog by remember { mutableStateOf(false) }
    ...
        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                vehicleName = title,
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onDeleted)
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
```

**Destructive action last, after a 2xl 48px break** (`VehicleDetailScreen.kt:302-330`):
```kotlin
        Spacer(modifier = Modifier.height(48.dp))
        ComingSoonSlot()                              // ← becomes ServiceSummaryRow (Group H)
        Spacer(modifier = Modifier.height(48.dp))

        OutlinedButton(
            onClick = onDeleteClick,
            enabled = deleteEnabled,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Icon(imageVector = Icons.Filled.Delete, contentDescription = null)
            Text(text = stringResource(Res.string.action_delete_vehicle), modifier = Modifier.padding(start = 8.dp))
        }
```
Entry detail: same button, `action_delete_service_entry`, preceded by the Photos block; then the `FieldRow` run and the stacked Notes row from UI-SPEC §Service entry detail (lines 238-249).

**Field rows** — reuse `FieldRow` unchanged (`FieldRow.kt:47-93`): label `labelLarge`/`Slate`, value `bodyLarge`/`Ink`/`TextAlign.End`, em-dash placeholder from `value_empty`, optional `trailing` slot (the `LicensePlateChip` precedent shows how to swap the value slot), `showDivider = false` on the last row.

**Delete-failed path** (`VehicleDetailScreen.kt:119-132`): one-shot `LaunchedEffect(deleteFailed)` → snackbar → `viewModel.onDeleteErrorShown()`; the entry stays on screen.

#### `ui/service/ServiceEntryDetailViewModel.kt` (ViewModel, streaming)

**Analog:** `ui/vehicles/VehicleDetailViewModel.kt` — nearly a straight port.

**Streaming state + initial Loading** (`VehicleDetailViewModel.kt:54-70`):
```kotlin
    val uiState: StateFlow<VehicleDetailUiState> =
        repo.observeVehicle(vehicleId)
            .map { vehicle -> if (vehicle == null) VehicleDetailUiState.NotFound else VehicleDetailUiState.Content(vehicle) }
            .catch { emit(VehicleDetailUiState.NotFound) }
            .stateIn(scope = viewModelScope, started = SharingStarted.WhileSubscribed(5_000), initialValue = VehicleDetailUiState.Loading)
```

**Single-flight delete guard + failure flag** (`VehicleDetailViewModel.kt:72-135`):
```kotlin
    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting
    private val _deleteFailed = MutableStateFlow(false)
    val deleteFailed: StateFlow<Boolean> = _deleteFailed

    fun delete(onDeleted: () -> Unit) {
        if (deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                repo.delete(vehicleId)
                _deleting.value = false
                onDeleted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _deleting.value = false
                _deleteFailed.value = true
            }
        }
    }

    fun onDeleteErrorShown() { _deleteFailed.value = false }
```
For entries, `uiState` is built from the composed read (`entry` + parts + photo paths) the repository exposes, with a `NotFound` state for the "no longer available" copy.

---

### Group F — Service components

#### `ServiceTypePicker.kt` (component)

**Analog:** `ui/vehicles/components/VehicleTypePicker.kt` (palette declaration + 48dp rule) — the layout itself comes from RES §Code Examples lines 815-839 (`FlowRow` + `FilterChip` + `AssistChip`).

**Palette-not-M3-defaults rule** (`VehicleTypePicker.kt:45-69`) — every Phase 2 chip/segment declaration copies this shape:
```kotlin
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = Paper, activeContentColor = Ink, activeBorderColor = Ink,
        inactiveContainerColor = Mist, inactiveContentColor = Slate, inactiveBorderColor = Hairline,
        disabledActiveContainerColor = Paper, disabledActiveContentColor = Slate, disabledActiveBorderColor = Hairline,
        disabledInactiveContainerColor = Mist, disabledInactiveContentColor = Slate, disabledInactiveBorderColor = Hairline,
    )
    …
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
```
Phase 2 additions per UI-SPEC §Service-type chip group (lines 191-198): `FlowRow` with `sm` 8px both axes; selected chip = `Ink` border + `Ink` label + leading 16dp `Check` glyph (`contentDescription = null`); unselected = `Mist`/`Slate`/`Hairline`; `AssistChip` for "Add custom…"; **no type preselected**; enabled flag mirrors `enabled: Boolean = true` param style.

#### `PartsEditor.kt` (component) — **PARTIAL MATCH**

**Analog:** `ui/vehicles/components/PhotoField.kt` for the *row + control + enabled* grammar (`PhotoField.kt:121-154`):
```kotlin
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { picker.launch() }, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(text = stringResource(Res.string.action_add_photo))
                }
                …
                TextButton(onClick = onRemove, enabled = enabled, modifier = Modifier.defaultMinSize(minHeight = 48.dp)) {
                    Text(text = stringResource(Res.string.action_remove_photo))
                }
```
There is **no** multi-row editor in the repo. The parts block is new layout: per UI-SPEC §Component Dimensions (lines 90-92), each row is `name OutlinedTextField(weight 1f)` + `sm` gap + `qty OutlinedTextField(72dp, digits only)` + `sm` gap + remove `IconButton` (48dp), wrapped in a `Column(spacedBy(8.dp))` with an "Add part" `TextButton` below. State is hoisted exactly like every other Phase 1 field: the component renders and reports intent (`onPartChange`/`onAddPart`/`onRemovePart`), the ViewModel owns the list. Digits-only filtering follows `VehicleFormValidation.sanitizeYear` (`it.isDigit()`).

#### `ServicePhotoStrip.kt` (component, file-I/O)

**Analog:** `ui/vehicles/components/PhotoField.kt` — a direct generalization of one photo to N.

**Picker obtained inside composition** (`PhotoField.kt:72-74`) — mandatory on iOS (the launcher captures the hosting view controller):
```kotlin
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { picked ->
        if (picked != null) onPicked(picked)
    }
```
**Decorative vs. labelled images** (`PhotoField.kt:98-99, 110-117`): the clickable empty block merges semantics and carries the glyph's label; the real image uses `PhotoStorage.resolve(path)` + `contentScale = ContentScale.Crop`. For the strip, thumbnails are decorative (`contentDescription = null`) and each remove control gets `cd_remove_photo` with the index — matching `cd_vehicle_photo`'s `%1$s` parameter style (`strings.xml:67`).
**Presentational-only rule** (`PhotoField.kt:55-56`): "Which file may be removed and when is the ViewModel's decision" — the strip never calls `PhotoStorage` mutators, only `resolve` for display.

#### `ServiceHistoryRow.kt` (component)

**Analog:** `ui/vehicles/components/VehicleCard.kt`

**Card language** (`VehicleCard.kt:62-104`):
```kotlin
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = 88.dp).semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Mist),
    ) {
        Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            …
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = vehicle.name, style = MaterialTheme.typography.titleMedium, color = Ink)
                Text(text = metaLine, style = MaterialTheme.typography.bodyLarge, color = Slate)
```
**Meta-line helper + test pattern** (`VehicleCard.kt:29-40`, tested in `VehicleMetaLineTest.kt:7-45`): an `internal fun vehicleMetaLine(...)` in the component file, blank-after-trim treated like null, the `·` (U+00B7) separator with single spaces. Phase 2 adds the sibling `internal fun serviceMetaLine(dateEpochMs: Long, mileageKm: Long): String` returning `"19 Oct 2026 · 123.456 km"` — built from `ServiceDates.display` + the mileage formatter, unit-tested the same way. Row anatomy per UI-SPEC §Service history (lines 225-229): title = type (`Title`/`Ink`, `maxLines = 1` + ellipsis), meta `sm` below, **cost trailing** (`Title`/`Ink`, or em dash in `Slate` when null), min 72dp/80dp height, merged semantics.

#### `HistorySortControl.kt` (component)

**Analog:** `ui/vehicles/components/VehicleTypePicker.kt` — an almost mechanical copy with `count = 2`, labels `sort_date` / `sort_mileage`, and no per-item icon. `SingleChoiceSegmentedButtonRow` + `SegmentedButtonDefaults.itemShape(index, count)` (`VehicleTypePicker.kt:60-74`), `Modifier.defaultMinSize(minHeight = 48.dp)`, the exact same `colors` block. Not an accent surface (UI-SPEC §Color).

#### `ServiceDateField.kt` + `ServiceDatePickerDialog` (component)

**Analog (field):** the required-marker label style in `VehicleEditScreen.kt:222-237`; **analog (dialog):** RES §Code Examples lines 844-867 (verified against the pinned material3 common source).

The field is a **read-only** `OutlinedTextField` (not clickable-through-to-keyboard): UI-SPEC line 90 locks 56dp height, `Hairline` outline, trailing 24dp `DateRange` glyph in `Slate`, `contentDescription = null`, tap opens the dialog — the keyboard must never open for it. `showModeToggle = false` (UI-SPEC line 119). The dialog's confirm `TextButton` is disabled while `selectedDateMillis == null`; every dismiss path keeps the previous value.

#### `ServiceTypeDialog.kt` (component)

**Analog:** `ui/vehicles/components/ConfirmDeleteDialog.kt`

**AlertDialog anatomy** (`ConfirmDeleteDialog.kt:56-97`): `containerColor = Paper` (never M3's container tint), `title` in `titleLarge`, body in `bodyLarge`, and both `TextButton`s at `Modifier.defaultMinSize(minHeight = 48.dp)` — Cancel in `Ink`, the action button optionally `error`-tinted via `ButtonDefaults.textButtonColors(contentColor = …)`.
Phase 2 differences (UI-SPEC line 198): one `OutlinedTextField` (`singleLine`, max 60 chars, trimmed), confirm "Add type" (`Ink` text button, disabled while the trimmed value is blank), title "Add service type", Cancel unchanged. Dismiss changes nothing.

#### `ServiceSummaryRow.kt` (component)

**Analog:** `ui/vehicles/components/ComingSoonSlot.kt` (position + geometry) + `VehicleCard.kt` (tappable card) — it is literally the tappable successor of the former.

**Container it replaces** (`ComingSoonSlot.kt:32-45`):
```kotlin
    Box(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Mist).padding(16.dp),
    ) {
        Text(text = stringResource(Res.string.detail_service_history_coming_soon), style = MaterialTheme.typography.labelLarge, color = Slate)
    }
```
Phase 2 (UI-SPEC lines 231-236): `Mist` container, 12dp radius, `md` inner padding, **min height 72dp**, `Modifier.clickable` ripple, merged semantics; `Column(weight 1f)` with "Service history" (`Title`/`Ink`) + meta (`Body`/`Slate`, `sm` below) + trailing 24dp `KeyboardArrowRight` in `Slate` (`contentDescription = null`). Tappable at zero entries (the empty state with its CTA is on the history screen); while the aggregate loads, render title only at stable 72dp — no spinner.

---

### Group G — Moved & parameterized shared components

#### `ui/components/ConfirmDeleteDialog.kt` (MOVE from `ui/vehicles/components/`)

**Analog:** `ui/vehicles/components/ConfirmDeleteDialog.kt` in full. The move is mechanical except for the **copy parameters**; body stays identical (`Paper` container, `AlertDialog`, `onDismissRequest = onDismiss`, both buttons ≥ 48dp, confirm `error`-tinted, Cancel `Ink`).

Parameterize as `title: String`, `message: String`, `confirmLabel: String` (strings resolved by the caller via `stringResource`) — or pass `StringResource`/format args; either way **the guarantees do not change**: `onDismissRequest = onDismiss` covers back / outside tap / Escape (`ConfirmDeleteDialog.kt:37-38`), and the KDoc warning that this dialog is the only path to the destructive ViewModel call (`:26-28`) must move with the file. Update both vehicle call sites (`VehicleDetailScreen.kt:50, 210-221`) in the same change.

#### `ui/components/EmptyState.kt` (MOVE from `ui/vehicles/components/`)

**Analog:** `ui/vehicles/components/EmptyState.kt` in full (`:39-82`): 64dp `Mist` circle + 24dp glyph + `xl` 32px to the heading (`titleLarge`/`Ink`) + `sm` 8px to the body (`bodyLarge`/`Slate`) + `md` 24px to a filled CTA `Button`. Copy is hardcoded in the analog's imports (`empty_vehicles_heading` / `empty_vehicles_body` / `action_add_vehicle`) and the visual is a drawable (`vectorResource(Res.drawable.ic_vehicle_car)`).

**Parameterize:** heading body/CTA strings + the leading visual. The history empty state needs a **24dp `Build` glyph inside the 64dp circle** (material-icons-core, no new drawable — UI-SPEC §Icon Contract line 39), so accept either an `ImageVector` or a `@Composable` slot. The `VectorPainter`/`ImageVector` difference must be hidden inside the component — the vehicle call site keeps rendering its bundled `ic_vehicle_car` vector.

---

### Group H — Modified / retired vehicle surfaces

#### `ui/vehicles/VehicleDetailScreen.kt` — MODIFIED

**Analog:** itself. Three edits, no restyling:
1. `ComingSoonSlot()` at `VehicleDetailScreen.kt:303` → `ServiceSummaryRow(...)` fed by the new aggregate (UI-SPEC line 189).
2. Add an `onOpenServiceHistory: () -> Unit` parameter alongside `onEdit`/`onBack`/`onDeleted` (`:97-102`) and thread it into `VehicleDetailContent` — navigation stays injected (`App.kt` wires it).
3. Import the moved `ConfirmDeleteDialog` from `ui/components/` and pass the vehicle copy explicitly.

The `Spacer(48.dp)` above and below (`:302-304`) stay — UI-SPEC line 96 says the row "takes the exact break-above it had".

#### `ui/vehicles/components/ComingSoonSlot.kt` — DELETED

Its own KDoc (`ComingSoonSlot.kt:21-29`) predicted this: *"When Phase 2 lands it replaces or wraps this slot — nothing outside this file needs to change."* Delete the file **and** `detail_service_history_coming_soon` from `strings.xml:52` in the same commit (UI-SPEC line 397).

---

### Group I — Tests

**Test-layer convention (all of them):** no assertion on internal flags — assert observable outcomes.

#### `commonTest/…/data/ServiceEntryEntityMapperTest.kt`

**Analog:** `commonTest/…/data/VehicleEntityMapperTest.kt:10-94` — constructs the generated row entity directly (no DB), asserts one-to-one column mapping, asserts absent optionals stay `null`, asserts the `toBindParameters` output, and asserts the unknown-string fallback (`:80-93`). Phase 2 adds: part/photo child mappers, `cost_rupiah` null vs set, and the enum/plain-string service type.

#### `commonTest/…/util/ServiceDatesTest.kt`, `RupiahFormatTest.kt`, `domain/ServiceTypeVocabularyTest.kt`

**Analog:** `commonTest/…/ui/vehicles/VehicleFormValidationTest.kt:15-70` — table-driven pure-function assertions with explicit edge cases and a comment citing the decision (`// D-13: "no range validation — deliberately"`). Add: `toStorage`/`fromStorage` round-trip and the `display` string; `formatRupiah(450_000) == "Rp 450.000"` plus the 12-digit cap; the learned-type filter collapsing a case-insensitive preset match (RES §Pattern 3).

#### `iosTest/…/data/ServiceEntryRepositoryTest.kt`

**Analog:** `iosTest/…/data/VehicleRepositoryTest.kt:27-118` — scratch DB name with `Random.nextLong`, `createDriverFactory(databaseName = …)`, `VehicleRepositoryImpl(database)` under test, `driver.close()` + `deleteTestDatabaseFiles(databaseName)` in teardown:
```kotlin
    private val databaseName = "vehicare-repo-test-${Random.nextLong(0, Long.MAX_VALUE)}.db"

    @BeforeTest
    fun setUp() {
        driver = createDriverFactory(databaseName = databaseName).createDriver()
        database = VehiCareDatabase(driver)
        repository = VehicleRepositoryImpl(database)
    }
```
Add: save-add → child rows written with positions; save-edit → children fully replaced (no orphan rows); the two history orders returning the documented sequences; summary aggregate; learned types cross-vehicle.

#### `iosTest/…/data/ServiceEntryDeletionTest.kt` (+ vehicle cascade)

**Analog:** `iosTest/…/ui/vehicles/VehicleDeletionTest.kt` — the strongest test in the repo for this phase's riskiest behaviour.

- **File assertions on the real simulator filesystem** (`:94-147`): `assertFalse(PhotoStorage.resolve(relativePath).exists(), "deleting a vehicle must remove its photo file too (D-07, Pitfall 4)")`.
- **Wiring mirrors production** (`:78-81`): `repository = VehicleRepositoryImpl(database, photoDeleter = PhotoStorage::delete)` with the comment saying so.
- **ViewModel half with `Dispatchers.setMain(StandardTestDispatcher())`** (`:59-75`), `/`gate`/`failure` recording decorator (`:301-315`):
```kotlin
private class RecordingVehicleRepository(
    private val delegate: VehicleRepository,
    private val gate: CompletableDeferred<Unit>? = null,
    private val failure: Throwable? = null,
) : VehicleRepository by delegate {
    val deleteCalls = mutableListOf<Long>()
    override suspend fun delete(id: Long) {
        deleteCalls += id
        gate?.await()
        failure?.let { throw it }
        delegate.delete(id)
    }
}
```
Notes for Phase 2: the decorator can be copied for `ServiceEntryRepository`; the `-insert`/`-delete` state can be asserted inside the `onDeleted` callback (the trick at `:160-183` proves "success only after the repository returned"). Add the RES §Pitfall 5 cases: abandon (`onCleared`) deletes only session-owned files, failed save deletes nothing, and the vehicle cascade removes **every** entry photo.

#### `iosTest/…/util/PhotoStorageTest.kt` — EXTENDED

**Analog:** itself (`:46-186`) — `persistCopiesThePickedBytesAndReturnsARelativePath` asserts `returned == "vehicles/$fileName"` (`:69`), and `private companion object { const val directoryName = "vehicles" }` (`:172-174`). Phase 2 adds an equivalent case for the `service_entries` directory (returned path prefixed with it, file exists, `persistCreatesTheStorageDirectoryWhenItIsAbsent` still passes for the second directory). The synthetic-bytes + unique-name + teardown discipline (`:176-186`) carries over unchanged.

#### `androidDeviceTest/…/data/db/ServiceMigrationTest.kt` *(optional — research Open Q 2)*

**Analog:** `androidDeviceTest/…/data/db/WalModeTest.kt:28-118` — `@RunWith(AndroidJUnit4::class)`, `InstrumentationRegistry.getInstrumentation().targetContext`, scratch DB name, `context.deleteDatabase(databaseName)` in teardown. CI has **no emulator job** (`ci.yml` runs `testAndroidHostTest` + `iosSimulatorArm64Test` only), so this is a local pre-verification step: open a v1 database, apply the migration, assert the three tables exist.

---

## Shared Patterns

### 1. Serialized DB lane — never `Dispatchers.IO` inline
**Source:** `VehicleRepositoryImpl.kt:26-29`
**Apply to:** every `ServiceEntryRepositoryImpl` method, including the pure `asFlow()` reads.
```kotlin
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
```
Guard rationale in the class KDoc (`:16-25`): concurrent `NativeSqliteDriver` access aborts the iOS process.

### 2. Mapper boundary — no generated type above `data/`
**Source:** `VehicleEntityMapper.kt:7-22`; `VehicleRepository.kt:6-11`
**Apply to:** repository, mappers, every ViewModel, every composable. The alias-import trick (`import com.vehicare.db.Vehicle as VehicleEntity`) keeps row types visibly boxed in `data/`.

### 3. Writes: one transaction, id from `lastInsertId()`
**Source:** `VehicleRepositoryImpl.kt:44-58`
**Apply to:** `save`, `delete`, the vehicle cascade. `RETURNING` is unavailable at minSdk 26 (`Vehicle.sq:33` supplies `lastInsertId: SELECT last_insert_rowid();`).

### 4. Files are deleted only after the rows are durable
**Source:** `VehicleRepositoryImpl.kt:75-83`; `PhotoStorage.kt:72-87`
**Apply to:** entry delete, vehicle cascade, photo-replacement bookkeeping. Read paths → delete rows in the transaction → delete files after.

### 5. One-shot failure flags retire with their message
**Source:** `VehicleEditViewModel.kt:202-206, 280-283`; consumed at `VehicleEditScreen.kt:99-117`; `VehicleDetailViewModel.kt:126-135`
**Apply to:** every Phase 2 save/delete/photo-error surface.

### 6. State matrices that never paint blank
**Source:** `VehicleListViewModel.kt:20-33`, `VehicleDetailViewModel.kt:19-32`, `VehicleEditViewModel.kt:23-51`
**Apply to:** all three service screens — Loading / Empty / Content / Error (history), Loading / NotFound / Content (detail + edit).

### 7. Shell-owned success snackbar for popped screens
**Source:** `App.kt:78-88` + `:120-128`
**Apply to:** entry delete ("Service entry deleted.") — launch the message on the shell's scope before the pop.

### 8. Navigation injected, graph owned by `App.kt`
**Source:** `VehicleListScreen.kt:42-57` (`onAddVehicle` / `onOpenVehicle` params); `App.kt:90-131`
**Apply to:** all three new screens. No composable holds a `NavController`.

### 9. Palette-declared colours and 48dp targets on every new control
**Source:** `VehicleTypePicker.kt:45-69`; `ConfirmDeleteDialog.kt:80, 91`; `FieldRow`, `PhotoField`, `EmptyState`
**Apply to:** chips, segmented sort control, part-row remove buttons, dialog buttons, photo remove overlays. M3 defaults are overridden, never inherited.

### 10. Strings only via `vehicare.shared.generated.resources.Res`
**Source:** every UI file (e.g. `VehicleEditScreen.kt:52-69`)
**Apply to:** every new composable. No inline literal, including the `· `separator — that one is a **code constant** (`vehicleMetaLine`), the Phase 1 precedent (UI-SPEC line 399).

### 11. Deletion always runs through the confirm dialog
**Source:** `ConfirmDeleteDialog.kt:26-28, 37-38`; `VehicleDetailScreen.kt:134-137, 209-222`
**Apply to:** entry delete. The dialog's confirm is the single call site of the ViewModel's delete; no long-press, no swipe anywhere.

### 12. Migration guard is already wired — keep it green
**Source:** `ci.yml:31-39`; `shared/build.gradle.kts:97-105`
**Apply to:** the `.sq` + `1.sqm` + `2.db` change set. Never delete `1.db`; never hand-edit `*.db`.

---

## No Analog Found

| File | Role | Data Flow | Reason | Substitute convention |
|---|---|---|---|---|
| `shared/src/commonMain/sqldelight/com/vehicare/db/migrations/1.sqm` | migration | CRUD | The repo has no migration yet — `migrations/` holds only `.gitkeep` | `02-RESEARCH.md` §Pattern 1 (lines 263-283) + §Code Examples (lines 519-557); DDL must be byte-identical to `ServiceEntry.sq`; CI guard at `ci.yml:31-39` proves it |

Every other planned file has a first-party analog. The four "role-match / partial" entries in Group F (`PartsEditor`, `ServiceTypePicker`, `ServiceDateField`, `ServiceSummaryRow`, `ServicePhotoStrip`) are **generalizations of an existing single-item component**, not new inventions — the planner should state the generalization explicitly ("extend `PhotoField`'s control grammar to N rows") rather than pointing at a component that renders something else.

---

## Metadata

**Analog search scope:** whole repository — `shared/src/commonMain/kotlin/com/vehicare/{data,domain,di,ui,navigation,util}`, `shared/src/commonMain/sqldelight/`, `shared/src/commonMain/composeResources/`, `shared/src/{commonTest,iosTest,androidDeviceTest,androidMain,iosMain}/`, `gradle/libs.versions.toml`, `shared/build.gradle.kts`, `.github/workflows/ci.yml`, `.gitignore`
**Files scanned:** 30 (all Phase 1 implementation + test files, plus build/CI config)
**Analog matches:** 33 exact · 11 role-match · 1 partial · 1 none
**Pattern extraction date:** 2026-10-19
**Authoritative inputs:** `02-CONTEXT.md` (D-23…D-40 + carry-forwards), `02-RESEARCH.md` (stack, schema, storage, date, query patterns), `02-UI-SPEC.md` (binding design contract), `01-PATTERNS.md` (superseded where §Corrections says so), and the shipped Phase 1 source.
**Planner note:** every "copy the pattern from X" action should cite the **file + line range** in this document, because those line numbers refer to code that exists in this working tree — not to research sketches. Where this document's §Corrections disagrees with `01-PATTERNS.md` or `02-RESEARCH.md`, the corrections win; they were derived by reading the shipped code.
