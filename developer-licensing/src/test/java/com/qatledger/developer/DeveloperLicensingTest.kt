package com.qatledger.developer

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.qatledger.developer.data.DeveloperLicenseDatabase
import com.qatledger.developer.data.DeveloperLicenseRepository
import com.qatledger.licensing.LicenseFeature
import com.qatledger.licensing.LicenseManager
import com.qatledger.licensing.LicenseStatus
import com.qatledger.licensing.LicenseType
import com.qatledger.licensing.PackageId
import com.qatledger.licensing.TrustedPublicKeyRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeveloperLicensingTest {

    private lateinit var app: Application
    private lateinit var devRepo: DeveloperLicenseRepository
    private lateinit var customerLicenseManager: LicenseManager

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        DeveloperLicenseDatabase.closeForTests()
        app.deleteDatabase(DeveloperLicenseDatabase.DB_NAME)
        TrustedPublicKeyRegistry.clearSessionKeys()
        devRepo = DeveloperLicenseRepository(app)
        customerLicenseManager = LicenseManager.getInstance(app)
        customerLicenseManager.clearActiveLicense()
    }

    @After
    fun tearDown() {
        customerLicenseManager.clearActiveLicense()
        TrustedPublicKeyRegistry.clearSessionKeys()
        DeveloperLicenseDatabase.closeForTests()
    }

    @Test
    fun `secret pricing starts empty with zero invented defaults and persists developer entries`() = runBlocking {
        val initial = devRepo.observePrices().first()
        assertTrue(initial.isEmpty())

        devRepo.saveSecretPackagePricing(
            packageId = PackageId.PRO,
            currency = "ريال سعودي",
            perpetualPrice = 450.0,
            monthlyPrice = 60.0,
            quarterlyPrice = null,
            yearlyPrice = 320.0
        )

        val saved = devRepo.observePrices().first()
        assertEquals(1, saved.size)
        assertEquals(450.0, saved.first().perpetualPrice!!, 0.01)
        assertNull(saved.first().quarterlyPrice)
    }

    @Test
    fun `developer issues Ed25519 signed PRO license and customer activates offline`() = runBlocking {
        val custId = devRepo.upsertCustomer(
            agencyName = "وكالة الفتح",
            ownerName = "زيد",
            phone = "771234567",
            city = "صنعاء",
            notes = ""
        )
        val localInstallId = customerLicenseManager.getInstallationId()
        val issued = devRepo.issueNewLicense(
            customerId = custId,
            agencyName = "وكالة الفتح",
            ownerName = "زيد",
            phone = "771234567",
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            durationDays = null,
            installationId = localInstallId,
            priceCharged = 500.0,
            priceCurrency = "ريال سعودي",
            paidAmount = 500.0,
            requestedPaidConfirmed = false, // Must NOT mark PAID without explicit confirmation!
            notes = "تجربة إصدار"
        )

        // Confirm payment confirmation rule
        assertEquals("PARTIAL", issued.paymentStatus)
        // Confirm price does NOT leak into signed `.qlic` or compact token
        assertFalse(issued.qlicJson.contains("priceCharged"))
        assertFalse(issued.qlicJson.contains("500.0"))

        val activationResult = customerLicenseManager.activateOffline(issued.compactToken)
        assertTrue(activationResult.isValid)
        assertEquals(LicenseStatus.ACTIVE_PRO_PERPETUAL, activationResult.status)
        assertTrue(customerLicenseManager.hasFeatureEntitlement(LicenseFeature.REPORTS))
        assertTrue(customerLicenseManager.hasFeatureEntitlement(LicenseFeature.ARCHIVE))
    }

    @Test
    fun `renew upgrade installation change revocation and backup restore preserve audit trail`() = runBlocking {
        val custId = devRepo.upsertCustomer(
            agencyName = "وكالة السعيد",
            ownerName = "عبدالله",
            phone = "770001122",
            city = "إب",
            notes = ""
        )
        val initial = devRepo.issueNewLicense(
            customerId = custId,
            agencyName = "وكالة السعيد",
            ownerName = "عبدالله",
            phone = "770001122",
            packageId = PackageId.BASIC,
            licenseType = LicenseType.SUBSCRIPTION,
            durationDays = 30,
            installationId = "QAT-1111-2222-3333",
            priceCharged = 100.0,
            priceCurrency = "ريال سعودي",
            paidAmount = 100.0,
            requestedPaidConfirmed = true,
            notes = "بداية"
        )
        assertEquals("PAID", initial.paymentStatus)

        val renewed = devRepo.renewSubscription(initial.licenseId, additionalDays = 30)!!
        val changed = devRepo.changeInstallationId(
            previousLicenseId = renewed.licenseId,
            newInstallationId = "QAT-4444-5555-6666",
            reason = "استبدال هاتف العميل"
        )!!
        val upgraded = devRepo.upgradeToPerpetual(changed.licenseId, PackageId.PRO)!!
        val revoked = devRepo.revokeLicense(upgraded.licenseId, reason = "إلغاء تجريبي")!!
        assertEquals("REVOKED", revoked.status)

        val audits = devRepo.observeAllAudits().first()
        assertTrue(audits.any { it.actionType == "INSTALLATION_CHANGED" && it.reason == "استبدال هاتف العميل" })
        assertTrue(audits.any { it.actionType == "REVOKED" && it.reason == "إلغاء تجريبي" })

        val backupJson = devRepo.exportRegistryBackupJson()
        assertFalse(backupJson.contains("privateKey"))

        // Wipe DB and restore backup cleanly
        DeveloperLicenseDatabase.closeForTests()
        app.deleteDatabase(DeveloperLicenseDatabase.DB_NAME)
        val freshRepo = DeveloperLicenseRepository(app)
        assertTrue(freshRepo.importRegistryBackupJson(backupJson))
        val restoredLicenses = freshRepo.observeIssuedLicenses().first()
        assertTrue(restoredLicenses.any { it.licenseId == upgraded.licenseId && it.status == "REVOKED" })
        assertNotNull(restoredLicenses.firstOrNull { it.licenseId == initial.licenseId })
    }
}
