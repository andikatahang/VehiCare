---
phase: 01-foundation-vehicle-management
plan: 02
subsystem: ui
tags: [kotlin-multiplatform, compose-multiplatform, compose-resources, material3, theme, android, ios, xcode]

# Dependency graph
requires:
  - phase: 01-foundation-vehicle-management
    plan: 01
    provides: ":shared (com.android.kotlin.multiplatform.library) + :androidApp build foundation, libs.versions.toml version set, composeResources wiring, Xcode project scaffold (deployment target 16.0, embedAndSignAppleFrameworkForXcode phase), minSdk 26"
provides:
  - "The single shared rendering path: com.vehicare.App() rendered by both hosts (Android MainActivity, iOS ComposeUIViewController) with zero platform UI forks"
  - "Centralized English string set (49 strings) at shared/src/commonMain/composeResources/values/strings.xml — the only sanctioned copy source (D-11)"
  - "Three vehicle-type vector drawables (ic_vehicle_car/motorcycle/truck) as Compose Resources, 24dp artboard, fixed #FF1C1C1E fill"
  - "Theme tokens: Paper/Mist/Ink/Slate/Hairline/Danger, VehiCareLightColors, VehiCareTypography, VehiCareTheme (light-only)"
  - "iOS bundle id com.vehicare + FRAMEWORK_SEARCH_PATHS wiring; an opaque 1024px placeholder app icon"
affects: [01-04 (nav shell replaces the App() body), 01-05, 01-06, 01-07, 01-08, 01-09, 01-10 (all consume the strings, the three drawables and the theme tokens)]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Shared-resource-first UI: every user-facing string is read through stringResource(Res.string.*); no inline literal in a composable"
    - "Theme tokens declared once in ui/theme, applied unconditionally by VehiCareTheme — no dark branch, no dynamic color"
    - "Thin platform hosts: the host file mounts App() and holds no Compose UI of its own"
    - "iOS placeholder icons ship as one 1024px opaque PNG; Xcode derives every required size from it"

key-files:
  created:
    - shared/src/commonMain/composeResources/values/strings.xml
    - shared/src/commonMain/composeResources/drawable/ic_vehicle_car.xml
    - shared/src/commonMain/composeResources/drawable/ic_vehicle_motorcycle.xml
    - shared/src/commonMain/composeResources/drawable/ic_vehicle_truck.xml
    - shared/src/commonMain/kotlin/com/vehicare/ui/theme/Color.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/theme/Type.kt
    - shared/src/commonMain/kotlin/com/vehicare/ui/theme/Theme.kt
    - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png
  modified:
    - shared/src/commonMain/kotlin/com/vehicare/App.kt
    - androidApp/src/main/kotlin/com/vehicare/MainActivity.kt
    - shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt
    - iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json
    - iosApp/iosApp.xcodeproj/project.pbxproj
    - iosApp/Configuration/Config.xcconfig

key-decisions:
  - "Generated Compose Resources accessor package is `vehicare.shared.generated.resources` (build-derived, confirmed by generating accessors) — every later UI plan must import Res from there, not the composeapp name the research guessed"
  - "The deferred brand accent (D-21) is exposed as the single `Ink` value used for primary/secondary, so one value swap re-tints all six reserved surfaces with no layout change"
  - "iOS: bundle id `com.vehicare` is declared at target level in project.pbxproj (overriding Config.xcconfig) and the project carries FRAMEWORK_SEARCH_PATHS=$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME); Swift autolinks Shared, so no OTHER_LDFLAGS entry is needed"
  - "The D-22 iOS icon is one opaque 1024px PNG generated deterministically at the Android adaptive-icon's proportions; Xcode generates every derivative, so no per-size rasters are committed"
  - "Theme.kt's doc comment may not name the dark-mode/dynamic-color APIs: the light-only acceptance grep is literal and matches comments"

patterns-established:
  - "Compose Resources only: strings via Res.string.*, vectors via Res.drawable.* — one mechanism on both platforms"
  - "Ink-on-white light theme applied unconditionally (UI-SPEC C-8): no isSystemInDarkTheme consult, no Material You"
  - "Type scale held to 4 sizes (14/16/20/24sp) and 2 weights (400/500) by overriding only 5 M3 slots"
  - "Platform hosts stay one line of intent: setContent { App() } / ComposeUIViewController { App() }"

requirements-completed: [DAT-02]

# Metrics
duration: 17min
completed: 2026-10-18
---

# Phase 1 Plan 2: Shared UI Foundation (strings, icons, theme, platform hosts) Summary

**One `App()` composable renders ink-on-white on both platforms, with all 49 user-facing strings read from a single Compose Resources file and the three vehicle-type icons shipped as shared vectors**

## Performance

- **Duration:** 17 min
- **Started:** 2026-10-18T15:57:00Z
- **Completed:** 2026-10-18T16:14:00Z
- **Tasks:** 3
- **Files modified:** 15 (8 created, 6 modified, 1 deleted)

## Accomplishments

- **The one-and-only rendering path exists.** `com.vehicare.App()` is declared once in `commonMain`; `MainActivity` mounts it with `setContent { App() }` and `MainViewController()` wraps it in a `ComposeUIViewController`. The grep for composables outside `commonMain` returns nothing — D-17 holds with zero platform UI duplication.
- **Copy can no longer be invented.** `strings.xml` transcribes all 49 UI-SPEC strings verbatim (verified byte-for-byte against the spec: the sorted name list diffs clean, `field_required_marker` = `*`, `value_empty` = U+2014, four `%1\$s` placeholders, and no `Submit`/`OK`/`Save`/`Done`/`Yes` element). `App.kt` reads `Res.string.app_name`; an inline-literal scan of the file finds none.
- **The three icons the pinned icon set cannot supply now exist** as 24dp single-colour vectors (`#FF1C1C1E`, no `tint`) in Compose Resources, resolving them to `Res.drawable.ic_vehicle_car|motorcycle|truck` for plans 01-04 … 01-10.
- **The theme is real and light-only.** Six tokens plus `VehiCareLightColors`/`VehiCareTypography`/`VehiCareTheme`; the dark-mode/dynamic-color grep over `shared/src/commonMain` returns zero matches, and the type file declares exactly 14/16/20/24sp at 400/500.
- **Both platforms compile and link, verified end-to-end.** `:androidApp:assembleDebug` and `:shared:linkDebugFrameworkIosSimulatorArm64` both exit 0, and an actual `xcodebuild` simulator build **succeeded**, producing `VehiCare.app` with `CFBundleIdentifier = com.vehicare`, `MinimumOSVersion = 16.0`, a bundled `compose-resources` folder and an `Assets.car` whose `AppIcon` rendition resolves to `AppIcon-1024.png`.

## Task Commits

Each task was committed atomically:

1. **Task 1: Author the centralized string set and the three vehicle-type vector drawables** — `7b9e2a6` (feat)
2. **Task 2: Author the theme tokens (color + typography)** — `c0cebbf` (feat)
3. **Task 3: Author the platform hosts, the root composable, and the Xcode project** — `77ede11` (feat)

**Plan metadata:** this file is carried by the `docs(01-02): complete …` commit.

## Files Created/Modified

**Created**
- `shared/src/commonMain/composeResources/values/strings.xml` — all 49 D-11 strings, English only, implementation-ready copy for the whole phase.
- `shared/src/commonMain/composeResources/drawable/ic_vehicle_car.xml` / `ic_vehicle_motorcycle.xml` / `ic_vehicle_truck.xml` — 24dp vector silhouettes, single `#FF1C1C1E` path each.
- `shared/src/commonMain/kotlin/com/vehicare/ui/theme/Color.kt` — `Paper`/`Mist`/`Ink`/`Slate`/`Hairline`/`Danger` + `VehiCareLightColors`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/theme/Type.kt` — `VehiCareTypography`, five M3 slots, `FontFamily.Default`.
- `shared/src/commonMain/kotlin/com/vehicare/ui/theme/Theme.kt` — `VehiCareTheme`, applied unconditionally.
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png` — D-22 placeholder (see "iOS icon provenance").

**Modified**
- `shared/src/commonMain/kotlin/com/vehicare/App.kt` — the wizard demo body is replaced by `VehiCareTheme { Surface { centred Text(stringResource(Res.string.app_name)) } }`; name and zero-parameter signature frozen for plan 01-04.
- `androidApp/src/main/kotlin/com/vehicare/MainActivity.kt` — thin host; the wizard's `@Preview` composable is gone so the host holds no Compose UI of its own.
- `shared/src/iosMain/kotlin/com/vehicare/MainViewController.kt` — explicit `UIViewController` return type per the plan's interface contract.
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/Contents.json` — single-entry `single-size` set pointing at `AppIcon-1024.png` (the wizard's dark/tinted placeholder entries are gone; Phase 1 is light-only).
- `iosApp/iosApp.xcodeproj/project.pbxproj` — `PRODUCT_BUNDLE_IDENTIFIER = com.vehicare` and `FRAMEWORK_SEARCH_PATHS` added to both target configurations.
- `iosApp/Configuration/Config.xcconfig` — bundle id aligned to `com.vehicare`.

**Deleted**
- `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/app-icon-1024.png` — the wizard's alpha-bearing raster, replaced by `AppIcon-1024.png` and unreferenced after the `Contents.json` rewrite (see deviations).

**Unchanged, verified in place** (already in the required shape from 01-01's adopted wizard scaffold)
- `iosApp/iosApp/iOSApp.swift` — `@main struct iOSApp: App` with `WindowGroup { ContentView() }`.
- `iosApp/iosApp/ContentView.swift` — `UIViewControllerRepresentable` whose `makeUIViewController` returns `MainViewControllerKt.MainViewController()`.

## iOS icon provenance (D-22, recorded for reproducibility)

The 1024×1024 PNG was generated — not hand-drawn and not copied — by a throwaway `python3` script (Pillow) kept outside the repo and deleted afterwards:

- Reproduces the Android adaptive-icon artwork from plan 01-01 (`androidApp/src/main/res/drawable/ic_launcher_foreground.xml`: a "V" monogram, `#FF1C1C1E`, round caps/joins, path `M34,38 L54,74 L74,38`, stroke 10 on the 108dp canvas) at the **same proportional geometry** on the iOS canvas: factor `1024/108`, so the mark spans x 322…703, y 360…702 with a ~95px stroke.
- Drawn at 4× (4096px) and downsampled with LANCZOS so the strokes are anti-aliased, then saved as **RGB (no alpha channel)** because App Store icons must be opaque.
- Verified: `sips -g pixelWidth -g pixelHeight -g hasAlpha` → `1024`, `1024`, `hasAlpha: no`; `file` reports 8-bit RGB (no RGBA). The simulator build derived `AppIcon60x60@2x.png` and `AppIcon76x76@2x~ipad.png` from it, so no per-size asset needed authoring.

## Threat Model Compliance

| Threat | Disposition applied |
|--------|---------------------|
| T-01-08 (tampering, resource files) | All four resource files are plain in-repo XML: no `<a href>`, no script, no remote reference; the three drawables are single paths with a fixed `fillColor` and no `tint` (verified by grep). |
| T-01-09 (info disclosure, iOS host) | `ContentView` only constructs the shared controller; nothing is stored, and no URL scheme, document-picker entitlement or `FileProvider` was added. |
| T-01-10 (spoofing, Android intent filter) | No manifest change in this plan; only the pre-existing LAUNCHER activity is exported. |
| T-01-11 (tampering, theme tokens) | Light-only enforced and grep-verified: zero occurrences of the dark-mode/dynamic-color APIs anywhere in `shared/src/commonMain`. |
| T-01-12 (repudiation, copy drift) | Every rendered string resolves through `stringResource(Res.string.*)`; `App.kt` contains no user-facing literal. |
| T-01-SC (dependency installs) | No package was installed and `gradle/libs.versions.toml` is untouched — the plan added resources and sources only. |

## Decisions Made

1. **`vehicare.shared.generated.resources` is the real `Res` package** (the plan and RESEARCH flagged it as build-derived; `com.vehicare.composeapp.generated.resources` was the guess). Confirmed by generating the accessors: 49 `Res.string.*` and 3 `Res.drawable.ic_vehicle_*` declarations land in that package. Every later UI plan imports from there.
2. **The accent stays a token, not a colour.** `primary`/`secondary` are `Ink`, so D-21's deferred brand choice remains a single-value edit that re-tints FAB, CTA, submit, selected tab, focused field and the required marker at once.
3. **iOS bundle id and search path live in `project.pbxproj`.** The plan required the pbxproj to carry `com.vehicare`; setting it at target level (both Debug and Release) also makes the app identity explicit rather than inherited, and `Config.xcconfig` was aligned so there is only one truth.
4. **`FRAMEWORK_SEARCH_PATHS` points at `$(SRCROOT)/../shared/build/xcode-frameworks/$(CONFIGURATION)/$(SDK_NAME)`** — the path `embedAndSignAppleFrameworkForXcode` actually populates (confirmed: the build produced `Debug/iphonesimulator27.0/Shared.framework`). `OTHER_LDFLAGS` was deliberately **not** added: the verified simulator build links `Shared` without it.
5. **The old wizard raster was deleted rather than left orphaned**, so the asset catalog has no unassigned child and the repo carries no unused 67KB RGBA image.
6. **`Theme.kt`'s comment avoids naming `isSystemInDarkTheme()`/`dynamic*ColorScheme()`** — the light-only acceptance grep is literal, and a comment would have tripped it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] `MainActivity` tripped the light-only/host grep via its leftover `@Preview` composable**
- **Found during:** Task 3 (platform hosts)
- **Issue:** The adopted wizard scaffold had a `@Preview @Composable fun AppAndroidPreview()` in `MainActivity.kt`. The plan's `<action>` says "Do not add a `@Preview`" and describes a host that holds no UI of its own; leaving it also kept a second `@Composable` in the Android module.
- **Fix:** Removed the preview function and its two imports; `MainActivity.kt` is now `enableEdgeToEdge()` + `super.onCreate` + `setContent { App() }`.
- **Files modified:** `androidApp/src/main/kotlin/com/vehicare/MainActivity.kt`
- **Verification:** `grep -rn '@Composable' shared/src/androidMain shared/src/iosMain androidApp/src` → no matches; `:androidApp:assembleDebug` exits 0.
- **Committed in:** `77ede11`

**2. [Rule 3 - Blocking] The theme doc comment matched the dark-mode acceptance grep**
- **Found during:** Task 2 acceptance-criteria gate
- **Issue:** `Theme.kt`'s KDoc named `isSystemInDarkTheme()`, so `grep -rE 'isSystemInDarkTheme|dynamicLightColorScheme|dynamicDarkColorScheme' shared/src/commonMain/kotlin/com/vehicare/ui/theme/` returned a match and printed `DARK_OR_DYNAMIC_FOUND` — a failing criterion for a file with no such call.
- **Fix:** Reworded the comment to describe the rule without naming the APIs ("no dark-mode branch and no dynamic color"); the code was already correct.
- **Files modified:** `shared/src/commonMain/kotlin/com/vehicare/ui/theme/Theme.kt`
- **Verification:** the same grep now prints `LIGHT_ONLY_OK` for `ui/theme/` **and** for all of `shared/src/commonMain`; `:shared:compileAndroidMain` exits 0. (Noted in the STATE decision log so later plans do not reintroduce it.)
- **Committed in:** `c0cebbf`

**3. [Rule 2 - Missing Critical] iOS app identity and framework search path were absent from the Xcode target**
- **Found during:** Task 3 (Xcode project configuration)
- **Issue:** The adopted project carried the wizard's `com.example.vehicare.VehiCare$(TEAM_ID)` bundle id and declared no framework search path, while the plan requires bundle id `com.vehicare` "plus a framework search path pointing at the built `Shared.framework`".
- **Fix:** Added `PRODUCT_BUNDLE_IDENTIFIER = com.vehicare` and `FRAMEWORK_SEARCH_PATHS` (at the `embedAndSignAppleFrameworkForXcode` output location) to both target configurations, and aligned `Config.xcconfig` — a file outside the plan's `files_modified` list, changed so the project-level base config cannot contradict the target.
- **Files modified:** `iosApp/iosApp.xcodeproj/project.pbxproj`, `iosApp/Configuration/Config.xcconfig`
- **Verification:** `xcodebuild … build` succeeded and logged `--bundle-identifier com.vehicare --deployment-target 16.0`; the built `Info.plist` reports `CFBundleIdentifier = com.vehicare` and `MinimumOSVersion = 16.0`.
- **Committed in:** `77ede11`

**4. [Rule 2 - Missing Critical] The app icon asset could not satisfy the semantic criteria with the wizard's raster**
- **Found during:** Task 3 (D-22 placeholder icon)
- **Issue:** The existing `app-icon-1024.png` had `hasAlpha: yes` (App-Store-invalid) and did not match the artwork intent of the Android placeholder; the plan also mandates the filename `AppIcon-1024.png` and an alpha-free asset.
- **Fix:** Generated `AppIcon-1024.png` (opaque ink "V" on white, same proportions as the Android adaptive icon), rewrote `Contents.json` to the single-entry `single-size` form, and deleted the now-unreferenced wizard raster.
- **Files modified:** `iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/{AppIcon-1024.png,Contents.json}`, deleted `app-icon-1024.png`
- **Verification:** `sips` reports 1024×1024 with `hasAlpha: no`; the appiconset holds exactly `Contents.json` + `AppIcon-1024.png` (no per-size derivatives); `assetutil --info Assets.car` finds `AppIcon` with rendition `AppIcon-1024.png`, and Xcode derived `AppIcon60x60@2x.png` / `AppIcon76x76@2x~ipad.png` itself.
- **Committed in:** `77ede11`

---

**Total deviations:** 4 auto-fixed (3 blocking-class, 1 missing-critical). No Rule 4 (architectural) change was needed and no scope was added beyond the plan's own intent.
**Impact on plan:** All four were required for the plan's acceptance criteria to hold literally (deviation 2), for the hosts to match the plan's own description (1), or for the iOS app identity, framework resolution and App-Store-valid placeholder that the plan explicitly asked for (3, 4). The one file touched outside `files_modified` is `iosApp/Configuration/Config.xcconfig`, changed only so the bundle id has a single source of truth.

## Issues Encountered

- **The stale `generateComposeResClass` output is not the accessor source.** The first run of that task left `Drawable0.commonMain.kt` containing only `compose_multiplatform`; the three new vectors and the 49 strings appeared after `:shared:generateResourceAccessorsForCommonMain` ran. Anyone verifying "the resources are in the build" must check the *accessors*, not just `Res.kt`. Not a defect — noted so later plans do not mistake a stale accessor file for a missing resource.
- **Human check deferred (not skipped).** The plan's `<verify>` human-check — launching the app on an Android API 26+ device/emulator and an iOS 16+ simulator and comparing the two screens — was **not** executed by this executor. `workflow.human_verify_mode` is `end-of-phase`, so runtime UAT belongs to `/gsd-verify-work`. What is verified here is everything up to launch: both platform build targets compile and link, the iOS app bundle builds with the right identity, the shared `compose-resources` folder ships inside it, and there is exactly one composable path. Commands for the verifier: `./gradlew :androidApp:installDebug` (API 26+ emulator attached) and `open iosApp/iosApp.xcodeproj` → Run on an iOS 16+ simulator; both must show a white screen with "VehiCare" and no permission prompt.

## Known Stubs

None. Every asset added is a real artefact (`strings.xml`, three complete vectors, the icon) — no placeholder text, no mock data source, no unwired component. `App.kt`'s single centred label is the plan's deliberate thinnest possible proof of the shared layer, and plan 01-04 replaces its body with the `KoinApplication` + `NavHost` shell by design.

## Deferred Issues (out of scope for this plan)

- The wizard's `Greeting.kt` / `GreetingUtil.kt` / `Platform.kt` and `composeResources/drawable/compose-multiplatform.xml` are now unreferenced (the old `App.kt` was their only consumer). Harmless dead scaffold; a later plan or the phase-level cleanup should remove them rather than each executor re-deciding.
- `:shared:linkDebugFrameworkIosSimulatorArm64` emits a pre-existing warning: *"Cannot infer a bundle ID from packages of source files … use -Xbinary=bundleId"*. It predates this plan (framework config from 01-01) and is not surfaced by any acceptance criterion; fix belongs with the framework/bundle-ID work, not here.

## User Setup Required

None — no external service, environment variable or credential is involved in this plan.

## Next Phase Readiness

- **Ready for plan 01-03** (WAL schema, domain models, driver factories, DI, repository + mapper): the version set, resource pipeline and both platform hosts are proven, so 01-03 adds no UI risk to the build.
- **Ready for plan 01-04** (nav shell): `fun App()` exists with a frozen signature; its body is the single edit point, and the 49 strings already include every shell/tab/title string the nav shell needs.
- **Consumers are unblocked**: `Res.string.*` (49), `Res.drawable.ic_vehicle_car|motorcycle|truck`, `VehiCareTheme` and the six colour tokens all resolve from `commonMain` today, verified by a real Android APK build, a real Kotlin/Native framework link and a real Xcode simulator build.
- **Open item for the verifier:** runtime launch on both platforms (see Issues Encountered).

---

## Self-Check: PASSED

Every artifact claimed above was re-checked against disk and git after the summary was written:

- **Files created (8):** all present — `strings.xml`, the three `ic_vehicle_*.xml` drawables, `Color.kt`, `Type.kt`, `Theme.kt`, `AppIcon-1024.png`.
- **Files modified (6):** all present — `App.kt`, `MainActivity.kt` (`androidApp/src/main/kotlin/com/vehicare/`), `MainViewController.kt` (`shared/src/iosMain/kotlin/com/vehicare/`), `Contents.json`, `project.pbxproj`, `Config.xcconfig`.
- **Deletion (1):** `app-icon-1024.png` is gone and still unreferenced; the deletion is intentional and documented (deviation 4).
- **Commits:** `7b9e2a6`, `c0cebbf`, `77ede11` all exist and each is a `feat(01-02)` task commit; the metadata commit below exists and the working tree is clean.
- **Authorship:** every commit in this plan is authored by `andikatahang <mandikatahang@gmail.com>` with no `Co-Authored-By` trailer.

---

*Phase: 01-foundation-vehicle-management*
*Completed: 2026-10-18*
