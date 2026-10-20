package com.vehicare.ui.service.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vehicare.domain.ServiceSummary
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.util.ServiceDates
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.detail_service_history
import vehicare.shared.generated.resources.service_summary_entries
import vehicare.shared.generated.resources.service_summary_last
import vehicare.shared.generated.resources.service_summary_none

/**
 * The vehicle Detail's Service history summary row (D-32): the reserved slot's replacement.
 *
 * It is **navigation only** — no add control, no entry list — and it stays tappable at zero
 * entries so the history screen's empty state (which carries the CTA) is always reachable
 * (D-32/D-36).
 *
 * The title always renders. The meta line renders only once [summary] has resolved
 * (`null` = unresolved or unreadable, never a spinner inside a compact row): `No entries yet` at
 * zero entries, otherwise `N entries · Last service <date>` — the date produced by
 * [ServiceDates.display], never re-formatted here.
 */
@Composable
fun ServiceSummaryRow(
    summary: ServiceSummary?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Mist)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .heightIn(min = 72.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.detail_service_history),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            if (summary != null) {
                Text(
                    text = summaryMetaLine(summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = Slate,
                )
            }
        }
        // Decorative: the row's label carries the meaning, and the chevron is never accent.
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Slate,
        )
    }
}

/**
 * The row's meta line. A positive count with a missing date degrades to the count phrase rather
 * than a broken sentence (T-02-06-02).
 */
@Composable
private fun summaryMetaLine(summary: ServiceSummary): String {
    if (summary.entryCount == 0) {
        return stringResource(Res.string.service_summary_none)
    }
    val countPhrase = pluralStringResource(
        Res.plurals.service_summary_entries,
        summary.entryCount,
        summary.entryCount,
    )
    val latest = summary.lastServiceEpochMs ?: return countPhrase
    return countPhrase + " · " + stringResource(
        Res.string.service_summary_last,
        ServiceDates.display(latest),
    )
}
