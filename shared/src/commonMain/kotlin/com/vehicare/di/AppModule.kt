package com.vehicare.di

import com.vehicare.domain.repository.ReminderRepository
import com.vehicare.domain.repository.ServiceEntryRepository
import com.vehicare.domain.repository.VehicleRepository
import com.vehicare.data.repository.ReminderRepositoryImpl
import com.vehicare.data.repository.ServiceEntryRepositoryImpl
import com.vehicare.data.repository.VehicleRepositoryImpl
import com.vehicare.data.db.DatabaseProvider
import com.vehicare.db.VehiCareDatabase
import com.vehicare.notifications.NotificationScheduler
import com.vehicare.ui.reminders.ReminderEditViewModel
import com.vehicare.ui.reminders.ReminderSummaryViewModel
import com.vehicare.ui.reminders.RemindersViewModel
import com.vehicare.ui.service.ServiceEntryDetailViewModel
import com.vehicare.ui.service.ServiceEntryEditViewModel
import com.vehicare.ui.service.ServiceHistoryViewModel
import com.vehicare.ui.service.ServiceSummaryViewModel
import com.vehicare.ui.vehicles.VehicleDetailViewModel
import com.vehicare.ui.vehicles.VehicleEditViewModel
import com.vehicare.ui.vehicles.VehicleListViewModel
import com.vehicare.ui.settings.SettingsViewModel
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
    // The settings store binding exists for ViewModels only (261024-u9g): the delivery paths read
    // `SettingsProvider.require()` directly, because they are built outside this graph and could
    // not resolve a Koin binding anyway — the provider is their holder, exactly as DatabaseProvider
    // is for the Android worker.
    single<com.vehicare.data.settings.SettingsStore> { com.vehicare.data.settings.SettingsProvider.require() }
    // Deleting a vehicle removes its photo file through the real storage seam (D-07), replacing the
    // no-op default plan 01-03 left in place while the write side did not exist yet (T-01-18).
    // A deleted vehicle must not leave a delivered or scheduled notification behind, so the hook
    // cancels every rule id the cascade removed before re-evaluating through the same platform
    // seam the iOS sweep uses to clear and re-schedule its own pending requests (D-64).
    single<VehicleRepository> {
        VehicleRepositoryImpl(
            get(),
            photoDeleter = PhotoStorage::delete,
            onVehicleDeleted = { _, ruleIds ->
                val scheduler = get<NotificationScheduler>()
                scheduler.cancel(ruleIds)
                scheduler.evaluateNow()
            },
        )
    }
    // The service entry repository with the real photo deleter (02-03) — this is the phase's only
    // repository binding; 02-01 wires nothing (its impl default deleter stays `{}` for tests only)
    // and 02-04/05/06 add only their ViewModel factories.
    single<ServiceEntryRepository> {
        ServiceEntryRepositoryImpl(get(), photoDeleter = PhotoStorage::delete)
    }
    // The reminder rules the Reminders screen, the Detail summary row, the dashboard and the
    // notification worker all read (plan 03-03). The graph cancels the changed rule's
    // delivered/pending notification and re-arms delivery after a user edit through the platform
    // seam. The delivery orchestrator is deliberately not bound in this graph — both delivery
    // paths (the Android worker, the iOS sweep) build their own instance through the
    // notifications module's own factory object, so a third construction point here would be an
    // untested path.
    single<ReminderRepository> {
        ReminderRepositoryImpl(
            get(),
            onRulesChanged = { _, ruleIds ->
                val scheduler = get<NotificationScheduler>()
                scheduler.cancel(ruleIds)
                scheduler.evaluateNow()
            },
        )
    }
    // The home dashboard reads the same reminder repository the Reminders screen uses — one
    // aggregated evaluation join, never a second reminder read path (D-57, DSH-01).
    viewModel { VehicleListViewModel(get(), get()) }
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
    // The Reminders list: one vehicleId from the route, three flat reads behind one state flow.
    viewModel { (vehicleId: Long) -> RemindersViewModel(vehicleId, get()) }
    // The reminder editor: vehicleId + ruleId from the route (ruleId = 0L means add). The
    // editor's learned-chip source is the entry repository's own flow, not a second reminder read.
    viewModel { (vehicleId: Long, ruleId: Long) ->
        ReminderEditViewModel(get(), get<ServiceEntryRepository>().observeLearnedTypes(), vehicleId, ruleId)
    }
    // The Detail's Reminders summary row: the same route id, three flat reads joined once.
    viewModel { (vehicleId: Long) -> ReminderSummaryViewModel(get(), vehicleId) }
    // The Settings screen (261024-u9g, D-1): the interval setting write path — store + the
    // fire-and-forget re-arm seam, both resolved from the graph.
    viewModel { SettingsViewModel(get(), get<NotificationScheduler>()) }
}
