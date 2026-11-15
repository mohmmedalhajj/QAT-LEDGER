package com.example.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.components.AgencyCurrencySettingsCard
import com.example.ui.components.LedgerPrimaryButton
import com.example.ui.components.LedgerSurfaceCard
import com.example.ui.components.QatLedgerAppMark
import com.example.ui.theme.*
import com.example.util.BrandingManager
import com.example.util.CurrencyMode
import com.example.util.MoneyMath

@Composable
fun AgencySetupScreen(viewModel: AppViewModel) {
    Scaffold(
        containerColor = PalBlackDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Institutional Software Welcome Header (distinct from merchant agency identity)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QatLedgerAppMark(size = 64.dp, cornerRadius = 16.dp)

                Text(
                    text = "إعداد هوية الوكالة المستقلة",
                    color = PalWhitePure,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 21.sp
                    ),
                    textAlign = TextAlign.Center
                )

                Text(
                    text = "أدخل بيانات وكالتك التجارية الخاصة ليتم ربطها تلقائياً بجميع الفواتير وملفات PDF والطباعة الحرارية ورسائل واتساب وSMS. الحقول الاختيارية لا تمنع إكمال الإعداد.",
                    color = PalWhiteMuted,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 620.dp)
            ) {
                AgencyProfileEditorContent(
                    viewModel = viewModel,
                    isOnboardingMode = true
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
fun AgencyProfileEditorContent(
    viewModel: AppViewModel,
    isOnboardingMode: Boolean,
    onSavedCallback: (() -> Unit)? = null
) {
    val context = LocalContext.current

    val currentAgencyName by viewModel.brandingAgencyName.collectAsState()
    val currentOwnerName by viewModel.brandingOwnerName.collectAsState()
    val currentPhone by viewModel.brandingPhone.collectAsState()
    val currentAddress by viewModel.brandingAddress.collectAsState()
    val currentAdditionalContact by viewModel.brandingAdditionalContact.collectAsState()
    val currentTagline by viewModel.brandingTagline.collectAsState()
    val currentQatTypes by viewModel.brandingQatTypes.collectAsState()
    val currentDefaultCurrency by viewModel.brandingDefaultCurrency.collectAsState()
    val currentCurrencyMode by viewModel.brandingCurrencyMode.collectAsState()
    val currentCommercialDetails by viewModel.brandingCommercialDetails.collectAsState()
    val currentLogoEnabled by viewModel.brandingLogoEnabled.collectAsState()
    val currentRate by viewModel.sarToYerExchangeRate.collectAsState()

    var agencyName by remember(currentAgencyName) { mutableStateOf(currentAgencyName) }
    var ownerName by remember(currentOwnerName) { mutableStateOf(currentOwnerName) }
    var phone by remember(currentPhone) { mutableStateOf(currentPhone) }
    var address by remember(currentAddress) { mutableStateOf(currentAddress) }
    var additionalContact by remember(currentAdditionalContact) { mutableStateOf(currentAdditionalContact) }
    var tagline by remember(currentTagline) { mutableStateOf(currentTagline) }
    var selectedCurrencyMode by remember(currentCurrencyMode) {
        mutableStateOf(CurrencyMode.fromStorageKey(currentCurrencyMode))
    }
    var defaultCurrency by remember(currentDefaultCurrency, selectedCurrencyMode) {
        mutableStateOf(
            when (selectedCurrencyMode) {
                CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
                CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
                CurrencyMode.BOTH -> if (currentDefaultCurrency.contains("سعودي")) MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
            }
        )
    }
    var commercialDetails by remember(currentCommercialDetails) { mutableStateOf(currentCommercialDetails) }
    var exchangeRateInput by remember(currentRate) {
        mutableStateOf(if (currentRate % 1.0 == 0.0) currentRate.toInt().toString() else currentRate.toString())
    }

    val qatTypesList = remember {
        mutableStateListOf<String>().apply {
            currentQatTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { add(it) }
        }
    }
    LaunchedEffect(currentQatTypes) {
        val incoming = currentQatTypes.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (incoming.isNotEmpty() && qatTypesList.isEmpty()) {
            qatTypesList.addAll(incoming)
        }
    }

    var newQatTypeInput by remember { mutableStateOf("") }
    var editingQatTypeIndex by remember { mutableStateOf<Int?>(null) }
    var editingQatTypeText by remember { mutableStateOf("") }

    var selectedLogoUri by remember { mutableStateOf<Uri?>(null) }
    var logoCleared by remember { mutableStateOf(false) }

    var validationError by remember { mutableStateOf("") }
    var showFullPreviewDialog by remember { mutableStateOf(false) }

    // Play Policy compliant zero-permission photo picker
    val pickLogoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedLogoUri = uri
            logoCleared = false
        }
    }

    val previewLogoBitmap: Bitmap? = remember(selectedLogoUri, logoCleared, currentLogoEnabled, BrandingManager.logoUpdateTrigger) {
        when {
            logoCleared -> null
            selectedLogoUri != null -> {
                try {
                    context.contentResolver.openInputStream(selectedLogoUri!!)?.use {
                        BitmapFactory.decodeStream(it)
                    }
                } catch (_: Exception) {
                    null
                }
            }
            currentLogoEnabled -> BrandingManager.getCustomLogoBitmap(context)
            else -> null
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = PalGreenNormal,
        unfocusedBorderColor = PalBorderColor,
        focusedTextColor = PalWhitePure,
        unfocusedTextColor = PalWhiteSoft,
        focusedContainerColor = PalBlackDark,
        unfocusedContainerColor = PalBlackDark
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (validationError.isNotEmpty()) {
            Surface(
                color = PalRedNormal.copy(alpha = 0.1f),
                border = BorderStroke(1.dp, PalRedNormal.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = PalRedNormal, modifier = Modifier.size(18.dp))
                    Text(validationError, color = PalRedNormal, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 1. Logo Upload & Live Preview Card
        LedgerSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "شعار الوكالة (اختياري)",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(PalBlackDark)
                            .border(1.2.dp, PalBorderColor, RoundedCornerShape(14.dp))
                            .clickable {
                                pickLogoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (previewLogoBitmap != null) {
                            Image(
                                bitmap = previewLogoBitmap.asImageBitmap(),
                                contentDescription = "شعار الوكالة",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "الشعار الافتراضي للوكالة",
                                tint = PalIconUnified,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                pickLogoLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            border = BorderStroke(1.dp, PalGreenNormal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("agency_logo_pick_btn")
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (previewLogoBitmap != null) "تغيير صورة الشعار" else "رفع صورة الشعار",
                                color = PalIconUnified,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (previewLogoBitmap != null) {
                            TextButton(
                                onClick = {
                                    selectedLogoUri = null
                                    logoCleared = true
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = PalRedNormal),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(40.dp)
                                    .testTag("agency_logo_clear_btn")
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("إزالة الشعار والعودة للرمز المحايد", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }

        // 2. Core Agency Identity & Contact Fields
        LedgerSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "البيانات الأساسية للوكالة",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )

                OutlinedTextField(
                    value = agencyName,
                    onValueChange = {
                        agencyName = it
                        if (validationError.isNotEmpty()) validationError = ""
                    },
                    label = { Text("اسم الوكالة *", color = PalWhiteMuted) },
                    placeholder = { Text("مثال: وكالة الخير لتجارة القات", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Business, contentDescription = null, tint = PalIconUnified) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_name_input")
                )

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = {
                        ownerName = it
                        if (validationError.isNotEmpty()) validationError = ""
                    },
                    label = { Text("اسم صاحب الوكالة / المدير *", color = PalWhiteMuted) },
                    placeholder = { Text("مثال: محمد علي", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = PalIconUnified) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_owner_input")
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("رقم الهاتف (اختياري)", color = PalWhiteMuted) },
                    placeholder = { Text("مثال: 777000000", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null, tint = PalIconUnified) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_phone_input")
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("العنوان / المقر (اختياري)", color = PalWhiteMuted) },
                    placeholder = { Text("مثال: صنعاء - شارع التحرير", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null, tint = PalIconUnified) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_address_input")
                )

                OutlinedTextField(
                    value = additionalContact,
                    onValueChange = { additionalContact = it },
                    label = { Text("بيانات تواصل إضافية (اختياري)", color = PalWhiteMuted) },
                    placeholder = { Text("رقم إضافي أو واتساب أو بريد إلكتروني", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.ContactPhone, contentDescription = null, tint = PalIconUnified) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_extra_contact_input")
                )

                OutlinedTextField(
                    value = tagline,
                    onValueChange = { tagline = it },
                    label = { Text("وصف أو شعار نصي للوكالة (اختياري)", color = PalWhiteMuted) },
                    placeholder = { Text("يظهر أسفل اسم الوكالة في الفواتير والتقارير", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.ShortText, contentDescription = null, tint = PalIconUnified) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_tagline_input")
                )
            }
        }

        // 3. Optional Dynamic Qat Types Manager (Add / Edit / Delete)
        LedgerSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "أنواع القات (اختياري — إضافة، تعديل، حذف)",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = "يمكنك إضافة الأصناف التي تتاجر بها وكالتك لتظهر كاختيارات سريعة في المخزون والمبيعات، أو تركها فارغة.",
                    color = PalWhiteMuted,
                    fontSize = 11.5.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = newQatTypeInput,
                        onValueChange = { newQatTypeInput = it },
                        label = { Text("اسم الصنف الجديد", color = PalWhiteMuted, fontSize = 12.sp) },
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("agency_qat_type_add_input")
                    )
                    Button(
                        onClick = {
                            val trimmed = newQatTypeInput.trim()
                            if (trimmed.isNotEmpty() && !qatTypesList.contains(trimmed)) {
                                qatTypesList.add(trimmed)
                                newQatTypeInput = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("agency_qat_type_add_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "إضافة نوع", tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("إضافة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                // Quick optional suggestions
                val quickSuggestions = listOf("عود", "فراد", "شرعي", "شامي", "بلدي")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    quickSuggestions.forEach { suggestion ->
                        val alreadyAdded = qatTypesList.contains(suggestion)
                        FilterChip(
                            selected = alreadyAdded,
                            onClick = {
                                if (alreadyAdded) {
                                    qatTypesList.remove(suggestion)
                                } else {
                                    qatTypesList.add(suggestion)
                                }
                            },
                            label = {
                                Text(
                                    text = if (alreadyAdded) "✓ $suggestion" else "+ $suggestion",
                                    fontSize = 11.sp
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PalSoftGreenTint,
                                selectedLabelColor = PalGreenDark
                            )
                        )
                    }
                }

                if (qatTypesList.isNotEmpty()) {
                    HorizontalDivider(color = PalBorderColor)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        qatTypesList.forEachIndexed { idx, typeName ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(PalBlackDark)
                                    .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = typeName,
                                    color = PalWhitePure,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.weight(1f)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            editingQatTypeIndex = idx
                                            editingQatTypeText = typeName
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.Edit, contentDescription = "تعديل نوع القات", tint = PalIconUnified, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { qatTypesList.removeAt(idx) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(Icons.Default.DeleteOutline, contentDescription = "حذف نوع القات", tint = PalRedNormal, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 4. Central Currency & Commercial Settings
        AgencyCurrencySettingsCard(
            selectedMode = selectedCurrencyMode,
            selectedDefaultCurrency = defaultCurrency,
            exchangeRateInput = exchangeRateInput,
            onModeSelected = { newMode ->
                selectedCurrencyMode = newMode
                defaultCurrency = when (newMode) {
                    CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
                    CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
                    CurrencyMode.BOTH -> if (defaultCurrency.contains("سعودي")) MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
                }
            },
            onDefaultCurrencySelected = { newDefault ->
                defaultCurrency = newDefault
            },
            onExchangeRateChange = { exchangeRateInput = it }
        )

        LedgerSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "البيانات التجارية الإضافية (اختياري)",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                OutlinedTextField(
                    value = commercialDetails,
                    onValueChange = { commercialDetails = it },
                    label = { Text("السجل التجاري أو ملاحظات تجارية (اختياري)", color = PalWhiteMuted) },
                    placeholder = { Text("مثال: سجل تجاري رقم 12345", color = PalWhiteMuted.copy(alpha = 0.6f)) },
                    leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null, tint = PalIconUnified) },
                    colors = fieldColors,
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("agency_setup_commercial_input")
                )
            }
        }

        // 5. Live Agency Identity Card Preview
        LedgerSurfaceCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "معاينة الهوية في الفواتير والرسائل",
                        color = PalWhitePure,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp
                    )
                    TextButton(
                        onClick = { showFullPreviewDialog = true },
                        modifier = Modifier.testTag("agency_identity_preview_btn")
                    ) {
                        Icon(Icons.Default.Visibility, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("معاينة تفصيلية", color = PalIconUnified, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Surface(
                    color = PalBlackDark,
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(PalSoftGreenTint),
                            contentAlignment = Alignment.Center
                        ) {
                            if (previewLogoBitmap != null) {
                                Image(
                                    bitmap = previewLogoBitmap.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Storefront, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(26.dp))
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = agencyName.trim().ifEmpty { "اسم الوكالة سيظهر هنا" },
                                color = PalWhitePure,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            if (tagline.trim().isNotEmpty()) {
                                Text(text = tagline.trim(), color = PalGreenNormal, fontSize = 11.5.sp)
                            }
                            if (ownerName.trim().isNotEmpty()) {
                                Text(text = "المالك / الوكيل: ${ownerName.trim()}", color = PalWhiteSoft, fontSize = 11.5.sp)
                            }
                            val contactLine = listOfNotNull(
                                phone.trim().takeIf { it.isNotEmpty() },
                                address.trim().takeIf { it.isNotEmpty() }
                            ).joinToString(" • ")
                            if (contactLine.isNotEmpty()) {
                                Text(text = contactLine, color = PalWhiteMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }

        // 6. Save Action Button
        LedgerPrimaryButton(
            text = if (isOnboardingMode) "حفظ الهوية والمتابعة" else "حفظ تعديلات هوية الوكالة",
            icon = Icons.Default.CheckCircle,
            onClick = {
                val trimmedAgency = agencyName.trim()
                val trimmedOwner = ownerName.trim()
                if (trimmedAgency.isEmpty()) {
                    validationError = "يرجى إدخال اسم الوكالة أولاً لإكمال الإعداد."
                    return@LedgerPrimaryButton
                }
                if (trimmedOwner.isEmpty()) {
                    validationError = "يرجى إدخال اسم صاحب الوكالة أولاً لإكمال الإعداد."
                    return@LedgerPrimaryButton
                }

                val parsedRate = MoneyMath.parseDoubleOrNull(exchangeRateInput)
                if (parsedRate != null && parsedRate > 0.0) {
                    viewModel.updateExchangeRate(parsedRate)
                }

                val logoStream = if (selectedLogoUri != null && !logoCleared) {
                    try {
                        context.contentResolver.openInputStream(selectedLogoUri!!)
                    } catch (_: Exception) {
                        null
                    }
                } else null

                viewModel.saveAgencyProfile(
                    agencyName = trimmedAgency,
                    ownerName = trimmedOwner,
                    phone = phone,
                    address = address,
                    additionalContact = additionalContact,
                    tagline = tagline,
                    qatTypes = qatTypesList.joinToString(", "),
                    defaultCurrency = defaultCurrency,
                    currencyMode = selectedCurrencyMode.storageKey,
                    commercialDetails = commercialDetails,
                    logoStream = logoStream,
                    clearLogo = logoCleared,
                    markConfigured = true,
                    navigateNextAfterSetup = isOnboardingMode,
                    showSystemToast = false,
                    onComplete = {
                        selectedLogoUri = null
                        logoCleared = false
                        Toast.makeText(
                            context,
                            if (isOnboardingMode) "تم حفظ هوية الوكالة بنجاح" else "تم تحديث بيانات هوية الوكالة بنجاح",
                            Toast.LENGTH_SHORT
                        ).show()
                        onSavedCallback?.invoke()
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("agency_setup_save_btn")
        )
    }

    // Edit Qat Type Dialog
    if (editingQatTypeIndex != null) {
        AlertDialog(
            onDismissRequest = { editingQatTypeIndex = null },
            containerColor = PalBlackNormal,
            title = {
                Text(
                    text = "تعديل اسم صنف القات",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                OutlinedTextField(
                    value = editingQatTypeText,
                    onValueChange = { editingQatTypeText = it },
                    label = { Text("الاسم الجديد", color = PalWhiteMuted) },
                    colors = fieldColors,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val idx = editingQatTypeIndex
                        val updated = editingQatTypeText.trim()
                        if (idx != null && idx in qatTypesList.indices && updated.isNotEmpty()) {
                            qatTypesList[idx] = updated
                        }
                        editingQatTypeIndex = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
                ) {
                    Text("حفظ", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingQatTypeIndex = null }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            }
        )
    }

    // Full Preview Modal
    if (showFullPreviewDialog) {
        val sampleHeader = buildString {
            appendLine(agencyName.trim().ifEmpty { "اسم الوكالة" })
            if (tagline.trim().isNotEmpty()) appendLine(tagline.trim())
            if (ownerName.trim().isNotEmpty()) appendLine("صاحب الوكالة: ${ownerName.trim()}")
            if (phone.trim().isNotEmpty()) appendLine("هاتف: ${phone.trim()}")
            if (address.trim().isNotEmpty()) appendLine("العنوان: ${address.trim()}")
            if (additionalContact.trim().isNotEmpty()) appendLine(additionalContact.trim())
            if (commercialDetails.trim().isNotEmpty()) appendLine(commercialDetails.trim())
            appendLine("العملة الافتراضية: $defaultCurrency")
            if (qatTypesList.isNotEmpty()) appendLine("الأصناف: ${qatTypesList.joinToString("، ")}")
        }.trimEnd()

        AlertDialog(
            onDismissRequest = { showFullPreviewDialog = false },
            containerColor = PalBlackNormal,
            title = {
                Text(
                    text = "معاينة ترويسة المستندات والرسائل",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(
                        color = PalBlackDark,
                        border = BorderStroke(1.dp, PalBorderColor),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sampleHeader,
                            color = PalWhiteSoft,
                            fontSize = 12.5.sp,
                            lineHeight = 20.sp,
                            textAlign = TextAlign.Right,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Text(
                        text = "سيتم اعتماد هذه الترويسة والشعار تلقائياً في فواتير المبيعات، تقارير PDF، الطباعة الحرارية، ومشاركات واتساب وSMS.",
                        color = PalWhiteMuted,
                        fontSize = 11.5.sp,
                        textAlign = TextAlign.Right
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showFullPreviewDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
                ) {
                    Text("إغلاق", color = Color.White)
                }
            }
        )
    }
}
