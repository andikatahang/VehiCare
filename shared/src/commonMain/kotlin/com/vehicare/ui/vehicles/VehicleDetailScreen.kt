package com.vehicare.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vehicare.domain.ServiceSummary
import com.vehicare.domain.Vehicle
import com.vehicare.ui.reminders.ReminderSummary
import com.vehicare.ui.reminders.ReminderSummaryViewModel
import com.vehicare.ui.reminders.components.ReminderSummaryRow
import com.vehicare.ui.service.ServiceSummaryViewModel
import com.vehicare.ui.service.components.ServiceSummaryRow
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.ui.vehicles.components.LicensePlateChip
import com.vehicare.ui.vehicles.components.VehicleTypeCircle
import com.vehicare.ui.vehicles.components.labelRes
import com.vehicare.ui.components.ConfirmDeleteDialog
import com.vehicare.ui.components.FieldRow
import com.vehicare.util.PhotoStorage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_back_to_vehicles
import vehicare.shared.generated.resources.action_delete_vehicle
import vehicare.shared.generated.resources.action_edit
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.cd_vehicle_photo
import vehicare.shared.generated.resources.detail_not_found
import vehicare.shared.generated.resources.dialog_delete_message
import vehicare.shared.generated.resources.dialog_delete_title
import vehicare.shared.generated.resources.error_delete_vehicle
import vehicare.shared.generated.resources.field_make
import vehicare.shared.generated.resources.field_model
import vehicare.shared.generated.resources.field_plate
import vehicare.shared.generated.resources.field_type
import vehicare.shared.generated.resources.field_year
import vehicare.shared.generated.resources.title_vehicle_fallback

/**
 * One vehicle's Detail screen (D-18): the hero photo or the type-circle placeholder, the name at
 * `Display` size, the five locked field rows, the Service history summary row, the Reminders
 * summary row (D-45) and the recessive destructive action — under a D-20 simple title bar with a
 * back arrow and one Edit action.
 *
 * All three states of [VehicleDetailUiState] render something: a progress indicator while the row
 * is being read, a not-available message with a way back when it is gone, and the content above.
 * There is no fourth state and no blank area anywhere.
 *
 * The state comes from the ViewModel's streaming flow, so an edit made on the form appears here as
 * soon as the user returns, and a confirmed deletion makes the row disappear — with nothing on
 * this screen refreshing anything by hand.
 *
 * Deleting is gated twice over: the destructive button only opens [ConfirmDeleteDialog], and while
 * a deletion is in flight the button is disabled and a second confirm is dropped by the ViewModel,
 * so an irreversible action can be started once and only once per confirmation.
 *
 * @param onDeleted invoked after a deletion really happened, so the shell can report it and return
 *   to the list. A failure never reaches it.
 * @param onOpenServiceHistory opens this vehicle's full service history — navigation stays injected,
 *   so this screen never touches a nav controller (D-32).
 * @param onOpenReminders opens this vehicle's Reminders destination — the Reminders summary row's
 *   only action (D-45), injected the same way as [onOpenServiceHistory].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailRoute(
    vehicleId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onOpenServiceHistory: () -> Unit,
    onOpenReminders: () -> Unit,
) {
    val viewModel = koinViewModel<VehicleDetailViewModel>(
        parameters = { parametersOf(vehicleId) },
    )
    // One aggregate read (count + newest date) drives the summary row; the screen reads no entry
    // rows of its own, so Detail cost stays constant in history length (T-02-06-01).
    val summaryViewModel = koinViewModel<ServiceSummaryViewModel>(
        parameters = { parametersOf(vehicleId) },
    )
    val summary = summaryViewModel.summary.collectAsState().value
    // The Reminders row's own aggregate (D-45) — the same join `RemindersViewModel` and the
    // dashboard use, so this row cannot name a different "most urgent rule" than either of them.
    val reminderSummaryViewModel = koinViewModel<ReminderSummaryViewModel>(
        parameters = { parametersOf(vehicleId) },
    )
    val reminderSummary = reminderSummaryViewModel.summary.collectAsState().value
    val state = viewModel.uiState.collectAsState().value
    val deleting = viewModel.deleting.collectAsState().value
    val deleteFailed = viewModel.deleteFailed.collectAsState().value

    // A title bar must never render empty, so the fallback names the destination while the row is
    // still being read or has turned out to be gone.
    val fallbackTitle = stringResource(Res.string.title_vehicle_fallback)
    val title = (state as? VehicleDetailUiState.Content)
        ?.vehicle
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: fallbackTitle

    val snackbarHostState = remember { SnackbarHostState() }
    val deleteErrorText = stringResource(Res.string.error_delete_vehicle)

    // UI-SPEC Detail "Delete fails": the failure is reported and nothing else changes — the dialog
    // is already closed, the vehicle stays on screen and nothing is removed locally, because the
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
                title = { Text(text = title) },
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
                    // clean (D-20, UI-SPEC Interaction rules → Detail → Edit). Delete lives at the
                    // end of the content instead (C-4).
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = stringResource(Res.string.action_edit))
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        when (val current = state) {
            VehicleDetailUiState.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            VehicleDetailUiState.NotFound -> Box(
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
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(Res.string.action_back_to_vehicles))
                    }
                }
            }

            is VehicleDetailUiState.Content -> VehicleDetailContent(
                vehicle = current.vehicle,
                contentPadding = innerPadding,
                summary = summary,
                onOpenServiceHistory = onOpenServiceHistory,
                reminderSummary = reminderSummary,
                onOpenReminders = onOpenReminders,
                // The visible half of the single-flight guard: a deletion that is already running
                // cannot be started again from here.
                deleteEnabled = !deleting,
                onDeleteClick = { showDeleteDialog = true },
            )
        }

        if (showDeleteDialog) {
            ConfirmDeleteDialog(
                title = stringResource(Res.string.dialog_delete_title),
                message = stringResource(Res.string.dialog_delete_message, title),
                confirmLabel = stringResource(Res.string.action_delete_vehicle),
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
 * The D-18 body, in the locked order: hero, name, the five field rows, a `2xl` break, the Service
 * history summary row, a `2xl` break, then the destructive action last (UI-SPEC §Detail field
 * rows, §Visual Hierarchy).
 *
 * The scroll container is what makes the field block and the row reachable on a small screen;
 * every spacing value in here is one of the seven declared tokens.
 */
@Composable
private fun VehicleDetailContent(
    vehicle: Vehicle,
    contentPadding: PaddingValues,
    summary: ServiceSummary?,
    onOpenServiceHistory: () -> Unit,
    reminderSummary: ReminderSummary?,
    onOpenReminders: () -> Unit,
    deleteEnabled: Boolean,
    onDeleteClick: () -> Unit,
) {
    val photoPath = vehicle.photoPath?.takeIf { it.isNotBlank() }
    val fallbackName = stringResource(Res.string.title_vehicle_fallback)
    val displayName = vehicle.name.takeIf { it.isNotBlank() } ?: fallbackName

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 48.dp),
    ) {
        // Hero (D-18): full content width at 4:3 with no corner radius. The photo is resolved from
        // its stored **relative** location through the single filesystem seam — never from a raw
        // path and never by constructing a location here (T-01-54).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) {
            if (photoPath == null) {
                // The no-photo fallback reuses the declared 64dp type circle (D-14) on a `Mist`
                // block, so the icon language is the same on the list and on this screen instead of
                // inventing a second hero-placeholder size.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Mist),
                    contentAlignment = Alignment.Center,
                ) {
                    VehicleTypeCircle(type = vehicle.type)
                }
            } else {
                AsyncImage(
                    model = PhotoStorage.resolve(photoPath),
                    contentDescription = stringResource(
                        Res.string.cd_vehicle_photo,
                        displayName,
                    ),
                    // Crop inside the declared 4:3 frame: no photo the user picks can distort the
                    // screen's layout, whatever its own aspect ratio is.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The screen's single largest text (UI-SPEC §Typography: `Display` applies to the vehicle
        // name on Detail only) and therefore its focal point.
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            modifier = Modifier.padding(top = 16.dp),
        )

        DetailFieldBlock(
            vehicle = vehicle,
            modifier = Modifier.padding(top = 24.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))
        // The Phase 1 placeholder's exact band, now occupied by the real summary row (D-32). The
        // 48px breaks above and below are untouched, so the screen's rhythm does not shift.
        ServiceSummaryRow(
            summary = summary,
            onClick = onOpenServiceHistory,
        )
        // The Reminders summary row (D-45): lg 24px below Service history, inside the same pair
        // of 48px breaks — the screen gains one row, it does not re-time.
        Spacer(modifier = Modifier.height(24.dp))
        ReminderSummaryRow(
            summary = reminderSummary,
            onClick = onOpenReminders,
        )
        Spacer(modifier = Modifier.height(48.dp))

        // The screen's only destructive control, and the lowest-weight element on it: it opens the
        // confirmation dialog and does nothing else. The dialog is the only path to the ViewModel's
        // delete entry point, and the button is disabled while a deletion is already running.
        // Deletion is irreversible in Phase 1 — no recovery surface exists behind it.
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
                // The worded label carries the meaning; the glyph is decorative (UI-SPEC
                // §Accessibility: no colour-only meaning).
                contentDescription = null,
            )
            Text(
                text = stringResource(Res.string.action_delete_vehicle),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The five locked field rows in the locked order — Make → Model → Year → License plate → Type
 * (C-2) — and nothing else.
 *
 * Every row is always present: an absent optional value renders the em dash rather than
 * collapsing, so the block has the same shape for every vehicle. Only the last row suppresses its
 * divider, so the block never ends on a dangling rule.
 */
@Composable
private fun DetailFieldBlock(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FieldRow(
            label = stringResource(Res.string.field_make),
            value = vehicle.make,
        )
        FieldRow(
            label = stringResource(Res.string.field_model),
            value = vehicle.model,
        )
        FieldRow(
            label = stringResource(Res.string.field_year),
            // Raw stored digits, deliberately unreformatted and unpadded (D-13).
            value = vehicle.year,
        )

        val plate = vehicle.plate?.takeIf { it.isNotBlank() }
        // The chip is a non-blank-only component, so the row shows either the pill or the em dash:
        // a present plate takes the trailing slot (and the value slot is suppressed with it), while
        // an absent one falls back to the same placeholder every other optional field uses.
        val plateSlot: (@Composable () -> Unit)? = if (plate != null) {
            { LicensePlateChip(plate = plate) }
        } else {
            null
        }
        FieldRow(
            label = stringResource(Res.string.field_plate),
            value = null,
            trailing = plateSlot,
        )

        FieldRow(
            label = stringResource(Res.string.field_type),
            // The type's display word comes from the one shared mapping (D-11): no second
            // type-to-copy table exists anywhere.
            value = stringResource(vehicle.type.labelRes()),
            showDivider = false,
        )
    }
}
