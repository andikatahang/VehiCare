---
phase: 1
slug: foundation-vehicle-management
status: verified
threats_open: 0
asvs_level: 1
created: 2026-10-19
---

# Phase 1 — Security

> Per-phase security contract: threat register, accepted risks, and audit trail.
> Register authored at plan time (all ten PLAN.md files carry a `<threat_model>` block):
> **72 threats — 68 mitigate, 4 accept, 0 transfer.**
> Verified against implemented code at HEAD `0757e7c`; no implementation file was modified by this audit.

---

## Trust Boundaries

| Boundary | Description | Data Crossing |
|----------|-------------|---------------|
| build machine → Maven Central / Google Maven (01-01) | Third-party artifacts are fetched and executed by the build; untrusted by default | dependency coordinates, build-tool code |
| repo → git remote (01-01) | `local.properties` (machine SDK path) and any future keystore path must never leave the machine | machine paths, credentials |
| Android OS → app data dir (01-01) | `android:allowBackup` decides whether the OS may copy the database and photos off-device | database, photo files |
| Android Activity / iOS UIViewController → shared composable (01-02) | Platform host code is trusted and must not fork UI (D-17) | rendered UI tree |
| app bundle → user (01-02) | All rendered text must come from the vetted in-repo string resources | user-facing copy |
| compose resources → rendered vector art (01-02) | Bundled vector XML is parsed by the Compose resources pipeline on both platforms | drawable XML |
| user input → SQLite (01-03) | Free-text name/make/model/year/plate and a photo path cross into SQL | personal vehicle data |
| shared code → platform SQL driver (01-03) | The `expect/actual` seam; the only genuinely platform-divergent data-layer code | SQL statements, bound parameters |
| app process → device storage (01-03) | The database file and photo files are the user's personal data at rest | database, photo files |
| domain layer → generated SQLDelight types (01-03) | Schema-coupling boundary; must stay one-directional | generated row types |
| user → text field (01-04) | Free-text vehicle name (later: all six fields) | personal vehicle data |
| ViewModel → repository (01-04, 01-07, 01-10) | Domain objects only; no generated types may cross | `Vehicle` domain objects |
| shared navigation → platform back gesture (01-04) | iOS predictive-back and Android system back both drive the shared NavHost | navigation state |
| app version accessor → OS (01-04) | Reading the app's own version string | app version |
| repo → CI runner (01-05) | Workflow definition executes with repository write access | repository contents, GITHUB_TOKEN |
| schema source (`.sq`) → shipped app (01-05) | A schema change without a migration corrupts existing users' databases on update | database schema |
| test process → machine filesystem (01-05) | Tests create and delete database files and sidecars | scratch database files |
| database → UI (01-06, 01-09) | A stored `photoPath`/nullable text is turned into a filesystem path, a bitmap and screen-reader output | personal data, image data |
| user gesture → navigation (01-06) | A card tap and a FAB tap are the only navigation triggers on the list | navigation intent |
| `PhotoStorage` → app-private filesystem (01-06) | The only filesystem read on the list screen | photo files |
| Coil → OS image decoder (01-06) | A corrupt or hostile image could reach the decoder if a file were placed in the photo directory | image bytes |
| user → form fields (01-07) | Six free-text fields plus a constrained type selection | personal vehicle data |
| repository → form (01-07) | An existing vehicle's stored values are loaded back into an editable form | personal vehicle data |
| Snackbar → user (01-07) | Success and failure must be distinguishable after the fact | outcome copy |
| OS photo picker → app (01-08) | An arbitrary user-selected image and the picker's short-lived temporary location cross into the app | image bytes, temporary location |
| app → private filesystem (01-08) | Compressed image bytes are written into the app's own directory | derived image file |
| database → filesystem (01-08, 01-09) | A stored `photo_path` string is turned into a filesystem path at read time | photo file |
| ViewModel → repository seam (01-08) | The photo-deletion callback is injected from the DI module | deletion intent |
| route argument → screen (01-09) | A `Long` id arrives from the typed route and is used to fetch a row | row identity |
| app shell → navigation (01-09) | Bottom-bar visibility depends on the current destination | navigation state |
| user → destructive action (01-10) | One tap leads to irreversible data loss; the confirmation is the only brake | personal data destruction |
| repository → filesystem (01-10) | Deleting the row also deletes the user's image file from private storage | photo file |
| Detail screen → app shell (01-10) | The completion signal crosses a navigation boundary that destroys the emitting screen | success notification |

---

## Threat Register

| Threat ID | Category | Component | Disposition | Mitigation | Status |
|-----------|----------|-----------|-------------|------------|--------|
| T-01-01 | Tampering | `shared/build.gradle.kts` dependency resolution | mitigate | Every alias resolves through the single catalog (`gradle/libs.versions.toml`); zero version literals in any module build file; live `:shared:dependencies --configuration commonMainImplementation` shows all CMP-versioned artifacts at one version (1.12.1) with `material3` 1.12.0-alpha03 / `material-icons-core` 1.7.3 as separately pinned tracks — literal single-version clause resolved by VERIFICATION override G-1 | closed |
| T-01-02 | Information Disclosure | `local.properties` (Android SDK path) | mitigate | `.gitignore:7` matches `local.properties` (`git check-ignore -v` → hit); not tracked (`git ls-files` empty); `gradle.properties` carries no credential | closed |
| T-01-03 | Information Disclosure (high) | Android manifest — `android:allowBackup` | mitigate | `androidApp/src/main/AndroidManifest.xml:5` sets `android:allowBackup="false"`; it is the repository's **only** manifest — the plan's `:shared/src/androidMain/AndroidManifest.xml` path was moved by the approved two-module split (01-01-SUMMARY D-5) | closed |
| T-01-04 | Tampering | DB moved off-device via `content://` / shared URI | mitigate | Zero `FileProvider`/`<provider>` declarations and zero storage permissions in any manifest or source file; photos are app-private only (D-05) | closed |
| T-01-05 | Elevation of Privilege | Manifest permissions | mitigate | Manifest declares **zero** `uses-permission`; the single activity carries only MAIN/LAUNCHER; no CAMERA, READ_MEDIA_*, READ/WRITE_EXTERNAL_STORAGE anywhere | closed |
| T-01-06 | Tampering | SQLDelight migration enforcement | mitigate | `shared/build.gradle.kts:101` sets `schemaOutputDirectory` — the two per-source-set tasks exist (`verifyCommonMainVehiCareDatabaseMigration`, `generateCommonMainVehiCareDatabaseSchema`), observed in `:shared:tasks` | closed |
| T-01-07 | Spoofing | n/a — no accounts, sessions or network surface in Phase 1 | accept | Accepted risk **AR-01** (see Accepted Risks Log) | closed |
| T-01-SC | Tampering | Maven Central / Google Maven installs (Kotlin, CMP, SQLDelight, Koin, navigation, FileKit, Coil, material-icons-core) | mitigate | 01-RESEARCH.md §Package Legitimacy Audit: all 8 package families OK on Maven Central, 0 `[SLOP]` / 0 `[SUS]` / no `[ASSUMED]`; shipped coordinates cross-verified (01-VERSIONS-OPTION-B) | closed |
| T-01-08 | Tampering | `composeResources/**` (vectors + strings) | mitigate | All four resource files are in-repo XML: no `<a href>`, no `<script>`, no remote fetch (only `xmlns:` namespace URIs — identifiers, not URLs); the three vehicle drawables are single-path with fixed `fillColor="#FF1C1C1E"` and no `tint` | closed |
| T-01-09 | Information Disclosure | iOS host / `ContentView.swift` | mitigate | `ContentView.swift:13-18`, `iOSApp.swift:5-8` and `MainViewController.kt:11` only construct the shared controller; `Info.plist` holds no URL scheme, no document-picker/file-sharing key and the project declares no entitlements file | closed |
| T-01-10 | Spoofing | Android `MainActivity` intent filter | mitigate | `AndroidManifest.xml:11-19` — one activity, `android:exported="true"`, MAIN/LAUNCHER only; no `<data>` deep link, no second exported component | closed |
| T-01-11 | Tampering | Theme tokens (`Color.kt`, `Theme.kt`) | mitigate | `Theme.kt:13-18` applies `VehiCareLightColors` unconditionally; zero occurrences of `isSystemInDarkTheme` / `dynamicLightColorScheme` / `dynamicDarkColorScheme` / `dynamicColor` in the repo | closed |
| T-01-12 | Repudiation / Data lifecycle | Inline literals drifting from vetted copy | mitigate | 49 keys in `composeResources/values/strings.xml`; `App.kt:113` resolves `stringResource(Res.string.snackbar_vehicle_deleted)`; no user-facing literal in `App.kt` | closed |
| T-01-SC | Tampering | Dependency installs (01-02) | mitigate | No package added — `gradle/libs.versions.toml` untouched by this plan; RESEARCH audit applies | closed |
| T-01-13 | Tampering (high) | `Vehicle.sq` queries | mitigate | All five queries are `?`-placeholder only (`Vehicle.sq:17-33`); the repository binds named typed parameters (`VehicleRepositoryImpl.kt:44-73`); zero `rawQuery`/`execute(`-with-string/`prepareStatement`/`createStatement` call sites in the codebase | closed |
| T-01-14 | Information Disclosure (high) | `vehicle.photo_path` column | mitigate | `Vehicle.sq:14` declares `photo_path TEXT` — no BLOB, no URI type; zero `content://` / `file://` / absolute-path writes anywhere; `persist` returns `vehicles/<name>` and the test asserts non-absolute **and** base-directory-free (`PhotoStorageTest.kt:69-76`) | closed |
| T-01-15 | Denial of Service (high) | `VehicleRepositoryImpl` concurrent driver access | mitigate | Single injected lane `Dispatchers.IO.limitedParallelism(1)` (`VehicleRepositoryImpl.kt:28`), used by `withContext` in `save`/`delete` (`:44`, `:75`) and by `mapToList`/`mapToOneOrNull` (`:35`, `:41`); **no other `Dispatchers.IO` in main source** (only the iOS test's `setMain`) | closed |
| T-01-16 | Tampering | Bypassing the library (direct SQLite file access) | mitigate | Driver built once inside `AppModule.kt:28-31` → `DatabaseFactory.kt:13-14`; `VehicleRepositoryImpl` is `internal` and bound as `single<VehicleRepository>`; only the repository touches `vehicleQueries`; the `SqlDriver` is not in the DI graph (residual: RO-01) | closed |
| T-01-17 | Information Disclosure | Logs / crash reports | mitigate | Zero `println` / `Log.` / `NSLog` / `Timber` call sites in `commonMain`, `androidMain`, `iosMain` or `androidApp`; failures surface as typed state or one-shot flags only | closed |
| T-01-18 | Information Disclosure / Data lifecycle | Orphaned photo files after delete | mitigate | Injected `photoDeleter` seam (`VehicleRepositoryImpl.kt:29`, invoked `:82`) bound to the real `PhotoStorage::delete` (`AppModule.kt:36`); real-filesystem assertion that the file is gone (`VehicleDeletionTest.kt:107-110`) | closed |
| T-01-19 | Tampering | Unrecognised `type` value from a newer version | mitigate | `VehicleType.kt:20` — `fromDb` falls back to `Car` instead of throwing; unit test `VehicleEntityMapperTest.kt:80` | closed |
| T-01-20 | Information Disclosure | Network egress (offline-first claim) | mitigate | Absent by construction: zero okhttp/ktor/retrofit/analytics/crash-SDK coordinates in the catalog or build files; **live resolution** reports 0 such artifacts on `:shared:commonMainImplementation` and 0 on `:androidApp:releaseRuntimeClasspath` (1,351 nodes) — the only `analytics` match is AGP *build tooling* (`com.android.tools.analytics-library` on an internal test-tooling configuration), never a runtime dependency | closed |
| T-01-21 | Tampering | SQLDelight migration drift (high) | mitigate | `shared/src/commonMain/sqldelight/databases/1.db` is committed (`git ls-files`); `migrations/` contains only `.gitkeep` (zero `.sqm`); CI runs both guards (`ci.yml:31-39`) | closed |
| T-01-SC | Tampering | Dependency installs (01-03) | mitigate | No new package; SQLDelight/Koin/coroutines/serialization declared in 01-01 and OK in the RESEARCH audit | closed |
| T-01-22 | Tampering | User-supplied vehicle name reaching SQL | mitigate | The name becomes a domain `Vehicle` field (`VehicleFormValidation.kt:66-77`) and `repo.save` binds it as a parameter; zero `app.cash.sqldelight` imports under `ui/` or `domain/` | closed |
| T-01-23 | Denial of Service | Double-submit creating duplicate rows | mitigate | Submit `enabled = isSubmittable(values) && !saving` (`VehicleEditScreen.kt:324`) with every field disabled while saving (`:239,:261,:270,:281,:292`) plus the ViewModel's `saving` early return (`VehicleEditViewModel.kt:180`) | closed |
| T-01-24 | Elevation of Privilege | Multiple `KoinApplication` / widened DI surface | mitigate | Exactly one `KoinApplication` (`App.kt:66`); ViewModels registered with `viewModel {}` (`AppModule.kt:38-44`) and resolved only via nav-scoped `koinViewModel()` (`VehicleListScreen.kt:56`, `VehicleEditScreen.kt:89`, `VehicleDetailScreen.kt:103`) | closed |
| T-01-25 | Tampering | Android-only symbols leaking into `commonMain` | mitigate | `platformModule` is a parameter (`App.kt:54` ← `MainActivity.kt:23`); zero matches for `org.koin.androidx` / `androidx.activity` / `import android.` under `shared/src/commonMain` | closed |
| T-01-26 | Information Disclosure | Run docs leaking paths or secrets | mitigate | `README.md` contains zero `/Users/`, `/home/`, credential or SDK-path literals; the SDK path stays in the git-ignored `local.properties` | closed |
| T-01-27 | Spoofing | iOS back gesture diverging from the Android back stack | mitigate | Exactly one `NavHost` (`App.kt:90`) with typed `composable<…>` entries; no platform-specific navigation container exists | closed |
| T-01-28 | Information Disclosure | Persisted data location vs the offline-first claim | mitigate | No network client (T-01-20 evidence); no analytics or crash reporter; `README.md:52-61` documents app-private storage and "no data leaves the device, ever" | closed |
| T-01-SC | Tampering | Dependency installs (01-04) | mitigate | No new package; navigation/lifecycle/Koin declared in 01-01 and OK in the RESEARCH audit | closed |
| T-01-29 | Tampering (high) | `.github/workflows/ci.yml` migration gate | mitigate | `ci.yml:31-32` per-source-set verify task **plus** `:36-39` `generate…Schema && git diff --exit-code` on the committed baseline; the aggregate task appears zero times in the workflow | closed |
| T-01-30 | Tampering (high) | Schema change shipping without a `.sqm` | mitigate | Same diff guard (`ci.yml:36-39`); guard-fire probe recorded with observed exit codes (`01-05-SUMMARY.md:72-81` — diff guard exit 1, aggregate task exit 0) | closed |
| T-01-31 | Denial of Service | Database corruption on iOS `SIGKILL` | mitigate | WAL established on the first connection: Android row-safe pragma at construction (`DriverFactory.android.kt:15-27`), iOS SQLiter WAL default (`DriverFactory.ios.kt:13-20`); real-driver tests assert mode-before-write + `-wal` sidecar + second-connection persistence (`iosTest/…/WalModeTest.kt:46-86`, `androidDeviceTest/…/WalModeTest.kt:52-95`) — "declared in SQL" mechanism clause resolved by VERIFICATION override G-2 | closed |
| T-01-32 | Information Disclosure | CI workflow leaking credentials | mitigate | Zero `secrets.` references; explicit least-privilege `permissions: contents: read` (`ci.yml:7-10`) | closed |
| T-01-33 | Tampering | Test pollution of real user data | mitigate | Both WAL tests use `wal-probe-<random>.db` and remove the database plus sidecars (`WalModeTest.kt:27,40-50` and `:31,46-50`); neither ever opens the app's `vehicare.db` | closed |
| T-01-34 | Repudiation | Unverified claims of enforcement | mitigate | Recorded exit-code evidence table (`01-05-SUMMARY.md:72-81`), including the observed vacuous pass of the aggregate task and the exit-1 diff guard, with the restored-tree re-verification | closed |
| T-01-SC | Tampering | GitHub Actions used by the workflow | mitigate | Only first-party actions pinned to major tags: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`; no third-party action | closed |
| T-01-35 | Tampering (high) | `vehicle.photo_path` → `PhotoStorage.resolve` | mitigate | The card resolves the stored **relative** path through the single seam (`VehicleCard.kt:78`); zero `absolutePath` / `PlatformFile.path` / `file://` occurrences in `commonMain` | closed |
| T-01-36 | Denial of Service | Coil decode of a huge photo in a scrolling list | mitigate | Coil `AsyncImage` into a 64dp bounded target (`VehicleCard.kt:77-86`); no hand-rolled decoder in production code (the only `BitmapFactory`/`UIImage` use is an iOS **test** helper) (residual: RO-02) | closed |
| T-01-37 | Spoofing / Repudiation | Accidental deletion reachable from the home screen | mitigate | The card's only gesture is `Card(onClick = …)` (`VehicleCard.kt:62-63`); zero `combinedClickable`/`onLongClick` in the repo; no delete reference in the list screen or its ViewModel | closed |
| T-01-38 | Elevation of Privilege | SQLDelight generated types leaking above the data layer | mitigate | `VehicleCard(vehicle: Vehicle, …)` (`VehicleCard.kt:55-56`); zero generated-type imports under `ui/vehicles/components/` | closed |
| T-01-39 | Information Disclosure | Photo content exposed by Coil's disk cache | accept | Accepted risk **AR-02** (see Accepted Risks Log) | closed |
| T-01-SC | Tampering | Dependency installs (01-06) | mitigate | No new package; `coil-compose` and `filekit-coil` declared in 01-01 and OK in the RESEARCH audit (versions cross-checked against each library's own catalog) | closed |
| T-01-40 | Tampering (high) | Six free-text fields → SQLite | mitigate | The ViewModel builds a `Vehicle` and calls `repo.save(...)` (`VehicleEditViewModel.kt:188`), which binds every field as a parameter; zero `app.cash.sqldelight` imports in the ViewModel or screens | closed |
| T-01-41 | Denial of Service | Double-tap on submit creating duplicate rows | mitigate | State-machine early return while `saving` **or** `importing` (`VehicleEditViewModel.kt:180`); the button is replaced by a progress indicator and inputs are disabled (`VehicleEditScreen.kt:320-334`, `:239`, `:305`) | closed |
| T-01-42 | Tampering | Year field accepting arbitrary input | mitigate | `sanitizeYear` filters to digits and caps at four characters (`VehicleFormValidation.kt:57`), applied on every change (`VehicleEditScreen.kt:277`) with a numeric keyboard request (`:282`); the value is stored as bound text (residual: RO-06) | closed |
| T-01-43 | Information Disclosure | User data in logs / crash reports | mitigate | No logging call sites; failures become one-shot flags (`VehicleEditViewModel.kt:197`, `:235`) or `NotFound` (`:127-132`) and render as vetted copy only | closed |
| T-01-44 | Tampering / Data integrity | Edit silently clearing untouched fields (notably `photoPath`) | mitigate | Only the first repository emission is applied (`VehicleEditViewModel.kt:100`, `:134-135`); `toVehicle` preserves `type`/`photoPath` and nulls only the blank field (`VehicleFormValidation.kt:66-77`); tests `VehicleFormValidationTest.kt:127-150` | closed |
| T-01-45 | Repudiation | Failed save presented as success | mitigate | Failure keeps every value and sets `saveFailed` (`VehicleEditViewModel.kt:194-198`); `onSaved()` runs only after `repo.save` returned (`:191`); Snackbar copy `VehicleEditScreen.kt:102-107`; no optimistic local list exists (residual: RO-03) | closed |
| T-01-SC | Tampering | Dependency installs (01-07) | mitigate | No new package; `SingleChoiceSegmentedButtonRow`/`FilterChip` ship with CMP's Material 3 already declared in 01-01 | closed |
| T-01-46 | Information Disclosure (high) | `vehicle.photo_path` column contents | mitigate | `persist` returns only `vehicles/<fileName>` (`PhotoStorage.kt:55-61`); test asserts non-absolute and free of the resolved base directory (`PhotoStorageTest.kt:69-76`); the schema has no BLOB/URI variant at all (residual: RO-05) | closed |
| T-01-47 | Information Disclosure (high) | Photo readable by another app or exposed via a provider | mitigate | Written under `FileKit.filesDir` — Android `filesDir` / iOS container Documents — (`PhotoStorage.kt:58-59`); no `FileProvider`, no storage permission and no share intent anywhere in source; iOS `Info.plist` has no file-sharing/document keys | closed |
| T-01-48 | Denial of Service | Huge or malformed gallery image exhausting memory | mitigate | `compressImage` with a named `MAX_IMAGE_DIMENSION = 1280` before writing (`PhotoStorage.kt:38`, `:93-101`); test asserts the stored dimensions are capped (`PhotoStorageTest.kt:149-166`); hand-rolled decode remains absent | closed |
| T-01-49 | Tampering / Data lifecycle | Orphaned or prematurely deleted photo files | mitigate | Supersede-after-durable-save (`VehicleEditViewModel.kt:317-322`), remove path (`:264-274`), abandon in `onCleared` (`:294-300`), and `photoDeleter` bound to `PhotoStorage::delete` (`AppModule.kt:36`); the plan's "`onCleared` also deletes a pending superseded path" step is deliberately **not** implemented because it would destroy the row's only copy (01-08-SUMMARY deviation 1) (residual: RO-04) | closed |
| T-01-50 | Spoofing / Privilege broadening | Camera capture path or camera permission creeping in | mitigate | Launcher restricted to `FileKitType.Image` (`PhotoField.kt:72`); `grep -ri camera shared/src` → 0 matches; zero CAMERA/READ_MEDIA permission declarations | closed |
| T-01-51 | Elevation of Privilege | Persisting the picker's temporary location | mitigate | `persist` always writes its own file and returns its own relative path (`PhotoStorage.kt:55-61`); the byte-equality assertion proves the data was copied rather than referenced (`PhotoStorageTest.kt:78`) | closed |
| T-01-SC | Tampering | Dependency installs (01-08) | mitigate | No new package; FileKit 0.16.0 / Coil 3.6.3 declared in 01-01 and OK in the RESEARCH audit | closed |
| T-01-52 | Tampering | Route id used to fetch an arbitrary row | accept | Accepted risk **AR-03** (see Accepted Risks Log) | closed |
| T-01-53 | Information Disclosure | Personal data rendered into accessibility nodes | accept | Accepted risk **AR-04** (see Accepted Risks Log) | closed |
| T-01-54 | Tampering (high) | Hero photo resolved from a stored path | mitigate | `AsyncImage(model = PhotoStorage.resolve(photoPath), …)` inside a bounded 4:3 frame (`VehicleDetailScreen.kt:274-284`); zero `absolutePath`/`file://`/`PlatformFile.path` in `commonMain` — the single-seam rule holds on Detail too | closed |
| T-01-55 | Repudiation / Data integrity | Stale Detail screen showing edited-elsewhere values | mitigate | `uiState` streams from the repository with `WhileSubscribed(5_000)` (`VehicleDetailViewModel.kt:54-70`); a deleted row becomes `NotFound`; the screen caches no copy of the vehicle | closed |
| T-01-56 | Spoofing | Destructive affordance reachable before plan 01-10 | mitigate | The inert stub recorded in `01-09-SUMMARY.md:172` was turned on in exactly one place (`VehicleDetailScreen.kt:205`, `:214-219`) together with the confirmation dialog and the photo-file assertion; a partially wired destructive action does not exist | closed |
| T-01-SC | Tampering | Dependency installs (01-09) | mitigate | No new package; Coil, FileKit and the CMP Material 3 components were declared in 01-01 and cleared the RESEARCH audit | closed |
| T-01-57 | Tampering (high) | Irreversible deletion reachable without confirmation | mitigate | The button only sets `showDeleteDialog = true` (`VehicleDetailScreen.kt:205`); exactly **one** `viewModel.delete(...)` call site, inside the dialog's `onConfirm` (`:214-219`); every non-confirming exit routes to `onDismiss` (`ConfirmDeleteDialog.kt:37-38`, `:57`, `:88-96`); zero long-press gestures repo-wide | closed |
| T-01-58 | Information Disclosure (high) | Orphaned photo file surviving its vehicle's deletion | mitigate | `AppModule.kt:36` binding plus real-filesystem assertions: row gone, list-flow entry gone and `PhotoStorage.resolve(relativePath).exists()` false (`VehicleDeletionTest.kt:95-111`), and a second vehicle's row **and** file untouched (`:133-147`); the ViewModel test asserts the file is gone *before* success is reported (`:150-183`) | closed |
| T-01-59 | Denial of Service | Repeated confirms enqueueing repeated deletes | mitigate | `if (deleting.value) return` single-flight guard (`VehicleDetailViewModel.kt:109-110`); the destructive button is `enabled = !deleting` (`VehicleDetailScreen.kt:204`, `:312`); gated test proves one repository call per accepted tap (`VehicleDeletionTest.kt:186-222`) | closed |
| T-01-60 | Repudiation | Failed delete reported as success, or vehicle hidden while stored | mitigate | `onDeleted()` runs only after `repo.delete` returned without throwing (`VehicleDetailViewModel.kt:112-115`); on failure `deleteFailed` is raised, the screen and the vehicle stay (`:119-122`, `VehicleDetailScreen.kt:127-132`); `CancellationException` is rethrown; no optimistic removal to roll back; tests `VehicleDeletionTest.kt:224-258` | closed |
| T-01-61 | Spoofing / Misleading affordance | Safe option looking destructive, or vice versa | mitigate | Confirm is the only danger-tinted **dialog** control (`ConfirmDeleteDialog.kt:77-87`); Cancel is `Ink` and deliberately not danger-tinted (`:88-96`); both are worded and ≥48dp; the copy names the vehicle and the scope (`dialog_delete_message`) with a guaranteed non-blank name (`VehicleDetailScreen.kt:213`) | closed |
| T-01-62 | Repudiation | Deleted vehicle reappearing after the pop to the list | mitigate | The list is Flow-driven with `WhileSubscribed(5_000)` (`VehicleListViewModel.kt:51-65`) — no cached list to resurrect a row; the success Snackbar is hosted on the shell and launched before the pop (`App.kt:83-88`, `:125-128`); asserted at `VehicleDeletionTest.kt:102-106`, `:176-179` | closed |
| T-01-SC | Tampering | Dependency installs (01-10) | mitigate | No new package; the dialog, Snackbar and icon come from CMP's Material 3 plus the pinned `material-icons-core` declared in 01-01 and cleared by the RESEARCH audit | closed |

*Status: open · closed*
*Disposition: mitigate (implementation required) · accept (documented risk) · transfer (third-party)*

**Threat-flag review (SUMMARY.md documents):** no plan SUMMARY carries a `## Threat Flags` section for this phase; the threat-related evidence appears as `## Threat Model Compliance` (01-02) and as inline T-01-* notes elsewhere. Every new-attack-surface note found was checked — none introduced an unmapped surface:

- 01-05: `permissions: contents: read` added to the workflow — a **reduction** of surface, self-described as "T-01-32 adjacent" (maps to T-01-32).
- 01-01/01-02 asset work (launcher vectors, iOS `AppIcon-1024.png` RGB/no-alpha, `Config.xcconfig` bundle id) — in-repo artwork and build identity, no surface (maps to T-01-08's "in-repo XML only" and T-01-05's zero-permission posture).
- 01-07/01-08/01-09/01-10 added state flags and one shell handoff parameter (`saveFailed`, `importing`, `photoError`, `deleteFailed`, `onDeleted`), and 01-09 added a `.catch` → `NotFound` path — UI state and error handling only, no new entry point, permission, provider or data path.
- 01-03 moved the WAL mechanism into the per-platform driver factories — a mechanism change inside an existing threat (T-01-31), documented and re-proven by real-driver tests.

**Unregistered flags: none.**

---

## Accepted Risks Log

| Risk ID | Threat Ref | Rationale | Accepted By | Date |
|---------|------------|-----------|-------------|------|
| AR-01 | T-01-07 (Spoofing) | Phase 1 has no accounts, sessions, credentials or network surface at all — DAT-01 mandates local-only, account-free storage and RESEARCH records the ASVS V2/V3 identity categories as not applicable. There is no identity for a spoofer to forge. Revisit when any account, sync or multi-user feature is introduced. | andikatahang | 2026-10-19 |
| AR-02 | T-01-39 (Information Disclosure) | Coil's memory and disk cache live inside the same app-private container as the photo itself (`FileKit.filesDir`), so caching introduces no new exposure surface and no path another app can reach. Phase 1 ships no share/export path and `android:allowBackup="false"`. Revisit only if an export or share feature is added. | andikatahang | 2026-10-19 |
| AR-03 | T-01-52 (Tampering) | Single-user, fully offline, account-free app (DAT-01) with no cross-user data: every row belongs to the device's owner, so the route id crosses no privilege boundary. The id is bound as a typed parameter and an unknown id yields `NotFound` rather than an error. Revisit if multi-profile or shared-device handling is introduced. | andikatahang | 2026-10-19 |
| AR-04 | T-01-53 (Information Disclosure) | Assistive-technology output on the user's own device is the intended disclosure — it is the value of the feature. No data leaves the app: no analytics, no logging, no export path, and `android:allowBackup="false"`. Revisit if a screenshot or share feature is added. | andikatahang | 2026-10-19 |

*Accepted risks do not resurface in future audit runs.*

---

## Residual Observations (non-blocking)

Recorded so they are not lost; each is a residual **inside** an existing mitigation area or a cross-reference — none is a declared mitigation that is absent from the code, and none blocks phase advancement (`security_block_on: high`).

| Ref | Threat Ref | Observation | Severity |
|-----|-----------|-------------|----------|
| RO-01 | T-01-16 | `single<VehiCareDatabase>` is registered in `AppModule.kt:32` and is therefore resolvable from Koin even though no consumer injects it (the `SqlDriver` itself is not registered). Latent DI surface, not a reachable bypass. | low |
| RO-02 | T-01-36 | `maxBitmapSize` is Coil's built-in default — the app's `ImageLoader` (`App.kt:59-63`) does not set it explicitly. The guard is real (Coil's decoder coerces destination dimensions to `maxBitmapSize`) but it is a library default rather than an app-authored bound. | low |
| RO-03 | T-01-45 | `VehicleRepositoryImpl.kt:60-70` returns the id without checking that the update affected a row, so an edit of a row deleted elsewhere can report "saved" (01-REVIEW **WR-03**). The declared error path (`saveFailed`, Snackbar, retained values, no optimistic list) is implemented as specified; this fourth failure mode was not enumerated by the mitigation plan, so the intent "a failed save is never presented as success" is not fully closed. | medium |
| RO-04 | T-01-49 / T-01-18 | `PhotoStorage.persist` (`PhotoStorage.kt:55-61`) writes straight to the final name; a failed, partial or cancelled write leaves an unreferenced file that nothing ever cleans up, and the monotonic file-name stamp guarantees the name is never reused (01-REVIEW **WR-02**). The three declared lifecycle paths are implemented and tested; this orphan path is not covered by the mitigation plan. | medium |
| RO-05 | T-01-35 / T-01-46 | `PhotoStorage.resolve`/`delete` accept any string and perform no `..` containment check (01-REVIEW **IN-02**). Not reachable today — every caller passes a self-generated name or a value written by this same object — so this is defence-in-depth only. | low |
| RO-06 | T-01-42 | `sanitizeYear` uses `Char.isDigit()`, which is Unicode-aware, so non-ASCII digit characters survive the filter (01-REVIEW **IN-04**). The declared rule ("digits only, cap four") is nonetheless met, and values are always bound as parameters — never interpolated. | low |
| RO-07 | T-01-SC | The shipped version set differs from the RESEARCH-audited pins (e.g. Coil 3.6.3 vs 3.3.0, FileKit 0.16.0 vs 0.11.0, CMP 1.12.1 vs 1.8.2) because the user overrode the pins with the version set already present in their Android Studio project (01-VERSIONS-OPTION-B superseded notice + 01-01-SUMMARY version table). The legitimacy audit is per package (registry / publisher / source repository) and does not change with version; each shipped coordinate is verified to exist. Note that `material3` ships as a **pre-release** (`1.12.0-alpha03`), the user's own pin, recorded as Unresolved #4 of the version contract. | low |
| RO-08 | T-01-08 | `composeResources/drawable/compose-multiplatform.xml` is the unreferenced scaffold placeholder (5 paths, gradients, `aapt:` namespace). It remains fully in-repo XML with no remote reference, so T-01-08 holds; it is dead weight that no screen references. | info |
| RO-09 | — (no threat mapping) | Cross-reference only, **not a security finding**: 01-REVIEW **CR-01** ("Try again" cannot recover the list — `.catch` placed after `flatMapLatest` completes the assembled flow) is a functional defect. The threat register declares no mitigation requiring list-retry recovery, so it is out of scope for this audit and is recorded here only so it is not lost. | n/a (functional) |

---

## Security Audit Trail

| Audit Date | Threats Total | Closed | Open | Run By |
|------------|---------------|--------|------|--------|
| 2026-10-19 | 72 | 72 | 0 | gsd-security-auditor |

**Method.** State B audit: the register was extracted from the ten PLAN.md `<threat_model>` blocks (10 trust-boundary tables, 72 STRIDE rows). Each `mitigate` row was verified by locating its declared mitigation in the cited implementation files at HEAD `0757e7c` — not by re-reading the plans' or summaries' claims. Where the mitigation's stated verification was a command, the command was re-run in this session (dependency resolution for T-01-01/T-01-20; `.gitignore`/`git ls-files` for T-01-02/T-01-21; the source greps behind T-01-04/05/11/25/35/37/50). The four `accept` rows are verified CLOSED by their presence in the Accepted Risks Log above. No implementation file was created or modified by this audit.

**Carried forward from verification:** two must-have clauses in this phase were recorded as developer-accepted overrides in `01-VERIFICATION.md` (G-1: the literal "one `org.jetbrains.compose.*` version" clause, unsatisfiable as written; G-2: the WAL "declared in SQL rather than platform config" mechanism clause). Both are referenced by T-01-01 and T-01-31 above; in each case the *protected intent* was independently re-verified in this audit against the code (single catalog pin per stream / WAL active on the first connection on both platforms).

---

## Sign-Off

- [x] All threats have a disposition (mitigate / accept / transfer)
- [x] Accepted risks documented in Accepted Risks Log (AR-01 … AR-04)
- [x] `threats_open: 0` confirmed
- [x] `status: verified` set in frontmatter

**Approval:** verified 2026-10-19
