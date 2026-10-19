package com.vehicare.ui.vehicles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Slate
import com.vehicare.ui.vehicles.components.PhotoField
import com.vehicare.ui.vehicles.components.VehicleTypePicker
import io.github.vinceglb.filekit.PlatformFile
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_vehicle
import vehicare.shared.generated.resources.action_back_to_vehicles
import vehicare.shared.generated.resources.action_update_vehicle
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.detail_not_found
import vehicare.shared.generated.resources.error_add_photo
import vehicare.shared.generated.resources.error_name_required
import vehicare.shared.generated.resources.error_save_vehicle
import vehicare.shared.generated.resources.field_make
import vehicare.shared.generated.resources.field_model
import vehicare.shared.generated.resources.field_name
import vehicare.shared.generated.resources.field_plate
import vehicare.shared.generated.resources.field_required_marker
import vehicare.shared.generated.resources.field_type
import vehicare.shared.generated.resources.field_year
import vehicare.shared.generated.resources.title_add_vehicle
import vehicare.shared.generated.resources.title_edit_vehicle

/**
 * The one Add/Edit form (D-08, D-17): `vehicleId == 0L` adds, any other id edits that row in place.
 *
 * This is the only screen that ever edits a vehicle — plan 01-09's Detail screen navigates here
 * rather than authoring a second form, and the photo block appended after the Type row (C-1) is
 * part of this same `Column`.
 *
 * Every state is explicit: an edit that is still loading shows a progress indicator, a row that no
 * longer exists says so and offers the way back — never a blank or half-populated form. A failed
 * save keeps every field exactly as the user left it and reports the failure in a Snackbar, and a
 * failed photo import reports itself the same way while leaving the form untouched.
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

    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorText = stringResource(Res.string.error_save_vehicle)
    val saveFailed = (state as? VehicleEditUiState.Ready)?.saveFailed == true
    val photoErrorText = stringResource(Res.string.error_add_photo)
    val photoFailed = (state as? VehicleEditUiState.Ready)?.photoError == true

    // UI-SPEC Add/Edit "Save failed": the form is retained and the failure is reported. The flag is
    // one-shot — the ViewModel clears it once the Snackbar has been shown, so a recomposition can
    // never replay it, and `onSaveErrorShown` never touches the field values.
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            snackbarHostState.showSnackbar(saveErrorText)
            viewModel.onSaveErrorShown()
        }
    }

    // UI-SPEC Photo field: an import that did not work says so instead of being silently dropped,
    // through the same host — the flag retires with the message (the call after `showSnackbar`
    // returns is exactly its dismiss path).
    LaunchedEffect(photoFailed) {
        if (photoFailed) {
            snackbarHostState.showSnackbar(photoErrorText)
            viewModel.onPhotoImportFailed()
        }
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val current = state) {
            VehicleEditUiState.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            VehicleEditUiState.NotFound -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    OutlinedButton(onClick = onDone, modifier = Modifier.padding(top = 16.dp)) {
                        Text(text = stringResource(Res.string.action_back_to_vehicles))
                    }
                }
            }

            is VehicleEditUiState.Ready -> VehicleEditForm(
                state = current,
                isAdd = isAdd,
                contentPadding = innerPadding,
                onFieldChange = viewModel::onFieldChange,
                onPhotoPicked = viewModel::onPhotoPicked,
                onPhotoRemoved = viewModel::onPhotoRemoved,
                onSave = { viewModel.save(onSaved = onDone) },
            )
        }
    }
}

/**
 * The C-1 field order — Name → Make → Model → Year → License plate → Type → Photo — in one
 * `Column` with `lg` 24px between field groups. The photo block is last (UI-SPEC rationale: the
 * heaviest interaction must never sit between the user and a fast add); the submit button stays
 * below it and closes the form.
 */
@Composable
private fun VehicleEditForm(
    state: VehicleEditUiState.Ready,
    isAdd: Boolean,
    contentPadding: PaddingValues,
    onFieldChange: ((VehicleFormValues) -> VehicleFormValues) -> Unit,
    onPhotoPicked: (PlatformFile) -> Unit,
    onPhotoRemoved: () -> Unit,
    onSave: () -> Unit,
) {
    val values = state.values
    val saving = state.saving
    val focusRequester = remember { FocusRequester() }

    // The name is the only required field and the fastest path to a saved vehicle, so the add case
    // opens with the keyboard already on it (UI-SPEC Visual Hierarchy: the focused Name field is
    // this screen's focal point).
    LaunchedEffect(isAdd) {
        if (isAdd) focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            // Keeps the submit button above the software keyboard (Android is edge-to-edge, so the
            // window itself is never resized for the IME).
            .imePadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        OutlinedTextField(
            value = values.name,
            onValueChange = { name -> onFieldChange { it.copy(name = name) } },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester),
            label = {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.field_name))
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(stringResource(Res.string.field_required_marker))
                        }
                    },
                )
            },
            singleLine = true,
            enabled = !saving,
            // `isError` + `supportingText` is the mechanism UI-SPEC's Accessibility table requires:
            // a screen reader reads the message together with the field.
            isError = state.error != null,
            supportingText = if (state.error != null) {
                {
                    Text(
                        text = stringResource(Res.string.error_name_required),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                null
            },
        )

        OutlinedTextField(
            value = values.make,
            onValueChange = { make -> onFieldChange { it.copy(make = make) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_make)) },
            singleLine = true,
            enabled = !saving,
        )

        OutlinedTextField(
            value = values.model,
            onValueChange = { model -> onFieldChange { it.copy(model = model) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_model)) },
            singleLine = true,
            enabled = !saving,
        )

        OutlinedTextField(
            value = values.year,
            // Every change goes through the digits-only filter, so a non-digit can never enter the
            // state (D-13, T-01-42) — the field requests a numeric keyboard as well.
            onValueChange = { raw -> onFieldChange { it.copy(year = VehicleFormValidation.sanitizeYear(raw)) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_year)) },
            singleLine = true,
            enabled = !saving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        OutlinedTextField(
            // Free text, exactly as D-12 locks it: no format hint, no uppercase transform, no limit.
            value = values.plate,
            onValueChange = { plate -> onFieldChange { it.copy(plate = plate) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_plate)) },
            singleLine = true,
            enabled = !saving,
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.field_type),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            VehicleTypePicker(
                selected = values.type,
                onSelect = { type -> onFieldChange { it.copy(type = type) } },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
            )
        }

        PhotoField(
            photoPath = values.photoPath,
            name = values.name,
            onPicked = onPhotoPicked,
            onRemove = onPhotoRemoved,
            error = state.photoError,
            // A save or an import both leave the values mid-flight, so neither may be interrupted by
            // a new pick or a removal (T-01-49's ordering depends on it).
            enabled = !saving && !state.importing,
        )

        Button(
            onClick = onSave,
            // Enabled only for a submittable form; the disabled state is also the visible half of
            // the double-submit guard, whose other half is the ViewModel's `saving` check (T-01-41).
            enabled = VehicleFormValidation.isSubmittable(values) && !saving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                // In place of its label (UI-SPEC Add/Edit `Saving`); `LocalContentColor` keeps the
                // indicator legible on the button's disabled container.
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(
                        if (isAdd) Res.string.action_add_vehicle else Res.string.action_update_vehicle,
                    ),
                )
            }
        }
    }
}
