package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.value_empty

/**
 * One two-column Detail row: the field label leading in `Label`/`Slate`, its value trailing in
 * `Body`/`Ink`, an `sm` 8px gap between them and a 1dp `Hairline` rule below
 * (UI-SPEC §Detail field rows, §Component Dimensions → Divider).
 *
 * **An absent optional value keeps its row.** A null or blank-after-trim [value] renders the em
 * dash placeholder in `Slate` rather than collapsing the row, so the Detail screen has the same
 * shape for every vehicle and a field filled in later appears in place instead of shifting the
 * layout (C-2, D-09).
 *
 * @param label the already-resolved field name; this component holds no copy of its own (D-11).
 * @param value the raw stored value, or `null` for an optional field that was never filled. A
 *   null, empty or whitespace-only value is the same thing here, and a blank string is never
 *   painted into the value slot.
 * @param trailing an optional composable for the row's right-hand side, used by the license-plate
 *   row to render the shared plate pill instead of plain text.
 *   **When it is non-null the value slot is not rendered at all** — the pill is a non-blank-only
 *   component (C-3), so a row carries either the value text (or its em dash) *or* the pill, never
 *   both. Passing a value alongside a trailing slot is therefore not a way to add a fallback: pass
 *   `null` and let the slot own the row's content.
 * @param showDivider whether the 1dp rule is drawn below this row. The rule separates rows, so the
 *   last row of a block passes `false` and the block never ends on a dangling divider.
 */
@Composable
fun FieldRow(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = true,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = Slate,
            )

            val present = value?.trim()?.takeIf { it.isNotEmpty() }
            if (trailing != null) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    trailing()
                }
            } else {
                Text(
                    text = present ?: stringResource(Res.string.value_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    // The placeholder is secondary information, so it takes the recessive tone and
                    // a real value takes the primary one (UI-SPEC §Color).
                    color = if (present == null) Slate else Ink,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (showDivider) {
            HorizontalDivider(thickness = 1.dp, color = Hairline)
        }
    }
}
