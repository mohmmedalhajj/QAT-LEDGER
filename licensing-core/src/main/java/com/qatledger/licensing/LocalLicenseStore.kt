package com.qatledger.licensing

import android.content.Context
import java.io.File

/**
 * Persists the signed license envelope in app private storage (`noBackupFilesDir` + `SharedPreferences` mirror).
 * Note: Stored state is NEVER blindly trusted on read — callers always re-run [LicenseVerifier.verifyEnvelope]
 * with Ed25519 before granting any entitlement.
 */
class LocalLicenseStore(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val envelopeFile = File(context.noBackupFilesDir, ENVELOPE_FILE)

    fun saveSignedDocument(qlicJson: String) {
        runCatching {
            envelopeFile.parentFile?.mkdirs()
            envelopeFile.writeText(qlicJson)
        }
        prefs.edit().putString(KEY_ACTIVE_QLIC, qlicJson).apply()
    }

    fun loadSignedDocument(): String? {
        val fromFile = runCatching {
            if (envelopeFile.exists()) envelopeFile.readText().trim() else ""
        }.getOrDefault("")
        if (fromFile.isNotEmpty()) return fromFile

        val fromPrefs = prefs.getString(KEY_ACTIVE_QLIC, null)?.trim().orEmpty()
        return fromPrefs.takeIf { it.isNotEmpty() }
    }

    fun clearStoredLicense() {
        runCatching { if (envelopeFile.exists()) envelopeFile.delete() }
        prefs.edit().remove(KEY_ACTIVE_QLIC).apply()
    }

    companion object {
        private const val PREFS_NAME = "qat_active_license_store"
        private const val ENVELOPE_FILE = "active_license.qlic"
        private const val KEY_ACTIVE_QLIC = "active_qlic_doc"
    }
}
