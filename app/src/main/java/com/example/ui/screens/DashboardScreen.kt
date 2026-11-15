package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.Screen
import com.example.data.TaxEntry
import com.example.data.InventoryItem
import com.example.ui.theme.*
import com.example.util.Helpers
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.graphics.asImageBitmap
import com.example.ui.components.LedgerSectionHeader
import com.example.ui.components.QatLedgerAppMark
import com.example.ui.components.UnifiedPeriodSelector
import com.example.util.BrandingManager
import com.example.util.MoneyMath

// Representation of a transaction line for bento logs
data class UnifiedOperation(
    val title: String,
    val subtitle: String,
    val amount: Double,
    val isExpense: Boolean
)

data class DashboardCardInfo(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: androidx.compose.ui.graphics.Color,
    val onClick: () -> Unit
)

@Composable
fun WavingPalestineFlag(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "dual_flags_wave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )
    
    val glowIntensity by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        
        // Define crossed flagpoles (X shape) - perfectly balanced to prevent clipping!
        // Pole 1: bottom-left to top-right (holds Palestine flag)
        val pole1Start = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.92f)
        val pole1End = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.20f)
        
        // Pole 2: bottom-right to top-left (holds Yemen flag)
        val pole2Start = androidx.compose.ui.geometry.Offset(width * 0.65f, height * 0.92f)
        val pole2End = androidx.compose.ui.geometry.Offset(width * 0.35f, height * 0.20f)
        
        // 1. Draw glowing background aura behind poles
        drawCircle(
            color = Color(0xFFD4AF37).copy(alpha = 0.15f * glowIntensity),
            radius = width * 0.35f,
            center = androidx.compose.ui.geometry.Offset(width / 2f, height / 2f)
        )
        
        // 2. Draw metallic gold flagpoles
        val poleColor = Color(0xFFD4AF37) // Luxury Gold
        val poleStroke = 4f
        
        drawLine(
            color = poleColor,
            start = pole1Start,
            end = pole1End,
            strokeWidth = poleStroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        drawLine(
            color = poleColor,
            start = pole2Start,
            end = pole2End,
            strokeWidth = poleStroke,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )
        
        // Golden spheres on top of the flagpoles
        drawCircle(
            color = Color(0xFFFFD700),
            radius = 6f,
            center = pole1End
        )
        drawCircle(
            color = Color(0xFFFFD700),
            radius = 6f,
            center = pole2End
        )
        
        // 3. Draw Flag 1 (Yemen Flag): attached to Pole 2 (ends at top-left), flying left without clipping
        val f1AnchorX = width * 0.35f
        val f1AnchorY = height * 0.20f
        val flagH = height * 0.36f
        val flagW = width * 0.28f
        
        val amplitude = height * 0.05f
        val frequency = 0.12f
        val sliceWidth = 2f
        
        val yemenStripeH = flagH / 3f
        for (x in f1AnchorX.toInt() downTo (f1AnchorX - flagW).toInt() step sliceWidth.toInt()) {
            val dist = f1AnchorX - x
            // Apply damping at the pole anchor for realism
            val damping = (dist / flagW).coerceIn(0f, 1f)
            val angle = dist * frequency - phase
            val yOffset = (kotlin.math.sin(angle.toDouble()) * amplitude * damping).toFloat()
            
            val drawX = x.toFloat()
            
            // Red stripe (top)
            drawRect(
                color = Color(0xFFCE1126),
                topLeft = androidx.compose.ui.geometry.Offset(drawX, f1AnchorY + yOffset),
                size = androidx.compose.ui.geometry.Size(sliceWidth, yemenStripeH)
            )
            // White stripe (middle)
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(drawX, f1AnchorY + yemenStripeH + yOffset),
                size = androidx.compose.ui.geometry.Size(sliceWidth, yemenStripeH)
            )
            // Black stripe (bottom)
            drawRect(
                color = Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(drawX, f1AnchorY + 2f * yemenStripeH + yOffset),
                size = androidx.compose.ui.geometry.Size(sliceWidth, yemenStripeH)
            )
        }
        
        // 4. Draw Flag 2 (Palestine Flag): attached to Pole 1 (ends at top-right), flying right without clipping
        val f2AnchorX = width * 0.65f
        val f2AnchorY = height * 0.20f
        
        val palStripeH = flagH / 3f
        for (x in f2AnchorX.toInt() until (f2AnchorX + flagW).toInt() step sliceWidth.toInt()) {
            val dist = x - f2AnchorX
            val damping = (dist / flagW).coerceIn(0f, 1f)
            val angle = dist * frequency - phase
            val yOffset = (kotlin.math.sin(angle.toDouble()) * amplitude * damping).toFloat()
            
            val drawX = x.toFloat()
            
            // Black stripe (top)
            drawRect(
                color = Color.Black,
                topLeft = androidx.compose.ui.geometry.Offset(drawX, f2AnchorY + yOffset),
                size = androidx.compose.ui.geometry.Size(sliceWidth, palStripeH)
            )
            // White stripe (middle)
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(drawX, f2AnchorY + palStripeH + yOffset),
                size = androidx.compose.ui.geometry.Size(sliceWidth, palStripeH)
            )
            // Green stripe (bottom)
            drawRect(
                color = Color(0xFF009639),
                topLeft = androidx.compose.ui.geometry.Offset(drawX, f2AnchorY + 2f * palStripeH + yOffset),
                size = androidx.compose.ui.geometry.Size(sliceWidth, palStripeH)
            )
        }
        
        // Draw Red Triangle of Palestine at hoist side (f2AnchorX, pointing right)
        val palTriangleW = flagW * 0.35f
        val trianglePath = androidx.compose.ui.graphics.Path().apply {
            val yOffset0 = 0f
            val angleMid = palTriangleW * frequency - phase
            val yOffsetMid = (kotlin.math.sin(angleMid.toDouble()) * amplitude * (palTriangleW / flagW)).toFloat()
            
            moveTo(f2AnchorX, f2AnchorY + yOffset0)
            lineTo(f2AnchorX + palTriangleW, f2AnchorY + (flagH / 2f) + yOffsetMid)
            lineTo(f2AnchorX, f2AnchorY + flagH + yOffset0)
            close()
        }
        drawPath(
            path = trianglePath,
            color = Color(0xFFE4312B)
        )
    }
}

@Composable
fun AgencyLogoBadge(modifier: Modifier = Modifier) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val logoEnabled = com.example.util.BrandingManager.isLogoEnabled
    val customBitmap = remember(logoEnabled, com.example.util.BrandingManager.logoUpdateTrigger) {
        if (logoEnabled) com.example.util.BrandingManager.getCustomLogoBitmap(context) else null
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.5.dp, PalGreenNormal, RoundedCornerShape(8.dp))
            .background(PalBlackDark),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = "شعار الوكالة",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = "شعار الوكالة",
                tint = PalIconUnified,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun DashboardScreen(viewModel: AppViewModel, onMenuClick: (() -> Unit)? = null) {
    val currencyConfig by viewModel.currencyConfig.collectAsState()
    var showTaxDialog by remember { mutableStateOf(false) }
    var showSupplierCommitmentDialog by remember { mutableStateOf(false) }
    var selectedDashboardCurrency by remember {
        mutableStateOf(currencyConfig.coerceDashboardCurrency(null))
    }
    var selectedPeriod by remember { mutableStateOf("الكل") }
    var customPeriodValue by remember { mutableStateOf("") }
    var customPeriodType by remember { mutableStateOf("يوم") }
    val customPeriodTypeMapped = if (customPeriodType == "يوم") "date" else if (customPeriodType == "شهر") "month" else "year"
    
    // Period selection states for supplier reports dialog
    var supplierCommitmentPeriod by remember { mutableStateOf("يومي") } // "يومي", "أسبوعي", "شهري", "سنوي", "تقويم مخصص"
    var supplierCommitmentCustomType by remember { mutableStateOf("يوم") } // "يوم", "شهر", "سنة"
    var supplierCommitmentCustomValue by remember { mutableStateOf(Helpers.getCurrentDate()) }
    var showSupplierThermalPrintDialog by remember { mutableStateOf(false) }

    var supplierWeekYearMonth by remember {
        val todayStr = Helpers.getCurrentDate()
        mutableStateOf(todayStr.substring(0, 7))
    }
    var supplierWeekNum by remember {
        mutableStateOf(1)
    }

    LaunchedEffect(supplierCommitmentPeriod, supplierWeekYearMonth, supplierWeekNum) {
        if (supplierCommitmentPeriod == "أسبوعي" || supplierCommitmentPeriod == "اسبوعي") {
            supplierCommitmentCustomValue = "WEEK_FILTER|$supplierWeekYearMonth|$supplierWeekNum"
        }
    }
    
    val brandingAppName by viewModel.brandingAppName.collectAsState()
    val brandingAgencyName by viewModel.brandingAgencyName.collectAsState()
    val brandingOwnerName by viewModel.brandingOwnerName.collectAsState()

    val todayTax by viewModel.todayTaxAmount.collectAsState()
    val todayTaxEntry by viewModel.todayTaxEntry.collectAsState()
    val allItems by viewModel.inventoryItems.collectAsState()
    val invoices by viewModel.allInvoices.collectAsState()
    val purchases by viewModel.allPurchases.collectAsState()
    val customers by viewModel.allCustomers.collectAsState()
    val supplies by viewModel.allSuppliers.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()
    val transfers by viewModel.allTransfers.collectAsState()
    val allPayments by viewModel.allPayments.collectAsState()
    val allTaxHistory by viewModel.allTaxHistory.collectAsState()
    val allInvoiceItems by viewModel.allInvoiceItems.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.fetchTodayTax()
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    val sarToYerExchangeRateState by viewModel.sarToYerExchangeRate.collectAsState()
    var dashboardExchangeRateInput by remember(sarToYerExchangeRateState) { mutableStateOf(sarToYerExchangeRateState.toString()) }
    var mergeSarToYer by remember { mutableStateOf(false) }

    LaunchedEffect(currencyConfig) {
        selectedDashboardCurrency = currencyConfig.coerceDashboardCurrency(selectedDashboardCurrency)
        if (!currencyConfig.allowsMultiple) {
            mergeSarToYer = false
        }
    }

    val isSarSelected = selectedDashboardCurrency == "الريال السعودي"
    val rateVal = sarToYerExchangeRateState
    val mergeActive = currencyConfig.allowsMultiple && !isSarSelected && mergeSarToYer

    val periodStr = selectedPeriod
    val customType = when (customPeriodType) {
        "يوم" -> "date"
        "شهر" -> "month"
        "سنة" -> "year"
        else -> "date"
    }

    val periodInvoices = remember(invoices, selectedPeriod, customPeriodValue, customPeriodType) {
        invoices.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
    }
    val periodPurchases = remember(purchases, selectedPeriod, customPeriodValue, customPeriodType) {
        purchases.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
    }
    val periodExpenses = remember(expenses, selectedPeriod, customPeriodValue, customPeriodType) {
        expenses.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
    }
    val periodPayments = remember(allPayments, selectedPeriod, customPeriodValue, customPeriodType) {
        allPayments.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
    }
    val periodTaxHistory = remember(allTaxHistory, selectedPeriod, customPeriodValue, customPeriodType) {
        allTaxHistory.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
    }
    val periodTransfers = remember(transfers, selectedPeriod, customPeriodValue, customPeriodType) {
        transfers.filter { Helpers.matchesPeriod(it.date, periodStr, customPeriodValue, customType) }
    }

    // Calculations & Filtered values by selected currency
    val filteredInvoices = remember(periodInvoices, selectedDashboardCurrency, mergeActive) {
        periodInvoices.filter {
            if (isSarSelected) it.currency.contains("سعودي") 
            else (it.currency.contains("يمني") || mergeActive)
        }
    }

    val filteredPurchases = remember(periodPurchases, selectedDashboardCurrency, mergeActive) {
        periodPurchases.filter {
            val curr = it.currency ?: "ريال يمني"
            if (isSarSelected) curr.contains("سعودي") 
            else (curr.contains("يمني") || mergeActive)
        }
    }

    val filteredExpenses = remember(periodExpenses, selectedDashboardCurrency, mergeActive) {
        periodExpenses.filter {
            val curr = it.currency ?: "ريال يمني"
            if (isSarSelected) curr.contains("سعودي") 
            else (curr.contains("يمني") || mergeActive)
        }
    }

    val filteredStockItems = remember(allItems, selectedDashboardCurrency, mergeActive) {
        allItems.filter {
            val curr = it.buyPriceCurrency.ifEmpty { "ريال يمني" }
            if (isSarSelected) curr.contains("سعودي") 
            else (curr.contains("يمني") || mergeActive)
        }
    }

    val totalStockQty = filteredStockItems.sumOf { it.quantity }
    
    val totalStockValYer = allItems.filter { (it.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("يمني") }.sumOf { it.quantity * it.buyPrice }
    val totalStockValSar = allItems.filter { (it.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي") }.sumOf { it.quantity * it.buyPrice }
    val totalStockVal = if (isSarSelected) {
        totalStockValSar + (if (rateVal > 0) totalStockValYer / rateVal else 0.0)
    } else {
        if (mergeActive) totalStockValYer + (totalStockValSar * rateVal) else totalStockValYer
    }
    
    val todaySalesTotalYer = periodInvoices.sumOf {
        if (it.totalAmountYer > 0.0) it.totalAmountYer else if (it.currency.contains("يمني")) it.totalAmount else 0.0
    }
    val todaySalesTotalSar = periodInvoices.sumOf {
        if (it.totalAmountSar > 0.0) it.totalAmountSar else if (it.currency.contains("سعودي")) it.totalAmount else 0.0
    }
    val todaySalesTotal = if (isSarSelected) {
        todaySalesTotalSar + (if (rateVal > 0) todaySalesTotalYer / rateVal else 0.0)
    } else {
        if (mergeActive) todaySalesTotalYer + (todaySalesTotalSar * rateVal) else todaySalesTotalYer
    }
    
    val todayPurchasesTotalYer = periodPurchases.sumOf {
        if (it.totalAmountYer > 0.0) it.totalAmountYer else if (it.currency.contains("يمني")) it.totalAmount else 0.0
    }
    val todayPurchasesTotalSar = periodPurchases.sumOf {
        if (it.totalAmountSar > 0.0) it.totalAmountSar else if (it.currency.contains("سعودي")) it.totalAmount else 0.0
    }
    val todayPurchasesTotal = if (isSarSelected) {
        todayPurchasesTotalSar + (if (rateVal > 0) todayPurchasesTotalYer / rateVal else 0.0)
    } else {
        if (mergeActive) todayPurchasesTotalYer + (todayPurchasesTotalSar * rateVal) else todayPurchasesTotalYer
    }
    
    val totalDebtsOutstandingYer = remember(periodInvoices, periodPayments) {
        val invoicesTotalYer = periodInvoices.sumOf {
            if (it.totalAmountYer > 0.0) it.totalAmountYer else if (it.currency.contains("يمني")) it.totalAmount else 0.0
        }
        val invoicesPaidYer = periodInvoices.sumOf {
            if (it.paidAmountYer > 0.0) it.paidAmountYer else if (it.currency.contains("يمني")) it.paidAmount else 0.0
        }
        val paymentsCollectedYer = periodPayments.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
        (invoicesTotalYer - (invoicesPaidYer + paymentsCollectedYer)).coerceAtLeast(0.0)
    }

    val totalDebtsOutstandingSar = remember(periodInvoices, periodPayments) {
        val invoicesTotalSar = periodInvoices.sumOf {
            if (it.totalAmountSar > 0.0) it.totalAmountSar else if (it.currency.contains("سعودي")) it.totalAmount else 0.0
        }
        val invoicesPaidSar = periodInvoices.sumOf {
            if (it.paidAmountSar > 0.0) it.paidAmountSar else if (it.currency.contains("سعودي")) it.paidAmount else 0.0
        }
        val paymentsCollectedSar = periodPayments.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
        (invoicesTotalSar - (invoicesPaidSar + paymentsCollectedSar)).coerceAtLeast(0.0)
    }

    val totalDebtsOutstanding = remember(totalDebtsOutstandingYer, totalDebtsOutstandingSar, selectedDashboardCurrency, mergeActive, rateVal) {
        if (isSarSelected) {
            totalDebtsOutstandingSar + (if (rateVal > 0) totalDebtsOutstandingYer / rateVal else 0.0)
        } else {
            if (mergeActive) totalDebtsOutstandingYer + (totalDebtsOutstandingSar * rateVal) else totalDebtsOutstandingYer
        }
    }

    val todayCollectionsTotalYer = remember(periodInvoices, periodPayments) {
        val invoicesPaidYer = periodInvoices.sumOf {
            if (it.paidAmountYer > 0.0) it.paidAmountYer else if (it.currency.contains("يمني")) it.paidAmount else 0.0
        }
        val paymentsCollectedYer = periodPayments.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
        invoicesPaidYer + paymentsCollectedYer
    }

    val todayCollectionsTotalSar = remember(periodInvoices, periodPayments) {
        val invoicesPaidSar = periodInvoices.sumOf {
            if (it.paidAmountSar > 0.0) it.paidAmountSar else if (it.currency.contains("سعودي")) it.paidAmount else 0.0
        }
        val paymentsCollectedSar = periodPayments.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
        invoicesPaidSar + paymentsCollectedSar
    }

    val todayCollectionsTotal = remember(todayCollectionsTotalYer, todayCollectionsTotalSar, selectedDashboardCurrency, mergeActive, rateVal) {
        if (isSarSelected) {
            todayCollectionsTotalSar + (if (rateVal > 0) todayCollectionsTotalYer / rateVal else 0.0)
        } else {
            if (mergeActive) todayCollectionsTotalYer + (todayCollectionsTotalSar * rateVal) else todayCollectionsTotalYer
        }
    }

    // Today outflows & tax apply to Yemeni Rial only
    val todayTaxValYer = periodTaxHistory.fold(0.0) { acc, entry -> acc + entry.taxAmount }
    val todayStallOutflowYer = periodTaxHistory.fold(0.0) { acc, entry -> acc + entry.stallOutflow }
    val todayLaborOutflowYer = periodTaxHistory.fold(0.0) { acc, entry -> acc + entry.laborOutflow }

    val todayTaxVal = if (isSarSelected) (if (rateVal > 0) todayTaxValYer / rateVal else 0.0) else todayTaxValYer
    val todayStallOutflow = if (isSarSelected) (if (rateVal > 0) todayStallOutflowYer / rateVal else 0.0) else todayStallOutflowYer
    val todayLaborOutflow = if (isSarSelected) (if (rateVal > 0) todayLaborOutflowYer / rateVal else 0.0) else todayLaborOutflowYer

    val todayExpensesTotalYer = periodExpenses.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
    val todayExpensesTotalSar = periodExpenses.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
    val todayExpensesTotal = if (isSarSelected) {
        todayExpensesTotalSar + (if (rateVal > 0) todayExpensesTotalYer / rateVal else 0.0)
    } else {
        if (mergeActive) todayExpensesTotalYer + (todayExpensesTotalSar * rateVal) else todayExpensesTotalYer
    }

    val todayProfitEstYer = todaySalesTotalYer - todayPurchasesTotalYer - todayTaxValYer - todayStallOutflowYer - todayLaborOutflowYer - todayExpensesTotalYer
    val todayProfitEstSar = todaySalesTotalSar - todayPurchasesTotalSar - todayExpensesTotalSar

    val todayProfitEst = if (isSarSelected) {
        todayProfitEstSar + (if (rateVal > 0) todayProfitEstYer / rateVal else 0.0)
    } else {
        if (mergeActive) todayProfitEstYer + (todayProfitEstSar * rateVal) else todayProfitEstYer
    }

    val currencySuffix = when (selectedDashboardCurrency) {
        "الريال السعودي" -> "ر.س"
        "الريال اليمني" -> "ر.ي"
        else -> "ريال"
    }

    val todaySoldQty = remember(periodInvoices, allInvoiceItems) {
        val periodInvoiceIds = periodInvoices.map { it.id }.toSet()
        allInvoiceItems.filter { it.invoiceId in periodInvoiceIds }.sumOf { it.quantity }
    }

    val todayPurchasesPaidYer = remember(periodPurchases) {
        periodPurchases.sumOf {
            if (it.paidAmountYer > 0.0) it.paidAmountYer else if (it.currency.contains("يمني")) it.paidAmount else 0.0
        }
    }
    val todayPurchasesPaidSar = remember(periodPurchases) {
        periodPurchases.sumOf {
            if (it.paidAmountSar > 0.0) it.paidAmountSar else if (it.currency.contains("سعودي")) it.paidAmount else 0.0
        }
    }

    val cashBoxYer = remember(todayCollectionsTotalYer, todayExpensesTotalYer, todayPurchasesPaidYer, todayTaxValYer, todayStallOutflowYer, todayLaborOutflowYer) {
        (todayCollectionsTotalYer - todayExpensesTotalYer - todayPurchasesPaidYer - todayTaxValYer - todayStallOutflowYer - todayLaborOutflowYer).coerceAtLeast(0.0)
    }
    val cashBoxSar = remember(todayCollectionsTotalSar, todayExpensesTotalSar, todayPurchasesPaidSar) {
        (todayCollectionsTotalSar - todayExpensesTotalSar - todayPurchasesPaidSar).coerceAtLeast(0.0)
    }

    val todayNewDebtYer = remember(periodInvoices) {
        periodInvoices.sumOf {
            if (it.totalAmountYer > 0.0) it.debtAmountYer else if (it.currency.contains("يمني")) it.debtAmount else 0.0
        }
    }
    val todayNewDebtSar = remember(periodInvoices) {
        periodInvoices.sumOf {
            if (it.totalAmountSar > 0.0) it.debtAmountSar else if (it.currency.contains("سعودي")) it.debtAmount else 0.0
        }
    }

    // Fetch live recent operations directly from tables filtered by selected period
    val recentOperations = remember(periodInvoices, periodExpenses, selectedDashboardCurrency, rateVal) {
        val ops = mutableListOf<UnifiedOperation>()
        for (inv in periodInvoices) {
            val isInvSar = inv.currency.contains("سعودي")
            val convertedAmount = if (isSarSelected) {
                if (isInvSar) inv.totalAmount else (if (rateVal > 0) inv.totalAmount / rateVal else 0.0)
            } else {
                if (!isInvSar) inv.totalAmount else (inv.totalAmount * rateVal)
            }
            val displayCurrency = if (isSarSelected) "سعودي (محول)" else (if (isInvSar) "سعودي" else "يمني")
            ops.add(
                UnifiedOperation(
                    title = "مبيع: " + (inv.customerName.ifEmpty { "عميل نقدي" }),
                    subtitle = "فاتورة #${inv.id} • ${inv.date.split(" ").firstOrNull() ?: ""} • $displayCurrency",
                    amount = convertedAmount,
                    isExpense = false
                )
            )
        }
        for (exp in periodExpenses) {
            val expCurr = exp.currency ?: "ريال يمني"
            val isExpSar = expCurr.contains("سعودي")
            val convertedAmount = if (isSarSelected) {
                if (isExpSar) exp.amount else (if (rateVal > 0) exp.amount / rateVal else 0.0)
            } else {
                if (!isExpSar) exp.amount else (exp.amount * rateVal)
            }
            val displayCurrency = if (isSarSelected) "سعودي (محول)" else (if (isExpSar) "سعودي" else "يمني")
            ops.add(
                UnifiedOperation(
                    title = "مصروف: " + exp.category,
                    subtitle = "${exp.notes.ifEmpty { "منصرف عام" }} • ${exp.date.split(" ").firstOrNull() ?: ""} • $displayCurrency",
                    amount = convertedAmount,
                    isExpense = true
                )
            )
        }
        // Sort both by date descending if possible, but since lists might not be fully sorted, we can just take the latest 4
        ops.take(4)
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PalBlackNormal)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        if (onMenuClick != null) {
                            IconButton(onClick = onMenuClick) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "القائمة الرئيسية",
                                    tint = PalIconUnified
                                )
                            }
                        }
                        QatLedgerAppMark(size = 38.dp, cornerRadius = 10.dp)
                        Column {
                            Text(
                                text = brandingAppName.ifEmpty { BrandingManager.PRODUCT_NAME_AR },
                                color = PalWhitePure,
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                            )
                            Text(
                                text = brandingAgencyName.ifBlank { BrandingManager.resolvedAgencyName() },
                                color = PalWhiteMuted,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 11.sp
                                )
                            )
                        }
                    }

                    Surface(
                        color = PalSoftGreenTint,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(0.8.dp, PalSuccessNormal.copy(alpha = 0.28f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .background(PalSuccessNormal, CircleShape)
                            )
                            Text(
                                text = "محلي متصل",
                                color = PalSuccessNormal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
            }
        },
        containerColor = PalBlackDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            
            // 1. Executive Agency Banner & Live Cash Register Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val finalAgencyName = brandingAgencyName.ifBlank { BrandingManager.resolvedAgencyName() }
                            val ownerLabel = brandingOwnerName.trim().let { if (it.isNotEmpty()) "الوكيل: $it • " else "" }
                            Text(
                                text = finalAgencyName,
                                color = PalWhitePure,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                lineHeight = 20.sp
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "${ownerLabel}إدارة المبيعات والمخزون والحسابات",
                                color = PalWhiteMuted,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        AgencyLogoBadge(modifier = Modifier.size(46.dp))
                    }

                    HorizontalDivider(color = PalBorderColor, thickness = 1.dp)

                    // Live Cash Register (صندوق النقدية المباشر)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(PalSoftGreenTint),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                            }
                            Text(
                                text = "صندوق النقدية المباشر",
                                color = PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Surface(
                            color = PalSoftGreenTint,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.8.dp, PalSuccessNormal.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "الصندوق مفتوح",
                                color = PalSuccessNormal,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (currencyConfig.allowsYer || cashBoxYer != 0.0) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = PalBlackDark,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PalBorderColor)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    val label = if (currencyConfig.allowsYer) "نقدية الصندوق (يمني)" else "نقدية الصندوق (يمني - تاريخي)"
                                    Text(label, color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Helpers.formatWithCurrency(cashBoxYer, "ريال يمني"),
                                        color = PalSuccessNormal,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }

                        if (currencyConfig.allowsSar || cashBoxSar != 0.0) {
                            Surface(
                                modifier = Modifier.weight(1f),
                                color = PalBlackDark,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, PalBorderColor)
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                    val label = if (currencyConfig.allowsSar) "نقدية الصندوق (سعودي)" else "نقدية الصندوق (سعودي - تاريخي)"
                                    Text(label, color = PalWhiteMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = Helpers.formatWithCurrency(cashBoxSar, "ريال سعودي"),
                                        color = PalWhitePure,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Today's Daily Register Metrics Bar (ميزان اليومية السريع)
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ميزان اليومية وجرد الجلسة ($periodStr)",
                            color = PalWhitePure,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تحديث لحظي",
                            color = PalSuccessNormal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Metric 1: Sales
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = PalBlackDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, PalBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("المبيعات", color = PalWhiteMuted, fontSize = 9.5.sp)
                                Text(
                                    text = if (isSarSelected) Helpers.formatWithCurrency(todaySalesTotalSar, "ريال سعودي") else Helpers.formatWithCurrency(todaySalesTotalYer, "ريال يمني"),
                                    color = PalWhitePure,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Metric 2: Cash in
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = PalBlackDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, PalBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("المقبوض", color = PalWhiteMuted, fontSize = 9.5.sp)
                                Text(
                                    text = if (isSarSelected) Helpers.formatWithCurrency(todayCollectionsTotalSar, "ريال سعودي") else Helpers.formatWithCurrency(todayCollectionsTotalYer, "ريال يمني"),
                                    color = PalSuccessNormal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Metric 3: Debt Out
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = PalBlackDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, PalBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("الآجل (الديون)", color = PalWhiteMuted, fontSize = 9.5.sp)
                                Text(
                                    text = if (isSarSelected) Helpers.formatWithCurrency(todayNewDebtSar, "ريال سعودي") else Helpers.formatWithCurrency(todayNewDebtYer, "ريال يمني"),
                                    color = PalRedNormal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }

                        // Metric 4: Bundles Sold
                        Surface(
                            modifier = Modifier.weight(1f),
                            color = PalBlackDark,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(0.5.dp, PalBorderColor)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("مباع القات", color = PalWhiteMuted, fontSize = 9.5.sp)
                                Text(
                                    text = "${Helpers.formatQuantity(todaySoldQty)} حبة",
                                    color = PalWhitePure,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 3. Core Quick Actions Grid
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "العمليات المحاسبية واليومية السريعة",
                            color = PalWhitePure,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "بوابة العمليات",
                            color = PalWhiteMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Row 1: Sales & Supplies
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardQuickActionTile(
                            title = "فاتورة بيع ومبيعات",
                            subtitle = "إنشاء بيع وسند فوري",
                            icon = Icons.Default.AddShoppingCart,
                            onClick = { viewModel.navigateTo(Screen.Sales) },
                            modifier = Modifier.weight(1f)
                        )
                        DashboardQuickActionTile(
                            title = "توريد قات ومشتريات",
                            subtitle = "تسجيل وارد من الموردين",
                            icon = Icons.Default.LocalShipping,
                            onClick = { viewModel.navigateTo(Screen.Suppliers) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 2: Customer Collections & Customer Ledgers
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardQuickActionTile(
                            title = "سند تحصيل وقبض",
                            subtitle = "قبض دفعة من مديونية عميل",
                            icon = Icons.Default.Payments,
                            onClick = { viewModel.navigateTo(Screen.Customers) },
                            modifier = Modifier.weight(1f)
                        )
                        DashboardQuickActionTile(
                            title = "حسابات وديون العملاء",
                            subtitle = "كشف الذمم والمطالبات",
                            icon = Icons.Default.People,
                            onClick = { viewModel.navigateTo(Screen.Customers) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 3: Inventory & Statement
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardQuickActionTile(
                            title = "جرد ومخزون القات",
                            subtitle = "الحبات والعلاقي والمخزن",
                            icon = Icons.Default.Layers,
                            onClick = { viewModel.navigateTo(Screen.Inventory) },
                            modifier = Modifier.weight(1f)
                        )
                        DashboardQuickActionTile(
                            title = "كشف حساب ومطابقة",
                            subtitle = "كشوفات تفصيلية وطباعة",
                            icon = Icons.Default.Description,
                            onClick = { viewModel.navigateTo(Screen.Statement) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Row 4: Expenses & Reports
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DashboardQuickActionTile(
                            title = "سند صرف ومصروفات",
                            subtitle = "نثريات، مفرش، مصاريف",
                            icon = Icons.Default.PriceCheck,
                            onClick = { viewModel.navigateTo(Screen.Expenses) },
                            modifier = Modifier.weight(1f)
                        )
                        DashboardQuickActionTile(
                            title = "التقارير والأرباح",
                            subtitle = "أداء اليوم وميزان المبيعات",
                            icon = Icons.Default.Assessment,
                            onClick = { viewModel.navigateTo(Screen.Reports) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Executive Dashboard Interactive Filter Deck
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "لوحة التحكم وفلاتر المراقبة المالية",
                            color = PalWhitePure,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(PalSoftGreenTint)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "مباشر",
                                color = PalSuccessNormal,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // 1. Currency Switching Tabs (driven by Central Agency Currency Config)
                    val dashboardCurrOpts = currencyConfig.dashboardCurrencyOptions()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        dashboardCurrOpts.forEach { currencyOption ->
                            val isSelected = selectedDashboardCurrency == currencyOption
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) PalGreenNormal else PalBlackDark)
                                    .border(1.dp, if (isSelected) PalGreenNormal else PalBorderColor, RoundedCornerShape(10.dp))
                                    .clickable { selectedDashboardCurrency = currencyOption }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(if (isSelected) Color.White else PalWhiteMuted, CircleShape)
                                    )
                                    Text(
                                        text = currencyOption,
                                        color = if (isSelected) Color.White else PalWhiteSoft,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    // 2. Dynamic Time Period Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(10.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val periods = listOf(
                            "الكل" to "الكل",
                            "يومي" to "اليوم",
                            "أسبوعي" to "الأسبوع",
                            "شهري" to "الشهر",
                            "سنوي" to "السنة"
                        )
                        periods.forEach { (pKey, pLabel) ->
                            val isSelected = selectedPeriod == pKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) PalSoftGreenTint else Color.Transparent)
                                    .border(1.dp, if (isSelected) PalGreenNormal else Color.Transparent, RoundedCornerShape(8.dp))
                                    .clickable { selectedPeriod = pKey }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = pLabel,
                                    color = if (isSelected) PalSuccessNormal else PalWhiteMuted,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Currency Conversion & Exchange Rate Manager Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Checkbox(
                                checked = mergeSarToYer,
                                onCheckedChange = { mergeSarToYer = it },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = PalGreenNormal,
                                    uncheckedColor = PalWhiteMuted,
                                    checkmarkColor = Color.White
                                ),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = "دمج وتحويل العمليات من السعودي إلى اليمني",
                                color = PalWhitePure,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سعر صرف السعودي مقابل اليمني:",
                            color = PalWhiteMuted,
                            fontSize = 11.sp,
                            modifier = Modifier.weight(1.3f)
                        )

                        OutlinedTextField(
                            value = dashboardExchangeRateInput,
                            onValueChange = { dashboardExchangeRateInput = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenNormal,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, textAlign = TextAlign.Center),
                            modifier = Modifier.weight(1.0f)
                        )

                        Button(
                            onClick = {
                                val r = dashboardExchangeRateInput.toDoubleOrNull() ?: 0.0
                                if (r > 0) {
                                    viewModel.updateExchangeRate(r)
                                    android.widget.Toast.makeText(context, "تم تحديث سعر الصرف بنجاح", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(0.8f)
                                .heightIn(min = 44.dp)
                        ) {
                            Text("تحديث", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            // Tax & Outflows Status Block
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Card 1: Tax
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (todayTaxVal > 0) PalSoftGreenTint else PalRedNormal.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (todayTaxVal > 0) PalSuccessNormal.copy(alpha = 0.35f) else PalRedNormal.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTaxDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (todayTaxVal > 0) PalSuccessNormal.copy(alpha = 0.14f) else PalRedNormal.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (todayTaxVal > 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (todayTaxVal > 0) PalSuccessNormal else PalRedNormal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ضريبة القات اليومية", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = if (todayTaxVal > 0) Helpers.formatMoney(todayTaxVal) else "لم تسجل بعد",
                                color = if (todayTaxVal > 0) PalSuccessNormal else PalRedNormal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Card 2: Stall & Labor Outflows
                val isOutflowEntered = todayStallOutflow > 0 || todayLaborOutflow > 0
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isOutflowEntered) PalSoftGreenTint else PalRedNormal.copy(alpha = 0.08f)
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isOutflowEntered) PalSuccessNormal.copy(alpha = 0.35f) else PalRedNormal.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showTaxDialog = true }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .background(
                                    if (isOutflowEntered) PalSuccessNormal.copy(alpha = 0.14f) else PalRedNormal.copy(alpha = 0.12f),
                                    CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isOutflowEntered) Icons.Default.Payments else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (isOutflowEntered) PalSuccessNormal else PalRedNormal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("مفرش وعمالة اليوم", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                            Text(
                                text = if (isOutflowEntered) Helpers.formatMoney(todayStallOutflow + todayLaborOutflow) else "لم تسجل بعد",
                                color = if (isOutflowEntered) PalSuccessNormal else PalRedNormal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Dynamic Labels based on selectedPeriod
            val periodLabelSuffix = when (selectedPeriod) {
                "يومي" -> "اليوم"
                "أسبوعي" -> "الأسبوع"
                "شهري" -> "الشهر"
                "سنوي" -> "السنة"
                else -> "الفترة"
            }
            val salesCardLabel = if (selectedPeriod == "الكل") "إجمالي المبيعات الكلية" else "مبيعات $periodLabelSuffix"
            val collectionsLabel = if (selectedPeriod == "الكل") "إجمالي تحصيلات الخزينة" else "تحصيلات $periodLabelSuffix"
            val debtsLabel = "إجمالي ديون المستحقات المعلقة"

            // 1. Prominent Sales Card (Hero card)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalGreenNormal.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .clickable { viewModel.navigateTo(Screen.Sales) }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(PalSoftGreenTint, PalBlackNormal)
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = salesCardLabel,
                                color = PalSuccessNormal,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            if (isSarSelected) {
                                Text(
                                    text = Helpers.formatWithCurrency(todaySalesTotalSar, "ريال سعودي"),
                                    color = PalWhitePure,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "ما يعادل باليمني: " + Helpers.formatWithCurrency(todaySalesTotalSar * rateVal, "ريال يمني"),
                                    color = PalSuccessNormal,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Text(
                                    text = Helpers.formatWithCurrency(todaySalesTotalYer, "ريال يمني"),
                                    color = PalWhitePure,
                                    fontSize = 23.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                if (mergeActive) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "شامل المبيعات المحولة من السعودي بسعر صرف: $rateVal",
                                        color = PalWhiteMuted,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .background(PalSoftGreenTint, CircleShape)
                                .border(1.dp, PalBorderColor, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.TrendingUp,
                                contentDescription = null,
                                tint = PalIconUnified,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // 2. Double-Column Grid for Profit & Collections
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val todayProfitVal = if (isSarSelected) todayProfitEstSar else todayProfitEstYer
                val isProfitToday = todayProfitVal >= 0
                val netLabelColor = if (isProfitToday) PalSuccessNormal else PalRedNormal
                val profitBgColor = if (isProfitToday) PalSoftGreenTint else PalRedNormal.copy(alpha = 0.08f)

                val netProfitLabelDynamic = if (isProfitToday) {
                    if (selectedPeriod == "الكل") "إجمالي صافي الأرباح" else "صافي الربح ($periodLabelSuffix)"
                } else {
                    if (selectedPeriod == "الكل") "إجمالي صافي الخسائر" else "صافي الخسارة ($periodLabelSuffix)"
                }

                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, netLabelColor.copy(alpha = 0.25f)),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 112.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(profitBgColor)
                            .padding(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = netProfitLabelDynamic,
                                    color = netLabelColor,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Icon(
                                    imageVector = if (isProfitToday) Icons.Default.TrendingUp else Icons.Default.TrendingDown,
                                    contentDescription = null,
                                    tint = netLabelColor,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Column {
                                if (isSarSelected) {
                                    Text(
                                        text = Helpers.formatWithCurrency(kotlin.math.abs(todayProfitEstSar), "ريال سعودي"),
                                        color = netLabelColor,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = Helpers.formatWithCurrency(kotlin.math.abs(todayProfitEstSar * rateVal), "ريال يمني"),
                                        color = PalWhiteMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else {
                                    Text(
                                        text = Helpers.formatWithCurrency(kotlin.math.abs(todayProfitEstYer), "ريال يمني"),
                                        color = netLabelColor,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (rateVal > 0) {
                                        Text(
                                            text = Helpers.formatWithCurrency(kotlin.math.abs(todayProfitEstYer / rateVal), "ريال سعودي"),
                                            color = PalWhiteMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Collections Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    modifier = Modifier
                        .weight(1.1f)
                        .heightIn(min = 112.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = collectionsLabel,
                                color = PalSuccessNormal,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                imageVector = Icons.Default.Payments,
                                contentDescription = null,
                                tint = PalIconUnified,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        Column {
                            if (isSarSelected) {
                                Text(
                                    text = Helpers.formatWithCurrency(todayCollectionsTotalSar, "ريال سعودي"),
                                    color = PalSuccessNormal,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = Helpers.formatWithCurrency(todayCollectionsTotalSar * rateVal, "ريال يمني"),
                                    color = PalWhiteMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            } else {
                                Text(
                                    text = Helpers.formatWithCurrency(todayCollectionsTotalYer, "ريال يمني"),
                                    color = PalSuccessNormal,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 3. Outstanding Debts Full-Width Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalRedNormal.copy(alpha = 0.25f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = debtsLabel,
                            color = PalRedNormal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        if (isSarSelected) {
                            Text(
                                text = Helpers.formatWithCurrency(totalDebtsOutstandingSar, "ريال سعودي"),
                                color = PalRedNormal,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "ما يعادل: " + Helpers.formatWithCurrency(totalDebtsOutstandingSar * rateVal, "ريال يمني"),
                                color = PalWhiteMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text(
                                text = Helpers.formatWithCurrency(totalDebtsOutstandingYer, "ريال يمني"),
                                color = PalRedNormal,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(PalRedNormal.copy(alpha = 0.12f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = PalRedNormal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 4. Stock Status Progress Card
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .clickable { viewModel.navigateTo(Screen.Inventory) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    val lowStockItemsCount = allItems.count { it.quantity < 10 }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Layers,
                                contentDescription = null,
                                tint = PalIconUnified,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("حالة المخزون والأصناف", color = PalWhitePure, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        }

                        if (lowStockItemsCount > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(PalRedNormal.copy(alpha = 0.12f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "كمية منخفضة لـ $lowStockItemsCount أصناف",
                                    color = PalRedNormal,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(100.dp))
                                    .background(PalSoftGreenTint)
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "المخزون مستقر ومتوفر",
                                    color = PalSuccessNormal,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    val totalInventoryItems = allItems.size
                    val percentage = if (totalInventoryItems > 0) {
                        (totalInventoryItems - lowStockItemsCount).toFloat() / totalInventoryItems.toFloat()
                    } else {
                        1.0f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(100.dp))
                            .background(PalBorderColor)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(percentage)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(100.dp))
                                .background(if (lowStockItemsCount > 0) PalRedNormal else PalSuccessNormal)
                        )
                    }
                }
            }

            // CORE BUSINESS PILLARS (KPI CARDS GRID)
            LedgerSectionHeader(
                title = "المؤشرات والأداء التشغيلي",
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Re-calculate KPI stats variables (preserved calculations)
            val stockItemQtyYer = allItems.filter { (it.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("يمني") }.sumOf { it.quantity }
            val stockItemQtySar = allItems.filter { (it.buyPriceCurrency.ifEmpty { "ريال يمني" }).contains("سعودي") }.sumOf { it.quantity }

            val debtorCountYer = invoices.filter {
                (if (it.debtAmountYer > 0.0) it.debtAmountYer else if (it.currency.contains("يمني")) it.debtAmount else 0.0) > 0.0
            }.map { it.customerName }.distinct().size

            val totalSupplierDebtYer = purchases.sumOf {
                val d = if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.debtRemainingYer else if (it.currency.contains("يمني")) it.debtRemaining else 0.0
                if (d > 0.0) d else 0.0
            }
            val totalSupplierDebtSar = purchases.sumOf {
                val d = if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.debtRemainingSar else if (it.currency.contains("سعودي")) it.debtRemaining else 0.0
                if (d > 0.0) d else 0.0
            }
            val totalSupplierSurplusYer = purchases.sumOf {
                val d = if (it.totalAmountYer > 0.0 || it.totalAmountSar > 0.0) it.debtRemainingYer else if (it.currency.contains("يمني")) it.debtRemaining else 0.0
                if (d < 0.0) -d else 0.0
            }

            val totalSupplierDebt = if (isSarSelected) {
                totalSupplierDebtSar + (if (rateVal > 0) totalSupplierDebtYer / rateVal else 0.0)
            } else {
                if (mergeActive) totalSupplierDebtYer + (totalSupplierDebtSar * rateVal) else totalSupplierDebtYer
            }

            val opMarginYer = if (todaySalesTotalYer > 0) (todayProfitEstYer / todaySalesTotalYer) * 100.0 else 0.0
            val opMarginSar = if (todaySalesTotalSar > 0) (todayProfitEstSar / todaySalesTotalSar) * 100.0 else 0.0
            val activeOpMargin = if (isSarSelected) opMarginSar else opMarginYer
            val performanceLabel = if (isSarSelected) {
                when {
                    todayProfitEstSar > 5000 -> "أداء مالي متميز"
                    todayProfitEstSar > 1000 -> "أداء مستقر ومتزن"
                    else -> "تحت المراجعة"
                }
            } else {
                when {
                    todayProfitEstYer > 25000 -> "أداء مالي متميز"
                    todayProfitEstYer > 5000 -> "أداء مستقر ومتزن"
                    else -> "تحت المراجعة"
                }
            }

            val totalSentYer = periodTransfers.filter { (it.currency ?: "ريال يمني").contains("يمني") }.sumOf { it.amount }
            val totalSentSar = periodTransfers.filter { (it.currency ?: "ريال يمني").contains("سعودي") }.sumOf { it.amount }
            val activeSentVal = if (isSarSelected) {
                totalSentSar + (if (rateVal > 0) totalSentYer / rateVal else 0.0)
            } else {
                if (mergeActive) totalSentYer + (totalSentSar * rateVal) else totalSentYer
            }
            val transfersCount = periodTransfers.filter {
                (it.currency ?: "ريال يمني").contains(if (isSarSelected) "سعودي" else "يمني")
            }.size

            // Collect permission state flows
            val customersEnabled by viewModel.customersEnabled.collectAsState()
            val accountsEnabled by viewModel.accountsEnabled.collectAsState()
            val transfersEnabled by viewModel.transfersEnabled.collectAsState()
            val expensesEnabled by viewModel.expensesEnabled.collectAsState()
            val archiveEnabled by viewModel.archiveEnabled.collectAsState()

            val unifiedIconTint = PalIconUnified
            val activeCards = remember(
                allItems, invoices, purchases, periodTransfers, todayExpensesTotal,
                isSarSelected, rateVal, totalStockVal, totalDebtsOutstanding,
                totalSupplierDebt, activeOpMargin, performanceLabel, activeSentVal,
                transfersCount, customersEnabled, accountsEnabled, transfersEnabled, expensesEnabled,
                unifiedIconTint
            ) {
                val list = mutableListOf<com.example.ui.screens.DashboardCardInfo>()

                // KPI 1: Stock Asset Value
                list.add(
                    com.example.ui.screens.DashboardCardInfo(
                        title = "قيمة أصول مخزون القات",
                        value = if (isSarSelected) Helpers.formatWithCurrency(totalStockVal, "ريال سعودي") else Helpers.formatWithCurrency(totalStockVal, "ريال يمني"),
                        subtitle = if (isSarSelected) "ما يعادل: " + Helpers.formatWithCurrency(totalStockVal * rateVal, "ريال يمني") else "المخزون المتوفر: $stockItemQtyYer حبة",
                        icon = Icons.Default.Layers,
                        accentColor = unifiedIconTint,
                        onClick = { viewModel.navigateTo(Screen.Inventory) }
                    )
                )

                // KPI 2: Customer Debts (Receivables)
                if (customersEnabled) {
                    list.add(
                        com.example.ui.screens.DashboardCardInfo(
                            title = "ديون ومستحقات العملاء",
                            value = if (isSarSelected) Helpers.formatWithCurrency(totalDebtsOutstanding, "ريال سعودي") else Helpers.formatWithCurrency(totalDebtsOutstanding, "ريال يمني"),
                            subtitle = if (isSarSelected) "ما يعادل: " + Helpers.formatWithCurrency(totalDebtsOutstanding * rateVal, "ريال يمني") else "المستحق لدى $debtorCountYer عملاء",
                            icon = Icons.Default.People,
                            accentColor = unifiedIconTint,
                            onClick = { viewModel.navigateTo(Screen.Customers) }
                        )
                    )
                }

                // KPI 3: Supplier Obligations
                list.add(
                    com.example.ui.screens.DashboardCardInfo(
                        title = "التزامات الموردين الكلية",
                        value = if (isSarSelected) Helpers.formatWithCurrency(totalSupplierDebt, "ريال سعودي") else Helpers.formatWithCurrency(totalSupplierDebt, "ريال يمني"),
                        subtitle = if (isSarSelected) "ما يعادل: " + Helpers.formatWithCurrency(totalSupplierDebt * rateVal, "ريال يمني") else "المستحق: ${Helpers.formatMoney(totalSupplierDebtYer)} (فائض: ${Helpers.formatMoney(totalSupplierSurplusYer)})",
                        icon = Icons.Default.LocalShipping,
                        accentColor = unifiedIconTint,
                        onClick = { showSupplierCommitmentDialog = true }
                    )
                )

                // KPI 4: Operating Margin
                if (accountsEnabled) {
                    list.add(
                        com.example.ui.screens.DashboardCardInfo(
                            title = "الهامش التشغيلي الكلي",
                            value = "${String.format(Locale.US, "%.1f", activeOpMargin)}%",
                            subtitle = performanceLabel,
                            icon = Icons.Default.BarChart,
                            accentColor = unifiedIconTint,
                            onClick = { viewModel.navigateTo(Screen.Accounts) }
                        )
                    )
                }

                // KPI 5: Period Money Transfers
                if (transfersEnabled) {
                    list.add(
                        com.example.ui.screens.DashboardCardInfo(
                            title = "عموم إرساليات الفترة",
                            value = if (isSarSelected) Helpers.formatWithCurrency(activeSentVal, "ريال سعودي") else Helpers.formatWithCurrency(activeSentVal, "ريال يمني"),
                            subtitle = if (isSarSelected) "ما يعادل: " + Helpers.formatWithCurrency(activeSentVal * rateVal, "ريال يمني") else "مجموع: $transfersCount حركة مالية",
                            icon = Icons.Default.CompareArrows,
                            accentColor = unifiedIconTint,
                            onClick = { viewModel.navigateTo(Screen.Transfers) }
                        )
                    )
                }

                // KPI 6: Operational Expenses
                if (expensesEnabled) {
                    list.add(
                        com.example.ui.screens.DashboardCardInfo(
                            title = "مصروفات التشغيل المخرجة",
                            value = if (isSarSelected) Helpers.formatWithCurrency(todayExpensesTotal, "ريال سعودي") else Helpers.formatWithCurrency(todayExpensesTotal, "ريال يمني"),
                            subtitle = if (isSarSelected) "ما يعادل: " + Helpers.formatWithCurrency(todayExpensesTotal * rateVal, "ريال يمني") else "شاملة النثريات اليومية للوكالة",
                            icon = Icons.Default.Payments,
                            accentColor = unifiedIconTint,
                            onClick = { viewModel.navigateTo(Screen.Expenses) }
                        )
                    )
                }

                list
            }

            // Render KPI Grid
            Column(
                modifier = Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                activeCards.chunked(2).forEach { rowCards ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowCards.forEach { card ->
                            DashboardKpiCard(
                                title = card.title,
                                value = card.value,
                                subtitle = card.subtitle,
                                icon = card.icon,
                                accentColor = card.accentColor,
                                onClick = card.onClick,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowCards.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            // ANALYTICAL PERFORMANCE CHART
            LedgerSectionHeader(
                title = "الأداء المالي البياني",
                modifier = Modifier.padding(bottom = 10.dp)
            )

            val chartBorderColor = PalBorderColor
            val chartSalesColor = PalSuccessNormal
            val chartExpensesColor = PalRedNormal

            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                border = BorderStroke(1.dp, PalBorderColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("مقارنة مبيعات ومصروفات الأسبوع", color = PalWhitePure, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(chartSalesColor, RoundedCornerShape(2.dp)))
                            Text(" مبيعات", color = PalWhiteMuted, fontSize = 9.5.sp, modifier = Modifier.padding(start = 4.dp, end = 12.dp))
                            Box(modifier = Modifier.size(8.dp).background(chartExpensesColor, RoundedCornerShape(2.dp)))
                            Text(" مصروفات", color = PalWhiteMuted, fontSize = 9.5.sp, modifier = Modifier.padding(start = 4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    ) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val gridLinesCount = 3
                        for (j in 0..gridLinesCount) {
                            val yPos = (canvasHeight / gridLinesCount) * j
                            drawLine(
                                color = chartBorderColor,
                                start = androidx.compose.ui.geometry.Offset(0f, yPos),
                                end = androidx.compose.ui.geometry.Offset(canvasWidth, yPos),
                                strokeWidth = 1.dp.toPx(),
                                pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                            )
                        }

                        val numPoints = 7
                        val barGap = canvasWidth / (numPoints + 1)
                        val maxSales = 120000.0
                        val rawSales = listOf(30000f, 65000f, 40000f, 85000f, 95000f, 110000f, todaySalesTotal.toFloat())
                        val rawExp = listOf(12000f, 19000f, 8000f, 32000f, 15000f, 22000f, todayExpensesTotal.toFloat())

                        for (i in 0 until numPoints) {
                            val xPos = barGap * (i + 1)
                            val salesHeight = ((rawSales[i] / maxSales) * canvasHeight).coerceIn(0.0, canvasHeight.toDouble())
                            val expHeight = ((rawExp[i] / maxSales) * canvasHeight).coerceIn(0.0, canvasHeight.toDouble())

                            drawRoundRect(
                                color = chartSalesColor,
                                topLeft = androidx.compose.ui.geometry.Offset(xPos - 8.dp.toPx(), (canvasHeight - salesHeight).toFloat()),
                                size = androidx.compose.ui.geometry.Size(6.dp.toPx(), salesHeight.toFloat()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )

                            drawRoundRect(
                                color = chartExpensesColor,
                                topLeft = androidx.compose.ui.geometry.Offset(xPos + 1.dp.toPx(), (canvasHeight - expHeight).toFloat()),
                                size = androidx.compose.ui.geometry.Size(6.dp.toPx(), expHeight.toFloat()),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                            )
                        }
                    }
                }
            }

            // REAL-TIME RECENT OPERATIONS LIST
            if (recentOperations.isNotEmpty()) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
                    border = BorderStroke(1.dp, PalBorderColor),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("آخر العمليات والنشاطات الحسابية", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            if (archiveEnabled) {
                                Text(
                                    text = "عرض السجل بالكامل",
                                    color = PalSuccessNormal,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { viewModel.navigateTo(Screen.Archive) }
                                        .padding(4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            recentOperations.forEachIndexed { index, op ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .background(
                                                if (op.isExpense) PalRedNormal.copy(alpha = 0.12f) else PalSoftGreenTint,
                                                CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (op.isExpense) Icons.Default.Remove else Icons.Default.Check,
                                            contentDescription = if (op.isExpense) "سحب أو مصروف" else "إيداع أو بيع",
                                            tint = if (op.isExpense) PalRedNormal else PalSuccessNormal,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(op.title, color = PalWhitePure, fontSize = 12.5.sp, fontWeight = FontWeight.Bold)
                                        Text(op.subtitle, color = PalWhiteMuted, fontSize = 10.sp)
                                    }
                                    Text(
                                        text = (if (op.isExpense) "-" else "+") + Helpers.formatMoney(op.amount),
                                        color = if (op.isExpense) PalRedNormal else PalSuccessNormal,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                if (index < recentOperations.size - 1) {
                                    HorizontalDivider(color = PalBorderColor, thickness = 1.dp, modifier = Modifier.padding(top = 10.dp))
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(30.dp))
        }
    }

    // --- Daily Tax Input Dialog ---
    if (showTaxDialog) {
        val currentEntry = todayTaxEntry ?: TaxEntry(date = Helpers.getCurrentDate(), taxAmount = 0.0)
        var taxInput by remember { mutableStateOf(if (currentEntry.taxAmount > 0) currentEntry.taxAmount.toInt().toString() else "") }
        var stallInput by remember { mutableStateOf(if (currentEntry.stallOutflow > 0) currentEntry.stallOutflow.toInt().toString() else "") }
        var laborInput by remember { mutableStateOf(if (currentEntry.laborOutflow > 0) currentEntry.laborOutflow.toInt().toString() else "") }
        var notesInput by remember { mutableStateOf(currentEntry.notes) }

        AlertDialog(
            onDismissRequest = { showTaxDialog = false },
            title = {
                Text(
                    text = "الرسوم والمصروفات اليومية الثابتة",
                    color = PalWhitePure,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
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
                        text = "يتم تسجيل الضريبة والمصروفات الثابتة مرة واحدة يومياً وتنعكس تلقائياً في التقارير الحسابية والمالية والمطبوعات.",
                        color = PalWhiteMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = taxInput,
                        onValueChange = { taxInput = it },
                        label = { Text("ضريبة القات اليومية (ر.ي)", color = PalWhiteMuted) },
                        suffix = { Text("ر.ي", color = PalSuccessNormal, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = stallInput,
                        onValueChange = { stallInput = it },
                        label = { Text("خرج المفرش اليومي (ر.ي)", color = PalWhiteMuted) },
                        suffix = { Text("ر.ي", color = PalSuccessNormal, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = laborInput,
                        onValueChange = { laborInput = it },
                        label = { Text("خرج العمال اليومي (ر.ي)", color = PalWhiteMuted) },
                        suffix = { Text("ر.ي", color = PalSuccessNormal, fontWeight = FontWeight.Bold) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = notesInput,
                        onValueChange = { notesInput = it },
                        label = { Text("شرح / ملاحظات المصروفات", color = PalWhiteMuted) },
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
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tax = taxInput.toDoubleOrNull() ?: 0.0
                        val stall = stallInput.toDoubleOrNull() ?: 0.0
                        val labor = laborInput.toDoubleOrNull() ?: 0.0
                        viewModel.saveTodayTaxAndOutflows(tax, stall, labor, notesInput)
                        showTaxDialog = false
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
                ) {
                    Text("حفظ البيانات اليومية", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTaxDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }

    // --- Supplier Commitment Statistics Dialog ---
    if (showSupplierCommitmentDialog) {
        val dateToday = Helpers.getCurrentDate()
        val curMonthStr = dateToday.substring(0, 7)
        val curYearStr = dateToday.substring(0, 4)

        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val todayTime = try { sdf.parse(dateToday)?.time ?: 0L } catch(e: Exception) { 0L }
        val checkIsWeekly: (String) -> Boolean = { dateStr ->
            val itemDate = dateStr.take(10)
            try {
                val itemTime = sdf.parse(itemDate)?.time ?: 0L
                val diff = todayTime - itemTime
                diff in 0..(7L * 24 * 60 * 60 * 1000)
            } catch (e: Exception) {
                false
            }
        }

        val filteredCommitmentPurchases = purchases.filter {
            Helpers.matchesPeriod(
                dateStr = it.date,
                period = supplierCommitmentPeriod,
                customValue = supplierCommitmentCustomValue,
                customType = supplierCommitmentCustomType
            )
        }

        val purchasesYerToday = filteredCommitmentPurchases.sumOf { if (it.totalAmountYer > 0.0) it.totalAmountYer else if (it.currency.contains("يمني")) it.totalAmount else 0.0 }
        val purchasesSarToday = filteredCommitmentPurchases.sumOf { if (it.totalAmountSar > 0.0) it.totalAmountSar else if (it.currency.contains("سعودي")) it.totalAmount else 0.0 }
        val paidYerToday = filteredCommitmentPurchases.sumOf { if (it.totalAmountYer > 0.0) it.paidAmountYer else if (it.currency.contains("يمني")) it.paidAmount else 0.0 }
        val paidSarToday = filteredCommitmentPurchases.sumOf { if (it.totalAmountSar > 0.0) it.paidAmountSar else if (it.currency.contains("سعودي")) it.paidAmount else 0.0 }

        val totalDebitYer = filteredCommitmentPurchases.sumOf {
            val v = if (it.totalAmountYer > 0.0) it.debtRemainingYer else if (it.currency.contains("يمني")) it.debtRemaining else 0.0
            if (v > 0.0) v else 0.0
        }
        val totalDebitSar = filteredCommitmentPurchases.sumOf {
            val v = if (it.totalAmountSar > 0.0) it.debtRemainingSar else if (it.currency.contains("سعودي")) it.debtRemaining else 0.0
            if (v > 0.0) v else 0.0
        }

        val totalCreditYer = filteredCommitmentPurchases.sumOf {
            val v = if (it.totalAmountYer > 0.0) it.debtRemainingYer else if (it.currency.contains("يمني")) it.debtRemaining else 0.0
            if (v < 0.0) -v else 0.0
        }
        val totalCreditSar = filteredCommitmentPurchases.sumOf {
            val v = if (it.totalAmountSar > 0.0) it.debtRemainingSar else if (it.currency.contains("سعودي")) it.debtRemaining else 0.0
            if (v < 0.0) -v else 0.0
        }

        val supplierBalances = supplies.map { sup ->
            val supPurchases = filteredCommitmentPurchases.filter { it.supplierId == sup.id }
            val netYer = supPurchases.sumOf { if (it.totalAmountYer > 0.0) it.debtRemainingYer else if (it.currency.contains("يمني")) it.debtRemaining else 0.0 }
            val netSar = supPurchases.sumOf { if (it.totalAmountSar > 0.0) it.debtRemainingSar else if (it.currency.contains("سعودي")) it.debtRemaining else 0.0 }
            Triple(sup, netYer, netSar)
        }

        val reportMsg = buildString {
            val periodLabelText = when (supplierCommitmentPeriod) {
                "يومي" -> "اليومي ($dateToday)"
                "أسبوعي" -> if (supplierCommitmentCustomValue.startsWith("WEEK_FILTER|")) "الأسبوعي (${Helpers.formatCustomPeriodToArabic("أسبوعي", supplierCommitmentCustomValue)})" else "الأسبوعي (7 أيام الماضية)"
                "شهري" -> "الشهري ($curMonthStr)"
                "سنوي" -> "السنوي ($curYearStr)"
                else -> "المخصص لـ ($supplierCommitmentCustomValue)"
            }
            appendLine(BrandingManager.buildShareHeader("تقرير التزامات وأرصدة الموردين $periodLabelText"))
            appendLine("التاريخ والوقت: ${Helpers.getCurrentDateTime()}")
            appendLine("---------------------------------------")
            appendLine("🔺 إحصائيات حركة التوريد للفترة المحددة:")
            appendLine("  • توريد بالريال اليمني: ${Helpers.formatWithCurrency(purchasesYerToday, "ريال يمني")}")
            appendLine("  • مسدد باليمني: ${Helpers.formatWithCurrency(paidYerToday, "ريال يمني")}")
            appendLine("  • توريد بالريال السعودي: ${Helpers.formatWithCurrency(purchasesSarToday, "ريال سعودي")}")
            appendLine("  • مسدد بالسعودي: ${Helpers.formatWithCurrency(paidSarToday, "ريال سعودي")}")
            appendLine("---------------------------------------")
            appendLine("💳 الالتزامات والأرصدة القائمة للفترة:")
            appendLine("  • ديون الموردين متبقية (يمني): ${Helpers.formatWithCurrency(totalDebitYer, "ريال يمني")}")
            appendLine("  • أرصدة زائدة لنا عند الموردين (يمني): ${Helpers.formatWithCurrency(totalCreditYer, "ريال يمني")}")
            appendLine("  • ديون الموردين متبقية (سعودي): ${Helpers.formatWithCurrency(totalDebitSar, "ريال سعودي")}")
            appendLine("  • أرصدة زائدة لنا عند الموردين (سعودي): ${Helpers.formatWithCurrency(totalCreditSar, "ريال سعودي")}")
            appendLine("---------------------------------------")
            appendLine("🤝 أرصدة الحسابات والتزامات كل مورد على حدة:")
            supplierBalances.forEach { (sup, netYer, netSar) ->
                val regionSuffix = if (sup.region.isNotEmpty()) " (${sup.region})" else ""
                appendLine("👤 ${sup.name}$regionSuffix:")
                if (netYer > 0.0) {
                    appendLine("  • العملة اليمني: مدين له بقيمة ${Helpers.formatWithCurrency(netYer, "ريال يمني")} ⏳")
                } else if (netYer < 0.0) {
                    appendLine("  • العملة اليمني: رصيد فائض زائد لنا قيمة ${Helpers.formatWithCurrency(-netYer, "ريال يمني")} 🟢")
                } else {
                    appendLine("  • العملة اليمني: حساب مصفى (صفر) ✅")
                }

                if (netSar > 0.0) {
                    appendLine("  • العملة السعودي: مدين له بقيمة ${Helpers.formatWithCurrency(netSar, "ريال سعودي")} ⏳")
                } else if (netSar < 0.0) {
                    appendLine("  • العملة السعودي: رصيد فائض زائد لنا قيمة ${Helpers.formatWithCurrency(-netSar, "ريال سعودي")} 🟢")
                } else {
                    appendLine("  • العملة السعودي: حساب مصفى (صفر) ✅")
                }
            }
            appendLine("---------------------------------------")
            appendLine(com.example.util.BrandingManager.buildShareFooter())
        }

        AlertDialog(
            onDismissRequest = { showSupplierCommitmentDialog = false },
            title = {
                Text(
                    text = "التزامات وأرصدة الموردين الكلية",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "كشف مفصل بـ حركة التوريد للفترة والالتزامات والمديونيات بكل الموردين مسجلة بدقة وبـ فصل للعملات مستقل وبشكل كامل.",
                        color = PalWhiteSoft,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Right
                    )

                    // Period Selector inside Dialog
                    ScrollableTabRow(
                        selectedTabIndex = when (supplierCommitmentPeriod) {
                            "يومي" -> 0
                            "أسبوعي" -> 1
                            "شهري" -> 2
                            "سنوي" -> 3
                            "تقويم مخصص" -> 4
                            else -> 0
                        },
                        containerColor = PalBlackNormal,
                        contentColor = PalSuccessNormal,
                        edgePadding = 4.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Tab(selected = supplierCommitmentPeriod == "يومي", onClick = { supplierCommitmentPeriod = "يومي" }, text = { Text("اليومية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                        Tab(selected = supplierCommitmentPeriod == "أسبوعي", onClick = { supplierCommitmentPeriod = "أسبوعي" }, text = { Text("الأسبوعية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                        Tab(selected = supplierCommitmentPeriod == "شهري", onClick = { supplierCommitmentPeriod = "شهري" }, text = { Text("الشهرية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                        Tab(selected = supplierCommitmentPeriod == "سنوي", onClick = { supplierCommitmentPeriod = "سنوي" }, text = { Text("السنوية", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                        Tab(selected = supplierCommitmentPeriod == "تقويم مخصص", onClick = { supplierCommitmentPeriod = "تقويم مخصص" }, text = { Text("مخصص", fontSize = 11.sp, fontWeight = FontWeight.Bold) })
                    }

                    if (supplierCommitmentPeriod == "أسبوعي" || supplierCommitmentPeriod == "اسبوعي") {
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
                                            val parts = supplierWeekYearMonth.split("-")
                                            var year = parts[0].toInt()
                                            var month = parts[1].toInt()
                                            month -= 1
                                            if (month < 1) {
                                                month = 12
                                                year -= 1
                                            }
                                            supplierWeekYearMonth = String.format("%04d-%02d", year, month)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(androidx.compose.material.icons.Icons.Default.ChevronRight, contentDescription = "الشهر السابق", tint = PalIconUnified)
                                }

                                val mParts = supplierWeekYearMonth.split("-")
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
                                            val parts = supplierWeekYearMonth.split("-")
                                            var year = parts[0].toInt()
                                            var month = parts[1].toInt()
                                            month += 1
                                            if (month > 12) {
                                                month = 1
                                                year += 1
                                            }
                                            supplierWeekYearMonth = String.format("%04d-%02d", year, month)
                                        } catch (e: Exception) {}
                                    },
                                    modifier = Modifier.size(36.dp)
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
                                    val isSel = supplierWeekNum == w
                                    val weekLabels = arrayOf("", "الأول", "الثاني", "الثالث", "الرابع", "الخامس")
                                    val label = "الأسبوع ${weekLabels.getOrElse(w) { w.toString() }}"

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSel) PalGreenNormal else PalBlackDark)
                                            .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(6.dp))
                                            .clickable { supplierWeekNum = w }
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

                    if (supplierCommitmentPeriod == "تقويم مخصص") {
                        UnifiedPeriodSelector(
                            initialType = supplierCommitmentCustomType,
                            initialValue = supplierCommitmentCustomValue,
                            onPeriodChanged = { type, finalVal ->
                                supplierCommitmentCustomType = type
                                supplierCommitmentCustomValue = finalVal
                            }
                        )
                    }

                    // Card 1: Today's / Period Figures
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(8.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val periodLabelDetail = when (supplierCommitmentPeriod) {
                            "يومي" -> "اليومية ($dateToday)"
                            "أسبوعي" -> if (supplierCommitmentCustomValue.startsWith("WEEK_FILTER|")) Helpers.formatCustomPeriodToArabic("أسبوعي", supplierCommitmentCustomValue) else "الأسبوعية (7 أيام)"
                            "شهري" -> "الشهرية ($curMonthStr)"
                            "سنوي" -> "السنوية ($curYearStr)"
                            else -> "المخصصة ($supplierCommitmentCustomValue)"
                        }
                        Text("معاملات وحركة الفترة $periodLabelDetail:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("توريد يمني بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(purchasesYerToday, "ريال يمني"), color = PalWhitePure, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سداد يمني بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(paidYerToday, "ريال يمني"), color = PalSuccessNormal, fontSize = 11.sp)
                        }
                        HorizontalDivider(color = PalBorderColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("توريد سعودي بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(purchasesSarToday, "ريال سعودي"), color = PalWhitePure, fontSize = 11.sp)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("سداد سعودي بالفترة:", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(paidSarToday, "ريال سعودي"), color = PalSuccessNormal, fontSize = 11.sp)
                        }
                    }

                    // Card 2: Accumulative Figures
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PalBlackDark, RoundedCornerShape(8.dp))
                            .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text("الالتزامات والأرصدة القائمة الكلية:", color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ديون متبقية علينا (يمني):", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalDebitYer, "ريال يمني"), color = PalRedNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("أرصدة فائضة زائدة لنا (يمني):", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalCreditYer, "ريال يمني"), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        HorizontalDivider(color = PalBorderColor, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 2.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("ديون متبقية علينا (سعودي):", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalDebitSar, "ريال سعودي"), color = PalRedNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("أرصدة فائضة زائدة لنا (سعودي):", color = PalWhiteSoft, fontSize = 11.sp)
                            Text(Helpers.formatWithCurrency(totalCreditSar, "ريال سعودي"), color = PalSuccessNormal, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Section 3: Per-Supplier Detail
                    Text("رصيد وموقف حسابات الموردين فردياً:", color = PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                    if (supplierBalances.isEmpty()) {
                        Text("لا يوجد موردين مسجلين بقاعدة البيانات حالياً.", color = PalWhiteMuted, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    } else {
                        supplierBalances.forEach { (sup, netYer, netSar) ->
                            val regionSuffix = if (sup.region.isNotEmpty()) " (${sup.region})" else ""
                            val supPurchases = purchases.filter { it.supplierId == sup.id }
                            val totalYerSum = supPurchases.sumOf { if (it.totalAmountYer > 0.0) it.totalAmountYer else if (it.currency.contains("يمني")) it.totalAmount else 0.0 }
                            val paidYerSum = supPurchases.sumOf { if (it.totalAmountYer > 0.0) it.paidAmountYer else if (it.currency.contains("يمني")) it.paidAmount else 0.0 }
                            val totalSarSum = supPurchases.sumOf { if (it.totalAmountSar > 0.0) it.totalAmountSar else if (it.currency.contains("سعودي")) it.totalAmount else 0.0 }
                            val paidSarSum = supPurchases.sumOf { if (it.totalAmountSar > 0.0) it.paidAmountSar else if (it.currency.contains("سعودي")) it.paidAmount else 0.0 }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(PalBlackDark, RoundedCornerShape(8.dp))
                                    .border(0.8.dp, PalBorderColor, RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("${sup.name}$regionSuffix", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 11.sp)

                                Row(Modifier.fillMaxWidth().padding(start = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("يمني - كلي: ${Helpers.formatMoney(totalYerSum)} | مسدد: ${Helpers.formatMoney(paidYerSum)}", color = PalWhiteSoft, fontSize = 9.5.sp)
                                    val yerLabel = when {
                                        netYer > 0.0 -> "متبقي: " + Helpers.formatWithCurrency(netYer, "ريال يمني")
                                        netYer < 0.0 -> "فائض: " + Helpers.formatWithCurrency(-netYer, "ريال يمني")
                                        else -> "مصفى (0)"
                                    }
                                    val yerColor = if (netYer > 0) PalRedNormal else if (netYer < 0) PalSuccessNormal else PalWhiteMuted
                                    Text(yerLabel, color = yerColor, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                }

                                Row(Modifier.fillMaxWidth().padding(start = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("سعودي - كلي: ${Helpers.formatMoney(totalSarSum)} | مسدد: ${Helpers.formatMoney(paidSarSum)}", color = PalWhiteSoft, fontSize = 9.5.sp)
                                    val sarLabel = when {
                                        netSar > 0.0 -> "متبقي: " + Helpers.formatWithCurrency(netSar, "ريال سعودي")
                                        netSar < 0.0 -> "فائض: " + Helpers.formatWithCurrency(-netSar, "ريال سعودي")
                                        else -> "مصفى (0)"
                                    }
                                    val sarColor = if (netSar > 0) PalRedNormal else if (netSar < 0) PalSuccessNormal else PalWhiteMuted
                                    Text(sarLabel, color = sarColor, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Button(
                        onClick = {
                            Helpers.shareViaWhatsApp(context, "", reportMsg)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("واتساب", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            val headers = listOf("المورد", "المنطقة", "رصيد يمني", "رصيد سعودي")
                            val rows = supplierBalances.map { (sup, netYer, netSar) ->
                                val yerStr = when {
                                    netYer > 0.0 -> "دين: ${Helpers.formatMoney(netYer)}"
                                    netYer < 0.0 -> "لك: ${Helpers.formatMoney(-netYer)}"
                                    else -> "سليم"
                                }
                                val sarStr = when {
                                    netSar > 0.0 -> "دين: ${Helpers.formatMoney(netSar)}"
                                    netSar < 0.0 -> "لك: ${Helpers.formatMoney(-netSar)}"
                                    else -> "سليم"
                                }
                                listOf(sup.name, sup.region, yerStr + " يمني", sarStr + " سعودي")
                            }

                            val totalsMap = mapOf(
                                "إجمالي ديون الموردين (يمني)" to Helpers.formatWithCurrency(totalDebitYer, "ريال يمني"),
                                "أرصدة زائدة لنا عند الموردين (يمني)" to Helpers.formatWithCurrency(totalCreditYer, "ريال يمني"),
                                "-----------------------------" to "-----------------------------",
                                "إجمالي ديون الموردين (سعودي)" to Helpers.formatWithCurrency(totalDebitSar, "ريال سعودي"),
                                "أرصدة زائدة لنا عند الموردين (سعودي)" to Helpers.formatWithCurrency(totalCreditSar, "ريال سعودي")
                            )

                            Helpers.generatePdfAndShare(
                                context = context,
                                title = "تقرير التزامات وأرصدة الموردين الكلي",
                                headers = headers,
                                rows = rows,
                                totals = totalsMap
                            )
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalRedNormal),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ملف PDF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            showSupplierThermalPrintDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenDark),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("حراري", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(
                        onClick = { showSupplierCommitmentDialog = false }
                    ) {
                        Text("إغلاق", color = PalWhiteMuted, fontSize = 11.sp)
                    }
                }
            },
            containerColor = PalBlackNormal
        )

        if (showSupplierThermalPrintDialog) {
            com.example.ui.components.BluetoothPrintDialog(
                receiptText = reportMsg,
                onDismiss = { showSupplierThermalPrintDialog = false }
            )
        }
    }
}

@Composable
fun QuickActionItem(
    title: String,
    icon: ImageVector,
    iconBgColor: Color,
    iconColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
        border = BorderStroke(1.dp, PalBorderColor),
        modifier = modifier
            .aspectRatio(1f)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PalSoftGreenTint, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = title, tint = PalIconUnified, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(title, color = PalWhitePure, fontSize = 11.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun MenuTile(title: String, icon: ImageVector, color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
        border = BorderStroke(1.dp, PalBorderColor),
        modifier = modifier
            .height(72.dp)
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(PalSoftGreenTint, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun BottomNavigationBar(viewModel: AppViewModel) {
    val currentScreen by viewModel.currentScreen.collectAsState()
    val accountsEnabled by viewModel.accountsEnabled.collectAsState()
    val archiveEnabled by viewModel.archiveEnabled.collectAsState()
    val licenseState by viewModel.licenseState.collectAsState()
    val canShowArchive = archiveEnabled && licenseState.hasFeature(com.qatledger.licensing.LicenseFeature.ARCHIVE)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PalBlackNormal)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PalBlackNormal)
                .padding(vertical = 8.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomNavItem(
                title = "الرئيسية",
                icon = Icons.Default.Home,
                selected = currentScreen is Screen.Dashboard,
                onClick = { viewModel.navigateTo(Screen.Dashboard) }
            )
            if (accountsEnabled) {
                BottomNavItem(
                    title = "الحسابات",
                    icon = Icons.Default.Leaderboard,
                    selected = currentScreen is Screen.Accounts,
                    onClick = { viewModel.navigateTo(Screen.Accounts) }
                )
            }
            if (canShowArchive) {
                BottomNavItem(
                    title = "الأرشيف",
                    icon = Icons.Default.History,
                    selected = currentScreen is Screen.Archive,
                    onClick = { viewModel.navigateTo(Screen.Archive) }
                )
            }
            BottomNavItem(
                title = "الإعدادات",
                icon = Icons.Default.Settings,
                selected = currentScreen is Screen.Settings,
                onClick = { viewModel.navigateTo(Screen.Settings) }
            )
        }
    }
}

@Composable
fun BottomNavItem(
    title: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PalSoftGreenTint else Color.Transparent)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) PalGreenNormal.copy(alpha = 0.35f) else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (selected) PalIconUnified else PalWhiteMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = title,
                color = if (selected) PalIconUnified else PalWhiteMuted,
                fontSize = 10.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

@Composable
fun DashboardQuickActionTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    primaryColor: Color = PalIconUnified,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PalBlackDark),
        border = BorderStroke(1.dp, PalBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PalSoftGreenTint)
                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = PalWhitePure,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    color = PalWhiteMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun DashboardKpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
        border = BorderStroke(1.dp, PalBorderColor),
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = androidx.compose.material3.ripple(bounded = true, color = accentColor)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = PalWhiteMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(PalSoftGreenTint, RoundedCornerShape(9.dp))
                        .border(1.dp, PalBorderColor, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = value,
                color = PalWhitePure,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(accentColor, CircleShape)
                )
                Text(
                    text = subtitle,
                    color = PalWhiteSoft,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun AgencyAppBarTitle(screenTitle: String) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
        modifier = androidx.compose.ui.Modifier.fillMaxWidth()
    ) {
        QatLedgerAppMark(size = 32.dp, cornerRadius = 8.dp)
        androidx.compose.foundation.layout.Column(
            modifier = androidx.compose.ui.Modifier.weight(1f, fill = false)
        ) {
            Text(
                text = "${BrandingManager.appName.ifEmpty { BrandingManager.PRODUCT_NAME_AR }} • ${BrandingManager.resolvedAgencyName()}",
                color = PalWhiteMuted,
                fontWeight = FontWeight.Medium,
                fontSize = 10.5.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
            Text(
                text = screenTitle,
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
        androidx.compose.material3.Surface(
            color = PalSoftGreenTint,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(0.8.dp, PalSuccessNormal.copy(alpha = 0.25f))
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = androidx.compose.ui.Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .size(6.dp)
                        .background(PalSuccessNormal, androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = "معتمد",
                    color = PalSuccessNormal,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
