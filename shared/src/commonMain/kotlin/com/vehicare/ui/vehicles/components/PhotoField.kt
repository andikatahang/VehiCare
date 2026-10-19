package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vehicare.ui.theme.Mist
import com.vehicare.util.PhotoStorage
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_photo
import vehicare.shared.generated.resources.action_remove_photo
import vehicare.shared.generated.resources.action_replace_photo
import vehicare.shared.generated.resources.cd_vehicle_photo
import vehicare.shared.generated.resources.error_add_photo
import vehicare.shared.generated.resources.title_vehicle_fallback

/**
 * The Add/Edit form's photo block (D-06; UI-SPEC §Component Dimensions → Form photo block): the
 * vehicle's picture at 16:9 with a 12dp radius, or a `Mist` placeholder with a centred 24dp `Add`
 * glyph while none is attached.
 *
 * The picker is the photo library only (D-04): no other source is offered anywhere on this screen.
 * It is obtained **inside composition** on purpose, because on iOS the launcher captures the view
 * controller hosting the composition and one created outside it silently does nothing (RESEARCH
 * Pitfall 10). The image itself is loaded from the stored **relative** location through
 * [PhotoStorage.resolve], so this screen never handles a container-dependent one (T-01-46).
 *
 * Presentational only: it renders [photoPath] and reports intent through [onPicked] / [onRemove].
 * Which file may be removed and when is the ViewModel's decision (T-01-49).
 *
 * @param name the vehicle's name, used only to label the image for screen readers.
 * @param error renders the import-failure copy beneath the block instead of staying silent.
 * @param enabled false while a save or an import is running: every control here goes inert.
 */
@Composable
fun PhotoField(
    photoPath: String?,
    name: String,
    onPicked: (PlatformFile) -> Unit,
    onRemove: () -> Unit,
    error: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val picker = rememberFilePickerLauncher(type = FileKitType.Image) { picked ->
        if (picked != null) onPicked(picked)
    }
    val attached = photoPath?.takeIf { it.isNotBlank() }
    val fallbackName = stringResource(Res.string.title_vehicle_fallback)
    val description = stringResource(
        Res.string.cd_vehicle_photo,
        if (name.isBlank()) fallbackName else name,
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (attached == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Mist)
                        // Merged into a single labelled node: the glyph carries the control's name,
                        // so the placeholder is never an unlabelled target.
                        .semantics(mergeDescendants = true) {}
                        .clickable(enabled = enabled) { picker.launch() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(Res.string.action_add_photo),
                        modifier = Modifier.size(24.dp),
                    )
                }
            } else {
                AsyncImage(
                    model = PhotoStorage.resolve(attached),
                    contentDescription = description,
                    // Crop inside a fixed 16:9 frame: no photo the user picks can distort the
                    // form's layout, whatever its own aspect ratio is.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (attached == null) {
                TextButton(
                    onClick = { picker.launch() },
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(text = stringResource(Res.string.action_add_photo))
                }
            } else {
                OutlinedButton(
                    onClick = { picker.launch() },
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(text = stringResource(Res.string.action_replace_photo))
                }
                TextButton(
                    onClick = onRemove,
                    enabled = enabled,
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                ) {
                    Text(text = stringResource(Res.string.action_remove_photo))
                }
            }
        }

        if (error) {
            Text(
                text = stringResource(Res.string.error_add_photo),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
