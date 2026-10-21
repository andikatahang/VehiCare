# VehiCare

## What This Is

VehiCare is a Kotlin Multiplatform app (iOS + Android) for tracking service history across multiple vehicles. Users log every service event per car — date, mileage, service type, parts replaced/repaired, cost, and notes — and get proactive reminders when the next service is due, based on mileage intervals or time elapsed.

## Core Value

Every car's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

- ✓ User can add and manage multiple cars — Phase 1 (VEH-01/03/04/05; UAT H-1…H-5 passed)
- ✓ Data is stored locally on device (local-first) — Phase 1 (DAT-01; zero network surface)
- ✓ App runs on iOS and Android via Kotlin Multiplatform — Phase 1 (DAT-02; both platforms build and test in CI)
- ✓ User can log service entries per car (date, mileage, type, parts, cost, notes) — Phase 2 (SVC-01…SVC-04, SVC-06; UAT 8/8 passed)
- ✓ User can view full service history per car — Phase 2 (SVC-05, SVC-07; date/mileage sort, edit and delete)

### Active

- [ ] User can set mileage-based and time-based reminders per service type
- [ ] User receives push notifications when a service is due

### Out of Scope

- Fuel log — deferred to v2; not core to service tracking
- Cost analytics / spending reports — future milestone
- Document storage (insurance, registration) — future milestone
- Cloud sync / multi-device sync — local-first for v1; optional in future
- Web app — mobile-only (iOS + Android) for v1

## Context

- Tech stack: Kotlin Multiplatform (KMP) targeting iOS and Android
- UI framework: Compose Multiplatform for shared UI across both platforms
- Local persistence: SQLDelight (KMP-compatible SQL)
- Notifications: platform-specific notification APIs via KMP expect/actual
- Design direction: minimal, light, clean — white space, subtle colors, card-based layout
- The app is personal / small-team use; no multi-user or account system in v1

## Constraints

- **Tech stack**: Kotlin Multiplatform — no React Native, Flutter, or separate native codebases
- **Platforms**: iOS + Android only for v1
- **Data**: Local-first — app must be fully functional offline
- **Design**: Minimal and light — no heavy UI frameworks or dark-only design

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Kotlin Multiplatform over Flutter/RN | User's explicit preference; shared business logic with native feel | ✓ Phase 1 — one shared codebase; Android/iOS hosts are thin bridges |
| Compose Multiplatform for shared UI | Reduces duplication; consistent design across platforms | ✓ Phase 1 — a single rendering path; no platform UI duplication |
| SQLDelight for local DB | KMP-compatible, type-safe SQL, offline-first | ✓ Phase 1 — schema baseline + CI migration guard; WAL proven on real drivers |
| Local-first data (no cloud v1) | Simpler scope, no auth needed, faster to ship | ✓ Phase 1 — zero network surface |
| Minimal light design system | User preference; clean aesthetic suits a utility app | ✓ Phase 1 — ink-on-paper light theme shipped; extended in later phases |
| WAL via platform config (Android construction-time pragma; iOS SQLiter `JournalMode.WAL` default) | The `.sq`-preamble mechanism crashes iOS driver creation (disproven in execution); capability re-proven on a real iOS driver | ✓ Phase 1 — `WalModeTest` green; accepted override G-2 recorded in `01-VERIFICATION.md` |
| Single version catalog as the sole version source (Wave-0 option-b + the project's own pin set) | Upstream CMP decouples material3; every pin backed by an executed build | ✓ Phase 1 — all CMP artifacts resolve through the single 1.12.1 pin (accepted override G-1) |
| Service types stored as an ordered child table (`service_entry_type`), with `service_entry.service_type` kept as a position-0 mirror | Multi-select needs order and cardinality SQLite cannot express in one column; minSdk 26 has no `DROP COLUMN`, and a table rebuild breaks the byte-identical baseline guard | ✓ Phase 2 — schema v3 + `2.sqm` backfill, verified row-for-row by migration test |
| One pure transition owns custom-type confirmation, writing selection and chip list together | The UAT bug survived static must-haves because a selection outside presets+learned had no chip to render; one transition makes the lists unable to drift | ✓ Phase 2 — GAP 1 re-test passed 2026-10-21 |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-10-21 after Phase 2*
