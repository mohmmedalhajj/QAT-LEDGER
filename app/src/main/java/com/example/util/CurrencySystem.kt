package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class CurrencyMode(
    val storageKey: String,
    val labelAr: String,
    val descriptionAr: String
) {
    YER_ONLY(
        storageKey = "YER_ONLY",
        labelAr = "الريال اليمني فقط",
        descriptionAr = "قصر السندات والحسابات الجديدة على الريال اليمني فقط"
    ),
    SAR_ONLY(
        storageKey = "SAR_ONLY",
        labelAr = "الريال السعودي فقط",
        descriptionAr = "قصر السندات والحسابات الجديدة على الريال السعودي فقط"
    ),
    BOTH(
        storageKey = "BOTH",
        labelAr = "الريال اليمني والريال السعودي معاً",
        descriptionAr = "إتاحة العملتين (اليمني والسعودي) في جميع العمليات والتقارير"
    );

    companion object {
        fun fromStorageKey(key: String?): CurrencyMode {
            return when (key?.trim()?.uppercase(Locale.US)) {
                "YER_ONLY", "YER", "يمني", "ريال يمني" -> YER_ONLY
                "SAR_ONLY", "SAR", "سعودي", "ريال سعودي" -> SAR_ONLY
                "BOTH", "ALL", "MULTI" -> BOTH
                else -> BOTH
            }
        }

        fun inferFromSelectionLabel(label: String?): CurrencyMode {
            val cleaned = label?.trim().orEmpty()
            return when {
                cleaned == YER_ONLY.labelAr || cleaned == "YER_ONLY" -> YER_ONLY
                cleaned == SAR_ONLY.labelAr || cleaned == "SAR_ONLY" -> SAR_ONLY
                cleaned == BOTH.labelAr || cleaned == "BOTH" || cleaned.contains("معاً") -> BOTH
                cleaned.contains("سعودي") && !cleaned.contains("يمني") -> SAR_ONLY
                cleaned.contains("يمني") && !cleaned.contains("سعودي") -> YER_ONLY
                else -> BOTH
            }
        }
    }
}

data class CurrencyConfig(
    val mode: CurrencyMode = CurrencyMode.BOTH,
    val defaultCurrency: String = MoneyMath.CURRENCY_YER,
    val exchangeRateSarToYer: BigDecimal = BigDecimal("150")
) {
    val allowedCurrencies: List<String>
        get() = when (mode) {
            CurrencyMode.YER_ONLY -> listOf(MoneyMath.CURRENCY_YER)
            CurrencyMode.SAR_ONLY -> listOf(MoneyMath.CURRENCY_SAR)
            CurrencyMode.BOTH -> listOf(MoneyMath.CURRENCY_YER, MoneyMath.CURRENCY_SAR)
        }

    val allowsYer: Boolean get() = mode != CurrencyMode.SAR_ONLY
    val allowsSar: Boolean get() = mode != CurrencyMode.YER_ONLY
    val allowsMultiple: Boolean get() = mode == CurrencyMode.BOTH

    val resolvedDefaultCurrency: String
        get() = coerceOrDefault(defaultCurrency)

    fun isAllowed(currency: String?): Boolean {
        val canonical = MoneyMath.canonicalCurrency(currency)
        return when (mode) {
            CurrencyMode.YER_ONLY -> canonical == MoneyMath.CURRENCY_YER
            CurrencyMode.SAR_ONLY -> canonical == MoneyMath.CURRENCY_SAR
            CurrencyMode.BOTH -> canonical == MoneyMath.CURRENCY_YER ||
                canonical == MoneyMath.CURRENCY_SAR ||
                canonical == MoneyMath.CURRENCY_MULTI
        }
    }

    fun isSingleCurrencyAllowed(currency: String?): Boolean {
        val canonical = MoneyMath.canonicalCurrency(currency)
        return canonical in allowedCurrencies
    }

    fun coerceOrDefault(currency: String?): String {
        val canonical = MoneyMath.canonicalCurrency(currency)
        return when {
            canonical in allowedCurrencies -> canonical
            mode == CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
            mode == CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
            else -> MoneyMath.CURRENCY_YER
        }
    }

    fun statementFilterOptions(): List<String> = when (mode) {
        CurrencyMode.YER_ONLY -> listOf("الريال اليمني")
        CurrencyMode.SAR_ONLY -> listOf("الريال السعودي")
        CurrencyMode.BOTH -> listOf("الريال اليمني", "الريال السعودي", "الريال اليمني والريال السعودي معاً")
    }

    fun coerceStatementFilter(current: String?): String {
        val options = statementFilterOptions()
        if (current != null && current in options) return current
        return when (mode) {
            CurrencyMode.YER_ONLY -> "الريال اليمني"
            CurrencyMode.SAR_ONLY -> "الريال السعودي"
            CurrencyMode.BOTH -> "الريال اليمني والريال السعودي معاً"
        }
    }

    fun dashboardCurrencyOptions(): List<String> = when (mode) {
        CurrencyMode.YER_ONLY -> listOf("الريال اليمني")
        CurrencyMode.SAR_ONLY -> listOf("الريال السعودي")
        CurrencyMode.BOTH -> listOf("الريال اليمني", "الريال السعودي")
    }

    fun coerceDashboardCurrency(current: String?): String {
        val options = dashboardCurrencyOptions()
        if (current != null && current in options) return current
        return if (resolvedDefaultCurrency == MoneyMath.CURRENCY_SAR) "الريال السعودي" else "الريال اليمني"
    }

    fun invoiceEditableCurrencies(): List<String> = when (mode) {
        CurrencyMode.YER_ONLY -> listOf(MoneyMath.CURRENCY_YER)
        CurrencyMode.SAR_ONLY -> listOf(MoneyMath.CURRENCY_SAR)
        CurrencyMode.BOTH -> listOf(MoneyMath.CURRENCY_YER, MoneyMath.CURRENCY_SAR, MoneyMath.CURRENCY_MULTI)
    }

    fun supplierAccountCurrencies(): List<String> = when (mode) {
        CurrencyMode.YER_ONLY -> listOf("يمني")
        CurrencyMode.SAR_ONLY -> listOf("سعودي")
        CurrencyMode.BOTH -> listOf("يمني", "سعودي")
    }

    fun disabledCurrencyWarning(attemptedCurrency: String): String {
        val clean = MoneyMath.canonicalCurrency(attemptedCurrency)
        return "العملة ($clean) معطّلة حالياً حسب إعدادات الوكالة (${mode.labelAr}). يرجى اختيار عملة مسموحة قبل الحفظ."
    }

    companion object {
        fun create(
            mode: CurrencyMode,
            defaultCurrency: String?,
            exchangeRateSarToYer: Double
        ): CurrencyConfig {
            val safeRate = if (exchangeRateSarToYer > 0.0 && exchangeRateSarToYer.isFinite()) {
                BigDecimal.valueOf(exchangeRateSarToYer)
            } else {
                BigDecimal("150")
            }
            val canonicalDefault = MoneyMath.canonicalCurrency(defaultCurrency)
            val coercedDefault = when (mode) {
                CurrencyMode.YER_ONLY -> MoneyMath.CURRENCY_YER
                CurrencyMode.SAR_ONLY -> MoneyMath.CURRENCY_SAR
                CurrencyMode.BOTH -> if (canonicalDefault == MoneyMath.CURRENCY_SAR) MoneyMath.CURRENCY_SAR else MoneyMath.CURRENCY_YER
            }
            return CurrencyConfig(
                mode = mode,
                defaultCurrency = coercedDefault,
                exchangeRateSarToYer = safeRate
            )
        }
    }
}

object MoneyMath {
    const val CURRENCY_YER = "ريال يمني"
    const val CURRENCY_SAR = "ريال سعودي"
    const val CURRENCY_MULTI = "عملات متعددة"

    private val formatterSymbols = DecimalFormatSymbols(Locale.US)

    fun normalizeDigits(raw: String): String {
        val sb = StringBuilder(raw.length)
        for (ch in raw) {
            when (ch) {
                in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
                in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
                '٫' -> sb.append('.')
                '٬', ',' -> Unit // skip thousands separators cleanly
                else -> sb.append(ch)
            }
        }
        return sb.toString().trim()
    }

    fun bd(value: Double): BigDecimal {
        if (!value.isFinite()) return BigDecimal.ZERO
        return BigDecimal.valueOf(value)
    }

    fun parseDecimal(raw: String?): BigDecimal? {
        if (raw.isNullOrBlank()) return null
        val normalized = normalizeDigits(raw)
        if (normalized.isEmpty()) return null
        return try {
            BigDecimal(normalized)
        } catch (_: NumberFormatException) {
            null
        }
    }

    fun parseDoubleOrNull(raw: String?): Double? = parseDecimal(raw)?.toDouble()

    fun add(a: Double, b: Double): Double = bd(a).add(bd(b)).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun subtract(a: Double, b: Double): Double = bd(a).subtract(bd(b)).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun multiply(a: Double, b: Double): Double = bd(a).multiply(bd(b)).setScale(2, RoundingMode.HALF_UP).toDouble()

    fun divide(a: Double, b: Double, scale: Int = 2): Double {
        val denom = bd(b)
        if (denom.compareTo(BigDecimal.ZERO) == 0) return 0.0
        return bd(a).divide(denom, scale, RoundingMode.HALF_UP).toDouble()
    }

    fun sum(values: Iterable<Double>): Double {
        var total = BigDecimal.ZERO
        for (v in values) {
            total = total.add(bd(v))
        }
        return total.setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun sumBD(values: Iterable<Double>): BigDecimal {
        var total = BigDecimal.ZERO
        for (v in values) {
            total = total.add(bd(v))
        }
        return total.setScale(2, RoundingMode.HALF_UP)
    }

    fun convertSarToYer(sar: Double, rateSarToYer: BigDecimal): Double {
        if (rateSarToYer <= BigDecimal.ZERO) return 0.0
        return bd(sar).multiply(rateSarToYer).setScale(2, RoundingMode.HALF_UP).toDouble()
    }

    fun convertYerToSar(yer: Double, rateSarToYer: BigDecimal): Double {
        if (rateSarToYer <= BigDecimal.ZERO) return 0.0
        return bd(yer).divide(rateSarToYer, 2, RoundingMode.HALF_UP).toDouble()
    }

    fun canonicalCurrency(raw: String?): String {
        val cleaned = raw?.trim().orEmpty()
        if (cleaned.isEmpty()) return CURRENCY_YER
        val lower = cleaned.lowercase(Locale.US)
        return when {
            cleaned.contains("متعددة") || cleaned.contains("معاً") || lower == "multi" || lower == "both" -> CURRENCY_MULTI
            cleaned.contains("سعودي") || cleaned.contains("ر.س") || lower == "sar" -> CURRENCY_SAR
            cleaned.contains("يمني") || cleaned.contains("ر.ي") || lower == "yer" -> CURRENCY_YER
            else -> cleaned // Preserve unknown legacy currency strings without silent mutation
        }
    }

    fun isYer(currency: String?): Boolean {
        val c = canonicalCurrency(currency)
        return c == CURRENCY_YER
    }

    fun isSar(currency: String?): Boolean {
        val c = canonicalCurrency(currency)
        return c == CURRENCY_SAR
    }

    fun isMultiCurrency(currency: String?): Boolean {
        val c = canonicalCurrency(currency)
        return c == CURRENCY_MULTI
    }

    fun shortSymbol(currency: String?): String = when (canonicalCurrency(currency)) {
        CURRENCY_SAR -> "ر.س"
        CURRENCY_YER -> "ر.ي"
        CURRENCY_MULTI -> "متعدد"
        else -> canonicalCurrency(currency)
    }

    fun matchesFilter(recordCurrency: String?, filterLabel: String): Boolean {
        val canon = canonicalCurrency(recordCurrency)
        return when (filterLabel.trim()) {
            "الريال اليمني", CURRENCY_YER -> canon == CURRENCY_YER || canon == CURRENCY_MULTI
            "الريال السعودي", CURRENCY_SAR -> canon == CURRENCY_SAR || canon == CURRENCY_MULTI
            else -> true
        }
    }

    fun format(amount: Double, forceDecimals: Boolean = false): String {
        val decimal = bd(amount).setScale(2, RoundingMode.HALF_UP)
        val hasFractional = decimal.stripTrailingZeros().scale() > 0
        val pattern = if (forceDecimals || hasFractional) "#,##0.00" else "#,##0"
        return DecimalFormat(pattern, formatterSymbols).format(decimal)
    }

    fun formatWithCurrency(amount: Double, currency: String?): String {
        val sym = shortSymbol(currency)
        return "${format(amount)} $sym"
    }
}

data class CurrencyTotals(
    val yer: Double = 0.0,
    val sar: Double = 0.0,
    val otherByCurrency: Map<String, Double> = emptyMap(),
    val countYer: Int = 0,
    val countSar: Int = 0,
    val countOther: Int = 0
) {
    val totalCount: Int get() = countYer + countSar + countOther

    fun hasAnyAmount(): Boolean = yer != 0.0 || sar != 0.0 || otherByCurrency.values.any { it != 0.0 }

    fun toDisplayLines(
        config: CurrencyConfig? = null,
        includeZeroForAllowed: Boolean = true
    ): List<Pair<String, String>> {
        val lines = mutableListOf<Pair<String, String>>()
        val showYer = (config?.allowsYer ?: true) || yer != 0.0 || countYer > 0
        val showSar = (config?.allowsSar ?: true) || sar != 0.0 || countSar > 0

        if (showYer && (includeZeroForAllowed || yer != 0.0 || countYer > 0)) {
            lines.add("الإجمالي بالريال اليمني" to MoneyMath.formatWithCurrency(yer, MoneyMath.CURRENCY_YER))
        }
        if (showSar && (includeZeroForAllowed || sar != 0.0 || countSar > 0)) {
            lines.add("الإجمالي بالريال السعودي" to MoneyMath.formatWithCurrency(sar, MoneyMath.CURRENCY_SAR))
        }
        for ((curr, value) in otherByCurrency) {
            if (value != 0.0) {
                lines.add("الإجمالي ($curr)" to MoneyMath.formatWithCurrency(value, curr))
            }
        }
        return lines
    }

    companion object {
        fun fromAmounts(items: Iterable<Pair<Double, String?>>): CurrencyTotals {
            var yerSum = BigDecimal.ZERO
            var sarSum = BigDecimal.ZERO
            val otherSums = linkedMapOf<String, BigDecimal>()
            var cYer = 0
            var cSar = 0
            var cOther = 0

            for ((rawAmount, rawCurrency) in items) {
                val amount = MoneyMath.bd(rawAmount)
                when (val canon = MoneyMath.canonicalCurrency(rawCurrency)) {
                    MoneyMath.CURRENCY_YER -> {
                        yerSum = yerSum.add(amount)
                        cYer++
                    }
                    MoneyMath.CURRENCY_SAR -> {
                        sarSum = sarSum.add(amount)
                        cSar++
                    }
                    else -> {
                        otherSums[canon] = (otherSums[canon] ?: BigDecimal.ZERO).add(amount)
                        cOther++
                    }
                }
            }

            return CurrencyTotals(
                yer = yerSum.setScale(2, RoundingMode.HALF_UP).toDouble(),
                sar = sarSum.setScale(2, RoundingMode.HALF_UP).toDouble(),
                otherByCurrency = otherSums.mapValues { it.value.setScale(2, RoundingMode.HALF_UP).toDouble() },
                countYer = cYer,
                countSar = cSar,
                countOther = cOther
            )
        }
    }
}
