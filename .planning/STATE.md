---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: unknown
last_updated: "2026-10-18T11:51:57.837Z"
progress:
  total_phases: 3
  completed_phases: 0
  total_plans: 0
  completed_plans: 0
  percent: 0
---

# STATE: CarRecord

**Project:** CarRecord
**Core Value:** Every vehicle's full service history and upcoming reminders, always accessible offline, in a clean and minimal interface.

---

## Current Position

| Field | Value |
|-------|-------|
| **Active Phase** | Phase 1: Foundation & Vehicle Management |
| **Active Plan** | None (not yet planned) |
| **Status** | Ready to plan |
| **Progress** | ░░░░░░░░░░ 0% |

```
Phase 1 [Not Started] → Phase 2 [Not Started] → Phase 3 [Not Started]
   ↑
   Current
```

---

## Phase Summary

| Phase | Name | Status |
|-------|------|--------|
| 1 | Foundation & Vehicle Management | Not started |
| 2 | Service Log | Not started |
| 3 | Reminders & Dashboard | Not started |

---

## Performance Metrics

| Metric | Value |
|--------|-------|
| Phases completed | 0 / 3 |
| Plans completed | 0 / ? |
| Requirements covered | 0 / 21 |
| Session count | 1 |

---

## Accumulated Context

### Key Decisions

| Decision | Rationale | Phase |
|----------|-----------|-------|
| SQLDelight 2.3.2 over Room KMP | Community consensus for KMP; 5+ years iOS parity; Room KMP has known iOS gaps | Phase 1 |
| Koin 4.2.0 for DI | KMP-native, no kapt, `koinViewModel()` works in commonMain | Phase 1 |
| JetBrains Navigation Compose 2.9.0 | Production-stable, type-safe routes, native iOS back gesture | Phase 1 |
| WAL mode from Day 1 | Prevents SQLite corruption from iOS SIGKILL (0x8badf00d) | Phase 1 |
| Migration CI from Phase 1 | Any schema change without .sqm crashes existing users on update | Phase 1 |
| iOS mileage reminders: foreground-check | iOS cannot evaluate mileage in background; check on app foreground via `triggerImmediateIfOverdue()` | Phase 3 |
| Notification permission gated to first reminder | iOS one-time prompt denied on cold launch = reminders permanently broken | Phase 3 |
| `Dispatchers.IO.limitedParallelism(1)` for DB writes | Prevents iOS `SIGABRT` from concurrent NativeSqliteDriver access | Phase 1 |

### Architecture Constraints

- UI composables: never touch DB directly
- Domain models: never reference SQLDelight generated types (use mappers)
- Firebase and Android-only SDKs: `androidMain` only — never `commonMain`
- Mileage reminder logic: `commonMain`; platform delivery: `expect/actual`

### Open Questions (from Research)

1. Mileage units (km vs miles) — global or per-vehicle setting? Affects DB schema — decide before Phase 1 schema is finalized
2. iOS mileage reminder UX — silent foreground check or only on explicit mileage log?
3. Reminder advance notice — global or per-reminder `lead_miles`/`lead_days` column?
4. `kotlinx-datetime` 0.7.x — confirm no dependency expects 0.6.x compat artifact
5. SQLDelight `generateAsync = true` — verify compatibility before data layer build
6. Monetization model — one-time purchase or free?
7. Android notification delivery — WorkManager vs AlarmManager (SCHEDULE_EXACT_ALARM)?

### Todos

- [ ] Answer open questions before Phase 1 schema finalization
- [ ] Bootstrap project at kmp.jetbrains.com
- [ ] Set up CI before first release commit (generateMainCarRecordSchema + verifySqlDelightMigration)

### Blockers

None.

---

## Session Continuity

**Last session:** 2026-10-18T11:51:57.832Z
**Next action:** Run `/gsd-plan-phase 1` to create PLAN.md for Phase 1

---
*State initialized: 2026-10-18*
*Last updated: 2026-10-18 after roadmap creation*
