# Pitfalls Research: KMP Car Service App

**Domain:** Kotlin Multiplatform — iOS + Android, Compose Multiplatform, SQLDelight, local notifications
**Researched:** 2026-10-18
**Confidence:** HIGH (sourced from JetBrains official docs, GitHub issues, production post-mortems, KotlinConf 2025 talks)

---

## Critical Pitfalls

Mistakes that cause app crashes, data loss, or rewrites.

---

### Pitfall 1: SQLDelight NativeSqliteDriver Thread-Safety Crashes on iOS

**Description:** SQLDelight's `NativeSqliteDriver` on iOS can crash if multiple coroutines or threads access the database concurrently without proper serialization. In SQLDelight 2.0.0, the internal connection map was changed to a non-locked `MutableMap`, making it thread-unsafe. The 2.0.1 release fixed this specific bug, but concurrent DB access via multiple coroutines collecting the same Flow or racing writes can still cause `co.touchlab.sqliter.interop.SQLiteException` crashes — sometimes silently on the background dispatcher, causing `SIGABRT` (EXC_CRASH) in release builds on TestFlight/App Store.

**Warning Signs:**
- Crashes in Firebase Crashlytics/TestFlight with `co.touchlab.sqliter.interop.SQLiteException` or `SIGABRT`
- Stack trace containing `NativeSqliteDriver`, `Pool.borrowEntry`, or `NativeDatabaseConnection.createStatement`
- Crashes only appear in release builds or under load (multiple coroutines collecting DB Flows simultaneously)
- Threads stuck in `Pool.borrowEntry / PoolLock#withLock`

**Prevention:**
1. Pin SQLDelight to **2.0.2 or later** — never use 2.0.0
2. Serialize all DB writes through a single `Dispatchers.IO.limitedParallelism(1)` dispatcher
3. Wrap DAO calls in `runCatching {}` + `.catch {}` inside Flows so a transient SQLite error emits an error state rather than crashing the coroutine
4. Never collect multiple instances of the same DB Flow simultaneously in `StandaloneCoroutine` without a `SupervisorJob + CoroutineExceptionHandler`
5. Use a Repository layer that exposes a single-entry-point DB accessor — do not let multiple use cases directly create their own connections

**Phase:** Database setup phase (Phase 1/2). Wrong to fix later when data is live.

---

### Pitfall 2: iOS Background Task Kills SQLite Mid-Write → Database Corruption

**Description:** iOS aggressively terminates apps in the background via SIGKILL (exception code `0x8badf00d` — "ate bad food"). Any pending SQLite write or WAL commit that is interrupted mid-flight by iOS terminating the process results in a corrupted database file. This is especially dangerous for a local-first app with no cloud sync backup. Users lose ALL their car service history. Repeated 0x8badf00d kills also cause iOS to reduce the app's background execution budget ("shadowban").

**Warning Signs:**
- Users report "app lost all my data" after an OS update or leaving the app in the background
- Crash reports showing SQLite errors on next cold launch
- `co.touchlab.sqliter.interop.SQLiteExceptionErrorCode` on app startup

**Prevention:**
1. Never schedule background DB writes via `BGAppRefreshTaskRequest` — iOS timing is non-deterministic and termination can corrupt mid-write
2. Always complete writes in the foreground (while the app is active)
3. Enable SQLite WAL (Write-Ahead Logging) mode: `PRAGMA journal_mode=WAL;` — WAL is more corruption-resistant than the default rollback journal
4. On app backgrounding (`applicationDidEnterBackground`), close any open transactions before returning
5. Add a database integrity check (`PRAGMA integrity_check;`) on first launch after a crash
6. Never use BGTask for "exact time" reminder logic — use `UNUserNotificationCenter` local notifications instead (the OS handles delivery even when the app is killed)

**Phase:** Database setup phase; also relevant during notification scheduling phase.

---

### Pitfall 3: Treating KMP as "Write Once, Run Everywhere" — Then Hitting Platform Walls

**Description:** KMP shares *logic*, not *everything*. Teams start putting platform-specific code in `commonMain` and hit compiler errors, or use `expect/actual` for too many things — turning every small utility into a two-implementation maintenance burden. The flip side: teams attempt to avoid `expect/actual` entirely and write awkward workarounds in common code that work badly on one platform. The real KMP split is: ~40% shared code in production apps (logic + data layers), UI and platform behavior remain platform-specific.

**Warning Signs:**
- `expect` declarations for trivial things (UUID generation, date formatting, string operations)
- Trying to use `java.io.File`, `java.time.*`, or Android SDK classes from `commonMain`
- Compile errors: "Unresolved reference" when a library isn't KMP-compatible
- Build time exploding because too many expect/actual files exist

**Prevention:**
1. Use `kotlinx.datetime` (not `java.time`) for all date/time in `commonMain`
2. Use `kotlinx.coroutines` for all async — it is fully KMP-compatible
3. For the CarRecord app: keep notification scheduling logic (which reminder is due?) in `commonMain`; keep platform delivery (`UNUserNotificationCenter` vs `NotificationManager`) in `iosMain`/`androidMain` via a lean `expect class NotificationScheduler`
4. Check `klibs.io` before building any utility — most common needs have KMP-ready libraries
5. Reserve `expect/actual` for genuine platform divergence (permissions UI, file paths, notification APIs, context objects)

**Phase:** Project setup / architecture phase. Wrong approach baked in early is expensive to fix.

---

### Pitfall 4: Missing SQLDelight Schema Migrations → App Crashes on Update

**Description:** SQLDelight generates the schema from `.sq` files, but those `CREATE TABLE` statements only run on a fresh install. When you alter a table (add a column for `mileage_unit`, add a reminder table, etc.) without writing a migration `.sqm` file, every user who upgrades gets a crash on launch because the DB schema doesn't match the expected schema. On iOS there is no graceful fallback — the app crashes with a SQLite error.

**Warning Signs:**
- You changed a `CREATE TABLE` statement in a `.sq` file without creating a corresponding `.sqm` migration
- No `.db` snapshot file exists for verifying migrations
- `generateSqlDelightSchema` has never been run

**Prevention:**
1. Set `schemaOutputDirectory` in `build.gradle.kts` from Day 1 and run `generateMainCarRecordSchema` before the first release — this creates `1.db` used by `verifySqlDelightMigration`
2. Every schema change = a new `<version>.sqm` migration file — no exceptions
3. Add `verifySqlDelightMigration` to CI to catch missing migrations before they reach users
4. Never use `destructiveMigration` in production (drops all user data)
5. For columns with NOT NULL constraint, always provide a DEFAULT value in the migration

**Phase:** Every phase that touches the data model. Set up the verification task in Phase 1 and never skip it.

---

### Pitfall 5: iOS Notification Permissions Assumed Rather Than Checked

**Description:** On iOS, notification permission must be explicitly requested via `UNUserNotificationCenter.requestAuthorization()` and is a one-time, irreversible user prompt. If you request permission at the wrong time (app launch, before the user understands why), users deny it and you can never re-prompt — only direct them to Settings. Additionally, the iOS delegate (`UNUserNotificationCenterDelegate`) must be set **before** the notification center performs any notification-related work; setting it after in KMP initialization sequences is a common mistake.

**Warning Signs:**
- Permission dialog appears on first launch with no context ("Why does a car app want notifications?")
- Notifications silently fail to fire after the first denied permission
- iOS UNUserNotificationCenter delegate set too late (after `didFinishLaunchingWithOptions` returns)

**Prevention:**
1. Request notification permission only when the user creates their first reminder — not on app launch
2. Before requesting, show an in-app explanation: "We'll remind you when your next oil change is due"
3. Always check `UNNotificationSettings.authorizationStatus` before scheduling — show a "Go to Settings" prompt if denied
4. Set `UNUserNotificationCenter.current().delegate = self` in `application(_:didFinishLaunchingWithOptions:)`, before any KMP initialization
5. On Android 13+ (API 33+), the `POST_NOTIFICATIONS` permission must be requested at runtime — handle this through `expect/actual`

**Phase:** Notification/reminder phase. Never assume permission is granted; always check status first.

---

## iOS-Specific Gotchas

### iOS 1: Compose Renders via Skiko/Metal, Not UIKit — Profiling Behaves Differently

CMP on iOS renders through a bundled Skia instance (Skiko) directly to a `CAMetalLayer` — not through UIKit's layout engine. This means:
- **Do not profile on the simulator** — Metal behavior on simulators is different from real devices; simulator frame times are meaningless
- Metal shader compilation on iOS happens on the main thread and can cause 20-40ms spikes (hitches) on first render of new UI elements
- Texture atlas thrashing (too many different font sizes, many different image sizes) looks like a recomposition bug but is actually a GPU memory problem
- ProMotion (120Hz) requires the app to hit an 8.3ms frame budget — the same as Android, but the iOS rendering pipeline is less forgiving

**Prevention:** Profile on a physical ProMotion device with Xcode Instruments → Metal System Trace. Use a limited set of font sizes. Pre-warm shaders where possible.

### iOS 2: Safe Area / Keyboard Insets Require Explicit SwiftUI Setup

Compose Multiplatform manages its own inset system starting from 1.5.0. Without proper SwiftUI configuration, the software keyboard will either overlap content or create double-padding:

```swift
// In your SwiftUI ContentView wrapping ComposeUIViewController:
ComposeView()
    .ignoresSafeArea(.all)  // Let Compose manage its own insets
```

Inside Compose, use `Modifier.imePadding()` and `Modifier.safeContentPadding()` to handle the keyboard and notch. Failing to set `ignoreSafeArea(.all)` causes the keyboard to misalign input fields — a common bug reported in GitHub issues #3621, #4567.

**Warning Signs:** TextField scrolls off screen when keyboard appears; layout shifts incorrectly on iPhone with notch.

### iOS 3: Coroutine Cancellation Stack Traces Are Expensive on Kotlin/Native

Cancelling coroutines (e.g., cancelling a Flow collection when navigating away) on Kotlin/Native iOS involves capturing full stack traces, which is significantly more expensive than on the JVM. Cancelling many coroutines in rapid succession (e.g., during fast navigation) can cause visible jank.

**Prevention:** Use `SupervisorScope` and structured cancellation. Avoid rapid start/cancel cycles. Use `stateIn(SharingStarted.WhileSubscribed(5000))` for ViewModels to avoid restarting cold flows on rotation/navigation.

### iOS 4: Suspend Functions Are Exposed as Completion Handlers to Swift

KMP suspend functions are exposed as Objective-C completion handlers to Swift — not as Swift `async/await`. Calling them from Swift feels clunky and cancellation doesn't work properly by default.

**Prevention:** Use **SKIE** (Touchlab) or **KMP-NativeCoroutines** to expose Kotlin coroutines as proper Swift `async/await`. JetBrains officially recommends these libraries. This is especially important if you ever add native iOS UI or Swift-side code.

### iOS 5: `NativeSqliteDriver` Build Failures on First Setup

A known issue (#5007) causes iOS builds to fail at link time when `NativeSqliteDriver` is used, often related to Gradle/Xcode toolchain version mismatches. This blocks development early.

**Prevention:** Follow the exact SQLDelight multiplatform setup from official docs. Ensure `iosMain` dependency uses `app.cash.sqldelight:native-driver`, not the Android driver. Match SQLDelight version to Kotlin version compatibility matrix.

---

## Android-Specific Gotchas

### Android 1: Notification Channel Must Be Created Before Any Notification

Android requires a `NotificationChannel` to be created (once) before any notification can be posted. Not creating it crashes silently — notifications just don't appear without any error in logs. The channel also cannot be changed after creation (name, importance level are locked in).

**Prevention:** Create the channel in `Application.onCreate()` with the correct importance level (`IMPORTANCE_DEFAULT` for reminders). Use `expect/actual` to call this from KMP initialization.

### Android 2: `POST_NOTIFICATIONS` Permission Required on API 33+

Android 13+ requires explicit runtime permission for notifications. Apps that don't request this will silently fail to show notifications on newer devices.

**Prevention:** Request `POST_NOTIFICATIONS` permission at the same time as the notification permission explanation flow. Handle the case where users deny it — show "Reminders won't work unless you grant notification permission in Settings."

### Android 3: Exact Alarms Require `SCHEDULE_EXACT_ALARM` Permission (API 31+)

For mileage/time-based reminders that need to fire at a precise time (e.g., "12 months from today"), Android 12+ requires `SCHEDULE_EXACT_ALARM` permission, which users can revoke. Inexact alarms can be off by hours.

**Prevention:** Declare `SCHEDULE_EXACT_ALARM` and handle the case where it's not granted. Use `AlarmManager.canScheduleExactAlarms()` before scheduling. Fall back to inexact alarms gracefully, and note in the UI that reminder timing may vary.

### Android 4: AlarmManager Alarms Are Lost After Reboot

`AlarmManager` scheduled alarms are cleared when the device reboots. Service records reminder will not fire after a reboot unless rescheduled.

**Prevention:** Register a `BOOT_COMPLETED` broadcast receiver that re-schedules all pending reminders from the database on device startup. This is the correct Android pattern.

---

## SQLDelight Pitfalls

### SQL 1: Modifying `.sq` Files Without Writing `.sqm` Migrations

See Critical Pitfall #4. This is the #1 SQLDelight mistake in production apps.

### SQL 2: Using `Long` for Dates and Mileage Without Enforced Conventions

SQLite has no native date type. SQLDelight stores everything as `TEXT`, `INTEGER`, or `REAL`. Teams commonly store dates as `Long` (epoch milliseconds), but this is invisible at the schema level — callers may accidentally insert wrong-unit values (seconds vs milliseconds vs Julian day numbers).

**Prevention:** Use SQLDelight's custom column adapters to map between `Long` (epoch ms) and `kotlinx.datetime.Instant` or `LocalDate`. Define a `DateAdapter` in your module and use it consistently. Document the unit in the column name: `service_date_epoch_ms INTEGER NOT NULL`.

### SQL 3: N+1 Query Pattern — Querying Per-Car Records Inside a Loop

For a list of cars each with their latest service entry, naive code fetches all cars then queries for each car's latest service in a loop — causing N+1 database roundtrips.

**Prevention:** Use a single `JOIN` query that returns cars + latest service entry in one result set. SQLDelight makes this easy with type-safe SQL:

```sql
SELECT cars.*, service.service_date_epoch_ms AS last_service_date
FROM cars
LEFT JOIN service_entries service ON service.car_id = cars.id
  AND service.id = (
    SELECT id FROM service_entries WHERE car_id = cars.id
    ORDER BY service_date_epoch_ms DESC LIMIT 1
  );
```

### SQL 4: Not Wrapping Multi-Step Inserts in a Transaction

Inserting a service entry may involve multiple writes (insert entry + update reminder state). Without a transaction, a partial failure leaves the database in an inconsistent state.

**Prevention:** Use SQLDelight's `transactionWithReturn {}` or `transaction {}` for any operation that touches more than one table or performs more than one write.

### SQL 5: Forgetting `verifySqlDelightMigration` in CI

Migration files can be syntactically valid SQL but still fail to produce the correct schema. The Gradle task `verifySqlDelightMigration` confirms that applying all migrations to a baseline snapshot yields the current schema.

**Prevention:** Add `./gradlew verifySqlDelightMigration` to CI. Store the baseline `.db` snapshot in version control.

---

## Compose Multiplatform Pitfalls

### CMP 1: LazyColumn Performance on iOS — Missing `key` Parameter

`LazyColumn` without a `key` lambda uses positional identity. When items are added, removed, or reordered (e.g., sorting service history by date), Compose recomposes every visible item even if only one changed. On iOS this is noticeably jankier than on Android because the Skiko renderer's CPU overhead is higher.

**Prevention:** Always provide a `key` parameter to `items()`:
```kotlin
LazyColumn {
    items(serviceEntries, key = { it.id }) { entry ->
        ServiceEntryRow(entry)
    }
}
```

### CMP 2: `LazyColumn` Inside `Column + verticalScroll` — Destroys Laziness

Wrapping a `LazyColumn` inside a `Column` with `verticalScroll()` gives the lazy list unbounded height, causing it to compose *all items immediately* — eliminating all performance benefits of lazy loading.

**Prevention:** Never nest `LazyColumn` inside a scrollable container. Use `LazyColumn` header slots (`item {}`) for non-list content that should scroll with the list.

### CMP 3: Unstable Data Classes Cause Excessive Recomposition

If your data classes contain `List<T>` or other unstable types, Compose cannot prove they are stable and will recompose every item on every state change — even when the data hasn't changed.

**Prevention:** Either annotate data classes with `@Stable`/`@Immutable` (only if they truly are), or use `ImmutableList` from `kotlinx.collections.immutable`. For this app, service entry lists and car lists should be wrapped in `ImmutableList`.

### CMP 4: iOS TextField / Keyboard Handling Is Not Identical to Android

`TextField` keyboard behavior on iOS differs from Android in several ways:
- Dismissing the keyboard requires `singleLine = true` + `ImeAction.Done` for single-line fields
- Multiline TextFields behave differently with the Return key
- The keyboard can partially cover form fields without proper `imePadding()` setup

**Prevention:** Test all forms with the keyboard on a physical iOS device early. Apply `Modifier.imePadding()` to scrollable containers that contain text fields. Set `ignoreSafeArea(.all)` on the SwiftUI wrapper.

### CMP 5: Resource Management — Using Android-Only Resources

Compose Multiplatform has its own multiplatform resource system. Android-specific resource patterns (`R.string.foo`, `stringResource()` from Jetpack Compose Android) do not work in `commonMain`.

**Prevention:** Use `org.jetbrains.compose.resources` for all shared resources (strings, images, fonts). Place all shared strings in `commonMain/composeResources/values/strings.xml`. Never reference `R.` classes from shared code.

---

## Notification Pitfalls

### Notif 1: Scheduling Reminders with `BGAppRefreshTask` on iOS — Will NOT Fire on Time

`BGAppRefreshTaskRequest` on iOS is opportunistic — iOS decides when (and if) to wake the app. It cannot be relied upon for time-sensitive reminders like "oil change due today." Teams that use this for reminder delivery find that reminders fire hours late or not at all.

**Prevention:** Use `UNUserNotificationCenter` with `UNCalendarNotificationTrigger` or `UNTimeIntervalNotificationTrigger` for all reminders. These fire at the scheduled time even when the app is killed — the OS handles delivery. Reserve `BGAppRefreshTask` only for best-effort background sync operations.

### Notif 2: iOS Notification Limit of 64 Pending Notifications

iOS silently drops pending notifications beyond the 64-notification limit. If the app schedules one notification per service type per car (oil, tires, brakes, filters) for multiple cars, it can easily hit this limit.

**Prevention:** Schedule only the *next upcoming* reminder per car per service type, not future ones. Reschedule the next reminder after the user logs a completed service. For this app (few cars, few service types), 64 is unlikely to be a problem — but track it.

### Notif 3: Notification Permission State Is Not Reactive — Must Be Re-Checked

On iOS, the user can revoke notification permission from Settings at any time. The app is not notified when this happens. If the app schedules notifications without checking the current permission status, they will simply not appear — with no error.

**Prevention:** Check `UNNotificationSettings.authorizationStatus` every time the app foregrounds (in `applicationDidBecomeActive` or `sceneDidBecomeActive`). If status has changed to `denied`, update the UI to show a "Notifications disabled — tap to fix" banner.

### Notif 4: Deep Linking from Notification Tap Not Set Up from KMP

When a user taps a service reminder notification, they expect to land on the relevant car's service history screen — not the app home screen. This requires deep link payload in the notification and notification tap handling wired in both platform entry points.

**Prevention:** Include a `carId` payload in every notification. On iOS, implement `userNotificationCenter(_:didReceive:withCompletionHandler:)` in the `AppDelegate`/`SceneDelegate` and route to the correct screen. On Android, handle the `Intent` extras in `MainActivity`. Wire both into the shared navigation state.

### Notif 5: Mileage-Based Reminders Cannot Be Scheduled Without Odometer Input

Mileage-based reminders ("next oil change at 50,000 km") depend on knowing the current odometer reading — which only exists when the user logs it. Teams sometimes try to auto-detect mileage (location-based heuristics, etc.), which is overcomplicated and unreliable.

**Prevention:** Mileage-based reminders are computed, not scheduled in advance. When the user logs a service entry, calculate the next-due mileage. Store it in the DB. Show a reminder when the user manually logs mileage that exceeds the threshold. Separately, schedule a time-based notification as a "have you reached X km yet?" prompt. Keep the mental model simple: time-based reminders use `UNCalendarTrigger`; mileage-based reminders are checked on user interaction.

---

## Phase-Specific Warnings

| Phase Topic | Likely Pitfall | Mitigation |
|-------------|----------------|------------|
| Project setup & DB schema | No migration verification in CI from Day 1 | Run `generateSchema` before first commit; add `verifySqlDelightMigration` to CI |
| Car + service entry CRUD | N+1 queries for car list with latest service | Write JOIN query from the start; don't optimize later |
| SQLDelight initial setup | iOS NativeSqliteDriver build link failures | Follow official docs exactly; verify on simulator before device |
| Reminder scheduling | mileage-based reminder architecture confusion | Separate time-based (scheduled notification) from mileage-based (app-side check on log entry) |
| Notification setup | iOS permission requested too early | Always gate permission request behind first reminder creation |
| iOS notification delivery | Using BGTask instead of UNCalendarTrigger | Use UNUserNotificationCenter for all time-based reminders |
| LazyColumn service history | Missing `key` causing full recompose on scroll | Add `key = { it.id }` from the beginning |
| TextField forms | Keyboard covering fields on iOS | Apply `imePadding()` + `ignoreSafeArea(.all)` in SwiftUI wrapper |
| App update / data migration | Schema change without `.sqm` migration file | Every schema change requires a migration; enforce in PR checklist |
| Background writes | iOS kills process mid-write → DB corruption | Enable WAL mode; complete writes in foreground |

---

## Sources

- JetBrains Compose Multiplatform iOS — Official docs & KotlinConf 2025 presentation (Sebastian Aigner)
- SQLDelight GitHub Issues: #5007 (iOS build failures), #5674 (NativeSqliteDriver query errors), SIGABRT crash (#59 KtorMonitor)
- SQLDelight 2.0.1 release notes — Touchlab (thread-safety fix for Kotlin Native)
- compose-multiplatform GitHub Issues: #4912 (iOS memory/CPU), #3621 (keyboard/TextField), #4438 (LazyColumn perf on iOS), #4567 (keyboard safe area)
- kmpworkmanager/docs/IOS_BGTASK_LIMITS.md — BGTask timing guarantees and SIGKILL behavior
- "The Honest Developer's Guide to KMP in 2026" — AndroidLab (Medium, March 2026)
- "I Thought KMP Would Be Just Kotlin on iOS" — Rudy DevKits (Medium, March 2026)
- KMP-NativeCoroutines / SKIE — official recommendations from JetBrains FAQ
- Apple Developer Documentation — UNUserNotificationCenter, BGTaskScheduler, SafeAreaRegions
- Compose Multiplatform 1.8.0 release notes (May 2025) — iOS stable status
- Kotlin/Native memory management docs (updated May 2026)
- KMPNotifier library README — notification feature matrix for Android/iOS
