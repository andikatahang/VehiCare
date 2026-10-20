package com.vehicare.ui.service

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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vehicare.ui.components.EmptyState
import com.vehicare.ui.service.components.HistorySortControl
import com.vehicare.ui.service.components.ServiceHistoryRow
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_service
import vehicare.shared.generated.resources.action_try_again
import vehicare.shared.generated.resources.cd_add_service
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.empty_service_history_body
import vehicare.shared.generated.resources.empty_service_history_heading
import vehicare.shared.generated.resources.error_load_history
import vehicare.shared.generated.resources.loading_service_history
import vehicare.shared.generated.resources.title_service_history

/**
 * The Service history destination: a sortable, keyed list of a vehicle's entries with its
 * empty/loading/error states and the "Add service" entry point.
 *
 * Navigation is injected, never owned: the screen receives callbacks so the single graph in
 * `App.kt` stays the only place that knows about destinations.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceHistoryRoute(
    vehicleId: Long,
    onAddService: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onBack: () -> Unit,
    viewModel: ServiceHistoryViewModel = koinViewModel(parameters = { parametersOf(vehicleId) }),
) {
    val sort by viewModel.sort.collectAsState()
    val state = viewModel.uiState.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.title_service_history),
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
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            when (state) {
                is ServiceHistoryUiState.Loading -> {
                    val loadingLabel = stringResource(Res.string.loading_service_history)
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.semantics { contentDescription = loadingLabel },
                        )
                    }
                }

                is ServiceHistoryUiState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyState(
                            icon = Icons.Filled.Build,
                            heading = stringResource(Res.string.empty_service_history_heading),
                            body = stringResource(Res.string.empty_service_history_body),
                            actionLabel = stringResource(Res.string.action_add_service),
                            onAction = onAddService,
                        )
                    }
                }

                is ServiceHistoryUiState.Content -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        HistorySortControl(
                            selected = sort,
                            onSelect = viewModel::onSortSelected,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 64.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            items(state.entries, key = { entry -> entry.id }) { entry ->
                                ServiceHistoryRow(
                                    entry = entry,
                                    onClick = { onOpenEntry(entry.id) },
                                )
                            }
                        }
                    }
                }

                is ServiceHistoryUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = stringResource(Res.string.error_load_history),
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

            // FAB overlay outside the when so the add control is present in every state (C-5, D-36)
            FloatingActionButton(
                onClick = onAddService,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.cd_add_service),
                )
            }
        }
    }
}