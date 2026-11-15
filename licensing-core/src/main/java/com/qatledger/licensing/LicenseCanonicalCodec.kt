package com.qatledger.licensing

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

/**
 * Pure RFC 4648 Base64Url (no padding) + canonical deterministic signing serialization.
 * Identical across Android API 24-36 and Robolectric/JVM unit tests.
 */
object LicenseCanonicalCodec {

    private const val COMPACT_PREFIX = "QL1"
    private const val FILE_FORMAT = "QAT_LEDGER_LICENSE_V1"

    private const val URL_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val DECODE_TABLE = IntArray(128) { -1 }.apply {
        URL_ALPHABET.forEachIndexed { idx, c -> this[c.code] = idx }
        this['+'.code] = 62
        this['/'.code] = 63
    }

    fun encodeBase64Url(bytes: ByteArray): String {
        if (bytes.isEmpty()) return ""
        val out = StringBuilder(((bytes.size + 2) / 3) * 4)
        var i = 0
        while (i < bytes.size) {
            val b0 = bytes[i].toInt() and 0xFF
            val hasB1 = i + 1 < bytes.size
            val hasB2 = i + 2 < bytes.size
            val b1 = if (hasB1) (bytes[i + 1].toInt() and 0xFF) else 0
            val b2 = if (hasB2) (bytes[i + 2].toInt() and 0xFF) else 0

            out.append(URL_ALPHABET[b0 ushr 2])
            out.append(URL_ALPHABET[((b0 and 0x03) shl 4) or (b1 ushr 4)])
            if (hasB1) {
                out.append(URL_ALPHABET[((b1 and 0x0F) shl 2) or (b2 ushr 6)])
            }
            if (hasB2) {
                out.append(URL_ALPHABET[b2 and 0x3F])
            }
            i += 3
        }
        return out.toString()
    }

    fun decodeBase64Url(encoded: String): ByteArray? {
        val clean = encoded.trim().trimEnd('=')
        if (clean.isEmpty()) return ByteArray(0)
        val rem = clean.length % 4
        if (rem == 1) return null

        val outLen = (clean.length * 3) / 4
        val out = ByteArray(outLen)
        var outIdx = 0
        var i = 0
        while (i < clean.length) {
            val c0 = decodeChar(clean[i]) ?: return null
            val c1 = if (i + 1 < clean.length) (decodeChar(clean[i + 1]) ?: return null) else return null
            val c2 = if (i + 2 < clean.length) (decodeChar(clean[i + 2]) ?: return null) else 0
            val c3 = if (i + 3 < clean.length) (decodeChar(clean[i + 3]) ?: return null) else 0

            val triple = (c0 shl 18) or (c1 shl 12) or (c2 shl 6) or c3
            if (outIdx < outLen) out[outIdx++] = ((triple ushr 16) and 0xFF).toByte()
            if (i + 2 < clean.length && outIdx < outLen) out[outIdx++] = ((triple ushr 8) and 0xFF).toByte()
            if (i + 3 < clean.length && outIdx < outLen) out[outIdx++] = (triple and 0xFF).toByte()
            i += 4
        }
        return out
    }

    private fun decodeChar(ch: Char): Int? {
        val code = ch.code
        if (code >= 128) return null
        val v = DECODE_TABLE[code]
        return if (v >= 0) v else null
    }

    /**
     * Produces canonical immutable UTF-8 bytes used for Ed25519 signature creation & verification.
     * Field order is strictly fixed and unambiguous regardless of JSON formatter differences.
     */
    fun canonicalSigningBytes(payload: LicensePayload): ByteArray {
        val sortedFeatureCodes = payload.features
            .map { it.code }
            .distinct()
            .sorted()
            .joinToString(",")

        val normInstallId = payload.installationId?.trim()?.uppercase().orEmpty()
        val normExpires = payload.expiresAtEpochMs?.toString() ?: "NONE"

        val lines = listOf(
            "QAT_LEDGER_CANONICAL_PAYLOAD_V1",
            "licenseId=${escapeField(payload.licenseId.trim())}",
            "version=${payload.version}",
            "packageId=${payload.packageId.code}",
            "licenseType=${payload.licenseType.code}",
            "features=$sortedFeatureCodes",
            "agencyName=${escapeField(payload.agencyName.trim())}",
            "ownerName=${escapeField(payload.ownerName.trim())}",
            "phone=${escapeField(payload.phone.trim())}",
            "installationId=${escapeField(normInstallId)}",
            "issuedAtEpochMs=${payload.issuedAtEpochMs}",
            "expiresAtEpochMs=$normExpires",
            "keyId=${escapeField(payload.keyId.trim())}",
            "maxClockSkewSeconds=${payload.maxClockSkewSeconds}",
            "notes=${escapeField(payload.notes.trim())}"
        )
        return lines.joinToString("\n").toByteArray(StandardCharsets.UTF_8)
    }

    private fun escapeField(raw: String): String {
        return encodeBase64Url(raw.toByteArray(StandardCharsets.UTF_8))
    }

    fun payloadToJson(payload: LicensePayload): JSONObject {
        val sortedFeatures = payload.features.map { it.code }.distinct().sorted()
        return JSONObject().apply {
            put("licenseId", payload.licenseId.trim())
            put("version", payload.version)
            put("packageId", payload.packageId.code)
            put("licenseType", payload.licenseType.code)
            put("features", JSONArray(sortedFeatures))
            put("agencyName", payload.agencyName.trim())
            put("ownerName", payload.ownerName.trim())
            put("phone", payload.phone.trim())
            if (!payload.installationId.isNullOrBlank()) {
                put("installationId", payload.installationId.trim().uppercase())
            } else {
                put("installationId", JSONObject.NULL)
            }
            put("issuedAtEpochMs", payload.issuedAtEpochMs)
            if (payload.expiresAtEpochMs != null) {
                put("expiresAtEpochMs", payload.expiresAtEpochMs)
            } else {
                put("expiresAtEpochMs", JSONObject.NULL)
            }
            put("keyId", payload.keyId.trim())
            put("maxClockSkewSeconds", payload.maxClockSkewSeconds)
            put("notes", payload.notes.trim())
        }
    }

    fun payloadFromJson(obj: JSONObject): LicensePayload? {
        return try {
            val licenseId = obj.optString("licenseId", "").trim()
            val version = obj.optInt("version", -1)
            val packageId = PackageId.fromCode(obj.optString("packageId", "")) ?: return null
            val licenseType = LicenseType.fromCode(obj.optString("licenseType", "")) ?: return null
            val featuresArr = obj.optJSONArray("features") ?: return null
            val featureCodes = buildList {
                for (i in 0 until featuresArr.length()) {
                    add(featuresArr.optString(i, ""))
                }
            }
            val features = LicenseFeature.fromCodes(featureCodes)
            if (licenseId.isEmpty() || version <= 0 || features.isEmpty()) return null

            val agencyName = obj.optString("agencyName", "").trim()
            val ownerName = obj.optString("ownerName", "").trim()
            val phone = obj.optString("phone", "").trim()
            val installationIdRaw = if (obj.isNull("installationId")) "" else obj.optString("installationId", "")
            val installationId = installationIdRaw.trim().uppercase().takeIf { it.isNotEmpty() }
            if (!obj.has("issuedAtEpochMs") || obj.isNull("issuedAtEpochMs")) return null
            val issuedAtEpochMs = obj.getLong("issuedAtEpochMs")
            val expiresAtEpochMs = if (obj.isNull("expiresAtEpochMs") || !obj.has("expiresAtEpochMs")) {
                null
            } else {
                obj.getLong("expiresAtEpochMs")
            }
            val keyId = obj.optString("keyId", "").trim()
            if (keyId.isEmpty()) return null
            val maxSkew = obj.optLong("maxClockSkewSeconds", LicensePayload.DEFAULT_CLOCK_SKEW_SECONDS)
            val notes = obj.optString("notes", "").trim()

            LicensePayload(
                licenseId = licenseId,
                version = version,
                packageId = packageId,
                licenseType = licenseType,
                features = features,
                agencyName = agencyName,
                ownerName = ownerName,
                phone = phone,
                installationId = installationId,
                issuedAtEpochMs = issuedAtEpochMs,
                expiresAtEpochMs = expiresAtEpochMs,
                keyId = keyId,
                maxClockSkewSeconds = maxSkew,
                notes = notes
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Compact offline activation string suitable for SMS/WhatsApp/copy-paste or QR code:
     * `QL1.<base64url(payloadJson)>.<base64url(signatureBytes)>`
     */
    fun encodeCompactToken(envelope: SignedLicenseEnvelope): String {
        val payloadJsonBytes = payloadToJson(envelope.payload).toString().toByteArray(StandardCharsets.UTF_8)
        val payloadB64 = encodeBase64Url(payloadJsonBytes)
        val sigClean = envelope.signatureBase64Url.trim().trimEnd('=')
        return "$COMPACT_PREFIX.$payloadB64.$sigClean"
    }

    /**
     * Full `.qlic` JSON document for offline file sharing/importing.
     */
    fun encodeQlicDocument(envelope: SignedLicenseEnvelope): String {
        return JSONObject().apply {
            put("format", FILE_FORMAT)
            put("algorithm", envelope.algorithm)
            put("payload", payloadToJson(envelope.payload))
            put("signature", envelope.signatureBase64Url.trim().trimEnd('='))
        }.toString(2)
    }

    /**
     * Parses either a compact `QL1.<payload>.<sig>` string (whitespace-tolerant) or `.qlic` JSON document.
     */
    fun parseInput(rawInput: String): SignedLicenseEnvelope? {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return null

        if (trimmed.startsWith("{")) {
            return parseQlicJson(trimmed)
        }

        // Remove accidental newlines/spaces from WhatsApp copy/paste
        val normalized = trimmed.replace(Regex("\\s+"), "")
        val parts = normalized.split(".")
        if (parts.size != 3 || parts[0] != COMPACT_PREFIX) return null

        val payloadBytes = decodeBase64Url(parts[1]) ?: return null
        val sigBytes = decodeBase64Url(parts[2]) ?: return null
        if (sigBytes.size != 64) return null

        return try {
            val jsonObj = JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
            val payload = payloadFromJson(jsonObj) ?: return null
            SignedLicenseEnvelope(
                payload = payload,
                signatureBase64Url = encodeBase64Url(sigBytes),
                algorithm = SignedLicenseEnvelope.ALGORITHM_ED25519
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun parseQlicJson(jsonText: String): SignedLicenseEnvelope? {
        return try {
            val root = JSONObject(jsonText)
            val format = root.optString("format", "")
            val algorithm = root.optString("algorithm", SignedLicenseEnvelope.ALGORITHM_ED25519)
            if (format != FILE_FORMAT || !algorithm.equals(SignedLicenseEnvelope.ALGORITHM_ED25519, ignoreCase = true)) {
                return null
            }
            val payloadObj = root.optJSONObject("payload") ?: return null
            val payload = payloadFromJson(payloadObj) ?: return null
            val sigStr = root.optString("signature", "").trim()
            val sigBytes = decodeBase64Url(sigStr) ?: return null
            if (sigBytes.size != 64) return null
            SignedLicenseEnvelope(
                payload = payload,
                signatureBase64Url = encodeBase64Url(sigBytes),
                algorithm = SignedLicenseEnvelope.ALGORITHM_ED25519
            )
        } catch (_: Exception) {
            null
        }
    }
}
