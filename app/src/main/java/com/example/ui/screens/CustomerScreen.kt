package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.Customer
import com.example.data.CustomerPayment
import com.example.data.SalesInvoice
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterManager
import com.example.util.Helpers
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.ui.components.BluetoothPrintDialog
import com.example.ui.components.CurrencySelectionRow
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.components.AccountingTextField
import com.example.util.MoneyMath

data class AccountMovement(
    val date: String,
    val type: String, // "invoice" or "payment"
    val reference: String,
    val notes: String,
    val amount: Double,
    val paid: Double,
    val debt: Double,
    val currency: String = "ريال يمني",
    val originalId: Int = 0
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.importContactsFromPhone()
        } else {
            android.widget.Toast.makeText(context, "تم رفض إذن الوصول لجهات الاتصال", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    val customersList by viewModel.allCustomers.collectAsState()
    val invoicesList by viewModel.allInvoices.collectAsState()
    val paymentsList by viewModel.allPayments.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"

    val selectedReportPeriod = selectedPeriod
    val customDebtReportType = customPeriodType
    val customDebtReportValue = customPeriodValue

    val selectedStatementPeriod = selectedPeriod
    val customStatementReportType = customPeriodType
    val customStatementReportValue = customPeriodValue
    
    var showDebtReportsDialog by remember { mutableStateOf(false) }
    var customerGeneralReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    var customerReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }

    LaunchedEffect(selectedPeriod, customPeriodValue, customPeriodType) {
        val label = when (selectedPeriod) {
            "الكل" -> "جميع السجلات والأوقات"
            "أسبوعي", "اسبوعي" -> {
                if (customPeriodValue.startsWith("WEEK_FILTER|")) Helpers.formatCustomPeriodToArabic("أسبوعي", customPeriodValue)
                else "الأسبوع الحالي"
            }
            "شهري" -> "الشهر الحالي"
            "سنوي" -> "السنة الحالية"
            "تقويم مخصص" -> Helpers.formatCustomPeriodToArabic(customPeriodType, customPeriodValue)
            else -> selectedPeriod
        }
        customerReportDate = label
        customerGeneralReportDate = label
    }

    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }
    
    var showAddCustDialog by remember { mutableStateOf(false) }
    var selectedCust by remember { mutableStateOf<Customer?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Form inputs for customer
    var custName by remember { mutableStateOf("") }
    var custPhone by remember { mutableStateOf("") }
    var custAddress by remember { mutableStateOf("") }
    var custNotes by remember { mutableStateOf("") }

    // Direct debt registration state
    var showDirectDebtDialog by remember { mutableStateOf(false) }
    var directDebtAmountInput by remember { mutableStateOf("") }
    var directDebtStatementInput by remember { mutableStateOf("") }
    var directDebtNotesInput by remember { mutableStateOf("") }
    var directDebtDateInput by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    var directDebtDueDateInput by remember { mutableStateOf("") }
    var directDebtOpNumber by remember { mutableStateOf("") }
    var directDebtCurrencySelected by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }

    var searchText by remember { mutableStateOf("") }

    // Payment registration state
    var showPaymentDialog by remember { mutableStateOf(false) }
    var paymentAmountInput by remember { mutableStateOf("") }
    var paymentNotesInput by remember { mutableStateOf("") }
    var paymentMethodSelected by remember { mutableStateOf("نقداً") }
    var paymentCurrencySelected by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }

    var showStatementDialog by remember { mutableStateOf(false) }
    var selectedCustForStatement by remember { mutableStateOf<Customer?>(null) }
    var paymentsHistoryList by remember { mutableStateOf<List<CustomerPayment>>(emptyList()) }

    // Statement Movement Edit State - Invoice
    var showEditStatementInvoiceDialog by remember { mutableStateOf(false) }
    var editingStatementInvoice by remember { mutableStateOf<SalesInvoice?>(null) }
    var editInvCustName by remember { mutableStateOf("") }
    var editInvDate by remember { mutableStateOf("") }
    var editInvCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }
    var editInvTotal by remember { mutableStateOf("") }
    var editInvDiscount by remember { mutableStateOf("") }
    var editInvTax by remember { mutableStateOf("") }
    var editInvPaid by remember { mutableStateOf("") }
    var editInvDebt by remember { mutableStateOf("") }
    var editInvPaymentMethod by remember { mutableStateOf("نقداً") }
    var editInvTotalYer by remember { mutableStateOf("") }
    var editInvTotalSar by remember { mutableStateOf("") }
    var editInvPaidYer by remember { mutableStateOf("") }
    var editInvPaidSar by remember { mutableStateOf("") }
    var editInvDebtYer by remember { mutableStateOf("") }
    var editInvDebtSar by remember { mutableStateOf("") }
    var editInvPaymentMethodYer by remember { mutableStateOf("نقداً") }
    var editInvPaymentMethodSar by remember { mutableStateOf("نقداً") }

    // Statement Movement Edit State - Payment Voucher
    var showEditStatementPaymentDialog by remember { mutableStateOf(false) }
    var editingStatementPayment by remember { mutableStateOf<CustomerPayment?>(null) }
    var editPayAmount by remember { mutableStateOf("") }
    var editPayCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }
    var editPayDate by remember { mutableStateOf("") }
    var editPayMethod by remember { mutableStateOf("نقداً") }
    var editPayNotes by remember { mutableStateOf("") }

    LaunchedEffect(selectedCustForStatement) {
        selectedCustForStatement?.let { customer ->
            viewModel.getPaymentsForCustomer(customer.id).collect { list ->
                paymentsHistoryList = list
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { AgencyAppBarTitle("إدارة حسابات العملاء والديون") },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PalIconUnified)
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = {
                                selectedCust = null
                                custName = ""
                                custPhone = ""
                                custAddress = ""
                                custNotes = ""
                                showAddCustDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة عميل", tint = PalIconUnified)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
            }
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        // Outstanding Multi-Currency Stats (YER / SAR)
        val totalDebtsYer = invoicesList.sumOf { it.totalAmountYer } - 
                            (paymentsList.filter { it.currency.contains("يمني") }.sumOf { it.amount } + invoicesList.sumOf { it.paidAmountYer })
        val totalDebtsSar = invoicesList.sumOf { it.totalAmountSar } - 
                            (paymentsList.filter { it.currency.contains("سعودي") }.sumOf { it.amount } + invoicesList.sumOf { it.paidAmountSar })
        val totalPaymentsCollectedYer = paymentsList.filter { it.currency.contains("يمني") }.sumOf { it.amount }
        val totalPaymentsCollectedSar = paymentsList.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

        val filteredCustomers = remember(customersList, searchText) {
            if (searchText.trim().isEmpty()) {
                customersList
            } else {
                customersList.filter {
                    it.name.contains(searchText, ignoreCase = true) ||
                    it.phone.contains(searchText)
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // YER Debt Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("الديون الآجلة (يمني)", color = PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(PalSoftGreenTint, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = Helpers.formatWithCurrency(totalDebtsYer, "ريال يمني"),
                                    color = if (totalDebtsYer > 0) PalRedNormal else PalWhitePure,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // SAR Debt Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("الديون الآجلة (سعودي)", color = PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(PalSoftGreenTint, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = Helpers.formatWithCurrency(totalDebtsSar, "ريال سعودي"),
                                    color = if (totalDebtsSar > 0) PalRedNormal else PalWhitePure,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // YER Collections Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("المتحصلات (يمني)", color = PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(PalSoftGreenTint, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = Helpers.formatWithCurrency(totalPaymentsCollectedYer, "ريال يمني"),
                                    color = PalSuccessNormal,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // SAR Collections Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("المتحصلات (سعودي)", color = PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(PalSoftGreenTint, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Payments, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(14.dp))
                                }
                                Text(
                                    text = Helpers.formatWithCurrency(totalPaymentsCollectedSar, "ريال سعودي"),
                                    color = PalSuccessNormal,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }
                }
            }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Button(
                    onClick = { showDebtReportsDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.0f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Assessment, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("التقارير الحسابية", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val hasPermission = androidx.core.content.ContextCompat.checkSelfPermission(
                            context,
                            android.Manifest.permission.READ_CONTACTS
                        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

                        if (hasPermission) {
                            viewModel.importContactsFromPhone()
                        } else {
                            permissionLauncher.launch(android.Manifest.permission.READ_CONTACTS)
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1.0f).heightIn(min = 48.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp)
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("استيراد جهات الاتصال", color = PalWhitePure, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }
            }
            }

            item {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    placeholder = { Text("بحث عن عميل بالاسم أو رقم الهاتف...", color = PalWhiteMuted, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = PalIconUnified) },
                    trailingIcon = {
                        if (searchText.isNotEmpty()) {
                            IconButton(onClick = { searchText = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح", tint = PalWhiteMuted)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor,
                        focusedContainerColor = PalBlackNormal,
                        unfocusedContainerColor = PalBlackNormal,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )
            }

            // Customer list
            if (filteredCustomers.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchText.isNotEmpty()) "لا توجد نتائج تطابق البحث" else "لا يوجد عملاء مسجلين حاليا. انقر فوق زر الزائد لإضافة عميل",
                            color = PalWhiteMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(filteredCustomers) { customer ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(customer.name, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(
                                            if (customer.phone.isNotEmpty()) "هاتف: ${customer.phone}" else "لا يوجد رقم هاتف",
                                            color = PalWhiteMuted,
                                            fontSize = 12.sp
                                        )
                                        if (customer.address.isNotEmpty()) {
                                            Text(
                                                "العنوان: ${customer.address}",
                                                color = PalWhiteMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                        if (customer.notes.isNotEmpty()) {
                                            Text(
                                                "ملاحظات: ${customer.notes}",
                                                color = PalWhiteMuted,
                                                fontSize = 11.sp,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        val cInvoices = invoicesList.filter { it.customerName == customer.name }
                                        val cPayments = paymentsList.filter { it.customerId == customer.id }
                                        val cDebtYer = cInvoices.sumOf { inv ->
                                            if (inv.totalAmountYer > 0.0) inv.debtAmountYer
                                            else if (inv.currency.contains("يمني")) inv.debtAmount
                                            else 0.0
                                        } - cPayments.filter { it.currency.contains("يمني") }.sumOf { it.amount }

                                        val cDebtSar = cInvoices.sumOf { inv ->
                                            if (inv.totalAmountSar > 0.0) inv.debtAmountSar
                                            else if (inv.currency.contains("سعودي")) inv.debtAmount
                                            else 0.0
                                        } - cPayments.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

                                        Text("رصيد الدين الحالي", color = PalWhiteMuted, fontSize = 10.sp)
                                        if (cDebtYer != 0.0 || cDebtSar != 0.0) {
                                            if (cDebtYer != 0.0) {
                                                Text(
                                                    Helpers.formatWithCurrency(cDebtYer, "ريال يمني"),
                                                    color = if (cDebtYer > 0) PalRedNormal else PalSuccessNormal,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                            if (cDebtSar != 0.0) {
                                                Text(
                                                    Helpers.formatWithCurrency(cDebtSar, "ريال سعودي"),
                                                    color = if (cDebtSar > 0) PalRedNormal else PalSuccessNormal,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                            }
                                        } else {
                                            Text(
                                                "0 ريال يمني",
                                                color = PalSuccessNormal,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            selectedCust = customer
                                            paymentAmountInput = ""
                                            paymentNotesInput = ""
                                            paymentCurrencySelected = currencyConfig.resolvedDefaultCurrency
                                            showPaymentDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تسديد دفعة", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }

                                    Button(
                                        onClick = {
                                            selectedCust = customer
                                            directDebtAmountInput = ""
                                            directDebtStatementInput = ""
                                            directDebtNotesInput = ""
                                            directDebtDateInput = Helpers.getCurrentDateTime()
                                            directDebtOpNumber = "INV-${(1000..9999).random()}"
                                            directDebtDueDateInput = ""
                                            directDebtCurrencySelected = currencyConfig.resolvedDefaultCurrency
                                            showDirectDebtDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 44.dp)
                                    ) {
                                        Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("تسجيل دين جديد", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            selectedCustForStatement = customer
                                            showStatementDialog = true
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                                        border = BorderStroke(1.dp, PalBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                                        modifier = Modifier.weight(1.5f).heightIn(min = 44.dp)
                                    ) {
                                        Icon(Icons.Default.LibraryBooks, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("كشف الحساب", color = PalWhitePure, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedCust = customer
                                            custName = customer.name
                                            custPhone = customer.phone
                                            custAddress = customer.address
                                            custNotes = customer.notes
                                            showAddCustDialog = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = PalIconUnified)
                                    }

                                    IconButton(
                                        onClick = {
                                            selectedCust = customer
                                            showDeleteConfirm = true
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف العميل", tint = PalRedNormal)
                                    }

                                    IconButton(
                                        onClick = {
                                            val bAgency = com.example.util.BrandingManager.resolvedAgencyName()
                                            val bTagline = com.example.util.BrandingManager.agencyTagline.trim()
                                            val agencyIntro = if (bTagline.isNotEmpty()) "$bAgency ($bTagline)" else bAgency
                                            val bOwnerName = com.example.util.BrandingManager.resolvedOwnerName()
                                            val bPhone = com.example.util.BrandingManager.phone.trim()
                                            val message = buildString {
                                                appendLine("السلام عليكم ورحمة الله وبركاته، الأخ الكريم العميل المحترم: ${customer.name}")
                                                appendLine("تحية طيبة محملة بالتقدير والاحترام من $agencyIntro.")
                                                appendLine()
                                                val debtStr = run {
                                                    val cInv = invoicesList.filter { it.customerName == customer.name }
                                                    val cPay = paymentsList.filter { it.customerId == customer.id }
                                                    val y = cInv.sumOf { if (it.totalAmountYer > 0.0) it.debtAmountYer else if (it.currency.contains("يمني")) it.debtAmount else 0.0 } - cPay.filter { it.currency.contains("يمني") }.sumOf { it.amount }
                                                    val s = cInv.sumOf { if (it.totalAmountSar > 0.0) it.debtAmountSar else if (it.currency.contains("سعودي")) it.debtAmount else 0.0 } - cPay.filter { it.currency.contains("سعودي") }.sumOf { it.amount }
                                                    buildString {
                                                        if (y != 0.0) append(Helpers.formatWithCurrency(y, "ريال يمني"))
                                                        if (s != 0.0) {
                                                            if (isNotEmpty()) append(" و ")
                                                            append(Helpers.formatWithCurrency(s, "ريال سعودي"))
                                                        }
                                                        if (isEmpty()) append("0 ريال يمني")
                                                    }
                                                }
                                                appendLine("نود تذكيركم بلطف وعناية بأن رصيد مديونيتكم المتبقي لصالح الوكالة هو: $debtStr.")
                                                appendLine("نأمل منكم التكرم بسداد هذا المبلغ في أقرب فرصة مناسبة لكم، وذلك لمساعدتنا في تنظيم الحسابات التشغيلية واليومية ومواصلة تقديم أفضل الخدمات لكم.")
                                                appendLine()
                                                appendLine("شاكرين لكم عظيم تفهمكم وحسن تعاونكم الدائم.")
                                                if (bOwnerName.isNotEmpty()) appendLine("إدارة الوكالة / $bOwnerName")
                                                if (bPhone.isNotEmpty()) appendLine("للتواصل: $bPhone")
                                                append("نظام ${com.example.util.BrandingManager.PRODUCT_NAME_AR} المحاسبي")
                                            }
                                            Helpers.shareText(context, message)
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = "إرسال تذكير بالدين عبر الواتساب", tint = PalIconUnified)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    // --- Add / Edit Customer Dialog ---
    if (showAddCustDialog) {
        AlertDialog(
            onDismissRequest = { showAddCustDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = if (selectedCust == null) "إضافة عميل جديد" else "تعديل بيانات العميل",
                    color = PalWhitePure,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "معلومات الهوية والاتصال للعميل:",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = custName,
                                        onValueChange = { custName = it },
                                        label = "اسم العميل بالكامل",
                                        placeholder = "أدخل الاسم الرباعي..."
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = custPhone,
                                        onValueChange = { custPhone = it },
                                        label = "رقم هاتف العميل",
                                        placeholder = "للمشاركة التلقائية...",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                    )
                                }
                            }
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "العنوان والملاحظات المحاسبية:",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = custAddress,
                                        onValueChange = { custAddress = it },
                                        label = "العنوان السكني / التجاري",
                                        placeholder = "مثال: صعدة، الشارع العام..."
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = custNotes,
                                        onValueChange = { custNotes = it },
                                        label = "ملاحظات إضافية",
                                        placeholder = "أي تفاصيل اختيارية..."
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedCust != null) {
                        Button(
                            onClick = {
                                showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("حذف العميل", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (custName.trim().isEmpty()) {
                                android.widget.Toast.makeText(context, "الرجاء إدخال اسم العميل!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedCust == null) {
                                viewModel.createCustomer(custName, custPhone, custAddress, custNotes)
                            } else {
                                viewModel.updateCustomer(selectedCust!!.copy(name = custName, phone = custPhone, address = custAddress, notes = custNotes))
                            }
                            showAddCustDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddCustDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Payment Registration Dialog ---
    if (showPaymentDialog && selectedCust != null) {
        AlertDialog(
            onDismissRequest = { showPaymentDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = { Text("تسجيل سداد دفعة مالية", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val selInvoices = invoicesList.filter { it.customerName == selectedCust!!.name }
                    val selPayments = paymentsList.filter { it.customerId == selectedCust!!.id }
                    val selDebtYer = selInvoices.sumOf { inv ->
                        if (inv.totalAmountYer > 0.0) inv.debtAmountYer
                        else if (inv.currency.contains("يمني")) inv.debtAmount
                        else 0.0
                    } - selPayments.filter { it.currency.contains("يمني") }.sumOf { it.amount }

                    val selDebtSar = selInvoices.sumOf { inv ->
                        if (inv.totalAmountSar > 0.0) inv.debtAmountSar
                        else if (inv.currency.contains("سعودي")) inv.debtAmount
                        else 0.0
                    } - selPayments.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

                    // Card 1: Debt balance summaries
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "مديونية وحساب العميل الحالي:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = "العميل: ${selectedCust!!.name}",
                                color = PalWhitePure,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مديونية بالريال اليمني:",
                                    color = PalWhiteSoft,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = Helpers.formatWithCurrency(selDebtYer, "ريال يمني"),
                                    color = PalRedNormal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مديونية بالريال السعودي:",
                                    color = PalWhiteSoft,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = Helpers.formatWithCurrency(selDebtSar, "ريال سعودي"),
                                    color = PalRedNormal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Card 2: Payment details
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "بيانات دفعة السداد المعتمدة:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AccountingTextField(
                                    value = paymentAmountInput,
                                    onValueChange = { paymentAmountInput = it },
                                    label = "المبلغ المسدد *",
                                    placeholder = "أدخل المبلغ المسدد...",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                CurrencySelectionRow(
                                    selectedCurrency = paymentCurrencySelected,
                                    currencyConfig = currencyConfig,
                                    onCurrencySelected = { paymentCurrencySelected = it },
                                    title = "عملة السداد:"
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = paymentNotesInput,
                                        onValueChange = { paymentNotesInput = it },
                                        label = "ملاحظات السداد",
                                        placeholder = "أي ملاحظات..."
                                    )
                                }
                                
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "طريقة السداد",
                                        color = PalWhitePure,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .background(PalBlackNormal, RoundedCornerShape(10.dp))
                                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                                            .padding(4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        listOf("نقداً", "إيداع", "تحويل").forEach { method ->
                                            val isSel = paymentMethodSelected == method
                                            val bg = if (isSel) PalGreenNormal else Color.Transparent
                                            val fg = if (isSel) Color.White else PalWhiteMuted
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(bg)
                                                    .clickable { paymentMethodSelected = method },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(text = method, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = MoneyMath.parseDoubleOrNull(paymentAmountInput) ?: 0.0
                        if (amount <= 0.0) {
                            android.widget.Toast.makeText(context, "يرجى تحديد رصيد سداد صحيح أكبر من الصفر!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!currencyConfig.isSingleCurrencyAllowed(paymentCurrencySelected)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(paymentCurrencySelected), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        viewModel.addCustomerPayment(
                            customerId = selectedCust!!.id,
                            amount = amount,
                            notes = paymentNotesInput,
                            paymentMethod = paymentMethodSelected,
                            currency = paymentCurrencySelected
                        )
                        android.widget.Toast.makeText(context, "تم قيد سداد الدفعة وحفظ السجل المالي", android.widget.Toast.LENGTH_SHORT).show()
                        showPaymentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد التسديد", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPaymentDialog = false }) {
                    Text("تراجع", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Direct Customer Debt Registration Dialog ---
    if (showDirectDebtDialog && selectedCust != null) {
        AlertDialog(
            onDismissRequest = { showDirectDebtDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تسجيل حركة دين مباشر جديد",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val directInvoices = invoicesList.filter { it.customerName == selectedCust!!.name }
                    val directPayments = paymentsList.filter { it.customerId == selectedCust!!.id }
                    val selDebtYer = directInvoices.sumOf { inv ->
                        if (inv.totalAmountYer > 0.0) inv.debtAmountYer
                        else if (inv.currency.contains("يمني")) inv.debtAmount
                        else 0.0
                    } - directPayments.filter { it.currency.contains("يمني") }.sumOf { it.amount }

                    val selDebtSar = directInvoices.sumOf { inv ->
                        if (inv.totalAmountSar > 0.0) inv.debtAmountSar
                        else if (inv.currency.contains("سعودي")) inv.debtAmount
                        else 0.0
                    } - directPayments.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

                    // Card 1: Debt balance summaries
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "مديونية وحساب العميل الحالي:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            Text(
                                text = "إضافة دين مباشر للعميل: ${selectedCust!!.name}",
                                color = PalWhitePure,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مديونية بالريال اليمني:",
                                    color = PalWhiteSoft,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = Helpers.formatWithCurrency(selDebtYer, "ريال يمني"),
                                    color = PalRedNormal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "مديونية بالريال السعودي:",
                                    color = PalWhiteSoft,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = Helpers.formatWithCurrency(selDebtSar, "ريال سعودي"),
                                    color = PalRedNormal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // Card 2: Value & Currency
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "قيمة الدين المباشر والعملة المعتمدة:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AccountingTextField(
                                    value = directDebtAmountInput,
                                    onValueChange = { directDebtAmountInput = it },
                                    label = "مبلغ الدين المباشر *",
                                    placeholder = "أدخل قيمة مبلغ الحركة...",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                CurrencySelectionRow(
                                    selectedCurrency = directDebtCurrencySelected,
                                    currencyConfig = currencyConfig,
                                    onCurrencySelected = { directDebtCurrencySelected = it },
                                    title = "عملة القيد المباشر:"
                                )
                            }
                        }
                    }

                    // Card 3: Reference & Dates
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "التوثيق والبيانات المرجعية والمواعيد:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = directDebtOpNumber,
                                        onValueChange = { directDebtOpNumber = it },
                                        label = "الرقم المرجعي / الفاتورة",
                                        placeholder = "مثال: INV-10029"
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = directDebtDateInput,
                                        onValueChange = { directDebtDateInput = it },
                                        label = "تاريخ ووقت الحركة",
                                        placeholder = "yyyy-MM-dd HH:mm:ss"
                                    )
                                }
                            }
                        }
                    }

                    // Card 4: Details & Statement
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "تفاصيل وبيان الدين المباشر:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = directDebtStatementInput,
                                        onValueChange = { directDebtStatementInput = it },
                                        label = "البيان والغرض *",
                                        placeholder = "مثال مبيعات سابقة..."
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = directDebtNotesInput,
                                        onValueChange = { directDebtNotesInput = it },
                                        label = "ملاحظات إضافية",
                                        placeholder = "أي تفاصيل أخرى..."
                                    )
                                }
                            }
                        }
                    }

                    // Card 5: Due date
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "تاريخ الاستحقاق المحاسبي:",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            AccountingTextField(
                                value = directDebtDueDateInput,
                                onValueChange = { directDebtDueDateInput = it },
                                label = "تاريخ الاستحقاق المحاسبي (اختياري)",
                                placeholder = "yyyy-MM-dd"
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = MoneyMath.parseDoubleOrNull(directDebtAmountInput) ?: 0.0
                        if (amount <= 0.0) {
                            android.widget.Toast.makeText(context, "الرجاء كتابة قيمة دين صحيحة أكبر من الصفر!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!currencyConfig.isSingleCurrencyAllowed(directDebtCurrencySelected)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(directDebtCurrencySelected), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        if (directDebtStatementInput.trim().isEmpty()) {
                            android.widget.Toast.makeText(context, "الرجاء إدخال بيان لحركة الدين لشرح المعاملة!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        
                        val statementText = directDebtStatementInput.trim()
                        val fullNotes = buildString {
                            append("رقم قيد: ")
                            append(directDebtOpNumber)
                            if (directDebtDueDateInput.trim().isNotEmpty()) {
                                append(" • استحقاق: ")
                                append(directDebtDueDateInput.trim())
                            }
                            if (directDebtNotesInput.trim().isNotEmpty()) {
                                append(" • ملاحظات: ")
                                append(directDebtNotesInput.trim())
                            }
                        }

                        viewModel.recordDirectDebt(
                            customerName = selectedCust!!.name,
                            amount = amount,
                            statement = statementText,
                            date = directDebtDateInput.trim().ifEmpty { Helpers.getCurrentDateTime() },
                            notes = fullNotes,
                            currency = directDebtCurrencySelected
                        )
                        
                        android.widget.Toast.makeText(context, "تم تسجيل وإثبات حركة الدين وإبلاغ كشف الحساب بنجاح!", android.widget.Toast.LENGTH_LONG).show()
                        showDirectDebtDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ الدين", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDirectDebtDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Customer Account Statement Dialog ---
    if (showStatementDialog && selectedCustForStatement != null) {
        val todayStr = Helpers.getCurrentDate()
        val curMonthStr = todayStr.substring(0, 7)
        val curYearStr = todayStr.substring(0, 4)

        // Filter invoices & payments based on selectedStatementPeriod using robust trimmed case-insensitive matching
        val filteredCustInvs = invoicesList.filter { 
            it.customerName.trim().equals(selectedCustForStatement!!.name.trim(), ignoreCase = true) 
        }.filter { inv ->
            Helpers.matchesPeriod(inv.date, selectedStatementPeriod, customStatementReportValue, customStatementReportType)
        }

        val filteredCustPays = paymentsHistoryList.filter { pay ->
            Helpers.matchesPeriod(pay.date, selectedStatementPeriod, customStatementReportValue, customStatementReportType)
        }

        val totalInvoicedLocal = filteredCustInvs.sumOf { it.totalAmount }
        val totalPaidLocal = filteredCustPays.sumOf { it.amount } + filteredCustInvs.sumOf { it.paidAmount }

        // Create a beautifully unified chronological list of account movements
        val movements = remember(filteredCustInvs, filteredCustPays) {
            val list = mutableListOf<AccountMovement>()
            filteredCustInvs.forEach { inv ->
                val isMulti = inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0
                if (isMulti) {
                    list.add(
                        AccountMovement(
                            date = inv.date,
                            type = "invoice",
                            reference = "مبيعات يمني (فاتورة #${inv.id})",
                            notes = "الدفع: ${inv.paymentMethodYer} (متبقي آجل يمني: ${Helpers.formatWithCurrency(inv.debtAmountYer, "ريال يمني")})",
                            amount = inv.totalAmountYer,
                            paid = inv.paidAmountYer,
                            debt = inv.debtAmountYer,
                            currency = "ريال يمني",
                            originalId = inv.id
                        )
                    )
                    list.add(
                        AccountMovement(
                            date = inv.date,
                            type = "invoice",
                            reference = "مبيعات سعودي (فاتورة #${inv.id})",
                            notes = "الدفع: ${inv.paymentMethodSar} (متبقي آجل سعودي: ${Helpers.formatWithCurrency(inv.debtAmountSar, "ريال سعودي")})",
                            amount = inv.totalAmountSar,
                            paid = inv.paidAmountSar,
                            debt = inv.debtAmountSar,
                            currency = "ريال سعودي",
                            originalId = inv.id
                        )
                    )
                } else {
                    val dispCurr = if (inv.totalAmountSar > 0.0 || inv.currency.contains("سعودي")) "ريال سعودي" else "ريال يمني"
                    val dispAmt = if (inv.totalAmountSar > 0.0) inv.totalAmountSar else if (inv.totalAmountYer > 0.0) inv.totalAmountYer else inv.totalAmount
                    val dispPaid = if (inv.totalAmountSar > 0.0) inv.paidAmountSar else if (inv.totalAmountYer > 0.0) inv.paidAmountYer else inv.paidAmount
                    val dispDebt = if (inv.totalAmountSar > 0.0) inv.debtAmountSar else if (inv.totalAmountYer > 0.0) inv.debtAmountYer else inv.debtAmount
                    list.add(
                        AccountMovement(
                            date = inv.date,
                            type = "invoice",
                            reference = "مبيعات بموجب فاتورة #${inv.id}",
                            notes = "الدفع: ${inv.paymentMethod} (متبقي آجل: ${Helpers.formatWithCurrency(dispDebt, dispCurr)})",
                            amount = dispAmt,
                            paid = dispPaid,
                            debt = dispDebt,
                            currency = dispCurr,
                            originalId = inv.id
                        )
                    )
                }
            }
            filteredCustPays.forEach { pay ->
                list.add(
                    AccountMovement(
                        date = pay.date,
                        type = "payment",
                        reference = "سند قبض تسديد دفعة #${pay.id}",
                        notes = pay.notes.ifEmpty { "سداد مباشر من العميل" } + " (${pay.paymentMethod})",
                        amount = pay.amount,
                        paid = pay.amount,
                        debt = 0.0,
                        currency = pay.currency,
                        originalId = pay.id
                    )
                )
            }
            list.sortedByDescending { it.date }
        }

        val cInvoices = invoicesList.filter { 
            it.customerName.trim().equals(selectedCustForStatement!!.name.trim(), ignoreCase = true) 
        }
        val allPaysYer = paymentsHistoryList.filter { it.currency.contains("يمني") }
        val allPaysSar = paymentsHistoryList.filter { it.currency.contains("سعودي") }

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

        val msg = buildString {
            appendLine(com.example.util.BrandingManager.buildShareHeader("كشف الحساب للعميل: ${selectedCustForStatement!!.name}"))
            appendLine("رقم هاتف العميل: ${selectedCustForStatement!!.phone.ifEmpty { "غير محدد" }}")
            appendLine("الفترة المحددة: $selectedStatementPeriod")
            appendLine("تاريخ التقرير: $customerReportDate")
            appendLine("-------------------------")
            appendLine("رصيد المديونية الكلي المتبقي حالياً:")
            appendLine("- ريال يمني: ${Helpers.formatWithCurrency(debtYer, "ريال يمني")}")
            appendLine("- ريال سعودي: ${Helpers.formatWithCurrency(debtSar, "ريال سعودي")}")
            appendLine("-------------------------")
            if (movements.isNotEmpty()) {
                appendLine("حركات الحساب التفصيلية بالفترة المعينة:")
                movements.forEach { mov ->
                    if (mov.type == "invoice") {
                        appendLine("• ${mov.date.split(" ")[0]} - فاتورة بقيمة ${Helpers.formatWithCurrency(mov.amount, mov.currency)} (المتبقي آجل: +${Helpers.formatWithCurrency(mov.debt, mov.currency)})")
                    } else {
                        appendLine("• ${mov.date.split(" ")[0]} - تسديد بقيمة -${Helpers.formatWithCurrency(mov.amount, mov.currency)} (${mov.notes})")
                    }
                }
                appendLine("-------------------------")
            }
            appendLine("إجمالي فواتير الفترة:")
            appendLine("• ريال يمني: ${Helpers.formatWithCurrency(periodInvoicedYer, "ريال يمني")}")
            appendLine("• ريال سعودي: ${Helpers.formatWithCurrency(periodInvoicedSar, "ريال سعودي")}")
            appendLine("إجمالي مسددات الفترة:")
            appendLine("• ريال يمني: ${Helpers.formatWithCurrency(periodPaidYer, "ريال يمني")}")
            appendLine("• ريال سعودي: ${Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي")}")
            appendLine("-------------------------")
            appendLine("نرجو سرعة سداد المبالغ وتصفية الحساب. شاكرين تعاونكم.")
            appendLine(com.example.util.BrandingManager.buildShareFooter())
        }

        AlertDialog(
            onDismissRequest = { showStatementDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            containerColor = PalBlackNormal,
            title = { Text("كشف حساب وتفاصيل العميل", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("العميل: ${selectedCustForStatement!!.name}", color = PalWhitePure, fontWeight = FontWeight.Bold)
                    Text("الهاتف: ${selectedCustForStatement!!.phone.ifEmpty { "غير متوفر" }}", color = PalWhiteMuted, fontSize = 12.sp)
                    
                    HorizontalDivider(color = PalBorderColor)

                    Text("رصيد الدين الإجمالي المتبقي:", color = PalWhiteSoft, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("• بالريال اليمني: ${Helpers.formatWithCurrency(debtYer, "ريال يمني")}", color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("• بالريال السعودي: ${Helpers.formatWithCurrency(debtSar, "ريال سعودي")}", color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                    Spacer(modifier = Modifier.height(4.dp))

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        modifier = Modifier.fillMaxWidth().border(1.dp, PalBorderColor, RoundedCornerShape(10.dp)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                "ملخص حساب الفترة المختارة ($selectedStatementPeriod):",
                                color = PalWhitePure,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            HorizontalDivider(color = PalBorderColor)
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("يمني — ديون الفترة:", color = PalWhiteMuted, fontSize = 10.sp)
                                    Text(Helpers.formatWithCurrency(periodInvoicedYer, "ريال يمني"), color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("يمني — السداد المخصوم:", color = PalWhiteMuted, fontSize = 10.sp)
                                    Text(Helpers.formatWithCurrency(periodPaidYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column {
                                    Text("سعودي — ديون الفترة:", color = PalWhiteMuted, fontSize = 10.sp)
                                    Text(Helpers.formatWithCurrency(periodInvoicedSar, "ريال سعودي"), color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("سعودي — السداد المخصوم:", color = PalWhiteMuted, fontSize = 10.sp)
                                    Text(Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(6.dp))

                    Text("اختر فترة فلترة وتصفية العمليات أو حدد تقويماً مخصصاً:", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص", "الكل").forEach { period ->
                            val selected = selectedStatementPeriod == period
                            val bg = if (selected) PalGreenNormal else PalBlackDark
                            val fg = if (selected) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (selected) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedPeriod = period
                                        if (period == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                                            customPeriodValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(period, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    if (selectedStatementPeriod == "تقويم مخصص" || selectedStatementPeriod == "أسبوعي") {
                        UnifiedPeriodSelector(
                            initialType = if (selectedStatementPeriod == "أسبوعي") "أسبوع" else customPeriodType,
                            initialValue = if (selectedStatementPeriod == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                                "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                            } else {
                                customPeriodValue
                            },
                            onPeriodChanged = { type, finalVal ->
                                customPeriodType = type
                                customPeriodValue = finalVal
                                customerReportDate = Helpers.formatCustomPeriodToArabic(type, finalVal)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customerReportDate,
                        onValueChange = { customerReportDate = it },
                        label = { Text("تعيين وتخصيص تاريخ كشف الحساب المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("customer_statement_date_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    HorizontalDivider(color = PalBorderColor)

                    Text("سجل حركات الحساب التفصيلية بالفترة المختارة:", color = PalWhiteMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    
                    if (movements.isEmpty()) {
                        Text("لا توجد حركات حساب مسجلة بالفترة المعينة", color = PalWhiteMuted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 10.dp), textAlign = TextAlign.Center)
                    } else {
                        movements.forEach { movement ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PalBlackDark, RoundedCornerShape(10.dp))
                                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    val icon = if (movement.type == "invoice") Icons.Default.TrendingUp else Icons.Default.TrendingDown
                                    val iconTint = if (movement.type == "invoice") PalRedNormal else PalSuccessNormal
                                    val bgTint = iconTint.copy(alpha = 0.1f)
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .background(bgTint, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(18.dp))
                                    }
                                    
                                    Column {
                                        Text(movement.reference, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(movement.notes, color = PalWhiteMuted, fontSize = 10.sp)
                                    }
                                }
                                
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        val textValue = if (movement.type == "invoice") {
                                            "+${Helpers.formatWithCurrency(movement.debt, movement.currency)}"
                                        } else {
                                            "-${Helpers.formatWithCurrency(movement.amount, movement.currency)}"
                                        }
                                        val textColor = if (movement.type == "invoice") PalRedNormal else PalSuccessNormal
                                        Text(textValue, color = textColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Text(movement.date.split(" ")[0], color = PalWhiteMuted, fontSize = 11.sp)
                                    }

                                    IconButton(
                                        onClick = {
                                            if (movement.type == "invoice") {
                                                val inv = invoicesList.find { it.id == movement.originalId }
                                                if (inv != null) {
                                                    editingStatementInvoice = inv
                                                    editInvCustName = inv.customerName
                                                    editInvDate = inv.date
                                                    editInvCurrency = inv.currency
                                                    editInvTotal = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                                        (inv.totalAmountYer + inv.totalAmountSar).toString()
                                                    } else inv.totalAmount.toString()
                                                    editInvDiscount = inv.discount.toString()
                                                    editInvTax = inv.taxApplied.toString()
                                                    editInvPaid = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                                        (inv.paidAmountYer + inv.paidAmountSar).toString()
                                                    } else inv.paidAmount.toString()
                                                    editInvDebt = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                                        (inv.debtAmountYer + inv.debtAmountSar).toString()
                                                    } else inv.debtAmount.toString()
                                                    editInvPaymentMethod = inv.paymentMethod
                                                    editInvTotalYer = inv.totalAmountYer.toString()
                                                    editInvTotalSar = inv.totalAmountSar.toString()
                                                    editInvPaidYer = inv.paidAmountYer.toString()
                                                    editInvPaidSar = inv.paidAmountSar.toString()
                                                    editInvDebtYer = inv.debtAmountYer.toString()
                                                    editInvDebtSar = inv.debtAmountSar.toString()
                                                    editInvPaymentMethodYer = inv.paymentMethodYer
                                                    editInvPaymentMethodSar = inv.paymentMethodSar
                                                    showEditStatementInvoiceDialog = true
                                                }
                                            } else {
                                                val pay = paymentsHistoryList.find { it.id == movement.originalId }
                                                    ?: paymentsList.find { it.id == movement.originalId }
                                                if (pay != null) {
                                                    editingStatementPayment = pay
                                                    editPayAmount = pay.amount.toString()
                                                    editPayCurrency = pay.currency
                                                    editPayDate = pay.date
                                                    editPayMethod = pay.paymentMethod
                                                    editPayNotes = pay.notes
                                                    showEditStatementPaymentDialog = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(34.dp)
                                                .background(PalSoftGreenTint, RoundedCornerShape(8.dp))
                                                .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Edit,
                                                contentDescription = "تعديل السجل",
                                                tint = PalIconUnified,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val headers = listOf("التاريخ", "الحركة / البيان المالي", "العملة", "قيمة الدين المضاف", "قيمة السداد المخصوم")
                                val rows = mutableListOf<List<String>>()
                                movements.forEach { mov ->
                                    rows.add(
                                        listOf(
                                            mov.date.split(" ")[0],
                                            mov.reference + "\n(" + mov.notes + ")",
                                            mov.currency,
                                            if (mov.type == "invoice") Helpers.formatWithCurrency(mov.debt, mov.currency) else "0 ${mov.currency}",
                                            if (mov.type == "payment") Helpers.formatWithCurrency(mov.amount, mov.currency) else "0 ${mov.currency}"
                                        )
                                    )
                                }
                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "كشف حساب عميل: ${selectedCustForStatement!!.name}",
                                    headers = headers,
                                    rows = rows,
                                    totals = mapOf(
                                        "إجمالي مبيعات الفترة (ريال يمني)" to Helpers.formatWithCurrency(periodInvoicedYer, "ريال يمني"),
                                        "إجمالي السداد المخصوم للفترة (ريال يمني)" to Helpers.formatWithCurrency(periodPaidYer, "ريال يمني"),
                                        "الرصيد المتبقي الكلي حالياً (ريال يمني)" to Helpers.formatWithCurrency(debtYer, "ريال يمني"),
                                        "----------------------------------------" to "----------------------------------------",
                                        "إجمالي مبيعات الفترة (ريال سعودي)" to Helpers.formatWithCurrency(periodInvoicedSar, "ريال سعودي"),
                                        "إجمالي السداد المخصوم للفترة (ريال سعودي)" to Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي"),
                                        "الرصيد المتبقي الكلي حالياً (ريال سعودي)" to Helpers.formatWithCurrency(debtSar, "ريال سعودي")
                                    ),
                                    customDate = Helpers.getCurrentDateTime(),
                                    reportPeriod = customerReportDate
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                Helpers.shareViaWhatsApp(context, selectedCustForStatement!!.phone, msg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("واتساب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                Helpers.shareViaSMS(context, selectedCustForStatement!!.phone, msg)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("SMS", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                bluetoothPrintText = msg
                                showBluetoothPrintTrigger = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("طباعة", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showStatementDialog = false }) {
                    Text("إغلاق", color = PalWhiteMuted)
                }
            },
        )
    }

    // --- Edit Movement (Invoice) Dialog from Customer Statement ---
    val currentEditingInv = editingStatementInvoice
    if (showEditStatementInvoiceDialog && currentEditingInv != null) {
        val isMulti = currentEditingInv.totalAmountYer > 0.0 && currentEditingInv.totalAmountSar > 0.0
        AlertDialog(
            onDismissRequest = { showEditStatementInvoiceDialog = false },
            title = {
                Text(
                    text = "تعديل حركة الفاتورة رقم #${currentEditingInv.id}",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editInvCustName,
                        onValueChange = { editInvCustName = it },
                        label = { Text("اسم العميل", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editInvDate,
                        onValueChange = { editInvDate = it },
                        label = { Text("تاريخ ووقت الفاتورة", color = PalWhiteMuted) },
                        trailingIcon = {
                            IconButton(onClick = { editInvDate = Helpers.getCurrentDateTime() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "الوقت الحالي", tint = PalIconUnified)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CurrencySelectionRow(
                        selectedCurrency = if (isMulti) MoneyMath.CURRENCY_MULTI else editInvCurrency,
                        currencyConfig = currencyConfig,
                        onCurrencySelected = { editInvCurrency = it },
                        title = "العملة:",
                        extraOptionsWhenAllowed = listOf(MoneyMath.CURRENCY_MULTI)
                    )

                    if (editInvCurrency == "عملات متعددة" || isMulti) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PalBorderColor, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("تفاصيل الريال اليمني:", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = editInvTotalYer,
                                    onValueChange = {
                                        editInvTotalYer = it
                                        val tY = it.toDoubleOrNull() ?: 0.0
                                        val pY = editInvPaidYer.toDoubleOrNull() ?: 0.0
                                        editInvDebtYer = (tY - pY).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("إجمالي يمني", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvPaidYer,
                                    onValueChange = {
                                        editInvPaidYer = it
                                        val tY = editInvTotalYer.toDoubleOrNull() ?: 0.0
                                        val pY = it.toDoubleOrNull() ?: 0.0
                                        editInvDebtYer = (tY - pY).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("المدفوع يمني", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvDebtYer,
                                    onValueChange = { editInvDebtYer = it },
                                    label = { Text("المتبقي آجل يمني", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PalBorderColor, RoundedCornerShape(10.dp)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("تفاصيل الريال السعودي:", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = editInvTotalSar,
                                    onValueChange = {
                                        editInvTotalSar = it
                                        val tS = it.toDoubleOrNull() ?: 0.0
                                        val pS = editInvPaidSar.toDoubleOrNull() ?: 0.0
                                        editInvDebtSar = (tS - pS).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("إجمالي سعودي", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvPaidSar,
                                    onValueChange = {
                                        editInvPaidSar = it
                                        val tS = editInvTotalSar.toDoubleOrNull() ?: 0.0
                                        val pS = it.toDoubleOrNull() ?: 0.0
                                        editInvDebtSar = (tS - pS).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("المدفوع سعودي", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvDebtSar,
                                    onValueChange = { editInvDebtSar = it },
                                    label = { Text("المتبقي آجل سعودي", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = editInvTotal,
                            onValueChange = {
                                editInvTotal = it
                                val t = it.toDoubleOrNull() ?: 0.0
                                val p = editInvPaid.toDoubleOrNull() ?: 0.0
                                val d = editInvDiscount.toDoubleOrNull() ?: 0.0
                                editInvDebt = (t - p - d).coerceAtLeast(0.0).toString()
                            },
                            label = { Text("إجمالي الفاتورة", color = PalWhiteMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editInvDiscount,
                                onValueChange = {
                                    editInvDiscount = it
                                    val t = editInvTotal.toDoubleOrNull() ?: 0.0
                                    val p = editInvPaid.toDoubleOrNull() ?: 0.0
                                    val d = it.toDoubleOrNull() ?: 0.0
                                    editInvDebt = (t - p - d).coerceAtLeast(0.0).toString()
                                },
                                label = { Text("الخصم", color = PalWhiteMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft,
                                    focusedBorderColor = PalGreenNormal,
                                    unfocusedBorderColor = PalBorderColor
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editInvTax,
                                onValueChange = { editInvTax = it },
                                label = { Text("الضريبة", color = PalWhiteMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft,
                                    focusedBorderColor = PalGreenNormal,
                                    unfocusedBorderColor = PalBorderColor
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = editInvPaid,
                            onValueChange = {
                                editInvPaid = it
                                val t = editInvTotal.toDoubleOrNull() ?: 0.0
                                val p = it.toDoubleOrNull() ?: 0.0
                                val d = editInvDiscount.toDoubleOrNull() ?: 0.0
                                editInvDebt = (t - p - d).coerceAtLeast(0.0).toString()
                            },
                            label = { Text("المبلغ المدفوع", color = PalWhiteMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editInvDebt,
                            onValueChange = { editInvDebt = it },
                            label = { Text("المبلغ المتبقي (الآجل)", color = PalWhiteMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text("طريقة التحصيل / الدفع:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val methods = listOf("نقداً", "آجل", "إيداع", "تحويل")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        methods.forEach { method ->
                            val isSel = editInvPaymentMethod == method
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { editInvPaymentMethod = method }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(method, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!currencyConfig.isAllowed(editInvCurrency)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(editInvCurrency), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val isMultiNow = editInvCurrency == "عملات متعددة" || (editInvTotalYer.toDoubleOrNull() ?: 0.0) > 0.0 && (editInvTotalSar.toDoubleOrNull() ?: 0.0) > 0.0
                        val totYer = editInvTotalYer.toDoubleOrNull() ?: 0.0
                        val totSar = editInvTotalSar.toDoubleOrNull() ?: 0.0
                        val paidYer = editInvPaidYer.toDoubleOrNull() ?: 0.0
                        val paidSar = editInvPaidSar.toDoubleOrNull() ?: 0.0
                        val debtYer = editInvDebtYer.toDoubleOrNull() ?: 0.0
                        val debtSar = editInvDebtSar.toDoubleOrNull() ?: 0.0

                        val totalAmt = if (isMultiNow) (totYer + totSar) else (editInvTotal.toDoubleOrNull() ?: 0.0)
                        val paidAmt = if (isMultiNow) (paidYer + paidSar) else (editInvPaid.toDoubleOrNull() ?: 0.0)
                        val debtAmt = if (isMultiNow) (debtYer + debtSar) else (editInvDebt.toDoubleOrNull() ?: 0.0)
                        val discountAmt = editInvDiscount.toDoubleOrNull() ?: 0.0
                        val taxAmt = editInvTax.toDoubleOrNull() ?: 0.0

                        viewModel.updateSalesInvoiceFull(
                            invoiceId = currentEditingInv.id,
                            newCustomerName = editInvCustName.ifEmpty { currentEditingInv.customerName },
                            newDate = editInvDate.ifEmpty { currentEditingInv.date },
                            newCurrency = editInvCurrency,
                            newTotalAmount = totalAmt,
                            newTaxApplied = taxAmt,
                            newDiscount = discountAmt,
                            newPaidAmount = paidAmt,
                            newDebtAmount = debtAmt,
                            newPaymentMethod = editInvPaymentMethod,
                            newTotalAmountYer = totYer,
                            newTotalAmountSar = totSar,
                            newPaidAmountYer = paidYer,
                            newPaidAmountSar = paidSar,
                            newDebtAmountYer = debtYer,
                            newDebtAmountSar = debtSar,
                            newPaymentMethodYer = editInvPaymentMethodYer,
                            newPaymentMethodSar = editInvPaymentMethodSar
                        )
                        showEditStatementInvoiceDialog = false
                        android.widget.Toast.makeText(context, "تم تعديل الفاتورة وتحديث كشف الحساب والمبيعات بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ التعديلات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditStatementInvoiceDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Edit Movement (Payment Voucher) Dialog from Customer Statement ---
    if (showEditStatementPaymentDialog && editingStatementPayment != null) {
        AlertDialog(
            onDismissRequest = { showEditStatementPaymentDialog = false },
            title = {
                Text(
                    text = "تعديل سند قبض الدفعة رقم #${editingStatementPayment!!.id}",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editPayAmount,
                        onValueChange = { editPayAmount = it },
                        label = { Text("المبلغ المسدد", color = PalWhiteMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CurrencySelectionRow(
                        selectedCurrency = editPayCurrency,
                        currencyConfig = currencyConfig,
                        onCurrencySelected = { editPayCurrency = it },
                        title = "العملة:"
                    )

                    OutlinedTextField(
                        value = editPayDate,
                        onValueChange = { editPayDate = it },
                        label = { Text("تاريخ ووقت السداد", color = PalWhiteMuted) },
                        trailingIcon = {
                            IconButton(onClick = { editPayDate = Helpers.getCurrentDateTime() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "الوقت الحالي", tint = PalIconUnified)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("طريقة التحصيل:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val payMethods = listOf("نقداً", "إيداع", "تحويل", "شيك")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        payMethods.forEach { method ->
                            val isSel = editPayMethod == method
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { editPayMethod = method }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(method, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    OutlinedTextField(
                        value = editPayNotes,
                        onValueChange = { editPayNotes = it },
                        label = { Text("البيان والملاحظات", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = false,
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!currencyConfig.isSingleCurrencyAllowed(editPayCurrency)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(editPayCurrency), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val amt = MoneyMath.parseDoubleOrNull(editPayAmount) ?: 0.0
                        viewModel.updateCustomerPayment(
                            paymentId = editingStatementPayment!!.id,
                            newAmount = amt,
                            newDate = editPayDate.ifEmpty { editingStatementPayment!!.date },
                            newNotes = editPayNotes,
                            newPaymentMethod = editPayMethod,
                            newCurrency = editPayCurrency
                        )
                        showEditStatementPaymentDialog = false
                        android.widget.Toast.makeText(context, "تم تعديل سند القبض وتحديث رصيد الحساب بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ التعديلات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditStatementPaymentDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showDeleteConfirm && selectedCust != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "تأكيد حذف العميل",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف العميل (${selectedCust!!.name}) نهائياً؟ سيتم مسح حساب العميل وأي سجلات مرتبطة به تماماً.",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteCustomer(selectedCust!!)
                        showDeleteConfirm = false
                        showAddCustDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false }
                ) {
                    Text("إلغاء", color = PalWhiteMuted, fontWeight = FontWeight.Normal)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showDebtReportsDialog) {
        val periodInvoices = invoicesList.filter { Helpers.matchesPeriod(it.date, selectedReportPeriod, customDebtReportValue, customDebtReportType) }
        val periodNewDebt = periodInvoices.sumOf { it.debtAmount }
        val periodNewDebtYer = periodInvoices.sumOf { it.debtAmountYer }
        val periodNewDebtSar = periodInvoices.sumOf { it.debtAmountSar }

        val periodPayments = paymentsList.filter { Helpers.matchesPeriod(it.date, selectedReportPeriod, customDebtReportValue, customDebtReportType) }
        val periodPaymentsCollected = periodPayments.sumOf { it.amount }
        val periodPaymentsCollectedYer = periodPayments.filter { it.currency.contains("يمني") }.sumOf { it.amount }
        val periodPaymentsCollectedSar = periodPayments.filter { it.currency.contains("سعودي") }.sumOf { it.amount }

        val totalDebtsOutstanding = customersList.sumOf { it.totalDebt }
        val totalDebtsOutstandingYer = invoicesList.sumOf { it.totalAmountYer } - 
                                      (paymentsList.filter { it.currency.contains("يمني") }.sumOf { it.amount } + invoicesList.sumOf { it.paidAmountYer })

        val totalDebtsOutstandingSar = invoicesList.sumOf { it.totalAmountSar } - 
                                      (paymentsList.filter { it.currency.contains("سعودي") }.sumOf { it.amount } + invoicesList.sumOf { it.paidAmountSar })

        AlertDialog(
            onDismissRequest = { showDebtReportsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تقارير حسابات الديون والتحصيلات",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "اختر دورة أو فترة التقرير لتجهيز إحصائيات الديون وحركات الدفع أو حدد تقويماً مخصصاً:",
                        color = PalWhiteMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right
                    )

                    // Period Selection Toggles
                    val periods = listOf("يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        periods.forEach { period ->
                            val isSel = selectedReportPeriod == period
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedPeriod = period
                                        if (period == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                                            customPeriodValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = period,
                                    color = fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (selectedReportPeriod == "تقويم مخصص" || selectedReportPeriod == "أسبوعي") {
                        UnifiedPeriodSelector(
                            initialType = if (selectedReportPeriod == "أسبوعي") "أسبوع" else customPeriodType,
                            initialValue = if (selectedReportPeriod == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                                "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                            } else {
                                customPeriodValue
                            },
                            onPeriodChanged = { type, finalVal ->
                                customPeriodType = type
                                customPeriodValue = finalVal
                                customerGeneralReportDate = Helpers.formatCustomPeriodToArabic(type, finalVal)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = customerGeneralReportDate,
                        onValueChange = { customerGeneralReportDate = it },
                        label = { Text("تعيين وتخصيص تاريخ التقرير المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("customer_general_report_date_input"),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )

                    HorizontalDivider(color = PalBorderColor)

                    // Display Calculations
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("الحسابات بالريال اليمني:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الديون الجديدة المقيدة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(periodNewDebtYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المتحصلات والمسدد نقداً:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(periodPaymentsCollectedYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        val netChangeYer = periodNewDebtYer - periodPaymentsCollectedYer
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("صافي التغير بالمديونية:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            val colorChange = if (netChangeYer >= 0) PalRedNormal else PalSuccessNormal
                            val sign = if (netChangeYer >= 0) "+" else ""
                            Text("$sign${Helpers.formatWithCurrency(netChangeYer, "ريال يمني")}", color = colorChange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }

                        HorizontalDivider(color = PalBorderColor)

                        Text("الحسابات بالريال السعودي:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الديون الجديدة المقيدة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(periodNewDebtSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المتحصلات والمسدد نقداً:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(periodPaymentsCollectedSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        }
                        val netChangeSar = periodNewDebtSar - periodPaymentsCollectedSar
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("صافي التغير بالمديونية:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            val colorChange = if (netChangeSar >= 0) PalRedNormal else PalSuccessNormal
                            val sign = if (netChangeSar >= 0) "+" else ""
                            Text("$sign${Helpers.formatWithCurrency(netChangeSar, "ريال سعودي")}", color = colorChange, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "كشف مفصل بالعملاء والتواريخ ($selectedReportPeriod):",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (periodInvoices.isEmpty() && periodPayments.isEmpty()) {
                            Text(
                                text = "لا توجد حركات ديون أو سداد مالي مسجلة في هذه الفترة.",
                                color = PalWhiteMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            if (periodInvoices.isNotEmpty()) {
                                Text("فواتير ديون جديدة:", color = PalRedNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                periodInvoices.forEach { inv ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("${inv.customerName}: +${Helpers.formatWithCurrency(inv.debtAmount, inv.currency)}", color = PalWhitePure, fontSize = 10.sp)
                                        Text(inv.date.split(" ")[0], color = PalWhiteMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                            if (periodPayments.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("دفعات وتحصيلات المستلمين:", color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                periodPayments.forEach { pay ->
                                    val custName = customersList.find { it.id == pay.customerId }?.name ?: "عميل"
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("$custName: -${Helpers.formatWithCurrency(pay.amount, pay.currency)}", color = PalWhitePure, fontSize = 10.sp)
                                        Text(pay.date.split(" ")[0], color = PalWhiteMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val reportMsg = buildString {
                        appendLine(com.example.util.BrandingManager.buildShareHeader("تقرير حسابات الديون والتحصيلات التفصيلي"))
                        appendLine("فترة التقرير: $selectedReportPeriod")
                        appendLine("التاريخ والوقت: $customerGeneralReportDate")
                        appendLine("===============================")
                        if (periodInvoices.isNotEmpty()) {
                            appendLine("فواتير الديون الجديدة بالتفصيل للعملاء:")
                            periodInvoices.forEachIndexed { i, it ->
                                appendLine("${i+1}) العميل: ${it.customerName} | تاريخ: ${it.date} | دين جديد بقيمة: ${Helpers.formatWithCurrency(it.debtAmount, it.currency)}")
                            }
                            appendLine("===============================")
                        }
                        if (periodPayments.isNotEmpty()) {
                            appendLine("سجل التحصيلات والقبض نقداً:")
                            periodPayments.forEachIndexed { i, p ->
                                val name = customersList.find { it.id == p.customerId }?.name ?: "عميل"
                                appendLine("${i+1}) العميل: $name | تاريخ: ${p.date} | سدد نقداً: ${Helpers.formatWithCurrency(p.amount, p.currency)} | البيان: ${p.notes.ifEmpty { "مسدد الديون" }}")
                            }
                            appendLine("===============================")
                        }
                        appendLine("الخلاصة بالريال اليمني:")
                        appendLine("- إجمالي الديون الجديدة: ${Helpers.formatWithCurrency(periodNewDebtYer, "ريال يمني")}")
                        appendLine("- إجمالي التحصيلات: ${Helpers.formatWithCurrency(periodPaymentsCollectedYer, "ريال يمني")}")
                        val changeYer = periodNewDebtYer - periodPaymentsCollectedYer
                        appendLine("- صافي التغير: ${if (changeYer >= 0) "زيادة" else "تراجع"} قدره ${Helpers.formatWithCurrency(kotlin.math.abs(changeYer), "ريال يمني")}")
                        appendLine("-------------------------")
                        appendLine("الخلاصة بالريال السعودي:")
                        appendLine("- إجمالي الديون الجديدة: ${Helpers.formatWithCurrency(periodNewDebtSar, "ريال سعودي")}")
                        appendLine("- إجمالي التحصيلات: ${Helpers.formatWithCurrency(periodPaymentsCollectedSar, "ريال سعودي")}")
                        val changeSar = periodNewDebtSar - periodPaymentsCollectedSar
                        appendLine("- صافي التغير: ${if (changeSar >= 0) "زيادة" else "تراجع"} قدره ${Helpers.formatWithCurrency(kotlin.math.abs(changeSar), "ريال سعودي")}")
                        appendLine("===============================")
                        appendLine("إجمالي الذمم والديون المعلقة حالياً بطرفهم:")
                        appendLine("- يمني: ${Helpers.formatWithCurrency(totalDebtsOutstandingYer, "ريال يمني")}")
                        appendLine("- سعودي: ${Helpers.formatWithCurrency(totalDebtsOutstandingSar, "ريال سعودي")}")
                        appendLine(com.example.util.BrandingManager.buildShareFooter())
                    }

                    OutlinedButton(
                        onClick = {
                            val debtors = customersList.filter { it.totalDebt > 0 }
                            if (debtors.isEmpty()) {
                                android.widget.Toast.makeText(context, "لا يوجد أي عملاء مديونين حالياً لتذكيرهم!", android.widget.Toast.LENGTH_SHORT).show()
                            } else {
                                val bAgencyTitle = com.example.util.BrandingManager.resolvedAgencyName()
                                val bOwnerName = com.example.util.BrandingManager.resolvedOwnerName()
                                val bPhone = com.example.util.BrandingManager.phone.trim()
                                val reminderMessage = buildString {
                                    appendLine("السلام عليكم ورحمة الله وبركاته، الأخ العزيز والعميل الأكرم.")
                                    appendLine("تحية طيبة محملة بالتقدير والاحترام مبعوثة لكم من $bAgencyTitle.")
                                    appendLine()
                                    appendLine("نود تذكيركم بلطف وعناية بمراجعة حسابكم الجاري لدينا، والمبادرة الطيبة بسداد المديونية المتبقية بطرفكم بأقرب وقت متاح لكم، وذلك لمساعدتنا في تسيير المعاملات اليومية والحسابات بانتظام.")
                                    appendLine()
                                    append("نشكركم جزيل الشكر والتقدير ولكم منا كل الامتنان لحسن تفهمكم وتعاونكم الدائم معنا.")
                                    if (bOwnerName.isNotEmpty()) {
                                        appendLine()
                                        append("إدارة الوكالة / $bOwnerName")
                                    }
                                    if (bPhone.isNotEmpty()) {
                                        appendLine()
                                        append("للتواصل: $bPhone")
                                    }
                                }
                                val phones = debtors.map { it.phone }
                                Helpers.shareGroupSMS(context, phones, reminderMessage)
                                android.widget.Toast.makeText(context, "تم فتح تطبيق الرسائل لإرسال التذكير لـ ${debtors.size} عملاء مديونين", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                        border = BorderStroke(1.dp, PalBorderColor),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Icon(Icons.Default.Sms, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إرسال رسائل تذكير جماعية SMS للمديونين", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val tPrefix = when (selectedReportPeriod) {
                                    "يومي" -> "تقرير يومي"
                                    "أسبوعي" -> "تقرير أسبوعي"
                                    "شهري" -> "تقرير شهري"
                                    "سنوي" -> "تقرير سنوي"
                                    else -> "تقرير دوري"
                                }
                                val bAgencyTitle = com.example.util.BrandingManager.resolvedAgencyName()
                                val headers = listOf("التاريخ والوقت", "نوع العملية والحركة", "اسم العميل", "المبلغ المقيد")
                                val rows = mutableListOf<List<String>>()

                                periodInvoices.forEach { inv ->
                                    val debtValue = if (inv.debtAmountYer > 0.0 && inv.debtAmountSar > 0.0) {
                                        "يمني +${Helpers.formatWithCurrency(inv.debtAmountYer, "ريال يمني")}\nسعودي +${Helpers.formatWithCurrency(inv.debtAmountSar, "ريال سعودي")}"
                                    } else if (inv.debtAmountYer > 0.0) {
                                        "يمني +${Helpers.formatWithCurrency(inv.debtAmountYer, "ريال يمني")}"
                                    } else if (inv.debtAmountSar > 0.0) {
                                        "سعودي +${Helpers.formatWithCurrency(inv.debtAmountSar, "ريال سعودي")}"
                                    } else {
                                        "+${Helpers.formatWithCurrency(inv.debtAmount, inv.currency)}"
                                    }
                                    rows.add(listOf(
                                        inv.date,
                                        "دين جديد (آجل)",
                                        inv.customerName,
                                        debtValue
                                    ))
                                }
                                periodPayments.forEach { pay ->
                                    val name = customersList.find { it.id == pay.customerId }?.name ?: "عميل"
                                    val formattedAmt = if (pay.currency.contains("سعودي")) {
                                        "سعودي -${Helpers.formatWithCurrency(pay.amount, "ريال سعودي")}"
                                    } else {
                                        "يمني -${Helpers.formatWithCurrency(pay.amount, "ريال يمني")}"
                                    }
                                    rows.add(listOf(
                                        pay.date,
                                        "تحصيل وسداد نقدي",
                                        name,
                                        formattedAmt
                                    ))
                                }
                                if (rows.isEmpty()) {
                                    rows.add(listOf("-", "لا توجد معاملات مالية", "-", "0"))
                                }

                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "$tPrefix ديون وتحصيلات $bAgencyTitle",
                                    headers = headers,
                                    rows = rows,
                                    totals = mapOf(
                                        "إجمالي ديون جديدة (ريال يمني)" to Helpers.formatWithCurrency(periodNewDebtYer, "ريال يمني"),
                                        "إجمالي ديون جديدة (ريال سعودي)" to Helpers.formatWithCurrency(periodNewDebtSar, "ريال سعودي"),
                                        "إجمالي تحصيلات (ريال يمني)" to Helpers.formatWithCurrency(periodPaymentsCollectedYer, "ريال يمني"),
                                        "إجمالي تحصيلات (ريال سعودي)" to Helpers.formatWithCurrency(periodPaymentsCollectedSar, "ريال سعودي"),
                                        "إجمالي مديونية معلقة (ريال يمني)" to Helpers.formatWithCurrency(totalDebtsOutstandingYer, "ريال يمني"),
                                        "إجمالي مديونية معلقة (ريال سعودي)" to Helpers.formatWithCurrency(totalDebtsOutstandingSar, "ريال سعودي")
                                    ),
                                    customDate = Helpers.getCurrentDateTime(),
                                    reportPeriod = customerGeneralReportDate
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                        ) {
                            Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                Helpers.shareViaWhatsApp(context, "", reportMsg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                        ) {
                            Text("واتساب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                bluetoothPrintText = reportMsg
                                showBluetoothPrintTrigger = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).height(46.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp)
                        ) {
                            Text("طباعة", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDebtReportsDialog = false }) {
                    Text("إغلاق", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showBluetoothPrintTrigger) {
        BluetoothPrintDialog(
            receiptText = bluetoothPrintText,
            onDismiss = { showBluetoothPrintTrigger = false }
        )
    }
}
