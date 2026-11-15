package com.qatledger.licensing

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.google.crypto.tink.subtle.Ed25519Sign
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LicensingCoreTest {

    private lateinit var app: Application
    private lateinit var keyPair: Ed25519Sign.KeyPair
    private lateinit var keyId: String
    private lateinit var manager: LicenseManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        TrustedPublicKeyRegistry.clearSessionKeys()
        keyPair = Ed25519Sign.KeyPair.newKeyPair()
        keyId = TrustedPublicKeyRegistry.deriveKeyId(keyPair.publicKey)
        assertTrue(TrustedPublicKeyRegistry.registerPublicKeyForSession(keyId, keyPair.publicKey))
        manager = LicenseManager.getInstance(app)
        manager.clearActiveLicense()
    }

    @After
    fun tearDown() {
        manager.clearActiveLicense()
        TrustedPublicKeyRegistry.clearSessionKeys()
        TrustedTimeGuard(app).resetGuardForTestsOnly()
    }

    private fun signPayload(payload: LicensePayload, signerKeyPair: Ed25519Sign.KeyPair = keyPair): SignedLicenseEnvelope {
        val canonical = LicenseCanonicalCodec.canonicalSigningBytes(payload)
        val sig = Ed25519Sign(signerKeyPair.privateKey).sign(canonical)
        return SignedLicenseEnvelope(
            payload = payload,
            signatureBase64Url = LicenseCanonicalCodec.encodeBase64Url(sig)
        )
    }

    @Test
    fun `canonical signing bytes are deterministic regardless of feature insertion order`() {
        val now = 1_780_000_000_000L
        val p1 = LicensePayload(
            licenseId = "LIC-001",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            features = linkedSetOf(LicenseFeature.ARCHIVE, LicenseFeature.REPORTS, LicenseFeature.DASHBOARD),
            agencyName = "  وكالة النخبة ",
            ownerName = "أحمد  ",
            installationId = "qat-aaaa-bbbb-cccc",
            issuedAtEpochMs = now,
            keyId = keyId
        )
        val p2 = LicensePayload(
            licenseId = "LIC-001",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            features = linkedSetOf(LicenseFeature.DASHBOARD, LicenseFeature.ARCHIVE, LicenseFeature.REPORTS),
            agencyName = "وكالة النخبة",
            ownerName = "أحمد",
            installationId = "QAT-AAAA-BBBB-CCCC",
            issuedAtEpochMs = now,
            keyId = keyId
        )
        assertArrayEquals(
            LicenseCanonicalCodec.canonicalSigningBytes(p1),
            LicenseCanonicalCodec.canonicalSigningBytes(p2)
        )
    }

    @Test
    fun `valid PRO perpetual license activates offline via compact code and unlocks Reports and Archive`() {
        val now = 1_780_000_000_000L
        val installId = manager.getInstallationId()
        val payload = LicensePayload(
            licenseId = "LIC-PRO-001",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            features = LicenseFeature.proFeatures,
            agencyName = "وكالة الشامخ للقات",
            ownerName = "صالح علي",
            phone = "777000111",
            installationId = installId,
            issuedAtEpochMs = now,
            expiresAtEpochMs = null,
            keyId = keyId
        )
        val token = LicenseCanonicalCodec.encodeCompactToken(signPayload(payload))
        val res = manager.activateOffline(token, nowEpochMs = now + 1000)

        assertTrue(res.isValid)
        assertEquals(LicenseStatus.ACTIVE_PRO_PERPETUAL, res.status)
        assertEquals(PackageId.PRO, manager.state.value.effectivePackage)
        assertTrue(manager.hasFeatureEntitlement(LicenseFeature.REPORTS))
        assertTrue(manager.hasFeatureEntitlement(LicenseFeature.ARCHIVE))
        assertTrue(manager.hasFeatureEntitlement(LicenseFeature.SALES))
    }

    @Test
    fun `tampered payload or forged signature is rejected with INVALID_SIGNATURE`() {
        val now = 1_780_000_000_000L
        val payload = LicensePayload(
            licenseId = "LIC-TAMPER-1",
            packageId = PackageId.BASIC,
            licenseType = LicenseType.PERPETUAL,
            features = LicenseFeature.basicFeatures,
            agencyName = "وكالة",
            ownerName = "مالك",
            issuedAtEpochMs = now,
            keyId = keyId
        )
        val env = signPayload(payload)
        // Tamper BASIC -> PRO after signing
        val tamperedEnv = env.copy(
            payload = payload.copy(
                packageId = PackageId.PRO,
                features = LicenseFeature.proFeatures
            )
        )
        val tamperedToken = LicenseCanonicalCodec.encodeCompactToken(tamperedEnv)
        val tamperedRes = manager.activateOffline(tamperedToken, nowEpochMs = now)
        assertFalse(tamperedRes.isValid)
        assertEquals(LicenseStatus.INVALID_SIGNATURE, tamperedRes.status)

        // Forged signature signed by another unknown private key using trusted keyId
        val attackerPair = Ed25519Sign.KeyPair.newKeyPair()
        val forgedEnv = signPayload(payload.copy(packageId = PackageId.PRO), signerKeyPair = attackerPair)
        val forgedRes = manager.activateOffline(LicenseCanonicalCodec.encodeQlicDocument(forgedEnv), nowEpochMs = now)
        assertFalse(forgedRes.isValid)
        assertEquals(LicenseStatus.INVALID_SIGNATURE, forgedRes.status)
    }

    @Test
    fun `untrusted keyId is rejected even with valid signature from unknown keypair`() {
        val unknownPair = Ed25519Sign.KeyPair.newKeyPair()
        val unknownKeyId = TrustedPublicKeyRegistry.deriveKeyId(unknownPair.publicKey)
        val now = 1_780_000_000_000L
        val payload = LicensePayload(
            licenseId = "LIC-UNKNOWN-KEY",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            features = LicenseFeature.proFeatures,
            agencyName = "وكالة",
            ownerName = "مالك",
            issuedAtEpochMs = now,
            keyId = unknownKeyId
        )
        val env = signPayload(payload, signerKeyPair = unknownPair)
        val res = manager.activateOffline(LicenseCanonicalCodec.encodeCompactToken(env), nowEpochMs = now)
        assertFalse(res.isValid)
        assertEquals(LicenseStatus.UNTRUSTED_KEY, res.status)
    }

    @Test
    fun `installationId mismatch rejects activation while unbound license works on any installation`() {
        val now = 1_780_000_000_000L
        val boundWrong = LicensePayload(
            licenseId = "LIC-BOUND-WRONG",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            features = LicenseFeature.proFeatures,
            agencyName = "وكالة",
            ownerName = "مالك",
            installationId = "QAT-9999-8888-7777",
            issuedAtEpochMs = now,
            keyId = keyId
        )
        val wrongRes = manager.activateOffline(
            LicenseCanonicalCodec.encodeQlicDocument(signPayload(boundWrong)),
            nowEpochMs = now
        )
        assertFalse(wrongRes.isValid)
        assertEquals(LicenseStatus.WRONG_INSTALLATION, wrongRes.status)

        val unbound = boundWrong.copy(licenseId = "LIC-UNBOUND", installationId = null)
        val okRes = manager.activateOffline(
            LicenseCanonicalCodec.encodeCompactToken(signPayload(unbound)),
            nowEpochMs = now
        )
        assertTrue(okRes.isValid)
        assertTrue(manager.hasFeatureEntitlement(LicenseFeature.REPORTS))
    }

    @Test
    fun `expired subscription reverts to BASIC and clock rollback disables PRO entitlements`() {
        val now = 1_780_000_000_000L
        val sevenDaysLater = now + 7L * 86_400_000L
        val payload = LicensePayload(
            licenseId = "LIC-SUB-7D",
            packageId = PackageId.PRO,
            licenseType = LicenseType.SUBSCRIPTION,
            features = LicenseFeature.proFeatures,
            agencyName = "وكالة",
            ownerName = "مالك",
            issuedAtEpochMs = now,
            expiresAtEpochMs = sevenDaysLater,
            keyId = keyId
        )
        val qlic = LicenseCanonicalCodec.encodeQlicDocument(signPayload(payload))

        // Mock time guard at `now`
        var fakeWall = now + 3_600_000L
        val fakeElapsed = 100_000L
        val guard = TrustedTimeGuard(app, { fakeWall }, { fakeElapsed })
        guard.resetGuardForTestsOnly()

        val activeRes = LicenseVerifier.verifyEnvelope(qlic, manager.getInstallationId(), guard, fakeWall)
        assertTrue(activeRes.isValid)
        assertEquals(LicenseStatus.ACTIVE_PRO_SUBSCRIPTION, activeRes.status)
        assertEquals(PackageId.PRO, activeRes.effectivePackage)

        // Roll clock backwards 3 days -> CLOCK_TAMPERED, effective BASIC
        fakeWall -= 3L * 86_400_000L
        val tamperedClockRes = LicenseVerifier.verifyEnvelope(qlic, manager.getInstallationId(), guard, fakeWall)
        assertFalse(tamperedClockRes.isValid)
        assertEquals(LicenseStatus.CLOCK_TAMPERED, tamperedClockRes.status)
        assertEquals(PackageId.BASIC, tamperedClockRes.effectivePackage)

        // Advance clock past expiry -> EXPIRED, effective BASIC
        fakeWall = sevenDaysLater + 10_000L
        val expiredRes = LicenseVerifier.verifyEnvelope(qlic, manager.getInstallationId(), guard, fakeWall)
        assertFalse(expiredRes.isValid)
        assertEquals(LicenseStatus.EXPIRED, expiredRes.status)
        assertEquals(PackageId.BASIC, expiredRes.effectivePackage)
    }

    @Test
    fun `QR code round trip preserves compact license token accurately`() {
        val now = 1_780_000_000_000L
        val payload = LicensePayload(
            licenseId = "LIC-QR-1",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            features = LicenseFeature.proFeatures,
            agencyName = "وكالة البيضاء",
            ownerName = "ناصر",
            issuedAtEpochMs = now,
            keyId = keyId
        )
        val token = LicenseCanonicalCodec.encodeCompactToken(signPayload(payload))
        val qrBitmap = QrCodeCodec.generateQrBitmap(token, 480)
        assertNotNull(qrBitmap)
        val decoded = QrCodeCodec.decodeFromBitmap(qrBitmap!!)
        assertEquals(token, decoded)
    }
}
