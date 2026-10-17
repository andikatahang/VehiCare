# Stack Research: CarRecord (KMP)

**Project:** CarRecord — car service record tracker (iOS + Android)  
**Researched:** 2026-10-18  
**Overall Confidence:** HIGH (sourced from official JetBrains docs, Context7, and verified community consensus)

---

## Recommended Stack

| Library / Tool | Version | Purpose | Confidence |
|---|---|---|---|
| Kotlin | 2.2.20 | Language & multiplatform runtime | HIGH |
| Compose Multiplatform | 1.8.2 | Shared UI (iOS + Android) — stable since May 2025 | HIGH |
| SQLDelight | 2.3.2 | Local-first SQLite, type-safe KMP-native | HIGH |
| Koin | 4.2.0 (BOM) | Dependency injection — KMP native, no kapt | HIGH |
| Navigation Compose (JetBrains fork) | 2.9.0 | Screen routing via NavHost/NavController | HIGH |
| kotlinx.coroutines | 1.10.2 | Async, Flows, structured concurrency | HIGH |
| kotlinx.datetime | 0.7.1 | Cross-platform date/time (service dates, reminders) | HIGH |
| kotlinx.serialization | 2.2.20 | Serialize routes for type-safe navigation | HIGH |
| Android Gradle Plugin | 8.10+ | Android build tooling | HIGH |
| Gradle | 8.12+ | Build system (version catalog via `libs.versions.toml`) | HIGH |
| Xcode | 16+ | iOS framework build and signing | HIGH |

---

## UI Layer

### Compose Multiplatform 1.8.2

**Status: Production-stable as of May 2025.** JetBrains declared Compose for iOS Stable in the 1.8.0 release. This is the right time to build on it — the API surface is frozen with strong compatibility guarantees.

**Why Compose Multiplatform over native per-platform UI:**
- 100% shared UI across iOS and Android from `commonMain`
- Full Material 3 component library available
- iOS renders via Skia (not UIKit widgets), but achieves native-feel gestures, accessibility, and back-swipe navigation
- Accessibility support improved significantly in 1.8.x (VoiceControl, VoiceOver, Bold Text, Reduce Motion)

**Minimum Kotlin version:** 2.1.0+ (required by the K2 compiler migration in CMP 1.8). Use 2.2.20 for best iOS/web support.

**Material 3 design system** (`compose.material3`) is the right choice for CarRecord's clean, minimal aesthetic. Material You color system gives you light theme defaults without heavy configuration.

```toml
# gradle/libs.versions.toml
[versions]
compose-multiplatform = "1.8.2"
kotlin = "2.2.20"
agp = "8.10.1"

[plugins]
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**Shared UI entry points:**
- Android: `MainActivity` → `setContent { App() }`
- iOS: `MainViewController.kt` → `ComposeUIViewController { App() }`

---

## Data Layer

### SQLDelight 2.3.2 — ✅ Recommended over Room KMP

**Why SQLDelight, not Room KMP:**

| Criterion | SQLDelight 2.3.2 | Room KMP 2.7.x |
|---|---|---|
| KMP maturity | Mature (5+ years, purpose-built) | Newer to KMP (stable from 2.7.0, 2025) |
| Type safety | Compile-time SQL verification | Annotation-based |
| iOS parity | Full feature parity | Known feature gaps vs Android |
| Performance on iOS | Better (direct SQLite driver) | Performance differences reported vs Android |
| Server coupling | None — cash app owned, independent | Google dependency |
| Community consensus | Preferred for KMP projects | Preferred for Android-only |

**Why NOT Room KMP:** Room KMP 2.7.0 only became stable in 2025 and community reports (Kotlin Slack, Reddit r/KotlinMultiplatform, Nov 2025) show feature gaps and performance inconsistencies on iOS vs Android. For a local-first KMP app, SQLDelight is the safer, more proven choice.

**Setup:**

```kotlin
// shared/build.gradle.kts
plugins {
    id("app.cash.sqldelight") version "2.3.2"
}

sqldelight {
    databases {
        register("CarRecordDatabase") {
            packageName.set("com.carrecord.db")
            generateAsync.set(true) // enables suspend functions
        }
    }
}

kotlin {
    sourceSets {
        androidMain.dependencies {
            implementation("app.cash.sqldelight:android-driver:2.3.2")
        }
        nativeMain.dependencies {
            // covers iosArm64, iosSimulatorArm64, iosX64
            implementation("app.cash.sqldelight:native-driver:2.3.2")
        }
        commonMain.dependencies {
            implementation("app.cash.sqldelight:coroutines-extensions:2.3.2")
        }
    }
}
```

**Query-as-Flow pattern for reactive UI:**

```kotlin
// CarServiceQueries → observe as Flow
val serviceHistory: Flow<List<ServiceEntry>> =
    queries.selectAllByVehicle(vehicleId)
        .asFlow()
        .mapToList(Dispatchers.IO)
```

**expect/actual driver factory** (required for KMP):

```kotlin
// commonMain
expect class DriverFactory {
    fun createDriver(): SqlDriver
}

// androidMain
actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver =
        AndroidSqliteDriver(CarRecordDatabase.Schema, context, "carrecord.db")
}

// iosMain
actual class DriverFactory {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(CarRecordDatabase.Schema, "carrecord.db")
}
```

**DataStore (for small key-value preferences):** Use `androidx.datastore:datastore-preferences:1.1.x` for small settings (e.g., default mileage unit). DataStore has KMP support for Android + iOS.

---

## Notifications

### Approach: `expect/actual` with platform-native SDKs

There is **no single KMP notification library** that handles both local scheduling and push — and that's fine. The correct pattern is:

1. Define a `NotificationService` interface or expect class in `commonMain`
2. Implement natively in `androidMain` and `iosMain`
3. Wire via Koin's platform modules

**Shared interface (commonMain):**

```kotlin
interface NotificationService {
    fun scheduleServiceReminder(
        vehicleId: String,
        title: String,
        body: String,
        triggerAt: Long // epoch millis
    )
    fun cancelReminder(vehicleId: String)
    suspend fun requestPermission(): Boolean
}

expect val platformNotificationModule: Module // Koin module
```

**Android (androidMain):**
- Local scheduling: `WorkManager` + `NotificationCompat` (reliable, survives app kill)
- Permission: `POST_NOTIFICATIONS` (Android 13+)
- Push (future): Firebase Cloud Messaging — add as `androidMain` dependency only

**iOS (iosMain):**
- Local scheduling: `UNUserNotificationCenter` + `UNTimeIntervalNotificationTrigger`
- Permission: `UNAuthorizationOptions`
- Push (future): APNs token registration from `AppDelegate` (Swift)
- Minimal Swift: only `AppDelegate` needs to register `UNUserNotificationCenterDelegate`

**For v1 (local-only reminders):** No push infrastructure needed. CarRecord's reminders are mileage-based and time-based — WorkManager (Android) and UNUserNotificationCenter (iOS) cover this completely.

**For future push:** Use OneSignal or Firebase. **Do NOT add Firebase to `commonMain`** — Firebase has no official KMP support. Add it only to `androidMain` via an interface injected through Koin.

---

## Dependency Injection

### Koin 4.2.0 (BOM) — ✅ Recommended

**Why Koin over Hilt/Dagger:**
- Koin is **KMP-native** — works identically in `commonMain`, `androidMain`, `iosMain`
- No annotation processing (no kapt/KSP footprint for DI setup)
- Koin 4.x ships a BOM for version alignment
- `koin-compose-viewmodel` gives `koinViewModel()` directly in Composables

**Why NOT Hilt:** Android-only — would require duplicating ViewModel wiring for iOS.

```kotlin
// commonMain dependencies
implementation(platform("io.insert-koin:koin-bom:4.2.0"))
implementation("io.insert-koin:koin-core")
implementation("io.insert-koin:koin-compose")
implementation("io.insert-koin:koin-compose-viewmodel")

// androidMain
implementation("io.insert-koin:koin-android")
```

**KMP initialization pattern:**

```kotlin
// commonMain
fun initKoin(config: KoinAppDeclaration? = null) {
    startKoin {
        config?.invoke(this)
        modules(sharedModule, databaseModule)
    }
}

// androidMain (Application.onCreate)
initKoin {
    androidContext(this@App)
    androidLogger()
}

// iosMain (called from Swift iOSApp.init())
fun initKoinIos() = initKoin()
```

**ViewModel pattern (shared in commonMain):**

```kotlin
@KoinViewModel
class VehicleListViewModel(
    private val repo: VehicleRepository
) : ViewModel() {
    val vehicles = repo.observeAll().stateIn(viewModelScope, ...)
}

// In Composable (commonMain):
@Composable
fun VehicleListScreen(vm: VehicleListViewModel = koinViewModel()) { ... }
```

---

## Navigation

### Navigation Compose (JetBrains fork) 2.9.0 — ✅ Recommended

**Why this over Voyager or Decompose:**

| Library | Status | Pros | Cons |
|---|---|---|---|
| **Navigation Compose (JetBrains)** | ✅ Stable | Official JetBrains fork, Jetpack-compatible API, type-safe routes via `@Serializable`, native iOS back gesture, integrated with ViewModel | Serialization required for cross-platform route types |
| Voyager | Beta (1.1.0-beta02) | Simple API, Koin integration, ScreenModel | No predictive back on non-Android, smaller community, no deep links |
| Decompose | Stable | Lifecycle-aware, component model | Higher boilerplate, steeper curve, over-engineered for this app size |
| Navigation 3 | Alpha (JetBrains 1.1.1) | Future of CMP navigation | Alpha — not production-ready yet |

**Why Navigation Compose wins for CarRecord:** It's the standard going forward (JetBrains confirmed Nav 3 is the roadmap, Nav 2 gets stability work in the meantime). For a small-to-medium app, the NavHost + typed routes pattern is simple enough and well-documented.

**Setup:**

```toml
[versions]
navigation-compose = "2.9.0"

[libraries]
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation-compose" }
```

```kotlin
// commonMain dependency
implementation("org.jetbrains.androidx.navigation:navigation-compose:2.9.0")
```

**Type-safe routes (sealed interface, small project pattern):**

```kotlin
@Serializable sealed interface Route : NavKey
@Serializable data object VehicleList : Route
@Serializable data class VehicleDetail(val vehicleId: String) : Route
@Serializable data class AddService(val vehicleId: String) : Route
@Serializable data object ServiceHistory : Route
```

**iOS native back gesture:** Enabled by default in CMP 1.8+. The NavHost applies the correct slide transition matching iOS system behavior automatically.

---

## Build & Tooling

### Project Generator

Use the official **KMP Wizard** at [kmp.jetbrains.com](https://kmp.jetbrains.com) to bootstrap. Select Android + iOS, Compose Multiplatform UI. This gives you:
- Version catalog (`gradle/libs.versions.toml`)
- Correct plugin setup
- `composeApp/` shared module + `androidApp/` + `iosApp/` structure

### Gradle Version Catalog

```toml
# gradle/libs.versions.toml — recommended pinned versions

[versions]
kotlin = "2.2.20"
agp = "8.10.1"
compose-multiplatform = "1.8.2"
koin = "4.2.0"
sqldelight = "2.3.2"
coroutines = "1.10.2"
navigation = "2.9.0"
datetime = "0.7.1"
serialization = "2.2.20"
lifecycle = "2.9.1"

[libraries]
# Core
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

# Navigation
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }

# Lifecycle / ViewModel
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
lifecycle-viewmodel-compose = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "lifecycle" }

# Koin DI
koin-bom = { module = "io.insert-koin:koin-bom", version.ref = "koin" }
koin-core = { module = "io.insert-koin:koin-core" }
koin-compose = { module = "io.insert-koin:koin-compose" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel" }
koin-android = { module = "io.insert-koin:koin-android" }

# SQLDelight
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### Build Performance

Add to `gradle.properties`:
```properties
org.gradle.parallel=true
org.gradle.caching=true
kotlin.incremental=true
kotlin.native.cacheKind=static
```

### IDE

Android Studio (Ladybug 2024.2.1+ or newer) with the Kotlin Multiplatform plugin is the recommended environment. IntelliJ IDEA 2025.1+ also works. Xcode 16+ required for iOS targets.

---

## What NOT to Use

| Library / Approach | Reason to Avoid |
|---|---|
| **Room KMP** | Feature gaps on iOS, performance inconsistencies, younger KMP support than SQLDelight |
| **Hilt / Dagger** | Android-only — forces native code duplication for iOS |
| **Voyager** | Still in beta; no predictive back on non-Android; no deep links; smaller community than JetBrains navigation |
| **Navigation 3** (`navigation3-ui`) | Alpha as of Oct 2026 — API unstable, not production-ready. Consider for v2. |
| **Realm** | Proprietary object model, different performance characteristics, overkill for local CRUD with modest data |
| **Firebase in commonMain** | No official KMP support — must be isolated to `androidMain`; use interface injection |
| **Kotlin 1.x** | CMP 1.8.0+ requires Kotlin 2.1.0 minimum — K2 compiler only |
| **SQLDelight 1.x** | Deprecated; package name changed (`com.squareup.sqldelight` → `app.cash.sqldelight`). Use 2.x only. |
| **kapt** for Koin | Koin 4.x no longer requires kapt. Use the compiler plugin (`io.insert-koin.compiler.plugin`) or pure DSL. |
| **SharedPreferences** directly | Android-only. Use DataStore (KMP) for shared key-value state. |

---

## Open Questions

1. **WorkManager availability in KMP commonMain:** WorkManager is Android-only. The alarm/reminder scheduling logic interface must live in commonMain but implementations are platform-specific. Needs a concrete design for the reminder scheduling service in Phase 2.

2. **kotlinx-datetime 0.7.x breaking changes:** v0.7.0 removed `kotlinx.datetime.Instant` in favor of `kotlin.time.Instant` and renamed `dayOfMonth`→`day`, `monthNumber`→`month`. If any dependency (e.g., CMP Material3 DatePicker) still expects 0.6.x, use `0.7.1-0.6.x-compat` artifact instead.

3. **SQLDelight `generateAsync`:** Enabling `generateAsync = true` in the Gradle plugin switches to suspend-based query APIs. Verify this works with the coroutines-extensions version — both must be 2.3.2.

4. **AGP 9.x migration:** AGP 9.0+ requires the new `com.android.kotlin.multiplatform.library` plugin instead of `com.android.library` for shared KMP modules. The Wizard output at kmp.jetbrains.com generates the right setup for the chosen AGP version. If you stay on AGP 8.10.x, the existing setup is fine for v1.

5. **Navigation 3 readiness:** JetBrains confirmed official CMP support for Navigation 3 in CMP 1.10 (the "Upcoming" section from KotlinConf 2025 slides). By the time CarRecord reaches a second major version, Nav 3 may be the better choice. Designing routes as sealed interfaces now will make migration straightforward.

---

## Sources

- [JetBrains CMP 1.8.0 Blog Post — iOS Stable](https://blog.jetbrains.com/kotlin/2025/05/compose-multiplatform-1-8-0-released-compose-multiplatform-for-ios-is-stable-and-production-ready/) — HIGH confidence
- [CMP What's New 1.8.2 — Official Docs](https://kotlinlang.org/docs/multiplatform/whats-new-compose-180.html) — HIGH confidence
- [SQLDelight Multiplatform SQLite Docs](https://sqldelight.github.io/sqldelight/latest/multiplatform_sqlite) — HIGH confidence (Context7)
- [Koin KMP Setup — Official Docs](https://github.com/insertkoinio/koin/blob/main/docs/reference/koin-core/kmp-setup.md) — HIGH confidence (Context7)
- [Navigation Compose KMP — Official Docs](https://kotlinlang.org/docs/multiplatform/compose-navigation.html) — HIGH confidence
- [Navigation 3 CMP — Official Docs](https://kotlinlang.org/docs/multiplatform/compose-navigation-3.html) — HIGH confidence
- [KMP Push Notifications via expect/actual — kmpbits.com](https://kmpbits.com/posts/notifications-kmp/) — MEDIUM confidence (community)
- [Reddit r/KotlinMultiplatform — Room KMP vs SQLDelight](https://www.reddit.com/r/KotlinMultiplatform/comments/1m5nh6d/) — MEDIUM confidence (community consensus)
- [kotlinx-datetime GitHub](https://github.com/Kotlin/kotlinx-datetime) — HIGH confidence
- [KMP Build System Guide — androidboss.info](https://androidboss.info/kotlin-multiplatform-build-system-guide/) — MEDIUM confidence (community, reviewed Sept 2026)
