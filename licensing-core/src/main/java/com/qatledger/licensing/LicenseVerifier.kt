package com.qatledger.licensing

import com.google.crypto.tink.subtle.Ed25519Verify
import java.security.GeneralSecurityException
import kotlin.math.ceil

/**
 * Pure offline cryptographic verifier using Ed25519 (Google Tink).
 * Rejects tampered payloads, forged signatures, unknown keys, mismatched installations,
 * expired subscriptions, and clock rollbacks — cleanly and without mutating user accounting data.
 */
object LicenseVerifier {

    fun verifyEnvelope(
        rawInput: String,
        localInstallationId: String,
        timeGuard: TrustedTimeGuard? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): LicenseVerificationResult {
        val envelope = LicenseCanonicalCodec.parseInput(rawInput)
            ?: return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.INVALID_FORMAT,
                reasonArabic = "صيغة كود أو ملف الترخيص غير صالحة أو تالفة."
            )
        return verifyEnvelope(envelope, localInstallationId, timeGuard, nowEpochMs)
    }

    fun verifyEnvelope(
        envelope: SignedLicenseEnvelope,
        localInstallationId: String,
        timeGuard: TrustedTimeGuard? = null,
        nowEpochMs: Long = System.currentTimeMillis()
    ): LicenseVerificationResult {
        if (!envelope.algorithm.equals(SignedLicenseEnvelope.ALGORITHM_ED25519, ignoreCase = true)) {
            return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.INVALID_FORMAT,
                reasonArabic = "خوارزمية التوقيع غير مدعومة."
            )
        }

        val payload = envelope.payload
        val publicKeyRaw32 = TrustedPublicKeyRegistry.getPublicKeyRaw32(payload.keyId)
            ?: return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.UNTRUSTED_KEY,
                reasonArabic = "مفتاح التوقيع (${payload.keyId}) غير معرّف كـ Public Key موثوق داخل هذه النسخة."
            )

        val sigBytes = LicenseCanonicalCodec.decodeBase64Url(envelope.signatureBase64Url)
        if (sigBytes == null || sigBytes.size != 64) {
            return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.INVALID_SIGNATURE,
                reasonArabic = "التوقيع الرقمي مفقود أو غير مكتمل."
            )
        }

        val canonicalBytes = LicenseCanonicalCodec.canonicalSigningBytes(payload)
        try {
            Ed25519Verify(publicKeyRaw32).verify(sigBytes, canonicalBytes)
        } catch (_: GeneralSecurityException) {
            return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.INVALID_SIGNATURE,
                reasonArabic = "فشل التحقق التشفيري Ed25519 — تم تعديل الترخيص أو التوقيع مزوّر."
            )
        } catch (_: Exception) {
            return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.INVALID_SIGNATURE,
                reasonArabic = "تعذر التحقق التشفيري من التوقيع."
            )
        }

        // Verify optional Installation ID binding
        val requiredInstallId = payload.installationId?.trim()?.uppercase()
        val isBound = !requiredInstallId.isNullOrEmpty()
        if (isBound && requiredInstallId != localInstallationId.trim().uppercase()) {
            return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.WRONG_INSTALLATION,
                payload = payload,
                reasonArabic = "هذا الترخيص مرتبط بمعرّف تثبيت آخر ($requiredInstallId) ولا يطابق جهازك ($localInstallationId).",
                isInstallationBound = true
            )
        }

        // Validate licenseType & expiresAtEpochMs consistency
        if (payload.licenseType == LicenseType.SUBSCRIPTION) {
            val expiresAt = payload.expiresAtEpochMs
            if (expiresAt == null || expiresAt <= payload.issuedAtEpochMs) {
                return LicenseVerificationResult(
                    isValid = false,
                    status = LicenseStatus.INVALID_FORMAT,
                    reasonArabic = "بيانات تاريخ انتهاء الاشتراك غير صالحة."
                )
            }

            // Trusted clock evaluation for subscriptions
            val timeOutcome = timeGuard?.evaluateAndRecord(
                issuedAtEpochMs = payload.issuedAtEpochMs,
                maxSkewSeconds = payload.maxClockSkewSeconds
            )
            if (timeOutcome?.isTampered == true) {
                return LicenseVerificationResult(
                    isValid = false,
                    status = LicenseStatus.CLOCK_TAMPERED,
                    payload = payload,
                    effectivePackage = PackageId.BASIC,
                    reasonArabic = "تم اكتشاف إرجاع ساعة الجهاز للخلف. عاد النظام تلقائياً إلى الباقة الأساسية (BASIC) مع بقاء جميع بياناتك محفوظة.",
                    isInstallationBound = isBound
                )
            }

            val evalTime = timeOutcome?.currentWallClockMs ?: nowEpochMs
            if (evalTime > expiresAt) {
                return LicenseVerificationResult(
                    isValid = false,
                    status = LicenseStatus.EXPIRED,
                    payload = payload,
                    effectivePackage = PackageId.BASIC,
                    reasonArabic = "انتهت مدة صلاحية الاشتراك. تم إيقاف ميزات PRO فقط وبقيت جميع سجلاتك وبياناتك آمنة.",
                    remainingDays = 0L,
                    isInstallationBound = isBound
                )
            }

            val remainingMs = (expiresAt - evalTime).coerceAtLeast(0L)
            val remainingDays = ceil(remainingMs / 86_400_000.0).toLong().coerceAtLeast(1L)
            val status = when (payload.packageId) {
                PackageId.PRO -> LicenseStatus.ACTIVE_PRO_SUBSCRIPTION
                PackageId.BASIC -> LicenseStatus.ACTIVE_BASIC_SUBSCRIPTION
            }
            return LicenseVerificationResult(
                isValid = true,
                status = status,
                payload = payload,
                effectivePackage = payload.packageId,
                reasonArabic = "تم التحقق من الترخيص بنجاح (${payload.packageId.arabicName} — متبقي $remainingDays يوم).",
                remainingDays = remainingDays,
                isInstallationBound = isBound
            )
        } else {
            val status = when (payload.packageId) {
                PackageId.PRO -> LicenseStatus.ACTIVE_PRO_PERPETUAL
                PackageId.BASIC -> LicenseStatus.ACTIVE_BASIC_PERPETUAL
            }
            return LicenseVerificationResult(
                isValid = true,
                status = status,
                payload = payload,
                effectivePackage = payload.packageId,
                reasonArabic = "تم التحقق من الترخيص بنجاح (${payload.packageId.arabicName} — دائم).",
                remainingDays = null,
                isInstallationBound = isBound
            )
        }
    }
}
