package com.example

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.Customer
import com.example.data.QatRepository
import com.example.licensing.EditionCapabilities
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.screens.ArchiveScreen
import com.example.ui.screens.ReportsScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.util.BrandingManager
import com.google.crypto.tink.subtle.Ed25519Sign
import com.qatledger.licensing.LicenseCanonicalCodec
import com.qatledger.licensing.LicenseFeature
import com.qatledger.licensing.LicenseManager
import com.qatledger.licensing.LicensePayload
import com.qatledger.licensing.LicenseStatus
import com.qatledger.licensing.LicenseType
import com.qatledger.licensing.PackageId
import com.qatledger.licensing.SignedLicenseEnvelope
import com.qatledger.licensing.TrustedPublicKeyRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class LicenseIntegrationRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application
    private lateinit var repository: QatRepository
    private lateinit var viewModel: AppViewModel
    private lateinit var keyPair: Ed25519Sign.KeyPair
    private lateinit var keyId: String

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        AppDatabase.closeDatabase()
        app.deleteDatabase("tofan_al_aqsa_qat_db")
        BrandingManager.restoreDefaults(app)
        TrustedPublicKeyRegistry.clearSessionKeys()

        keyPair = Ed25519Sign.KeyPair.newKeyPair()
        keyId = TrustedPublicKeyRegistry.deriveKeyId(keyPair.publicKey)
        TrustedPublicKeyRegistry.registerPublicKeyForSession(keyId, keyPair.publicKey)

        LicenseManager.getInstance(app).clearActiveLicense()
        val db = AppDatabase.getDatabase(app)
        repository = QatRepository(db)
        viewModel = AppViewModel(app, repository)
    }

    @After
    fun tearDown() {
        LicenseManager.getInstance(app).clearActiveLicense()
        TrustedPublicKeyRegistry.clearSessionKeys()
        AppDatabase.closeDatabase()
    }

    private fun issueSignedToken(
        packageId: PackageId,
        licenseType: LicenseType = LicenseType.PERPETUAL,
        expiresAtEpochMs: Long? = null,
        installationId: String? = null
    ): String {
        val now = System.currentTimeMillis()
        val payload = LicensePayload(
            licenseId = "TEST-${packageId.code}-1",
            packageId = packageId,
            licenseType = licenseType,
            features = LicenseFeature.defaultFeaturesFor(packageId),
            agencyName = "وكالة الاختبار",
            ownerName = "تاجر",
            installationId = installationId,
            issuedAtEpochMs = now - 60_000L,
            expiresAtEpochMs = expiresAtEpochMs,
            keyId = keyId
        )
        val sig = Ed25519Sign(keyPair.privateKey).sign(LicenseCanonicalCodec.canonicalSigningBytes(payload))
        return LicenseCanonicalCodec.encodeCompactToken(
            SignedLicenseEnvelope(
                payload = payload,
                signatureBase64Url = LicenseCanonicalCodec.encodeBase64Url(sig)
            )
        )
    }

    @Test
    fun `edition flags match active build type cleanly`() {
        assertEquals(BuildConfig.IS_DEVELOPER_BUILD, EditionCapabilities.isDeveloperEdition)
    }

    @Test
    fun `BASIC license unlocks core accounting screens and gates Reports and Archive`() {
        assertEquals(PackageId.BASIC, viewModel.licenseState.value.effectivePackage)
        assertTrue(viewModel.isScreenEnabled(Screen.Dashboard))
        assertTrue(viewModel.isScreenEnabled(Screen.Sales))
        assertTrue(viewModel.isScreenEnabled(Screen.Customers))
        assertTrue(viewModel.isScreenEnabled(Screen.Suppliers))
        assertTrue(viewModel.isScreenEnabled(Screen.Inventory))
        assertTrue(viewModel.isScreenEnabled(Screen.Expenses))
        assertTrue(viewModel.isScreenEnabled(Screen.Accounts))
        assertTrue(viewModel.isScreenEnabled(Screen.Transfers))
        assertTrue(viewModel.isScreenEnabled(Screen.Statement))
        assertTrue(viewModel.isScreenEnabled(Screen.Settings))

        assertFalse(viewModel.isScreenEnabled(Screen.Reports))
        assertFalse(viewModel.isScreenEnabled(Screen.Archive))

        viewModel.navigateTo(Screen.Reports)
        assertEquals(LicenseFeature.REPORTS, viewModel.proPromptFeature.value)

        viewModel.navigateTo(Screen.Archive)
        assertEquals(LicenseFeature.ARCHIVE, viewModel.proPromptFeature.value)
    }

    @Test
    fun `direct ReportsScreen and ArchiveScreen composables enforce PRO lock when BASIC`() {
        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ReportsScreen(viewModel)
            }
        }
        composeTestRule.onNodeWithText("تفعيل الباقة الاحترافية الآن").assertIsDisplayed()
    }

    @Test
    fun `valid PRO activation unlocks Reports and Archive while preserving customer database records`() = runBlocking {
        // Insert customer record BEFORE license changes
        repository.addCustomer(Customer(name = "عميل مخلص", phone = "777111222", totalDebt = 15000.0))

        // Invalid token must be rejected without touching DB
        val badRes = viewModel.activateLicenseOffline("QL1.bad.sig")
        assertFalse(badRes.isValid)
        assertEquals(PackageId.BASIC, viewModel.licenseState.value.effectivePackage)

        // Valid PRO activation succeeds
        val token = issueSignedToken(
            packageId = PackageId.PRO,
            licenseType = LicenseType.PERPETUAL,
            installationId = viewModel.licenseState.value.localInstallationId
        )
        val okRes = viewModel.activateLicenseOffline(token)
        assertTrue(okRes.isValid)
        assertEquals(LicenseStatus.ACTIVE_PRO_PERPETUAL, okRes.status)
        assertTrue(viewModel.isScreenEnabled(Screen.Reports))
        assertTrue(viewModel.isScreenEnabled(Screen.Archive))

        // Customer records untouched and preserved
        val customers = repository.allCustomers.first()
        val saved = customers.firstOrNull { it.name == "عميل مخلص" }
        assertNotNull(saved)
        assertEquals(15000.0, saved!!.totalDebt, 0.01)
    }

    @Test
    fun `ArchiveScreen renders content once PRO activated and remains locked if subscription expired`() {
        val expiredToken = issueSignedToken(
            packageId = PackageId.PRO,
            licenseType = LicenseType.SUBSCRIPTION,
            expiresAtEpochMs = System.currentTimeMillis() - 10_000L
        )
        val expiredRes = viewModel.activateLicenseOffline(expiredToken)
        assertFalse(expiredRes.isValid)
        assertEquals(LicenseStatus.EXPIRED, expiredRes.status)

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = true) {
                ArchiveScreen(viewModel)
            }
        }
        composeTestRule.onNodeWithText("تفعيل الباقة الاحترافية الآن").assertIsDisplayed()
    }
}
