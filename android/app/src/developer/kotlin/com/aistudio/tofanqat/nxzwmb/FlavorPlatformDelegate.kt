package com.aistudio.tofanqat.nxzwmb

import android.content.Context

object FlavorPlatformDelegate {
    fun getDeveloperBootstrapSeedB64(context: Context): String? {
        return try {
            context.assets.open("dev_bootstrap_seed.b64").bufferedReader().use {
                it.readText().trim().ifEmpty { null }
            }
        } catch (_: Exception) {
            null
        }
    }
}
