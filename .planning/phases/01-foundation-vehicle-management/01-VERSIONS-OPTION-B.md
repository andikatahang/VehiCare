# 01 — VERSIONS (Option B: current-generation stack)

> ## ⚠️ SUPERSEDED — DO NOT USE THE PINS BELOW
>
> The user exercised the `option-b` gate, then **overrode the version pins in this file** with an explicit instruction: adopt the version set that already ships with the user-supplied Android Studio KMP project at `~/Proyek Aplikasi/VehiCare`, and keep it rather than re-pinning.
>
> Four pins therefore differ from this document: **Kotlin 2.4.20** (not 2.4.21), **AGP 9.1.1** (not 9.3.1), **Compose Multiplatform 1.12.1** (not 1.12.2), and **material3 1.12.0-alpha03** (not 1.9.0). Everything else here is in force. `compileSdk`/`targetSdk` 37, `minSdk` 26 and iOS 16.0 are unchanged.
>
> **The authoritative source of truth is now `gradle/libs.versions.toml`, plus the "Version set actually in use" table in `01-01-SUMMARY.md`.** Every pin in the adopted set is backed by an executed build (`:androidApp:assembleDebug` and `:shared:compileKotlinIosSimulatorArm64` both passed on 2026-10-18), which is stronger evidence than this file's research-based claims.
>
> This file is retained unchanged below as research evidence. Its `## Unresolved` risks remain useful context.

**Phase:** 01-foundation-vehicle-management
**Generated:** 2026-10-18
**Purpose:** the single, verified version contract for plan **01-01** (Wave 0 build gate). Every pin below was re-verified on 2026-10-18 against live repository metadata, published artifacts, or official documentation — nothing is carried over from `01-RESEARCH.md`'s aligned-down set or from a wizard default.
**Writer rule:** ~~use exactly these versions. Do not "nearest-match", do not relax a pin, and do not add a dependency whose version is not sourced from this document. If something forces a change, republish this file.~~ **Superseded — see the notice above.**

---

## 1. Version table (verified 2026-10-18)

`Status` is one of **VERIFIED** (exists at this exact coordinate and the pin is justified by the cited evidence) or **ASSUMED** (no such pin below — every row is VERIFIED; residual risks live in `## Unresolved`).

| Component | Pin | Newest stable? | Status | Evidence |
|---|---|---|---|---|
| Kotlin / KGP | **2.4.21** | yes (2.5.0-Beta1 is pre-release, excluded) | VERIFIED | `kotlin-gradle-plugin` maven-metadata.xml; JetBrains KGP↔Gradle/AGP compatibility table |
| Gradle (wrapper) | **9.5.1** | no — newest overall is 9.8.1, but it is outside the KGP-supported range (≤ 9.7.0) | VERIFIED | services.gradle.org/versions/all; JetBrains compatibility table (Gradle 7.6.3–9.7.0 for KGP 2.4.20/2.4.21); dist already in `~/.gradle/wrapper/dists` |
| AGP | **9.3.1** | no — newest stable is 9.4.1, which needs Gradle ≥ 9.6.0 and is outside the KGP-verified table; 9.3.2/9.3.3 also exist but beyond the verified table (see Unresolved #2) | VERIFIED | Google Maven `com.android.tools.build:gradle` metadata; AGP 9.3/9.4 release notes; JetBrains table (AGP 8.5.2–9.3.1 for KGP 2.4.21) |
| Compose Multiplatform | **1.12.2** | yes (published 2026-10-15) | VERIFIED | GitHub release `v1.12.2`; `org.jetbrains.compose:compose-gradle-plugin` metadata |
| SQLDelight | **2.4.1** | yes | VERIFIED | `app.cash.sqldelight:gradle-plugin` metadata; task registration confirmed in 2.4.1 source (`SqlDelightDatabase.kt`) |
| Koin | **4.2.2** | yes | VERIFIED | `io.insert-koin:koin-core` / `koin-compose` / `koin-compose-viewmodel` metadata; also the pin used by the current official template |
| Navigation (JetBrains) | **2.9.2** | yes (CMP 1.12.2 release notes pair `2.10.0-rc01`, a pre-release — excluded) | VERIFIED | `org.jetbrains.androidx.navigation:navigation-compose` metadata; CMP 1.12.2 release notes table |
| Lifecycle (JetBrains) | **2.11.0** | yes (same version CMP 1.12.2 pairs) | VERIFIED | `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel` metadata (2.11.0 final present); CMP 1.12.2 release notes table |
| kotlinx-coroutines | **1.11.0** | yes | VERIFIED | `kotlinx-coroutines-core` metadata |
| kotlinx-serialization | **1.11.0** | yes (1.12.0-RC is pre-release, excluded) | VERIFIED | `kotlinx-serialization-json` metadata |
| FileKit | **0.16.0** | yes | VERIFIED | `io.github.vinceglb:filekit-dialogs-compose` / `filekit-core` / `filekit-coil` metadata |
| Coil | **3.6.3** | yes | VERIFIED | `io.coil-kt.coil3:coil-compose` metadata |
| androidx activity-compose | **1.13.0** | yes (1.14.0-alpha03 is pre-release) | VERIFIED | Google Maven `androidx.activity:activity-compose` metadata |
| Compose material-icons-core | **1.7.3** | frozen — last version ever published, and the CMP 1.12.2 plugin itself pins 1.7.3 for its icons artifact | VERIFIED | `material-icons-core` metadata; CMP `ComposePlugin.kt` at `v1.12.2` |
| Compose material3 | **1.9.0** | last stable line; the newest available line is `1.12.0-alpha03` (alpha, excluded — Unresolved #4) | VERIFIED | `material3` metadata; the published plugin jar embeds `composeMaterial3Version = "1.9.0"` (javap on `compose-gradle-plugin-1.12.2.jar`); CMP 1.12.2 release notes |
| Android compileSdk (platform) | **37.0** via `compileSdk { version = release(37) { minorApiLevel = 0 } }` | n/a | VERIFIED | AGP API-level table (`tools/gradle-api` 9.3 docs: API 37.0 min AGP 9.1.1); installed platform `android-37.0`; `CompileSdkSpec` block DSL exists on both extensions |
| Android minSdk | **26** | n/a | VERIFIED | carried over unchanged from `SKELETON.md`; every pinned AndroidX artifact supports minSdk 26 |
| Android targetSdk | **37** | n/a | VERIFIED | matches compileSdk; `targetSdk` property exists on `ApplicationDefaultConfig` (AGP 9.3 API) |
| Build JVM | **JDK 25.0.4 / 26.0.1** (installed) | n/a | VERIFIED | Gradle Java matrix (Gradle runs on JDK 25 since 9.1.0 and JDK 26 since 9.4.0); both installed locally |

Compatibility chain check (all VERIFIED): KGP 2.4.21 supports Gradle 7.6.3–9.7.0 and AGP 8.5.2–9.3.1 → Gradle **9.5.1** and AGP **9.3.1** both inside the window. AGP 9.3.1 requires Gradle ≥ 9.5.0 and runs on JDK ≥ 17 → satisfied. CMP requires the Compose Compiler plugin version to equal the KGP version → satisfied by pinning `composeCompiler` to `version.ref = "kotlin"`. CMP 1.12.2's own CI matrix (Gradle 9.3.1/9.5.0, AGP 9.1.1/9.2.0) bracketed by our picks — note the AGP row in Unresolved #2.

---

## 2. `gradle/libs.versions.toml` (complete file)

Aliases marked `mandated` are contract-fixed names; aliases marked `added` are additions required to make the skeleton compilable (no renames, only additions). The required version slots `coroutines` and `serialization` are consumed by the two added `kotlinx-*` library aliases below.

```toml
[versions]
kotlin = "2.4.21"
agp = "9.3.1"
compose-multiplatform = "1.12.2"
sqldelight = "2.4.1"
koin = "4.2.2"
navigation = "2.9.2"
lifecycle = "2.11.0"
coroutines = "1.11.0"
serialization = "1.11.0"
filekit = "0.16.0"
coil = "3.6.3"
android-compileSdk = "37"
android-minSdk = "26"
android-targetSdk = "37"
# --- added: needed by a compilable skeleton (not part of the mandated slot list) ---
androidx-activity = "1.13.0"
material-icons = "1.7.3"
material3 = "1.9.0"

[libraries]
# mandated
sqldelight-android-driver = { module = "app.cash.sqldelight:android-driver", version.ref = "sqldelight" }
sqldelight-native-driver = { module = "app.cash.sqldelight:native-driver", version.ref = "sqldelight" }
sqldelight-coroutines = { module = "app.cash.sqldelight:coroutines-extensions", version.ref = "sqldelight" }
koin-core = { module = "io.insert-koin:koin-core", version.ref = "koin" }
koin-compose = { module = "io.insert-koin:koin-compose", version.ref = "koin" }
koin-compose-viewmodel = { module = "io.insert-koin:koin-compose-viewmodel", version.ref = "koin" }
navigation-compose = { module = "org.jetbrains.androidx.navigation:navigation-compose", version.ref = "navigation" }
lifecycle-viewmodel = { module = "org.jetbrains.androidx.lifecycle:lifecycle-viewmodel", version.ref = "lifecycle" }
filekit-dialogs-compose = { module = "io.github.vinceglb:filekit-dialogs-compose", version.ref = "filekit" }
filekit-core = { module = "io.github.vinceglb:filekit-core", version.ref = "filekit" }
filekit-coil = { module = "io.github.vinceglb:filekit-coil", version.ref = "filekit" }
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
# added
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
compose-runtime = { module = "org.jetbrains.compose.runtime:runtime", version.ref = "compose-multiplatform" }
compose-foundation = { module = "org.jetbrains.compose.foundation:foundation", version.ref = "compose-multiplatform" }
compose-ui = { module = "org.jetbrains.compose.ui:ui", version.ref = "compose-multiplatform" }
compose-uiTooling = { module = "org.jetbrains.compose.ui:ui-tooling", version.ref = "compose-multiplatform" }
compose-uiToolingPreview = { module = "org.jetbrains.compose.ui:ui-tooling-preview", version.ref = "compose-multiplatform" }
compose-components-resources = { module = "org.jetbrains.compose.components:components-resources", version.ref = "compose-multiplatform" }
compose-material3 = { module = "org.jetbrains.compose.material3:material3", version.ref = "material3" }
compose-material-icons-core = { module = "org.jetbrains.compose.material:material-icons-core", version.ref = "material-icons" }
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }

[plugins]
# mandated — note: `composeCompiler` MUST reference the `kotlin` version alias so the
# Compose compiler can never drift from the Kotlin version.
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
androidLibrary = { id = "com.android.kotlin.multiplatform.library", version.ref = "agp" }
composeMultiplatform = { id = "org.jetbrains.compose", version.ref = "compose-multiplatform" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

Catalog rules:

- `androidLibrary` now points at **`com.android.kotlin.multiplatform.library`** (the KMP Android library plugin). In the old plan this alias pointed at the plain `com.android.library` — that plugin cannot host a KMP module on the AGP 9 new DSL (see §7 Deltas).
- There is deliberately **no `koin-bom`** and no BOM-managed version-less entries (the old plan used one); each Koin artifact carries an explicit `version.ref`.
- `composeCompiler`, `kotlinSerialization` and `sqldelight` plugin refs are alias-bound to `kotlin` / `sqldelight` as mandated.

---

## 3. Module skeleton — Q1 answer + `shared/build.gradle.kts`

### Q1 — which plugin applies the Android target in the KMP module

**`com.android.kotlin.multiplatform.library`** (catalog alias `androidLibrary`) is the plugin that brings the Android target into the KMP module; it is configured as a `kotlin { androidLibrary { … } }` block (it replaces `androidTarget()` entirely on this stack). There is **no** `com.android.kotlin.multiplatform.application` plugin and KMP + `com.android.application` in one subproject is incompatible with AGP 9's new DSL — so the application host is a **separate `:androidApp` module** that applies `com.android.application` with built-in Kotlin support (do **not** add `org.jetbrains.kotlin.android`; the app module needs only `androidApplication` + `composeCompiler`).

Module map (mandatory split):

| Module | Plugins (from catalog) | Role |
|---|---|---|
| `:shared` | `kotlinMultiplatform`, `androidLibrary`, `composeMultiplatform`, `composeCompiler`, `kotlinSerialization`, `sqldelight` | All shared code, UI, navigation, DB, drivers; Android **library** target via `androidLibrary {}`; iOS static framework `Shared` |
| `:androidApp` | `androidApplication`, `composeCompiler` only | `MainActivity`, manifest, launcher resources, `applicationId = "com.vehicare"` |
| `iosApp/` (Xcode) | — | Unchanged consumer of the `Shared` framework via `embedAndSignAppleFrameworkForXcode` |

`settings.gradle.kts` must `include(":shared", ":androidApp")`. `gradle.properties` must contain `android.useAndroidX=true` and `android.nonTransitiveRClass=true` (official-template parity; AGP 9 requires the AndroidX mode). Recommended: `org.gradle.jvmargs=-Xmx4096M -Dfile.encoding=UTF-8`, `kotlin.daemon.jvmargs=-Xmx3072M`, `org.gradle.caching=true`. Pin the wrapper with `./gradlew wrapper --gradle-version 9.5.1` (already in the local wrapper cache).

### `shared/build.gradle.kts` (complete)

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.sqldelight)
}

kotlin {
    androidLibrary {
        namespace = "com.vehicare.shared"
        // Q3: block form is the only way to express the minor API level here —
        // KotlinMultiplatformAndroidLibraryExtension has no `compileSdkMinor` property.
        compileSdk {
            version = release(libs.versions.android.compileSdk.get().toInt()) {
                minorApiLevel = 0
            }
        }
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
    }

    listOf(
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)

            implementation(libs.navigation.compose)
            implementation(libs.lifecycle.viewmodel)
            implementation(libs.compose.material.icons.core)

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(libs.sqldelight.coroutines)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.filekit.dialogs.compose)
            implementation(libs.filekit.core)
            implementation(libs.filekit.coil)
            implementation(libs.coil.compose)
        }
        androidMain.dependencies {
            implementation(libs.sqldelight.android.driver)
        }
        iosMain.dependencies {
            implementation(libs.sqldelight.native.driver)
        }
    }
}

sqldelight {
    databases {
        create("VehiCareDatabase") {
            packageName.set("com.vehicare.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
            generateAsync.set(false)
        }
    }
}

dependencies {
    // configuration provided by the KMP Android library plugin (template-verified)
    androidRuntimeClasspath(libs.compose.uiTooling)
}
```

### `androidApp/build.gradle.kts` (complete — required by the Q1 split)

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.vehicare"
    compileSdk {
        version = release(libs.versions.android.compileSdk.get().toInt()) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        applicationId = "com.vehicare"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.compose.uiToolingPreview)
}
```

Not in the build files but part of the split (file-path changes, see §7):

- `MainActivity` lives at `androidApp/src/main/kotlin/com/vehicare/MainActivity.kt` (package `com.vehicare`).
- Manifest + launcher resources live under `androidApp/src/main/` (`AndroidManifest.xml`, `res/mipmap-anydpi-v26/ic_launcher.xml`, `res/drawable/ic_launcher_foreground.xml`, `res/values/ic_launcher_background.xml`) — same content as the old plan specified, new location.
- The `:shared` module needs no Android manifest.

---

## 4. Q2 — SQLDelight migration task names (verified for 2.4.1)

Per-source-set task names are **unchanged by the version bump and by the module split** because the database lives in `commonMain` of `:shared`:

- `./gradlew :shared:generateCommonMainVehiCareDatabaseSchema` → writes the schema snapshot into `schemaOutputDirectory` (`shared/src/commonMain/sqldelight/databases/`).
- `./gradlew :shared:verifyCommonMainVehiCareDatabaseMigration` → replays `.sqm` migrations against the snapshot.

Both are registered by `SqlDelightDatabase.kt` in 2.4.1 (source-verified). The aggregate `verifySqlDelightMigration` task is **not** a substitute (it is registered unconditionally and passes vacuously; the `git diff --exit-code` guard on the schema snapshot remains load-bearing). One 2.4.x risk to watch: "Implement stricter MigrationFile versioning (#5730)" (see Unresolved #3).

## 5. Q3 — compileSdk (verified)

Use the **block form** in both modules:

```kotlin
compileSdk {
    version = release(37) {
        minorApiLevel = 0
    }
}
```

Why: this machine has only `android-37.0` (and `android-36.1`) installed — there is no plain `android-37` platform, so a bare `compileSdk = 37` risks the historical "Failed to find target with hash string 'android-37'" resolution. The block form requests the `android-37.0` hash explicitly, and on the KMP `androidLibrary` extension it is the **only** way to state the minor (no `compileSdkMinor` property exists there; the property exists only on `CommonExtension`, i.e. the app module, as a fallback). API 37.0 needs AGP ≥ 9.1.1 → satisfied (9.3.1). Build tools ≥ 36.0.0 installed (36.0.0 / 36.1.0 / 37.0.0 present). SDK location: `~/Library/Android/sdk` (no `cmdline-tools`; not needed).

---

## 6. Single Compose stack — exact check

**Rule:** on the final app runtime classpath there must be exactly **one** version across all `org.jetbrains.compose.*` artifacts, equal to the CMP pin **1.12.2** — with exactly two documented exceptions: `org.jetbrains.compose.material3:material3:1.9.0` (the plugin-embedded stable pairing) and `org.jetbrains.compose.material:material-icons-core:1.7.3` (frozen artifact).

**Exact command (run after 01-01 creates both modules):**

```bash
./gradlew :androidApp:dependencies --configuration debugRuntimeClasspath 2>/dev/null \
  | grep -oE 'org\.jetbrains\.compose\.[a-z0-9.-]+:[a-z0-9.-]+:[0-9][0-9a-zA-Z.\-]*' \
  | grep -vE 'org\.jetbrains\.compose\.material3:|material-icons-core' \
  | awk -F: '{print $3}' | sort -u
```

**Expected output:** exactly one line — `1.12.2`.

**Violation shape:** two or more distinct version lines (e.g. `1.10.2` and `1.12.2`). That is the half-upgraded stack from RESEARCH Pitfall 2 — a library transitively declares a different CMP generation and Gradle "highest wins" promotes only some artifacts. Find the culprit with:

```bash
./gradlew :androidApp:dependencyInsight --configuration debugRuntimeClasspath --dependency org.jetbrains.compose.runtime:runtime
```

and fix by bumping/removing the offending library — never by adding `resolutionStrategy.force`.

---

## 7. Deltas vs old plans (`01-RESEARCH.md`, `SKELETON.md`, `01-01…01-10-PLAN.md`)

1. **Whole version generation replaced.** Kotlin 2.2.20 / AGP 8.11.1 / CMP 1.8.2 / Gradle 8.14.6 / SQLDelight 2.2.1 / Koin 4.1.1 / nav 2.9.0 / lifecycle 2.9.4 / coroutines 1.10.2 / serialization 1.7.3 / FileKit 0.11.0 / Coil 3.3.0 / SDK 36 → **Kotlin 2.4.21 / AGP 9.3.1 / CMP 1.12.2 / Gradle 9.5.1 / SQLDelight 2.4.1 / Koin 4.2.2 / nav 2.9.2 / lifecycle 2.11.0 / coroutines 1.11.0 / serialization 1.11.0 / FileKit 0.16.0 / Coil 3.6.3 / SDK 37.0**. The old set's JDK 21 + Xcode 16.4 install requirements are dropped (Gradle 9.5.1 runs on the installed JDK 25/26); Xcode 27 remains a watch item (Unresolved #1).
2. **Module layout: single `:shared` → `:shared` (KMP library) + `:androidApp` (application).** Forced by AGP 9 (no `androidTarget()`, no KMP+`com.android.application` co-existence). Gradle task paths move: `:shared:assembleDebug` → `:androidApp:assembleDebug`; `:shared:installDebug` → `:androidApp:installDebug`; `:shared:testDebugUnitTest` → `:androidApp:testDebugUnitTest`. Unchanged: `:shared:compileKotlinIosSimulatorArm64`, `:shared:linkDebugFrameworkIosSimulatorArm64`, `:shared:iosSimulatorArm64Test`, and both SQLDelight tasks (still on `:shared`).
3. **Android DSL:** `androidTarget { }` + `com.android.application` → `kotlin { androidLibrary { } }` via `com.android.kotlin.multiplatform.library`; app module uses built-in Kotlin (no `org.jetbrains.kotlin.android`). The `android.newDsl=false` escape hatch exists but is deprecated and removed in AGP 10 — not used.
4. **Android file moves:** `shared/src/androidMain/AndroidManifest.xml`, `MainActivity`, and launcher resources → `androidApp/src/main/…`; library namespace `com.vehicare.shared`, app namespace + applicationId `com.vehicare`.
5. **SDK levels:** compileSdk/targetSdk 36 → **37** with the block DSL (`release(37) { minorApiLevel = 0 }`); minSdk stays 26. Reason: only `android-37.0` is installed; the block form is required on the KMP extension.
6. **Gradle wrapper:** 8.14.6 → **9.5.1** (AGP 9.3 needs ≥ 9.5.0; KGP 2.4.21 supports ≤ 9.7.0; 9.5.1 is already cached locally; Gradle 9.8.1 is outside the verified range).
7. **Compose accessors → explicit catalog coordinates.** CMP 1.12.2 deprecates **all** `compose.*` DSL accessors ("Specify dependency directly") and `compose.materialIconsCore` no longer exists; the contract uses catalog aliases (`compose-runtime`, `compose-foundation`, `compose-ui`, `compose-material3`, `compose-components-resources`, `compose-uiTooling(+Preview)`) exactly like the official KMP-App-Template.
8. **material3 pinned explicitly to 1.9.0** (plugin-embedded stable pairing; newest line 1.12.0-alpha03 is an alpha — Unresolved #4).
9. **Koin BOM dropped**; `koin-core` / `koin-compose` / `koin-compose-viewmodel` are direct `version.ref` pins at 4.2.2.
10. **New direct deps consume the required slots:** `kotlinx-coroutines-core` (1.11.0 — the serialized-IO lane and `Dispatchers` use) and `kotlinx-serialization-json` (1.11.0 — `@Serializable` nav routes).
11. **Single Compose stack check moves** to `:androidApp:dependencies --configuration debugRuntimeClasspath` (§6); the old `:shared:dependencies --configuration commonMainImplementation` form only proves declared deps and no longer front-runs the runtime classpath.
12. **First iOS build downloads Kotlin/Native prebuilt 2.4.21** (the local konan cache has 2.4.20 only). Expected, not a failure — do not "fix" it by downgrading Kotlin.

---

## Evidence

All fetched/verified 2026-10-18. Repository metadata (existence + newest versions): Maven Central `maven-metadata.xml` for `org.jetbrains.kotlin:kotlin-gradle-plugin`, `org.jetbrains.compose:compose-gradle-plugin`, `org.jetbrains.compose.material3:material3`, `org.jetbrains.compose.material:material-icons-core`, `app.cash.sqldelight:gradle-plugin` + `runtime`, `io.insert-koin:koin-core|koin-compose|koin-compose-viewmodel`, `org.jetbrains.androidx.navigation:navigation-compose`, `org.jetbrains.androidx.lifecycle:lifecycle-viewmodel`, `org.jetbrains.kotlinx:kotlinx-coroutines-core`, `org.jetbrains.kotlinx:kotlinx-serialization-json`, `io.github.vinceglb:filekit-dialogs-compose|filekit-core|filekit-coil`, `io.coil-kt.coil3:coil-compose`; Google Maven metadata for `androidx.activity:activity-compose` and `com.android.tools.build:gradle`.

- https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-gradle-plugin/maven-metadata.xml — Kotlin 2.4.21 newest stable (2.5.0-Beta1 pre-release)
- https://services.gradle.org/versions/all — Gradle stable list (9.8.1 newest)
- https://docs.gradle.org/current/userguide/compatibility.html — Gradle↔JDK matrix (JDK 25 from 9.1.0, JDK 26 from 9.4.0)
- https://kotlinlang.org/docs/multiplatform-compatibility-guide.html — KGP↔Gradle/AGP/Xcode compatibility table (KGP 2.4.21: Gradle 7.6.3–9.7.0, AGP 8.5.2–9.3.1)
- https://dl.google.com/android/maven2/com/android/tools/build/gradle/maven-metadata.xml — AGP versions (9.3.1 / 9.3.2 / 9.3.3 / 9.4.1)
- https://developer.android.com/build/releases/gradle-plugin — AGP release notes (9.3/9.4 min Gradle, max API, min JDK, build-tools)
- https://github.com/JetBrains/compose-multiplatform/releases/tag/v1.12.2 — CMP 1.12.2 release + pairing table (lifecycle 2.11.0, navigation 2.10.0-rc01, material3 1.12.0-alpha03)
- https://repo1.maven.org/maven2/org/jetbrains/compose/compose-gradle-plugin/1.12.2/compose-gradle-plugin-1.12.2.jar — published plugin jar; `javap -constants` on `org/jetbrains/compose/ComposeBuildConfig.class` → `composeVersion = "1.12.2"`, `composeMaterial3Version = "1.9.0"` (ground truth for the material3 pin)
- https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposePlugin.kt — accessors deprecated ("Specify dependency directly"); icons pinned to 1.7.3; no `materialIconsCore`
- https://github.com/JetBrains/compose-multiplatform/blob/v1.12.2/gradle-plugins/gradle.properties — CMP 1.12.2 CI matrix (Gradle 9.3.1/9.5.0, AGP 9.1.1/9.2.0)
- https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/KotlinMultiplatformAndroidLibraryExtension — `androidLibrary` DSL: `compileSdk {}` block, `minSdk`, no `targetSdk`, no `compileSdkMinor`
- https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/CommonExtension — `compileSdk {}` block + `compileSdkMinor` (app module fallback)
- https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/CompileSdkSpec — `version = release(33) { minorApiLevel = 1 }` block syntax
- https://developer.android.com/reference/tools/gradle-api/9.3/com/android/build/api/dsl/ApplicationDefaultConfig — `targetSdk` property + `targetSdk {}` block (AGP 9.3)
- https://github.com/Kotlin/KMP-App-Template — `shared/build.gradle.kts`, `androidApp/build.gradle.kts`, `gradle/libs.versions.toml`, `settings.gradle.kts`, `gradle.properties` (blessed split-module pattern on AGP 9)
- https://github.com/sqldelight/sqldelight/blob/2.4.1/gradle-plugin/src/main/kotlin/app/cash/sqldelight/gradle/SqlDelightDatabase.kt — per-source-set task registration (`generateCommonMainVehiCareDatabaseSchema`, `verifyCommonMainVehiCareDatabaseMigration`)
- https://sqldelight.github.io/sqldelight/2.4.1/ — migrations workflows (`<version>.sqm` naming, `schemaOutputDirectory`, `verifySqlDelightMigration` runs as part of `check`)

Local machine checks (read-only, 2026-10-18): `~/Library/Android/sdk/platforms` = `android-36.1`, `android-37.0`; build-tools 36.0.0/36.1.0/37.0.0; `~/.gradle/wrapper/dists` contains 9.1.0, 9.2.0, 9.3.0, 9.5.0, **9.5.1**; JDK 25.0.4 + 26.0.1 installed; `~/.konan/kotlin-native-prebuilt-macos-aarch64-2.4.20` cached (2.4.21 will download).

## Unresolved

1. **Xcode 27.0 ↔ Kotlin 2.4.21.** This machine has only Xcode 27.0; the JetBrains compatibility table was verified only through the Xcode 26 line. Proof/failure will come from the first `:shared:linkDebugFrameworkIosSimulatorArm64` run; a Kotlin bump or a side-by-side Xcode would be the fix.
2. **AGP patch pick: 9.3.1 vs 9.3.2/9.3.3.** The newer 9.3.x patches exist on Google Maven, but the KGP-verified table stops at 9.3.1 and CMP 1.12.2's CI matrix only tests AGP ≤ 9.2.0. Pinned the last patch inside the verified window; bump only after a fully green build.
3. **SQLDelight 2.4.x stricter MigrationFile versioning (#5730).** The exact `.sq`/`.sqm` validation semantics introduced in 2.4.0 were not confirmable from release notes alone. Proof: the first authored `.sqm` + `verifyCommonMainVehiCareDatabaseMigration` run in plan 01-03/01-05.
4. **material3 1.9.0 vs 1.12.0-alpha03.** Stable 1.9.0 is the plugin-embedded default and the pin; the CMP 1.12.2 release notes advertise the 1.12.0-alpha03 line. Revisit only when a stable material3 for the 1.12 line ships.
5. **Bare `compileSdk = 37` (without `minorApiLevel`) hash resolution is undocumented** for the KMP `androidLibrary` extension (it has no `compileSdkMinor` property). The contract mandates the block form everywhere; re-verify if anyone later switches to the property form.
