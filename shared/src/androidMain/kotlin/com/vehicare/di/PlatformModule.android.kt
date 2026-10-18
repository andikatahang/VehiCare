package com.vehicare.di

import android.content.Context
import com.vehicare.data.db.DriverFactory
import com.vehicare.data.db.createDriverFactory
import com.vehicare.data.db.defaultDatabaseName
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
 * @param androidContext the application context; only `applicationContext` is kept so no
 *   Activity is ever retained by the graph.
 */
fun platformModule(androidContext: Context): Module = module {
    single<Context> { androidContext.applicationContext }
    single<DriverFactory> { createDriverFactory(get<Context>(), defaultDatabaseName) }
}
