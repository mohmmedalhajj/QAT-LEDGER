package com.aistudio.tofanqat.nxzwmb

import android.content.Context
import java.io.File

object FlavorPlatformDelegate {
    // Production & QA security policy: NEVER package plaintext Ed25519 private seeds inside APK assets.
    // Developer signing keys are generated on-device inside AndroidKeyStore AES-GCM or imported via encrypted passphrase bundles.
    fun getDeveloperBootstrapSeedB64(context: Context): String? = null

    fun extraLegacyPrefNames(): List<String> = listOf("qat_ledger_developer_store")

    fun extraLegacyDbInfo(context: Context): Map<String, Any?> {
        val legacyDevDb: File = context.getDatabasePath("qat_ledger_developer_registry.db")
        return mapOf(
            "legacyDevDbExists" to legacyDevDb.exists(),
            "legacyDevDbPath" to legacyDevDb.absolutePath
        )
    }
}
