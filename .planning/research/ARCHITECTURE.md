# Architecture Research: KMP Car Service App

**Project:** CarRecord — multi-vehicle service history tracker  
**Researched:** 2026-10-18  
**Confidence:** HIGH (official JetBrains docs, SQLDelight docs, Android KMP samples)

---

## Module Structure

CarRecord uses **Strategy 1: Shared UI** — Compose Multiplatform shares both UI and business logic across Android and iOS. This is the right call given CMP is already chosen; it maximises code reuse and keeps the codebase a single team can own.

```
CarRecord/
├── shared/                         # The core KMP module
│   ├── build.gradle.kts            # KMP targets + dependencies
│   └── src/
│       ├── commonMain/             # ALL shared code: UI, domain, data
│       │   └── kotlin/
│       │       ├── ui/             # Compose screens, components
│       │       ├── viewmodel/      # ViewModels (androidx.lifecycle)
│       │       ├── domain/         # Use cases, domain models
│       │       ├── data/           # Repositories, DAOs, mappers
│       │       ├── db/             # SQLDelight .sq files (generated API here)
│       │       └── platform/       # expect declarations (notifications, etc.)
│       ├── androidMain/            # Android actual implementations
│       │   └── kotlin/
│       │       └── platform/       # actual NotificationScheduler (AlarmManager/WorkManager)
│       │       └── platform/       # actual DriverFactory (AndroidSqliteDriver)
│       └── iosMain/                # iOS actual implementations
│           └── kotlin/
│               └── platform/       # actual NotificationScheduler (UNUserNotificationCenter)
│               └── platform/       # actual DriverFactory (NativeSqliteDriver)
│
├── androidApp/                     # Thin Android shell (entry point only)
│   ├── build.gradle.kts            # AGP, applicationId
│   └── src/main/
│       └── MainActivity.kt         # setContent { App() } — ~10 lines
│
└── iosApp/                         # Xcode project (iOS shell)
    ├── iosApp.xcodeproj
    └── iosApp/
        └── ContentView.swift       # UIViewControllerRepresentable wrapping ComposeUIViewController
```

**Why this split:**
- `shared/commonMain` contains ~95% of the code — UI, ViewModels, domain, data
- `androidApp` and `iosApp` are thin shells: set up DI root, call into shared composable
- `androidMain`/`iosMain` only hold `actual` implementations for platform APIs (notifications, DB driver)
- SQLDelight `.sq` files live in `shared/src/commonMain/sqldelight/` and generate type-safe Kotlin into `commonMain`

---

## Layer Breakdown

```
┌──────────────────────────────────────────┐
│  PRESENTATION LAYER (commonMain/ui/)     │
│  @Composable screens + navigation        │
│  NavHost + Screen composables            │
│  Collects StateFlow via collectAsState() │
├──────────────────────────────────────────┤
│  VIEWMODEL LAYER (commonMain/viewmodel/) │
│  ViewModel (androidx.lifecycle KMP)      │
│  Exposes StateFlow<UiState>              │
│  Calls use cases / repository            │
├──────────────────────────────────────────┤
│  DOMAIN LAYER (commonMain/domain/)       │
│  Use cases (optional, for complex logic) │
│  Domain models (Car, ServiceEntry, etc.) │
│  Reminder calculation logic              │
├──────────────────────────────────────────┤
│  DATA LAYER (commonMain/data/)           │
│  Repository (single source of truth)     │
│  SQLDelight DAOs (generated queries)     │
│  Mappers (DB entity ↔ domain model)      │
├──────────────────────────────────────────┤
│  PLATFORM LAYER (expect/actual)          │
│  NotificationScheduler (expect)          │
│  DriverFactory (expect)                  │
│  androidMain / iosMain (actual)          │
└──────────────────────────────────────────┘
```

### Presentation Layer
- `@Composable` screens: `CarListScreen`, `CarDetailScreen`, `AddServiceScreen`, `ReminderScreen`
- `NavHost` with type-safe routes in `commonMain` — works identically on both platforms
- State consumed via `viewModel.uiState.collectAsState()`
- Stateless inner composables receive data + lambdas (no direct ViewModel reference in leaf composables)

### ViewModel Layer
- `androidx.lifecycle:lifecycle-viewmodel` has KMP support — use it
- One ViewModel per screen or feature cluster
- Exposes `StateFlow<ScreenUiState>` — sealed class covering Loading, Success, Error
- `viewModelScope` for coroutines

### Domain Layer
- Plain Kotlin data classes: `Car`, `ServiceEntry`, `ReminderRule`
- Use cases for non-trivial logic: `CalculateNextServiceUseCase`, `CheckOverdueRemindersUseCase`
- No platform dependencies — pure Kotlin, fully testable in `commonTest`

### Data Layer
- `CarRepository` / `ServiceRepository` — single interface, SQLDelight implementation
- SQLDelight generates type-safe query functions from `.sq` files at compile time
- `DriverFactory` (expect/actual) provides the platform-correct `SqlDriver`
- Repository exposes `Flow<List<Car>>` for reactive UI updates

### Platform Layer (expect/actual)
```kotlin
// commonMain/platform/NotificationScheduler.kt
expect class NotificationScheduler {
    fun scheduleServiceReminder(carId: String, serviceName: String, dueDate: Long)
    fun cancelReminder(reminderId: String)
    fun requestPermission()   // no-op on Android < 13; UNUserNotificationCenter on iOS
}

// androidMain
actual class NotificationScheduler(private val context: Context) {
    actual fun scheduleServiceReminder(...) {
        // WorkManager or AlarmManager + NotificationCompat
    }
}

// iosMain
actual class NotificationScheduler {
    actual fun scheduleServiceReminder(...) {
        // UNUserNotificationCenter.addNotificationRequest()
    }
}
```

```kotlin
// commonMain/platform/DriverFactory.kt
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver = AndroidSqliteDriver(
        Database.Schema, context, "carrecord.db"
    )
}

// iosMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver = NativeSqliteDriver(
        Database.Schema, "carrecord.db"
    )
}
```

---

## Component Boundaries

```
UI Composables
    │ reads StateFlow via collectAsState()
    ▼
ViewModel
    │ calls
    ▼
Use Cases (optional, for business logic)
    │ calls
    ▼
Repository (interface in domain, impl in data)
    │ uses
    ├──► SQLDelight Queries (generated, type-safe)
    │        │ uses
    │        ▼
    │    DriverFactory (expect/actual → platform SqlDriver)
    │
    └──► NotificationScheduler (expect/actual)
             │
             ├── Android: WorkManager / AlarmManager
             └── iOS: UNUserNotificationCenter
```

**Rules:**
- UI composables NEVER access the database directly
- Repository NEVER imports Compose or UI code
- Domain models NEVER reference SQLDelight generated types (use mappers)
- `expect` declarations live in `commonMain/platform/`; `actual` in `androidMain/platform/` and `iosMain/platform/`
- ViewModel is the only layer that touches both UI state shaping and domain calls

---

## Data Flow

### Read flow (showing car list on home screen):

```
User opens app
    │
    ▼
CarListScreen (Composable)
    │  collectAsState(CarsUiState.Loading)
    ▼
CarListViewModel.carsUiState: StateFlow<CarsUiState>
    │  collects Flow from repository
    ▼
CarRepository.getAllCars(): Flow<List<Car>>
    │  maps SQLDelight type → domain model
    ▼
SQLDelight CarQueries.selectAll()
    │  emits on every DB change (reactive)
    ▼
DriverFactory → SqlDriver → carrecord.db
```

### Write flow (logging a new service entry):

```
User taps "Save" on AddServiceScreen
    │  calls onSave(serviceEntry)
    ▼
AddServiceViewModel.saveServiceEntry(entry: ServiceEntry)
    │  suspend fun in viewModelScope
    ▼
ServiceRepository.insert(entry)
    │  maps domain model → SQLDelight insert params
    ▼
SQLDelight ServiceEntryQueries.insert(...)
    │  writes to SQLite
    ▼
DB change triggers Flow emission (reactive)
    │
    ▼
CarDetailScreen auto-updates (StateFlow recompose)
```

### Notification scheduling flow:

```
ServiceRepository.insert() or update()
    │  After save
    ▼
ReminderRuleRepository.getRulesForCar(carId)
    │
    ▼
CalculateNextServiceUseCase.calculate(car, entries, rules)
    │  Returns: NextServiceInfo(dueDate, mileageDue)
    ▼
NotificationScheduler.scheduleServiceReminder(...)
    │  platform-specific actual
    ├── Android: WorkManager schedules background check
    └── iOS: UNUserNotificationCenter.add(UNNotificationRequest)
```

---

## Suggested Build Order

Build in dependency order — each step is deliverable and testable independently.

### Wave 1 — Foundation (no UI, no platform APIs)
1. **Project scaffold** — KMP Gradle setup, `androidApp`, `iosApp`, `shared` modules, source sets configured
2. **Domain models** — `Car`, `ServiceEntry`, `ReminderRule`, `NextServiceInfo` data classes (pure Kotlin)
3. **SQLDelight schema** — `.sq` files defining tables, queries; verify codegen works
4. **DriverFactory expect/actual** — Android + iOS drivers; basic DB roundtrip test in `commonTest`
5. **Repository implementations** — `CarRepository`, `ServiceEntryRepository` with SQLDelight; Flow-based reads

### Wave 2 — Core Features (shared UI + ViewModel)
6. **Design system** — `CarRecordTheme`, typography, colors, card components, spacing tokens
7. **Car management screens** — `CarListScreen`, `AddCarScreen` + `CarListViewModel`
8. **Service entry screens** — `AddServiceScreen`, `ServiceHistoryScreen` + `ServiceViewModel`
9. **Navigation** — `NavHost` with routes wiring all screens together
10. **Car detail screen** — aggregates service history, reminder status

### Wave 3 — Reminders + Notifications
11. **Reminder domain logic** — `CalculateNextServiceUseCase`, `CheckOverdueUseCase`; unit test heavily
12. **Reminder UI** — `ReminderSetupScreen`, `ReminderListScreen`
13. **NotificationScheduler expect/actual** — Android (WorkManager) + iOS (UNUserNotificationCenter)
14. **Trigger wiring** — schedule/cancel notifications on service entry save/delete

### Wave 4 — Polish + Platform
15. **iOS-specific polish** — CocoaPods/SPM integration verification, iOS app icon, splash
16. **Android-specific polish** — notification channel setup, adaptive icon
17. **Edge cases** — empty states, form validation, mileage ordering, duplicate entry guard

**Dependency graph:**
- Wave 1 must complete before Wave 2 (ViewModels depend on repositories)
- Reminder logic (Wave 3, step 11) depends on having real data from Wave 2
- Notification platform code (step 13) depends on reminder logic (step 11)
- Platform polish (Wave 4) is always last

---

## Platform-Specific Concerns

### iOS Differences in KMP

| Concern | Android | iOS | Notes |
|---------|---------|-----|-------|
| **UI Entry** | `MainActivity.setContent { App() }` | `ComposeUIViewController { App() }` wrapped in Swift `ContentView` | CMP handles this |
| **SQLite Driver** | `AndroidSqliteDriver` | `NativeSqliteDriver` | Both via `DriverFactory` actual |
| **Notifications** | `NotificationCompat` + `NotificationChannel` + `WorkManager` | `UNUserNotificationCenter` + `UNNotificationRequest` | Separate actual classes |
| **Notification Permission** | Android 13+ requires `POST_NOTIFICATIONS` runtime permission | iOS always requires explicit `requestAuthorization()` | Handle in actual |
| **Background Scheduling** | WorkManager (guaranteed delivery, battery-aware) | UNUserNotificationCenter (local only, no background task needed for scheduled) | iOS local notifications are simpler |
| **Build toolchain** | Gradle + AGP | Gradle generates xcframework → Xcode links it | `iosApp.xcodeproj` runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` |
| **Memory model** | JVM GC | Kotlin/Native GC (since 1.7.20, much improved) | Avoid sharing mutable state across threads (use `@ThreadLocal` if needed) |
| **ViewModel** | `androidx.lifecycle` | `androidx.lifecycle` (KMP support added) | Same API, works in commonMain |

### iOS-Specific Build Setup
- Xcode project must invoke `embedAndSignAppleFrameworkForXcode` Gradle task as a Build Phase
- SQLite is available on iOS but must be linked — SQLDelight's `linkSqlite = true` (default) handles this automatically
- CMP iOS uses `UIKitView` interop for any native views (camera, maps) — not needed for CarRecord v1

### Android-Specific Notes
- Must declare notification channel in `Application.onCreate()` (Android 8.0+)
- `WorkManager` for background reminder checks is the correct API — `AlarmManager` is discouraged for new apps
- AGP 9+ requires `androidApp` to be a separate module from `shared` (already the case in recommended structure)

### Notifications: iOS Timing Constraint
iOS local notifications can only fire at a specific date/time — they cannot re-evaluate mileage on a schedule. For **mileage-based reminders**, the strategy is:
- **Android:** WorkManager periodic task checks mileage threshold and fires notification if needed
- **iOS:** On app foreground, check mileage; if overdue, trigger immediate local notification (or schedule one for next open)

This is the single biggest platform divergence in CarRecord. Design the `NotificationScheduler` interface to accommodate both strategies:

```kotlin
expect class NotificationScheduler {
    fun scheduleTimeBased(reminderId: String, dueDate: Long, title: String, body: String)
    fun triggerImmediateIfOverdue(reminderId: String, title: String, body: String)
    fun cancel(reminderId: String)
    fun requestPermissionIfNeeded()
}
```

---

## Sources

- JetBrains KMP Official Docs: https://kotlinlang.org/docs/multiplatform.html (HIGH confidence)
- KMP Project Structure Guide: https://www.jetbrains.com/help/kotlin-multiplatform-dev/recommended-project-structure.html (HIGH)
- SQLDelight Multiplatform SQLite: https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite (HIGH)
- Compose Multiplatform: https://github.com/JetBrains/compose-multiplatform (HIGH)
- Android KMP Samples (Fruitties): https://github.com/android/kotlin-multiplatform-samples (HIGH)
- KMP DeepWiki Architecture: https://deepwiki.com/JetBrains/kotlin-multiplatform-dev-docs (MEDIUM)
