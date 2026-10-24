package com.vehicare.di

import com.vehicare.notifications.NotificationScheduler
import com.vehicare.notifications.createNotificationScheduler
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * iOS needs no `Context`, so this is the whole platform graph — the same parameter Android fills
 * with `platformModule(applicationContext)`. Passed to `App(...)` from `MainViewController`.
 */
fun iosPlatformModule(): Module = module {
    single<NotificationScheduler> { createNotificationScheduler() }
}
