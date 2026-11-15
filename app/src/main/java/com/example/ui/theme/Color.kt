package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Institutional Reactive Palette for QAT LEDGER (ميزان القات)
val PalGreenDark: Color
    @Composable
    get() = AppThemeManager.currentTheme.primaryDark

val PalGreenNormal: Color
    @Composable
    get() = AppThemeManager.currentTheme.primaryNormal

val PalGreenLight: Color
    @Composable
    get() = if (isSystemDarkZone) AppThemeManager.currentTheme.primaryLight else AppThemeManager.currentTheme.primaryNormal

val PalRedNormal: Color
    @Composable
    get() = AppThemeManager.currentTheme.accentNormal

val PalRedLight: Color
    @Composable
    get() = if (isSystemDarkZone) AppThemeManager.currentTheme.accentLight else AppThemeManager.currentTheme.accentNormal

val PalGoldCalligraphy: Color
    @Composable
    get() = if (isSystemDarkZone) AppThemeManager.currentTheme.goldColor else AppThemeManager.currentTheme.primaryDark

val PalGoldMuted: Color
    @Composable
    get() = AppThemeManager.currentTheme.goldColor

val isSystemDarkZone: Boolean
    @Composable
    get() = MaterialTheme.colorScheme.background.luminance() < 0.5f

// Semantic Surface & Text Tokens mapped cleanly to Light & Dark Theme
val PalBlackDark: Color
    @Composable
    get() = MaterialTheme.colorScheme.background

val PalBlackNormal: Color
    @Composable
    get() = MaterialTheme.colorScheme.surface

val PalBlackLight: Color
    @Composable
    get() = MaterialTheme.colorScheme.surfaceVariant

val PalWhitePure: Color
    @Composable
    get() = MaterialTheme.colorScheme.onBackground

val PalWhiteSoft: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurface

val PalWhiteMuted: Color
    @Composable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

val PalBorderColor: Color
    @Composable
    get() = MaterialTheme.colorScheme.outline

val PalSoftGreenTint: Color
    @Composable
    get() = if (isSystemDarkZone) Color(0xFF1A2D25) else AppThemeManager.currentTheme.primaryContainerLight

// Unified Institutional Icon & Semantic Status Tokens
val PalIconUnified: Color
    @Composable
    get() = if (isSystemDarkZone) AppThemeManager.currentTheme.primaryLight else AppThemeManager.currentTheme.primaryNormal

val PalSuccessNormal: Color
    @Composable
    get() = if (isSystemDarkZone) AppThemeManager.currentTheme.primaryLight else AppThemeManager.currentTheme.primaryNormal

val PalWarningNormal: Color
    @Composable
    get() = if (isSystemDarkZone) Color(0xFFE09446) else Color(0xFFC97A2B)
