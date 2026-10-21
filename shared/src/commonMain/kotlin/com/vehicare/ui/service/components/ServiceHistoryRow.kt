package com.vehicare.ui.service.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vehicare.domain.ServiceEntry
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.util.RupiahFormat
import org.jetbrains.compose.resources.stringResource
import com.vehicare.ui.service.serviceMetaLine
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.value_empty

@Composable
fun ServiceHistoryRow(
    entry: ServiceEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Mist),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // temporary compile bridge — 02-08 replaces this with serviceTypeLine(...)
                Text(
                    text = entry.serviceTypes.firstOrNull().orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = serviceMetaLine(entry.dateEpochMs, entry.mileageKm),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate,
                )
            }
            Text(
                text = entry.costRupiah?.let { RupiahFormat.formatRupiah(it) } ?: stringResource(Res.string.value_empty),
                style = MaterialTheme.typography.titleMedium,
                color = if (entry.costRupiah != null) Ink else Slate,
            )
        }
    }
}