package com.vehicare.ui.service.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vehicare.ui.theme.Hairline
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Paper
import com.vehicare.ui.theme.Slate
import com.vehicare.util.PhotoStorage
import io.github.vinceglb.filekit.PlatformFile
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_add_photo
import vehicare.shared.generated.resources.cd_remove_photo
import vehicare.shared.generated.resources.service_photo_count

/**
 * Photo strip for service entries — N thumbnails in a horizontally scrollable row (D-37).
 *
 * Each thumbnail: 80dp square, 12dp radius, `sm` 8px gaps, `ContentScale.Crop` from
 * `PhotoStorage.resolve(path)`, decorative (`contentDescription = null`). Each has a remove
 * control — 16dp `Close` on a 24dp `Paper` circle with 1dp `Hairline` ring, touch target 48dp,
 * overlaid top-end, `cd_remove_photo` index-parameterized.
 *
 * At zero photos: 16:9 `Mist` empty block with centred `Add` glyph and add affordance
 * (`action_add_photo`). Picker obtained inside composition via `rememberFilePickerLauncher`
 * (mandatory on iOS). At 5 photos the add control is **absent**.
 *
 * Supporting count line renders `service_photo_count` in the `Label` role (C-27).
 *
 * **Read-only mode** (`readOnly = true`): renders the same 80dp thumbnails row and nothing
 * else — no remove controls, no add affordance, no count line, and **no picker launcher is
 * created** (skip `rememberFilePickerLauncher` on that path; it must not be obtained in
 * composition for a read-only surface). `onPicked`/`onRemove` are required (non-null) whenever
 * `readOnly = false` and ignored when it is true.
 *
 * The strip only ever calls `PhotoStorage.resolve` for display — which file may be removed and
 * when is the ViewModel's decision (PhotoField's stated rule carries).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServicePhotoStrip(
    photoPaths: List<String>,
    onPicked: ((PlatformFile) -> Unit)? = null,
    onRemove: ((Int) -> Unit)? = null,
    importing: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    modifier: Modifier = Modifier,
) {
    // Picker launcher ONLY on the interactive path (never on read-only)
    val picker = if (!readOnly) {
        rememberFilePickerLauncher(type = FileKitType.Image) { picked ->
            if (picked != null) onPicked?.invoke(picked)
        }
    } else {
        null
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!readOnly && photoPaths.isEmpty()) {
            // The specified empty anatomy: full-content-width 16:9 Mist block with a centred
            // Add glyph and the add affordance (WR-11 / UI-SPEC Component Dimensions).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mist)
                    .clickable(enabled = enabled && !importing) {
                        picker?.launch()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(Res.string.action_add_photo),
                    modifier = Modifier.size(24.dp),
                    tint = Slate,
                )
            }
        } else {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(photoPaths) { path ->
                    val index = photoPaths.indexOf(path)
                    val removeContentDesc = stringResource(Res.string.cd_remove_photo, index + 1)
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        AsyncImage(
                            model = PhotoStorage.resolve(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                        )

                        if (!readOnly) {
                            // 48dp touch target wraps the 24dp visual, and the target carries the
                            // label the icon itself must not duplicate (WR-06 / C-18).
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clickable(enabled = enabled && !importing) {
                                        onRemove?.invoke(index)
                                    }
                                    .semantics { contentDescription = removeContentDesc },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Paper, RoundedCornerShape(50.dp))
                                        .drawBehind {
                                            drawCircle(
                                                color = Hairline,
                                                radius = 12.dp.toPx(),
                                                style = Stroke(width = 1.dp.toPx()),
                                            )
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = Slate,
                                    )
                                }
                            }
                        }
                    }
                }

                // Add affordance (only when interactive, not at 5 photos)
                if (!readOnly && photoPaths.size < 5) {
                    item {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .background(Mist)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = enabled && !importing) {
                                    picker?.launch()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = stringResource(Res.string.action_add_photo),
                                modifier = Modifier.size(24.dp),
                                tint = Slate,
                            )
                        }
                    }
                }
            }
        }

        // Count line (only when interactive)
        if (!readOnly) {
            Text(
                text = stringResource(Res.string.service_photo_count, photoPaths.size),
                style = MaterialTheme.typography.labelLarge,
                color = Slate,
            )
        }
    }
}