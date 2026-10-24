---
quick_id: 261024-u9g
description: "rebuild bagian reminder service. Yang saya maksud, reminder bisa muncul dalam beberapa bulan berikutnya dengan menghitung bulan terakhir kali servis. Pengaturan interval bulan dapat diubah ubah pada settingan dan berlaku universal (untuk semua jenis kendaraan)."
status: in-progress
created: 2026-10-24
depends_on: []
files_modified:
  - shared/src/commonMain/kotlin/com/vehicare/data/settings/SettingsStore.kt
  - shared/src/androidMain/kotlin/com/vehicare/data/settings/SettingsStore.android.kt
  - shared/src/iosMain/kotlin/com/vehicare/data/settings/SettingsStore.ios.kt
  - androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt
  - shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt
  - shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt
  - shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt
  - shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationScheduler.kt
  - shared/src/commonMain/kotlin/com/vehicare/notifications/ReminderDelivery.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderFormValidation.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditViewModel.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditScreen.kt
  - shared/src/commonMain/kotlin/com/vehicare/ui/settings/SettingsScreen.kt
  - shared/src/commonMain/composeResources/values/strings.xml
  - .planning/phases/03-reminders-dashboard/03-UAT.md
autonomous: true
---

# Plan: Global service-interval (months) setting + scheduler rebuild

## Objective

The user's story: "Saya servis mobil di bulan Maret, lalu reminder otomatis menentukan kapan
servis selanjutnya = bulan Maret + jumlah bulan interval, dan tersimpan otomatis." The interval
is one app-level setting, editable in Settings, and universally applied to every reminder rule
(across all vehicles and service types). Locked decisions:

- **D-1 (global override):** A new app-level "service interval (months)" setting is stored once
  via a minimal expect/actual `SettingsStore` (SharedPreferences on Android, `NSUserDefaults` on
  iOS — **no SQLDelight migration**). When it is set, it overrides every rule's `intervalMonths`
  for evaluation, due-date arithmetic and cycle-key derivation. When it has never been set,
  rules fall back to their own `intervalMonths` (existing behavior). The existing D-47
  `TypeBaseline` machinery is the ONE baseline — do not invent a second one.
- **D-2 (reschedule-only):** No auto-creation of rules after logging a service entry. The
  existing machinery (baseline auto-shifts to the newest entry of that type via
  `ServiceEntry.sq`'s `selectTypeBaselines`, next due recomputed with the effective interval,
  delivered once per cycle via D-51 claim) already IS the user's story. Verify it end-to-end
  with a test: March entry → baseline = March → due = March + global months → one notification.
- **D-3 (strip the editor):** `ReminderFormValidation`, `ReminderEditViewModel` and
  `ReminderEditScreen` no longer offer a months field. The km axis and the per-rule lead
  windows (`leadKm`, `leadDays`) stay exactly as today. Because C-35 ("a rule needs at least one
  interval") must keep holding, the DISTANCE km field becomes **mandatory** in the editor.
  Do NOT change the stored `ReminderRule` shape, the schema, or the rule-row interval display
  (`ReminderCopy.intervalSentence` keeps rendering stored values — legacy months-only rows keep
  working through the fallback).

Key design constant to thread everywhere the months interval is used:
`effectiveMonths = globalSetting ?: rule.intervalMonths` — evaluation (`ReminderStatus`), due
arithmetic (`ReminderSchedule`), cycle keys (must include the **effective** months so a
setting change naturally starts a new cycle and re-arms), time-path ownership
(`ReminderDelivery.timePathOwnedRuleIds`) and eligibility filters all consume it.

UI language: strings.xml is English — all new copy is English.

## Tasks

### Task 1 — SettingsStore (expect/actual) + provider + wiring

`<files>`:
- `shared/src/commonMain/kotlin/com/vehicare/data/settings/SettingsStore.kt` (new)
- `shared/src/androidMain/kotlin/com/vehicare/data/settings/SettingsStore.android.kt` (new)
- `shared/src/iosMain/kotlin/com/vehicare/data/settings/SettingsStore.ios.kt` (new)
- `androidApp/src/main/kotlin/com/vehicare/VehiCareApp.kt`
- `shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt`
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt`

`<action>`:
- In commonMain `com.vehicare.data.settings`, define:
  - `interface SettingsStore { fun readIntervalMonths(): Long?; fun setIntervalMonths(months: Long?); fun observeIntervalMonths(): Flow<Long?> }`. `null` means "the global interval has never been set" (fall back to per-rule `intervalMonths` per D-1). Back `observe` with a `StateFlow` kept in sync on set/delete so the Settings screen updates without a manual refresh.
  - `object SettingsProvider` — an exact clone of `DatabaseProvider`'s `initialize`/`require` contract (double-`initialize` throws; `require` fails with an actionable error). This holder exists for the same reason DatabaseProvider does: `ReminderDeliveryFactory` (Task 2) and the Android worker build outside the Koin graph and cannot read a Koin binding.
  - `expect fun createSettingsStore(platformContext: Any? = null): SettingsStore` — same `Any?` contract as `createNotificationScheduler`.
- Android actual (`SharedPreferences`): key `reminder_interval_months` stored as a `Long`; setting `null` removes the key (use `apply()` writes; reads are sync). Document that it uses the **application** context like `NotificationPermissionStore.android.kt` does — never an Activity.
- iOS actual: `NSUserDefaults.standardUserDefaults` with the same key, mirroring `NotificationPermissionStore.ios.kt` structure.
- Entry points: in `VehiCareApp.onCreate` call `SettingsProvider.initialize(createSettingsStore(applicationContext))` right after `DatabaseProvider.initialize`; in `MainViewController` do the same before `ComposeUIViewController`. Follow the existing "exactly once per process" comments.
- Koin: in `appModule` add `single<SettingsStore> { SettingsProvider.require() }` with a comment noting the binding exists for ViewModels; delivery paths read the provider directly.

`<verify>`: `./gradlew :shared:testAndroidHostTest` (compiles common + Android-host; the expect/actual pair is covered by the iOS test task in Task 4's full gate).

`<done when>`: `SettingsStore` compiles on both targets, both entry points initialize it exactly once, and `appModule` exposes the binding. No settings UI yet.

### Task 2 — Thread `globalIntervalMonths` through evaluation, scheduling and delivery

`<files>`:
- `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt`
- `shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationScheduler.kt`
- `shared/src/commonMain/kotlin/com/vehicare/notifications/ReminderDelivery.kt`
- `shared/src/commonTest/kotlin/com/vehicare/domain/ReminderStatusTest.kt`
- `shared/src/commonTest/kotlin/com/vehicare/notifications/ReminderScheduleTest.kt`
- `shared/src/commonTest/kotlin/com/vehicare/notifications/ReminderDeliveryTest.kt`

`<action>`:
- `ReminderStatus`: add `globalIntervalMonths: Long? = null` parameter to `evaluateAll` and `evaluate`. Compute `val effectiveMonths = globalIntervalMonths ?: rule.intervalMonths` once and use it (not `rule.intervalMonths`) in: the C-34 range guard (`require(effectiveMonths == null || effectiveMonths in 1..MAX_INTERVAL_MONTHS)`), `timeReading`, the TIME eligibility test, and **`cycleKey`** — give `cycleKey` an `effectiveMonths: Long?` parameter (keep the existing `-1` sentinel for null) and call it with the effective value. Preserve the existing doc-comment contract and add one line: when the global setting is set it replaces `intervalMonths` for both arithmetic and the cycle key (D-1's re-arm guarantee).
- `ReminderSchedule` (lives in `NotificationScheduler.kt`): add `globalIntervalMonths: Long? = null` to `currentOccurrence` and `notificationDate`; inside, `val intervalMonths = globalIntervalMonths ?: rule.intervalMonths ?: return null`. Update the doc comments ("the interval is the effective one: global setting when set, else the rule's").
- `ReminderDelivery`: add constructor param `private val globalIntervalMonths: () -> Long? = { null }`. In `evaluateAndDeliver` read it ONCE into a local and thread it through: `ReminderStatus.evaluateAll`, both `ReminderSchedule.currentOccurrence` / `notificationDate` call sites, and `timePathOwnedRuleIds` (add a `globalIntervalMonths: Long?` param there; its filter becomes "effective months != null" — note that once the global setting is set, EVERY enabled rule gains a time axis, which is exactly the universality D-1 asks for). `ReminderDeliveryFactory.create` builds `{ SettingsProvider.require().readIntervalMonths() }` as the provider (keep it a lazily-read lambda so each sweep picks up a fresh value).
- Tests (`ReminderStatusTest`, `ReminderScheduleTest`, `ReminderDeliveryTest`): use the tdd loop. Required behaviors:
  1. Global set → a km-only rule (or months-null rule) with a March baseline evaluates time-due at March + N, and its `cycleKey` contains the global N.
  2. Global set → same rule whose `intervalMonths` differs gets the GLOBAL value (override, not max/min).
  3. Global null → existing per-rule behavior is byte-for-byte unchanged (existing tests keep passing unmodified).
  4. Cycle-change re-arm: rule previously notified under global N=6, sweep again with global N=3 → `notifiedCycleKey` no longer matches the new key → the cycle is delivered again (never re-claimed twice for the same key; assert via the fake repository's `markNotified` counts).
  5. The user story end-to-end (D-2, reschedule-only, NO rule creation): rule exists + already-notified earlier cycle, then a NEW service entry for that type is inserted through the real in-memory repository → the baseline shifts to the March entry → next sweep posts exactly one notification whose next due is March + effective months, and no row was created. Assert `observeAllRules()` row count is unchanged.
- Do NOT touch `ReminderCopy`, `ReminderOrdering` or rule-row display (scope per D-3 note above).

`<verify>`: `./gradlew :shared:testAndroidHostTest` — all five new/updated behavior cases green and no pre-existing test regressed.

`<done when>`: evaluation, due arithmetic and cycle keys all honor `globalSetting ?: rule.intervalMonths`; the March-story test passes end-to-end; legacy behavior is provably unchanged when the setting is null.

### Task 3 — Strip the months field from the reminder editor

`<files>`:
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderFormValidation.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditViewModel.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/ReminderEditScreen.kt`
- `shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderFormValidationTest.kt`
- `shared/src/commonTest/kotlin/com/vehicare/ui/reminders/ReminderEditViewModelTest.kt`

`<action>`:
- `ReminderFormValidation`: remove `intervalMonths` from `ReminderFormValues` and `ReminderFormField.INTERVAL_MONTHS` from the enum; the OK/invalid logic now requires a **valid non-blank `intervalKm`** (line 111's combined test collapses to km-only — keep `isInvalidCount`). Delete the months branch of `ReminderResults.map`'s month parse (line 151). Strictly do NOT touch `leadKm` / `leadDays` handling.
- `ReminderEditViewModel`: drop the `INTERVAL_MONTHS` field-change case (line 192) and stop seeding `intervalMonths` from the loaded rule (line 162) — the repository still stores `intervalMonths = null` for newly edited rows; legacy loaded months values are intentionally NOT rewritten (no schema or repository change).
- `ReminderEditScreen`: remove the Time (`field_interval_time`) interval field row and its `INTERVAL_MONTHS` error/supporting-text wiring (lines ~350–364); keep the Distance field and its `field_interval_distance` copy; keep the page's `section_intervals` header for the km + lead fields.
- Tests: `ReminderFormValidationTest` — months cases deleted; new cases: blank km + blank months → INVALID with the km error; km 1234 → OK (months input is no longer a thing). `ReminderEditViewModelTest` — field-change for months assertions removed; saving an edit stores `intervalKm` and `intervalMonths = null`. Do not adjust other ViewModels' tests.

`<verify>`: `./gradlew :shared:testAndroidHostTest` — editor tests green.

`<done when>`: no months field exists anywhere in the reminder editor; km is mandatory; validation/VM tests reflect it; legacy schema untouched.

### Task 4 — Settings UI: interval field + save (+ re-arm), UAT note, full gates

`<files>`:
- `shared/src/commonMain/kotlin/com/vehicare/ui/settings/SettingsViewModel.kt` (new)
- `shared/src/commonMain/kotlin/com/vehicare/ui/settings/SettingsScreen.kt`
- `shared/src/commonMain/composeResources/values/strings.xml`
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt`
- `shared/src/commonTest/kotlin/com/vehicare/ui/settings/SettingsViewModelTest.kt` (new)
- `.planning/phases/03-reminders-dashboard/03-UAT.md`

`<action>`:
- `strings.xml` (English, matching file conventions): `settings_service_interval_label` ("Service interval"), `settings_service_interval_field` ("Months"), `settings_service_interval_hint` ("Not set — each reminder uses its own interval until you set a universal one here."), `settings_service_interval_save` ("Save"), `settings_service_interval_saved` ("Saved"), `error_interval_months_universal` ("Enter 1 or more, or leave blank.") — exactly six strings, wired via `stringResource`.
- `SettingsViewModel` (Koin `viewModel { SettingsViewModel(get(), get<NotificationScheduler>()) }` in `appModule`): exposes `val intervalText: StateFlow<String>` (loaded from `SettingsStore.observeIntervalMonths()`, blank when null) and `fun save(text: String)`. `save` parses: blank → set `null` (per-rule fallback restored); else require `1..1200` (C-34 bound, same rule as the old editor cap) → write through `SettingsStore.setIntervalMonths`, then fire `scheduler.evaluateNow()` (fire-and-forget, same seam the repository hooks use) so iOS re-derives declarations for the new cycle and Android re-sweeps — with the new key every previously armed/notified cycle re-arms per Task 2 case 4. Invalid input exposes an error state; invalid saves never write.
- `SettingsScreen`: add a clearly separated section BELOW the App-version row: label + numeric `OutlinedTextField` (single line, digits-only sanitization), the hint line shown when the field is blank, Save button that shows the `settings_service_interval_saved` confirmation after a successful write. Follow the existing scaffold/topBar/padding patterns; no bottom-tab changes (a Settings tab already exists — the codebase-map note "no Settings screen exists" is stale: `SettingsRoute` + `ui/settings/SettingsScreen.kt` exist as the D-17 placeholder).
- `SettingsViewModelTest` with a fake `SettingsStore` and fake `NotificationScheduler`: save("6") → store set 6 + `evaluateNow()` called once; save("") → set null + `evaluateNow`; save("0") → no write, no `evaluateNow`, error state; initial blank loads hint state.
- Append (do NOT modify results) a dated note at the END of `.planning/phases/03-reminders-dashboard/03-UAT.md`: Tests 1/2 expectations that involve per-rule month intervals are superseded by the universal setting from quick task 261024-u9g; month timing now comes from the Settings value (fallback: the rule's own interval when the setting was never set).
- Final full verification — all three gates green in this order:
  1. `./gradlew :androidApp:assembleDebug`
  2. `./gradlew :shared:testAndroidHostTest`
  3. `./gradlew :shared:iosSimulatorArm64Test`
- Commit each task atomically (local only, no push).

`<verify>`: the three gate commands above, plus the new `SettingsViewModelTest` inside gate 2.

`<done when>`: user can set/change/delete a universal month interval in Settings; changing it re-arms delivery on both platforms; editor has no months field; all three gates green; UAT note recorded; local commits exist per task.

## Sequencing

Task 1 → Task 2 → Task 3 → Task 4, strictly sequential (single concern chain, tasks 2 and 3 both grep-touch pieces of the same files Task 1 registers). No checkpoints: `workflow.human_verify_mode` is end-of-phase and this is a quick task — the three gradle gates are the acceptance proof.
