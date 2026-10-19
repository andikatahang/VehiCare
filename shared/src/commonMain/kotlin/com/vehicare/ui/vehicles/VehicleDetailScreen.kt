package com.vehicare.ui.vehicles

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.vehicare.domain.Vehicle
import com.vehicare.ui.theme.Ink
import com.vehicare.ui.theme.Mist
import com.vehicare.ui.theme.Slate
import com.vehicare.ui.vehicles.components.ComingSoonSlot
import com.vehicare.ui.vehicles.components.FieldRow
import com.vehicare.ui.vehicles.components.LicensePlateChip
import com.vehicare.ui.vehicles.components.VehicleTypeCircle
import com.vehicare.ui.vehicles.components.labelRes
import com.vehicare.util.PhotoStorage
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.action_back_to_vehicles
import vehicare.shared.generated.resources.action_delete_vehicle
import vehicare.shared.generated.resources.action_edit
import vehicare.shared.generated.resources.cd_back
import vehicare.shared.generated.resources.cd_vehicle_photo
import vehicare.shared.generated.resources.detail_not_found
import vehicare.shared.generated.resources.field_make
import vehicare.shared.generated.resources.field_model
import vehicare.shared.generated.resources.field_plate
import vehicare.shared.generated.resources.field_type
import vehicare.shared.generated.resources.field_year
import vehicare.shared.generated.resources.title_vehicle_fallback

/**
 * One vehicle's Detail screen (D-18): the hero photo or the type-circle placeholder, the name at
 * `Display` size, the five locked field rows, the reserved Phase 2 service-history slot and the
 * recessive destructive action — under a D-20 simple title bar with a back arrow and one Edit
 * action.
 *
 * All three states of [VehicleDetailUiState] render something: a progress indicator while the row
 * is being read, a not-available message with a way back when it is gone, and the content above.
 * There is no fourth state and no blank area anywhere.
 *
 * The state comes from the ViewModel's streaming flow, so an edit made on the form appears here as
 * soon as the user returns, with nothing on this screen refreshing anything by hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleDetailRoute(
    vehicleId: Long,
    onEdit: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel = koinViewModel<VehicleDetailViewModel>(
        parameters = { parametersOf(vehicleId) },
    )
    val state = viewModel.uiState.collectAsState().value

    // A title bar must never render empty, so the fallback names the destination while the row is
    // still being read or has turned out to be gone.
    val fallbackTitle = stringResource(Res.string.title_vehicle_fallback)
    val title = (state as? VehicleDetailUiState.Content)
        ?.vehicle
        ?.name
        ?.takeIf { it.isNotBlank() }
        ?: fallbackTitle

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(Res.string.cd_back),
                        )
                    }
                },
                actions = {
                    // Edit is a text action with no icon on purpose: the simple title bar stays
                    // clean (D-20, UI-SPEC Interaction rules → Detail → Edit). Delete lives at the
                    // end of the content instead (C-4).
                    TextButton(
                        onClick = onEdit,
                        modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                    ) {
                        Text(text = stringResource(Res.string.action_edit))
                    }
                },
            )
        },
    ) { innerPadding ->
        when (val current = state) {
            VehicleDetailUiState.Loading -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            VehicleDetailUiState.NotFound -> Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(Res.string.detail_not_found),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Slate,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    OutlinedButton(
                        onClick = onBack,
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        Text(text = stringResource(Res.string.action_back_to_vehicles))
                    }
                }
            }

            is VehicleDetailUiState.Content -> VehicleDetailContent(
                vehicle = current.vehicle,
                contentPadding = innerPadding,
                onDelete = { viewModel.delete(onDeleted = onBack) },
            )
        }
    }
}

/**
 * The D-18 body, in the locked order: hero, name, the five field rows, a `2xl` break, the
 * service-history slot, a `2xl` break, then the destructive action last (UI-SPEC §Detail field
 * rows, §Visual Hierarchy).
 *
 * The scroll container is what makes the field block and the slot reachable on a small screen;
 * every spacing value in here is one of the seven declared tokens.
 */
@Composable
private fun VehicleDetailContent(
    vehicle: Vehicle,
    contentPadding: PaddingValues,
    onDelete: () -> Unit,
) {
    val photoPath = vehicle.photoPath?.takeIf { it.isNotBlank() }
    val fallbackName = stringResource(Res.string.title_vehicle_fallback)
    val displayName = vehicle.name.takeIf { it.isNotBlank() } ?: fallbackName

    Column(
        modifier = Modifier
            .padding(contentPadding)
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 48.dp),
    ) {
        // Hero (D-18): full content width at 4:3 with no corner radius. The photo is resolved from
        // its stored **relative** location through the single filesystem seam — never from a raw
        // path and never by constructing a location here (T-01-54).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f),
        ) {
            if (photoPath == null) {
                // The no-photo fallback reuses the declared 64dp type circle (D-14) on a `Mist`
                // block, so the icon language is the same on the list and on this screen instead of
                // inventing a second hero-placeholder size.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Mist),
                    contentAlignment = Alignment.Center,
                ) {
                    VehicleTypeCircle(type = vehicle.type)
                }
            } else {
                AsyncImage(
                    model = PhotoStorage.resolve(photoPath),
                    contentDescription = stringResource(
                        Res.string.cd_vehicle_photo,
                        displayName,
                    ),
                    // Crop inside the declared 4:3 frame: no photo the user picks can distort the
                    // screen's layout, whatever its own aspect ratio is.
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        // The screen's single largest text (UI-SPEC §Typography: `Display` applies to the vehicle
        // name on Detail only) and therefore its focal point.
        Text(
            text = displayName,
            style = MaterialTheme.typography.headlineSmall,
            color = Ink,
            modifier = Modifier.padding(top = 16.dp),
        )

        DetailFieldBlock(
            vehicle = vehicle,
            modifier = Modifier.padding(top = 24.dp),
        )

        Spacer(modifier = Modifier.height(48.dp))
        ComingSoonSlot()
        Spacer(modifier = Modifier.height(48.dp))

        // Rendered here and wired to the ViewModel's delete entry point; **plan 01-10 owns its
        // behaviour**, the confirmation dialog and the failure path. Until that plan lands the call
        // is an explicit no-op stub, so nothing can be destroyed by accident (T-01-56).
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                // The worded label carries the meaning; the glyph is decorative (UI-SPEC
                // §Accessibility: no colour-only meaning).
                contentDescription = null,
            )
            Text(
                text = stringResource(Res.string.action_delete_vehicle),
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * The five locked field rows in the locked order — Make → Model → Year → License plate → Type
 * (C-2) — and nothing else.
 *
 * Every row is always present: an absent optional value renders the em dash rather than
 * collapsing, so the block has the same shape for every vehicle. Only the last row suppresses its
 * divider, so the block never ends on a dangling rule.
 */
@Composable
private fun DetailFieldBlock(
    vehicle: Vehicle,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        FieldRow(
            label = stringResource(Res.string.field_make),
            value = vehicle.make,
        )
        FieldRow(
            label = stringResource(Res.string.field_model),
            value = vehicle.model,
        )
        FieldRow(
            label = stringResource(Res.string.field_year),
            // Raw stored digits, deliberately unreformatted and unpadded (D-13).
            value = vehicle.year,
        )

        val plate = vehicle.plate?.takeIf { it.isNotBlank() }
        // The chip is a non-blank-only component, so the row shows either the pill or the em dash:
        // a present plate takes the trailing slot (and the value slot is suppressed with it), while
        // an absent one falls back to the same placeholder every other optional field uses.
        val plateSlot: (@Composable () -> Unit)? = if (plate != null) {
            { LicensePlateChip(plate = plate) }
        } else {
            null
        }
        FieldRow(
            label = stringResource(Res.string.field_plate),
            value = null,
            trailing = plateSlot,
        )

        FieldRow(
            label = stringResource(Res.string.field_type),
            // The type's display word comes from the one shared mapping (D-11): no second
            // type-to-copy table exists anywhere.
            value = stringResource(vehicle.type.labelRes()),
            showDivider = false,
        )
    }
}
