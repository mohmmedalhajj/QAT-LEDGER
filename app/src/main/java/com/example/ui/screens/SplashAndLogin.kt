package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.licensing.EditionCapabilities
import com.example.ui.AppViewModel
import com.example.ui.components.LedgerPrimaryButton
import com.example.ui.components.LedgerSurfaceCard
import com.example.ui.components.QatLedgerAppMark
import com.example.ui.theme.*
import com.example.util.BrandingManager
import com.example.util.LauncherIdentityManager

@Composable
fun SplashScreen(viewModel: AppViewModel) {
    val brandingAppName by viewModel.brandingAppName.collectAsState()
    val brandingAgencyName by viewModel.brandingAgencyName.collectAsState()
    val brandingOwnerName by viewModel.brandingOwnerName.collectAsState()

    val infiniteTransition = rememberInfiniteTransition(label = "splash_float")
    val gentleFloat by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "float_y"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PalGreenDark),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp)
        ) {
            QatLedgerAppMark(
                size = 116.dp,
                cornerRadius = 26.dp,
                modifier = Modifier.offset(y = gentleFloat.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = brandingAppName.ifEmpty { BrandingManager.PRODUCT_NAME_AR },
                color = Color.White,
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 30.sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = BrandingManager.PRODUCT_NAME_EN,
                color = PalGoldMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(top = 4.dp)
            )

            Text(
                text = BrandingManager.PRODUCT_TAGLINE,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.5.sp
                ),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp)
            )

            Spacer(modifier = Modifier.height(38.dp))

            CircularProgressIndicator(
                color = PalGoldMuted,
                strokeWidth = 2.8.dp,
                modifier = Modifier.size(32.dp)
            )

            if (brandingAgencyName.isNotBlank() || brandingOwnerName.isNotBlank()) {
                Spacer(modifier = Modifier.height(28.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                ) {
                    val infoLine = listOfNotNull(
                        brandingAgencyName.trim().takeIf { it.isNotEmpty() },
                        brandingOwnerName.trim().takeIf { it.isNotEmpty() }?.let { "الوكيل: $it" }
                    ).joinToString(" • ")
                    Text(
                        text = infoLine,
                        color = Color.White.copy(alpha = 0.86f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LoginScreen(viewModel: AppViewModel) {
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf("") }
    val isPassVisible by viewModel.isPasswordVisible.collectAsState()

    var logoClickCount by remember { mutableStateOf(0) }
    var showSecretPasswordDialog by remember { mutableStateOf(false) }
    var showBrandingDialog by remember { mutableStateOf(false) }
    var showQatTypesDialog by remember { mutableStateOf(false) }

    val brandingAppName by viewModel.brandingAppName.collectAsState()
    val brandingAgencyName by viewModel.brandingAgencyName.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PalBlackDark)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.Center
        ) {
            // Preserved 3-consecutive-tap hidden identity access on the login emblem
            QatLedgerAppMark(
                size = 96.dp,
                cornerRadius = 22.dp,
                modifier = Modifier
                    .testTag("login_logo_secret_trigger")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        logoClickCount++
                        if (logoClickCount >= 3) {
                            logoClickCount = 0
                            showQatTypesDialog = true
                        }
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = brandingAppName.ifEmpty { BrandingManager.PRODUCT_NAME_AR },
                color = PalWhitePure,
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                ),
                textAlign = TextAlign.Center
            )

            Text(
                text = BrandingManager.PRODUCT_NAME_EN,
                color = PalIconUnified,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            Text(
                text = BrandingManager.PRODUCT_TAGLINE,
                color = PalWhiteMuted,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            if (brandingAgencyName.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = PalSoftGreenTint,
                    border = BorderStroke(1.dp, PalBorderColor)
                ) {
                    Text(
                        text = brandingAgencyName,
                        color = PalGreenDark,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            LedgerSurfaceCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "تسجيل الدخول إلى النظام",
                        color = PalWhitePure,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Right
                    )

                    if (errorMessage.isNotEmpty()) {
                        Surface(
                            color = PalRedNormal.copy(alpha = 0.1f),
                            border = BorderStroke(1.dp, PalRedNormal.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = PalRedNormal,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = errorMessage,
                                    color = PalRedNormal,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        readOnly = true,
                        label = { Text("اسم المستخدم", color = PalWhiteMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Person, contentDescription = null, tint = PalIconUnified)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_username_input")
                    )

                    OutlinedTextField(
                        value = password,
                        onValueChange = {
                            password = it
                            if (errorMessage.isNotEmpty()) errorMessage = ""
                        },
                        label = { Text("كلمة المرور", color = PalWhiteMuted) },
                        leadingIcon = {
                            Icon(Icons.Default.Lock, contentDescription = null, tint = PalIconUnified)
                        },
                        trailingIcon = {
                            IconButton(onClick = { viewModel.isPasswordVisible.value = !isPassVisible }) {
                                Icon(
                                    imageVector = if (isPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = "إظهار أو إخفاء كلمة المرور",
                                    tint = PalWhiteMuted
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        visualTransformation = if (isPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_password_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start
                    ) {
                        Checkbox(
                            checked = rememberMe,
                            onCheckedChange = { rememberMe = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = PalGreenNormal,
                                uncheckedColor = PalWhiteMuted
                            )
                        )
                        Text(
                            text = "حفظ بيانات تسجيل الدخول",
                            color = PalWhiteSoft,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { rememberMe = !rememberMe }
                        )
                    }

                    LedgerPrimaryButton(
                        text = "دخول إلى النظام",
                        icon = Icons.Default.Login,
                        onClick = {
                            val success = viewModel.attemptLogin(username, password, rememberMe)
                            if (!success) {
                                errorMessage = "خطأ في اسم المستخدم أو كلمة المرور"
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_submit_button")
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Secret password dialog (unchanged security gate)
    if (showSecretPasswordDialog) {
        var secretPassword by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showSecretPasswordDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        if (secretPassword == "780mohmmed") {
                            showSecretPasswordDialog = false
                            showBrandingDialog = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
                ) {
                    Text("موافق", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecretPasswordDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            text = {
                OutlinedTextField(
                    value = secretPassword,
                    onValueChange = {
                        secretPassword = it
                        if (it == "780mohmmed") {
                            showSecretPasswordDialog = false
                            showBrandingDialog = true
                        }
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("secret_branding_password_input")
                )
            },
            containerColor = PalBlackNormal
        )
    }

    if (showBrandingDialog) {
        BrandingDialog(
            viewModel = viewModel,
            onDismiss = { showBrandingDialog = false }
        )
    }

    if (showQatTypesDialog) {
        val brandingQatTypes by viewModel.brandingQatTypes.collectAsState()
        val brandingOwnerName by viewModel.brandingOwnerName.collectAsState()
        var tempQatTypes by remember { mutableStateOf(brandingQatTypes.ifEmpty { BrandingManager.DEFAULT_QAT_TYPES }) }

        AlertDialog(
            onDismissRequest = { showQatTypesDialog = false },
            title = {
                Text(
                    text = "إعداد أصناف القات والهوية",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "أدخل أمثلة وأنواع القات المفصولة بفواصل لتحديث القائمة السريعة في شاشة المبيعات والفواتير:",
                        color = PalWhiteSoft,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Right
                    )
                    OutlinedTextField(
                        value = tempQatTypes,
                        onValueChange = { tempQatTypes = it },
                        label = { Text("أنواع القات (مفصولة بفواصل)", color = PalWhiteMuted, fontSize = 11.sp) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = PalWhitePure,
                            unfocusedTextColor = PalWhiteSoft,
                            focusedBorderColor = PalGreenNormal,
                            unfocusedBorderColor = PalBorderColor,
                            focusedContainerColor = PalBlackDark,
                            unfocusedContainerColor = PalBlackDark
                        ),
                        singleLine = false,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("config_qat_types_input")
                    )
                    TextButton(
                        onClick = {
                            showQatTypesDialog = false
                            showSecretPasswordDialog = true
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("open_hidden_identity_settings_btn")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "تغيير اسم التطبيق، الوكالة، المالك أو الشعار",
                            color = PalIconUnified,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            style = androidx.compose.ui.text.TextStyle(textDecoration = TextDecoration.Underline)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateBranding(
                            newName = brandingAppName.ifEmpty { BrandingManager.DEFAULT_APP_NAME },
                            newAgency = brandingAgencyName,
                            newOwner = brandingOwnerName,
                            newQatTypes = tempQatTypes,
                            logoStream = null
                        )
                        showQatTypesDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
                ) {
                    Text("حفظ وتحديث", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showQatTypesDialog = false }) {
                    Text("إلغاء", color = PalWhiteMuted)
                }
            },
            containerColor = PalBlackNormal
        )
    }
}

@Composable
fun BrandingDialog(
    viewModel: AppViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    val brandingAppName by viewModel.brandingAppName.collectAsState()
    val brandingAgencyName by viewModel.brandingAgencyName.collectAsState()
    val brandingOwnerName by viewModel.brandingOwnerName.collectAsState()
    val brandingQatTypes by viewModel.brandingQatTypes.collectAsState()
    val brandingLogoEnabled by viewModel.brandingLogoEnabled.collectAsState()

    var tempAppName by remember { mutableStateOf(brandingAppName.ifEmpty { BrandingManager.DEFAULT_APP_NAME }) }
    var tempAgencyName by remember { mutableStateOf(brandingAgencyName) }
    var tempOwnerName by remember { mutableStateOf(brandingOwnerName) }
    var tempQatTypes by remember { mutableStateOf(brandingQatTypes.ifEmpty { BrandingManager.DEFAULT_QAT_TYPES }) }
    var selectedUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var clearExistingLogo by remember { mutableStateOf(false) }
    var showDeveloperConsole by remember { mutableStateOf(false) }

    val pickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri: android.net.Uri? ->
        if (uri != null) {
            selectedUri = uri
            clearExistingLogo = false
        }
    }

    val previewBitmap = remember(selectedUri, clearExistingLogo, brandingLogoEnabled, BrandingManager.logoUpdateTrigger) {
        if (clearExistingLogo) {
            null
        } else if (selectedUri != null) {
            try {
                context.contentResolver.openInputStream(selectedUri!!).use {
                    android.graphics.BitmapFactory.decodeStream(it)
                }
            } catch (_: Exception) {
                null
            }
        } else if (brandingLogoEnabled) {
            BrandingManager.getCustomLogoBitmap(context)
        } else {
            null
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "إعدادات الهوية والشعار",
                color = PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        containerColor = PalBlackNormal,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(PalBlackDark)
                        .border(1.2.dp, PalBorderColor, RoundedCornerShape(14.dp))
                        .clickable {
                            pickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (previewBitmap != null) {
                        Image(
                            bitmap = previewBitmap.asImageBitmap(),
                            contentDescription = "معاينة الشعار المخصص",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = null,
                                tint = PalIconUnified,
                                modifier = Modifier.size(28.dp)
                            )
                            Text(
                                text = "انقر لاختيار صورة شعار مخصص من الجهاز",
                                color = PalWhiteSoft,
                                fontSize = 11.5.sp,
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                if (previewBitmap != null) {
                    TextButton(
                        onClick = {
                            selectedUri = null
                            clearExistingLogo = true
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = PalRedNormal)
                    ) {
                        Text("مسح الشعار المخصص", fontSize = 11.5.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (selectedUri != null) {
                    Text(
                        text = "تم اختيار شعار جديد وجاهز للحفظ",
                        color = PalIconUnified,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                OutlinedTextField(
                    value = tempAppName,
                    onValueChange = { tempAppName = it },
                    label = { Text("اسم التطبيق على الجهاز", color = PalWhiteMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempAgencyName,
                    onValueChange = { tempAgencyName = it },
                    label = { Text("اسم الوكالة (في الفواتير والتقارير)", color = PalWhiteMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempOwnerName,
                    onValueChange = { tempOwnerName = it },
                    label = { Text("اسم الوكيل المعتمد", color = PalWhiteMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempQatTypes,
                    onValueChange = { tempQatTypes = it },
                    label = { Text("أنواع القات (مفصولة بفواصل)", color = PalWhiteMuted) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor
                    ),
                    singleLine = false,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val logoStream = if (selectedUri != null && !clearExistingLogo) {
                            try {
                                context.contentResolver.openInputStream(selectedUri!!)
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        viewModel.updateBranding(
                            newName = tempAppName,
                            newAgency = tempAgencyName,
                            newOwner = tempOwnerName,
                            newQatTypes = tempQatTypes,
                            logoStream = logoStream,
                            clearLogo = clearExistingLogo
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("حفظ التعديلات وتطبيق الهوية", color = Color.White, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = {
                        val logoStream = if (selectedUri != null && !clearExistingLogo) {
                            try {
                                context.contentResolver.openInputStream(selectedUri!!)
                            } catch (_: Exception) {
                                null
                            }
                        } else null
                        viewModel.updateBranding(
                            newName = tempAppName,
                            newAgency = tempAgencyName,
                            newOwner = tempOwnerName,
                            newQatTypes = tempQatTypes,
                            logoStream = logoStream,
                            clearLogo = clearExistingLogo
                        )
                    },
                    border = BorderStroke(1.dp, PalGreenNormal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AppShortcut, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("تحديث الأيقونة والاسم على الشاشة الرئيسية", color = PalIconUnified, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }

                if (EditionCapabilities.isDeveloperEdition) {
                    OutlinedButton(
                        onClick = { showDeveloperConsole = true },
                        border = BorderStroke(1.dp, PalGreenNormal),
                        modifier = Modifier.fillMaxWidth().testTag("open_developer_console_from_hidden_admin")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("إدارة الباقات وتوقيع التراخيص (Developer)", color = PalGreenNormal, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                TextButton(
                    onClick = {
                        viewModel.resetBrandingToDefaults()
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = PalRedNormal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("إعادة تعيين بيانات الهوية", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = PalWhiteMuted)
            ) {
                Text("إغلاق")
            }
        }
    )

    EditionCapabilities.DeveloperConsoleHost(
        visible = showDeveloperConsole,
        onDismiss = {
            showDeveloperConsole = false
            viewModel.refreshLicenseStatus()
        }
    )
}

@Composable
fun AgencyPremiumLogo(
    modifier: Modifier = Modifier,
    logoSize: Dp = 120.dp
) {
    val context = LocalContext.current
    val logoEnabled = BrandingManager.isLogoEnabled
    val customBitmap = remember(logoEnabled, BrandingManager.logoUpdateTrigger) {
        if (logoEnabled) BrandingManager.getCustomLogoBitmap(context) else null
    }

    Box(
        modifier = modifier
            .size(logoSize)
            .clip(RoundedCornerShape(18.dp))
            .background(PalBlackNormal)
            .border(1.dp, PalBorderColor, RoundedCornerShape(18.dp))
            .padding(6.dp),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = BrandingManager.resolvedAgencyName(),
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = BrandingManager.resolvedAgencyName(),
                tint = PalIconUnified,
                modifier = Modifier.size(logoSize * 0.5f)
            )
        }
    }
}
