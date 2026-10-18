package com.vehicare.ui.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Paper
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.ic_vehicle_car
import vehicare.shared.generated.resources.tab_settings
import vehicare.shared.generated.resources.tab_vehicles

/**
 * The shared two-item bottom tab bar (UI-SPEC Component Dimensions): a 1dp hairline over a
 * `NavigationBar` filled with [Paper].
 *
 * Selection follows the current nav destination and tapping a tab navigates to its route with
 * `launchSingleTop` + `popUpTo(start) { saveState }` + `restoreState`, so selection stays in sync
 * in both directions and each tab's state survives switching. The icon carries no
 * `contentDescription` — the label is the accessibility name — and selection is additionally
 * visible as the M3 indicator pill (Icon-only state is never the sole cue).
 */
@Composable
fun BottomTabs(navController: NavHostController) {
    val currentDestination = navController.currentBackStackEntryAsState().value?.destination

    // The theme reads Ink as `primary`, so the indicator pill is the reserved accent surface and
    // the selected icon sits on it in `onPrimary` white; the defaults would draw a lavender pill
    // outside this palette.
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
        indicatorColor = MaterialTheme.colorScheme.primary,
    )

    Column {
        HorizontalDivider(
            thickness = 1.dp,
            color = Hairline,
            modifier = Modifier.fillMaxWidth(),
        )
        NavigationBar(containerColor = Paper) {
            NavigationBarItem(
                selected = currentDestination?.hasRoute(VehicleListRoute::class) == true,
                onClick = {
                    navController.navigate(VehicleListRoute) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = vectorResource(Res.drawable.ic_vehicle_car),
                        contentDescription = null,
                    )
                },
                label = { Text(text = stringResource(Res.string.tab_vehicles)) },
                colors = itemColors,
            )
            NavigationBarItem(
                selected = currentDestination?.hasRoute(SettingsRoute::class) == true,
                onClick = {
                    navController.navigate(SettingsRoute) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(imageVector = Icons.Filled.Settings, contentDescription = null) },
                label = { Text(text = stringResource(Res.string.tab_settings)) },
                colors = itemColors,
            )
        }
    }
}
