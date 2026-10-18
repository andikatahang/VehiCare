package com.vehicare.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * The declared type scale: 4 sizes (14/16/20/24sp) and 2 weights (400/500), nothing else.
 *
 * Only the five Material 3 slots UI-SPEC uses are overridden — the remaining slots keep their
 * defaults on purpose, because M3's defaults introduce 12sp and 22sp, i.e. sizes outside the scale.
 * `FontFamily.Default` keeps Roboto on Android and SF Pro on iOS: no font is bundled or downloaded.
 */
val VehiCareTypography = Typography(
    // Display — vehicle name on the Detail screen only.
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
        lineHeight = 28.sp,
    ),
    // Heading — top app bar title, delete-dialog title, empty-state heading.
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 24.sp,
    ),
    // Title — vehicle name on list cards, form section label.
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    // Body — text-field input, Detail values, meta line, dialog/empty-state body.
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    // Label — buttons, tab labels, plate chip, detail field labels.
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
)
