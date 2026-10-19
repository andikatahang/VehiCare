package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_vehicle
import vehicare.shared.generated.resources.empty_vehicles_body
import vehicare.shared.generated.resources.empty_vehicles_heading
import vehicare.shared.generated.resources.ic_vehicle_car

/**
 * The list's empty state (D-15): the first-run guidance that turns an empty screen into the first
 * add. A 64dp `Mist` circle with the car glyph, `xl` 32px below it the heading in `Ink`, the body
 * copy in `Slate`, then the prominent filled call to action.
 *
 * The button is a reserved accent surface (UI-SPEC Color): it takes the theme's `primary` container
 * as-is, so the later accent swap re-tints it without touching this file. The visual is decorative —
 * the heading carries the meaning — and the screen keeps the FAB visible alongside this CTA (D-19).
 */
@Composable
fun EmptyState(
    onAddVehicle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Mist),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = vectorResource(Res.drawable.ic_vehicle_car),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = stringResource(Res.string.empty_vehicles_heading),
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = stringResource(Res.string.empty_vehicles_body),
            style = MaterialTheme.typography.bodyLarge,
            color = Slate,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onAddVehicle,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = stringResource(Res.string.action_add_vehicle))
        }
    }
}
