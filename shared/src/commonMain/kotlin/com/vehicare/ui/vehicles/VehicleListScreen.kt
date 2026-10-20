package com.vehicare.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vehicare.ui.components.EmptyState
import com.vehicare.ui.theme.Slate
import com.vehicare.ui.vehicles.components.VehicleCard
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import org.koin.compose.viewmodel.koinViewModel
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_vehicle
import vehicare.shared.generated.resources.action_try_again
import vehicare.shared.generated.resources.cd_add_vehicle
import vehicare.shared.generated.resources.empty_vehicles_body
import vehicare.shared.generated.resources.empty_vehicles_heading
import vehicare.shared.generated.resources.error_load_vehicles
import vehicare.shared.generated.resources.ic_vehicle_car
import vehicare.shared.generated.resources.loading_vehicles
import vehicare.shared.generated.resources.title_vehicles

/**
 * The Vehicles tab: a `Scaffold` with the simple title bar (D-20) whose content renders the
 * UI-SPEC state matrix — Loading, Empty, Content, Error — with a `FloatingActionButton` that stays
 * visible in every one of them (D-19).
 *
 * Navigation is injected, never owned: the screen receives an add callback and a per-vehicle open
 * callback so the single graph in `App.kt` stays the only place that knows about destinations, and
 * this composable stays testable and free of any navigation-controller reference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListRoute(
    onAddVehicle: () -> Unit,
    onOpenVehicle: (Long) -> Unit,
    viewModel: VehicleListViewModel = koinViewModel(),
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(Res.string.title_vehicles)) }) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (val state = viewModel.uiState.collectAsState().value) {
                is VehicleListUiState.Loading -> {
                    val loadingLabel = stringResource(Res.string.loading_vehicles)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        // No skeleton, no shimmer (UI-SPEC Motion bullets): the progress indicator
                        // is the whole loading state, and it announces itself as one.
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { contentDescription = loadingLabel },
                        )
                    }
                }

                is VehicleListUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = vectorResource(Res.drawable.ic_vehicle_car),
                            heading = stringResource(Res.string.empty_vehicles_heading),
                            body = stringResource(Res.string.empty_vehicles_body),
                            actionLabel = stringResource(Res.string.action_add_vehicle),
                            onAction = onAddVehicle,
                        )
                    }
                }

                is VehicleListUiState.Content -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // Rows arrive newest-first (D-16). The 64px bottom band is what keeps the
                        // last card clear of the 56dp FAB that floats over it.
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 32.dp,
                            bottom = 64.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(state.items, key = { vehicle -> vehicle.id }) { vehicle ->
                            VehicleCard(
                                vehicle = vehicle,
                                onClick = { onOpenVehicle(vehicle.id) },
                            )
                        }
                    }
                }

                is VehicleListUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.error_load_vehicles),
                                style = MaterialTheme.typography.bodyLarge,
                                color = Slate,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            OutlinedButton(
                                onClick = viewModel::retry,
                                modifier = Modifier.padding(top = 16.dp),
                            ) {
                                Text(text = stringResource(Res.string.action_try_again))
                            }
                        }
                    }
                }
            }

            // Outside the state `when` on purpose: the add control is present while loading, on an
            // empty list, over the cards and after a failed read.
            FloatingActionButton(
                onClick = onAddVehicle,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.cd_add_vehicle),
                )
            }
        }
    }
}
