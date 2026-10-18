package com.vehicare.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

/**
 * Applies the VehiCare palette and type scale unconditionally.
 *
 * There is deliberately no dark-mode branch and no dynamic color on Android — either would replace
 * the ink-on-white language the user chose (D-21, UI-SPEC C-8).
 */
@Composable
fun VehiCareTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VehiCareLightColors,
        typography = VehiCareTypography,
        content = content,
    )
}
