package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val theme = AppThemeManager.currentTheme

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = theme.primaryLight,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = theme.primaryDark,
            onPrimaryContainer = Color(0xFFE8F2EC),
            secondary = Color(0xFF1D2E27),
            onSecondary = Color(0xFFEEF3EF),
            tertiary = theme.goldColor,
            onTertiary = Color(0xFF173B2F),
            background = Color(0xFF0E1713),
            onBackground = Color(0xFFEEF3EF),
            surface = Color(0xFF15221D),
            onSurface = Color(0xFFEEF3EF),
            surfaceVariant = Color(0xFF1D2E27),
            onSurfaceVariant = Color(0xFF94A69B),
            outline = Color(0xFF243830),
            outlineVariant = Color(0xFF1E3029),
            error = theme.accentLight,
            onError = Color(0xFFFFFFFF)
        )
    } else {
        lightColorScheme(
            primary = theme.primaryNormal,
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = theme.primaryContainerLight,
            onPrimaryContainer = theme.primaryDark,
            secondary = Color(0xFFE8F2EC),
            onSecondary = Color(0xFF173B2F),
            tertiary = theme.goldColor,
            onTertiary = Color(0xFFFFFFFF),
            background = Color(0xFFF5F7F5),
            onBackground = Color(0xFF25332C),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF25332C),
            surfaceVariant = Color(0xFFEEF3EF),
            onSurfaceVariant = Color(0xFF748078),
            outline = Color(0xFFE1E8E3),
            outlineVariant = Color(0xFFE8EFEA),
            error = theme.accentNormal,
            onError = Color(0xFFFFFFFF)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
