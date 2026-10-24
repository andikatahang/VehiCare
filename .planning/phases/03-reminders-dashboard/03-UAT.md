---
status: testing
phase: 03-reminders-dashboard
source: [03-VERIFICATION.md]
started: 2026-10-24T13:25:00+07:00
updated: 2026-10-24T13:25:00+07:00
---

## Current Test

number: 1
name: Notification delivery end-to-end on both platforms
expected: |
  Each of 03-07-PLAN.md's 19 numbered `<human-check>` steps (groups A–E) matches the
  expectation recorded in that plan: one notification per due cycle then silence,
  re-notification after the baseline moves, tap routing from both a cold and a warm
  start, the iOS foreground banner (proving the delegate is retained), time-based
  pre-scheduling, and dual-axis cross-path exclusivity. Where a step cannot be
  performed (e.g. a simulator clock override), record which step and what was
  observed instead.
awaiting: user response

## Tests

### 1. Notification delivery end-to-end on both platforms

Android emulator (API 34) + booted iOS simulator. Install the debug build, grant
notifications, add a vehicle, log a service entry, add a mileage reminder whose
interval is already exceeded, then background and reopen.

expected: Exactly one OS notification titled `{vehicle} · {service type}` with the overdue sentence as its body. Backgrounding and reopening twice more produces no second notification. Logging a newer entry of the same type and reopening produces a new notification. Then follow 03-07-PLAN.md `<human-check>` groups A–E (19 steps).
covers: SC2, SC3
result: [pending]

### 2. Notification permission flow on both platforms

Clean installs on both platforms. Follow 03-08-PLAN.md `<human-check>` groups A–D
(15 numbered steps).

expected: No ask on cold launch or on entering the Reminders screen; the dialog appears only after the first successful reminder save; `Not now` spends the automatic ask so later adds *and* edits stay silent; the denied variant shows the denied copy and its settings deep link. Specifically steps 6 and 13: enabling notifications in system settings clears both the home banner and the Reminders inline row immediately on return, with no relaunch. No surface appears when there are zero enabled rules.
covers: SC2 (permission-timing half, as experienced by a user)
result: [pending]

### 3. Visual verification against 03-UI-SPEC.md

Reminders screen, reminder editor, Vehicle Detail Reminders summary row, home
dashboard status line, and the permission surface.

expected: Urgency ordering is visible; each card's status line is one line with `maxLines = 1` truncating gracefully; overdue and due-now sentences render in `Danger` and the rest in `Slate`; the summary row sits 24dp below Service history; no card changes height when a status line is added.
covers: UI-SPEC conformance (no Compose UI test target exists in this project)
result: [pending]

### 4. Dashboard copy format for the three DSH-01 cases

A vehicle with a service entry and an active reminder; a vehicle without a service
entry; a vehicle with no enabled rule.

expected: `Last service {date} · {due sentence}` for the first; `{type} · {sentence}` for the second; `Last service {date} · No active reminders` for the third, with the date segment omitted entirely when there is no entry. ` · +N due` is appended only when another enabled rule is due or overdue.
covers: DSH-01 rendered output (`vehicleStatusLine` is a private @Composable with no unit-test coverage)
result: [pending]

## Summary

total: 4
passed: 0
issues: 0
pending: 4
skipped: 0
blocked: 0

## Gaps

## Notes

Automated verification is already green on this commit and is **not** part of this UAT:
`:shared:testAndroidHostTest` 256 tests / 0 failures, `:shared:iosSimulatorArm64Test`
318 tests / 0 failures, `:shared:compileKotlinIosSimulatorArm64`,
`:androidApp:assembleDebug`, and `:shared:verifyCommonMainVehiCareDatabaseMigration`
all pass. Success criteria 1 and 4 are fully verified statically; this file covers
only what needs a real OS.

Why these four need a human: the final hop of SC2/SC3 is platform code with no test
coverage and no device observation — `NotificationManagerCompat.notify` on Android,
`UNUserNotificationCenter.addNotificationRequest` and the retained
`UNUserNotificationCenterDelegate` on iOS. Static analysis proves the orchestrator
*decides* to post; it cannot prove the OS renders anything. The OS permission prompt
is additionally one-time and irreversible per install, so it cannot be automated.
No executor in this phase had an emulator or simulator attached.
