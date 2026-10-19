package com.vehicare.ui.vehicles.components

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
import vehicare.shared.generated.resources.action_delete_vehicle
import vehicare.shared.generated.resources.dialog_delete_message
import vehicare.shared.generated.resources.dialog_delete_title

/**
 * The delete confirmation dialog (D-18): the state's title, the locked message naming the vehicle
 * and its scope, a Cancel that is visually neutral and a Delete that is the only danger-tinted
 * control on the screen.
 *
 * **This dialog is the only code path that reaches `VehicleDetailViewModel.delete`.** A later
 * author adding a second call site is bypassing the confirmation gate that makes an irreversible
 * action safe, so any new entry point must go through this dialog rather than around it.
 *
 * The copy is fixed by the UI-SPEC's copywriting contract — the title, the `%1$s` message and both
 * button labels come from the shared string set (D-11), so no word here is written inline:
 * - Confirm is labelled `Delete vehicle` and carries the palette's danger tone.
 * - Cancel is labelled `Cancel`, takes the `Ink` tone and is deliberately **not** danger-tinted: a
 *   red Cancel would read as the destructive choice, and the safe option must not look like the
 *   dangerous one (UI-SPEC §Interaction rules → Confirm dialog, §Accessibility → No colour-only
 *   meaning).
 * - `onDismissRequest` is [onDismiss], so backing out, tapping outside and Escape all mean cancel —
 *   deletion is reachable only through the confirm button's [onConfirm].
 *
 * Cancelling is also the safe default in layout terms: the neutral option sits on the leading side
 * of the M3 button row. Both buttons are worded rather than icon-only and both are at least 48dp
 * tall (UI-SPEC §Accessibility → Touch targets); the casing of the message is the caller's
 * business — it passes the vehicle's display name, never a blank one.
 *
 * @param vehicleName the name the message names, already resolved to the shared fallback word when
 *   the vehicle's own name is blank, so the sentence never reads "Delete  and all its records?".
 * @param onConfirm the user chose deletion; the caller closes the dialog and starts the delete.
 * @param onDismiss every non-confirming exit: the dialog closes and nothing is removed.
 */
@Composable
fun ConfirmDeleteDialog(
    vehicleName: String,
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
                text = stringResource(Res.string.dialog_delete_title),
                // The dialog's heading is the `Heading` role, never `Display`: the vehicle name on
                // the Detail screen behind this dialog stays the single largest text in the app.
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                // The locked sentence, formatted with the vehicle's display name.
                text = stringResource(Res.string.dialog_delete_message, vehicleName),
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
                Text(text = stringResource(Res.string.action_delete_vehicle))
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
