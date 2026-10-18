# Walking Skeleton — CarRecord

**Phase:** 1
**Generated:** 2026-10-18
**Mode:** MVP phase 1 (`WALKING_SKELETON=true`, new project)

## Capability Proven End-to-End

A vehicle owner can add a vehicle from the shared form, see it in the shared list, and find it still there after restarting the app — on both Android and iOS from one Kotlin Multiplatform codebase.

This is the thinnest slice that exercises the whole stack: Compose Multiplatform UI → Koin-injected ViewModel → repository → SQLDelight driver → SQLite on device, plus the app-private filesystem seam used by the optional photo.

## Architectural Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Framework | Kotlin Multiplatform + Compose Multiplatform (Material 3), single `composeApp` module | One shared UI for Android and iOS; D-17 forbids platform-specific UI duplication, and CMP's iOS target is stable at the selected version. |
| Build | Gradle Kotlin DSL with a single `gradle/libs.versions.toml` version catalog; `androidTarget` (AGP 8.x path) | Kotlin 2.2.20 requires the AGP 8.x + `androidTarget` configuration; the catalog is the one place versions are pinned, so the aligned-down stack cannot drift. Exact versions are resolved at the plan 01-01 Wave 0 gate. |
| Data layer | SQLDelight (`.sq` schema + committed `databases/1.db` baseline + `migrations/`) with `AndroidSqliteDriver` / `NativeSqliteDriver` behind an `expect`/`actual` driver factory | Compile-time-verified SQL and build-time migration verification are what make DAT-04 enforceable; hand-written `ALTER TABLE` migrations drift silently. |
| Journal mode | `PRAGMA journal_mode=wal;` declared in the `.sq` preamble | Emitted by generated `Schema.create()` on both platforms from first launch, so WAL is active without any platform-specific `onConfigure` — the failure mode that never runs on one of the two platforms. |
| DB concurrency | All reads and writes through one injected `Dispatchers.IO.limitedParallelism(1)` lane | Serialises access to the single SQLite connection and prevents the iOS `SIGABRT` from concurrent `NativeSqliteDriver` access. |
| DI | Koin, with `koinViewModel()` from `org.koin.compose.viewmodel` in `commonMain` and exactly one `KoinApplication` in `App()` | ViewModels must be scoped to nav entries on both platforms; the Compose-Multiplatform Koin overload does that, and the Android-only `org.koin.androidx.*` package cannot compile in shared code. |
| Navigation | JetBrains Navigation Compose with type-safe `@Serializable` routes and one shared `NavHost` | iOS back-swipe and Android system back both drive the same stack, and each nav entry gets its own ViewModel scope for free. |
| Domain boundary | `Vehicle` / `VehicleType` domain models; SQLDelight generated types confined to the data layer behind a mapper | Keeps the UI testable, survives schema regeneration, and makes "UI never touches the DB directly" a structural property rather than a convention. |
| Photos | Gallery-only picker (FileKit), bytes copied into `FileKit.filesDir/vehicles/`, database stores the **relative** path only | Absolute paths and picker temp URLs break after an iOS reinstall, and a BLOB in the row would make the database the wrong size to move or back up. |
| Image loading | Coil 3 with the `filekit-coil` fetcher | Size-aware downsampling plus a memory/disk cache; a hand-rolled decoder is the standard source of OOM crashes in list screens. |
| Strings | Compose Resources (`composeResources/values/strings.xml`, `Res.string.*`) | The only sanctioned way to hold user-facing copy in `commonMain`; Android `R.string` does not exist there, and D-11 requires centralised strings from day one. |
| Theme | Light only, no dynamic colour; near-black ink on white with the accent exposed as one swappable token | D-21 defers the accent colour decision until the user has seen the build, so the accent equals the ink token for now and changing one value re-tints every accent surface. |
| Navigation shell | One app-level `Scaffold`; a shared two-item bottom bar (Vehicles, Settings) shown on the tab roots only | D-17 scaffolds the shell now so later phases fill an existing slot instead of retrofitting navigation. |
| Deployment target | Local full-stack run on both platforms (Android emulator/device API 26+, iOS 16+ simulator) with the commands documented in `README.md` | Phase 1 is a local-first app with no server, so "deployment" is a reproducible local run on both targets. |

## Stack Touched in Phase 1

- [x] Project scaffold (framework, build, lint, test runner) — Gradle multi-target module with Android and iOS targets and a working test task on each
- [x] Routing — at least one real route: `VehicleListRoute` as the start destination, plus Settings, Add/Edit and Detail
- [x] Database — at least one real read AND one real write: `observeVehicles()` reads, adding a vehicle writes, and the row survives a restart
- [x] UI — at least one interactive element wired to the API: the Add form's submit writes through the ViewModel and the list re-renders from the database flow
- [x] Deployment — documented local full-stack run command for both platforms in `README.md`, with the WAL sidecar and database file location noted

## Out of Scope (Deferred to Later Slices)

Declared here so no later phase re-litigates Phase 1's minimalism:

- Service entries, parts, workshop details, service photos, service history view (Phase 2)
- Reminders (mileage- and time-based), notifications, notification permission, dashboard (Phase 3)
- The `ComingSoonSlot` on the Detail screen is deliberately an inert placeholder — Phase 2 fills it
- Dark mode, iOS Dynamic Type wiring, km/miles unit toggle, per-vehicle units
- A chosen brand accent colour (tracked in D-21) and custom app icon artwork (D-22)
- Camera capture and camera permission (D-04 excludes them in v1)
- Any network client, account, sync, analytics or crash-reporting SDK — DAT-01 makes offline-only a property of the stack, not a setting
- Undo, trash or soft delete; export/import; list drag-reorder
- Localisation beyond English (D-11)

## Subsequent Slice Plan

Each later phase adds one vertical slice on top of this skeleton without altering its architectural decisions:

- **Phase 2: Service Log** — a user can log a service entry (date, mileage, service type, cost, parts, workshop, photos) for a vehicle and view, edit and delete the vehicle's full service history. Fills the Detail screen's reserved service-history slot.
- **Phase 3: Reminders & Dashboard** — a user can set mileage- and time-based reminders, receive a local notification when one is due (Android `WorkManager`; iOS foreground check), and see all vehicles with last-service and next-due at a glance on a home dashboard.

Both build on this skeleton unchanged: the same version catalog, the same driver factory and WAL pragma, the same serialized IO lane, the same repository/mapper boundary, the same shared `NavHost`, the same strings mechanism, and the same light-only theme with the accent token ready for the user's colour decision.
