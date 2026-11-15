package com.qatledger.developer.crypto

import android.graphics.Bitmap
import com.qatledger.licensing.LicenseCanonicalCodec
import com.qatledger.licensing.LicensePayload
import com.qatledger.licensing.QrCodeCodec
import com.qatledger.licensing.SignedLicenseEnvelope

/**
 * Signs deterministic [LicensePayload] bytes using the developer's private key inside [DeveloperKeyManager].
 * Produces compact activation codes (`QL1....`), `.qlic` JSON documents, and QR bitmaps.
 */
class LicenseSigner(private val keyManager: DeveloperKeyManager) {

    data class SignedLicenseArtifacts(
        val envelope: SignedLicenseEnvelope,
        val compactToken: String,
        val qlicJson: String,
        val qrBitmap: Bitmap?
    )

    fun signPayload(payload: LicensePayload, generateQr: Boolean = true): SignedLicenseArtifacts {
        val canonicalBytes = LicenseCanonicalCodec.canonicalSigningBytes(payload)
        val rawSig = keyManager.signBytes(payload.keyId, canonicalBytes)
        val sigB64 = LicenseCanonicalCodec.encodeBase64Url(rawSig)
        val envelope = SignedLicenseEnvelope(
            payload = payload,
            signatureBase64Url = sigB64,
            algorithm = SignedLicenseEnvelope.ALGORITHM_ED25519
        )
        val compactToken = LicenseCanonicalCodec.encodeCompactToken(envelope)
        val qlicJson = LicenseCanonicalCodec.encodeQlicDocument(envelope)
        val qr = if (generateQr) QrCodeCodec.generateQrBitmap(compactToken, 560) else null
        return SignedLicenseArtifacts(
            envelope = envelope,
            compactToken = compactToken,
            qlicJson = qlicJson,
            qrBitmap = qr
        )
    }
}
