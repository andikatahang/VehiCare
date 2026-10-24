package com.vehicare.di

import android.content.Context
import com.vehicare.notifications.NotificationScheduler
import com.vehicare.notifications.createNotificationScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Android-only bindings that cannot live in shared code.
 *
 * The `Application` context arrives as a parameter instead of Koin's `androidContext()` module
 * DSL because Koin 4.2.2 exposes `androidContext`/`androidLogger` only as `KoinApplication`
 * extensions, not as module declarations. Passing the context in keeps the module self-contained
 * and lets the host hand it straight to `App(...)`.
 *
 * This module no longer constructs the database: `VehiCareApp.onCreate` installs the process-wide
 * holder before Compose starts and `appModule` reads it. A `DriverFactory` binding here would be
 * dead — nothing consumes it any more — and a dead binding that still calls `createDriverFactory`
 * is a second driver waiting to happen (03-PATTERNS C-4).
 *
 * @param androidContext the application context; only `applicationContext` is kept so no
 *   Activity is ever retained by the graph.
 */
fun platformModule(androidContext: Context): Module = module {
    single<Context> { androidContext.applicationContext }
    // The scheduler is platform-bound because only Android needs a `Context` to post.
    single<NotificationScheduler> { createNotificationScheduler(get<Context>()) }
}
