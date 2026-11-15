package com.qatledger.developer.crypto

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.crypto.tink.subtle.Ed25519Sign
import com.qatledger.licensing.LicenseCanonicalCodec
import com.qatledger.licensing.TrustedPublicKeyRegistry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Developer-only Ed25519 Keystore Manager.
 *
 * Security Properties:
 * - ZERO hardcoded private keys anywhere in source, resources, build config, or logs.
 * - API 33+ (Android 13+): Generates non-exportable hardware-backed `Ed25519` keypairs inside
 *   `AndroidKeyStore` (preferring StrongBox, with TEE fallback).
 * - API 24-32 (or OEMs lacking native Keystore Ed25519): Generates an `AndroidKeyStore` AES-256-GCM
 *   master key (StrongBox preferred on API 28+, TEE fallback) and seals the 32-byte Ed25519 private
 *   key inside `noBackupFilesDir`, unwrapping only inside `signBytes` and immediately zeroing memory.
 * - Supports key rotation with `keyId` tracking and exports ONLY the public key + `QAT_LICENSE_PUBLIC_KEYS` snippet.
 */
class DeveloperKeyManager(private val context: Context) {

    enum class BackingMode(val arabicLabel: String) {
        KEYSTORE_ED25519_STRONGBOX("Android Keystore Ed25519 (StrongBox)"),
        KEYSTORE_ED25519_TEE("Android Keystore Ed25519 (TEE)"),
        KEYSTORE_AES_WRAPPED_STRONGBOX("مغلف بـ Android Keystore AES-GCM (StrongBox)"),
        KEYSTORE_AES_WRAPPED_TEE("مغلف بـ Android Keystore AES-GCM (TEE)"),
        JVM_TEST_EPHEMERAL("ذاكرة اختبار مؤقتة (بيئة اختبارات JVM فقط)")
    }

    data class DeveloperKeyRecord(
        val keyId: String,
        val alias: String,
        val publicKeyRaw32Base64Url: String,
        val backingMode: BackingMode,
        val createdAtEpochMs: Long,
        val isActive: Boolean
    ) {
        fun buildPublicConfigLine(): String {
            return "$keyId:$publicKeyRaw32Base64Url"
        }
    }

    private val vaultDir = File(context.noBackupFilesDir, "qat_dev_keystore_vault").apply { mkdirs() }
    private val indexFile = File(vaultDir, "keys_index.json")
    private val inMemoryTestSeeds = mutableMapOf<String, ByteArray>()

    @Synchronized
    fun getActiveKey(): DeveloperKeyRecord {
        val existing = listKeys().firstOrNull { it.isActive }
        if (existing != null && verifyKeyUsable(existing)) {
            registerPublicKeyInSession(existing)
            return existing
        }
        return generateAndActivateNewKey()
    }

    @Synchronized
    fun listKeys(): List<DeveloperKeyRecord> {
        if (!indexFile.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(indexFile.readText())
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val modeName = obj.optString("backingMode", BackingMode.KEYSTORE_AES_WRAPPED_TEE.name)
                    val mode = BackingMode.entries.firstOrNull { it.name == modeName }
                        ?: BackingMode.KEYSTORE_AES_WRAPPED_TEE
                    add(
                        DeveloperKeyRecord(
                            keyId = obj.getString("keyId"),
                            alias = obj.getString("alias"),
                            publicKeyRaw32Base64Url = obj.getString("publicKeyRaw32"),
                            backingMode = mode,
                            createdAtEpochMs = obj.getLong("createdAt"),
                            isActive = obj.optBoolean("isActive", false)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun generateAndActivateNewKey(): DeveloperKeyRecord {
        val now = System.currentTimeMillis()
        val alias = "qat_dev_ed25519_$now"

        val generated = tryNativeAndroidKeystoreEd25519(alias, now)
            ?: tryAesKeystoreWrappedEd25519(alias, now)
            ?: createEphemeralTestKeyIfInUnitTests(alias, now)
            ?: throw IllegalStateException("تعذر إنشاء مفتاح توقيع آمن داخل Android Keystore")

        val updatedList = listKeys().map { it.copy(isActive = false) } + generated
        persistIndex(updatedList)
        registerPublicKeyInSession(generated)
        return generated
    }

    @Synchronized
    fun signBytes(keyId: String, canonicalPayloadBytes: ByteArray): ByteArray {
        val record = listKeys().firstOrNull { it.keyId == keyId }
            ?: throw IllegalStateException("مفتاح التوقيع ($keyId) غير موجود في المخزن الآمن")

        return when (record.backingMode) {
            BackingMode.KEYSTORE_ED25519_STRONGBOX,
            BackingMode.KEYSTORE_ED25519_TEE -> {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                val entry = ks.getEntry(record.alias, null) as? KeyStore.PrivateKeyEntry
                    ?: throw IllegalStateException("تعذر استرجاع المفتاح الخاص من Android Keystore")
                Signature.getInstance("Ed25519").run {
                    initSign(entry.privateKey)
                    update(canonicalPayloadBytes)
                    sign()
                }
            }

            BackingMode.KEYSTORE_AES_WRAPPED_STRONGBOX,
            BackingMode.KEYSTORE_AES_WRAPPED_TEE -> {
                val sealedFile = File(vaultDir, "${record.alias}.sealed")
                if (!sealedFile.exists()) {
                    throw IllegalStateException("ملف المفتاح المغلف غير موجود: ${record.keyId}")
                }
                val rawSeed = unwrapSeedWithAesMasterKey(record.alias, sealedFile.readBytes())
                try {
                    Ed25519Sign(rawSeed).sign(canonicalPayloadBytes)
                } finally {
                    rawSeed.fill(0)
                }
            }

            BackingMode.JVM_TEST_EPHEMERAL -> {
                val seed = inMemoryTestSeeds[record.keyId]
                    ?: throw IllegalStateException("مفتاح الاختبار المؤقت غير موجود في الجلسة")
                Ed25519Sign(seed).sign(canonicalPayloadBytes)
            }
        }
    }

    fun registerAllKnownPublicKeysInSession() {
        listKeys().forEach { registerPublicKeyInSession(it) }
    }

    private fun registerPublicKeyInSession(record: DeveloperKeyRecord) {
        val raw = LicenseCanonicalCodec.decodeBase64Url(record.publicKeyRaw32Base64Url) ?: return
        TrustedPublicKeyRegistry.registerPublicKeyForSession(record.keyId, raw)
    }

    private fun verifyKeyUsable(record: DeveloperKeyRecord): Boolean {
        return when (record.backingMode) {
            BackingMode.KEYSTORE_ED25519_STRONGBOX,
            BackingMode.KEYSTORE_ED25519_TEE -> runCatching {
                val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                ks.containsAlias(record.alias)
            }.getOrDefault(false)

            BackingMode.KEYSTORE_AES_WRAPPED_STRONGBOX,
            BackingMode.KEYSTORE_AES_WRAPPED_TEE -> {
                File(vaultDir, "${record.alias}.sealed").exists()
            }

            BackingMode.JVM_TEST_EPHEMERAL -> inMemoryTestSeeds.containsKey(record.keyId)
        }
    }

    private fun tryNativeAndroidKeystoreEd25519(alias: String, createdAt: Long): DeveloperKeyRecord? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        // StrongBox first, then TEE
        for (strongBox in listOf(true, false)) {
            val result = runCatching {
                val specBuilder = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
                )
                if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    specBuilder.setIsStrongBoxBacked(true)
                }
                val kpg = KeyPairGenerator.getInstance("Ed25519", ANDROID_KEYSTORE)
                kpg.initialize(specBuilder.build())
                val kp = kpg.generateKeyPair()
                val raw32 = TrustedPublicKeyRegistry.extractRaw32PublicKey(kp.public.encoded)
                    ?: error("Unexpected SPKI length")
                val keyId = TrustedPublicKeyRegistry.deriveKeyId(raw32)
                DeveloperKeyRecord(
                    keyId = keyId,
                    alias = alias,
                    publicKeyRaw32Base64Url = LicenseCanonicalCodec.encodeBase64Url(raw32),
                    backingMode = if (strongBox) BackingMode.KEYSTORE_ED25519_STRONGBOX else BackingMode.KEYSTORE_ED25519_TEE,
                    createdAtEpochMs = createdAt,
                    isActive = true
                )
            }.getOrNull()
            if (result != null) return result
        }
        return null
    }

    private fun tryAesKeystoreWrappedEd25519(alias: String, createdAt: Long): DeveloperKeyRecord? {
        val kp = Ed25519Sign.KeyPair.newKeyPair()
        val seed = kp.privateKey.copyOf()
        return try {
            val masterMode = ensureAesMasterKey(alias) ?: return null
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getAesMasterKey(alias))
            val iv = cipher.iv
            val ciphertext = cipher.doFinal(seed)

            val payload = ByteArray(1 + iv.size + ciphertext.size)
            payload[0] = iv.size.toByte()
            System.arraycopy(iv, 0, payload, 1, iv.size)
            System.arraycopy(ciphertext, 0, payload, 1 + iv.size, ciphertext.size)
            File(vaultDir, "$alias.sealed").writeBytes(payload)

            val keyId = TrustedPublicKeyRegistry.deriveKeyId(kp.publicKey)
            DeveloperKeyRecord(
                keyId = keyId,
                alias = alias,
                publicKeyRaw32Base64Url = LicenseCanonicalCodec.encodeBase64Url(kp.publicKey),
                backingMode = masterMode,
                createdAtEpochMs = createdAt,
                isActive = true
            )
        } catch (_: Exception) {
            null
        } finally {
            seed.fill(0)
        }
    }

    private fun unwrapSeedWithAesMasterKey(alias: String, sealedBytes: ByteArray): ByteArray {
        val ivLen = sealedBytes[0].toInt() and 0xFF
        val iv = sealedBytes.copyOfRange(1, 1 + ivLen)
        val ciphertext = sealedBytes.copyOfRange(1 + ivLen, sealedBytes.size)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getAesMasterKey(alias), GCMParameterSpec(128, iv))
        return cipher.doFinal(ciphertext)
    }

    private fun ensureAesMasterKey(alias: String): BackingMode? {
        val ks = runCatching { KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) } }.getOrNull()
            ?: return null
        val aesAlias = "${alias}_aes"
        if (ks.containsAlias(aesAlias)) return BackingMode.KEYSTORE_AES_WRAPPED_TEE

        val tryStrongBox = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) listOf(true, false) else listOf(false)
        for (strongBox in tryStrongBox) {
            val ok = runCatching {
                val spec = KeyGenParameterSpec.Builder(
                    aesAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .apply {
                        if (strongBox && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            setIsStrongBoxBacked(true)
                        }
                    }
                    .build()
                val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                kg.init(spec)
                kg.generateKey()
            }.isSuccess
            if (ok) {
                return if (strongBox) BackingMode.KEYSTORE_AES_WRAPPED_STRONGBOX else BackingMode.KEYSTORE_AES_WRAPPED_TEE
            }
        }
        return null
    }

    private fun getAesMasterKey(alias: String): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        return ks.getKey("${alias}_aes", null) as SecretKey
    }

    private fun createEphemeralTestKeyIfInUnitTests(alias: String, createdAt: Long): DeveloperKeyRecord? {
        if (!isRunningUnderUnitTest()) return null
        val kp = Ed25519Sign.KeyPair.newKeyPair()
        val keyId = TrustedPublicKeyRegistry.deriveKeyId(kp.publicKey)
        inMemoryTestSeeds[keyId] = kp.privateKey.copyOf()
        return DeveloperKeyRecord(
            keyId = keyId,
            alias = alias,
            publicKeyRaw32Base64Url = LicenseCanonicalCodec.encodeBase64Url(kp.publicKey),
            backingMode = BackingMode.JVM_TEST_EPHEMERAL,
            createdAtEpochMs = createdAt,
            isActive = true
        )
    }

    private fun isRunningUnderUnitTest(): Boolean {
        return Build.FINGERPRINT.equals("robolectric", ignoreCase = true) ||
            Build.HARDWARE.equals("robolectric", ignoreCase = true)
    }

    private fun persistIndex(records: List<DeveloperKeyRecord>) {
        val arr = JSONArray()
        records.forEach { r ->
            arr.put(JSONObject().apply {
                put("keyId", r.keyId)
                put("alias", r.alias)
                put("publicKeyRaw32", r.publicKeyRaw32Base64Url)
                put("backingMode", r.backingMode.name)
                put("createdAt", r.createdAtEpochMs)
                put("isActive", r.isActive)
            })
        }
        indexFile.writeText(arr.toString(2))
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
