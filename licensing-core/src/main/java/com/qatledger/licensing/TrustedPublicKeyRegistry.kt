package com.qatledger.licensing

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of trusted Ed25519 PUBLIC keys for offline license signature verification.
 *
 * SECURITY POLICY:
 * - Contains PUBLIC keys only — NEVER private keys.
 * - Customer Build validates ONLY against keys embedded via BuildConfig / compile-time registry,
 *   never trusting arbitrary public keys carried inside a license file.
 * - Developer Build registers the active local Keystore public key in memory so the developer
 *   can test newly issued licenses and inspect the exact `QAT_LICENSE_PUBLIC_KEYS` entry to
 *   embed before building Customer APKs.
 */
object TrustedPublicKeyRegistry {

    // Fixed ASN.1 X.509 SubjectPublicKeyInfo header for Ed25519 (OID 1.3.101.112)
    private val ED25519_SPKI_PREFIX = byteArrayOf(
        0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65,
        0x70, 0x03, 0x21, 0x00
    )

    private val compileTimeKeys: Map<String, ByteArray> by lazy {
        parseConfigEntries(BuildConfig.LICENSE_PUBLIC_KEYS)
    }

    private val runtimeSessionKeys = ConcurrentHashMap<String, ByteArray>()

    fun getPublicKeyRaw32(keyId: String): ByteArray? {
        val cleanId = keyId.trim()
        if (cleanId.isEmpty()) return null
        return compileTimeKeys[cleanId]?.copyOf() ?: runtimeSessionKeys[cleanId]?.copyOf()
    }

    fun getAllRegisteredKeyIds(): Set<String> {
        return compileTimeKeys.keys + runtimeSessionKeys.keys
    }

    fun hasAnyTrustedKey(): Boolean {
        return compileTimeKeys.isNotEmpty() || runtimeSessionKeys.isNotEmpty()
    }

    /**
     * Used ONLY by Developer Build (when testing local Keystore public key) and unit tests.
     * Validates that the key is strictly a 32-byte raw Ed25519 public key or 44-byte X.509 SPKI public key.
     */
    fun registerPublicKeyForSession(keyId: String, publicKeyBytes: ByteArray): Boolean {
        val raw32 = extractRaw32PublicKey(publicKeyBytes) ?: return false
        val derivedId = deriveKeyId(raw32)
        if (keyId.trim() != derivedId) return false
        runtimeSessionKeys[derivedId] = raw32
        return true
    }

    fun clearSessionKeys() {
        runtimeSessionKeys.clear()
    }

    fun extractRaw32PublicKey(encoded: ByteArray): ByteArray? {
        return when {
            encoded.size == 32 -> encoded.copyOf()
            encoded.size == 44 && encoded.copyOfRange(0, ED25519_SPKI_PREFIX.size).contentEquals(ED25519_SPKI_PREFIX) -> {
                encoded.copyOfRange(ED25519_SPKI_PREFIX.size, 44)
            }
            else -> null
        }
    }

    fun encodeAsX509Spki(raw32: ByteArray): ByteArray {
        require(raw32.size == 32) { "Ed25519 raw public key must be 32 bytes" }
        return ED25519_SPKI_PREFIX + raw32
    }

    fun deriveKeyId(publicKeyBytes: ByteArray): String {
        val raw32 = extractRaw32PublicKey(publicKeyBytes)
            ?: throw IllegalArgumentException("Invalid Ed25519 public key length: ${publicKeyBytes.size}")
        val digest = MessageDigest.getInstance("SHA-256").digest(raw32)
        val hex = buildString(8) {
            for (i in 0 until 4) {
                append(((digest[i].toInt() ushr 4) and 0xF).toString(16))
                append((digest[i].toInt() and 0xF).toString(16))
            }
        }.uppercase()
        return "QLK-$hex"
    }

    fun formatBuildConfigEntry(keyId: String, raw32: ByteArray): String {
        require(raw32.size == 32) { "Ed25519 raw public key must be 32 bytes" }
        return "$keyId:${LicenseCanonicalCodec.encodeBase64Url(raw32)}"
    }

    fun parseConfigEntries(rawConfig: String): Map<String, ByteArray> {
        if (rawConfig.isBlank()) return emptyMap()
        val result = linkedMapOf<String, ByteArray>()
        rawConfig.split(",").forEach { entry ->
            val trimmed = entry.trim()
            val sep = trimmed.indexOf(':')
            if (sep > 0 && sep < trimmed.lastIndex) {
                val keyId = trimmed.substring(0, sep).trim()
                val keyB64 = trimmed.substring(sep + 1).trim()
                val bytes = LicenseCanonicalCodec.decodeBase64Url(keyB64)
                val raw32 = bytes?.let { extractRaw32PublicKey(it) }
                if (raw32 != null && deriveKeyId(raw32) == keyId) {
                    result[keyId] = raw32
                }
            }
        }
        return result
    }
}
