package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.AppViewModel
import com.example.ui.theme.*
import com.example.util.BrandingManager
import com.example.util.Helpers

@Composable
fun UniversalColorThemeSettings(viewModel: AppViewModel) {
    val context = LocalContext.current
    val activeTheme = AppThemeManager.currentTheme

    // State for selected theme in the selector (before or after saving)
    var selectedThemeId by remember(activeTheme.id) { mutableStateOf(activeTheme.id) }
    var previewTheme by remember(activeTheme) { mutableStateOf(activeTheme) }

    // Custom color builder state
    var customPrimaryHex by remember { mutableStateOf("#267451") }
    var customAccentHex by remember { mutableStateOf("#C83E3B") }
    var isCustomMode by remember(selectedThemeId) { mutableStateOf(selectedThemeId == "custom") }

    val quickSwatches = listOf(
        "#173B2F" to "أخضر غابات داكن",
        "#267451" to "أخضر ميزان القات",
        "#0D2B52" to "أزرق ملكي",
        "#15539E" to "ياقوتي أزرق",
        "#4A0E17" to "عنابي إمبراطوري",
        "#8B1E2D" to "قرمزي فاخر",
        "#00363A" to "فيروزي محيطي",
        "#00695C" to "بترولي أنيق",
        "#4E3800" to "ذهبي داكن",
        "#7A5900" to "عنبر تجاري",
        "#2E114D" to "بنفسجي ملكي",
        "#5B2396" to "أرجواني راقي",
        "#1E272E" to "رمادي فحمي",
        "#34495E" to "أونيكس مودرن",
        "#0A192F" to "كحلي داكن",
        "#1B3A57" to "نيلي برونزي"
    )

    val accentSwatches = listOf(
        "#C83E3B" to "أحمر تنبيهي هادئ",
        "#E65100" to "برتقالي دافئ",
        "#0288D1" to "أزرق سماوي",
        "#267451" to "أخضر مؤسسي",
        "#FF6F00" to "عنبري مشرق",
        "#00897B" to "تيل نقي",
        "#D35400" to "نحاسي فاخر",
        "#8E44AD" to "أرجواني ناصع"
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- 1. Header Card with Dark Mode Toggle ---
        Card(
            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
            border = BorderStroke(1.dp, PalBorderColor),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PalSoftGreenTint),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "نظام تخصيص ألوان التطبيق وملفات PDF",
                            color = PalWhitePure,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "تغيير وتوحيد الألوان على كافة الواجهات والشاشات ومحرك تقارير PDF",
                            color = PalWhiteMuted,
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                }

                HorizontalDivider(color = PalBorderColor)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DarkMode,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "الوضع الداكن (Dark Mode)",
                            color = PalWhitePure,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Switch(
                        checked = viewModel.isDarkMode.collectAsState().value,
                        onCheckedChange = { viewModel.toggleTheme() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PalGreenNormal
                        ),
                        modifier = Modifier.testTag("toggle_dark_mode_switch")
                    )
                }
            }
        }

        // --- 2. Live Interactive Preview Card ---
        Card(
            colors = CardDefaults.cardColors(containerColor = PalBlackNormal),
            border = BorderStroke(1.dp, previewTheme.primaryNormal.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "معاينة حية للمظهر والتقارير",
                        color = previewTheme.primaryNormal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Badge(
                        containerColor = previewTheme.primaryContainerLight,
                        contentColor = previewTheme.primaryDark
                    ) {
                        Text(previewTheme.nameAr, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }

                // Mock Header Strip
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(previewTheme.primaryDark)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${BrandingManager.PRODUCT_NAME_AR} • ${BrandingManager.agencyName}",
                            color = Color.White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(previewTheme.goldColor)
                        )
                    }
                }

                // Mock Interactive UI Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sample Primary Button
                    Button(
                        onClick = { },
                        colors = ButtonDefaults.buttonColors(containerColor = previewTheme.primaryNormal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("زر تفاعلي أساسي", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    // Sample Accent Chip
                    OutlinedButton(
                        onClick = { },
                        border = BorderStroke(1.dp, previewTheme.accentNormal),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = previewTheme.accentNormal),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f).height(40.dp)
                    ) {
                        Text("تنبيه/تمييز", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Mock PDF Document Appearance Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(PalBlackDark)
                        .border(1.dp, PalBorderColor, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "نموذج ألوان تقارير وفواتير PDF:",
                                color = PalWhitePure,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "A4 معتمد",
                                color = PalWhiteMuted,
                                fontSize = 9.5.sp
                            )
                        }

                        // Mini Table Header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(previewTheme.primaryDark)
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("البيان والنوع", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("الوزن / الكمية", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("المبلغ الإجمالي", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        // Mini Alternating Data Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(android.graphics.Color.parseColor(previewTheme.pdfColors.rowAlternateHex)))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("قات شامي ممتاز", color = Color.Black, fontSize = 9.sp)
                            Text("12.5 كجم", color = Color.Black, fontSize = 9.sp)
                            Text("150,000 ر.ي", color = previewTheme.primaryDark, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }

                        // Mini Totals Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(android.graphics.Color.parseColor(previewTheme.pdfColors.totalRowBgHex)))
                                .border(0.5.dp, previewTheme.primaryDark.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("إجمالي الفاتورة الصافي:", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text("350,000 ر.ي", color = previewTheme.accentNormal, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- 3. Preset Harmonious Palettes List ---
        Text(
            text = "اختر من المجموعات اللونية الجاهزة والمتناسقة:",
            color = PalWhitePure,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AppPalettes.allPresets.forEach { palette ->
                val isSelected = selectedThemeId == palette.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) PalSoftGreenTint else PalBlackNormal
                    ),
                    border = BorderStroke(
                        width = if (isSelected) 1.5.dp else 1.dp,
                        color = if (isSelected) palette.primaryNormal else PalBorderColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selectedThemeId = palette.id
                            previewTheme = palette
                            isCustomMode = false
                        }
                        .testTag("theme_preset_${palette.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Swatches Circles
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.primaryDark)
                                    .border(1.dp, PalBorderColor, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.primaryNormal)
                                    .border(1.dp, PalBorderColor, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.primaryLight)
                                    .border(1.dp, PalBorderColor, CircleShape)
                            )
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(palette.accentNormal)
                                    .border(1.dp, PalBorderColor, CircleShape)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = palette.nameAr,
                                    color = if (isSelected) palette.primaryNormal else PalWhitePure,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                                )
                                if (activeTheme.id == palette.id) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(palette.primaryContainerLight)
                                            .padding(horizontal = 5.dp, vertical = 1.dp)
                                    ) {
                                        Text("النشط حالياً", color = palette.primaryDark, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            Text(
                                text = palette.subtitleAr,
                                color = PalWhiteMuted,
                                fontSize = 10.sp
                            )
                        }

                        RadioButton(
                            selected = isSelected,
                            onClick = {
                                selectedThemeId = palette.id
                                previewTheme = palette
                                isCustomMode = false
                            },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = palette.primaryNormal,
                                unselectedColor = PalWhiteMuted
                            )
                        )
                    }
                }
            }
        }

        // --- 4. Custom Palette Builder Card ---
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isCustomMode) PalSoftGreenTint else PalBlackNormal
            ),
            border = BorderStroke(
                width = if (isCustomMode) 1.5.dp else 1.dp,
                color = if (isCustomMode) previewTheme.primaryNormal else PalBorderColor
            ),
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    isCustomMode = true
                    selectedThemeId = "custom"
                    previewTheme = AppPalettes.createCustom(customPrimaryHex, customAccentHex)
                }
                .testTag("theme_preset_custom")
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
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ColorLens,
                            contentDescription = null,
                            tint = PalIconUnified,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "تخصيص حر بألوان دقيقة",
                                color = if (isCustomMode) previewTheme.primaryNormal else PalWhitePure,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "حدد كود اللون الرئيسي أو اختر من التدرجات المتناسقة مباشرة",
                                color = PalWhiteMuted,
                                fontSize = 10.sp
                            )
                        }
                    }

                    RadioButton(
                        selected = isCustomMode,
                        onClick = {
                            isCustomMode = true
                            selectedThemeId = "custom"
                            previewTheme = AppPalettes.createCustom(customPrimaryHex, customAccentHex)
                        },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = previewTheme.primaryLight,
                            unselectedColor = PalWhiteMuted
                        )
                    )
                }

                AnimatedVisibility(visible = isCustomMode) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HorizontalDivider(color = PalWhiteMuted.copy(alpha = 0.12f))

                        // Quick Primary Color Swatches
                        Text("اللون الأساسي للنظام والتقارير:", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            quickSwatches.take(8).forEach { (hex, _) ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isCur = customPrimaryHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isCur) 2.5.dp else 1.dp,
                                            color = if (isCur) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            customPrimaryHex = hex
                                            previewTheme = AppPalettes.createCustom(customPrimaryHex, customAccentHex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCur) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Hex Input
                        OutlinedTextField(
                            value = customPrimaryHex,
                            onValueChange = {
                                customPrimaryHex = it
                                if (it.trim().matches(Regex("^#?[0-9a-fA-F]{6}$"))) {
                                    previewTheme = AppPalettes.createCustom(customPrimaryHex, customAccentHex)
                                }
                            },
                            label = { Text("كود اللون الأساسي (HEX)") },
                            singleLine = true,
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(previewTheme.primaryNormal)
                                        .border(1.dp, PalBorderColor, CircleShape)
                                )
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = previewTheme.primaryNormal,
                                unfocusedBorderColor = PalBorderColor,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        // Quick Accent Swatches
                        Text("لون التمييز والأرقام المهمة (Accent):", color = PalWhiteSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            accentSwatches.forEach { (hex, _) ->
                                val color = Color(android.graphics.Color.parseColor(hex))
                                val isCur = customAccentHex.equals(hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .border(
                                            width = if (isCur) 2.5.dp else 1.dp,
                                            color = if (isCur) Color.White else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            customAccentHex = hex
                                            previewTheme = AppPalettes.createCustom(customPrimaryHex, customAccentHex)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isCur) {
                                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- 5. Action Buttons (Apply & Save, Reset, Test PDF) ---
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Main Button: Apply & Save
            Button(
                onClick = {
                    if (isCustomMode) {
                        AppThemeManager.applyCustomColor(context, customPrimaryHex, customAccentHex)
                    } else {
                        AppThemeManager.applyTheme(context, previewTheme)
                    }
                    Toast.makeText(
                        context,
                        "تم تطبيق الهوية اللونية (${previewTheme.nameAr}) بنجاح على كامل النظام وملفات PDF!",
                        Toast.LENGTH_LONG
                    ).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = previewTheme.primaryNormal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("apply_theme_button")
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "اعتماد وتطبيق الألوان على التطبيق وملفات PDF",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp
                )
            }

            // Test PDF Generation Button
            OutlinedButton(
                onClick = {
                    // Ensure active theme is saved before generating
                    if (isCustomMode) {
                        AppThemeManager.applyCustomColor(context, customPrimaryHex, customAccentHex)
                    } else {
                        AppThemeManager.applyTheme(context, previewTheme)
                    }

                    // Generate a clean test PDF to verify colors live
                    Helpers.generatePdfAndShare(
                        context = context,
                        title = "تقرير تجربة الألوان المعتمدة",
                        headers = listOf("التاريخ", "البيان والنوع", "الكمية (حبة)", "المبلغ الإجمالي"),
                        rows = listOf(
                            listOf("2026-10-13", "صنف ممتاز درجه أولى", "15", "180,000 ر.ي"),
                            listOf("2026-10-13", "صنف بلدي ممتاز", "10", "120,000 ر.ي"),
                            listOf("2026-10-13", "صنف خاص للوكالة", "8", "96,000 ر.ي")
                        ),
                        totals = mapOf(
                            "إجمالي المبيعات بالريال اليمني" to "396,000 ر.ي",
                            "الصافي المستحق بالريال اليمني" to "396,000 ر.ي"
                        ),
                        customDate = "2026-10-13",
                        reportPeriod = "معاينة واختبار نظام الألوان الموحد",
                        shareToWhatsApp = false,
                    receiverName = "إدارة ${BrandingManager.resolvedAgencyName()}"
                    )
                },
                border = BorderStroke(1.dp, previewTheme.primaryNormal.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = previewTheme.primaryNormal),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("test_pdf_colors_button")
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = PalIconUnified)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "معاينة وإنشاء تقرير PDF تجريبي بالألوان الحالية",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.5.sp
                )
            }

            // Reset to Default Button
            if (activeTheme.id != AppPalettes.EmeraldGreen.id || selectedThemeId != AppPalettes.EmeraldGreen.id) {
                TextButton(
                    onClick = {
                        AppThemeManager.resetToDefault(context)
                        selectedThemeId = AppPalettes.EmeraldGreen.id
                        previewTheme = AppPalettes.EmeraldGreen
                        isCustomMode = false
                        Toast.makeText(context, "تمت استعادة المظهر الرسمي لأخضر ميزان القات المؤسسي بنجاح", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("reset_default_theme_button")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, tint = PalIconUnified)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "استعادة المظهر الافتراضي الرسمي (أخضر ميزان القات)",
                        color = PalWhiteMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
