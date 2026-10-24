---
quick_id: 261024-u9g
description: "rebuild bagian reminder service"
status: complete
date: 2026-10-24
commit: 38a66f1
---

# Quick Task 261024-u9g: Rebuild bagian reminder service — Summary

One app-level "Service interval (months)" setting, stored outside SQLDelight, that overrides every
reminder rule's per-rule month interval for evaluation, due-date arithmetic and cycle keys —
plus the reminder editor's months field removed entirely. Reschedule-only: no rule is ever
auto-created after logging a service; the existing baseline machinery already is the user's story.

## What changed

- **Task 1 — SettingsStore (expect/actual) + provider + wiring:** `SettingsStore` interface
  (read/set/observe a nullable `Long?`, `null` = never set → per-rule fallback), backed by
  `SharedPreferences` on Android (application context, key `reminder_interval_months`) and
  `NSUserDefaults` on iOS (`vehicare.settings.reminder_interval_months`, `integerForKey`/`setInteger`).
  `SettingsProvider` clones `DatabaseProvider`'s `initialize`/`require` contract; both platform entry
  points (`VehiCareApp.onCreate`, `MainViewController`) initialize exactly once; Koin
  `single<SettingsStore>` exists for ViewModels while delivery paths read the provider directly.
- **Task 2 — threading the effective interval:** `ReminderStatus.evaluate/evaluateAll` gained
  `globalIntervalMonths`; `effectiveMonths = globalSetting ?: rule.intervalMonths` drives the
  C-34 guard, `timeReading` and `cycleKey(effectiveMonths = …)` — so a setting change produces new
  cycle keys and re-arms every previously notified cycle. `ReminderSchedule.currentOccurrence` and
  `notificationDate` accept the override (months-null rules gain a time axis). `ReminderDelivery`
  reads the setting lazily per sweep via `ReminderDeliveryFactory` → `SettingsProvider.require()`,
  threads it through evaluation, both schedule call sites, and the time-path eligibility filters.
- **Task 3 — editor strip:** `ReminderFormValues`/`ReminderFormField` lost the months member; the
  km field is now the mandatory interval gate (C-35 collapses to km-only); `toRule` always writes
  `intervalMonths = null`; the editor screen dropped the Time row; `sanitizeMonths` survives only
  as the "days before" filter.
- **Task 4 — Settings UI:** `SettingsViewModel` (live field state via `stateIn`, save validates
  blank → clear else 1..1200, invalid saves never write, then fires `scheduler.evaluateNow()` —
  the same fire-and-forget seam the repository hooks use), a new "Service interval" section in
  `SettingsScreen` below the App-version row (numeric field, blank-state hint, Save + "Saved"
  confirmation + invalid error line), six new English strings, Koin `viewModel` binding.

## Key files

- `shared/src/commonMain/kotlin/com/vehicare/data/settings/SettingsStore.kt` (+ android/ios actuals, new)
- `shared/src/commonMain/kotlin/com/vehicare/domain/ReminderStatus.kt`
- `shared/src/commonMain/kotlin/com/vehicare/notifications/NotificationScheduler.kt` (`ReminderSchedule`)
- `shared/src/commonMain/kotlin/com/vehicare/notifications/ReminderDelivery.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/reminders/{ReminderFormValidation,ReminderEditViewModel,ReminderEditScreen}.kt`
- `shared/src/commonMain/kotlin/com/vehicare/ui/settings/{SettingsViewModel,SettingsScreen}.kt`
- `shared/src/commonMain/kotlin/com/vehicare/di/AppModule.kt`; `androidApp/.../VehiCareApp.kt`; `shared/src/iosMain/.../MainViewController.kt`
- `shared/src/commonMain/composeResources/values/strings.xml` (−2 orphaned months-editor strings, +6 settings strings, hint_intervals copy)
- Tests: `ReminderStatusTest` (+6), `ReminderScheduleTest` (+4), `ReminderDeliveryTest` (+3,
  incl. the March-entry user story and setting-change re-arm), `ReminderFormValidationTest` (+2 net),
  `ReminderEditViewModelTest`, `SettingsViewModelTest` (new, 9).

## Verification results

- `./gradlew :androidApp:assembleDebug` — **BUILD SUCCESSFUL**
- `./gradlew :shared:testAndroidHostTest` — **278 tests, 6 skipped (pre-existing `@IgnoreOnAndroidHost`
  JVM-host skip set), 0 failures** (254/2026 baseline was 256; net +22 with the new suites)
- `./gradlew :shared:iosSimulatorArm64Test` — **340 tests, 0 failures, 0 skipped** — includes the
  delivery post-path tests the JVM host cannot run (Compose Resources `getString`)

## Deviations from the plan

1. **[Registered before Task 2] UI evaluation surfaces consume the global interval.** Plan Task 2
   listed only `ReminderStatus`/`ReminderSchedule`/`ReminderDelivery`; the three lists' ViewModels
   (`VehicleListViewModel`, `RemindersViewModel`, `ReminderSummaryViewModel`) would have kept
   evaluating with per-rule months and contradicted the notifications. They now combine
   `SettingsProvider.observeIntervalMonthsOrDefault()` (a non-throwing, reactive read that degrades
   to a plain `null` flow when the provider was never initialized — zero test breakage).
2. **[Rule 2] `ReminderFormValidation.hint_intervals` copy updated.** The old sentence described a
   dual-axis editor that no longer exists ("whichever is reached first"); it now points the monthly
   choice at Settings.
3. **[Rule 3] The D-2 test's "real in-memory repository" is a hand-written in-memory fake**
   (`InMemoryServiceDataRepository` in `ReminderDeliveryTest`) that reproduces
   `ServiceEntry.sq`'s `selectTypeBaselines` semantics (newest entry per vehicle/case-insensitive
   type) with a real insertion path — the suite has no JDBC/native SQLDelight test driver, and
   adding one would be a new build dependency outside this quick task.
4. **[Rule 3] Two orphaned editor strings removed** (`field_interval_time`, `field_suffix_months`)
   after their only consumers disappeared, and the iOS actual needed Kotlin/Native's
   `integerForKey`/`setInteger` names rather than `longForKey`/`setLong`.
5. **Editor save semantics detail:** an edit of a legacy months-only now-rule seeds the km field
   empty and (correctly) save refuses until km is filled — the supported intent (D-3's
   "km stays per-rule, and is now mandatory"). Legacy rows' stored months keep rendering in the
   rule rows through `ReminderCopy.intervalSentence`.

Locked decisions held: D-1 global override everywhere the effective months matter; D-2
reschedule-only (verified: the March-entry sweep posts exactly one notification, due March + N,
`observeAllRules` count unchanged); D-3 months stripped from the editor, stable stored schema.

## Threat surface

No new network surface, auth path or schema change: the setting is local per-platform service
storage (`SharedPreferences`/`NSUserDefaults`), written only through the Settings UI, read by the
delivery paths, and never leaves the device.
