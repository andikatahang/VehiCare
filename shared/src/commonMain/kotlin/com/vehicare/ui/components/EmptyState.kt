package com.vehicare.ui.components

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import org.jetbrains.compose.resources.stringResource

/**
 * The shared empty state: the first-run guidance that turns an empty screen into the first add.
 * Serves both the vehicle list (D-15) and the service history screen (D-36) — the caller owns
 * every word and the visual, and the component owns the shape.
 *
 * A 64dp `Mist` circle (`CircleShape`) holding the icon at `Modifier.size(24.dp)`, `xl` 32px to
 * the heading (`titleLarge`/`Ink`, centred), `sm` 8px to the body (`bodyLarge`/`Slate`, centred),
 * `md` 24px to a filled `Button` carrying `actionLabel`; the icon stays decorative
 * (`contentDescription = null`) and the button keeps the theme's `primary` container as-is so the
 * later accent swap re-tints it without touching this file. The column keeps `modifier.padding(24.dp)`
 * and centred horizontal alignment.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    heading: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
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
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = heading,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 32.dp),
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            color = Slate,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        Button(
            onClick = onAction,
            modifier = Modifier.padding(top = 24.dp),
        ) {
            Text(text = actionLabel)
        }
    }
}