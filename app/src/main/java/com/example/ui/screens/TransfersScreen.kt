package com.example.ui.screens

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
import com.example.data.MoneyTransfer
import com.example.data.TransferRecipientEntity
import com.example.ui.AppViewModel
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.components.AccountingTextField
import com.example.ui.components.CurrencySelectionRow
import com.example.ui.components.CurrencyTotalsSummaryCard
import com.example.ui.components.LedgerSectionHeader
import com.example.ui.theme.*
import com.example.util.BrandingManager
import com.example.util.CurrencyTotals
import com.example.util.Helpers
import com.example.util.MoneyMath
import com.example.util.ThermalReceiptTemplates

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransfersScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val transfersList by viewModel.allTransfers.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()
    val allRecipients by viewModel.allTransferRecipients.collectAsState()
    val activeRecipients by viewModel.activeTransferRecipients.collectAsState()

    val printerManager = remember { com.example.util.BluetoothPrinterManager.getInstance(context) }
    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"

    val selectedReportPeriod = selectedPeriod
    val customTransferType = customPeriodType
    val customTransferValue = customPeriodValue
    var transferReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }

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
        transferReportDate = label
    }

    var searchQuery by remember { mutableStateOf("") }

    var showAddDialog by remember { mutableStateOf(false) }
    var editingTransfer by remember { mutableStateOf<MoneyTransfer?>(null) }

    // Dialog inputs
    var inputAmount by remember { mutableStateOf("") }
    var inputSender by remember { mutableStateOf(BrandingManager.resolvedOwnerName()) }
    var inputReceiver by remember { mutableStateOf("") }
    var inputRef by remember { mutableStateOf("") }
    var inputStatement by remember { mutableStateOf("") }
    var inputNotes by remember { mutableStateOf("") }
    var inputDate by remember { mutableStateOf("") }
    var inputCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }
    var selectedRecipientId by remember { mutableIntStateOf(0) }
    LaunchedEffect(currencyConfig) {
        if (!showAddDialog) {
            inputCurrency = currencyConfig.resolvedDefaultCurrency
        }
    }

    // Recipient directory & per-recipient report states
    var showRecipientsDirectoryDialog by remember { mutableStateOf(false) }
    var reportRecipient by remember { mutableStateOf<TransferRecipientEntity?>(null) }

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var transferToDelete by remember { mutableStateOf<MoneyTransfer?>(null) }

    val todayDate = Helpers.getCurrentDate()
    val currentMonth = todayDate.substring(0, 7) // yyyy-MM
    val currentYear = todayDate.substring(0, 4)

    // Calculate dynamic stats using MoneyMath exact decimal arithmetic
    val todayTransfers = transfersList.filter { it.date.startsWith(todayDate) }
    val monthTransfers = transfersList.filter { it.date.startsWith(currentMonth) }

    val todayTotals = remember(todayTransfers) {
        CurrencyTotals.fromAmounts(todayTransfers.map { it.amount to it.currency })
    }
    val allTimeTotals = remember(transfersList) {
        CurrencyTotals.fromAmounts(transfersList.map { it.amount to it.currency })
    }

    // Lazy list computations
    val filteredTransfers = transfersList.filter { item ->
        val matchesPeriod = Helpers.matchesPeriod(item.date, selectedReportPeriod, customTransferValue, customTransferType)
        val linkedRec = allRecipients.find { it.id == item.recipientId }
        val matchesSearch = searchQuery.trim().isEmpty() ||
                item.sender.contains(searchQuery, ignoreCase = true) ||
                item.receiver.contains(searchQuery, ignoreCase = true) ||
                (linkedRec?.name?.contains(searchQuery, ignoreCase = true) == true) ||
                (linkedRec?.phone?.contains(searchQuery, ignoreCase = true) == true) ||
                item.referenceNumber.contains(searchQuery, ignoreCase = true) ||
                item.statement.contains(searchQuery, ignoreCase = true) ||
                item.notes.contains(searchQuery, ignoreCase = true)

        matchesPeriod && matchesSearch
    }.sortedBy { it.date }

    val filteredTotals = remember(filteredTransfers) {
        CurrencyTotals.fromAmounts(filteredTransfers.map { it.amount to it.currency })
    }

    // Text template for sharing reports
    val transferMsg = buildString {
        appendLine(BrandingManager.buildShareHeader("تقرير حركة وتحويلات الأموال ($selectedReportPeriod)"))
        appendLine("تاريخ التقرير المعتمد: $transferReportDate")
        appendLine("معيار البحث: ${if (searchQuery.isNotEmpty()) searchQuery else "الكل"}")
        appendLine("----------------------------------------")
        if (filteredTransfers.isEmpty()) {
            appendLine("لا توجد تحويلات مالية مقيدة تطابق المعايير.")
        } else {
            filteredTransfers.forEachIndexed { idx, item ->
                appendLine("${idx + 1}. حوالة بقيمة: ${MoneyMath.formatWithCurrency(item.amount, item.currency)}")
                appendLine("   • من: ${item.sender} -> إلى: ${item.receiver}")
                appendLine("   • مرجع: ${item.referenceNumber.ifEmpty { "غير محدد" }}")
                appendLine("   • تاريخ: ${item.date}")
                if (item.statement.isNotEmpty()) appendLine("   • الغرض/البيان: ${item.statement}")
                if (item.notes.isNotEmpty()) appendLine("   • ملاحظات: ${item.notes}")
                appendLine("- - - - - - - - - - - - - - - - - - - -")
            }
        }
        appendLine("----------------------------------------")
        filteredTotals.toDisplayLines(currencyConfig).forEach { (lbl, valueStr) ->
            appendLine("$lbl: $valueStr")
        }
        appendLine(BrandingManager.buildShareFooter())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { AgencyAppBarTitle("حركة وإدارة التحويلات المالية") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.navigateBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "رجوع",
                            tint = PalIconUnified
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showRecipientsDirectoryDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = "دليل المستلمين والتقارير",
                            tint = PalIconUnified
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
            )
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
            ) {


                // 1. Banner
                item {
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
                            Text(
                                text = bAgencyName,
                                color = PalWhitePure,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.5.sp,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = if (bOwnerName.isNotEmpty()) "الوكيل: $bOwnerName • سجل التحويلات المالية" else "سجل التحويلات المالية",
                                color = PalWhiteMuted,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }

                // 2. Stats (strictly separated currencies, zero cross-currency addition)
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        @Composable
                        fun TransferStatMiniCard(title: String, totals: CurrencyTotals, modifier: Modifier) {
                            Card(
                                modifier = modifier,
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                border = BorderStroke(1.dp, PalBorderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(title, color = PalWhiteMuted, fontSize = 10.sp, textAlign = TextAlign.Center)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    if (currencyConfig.allowsYer || totals.yer != 0.0) {
                                        Text(
                                            text = "يمني:\n" + MoneyMath.format(totals.yer),
                                            color = PalSuccessNormal,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                        Spacer(modifier = Modifier.height(3.dp))
                                    }
                                    if (currencyConfig.allowsSar || totals.sar != 0.0) {
                                        Text(
                                            text = "سعودي:\n" + MoneyMath.format(totals.sar),
                                            color = PalWhitePure,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.5.sp,
                                            lineHeight = 14.sp,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                        }

                        TransferStatMiniCard("تحويلات اليوم", todayTotals, Modifier.weight(1f))
                        TransferStatMiniCard("حوالات الفترة", filteredTotals, Modifier.weight(1f))
                        TransferStatMiniCard("المجموع الكلي", allTimeTotals, Modifier.weight(1f))
                    }
                }

                // 3. Primary Actions: Register Transfer + Recipient Directory
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                editingTransfer = null
                                inputAmount = ""
                                inputSender = BrandingManager.resolvedOwnerName()
                                inputReceiver = ""
                                selectedRecipientId = 0
                                inputRef = ""
                                inputStatement = ""
                                inputNotes = ""
                                inputDate = Helpers.getCurrentDateTime()
                                inputCurrency = currencyConfig.resolvedDefaultCurrency
                                showAddDialog = true
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCard,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text("قيد تسوية حوالة مالية جديدة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = { showRecipientsDirectoryDialog = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Group,
                                contentDescription = null,
                                tint = PalIconUnified,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = "دليل مستلمي الحوالات والتقارير (${allRecipients.size})",
                                color = PalWhitePure,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.5.sp
                            )
                        }
                    }
                }

                // 4. Filters Box
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "مشاركة وتقارير حركة الحوالات المالية",
                                color = PalWhitePure,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Right,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                listOf("يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص", "الكل").forEach { periodName ->
                                    val isSel = selectedReportPeriod == periodName
                                    val bg = if (isSel) PalGreenNormal else PalBlackDark
                                    val fg = if (isSel) Color.White else PalWhiteSoft
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                            .clickable {
                                                selectedPeriod = periodName
                                                if (periodName == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                                                    customPeriodValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(periodName, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
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
                                        transferReportDate = Helpers.formatCustomPeriodToArabic(type, finalVal)
                                    }
                                )
                            }

                            OutlinedTextField(
                                value = transferReportDate,
                                onValueChange = { transferReportDate = it },
                                label = { Text("تخصيص تاريخ التقرير المطبوع والمنشور", color = PalWhiteMuted, fontSize = 10.sp) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                leadingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified) },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PalGreenNormal,
                                    unfocusedBorderColor = PalBorderColor,
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft
                                ),
                                shape = RoundedCornerShape(10.dp)
                            )

                            CurrencyTotalsSummaryCard(
                                title = "إجماليات الحوالات للفترة والبحث (${filteredTransfers.size} حوالة):",
                                totals = filteredTotals,
                                currencyConfig = currencyConfig
                            )

                            // Share Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val headers = listOf("التاريخ والوقت", "المرسل ماليًا", "المستلم والصراف", "المبلغ المقيد", "رقم الحوالة", "البيان/الغرض", "ملاحظات")
                                        val rows = filteredTransfers.map { item ->
                                            listOf(
                                                item.date,
                                                item.sender,
                                                item.receiver,
                                                MoneyMath.formatWithCurrency(item.amount, item.currency),
                                                item.referenceNumber.ifEmpty { "—" },
                                                item.statement.ifEmpty { "—" },
                                                item.notes.ifEmpty { "—" }
                                            )
                                        }
                                        val pdfTotals = filteredTotals.toDisplayLines(currencyConfig).toMap()
                                        Helpers.generatePdfAndShare(
                                            context = context,
                                            title = "تقرير حركة التحويلات المالية",
                                            headers = headers,
                                            rows = rows,
                                            totals = pdfTotals,
                                            customDate = Helpers.getCurrentDateTime(),
                                            reportPeriod = transferReportDate
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("تصدير PDF", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        Helpers.shareViaWhatsApp(context, "", transferMsg)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("واتساب", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }

                                Button(
                                    onClick = {
                                        bluetoothPrintText = ThermalReceiptTemplates.buildTransfersLedgerSummaryReceipt(
                                            transfers = filteredTransfers,
                                            periodLabel = transferReportDate,
                                            recipientLabel = if (searchQuery.isBlank()) "الكل" else searchQuery,
                                            currencyConfig = currencyConfig,
                                            paperWidthMm = printerManager.currentPrinterSize
                                        )
                                        showBluetoothPrintTrigger = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenDark),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("طباعة حرارية", fontSize = 10.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                // 5. Search Bar
                item {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("البحث السريع في التحويلات بالمرسل أو المستلم أو المرجع...", color = PalWhiteMuted, fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedContainerColor = PalBlackNormal,
                            unfocusedContainerColor = PalBlackNormal,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // 6. Header
                item {
                    LedgerSectionHeader(
                        title = "سجل الحوالات المالية المنقحة",
                        trailing = {
                            Text(
                                text = "العدد: (${filteredTransfers.size})",
                                color = PalWhiteMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    )
                }

                // 7. Dynamic list elements
                if (filteredTransfers.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "لا توجد أي حوالات مطابقة لمعايير البحث والفلترة.",
                                color = PalWhiteMuted,
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(filteredTransfers, key = { it.id }) { item ->
                        val linkedRec = allRecipients.find { it.id == item.recipientId }
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "حوالة رقم: ${item.referenceNumber.ifEmpty { item.id.toString() }}",
                                        color = PalWhitePure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = item.date,
                                        color = PalWhiteMuted,
                                        fontSize = 10.sp
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                                        Text("المرسل: ${item.sender}", color = PalWhiteSoft, fontSize = 12.sp)
                                        val recDisplay = buildString {
                                            append("المستلم: ${linkedRec?.name ?: item.receiver}")
                                            if (linkedRec != null) {
                                                append(" (#${linkedRec.id})")
                                                if (!linkedRec.isActive) append(" [موقوف]")
                                            }
                                        }
                                        Text(recDisplay, color = PalWhiteSoft, fontSize = 12.sp)
                                    }

                                    Text(
                                        text = MoneyMath.formatWithCurrency(item.amount, item.currency),
                                        color = PalSuccessNormal,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp
                                    )
                                }

                                if (item.statement.isNotEmpty() || item.notes.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "البيان: ${item.statement} ${if (item.notes.isNotEmpty()) " (ملاحظة: ${item.notes})" else ""}",
                                        color = PalWhiteMuted,
                                        fontSize = 11.sp,
                                        textAlign = TextAlign.Right,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = PalBorderColor)
                                Spacer(modifier = Modifier.height(6.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (linkedRec != null) {
                                        IconButton(onClick = { reportRecipient = linkedRec }) {
                                            Icon(
                                                imageVector = Icons.Default.Assessment,
                                                contentDescription = "تقرير المستلم",
                                                tint = PalIconUnified
                                            )
                                        }
                                    }

                                    IconButton(onClick = {
                                        editingTransfer = item
                                        inputAmount = if (item.amount % 1.0 == 0.0) item.amount.toLong().toString() else item.amount.toString()
                                        inputSender = item.sender
                                        selectedRecipientId = item.recipientId
                                        inputReceiver = linkedRec?.name ?: item.receiver
                                        inputRef = item.referenceNumber
                                        inputStatement = item.statement
                                        inputNotes = item.notes
                                        inputDate = item.date
                                        inputCurrency = item.currency.ifEmpty { currencyConfig.resolvedDefaultCurrency }
                                        showAddDialog = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "تعديل الحوالة",
                                            tint = PalIconUnified
                                        )
                                    }

                                    IconButton(onClick = {
                                        val msg = buildString {
                                            appendLine(BrandingManager.buildShareHeader("سند تحويل مالي صادرة/واردة"))
                                            appendLine("-----------------------")
                                            appendLine("المبلغ: ${MoneyMath.formatWithCurrency(item.amount, item.currency)}")
                                            appendLine("المرسل: ${item.sender}")
                                            appendLine("المستلم: ${linkedRec?.name ?: item.receiver}")
                                            appendLine("رقم الحوالة: ${item.referenceNumber}")
                                            appendLine("تاريخ: ${item.date}")
                                            appendLine("البيان: ${item.statement}")
                                            appendLine("-----------------------")
                                            append(BrandingManager.buildShareFooter())
                                        }
                                        Helpers.shareText(context, msg)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "مشاركة",
                                            tint = PalIconUnified
                                        )
                                    }

                                    IconButton(onClick = {
                                        val smsMsg = buildString {
                                            appendLine(BrandingManager.buildShareHeader("سند حركة تحويل مالي"))
                                            appendLine("رقم المرجع: ${item.referenceNumber.ifEmpty { item.id.toString() }}")
                                            appendLine("التاريخ والوقت: ${item.date}")
                                            appendLine("المرسل: ${item.sender}")
                                            appendLine("المستلم والصراف: ${linkedRec?.name ?: item.receiver}")
                                            appendLine("المبلغ والعملة: ${MoneyMath.formatWithCurrency(item.amount, item.currency)}")
                                            appendLine("البيان والغرض: ${item.statement.ifEmpty { "لا يوجد" }}")
                                            appendLine("ملاحظات أخرى: ${item.notes.ifEmpty { "لا يوجد" }}")
                                            append(BrandingManager.buildShareFooter())
                                        }
                                        Helpers.shareViaSMS(context, linkedRec?.phone.orEmpty(), smsMsg)
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Email,
                                            contentDescription = "إرسال SMS",
                                            tint = PalIconUnified
                                        )
                                    }

                                    IconButton(onClick = {
                                        val headers = listOf("البند", "التفاصيل")
                                        val rows = listOf(
                                            listOf("رقم التحويل للمطابقة", item.referenceNumber.ifEmpty { item.id.toString() }),
                                            listOf("المرسل والمعتمد", item.sender),
                                            listOf("المستلم والصراف", linkedRec?.name ?: item.receiver),
                                            listOf("تعبير الحوالة الفعلي", item.statement),
                                            listOf("ملاحظات أخرى للمراجعة", item.notes.ifEmpty { "لا يوجد" }),
                                            listOf("تاريخ الحوالة", item.date)
                                        )
                                        Helpers.generatePdfAndShare(
                                            context = context,
                                            title = "سند قيد تحويل مالي",
                                            headers = headers,
                                            rows = rows,
                                            totals = mapOf("إجمالي قيمة الحوالة" to MoneyMath.formatWithCurrency(item.amount, item.currency)),
                                            customDate = item.date,
                                            recipientInfo = linkedRec?.let { "المستلم: ${it.name} (#${it.id})" }
                                        )
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = "طباعة PDF",
                                            tint = PalIconUnified
                                        )
                                    }

                                    IconButton(onClick = {
                                        bluetoothPrintText = ThermalReceiptTemplates.buildTransferVoucherReceipt(
                                            transfer = item,
                                            recipient = linkedRec,
                                            paperWidthMm = printerManager.currentPrinterSize
                                        )
                                        showBluetoothPrintTrigger = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Sensors,
                                            contentDescription = "طباعة حرارية للبلوتوث",
                                            tint = PalIconUnified
                                        )
                                    }

                                    IconButton(onClick = {
                                        transferToDelete = item
                                        showDeleteConfirm = true
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "حذف الحوالة",
                                            tint = PalRedNormal
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

    // --- Create / Edit Transfer Dialog ---
    if (showAddDialog) {
        var recipientSearch by remember { mutableStateOf("") }
        var showQuickNewRecipient by remember { mutableStateOf(false) }
        var quickRecName by remember { mutableStateOf("") }
        var quickRecPhone by remember { mutableStateOf("") }
        var quickRecCompany by remember { mutableStateOf("") }

        val selectableRecipients = remember(activeRecipients, allRecipients, editingTransfer) {
            val linkedInactive = editingTransfer?.recipientId?.let { rid ->
                allRecipients.find { it.id == rid && !it.isActive }
            }
            if (linkedInactive != null) activeRecipients + linkedInactive else activeRecipients
        }
        val filteredSelectableRecipients = remember(selectableRecipients, recipientSearch) {
            val q = recipientSearch.trim()
            if (q.isEmpty()) selectableRecipients else selectableRecipients.filter {
                it.name.contains(q, ignoreCase = true) ||
                    it.phone.contains(q, ignoreCase = true) ||
                    it.company.contains(q, ignoreCase = true) ||
                    it.id.toString() == q
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAddDialog = false
                editingTransfer = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = if (editingTransfer != null) "تعديل تفاصيل الحوالة المالية" else "تسجيل حركة تحويل مالي جديدة",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Financial details
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
                                text = "بيانات حركة التحويل المالية:",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            AccountingTextField(
                                value = inputAmount,
                                onValueChange = { inputAmount = it },
                                label = "مبلغ التحويل المالي",
                                placeholder = "أدخل المبلغ...",
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )

                            CurrencySelectionRow(
                                selectedCurrency = inputCurrency,
                                currencyConfig = currencyConfig,
                                onCurrencySelected = { inputCurrency = it },
                                title = "العملة المعتمدة"
                            )
                        }
                    }

                    // Card 2: Parties & Searchable Recipient Picker (keyed by unique recipientId)
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
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { showQuickNewRecipient = !showQuickNewRecipient }) {
                                    Text(
                                        text = if (showQuickNewRecipient) "إخفاء النموذج" else "+ مستلم جديد",
                                        color = PalGreenNormal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.5.sp
                                    )
                                }
                                Text(
                                    text = "أطراف المعاملة والمستلم المستقل:",
                                    color = PalWhitePure,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            AccountingTextField(
                                value = inputSender,
                                onValueChange = { inputSender = it },
                                label = "المرسل والمعتمد",
                                placeholder = "اسم المرسل أو المندوب..."
                            )

                            if (showQuickNewRecipient) {
                                Surface(
                                    color = PalBlackNormal,
                                    border = BorderStroke(1.dp, PalGreenNormal.copy(alpha = 0.5f)),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text("إضافة مستلم مستقل جديد (بسجل ومعرف مستقل):", color = PalSuccessNormal, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                        AccountingTextField(
                                            value = quickRecName,
                                            onValueChange = { quickRecName = it },
                                            label = "اسم المستلم الجديد",
                                            placeholder = "الاسم الكامل للمستلم أو الصراف..."
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Box(modifier = Modifier.weight(1f)) {
                                                AccountingTextField(
                                                    value = quickRecPhone,
                                                    onValueChange = { quickRecPhone = it },
                                                    label = "رقم الهاتف (اختياري)",
                                                    placeholder = "77xxxxxxx"
                                                )
                                            }
                                            Box(modifier = Modifier.weight(1f)) {
                                                AccountingTextField(
                                                    value = quickRecCompany,
                                                    onValueChange = { quickRecCompany = it },
                                                    label = "شركة الصرافة / الجهة",
                                                    placeholder = "اختياري..."
                                                )
                                            }
                                        }
                                        Button(
                                            onClick = {
                                                val clean = quickRecName.trim()
                                                if (clean.isEmpty()) {
                                                    android.widget.Toast.makeText(context, "يرجى كتابة اسم المستلم أولاً", android.widget.Toast.LENGTH_SHORT).show()
                                                } else {
                                                    viewModel.addTransferRecipient(
                                                        name = clean,
                                                        phone = quickRecPhone.trim(),
                                                        company = quickRecCompany.trim()
                                                    ) { created ->
                                                        selectedRecipientId = created.id
                                                        inputReceiver = created.name
                                                        showQuickNewRecipient = false
                                                        quickRecName = ""
                                                        quickRecPhone = ""
                                                        quickRecCompany = ""
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("إنشاء وتحديد المستلم", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            if (selectableRecipients.isNotEmpty()) {
                                OutlinedTextField(
                                    value = recipientSearch,
                                    onValueChange = { recipientSearch = it },
                                    placeholder = { Text("بحث سريع عن مستلم بالاسم أو الهاتف أو المعرف...", color = PalWhiteMuted, fontSize = 11.sp) },
                                    singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhiteSoft,
                                        focusedContainerColor = PalBlackNormal,
                                        unfocusedContainerColor = PalBlackNormal
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 135.dp)
                                        .background(PalBlackNormal, RoundedCornerShape(8.dp))
                                        .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                        .verticalScroll(rememberScrollState())
                                ) {
                                    filteredSelectableRecipients.forEach { rec ->
                                        val isSel = selectedRecipientId == rec.id
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(if (isSel) PalSoftGreenTint else Color.Transparent)
                                                .clickable {
                                                    selectedRecipientId = rec.id
                                                    inputReceiver = rec.name
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "${rec.name} (#${rec.id})${if (!rec.isActive) " [موقوف]" else ""}",
                                                    color = if (isSel) PalGreenNormal else PalWhitePure,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 12.sp
                                                )
                                                val meta = listOf(rec.phone, rec.company).filter { it.isNotBlank() }.joinToString(" • ")
                                                if (meta.isNotBlank()) {
                                                    Text(meta, color = PalWhiteMuted, fontSize = 10.sp)
                                                }
                                            }
                                            if (isSel) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = "محدد", tint = PalGreenNormal, modifier = Modifier.size(18.dp))
                                            }
                                        }
                                        HorizontalDivider(color = PalBorderColor.copy(alpha = 0.5f))
                                    }
                                }
                            }

                            AccountingTextField(
                                value = inputReceiver,
                                onValueChange = {
                                    inputReceiver = it
                                    val linked = selectableRecipients.find { r -> r.id == selectedRecipientId }
                                    if (linked != null && linked.name != it) {
                                        selectedRecipientId = 0
                                    }
                                },
                                label = if (selectedRecipientId > 0) "المستلم المحدد (#$selectedRecipientId)" else "اسم المستلم (أو اختر من القائمة أعلاه)",
                                placeholder = "أدخل اسم المستلم أو اختره من الدليل..."
                            )
                        }
                    }

                    // Card 3: Reference & Date
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
                                        value = inputRef,
                                        onValueChange = { inputRef = it },
                                        label = "الرقم المرجعي / السند",
                                        placeholder = "مثال: TXN-10293"
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = inputDate,
                                        onValueChange = { inputDate = it },
                                        label = "تاريخ ووقت القيد",
                                        placeholder = "yyyy-MM-dd HH:mm:ss"
                                    )
                                }
                            }
                        }
                    }

                    // Card 4: Statement & Notes
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
                                text = "البيان والملاحظات الإضافية:",
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
                                        value = inputStatement,
                                        onValueChange = { inputStatement = it },
                                        label = "البيان والغرض",
                                        placeholder = "بيان الغرض من التحويل..."
                                    )
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(
                                        value = inputNotes,
                                        onValueChange = { inputNotes = it },
                                        label = "ملاحظات اختيارية",
                                        placeholder = "أي ملاحظات إضافية..."
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (!currencyConfig.isSingleCurrencyAllowed(inputCurrency)) {
                            android.widget.Toast.makeText(context, currencyConfig.disabledCurrencyWarning(inputCurrency), android.widget.Toast.LENGTH_LONG).show()
                            return@Button
                        }
                        val amt = MoneyMath.parseDoubleOrNull(inputAmount) ?: 0.0
                        if (amt <= 0.0) {
                            android.widget.Toast.makeText(context, "يرجى إدخال مبلغ حوالة صحيح أكبر من الصفر", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (inputReceiver.trim().isEmpty() && selectedRecipientId <= 0) {
                            android.widget.Toast.makeText(context, "يرجى تحديد المستلم أو كتابة اسمه", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (editingTransfer != null) {
                            viewModel.updateTransfer(
                                editingTransfer!!.copy(
                                    amount = amt,
                                    sender = inputSender,
                                    receiver = inputReceiver.trim(),
                                    recipientId = selectedRecipientId,
                                    referenceNumber = inputRef,
                                    statement = inputStatement,
                                    notes = inputNotes,
                                    date = inputDate.ifEmpty { Helpers.getCurrentDateTime() },
                                    currency = inputCurrency
                                )
                            )
                            editingTransfer = null
                        } else {
                            viewModel.recordTransfer(
                                amount = amt,
                                sender = inputSender,
                                receiver = inputReceiver.trim(),
                                referenceNumber = inputRef,
                                statement = inputStatement,
                                notes = inputNotes,
                                currency = inputCurrency,
                                recipientId = selectedRecipientId,
                                date = inputDate.ifEmpty { Helpers.getCurrentDateTime() }
                            )
                        }
                        showAddDialog = false
                        inputAmount = ""
                        inputRef = ""
                        inputStatement = ""
                        inputNotes = ""
                        inputDate = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
                ) {
                    Text(if (editingTransfer != null) "حفظ التعديل" else "حفظ الحوالة", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddDialog = false
                    editingTransfer = null
                }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Delete Confirmation Dialog ---
    if (showDeleteConfirm && transferToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "تأكيد حذف الحوالة",
                    color = PalRedNormal,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    text = "سيتم التراجع عن هذه الحوالة المالية الصادرة وحذفها بشكل نهائي من قاعدة البيانات ولا يمكن استرجاعها.",
                    color = PalWhiteSoft,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteTransfer(transferToDelete!!)
                        showDeleteConfirm = false
                        transferToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal)
                ) {
                    Text("حذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showRecipientsDirectoryDialog) {
        TransferRecipientsDirectoryDialog(
            recipients = allRecipients,
            transfers = transfersList,
            currencyConfig = currencyConfig,
            onDismiss = { showRecipientsDirectoryDialog = false },
            onAddRecipient = { name, phone, company, notes ->
                viewModel.addTransferRecipient(name, phone, company, notes)
            },
            onUpdateRecipient = { updated ->
                viewModel.updateTransferRecipient(updated)
            },
            onToggleActive = { id, newActive ->
                viewModel.setTransferRecipientActive(id, newActive)
            },
            onOpenReport = { recipient ->
                reportRecipient = recipient
            }
        )
    }

    if (reportRecipient != null) {
        val currentRec = allRecipients.find { it.id == reportRecipient!!.id } ?: reportRecipient!!
        TransferRecipientReportDialog(
            recipient = currentRec,
            viewModel = viewModel,
            currencyConfig = currencyConfig,
            paperWidthMm = printerManager.currentPrinterSize,
            onDismiss = { reportRecipient = null },
            onThermalPrint = { receipt ->
                bluetoothPrintText = receipt
                showBluetoothPrintTrigger = true
            }
        )
    }

    if (showBluetoothPrintTrigger) {
        com.example.ui.components.BluetoothPrintDialog(
            receiptText = bluetoothPrintText,
            onDismiss = { showBluetoothPrintTrigger = false }
        )
    }
}

@Composable
fun TransferRecipientsDirectoryDialog(
    recipients: List<TransferRecipientEntity>,
    transfers: List<MoneyTransfer>,
    currencyConfig: com.example.util.CurrencyConfig,
    onDismiss: () -> Unit,
    onAddRecipient: (name: String, phone: String, company: String, notes: String) -> Unit,
    onUpdateRecipient: (TransferRecipientEntity) -> Unit,
    onToggleActive: (id: Int, isActive: Boolean) -> Unit,
    onOpenReport: (TransferRecipientEntity) -> Unit
) {
    val context = LocalContext.current
    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("الكل") } // "النشطون", "الموقوفون", "الكل"
    var editingRecipient by remember { mutableStateOf<TransferRecipientEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }

    var formName by remember { mutableStateOf("") }
    var formPhone by remember { mutableStateOf("") }
    var formCompany by remember { mutableStateOf("") }
    var formNotes by remember { mutableStateOf("") }

    val filtered = remember(recipients, search, statusFilter) {
        val q = search.trim()
        recipients.filter { r ->
            val matchesStatus = when (statusFilter) {
                "النشطون" -> r.isActive
                "الموقوفون" -> !r.isActive
                else -> true
            }
            val matchesQuery = q.isEmpty() ||
                r.name.contains(q, ignoreCase = true) ||
                r.phone.contains(q, ignoreCase = true) ||
                r.company.contains(q, ignoreCase = true) ||
                r.id.toString() == q
            matchesStatus && matchesQuery
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = {
                        editingRecipient = null
                        formName = ""
                        formPhone = ""
                        formCompany = ""
                        formNotes = ""
                        showForm = !showForm
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(if (showForm) "إغلاق" else "+ إضافة مستلم", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Text(
                    text = "دليل مستلمي الحوالات",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showForm) {
                    Surface(
                        color = PalBlackDark,
                        border = BorderStroke(1.dp, PalGreenNormal.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (editingRecipient == null) "إضافة مستلم مستقل جديد" else "تعديل بيانات المستلم (#${editingRecipient!!.id})",
                                color = PalSuccessNormal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            AccountingTextField(value = formName, onValueChange = { formName = it }, label = "اسم المستلم", placeholder = "الاسم...")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(value = formPhone, onValueChange = { formPhone = it }, label = "الهاتف", placeholder = "77xxxxxxx")
                                }
                                Box(modifier = Modifier.weight(1f)) {
                                    AccountingTextField(value = formCompany, onValueChange = { formCompany = it }, label = "الجهة / الصراف", placeholder = "اختياري...")
                                }
                            }
                            AccountingTextField(value = formNotes, onValueChange = { formNotes = it }, label = "ملاحظات", placeholder = "اختياري...")
                            Button(
                                onClick = {
                                    val clean = formName.trim()
                                    if (clean.isEmpty()) {
                                        android.widget.Toast.makeText(context, "يرجى إدخال اسم المستلم", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        if (editingRecipient == null) {
                                            onAddRecipient(clean, formPhone, formCompany, formNotes)
                                        } else {
                                            onUpdateRecipient(
                                                editingRecipient!!.copy(
                                                    name = clean,
                                                    phone = formPhone,
                                                    company = formCompany,
                                                    notes = formNotes
                                                )
                                            )
                                        }
                                        showForm = false
                                        editingRecipient = null
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("حفظ بيانات المستلم", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("ابحث بالاسم أو الهاتف أو المعرف #...", color = PalWhiteMuted, fontSize = 11.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedContainerColor = PalBlackDark,
                        unfocusedContainerColor = PalBlackDark
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("الكل", "النشطون", "الموقوفون").forEach { st ->
                        val isSel = statusFilter == st
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) PalGreenNormal else PalBlackDark)
                                .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                .clickable { statusFilter = st }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(st, color = if (isSel) Color.White else PalWhiteMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("لا يوجد مستلمون مطابقون", color = PalWhiteMuted, fontSize = 12.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filtered, key = { it.id }) { rec ->
                            val recTransfers = transfers.filter { it.recipientId == rec.id }
                            val recTotals = CurrencyTotals.fromAmounts(recTransfers.map { it.amount to it.currency })
                            Surface(
                                color = PalBlackDark,
                                border = BorderStroke(1.dp, if (rec.isActive) PalBorderColor else PalWarningNormal.copy(alpha = 0.45f)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (rec.isActive) "نشط" else "موقوف (سجل محفوظ)",
                                            color = if (rec.isActive) PalSuccessNormal else PalWarningNormal,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "${rec.name} (#${rec.id})",
                                            color = PalWhitePure,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    val sub = listOf(rec.phone, rec.company, "${recTransfers.size} حوالة")
                                        .filter { it.isNotBlank() }
                                        .joinToString(" • ")
                                    Text(sub, color = PalWhiteMuted, fontSize = 11.sp)
                                    val totSummary = recTotals.toDisplayLines(currencyConfig, includeZeroForAllowed = false)
                                        .joinToString(" | ") { "${it.second}" }
                                    if (totSummary.isNotBlank()) {
                                        Text(totSummary, color = PalSuccessNormal, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = { onOpenReport(rec) },
                                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(1.3f).heightIn(min = 38.dp)
                                        ) {
                                            Text("تقرير المستلم", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                editingRecipient = rec
                                                formName = rec.name
                                                formPhone = rec.phone
                                                formCompany = rec.company
                                                formNotes = rec.notes
                                                showForm = true
                                            },
                                            border = BorderStroke(1.dp, PalBorderColor),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(0.9f).heightIn(min = 38.dp)
                                        ) {
                                            Text("تعديل", color = PalWhitePure, fontSize = 11.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onToggleActive(rec.id, !rec.isActive) },
                                            border = BorderStroke(1.dp, PalBorderColor),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                            modifier = Modifier.weight(0.9f).heightIn(min = 38.dp)
                                        ) {
                                            Text(
                                                text = if (rec.isActive) "إيقاف" else "تفعيل",
                                                color = if (rec.isActive) PalWarningNormal else PalSuccessNormal,
                                                fontSize = 11.sp
                                            )
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
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = PalWhitePure, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PalBlackNormal
    )
}

@Composable
fun TransferRecipientReportDialog(
    recipient: TransferRecipientEntity,
    viewModel: AppViewModel,
    currencyConfig: com.example.util.CurrencyConfig,
    paperWidthMm: Int,
    onDismiss: () -> Unit,
    onThermalPrint: (String) -> Unit
) {
    val context = LocalContext.current
    // Strict query by unique recipient.id — NEVER mixes similar names
    val recipientTransfers by viewModel.getTransfersForRecipientFlow(recipient.id).collectAsState(initial = emptyList())

    var period by remember { mutableStateOf("الكل") } // "الكل", "يومي", "أسبوعي", "شهري", "سنوي", "نطاق مخصص"
    var rangeStart by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var rangeEnd by remember { mutableStateOf(Helpers.getCurrentDate()) }

    val customValue = when (period) {
        "نطاق مخصص" -> "$rangeStart|$rangeEnd"
        else -> ""
    }
    val mappedPeriod = if (period == "نطاق مخصص") "تقويم مخصص" else period
    val mappedType = if (period == "نطاق مخصص") "range" else ""

    val periodTransfers = remember(recipientTransfers, period, rangeStart, rangeEnd) {
        recipientTransfers.filter { tr ->
            Helpers.matchesPeriod(tr.date, mappedPeriod, customValue, mappedType)
        }
    }
    val totals = remember(periodTransfers) {
        CurrencyTotals.fromAmounts(periodTransfers.map { it.amount to it.currency })
    }
    val periodRangeLabel = when (period) {
        "نطاق مخصص" -> Helpers.formatCustomPeriodToArabic("range", "$rangeStart|$rangeEnd")
        "يومي" -> Helpers.getCurrentDate()
        else -> period
    }

    val shareText = remember(recipient, periodTransfers, totals, period, periodRangeLabel, currencyConfig) {
        buildString {
            appendLine(BrandingManager.buildShareHeader("تقرير حساب المستلم: ${recipient.name} (#${recipient.id})"))
            if (recipient.phone.isNotBlank()) appendLine("الهاتف: ${recipient.phone}")
            if (recipient.company.isNotBlank()) appendLine("الجهة/الصرافة: ${recipient.company}")
            appendLine("الحالة: ${if (recipient.isActive) "نشط" else "موقوف (سجل محفوظ)"}")
            appendLine("الفترة: $periodRangeLabel | العدد: ${periodTransfers.size} حوالة")
            appendLine("----------------------------------------")
            periodTransfers.forEachIndexed { idx, t ->
                val ref = t.referenceNumber.ifBlank { "#${t.id}" }
                appendLine("${idx + 1}. [$ref] ${MoneyMath.formatWithCurrency(t.amount, t.currency)} | ${t.date}")
                appendLine("   المرسل: ${t.sender}" + if (t.statement.isNotBlank()) " • ${t.statement}" else "")
            }
            appendLine("----------------------------------------")
            totals.toDisplayLines(currencyConfig).forEach { (k, v) -> appendLine("$k: $v") }
            append(BrandingManager.buildShareFooter())
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        title = {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                Text(
                    text = "كشف حساب المستلم: ${recipient.name} (#${recipient.id})",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Right
                )
                val meta = listOf(recipient.phone, recipient.company, if (recipient.isActive) "نشط" else "موقوف")
                    .filter { it.isNotBlank() }
                    .joinToString(" • ")
                Text(meta, color = PalWhiteMuted, fontSize = 11.sp)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 540.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf("الكل", "يومي", "أسبوعي", "شهري", "سنوي", "نطاق مخصص").forEach { p ->
                        val isSel = period == p
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) PalGreenNormal else PalBlackDark)
                                .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                .clickable { period = p }
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(p, color = if (isSel) Color.White else PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                if (period == "نطاق مخصص") {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(value = rangeStart, onValueChange = { rangeStart = it }, label = "من تاريخ (yyyy-MM-dd)", placeholder = "2026-01-01")
                        }
                        Box(modifier = Modifier.weight(1f)) {
                            AccountingTextField(value = rangeEnd, onValueChange = { rangeEnd = it }, label = "إلى تاريخ (yyyy-MM-dd)", placeholder = "2026-12-31")
                        }
                    }
                }

                CurrencyTotalsSummaryCard(
                    title = "إجماليات المستلم (${periodTransfers.size} حوالة):",
                    totals = totals,
                    currencyConfig = currencyConfig
                )

                if (periodTransfers.isEmpty()) {
                    Text(
                        text = "لا توجد حوالات مسجلة لهذا المستلم في الفترة المحددة.",
                        color = PalWhiteMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                    )
                } else {
                    periodTransfers.forEach { tr ->
                        Surface(
                            color = PalBlackDark,
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(
                                        MoneyMath.formatWithCurrency(tr.amount, tr.currency),
                                        color = PalSuccessNormal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(tr.referenceNumber.ifBlank { "#${tr.id}" }, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(tr.date, color = PalWhiteMuted, fontSize = 10.sp)
                                    Text("المرسل: ${tr.sender}", color = PalWhiteSoft, fontSize = 11.sp)
                                }
                                if (tr.statement.isNotBlank() || tr.notes.isNotBlank()) {
                                    Text(
                                        listOf(tr.statement, tr.notes).filter { it.isNotBlank() }.joinToString(" • "),
                                        color = PalWhiteMuted,
                                        fontSize = 10.5.sp
                                    )
                                }
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            onThermalPrint(
                                ThermalReceiptTemplates.buildRecipientTransfersReportReceipt(
                                    recipient = recipient,
                                    transfers = periodTransfers,
                                    periodLabel = period,
                                    dateRangeLabel = periodRangeLabel,
                                    currencyConfig = currencyConfig,
                                    paperWidthMm = paperWidthMm
                                )
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenDark),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حرارية", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = {
                            val headers = listOf("التاريخ", "المرجع", "المرسل", "المبلغ والعملة", "البيان")
                            val rows = periodTransfers.map { t ->
                                listOf(t.date, t.referenceNumber.ifBlank { "#${t.id}" }, t.sender, MoneyMath.formatWithCurrency(t.amount, t.currency), t.statement.ifBlank { "—" })
                            }
                            Helpers.generatePdfAndShare(
                                context = context,
                                title = "تقرير حساب المستلم: ${recipient.name}",
                                headers = headers,
                                rows = rows,
                                totals = totals.toDisplayLines(currencyConfig).toMap(),
                                customDate = Helpers.getCurrentDateTime(),
                                reportPeriod = periodRangeLabel,
                                recipientInfo = "المستلم: ${recipient.name} (#${recipient.id})" +
                                    (if (recipient.phone.isNotBlank()) " • ${recipient.phone}" else "") +
                                    (if (recipient.company.isNotBlank()) " • ${recipient.company}" else "")
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("PDF", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { Helpers.shareViaWhatsApp(context, recipient.phone, shareText) },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("واتساب", color = Color.White, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = { Helpers.shareViaSMS(context, recipient.phone, shareText) },
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("SMS", color = PalWhitePure, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("إغلاق", color = PalWhitePure, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = PalBlackNormal
    )
}
