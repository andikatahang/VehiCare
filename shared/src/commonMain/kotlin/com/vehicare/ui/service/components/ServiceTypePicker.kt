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
import com.vehicare.ui.service.SelectionMode
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_type

/**
 * The service-type chip group (D-26, superseded 2026-10-21): presets → learned → "Add custom…" in a
 * wrapping layout.
 *
 * **Multi-select** is the approved contract (user decision 2026-10-21, UAT gap 2): tapping a chip
 * toggles it, any number of types may be selected, and any selected chip may be deselected. That
 * overrides D-26's original one-of rule; its order, geometry, palette and glyph rules stay in force.
 * M3 gives each chip `Role.Checkbox`, so every chip announces its own state and selection is never
 * carried by colour alone.
 *
 * [selectionMode] adds the reminder editor's one-of model (C-32) **without a second component**: it
 * defaults to [SelectionMode.Multiple], so every existing call site compiles and behaves exactly as
 * before, and the editor passes [SelectionMode.Single]. In single mode a tap on another chip moves
 * the selection and a tap on the already-selected chip is a no-op — never a deselect.
 *
 * **Recorded limitation (do not "fix" it by hand-rolling a chip):** the pinned M3 `FilterChip` is
 * built on `SelectableChip`, which applies `semantics { role = Role.Checkbox }` *inside* the
 * component, after the caller's modifier. An external `Role.RadioButton` would therefore be
 * overridden. A single-select group announces checkbox semantics; that is accepted because exactly
 * one chip is always selected once the user picks (UI-SPEC Verification Notes #3).
 *
 * The six presets are code constants from [ServiceTypePresets.ALL] (locked order: Oil change,
 * Tire rotation, Brake service, Inspection, Battery, Filters). Learned labels come from the
 * repository already filtered for case-insensitive preset matches (D-40), newest-used first.
 *
 * [allOptions] is `presets + learned + any selected label not already present`. That union is a
 * **defensive** guard, not the primary fix for UAT gap 1: a selected value must always have a chip to
 * render, even if the learned list is momentarily stale (for example right after a custom confirm).
 *
 * Every chip is at least 48dp tall (UI-SPEC Accessibility). Every selected chip shows a leading
 * 16dp `Check` glyph and takes `Ink` border/label on `Paper`; unselected chips are `Mist`/`Slate`/`Hairline`.
 * "Add custom…" is an `AssistChip` with a leading `Add` glyph and `Mist`/`Hairline`/`Ink` treatment.
 * Nothing is preselected on add — the form's submit gate requires at least one type (D-27/D-31).
 *
 * Presentational only: renders the given lists and reports intent via [onToggle] / [onAddCustom].
 * No component calls a repository or ViewModel and none autofocuses (D-31).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServiceTypePicker(
    presets: List<String> = ServiceTypePresets.ALL,
    learned: List<String> = emptyList(),
    selected: List<String> = emptyList(),
    onToggle: (String) -> Unit,
    onAddCustom: () -> Unit,
    enabled: Boolean = true,
    selectionMode: SelectionMode = SelectionMode.Multiple,
    modifier: Modifier = Modifier,
) {
    val allOptions = buildList {
        addAll(presets)
        addAll(learned)
        // Defensive union: a selected label that appears in neither list still gets a chip.
        selected.forEach { label ->
            if (none { it.equals(label, ignoreCase = true) }) add(label)
        }
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
                renderChip(
                    option = option,
                    isSelected = selected.any { it.equals(option, ignoreCase = true) },
                    enabled = enabled,
                    selectionMode = selectionMode,
                    onToggle = onToggle,
                )
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
    selectionMode: SelectionMode,
    onToggle: (String) -> Unit,
) {
    // C-32: in single mode the selected chip's tap is a no-op, so the group can never be emptied
    // by a gesture. The transition itself enforces the same rule; this keeps the ripple honest.
    val tapIsNoOp = isSelected && selectionMode == SelectionMode.Single
    FilterChip(
        selected = isSelected,
        onClick = { if (enabled && !tapIsNoOp) onToggle(option) },
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