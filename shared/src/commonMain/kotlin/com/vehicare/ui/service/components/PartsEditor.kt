package com.vehicare.ui.service.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.vehicare.ui.service.PartRow
import com.vehicare.ui.service.ServiceEntryFormValidation
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_part
import vehicare.shared.generated.resources.cd_remove_part
import vehicare.shared.generated.resources.field_part_name
import vehicare.shared.generated.resources.field_quantity

/**
 * Inline parts editor — a `Column(spacedBy(8.dp))` of one-line rows:
 * name `OutlinedTextField(weight(1f))` + `sm` 8px gap + quantity `OutlinedTextField` fixed 72dp,
 * digits only (numeric keyboard, filtered in ViewModel), defaulting to "1". Trailing remove
 * `IconButton` 48dp with `Close` glyph and `cd_remove_part` content description (index-parameterized).
 *
 * Removal is immediate, no confirmation (unsaved-form edit). An "Add part" `TextButton`
 * (`action_add_part`, 48dp) appends an empty row at the end. The form starts with zero rows.
 * No autofocus anywhere.
 *
 * Presentational only: renders `rows` and reports intent via `onPartChange` / `onAddPart` / `onRemovePart`.
 */
@Composable
fun PartsEditor(
    rows: List<PartRow>,
    onPartChange: (Int, String, String) -> Unit,
    onAddPart: () -> Unit,
    onRemovePart: (Int) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = row.name,
                    onValueChange = { name -> onPartChange(index, name, row.quantity) },
                    modifier = Modifier
                        .weight(1f)
                        .defaultMinSize(minHeight = 48.dp),
                    label = { Text(text = stringResource(Res.string.field_part_name)) },
                    singleLine = true,
                    enabled = enabled,
                )

                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { qty -> onPartChange(index, row.name, qty) },
                    modifier = Modifier
                        .width(72.dp)
                        .defaultMinSize(minHeight = 48.dp),
                    label = { Text(text = stringResource(Res.string.field_quantity)) },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                IconButton(
                    onClick = { if (enabled) onRemovePart(index) },
                    modifier = Modifier
                        .size(48.dp)
                        .defaultMinSize(minHeight = 48.dp),
                    enabled = enabled,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(Res.string.cd_remove_part, index + 1),
                        tint = Slate,
                    )
                }
            }
        }

        TextButton(
            onClick = { if (enabled) onAddPart() },
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            enabled = enabled,
        ) {
            Text(
                text = stringResource(Res.string.action_add_part),
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
            )
        }
    }
}