package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.CurrencyConfig
import com.example.util.CurrencyMode
import com.example.util.CurrencyTotals
import com.example.util.MoneyMath

/**
 * Reusable Agency-wide Currency Configuration card used in both
 * Agency Onboarding / Identity Editor and Settings -> Currencies tab.
 */
@Composable
fun AgencyCurrencySettingsCard(
    selectedMode: CurrencyMode,
    selectedDefaultCurrency: String,
    exchangeRateInput: String,
    onModeSelected: (CurrencyMode) -> Unit,
    onDefaultCurrencySelected: (String) -> Unit,
    onExchangeRateChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    showExchangeRateField: Boolean = true,
    onSaveClick: (() -> Unit)? = null,
    saveButtonLabel: String = "حفظ إعدادات العملات"
) {
    val allowedForMode = when (selectedMode) {
        CurrencyMode.YER_ONLY -> listOf(MoneyMath.CURRENCY_YER)
        CurrencyMode.SAR_ONLY -> listOf(MoneyMath.CURRENCY_SAR)
        CurrencyMode.BOTH -> listOf(MoneyMath.CURRENCY_YER, MoneyMath.CURRENCY_SAR)
    }

    LedgerSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.CurrencyExchange,
                    contentDescription = null,
                    tint = PalIconUnified,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "العملة الافتراضية والعملات المسموح بها",
                    color = PalWhitePure,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Text(
                text = "تحكّم بالعملات المفعّلة في الإدخال والفواتير والتقارير دون التأثير على السجلات التاريخية السابقة.",
                color = PalWhiteMuted,
                fontSize = 11.5.sp,
                lineHeight = 17.sp
            )

            Text(
                text = "نظام العملات المسموح بها:",
                color = PalWhiteSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CurrencyMode.entries.forEach { mode ->
                    val isSelected = selectedMode == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) PalSoftGreenTint else PalBlackDark)
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) PalGreenNormal else PalBorderColor,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { onModeSelected(mode) }
                            .padding(horizontal = 12.dp, vertical = 12.dp)
                            .testTag("currency_mode_${mode.storageKey}"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = mode.labelAr,
                                color = if (isSelected) PalGreenNormal else PalWhitePure,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            val desc = when (mode) {
                                CurrencyMode.YER_ONLY -> "تُعتمد جميع العمليات الجديدة بالريال اليمني فقط."
                                CurrencyMode.SAR_ONLY -> "تُعتمد جميع العمليات الجديدة بالريال السعودي فقط."
                                CurrencyMode.BOTH -> "يُسمح بالإدخال والتعامل بالريال اليمني والسعودي معاً مع فصل الإجماليات."
                            }
                            Text(
                                text = desc,
                                color = PalWhiteMuted,
                                fontSize = 11.sp
                            )
                        }
                        RadioButton(
                            selected = isSelected,
                            onClick = { onModeSelected(mode) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PalGreenNormal,
                                unselectedColor = PalWhiteMuted
                            )
                        )
                    }
                }
            }

            if (allowedForMode.size > 1) {
                HorizontalDivider(color = PalBorderColor)
                Text(
                    text = "العملة الافتراضية عند فتح النماذج:",
                    color = PalWhiteSoft,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    allowedForMode.forEach { curr ->
                        val isSel = MoneyMath.canonicalCurrency(selectedDefaultCurrency) == curr
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) PalGreenNormal else PalBlackDark)
                                .border(
                                    1.dp,
                                    if (isSel) PalGreenNormal else PalBorderColor,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { onDefaultCurrencySelected(curr) }
                                .padding(horizontal = 10.dp, vertical = 10.dp)
                                .testTag("default_currency_${if (curr == MoneyMath.CURRENCY_SAR) "sar" else "yer"}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = curr,
                                color = if (isSel) Color.White else PalWhiteSoft,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                Surface(
                    color = PalBlackDark,
                    border = BorderStroke(1.dp, PalBorderColor),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "العملة الافتراضية المعتمدة تلقائياً:",
                            color = PalWhiteMuted,
                            fontSize = 11.5.sp
                        )
                        Text(
                            text = allowedForMode.first(),
                            color = PalSuccessNormal,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            if (showExchangeRateField) {
                OutlinedTextField(
                    value = exchangeRateInput,
                    onValueChange = onExchangeRateChange,
                    label = { Text("سعر صرف الريال السعودي (بالريال اليمني)", color = PalWhiteMuted, fontSize = 11.5.sp) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                        .testTag("currency_exchange_rate_input")
                )
            }

            if (onSaveClick != null) {
                Button(
                    onClick = onSaveClick,
                    colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("save_currency_settings_btn")
                ) {
                    Text(saveButtonLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
    }
}

/**
 * Reusable currency picker for transaction dialogs and creation forms.
 * - Renders only currencies permitted by [currencyConfig]
 * - Displays an explicit non-destructive warning banner if an open form/edit dialog
 *   currently holds a disabled currency, preserving all other entered fields.
 */
@Composable
fun CurrencySelectionRow(
    selectedCurrency: String,
    currencyConfig: CurrencyConfig,
    onCurrencySelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    title: String? = "العملة:",
    useShortLabels: Boolean = false,
    extraOptionsWhenAllowed: List<String> = emptyList()
) {
    val allowed = currencyConfig.allowedCurrencies + extraOptionsWhenAllowed
    val canonicalSel = MoneyMath.canonicalCurrency(selectedCurrency)
    val isCurrentAllowed = currencyConfig.isSingleCurrencyAllowed(selectedCurrency) ||
        (currencyConfig.allowsMultiple && extraOptionsWhenAllowed.any { MoneyMath.canonicalCurrency(it) == canonicalSel })

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                color = PalWhiteSoft,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (!isCurrentAllowed) {
            Surface(
                color = PalWarningNormal.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, PalWarningNormal.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("disabled_currency_warning_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.WarningAmber,
                        contentDescription = "تنبيه العملة",
                        tint = PalWarningNormal,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currencyConfig.disabledCurrencyWarning(selectedCurrency),
                            color = PalWhitePure,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                    TextButton(
                        onClick = { onCurrencySelected(currencyConfig.resolvedDefaultCurrency) },
                        modifier = Modifier.heightIn(min = 36.dp)
                    ) {
                        Text(
                            text = "اعتماد ${currencyConfig.resolvedDefaultCurrency}",
                            color = PalGreenNormal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(PalBlackDark)
                .border(1.dp, PalBorderColor, RoundedCornerShape(10.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            allowed.forEach { curr ->
                val isSel = MoneyMath.canonicalCurrency(selectedCurrency) == curr || selectedCurrency == curr
                val displayText = if (useShortLabels) {
                    when {
                        curr.contains("يمني") -> "يمني"
                        curr.contains("سعودي") -> "سعودي"
                        else -> curr
                    }
                } else curr

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSel) PalGreenNormal else Color.Transparent)
                        .clickable { onCurrencySelected(curr) }
                        .padding(horizontal = 6.dp, vertical = 8.dp)
                        .testTag("currency_option_${curr}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayText,
                        color = if (isSel) Color.White else PalWhiteMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Displays strictly separated currency totals without mixing different currencies.
 */
@Composable
fun CurrencyTotalsSummaryCard(
    title: String,
    totals: CurrencyTotals,
    currencyConfig: CurrencyConfig,
    modifier: Modifier = Modifier
) {
    Surface(
        color = PalBlackDark,
        border = BorderStroke(1.dp, PalBorderColor),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = PalWhiteMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            val lines = totals.toDisplayLines(currencyConfig)
            lines.forEach { (label, formattedValue) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(PalGreenNormal, CircleShape)
                        )
                        Text(label, color = PalWhiteSoft, fontSize = 12.sp)
                    }
                    Text(
                        text = formattedValue,
                        color = PalSuccessNormal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    }
}
