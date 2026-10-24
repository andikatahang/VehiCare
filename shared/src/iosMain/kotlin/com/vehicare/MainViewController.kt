package com.vehicare

import androidx.compose.ui.window.ComposeUIViewController
import com.vehicare.data.db.DatabaseFactory
import com.vehicare.data.db.DatabaseProvider
import com.vehicare.data.db.createDriverFactory
import com.vehicare.data.settings.SettingsProvider
import com.vehicare.data.settings.createSettingsStore
import com.vehicare.di.iosPlatformModule
import platform.UIKit.UIViewController

/**
 * The iOS host: wraps the shared [App] composable in a `UIViewController` for SwiftUI to present.
 *
 * Also the iOS construction point for the process-wide database (D-63): the holder is installed
 * before the controller is built, so `appModule`'s `DatabaseProvider.require()` resolves the same
 * driver every repository observes. It stays a plain function so SwiftUI's `makeUIViewController`
 * can call it directly.
 *
 * No platform-specific Compose theme or UIKit styling is applied here — that would fork the UI (D-17).
 */
fun MainViewController(): UIViewController {
    DatabaseProvider.initialize(DatabaseFactory(createDriverFactory().createDriver()))
    // Exactly once per process, mirroring the database holder above (261024-u9g / D-63's rule).
    SettingsProvider.initialize(createSettingsStore())
    return ComposeUIViewController { App(iosPlatformModule()) }
}
