package com.vehicare.ui.reminders.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Paper
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_not_now
import vehicare.shared.generated.resources.action_turn_on
import vehicare.shared.generated.resources.dialog_notifications_message
import vehicare.shared.generated.resources.dialog_notifications_title

/**
 * The fixed-copy explanation dialog that precedes the OS notification prompt (UI-SPEC
 * §"The notification explanation").
 *
 * No string parameters: there is exactly one permission message in this phase, so this component
 * resolves [dialog_notifications_title], [dialog_notifications_message], [action_turn_on] and
 * [action_not_now] itself. A caller cannot invent copy, and a second dialog with different words
 * cannot be built without editing this file.
 *
 * Container pinned to `Paper` for the same reason as `ConfirmDeleteDialog` (C-8). Both buttons are
 * 48dp and `Ink` — [onDismiss] is deliberately **not** danger-tinted and deliberately not worded
 * "Cancel": it defers a permission ask, it does not cancel anything. This dialog holds no data
 * layer or screen-state owner and no destination graph reference, and it cannot write, delete or
 * schedule anything by itself — [onConfirm] only starts the platform request through whichever
 * coordinator the caller already holds.
 */
@Composable
fun NotificationsPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Paper,
        title = {
            Text(
                text = stringResource(Res.string.dialog_notifications_title),
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = stringResource(Res.string.dialog_notifications_message),
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink),
            ) {
                Text(text = stringResource(Res.string.action_turn_on))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink),
            ) {
                Text(text = stringResource(Res.string.action_not_now))
            }
        },
    )
}
