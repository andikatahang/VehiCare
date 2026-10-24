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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderRow
import com.vehicare.domain.Vehicle
import com.vehicare.ui.theme.Danger
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.util.PhotoStorage
import com.vehicare.util.ReminderCopy
import com.vehicare.util.ServiceDates
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.service_summary_last
import vehicare.shared.generated.resources.vehicle_due_counter
import vehicare.shared.generated.resources.vehicle_no_active_reminders

/** The locked separator between the status line's segments — a code constant, never a resource. */
private const val STATUS_LINE_SEPARATOR = " · "

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
 *
 * [top], [otherDueCount] and [lastServiceEpochMs] feed the card's one DSH-01 status line
 * (D-58/D-59/D-60) — the dashboard's only addition to this card. There is exactly one call site
 * ([com.vehicare.ui.vehicles.VehicleListScreen]), so none of the three carries a default: a
 * missing status must be a compile error, not a silently blank line.
 */
@Composable
fun VehicleCard(
    vehicle: Vehicle,
    top: ReminderRow?,
    otherDueCount: Int,
    lastServiceEpochMs: Long?,
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
                val statusLine = vehicleStatusLine(top, otherDueCount, lastServiceEpochMs)
                Text(
                    text = statusLine,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (top?.evaluation?.state is DueState.Overdue ||
                        top?.evaluation?.state == DueState.DueNow
                    ) {
                        Danger
                    } else {
                        Slate
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * The card's one DSH-01 status line (D-58/D-59, amended 2026-10-23): `Last service {date} ·
 * {due sentence}` whenever the vehicle has a service entry, falling back to `{type} ·
 * {sentence}` without one, plus ` · +N due` when at least one other enabled rule is due, or the
 * bare `No active reminders` (optionally prefixed by the date) when the vehicle has no enabled
 * rule at all. The date segment is produced only through the last-service string resource and
 * the app's one date formatter — this function never formats a date by hand (C-30).
 */
@Composable
private fun vehicleStatusLine(
    top: ReminderRow?,
    otherDueCount: Int,
    lastServiceEpochMs: Long?,
): String {
    val dateSegment = lastServiceEpochMs?.let {
        stringResource(Res.string.service_summary_last, ServiceDates.display(it))
    }

    if (top == null) {
        val noActiveReminders = stringResource(Res.string.vehicle_no_active_reminders)
        return dateSegment?.let { it + STATUS_LINE_SEPARATOR + noActiveReminders }
            ?: noActiveReminders
    }

    val lead = dateSegment ?: top.rule.serviceType
    val dueSentence = ReminderCopy.statusSentenceText(top.rule, top.evaluation?.state)
    val base = lead + STATUS_LINE_SEPARATOR + dueSentence
    return if (otherDueCount > 0) {
        base + STATUS_LINE_SEPARATOR + stringResource(Res.string.vehicle_due_counter, otherDueCount)
    } else {
        base
    }
}
