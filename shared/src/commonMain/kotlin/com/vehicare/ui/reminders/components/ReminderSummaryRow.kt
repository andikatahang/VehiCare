package com.vehicare.ui.reminders.components

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vehicare.domain.DueState
import com.vehicare.ui.reminders.ReminderSummary
import com.vehicare.ui.theme.Danger
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.util.ReminderCopy
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.reminder_summary_all_off
import vehicare.shared.generated.resources.reminder_summary_count
import vehicare.shared.generated.resources.reminders_none
import vehicare.shared.generated.resources.title_reminders

/** The locked meta-line separator: a single mid-dot with single spaces, never a resource. */
private const val SEPARATOR = " · "

/**
 * The vehicle Detail's Reminders summary row (D-45): the reserved slot's second occupant,
 * immediately below [com.vehicare.ui.service.components.ServiceSummaryRow].
 *
 * It is **navigation only** — no add control, no rule list, no switch — and it stays tappable at
 * zero rules so the Reminders empty state (which carries the CTA) is always reachable (D-32/D-36,
 * applied to this second row).
 *
 * The title always renders. The meta line renders only once [summary] has resolved (`null` means
 * unresolved or unreadable, never a spinner inside a compact row): `No reminders yet` at zero
 * rules, `{N} reminders · All turned off` when every rule is disabled, otherwise
 * `{N} reminders · {due sentence of the most urgent enabled rule}` — `Danger` toned when that
 * sentence is overdue or due-now.
 */
@Composable
fun ReminderSummaryRow(
    summary: ReminderSummary?,
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
                text = stringResource(Res.string.title_reminders),
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
            )
            if (summary != null) {
                Text(
                    text = summaryMetaLine(summary),
                    style = MaterialTheme.typography.bodyLarge,
                    color = summaryMetaColor(summary),
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
 * The row's three meta-line cases (UI-SPEC §Vehicle Detail — the Reminders summary row).
 *
 * Only called when [summary] is non-null, so there is no fourth "unresolved" branch here — that
 * case is handled by [ReminderSummaryRow] rendering no meta line at all.
 */
@Composable
private fun summaryMetaLine(summary: ReminderSummary): String {
    if (summary.ruleCount == 0) {
        return stringResource(Res.string.reminders_none)
    }
    val countPhrase = pluralStringResource(
        Res.plurals.reminder_summary_count,
        summary.ruleCount,
        summary.ruleCount,
    )
    val top = summary.top ?: return countPhrase + SEPARATOR + stringResource(
        Res.string.reminder_summary_all_off,
    )
    return countPhrase + SEPARATOR + ReminderCopy.statusSentenceText(top.rule, top.evaluation?.state)
}

/** `Danger` when the top rule's sentence is overdue or due-now, `Slate` in every other case. */
private fun summaryMetaColor(summary: ReminderSummary): Color {
    val state = summary.top?.evaluation?.state
    return when (state) {
        is DueState.Overdue, DueState.DueNow -> Danger
        else -> Slate
    }
}
