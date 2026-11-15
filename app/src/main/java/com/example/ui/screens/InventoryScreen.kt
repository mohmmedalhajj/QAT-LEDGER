@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.InventoryItem
import com.example.data.QatImportWeight
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.components.AccountingTextField
import com.example.ui.components.CurrencySelectionRow
import com.example.util.BluetoothPrinterManager
import com.example.util.Helpers
import com.example.util.MoneyMath

data class ReportSection(
    val title: String,
    val subtitle: String,
    val items: List<Triple<InventoryItem, Double, Double>>,
    val totalWeight: Double,
    val totalUnitsBought: Double,
    val totalValueYer: Double,
    val totalValueSar: Double
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    
    val allItems by viewModel.inventoryItems.collectAsState()
    val allInvoices by viewModel.allInvoices.collectAsState()
    val allInvoiceItems by viewModel.allInvoiceItems.collectAsState()
    val allPurchases by viewModel.allPurchases.collectAsState()
    val allPurchaseItems by viewModel.allPurchaseItems.collectAsState()
    val allWeights by viewModel.allWeights.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var showWeightDialog by remember { mutableStateOf(false) }
    var weightInput by remember { mutableStateOf("") }
    var weightDateInput by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var weightNotesInput by remember { mutableStateOf("") }

    var showReceiverDialog by remember { mutableStateOf(false) }
    var receiverInput by remember { mutableStateOf("") }
    var receiverDateInput by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var editingReceiverWeightRecord by remember { mutableStateOf<QatImportWeight?>(null) }
    var receiverWeightInput by remember { mutableStateOf("") }
    var receiverNotesInput by remember { mutableStateOf("") }

    var searchQuery by remember { mutableStateOf("") }
    
    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }
    
    var showReportsDialog by remember { mutableStateOf(false) }
    var showReceiverSelectionDialog by remember { mutableStateOf(false) }
    var selectedReceiverForReport by remember { mutableStateOf("") }
    var showDeleteWeightConfirmDialog by remember { mutableStateOf(false) }
    var deleteWeightTargetDate by remember { mutableStateOf("") }
    var showTopBarPdfPeriodDialog by remember { mutableStateOf(false) }
    var topBarPdfSelectedPeriod by remember { mutableStateOf("يومي") } // يومي, أسبوعي, شهري, سنوي, تقويم مخصص
    var topBarPdfCustomType by remember { mutableStateOf("يوم") }
    var topBarPdfCustomValue by remember { mutableStateOf(Helpers.getCurrentDate()) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var selectedReportPeriod by remember { mutableStateOf("يومي") } // يومي, أسبوعي, شهري, سنوي, تقويم مخصص
    var customReportType by remember { mutableStateOf("يوم") }
    var customReportValue by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var customReportPrintDate by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var reportsWeekYearMonth by remember { mutableStateOf(Helpers.getCurrentDate().substring(0, 7)) }
    var reportsWeekNum by remember { mutableIntStateOf(Helpers.getCurrentWeekOfMonth()) }

    var showEditDialog by remember { mutableStateOf(false) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteReceiverByIdConfirm by remember { mutableStateOf(false) }
    var receiverRecordToDelete by remember { mutableStateOf<QatImportWeight?>(null) }

    var showMatchingDialog by remember { mutableStateOf(false) }
    var selectedPeriodicPeriod by remember { mutableStateOf("يومي") } // يومي, أسبوعي, شهري, سنوي, تقويم مخصص
    var customPeriodicType by remember { mutableStateOf("يوم") }
    var customPeriodicValue by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var customPeriodicPrintDate by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var periodicWeekYearMonth by remember { mutableStateOf(Helpers.getCurrentDate().substring(0, 7)) }
    var periodicWeekNum by remember { mutableIntStateOf(Helpers.getCurrentWeekOfMonth()) }
    var physicalCounts by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"
    var matchingReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    
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
        matchingReportDate = label
    }
    
    // Dialog inputs
    var itemName by remember { mutableStateOf("") }
    var itemQty by remember { mutableStateOf("") }
    var itemBuyPrice by remember { mutableStateOf("") }
    var itemSellPrice by remember { mutableStateOf("") }
    var itemLowThreshold by remember { mutableStateOf("") }
    var itemBuyPriceCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }
    LaunchedEffect(currencyConfig) {
        if (!showEditDialog) {
            itemBuyPriceCurrency = currencyConfig.resolvedDefaultCurrency
        }
    }
    var itemDateAdded by remember { mutableStateOf("") }
    var itemNumber by remember { mutableStateOf("") }
    var itemSupplierId by remember { mutableIntStateOf(0) }
    var itemSupplierName by remember { mutableStateOf("") }
    var itemReceiverName by remember { mutableStateOf("") }
    var itemReceiverId by remember { mutableIntStateOf(0) }

    val suppliersList by viewModel.allSuppliers.collectAsState(emptyList())

    val periodFilteredItems = remember(allItems, selectedPeriod, customPeriodValue, customPeriodTypeMapped) {
        allItems.filter {
            Helpers.matchesPeriod(it.dateAdded, selectedPeriod, customPeriodValue, customPeriodTypeMapped)
        }.sortedWith(
            compareBy<InventoryItem> { Helpers.parseToDateSafe(it.dateAdded) }
                .thenBy { it.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.itemNumber }
                .thenBy { it.id }
        )
    }

    val filteredItems = periodFilteredItems.filter {
        val matchesSearch = searchQuery.trim().isEmpty() ||
                it.name.contains(searchQuery, ignoreCase = true) ||
                it.buyPrice.toString().contains(searchQuery) ||
                it.itemNumber.contains(searchQuery, ignoreCase = true) ||
                it.supplierName.contains(searchQuery, ignoreCase = true) ||
                (searchQuery.trim() == "حرج" && it.quantity <= it.lowStockThreshold) ||
                (searchQuery.trim() == "نقص" && it.quantity <= it.lowStockThreshold) ||
                (searchQuery.trim() == "نواقص" && it.quantity <= it.lowStockThreshold)
        matchesSearch
    }

    val lowStockCount = periodFilteredItems.count { it.quantity <= it.lowStockThreshold }
    val totalInventoryValueYer = periodFilteredItems.filter { it.buyPriceCurrency.contains("يمني") }.sumOf { it.quantity * it.buyPrice }
    val totalInventoryValueSar = periodFilteredItems.filter { it.buyPriceCurrency.contains("سعودي") }.sumOf { it.quantity * it.buyPrice }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    title = { AgencyAppBarTitle("إدارة مخزون القات") },
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
                                topBarPdfSelectedPeriod = "يومي"
                                showTopBarPdfPeriodDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = "تصدير PDF", tint = PalIconUnified)
                        }
                        IconButton(
                            onClick = {
                                val recordText = buildString {
                                    appendLine("       كشف جرد المخزون")
                                    appendLine(com.example.util.BrandingManager.buildShareHeader())
                                    appendLine("تاريخ الجرد: ${Helpers.getCurrentDateTime()}")
                                    appendLine("--------------------------------")
                                    appendLine("تفاصيل وبنود المخزون:")
                                    appendLine("--------------------------------")
                                    for (item in filteredItems) {
                                        val cleanName = if (item.name.length > 20) item.name.substring(0, 19) else item.name
                                        appendLine("- صنف: $cleanName")
                                        appendLine(" • النوع: ${item.qatType.ifEmpty { "غير محدد" }}")
                                        appendLine(" • التفاصيل والملاحظات: ${item.notes.ifEmpty { "لا يوجد" }}")
                                        appendLine(" • تاريخ الإضافة: ${item.dateAdded.ifEmpty { "غير محدد" }}")
                                        appendLine(" • المتوفر: ${Helpers.formatQuantity(item.quantity)} حبة")
                                        appendLine(" • سعر التوريد (الشراء): ${Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency)}")
                                        appendLine(" • سعر البيع المقدر: ${Helpers.formatWithCurrency(item.sellPrice, item.buyPriceCurrency)}")
                                        appendLine("--------------------------------")
                                    }
                                    appendLine("إجمالي قيمة المخزون الدفترية:")
                                    appendLine("بالريال اليمني: ${Helpers.formatWithCurrency(totalInventoryValueYer, "ريال يمني")}")
                                    appendLine("بالريال السعودي: ${Helpers.formatWithCurrency(totalInventoryValueSar, "ريال سعودي")}")
                                    appendLine("--------------------------------")
                                    appendLine(com.example.util.BrandingManager.buildShareFooter())
                                }
                                bluetoothPrintText = recordText
                                showBluetoothPrintTrigger = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Print, contentDescription = "طباعة حرارية", tint = PalIconUnified)
                        }
                        Box {
                            IconButton(
                                onClick = { showOverflowMenu = true },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "خيارات إضافية", tint = PalIconUnified)
                            }
                            DropdownMenu(
                                expanded = showOverflowMenu,
                                onDismissRequest = { showOverflowMenu = false },
                                modifier = Modifier.background(PalBlackNormal)
                            ) {
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Balance, contentDescription = null, tint = PalIconUnified) },
                                    text = { Text("مطابقة الجرد والحيود", color = PalWhitePure, fontSize = 12.5.sp) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showMatchingDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    leadingIcon = { Icon(Icons.Default.Summarize, contentDescription = null, tint = PalIconUnified) },
                                    text = { Text("تقارير الأصناف والعمليات", color = PalWhitePure, fontSize = 12.5.sp) },
                                    onClick = {
                                        showOverflowMenu = false
                                        showReceiverSelectionDialog = true
                                    }
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedItem = null
                    itemName = ""
                    itemQty = ""
                    itemBuyPrice = ""
                    itemNumber = ""
                    itemSupplierId = 0
                    itemSupplierName = ""
                    itemLowThreshold = "5"
                    itemBuyPriceCurrency = currencyConfig.resolvedDefaultCurrency
                    itemDateAdded = Helpers.getCurrentDateTime()
                    itemReceiverName = ""
                    itemReceiverId = 0
                    showEditDialog = true
                },
                containerColor = PalGreenNormal,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "إضافة صنف")
            }
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("أنواع الأصناف", color = PalWhiteMuted, fontSize = 11.sp)
                            Text("${allItems.size}", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (lowStockCount > 0) PalRedNormal.copy(0.10f) else PalBlackNormal
                        ),
                        border = BorderStroke(1.dp, if (lowStockCount > 0) PalRedNormal.copy(0.4f) else PalBorderColor),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("النواقص", color = PalWhiteMuted, fontSize = 11.sp)
                            Text("$lowStockCount", color = if (lowStockCount > 0) PalRedNormal else PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                
                val showYerValuation = currencyConfig.allowsYer || totalInventoryValueYer != 0.0
                val showSarValuation = currencyConfig.allowsSar || totalInventoryValueSar != 0.0
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (showYerValuation) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (currencyConfig.allowsYer) "قيمة المخزون (يمني)" else "قيمة المخزون (يمني - تاريخي)",
                                    color = PalWhiteMuted,
                                    fontSize = 11.sp
                                )
                                Text(Helpers.formatWithCurrency(totalInventoryValueYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                    if (showSarValuation) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (currencyConfig.allowsSar) "قيمة المخزون (سعودي)" else "قيمة المخزون (سعودي - تاريخي)",
                                    color = PalWhiteMuted,
                                    fontSize = 11.sp
                                )
                                Text(Helpers.formatWithCurrency(totalInventoryValueSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }
                }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons for Qat Weight & Qat Stock Receiver
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        weightDateInput = Helpers.getCurrentDate()
                        val existing = allWeights.find { it.date == weightDateInput }
                        if (existing != null) {
                            weightInput = existing.weight.toString()
                            weightNotesInput = existing.notes
                        } else {
                            weightInput = ""
                            weightNotesInput = ""
                        }
                        showWeightDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Default.Balance, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("وزن القات المستورد", color = Color.White, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        receiverDateInput = Helpers.getCurrentDate()
                        showReceiverDialog = true
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = PalSoftGreenTint,
                        contentColor = PalWhitePure
                    ),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = PalIconUnified,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "مستلم مخزون القات",
                        color = PalWhitePure,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("البحث عن صنف أو رقم أو مورد...", color = PalWhiteMuted) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified) },
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PalGreenNormal,
                    unfocusedBorderColor = PalBorderColor,
                    focusedContainerColor = PalBlackNormal,
                    unfocusedContainerColor = PalBlackNormal,
                    focusedTextColor = PalWhitePure,
                    unfocusedTextColor = PalWhiteSoft
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // Items List
    if (filteredItems.isEmpty()) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("لا توجد أصناف تطابق البحث", color = PalWhiteMuted, textAlign = TextAlign.Center)
            }
        }
    } else {
        items(filteredItems) { item ->
                        val isLowStock = item.quantity <= item.lowStockThreshold
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, if (isLowStock) PalRedNormal.copy(0.45f) else PalBorderColor),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    selectedItem = item
                                    itemName = item.name
                                    itemQty = item.quantity.toString()
                                    itemBuyPrice = item.buyPrice.toString()
                                    itemLowThreshold = item.lowStockThreshold.toString()
                                    itemBuyPriceCurrency = item.buyPriceCurrency.ifEmpty { currencyConfig.resolvedDefaultCurrency }
                                    itemDateAdded = item.dateAdded.ifEmpty { Helpers.getCurrentDateTime() }
                                    itemNumber = item.itemNumber
                                    itemSupplierId = item.supplierId
                                    itemSupplierName = item.supplierName
                                    itemReceiverName = item.receiverName
                                    itemReceiverId = item.receiverId
                                    showEditDialog = true
                                }
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (isLowStock) PalRedNormal.copy(0.12f) else PalSoftGreenTint,
                                                    RoundedCornerShape(10.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isLowStock) Icons.Default.Warning else Icons.Default.Inventory,
                                                contentDescription = null,
                                                tint = if (isLowStock) PalRedNormal else PalIconUnified,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = item.name,
                                                color = PalWhitePure,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                textAlign = TextAlign.Start
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "تاريخ الإضافة: ${item.dateAdded.ifEmpty { "غير محدد" }}",
                                                color = PalWhiteMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "${if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity} حبة",
                                            color = if (isLowStock) PalRedNormal else PalWhitePure,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        if (isLowStock) {
                                            Text("مخزون حرج", color = PalRedNormal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                        } else {
                                            Text("متوفر", color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                }

                                HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 10.dp), thickness = 0.8.dp)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("سعر الشراء: ", color = PalWhiteMuted, fontSize = 12.sp)
                                            Text(
                                                Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency.ifEmpty { "ريال يمني" }),
                                                color = PalWhitePure,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("الرقم: ", color = PalWhiteMuted, fontSize = 12.sp)
                                            Text(item.itemNumber.ifEmpty { "لا يوجد" }, color = PalWhitePure, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                            Text(
                                                if (item.supplierName.isNotEmpty()) " | المورد: ${item.supplierName}" else "",
                                                color = PalSuccessNormal,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.End,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        IconButton(
                                            onClick = {
                                                val currencyStr = item.buyPriceCurrency.ifEmpty { "ريال يمني" }
                                                val itemShareMsg = buildString {
                                                    appendLine(com.example.util.BrandingManager.buildShareHeader("بيانات صنف مخزني"))
                                                    appendLine("---------------------------------")
                                                    appendLine("اسم الصنف: ${item.name}")
                                                    appendLine("نوع الصنف: ${item.qatType.ifEmpty { "غير محدد" }}")
                                                    appendLine("التفاصيل والملاحظات: ${item.notes.ifEmpty { "لا يوجد" }}")
                                                    appendLine("تاريخ الإضافة: ${item.dateAdded.ifEmpty { "غير محدد" }}")
                                                    appendLine("الكمية المتوفرة: ${Helpers.formatQuantity(item.quantity)} حبة")
                                                    appendLine("العملة المستخدمة: $currencyStr")
                                                    appendLine("سعر الشراء: ${Helpers.formatWithCurrency(item.buyPrice, currencyStr)}")
                                                    appendLine("سعر البيع المقدر: ${Helpers.formatWithCurrency(item.sellPrice, currencyStr)}")
                                                    appendLine("حالة المخزون: ${if (isLowStock) "مخزون حرج (تحت الحد)" else "متوفر وجاهز"}")
                                                    appendLine("---------------------------------")
                                                    append(com.example.util.BrandingManager.buildShareFooter())
                                                }
                                                Helpers.shareViaWhatsApp(context, "", itemShareMsg)
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "مشاركة عبر واتساب",
                                                tint = PalIconUnified,
                                                modifier = Modifier.size(19.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                val currencyStr = item.buyPriceCurrency.ifEmpty { "ريال يمني" }
                                                val itemReceipt = buildString {
                                                    appendLine("سند بيانات صنف مخزني تفصيلي")
                                                    appendLine("--------------------------------")
                                                    appendLine("اسم الصنف: ${item.name}")
                                                    appendLine("نوع الصنف: ${item.qatType.ifEmpty { "غير محدد" }}")
                                                    appendLine("تفاصيل وملاحظات: ${item.notes.ifEmpty { "لا يوجد" }}")
                                                    appendLine("تاريخ الإضافة: ${item.dateAdded.ifEmpty { "غير محدد" }}")
                                                    appendLine("المخزون الحالي: ${Helpers.formatQuantity(item.quantity)} حبة")
                                                    appendLine("العملة المعتمدة: $currencyStr")
                                                    appendLine("سعر الشراء: ${Helpers.formatWithCurrency(item.buyPrice, currencyStr)}")
                                                    appendLine("سعر البيع المقدر: ${Helpers.formatWithCurrency(item.sellPrice, currencyStr)}")
                                                    appendLine("حالة التوفر: ${if (isLowStock) "مخزون حرج (تحت الحد)" else "متوفر وجاهز"}")
                                                    appendLine("--------------------------------")
                                                    append(com.example.util.BrandingManager.buildShareFooter())
                                                }
                                                bluetoothPrintText = itemReceipt
                                                showBluetoothPrintTrigger = true
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = "طباعة حرارية لصنف",
                                                tint = PalIconUnified,
                                                modifier = Modifier.size(19.dp)
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

    if (showEditDialog) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = if (selectedItem == null) "إضافة صنف نوع قات جديد" else "تعديل بيانات الصنف",
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
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val currentQatTypes = remember(com.example.util.BrandingManager.qatTypes) {
                        com.example.util.BrandingManager.qatTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    }

                    // Row 1: Item Number & Item Name
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(0.35f)) {
                            AccountingTextField(
                                value = itemNumber,
                                onValueChange = { itemNumber = it },
                                label = "الرقم",
                                placeholder = "الرقم"
                            )
                        }
                        Box(modifier = Modifier.weight(0.65f)) {
                            AccountingTextField(
                                value = itemName,
                                onValueChange = { itemName = it },
                                label = "اسم الصنف",
                                placeholder = "مثال: بلدي، همداني"
                            )
                        }
                    }

                    if (currentQatTypes.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("أنواع القات المتاحة للاختيار السريع:", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                currentQatTypes.forEach { type ->
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, PalGreenLight.copy(0.5f), RoundedCornerShape(12.dp))
                                            .background(if (itemName == type) PalGreenDark else PalBlackDark, RoundedCornerShape(12.dp))
                                            .clickable { itemName = type }
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(type, color = PalWhitePure, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }

                    // Row 2: Quantity & Price
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(1.5f)) {
                            AccountingTextField(
                                value = itemQty,
                                onValueChange = { itemQty = it },
                                label = "الكمية (حبة)",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Box(modifier = Modifier.weight(1.5f)) {
                            AccountingTextField(
                                value = itemBuyPrice,
                                onValueChange = { itemBuyPrice = it },
                                label = "سعر الشراء",
                                placeholder = "0.00",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                    }

                    // Row 3: Supplier & Currency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        var supplierDropdownExpanded by remember { mutableStateOf(false) }
                        val filteredSuppliers = if (itemSupplierName.trim().isEmpty()) {
                            suppliersList
                        } else {
                            suppliersList.filter { it.name.contains(itemSupplierName, ignoreCase = true) }
                        }

                        Box(modifier = Modifier.weight(0.55f)) {
                            AccountingTextField(
                                value = itemSupplierName,
                                onValueChange = {
                                    itemSupplierName = it
                                    supplierDropdownExpanded = true
                                    val matched = suppliersList.find { s -> s.name.trim() == it.trim() }
                                    itemSupplierId = matched?.id ?: 0
                                },
                                label = "اسم المورد",
                                placeholder = "اختر المورد",
                                trailingIcon = {
                                    IconButton(onClick = { supplierDropdownExpanded = !supplierDropdownExpanded }) {
                                        Icon(
                                            imageVector = if (supplierDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                            contentDescription = "عرض الموردين",
                                            tint = PalWhiteMuted
                                        )
                                    }
                                }
                            )

                            if (supplierDropdownExpanded && filteredSuppliers.isNotEmpty()) {
                                androidx.compose.ui.window.Popup(
                                    onDismissRequest = { supplierDropdownExpanded = false }
                                ) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, PalBorderColor),
                                        modifier = Modifier
                                            .fillMaxWidth(0.55f)
                                            .heightIn(max = 140.dp)
                                    ) {
                                        LazyColumn(modifier = Modifier.padding(4.dp)) {
                                            items(filteredSuppliers) { sup ->
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable {
                                                            itemSupplierName = sup.name
                                                            itemSupplierId = sup.id
                                                            supplierDropdownExpanded = false
                                                        }
                                                        .padding(10.dp)
                                                ) {
                                                    Text(sup.name, color = PalWhitePure, fontSize = 12.sp)
                                                    if (sup.region.isNotEmpty()) {
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Text("(${sup.region})", color = PalWhiteMuted, fontSize = 11.sp)
                                                    }
                                                }
                                                Divider(color = PalBlackDark)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Currency Selector
                        Box(modifier = Modifier.weight(0.45f)) {
                            CurrencySelectionRow(
                                selectedCurrency = itemBuyPriceCurrency,
                                currencyConfig = currencyConfig,
                                onCurrencySelected = { itemBuyPriceCurrency = it },
                                title = "العملة",
                                useShortLabels = true
                            )
                        }
                    }

                    // Row 4: Low Threshold & Date Added
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(modifier = Modifier.weight(0.45f)) {
                            AccountingTextField(
                                value = itemLowThreshold,
                                onValueChange = { itemLowThreshold = it },
                                label = "الحد الأدنى",
                                placeholder = "5.0",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                        }
                        Box(modifier = Modifier.weight(0.55f)) {
                            AccountingTextField(
                                value = itemDateAdded,
                                onValueChange = { itemDateAdded = it },
                                label = "تاريخ الإضافة",
                                placeholder = "yyyy-MM-dd"
                            )
                        }
                    }

                    // Row 5: Qat Receiver Dropdown / Input Field
                    var receiverDropdownExpanded by remember { mutableStateOf(false) }
                    val filteredWeights = if (itemReceiverName.trim().isEmpty()) {
                        allWeights
                    } else {
                        allWeights.filter { it.receiverName.contains(itemReceiverName, ignoreCase = true) }
                    }

                    Box(modifier = Modifier.fillMaxWidth()) {
                        AccountingTextField(
                            value = itemReceiverName,
                            onValueChange = {
                                itemReceiverName = it
                                receiverDropdownExpanded = true
                            },
                            label = "مستلم مخزون القات",
                            placeholder = "اختر مستلم مخزون القات أو اكتب اسماً جديداً",
                            trailingIcon = {
                                IconButton(onClick = { receiverDropdownExpanded = !receiverDropdownExpanded }) {
                                    Icon(
                                        imageVector = if (receiverDropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                        contentDescription = "عرض المستلمين",
                                        tint = PalIconUnified
                                    )
                                }
                            }
                        )

                        if (receiverDropdownExpanded && filteredWeights.isNotEmpty()) {
                            androidx.compose.ui.window.Popup(
                                onDismissRequest = { receiverDropdownExpanded = false }
                            ) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, PalBorderColor),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp)
                                ) {
                                    LazyColumn(modifier = Modifier.padding(4.dp)) {
                                        items(filteredWeights) { weight ->
                                            val labelText = "${weight.receiverName} (${weight.date} | ${weight.weight} كجم) [ID: ${weight.id}]"
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        itemReceiverName = weight.receiverName
                                                        itemReceiverId = weight.id
                                                        receiverDropdownExpanded = false
                                                    }
                                                    .padding(10.dp)
                                            ) {
                                                Text(labelText, color = PalWhitePure, fontSize = 11.sp)
                                            }
                                            HorizontalDivider(color = PalBorderColor)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selectedItem != null) {
                        Button(
                            onClick = {
                                showDeleteConfirm = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("حذف", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            if (!currencyConfig.isSingleCurrencyAllowed(itemBuyPriceCurrency)) {
                                android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(itemBuyPriceCurrency), android.widget.Toast.LENGTH_LONG).show()
                                return@Button
                            }
                            val buyVal = MoneyMath.parseDoubleOrNull(itemBuyPrice) ?: 0.0
                            var resolvedReceiverId = itemReceiverId
                            if (resolvedReceiverId == 0 && itemReceiverName.trim().isNotEmpty()) {
                                val match = allWeights.find { it.receiverName.trim().equals(itemReceiverName.trim(), ignoreCase = true) }
                                if (match != null) {
                                    resolvedReceiverId = match.id
                                }
                            }
                            if (itemReceiverName.trim().isEmpty()) {
                                resolvedReceiverId = 0
                            }
                            val item = InventoryItem(
                                id = selectedItem?.id ?: 0,
                                name = itemName,
                                quantity = MoneyMath.parseDoubleOrNull(itemQty) ?: 0.0,
                                buyPrice = buyVal,
                                sellPrice = 0.0,
                                lowStockThreshold = MoneyMath.parseDoubleOrNull(itemLowThreshold) ?: 5.0,
                                buyPriceCurrency = itemBuyPriceCurrency,
                                dateAdded = itemDateAdded.ifEmpty { Helpers.getCurrentDateTime() },
                                itemNumber = itemNumber.trim(),
                                supplierId = itemSupplierId,
                                supplierName = itemSupplierName.trim(),
                                receiverName = itemReceiverName.trim(),
                                receiverId = resolvedReceiverId
                            )
                            viewModel.saveInventoryItem(item)
                            showEditDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("حفظ صنف", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showDeleteConfirm && selectedItem != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "تأكيد حذف الصنف",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف الصنف (${selectedItem!!.name}) نهائياً من المخزن؟ لا يمكن التراجع عن هذا الإجراء.",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteInventoryItem(selectedItem!!)
                        showDeleteConfirm = false
                        showEditDialog = false
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

    if (showReportsDialog) {
        val finalReportValue = if (selectedReportPeriod == "أسبوعي") "WEEK_FILTER|$reportsWeekYearMonth|$reportsWeekNum" else customReportValue
        val finalReportType = if (selectedReportPeriod == "أسبوعي") "أسبوعي" else customReportType

        val filteredInvoices = allInvoices.filter { Helpers.matchesPeriod(it.date, selectedReportPeriod, finalReportValue, finalReportType) }
        val filteredPurchases = allPurchases.filter { Helpers.matchesPeriod(it.date, selectedReportPeriod, finalReportValue, finalReportType) }

        val invoiceIds = filteredInvoices.map { it.id }.toSet()
        val purchaseIds = filteredPurchases.map { it.id }.toSet()

        val invoiceItemsForPeriod = allInvoiceItems.filter { it.invoiceId in invoiceIds }
        val purchaseItemsForPeriod = allPurchaseItems.filter { it.purchaseId in purchaseIds }

        // Compile Sections
        val sections = mutableListOf<ReportSection>()

        if (selectedReceiverForReport == "ALL_RECEIVERS" || selectedReceiverForReport.isEmpty()) {
            val periodWeights = allWeights.filter {
                Helpers.matchesPeriod(it.date, selectedReportPeriod, finalReportValue, finalReportType)
            }.sortedBy { it.date }

            periodWeights.forEach { weight ->
                val itemsForRec = allItems.filter { item ->
                    item.receiverId == weight.id || (item.receiverId == 0 && item.receiverName.trim().isNotEmpty() && item.receiverName.trim().equals(weight.receiverName.trim(), ignoreCase = true))
                }
                val itemStats = itemsForRec.map { item ->
                    val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                    val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                    Triple(item, soldQty, boughtQty)
                }.filter { (item, sold, bought) ->
                    sold > 0.0 || bought > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedReportPeriod, finalReportValue, finalReportType)
                }.sortedWith(
                    compareBy<Triple<InventoryItem, Double, Double>> { Helpers.parseToDateSafe(it.first.dateAdded) }
                        .thenBy { it.first.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.first.id }
                )

                if (itemStats.isNotEmpty()) {
                    val recUnits = itemStats.sumOf { if (it.third > 0.0) it.third else it.first.quantity }
                    val recValYer = itemStats.filter { it.first.buyPriceCurrency.contains("يمني") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                    val recValSar = itemStats.filter { it.first.buyPriceCurrency.contains("سعودي") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                    sections.add(
                        ReportSection(
                            title = "مستلم الشحنة: ${weight.receiverName}",
                            subtitle = "التاريخ: ${weight.date} | الوزن: ${weight.weight} كجم",
                            items = itemStats,
                            totalWeight = weight.weight,
                            totalUnitsBought = recUnits,
                            totalValueYer = recValYer,
                            totalValueSar = recValSar
                        )
                    )
                }
            }

            // Unreceived section
            val noReceiverItems = allItems.filter { item ->
                item.receiverId == 0 && item.receiverName.trim().isEmpty()
            }
            val noRecStats = noReceiverItems.map { item ->
                val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                Triple(item, soldQty, boughtQty)
            }.filter { (item, sold, bought) ->
                sold > 0.0 || bought > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedReportPeriod, finalReportValue, finalReportType)
            }.sortedWith(
                compareBy<Triple<InventoryItem, Double, Double>> { Helpers.parseToDateSafe(it.first.dateAdded) }
                    .thenBy { it.first.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.first.id }
            )

            if (noRecStats.isNotEmpty()) {
                val noRecUnits = noRecStats.sumOf { if (it.third > 0.0) it.third else it.first.quantity }
                val noRecValYer = noRecStats.filter { it.first.buyPriceCurrency.contains("يمني") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                val noRecValSar = noRecStats.filter { it.first.buyPriceCurrency.contains("سعودي") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                sections.add(
                    ReportSection(
                        title = "الأصناف غير المستلمة",
                        subtitle = "أصناف غير مرتبطة بمستلم مخزون محدد",
                        items = noRecStats,
                        totalWeight = 0.0,
                        totalUnitsBought = noRecUnits,
                        totalValueYer = noRecValYer,
                        totalValueSar = noRecValSar
                    )
                )
            }
        } else if (selectedReceiverForReport == "UNRECEIVED") {
            val noReceiverItems = allItems.filter { item ->
                item.receiverId == 0 && item.receiverName.trim().isEmpty()
            }
            val noRecStats = noReceiverItems.map { item ->
                val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                Triple(item, soldQty, boughtQty)
            }.filter { (item, sold, bought) ->
                sold > 0.0 || bought > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedReportPeriod, finalReportValue, finalReportType)
            }.sortedWith(
                compareBy<Triple<InventoryItem, Double, Double>> { Helpers.parseToDateSafe(it.first.dateAdded) }
                    .thenBy { it.first.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                    .thenBy { it.first.id }
            )

            if (noRecStats.isNotEmpty()) {
                val noRecUnits = noRecStats.sumOf { if (it.third > 0.0) it.third else it.first.quantity }
                val noRecValYer = noRecStats.filter { it.first.buyPriceCurrency.contains("يمني") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                val noRecValSar = noRecStats.filter { it.first.buyPriceCurrency.contains("سعودي") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                sections.add(
                    ReportSection(
                        title = "الأصناف غير المستلمة",
                        subtitle = "أصناف غير مرتبطة بمستلم مخزون محدد",
                        items = noRecStats,
                        totalWeight = 0.0,
                        totalUnitsBought = noRecUnits,
                        totalValueYer = noRecValYer,
                        totalValueSar = noRecValSar
                    )
                )
            }
        } else if (selectedReceiverForReport.startsWith("RECEIVER_ID:")) {
            val weightId = selectedReceiverForReport.substringAfter("RECEIVER_ID:").toIntOrNull()
            val weight = allWeights.find { it.id == weightId }
            if (weight != null) {
                val itemsForRec = allItems.filter { item ->
                    item.receiverId == weight.id || (item.receiverId == 0 && item.receiverName.trim().isNotEmpty() && item.receiverName.trim().equals(weight.receiverName.trim(), ignoreCase = true))
                }
                val itemStats = itemsForRec.map { item ->
                    val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                    val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                    Triple(item, soldQty, boughtQty)
                }.filter { (item, sold, bought) ->
                    sold > 0.0 || bought > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedReportPeriod, finalReportValue, finalReportType)
                }.sortedWith(
                    compareBy<Triple<InventoryItem, Double, Double>> { Helpers.parseToDateSafe(it.first.dateAdded) }
                        .thenBy { it.first.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.first.id }
                )

                if (itemStats.isNotEmpty()) {
                    val recUnits = itemStats.sumOf { if (it.third > 0.0) it.third else it.first.quantity }
                    val recValYer = itemStats.filter { it.first.buyPriceCurrency.contains("يمني") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                    val recValSar = itemStats.filter { it.first.buyPriceCurrency.contains("سعودي") }.sumOf { (if (it.third > 0.0) it.third else it.first.quantity) * it.first.buyPrice }
                    sections.add(
                        ReportSection(
                            title = "مستلم الشحنة: ${weight.receiverName}",
                            subtitle = "التاريخ: ${weight.date} | الوزن: ${weight.weight} كجم",
                            items = itemStats,
                            totalWeight = weight.weight,
                            totalUnitsBought = recUnits,
                            totalValueYer = recValYer,
                            totalValueSar = recValSar
                        )
                    )
                }
            }
        }

        val totalUnitsSold = sections.sumOf { it.items.sumOf { stat -> stat.second } }
        val totalUnitsBought = sections.sumOf { it.totalUnitsBought }
        val totalWeightsSum = sections.sumOf { it.totalWeight }
        val reportInventoryValueYer = sections.sumOf { it.totalValueYer }
        val reportInventoryValueSar = sections.sumOf { it.totalValueSar }

        AlertDialog(
            onDismissRequest = { showReportsDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "تقرير حركة المخزون والمبيعات بالتفصيل",
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
                        text = "اختر الفترة الزمنية لعرض وتصدير تقرير حركة المخزون والمبيعات بالتفصيل:",
                        color = PalWhiteMuted,
                        fontSize = 11.5.sp,
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
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteSoft
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(10.dp))
                                    .clickable { selectedReportPeriod = period }
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

                    if (selectedReportPeriod == "أسبوعي") {
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
                                text = "تحديد الشهر والسنة للأسبوع المحاسبي:",
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
                                            val parts = reportsWeekYearMonth.split("-")
                                            var year = parts[0].toInt()
                                            var month = parts[1].toInt()
                                            month -= 1
                                            if (month < 1) {
                                                month = 12
                                                year -= 1
                                            }
                                            reportsWeekYearMonth = String.format(java.util.Locale.US, "%04d-%02d", year, month)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = PalIconUnified)
                                }

                                val mParts = reportsWeekYearMonth.split("-")
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
                                            val parts = reportsWeekYearMonth.split("-")
                                            var year = parts[0].toInt()
                                            var month = parts[1].toInt()
                                            month += 1
                                            if (month > 12) {
                                                month = 1
                                                year += 1
                                            }
                                            reportsWeekYearMonth = String.format(java.util.Locale.US, "%04d-%02d", year, month)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.ChevronLeft, contentDescription = "الشهر التالي", tint = PalIconUnified)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "اختر الأسبوع من هذا الشهر:",
                                color = PalWhiteSoft,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..5).forEach { w ->
                                    val isSel = reportsWeekNum == w
                                    val weekLabels = arrayOf("", "الأول", "الثاني", "الثالث", "الرابع", "الخامس")
                                    val label = "الأسبوع ${weekLabels.getOrElse(w) { w.toString() }}"
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) PalGreenNormal else PalBlackDark)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { reportsWeekNum = w }
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

                    if (selectedReportPeriod == "تقويم مخصص") {
                        UnifiedPeriodSelector(
                            initialType = customReportType,
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
                                focusedBorderColor = PalGreenNormal,
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

                    // Metrics Banner
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(8.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي حبات بيعت بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text("${Helpers.formatQuantity(totalUnitsSold)} حبة", color = PalGreenLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي حبات استوردت بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text("${Helpers.formatQuantity(totalUnitsBought)} حبة", color = PalGoldCalligraphy, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي وزن القات المستورد:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text("${Helpers.formatQuantity(totalWeightsSum)} كجم", color = PalGreenLight, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        if (reportInventoryValueYer > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي مخزون يمني:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(Helpers.formatWithCurrency(reportInventoryValueYer, "ريال يمني"), color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                        if (reportInventoryValueSar > 0.0) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي مخزون سعودي:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(Helpers.formatWithCurrency(reportInventoryValueSar, "ريال سعودي"), color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            }
                        }
                    }

                    val receiversText = when (selectedReceiverForReport) {
                        "ALL_RECEIVERS" -> "تقرير جميع المستلمين"
                        "UNRECEIVED" -> "الأصناف غير المستلمة"
                        else -> {
                            val weightId = selectedReceiverForReport.substringAfter("RECEIVER_ID:").toIntOrNull()
                            val weight = allWeights.find { it.id == weightId }
                            weight?.receiverName ?: "غير محدد"
                        }
                    }

                    // Helper to get weight for PDF rows and text reports
                    val getWeightForItem = { item: InventoryItem ->
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

                    // Receiver Banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مستلم مخزون القات:", color = PalWhiteSoft, fontSize = 11.sp)
                        Text(receiversText, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }

                    Text(
                        text = "تفاصيل حركة وحالة الأصناف بالفترة والمستلم بالتفصيل:",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(6.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            sections.forEach { section ->
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(PalBlackNormal, RoundedCornerShape(10.dp))
                                        .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(10.dp))
                                        .padding(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(section.title, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text(section.subtitle, color = PalWhiteMuted, fontSize = 9.sp)
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (section.totalWeight > 0.0) {
                                            Text("الوزن: ${Helpers.formatQuantity(section.totalWeight)} كجم", color = PalSuccessNormal, fontSize = 9.sp)
                                        }
                                        Text("الوحدات: ${Helpers.formatQuantity(section.totalUnitsBought)} حبة", color = PalWhiteSoft, fontSize = 9.sp)
                                        if (section.totalValueYer > 0.0) {
                                            Text("يمني: ${Helpers.formatMoney(section.totalValueYer)}", color = PalSuccessNormal, fontSize = 9.sp)
                                        }
                                        if (section.totalValueSar > 0.0) {
                                            Text("سعودي: ${Helpers.formatMoney(section.totalValueSar)}", color = PalSuccessNormal, fontSize = 9.sp)
                                        }
                                    }

                                    HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 6.dp), thickness = 0.6.dp)

                                    val groupedInSection = section.items.groupBy { Helpers.standardizeDateToYmd(it.first.dateAdded) }
                                    groupedInSection.forEach { (groupDate, itemsInGroup) ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(PalBlackDark, RoundedCornerShape(6.dp))
                                                .padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(2f)) {
                                                itemsInGroup.forEach { (item, sold, bought) ->
                                                    Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                                        Text(item.name, color = PalWhitePure, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                        Text("سعر التوريد: ${Helpers.formatMoney(item.buyPrice)} ${item.buyPriceCurrency}", color = PalWhiteMuted, fontSize = 8.sp)
                                                    }
                                                }
                                            }
                                            Text(
                                                text = groupDate.split(" ").firstOrNull() ?: groupDate,
                                                color = PalWhiteSoft,
                                                fontSize = 9.sp,
                                                modifier = Modifier.weight(1.2f),
                                                textAlign = TextAlign.Center
                                            )
                                            Column(modifier = Modifier.weight(1.8f), horizontalAlignment = Alignment.End) {
                                                itemsInGroup.forEach { (item, sold, bought) ->
                                                    Column(modifier = Modifier.padding(vertical = 2.dp), horizontalAlignment = Alignment.End) {
                                                        Text("مباع: ${Helpers.formatQuantity(sold)} | مورد: ${Helpers.formatQuantity(bought)}", color = PalWhiteSoft, fontSize = 8.sp)
                                                        Text("مخزون: ${Helpers.formatQuantity(item.quantity)} حبة", color = if (item.quantity <= item.lowStockThreshold) PalRedNormal else PalSuccessNormal, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val reportMsg = buildString {
                        appendLine(com.example.util.BrandingManager.buildShareHeader("تقرير حركة وجرد المخزون والعمليات بالتفصيل"))
                        appendLine("الفترة: ${if (selectedReportPeriod == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", finalReportValue) else selectedReportPeriod}")
                        appendLine("مستلم مخزون القات: $receiversText")
                        appendLine("التاريخ: ${Helpers.getCurrentDateTime()}")
                        appendLine("--------------------------------")

                        sections.forEach { sec ->
                            appendLine(sec.title)
                            appendLine(sec.subtitle)
                            if (sec.totalWeight > 0.0) {
                                appendLine("إجمالي الوزن المستورد الكلي: ${sec.totalWeight} كجم")
                            }
                            appendLine("إجمالي الوحدات المستوردة: ${Helpers.formatQuantity(sec.totalUnitsBought)} حبة")
                            if (sec.totalValueYer > 0.0) {
                                appendLine("إجمالي القيمة (يمني): ${Helpers.formatWithCurrency(sec.totalValueYer, "ريال يمني")}")
                            }
                            if (sec.totalValueSar > 0.0) {
                                appendLine("إجمالي القيمة (سعودي): ${Helpers.formatWithCurrency(sec.totalValueSar, "ريال سعودي")}")
                            }
                            appendLine("--- الأصناف والكميات ---")

                            val groupedInSec = sec.items.groupBy { Helpers.standardizeDateToYmd(it.first.dateAdded) }
                            groupedInSec.forEach { (dateAdded, list) ->
                                appendLine("تاريخ الإضافة/الاستيراد: $dateAdded")
                                list.forEach { (item, sold, bought) ->
                                    val cleanName = if (item.name.length > 20) item.name.substring(0, 19) else item.name
                                    val itemNumStr = item.itemNumber.trim().ifEmpty { item.id.toString() }
                                    val itemWeightStr = if (sec.totalWeight > 0.0) "${Helpers.formatQuantity(sec.totalWeight)} كجم" else getWeightForItem(item)
                                    appendLine(" • صنف: $cleanName")
                                    appendLine("   - النوع: ${item.qatType.ifEmpty { "غير محدد" }}")
                                    appendLine("   - الرقم: $itemNumStr")
                                    appendLine("   - وزن القات المستورد: $itemWeightStr")
                                    appendLine("   - الكمية الحالية: ${Helpers.formatQuantity(item.quantity)} حبة")
                                    appendLine("   - مباع بالفترة: ${Helpers.formatQuantity(sold)} | مورد بالفترة: ${Helpers.formatQuantity(bought)}")
                                    appendLine("   - سعر التوريد: ${Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency)}")
                                }
                            }
                            appendLine("--------------------------------")
                        }

                        appendLine("- إجمالي وزن القات المستورد بالفترة: ${Helpers.formatQuantity(totalWeightsSum)} كجم")
                        appendLine("- إجمالي الوحدات المباعة بالفترة: ${Helpers.formatQuantityWithUnit(totalUnitsSold)}")
                        appendLine("- إجمالي الوحدات المستوردة بالفترة: ${Helpers.formatQuantityWithUnit(totalUnitsBought)}")
                        if (reportInventoryValueYer > 0.0) {
                            appendLine("- إجمالي مخزون يمني: ${Helpers.formatWithCurrency(reportInventoryValueYer, "ريال يمني")}")
                        }
                        if (reportInventoryValueSar > 0.0) {
                            appendLine("- إجمالي مخزون سعودي: ${Helpers.formatWithCurrency(reportInventoryValueSar, "ريال سعودي")}")
                        }
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
                                    "أسبوعي" -> "الأسبوعي (${Helpers.formatCustomPeriodToArabic("أسبوعي", finalReportValue)})"
                                    "شهري" -> "الشهري"
                                    "سنوي" -> "السنوي"
                                    "تقويم مخصص" -> "الخاص بالفترة المحدد ($customReportPrintDate)"
                                    else -> "الدوري"
                                }
                                val headers = listOf("التاريخ", "العدد المستورد", "وزن القات المستورد (كجم)", "الرقم", "سعر الحبة", "الإجمالي", "النوع")
                                val rows = mutableListOf<List<String>>()

                                sections.forEach { sec ->
                                    rows.add(listOf("SECTION_HEADER|${sec.title} - ${sec.subtitle}"))
                                    sec.items.forEach { (item, sold, bought) ->
                                        val importedQty = if (bought > 0.0) bought else item.quantity
                                        val totalVal = importedQty * item.buyPrice
                                        val weightValStr = if (sec.totalWeight > 0.0) "${Helpers.formatQuantity(sec.totalWeight)} كجم" else getWeightForItem(item)
                                        val itemNumStr = item.itemNumber.trim().ifEmpty { item.id.toString() }
                                        rows.add(listOf(
                                            item.dateAdded.ifEmpty { "غير محدد" },
                                            "${Helpers.formatQuantity(importedQty)} حبة",
                                            weightValStr,
                                            itemNumStr,
                                            Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency),
                                            Helpers.formatWithCurrency(totalVal, item.buyPriceCurrency),
                                            "${item.name} ${if (item.qatType.isNotEmpty()) "(${item.qatType})" else ""}"
                                        ))
                                    }
                                    val secTotalYerStr = if (sec.totalValueYer > 0.0) " | يمني: ${Helpers.formatWithCurrency(sec.totalValueYer, "ريال يمني")}" else ""
                                    val secTotalSarStr = if (sec.totalValueSar > 0.0) " | سعودي: ${Helpers.formatWithCurrency(sec.totalValueSar, "ريال سعودي")}" else ""
                                    val secWeightStr = if (sec.totalWeight > 0.0) " | وزن: ${Helpers.formatQuantity(sec.totalWeight)} كجم" else ""
                                    rows.add(listOf("SECTION_TOTALS|إجمالي الوحدات: ${Helpers.formatQuantity(sec.totalUnitsBought)} حبة$secWeightStr$secTotalYerStr$secTotalSarStr"))
                                }

                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "التقرير $tPeriodStr لحركة مخزن $bAgency",
                                    headers = headers,
                                    rows = rows,
                                    totals = buildMap {
                                        put("إجمالي وزن القات المستورد", "${Helpers.formatQuantity(totalWeightsSum)} كجم")
                                        put("إجمالي الوحدات المستوردة", Helpers.formatQuantityWithUnit(totalUnitsBought))
                                        if (reportInventoryValueYer > 0.0) {
                                            put("إجمالي القيمة بالريال اليمني", Helpers.formatWithCurrency(reportInventoryValueYer, "ريال يمني"))
                                        }
                                        if (reportInventoryValueSar > 0.0) {
                                            put("إجمالي القيمة بالريال السعودي", Helpers.formatWithCurrency(reportInventoryValueSar, "ريال سعودي"))
                                        }
                                    },
                                    customDate = if (selectedReportPeriod == "تقويم مخصص") customReportPrintDate else Helpers.getCurrentDateTime(),
                                    reportPeriod = if (selectedReportPeriod == "أسبوعي") Helpers.formatCustomPeriodToArabic("أسبوعي", finalReportValue) else if (selectedReportPeriod == "تقويم مخصص") "$customReportType : $customReportValue" else selectedReportPeriod,
                                    receiverName = receiversText
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                Helpers.shareViaWhatsApp(context, "", reportMsg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
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
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
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

    if (showMatchingDialog) {
        val finalPeriodicValue = if (selectedPeriodicPeriod == "أسبوعي") "WEEK_FILTER|$periodicWeekYearMonth|$periodicWeekNum" else customPeriodicValue
        val finalPeriodicType = if (selectedPeriodicPeriod == "أسبوعي") "أسبوعي" else customPeriodicType

        val filteredInvs = allInvoices.filter { Helpers.matchesPeriod(it.date, selectedPeriodicPeriod, finalPeriodicValue, finalPeriodicType) }
        val filteredPurchs = allPurchases.filter { Helpers.matchesPeriod(it.date, selectedPeriodicPeriod, finalPeriodicValue, finalPeriodicType) }
        val invoiceIds = filteredInvs.map { it.id }.toSet()
        val purchaseIds = filteredPurchs.map { it.id }.toSet()
        val invoiceItemsForPeriod = allInvoiceItems.filter { it.invoiceId in invoiceIds }
        val purchaseItemsForPeriod = allPurchaseItems.filter { it.purchaseId in purchaseIds }

        val sortedPeriodItems = allItems.filter { item ->
            val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
            val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
            soldQty > 0.0 || boughtQty > 0.0 || Helpers.matchesPeriod(item.dateAdded, selectedPeriodicPeriod, finalPeriodicValue, finalPeriodicType)
        }.sortedWith(
            compareBy<InventoryItem> { Helpers.parseToDateSafe(it.dateAdded) }
                .thenBy { it.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                .thenBy { it.itemNumber }
                .thenBy { it.id }
        )

        AlertDialog(
            onDismissRequest = { showMatchingDialog = false },
            title = {
                Text(
                    text = "تقرير جرد وتقييم المخزون الدوري",
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
                        text = "اختر الفترة الزمنية لعرض وتصدير تقرير الجرد والتقييم الدوري للمخزون:",
                        color = PalWhiteMuted,
                        fontSize = 11.5.sp,
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
                            val isSel = selectedPeriodicPeriod == period
                            val bg = if (isSel) PalGreenNormal else PalBlackDark
                            val fg = if (isSel) Color.White else PalWhiteSoft
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(10.dp))
                                    .clickable { selectedPeriodicPeriod = period }
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

                    if (selectedPeriodicPeriod == "أسبوعي") {
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
                                text = "تحديد الشهر والسنة للأسبوع المحاسبي:",
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
                                            val parts = periodicWeekYearMonth.split("-")
                                            var year = parts[0].toInt()
                                            var month = parts[1].toInt()
                                            month -= 1
                                            if (month < 1) {
                                                month = 12
                                                year -= 1
                                            }
                                            periodicWeekYearMonth = String.format(java.util.Locale.US, "%04d-%02d", year, month)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = PalIconUnified)
                                }

                                val mParts = periodicWeekYearMonth.split("-")
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
                                            val parts = periodicWeekYearMonth.split("-")
                                            var year = parts[0].toInt()
                                            var month = parts[1].toInt()
                                            month += 1
                                            if (month > 12) {
                                                month = 1
                                                year += 1
                                            }
                                            periodicWeekYearMonth = String.format(java.util.Locale.US, "%04d-%02d", year, month)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.ChevronLeft, contentDescription = "الشهر التالي", tint = PalIconUnified)
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "اختر الأسبوع من هذا الشهر:",
                                color = PalWhiteSoft,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                (1..5).forEach { w ->
                                    val isSel = periodicWeekNum == w
                                    val weekLabels = arrayOf("", "الأول", "الثاني", "الثالث", "الرابع", "الخامس")
                                    val label = "الأسبوع ${weekLabels.getOrElse(w) { w.toString() }}"
                                    
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSel) PalGreenNormal else PalBlackDark)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                            .clickable { periodicWeekNum = w }
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

                    if (selectedPeriodicPeriod == "تقويم مخصص") {
                        UnifiedPeriodSelector(
                            initialType = customPeriodicType,
                            initialValue = customPeriodicValue,
                            onPeriodChanged = { type, finalVal ->
                                customPeriodicType = type
                                customPeriodicValue = finalVal
                                customPeriodicPrintDate = Helpers.formatCustomPeriodToArabic(type, finalVal)
                            }
                        )

                        OutlinedTextField(
                            value = customPeriodicPrintDate,
                            onValueChange = { customPeriodicPrintDate = it },
                            label = { Text("تخصيص وتحديد تاريخ التقرير", color = PalWhiteSoft) },
                            placeholder = { Text("تاريخ تخصيص التقرير...", color = PalWhiteMuted) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenNormal,
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

                    HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 4.dp))

                    Text(
                        text = "تفاصيل المخزون وحركته المفلترة:",
                        color = PalWhitePure,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (sortedPeriodItems.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(PalBlackDark, RoundedCornerShape(10.dp))
                                .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("لا يوجد أصناف مضافة أو حركة مفعّلة في هذه الفترة الزمنية", color = PalWhiteMuted, fontSize = 11.5.sp)
                        }
                    } else {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            sortedPeriodItems.forEachIndexed { index, item ->
                                val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                                val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                                val itemNumStr = item.itemNumber.trim().ifEmpty { item.id.toString() }
                                
                                val saleItems = allInvoiceItems.filter { it.invoiceId in invoiceIds && it.itemId == item.id }
                                val saleDetails = saleItems.map { saleItem ->
                                    val inv = filteredInvs.find { it.id == saleItem.invoiceId }
                                    val method = inv?.paymentMethod ?: "نقداً"
                                    val price = saleItem.unitPrice
                                    val curr = saleItem.saleCurrency.ifEmpty { saleItem.currency }
                                    "${Helpers.formatWithCurrency(price, curr)} ($method)"
                                }.distinct()
                                val actualSellPriceStr = if (saleDetails.isEmpty()) "لا يوجد عمليات مبيعات" else saleDetails.joinToString(" \n ")

                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                    border = BorderStroke(1.dp, PalBorderColor),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(item.name, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("النوع: ${item.qatType.ifEmpty { "غير محدد" }}", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("الرقم: $itemNumStr", color = PalWhiteMuted, fontSize = 11.sp)
                                            Text("تاريخ الإضافة: ${item.dateAdded.ifEmpty { "غير محدد" }}", color = PalWhiteMuted, fontSize = 11.sp)
                                        }
                                        HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 6.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("المخزون الحالي: ${Helpers.formatQuantity(item.quantity)} حبة", color = PalWhiteSoft, fontSize = 11.sp)
                                            Text("سعر الشراء: ${Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency)}", color = PalWhiteSoft, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("مباع بالفترة: ${Helpers.formatQuantity(soldQty)} حبة", color = PalSuccessNormal, fontSize = 11.sp)
                                            Text("مورد بالفترة: ${Helpers.formatQuantity(boughtQty)} حبة", color = PalWhitePure, fontSize = 11.sp)
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text("سعر البيع الفعلي وطرق السداد:", color = PalWhiteMuted, fontSize = 10.sp)
                                        Text(actualSellPriceStr, color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    val compileReportMsg: () -> String = {
                        buildString {
                            appendLine(com.example.util.BrandingManager.buildShareHeader("كشف تقييم وجرد المخزون الدوري للمبيعات"))
                            val periodStr = when (selectedPeriodicPeriod) {
                                "أسبوعي" -> "الأسبوعي (${Helpers.formatCustomPeriodToArabic("أسبوعي", finalPeriodicValue)})"
                                "تقويم مخصص" -> customPeriodicPrintDate
                                else -> selectedPeriodicPeriod
                            }
                            appendLine("الفترة: $periodStr")
                            appendLine("----------------------------------------")
                            
                            val totalStockQty = sortedPeriodItems.sumOf { it.quantity }
                            val totalSoldQty = sortedPeriodItems.sumOf { item -> invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity } }
                            val totalBoughtQty = sortedPeriodItems.sumOf { item -> purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity } }
                            val totalBuyValueYer = sortedPeriodItems.filter { it.buyPriceCurrency.contains("يمني") || it.buyPriceCurrency.contains("YER") }.sumOf { it.quantity * it.buyPrice }
                            val totalBuyValueSar = sortedPeriodItems.filter { it.buyPriceCurrency.contains("سعودي") || it.buyPriceCurrency.contains("SAR") }.sumOf { it.quantity * it.buyPrice }

                            sortedPeriodItems.forEachIndexed { index, item ->
                                val soldQty = invoiceItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                                val boughtQty = purchaseItemsForPeriod.filter { it.itemId == item.id }.sumOf { it.quantity }
                                val saleItems = allInvoiceItems.filter { it.invoiceId in invoiceIds && it.itemId == item.id }
                                val itemNumStr = item.itemNumber.trim().ifEmpty { item.id.toString() }
                                
                                val uniquePrices = saleItems.map { saleItem ->
                                    val curr = saleItem.saleCurrency.ifEmpty { saleItem.currency }
                                    "${Helpers.formatMoney(saleItem.unitPrice)} $curr"
                                }.distinct()
                                val actualSellPriceStr = if (uniquePrices.isEmpty()) "لا يوجد" else uniquePrices.joinToString(" \n ")

                                val uniqueMethods = saleItems.map { saleItem ->
                                    val inv = filteredInvs.find { it.id == saleItem.invoiceId }
                                    inv?.paymentMethod ?: "نقداً"
                                }.distinct()
                                val paymentMethodsStr = if (uniqueMethods.isEmpty()) "-" else uniqueMethods.joinToString(" \n ")

                                appendLine("- صنف: ${item.name} (${item.qatType.ifEmpty { "غير محدد" }})")
                                appendLine(" • الرقم: $itemNumStr")
                                appendLine(" • تاريخ الإضافة: ${item.dateAdded.ifEmpty { "غير محدد" }}")
                                appendLine(" • المخزون الحالي: ${Helpers.formatQuantity(item.quantity)} حبة")
                                appendLine(" • مباع بالفترة: ${Helpers.formatQuantity(soldQty)} حبة")
                                appendLine(" • مورد بالفترة: ${Helpers.formatQuantity(boughtQty)} حبة")
                                appendLine(" • سعر الشراء: ${Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency)}")
                                appendLine(" • سعر البيع الفعلي:\n$actualSellPriceStr")
                                appendLine(" • طرق السداد:\n$paymentMethodsStr")
                                appendLine("----------------------------------------")
                            }
                            
                            appendLine("إجمالي الإحصائيات والتسويات:")
                            appendLine(" • عدد البنود المفلترة: ${sortedPeriodItems.size} بند")
                            appendLine(" • إجمالي كمية المخزون الحالي: ${Helpers.formatQuantity(totalStockQty)} حبة")
                            appendLine(" • إجمالي كمية المباع بالفترة: ${Helpers.formatQuantity(totalSoldQty)} حبة")
                            appendLine(" • إجمالي كمية المورد بالفترة: ${Helpers.formatQuantity(totalBoughtQty)} حبة")
                            appendLine(" • قيمة شراء المخزون الحالي (يمني): ${Helpers.formatWithCurrency(totalBuyValueYer, "ريال يمني")}")
                            appendLine(" • قيمة شراء المخزون الحالي (سعودي): ${Helpers.formatWithCurrency(totalBuyValueSar, "ريال سعودي")}")
                            appendLine("----------------------------------------")
                            appendLine(com.example.util.BrandingManager.buildShareFooter())
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val bAgency = com.example.util.BrandingManager.resolvedAgencyName()
                                val tPeriodStr = when (selectedPeriodicPeriod) {
                                    "أسبوعي" -> "الأسبوعي (${Helpers.formatCustomPeriodToArabic("أسبوعي", finalPeriodicValue)})"
                                    "تقويم مخصص" -> customPeriodicPrintDate
                                    else -> selectedPeriodicPeriod
                                }
                                val headers = listOf(
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

                                val rows = sortedPeriodItems.mapIndexed { index, item ->
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
                                Helpers.generatePdfAndShare(
                                    context = context,
                                    title = "كشف تقييم وجرد المخزون الدوري للمبيعات - $bAgency",
                                    headers = headers,
                                    rows = rows,
                                    totals = mapOf(
                                        "عدد البنود المفلترة" to "${sortedPeriodItems.size} بند",
                                        "إجمالي كمية المخزون الحالي" to "${Helpers.formatQuantity(totalStockQty)} حبة",
                                        "إجمالي كمية المباع بالفترة" to "${Helpers.formatQuantity(totalSoldQty)} حبة",
                                        "إجمالي كمية المورد بالفترة" to "${Helpers.formatQuantity(totalBoughtQty)} حبة",
                                        "قيمة شراء المخزون باليمني" to Helpers.formatWithCurrency(totalBuyValueYer, "ريال يمني"),
                                        "قيمة شراء المخزون بالسعودي" to Helpers.formatWithCurrency(totalBuyValueSar, "ريال سعودي")
                                    ),
                                    customDate = if (selectedPeriodicPeriod == "تقويم مخصص") customPeriodicPrintDate else Helpers.getCurrentDateTime(),
                                    reportPeriod = tPeriodStr
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("طباعة PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                val reportString = compileReportMsg()
                                Helpers.shareViaWhatsApp(context, "", reportString)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("واتساب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = {
                                val reportString = compileReportMsg()
                                bluetoothPrintText = reportString
                                showBluetoothPrintTrigger = true
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                            border = BorderStroke(1.dp, PalBorderColor),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(vertical = 10.dp)
                        ) {
                            Text("طباعة حرارية", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showMatchingDialog = false }) {
                    Text("إغلاق", color = PalIconUnified, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showBluetoothPrintTrigger) {
        com.example.ui.components.BluetoothPrintDialog(
            receiptText = bluetoothPrintText,
            onDismiss = { showBluetoothPrintTrigger = false }
        )
    }

    LaunchedEffect(showWeightDialog, weightDateInput) {
        if (showWeightDialog) {
            val stdDate = Helpers.standardizeDateToYmd(weightDateInput.trim())
            val existing = allWeights.find { Helpers.standardizeDateToYmd(it.date) == stdDate }
            if (existing != null) {
                weightInput = existing.weight.toString()
                weightNotesInput = existing.notes
            } else {
                weightInput = ""
                weightNotesInput = ""
            }
        }
    }

    if (showWeightDialog) {
        val stdDate = Helpers.standardizeDateToYmd(weightDateInput.trim())
        val existing = allWeights.find { Helpers.standardizeDateToYmd(it.date) == stdDate }
        AlertDialog(
            onDismissRequest = { showWeightDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "وزن القات المستورد اليومي",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp
                    )
                    IconButton(onClick = { showWeightDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = PalIconUnified)
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            text = "قم بإدخال وزن القات المستورد بالكيلو جرام للتاريخ المحدد لتسجيله وحفظه في النظام المحاسبي للوكالة:",
                            color = PalWhiteSoft,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = weightDateInput,
                            onValueChange = { weightDateInput = it },
                            label = { Text("التاريخ (yyyy-MM-dd)", color = PalWhiteMuted) },
                            placeholder = { Text("مثال: 2026-07-19", color = PalWhiteMuted) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = weightInput,
                            onValueChange = { weightInput = it },
                            label = { Text("وزن القات المستورد (كجم)", color = PalWhiteMuted) },
                            placeholder = { Text("أدخل الوزن بالكيلوجرام (مثال: 12.5)", color = PalWhiteMuted) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    item {
                        OutlinedTextField(
                            value = weightNotesInput,
                            onValueChange = { weightNotesInput = it },
                            label = { Text("ملاحظات (اختياري)", color = PalWhiteMuted) },
                            placeholder = { Text("أدخل أي ملاحظات إضافية هنا...", color = PalWhiteMuted) },
                            shape = RoundedCornerShape(10.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    if (allWeights.isNotEmpty()) {
                        item {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "السجلات المحفوظة سابقاً:",
                                color = PalWhitePure,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                border = BorderStroke(1.dp, PalBorderColor),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("التاريخ", color = PalWhiteMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                                        Text("الوزن (كجم)", color = PalWhiteMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(1.2f))
                                        Text("ملاحظات", color = PalWhiteMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.weight(2f))
                                    }
                                    HorizontalDivider(color = PalBorderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 4.dp))
                                    
                                    allWeights.take(10).forEach { weightItem ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    weightDateInput = weightItem.date
                                                    weightInput = weightItem.weight.toString()
                                                    weightNotesInput = weightItem.notes
                                                }
                                                .padding(vertical = 4.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(weightItem.date, color = PalWhiteSoft, fontSize = 11.sp, modifier = Modifier.weight(1.5f))
                                            Text("${weightItem.weight} كجم", color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1.2f))
                                            Text(weightItem.notes.ifEmpty { "لا يوجد" }, color = PalWhiteMuted, fontSize = 11.sp, maxLines = 1, modifier = Modifier.weight(2f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (existing != null) {
                        Button(
                            onClick = {
                                val stdDate = Helpers.standardizeDateToYmd(weightDateInput.trim()).ifEmpty { weightDateInput.trim() }
                                deleteWeightTargetDate = stdDate
                                showDeleteWeightConfirmDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("حذف السجل", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    Button(
                        onClick = {
                            val w = weightInput.toDoubleOrNull()
                            if (w != null && weightDateInput.trim().isNotEmpty()) {
                                val stdDate = Helpers.standardizeDateToYmd(weightDateInput.trim()).ifEmpty { weightDateInput.trim() }
                                viewModel.saveQatImportWeight(stdDate, w, weightNotesInput.trim())
                                showWeightDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (existing != null) "تعديل الوزن" else "حفظ الوزن", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showWeightDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    LaunchedEffect(showReceiverDialog, receiverDateInput) {
        if (showReceiverDialog) {
            editingReceiverWeightRecord = null
            receiverInput = ""
            receiverWeightInput = ""
            receiverNotesInput = ""
        }
    }

    if (showReceiverDialog) {
        val stdDate = Helpers.standardizeDateToYmd(receiverDateInput.trim()).ifEmpty { receiverDateInput.trim() }
        val weightsForSelectedDate = allWeights.filter { Helpers.standardizeDateToYmd(it.date) == stdDate }
        
        AlertDialog(
            onDismissRequest = { showReceiverDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "إدارة مستلمي مخزون القات",
                        color = PalWhitePure,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { showReceiverDialog = false }) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = PalIconUnified)
                    }
                }
            },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Text(
                            text = "يمكنك تسجيل أكثر من مستلم لمخزون القات في نفس اليوم، مع ربط كل مستلم بسجله الخاص ووزن القات المستورد التابع له بشكل مستقل.",
                            color = PalWhiteSoft,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    item {
                        AccountingTextField(
                            value = receiverDateInput,
                            onValueChange = { receiverDateInput = it },
                            label = "التاريخ (yyyy-MM-dd)",
                            placeholder = "مثال: 2026-07-20"
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "مستلمو اليوم (${weightsForSelectedDate.size}):",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (weightsForSelectedDate.isEmpty()) {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                    border = BorderStroke(1.dp, PalBorderColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "لا يوجد مستلمون مسجلون لهذا التاريخ بعد.",
                                            color = PalWhiteMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                weightsForSelectedDate.forEach { record ->
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                        border = BorderStroke(1.dp, PalBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                IconButton(
                                                    onClick = {
                                                        editingReceiverWeightRecord = record
                                                        receiverInput = record.receiverName
                                                        receiverWeightInput = record.weight.toString()
                                                        receiverNotesInput = record.notes
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Edit, contentDescription = "تعديل", tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                                }
                                                IconButton(
                                                    onClick = {
                                                        receiverRecordToDelete = record
                                                        showDeleteReceiverByIdConfirm = true
                                                    },
                                                    modifier = Modifier.size(48.dp)
                                                ) {
                                                    Icon(Icons.Default.Delete, contentDescription = "حذف", tint = PalRedNormal, modifier = Modifier.size(18.dp))
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(record.receiverName, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                                Spacer(modifier = Modifier.height(2.dp))
                                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    if (record.notes.isNotEmpty()) {
                                                        Text("(${record.notes})", color = PalWhiteMuted, fontSize = 10.sp)
                                                    }
                                                    Text("${record.weight} كجم", color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
                    }

                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = if (editingReceiverWeightRecord == null) "تسجيل مستلم ومخزون جديد" else "تعديل سجل المستلم",
                                color = PalWhitePure,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )

                            AccountingTextField(
                                value = receiverInput,
                                onValueChange = { receiverInput = it },
                                label = "اسم مستلم المخزون",
                                placeholder = "أدخل اسم مستلم مخزون القات..."
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = receiverNotesInput,
                                        onValueChange = { receiverNotesInput = it },
                                        label = "ملاحظات السجل",
                                        placeholder = "أي ملاحظات إضافية..."
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = receiverWeightInput,
                                        onValueChange = { receiverWeightInput = it },
                                        label = "الوزن المستورد (كجم)",
                                        placeholder = "0.00",
                                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (editingReceiverWeightRecord != null) {
                                    OutlinedButton(
                                        onClick = {
                                            editingReceiverWeightRecord = null
                                            receiverInput = ""
                                            receiverWeightInput = ""
                                            receiverNotesInput = ""
                                        },
                                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackDark, contentColor = PalWhitePure),
                                        border = BorderStroke(1.dp, PalBorderColor),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.weight(0.4f).heightIn(min = 48.dp)
                                    ) {
                                        Text("إلغاء التعديل", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Button(
                                    onClick = {
                                        if (receiverInput.trim().isNotEmpty() && receiverDateInput.trim().isNotEmpty()) {
                                            val w = receiverWeightInput.toDoubleOrNull() ?: 0.0
                                            val stdYmd = Helpers.standardizeDateToYmd(receiverDateInput.trim()).ifEmpty { receiverDateInput.trim() }
                                            viewModel.saveQatImportRecord(
                                                id = editingReceiverWeightRecord?.id ?: 0,
                                                date = stdYmd,
                                                weight = w,
                                                receiverName = receiverInput.trim(),
                                                notes = receiverNotesInput.trim()
                                            )
                                            editingReceiverWeightRecord = null
                                            receiverInput = ""
                                            receiverWeightInput = ""
                                            receiverNotesInput = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(0.6f).heightIn(min = 48.dp)
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                        Text(
                                            text = if (editingReceiverWeightRecord == null) "حفظ المستلم الجديد" else "تعديل سجل المستلم",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReceiverDialog = false }) {
                    Text("إغلاق النافذة", color = PalWhiteMuted, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showReceiverSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showReceiverSelectionDialog = false },
            title = {
                Text(
                    text = "اختيار مستلم المخزون",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "يرجى اختيار نوع التقرير أو تحديد مستلم شحنة معين لعرض وتصفية حركة المخزون بالتفصيل:",
                        color = PalWhiteSoft,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedReceiverForReport = "ALL_RECEIVERS"
                                        showReceiverSelectionDialog = false
                                        showReportsDialog = true
                                    },
                                colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                border = BorderStroke(1.dp, PalBorderColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.People, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                    Text("عرض تقرير جميع المستلمين (بدون تصفية)", color = PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedReceiverForReport = "UNRECEIVED"
                                        showReceiverSelectionDialog = false
                                        showReportsDialog = true
                                    },
                                colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                border = BorderStroke(1.dp, PalBorderColor),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = PalRedNormal, modifier = Modifier.size(18.dp))
                                    Text("الأصناف غير المستلمة", color = PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        items(allWeights) { weight ->
                            val displayName = "${weight.receiverName} (${weight.date} | ${weight.weight} كجم) [ID: ${weight.id}]"
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedReceiverForReport = "RECEIVER_ID:${weight.id}"
                                        showReceiverSelectionDialog = false
                                        showReportsDialog = true
                                    },
                                colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PalBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                    Text(displayName, color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReceiverSelectionDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showDeleteWeightConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteWeightConfirmDialog = false },
            title = {
                Text(
                    text = "تأكيد حذف سجل الوزن",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف سجل الوزن للتاريخ ($deleteWeightTargetDate)؟\n\nتنبيه: سيؤثر حذف سجل الوزن هذا على جميع أصناف القات المستوردة المرتبطة بنفس التاريخ داخل التقارير والإحصائيات.",
                    color = PalWhiteSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQatImportWeightByDate(deleteWeightTargetDate)
                        weightInput = ""
                        weightNotesInput = ""
                        showDeleteWeightConfirmDialog = false
                        showWeightDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، احذف السجل", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteWeightConfirmDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted, fontSize = 11.sp)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showDeleteReceiverByIdConfirm && receiverRecordToDelete != null) {
        val record = receiverRecordToDelete!!
        AlertDialog(
            onDismissRequest = { showDeleteReceiverByIdConfirm = false },
            title = {
                Text(
                    text = "تأكيد حذف المستلم",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف مستلم مخزون القات (${record.receiverName}) بوزن (${record.weight} كجم)؟\n\nتنبيه: سيؤدي هذا الإجراء إلى حذف سجل وزن الاستيراد وفك ربط جميع أصناف القات المرتبطة بهذا المستلم المحدد تلقائياً دون التأثير على بقية البيانات.",
                    color = PalWhiteSoft,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteQatImportWeightById(record.id)
                        showDeleteReceiverByIdConfirm = false
                        receiverRecordToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، احذف المستلم", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteReceiverByIdConfirm = false }) {
                    Text("إلغاء", color = PalWhiteMuted, fontSize = 11.sp)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showTopBarPdfPeriodDialog) {
        AlertDialog(
            onDismissRequest = { showTopBarPdfPeriodDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(22.dp))
                    Text(
                        text = "اختر الفترة الزمنية لتقرير المخزون",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Right
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "حدد الفترة الزمنية المراد إنشاء وطباعة تقرير جرد المخزون لها:",
                        color = PalWhiteSoft,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("يومي", "أسبوعي", "شهري").forEach { periodName ->
                                val isSelected = topBarPdfSelectedPeriod == periodName
                                val bg = if (isSelected) PalGreenNormal else PalBlackDark
                                val fg = if (isSelected) Color.White else PalWhiteSoft
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .border(BorderStroke(1.dp, if (isSelected) PalGreenNormal else PalBorderColor), RoundedCornerShape(10.dp))
                                        .clickable { topBarPdfSelectedPeriod = periodName }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(periodName, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf("سنوي", "تقويم مخصص").forEach { periodName ->
                                val isSelected = topBarPdfSelectedPeriod == periodName
                                val bg = if (isSelected) PalGreenNormal else PalBlackDark
                                val fg = if (isSelected) Color.White else PalWhiteSoft
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .border(BorderStroke(1.dp, if (isSelected) PalGreenNormal else PalBorderColor), RoundedCornerShape(10.dp))
                                        .clickable { topBarPdfSelectedPeriod = periodName }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(periodName, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (topBarPdfSelectedPeriod == "تقويم مخصص" || topBarPdfSelectedPeriod == "أسبوعي") {
                        val initType = if (topBarPdfSelectedPeriod == "أسبوعي") "أسبوع" else topBarPdfCustomType
                        val initVal = if (topBarPdfSelectedPeriod == "أسبوعي" && !topBarPdfCustomValue.startsWith("WEEK_FILTER|")) {
                            "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                        } else topBarPdfCustomValue
                        UnifiedPeriodSelector(
                            initialType = initType,
                            initialValue = initVal,
                            onPeriodChanged = { type, formattedVal ->
                                topBarPdfCustomType = type
                                topBarPdfCustomValue = formattedVal
                            }
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTopBarPdfPeriodDialog = false

                        val mappedCustomType = if (topBarPdfCustomType == "يوم" || topBarPdfCustomType == "date") "date"
                            else if (topBarPdfCustomType == "شهر" || topBarPdfCustomType == "month") "month"
                            else "year"

                        val pdfFilteredItems = allItems.filter { item ->
                            Helpers.matchesPeriod(item.dateAdded, topBarPdfSelectedPeriod, topBarPdfCustomValue, mappedCustomType)
                        }.sortedWith(
                            compareBy<InventoryItem> { Helpers.parseToDateSafe(it.dateAdded) }
                                .thenBy { it.itemNumber.toIntOrNull() ?: Int.MAX_VALUE }
                                .thenBy { it.itemNumber }
                                .thenBy { it.id }
                        )

                        val headers = listOf("التاريخ", "العدد المستورد", "الرقم", "سعر الحبة", "الإجمالي", "النوع")
                        val rows = pdfFilteredItems.map { item ->
                            val qtyText = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else Helpers.formatQuantity(item.quantity)
                            val dateStr = item.dateAdded.ifEmpty { "غير محدد" }
                            val totalVal = item.quantity * item.buyPrice
                            val qatTypeStr = if (item.qatType.isNotEmpty()) " (${item.qatType})" else ""
                            listOf(
                                dateStr,
                                "$qtyText حبة",
                                item.itemNumber.ifEmpty { "لا يوجد" },
                                Helpers.formatWithCurrency(item.buyPrice, item.buyPriceCurrency.ifEmpty { "ريال يمني" }),
                                Helpers.formatWithCurrency(totalVal, item.buyPriceCurrency.ifEmpty { "ريال يمني" }),
                                "${item.name}$qatTypeStr"
                            )
                        }

                        val totalsYer = pdfFilteredItems.filter { it.buyPriceCurrency.contains("يمني") }.sumOf { it.quantity * it.buyPrice }
                        val totalsSar = pdfFilteredItems.filter { it.buyPriceCurrency.contains("سعودي") }.sumOf { it.quantity * it.buyPrice }
                        val totalQty = pdfFilteredItems.sumOf { it.quantity }

                        val periodLabel = when (topBarPdfSelectedPeriod) {
                            "يومي" -> "اليومي (${Helpers.getCurrentDate()})"
                            "أسبوعي" -> "الأسبوعي"
                            "شهري" -> "الشهري (${Helpers.getCurrentDate().substring(0, 7)})"
                            "سنوي" -> "السنوي (${Helpers.getCurrentDate().substring(0, 4)})"
                            "تقويم مخصص" -> Helpers.formatCustomPeriodToArabic(topBarPdfCustomType, topBarPdfCustomValue)
                            else -> topBarPdfSelectedPeriod
                        }

                        val totalsMap = buildMap {
                            put("عدد البنود المفلترة", "${pdfFilteredItems.size} بند")
                            put("إجمالي كمية المخزون بالفترة", "${Helpers.formatQuantity(totalQty)} حبة")
                            if (totalsYer > 0.0) {
                                put("إجمالي قيمة المخزون بالريال اليمني", Helpers.formatWithCurrency(totalsYer, "ريال يمني"))
                            }
                            if (totalsSar > 0.0) {
                                put("إجمالي قيمة المخزون بالريال السعودي", Helpers.formatWithCurrency(totalsSar, "ريال سعودي"))
                            }
                        }

                        Helpers.generatePdfAndShare(
                            context = context,
                            title = "كشف جرد المخزون التفصيلي - $periodLabel",
                            headers = headers,
                            rows = rows,
                            totals = totalsMap,
                            reportPeriod = periodLabel
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("إنشاء وتصدير PDF", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTopBarPdfPeriodDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted, fontSize = 12.sp)
                }
            },
            containerColor = PalBlackNormal,
            shape = RoundedCornerShape(14.dp)
        )
    }
}
