package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.data.AgencyProfile
import java.io.File
import java.io.InputStream

object BrandingManager {
    private const val PREFS_NAME = "branding_prefs"
    private const val KEY_APP_NAME = "branding_app_name"
    private const val KEY_AGENCY_NAME = "branding_agency_name"
    private const val KEY_AGENCY_TAGLINE = "branding_agency_tagline"
    private const val KEY_OWNER_NAME = "branding_owner_name"
    private const val KEY_PHONE = "branding_phone"
    private const val KEY_ADDRESS = "branding_address"
    private const val KEY_ADDITIONAL_CONTACT = "branding_additional_contact"
    private const val KEY_QAT_TYPES = "branding_qat_types"
    private const val KEY_DEFAULT_CURRENCY = "branding_default_currency"
    private const val KEY_CURRENCY_MODE = "branding_currency_mode"
    private const val KEY_COMMERCIAL_DETAILS = "branding_commercial_details"
    private const val KEY_LOGO_ENABLED = "branding_logo_enabled"
    private const val KEY_LOGO_PATH = "branding_logo_path"
    private const val KEY_IS_CONFIGURED = "agency_profile_configured_v3"
    private const val KEY_LEGACY_SCRUBBED_V3 = "legacy_agency_scrubbed_v3"

    private const val KEY_HEADER_ENABLED = "branding_header_enabled"
    private const val KEY_FOOTER_ENABLED = "branding_footer_enabled"
    private const val KEY_HEADER_TEXT = "branding_header_text"
    private const val KEY_FOOTER_TEXT = "branding_footer_text"

    // Official Software Product Identity (strictly separate from merchant agency profile)
    const val PRODUCT_NAME_AR = "ميزان القات"
    const val PRODUCT_NAME_EN = "QAT LEDGER"
    const val PRODUCT_TAGLINE = "نظام الإدارة والمحاسبة المتكامل لوكالات تجارة القات"

    // Clean neutral defaults (never bound to any previous private agency)
    const val DEFAULT_APP_NAME = PRODUCT_NAME_AR
    const val DEFAULT_QAT_TYPES = "عود, فراد"

    private val LEGACY_MARKERS = listOf(
        "طوفان الأقصى",
        "أحمد منصور",
        "لأجود أنواع القات الصعدي",
        "تجدون جودة في المذاق",
        "تكييفة لا تُنسى"
    )

    private fun scrubLegacy(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return if (LEGACY_MARKERS.any { value.contains(it) }) "" else value
    }

    var appName: String by mutableStateOf(DEFAULT_APP_NAME)
        internal set
    var agencyName: String by mutableStateOf("")
        internal set
    var agencyTagline: String by mutableStateOf("")
        internal set
    var ownerName: String by mutableStateOf("")
        internal set
    var phone: String by mutableStateOf("")
        internal set
    var address: String by mutableStateOf("")
        internal set
    var additionalContact: String by mutableStateOf("")
        internal set
    var qatTypes: String by mutableStateOf("")
        internal set
    var defaultCurrency: String by mutableStateOf("ريال يمني")
        internal set
    var currencyMode: String by mutableStateOf(CurrencyMode.BOTH.storageKey)
        internal set
    var commercialDetails: String by mutableStateOf("")
        internal set
    var logoPath: String by mutableStateOf("")
        internal set
    var isLogoEnabled: Boolean by mutableStateOf(false)
        internal set
    var isConfigured: Boolean by mutableStateOf(false)
        internal set

    var isHeaderEnabled: Boolean by mutableStateOf(true)
        internal set
    var isFooterEnabled: Boolean by mutableStateOf(true)
        internal set
    var headerText: String by mutableStateOf("")
        internal set
    var footerText: String by mutableStateOf("")
        internal set

    var logoUpdateTrigger: Int by mutableStateOf(0)
        internal set

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // One-time scrub of any legacy agency data cached in SharedPreferences from prior builds
        if (!prefs.getBoolean(KEY_LEGACY_SCRUBBED_V3, false)) {
            val edit = prefs.edit().putBoolean(KEY_LEGACY_SCRUBBED_V3, true)
            val cleanedApp = scrubLegacy(prefs.getString(KEY_APP_NAME, null)).ifBlank { DEFAULT_APP_NAME }
            val cleanedAgency = scrubLegacy(prefs.getString(KEY_AGENCY_NAME, null))
            val cleanedOwner = scrubLegacy(prefs.getString(KEY_OWNER_NAME, null))
            val cleanedTagline = scrubLegacy(prefs.getString(KEY_AGENCY_TAGLINE, null))
            val cleanedHeader = scrubLegacy(prefs.getString(KEY_HEADER_TEXT, null))
            val cleanedFooter = scrubLegacy(prefs.getString(KEY_FOOTER_TEXT, null))

            edit.putString(KEY_APP_NAME, cleanedApp)
            edit.putString(KEY_AGENCY_NAME, cleanedAgency)
            edit.putString(KEY_OWNER_NAME, cleanedOwner)
            edit.putString(KEY_AGENCY_TAGLINE, cleanedTagline)
            edit.putString(KEY_HEADER_TEXT, cleanedHeader)
            edit.putString(KEY_FOOTER_TEXT, cleanedFooter)
            if (cleanedAgency.isBlank()) {
                edit.putBoolean(KEY_IS_CONFIGURED, false)
            }
            edit.apply()
        }

        appName = scrubLegacy(prefs.getString(KEY_APP_NAME, DEFAULT_APP_NAME)).ifBlank { DEFAULT_APP_NAME }
        agencyName = scrubLegacy(prefs.getString(KEY_AGENCY_NAME, ""))
        agencyTagline = scrubLegacy(prefs.getString(KEY_AGENCY_TAGLINE, ""))
        ownerName = scrubLegacy(prefs.getString(KEY_OWNER_NAME, ""))
        phone = prefs.getString(KEY_PHONE, "") ?: ""
        address = prefs.getString(KEY_ADDRESS, "") ?: ""
        additionalContact = prefs.getString(KEY_ADDITIONAL_CONTACT, "") ?: ""
        qatTypes = prefs.getString(KEY_QAT_TYPES, "") ?: ""
        val rawCurrency = prefs.getString(KEY_DEFAULT_CURRENCY, "ريال يمني") ?: "ريال يمني"
        val rawMode = prefs.getString(KEY_CURRENCY_MODE, null)
        val resolvedMode = if (rawMode.isNullOrBlank()) {
            CurrencyMode.inferFromSelectionLabel(rawCurrency).let {
                if (rawCurrency == "العملتين معاً") CurrencyMode.BOTH else CurrencyMode.BOTH
            }
        } else {
            CurrencyMode.fromStorageKey(rawMode)
        }
        currencyMode = resolvedMode.storageKey
        defaultCurrency = when (resolvedMode) {
            CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
            CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
            CurrencyMode.BOTH -> if (rawCurrency.contains("سعودي")) MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
        }
        commercialDetails = prefs.getString(KEY_COMMERCIAL_DETAILS, "") ?: ""

        val defaultLogoFile = File(context.filesDir, "custom_brand_logo.png")
        val savedPath = prefs.getString(KEY_LOGO_PATH, "") ?: ""
        val activeFile = when {
            savedPath.isNotBlank() && File(savedPath).exists() -> File(savedPath)
            defaultLogoFile.exists() -> defaultLogoFile
            else -> null
        }
        logoPath = activeFile?.absolutePath ?: ""
        isLogoEnabled = prefs.getBoolean(KEY_LOGO_ENABLED, false) && activeFile != null

        isHeaderEnabled = prefs.getBoolean(KEY_HEADER_ENABLED, true)
        isFooterEnabled = prefs.getBoolean(KEY_FOOTER_ENABLED, true)
        headerText = scrubLegacy(prefs.getString(KEY_HEADER_TEXT, ""))
        footerText = scrubLegacy(prefs.getString(KEY_FOOTER_TEXT, ""))
        isConfigured = prefs.getBoolean(KEY_IS_CONFIGURED, false)
    }

    /**
     * Synchronize centralized runtime snapshot from Room AgencyProfile entity.
     */
    fun applyProfile(context: Context, profile: AgencyProfile?) {
        if (profile == null) {
            initialize(context)
            return
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val logoFile = if (profile.logoPath.isNotBlank()) File(profile.logoPath) else File(context.filesDir, "custom_brand_logo.png")
        val hasValidLogo = profile.logoPath.isNotBlank() && logoFile.exists()

        val modeObj = CurrencyMode.fromStorageKey(profile.currencyMode)
        val normalizedDefault = when (modeObj) {
            CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
            CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
            CurrencyMode.BOTH -> if (profile.defaultCurrency.contains("سعودي")) MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
        }

        prefs.edit()
            .putBoolean(KEY_LEGACY_SCRUBBED_V3, true)
            .putString(KEY_APP_NAME, profile.appDisplayName.ifBlank { DEFAULT_APP_NAME })
            .putString(KEY_AGENCY_NAME, profile.agencyName)
            .putString(KEY_AGENCY_TAGLINE, profile.tagline)
            .putString(KEY_OWNER_NAME, profile.ownerName)
            .putString(KEY_PHONE, profile.phone)
            .putString(KEY_ADDRESS, profile.address)
            .putString(KEY_ADDITIONAL_CONTACT, profile.additionalContact)
            .putString(KEY_QAT_TYPES, profile.qatTypes)
            .putString(KEY_DEFAULT_CURRENCY, normalizedDefault)
            .putString(KEY_CURRENCY_MODE, modeObj.storageKey)
            .putString(KEY_COMMERCIAL_DETAILS, profile.commercialDetails)
            .putString(KEY_LOGO_PATH, if (hasValidLogo) logoFile.absolutePath else "")
            .putBoolean(KEY_LOGO_ENABLED, hasValidLogo)
            .putBoolean(KEY_HEADER_ENABLED, profile.headerEnabled)
            .putBoolean(KEY_FOOTER_ENABLED, profile.footerEnabled)
            .putString(KEY_HEADER_TEXT, profile.headerText)
            .putString(KEY_FOOTER_TEXT, profile.footerText)
            .putBoolean(KEY_IS_CONFIGURED, profile.isConfigured)
            .apply()

        appName = profile.appDisplayName.ifBlank { DEFAULT_APP_NAME }
        agencyName = profile.agencyName
        agencyTagline = profile.tagline
        ownerName = profile.ownerName
        phone = profile.phone
        address = profile.address
        additionalContact = profile.additionalContact
        qatTypes = profile.qatTypes
        defaultCurrency = normalizedDefault
        currencyMode = modeObj.storageKey
        commercialDetails = profile.commercialDetails
        logoPath = if (hasValidLogo) logoFile.absolutePath else ""
        isLogoEnabled = hasValidLogo
        isHeaderEnabled = profile.headerEnabled
        isFooterEnabled = profile.footerEnabled
        headerText = profile.headerText
        footerText = profile.footerText
        isConfigured = profile.isConfigured
        logoUpdateTrigger++
    }

    /**
     * Save or clear custom logo file in internal storage and return the resulting path.
     */
    fun persistLogoFile(context: Context, logoStream: InputStream?, clearLogo: Boolean = false): String {
        val logoFile = File(context.filesDir, "custom_brand_logo.png")
        if (clearLogo) {
            if (logoFile.exists()) logoFile.delete()
            return ""
        }
        if (logoStream != null) {
            return try {
                if (logoFile.exists()) logoFile.delete()
                logoFile.outputStream().use { output ->
                    logoStream.copyTo(output)
                }
                logoFile.absolutePath
            } catch (e: Exception) {
                e.printStackTrace()
                if (logoFile.exists()) logoFile.absolutePath else ""
            }
        }
        return if (isLogoEnabled && logoFile.exists()) logoFile.absolutePath else ""
    }

    fun saveBranding(
        context: Context,
        newAppName: String,
        newAgencyName: String,
        newOwnerName: String,
        newQatTypes: String,
        logoStream: InputStream? = null,
        clearLogo: Boolean = false
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = prefs.edit()

        val effectiveAppName = newAppName.trim().ifEmpty { DEFAULT_APP_NAME }
        val effectiveAgencyName = newAgencyName.trim()
        val effectiveOwnerName = newOwnerName.trim()
        val resultingLogoPath = persistLogoFile(context, logoStream, clearLogo)
        val hasLogo = resultingLogoPath.isNotBlank()

        editor.putBoolean(KEY_LEGACY_SCRUBBED_V3, true)
        editor.putString(KEY_APP_NAME, effectiveAppName)
        editor.putString(KEY_AGENCY_NAME, effectiveAgencyName)
        editor.putString(KEY_OWNER_NAME, effectiveOwnerName)
        editor.putString(KEY_QAT_TYPES, newQatTypes.trim())
        editor.putString(KEY_LOGO_PATH, resultingLogoPath)
        editor.putBoolean(KEY_LOGO_ENABLED, hasLogo)
        if (effectiveAgencyName.isNotEmpty()) {
            editor.putBoolean(KEY_IS_CONFIGURED, true)
        }
        if (headerText.isBlank() && effectiveAgencyName.isNotEmpty()) {
            editor.putString(KEY_HEADER_TEXT, effectiveAgencyName)
        }

        editor.apply()
        logoUpdateTrigger++
        initialize(context)

        val currentBitmap = getCustomLogoBitmap(context)
        LauncherIdentityManager.applyIdentityToAndroid(
            context = context,
            appName = effectiveAppName,
            logoBitmap = currentBitmap,
            showNotificationToast = true
        )

        return true
    }

    fun savePrintSettings(
        context: Context,
        headerEnabled: Boolean,
        footerEnabled: Boolean,
        headerTxt: String,
        footerTxt: String
    ): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean(KEY_HEADER_ENABLED, headerEnabled)
            .putBoolean(KEY_FOOTER_ENABLED, footerEnabled)
            .putString(KEY_HEADER_TEXT, headerTxt.trim())
            .putString(KEY_FOOTER_TEXT, footerTxt.trim())
            .apply()
        initialize(context)
        return true
    }

    fun restoreDefaults(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .clear()
            .putBoolean(KEY_LEGACY_SCRUBBED_V3, true)
            .putString(KEY_APP_NAME, DEFAULT_APP_NAME)
            .putString(KEY_AGENCY_NAME, "")
            .putString(KEY_OWNER_NAME, "")
            .putString(KEY_PHONE, "")
            .putString(KEY_ADDRESS, "")
            .putString(KEY_ADDITIONAL_CONTACT, "")
            .putString(KEY_AGENCY_TAGLINE, "")
            .putString(KEY_QAT_TYPES, "")
            .putString(KEY_DEFAULT_CURRENCY, "ريال يمني")
            .putString(KEY_CURRENCY_MODE, CurrencyMode.BOTH.storageKey)
            .putString(KEY_COMMERCIAL_DETAILS, "")
            .putString(KEY_HEADER_TEXT, "")
            .putString(KEY_FOOTER_TEXT, "")
            .putString(KEY_LOGO_PATH, "")
            .putBoolean(KEY_LOGO_ENABLED, false)
            .putBoolean(KEY_IS_CONFIGURED, false)
            .apply()

        val logoFile = File(context.filesDir, "custom_brand_logo.png")
        if (logoFile.exists()) {
            logoFile.delete()
        }

        logoUpdateTrigger++
        initialize(context)

        LauncherIdentityManager.applyIdentityToAndroid(
            context = context,
            appName = DEFAULT_APP_NAME,
            logoBitmap = null,
            showNotificationToast = false
        )
    }

    fun getCustomLogoBitmap(context: Context): Bitmap? {
        if (!isLogoEnabled) return null
        val pathFile = if (logoPath.isNotBlank()) File(logoPath) else File(context.filesDir, "custom_brand_logo.png")
        return if (pathFile.exists()) {
            try {
                BitmapFactory.decodeFile(pathFile.absolutePath)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    // --- Centralized Formatters for Reports, Invoices, WhatsApp, SMS & Thermal Receipts ---

    fun resolvedAgencyName(): String = agencyName.trim().ifEmpty { PRODUCT_NAME_AR }

    fun resolvedOwnerName(): String = ownerName.trim()

    fun buildShareHeader(documentTitle: String? = null): String = buildString {
        val name = resolvedAgencyName()
        val tag = agencyTagline.trim()
        appendLine(if (tag.isNotEmpty()) "$name — $tag" else name)
        if (!documentTitle.isNullOrBlank()) {
            appendLine(documentTitle.trim())
        }
        val contactParts = listOfNotNull(
            phone.trim().takeIf { it.isNotEmpty() }?.let { "هاتف: $it" },
            address.trim().takeIf { it.isNotEmpty() }?.let { "العنوان: $it" },
            additionalContact.trim().takeIf { it.isNotEmpty() }
        )
        if (contactParts.isNotEmpty()) {
            appendLine(contactParts.joinToString(" | "))
        }
        if (commercialDetails.trim().isNotEmpty()) {
            appendLine(commercialDetails.trim())
        }
    }.trimEnd()

    fun buildShareFooter(): String = buildString {
        if (ownerName.trim().isNotEmpty()) {
            appendLine("إدارة الوكالة: ${ownerName.trim()}")
        }
        if (phone.trim().isNotEmpty()) {
            appendLine("للتواصل: ${phone.trim()}")
        }
        if (isFooterEnabled && footerText.trim().isNotEmpty()) {
            appendLine(footerText.trim())
        }
        append("نظام $PRODUCT_NAME_AR المحاسبي")
    }.trimEnd()

    fun buildThermalHeaderLines(): List<String> {
        val lines = mutableListOf<String>()
        val name = agencyName.trim()
        val customHeader = headerText.trim()
        if (name.isNotEmpty()) lines.add(name)
        if (customHeader.isNotEmpty() && customHeader != name) {
            lines.add(customHeader)
        } else if (agencyTagline.trim().isNotEmpty()) {
            lines.add(agencyTagline.trim())
        }
        if (ownerName.trim().isNotEmpty()) lines.add("الوكيل: ${ownerName.trim()}")
        if (phone.trim().isNotEmpty()) lines.add("هاتف: ${phone.trim()}")
        if (address.trim().isNotEmpty()) lines.add(address.trim())
        if (commercialDetails.trim().isNotEmpty()) lines.add(commercialDetails.trim())
        if (lines.isEmpty()) {
            lines.add(PRODUCT_NAME_AR)
        }
        return lines
    }

    fun buildThermalFooterLines(): List<String> {
        val lines = mutableListOf<String>()
        if (footerText.trim().isNotEmpty()) {
            lines.add(footerText.trim())
        }
        if (phone.trim().isNotEmpty()) {
            lines.add("للتواصل: ${phone.trim()}")
        }
        lines.add("نظام $PRODUCT_NAME_AR المحاسبي")
        return lines
    }
}
