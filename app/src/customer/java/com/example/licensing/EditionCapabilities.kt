package com.example.licensing

import android.content.Context
import androidx.compose.runtime.Composable

/**
 * Customer Build edition bridge: strictly contains ZERO developer-licensing classes or keys.
 */
object EditionCapabilities {
    const val isDeveloperEdition: Boolean = false

    fun initOnLaunch(context: Context) {
        // Customer Build relies strictly on compiled-in public verification keys in :licensing-core.
    }

    @Composable
    fun DeveloperConsoleHost(visible: Boolean, onDismiss: () -> Unit) {
        // Strictly absent in Customer Build.
    }
}
