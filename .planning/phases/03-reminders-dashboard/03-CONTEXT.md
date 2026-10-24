# Phase 3: Reminders & Dashboard - Context

**Gathered:** 2026-10-21
**Status:** Ready for planning

<domain>
## Phase Boundary

Phase 3 delivers proactive maintenance reminders and the at-a-glance home overview:

- A per-vehicle **Reminders screen** where the user creates, edits, disables and deletes reminder rules. A rule attaches to one service type on one vehicle and may carry a mileage interval (`every X km`), a time interval (`every X months`), or both — whichever is reached first sets the due state.
- **Local notification delivery** when a rule becomes due: Android evaluates both rule kinds through scheduled background work; iOS pre-schedules time-based reminders with the OS and evaluates mileage rules on app foreground (REM-04 / SC-3). Notification permission is requested only when the user saves their first reminder (SC-2).
- The **home vehicle list becomes the dashboard**: each card carries the vehicle's most urgent next-due state, and the list is ordered by urgency rather than recency.

Cost analytics, fuel logs, export/backup, document storage, cloud sync, search/filter and notification snooze/acknowledge flows are **out of scope** — later phases or v2. The phase boundary is fixed by ROADMAP.md; this discussion clarified HOW, not WHETHER.

</domain>

<decisions>
## Implementation Decisions

### Reminder Placement & Management
- **D-41:** Reminder rules live on a **dedicated per-vehicle Reminders screen**, reached from a **new summary row on vehicle Detail** — the same slot→screen pattern as Service history (D-32). Rules are per-vehicle because intervals differ by vehicle (motorcycle vs car) and every rule is keyed by `vehicle_id` + the stored service-type string (D-40).
- **D-42:** Adding and editing follow the Phase 2 grammar: a **FAB "Add reminder"** opens a **single-screen editor** (D-30); tapping a row opens the same editor in edit mode. No expand-in-place editing, no multi-step flow.
- **D-43:** Every rule carries an **`enabled` flag with a row toggle**. Disabling silences the rule without losing its intervals — deleting remains the only destructive action and stays behind the established confirm dialog (D-35 shape).
- **D-44:** The rule's service type is chosen from the **same chip group as the service form** — the six presets then the learned custom types (D-24/D-25), never free text. A rule therefore always matches a stored type string, and D-40's "never silently rewrite the stored value" applies unchanged.
- **D-45:** The Detail summary row mirrors `ServiceSummaryRow` (D-32): it shows **reminder count + next due** and opens the Reminders screen. The row does not duplicate the list.

### Rule Shape & Baseline
- **D-46:** `reminder_rule` holds `vehicle_id`, the service-type string, `interval_km` (**nullable**) and `interval_months` (**nullable**). **Both may be set on one rule**; the rule becomes due when *either* is reached ("5,000 km OR 6 months"). At least one interval must be present.
- **D-47:** Intervals are measured from **the last service entry of the same type** — the mileage baseline is that entry's mileage, the time baseline is its service date. Not from the latest entry of any type, and not frozen at rule-creation time.
- **D-48:** **Baselines** roll forward automatically: logging a service of that type moves the baseline and clears the due state with no rule edit, no confirmation prompt and no "mark as done" step. The 30-second entry path (D-31) must not gain a reminder question.
- **D-49:** For a rule whose type has **no entry yet**, the km baseline is the vehicle's latest entry mileage (any type) and the time baseline is the **rule's creation date**. When the vehicle has no entries at all, the km component renders as **"no mileage data yet"** rather than a due figure — never an invented odometer value (research warns against odometer extrapolation).
- **D-50:** **Advance notice is per-rule and optional**: `lead_km` and `lead_days` (both nullable, empty = notify exactly at due). *This deliberately exceeds the letter of REM-01/REM-02 — user-approved during this discussion, recorded here so downstream agents do not treat it as scope creep.* A rule with a lead window enters the "due soon" state before it is actually due.

### Notification Policy & Delivery
- **D-51:** A rule **notifies once per due cycle**. After a notification is delivered, the rule stays silent until its baseline moves (a service of that type is logged) or the rule is edited. Requires an explicit once-per-cycle **claim** marker on the rule row (shape resolved in research: a cycle key compared atomically); the separate `armed_cycle_key` is D-53 bookkeeping, not a claim. Research is unambiguous that repeated nagging is the anti-feature that gets apps uninstalled.
- **D-52:** **Android delivers both rule kinds through WorkManager** — mileage evaluation (already fixed by SC-3) and time-based reminders. No `SCHEDULE_EXACT_ALARM`, no `BOOT_COMPLETED` receiver (WorkManager survives reboot by itself). Accepted trade-off: timing may drift by hours under Doze, and the UI copy must not promise exact delivery.
- **D-53:** **iOS pre-schedules time-based reminders** with `UNCalendarNotificationTrigger` (fires even when the app is killed) and **evaluates mileage rules on app foreground** via the ROADMAP's `triggerImmediateIfOverdue()` (REM-04, SC-3). Time-based delivery is exact on iOS; mileage is inherently manual because the odometer only moves when the user logs it (research Notif 5).
- **D-54:** **Notification permission is requested only when the user saves their first reminder**, preceded by a short in-app explanation, with denial non-blocking (the rule still saves). SC-2 and the STATE.md Key Decision stand: asking at first launch would burn iOS's one-time prompt before the user knows why. An initial suggestion to prompt at first launch was explicitly withdrawn after the conflict was surfaced. **Clarified 2026-10-23 (user-approved):** "first reminder" means the first successful add — at most **one automatic explanation dialog per install**; edits and later adds never re-open it, `Not now` ends the automatic ask, and the permission surfaces (tap → dialog) remain the manual way back.
- **D-55:** Tapping a notification opens **that vehicle's Detail screen** (payload = `vehicleId`) on both platforms — Detail is the hub carrying both summary rows and the path to every action. Research Notif 4 is explicit that landing on a generic home screen reads as a bug.
- **D-56:** Notification content names **the vehicle and the service type** and states the reason (due in X km / due on DATE / overdue by X km), so two vehicles' reminders are never confusable.

### Home Dashboard
- **D-57:** **The home list is ordered by urgency**, not recency: overdue first, then due soon, then upcoming by proximity, with vehicles that have no active reminder last (newest-first inside that group). **This SUPERSEDES D-16 (newest-first)** — recorded here in the same way D-26 was superseded in Phase 2, so the Phase 1 decision is not silently violated. A dashboard whose purpose is "which vehicle needs attention" cannot be ordered by when it was added.
- **D-58:** The **existing `VehicleCard` gains one status line**; no second card component is introduced for the same entity (D-14's identity-card language is preserved).
- **D-59:** The status line shows the vehicle's **single most urgent rule**, plus a **"+N due" counter** when more than one rule is due or overdue. Cards stay uniform in height and the list stays scannable. **Amended 2026-10-23 (user-approved, DSH-01):** the line leads with the last-service date — `Last service {date} · {due sentence}` — and drops the type prefix whenever that date is present, because one `Body` line cannot carry type + date + due sentence without truncating; the due sentence still belongs to the most urgent rule, whose type stays visible on the Detail summary row and the Reminders list.
- **D-60:** Three due states — **upcoming / due soon / overdue**. Only overdue uses the enforced `Danger` token, and **every state carries text** so colour is never the sole carrier of meaning (01-UI-SPEC accessibility contract).
- **D-61:** When notification permission is denied and at least one active reminder exists, **home shows a thin banner** above the list and the **Reminders screen shows the same status inline**. Tapping leads to the Reminders screen (or to system Settings when permission is permanently denied). Permission status is **re-checked on every foreground** — it is not reactive (research Notif 3). **Amended 2026-10-23 (user-approved):** the tap is two-case instead of navigating — **askable** opens the in-app explanation dialog, **denied** leaves the app for the OS notification settings. The fleet-level banner never navigates because it has no vehicle context; the Reminders screen is reached from the vehicle Detail summary row.

### Cross-Cutting (requirements, not re-discussed)
- **D-62:** All new schema ships in a **`3.sqm` migration** with a regenerated **`4.db`** baseline; both CI guards (`generateCommonMainVehiCareDatabaseSchema` + `git diff --exit-code`, and `verifyCommonMainVehiCareDatabaseMigration`) must stay green (DAT-04, D-38 precedent). New table: `reminder_rule`, keyed by `vehicle_id`.
- **D-63:** All reminder **calculation** (which rule is due, by how much, in what order) lives in `commonMain`; **delivery** is a platform seam (`expect`/`actual` `NotificationScheduler` per `.planning/research/ARCHITECTURE.md`). Android-only SDKs stay in `androidMain`, never `commonMain`.
- **D-64:** Deleting a vehicle deletes **its reminder rules** as well — an extension of the existing cascade (D-39), not a second delete path.
- **D-65:** Every new user-facing string goes through `Res.string.*` (D-11); the existing theme tokens, spacing scale and type sizes are reused unchanged (01-UI-SPEC), with Phase 3's own UI-SPEC layered on top (ROADMAP `UI hint: yes`).

### the agent's Discretion
Bounded by the requirements and the decisions above — decide these during research/planning:

- Exact `reminder_rule` column set beyond the decided ones (resolved in research: a cycle-key claim plus a separate armed bookkeeping marker), indexes, and the `created_at` representation.
- The dashboard query shape — one aggregated read per list load, no N+1 (PITFALLS § SQL 3); how "most urgent" is computed and compared across the km and time axes.
- Android WorkManager cadence (periodic daily vs. a chain of delayed one-off work), constraint set, and where the worker and channel creation live (`AndroidManifest.xml` and `Application` belong to `androidApp`, so the split between `:shared` `androidMain` and `:androidApp` is a planning decision).
- Notification channel id/name/importance (research Android 1: create once in `Application.onCreate`, before any notification) and the iOS `UNUserNotificationCenter.delegate` assignment point (research Pitfall 5: before any KMP initialization).
- Editor field layout, interval validation bounds and their messages, and whether km/month inputs are two fields or one row.
- Wording of notification title/body, the Reminders empty state, the banner copy, and the `"+N due"` phrasing.
- Whether a rule row shows its own due status inline, and the tie-break rule when two vehicles are equally urgent.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project definition & scope
- `.planning/PROJECT.md` — core value ("full service history and upcoming reminders, always accessible offline"), constraints (KMP only, local-first, minimal light), Active requirements (REM/DSH), Key Decisions table
- `.planning/REQUIREMENTS.md` — REM-01…REM-04 and DSH-01 definitions; DAT-04 migration rule; the v2 list and Out of Scope table (export, fuel log, cost analytics, documents, sync are **not** Phase 3)
- `.planning/ROADMAP.md` § Phase 3 — the user-story goal, the four success criteria (SC-1…SC-4), `**Mode:** mvp`, `UI hint: yes`, and the "Architecture Notes (from Research)" entries for the iOS reminder seam and the permission gate
- `.planning/STATE.md` — Key Decisions (iOS foreground check, permission gated to first reminder, serialized IO lane, WAL), Architecture Constraints, and Open Questions (Q2 iOS reminder UX, Q3 advance notice, Q7 Android delivery — **all three are answered by D-50/D-52/D-53 in this document**)

### Prior phase decisions & design contracts
- `.planning/phases/01-foundation-vehicle-management/01-CONTEXT.md` — carry-forwards: **D-01/D-02** km is the unit and mileage is stored as `INTEGER` (km-only in v1, no toggle UI, no unit column), **D-08** single-screen forms, **D-11** centralized English strings, **D-15** empty state = message + CTA, **D-17** bottom tab shell, **D-19** FAB placement, **D-20** simple title bar. **D-16 (newest-first list) is superseded by D-57.**
- `.planning/phases/01-foundation-vehicle-management/01-UI-SPEC.md` — binding design system: spacing scale (4/8/16/24/32/48/64 only), four type sizes (14/16/20/24sp) and two weights, the light-only palette (`Paper`/`Mist`/`Ink`/`Slate`/`Hairline`/`Danger`), the state matrix ("no state may render a blank white area"), the colour-is-never-the-only-carrier accessibility rule, and the copywriting contract
- `.planning/phases/01-foundation-vehicle-management/01-PATTERNS.md` — pattern map and binding conventions (serialized IO lane, mapper boundary, typed bind parameters, migration enforcement)
- `.planning/phases/02-service-log/02-CONTEXT.md` — **D-23/D-24/D-25/D-26** the six presets + learned custom types and the wrapping chip group (D-44 reuses it), **D-30** single-screen form with the submit at the bottom, **D-32/D-33/D-34/D-35/D-36** the slot→screen, card grammar, sort control, read-only detail and FAB patterns D-41/D-42/D-45 mirror, **D-38** migration + regenerated baseline, **D-39** vehicle-delete cascade, **D-40** service type is a stable string that reminder rules will key on
- `.planning/phases/02-service-log/02-UI-SPEC.md` — Phase 2's design contract: card anatomy, multi-select chip group, `serviceMetaLine` copy rules, destructive-action placement
- `.planning/phases/02-service-log/02-PATTERNS.md` — the concrete file/pattern map Phase 3 should extend (repository + mapper + ViewModel + screen layering)

### Research
- `.planning/research/PITFALLS.md` § Pitfall 5 — iOS notification permission is one-time and irreversible; explanation before the prompt; delegate set before KMP init
- `.planning/research/PITFALLS.md` § Android 1–4 — channel before any notification, `POST_NOTIFICATIONS` on API 33+, exact-alarm permission cost, alarms lost on reboot
- `.planning/research/PITFALLS.md` § Notif 1–5 — never `BGAppRefreshTask` for timed reminders, the 64-pending-notification cap (schedule only the next due per rule), permission state is not reactive, deep-link payloads, and mileage reminders are computed not scheduled
- `.planning/research/PITFALLS.md` § SQL 2 — dates and mileage need enforced unit conventions; § SQL 3 — no N+1 query when the list needs each vehicle's next due
- `.planning/research/ARCHITECTURE.md` § notification scheduling flow — the expected `NotificationScheduler` `expect`/`actual` seam (`scheduleTimeBased()` + `triggerImmediateIfOverdue()`), and the write/read data flow the Reminders screen must follow
- `.planning/research/FEATURES.md` § Table Stakes — mileage + date reminders and push notifications are P0; § priority table — dual triggers and the multi-vehicle dashboard are high value, advance notice is P1 (D-50); § Anti-Features — aggressive notification batching, odometer extrapolation

No external specs or ADRs exist — requirements and decisions above are complete.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `shared/src/commonMain/kotlin/com/vehicare/ui/service/components/ServiceSummaryRow.kt` and `ServiceSummaryViewModel.kt` with `shared/src/commonMain/kotlin/com/vehicare/domain/ServiceSummary.kt` — the exact shape D-45 mirrors: an aggregate read (count + newest date) driving a tappable summary row without loading the list.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/VehicleCard.kt` — the card D-58 extends; its `vehicleMetaLine` helper and merged-semantics pattern show how to add one text line without breaking the single-node card announcement.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/ConfirmDeleteDialog.kt` — generalized in Phase 2 (title/message/confirmLabel parameters), so rule deletion reuses it with no new dialog.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/components/EmptyState.kt` — the parameterized message + CTA block for the Reminders empty state (D-15).
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleEditScreen.kt` + `VehicleFormValidation.kt` — the single-screen form + pure-validation-function pattern the reminder editor should follow (validation stays a testable pure Kotlin object, not inline composable logic).
- `shared/src/commonMain/kotlin/com/vehicare/ui/theme/Color.kt` / `Type.kt` — `Danger` is the only destructive token; D-60 reuses it and invents no colour.
- `shared/src/commonMain/kotlin/com/vehicare/util/RupiahFormat.kt` (Phase 2) — the precedent for a single pure formatting helper unit-tested in `commonTest`; the mileage-and-date status line (D-56/D-60) needs the same treatment so Android and iOS cannot drift.
- `shared/src/commonMain/composeResources/values/strings.xml` — the centralized string set; every new string lands here (D-11/D-65).

### Established Patterns
- **Repository boundary:** interface + impl + mapper between SQLDelight rows and domain models; the UI never sees generated types or a driver.
- **Serialized DB lane:** every read and write goes through `Dispatchers.IO.limitedParallelism(1)`; writes use `database.transactionWithResult`; insert ids via `lastInsertId()` (no `RETURNING` at minSdk 26).
- **Reactive reads:** `query.asFlow().mapToList(ioDispatcher)` → `Flow` → ViewModel `stateIn(WhileSubscribed(5_000))`, with explicit Loading / Empty / Content / Error states and no blank screen.
- **One-shot failure flags:** a `saveFailed`-style boolean is retired after the snackbar shows, so recomposition cannot replay it.
- **Navigation:** typed `@Serializable` route classes in `ui/navigation/Routes.kt`, `App.kt` as the only nav graph, bottom tabs rendered only on tab roots (`isTabRoot`), pushed screens carrying their own title bar.
- **DI:** `di/AppModule.kt` registers repositories and ViewModels; route parameters arrive via `parametersOf(id)` and are destructured in the `viewModel { (id: Long) -> … }` factory.
- **Platform seams:** only three `expect`/`actual` pairs exist today (`Platform.kt`, `AppVersion.kt`, `data/db/DriverFactory.kt`) — a fourth (`NotificationScheduler`) is the established way to add platform delivery, and no notification code exists yet anywhere in the tree.
- **Tests:** mapper round-trips and pure-helper tests in `commonTest`; real-driver and real-filesystem assertions live in `iosTest` (see `PhotoStorageTest`, `ServiceEntryDeletionTest`) — the migration and cascade work in this phase needs the same treatment.

### Integration Points
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleDetailScreen.kt` — the `ServiceSummaryRow` call site (inside `VehicleDetailContent`) is where the reminder summary row joins; its `summaryViewModel` shows the Koin-injected aggregate-read pattern.
- `shared/src/commonMain/kotlin/com/vehicare/ui/vehicles/VehicleListScreen.kt` + `VehicleListViewModel.kt` — the home list whose ordering (D-57) and card status line (D-58/D-59/D-60) change; this is also where the permission banner (D-61) mounts.
- `shared/src/commonMain/kotlin/com/vehicare/ui/navigation/Routes.kt` — add the per-vehicle Reminders route (and the editor route) here as typed classes.
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` — add the `composable<…>` entries; keep the single `NavHost` and the bottom-bar scoping untouched.
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt` — register `ReminderRepository`, the ViewModels, and the platform `NotificationScheduler` binding (Android needs the application `Context`, which already arrives through the `platformModule` factory parameter).
- `shared/src/commonMain/sqldelight/com/vehicare/db/` — new `ReminderRule.sq` plus `migrations/3.sqm`, then regenerate the committed baseline to `4.db` (D-62).
- `shared/src/commonMain/kotlin/com/vehicare/data/VehicleRepositoryImpl.kt` — `delete(id)` already deletes photos, parts and entries in one transaction; reminder rules join that cascade (D-64).
- `androidApp/src/main/AndroidManifest.xml` — currently declares **no permissions**; `POST_NOTIFICATIONS` (and the notification channel/receiver wiring the worker needs) lands here, not in `:shared`.
- `iosApp/iosApp/iOSApp.swift` (`@main struct iOSApp`) + `ContentView.swift` — the entire iOS host today is these two files with no `AppDelegate`; the notification delegate and permission plumbing (research Pitfall 5 point 4) enter here.

</code_context>

<specifics>
## Specific Ideas

- **A reminder is a rule the user sets once and forgets** — hence D-48's automatic roll-forward: the fast capture path (D-31) must never grow a reminder question, and a logged service silently resets the clock.
- **"Whichever comes first" is the mental model**, not two separate reminders: one row per service type, both intervals on it (D-46).
- **Quiet by default**: one notification per due cycle (D-51), no repeats, because the research is explicit that repeated nagging is what makes users turn notifications off entirely.
- **The dashboard answers one question** — "which vehicle needs attention?" — which is why it sorts by urgency (D-57) and why each card shows exactly one status line (D-59) rather than a mini report.
- **Honesty about timing**: because Android reminders ride WorkManager (D-52), the copy must not promise to-the-minute delivery — a reminder that arrives two hours late is fine; one that promises punctuality and misses is a trust break.
- **Never invent an odometer value** (D-49): when there is no mileage data, the app says so instead of estimating one.

</specifics>

<deferred>
## Deferred Ideas

- **Snooze / acknowledge directly from the notification** ("mark as done" without opening the app) — research rates it P1; it needs a notification-action surface on both platforms. Own future phase.
- **Predicted due date from mileage rate** (average km/day projected forward) — research P2, and it needs ≥2 entries per type to be meaningful.
- **Reminder rules for document expiry** (insurance, registration, tax) — DOC-01 territory, v2.
- **Per-rule notification sound / custom channel per service type** — not needed for v1; one channel keeps the settings surface small.
- **Reminder history log** (what was sent, when, and whether it was opened) — no user-facing need in v1; revisit with any future analytics work.
- **Quiet hours / do-not-disturb window** — plausible later refinement of D-51, deliberately not in this phase.
- **Home-screen widget showing next due** — no requirement behind it; would be a new platform surface.

None of these block Phase 3 scope. The `reminder_rule` schema (D-46) leaves room for the snooze and history items without a further migration if they are ever scheduled.

</deferred>

---

*Phase: 03-reminders-dashboard*
*Context gathered: 2026-10-21*
