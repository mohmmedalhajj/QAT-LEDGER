package com.example.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.example.data.Expense
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.util.BluetoothPrinterManager
import com.example.util.Helpers
import androidx.compose.ui.platform.testTag
import java.util.*

import com.example.licensing.EditionCapabilities
import com.example.ui.components.AgencyCurrencySettingsCard
import com.example.ui.components.CurrencySelectionRow
import com.example.ui.components.CurrencyTotalsSummaryCard
import com.example.ui.components.LicenseStatusSettingsCard
import com.example.ui.components.ProLockedScreenPlaceholder
import com.example.ui.components.UnifiedPeriodSelector
import com.example.ui.components.AccountingTextField
import androidx.compose.ui.graphics.asImageBitmap
import com.example.util.CurrencyMode
import com.example.util.CurrencyTotals
import com.example.util.MoneyMath
import com.qatledger.licensing.LicenseFeature

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpensesScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val expensesList by viewModel.allExpenses.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var showExpenseDialog by remember { mutableStateOf(false) }
    var inputAmount by remember { mutableStateOf("") }
    var inputNotes by remember { mutableStateOf("") }
    var selectedExpenseCurrency by remember { mutableStateOf(currencyConfig.resolvedDefaultCurrency) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var expenseToDelete by remember { mutableStateOf<Expense?>(null) }
    var editingExpense by remember { mutableStateOf<Expense?>(null) }
    var expenseReportDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }
    
    val categories = listOf("نقل ومواصلات", "إيجار المحل والجمارك", "تعبئة وتغليف وعمالة", "خدمات وتبرعات وطاقة", "مصاريف عامة أخرى")
    var selectedCategory by remember { mutableStateOf(categories[0]) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"
    val customExpenseReportType = customPeriodType
    val customExpenseReportValue = customPeriodValue
    var selectedCategoryFilter by remember { mutableStateOf("الكل") }

    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }

    val todayStr = Helpers.getCurrentDate()
    val curMonthStr = todayStr.substring(0, 7)
    val curYearStr = todayStr.substring(0, 4)

    val filteredList = expensesList.filter { exp ->
        val matchesPeriod = Helpers.matchesPeriod(exp.date, selectedPeriod, customExpenseReportValue, customExpenseReportType)
        val matchesCategory = selectedCategoryFilter == "الكل" || exp.category == selectedCategoryFilter
        val matchesSearch = searchQuery.trim().isEmpty() || 
                exp.notes.contains(searchQuery, ignoreCase = true) || 
                exp.category.contains(searchQuery, ignoreCase = true)
        
        matchesPeriod && matchesCategory && matchesSearch
    }.sortedBy { it.date }

    val expensesYer = filteredList.filter { MoneyMath.isYer(it.currency) }
    val expensesSar = filteredList.filter { MoneyMath.isSar(it.currency) }

    val totalExpenseSumYer = MoneyMath.sum(expensesYer.map { it.amount })
    val totalExpenseSumSar = MoneyMath.sum(expensesSar.map { it.amount })
    val expenseTotals = CurrencyTotals.fromAmounts(filteredList.map { it.amount to it.currency })

    val periodLabelText = when (selectedPeriod) {
        "الكل" -> "جميع الأوقات"
        "يومي" -> "اليوم"
        "أسبوعي" -> if (customPeriodValue.startsWith("WEEK_FILTER|")) {
            Helpers.formatCustomPeriodToArabic("أسبوع", customPeriodValue)
        } else {
            "الأسبوع الحالي"
        }
        "شهري" -> "الشهر الحالي"
        "سنوي" -> "السنة الحالية"
        "تقويم مخصص" -> if (customPeriodValue.startsWith("WEEK_FILTER|")) {
            Helpers.formatCustomPeriodToArabic("أسبوع", customPeriodValue)
        } else {
            "مخصص ($customPeriodValue)"
        }
        else -> selectedPeriod
    }

    val printExpensesMsg = buildString {
        appendLine(com.example.util.BrandingManager.buildShareHeader("كشف المصروفات والمنصرفات التشغيلية"))
        appendLine("فترة البحث والتصفية: $periodLabelText")
        appendLine("الفئة المحددة: $selectedCategoryFilter")
        appendLine("تاريخ الجرد: $expenseReportDate")
        appendLine("--------------------------------")
        if (filteredList.isEmpty()) {
            appendLine("لا توجد بنود مصروفات مسجلة مسبقاً لهذه الفئة.")
        } else {
            filteredList.forEach { exp ->
                appendLine("• الفئة: ${exp.category}")
                appendLine("  البيان: ${exp.notes.ifEmpty { "بدون شرح" }}")
                appendLine("  المبلغ: ${Helpers.formatWithCurrency(exp.amount, exp.currency)} | التاريخ: ${exp.date.split(" ")[0]}")
                appendLine("- - - - - - - - - - - - - - - -")
            }
        }
        appendLine("--------------------------------")
        appendLine("إجمالي المصروفات المصدرة:")
        expenseTotals.toDisplayLines(currencyConfig).forEach { (label, value) ->
            appendLine("- $label: $value")
        }
        appendLine(com.example.util.BrandingManager.buildShareFooter())
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { AgencyAppBarTitle("المصروفات الإدارية والعمومية") },
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
                                editingExpense = null
                                inputAmount = ""
                                inputNotes = ""
                                selectedExpenseCurrency = currencyConfig.resolvedDefaultCurrency
                                showExpenseDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "إضافة مصروف", tint = PalIconUnified)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor)
            }
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Stats Card
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("إجمالي مصروفات تشغيلية لـ ($periodLabelText)", color = PalWhiteMuted, fontSize = 11.sp)
                            if (currencyConfig.allowsYer || totalExpenseSumYer != 0.0) {
                                Text(Helpers.formatWithCurrency(totalExpenseSumYer, "ريال يمني"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                            if (currencyConfig.allowsSar || totalExpenseSumSar != 0.0) {
                                Text(Helpers.formatWithCurrency(totalExpenseSumSar, "ريال سعودي"), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(45.dp)
                                .background(PalRedNormal.copy(0.10f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.TrendingDown, contentDescription = null, tint = PalRedNormal, modifier = Modifier.size(22.dp))
                        }
                    }
                }
            }

            // Search and Filters Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("البحث في المصروفات والمنصرفات...", color = PalWhiteMuted, fontSize = 12.sp) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhitePure,
                        focusedContainerColor = PalBlackNormal,
                        unfocusedContainerColor = PalBlackNormal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Period Selection Filters
            item {
                val periods = listOf("الكل", "يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    periods.forEach { period ->
                        val isSel = selectedPeriod == period
                        val bg = if (isSel) PalGreenNormal else PalBlackNormal
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
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(period, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (selectedPeriod == "تقويم مخصص" || selectedPeriod == "أسبوعي") {
                item {
                    UnifiedPeriodSelector(
                        initialType = if (selectedPeriod == "أسبوعي") "أسبوع" else customPeriodType,
                        initialValue = if (selectedPeriod == "أسبوعي" && !customPeriodValue.startsWith("WEEK_FILTER|")) {
                            "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                        } else {
                            customPeriodValue.ifEmpty { Helpers.getCurrentDate() }
                        },
                        onPeriodChanged = { type, finalVal ->
                            customPeriodType = type
                            customPeriodValue = finalVal
                        }
                    )
                }
            }



            // Category Filter Scrollable Bar
            item {
                val fullCategoryFilters = listOf("الكل") + categories
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    fullCategoryFilters.forEach { cat ->
                        val isSel = selectedCategoryFilter == cat
                        val bg = if (isSel) PalGreenNormal else PalBlackNormal
                        val fg = if (isSel) Color.White else PalWhiteMuted
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                .clickable { selectedCategoryFilter = cat }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(cat, color = fg, fontSize = 11.sp, fontWeight = if (isSel) FontWeight.Bold else FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = expenseReportDate,
                    onValueChange = { expenseReportDate = it },
                    label = { Text("تعيين وتخصيص تاريخ التقرير المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth().testTag("expenses_report_date_input"),
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
            }

            // Print / Export Actions Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = {
                            val headers = listOf("التاريخ", "التصنيف وفئة المصروف", "البيان والشرح", "العملة", "المبلغ المقيد")
                            val rows = filteredList.map { exp ->
                                listOf(exp.date.split(" ")[0], exp.category, exp.notes.ifEmpty { "إداري" }, exp.currency, Helpers.formatWithCurrency(exp.amount, exp.currency))
                            }
                            Helpers.generatePdfAndShare(
                                context = context,
                                title = "كشف المصروفات والمنصرفات ($periodLabelText)",
                                headers = headers,
                                rows = rows,
                                totals = expenseTotals.toDisplayLines(currencyConfig).toMap(),
                                customDate = expenseReportDate,
                                reportPeriod = periodLabelText
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).height(44.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                    ) {
                        Text("PDF تصدير", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            Helpers.shareViaWhatsApp(context, "", printExpensesMsg)
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
                            Helpers.shareViaSMS(context, "", printExpensesMsg)
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
                            bluetoothPrintText = printExpensesMsg
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

            item {
                HorizontalDivider(color = PalBorderColor)
            }

            // Expenses List display
            if (filteredList.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                        Text("لا توجد بنود مصروفات مطابقة للبحث مسبقاً", color = PalWhiteMuted, fontSize = 12.sp)
                    }
                }
            } else {
                items(filteredList) { exp ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(PalSoftGreenTint, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Payments, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(exp.category, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("شرح: ${exp.notes.ifEmpty { "مصاريف إدارية عمومية" }}", color = PalWhiteMuted, fontSize = 10.sp)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(Helpers.formatWithCurrency(exp.amount, exp.currency), color = PalRedNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text(exp.date.split(" ")[0], color = PalWhiteMuted, fontSize = 9.sp)
                            }
                            IconButton(
                                onClick = {
                                    editingExpense = exp
                                    selectedCategory = exp.category
                                    inputAmount = exp.amount.toInt().toString()
                                    inputNotes = exp.notes
                                    selectedExpenseCurrency = exp.currency
                                    showExpenseDialog = true
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "تعديل المصروف", tint = PalIconUnified)
                            }
                            IconButton(
                                onClick = {
                                    expenseToDelete = exp
                                    showDeleteConfirm = true
                                },
                                modifier = Modifier.minimumInteractiveComponentSize()
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "حذف المصروف", tint = PalRedNormal)
                            }
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

    if (showExpenseDialog) {
        AlertDialog(
            onDismissRequest = { 
                showExpenseDialog = false
                editingExpense = null
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = { Text(if (editingExpense != null) "تعديل بند المصروف" else "قيد بند مصروفات تشغيلي", color = PalWhitePure, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Category selection
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "تصنيف بند المصروف المعتمد:",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                categories.forEach { cat ->
                                    val isSel = selectedCategory == cat
                                    val bg = if (isSel) PalGreenNormal else PalBlackNormal
                                    val borderCol = if (isSel) PalGreenNormal else PalBorderColor
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(bg)
                                            .border(1.dp, borderCol, RoundedCornerShape(8.dp))
                                            .clickable { selectedCategory = cat }
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Text(
                                            text = cat,
                                            color = if (isSel) Color.White else PalWhitePure,
                                            fontSize = 12.sp,
                                            fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                            textAlign = TextAlign.Right
                                        )
                                        if (isSel) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .background(Color.White, androidx.compose.foundation.shape.CircleShape)
                                            )
                                        }
                                    }
                                }
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
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "القيمة والعملة المعتمدة:",
                                color = PalWhitePure,
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
                                    value = inputAmount,
                                    onValueChange = { inputAmount = it },
                                    label = "مبلغ المصروف *",
                                    placeholder = "أدخل مبلغ المصروف...",
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                )
                                CurrencySelectionRow(
                                    selectedCurrency = selectedExpenseCurrency,
                                    currencyConfig = currencyConfig,
                                    onCurrencySelected = { selectedExpenseCurrency = it },
                                    title = "نوع العملة:",
                                    useShortLabels = false
                                )
                            }
                        }
                    }

                    // Card 3: Notes & Statement
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "تفاصيل الصرف والبيان المرفق:",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Right
                            )

                            AccountingTextField(
                                value = inputNotes,
                                onValueChange = { inputNotes = it },
                                label = "ملاحظات وتفاصيل الصادر",
                                placeholder = "أدخل أي ملاحظات مرافقة أو شرح لتفصيل المصروف..."
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amount = MoneyMath.parseDoubleOrNull(inputAmount) ?: 0.0
                        if (amount <= 0.0) {
                            android.widget.Toast.makeText(context, "الرجاء تحديد مبلغ مصروفات صحيح!", android.widget.Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (!currencyConfig.isSingleCurrencyAllowed(selectedExpenseCurrency)) {
                            android.widget.Toast.makeText(
                                context,
                                currencyConfig.disabledCurrencyWarning(selectedExpenseCurrency),
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                            return@Button
                        }
                        if (editingExpense != null) {
                            viewModel.updateExpense(editingExpense!!.copy(category = selectedCategory, amount = amount, notes = inputNotes, currency = selectedExpenseCurrency))
                            editingExpense = null
                        } else {
                            viewModel.recordExpense(selectedCategory, amount, inputNotes, currency = selectedExpenseCurrency)
                        }
                        showExpenseDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (editingExpense != null) "حفظ التعديل" else "قيد الصادر", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showExpenseDialog = false
                        editingExpense = null
                    }
                ) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

        if (showDeleteConfirm && expenseToDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    text = "تأكيد حذف المصروف",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف هذا المصروف بقيمة (${com.example.util.Helpers.formatMoney(expenseToDelete!!.amount)}) من فئة (${expenseToDelete!!.category})؟ لا يمكن التراجع عن هذه العملية.",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteExpense(expenseToDelete!!)
                        showDeleteConfirm = false
                        expenseToDelete = null
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val invoices by viewModel.allInvoices.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val taxHistory by viewModel.allTaxHistory.collectAsState()
    val supplies by viewModel.allSuppliers.collectAsState()
    val items by viewModel.inventoryItems.collectAsState()
    val purchases by viewModel.allPurchases.collectAsState()
    val transfers by viewModel.allTransfers.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var selectedPeriodStr by remember { mutableStateOf("يومي") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }

    var accountingWeekYearMonth by remember {
        val todayStr = Helpers.getCurrentDate()
        mutableStateOf(todayStr.substring(0, 7))
    }
    var accountingWeekNum by remember {
        mutableStateOf(Helpers.getCurrentWeekOfMonth())
    }

    LaunchedEffect(selectedPeriodStr, accountingWeekYearMonth, accountingWeekNum) {
        if (selectedPeriodStr == "أسبوعي") {
            customPeriodValue = "WEEK_FILTER|$accountingWeekYearMonth|$accountingWeekNum"
        }
    }

    val selectedPeriod = when (selectedPeriodStr) {
        "يومي" -> 0
        "أسبوعي" -> 1
        "شهري" -> 2
        "سنوي" -> 3
        "تقويم مخصص" -> 4
        else -> 4
    }
    var accountingReportPrintDate by remember { mutableStateOf(Helpers.getCurrentDateTime()) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { AgencyAppBarTitle("التحاسب التجاري والتقارير العامة") },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PalIconUnified)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor)
            }
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
            ScrollableTabRow(
                selectedTabIndex = selectedPeriod,
                containerColor = PalBlackNormal,
                contentColor = PalGreenNormal,
                edgePadding = 8.dp
            ) {
                Tab(selected = selectedPeriod == 0, onClick = { selectedPeriodStr = "يومي" }, text = { Text("اليومية", fontSize = 12.sp, color = if (selectedPeriod == 0) PalGreenNormal else PalWhiteMuted, fontWeight = if (selectedPeriod == 0) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedPeriod == 1, onClick = { selectedPeriodStr = "أسبوعي" }, text = { Text("أسبوعية", fontSize = 12.sp, color = if (selectedPeriod == 1) PalGreenNormal else PalWhiteMuted, fontWeight = if (selectedPeriod == 1) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedPeriod == 2, onClick = { selectedPeriodStr = "شهري" }, text = { Text("الشهرية", fontSize = 12.sp, color = if (selectedPeriod == 2) PalGreenNormal else PalWhiteMuted, fontWeight = if (selectedPeriod == 2) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedPeriod == 3, onClick = { selectedPeriodStr = "سنوي" }, text = { Text("السنوية", fontSize = 12.sp, color = if (selectedPeriod == 3) PalGreenNormal else PalWhiteMuted, fontWeight = if (selectedPeriod == 3) FontWeight.Bold else FontWeight.Normal) })
                Tab(selected = selectedPeriod == 4, onClick = { selectedPeriodStr = "تقويم مخصص" }, text = { Text("تقويم مخصص", fontSize = 12.sp, color = if (selectedPeriod == 4) PalGreenNormal else PalWhiteMuted, fontWeight = FontWeight.Bold) })
            }

            if (selectedPeriod == 1) {
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
                            modifier = Modifier.minimumInteractiveComponentSize()
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
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "الشهر التالي", tint = PalIconUnified)
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
                            val isSel = accountingWeekNum == w
                            val weekLabels = arrayOf("", "الأول", "الثاني", "الثالث", "الرابع", "الخامس")
                            val label = "الأسبوع ${weekLabels.getOrElse(w) { w.toString() }}"
                            
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) PalGreenNormal else PalBlackDark)
                                    .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                    .clickable { accountingWeekNum = w }
                                    .padding(vertical = 8.dp),
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

            if (selectedPeriod == 4) {
                UnifiedPeriodSelector(
                    initialType = customPeriodType,
                    initialValue = customPeriodValue,
                    onPeriodChanged = { type, finalVal ->
                        customPeriodType = type
                        customPeriodValue = finalVal
                    }
                )
            }

            // Calculations based on Period selection using unified Helpers.matchesPeriod
            val periodStr = when (selectedPeriod) {
                0 -> "يومي"
                1 -> "أسبوعي"
                2 -> "شهري"
                3 -> "سنوي"
                else -> "تقويم مخصص"
            }
            val customType = when (customPeriodType) {
                "يوم" -> "date"
                "شهر" -> "month"
                "سنة" -> "year"
                else -> "date"
            }

            val filteredInvoices = invoices.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
            val filteredExpenses = expenses.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
            val filteredPurchases = purchases.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
            val filteredTaxHistory = taxHistory.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
            val filteredTransfers = transfers.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
            val filteredPayments = allPayments.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }

            // Calculations for YER
            val totalSalesYer = filteredInvoices.sumOf { it.totalAmountYer }
            val totalExpensesYer = filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
            val totalPurchasesYer = filteredPurchases.sumOf { it.totalAmountYer }
            val totalTaxAmountYer = filteredTaxHistory.sumOf { it.taxAmount }
            val totalStallOutflowYer = filteredTaxHistory.sumOf { it.stallOutflow }
            val totalLaborOutflowYer = filteredTaxHistory.sumOf { it.laborOutflow }

            val totalCustomerDebtsYer = filteredInvoices.sumOf { it.debtAmountYer }
            val totalCustomerCollectionsYer = filteredPayments.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
            val totalSupplierDebtsYer = filteredPurchases.sumOf { it.debtRemainingYer }
            val totalSupplierPaymentsYer = filteredPurchases.sumOf { it.paidAmountYer }
            val totalTransfersYer = filteredTransfers.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }

            val netApproxProfitYer = totalSalesYer - totalPurchasesYer - totalTaxAmountYer - totalStallOutflowYer - totalLaborOutflowYer - totalExpensesYer

            // Calculations for SAR
            val totalSalesSar = filteredInvoices.sumOf { it.totalAmountSar }
            val totalExpensesSar = filteredExpenses.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
            val totalPurchasesSar = filteredPurchases.sumOf { it.totalAmountSar }

            val totalCustomerDebtsSar = filteredInvoices.sumOf { it.debtAmountSar }
            val totalCustomerCollectionsSar = filteredPayments.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
            val totalSupplierDebtsSar = filteredPurchases.sumOf { it.debtRemainingSar }
            val totalSupplierPaymentsSar = filteredPurchases.sumOf { it.paidAmountSar }
            val totalTransfersSar = filteredTransfers.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }

            val netApproxProfitSar = totalSalesSar - totalPurchasesSar - totalExpensesSar

            val pLabel = when (selectedPeriod) {
                0 -> "اليومية"
                1 -> "الأسبوعية"
                2 -> "الشهرية"
                3 -> "السنوية"
                else -> "تقويم مخصص"
            }
            val pValDetail = when (selectedPeriod) {
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

            Text("ملخص أداء الفترة المختارة ($pLabel - $pValDetail)", color = PalWhitePure, fontWeight = FontWeight.Bold)

            val showYerSection = currencyConfig.allowsYer ||
                totalSalesYer != 0.0 || totalPurchasesYer != 0.0 || totalExpensesYer != 0.0 ||
                totalTaxAmountYer != 0.0 || totalStallOutflowYer != 0.0 || totalLaborOutflowYer != 0.0 ||
                totalCustomerDebtsYer != 0.0 || totalCustomerCollectionsYer != 0.0 ||
                totalSupplierDebtsYer != 0.0 || totalSupplierPaymentsYer != 0.0 || totalTransfersYer != 0.0
            val showSarSection = currencyConfig.allowsSar ||
                totalSalesSar != 0.0 || totalPurchasesSar != 0.0 || totalExpensesSar != 0.0 ||
                totalCustomerDebtsSar != 0.0 || totalCustomerCollectionsSar != 0.0 ||
                totalSupplierDebtsSar != 0.0 || totalSupplierPaymentsSar != 0.0 || totalTransfersSar != 0.0

            Card(
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                shape = RoundedCornerShape(14.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (showYerSection) {
                        val yerSuffix = if (!currencyConfig.allowsYer) " (سجل تاريخي)" else ""
                        Text("الحساب والربحية بالريال اليمني$yerSuffix", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المبيعات (+):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalSalesYer, "ريال يمني"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المشتريات (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalPurchasesYer, "ريال يمني")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي ضريبة القات (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalTaxAmountYer, "ريال يمني")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي خرج المفرش (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalStallOutflowYer, "ريال يمني")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي خرج العمال (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalLaborOutflowYer, "ريال يمني")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي مصروفات التشغيل (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalExpensesYer, "ريال يمني")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي ديون العملاء الآجلة الجديدة:", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalCustomerDebtsYer, "ريال يمني"), color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي تحصيلات وسداد العملاء (+):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalCustomerCollectionsYer, "ريال يمني"), color = PalSuccessNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي ديون الموردين الآجلة الجديدة:", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalSupplierDebtsYer, "ريال يمني"), color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الدفعات المسددة للموردين (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalSupplierPaymentsYer, "ريال يمني")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الحوالات الصادرة والواردة:", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalTransfersYer, "ريال يمني"), color = PalWhitePure, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val isProfitYer = netApproxProfitYer >= 0
                            val labelYer = if (isProfitYer) "صافي الربح بالريال اليمني:" else "صافي الخسارة بالريال اليمني:"
                            val valueColorYer = if (isProfitYer) PalSuccessNormal else PalRedNormal
                            Text(labelYer, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(Helpers.formatWithCurrency(netApproxProfitYer, "ريال يمني"), color = valueColorYer, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    if (showYerSection && showSarSection) {
                        HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 4.dp))
                    }

                    if (showSarSection) {
                        val sarSuffix = if (!currencyConfig.allowsSar) " (سجل تاريخي)" else ""
                        Text("الحساب والربحية بالريال السعودي$sarSuffix", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المبيعات (+):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalSalesSar, "ريال سعودي"), color = PalSuccessNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي المشتريات (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalPurchasesSar, "ريال سعودي")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي مصروفات التشغيل (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalExpensesSar, "ريال سعودي")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي ديون العملاء الآجلة الجديدة:", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalCustomerDebtsSar, "ريال سعودي"), color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي تحصيلات وسداد العملاء (+):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalCustomerCollectionsSar, "ريال سعودي"), color = PalSuccessNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي ديون الموردين الآجلة الجديدة:", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalSupplierDebtsSar, "ريال سعودي"), color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الدفعات المسددة للموردين (-):", color = PalWhiteSoft, fontSize = 12.sp)
                            Text("- ${Helpers.formatWithCurrency(totalSupplierPaymentsSar, "ريال سعودي")}", color = PalRedNormal, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("إجمالي الحوالات الصادرة والواردة:", color = PalWhiteSoft, fontSize = 12.sp)
                            Text(Helpers.formatWithCurrency(totalTransfersSar, "ريال سعودي"), color = PalWhitePure, fontSize = 12.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            val isProfitSar = netApproxProfitSar >= 0
                            val labelSar = if (isProfitSar) "صافي الربح بالريال السعودي:" else "صافي الخسارة بالريال السعودي:"
                            val valueColorSar = if (isProfitSar) PalSuccessNormal else PalRedNormal
                            Text(labelSar, color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(Helpers.formatWithCurrency(netApproxProfitSar, "ريال سعودي"), color = valueColorSar, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            OutlinedTextField(
                value = accountingReportPrintDate,
                onValueChange = { accountingReportPrintDate = it },
                label = { Text("تعيين وتخصيص تاريخ التقرير المطبوع", color = PalWhiteMuted, fontSize = 11.sp) },
                modifier = Modifier.fillMaxWidth().testTag("accounts_report_date_input"),
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

            Button(
                onClick = {
                    val headers = mutableListOf("البند والبيان المحاسبي")
                    if (showYerSection) headers.add("المبلغ بالريال اليمني")
                    if (showSarSection) headers.add("المبلغ بالريال السعودي")

                    fun rowFor(label: String, yerVal: String, sarVal: String): List<String> {
                        val r = mutableListOf(label)
                        if (showYerSection) r.add(yerVal)
                        if (showSarSection) r.add(sarVal)
                        return r
                    }

                    val rows = listOf(
                        rowFor("إجمالي المبيعات المحققة (+)", Helpers.formatWithCurrency(totalSalesYer, "ريال يمني"), Helpers.formatWithCurrency(totalSalesSar, "ريال سعودي")),
                        rowFor("إجمالي المشتريات والمخازن (-)", Helpers.formatWithCurrency(totalPurchasesYer, "ريال يمني"), Helpers.formatWithCurrency(totalPurchasesSar, "ريال سعودي")),
                        rowFor("ضرائب القات المسددة (-)", Helpers.formatWithCurrency(totalTaxAmountYer, "ريال يمني"), "0.00 ريال سعودي"),
                        rowFor("خرج المفرش المعتمد (-)", Helpers.formatWithCurrency(totalStallOutflowYer, "ريال يمني"), "0.00 ريال سعودي"),
                        rowFor("خرج العمال اليومي المعتمد (-)", Helpers.formatWithCurrency(totalLaborOutflowYer, "ريال يمني"), "0.00 ريال سعودي"),
                        rowFor("إجمالي المصاريف والتشغيل (-)", Helpers.formatWithCurrency(totalExpensesYer, "ريال يمني"), Helpers.formatWithCurrency(totalExpensesSar, "ريال سعودي")),
                        rowFor("إجمالي ديون العملاء الجديدة", Helpers.formatWithCurrency(totalCustomerDebtsYer, "ريال يمني"), Helpers.formatWithCurrency(totalCustomerDebtsSar, "ريال سعودي")),
                        rowFor("إجمالي سداد وتحصيل الديون (+)", Helpers.formatWithCurrency(totalCustomerCollectionsYer, "ريال يمني"), Helpers.formatWithCurrency(totalCustomerCollectionsSar, "ريال سعودي")),
                        rowFor("إجمالي ديون الموردين الجديدة", Helpers.formatWithCurrency(totalSupplierDebtsYer, "ريال يمني"), Helpers.formatWithCurrency(totalSupplierDebtsSar, "ريال سعودي")),
                        rowFor("إجمالي الدفعات المسددة للموردين (-)", Helpers.formatWithCurrency(totalSupplierPaymentsYer, "ريال يمني"), Helpers.formatWithCurrency(totalSupplierPaymentsSar, "ريال سعودي")),
                        rowFor("إجمالي الحوالات الصادرة/الواردة", Helpers.formatWithCurrency(totalTransfersYer, "ريال يمني"), Helpers.formatWithCurrency(totalTransfersSar, "ريال سعودي"))
                    )
                    val totalsMap = linkedMapOf<String, String>()
                    if (showYerSection) {
                        totalsMap[if (netApproxProfitYer >= 0) "صافي الربح بالريال اليمني" else "صافي الخسارة بالريال اليمني"] =
                            Helpers.formatWithCurrency(netApproxProfitYer, "ريال يمني")
                    }
                    if (showSarSection) {
                        totalsMap[if (netApproxProfitSar >= 0) "صافي الربح بالريال السعودي" else "صافي الخسارة بالريال السعودي"] =
                            Helpers.formatWithCurrency(netApproxProfitSar, "ريال سعودي")
                    }
                    val bAgencyName = com.example.util.BrandingManager.resolvedAgencyName()
                    Helpers.generatePdfAndShare(
                        context = context,
                        title = "التقرير المحاسبي العام ($pLabel) - $bAgencyName",
                        headers = headers,
                        rows = rows,
                        totals = totalsMap,
                        customDate = accountingReportPrintDate,
                        reportPeriod = if (selectedPeriod == 4) "$customPeriodType : $customPeriodValue" else pValDetail
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            ) {
                Text("تصدير التقرير المحاسبي PDF", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

sealed class ArchiveItem {
    abstract val id: Int
    abstract val date: String
    abstract val amount: Double
    abstract val typeArabic: String

    data class Invoice(val data: com.example.data.SalesInvoice) : ArchiveItem() {
        override val id = data.id
        override val date = data.date
        override val amount = data.totalAmount
        override val typeArabic = "مبيعات"
    }

    data class Purchase(val data: com.example.data.SupplierPurchase, val supplierName: String) : ArchiveItem() {
        override val id = data.id
        override val date = data.date
        override val amount = data.totalAmount
        override val typeArabic = "شراء"
    }

    data class GeneralExpense(val data: com.example.data.Expense) : ArchiveItem() {
        override val id = data.id
        override val date = data.date
        override val amount = data.amount
        override val typeArabic = "مصروف"
    }

    data class Transfer(val data: com.example.data.MoneyTransfer) : ArchiveItem() {
        override val id = data.id
        override val date = data.date
        override val amount = data.amount
        override val typeArabic = "حوالة"
    }

    data class CustomerDebt(val data: com.example.data.SalesInvoice) : ArchiveItem() {
        override val id = -data.id
        override val date = data.date
        override val amount = data.debtAmount
        override val typeArabic = "دين عميل"
    }

    data class SupplierDebt(val data: com.example.data.SupplierPurchase, val supplierName: String) : ArchiveItem() {
        override val id = -data.id - 100000
        override val date = data.date
        override val amount = data.debtRemaining
        override val typeArabic = "دين مورد"
    }

    data class Collection(val data: com.example.data.CustomerPayment, val customerName: String) : ArchiveItem() {
        override val id = data.id + 200000
        override val date = data.date
        override val amount = data.amount
        override val typeArabic = "تحصيل"
    }
}

data class ArchiveUIModel(
    val title: String,
    val sub: String,
    val valStr: String,
    val colorVal: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val details: List<Pair<String, String>> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArchiveScreen(viewModel: AppViewModel) {
    val licenseState by viewModel.licenseState.collectAsState()
    if (!licenseState.hasFeature(LicenseFeature.ARCHIVE)) {
        ProLockedScreenPlaceholder(feature = LicenseFeature.ARCHIVE, viewModel = viewModel)
        return
    }
    val context = LocalContext.current
    
    val invoices by viewModel.allInvoices.collectAsState()
    val purchases by viewModel.allPurchases.collectAsState()
    val suppliers by viewModel.allSuppliers.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val transfers by viewModel.allTransfers.collectAsState()
    val payments by viewModel.allPayments.collectAsState()
    val customers by viewModel.allCustomers.collectAsState()
    val currencyConfig by viewModel.currencyConfig.collectAsState()

    var searchStr by remember { mutableStateOf("") }
    
    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("date") }

    var showShareArchiveDialog by remember { mutableStateOf(false) }
    var selectedSharePeriod by remember(selectedPeriod) { mutableStateOf(selectedPeriod) } // الكل, يومي, شهري, سنوي, تقويم مخصص
    var selectedShareCategory by remember { mutableStateOf("الكل") } // الكل, المبيعات, المشتريات, المصروفات, الحوالات, الديون, التحصيلات
    var customShareType by remember(customPeriodType) { mutableStateOf(customPeriodType) }
    var customShareValue by remember(customPeriodValue) { mutableStateOf(customPeriodValue) }
    var customSharePrintDate by remember(customPeriodValue) { mutableStateOf(customPeriodValue.ifEmpty { com.example.util.Helpers.getCurrentDate() }) }
    
    var showBluetoothPrintTrigger by remember { mutableStateOf(false) }
    var bluetoothPrintText by remember { mutableStateOf("") }
    var selectedCategoryIndex by remember { mutableStateOf(0) } // 0: الكل, 1: مبيعات, 2: مشتريات, 3: مصروفات, 4: حوالات, 5: ديون, 6: تحصيلات
    val categories = listOf("الكل", "المبيعات", "المشتريات", "المصروفات", "الحوالات", "الديون", "التحصيلات")

    // Compile into single list of ArchiveItems
    val allItems = remember(invoices, purchases, suppliers, expenses, transfers, payments, customers) {
        val list = mutableListOf<ArchiveItem>()
        invoices.forEach { list.add(ArchiveItem.Invoice(it)) }
        purchases.forEach { p ->
            val supName = suppliers.find { it.id == p.supplierId }?.name ?: "مورد غير معروف"
            list.add(ArchiveItem.Purchase(p, supName))
        }
        expenses.forEach { list.add(ArchiveItem.GeneralExpense(it)) }
        transfers.forEach { list.add(ArchiveItem.Transfer(it)) }

        // Add Debts (الديون)
        invoices.filter { it.debtAmount > 0 }.forEach { list.add(ArchiveItem.CustomerDebt(it)) }
        purchases.filter { it.debtRemaining > 0 }.forEach { p ->
            val supName = suppliers.find { it.id == p.supplierId }?.name ?: "مورد غير معروف"
            list.add(ArchiveItem.SupplierDebt(p, supName))
        }

        // Add Collections (التحصيلات)
        payments.forEach { pay ->
            val custName = customers.find { it.id == pay.customerId }?.name ?: "عميل غير معروف"
            list.add(ArchiveItem.Collection(pay, custName))
        }

        // Sort by date ascending
        list.sortBy { it.date }
        list
    }

    // Filter by period, type & search text
    val filtered = remember(allItems, searchStr, selectedCategoryIndex, selectedPeriod, customPeriodType, customPeriodValue) {
        val periodFiltered = allItems.filter { Helpers.matchesPeriod(it.date, selectedPeriod, customPeriodValue, customPeriodType) }

        periodFiltered.filter { item ->
            // Category filter
            val matchesCategory = when (selectedCategoryIndex) {
                0 -> true
                1 -> item is ArchiveItem.Invoice
                2 -> item is ArchiveItem.Purchase
                3 -> item is ArchiveItem.GeneralExpense
                4 -> item is ArchiveItem.Transfer
                5 -> item is ArchiveItem.CustomerDebt || item is ArchiveItem.SupplierDebt
                6 -> item is ArchiveItem.Collection
                else -> true
            }
            if (!matchesCategory) return@filter false

            // Search filter
            if (searchStr.trim().isEmpty()) return@filter true
            
            val query = searchStr.trim().lowercase()
            val matchesSearch = when (item) {
                is ArchiveItem.Invoice -> {
                    item.data.customerName.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.id.toString().contains(query) ||
                            item.data.totalAmount.toString().contains(query)
                }
                is ArchiveItem.Purchase -> {
                    item.supplierName.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.id.toString().contains(query) ||
                            item.data.totalAmount.toString().contains(query)
                }
                is ArchiveItem.GeneralExpense -> {
                    item.data.category.lowercase().contains(query) ||
                            item.data.notes.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.amount.toString().contains(query)
                }
                is ArchiveItem.Transfer -> {
                    item.data.sender.lowercase().contains(query) ||
                            item.data.receiver.lowercase().contains(query) ||
                            item.data.notes.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.amount.toString().contains(query)
                }
                is ArchiveItem.CustomerDebt -> {
                    item.data.customerName.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.id.toString().contains(query) ||
                            item.data.debtAmount.toString().contains(query)
                }
                is ArchiveItem.SupplierDebt -> {
                    item.supplierName.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.id.toString().contains(query) ||
                            item.data.debtRemaining.toString().contains(query)
                }
                is ArchiveItem.Collection -> {
                    item.customerName.lowercase().contains(query) ||
                            item.data.notes.lowercase().contains(query) ||
                            item.data.date.contains(query) ||
                            item.data.amount.toString().contains(query)
                }
            }
            matchesSearch
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { AgencyAppBarTitle("أرشيف ومحفوظات السجلات الكلي") },
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
                            onClick = { showShareArchiveDialog = true },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "تصدير الأرشيف", tint = PalIconUnified)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor)
            }
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Search field
            OutlinedTextField(
                value = searchStr,
                onValueChange = { searchStr = it },
                placeholder = { Text("البحث في الأرشيف (بالاسم، التاريخ، المبلغ، الملاحظات)...", color = PalWhiteMuted, fontSize = 12.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = PalIconUnified) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PalGreenNormal,
                    unfocusedBorderColor = PalBorderColor,
                    focusedTextColor = PalWhitePure,
                    unfocusedTextColor = PalWhitePure,
                    focusedContainerColor = PalBlackNormal,
                    unfocusedContainerColor = PalBlackNormal
                ),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Category filters horizontal scrollable list
            androidx.compose.foundation.lazy.LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(categories) { index, catName ->
                    val isSelected = selectedCategoryIndex == index
                    AssistChip(
                        onClick = { selectedCategoryIndex = index },
                        label = { Text(catName, color = if (isSelected) Color.White else PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) PalGreenNormal else PalBlackNormal
                        ),
                        border = BorderStroke(1.dp, if (isSelected) PalGreenNormal else PalBorderColor)
                    )
                }
            }

            if (filtered.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("لا توجد فواتير أو حركات مؤرشفة مطابقة لعملية البحث", color = PalWhiteMuted, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filtered) { item ->
                        val uiModel = when (item) {
                            is ArchiveItem.Invoice -> {
                                val cName = item.data.customerName.ifEmpty { "عميل نقدي" }
                                val formattedVal = when {
                                    item.data.totalAmountYer > 0.0 && item.data.totalAmountSar > 0.0 -> {
                                        "+ ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                    }
                                    item.data.totalAmountYer > 0.0 -> {
                                        "+ ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")}"
                                    }
                                    item.data.totalAmountSar > 0.0 -> {
                                        "+ ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                    }
                                    else -> {
                                        "+ ${com.example.util.Helpers.formatWithCurrency(item.amount, item.data.currency)}"
                                    }
                                }
                                ArchiveUIModel(
                                    title = "فاتورة مبيعات للعميل: $cName",
                                    sub = "رقم الفاتورة: #${item.data.id} • طريقة الدفع: ${item.data.paymentMethod} • التاريخ: ${item.data.date}",
                                    valStr = formattedVal,
                                    colorVal = PalSuccessNormal,
                                    icon = Icons.Default.ReceiptLong,
                                    details = listOf(
                                        "رقم الفاتورة" to "#${item.data.id}",
                                        "طريقة الدفع" to item.data.paymentMethod,
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                            is ArchiveItem.Purchase -> {
                                val formattedVal = when {
                                    item.data.totalAmountYer > 0.0 && item.data.totalAmountSar > 0.0 -> {
                                        "- [${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}]"
                                    }
                                    item.data.totalAmountYer > 0.0 -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")}"
                                    }
                                    item.data.totalAmountSar > 0.0 -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                    }
                                    else -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.amount, item.data.currency)}"
                                    }
                                }
                                ArchiveUIModel(
                                    title = "سند شراء بضاعة من المورد: ${item.supplierName}",
                                    sub = "رقم التوريد: #${item.data.id} • طريقة الدفع: ${item.data.paymentMethod} • التاريخ: ${item.data.date}",
                                    valStr = formattedVal,
                                    colorVal = PalRedNormal,
                                    icon = Icons.Default.ShoppingCart,
                                    details = listOf(
                                        "رقم التوريد" to "#${item.data.id}",
                                        "طريقة الدفع" to item.data.paymentMethod,
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                            is ArchiveItem.GeneralExpense -> {
                                ArchiveUIModel(
                                    title = "مصروف عام: ${item.data.category}",
                                    sub = "البيان: ${item.data.notes.ifEmpty { "لا يوجد" }} • التاريخ: ${item.data.date}",
                                    valStr = "- ${com.example.util.Helpers.formatWithCurrency(item.data.amount, item.data.currency)}",
                                    colorVal = PalRedNormal,
                                    icon = Icons.Default.TrendingDown,
                                    details = listOf(
                                        "الفئة" to item.data.category,
                                        "البيان" to item.data.notes.ifEmpty { "لا يوجد" },
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                            is ArchiveItem.Transfer -> {
                                ArchiveUIModel(
                                    title = "حوالة مالية: من (${item.data.sender}) إلى (${item.data.receiver})",
                                    sub = "رقم المعاملة: ${item.data.referenceNumber.ifEmpty { "غير محدد" }} • البيان: ${item.data.notes} • التاريخ: ${item.data.date}",
                                    valStr = com.example.util.Helpers.formatWithCurrency(item.data.amount, item.data.currency),
                                    colorVal = PalWhitePure,
                                    icon = Icons.Default.CompareArrows,
                                    details = listOf(
                                        "من" to item.data.sender,
                                        "إلى" to item.data.receiver,
                                        "رقم المعاملة" to item.data.referenceNumber.ifEmpty { "لا يوجد" },
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                            is ArchiveItem.CustomerDebt -> {
                                val cName = item.data.customerName.ifEmpty { "عميل" }
                                val formattedVal = when {
                                    item.data.debtAmountYer > 0.0 && item.data.debtAmountSar > 0.0 -> {
                                        "- [${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountSar, "ريال سعودي")}]"
                                    }
                                    item.data.debtAmountYer > 0.0 -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountYer, "ريال يمني")}"
                                    }
                                    item.data.debtAmountSar > 0.0 -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountSar, "ريال سعودي")}"
                                    }
                                    else -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.amount, item.data.currency)}"
                                    }
                                }
                                ArchiveUIModel(
                                    title = "دين آجل على العميل: $cName",
                                    sub = "رقم الفاتورة المرجعية: #${item.data.id} • التاريخ: ${item.data.date}",
                                    valStr = formattedVal,
                                    colorVal = PalRedNormal,
                                    icon = Icons.Default.TrendingDown,
                                    details = listOf(
                                        "العميل" to cName,
                                        "الفاتورة المرجعية" to "#${item.data.id}",
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                            is ArchiveItem.SupplierDebt -> {
                                val formattedVal = when {
                                    item.data.debtRemainingYer > 0.0 && item.data.debtRemainingSar > 0.0 -> {
                                        "- [${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingSar, "ريال سعودي")}]"
                                    }
                                    item.data.debtRemainingYer > 0.0 -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingYer, "ريال يمني")}"
                                    }
                                    item.data.debtRemainingSar > 0.0 -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingSar, "ريال سعودي")}"
                                    }
                                    else -> {
                                        "- ${com.example.util.Helpers.formatWithCurrency(item.amount, item.data.currency)}"
                                    }
                                }
                                ArchiveUIModel(
                                    title = "دين آجل للمورد: ${item.supplierName}",
                                    sub = "رقم فاتورة الشراء: #${item.data.id} • التاريخ: ${item.data.date}",
                                    valStr = formattedVal,
                                    colorVal = PalRedNormal,
                                    icon = Icons.Default.TrendingDown,
                                    details = listOf(
                                        "المورد" to item.supplierName,
                                        "سند المشتريات مـرجع" to "#${item.data.id}",
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                            is ArchiveItem.Collection -> {
                                ArchiveUIModel(
                                    title = "تحصيل دفعة مالية من العميل: ${item.customerName}",
                                    sub = "رقم السند: #${item.data.id} • البيان: ${item.data.notes} • التاريخ: ${item.data.date}",
                                    valStr = "+ ${com.example.util.Helpers.formatWithCurrency(item.data.amount, item.data.currency)}",
                                    colorVal = PalSuccessNormal,
                                    icon = Icons.Default.Payments,
                                    details = listOf(
                                        "العميل" to item.customerName,
                                        "رقم السند" to "#${item.data.id}",
                                        "البيان" to item.data.notes.ifEmpty { "سداد حساب" },
                                        "التاريخ" to item.data.date
                                    )
                                )
                            }
                        }

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp)
                            ) {
                                // Row 1: Icon & Operation Name (Taking full remaining horizontal width)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(PalSoftGreenTint),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(uiModel.icon, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                    }
                                    Text(
                                        text = uiModel.title,
                                        color = PalWhitePure,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Row 2: Value Row (Clearly laid out below the title)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "المبلغ الصافي:",
                                        color = PalWhiteMuted,
                                        fontSize = 11.sp
                                    )
                                    Text(
                                        text = uiModel.valStr,
                                        color = uiModel.colorVal,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 14.sp
                                    )
                                }
                                
                                if (uiModel.details.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        uiModel.details.forEach { detail ->
                                            Row(
                                                modifier = Modifier
                                                    .background(PalBlackDark, RoundedCornerShape(6.dp))
                                                    .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 5.dp),
                                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = detail.first + ":", color = PalWhiteMuted, fontSize = 10.sp)
                                                Text(text = detail.second, color = PalWhiteSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }
                                } else if (uiModel.sub.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = uiModel.sub,
                                        color = PalWhiteMuted,
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

    if (showShareArchiveDialog) {
        val today = com.example.util.Helpers.getCurrentDate()
        
        // 1. First, compute period-filtered items (independently of category filter)
        val periodFilteredItems = remember(allItems, selectedSharePeriod, customShareValue, customShareType) {
            val list = allItems.toList()
            list.filter { Helpers.matchesPeriod(it.date, selectedSharePeriod, customShareValue, customShareType) }
                .sortedByDescending { it.date }
        }

        // 2. Compute sortedItems (category-filtered) for display/export details
        val sortedItems = remember(periodFilteredItems, selectedShareCategory) {
            val list = when (selectedShareCategory) {
                "المبيعات" -> periodFilteredItems.filter { it is ArchiveItem.Invoice }
                "المشتريات" -> periodFilteredItems.filter { it is ArchiveItem.Purchase }
                "المصروفات" -> periodFilteredItems.filter { it is ArchiveItem.GeneralExpense }
                "الحوالات" -> periodFilteredItems.filter { it is ArchiveItem.Transfer }
                "الديون" -> periodFilteredItems.filter { it is ArchiveItem.CustomerDebt || it is ArchiveItem.SupplierDebt }
                "التحصيلات" -> periodFilteredItems.filter { it is ArchiveItem.Collection }
                else -> periodFilteredItems
            }
            list.sortedByDescending { it.date }
        }

        // Calculations for totals BASED ON period-filtered items to ensure absolute correctness and completeness!
        val totalIn = periodFilteredItems.filterIsInstance<ArchiveItem.Invoice>().sumOf { it.amount }
        val totalOutBuy = periodFilteredItems.filterIsInstance<ArchiveItem.Purchase>().sumOf { it.amount }
        val totalOutExp = periodFilteredItems.filterIsInstance<ArchiveItem.GeneralExpense>().sumOf { it.amount }
        val totalTransfers = periodFilteredItems.filterIsInstance<ArchiveItem.Transfer>().sumOf { it.amount }
        val totalCustomerDebts = periodFilteredItems.filterIsInstance<ArchiveItem.CustomerDebt>().sumOf { it.amount }
        val totalSupplierDebts = periodFilteredItems.filterIsInstance<ArchiveItem.SupplierDebt>().sumOf { it.amount }
        val totalCollections = periodFilteredItems.filterIsInstance<ArchiveItem.Collection>().sumOf { it.amount }

        val totalInYer = periodFilteredItems.filterIsInstance<ArchiveItem.Invoice>().sumOf {
            if (it.data.totalAmountYer > 0.0) it.data.totalAmountYer else if (it.data.currency.contains("يمني")) it.amount else 0.0
        }
        val totalInSar = periodFilteredItems.filterIsInstance<ArchiveItem.Invoice>().sumOf {
            if (it.data.totalAmountSar > 0.0) it.data.totalAmountSar else if (it.data.currency.contains("سعودي")) it.amount else 0.0
        }

        val totalOutBuyYer = periodFilteredItems.filterIsInstance<ArchiveItem.Purchase>().sumOf {
            if (it.data.totalAmountYer > 0.0) it.data.totalAmountYer else if (it.data.currency.contains("يمني")) it.amount else 0.0
        }
        val totalOutBuySar = periodFilteredItems.filterIsInstance<ArchiveItem.Purchase>().sumOf {
            if (it.data.totalAmountSar > 0.0) it.data.totalAmountSar else if (it.data.currency.contains("سعودي")) it.amount else 0.0
        }

        val totalOutExpYer = periodFilteredItems.filterIsInstance<ArchiveItem.GeneralExpense>().filter { it.data.currency.contains("يمني") }.sumOf { it.amount }
        val totalOutExpSar = periodFilteredItems.filterIsInstance<ArchiveItem.GeneralExpense>().filter { it.data.currency.contains("سعودي") }.sumOf { it.amount }

        val totalTransfersYer = periodFilteredItems.filterIsInstance<ArchiveItem.Transfer>().filter { it.data.currency.contains("يمني") }.sumOf { it.amount }
        val totalTransfersSar = periodFilteredItems.filterIsInstance<ArchiveItem.Transfer>().filter { it.data.currency.contains("سعودي") }.sumOf { it.amount }

        val totalCustomerDebtsYer = periodFilteredItems.filterIsInstance<ArchiveItem.CustomerDebt>().sumOf {
            if (it.data.debtAmountYer > 0.0) it.data.debtAmountYer else if (it.data.currency.contains("يمني")) it.amount else 0.0
        }
        val totalCustomerDebtsSar = periodFilteredItems.filterIsInstance<ArchiveItem.CustomerDebt>().sumOf {
            if (it.data.debtAmountSar > 0.0) it.data.debtAmountSar else if (it.data.currency.contains("سعودي")) it.amount else 0.0
        }

        val totalSupplierDebtsYer = periodFilteredItems.filterIsInstance<ArchiveItem.SupplierDebt>().sumOf {
            if (it.data.debtRemainingYer > 0.0) it.data.debtRemainingYer else if (it.data.currency.contains("يمني")) it.amount else 0.0
        }
        val totalSupplierDebtsSar = periodFilteredItems.filterIsInstance<ArchiveItem.SupplierDebt>().sumOf {
            if (it.data.debtRemainingSar > 0.0) it.data.debtRemainingSar else if (it.data.currency.contains("سعودي")) it.amount else 0.0
        }

        val totalCollectionsYer = periodFilteredItems.filterIsInstance<ArchiveItem.Collection>().filter { it.data.currency.contains("يمني") }.sumOf { it.amount }
        val totalCollectionsSar = periodFilteredItems.filterIsInstance<ArchiveItem.Collection>().filter { it.data.currency.contains("سعودي") }.sumOf { it.amount }

        AlertDialog(
            onDismissRequest = { showShareArchiveDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            title = {
                Text(
                    text = "مشاركة وتصدير الأرشيف الكلي",
                    color = PalWhitePure,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
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
                        text = "فلترة وتصدير الأرشيف لمشاركتها عبر التقارير التفصيلية أو الواتساب أو الطباعة الحرارية المباشرة:",
                        color = PalWhiteMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(color = PalBorderColor)

                    // Period choices
                    Text("اختر المدى الزمني للتقرير بالأرشيف:", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    val sharePeriods = listOf("الكل", "يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            sharePeriods.take(3).forEach { period ->
                                val isSel = selectedSharePeriod == period
                                val bg = if (isSel) PalGreenNormal else PalBlackDark
                                val fg = if (isSel) Color.White else PalWhiteMuted
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedSharePeriod = period
                                            if (period == "أسبوعي" && !customShareValue.startsWith("WEEK_FILTER|")) {
                                                customShareValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (period) {
                                            "الكل" -> "كامل السجلات"
                                            "يومي" -> "اليوم"
                                            "أسبوعي" -> "أسبوعي"
                                            else -> period
                                        },
                                        color = fg,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            sharePeriods.drop(3).forEach { period ->
                                val isSel = selectedSharePeriod == period
                                val bg = if (isSel) PalGreenNormal else PalBlackDark
                                val fg = if (isSel) Color.White else PalWhiteMuted
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                        .clickable {
                                            selectedSharePeriod = period
                                            if (period == "أسبوعي" && !customShareValue.startsWith("WEEK_FILTER|")) {
                                                customShareValue = "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                                            }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (period) {
                                            "شهري" -> "الشهر"
                                            "سنوي" -> "العام"
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
                    }

                    if (selectedSharePeriod == "تقويم مخصص" || selectedSharePeriod == "أسبوعي") {
                        UnifiedPeriodSelector(
                            initialType = if (selectedSharePeriod == "أسبوعي") "أسبوع" else (if (customShareType == "date") "يوم" else if (customShareType == "month") "شهر" else "سنة"),
                            initialValue = if (selectedSharePeriod == "أسبوعي" && !customShareValue.startsWith("WEEK_FILTER|")) {
                                "WEEK_FILTER|${Helpers.getCurrentDate().substring(0, 7)}|${Helpers.getCurrentWeekOfMonth()}"
                            } else {
                                customShareValue.ifEmpty { com.example.util.Helpers.getCurrentDate() }
                            },
                            onPeriodChanged = { type, formattedValue ->
                                customShareType = when (type) {
                                    "يوم" -> "date"
                                    "شهر" -> "month"
                                    "سنة" -> "year"
                                    else -> "date"
                                }
                                customShareValue = formattedValue
                                customSharePrintDate = Helpers.formatCustomPeriodToArabic(type, formattedValue)
                            }
                        )
                        
                        OutlinedTextField(
                            value = customSharePrintDate,
                            onValueChange = { customSharePrintDate = it },
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

                    // Category choices
                    Text("اختر تجميع السجلات حسب الفئة:", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    val shareCategories = listOf("الكل", "المبيعات", "المشتريات", "المصروفات", "الحوالات", "الديون", "التحصيلات")
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            shareCategories.take(3).forEach { cat ->
                                val isSel = selectedShareCategory == cat
                                val bg = if (isSel) PalGreenNormal else PalBlackDark
                                val fg = if (isSel) Color.White else PalWhiteMuted
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedShareCategory = cat }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            shareCategories.slice(3..4).forEach { cat ->
                                val isSel = selectedShareCategory == cat
                                val bg = if (isSel) PalGreenNormal else PalBlackDark
                                val fg = if (isSel) Color.White else PalWhiteMuted
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedShareCategory = cat }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat, color = fg, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            shareCategories.drop(5).forEach { cat ->
                                val isSel = selectedShareCategory == cat
                                val bg = if (isSel) PalGreenNormal else PalBlackDark
                                val fg = if (isSel) Color.White else PalWhiteMuted
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(bg)
                                        .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                        .clickable { selectedShareCategory = cat }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(cat, color = fg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = PalBorderColor)

                    // Summary statistics
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("ملخص حركات التصدير المحددة:", color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "المبيعات") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المبيعات (يمني):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalInYer, "ريال يمني"), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المبيعات (سعودي):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalInSar, "ريال سعودي"), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "المشتريات") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي التزويد والمشتريات (يمني):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text("- " + com.example.util.Helpers.formatWithCurrency(totalOutBuyYer, "ريال يمني"), color = PalRedNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي التزويد والمشتريات (سعودي):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text("- " + com.example.util.Helpers.formatWithCurrency(totalOutBuySar, "ريال سعودي"), color = PalRedNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "المصروفات") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المصروفات (يمني):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text("- " + com.example.util.Helpers.formatWithCurrency(totalOutExpYer, "ريال يمني"), color = PalWhitePure, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي المصروفات (سعودي):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text("- " + com.example.util.Helpers.formatWithCurrency(totalOutExpSar, "ريال سعودي"), color = PalWhitePure, fontSize = 11.sp)
                            }
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "الحوالات") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي قيمة الحوالات (يمني):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalTransfersYer, "ريال يمني"), color = PalWhitePure, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي قيمة الحوالات (سعودي):", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalTransfersSar, "ريال سعودي"), color = PalWhitePure, fontSize = 11.sp)
                            }
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "الديون") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي الديون الآجلة للعملاء:", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalCustomerDebtsYer, "ريال يمني") + " / " + com.example.util.Helpers.formatWithCurrency(totalCustomerDebtsSar, "ريال سعودي"), color = PalRedNormal, fontSize = 11.sp)
                            }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي حركات آجل الموردين:", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalSupplierDebtsYer, "ريال يمني") + " / " + com.example.util.Helpers.formatWithCurrency(totalSupplierDebtsSar, "ريال سعودي"), color = PalRedNormal, fontSize = 11.sp)
                            }
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "التحصيلات") {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("إجمالي التحصيلات المستلمة:", color = PalWhiteSoft, fontSize = 11.sp)
                                Text(com.example.util.Helpers.formatWithCurrency(totalCollectionsYer, "ريال يمني") + " / " + com.example.util.Helpers.formatWithCurrency(totalCollectionsSar, "ريال سعودي"), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        HorizontalDivider(color = PalBorderColor)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("مجموع العمليات المفلترة:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${sortedItems.size} عملية", color = PalGoldCalligraphy, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    val shareMsgText = buildString {
                        appendLine(com.example.util.BrandingManager.buildShareHeader("تصدير الأرشيف ومحفوظات السجلات الموحد الكامل"))
                        val pStr = if (selectedSharePeriod == "تقويم مخصص") customSharePrintDate else selectedSharePeriod
                        appendLine("نوع المدى الزمني: $pStr")
                        appendLine("نوع السجلات: $selectedShareCategory")
                        appendLine("التاريخ والوقت: ${com.example.util.Helpers.getCurrentDateTime()}")
                        appendLine("=================================")
                        if (sortedItems.isEmpty()) {
                            appendLine("لا توجد عمليات مسجلة متطابقة مع شروط الفلترة لهذه الفترة.")
                        } else {
                            sortedItems.forEachIndexed { idx, item ->
                                val dateClean = item.date
                                val currency = when (item) {
                                    is ArchiveItem.Invoice -> item.data.currency
                                    is ArchiveItem.Purchase -> item.data.currency
                                    is ArchiveItem.GeneralExpense -> item.data.currency
                                    is ArchiveItem.Transfer -> item.data.currency
                                    is ArchiveItem.CustomerDebt -> item.data.currency
                                    is ArchiveItem.SupplierDebt -> item.data.currency
                                    is ArchiveItem.Collection -> item.data.currency
                                }
                                when (item) {
                                    is ArchiveItem.Invoice -> {
                                        val cName = item.data.customerName.ifEmpty { "عميل نقدي" }
                                        appendLine("${idx + 1}) [مبيعات] فاتورة بيع للعميل ($cName) #${item.data.id}")
                                        appendLine("   طريقة الدفع: ${item.data.paymentMethod} • التاريخ: $dateClean")
                                        val valStr = when {
                                            item.data.totalAmountYer > 0.0 && item.data.totalAmountSar > 0.0 -> {
                                                "${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                            }
                                            else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                        }
                                        appendLine("   المبلغ الصافي: + $valStr")
                                    }
                                    is ArchiveItem.Purchase -> {
                                        appendLine("${idx + 1}) [مشتريات] تزويد بضاعة من المورد (${item.supplierName}) #${item.data.id}")
                                        appendLine("   طريقة الدفع: ${item.data.paymentMethod} • التاريخ: $dateClean")
                                        val valStr = when {
                                            item.data.totalAmountYer > 0.0 && item.data.totalAmountSar > 0.0 -> {
                                                "${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                            }
                                            else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                        }
                                        appendLine("   المبلغ الصافي: - $valStr")
                                    }
                                    is ArchiveItem.GeneralExpense -> {
                                        appendLine("${idx + 1}) [مصروف] مصروف عام فئة: (${item.data.category})")
                                        appendLine("   ملاحظة: ${item.data.notes} • التاريخ: $dateClean")
                                        appendLine("   القيمة المخصومة: - ${com.example.util.Helpers.formatWithCurrency(item.data.amount, currency)}")
                                    }
                                    is ArchiveItem.Transfer -> {
                                        appendLine("${idx + 1}) [حوالة] حوالة من (${item.data.sender}) إلى (${item.data.receiver})")
                                        appendLine("   رقم المرجع: ${item.data.referenceNumber} • الملاحظة: ${item.data.notes}")
                                        appendLine("   الصافي: ${com.example.util.Helpers.formatWithCurrency(item.data.amount, currency)} • التاريخ: $dateClean")
                                    }
                                    is ArchiveItem.CustomerDebt -> {
                                        val cName = item.data.customerName.ifEmpty { "عميل" }
                                        appendLine("${idx + 1}) [دين عميل] دين آجل على العميل ($cName)")
                                        appendLine("   رقم الفاتورة: #${item.data.id} • التاريخ: $dateClean")
                                        val valStr = when {
                                            item.data.debtAmountYer > 0.0 && item.data.debtAmountSar > 0.0 -> {
                                                "${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountSar, "ريال سعودي")}"
                                            }
                                            else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                        }
                                        appendLine("   قيمة الدين: - $valStr")
                                    }
                                    is ArchiveItem.SupplierDebt -> {
                                        appendLine("${idx + 1}) [دين مورد] دين آجل للمورد (${item.supplierName})")
                                        appendLine("   رقم الفاتورة: #${item.data.id} • التاريخ: $dateClean")
                                        val valStr = when {
                                            item.data.debtRemainingYer > 0.0 && item.data.debtRemainingSar > 0.0 -> {
                                                "${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingSar, "ريال سعودي")}"
                                            }
                                            else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                        }
                                        appendLine("   قيمة الدين: - $valStr")
                                    }
                                    is ArchiveItem.Collection -> {
                                        appendLine("${idx + 1}) [تحصيل] دفعة مالية مستلمة من العميل (${item.customerName})")
                                        appendLine("   رقم السند: #${item.data.id} • البيان: ${item.data.notes} • التاريخ: $dateClean")
                                        appendLine("   المبلغ المستلم: + ${com.example.util.Helpers.formatWithCurrency(item.data.amount, currency)}")
                                    }
                                }
                                appendLine("   -------------------")
                            }
                        }
                        appendLine("=================================")
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "المبيعات") {
                            appendLine("إجمالي مبيعات (+):")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalInYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalInSar, "ريال سعودي")}")
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "المشتريات") {
                            appendLine("إجمالي مشتريات وتوريد (-):")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalOutBuyYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalOutBuySar, "ريال سعودي")}")
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "المصروفات") {
                            appendLine("إجمالي مصروفات تشغيلية (-):")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalOutExpYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalOutExpSar, "ريال سعودي")}")
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "الحوالات") {
                            appendLine("إجمالي قيمة الحوالات:")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalTransfersYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalTransfersSar, "ريال سعودي")}")
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "الديون") {
                            appendLine("إجمالي ديون العملاء:")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalCustomerDebtsYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalCustomerDebtsSar, "ريال سعودي")}")
                            appendLine("إجمالي ديون الموردين:")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalSupplierDebtsYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalSupplierDebtsSar, "ريال سعودي")}")
                        }
                        if (selectedShareCategory == "الكل" || selectedShareCategory == "التحصيلات") {
                            appendLine("إجمالي التحصيلات:")
                            appendLine("   - بالريال اليمني: ${com.example.util.Helpers.formatWithCurrency(totalCollectionsYer, "ريال يمني")}")
                            appendLine("   - بالريال السعودي: ${com.example.util.Helpers.formatWithCurrency(totalCollectionsSar, "ريال سعودي")}")
                        }
                        appendLine("=================================")
                        appendLine("إجمالي العمليات المتبقية: ${sortedItems.size} عملية")
                        appendLine(com.example.util.BrandingManager.buildShareFooter())
                    }

                    // Action buttons with spacing
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = {
                                val printPeriod = if (selectedSharePeriod == "تقويم مخصص") customSharePrintDate else selectedSharePeriod
                                val bAgencyName = com.example.util.BrandingManager.resolvedAgencyName()
                                val titlePdf = "تصدير تفصيلي للأرشيف - $bAgencyName ($printPeriod)"
                                val headers = listOf("نوع السند", "البيان والتفاصيل", "القيمة المالية", "التاريخ والوقت")
                                val pdfRows = sortedItems.map { item ->
                                    val currency = when (item) {
                                        is ArchiveItem.Invoice -> item.data.currency
                                        is ArchiveItem.Purchase -> item.data.currency
                                        is ArchiveItem.GeneralExpense -> item.data.currency
                                        is ArchiveItem.Transfer -> item.data.currency
                                        is ArchiveItem.CustomerDebt -> item.data.currency
                                        is ArchiveItem.SupplierDebt -> item.data.currency
                                        is ArchiveItem.Collection -> item.data.currency
                                    }
                                    val (typeStr, descStr, valueStr) = when (item) {
                                        is ArchiveItem.Invoice -> {
                                            val cName = item.data.customerName.ifEmpty { "عميل نقدي" }
                                            val valText = when {
                                                item.data.totalAmountYer > 0.0 && item.data.totalAmountSar > 0.0 -> {
                                                    "${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                                }
                                                else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                            }
                                            Triple("مبيعات", "فاتورة بيع للعميل: $cName (#${item.data.id})", "+ $valText")
                                        }
                                        is ArchiveItem.Purchase -> {
                                            val valText = when {
                                                item.data.totalAmountYer > 0.0 && item.data.totalAmountSar > 0.0 -> {
                                                    "${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.totalAmountSar, "ريال سعودي")}"
                                                }
                                                else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                            }
                                            Triple("مشتريات", "شراء وتوريد بضاعة: ${item.supplierName} (#${item.data.id})", "- $valText")
                                        }
                                        is ArchiveItem.GeneralExpense -> {
                                            Triple("مصروف", "مصروف عام: ${item.data.category} - ${item.data.notes}", "- ${com.example.util.Helpers.formatWithCurrency(item.data.amount, currency)}")
                                        }
                                        is ArchiveItem.Transfer -> {
                                            Triple("حوالة", "من ${item.data.sender} إلى ${item.data.receiver} - ${item.data.notes}", com.example.util.Helpers.formatWithCurrency(item.data.amount, currency))
                                        }
                                        is ArchiveItem.CustomerDebt -> {
                                            val cName = item.data.customerName.ifEmpty { "عميل" }
                                            val valText = when {
                                                item.data.debtAmountYer > 0.0 && item.data.debtAmountSar > 0.0 -> {
                                                    "${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.debtAmountSar, "ريال سعودي")}"
                                                }
                                                else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                            }
                                            Triple("دين عميل", "دين آجل على العميل: $cName (#${item.data.id})", "- $valText")
                                        }
                                        is ArchiveItem.SupplierDebt -> {
                                            val valText = when {
                                                item.data.debtRemainingYer > 0.0 && item.data.debtRemainingSar > 0.0 -> {
                                                    "${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingYer, "ريال يمني")} + ${com.example.util.Helpers.formatWithCurrency(item.data.debtRemainingSar, "ريال سعودي")}"
                                                }
                                                else -> com.example.util.Helpers.formatWithCurrency(item.amount, currency)
                                            }
                                            Triple("دين مورد", "دين آجل للمورد: ${item.supplierName} (#${item.data.id})", "- $valText")
                                        }
                                        is ArchiveItem.Collection -> {
                                            Triple("تحصيل", "تحصيل دفعة من العميل: ${item.customerName} (#${item.data.id}) - ${item.data.notes}", "+ ${com.example.util.Helpers.formatWithCurrency(item.data.amount, currency)}")
                                        }
                                    }
                                    listOf(typeStr, descStr, valueStr, item.date)
                                }
                                val pdfTotals = linkedMapOf<String, String>()
                                if (selectedShareCategory == "الكل" || selectedShareCategory == "المبيعات") {
                                    if (currencyConfig.allowsYer || totalInYer != 0.0) {
                                        pdfTotals["إجمالي مبيعات بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalInYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalInSar != 0.0) {
                                        pdfTotals["إجمالي مبيعات بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalInSar, "ريال سعودي")
                                    }
                                }
                                if (selectedShareCategory == "الكل" || selectedShareCategory == "المشتريات") {
                                    if (currencyConfig.allowsYer || totalOutBuyYer != 0.0) {
                                        pdfTotals["إجمالي مشتريات بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalOutBuyYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalOutBuySar != 0.0) {
                                        pdfTotals["إجمالي مشتريات بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalOutBuySar, "ريال سعودي")
                                    }
                                }
                                if (selectedShareCategory == "الكل" || selectedShareCategory == "المصروفات") {
                                    if (currencyConfig.allowsYer || totalOutExpYer != 0.0) {
                                        pdfTotals["إجمالي مصروفات بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalOutExpYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalOutExpSar != 0.0) {
                                        pdfTotals["إجمالي مصروفات بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalOutExpSar, "ريال سعودي")
                                    }
                                }
                                if (selectedShareCategory == "الكل" || selectedShareCategory == "الحوالات") {
                                    if (currencyConfig.allowsYer || totalTransfersYer != 0.0) {
                                        pdfTotals["إجمالي حوالات بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalTransfersYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalTransfersSar != 0.0) {
                                        pdfTotals["إجمالي حوالات بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalTransfersSar, "ريال سعودي")
                                    }
                                }
                                if (selectedShareCategory == "الكل" || selectedShareCategory == "الديون") {
                                    if (currencyConfig.allowsYer || totalCustomerDebtsYer != 0.0) {
                                        pdfTotals["إجمالي ديون العملاء بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalCustomerDebtsYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalCustomerDebtsSar != 0.0) {
                                        pdfTotals["إجمالي ديون العملاء بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalCustomerDebtsSar, "ريال سعودي")
                                    }
                                    if (currencyConfig.allowsYer || totalSupplierDebtsYer != 0.0) {
                                        pdfTotals["إجمالي ديون الموردين بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalSupplierDebtsYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalSupplierDebtsSar != 0.0) {
                                        pdfTotals["إجمالي ديون الموردين بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalSupplierDebtsSar, "ريال سعودي")
                                    }
                                }
                                if (selectedShareCategory == "الكل" || selectedShareCategory == "التحصيلات") {
                                    if (currencyConfig.allowsYer || totalCollectionsYer != 0.0) {
                                        pdfTotals["إجمالي التحصيلات بالريال اليمني"] = com.example.util.Helpers.formatWithCurrency(totalCollectionsYer, "ريال يمني")
                                    }
                                    if (currencyConfig.allowsSar || totalCollectionsSar != 0.0) {
                                        pdfTotals["إجمالي التحصيلات بالريال السعودي"] = com.example.util.Helpers.formatWithCurrency(totalCollectionsSar, "ريال سعودي")
                                    }
                                }
                                com.example.util.Helpers.generatePdfAndShare(
                                    context = context,
                                    title = titlePdf,
                                    headers = headers,
                                    rows = pdfRows,
                                    totals = pdfTotals,
                                    customDate = if (selectedSharePeriod == "تقويم مخصص") customSharePrintDate else com.example.util.Helpers.getCurrentDateTime(),
                                    reportPeriod = if (selectedSharePeriod == "تقويم مخصص") {
                                        val displayType = when (customShareType) {
                                            "date" -> "يوم"
                                            "month" -> "شهر"
                                            "year" -> "سنة"
                                            else -> customShareType
                                        }
                                        "$displayType : $customShareValue"
                                    } else {
                                        selectedSharePeriod
                                    }
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal, contentColor = Color.White),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1.2f).height(44.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
                        ) {
                            Text("تصدير PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = {
                                com.example.util.Helpers.shareViaWhatsApp(context, "", shareMsgText)
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
                                bluetoothPrintText = shareMsgText
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
                TextButton(onClick = { showShareArchiveDialog = false }) {
                    Text("إغلاق", color = PalWhiteMuted)
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel) {
    val context = LocalContext.current
    val printerManager = remember { BluetoothPrinterManager.getInstance(context) }
    
    val brandingAppName by viewModel.brandingAppName.collectAsState()
    val brandingAgencyName by viewModel.brandingAgencyName.collectAsState()
    val brandingOwnerName by viewModel.brandingOwnerName.collectAsState()
    val brandingPhone by viewModel.brandingPhone.collectAsState()
    val brandingAddress by viewModel.brandingAddress.collectAsState()
    val brandingDefaultCurrency by viewModel.brandingDefaultCurrency.collectAsState()
    val brandingLogoEnabled by viewModel.brandingLogoEnabled.collectAsState()
    val brandingQatTypes by viewModel.brandingQatTypes.collectAsState()

    var showPassDialog by remember { mutableStateOf(false) }
    var inputNewPass by remember { mutableStateOf("") }

    val printers = remember { printerManager.getBondedPrinters() }
    var selectedPrinterName by remember { mutableStateOf(printerManager.connectedDeviceName) }

    val appLockEnabled by viewModel.isAppLockEnabled.collectAsState()

    var localBackupsList by remember { mutableStateOf(emptyList<java.io.File>()) }
    var showBackupRestoreConfirmDialog by remember { mutableStateOf(false) }
    var showBackupDeleteConfirmDialog by remember { mutableStateOf(false) }
    var selectedBackupForAction by remember { mutableStateOf<java.io.File?>(null) }

    val refreshBackupsList = {
        val backupDir = java.io.File(context.filesDir, "qat_ledger_backups")
        if (!backupDir.exists()) {
            backupDir.mkdirs()
        }
        val legacyDir = java.io.File(context.filesDir, "tofan_backups")
        val currentFiles = backupDir.listFiles { f -> f.isFile && f.name.endsWith(".db") }?.toList().orEmpty()
        val legacyFiles = if (legacyDir.exists()) {
            legacyDir.listFiles { f -> f.isFile && f.name.endsWith(".db") }?.toList().orEmpty()
        } else {
            emptyList()
        }
        localBackupsList = (currentFiles + legacyFiles).sortedByDescending { f -> f.lastModified() }
    }

    LaunchedEffect(Unit) {
        refreshBackupsList()
    }

    var showLogoutConfirmDialog by remember { mutableStateOf(false) }

    var isGeneratingStressData by remember { mutableStateOf(false) }
    var isPurgingDatabase by remember { mutableStateOf(false) }
    var showStressTestingConfirmDialog by remember { mutableStateOf(false) }
    var showPurgeConfirmDialog by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "logo_anim_transition")
    val scaleAnim by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_scale"
    )
    val rotationAnim by infiniteTransition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_rotation"
    )
    val bounceOffsetYAnimFloat by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logo_bounce"
    )

    val filePickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val success = Helpers.restoreDatabase(context, uri)
            if (success) {
                android.widget.Toast.makeText(context, "تم استعادة قاعدة البيانات بنجاح! جارٍ إعادة تشغيل التطبيق لتحديث السجلات.", android.widget.Toast.LENGTH_LONG).show()
                val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
                context.startActivity(intent)
                (context as? android.app.Activity)?.finish()
                kotlin.system.exitProcess(0)
            } else {
                android.widget.Toast.makeText(context, "فشل استعادة قاعدة البيانات! تأكد من ملف النسخة الاحتياطية الصحيح.", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val createDocumentLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            try {
                com.example.data.AppDatabase.checkpoint()
                val dbFile = context.getDatabasePath("tofan_al_aqsa_qat_db")
                if (dbFile.exists()) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        dbFile.inputStream().use { input ->
                            input.copyTo(output)
                        }
                    }
                    android.widget.Toast.makeText(context, "تم تصدير قاعدة البيانات وحفظها بنجاح في ملفات الهاتف", android.widget.Toast.LENGTH_LONG).show()
                } else {
                    android.widget.Toast.makeText(context, "فشل: لم يتم العثور على قاعدة البيانات حالياً!", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.widget.Toast.makeText(context, "فشل تصدير قاعدة البيانات: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    val customBitmap = remember(brandingLogoEnabled, com.example.util.BrandingManager.logoUpdateTrigger) {
        if (brandingLogoEnabled) com.example.util.BrandingManager.getCustomLogoBitmap(context) else null
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { AgencyAppBarTitle("إعدادات وتخصيص النظام") },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.navigateBack() },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = PalIconUnified)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = PalBlackNormal)
                )
                HorizontalDivider(color = PalBorderColor)
            }
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
            
            // Product Identity & Agency Summary Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(112.dp)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.QatLedgerAppMark(
                            size = 96.dp,
                            cornerRadius = 22.dp,
                            modifier = Modifier
                                .graphicsLayer(
                                    scaleX = scaleAnim,
                                    scaleY = scaleAnim,
                                    rotationZ = rotationAnim
                                )
                                .offset(y = bounceOffsetYAnimFloat.dp)
                        )
                    }

                    Text(
                        text = "${com.example.util.BrandingManager.PRODUCT_NAME_AR} — ${com.example.util.BrandingManager.PRODUCT_NAME_EN}",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = com.example.util.BrandingManager.PRODUCT_TAGLINE,
                        color = PalGreenNormal,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(
                        color = PalBorderColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    Text(
                        text = "بيانات النسخة والوكالة المسجلة",
                        color = PalWhiteSoft,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "إصدار النظام:", color = PalWhiteMuted, fontSize = 11.5.sp)
                            Text(
                                text = "v3.5.0 • ${com.example.util.BrandingManager.PRODUCT_NAME_EN}",
                                color = PalGreenNormal,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "الوكالة المسجلة:", color = PalWhiteMuted, fontSize = 11.5.sp)
                            Text(
                                text = brandingAgencyName.ifBlank { "غير مسجلة — يرجى إكمال الإعداد" },
                                color = PalWhitePure,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "صاحب الوكالة:", color = PalWhiteMuted, fontSize = 11.5.sp)
                            Text(
                                text = brandingOwnerName.ifBlank { "غير محدد" },
                                color = PalWhitePure,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (brandingPhone.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "رقم الهاتف:", color = PalWhiteMuted, fontSize = 11.5.sp)
                                Text(
                                    text = brandingPhone,
                                    color = PalWhiteSoft,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        if (brandingAddress.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "العنوان:", color = PalWhiteMuted, fontSize = 11.5.sp)
                                Text(
                                    text = brandingAddress,
                                    color = PalWhiteSoft,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currencyConfigHeader by viewModel.currencyConfig.collectAsState()
                            Text(text = "العملات والافتراضية:", color = PalWhiteMuted, fontSize = 11.5.sp)
                            Text(
                                text = "${currencyConfigHeader.mode.labelAr} • الافتراضية: ${currencyConfigHeader.resolvedDefaultCurrency}",
                                color = PalWhiteSoft,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        if (brandingQatTypes.isNotBlank()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = "أصناف القات:", color = PalWhiteMuted, fontSize = 11.5.sp)
                                Text(
                                    text = brandingQatTypes,
                                    color = PalWhiteSoft,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    val licenseState by viewModel.licenseState.collectAsState()
                    HorizontalDivider(color = PalBorderColor, thickness = 1.dp, modifier = Modifier.padding(vertical = 2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "الباقة والترخيص:", color = PalWhiteMuted, fontSize = 11.5.sp)
                        TextButton(
                            onClick = { viewModel.openCustomerActivationDialog() },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.testTag("settings_header_license_btn")
                        ) {
                            Text(
                                text = "${licenseState.effectivePackage.code} • تفعيل / ترقية",
                                color = if (licenseState.isProActive) PalGreenNormal else PalWhitePure,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            var selectedCategoryIndex by remember { mutableStateOf(0) }
            var isDeveloperUnlocked by remember { mutableStateOf(false) }
            var showDevConsoleInSettings by remember { mutableStateOf(false) }
            val categories = listOf(
                "هوية الوكالة",
                "الأمان والترخيص",
                "العملات",
                "النسخ الاحتياطي",
                "الطباعة",
                "المظهر العام",
                "الصلاحيات"
            )

            LaunchedEffect(selectedCategoryIndex) {
                if (selectedCategoryIndex != 6) {
                    isDeveloperUnlocked = false
                }
            }

            ScrollableTabRow(
                selectedTabIndex = selectedCategoryIndex,
                containerColor = PalBlackNormal,
                contentColor = PalGreenNormal,
                edgePadding = 4.dp,
                divider = { HorizontalDivider(color = PalBorderColor) },
                indicator = { tabPositions ->
                    if (selectedCategoryIndex < tabPositions.size) {
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedCategoryIndex]),
                            color = PalGreenNormal
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).border(1.dp, PalBorderColor, RoundedCornerShape(12.dp))
            ) {
                categories.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedCategoryIndex == index,
                        onClick = { selectedCategoryIndex = index },
                        text = {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = if (selectedCategoryIndex == index) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedCategoryIndex == index) PalGreenNormal else PalWhiteMuted
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            when (selectedCategoryIndex) {
                0 -> {
                    Text("إدارة وتعديل هوية الوكالة المستقلة والشعار", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    AgencyProfileEditorContent(
                        viewModel = viewModel,
                        isOnboardingMode = false
                    )
                }
                1 -> {
                    Text("أمن التطبيق وصلاحيات الدخول", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("قفل التطبيق بكلمة مرور عند الفتح", color = PalWhitePure, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = appLockEnabled,
                                    onCheckedChange = { viewModel.toggleAppLock(it) },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal)
                                )
                            }

                            HorizontalDivider(color = PalBorderColor)

                            Text("تغيير كلمة مرور المدير الأدمن (الافتراضي: 123456)", color = PalWhiteMuted, fontSize = 11.5.sp)

                            var inlineNewPass by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inlineNewPass,
                                    onValueChange = { inlineNewPass = it },
                                    label = { Text("كلمة المرور الجديدة", color = PalWhiteMuted, fontSize = 11.sp) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhitePure
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(
                                    onClick = {
                                        if (inlineNewPass.trim().isNotEmpty()) {
                                            viewModel.changeAdminPassword(inlineNewPass.trim())
                                            android.widget.Toast.makeText(context, "تم تحديث كلمة المرور الجديدة للأدمن بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                            inlineNewPass = ""
                                        } else {
                                            android.widget.Toast.makeText(context, "الرجاء إدخال كلمة مرور صالحة", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    Text("تحديث", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LicenseStatusSettingsCard(
                        viewModel = viewModel,
                        onOpenActivationDialog = { viewModel.openCustomerActivationDialog() }
                    )
                }
                2 -> {
                    Text("العملة الافتراضية والعملات المسموح بها وسعر الصرف", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    val currencyConfig by viewModel.currencyConfig.collectAsState()
                    val rateVal by viewModel.sarToYerExchangeRate.collectAsState()

                    var selectedMode by remember(currencyConfig.mode) { mutableStateOf(currencyConfig.mode) }
                    var selectedDefaultCurr by remember(currencyConfig.resolvedDefaultCurrency, selectedMode) {
                        mutableStateOf(
                            when (selectedMode) {
                                CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
                                CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
                                CurrencyMode.BOTH -> currencyConfig.resolvedDefaultCurrency
                            }
                        )
                    }
                    var rateInput by remember(rateVal) {
                        mutableStateOf(if (rateVal % 1.0 == 0.0) rateVal.toInt().toString() else rateVal.toString())
                    }

                    AgencyCurrencySettingsCard(
                        selectedMode = selectedMode,
                        selectedDefaultCurrency = selectedDefaultCurr,
                        exchangeRateInput = rateInput,
                        onModeSelected = { newMode ->
                            selectedMode = newMode
                            selectedDefaultCurr = when (newMode) {
                                CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
                                CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
                                CurrencyMode.BOTH -> if (selectedDefaultCurr.contains("سعودي")) MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
                            }
                        },
                        onDefaultCurrencySelected = { newDef ->
                            selectedDefaultCurr = newDef
                        },
                        onExchangeRateChange = { rateInput = it },
                        onSaveClick = {
                            val parsedRate = MoneyMath.parseDoubleOrNull(rateInput)
                            if (parsedRate == null || parsedRate <= 0.0) {
                                android.widget.Toast.makeText(context, "يرجى إدخال سعر صرف صحيح أكبر من الصفر", android.widget.Toast.LENGTH_SHORT).show()
                                return@AgencyCurrencySettingsCard
                            }
                            viewModel.updateCurrencySettings(
                                mode = selectedMode,
                                defaultCurrency = selectedDefaultCurr,
                                exchangeRate = parsedRate
                            ) {
                                android.widget.Toast.makeText(
                                    context,
                                    "تم حفظ إعدادات العملات (${selectedMode.labelAr}) وتعميمها فوراً على النظام",
                                    android.widget.Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    )
                }
                3 -> {
                    Text("النسخ الاحتياطي السحابي والمحلي للأمان", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    com.example.data.AppDatabase.checkpoint()
                                    val backupDir = java.io.File(context.filesDir, "qat_ledger_backups")
                                    if (!backupDir.exists()) {
                                        backupDir.mkdirs()
                                    }
                                    val datePart = Helpers.getCurrentDate().replace("-", "_")
                                    val timePart = System.currentTimeMillis()
                                    val backupName = "qat_ledger_backup_${datePart}_${timePart}.db"
                                    val backupFile = java.io.File(backupDir, backupName)

                                    val dbFile = context.getDatabasePath("tofan_al_aqsa_qat_db")
                                    try {
                                        if (dbFile.exists()) {
                                            dbFile.inputStream().use { input ->
                                                backupFile.outputStream().use { output ->
                                                    input.copyTo(output)
                                                }
                                            }
                                            refreshBackupsList()
                                            android.widget.Toast.makeText(context, "تم حفظ نسخة احتياطية محلية جديدة بنجاح", android.widget.Toast.LENGTH_LONG).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "لم يندمج ملف قاعدة البيانات حالياً", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } catch (e: Exception) {
                                        android.widget.Toast.makeText(context, "فشل إنشاء النسخة: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("إنشاء نسخة احتياطية محلية جديدة", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val ok = Helpers.backupDatabase(context)
                                    if (ok) {
                                        android.widget.Toast.makeText(context, "تم تصدير نسخة احتياطية ومشاركتها سحابياً بنجاح", android.widget.Toast.LENGTH_LONG).show()
                                    } else {
                                        android.widget.Toast.makeText(context, "فشل تصدير نسخة احتياطية", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                                border = BorderStroke(1.dp, PalBorderColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تصدير ومشاركة نسخة احتياطية سحابياً", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            Button(
                                onClick = {
                                    val dateString = Helpers.getCurrentDate().replace("-", "_")
                                    createDocumentLauncher.launch("qat_ledger_backup_$dateString.db")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.SaveAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("تصدير النسخة وحفظها بملفات الهاتف", color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    filePickerLauncher.launch("*/*")
                                },
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                                border = BorderStroke(1.dp, PalBorderColor),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Icon(Icons.Default.CloudSync, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("استيراد واستعادة نسخة من ملفات الهاتف", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                            }

                            HorizontalDivider(color = PalBorderColor, modifier = Modifier.padding(vertical = 4.dp))
                            Text("قائمة السجلات والنسخ المحفوظة (${localBackupsList.size}):", color = PalWhiteSoft, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                            if (localBackupsList.isEmpty()) {
                                Text(
                                    text = "لا توجد أي نسخ احتياطية محفوظة حالياً في أرشيف النظام.",
                                    color = PalWhiteMuted,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.padding(vertical = 6.dp).fillMaxWidth(),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    localBackupsList.forEach { file ->
                                        val dateStr = java.text.SimpleDateFormat("yyyy/MM/dd hh:mm a", java.util.Locale.getDefault()).format(java.util.Date(file.lastModified()))
                                        val sizeStr = if (file.length() < 1024) "${file.length()} B" else String.format("%.1f KB", file.length() / 1024.0)
                                        Card(
                                            colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                            border = BorderStroke(1.dp, PalBorderColor),
                                            shape = RoundedCornerShape(12.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                Text(text = "نسخة احتياطية: ${file.name.substringBeforeLast(".db")}", color = PalWhitePure, fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column {
                                                        Text(text = "التاريخ: $dateStr", color = PalWhiteSoft, fontSize = 10.sp)
                                                        Text(text = "الحجم: $sizeStr", color = PalGreenNormal, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        Button(
                                                            onClick = {
                                                                selectedBackupForAction = file
                                                                showBackupRestoreConfirmDialog = true
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenDark),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.height(36.dp)
                                                        ) {
                                                            Text("استعادة", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                                        }
                                                        Button(
                                                            onClick = {
                                                                selectedBackupForAction = file
                                                                showBackupDeleteConfirmDialog = true
                                                            },
                                                            colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                            shape = RoundedCornerShape(8.dp),
                                                            modifier = Modifier.height(36.dp)
                                                        ) {
                                                            Text("حذف", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
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
                4 -> {
                    Text("إعدادات طابعة الفواتير والترويسة والذيل المخصص", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    val bHeaderEnabled by viewModel.brandingHeaderEnabled.collectAsState()
                    val bFooterEnabled by viewModel.brandingFooterEnabled.collectAsState()
                    val bHeaderText by viewModel.brandingHeaderText.collectAsState()
                    val bFooterText by viewModel.brandingFooterText.collectAsState()

                    var editHeaderEnabled by remember(bHeaderEnabled) { mutableStateOf(bHeaderEnabled) }
                    var editFooterEnabled by remember(bFooterEnabled) { mutableStateOf(bFooterEnabled) }
                    var editHeaderText by remember(bHeaderText) { mutableStateOf(bHeaderText) }
                    var editFooterText by remember(bFooterText) { mutableStateOf(bFooterText) }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("تخصيص الفواتير الحرارية والسندات المستخرجة والمشتركة:", color = PalWhiteMuted, fontSize = 12.sp)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تفعيل ترويسة الفاتورة المخصصة", color = PalWhitePure, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = editHeaderEnabled,
                                    onCheckedChange = { editHeaderEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal)
                                )
                            }

                            if (editHeaderEnabled) {
                                OutlinedTextField(
                                    value = editHeaderText,
                                    onValueChange = { editHeaderText = it },
                                    label = { Text("نص الترويسة المخصص للفاتورة", color = PalWhiteMuted, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhitePure
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            HorizontalDivider(color = PalBorderColor)

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("تفعيل تذييل الفاتورة المخصص", color = PalWhitePure, fontSize = 12.5.sp, modifier = Modifier.weight(1f))
                                Switch(
                                    checked = editFooterEnabled,
                                    onCheckedChange = { editFooterEnabled = it },
                                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal)
                                )
                            }

                            if (editFooterEnabled) {
                                OutlinedTextField(
                                    value = editFooterText,
                                    onValueChange = { editFooterText = it },
                                    label = { Text("نص التذييل المخصص للفاتورة", color = PalWhiteMuted, fontSize = 11.sp) },
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhitePure
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            Button(
                                onClick = {
                                    viewModel.updatePrintSettings(editHeaderEnabled, editFooterEnabled, editHeaderText, editFooterText)
                                    android.widget.Toast.makeText(context, "تم حفظ وتطبيق إعدادات ترويسة وتذييل الفواتير بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().height(48.dp)
                            ) {
                                Text("حفظ وتحديث نصوص الطباعة بالفواتير", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text("الاتصال بالطابعات وتحديد حجم الورق", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("اختر طابعة البلوتوث المقترنة المتاحة:", color = PalWhiteMuted, fontSize = 12.sp)

                            if (printers.isEmpty()) {
                                Text(
                                    text = "لا توجد أجهزة بلوتوث مقترنة حالياً.",
                                    color = PalWhiteMuted,
                                    fontSize = 11.5.sp,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                                    textAlign = TextAlign.Center
                                )
                            } else {
                                printers.forEach { p ->
                                    val isPrinterSelected = selectedPrinterName.contains(p.first)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isPrinterSelected) PalGreenNormal else Color.Transparent)
                                            .clickable {
                                                val (deviceType, isPrinter) = printerManager.getDeviceTypeNameAndIsPrinter(p.first, p.second)
                                                if (!isPrinter) {
                                                    android.widget.Toast.makeText(
                                                        context,
                                                        "عذراً، الجهاز المختار هو ($deviceType) وليس طابعة حرارية مدعومة.",
                                                        android.widget.Toast.LENGTH_LONG
                                                    ).show()
                                                } else {
                                                    android.widget.Toast.makeText(context, "جاري تحديد الطابعة الافتراضية...", android.widget.Toast.LENGTH_SHORT).show()
                                                    printerManager.connectPrinter(p.first, p.second) { success ->
                                                        if (success) {
                                                            selectedPrinterName = "${p.first} (${p.second})"
                                                            android.widget.Toast.makeText(context, "تم تحديد الطابعة الافتراضية بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                }
                                            }
                                            .padding(10.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(p.first, color = if (isPrinterSelected) Color.White else PalWhitePure, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                        Text(p.second, color = if (isPrinterSelected) Color.White.copy(alpha = 0.85f) else PalWhiteMuted, fontSize = 11.sp)
                                    }
                                    HorizontalDivider(color = PalBorderColor)
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            var selectedPaperMm by remember { mutableIntStateOf(printerManager.currentPrinterSize) }
                            Text("عرض ورق الطباعة المعتمد للفواتير:", color = PalWhiteMuted, fontSize = 12.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val is58 = selectedPaperMm == 58
                                val is80 = selectedPaperMm == 80
                                OutlinedButton(
                                    onClick = {
                                        selectedPaperMm = 58
                                        printerManager.savePrinterConfig(58, "")
                                        android.widget.Toast.makeText(context, "تم اعتماد مقاس 58mm للطباعة الحرارية", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (is58) PalGreenNormal else PalBlackDark,
                                        contentColor = if (is58) Color.White else PalWhitePure
                                    ),
                                    border = BorderStroke(1.dp, if (is58) PalGreenNormal else PalBorderColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("58mm (32 عمود)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectedPaperMm = 80
                                        printerManager.savePrinterConfig(80, "")
                                        android.widget.Toast.makeText(context, "تم اعتماد مقاس 80mm للطباعة الحرارية", android.widget.Toast.LENGTH_SHORT).show()
                                    },
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (is80) PalGreenNormal else PalBlackDark,
                                        contentColor = if (is80) Color.White else PalWhitePure
                                    ),
                                    border = BorderStroke(1.dp, if (is80) PalGreenNormal else PalBorderColor),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) {
                                    Text("80mm (48 عمود)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                5 -> {
                    com.example.ui.components.UniversalColorThemeSettings(viewModel = viewModel)
                }
                6 -> {
                    Text("إدارة صلاحيات الأقسام (Developer License System)", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    if (!isDeveloperUnlocked) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(18.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "هذا القسم مخصص لمطور النظام فقط. للوصول إلى لوحة تفعيل الأقسام وصلاحيات الترخيص، يرجى إدخال رمز التحقق الآمن.",
                                    color = PalWhiteSoft,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                var developerPasswordInput by remember { mutableStateOf("") }

                                OutlinedTextField(
                                    value = developerPasswordInput,
                                    onValueChange = { developerPasswordInput = it },
                                    label = { Text("رمز التحقق", color = PalWhiteMuted, fontSize = 11.sp) },
                                    visualTransformation = PasswordVisualTransformation(),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = PalGreenNormal,
                                        unfocusedBorderColor = PalBorderColor,
                                        focusedTextColor = PalWhitePure,
                                        unfocusedTextColor = PalWhitePure
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth().testTag("developer_password_input")
                                )

                                Button(
                                    onClick = {
                                        if (developerPasswordInput == "780mohmmed") {
                                            isDeveloperUnlocked = true
                                            android.widget.Toast.makeText(context, "تم التحقق بنجاح! مرحباً بك يا باشمهندس محمد", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            android.widget.Toast.makeText(context, "رمز التحقق غير صحيح! الدخول مرفوض.", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().height(48.dp).testTag("verify_developer_btn")
                                ) {
                                    Text("دخول للوحة الصلاحيات", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                            }
                        }
                    } else {
                        val accountsEnabled by viewModel.accountsEnabled.collectAsState()
                        val transfersEnabled by viewModel.transfersEnabled.collectAsState()
                        val customersEnabled by viewModel.customersEnabled.collectAsState()
                        val expensesEnabled by viewModel.expensesEnabled.collectAsState()
                        val reportsEnabled by viewModel.reportsEnabled.collectAsState()
                        val archiveEnabled by viewModel.archiveEnabled.collectAsState()

                        Card(
                            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    text = "لوحة تفعيل الأقسام والميزات الإضافية",
                                    color = PalGreenNormal,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Right
                                )

                                HorizontalDivider(color = PalBorderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قسم التحاسب وجرد الحسابات", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("جرد المبيعات والأرباح وتصفية الحسابات اليومية والفرعية والودائع", color = PalWhiteMuted, fontSize = 10.5.sp)
                                    }
                                    Switch(
                                        checked = accountsEnabled,
                                        onCheckedChange = { viewModel.setSectionEnabled("accounts", it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal),
                                        modifier = Modifier.testTag("switch_accounts")
                                    )
                                }

                                HorizontalDivider(color = PalBorderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قسم سجل الحوالات والتحويلات", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("تسجيل حركات التحويل والتبادل المالي الخارجي للصرافين والموردين", color = PalWhiteMuted, fontSize = 10.5.sp)
                                    }
                                    Switch(
                                        checked = transfersEnabled,
                                        onCheckedChange = { viewModel.setSectionEnabled("transfers", it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal),
                                        modifier = Modifier.testTag("switch_transfers")
                                    )
                                }

                                HorizontalDivider(color = PalBorderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قسم دليل وحسابات العملاء", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("دليل العملاء، تتبع أرصدتهم، وتفاصيل الديون والسدادات النقدية", color = PalWhiteMuted, fontSize = 10.5.sp)
                                    }
                                    Switch(
                                        checked = customersEnabled,
                                        onCheckedChange = { viewModel.setSectionEnabled("customers", it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal),
                                        modifier = Modifier.testTag("switch_customers")
                                    )
                                }

                                HorizontalDivider(color = PalBorderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قسم المصروفات والمصاريف اليومية", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("تتبع تكاليف التشغيل، أجور النقل، الجمارك، الإيجارات، والعمالة", color = PalWhiteMuted, fontSize = 10.5.sp)
                                    }
                                    Switch(
                                        checked = expensesEnabled,
                                        onCheckedChange = { viewModel.setSectionEnabled("expenses", it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal),
                                        modifier = Modifier.testTag("switch_expenses")
                                    )
                                }

                                HorizontalDivider(color = PalBorderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قسم التقارير الحسابية المتقدمة", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("إحصائيات متقدمة، كشوفات ختامية للوكالة والشركاء مع الطباعة والمشاركة", color = PalWhiteMuted, fontSize = 10.5.sp)
                                    }
                                    Switch(
                                        checked = reportsEnabled,
                                        onCheckedChange = { viewModel.setSectionEnabled("reports", it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal),
                                        modifier = Modifier.testTag("switch_reports")
                                    )
                                }

                                HorizontalDivider(color = PalBorderColor)

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("قسم الأرشيف الكلي للسجلات", color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                        Text("عرض السجلات والعمليات المؤرشفة والعمليات الأخيرة للنظام بأكمله", color = PalWhiteMuted, fontSize = 10.5.sp)
                                    }
                                    Switch(
                                        checked = archiveEnabled,
                                        onCheckedChange = { viewModel.setSectionEnabled("archive", it) },
                                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PalGreenNormal),
                                        modifier = Modifier.testTag("switch_archive")
                                    )
                                }
                            }
                        }

                        if (EditionCapabilities.isDeveloperEdition) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                                border = BorderStroke(1.dp, PalGreenNormal),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Text(
                                        text = "إدارة الباقات وتوقيع التراخيص التجارية (Developer Edition)",
                                        color = PalGreenNormal,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "إصدار وتوقيع تراخيص Ed25519 للعملاء، التسعير السري، إدارة المفاتيح، والنسخ الاحتياطي لسجل التراخيص.",
                                        color = PalWhiteSoft,
                                        fontSize = 11.5.sp
                                    )
                                    Button(
                                        onClick = { showDevConsoleInSettings = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth().height(48.dp).testTag("open_dev_console_settings_btn")
                                    ) {
                                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("فتح لوحة الباقات والتراخيص", color = Color.White, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            EditionCapabilities.DeveloperConsoleHost(
                visible = showDevConsoleInSettings,
                onDismiss = {
                    showDevConsoleInSettings = false
                    viewModel.refreshLicenseStatus()
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Developer Signature and Copyright Section - المهندس محمد امين ردمان الحاج
            Text(
                text = "مطور النظام والدعم الفني",
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "هندسة وتطوير الأنظمة المحاسبية التجارية",
                            color = PalGreenNormal,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = "المهندس/ محمد امين ردمان الحاج",
                        color = PalWhitePure,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 17.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Eng. Mohammed Amin Radman Al-Hajj",
                        color = PalWhiteMuted,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    val resolvedAgency = com.example.util.BrandingManager.resolvedAgencyName()
                    val qatTypesText = brandingQatTypes.ifBlank { "متعدد الأصناف" }
                    Text(
                        text = "منشئ ومصمم ومطور نظام ${com.example.util.BrandingManager.PRODUCT_NAME_AR} (${com.example.util.BrandingManager.PRODUCT_NAME_EN}) المحاسبي المتكامل لإدارة وكالات تجارة القات — مخصص حالياً لـ $resolvedAgency ($qatTypesText).",
                        color = PalWhiteSoft,
                        fontSize = 11.5.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )

                    HorizontalDivider(
                        color = PalBorderColor,
                        thickness = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "+967780961823",
                            color = PalGreenNormal,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Left
                        )
                        Text(
                            text = "الحقوق محفوظة © 2026",
                            color = PalWhiteMuted,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Right
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:+967780961823")
                                ).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                try {
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(context, "تعذر بدء الاتصال تلقائياً، الرقم هو: +967780961823", android.widget.Toast.LENGTH_LONG).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = "اتصال",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "اتصال مباشر",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = {
                                val whatsappUrl = "https://wa.me/967780961823"
                                try {
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(whatsappUrl)
                                    ).apply {
                                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    android.widget.Toast.makeText(
                                        context,
                                        "تعذر فتح الواتساب تلقائياً، يرجى مراسلة الرقم: +967780961823",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                }
                            },
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = PalSoftGreenTint, contentColor = PalWhitePure),
                            border = BorderStroke(1.dp, PalBorderColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Chat,
                                contentDescription = "واتساب",
                                tint = PalIconUnified,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "واتساب مباشر",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showLogoutConfirmDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("تسجيل الخروج من النظام المحاسبي", color = Color.White, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(36.dp))
        }
    }

    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = {
                Text(
                    text = "تأكيد تسجيل الخروج",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في تسجيل الخروج من نظام ${com.example.util.BrandingManager.PRODUCT_NAME_AR} المحاسبي؟",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        viewModel.logout()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("نعم، خروج", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted, fontWeight = FontWeight.Normal)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showPassDialog) {
        AlertDialog(
            onDismissRequest = { showPassDialog = false },
            title = { Text("تغيير كلمة المرور", color = PalWhitePure, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Right) },
            text = {
                OutlinedTextField(
                    value = inputNewPass,
                    onValueChange = { inputNewPass = it },
                    label = { Text("أدخل كلمة المرور الجديدة للأدمن", color = PalWhiteMuted) },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhitePure
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (inputNewPass.trim().isNotEmpty()) {
                            viewModel.changeAdminPassword(inputNewPass)
                            android.widget.Toast.makeText(context, "تم تحديث كلمة المرور الجديدة بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showPassDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد وحفظ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showBackupRestoreConfirmDialog && selectedBackupForAction != null) {
        AlertDialog(
            onDismissRequest = { showBackupRestoreConfirmDialog = false },
            title = {
                Text(
                    text = "تأكيد استعادة قاعدة البيانات",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في استعادة هذا الأرشيف بالكامل؟ سيتم استبدال قاعدة البيانات الحالية النشطة ببيانات هذه النسخة، وسيتم إعادة تشغيل التطبيق تلقائياً لتطبيق التغييرات.",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedBackupForAction!!
                        val dbFile = context.getDatabasePath("tofan_al_aqsa_qat_db")
                        val dbWal = java.io.File(dbFile.path + "-wal")
                        val dbShm = java.io.File(dbFile.path + "-shm")
                        try {
                            try {
                                com.example.data.AppDatabase.closeDatabase()
                            } catch (ex: Exception) {
                                // ignore
                            }

                            val tempFile = java.io.File.createTempFile("db_restore", null, context.cacheDir)
                            file.inputStream().use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            if (!tempFile.exists() || tempFile.length() == 0L) {
                                throw Exception("فشل قراءة ملف النسخة الاحتياطية أو الملف فارغ")
                            }

                            if (dbFile.exists()) dbFile.delete()
                            if (dbWal.exists()) dbWal.delete()
                            if (dbShm.exists()) dbShm.delete()

                            tempFile.copyTo(dbFile, overwrite = true)
                            tempFile.delete()

                            android.widget.Toast.makeText(context, "تمت استعادة النسخة بنجاح! جاري إعادة تشغيل النظام المحاسبي...", android.widget.Toast.LENGTH_LONG).show()

                            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
                            intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                            context.startActivity(intent)
                            (context as? android.app.Activity)?.finish()
                            java.lang.Runtime.getRuntime().exit(0)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "فشل استعادة قاعدة البيانات: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                        showBackupRestoreConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("استعادة وإعادة التشغيل", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupRestoreConfirmDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted, fontWeight = FontWeight.Normal)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    if (showBackupDeleteConfirmDialog && selectedBackupForAction != null) {
        AlertDialog(
            onDismissRequest = { showBackupDeleteConfirmDialog = false },
            title = {
                Text(
                    text = "تأكيد حذف النسخة الاحتياطية",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Text(
                    text = "هل أنت متأكد من رغبتك في حذف ملف هذه النسخة الاحتياطية نهائياً من أرشيف التطبيق؟ لا يمكن التراجع عن هذه العملية.",
                    color = PalWhiteSoft,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val file = selectedBackupForAction!!
                        if (file.exists()) {
                            file.delete()
                            refreshBackupsList()
                            android.widget.Toast.makeText(context, "تم حذف ملف النسخة الاحتياطية بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        showBackupDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("تأكيد الحذف", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBackupDeleteConfirmDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted, fontWeight = FontWeight.Normal)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // Removed showBrandingCustomizer dialog per user request
}
