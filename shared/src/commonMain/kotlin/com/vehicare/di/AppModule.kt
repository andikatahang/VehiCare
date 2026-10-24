package com.vehicare.di

import com.vehicare.data.ReminderRepository
import com.vehicare.data.ReminderRepositoryImpl
import com.vehicare.data.ServiceEntryRepository
import com.vehicare.data.ServiceEntryRepositoryImpl
import com.vehicare.data.VehicleRepository
import com.vehicare.data.VehicleRepositoryImpl
import com.vehicare.data.db.DatabaseProvider
import com.vehicare.db.VehiCareDatabase
import com.vehicare.ui.service.ServiceEntryDetailViewModel
import com.vehicare.ui.service.ServiceEntryEditViewModel
import com.vehicare.ui.service.ServiceHistoryViewModel
import com.vehicare.ui.service.ServiceSummaryViewModel
import com.vehicare.ui.vehicles.VehicleDetailViewModel
import com.vehicare.ui.vehicles.VehicleEditViewModel
import com.vehicare.ui.vehicles.VehicleListViewModel
import com.vehicare.util.PhotoStorage
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The shared Koin graph: database holder → repository → ViewModel.
 *
 * The graph no longer owns driver construction. [DatabaseProvider] holds the process's single
 * [VehiCareDatabase], and each platform entry point installs it before Compose starts:
 * `VehiCareApp.onCreate` on Android, `MainViewController` on iOS. That is not a stylistic choice —
 * SQLDelight's change-notification listener registry lives on the **driver instance**, so a second
 * database over the same file would write successfully while the UI's `asFlow()` queries never
 * emitted, leaving due states stale until a restart (03-RESEARCH Pattern 1 / Pitfall 1). The holder
 * is also what lets the future Android `CoroutineWorker` — which has no Koin container at all —
 * read the same driver the UI observes (D-63).
 *
 * The graph grows with the plans that author each type: ViewModels are registered by the plan
 * that writes them (01-04/01-07/01-09) because binding a type that does not exist yet cannot
 * compile.
 */
val appModule = module {
    single<VehiCareDatabase> { DatabaseProvider.require() }
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
    // The reminder rules the Reminders screen, the Detail summary row, the dashboard and the
    // notification worker all read (plan 03-03). Plan 07 replaces this binding with the
    // notification-refresh hook the third constructor parameter exists for:
    //   onRulesChanged = { _, ruleIds ->
    //       val scheduler = get<NotificationScheduler>()
    //       scheduler.cancel(ruleIds)
    //       scheduler.evaluateNow()
    //   }
    single<ReminderRepository> { ReminderRepositoryImpl(get()) }
    viewModel { VehicleListViewModel(get()) }
    // The form's id comes from the route: `parametersOf(vehicleId)` at the call site, destructured
    // here. 0L is the add case; anything else loads that row for edit.
    viewModel { (vehicleId: Long) -> VehicleEditViewModel(get(), vehicleId) }
    // The Detail destination's id arrives the same way the form's does, so both route-id
    // ViewModels are built by one mechanism — a typed `Long` from the route, never a string key.
    viewModel { (vehicleId: Long) -> VehicleDetailViewModel(get(), vehicleId) }
    // Service entry edit form: vehicleId + entryId from the route (entryId = 0L means add).
    viewModel { (vehicleId: Long, entryId: Long) -> ServiceEntryEditViewModel(get(), vehicleId, entryId) }
    // Read-only entry detail: entryId from the route.
    viewModel { (entryId: Long) -> ServiceEntryDetailViewModel(get(), entryId) }
    // The history list: one vehicleId from the route.
    viewModel { (vehicleId: Long) -> ServiceHistoryViewModel(get(), vehicleId) }
    // The Detail's summary row: the same route id, one aggregate read.
    viewModel { (vehicleId: Long) -> ServiceSummaryViewModel(get(), vehicleId) }
}
