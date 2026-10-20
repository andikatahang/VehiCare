package com.vehicare.ui.service.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Paper
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_type
import vehicare.shared.generated.resources.action_cancel
import vehicare.shared.generated.resources.dialog_add_type_title
import vehicare.shared.generated.resources.field_service_type

/**
 * The one-field prompt to add a custom service type (D-26/D-40).
 *
 * Title "Add service type" (`Heading`), one `OutlinedTextField` (`singleLine`, label "Service type",
 * max 60 chars, trimmed, no case transform). Confirm `TextButton` "Add type" (`Ink`, disabled
 * while the trimmed value is blank). Dismiss `TextButton` "Cancel" (`Ink`). Every dismiss path
 * changes nothing — the callback is only invoked on confirm with the trimmed label.
 *
 * The caller (ViewModel) performs the case-insensitive collapse against presets and learned labels
 * — this component never rewrites or inspects stored values (D-40).
 *
 * Presentational only: no repository, no ViewModel, no autofocus.
 */
@Composable
fun ServiceTypeDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf("") }

    val trimmed = text.trim()
    val canConfirm = trimmed.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = {
            Text(
                text = stringResource(Res.string.dialog_add_type_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(text = stringResource(Res.string.field_service_type)) },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                visualTransformation = VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(trimmed) },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink),
                enabled = canConfirm,
            ) {
                Text(text = stringResource(Res.string.action_add_type))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink),
            ) {
                Text(text = stringResource(Res.string.action_cancel))
            }
        },
    )
}