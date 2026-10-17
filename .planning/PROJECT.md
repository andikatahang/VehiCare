# CarRecord

## What This Is

CarRecord is a Kotlin Multiplatform app (iOS + Android) for tracking service history across multiple vehicles. Users log every service event per car — date, mileage, service type, parts replaced/repaired, cost, and notes — and get proactive reminders when the next service is due, based on mileage intervals or time elapsed.

## Core Value

Every car's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

## Requirements

### Validated

<!-- Shipped and confirmed valuable. -->

(None yet — ship to validate)

### Active

- [ ] User can add and manage multiple cars
- [ ] User can log service entries per car (date, mileage, type, parts, cost, notes)
- [ ] User can view full service history per car
- [ ] User can set mileage-based and time-based reminders per service type
- [ ] User receives push notifications when a service is due
- [ ] Data is stored locally on device (local-first)
- [ ] App runs on iOS and Android via Kotlin Multiplatform

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
| Kotlin Multiplatform over Flutter/RN | User's explicit preference; shared business logic with native feel | — Pending |
| Compose Multiplatform for shared UI | Reduces duplication; consistent design across platforms | — Pending |
| SQLDelight for local DB | KMP-compatible, type-safe SQL, offline-first | — Pending |
| Local-first data (no cloud v1) | Simpler scope, no auth needed, faster to ship | — Pending |
| Minimal light design system | User preference; clean aesthetic suits a utility app | — Pending |

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
*Last updated: 2026-10-18 after initialization*
