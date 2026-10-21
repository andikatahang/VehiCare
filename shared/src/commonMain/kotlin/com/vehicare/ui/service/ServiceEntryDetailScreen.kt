package com.vehicare.ui.service

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vehicare.domain.ServiceEntry
import com.vehicare.domain.ServicePart
import com.vehicare.ui.components.ConfirmDeleteDialog
import com.vehicare.ui.components.FieldRow
import com.vehicare.ui.service.components.ServicePhotoStrip
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.util.RupiahFormat
import com.vehicare.util.ServiceDates
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_back_to_history
import vehicare.shared.generated.resources.action_delete_service_entry
import vehicare.shared.generated.resources.action_edit
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.detail_entry_not_found
import vehicare.shared.generated.resources.dialog_delete_entry_message
import vehicare.shared.generated.resources.dialog_delete_entry_title
import vehicare.shared.generated.resources.error_delete_entry
import vehicare.shared.generated.resources.field_cost
import vehicare.shared.generated.resources.field_mechanic
import vehicare.shared.generated.resources.field_notes
import vehicare.shared.generated.resources.field_parts
import vehicare.shared.generated.resources.field_photos
import vehicare.shared.generated.resources.field_workshop
import vehicare.shared.generated.resources.loading_service_entry
import vehicare.shared.generated.resources.title_service_entry
import vehicare.shared.generated.resources.value_empty

/**
 * The read-only Service entry detail destination (D-35, UI-SPEC §Service entry detail — read-only anatomy).
 *
 * The locked order: focal block (Display type + date·mileage meta), `lg` 24px break, then the field run:
 * Cost (divider), Parts (omitted when empty), Workshop (divider), Mechanic (divider), stacked Notes (no divider),
 * Photos (omitted when empty, read-only strip), `2xl` 48px break, destructive action last.
 *
 * All three states render something: centred progress while loading, not-found copy + back button when gone,
 * and the content above. The destructive action goes through [ConfirmDeleteDialog] — the dialog's confirm
 * is the screen's only call site of the ViewModel's delete.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceEntryDetailRoute(
    entryId: Long,
    onEdit: (vehicleId: Long, entryId: Long) -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: ServiceEntryDetailViewModel = koinViewModel(parameters = { parametersOf(entryId) }),
) {
    val state = viewModel.uiState.collectAsState().value
    val deleting = viewModel.deleting.collectAsState().value
    val deleteFailed = viewModel.deleteFailed.collectAsState().value

    val snackbarHostState = remember { SnackbarHostState() }
    val deleteErrorText = stringResource(Res.string.error_delete_entry)

    // UI-SPEC Detail "Delete fails": the failure is reported and nothing else changes — the dialog
    // is already closed, the entry stays on screen and nothing is removed locally, because the
    // repository said the row is still stored. The flag is one-shot: it retires with the message
    // (the call after `showSnackbar` returns is exactly its dismiss path), so a recomposition can
    // never replay it.
    LaunchedEffect(deleteFailed) {
        if (deleteFailed) {
            snackbarHostState.showSnackbar(deleteErrorText)
            viewModel.onDeleteErrorShown()
        }
    }

    // The confirmation gate (UI-SPEC §Interaction rules → Detail → Delete): the destructive button
    // opens this state and nothing else, and the dialog's confirm action is the screen's only call
    // site of the ViewModel's delete entry point.
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.title_service_entry)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
                actions = {
                    // Edit is a text action with no icon on purpose: the simple title bar stays
                    // clean. Delete lives at the end of the content instead.
                    TextButton(
                        onClick = {
                            val content = state as? ServiceEntryDetailUiState.Content
                            content?.entry?.let { entry ->
                                onEdit(entry.vehicleId, entry.id)
                            }
                        },
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                        enabled = state is ServiceEntryDetailUiState.Content,
                    ) {
                        Text(text = stringResource(Res.string.action_edit))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val current = state) {
            ServiceEntryDetailUiState.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            ServiceEntryDetailUiState.NotFound -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.detail_entry_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(Res.string.action_back_to_history))
                    }
                }
            }

            is ServiceEntryDetailUiState.Content -> ServiceEntryDetailContent(
                entry = current.entry,
                contentPadding = innerPadding,
                deleteEnabled = !deleting,
                onDeleteClick = { showDeleteDialog = true },
            )
        }

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                title = stringResource(Res.string.dialog_delete_entry_title),
                message = stringResource(Res.string.dialog_delete_entry_message),
                confirmLabel = stringResource(Res.string.action_delete_service_entry),
                onConfirm = {
                    // Close first, then start the deletion: the dialog is never left on screen
                    // describing an action that has already been decided.
                    showDeleteDialog = false
                    viewModel.delete(onDeleted = onDeleted)
                },
                onDismiss = { showDeleteDialog = false },
            )
        }
    }
}

/**
 * The D-35 body, in the locked order: focal block, `lg` break, field run (Cost divider, Parts
 * omitted when empty, Workshop divider, Mechanic divider, stacked Notes no divider), Photos
 * omitted when empty (read-only strip), `2xl` break, destructive action last.
 *
 * The scroll container is what makes the field block and the destructive action reachable on a
 * small screen; every spacing value in here is one of the seven declared tokens.
 */
@Composable
private fun ServiceEntryDetailContent(
    entry: ServiceEntry,
    contentPadding: PaddingValues,
    deleteEnabled: Boolean,
    onDeleteClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 48.dp),
    ) {
        // Focal block (UI-SPEC §Service entry detail line 238-241): the service type at `Display`
        // 24sp/500 `Ink`, then the meta `19 Oct 2026 · 123.456 km` (`Body`/`Slate`, `sm` 8px below).
        // temporary compile bridge — 02-08 replaces this with serviceTypeLine(...)
        Text(
            text = entry.serviceTypes.firstOrNull().orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
        )
        Text(
            text = serviceMetaLine(entry.dateEpochMs, entry.mileageKm),
            style = MaterialTheme.typography.bodyLarge,
            color = Slate,
            modifier = Modifier.padding(top = 8.dp),
        )

        // `lg` 24px break before the field run
        Spacer(modifier = Modifier.height(24.dp))

        FieldRow(
            label = stringResource(Res.string.field_cost),
            value = entry.costRupiah?.let { RupiahFormat.formatRupiah(it) },
        )

        // Parts — omitted entirely when entry.parts.isEmpty()
        if (entry.parts.isNotEmpty()) {
            PartsBlock(parts = entry.parts)
        }

        FieldRow(
            label = stringResource(Res.string.field_workshop),
            value = entry.workshop,
        )

        FieldRow(
            label = stringResource(Res.string.field_mechanic),
            value = entry.mechanic,
        )

        // Notes — stacked row (label above left-aligned wrapping value), no divider
        DetailNotesRow(
            label = stringResource(Res.string.field_notes),
            value = entry.notes,
        )

        // Photos — omitted entirely when entry.photoPaths.isEmpty(), otherwise read-only strip
        if (entry.photoPaths.isNotEmpty()) {
            PhotosBlock(photoPaths = entry.photoPaths)
        }

        // `2xl` 48px break before the destructive action
        Spacer(modifier = Modifier.height(48.dp))

        // The screen's only destructive control, and the lowest-weight element on it: it opens the
        // confirmation dialog and does nothing else. The dialog is the only path to the ViewModel's
        // delete entry point, and the button is disabled while a deletion is already running.
        OutlinedButton(
            onClick = onDeleteClick,
            enabled = deleteEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
            )
            Text(
                text = stringResource(Res.string.action_delete_service_entry),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The Parts section: a `Title` label "Parts" followed by one `Body`/`Ink` line per part
 * reading `"${part.name} × ${part.quantity}"` with `sm` 8px between lines.
 */
@Composable
private fun PartsBlock(parts: List<ServicePart>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.field_parts),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        parts.forEach { part ->
            Text(
                text = "${part.name} × ${part.quantity}",
                style = MaterialTheme.typography.bodyLarge,
                color = Ink,
            )
        }
    }
}

/**
 * Stacked notes row: `Label`/`Slate` label above a left-aligned wrapping `Body`/`Ink` value,
 * `xs` 4px gap, no divider (because a right-aligned wrapping value in a two-column row is unreadable).
 */
@Composable
private fun DetailNotesRow(label: String, value: String?) {
    Column(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Slate,
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: stringResource(Res.string.value_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = if (value?.isBlank() == false) Ink else Slate,
            textAlign = TextAlign.Start,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

/**
 * Photos section: `Title` label "Photos" + the read-only 80dp thumbnail strip.
 * Thumbnails are decorative and not tappable — no viewer ships this phase.
 */
@Composable
private fun PhotosBlock(photoPaths: List<String>) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(Res.string.field_photos),
            style = MaterialTheme.typography.titleMedium,
            color = Ink,
        )
        ServicePhotoStrip(
            photoPaths = photoPaths,
            readOnly = true,
        )
    }
}

/**
 * The single source of the `date · mileage` line for both this screen and the history row
 * (02-05 imports it from here — `ServiceHistoryRow` must not re-implement it).
 *
 * Both halves come from the shared utilities so no screen formats a date or a mileage itself.
 * The separator is the middle dot (U+00B7) with single spaces, matching Phase 1's `vehicleMetaLine` convention.
 */
internal fun serviceMetaLine(dateEpochMs: Long, mileageKm: Long): String =
    "${ServiceDates.display(dateEpochMs)} · ${RupiahFormat.formatMileage(mileageKm)}"