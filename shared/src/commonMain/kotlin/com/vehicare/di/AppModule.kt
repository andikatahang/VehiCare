package com.vehicare.di

import com.vehicare.data.db.DatabaseFactory
import com.vehicare.data.db.DriverFactory
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
import org.koin.dsl.module

/**
 * The shared Koin graph.
 *
 * The graph grows with the plans that author each type: ViewModels are registered by the plan
 * that writes them (01-04/01-07/01-09) because binding a type that does not exist yet cannot
 * compile. Today it wires driver → database, and plan 01-03 adds the repository binding.
 *
 * Driver construction stays platform-owned: Android loads its platform module which registers
 * a [DriverFactory] built with the real application `Context`; platforms without such a module
 * (iOS) fall back to the context-less platform entry point.
 */
val appModule = module {
    single {
        val driverFactory = getOrNull<DriverFactory>() ?: createDriverFactory()
        DatabaseFactory(driverFactory.createDriver())
    }
    single<VehiCareDatabase> { get<DatabaseFactory>().database }
}
