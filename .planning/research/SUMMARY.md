# Research Summary: CarRecord

**Synthesized:** 2026-10-18  
**Sources:** STACK.md · FEATURES.md · ARCHITECTURE.md · PITFALLS.md · PROJECT.md  
**Overall Confidence:** HIGH

---

## Recommended Stack

| Library / Tool | Version | Why |
|---|---|---|
| **Kotlin** | 2.2.20 | Language + multiplatform runtime; K2 compiler required by CMP 1.8+ |
| **Compose Multiplatform** | 1.8.2 | Shared UI across iOS + Android — declared stable for iOS in May 2025 |
| **SQLDelight** | 2.3.2 | KMP-native SQLite: type-safe SQL, compile-time verification, full iOS parity (use over Room KMP) |
| **Koin** | 4.2.0 (BOM) | KMP-native DI; no kapt/KSP; `koinViewModel()` works in commonMain Composables |
| **Navigation Compose (JetBrains fork)** | 2.9.0 | Type-safe routes via `@Serializable`; native iOS back gesture; official JetBrains standard |
| **kotlinx.coroutines** | 1.10.2 | Async + reactive Flows; fully KMP-compatible |
| **kotlinx.datetime** | 0.7.1 | Cross-platform date/time — replaces `java.time.*` which cannot be used in commonMain |
| **kotlinx.serialization** | 2.2.20 | Required for type-safe navigation route serialization |

> **Bootstrap with:** [kmp.jetbrains.com](https://kmp.jetbrains.com) — generates correct Gradle version catalog, source set structure, and plugin wiring.

---

## Table Stakes Features

Must ship in v1 or users leave / the app feels incomplete:

- **Multi-vehicle management** — add, edit, delete cars (name, make, model, year, nickname, odometer, optional VIN)
- **Service entry logging** — date, mileage, service type (preset + custom), cost, notes; <30 seconds friction is the bar
- **Full service history per vehicle** — chronological list; the core "when did I last…" lookup
- **Mileage-based reminders** — "every X miles" trigger computed from odometer at time of service entry
- **Date-based reminders** — annual inspection, insurance renewals; time-elapsed trigger
- **Dual trigger (mileage AND date, whichever comes first)** — this is the real-world interval model; single-type apps feel wrong
- **Push notifications when service is due** — reminders that only live inside the app are ignored
- **Reminder acknowledge from notification** — one-tap "done" from the notification itself
- **Offline-first, always** — fully functional with no network; non-negotiable for a local-first app
- **No account required** — instant onboarding; zero auth friction; data stays on device
- **Preset service types** — oil change, tire rotation, brake inspection out-of-box; custom types for power users

---

## Architecture in One Page

### Module Structure

```
CarRecord/
├── shared/commonMain/        ← ~95% of all code
│   ├── ui/                   Compose screens + components
│   ├── viewmodel/            ViewModels (androidx.lifecycle KMP)
│   ├── domain/               Use cases, pure domain models
│   ├── data/                 Repositories, SQLDelight query wrappers, mappers
│   ├── db/                   SQLDelight .sq schema files
│   └── platform/             expect declarations (NotificationScheduler, DriverFactory)
├── shared/androidMain/       actual: AndroidSqliteDriver, WorkManager notifications
├── shared/iosMain/           actual: NativeSqliteDriver, UNUserNotificationCenter
├── androidApp/               Thin shell: MainActivity → setContent { App() }
└── iosApp/                   Thin Xcode shell: ContentView wraps ComposeUIViewController
```

### Layer Stack (top → bottom)

```
Presentation    @Composable screens → collectAsState() from StateFlow
ViewModel       StateFlow<UiState>; sealed Loading/Success/Error; viewModelScope
Domain          Pure Kotlin: Car, ServiceEntry, ReminderRule; CalculateNextServiceUseCase
Data            Repository (interface) → SQLDelight generated queries → SqlDriver
Platform        expect/actual: NotificationScheduler + DriverFactory
```

**Rules that must not be broken:**
- UI composables never touch the DB directly
- Domain models never reference SQLDelight generated types (use mappers)
- Firebase and other Android-only SDKs go in `androidMain` only — never `commonMain`
- Mileage-based reminder *logic* lives in `commonMain`; platform *delivery* is `expect/actual`

### iOS Mileage Reminder Strategy (biggest platform divergence)

iOS cannot re-evaluate mileage in background. Solution:
- **Android:** WorkManager periodic task checks mileage threshold → fires `NotificationCompat`
- **iOS:** On app foreground, check mileage; if overdue → trigger immediate `UNUserNotificationCenter` notification

Design `NotificationScheduler` with `scheduleTimeBased()` + `triggerImmediateIfOverdue()` from day 1.

---

## Critical Decisions Made by Research

1. **Use SQLDelight 2.3.2, not Room KMP.** Room KMP 2.7.0 only became stable in 2025 with known iOS feature gaps and performance inconsistencies. SQLDelight is the community consensus choice for KMP with 5+ years of iOS parity.

2. **Use Koin 4.2.0, not Hilt/Dagger.** Hilt is Android-only and would force duplicating ViewModel wiring for iOS. Koin is KMP-native, requires no kapt, and `koinViewModel()` works from `commonMain` Composables.

3. **Use JetBrains Navigation Compose 2.9.0, not Voyager or Decompose.** Voyager is still beta; Decompose is over-engineered for this app size. JetBrains' fork is the production-stable path forward with type-safe routes and native iOS back gesture.

4. **Mileage-based reminders are checked on user interaction, not scheduled in advance.** When a user logs a service entry, calculate next-due mileage and store it. Check the threshold on app foreground / odometer log. Do NOT attempt to schedule mileage-based notifications in advance — the OS cannot evaluate mileage in the background.

5. **Enable SQLite WAL mode from Day 1.** `PRAGMA journal_mode=WAL;` on the iOS driver prevents database corruption from background process kills (iOS `0x8badf00d` SIGKILL). This is a one-line setup with a critical safety guarantee for a local-first app with no backup.

6. **Set up SQLDelight migration verification in CI before the first release commit.** Run `generateMainCarRecordSchema` to produce `1.db` snapshot; add `verifySqlDelightMigration` to CI. Every schema change in subsequent phases requires a corresponding `.sqm` file — no exceptions.

7. **Request notification permission only when the user creates their first reminder, not on app launch.** iOS one-time permission prompt denied early = reminders never work. Always show an explanation screen before triggering `requestAuthorization()`.

8. **Keep Firebase and platform-specific SDKs out of `commonMain`.** Use a `NotificationService` interface in `commonMain` + Koin platform modules to inject platform implementations. Firebase goes in `androidMain` only.

---

## Watch Out For

Ranked by severity (data loss and crash bugs first):

1. **🔴 iOS background SIGKILL corrupts SQLite DB** — iOS kills apps in background mid-write; users lose all data. Prevention: enable WAL mode, complete writes in foreground, add `PRAGMA integrity_check` on cold launch.

2. **🔴 SQLDelight schema change without `.sqm` migration → crash on update** — Any `CREATE TABLE` change without a migration file crashes every existing user on app update. Prevention: `generateSchema` before first release, `verifySqlDelightMigration` in CI, migration file required per PR checklist.

3. **🟠 NativeSqliteDriver concurrent access crashes on iOS** — Multiple coroutines hitting the DB simultaneously cause `SIGABRT` crashes (silently in release builds). Prevention: serialize all DB writes via `Dispatchers.IO.limitedParallelism(1)`; wrap DAO calls in `runCatching`.

4. **🟠 iOS notification permission requested too early → permanently denied** — Permission denied on cold launch means reminders never work. Prevention: gate permission request behind first reminder creation; always show context explanation first.

5. **🟡 `BGAppRefreshTask` used for reminder delivery → fires hours late or never** — This is the wrong iOS API for time-sensitive reminders. Prevention: use `UNCalendarNotificationTrigger` for all time-based reminders; `BGTask` only for best-effort sync.

**Bonus (performance):** Always provide `key = { it.id }` in `LazyColumn` items — missing this causes full recomposition on iOS on every scroll update, noticeably jankier than Android due to Skiko CPU overhead.

---

## Open Questions

Unresolved questions that need a decision before or during planning:

1. **Mileage units (km vs miles) — how does the user set this?** App needs a global or per-vehicle mileage unit preference. Stored in DataStore (KMP). Affects reminder threshold display, history display, and column naming in DB schema. Decide before schema is finalized.

2. **iOS mileage reminder UX — what triggers the foreground check?** When the app foregrounds, should it always silently check overdue reminders and fire a local notification? Or only when the user explicitly logs mileage? The interaction model needs to be defined before the notification phase.

3. **Reminder advance notice — global or per-reminder setting?** Research shows "notify me 200 miles before due" is a meaningful differentiator. Is this a per-reminder setting in v1 or a global preference? Affects DB schema (reminder table needs a `lead_miles` / `lead_days` column if per-reminder).

4. **`kotlinx-datetime` 0.7.x breaking changes** — v0.7.0 renamed `dayOfMonth`→`day`, `monthNumber`→`month`, and moved `Instant` to `kotlin.time.Instant`. If any dependency expects 0.6.x, use the `0.7.1-0.6.x-compat` artifact. Verify at project setup before writing date-handling code.

5. **SQLDelight `generateAsync = true` — confirm compatibility** — Enabling async mode switches query APIs to suspend functions. Both `sqldelight` plugin and `coroutines-extensions` must be exactly 2.3.2. Verify in a spike before building the data layer.

6. **Monetization model — one-time purchase or free?** Research shows "pay once, own it forever" resonates strongly with car-tracker users. Hidden paywalls after data entry destroy trust. This decision affects the app store listing and whether any feature gating is needed in the codebase. Decide before release planning.

7. **Android notification delivery: WorkManager vs AlarmManager?** WorkManager is recommended for new apps (battery-aware, survives reboots via `BOOT_COMPLETED` receiver). But `SCHEDULE_EXACT_ALARM` permission (API 31+) may be needed for precise timing and users can revoke it. Decide which mechanism to implement and how to handle degraded permission gracefully.

---

*Research confidence: HIGH across all four domains. Primary uncertainties are product decisions (units, monetization), not technology choices.*
