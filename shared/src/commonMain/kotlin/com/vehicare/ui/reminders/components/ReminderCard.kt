package com.vehicare.ui.reminders.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vehicare.domain.DueState
import com.vehicare.domain.ReminderRow
import com.vehicare.ui.theme.Danger
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import com.vehicare.util.ReminderCopy
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.cd_edit_reminder
import vehicare.shared.generated.resources.cd_reminder_switch

/**
 * One reminder rule as a row: its service type, its interval sentence, its due-state sentence and an
 * independent on/off switch (D-43).
 *
 * Presentational only — no repository, no ViewModel and no state of its own. Both sentences come from
 * [ReminderCopy], so this row and the dashboard card and the notification body cannot drift (C-28).
 *
 * **Two accessibility stops, on purpose.** The container carries **no** click action and does **not**
 * merge its descendants: it holds a live [Switch], and merging would swallow the switch's role and
 * state, leaving a screen-reader user unable to toggle it. The click action instead sits on the text
 * [Column], which merges its own three lines into one labelled item — so TalkBack announces the row
 * once ("Brake service, Every 5.000 km, Overdue by 300 km, Edit reminder") and then the switch as its
 * own control. Putting the click on the outer container would produce a focusable, label-less button
 * node and three stops per row (UI-SPEC §Rule row anatomy).
 *
 * @param row      the rule plus its evaluation; `evaluation` is `null` exactly when the rule is off
 * @param onOpen   tap on the text column — opens the editor in edit mode
 * @param onToggle the switch — one tap, no confirmation, no optimistic local state (D-43)
 */
@Composable
fun ReminderCard(
    row: ReminderRow,
    onOpen: () -> Unit,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val editLabel = stringResource(Res.string.cd_edit_reminder)
    val switchDescription = stringResource(Res.string.cd_reminder_switch, row.rule.serviceType)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Mist)
            .heightIn(min = 72.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClickLabel = editLabel, onClick = onOpen)
                .semantics(mergeDescendants = true) {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // D-40/D-44: the stored label verbatim — never rewritten, normalized or title-cased.
            Text(
                text = row.rule.serviceType,
                style = MaterialTheme.typography.titleMedium,
                color = Ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = ReminderCopy.intervalSentenceText(row.rule),
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
            )
            Text(
                text = ReminderCopy.statusSentenceText(row.rule, row.evaluation?.state),
                style = MaterialTheme.typography.bodyLarge,
                color = statusColor(row.evaluation?.state),
            )
        }

        Switch(
            checked = row.rule.enabled,
            onCheckedChange = onToggle,
            modifier = Modifier.semantics { contentDescription = switchDescription },
            // Palette-locked (C-33). The switch position, the announced state and the "Turned off"
            // line all carry the meaning — never colour alone.
            colors = SwitchDefaults.colors(
                checkedThumbColor = Paper,
                checkedTrackColor = Ink,
                uncheckedThumbColor = Slate,
                uncheckedTrackColor = Mist,
                uncheckedBorderColor = Hairline,
            ),
        )
    }
}

/**
 * The due-state line's tone: `Danger` for the two states that mean "due now or already late", `Slate`
 * for everything else (D-60/C-41). A disabled rule has no state and therefore never renders danger.
 */
private fun statusColor(state: DueState?): Color = when (state) {
    is DueState.Overdue, DueState.DueNow -> Danger
    else -> Slate
}
