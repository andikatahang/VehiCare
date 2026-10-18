package com.vehicare

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/**
 * The iOS host: wraps the shared [App] composable in a `UIViewController` for SwiftUI to present.
 *
 * No platform-specific Compose theme or UIKit styling is applied here — that would fork the UI (D-17).
 */
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
