# ROADMAP: VehiCare

**Project:** VehiCare — Kotlin Multiplatform car service record app (iOS + Android)
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.
**Granularity:** Coarse (3 phases)
**Coverage:** 21/21 v1 requirements mapped ✓

---

## Phases

- [ ] **Phase 1: Foundation & Vehicle Management** — KMP project scaffold, SQLDelight schema with WAL + migration CI, full vehicle CRUD
- [ ] **Phase 2: Service Log** — Complete service entry logging, history view, parts, photos, edit/delete
- [ ] **Phase 3: Reminders & Dashboard** — Mileage and time-based reminders, push notifications, iOS foreground-check pattern, home dashboard

---

## Phase Details

### Phase 1: Foundation & Vehicle Management

**Goal:** The KMP project runs on both platforms with a stable, migration-safe local database, and users can manage their vehicle list.
**Mode:** mvp
**Depends on:** Nothing (first phase)
**Requirements:** DAT-01, DAT-02, DAT-03, DAT-04, VEH-01, VEH-02, VEH-03, VEH-04, VEH-05
**Success Criteria**:

1. App launches on both Android (API 26+) and iOS (16+) from a single shared KMP codebase with no platform-specific UI duplication
2. User can add a vehicle with name, make, model, year, license plate, and type — vehicle persists after app restart with no data loss
3. User can attach a photo to a vehicle, and edit or delete a vehicle (with all its records)
4. User can view a list of all their vehicles on the home screen
5. SQLite WAL mode is active from first launch, and all schema changes are blocked in CI without a corresponding `.sqm` migration file

**Plans:** 4/10 plans executed
Plans:
**Wave 1**

- [x] 01-01-PLAN.md — Wave 0 version/environment gate, then the Gradle root config and KMP module config (migration tasks exposed)

**Wave 2** *(blocked on Wave 1 completion)*

- [x] 01-02-PLAN.md — Compose Resources strings, 3 vehicle-type vector drawables, theme tokens, platform hosts, minimal `App()`
- [x] 01-03-PLAN.md — WAL `.sq` schema, committed `1.db` baseline, domain models, driver factories, DI, repository + mapper with tests

**Wave 3** *(blocked on Wave 2 completion)*

- [x] 01-04-PLAN.md — nav shell (routes, bottom tabs, Settings placeholder), the Walking Skeleton end-to-end slice, `SKELETON.md`, `README.md`
- [ ] 01-05-PLAN.md — CI migration enforcement, per-platform WAL proof tests, and a probe-revert exercise proving the guard fires

**Wave 4** *(blocked on Wave 3 completion)*

- [ ] 01-06-PLAN.md — the vehicle list slice: `VehicleCard`, `VehicleTypeCircle`, `LicensePlateChip`, `EmptyState`, FAB

**Wave 5** *(blocked on Wave 4 completion)*

- [ ] 01-07-PLAN.md — the single Add/Edit form: all fields, validation rules, type picker, digits-only year

**Wave 6** *(blocked on Wave 5 completion)*

- [ ] 01-08-PLAN.md — the photo slice: `PhotoStorage`, `PhotoField`, FileKit gallery picker, Coil preview, orphan cleanup

**Wave 7** *(blocked on Wave 6 completion)*

- [ ] 01-09-PLAN.md — the Detail screen: hero photo, Display name, field rows, reserved service-history slot, Edit entry point

**Wave 8** *(blocked on Wave 7 completion)*

- [ ] 01-10-PLAN.md — confirmed deletion: dialog, single-flight delete, photo-file removal asserted on a real filesystem

### Phase 2: Service Log

**Goal:** Users can log, view, and manage the complete history of service events for any vehicle.
**Mode:** mvp
**Depends on:** Phase 1
**Requirements:** SVC-01, SVC-02, SVC-03, SVC-04, SVC-05, SVC-06, SVC-07
**Success Criteria**:

1. User can create a service entry with date, mileage, service type (preset or custom), cost, and notes in under 30 seconds
2. User can add parts replaced/repaired (part name, quantity) and workshop/mechanic details to a service entry
3. User can attach one or more photos (e.g., receipts) to a service entry
4. User can view the full service history for a vehicle sorted by date or mileage
5. User can edit or delete any existing service entry

**Plans:** TBD

### Phase 3: Reminders & Dashboard

**Goal:** Users receive proactive mileage- and time-based reminders when service is due, and see an at-a-glance summary of all vehicles on the home screen.
**Mode:** mvp
**Depends on:** Phase 2
**Requirements:** REM-01, REM-02, REM-03, REM-04, DSH-01
**Success Criteria**:

1. User can set a mileage-based reminder ("every X km from last service") and a time-based reminder ("every X months") for any service type on any vehicle
2. User receives a local push notification when a service reminder becomes due — notification permission is only requested when the user creates their first reminder
3. On Android, overdue mileage reminders are evaluated by a background WorkManager task; on iOS, overdue mileage reminders are evaluated on app foreground via `triggerImmediateIfOverdue()` — both platforms deliver the notification correctly
4. Home screen dashboard shows all vehicles with last service date and next due service at a glance

**Plans:** TBD
**UI hint**: yes

---

## Progress

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Foundation & Vehicle Management | 4/10 | In Progress|  |
| 2. Service Log | 0/? | Not started | — |
| 3. Reminders & Dashboard | 0/? | Not started | — |

---

## Requirement Coverage

| Phase | Requirements |
|-------|-------------|
| 1 – Foundation & Vehicle Management | DAT-01, DAT-02, DAT-03, DAT-04, VEH-01, VEH-02, VEH-03, VEH-04, VEH-05 |
| 2 – Service Log | SVC-01, SVC-02, SVC-03, SVC-04, SVC-05, SVC-06, SVC-07 |
| 3 – Reminders & Dashboard | REM-01, REM-02, REM-03, REM-04, DSH-01 |

**Total mapped:** 21/21 ✓

---

## Architecture Notes (from Research)

Critical decisions captured for execution phases:

- **Stack:** Kotlin 2.4.20 + Compose Multiplatform 1.12.1 + SQLDelight 2.4.1 + Koin 4.2.2 + JetBrains Navigation Compose 2.9.2 + AGP 9.1.1 + Gradle 9.5.1 (Android SDK 37, minSdk 26) — **the set in force**: the version generation already shipping in the VehiCare project, which the user directed be adopted after the Wave 0 gate was answered. Supersedes both the originally locked Kotlin 2.2.20 / CMP 1.8.2 / SQLDelight 2.3.2 / Koin 4.2.0 pin set *and* the option-b contract pins (Kotlin 2.4.21 / CMP 1.12.2 / AGP 9.3.1 / material3 1.9.0) which the user overrode. Authoritative source of truth is `gradle/libs.versions.toml`; see `phases/01-foundation-vehicle-management/01-01-SUMMARY.md` § "Version set actually in use" for the full table and the build evidence behind each pin.
- **Bootstrap:** Use [kmp.jetbrains.com](https://kmp.jetbrains.com) for correct Gradle version catalog and source set wiring
- **WAL mode:** Enable `PRAGMA journal_mode=WAL;` on iOS `NativeSqliteDriver` from Phase 1 — non-negotiable for data safety
- **Migration CI:** Run `generateCommonMainVehiCareDatabaseSchema` before first release commit; add `verifyCommonMainVehiCareDatabaseMigration` to CI
- **iOS reminders:** `NotificationScheduler` interface with `scheduleTimeBased()` + `triggerImmediateIfOverdue()` — designed in Phase 3; mileage logic stays in `commonMain`, delivery is `expect/actual`
- **DB concurrency:** Serialize all DB writes via `Dispatchers.IO.limitedParallelism(1)` to prevent iOS `SIGABRT` crashes
- **Notification permission:** Request only on first reminder creation, never on cold launch

---
*Roadmap created: 2026-10-18*
*Last updated: 2026-10-18 after initial roadmap creation*
