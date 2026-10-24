package com.vehicare.ui.reminders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vehicare.notifications.LocalNotificationPermissionCoordinator
import com.vehicare.notifications.NotificationPermissionPolicy
import com.vehicare.ui.components.EmptyState
import com.vehicare.ui.reminders.components.NotificationsPermissionDialog
import com.vehicare.ui.reminders.components.ReminderCard
import com.vehicare.ui.reminders.components.ReminderStatusRow
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_reminder
import vehicare.shared.generated.resources.action_try_again
import vehicare.shared.generated.resources.cd_add_reminder
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.empty_reminders_body
import vehicare.shared.generated.resources.error_load_reminders
import vehicare.shared.generated.resources.error_save_reminder
import vehicare.shared.generated.resources.loading_reminders
import vehicare.shared.generated.resources.reminders_none
import vehicare.shared.generated.resources.title_reminders

/**
 * The Reminders destination: one vehicle's rules, most urgent first, with an in-place on/off switch
 * and the Add entry point (D-41/D-42/D-43).
 *
 * All four states are explicit — loading, empty, content, error — and the FAB sits **outside** the
 * `when` so "Add reminder" is reachable from every one of them (D-19/D-42 precedent). Navigation is
 * injected, never owned: the screen receives callbacks so the single graph in `App.kt` stays the only
 * place that knows about destinations.
 *
 * This screen is also the host of the notification permission ask (D-54/D-61): the inline status row
 * is the first item of the **Content** state only, and the one explanation dialog below serves both
 * the automatic post-save ask and a manual tap on that row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersRoute(
    vehicleId: Long,
    onAddReminder: () -> Unit,
    onOpenRule: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: RemindersViewModel = koinViewModel(parameters = { parametersOf(vehicleId) }),
) {
    val state = viewModel.uiState.collectAsState().value
    val snackbarHostState = remember { SnackbarHostState() }

    // Resolve the message before the effect: the effect body must hold no composable call.
    val saveErrorText = stringResource(Res.string.error_save_reminder)
    val toggleFailed = (state as? RemindersUiState.Content)?.toggleFailed == true

    // The flag is one-shot: the write never landed, the row still shows the database's value, and
    // the message retires as soon as it has been shown (T-03-19).
    LaunchedEffect(toggleFailed) {
        if (toggleFailed) {
            snackbarHostState.showSnackbar(saveErrorText)
            viewModel.acknowledgeToggleFailure()
        }
    }

    // D-54 (amended 2026-10-23): this screen is the only host for the automatic explanation
    // dialog. A post-save ask never appears over the editor, because the editor has already
    // popped by the time its signal is consumed here.
    val coordinator = LocalNotificationPermissionCoordinator.current
    val permissionUi by coordinator.state.collectAsState()
    val pendingAsk by viewModel.pendingPermissionAsk.collectAsState()
    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(pendingAsk, permissionUi) {
        if (!pendingAsk) return@LaunchedEffect
        // Wait for the coordinator's first read instead of dropping the ask: `null` means "not
        // read yet", not "no permission" (the coordinator's own contract).
        val ui = permissionUi ?: return@LaunchedEffect
        viewModel.consumePermissionAsk()
        if (NotificationPermissionPolicy.shouldAutoExplain(
                ui.permission,
                ui.hasRequestedSystemPrompt,
                ui.hasAutoExplained,
            )
        ) {
            // Spent at the moment it is presented, not at the moment it is dismissed — a second
            // add or an edit while this dialog is still open can never buy a second automatic ask.
            coordinator.markAutoExplained()
            showPermissionDialog = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.title_reminders),
                        style = MaterialTheme.typography.titleLarge,
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
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (state) {
                is RemindersUiState.Loading -> {
                    val loadingLabel = stringResource(Res.string.loading_reminders)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { contentDescription = loadingLabel },
                        )
                    }
                }

                is RemindersUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Filled.Notifications,
                            heading = stringResource(Res.string.reminders_none),
                            body = stringResource(Res.string.empty_reminders_body),
                            actionLabel = stringResource(Res.string.action_add_reminder),
                            onAction = onAddReminder,
                        )
                    }
                }

                is RemindersUiState.Content -> {
                    // Snapshotted once, before the list: `null` means "not read yet", so no
                    // surface renders until the coordinator's first read lands. The askable value
                    // is computed here so the row's copy and its tap target cannot disagree.
                    val ui = permissionUi
                    val askable = ui != null &&
                        NotificationPermissionPolicy.isAskable(ui.permission, ui.hasRequestedSystemPrompt)
                    val showStatusRow = ui != null &&
                        NotificationPermissionPolicy.shouldShowSurface(
                            ui.permission,
                            hasEnabledRule = state.rows.any { it.rule.enabled },
                        )

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        // `md` 16px around the list, `3xl` 64px at the bottom to clear the FAB.
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 64.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // The same component as the home banner, under the same policy, as the
                        // first item of this Content state only — never in Loading, Empty or
                        // Error. Its 16dp gap to the first rule row comes free from the list's own
                        // `spacedBy(16.dp)`. The tap reuses the dialog hosted below: askable →
                        // the explanation dialog (the manual way back after a "Not now"), denied →
                        // straight to the OS app settings (D-61, amended 2026-10-23).
                        if (showStatusRow) {
                            item(key = "permission-status") {
                                ReminderStatusRow(
                                    askable = askable,
                                    onClick = {
                                        if (askable) {
                                            showPermissionDialog = true
                                        } else {
                                            coordinator.openSettings()
                                        }
                                    },
                                )
                            }
                        }

                        // Keys are mandatory: rows re-order as due states change, and a key keeps
                        // each row's switch and click target attached to its own rule.
                        items(state.rows, key = { row -> row.rule.id }) { row ->
                            ReminderCard(
                                row = row,
                                onOpen = { onOpenRule(row.rule.id) },
                                onToggle = { enabled -> viewModel.toggle(row.rule.id, enabled) },
                            )
                        }
                    }
                }

                is RemindersUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.error_load_reminders),
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

            // FAB overlay outside the when so the add control is present in every state (D-19/D-42).
            FloatingActionButton(
                onClick = onAddReminder,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.cd_add_reminder),
                )
            }

            // Outside the state `when`: the automatic post-save ask and a manual tap on the
            // inline status row (Task 3) both drive the same flag and the same dialog instance —
            // there is no second dialog and no second state.
            if (showPermissionDialog) {
                NotificationsPermissionDialog(
                    onConfirm = {
                        showPermissionDialog = false
                        coordinator.requestPermission()
                    },
                    onDismiss = { showPermissionDialog = false },
                )
            }
        }
    }
}
