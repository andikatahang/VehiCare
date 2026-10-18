package com.vehicare

import android.content.Context
import org.koin.mp.KoinPlatformTools

/**
 * Android actual: the `versionName` of the installed package — the only `versionName` in this
 * build is declared by `androidApp/build.gradle.kts`, so `:shared` reads it at runtime from the
 * app's own package info.
 *
 * The [Context] is read from the Koin graph (registered by `platformModule`, which receives the
 * application context from the host activity), so no hidden API and no extra wiring is needed.
 * Lazy because Koin starts once inside `App()` — the Settings screen reads this well after start.
 */
actual val appVersionName: String by lazy {
    val context = KoinPlatformTools.defaultContext().get().get<Context>()
    context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
}
