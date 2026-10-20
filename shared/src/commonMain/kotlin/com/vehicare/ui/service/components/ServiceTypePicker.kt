package com.vehicare.ui.service.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vehicare.domain.ServiceTypePresets
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_type

/**
 * The service-type chip group (D-26): presets → learned → "Add custom…" in a wrapping layout.
 *
 * The six presets are code constants from [ServiceTypePresets.ALL] (locked order: Oil change,
 * Tire rotation, Brake service, Inspection, Battery, Filters). Learned labels come from the
 * repository already filtered for case-insensitive preset matches (D-40), newest-used first.
 *
 * Every chip is at least 48dp tall (UI-SPEC Accessibility). The selected chip shows a leading
 * 16dp `Check` glyph and takes `Ink` border/label on `Paper`; unselected chips are `Mist`/`Slate`/`Hairline`.
 * "Add custom…" is an `AssistChip` with a leading `Add` glyph and `Mist`/`Hairline`/`Ink` treatment.
 * Nothing is preselected on add — the form's submit gate requires an explicit choice (D-27/D-31).
 *
 * Presentational only: renders the given lists and reports intent via [onSelect] / [onAddCustom].
 * No component calls a repository or ViewModel and none autofocuses (D-31).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceTypePicker(
    presets: List<String> = ServiceTypePresets.ALL,
    learned: List<String> = emptyList(),
    selected: String?,
    onSelect: (String) -> Unit,
    onAddCustom: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val allOptions = buildList {
        addAll(presets)
        addAll(learned)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // FlowRow measures real chip widths, so wrapping stays correct at any screen width,
        // font scale and selection state (WR-09 / D-26 / C-15).
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            allOptions.forEach { option ->
                renderChip(option, option == selected, enabled, onSelect)
            }
        }

        // "Add custom…" AssistChip — always last, on its own row if needed
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(
                onClick = { if (enabled) onAddCustom() },
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .width(IntrinsicSize.Min),
                enabled = enabled,
                label = {
                    Text(
                        text = stringResource(Res.string.action_add_type),
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = Ink,
                    )
                },
            )
        }
    }
}

@Composable
private fun renderChip(
    option: String,
    isSelected: Boolean,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    FilterChip(
        selected = isSelected,
        onClick = { if (enabled) onSelect(option) },
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .width(IntrinsicSize.Min),
        enabled = enabled,
        label = {
            Text(
                text = option,
                style = MaterialTheme.typography.labelLarge,
            )
        },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Ink,
                )
            }
        } else {
            null
        },
    )
}