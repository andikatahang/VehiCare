package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.detail_service_history_coming_soon

/**
 * The reserved service-history slot on the Detail screen — **this is the Phase 2 integration
 * point**, placed now so the next phase drops its content into an existing structure instead of
 * restyling the screen (D-18).
 *
 * It is a deliberate placeholder, not a feature: a `Mist` container with a 12dp corner radius and
 * `md` 16px inner padding holding one line of `Label`/`Slate` copy. There is no glyph, no chevron,
 * no button and no navigation here, because a tappable affordance would promise a capability
 * Phase 1 does not ship. When Phase 2 lands it replaces or wraps this slot — nothing outside this
 * file needs to change.
 */
@Composable
fun ComingSoonSlot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Mist)
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(Res.string.detail_service_history_coming_soon),
            style = MaterialTheme.typography.labelLarge,
            color = Slate,
        )
    }
}
