package com.qatledger.developer.ui

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.qatledger.developer.data.*
import com.qatledger.licensing.PackageId
import kotlinx.coroutines.launch

/**
 * Isolated Developer Console UI (`:developer-licensing` only — strictly absent from Customer Build).
 */
@Composable
fun DeveloperConsoleDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) { DeveloperLicenseRepository(context.applicationContext) }
    val prices by repository.observePrices().collectAsState(initial = emptyList())
    val customers by repository.observeCustomers().collectAsState(initial = emptyList())
    val licenses by repository.observeIssuedLicenses().collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(1) } // 0: Pricing, 1: Issue, 2: Registry, 3: Keys/Backup
    var inspectedLicense by remember { mutableStateOf<IssuedLicenseEntity?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Surface(
                color = DevBgDark,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp)
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "لوحة المطور — إدارة الباقات والتراخيص",
                                color = DevTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Developer Edition • توقيع Ed25519 محلي غير متصل",
                                color = DevAccent,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = DevIconMuted)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = DevSurface,
                        contentColor = DevAccent,
                        edgePadding = 4.dp
                    ) {
                        listOf("التسعير السري", "إصدار ترخيص", "السجل (${licenses.size})", "المفاتيح والنسخ").forEachIndexed { idx, title ->
                            Tab(
                                selected = selectedTab == idx,
                                onClick = { selectedTab = idx },
                                text = {
                                    Text(
                                        text = title,
                                        color = if (selectedTab == idx) DevAccent else DevTextSecondary,
                                        fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (selectedTab) {
                            0 -> DevSecretPricingTab(repository = repository, prices = prices)
                            1 -> DevIssueLicenseTab(
                                repository = repository,
                                customers = customers,
                                prices = prices,
                                onIssued = { /* stays on issue card to show QR & share */ }
                            )
                            2 -> DevRegistryTab(
                                licenses = licenses,
                                onSelectLicense = { inspectedLicense = it }
                            )
                            else -> DevKeysAndBackupTab(repository = repository)
                        }
                    }
                }
            }

            inspectedLicense?.let { currentLic ->
                val latest = licenses.firstOrNull { it.licenseId == currentLic.licenseId } ?: currentLic
                LicenseDetailActionsDialog(
                    repository = repository,
                    license = latest,
                    onDismiss = { inspectedLicense = null },
                    onReplacedWith = { newLic -> inspectedLicense = newLic }
                )
            }
        }
    }
}

@Composable
private fun DevRegistryTab(
    licenses: List<IssuedLicenseEntity>,
    onSelectLicense: (IssuedLicenseEntity) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var filterStatus by remember { mutableStateOf("ALL") } // ALL, ACTIVE, REVOKED, UNPAID

    val filtered = remember(licenses, query, filterStatus) {
        val q = query.trim().lowercase()
        licenses.filter { lic ->
            val matchesFilter = when (filterStatus) {
                "ACTIVE" -> lic.status == "ACTIVE"
                "REVOKED" -> lic.status == "REVOKED"
                "UNPAID" -> lic.paymentStatus != "PAID"
                else -> true
            }
            val matchesQuery = q.isEmpty() ||
                lic.licenseId.lowercase().contains(q) ||
                lic.agencyNameSnapshot.lowercase().contains(q) ||
                lic.ownerNameSnapshot.lowercase().contains(q) ||
                lic.phoneSnapshot.contains(q) ||
                lic.installationId.orEmpty().lowercase().contains(q)
            matchesFilter && matchesQuery
        }
    }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("ابحث بالاسم أو الهاتف أو رقم الترخيص أو المعرّف...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = DevIconMuted) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("ALL" to "الكل", "ACTIVE" to "نشط", "UNPAID" to "غير مكتمل الدفع", "REVOKED" to "ملغي").forEach { (code, lbl) ->
                FilterChip(
                    selected = filterStatus == code,
                    onClick = { filterStatus = code },
                    label = { Text(lbl, fontSize = 11.sp) }
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(filtered, key = { it.licenseId }) { lic ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = DevSurface),
                    border = BorderStroke(1.dp, DevBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectLicense(lic) }
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${lic.agencyNameSnapshot} (${lic.packageCode})", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(lic.licenseId, color = DevAccent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Text("المالك: ${lic.ownerNameSnapshot} • ${lic.phoneSnapshot}", color = DevTextSecondary, fontSize = 11.5.sp)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("النوع: ${lic.licenseType} • الانتهاء: ${formatDateShort(lic.expiresAtEpochMs)}", color = DevTextSecondary, fontSize = 11.sp)
                            Text("الحالة: ${lic.status} | السداد: ${lic.paymentStatus}", color = if (lic.status == "REVOKED") DevDanger else DevTextPrimary, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LicenseDetailActionsDialog(
    repository: DeveloperLicenseRepository,
    license: IssuedLicenseEntity,
    onDismiss: () -> Unit,
    onReplacedWith: (IssuedLicenseEntity) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val audits by repository.observeAuditsForLicense(license.licenseId).collectAsState(initial = emptyList())

    var renewDays by remember { mutableStateOf("365") }
    var newInstallId by remember { mutableStateOf(license.installationId.orEmpty()) }
    var installReason by remember { mutableStateOf("") }
    var revokeReason by remember { mutableStateOf("") }
    var paidInput by remember { mutableStateOf(license.paidAmount?.toString().orEmpty()) }
    var confirmPaid by remember { mutableStateOf(license.paymentStatus == "PAID") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = DevSurface,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(4.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("إدارة الترخيص ${license.licenseId}", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = DevIconMuted)
                    }
                }

                IssuedArtifactPreviewCard(context = context, license = license)

                HorizontalDivider(color = DevBorder)
                Text("تحديث السداد السري (المطور فقط):", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                OutlinedTextField(
                    value = paidInput,
                    onValueChange = { paidInput = it },
                    label = { Text("المبلغ المدفوع (${license.priceCurrency})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { confirmPaid = !confirmPaid }) {
                    Checkbox(checked = confirmPaid, onCheckedChange = { confirmPaid = it })
                    Text("تأكيد استلام السداد الفعلي (PAID)", color = DevTextPrimary, fontSize = 11.5.sp)
                }
                Button(
                    onClick = {
                        scope.launch {
                            repository.confirmOrUpdatePayment(license.licenseId, license.priceCharged, paidInput.toDoubleOrNull(), confirmPaid)
                            Toast.makeText(context, "تم تحديث حالة السداد", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevAccent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ السداد", color = Color.White)
                }

                HorizontalDivider(color = DevBorder)
                Text("عمليات الترخيص:", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = renewDays,
                        onValueChange = { renewDays = it.filter(Char::isDigit) },
                        label = { Text("أيام التجديد") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            scope.launch {
                                val next = repository.renewSubscription(license.licenseId, renewDays.toIntOrNull() ?: 30)
                                if (next != null) {
                                    Toast.makeText(context, "تم التجديد: ${next.licenseId}", Toast.LENGTH_SHORT).show()
                                    onReplacedWith(next)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DevAccent)
                    ) {
                        Text("تجديد", color = Color.White)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val upgraded = repository.upgradeToPerpetual(license.licenseId, PackageId.PRO)
                                if (upgraded != null) {
                                    Toast.makeText(context, "تمت الترقية إلى PRO دائم: ${upgraded.licenseId}", Toast.LENGTH_SHORT).show()
                                    onReplacedWith(upgraded)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ترقية PRO دائم", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val reissued = repository.reissueLicense(license.licenseId)
                                if (reissued != null) {
                                    Toast.makeText(context, "تمت إعادة التوقيع والإصدار", Toast.LENGTH_SHORT).show()
                                    onReplacedWith(reissued)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("إعادة إصدار", fontSize = 11.sp)
                    }
                }

                OutlinedTextField(
                    value = newInstallId,
                    onValueChange = { newInstallId = it.uppercase() },
                    label = { Text("معرّف التثبيت الجديد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = installReason,
                    onValueChange = { installReason = it },
                    label = { Text("سبب تغيير معرّف التثبيت (إلزامي)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        if (installReason.isBlank()) {
                            Toast.makeText(context, "يجب توثيق سبب التغيير أولاً", Toast.LENGTH_SHORT).show()
                            return@OutlinedButton
                        }
                        scope.launch {
                            val updated = repository.changeInstallationId(license.licenseId, newInstallId.ifBlank { null }, installReason)
                            if (updated != null) {
                                Toast.makeText(context, "تم إصدار نسخة بالمعرّف الجديد: ${updated.licenseId}", Toast.LENGTH_SHORT).show()
                                onReplacedWith(updated)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("تغيير معرّف التثبيت وتوثيق السبب")
                }

                OutlinedTextField(
                    value = revokeReason,
                    onValueChange = { revokeReason = it },
                    label = { Text("سبب الإلغاء في سجل المطور") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Button(
                    onClick = {
                        if (revokeReason.isBlank()) {
                            Toast.makeText(context, "يرجى كتابة سبب الإلغاء", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        scope.launch {
                            repository.revokeLicense(license.licenseId, revokeReason)
                            Toast.makeText(context, "تم إلغاء الترخيص في سجل المطور", Toast.LENGTH_SHORT).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DevDanger),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إلغاء الترخيص في السجل", color = Color.White)
                }

                if (audits.isNotEmpty()) {
                    HorizontalDivider(color = DevBorder)
                    Text("سجل المراجعة (Audit Trail):", color = DevTextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    audits.forEach { a ->
                        Text("• [${a.actionType}] ${a.detailsArabic}${a.reason?.let { r -> " — السبب: $r" }.orEmpty()}", color = DevTextSecondary, fontSize = 10.5.sp)
                    }
                }
            }
        }
    }
}
