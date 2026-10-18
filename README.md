# VehiCare

A local-first vehicle service-log app for Android and iOS, built from one Kotlin Multiplatform
codebase: shared Compose Multiplatform UI, Koin dependency injection, JetBrains type-safe
navigation, and SQLDelight (SQLite) persistence. Everything runs offline on the device — the
data layer has no network client by design.

## Requirements

- **JDK 17+** (any newer JDK works; the Gradle wrapper pins Gradle 9.5.1).
- **Android:** an Android emulator or device running **API 26+** (Android Studio or the
  command-line tools provide it; the SDK location stays in the git-ignored `local.properties`).
- **iOS:** **Xcode** (verified with Xcode 27) and an installed simulator runtime of **iOS 16+**.

No third-party accounts, APIs or network services are needed to build or run the app.

## Run the app locally (full stack)

**Android** — build straight onto a connected device/emulator:

```
./gradlew :androidApp:installDebug
```

**iOS** — open the Xcode project and run on a simulator:

```
open iosApp/iosApp.xcodeproj
```

Pick an iOS 16+ simulator and press Run. The iOS host loads the same shared code the Android
launcher mounts, so a feature you exercise on one platform exists on the other.

First build takes a while; both Gradle runs use the configuration cache, so subsequent builds
are fast.

## Verify the stack

```
./gradlew :shared:testAndroidHostTest :shared:iosSimulatorArm64Test
```

runs the Android host unit tests and the iOS simulator unit tests for the shared module.

```
./gradlew verifyCommonMainVehiCareDatabaseMigration
```

builds the SQLDelight baseline schema and replays every committed migration, failing if schema
and migration files drift — a built-in contract, not an option.

## Where your data lives

Everything is app-private. The SQLite database is `vehicare.db` inside the app's own sandbox —
on Android a private `databases/` directory, on iOS inside the app container's Application
Support — with the `vehicare.db-wal` write-ahead-log sidecar next to it. WAL is switched on from
the first connection on both platforms, which keeps readers and writers from blocking each
other; write the row, kill the app, relaunch, the row is still there. Later phases store photos
as separate files (the database keeps only a relative path per row), so the database file stays
small and movable across devices. Delete the app and everything goes with it — no data leaves
the device, ever.
