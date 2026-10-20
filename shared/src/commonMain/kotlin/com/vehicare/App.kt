package com.vehicare

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.vehicare.di.appModule
import com.vehicare.ui.navigation.BottomTabs
import com.vehicare.ui.navigation.ServiceEntryEditRoute
import com.vehicare.ui.navigation.SettingsRoute
import com.vehicare.ui.navigation.VehicleDetailRoute
import com.vehicare.ui.navigation.VehicleEditRoute
import com.vehicare.ui.navigation.VehicleListRoute
import com.vehicare.ui.service.ServiceEntryEditRoute as ServiceEntryEditScreen
import com.vehicare.ui.vehicles.VehicleDetailRoute as VehicleDetailScreen
import com.vehicare.ui.vehicles.VehicleEditRoute as VehicleEditScreen
import com.vehicare.ui.vehicles.VehicleListRoute as VehicleListScreen
import com.vehicare.ui.theme.VehiCareTheme
import com.vehicare.ui.settings.SettingsScreen
import io.github.vinceglb.filekit.coil.addPlatformFileSupport
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.module
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.snackbar_vehicle_deleted

/**
 * Fallback for hosts that supply no platform-specific bindings (iOS): an empty module handed to
 * `App` when the Koin graph needs no Android-only symbols.
 */
private val noPlatformModule: Module = module {}

/**
 * The one composable both platform hosts render: the Koin application, the single shared
 * navigation graph and the bottom tab shell, once — never nested per screen (T-01-24).
 *
 * The Android-only `platformModule` is a parameter (not a `commonMain` import — RESEARCH Pitfall 7):
 * the Android host passes the module built from its real application `Context`, other platforms
 * fall back to [noPlatformModule].
 */
@Composable
fun App(platformModule: Module = noPlatformModule) {
    // Teach Coil's process-wide image loader about FileKit's `PlatformFile`, so an image model
    // built from the stored relative photo path is a first-class Coil model (UI-SPEC Icon Contract
    // / RESEARCH Don't Hand-Roll: decode through Coil, never by hand). Registered here — the single
    // rendering path — so every screen that shows a photo gets it, before any image is requested.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context)
            .components { addPlatformFileSupport() }
            .build()
    }

    VehiCareTheme {
        KoinApplication(application = { modules(appModule, platformModule) }) {
            val navController = rememberNavController()

            // The bottom bar belongs to the two tab roots only. Detail and the Add/Edit form are
            // pushed sub-screens: each carries its own simple title bar with a back arrow (D-20),
            // and a tab bar underneath one would contradict the UI-SPEC destination table (the
            // shared bar is listed on `VehicleListRoute` and `SettingsRoute`) as well as the M3 /
            // iOS convention that tabs belong to the roots of a stack.
            val currentDestination = navController.currentBackStackEntryAsState().value?.destination
            val isTabRoot = currentDestination?.hasRoute(VehicleListRoute::class) == true ||
                currentDestination?.hasRoute(SettingsRoute::class) == true

            // The success message for a deletion lives here, not on the Detail screen that causes
            // it: that screen — and any Snackbar host of its own — is popped the moment the delete
            // succeeds, so a message hosted there would vanish with it. Hosting it on the shell and
            // showing it on the shell's scope is what lets "Vehicle deleted." appear over the list
            // (UI-SPEC §Interaction rules → Delete succeeds).
            val snackbarHostState = remember { SnackbarHostState() }
            val scope = rememberCoroutineScope()

            Scaffold(
                bottomBar = { if (isTabRoot) BottomTabs(navController) },
                snackbarHost = { SnackbarHost(snackbarHostState) },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = VehicleListRoute,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable<VehicleListRoute> {
                        VehicleListScreen(
                            onAddVehicle = { navController.navigate(VehicleEditRoute()) },
                            onOpenVehicle = { id -> navController.navigate(VehicleDetailRoute(id)) },
                        )
                    }
                    composable<SettingsRoute> {
                        SettingsScreen()
                    }
                    composable<VehicleEditRoute> { backStackEntry ->
                        val editRoute = backStackEntry.toRoute<VehicleEditRoute>()
                        VehicleEditScreen(
                            vehicleId = editRoute.id,
                            onDone = { navController.popBackStack() },
                        )
                    }
                    composable<VehicleDetailRoute> { backStackEntry ->
                        val detailRoute = backStackEntry.toRoute<VehicleDetailRoute>()
                        val deletedMessage = stringResource(Res.string.snackbar_vehicle_deleted)
                        VehicleDetailScreen(
                            vehicleId = detailRoute.id,
                            // Edit reuses the one Add/Edit form (D-08) — there is no second form —
                            // and returns here, where the streaming state already shows the change.
                            onEdit = { navController.navigate(VehicleEditRoute(detailRoute.id)) },
                            onBack = { navController.popBackStack() },
                            // A deletion that really happened (the ViewModel reports success only
                            // after the repository returned): the message is launched on the
                            // shell's scope first, so the pop to the list cannot take it away,
                            // then the stack returns to the list — Flow-driven, so the deleted
                            // card is already gone (T-01-62).
                            onDeleted = {
                                scope.launch { snackbarHostState.showSnackbar(deletedMessage) }
                                navController.popBackStack()
                            },
                        )
                    }
                    composable<ServiceEntryEditRoute> { backStackEntry ->
                        val route = backStackEntry.toRoute<ServiceEntryEditRoute>()
                        ServiceEntryEditScreen(
                            vehicleId = route.vehicleId,
                            entryId = route.entryId,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
