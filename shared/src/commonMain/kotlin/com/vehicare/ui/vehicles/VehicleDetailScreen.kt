package com.vehicare.ui.vehicles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.title_vehicle_fallback

/**
 * The Detail destination exists in the graph from the shell plan so the route set is complete
 * (UI-SPEC screens inventory); its real content — the vehicle header and actions — is authored
 * by plan 01-09. Until then this renders the neutral fallback title, never a blank screen.
 */
@Composable
fun VehicleDetailRoute(vehicleId: Long) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = stringResource(Res.string.title_vehicle_fallback))
    }
}
