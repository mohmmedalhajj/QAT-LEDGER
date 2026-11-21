package com.aistudio.tofanqat.nxzwmb

import android.content.Context
import java.io.File

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

    fun extraLegacyPrefNames(): List<String> = listOf("qat_ledger_developer_store")

    fun extraLegacyDbInfo(context: Context): Map<String, Any?> {
        val legacyDevDb: File = context.getDatabasePath("qat_ledger_developer_registry.db")
        return mapOf(
            "legacyDevDbExists" to legacyDevDb.exists(),
            "legacyDevDbPath" to legacyDevDb.absolutePath
        )
    }
}
