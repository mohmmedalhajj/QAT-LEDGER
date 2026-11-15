package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.licensing.EditionCapabilities
import com.example.util.Helpers
import com.qatledger.licensing.LicenseFeature
import com.qatledger.licensing.LicenseManager
import com.qatledger.licensing.LicenseVerificationResult
import com.qatledger.licensing.VerifiedLicenseState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface Screen {
    object Splash : Screen
    object AgencySetup : Screen
    object Login : Screen
    object Dashboard : Screen
    object Inventory : Screen
    object Sales : Screen
    object Accounts : Screen
    object Customers : Screen
    object Suppliers : Screen
    object Expenses : Screen
    object Transfers : Screen
    object Reports : Screen
    object Archive : Screen
    object Settings : Screen
    object Statement : Screen
}

class AppViewModel(
    application: Application,
    private val repository: QatRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val appPrefs = context.getSharedPreferences("app_settings", Context.MODE_PRIVATE)

    // --- Dynamic Branding & Agency Profile Flows ---
    val agencyProfile: StateFlow<AgencyProfile?> = repository.agencyProfile
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val brandingAppName = MutableStateFlow(com.example.util.BrandingManager.DEFAULT_APP_NAME)
    val brandingAgencyName = MutableStateFlow("")
    val brandingOwnerName = MutableStateFlow("")
    val brandingPhone = MutableStateFlow("")
    val brandingAddress = MutableStateFlow("")
    val brandingAdditionalContact = MutableStateFlow("")
    val brandingTagline = MutableStateFlow("")
    val brandingQatTypes = MutableStateFlow("")
    val brandingDefaultCurrency = MutableStateFlow("ريال يمني")
    val brandingCurrencyMode = MutableStateFlow(com.example.util.CurrencyMode.BOTH.storageKey)
    val brandingCommercialDetails = MutableStateFlow("")
    val brandingLogoPath = MutableStateFlow("")
    val brandingLogoEnabled = MutableStateFlow(false)
    val brandingHeaderEnabled = MutableStateFlow(true)
    val brandingFooterEnabled = MutableStateFlow(true)
    val brandingHeaderText = MutableStateFlow("")
    val brandingFooterText = MutableStateFlow("")
    val isAgencyConfigured = MutableStateFlow(false)
    val sarToYerExchangeRate = MutableStateFlow(150.0)

    val currencyConfig: StateFlow<com.example.util.CurrencyConfig> = combine(
        brandingCurrencyMode,
        brandingDefaultCurrency,
        sarToYerExchangeRate
    ) { modeKey, defCurr, rate ->
        com.example.util.CurrencyConfig.create(
            mode = com.example.util.CurrencyMode.fromStorageKey(modeKey),
            defaultCurrency = defCurr,
            exchangeRateSarToYer = rate
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        com.example.util.CurrencyConfig.create(
            mode = com.example.util.CurrencyMode.fromStorageKey(com.example.util.BrandingManager.currencyMode),
            defaultCurrency = com.example.util.BrandingManager.defaultCurrency,
            exchangeRateSarToYer = 150.0
        )
    )

    // --- Local Agency Section Visibility Permissions (independent from license package tiers) ---
    val accountsEnabled = MutableStateFlow(true)
    val transfersEnabled = MutableStateFlow(true)
    val customersEnabled = MutableStateFlow(true)
    val expensesEnabled = MutableStateFlow(true)
    val reportsEnabled = MutableStateFlow(true)
    val archiveEnabled = MutableStateFlow(true)

    // --- Commercial Offline License System State ---
    private val licenseManager: LicenseManager = LicenseManager.getInstance(application)
    val licenseState: StateFlow<VerifiedLicenseState> = licenseManager.state
    val proPromptFeature = MutableStateFlow<LicenseFeature?>(null)
    val showCustomerActivationDialog = MutableStateFlow(false)

    init {
        EditionCapabilities.initOnLaunch(context)
        licenseManager.refreshStatus()
        com.example.util.BrandingManager.initialize(context)
        com.example.ui.theme.AppThemeManager.initialize(context)
        refreshBranding()
        sarToYerExchangeRate.value = appPrefs.getFloat("sar_to_yer_rate", 150.0f).toDouble()

        accountsEnabled.value = appPrefs.getBoolean("section_accounts_enabled", true)
        transfersEnabled.value = appPrefs.getBoolean("section_transfers_enabled", true)
        customersEnabled.value = appPrefs.getBoolean("section_customers_enabled", true)
        expensesEnabled.value = appPrefs.getBoolean("section_expenses_enabled", true)
        reportsEnabled.value = appPrefs.getBoolean("section_reports_enabled", true)
        archiveEnabled.value = appPrefs.getBoolean("section_archive_enabled", true)

        viewModelScope.launch {
            repository.agencyProfile.collect { profile ->
                if (profile != null) {
                    com.example.util.BrandingManager.applyProfile(context, profile)
                    refreshBranding()
                }
            }
        }
    }

    fun hasLicenseEntitlement(feature: LicenseFeature): Boolean {
        return licenseState.value.hasFeature(feature)
    }

    fun refreshLicenseStatus(): VerifiedLicenseState {
        return licenseManager.refreshStatus()
    }

    fun activateLicenseOffline(rawInput: String): LicenseVerificationResult {
        return licenseManager.activateOffline(rawInput)
    }

    fun openCustomerActivationDialog() {
        showCustomerActivationDialog.value = true
    }

    fun dismissCustomerActivationDialog() {
        showCustomerActivationDialog.value = false
    }

    fun dismissProUpgradePrompt() {
        proPromptFeature.value = null
    }

    fun isSectionAdminEnabled(screen: Screen): Boolean {
        return when (screen) {
            is Screen.Accounts -> accountsEnabled.value
            is Screen.Transfers -> transfersEnabled.value
            is Screen.Customers -> customersEnabled.value
            is Screen.Expenses -> expensesEnabled.value
            is Screen.Reports -> reportsEnabled.value
            is Screen.Archive -> archiveEnabled.value
            else -> true
        }
    }

    fun isScreenEnabled(screen: Screen): Boolean {
        if (!isSectionAdminEnabled(screen)) return false
        return when (screen) {
            is Screen.Reports -> hasLicenseEntitlement(LicenseFeature.REPORTS)
            is Screen.Archive -> hasLicenseEntitlement(LicenseFeature.ARCHIVE)
            else -> true
        }
    }

    fun setSectionEnabled(sectionKey: String, enabled: Boolean) {
        appPrefs.edit().putBoolean("section_${sectionKey}_enabled", enabled).apply()
        when (sectionKey) {
            "accounts" -> accountsEnabled.value = enabled
            "transfers" -> transfersEnabled.value = enabled
            "customers" -> customersEnabled.value = enabled
            "expenses" -> expensesEnabled.value = enabled
            "reports" -> reportsEnabled.value = enabled
            "archive" -> archiveEnabled.value = enabled
        }
    }

    fun updateExchangeRate(rate: Double) {
        if (rate <= 0.0 || !rate.isFinite()) return
        sarToYerExchangeRate.value = rate
        appPrefs.edit().putFloat("sar_to_yer_rate", rate.toFloat()).apply()
    }

    fun updateCurrencySettings(
        mode: com.example.util.CurrencyMode,
        defaultCurrency: String,
        exchangeRate: Double? = null,
        onComplete: (() -> Unit)? = null
    ) {
        val coercedDefault = when (mode) {
            com.example.util.CurrencyMode.YER_ONLY -> com.example.util.MoneyMath.CURRENCY_YER
            com.example.util.CurrencyMode.SAR_ONLY -> com.example.util.MoneyMath.CURRENCY_SAR
            com.example.util.CurrencyMode.BOTH -> if (defaultCurrency.contains("سعودي")) {
                com.example.util.MoneyMath.CURRENCY_SAR
            } else {
                com.example.util.MoneyMath.CURRENCY_YER
            }
        }
        if (exchangeRate != null && exchangeRate > 0.0 && exchangeRate.isFinite()) {
            updateExchangeRate(exchangeRate)
        }
        brandingCurrencyMode.value = mode.storageKey
        brandingDefaultCurrency.value = coercedDefault

        viewModelScope.launch {
            val current = repository.getAgencyProfileDirect() ?: AgencyProfile(id = 1)
            val updated = current.copy(
                defaultCurrency = coercedDefault,
                currencyMode = mode.storageKey,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveAgencyProfile(updated)
            com.example.util.BrandingManager.applyProfile(context, updated)
            refreshBranding()
            onComplete?.invoke()
        }
    }

    fun refreshBranding() {
        brandingAppName.value = com.example.util.BrandingManager.appName
        brandingAgencyName.value = com.example.util.BrandingManager.agencyName
        brandingOwnerName.value = com.example.util.BrandingManager.ownerName
        brandingPhone.value = com.example.util.BrandingManager.phone
        brandingAddress.value = com.example.util.BrandingManager.address
        brandingAdditionalContact.value = com.example.util.BrandingManager.additionalContact
        brandingTagline.value = com.example.util.BrandingManager.agencyTagline
        brandingQatTypes.value = com.example.util.BrandingManager.qatTypes
        brandingDefaultCurrency.value = com.example.util.BrandingManager.defaultCurrency
        brandingCurrencyMode.value = com.example.util.BrandingManager.currencyMode
        brandingCommercialDetails.value = com.example.util.BrandingManager.commercialDetails
        brandingLogoPath.value = com.example.util.BrandingManager.logoPath
        brandingLogoEnabled.value = com.example.util.BrandingManager.isLogoEnabled
        brandingHeaderEnabled.value = com.example.util.BrandingManager.isHeaderEnabled
        brandingFooterEnabled.value = com.example.util.BrandingManager.isFooterEnabled
        brandingHeaderText.value = com.example.util.BrandingManager.headerText
        brandingFooterText.value = com.example.util.BrandingManager.footerText
        isAgencyConfigured.value = com.example.util.BrandingManager.isConfigured
    }

    fun saveAgencyProfile(
        agencyName: String,
        ownerName: String,
        phone: String = "",
        address: String = "",
        additionalContact: String = "",
        tagline: String = "",
        qatTypes: String = "",
        defaultCurrency: String = "ريال يمني",
        currencyMode: String? = null,
        commercialDetails: String = "",
        appDisplayName: String = brandingAppName.value.ifBlank { com.example.util.BrandingManager.PRODUCT_NAME_AR },
        logoStream: java.io.InputStream? = null,
        clearLogo: Boolean = false,
        markConfigured: Boolean = true,
        navigateNextAfterSetup: Boolean = false,
        showSystemToast: Boolean = false,
        onComplete: ((AgencyProfile) -> Unit)? = null
    ) {
        viewModelScope.launch {
            val current = repository.getAgencyProfileDirect()
            val resolvedLogoPath = if (logoStream != null || clearLogo) {
                com.example.util.BrandingManager.persistLogoFile(context, logoStream, clearLogo)
            } else {
                current?.logoPath ?: com.example.util.BrandingManager.logoPath
            }

            val trimmedAgency = agencyName.trim()
            val trimmedOwner = ownerName.trim()
            val updatedHeaderText = if (current?.headerText.isNullOrBlank() || current?.headerText == current?.agencyName) {
                trimmedAgency
            } else {
                current?.headerText ?: trimmedAgency
            }

            val resolvedMode = when {
                !currencyMode.isNullOrBlank() -> com.example.util.CurrencyMode.fromStorageKey(currencyMode)
                defaultCurrency.trim() == "العملتين معاً" -> com.example.util.CurrencyMode.BOTH
                !current?.currencyMode.isNullOrBlank() -> com.example.util.CurrencyMode.fromStorageKey(current?.currencyMode)
                else -> com.example.util.CurrencyMode.BOTH
            }
            val normalizedDefaultCurr = when (resolvedMode) {
                com.example.util.CurrencyMode.YER_ONLY -> com.example.util.MoneyMath.CURRENCY_YER
                com.example.util.CurrencyMode.SAR_ONLY -> com.example.util.MoneyMath.CURRENCY_SAR
                com.example.util.CurrencyMode.BOTH -> if (defaultCurrency.contains("سعودي")) {
                    com.example.util.MoneyMath.CURRENCY_SAR
                } else {
                    com.example.util.MoneyMath.CURRENCY_YER
                }
            }

            val saved = AgencyProfile(
                id = 1,
                agencyName = trimmedAgency,
                ownerName = trimmedOwner,
                phone = phone.trim(),
                address = address.trim(),
                additionalContact = additionalContact.trim(),
                tagline = tagline.trim(),
                qatTypes = qatTypes.trim(),
                defaultCurrency = normalizedDefaultCurr,
                currencyMode = resolvedMode.storageKey,
                commercialDetails = commercialDetails.trim(),
                appDisplayName = appDisplayName.trim().ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR },
                logoPath = resolvedLogoPath,
                headerEnabled = current?.headerEnabled ?: com.example.util.BrandingManager.isHeaderEnabled,
                footerEnabled = current?.footerEnabled ?: com.example.util.BrandingManager.isFooterEnabled,
                headerText = updatedHeaderText,
                footerText = current?.footerText ?: com.example.util.BrandingManager.footerText,
                isConfigured = markConfigured,
                updatedAt = System.currentTimeMillis()
            )
            repository.saveAgencyProfile(saved)
            com.example.util.BrandingManager.applyProfile(context, saved)
            refreshBranding()

            val bitmap = com.example.util.BrandingManager.getCustomLogoBitmap(context)
            com.example.util.LauncherIdentityManager.applyIdentityToAndroid(
                context = context,
                appName = saved.appDisplayName,
                logoBitmap = bitmap,
                showNotificationToast = showSystemToast
            )

            if (navigateNextAfterSetup) {
                val lastUser = appPrefs.getString("remembered_user", null)
                val isLocked = appPrefs.getBoolean("app_locked_v2", false)
                if (lastUser != null && !isLocked) {
                    isLoggedIn.value = true
                    _currentScreen.value = Screen.Dashboard
                } else {
                    _currentScreen.value = Screen.Login
                }
            }
            onComplete?.invoke(saved)
        }
    }

    fun updateBranding(
        newName: String,
        newAgency: String,
        newOwner: String,
        newQatTypes: String,
        logoStream: java.io.InputStream? = null,
        clearLogo: Boolean = false
    ): Boolean {
        val result = com.example.util.BrandingManager.saveBranding(
            context = context,
            newAppName = newName,
            newAgencyName = newAgency,
            newOwnerName = newOwner,
            newQatTypes = newQatTypes,
            logoStream = logoStream,
            clearLogo = clearLogo
        )
        if (result) {
            refreshBranding()
            viewModelScope.launch {
                val current = repository.getAgencyProfileDirect()
                val resolvedLogoPath = if (clearLogo) "" else com.example.util.BrandingManager.logoPath
                val updated = (current ?: AgencyProfile(id = 1)).copy(
                    appDisplayName = newName.trim().ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR },
                    agencyName = newAgency.trim(),
                    ownerName = newOwner.trim(),
                    qatTypes = newQatTypes.trim(),
                    logoPath = resolvedLogoPath,
                    isConfigured = newAgency.trim().isNotEmpty() || (current?.isConfigured == true),
                    updatedAt = System.currentTimeMillis()
                )
                repository.saveAgencyProfile(updated)
            }
        }
        return result
    }

    fun updatePrintSettings(headerEnabled: Boolean, footerEnabled: Boolean, headerTxt: String, footerTxt: String): Boolean {
        val result = com.example.util.BrandingManager.savePrintSettings(context, headerEnabled, footerEnabled, headerTxt, footerTxt)
        if (result) {
            refreshBranding()
            viewModelScope.launch {
                val current = repository.getAgencyProfileDirect()
                if (current != null) {
                    repository.saveAgencyProfile(
                        current.copy(
                            headerEnabled = headerEnabled,
                            footerEnabled = footerEnabled,
                            headerText = headerTxt.trim(),
                            footerText = footerTxt.trim(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            }
        }
        return result
    }

    fun resetBrandingToDefaults() {
        com.example.util.BrandingManager.restoreDefaults(context)
        refreshBranding()
        viewModelScope.launch {
            repository.saveAgencyProfile(AgencyProfile(id = 1, isConfigured = false, updatedAt = System.currentTimeMillis()))
        }
    }

    // --- State-based navigation ---
    private val _currentScreen = MutableStateFlow<Screen>(Screen.Splash)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _navigationHistory = mutableListOf<Screen>()

    fun navigateTo(screen: Screen) {
        if (!isSectionAdminEnabled(screen)) return
        if (screen is Screen.Reports && !hasLicenseEntitlement(LicenseFeature.REPORTS)) {
            proPromptFeature.value = LicenseFeature.REPORTS
            return
        }
        if (screen is Screen.Archive && !hasLicenseEntitlement(LicenseFeature.ARCHIVE)) {
            proPromptFeature.value = LicenseFeature.ARCHIVE
            return
        }
        _navigationHistory.add(_currentScreen.value)
        _currentScreen.value = screen
    }

    fun navigateToDashboard() {
        _navigationHistory.clear()
        _currentScreen.value = Screen.Dashboard
    }

    fun navigateBack() {
        if (_navigationHistory.isNotEmpty()) {
            _currentScreen.value = _navigationHistory.removeAt(_navigationHistory.size - 1)
        } else {
            _currentScreen.value = Screen.Dashboard
        }
    }

    // --- Authentication ---
    var isLoggedIn = MutableStateFlow(false)
    var isPasswordVisible = MutableStateFlow(false)
    val adminUsername = "admin"
    val savedPasswordKey = "admin_password"

    fun getAdminPassword(): String {
        return appPrefs.getString(savedPasswordKey, "123456") ?: "123456"
    }

    fun changeAdminPassword(newPass: String) {
        appPrefs.edit().putString(savedPasswordKey, newPass).apply()
    }

    // --- Theme Settings ---
    val isDarkMode = MutableStateFlow(appPrefs.getBoolean("dark_mode", false))

    fun toggleTheme() {
        val newVal = !isDarkMode.value
        isDarkMode.value = newVal
        appPrefs.edit().putBoolean("dark_mode", newVal).apply()
    }

    // --- Passcode / Lock settings ---
    val isAppLockEnabled = MutableStateFlow(appPrefs.getBoolean("app_locked_v2", false))
    fun toggleAppLock(enabled: Boolean) {
        isAppLockEnabled.value = enabled
        appPrefs.edit().putBoolean("app_locked_v2", enabled).apply()
    }



    // --- Live Flows from Repository ---
    val inventoryItems: StateFlow<List<InventoryItem>> = repository.allInventoryItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoices: StateFlow<List<SalesInvoice>> = repository.allInvoices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPayments: StateFlow<List<CustomerPayment>> = repository.allPaymentsCode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allSuppliers: StateFlow<List<Supplier>> = repository.allSuppliers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchases: StateFlow<List<SupplierPurchase>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allExpenses: StateFlow<List<Expense>> = repository.allExpenses
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTaxHistory: StateFlow<List<TaxEntry>> = repository.allTax
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allTransfers: StateFlow<List<MoneyTransfer>> = repository.allTransfers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPurchaseItems: StateFlow<List<com.example.data.SupplierPurchaseItem>> = repository.allPurchaseItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allInvoiceItems: StateFlow<List<com.example.data.SalesInvoiceItem>> = repository.allInvoiceItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allWeights: StateFlow<List<QatImportWeight>> = repository.allWeights
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Today Qat Tax Tracker ---
    val todayTaxAmount = MutableStateFlow(0.0)
    val todayTaxEntry = MutableStateFlow<TaxEntry?>(null)

    fun fetchTodayTax() {
        viewModelScope.launch {
            val entry = repository.getTaxEntryForDate(Helpers.getCurrentDate())
            todayTaxEntry.value = entry ?: TaxEntry(date = Helpers.getCurrentDate(), taxAmount = 0.0, stallOutflow = 0.0, laborOutflow = 0.0, notes = "")
            todayTaxAmount.value = entry?.taxAmount ?: 0.0
        }
    }

    fun saveTodayTax(amount: Double) {
        viewModelScope.launch {
            val current = todayTaxEntry.value ?: TaxEntry(date = Helpers.getCurrentDate(), taxAmount = 0.0)
            val updated = current.copy(taxAmount = amount)
            repository.saveTaxEntry(updated)
            todayTaxEntry.value = updated
            todayTaxAmount.value = amount
        }
    }

    fun saveTodayTaxAndOutflows(tax: Double, stall: Double, labor: Double, notes: String) {
        viewModelScope.launch {
            val entry = TaxEntry(
                date = Helpers.getCurrentDate(),
                taxAmount = tax,
                stallOutflow = stall,
                laborOutflow = labor,
                notes = notes
            )
            repository.saveTaxEntry(entry)
            todayTaxEntry.value = entry
            todayTaxAmount.value = tax
        }
    }

    // --- Qat Import Weight Operations ---
    fun saveQatImportWeight(date: String, weight: Double, notes: String) {
        viewModelScope.launch {
            val existing = repository.getWeightForDate(date)
            val receiver = existing?.receiverName ?: ""
            val entry = QatImportWeight(id = existing?.id ?: 0, date = date, weight = weight, notes = notes, receiverName = receiver)
            repository.saveWeight(entry)
        }
    }

    fun saveQatImportReceiver(date: String, receiverName: String) {
        viewModelScope.launch {
            val existing = repository.getWeightForDate(date)
            if (existing != null) {
                val updated = existing.copy(receiverName = receiverName)
                repository.saveWeight(updated)
            } else {
                val newEntry = QatImportWeight(date = date, weight = 0.0, notes = "", receiverName = receiverName)
                repository.saveWeight(newEntry)
            }
        }
    }

    fun saveQatImportRecord(id: Int, date: String, weight: Double, receiverName: String, notes: String) {
        viewModelScope.launch {
            val entry = QatImportWeight(id = id, date = date, weight = weight, receiverName = receiverName, notes = notes)
            repository.saveWeight(entry)
        }
    }

    fun deleteQatImportWeightById(id: Int) {
        viewModelScope.launch {
            repository.deleteWeightById(id)
        }
    }

    fun deleteQatImportWeightByDate(date: String) {
        viewModelScope.launch {
            repository.deleteWeightByDate(date)
        }
    }

    // --- Inventory Operations ---
    fun saveInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.addOrUpdateInventoryItem(item)
        }
    }

    suspend fun saveInventoryItemDirectly(item: InventoryItem): Int {
        return repository.addOrUpdateInventoryItem(item).toInt()
    }

    fun deleteInventoryItem(item: InventoryItem) {
        viewModelScope.launch {
            repository.deleteInventoryItem(item)
        }
    }

    // --- Sales Operations ---
    fun executeSale(
        customerName: String,
        customerPhone: String = "",
        customerAddress: String = "",
        customerNotes: String = "",
        items: List<Pair<InventoryItem, Double>>,
        discount: Double,
        paidAmount: Double,
        paymentMethod: String = "نقداً",
        currency: String = "ريال يمني",
        paidAmountYer: Double = 0.0,
        paidAmountSar: Double = 0.0,
        paymentMethodYer: String = "نقداً",
        paymentMethodSar: String = "نقداً",
        onSuccess: (Long) -> Unit
    ) {
        viewModelScope.launch {
            val list = repository.allCustomers.firstOrNull() ?: emptyList()
            val exists = list.any { it.name.trim().equals(customerName.trim(), ignoreCase = true) }
            if (!exists && customerName.trim().isNotEmpty() && customerName.trim() != "عميل نقدي") {
                repository.addCustomer(Customer(name = customerName.trim(), phone = customerPhone, totalDebt = 0.0, address = customerAddress, notes = customerNotes))
            }
            val invoiceId = repository.makeSale(
                customerName = customerName,
                date = Helpers.getCurrentDateTime(),
                items = items,
                taxPercent = 0.0,
                discount = discount,
                paidAmount = paidAmount,
                paymentMethod = paymentMethod,
                currency = currency,
                paidAmountYer = paidAmountYer,
                paidAmountSar = paidAmountSar,
                paymentMethodYer = paymentMethodYer,
                paymentMethodSar = paymentMethodSar,
                exchangeRate = sarToYerExchangeRate.value
            )
            onSuccess(invoiceId)
        }
    }

    fun updateSalesInvoice(
        invoiceId: Int,
        newCustomerName: String,
        newPaidAmount: Double,
        newPaymentMethod: String
    ) {
        viewModelScope.launch {
            repository.updateInvoice(invoiceId, newCustomerName, newPaidAmount, newPaymentMethod)
        }
    }

    fun updateSalesInvoiceFull(
        invoiceId: Int,
        newCustomerName: String,
        newDate: String,
        newCurrency: String,
        newTotalAmount: Double,
        newTaxApplied: Double,
        newDiscount: Double,
        newPaidAmount: Double,
        newDebtAmount: Double,
        newPaymentMethod: String,
        newTotalAmountYer: Double = 0.0,
        newTotalAmountSar: Double = 0.0,
        newPaidAmountYer: Double = 0.0,
        newPaidAmountSar: Double = 0.0,
        newDebtAmountYer: Double = 0.0,
        newDebtAmountSar: Double = 0.0,
        newPaymentMethodYer: String = "نقداً",
        newPaymentMethodSar: String = "نقداً"
    ) {
        viewModelScope.launch {
            repository.updateSalesInvoiceFull(
                invoiceId = invoiceId,
                newCustomerName = newCustomerName,
                newDate = newDate,
                newCurrency = newCurrency,
                newTotalAmount = newTotalAmount,
                newTaxApplied = newTaxApplied,
                newDiscount = newDiscount,
                newPaidAmount = newPaidAmount,
                newDebtAmount = newDebtAmount,
                newPaymentMethod = newPaymentMethod,
                newTotalAmountYer = newTotalAmountYer,
                newTotalAmountSar = newTotalAmountSar,
                newPaidAmountYer = newPaidAmountYer,
                newPaidAmountSar = newPaidAmountSar,
                newDebtAmountYer = newDebtAmountYer,
                newDebtAmountSar = newDebtAmountSar,
                newPaymentMethodYer = newPaymentMethodYer,
                newPaymentMethodSar = newPaymentMethodSar
            )
        }
    }

    fun updateCustomerPayment(
        paymentId: Int,
        newAmount: Double,
        newDate: String,
        newNotes: String,
        newPaymentMethod: String,
        newCurrency: String
    ) {
        viewModelScope.launch {
            repository.updateCustomerPayment(
                paymentId = paymentId,
                newAmount = newAmount,
                newDate = newDate,
                newNotes = newNotes,
                newPaymentMethod = newPaymentMethod,
                newCurrency = newCurrency
            )
        }
    }

    fun voidSale(invoiceId: Int) {
        viewModelScope.launch {
            repository.deleteSale(invoiceId)
        }
    }

    fun getSaleItemsFlow(invoiceId: Int): Flow<List<SalesInvoiceItem>> {
        return repository.getSaleItems(invoiceId)
    }

    // --- Customer Operations ---
    fun createCustomer(name: String, phone: String, address: String = "", notes: String = "") {
        viewModelScope.launch {
            repository.addCustomer(Customer(name = name, phone = phone, totalDebt = 0.0, address = address, notes = notes))
        }
    }

    fun updateCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.updateCustomer(customer)
        }
    }

    fun deleteCustomer(customer: Customer) {
        viewModelScope.launch {
            repository.deleteCustomer(customer)
        }
    }

    fun addCustomerPayment(customerId: Int, amount: Double, notes: String, paymentMethod: String = "نقداً", currency: String = "ريال يمني") {
        viewModelScope.launch {
            repository.payCustomerDebt(
                customerId = customerId,
                amount = amount,
                date = Helpers.getCurrentDateTime(),
                notes = notes,
                paymentMethod = paymentMethod,
                currency = currency
            )
        }
    }

    fun recordDirectDebt(
        customerName: String,
        amount: Double,
        statement: String,
        date: String = Helpers.getCurrentDateTime(),
        notes: String = "",
        currency: String = "ريال يمني"
    ) {
        viewModelScope.launch {
            repository.recordDirectCustomerDebt(
                customerName = customerName,
                amount = amount,
                date = date,
                statement = statement,
                notes = notes,
                currency = currency
            )
        }
    }

    fun getPaymentsForCustomer(customerId: Int): Flow<List<CustomerPayment>> {
        return repository.getPaymentsForCustomer(customerId)
    }

    // --- Supplier Operations ---
    fun createSupplier(name: String, phone: String, region: String = "") {
        viewModelScope.launch {
            repository.addSupplier(Supplier(name = name, phone = phone, totalBalance = 0.0, region = region))
        }
    }

    fun updateSupplier(supplier: Supplier) {
        viewModelScope.launch {
            repository.updateSupplier(supplier)
        }
    }

    fun deleteSupplier(supplier: Supplier) {
        viewModelScope.launch {
            repository.deleteSupplier(supplier)
        }
    }

    fun executeSupplierPurchase(
        supplierId: Int,
        itemsPriceAndQty: List<Triple<InventoryItem, Double, Double>>, // Item, Qty, UnitPurchasePrice
        paidAmount: Double,
        paymentMethod: String = "نقداً",
        paidAmountYer: Double = 0.0,
        paidAmountSar: Double = 0.0,
        paymentMethodYer: String = "نقداً",
        paymentMethodSar: String = "نقداً",
        customDate: String? = null
    ) {
        viewModelScope.launch {
            repository.makeSupplierPurchase(
                supplierId = supplierId,
                date = customDate ?: Helpers.getCurrentDateTime(),
                itemsPriceAndQty = itemsPriceAndQty,
                paidAmount = paidAmount,
                paymentMethod = paymentMethod,
                paidAmountYer = paidAmountYer,
                paidAmountSar = paidAmountSar,
                paymentMethodYer = paymentMethodYer,
                paymentMethodSar = paymentMethodSar
            )
        }
    }

    fun executeSupplierReturn(
        supplierId: Int,
        itemId: Int,
        itemName: String,
        returnedQty: Double,
        returnedPrice: Double,
        returnDate: String,
        refundedAmount: Double,
        notes: String
    ) {
        viewModelScope.launch {
            repository.makeSupplierReturn(
                supplierId = supplierId,
                itemId = itemId,
                itemName = itemName,
                returnedQty = returnedQty,
                returnedPrice = returnedPrice,
                returnDate = returnDate,
                refundedAmount = refundedAmount,
                notes = notes
            )
        }
    }

    fun voidPurchase(purchaseId: Int) {
        viewModelScope.launch {
            repository.deletePurchase(purchaseId)
        }
    }

    fun updateSupplierPurchase(
        purchaseId: Int,
        newPaidAmount: Double,
        newPaymentMethod: String
    ) {
        viewModelScope.launch {
            repository.updatePurchaseInvoice(purchaseId, newPaidAmount, newPaymentMethod)
        }
    }

    fun paySupplierInvoice(
        purchaseId: Int,
        payAmount: Double,
        currency: String,
        paymentDetails: String,
        dateOfPayment: String,
        transferNumber: String = "",
        bankName: String = "",
        demarSar: Double = 0.0,
        demarYer: Double = 0.0,
        demarTypeYer: String = "",
        demarNumberYer: String = "",
        demarTypeSar: String = "",
        demarNumberSar: String = ""
    ) {
        viewModelScope.launch {
            repository.paySupplierInvoice(
                purchaseId, payAmount, currency, paymentDetails, dateOfPayment,
                transferNumber, bankName, demarSar, demarYer,
                demarTypeYer, demarNumberYer, demarTypeSar, demarNumberSar
            )
        }
    }

    fun editSupplierPurchaseItem(
        purchaseItemId: Int,
        newItemName: String,
        newQuantity: Double,
        newUnitPrice: Double,
        newItemNumber: String
    ) {
        viewModelScope.launch {
            repository.editSupplierPurchaseItem(
                purchaseItemId,
                newItemName,
                newQuantity,
                newUnitPrice,
                newItemNumber
            )
        }
    }

    fun editSupplierPurchase(
        purchaseId: Int,
        newSupplierId: Int,
        newDate: String,
        newItemsPriceAndQty: List<Triple<InventoryItem, Double, Double>>,
        newPaidAmount: Double,
        newPaymentMethod: String,
        paidAmountYer: Double = 0.0,
        paidAmountSar: Double = 0.0,
        paymentMethodYer: String = "نقداً",
        paymentMethodSar: String = "نقداً"
    ) {
        viewModelScope.launch {
            repository.editSupplierPurchaseFull(
                purchaseId = purchaseId,
                newSupplierId = newSupplierId,
                newDate = newDate,
                newItemsPriceAndQty = newItemsPriceAndQty,
                newPaidAmount = newPaidAmount,
                newPaymentMethod = newPaymentMethod,
                paidAmountYer = paidAmountYer,
                paidAmountSar = paidAmountSar,
                paymentMethodYer = paymentMethodYer,
                paymentMethodSar = paymentMethodSar
            )
        }
    }

    fun getPurchaseItemsFlow(purchaseId: Int): Flow<List<SupplierPurchaseItem>> {
        return repository.getPurchaseItems(purchaseId)
    }

    // --- Expenses ---
    fun recordExpense(category: String, amount: Double, notes: String, currency: String = "ريال يمني") {
        viewModelScope.launch {
            repository.addExpense(
                Expense(category = category, amount = amount, date = Helpers.getCurrentDateTime(), notes = notes, currency = currency)
            )
        }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch {
            repository.addExpense(expense)
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteExpense(expense)
        }
    }

    // --- Transfer Recipients & Transfers ---
    val allTransferRecipients: StateFlow<List<TransferRecipientEntity>> = repository.allTransferRecipients
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val activeTransferRecipients: StateFlow<List<TransferRecipientEntity>> = repository.activeTransferRecipients
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun getTransfersForRecipientFlow(recipientId: Int): Flow<List<MoneyTransfer>> {
        return repository.getTransfersByRecipientId(recipientId)
    }

    fun addTransferRecipient(
        name: String,
        phone: String = "",
        company: String = "",
        notes: String = "",
        onCreated: ((TransferRecipientEntity) -> Unit)? = null
    ) {
        if (name.trim().isEmpty()) return
        viewModelScope.launch {
            val created = repository.addTransferRecipient(name, phone, company, notes)
            onCreated?.invoke(created)
        }
    }

    fun updateTransferRecipient(
        recipient: TransferRecipientEntity,
        onComplete: (() -> Unit)? = null
    ) {
        if (recipient.name.trim().isEmpty()) return
        viewModelScope.launch {
            repository.updateTransferRecipient(recipient)
            onComplete?.invoke()
        }
    }

    fun setTransferRecipientActive(id: Int, isActive: Boolean) {
        viewModelScope.launch {
            repository.setTransferRecipientActive(id, isActive)
        }
    }

    fun recordTransfer(
        amount: Double,
        sender: String,
        receiver: String,
        referenceNumber: String,
        statement: String,
        notes: String,
        currency: String = currencyConfig.value.resolvedDefaultCurrency,
        recipientId: Int = 0,
        date: String = Helpers.getCurrentDateTime(),
        status: String = "مكتملة"
    ) {
        viewModelScope.launch {
            repository.addTransfer(
                MoneyTransfer(
                    amount = amount,
                    sender = sender,
                    receiver = receiver,
                    referenceNumber = referenceNumber,
                    statement = statement,
                    notes = notes,
                    date = date.ifBlank { Helpers.getCurrentDateTime() },
                    currency = currency,
                    recipientId = recipientId,
                    status = status
                )
            )
        }
    }

    fun updateTransfer(transfer: MoneyTransfer) {
        viewModelScope.launch {
            repository.addTransfer(transfer)
        }
    }

    fun deleteTransfer(transfer: MoneyTransfer) {
        viewModelScope.launch {
            repository.deleteTransfer(transfer)
        }
    }

    // --- Splash & First-Run Onboarding Router ---
    init {
        viewModelScope.launch {
            try {
                fetchTodayTax()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            val profile = try {
                repository.getAgencyProfileDirect()?.also {
                    com.example.util.BrandingManager.applyProfile(context, it)
                    refreshBranding()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
            delay(1200) // Fast, responsive transition for splash screen
            try {
                if (profile == null || !profile.isConfigured || profile.agencyName.isBlank()) {
                    _currentScreen.value = Screen.AgencySetup
                } else {
                    val lastUser = appPrefs.getString("remembered_user", null)
                    val isLocked = appPrefs.getBoolean("app_locked_v2", false)
                    if (lastUser != null && !isLocked) {
                        isLoggedIn.value = true
                        _currentScreen.value = Screen.Dashboard
                    } else {
                        _currentScreen.value = Screen.Login
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _currentScreen.value = Screen.Login
            }
        }
    }

    fun attemptLogin(username: String, pass: String, remember: Boolean): Boolean {
        val correctPass = getAdminPassword()
        if (username.trim() == adminUsername && pass == correctPass) {
            isLoggedIn.value = true
            if (remember) {
                appPrefs.edit().putString("remembered_user", username).apply()
            } else {
                appPrefs.edit().remove("remembered_user").apply()
            }
            _currentScreen.value = Screen.Dashboard
            return true
        }
        return false
    }

    fun logout() {
        isLoggedIn.value = false
        appPrefs.edit().remove("remembered_user").apply()
        _currentScreen.value = Screen.Login
    }

    fun runStressTestingAndLoad(invoiceCount: Int, customerCount: Int, onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.generateStressTestDataDirectly(invoiceCount, customerCount)
            onComplete()
        }
    }

    fun purgeSystemDatabase(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearDatabaseDirectly()
            onComplete()
        }
    }

    private fun normalizePhoneNumber(phone: String): String {
        var cleaned = phone.replace(Regex("[^0-9]"), "")
        if (cleaned.startsWith("00967")) {
            cleaned = cleaned.substring(5)
        } else if (cleaned.startsWith("967")) {
            cleaned = cleaned.substring(3)
        }
        if (cleaned.startsWith("0")) {
            cleaned = cleaned.substring(1)
        }
        return cleaned
    }

    fun importContactsFromPhone() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cr = context.contentResolver
                val uri = android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI
                val projection = arrayOf(
                    android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER
                )
                
                val cursor = cr.query(uri, projection, null, null, null)
                val contactsToImport = mutableListOf<Pair<String, String>>()
                
                if (cursor != null) {
                    val nameIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numIdx = cursor.getColumnIndex(android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    while (cursor.moveToNext()) {
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) else ""
                        val rawNumber = if (numIdx != -1) cursor.getString(numIdx) else ""
                        
                        // Clean telephone number: allow only numbers and '+'
                        val number = rawNumber.replace(" ", "").replace("-", "").replace("(", "").replace(")", "").replace("[", "").replace("]", "")
                        
                        if (name.trim().isNotEmpty() && number.trim().isNotEmpty()) {
                            contactsToImport.add(Pair(name.trim(), number.trim()))
                        }
                    }
                    cursor.close()
                }
                
                if (contactsToImport.isNotEmpty()) {
                    val existingCustomers = repository.getAllCustomersDirect()
                    val existingPhonesNormalized = existingCustomers.map { normalizePhoneNumber(it.phone) }.filter { it.isNotEmpty() }.toSet()
                    
                    val uniqueNewContacts = contactsToImport.distinctBy { normalizePhoneNumber(it.second) }.filter { contact ->
                        val normalized = normalizePhoneNumber(contact.second)
                        normalized.isNotEmpty() && !existingPhonesNormalized.contains(normalized)
                    }
                    
                    if (uniqueNewContacts.isNotEmpty()) {
                        var importedCount = 0
                        uniqueNewContacts.forEach { contact ->
                            repository.addCustomer(
                                Customer(
                                    name = contact.first,
                                    phone = contact.second,
                                    totalDebt = 0.0,
                                    address = "مستورد من الهاتف",
                                    notes = "جهة اتصال مضافة تلقائياً 📱"
                                )
                            )
                            importedCount++
                        }
                        
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "تم جلب وتنظيم $importedCount جهة اتصال جديدة بنجاح في قسم العملاء! 📱✨",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        launch(kotlinx.coroutines.Dispatchers.Main) {
                            android.widget.Toast.makeText(
                                context,
                                "كل جهات الاتصال المسجلة في هاتفك مستوردة بالفعل في قسم العملاء! ✅",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    launch(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(
                            context,
                            "لم يتم العثور على أي جهات اتصال تحتوي على أرقام هواتف لتشغيل الاستيراد! ⚠️",
                            android.widget.Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                launch(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        context,
                        "حدث خطأ أثناء جلب جهات الاتصال: ${e.localizedMessage}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
}

class AppViewModelFactory(
    private val application: Application,
    private val repository: QatRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AppViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
