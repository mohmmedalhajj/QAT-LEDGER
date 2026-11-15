package com.example.licensing

import android.content.Context
import androidx.compose.runtime.Composable
import com.qatledger.developer.crypto.DeveloperKeyManager
import com.qatledger.developer.ui.DeveloperConsoleDialog

/**
 * Developer Build edition bridge: connects the isolated `:developer-licensing` module.
 */
object EditionCapabilities {
    const val isDeveloperEdition: Boolean = true

    fun initOnLaunch(context: Context) {
        DeveloperKeyManager(context.applicationContext).registerAllKnownPublicKeysInSession()
    }

    @Composable
    fun DeveloperConsoleHost(visible: Boolean, onDismiss: () -> Unit) {
        if (visible) {
            DeveloperConsoleDialog(onDismiss = onDismiss)
        }
    }
}
