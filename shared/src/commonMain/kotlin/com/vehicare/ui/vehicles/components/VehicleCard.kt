package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vehicare.domain.Vehicle
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.util.PhotoStorage

/**
 * Joins the optional make, model and year into the card's meta line (D-14).
 *
 * A blank segment disappears together with its separator (C-3): a missing year renders
 * `make · model`, and when every segment is blank the function returns an empty string so the
 * caller can drop the line entirely instead of drawing a lone separator. Null and blank-after-trim
 * mean the same thing.
 */
internal fun vehicleMetaLine(make: String?, model: String?, year: String?): String =
    listOf(make, model, year)
        .mapNotNull { segment -> segment?.trim()?.takeIf { it.isNotEmpty() } }
        .joinToString(separator = " · ")

/**
 * One vehicle as an identity card (D-14): a uniform 64dp leading visual — the photo when the
 * vehicle has one, the type circle otherwise — then the name, the meta line and the plate chip.
 *
 * The whole row is one M3 `Card` whose click is the only gesture: deletion lives on the Detail
 * screen behind a confirmation (D-18), so no long-press can ever be expressed here. The card
 * merges its descendants so it is announced as a single node: name, then meta line, then the
 * labelled plate.
 *
 * The photo is loaded from the stored **relative** path through [PhotoStorage.resolve] by Coil,
 * which supplies size-aware downsampling for the 64dp target (RESEARCH Don't Hand-Roll).
 */
@Composable
fun VehicleCard(
    vehicle: Vehicle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val photoPath = vehicle.photoPath?.takeIf { it.isNotBlank() }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Mist),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (photoPath != null) {
                AsyncImage(
                    model = PhotoStorage.resolve(photoPath),
                    // Decorative here: the card merges its descendants, and the labelled photo
                    // description belongs to the Detail hero where the image is its own node.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                VehicleTypeCircle(type = vehicle.type)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = vehicle.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                )
                val metaLine = vehicleMetaLine(vehicle.make, vehicle.model, vehicle.year)
                if (metaLine.isNotEmpty()) {
                    Text(
                        text = metaLine,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate,
                    )
                }
                val plate = vehicle.plate
                if (!plate.isNullOrBlank()) {
                    LicensePlateChip(plate = plate)
                }
            }
        }
    }
}
