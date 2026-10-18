package com.vehicare.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

val Paper = Color(0xFFFFFFFF) // dominant surface
val Mist = Color(0xFFF4F4F5) // secondary surface
val Ink = Color(0xFF1C1C1E) // accent fill + all primary text/icons
val Slate = Color(0xFF5C5C61) // secondary text + inactive icons
val Hairline = Color(0xFFE4E4E7) // outlines / dividers
val Danger = Color(0xFFB3261E) // destructive only

/**
 * The one light-only palette (UI-SPEC C-8): no dark variant, no dynamic color.
 *
 * `primary` carries the Ink token because D-21 leaves the brand accent undecided; swapping this
 * single value later re-tints every reserved accent surface with no layout change.
 */
val VehiCareLightColors = lightColorScheme(
    primary = Ink, onPrimary = Paper,
    secondary = Ink, onSecondary = Paper,
    background = Paper, onBackground = Ink,
    surface = Paper, onSurface = Ink,
    surfaceVariant = Mist, onSurfaceVariant = Slate,
    outline = Hairline, error = Danger, onError = Paper,
)
