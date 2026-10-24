package com.vehicare.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vehicare.appVersionName
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.error_interval_months_universal
import vehicare.shared.generated.resources.settings_app_version
import vehicare.shared.generated.resources.settings_service_interval_field
import vehicare.shared.generated.resources.settings_service_interval_hint
import vehicare.shared.generated.resources.settings_service_interval_label
import vehicare.shared.generated.resources.settings_service_interval_save
import vehicare.shared.generated.resources.settings_service_interval_saved
import vehicare.shared.generated.resources.title_settings

/**
 * The D-17 Settings screen.
 *
 * **App version:** the placeholder row — version label on the left, runtime version on the right.
 *
 * **Service interval** (261024-u9g, D-1): a clearly separated section below the App-version row.
 * One universal monthly interval overriding every reminder rule's own interval; the blank field is
 * the unset state and the hint names the fallback the rules then use. Saving fires the scheduler
 * seam so delivery re-arms on both platforms — a change here is effective immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = koinViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(text = stringResource(Res.string.title_settings)) })
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(Res.string.settings_app_version),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = appVersionName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ServiceIntervalSection(viewModel)
        }
    }
}

/**
 * The universal month-interval setting: label, numeric field, blank-state hint, Save and the
 * confirmation of the last successful write (and the invalid-input error line).
 *
 * The field shows the store's value until the user types (`edited` overrides it), so a save is
 * immediate and an external store change still lands; typing keeps the digits-only filter inside
 * the ViewModel, exactly like the editor's fields.
 */
@Composable
private fun ServiceIntervalSection(viewModel: SettingsViewModel) {
    val storeText by viewModel.intervalText.collectAsState()
    val intervalError by viewModel.intervalError.collectAsState()
    val savedFlash by viewModel.savedFlash.collectAsState()
    var edited by remember { mutableStateOf<String?>(null) }

    val shown = edited ?: storeText

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.settings_service_interval_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = shown,
            onValueChange = { raw ->
                edited = viewModel.onIntervalChange(raw)
                viewModel.onSavedShown()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.settings_service_interval_field)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            isError = intervalError,
            supportingText = if (intervalError) {
                {
                    Text(
                        text = stringResource(Res.string.error_interval_months_universal),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                null
            },
        )
        if (shown.isBlank()) {
            Text(
                text = stringResource(Res.string.settings_service_interval_hint),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = {
                    viewModel.save(shown)
                    // The store now holds the saved value; stop overriding the field with the
                    // typed text so the queued external confirmation shows through.
                    if (!viewModel.intervalError.value) edited = null
                },
            ) {
                Text(text = stringResource(Res.string.settings_service_interval_save))
            }
            if (savedFlash) {
                Text(
                    text = stringResource(Res.string.settings_service_interval_saved),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
