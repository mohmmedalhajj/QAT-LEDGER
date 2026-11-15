package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import com.example.data.AppDatabase
import com.example.data.QatRepository
import com.example.ui.AppViewModel
import com.example.ui.AppViewModelFactory
import com.example.ui.Screen
import com.example.ui.components.CustomerLicenseActivationDialog
import com.example.ui.components.LedgerConfirmDialog
import com.example.ui.components.ProUpgradeRequiredDialog
import com.example.ui.components.QatAgencyBottomBar
import com.example.ui.components.QatLedgerAppMark
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.util.BrandingManager
import com.example.util.LauncherIdentityManager
import com.qatledger.licensing.LicenseFeature
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var mainViewModel: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        AppThemeManager.initialize(applicationContext)
        BrandingManager.initialize(applicationContext)
        val database = AppDatabase.getDatabase(applicationContext)
        val repository = QatRepository(database)
        val viewModelFactory = AppViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[AppViewModel::class.java]
        mainViewModel = viewModel

        if (androidx.core.content.ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.READ_CONTACTS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            viewModel.importContactsFromPhone()
        }

        // Apply official Qat Ledger launcher identity & recents task description
        LauncherIdentityManager.applyIdentityToAndroid(
            context = this,
            appName = BrandingManager.appName,
            logoBitmap = BrandingManager.getCustomLogoBitmap(this),
            showNotificationToast = false
        )

        enableEdgeToEdge()

        setContent {
            val isDark by viewModel.isDarkMode.collectAsState()
            MyApplicationTheme(darkTheme = isDark) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    val currentScreen by viewModel.currentScreen.collectAsState()
                    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                    val scope = rememberCoroutineScope()

                    if (currentScreen != Screen.Dashboard &&
                        currentScreen != Screen.Splash &&
                        currentScreen != Screen.Login &&
                        currentScreen != Screen.AgencySetup
                    ) {
                        BackHandler {
                            viewModel.navigateToDashboard()
                        }
                    }

                    val navigateWithDrawer: (Screen) -> Unit = { screen ->
                        scope.launch { drawerState.close() }
                        viewModel.navigateTo(screen)
                    }

                    val showChrome = currentScreen != Screen.Splash &&
                        currentScreen != Screen.Login &&
                        currentScreen != Screen.AgencySetup

                    ModalNavigationDrawer(
                        drawerState = drawerState,
                        gesturesEnabled = showChrome,
                        drawerContent = {
                            if (showChrome) {
                                DrawerContent(
                                    currentScreen = currentScreen,
                                    onNavigate = navigateWithDrawer,
                                    viewModel = viewModel,
                                    onLogout = {
                                        scope.launch {
                                            drawerState.close()
                                            viewModel.logout()
                                        }
                                    }
                                )
                            }
                        }
                    ) {
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            containerColor = PalBlackDark,
                            bottomBar = {
                                if (showChrome) {
                                    QatAgencyBottomBar(
                                        currentScreen = currentScreen,
                                        onNavigate = navigateWithDrawer,
                                        onOpenMenu = { scope.launch { drawerState.open() } }
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .widthIn(max = 900.dp)
                                        .fillMaxWidth()
                                        .background(PalBlackDark)
                                ) {
                                    Crossfade(
                                        targetState = currentScreen,
                                        modifier = Modifier.fillMaxSize(),
                                        label = "screen_navigation"
                                    ) { screen ->
                                        when (screen) {
                                            is Screen.Splash -> SplashScreen(viewModel)
                                            is Screen.AgencySetup -> AgencySetupScreen(viewModel)
                                            is Screen.Login -> LoginScreen(viewModel)
                                            is Screen.Dashboard -> DashboardScreen(
                                                viewModel = viewModel,
                                                onMenuClick = {
                                                    scope.launch { drawerState.open() }
                                                }
                                            )
                                            is Screen.Inventory -> InventoryScreen(viewModel)
                                            is Screen.Sales -> SalesScreen(viewModel)
                                            is Screen.Accounts -> AccountsScreen(viewModel)
                                            is Screen.Customers -> CustomerScreen(viewModel)
                                            is Screen.Suppliers -> SupplierScreen(viewModel)
                                            is Screen.Expenses -> ExpensesScreen(viewModel)
                                            is Screen.Transfers -> TransfersScreen(viewModel)
                                            is Screen.Reports -> ReportsScreen(viewModel)
                                            is Screen.Archive -> ArchiveScreen(viewModel)
                                            is Screen.Settings -> SettingsScreen(viewModel)
                                            is Screen.Statement -> StatementScreen(viewModel)
                                        }
                                    }
                                }
                            }
                        }

                        val proPromptFeature by viewModel.proPromptFeature.collectAsState()
                        val showActivationDialog by viewModel.showCustomerActivationDialog.collectAsState()
                        proPromptFeature?.let { lockedFeature ->
                            ProUpgradeRequiredDialog(
                                feature = lockedFeature,
                                onActivateClick = { viewModel.openCustomerActivationDialog() },
                                onDismiss = { viewModel.dismissProUpgradePrompt() }
                            )
                        }
                        if (showActivationDialog) {
                            CustomerLicenseActivationDialog(
                                viewModel = viewModel,
                                onDismiss = { viewModel.dismissCustomerActivationDialog() }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        mainViewModel?.refreshLicenseStatus()
    }
}

@Composable
fun DrawerContent(
    currentScreen: Screen,
    onNavigate: (Screen) -> Unit,
    viewModel: AppViewModel,
    onLogout: () -> Unit
) {
    val bAppName by viewModel.brandingAppName.collectAsState()
    val bAgencyName by viewModel.brandingAgencyName.collectAsState()
    val bOwnerName by viewModel.brandingOwnerName.collectAsState()
    val accountsEnabled by viewModel.accountsEnabled.collectAsState()
    val transfersEnabled by viewModel.transfersEnabled.collectAsState()
    val expensesEnabled by viewModel.expensesEnabled.collectAsState()
    val reportsEnabled by viewModel.reportsEnabled.collectAsState()
    val archiveEnabled by viewModel.archiveEnabled.collectAsState()

    ModalDrawerSheet(
        drawerContainerColor = PalBlackNormal,
        drawerContentColor = PalWhitePure,
        modifier = Modifier.width(305.dp)
    ) {
        // Software Product + Active Merchant Agency Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PalGreenDark)
                .padding(top = 42.dp, bottom = 18.dp, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QatLedgerAppMark(size = 50.dp, cornerRadius = 12.dp)
                Column {
                    Text(
                        text = bAppName.ifEmpty { BrandingManager.PRODUCT_NAME_AR },
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    )
                    Text(
                        text = BrandingManager.PRODUCT_NAME_EN,
                        color = PalGoldMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp
                    )
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.14f))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = bAgencyName.ifEmpty { "وكالة غير مسماة" },
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (bOwnerName.isNotBlank()) {
                    Text(
                        text = "الوكيل: $bOwnerName",
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 11.sp
                    )
                }
            }
        }

        @Composable
        fun DrawerCategoryHeader(title: String) {
            Text(
                text = title,
                color = PalWhiteMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp)
            )
        }

        @Composable
        fun DrawerItemCustom(
            title: String,
            icon: ImageVector,
            target: Screen,
            testTagId: String
        ) {
            val isSelected = currentScreen::class == target::class
            val bg = if (isSelected) PalSoftGreenTint else Color.Transparent
            val border = if (isSelected) BorderStroke(1.dp, PalIconUnified.copy(alpha = 0.3f)) else null
            val contentTint = if (isSelected) PalIconUnified else PalWhiteSoft
            val iconTint = if (isSelected) PalIconUnified else PalIconUnified.copy(alpha = 0.85f)

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = bg),
                border = border,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 3.dp)
                    .clickable { onNavigate(target) }
                    .testTag(testTagId)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = title,
                        color = contentTint,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(vertical = 10.dp)
        ) {
            DrawerCategoryHeader("الرئيسية والميزان")
            DrawerItemCustom("لوحة الجلسة اليومية", Icons.Default.Dashboard, Screen.Dashboard, "drawer_dashboard")
            DrawerItemCustom("كشف الحسابات المعتمد", Icons.Default.Description, Screen.Statement, "drawer_statement")

            Spacer(modifier = Modifier.height(6.dp))
            DrawerCategoryHeader("المبيعات والعملاء")
            DrawerItemCustom("فواتير ومبيعات القات", Icons.Default.PointOfSale, Screen.Sales, "drawer_sales")
            DrawerItemCustom("العملاء والديون", Icons.Default.People, Screen.Customers, "drawer_customers")
            if (accountsEnabled) {
                DrawerItemCustom("التحاسب وجرد الحسابات", Icons.Default.BarChart, Screen.Accounts, "drawer_accounts")
            }
            if (transfersEnabled) {
                DrawerItemCustom("الحوالات والصرافة", Icons.Default.CompareArrows, Screen.Transfers, "drawer_transfers")
            }

            Spacer(modifier = Modifier.height(6.dp))
            DrawerCategoryHeader("الرعيّة والمخزون والمصروفات")
            DrawerItemCustom("الرعيّة والمورّدين", Icons.Default.LocalShipping, Screen.Suppliers, "drawer_suppliers")
            DrawerItemCustom("مخزون أصناف القات", Icons.Default.Inventory2, Screen.Inventory, "drawer_inventory")
            if (expensesEnabled) {
                DrawerItemCustom("المصروفات ونثريات المفرش", Icons.Default.Payments, Screen.Expenses, "drawer_expenses")
            }

            val licenseState by viewModel.licenseState.collectAsState()
            if (reportsEnabled || archiveEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                DrawerCategoryHeader("التقارير والأرشيف")
                if (reportsEnabled) {
                    val hasReportsPro = licenseState.hasFeature(LicenseFeature.REPORTS)
                    DrawerItemCustom(
                        title = if (hasReportsPro) "التقارير المالية والأرباح" else "التقارير المالية والأرباح (PRO)",
                        icon = if (hasReportsPro) Icons.Default.Assessment else Icons.Default.Lock,
                        target = Screen.Reports,
                        testTagId = "drawer_reports"
                    )
                }
                if (archiveEnabled) {
                    val hasArchivePro = licenseState.hasFeature(LicenseFeature.ARCHIVE)
                    DrawerItemCustom(
                        title = if (hasArchivePro) "الأرشيف الحسابي الشامل" else "الأرشيف الحسابي الشامل (PRO)",
                        icon = if (hasArchivePro) Icons.Default.FolderOpen else Icons.Default.Lock,
                        target = Screen.Archive,
                        testTagId = "drawer_archive"
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            DrawerCategoryHeader("النظام والتهيئة")
            DrawerItemCustom("الإعدادات وتخصيص النظام", Icons.Default.Settings, Screen.Settings, "drawer_settings")

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                color = PalBorderColor,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))

            var showLogoutConfirm by remember { mutableStateOf(false) }

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickable { showLogoutConfirm = true }
                    .testTag("drawer_logout")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = PalRedNormal,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "تسجيل الخروج",
                        color = PalRedNormal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            if (showLogoutConfirm) {
                LedgerConfirmDialog(
                    title = "تأكيد تسجيل الخروج",
                    message = "هل أنت متأكد من رغبتك في تسجيل الخروج من جلسة العمل الحالية؟ تظل كافة السجلات والبيانات محفوظة بأمان.",
                    confirmText = "خروج آمن",
                    isDestructive = true,
                    onConfirm = {
                        showLogoutConfirm = false
                        onLogout()
                    },
                    onDismiss = { showLogoutConfirm = false }
                )
            }
        }
    }
}
