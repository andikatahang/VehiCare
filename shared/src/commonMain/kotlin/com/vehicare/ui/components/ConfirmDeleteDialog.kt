package com.vehicare.ui.components

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
import vehicare.shared.generated.resources.action_cancel

/**
 * The shared delete-confirmation gate for both vehicles and service entries (D-18, C-23).
 *
 * **This dialog is the only code path that reaches a destructive ViewModel call** — for
 * vehicles ([VehicleDetailViewModel.delete]) and for service entries
 * ([ServiceEntryDetailViewModel.delete]). A later author adding a second call site is
 * bypassing the confirmation gate that makes an irreversible action safe, so any new
 * entry point must go through this dialog rather than around it.
 *
 * The copy is caller-resolved through [stringResource] and passed as plain [String]s:
 * - [title] the dialog heading (e.g. "Delete vehicle" / "Delete service entry").
 * - [message] the locked sentence naming the subject (the caller formats it, so this
 *   component holds no entity-specific vocabulary).
 * - [confirmLabel] the destructive button label (e.g. "Delete vehicle" / "Delete service entry"),
 *   rendered in the palette's danger tone.
 * - [onConfirm] the user chose deletion; the caller closes the dialog and starts the delete.
 * - [onDismiss] every non-confirming exit: back, outside tap, Escape, or the Cancel button —
 *   the dialog closes and nothing is removed.
 *
 * Visual guarantees (unchanged from the vehicle-only dialog):
 * - The dialog container is pinned to `Paper`, never Material's container tint (C-8).
 * - Title in `titleLarge`, body in `bodyLarge`.
 * - Confirm button: `TextButton` at 48dp minimum height, `contentColor = Danger`.
 * - Cancel button: `TextButton` at 48dp minimum height, `contentColor = Ink` — deliberately
 *   **not** danger-tinted: a red Cancel would read as the destructive choice, and the safe
 *   option must not look like the dangerous one (UI-SPEC §Interaction rules → Confirm dialog,
 *   §Accessibility → No colour-only meaning).
 * - `onDismissRequest = onDismiss`, so backing out, tapping outside and Escape all mean cancel —
 *   deletion is reachable only through the confirm button's [onConfirm].
 * - Cancelling is the safe default in layout terms: the neutral option sits on the leading
 *   side of the M3 button row. Both buttons are worded rather than icon-only and both are at
 *   least 48dp tall (UI-SPEC §Accessibility → Touch targets).
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // The dialog is a background surface, so it takes the dominant `Paper` token rather than
        // Material's own container tint, which is outside this app's declared palette (C-8) — the
        // same reason the vehicle-type picker is re-coloured from the palette.
        containerColor = Paper,
        title = {
            Text(
                text = title,
                // The dialog's heading is the `Heading` role, never `Display`: the vehicle name on
                // the Detail screen behind this dialog stays the single largest text in the app.
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Text(text = confirmLabel)
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