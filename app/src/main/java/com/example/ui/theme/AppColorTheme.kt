package com.example.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * PDF Color Scheme ensuring high contrast, elegance, and professional printing.
 */
data class PdfColors(
    val primaryDarkHex: String,
    val primaryNormalHex: String,
    val primaryLightHex: String,
    val accentHex: String,
    val goldHex: String = "#C7A45B",
    val rowAlternateHex: String = "#F5F7F5",
    val subHeaderBgHex: String = "#E8F2EC",
    val totalRowBgHex: String = "#F8FAF8",
    val borderHex: String = "#E1E8E3"
)

/**
 * Universal App Color Theme Model linking Compose UI and PDF documents.
 */
data class AppColorTheme(
    val id: String,
    val nameAr: String,
    val subtitleAr: String,
    val primaryDark: Color,
    val primaryNormal: Color,
    val primaryLight: Color,
    val primaryContainerLight: Color,
    val accentNormal: Color,
    val accentLight: Color,
    val goldColor: Color = Color(0xFFFFD700),
    val pdfColors: PdfColors
)

/**
 * Built-in harmonious color palettes designed for accounting clarity, contrast, and elegance.
 */
object AppPalettes {

    // 1. Qat Ledger Institutional Forest & Emerald Green (Official Default)
    val EmeraldGreen = AppColorTheme(
        id = "emerald_green",
        nameAr = "أخضر ميزان القات المؤسسي (الرسمي)",
        subtitleAr = "الهوية الرسمية الهادئة لنظام ميزان القات — QAT LEDGER",
        primaryDark = Color(0xFF173B2F),
        primaryNormal = Color(0xFF267451),
        primaryLight = Color(0xFF3E946C),
        primaryContainerLight = Color(0xFFE8F2EC),
        accentNormal = Color(0xFFC83E3B),
        accentLight = Color(0xFFE05A57),
        goldColor = Color(0xFFC7A45B),
        pdfColors = PdfColors(
            primaryDarkHex = "#173B2F",
            primaryNormalHex = "#267451",
            primaryLightHex = "#3E946C",
            accentHex = "#C83E3B",
            goldHex = "#C7A45B",
            rowAlternateHex = "#F5F7F5",
            subHeaderBgHex = "#E8F2EC",
            totalRowBgHex = "#F8FAF8",
            borderHex = "#E1E8E3"
        )
    )

    // 2. Royal Sapphire Blue
    val SapphireBlue = AppColorTheme(
        id = "sapphire_blue",
        nameAr = "الأزرق الملكي الياقوتي",
        subtitleAr = "نمط مؤسسي رفيع يعكس الدقة والاحترافية الحسابية",
        primaryDark = Color(0xFF0D2B52),
        primaryNormal = Color(0xFF15539E),
        primaryLight = Color(0xFF2979FF),
        primaryContainerLight = Color(0xFFE3F2FD),
        accentNormal = Color(0xFFE65100),
        accentLight = Color(0xFFFF8F00),
        goldColor = Color(0xFFFFB300),
        pdfColors = PdfColors(
            primaryDarkHex = "#0D2B52",
            primaryNormalHex = "#15539E",
            primaryLightHex = "#2979FF",
            accentHex = "#E65100",
            goldHex = "#FFB300",
            rowAlternateHex = "#F4F8FC",
            subHeaderBgHex = "#E3EDF7",
            totalRowBgHex = "#F8FAFD"
        )
    )

    // 3. Imperial Burgundy
    val RoyalBurgundy = AppColorTheme(
        id = "royal_burgundy",
        nameAr = "العنابي والياقوت الإمبراطوري",
        subtitleAr = "هيبة راقية وفخامة عربية متميزة للتقارير والواجهات",
        primaryDark = Color(0xFF4A0E17),
        primaryNormal = Color(0xFF8B1E2D),
        primaryLight = Color(0xFFE53935),
        primaryContainerLight = Color(0xFFFCE4EC),
        accentNormal = Color(0xFF0288D1),
        accentLight = Color(0xFF03A9F4),
        goldColor = Color(0xFFFFD700),
        pdfColors = PdfColors(
            primaryDarkHex = "#4A0E17",
            primaryNormalHex = "#8B1E2D",
            primaryLightHex = "#E53935",
            accentHex = "#0288D1",
            goldHex = "#D4AF37",
            rowAlternateHex = "#FCF6F7",
            subHeaderBgHex = "#F7E8EB",
            totalRowBgHex = "#FDF7F8"
        )
    )

    // 4. Ocean Deep Teal
    val DeepTeal = AppColorTheme(
        id = "deep_teal",
        nameAr = "الفيروزي المحيطي الأنيق",
        subtitleAr = "ألوان عصرية مريحة للعين مع تباين فائق للبيانات",
        primaryDark = Color(0xFF00363A),
        primaryNormal = Color(0xFF00695C),
        primaryLight = Color(0xFF00BFA5),
        primaryContainerLight = Color(0xFFE0F2F1),
        accentNormal = Color(0xFFFF6F00),
        accentLight = Color(0xFFFF9800),
        goldColor = Color(0xFFFFD54F),
        pdfColors = PdfColors(
            primaryDarkHex = "#00363A",
            primaryNormalHex = "#00695C",
            primaryLightHex = "#00BFA5",
            accentHex = "#FF6F00",
            goldHex = "#FFD54F",
            rowAlternateHex = "#F2F9F9",
            subHeaderBgHex = "#DCEDED",
            totalRowBgHex = "#F6FAFA"
        )
    )

    // 5. Imperial Gold & Amber
    val ImperialGold = AppColorTheme(
        id = "imperial_gold",
        nameAr = "الذهبي الملكي والعنبر الفاخر",
        subtitleAr = "طابع الثراء التجاري المالي والتميز الموثوق",
        primaryDark = Color(0xFF4E3800),
        primaryNormal = Color(0xFF7A5900),
        primaryLight = Color(0xFFB58500),
        primaryContainerLight = Color(0xFFFFF8E1),
        accentNormal = Color(0xFF1E753C),
        accentLight = Color(0xFF4CAF50),
        goldColor = Color(0xFFFFD700),
        pdfColors = PdfColors(
            primaryDarkHex = "#4E3800",
            primaryNormalHex = "#7A5900",
            primaryLightHex = "#B58500",
            accentHex = "#1E753C",
            goldHex = "#FFD700",
            rowAlternateHex = "#FAF7EE",
            subHeaderBgHex = "#F7EED6",
            totalRowBgHex = "#FCF9F2"
        )
    )

    // 6. Royal Amethyst Violet
    val RoyalAmethyst = AppColorTheme(
        id = "royal_amethyst",
        nameAr = "البنفسجي الإمبراطوري الراقي",
        subtitleAr = "لمسة أرستقراطية فريدة وجذابة لقراءة الحسابات",
        primaryDark = Color(0xFF2E114D),
        primaryNormal = Color(0xFF5B2396),
        primaryLight = Color(0xFF9C27B0),
        primaryContainerLight = Color(0xFFF3E5F5),
        accentNormal = Color(0xFF00897B),
        accentLight = Color(0xFF26A69A),
        goldColor = Color(0xFFFFD700),
        pdfColors = PdfColors(
            primaryDarkHex = "#2E114D",
            primaryNormalHex = "#5B2396",
            primaryLightHex = "#9C27B0",
            accentHex = "#00897B",
            goldHex = "#D4AF37",
            rowAlternateHex = "#F9F4FC",
            subHeaderBgHex = "#EDE2F5",
            totalRowBgHex = "#FAF6FD"
        )
    )

    // 7. Slate Charcoal & Graphite
    val SlateCharcoal = AppColorTheme(
        id = "slate_charcoal",
        nameAr = "الرمادي الفحمي والأونيكس المودرن",
        subtitleAr = "طابع تقني حديث وهادئ يركز على تباين الأرقام",
        primaryDark = Color(0xFF1E272E),
        primaryNormal = Color(0xFF34495E),
        primaryLight = Color(0xFF576574),
        primaryContainerLight = Color(0xFFECEFF1),
        accentNormal = Color(0xFFD35400),
        accentLight = Color(0xFFE67E22),
        goldColor = Color(0xFFF1C40F),
        pdfColors = PdfColors(
            primaryDarkHex = "#1E272E",
            primaryNormalHex = "#34495E",
            primaryLightHex = "#576574",
            accentHex = "#D35400",
            goldHex = "#F1C40F",
            rowAlternateHex = "#F4F6F7",
            subHeaderBgHex = "#EAEDED",
            totalRowBgHex = "#F7F8F9"
        )
    )

    // 8. Commercial Navy & Bronze
    val NavyBronze = AppColorTheme(
        id = "navy_bronze",
        nameAr = "الكحلي التجاري والبرونزي",
        subtitleAr = "توازن مثالي بين الكحلي الداكن ولمسات البرونز والذهب",
        primaryDark = Color(0xFF0A192F),
        primaryNormal = Color(0xFF1B3A57),
        primaryLight = Color(0xFF3282B8),
        primaryContainerLight = Color(0xFFE8EEF5),
        accentNormal = Color(0xFFC0392B),
        accentLight = Color(0xFFE74C3C),
        goldColor = Color(0xFFD4AF37),
        pdfColors = PdfColors(
            primaryDarkHex = "#0A192F",
            primaryNormalHex = "#1B3A57",
            primaryLightHex = "#3282B8",
            accentHex = "#C0392B",
            goldHex = "#D4AF37",
            rowAlternateHex = "#F3F6FA",
            subHeaderBgHex = "#E2EBF4",
            totalRowBgHex = "#F7F9FC"
        )
    )

    val allPresets = listOf(
        EmeraldGreen,
        SapphireBlue,
        RoyalBurgundy,
        DeepTeal,
        ImperialGold,
        RoyalAmethyst,
        SlateCharcoal,
        NavyBronze
    )

    /**
     * Builds a cohesive custom theme from given hex codes.
     */
    fun createCustom(primaryHex: String, accentHex: String = "#C83E3B"): AppColorTheme {
        val cleanPrimary = normalizeHex(primaryHex, "#267451")
        val cleanAccent = normalizeHex(accentHex, "#C83E3B")

        val pColor = parseHexColor(cleanPrimary)
        val aColor = parseHexColor(cleanAccent)

        // Derive shades mathematically
        val pDark = adjustColorBrightness(pColor, 0.55f)
        val pLight = adjustColorBrightness(pColor, 1.35f)
        val pContainer = adjustColorBrightness(pColor, 1.85f).copy(alpha = 0.25f)

        val aLight = adjustColorBrightness(aColor, 1.25f)

        val darkHex = colorToHex(pDark)
        val normalHex = colorToHex(pColor)
        val lightHex = colorToHex(pLight)
        val accHex = colorToHex(aColor)

        return AppColorTheme(
            id = "custom",
            nameAr = "النمط المخصص الفاخر",
            subtitleAr = "ألوان مخصصة من اختيارك متناسقة للواجهات وملفات PDF",
            primaryDark = pDark,
            primaryNormal = pColor,
            primaryLight = pLight,
            primaryContainerLight = pContainer,
            accentNormal = aColor,
            accentLight = aLight,
            goldColor = Color(0xFFC7A45B),
            pdfColors = PdfColors(
                primaryDarkHex = darkHex,
                primaryNormalHex = normalHex,
                primaryLightHex = lightHex,
                accentHex = accHex,
                goldHex = "#C7A45B",
                rowAlternateHex = "#F5F7F5",
                subHeaderBgHex = "#E8F2EC",
                totalRowBgHex = "#F8FAF8",
                borderHex = "#E1E8E3"
            )
        )
    }

    private fun normalizeHex(hex: String, fallback: String): String {
        val trimmed = hex.trim()
        val withHash = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
        return if (withHash.matches(Regex("^#[0-9a-fA-F]{6}$"))) withHash.uppercase() else fallback
    }

    private fun parseHexColor(hex: String): Color {
        return try {
            val colorInt = android.graphics.Color.parseColor(hex)
            Color(colorInt)
        } catch (e: Exception) {
            Color(0xFF1E753C)
        }
    }

    private fun adjustColorBrightness(color: Color, factor: Float): Color {
        val r = (color.red * factor).coerceIn(0f, 1f)
        val g = (color.green * factor).coerceIn(0f, 1f)
        val b = (color.blue * factor).coerceIn(0f, 1f)
        return Color(r, g, b, 1f)
    }

    private fun colorToHex(color: Color): String {
        val r = (color.red * 255).toInt().coerceIn(0, 255)
        val g = (color.green * 255).toInt().coerceIn(0, 255)
        val b = (color.blue * 255).toInt().coerceIn(0, 255)
        return String.format("#%02X%02X%02X", r, g, b)
    }
}
