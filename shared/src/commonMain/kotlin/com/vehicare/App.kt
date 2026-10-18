package com.vehicare

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vehicare.di.appModule
import com.vehicare.ui.navigation.BottomTabs
import com.vehicare.ui.navigation.SettingsRoute
import com.vehicare.ui.navigation.VehicleDetailRoute
import com.vehicare.ui.navigation.VehicleEditRoute
import com.vehicare.ui.navigation.VehicleListRoute
import com.vehicare.ui.vehicles.VehicleDetailRoute as VehicleDetailScreen
import com.vehicare.ui.vehicles.VehicleEditRoute as VehicleEditScreen
import com.vehicare.ui.vehicles.VehicleListRoute as VehicleListScreen
import com.vehicare.ui.theme.VehiCareTheme
import com.vehicare.ui.settings.SettingsScreen
import org.koin.compose.KoinApplication
import org.koin.core.module.Module
import org.koin.dsl.module

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
    VehiCareTheme {
        KoinApplication(application = { modules(appModule, platformModule) }) {
            val navController = rememberNavController()

            Scaffold(
                bottomBar = { BottomTabs(navController) },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = VehicleListRoute,
                    modifier = Modifier.padding(innerPadding),
                ) {
                    composable<VehicleListRoute> {
                        VehicleListScreen()
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
                        VehicleDetailScreen(vehicleId = detailRoute.id)
                    }
                }
            }
        }
    }
}
