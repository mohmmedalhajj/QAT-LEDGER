package com.example.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Singleton manager governing universal theme colors across the entire Android app and PDF engine.
 * Automatically saves state to SharedPreferences and triggers instant Compose recomposition.
 */
object AppThemeManager {

    private const val PREFS_NAME = "app_universal_theme_prefs"
    private const val KEY_THEME_ID = "theme_id"
    private const val KEY_CUSTOM_PRIMARY = "custom_primary_hex"
    private const val KEY_CUSTOM_ACCENT = "custom_accent_hex"

    // Active reactive theme state for Compose
    var currentTheme: AppColorTheme by mutableStateOf(AppPalettes.EmeraldGreen)
        private set

    private var initialized = false

    /**
     * Initializes theme state from persistent storage.
     */
    fun initialize(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val themeId = prefs.getString(KEY_THEME_ID, AppPalettes.EmeraldGreen.id) ?: AppPalettes.EmeraldGreen.id

        currentTheme = if (themeId == "custom") {
            val customPrimary = prefs.getString(KEY_CUSTOM_PRIMARY, "#267451") ?: "#267451"
            val customAccent = prefs.getString(KEY_CUSTOM_ACCENT, "#C83E3B") ?: "#C83E3B"
            AppPalettes.createCustom(customPrimary, customAccent)
        } else {
            AppPalettes.allPresets.find { it.id == themeId } ?: AppPalettes.EmeraldGreen
        }
        initialized = true
    }

    /**
     * Applies a preset theme immediately and persists it.
     */
    fun applyTheme(context: Context, theme: AppColorTheme) {
        currentTheme = theme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_THEME_ID, theme.id).apply()
    }

    /**
     * Applies a theme by its string identifier.
     */
    fun applyThemeById(context: Context, id: String) {
        val theme = AppPalettes.allPresets.find { it.id == id } ?: AppPalettes.EmeraldGreen
        applyTheme(context, theme)
    }

    /**
     * Applies a custom theme with chosen primary and accent colors and persists it.
     */
    fun applyCustomColor(context: Context, primaryHex: String, accentHex: String = "#C83E3B") {
        val customTheme = AppPalettes.createCustom(primaryHex, accentHex)
        currentTheme = customTheme
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_THEME_ID, "custom")
            .putString(KEY_CUSTOM_PRIMARY, primaryHex)
            .putString(KEY_CUSTOM_ACCENT, accentHex)
            .apply()
    }

    /**
     * Resets to the official Palestinian Emerald Green palette.
     */
    fun resetToDefault(context: Context) {
        applyTheme(context, AppPalettes.EmeraldGreen)
    }

    /**
     * Provides the currently active PDF colors for the PDF generation engine.
     * Guaranteed to return consistent colors even if called prior to Compose initialization.
     */
    fun getPdfColors(context: Context? = null): PdfColors {
        if (!initialized && context != null) {
            initialize(context)
        }
        return currentTheme.pdfColors
    }
}
