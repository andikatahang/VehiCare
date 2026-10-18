package com.vehicare

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.vehicare.ui.theme.VehiCareTheme
import org.jetbrains.compose.resources.stringResource
import vehicare.shared.generated.resources.Res
import vehicare.shared.generated.resources.app_name

/**
 * The one composable both platform hosts render.
 *
 * This body is deliberately the thinnest proof that the shared layer works: no navigation and no
 * dependency injection yet. Plan 01-04 replaces the body with the Koin + NavHost shell; the name and
 * the zero-parameter signature are fixed so the hosts never need editing again (D-17).
 */
@Composable
fun App() {
    VehiCareTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = stringResource(Res.string.app_name))
            }
        }
    }
}
