package com.vehicare.ui.service.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import com.vehicare.util.ServiceDates
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_cancel
import vehicare.shared.generated.resources.action_set_date
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.field_service_date

/**
 * Read-only service date field with trailing DateRange glyph (D-30, UI-SPEC §Component Dimensions).
 *
 * The field is an `OutlinedTextField` (min height 56dp) showing `ServiceDates.display(epochMs)`,
 * trailing 24dp `DateRange` glyph in `Slate` (decorative, `contentDescription = null`). Tapping
 * the whole field opens the picker dialog and **never the keyboard**.
 *
 * The picker dialog uses `rememberDatePickerState` with `initialSelectedDateMillis`,
 * confirm `action_set_date` disabled while no date is selected, dismiss `action_cancel`.
 * Every dismiss path keeps the previous value; no date-range restriction.
 *
 * Params: `epochMs: Long`, `onDateSelected: (Long) -> Unit`, `enabled: Boolean = true`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDateField(
    epochMs: Long,
    onDateSelected: (Long) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }
    val displayText = ServiceDates.display(epochMs)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 56.dp),
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {}, // Read-only — keyboard never opens
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = stringResource(Res.string.field_service_date)) },
            readOnly = true,
            enabled = enabled,
            trailingIcon = {
                Icon(
                    imageVector = Icons.Filled.DateRange,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .padding(end = 16.dp),
                    tint = Slate,
                )
            },
        )

        // Transparent clickable area over the whole field
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 56.dp)
                    .clickable { showPicker = true },
                contentAlignment = Alignment.Center,
            ) {}
        }
    }

    if (showPicker) {
        ServiceDatePickerDialog(
            initialEpochMs = epochMs,
            onDateSelected = { newEpochMs ->
                onDateSelected(newEpochMs)
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * Date picker dialog — Material3 `DatePickerDialog`.
 *
 * Confirm button uses `action_set_date` (NOT `action_ok` — vocabulary rule bans "OK"),
 * disabled while no date is selected. Dismiss button is `action_cancel`. Every dismiss path
 * keeps the previous value.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceDatePickerDialog(
    initialEpochMs: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialEpochMs,
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    pickerState.selectedDateMillis?.let { onDateSelected(it) }
                },
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Ink),
                enabled = pickerState.selectedDateMillis != null,
            ) {
                Text(text = stringResource(Res.string.action_set_date))
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
    ) {
        DatePicker(
            state = pickerState,
            modifier = Modifier.padding(16.dp),
        )
    }
}