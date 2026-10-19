package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.cd_license_plate

/**
 * The `Mist` license-plate chip (UI-SPEC Component Dimensions: 8dp corner radius, `xs` 4px
 * vertical and `sm` 8px horizontal padding, `Label` typography in `Ink`). Used on the list card
 * and, later, on the Detail screen.
 *
 * The plate is optional (D-12), so this chip is only ever composed when the plate is non-blank:
 * the caller decides, and this composable refuses a blank string instead of hiding it, so a
 * caller-side mistake fails loudly in debug rather than painting an empty pill (C-3).
 *
 * The chip announces the plate as a labelled value rather than a bare string, so a merged card
 * reads "... License plate {plate}" (UI-SPEC Card semantics).
 */
@Composable
fun LicensePlateChip(
    plate: String,
    modifier: Modifier = Modifier,
) {
    require(plate.isNotBlank()) {
        "plate must be non-blank when rendering LicensePlateChip (C-3: no empty chip)"
    }
    val plateDescription = stringResource(Res.string.cd_license_plate, plate)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Mist)
            .padding(vertical = 4.dp, horizontal = 8.dp)
            .clearAndSetSemantics { contentDescription = plateDescription },
    ) {
        Text(
            text = plate,
            style = MaterialTheme.typography.labelLarge,
            color = Ink,
        )
    }
}
