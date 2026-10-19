package com.vehicare.ui.vehicles.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vehicare.domain.VehicleType
import com.vehicare.ui.theme.Mist
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.resources.vectorResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.cd_vehicle_type_icon
import vehicare.shared.generated.resources.ic_vehicle_car
import vehicare.shared.generated.resources.ic_vehicle_motorcycle
import vehicare.shared.generated.resources.ic_vehicle_truck
import vehicare.shared.generated.resources.type_car
import vehicare.shared.generated.resources.type_motorcycle
import vehicare.shared.generated.resources.type_truck

/**
 * The type word shown to the user (D-10's fixed list) — the label the type picker renders and the
 * name the type circle announces.
 *
 * This mapping lives in the UI layer on purpose: the domain enum must never gain a dependency on
 * Compose Resources, so `com.vehicare.domain.VehicleType` stays plain Kotlin.
 */
fun VehicleType.labelRes(): StringResource = when (this) {
    VehicleType.Car -> Res.string.type_car
    VehicleType.Motorcycle -> Res.string.type_motorcycle
    VehicleType.Truck -> Res.string.type_truck
}

/**
 * The bundled vector drawable for this type (UI-SPEC Icon Contract: the three vehicle glyphs ship
 * as Compose Resources because the core Material icon set has no car/motorcycle/truck icon).
 */
fun VehicleType.iconRes(): DrawableResource = when (this) {
    VehicleType.Car -> Res.drawable.ic_vehicle_car
    VehicleType.Motorcycle -> Res.drawable.ic_vehicle_motorcycle
    VehicleType.Truck -> Res.drawable.ic_vehicle_truck
}

/**
 * The 64dp `Mist` circle with the type glyph, centered in a card's leading slot whenever a vehicle
 * has no photo (D-14's uniform leading visual). Also the reusable identity mark for the Type
 * picker and the Detail screen.
 *
 * The glyph is not tinted: the drawables ship in `Ink` (UI-SPEC Icon Contract), so ink on a light
 * circle stays the single visual language of the app. The circle itself carries the accessible
 * type name, because a 24dp glyph alone would announce nothing.
 */
@Composable
fun VehicleTypeCircle(
    type: VehicleType,
    size: Dp = 64.dp,
    modifier: Modifier = Modifier,
) {
    val typeName = stringResource(type.labelRes())
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Mist),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = vectorResource(type.iconRes()),
            contentDescription = stringResource(Res.string.cd_vehicle_type_icon, typeName),
            modifier = Modifier.size(24.dp),
        )
    }
}
