package com.vehicare.ui.vehicles

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_vehicle
import vehicare.shared.generated.resources.action_update_vehicle
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.error_save_vehicle
import vehicare.shared.generated.resources.field_name
import vehicare.shared.generated.resources.field_required_marker
import vehicare.shared.generated.resources.title_add_vehicle
import vehicare.shared.generated.resources.title_edit_vehicle

/**
 * The shared Add/Edit form (D-08: one destination, `vehicleId == 0L` is the add case).
 *
 * THIS slice is name-only: one autofocused field, a primary submit, and no celebratory state —
 * on success the screen pops back and the Flow-driven list reveals the new row. The submit
 * control disables while a save is in flight, so double taps cannot enqueue duplicate inserts
 * (T-01-23); a failed save keeps the form populated with the recovery copy attached to the field.
 * Plan 01-07 owns the full field set, validation and Snackbar polish.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleEditRoute(
    vehicleId: Long,
    onDone: () -> Unit,
) {
    val viewModel = koinViewModel<VehicleEditViewModel>()
    val state = viewModel.uiState.collectAsState().value

    val (name, isSaving, saveFailed) = when (state) {
        is VehicleEditUiState.Ready -> Triple(state.name, state.isSaving, state.saveFailed)
        VehicleEditUiState.Idle -> Triple("", false, false)
    }
    val isAdd = vehicleId == 0L
    val canSubmit = name.isNotBlank() && !isSaving

    // The back arrow is the same pop the save-success path uses: the list is flow-driven and
    // shows whatever has already been persisted.
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        if (isAdd) focusRequester.requestFocus()
    }

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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.End,
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::onNameChange,
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
                enabled = !isSaving,
                isError = saveFailed,
                supportingText = {
                    if (saveFailed) {
                        Text(
                            text = stringResource(Res.string.error_save_vehicle),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
            Button(
                onClick = {
                    scope.launch {
                        val savedId = viewModel.save()
                        if (savedId > 0L) onDone()
                    }
                },
                enabled = canSubmit,
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
