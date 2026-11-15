package com.example

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.example.data.AgencyProfile
import com.example.data.AppDatabase
import com.example.data.Customer
import com.example.data.InventoryItem
import com.example.data.QatRepository
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.screens.AgencySetupScreen
import com.example.ui.screens.LoginScreen
import com.example.ui.screens.SplashScreen
import com.example.ui.theme.AppPalettes
import com.example.ui.theme.AppThemeManager
import com.example.ui.theme.MyApplicationTheme
import com.example.util.BluetoothPrinterManager
import com.example.util.BrandingManager
import com.example.util.Helpers
import com.example.util.LauncherIdentityManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var app: Application

    @Before
    fun setupCleanState() {
        app = ApplicationProvider.getApplicationContext()
        AppDatabase.closeDatabase()
        app.deleteDatabase("tofan_al_aqsa_qat_db")
        BrandingManager.restoreDefaults(app)
    }

    @After
    fun tearDown() {
        AppDatabase.closeDatabase()
    }

    @Test
    fun `read app_name string matches Qat Ledger`() {
        val appName = app.getString(R.string.app_name)
        assertEquals("ميزان القات", appName)
    }

    @Test
    fun `legacy agency data is scrubbed cleanly and no legacy constants remain`() {
        val prefs = app.getSharedPreferences("branding_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .putBoolean("legacy_agency_scrubbed_v3", false)
            .putBoolean("agency_profile_configured_v3", true)
            .putString("branding_app_name", "وكالة طوفان الأقصى")
            .putString("branding_agency_name", "وكالة طوفان الأقصى")
            .putString("branding_owner_name", "أحمد منصور")
            .putString("branding_agency_tagline", "لأجود أنواع القات الصعدي")
            .putString("branding_header_text", "وكالة طوفان الأقصى")
            .putString("branding_footer_text", "تكييفة لا تُنسى")
            .apply()

        BrandingManager.initialize(app)

        assertEquals(BrandingManager.PRODUCT_NAME_AR, BrandingManager.appName)
        assertEquals("QAT LEDGER", BrandingManager.PRODUCT_NAME_EN)
        assertEquals("", BrandingManager.agencyName)
        assertEquals("", BrandingManager.ownerName)
        assertEquals("", BrandingManager.agencyTagline)
        assertEquals("", BrandingManager.headerText)
        assertEquals("", BrandingManager.footerText)
        assertFalse(BrandingManager.isConfigured)
    }

    @Test
    fun `agency profile persists across database reopen with required fields only`() = runBlocking {
        val db1 = Room.databaseBuilder(app, AppDatabase::class.java, "persist_test_db")
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_16_17)
            .build()
        val repo1 = QatRepository(db1)

        val profile = AgencyProfile(
            id = 1,
            agencyName = "وكالة النخبة للتجارة",
            ownerName = "سالم الحاج",
            phone = "",
            address = "",
            additionalContact = "",
            tagline = "",
            qatTypes = "",
            defaultCurrency = "ريال يمني",
            commercialDetails = "",
            appDisplayName = "ميزان القات",
            logoPath = "",
            isConfigured = true,
            updatedAt = 123456L
        )
        repo1.saveAgencyProfile(profile)
        db1.close()

        val db2 = Room.databaseBuilder(app, AppDatabase::class.java, "persist_test_db")
            .allowMainThreadQueries()
            .addMigrations(AppDatabase.MIGRATION_16_17)
            .build()
        val loaded = db2.agencyProfileDao().getProfileDirect()
        db2.close()
        app.deleteDatabase("persist_test_db")

        assertNotNull(loaded)
        assertEquals("وكالة النخبة للتجارة", loaded?.agencyName)
        assertEquals("سالم الحاج", loaded?.ownerName)
        assertTrue(loaded?.isConfigured == true)
        assertEquals("", loaded?.phone)
        assertEquals("", loaded?.qatTypes)
    }

    @Test
    fun `MIGRATION_16_17 creates agency_profile table and preserves existing user records`() {
        val migDbName = "migration_16_17_test_db"
        app.deleteDatabase(migDbName)
        val dbFile = app.getDatabasePath(migDbName)
        dbFile.parentFile?.mkdirs()

        // Create a pre-v17 SQLite database containing customer & inventory rows
        val sqlite = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        sqlite.execSQL(
            """
            CREATE TABLE `customers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `totalDebt` REAL NOT NULL,
                `address` TEXT NOT NULL,
                `notes` TEXT NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            """
            CREATE TABLE `inventory_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `name` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `buyPrice` REAL NOT NULL,
                `sellPrice` REAL NOT NULL,
                `lowStockThreshold` REAL NOT NULL,
                `qatType` TEXT NOT NULL,
                `notes` TEXT NOT NULL,
                `dateAdded` TEXT NOT NULL,
                `buyPriceCurrency` TEXT NOT NULL,
                `itemNumber` TEXT NOT NULL,
                `supplierId` INTEGER NOT NULL,
                `supplierName` TEXT NOT NULL,
                `receiverId` INTEGER NOT NULL,
                `receiverName` TEXT NOT NULL
            )
            """.trimIndent()
        )
        sqlite.execSQL(
            "INSERT INTO customers (id, name, phone, totalDebt, address, notes) VALUES (10, 'عميل قديم محفوظ', '771122334', 25000.0, 'صنعاء', 'بيانات قبل التحديث')"
        )
        sqlite.execSQL(
            "INSERT INTO inventory_items (id, name, quantity, buyPrice, sellPrice, lowStockThreshold, qatType, notes, dateAdded, buyPriceCurrency, itemNumber, supplierId, supplierName, receiverId, receiverName) VALUES (5, 'صنف محفوظ', 30.0, 2000.0, 3000.0, 5.0, 'عود', '', '2026-01-01', 'ريال يمني', '1', 1, 'مورد قديم', 0, '')"
        )
        sqlite.version = 16
        sqlite.close()

        // Apply MIGRATION_16_17 using SupportSQLiteOpenHelper / Room migration directly
        val migratedDb = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        val supportDb = androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory()
            .create(
                androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(app)
                    .name(migDbName)
                    .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(17) {
                        override fun onCreate(db: androidx.sqlite.db.SupportSQLiteDatabase) {}
                        override fun onUpgrade(
                            db: androidx.sqlite.db.SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int
                        ) {
                            if (oldVersion == 16 && newVersion == 17) {
                                AppDatabase.MIGRATION_16_17.migrate(db)
                            }
                        }
                    })
                    .build()
            ).writableDatabase

        // Verify customers & inventory rows survived intact
        supportDb.query("SELECT name, totalDebt FROM customers WHERE id = 10").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("عميل قديم محفوظ", c.getString(0))
            assertEquals(25000.0, c.getDouble(1), 0.01)
        }
        supportDb.query("SELECT name, quantity FROM inventory_items WHERE id = 5").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("صنف محفوظ", c.getString(0))
            assertEquals(30.0, c.getDouble(1), 0.01)
        }
        // Verify agency_profile table exists and starts empty/unconfigured
        supportDb.query("SELECT COUNT(*) FROM agency_profile").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        supportDb.close()
        migratedDb.close()
        app.deleteDatabase(migDbName)
    }

    @Test
    fun `viewmodel saveAgencyProfile updates stateflows logo pdf thermal and share outputs`() {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = QatRepository(db)
        val viewModel = AppViewModel(app, repo)

        // Prepare valid PNG bitmap stream for logo test
        val sampleBmp = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        val bos = ByteArrayOutputStream()
        sampleBmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        val logoInputStream = ByteArrayInputStream(bos.toByteArray())

        var callbackProfile: AgencyProfile? = null
        viewModel.saveAgencyProfile(
            agencyName = "وكالة الخير التجارية",
            ownerName = "ياسر المنصوري",
            phone = "777555444",
            address = "صنعاء - شارع تعز",
            additionalContact = "واتساب: 777555444",
            tagline = "ثقة وأمانة في التعامل",
            qatTypes = "عود, فراد, نقوة",
            defaultCurrency = "ريال سعودي",
            commercialDetails = "سجل تجاري 99881",
            logoStream = logoInputStream,
            markConfigured = true,
            navigateNextAfterSetup = true,
            onComplete = { callbackProfile = it }
        )

        // Allow Robolectric looper + Room IO coroutines to complete reliably
        val deadline = System.currentTimeMillis() + 4000
        while (callbackProfile == null && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(app.mainLooper).idleFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }

        assertNotNull(callbackProfile)
        assertEquals("وكالة الخير التجارية", viewModel.brandingAgencyName.value)
        assertEquals("ياسر المنصوري", viewModel.brandingOwnerName.value)
        assertEquals("777555444", viewModel.brandingPhone.value)
        assertEquals("صنعاء - شارع تعز", viewModel.brandingAddress.value)
        assertEquals("عود, فراد, نقوة", viewModel.brandingQatTypes.value)
        assertEquals("ريال سعودي", viewModel.brandingDefaultCurrency.value)
        assertTrue(viewModel.isAgencyConfigured.value)
        assertTrue(viewModel.brandingLogoEnabled.value)
        assertTrue(File(viewModel.brandingLogoPath.value).exists())

        // Verify navigation routed out of AgencySetup once setup completed
        assertTrue(viewModel.currentScreen.value is Screen.Login || viewModel.currentScreen.value is Screen.Dashboard)

        // Verify share header & footer include new merchant identity & zero legacy strings
        val shareHeader = BrandingManager.buildShareHeader("فاتورة مبيعات #101")
        val shareFooter = BrandingManager.buildShareFooter()
        assertTrue(shareHeader.contains("وكالة الخير التجارية"))
        assertTrue(shareHeader.contains("777555444"))
        assertTrue(shareHeader.contains("صنعاء - شارع تعز"))
        assertTrue(shareHeader.contains("سجل تجاري 99881"))
        assertTrue(shareFooter.contains("ياسر المنصوري"))
        assertFalse(shareHeader.contains("طوفان"))
        assertFalse(shareFooter.contains("أحمد منصور"))

        // Verify thermal receipt output
        var thermalReceipt = ""
        BluetoothPrinterManager.getInstance(app).virtualPrint("الصنف: نقوة × 2 = 20000") {
            thermalReceipt = it
        }
        assertTrue(thermalReceipt.contains("وكالة الخير التجارية"))
        assertTrue(thermalReceipt.contains("ياسر المنصوري"))
        assertTrue(thermalReceipt.contains("777555444"))
        assertFalse(thermalReceipt.contains("طوفان"))

        // Verify PDF header & footer metadata reflect new merchant identity with zero legacy defaults
        val pdfMeta = Helpers.buildPdfBrandingMetadata()
        assertEquals("وكالة الخير التجارية", pdfMeta.agencyTitle)
        assertEquals("ثقة وأمانة في التعامل", pdfMeta.tagline)
        assertEquals("صاحب الوكالة: ياسر المنصوري", pdfMeta.ownerLine)
        assertTrue(pdfMeta.contactLine.contains("777555444"))
        assertTrue(pdfMeta.contactLine.contains("صنعاء - شارع تعز"))
        assertEquals("سجل تجاري 99881", pdfMeta.commercialLine)
        assertTrue(pdfMeta.footerText.contains("وكالة الخير التجارية"))
        assertTrue(pdfMeta.footerText.contains("ياسر المنصوري"))
        assertFalse(pdfMeta.agencyTitle.contains("طوفان"))
        assertFalse(pdfMeta.footerText.contains("أحمد منصور"))

        // Clear custom logo & verify cleanup
        var logoClearedDone = false
        viewModel.saveAgencyProfile(
            agencyName = "وكالة الخير التجارية",
            ownerName = "ياسر المنصوري",
            clearLogo = true,
            markConfigured = true,
            onComplete = { logoClearedDone = true }
        )
        val clearDeadline = System.currentTimeMillis() + 4000
        while (!logoClearedDone && System.currentTimeMillis() < clearDeadline) {
            Shadows.shadowOf(app.mainLooper).idleFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertFalse(viewModel.brandingLogoEnabled.value)
        assertNull(BrandingManager.getCustomLogoBitmap(app))

        db.close()
    }

    @Test
    fun `first-run routing enters AgencySetupScreen and completes with required fields only`() {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = QatRepository(db)
        val viewModel = AppViewModel(app, repo)

        // Advance Robolectric Looper past 1200ms splash delay + Room query
        val routeDeadline = System.currentTimeMillis() + 4000
        while (viewModel.currentScreen.value == Screen.Splash && System.currentTimeMillis() < routeDeadline) {
            Shadows.shadowOf(app.mainLooper).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }
        assertEquals(Screen.AgencySetup, viewModel.currentScreen.value)

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                AgencySetupScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()

        // Fill only required fields (agency name & owner name), leave all optional fields empty
        composeTestRule.onNodeWithTag("agency_setup_name_input").performTextInput("وكالة السلام")
        composeTestRule.onNodeWithTag("agency_setup_owner_input").performTextInput("عادل الحميري")

        // Add one dynamic Qat type via inline manager
        composeTestRule.onNodeWithTag("agency_qat_type_add_input").performScrollTo().performTextInput("نقوة ملكي")
        composeTestRule.onNodeWithTag("agency_qat_type_add_btn").performClick()

        // Save setup
        composeTestRule.onNodeWithTag("agency_setup_save_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()
        val saveDeadline = System.currentTimeMillis() + 4000
        while (!BrandingManager.isConfigured && System.currentTimeMillis() < saveDeadline) {
            Shadows.shadowOf(app.mainLooper).idleFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }

        assertEquals("وكالة السلام", BrandingManager.agencyName)
        assertEquals("عادل الحميري", BrandingManager.ownerName)
        assertTrue(BrandingManager.qatTypes.contains("نقوة ملكي"))
        assertTrue(BrandingManager.isConfigured)

        // Verify optional fields remain blank and buildShareHeader omits empty lines cleanly
        val header = BrandingManager.buildShareHeader("سند بيع")
        assertTrue(header.contains("وكالة السلام"))
        assertFalse(header.contains("هاتف:"))
        assertFalse(header.contains("العنوان:"))

        db.close()
    }

    @Test
    fun `verify LauncherIdentityManager generates adaptive and round launcher bitmaps`() {
        val defaultDrawable = BitmapFactory.decodeResource(app.resources, R.drawable.ic_qat_ledger_icon)
        assertNotNull(defaultDrawable)

        val adaptive = LauncherIdentityManager.createAdaptiveLauncherIcon(app, null)
        assertEquals(512, adaptive.width)
        assertEquals(512, adaptive.height)

        val round = LauncherIdentityManager.createRoundLauncherIcon(adaptive)
        assertEquals(512, round.width)
        assertEquals(512, round.height)
    }

    @Test
    fun `launch main activity successfully`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity)
            }
        }
    }

    @Test
    fun `test theme switching light dark mode and pdf colors synchronization`() {
        AppThemeManager.initialize(app)
        AppThemeManager.resetToDefault(app)

        assertEquals(AppPalettes.EmeraldGreen.id, AppThemeManager.currentTheme.id)
        assertEquals("#173B2F", AppThemeManager.currentTheme.pdfColors.primaryDarkHex)
        assertEquals("#267451", AppThemeManager.currentTheme.pdfColors.primaryNormalHex)

        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val viewModel = AppViewModel(app, QatRepository(db))
        val initialDark = viewModel.isDarkMode.value
        viewModel.toggleTheme()
        assertEquals(!initialDark, viewModel.isDarkMode.value)
        viewModel.toggleTheme()
        assertEquals(initialDark, viewModel.isDarkMode.value)

        AppThemeManager.applyTheme(app, AppPalettes.SapphireBlue)
        assertEquals(AppPalettes.SapphireBlue.id, AppThemeManager.currentTheme.id)
        assertEquals("#0D2B52", AppThemeManager.getPdfColors(app).primaryDarkHex)

        AppThemeManager.applyCustomColor(app, "#123456", "#E65100")
        assertEquals("custom", AppThemeManager.currentTheme.id)
        assertEquals("#123456", AppThemeManager.getPdfColors(app).primaryNormalHex)

        AppThemeManager.resetToDefault(app)
        assertEquals(AppPalettes.EmeraldGreen.id, AppThemeManager.currentTheme.id)
        db.close()
    }

    @Test
    fun `splash screen renders Qat Ledger product identity without legacy agency`() {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val viewModel = AppViewModel(app, QatRepository(db))

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                SplashScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ميزان القات", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("QAT LEDGER").assertIsDisplayed()
        db.close()
    }

    @Test
    fun `login screen renders Qat Ledger identity active agency and 3-tap secret dialog`() {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = QatRepository(db)
        val viewModel = AppViewModel(app, repo)

        var loginProfileSaved = false
        viewModel.saveAgencyProfile(
            agencyName = "وكالة التيسير للقات",
            ownerName = "ناصر غالب",
            markConfigured = true,
            onComplete = { loginProfileSaved = true }
        )
        val loginDeadline = System.currentTimeMillis() + 4000
        while (!loginProfileSaved && System.currentTimeMillis() < loginDeadline) {
            Shadows.shadowOf(app.mainLooper).idleFor(50, java.util.concurrent.TimeUnit.MILLISECONDS)
            Thread.sleep(20)
        }

        composeTestRule.setContent {
            MyApplicationTheme(darkTheme = false) {
                LoginScreen(viewModel = viewModel)
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("ميزان القات").assertIsDisplayed()
        composeTestRule.onNodeWithText("وكالة التيسير للقات").assertIsDisplayed()

        val logoTrigger = composeTestRule.onNodeWithTag("login_logo_secret_trigger")
        logoTrigger.assertIsDisplayed()
        logoTrigger.performClick()
        logoTrigger.performClick()
        logoTrigger.performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("إعداد أصناف القات والهوية").assertIsDisplayed()
        db.close()
    }

    @Test
    fun `core accounting math reduces inventory stock and updates customer credit debt`() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(app, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val repo = QatRepository(db)

        val custId = repo.addCustomer(
            Customer(name = "تاجر التجزئة", phone = "770123456", totalDebt = 0.0)
        )
        val itemId = repo.addOrUpdateInventoryItem(
            InventoryItem(
                name = "عود ممتاز",
                quantity = 20.0,
                buyPrice = 3000.0,
                sellPrice = 4500.0,
                lowStockThreshold = 5.0,
                qatType = "ممتاز",
                dateAdded = "2026-06-01 09:00",
                buyPriceCurrency = "ريال يمني"
            )
        )
        val savedItem = repo.getItemById(itemId.toInt())!!

        // Sell 5 units @ 4500 = 22,500 YER on credit (آجل, paid = 0)
        repo.makeSale(
            customerName = "تاجر التجزئة",
            date = "2026-06-01 10:00",
            items = listOf(savedItem to 5.0),
            taxPercent = 0.0,
            discount = 0.0,
            paidAmount = 0.0,
            paymentMethod = "آجل",
            currency = "ريال يمني",
            paidAmountYer = 0.0,
            paymentMethodYer = "آجل"
        )

        val updatedItem = repo.getItemById(itemId.toInt())!!
        val updatedCustomer = repo.getAllCustomersDirect().first { it.id == custId.toInt() }

        assertEquals(15.0, updatedItem.quantity, 0.001)
        assertEquals(22500.0, updatedCustomer.totalDebt, 0.01)
        db.close()
    }
}
