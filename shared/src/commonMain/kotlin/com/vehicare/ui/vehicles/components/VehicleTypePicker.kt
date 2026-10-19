package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vehicare.domain.VehicleType
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource

/**
 * The single-select vehicle type control (D-10's fixed three-item list, C-5's "exactly one is
 * always selected"): Car · Motorcycle · Truck, in that locked order.
 *
 * The labels come from the same `type_car` / `type_motorcycle` / `type_truck` entries the list card
 * and the Detail screen use, through [labelRes] — one copy source for the type names (D-11). The
 * control renders whatever [selected] says, including the initial `Car`, so there is no nullable
 * "nothing picked yet" state to render.
 *
 * Colours are declared from the app palette rather than M3's defaults, whose unselected/selected
 * container pair reads a lavender `secondaryContainer` that is outside the ink-on-paper language
 * (C-8): the track is `Mist`, the selected segment is `Paper` with an `Ink` outline and label, and
 * selection is additionally carried by the built-in selected-segment icon and the `Role.RadioButton`
 * semantics — never by colour alone.
 *
 * @param enabled while a save is in flight the picker stays readable but refuses input, like every
 *   other field of the form (UI-SPEC Add/Edit `Saving`).
 */
@Composable
fun VehicleTypePicker(
    selected: VehicleType,
    onSelect: (VehicleType) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val types = VehicleType.entries
    val colors = SegmentedButtonDefaults.colors(
        activeContainerColor = Paper,
        activeContentColor = Ink,
        activeBorderColor = Ink,
        inactiveContainerColor = Mist,
        inactiveContentColor = Slate,
        inactiveBorderColor = Hairline,
        disabledActiveContainerColor = Paper,
        disabledActiveContentColor = Slate,
        disabledActiveBorderColor = Hairline,
        disabledInactiveContainerColor = Mist,
        disabledInactiveContentColor = Slate,
        disabledInactiveBorderColor = Hairline,
    )

    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        types.forEachIndexed { index, type ->
            SegmentedButton(
                selected = type == selected,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = types.size),
                // Every interactive element is at least 48dp tall (UI-SPEC Accessibility).
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                enabled = enabled,
                colors = colors,
            ) {
                Text(text = stringResource(type.labelRes()))
            }
        }
    }
}
