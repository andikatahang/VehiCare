# Phase 3: Reminders & Dashboard — Research

**Researched:** 2026-10-22
**Domain:** Kotlin Multiplatform (Compose Multiplatform) · SQLDelight schema evolution · Android WorkManager · iOS UserNotifications · local-notification deep links
**Phase mode:** mvp (`ROADMAP.md` § Phase 3)
**Overall confidence:** HIGH for stack/architecture/migration mechanics · MEDIUM for the iOS UserNotifications Kotlin/Native surface names (no Apple docs reachable this session — see § Sources) · HIGH for the pinned-UI feasibility questions (verified against the exact artifacts in the Gradle cache)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

#### Reminder Placement & Management
- **D-41:** Reminder rules live on a **dedicated per-vehicle Reminders screen**, reached from a **new summary row on vehicle Detail** — the same slot→screen pattern as Service history (D-32). Rules are per-vehicle because intervals differ by vehicle (motorcycle vs car) and every rule is keyed by `vehicle_id` + the stored service-type string (D-40).
- **D-42:** Adding and editing follow the Phase 2 grammar: a **FAB "Add reminder"** opens a **single-screen editor** (D-30); tapping a row opens the same editor in edit mode. No expand-in-place editing, no multi-step flow.
- **D-43:** Every rule carries an **`enabled` flag with a row toggle**. Disabling silences the rule without losing its intervals — deleting remains the only destructive action and stays behind the established confirm dialog (D-35 shape).
- **D-44:** The rule's service type is chosen from the **same chip group as the service form** — the six presets then the learned custom types (D-24/D-25), never free text. A rule therefore always matches a stored type string, and D-40's "never silently rewrite the stored value" applies unchanged.
- **D-45:** The Detail summary row mirrors `ServiceSummaryRow` (D-32): it shows **reminder count + next due** and opens the Reminders screen. The row does not duplicate the list.

#### Rule Shape & Baseline
- **D-46:** `reminder_rule` holds `vehicle_id`, the service-type string, `interval_km` (**nullable**) and `interval_months` (**nullable**). **Both may be set on one rule**; the rule becomes due when *either* is reached ("5,000 km OR 6 months"). At least one interval must be present.
- **D-47:** Intervals are measured from **the last service entry of the same type** — the mileage baseline is that entry's mileage, the time baseline is its service date. Not from the latest entry of any type, and not frozen at rule-creation time.
- **D-48:** **Baselines** roll forward automatically: logging a service of that type moves the baseline and clears the due state with no rule edit, no confirmation prompt and no "mark as done" step. The 30-second entry path (D-31) must not gain a reminder question.
- **D-49:** For a rule whose type has **no entry yet**, the km baseline is the vehicle's latest entry mileage (any type) and the time baseline is the **rule's creation date**. When the vehicle has no entries at all, the km component renders as **"no mileage data yet"** rather than a due figure — never an invented odometer value (research warns against odometer extrapolation).
- **D-50:** **Advance notice is per-rule and optional**: `lead_km` and `lead_days` (both nullable, empty = notify exactly at due). *This deliberately exceeds the letter of REM-01/REM-02 — user-approved during this discussion, recorded here so downstream agents do not treat it as scope creep.* A rule with a lead window enters the "due soon" state before it is actually due.

#### Notification Policy & Delivery
- **D-51:** A rule **notifies once per due cycle**. After a notification is delivered, the rule stays silent until its baseline moves (a service of that type is logged) or the rule is edited. Requires an explicit once-per-cycle **claim** marker on the rule row (shape resolved below: a cycle key compared atomically); `armed_cycle_key` is D-53 bookkeeping, not a claim. Research is unambiguous that repeated nagging is the anti-feature that gets apps uninstalled.
- **D-52:** **Android delivers both rule kinds through WorkManager** — mileage evaluation (already fixed by SC-3) and time-based reminders. No `SCHEDULE_EXACT_ALARM`, no `BOOT_COMPLETED` receiver (WorkManager survives reboot by itself). Accepted trade-off: timing may drift by hours under Doze, and the UI copy must not promise exact delivery.
- **D-53:** **iOS pre-schedules time-based reminders** with `UNCalendarNotificationTrigger` (fires even when the app is killed) and **evaluates mileage rules on app foreground** via the ROADMAP's `triggerImmediateIfOverdue()` (REM-04, SC-3). Time-based delivery is exact on iOS; mileage is inherently manual because the odometer only moves when the user logs it (research Notif 5).
- **D-54:** **Notification permission is requested only when the user saves their first reminder**, preceded by a short in-app explanation, with denial non-blocking (the rule still saves). SC-2 and the STATE.md Key Decision stand: asking at first launch would burn iOS's one-time prompt before the user knows why. An initial suggestion to prompt at first launch was explicitly withdrawn after the conflict was surfaced.
- **D-55:** Tapping a notification opens **that vehicle's Detail screen** (payload = `vehicleId`) on both platforms — Detail is the hub carrying both summary rows and the path to every action. Research Notif 4 is explicit that landing on a generic home screen reads as a bug.
- **D-56:** Notification content names **the vehicle and the service type** and states the reason (due in X km / due on DATE / overdue by X km), so two vehicles' reminders are never confusable.

#### Home Dashboard
- **D-57:** **The home list is ordered by urgency**, not recency: overdue first, then due soon, then upcoming by proximity, with vehicles that have no active reminder last (newest-first inside that group). **This SUPERSEDES D-16 (newest-first)** — recorded here in the same way D-26 was superseded in Phase 2, so the Phase 1 decision is not silently violated. A dashboard whose purpose is "which vehicle needs attention" cannot be ordered by when it was added.
- **D-58:** The **existing `VehicleCard` gains one status line**; no second card component is introduced for the same entity (D-14's identity-card language is preserved).
- **D-59:** The status line shows the vehicle's **single most urgent rule**, plus a **"+N due" counter** when more than one rule is due or overdue. Cards stay uniform in height and the list stays scannable.
- **D-60:** Three due states — **upcoming / due soon / overdue**. Only overdue uses the enforced `Danger` token, and **every state carries text** so colour is never the sole carrier of meaning (01-UI-SPEC accessibility contract).
- **D-61:** When notification permission is denied and at least one active reminder exists, **home shows a thin banner** above the list and the **Reminders screen shows the same status inline**. Tapping leads to the Reminders screen (or to system Settings when permission is permanently denied). Permission status is **re-checked on every foreground** — it is not reactive (research Notif 3).

#### Cross-Cutting (requirements, not re-discussed)
- **D-62:** All new schema ships in a **`3.sqm` migration** with a regenerated **`4.db`** baseline; both CI guards (`generateCommonMainVehiCareDatabaseSchema` + `git diff --exit-code`, and `verifyCommonMainVehiCareDatabaseMigration`) must stay green (DAT-04, D-38 precedent). New table: `reminder_rule`, keyed by `vehicle_id`.
- **D-63:** All reminder **calculation** (which rule is due, by how much, in what order) lives in `commonMain`; **delivery** is a platform seam (`NotificationScheduler` interface plus an `expect fun` factory, per `.planning/research/ARCHITECTURE.md`). Android-only SDKs stay in `androidMain`, never `commonMain`.
- **D-64:** Deleting a vehicle deletes **its reminder rules** as well — an extension of the existing cascade (D-39), not a second delete path.
- **D-65:** Every new user-facing string goes through `Res.string.*` (D-11); the existing theme tokens, spacing scale and type sizes are reused unchanged (01-UI-SPEC), with Phase 3's own UI-SPEC layered on top (ROADMAP `UI hint: yes`).

### the agent's Discretion

Bounded by the requirements and the decisions above — decide these during research/planning:

- Exact `reminder_rule` column set beyond the decided ones (the once-per-cycle claim marker and the separate armed bookkeeping marker), indexes, and the `created_at` representation.
- The dashboard query shape — one aggregated read per list load, no N+1 (PITFALLS § SQL 3); how "most urgent" is computed and compared across the km and time axes.
- Android WorkManager cadence (periodic daily vs. a chain of delayed one-off work), constraint set, and where the worker and channel creation live (`AndroidManifest.xml` and `Application` belong to `androidApp`, so the split between `:shared` `androidMain` and `:androidApp` is a planning decision).
- Notification channel id/name/importance (research Android 1: create once in `Application.onCreate`, before any notification) and the iOS `UNUserNotificationCenter.delegate` assignment point (research Pitfall 5: before any KMP initialization).
- Editor field layout, interval validation bounds and their messages, and whether km/month inputs are two fields or one row.
- Wording of notification title/body, the Reminders empty state, the banner copy, and the `"+N due"` phrasing.
- Whether a rule row shows its own due status inline, and the tie-break rule when two vehicles are equally urgent.

### Deferred Ideas (OUT OF SCOPE)

- **Snooze / acknowledge directly from the notification** ("mark as done" without opening the app) — research rates it P1; it needs a notification-action surface on both platforms. Own future phase.
- **Predicted due date from mileage rate** (average km/day projected forward) — research P2, and it needs ≥2 entries per type to be meaningful.
- **Reminder rules for document expiry** (insurance, registration, tax) — DOC-01 territory, v2.
- **Per-rule notification sound / custom channel per service type** — not needed for v1; one channel keeps the settings surface small.
- **Reminder history log** (what was sent, when, and whether it was opened) — no user-facing need in v1; revisit with any future analytics work.
- **Quiet hours / do-not-disturb window** — plausible later refinement of D-51, deliberately not in this phase.
- **Home-screen widget showing next due** — no requirement behind it; would be a new platform surface.

None of these block Phase 3 scope. The `reminder_rule` schema (D-46) leaves room for the snooze and history items without a further migration if they are ever scheduled.
</user_constraints>

<phase_requirements>
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| REM-01 | User can set a mileage-based reminder for a service type (trigger after X km from last service) | § U6 (baseline query), § Architecture Patterns 4 (`ReminderStatus` km axis), § Schema (`interval_km`, `lead_km`), C-34/C-35 editor gate, C-42 interval vocabulary |
| REM-02 | User can set a time-based reminder for a service type (trigger after X months from last service) | § U6 (baseline query), § Architecture Patterns 4 (time axis + `kotlinx-datetime` calendar math), § Schema (`interval_months`, `lead_days`), C-43 time-axis forms |
| REM-03 | User receives a local push notification when a reminder becomes due | § U1 (WorkManager in `:shared` androidMain, 2.12.0), § U2 (shared DB holder), § U3 (iOS `UNUserNotificationCenter` from `iosMain`), § U4 (permission state), C-40 copy, § Don't Hand-Roll (channel, retry, dedupe) |
| REM-04 | On iOS, mileage-based reminders are evaluated on app foreground (iOS background limitation) | § U5 (`LifecycleEventEffect(ON_RESUME)` in `commonMain` — verified scene-driven on CMP 1.12.1 iOS), § U3 (`triggerImmediateIfOverdue` actual), D-53 |
| DSH-01 | User sees an overview of all vehicles with the last service date and next due service on the home screen | § U6 (`combine` of vehicles + rules + type baselines + per-vehicle stats), C-28…C-31, C-29 ordering, D-57…D-60 card line; last-service-date placement is Open Question 1 |
</phase_requirements>

## Summary

Phase 3 adds three things to an app that currently has none of them: a `reminder_rule` table and its CRUD, an out-of-process evaluation path that must reach the same database the UI reads, and a permission state the UI must display without ever having a reactive source for it. The stack does not change except for one Android artifact (`androidx.work:work-runtime:2.12.0`); everything else the phase needs is already pinned and present in the version catalog. The single highest-risk item is not a library — it is **process-global state**: a `CoroutineWorker` in `:shared` `androidMain` runs with no Koin container, so the database must be reachable through a process-wide holder created in a new `Application` subclass, and there must be exactly **one** `VehiCareDatabase` instance for the worker's write to emit SQLDelight's query notification to the UI's `Flow`s.

The second-highest risk is iOS: the `UNUserNotificationCenter` Kotlin/Native surface (`requestAuthorizationWithOptions`, `getNotificationSettingsWithCompletionHandler`, `UNCalendarNotificationTrigger.triggerWithDateMatchingComponents`) is only reachable through ObjC interop, all of it behind `@OptIn(ExperimentalForeignApi::class)`, and there is **no** `androidx.work` equivalent. The phase therefore has two structurally different delivery paths behind one `expect`/`actual` seam — Android periodic polling plus iOS pre-scheduled calendar triggers plus a foreground mileage sweep — and the research confirms `LifecycleEventEffect(Lifecycle.Event.ON_RESUME)` in `commonMain` is a sufficient foreground hook on both platforms without a fifth `expect`/`actual` pair (CMP 1.12.1's iOS lifecycle is scene-driven and re-fires on every foreground return).

The third risk area is the SQLDelight migration: `databases/` already holds `1.db`, `2.db`, `3.db`, so Phase 3 must ship `migrations/3.sqm` **and** regenerate `4.db`, with the `.sqm` DDL structurally identical to the new `.sq` `CREATE TABLE` (the Phase 1/2 convention). Everything in the UI-SPEC's pinned-component list survives verification: `Switch` colour parameters, `imePadding()` on iOS, `getString`/`getPluralString` suspend access from a worker, and the material3 tokens all behave as the spec assumes. **No UI-SPEC contract is technically blocked.**

**Primary recommendation:** Build it as one vertical slice — schema + repository + pure `ReminderStatus` in `commonMain`, a process-wide DB holder + `Application` + `CoroutineWorker` in `:shared` `androidMain`, a Swift `@UIApplicationDelegateAdaptor` that installs the Kotlin notification delegate before Compose starts, and both dashboard surfaces read from a `combine` of four flat queries — with every C-28…C-43 sentence produced by the same pure helper so Android and iOS notification copy cannot drift.

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Rule CRUD + persistence | **Database / Storage** (SQLDelight, per-platform driver) | commonMain repository | One table, one migration (`3.sqm`); the UI never sees generated rows (Phase 1 repository boundary). |
| Rule validation (intervals ≥ 1, at least one axis) | **commonMain** pure object | — | C-34 requires a pure object; `VehicleFormValidation` is the precedent, unit-tested in `commonTest`. |
| Due-state computation (km axis, time axis, four sentences) | **commonMain** `ReminderStatus` | — | D-63 + C-28: one source for card, rule row, summary row **and** both platforms' notification copy. |
| Dashboard aggregation (vehicles + last service + rules) | **Database / Storage** (four flat queries) | commonMain `combine` + ordering | PITFALLS § SQL 3 (no N+1); C-29 ordering needs a cross-axis ratio, computed in Kotlin. |
| Scheduling + delivery | **Platform** (`NotificationScheduler` interface + `expect fun` factory) | — | D-63. Android = WorkManager; iOS = `UNCalendarNotificationTrigger` + foreground sweep. No cross-platform notifier exists. |
| Permission request + status read | **Platform** (`actual`) | commonMain gate (when to ask) | The API is platform; the *policy* (first reminder save only, D-54) lives in commonMain. |
| Foreground hook | **commonMain** (`LifecycleEventEffect`) | — | Verified: CMP 1.12.1 supplies one lifecycle on both platforms (U5). No `expect`/`actual` needed. |
| Notification tap → Destination | **Platform** (Intent extra / `UNNotificationResponse` id) | commonMain nav state | The OS owns the tap; the payload is `vehicleId` (D-55) and routing stays in the single `NavHost`. |
| Vehicle-delete cascade | **Database / Storage** (existing transaction) | commonMain repo (`VehicleRepositoryImpl.delete`) | D-64 extends the existing cascade, not a second delete path. |
| Manifest permission, channel creation, `Application` | **Android app shell** (`:androidApp`) | `:shared` `androidMain` (holder/worker) | `AndroidManifest.xml` belongs to the application module; `:shared` is an AGP KMP library with no manifest of its own. |

## Standard Stack

### Core — the phase's only new dependency

| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| `androidx.work:work-runtime` | **2.12.0** | Android deferrable background work: one periodic evaluator + the notification dispatch for both rule kinds | The AndroidX canonical API for work that must run outside the app's lifetime. Survives reboot by itself (no `BOOT_COMPLETED` receiver — D-52), participates in Doze, needs **no** special permission. `CoroutineWorker` (a `suspend doWork()`) lives here. `[VERIFIED: maven.google.com — androidx.work:work-runtime 2.12.0 released 2026-09-23]` `[CITED: developer.android.com/topic/libraries/architecture/workmanager]` |

**Do not add `androidx.work:work-runtime-ktx`.** It has been an *empty* artifact since 2.9.0 — a correctness trap, not a compile error, because adding it does nothing. `CoroutineWorker`, `WorkManager.getInstance(context)`, `enqueueUniquePeriodicWork` and the `ExistingPeriodicWorkPolicy` enum are all in `work-runtime` itself. `[CITED: developer.android.com/jetpack/androidx/releases/work — "work-runtime-ktx is empty since 2.9.0"]` `[VERIFIED: POM of work-runtime 2.12.0 declares the KTX APIs in the base artifact]`

### Already pinned — used unchanged by this phase (no new install)

| Library | Version (from `gradle/libs.versions.toml`) | Role in Phase 3 |
|---------|--------------------------------------------|-----------------|
| `org.jetbrains.compose.components:components-resources` | 1.12.1 | `Res.string.*` + `getString`/`getPluralString` for notification copy resolved **from the worker and from iOS** (suspend, not `Context`-bound) — U9 |
| `org.jetbrains.androidx.lifecycle:lifecycle-runtime-compose` | 2.11.0 | `LifecycleEventEffect` — the foreground hook, in `commonMain`, both platforms (U5) |
| `app.cash.sqldelight:*` | 2.4.1 | `reminder_rule` table, `3.sqm` migration, `4.db` baseline, `asFlow().mapToList()` |
| `org.jetbrains.kotlinx:kotlinx-datetime` | 0.8.0 | Local-date arithmetic for the time axis and for the **iOS** `NSDateComponents` split (U3) — `Clock.System.now()` is already the app's time source (`ServiceDates`) |
| `io.insert-koin:koin-compose*` | 4.2.2 | Composition-scoped DI; **not** reachable from a worker (U2) |
| `org.jetbrains.compose.material3:material3` | 1.12.0-alpha03 | `Switch`, `Card`, `FilterChip`, `AlertDialog`, `Snackbar`, all already in the artifact |

### Explicitly NOT added

| Rejected | Why |
|----------|-----|
| `androidx.work:work-runtime-ktx` | Empty artifact since 2.9.0 (above). |
| `androidx.core:core-ktx` in `:shared` `androidMain` | Not needed. On minSdk 26 the platform calls are directly available: `context.checkSelfPermission(POST_NOTIFICATIONS)`, `context.getSystemService(NotificationManager::class.java).areNotificationsEnabled()`. Avoids a new dependency purely for a compat shim. `[VERIFIED: shared/build.gradle.kts androidMain currently has no direct androidx.core dependency — it would arrive only transitively via activity-compose]` |
| `androidx.work:work-multiprocess` | The app is single-process. |
| Any iOS notification library | Apple's own `UserNotifications` framework is the only correct answer; a wrapper would add an unmaintained dependency to a 20-line `actual`. |
| Any cron/`AlarmManager` scheduler | D-52 forbids exact alarms; WorkManager already is the scheduler. |
| `kotlinx-datetime` upgrade | 0.8.0 already pinned and in use. |

**Installation:**

```toml
# gradle/libs.versions.toml
[versions]
workmanager = "2.12.0"

[libraries]
androidx-work-runtime = { module = "androidx.work:work-runtime", version.ref = "workmanager" }
```

```kotlin
// shared/build.gradle.kts — inside kotlin { sourceSets { androidMain.dependencies { } } }
androidMain.dependencies {
    implementation(libs.androidx.work.runtime)   // ADD — the only new line
    // ...existing entries unchanged
}
```

**Version verification (run 2026-10-22):**

```bash
# latest stable
curl -s https://dl.google.com/dl/android/maven2/androidx/work/work-runtime/maven-metadata.xml | grep -E "<latest>|<release>"
# => <release>2.12.0</release>

# confirm the KTX artifact carries no classes (it is an empty artifact since 2.9.0)
curl -s -o /tmp/work-runtime-ktx.jar \
  https://dl.google.com/dl/android/maven2/androidx/work/work-runtime-ktx/2.12.0/work-runtime-ktx-2.12.0.jar \
  && unzip -l /tmp/work-runtime-ktx.jar | tail -3
```

Verified facts about 2.12.0: `minSdk 24` (our 26 is above it), requires `compileSdk ≥ 33` (ours is 37), transitive deps include `androidx.startup:startup-runtime`, `androidx.room:room-runtime:2.7.0`, `androidx.lifecycle:lifecycle-livedata`/`lifecycle-service:2.6.2`, `androidx.tracing:tracing-ktx:1.2.0`. `[VERIFIED: POM of androidx.work:work-runtime:2.12.0]` — the `androidx.startup` transitive is *why* default initialization works without any code (U1).

## Package Legitimacy Audit

> Scope: this phase installs exactly one external package. The `gsd-tools query package-legitimacy check` seam could not be invoked in this session (`gsd-tools` is not on `PATH` and all search providers are disabled in `.planning/config.json`), so the verdicts below are derived from the registry POM metadata and the authoritative AndroidX documentation, both of which were fetched directly.

| Package | Registry | Age | Downloads | Source Repo | Verdict | Disposition |
|---------|----------|-----|-----------|-------------|---------|-------------|
| `androidx.work:work-runtime` 2.12.0 | Google Maven (mirrored to Maven Central) | First GA 2018-06 (WorkManager 1.0.0); 2.x line since 2019 — ~8 yrs | N/A on Google Maven; shipped by default in the Android platform's own Jetpack set | `androidx/androidx` (AOSP, `work/` module) | **OK** | Approved |

**Packages removed due to [SLOP] verdict:** none.
**Packages flagged as suspicious [SUS]:** none.

Signals checked: (1) the artifact is published under the `androidx.*` group, which Google controls; (2) its POM declares first-party AndroidX dependencies only; (3) the API used (`CoroutineWorker`, `PeriodicWorkRequest`, `WorkManager.getInstance`) is documented on `developer.android.com` and in Context7's WorkManager corpus; (4) no `postinstall`-equivalent exists in an AAR, and the manifest it merges contributes only WorkManager's own components — `SystemJobService` (service, not exported), `RescheduleReceiver` (not exported) and `DiagnosticsReceiver` (guarded by a signature-level permission) — plus `androidx.startup.InitializationProvider`. `[CITED: developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration — the manifest components and the startup provider]`

## Architecture Patterns

### System Architecture Diagram

```
                       ┌──────────────────────────── COMMON (commonMain) ───────────────────────────┐
                       │                                                                            │
  [App launch] ───────▶│  App.kt  ── NavHost ──┬─ VehicleListScreen  (dashboard)                     │
                       │                       ├─ VehicleDetailScreen ── ReminderSummaryRow          │
                       │                       ├─ RemindersScreen     (list + Switch rows)            │
                       │                       └─ ReminderEditScreen  (single-screen editor)         │
                       │                                  │                                         │
                       │                     ReminderRepository ◀──── ReminderStatus (pure)          │
                       │                                  │              ▲  km axis / time axis /     │
                       │                     combine(...) │              │  4 sentences / urgency    │
                       │                                  ▼              │                          │
                       │            ┌────────── SQLDelight FLOW queries ──────────┐                  │
                       └────────────┼──────────────────────────────────────────────┼─────────────────┘
                                    │                                              │
                    ┌───────────────▼───────────────┐              ┌───────────────▼──────────────────┐
                    │     ANDROID (androidMain)     │              │         iOS (iosMain)            │
                    │                               │              │                                  │
  [Periodic tick]──▶│ ReminderWorker (Coroutine)    │              │ scheduleTimeBased()              │
  (WorkManager,     │   ├─ read rules + baselines   │              │   └─ UNCalendarNotification      │
   ~24 h, Doze-     │   ├─ ReminderStatus.evaluate  │              │        Trigger (pre-scheduled,   │
   tolerant)        │   ├─ Res.getString(...)       │              │        fires when app is killed) │
                    │   ├─ mark notified (UPDATE)   │              │ triggerImmediateIfOverdue()      │
                    │   └─ NotificationManager.     │              │   └─ same evaluation,          ◀──┼── [ON_RESUME]
                    │        notify(vehicleId)      │              │        delivered immediately     │  LifecycleEventEffect
                    │                               │              │                                  │
                    │ PROCESS-WIDE DB HOLDER        │              │ UNUserNotificationCenter          │
                    │   VehiCareApp : Application   │              │   .delegate = Kotlin object      │
                    │   (single VehiCareDatabase)   │              │   (installed from Swift         │
                    └──────────────┬────────────────┘              │    AppDelegate before Compose)  │
                                   │                               └──────────────┬───────────────────┘
                                   ▼                                              ▼
                    ┌──────────────────────────────┐              ┌──────────────────────────────────┐
                    │   ONE SQLite file (WAL)      │              │   OS notification centre         │
                    │   vehicle · service_entry ·  │              │   tap → response.notification     │
                    │   service_entry_type ·       │              │        .request.identifier        │
                    │   reminder_rule  (v4)        │              │        = "v<vehicleId>:r<ruleId>" │
                    └──────────────────────────────┘              └──────────────────────────────────┘
                                                                             │
  [tap on Android] Intent extra / onNewIntent ──────────────────────────────┴──▶ navigate(Detail(vehicleId))
```

Trace of the primary use case: user saves a rule → `ReminderEditScreen` writes `reminder_rule` (creating the notification channel first on Android, requesting permission on iOS if `notDetermined`) → Android schedules/refreshes the unique periodic worker and iOS immediately calls `scheduleTimeBased()` to place the next calendar trigger → when the worker or the foreground sweep finds the rule due, it marks the row and posts a notification whose identifier carries `vehicleId` → tapping it lands on `VehicleDetailScreen` for that vehicle, where both summary rows already live.

### Recommended Project Structure

```
shared/src/
├── commonMain/
│   ├── kotlin/com/vehicare/
│   │   ├── App.kt                                  # + RemindersRoute/ReminderEditRoute composables, deep-link collector
│   │   ├── domain/
│   │   │   ├── ReminderRule.kt                     # domain model (no generated types)
│   │   │   ├── ReminderStatus.kt                   # NEW pure object: due state, sentences (C-28), urgency (C-29)
│   │   │   └── ReminderInterval.kt                 # C-42 interval sentence (pure)
│   │   ├── data/
│   │   │   ├── ReminderRepository.kt               # interface
│   │   │   ├── ReminderRepositoryImpl.kt           # impl + mapper + serialized IO lane
│   │   │   └── VehicleRepositoryImpl.kt            # MODIFIED: delete() also deletes reminder_rule
│   │   ├── data/db/DatabaseProvider.kt             # NEW expect: process-wide DB accessor (U2)
│   │   ├── notifications/NotificationScheduler.kt  # NEW seam (4th): interface + expect factory
│   │   ├── notifications/NotificationPermission.kt # NEW expect seam: status + request
│   │   ├── ui/reminders/…                          # RemindersScreen, ReminderEditScreen, ReminderRuleRow,
│   │   │                                           # ReminderSummaryRow, ReminderFormValidation, ViewModels
│   │   │                                           # PermissionExplanationDialog, PermissionBanner (C-38/C-39)
│   │   └── ui/vehicles/…                           # MODIFIED: VehicleCard status line, VehicleList ordering
│   ├── sqldelight/com/vehicare/db/
│   │   ├── ReminderRule.sq                         # NEW: DDL mirror + queries (U6)
│   │   └── migrations/3.sqm                        # NEW: v3 → v4
│   └── composeResources/values/strings.xml         # MODIFIED: every new string (D-11/D-65)
├── androidMain/kotlin/com/vehicare/
│   ├── data/db/DatabaseProvider.android.kt         # holder wiring + createDriverFactory
│   ├── notifications/NotificationScheduler.android.kt
│   ├── notifications/NotificationPermission.android.kt
│   └── work/ReminderWorker.kt                      # CoroutineWorker (U1/U2)
└── iosMain/kotlin/com/vehicare/
    ├── data/db/DatabaseProvider.ios.kt
    ├── notifications/NotificationScheduler.ios.kt  # UNUserNotificationCenter (U3)
    ├── notifications/NotificationPermission.ios.kt
    └── notifications/IosNotificationDelegate.kt    # install() called from Swift

androidApp/src/main/
├── AndroidManifest.xml                             # + POST_NOTIFICATIONS, launchMode=singleTop, android:name
└── kotlin/com/vehicare/
    ├── VehiCareApp.kt                              # NEW Application: channel + holder init (U1)
    └── MainActivity.kt                             # MODIFIED: onNewIntent + cold-start extra (U7)

iosApp/iosApp/
├── iOSApp.swift                                    # MODIFIED: @UIApplicationDelegateAdaptor
└── AppDelegate.swift                               # NEW: installs the Kotlin delegate at launch (U3)
```

### Pattern 1: One database instance per process, reachable without Koin

**What:** A single `VehiCareDatabase` is created once and exposed through a process-wide holder. The Compose side gets it from Koin, but the Koin definition *delegates* to the holder rather than constructing a second database. The worker reads the holder directly.
**When to use:** Any time a non-Compose Android component in `:shared` needs the database. Here: the `CoroutineWorker`.
**Why it is non-negotiable:** SQLDelight's `asFlow()` notification is driven by the **driver instance** that executed the write. Two `AndroidSqliteDriver` instances over the same file are two separate notification graphs — a worker write through driver B never wakes a `Flow` subscribed through driver A. The UI would show stale due-states until the next manual refresh, which reads as "reminders don't work". This is the single most consequential design constraint in the phase.

```kotlin
// shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseProvider.kt  — NEW, plain object (no expect/actual)
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

```kotlin
// androidApp/.../VehiCareApp.kt  — the single construction point on Android
class VehiCareApp : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        DatabaseProvider.initialize(
            DatabaseFactory(createDriverFactory(this, defaultDatabaseName).createDriver()),
        )
    }
}
```

```kotlin
// shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt  — iOS construction point
fun MainViewController(): UIViewController {
    DatabaseProvider.initialize(DatabaseFactory(createDriverFactory().createDriver()))
    return ComposeUIViewController { App() }
}
```

`AppModule` then becomes `single<VehiCareDatabase> { DatabaseProvider.require() }`, and its old `single { DatabaseFactory(getOrNull<DriverFactory>() ?: createDriverFactory()) }` plus `getOrNull<DriverFactory>()` fallback are deleted — the Android `single<DriverFactory>` binding in `platformModule` becomes unused (nothing else consumes it). `[VERIFIED: shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt — the driver is built in the shared module with a nullable-DriverFactory fallback; shared/src/androidMain/.../PlatformModule.android.kt registers exactly `Context` + `DriverFactory`]`

The existing `DatabaseFactory` doc comment already states the invariant this pattern enforces: *"SQLDelight drivers are not safe to instantiate more than once against the same file […] Nothing else in the app may construct one."* The worker is exactly the "something else" that would have violated it. `[VERIFIED: shared/src/commonMain/kotlin/com/vehicare/data/db/DatabaseFactory.kt]` `[VERIFIED: no startKoin/KoinComponent/getKoin exists anywhere in the tree; Koin is created inside `KoinApplication {}` in App.kt and only there]` `[VERIFIED: createDriverFactory(platformContext: Any?, databaseName: String) is the single platform entry point for driver construction; iOS's actual ignores the context]`

### Pattern 2: delivery seam — `commonMain` interface + `expect fun` factory (the fourth platform pair)

**What:** A `commonMain` seam with two delivery intents — `scheduleTimeBased()` (declarative: "make the OS hold the right future triggers") and `triggerImmediateIfOverdue()` (imperative: "notify now") — plus the permission read, a cancel hook for stale notifications, the fire-and-forget re-entry point and the OS-settings jump. Both implementations take the already-evaluated `List<DueReminder>` so **zero calculation happens on the platform side** (D-63).
**When to use:** Always for this phase's delivery. Do not add a fifth seam for foreground detection (U5).

```kotlin
// commonMain
interface NotificationScheduler {
    suspend fun permissionState(): NotificationPermission            // read-only; polled on every foreground
    suspend fun scheduleTimeBased(due: List<ScheduledReminder>)       // iOS: pre-schedule. Android: no-op
    suspend fun triggerImmediateIfOverdue(due: List<DueReminder>)     // iOS: post now. Android: post now
    suspend fun cancel(ruleIds: List<Long>)                           // clear pending + delivered per rule
    fun evaluateNow()                                                 // non-suspend: claim/post must not be cancelled
    suspend fun openNotificationSettings()                            // denied → the OS app settings screen
}

expect fun createNotificationScheduler(platformContext: Any? = null): NotificationScheduler
```

The interface (not an `expect class`) is deliberate: `commonTest` implements it with a fake to assert the permission gate and the three time-path cases, and the platform implementations stay `internal` (`AndroidNotificationScheduler`, `IosNotificationScheduler`). The `expect fun` factory is still the fourth `expect`/`actual` pair in the repo alongside `Platform.kt`, `AppVersion.kt` and `data/db/DriverFactory.kt`, and it is the repo's established way to add platform delivery. `[VERIFIED: filesystem inventory of shared/src/*Main]`

**One additional, composition-scoped seam is recommended** — `@Composable expect fun rememberNotificationPermissionRequester(...)` (U4). It is not a fifth *delivery* seam: Android's runtime prompt is only constructible from a composition (`rememberLauncherForActivityResult`), while iOS's is a plain suspend framework call. Keeping it separate from `NotificationScheduler` is what lets the scheduler stay a plain interface with no Compose dependency, callable from a worker.

*How the implementation gets its Android `Context`.* Mirror `DriverFactory` exactly: `expect fun createNotificationScheduler(platformContext: Any? = null): NotificationScheduler`, with the Android factory casting to `Context` and erroring with the same "load the platform module" message. Then the binding is one line per platform — `platformModule(androidContext)` registers `single<NotificationScheduler> { createNotificationScheduler(get<Context>()) }`, and `iosMain` gains a two-line `iosPlatformModule` with `single<NotificationScheduler> { createNotificationScheduler() }`, passed from `MainViewController` as `App(iosPlatformModule)`. No Koin override, no global context holder, and the same shape the database seam already uses. `[VERIFIED: DriverFactory.kt — `expect fun createDriverFactory(platformContext: Any? = null, databaseName: String = defaultDatabaseName)`]`

### Pattern 3: Flat queries + `combine`, never a LEFT JOIN

**What:** The dashboard read is `combine(selectAllVehicles, selectAllRules, selectTypeBaselines, selectVehicleStats)`; each is a flat query. Rule-to-baseline resolution happens in Kotlin.
**When to use:** For DSH-01 and for the Reminders screen list.
**Why:** (a) PITFALLS § SQL 3 — one read per list load, no N+1; (b) `LEFT JOIN` against `service_entry` at minSdk 26's SQLite 3.18 has **no window functions**, so "latest entry per (vehicle, type)" cannot be expressed in SQL without a correlated subquery that returns nullable columns and multiplies the mapper's branch count. Two flat queries plus an in-memory `groupBy` is simpler, testable in `commonTest`, and mirrors the existing `observeHistory` combine precedent. `[VERIFIED: ServiceEntry.sq — DB comment records API-26 SQLite 3.18 has no window functions; selectSummary already uses MAX() with a nullable Long result]`

### Pattern 4: One pure `ReminderStatus` produces every sentence

**What:** A `commonMain` object with no Compose and no platform types, taking a `ReminderRule`, an optional baseline, a vehicle mileage, and `now: Instant`, returning a sealed `DueState` plus the formatted sentence.
**When to use:** Card status line (C-30), rule row inline status (C-36), Detail summary row (C-37), notification body (C-40), banner copy — all five call the same functions. This is what makes C-28's "one source of truth" mechanically true rather than aspirational.
**Test surface:** pure `commonTest` — no emulator, no Robolectric, runs on `iosSimulatorArm64Test` and `testAndroidHostTest` alike.

### Pattern 5: Notification-carried navigation, both platforms

**What:** The notification identifier encodes the destination payload (`"vehicle:<id>"`), and each platform turns a tap into a `Flow<Long?>` of vehicle ids that `App.kt` collects.
**When to use:** D-55. On Android the payload travels as an `Intent` extra (survives both cold start via `activity.intent` and warm start via `onNewIntent`); on iOS it is parsed back out of `response.notification.request.identifier`, avoiding `userInfo`'s `NSDictionary`/`NSNumber` bridging entirely.
**Why identifier, not `userInfo`:** `setUserInfo(mapOf("vehicleId" to id))` requires boxing a Kotlin `Long` into something `NSDictionary` accepts and reading it back as `NSNumber` — a well-known source of `ClassCastException` in Kotlin/Native. The identifier is a `String`, free on both sides.

### Anti-Patterns to Avoid

- **A second `VehiCareDatabase` for the worker.** Silently breaks reactive UI (Pattern 1). This is the phase's #1 way to ship something that "works" in a manual test and is broken in production.
- **`KoinComponent` inside the worker.** There is no global Koin instance to find; `KoinComponent` would compile and then throw `KoinAppAlreadyStartedException`/`NoDefinitionFoundException` at runtime depending on how it is resolved. `[VERIFIED: grep — `startKoin` appears nowhere]`
- **Mutating `ReminderStatus`'s "today" from anything but an injected `Clock`.** The time axis must be testable; `Clock.System.now()` must be passed in, never read inside the pure object.
- **Requesting notification permission at launch or at screen entry.** D-54. The only trigger is a successful save of the first rule (C-39).
- **Scheduling a `PeriodicWorkRequest` per rule.** One unique periodic work for the whole app; the worker fans out over all rules. Per-rule work multiplies wakeups and Doze deferrals, and Android's job scheduler will throttle it.
- **Pre-scheduling all future iOS notifications per rule.** PITFALLS § Notif 2: iOS keeps at most 64 pending notifications; schedule only the next due per rule.
- **`NotificationManager.notify` with a constant id.** Two vehicles' reminders would overwrite each other. Use the rule id (or a stable hash of `ruleId`), and always include the channel id on API 26+.
- **Dropping the channel creation into Compose.** If a notification is ever posted from the worker before the channel exists on Android 8+, the notification is silently dropped. Channel creation belongs in `Application.onCreate` (U1).
- **Relying on `service_entry.service_type` for baseline matching.** It is a position-0 mirror of a multi-select field. The authoritative join is `service_entry_type.label` (below).

### The baseline join (authoritative shape)

`service_entry.service_type` holds only the first of the selected types — it is a display mirror kept for the Phase 2 list header. A rule created on "Brake service" must match entry #7 even when the user selected *Oil change* first. Therefore the km/time baseline resolves through the join table:

```sql
-- service_entry_type is the authoritative multi-select record (Phase 2, D-24/D-25)
selectTypeBaselines:
SELECT se.vehicle_id                AS vehicle_id,
       t.label                      AS type_label,
       MAX(se.service_date_epoch_ms) AS baseline_date_epoch_ms,
       se.mileage_km                 AS baseline_km          -- bare column: taken from the MAX row
FROM service_entry se
JOIN service_entry_type t ON t.entry_id = se.id
GROUP BY se.vehicle_id, lower(t.label);
```

Two deliberate choices here. **(1) The bare column, not `MAX(mileage_km)`.** With exactly one `MAX()` aggregate, SQLite takes bare columns from the row that supplied the maximum — the exact trick `selectLearnedTypes` already relies on in `ServiceEntry.sq`. That yields a *consistent pair* (the latest entry's mileage **and** its date), which is precisely D-47's definition. `MAX(mileage_km)` separately would be a different, weaker claim. **(2) Group on `lower(t.label)`.** Phase 2 already normalises labels case-insensitively for the chip group (`selectLearnedTypes` groups `lower(label)`), so a rule saved from the chip list carries the newest casing while older entries may carry another; matching case-insensitively in Kotlin (`rule.service_type.lowercase()`, locale-invariant) is the only way the baseline does not silently vanish when a user re-types a service with different capitalisation.

`[VERIFIED: ServiceEntry.sq — `selectLearnedTypes` comment documents the single-MAX bare-column behaviour; `service_date_epoch_ms` is "UTC start-of-day millis of the civil date"; `service_entry_type` is keyed `(entry_id, position)`]`

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Deferrable Android background execution | `AlarmManager`, a `Service`, a `BOOT_COMPLETED` receiver, JobScheduler direct | `WorkManager` 2.12.0, periodic + unique work | Reboot survival, Doze integration, battery-aware scheduling and retry/backoff are already solved; a hand-rolled alarm loses everything on reboot (PITFALLS § Android 4) and needs `SCHEDULE_EXACT_ALARM` (D-52 forbids it). |
| "Notify once per due cycle" bookkeeping | An in-memory `Set<Long>` of notified rule ids | The persisted marker column + a **conditional `UPDATE … WHERE marker IS NULL OR marker <> :baseline`**, and notify only when the affected-row count is 1 | A process can die between notify and bookkeeping; only the database write is atomic with the decision. The in-memory set also resets on every app start → re-nagging, which D-51 forbids. |
| Notification channel lifecycle | Creating the channel on first notify | `NotificationChannel` created once in `Application.onCreate` | On Android 8+ a notification posted to a non-existent channel is **silently dropped**. Creating it defensively before each notify is fine as a second guard, but the primary creation point is startup. |
| Permission compat shims | `NotificationManagerCompat`/`ContextCompat` wrappers on minSdk 26 | `context.checkSelfPermission(...)`, `context.getSystemService(NotificationManager::class.java)` | Both are API 23+/24+; the app's minSdk is 26 so the compat layer adds a dependency for nothing. |
| Calendar-month arithmetic | `30 days × months`, manual day counts | `kotlinx-datetime` `LocalDate.plus(n, DateTimeUnit.MONTH)` | Month lengths and leap years are not uniform; "every 6 months" from 31 Aug is not 180 days. |
| iOS pending-notification capacity management | Scheduling every future occurrence per rule | Schedule exactly the **next** due occurrence per rule; `removePendingNotificationRequestsWithIdentifiers` before re-adding | iOS silently discards beyond 64 pending notifications (PITFALLS § Notif 2) — the failure is invisible until a reminder never fires. |
| Notification-tap plumbing | A custom router / global navigation singleton | The OS payload (Intent extra / identifier) → a one-slot replay bus → the existing `NavHost` | The OS owns the tap; duplicating routing logic outside `App.kt` splits navigation into two truths. |
| Number/date formatting in notification copy | A second formatter for the worker | The same pure helpers the UI uses (`ReminderStatus`, the locked distance/date formatters) | C-40 requires the notification to say exactly what the card says; two formatters drift. |
| "Latest row per group" in SQL | Correlated subqueries / window functions | `GROUP BY … MAX(...)` flat queries + in-memory association | minSdk 26's SQLite 3.18 has **no window functions** (the `ServiceEntry.sq` header records this), and correlated subqueries multiply mapper nullability. |
| Form validation | Validation inside the composable | A pure object (`ReminderFormValidation`) invoked by the ViewModel | The existing `VehicleFormValidation` pattern; testable in `commonTest` with no UI. |
| Unique-work de-duplication | A "have I scheduled this already" flag in prefs | `enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, request)` | WorkManager's own uniqueness contract; a second source of truth desynchronises after a force-stop. |

**Key insight:** the three genuinely hard problems in this phase — cross-process database identity, once-per-cycle notification semantics, and month arithmetic — all have a *stateful* correct answer and a *plausible-looking* wrong one. Every one of them is solved by putting the state in SQLite and the logic in a pure `commonMain` object, not by adding a library.

## Research Findings — The Nine Open Questions

Each answer states the evidence, then what it means for the plan.

### U1 — WorkManager inside the KMP shared module: placement, version, initialisation, cadence

**Answer: declare `androidx.work:work-runtime:2.12.0` in `:shared`'s `androidMain`, use default initialisation, one daily periodic worker plus one-shot immediate work, and put the channel helper in `:shared` `androidMain` while the `Application` class stays in `:androidApp`.**

*Placement.* `:shared` is a real Android library (`com.android.kotlin.multiplatform.library` with `namespace`, `compileSdk`, `minSdk`), so its `androidMain` compiles against the Android SDK and can consume AARs — `androidx.activity:activity-compose`, `sqldelight-android-driver` and `filekit-dialogs-compose` are already declared exactly there. WorkManager follows the same line. `implementation` is the right scope: no `:androidApp` source references a WorkManager type, so the dependency only needs to be on the runtime classpath. `[VERIFIED: shared/build.gradle.kts — `android { namespace/compileSdk/minSdk }` + `androidMain.dependencies` block]`

*Initialisation.* WorkManager ships `androidx.startup.InitializationProvider` in its AAR, and Android instantiates content providers **before** `Application.onCreate`, so `WorkManager.getInstance(context)` is already valid inside `onCreate` with **no** `Configuration.Provider` and **no** manifest surgery. On-demand initialisation would require `tools:node="remove"` on that provider plus a `Configuration.Provider` implementation — all cost, no benefit here. `[CITED: developer.android.com/topic/libraries/architecture/workmanager/advanced/custom-configuration]` `[VERIFIED: work-runtime 2.12.0 POM depends on androidx.startup:startup-runtime]`

*Cadence.* `PeriodicWorkRequestBuilder<ReminderWorker>(1, TimeUnit.DAYS)` — the platform floor is 15 minutes (`PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS`), and daily is the right trade for D-52's accepted drift: finer granularity buys nothing when Doze may defer the run by hours anyway, and it multiplies wakeups. `setInitialDelay` is unnecessary; the worker is idempotent and the conditional claim makes the first run harmless. No constraints (`setRequiresBatteryNotLow(false)` default, no network requirement) because the work is local and must run on a flat battery. `[CITED: developer.android.com/reference/androidx/work/PeriodicWorkRequest — MIN_PERIODIC_INTERVAL_MILLIS = 900000]`

*Immediate evaluation.* Enqueue a `OneTimeWorkRequest` under the unique name `"reminder-eval-now"` with `ExistingWorkPolicy.REPLACE` whenever the inputs change: a rule is saved/enabled/deleted, or a service entry is logged (baseline moved). This is also the natural implementation of the Android `actual` for `triggerImmediateIfOverdue()` — the common `ON_RESUME` hook calls it on both platforms, Android defers to the worker, iOS evaluates inline. Same call site, two implementations. `[CITED: developer.android.com/topic/libraries/architecture/workmanager/how-to/managing-work]`

*Retry.* `Result.retry()` while `runAttemptCount < 3`, otherwise `Result.success()` (never `failure()` for a delivery attempt the next periodic run will repeat). `[CITED: developer.android.com/topic/libraries/architecture/workmanager/how-to/define-work — Result semantics]`

*Split.* `:androidApp` owns `AndroidManifest.xml` and therefore the `<application android:name>`, the `POST_NOTIFICATIONS` permission and `launchMode="singleTop"`. `:shared` `androidMain` owns the worker, the channel helper and the scheduler `actual`. This satisfies CONTEXT's "the split between `:shared` `androidMain` and `:androidApp` is a planning decision" with one rule: **manifest and Application class = app module; everything that touches the database or shared strings = shared module.**

*Why not `work-runtime-ktx`.* Empty since 2.9.0 (see § Standard Stack).

### U2 — How the worker reaches SQLDelight and why Koin cannot help

**Answer: a process-wide `DatabaseProvider` object in `commonMain`, initialised at each platform entry point before Compose, with the Koin graph now *reading* from it instead of constructing a database.**

*Why Koin cannot help.* A `CoroutineWorker` is instantiated reflectively by WorkManager with `(Context, WorkerParameters)`; it is not a Koin-managed object. Worse, this app has **no global Koin container at all** — `KoinApplication {}` inside `App.kt` creates a composition-scoped instance and nothing calls `startKoin`. A `KoinComponent` worker would therefore find no application to resolve from and fail at runtime, not at compile time. `[VERIFIED: grep over shared/src — `startKoin`, `KoinComponent`, `getKoin` appear nowhere; the only Koin entry is `KoinApplication(application = { modules(appModule, platformModule) })` in App.kt]`

*Why not re-create the database in the worker.* SQLDelight's listener registry lives on the **driver**: `addListener`/`notifyListeners` are `SqlDriver` methods, and a write through driver B does not wake a `Flow` subscribed through driver A. A second `AndroidSqliteDriver` over the same file therefore produces a UI that never learns the worker wrote anything — the due-state badge would stay stale until the app restarted, which is indistinguishable from "reminders are broken". The repo already documents the invariant: *"SQLDelight drivers are not safe to instantiate more than once against the same file […] Nothing else in the app may construct one."* `[VERIFIED: DatabaseFactory.kt doc comment]` `[CITED: sqldelight.github.io — SqlDriver listener API backs `asFlow()`]`

*Why an object rather than a fifth `expect`/`actual` pair.* Both platform entry points can already reach `createDriverFactory(...)` (it is a common `expect fun`), so a plain `commonMain` object with `initialize(factory)` / `require()` needs **no** new expect/actual and keeps the seam count at four. The alternative — registering the database in the Android platform module and keeping a fallback in `appModule` — was rejected because two bindings for one type resolve by module-override order, which is implicit and fails differently under different `allowOverride` settings.

*Threading.* `CoroutineWorker.doWork()` runs on `Dispatchers.Default`, so the worker must `withContext(ioDispatcher)` like every repository. Be aware that each repository currently constructs its **own** `Dispatchers.IO.limitedParallelism(1)` (`VehicleRepositoryImpl:29`, `ServiceEntryRepositoryImpl:39`) — these are independent views, not one shared lane, so the "serialized IO lane" is a per-repository convention rather than a process-wide mutex. The worker must not *depend* on it for correctness: the atomic guard is the conditional `UPDATE` described in § Don't Hand-Roll. `[VERIFIED: both repository files declare their own `private val ioDispatcher = Dispatchers.IO.limitedParallelism(1)`]`

*Atomic once-per-cycle claim.* Mutators in SQLDelight 2.4.1 return `QueryResult<Long>` (affected rows) — `generateAsync = false`, so the value is available synchronously as `.value`; the existing repositories simply discard it. The worker uses it: run the conditional `UPDATE` inside `database.transactionWithResult`, and post the notification only when the returned count is `1`. Two concurrent evaluations (periodic + immediate) then produce exactly one notification. `[VERIFIED: shared/build/generated/sqldelight/.../VehicleQueries.kt — `delete(id): QueryResult<Long>`; `insert(...): QueryResult<Long>`]`

### U3 — `UNUserNotificationCenter` from `iosMain`

**Answer: it is fully reachable from Kotlin/Native through `platform.UserNotifications.*`, all of it behind `@OptIn(ExperimentalForeignApi::class)`, and two details are easy to get wrong: the delegate must be strongly retained *and* must implement `willPresent`, and every scheduled trigger instant must be strictly in the future — a past instant is skipped, never rolled forward.**

Verified API surface (names as they appear in compiling Kotlin/Native code):

| Need | Kotlin/Native call |
|------|--------------------|
| Centre | `UNUserNotificationCenter.currentNotificationCenter()` |
| Status | `getNotificationSettingsWithCompletionHandler { settings -> settings?.authorizationStatus }` |
| Ask | `requestAuthorizationWithOptions(options, completionHandler = { granted, error -> })` |
| Status values | `UNAuthorizationStatusNotDetermined` / `…Denied` / `…Authorized` / `…Provisional` / `…Ephemeral` |
| Content | `UNMutableNotificationContent().apply { title = …; body = …; sound = UNNotificationSound.defaultSound() }` |
| Trigger | `UNCalendarNotificationTrigger.triggerWithDateMatchingComponents(components, repeats = false)` |
| Components | `NSDateComponents().apply { year = …; month = …; day = …; hour = …; minute = … }` (properties are `NSInteger` → `Long`) |
| Add | `center.addNotificationRequest(request) { error -> }` |
| Request | `UNNotificationRequest.requestWithIdentifier(identifier, content, trigger)` |
| Cancel | `removePendingNotificationRequestsWithIdentifiers(listOf(id))`, `removeDeliveredNotificationsWithIdentifiers(listOf(id))` |
| Delegate | `center.delegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol { … }` |

`[CITED: gh_grep — these exact signatures appear in shipping Kotlin/Native notification code in public KMP repositories]` — Apple's own documentation was not reachable this session (all search providers are disabled); see § Assumptions Log A1.

**Pitfall A — `delegate` is a weak reference.** A Kotlin `object : NSObject(), UNUserNotificationCenterDelegateProtocol` assigned inline is eligible for collection immediately, after which taps silently stop being delivered. Retain it in a property of a long-lived object (the `iosMain` scheduler singleton) **and** keep the installation call in the Swift `AppDelegate`. `[CITED: developer.apple.com UNUserNotificationCenter.delegate is `weak`]`

**Pitfall B — foreground delivery is suppressed by default.** iOS mileage checks *only* run while the app is in the foreground (D-53), so the notification they post is a foreground notification. Without `userNotificationCenter(_:willPresent:withCompletionHandler:)` returning an alert/banner option, iOS delivers **nothing visible**. The delegate must implement `willPresent` and call `completionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList)` (fall back to `…Alert` on older iOS). This is the single most likely "reminders work on Android and not on iOS" bug in the phase. `[CITED: developer.apple.com — willPresent default behaviour suppresses presentation]`

**Pitfall C — a past-dated calendar trigger fires immediately.** If a rule is 3 months overdue and the code schedules a trigger for `baseline + interval` (in the past), iOS fires it as soon as it is added — on every reschedule, which is a nag loop. The scheduling code must compute `dueDate` and then advance by whole intervals until it is strictly in the future, and schedule *that*. The "it is already overdue" case is not a scheduled notification at all: it is delivered by `triggerImmediateIfOverdue()` on the next foreground. `[CITED: developer.apple.com — UNCalendarNotificationTrigger with non-repeating past components]`

**Pitfall D — installation order.** The delegate must exist before the app finishes launching, because the launch-from-notification response is delivered right after `didFinishLaunchingWithOptions`. Setting it lazily from a SwiftUI `.task` is a cold-start race that loses the first tap. Hence `@UIApplicationDelegateAdaptor(AppDelegate.self)` → `didFinishLaunchingWithOptions` → `IosNotificationBridge.shared.install()`. `[CITED: PITFALLS.md § Pitfall 5 — delegate set before KMP init]`

### U4 — Reading permission state: `notDetermined` vs `denied`

**Answer: iOS gives the truth through `UNAuthorizationStatus`; Android does not, so the two-variant decision is derived from a fact the phase already has — "does at least one reminder rule exist?"**

*iOS.* `getNotificationSettingsWithCompletionHandler` is the only reliable read; `UNAuthorizationStatusNotDetermined` means the one-time prompt has never been shown and the explanation dialog (C-39) should lead; `…Denied` means only Settings can help; `…Authorized`/`…Provisional`/`…Ephemeral` all count as granted. Nothing about this is observable — it must be polled on every foreground (D-61). `[CITED: developer.apple.com/documentation/usernotifications/unnotificationsettings]`

*Android.* The state is the conjunction of two facts: `context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)` (API 33+) and `NotificationManager.areNotificationsEnabled()`. Neither distinguishes "never asked" from "denied permanently": after a denial, the permission result and `shouldShowRequestPermissionRationale` are identical to the never-asked case on many OEM builds. The phase therefore derives the distinction from the rule count, which is exactly the condition the banner already requires (D-61): **zero rules → nothing to show; ≥1 rule and notifications disabled → `denied`**. That matches C-38's two copy variants without inventing storage, and it is correct by construction because D-54 guarantees the prompt only ever follows a rule save. `[VERIFIED: PITFALLS.md § Notif 3 — permission state is not reactive on either platform]`

*Requesting.* Android needs an `ActivityResultLauncher` bound to the composition (`rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission())`), which cannot be constructed from `commonMain`. **Recommendation:** add one small `@Composable expect fun rememberNotificationPermissionRequester(onResult: (Boolean) -> Unit): () -> Unit` with an `androidMain` actual (the launcher) and an `iosMain` actual (`requestAuthorizationWithOptions`). This is a *second* notification seam alongside `NotificationScheduler`, and it is justified: the OS prompt is an Activity-scoped concept on one platform and a framework call on the other, and there is no way to hide the difference behind a plain interface. The alternative — caching an `Activity` reference in a holder so a non-composable `actual` can call `requestPermissions` — leaks an Activity and loses the result callback.

*After the result.* Refresh the state flow and let the banner disappear; a denial must never roll back the saved rule (D-54/C-39).

### U5 — The KMP foreground hook

**Answer: `LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { … }` in `commonMain`, with no `expect`/`actual`.**

*Why it works on both platforms.* The composable is in `lifecycle-runtime-compose:2.11.0`, already a `commonMain` dependency of `:shared`. CMP 1.12.1's iOS implementation of `LocalLifecycleOwner` derives `RESUMED` from `isViewAppeared && isSceneInForeground && isSceneActive` and drives transitions from `UIScene` notifications, so returning from another app or the home screen re-emits `ON_RESUME`. On Android the same composable observes the Activity's lifecycle. One implementation, two platforms. `[VERIFIED: androidx.lifecycle 2.11.0 `lifecycle-runtime-compose` commonMain sources — `LifecycleEventEffect(event, lifecycleOwner, onEvent)`]` `[VERIFIED: CMP 1.12.1 `ui/iosMain/.../ComposeContainerLifecycleDelegate.ios.kt` — RESUMED composed from scene + view state]`

*Placement.* Inside `App.kt`, above the `NavHost`, so it survives every navigation and is not owned by a screen that can be popped. It also fires on the very first composition, which is what makes a long-overdue rule notify at launch — desirable.

*What it triggers.* `NotificationScheduler.triggerImmediateIfOverdue(evaluate(now))` plus a `NotificationScheduler.permissionState()` refresh. Both are idempotent: the due list is computed by the same pure function, and the conditional claim (`markNotified`) makes delivery once-per-cycle — `armed_cycle_key` only records that the OS already had the cycle and is never a claim.

*Guard against double-fire.* `ON_RESUME` can fire in quick succession (e.g. returning from the permission dialog triggers a configuration-less resume on some devices). Keep a short single-flight guard (`Mutex.tryLock`) in the scheduler, or simply rely on the atomic `UPDATE` — a second evaluation finds the claim set and notifies nothing.

*Rejected alternative:* a fifth `expect`/`actual` (`AppForegroundDetector`). It would duplicate what the lifecycle already models, and the iOS `actual` would have to re-implement scene-observer bookkeeping that CMP already ships.

### U6 — Dashboard aggregation in SQLDelight 2.4.1: the query shape

**Answer: four flat queries combined in Kotlin — vehicles, rules, type baselines, per-vehicle stats — with zero per-vehicle queries and no `LEFT JOIN`.**

```kotlin
// commonMain repository — one read per list load, constant query count regardless of fleet size
combine(
    vehicleQueries.selectAll().asFlow().mapToList(io),
    reminderRuleQueries.selectAll().asFlow().mapToList(io),
    reminderRuleQueries.selectTypeBaselines().asFlow().mapToList(io),
    serviceEntryQueries.selectVehicleStats().asFlow().mapToList(io),
) { vehicles, rules, baselines, stats -> /* associate + ReminderStatus.evaluate */ }
    .map { snapshot -> DashboardState.Content(snapshot) }
```

*Why four and not one.* DSH-01 needs per vehicle (a) the latest service date, (b) the most urgent rule's due state. A single statement would have to join `vehicle → reminder_rule → service_entry → service_entry_type` and aggregate two different axes at two different grains; the result multiplies rows (one per rule × entry-type) and every aggregate becomes nullable-by-construction in the generated mapper. Four flat reads whose results are keyed by `vehicle_id` (and `lower(label)`) can each be reasoned about and tested on their own, and `combine` re-emits whenever *any* of the four tables changes — which is exactly the invalidation the dashboard needs. `[CITED: sqldelight.github.io — `asFlow()` emits on writes to the queried tables; `mapToList(dispatcher)` materialises a `Flow<List<T>>`]`

*This refines the "two flat queries" shorthand recorded during discussion.* Two would cover vehicles + rules but not DSH-01's *last service date* nor D-49's vehicle-level fallback mileage; those are two more aggregates, both flat, both `GROUP BY vehicle_id`. The invariant the discussion actually wanted — "one aggregated read per list load, no N+1" (PITFALLS § SQL 3) — is preserved: **four queries total, never four per vehicle.**

*The other read: `selectVehicleStats`.* It needs the latest entry's date **and** its mileage (D-49's fallback, C-30's sentence context), which the bare-column trick delivers in one statement:

```sql
selectVehicleStats:
SELECT vehicle_id,
       MAX(service_date_epoch_ms) AS last_service_epoch_ms,
       mileage_km                 AS last_mileage_km     -- bare column: the MAX row's mileage
FROM service_entry
GROUP BY vehicle_id;
```

This is the *same* semantics as the existing `selectLatestMileage` (`ORDER BY service_date_epoch_ms DESC, id DESC LIMIT 1`), applied to all vehicles at once — deliberately, so D-49's fallback and Phase 2's display never disagree. `[VERIFIED: ServiceEntry.sq — selectLatestMileage orders by date then id, so "latest" is date-first]`

*Generated-type gotchas in SQLDelight 2.4.1:*

- `MAX(...)` is typed **nullable** in the generated row (`SelectSummary.last_service_epoch_ms: Long?` proves it), even when `GROUP BY` guarantees a non-empty group. The mapper must handle it with `?: 0L` or, better, treat `0L`/`null` as "no data" explicitly.
- `COUNT(*)` is non-null `Long` (`entry_count: Long`).
- Mutators return `QueryResult<Long>`; with `generateAsync = false` the existing repositories simply discard the result and rely on eager execution. New code that needs the affected-row count reads `.value`.
- No window functions at SQLite 3.18 (minSdk 26) — the bare-column/`MAX` idiom is the sanctioned substitute, not a workaround.

`[VERIFIED: shared/build/generated/sqldelight/.../SelectSummary.kt — `last_service_epoch_ms: Long?`; VehicleQueries.kt — mutators return `QueryResult<Long>`]`

*Ordering.* C-29's six-level key (`group → urgency → axis → rule id → vehicle name → vehicle id`) is computed in Kotlin from the snapshot, not in SQL: `urgency = elapsed / interval` is a division across two different units and cannot be expressed portably, and the tie-break chain would make an unindexable `ORDER BY`. Sorting N vehicles in memory is free. `[CITED: 03-UI-SPEC.md C-29 — the ordering contract]`

### U7 — Notification tap → vehicle Detail on both platforms

**Answer: encode `vehicleId` in the notification identifier on iOS and in the Intent extra on Android; funnel both into one `commonMain` replay-1 bus that `App.kt` collects.**

*Shared bus (commonMain).*

```kotlin
object NotificationTapBus {
    // replay = 1 so a cold-start tap that arrives before composition is not lost.
    private val _taps = MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 0)
    val taps: SharedFlow<Long> = _taps
    fun publish(vehicleId: Long) { _taps.tryEmit(vehicleId) }
    fun consume() { _taps.resetReplayCache() }   // call after navigating, or rotation re-navigates
}
```

Collected once in `App.kt` above the `NavHost`; navigating to `VehicleDetailRoute(vehicleId)` with `launchSingleTop = true`, then `consume()`.

*Android.*
- `PendingIntent.getActivity(context, requestCode = ruleId.toInt(), intent, FLAG_IMMUTABLE or FLAG_UPDATE_CURRENT)`. **`FLAG_IMMUTABLE` (or `FLAG_MUTABLE`) is mandatory from API 31** — omitting it throws at runtime. `FLAG_UPDATE_CURRENT` keeps the extras current when the same rule re-notifies. A per-rule `requestCode` is required, otherwise `FLAG_UPDATE_CURRENT` overwrites one rule's extras with another's. `[CITED: developer.android.com — PendingIntent mutability requirement on Android 12+]`
- `MainActivity` gets `android:launchMode="singleTop"`; it publishes from `intent` in `onCreate` (cold start / process death) **and** from `onNewIntent` (warm start, with `setIntent(intent)` first so `getIntent()` stays truthful). One activity hosts the whole app, so a notification tap never stacks a second instance.
- `NotificationManager.notify(ruleId.toInt(), notification)` — the per-rule id means a re-notify replaces rather than duplicates.

*iOS.*
- Identifier format `"v<vehicleId>:r<ruleId>"`, produced and parsed by a pure `commonMain` helper (`ReminderNotificationId`) so both sides agree and the parser is unit-tested. Using the identifier avoids `userInfo`'s `NSDictionary`/`NSNumber` boxing entirely.
- In `userNotificationCenter(_:didReceive:withCompletionHandler:)`, act only when `response.actionIdentifier == UNNotificationDefaultActionIdentifier` (a dismiss must not navigate), parse the id, `NotificationTapBus.publish(vehicleId)`, then `completionHandler()`.
- Cancel-by-rule needs enumeration, since the identifier is per rule: `getPendingNotificationRequestsWithCompletionHandler { requests -> center.removePendingNotificationRequestsWithIdentifiers(requests.map { it.identifier }.filter { it.endsWith(":r$ruleId") }) }` (and the same for delivered). The list is capped at 64, so this is trivial. `[CITED: developer.apple.com — pending requests are enumerable, removals are by identifier]`

*Why a replay-1 bus rather than a `Channel` or a nullable state.* A cold-start tap publishes before the first composition (Android: `onCreate`; iOS: the launch response). `MutableSharedFlow(replay = 1)` delivers that value to the first collector, and `resetReplayCache()` prevents a re-navigation on configuration change. A `Channel` would need an explicit receiver before the send; nullable state would re-navigate on every recomposition that re-reads it.

### U8 — Test strategy with no emulator and no device in CI

**Answer: the worker must contain no logic. Everything decidable is a pure `commonMain` function tested in `commonTest`; the SQL is proved against a real driver in `iosTest`; the Android-only glue is explicitly manual.**

| Layer | Where it is tested | What it proves |
|-------|--------------------|----------------|
| `ReminderStatus.evaluate(rule, baseline, vehicleStats, now)` → sealed `DueState` | `commonTest` | km axis, time axis, "whichever comes first", exact-zero = `Due now`, lead windows, `No mileage data yet`, D-49 fallbacks |
| `ReminderStatus.urgency` / `orderBy` | `commonTest` | C-29's four groups and every tie-break, including the case-insensitive name compare |
| `ReminderFormValidation` | `commonTest` | C-34 (non-blank ⇒ ≥ 1) and C-35's gate, including "blank is not an error" |
| `ReminderInterval.sentence` inputs and `ReminderNotificationId` encode/parse | `commonTest` | C-42 vocabulary inputs, id round-trip, malformed-id rejection |
| `selectTypeBaselines` / `selectVehicleStats` / conditional `markNotified` | `iosTest` (real `NativeSqliteDriver`, scratch db name) | baseline pairing via the bare column, case-insensitive grouping, affected-row count 1 then 0 |
| `3.sqm` → `4.db` migration | `iosTest` + CI guards | schema equality; existing `ServiceEntryMigrationTest` is the template |
| Vehicle-delete cascade includes `reminder_rule` | `iosTest` | D-64 |
| `ReminderWorker`, `UNUserNotificationCenter` calls, permission prompts, tap routing | **manual** | no emulator job exists in `.github/workflows/ci.yml` |

*Consequence for the plan:* the worker is ~15 lines — evaluate via the shared function, claim via `markNotified`, render via `getString`, post. Every one of those four steps other than "post" is separately testable; "post" is the only manual step on Android.

*Resource strings are not unit-testable* — `getString` needs the generated `ResourceContainer`, so the pure helper must return a **state + numbers**, never a formatted sentence. One `commonMain` `ReminderCopy` (suspend, resource-bound) turns any `DueState` into the C-28/C-40 wording; card, rule row, summary row and both platforms' notifications call it. That keeps C-28's "one source of truth" while leaving the state machine assertable in `commonTest`. `[VERIFIED: components-resources 1.12.1 — `getString`/`getPluralString` are suspend and require `Res`; no pure formatting path]`

*Running the suites.* `./gradlew :shared:testAndroidHostTest` and `./gradlew :shared:iosSimulatorArm64Test` both already run in CI; `commonTest` is picked up by both. There is no `androidDeviceTest` job and adding one is out of scope. `[VERIFIED: .github/workflows/ci.yml]`

### U9 — Pinned-stack API risk (the UI-SPEC's component assumptions)

Every assume-and-verify item the UI-SPEC relies on was checked against the exact artifacts in the Gradle cache. **No contract is blocked.**

| Assumption | Verdict | Evidence |
|------------|---------|----------|
| `SwitchDefaults.colors(...)` exposes the parameters C-33 needs | ✅ Feasible | `checked/unchecked` × `Thumb/Track/Border/IconColor` + `disabled*` all present in material3 1.12.0-alpha03 sources. `[VERIFIED: material3-1.12.0-alpha03-sources.jar — SwitchDefaults.colors]` |
| M3's default unchecked track is outside the palette (so C-33 is necessary, not decorative) | ✅ Confirmed | `SwitchTokens.UnselectedTrackColor = SurfaceContainerHighest` → the scheme's `Neutral92 #ECE6F0`; the app's palette does not override it. `[VERIFIED: material3 sources — SwitchTokens]` |
| The switch gets a 48dp touch target for free | ✅ Confirmed | `Modifier.minimumInteractiveComponentSize()` is applied only when `onCheckedChange != null` (`Switch.kt:104-112`) — the rule row passes a live handler. `[VERIFIED: material3 sources]` |
| `imePadding()` works on iOS (the editor sits above the keyboard) | ✅ Confirmed | `foundation-layout/skikoMain/WindowInsetsPadding.skiko.kt:96` → `ime.toWindowInsets()`; `ui/iosMain/UIKitWindowInsetsManager.ios.kt:166` → `ime = PlatformInsets(getBottom = keyboardOverlapHeight)`. `[VERIFIED: CMP 1.12.1 sources]` |
| `getString`/`getPluralString` are callable from a coroutine with no Compose scope | ✅ Confirmed (suspend) | `components-resources:1.12.1` commonMain declares both as `suspend fun`; usable from `doWork()` and from iOS coroutines. `[VERIFIED: components-resources 1.12.1 sources]` |
| `LifecycleEventEffect` exists in the already-declared lifecycle artifact | ✅ Confirmed | `lifecycle-runtime-compose:2.11.0` commonMain. `[VERIFIED: sources]` |
| `FlowRow` (`@ExperimentalLayoutApi`) is available for the chip group | ✅ Already used in Phase 2 (`ServiceTypePicker`) | `[VERIFIED: source]` |

*Residual risks (not blockers):* material3 is pinned at an `alpha03`; the parameters in use are stable M3 API and have not moved across the 1.12 alphas. `imePadding()` on iOS was verified for **existence and wiring**, not for pixel-perfect behaviour against every keyboard — UI-SPEC verification note #8 already schedules a device pass for it.

*One API-shape note for the plan:* `androidx.work` types must never leak into `commonMain`. If a shared signature needs to expose a schedule, it exposes a plain data class (rule id, epoch ms, title, body) — the `androidMain` `actual` converts to a `WorkRequest`. The same rule the codebase already applies to `Context` (`Any?` in `createDriverFactory`).

## Schema: `reminder_rule` and the `3.sqm` migration

### Table

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
    notified_cycle_key  TEXT,
    armed_cycle_key     TEXT
);

CREATE INDEX reminder_rule_vehicle ON reminder_rule(vehicle_id);
```

Column-by-column rationale (the CONTEXT left the extra shape to the agent):

- **`vehicle_id … REFERENCES vehicle(id)` with no `ON DELETE CASCADE`.** Foreign keys are **never enabled** in this app — no `PRAGMA foreign_keys=ON` exists, as `ServiceEntry.sq` explicitly documents — so a cascade clause would be decorative. D-64 is satisfied by an explicit `deleteRulesForVehicle` inside the existing `VehicleRepositoryImpl.delete` transaction, exactly like `deleteEntriesForVehicle`. `[VERIFIED: ServiceEntry.sq comment — "ON DELETE CASCADE documents intent only: foreign keys are never enabled […] the explicit delete queries below are what remove rows"]`
- **`interval_km` / `interval_months` nullable, at least one non-null.** D-46. Two columns, not a polymorphic `(value, unit)` pair: the "whichever comes first" rule needs both simultaneously, and a unit column would make every query branch.
- **`lead_km` / `lead_days` nullable** (D-50). Empty = notify exactly at due. Validated ≥ 1 when non-blank (C-34).
- **`enabled INTEGER NOT NULL DEFAULT 1`.** No boolean adapter exists in the project and SQLDelight maps `INTEGER` to `Long`; the mapper does `enabled != 0L`. Introducing a `ColumnAdapter<Boolean, Long>` for one column would add a second custom-type mechanism for no gain.
- **`created_at_epoch_ms` as UTC start-of-day millis** — deliberately the *same* convention as `service_entry.service_date_epoch_ms` ("UTC start-of-day millis of the civil date"), because D-49 makes it a time baseline that is compared against entry dates. Storing a wall-clock instant here would make rules created at 23:50 behave differently from rules created at 00:10 on the same day.
- **`notified_cycle_key TEXT` nullable, `NULL` = never notified in the current cycle.** See below.
- **`armed_cycle_key TEXT` nullable, `NULL` = no OS request has been accepted for the current cycle.** This is **bookkeeping, not a claim**: it is written only after `scheduleTimeBased` returns successfully, and it answers one question the claim cannot answer on iOS — "did the OS ever have this cycle?" A pre-scheduled request fires with no app code running, so the app cannot write `notified_cycle_key` at delivery. The sweep uses this marker to distinguish an armed cycle that is now past (claim only, the OS already had its chance) from a cycle that was never armed because the rule was created inside or after its own window (claim **then post**, so the first notification is not swallowed). It is never read as a claim and never gates a post by itself. See "Why a second marker" below.

### Why a cycle key, not a timestamp or two baseline columns

D-51 needs "silent until the baseline moves **or the rule is edited**". A single `notified_at_epoch_ms` cannot express that. Two baseline columns (`notified_baseline_km`, `notified_baseline_date`) get closer but still fail on rule edits: changing `interval_km` from 5 000 to 3 000 re-arms nothing because the baseline did not move. The cycle key is the output of a pure function and includes exactly the inputs that define a cycle:

```kotlin
// pure, commonTest-able
fun cycleKey(rule: ReminderRule, baseline: Baseline?): String =
    buildString {
        append("b"); append(baseline?.km ?: -1); append(':')
        append(baseline?.dateEpochMs ?: rule.createdAtEpochMs); append('|')
        append("i"); append(rule.intervalKm ?: -1); append(':'); append(rule.intervalMonths ?: -1); append('|')
        append("l"); append(rule.leadKm ?: -1); append(':'); append(rule.leadDays ?: -1)
    }
```

Consequences, each of which is a requirement:
- logging a service of that type changes `baseline` → new key → re-arms (D-48);
- editing any interval or lead changes the key → re-arms (D-51);
- two evaluations in the same cycle compute the same key → the conditional `UPDATE` affects 0 rows → no second notification (D-51);
- deleting an entry moves the baseline backwards → new key → the rule legitimately becomes due again. That is correct, not a regression, and it is a case worth a `commonTest` assertion.

### Why a second marker (`armed_cycle_key`) on iOS

D-53 splits delivery: time-based rules are pre-scheduled with the OS, mileage rules run in the foreground sweep. When a pre-scheduled request fires, **no app code runs**, so `markNotified` cannot be called at delivery. Without a second fact the next sweep would see `notified_cycle_key = NULL` and either re-arm the same cycle (double notification) or claim it silently (the first notification of a rule created inside its own window is swallowed). `armed_cycle_key` records that the OS accepted a request for that exact cycle, and `scheduleTimeBased` is its only writer. The time path is then exactly three cases:

- **armed and strictly past** → claim only: the OS had its chance, and the claim stops future sweeps from re-arming the cycle.
- **armed and due today** → retain the request **unclaimed**: the 09:00 trigger may still fire, and the first sweep that sees the instant strictly past writes the claim.
- **never armed, notification instant today or past** → claim **then post**: the rule was created inside or after its own window, and claiming without posting would make its first notification silent.

A claimed cycle is never re-armed, and the armed marker is never a claim. Editing or toggling the rule clears both markers, so the next schedule is a fresh arm.

The repository *also* clears both keys explicitly on rule edit — defence in depth, in case a future column is added that the key does not cover.

### Queries

```sql
selectForVehicle:  SELECT * FROM reminder_rule WHERE vehicle_id = ? ORDER BY id;
selectAll:         SELECT * FROM reminder_rule ORDER BY vehicle_id, id;
selectById:        SELECT * FROM reminder_rule WHERE id = ?;

insert:
INSERT INTO reminder_rule(vehicle_id, service_type, interval_km, interval_months,
                          lead_km, lead_days, enabled, created_at_epoch_ms,
                          notified_cycle_key, armed_cycle_key)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL);

update:
UPDATE reminder_rule
SET service_type=?, interval_km=?, interval_months=?, lead_km=?, lead_days=?,
    enabled=?, notified_cycle_key=NULL, armed_cycle_key=NULL
WHERE id=?;

setEnabled:
UPDATE reminder_rule
SET enabled=?, notified_cycle_key=NULL, armed_cycle_key=NULL
WHERE id=?;

-- The atomic once-per-cycle claim. Returns the number of rows changed.
markNotified:
UPDATE reminder_rule SET notified_cycle_key = ?
WHERE id = ? AND (notified_cycle_key IS NULL OR notified_cycle_key <> ?);

-- Bookkeeping only: the caller writes it after the platform accepted the request.
markArmed:
UPDATE reminder_rule SET armed_cycle_key = ?
WHERE id = ? AND (armed_cycle_key IS NULL OR armed_cycle_key <> ?);

delete:              DELETE FROM reminder_rule WHERE id = ?;
deleteRulesForVehicle: DELETE FROM reminder_rule WHERE vehicle_id = ?;

countEnabledForVehicle:
SELECT COUNT(*) FROM reminder_rule WHERE vehicle_id = ? AND enabled = 1;
```

`markNotified`'s `WHERE` clause is the whole of D-51's guarantee — it is a compare-and-set, not a read-then-write, so the periodic run and the immediate run cannot both notify. `markArmed` has the same compare-and-set shape but is **not** a claim: its affected-row count is ignored, it is written only after `scheduleTimeBased` returns, and a failed schedule leaves the cycle unarmed so the next sweep retries.

### Migration `3.sqm` → baseline `4.db`

```sql
-- migrations/3.sqm — file name = the version being upgraded FROM (v3 → v4).
-- The DDL below must stay structurally identical to the CREATE TABLE in ReminderRule.sq:
-- verifyCommonMainVehiCareDatabaseMigration compares the two, and any drift in column order or
-- constraint fails the build.
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
    notified_cycle_key  TEXT,
    armed_cycle_key     TEXT
);

CREATE INDEX reminder_rule_vehicle ON reminder_rule(vehicle_id);
```

Mechanics that must not be guessed:
- `sqldelight/databases/` currently contains `1.db`, `2.db`, `3.db` — the committed baselines are named for the schema version they describe, so the phase produces **`4.db`**. The file name of the migration is the version it upgrades **from**, hence `3.sqm`. `[VERIFIED: shared/src/commonMain/sqldelight/databases/ inventory + 2.sqm header "File name = the version being upgraded FROM"]`
- Regeneration command: `./gradlew generateCommonMainVehiCareDatabaseSchema`, then commit `4.db`; CI fails on any drift (`git diff --exit-code -- shared/src/commonMain/sqldelight/databases`).
- `./gradlew verifyCommonMainVehiCareDatabaseMigration` replays the `.sqm` chain and compares it to the `.sq` schema.
- No `PRAGMA` lines in the `.sqm` — the same rule as the `.sq` files, for the same reason (the iOS non-query path rejects row-returning statements).
- This migration adds a table only: no `ALTER TABLE`, so the SQLite 3.18 limitations (`DROP COLUMN`, `RENAME` rewriting `sqlite_master`) do not apply. That is a deliberate reason to keep the table additive. `[VERIFIED: ServiceEntry.sq comment on the 3.18 constraints]`

## Common Pitfalls

### Pitfall 1: The worker and the UI end up with two databases
**What goes wrong:** the worker constructs its own `VehiCareDatabase`; writes succeed but the dashboard never updates.
**Why it happens:** the worker cannot see Koin, so creating an instance "locally" looks harmless.
**How to avoid:** the `DatabaseProvider` holder (U2), plus a grep-level rule in review: `DatabaseFactory(` appears exactly once in production code.
**Warning signs:** due state correct after a restart but stale in-session; `NotificationScheduler` tests pass while the UI "doesn't refresh".

### Pitfall 2: The iOS delegate is collected or installed too late
**What goes wrong:** notifications arrive but tapping one opens the app on the home screen.
**Why it happens:** `UNUserNotificationCenter.delegate` is a **weak** reference, and a SwiftUI `.task` installs it after the launch response was already delivered.
**How to avoid:** strong reference in the `iosMain` scheduler singleton; `@UIApplicationDelegateAdaptor` → `didFinishLaunchingWithOptions` → `install()`.
**Warning signs:** works while the app is running, fails from a cold start — the classic signature.

### Pitfall 3: iOS mileage notifications are invisible because they are foreground notifications
**What goes wrong:** `triggerImmediateIfOverdue()` posts while the app is open, and iOS suppresses it by default.
**How to avoid:** implement `willPresent` and return a banner/list presentation option (U3, Pitfall B).
**Warning signs:** the notification appears in Notification Centre but no banner is shown, or nothing at all.

### Pitfall 4: A past-dated `UNCalendarNotificationTrigger` fires immediately, every time it is scheduled
**What goes wrong:** an overdue rule produces a notification on every foreground/save, re-nagging (D-51 violation).
**How to avoid:** never roll a due date forward. `ReminderSchedule.currentOccurrence` is the cycle's due date, `notificationDate` is that date minus `lead_days`, and neither ever rolls. The orchestrator schedules only a `notificationDate` strictly in the future, plus an already-armed due-today row; the iOS actual resolves the trigger instant and skips anything whose `timeIntervalSinceNow <= 0`, so an already-fired request cannot be resurrected. An overdue cycle is either claimed **then** posted by the sweep (never armed) or claimed **without** posting when `armed_cycle_key` proves the OS already had it (U3, Pitfall C).
**Warning signs:** duplicate notifications whose count matches screen visits rather than due cycles.

### Pitfall 5: Pending iOS notifications outlive the rule
**What goes wrong:** a deleted, disabled or edited rule still fires — iOS holds the trigger in the OS.
**How to avoid:** a single invariant — **every mutation that invalidates a notification reports the affected rule ids to a repository hook, and the Koin binding calls `NotificationScheduler.cancel(ruleIds)` before `evaluateNow()`**. `ReminderRepositoryImpl` passes the edited/toggled/deleted rule id; `VehicleRepositoryImpl` collects the vehicle's rule ids before the cascade delete and passes them with the vehicle id. The identifiers carry `vehicleId` and `ruleId`, so `cancel` needs no row; iOS removes both pending (`v<id>:r<id>`) and delivered (`v<id>:r<id>:now`) requests, and Android cancels `ruleId.toInt()`.
**Warning signs:** a reminder for a rule that no longer exists.

### Pitfall 6: The notification channel does not exist yet
**What goes wrong:** the first notification is silently dropped on Android 8+.
**How to avoid:** create the channel in `Application.onCreate` and again defensively in the notify path (idempotent).
**Warning signs:** "the first one never arrives, later ones do" (or the reverse after a reinstall).

### Pitfall 7: Notification and `PendingIntent` id collisions
**What goes wrong:** vehicle B's reminder replaces vehicle A's, or A's tap opens B.
**How to avoid:** notification id = `PendingIntent` request code = `ruleId.toInt()`; never a constant, never `vehicleId` alone (two rules on one vehicle would collide).
**Warning signs:** the number of live notifications is smaller than the number of due rules.

### Pitfall 8: Off-by-one days on the time axis
**What goes wrong:** "Due soon — 0 days left" appears a day early, or an overdue rule reads as due today.
**Why it happens:** `service_date_epoch_ms` is UTC start-of-day of a **civil** date; subtracting instants and dividing by 86 400 000 breaks across DST and local midnight.
**How to avoid:** convert both sides to `LocalDate` in the device timezone and subtract with `kotlinx-datetime` day arithmetic; unit-test around a DST boundary.
**Warning signs:** tests pass in UTC CI but the device (UTC+7) shows different counts.

### Pitfall 9: Copy drift between platform notifications and the card
**What goes wrong:** the notification says "Overdue by 12 days" and the card says "Due now".
**Why it happens:** two formatters, or a notification built in `androidMain` with its own string.
**How to avoid:** one `ReminderCopy` in `commonMain` used by both, and one `ReminderStatus` producing the state (U8).
**Warning signs:** any string literal in `androidMain`/`iosMain` outside the channel name.

### Pitfall 10: WorkManager is dead after a force-stop
**What goes wrong:** after the user force-stops the app (common on aggressive OEM builds), no reminder fires until the app is opened again.
**Why:** force-stop puts the package in the stopped state; WorkManager cannot re-arm until the app runs. WorkManager *does* survive reboot, which is why no `BOOT_COMPLETED` receiver is needed (D-52). `[CITED: developer.android.com — stopped-state behaviour]`
**How to avoid:** not fixable within D-52's constraints — document it, and keep the copy honest ("reminders may arrive late") rather than promising punctuality.
**Warning signs:** none in logs; the behaviour is only observable on a device.

## Code Examples

Verified patterns from the pinned artifacts. These are the shapes the plan should reproduce — not full files.

### The Android worker (all logic delegated)

```kotlin
// shared/src/androidMain/kotlin/com/vehicare/work/ReminderWorker.kt
class ReminderWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO.limitedParallelism(1)) {
        // DatabaseProvider is initialised by VehiCareApp.onCreate — never a second instance (U2).
        val database = DatabaseProvider.require()
        val now = Clock.System.now()
        val due = evaluateDueReminders(database, now)      // pure commonMain logic, no Android types

        due.forEach { reminder ->
            // Compare-and-set: 0 rows means another evaluation already claimed this cycle (D-51).
            val claimed = database.reminderRuleQueries
                .markNotified(reminder.cycleKey, reminder.ruleId, reminder.cycleKey)
                .value
            if (claimed == 0L) return@forEach

            // Suspend resource lookup — no Context, no duplicated strings (D-11/D-65).
            val title = org.jetbrains.compose.resources.getString(
                Res.string.notification_title, reminder.vehicleName, reminder.typeLabel,
            )
            val body = ReminderCopy.body(reminder.state)   // same copy the card uses (C-40)
            postNotification(applicationContext, reminder.ruleId, reminder.vehicleId, title, body)
        }
        Result.success()
    }
}
```

`[VERIFIED: CoroutineWorker with `suspend doWork()`; `ExistingPeriodicWorkPolicy`/`Result` from work-runtime 2.12.0]` `[VERIFIED: components-resources 1.12.1 `getString(resource, vararg formatArgs)` is suspend]` `[VERIFIED: generated mutators return `QueryResult<Long>`; `.value` is the sync accessor under `generateAsync = false`]`

### The iOS scheduler and its delegate

```kotlin
// shared/src/iosMain/kotlin/com/vehicare/notifications/NotificationScheduler.ios.kt
internal const val IMMEDIATE_ID_SUFFIX: String = ":now"

@OptIn(ExperimentalForeignApi::class)
internal class IosNotificationScheduler internal constructor() : NotificationScheduler {

    private val center = UNUserNotificationCenter.currentNotificationCenter()

    override suspend fun scheduleTimeBased(due: List<ScheduledReminder>) {
        // Declarative: clear this app's own pending requests, then add exactly this list.
        val existing = suspendCancellableCoroutine { cont ->
            center.getPendingNotificationRequestsWithCompletionHandler { requests ->
                cont.resume(
                    requests.mapNotNull { (it as? UNNotificationRequest)?.identifier }
                        .filter(::isOwnIdentifier),
                )
            }
        }
        center.removePendingNotificationRequestsWithIdentifiers(existing)

        due.forEach { item ->
            val id = ReminderNotificationId.encode(item.vehicleId, item.ruleId)
            val content = UNMutableNotificationContent().apply {
                title = item.title
                body = item.body
                sound = UNNotificationSound.defaultSound()
            }
            // item.notificationDate is the occurrence minus lead_days; it is never rolled forward.
            val components = NSDateComponents().apply {
                year = item.notificationDate.year.toLong()
                month = item.notificationDate.monthNumber.toLong()
                day = item.notificationDate.dayOfMonth.toLong()
                hour = REMINDER_NOTIFICATION_HOUR.toLong()
                minute = 0
            }
            // Due-today requests are retained only while 09:00 is still ahead; never resurrect one.
            val instant = NSCalendar.currentCalendar.dateFromComponents(components)
            if (instant == null || instant.timeIntervalSinceNow <= 0) return@forEach
            val trigger = UNCalendarNotificationTrigger
                .triggerWithDateMatchingComponents(components, repeats = false)
            val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
            center.addNotificationRequest(request) { /* non-nil error: log, do not crash */ }
        }
    }

    override suspend fun triggerImmediateIfOverdue(due: List<DueReminder>) {
        due.forEach { reminder ->
            val content = UNMutableNotificationContent().apply {
                title = reminder.title
                body = reminder.body
                sound = UNNotificationSound.defaultSound()
            }
            // nil trigger = deliver now. Visible only because willPresent returns .banner (Pitfall 3).
            val request = UNNotificationRequest.requestWithIdentifier(
                ReminderNotificationId.encode(reminder.vehicleId, reminder.ruleId) + IMMEDIATE_ID_SUFFIX,
                content,
                trigger = null,
            )
            center.addNotificationRequest(request) { _ -> }
        }
    }

    // cancel(ruleIds), permissionState(), evaluateNow() and openNotificationSettings() are
    // specified member-for-member in 03-07 Task 2; the snippet shows the two delivery paths.
}

// The delegate — installed once, before Compose (U3, Pitfall D).
@OptIn(ExperimentalForeignApi::class)
internal class IosNotificationDelegate : NSObject(), UNUserNotificationCenterDelegateProtocol {

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        willPresentNotification: UNNotification,
        withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
    ) {
        // Without this, a foreground notification (all iOS mileage reminders) shows nothing.
        withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionList)
    }

    override fun userNotificationCenter(
        center: UNUserNotificationCenter,
        didReceiveNotificationResponse: UNNotificationResponse,
        withCompletionHandler: () -> Unit,
    ) {
        if (didReceiveNotificationResponse.actionIdentifier == UNNotificationDefaultActionIdentifier) {
            val identifier = didReceiveNotificationResponse.notification.request.identifier
            ReminderNotificationId.vehicleIdOf(identifier.removeSuffix(IMMEDIATE_ID_SUFFIX))
                ?.let(NotificationTapBus::publish)
        }
        withCompletionHandler()
    }
}
```

```swift
// iosApp/iosApp/iOSApp.swift  — the delegate must exist before the launch response arrives
@main
struct iOSApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate
    var body: some Scene { WindowGroup { ContentView() } }
}

final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        NotificationBridge.shared.install()   // Kotlin object, exported as `.shared`
        return true
    }
}
```

`[CITED: gh_grep — each of these Kotlin/Native call shapes appears in compiling KMP notification code; see Assumptions Log A1]`

### The pure state machine (the only place a due state is decided)

```kotlin
// shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt
sealed interface DueState {
    data class Upcoming(val kmLeft: Long?, val daysLeft: Long?) : DueState
    data class DueSoon(val kmLeft: Long?, val daysLeft: Long?) : DueState
    data object DueNow : DueState                                   // the exact-zero boundary (C-28)
    data class Overdue(val kmOver: Long?, val daysOver: Long?) : DueState
    data object NoMileageData : DueState                            // D-49: never invent an odometer
}

object ReminderStatus {
    /** null when the rule is disabled. Pure: `today` and stats are injected, never read globally. */
    fun evaluate(
        rule: ReminderRule,
        baseline: Baseline?,          // null → D-49 fallbacks
        vehicle: VehicleStats,        // latest entry date + latest mileage, may be nulls
        today: LocalDate,
    ): DueState?

    /** C-29: elapsed / interval, comparable across the km and time axes. */
    fun urgency(state: DueState, rule: ReminderRule): Double
}
```

`[VERIFIED: C-28/C-29 are the binding contract; `LocalDate` arithmetic comes from kotlinx-datetime 0.8.0]`

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `AlarmManager` + `BOOT_COMPLETED` receiver for scheduled reminders | `WorkManager` periodic + one-shot work | WorkManager 1.0 (2018) is the AndroidX recommendation for deferrable work | No exact-alarm permission, reboot handled, Doze-aware. Accepted cost: delivery can drift by hours (D-52). |
| `work-runtime-ktx` as a separate dependency | Empty artifact; use `work-runtime` | 2.9.0 | Adding the `-ktx` artifact does nothing — a silent no-op that looks correct. |
| `BGAppRefreshTask` / silent push for timed reminders | `UNCalendarNotificationTrigger` pre-scheduled by the app | iOS 10+ | Fires with the app killed, no server, no background-mode entitlement (PITFALLS § Notif 1). |
| Window functions for "latest row per group" | Single-`MAX()` bare-column aggregation | SQLite 3.18 on API 26 has no window functions | The idiom is already the project's precedent (`selectLearnedTypes`). |
| `NotificationManagerCompat` / `ContextCompat` | Platform APIs directly | minSdk 26 | One fewer dependency; the compat shims add nothing above API 26. |
| Global `startKoin` container | Composition-scoped `KoinApplication {}` | This project's Phase 1 choice | Correct for a single-`App()` UI, but it is *why* the worker cannot use Koin (U2). |

**Deprecated/outdated in this codebase's context:**
- `D-16` (home list newest-first) — superseded by `D-57` (urgency order). Any plan that keeps `ORDER BY id DESC` for the home list contradicts the current contract.
- `service_entry.service_type` as a *matching* key for reminders — it is a position-0 mirror only; the join table is authoritative.

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | The iOS UserNotifications Kotlin/Native signatures (parameter labels, `NSDateComponents` property types, protocol method names) are as shown | U3, Code Examples | MEDIUM-LOW: all were confirmed in compiling public KMP code via `gh_grep`, but Apple's documentation was unreachable this session (all search providers disabled). A wrong label is a compile error caught in the first build, not a runtime surprise — the risk is schedule churn, not correctness. |
| A2 | `androidx.work:work-runtime:2.12.0` is compatible with AGP 9.1.1 / Kotlin 2.4.20 / compileSdk 37 | U1 | LOW: its stated floors are `minSdk 24` and `compileSdk 33`; ours exceed both. If a metadata conflict appears, fall back to 2.11.x — the APIs used are unchanged across 2.9→2.12. |
| A3 | Default WorkManager initialisation (the `androidx.startup` provider) runs before `Application.onCreate`, so `WorkManager.getInstance` is safe there | U1 | LOW: the design does not actually call `getInstance` in `onCreate` — only the channel and the DB holder are initialised there. Even if the ordering differed, the first `getInstance` happens from the repository/worker, well after startup. |
| A4 | SQLite's single-`MAX()` bare-column rule behaves identically on the API 26 SQLite (3.18) | U6, Schema | LOW: the project already depends on this exact behaviour in `selectLearnedTypes`, which ships and passes its tests on the same engine. |
| A5 | `getString`/`getPluralString` are safe to call off the main thread from a worker | U9, Code Examples | LOW: they read the resource bundle through the generated accessor with no thread affinity; the only cost is first-call I/O. If a platform ever asserted, the fix is to resolve the strings before entering `withContext`. |
| A6 | DSH-01's "last service date" is delivered somewhere the UI-SPEC already defines | Open Questions | MEDIUM → **RESOLVED 2026-10-23**: the date shares the card's single status line (`Last service {date} · {sentence}`, C-30 amended); the original risk note is kept below. The UI-SPEC adds exactly one status line (C-30) and never mentions a last-service-date element. See Open Question 1 — this was a planning/product call, not something research may redesign; it was decided by the user on 2026-10-23. |
| A7 | Android's `notDetermined` vs `denied` can be derived from "at least one rule exists" without extra storage | U4 | LOW: D-54 guarantees the prompt is only ever triggered by a first rule save, so the rule count is a faithful proxy. Edge case: a user who denies, then deletes every rule, then re-adds one — the banner would offer the explanation dialog again and the OS would decline silently (harmless, no crash). |
| A8 | The 4-flow `combine` dashboard read is fast enough without pagination | U6 | LOW: N is a personal vehicle count; four indexed reads plus an in-memory sort is microseconds at any realistic N. |

## Open Questions (RESOLVED)

> All four questions below were resolved on 2026-10-23; the `RESOLVED:` line under each records the decision. The original analysis is kept verbatim as evidence.

1. **Where DSH-01's "last service date" is rendered on the home screen.**
   - What we know: the approved UI-SPEC gives the card exactly one new status line (`{type} · {sentence}`, C-30) and never specifies a last-service-date element; the data is available in the same read (`selectVehicleStats.last_service_epoch_ms`), so no extra query is needed either way.
   - What's unclear: whether the planner treats DSH-01's two facts as satisfied by the status line (next due) plus the existing Detail summary row, or expects the status line's `upcoming` form to carry the date (`Due on 12 Mar 2027`, C-43).
   - Recommendation: implement the query regardless (it is needed for D-49's fallback and for the sentence context), and let the UI-SPEC's line contract decide the rendering. **Research does not redesign the card.**
   - **RESOLVED 2026-10-23 (user-approved):** the last-service date is rendered **on the home card's existing single status line** — `Last service {date} · {due sentence}` (+ ` · +N due`), falling back to `{type} · {sentence}` when the vehicle has no service entry. DSH-01 is therefore met literally on the home screen. UI-SPEC C-30 and CONTEXT D-59 were amended to match; plan 03-06 implements it (reusing `service_summary_last` and `ServiceDates`).

2. **Whether `(vehicle_id, service_type)` should be unique.**
   - What we know: D-46 puts both intervals on one rule, which makes two rules for the same type on the same vehicle redundant.
   - What's unclear: whether a duplicate is a user error to block or a legitimate state to allow.
   - Recommendation: do **not** add a `UNIQUE` index (it is a product decision with migration consequences). Add the plain `vehicle_id` index; the editor's chip group makes accidental duplicates unlikely. If uniqueness is later wanted, it needs its own migration *and* a decision about which duplicate survives.
   - **RESOLVED 2026-10-23:** the recommendation stands — no `UNIQUE` index; `3.sqm` ships the plain `vehicle_id` index (plan 03-01).

3. **Force-stop behaviour on aggressive OEM builds.**
   - What we know: WorkManager cannot re-arm after a force-stop until the app next runs (Pitfall 10); D-52 forbids the `BOOT_COMPLETED` workaround that some apps use.
   - What's unclear: nothing technical — it is a documented limitation.
   - Recommendation: keep the copy honest and record the limitation in the phase summary; no code change.
   - **RESOLVED 2026-10-23:** accepted as a documented limitation — the phase summary records it, no code change.

4. **`UNUserNotificationCenter` provisional authorisation.**
   - What we know: iOS can grant "provisional" delivery without a prompt, which would bypass the whole C-38/C-39 explanation flow.
   - What's unclear: whether that is desirable — it conflicts with D-54's explicit "explain, then ask".
   - Recommendation: do not use it. D-54 is locked; noted only so a future reviewer does not "discover" it as an improvement.
   - **RESOLVED 2026-10-23:** not used — the explicit explanation-then-ask flow stays; the iOS requester requests alert + sound only, no provisional authorization (plan 03-08).

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| JDK | Gradle build | ✓ | 26.0.1 | — |
| Gradle wrapper | Build | ✓ | 9.5.1 (per ROADMAP) | — |
| Android SDK platform | `:shared`/`:androidApp` compile | ✓ | `android-37.0` (and `android-36.1`) installed | — |
| Android emulator + AVD | Manual verification of notifications | ✓ locally | `emulator` present, AVD `Medium_Phone` | Physical device |
| `adb` | Install/inspect on emulator | ✓ (not on `PATH`) | 1.0.41 / 37.0.1 at `~/Library/Android/sdk/platform-tools/adb` | — |
| Xcode | iOS build, `iosSimulatorArm64Test` | ✓ | 27.0 (27A266a) | — |
| iOS simulator | iOS tests + manual pass | ✓ | iPhone 18 Pro / 18 Pro Max / 17e | Physical device |
| `androidx.work:work-runtime` | Android worker | ✗ (not yet declared) | 2.12.0 to be added | none — the phase cannot deliver REM-03 on Android without it |
| Emulator in CI | Automated notification tests | ✗ | — | Accepted: delivery stays manual (U8) |

**Missing dependencies with no fallback:**
- `androidx.work:work-runtime` — must be added to the version catalog and `:shared` `androidMain` (the only install in this phase).

**Missing dependencies with fallback:**
- No emulator job in CI — manual verification on the local `Medium_Phone` AVD; the UI-SPEC's verification notes already schedule that pass.
- `adb` is not on `PATH`; scripts must use the absolute path or the plan should add it to the invocation.

## Security Domain

`security_enforcement: true`, `security_asvs_level: 1`, `security_block_on: high`. Phase 3 adds no network surface and no authentication, but it does add OS-level capabilities and a new persisted table, so the applicable set is narrow:

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | No accounts in the app. |
| V3 Session Management | no | No sessions. |
| V4 Access Control | no | Single-user, single-process local app; no cross-app surface is exposed. |
| V5 Input Validation | **yes** | Intervals and leads: non-blank ⇒ integer ≥ 1, km ≤ 6 digits, months ≤ 3 digits (C-34). Validation is a pure object applied in the ViewModel *and* re-asserted by the repository before the insert, so a future caller cannot bypass it. Service type is always a chip value, never free text (D-44). |
| V6 Cryptography | no | Nothing is encrypted at rest by design (local, non-sensitive vehicle maintenance data); no new secret is introduced. |
| V7 Error handling & logging | **yes** (ASVS L1 hygiene) | Notification failures must never crash the app or leak a stack trace into the UI. The iOS `addNotificationRequest` completion and the Android `postNotification` path swallow-with-log; the worker returns `Result.success()` after a failed post so a single bad notification cannot wedge the whole periodic job into a retry loop. |

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Notification spoofing / tap hijacking — another app posts a notification that opens VehiCare | Spoofing | Not exploitable in a meaningful way: our `MainActivity` reads only an integer extra from its own `Intent`. Do **not** add a public deep-link `intent-filter` or a custom scheme for this feature — the tap path is a `PendingIntent` to our own component, which any external app cannot forge. |
| `PendingIntent` mutability — an implicit/mutable intent lets another app rewrite the extras | Tampering | `FLAG_IMMUTABLE` on every `PendingIntent` (mandatory on API 31+), explicit `Intent(context, MainActivity::class.java)` — never an implicit intent. |
| SQL injection through the service-type string | Tampering | SQLDelight generates typed bind parameters; no string concatenation into SQL. The type string comes from a chip, not a text field. |
| Exporting the worker or the receiver | Elevation of privilege | Nothing new is exported: WorkManager's own manifest entries ship `android:exported="false"` where required, and we add no receiver/service. Keep `MainActivity` `exported="true"` only because it is the launcher. |
| Sensitive data in notification content | Information disclosure | The body names the vehicle and the service type only (D-56) — no plate, no cost, no notes. The lock-screen exposure is intentional and bounded. |
| Denial of service via unbounded notification volume | DoS | One notification per due cycle per rule (D-51); iOS pending capped at the next occurrence per rule; Android notification id = rule id so repeats replace rather than stack. |

**Highest-value security check for the verifier:** grep for `PendingIntent.get` and assert every call site passes `FLAG_IMMUTABLE`; grep for `Intent(` in `androidMain`/`:androidApp` and assert none is implicit.

## Sources

### Primary (HIGH confidence)
- `WebFetch` — `developer.android.com/topic/libraries/architecture/workmanager` and the `custom-configuration`, `define-work`, `managing-work`, `coroutineworker` sub-pages: initialisation models, `Configuration.Provider`, periodic minimums, unique work, `Result` semantics.
- `WebFetch` — `developer.android.com/jetpack/androidx/releases/work`: 2.12.0 release date, `work-runtime-ktx` being empty since 2.9.0.
- `context7` — `/websites/developer_android_topic_libraries_architecture_workmanager`: `CoroutineWorker` shape, `PeriodicWorkRequestBuilder`, `enqueueUniquePeriodicWork`.
- `context7` — `/jetbrains/compose-multiplatform`: `getString`/`getPluralString` suspend semantics, iOS lifecycle notes.
- Local artifact inspection (the strongest evidence in this document, because it is the exact pinned build input):
  - `material3-1.12.0-alpha03-sources.jar` — `SwitchDefaults.colors` parameters, `SwitchTokens.UnselectedTrackColor`, `Switch.kt:104-112` `minimumInteractiveComponentSize`.
  - CMP 1.12.1 sources — `ComposeContainerLifecycleDelegate.ios.kt`, `WindowInsetsPadding.skiko.kt:96`, `UIKitWindowInsetsManager.ios.kt:166`.
  - `lifecycle-runtime-compose:2.11.0` sources — `LifecycleEventEffect`.
  - `components-resources:1.12.1` sources — `getString`, `getPluralString`.
  - Generated SQLDelight output — `SelectSummary.last_service_epoch_ms: Long?`, `VehicleQueries` mutators returning `QueryResult<Long>`.
  - `work-runtime:2.12.0` POM — transitive `androidx.startup`, `room-runtime`, `lifecycle-*`, `tracing-ktx`; `minSdk 24`.

### Secondary (MEDIUM confidence)
- `gh_grep` code search across public KMP repositories for the Kotlin/Native UserNotifications surface (`currentNotificationCenter`, `requestAuthorizationWithOptions`, `triggerWithDateMatchingComponents`, `addNotificationRequest`, `UNUserNotificationCenterDelegateProtocol`). Compiling code is strong evidence of the API shape, but it is not the specification.
- `sqldelight.github.io` — `asFlow()` + listener semantics, migration file naming, `QueryResult` under `generateAsync = false`.
- `developer.apple.com` — `UNUserNotificationCenter.delegate` weakness, `willPresent` presentation default, pending-notification cap, calendar-trigger semantics. Referenced from training knowledge plus `PITFALLS.md`; **not** fetched this session.

### Tertiary (LOW confidence)
- None used as a basis for a decision. `WebSearch` returned "No search results found" for every query in this session (all providers disabled by `.planning/config.json`), so nothing in this document rests on it.

### Project-internal sources
- `03-CONTEXT.md` (D-41…D-65), `03-UI-SPEC.md` (C-28…C-43 and the component-verification table), `.planning/research/PITFALLS.md` §§ Notif 1–5, Android 1–4, SQL 1–5, `.planning/research/ARCHITECTURE.md` § notification scheduling flow.
- Source files quoted throughout: `AppModule.kt`, `PlatformModule.android.kt`, `DatabaseFactory.kt`, `DriverFactory.kt`/`.android.kt`/`.ios.kt`, `VehicleRepositoryImpl.kt`, `ServiceEntryRepositoryImpl.kt`, `ServiceEntry.sq`, `Vehicle.sq`, `migrations/2.sqm`, `App.kt`, `MainActivity.kt`, `MainViewController.kt`, `AndroidManifest.xml`, `shared/build.gradle.kts`, `gradle/libs.versions.toml`, `.github/workflows/ci.yml`.

## Metadata

**Confidence breakdown:**
- Standard stack — **HIGH**: one new artifact, version and POM inspected directly, `work-runtime-ktx` trap documented.
- Architecture — **HIGH** for the database/provider/seam design (it is grounded in this project's own files and a documented SQLDelight invariant); **MEDIUM** for the iOS delegate details (verified in compiling code, not Apple docs).
- Schema/migration — **HIGH**: conventions read from the existing `.sqm`/`.sq` files and generated output.
- Pitfalls — **HIGH**: each one is tied to either a verified artifact behaviour or an existing `PITFALLS.md` entry.
- UI-SPEC feasibility (U9) — **HIGH**: every claim checked against the pinned sources jars, not inferred.
- Test strategy — **HIGH** for what is automatable; the manual surface is stated explicitly rather than assumed.

**Provenance note.** All search MCP providers are disabled in `.planning/config.json` and `gsd-tools` is not on `PATH`, so the `research-plan` / `classify-confidence` / `package-legitimacy` seams could not be invoked. Every claim above is therefore tagged by hand from the source actually consulted: local pinned artifacts and `WebFetch`/`context7` of official docs are marked VERIFIED/CITED; the iOS interop surface is marked CITED-from-code and listed in the Assumptions Log.

**Research date:** 2026-10-22
**Valid until:** ~2026-11-21 for architecture and schema (stable); ~2026-11-05 for the iOS API surface and WorkManager, should a newer alpha or a Kotlin/Native interop change land.


