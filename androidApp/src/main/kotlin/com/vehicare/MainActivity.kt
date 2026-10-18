package com.vehicare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import com.vehicare.di.platformModule

/**
 * The Android host: nothing but the launcher activity that mounts the shared UI.
 *
 * It holds no Compose UI of its own — `setContent { App(...) }` is the whole contract (D-17).
 * Its only job beyond the earlier plans is supplying the platform module, built once per
 * composition from the real application `Context`, so the shared Koin graph can reach the OS.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(remember { platformModule(applicationContext) })
        }
    }
}
