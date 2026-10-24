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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.notifications_off_ask_body
import vehicare.shared.generated.resources.notifications_off_denied_body
import vehicare.shared.generated.resources.notifications_off_title

/**
 * The one "notifications are off" surface, used in **two** placements (D-61): the home banner on
 * the Vehicles tab and the inline status row at the top of the Reminders list. There is no second
 * banner composable anywhere — both placements gate on the same policy and render this component.
 *
 * The whole surface is one merged clickable node with **no close button**: the state it describes
 * persists until it is fixed, so dismissing it would only hide a true statement (UI-SPEC
 * §"Notifications off" — "not dismissible").
 *
 * [askable] chooses the body only. `true` → [notifications_off_ask_body] ("…until you turn them
 * on."), `false` → [notifications_off_denied_body] ("…Turn them on in your device settings."). The
 * title never changes, which is what lets the two states read as the same problem with two
 * remedies.
 *
 * The leading glyph is deliberately `Notifications` — the same one the Reminders empty state uses.
 * `NotificationsOff`, `Alarm`, `Timer`, `Schedule` and `Speed` do not exist in
 * `material-icons-core:1.7.3`, so any of them would fail to compile. Both glyphs are decorative
 * (`contentDescription = null`) because the row's text carries the meaning.
 */
@Composable
fun ReminderStatusRow(
    askable: Boolean,
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
            .heightIn(min = 56.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Notifications,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Slate,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(Res.string.notifications_off_title),
                style = MaterialTheme.typography.labelLarge,
                color = Ink,
            )
            Text(
                text = if (askable) {
                    stringResource(Res.string.notifications_off_ask_body)
                } else {
                    stringResource(Res.string.notifications_off_denied_body)
                },
                style = MaterialTheme.typography.bodyLarge,
                color = Slate,
            )
        }
        Icon(
            imageVector = Icons.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = Slate,
        )
    }
}
