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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.InventoryItem
import com.example.data.Supplier
import com.example.data.SupplierPurchase
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.util.Helpers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.ui.components.BluetoothPrintDialog
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.components.AccountingTextField
import com.example.ui.components.CurrencySelectionRow
import com.example.util.MoneyMath

@Composable
fun AssistButton(
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    tint: Color = PalIconUnified
) {
    Surface(
        onClick = onClick,
        color = PalSoftGreenTint,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, PalBorderColor),
        modifier = Modifier.heightIn(min = 40.dp).padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(icon, contentDescription = text, tint = tint, modifier = Modifier.size(16.dp))
            Text(text, color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupplierScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val suppliersList by viewModel.allSuppliers.collectAsState()
    val allPurchasesList by viewModel.allPurchases.collectAsState()
    val inventoryItems by viewModel.inventoryItems.collectAsState()
    val allPurchaseItems by viewModel.allPurchaseItems.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"

    var isAddingPurchase by remember { mutableStateOf(false) }
    var showAddSupplierDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Forms for adding/editing suppliers
    var selectedSupplierForEdit by remember { mutableStateOf<Supplier?>(null) }
    var supplierFormName by remember { mutableStateOf("") }
    var supplierFormPhone by remember { mutableStateOf("") }
    var supplierFormRegion by remember { mutableStateOf("") }

    // Forms for creating new purchase
    var selectedSupplierForPurchase by remember { mutableStateOf<Supplier?>(null) }
    val newPurchaseItems = remember { mutableStateListOf<Triple<InventoryItem, Double, Double>>() } // Item, Quantity, Unit Buy Price
    var paidAmountInput by remember { mutableStateOf("") }
    var paidAmountInputYer by remember { mutableStateOf("") }
    var paidAmountInputSar by remember { mutableStateOf("") }
    var purchasePaymentMethod by remember { mutableStateOf("نقداً") }
    var purchasePaymentMethodYer by remember { mutableStateOf("نقداً") }
    var purchasePaymentMethodSar by remember { mutableStateOf("نقداً") }
    
    var showProductSelectionDialog by remember { mutableStateOf(false) }

    // View Purchase Details State
    var viewingPurchase by remember { mutableStateOf<SupplierPurchase?>(null) }
    var viewingPurchaseItems by remember { mutableStateOf<List<com.example.data.SupplierPurchaseItem>>(emptyList()) }
    var showPurchaseDetailsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewingPurchase) {
        viewingPurchase?.let { purchase ->
            viewModel.getPurchaseItemsFlow(purchase.id).collect { items ->
                viewingPurchaseItems = items
            }
        }
    }

    // Void Purchase Confirm State
    var purchaseToVoid by remember { mutableStateOf<SupplierPurchase?>(null) }
    var showVoidConfirm by remember { mutableStateOf(false) }

    var showSupplierReportsDialog by remember { mutableStateOf(false) }
    val selectedSupplierReportPeriod = selectedPeriod

    var showSupplierStatementDialog by remember { mutableStateOf(false) }
    var selectedSupplierForStatement by remember { mutableStateOf<Supplier?>(null) }
    var selectedSupplierStatementPeriod by remember { mutableStateOf("الكل") }
    var supplierSearchQuery by remember { mutableStateOf("") }
    var editingPurchaseId by remember { mutableStateOf<Int?>(null) }
    var deedDateInput by remember { mutableStateOf("") }
    var purchaseSearchQuery by remember { mutableStateOf("") }
    var supplierReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    var supplierGeneralReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }

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
        supplierReportDate = label
        supplierGeneralReportDate = label
    }

    var showEditPurchaseItemDialog by remember { mutableStateOf(false) }
    var editingSupplierPurchaseItemState by remember { mutableStateOf<com.example.data.SupplierPurchaseItem?>(null) }
    var editPurchaseItemNameInput by remember { mutableStateOf("") }
    var editPurchaseItemQtyInput by remember { mutableStateOf("") }
    var editPurchaseItemPriceInput by remember { mutableStateOf("") }
    var editPurchaseItemNumberInput by remember { mutableStateOf("") }

    val customSupplierReportType = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"
    val customSupplierReportValue = customPeriodValue
    var customSupplierStatementType by remember { mutableStateOf("date") }
    var customSupplierStatementValue by remember { mutableStateOf("") }

    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }

    var showEditPurchaseDialog by remember { mutableStateOf(false) }
    var editPurchasePaidAmount by remember { mutableStateOf("") }
    var editPurchasePaymentMethod by remember { mutableStateOf("نقداً") }

    // Supplier Payment states
    var showSupplierPaymentDialog by remember { mutableStateOf(false) }
    var selectedSupplierForPayment by remember { mutableStateOf<Supplier?>(null) }
    var paymentMethodChoice by remember { mutableStateOf("نقداً") } // "نقداً", "تحويل عبر صرافة", "إيداع إلى حساب"
    var exchangeOfficeName by remember { mutableStateOf("") }
    var bankName by remember { mutableStateOf("") }
    var accountCurrency by remember { mutableStateOf(currencyConfig.supplierAccountCurrencies().first()) } // "يمني" or "سعودي"
    LaunchedEffect(currencyConfig) {
        if (!showSupplierPaymentDialog) {
            accountCurrency = currencyConfig.supplierAccountCurrencies().first()
        }
    }
    var paymentAmountInput by remember { mutableStateOf("") }
    var selectedPurchaseForPayment by remember { mutableStateOf<SupplierPurchase?>(null) }
    var useAutoPaymentDate by remember { mutableStateOf(true) }
    var customPaymentDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    var paymentNotesInput by remember { mutableStateOf("") }
    var paymentBankNameInput by remember { mutableStateOf("") }
    var demarAmountYer by remember { mutableStateOf("") }
    var demarTypeYer by remember { mutableStateOf("") } // "حوالة" or "إيداع"
    var demarNumberYer by remember { mutableStateOf("") }
    var demarAmountSar by remember { mutableStateOf("") }
    var demarTypeSar by remember { mutableStateOf("") } // "حوالة" or "إيداع"
    var demarNumberSar by remember { mutableStateOf("") }

    // Return Goods form states
    var showAddReturnDialog by remember { mutableStateOf(false) }
    var selectedSupplierForReturn by remember { mutableStateOf<Supplier?>(null) }
    var selectedItemForReturn by remember { mutableStateOf<InventoryItem?>(null) }
    var returnedQtyInput by remember { mutableStateOf("") }
    var returnedPriceInput by remember { mutableStateOf("") }
    var returnDateInput by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    var returnNotesInput by remember { mutableStateOf("") }
    var refundedAmountInput by remember { mutableStateOf("") }

    // Calculations
    val purchaseTotalYer = newPurchaseItems.filter { (it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("يمني") }.sumOf { it.second * it.third }
    val purchaseTotalSar = newPurchaseItems.filter { (it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي") }.sumOf { it.second * it.third }

    val paidBuyAmountYer = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) {
        paidAmountInputYer.toDoubleOrNull() ?: 0.0
    } else if (newPurchaseItems.any { (it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("يمني") }) {
        paidAmountInput.toDoubleOrNull() ?: 0.0
    } else 0.0

    val paidBuyAmountSar = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) {
        paidAmountInputSar.toDoubleOrNull() ?: 0.0
    } else if (newPurchaseItems.any { (it.first.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي") }) {
        paidAmountInput.toDoubleOrNull() ?: 0.0
    } else 0.0

    val remainingDebtOwedYer = if (purchaseTotalYer > paidBuyAmountYer) purchaseTotalYer - paidBuyAmountYer else 0.0
    val remainingDebtOwedSar = if (purchaseTotalSar > paidBuyAmountSar) purchaseTotalSar - paidBuyAmountSar else 0.0

    val purchaseTotal = purchaseTotalYer + purchaseTotalSar
    val paidBuyAmount = paidBuyAmountYer + paidBuyAmountSar
    val remainingDebtOwed = remainingDebtOwedYer + remainingDebtOwedSar

    LaunchedEffect(purchasePaymentMethod) {
        if (purchaseTotalYer > 0.0 && purchaseTotalSar == 0.0) {
            purchasePaymentMethodYer = purchasePaymentMethod
        } else if (purchaseTotalSar > 0.0 && purchaseTotalYer == 0.0) {
            purchasePaymentMethodSar = purchasePaymentMethod
        }
    }

    LaunchedEffect(purchasePaymentMethodYer, purchaseTotalYer) {
        if (purchasePaymentMethodYer in listOf("نقداً", "تحويل", "إيداع")) {
            paidAmountInputYer = if (purchaseTotalYer == 0.0) "" else purchaseTotalYer.toString()
        } else if (purchasePaymentMethodYer == "آجل") {
            paidAmountInputYer = "0"
        } else if (purchasePaymentMethodYer == "دفعة جزئية") {
            if (paidAmountInputYer == purchaseTotalYer.toString() || paidAmountInputYer == "0" || paidAmountInputYer == "0.0") {
                paidAmountInputYer = ""
            }
        }
    }

    LaunchedEffect(purchasePaymentMethodSar, purchaseTotalSar) {
        if (purchasePaymentMethodSar in listOf("نقداً", "تحويل", "إيداع")) {
            paidAmountInputSar = if (purchaseTotalSar == 0.0) "" else purchaseTotalSar.toString()
        } else if (purchasePaymentMethodSar == "آجل") {
            paidAmountInputSar = "0"
        } else if (purchasePaymentMethodSar == "دفعة جزئية") {
            if (paidAmountInputSar == purchaseTotalSar.toString() || paidAmountInputSar == "0" || paidAmountInputSar == "0.0") {
                paidAmountInputSar = ""
            }
        }
    }

    // Legacy sync
    LaunchedEffect(purchasePaymentMethod, purchaseTotal) {
        if (purchasePaymentMethod in listOf("نقداً", "تحويل", "إيداع")) {
            paidAmountInput = if (purchaseTotal == 0.0) "" else purchaseTotal.toString()
        } else if (purchasePaymentMethod == "آجل") {
            paidAmountInput = "0"
        } else if (purchasePaymentMethod == "دفعة جزئية") {
            if (paidAmountInput == purchaseTotal.toString() || paidAmountInput == "0" || paidAmountInput == "0.0") {
                paidAmountInput = ""
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(PalBlackNormal)) {
                TopAppBar(
                    title = { AgencyAppBarTitle(if (isAddingPurchase) "شراء وتوريد بضاعة" else "إدارة الموردين والمشتريات") },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (isAddingPurchase) {
                                    isAddingPurchase = false
                                } else {
                                    viewModel.navigateBack()
                                }
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PalIconUnified)
                        }
                    },
                    actions = {
                        if (!isAddingPurchase) {
                            IconButton(
                                onClick = {
                                    android.widget.Toast.makeText(context, "تم تحديث سجلات الموردين والمشتريات بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "تحديث السجلات", tint = PalIconUnified)
                            }
                            IconButton(
                                onClick = {
                                    selectedSupplierForEdit = null
                                    supplierFormName = ""
                                    supplierFormPhone = ""
                                    supplierFormRegion = ""
                                    showAddSupplierDialog = true
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.PersonAdd, contentDescription = "مورد جديد", tint = PalIconUnified)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
            }
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        if (isAddingPurchase) {
            // Screen layout to ADD multi-item Purchases under a supplier
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Supplier select
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("مورد البضاعة المسجلة", color = PalWhiteMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        var expanded by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(PalBlackDark)
                                .clickable { expanded = true }
                                .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                                .padding(14.dp)
                        ) {
                            Text(
                                text = selectedSupplierForPurchase?.name ?: "انقر هنا لتحديد المورد المعتمد للعملية",
                                color = if (selectedSupplierForPurchase != null) PalWhitePure else PalWhiteMuted
                            )
                        }

                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .background(PalBlackNormal)
                        ) {
                            suppliersList.forEach { supplier ->
                                DropdownMenuItem(
                                    text = { Text(supplier.name, color = PalWhitePure) },
                                    onClick = {
                                        selectedSupplierForPurchase = supplier
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Add several items inside the purchase block
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("أصناف فاتورة التوريد والشراء", color = PalWhiteMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Button(
                                onClick = { showProductSelectionDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("توريد صنف", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (newPurchaseItems.isEmpty()) {
                            Text(
                                "لم يتم إدراج أصناف توريد في هذه الفاتورة",
                                color = PalWhiteMuted,
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 16.dp)
                            )
                        } else {
                            newPurchaseItems.forEachIndexed { index, triple ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1.5f)) {
                                        Text(triple.first.name, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${Helpers.formatQuantity(triple.second)} حبة × ${Helpers.formatMoney(triple.third)}", color = PalWhiteMuted, fontSize = 11.sp)
                                    }

                                    Text(
                                        Helpers.formatMoney(triple.second * triple.third),
                                        color = PalWhitePure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f),
                                        textAlign = TextAlign.End
                                    )

                                    IconButton(
                                        onClick = { newPurchaseItems.removeAt(index) },
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "حذف الصنف", tint = PalRedNormal, modifier = Modifier.size(18.dp))
                                    }
                                }
                                HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
                            }
                        }
                    }
                }

                // Financial Box for Purchases
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("التحاسب المالي للتوريد", color = PalWhiteMuted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)

                        if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المشتريات (ريال يمني):", color = PalWhiteSoft)
                                Text(Helpers.formatWithCurrency(purchaseTotalYer, "ريال يمني"), color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المشتريات (ريال سعودي):", color = PalWhiteSoft)
                                Text(Helpers.formatWithCurrency(purchaseTotalSar, "ريال سعودي"), color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val activeCurrency = if (purchaseTotalYer > 0.0) "ريال يمني" else if (purchaseTotalSar > 0.0) "ريال سعودي" else "ريال يمني"
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي قيمة المشتريات:", color = PalWhiteSoft)
                                Text(Helpers.formatWithCurrency(purchaseTotal, activeCurrency), color = PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }

                        OutlinedTextField(
                            value = deedDateInput,
                            onValueChange = { deedDateInput = it },
                            label = { Text("تاريخ وقت السند (yyyy-MM-dd HH:mm)", color = PalWhiteMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft,
                                focusedContainerColor = PalBlackDark,
                                unfocusedContainerColor = PalBlackDark
                            ),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) {
                            Text("طريقة تسديد الجزء بالريال اليمني:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            val buyPaymentMethods = listOf("نقداً", "آجل", "إيداع", "تحويل", "دفعة جزئية")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                buyPaymentMethods.forEach { method ->
                                    val isSel = purchasePaymentMethodYer == method
                                    val bg = if (isSel) PalGreenNormal else PalBlackDark
                                    val fg = if (isSel) Color.White else PalWhiteMuted
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { purchasePaymentMethodYer = method }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(text = method, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (purchasePaymentMethodYer == "دفعة جزئية") {
                                OutlinedTextField(
                                    value = paidAmountInputYer,
                                    onValueChange = { paidAmountInputYer = it },
                                    label = { Text("المبلغ المدفوع بالريال اليمني *", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedContainerColor = PalBlackDark,
                                        unfocusedContainerColor = PalBlackDark
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                val labelYer = when (purchasePaymentMethodYer) {
                                    "آجل" -> "المبلغ المدفوع باليمني: 0 (مخزن بالآجل)"
                                    else -> "المبلغ المدفوع باليمني: $purchaseTotalYer (مسدد بالكامل)"
                                }
                                OutlinedTextField(
                                    value = if (purchasePaymentMethodYer == "آجل") "0" else purchaseTotalYer.toString(),
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text(labelYer, color = PalWhiteMuted, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = PalBorderColor, disabledTextColor = PalWhiteMuted),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text("طريقة تسديد الجزء بالريال السعودي:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                buyPaymentMethods.forEach { method ->
                                    val isSel = purchasePaymentMethodSar == method
                                    val bg = if (isSel) PalGreenNormal else PalBlackDark
                                    val fg = if (isSel) Color.White else PalWhiteMuted
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { purchasePaymentMethodSar = method }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(text = method, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            if (purchasePaymentMethodSar == "دفعة جزئية") {
                                OutlinedTextField(
                                    value = paidAmountInputSar,
                                    onValueChange = { paidAmountInputSar = it },
                                    label = { Text("المبلغ المدفوع بالريال السعودي *", color = PalWhiteMuted, fontSize = 11.sp) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedContainerColor = PalBlackDark,
                                        unfocusedContainerColor = PalBlackDark
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                val labelSar = when (purchasePaymentMethodSar) {
                                    "آجل" -> "المبلغ المدفوع بالسعودي: 0 (مخزن بالآجل)"
                                    else -> "المبلغ المدفوع بالسعودي: $purchaseTotalSar (مسدد بالكامل)"
                                }
                                OutlinedTextField(
                                    value = if (purchasePaymentMethodSar == "آجل") "0" else purchaseTotalSar.toString(),
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text(labelSar, color = PalWhiteMuted, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = PalBorderColor, disabledTextColor = PalWhiteMuted),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        } else {
                            // Single Currency purchase flow
                            val activeCurrency = if (purchaseTotalYer > 0.0) "ريال يمني" else if (purchaseTotalSar > 0.0) "ريال سعودي" else "ريال يمني"
                            val isPaidAmountEditable = purchasePaymentMethod == "دفعة جزئية"
                            if (isPaidAmountEditable) {
                                OutlinedTextField(
                                    value = paidAmountInput,
                                    onValueChange = { paidAmountInput = it },
                                    label = { Text("المبلغ المدفوع للمورد ($activeCurrency) *", color = PalWhiteMuted) },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedContainerColor = PalBlackDark,
                                        unfocusedContainerColor = PalBlackDark
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                val placeholderLabel = when (purchasePaymentMethod) {
                                    "آجل" -> "المبلغ المدفوع للمورد بقيمة 0 (آجل بالكامل)"
                                    else -> "المبلغ المدفوع للمورد بالكامل (مسدد بالكامل)"
                                }
                                OutlinedTextField(
                                    value = if (purchasePaymentMethod == "آجل") "0" else purchaseTotal.toString(),
                                    onValueChange = {},
                                    enabled = false,
                                    label = { Text("$placeholderLabel ($activeCurrency)", color = PalWhiteMuted) },
                                    colors = OutlinedTextFieldDefaults.colors(disabledBorderColor = PalBorderColor, disabledTextColor = PalWhiteMuted),
                                    shape = RoundedCornerShape(10.dp),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text("طريقة تسديد المشتريات:", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            val buyPaymentMethods = listOf("نقداً", "آجل", "إيداع", "تحويل", "دفعة جزئية")
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                buyPaymentMethods.forEach { method ->
                                    val isSel = purchasePaymentMethod == method
                                    val bg = if (isSel) PalGreenNormal else PalBlackDark
                                    val fg = if (isSel) Color.White else PalWhiteMuted
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { purchasePaymentMethod = method }
                                            .padding(horizontal = 12.dp, vertical = 8.dp)
                                    ) {
                                        Text(text = method, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = PalBorderColor, thickness = 1.dp)

                        if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الآجل المتبقي (بالريال اليمني):", color = PalWhiteSoft)
                                Text(Helpers.formatWithCurrency(remainingDebtOwedYer, "ريال يمني"), color = if (remainingDebtOwedYer > 0) PalRedNormal else PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الآجل المتبقي (بالريال السعودي):", color = PalWhiteSoft)
                                Text(Helpers.formatWithCurrency(remainingDebtOwedSar, "ريال سعودي"), color = if (remainingDebtOwedSar > 0) PalRedNormal else PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            val activeCurrency = if (purchaseTotalYer > 0.0) "ريال يمني" else if (purchaseTotalSar > 0.0) "ريال سعودي" else "ريال يمني"
                            val diffDebt = if (purchaseTotalYer > 0.0) remainingDebtOwedYer else if (purchaseTotalSar > 0.0) remainingDebtOwedSar else remainingDebtOwed
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("الرصيد المتبقي آجل للمورد (علينا ديون):", color = PalWhiteSoft)
                                Text(Helpers.formatWithCurrency(diffDebt, activeCurrency), color = if (diffDebt > 0) PalRedNormal else PalWhitePure, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Save Purchases
                Button(
                    onClick = {
                        if (selectedSupplierForPurchase == null) {
                            android.widget.Toast.makeText(context, "الرجاء تحديد المورد المعتمد لهذه العملية!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (newPurchaseItems.isEmpty()) {
                            android.widget.Toast.makeText(context, "الرجاء إدراج صنف وارد واحد على الأقل!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        val finalDate = deedDateInput.ifEmpty { Helpers.getCurrentDateTime() }

                        val finalPaid = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) {
                            (paidBuyAmountYer + paidBuyAmountSar)
                        } else if (purchaseTotalYer > 0.0) {
                            paidBuyAmountYer
                        } else if (purchaseTotalSar > 0.0) {
                            paidBuyAmountSar
                        } else {
                            paidBuyAmount
                        }

                        if (editingPurchaseId == null) {
                            viewModel.executeSupplierPurchase(
                                supplierId = selectedSupplierForPurchase!!.id,
                                itemsPriceAndQty = newPurchaseItems.toList(),
                                paidAmount = finalPaid,
                                paymentMethod = purchasePaymentMethod,
                                paidAmountYer = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) paidBuyAmountYer else if (purchaseTotalYer > 0.0) finalPaid else 0.0,
                                paidAmountSar = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) paidBuyAmountSar else if (purchaseTotalSar > 0.0) finalPaid else 0.0,
                                paymentMethodYer = purchasePaymentMethodYer,
                                paymentMethodSar = purchasePaymentMethodSar,
                                customDate = finalDate
                            )
                            android.widget.Toast.makeText(context, "تم حفظ وتوريد الأصناف بنجاح للمخازن وحفظ القيود", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.editSupplierPurchase(
                                purchaseId = editingPurchaseId!!,
                                newSupplierId = selectedSupplierForPurchase!!.id,
                                newDate = finalDate,
                                newItemsPriceAndQty = newPurchaseItems.toList(),
                                newPaidAmount = finalPaid,
                                newPaymentMethod = purchasePaymentMethod,
                                paidAmountYer = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) paidBuyAmountYer else if (purchaseTotalYer > 0.0) finalPaid else 0.0,
                                paidAmountSar = if (purchaseTotalYer > 0.0 && purchaseTotalSar > 0.0) paidBuyAmountSar else if (purchaseTotalSar > 0.0) finalPaid else 0.0,
                                paymentMethodYer = purchasePaymentMethodYer,
                                paymentMethodSar = purchasePaymentMethodSar
                            )
                            android.widget.Toast.makeText(context, "تم تعديل سند التوريد وتحديث المخزون والقيود بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        isAddingPurchase = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("حفظ وتسجيل عملية التوريد والمشتريات", color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            // General suppliers directory and purchase statements list
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Segment Choice: Suppliers Directory or Past Purchases List
                var selectedTab by remember { mutableStateOf(0) }
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = PalBlackNormal,
                    contentColor = PalGreenNormal,
                    divider = { HorizontalDivider(color = PalBorderColor) }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                "سجل الموردين المسجلين",
                                color = if (selectedTab == 0) PalWhitePure else PalWhiteMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                "فواتير المشتريات والتوريد",
                                color = if (selectedTab == 1) PalWhitePure else PalWhiteMuted,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    )
                }

                // Reports and Refresh action buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showSupplierReportsDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1.3f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Assessment, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("التقارير والمشتريات", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = {
                            android.widget.Toast.makeText(context, "تم تحديث سجلات المشتريات والموردين بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("تحديث البيانات", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                if (selectedTab == 0) {
                    // Search box for suppliers
                    OutlinedTextField(
                        value = supplierSearchQuery,
                        onValueChange = { supplierSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("supplier_search_bar"),
                        placeholder = { Text("ابحث عن مورد باسمه أو رقم هاتفه...", color = PalWhiteMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = PalIconUnified) },
                        trailingIcon = {
                            if (supplierSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { supplierSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = PalIconUnified)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedContainerColor = PalBlackNormal,
                            unfocusedContainerColor = PalBlackNormal
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    val filteredSuppliers = remember(suppliersList, supplierSearchQuery) {
                        suppliersList.filter {
                            it.name.contains(supplierSearchQuery, ignoreCase = true) ||
                            it.phone.contains(supplierSearchQuery)
                        }
                    }

                    // Suppliers List
                    if (filteredSuppliers.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (supplierSearchQuery.isEmpty()) "لا يوجد موردين مسجلين" else "لا توجد نتائج مطابقة للبحث",
                                color = PalWhiteMuted
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredSuppliers) { supplier ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                    border = BorderStroke(1.dp, PalBorderColor),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp)
                                    ) {
                                        // Deck 1: Info and Balance
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(supplier.name, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Text("هاتف: ${supplier.phone.ifEmpty { "غير محدد" }}", color = PalWhiteMuted, fontSize = 12.sp)
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("حساب المورد (علينا له)", color = PalWhiteMuted, fontSize = 10.sp)
                                                val sPurchases = allPurchasesList.filter { it.supplierId == supplier.id }
                                                val sDebtYer = sPurchases.sumOf { it.debtRemainingYer }
                                                val sDebtSar = sPurchases.sumOf { it.debtRemainingSar }
                                                if (sDebtYer > 0 || sDebtSar > 0) {
                                                    if (sDebtYer > 0) {
                                                        Text(Helpers.formatWithCurrency(sDebtYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                    if (sDebtSar > 0) {
                                                        Text(Helpers.formatWithCurrency(sDebtSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                    }
                                                } else {
                                                    Text("0 ريال يمني", color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = PalBorderColor, modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(10.dp))

                                        // Deck 2: Actions Bar
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            AssistButton(
                                                onClick = {
                                                    selectedSupplierForStatement = supplier
                                                    selectedSupplierStatementPeriod = "الكل"
                                                    customSupplierStatementValue = ""
                                                    customSupplierStatementType = "date"
                                                    showSupplierStatementDialog = true
                                                },
                                                icon = Icons.Default.ReceiptLong,
                                                text = "كشف حساب",
                                                tint = PalIconUnified
                                            )

                                            AssistButton(
                                                onClick = {
                                                    selectedSupplierForPurchase = supplier
                                                    newPurchaseItems.clear()
                                                    paidAmountInput = ""
                                                    editingPurchaseId = null
                                                    deedDateInput = Helpers.getCurrentDateTime()
                                                    purchasePaymentMethod = "نقداً"
                                                    isAddingPurchase = true
                                                },
                                                icon = Icons.Default.CloudUpload,
                                                text = "توريد بضاعة",
                                                tint = PalIconUnified
                                            )

                                            AssistButton(
                                                onClick = {
                                                    selectedSupplierForReturn = supplier
                                                    selectedItemForReturn = null
                                                    returnedQtyInput = ""
                                                    returnedPriceInput = ""
                                                    refundedAmountInput = ""
                                                    returnNotesInput = ""
                                                    returnDateInput = Helpers.getCurrentDateTime()
                                                    showAddReturnDialog = true
                                                },
                                                icon = Icons.Default.AssignmentReturn,
                                                text = "إضافة مرتجع",
                                                tint = PalIconUnified
                                            )

                                            AssistButton(
                                                onClick = {
                                                    selectedSupplierForPayment = supplier
                                                    accountCurrency = currencyConfig.supplierAccountCurrencies().first()
                                                    paymentAmountInput = ""
                                                    selectedPurchaseForPayment = null
                                                    useAutoPaymentDate = true
                                                    customPaymentDate = Helpers.getCurrentDateTime()
                                                    paymentNotesInput = ""
                                                    paymentBankNameInput = ""
                                                    demarAmountYer = ""
                                                    demarTypeYer = ""
                                                    demarNumberYer = ""
                                                    demarAmountSar = ""
                                                    demarTypeSar = ""
                                                    demarNumberSar = ""
                                                    showSupplierPaymentDialog = true
                                                 },
                                                 icon = Icons.Default.Payments,
                                                 text = "تسديد المورد",
                                                 tint = PalIconUnified
                                            )

                                            AssistButton(
                                                onClick = {
                                                    selectedSupplierForEdit = supplier
                                                    supplierFormName = supplier.name
                                                    supplierFormPhone = supplier.phone
                                                    supplierFormRegion = supplier.region
                                                    showAddSupplierDialog = true
                                                },
                                                icon = Icons.Default.Edit,
                                                text = "تعديل",
                                                tint = PalIconUnified
                                            )

                                            AssistButton(
                                                onClick = {
                                                    selectedSupplierForEdit = supplier
                                                    showDeleteConfirm = true
                                                },
                                                icon = Icons.Default.Delete,
                                                text = "حذف",
                                                tint = PalRedNormal
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    val filteredPurchases = remember(allPurchasesList, suppliersList, purchaseSearchQuery, selectedPeriod, customPeriodValue, customPeriodType) {
                        allPurchasesList.filter { p ->
                            val matchesPeriod = Helpers.matchesPeriod(p.date, selectedPeriod, customPeriodValue, customPeriodType)
                            if (!matchesPeriod) return@filter false

                            val sup = suppliersList.find { it.id == p.supplierId }
                            val nameMatch = sup?.name?.contains(purchaseSearchQuery, ignoreCase = true) == true
                            val phoneMatch = sup?.phone?.contains(purchaseSearchQuery) == true
                            val idMatch = p.id.toString() == purchaseSearchQuery.trim()
                            val dateMatch = p.date.contains(purchaseSearchQuery)
                            val methodMatch = p.paymentMethod.contains(purchaseSearchQuery)
                            val amountMatch = p.totalAmount.toString().contains(purchaseSearchQuery) || p.paidAmount.toString().contains(purchaseSearchQuery) || p.debtRemaining.toString().contains(purchaseSearchQuery)
                            
                            purchaseSearchQuery.isEmpty() || nameMatch || phoneMatch || idMatch || dateMatch || methodMatch || amountMatch
                        }.sortedBy { it.date }
                    }

                    OutlinedTextField(
                        value = purchaseSearchQuery,
                        onValueChange = { purchaseSearchQuery = it },
                        modifier = Modifier.fillMaxWidth().testTag("purchase_search_bar"),
                        placeholder = { Text("بحث متقدم (مورد، هاتف، رقم، تاريخ، مبلغ، سداد)...", color = PalWhiteMuted, fontSize = 12.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث", tint = PalIconUnified) },
                        trailingIcon = {
                            if (purchaseSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { purchaseSearchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "مسح", tint = PalIconUnified)
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedContainerColor = PalBlackNormal,
                            unfocusedContainerColor = PalBlackNormal
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )

                    if (filteredPurchases.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text(if (purchaseSearchQuery.isEmpty()) "لا توجد فواتير مشتريات وتوريد مسجلة حالياً" else "لا توجد نتائج مطابقة للبحث السريع", color = PalWhiteMuted)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filteredPurchases) { purchase ->
                                val sup = suppliersList.find { it.id == purchase.supplierId }
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                    border = BorderStroke(1.dp, PalBorderColor),
                                    shape = RoundedCornerShape(14.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewingPurchase = purchase
                                            showPurchaseDetailsDialog = true
                                        }
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("سند شراء #${purchase.id}", color = PalWhiteMuted, fontSize = 11.sp)
                                            Text(purchase.date, color = PalWhiteMuted, fontSize = 11.sp)
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("المورد: ${sup?.name ?: "مورد مجهول"}", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 15.sp)

                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(if (purchase.totalAmountYer > 0.0 && purchase.totalAmountSar > 0.0) "إجمالي (متعدد)" else "إجمالي الفاتورة", color = PalWhiteMuted, fontSize = 11.sp)
                                                Text(if (purchase.totalAmountYer > 0.0 && purchase.totalAmountSar > 0.0) "يمني: ${purchase.totalAmountYer.toInt()}\nسعودي: ${purchase.totalAmountSar.toInt()}" else Helpers.formatWithCurrency(purchase.totalAmount, purchase.currency), color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                            Column {
                                                Text("المدفوع للمورد", color = PalWhiteMuted, fontSize = 11.sp)
                                                Text(if (purchase.totalAmountYer > 0.0 && purchase.totalAmountSar > 0.0) "يمني: ${purchase.paidAmountYer.toInt()}\nسعودي: ${purchase.paidAmountSar.toInt()}" else Helpers.formatWithCurrency(purchase.paidAmount, purchase.currency), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                Text("الآجل علينا متبقي", color = PalWhiteMuted, fontSize = 11.sp)
                                                Text(if (purchase.totalAmountYer > 0.0 && purchase.totalAmountSar > 0.0) "يمني: ${purchase.debtRemainingYer.toInt()}\nسعودي: ${purchase.debtRemainingSar.toInt()}" else Helpers.formatWithCurrency(purchase.debtRemaining, purchase.currency), color = if (purchase.debtRemaining > 0) PalRedNormal else PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(10.dp))
                                        HorizontalDivider(color = PalBorderColor)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("انقر للاطلاع والتصدير", color = PalWhiteMuted, fontSize = 11.sp)
                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        editingPurchaseId = purchase.id
                                                        selectedSupplierForPurchase = suppliersList.find { it.id == purchase.supplierId }
                                                        val itemsForThisPurchase = allPurchaseItems.filter { it.purchaseId == purchase.id }
                                                        val loadedItems = itemsForThisPurchase.map { pi ->
                                                            val invItem = inventoryItems.find { it.id == pi.itemId } ?: com.example.data.InventoryItem(id = pi.itemId, name = pi.itemName, quantity = pi.quantity, buyPrice = pi.unitPrice, sellPrice = 0.0, lowStockThreshold = 5.0)
                                                            Triple(invItem, pi.quantity, pi.unitPrice)
                                                        }
                                                        newPurchaseItems.clear()
                                                        newPurchaseItems.addAll(loadedItems)
                                                        paidAmountInput = purchase.paidAmount.toString()
                                                        purchasePaymentMethod = purchase.paymentMethod
                                                        deedDateInput = purchase.date
                                                        isAddingPurchase = true
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "تعديل الفاتورة", tint = PalIconUnified)
                                                }
                                                IconButton(
                                                    onClick = {
                                                        purchaseToVoid = purchase
                                                        showVoidConfirm = true
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "إلغاء المشتريات", tint = PalRedNormal)
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
        }
    }

    // --- Add / Edit Supplier Dialog ---
    if (showAddSupplierDialog) {
        AlertDialog(
            onDismissRequest = { showAddSupplierDialog = false },
            title = { Text(if (selectedSupplierForEdit == null) "إضافة مورد جديد" else "تعديل بيانات المورد", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Row 1: Supplier Name
                    AccountingTextField(
                        value = supplierFormName,
                        onValueChange = { supplierFormName = it },
                        label = "اسم المورد المسجل",
                        placeholder = "مثال: وكالة الأمل، مؤسسة القدس"
                    )

                    // Row 2: Phone & Region
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = supplierFormPhone,
                                onValueChange = { supplierFormPhone = it },
                                label = "رقم هاتف المورد المعتمد",
                                placeholder = "77xxxxxxx"
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = supplierFormRegion,
                                onValueChange = { supplierFormRegion = it },
                                label = "المنطقة أو المحافظة",
                                placeholder = "مثال: صنعاء، تعز"
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedSupplierForEdit != null) {
                        Button(
                            onClick = {
                                showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("حذف المورد", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (supplierFormName.trim().isEmpty()) {
                                android.widget.Toast.makeText(context, "الرجاء إدخال اسم المورد!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (selectedSupplierForEdit == null) {
                                viewModel.createSupplier(supplierFormName, supplierFormPhone, supplierFormRegion)
                            } else {
                                viewModel.updateSupplier(selectedSupplierForEdit!!.copy(name = supplierFormName, phone = supplierFormPhone, region = supplierFormRegion))
                            }
                            showAddSupplierDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSupplierDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Supplier Product Selection Picker ---
    if (showProductSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showProductSelectionDialog = false },
            title = { Text("إدراج صنف توريد قات", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    var isCustomItem by remember { mutableStateOf(false) }
                    var customItemNameInput by remember { mutableStateOf("") }
                    var customItemSellPriceInput by remember { mutableStateOf("") }
                    var customItemNumberInput by remember { mutableStateOf("") }
                    var pickedProd by remember { mutableStateOf<InventoryItem?>(null) }
                    var supplyQty by remember { mutableStateOf("10") }
                    var supplyBuyPrice by remember { mutableStateOf("") }
                    var customItemCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }

                    // Toggle row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isCustomItem = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (!isCustomItem) PalGreenNormal else PalBlackDark,
                                contentColor = if (!isCustomItem) Color.White else PalWhiteMuted
                            ),
                            border = if (!isCustomItem) null else BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("اختيار صنف موجود", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { isCustomItem = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isCustomItem) PalGreenNormal else PalBlackDark,
                                contentColor = if (isCustomItem) Color.White else PalWhiteMuted
                            ),
                            border = if (isCustomItem) null else BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("إدخال صنف جديد يدوياً", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    if (!isCustomItem) {
                        Text("اختر الصنف لتوريده للمخزن:", color = PalWhiteMuted, fontSize = 12.sp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(110.dp)
                                .background(PalBlackDark, RoundedCornerShape(10.dp))
                                .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                                .verticalScroll(rememberScrollState())
                        ) {
                            Column {
                                inventoryItems.forEach { prod ->
                                    val isSelProd = pickedProd == prod
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(if (isSelProd) PalSoftGreenTint else Color.Transparent)
                                            .clickable {
                                                pickedProd = prod
                                                supplyBuyPrice = prod.buyPrice.toString()
                                            }
                                            .padding(10.dp)
                                    ) {
                                        Text(
                                            prod.name,
                                            color = if (isSelProd) PalGreenNormal else PalWhitePure,
                                            fontWeight = if (isSelProd) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 13.sp
                                        )
                                    }
                                    HorizontalDivider(color = PalBorderColor)
                                }
                            }
                        }
                    } else {
                        val currentQatTypes = remember(com.example.util.BrandingManager.qatTypes) {
                            com.example.util.BrandingManager.qatTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        }
                        
                        // Custom Item Name & Number in parallel row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(modifier = Modifier.weight(2f)) {
                                AccountingTextField(
                                    value = customItemNameInput,
                                    onValueChange = { customItemNameInput = it },
                                    label = "اسم الصنف الجديد",
                                    placeholder = com.example.util.BrandingManager.qatTypes
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                AccountingTextField(
                                    value = customItemNumberInput,
                                    onValueChange = { customItemNumberInput = it },
                                    label = "رقم الصنف",
                                    placeholder = "الرقم"
                                )
                            }
                        }

                        if (currentQatTypes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("أنواع القات المتاحة للاختيار السريع:", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentQatTypes.forEach { type ->
                                    val isSelType = customItemNameInput == type
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, if (isSelType) PalGreenNormal else PalBorderColor, RoundedCornerShape(10.dp))
                                            .background(if (isSelType) PalSoftGreenTint else PalBlackDark, RoundedCornerShape(10.dp))
                                            .clickable { customItemNameInput = type }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Text(type, color = if (isSelType) PalGreenNormal else PalWhitePure, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Currency selection for custom item buy price
                        CurrencySelectionRow(
                            selectedCurrency = customItemCurrency,
                            currencyConfig = currencyConfig,
                            onCurrencySelected = { customItemCurrency = it },
                            title = "عملة شراء الصنف الجديد"
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Row of Quantity & Buy Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = supplyQty,
                                onValueChange = { supplyQty = it },
                                label = "الكمية المستلمة (حبة)",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = supplyBuyPrice,
                                onValueChange = { supplyBuyPrice = it },
                                label = "سعر شراء الحبة الواحد",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val qty = supplyQty.toDoubleOrNull() ?: 0.0
                            val bPrice = supplyBuyPrice.toDoubleOrNull() ?: 0.0
                            if (qty <= 0.0 || bPrice <= 0.0) {
                                android.widget.Toast.makeText(context, "الرجاء كتابة كمية وسعر شراء أكبر من الصفر!", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            if (!isCustomItem) {
                                if (pickedProd != null) {
                                    newPurchaseItems.add(Triple(pickedProd!!, qty, bPrice))
                                    showProductSelectionDialog = false
                                } else {
                                    android.widget.Toast.makeText(context, "يرجى تحديد الصنف أولاً!", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                if (customItemNameInput.trim().isEmpty()) {
                                    android.widget.Toast.makeText(context, "يرجى كتابة اسم الصنف الجديد!", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                if (!currencyConfig.isSingleCurrencyAllowed(customItemCurrency)) {
                                    android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(customItemCurrency), android.widget.Toast.LENGTH_LONG).show()
                                    return@Button
                                }
                                val sPrice = 0.0
                                scope.launch {
                                    val newItem = InventoryItem(
                                        id = 0,
                                        name = customItemNameInput.trim(),
                                        quantity = 0.0,
                                        buyPrice = bPrice,
                                        sellPrice = sPrice,
                                        lowStockThreshold = 5.0,
                                        buyPriceCurrency = customItemCurrency,
                                        itemNumber = customItemNumberInput.trim(),
                                        supplierId = selectedSupplierForPurchase?.id ?: 0,
                                        supplierName = selectedSupplierForPurchase?.name ?: ""
                                    )
                                    val generatedId = viewModel.saveInventoryItemDirectly(newItem)
                                    val completeItem = newItem.copy(id = generatedId)
                                    newPurchaseItems.add(Triple(completeItem, qty, bPrice))
                                    showProductSelectionDialog = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("إدراج بفاتورة التوريد", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {},
            containerColor = PalBlackNormal
        )
    }

    // --- Details Viewing Dialog ---
    if (showPurchaseDetailsDialog && viewingPurchase != null) {
        val sup = suppliersList.find { it.id == viewingPurchase!!.supplierId }
        val totalYer = if (viewingPurchase!!.totalAmountYer > 0.0) viewingPurchase!!.totalAmountYer else if (viewingPurchase!!.currency.contains("يمني")) viewingPurchase!!.totalAmount else 0.0
        val totalSar = if (viewingPurchase!!.totalAmountSar > 0.0) viewingPurchase!!.totalAmountSar else if (viewingPurchase!!.currency.contains("سعودي")) viewingPurchase!!.totalAmount else 0.0
        val paidYer = if (viewingPurchase!!.totalAmountYer > 0.0) viewingPurchase!!.paidAmountYer else if (viewingPurchase!!.currency.contains("يمني")) viewingPurchase!!.paidAmount else 0.0
        val paidSar = if (viewingPurchase!!.totalAmountSar > 0.0) viewingPurchase!!.paidAmountSar else if (viewingPurchase!!.currency.contains("سعودي")) viewingPurchase!!.paidAmount else 0.0
        val debtYer = if (viewingPurchase!!.totalAmountYer > 0.0) viewingPurchase!!.debtRemainingYer else if (viewingPurchase!!.currency.contains("يمني")) viewingPurchase!!.debtRemaining else 0.0
        val debtSar = if (viewingPurchase!!.totalAmountSar > 0.0) viewingPurchase!!.debtRemainingSar else if (viewingPurchase!!.currency.contains("سعودي")) viewingPurchase!!.debtRemaining else 0.0

        val yerDetailsItems = viewingPurchaseItems.filter { it.currency.contains("يمني") || (!it.currency.contains("سعودي") && viewingPurchase!!.currency.contains("يمني")) }
        val sarDetailsItems = viewingPurchaseItems.filter { it.currency.contains("سعودي") || (!it.currency.contains("يمني") && viewingPurchase!!.currency.contains("سعودي")) }

        val msg = buildString {
            val isRet = viewingPurchase!!.totalAmount < 0 || viewingPurchase!!.paymentMethod.startsWith("مرتجع")
            val docType = if (isRet) "سند مرتجع بضاعة" else "أمر توريد بضاعة"
            appendLine(com.example.util.BrandingManager.buildShareHeader(docType))
            val supRegionSuffix = if (sup != null && sup.region.isNotEmpty()) " (${sup.region})" else ""
            appendLine("المورد: ${sup?.name ?: "مورد مجهول"}$supRegionSuffix")
            appendLine("مستند رقم: #${viewingPurchase!!.id}")
            appendLine("التاريخ والوقت: ${viewingPurchase!!.date}")
            appendLine("-------------------------")
            
            if (yerDetailsItems.isNotEmpty()) {
                appendLine("أصناف بالريال اليمني:")
                yerDetailsItems.forEach { item ->
                    appendLine("  • ${item.itemName} × ${Helpers.formatQuantity(item.quantity)} حبة = ${Helpers.formatWithCurrency(item.quantity * item.unitPrice, "ريال يمني")}")
                }
            }
            if (sarDetailsItems.isNotEmpty()) {
                appendLine("أصناف بالريال السعودي:")
                sarDetailsItems.forEach { item ->
                    appendLine("  • ${item.itemName} × ${Helpers.formatQuantity(item.quantity)} حبة = ${Helpers.formatWithCurrency(item.quantity * item.unitPrice, "ريال سعودي")}")
                }
            }
            appendLine("-------------------------")
            
            if (totalYer > 0.0 || totalSar > 0.0) {
                if (totalYer > 0.0) {
                    appendLine("بالريال اليمني:")
                    appendLine("  • الإجمالي الصافي: ${Helpers.formatWithCurrency(totalYer, "ريال يمني")}")
                    appendLine("  • المدفوع نقداً: ${Helpers.formatWithCurrency(paidYer, "ريال يمني")}")
                    val statusSuffix = if (debtYer < 0.0) " (فائض زائد لنا)" else ""
                    appendLine("  • المتبقي علينا: ${Helpers.formatWithCurrency(debtYer, "ريال يمني")}$statusSuffix")
                    if (viewingPurchase!!.paymentMethodYer.isNotEmpty()) {
                        appendLine("  • تفاصيل السداد: ${viewingPurchase!!.paymentMethodYer}")
                    }
                }
                if (totalSar > 0.0) {
                    appendLine("بالريال السعودي:")
                    appendLine("  • الإجمالي الصافي: ${Helpers.formatWithCurrency(totalSar, "ريال سعودي")}")
                    appendLine("  • المدفوع نقداً: ${Helpers.formatWithCurrency(paidSar, "ريال سعودي")}")
                    val statusSuffix = if (debtSar < 0.0) " (فائض زائد لنا)" else ""
                    appendLine("  • المتبقي علينا: ${Helpers.formatWithCurrency(debtSar, "ريال سعودي")}$statusSuffix")
                    if (viewingPurchase!!.paymentMethodSar.isNotEmpty()) {
                        appendLine("  • تفاصيل السداد: ${viewingPurchase!!.paymentMethodSar}")
                    }
                }
            } else {
                val dispTotal = if (totalSar > 0.0) totalSar else totalYer
                val dispPaid = if (totalSar > 0.0) paidSar else paidYer
                val dispDebt = if (totalSar > 0.0) debtSar else debtYer
                val dispCurr = if (totalSar > 0.0) "ريال سعودي" else "ريال يمني"
                appendLine("المجموع الإجمالي: ${Helpers.formatWithCurrency(dispTotal, dispCurr)}")
                appendLine("المدفوع نقداً للمورد: ${Helpers.formatWithCurrency(dispPaid, dispCurr)}")
                appendLine("الرصيد الآجل المتبقي: ${Helpers.formatWithCurrency(dispDebt, dispCurr)}")
            }
            appendLine("-------------------------")
            appendLine(com.example.util.BrandingManager.buildShareFooter())
        }

        AlertDialog(
            onDismissRequest = { showPurchaseDetailsDialog = false },
            title = { Text("تفاصيل المستند #${viewingPurchase!!.id}", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("المورد: ${sup?.name ?: "مورد مجهول"}", color = PalWhitePure, fontWeight = FontWeight.Bold)
                    Text("التاريخ والوقت: ${viewingPurchase!!.date}", color = PalWhiteMuted, fontSize = 12.sp)
                    HorizontalDivider(color = PalBorderColor)

                    if (yerDetailsItems.isNotEmpty()) {
                        Text("الأصناف بالريال اليمني:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        yerDetailsItems.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• " + item.itemName, color = PalWhitePure, fontSize = 12.sp)
                                Text("${Helpers.formatQuantity(item.quantity)} حبة × ${Helpers.formatWithCurrency(item.unitPrice, "ريال يمني")}", color = PalWhiteSoft, fontSize = 12.sp)
                            }
                        }
                    }

                    if (sarDetailsItems.isNotEmpty()) {
                        Text("الأصناف بالريال السعودي:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                        sarDetailsItems.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth().padding(start = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("• " + item.itemName, color = PalWhitePure, fontSize = 12.sp)
                                Text("${Helpers.formatQuantity(item.quantity)} حبة × ${Helpers.formatWithCurrency(item.unitPrice, "ريال سعودي")}", color = PalWhiteSoft, fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(color = PalBorderColor)

                    Text("سجل وقيود سداد الدفعات والبيان:", color = PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    val rawMethod = viewingPurchase!!.paymentMethod
                    val logs = rawMethod.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                    if (logs.isNotEmpty()) {
                        logs.forEach { log ->
                            Text("  • $log", color = PalSuccessNormal, fontSize = 11.sp)
                        }
                    } else {
                        Text("  • لا توجد عمليات تسديد مسجلة حتى الآن لهذا السند.", color = PalWhiteMuted, fontSize = 11.sp)
                    }

                    HorizontalDivider(color = PalBorderColor)

                    if (totalYer > 0.0 || totalSar > 0.0) {
                        Text("القيم الإجمالية بالتفصيل:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (totalYer > 0.0) {
                            Text("ريال يمني:", color = PalWhitePure, fontWeight = FontWeight.Bold)
                            Text("  إجمالي يمني: ${Helpers.formatWithCurrency(totalYer, "ريال يمني")}", color = PalWhitePure, fontSize = 12.sp)
                            Text("  مدفوع يمني: ${Helpers.formatWithCurrency(paidYer, "ريال يمني")}", color = PalSuccessNormal, fontSize = 12.sp)
                            val remainingLabel = if (debtYer < 0.0) "رصيد زائد لنا عند المورد" else "متبقي علينا"
                            Text("  $remainingLabel: ${Helpers.formatWithCurrency(java.lang.Math.abs(debtYer), "ريال يمني")}", color = if (debtYer > 0) PalRedNormal else PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        if (totalSar > 0.0) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("ريال سعودي:", color = PalWhitePure, fontWeight = FontWeight.Bold)
                            Text("  إجمالي سعودي: ${Helpers.formatWithCurrency(totalSar, "ريال سعودي")}", color = PalWhitePure, fontSize = 12.sp)
                            Text("  مدفوع سعودي: ${Helpers.formatWithCurrency(paidSar, "ريال سعودي")}", color = PalSuccessNormal, fontSize = 12.sp)
                            val remainingLabel = if (debtSar < 0.0) "رصيد زائد لنا عند المورد" else "متبقي علينا"
                            Text("  $remainingLabel: ${Helpers.formatWithCurrency(java.lang.Math.abs(debtSar), "ريال سعودي")}", color = if (debtSar > 0) PalRedNormal else PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        val dispTotal = if (totalSar > 0.0) totalSar else totalYer
                        val dispPaid = if (totalSar > 0.0) paidSar else paidYer
                        val dispDebt = if (totalSar > 0.0) debtSar else debtYer
                        val dispCurr = if (totalSar > 0.0) "ريال سعودي" else "ريال يمني"
                        Text("المجموع الإجمالي: ${Helpers.formatWithCurrency(dispTotal, dispCurr)}", color = PalWhitePure, fontWeight = FontWeight.Bold)
                        Text("المدفوع نقداً للمورد: ${Helpers.formatWithCurrency(dispPaid, dispCurr)}", color = PalSuccessNormal)
                        Text("الرصيد الآجل المتبقي ديون للمورد: ${Helpers.formatWithCurrency(dispDebt, dispCurr)}", color = if (dispDebt > 0) PalRedNormal else PalWhitePure)
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val headers = listOf("الصنف المورد", "الكمية", "سعر الوحدة", "العملة", "الإجمالي")
                                val rows = mutableListOf<List<String>>()
                                if (yerDetailsItems.isNotEmpty()) {
                                    yerDetailsItems.forEach {
                                        rows.add(listOf(it.itemName, "${Helpers.formatQuantity(it.quantity)} حبة", Helpers.formatMoney(it.unitPrice), "ريال يمني", Helpers.formatWithCurrency(it.quantity * it.unitPrice, "ريال يمني")))
                                    }
                                }
                                if (sarDetailsItems.isNotEmpty()) {
                                    sarDetailsItems.forEach {
                                        rows.add(listOf(it.itemName, "${Helpers.formatQuantity(it.quantity)} حبة", Helpers.formatMoney(it.unitPrice), "ريال سعودي", Helpers.formatWithCurrency(it.quantity * it.unitPrice, "ريال سعودي")))
                                    }
                                }

                                val totalsMap = mutableMapOf<String, String>()
                                if (totalYer > 0.0 || totalSar > 0.0) {
                                    if (totalYer > 0.0) {
                                        totalsMap["إجمالي التوريد الصافي بالريال اليمني"] = Helpers.formatWithCurrency(totalYer, "ريال يمني")
                                        totalsMap["المدفوع بالريال اليمني"] = Helpers.formatWithCurrency(paidYer, "ريال يمني")
                                        val labelStr = if (debtYer < 0.0) "الرصيد الزائد لنا باليمني" else "المتبقي علينا باليمني"
                                        totalsMap[labelStr] = Helpers.formatWithCurrency(java.lang.Math.abs(debtYer), "ريال يمني")
                                    }
                                    if (totalSar > 0.0) {
                                        totalsMap["-----------------------"] = "-----------------------"
                                        totalsMap["إجمالي التوريد الصافي بالريال السعودي"] = Helpers.formatWithCurrency(totalSar, "ريال سعودي")
                                        totalsMap["المدفوع بالريال السعودي"] = Helpers.formatWithCurrency(paidSar, "ريال سعودي")
                                        val labelStr = if (debtSar < 0.0) "الرصيد الزائد لنا بالسعودي" else "المتبقي علينا بالسعودي"
                                        totalsMap[labelStr] = Helpers.formatWithCurrency(java.lang.Math.abs(debtSar), "ريال سعودي")
                                    }
                                } else {
                                    val dispTotal = if (totalSar > 0.0) totalSar else totalYer
                                    val dispPaid = if (totalSar > 0.0) paidSar else paidYer
                                    val dispDebt = if (totalSar > 0.0) debtSar else debtYer
                                    val dispCurr = if (totalSar > 0.0) "ريال سعودي" else "ريال يمني"
                                    totalsMap["قيمة البضائع والتوريد الكلية"] = Helpers.formatWithCurrency(dispTotal, dispCurr)
                                    totalsMap["المبلغ المدفوع للمورد"] = Helpers.formatWithCurrency(dispPaid, dispCurr)
                                    totalsMap["المتبقي للدائن في الذمة"] = Helpers.formatWithCurrency(dispDebt, dispCurr)
                                }

                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "أمر توريد بضاعة - مستند رقم #${viewingPurchase!!.id}",
                                    headers = headers,
                                    rows = rows,
                                    totals = totalsMap
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
                                Helpers.shareViaWhatsApp(context, sup?.phone ?: "", msg)
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
                                Helpers.shareViaSMS(context, sup?.phone ?: "", msg)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            editPurchasePaidAmount = viewingPurchase!!.paidAmount.toString()
                            editPurchasePaymentMethod = viewingPurchase!!.paymentMethod
                            showEditPurchaseDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("تعديل فاتورة الشراء", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = { showPurchaseDetailsDialog = false }) {
                        Text("إغلاق", color = PalWhiteMuted)
                    }
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Edit Purchase Invoice Dialog ---
    if (showEditPurchaseDialog && viewingPurchase != null) {
        val sup = suppliersList.find { it.id == viewingPurchase!!.supplierId }
        AlertDialog(
            onDismissRequest = { showEditPurchaseDialog = false },
            title = {
                Text(
                    text = "تعديل فاتورة الشراء رقم #${viewingPurchase!!.id}",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "المورد: ${sup?.name ?: "مورد مجهول"}",
                        color = PalWhiteMuted,
                        fontSize = 14.sp
                    )
                    Text(
                        text = "إجمالي قيمة الفاتورة: ${Helpers.formatMoney(viewingPurchase!!.totalAmount)}",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold
                    )

                    OutlinedTextField(
                        value = editPurchasePaidAmount,
                        onValueChange = { editPurchasePaidAmount = it },
                        label = { Text("المبلغ المدفوع للمورد (ريال)", color = PalWhiteMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("طريقة التحصيل / الدفع:", color = PalWhiteSoft, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    val purchasePaymentMethods = listOf("نقداً", "آجل", "إيداع", "تحويل")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        purchasePaymentMethods.forEach { method ->
                            val isSel = editPurchasePaymentMethod == method
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { editPurchasePaymentMethod = method }
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
                        val newPaid = editPurchasePaidAmount.toDoubleOrNull() ?: 0.0
                        viewModel.updateSupplierPurchase(
                            purchaseId = viewingPurchase!!.id,
                            newPaidAmount = newPaid,
                            newPaymentMethod = editPurchasePaymentMethod
                        )
                        showEditPurchaseDialog = false
                        showPurchaseDetailsDialog = false
                        android.widget.Toast.makeText(context, "تم تعديل فاتورة الشراء وتعديل حسابات المورد بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ التعديلات", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPurchaseDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Void Purchase Dialog ---
    if (showVoidConfirm && purchaseToVoid != null) {
        val isReturn = purchaseToVoid!!.totalAmount < 0.0 || purchaseToVoid!!.paymentMethod.startsWith("مرتجع")
        val titleText = if (isReturn) "تأكيد حذف وإلغاء سند مرتجع البضاعة" else "تأكيد حذف وإلغاء سند توريد البضاعة"
        val bodyText = if (isReturn) {
            "هل أنت متأكد من رغبتك في حذف وإلغاء سند المرتجع رقم #${purchaseToVoid!!.id}؟ سيتم إعادة الكميات إلى المخازن تلقائياً وتسوية حساب المورد المالي."
        } else {
            "هل أنت متأكد من رغبتك في حذف وإلغاء سند التوريد رقم #${purchaseToVoid!!.id}؟ سيتم إسقاط الكميات المضافة للمخازن تلقائياً وتسوية حساب المورد المالي."
        }
        AlertDialog(
            onDismissRequest = { showVoidConfirm = false },
            title = { Text(titleText, color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right, fontWeight = FontWeight.Bold) },
            text = { Text(bodyText, color = PalWhiteMuted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.voidPurchase(purchaseToVoid!!.id)
                        showVoidConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
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

    // --- Edit Purchase Item Dialog ---
    if (showEditPurchaseItemDialog && editingSupplierPurchaseItemState != null) {
        AlertDialog(
            onDismissRequest = { showEditPurchaseItemDialog = false },
            title = {
                Text(
                    text = "تعديل صنف توريد المخزون",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = editPurchaseItemNameInput,
                        onValueChange = { editPurchaseItemNameInput = it },
                        label = { Text("اسم الصنف", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPurchaseItemQtyInput,
                        onValueChange = { editPurchaseItemQtyInput = it },
                        label = { Text("العدد المستورد (الكمية)", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPurchaseItemPriceInput,
                        onValueChange = { editPurchaseItemPriceInput = it },
                        label = { Text("سعر الحبة (ريال)", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = editPurchaseItemNumberInput,
                        onValueChange = { editPurchaseItemNumberInput = it },
                        label = { Text("الرقم", color = PalWhiteMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val qValue = editPurchaseItemQtyInput.toDoubleOrNull() ?: 0.0
                        val pValue = editPurchaseItemPriceInput.toDoubleOrNull() ?: 0.0
                        viewModel.editSupplierPurchaseItem(
                            purchaseItemId = editingSupplierPurchaseItemState!!.id,
                            newItemName = editPurchaseItemNameInput.trim(),
                            newQuantity = qValue,
                            newUnitPrice = pValue,
                            newItemNumber = editPurchaseItemNumberInput.trim()
                        )
                        showEditPurchaseItemDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ التغييرات والتحديث الفوري", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditPurchaseItemDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showDeleteConfirm && selectedSupplierForEdit != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "تأكيد حذف المورد",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف المورد (${selectedSupplierForEdit!!.name}) نهائياً؟ سيتم مسح حساب المورد السحابي وكل السجلات المالية المرتبطة به.",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSupplier(selectedSupplierForEdit!!)
                        showDeleteConfirm = false
                        showAddSupplierDialog = false
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

    if (showSupplierReportsDialog) {
        val periodPurchases = allPurchasesList.filter {
            Helpers.matchesPeriod(
                dateStr = it.date,
                period = selectedSupplierReportPeriod,
                customValue = customSupplierReportValue,
                customType = customSupplierReportType
            )
        }
        val periodCostYer = periodPurchases.sumOf { it.totalAmountYer }
        val periodCostSar = periodPurchases.sumOf { it.totalAmountSar }

        val periodPaidYer = periodPurchases.sumOf { it.paidAmountYer }
        val periodPaidSar = periodPurchases.sumOf { it.paidAmountSar }

        val periodDebtYer = periodPurchases.sumOf { it.debtRemainingYer }
        val periodDebtSar = periodPurchases.sumOf { it.debtRemainingSar }

        val outstandingYer = allPurchasesList.sumOf { it.debtRemainingYer }
        val outstandingSar = allPurchasesList.sumOf { it.debtRemainingSar }

        AlertDialog(
            onDismissRequest = { showSupplierReportsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تقارير المشتريات والتوريد للمفرش",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "اختر دورة أو فترة التقرير لتجهيز إحصائيات المشتريات وحركات سداد الموردين:",
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
                            val isSel = selectedSupplierReportPeriod == period
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
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when (period) {
                                        "يومي" -> "يومية (اليوم)"
                                        "أسبوعي" -> "أسبوعي"
                                        "شهري" -> "شهرية (الشهر)"
                                        "سنوي" -> "سنوية (العام)"
                                        "تقويم مخصص" -> "تقويم مخصص"
                                        else -> period
                                    },
                                    color = fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (selectedSupplierReportPeriod == "تقويم مخصص" || selectedSupplierReportPeriod == "أسبوعي") {
                        UnifiedPeriodSelector(
                            initialType = if (selectedSupplierReportPeriod == "أسبوعي") "أسبوع" else (if (customSupplierReportType == "date") "يوم" else if (customSupplierReportType == "month") "شهر" else "سنة"),
                            initialValue = if (selectedSupplierReportPeriod == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                                "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                            } else {
                                customPeriodValue.ifEmpty { Helpers.getCurrentDate() }
                            },
                            onPeriodChanged = { type, formattedValue ->
                                customPeriodType = if (type == "يوم" || type == "date") "يوم" else if (type == "شهر" || type == "month") "شهر" else "سنة"
                                customPeriodValue = formattedValue
                                supplierGeneralReportDate = Helpers.formatCustomPeriodToArabic(type, formattedValue)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = supplierGeneralReportDate,
                        onValueChange = { supplierGeneralReportDate = it },
                        label = { Text("تعيين وتخصيص تاريخ التقرير المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("supplier_general_report_date_input"),
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
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي قيمة المشتريات:", color = PalWhiteSoft, fontSize = 12.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Helpers.formatWithCurrency(periodCostYer, "ريال يمني"), color = PalWhitePure, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(Helpers.formatWithCurrency(periodCostSar, "ريال سعودي"), color = PalWhitePure, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("المسدد نقداً للموردين:", color = PalWhiteSoft, fontSize = 12.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Helpers.formatWithCurrency(periodPaidYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("الديون المتبقية للموردين:", color = PalWhiteSoft, fontSize = 12.sp)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Helpers.formatWithCurrency(periodDebtYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                Text(Helpers.formatWithCurrency(periodDebtSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "كشف مفصل بالتوريد والتواريخ ($selectedSupplierReportPeriod):",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (periodPurchases.isEmpty()) {
                                Text(
                                    text = "لا توجد عمليات مبيعات أو توريد مشتريات مسجلة لهذه الفترة.",
                                    color = PalWhiteMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                periodPurchases.forEach { purchase ->
                                    val supName = suppliersList.find { it.id == purchase.supplierId }?.name ?: "مورد"
                                    val isReturn = purchase.totalAmount < 0 || purchase.paymentMethod.startsWith("مرتجع")
                                    val valPrefix = if (isReturn) "مرتجع: " else "توريد: "
                                    val absAmount = java.lang.Math.abs(purchase.totalAmount)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("$supName: $valPrefix${Helpers.formatMoney(absAmount)}", color = if (isReturn) PalGoldMuted else PalWhitePure, fontSize = 10.sp)
                                        Text(purchase.date.split(" ")[0], color = PalWhiteMuted, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val reportMsg = buildString {
                        appendLine(com.example.util.BrandingManager.buildShareHeader("تقرير المشتريات والتوريد وحسابات الموردين التفصيلي"))
                        appendLine("فترة التقرير: $selectedSupplierReportPeriod")
                        appendLine("التاريخ والوقت: $supplierGeneralReportDate")
                        appendLine("---------------------------------")
                        if (periodPurchases.isNotEmpty()) {
                            appendLine("كشف تفاصيل عمليات الشراء والتوريد بالتفصيل والتواريخ:")
                            periodPurchases.forEachIndexed { index, p ->
                                val sup = suppliersList.find { it.id == p.supplierId }
                                val supName = sup?.name ?: "مورد غير معروف"
                                val supRegionSuffix = if (sup != null && sup.region.isNotEmpty()) " (${sup.region})" else ""
                                val isReturn = p.totalAmount < 0 || p.paymentMethod.startsWith("مرتجع")
                                val docTitle = if (isReturn) "سند مرتجع بضاعة" else "سند توريد"
                                val absTotal = java.lang.Math.abs(p.totalAmount)
                                val absPaid = java.lang.Math.abs(p.paidAmount)
                                val absDebt = java.lang.Math.abs(p.debtRemaining)

                                appendLine("${index + 1}) $docTitle #${p.id} - المورد: $supName$supRegionSuffix")
                                appendLine("   التاريخ والوقت: ${p.date}")
                                val realCurrency = if (p.currency.contains("سعودي") || p.currency.contains("SAR")) "ريال سعودي" else "ريال يمني"
                                appendLine("   العملة: $realCurrency")
                                appendLine("   طريقة الدفع/البيان: ${p.paymentMethod}")
                                appendLine("   قيمة الفاتورة الكلية: ${Helpers.formatWithCurrency(absTotal, realCurrency)}")
                                appendLine("   المسترد/المدفوع نقداً: ${Helpers.formatWithCurrency(absPaid, realCurrency)}")
                                appendLine("   المتبقي آجل ديون: ${Helpers.formatWithCurrency(absDebt, realCurrency)}")
                                appendLine("   -------------------")
                            }
                            appendLine("---------------------------------")
                        } else {
                            appendLine("لا توجد فواتير مشتريات وتوريد مسجلة حالياً لهذه الفترة.")
                        }
                        appendLine("إجمالي قيمة المشتريات:")
                        appendLine("- بالريال اليمني: ${Helpers.formatWithCurrency(periodCostYer, "ريال يمني")}")
                        appendLine("- بالريال السعودي: ${Helpers.formatWithCurrency(periodCostSar, "ريال سعودي")}")
                        appendLine("إجمالي المسدد نقداً للموردين:")
                        appendLine("- بالريال اليمني: ${Helpers.formatWithCurrency(periodPaidYer, "ريال يمني")}")
                        appendLine("- بالريال السعودي: ${Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي")}")
                        appendLine("إجمالي الديون الآجلة الجديدة:")
                        appendLine("- بالريال اليمني: ${Helpers.formatWithCurrency(periodDebtYer, "ريال يمني")}")
                        appendLine("- بالريال السعودي: ${Helpers.formatWithCurrency(periodDebtSar, "ريال سعودي")}")
                        appendLine("---------------------------------")
                        appendLine("إجمالي الذمم والديون المتبقية المعلقة للموردين حالياً:")
                        appendLine("- بالريال اليمني: ${Helpers.formatWithCurrency(outstandingYer, "ريال يمني")}")
                        appendLine("- بالريال السعودي: ${Helpers.formatWithCurrency(outstandingSar, "ريال سعودي")}")
                        appendLine("---------------------------------")
                        appendLine(com.example.util.BrandingManager.buildShareFooter())
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val bAgencyTitle = com.example.util.BrandingManager.resolvedAgencyName()
                                val tPrefix = when (selectedSupplierReportPeriod) {
                                    "يومي" -> "تقرير يومي"
                                    "أسبوعي" -> "تقرير أسبوعي"
                                    "شهري" -> "تقرير شهري"
                                    "سنوي" -> "تقرير سنوي"
                                    "تقويم مخصص" -> "التقرير المخصص ($customSupplierReportValue)"
                                    else -> "تقرير دوري"
                                }
                                val headers = listOf("التاريخ والسند", "تفاصيل العملية والأصناف المشمولة", "العملة", "المبلغ الكلي", "المدفوع/المسترد", "المتبقي الآجل", "طريقة الدفع/البيان")
                                val rows = periodPurchases.map { p ->
                                    val sup = suppliersList.find { it.id == p.supplierId }
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
                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "$tPrefix مشتريات وتوريد $bAgencyTitle التفصيلي",
                                    headers = headers,
                                    rows = rows,
                                    totals = mapOf(
                                        "إجمالي المشتريات بالريال اليمني" to Helpers.formatWithCurrency(periodCostYer, "ريال يمني"),
                                        "إجمالي المشتريات بالريال السعودي" to Helpers.formatWithCurrency(periodCostSar, "ريال سعودي"),
                                        "المسدد للموردين بالريال اليمني" to Helpers.formatWithCurrency(periodPaidYer, "ريال يمني"),
                                        "المسدد للموردين بالريال السعودي" to Helpers.formatWithCurrency(periodPaidSar, "ريال سعودي"),
                                        "الديون الجديدة بالريال اليمني" to Helpers.formatWithCurrency(periodDebtYer, "ريال يمني"),
                                        "الديون الجديدة بالريال السعودي" to Helpers.formatWithCurrency(periodDebtSar, "ريال سعودي"),
                                        "الدين الحالي المعلق بالريال اليمني" to Helpers.formatWithCurrency(outstandingYer, "ريال يمني"),
                                        "الدين الحالي المعلق بالريال السعودي" to Helpers.formatWithCurrency(outstandingSar, "ريال سعودي")
                                    ),
                                    customDate = Helpers.getCurrentDateTime(),
                                    reportPeriod = supplierGeneralReportDate
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
                                Helpers.shareViaWhatsApp(context, "", reportMsg)
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
                                bluetoothPrintText = reportMsg
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
                TextButton(onClick = { showSupplierReportsDialog = false }) {
                    Text("إغلاق", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showSupplierStatementDialog && selectedSupplierForStatement != null) {
        val todayStrStr = Helpers.getCurrentDate()
        val curMonthStrStr = todayStrStr.substring(0, 7)
        val curYearStrStr = todayStrStr.substring(0, 4)

        val filteredStatementPurchases = allPurchasesList.filter { it.supplierId == selectedSupplierForStatement!!.id }.filter { p ->
            Helpers.matchesPeriod(p.date, selectedSupplierStatementPeriod, customSupplierStatementValue, customSupplierStatementType)
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

        val sPurchasesAll = allPurchasesList.filter { it.supplierId == selectedSupplierForStatement!!.id }
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
            Helpers.isBeforePeriod(p.date, selectedSupplierStatementPeriod, customSupplierStatementValue, customSupplierStatementType)
        }
        val prevOutstandingYer = sPurchasesBeforePeriod.sumOf { it.debtRemainingYer }
        val prevOutstandingSar = sPurchasesBeforePeriod.sumOf { it.debtRemainingSar }

        val statementMsg = buildString {
            appendLine(com.example.util.BrandingManager.buildShareHeader("كشف حساب محاسبي وتفصيلي للمورد"))
            appendLine("المورد: ${selectedSupplierForStatement!!.name}")
            if (selectedSupplierForStatement!!.region.isNotEmpty()) {
                appendLine("المنطقة/المحافظة: ${selectedSupplierForStatement!!.region}")
            }
            appendLine("رقم الهاتف: ${selectedSupplierForStatement!!.phone.ifEmpty { "غير محدد" }}")
            appendLine("الفترة المعنية: $selectedSupplierStatementPeriod")
            appendLine("تاريخ إصدار التقرير: $supplierReportDate")
            
            val showPrevYer = prevOutstandingYer > 0.0
            val showPrevSar = prevOutstandingSar > 0.0
            if (showPrevYer || showPrevSar) {
                appendLine("---------------------------------------")
                appendLine("الأرصدة المفتوحة (الرصيد السابق):")
                if (showPrevYer) {
                    appendLine("- بالريال اليمني: ${Helpers.formatWithCurrency(prevOutstandingYer, "ريال يمني")}")
                }
                if (showPrevSar) {
                    appendLine("- بالريال السعودي: ${Helpers.formatWithCurrency(prevOutstandingSar, "ريال سعودي")}")
                }
            }
            
            if (filteredStatementPurchases.isNotEmpty()) {
                appendLine("---------------------------------------")
                appendLine("حركات التوريد والتسديد وقيمتها بالعملة والتاريخ:")
                filteredStatementPurchases.forEach { purchase ->
                    val isReturn = purchase.totalAmount < 0 || purchase.paymentMethod.startsWith("مرتجع")
                    val typeStr = if (isReturn) "سند مرتجع" else "سند توريد"
                    val absTotal = java.lang.Math.abs(purchase.totalAmount)
                    val absPaid = java.lang.Math.abs(purchase.paidAmount)
                    val absDebt = java.lang.Math.abs(purchase.debtRemaining)
                    
                    val hasDual = java.lang.Math.abs(purchase.totalAmountYer) > 0.0 && java.lang.Math.abs(purchase.totalAmountSar) > 0.0
                    val detailsText = if (hasDual) {
                        "القيمة: [${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")} + ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")}] • المسدد: [${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountYer), "ريال يمني")} + ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountSar), "ريال سعودي")}] • المتبقي: [${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingYer), "ريال يمني")} + ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingSar), "ريال سعودي")}]"
                    } else {
                        val actualCurrency = if (java.lang.Math.abs(purchase.totalAmountSar) > 0.0) "ريال سعودي" else "ريال يمني"
                        "القيمة: ${Helpers.formatWithCurrency(absTotal, actualCurrency)} • المسدد: ${Helpers.formatWithCurrency(absPaid, actualCurrency)} • المتبقي: ${Helpers.formatWithCurrency(absDebt, actualCurrency)}"
                    }
                    
                    appendLine("• $typeStr #${purchase.id} بتاريخ ${purchase.date.take(16)}:")
                    appendLine("   - $detailsText")
                    
                    // Independent details for YER/SAR Demar and Process types/numbers
                    val dParts = mutableListOf<String>()
                    if (purchase.demarYer > 0.0) {
                        val typeSuffix = if (purchase.demarTypeYer.isNotEmpty()) " (${purchase.demarTypeYer})" else ""
                        var pStr = "يمني: ${Helpers.formatWithCurrency(purchase.demarYer, "ريال يمني")}$typeSuffix"
                        if (purchase.demarNumberYer.isNotEmpty()) {
                            pStr += " رقم: ${purchase.demarNumberYer}"
                        }
                        dParts.add(pStr)
                    }
                    if (purchase.demarSar > 0.0) {
                        val typeSuffix = if (purchase.demarTypeSar.isNotEmpty()) " (${purchase.demarTypeSar})" else ""
                        var pStr = "سعودي: ${Helpers.formatWithCurrency(purchase.demarSar, "ريال سعودي")}$typeSuffix"
                        if (purchase.demarNumberSar.isNotEmpty()) {
                            pStr += " رقم: ${purchase.demarNumberSar}"
                        }
                        dParts.add(pStr)
                    }
                    if (purchase.transferNumber.isNotEmpty()) {
                        appendLine("   رقم الحوالة أو الإيداع: ${purchase.transferNumber}")
                    }
                    if (dParts.isNotEmpty()) {
                        appendLine("   تفاصيل الضمار: ${dParts.joinToString(" - ")}")
                    }
                    
                    val notesPart = Helpers.extractSupplierStatementNotes(purchase.paymentMethod, purchase.bankName)
                    if (notesPart.isNotEmpty()) {
                        appendLine("   الملاحظات: ${notesPart.replace("\n", " - ")}")
                    }
                    
                    val items = allPurchaseItems.filter { it.purchaseId == purchase.id }
                    if (items.isNotEmpty()) {
                        appendLine("   الأصناف:")
                        items.forEach { item ->
                            val absQty = java.lang.Math.abs(item.quantity)
                            val itemTotal = absQty * item.unitPrice
                            val formattedCurrency = if (item.currency.contains("سعودي")) "ريال سعودي" else "ريال يمني"
                            appendLine("     - ${item.itemName} (${Helpers.formatQuantity(absQty)} حبة × ${Helpers.formatMoney(item.unitPrice)} - $formattedCurrency) [الإجمالي: ${Helpers.formatWithCurrency(itemTotal, formattedCurrency)}]")
                        }
                    }
                    appendLine("   -------------------")
                }
            }
            
            appendLine("---------------------------------------")
            appendLine("إجماليات ملخص الفترة بشكل مستقل لكل عملة:")
            
            val periodTotalDemarSar = filteredStatementPurchases.sumOf { it.demarSar }
            val periodTotalDemarYer = filteredStatementPurchases.sumOf { it.demarYer }
            
            // YER Summary
            val showYerSummary = periodTotalPurchasesYer > 0.0 || periodTotalReturnsYer > 0.0 || periodTotalPaidYer > 0.0 || periodTotalRefundsYer > 0.0 || periodTotalDemarYer > 0.0
            if (showYerSummary) {
                appendLine("معاملات الريال اليمني:")
                if (periodTotalPurchasesYer > 0.0) appendLine("  • إجمالي المشتريات: ${Helpers.formatWithCurrency(periodTotalPurchasesYer, "ريال يمني")}")
                if (periodTotalReturnsYer > 0.0) appendLine("  • إجمالي المرتجعات: ${Helpers.formatWithCurrency(periodTotalReturnsYer, "ريال يمني")}")
                if (periodTotalPaidYer > 0.0) appendLine("  • إجمالي المسدد الفعلي: ${Helpers.formatWithCurrency(periodTotalPaidYer, "ريال يمني")}")
                if (periodTotalRefundsYer > 0.0) appendLine("  • إجمالي مسترد المرتجعات: ${Helpers.formatWithCurrency(periodTotalRefundsYer, "ريال يمني")}")
                if (periodTotalDemarYer > 0.0) appendLine("  • إجمالي ضمار صافي البيعة: ${Helpers.formatWithCurrency(periodTotalDemarYer, "ريال يمني")}")
            }
            
            // SAR Summary
            val showSarSummary = periodTotalPurchasesSar > 0.0 || periodTotalReturnsSar > 0.0 || periodTotalPaidSar > 0.0 || periodTotalRefundsSar > 0.0 || periodTotalDemarSar > 0.0
            if (showSarSummary) {
                appendLine("معاملات الريال السعودي:")
                if (periodTotalPurchasesSar > 0.0) appendLine("  • إجمالي المشتريات: ${Helpers.formatWithCurrency(periodTotalPurchasesSar, "ريال سعودي")}")
                if (periodTotalReturnsSar > 0.0) appendLine("  • إجمالي المرتجعات: ${Helpers.formatWithCurrency(periodTotalReturnsSar, "ريال سعودي")}")
                if (periodTotalPaidSar > 0.0) appendLine("  • إجمالي المسدد الفعلي: ${Helpers.formatWithCurrency(periodTotalPaidSar, "ريال سعودي")}")
                if (periodTotalRefundsSar > 0.0) appendLine("  • إجمالي مسترد المرتجعات: ${Helpers.formatWithCurrency(periodTotalRefundsSar, "ريال سعودي")}")
                if (periodTotalDemarSar > 0.0) appendLine("  • إجمالي ضمار صافي البيعة: ${Helpers.formatWithCurrency(periodTotalDemarSar, "ريال سعودي")}")
            }
            
            appendLine("---------------------------------------")
            appendLine("أرصدة المديونية الكلية المتبقية حالياً:")
            if (totalOutstandingYer > 0.0) {
                appendLine("- بالريال اليمني: ${Helpers.formatWithCurrency(totalOutstandingYer, "ريال يمني")}")
            }
            if (totalOutstandingSar > 0.0) {
                appendLine("- بالريال السعودي: ${Helpers.formatWithCurrency(totalOutstandingSar, "ريال سعودي")}")
            }
            appendLine("---------------------------------------")
            appendLine("نرجو مسارعة تصفية حساباتنا معكم. شاكرين حسن تعاونكم.")
            appendLine(com.example.util.BrandingManager.buildShareFooter())
        }

        val smsMsg = buildString {
            appendLine(com.example.util.BrandingManager.resolvedAgencyName())
            appendLine("كشف حساب المورد: ${selectedSupplierForStatement!!.name}")
            appendLine("الفترة: $selectedSupplierStatementPeriod - تاريخ: $supplierReportDate")
            
            if (prevOutstandingYer > 0.0 || prevOutstandingSar > 0.0) {
                appendLine("الرصيد السابق:")
                if (prevOutstandingYer > 0.0) appendLine(" - يمني: ${Helpers.formatWithCurrency(prevOutstandingYer, "ريال يمني")}")
                if (prevOutstandingSar > 0.0) appendLine(" - سعودي: ${Helpers.formatWithCurrency(prevOutstandingSar, "ريال سعودي")}")
            }
            
            val periodTotalDemarSar = filteredStatementPurchases.sumOf { it.demarSar }
            val periodTotalDemarYer = filteredStatementPurchases.sumOf { it.demarYer }

            if (periodTotalPurchasesYer > 0.0 || periodTotalPaidYer > 0.0 || periodTotalDemarYer > 0.0) {
                appendLine("ملخص يمني:")
                if (periodTotalPurchasesYer > 0.0) appendLine(" - مشتريات: ${Helpers.formatWithCurrency(periodTotalPurchasesYer, "ريال يمني")}")
                if (periodTotalPaidYer > 0.0) appendLine(" - مسدد: ${Helpers.formatWithCurrency(periodTotalPaidYer, "ريال يمني")}")
                if (periodTotalDemarYer > 0.0) appendLine(" - ضمار: ${Helpers.formatWithCurrency(periodTotalDemarYer, "ريال يمني")}")
            }
            
            if (periodTotalPurchasesSar > 0.0 || periodTotalPaidSar > 0.0 || periodTotalDemarSar > 0.0) {
                appendLine("ملخص سعودي:")
                if (periodTotalPurchasesSar > 0.0) appendLine(" - مشتريات: ${Helpers.formatWithCurrency(periodTotalPurchasesSar, "ريال سعودي")}")
                if (periodTotalPaidSar > 0.0) appendLine(" - مسدد: ${Helpers.formatWithCurrency(periodTotalPaidSar, "ريال سعودي")}")
                if (periodTotalDemarSar > 0.0) appendLine(" - ضمار: ${Helpers.formatWithCurrency(periodTotalDemarSar, "ريال سعودي")}")
            }
            
            appendLine("الرصيد الكلي المتبقي حالياً:")
            if (totalOutstandingYer > 0.0) appendLine(" - يمني: ${Helpers.formatWithCurrency(totalOutstandingYer, "ريال يمني")}")
            if (totalOutstandingSar > 0.0) appendLine(" - سعودي: ${Helpers.formatWithCurrency(totalOutstandingSar, "ريال سعودي")}")
        }


        AlertDialog(
            onDismissRequest = { showSupplierStatementDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "كشف حساب المورد التفصيلي",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "تفاصيل كافة حركات التوريد والتسديد وقيمتها بالعملة والتاريخ للمورد: ${selectedSupplierForStatement!!.name}",
                        color = PalWhiteMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right
                    )

                    // Period Toggles
                    val periodsToggle = listOf("الكل", "يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص")
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        periodsToggle.forEach { period ->
                            val isSel = selectedSupplierStatementPeriod == period
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteMuted
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedSupplierStatementPeriod = period
                                        if (period == "أسبوعي" && !customSupplierStatementValue.startsWith("WEEK_FILTER|")) {
                                            customSupplierStatementValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                        }
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
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

                    if (selectedSupplierStatementPeriod == "تقويم مخصص" || selectedSupplierStatementPeriod == "أسبوعي") {
                        UnifiedPeriodSelector(
                            initialType = if (selectedSupplierStatementPeriod == "أسبوعي") "أسبوع" else (if (customSupplierStatementType == "date") "يوم" else if (customSupplierStatementType == "month") "شهر" else "سنة"),
                            initialValue = if (selectedSupplierStatementPeriod == "أسبوعي" && !customSupplierStatementValue.startsWith("WEEK_FILTER|")) {
                                "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                            } else {
                                customSupplierStatementValue.ifEmpty { Helpers.getCurrentDate() }
                            },
                            onPeriodChanged = { type, formattedValue ->
                                customSupplierStatementType = if (type == "يوم" || type == "date") "date" else if (type == "شهر" || type == "month") "month" else "year"
                                customSupplierStatementValue = formattedValue
                                supplierReportDate = Helpers.formatCustomPeriodToArabic(type, formattedValue)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = supplierReportDate,
                        onValueChange = { supplierReportDate = it },
                        label = { Text("تعيين وتخصيص تاريخ السند المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth().testTag("supplier_statement_date_input"),
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

                    Spacer(modifier = Modifier.height(6.dp))

                    // ON-SCREEN SUMMARY CONTROLS OF THE TOTALS (YER vs SAR SPLIT)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(12.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "ملخص الحساب للفترة المحددة:",
                            color = PalWhitePure,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Right
                        )
                        
                        // YER section
                        val showYerSummary = currencyConfig.allowsYer ||
                            periodTotalPurchasesYer != 0.0 ||
                            periodTotalReturnsYer != 0.0 ||
                            periodTotalPaidYer != 0.0 ||
                            periodTotalRefundsYer != 0.0 ||
                            totalOutstandingYer != 0.0
                        if (showYerSummary) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PalBlackNormal, RoundedCornerShape(8.dp))
                                    .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (currencyConfig.allowsYer) "المعاملات بالريال اليمني:" else "المعاملات بالريال اليمني (سجل تاريخي):",
                                    color = PalWhitePure,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "المشتريات: ${Helpers.formatWithCurrency(periodTotalPurchasesYer, "ريال يمني")}",
                                        color = PalWhiteSoft,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "المرتجعات: ${Helpers.formatWithCurrency(periodTotalReturnsYer, "ريال يمني")}",
                                        color = PalRedNormal,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "المسدد للمورد: ${Helpers.formatWithCurrency(periodTotalPaidYer, "ريال يمني")}",
                                        color = PalSuccessNormal,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "المسترد للمرتجعات: ${Helpers.formatWithCurrency(periodTotalRefundsYer, "ريال يمني")}",
                                        color = PalGoldCalligraphy,
                                        fontSize = 10.sp
                                    )
                                }
                                HorizontalDivider(color = PalBorderColor, thickness = 0.5.dp)
                                Text(
                                    text = "رصيد المديونية الكلي الحالي: ${Helpers.formatWithCurrency(totalOutstandingYer, "ريال يمني")}",
                                    color = PalGoldCalligraphy,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // SAR section
                        val showSarSummary = currencyConfig.allowsSar ||
                            periodTotalPurchasesSar != 0.0 ||
                            periodTotalReturnsSar != 0.0 ||
                            periodTotalPaidSar != 0.0 ||
                            periodTotalRefundsSar != 0.0 ||
                            totalOutstandingSar != 0.0
                        if (showSarSummary) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PalBlackNormal, RoundedCornerShape(8.dp))
                                    .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = if (currencyConfig.allowsSar) "المعاملات بالريال السعودي:" else "المعاملات بالريال السعودي (سجل تاريخي):",
                                    color = PalWhitePure,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "المشتريات: ${Helpers.formatWithCurrency(periodTotalPurchasesSar, "ريال سعودي")}",
                                        color = PalWhiteSoft,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "المرتجعات: ${Helpers.formatWithCurrency(periodTotalReturnsSar, "ريال سعودي")}",
                                        color = PalRedNormal,
                                        fontSize = 10.sp
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "المسدد للمورد: ${Helpers.formatWithCurrency(periodTotalPaidSar, "ريال سعودي")}",
                                        color = PalSuccessNormal,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        text = "المسترد للمرتجعات: ${Helpers.formatWithCurrency(periodTotalRefundsSar, "ريال سعودي")}",
                                        color = PalGoldCalligraphy,
                                        fontSize = 10.sp
                                    )
                                }
                                HorizontalDivider(color = PalBorderColor, thickness = 0.5.dp)
                                Text(
                                    text = "رصيد المديونية الكلي الحالي: ${Helpers.formatWithCurrency(totalOutstandingSar, "ريال سعودي")}",
                                    color = PalGoldCalligraphy,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Right,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    HorizontalDivider(color = PalBorderColor)

                    // Detailed Transactions View Block
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 340.dp)
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredStatementPurchases.isEmpty()) {
                                Text(
                                    text = "لا توجد معاملات شراء أو توريد بضاعة مسجلة خلال الفترة المحددة.",
                                    color = PalWhiteMuted,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                                )
                            } else {
                                filteredStatementPurchases.forEach { purchase ->
                                    val isReturn = purchase.totalAmount < 0 || purchase.paymentMethod.startsWith("مرتجع")
                                    val typeStr = if (isReturn) "سند مرتجع بضاعة" else "سند توريد"
                                    val absTotal = java.lang.Math.abs(purchase.totalAmount)
                                    val absPaid = java.lang.Math.abs(purchase.paidAmount)
                                    val absDebt = java.lang.Math.abs(purchase.debtRemaining)

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(PalBlackNormal, RoundedCornerShape(8.dp))
                                            .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                            .padding(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("$typeStr #${purchase.id}", color = if (isReturn) PalGoldCalligraphy else PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                            Text(purchase.date, color = PalWhiteMuted, fontSize = 10.sp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("البيان: ${purchase.paymentMethod}", color = PalWhiteSoft, fontSize = 10.sp)
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Button(
                                                onClick = {
                                                    editingPurchaseId = purchase.id
                                                    selectedSupplierForPurchase = suppliersList.find { it.id == purchase.supplierId }
                                                    val itemsForThisPurchase = allPurchaseItems.filter { it.purchaseId == purchase.id }
                                                    val loadedItems = itemsForThisPurchase.map { pi ->
                                                        val invItem = inventoryItems.find { it.id == pi.itemId } ?: com.example.data.InventoryItem(id = pi.itemId, name = pi.itemName, quantity = pi.quantity, buyPrice = pi.unitPrice, sellPrice = 0.0, lowStockThreshold = 5.0)
                                                        Triple(invItem, pi.quantity, pi.unitPrice)
                                                    }
                                                    newPurchaseItems.clear()
                                                    newPurchaseItems.addAll(loadedItems)
                                                    paidAmountInput = purchase.paidAmount.toString()
                                                    purchasePaymentMethod = purchase.paymentMethod
                                                    deedDateInput = purchase.date
                                                    isAddingPurchase = true
                                                    showSupplierStatementDialog = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("تعديل", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Button(
                                                onClick = {
                                                    purchaseToVoid = purchase
                                                    showVoidConfirm = true
                                                    showSupplierStatementDialog = false
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                                                shape = RoundedCornerShape(8.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(36.dp)
                                            ) {
                                                Icon(Icons.Default.Delete, contentDescription = "حذف", tint = Color.White, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("حذف (إلغاء)", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                        val hasDual = java.lang.Math.abs(purchase.totalAmountYer) > 0.0 && java.lang.Math.abs(purchase.totalAmountSar) > 0.0
                                        if (hasDual) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("القيمة (يمني): ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")}", color = PalWhiteSoft, fontSize = 10.sp)
                                                Text("القيمة (سعودي): ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")}", color = PalWhiteSoft, fontSize = 10.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("المسدد (يمني): ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountYer), "ريال يمني")}", color = PalSuccessNormal, fontSize = 10.sp)
                                                Text("المسدد (سعودي): ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountSar), "ريال سعودي")}", color = PalSuccessNormal, fontSize = 10.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("المتبقي (يمني): ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingYer), "ريال يمني")}", color = PalGoldCalligraphy, fontSize = 10.sp)
                                                Text("المتبقي (سعودي): ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.debtRemainingSar), "ريال سعودي")}", color = PalGoldCalligraphy, fontSize = 10.sp)
                                            }
                                        } else {
                                            val actualCurrency = if (java.lang.Math.abs(purchase.totalAmountSar) > 0.0) "ريال سعودي" else "ريال يمني"
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("القيمة الكلية: ${Helpers.formatWithCurrency(absTotal, actualCurrency)}", color = PalWhiteSoft, fontSize = 10.sp)
                                                Text("المسدد/المسترد: ${Helpers.formatWithCurrency(absPaid, actualCurrency)}", color = PalSuccessNormal, fontSize = 10.sp)
                                            }
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text("الآجل المتبقي: ${Helpers.formatWithCurrency(absDebt, actualCurrency)}", color = PalGoldCalligraphy, fontSize = 10.sp)
                                            }
                                        }
                                        val items = allPurchaseItems.filter { it.purchaseId == purchase.id }
                                        if (items.isNotEmpty()) {
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Column(modifier = Modifier.padding(start = 12.dp)) {
                                                items.forEach { item ->
                                                    val absQty = java.lang.Math.abs(item.quantity)
                                                    val formattedQty = if (absQty % 1.0 == 0.0) absQty.toInt().toString() else absQty.toString()
                                                    val curText = if (item.currency.contains("سعودي") || (!item.currency.contains("يمني") && purchase.currency.contains("سعودي"))) "ريال سعودي" else "ريال يمني"
                                                    Row(
                                                         modifier = Modifier
                                                             .fillMaxWidth()
                                                             .heightIn(min = 36.dp)
                                                             .clickable {
                                                                 editingSupplierPurchaseItemState = item
                                                                 editPurchaseItemNameInput = item.itemName
                                                                 editPurchaseItemQtyInput = formattedQty
                                                                 editPurchaseItemPriceInput = item.unitPrice.toString()
                                                                 val relatedInvItem = inventoryItems.find { it.id == item.itemId }
                                                                 editPurchaseItemNumberInput = relatedInvItem?.itemNumber ?: ""
                                                                 showEditPurchaseItemDialog = true
                                                             }
                                                             .padding(vertical = 4.dp),
                                                         verticalAlignment = Alignment.CenterVertically,
                                                         horizontalArrangement = Arrangement.SpaceBetween
                                                     ) {
                                                         Text(
                                                             text = "• ${item.itemName} (${formattedQty} حبة × ${item.unitPrice.toInt()} - $curText)",
                                                             color = PalWhitePure,
                                                             fontSize = 11.sp,
                                                             fontWeight = FontWeight.Medium,
                                                             modifier = Modifier.weight(1f)
                                                         )
                                                         Icon(
                                                             imageVector = Icons.Default.Edit,
                                                             contentDescription = "تعديل الصنف",
                                                             tint = PalIconUnified,
                                                             modifier = Modifier.size(16.dp)
                                                         )
                                                     }


                                                 }
                                             }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val tPrefix = when (selectedSupplierStatementPeriod) {
                                    "يومي" -> "تقرير يومي"
                                    "اسبوعي" -> "تقرير أسبوعي"
                                    "شهري" -> "تقرير شهري"
                                    "سنوي" -> "تقرير سنوي"
                                    "تقويم مخصص" -> "التقرير المخصص لفترة ($customSupplierStatementValue)"
                                    "حسب اليوم" -> "تقرير يوم مخصص ($customSupplierStatementValue)"
                                    "حسب الشهر" -> "تقرير شهر مخصص ($customSupplierStatementValue)"
                                    "حسب السنة" -> "تقرير سنة مخصصة ($customSupplierStatementValue)"
                                    else -> "تقرير دوري"
                                }
                                val hasReturns = filteredStatementPurchases.any { it.totalAmount < 0 || it.paymentMethod.startsWith("مرتجع") }
                                val hasRemainingDebt = filteredStatementPurchases.any { it.debtRemaining > 0.0 || it.debtRemainingYer > 0.0 || it.debtRemainingSar > 0.0 }

                                 val hasNotes = filteredStatementPurchases.any { purchase ->
                                     val notesPart = Helpers.extractActualPaymentNotes(purchase.paymentMethod, purchase.bankName)
                                     purchase.bankName.isNotEmpty() || notesPart.isNotEmpty()
                                 }

                                val headers = mutableListOf<String>()
                                headers.add("التاريخ")
                                headers.add("ضمار صافي البيعة")
                                headers.add("رقم الحوالة أو الإيداع")
                                 if (hasNotes) {
                                     headers.add("الملاحظات")
                                 }
                                headers.add("نوع العملية")
                                headers.add("القيمة الإجمالية")
                                headers.add("المبلغ المسدد")
                                if (hasReturns) {
                                    headers.add("المسترد")
                                }
                                if (hasRemainingDebt) {
                                    headers.add("الآجل المتبقي")
                                }

                                val rows = filteredStatementPurchases.map { purchase ->
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
                                    val totalCell = if (hasDual) {
                                        "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")}"
                                    } else {
                                        Helpers.formatWithCurrency(absTotal, curLabel)
                                    }
                                    val paidCell = if (hasDual) {
                                        "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.paidAmountSar), "ريال سعودي")}"
                                    } else {
                                        Helpers.formatWithCurrency(absPaid, curLabel)
                                    }
                                    val returnCell = if (isReturn) {
                                        if (hasDual) {
                                            "يمني: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountYer), "ريال يمني")}\nسعودي: ${Helpers.formatWithCurrency(java.lang.Math.abs(purchase.totalAmountSar), "ريال سعودي")}"
                                        } else {
                                            Helpers.formatWithCurrency(absTotal, curLabel)
                                        }
                                    } else {
                                        "-"
                                    }
                                    val debtCell = if (hasDual) {
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

                                val periodTotalDemarSar = filteredStatementPurchases.sumOf { it.demarSar }
                                val periodTotalDemarYer = filteredStatementPurchases.sumOf { it.demarYer }

                                val pdfTotals = mutableMapOf<String, String>()
                                // These 4 must always be shown:
                                pdfTotals["إجمالي ضمار صافي البيعة بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalDemarYer, "ريال يمني")
                                pdfTotals["إجمالي ضمار صافي البيعة بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalDemarSar, "ريال سعودي")
                                pdfTotals["إجمالي المشتريات المستوردة بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalPurchasesSar, "ريال سعودي")
                                pdfTotals["المسدد الفعلي للمورد بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalPaidSar, "ريال سعودي")

                                // These are only shown if they contain real/positive values (> 0.0):
                                if (prevOutstandingYer > 0.0) {
                                    pdfTotals["الرصيد السابق بالريال اليمني"] = Helpers.formatWithCurrency(prevOutstandingYer, "ريال يمني")
                                }
                                if (prevOutstandingSar > 0.0) {
                                    pdfTotals["الرصيد السابق بالريال السعودي"] = Helpers.formatWithCurrency(prevOutstandingSar, "ريال سعودي")
                                }
                                if (periodTotalPurchasesYer > 0.0) {
                                    pdfTotals["إجمالي المشتريات المستوردة بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalPurchasesYer, "ريال يمني")
                                }
                                if (periodTotalReturnsYer > 0.0) {
                                    pdfTotals["إجمالي مرتجعات البضاعة بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalReturnsYer, "ريال يمني")
                                }
                                if (periodTotalReturnsSar > 0.0) {
                                    pdfTotals["إجمالي مرتجعات البضاعة بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalReturnsSar, "ريال سعودي")
                                }
                                if (periodTotalPaidYer > 0.0) {
                                    pdfTotals["المسدد الفعلي للمورد بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalPaidYer, "ريال يمني")
                                }
                                if (periodTotalRefundsYer > 0.0) {
                                    pdfTotals["المسترد الفعلي للمرتجع بالريال اليمني"] = Helpers.formatWithCurrency(periodTotalRefundsYer, "ريال يمني")
                                }
                                if (periodTotalRefundsSar > 0.0) {
                                    pdfTotals["المسترد الفعلي للمرتجع بالريال السعودي"] = Helpers.formatWithCurrency(periodTotalRefundsSar, "ريال سعودي")
                                }
                                if (totalOutstandingYer > 0.0) {
                                    pdfTotals["المديونية الكلية المستحقة له بالريال اليمني"] = Helpers.formatWithCurrency(totalOutstandingYer, "ريال يمني")
                                }
                                if (totalOutstandingSar > 0.0) {
                                    pdfTotals["المديونية الكلية المستحقة له بالريال السعودي"] = Helpers.formatWithCurrency(totalOutstandingSar, "ريال سعودي")
                                }

                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "$tPrefix كشف حساب المورد التفصيلي: ${selectedSupplierForStatement!!.name}" + if (selectedSupplierForStatement!!.region.isNotEmpty()) " (${selectedSupplierForStatement!!.region})" else "",
                                    headers = headers,
                                    rows = rows,
                                    totals = pdfTotals,
                                    customDate = Helpers.getCurrentDateTime(),
                                    reportPeriod = supplierReportDate
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
                                Helpers.shareViaWhatsApp(context, selectedSupplierForStatement!!.phone, statementMsg)
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
                                Helpers.shareViaSMS(context, selectedSupplierForStatement!!.phone, smsMsg)
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
                                bluetoothPrintText = statementMsg
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
                TextButton(onClick = { showSupplierStatementDialog = false }) {
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

    if (showAddReturnDialog && selectedSupplierForReturn != null) {
        AlertDialog(
            onDismissRequest = { showAddReturnDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تسجيل مرتجع بضاعة للمورد: ${selectedSupplierForReturn!!.name}",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "قم بتحديد الصنف والكمية المرتجعة والمبلغ المسترد نقداً إن وجد لتحديث حساب المورد والمخزون بدقة:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right
                    )

                    // 1. Product selection dropdown
                    var showProductDropdown by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "الصنف المراد إرجاعه",
                            color = PalWhitePure,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { showProductDropdown = true },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackDark, contentColor = PalWhitePure),
                                border = BorderStroke(1.dp, PalBorderColor),
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = selectedItemForReturn?.name ?: "اضغط لاختيار الصنف من المخزن",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedItemForReturn != null) PalGreenNormal else PalWhitePure
                                )
                            }
                            DropdownMenu(
                                expanded = showProductDropdown,
                                onDismissRequest = { showProductDropdown = false },
                                modifier = Modifier.background(PalBlackNormal).fillMaxWidth(0.9f)
                            ) {
                                inventoryItems.forEach { item ->
                                    DropdownMenuItem(
                                        text = { Text("${item.name} (المتوفر: ${Helpers.formatQuantity(item.quantity)} حبة)", color = PalWhitePure, fontSize = 12.sp) },
                                        onClick = {
                                            selectedItemForReturn = item
                                            returnedPriceInput = item.buyPrice.toString()
                                            showProductDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Row 2: Quantity & Return Unit Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = returnedQtyInput,
                                onValueChange = { returnedQtyInput = it },
                                label = "الكمية المرتجعة",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = returnedPriceInput,
                                onValueChange = { returnedPriceInput = it },
                                label = "سعر الإرجاع للوحدة",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }

                    // Row 3: Refunded Cash & Return Date
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = refundedAmountInput,
                                onValueChange = { refundedAmountInput = it },
                                label = "المسترد نقداً (إن وجد)",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(
                                value = returnDateInput,
                                onValueChange = { returnDateInput = it },
                                label = "تاريخ ووقت الإرجاع",
                                placeholder = "yyyy-MM-dd HH:mm",
                                trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp)) }
                            )
                        }
                    }

                    // Row 4: Notes
                    AccountingTextField(
                        value = returnNotesInput,
                        onValueChange = { returnNotesInput = it },
                        label = "بيان أو ملاحظات المرتجع",
                        placeholder = "أدخل تفاصيل أو أسباب مرتجع البضاعة..."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val product = selectedItemForReturn
                        val qty = returnedQtyInput.toDoubleOrNull() ?: 0.0
                        val price = returnedPriceInput.toDoubleOrNull() ?: 0.0
                        val refund = refundedAmountInput.toDoubleOrNull() ?: 0.0

                        if (product == null) {
                            android.widget.Toast.makeText(context, "الرجاء اختيار صنف مرتجع أولاً!", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (qty <= 0.0) {
                            android.widget.Toast.makeText(context, "يرجى تحديد كمية إرجاع صحيحة تزيد عن الصفر!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.executeSupplierReturn(
                                supplierId = selectedSupplierForReturn!!.id,
                                itemId = product.id,
                                itemName = product.name,
                                returnedQty = qty,
                                returnedPrice = price,
                                returnDate = returnDateInput.ifEmpty { Helpers.getCurrentDateTime() },
                                refundedAmount = refund,
                                notes = returnNotesInput
                            )
                            android.widget.Toast.makeText(context, "تم تسجيل المرتجع بنجاح وتحديث الحساب والمخزن!", android.widget.Toast.LENGTH_SHORT).show()
                            showAddReturnDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("حفظ المرتجع", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddReturnDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showSupplierPaymentDialog && selectedSupplierForPayment != null) {
        val sarToYerRate by viewModel.sarToYerExchangeRate.collectAsState()
        val supplierPurchases = allPurchasesList.filter { 
            it.supplierId == selectedSupplierForPayment!!.id
        }.sortedWith(
            compareBy<com.example.data.SupplierPurchase> { (it.debtRemainingYer <= 0.0 && it.debtRemainingSar <= 0.0) }
            .thenByDescending { it.date }
        )

        AlertDialog(
            onDismissRequest = { showSupplierPaymentDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تسجيل مستند سداد للمورد: ${selectedSupplierForPayment!!.name}",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "قم بإدخال بيانات تسديد الحساب للمورد وتحديد سند التوريد أو المرتجع المراد تسويته:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right
                    )

                    // 1. Selection of Purchase/Return Invoice
                    var purchaseDropdownExpanded by remember { mutableStateOf(false) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "اختر سند التوريد أو المرتجع للتسوية",
                            color = PalWhitePure,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { purchaseDropdownExpanded = true },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackDark, contentColor = PalWhitePure),
                                border = BorderStroke(1.dp, PalBorderColor),
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                val displayText = if (selectedPurchaseForPayment != null) {
                                    val p = selectedPurchaseForPayment!!
                                    val isRet = p.totalAmount < 0 || p.paymentMethod.startsWith("مرتجع")
                                    val typeStr = if (isRet) "مرتجع" else "توريد"
                                    val isFullyPaid = p.debtRemainingYer <= 0.0 && p.debtRemainingSar <= 0.0
                                    val statusStr = if (isFullyPaid) "مسدد" else "معلق متبقي"
                                    "[$statusStr] سند $typeStr رقم ${p.id} (${p.date.take(10)}) - يمني: ${p.debtRemainingYer} | سعودي: ${p.debtRemainingSar}"
                                } else {
                                    "اضغط لاختيار السند المراد سداده"
                                }
                                Text(
                                    text = displayText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedPurchaseForPayment != null) PalGreenNormal else PalWhitePure,
                                    textAlign = TextAlign.Center
                                )
                            }
                            DropdownMenu(
                                expanded = purchaseDropdownExpanded,
                                onDismissRequest = { purchaseDropdownExpanded = false },
                                modifier = Modifier.background(PalBlackNormal).fillMaxWidth(0.9f)
                            ) {
                                if (supplierPurchases.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("لا توجد فواتير أو مرتجعات لهذا المورد", color = PalWhiteMuted, fontSize = 11.sp) },
                                        onClick = { purchaseDropdownExpanded = false }
                                    )
                                } else {
                                    supplierPurchases.forEach { purchase ->
                                        val isRet = purchase.totalAmount < 0 || purchase.paymentMethod.startsWith("مرتجع")
                                        val typeStr = if (isRet) "مرتجع" else "توريد"
                                        val isFullyPaid = purchase.debtRemainingYer <= 0.0 && purchase.debtRemainingSar <= 0.0
                                        
                                        val statusStr = if (isFullyPaid) " (مسدد)" else " (معلق)"
                                        val dColor = if (isFullyPaid) PalSuccessNormal else PalWhitePure
                                        
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = "رقم ${purchase.id} ($typeStr)$statusStr - تاريخ: ${purchase.date.take(10)}\nمتبقي يمني: ${purchase.debtRemainingYer} | سعودي: ${purchase.debtRemainingSar}",
                                                    color = dColor,
                                                    fontSize = 11.sp
                                                )
                                            },
                                            onClick = {
                                                selectedPurchaseForPayment = purchase
                                                purchaseDropdownExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: Bank Name
                    AccountingTextField(
                        value = paymentBankNameInput,
                        onValueChange = { paymentBankNameInput = it },
                        label = "البنك المحول من خلاله",
                        placeholder = "اسم البنك المحول من خلاله (إن وجد)..."
                    )

                    // YER Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "تفاصيل الضمار بالريال اليمني",
                                color = PalWhitePure,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1.2f)) {
                                    AccountingTextField(
                                        value = demarAmountYer,
                                        onValueChange = { demarAmountYer = it },
                                        label = "مبلغ الضمار (يمني)",
                                        placeholder = "0.00",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                Box(modifier = Modifier.weight(0.8f)) {
                                    var typeDropdownExpanded by remember { mutableStateOf(false) }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "نوع العملية",
                                            color = PalWhitePure,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedButton(
                                            onClick = { typeDropdownExpanded = true },
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackNormal, contentColor = PalWhitePure),
                                            border = BorderStroke(1.dp, PalBorderColor),
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = demarTypeYer.ifEmpty { "حدد النوع" },
                                                fontSize = 11.sp,
                                                color = if (demarTypeYer.isNotEmpty()) PalGoldCalligraphy else PalWhiteMuted,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = typeDropdownExpanded,
                                            onDismissRequest = { typeDropdownExpanded = false },
                                            modifier = Modifier.background(PalBlackNormal)
                                        ) {
                                            listOf("حوالة", "إيداع", "").forEach { option ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            text = if (option.isEmpty()) "إلغاء التحديد" else option, 
                                                            color = if (option.isEmpty()) PalRedNormal else PalWhitePure, 
                                                            fontSize = 12.sp
                                                        ) 
                                                    },
                                                    onClick = {
                                                        demarTypeYer = option
                                                        typeDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (demarTypeYer.isNotEmpty()) {
                                AccountingTextField(
                                    value = demarNumberYer,
                                    onValueChange = { demarNumberYer = it },
                                    label = "رقم ال${demarTypeYer} (يمني)",
                                    placeholder = "أدخل رقم ال${demarTypeYer}..."
                                )
                            }
                        }
                    }

                    // SAR Section
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "تفاصيل الضمار بالريال السعودي",
                                color = PalGoldCalligraphy,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1.2f)) {
                                    AccountingTextField(
                                        value = demarAmountSar,
                                        onValueChange = { demarAmountSar = it },
                                        label = "مبلغ الضمار (سعودي)",
                                        placeholder = "0.00",
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                                Box(modifier = Modifier.weight(0.8f)) {
                                    var typeDropdownExpanded by remember { mutableStateOf(false) }
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            text = "نوع العملية",
                                            color = PalWhitePure,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        OutlinedButton(
                                            onClick = { typeDropdownExpanded = true },
                                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackNormal, contentColor = PalWhitePure),
                                            border = BorderStroke(1.dp, PalBorderColor),
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = demarTypeSar.ifEmpty { "حدد النوع" },
                                                fontSize = 11.sp,
                                                color = if (demarTypeSar.isNotEmpty()) PalGoldCalligraphy else PalWhiteMuted,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                        DropdownMenu(
                                            expanded = typeDropdownExpanded,
                                            onDismissRequest = { typeDropdownExpanded = false },
                                            modifier = Modifier.background(PalBlackNormal)
                                        ) {
                                            listOf("حوالة", "إيداع", "").forEach { option ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Text(
                                                            text = if (option.isEmpty()) "إلغاء التحديد" else option, 
                                                            color = if (option.isEmpty()) PalRedNormal else PalWhitePure, 
                                                            fontSize = 12.sp
                                                        ) 
                                                    },
                                                    onClick = {
                                                        demarTypeSar = option
                                                        typeDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            if (demarTypeSar.isNotEmpty()) {
                                AccountingTextField(
                                    value = demarNumberSar,
                                    onValueChange = { demarNumberSar = it },
                                    label = "رقم ال${demarTypeSar} (سعودي)",
                                    placeholder = "أدخل رقم ال${demarTypeSar}..."
                                )
                            }
                        }
                    }

                    // Row 4: Account Currency & Payment Date type
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        // Account Currency
                        Box(modifier = Modifier.weight(1f)) {
                            CurrencySelectionRow(
                                selectedCurrency = if (accountCurrency == "سعودي") MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER,
                                currencyConfig = currencyConfig,
                                onCurrencySelected = { picked ->
                                    accountCurrency = if (MoneyMath.isSar(picked)) "سعودي" else "يمني"
                                },
                                title = "حساب عملة السداد",
                                useShortLabels = true
                            )
                        }

                        // Payment Date Choice
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "تاريخ مستند السداد",
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PalBlackDark)
                                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp)),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                listOf("تلقائي", "مخصص").forEach { dateType ->
                                    val isSel = if (dateType == "تلقائي") useAutoPaymentDate else !useAutoPaymentDate
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(if (isSel) PalGreenNormal else Color.Transparent)
                                            .clickable {
                                                if (dateType == "تلقائي") {
                                                    useAutoPaymentDate = true
                                                } else {
                                                    useAutoPaymentDate = false
                                                    customPaymentDate = Helpers.getCurrentDateTime()
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dateType,
                                            color = if (isSel) Color.White else PalWhiteMuted,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (!useAutoPaymentDate) {
                        AccountingTextField(
                            value = customPaymentDate,
                            onValueChange = { customPaymentDate = it },
                            label = "تاريخ السداد المخصص",
                            placeholder = "yyyy-MM-dd HH:mm",
                            trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp)) }
                        )
                    }

                    // Row 5: Payment Amount
                    AccountingTextField(
                        value = paymentAmountInput,
                        onValueChange = { paymentAmountInput = it },
                        label = "المبلغ المراد سداده بالشكل الصحيح",
                        placeholder = "0.00",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    // Explanation of overpayment
                    val currentRemaining = selectedPurchaseForPayment?.let { if (accountCurrency == "سعودي") it.debtRemainingSar else it.debtRemainingYer } ?: 0.0
                    val inputtedDouble = paymentAmountInput.toDoubleOrNull() ?: 0.0
                    if (inputtedDouble > currentRemaining && selectedPurchaseForPayment != null) {
                        val overAmt = inputtedDouble - currentRemaining
                        Text(
                            text = "تنبيه: المبلغ المدخل ($inputtedDouble) يزيد عن متبقي السند ($currentRemaining). سيتم تسجيل المبلغ الزائد ($overAmt) كرصيد إضافي فائض للوكالة عند المورد بشكل منفصل.",
                            color = PalGoldCalligraphy,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Right
                        )
                    }

                    // Row 6: Notes
                    AccountingTextField(
                        value = paymentNotesInput,
                        onValueChange = { paymentNotesInput = it },
                        label = "ملاحظات إضافية على السداد",
                        placeholder = "أدخل أي ملاحظات أو تفاصيل إضافية عن السداد هنا..."
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val paymentCanonicalCurr = if (accountCurrency == "سعودي") MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
                        val amount = MoneyMath.parseDoubleOrNull(paymentAmountInput) ?: 0.0
                        if (!currencyConfig.isSingleCurrencyAllowed(paymentCanonicalCurr)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(paymentCanonicalCurr), android.widget.Toast.LENGTH_LONG).show()
                        } else if (selectedPurchaseForPayment == null) {
                            android.widget.Toast.makeText(context, "الرجاء تحديد سند التوريد أو المرتجع المختار أولاً!", android.widget.Toast.LENGTH_SHORT).show()
                        } else if (selectedPurchaseForPayment!!.debtRemainingYer <= 0.0 && selectedPurchaseForPayment!!.debtRemainingSar <= 0.0) {
                            android.widget.Toast.makeText(context, "هذا سند التوريد مسدد بالكامل.", android.widget.Toast.LENGTH_LONG).show()
                        } else if (amount <= 0.0) {
                            android.widget.Toast.makeText(context, "الرجاء إدخال قيمة سداد صحيحة وموجبة!", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            val finalPaymentDateStr = if (useAutoPaymentDate) Helpers.getCurrentDateTime() else customPaymentDate
                            val targetDateStr = finalPaymentDateStr.take(10)
 
                            val dAmtYer = demarAmountYer.toDoubleOrNull() ?: 0.0
                            val dAmtSar = demarAmountSar.toDoubleOrNull() ?: 0.0
 
                            val details = buildString {
                                val dParts = mutableListOf<String>()
                                if (dAmtYer > 0.0) {
                                    var part = "ضمار يمني: $dAmtYer"
                                    if (demarTypeYer.isNotEmpty()) {
                                        part += " ($demarTypeYer"
                                        if (demarNumberYer.isNotEmpty()) {
                                            part += " رقم $demarNumberYer"
                                        }
                                        part += ")"
                                    }
                                    dParts.add(part)
                                }
                                if (dAmtSar > 0.0) {
                                    var part = "ضمار سعودي: $dAmtSar"
                                    if (demarTypeSar.isNotEmpty()) {
                                        part += " ($demarTypeSar"
                                        if (demarNumberSar.isNotEmpty()) {
                                            part += " رقم $demarNumberSar"
                                        }
                                        part += ")"
                                    }
                                    dParts.add(part)
                                }
                                
                                if (dParts.isNotEmpty()) {
                                    append(dParts.joinToString(" - "))
                                }
                                
                                if (paymentBankNameInput.isNotEmpty()) {
                                    if (isNotEmpty()) append(" - ")
                                    append(paymentBankNameInput)
                                }
                                if (paymentNotesInput.isNotEmpty()) {
                                    if (isNotEmpty()) append(" - ")
                                    append(paymentNotesInput)
                                }
                                if (isEmpty()) {
                                    append("نقداً")
                                }
                            }
 
                            val fallbackTransferNo = listOfNotNull(
                                demarNumberYer.ifEmpty { null },
                                demarNumberSar.ifEmpty { null }
                            ).joinToString(" / ")

                            viewModel.paySupplierInvoice(
                                purchaseId = selectedPurchaseForPayment!!.id,
                                payAmount = amount,
                                currency = if (accountCurrency == "سعودي") "ريال سعودي" else "ريال يمني",
                                paymentDetails = details,
                                dateOfPayment = finalPaymentDateStr,
                                transferNumber = fallbackTransferNo,
                                bankName = paymentBankNameInput,
                                demarSar = dAmtSar,
                                demarYer = dAmtYer,
                                demarTypeYer = demarTypeYer,
                                demarNumberYer = demarNumberYer,
                                demarTypeSar = demarTypeSar,
                                demarNumberSar = demarNumberSar
                             )
 
                            android.widget.Toast.makeText(context, "تم قيد سداد الدفعة بنجاح وتصفية رصيد المورد للعملة المحددة!", android.widget.Toast.LENGTH_SHORT).show()
                            showSupplierPaymentDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إجراء السداد وحفظ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSupplierPaymentDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }
}
