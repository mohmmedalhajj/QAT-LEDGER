package com.example.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.*
import com.example.util.BrandingManager

enum class LedgerStatusType {
    SUCCESS,
    WARNING,
    ERROR,
    NEUTRAL
}

/**
 * Official QAT LEDGER (ميزان القات) product emblem.
 * Respects any custom app icon uploaded via the hidden identity dialog,
 * otherwise displays the official Qat Ledger forest-green emblem.
 */
@Composable
fun QatLedgerAppMark(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    cornerRadius: Dp = 16.dp
) {
    val context = LocalContext.current
    val logoEnabled = BrandingManager.isLogoEnabled
    val customBitmap = remember(logoEnabled, BrandingManager.logoUpdateTrigger) {
        if (logoEnabled) BrandingManager.getCustomLogoBitmap(context) else null
    }
    val productBitmap = remember(context) {
        try {
            BitmapFactory.decodeResource(context.resources, R.drawable.ic_qat_ledger_icon)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(Color(0xFF173D2E))
            .border(1.2.dp, PalGoldMuted.copy(alpha = 0.55f), RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center
    ) {
        if (customBitmap != null) {
            Image(
                bitmap = customBitmap.asImageBitmap(),
                contentDescription = BrandingManager.PRODUCT_NAME_AR,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop
            )
        } else if (productBitmap != null) {
            Image(
                bitmap = productBitmap,
                contentDescription = BrandingManager.PRODUCT_NAME_AR,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(cornerRadius)),
                contentScale = ContentScale.Crop
            )
        }
    }
}

/**
 * Unified institutional Top App Bar across all screens.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QatLedgerTopAppBar(
    title: String,
    subtitle: String = "${BrandingManager.PRODUCT_NAME_AR}${if (BrandingManager.agencyName.isNotBlank()) " • ${BrandingManager.agencyName}" else ""}",
    onBackClick: (() -> Unit)? = null,
    navigationIcon: ImageVector? = null,
    onNavigationClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(
        color = PalBlackNormal,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QatLedgerAppMark(size = 32.dp, cornerRadius = 8.dp)
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = title,
                                color = PalWhitePure,
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                color = PalWhiteMuted,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                navigationIcon = {
                    val clickHandler = onNavigationClick ?: onBackClick
                    if (clickHandler != null) {
                        IconButton(onClick = clickHandler) {
                            Icon(
                                imageVector = navigationIcon ?: Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "رجوع",
                                tint = PalIconUnified
                            )
                        }
                    }
                },
                actions = actions,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = PalBlackNormal,
                    titleContentColor = PalWhitePure,
                    navigationIconContentColor = PalIconUnified,
                    actionIconContentColor = PalIconUnified
                )
            )
            HorizontalDivider(color = PalBorderColor, thickness = 1.dp)
        }
    }
}

/**
 * Clean bordered institutional surface card.
 */
@Composable
fun LedgerSurfaceCard(
    modifier: Modifier = Modifier,
    containerColor: Color = PalBlackNormal,
    borderColor: Color = PalBorderColor,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val cardModifier = if (onClick != null) {
        modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick)
    } else {
        modifier
    }
    Card(
        modifier = cardModifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        content = content
    )
}

/**
 * Calm ERP KPI metric card with monochrome unified icon and crisp numeric contrast.
 */
@Composable
fun LedgerKpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    valueColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    LedgerSurfaceCard(
        modifier = modifier,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = PalWhiteMuted,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(PalSoftGreenTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = PalIconUnified,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = value,
                color = valueColor ?: PalWhitePure,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    color = PalWhiteMuted,
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun LedgerSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(18.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(PalGreenNormal)
            )
            Column {
                Text(
                    text = title,
                    color = PalWhitePure,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        color = PalWhiteMuted,
                        fontSize = 11.sp
                    )
                }
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun LedgerSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = placeholder,
                color = PalWhiteMuted,
                fontSize = 13.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "بحث",
                tint = PalIconUnified
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "مسح البحث",
                        tint = PalWhiteMuted
                    )
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = PalGreenNormal,
            unfocusedBorderColor = PalBorderColor,
            focusedContainerColor = PalBlackNormal,
            unfocusedContainerColor = PalBlackNormal,
            focusedTextColor = PalWhitePure,
            unfocusedTextColor = PalWhiteSoft
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
    )
}

@Composable
fun <T> LedgerFilterChipRow(
    options: List<T>,
    selectedOption: T,
    labelProvider: (T) -> String,
    onOptionSelected: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            val selected = option == selectedOption
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (selected) PalGreenNormal else PalBlackNormal,
                border = BorderStroke(
                    1.dp,
                    if (selected) PalGreenNormal else PalBorderColor
                ),
                modifier = Modifier
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onOptionSelected(option) }
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = labelProvider(option),
                        color = if (selected) Color.White else PalWhiteSoft,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun LedgerStatusBadge(
    text: String,
    status: LedgerStatusType,
    modifier: Modifier = Modifier
) {
    val fg = when (status) {
        LedgerStatusType.SUCCESS -> PalSuccessNormal
        LedgerStatusType.WARNING -> PalWarningNormal
        LedgerStatusType.ERROR -> PalRedNormal
        LedgerStatusType.NEUTRAL -> PalWhiteMuted
    }
    val bg = fg.copy(alpha = 0.12f)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = bg,
        border = BorderStroke(0.8.dp, fg.copy(alpha = 0.3f))
    ) {
        Text(
            text = text,
            color = fg,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun LedgerEmptyState(
    title: String,
    subtitle: String,
    icon: ImageVector = Icons.Default.Inbox,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    LedgerSurfaceCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(PalSoftGreenTint),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = PalIconUnified,
                    modifier = Modifier.size(28.dp)
                )
            }
            Text(
                text = title,
                color = PalWhitePure,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = subtitle,
                color = PalWhiteMuted,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(4.dp))
                LedgerPrimaryButton(
                    text = actionLabel,
                    onClick = onAction,
                    modifier = Modifier.widthIn(min = 160.dp)
                )
            }
        }
    }
}

@Composable
fun LedgerPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PalGreenNormal,
            contentColor = Color.White,
            disabledContainerColor = PalBlackLight,
            disabledContentColor = PalWhiteMuted
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun LedgerSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PalBorderColor),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = PalBlackNormal,
            contentColor = PalWhitePure
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = PalIconUnified, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
fun LedgerDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = PalRedNormal,
            contentColor = Color.White
        ),
        modifier = modifier.heightIn(min = 48.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(text = text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    }
}

@Composable
fun LedgerConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    dismissText: String = "إلغاء",
    isDestructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PalBlackNormal,
        title = {
            Text(
                text = title,
                color = if (isDestructive) PalRedNormal else PalWhitePure,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Text(
                text = message,
                color = PalWhiteSoft,
                fontSize = 13.sp,
                textAlign = TextAlign.Right,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isDestructive) PalRedNormal else PalGreenNormal,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(confirmText, fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                border = BorderStroke(1.dp, PalBorderColor),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.heightIn(min = 44.dp)
            ) {
                Text(dismissText, color = PalWhiteMuted, fontWeight = FontWeight.SemiBold, fontSize = 12.5.sp)
            }
        }
    )
}
