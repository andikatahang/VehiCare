package com.vehicare.ui.service

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
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.vehicare.ui.service.ServiceEntryFormValidation
import com.vehicare.ui.service.components.PartsEditor
import com.vehicare.ui.service.components.ServiceDateField
import com.vehicare.ui.service.components.ServicePhotoStrip
import com.vehicare.ui.service.components.ServiceTypeDialog
import com.vehicare.ui.service.components.ServiceTypePicker
import com.vehicare.ui.theme.Slate
import com.vehicare.util.RupiahFormat
import io.github.vinceglb.filekit.PlatformFile
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_service
import vehicare.shared.generated.resources.action_back_to_vehicles
import vehicare.shared.generated.resources.action_try_again
import vehicare.shared.generated.resources.action_update_service
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.detail_not_found
import vehicare.shared.generated.resources.error_add_photo
import vehicare.shared.generated.resources.error_mileage_required
import vehicare.shared.generated.resources.error_save_entry
import vehicare.shared.generated.resources.field_cost
import vehicare.shared.generated.resources.field_mechanic
import vehicare.shared.generated.resources.field_mileage
import vehicare.shared.generated.resources.field_notes
import vehicare.shared.generated.resources.field_parts
import vehicare.shared.generated.resources.field_service_type
import vehicare.shared.generated.resources.field_workshop
import vehicare.shared.generated.resources.title_add_service
import vehicare.shared.generated.resources.title_edit_service
import vehicare.shared.generated.resources.warn_mileage_lower
import vehicare.shared.generated.resources.field_required_marker
import vehicare.shared.generated.resources.field_photos

/**
 * The one Add/Edit form (D-08, D-30): `entryId == 0L` adds, any other id edits that row in place.
 *
 * This is the only screen that ever edits a service entry — the history screen's FAB and the
 * entry detail's Edit action navigate here. The single scrolling form has the locked field order:
 * date → mileage → type → cost → notes → parts → workshop/mechanic → photos → submit.
 *
 * Every state is explicit: an edit that is still loading shows a progress indicator, a row that no
 * longer exists says so and offers the way back — never a blank or half-populated form. A failed
 * save keeps every field exactly as the user left it and reports the failure in a Snackbar, and a
 * failed photo import reports itself the same way while leaving the form untouched.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEntryEditRoute(
    vehicleId: Long,
    entryId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: ServiceEntryEditViewModel = koinViewModel(parameters = { parametersOf(vehicleId, entryId) }),
) {
    val state = viewModel.uiState.collectAsState().value
    val isAdd = entryId == 0L

    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorText = stringResource(Res.string.error_save_entry)
    val retryLabel = stringResource(Res.string.action_try_again)
    val saveFailed = (state as? ServiceEntryEditUiState.Ready)?.saveFailed == true
    val photoErrorText = stringResource(Res.string.error_add_photo)
    val photoFailed = (state as? ServiceEntryEditUiState.Ready)?.photoError == true

    // UI-SPEC Add/Edit "Save failed": the form is retained, the failure is reported with the
    // in-context recovery action the contract promises, and choosing it re-invokes submit.
    // The flag is one-shot — the ViewModel clears it once the Snackbar has been shown.
    LaunchedEffect(saveFailed) {
        if (saveFailed) {
            val result = snackbarHostState.showSnackbar(
                message = saveErrorText,
                actionLabel = retryLabel,
            )
            viewModel.onSaveErrorShown()
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.save(onSaved = onSaved)
            }
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

    var showTypeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isAdd) Res.string.title_add_service else Res.string.title_edit_service,
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
            ServiceEntryEditUiState.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            ServiceEntryEditUiState.NotFound -> Box(
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
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text(text = stringResource(Res.string.action_back_to_vehicles))
                    }
                }
            }

            is ServiceEntryEditUiState.Ready -> ServiceEntryEditForm(
                state = current,
                isAdd = isAdd,
                contentPadding = innerPadding,
                onFieldChange = viewModel::onFieldChange,
                onCustomTypeConfirm = { showTypeDialog = true },
                onPhotoPicked = viewModel::onPhotoPicked,
                onPhotoRemoved = viewModel::onPhotoRemoved,
                onAddPart = viewModel::onAddPart,
                onPartChange = viewModel::onPartChange,
                onRemovePart = viewModel::onRemovePart,
                onSave = { viewModel.save(onSaved = onSaved) },
                onTypeDialogDismiss = { showTypeDialog = false },
            )
        }

        if (showTypeDialog) {
            ServiceTypeDialog(
                onConfirm = { trimmed ->
                    viewModel.onCustomTypeConfirmed(trimmed)
                    showTypeDialog = false
                },
                onDismiss = { showTypeDialog = false },
            )
        }
    }
}

/**
 * The locked field order (D-30) — date → mileage → type → cost → notes → parts → workshop/mechanic → photos → submit
 * in one `Column` with `lg` 24px between field groups and `3xl` 64px bottom padding.
 * No autofocus (D-31) and no nested vertical scrolling (Pitfall 7).
 */
@Composable
private fun ServiceEntryEditForm(
    state: ServiceEntryEditUiState.Ready,
    isAdd: Boolean,
    contentPadding: PaddingValues,
    onFieldChange: ((ServiceEntryFormValues) -> ServiceEntryFormValues) -> Unit,
    onCustomTypeConfirm: () -> Unit,
    onPhotoPicked: (PlatformFile) -> Unit,
    onPhotoRemoved: (Int) -> Unit,
    onAddPart: () -> Unit,
    onPartChange: (Int, String, String) -> Unit,
    onRemovePart: (Int) -> Unit,
    onSave: () -> Unit,
    onTypeDialogDismiss: () -> Unit,
) {
    val values = state.values
    val saving = state.saving
    val importing = state.importing
    val mileageBaseline = state.mileageBaseline
    val learnedTypes = state.learnedTypes

    // WR-01: the "required" error appears only after the user clears a previously non-empty value,
    // matching UI-SPEC's Invalid state (an untouched blank form carries no error line).
    var mileageCleared by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .imePadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 1. Service date (required *)
        ServiceDateField(
            epochMs = values.dateEpochMs ?: com.vehicare.util.ServiceDates.toStorage(com.vehicare.util.ServiceDates.today()),
            onDateSelected = { epochMs ->
                onFieldChange { it.copy(dateEpochMs = epochMs) }
            },
            enabled = !saving && !importing,
        )

        // 2. Mileage (required *) with supporting line: cleared-error → lower-warning → preview.
        val mileageValue = values.mileage
        val mileageLong = mileageValue.toLongOrNull()
        val mileageWarning = mileageBaseline != null && mileageLong != null && mileageLong < mileageBaseline
        val mileageError = mileageCleared && mileageValue.isBlank()
        val mileagePreview = mileageLong?.let(RupiahFormat::formatMileage)

        OutlinedTextField(
            value = mileageValue,
            onValueChange = { raw ->
                val sanitized = ServiceEntryFormValidation.sanitizeMileage(raw)
                // Flip only when a previously non-empty value is cleared.
                mileageCleared = sanitized.isBlank() && values.mileage.isNotBlank()
                onFieldChange { it.copy(mileage = sanitized) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = {
                Text(
                    text = buildAnnotatedString {
                        append(stringResource(Res.string.field_mileage))
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                            append(stringResource(Res.string.field_required_marker))
                        }
                    },
                )
            },
            singleLine = true,
            enabled = !saving && !importing,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = mileageError,
            supportingText = when {
                mileageError -> {
                    {
                        Text(
                            text = stringResource(Res.string.error_mileage_required),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                mileageWarning -> {
                    {
                        Text(
                            text = stringResource(Res.string.warn_mileage_lower),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                mileagePreview != null -> {
                    {
                        Text(
                            text = mileagePreview,
                            style = MaterialTheme.typography.labelLarge,
                            color = Slate,
                        )
                    }
                }

                else -> null
            },
        )

        // 3. Service type (required *) - chip group
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.field_service_type))
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(stringResource(Res.string.field_required_marker))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ServiceTypePicker(
                presets = com.vehicare.domain.ServiceTypePresets.ALL,
                learned = learnedTypes,
                selected = values.serviceType,
                onSelect = { type ->
                    onFieldChange { it.copy(serviceType = type) }
                },
                onAddCustom = onCustomTypeConfirm,
                enabled = !saving && !importing,
            )
        }

        // 4. Cost (optional) with formatted preview
        OutlinedTextField(
            value = values.cost,
            onValueChange = { raw ->
                onFieldChange { it.copy(cost = ServiceEntryFormValidation.sanitizeCost(raw)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_cost)) },
            singleLine = true,
            enabled = !saving && !importing,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            trailingIcon = {
                if (values.cost.isNotBlank()) {
                    val parsed = com.vehicare.util.RupiahFormat.parseRupiahDigits(values.cost)
                    if (parsed != null) {
                        Text(
                            text = com.vehicare.util.RupiahFormat.formatRupiah(parsed),
                            style = MaterialTheme.typography.labelLarge,
                            color = Slate,
                        )
                    }
                }
            },
        )

        // 5. Notes (optional) multi-line
        OutlinedTextField(
            value = values.notes,
            onValueChange = { notes ->
                onFieldChange { it.copy(notes = notes.take(ServiceEntryFormValidation.MAX_NOTES)) }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_notes)) },
            singleLine = false,
            minLines = 3,
            maxLines = 6,
            enabled = !saving && !importing,
        )

        // 6. Parts (optional)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.field_parts),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            PartsEditor(
                rows = values.parts,
                onPartChange = onPartChange,
                onAddPart = onAddPart,
                onRemovePart = onRemovePart,
                enabled = !saving && !importing,
            )
        }

        // 7. Workshop (optional)
        OutlinedTextField(
            value = values.workshop,
            onValueChange = { workshop ->
                onFieldChange {
                    it.copy(workshop = workshop.take(ServiceEntryFormValidation.MAX_WORKSHOP))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_workshop)) },
            singleLine = true,
            enabled = !saving && !importing,
        )

        // 8. Mechanic (optional)
        OutlinedTextField(
            value = values.mechanic,
            onValueChange = { mechanic ->
                onFieldChange {
                    it.copy(mechanic = mechanic.take(ServiceEntryFormValidation.MAX_MECHANIC))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_mechanic)) },
            singleLine = true,
            enabled = !saving && !importing,
        )

        // 9. Photos (optional)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.field_photos),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ServicePhotoStrip(
                photoPaths = values.photoPaths,
                onPicked = onPhotoPicked,
                onRemove = onPhotoRemoved,
                importing = importing,
                enabled = !saving,
                readOnly = false,
            )
        }

        // 10. Submit button (last)
        Button(
            onClick = onSave,
            enabled = ServiceEntryFormValidation.isSubmittable(values) && !saving && !importing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(
                        if (isAdd) Res.string.action_add_service else Res.string.action_update_service,
                    ),
                )
            }
        }
    }
}