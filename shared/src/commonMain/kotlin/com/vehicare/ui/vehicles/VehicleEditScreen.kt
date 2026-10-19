package com.vehicare.ui.vehicles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_vehicle
import vehicare.shared.generated.resources.action_back_to_vehicles
import vehicare.shared.generated.resources.action_update_vehicle
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.detail_not_found
import vehicare.shared.generated.resources.error_name_required
import vehicare.shared.generated.resources.error_save_vehicle
import vehicare.shared.generated.resources.field_name
import vehicare.shared.generated.resources.field_required_marker
import vehicare.shared.generated.resources.title_add_vehicle
import vehicare.shared.generated.resources.title_edit_vehicle

/**
 * The shared Add/Edit form (D-08: one destination, `vehicleId == 0L` is the add case).
 *
 * Interim shape: the state machine, the route id and the load/not-found states are already the real
 * ones, but the form still holds the single name field. Task 3 of this plan replaces this body with
 * the locked five-field C-1 form plus the type picker.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditRoute(
    vehicleId: Long,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<VehicleEditViewModel>(parameters = { parametersOf(vehicleId) })
    val state = viewModel.uiState.collectAsState().value
    val isAdd = vehicleId == 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isAdd) Res.string.title_add_vehicle else Res.string.title_edit_vehicle,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            VehicleEditUiState.Loading -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            VehicleEditUiState.NotFound -> Box(
                modifier = Modifier.padding(innerPadding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = stringResource(Res.string.detail_not_found))
                    OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) {
                        Text(text = stringResource(Res.string.action_back_to_vehicles))
                    }
                }
            }

            is VehicleEditUiState.Ready -> {
                val focusRequester = remember { FocusRequester() }
                LaunchedEffect(Unit) {
                    if (isAdd) focusRequester.requestFocus()
                }

                Column(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize()
                        .padding(16.dp),
                ) {
                    OutlinedTextField(
                        value = current.values.name,
                        onValueChange = { name -> viewModel.onFieldChange { it.copy(name = name) } },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        label = {
                            Text(
                                text = stringResource(Res.string.field_name) +
                                    stringResource(Res.string.field_required_marker),
                            )
                        },
                        singleLine = true,
                        enabled = !current.saving,
                        isError = current.error != null,
                        supportingText = if (current.error != null) {
                            { Text(text = stringResource(Res.string.error_name_required)) }
                        } else {
                            null
                        },
                    )
                    if (current.saveFailed) {
                        Text(
                            text = stringResource(Res.string.error_save_vehicle),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Button(
                        onClick = { viewModel.save(onSaved = onDone) },
                        enabled = VehicleFormValidation.isSubmittable(current.values) && !current.saving,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(
                            text = stringResource(
                                if (isAdd) Res.string.action_add_vehicle else Res.string.action_update_vehicle,
                            ),
                        )
                    }
                }
            }
        }
    }
}
