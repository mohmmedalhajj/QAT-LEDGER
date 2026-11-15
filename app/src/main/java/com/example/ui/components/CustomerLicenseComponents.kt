package com.example.ui.components

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.qatledger.licensing.LicenseFeature
import com.qatledger.licensing.LicenseStatus
import com.qatledger.licensing.LicenseVerificationResult
import com.qatledger.licensing.PackageId
import com.qatledger.licensing.QrCodeCodec
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

@Composable
fun LicenseStatusSettingsCard(
    viewModel: AppViewModel,
    onOpenActivationDialog: () -> Unit
) {
    val context = LocalContext.current
    val licenseState by viewModel.licenseState.collectAsState()
    val payload = licenseState.activePayload

    Card(
        colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
        border = BorderStroke(
            width = 1.dp,
            color = if (licenseState.isProActive) PalGreenNormal.copy(alpha = 0.65f) else PalBorderColor
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("customer_license_status_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        imageVector = if (licenseState.isProActive) Icons.Default.VerifiedUser else Icons.Default.WorkspacePremium,
                        contentDescription = null,
                        tint = PalIconUnified,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "حالة الترخيص والباقة (بدون إنترنت)",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (licenseState.effectivePackage == PackageId.PRO) PalSoftGreenTint else PalBlackDark,
                    border = BorderStroke(1.dp, if (licenseState.isProActive) PalGreenNormal else PalBorderColor)
                ) {
                    Text(
                        text = licenseState.effectivePackage.code,
                        color = if (licenseState.isProActive) PalGreenNormal else PalWhiteSoft,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider(color = PalBorderColor)

            Text(
                text = licenseState.statusMessageArabic,
                color = if (licenseState.status == LicenseStatus.CLOCK_TAMPERED || licenseState.status == LicenseStatus.EXPIRED) {
                    PalRedNormal
                } else {
                    PalWhiteSoft
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PalBlackDark, RoundedCornerShape(10.dp))
                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("معرّف التثبيت الخاص بنسختك:", color = PalWhiteMuted, fontSize = 10.5.sp)
                    Text(
                        text = licenseState.localInstallationId,
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        modifier = Modifier.testTag("customer_installation_id_text")
                    )
                }
                IconButton(
                    onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("QAT Installation ID", licenseState.localInstallationId))
                        Toast.makeText(context, "تم نسخ معرّف التثبيت", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "نسخ معرّف التثبيت", tint = PalIconUnified)
                }
            }

            if (payload != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("رقم الترخيص: ${payload.licenseId}", color = PalWhiteSoft, fontSize = 11.5.sp)
                    if (payload.agencyName.isNotBlank()) {
                        Text("الجهة المرخّصة: ${payload.agencyName}", color = PalWhiteSoft, fontSize = 11.5.sp)
                    }
                    Text("نوع الترخيص: ${payload.licenseType.arabicName}", color = PalWhiteSoft, fontSize = 11.5.sp)
                    payload.expiresAtEpochMs?.let { exp ->
                        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(exp))
                        val rem = licenseState.remainingDays?.let { " (متبقي $it يوم)" }.orEmpty()
                        Text("تاريخ الانتهاء: $dateStr$rem", color = PalWhiteMuted, fontSize = 11.sp)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val reportsOk = licenseState.hasFeature(LicenseFeature.REPORTS)
                val archiveOk = licenseState.hasFeature(LicenseFeature.ARCHIVE)
                FeatureEntitlementBadge("التقارير والأرباح", reportsOk, Modifier.weight(1f))
                FeatureEntitlementBadge("الأرشيف الشامل", archiveOk, Modifier.weight(1f))
            }

            Button(
                onClick = onOpenActivationDialog,
                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("open_customer_activation_btn")
            ) {
                Icon(Icons.Default.VpnKey, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (licenseState.isProActive) "تحديث / استبدال الترخيص (بدون إنترنت)" else "تفعيل أو ترقية إلى PRO (بدون إنترنت)",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }
        }
    }
}

@Composable
private fun FeatureEntitlementBadge(
    label: String,
    unlocked: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = PalBlackDark,
        border = BorderStroke(1.dp, if (unlocked) PalGreenNormal.copy(alpha = 0.55f) else PalBorderColor),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = if (unlocked) Icons.Default.CheckCircle else Icons.Default.Lock,
                contentDescription = null,
                tint = PalIconUnified,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = if (unlocked) PalWhitePure else PalWhiteMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
fun CustomerLicenseActivationDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val licenseState by viewModel.licenseState.collectAsState()
    var rawInput by remember { mutableStateOf("") }
    var lastResult by remember { mutableStateOf<LicenseVerificationResult?>(null) }
    var showQrScanner by remember { mutableStateOf(false) }

    val qlicFilePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val content = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty()
            if (content.isNotBlank()) {
                rawInput = content
                lastResult = viewModel.activateLicenseOffline(content)
            } else {
                Toast.makeText(context, "تعذر قراءة ملف الترخيص المختار", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showQrScanner = true
        } else {
            Toast.makeText(context, "يتطلب مسح QR إذن الكاميرا، يمكنك لصق الكود أو استيراد ملف .qlic مباشرة.", Toast.LENGTH_LONG).show()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PalBlackNormal,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, PalBorderColor),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "تفعيل الترخيص دون إنترنت",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "إغلاق", tint = PalIconUnified)
                    }
                }

                Text(
                    text = "معرّف التثبيت: ${licenseState.localInstallationId}",
                    color = PalWhiteMuted,
                    fontSize = 11.5.sp
                )

                OutlinedTextField(
                    value = rawInput,
                    onValueChange = { rawInput = it },
                    label = { Text("الصق كود التفعيل (QL1...) أو محتوى .qlic", color = PalWhiteMuted, fontSize = 11.5.sp) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedContainerColor = PalBlackDark,
                        unfocusedContainerColor = PalBlackDark
                    ),
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("customer_activation_code_input")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { qlicFilePicker.launch(arrayOf("*/*")) },
                        border = BorderStroke(1.dp, PalBorderColor),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.UploadFile, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("استيراد .qlic", color = PalWhitePure, fontSize = 11.5.sp)
                    }
                    OutlinedButton(
                        onClick = {
                            val hasCam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                PackageManager.PERMISSION_GRANTED
                            if (hasCam) showQrScanner = true else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        border = BorderStroke(1.dp, PalBorderColor),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("مسح QR", color = PalWhitePure, fontSize = 11.5.sp)
                    }
                }

                Button(
                    onClick = {
                        lastResult = viewModel.activateLicenseOffline(rawInput)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("customer_verify_activate_btn")
                ) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("التحقق وتفعيل الباقة", color = Color.White, fontWeight = FontWeight.Bold)
                }

                lastResult?.let { res ->
                    Surface(
                        color = if (res.isValid) PalSoftGreenTint else PalRedNormal.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, if (res.isValid) PalGreenNormal else PalRedNormal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = res.reasonArabic,
                            color = if (res.isValid) PalWhitePure else PalRedNormal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }
        }
    }

    if (showQrScanner) {
        QrCameraScannerDialog(
            onQrDetected = { scannedText ->
                showQrScanner = false
                rawInput = scannedText
                lastResult = viewModel.activateLicenseOffline(scannedText)
            },
            onDismiss = { showQrScanner = false }
        )
    }
}

@Composable
private fun QrCameraScannerDialog(
    onQrDetected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = PalBlackNormal,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("وجّه الكاميرا نحو رمز QR للترخيص", color = PalWhitePure, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx)
                            val future = ProcessCameraProvider.getInstance(ctx)
                            future.addListener({
                                runCatching {
                                    val cameraProvider = future.get()
                                    val preview = Preview.Builder().build().also {
                                        it.surfaceProvider = previewView.surfaceProvider
                                    }
                                    val analysis = ImageAnalysis.Builder()
                                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                        .build()
                                        .also { ia ->
                                            ia.setAnalyzer(executor) { image ->
                                                try {
                                                    val decoded = QrCodeCodec.decodeFromImageProxy(image)
                                                    if (!decoded.isNullOrBlank() && !handled) {
                                                        handled = true
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrDetected(decoded)
                                                        }
                                                    }
                                                } finally {
                                                    image.close()
                                                }
                                            }
                                        }
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        analysis
                                    )
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("إغلاق الكاميرا", color = PalWhiteMuted)
                }
            }
        }
    }
}

@Composable
fun ProUpgradeRequiredDialog(
    feature: LicenseFeature,
    onActivateClick: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PalBlackNormal,
        icon = {
            Icon(Icons.Default.Lock, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(32.dp))
        },
        title = {
            Text(
                text = "ميزة ضمن الباقة الاحترافية (PRO)",
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Text(
                text = "يتطلب الوصول إلى «${feature.arabicTitle}» تفعيل ترخيص QAT LEDGER PRO. جميع بياناتك وسجلاتك وعمليات الباقة الأساسية ونسخك الاحتياطي تظل متاحة وآمنة بالكامل.",
                color = PalWhiteSoft,
                fontSize = 12.5.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    onActivateClick()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
            ) {
                Text("تفعيل رمز / ملف الترخيص", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("لاحقاً", color = PalWhiteMuted)
            }
        }
    )
}

@Composable
fun ProLockedScreenPlaceholder(
    feature: LicenseFeature,
    viewModel: AppViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PalBlackDark)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = feature.arabicTitle,
            tint = PalIconUnified,
            modifier = Modifier.size(56.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "${feature.arabicTitle} — ميزة PRO",
            color = PalWhitePure,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "هذه الشاشة مفعّلة في الباقة الاحترافية فقط. سجلاتك محفوظة بأمان ويمكنك التفعيل دون إنترنت في أي وقت.",
            color = PalWhiteSoft,
            fontSize = 13.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = { viewModel.openCustomerActivationDialog() },
            colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Text("تفعيل الباقة الاحترافية الآن", color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = { viewModel.navigateToDashboard() },
            border = BorderStroke(1.dp, PalBorderColor),
            modifier = Modifier.fillMaxWidth().height(48.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = PalIconUnified)
            Spacer(Modifier.width(6.dp))
            Text("العودة إلى لوحة الجلسة", color = PalWhiteSoft)
        }
    }
}
