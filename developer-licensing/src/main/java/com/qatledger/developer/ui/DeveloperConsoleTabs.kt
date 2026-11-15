package com.qatledger.developer.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.qatledger.developer.crypto.DeveloperKeyManager
import com.qatledger.developer.data.*
import com.qatledger.licensing.LicenseFeature
import com.qatledger.licensing.LicenseType
import com.qatledger.licensing.PackageId
import com.qatledger.licensing.QrCodeCodec
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val DevBgDark = Color(0xFF101412)
internal val DevSurface = Color(0xFF191F1C)
internal val DevBorder = Color(0xFF2B3530)
internal val DevAccent = Color(0xFF2E7D32)
internal val DevIconMuted = Color(0xFF9E9E9E)
internal val DevTextPrimary = Color(0xFFF5F7F5)
internal val DevTextSecondary = Color(0xFFB7C2BC)
internal val DevDanger = Color(0xFFD32F2F)

@Composable
internal fun DevSecretPricingTab(
    repository: DeveloperLicenseRepository,
    prices: List<PackagePriceEntity>
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "تسعير الباقات السري (محفوظ في قاعدة بيانات المطور فقط — لا يُصدر مع نسخة العميل ولا يُعرض فيها)",
            color = DevTextSecondary,
            fontSize = 11.5.sp
        )

        listOf(PackageId.BASIC, PackageId.PRO).forEach { pkg ->
            val current = prices.firstOrNull { it.packageCode == pkg.code }
            var currency by remember(current) { mutableStateOf(current?.currency ?: "ريال يمني") }
            var perp by remember(current) { mutableStateOf(current?.perpetualPrice?.toString().orEmpty()) }
            var monthly by remember(current) { mutableStateOf(current?.monthlyPrice?.toString().orEmpty()) }
            var quarterly by remember(current) { mutableStateOf(current?.quarterlyPrice?.toString().orEmpty()) }
            var yearly by remember(current) { mutableStateOf(current?.yearlyPrice?.toString().orEmpty()) }

            Card(
                colors = CardDefaults.cardColors(containerColor = DevSurface),
                border = BorderStroke(1.dp, DevBorder),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(pkg.arabicName, color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it },
                        label = { Text("عملة التسعير (يمني / سعودي / دولار)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = perp,
                            onValueChange = { perp = it },
                            label = { Text("سعر الدائم (اختياري)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = monthly,
                            onValueChange = { monthly = it },
                            label = { Text("شهري") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = quarterly,
                            onValueChange = { quarterly = it },
                            label = { Text("ربع سنوي (90 يوم)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = yearly,
                            onValueChange = { yearly = it },
                            label = { Text("سنوي (365 يوم)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                repository.saveSecretPackagePricing(
                                    packageId = pkg,
                                    currency = currency,
                                    perpetualPrice = perp.toDoubleOrNull(),
                                    monthlyPrice = monthly.toDoubleOrNull(),
                                    quarterlyPrice = quarterly.toDoubleOrNull(),
                                    yearlyPrice = yearly.toDoubleOrNull()
                                )
                                Toast.makeText(context, "تم حفظ تسعير ${pkg.code} السري بنجاح", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevAccent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("حفظ التسعير السري لـ ${pkg.code}", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
internal fun DevIssueLicenseTab(
    repository: DeveloperLicenseRepository,
    customers: List<DeveloperCustomerEntity>,
    prices: List<PackagePriceEntity>,
    onIssued: (IssuedLicenseEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedCustomerId by remember { mutableStateOf(0L) }
    var agencyName by remember { mutableStateOf("") }
    var ownerName by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var packageId by remember { mutableStateOf(PackageId.PRO) }
    var licenseType by remember { mutableStateOf(LicenseType.PERPETUAL) }
    var durationDaysStr by remember { mutableStateOf("365") }
    var installationId by remember { mutableStateOf("") }
    var priceStr by remember { mutableStateOf("") }
    var currencyStr by remember { mutableStateOf("ريال يمني") }
    var paidStr by remember { mutableStateOf("") }
    var confirmPaidChecked by remember { mutableStateOf(false) }
    var notes by remember { mutableStateOf("") }
    var issuedArtifact by remember { mutableStateOf<IssuedLicenseEntity?>(null) }

    // Pre-fill agreed price from developer's secret table ONLY if developer already entered one
    LaunchedEffect(packageId, licenseType, prices) {
        val row = prices.firstOrNull { it.packageCode == packageId.code }
        if (row != null) {
            currencyStr = row.currency
            val suggested = when {
                licenseType == LicenseType.PERPETUAL -> row.perpetualPrice
                durationDaysStr.toIntOrNull() == 30 -> row.monthlyPrice
                durationDaysStr.toIntOrNull() == 90 -> row.quarterlyPrice
                durationDaysStr.toIntOrNull() == 365 -> row.yearlyPrice
                else -> null
            }
            if (suggested != null && priceStr.isBlank()) {
                priceStr = suggested.toString()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (customers.isNotEmpty()) {
            Text("اختر عميلاً مسجلاً (أو اتركه جديداً):", color = DevTextSecondary, fontSize = 11.5.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                customers.take(4).forEach { cust ->
                    FilterChip(
                        selected = selectedCustomerId == cust.id,
                        onClick = {
                            selectedCustomerId = cust.id
                            agencyName = cust.agencyName
                            ownerName = cust.ownerName
                            phone = cust.phone
                            city = cust.city
                        },
                        label = { Text(cust.agencyName, maxLines = 1, fontSize = 11.sp) }
                    )
                }
            }
        }

        OutlinedTextField(
            value = agencyName,
            onValueChange = { agencyName = it },
            label = { Text("اسم الوكالة (إلزامي) *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = ownerName,
                onValueChange = { ownerName = it },
                label = { Text("اسم الوكيل / المالك *") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("رقم الهاتف") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Text("الباقة المطلوبة:", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            PackageId.entries.forEach { pkg ->
                FilterChip(
                    selected = packageId == pkg,
                    onClick = { packageId = pkg; priceStr = "" },
                    label = { Text(pkg.arabicName, fontSize = 11.5.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Text("نوع الترخيص:", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            LicenseType.entries.forEach { lt ->
                FilterChip(
                    selected = licenseType == lt,
                    onClick = { licenseType = lt; priceStr = "" },
                    label = { Text(lt.arabicName, fontSize = 11.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (licenseType == LicenseType.SUBSCRIPTION) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                listOf("30" to "شهر", "90" to "3 أشهر", "365" to "سنة").forEach { (d, lbl) ->
                    AssistChip(onClick = { durationDaysStr = d }, label = { Text("$lbl ($d)") })
                }
            }
            OutlinedTextField(
                value = durationDaysStr,
                onValueChange = { durationDaysStr = it.filter(Char::isDigit) },
                label = { Text("المدة بالأيام") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = installationId,
            onValueChange = { installationId = it.uppercase() },
            label = { Text("معرّف التثبيت (اختياري — مثل QAT-XXXX-XXXX-XXXX)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider(color = DevBorder)
        Text("البيانات المالية السرية (تُسجّل عند المطور فقط ولا تُضمّن في الترخيص):", color = DevTextSecondary, fontSize = 11.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = priceStr,
                onValueChange = { priceStr = it },
                label = { Text("المبلغ المتفق عليه") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = paidStr,
                onValueChange = { paidStr = it },
                label = { Text("المبلغ المدفوع") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            value = currencyStr,
            onValueChange = { currencyStr = it },
            label = { Text("العملة") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { confirmPaidChecked = !confirmPaidChecked }
        ) {
            Checkbox(checked = confirmPaidChecked, onCheckedChange = { confirmPaidChecked = it })
            Text(
                text = "أؤكد صراحةً استلام السداد واعتماد حالة الدفع (PAID)",
                color = DevTextPrimary,
                fontSize = 12.sp
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("ملاحظات الإصدار") },
            singleLine = false,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                if (agencyName.isBlank() || ownerName.isBlank()) {
                    Toast.makeText(context, "يرجى إدخال اسم الوكالة واسم المالك", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                scope.launch {
                    val custId = repository.upsertCustomer(
                        existingId = selectedCustomerId,
                        agencyName = agencyName,
                        ownerName = ownerName,
                        phone = phone,
                        city = city,
                        notes = ""
                    )
                    val issued = repository.issueNewLicense(
                        customerId = custId,
                        agencyName = agencyName,
                        ownerName = ownerName,
                        phone = phone,
                        packageId = packageId,
                        licenseType = licenseType,
                        durationDays = if (licenseType == LicenseType.SUBSCRIPTION) durationDaysStr.toIntOrNull() ?: 30 else null,
                        customFeatures = LicenseFeature.defaultFeaturesFor(packageId),
                        installationId = installationId.ifBlank { null },
                        priceCharged = priceStr.toDoubleOrNull(),
                        priceCurrency = currencyStr,
                        paidAmount = paidStr.toDoubleOrNull(),
                        requestedPaidConfirmed = confirmPaidChecked,
                        notes = notes
                    )
                    issuedArtifact = issued
                    onIssued(issued)
                    Toast.makeText(context, "تم توقيع وإصدار الترخيص ${issued.licenseId}", Toast.LENGTH_SHORT).show()
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = DevAccent),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("توقيع وإصدار الترخيص دون إنترنت (Ed25519)", color = Color.White, fontWeight = FontWeight.Bold)
        }

        issuedArtifact?.let { lic ->
            IssuedArtifactPreviewCard(context = context, license = lic)
        }
    }
}

@Composable
internal fun IssuedArtifactPreviewCard(context: Context, license: IssuedLicenseEntity) {
    val qrBitmap = remember(license.compactToken) {
        QrCodeCodec.generateQrBitmap(license.compactToken, 440)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = DevSurface),
        border = BorderStroke(1.dp, DevAccent),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "الترخيص الموقّع: ${license.licenseId} (${license.packageCode})",
                color = DevTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.5.sp
            )
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(6.dp)
                ) {
                    Image(
                        bitmap = qrBitmap.asImageBitmap(),
                        contentDescription = "QR رمز التفعيل",
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Text(
                text = license.compactToken,
                color = DevTextSecondary,
                fontSize = 10.sp,
                maxLines = 3
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        copyTextToClipboard(context, "QAT License Token", license.compactToken)
                        Toast.makeText(context, "تم نسخ كود التفعيل", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevIconMuted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("نسخ الكود", fontSize = 11.5.sp)
                }
                Button(
                    onClick = { shareQlicFile(context, license) },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccent),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("مشاركة .qlic", color = Color.White, fontSize = 11.5.sp)
                }
            }
        }
    }
}

@Composable
internal fun DevKeysAndBackupTab(repository: DeveloperLicenseRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var keys by remember { mutableStateOf(repository.keyManager.listKeys()) }
    val activeKey = remember(keys) { repository.keyManager.getActiveKey().also { keys = repository.keyManager.listKeys() } }
    var rotateConfirm by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val json = repository.exportRegistryBackupJson()
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                Toast.makeText(context, "تم تصدير النسخة الاحتياطية لسجل التراخيص بنجاح", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val txt = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                val ok = repository.importRegistryBackupJson(txt)
                Toast.makeText(
                    context,
                    if (ok) "تم استيراد سجل التراخيص والأسعار بنجاح" else "ملف النسخة الاحتياطية غير صالح",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DevSurface),
            border = BorderStroke(1.dp, DevBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مفتاح التوقيع النشط (Android Keystore)", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text("معرّف المفتاح (KeyId): ${activeKey.keyId}", color = DevTextSecondary, fontSize = 12.sp)
                Text("آلية الحماية: ${activeKey.backingMode.arabicLabel}", color = DevTextSecondary, fontSize = 11.5.sp)
                Text(
                    text = "سطر التضمين في نسخة العميل (gradle.properties):",
                    color = DevTextPrimary,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val gradleLine = "QAT_LICENSE_PUBLIC_KEYS=${activeKey.buildPublicConfigLine()}"
                Text(
                    text = gradleLine,
                    color = DevAccent,
                    fontSize = 10.5.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DevBgDark, RoundedCornerShape(8.dp))
                        .padding(8.dp)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            copyTextToClipboard(context, "QAT Public Key Config", gradleLine)
                            Toast.makeText(context, "تم نسخ المفتاح العام لتضمينه في gradle.properties", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, tint = DevIconMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("نسخ Public Key", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { rotateConfirm = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Autorenew, contentDescription = null, tint = DevIconMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تدوير المفتاح", fontSize = 11.sp)
                    }
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = DevSurface),
            border = BorderStroke(1.dp, DevBorder),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("النسخ الاحتياطي لسجل المطور والعملاء", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
                Text(
                    text = "يصدر ويستعيد الأسعار السرية والعملاء والتراخيص وسجل المراجعة دون أي مفاتيح خاصة ودون المساس ببيانات العملاء.",
                    color = DevTextSecondary,
                    fontSize = 11.5.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { exportBackupLauncher.launch("qat_developer_registry_${System.currentTimeMillis()}.json") },
                        colors = ButtonDefaults.buttonColors(containerColor = DevAccent),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileUpload, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("تصدير سجل المطور", color = Color.White, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = { importBackupLauncher.launch(arrayOf("application/json", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FileDownload, contentDescription = null, tint = DevIconMuted, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("استيراد سجل المطور", fontSize = 11.5.sp)
                    }
                }
            }
        }
    }

    if (rotateConfirm) {
        AlertDialog(
            onDismissRequest = { rotateConfirm = false },
            containerColor = DevSurface,
            title = { Text("تدوير مفتاح التوقيع (Key Rotation)", color = DevTextPrimary) },
            text = {
                Text(
                    "سيتم إنشاء زوج مفاتيح Ed25519 جديد داخل Android Keystore وتفعيله للتراخيص الجديدة. تذكّر نسخ Public Key الجديد إلى نسخة العميل.",
                    color = DevTextSecondary,
                    fontSize = 12.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        repository.keyManager.generateAndActivateNewKey()
                        keys = repository.keyManager.listKeys()
                        rotateConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccent)
                ) {
                    Text("إنشاء وتفعيل الآن", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { rotateConfirm = false }) { Text("إلغاء", color = DevTextSecondary) }
            }
        )
    }
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun shareQlicFile(context: Context, license: IssuedLicenseEntity) {
    try {
        val dir = File(context.cacheDir, "shared_licenses").apply { mkdirs() }
        val outFile = File(dir, "${license.licenseId}.qlic")
        outFile.writeText(license.qlicJson)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/octet-stream"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, "ترخيص نظام ميزان القات (${license.licenseId})\n${license.compactToken}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة ملف الترخيص"))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذر مشاركة الملف: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

internal fun formatDateShort(epochMs: Long?): String {
    if (epochMs == null) return "دائم"
    return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMs))
}
