@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.InventoryItem
import com.example.data.SalesInvoice
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterManager
import com.example.util.Helpers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.ui.components.BluetoothPrintDialog
import com.example.ui.components.CurrencySelectionRow
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.components.AccountingTextField
import com.example.util.MoneyMath

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SalesScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val allInvoices by viewModel.allInvoices.collectAsState()
    val allInvoiceItems by viewModel.allInvoiceItems.collectAsState()
    val allProducts by viewModel.inventoryItems.collectAsState()
    val allCustomers by viewModel.allCustomers.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val todayTax by viewModel.todayTaxAmount.collectAsState()
    val rateVal by viewModel.sarToYerExchangeRate.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"

    var searchInvoiceQuery by remember { mutableStateOf("") }
    var showReportsDialog by remember { mutableStateOf(false) }
    var selectedReportPeriod by remember { mutableStateOf("يومي") } // يومي, أسبوعي, شهري, سنوي
    var customReportType by remember { mutableStateOf("يوم") }
    var customReportValue by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var customReportPrintDate by remember { mutableStateOf(Helpers.getCurrentDate()) }

    var isAddingInvoice by remember { mutableStateOf(false) }
    var selectedCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }

    // State for creating new invoice
    var customerNameInput by remember { mutableStateOf("") }
    var customerPhoneInput by remember { mutableStateOf("") } // Added customer phone for new customer creation
    val selectedItemsList = remember { mutableStateListOf<Pair<InventoryItem, Double>>() } // Item & Quantity sold
    var discountInput by remember { mutableStateOf("") }
    var paidAmountInput by remember { mutableStateOf("") }
    var paidAmountInputYer by remember { mutableStateOf("") }
    var paidAmountInputSar by remember { mutableStateOf("") }
    var invoicePaymentMethod by remember { mutableStateOf("نقداً") }
    var invoicePaymentMethodYer by remember { mutableStateOf("نقداً") }
    var invoicePaymentMethodSar by remember { mutableStateOf("نقداً") }
    
    // UI selections
    var showProductPicker by remember { mutableStateOf(false) }
    var showCustomerPicker by remember { mutableStateOf(false) }
    
    // Edit Invoice States
    var showEditInvoiceDialog by remember { mutableStateOf(false) }
    var editInvoiceCustomerName by remember { mutableStateOf("") }
    var editInvoiceDate by remember { mutableStateOf("") }
    var editInvoiceCurrency by remember { mutableStateOf("ريال يمني") }
    var editInvoiceTotalAmount by remember { mutableStateOf("") }
    var editInvoiceDiscount by remember { mutableStateOf("") }
    var editInvoiceTaxApplied by remember { mutableStateOf("") }
    var editInvoicePaidAmount by remember { mutableStateOf("") }
    var editInvoiceDebtAmount by remember { mutableStateOf("") }
    var editInvoicePaymentMethod by remember { mutableStateOf("نقداً") }
    var editInvoiceTotalYer by remember { mutableStateOf("") }
    var editInvoiceTotalSar by remember { mutableStateOf("") }
    var editInvoicePaidYer by remember { mutableStateOf("") }
    var editInvoicePaidSar by remember { mutableStateOf("") }
    var editInvoiceDebtYer by remember { mutableStateOf("") }
    var editInvoiceDebtSar by remember { mutableStateOf("") }
    var editInvoicePaymentMethodYer by remember { mutableStateOf("نقداً") }
    var editInvoicePaymentMethodSar by remember { mutableStateOf("نقداً") }
    
    // View Details dialog
    var viewingInvoice by remember { mutableStateOf<SalesInvoice?>(null) }
    var viewingInvoiceItems by remember { mutableStateOf<List<com.example.data.SalesInvoiceItem>>(emptyList()) }
    var showDetailsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewingInvoice) {
        viewingInvoice?.let { inv ->
            viewModel.getSaleItemsFlow(inv.id).collect { items ->
                viewingInvoiceItems = items
            }
        }
    }

    // Direct inputs for new customer's extra details
    var customerAddressInput by remember { mutableStateOf("") }
    var customerNotesInput by remember { mutableStateOf("") }

    // Void Invoice confirmation
    var invoiceToVoid by remember { mutableStateOf<SalesInvoice?>(null) }
    var showVoidConfirm by remember { mutableStateOf(false) }

    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }

    // Calculations for active sale creation
    val itemsSubtotalYer = selectedItemsList.filter { (it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("يمني") }.sumOf { it.first.sellPrice * it.second }
    val itemsSubtotalSar = selectedItemsList.filter { (it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي") }.sumOf { it.first.sellPrice * it.second }

    val finalTotalYer = itemsSubtotalYer
    val finalTotalSar = itemsSubtotalSar

    LaunchedEffect(invoicePaymentMethod) {
        if (itemsSubtotalYer > 0.0 && itemsSubtotalSar == 0.0) {
            invoicePaymentMethodYer = invoicePaymentMethod
        } else if (itemsSubtotalSar > 0.0 && itemsSubtotalYer == 0.0) {
            invoicePaymentMethodSar = invoicePaymentMethod
        }
    }

    LaunchedEffect(invoicePaymentMethodYer, finalTotalYer) {
        if (invoicePaymentMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
            paidAmountInputYer = if (finalTotalYer == 0.0) "" else finalTotalYer.toString()
        } else if (invoicePaymentMethodYer == "آجل") {
            paidAmountInputYer = "0"
        } else if (invoicePaymentMethodYer == "دفع جزئي") {
            if (paidAmountInputYer == finalTotalYer.toString() || paidAmountInputYer == "0" || paidAmountInputYer == "0.0") {
                paidAmountInputYer = ""
            }
        }
    }

    LaunchedEffect(invoicePaymentMethodSar, finalTotalSar) {
        if (invoicePaymentMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
            paidAmountInputSar = if (finalTotalSar == 0.0) "" else finalTotalSar.toString()
        } else if (invoicePaymentMethodSar == "آجل") {
            paidAmountInputSar = "0"
        } else if (invoicePaymentMethodSar == "دفع جزئي") {
            if (paidAmountInputSar == finalTotalSar.toString() || paidAmountInputSar == "0" || paidAmountInputSar == "0.0") {
                paidAmountInputSar = ""
            }
        }
    }

    val paidAmountYer = paidAmountInputYer.toDoubleOrNull() ?: 0.0
    val paidAmountSar = paidAmountInputSar.toDoubleOrNull() ?: 0.0

    val remainingDebtYer = if (finalTotalYer > paidAmountYer) finalTotalYer - paidAmountYer else 0.0
    val remainingDebtSar = if (finalTotalSar > paidAmountSar) finalTotalSar - paidAmountSar else 0.0

    // Backward compatibility for single-currency flows
    val itemsSubtotal = selectedItemsList.sumOf { it.first.sellPrice * it.second }
    val finalTotal = itemsSubtotal

    LaunchedEffect(invoicePaymentMethod, finalTotal) {
        if (invoicePaymentMethod in listOf("نقداً", "تحويل", "إيداع")) {
            paidAmountInput = if (finalTotal == 0.0) "" else finalTotal.toString()
        } else if (invoicePaymentMethod == "آجل") {
            paidAmountInput = "0"
        } else if (invoicePaymentMethod == "دفع جزئي") {
            if (paidAmountInput == finalTotal.toString() || paidAmountInput == "0" || paidAmountInput == "0.0") {
                paidAmountInput = ""
            }
        }
    }

    val paidAmount = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) (paidAmountYer + paidAmountSar) else (paidAmountInput.toDoubleOrNull() ?: 0.0)
    val remainingDebt = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) (remainingDebtYer + remainingDebtSar) else (if (finalTotal > paidAmount) finalTotal - paidAmount else 0.0)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AgencyAppBarTitle(if (isAddingInvoice) "إنشاء فاتورة جديدة" else "إدارة المبيعات والفواتير") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isAddingInvoice) {
                            isAddingInvoice = false
                        } else {
                            viewModel.navigateBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PalIconUnified)
                    }
                },
                actions = {
                    if (!isAddingInvoice) {
                        IconButton(onClick = { showReportsDialog = true }) {
                            Icon(Icons.Default.Summarize, contentDescription = "تقارير المبيعات", tint = PalIconUnified)
                        }
                        IconButton(onClick = {
                            isAddingInvoice = true
                            customerNameInput = ""
                            customerPhoneInput = ""
                            customerAddressInput = ""
                            customerNotesInput = ""
                            selectedItemsList.clear()
                            discountInput = "0"
                            paidAmountInput = ""
                            selectedCurrency = currencyConfig.resolvedDefaultCurrency
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "جديد", tint = PalIconUnified)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
            )
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        if (isAddingInvoice) {
            // New design layout for making a sale
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Customer selection
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Box(modifier = Modifier.weight(1f)) {
                                val isCash = invoicePaymentMethod == "نقداً"
                                val hintText = if (isCash) "اسم العميل (اختياري للبيع النقدي)" else "أدخل اسم العميل (أو انقر اختيار لتحديد عميل مسجل)"
                                AccountingTextField(
                                    value = customerNameInput,
                                    onValueChange = { customerNameInput = it },
                                    label = "اسم العميل المشتري",
                                    placeholder = hintText
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = { showCustomerPicker = true },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint),
                                border = BorderStroke(1.dp, PalBorderColor),
                                modifier = Modifier.height(52.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("إختيار عميل", color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }

                        val isCustomerNew = customerNameInput.trim().isNotEmpty() && allCustomers.none { it.name.trim().equals(customerNameInput.trim(), ignoreCase = true) }
                        AnimatedVisibility(visible = isCustomerNew) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Spacer(modifier = Modifier.height(4.dp))
                                AccountingTextField(
                                    value = customerPhoneInput,
                                    onValueChange = { customerPhoneInput = it },
                                    label = "رقم هاتف العميل الجديد",
                                    placeholder = "رقم هاتف العميل الجديد (تلقائي الحفظ)",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                                )
                                AccountingTextField(
                                    value = customerAddressInput,
                                    onValueChange = { customerAddressInput = it },
                                    label = "عنوان العميل السكني/التجاري",
                                    placeholder = "عنوان العميل الجديد السكني/التجاري (اختياري)"
                                )
                                AccountingTextField(
                                    value = customerNotesInput,
                                    onValueChange = { customerNotesInput = it },
                                    label = "ملاحظات عن العميل الجديد",
                                    placeholder = "ملاحظات خاصة ومميزة عن العميل الجديد (اختياري)",
                                    singleLine = false
                                )
                            }
                        }
                    }
                }

                // Selected Products table
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("أصناف الفاتورة", color = PalWhitePure, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Button(
                                onClick = { showProductPicker = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إضافة صنف", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (selectedItemsList.isEmpty()) {
                            Text(
                                "لم يتم إدراج أي مواد قات بعد بالفاتورة",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                        } else {
                            selectedItemsList.forEachIndexed { index, pair ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1.2f)) {
                                         val buyCurrency = pair.first.buyPriceCurrency.ifEmpty { "ريال يمني" }
                                         val isBuySar = buyCurrency.contains("سعودي")
                                         val activeSaleCurrency = if (pair.first.notes.startsWith("sale_currency:")) {
                                             pair.first.notes.substringAfter("sale_currency:").substringBefore("|")
                                         } else {
                                             buyCurrency
                                         }
                                         val isSaleSar = activeSaleCurrency.contains("سعودي")
                                         val itemCurrencyTag = if (isSaleSar) "(سعودي)" else "(يمني)"
                                         
                                         Text("${pair.first.name} $itemCurrencyTag", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                         Text(
                                             text = "الأصل: " + (if (isBuySar) "سعودي" else "يمني") + " • المخزون: ${com.example.util.Helpers.formatQuantity(pair.first.quantity)} حبة",
                                             color = PalWhiteMuted,
                                             fontSize = 10.sp
                                         )
                                         
                                         // Interactive switcher chip
                                         Box(
                                             modifier = Modifier
                                                 .padding(top = 4.dp)
                                                 .clip(RoundedCornerShape(6.dp))
                                                 .background(PalSoftGreenTint)
                                                 .border(1.dp, PalBorderColor, RoundedCornerShape(6.dp))
                                                 .clickable {
                                                     val newSaleCurrency = if (isSaleSar) "ريال يمني" else "ريال سعودي"
                                                     val oldPrice = pair.first.sellPrice
                                                     val newPrice = if (isSaleSar) {
                                                         oldPrice * rateVal
                                                     } else {
                                                         if (rateVal > 0) oldPrice / rateVal else oldPrice
                                                     }
                                                     
                                                     selectedItemsList[index] = pair.first.copy(
                                                         notes = "sale_currency:$newSaleCurrency",
                                                         sellPrice = newPrice
                                                     ) to pair.second
                                                 }
                                                 .padding(horizontal = 6.dp, vertical = 3.dp)
                                         ) {
                                             Text(
                                                 text = "تبديل العملة: " + if (isSaleSar) "ريال سعودي" else "ريال يمني",
                                                 color = PalIconUnified,
                                                 fontSize = 9.sp,
                                                 fontWeight = FontWeight.Bold
                                             )
                                         }
                                    }
                                    
                                    // Quantity management (+/- buttons)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1.0f),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        IconButton(onClick = {
                                            if (pair.second > 0.5001) {
                                                selectedItemsList[index] = pair.first to (pair.second - 0.5)
                                            } else {
                                                selectedItemsList.removeAt(index)
                                            }
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Remove, contentDescription = "إنقاص الكمية", tint = PalRedNormal, modifier = Modifier.size(16.dp))
                                        }
                                        Text(com.example.util.Helpers.formatQuantity(pair.second), color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 2.dp))
                                        IconButton(onClick = {
                                            val currentItemStock = pair.first.quantity
                                            if (pair.second + 0.5 <= currentItemStock + 0.0001) {
                                                selectedItemsList[index] = pair.first to (pair.second + 0.5)
                                            } else {
                                                android.widget.Toast.makeText(context, "الكمية المحددة تخطت المخزون المتاح (${com.example.util.Helpers.formatQuantity(currentItemStock)})!", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Default.Add, contentDescription = "زيادة الكمية", tint = PalIconUnified, modifier = Modifier.size(16.dp))
                                        }
                                    }

                                    // Unit Sale Price dynamic input field
                                    var sellPriceInputText by remember(pair.first.sellPrice) { 
                                        mutableStateOf(if (pair.first.sellPrice == 0.0) "" else if (pair.first.sellPrice % 1.0 == 0.0) pair.first.sellPrice.toInt().toString() else pair.first.sellPrice.toString()) 
                                    }
                                    val isItemSar = if (pair.first.notes.startsWith("sale_currency:")) pair.first.notes.substringAfter("sale_currency:").substringBefore("|").contains("سعودي") else (pair.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي")
                                    val itemCurrencyLabel = if (isItemSar) "ريال سعودي" else "ريال يمني"
                                    Column(
                                        modifier = Modifier.weight(1.5f).padding(horizontal = 2.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = itemCurrencyLabel,
                                            color = PalWhiteMuted,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(bottom = 2.dp)
                                        )
                                        androidx.compose.foundation.text.BasicTextField(
                                            value = sellPriceInputText,
                                            onValueChange = { input ->
                                                val clean = input.filter { it.isDigit() || it == '.' }
                                                sellPriceInputText = clean
                                                val price = clean.toDoubleOrNull() ?: 0.0
                                                selectedItemsList[index] = pair.first.copy(sellPrice = price) to pair.second
                                            },
                                            textStyle = androidx.compose.ui.text.TextStyle(
                                                color = PalWhitePure,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            ),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            cursorBrush = androidx.compose.ui.graphics.SolidColor(PalIconUnified),
                                            decorationBox = { innerTextField ->
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(38.dp)
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(PalBlackDark)
                                                        .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                                        .padding(horizontal = 4.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    if (sellPriceInputText.isEmpty()) {
                                                        Text("السعر", color = PalWhiteMuted, fontSize = 10.sp)
                                                    }
                                                    innerTextField()
                                                }
                                            }
                                        )
                                    }

                                    // Total for this item
                                    Text(
                                        Helpers.formatWithCurrency(pair.first.sellPrice * pair.second, if (isItemSar) "ريال سعودي" else "ريال يمني"),
                                        color = PalWhitePure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.weight(1.0f),
                                        textAlign = TextAlign.End
                                    )
                                }
                                HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)
                            }
                        }
                    }
                }

                // Invoicing Financial Box (Discount, Tax, Totals)
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("التحاسب المالي", color = PalWhitePure, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        if (itemsSubtotalYer > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المجموع الفرعي بالريال اليمني:", color = PalWhiteSoft, fontSize = 13.sp)
                                Text(Helpers.formatWithCurrency(itemsSubtotalYer, "ريال يمني"), color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (itemsSubtotalSar > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المجموع الفرعي بالريال السعودي:", color = PalWhiteSoft, fontSize = 13.sp)
                                Text(Helpers.formatWithCurrency(itemsSubtotalSar, "ريال سعودي"), color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (itemsSubtotalYer == 0.0 && itemsSubtotalSar == 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("المجموع الفرعي للأصناف:", color = PalWhiteSoft, fontSize = 13.sp)
                                Text(Helpers.formatMoney(itemsSubtotal), color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("عدد الأصناف المدرجة بالفاتورة:", color = PalWhiteSoft, fontSize = 13.sp)
                            Text("${selectedItemsList.size} أصناف", color = PalWhitePure, fontWeight = FontWeight.Bold)
                        }

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الكميات المباعة:", color = PalWhiteSoft, fontSize = 13.sp)
                            Text("${com.example.util.Helpers.formatQuantity(selectedItemsList.sumOf { it.second })} حبة", color = PalWhitePure, fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                         if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) {
                             // Dual Currency Flow: Separate billing and payment methods independently
                             Text("حساب الجزء بالريال اليمني:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                             val salePaymentMethods = listOf("نقداً", "تحويل", "إيداع", "آجل", "دفع جزئي")
                             Row(
                                 modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 salePaymentMethods.forEach { method ->
                                     val isSel = invoicePaymentMethodYer == method
                                     val bg = if (isSel) PalGreenNormal else PalBlackLight
                                     val fg = if (isSel) Color.White else PalWhiteMuted
                                     Box(
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(8.dp))
                                             .background(bg)
                                             .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                             .clickable { invoicePaymentMethodYer = method }
                                             .padding(horizontal = 12.dp, vertical = 8.dp)
                                     ) {
                                         Text(text = method, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     }
                                 }
                             }

                             if (invoicePaymentMethodYer == "دفع جزئي") {
                                 OutlinedTextField(
                                     value = paidAmountInputYer,
                                     onValueChange = { paidAmountInputYer = it },
                                     label = { Text("المبلغ المدفوع بالريال اليمني *", color = PalWhiteMuted, fontSize = 11.sp) },
                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                     colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PalIconUnified, unfocusedBorderColor = PalBorderColor, focusedTextColor = PalWhitePure, unfocusedTextColor = PalWhitePure),
                                     singleLine = true,
                                     modifier = Modifier.fillMaxWidth()
                                 )
                             } else {
                                 val labelYer = when (invoicePaymentMethodYer) {
                                     "آجل" -> "المبلغ المدفوع باليمني: 0 (مؤجل)"
                                     else -> "المبلغ المدفوع باليمني: $finalTotalYer (مسدد بالكامل)"
                                 }
                                 OutlinedTextField(
                                     value = if (invoicePaymentMethodYer == "آجل") "0" else finalTotalYer.toString(),
                                     onValueChange = {},
                                     enabled = false,
                                     label = { Text(labelYer, color = PalWhiteMuted, fontSize = 11.sp) },
                                     colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = PalBorderColor, disabledTextColor = PalWhiteMuted),
                                     singleLine = true,
                                     modifier = Modifier.fillMaxWidth()
                                 )
                             }

                             Spacer(modifier = Modifier.height(6.dp))

                             Text("حساب الجزء بالريال السعودي:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                             Row(
                                 modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 salePaymentMethods.forEach { method ->
                                     val isSel = invoicePaymentMethodSar == method
                                     val bg = if (isSel) PalGreenNormal else PalBlackLight
                                     val fg = if (isSel) Color.White else PalWhiteMuted
                                     Box(
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(8.dp))
                                             .background(bg)
                                             .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                             .clickable { invoicePaymentMethodSar = method }
                                             .padding(horizontal = 12.dp, vertical = 8.dp)
                                     ) {
                                         Text(text = method, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     }
                                 }
                             }

                             if (invoicePaymentMethodSar == "دفع جزئي") {
                                 OutlinedTextField(
                                     value = paidAmountInputSar,
                                     onValueChange = { paidAmountInputSar = it },
                                     label = { Text("المبلغ المدفوع بالريال السعودي *", color = PalWhiteMuted, fontSize = 11.sp) },
                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                     colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PalIconUnified, unfocusedBorderColor = PalBorderColor, focusedTextColor = PalWhitePure, unfocusedTextColor = PalWhitePure),
                                     singleLine = true,
                                     modifier = Modifier.fillMaxWidth()
                                 )
                             } else {
                                 val labelSar = when (invoicePaymentMethodSar) {
                                     "آجل" -> "المبلغ المدفوع بالسعودي: 0 (مؤجل)"
                                     else -> "المبلغ المدفوع بالسعودي: $finalTotalSar (مسدد بالكامل)"
                                 }
                                 OutlinedTextField(
                                     value = if (invoicePaymentMethodSar == "آجل") "0" else finalTotalSar.toString(),
                                     onValueChange = {},
                                     enabled = false,
                                     label = { Text(labelSar, color = PalWhiteMuted, fontSize = 11.sp) },
                                     colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = PalBorderColor, disabledTextColor = PalWhiteMuted),
                                     singleLine = true,
                                     modifier = Modifier.fillMaxWidth()
                                 )
                             }
                         } else {
                             // Single Currency Flow: Consistent classic layout
                             Text("طريقة التحصيل والدفع الكلي:", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                             val salePaymentMethods = listOf("نقداً", "تحويل", "إيداع", "آجل", "دفع جزئي")
                             Row(
                                 modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                 horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                 salePaymentMethods.forEach { method ->
                                     val isSel = invoicePaymentMethod == method
                                     val bg = if (isSel) PalGreenNormal else PalBlackLight
                                     val fg = if (isSel) Color.White else PalWhiteMuted
                                     Box(
                                         modifier = Modifier
                                             .clip(RoundedCornerShape(8.dp))
                                             .background(bg)
                                             .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                             .clickable { invoicePaymentMethod = method }
                                             .padding(horizontal = 12.dp, vertical = 8.dp)
                                     ) {
                                         Text(text = method, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                     }
                                 }
                             }

                             val isPaidAmountEditable = invoicePaymentMethod == "دفع جزئي"
                             if (isPaidAmountEditable) {
                                 OutlinedTextField(
                                     value = paidAmountInput,
                                     onValueChange = { paidAmountInput = it },
                                     label = { Text("المبلغ المدفوع (${if (itemsSubtotalSar > 0.0) "ريال سعودي" else "ريال يمني"}) *", color = PalWhiteMuted, fontSize = 11.sp) },
                                     keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                     colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = PalIconUnified, unfocusedBorderColor = PalBorderColor, focusedTextColor = PalWhitePure, unfocusedTextColor = PalWhitePure),
                                     singleLine = true,
                                     modifier = Modifier.fillMaxWidth()
                                 )
                             } else {
                                 val activeCurrLabel = if (itemsSubtotalSar > 0.0) "ريال سعودي" else "ريال يمني"
                                 val placeholderLabel = when (invoicePaymentMethod) {
                                     "آجل" -> "المبلغ المدفوع بقيمة 0 (شراء بالكامل بالآجل)"
                                     else -> "المبلغ المدفوع بالكامل (معاملة مدفوعة بالكامل)"
                                 }
                                 OutlinedTextField(
                                     value = if (invoicePaymentMethod == "آجل") "0" else finalTotal.toString(),
                                     onValueChange = {},
                                     enabled = false,
                                     label = { Text("$placeholderLabel ($activeCurrLabel)", color = PalWhiteMuted, fontSize = 11.sp) },
                                     colors = OutlinedTextFieldDefaults.colors(
                                         disabledBorderColor = PalBorderColor,
                                         disabledTextColor = PalWhiteMuted
                                     ),
                                     singleLine = true,
                                     modifier = Modifier.fillMaxWidth()
                                 )
                             }
                         }

                        HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                        CurrencySelectionRow(
                            selectedCurrency = selectedCurrency,
                            currencyConfig = currencyConfig,
                            onCurrencySelected = { selectedCurrency = it },
                            title = "عملة الفاتورة المعتمدة:"
                        )

                        HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp)

                        if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الفاتورة (يمني):", color = PalWhiteSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(Helpers.formatWithCurrency(finalTotalYer, "ريال يمني"), color = PalGreenLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الفاتورة (سعودي):", color = PalWhiteSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(Helpers.formatWithCurrency(finalTotalSar, "ريال سعودي"), color = PalGreenLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الآجل المتبقي (يمني):", color = PalWhiteSoft, fontSize = 13.sp)
                                Text(Helpers.formatWithCurrency(remainingDebtYer, "ريال يمني"), color = if (remainingDebtYer > 0) PalRedLight else PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الآجل المتبقي (سعودي):", color = PalWhiteSoft, fontSize = 13.sp)
                                Text(Helpers.formatWithCurrency(remainingDebtSar, "ريال سعودي"), color = if (remainingDebtSar > 0) PalRedLight else PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val activeCurrLabel = if (itemsSubtotalSar > 0.0) "ريال سعودي" else "ريال يمني"
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي قيمة الفاتورة:", color = PalWhiteSoft, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(Helpers.formatWithCurrency(finalTotal, activeCurrLabel), color = PalGreenLight, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الآجل المتبقي (دين على العميل):", color = PalWhiteSoft, fontSize = 13.sp)
                                Text(Helpers.formatWithCurrency(remainingDebt, activeCurrLabel), color = if (remainingDebt > 0) PalRedLight else PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Create Action Button
                Button(
                    onClick = {
                        val isDebtYer = finalTotalYer > paidAmountYer || invoicePaymentMethodYer == "آجل" || invoicePaymentMethodYer == "دفع جزئي"
                        val isDebtSar = finalTotalSar > paidAmountSar || invoicePaymentMethodSar == "آجل" || invoicePaymentMethodSar == "دفع جزئي"
                        val isDebt = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) {
                            isDebtYer || isDebtSar
                        } else {
                            (finalTotal > paidAmount) || invoicePaymentMethod == "آجل" || invoicePaymentMethod == "دفع جزئي"
                        }
                        val finalCustomerName = customerNameInput.trim().ifEmpty { "عميل نقدي" }

                        // Validate customer name based on debt
                        if (!isDebt) {
                            // Paid in full: can save without customer name.
                        } else {
                            // Partial payment or credit (آجل): requires real customer name.
                            if (customerNameInput.trim().isEmpty()) {
                                android.widget.Toast.makeText(context, "البيع بالآجل أو الدفع الجزئي يتطلب تحديد اسم العميل لإثبات المديونية!", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (finalCustomerName == "عميل نقدي") {
                                android.widget.Toast.makeText(context, "معاملات الذمم والآجل والدفع الجزئي لا يمكن قيدها للعميل النقدي العام!", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                        }

                        if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) {
                            if (invoicePaymentMethodYer == "دفع جزئي" && paidAmountYer <= 0.0) {
                                android.widget.Toast.makeText(context, "المبلغ المدفوع باليمني يجب أن يكون أكبر من الصفر في الدفع الجزئي!", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            if (invoicePaymentMethodSar == "دفع جزئي" && paidAmountSar <= 0.0) {
                                android.widget.Toast.makeText(context, "المبلغ المدفوع بالسعودي يجب أن يكون أكبر من الصفر في الدفع الجزئي!", android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                        } else {
                            if (invoicePaymentMethod == "دفع جزئي") {
                                if (paidAmount <= 0.0) {
                                    android.widget.Toast.makeText(context, "المبلغ المدفوع يجب أن يكون أكبر من الصفر في الدفع الجزئي! أو حدد البيع بالآجل كحساب كلي.", android.widget.Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                if (paidAmount >= finalTotal) {
                                    android.widget.Toast.makeText(context, "المبلغ المدفوع يجب أن يكون أقل من إجمالي الفاتورة في الدفع الجزئي! أو حدد خيار الدفع نقداً.", android.widget.Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                            }
                        }

                        if (selectedItemsList.isEmpty()) {
                            android.widget.Toast.makeText(context, "الرجاء تحديد الأطواق وإدراج صنف مبيعات واحد على الأقل!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!currencyConfig.isSingleCurrencyAllowed(selectedCurrency)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(selectedCurrency), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        
                        // Save Sale action
                        viewModel.executeSale(
                            customerName = finalCustomerName,
                            customerPhone = customerPhoneInput.trim(),
                            customerAddress = customerAddressInput.trim(),
                            customerNotes = customerNotesInput.trim(),
                            items = selectedItemsList.toList(),
                            discount = 0.0,
                            paidAmount = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) (paidAmountYer + paidAmountSar) else paidAmount,
                            paymentMethod = invoicePaymentMethod,
                            currency = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) "ريال يمني / ريال سعودي" else if (itemsSubtotalSar > 0.0) "ريال سعودي" else "ريال يمني",
                            paidAmountYer = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) paidAmountYer else if (itemsSubtotalYer > 0.0) paidAmount else 0.0,
                            paidAmountSar = if (itemsSubtotalYer > 0.0 && itemsSubtotalSar > 0.0) paidAmountSar else if (itemsSubtotalSar > 0.0) paidAmount else 0.0,
                            paymentMethodYer = invoicePaymentMethodYer,
                            paymentMethodSar = invoicePaymentMethodSar,
                            onSuccess = { invoiceId ->
                                android.widget.Toast.makeText(context, "تم قيد الفاتورة وحفظ المعاملة وتحديث قيود الذمم والقات بنجاح", android.widget.Toast.LENGTH_LONG).show()
                                isAddingInvoice = false
                                customerNameInput = ""
                                customerPhoneInput = ""
                                customerAddressInput = ""
                                customerNotesInput = ""
                                paidAmountInputYer = ""
                                paidAmountInputSar = ""
                                paidAmountInput = ""
                                selectedItemsList.clear()
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text("حفظ وتسجيل عملية البيع", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // General invoices history list
            val periodFilteredInvoices = remember(allInvoices, selectedPeriod, customPeriodValue, customPeriodTypeMapped) {
                allInvoices.filter { inv ->
                    Helpers.matchesPeriod(inv.date, selectedPeriod, customPeriodValue, customPeriodTypeMapped)
                }.sortedBy { inv -> inv.id }
            }

            val filteredInvoices = periodFilteredInvoices.filter { inv ->
                val query = searchInvoiceQuery.trim()
                if (query.isEmpty()) return@filter true
                
                val cleanQuery = if (query.startsWith("#")) query.drop(1) else query
                if (cleanQuery.isNotEmpty() && cleanQuery.all { it.isDigit() }) {
                    return@filter inv.id == cleanQuery.toInt()
                }
                
                // Helper to normalize Arabic diacritics and letters for flawless matching
                fun normalizeArabic(text: String): String {
                    return text.trim()
                        .lowercase()
                        .replace("[ًٌٍَُِّْ]".toRegex(), "") // Remove diacritics
                        .replace("[أإآ]".toRegex(), "ا")   // Normalize Alefs to bare Alef
                        .replace("ة", "ه")                    // Normalize Teh Marbuta to Heh
                        .replace("ى", "ي")                    // Normalize Alef Maksura to Yeh
                }

                fun convertEasternDigits(text: String): String {
                    return text.map { ch ->
                        if (ch in '٠'..'٩') {
                            (ch - '٠' + '0'.code).toChar()
                        } else {
                            ch
                        }
                    }.joinToString("")
                }

                val keywords = query.split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .map { kw ->
                        val normalizedKwDigit = convertEasternDigits(kw)
                        normalizeArabic(normalizedKwDigit)
                    }

                if (keywords.isEmpty()) return@filter true

                // Build a list of searchable texts for this invoice
                val searchableTexts = mutableListOf<String>()
                searchableTexts.add("سند")
                searchableTexts.add("فاتورة")
                searchableTexts.add("مبيعات")
                searchableTexts.add(inv.id.toString())
                searchableTexts.add(inv.customerName)
                searchableTexts.add(inv.date)
                searchableTexts.add(inv.currency)
                
                val pm = inv.paymentMethod
                searchableTexts.add(pm)
                if (pm.contains("نقداً") || pm.contains("كاش")) {
                    searchableTexts.add("نقدا")
                    searchableTexts.add("كاش")
                }
                if (pm.contains("آجل") || pm.contains("دين")) {
                    searchableTexts.add("اجل")
                    searchableTexts.add("دين")
                }
                if (pm.contains("تحويل")) {
                    searchableTexts.add("تحويل")
                }
                if (pm.contains("إيداع") || pm.contains("كريمي")) {
                    searchableTexts.add("إيداع")
                    searchableTexts.add("كريمي")
                }

                searchableTexts.add(inv.paymentMethodYer)
                searchableTexts.add(inv.paymentMethodSar)
                
                // Add amounts
                searchableTexts.add(inv.totalAmount.toString())
                searchableTexts.add(inv.totalAmountYer.toString())
                searchableTexts.add(inv.totalAmountSar.toString())
                searchableTexts.add(inv.paidAmountYer.toString())
                searchableTexts.add(inv.paidAmountSar.toString())
                searchableTexts.add(inv.debtAmountYer.toString())
                searchableTexts.add(inv.debtAmountSar.toString())

                // Items inside this invoice
                val items = allInvoiceItems.filter { it.invoiceId == inv.id }
                items.forEach { item ->
                    searchableTexts.add(item.itemName)
                    searchableTexts.add(item.unitPrice.toString())
                    searchableTexts.add(item.quantity.toInt().toString())
                }

                // Customer extra info (phone, address, notes)
                val customer = allCustomers.find { it.name.trim().equals(inv.customerName.trim(), ignoreCase = true) }
                if (customer != null) {
                    searchableTexts.add(customer.phone)
                    searchableTexts.add(customer.address)
                    searchableTexts.add(customer.notes)
                }

                // Check if all keywords are matched by at least one of the searchable texts
                keywords.all { kw ->
                    searchableTexts.any { src ->
                        val srcNormal = normalizeArabic(convertEasternDigits(src))
                        src.contains(kw, ignoreCase = true) || srcNormal.contains(kw, ignoreCase = true) || srcNormal.replace("\\s+".toRegex(), "").contains(kw, ignoreCase = true)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Spacer(modifier = Modifier.height(4.dp))

                // Header with smart search hint
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "البحث في سجل الفواتير",
                        color = PalWhitePure,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "رقم السند، العميل، التاريخ، الدفع، المبلغ",
                        color = PalWhiteMuted,
                        fontSize = 10.sp
                    )
                }

                // Advanced Search Input
                OutlinedTextField(
                    value = searchInvoiceQuery,
                    onValueChange = { searchInvoiceQuery = it },
                    placeholder = { Text("ابحث برقم السند، العميل، التاريخ، المبلغ، طريقة الدفع...", color = PalWhiteMuted, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (searchInvoiceQuery.isNotEmpty()) {
                            IconButton(onClick = { searchInvoiceQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "مسح البحث", tint = PalRedNormal, modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalIconUnified,
                        unfocusedBorderColor = PalBorderColor,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhitePure,
                        focusedContainerColor = PalBlackNormal,
                        unfocusedContainerColor = PalBlackNormal
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Advanced Filter Chips to instant-search
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val filterChips = listOf("الكل", "نقداً", "دين آجل", "إيداع", "حوالة")
                    filterChips.forEach { label ->
                        val isSelected = if (label == "الكل") searchInvoiceQuery.isEmpty() else searchInvoiceQuery.equals(label, ignoreCase = true)
                        
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .border(
                                    width = 1.dp,
                                    color = if (isSelected) PalGreenNormal else PalBorderColor,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .background(
                                    color = if (isSelected) PalGreenNormal else PalBlackNormal,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    searchInvoiceQuery = if (label == "الكل") "" else label
                                }
                                .padding(horizontal = 14.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = label,
                                color = if (isSelected) Color.White else PalWhiteSoft,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                // Match counter
                if (filteredInvoices.isNotEmpty()) {
                    Text(
                        text = "عدد الفواتير المطابقة: ${filteredInvoices.size}",
                        color = PalWhiteMuted,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )
                }

                if (filteredInvoices.isEmpty()) {
                    val query = searchInvoiceQuery.trim()
                    val isNumericQuery = query.isNotEmpty() && (query.all { it.isDigit() } || (query.startsWith("#") && query.drop(1).all { it.isDigit() }))
                    val emptyLabel = when {
                        allInvoices.isEmpty() -> "لا يوجد سجل فواتير مسجل حالياً"
                        isNumericQuery -> "عذراً، الفاتورة رقم ($query) غير موجودة"
                        else -> "لا توجد نتائج بحث مطابقة"
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(emptyLabel, color = PalWhiteMuted, textAlign = TextAlign.Center)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredInvoices) { inv ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                shape = RoundedCornerShape(14.dp),
                                border = BorderStroke(1.dp, PalBorderColor),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewingInvoice = inv
                                        showDetailsDialog = true
                                    }
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("رقم السند: #${inv.id}", color = PalIconUnified, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(inv.date, color = PalWhiteMuted, fontSize = 11.sp)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    Text(
                                        text = "العميل: ${inv.customerName}",
                                        color = PalWhitePure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Display list of items inside an organized mini-table
                                    val items = allInvoiceItems.filter { it.invoiceId == inv.id }
                                    if (items.isNotEmpty()) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(PalBlackDark, RoundedCornerShape(10.dp))
                                                .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                                                .padding(10.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("الصنف المباع", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(2f), textAlign = TextAlign.Right)
                                                Text("الكمية", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                                Text("سعر الحبة", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                                                Text("الإجمالي", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Left)
                                            }
                                            HorizontalDivider(color = PalBorderColor, thickness = 0.8.dp, modifier = Modifier.padding(vertical = 4.dp))
                                            items.forEach { item ->
                                                val isItemSar = (item.currency.ifEmpty { "ريال يمني" }).contains("سعودي")
                                                val curShort = if (isItemSar) "(س)" else "(ي)"
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text("${item.itemName} $curShort", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(2f), textAlign = TextAlign.Right)
                                                    Text("${Helpers.formatQuantity(item.quantity)} حبة", color = PalWhiteSoft, fontSize = 11.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                                                    Text(Helpers.formatMoney(item.unitPrice), color = PalWhiteSoft, fontSize = 11.sp, modifier = Modifier.weight(1.2f), textAlign = TextAlign.Center)
                                                    Text(Helpers.formatWithCurrency(item.unitPrice * item.quantity, item.currency), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1.5f), textAlign = TextAlign.Left)
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(10.dp))
                                    }

                                    // Display payment methods
                                    val payMethodText = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                        "طريقة السداد: يمني (${inv.paymentMethodYer}) • سعودي (${inv.paymentMethodSar})"
                                    } else {
                                        "طريقة السداد: ${inv.paymentMethod}"
                                    }
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(bottom = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(Icons.Default.Payments, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(14.dp))
                                        Text(
                                            text = payMethodText,
                                            color = PalWhiteSoft,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                        HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text("ريال يمني (إجمالي/مدفوع/متبقي):", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${Helpers.formatMoney(inv.totalAmountYer)} / ${Helpers.formatMoney(inv.paidAmountYer)} / ${Helpers.formatMoney(inv.debtAmountYer)}",
                                                    color = PalWhitePure,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("ريال سعودي (إجمالي/مدفوع/متبقي):", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                Text(
                                                    "${Helpers.formatMoney(inv.totalAmountSar)} / ${Helpers.formatMoney(inv.paidAmountSar)} / ${Helpers.formatMoney(inv.debtAmountSar)}",
                                                    color = PalWhitePure,
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    } else {
                                        val dispTotal = if (inv.totalAmountSar > 0.0) inv.totalAmountSar else if (inv.totalAmountYer > 0.0) inv.totalAmountYer else inv.totalAmount
                                        val dispPaid = if (inv.totalAmountSar > 0.0) inv.paidAmountSar else if (inv.totalAmountYer > 0.0) inv.paidAmountYer else inv.paidAmount
                                        val dispDebt = if (inv.totalAmountSar > 0.0) inv.debtAmountSar else if (inv.totalAmountYer > 0.0) inv.debtAmountYer else inv.debtAmount
                                        val dispCurr = if (inv.totalAmountSar > 0.0 || inv.currency.contains("سعودي")) "ريال سعودي" else "ريال يمني"
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("إجمالي الفاتورة ($dispCurr)", color = PalWhiteMuted, fontSize = 10.sp)
                                                Text(Helpers.formatWithCurrency(dispTotal, dispCurr), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                            }

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("المدفوع نقداً", color = PalWhiteMuted, fontSize = 10.sp)
                                                Text(Helpers.formatWithCurrency(dispPaid, dispCurr), color = PalWhitePure, fontSize = 12.sp)
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("الآجل / المتبقي", color = PalWhiteMuted, fontSize = 10.sp)
                                                Text(
                                                    Helpers.formatWithCurrency(dispDebt, dispCurr),
                                                    color = if (dispDebt > 0) PalRedNormal else PalWhitePure,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                viewingInvoice = inv
                                                editInvoiceCustomerName = inv.customerName
                                                editInvoiceDate = inv.date
                                                editInvoiceCurrency = inv.currency
                                                editInvoiceTotalAmount = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                                    (inv.totalAmountYer + inv.totalAmountSar).toString()
                                                } else inv.totalAmount.toString()
                                                editInvoiceDiscount = inv.discount.toString()
                                                editInvoiceTaxApplied = inv.taxApplied.toString()
                                                editInvoicePaidAmount = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                                    (inv.paidAmountYer + inv.paidAmountSar).toString()
                                                } else inv.paidAmount.toString()
                                                editInvoiceDebtAmount = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                                    (inv.debtAmountYer + inv.debtAmountSar).toString()
                                                } else inv.debtAmount.toString()
                                                editInvoicePaymentMethod = inv.paymentMethod
                                                editInvoiceTotalYer = inv.totalAmountYer.toString()
                                                editInvoiceTotalSar = inv.totalAmountSar.toString()
                                                editInvoicePaidYer = inv.paidAmountYer.toString()
                                                editInvoicePaidSar = inv.paidAmountSar.toString()
                                                editInvoiceDebtYer = inv.debtAmountYer.toString()
                                                editInvoiceDebtSar = inv.debtAmountSar.toString()
                                                editInvoicePaymentMethodYer = inv.paymentMethodYer
                                                editInvoicePaymentMethodSar = inv.paymentMethodSar
                                                showEditInvoiceDialog = true
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "تعديل الفاتورة", tint = PalIconUnified)
                                        }
                                        Spacer(modifier = Modifier.width(4.dp))
                                        IconButton(
                                            onClick = {
                                                invoiceToVoid = inv
                                                showVoidConfirm = true
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "إلغاء الفاتورة", tint = PalRedNormal)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Product Selection Picker Dialog ---
    if (showProductPicker) {
        AlertDialog(
            onDismissRequest = { showProductPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = { Text("تحديد صنف القات والكمية", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    var selectedProd by remember { mutableStateOf<InventoryItem?>(null) }
                    var sellQtyInput by remember { mutableStateOf("1") }
                    var searchProductQuery by remember { mutableStateOf("") }

                    Text("ابحث عن الصنف المتوفر:", color = PalWhiteMuted, fontSize = 11.sp, modifier = Modifier.padding(bottom = 4.dp))
                    OutlinedTextField(
                        value = searchProductQuery,
                        onValueChange = { searchProductQuery = it },
                        placeholder = { Text("اكتب اسم الصنف للبحث...", color = PalWhiteMuted, fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalIconUnified,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        shape = RoundedCornerShape(10.dp)
                    )

                    val currentQatTypes = remember(com.example.util.BrandingManager.qatTypes) {
                        com.example.util.BrandingManager.qatTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }
                    if (currentQatTypes.isNotEmpty()) {
                        Text("أنواع القات السريعة للبحث والمحاسبة:", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            currentQatTypes.forEach { type ->
                                val isTypeSel = searchProductQuery == type
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(1.dp, if (isTypeSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(12.dp))
                                        .background(if (isTypeSel) PalGreenNormal else PalBlackDark, RoundedCornerShape(12.dp))
                                        .clickable { searchProductQuery = type }
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(type, color = if (isTypeSel) Color.White else PalWhitePure, fontSize = 10.sp)
                                }
                            }
                        }
                    }

                    val filteredProductsList = remember(allProducts, searchProductQuery) {
                        allProducts.filter { it.name.contains(searchProductQuery, ignoreCase = true) }
                    }

                    Text("اختر الصنف المتوفر من المخزن:", color = PalWhiteMuted, fontSize = 12.sp, modifier = Modifier.padding(bottom = 6.dp))
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                            .verticalScroll(rememberScrollState())
                    ) {
                        Column {
                            filteredProductsList.forEach { prod ->
                                val isSelProd = selectedProd == prod
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(if (isSelProd) PalSoftGreenTint else Color.Transparent)
                                        .clickable { selectedProd = prod }
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    val isProdSar = (prod.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي")
                                     Text("${prod.name}", color = PalWhitePure, fontSize = 13.sp, fontWeight = if (isSelProd) FontWeight.Bold else FontWeight.Normal)
                                    Text("المخزون: ${com.example.util.Helpers.formatQuantity(prod.quantity)} حبة • " + if (isProdSar) "سعودي" else "يمني", color = PalWhiteMuted, fontSize = 11.sp)
                                }
                                HorizontalDivider(color = PalBorderColor)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    AccountingTextField(
                        value = sellQtyInput,
                        onValueChange = { sellQtyInput = it },
                        label = "الكمية المطلوبة للبيع (حبة)",
                        placeholder = "مثال: 5",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Button(
                        onClick = {
                            if (selectedProd != null) {
                                val qtyToSell = sellQtyInput.toDoubleOrNull() ?: 1.0
                                if (qtyToSell > selectedProd!!.quantity) {
                                    android.widget.Toast.makeText(context, "فشل: الكمية المطلوبة غير متوفرة بالمخزن!", android.widget.Toast.LENGTH_LONG).show()
                                } else {
                                    selectedItemsList.add(selectedProd!! to qtyToSell)
                                    showProductPicker = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
                    ) {
                        Text("إدراج بالفاتورة", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            containerColor = PalBlackNormal
        )
    }

    // --- Registered Customers Picker Dialog ---
    if (showCustomerPicker) {
        AlertDialog(
            onDismissRequest = { showCustomerPicker = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = { Text("تحديد عميل مسجل بمربع بحث", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    var searchCustomerQuery by remember { mutableStateOf("") }

                    AccountingTextField(
                        value = searchCustomerQuery,
                        onValueChange = { searchCustomerQuery = it },
                        label = "البحث باسم العميل المسجل",
                        placeholder = "اكتب اسم العميل للبحث السريع...",
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp)) }
                    )

                    val filteredCustomersList = remember(allCustomers, searchCustomerQuery) {
                        allCustomers.filter { it.name.contains(searchCustomerQuery, ignoreCase = true) }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .clip(RoundedCornerShape(10.dp))
                    ) {
                        if (filteredCustomersList.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("لا توجد نتائج بحث مطابقة", color = PalWhiteMuted, fontSize = 12.sp)
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredCustomersList) { cust ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                customerNameInput = cust.name
                                                showCustomerPicker = false
                                            }
                                            .padding(12.dp)
                                    ) {
                                        Text(cust.name, color = PalWhitePure, fontWeight = FontWeight.Bold)
                                    }
                                    HorizontalDivider(color = PalBorderColor)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            containerColor = PalBlackNormal
        )
    }

    // --- Details Viewing Dialog ---
    if (showDetailsDialog && viewingInvoice != null) {
        val targetCustomer = allCustomers.find { it.name.trim() == viewingInvoice!!.customerName.trim() }
        val customerPayments = if (targetCustomer != null) {
            allPayments.filter { it.customerId == targetCustomer.id }
        } else {
            emptyList()
        }

        val totalYer = if (viewingInvoice!!.totalAmountYer > 0.0) viewingInvoice!!.totalAmountYer else if (viewingInvoice!!.currency.contains("يمني")) viewingInvoice!!.totalAmount else 0.0
        val totalSar = if (viewingInvoice!!.totalAmountSar > 0.0) viewingInvoice!!.totalAmountSar else if (viewingInvoice!!.currency.contains("سعودي")) viewingInvoice!!.totalAmount else 0.0
        val paidYer = if (viewingInvoice!!.totalAmountYer > 0.0) viewingInvoice!!.paidAmountYer else if (viewingInvoice!!.currency.contains("يمني")) viewingInvoice!!.paidAmount else 0.0
        val paidSar = if (viewingInvoice!!.totalAmountSar > 0.0) viewingInvoice!!.paidAmountSar else if (viewingInvoice!!.currency.contains("سعودي")) viewingInvoice!!.paidAmount else 0.0
        val debtYer = if (viewingInvoice!!.totalAmountYer > 0.0) viewingInvoice!!.debtAmountYer else if (viewingInvoice!!.currency.contains("يمني")) viewingInvoice!!.debtAmount else 0.0
        val debtSar = if (viewingInvoice!!.totalAmountSar > 0.0) viewingInvoice!!.debtAmountSar else if (viewingInvoice!!.currency.contains("سعودي")) viewingInvoice!!.debtAmount else 0.0

        val msg = buildString {
            appendLine(com.example.util.BrandingManager.buildShareHeader("فاتورة مبيعات للعميل: ${viewingInvoice!!.customerName}"))
            appendLine("رقم الفاتورة: #${viewingInvoice!!.id}")
            appendLine("تاريخ التصدير: ${viewingInvoice!!.date}")
            appendLine("طريقة الدفع: ${viewingInvoice!!.paymentMethod}")
            appendLine("---------------------------------")
            for (item in viewingInvoiceItems) {
                appendLine("${item.itemName} x ${Helpers.formatQuantity(item.quantity)} حبة = ${Helpers.formatWithCurrency(item.quantity * item.unitPrice, item.currency)}")
            }
            appendLine("---------------------------------")
            if (totalYer > 0.0 && totalSar > 0.0) {
                appendLine("الإجماليات بالريال اليمني:")
                appendLine(" • الإجمالي الصافي: ${Helpers.formatWithCurrency(totalYer, "ريال يمني")}")
                appendLine(" • المدفوع عند الشراء: ${Helpers.formatWithCurrency(paidYer, "ريال يمني")}")
                appendLine(" • المتبقي دين معلق: ${Helpers.formatWithCurrency(debtYer, "ريال يمني")}")
                appendLine("الإجماليات بالريال السعودي:")
                appendLine(" • الإجمالي الصافي: ${Helpers.formatWithCurrency(totalSar, "ريال سعودي")}")
                appendLine(" • المدفوع عند الشراء: ${Helpers.formatWithCurrency(paidSar, "ريال سعودي")}")
                appendLine(" • المتبقي دين معلق: ${Helpers.formatWithCurrency(debtSar, "ريال سعودي")}")
            } else {
                val dispCurr = if (totalSar > 0.0) "ريال سعودي" else "ريال يمني"
                val dispTotal = if (totalSar > 0.0) totalSar else totalYer
                val dispPaid = if (totalSar > 0.0) paidSar else paidYer
                val dispDebt = if (totalSar > 0.0) debtSar else debtYer
                appendLine("الإجمالي الصافي للفاتورة: ${Helpers.formatWithCurrency(dispTotal, dispCurr)}")
                appendLine("المبلغ المدفوع عند الشراء: ${Helpers.formatWithCurrency(dispPaid, dispCurr)}")
                appendLine("رصيد الدين المتبقي المعلق: ${Helpers.formatWithCurrency(dispDebt, dispCurr)}")
            }
            
            if (customerPayments.isNotEmpty()) {
                appendLine("---------------------------------")
                appendLine("سجل الدفعات والمسددات المقبوضة لاحقاً:")
                customerPayments.forEachIndexed { i, p ->
                    appendLine(" • دفعة #${i + 1}: بقيمة ${Helpers.formatWithCurrency(p.amount, viewingInvoice!!.currency)} (${p.paymentMethod}) بتاريخ ${p.date}")
                    if (p.notes.isNotEmpty()) appendLine("   البيان: ${p.notes}")
                }
            }
            appendLine("---------------------------------")
            appendLine(com.example.util.BrandingManager.buildShareFooter())
        }

        AlertDialog(
            onDismissRequest = { showDetailsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = { Text("تفاصيل فاتورة البيع #${viewingInvoice!!.id}", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("العميل: ${viewingInvoice!!.customerName}", color = PalWhitePure, fontWeight = FontWeight.Bold)
                    Text("التاريخ والوقت: ${viewingInvoice!!.date}", color = PalWhiteMuted, fontSize = 12.sp)
                    HorizontalDivider(color = PalBorderColor)

                    Text("الأصناف المتضمنة بالفاتورة:", color = PalWhiteMuted, fontSize = 12.sp)
                    viewingInvoiceItems.forEach { item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(item.itemName, color = PalWhitePure, fontSize = 13.sp)
                            Text("${Helpers.formatQuantity(item.quantity)} حبة بسعر ${Helpers.formatWithCurrency(item.unitPrice, item.currency)}", color = PalWhiteSoft, fontSize = 12.sp)
                        }
                    }

                    HorizontalDivider(color = PalBorderColor)

                    if (totalYer > 0.0 && totalSar > 0.0) {
                        Text("مجموع الإجماليات لكل عملة بشكل مستقل:", color = PalIconUnified, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text("ريال يمني:", color = PalWhitePure, fontWeight = FontWeight.Bold)
                        Text("  إجمالي يمني: ${Helpers.formatWithCurrency(totalYer, "ريال يمني")}", color = PalWhitePure, fontSize = 12.sp)
                        Text("  مدفوع يمني: ${Helpers.formatWithCurrency(paidYer, "ريال يمني")}", color = PalSuccessNormal, fontSize = 12.sp)
                        Text("  متبقي يمني: ${Helpers.formatWithCurrency(debtYer, "ريال يمني")}", color = if (debtYer > 0) PalRedNormal else PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("ريال سعودي:", color = PalWhitePure, fontWeight = FontWeight.Bold)
                        Text("  إجمالي سعودي: ${Helpers.formatWithCurrency(totalSar, "ريال سعودي")}", color = PalWhitePure, fontSize = 12.sp)
                        Text("  مدفوع سعودي: ${Helpers.formatWithCurrency(paidSar, "ريال سعودي")}", color = PalSuccessNormal, fontSize = 12.sp)
                        Text("  متبقي سعودي: ${Helpers.formatWithCurrency(debtSar, "ريال سعودي")}", color = if (debtSar > 0) PalRedNormal else PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        val dispCurr = if (totalSar > 0.0) "ريال سعودي" else "ريال يمني"
                        val dispTotal = if (totalSar > 0.0) totalSar else totalYer
                        val dispPaid = if (totalSar > 0.0) paidSar else paidYer
                        val dispDebt = if (totalSar > 0.0) debtSar else debtYer
                        Text("إجمالي الفاتورة الصافي: ${Helpers.formatWithCurrency(dispTotal, dispCurr)}", color = PalWhitePure, fontWeight = FontWeight.Bold)
                        Text("المبلغ المدفوع عند الشراء: ${Helpers.formatWithCurrency(dispPaid, dispCurr)} (${viewingInvoice!!.paymentMethod})", color = PalSuccessNormal)
                        Text("المتبقي دين معلق حالياً: ${Helpers.formatWithCurrency(dispDebt, dispCurr)}", color = if (dispDebt > 0) PalRedNormal else PalWhitePure, fontWeight = FontWeight.Bold)
                    }
                    
                    if (customerPayments.isNotEmpty()) {
                        HorizontalDivider(color = PalBorderColor)
                        Text("سجل حركات تسديد ديون العميل لاحقاً:", color = PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        customerPayments.forEachIndexed { i, p ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PalBlackDark, RoundedCornerShape(8.dp))
                                    .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("دفعة #${i + 1} (${p.paymentMethod})", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text(Helpers.formatWithCurrency(p.amount, viewingInvoice!!.currency), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                if (p.notes.isNotEmpty()) {
                                    Text("البيان: ${p.notes}", color = PalWhiteMuted, fontSize = 10.sp)
                                }
                                Text("التاريخ: ${p.date}", color = PalWhiteMuted, fontSize = 9.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                // PDF Generation
                                val headers = listOf("الصنف", "الكمية", "سعر الوحدة", "العملة", "الإجمالي")
                                val rows = viewingInvoiceItems.map {
                                    listOf(it.itemName, "${Helpers.formatQuantity(it.quantity)} حبة", Helpers.formatMoney(it.unitPrice), it.currency, Helpers.formatWithCurrency(it.quantity * it.unitPrice, it.currency))
                                }
                                val totalsMap = mutableMapOf<String, String>()
                                if (totalYer > 0.0 && totalSar > 0.0) {
                                    totalsMap["الإجمالي الصافي بالريال اليمني"] = Helpers.formatWithCurrency(totalYer, "ريال يمني")
                                    totalsMap["المدفوع بالريال اليمني"] = Helpers.formatWithCurrency(paidYer, "ريال يمني")
                                    totalsMap["الدين المعلق بالريال اليمني"] = Helpers.formatWithCurrency(debtYer, "ريال يمني")
                                    totalsMap["-----------------------"] = "-----------------------"
                                    totalsMap["الإجمالي الصافي بالريال السعودي"] = Helpers.formatWithCurrency(totalSar, "ريال سعودي")
                                    totalsMap["المدفوع بالريال السعودي"] = Helpers.formatWithCurrency(paidSar, "ريال سعودي")
                                    totalsMap["الدين المعلق بالريال السعودي"] = Helpers.formatWithCurrency(debtSar, "ريال سعودي")
                                } else {
                                    val dispCurr = if (totalSar > 0.0) "ريال سعودي" else "ريال يمني"
                                    val dispTotal = if (totalSar > 0.0) totalSar else totalYer
                                    val dispPaid = if (totalSar > 0.0) paidSar else paidYer
                                    val dispDebt = if (totalSar > 0.0) debtSar else debtYer
                                    totalsMap["المجموع النهائي للفاتورة"] = Helpers.formatWithCurrency(dispTotal, dispCurr)
                                    totalsMap["المسدد نقداً عند الشراء"] = Helpers.formatWithCurrency(dispPaid, dispCurr)
                                    totalsMap["المتبقي معلق في الذمة"] = Helpers.formatWithCurrency(dispDebt, dispCurr)
                                }
                                customerPayments.forEachIndexed { i, p ->
                                    totalsMap["دفعة لاحقة #${i + 1} (${p.date.split(" ")[0]})"] = Helpers.formatWithCurrency(p.amount, viewingInvoice!!.currency)
                                }

                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "فاتورة تفصيلية رقم #${viewingInvoice!!.id} لتسجيل الدفعات",
                                    headers = headers,
                                    rows = rows,
                                    totals = totalsMap
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                Helpers.shareViaWhatsApp(context, "", msg)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackDark),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("واتساب", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                bluetoothPrintText = msg
                                showBluetoothPrintTrigger = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint),
                            border = BorderStroke(1.dp, PalGoldMuted),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("طباعة", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val inv = viewingInvoice ?: return@Button
                            editInvoiceCustomerName = inv.customerName
                            editInvoiceDate = inv.date
                            editInvoiceCurrency = inv.currency
                            editInvoiceTotalAmount = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                (inv.totalAmountYer + inv.totalAmountSar).toString()
                            } else inv.totalAmount.toString()
                            editInvoiceDiscount = inv.discount.toString()
                            editInvoiceTaxApplied = inv.taxApplied.toString()
                            editInvoicePaidAmount = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                (inv.paidAmountYer + inv.paidAmountSar).toString()
                            } else inv.paidAmount.toString()
                            editInvoiceDebtAmount = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                (inv.debtAmountYer + inv.debtAmountSar).toString()
                            } else inv.debtAmount.toString()
                            editInvoicePaymentMethod = inv.paymentMethod
                            editInvoiceTotalYer = inv.totalAmountYer.toString()
                            editInvoiceTotalSar = inv.totalAmountSar.toString()
                            editInvoicePaidYer = inv.paidAmountYer.toString()
                            editInvoicePaidSar = inv.paidAmountSar.toString()
                            editInvoiceDebtYer = inv.debtAmountYer.toString()
                            editInvoiceDebtSar = inv.debtAmountSar.toString()
                            editInvoicePaymentMethodYer = inv.paymentMethodYer
                            editInvoicePaymentMethodSar = inv.paymentMethodSar
                            showEditInvoiceDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("تعديل الفاتورة", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showDetailsDialog = false }) {
                        Text("إغلاق", color = PalWhiteMuted)
                    }
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Edit Invoice Dialog ---
    val curViewingInv = viewingInvoice
    if (showEditInvoiceDialog && curViewingInv != null) {
        val isMulti = curViewingInv.totalAmountYer > 0.0 && curViewingInv.totalAmountSar > 0.0
        AlertDialog(
            onDismissRequest = { showEditInvoiceDialog = false },
            title = {
                Text(
                    text = "تعديل الفاتورة رقم #${curViewingInv.id}",
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
                        value = editInvoiceCustomerName,
                        onValueChange = { editInvoiceCustomerName = it },
                        label = { Text("اسم العميل", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalIconUnified,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editInvoiceDate,
                        onValueChange = { editInvoiceDate = it },
                        label = { Text("تاريخ ووقت الفاتورة", color = PalWhiteMuted) },
                        trailingIcon = {
                            IconButton(onClick = { editInvoiceDate = Helpers.getCurrentDateTime() }) {
                                Icon(Icons.Default.CalendarToday, contentDescription = "الوقت الحالي", tint = PalIconUnified)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalIconUnified,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    CurrencySelectionRow(
                        selectedCurrency = if (isMulti) MoneyMath.CURRENCY_MULTI else editInvoiceCurrency,
                        currencyConfig = currencyConfig,
                        onCurrencySelected = { editInvoiceCurrency = it },
                        title = "العملة:",
                        extraOptionsWhenAllowed = listOf(MoneyMath.CURRENCY_MULTI)
                    )

                    if (editInvoiceCurrency == "عملات متعددة" || isMulti) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("تفاصيل الريال اليمني:", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = editInvoiceTotalYer,
                                    onValueChange = {
                                        editInvoiceTotalYer = it
                                        val tY = it.toDoubleOrNull() ?: 0.0
                                        val pY = editInvoicePaidYer.toDoubleOrNull() ?: 0.0
                                        editInvoiceDebtYer = (tY - pY).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("إجمالي يمني", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalIconUnified,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvoicePaidYer,
                                    onValueChange = {
                                        editInvoicePaidYer = it
                                        val tY = editInvoiceTotalYer.toDoubleOrNull() ?: 0.0
                                        val pY = it.toDoubleOrNull() ?: 0.0
                                        editInvoiceDebtYer = (tY - pY).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("المدفوع يمني", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalIconUnified,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvoiceDebtYer,
                                    onValueChange = { editInvoiceDebtYer = it },
                                    label = { Text("المتبقي آجل يمني", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalIconUnified,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("طريقة دفع اليمني:", color = PalWhiteSoft, fontSize = 11.sp)
                                val methodsYer = listOf("نقداً", "آجل", "إيداع", "تحويل")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    methodsYer.forEach { m ->
                                        val isSel = editInvoicePaymentMethodYer == m
                                        val bg = if (isSel) PalGreenNormal else PalBlackLight
                                        val fg = if (isSel) Color.White else PalWhiteMuted
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(bg)
                                                .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(6.dp))
                                                .clickable { editInvoicePaymentMethodYer = m }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(m, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("تفاصيل الريال السعودي:", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                OutlinedTextField(
                                    value = editInvoiceTotalSar,
                                    onValueChange = {
                                        editInvoiceTotalSar = it
                                        val tS = it.toDoubleOrNull() ?: 0.0
                                        val pS = editInvoicePaidSar.toDoubleOrNull() ?: 0.0
                                        editInvoiceDebtSar = (tS - pS).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("إجمالي سعودي", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalIconUnified,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvoicePaidSar,
                                    onValueChange = {
                                        editInvoicePaidSar = it
                                        val tS = editInvoiceTotalSar.toDoubleOrNull() ?: 0.0
                                        val pS = it.toDoubleOrNull() ?: 0.0
                                        editInvoiceDebtSar = (tS - pS).coerceAtLeast(0.0).toString()
                                    },
                                    label = { Text("المدفوع سعودي", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalIconUnified,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                OutlinedTextField(
                                    value = editInvoiceDebtSar,
                                    onValueChange = { editInvoiceDebtSar = it },
                                    label = { Text("المتبقي آجل سعودي", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedBorderColor = PalIconUnified,
                                        unfocusedBorderColor = PalBorderColor
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Text("طريقة دفع السعودي:", color = PalWhiteSoft, fontSize = 11.sp)
                                val methodsSar = listOf("نقداً", "آجل", "إيداع", "تحويل")
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    methodsSar.forEach { m ->
                                        val isSel = editInvoicePaymentMethodSar == m
                                        val bg = if (isSel) PalGreenNormal else PalBlackLight
                                        val fg = if (isSel) Color.White else PalWhiteMuted
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(bg)
                                                .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(6.dp))
                                                .clickable { editInvoicePaymentMethodSar = m }
                                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                        ) {
                                            Text(m, color = fg, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = editInvoiceTotalAmount,
                            onValueChange = {
                                editInvoiceTotalAmount = it
                                val t = it.toDoubleOrNull() ?: 0.0
                                val p = editInvoicePaidAmount.toDoubleOrNull() ?: 0.0
                                val d = editInvoiceDiscount.toDoubleOrNull() ?: 0.0
                                editInvoiceDebtAmount = (t - p - d).coerceAtLeast(0.0).toString()
                            },
                            label = { Text("إجمالي الفاتورة", color = PalWhiteMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedBorderColor = PalIconUnified,
                                unfocusedBorderColor = PalBorderColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = editInvoiceDiscount,
                                onValueChange = {
                                    editInvoiceDiscount = it
                                    val t = editInvoiceTotalAmount.toDoubleOrNull() ?: 0.0
                                    val p = editInvoicePaidAmount.toDoubleOrNull() ?: 0.0
                                    val d = it.toDoubleOrNull() ?: 0.0
                                    editInvoiceDebtAmount = (t - p - d).coerceAtLeast(0.0).toString()
                                },
                                label = { Text("الخصم", color = PalWhiteMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft,
                                    focusedBorderColor = PalIconUnified,
                                    unfocusedBorderColor = PalBorderColor
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = editInvoiceTaxApplied,
                                onValueChange = { editInvoiceTaxApplied = it },
                                label = { Text("الضريبة", color = PalWhiteMuted) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft,
                                    focusedBorderColor = PalIconUnified,
                                    unfocusedBorderColor = PalBorderColor
                                ),
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        OutlinedTextField(
                            value = editInvoicePaidAmount,
                            onValueChange = {
                                editInvoicePaidAmount = it
                                val t = editInvoiceTotalAmount.toDoubleOrNull() ?: 0.0
                                val p = it.toDoubleOrNull() ?: 0.0
                                val d = editInvoiceDiscount.toDoubleOrNull() ?: 0.0
                                editInvoiceDebtAmount = (t - p - d).coerceAtLeast(0.0).toString()
                            },
                            label = { Text("المبلغ المدفوع", color = PalWhiteMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedBorderColor = PalIconUnified,
                                unfocusedBorderColor = PalBorderColor
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = editInvoiceDebtAmount,
                            onValueChange = { editInvoiceDebtAmount = it },
                            label = { Text("المبلغ المتبقي (الآجل)", color = PalWhiteMuted) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedBorderColor = PalIconUnified,
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
                            val isSel = editInvoicePaymentMethod == method
                            val bg = if (isSel) PalGreenNormal else PalBlackLight
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { editInvoicePaymentMethod = method }
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
                        if (!currencyConfig.isAllowed(editInvoiceCurrency)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(editInvoiceCurrency), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val isMultiNow = editInvoiceCurrency == "عملات متعددة" || ((editInvoiceTotalYer.toDoubleOrNull() ?: 0.0) > 0.0 && (editInvoiceTotalSar.toDoubleOrNull() ?: 0.0) > 0.0)
                        val totYer = editInvoiceTotalYer.toDoubleOrNull() ?: 0.0
                        val totSar = editInvoiceTotalSar.toDoubleOrNull() ?: 0.0
                        val paidYer = editInvoicePaidYer.toDoubleOrNull() ?: 0.0
                        val paidSar = editInvoicePaidSar.toDoubleOrNull() ?: 0.0
                        val debtYer = editInvoiceDebtYer.toDoubleOrNull() ?: 0.0
                        val debtSar = editInvoiceDebtSar.toDoubleOrNull() ?: 0.0

                        val totalAmt = if (isMultiNow) (totYer + totSar) else (editInvoiceTotalAmount.toDoubleOrNull() ?: 0.0)
                        val paidAmt = if (isMultiNow) (paidYer + paidSar) else (editInvoicePaidAmount.toDoubleOrNull() ?: 0.0)
                        val debtAmt = if (isMultiNow) (debtYer + debtSar) else (editInvoiceDebtAmount.toDoubleOrNull() ?: 0.0)
                        val discountAmt = editInvoiceDiscount.toDoubleOrNull() ?: 0.0
                        val taxAmt = editInvoiceTaxApplied.toDoubleOrNull() ?: 0.0

                        viewModel.updateSalesInvoiceFull(
                            invoiceId = curViewingInv.id,
                            newCustomerName = editInvoiceCustomerName.ifEmpty { curViewingInv.customerName },
                            newDate = editInvoiceDate.ifEmpty { curViewingInv.date },
                            newCurrency = editInvoiceCurrency,
                            newTotalAmount = totalAmt,
                            newTaxApplied = taxAmt,
                            newDiscount = discountAmt,
                            newPaidAmount = paidAmt,
                            newDebtAmount = debtAmt,
                            newPaymentMethod = editInvoicePaymentMethod,
                            newTotalAmountYer = totYer,
                            newTotalAmountSar = totSar,
                            newPaidAmountYer = paidYer,
                            newPaidAmountSar = paidSar,
                            newDebtAmountYer = debtYer,
                            newDebtAmountSar = debtSar,
                            newPaymentMethodYer = editInvoicePaymentMethodYer,
                            newPaymentMethodSar = editInvoicePaymentMethodSar
                        )
                        showEditInvoiceDialog = false
                        showDetailsDialog = false
                        android.widget.Toast.makeText(context, "تم تعديل الفاتورة وتحديث كشف الحساب والمبيعات بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("حفظ التعديلات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditInvoiceDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Void (Delete) Invoice Confirm Dialog ---
    if (showVoidConfirm && invoiceToVoid != null) {
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text("إلغاء وإعدام الفاتورة والمبيعات؟", color = PalWhitePure) },
            text = { Text("هل أنت متأكد من إلغاء الفاتورة #${invoiceToVoid!!.id} للعميل ${invoiceToVoid!!.customerName}؟ سيتم إرجاع كميات المواد للمخزن تلقائيا وإسقاط بقايا الديون.", color = PalWhiteMuted) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.voidSale(invoiceToVoid!!.id)
                        showVoidConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("نعم، إلغاء السند", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showVoidConfirm = false }) {
                    Text("تراجع", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showReportsDialog) {
        var searchReportQuery by remember { mutableStateOf("") }
        val periodInvoices = allInvoices.filter { Helpers.matchesPeriod(it.date, selectedReportPeriod, customReportValue, customReportType) }.sortedBy { it.id }

        val totalSalesAmountYer = periodInvoices.sumOf { it.totalAmountYer }
        val totalPaidAmountYer = periodInvoices.sumOf { it.paidAmountYer }
        val totalDebtAmountYer = periodInvoices.sumOf { it.debtAmountYer }

        val totalSalesAmountSar = periodInvoices.sumOf { it.totalAmountSar }
        val totalPaidAmountSar = periodInvoices.sumOf { it.paidAmountSar }
        val totalDebtAmountSar = periodInvoices.sumOf { it.debtAmountSar }

        val periodInvoiceIds = periodInvoices.map { it.id }.toSet()
        val periodInvoiceItems = allInvoiceItems.filter { it.invoiceId in periodInvoiceIds }

        // Group by item name and currency separately to show sales summary without mixing any currency values
        val itemSalesRows = periodInvoiceItems.groupBy { Pair(it.itemName, periodInvoices.find { inv -> inv.id == it.invoiceId }?.currency ?: "ريال يمني") }.map { (key, group) ->
            val (name, currency) = key
            val qty = group.sumOf { it.quantity }
            val amount = group.sumOf { it.quantity * it.unitPrice }
            Triple(name, currency, Pair(qty, amount))
        }.sortedByDescending { it.third.second }
        .filter {
            searchReportQuery.trim().isEmpty() ||
            it.first.contains(searchReportQuery, ignoreCase = true)
        }

        AlertDialog(
            onDismissRequest = { showReportsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تقرير المبيعات والعمليات بالتفصيل",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "اختر دورة التقرير الزمني لعرض وتصدير حركات وفواتير المبيعات أو حدد تقويماً مخصصاً:",
                        color = PalWhiteMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Period Toggles
                    val periods = listOf("يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص")
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        periods.forEach { period ->
                            val isSel = selectedReportPeriod == period
                            val bg = if (isSel) PalGreenNormal else PalBlackLight
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedReportPeriod = period
                                        if (period == "أسبوعي" && !customReportValue.startsWith("WEEK_FILTER|")) {
                                            customReportValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                        }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
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
                            initialType = if (selectedReportPeriod == "أسبوعي") "أسبوع" else customReportType,
                            initialValue = customReportValue,
                            onPeriodChanged = { type, finalVal ->
                                customReportType = type
                                customReportValue = finalVal
                                customReportPrintDate = Helpers.formatCustomPeriodToArabic(type, finalVal)
                            }
                        )
                        
                        OutlinedTextField(
                            value = customReportPrintDate,
                            onValueChange = { customReportPrintDate = it },
                            label = { Text("تعيين وتخصيص تاريخ التقرير المطبوع", color = PalWhiteSoft) },
                            placeholder = { Text("اكتب تاريخ أو فترة تخصيص التقرير...", color = PalWhiteMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalIconUnified,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedContainerColor = PalBlackDark,
                                unfocusedContainerColor = PalBlackDark
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    HorizontalDivider(color = PalBorderColor)

                    // Financial Summary Banner (Yemeni Rial)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("ملخص مبيعات العملة (ريال يمني):", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المبيعات بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalSalesAmountYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتحصل نقدياً:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalPaidAmountYer, "ريال يمني"), color = PalWhitePure, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتبقي كملحق ديون:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalDebtAmountYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }

                    // Financial Summary Banner (Saudi Rial)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("ملخص مبيعات العملة (ريال سعودي):", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المبيعات بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalSalesAmountSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتحصل نقدياً:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalPaidAmountSar, "ريال سعودي"), color = PalWhitePure, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("المتبقي كملحق ديون:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalDebtAmountSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                        }
                    }

                    Text(
                        text = "تفاصيل الأصناف المباعة بالفترة والعملة:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = searchReportQuery,
                        onValueChange = { searchReportQuery = it },
                        placeholder = { Text("ابحث عن صنف في هذا التقرير...", color = PalWhiteMuted, fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalIconUnified,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (itemSalesRows.isEmpty()) {
                                Text(
                                    text = "لا توجد معاملات مبيعات مسجلة في هذه الفترة الزمنية.",
                                    color = PalWhiteMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(top = 32.dp)
                                )
                            } else {
                                itemSalesRows.forEach { (name, currency, stats) ->
                                    val (qty, amount) = stats
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(PalBlackNormal, RoundedCornerShape(6.dp))
                                            .border(1.dp, PalBorderColor, RoundedCornerShape(6.dp))
                                            .padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1.5f)) {
                                            Text(name, color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text("الكمية: ${qty.toInt()} حبة | العملة: $currency", color = PalWhiteMuted, fontSize = 9.sp)
                                        }
                                        Text(Helpers.formatWithCurrency(amount, currency), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val reportMsg = buildString {
                        appendLine(com.example.util.BrandingManager.buildShareHeader("التقرير المالي لحركة المبيعات بالتفصيل"))
                        val pStr = if (selectedReportPeriod == "تقويم مخصص") customReportPrintDate else selectedReportPeriod
                        appendLine("فترة التقرير: $pStr")
                        appendLine("تاريخ التصدير: ${Helpers.getCurrentDateTime()}")
                        appendLine("--------------------------------")
                        if (periodInvoices.isNotEmpty()) {
                            appendLine("سجل الفواتير وعمليات البيع بالتفصيل:")
                            periodInvoices.forEach { inv ->
                                val invItems = allInvoiceItems.filter { it.invoiceId == inv.id }
                                val itemsSummary = invItems.joinToString(", ") { "${it.itemName} (${Helpers.formatQuantity(it.quantity)} حبة)" }
                                appendLine("• سند #${inv.id} | التاريخ: ${inv.date}")
                                appendLine("  العميل: ${inv.customerName} | الأصناف: $itemsSummary")
                                appendLine("  طريقة الدفع: ${inv.paymentMethod} | الإجمالي: ${Helpers.formatWithCurrency(inv.totalAmount, inv.currency)}")
                                appendLine("  ----------------")
                            }
                        }
                        if (itemSalesRows.isNotEmpty()) {
                            appendLine("مجموع كميات الأصناف المباعة:")
                            itemSalesRows.forEach { (name, currency, stats) ->
                                val (qty, amt) = stats
                                appendLine("• $name | عملة $currency: ${Helpers.formatQuantity(qty)} حبة (بإجمالي: ${Helpers.formatWithCurrency(amt, currency)})")
                            }
                            appendLine("--------------------------------")
                        }
                        appendLine("[الريال اليمني] إجمالي المبيعات: ${Helpers.formatWithCurrency(totalSalesAmountYer, "ريال يمني")}")
                        appendLine("[الريال اليمني] إجمالي المسدد نقداً: ${Helpers.formatWithCurrency(totalPaidAmountYer, "ريال يمني")}")
                        appendLine("[الريال اليمني] بقايا الآجل والديون: ${Helpers.formatWithCurrency(totalDebtAmountYer, "ريال يمني")}")
                        appendLine("--------------------------------")
                        appendLine("[الريال السعودي] إجمالي المبيعات: ${Helpers.formatWithCurrency(totalSalesAmountSar, "ريال سعودي")}")
                        appendLine("[الريال السعودي] إجمالي المسدد نقداً: ${Helpers.formatWithCurrency(totalPaidAmountSar, "ريال سعودي")}")
                        appendLine("[الريال السعودي] بقايا الآجل والديون: ${Helpers.formatWithCurrency(totalDebtAmountSar, "ريال سعودي")}")
                        appendLine("--------------------------------")
                        appendLine(com.example.util.BrandingManager.buildShareFooter())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val bAgency = com.example.util.BrandingManager.resolvedAgencyName()
                                val tPeriodStr = when (selectedReportPeriod) {
                                    "يومي" -> "اليومي"
                                    "أسبوعي" -> "الأسبوعي"
                                    "شهري" -> "الشهري"
                                    "سنوي" -> "السنوي"
                                    "تقويم مخصص" -> "الخاص بالفترة المحدد ($customReportPrintDate)"
                                    else -> "الدوري"
                                }
                                val headers = listOf("رقم السند", "التاريخ والوقت بالتفصيل", "اسم العميل", "البيان والأصناف المباعة", "طريقة الدفع", "المبلغ الكلي")
                                val rows = periodInvoices.map { inv ->
                                    val invItems = allInvoiceItems.filter { it.invoiceId == inv.id }
                                    val itemsSummary = invItems.joinToString("\n") { "${it.itemName} (${Helpers.formatQuantity(it.quantity)} حبة)" }
                                    val formattedAmt = if (inv.totalAmountYer > 0.0 && inv.totalAmountSar > 0.0) {
                                        "يمني: ${Helpers.formatWithCurrency(inv.totalAmountYer, "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(inv.totalAmountSar, "ريال سعودي")}"
                                    } else if (inv.totalAmountYer > 0.0) {
                                        Helpers.formatWithCurrency(inv.totalAmountYer, "ريال يمني")
                                    } else if (inv.totalAmountSar > 0.0) {
                                        Helpers.formatWithCurrency(inv.totalAmountSar, "ريال سعودي")
                                    } else {
                                        Helpers.formatWithCurrency(inv.totalAmount, inv.currency)
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
                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "تقرير المبيعات $tPeriodStr $bAgency",
                                    headers = headers,
                                    rows = rows,
                                    totals = mapOf(
                                        "إجمالي مبيعات (يمني)" to Helpers.formatWithCurrency(totalSalesAmountYer, "ريال يمني"),
                                        "إجمالي مقبوض نقداً (يمني)" to Helpers.formatWithCurrency(totalPaidAmountYer, "ريال يمني"),
                                        "ديون جديدة مقيدة (يمني)" to Helpers.formatWithCurrency(totalDebtAmountYer, "ريال يمني"),
                                        "إجمالي مبيعات (سعودي)" to Helpers.formatWithCurrency(totalSalesAmountSar, "ريال سعودي"),
                                        "إجمالي مقبوض نقداً (سعودي)" to Helpers.formatWithCurrency(totalPaidAmountSar, "ريال سعودي"),
                                        "ديون جديدة مقيدة (سعودي)" to Helpers.formatWithCurrency(totalDebtAmountSar, "ريال سعودي")
                                    ),
                                    customDate = if (selectedReportPeriod == "تقويم مخصص") customReportPrintDate else Helpers.getCurrentDateTime(),
                                    reportPeriod = if (selectedReportPeriod == "تقويم مخصص") "$customReportType : $customReportValue" else selectedReportPeriod
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                Helpers.shareViaWhatsApp(context, "", reportMsg)
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackDark),
                            border = BorderStroke(1.dp, PalBorderColor),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("واتساب", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                bluetoothPrintText = reportMsg
                                showBluetoothPrintTrigger = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint),
                            border = BorderStroke(1.dp, PalGoldMuted),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 44.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            Text("طباعة", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportsDialog = false }) {
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
