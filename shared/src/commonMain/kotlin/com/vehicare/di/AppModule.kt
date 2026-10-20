package com.vehicare.di

import com.vehicare.data.ServiceEntryRepository
import com.vehicare.data.ServiceEntryRepositoryImpl
import com.vehicare.data.VehicleRepository
import com.vehicare.data.VehicleRepositoryImpl
import com.vehicare.data.db.DatabaseFactory
import com.vehicare.data.db.DriverFactory
import com.vehicare.data.db.createDriverFactory
import com.vehicare.db.VehiCareDatabase
import com.vehicare.ui.service.ServiceEntryEditViewModel
import com.vehicare.ui.vehicles.VehicleDetailViewModel
import com.vehicare.ui.vehicles.VehicleEditViewModel
import com.vehicare.ui.vehicles.VehicleListViewModel
import com.vehicare.util.PhotoStorage
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The shared Koin graph: driver → database → repository.
 *
 * The graph grows with the plans that author each type: ViewModels are registered by the plan
 * that writes them (01-04/01-07/01-09) because binding a type that does not exist yet cannot
 * compile.
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
    // Deleting a vehicle removes its photo file through the real storage seam (D-07), replacing the
    // no-op default plan 01-03 left in place while the write side did not exist yet (T-01-18).
    single<VehicleRepository> {
        VehicleRepositoryImpl(get(), photoDeleter = PhotoStorage::delete)
    }
    // The service entry repository with the real photo deleter (02-03) — this is the phase's only
    // repository binding; 02-01 wires nothing (its impl default deleter stays `{}` for tests only)
    // and 02-04/05/06 add only their ViewModel factories.
    single<ServiceEntryRepository> {
        ServiceEntryRepositoryImpl(get(), photoDeleter = PhotoStorage::delete)
    }
    viewModel { VehicleListViewModel(get()) }
    // The form's id comes from the route: `parametersOf(vehicleId)` at the call site, destructured
    // here. 0L is the add case; anything else loads that row for edit.
    viewModel { (vehicleId: Long) -> VehicleEditViewModel(get(), vehicleId) }
    // The Detail destination's id arrives the same way the form's does, so both route-id
    // ViewModels are built by one mechanism — a typed `Long` from the route, never a string key.
    viewModel { (vehicleId: Long) -> VehicleDetailViewModel(get(), vehicleId) }
    // Service entry edit form: vehicleId + entryId from the route (entryId = 0L means add).
    viewModel { (vehicleId: Long, entryId: Long) -> ServiceEntryEditViewModel(get(), vehicleId, entryId) }
}
