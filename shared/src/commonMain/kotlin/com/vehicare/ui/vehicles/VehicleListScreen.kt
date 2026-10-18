package com.vehicare.ui.vehicles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_try_again
import vehicare.shared.generated.resources.empty_vehicles_heading
import vehicare.shared.generated.resources.error_load_vehicles
import vehicare.shared.generated.resources.title_vehicles

/**
 * The Vehicles tab: a Scaffold whose content renders one `when` over the ViewModel's four
 * states — Loading, Empty, Content, Error — so no state ever shows a blank screen.
 *
 * Deliberately thin (this plan only proves the read path end to end): plan 01-06 replaces the
 * minimal states and plain rows with the final EmptyState/VehicleCard components and the add
 * control; the ViewModel wiring under this screen never changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleListRoute(
    viewModel: VehicleListViewModel = koinViewModel(),
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = stringResource(Res.string.title_vehicles)) }) },
    ) { innerPadding ->
        when (val state = viewModel.uiState.collectAsState().value) {
            is VehicleListUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            is VehicleListUiState.Empty -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = stringResource(Res.string.empty_vehicles_heading))
                }
            }
            is VehicleListUiState.Content -> {
                LazyColumn(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                ) {
                    items(state.items, key = { vehicle -> vehicle.id }) { vehicle ->
                        Text(
                            text = vehicle.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            is VehicleListUiState.Error -> {
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = stringResource(Res.string.error_load_vehicles),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        )
                        TextButton(
                            onClick = viewModel::retry,
                            modifier = Modifier.padding(top = 12.dp),
                        ) {
                            Text(text = stringResource(Res.string.action_try_again))
                        }
                    }
                }
            }
        }
    }
}
