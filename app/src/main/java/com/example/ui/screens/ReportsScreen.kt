package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.example.ui.AppViewModel
import com.example.ui.components.LedgerSectionHeader
import com.example.ui.components.ProLockedScreenPlaceholder
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.theme.*
import com.example.util.BrandingManager
import com.example.util.Helpers
import com.example.util.MoneyMath
import androidx.compose.ui.platform.testTag
import com.qatledger.licensing.LicenseFeature

data class TimelineOperation(
    val type: String,
    val docId: String,
    val date: String,
    val title: String,
    val details: String,
    val amount: Double,
    val isInflow: Boolean,
    val currency: String = "ريال يمني"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(viewModel: AppViewModel) {
    val licenseState by viewModel.licenseState.collectAsState()
    if (!licenseState.hasFeature(LicenseFeature.REPORTS)) {
        ProLockedScreenPlaceholder(feature = LicenseFeature.REPORTS, viewModel = viewModel)
        return
    }
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val taxHistory by viewModel.allTaxHistory.collectAsState()
    val purchases by viewModel.allPurchases.collectAsState()
    val transfers by viewModel.allTransfers.collectAsState()
    val allPurchaseItems by viewModel.allPurchaseItems.collectAsState()
    val allInvoiceItems by viewModel.allInvoiceItems.collectAsState()
    val rateVal by viewModel.sarToYerExchangeRate.collectAsState()
    val customers by viewModel.allCustomers.collectAsState()
    val suppliers by viewModel.allSuppliers.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val customerPayments by viewModel.allPayments.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }

    var selectedPeriod by remember { mutableStateOf(0) } // 0: Daily, 1: Weekly, 2: Monthly, 3: Yearly, 4: Archive (All Time), 5: Custom
    var activeCurrencyTab by remember { mutableStateOf(0) } // 0: ريال يمني, 1: ريال سعودي
    var customReportsType by remember { mutableStateOf("يوم") }
    var customReportsValue by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var reportsReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }

    var reportsWeekFilterYearMonth by remember {
        val todayStr = Helpers.getCurrentDate()
        mutableStateOf(todayStr.substring(0, 7))
    }
    var reportsWeekFilterNum by remember {
        mutableStateOf(Helpers.getCurrentWeekOfMonth())
    }

    LaunchedEffect(selectedPeriod, reportsWeekFilterYearMonth, reportsWeekFilterNum) {
        if (selectedPeriod == 1) {
            customReportsValue = "WEEK_FILTER|$reportsWeekFilterYearMonth|$reportsWeekFilterNum"
            reportsReportDate = Helpers.formatCustomPeriodToArabic("أسبوعي", customReportsValue)
        } else if (selectedPeriod == 5) {
            reportsReportDate = Helpers.formatCustomPeriodToArabic(customReportsType, customReportsValue)
        } else {
            reportsReportDate = Helpers.getCurrentDateTime()
        }
    }

    val todayStr = Helpers.getCurrentDate()
    val periodStr = when (selectedPeriod) {
        0 -> "يومي"
        1 -> "أسبوعي"
        2 -> "شهري"
        3 -> "سنوي"
        5 -> "تقويم مخصص"
        else -> "الكل"
    }
    val customType = when (customReportsType) {
        "يوم" -> "date"
        "شهر" -> "month"
        "سنة" -> "year"
        else -> "date"
    }

    val dateFilter = when (selectedPeriod) {
        0 -> Helpers.formatDateTo29June2026(todayStr)
        1 -> if (customReportsValue.startsWith("WEEK_FILTER|")) Helpers.formatCustomPeriodToArabic("أسبوعي", customReportsValue) else "7 أيام الماضية"
        2 -> Helpers.formatCustomPeriodToArabic("شهر", todayStr.substring(0, 7))
        3 -> Helpers.formatCustomPeriodToArabic("سنة", todayStr.substring(0, 4))
        5 -> Helpers.formatCustomPeriodToArabic(customReportsType, customReportsValue)
        else -> "جميع الأوقات"
    }

    val filteredInvoices = invoices.filter { Helpers.matchesPeriod(it.date, periodStr, customReportsValue, customType) }
    val filteredExpenses = expenses.filter { Helpers.matchesPeriod(it.date, periodStr, customReportsValue, customType) }
    val filteredPurchases = purchases.filter { Helpers.matchesPeriod(it.date, periodStr, customReportsValue, customType) }
    val filteredTaxHistory = taxHistory.filter { Helpers.matchesPeriod(it.date, periodStr, customReportsValue, customType) }
    val filteredTransfers = transfers.filter { Helpers.matchesPeriod(it.date, periodStr, customReportsValue, customType) }

    val totalSalesYer = filteredInvoices.sumOf {
        if (it.totalAmountYer > 0.0) it.totalAmountYer
        else if (it.currency.contains("يمني")) it.totalAmount
        else 0.0
    }
    val totalSalesSar = filteredInvoices.sumOf {
        if (it.totalAmountSar > 0.0) it.totalAmountSar
        else if (it.currency.contains("سعودي")) it.totalAmount
        else 0.0
    }

    val totalExpensesYer = filteredExpenses.filter { it.currency.contains("يمني") }.sumOf { it.amount }
    val totalExpensesSar = filteredExpenses.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

    val totalPurchasesYer = filteredPurchases.sumOf {
        if (it.totalAmountYer > 0.0) it.totalAmountYer
        else if (it.currency.contains("يمني")) it.totalAmount
        else 0.0
    }
    val totalPurchasesSar = filteredPurchases.sumOf {
        if (it.totalAmountSar > 0.0) it.totalAmountSar
        else if (it.currency.contains("سعودي")) it.totalAmount
        else 0.0
    }

    val totalPurchasesPaidYer = filteredPurchases.sumOf {
        if (it.totalAmountYer > 0.0) {
            if (it.paidAmountYer > it.totalAmountYer) it.totalAmountYer else it.paidAmountYer
        } else if (it.currency.contains("يمني")) {
            if (it.paidAmount > it.totalAmount) it.totalAmount else it.paidAmount
        } else 0.0
    }
    val totalPurchasesPaidSar = filteredPurchases.sumOf {
        if (it.totalAmountSar > 0.0) {
            if (it.paidAmountSar > it.totalAmountSar) it.totalAmountSar else it.paidAmountSar
        } else if (it.currency.contains("سعودي")) {
            if (it.paidAmount > it.totalAmount) it.totalAmount else it.paidAmount
        } else 0.0
    }

    // Independent overpayment surpluses in favor of the agency/agency credits
    val totalSupplierSurplusYer = filteredPurchases.sumOf {
        if (it.totalAmountYer > 0.0) {
            if (it.paidAmountYer > it.totalAmountYer) (it.paidAmountYer - it.totalAmountYer) else 0.0
        } else if (it.currency.contains("يمني")) {
            if (it.paidAmount > it.totalAmount) (it.paidAmount - it.totalAmount) else 0.0
        } else 0.0
    }
    val totalSupplierSurplusSar = filteredPurchases.sumOf {
        if (it.totalAmountSar > 0.0) {
            if (it.paidAmountSar > it.totalAmountSar) (it.paidAmountSar - it.totalAmountSar) else 0.0
        } else if (it.currency.contains("سعودي")) {
            if (it.paidAmount > it.totalAmount) (it.paidAmount - it.totalAmount) else 0.0
        } else 0.0
    }

    val totalTaxAmountYer = filteredTaxHistory.sumOf { it.taxAmount }
    val totalStallOutflowYer = filteredTaxHistory.sumOf { it.stallOutflow }
    val totalLaborOutflowYer = filteredTaxHistory.sumOf { it.laborOutflow }

    val totalTransferredYer = filteredTransfers.filter { it.currency.contains("يمني") }.sumOf { it.amount }
    val totalTransferredSar = filteredTransfers.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

    val filteredCustomerPayments = customerPayments.filter { Helpers.matchesPeriod(it.date, periodStr, customReportsValue, customType) }
    val totalCollectionsYer = filteredCustomerPayments.filter { it.currency.contains("يمني") }.sumOf { it.amount }
    val totalCollectionsSar = filteredCustomerPayments.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

    val totalCashReceivedYer = filteredInvoices.sumOf {
        if (it.totalAmountYer > 0.0) {
            if (it.paymentMethodYer == "نقداً" || it.paymentMethodYer == "دفع جزئي") it.paidAmountYer else 0.0
        } else {
            if (it.currency.contains("يمني") && (it.paymentMethod == "نقداً" || it.paymentMethod == "دفع جزئي")) it.paidAmount else 0.0
        }
    }
    val totalCashReceivedSar = filteredInvoices.sumOf {
        if (it.totalAmountSar > 0.0) {
            if (it.paymentMethodSar == "نقداً" || it.paymentMethodSar == "دفع جزئي") it.paidAmountSar else 0.0
        } else {
            if (it.currency.contains("سعودي") && (it.paymentMethod == "نقداً" || it.paymentMethod == "دفع جزئي")) it.paidAmount else 0.0
        }
    }

    val totalTransferReceivedYer = filteredInvoices.sumOf {
        if (it.totalAmountYer > 0.0) {
            if (it.paymentMethodYer == "تحويل") it.paidAmountYer else 0.0
        } else {
            if (it.currency.contains("يمني") && it.paymentMethod == "تحويل") it.paidAmount else 0.0
        }
    }
    val totalTransferReceivedSar = filteredInvoices.sumOf {
        if (it.totalAmountSar > 0.0) {
            if (it.paymentMethodSar == "تحويل") it.paidAmountSar else 0.0
        } else {
            if (it.currency.contains("سعودي") && it.paymentMethod == "تحويل") it.paidAmount else 0.0
        }
    }

    val totalDepositReceivedYer = filteredInvoices.sumOf {
        if (it.totalAmountYer > 0.0) {
            if (it.paymentMethodYer == "إيداع") it.paidAmountYer else 0.0
        } else {
            if (it.currency.contains("يمني") && it.paymentMethod == "إيداع") it.paidAmount else 0.0
        }
    }
    val totalDepositReceivedSar = filteredInvoices.sumOf {
        if (it.totalAmountSar > 0.0) {
            if (it.paymentMethodSar == "إيداع") it.paidAmountSar else 0.0
        } else {
            if (it.currency.contains("سعودي") && it.paymentMethod == "إيداع") it.paidAmount else 0.0
        }
    }

    val totalCreditSalesYer = filteredInvoices.sumOf {
        if (it.totalAmountYer > 0.0) {
            if (it.paymentMethodYer == "آجل") it.totalAmountYer else 0.0
        } else {
            if (it.currency.contains("يمني") && it.paymentMethod == "آجل") it.totalAmount else 0.0
        }
    }
    val totalCreditSalesSar = filteredInvoices.sumOf {
        if (it.totalAmountSar > 0.0) {
            if (it.paymentMethodSar == "آجل") it.totalAmountSar else 0.0
        } else {
            if (it.currency.contains("سعودي") && it.paymentMethod == "آجل") it.totalAmount else 0.0
        }
    }

    val totalNewDebtsYer = filteredInvoices.sumOf {
        if (it.totalAmountYer > 0.0) it.debtAmountYer
        else if (it.currency.contains("يمني")) it.debtAmount
        else 0.0
    }
    val totalNewDebtsSar = filteredInvoices.sumOf {
        if (it.totalAmountSar > 0.0) it.debtAmountSar
        else if (it.currency.contains("سعودي")) it.debtAmount
        else 0.0
    }

    val salesInflowCashYer = totalCashReceivedYer + totalTransferReceivedYer + totalDepositReceivedYer
    val salesInflowCashSar = totalCashReceivedSar + totalTransferReceivedSar + totalDepositReceivedSar

    val netActualCashYer = (salesInflowCashYer + totalCollectionsYer) - (totalPurchasesPaidYer + totalExpensesYer + totalTaxAmountYer + totalStallOutflowYer + totalLaborOutflowYer + totalTransferredYer)
    val netActualCashSar = (salesInflowCashSar + totalCollectionsSar) - (totalPurchasesPaidSar + totalExpensesSar + totalTransferredSar)

    val netActualProfitYer = (salesInflowCashYer + totalCollectionsYer) - (totalPurchasesPaidYer + totalExpensesYer + totalTaxAmountYer + totalStallOutflowYer + totalLaborOutflowYer)
    val netActualProfitSar = (salesInflowCashSar + totalCollectionsSar) - (totalPurchasesPaidSar + totalExpensesSar)

    val timelineOps = remember(filteredInvoices, filteredExpenses, filteredPurchases, filteredTransfers, filteredTaxHistory, allPurchaseItems, allInvoiceItems, filteredCustomerPayments, customers) {
        val list = mutableListOf<TimelineOperation>()
        
        filteredInvoices.forEach { inv ->
            val items = allInvoiceItems.filter { it.invoiceId == inv.id }
            val itemsDetail = if (items.isNotEmpty()) {
                "الأصناف: " + items.joinToString(", ") { "${it.itemName} (${Helpers.formatQuantity(it.quantity)} حبة x ${Helpers.formatMoney(it.unitPrice)})" }
            } else ""

            if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                list.add(
                    TimelineOperation(
                        type = "مبيعات",
                        docId = "فاتورة مبيعات #${inv.id}",
                        date = inv.date,
                        title = "فاتورة بيع (يمني) للزبون: ${inv.customerName}",
                        details = "طريقة الدفع: ${inv.paymentMethodYer} • المدفوع: ${Helpers.formatMoney(inv.paidAmountYer)} • المتبقي: ${Helpers.formatMoney(inv.debtAmountYer)}" + if (itemsDetail.isNotEmpty()) " • $itemsDetail" else "",
                        amount = inv.totalAmountYer,
                        isInflow = true,
                        currency = "ريال يمني"
                    )
                )
                list.add(
                    TimelineOperation(
                        type = "مبيعات",
                        docId = "فاتورة مبيعات #${inv.id}",
                        date = inv.date,
                        title = "فاتورة بيع (سعودي) للزبون: ${inv.customerName}",
                        details = "طريقة الدفع: ${inv.paymentMethodSar} • المدفوع: ${Helpers.formatMoney(inv.paidAmountSar)} • المتبقي: ${Helpers.formatMoney(inv.debtAmountSar)}" + if (itemsDetail.isNotEmpty()) " • $itemsDetail" else "",
                        amount = inv.totalAmountSar,
                        isInflow = true,
                        currency = "ريال سعودي"
                    )
                )
            } else {
                val dispTotal = if (inv.totalAmountSar > 0.0) inv.totalAmountSar else if (inv.totalAmountYer > 0.0) inv.totalAmountYer else inv.totalAmount
                val dispPaid = if (inv.totalAmountSar > 0.0) inv.paidAmountSar else if (inv.totalAmountYer > 0.0) inv.paidAmountYer else inv.paidAmount
                val dispDebt = if (inv.totalAmountSar > 0.0) inv.debtAmountSar else if (inv.totalAmountYer > 0.0) inv.debtAmountYer else inv.debtAmount
                val dispCurr = if (inv.totalAmountSar > 0.0 || inv.currency.contains("سعودي")) "ريال سعودي" else "ريال يمني"
                val fullDetails = "طريقة الدفع: ${inv.paymentMethod} • المدفوع: ${Helpers.formatMoney(dispPaid)} • المتبقي: ${Helpers.formatMoney(dispDebt)}" + if (itemsDetail.isNotEmpty()) " • $itemsDetail" else ""

                list.add(
                    TimelineOperation(
                        type = "مبيعات",
                        docId = "فاتورة مبيعات #${inv.id}",
                        date = inv.date,
                        title = "فاتورة بيع للزبون: ${inv.customerName}",
                        details = fullDetails,
                        amount = dispTotal,
                        isInflow = true,
                        currency = dispCurr
                    )
                )
            }
        }
        
        filteredPurchases.forEach { pur ->
            val items = allPurchaseItems.filter { it.purchaseId == pur.id }
            val itemsDetail = if (items.isNotEmpty()) {
                "الأصناف: " + items.joinToString(", ") { "${it.itemName} (${Helpers.formatQuantity(it.quantity)} حبة x ${Helpers.formatMoney(it.unitPrice)})" }
            } else ""

            if (pur.totalAmountYer > 0.0 && pur.totalAmountSar > 0.0) {
                list.add(
                    TimelineOperation(
                        type = "مشتريات",
                        docId = "أمر توريد #${pur.id}",
                        date = pur.date,
                        title = "توريد بضاعة (يمني) من المورد",
                        details = "طريقة الدفع: ${pur.paymentMethod} • المدفوع: ${Helpers.formatMoney(pur.paidAmount * (pur.totalAmountYer / pur.totalAmount))} • المتبقي: ${Helpers.formatMoney(pur.debtRemaining * (pur.totalAmountYer / pur.totalAmount))}" + if (itemsDetail.isNotEmpty()) " • $itemsDetail" else "",
                        amount = pur.totalAmountYer,
                        isInflow = false,
                        currency = "ريال يمني"
                    )
                )
                list.add(
                    TimelineOperation(
                        type = "مشتريات",
                        docId = "أمر توريد #${pur.id}",
                        date = pur.date,
                        title = "توريد بضاعة (سعودي) من المورد",
                        details = "طريقة الدفع: ${pur.paymentMethod} • المدفوع: ${Helpers.formatMoney(pur.paidAmount * (pur.totalAmountSar / pur.totalAmount))} • المتبقي: ${Helpers.formatMoney(pur.debtRemaining * (pur.totalAmountSar / pur.totalAmount))}" + if (itemsDetail.isNotEmpty()) " • $itemsDetail" else "",
                        amount = pur.totalAmountSar,
                        isInflow = false,
                        currency = "ريال سعودي"
                    )
                )
            } else {
                val fullDetails = "طريقة الدفع: ${pur.paymentMethod} • المدفوع: ${Helpers.formatMoney(pur.paidAmount)} • المتبقي: ${Helpers.formatMoney(pur.debtRemaining)}" + if (itemsDetail.isNotEmpty()) " • $itemsDetail" else ""
                list.add(
                    TimelineOperation(
                        type = "مشتريات",
                        docId = "أمر توريد #${pur.id}",
                        date = pur.date,
                        title = "توريد بضاعة من المورد",
                        details = fullDetails,
                        amount = pur.totalAmount,
                        isInflow = false,
                        currency = pur.currency
                    )
                )
            }
        }
        
        filteredExpenses.forEach { exp ->
            list.add(
                TimelineOperation(
                    type = "مصروفات",
                    docId = "سند صرف #${exp.id}",
                    date = exp.date,
                    title = "مصاريف تشغيلية: ${exp.category}",
                    details = exp.notes.ifEmpty { "مصاريف عامة" },
                    amount = exp.amount,
                    isInflow = false,
                    currency = exp.currency
                )
            )
        }
        
        filteredTransfers.forEach { tr ->
            list.add(
                TimelineOperation(
                    type = "حوالة",
                    docId = "حوالة مالية #${tr.id}",
                    date = tr.date,
                    title = "تحويل مالي من [${tr.sender}] ➔ إلى [${tr.receiver}]",
                    details = "مرجع: ${tr.referenceNumber.ifEmpty { "لا يوجد" }} • البيان: ${tr.statement}",
                    amount = tr.amount,
                    isInflow = false,
                    currency = tr.currency
                )
            )
        }
        
        filteredTaxHistory.forEach { tax ->
            list.add(
                TimelineOperation(
                    type = "رسوم",
                    docId = "رسوم يومية بتاريخ ${tax.date}",
                    date = tax.date,
                    title = "سجل التكلفة والرسوم والضرائب اليومية",
                    details = "ضريبة القات: ${Helpers.formatMoney(tax.taxAmount)} • خرج المفرش: ${Helpers.formatMoney(tax.stallOutflow)} • خرج العمال: ${Helpers.formatMoney(tax.laborOutflow)}",
                    amount = tax.taxAmount + tax.stallOutflow + tax.laborOutflow,
                    isInflow = false,
                    currency = "ريال يمني"
                )
            )
        }

        filteredCustomerPayments.forEach { pay ->
            val customerName = customers.find { it.id == pay.customerId }?.name ?: "زبون غير معروف"
            list.add(
                TimelineOperation(
                    type = "سداد",
                    docId = "سند قبض #${pay.id}",
                    date = pay.date,
                    title = "سداد دين من الزبون: $customerName",
                    details = "طريقة السداد: ${pay.paymentMethod} • الملاحظات: ${pay.notes.ifEmpty { "سداد حساب" }}",
                    amount = pay.amount,
                    isInflow = true,
                    currency = pay.currency
                )
            )
        }
        
        list.sortByDescending { it.date }
        list
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AgencyAppBarTitle("التقارير المالية والإحصاء") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = PalIconUnified
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Institutional Agency Header Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bAgencyName = BrandingManager.resolvedAgencyName()
                    val bOwnerName = BrandingManager.resolvedOwnerName()
                    val subtitleText = buildString {
                        if (bOwnerName.isNotEmpty()) append("الوكيل: $bOwnerName • ")
                        append("نظام التقارير والقيود (${BrandingManager.PRODUCT_NAME_AR})")
                    }
                    Text(
                        text = bAgencyName,
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = subtitleText,
                        color = PalWhiteMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            // Tabs to select filter
            ScrollableTabRow(
                selectedTabIndex = selectedPeriod,
                containerColor = PalBlackNormal,
                contentColor = PalGreenNormal,
                edgePadding = 8.dp,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
            ) {
                Tab(selected = selectedPeriod == 0, onClick = { selectedPeriod = 0 }, text = { Text("اليومية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedPeriod == 1, onClick = { selectedPeriod = 1 }, text = { Text("أسبوعية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedPeriod == 2, onClick = { selectedPeriod = 2 }, text = { Text("الشهرية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedPeriod == 3, onClick = { selectedPeriod = 3 }, text = { Text("السنوية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedPeriod == 4, onClick = { selectedPeriod = 4 }, text = { Text("الأرشيف", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                Tab(selected = selectedPeriod == 5, onClick = { selectedPeriod = 5 }, text = { Text("تقويم مخصص", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
            }

            if (selectedPeriod == 1) {
                val years = listOf("2024", "2025", "2026", "2027", "2028", "2029", "2030")
                val arabicMonths = arrayOf(
                    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                )
                val currentYear = reportsWeekFilterYearMonth.split("-").getOrNull(0) ?: todayStr.substring(0, 4)
                val currentMonthNum = reportsWeekFilterYearMonth.split("-").getOrNull(1)?.toIntOrNull() ?: todayStr.substring(5, 7).toInt()

                val totalWeeks = remember(reportsWeekFilterYearMonth) {
                    try {
                        val parts = reportsWeekFilterYearMonth.split("-")
                        val y = parts[0].toInt()
                        val m = parts[1].toInt()
                        val cal = java.util.Calendar.getInstance()
                        cal.set(java.util.Calendar.YEAR, y)
                        cal.set(java.util.Calendar.MONTH, m - 1)
                        val maxDays = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
                        if (maxDays >= 29) 5 else 4
                    } catch (e: Exception) {
                        5
                    }
                }

                LaunchedEffect(totalWeeks) {
                    if (reportsWeekFilterNum > totalWeeks) {
                        reportsWeekFilterNum = totalWeeks
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(PalBlackNormal, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "اختيار السنة:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        years.forEach { y ->
                            val isSel = currentYear == y
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteSoft
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        reportsWeekFilterYearMonth = String.format(java.util.Locale.US, "%s-%02d", y, currentMonthNum)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = y,
                                    color = fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "اختيار الشهر:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        arabicMonths.forEachIndexed { idx, mName ->
                            val mVal = idx + 1
                            val isSel = currentMonthNum == mVal
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteSoft
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        reportsWeekFilterYearMonth = String.format(java.util.Locale.US, "%s-%02d", currentYear, mVal)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mName (${idx + 1})",
                                    color = fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text(
                        text = "اختيار الأسبوع من الشهر:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        (1..totalWeeks).forEach { w ->
                            val isSel = reportsWeekFilterNum == w
                            val weekLabels = arrayOf("", "الأول", "الثاني", "الثالث", "الرابع", "الخامس")
                            val label = "الأسبوع ${weekLabels.getOrElse(w) { w.toString() }}"

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSel) PalGreenNormal else PalBlackDark)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(6.dp))
                                    .clickable { reportsWeekFilterNum = w }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSel) Color.White else PalWhiteSoft,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            if (selectedPeriod == 5) {
                UnifiedPeriodSelector(
                    initialType = customReportsType,
                    initialValue = customReportsValue,
                    onPeriodChanged = { type, finalVal ->
                        customReportsType = type
                        customReportsValue = finalVal
                        reportsReportDate = Helpers.formatCustomPeriodToArabic(type, finalVal)
                    }
                )
            }

            val periodLabel = when (selectedPeriod) {
                0 -> "تقرير أداء اليوم ($dateFilter)"
                1 -> "تقرير أداء الأسبوع الحالي ($dateFilter)"
                2 -> "تقرير أداء الشهر ($dateFilter)"
                3 -> "تقرير أداء السنة ($dateFilter)"
                5 -> "تقرير أداء الفترة المخصصة ($dateFilter)"
                else -> "تقرير الأداء المالي والأرشيف العام (شامل ماليًا)"
            }

            Text(
                text = periodLabel,
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )

            val hasYerActivity = totalSalesYer != 0.0 || totalPurchasesYer != 0.0 || totalExpensesYer != 0.0 ||
                totalCollectionsYer != 0.0 || totalTransferredYer != 0.0 || totalTaxAmountYer != 0.0 ||
                netActualCashYer != 0.0 || netActualProfitYer != 0.0 ||
                timelineOps.any { MoneyMath.isYer(it.currency) }
            val hasSarActivity = totalSalesSar != 0.0 || totalPurchasesSar != 0.0 || totalExpensesSar != 0.0 ||
                totalCollectionsSar != 0.0 || totalTransferredSar != 0.0 ||
                netActualCashSar != 0.0 || netActualProfitSar != 0.0 ||
                timelineOps.any { MoneyMath.isSar(it.currency) }
            val availableCurrencyTabs = remember(currencyConfig, hasYerActivity, hasSarActivity) {
                buildList {
                    if (currencyConfig.allowsYer || hasYerActivity) add(MoneyMath.CURRENCY_YER)
                    if (currencyConfig.allowsSar || hasSarActivity) add(MoneyMath.CURRENCY_SAR)
                    if (isEmpty()) add(currencyConfig.resolvedDefaultCurrency)
                }
            }
            LaunchedEffect(availableCurrencyTabs, currencyConfig) {
                val safeIndex = activeCurrencyTab.coerceIn(0, (availableCurrencyTabs.size - 1).coerceAtLeast(0))
                if (activeCurrencyTab >= availableCurrencyTabs.size) {
                    val defIdx = availableCurrencyTabs.indexOf(currencyConfig.resolvedDefaultCurrency)
                    activeCurrencyTab = if (defIdx >= 0) defIdx else safeIndex
                }
            }
            val selectedTabIndex = activeCurrencyTab.coerceIn(0, (availableCurrencyTabs.size - 1).coerceAtLeast(0))
            val selectedTabCurrency = availableCurrencyTabs[selectedTabIndex]
            val isYerTabSelected = MoneyMath.isYer(selectedTabCurrency)

            // Separator Tabs for YER vs SAR
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = PalBlackNormal,
                contentColor = PalGreenNormal,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
            ) {
                availableCurrencyTabs.forEachIndexed { idx, tabCurr ->
                    val isSel = selectedTabIndex == idx
                    val isHistOnly = !currencyConfig.isSingleCurrencyAllowed(tabCurr)
                    val baseTitle = if (MoneyMath.isYer(tabCurr)) "ريال يمني (منفصل)" else "ريال سعودي (منفصل)"
                    val title = if (isHistOnly) "$baseTitle - تاريخي" else baseTitle
                    Tab(
                        selected = isSel,
                        onClick = { activeCurrencyTab = idx },
                        text = {
                            Text(
                                title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) PalSuccessNormal else PalWhiteMuted
                            )
                        }
                    )
                }
            }

            if (isYerTabSelected) {
                val isProfitYer = netActualProfitYer >= 0
                val statusTextYer = if (isProfitYer) "حالة الحساب: صافي الربح بالريال اليمني" else "حالة الحساب: صافي الخسارة بالريال اليمني"
                val statusColorYer = if (isProfitYer) PalSuccessNormal else PalRedNormal
                val statusBgYer = if (isProfitYer) PalSoftGreenTint else PalRedNormal.copy(alpha = 0.08f)
                val statusBorderYer = statusColorYer.copy(alpha = 0.35f)

                Card(
                    colors = CardDefaults.cardColors(containerColor = statusBgYer),
                    border = BorderStroke(1.dp, statusBorderYer),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isProfitYer) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = statusColorYer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(statusTextYer, color = statusColorYer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "المجموع المالي المحقق: " + Helpers.formatWithCurrency(netActualProfitYer, "ريال يمني"),
                                color = PalWhitePure,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // YER Performance Details Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "تفاصيل القيود بالريال اليمني",
                            color = PalWhitePure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "صافي النقدية الفعلية (الخزينة):", color = PalWhitePure, fontSize = 12.sp)
                            Text(
                                text = Helpers.formatWithCurrency(netActualCashYer, "ريال يمني"),
                                color = if (netActualCashYer >= 0) PalSuccessNormal else PalRedNormal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val labelYer = if (isProfitYer) "صافي الربح الفعلي المالي:" else "صافي الخسارة الفعلية المالية:"
                            Text(text = labelYer, color = PalWhitePure, fontSize = 12.sp)
                            Text(
                                text = Helpers.formatWithCurrency(netActualProfitYer, "ريال يمني"),
                                color = statusColorYer,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        HorizontalDivider(color = PalBorderColor, thickness = 1.dp)

                        Text(
                            text = "تفاصيل الإيرادات والمقبوضات اليمني (+):",
                            color = PalSuccessNormal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ReportBreakdownRow(label = "إجمالي المبيعات الكلية (+)", amount = totalSalesYer, isNegative = false, currency = "ريال يمني")
                        ReportBreakdownRow(label = "المقبوض نقداً (كاش)", amount = totalCashReceivedYer, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال يمني")
                        ReportBreakdownRow(label = "المقبوض بنظام تحويل بنكي", amount = totalTransferReceivedYer, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال يمني")
                        ReportBreakdownRow(label = "المقبوض بنظام إيداع صرافة", amount = totalDepositReceivedYer, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال يمني")
                        ReportBreakdownRow(label = "مبيعات كلية آجلة (على الحساب)", amount = totalCreditSalesYer, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال يمني")
                        ReportBreakdownRow(label = "إجمالي الديون الجديدة المسجلة (-)", amount = totalNewDebtsYer, isNegative = true, tintColor = PalRedNormal, currency = "ريال يمني")
                        ReportBreakdownRow(label = "إجمالي تحصيلات ديون الزبائن (+)", amount = totalCollectionsYer, isNegative = false, tintColor = PalSuccessNormal, currency = "ريال يمني")

                        HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                        Text(
                            text = "تفاصيل المدفوعات والخرجات اليمني (-):",
                            color = PalRedNormal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ReportBreakdownRow(label = "إجمالي مدفوعات المشتريات والموردين", amount = totalPurchasesPaidYer, isNegative = true, currency = "ريال يمني")
                        if (totalSupplierSurplusYer > 0.0) {
                            ReportBreakdownRow(label = "رصيد فائض مستقل للوكالة", amount = totalSupplierSurplusYer, isNegative = false, tintColor = PalSuccessNormal, currency = "ريال يمني")
                        }
                        ReportBreakdownRow(label = "ضرائب ورسوم القات المسددة", amount = totalTaxAmountYer, isNegative = true, currency = "ريال يمني")
                        ReportBreakdownRow(label = "خرج المفرش وعمال اليومية", amount = totalStallOutflowYer + totalLaborOutflowYer, isNegative = true, currency = "ريال يمني")
                        ReportBreakdownRow(label = "مصاريف تشغيلية وعامة أخرى", amount = totalExpensesYer, isNegative = true, currency = "ريال يمني")
                        ReportBreakdownRow(label = "حركات الحوالات المالية الصادرة", amount = totalTransferredYer, isNegative = true, tintColor = PalWhiteSoft, currency = "ريال يمني")
                    }
                }
            } else {
                val isProfitSar = netActualProfitSar >= 0
                val statusTextSar = if (isProfitSar) "حالة الحساب: صافي الربح بالريال السعودي" else "حالة الحساب: صافي الخسارة بالريال السعودي"
                val statusColorSar = if (isProfitSar) PalSuccessNormal else PalRedNormal
                val statusBgSar = if (isProfitSar) PalSoftGreenTint else PalRedNormal.copy(alpha = 0.08f)
                val statusBorderSar = statusColorSar.copy(alpha = 0.35f)

                Card(
                    colors = CardDefaults.cardColors(containerColor = statusBgSar),
                    border = BorderStroke(1.dp, statusBorderSar),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (isProfitSar) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = statusColorSar,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text(statusTextSar, color = statusColorSar, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                "المجموع المالي المحقق: " + Helpers.formatWithCurrency(netActualProfitSar, "ريال سعودي"),
                                color = PalWhitePure,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp
                            )
                            Text(
                                "ما يعادل بالريال اليمني: " + Helpers.formatWithCurrency(netActualProfitSar * rateVal, "ريال يمني"),
                                color = statusColorSar,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }

                // SAR Performance Details Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "تفاصيل القيود بالريال السعودي",
                            color = PalWhitePure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "صافي النقدية الفعلية (الخزينة):", color = PalWhitePure, fontSize = 12.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Helpers.formatWithCurrency(netActualCashSar, "ريال سعودي"),
                                    color = if (netActualCashSar >= 0) PalSuccessNormal else PalRedNormal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "ما يعادل: " + Helpers.formatWithCurrency(netActualCashSar * rateVal, "ريال يمني"),
                                    color = PalWhiteMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val labelSar = if (isProfitSar) "صافي الربح الفعلي المالي:" else "صافي الخسارة الفعلية المالية:"
                            Text(text = labelSar, color = PalWhitePure, fontSize = 12.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = Helpers.formatWithCurrency(netActualProfitSar, "ريال سعودي"),
                                    color = statusColorSar,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "ما يعادل: " + Helpers.formatWithCurrency(netActualProfitSar * rateVal, "ريال يمني"),
                                    color = PalWhiteMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        HorizontalDivider(color = PalBorderColor, thickness = 1.dp)

                        Text(
                            text = "تفاصيل الإيرادات والمقبوضات السعودي (+):",
                            color = PalSuccessNormal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ReportBreakdownRow(label = "إجمالي المبيعات الكلية (+)", amount = totalSalesSar, isNegative = false, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "المقبوض نقداً (كاش)", amount = totalCashReceivedSar, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "المقبوض بنظام تحويل بنكي", amount = totalTransferReceivedSar, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "المقبوض بنظام إيداع صرافة", amount = totalDepositReceivedSar, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "مبيعات كلية آجلة (على الحساب)", amount = totalCreditSalesSar, isNegative = false, tintColor = PalWhiteMuted, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "إجمالي الديون الجديدة المسجلة (-)", amount = totalNewDebtsSar, isNegative = true, tintColor = PalRedNormal, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "إجمالي تحصيلات ديون الزبائن (+)", amount = totalCollectionsSar, isNegative = false, tintColor = PalSuccessNormal, currency = "ريال سعودي")

                        HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                        Text(
                            text = "تفاصيل المدفوعات والخرجات السعودي (-):",
                            color = PalRedNormal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        ReportBreakdownRow(label = "إجمالي مدفوعات المشتريات والموردين", amount = totalPurchasesPaidSar, isNegative = true, currency = "ريال سعودي")
                        if (totalSupplierSurplusSar > 0.0) {
                            ReportBreakdownRow(label = "رصيد فائض مستقل للوكالة", amount = totalSupplierSurplusSar, isNegative = false, tintColor = PalSuccessNormal, currency = "ريال سعودي")
                        }
                        ReportBreakdownRow(label = "مصاريف تشغيلية وعامة أخرى", amount = totalExpensesSar, isNegative = true, currency = "ريال سعودي")
                        ReportBreakdownRow(label = "حركات الحوالات المالية الصادرة", amount = totalTransferredSar, isNegative = true, tintColor = PalWhiteSoft, currency = "ريال سعودي")
                    }
                }
            }

            // Comprehensive System Management Summary
            val totalInventoryItemsCount = inventoryItems.size
            val totalInventoryQuantity = inventoryItems.sumOf { it.quantity }
            val totalInventoryValuationYer = inventoryItems.filter { it.buyPriceCurrency.contains("يمني") }.sumOf { it.quantity * it.buyPrice }
            val totalInventoryValuationSar = inventoryItems.filter { it.buyPriceCurrency.contains("سعودي") }.sumOf { it.quantity * it.buyPrice }

            val totalCustomersCount = customers.size
            val totalSuppliersCount = suppliers.size

            val overallClientDebtsYer = invoices.sumOf { it.totalAmountYer } - (customerPayments.filter { it.currency.contains("يمني") }.sumOf { it.amount } + invoices.sumOf { it.paidAmountYer })
            val overallClientDebtsSar = invoices.sumOf { it.totalAmountSar } - (customerPayments.filter { it.currency.contains("سعودي") }.sumOf { it.amount } + invoices.sumOf { it.paidAmountSar })

            val overallSupplierDebtsYer = purchases.sumOf { it.debtRemainingYer }
            val overallSupplierDebtsSar = purchases.sumOf { it.debtRemainingSar }

            LedgerSectionHeader(
                title = "الخلاصة الشاملة لإدارة النظام والعهود والسلع",
                modifier = Modifier.padding(top = 6.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Inventory,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "حالة المخزون وقيم السلع الحالية:",
                            color = PalWhitePure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي عدد أصناف المخزن:", color = PalWhiteMuted, fontSize = 11.sp)
                            Text("$totalInventoryItemsCount صنف", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي كمية المخزون (حبة):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text("${Helpers.formatQuantity(totalInventoryQuantity)} حبة", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("القيمة الشرائية للمخزون (يمني):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalInventoryValuationYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("القيمة الشرائية للمخزون (سعودي):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalInventoryValuationSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "حسابات العملاء والديون المستحقة:",
                            color = PalWhitePure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي عدد العملاء النشطين:", color = PalWhiteMuted, fontSize = 11.sp)
                            Text("$totalCustomersCount عميل", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ديون جديدة مسجلة بالفترة (يمني):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalNewDebtsYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ديون جديدة مسجلة بالفترة (سعودي):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalNewDebtsSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الديون المتبقية الكلية على العملاء (يمني):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(if (overallClientDebtsYer >= 0.0) overallClientDebtsYer else 0.0, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الديون المتبقية الكلية على العملاء (سعودي):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(if (overallClientDebtsSar >= 0.0) overallClientDebtsSar else 0.0, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }

                    HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalShipping,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "حسابات الموردين والالتزامات للوكالة:",
                            color = PalWhitePure,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Column(
                        modifier = Modifier.padding(start = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي عدد الموردين المسجلين:", color = PalWhiteMuted, fontSize = 11.sp)
                            Text("$totalSuppliersCount مورد", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الالتزامات الكلية المستحقة للموردين (يمني):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(overallSupplierDebtsYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("الالتزامات الكلية المستحقة للموردين (سعودي):", color = PalWhiteMuted, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(overallSupplierDebtsSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Real-time Chart Visualization
            LedgerSectionHeader(title = "مقارنة نسب الأداء المالي والتحليلي")

            Card(
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PalBorderColor)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text("ريال يمني:", color = PalSuccessNormal, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    val maxValYer = maxOf(totalSalesYer, totalPurchasesYer, totalTaxAmountYer + totalStallOutflowYer + totalLaborOutflowYer + totalExpensesYer, 1.0)
                    ChartBarItem(label = "إيراد المبيعات (يمني)", amount = totalSalesYer, maxAmount = maxValYer, color = PalSuccessNormal)
                    ChartBarItem(label = "قيمة المشتريات (يمني)", amount = totalPurchasesYer, maxAmount = maxValYer, color = PalRedNormal)
                    ChartBarItem(label = "مجموع الرسوم (يمني)", amount = totalTaxAmountYer + totalStallOutflowYer + totalLaborOutflowYer, maxAmount = maxValYer, color = PalWarningNormal)
                    ChartBarItem(label = "مصروفات تشغيلية (يمني)", amount = totalExpensesYer, maxAmount = maxValYer, color = PalGreenDark)

                    HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 4.dp))

                    Text("ريال سعودي:", color = PalSuccessNormal, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    val maxValSar = maxOf(totalSalesSar, totalPurchasesSar, totalExpensesSar, 1.0)
                    ChartBarItem(label = "إيراد المبيعات (سعودي)", amount = totalSalesSar, maxAmount = maxValSar, color = PalSuccessNormal)
                    ChartBarItem(label = "قيمة المشتريات (سعودي)", amount = totalPurchasesSar, maxAmount = maxValSar, color = PalRedNormal)
                    ChartBarItem(label = "مصروفات تشغيلية (سعودي)", amount = totalExpensesSar, maxAmount = maxValSar, color = PalGreenDark)
                }
            }

            val shareTitle = when (selectedPeriod) {
                0 -> "تقرير اليوم المالي التفصيلي"
                1 -> "تقرير الأسبوع المالي التفصيلي"
                2 -> "تقرير الشهر المالي التفصيلي"
                3 -> "تقرير السنة المالي التفصيلي"
                else -> "تقرير الأداء المالي والأرشيف العام الشامل"
            }

            val currencyLabel = if (isYerTabSelected) "الريال اليمني" else "الريال السعودي"
            val filterCurrencyName = if (isYerTabSelected) "يمني" else "سعودي"

            val filteredTimelineOps = remember(timelineOps, isYerTabSelected) {
                timelineOps.filter { it.currency.contains(filterCurrencyName) }
            }

            val archiveMsg = remember(isYerTabSelected, filteredTimelineOps, totalSalesYer, totalSalesSar, netActualCashYer, netActualCashSar, netActualProfitYer, netActualProfitSar, reportsReportDate, dateFilter) {
                buildString {
                    appendLine(BrandingManager.buildShareHeader("📊 الأرشيف والسجل المالي ($shareTitle)"))
                    appendLine("💵 العملة والفرع: $currencyLabel (منفصل)")
                    appendLine("📅 تاريخ التصدير: $reportsReportDate")
                    appendLine("========================================")
                    if (isYerTabSelected) {
                        appendLine("📊 🇾🇪 الخلاصة المالية بالريال اليمني:")
                        appendLine("• إجمالي المبيعات الكلية بالفترة (+): ${Helpers.formatWithCurrency(totalSalesYer, "ريال يمني")}")
                        appendLine("• إجمالي المقبوض نقداً (كاش): ${Helpers.formatWithCurrency(totalCashReceivedYer, "ريال يمني")}")
                        appendLine("• إجمالي المقبوض عبر تحويل: ${Helpers.formatWithCurrency(totalTransferReceivedYer, "ريال يمني")}")
                        appendLine("• إجمالي المقبوض عبر إيداع: ${Helpers.formatWithCurrency(totalDepositReceivedYer, "ريال يمني")}")
                        appendLine("• إجمالي المبيعات الآجلة (على الحساب): ${Helpers.formatWithCurrency(totalCreditSalesYer, "ريال يمني")}")
                        appendLine("• إجمالي الديون الجديدة المسجلة (-): ${Helpers.formatWithCurrency(totalNewDebtsYer, "ريال يمني")}")
                        appendLine("• إجمالي تحصيلات ديون الزبائن (+): ${Helpers.formatWithCurrency(totalCollectionsYer, "ريال يمني")}")
                        appendLine("• إجمالي مدفوعات المشتريات للموردين (-): ${Helpers.formatWithCurrency(totalPurchasesPaidYer, "ريال يمني")}")
                        appendLine("• ضرائب ورسوم القات المسددة (-): ${Helpers.formatWithCurrency(totalTaxAmountYer, "ريال يمني")}")
                        appendLine("• خرج المفرش وعمال اليومية (-): ${Helpers.formatWithCurrency(totalStallOutflowYer + totalLaborOutflowYer, "ريال يمني")}")
                        appendLine("• إجمالي المصروفات العامة (-): ${Helpers.formatWithCurrency(totalExpensesYer, "ريال يمني")}")
                        appendLine("• الحوالات المالية الصادرة (-): ${Helpers.formatWithCurrency(totalTransferredYer, "ريال يمني")}")
                        appendLine("----------------------------------------")
                        appendLine("💰 صافي النقدية بالخزينة: ${Helpers.formatWithCurrency(netActualCashYer, "ريال يمني")}")
                        val profitLabelYer = if (netActualProfitYer >= 0) "صافي الربح الفعلي المالي: " else "صافي الخسارة الفعلية المالية: "
                        appendLine("$profitLabelYer${Helpers.formatWithCurrency(netActualProfitYer, "ريال يمني")}")
                        appendLine("حالة الحساب: ${if (netActualProfitYer >= 0) "صافي الربح بالريال اليمني" else "صافي الخسارة بالريال اليمني"}")
                    } else {
                        appendLine("📊 🇸🇦 الخلاصة المالية بالريال السعودي:")
                        appendLine("• إجمالي المبيعات الكلية بالفترة (+): ${Helpers.formatWithCurrency(totalSalesSar, "ريال سعودي")}")
                        appendLine("• إجمالي المقبوض نقداً (كاش): ${Helpers.formatWithCurrency(totalCashReceivedSar, "ريال سعودي")}")
                        appendLine("• إجمالي المقبوض عبر تحويل: ${Helpers.formatWithCurrency(totalTransferReceivedSar, "ريال سعودي")}")
                        appendLine("• إجمالي المقبوض عبر إيداع: ${Helpers.formatWithCurrency(totalDepositReceivedSar, "ريال سعودي")}")
                        appendLine("• إجمالي المبيعات الآجلة (على الحساب): ${Helpers.formatWithCurrency(totalCreditSalesSar, "ريال سعودي")}")
                        appendLine("• إجمالي الديون الجديدة المسجلة (-): ${Helpers.formatWithCurrency(totalNewDebtsSar, "ريال سعودي")}")
                        appendLine("• إجمالي تحصيلات ديون الزبائن (+): ${Helpers.formatWithCurrency(totalCollectionsSar, "ريال سعودي")}")
                        appendLine("• إجمالي مدفوعات المشتريات للموردين (-): ${Helpers.formatWithCurrency(totalPurchasesPaidSar, "ريال سعودي")}")
                        appendLine("• إجمالي المصروفات العامة (-): ${Helpers.formatWithCurrency(totalExpensesSar, "ريال سعودي")}")
                        appendLine("• الحوالات المالية الصادرة (-): ${Helpers.formatWithCurrency(totalTransferredSar, "ريال سعودي")}")
                        appendLine("----------------------------------------")
                        appendLine("💰 صافي النقدية بالخزينة: ${Helpers.formatWithCurrency(netActualCashSar, "ريال سعودي")}")
                        val profitLabelSar = if (netActualProfitSar >= 0) "صافي الربح الفعلي المالي: " else "صافي الخسارة الفعلية المالية: "
                        appendLine("$profitLabelSar${Helpers.formatWithCurrency(netActualProfitSar, "ريال سعودي")}")
                        appendLine("حالة الحساب: ${if (netActualProfitSar >= 0) "صافي الربح بالريال السعودي" else "صافي الخسارة بالريال السعودي"}")
                    }
                    appendLine("============ سجل العمليات والقيود المفلترة ============")
                    if (filteredTimelineOps.isEmpty()) {
                        appendLine("لا توجد أي عمليات مالية مقيدة بالعملة المحددة.")
                    } else {
                        filteredTimelineOps.forEachIndexed { idx, op ->
                            appendLine("${idx + 1}. [${op.type}] - #${op.docId}")
                            appendLine("   • التفاصيل: ${op.title}")
                            appendLine("   • القيود: ${op.details}")
                            val sign = if (op.isInflow) "+" else "-"
                            appendLine("   • القيمة المالية: $sign ${Helpers.formatWithCurrency(op.amount, op.currency)}")
                            appendLine("   • التاريخ والوقت: ${op.date}")
                            appendLine("-------------------------")
                        }
                    }
                    appendLine(BrandingManager.buildShareFooter())
                }
            }

            LedgerSectionHeader(
                title = "تصدير ومشاركة تقارير الأرشيف المالي",
                modifier = Modifier.padding(top = 6.dp)
            )

            OutlinedTextField(
                value = reportsReportDate,
                onValueChange = { reportsReportDate = it },
                label = { Text("تعيين وتخصيص تاريخ التقرير المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth().testTag("reports_custom_date_input"),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PalGreenNormal,
                    unfocusedBorderColor = PalBorderColor,
                    focusedTextColor = PalWhitePure,
                    unfocusedTextColor = PalWhiteSoft,
                    focusedContainerColor = PalBlackNormal,
                    unfocusedContainerColor = PalBlackNormal
                ),
                shape = RoundedCornerShape(10.dp)
            )

            // Export Actions buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val headers = listOf("التاريخ", "رقم السند", "البيان والتفاصيل", "القيمة المالية والعملة")
                        val rows = filteredTimelineOps.map { op ->
                            listOf(
                                op.date.split(" ")[0],
                                "#${op.docId}",
                                "${op.title} - ${op.details}",
                                "${if (op.isInflow) "تحصيل +" else "صرف -"} ${Helpers.formatWithCurrency(op.amount, op.currency)}"
                            )
                        }
                        val pdfTotals = if (isYerTabSelected) {
                            val profitLabel = if (netActualProfitYer >= 0) "صافي الربح الفعلي (يمني)" else "صافي الخسارة الفعلية (يمني)"
                            mapOf(
                                "إجمالي المبيعات بالريال اليمني" to Helpers.formatWithCurrency(totalSalesYer, "ريال يمني"),
                                "صافي نقدية الخزينة (يمني)" to Helpers.formatWithCurrency(netActualCashYer, "ريال يمني"),
                                profitLabel to Helpers.formatWithCurrency(netActualProfitYer, "ريال يمني")
                            )
                        } else {
                            val profitLabel = if (netActualProfitSar >= 0) "صافي الربح الفعلي (سعودي)" else "صافي الخسارة الفعلية (سعودي)"
                            mapOf(
                                "إجمالي المبيعات بالريال السعودي" to Helpers.formatWithCurrency(totalSalesSar, "ريال سعودي"),
                                "صافي نقدية الخزينة (سعودي)" to Helpers.formatWithCurrency(netActualCashSar, "ريال سعودي"),
                                profitLabel to Helpers.formatWithCurrency(netActualProfitSar, "ريال سعودي")
                            )
                        }
                        Helpers.generatePdfAndShare(
                            context = context,
                            title = "$shareTitle - $currencyLabel",
                            headers = headers,
                            rows = rows,
                            totals = pdfTotals,
                            customDate = reportsReportDate,
                            reportPeriod = dateFilter
                        )
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("سجل PDF", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        Helpers.shareViaWhatsApp(context, "", archiveMsg)
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("واتساب", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        bluetoothPrintText = archiveMsg
                        showBluetoothPrintTrigger = true
                    },
                    modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenDark),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Text("طباعة حرارية", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = PalBorderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 6.dp))

            LedgerSectionHeader(
                title = "سجل العمليات والقيود ($shareTitle) - $currencyLabel",
                trailing = {
                    Text(
                        text = "العدد: (${filteredTimelineOps.size})",
                        color = PalWhiteMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (filteredTimelineOps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "لا توجد عمليات مسجلة تحت هذا النطاق الزمني لهذه العملة",
                            color = PalWhiteMuted,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    filteredTimelineOps.forEach { op ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val badgeBg = when (op.type) {
                                            "مبيعات", "سداد" -> PalGreenNormal
                                            "مشتريات" -> PalRedNormal
                                            "رسوم" -> PalWarningNormal
                                            "مصروفات" -> PalRedNormal.copy(alpha = 0.85f)
                                            else -> PalGreenDark
                                        }
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(badgeBg)
                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                        ) {
                                            Text(op.type, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Text(op.docId, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    }

                                    val amountColor = if (op.isInflow) PalSuccessNormal else PalRedNormal
                                    val sign = if (op.isInflow) "+" else "-"
                                    Text(
                                        text = "$sign ${Helpers.formatWithCurrency(op.amount, op.currency)}",
                                        color = amountColor,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }

                                Text(text = op.title, color = PalWhiteSoft, fontSize = 12.sp)
                                Text(text = op.details, color = PalWhiteMuted, fontSize = 11.sp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    Text(
                                        text = op.date,
                                        color = PalWhiteMuted,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Left
                                    )
                                }
                            }
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun ReportBreakdownRow(
    label: String,
    amount: Double,
    isNegative: Boolean,
    tintColor: Color = PalWhiteSoft,
    currency: String = ""
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = PalWhiteMuted, fontSize = 12.sp)
        val formatted = if (currency.isNotEmpty()) Helpers.formatWithCurrency(amount, currency) else Helpers.formatMoney(amount)
        val showText = if (isNegative) "- $formatted" else formatted
        val color = if (isNegative && amount > 0) PalRedNormal else if (!isNegative && amount > 0) PalSuccessNormal else tintColor
        Text(text = showText, color = color, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun ChartBarItem(
    label: String,
    amount: Double,
    maxAmount: Double,
    color: Color
) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, color = PalWhiteSoft, fontSize = 11.sp)
            Text(text = Helpers.formatMoney(amount), color = PalWhiteMuted, fontSize = 11.sp)
        }

        val fraction = if (maxAmount <= 0.0 || amount.isNaN() || maxAmount.isNaN()) {
            0.0f
        } else {
            (amount / maxAmount).coerceIn(0.0, 1.0).toFloat()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(PalBorderColor)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(color)
            )
        }
    }
}
