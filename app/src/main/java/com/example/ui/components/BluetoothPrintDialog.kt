package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.util.BluetoothPrinterManager
import com.example.util.Helpers
import com.example.ui.theme.*

@Composable
fun BluetoothPrintDialog(
    receiptText: String,
    onDismiss: () -> Unit,
    onPrintSuccess: () -> Unit = {}
) {
    val context = LocalContext.current
    val printerManager = BluetoothPrinterManager.getInstance(context)
    
    var hasPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            results[Manifest.permission.BLUETOOTH_CONNECT] == true
        } else {
            results[Manifest.permission.BLUETOOTH] == true
        }
        hasPermission = granted
        if (!granted) {
            Toast.makeText(context, "يرجى منح صلاحية البلوتوث للاتصال بالطابعة الحرارية", Toast.LENGTH_LONG).show()
        }
    }

    // Real paired devices state (never populated with fake MAC addresses)
    var devices by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    var bluetoothStatusMsg by remember { mutableStateOf("") }
    var isPrinting by remember { mutableStateOf(false) }
    var selectedPaperMm by remember { mutableIntStateOf(printerManager.currentPrinterSize) }

    fun loadDevices() {
        if (!hasPermission) {
            devices = emptyList()
            bluetoothStatusMsg = "يرجى منح صلاحية البلوتوث أولاً للبحث عن الطابعات المقترنة."
            return
        }
        val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            devices = emptyList()
            bluetoothStatusMsg = "هذا الجهاز لا يدعم البلوتوث أو لا يتوفر محول بلوتوث فعلي."
            return
        }
        if (!bluetoothAdapter.isEnabled) {
            devices = emptyList()
            bluetoothStatusMsg = "البلوتوث متوقف حالياً. قم بتفعيل البلوتوث واقتران الطابعة من إعدادات الجهاز، أو استخدم مشاركة النص أدناه."
            return
        }

        val bondedList = printerManager.getBondedPrinters()
        devices = bondedList
        bluetoothStatusMsg = if (bondedList.isEmpty()) {
            "لا توجد طابعات أو أجهزة بلوتوث مقترنة في إعدادات الهاتف حالياً."
        } else {
            ""
        }
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            loadDevices()
        } else {
            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
            } else {
                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
            }
            launcher.launch(perms)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!isPrinting) onDismiss() },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Bluetooth, contentDescription = "البلوتوث", tint = PalIconUnified)
                Text(
                    text = "الطباعة الحرارية والمعاينة",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Paper Width Selector (58mm vs 80mm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "مقاس الورق:",
                        color = PalWhiteSoft,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    listOf(58 to "58mm (32 عمود)", 80 to "80mm (48 عمود)").forEach { (mmVal, label) ->
                        val isSel = selectedPaperMm == mmVal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) PalGreenNormal else PalBlackDark)
                                .border(1.dp, if (isSel) PalGreenNormal else PalBorderColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedPaperMm = mmVal
                                    printerManager.savePrinterConfig(mmVal, printerManager.connectedDeviceAddress)
                                }
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (isSel) Color.White else PalWhiteMuted,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Text(
                    text = "معاينة السند قبل الطباعة:",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .background(Color(0xFFFCFBF9), RoundedCornerShape(8.dp))
                        .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                        .padding(2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        NativeReceiptPreview(receiptText = receiptText)
                    }
                }

                HorizontalDivider(color = PalBorderColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "الطابعات المقترنة فعلياً:",
                        color = PalWhiteSoft,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { loadDevices() },
                        enabled = !isPrinting,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "تحديث الطابعات", tint = PalIconUnified, modifier = Modifier.size(18.dp))
                    }
                }

                if (!hasPermission) {
                    Button(
                        onClick = {
                            val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
                            } else {
                                arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
                            }
                            launcher.launch(perms)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                    ) {
                        Text("منح صلاحية البلوتوث", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                } else if (devices.isEmpty()) {
                    Surface(
                        color = PalBlackDark,
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = bluetoothStatusMsg.ifBlank { "لا توجد أجهزة مقترنة حالياً." },
                            color = PalWhiteMuted,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(10.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.heightIn(max = 170.dp)
                    ) {
                        items(devices) { pair ->
                            val (name, address) = pair
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isPrinting) {
                                        val (deviceType, isPrinter) = printerManager.getDeviceTypeNameAndIsPrinter(name, address)
                                        if (!isPrinter) {
                                            Toast.makeText(
                                                context,
                                                "الجهاز المختار هو ($deviceType) وليس طابعة حرارية مدعومة.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            isPrinting = true
                                            printerManager.savePrinterConfig(selectedPaperMm, address)
                                            printerManager.connectPrinter(name, address) { connected ->
                                                if (!connected) {
                                                    isPrinting = false
                                                    Toast.makeText(context, "تعذر إعداد الاتصال بالطابعة.", Toast.LENGTH_SHORT).show()
                                                } else {
                                                    printerManager.printEscPosReceipt(receiptText, address) { success, message ->
                                                        Handler(Looper.getMainLooper()).post {
                                                            isPrinting = false
                                                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                                            if (success) {
                                                                onPrintSuccess()
                                                                onDismiss()
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    },
                                colors = CardDefaults.cardColors(containerColor = PalBlackDark),
                                border = BorderStroke(1.dp, PalBorderColor)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Print, contentDescription = "طابعة", tint = PalIconUnified)
                                    Column(
                                        horizontalAlignment = Alignment.End,
                                        modifier = Modifier.weight(1f).padding(end = 10.dp)
                                    ) {
                                        Text(name, color = PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text(address, color = PalWhiteMuted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                if (isPrinting) {
                    LinearProgressIndicator(
                        color = PalGreenNormal,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                OutlinedButton(
                    onClick = {
                        printerManager.virtualPrint(receiptText) { formatted ->
                            Helpers.shareText(context, formatted)
                        }
                    },
                    enabled = !isPrinting,
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("مشاركة / حفظ نص السند (${selectedPaperMm}mm)", color = PalWhitePure, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { if (!isPrinting) onDismiss() }) {
                Text("إغلاق", color = PalWhiteMuted)
            }
        },
        containerColor = PalBlackNormal
    )
}

@Composable
fun NativeReceiptPreview(receiptText: String) {
    val context = LocalContext.current
    val printerManager = remember(context) { BluetoothPrinterManager.getInstance(context) }
    val parsed = remember(receiptText) { printerManager.parseThermalReceipt(receiptText) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFFCFBF9))
            .padding(12.dp)
    ) {
        // 1. Titles block (centered, elegant arabic styling)
        for (line in parsed.titleLines) {
            Text(
                text = line,
                color = Color(0xFF1C1B19),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(10.dp))
        
        // 2. Metadata details (True grid/card of key-values)
        if (parsed.metaFields.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFE2DDD5), RoundedCornerShape(6.dp))
                    .background(Color(0xFFFBF9F6))
                    .padding(8.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (field in parsed.metaFields) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = field.second,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1C1B19)
                            )
                            Text(
                                text = "${field.first} :",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF6C6A67)
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 3. Items Table (True Table Layout with headers, columns and grids)
        if (parsed.itemRows.isNotEmpty()) {
            Text(
                text = "تفاصيل الأصناف المبيعة والمشتراة:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5C5B59),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            
            // Outer Border
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFDCD6CD), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1EDE4))
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الإجمالي",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4C4B49),
                        modifier = Modifier.weight(1.2f),
                        textAlign = TextAlign.Start
                    )
                    Text(
                        text = "الكمية",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4C4B49),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "اسم الصنف والبيان",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4C4B49),
                        modifier = Modifier.weight(2.5f),
                        textAlign = TextAlign.End
                    )
                }
                
                // Dividers and Items
                for ((index, item) in parsed.itemRows.withIndex()) {
                    HorizontalDivider(color = Color(0xFFEFECE6))
                    val bg = if (index % 2 == 0) Color.White else Color(0xFFFCFBF9)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(vertical = 6.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total
                        Text(
                            text = item.third,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C1B19),
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.Start
                        )
                        // Qty
                        Text(
                            text = item.second,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF4C4B49),
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        // Item name
                        Text(
                            text = item.first,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1C1B19),
                            modifier = Modifier.weight(2.5f),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 4. Statement Rows / Accounting Movements (True Table Layout)
        if (parsed.statementRows.isNotEmpty()) {
            Text(
                text = "كشف الحساب والعمليات الجارية:",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF5C5B59),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            )
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFFDCD6CD), RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF1EDE4))
                        .padding(vertical = 6.dp, horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "الحركة وتفاصيل العملية المالية",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4C4B49),
                        modifier = Modifier.weight(2.5f),
                        textAlign = TextAlign.End
                    )
                    Text(
                        text = "تاريخ القيد",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF4C4B49),
                        modifier = Modifier.weight(1.2f),
                        textAlign = TextAlign.Start
                    )
                }
                
                // Rows
                for ((index, row) in parsed.statementRows.withIndex()) {
                    HorizontalDivider(color = Color(0xFFEFECE6))
                    val bg = if (index % 2 == 0) Color.White else Color(0xFFFCFBF9)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(vertical = 8.dp, horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        val date = row[0]
                        val details = row[1]
                        
                        // Details Column on the right
                        Column(
                            modifier = Modifier.weight(2.5f),
                            horizontalAlignment = Alignment.End
                        ) {
                            val detailLines = details.split("\n")
                            for ((i, line) in detailLines.withIndex()) {
                                if (i == 0) {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF1C1B19),
                                        textAlign = TextAlign.End
                                    )
                                } else {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF5C5B59),
                                        textAlign = TextAlign.End,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        // Date Column on the left
                        Text(
                            text = date.ifEmpty { "-" },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6C6A67),
                            modifier = Modifier.weight(1.2f),
                            textAlign = TextAlign.Start
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        // 5. Totals Fields (Grand Summary Card Box)
        if (parsed.totalsFields.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.2.dp, Color(0xFF4A4947), RoundedCornerShape(6.dp))
                    .background(Color(0xFFFAF6F0))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((index, field) in parsed.totalsFields.withIndex()) {
                        if (index > 0) {
                            HorizontalDivider(color = Color(0xFFE6E1D8), thickness = 0.5.dp)
                        }
                        
                        val key = field.first
                        val value = field.second
                        val isGrandTotal = key.contains("إجمالي") || key.contains("الصافي") || key.contains("الرصيد الكلي") || key.contains("المتبقي")
                        val textColor = if (isGrandTotal) Color(0xFF1E4620) else Color(0xFF1C1B19)
                        val weight = if (isGrandTotal) FontWeight.Bold else FontWeight.SemiBold
                        val size = if (isGrandTotal) 14.sp else 12.sp
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = value,
                                fontSize = size,
                                fontWeight = weight,
                                color = textColor
                            )
                            Text(
                                text = key,
                                fontSize = size,
                                fontWeight = weight,
                                color = textColor
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 6. Signatures and Footers
        if (parsed.footerLines.isNotEmpty()) {
            val hasSignature = parsed.footerLines.any { it.contains("توقيع") }
            if (hasSignature) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "توقيع المستلم",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5C5B59)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = ".......................",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "توقيع المحاسب",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF5C5B59)
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = ".......................",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
            
            for (line in parsed.footerLines) {
                if (!line.contains("توقيع")) {
                    Text(
                        text = line,
                        color = Color(0xFF5C5B59),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}
