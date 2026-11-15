package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterManager
import com.example.util.CurrencyTotals
import com.example.util.Helpers
import com.example.util.MoneyMath
import com.example.util.ThermalReceiptTemplates
import com.qatledger.licensing.LicenseFeature
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatementScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    
    // Load States from ViewModel
    val invoices by viewModel.allInvoices.collectAsState()
    val customers by viewModel.allCustomers.collectAsState()
    val customerPayments by viewModel.allPayments.collectAsState()
    val suppliers by viewModel.allSuppliers.collectAsState()
    val purchases by viewModel.allPurchases.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val transfers by viewModel.allTransfers.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val taxHistory by viewModel.allTaxHistory.collectAsState()
    val allPurchaseItems by viewModel.allPurchaseItems.collectAsState()
    val allInvoiceItems by viewModel.allInvoiceItems.collectAsState()
    val allWeights by viewModel.allWeights.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()
    val allTransferRecipients by viewModel.allTransferRecipients.collectAsState()

    // Permissions/Role toggles + commercial PRO license entitlement check
    val accountsEnabled by viewModel.accountsEnabled.collectAsState()
    val transfersEnabled by viewModel.transfersEnabled.collectAsState()
    val customersEnabled by viewModel.customersEnabled.collectAsState()
    val expensesEnabled by viewModel.expensesEnabled.collectAsState()
    val reportsEnabled by viewModel.reportsEnabled.collectAsState()
    val archiveEnabled by viewModel.archiveEnabled.collectAsState()
    val licenseState by viewModel.licenseState.collectAsState()
    val hasReportsPro = licenseState.hasFeature(LicenseFeature.REPORTS)
    val hasArchivePro = licenseState.hasFeature(LicenseFeature.ARCHIVE)

    // State Variables for Statement Filters
    var selectedSection by remember { mutableStateOf("قسم فاتورة ومبيعات جديدة") }
    var selectedPeriodStr by remember { mutableStateOf("يومي") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    var selectedCurrency by remember { mutableStateOf(currencyConfig.coerceStatementFilter(null)) }
    LaunchedEffect(currencyConfig) {
        selectedCurrency = currencyConfig.coerceStatementFilter(selectedCurrency)
    }
    var selectedQatReceiver by remember { mutableStateOf("الكل") }
    var selectedTransferRecipientId by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(allTransferRecipients) {
        if (selectedTransferRecipientId == null && allTransferRecipients.size == 1) {
            selectedTransferRecipientId = allTransferRecipients.first().id
        }
    }

    // Dropdown options based on permissions and license package
    val sectionsList = remember(
        accountsEnabled, transfersEnabled, customersEnabled, expensesEnabled,
        reportsEnabled, archiveEnabled, hasReportsPro, hasArchivePro
    ) {
        listOf(
            "قسم فاتورة ومبيعات جديدة",
            "قسم التحاسب وجرد الحسابات",
            "قسم سجل الحوالات والتحويلات",
            "قسم دليل وحسابات العملاء",
            "قسم إدارة مخزون القات",
            "قسم كشف الموردين والشراء",
            "قسم المصروفات والمصاريف اليومية",
            "قسم التقارير الحسابية المتقدمة",
            "قسم الأرشيف الكلي للسجلات"
        ).filter { section ->
            when (section) {
                "قسم التحاسب وجرد الحسابات" -> accountsEnabled
                "قسم سجل الحوالات والتحويلات" -> transfersEnabled
                "قسم دليل وحسابات العملاء" -> customersEnabled
                "قسم المصروفات والمصاريف اليومية" -> expensesEnabled
                "قسم التقارير الحسابية المتقدمة" -> reportsEnabled && hasReportsPro
                "قسم الأرشيف الكلي للسجلات" -> archiveEnabled && hasArchivePro
                else -> true
            }
        }
    }

    // Auto-select valid section if current selection is disabled
    LaunchedEffect(sectionsList) {
        if (selectedSection !in sectionsList && sectionsList.isNotEmpty()) {
            selectedSection = sectionsList.first()
        }
    }

    // Dynamic Sub-reports options per section
    val subReportsList = remember(selectedSection) {
        when (selectedSection) {
            "قسم فاتورة ومبيعات جديدة" -> listOf("تقرير المبيعات والعمليات بالتفصيل")
            "قسم دليل وحسابات العملاء" -> listOf("تقرير حسابات الديون والتحصيلات", "كشف حساب وتفاصيل العميل")
            "قسم إدارة مخزون القات" -> listOf("تقرير حركة المخزون والمبيعات بالتفصيل", "تقرير جرد وتقييم المخزون الدوري")
            "قسم كشف الموردين والشراء" -> listOf("تقارير المشتريات والتوريد للمفرش", "كشف حساب المورد التفصيلي")
            "قسم المصروفات والمصاريف اليومية" -> listOf("الكل", "نقل ومواصلات", "إيجار المحل والجمارك", "تعبئة وتغليف وعمالة", "خدمات وتبرعات وطاقة", "مصاريف عامة أخرى")
            "قسم الأرشيف الكلي للسجلات" -> listOf("الكل", "المبيعات", "المشتريات", "المصروفات", "الحوالات", "الديون", "التحصيلات")
            else -> emptyList()
        }
    }

    var selectedSubReport by remember { mutableStateOf("") }
    LaunchedEffect(selectedSection, subReportsList) {
        selectedSubReport = if (subReportsList.isNotEmpty()) subReportsList.first() else ""
    }

    // Customer/Supplier specific dropdown selection
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var selectedSupplier by remember { mutableStateOf<Supplier?>(null) }
    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }

    LaunchedEffect(customers) {
        if (selectedCustomer == null && customers.isNotEmpty()) {
            selectedCustomer = customers.first()
        }
    }
    LaunchedEffect(suppliers) {
        if (selectedSupplier == null && suppliers.isNotEmpty()) {
            selectedSupplier = suppliers.first()
        }
    }

    // Weekly date selectors logic
    var accountingWeekYearMonth by remember {
        val todayStr = Helpers.getCurrentDate()
        mutableStateOf(todayStr.substring(0, 7))
    }
    var accountingWeekNum by remember { mutableStateOf(Helpers.getCurrentWeekOfMonth()) }

    LaunchedEffect(selectedPeriodStr, accountingWeekYearMonth, accountingWeekNum) {
        if (selectedPeriodStr == "أسبوعي") {
            customPeriodValue = "WEEK_FILTER|$accountingWeekYearMonth|$accountingWeekNum"
        }
    }

    // Active period mappings
    val selectedPeriodIndex = when (selectedPeriodStr) {
        "يومي" -> 0
        "أسبوعي" -> 1
        "شهري" -> 2
        "سنوي" -> 3
        "تقويم مخصص" -> 4
        else -> 0
    }

    val customType = when (customPeriodType) {
        "يوم" -> "date"
        "شهر" -> "month"
        "سنة" -> "year"
        else -> "date"
    }

    val pLabel = when (selectedPeriodIndex) {
        0 -> "يومية"
        1 -> "أسبوعية"
        2 -> "شهرية"
        3 -> "سنوية"
        else -> "تقويم مخصص"
    }

    val pValDetail = when (selectedPeriodIndex) {
        0 -> Helpers.getCurrentDate()
        1 -> if (customPeriodValue.startsWith("WEEK_FILTER|")) {
            Helpers.formatCustomPeriodToArabic("أسبوع", customPeriodValue)
        } else {
            "7 أيام الماضية"
        }
        2 -> Helpers.getCurrentDate().substring(0, 7)
        3 -> Helpers.getCurrentDate().substring(0, 4)
        else -> if (customPeriodValue.startsWith("WEEK_FILTER|")) {
            Helpers.formatCustomPeriodToArabic("أسبوع", customPeriodValue)
        } else {
            customPeriodValue
        }
    }

    // Helper functions for currency check
    fun matchesCurrency(currency: String): Boolean {
        return when (selectedCurrency) {
            "الريال اليمني" -> currency.contains("يمني") || currency.contains("YER")
            "الريال السعودي" -> currency.contains("سعودي") || currency.contains("SAR")
            else -> true
        }
    }

    // PDF Export & WhatsApp share triggers
    data class StatementData(
        val headers: List<String>,
        val rows: List<List<String>>,
        val totals: Map<String, String>
    )

    data class StatementQatReportSection(
        val title: String,
        val subtitle: String,
        val items: List<Triple<com.example.data.InventoryItem, Double, Double>>,
        val totalWeight: Double,
        val totalUnitsBought: Double,
        val totalValueYer: Double,
        val totalValueSar: Double
    )

    data class StatementAccountMovement(
        val date: String,
        val type: String,
        val reference: String,
        val notes: String,
        val amount: Double,
        val paid: Double,
        val debt: Double,
        val currency: String = "ريال يمني"
    )

    val reportData = remember(
        selectedSection, selectedSubReport, selectedPeriodStr, customPeriodValue, customType,
        selectedCurrency, selectedCustomer, selectedSupplier, invoices, purchases, expenses,
        transfers, customerPayments, taxHistory, inventoryItems, customers, suppliers,
        allPurchaseItems, allInvoiceItems, allWeights, selectedQatReceiver,
        selectedTransferRecipientId, allTransferRecipients, currencyConfig
    ) {
        var headers = listOf<String>()
        var rows = listOf<List<String>>()
        var totals = mapOf<String, String>()

        try {
            when (selectedSection) {
                "قسم فاتورة ومبيعات جديدة" -> {
                    val matches = invoices.filter { inv ->
                        val hasPeriod = Helpers.matchesPeriod(inv.date, selectedPeriodStr, customPeriodValue, customType)
                        val hasCurrency = when (selectedCurrency) {
                            "الريال اليمني" -> inv.totalAmountYer > 0.0 || (inv.totalAmountYer == 0.0 && inv.totalAmountSar == 0.0 && (inv.currency.contains("يمني") || inv.currency.contains("YER")))
                            "الريال السعودي" -> inv.totalAmountSar > 0.0 || (inv.totalAmountYer == 0.0 && inv.totalAmountSar == 0.0 && (inv.currency.contains("سعودي") || inv.currency.contains("SAR")))
                            else -> true
                        }
                        hasPeriod && hasCurrency
                    }.sortedBy { it.id } // Sort ascending by Invoice ID as required
                    
                    headers = listOf("رقم السند", "التاريخ والوقت بالتفصيل", "اسم العميل", "البيان والأصناف المباعة", "طريقة الدفع", "المبلغ الكلي")
                    rows = matches.map { inv ->
                        val invItems = allInvoiceItems.filter { it.invoiceId == inv.id }
                        val itemsSummary = invItems.joinToString("\n") { "${it.itemName} (${Helpers.formatQuantity(it.quantity)} حبة)" }
                        val formattedAmt = when (selectedCurrency) {
                            "الريال اليمني" -> Helpers.formatWithCurrency(if (inv.totalAmountYer > 0.0) inv.totalAmountYer else inv.totalAmount, "ريال يمني")
                            "الريال السعودي" -> Helpers.formatWithCurrency(if (inv.totalAmountSar > 0.0) inv.totalAmountSar else inv.totalAmount, "ريال سعودي")
                            else -> {
                                if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                    "${Helpers.formatWithCurrency(inv.totalAmountYer, "ريال يمني")}\n${Helpers.formatWithCurrency(inv.totalAmountSar, "ريال سعودي")}"
                                } else if (inv.totalAmountYer > 0.0) {
                                    Helpers.formatWithCurrency(inv.totalAmountYer, "ريال يمني")
                                } else if (inv.totalAmountSar > 0.0) {
                                    Helpers.formatWithCurrency(inv.totalAmountSar, "ريال سعودي")
                                } else {
                                    Helpers.formatWithCurrency(inv.totalAmount, inv.currency)
                                }
                            }
                        }
                        listOf(
                            "سند #${inv.id}",
                            inv.date,
                            inv.customerName,
                            itemsSummary,
                            inv.paymentMethod,
                            formattedAmt
                        )
                    }
                    val totYer = matches.sumOf { if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.totalAmountYer else if (it.currency.contains("يمني")) it.totalAmount else 0.0 }
                    val totSar = matches.sumOf { if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.totalAmountSar else if (it.currency.contains("سعودي")) it.totalAmount else 0.0 }
                    val paidYer = matches.sumOf { if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.paidAmountYer else if (it.currency.contains("يمني")) it.paidAmount else 0.0 }
                    val paidSar = matches.sumOf { if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.paidAmountSar else if (it.currency.contains("سعودي")) it.paidAmount else 0.0 }
                    val debtYer = matches.sumOf { if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.debtAmountYer else if (it.currency.contains("يمني")) it.debtAmount else 0.0 }
                    val debtSar = matches.sumOf { if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.debtAmountSar else if (it.currency.contains("سعودي")) it.debtAmount else 0.0 }
                    
                    totals = buildMap {
                        if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            put("إجمالي مبيعات (يمني)", Helpers.formatWithCurrency(totYer, "ريال يمني"))
                            put("إجمالي مقبوض نقداً (يمني)", Helpers.formatWithCurrency(paidYer, "ريال يمني"))
                            put("ديون جديدة مقيدة (يمني)", Helpers.formatWithCurrency(debtYer, "ريال يمني"))
                        }
                        if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            put("إجمالي مبيعات (سعودي)", Helpers.formatWithCurrency(totSar, "ريال سعودي"))
                            put("إجمالي مقبوض نقداً (سعودي)", Helpers.formatWithCurrency(paidSar, "ريال سعودي"))
                            put("ديون جديدة مقيدة (سعودي)", Helpers.formatWithCurrency(debtSar, "ريال سعودي"))
                        }
                    }
                }
                "قسم التحاسب وجرد الحسابات" -> {
                    val filteredInvoices = invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }
                    val filteredExpenses = expenses.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }
                    val filteredPurchases = purchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }
                    val filteredTaxHistory = taxHistory.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }

                    headers = when (selectedCurrency) {
                        "الريال اليمني" -> listOf("التاريخ", "البند والبيان المحاسبي", "المبلغ بالريال اليمني")
                        "الريال السعودي" -> listOf("التاريخ", "البند والبيان المحاسبي", "المبلغ بالريال السعودي")
                        else -> listOf("التاريخ", "البند والبيان المحاسبي", "المبلغ بالريال اليمني", "المبلغ بالريال السعودي")
                    }
                    
                    val rowsList = mutableListOf<List<String>>()
                    val entries = listOf(
                        Triple("إجمالي المبيعات المحققة (+)", filteredInvoices.sumOf { it.totalAmountYer }, filteredInvoices.sumOf { it.totalAmountSar }),
                        Triple("إجمالي المشتريات والمخازن (-)", filteredPurchases.sumOf { it.totalAmountYer }, filteredPurchases.sumOf { it.totalAmountSar }),
                        Triple("ضرائب القات والجمارك (-)", filteredTaxHistory.sumOf { it.taxAmount }, 0.0),
                        Triple("خرج المفرش المعتمد (-)", filteredTaxHistory.sumOf { it.stallOutflow }, 0.0),
                        Triple("خرج العمال اليومي (-)", filteredTaxHistory.sumOf { it.laborOutflow }, 0.0),
                        Triple(
                            "إجمالي المصاريف والتشغيل (-)",
                            filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount },
                            filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
                        )
                    )
                    
                    entries.forEach { (label, amtYer, amtSar) ->
                        rowsList.add(
                            when (selectedCurrency) {
                                "الريال اليمني" -> listOf(pValDetail, label, Helpers.formatWithCurrency(amtYer, "ريال يمني"))
                                "الريال السعودي" -> listOf(pValDetail, label, Helpers.formatWithCurrency(amtSar, "ريال سعودي"))
                                else -> listOf(
                                    pValDetail,
                                    label,
                                    Helpers.formatWithCurrency(amtYer, "ريال يمني"),
                                    Helpers.formatWithCurrency(amtSar, "ريال سعودي")
                                )
                            }
                        )
                    }
                    rows = rowsList

                    val profitYer = filteredInvoices.sumOf { it.totalAmountYer } - filteredPurchases.sumOf { it.totalAmountYer } - filteredTaxHistory.sumOf { it.taxAmount } - filteredTaxHistory.sumOf { it.stallOutflow } - filteredTaxHistory.sumOf { it.laborOutflow } - filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
                    val profitSar = filteredInvoices.sumOf { it.totalAmountSar } - filteredPurchases.sumOf { it.totalAmountSar } - filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
                    
                    totals = buildMap {
                        if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            val keyYer = if (profitYer >= 0) "صافي الربح بالريال اليمني" else "صافي الخسارة بالريال اليمني"
                            put(keyYer, Helpers.formatWithCurrency(profitYer, "ريال يمني"))
                        }
                        if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            val keySar = if (profitSar >= 0) "صافي الربح بالريال السعودي" else "صافي الخسارة بالريال السعودي"
                            put(keySar, Helpers.formatWithCurrency(profitSar, "ريال سعودي"))
                        }
                    }
                }
                "قسم سجل الحوالات والتحويلات" -> {
                    val matches = transfers.filter {
                        Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) &&
                            matchesCurrency(it.currency) &&
                            (selectedTransferRecipientId == null || it.recipientId == selectedTransferRecipientId)
                    }
                    val sortedMatches = matches.sortedBy { it.date }
                    headers = listOf("التاريخ", "المرسل", "المستلم والجهة", "رقم الحوالة", "المبلغ والعملة")
                    rows = sortedMatches.map { tr ->
                        val recObj = allTransferRecipients.find { it.id == tr.recipientId }
                        val recLabel = if (recObj != null) "${recObj.name} (#${recObj.id})" else tr.receiver
                        listOf(
                            tr.date,
                            tr.sender,
                            recLabel,
                            tr.referenceNumber.ifEmpty { "#${tr.id}" },
                            MoneyMath.formatWithCurrency(tr.amount, tr.currency)
                        )
                    }
                    val cTotals = CurrencyTotals.fromAmounts(matches.map { it.amount to it.currency })
                    totals = buildMap {
                        put("عدد الحوالات", "${sortedMatches.size} حوالة")
                        if (selectedCurrency == "الريال اليمني" || (selectedCurrency != "الريال السعودي" && (currencyConfig.allowsYer || cTotals.yer != 0.0))) {
                            put("إجمالي الحوالات بالريال اليمني", MoneyMath.formatWithCurrency(cTotals.yer, MoneyMath.CURRENCY_YER))
                        }
                        if (selectedCurrency == "الريال السعودي" || (selectedCurrency != "الريال اليمني" && (currencyConfig.allowsSar || cTotals.sar != 0.0))) {
                            put("إجمالي الحوالات بالريال السعودي", MoneyMath.formatWithCurrency(cTotals.sar, MoneyMath.CURRENCY_SAR))
                        }
                        cTotals.otherByCurrency.forEach { (curr, valAmt) ->
                            if (valAmt != 0.0) put("إجمالي ($curr)", MoneyMath.formatWithCurrency(valAmt, curr))
                        }
                    }
                }
                "قسم دليل وحسابات العملاء" -> {
                    if (selectedSubReport == "تقرير حسابات الديون والتحصيلات") {
                        val debts = invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && it.debtAmountYer + it.debtAmountSar > 0 }
                        val payments = customerPayments.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }

                        headers = listOf("التاريخ", "اسم العميل", "نوع العملية", "المبلغ", "العملة", "البيان والملاحظات")
                        
                        val combinedList = mutableListOf<List<String>>()
                        debts.forEach { inv ->
                            if (inv.debtAmountYer > 0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                combinedList.add(listOf(inv.date, inv.customerName, "دين مبيعات", Helpers.formatWithCurrency(inv.debtAmountYer, "ريال يمني"), "ريال يمني", "فاتورة مبيعات رقم ${inv.id}"))
                            }
                            if (inv.debtAmountSar > 0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                combinedList.add(listOf(inv.date, inv.customerName, "دين مبيعات", Helpers.formatWithCurrency(inv.debtAmountSar, "ريال سعودي"), "ريال سعودي", "فاتورة مبيعات رقم ${inv.id}"))
                            }
                        }
                        payments.forEach { pay ->
                            if (matchesCurrency(pay.currency ?: "ريال يمني")) {
                                val custName = customers.find { it.id == pay.customerId }?.name ?: "عميل مجهول"
                                combinedList.add(listOf(pay.date, custName, "سداد دفعة", Helpers.formatWithCurrency(pay.amount, pay.currency ?: "ريال يمني"), pay.currency ?: "ريال يمني", pay.notes ?: "سند تحصيل"))
                            }
                        }

                        rows = combinedList.sortedBy { it[0] }
                        
                        val totDebtYer = debts.sumOf { it.debtAmountYer }
                        val totDebtSar = debts.sumOf { it.debtAmountSar }
                        val totCollYer = payments.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
                        val totCollSar = payments.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }

                        totals = buildMap {
                            if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                put("إجمالي ديون العملاء الجديدة", Helpers.formatWithCurrency(totDebtYer, "ريال يمني"))
                                put("إجمالي تحصيلات وسداد العملاء", Helpers.formatWithCurrency(totCollYer, "ريال يمني"))
                            }
                            if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                put("إجمالي ديون العملاء الجديدة", Helpers.formatWithCurrency(totDebtSar, "ريال سعودي"))
                                put("إجمالي تحصيلات وسداد العملاء", Helpers.formatWithCurrency(totCollSar, "ريال سعودي"))
                            }
                        }
                    } else {
                        // كشف حساب وتفاصيل العميل
                        val cust = selectedCustomer
                        if (cust != null) {
                            val cInvoices = invoices.filter {
                                it.customerName.trim().equals(cust.name.trim(), ignoreCase = true)
                            }
                            val filteredCustInvs = cInvoices.filter { inv ->
                                Helpers.matchesPeriod(inv.date, selectedPeriodStr, customPeriodValue, customType)
                            }

                            val cPayments = customerPayments.filter { it.customerId == cust.id }
                            val filteredCustPays = cPayments.filter { pay ->
                                Helpers.matchesPeriod(pay.date, selectedPeriodStr, customPeriodValue, customType)
                            }

                            val movementsList = mutableListOf<StatementAccountMovement>()
                            filteredCustInvs.forEach { inv ->
                                val isMulti = inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0
                                if (isMulti) {
                                    movementsList.add(
                                        StatementAccountMovement(
                                            date = inv.date,
                                            type = "invoice",
                                            reference = "مبيعات يمني (فاتورة #${inv.id})",
                                            notes = "الدفع: ${inv.paymentMethodYer} (متبقي آجل يمني: ${Helpers.formatWithCurrency(inv.debtAmountYer, "ريال يمني")})",
                                            amount = inv.totalAmountYer,
                                            paid = inv.paidAmountYer,
                                            debt = inv.debtAmountYer,
                                            currency = "ريال يمني"
                                        )
                                    )
                                    movementsList.add(
                                        StatementAccountMovement(
                                            date = inv.date,
                                            type = "invoice",
                                            reference = "مبيعات سعودي (فاتورة #${inv.id})",
                                            notes = "الدفع: ${inv.paymentMethodSar} (متبقي آجل سعودي: ${Helpers.formatWithCurrency(inv.debtAmountSar, "ريال سعودي")})",
                                            amount = inv.totalAmountSar,
                                            paid = inv.paidAmountSar,
                                            debt = inv.debtAmountSar,
                                            currency = "ريال سعودي"
                                        )
                                    )
                                } else {
                                    val dispCurr = if (inv.totalAmountSar > 0.0 || inv.currency.contains("سعودي")) "ريال سعودي" else "ريال يمني"
                                    val dispAmt = if (inv.totalAmountSar > 0.0) inv.totalAmountSar else if (inv.totalAmountYer > 0.0) inv.totalAmountYer else inv.totalAmount
                                    val dispPaid = if (inv.totalAmountSar > 0.0) inv.paidAmountSar else if (inv.totalAmountYer > 0.0) inv.paidAmountYer else inv.paidAmount
                                    val dispDebt = if (inv.totalAmountSar > 0.0) inv.debtAmountSar else if (inv.totalAmountYer > 0.0) inv.debtAmountYer else inv.debtAmount
                                    movementsList.add(
                                        StatementAccountMovement(
                                            date = inv.date,
                                            type = "invoice",
                                            reference = "مبيعات بموجب فاتورة #${inv.id}",
                                            notes = "الدفع: ${inv.paymentMethod} (متبقي آجل: ${Helpers.formatWithCurrency(dispDebt, dispCurr)})",
                                            amount = dispAmt,
                                            paid = dispPaid,
                                            debt = dispDebt,
                                            currency = dispCurr
                                        )
                                    )
                                }
                            }
                            filteredCustPays.forEach { pay ->
                                movementsList.add(
                                    StatementAccountMovement(
                                        date = pay.date,
                                        type = "payment",
                                        reference = "سند قبض تسديد دفعة #${pay.id}",
                                        notes = pay.notes.ifEmpty { "سداد مباشر من العميل" } + " (${pay.paymentMethod})",
                                        amount = pay.amount,
                                        paid = pay.amount,
                                        debt = 0.0,
                                        currency = pay.currency
                                    )
                                )
                            }
                            val sortedMovements = movementsList.sortedByDescending { it.date }

                            headers = listOf("التاريخ", "الحركة / البيان المالي", "العملة", "قيمة الدين المضاف", "قيمة السداد المخصوم")

                            val compiledRows = mutableListOf<List<String>>()
                            sortedMovements.forEach { mov ->
                                compiledRows.add(
                                    listOf(
                                        mov.date.split(" ")[0],
                                        mov.reference + "\n(" + mov.notes + ")",
                                        mov.currency,
                                        if (mov.type == "invoice") Helpers.formatWithCurrency(mov.debt, mov.currency) else "0 ${mov.currency}",
                                        if (mov.type == "payment") Helpers.formatWithCurrency(mov.amount, mov.currency) else "0 ${mov.currency}"
                                    )
                                )
                            }
                            rows = compiledRows

                            val allPaysYer = cPayments.filter { it.currency.contains("يمني") }
                            val allPaysSar = cPayments.filter { it.currency.contains("سعودي") }

                            val invsYerTotal = cInvoices.sumOf { inv ->
                                if (inv.totalAmountYer > 0.0) inv.totalAmountYer
                                else if (inv.currency.contains("يمني")) inv.totalAmount
                                else 0.0
                            }
                            val invsYerPaidAtSale = cInvoices.sumOf { inv ->
                                if (inv.totalAmountYer > 0.0) inv.paidAmountYer
                                else if (inv.currency.contains("يمني")) inv.paidAmount
                                else 0.0
                            }

                            val invsSarTotal = cInvoices.sumOf { inv ->
                                if (inv.totalAmountSar > 0.0) inv.totalAmountSar
                                else if (inv.currency.contains("سعودي")) inv.totalAmount
                                else 0.0
                            }
                            val invsSarPaidAtSale = cInvoices.sumOf { inv ->
                                if (inv.totalAmountSar > 0.0) inv.paidAmountSar
                                else if (inv.currency.contains("سعودي")) inv.paidAmount
                                else 0.0
                            }

                            val debtYer = invsYerTotal - (allPaysYer.sumOf { it.amount } + invsYerPaidAtSale)
                            val debtSar = invsSarTotal - (allPaysSar.sumOf { it.amount } + invsSarPaidAtSale)

                            val periodInvoicedYer = filteredCustInvs.sumOf { inv ->
                                if (inv.totalAmountYer > 0.0) inv.totalAmountYer
                                else if (inv.currency.contains("يمني")) inv.totalAmount
                                else 0.0
                            }
                            val periodInvoicedSar = filteredCustInvs.sumOf { inv ->
                                if (inv.totalAmountSar > 0.0) inv.totalAmountSar
                                else if (inv.currency.contains("سعودي")) inv.totalAmount
                                else 0.0
                            }

                            val periodInvsPaidYerAtSale = filteredCustInvs.sumOf { inv ->
                                if (inv.totalAmountYer > 0.0) inv.paidAmountYer
                                else if (inv.currency.contains("يمني")) inv.paidAmount
                                else 0.0
                            }
                            val periodInvsPaidSarAtSale = filteredCustInvs.sumOf { inv ->
                                if (inv.totalAmountSar > 0.0) inv.paidAmountSar
                                else if (inv.currency.contains("سعودي")) inv.paidAmount
                                else 0.0
                            }

                            val periodPaysYer = filteredCustPays.filter { it.currency.contains("يمني") }
                            val periodPaysSar = filteredCustPays.filter { it.currency.contains("سعودي") }

                            val periodPaidYer = periodPaysYer.sumOf { it.amount } + periodInvsPaidYerAtSale
                            val periodPaidSar = periodPaysSar.sumOf { it.amount } + periodInvsPaidSarAtSale

                            totals = buildMap {
                                if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                    put("إجمالي مبيعات الفترة (ريال يمني)", Helpers.formatWithCurrency(periodInvoicedYer, "ريال يمني"))
                                    put("إجمالي السداد المخصوم للفترة (ريال يمني)", Helpers.formatWithCurrency(periodPaidYer, "ريال يمني"))
                                    put("الرصيد المتبقي الكلي حالياً (ريال يمني)", Helpers.formatWithCurrency(debtYer, "ريال يمني"))
                                }
                                if (selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                    put("----------------------------------------", "----------------------------------------")
                                }
                                if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                    put("إجمالي مبيعات الفترة (ريال سعودي)", Helpers.formatWithCurrency(periodInvoicedSar, "ريال سعودي"))
                                    put("إجمالي السداد المخصوم للفترة (ريال سعودي)", Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي"))
                                    put("الرصيد المتبقي الكلي حالياً (ريال سعودي)", Helpers.formatWithCurrency(debtSar, "ريال سعودي"))
                                }
                            }
                        }
                    }
                }
                "قسم إدارة مخزون القات" -> {
                    if (selectedSubReport == "تقرير حركة المخزون والمبيعات بالتفصيل") {
                        val filteredInvoices = invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency) }
                        val filteredPurchases = purchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency) }
                        val invoiceIds = filteredInvoices.map { it.id }.toSet()
                        val purchaseIds = filteredPurchases.map { it.id }.toSet()
                        val invoiceItemsForPeriod = allInvoiceItems.filter { it.invoiceId in invoiceIds }
                        val purchaseItemsForPeriod = allPurchaseItems.filter { it.purchaseId in purchaseIds }

                        headers = listOf("التاريخ", "العدد المستورد", "وزن القات المستورد (كجم)", "الرقم", "سعر الحبة", "الإجمالي", "النوع")

                        val getWeightForItem = { item: com.example.data.InventoryItem ->
                            val weightObj = if (item.receiverId != 0) {
                                allWeights.find { it.id == item.receiverId }
                            } else if (item.receiverName.trim().isNotEmpty()) {
                                allWeights.find { it.receiverName.trim().equals(item.receiverName.trim(), ignoreCase = true) }
                            } else null
                            val finalWeight = weightObj ?: run {
                                val stdDate = Helpers.standardizeDateToYmd(item.dateAdded)
                                allWeights.find { Helpers.standardizeDateToYmd(it.date) == stdDate }
                            }
                            if (finalWeight != null && finalWeight.weight > 0.0) "${Helpers.formatQuantity(finalWeight.weight)} كجم" else "غير محدد"
                        }

                        val extractId = { option: String ->
                            if (option.contains("[ID: ")) {
                                option.substringAfter("[ID: ").substringBefore("]").trim().toIntOrNull()
                            } else null
                        }

                        val targetId = extractId(selectedQatReceiver)
                        val isAllReceivers = selectedQatReceiver == "عرض تقرير جميع المستلمين (بدون تصفية)" || selectedQatReceiver == "الكل" || selectedQatReceiver.isEmpty()

                        val itemReportRows = inventoryItems.filter { matchesCurrency(it.buyPriceCurrency) }.map { item ->
                            val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                            val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                            Triple(item, soldQty, boughtQty)
                        }.filter { (item, soldQty, boughtQty) ->
                            soldQty > 0.0 || boughtQty > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedPeriodStr, customPeriodValue, customType)
                        }.sortedWith(
                            compareBy<Triple<com.example.data.InventoryItem, Double, Double>> { Helpers.parseToDateSafe(it.first.dateAdded) }
                                .thenBy { it.first.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                                .thenBy { it.first.id }
                        )

                        val sections = mutableListOf<StatementQatReportSection>()
                        val periodWeights = allWeights.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }.sortedBy { it.date }

                        if (isAllReceivers) {
                            periodWeights.forEach { weight ->
                                val receiverItems = itemReportRows.filter {
                                    it.first.receiverId == weight.id || (it.first.receiverId == 0 && it.first.receiverName.trim().isNotEmpty() && it.first.receiverName.trim().equals(weight.receiverName.trim(), ignoreCase = true))
                                }
                                if (receiverItems.isNotEmpty()) {
                                    sections.add(
                                        StatementQatReportSection(
                                            title = "مستلم الشحنة: ${weight.receiverName}",
                                            subtitle = "التاريخ: ${weight.date} | رقم الشحنة: [ID: ${weight.id}]",
                                            items = receiverItems,
                                            totalWeight = weight.weight,
                                            totalUnitsBought = receiverItems.sumOf { if (it.third > 0.0) it.third else it.first.quantity },
                                            totalValueYer = receiverItems.filter { it.first.buyPriceCurrency.contains("يمني") || it.first.buyPriceCurrency.contains("YER") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice },
                                            totalValueSar = receiverItems.filter { it.first.buyPriceCurrency.contains("سعودي") || it.first.buyPriceCurrency.contains("SAR") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                                        )
                                    )
                                }
                            }
                            // Unreceived items
                            val unreceivedItems = itemReportRows.filter {
                                it.first.receiverId == 0 && it.first.receiverName.trim().isEmpty()
                            }
                            if (unreceivedItems.isNotEmpty()) {
                                sections.add(
                                    StatementQatReportSection(
                                        title = "أصناف بدون مستلم مخزون محدد",
                                        subtitle = "أصناف مضافة بدون تعيين مستلم شحنة",
                                        items = unreceivedItems,
                                        totalWeight = 0.0,
                                        totalUnitsBought = unreceivedItems.sumOf { if (it.third > 0.0) it.third else it.first.quantity },
                                        totalValueYer = unreceivedItems.filter { it.first.buyPriceCurrency.contains("يمني") || it.first.buyPriceCurrency.contains("YER") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice },
                                        totalValueSar = unreceivedItems.filter { it.first.buyPriceCurrency.contains("سعودي") || it.first.buyPriceCurrency.contains("SAR") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                                    )
                                )
                            }
                        } else if (selectedQatReceiver == "أصناف بدون مستلم") {
                            val unreceivedItems = itemReportRows.filter {
                                it.first.receiverId == 0 && it.first.receiverName.trim().isEmpty()
                            }
                            if (unreceivedItems.isNotEmpty()) {
                                sections.add(
                                    StatementQatReportSection(
                                        title = "أصناف بدون مستلم مخزون محدد",
                                        subtitle = "أصناف مضافة بدون تعيين مستلم شحنة",
                                        items = unreceivedItems,
                                        totalWeight = 0.0,
                                        totalUnitsBought = unreceivedItems.sumOf { if (it.third > 0.0) it.third else it.first.quantity },
                                        totalValueYer = unreceivedItems.filter { it.first.buyPriceCurrency.contains("يمني") || it.first.buyPriceCurrency.contains("YER") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice },
                                        totalValueSar = unreceivedItems.filter { it.first.buyPriceCurrency.contains("سعودي") || it.first.buyPriceCurrency.contains("SAR") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                                    )
                                )
                            }
                        } else {
                            val weightId = targetId
                            val weight = allWeights.find { it.id == weightId }
                            if (weight != null) {
                                val receiverItems = itemReportRows.filter {
                                    it.first.receiverId == weight.id || (it.first.receiverId == 0 && it.first.receiverName.trim().isNotEmpty() && it.first.receiverName.trim().equals(weight.receiverName.trim(), ignoreCase = true))
                                }
                                if (receiverItems.isNotEmpty()) {
                                    sections.add(
                                        StatementQatReportSection(
                                            title = "مستلم الشحنة: ${weight.receiverName}",
                                            subtitle = "التاريخ: ${weight.date} | رقم الشحنة: [ID: ${weight.id}]",
                                            items = receiverItems,
                                            totalWeight = weight.weight,
                                            totalUnitsBought = receiverItems.sumOf { if (it.third > 0.0) it.third else it.first.quantity },
                                            totalValueYer = receiverItems.filter { it.first.buyPriceCurrency.contains("يمني") || it.first.buyPriceCurrency.contains("YER") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice },
                                            totalValueSar = receiverItems.filter { it.first.buyPriceCurrency.contains("سعودي") || it.first.buyPriceCurrency.contains("SAR") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                                        )
                                    )
                                }
                            }
                        }

                        val groupedRows = mutableListOf<List<String>>()
                        var totalWeightsSumAll = 0.0
                        var totalUnitsBoughtAll = 0.0
                        var totalValueYerAll = 0.0
                        var totalValueSarAll = 0.0

                        sections.forEach { sec ->
                            groupedRows.add(listOf("SECTION_HEADER|${sec.title} - ${sec.subtitle}"))
                            if (sec.items.isEmpty()) {
                                groupedRows.add(listOf("لا توجد أصناف قات مسجلة لهذا المستلم بفترة التقرير"))
                            } else {
                                sec.items.forEach { (item, sold, bought) ->
                                    val importedQty = if (bought > 0.0) bought else item.quantity
                                    val totalVal = importedQty * item.buyPrice
                                    val weightValStr = if (sec.totalWeight > 0.0) "${Helpers.formatQuantity(sec.totalWeight)} كجم" else getWeightForItem(item)
                                    val itemNumStr = item.itemNumber.trim().ifEmpty { item.id.toString() }
                                    groupedRows.add(listOf(
                                        item.dateAdded.ifEmpty { "غير محدد" },
                                        "${Helpers.formatQuantity(importedQty)} حبة",
                                        weightValStr,
                                        itemNumStr,
                                        Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency),
                                        Helpers.formatWithCurrency(totalVal, item.buyPriceCurrency),
                                        "${item.name} ${if (item.qatType.isNotEmpty()) "(${item.qatType})" else ""}"
                                    ))
                                }
                            }

                            totalWeightsSumAll += sec.totalWeight
                            totalUnitsBoughtAll += sec.totalUnitsBought
                            totalValueYerAll += sec.totalValueYer
                            totalValueSarAll += sec.totalValueSar

                            val secTotalYerStr = if (sec.totalValueYer > 0.0) " | يمني: ${Helpers.formatWithCurrency(sec.totalValueYer, "ريال يمني")}" else ""
                            val secTotalSarStr = if (sec.totalValueSar > 0.0) " | سعودي: ${Helpers.formatWithCurrency(sec.totalValueSar, "ريال سعودي")}" else ""
                            val secWeightStr = if (sec.totalWeight > 0.0) " | وزن: ${Helpers.formatQuantity(sec.totalWeight)} كجم" else ""
                            groupedRows.add(listOf("SECTION_TOTALS|إجمالي الوحدات: ${Helpers.formatQuantity(sec.totalUnitsBought)} حبة$secWeightStr$secTotalYerStr$secTotalSarStr"))
                        }

                        rows = groupedRows
                        totals = buildMap {
                            if (totalWeightsSumAll > 0.0) {
                                put("إجمالي وزن كافة المستلمين", "${Helpers.formatQuantity(totalWeightsSumAll)} كجم")
                            }
                            put("إجمالي الوحدات لكافة الأصناف", Helpers.formatQuantityWithUnit(totalUnitsBoughtAll))
                            if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                if (totalValueYerAll > 0.0) {
                                    put("إجمالي القيمة بالريال اليمني", Helpers.formatWithCurrency(totalValueYerAll, "ريال يمني"))
                                }
                            }
                            if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                if (totalValueSarAll > 0.0) {
                                    put("إجمالي القيمة بالريال السعودي", Helpers.formatWithCurrency(totalValueSarAll, "ريال سعودي"))
                                }
                            }
                        }
                    } else {
                        val filteredInvs = invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency) }
                        val filteredPurchs = purchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency) }
                        val invoiceIds = filteredInvs.map { it.id }.toSet()
                        val purchaseIds = filteredPurchs.map { it.id }.toSet()
                        val invoiceItemsForPeriod = allInvoiceItems.filter { it.invoiceId in invoiceIds }
                        val purchaseItemsForPeriod = allPurchaseItems.filter { it.purchaseId in purchaseIds }

                        val sortedPeriodItems = inventoryItems.filter { item ->
                            val matchesCurr = matchesCurrency(item.buyPriceCurrency)
                            val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                            val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                            matchesCurr && (soldQty > 0.0 || boughtQty > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedPeriodStr, customPeriodValue, customType))
                        }.sortedWith(
                            compareBy<InventoryItem> { Helpers.parseToDateSafe(it.dateAdded) }
                                .thenBy { it.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                                .thenBy { it.itemNumber }
                                .thenBy { it.id }
                        )
                        headers = listOf(
                            "التاريخ",
                            "النوع",
                            "رقم الصنف",
                            "المخزون الحالي",
                            "مباع بالفترة",
                            "مورد بالفترة",
                            "سعر الشراء",
                            "سعر البيع الفعلي",
                            "طرق السداد"
                        )
                        
                        val totalStockQty = sortedPeriodItems.sumOf { it.quantity }
                        val totalSoldQty = sortedPeriodItems.sumOf { item -> invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity } }
                        val totalBoughtQty = sortedPeriodItems.sumOf { item -> purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity } }
                        val totalBuyValueYer = sortedPeriodItems.filter { it.buyPriceCurrency.contains("يمني") || it.buyPriceCurrency.contains("YER") }.sumOf { it.quantity * it.buyPrice }
                        val totalBuyValueSar = sortedPeriodItems.filter { it.buyPriceCurrency.contains("سعودي") || it.buyPriceCurrency.contains("SAR") }.sumOf { it.quantity * it.buyPrice }

                        rows = sortedPeriodItems.mapIndexed { index, item ->
                            val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                            val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                            val itemNumStr = item.itemNumber.trim().ifEmpty { item.id.toString() }
                            
                            val saleItems = allInvoiceItems.filter { it.invoiceId in invoiceIds && it.itemId == item.id }
                            
                            val uniquePrices = saleItems.map { saleItem ->
                                val curr = saleItem.saleCurrency.ifEmpty { saleItem.currency }
                                "${Helpers.formatMoney(saleItem.unitPrice)} $curr"
                            }.distinct()
                            val actualSellPriceStr = if (uniquePrices.isEmpty()) "لا يوجد" else uniquePrices.joinToString("\n")

                            val uniqueMethods = saleItems.map { saleItem ->
                                val inv = filteredInvs.find { it.id == saleItem.invoiceId }
                                inv?.paymentMethod ?: "نقداً"
                            }.distinct()
                            val paymentMethodsStr = if (uniqueMethods.isEmpty()) "-" else uniqueMethods.joinToString("\n")

                            listOf(
                                item.dateAdded.ifEmpty { "غير محدد" },
                                "${item.name} (${item.qatType.ifEmpty { "غير محدد" }})",
                                itemNumStr,
                                "${Helpers.formatQuantity(item.quantity)} حبة",
                                "${Helpers.formatQuantity(soldQty)} حبة",
                                "${Helpers.formatQuantity(boughtQty)} حبة",
                                Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency),
                                actualSellPriceStr,
                                paymentMethodsStr
                            )
                        }
                        totals = buildMap {
                            put("عدد البنود المفلترة", "${sortedPeriodItems.size} بند")
                            put("إجمالي كمية المخزون الحالي", "${Helpers.formatQuantity(totalStockQty)} حبة")
                            put("إجمالي كمية المباع بالفترة", "${Helpers.formatQuantity(totalSoldQty)} حبة")
                            put("إجمالي كمية المورد بالفترة", "${Helpers.formatQuantity(totalBoughtQty)} حبة")
                            if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                put("قيمة شراء المخزون باليمني", Helpers.formatWithCurrency(totalBuyValueYer, "ريال يمني"))
                            }
                            if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                                put("قيمة شراء المخزون بالسعودي", Helpers.formatWithCurrency(totalBuyValueSar, "ريال سعودي"))
                            }
                        }
                    }
                }
                "قسم كشف الموردين والشراء" -> {
                    if (selectedSubReport == "تقارير المشتريات والتوريد للمفرش") {
                        val periodPurchases = purchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency) }
                        headers = listOf("التاريخ والسند", "تفاصيل العملية والأصناف المشمولة", "العملة", "المبلغ الكلي", "المدفوع/المسترد", "المتبقي الآجل", "طريقة الدفع/البيان")
                        rows = periodPurchases.map { p ->
                            val sup = suppliers.find { it.id == p.supplierId }
                            val supName = sup?.name ?: "مورد غير معروف"
                            val supRegionSuffix = if (sup != null && sup.region.isNotEmpty()) " (${sup.region})" else ""
                            val isReturn = p.totalAmount < 0 || p.paymentMethod.startsWith("مرتجع")
                            val typeTitle = if (isReturn) "مرتجع" else "توريد"
                            val items = allPurchaseItems.filter { it.purchaseId == p.id }
                            val itemsText = if (items.isNotEmpty()) {
                                "\n الأصناف: " + items.joinToString(" | ") { "${it.itemName} (${Helpers.formatQuantity(java.lang.Math.abs(it.quantity))} حبة • بسعر: ${Helpers.formatWithCurrency(it.unitPrice, p.currency)})" }
                            } else ""
                            val hasDual = (java.lang.Math.abs(p.totalAmountYer) > 0.0 && java.lang.Math.abs(p.totalAmountSar) > 0.0) ||
                                    (java.lang.Math.abs(p.paidAmountYer) > 0.0 && java.lang.Math.abs(p.paidAmountSar) > 0.0) ||
                                    (p.currency.contains("يمني") && p.currency.contains("سعودي"))

                            val currencyCell = if (hasDual) {
                                "ريال يمني\nريال سعودي"
                            } else if (p.currency.contains("سعودي") || p.currency.contains("SAR")) {
                                "ريال سعودي"
                            } else {
                                "ريال يمني"
                            }

                            val totalCell = if (hasDual) {
                                "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(p.totalAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(p.totalAmountSar), "ريال سعودي")}"
                            } else {
                                Helpers.formatWithCurrency(java.lang.Math.abs(p.totalAmount), currencyCell)
                            }

                            val paidCell = if (hasDual) {
                                "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(p.paidAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(p.paidAmountSar), "ريال سعودي")}"
                            } else {
                                Helpers.formatWithCurrency(java.lang.Math.abs(p.paidAmount), currencyCell)
                            }

                            val debtCell = if (hasDual) {
                                "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(p.debtRemainingYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(p.debtRemainingSar), "ريال سعودي")}"
                            } else {
                                Helpers.formatWithCurrency(java.lang.Math.abs(p.debtRemaining), currencyCell)
                            }

                            listOf(
                                p.date,
                                "$typeTitle #${p.id} - المورد: $supName$supRegionSuffix$itemsText",
                                currencyCell,
                                totalCell,
                                paidCell,
                                debtCell,
                                p.paymentMethod
                            )
                        }
                        val periodCostYer = periodPurchases.filter { it.currency.contains("يمني") }.sumOf { it.totalAmount }
                        val periodCostSar = periodPurchases.filter { it.currency.contains("سعودي") }.sumOf { it.totalAmount }
                        val periodPaidYer = periodPurchases.filter { it.currency.contains("يمني") }.sumOf { it.paidAmount }
                        val periodPaidSar = periodPurchases.filter { it.currency.contains("سعودي") }.sumOf { it.paidAmount }
                        val periodDebtYer = periodPurchases.filter { it.currency.contains("يمني") }.sumOf { it.debtRemaining }
                        val periodDebtSar = periodPurchases.filter { it.currency.contains("سعودي") }.sumOf { it.debtRemaining }
                        val outstandingYer = purchases.sumOf { it.debtRemainingYer }
                        val outstandingSar = purchases.sumOf { it.debtRemainingSar }
                        
                        totals = buildMap {
                            put("إجمالي المشتريات بالريال اليمني", Helpers.formatWithCurrency(periodCostYer, "ريال يمني"))
                            put("إجمالي المشتريات بالريال السعودي", Helpers.formatWithCurrency(periodCostSar, "ريال سعودي"))
                            put("المسدد للموردين بالريال اليمني", Helpers.formatWithCurrency(periodPaidYer, "ريال يمني"))
                            put("المسدد للموردين بالريال السعودي", Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي"))
                            put("الديون الجديدة بالريال اليمني", Helpers.formatWithCurrency(periodDebtYer, "ريال يمني"))
                            put("الديون الجديدة بالريال السعودي", Helpers.formatWithCurrency(periodDebtSar, "ريال سعودي"))
                            put("إجمالي الذمم والديون المتبقية المعلقة باليمني", Helpers.formatWithCurrency(outstandingYer, "ريال يمني"))
                            put("إجمالي الذمم والديون المتبقية المعلقة بالسعودي", Helpers.formatWithCurrency(outstandingSar, "ريال سعودي"))
                        }
                    } else {
                        val sup = selectedSupplier
                        if (sup != null) {
                            val filteredStatementPurchases = purchases.filter { it.supplierId == sup.id }.filter { p ->
                                Helpers.matchesPeriod(p.date, selectedPeriodStr, customPeriodValue, customType) &&
                                matchesCurrency(p.currency)
                            }.sortedWith { p1, p2 ->
                                val d1 = Helpers.parseToDateSafe(p1.date)
                                val d2 = Helpers.parseToDateSafe(p2.date)
                                val dateCompare = d1.compareTo(d2)
                                if (dateCompare != 0) {
                                    dateCompare
                                } else {
                                    val p1Items = allPurchaseItems.filter { it.purchaseId == p1.id }
                                    val p2Items = allPurchaseItems.filter { it.purchaseId == p2.id }
                                    
                                    val p1ItemNum = p1Items.mapNotNull { pi -> inventoryItems.find { it.id == pi.itemId }?.itemNumber }.firstOrNull() ?: ""
                                    val p2ItemNum = p2Items.mapNotNull { pi -> inventoryItems.find { it.id == pi.itemId }?.itemNumber }.firstOrNull() ?: ""
                                    
                                    val n1 = p1ItemNum.toIntOrNull()
                                    val n2 = p2ItemNum.toIntOrNull()
                                    
                                    if (n1 != null && n2 != null) {
                                        val numCompare = n1.compareTo(n2)
                                        if (numCompare != 0) numCompare else p1ItemNum.compareTo(p2ItemNum)
                                    } else if (n1 != null) {
                                        -1
                                    } else if (n2 != null) {
                                        1
                                    } else {
                                        val numStrCompare = p1ItemNum.compareTo(p2ItemNum)
                                        if (numStrCompare != 0) numStrCompare else p1.id.compareTo(p2.id)
                                    }
                                }
                            }

                            val sPurchasesAll = purchases.filter { it.supplierId == sup.id }
                            val totalOutstandingYer = sPurchasesAll.sumOf { it.debtRemainingYer }
                            val totalOutstandingSar = sPurchasesAll.sumOf { it.debtRemainingSar }

                            // Purchases (positive totalAmount)
                            val periodTotalPurchasesYer = filteredStatementPurchases.filter { it.totalAmount > 0 }.sumOf { it.totalAmountYer }
                            val periodTotalPurchasesSar = filteredStatementPurchases.filter { it.totalAmount > 0 }.sumOf { it.totalAmountSar }

                            // Returns (negative totalAmount or starts with "مرتجع")
                            val periodTotalReturnsYer = java.lang.Math.abs(filteredStatementPurchases.filter { it.totalAmount < 0 || it.paymentMethod.startsWith("مرتجع") }.sumOf { it.totalAmountYer })
                            val periodTotalReturnsSar = java.lang.Math.abs(filteredStatementPurchases.filter { it.totalAmount < 0 || it.paymentMethod.startsWith("مرتجع") }.sumOf { it.totalAmountSar })

                            // Paid amounts
                            val periodTotalPaidYer = filteredStatementPurchases.filter { it.totalAmount > 0 }.sumOf { it.paidAmountYer }
                            val periodTotalPaidSar = filteredStatementPurchases.filter { it.totalAmount > 0 }.sumOf { it.paidAmountSar }

                            // Refund amounts for returns
                            val periodTotalRefundsYer = java.lang.Math.abs(filteredStatementPurchases.filter { it.totalAmount < 0 || it.paymentMethod.startsWith("مرتجع") }.sumOf { it.paidAmountYer })
                            val periodTotalRefundsSar = java.lang.Math.abs(filteredStatementPurchases.filter { it.totalAmount < 0 || it.paymentMethod.startsWith("مرتجع") }.sumOf { it.paidAmountSar })

                            val sPurchasesBeforePeriod = sPurchasesAll.filter { p ->
                                Helpers.isBeforePeriod(p.date, selectedPeriodStr, customPeriodValue, customType)
                            }
                            val prevOutstandingYer = sPurchasesBeforePeriod.sumOf { it.debtRemainingYer }
                            val prevOutstandingSar = sPurchasesBeforePeriod.sumOf { it.debtRemainingSar }

                            val hasReturns = filteredStatementPurchases.any { it.totalAmount < 0 || it.paymentMethod.startsWith("مرتجع") }
                            val hasRemainingDebt = filteredStatementPurchases.any { it.debtRemaining > 0.0 || it.debtRemainingYer > 0.0 || it.debtRemainingSar > 0.0 }
                            val hasNotes = filteredStatementPurchases.any { purchase ->
                                val notesPart = Helpers.extractActualPaymentNotes(purchase.paymentMethod, purchase.bankName)
                                purchase.bankName.isNotEmpty() || notesPart.isNotEmpty()
                            }

                            val sHeaders = mutableListOf<String>()
                            sHeaders.add("التاريخ")
                            sHeaders.add("ضمار البيعة لليوم")
                            sHeaders.add("رقم الحوالة أو الإيداع")
                            if (hasNotes) {
                                sHeaders.add("الملاحظات")
                            }
                            sHeaders.add("نوع العملية")
                            sHeaders.add("القيمة الإجمالية")
                            sHeaders.add("المبلغ المسدد")
                            if (hasReturns) {
                                sHeaders.add("المسترد")
                            }
                            if (hasRemainingDebt) {
                                sHeaders.add("الآجل المتبقي")
                            }
                            headers = sHeaders

                            rows = filteredStatementPurchases.map { purchase ->
                                val isReturn = purchase.totalAmount < 0 || purchase.paymentMethod.startsWith("مرتجع")
                                val typeStr = if (purchase.totalAmount == 0.0) "سداد" else if (isReturn) "مرتجع" else "توريد"
                                val absTotal = java.lang.Math.abs(purchase.totalAmount)
                                val absPaid = java.lang.Math.abs(purchase.paidAmount)
                                val absDebt = java.lang.Math.abs(purchase.debtRemaining)
                                
                                val curLabel = if (isReturn) {
                                    if (purchase.totalAmountYer < 0 && purchase.totalAmountSar < 0) "ريال يمني / ريال سعودي"
                                    else if (purchase.totalAmountSar < 0) "ريال سعودي"
                                    else "ريال يمني"
                                } else {
                                    purchase.currency
                                }

                                val demarCell = buildString {
                                    val parts = mutableListOf<String>()
                                    if (purchase.demarYer > 0.0) {
                                        val typeSuffix = if (purchase.demarTypeYer.isNotEmpty()) " (${purchase.demarTypeYer})" else ""
                                        parts.add("يمني: ${Helpers.formatWithCurrency(purchase.demarYer, "ريال يمني")}$typeSuffix")
                                    }
                                    if (purchase.demarSar > 0.0) {
                                        val typeSuffix = if (purchase.demarTypeSar.isNotEmpty()) " (${purchase.demarTypeSar})" else ""
                                        parts.add("سعودي: ${Helpers.formatWithCurrency(purchase.demarSar, "ريال سعودي")}$typeSuffix")
                                    }
                                    if (parts.isEmpty()) {
                                        append("-")
                                    } else {
                                        append(parts.joinToString("\n"))
                                    }
                                }

                                val transferNoCell = buildString {
                                    val parts = mutableListOf<String>()
                                    if (purchase.demarNumberYer.isNotEmpty()) {
                                        parts.add("يمني: ${purchase.demarNumberYer}")
                                    }
                                    if (purchase.demarNumberSar.isNotEmpty()) {
                                        parts.add("سعودي: ${purchase.demarNumberSar}")
                                    }
                                    if (purchase.transferNumber.isNotEmpty()) {
                                        val tNo = purchase.transferNumber.trim()
                                        val isCombinedDemar = tNo == "${purchase.demarNumberYer} / ${purchase.demarNumberSar}".trim() ||
                                                tNo == "${purchase.demarNumberYer}/${purchase.demarNumberSar}".trim()
                                        if (!isCombinedDemar) {
                                            val subParts = tNo.split("/", ",", "\n", "|").map { it.trim() }.filter { it.isNotEmpty() }
                                            for (sub in subParts) {
                                                if (!parts.any { it.contains(sub) }) {
                                                    parts.add(sub)
                                                }
                                            }
                                        }
                                    }
                                    if (parts.isEmpty()) {
                                        append("-")
                                    } else {
                                        append(parts.distinct().joinToString("\n"))
                                    }
                                }

                                val notesCell = Helpers.extractSupplierStatementNotes(purchase.paymentMethod, purchase.bankName).ifEmpty { "-" }
                                
                                val hasDual = java.lang.Math.abs(purchase.totalAmountYer) > 0.0 && java.lang.Math.abs(purchase.totalAmountSar) > 0.0
                                val showYemeniOnly = selectedCurrency == "الريال اليمني"
                                val showSaudiOnly = selectedCurrency == "الريال السعودي"

                                val totalCell = if (showYemeniOnly) {
                                    Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")
                                } else if (showSaudiOnly) {
                                    Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")
                                } else if (hasDual) {
                                    "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")}"
                                } else {
                                    Helpers.formatWithCurrency(absTotal, curLabel)
                                }

                                val paidCell = if (showYemeniOnly) {
                                    Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountYer), "ريال يمني")
                                } else if (showSaudiOnly) {
                                    Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountSar), "ريال سعودي")
                                } else if (hasDual) {
                                    "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountSar), "ريال سعودي")}"
                                } else {
                                    Helpers.formatWithCurrency(absPaid, curLabel)
                                }

                                val returnCell = if (isReturn) {
                                    if (showYemeniOnly) {
                                        Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")
                                    } else if (showSaudiOnly) {
                                        Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")
                                    } else if (hasDual) {
                                        "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")}"
                                    } else {
                                        Helpers.formatWithCurrency(absTotal, curLabel)
                                    }
                                } else {
                                    "-"
                                }

                                val debtCell = if (showYemeniOnly) {
                                    Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingYer), "ريال يمني")
                                } else if (showSaudiOnly) {
                                    Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingSar), "ريال سعودي")
                                } else if (hasDual) {
                                    "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingSar), "ريال سعودي")}"
                                } else {
                                    Helpers.formatWithCurrency(absDebt, curLabel)
                                }

                                val rowCells = mutableListOf<String>()
                                rowCells.add(Helpers.formatDateTo29June2026(purchase.date)) // 1. التاريخ
                                rowCells.add(demarCell) // 2. ضمار صافي البيعة لليوم
                                rowCells.add(transferNoCell) // 3. رقم الحوالة أو الإيداع
                                if (hasNotes) {
                                    rowCells.add(notesCell) // 4. الملاحظات
                                }
                                rowCells.add(typeStr) // 5. نوع العملية
                                rowCells.add(if (purchase.totalAmount == 0.0) "-" else totalCell) // 6. القيمة الإجمالية
                                rowCells.add(paidCell) // 7. المبلغ المسدد
                                if (hasReturns) {
                                    rowCells.add(returnCell) // 8. المسترد
                                }
                                if (hasRemainingDebt) {
                                    rowCells.add(debtCell) // 9. الآجل المتبقي
                                }
                                rowCells
                            }

                            val pdfTotals = mutableMapOf<String, String>()
                            if (prevOutstandingYer > 0.0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["الرصيد السابق بالريال اليمني"] = Helpers.formatWithCurrency(prevOutstandingYer, "ريال يمني")
                            }
                            if (prevOutstandingSar > 0.0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["الرصيد السابق بالريال السعودي"] = Helpers.formatWithCurrency(prevOutstandingSar, "ريال سعودي")
                            }
                            if (periodTotalPurchasesYer > 0.0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["إجمالي المشتريات المستوردة بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalPurchasesYer, "ريال يمني")
                            }
                            if (periodTotalPurchasesSar > 0.0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["إجمالي المشتريات المستوردة بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalPurchasesSar, "ريال سعودي")
                            }
                            if (periodTotalReturnsYer > 0.0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["إجمالي مرتجعات البضاعة بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalReturnsYer, "ريال يمني")
                            }
                            if (periodTotalReturnsSar > 0.0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["إجمالي مرتجعات البضاعة بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalReturnsSar, "ريال سعودي")
                            }
                            if (periodTotalPaidYer > 0.0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["المسدد الفعلي للمورد بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalPaidYer, "ريال يمني")
                            }
                            if (periodTotalPaidSar > 0.0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["المسدد الفعلي للمورد بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalPaidSar, "ريال سعودي")
                            }
                            if (periodTotalRefundsYer > 0.0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["المسترد الفعلي للمرتجع بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalRefundsYer, "ريال يمني")
                            }
                            if (periodTotalRefundsSar > 0.0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["المسترد الفعلي للمرتجع بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalRefundsSar, "ريال سعودي")
                            }
                            if (totalOutstandingYer > 0.0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["المديونية الكلية المستحقة له بالريال اليمني"] = Helpers.formatWithCurrency(totalOutstandingYer, "ريال يمني")
                            }
                            if (totalOutstandingSar > 0.0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                pdfTotals["المديونية الكلية المستحقة له بالريال السعودي"] = Helpers.formatWithCurrency(totalOutstandingSar, "ريال سعودي")
                            }
                            totals = pdfTotals
                        }
                    }
                }
                "قسم المصروفات والمصاريف اليومية" -> {
                    val matches = expenses.filter {
                        Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) &&
                        matchesCurrency(it.currency ?: "ريال يمني") &&
                        (selectedSubReport == "الكل" || it.category == selectedSubReport)
                    }
                    val sortedMatches = matches.sortedBy { it.date }
                    headers = listOf("التاريخ", "التصنيف", "المبلغ المصروف", "العملة", "البيان والملاحظات")
                    rows = sortedMatches.map {
                        listOf(it.date, it.category, Helpers.formatWithCurrency(it.amount, it.currency ?: "ريال يمني"), it.currency ?: "ريال يمني", it.notes)
                    }
                    val totYer = matches.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
                    val totSar = matches.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
                    totals = buildMap {
                        if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            put("إجمالي المصروفات بالريال اليمني", Helpers.formatWithCurrency(totYer, "ريال يمني"))
                        }
                        if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            put("إجمالي المصروفات بالريال السعودي", Helpers.formatWithCurrency(totSar, "ريال سعودي"))
                        }
                    }
                }
                "قسم التقارير الحسابية المتقدمة" -> {
                    val filteredInvoices = invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }
                    val filteredExpenses = expenses.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }
                    val filteredPurchases = purchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }
                    val filteredTaxHistory = taxHistory.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }

                    headers = listOf("التاريخ", "المؤشر المحاسبي", "القيمة بالريال اليمني", "القيمة بالريال السعودي")
                    val combinedList = mutableListOf<List<String>>()
                    
                    val sYer = filteredInvoices.sumOf { it.totalAmountYer }
                    val sSar = filteredInvoices.sumOf { it.totalAmountSar }
                    val pYer = filteredPurchases.sumOf { it.totalAmountYer }
                    val pSar = filteredPurchases.sumOf { it.totalAmountSar }
                    val exYer = filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
                    val exSar = filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
                    
                    combinedList.add(listOf(pValDetail, "إجمالي الإيرادات والمبيعات", Helpers.formatWithCurrency(sYer, "ريال يمني"), Helpers.formatWithCurrency(sSar, "ريال سعودي")))
                    combinedList.add(listOf(pValDetail, "تكلفة السلع والمشتريات", Helpers.formatWithCurrency(pYer, "ريال يمني"), Helpers.formatWithCurrency(pSar, "ريال سعودي")))
                    combinedList.add(listOf(pValDetail, "إجمالي المصاريف العامة", Helpers.formatWithCurrency(exYer, "ريال يمني"), Helpers.formatWithCurrency(exSar, "ريال سعودي")))
                    
                    rows = combinedList

                    val profitYer = sYer - pYer - exYer - filteredTaxHistory.sumOf { it.taxAmount } - filteredTaxHistory.sumOf { it.stallOutflow } - filteredTaxHistory.sumOf { it.laborOutflow }
                    val profitSar = sSar - pSar - exSar

                    totals = buildMap {
                        if (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            val keyYer = if (profitYer >= 0) "صافي الربح بالريال اليمني" else "صافي الخسارة بالريال اليمني"
                            put(keyYer, Helpers.formatWithCurrency(profitYer, "ريال يمني"))
                        }
                        if (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً") {
                            val keySar = if (profitSar >= 0) "صافي الربح بالريال السعودي" else "صافي الخسارة بالريال السعودي"
                            put(keySar, Helpers.formatWithCurrency(profitSar, "ريال سعودي"))
                        }
                    }
                }
                "قسم الأرشيف الكلي للسجلات" -> {
                    val matches = mutableListOf<List<String>>()

                    if (selectedSubReport == "الكل" || selectedSubReport == "المبيعات") {
                        invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }.forEach { inv ->
                            if (inv.totalAmountYer > 0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                matches.add(listOf(inv.date, "مبيعات", Helpers.formatWithCurrency(inv.totalAmountYer, "ريال يمني"), "ريال يمني", "فاتورة مبيعات رقم ${inv.id} للعميل ${inv.customerName}"))
                            }
                            if (inv.totalAmountSar > 0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                matches.add(listOf(inv.date, "مبيعات", Helpers.formatWithCurrency(inv.totalAmountSar, "ريال سعودي"), "ريال سعودي", "فاتورة مبيعات رقم ${inv.id} للعميل ${inv.customerName}"))
                            }
                        }
                    }
                    if (selectedSubReport == "الكل" || selectedSubReport == "المشتريات") {
                        purchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) }.forEach { pur ->
                            val supName = suppliers.find { it.id == pur.supplierId }?.name ?: "غير محدد"
                            if (pur.totalAmountYer > 0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                matches.add(listOf(pur.date, "مشتريات", Helpers.formatWithCurrency(pur.totalAmountYer, "ريال يمني"), "ريال يمني", "فاتورة توريد وشراء للمورد $supName"))
                            }
                            if (pur.totalAmountSar > 0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                matches.add(listOf(pur.date, "مشتريات", Helpers.formatWithCurrency(pur.totalAmountSar, "ريال سعودي"), "ريال سعودي", "فاتورة توريد وشراء للمورد $supName"))
                            }
                        }
                    }
                    if (selectedSubReport == "الكل" || selectedSubReport == "المصروفات") {
                        expenses.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency ?: "ريال يمني") }.forEach { exp ->
                            matches.add(listOf(exp.date, "مصروفات", Helpers.formatWithCurrency(exp.amount, exp.currency ?: "ريال يمني"), exp.currency ?: "ريال يمني", exp.notes))
                        }
                    }
                    if (selectedSubReport == "الكل" || selectedSubReport == "الحوالات") {
                        transfers.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency) }.forEach { tr ->
                            matches.add(listOf(tr.date, "حوالة مالية", Helpers.formatWithCurrency(tr.amount, tr.currency), tr.currency, "حوالة صادرة من ${tr.sender} إلى ${tr.receiver}"))
                        }
                    }
                    if (selectedSubReport == "الكل" || selectedSubReport == "الديون") {
                        invoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && (it.debtAmountYer > 0 || it.debtAmountSar > 0) }.forEach { inv ->
                            if (inv.debtAmountYer > 0 && (selectedCurrency == "الريال اليمني" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                matches.add(listOf(inv.date, "دين عميل", Helpers.formatWithCurrency(inv.debtAmountYer, "ريال يمني"), "ريال يمني", "دين متبقي من فاتورة مبيعات رقم ${inv.id} للعميل ${inv.customerName}"))
                            }
                            if (inv.debtAmountSar > 0 && (selectedCurrency == "الريال السعودي" || selectedCurrency == "الريال اليمني والريال السعودي معاً")) {
                                matches.add(listOf(inv.date, "دين عميل", Helpers.formatWithCurrency(inv.debtAmountSar, "ريال سعودي"), "ريال سعودي", "دين متبقي من فاتورة مبيعات رقم ${inv.id} للعميل ${inv.customerName}"))
                            }
                        }
                    }
                    if (selectedSubReport == "الكل" || selectedSubReport == "التحصيلات") {
                        customerPayments.filter { Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) && matchesCurrency(it.currency ?: "ريال يمني") }.forEach { pay ->
                            val custName = customers.find { it.id == pay.customerId }?.name ?: "عميل مجهول"
                            matches.add(listOf(pay.date, "تحصيل دفعة", Helpers.formatWithCurrency(pay.amount, pay.currency ?: "ريال يمني"), pay.currency ?: "ريال يمني", "سداد دفعة من العميل $custName: ${pay.notes ?: "سند تحصيل"}"))
                        }
                    }

                    val sortedMatches = matches.sortedBy { it[0] }
                    headers = listOf("التاريخ", "نوع السجل", "المبلغ والعملة", "العملة", "تفاصيل البند والملاحظات")
                    rows = sortedMatches
                    totals = mapOf("إجمالي العمليات المجرودة بالأرشيف" to "${matches.size} عملية مؤرشفة")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        StatementData(headers, rows, totals)
    }

    fun triggerExport(shareToWhatsApp: Boolean) {
        val bAppName = com.example.util.BrandingManager.resolvedAgencyName()
        
        var title = "كشف حساب: ${selectedSection}${if (selectedSubReport.isNotEmpty()) " - $selectedSubReport" else ""}"
        var customDate = pValDetail
        var reportPeriod = pLabel

        if (selectedSection == "قسم فاتورة ومبيعات جديدة") {
            val tPeriodStr = when (selectedPeriodStr) {
                "يومي" -> "اليومي"
                "أسبوعي" -> "الأسبوعي"
                "شهري" -> "الشهري"
                "سنوي" -> "السنوي"
                "تقويم مخصص" -> "الخاص بالفترة المحدد ($customPeriodValue)"
                else -> "الدوري"
            }
            title = "تقرير المبيعات $tPeriodStr $bAppName"
            customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
            reportPeriod = if (selectedPeriodStr == "تقويم مخصص") "$customType : $customPeriodValue" else selectedPeriodStr
        } else if (selectedSection == "قسم إدارة مخزون القات") {
            if (selectedSubReport == "تقرير حركة المخزون والمبيعات بالتفصيل") {
                val tPeriodStr = when (selectedPeriodStr) {
                    "يومي" -> "اليومي"
                    "أسبوعي" -> "الأسبوعي (${Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue)})"
                    "شهري" -> "الشهري"
                    "سنوي" -> "السنوي"
                    "تقويم مخصص" -> "الخاص بالفترة المحدد ($customPeriodValue)"
                    else -> "الدوري"
                }
                title = "التقرير $tPeriodStr لحركة مخزن $bAppName"
                customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
                reportPeriod = if (selectedPeriodStr == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue) else if (selectedPeriodStr == "تقويم مخصص") "$customPeriodType : $customPeriodValue" else selectedPeriodStr
            } else if (selectedSubReport == "تقرير جرد وتقييم المخزون الدوري") {
                title = "كشف تقييم وجرد المخزون الدوري للمبيعات - $bAppName"
                customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
                reportPeriod = if (selectedPeriodStr == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue) else if (selectedPeriodStr == "تقويم مخصص") "$customPeriodType : $customPeriodValue" else selectedPeriodStr
            }
        } else if (selectedSection == "قسم كشف الموردين والشراء") {
            if (selectedSubReport == "تقارير المشتريات والتوريد للمفرش") {
                val tPrefix = when (selectedPeriodStr) {
                    "يومي" -> "تقرير يومي"
                    "أسبوعي" -> "تقرير أسبوعي"
                    "شهري" -> "تقرير شهري"
                    "سنوي" -> "تقرير سنوي"
                    "تقويم مخصص" -> "التقرير المخصص ($customPeriodValue)"
                    else -> "تقرير دوري"
                }
                title = "$tPrefix مشتريات وتوريد $bAppName التفصيلي"
                customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
                reportPeriod = if (selectedPeriodStr == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue) else selectedPeriodStr
            } else if (selectedSubReport == "كشف حساب المورد التفصيلي" && selectedSupplier != null) {
                val sup = selectedSupplier!!
                val tPrefix = when (selectedPeriodStr) {
                    "يومي" -> "تقرير يومي"
                    "أسبوعي" -> "تقرير أسبوعي"
                    "شهري" -> "تقرير شهري"
                    "سنوي" -> "تقرير سنوي"
                    "تقويم مخصص" -> "التقرير المخصص ($customPeriodValue)"
                    else -> "تقرير دوري"
                }
                title = "$tPrefix كشف حساب المورد التفصيلي: ${sup.name}" + if (sup.region.isNotEmpty()) " (${sup.region})" else ""
                customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
                reportPeriod = if (selectedPeriodStr == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue) else selectedPeriodStr
            }
        } else if (selectedSection == "قسم دليل وحسابات العملاء") {
            if (selectedSubReport == "كشف حساب وتفاصيل العميل" && selectedCustomer != null) {
                val cust = selectedCustomer!!
                val tPrefix = when (selectedPeriodStr) {
                    "يومي" -> "تقرير يومي"
                    "أسبوعي" -> "تقرير أسبوعي"
                    "شهري" -> "تقرير شهري"
                    "سنوي" -> "تقرير سنوي"
                    "تقويم مخصص" -> "التقرير المخصص ($customPeriodValue)"
                    else -> "تقرير دوري"
                }
                title = "$tPrefix كشف حساب وتفاصيل العميل: ${cust.name}" + if (cust.phone.isNotEmpty()) " (${cust.phone})" else ""
                customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
                reportPeriod = if (selectedPeriodStr == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue) else selectedPeriodStr
            } else if (selectedSubReport == "تقرير حسابات الديون والتحصيلات") {
                val tPrefix = when (selectedPeriodStr) {
                    "يومي" -> "تقرير يومي"
                    "أسبوعي" -> "تقرير أسبوعي"
                    "شهري" -> "تقرير شهري"
                    "سنوي" -> "تقرير سنوي"
                    "تقويم مخصص" -> "التقرير المخصص ($customPeriodValue)"
                    else -> "تقرير دوري"
                }
                title = "$tPrefix ديون وتحصيلات عملاء $bAppName"
                customDate = if (selectedPeriodStr == "تقويم مخصص") customPeriodValue else Helpers.getCurrentDateTime()
                reportPeriod = if (selectedPeriodStr == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue) else selectedPeriodStr
            }
        }

        val selectedRecObj = selectedTransferRecipientId?.let { rid -> allTransferRecipients.find { it.id == rid } }
        val receiverNameParam = when {
            selectedSection == "قسم إدارة مخزون القات" && selectedSubReport == "تقرير حركة المخزون والمبيعات بالتفصيل" -> selectedQatReceiver
            else -> null
        }
        val transferRecInfoParam = if (selectedSection == "قسم سجل الحوالات والتحويلات" && selectedRecObj != null) {
            buildString {
                append("المستلم: ${selectedRecObj.name} (#${selectedRecObj.id})")
                if (selectedRecObj.phone.isNotBlank()) append(" • الهاتف: ${selectedRecObj.phone}")
                if (selectedRecObj.company.isNotBlank()) append(" • الصرافة: ${selectedRecObj.company}")
                if (!selectedRecObj.isActive) append(" • الحالة: موقوف (سجل تاريخي)")
            }
        } else null

        Helpers.generatePdfAndShare(
            context = context,
            title = title,
            headers = reportData.headers,
            rows = reportData.rows,
            totals = reportData.totals,
            customDate = customDate,
            reportPeriod = reportPeriod,
            shareToWhatsApp = shareToWhatsApp,
            receiverName = receiverNameParam,
            recipientInfo = transferRecInfoParam
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AgencyAppBarTitle("كشف حساب وحركة السجلات") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PalIconUnified)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
            )
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Dropdown 1: Main Section
            StatementDropdown(
                label = "القسم المطلوب لعمل كشف الحساب:",
                selectedOption = selectedSection,
                options = sectionsList,
                optionToString = { it },
                onOptionSelected = { selectedSection = it }
            )

            // Dropdown 2: Sub-report (visible if available)
            if (subReportsList.isNotEmpty()) {
                StatementDropdown(
                    label = "نوع التقرير الفرعي:",
                    selectedOption = selectedSubReport,
                    options = subReportsList,
                    optionToString = { it },
                    onOptionSelected = { selectedSubReport = it }
                )
            }

            // Customer selection if selectedSubReport is "كشف حساب وتفاصيل العميل"
            if (selectedSection == "قسم دليل وحسابات العملاء" && selectedSubReport == "كشف حساب وتفاصيل العميل" && customers.isNotEmpty()) {
                StatementDropdown(
                    label = "العميل المعني:",
                    selectedOption = selectedCustomer?.name ?: "غير محدد",
                    options = customers,
                    optionToString = { it.name },
                    onOptionSelected = { selectedCustomer = it }
                )
            }

            // Supplier selection if selectedSubReport is "كشف حساب المورد التفصيلي"
            if (selectedSection == "قسم كشف الموردين والشراء" && selectedSubReport == "كشف حساب المورد التفصيلي" && suppliers.isNotEmpty()) {
                StatementDropdown(
                    label = "المورد المعني:",
                    selectedOption = selectedSupplier?.name ?: "غير محدد",
                    options = suppliers,
                    optionToString = { it.name },
                    onOptionSelected = { selectedSupplier = it }
                )
            }

            // Receiver selection if Qat inventory detailed report is chosen
            if (selectedSection == "قسم إدارة مخزون القات" && selectedSubReport == "تقرير حركة المخزون والمبيعات بالتفصيل") {
                val registeredReceivers = remember(allWeights) {
                    listOf(
                        "الكل",
                        "عرض تقرير جميع المستلمين (بدون تصفية)",
                        "أصناف بدون مستلم"
                    ) + allWeights.map { weight ->
                        "${weight.receiverName} (${weight.date} | ${weight.weight} كجم) [ID: ${weight.id}]"
                    }
                }
                StatementDropdown(
                    label = "مستلم مخزون القات:",
                    selectedOption = selectedQatReceiver,
                    options = registeredReceivers,
                    optionToString = { it },
                    onOptionSelected = { selectedQatReceiver = it }
                )
            }

            // Transfer Recipient selection if Transfers ledger section is chosen (strictly keyed by recipientId)
            if (selectedSection == "قسم سجل الحوالات والتحويلات") {
                val recipientOptions: List<TransferRecipientEntity?> = remember(allTransferRecipients) {
                    listOf(null) + allTransferRecipients
                }
                val selectedRecLabel = selectedTransferRecipientId?.let { rid ->
                    allTransferRecipients.find { it.id == rid }?.let { r ->
                        "${r.name} (#${r.id})${if (!r.isActive) " [موقوف]" else ""}"
                    }
                } ?: "جميع المستلمين (${allTransferRecipients.size})"
                StatementDropdown(
                    label = "تصفية حسب المستلم / جهة التحويل (بالمعرف المستقل):",
                    selectedOption = selectedRecLabel,
                    options = recipientOptions,
                    optionToString = { rec ->
                        if (rec == null) "جميع المستلمين (${allTransferRecipients.size})"
                        else "${rec.name} (#${rec.id})${if (rec.phone.isNotBlank()) " • ${rec.phone}" else ""}${if (!rec.isActive) " [موقوف]" else ""}"
                    },
                    onOptionSelected = { rec ->
                        selectedTransferRecipientId = rec?.id
                    }
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Section 3: Time period selection tabs
            Text("الفترة الزمنية والتقويم:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Surface(
                color = PalBlackNormal,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, PalBorderColor)
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedPeriodIndex,
                    containerColor = Color.Transparent,
                    contentColor = PalIconUnified,
                    edgePadding = 8.dp
                ) {
                    Tab(selected = selectedPeriodIndex == 0, onClick = { selectedPeriodStr = "يومي" }, text = { Text("يومي", fontSize = 12.sp, color = if (selectedPeriodIndex == 0) PalIconUnified else PalWhiteMuted) })
                    Tab(selected = selectedPeriodIndex == 1, onClick = { selectedPeriodStr = "أسبوعي" }, text = { Text("أسبوعي", fontSize = 12.sp, color = if (selectedPeriodIndex == 1) PalIconUnified else PalWhiteMuted) })
                    Tab(selected = selectedPeriodIndex == 2, onClick = { selectedPeriodStr = "شهري" }, text = { Text("شهري", fontSize = 12.sp, color = if (selectedPeriodIndex == 2) PalIconUnified else PalWhiteMuted) })
                    Tab(selected = selectedPeriodIndex == 3, onClick = { selectedPeriodStr = "سنوي" }, text = { Text("سنوي", fontSize = 12.sp, color = if (selectedPeriodIndex == 3) PalIconUnified else PalWhiteMuted) })
                    Tab(selected = selectedPeriodIndex == 4, onClick = { selectedPeriodStr = "تقويم مخصص" }, text = { Text("تقويم مخصص", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selectedPeriodIndex == 4) PalIconUnified else PalWhiteMuted) })
                }
            }

            // Weekly picker ui if weekly is selected
            if (selectedPeriodIndex == 1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(PalBlackNormal, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "الشهر والسنة للأسبوع المحاسبي:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(
                            onClick = {
                                try {
                                    val parts = accountingWeekYearMonth.split("-")
                                    var year = parts[0].toInt()
                                    var month = parts[1].toInt()
                                    month -= 1
                                    if (month < 1) {
                                        month = 12
                                        year -= 1
                                    }
                                    accountingWeekYearMonth = String.format("%04d-%02d", year, month)
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = PalIconUnified)
                        }

                        val mParts = accountingWeekYearMonth.split("-")
                        val yearStr = mParts.getOrNull(0) ?: ""
                        val monthNum = mParts.getOrNull(1)?.toIntOrNull() ?: 1
                        val arabicMonths = arrayOf(
                            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                        )
                        val monthName = arabicMonths.getOrElse(monthNum - 1) { "يناير" }

                        Text(
                            text = "$monthName $yearStr",
                            color = PalWhitePure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                try {
                                    val parts = accountingWeekYearMonth.split("-")
                                    var year = parts[0].toInt()
                                    var month = parts[1].toInt()
                                    month += 1
                                    if (month > 12) {
                                        month = 1
                                        year += 1
                                    }
                                    accountingWeekYearMonth = String.format("%04d-%02d", year, month)
                                } catch (e: Exception) {}
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر التالي", tint = PalIconUnified)
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "اختر الأسبوع من هذا الشهر:",
                        color = PalWhiteMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        (1..5).forEach { w ->
                            val isSel = accountingWeekNum == w
                            val weekLabels = arrayOf("", "الأول", "الثاني", "الثالث", "الرابع", "الخامس")
                            val label = "الأسبوع ${weekLabels.getOrElse(w) { w.toString() }}"
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PalGreenNormal else PalBlackLight)
                                    .border(BorderStroke(1.dp, if (isSel) PalGreenNormal else PalBorderColor), RoundedCornerShape(8.dp))
                                    .clickable { accountingWeekNum = w }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else PalWhiteMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Custom selector if custom is selected
            if (selectedPeriodIndex == 4) {
                UnifiedPeriodSelector(
                    initialType = customPeriodType,
                    initialValue = customPeriodValue,
                    onPeriodChanged = { type, finalVal ->
                        customPeriodType = type
                        customPeriodValue = finalVal
                    }
                )
            }

            // Section 4: Currency Selection Toggle Buttons driven by central CurrencyConfig
            Text("تصفية وفلترة نوع العملة:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val statementCurrencyOptions = remember(currencyConfig) { currencyConfig.statementFilterOptions() }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                statementCurrencyOptions.forEach { curr ->
                    val isSel = selectedCurrency == curr
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) PalGreenNormal else PalBlackNormal)
                            .border(BorderStroke(1.dp, if (isSel) PalGreenNormal else PalBorderColor), RoundedCornerShape(10.dp))
                            .clickable { selectedCurrency = curr }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = curr,
                            color = if (isSel) Color.White else PalWhiteSoft,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Section 5: Real-time Statistics / Summaries Cards based on filter!
            Text("إحصائيات الإجماليات للفترة الحالية ($pLabel)", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            
            // Render specific statistics view per section
            Card(
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PalBorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (reportData.totals.isEmpty()) {
                        Text(
                            text = "لا توجد إحصائيات متوفرة لهذه التصفية.",
                            color = PalWhiteMuted,
                            fontSize = 12.sp
                        )
                    } else {
                        reportData.totals.forEach { (key, value) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "$key:",
                                    color = PalWhiteMuted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = value,
                                    color = PalSuccessNormal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Section 5b: Interactive Records Preview List (100% in sync with report)
            Text(
                text = "معاينة السجلات المشمولة بالتقرير (${reportData.rows.size})",
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )

            if (reportData.rows.isEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد عمليات مسجلة خلال الفترة والتصفية المحددة.",
                            color = PalWhiteMuted,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                reportData.rows.forEachIndexed { index, row ->
                    val isHeader = row.firstOrNull()?.startsWith("SECTION_HEADER|") == true
                    val isTotals = row.firstOrNull()?.startsWith("SECTION_TOTALS|") == true

                    if (isHeader) {
                        val headerText = row[0].removePrefix("SECTION_HEADER|")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalSoftGreenTint),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PalGoldMuted)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(headerText, color = PalGoldCalligraphy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    } else if (isTotals) {
                        val totalsText = row[0].removePrefix("SECTION_TOTALS|")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, PalSuccessNormal)
                        ) {
                            Box(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                                Text(totalsText, color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    } else {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, PalBorderColor)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "سجل رقم #${index + 1}",
                                        color = PalIconUnified,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    if (reportData.headers.isNotEmpty() && row.isNotEmpty()) {
                                        Text(
                                            text = row[0],
                                            color = PalWhiteMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                                
                                HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                                // Render all other columns
                                for (i in 1 until row.size) {
                                    if (i < row.size && i < reportData.headers.size) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = reportData.headers[i] + ":",
                                                color = PalWhiteMuted,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = row[i],
                                                color = PalWhitePure,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.End
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }

            val compileTextReport: () -> String = {
                buildString {
                    val bAppName = com.example.util.BrandingManager.resolvedAgencyName()

                    var repTitle = "كشف حساب: ${selectedSection}${if (selectedSubReport.isNotEmpty()) " - $selectedSubReport" else ""}"
                    var repPeriod = selectedPeriodStr
                    if (selectedPeriodStr == "أسبوعي") {
                        repPeriod = Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue)
                    } else if (selectedPeriodStr == "تقويم مخصص") {
                        repPeriod = "$customPeriodType : $customPeriodValue"
                    }

                    if (selectedSection == "قسم إدارة مخزون القات") {
                        if (selectedSubReport == "تقرير جرد وتقييم المخزون الدوري") {
                            repTitle = "كشف تقييم وجرد المخزون الدوري للمبيعات - $bAppName"
                        } else if (selectedSubReport == "تقرير حركة المخزون والمبيعات بالتفصيل") {
                            repTitle = "التقرير الدوري لحركة مخزن $bAppName"
                        }
                    }

                    appendLine(com.example.util.BrandingManager.buildShareHeader(repTitle))
                    appendLine("الفترة: $repPeriod")
                    appendLine("----------------------------------------")
                    
                    reportData.rows.forEachIndexed { index, row ->
                        if (row.firstOrNull()?.startsWith("SECTION_HEADER|") == true) {
                            appendLine(row[0].removePrefix("SECTION_HEADER|"))
                        } else if (row.firstOrNull()?.startsWith("SECTION_TOTALS|") == true) {
                            appendLine(row[0].removePrefix("SECTION_TOTALS|"))
                        } else {
                            val itemName = row.getOrNull(1) ?: "صنف #${index + 1}"
                            val itemNum = row.getOrNull(2) ?: "${index + 1}"
                            appendLine("- $itemName")
                            appendLine(" • الرقم: $itemNum")
                            for (i in row.indices) {
                                if (i != 1 && i != 2 && i < reportData.headers.size) {
                                    appendLine(" • ${reportData.headers[i]}: ${row[i]}")
                                }
                            }
                        }
                        appendLine("----------------------------------------")
                    }
                    
                    if (reportData.totals.isNotEmpty()) {
                        appendLine("إجمالي الإحصائيات والتسويات:")
                        reportData.totals.forEach { (k, v) ->
                            appendLine(" • $k: $v")
                        }
                        appendLine("----------------------------------------")
                    }
                    append(com.example.util.BrandingManager.buildShareFooter())
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Section 6: Action Buttons (Print, Share to WhatsApp, Thermal Print)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Button 1: Export PDF & Print
                Button(
                    onClick = { triggerExport(shareToWhatsApp = false) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("طباعة PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }

                // Button 2: Share to WhatsApp
                OutlinedButton(
                    onClick = { triggerExport(shareToWhatsApp = true) },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("واتساب", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PalWhitePure)
                }

                // Button 3: Thermal Print
                OutlinedButton(
                    onClick = {
                        val paperSize = BluetoothPrinterManager.getInstance(context).currentPrinterSize
                        val reportText = if (selectedSection == "قسم سجل الحوالات والتحويلات") {
                            val matchingTransfers = transfers.filter {
                                Helpers.matchesPeriod(it.date, selectedPeriodStr, customPeriodValue, customType) &&
                                    matchesCurrency(it.currency) &&
                                    (selectedTransferRecipientId == null || it.recipientId == selectedTransferRecipientId)
                            }.sortedBy { it.date }
                            val recEntity = selectedTransferRecipientId?.let { rid -> allTransferRecipients.find { it.id == rid } }
                            val periodDisplay = when (selectedPeriodStr) {
                                "أسبوعي" -> Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue)
                                "تقويم مخصص" -> Helpers.formatCustomPeriodToArabic(customPeriodType, customPeriodValue)
                                else -> selectedPeriodStr
                            }
                            if (recEntity != null) {
                                ThermalReceiptTemplates.buildRecipientTransfersReportReceipt(
                                    recipient = recEntity,
                                    transfers = matchingTransfers,
                                    periodLabel = selectedPeriodStr,
                                    dateRangeLabel = periodDisplay,
                                    currencyConfig = currencyConfig,
                                    paperWidthMm = paperSize
                                )
                            } else {
                                ThermalReceiptTemplates.buildTransfersLedgerSummaryReceipt(
                                    transfers = matchingTransfers,
                                    periodLabel = periodDisplay,
                                    recipientLabel = "جميع المستلمين",
                                    currencyConfig = currencyConfig,
                                    paperWidthMm = paperSize
                                )
                            }
                        } else {
                            compileTextReport()
                        }
                        bluetoothPrintText = reportText
                        showBluetoothPrintTrigger = true
                    },
                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint),
                    border = BorderStroke(1.dp, PalGoldMuted),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Print, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("حرارية", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = PalWhitePure)
                }
            }
        }
    }

    if (showBluetoothPrintTrigger) {
        com.example.ui.components.BluetoothPrintDialog(
            receiptText = bluetoothPrintText,
            onDismiss = { showBluetoothPrintTrigger = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> StatementDropdown(
    label: String,
    selectedOption: String,
    options: List<T>,
    optionToString: (T) -> String,
    onOptionSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(text = label, color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(PalBlackNormal, RoundedCornerShape(12.dp))
                .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(12.dp))
                .clickable { expanded = true }
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = selectedOption, color = PalWhitePure, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Icon(
                    if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = PalIconUnified
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .background(PalBlackNormal)
                    .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(12.dp))
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(optionToString(option), color = PalWhitePure) },
                        onClick = {
                            onOptionSelected(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
