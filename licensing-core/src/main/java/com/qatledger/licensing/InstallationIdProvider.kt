package com.qatledger.licensing

import android.content.Context
import java.io.File
import java.security.SecureRandom

/**
 * Generates and persists a stable per-installation app identifier (not tied to hardware IMEI/MAC)
 * in `noBackupFilesDir`, plus a mirrored backup preference so it remains consistent across launches.
 */
object InstallationIdProvider {

    private const val PREFS_NAME = "qat_license_install_prefs"
    private const val KEY_INSTALL_ID = "installation_id"
    private const val FILE_NAME = "qat_installation_id.txt"
    private const val ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ" // unambiguous Crockford-like

    fun getOrCreateInstallationId(context: Context): String {
        val file = File(context.noBackupFilesDir, FILE_NAME)
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val fromFile = runCatching {
            if (file.exists()) file.readText().trim().uppercase() else ""
        }.getOrDefault("")
        if (isValidFormat(fromFile)) {
            if (prefs.getString(KEY_INSTALL_ID, null) != fromFile) {
                prefs.edit().putString(KEY_INSTALL_ID, fromFile).apply()
            }
            return fromFile
        }

        val fromPrefs = prefs.getString(KEY_INSTALL_ID, null)?.trim()?.uppercase().orEmpty()
        if (isValidFormat(fromPrefs)) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(fromPrefs)
            }
            return fromPrefs
        }

        val generated = generateNewId()
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(generated)
        }
        prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        return generated
    }

    fun isValidFormat(candidate: String): Boolean {
        return Regex("^QAT-[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$").matches(candidate.trim().uppercase())
    }

    private fun generateNewId(): String {
        val rng = SecureRandom()
        fun block(): String = buildString(4) {
            repeat(4) { append(ALPHABET[rng.nextInt(ALPHABET.length)]) }
        }
        return "QAT-${block()}-${block()}-${block()}"
    }
}
