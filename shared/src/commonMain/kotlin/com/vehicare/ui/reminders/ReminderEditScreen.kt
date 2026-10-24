package com.vehicare.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.vehicare.domain.ServiceTypePresets
import com.vehicare.ui.components.ConfirmDeleteDialog
import com.vehicare.ui.service.SelectionMode
import com.vehicare.ui.service.components.ServiceTypeDialog
import com.vehicare.ui.service.components.ServiceTypePicker
import com.vehicare.ui.theme.Danger
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_reminder
import vehicare.shared.generated.resources.action_delete_reminder
import vehicare.shared.generated.resources.action_try_again
import vehicare.shared.generated.resources.action_update_reminder
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.dialog_delete_reminder_message
import vehicare.shared.generated.resources.dialog_delete_reminder_title
import vehicare.shared.generated.resources.error_delete_reminder
import vehicare.shared.generated.resources.error_interval_min
import vehicare.shared.generated.resources.error_save_reminder
import vehicare.shared.generated.resources.field_interval_distance
import vehicare.shared.generated.resources.field_lead_days
import vehicare.shared.generated.resources.field_lead_km
import vehicare.shared.generated.resources.field_reminder_on
import vehicare.shared.generated.resources.field_required_marker
import vehicare.shared.generated.resources.field_service_type
import vehicare.shared.generated.resources.field_suffix_km
import vehicare.shared.generated.resources.hint_advance_notice
import vehicare.shared.generated.resources.hint_intervals
import vehicare.shared.generated.resources.loading_reminder
import vehicare.shared.generated.resources.reminder_not_found
import vehicare.shared.generated.resources.section_advance_notice
import vehicare.shared.generated.resources.section_intervals
import vehicare.shared.generated.resources.title_add_reminder
import vehicare.shared.generated.resources.title_edit_reminder

/**
 * The one Add/Edit reminder destination (D-08/D-42): `ruleId == 0L` adds, any other id edits that
 * rule in place.
 *
 * This is the only screen that renders [ReminderEditViewModel]'s state: a progress indicator while
 * an edit load is still running, a not-available message with a way back when the rule is gone (or
 * belongs to another vehicle, T-03-22), and the six-group form otherwise. A failed save keeps every
 * field exactly as the user left it; a failed delete leaves the editor and the rule untouched.
 *
 * **Two recorded limitations (do not "fix" them):** the single-select chip group still announces
 * `Role.Checkbox`, because the pinned `SelectableChip` applies that role internally after the
 * caller's modifier (UI-SPEC Verification Notes #3) — acceptable because exactly one chip is always
 * selected once the user picks. And this screen deliberately has no unsaved-changes confirmation:
 * that vocabulary is reserved for stored-entity deletion (Phase 1/2 precedent).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditRoute(
    vehicleId: Long,
    ruleId: Long,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ReminderEditViewModel = koinViewModel(parameters = { parametersOf(vehicleId, ruleId) }),
) {
    val state = viewModel.uiState.collectAsState().value
    val isAdd = ruleId == 0L

    val snackbarHostState = remember { SnackbarHostState() }
    val saveErrorText = stringResource(Res.string.error_save_reminder)
    val retryLabel = stringResource(Res.string.action_try_again)
    val deleteErrorText = stringResource(Res.string.error_delete_reminder)
    val saveFailed = (state as? ReminderEditUiState.Ready)?.saveFailed == true
    val deleteFailed = (state as? ReminderEditUiState.Ready)?.deleteFailed == true

    // UI-SPEC §Reminder editor "Saving/save failed": the form is retained, the failure is reported
    // with the in-context recovery action the contract promises, and choosing it re-invokes save.
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

    // UI-SPEC §Reminder editor "Delete fails": the dialog is already closed, the editor and the
    // rule stay untouched, and the flag retires with the message.
    LaunchedEffect(deleteFailed) {
        if (deleteFailed) {
            snackbarHostState.showSnackbar(deleteErrorText)
            viewModel.onDeleteErrorShown()
        }
    }

    var showTypeDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            if (isAdd) Res.string.title_add_reminder else Res.string.title_edit_reminder,
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
            ReminderEditUiState.Loading -> {
                val loadingLabel = stringResource(Res.string.loading_reminder)
                Box(
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics { contentDescription = loadingLabel },
                    )
                }
            }

            ReminderEditUiState.NotFound -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.reminder_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    OutlinedButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
                        Text(text = stringResource(Res.string.cd_back))
                    }
                }
            }

            is ReminderEditUiState.Ready -> ReminderEditForm(
                state = current,
                isAdd = isAdd,
                contentPadding = innerPadding,
                onFieldChange = viewModel::onFieldChange,
                onTypeSelected = viewModel::onTypeSelected,
                onAddCustomType = { showTypeDialog = true },
                onEnabledChange = viewModel::onEnabledChange,
                onSave = { viewModel.save(onSaved = onSaved) },
                onDeleteClick = { showDeleteDialog = true },
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

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                title = stringResource(Res.string.dialog_delete_reminder_title),
                message = stringResource(Res.string.dialog_delete_reminder_message),
                confirmLabel = stringResource(Res.string.action_delete_reminder),
                onConfirm = {
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onDeleted)
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}

/**
 * The locked field order (UI-SPEC §Reminder editor): service type → intervals → advance notice →
 * reminder on → submit → delete (edit mode only), one `Column` with `lg` 24dp between groups and a
 * `3xl` 64dp bottom pad. No autofocus and no nested vertical scrolling (Phase 1/2 precedent).
 */
@Composable
private fun ReminderEditForm(
    state: ReminderEditUiState.Ready,
    isAdd: Boolean,
    contentPadding: PaddingValues,
    onFieldChange: (ReminderFormField, String) -> Unit,
    onTypeSelected: (String) -> Unit,
    onAddCustomType: () -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val values = state.values
    val saving = state.saving
    val deleting = state.deleting
    val inputsEnabled = !saving && !deleting
    val errors = ReminderFormValidation.errors(values)
    val errorText = stringResource(Res.string.error_interval_min)

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .imePadding()
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp, bottom = 64.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // 1. Service type (required *) — single-select, no error line (C-35).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = buildAnnotatedString {
                    append(stringResource(Res.string.field_service_type))
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append(stringResource(Res.string.field_required_marker))
                    }
                },
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            ServiceTypePicker(
                presets = ServiceTypePresets.ALL,
                learned = state.learnedTypes,
                selected = values.serviceTypes,
                onToggle = onTypeSelected,
                onAddCustom = onAddCustomType,
                enabled = inputsEnabled,
                selectionMode = SelectionMode.Single,
            )
        }

        // 2. Intervals (km mandatory) — the monthly interval lives in Settings (261024-u9g, D-3).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.section_intervals),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = stringResource(Res.string.hint_intervals),
                style = MaterialTheme.typography.labelLarge,
                color = Slate,
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = values.intervalKm,
                    onValueChange = { onFieldChange(ReminderFormField.INTERVAL_KM, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(Res.string.field_interval_distance)) },
                    singleLine = true,
                    enabled = inputsEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    suffix = {
                        Text(
                            text = stringResource(Res.string.field_suffix_km),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    },
                    isError = ReminderFormField.INTERVAL_KM in errors,
                    supportingText = if (ReminderFormField.INTERVAL_KM in errors) {
                        { Text(text = errorText, style = MaterialTheme.typography.labelLarge, color = Danger) }
                    } else {
                        null
                    },
                )
            }
        }

        // 3. Advance notice (optional) — same error rule, no suffix (the label carries the unit).
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(Res.string.section_advance_notice),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            Text(
                text = stringResource(Res.string.hint_advance_notice),
                style = MaterialTheme.typography.labelLarge,
                color = Slate,
            )
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = values.leadKm,
                    onValueChange = { onFieldChange(ReminderFormField.LEAD_KM, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(Res.string.field_lead_km)) },
                    singleLine = true,
                    enabled = inputsEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = ReminderFormField.LEAD_KM in errors,
                    supportingText = if (ReminderFormField.LEAD_KM in errors) {
                        { Text(text = errorText, style = MaterialTheme.typography.labelLarge, color = Danger) }
                    } else {
                        null
                    },
                )
                OutlinedTextField(
                    value = values.leadDays,
                    onValueChange = { onFieldChange(ReminderFormField.LEAD_DAYS, it) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(text = stringResource(Res.string.field_lead_days)) },
                    singleLine = true,
                    enabled = inputsEnabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = ReminderFormField.LEAD_DAYS in errors,
                    supportingText = if (ReminderFormField.LEAD_DAYS in errors) {
                        { Text(text = errorText, style = MaterialTheme.typography.labelLarge, color = Danger) }
                    } else {
                        null
                    },
                )
            }
        }

        // 4. Reminder on
        ReminderToggleRow(
            enabled = inputsEnabled,
            checked = values.enabled,
            onCheckedChange = onEnabledChange,
        )

        // 5. Submit
        Button(
            onClick = onSave,
            enabled = ReminderFormValidation.isSubmittable(values) && inputsEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
        ) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = LocalContentColor.current,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = stringResource(
                        if (isAdd) Res.string.action_add_reminder else Res.string.action_update_reminder,
                    ),
                )
            }
        }

        // 6. Delete (edit mode only)
        if (!isAdd) {
            Spacer(modifier = Modifier.height(48.dp))
            OutlinedButton(
                onClick = onDeleteClick,
                enabled = inputsEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
            ) {
                Text(text = stringResource(Res.string.action_delete_reminder))
            }
        }
    }
}

/**
 * The "Reminder on" row (UI-SPEC §Reminder editor group 4): a label at `weight(1f)` and a trailing
 * palette-locked [Switch] (C-33). Private to this file — it has exactly one call site, and the
 * UI-SPEC names it as a row, not a standalone component.
 */
@Composable
private fun ReminderToggleRow(
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(Res.string.field_reminder_on),
            style = MaterialTheme.typography.bodyLarge,
            color = Ink,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Paper,
                checkedTrackColor = Ink,
                uncheckedThumbColor = Slate,
                uncheckedTrackColor = Mist,
                uncheckedBorderColor = Hairline,
            ),
        )
    }
}
