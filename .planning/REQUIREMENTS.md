# Requirements: VehiCare

**Defined:** 2026-10-18
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

## v1 Requirements

### Vehicle Management

- [x] **VEH-01**: User can add a vehicle with name, make, model, year, license plate, and vehicle type (car, motorcycle, truck, etc.)
- [x] **VEH-02**: User can attach a photo to a vehicle
- [x] **VEH-03**: User can edit vehicle details
- [ ] **VEH-04**: User can delete a vehicle and all its associated records
- [x] **VEH-05**: User can view a list of all their vehicles

### Service Log

- [ ] **SVC-01**: User can create a service entry with date, mileage, service type, cost, and notes
- [ ] **SVC-02**: User can add parts replaced or repaired to a service entry (part name, quantity)
- [ ] **SVC-03**: User can attach one or more photos (receipts, work done) to a service entry
- [ ] **SVC-04**: User can record the workshop name and mechanic for a service entry
- [ ] **SVC-05**: User can view the full service history for a vehicle, sorted by date or mileage
- [ ] **SVC-06**: User can edit an existing service entry
- [ ] **SVC-07**: User can delete a service entry

### Reminders

- [ ] **REM-01**: User can set a mileage-based reminder for a service type (trigger after X km from last service)
- [ ] **REM-02**: User can set a time-based reminder for a service type (trigger after X months from last service)
- [ ] **REM-03**: User receives a local push notification when a reminder becomes due
- [ ] **REM-04**: On iOS, mileage-based reminders are evaluated on app foreground (iOS background limitation)

### Dashboard

- [ ] **DSH-01**: User sees an overview of all vehicles with the last service date and next due service on the home screen

### Data & Platform

- [x] **DAT-01**: All data is stored locally on device (offline-first, no account required)
- [x] **DAT-02**: App runs on Android (API 26+) and iOS (16+) from a shared Kotlin Multiplatform codebase
- [x] **DAT-03**: SQLite database uses WAL mode from first launch to prevent corruption on iOS SIGKILL
- [x] **DAT-04**: All schema changes include a SQLDelight migration file (.sqm) verified in CI

## v2 Requirements

### Export & Backup

- **EXP-01**: User can export service history as PDF
- **EXP-02**: User can back up and restore data (local file export/import)

### Fuel Log

- **FUEL-01**: User can log fuel fill-ups (date, mileage, volume, cost)
- **FUEL-02**: User can view fuel efficiency trends per vehicle

### Cost Analytics

- **COST-01**: User can view total maintenance spend per vehicle
- **COST-02**: User can view cost breakdown by service type

### Documents

- **DOC-01**: User can store documents per vehicle (insurance, registration photos)

### Cloud Sync

- **SYNC-01**: User can optionally sync data across devices via cloud (requires account)

## Out of Scope

| Feature | Reason |
|---------|--------|
| Social / community feeds | Anti-feature — users explicitly leave apps that add social layers |
| OBD-II / diagnostic integration | High complexity, niche audience, not core to service records |
| Shop finder / map | Third-party data dependency, out of scope for personal tracker |
| Aggressive notification batching | Top complaint in competitor reviews — quality over quantity |
| Web app | Mobile-first for v1; KMP targets iOS + Android only |
| Firebase / remote push | No server, no account in v1; local notifications only |

## Traceability

Which phases cover which requirements. Updated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| VEH-01 | Phase 1: Foundation & Vehicle Management | Complete |
| VEH-02 | Phase 1: Foundation & Vehicle Management | Complete |
| VEH-03 | Phase 1: Foundation & Vehicle Management | Complete |
| VEH-04 | Phase 1: Foundation & Vehicle Management | Pending |
| VEH-05 | Phase 1: Foundation & Vehicle Management | Complete |
| SVC-01 | Phase 2: Service Log | Pending |
| SVC-02 | Phase 2: Service Log | Pending |
| SVC-03 | Phase 2: Service Log | Pending |
| SVC-04 | Phase 2: Service Log | Pending |
| SVC-05 | Phase 2: Service Log | Pending |
| SVC-06 | Phase 2: Service Log | Pending |
| SVC-07 | Phase 2: Service Log | Pending |
| REM-01 | Phase 3: Reminders & Dashboard | Pending |
| REM-02 | Phase 3: Reminders & Dashboard | Pending |
| REM-03 | Phase 3: Reminders & Dashboard | Pending |
| REM-04 | Phase 3: Reminders & Dashboard | Pending |
| DSH-01 | Phase 3: Reminders & Dashboard | Pending |
| DAT-01 | Phase 1: Foundation & Vehicle Management | Complete |
| DAT-02 | Phase 1: Foundation & Vehicle Management | Complete |
| DAT-03 | Phase 1: Foundation & Vehicle Management | Complete |
| DAT-04 | Phase 1: Foundation & Vehicle Management | Complete |

**Coverage:**

- v1 requirements: 21 total
- Mapped to phases: 21 ✓
- Unmapped: 0 ✓

---
*Requirements defined: 2026-10-18*
*Last updated: 2026-10-18 after initial definition*
