package com.qatledger.licensing

/**
 * Official commercial license tiers in QAT LEDGER.
 * Kept strictly separated from local Agency Role/Admin toggles.
 */
enum class PackageId(val code: String, val arabicName: String, val englishName: String) {
    BASIC(
        code = "BASIC",
        arabicName = "الباقة الأساسية (BASIC)",
        englishName = "QAT LEDGER BASIC"
    ),
    PRO(
        code = "PRO",
        arabicName = "الباقة الاحترافية (PRO)",
        englishName = "QAT LEDGER PRO"
    );

    companion object {
        fun fromCode(code: String?): PackageId? {
            return entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }
        }
    }
}

enum class LicenseType(val code: String, val arabicName: String) {
    PERPETUAL("PERPETUAL", "ترخيص دائم (مدى الحياة)"),
    SUBSCRIPTION("SUBSCRIPTION", "اشتراك محدد المدة");

    companion object {
        fun fromCode(code: String?): LicenseType? {
            return entries.firstOrNull { it.code.equals(code?.trim(), ignoreCase = true) }
        }
    }
}

enum class LicenseFeature(val code: String, val arabicTitle: String, val isProOnly: Boolean) {
    DASHBOARD("DASHBOARD", "لوحة الجلسة اليومية", false),
    STATEMENT("STATEMENT", "كشف الحسابات المعتمد", false),
    SALES("SALES", "فواتير ومبيعات القات", false),
    CUSTOMERS("CUSTOMERS", "العملاء والديون", false),
    ACCOUNTS("ACCOUNTS", "التحاسب وجرد الحسابات", false),
    TRANSFERS("TRANSFERS", "الحوالات والصرافة", false),
    SUPPLIERS("SUPPLIERS", "الرعيّة والمورّدين", false),
    INVENTORY("INVENTORY", "مخزون أصناف القات", false),
    EXPENSES("EXPENSES", "المصروفات ونثريات المفرش", false),
    SETTINGS_BASIC("SETTINGS_BASIC", "الإعدادات الأساسية والنسخ الاحتياطي", false),
    REPORTS("REPORTS", "التقارير المالية والأرباح", true),
    ARCHIVE("ARCHIVE", "الأرشيف الحسابي الشامل", true);

    companion object {
        val basicFeatures: Set<LicenseFeature>
            get() = entries.filter { !it.isProOnly }.toSet()

        val proFeatures: Set<LicenseFeature>
            get() = entries.toSet()

        fun defaultFeaturesFor(packageId: PackageId): Set<LicenseFeature> {
            return when (packageId) {
                PackageId.BASIC -> basicFeatures
                PackageId.PRO -> proFeatures
            }
        }

        fun fromCodes(codes: Iterable<String>): Set<LicenseFeature> {
            val map = entries.associateBy { it.code }
            return codes.mapNotNull { map[it.trim().uppercase()] }.toSet()
        }
    }
}

/**
 * Signed commercial entitlement payload.
 * IMPORTANT: Contains ZERO pricing, financial balances, or developer customer records.
 */
data class LicensePayload(
    val licenseId: String,
    val version: Int = CURRENT_VERSION,
    val packageId: PackageId,
    val licenseType: LicenseType,
    val features: Set<LicenseFeature>,
    val agencyName: String,
    val ownerName: String,
    val phone: String = "",
    val installationId: String? = null,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val keyId: String,
    val maxClockSkewSeconds: Long = DEFAULT_CLOCK_SKEW_SECONDS,
    val notes: String = ""
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val DEFAULT_CLOCK_SKEW_SECONDS = 300L
    }
}

data class SignedLicenseEnvelope(
    val payload: LicensePayload,
    val signatureBase64Url: String,
    val algorithm: String = ALGORITHM_ED25519
) {
    companion object {
        const val ALGORITHM_ED25519 = "Ed25519"
    }
}

enum class LicenseStatus(val arabicLabel: String, val isOperationalPro: Boolean) {
    NO_LICENSE_BASIC("النسخة الأساسية النشطة (بدون ترخيص PRO)", false),
    ACTIVE_PRO_PERPETUAL("مرخّص — باقة PRO دائمة", true),
    ACTIVE_PRO_SUBSCRIPTION("مرخّص — اشتراك PRO نشط", true),
    ACTIVE_BASIC_PERPETUAL("مرخّص — باقة BASIC دائمة", false),
    ACTIVE_BASIC_SUBSCRIPTION("مرخّص — اشتراك BASIC نشط", false),
    EXPIRED("انتهت صلاحية الاشتراك — رجوع تلقائي إلى BASIC", false),
    CLOCK_TAMPERED("تم رصد تلاعب في ساعة الجهاز — ميزات PRO موقفة", false),
    INVALID_SIGNATURE("فشل التحقق التشفيري من التوقيع", false),
    INVALID_FORMAT("صيغة الترخيص أو الكود غير صالحة", false),
    UNTRUSTED_KEY("مفتاح التوقيع غير معترف به في هذه النسخة", false),
    WRONG_INSTALLATION("الترخيص مقيّد بمعرّف تثبيت آخر", false)
}

data class LicenseVerificationResult(
    val isValid: Boolean,
    val status: LicenseStatus,
    val payload: LicensePayload? = null,
    val reasonArabic: String,
    val effectivePackage: PackageId = PackageId.BASIC,
    val remainingDays: Long? = null,
    val isInstallationBound: Boolean = false
)

data class VerifiedLicenseState(
    val status: LicenseStatus = LicenseStatus.NO_LICENSE_BASIC,
    val effectivePackage: PackageId = PackageId.BASIC,
    val activePayload: LicensePayload? = null,
    val statusMessageArabic: String = LicenseStatus.NO_LICENSE_BASIC.arabicLabel,
    val remainingDays: Long? = null,
    val localInstallationId: String = "",
    val lastVerifiedAtEpochMs: Long = 0L
) {
    fun hasFeature(feature: LicenseFeature): Boolean {
        if (!feature.isProOnly) return true
        if (effectivePackage != PackageId.PRO) return false
        if (!status.isOperationalPro) return false
        val payload = activePayload ?: return false
        return feature in payload.features
    }

    val isProActive: Boolean
        get() = effectivePackage == PackageId.PRO && status.isOperationalPro
}
