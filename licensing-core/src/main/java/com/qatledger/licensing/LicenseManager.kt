package com.qatledger.licensing

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single source of truth for runtime entitlement checks in QAT LEDGER.
 * Every verification re-checks Ed25519 signature + installation binding + expiry + time guard.
 */
class LicenseManager private constructor(private val appContext: Context) {

    private val store = LocalLicenseStore(appContext)
    private val timeGuard = TrustedTimeGuard(appContext)
    private val _state = MutableStateFlow(
        VerifiedLicenseState(
            localInstallationId = InstallationIdProvider.getOrCreateInstallationId(appContext)
        )
    )
    val state: StateFlow<VerifiedLicenseState> = _state.asStateFlow()

    init {
        refreshStatus()
    }

    fun getInstallationId(): String {
        return InstallationIdProvider.getOrCreateInstallationId(appContext)
    }

    /**
     * Re-reads persisted signed envelope and cryptographically re-verifies it.
     */
    fun refreshStatus(nowEpochMs: Long = System.currentTimeMillis()): VerifiedLicenseState {
        val installId = getInstallationId()
        val storedDoc = store.loadSignedDocument()
        if (storedDoc.isNullOrBlank()) {
            val basicState = VerifiedLicenseState(
                status = LicenseStatus.NO_LICENSE_BASIC,
                effectivePackage = PackageId.BASIC,
                activePayload = null,
                statusMessageArabic = LicenseStatus.NO_LICENSE_BASIC.arabicLabel,
                remainingDays = null,
                localInstallationId = installId,
                lastVerifiedAtEpochMs = nowEpochMs
            )
            _state.value = basicState
            return basicState
        }

        val result = LicenseVerifier.verifyEnvelope(
            rawInput = storedDoc,
            localInstallationId = installId,
            timeGuard = timeGuard,
            nowEpochMs = nowEpochMs
        )
        val evaluated = VerifiedLicenseState(
            status = result.status,
            effectivePackage = result.effectivePackage,
            activePayload = result.payload,
            statusMessageArabic = result.reasonArabic,
            remainingDays = result.remainingDays,
            localInstallationId = installId,
            lastVerifiedAtEpochMs = nowEpochMs
        )
        _state.value = evaluated
        return evaluated
    }

    /**
     * Verifies a candidate compact code (`QL1....`) or `.qlic` JSON document strictly BEFORE saving.
     * Invalid/tampered/forged/wrong-installation/expired inputs NEVER overwrite a currently valid license.
     */
    fun activateOffline(rawInput: String, nowEpochMs: Long = System.currentTimeMillis()): LicenseVerificationResult {
        val installId = getInstallationId()
        val parsed = LicenseCanonicalCodec.parseInput(rawInput)
            ?: return LicenseVerificationResult(
                isValid = false,
                status = LicenseStatus.INVALID_FORMAT,
                reasonArabic = "صيغة كود التفعيل أو ملف .qlic غير صحيحة."
            )

        val result = LicenseVerifier.verifyEnvelope(
            envelope = parsed,
            localInstallationId = installId,
            timeGuard = timeGuard,
            nowEpochMs = nowEpochMs
        )
        if (!result.isValid) {
            return result
        }

        store.saveSignedDocument(LicenseCanonicalCodec.encodeQlicDocument(parsed))
        refreshStatus(nowEpochMs)
        return result
    }

    fun clearActiveLicense() {
        store.clearStoredLicense()
        refreshStatus()
    }

    fun hasFeatureEntitlement(feature: LicenseFeature): Boolean {
        return _state.value.hasFeature(feature)
    }

    companion object {
        @Volatile
        private var INSTANCE: LicenseManager? = null

        fun getInstance(context: Context): LicenseManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LicenseManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
