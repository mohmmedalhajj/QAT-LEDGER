package com.example.util

import com.example.data.MoneyTransfer
import com.example.data.TransferRecipientEntity

/**
 * Centralized Arabic RTL thermal receipt formatter supporting 58mm (32 columns) and 80mm (48 columns).
 * Uses dynamic [BrandingManager] agency identity, avoids cross-currency summation, and preserves
 * original historical currencies on every entry.
 */
object ThermalReceiptTemplates {

    enum class PaperWidth(val mm: Int, val columns: Int) {
        MM_58(58, 32),
        MM_80(80, 48);

        companion object {
            fun fromMm(mm: Int): PaperWidth = if (mm >= 80) MM_80 else MM_58
        }
    }

    fun columnsForMm(mm: Int): Int = PaperWidth.fromMm(mm).columns

    fun divider(width: Int, char: Char = '-'): String = char.toString().repeat(width.coerceAtLeast(16))

    fun doubleDivider(width: Int): String = "=".repeat(width.coerceAtLeast(16))

    fun centerLine(text: String, width: Int): String {
        val clean = sanitize(text)
        if (clean.length >= width) return clean
        val left = (width - clean.length) / 2
        val right = width - clean.length - left
        return " ".repeat(left) + clean + " ".repeat(right)
    }

    fun wrapLines(text: String, width: Int): List<String> {
        val result = mutableListOf<String>()
        val paragraphs = sanitize(text).split("\n")
        for (p in paragraphs) {
            if (p.isEmpty()) continue
            if (p.length <= width) {
                result.add(p)
                continue
            }
            var current = ""
            for (word in p.split(" ")) {
                if (word.isEmpty()) continue
                if (word.length > width) {
                    if (current.isNotEmpty()) {
                        result.add(current)
                        current = ""
                    }
                    var rem = word
                    while (rem.length > width) {
                        result.add(rem.substring(0, width))
                        rem = rem.substring(width)
                    }
                    current = rem
                } else if (current.isEmpty()) {
                    current = word
                } else if (current.length + 1 + word.length <= width) {
                    current += " $word"
                } else {
                    result.add(current)
                    current = word
                }
            }
            if (current.isNotEmpty()) result.add(current)
        }
        return result
    }

    fun formatKeyValue(key: String, value: String, width: Int): String {
        val k = sanitize(key)
        val v = sanitize(value)
        if (k.isEmpty()) return v
        if (v.isEmpty()) return k
        val total = k.length + v.length + 2
        if (total <= width) {
            val spaces = width - k.length - v.length
            return "$v${" ".repeat(spaces)}$k"
        }
        return "$k:\n  $v"
    }

    private fun sanitize(input: String): String {
        return input
            .replace("🇾🇪", "")
            .replace("🇸🇦", "")
            .replace("🇵🇸", "")
            .replace("📊", "")
            .replace("💰", "")
            .replace("💸", "")
            .replace("💔", "")
            .replace("📄", "")
            .replace("📅", "")
            .replace("💵", "")
            .replace("📥", "")
            .replace("🔄", "")
            .replace("✔️", "")
            .trim()
    }

    fun appendAgencyHeader(sb: StringBuilder, docTitle: String, width: Int) {
        val sep = doubleDivider(width)
        val thin = divider(width)
        sb.appendLine(sep)
        if (BrandingManager.isHeaderEnabled) {
            BrandingManager.buildThermalHeaderLines().forEach { line ->
                wrapLines(line, width).forEach { wrapped ->
                    sb.appendLine(centerLine(wrapped, width))
                }
            }
            sb.appendLine(thin)
        }
        wrapLines(docTitle, width).forEach { wrapped ->
            sb.appendLine(centerLine(wrapped, width))
        }
        sb.appendLine(centerLine("تاريخ الطباعة: ${Helpers.getCurrentDateTime()}", width))
        sb.appendLine(sep)
    }

    fun appendAgencyFooter(sb: StringBuilder, width: Int) {
        val thin = divider(width)
        sb.appendLine(thin)
        if (BrandingManager.isFooterEnabled) {
            BrandingManager.buildThermalFooterLines().forEach { line ->
                wrapLines(line, width).forEach { wrapped ->
                    sb.appendLine(centerLine(wrapped, width))
                }
            }
        }
        sb.appendLine(formatKeyValue("توقيع المستلم: ....", "توقيع المحاسب: ....", width))
        sb.appendLine(doubleDivider(width))
    }

    fun buildTransferVoucherReceipt(
        transfer: MoneyTransfer,
        recipient: TransferRecipientEntity? = null,
        paperWidthMm: Int = 58
    ): String {
        val width = columnsForMm(paperWidthMm)
        val sb = StringBuilder()
        appendAgencyHeader(sb, "سند حوالة مالية", width)
        sb.appendLine(formatKeyValue("رقم السند/المرجع", transfer.referenceNumber.ifBlank { "#${transfer.id}" }, width))
        sb.appendLine(formatKeyValue("التاريخ", transfer.date, width))
        sb.appendLine(formatKeyValue("المرسل", transfer.sender, width))
        sb.appendLine(formatKeyValue("المستلم", recipient?.name ?: transfer.receiver, width))
        if (!recipient?.phone.isNullOrBlank()) {
            sb.appendLine(formatKeyValue("هاتف المستلم", recipient!!.phone, width))
        }
        if (!recipient?.company.isNullOrBlank()) {
            sb.appendLine(formatKeyValue("جهة الاستلام", recipient!!.company, width))
        }
        sb.appendLine(formatKeyValue("العملة", MoneyMath.canonicalCurrency(transfer.currency), width))
        sb.appendLine(formatKeyValue("الحالة", transfer.status.ifBlank { "مكتملة" }, width))
        sb.appendLine(divider(width))
        if (transfer.statement.isNotBlank()) {
            wrapLines("البيان: ${transfer.statement}", width).forEach { sb.appendLine(it) }
        }
        if (transfer.notes.isNotBlank()) {
            wrapLines("ملاحظات: ${transfer.notes}", width).forEach { sb.appendLine(it) }
        }
        sb.appendLine(divider(width))
        sb.appendLine(
            formatKeyValue(
                "المبلغ الإجمالي",
                MoneyMath.formatWithCurrency(transfer.amount, transfer.currency),
                width
            )
        )
        appendAgencyFooter(sb, width)
        return sb.toString()
    }

    fun buildRecipientTransfersReportReceipt(
        recipient: TransferRecipientEntity,
        transfers: List<MoneyTransfer>,
        periodLabel: String,
        dateRangeLabel: String,
        currencyConfig: CurrencyConfig,
        paperWidthMm: Int = 58
    ): String {
        val width = columnsForMm(paperWidthMm)
        val sb = StringBuilder()
        appendAgencyHeader(sb, "تقرير حساب مستلم حوالات", width)
        sb.appendLine(formatKeyValue("اسم المستلم", recipient.name, width))
        sb.appendLine(formatKeyValue("معرف المستلم", "#${recipient.id}", width))
        if (recipient.phone.isNotBlank()) {
            sb.appendLine(formatKeyValue("الهاتف", recipient.phone, width))
        }
        if (recipient.company.isNotBlank()) {
            sb.appendLine(formatKeyValue("شركة الصرافة", recipient.company, width))
        }
        sb.appendLine(formatKeyValue("الحالة", if (recipient.isActive) "نشط" else "موقوف (سجل تاريخي)", width))
        sb.appendLine(formatKeyValue("الفترة", periodLabel, width))
        if (dateRangeLabel.isNotBlank()) {
            wrapLines("النطاق: $dateRangeLabel", width).forEach { sb.appendLine(it) }
        }
        sb.appendLine(formatKeyValue("عدد الحوالات", "${transfers.size}", width))
        sb.appendLine(divider(width))

        if (transfers.isEmpty()) {
            sb.appendLine(centerLine("لا توجد حوالات مسجلة في هذه الفترة", width))
        } else {
            transfers.forEachIndexed { idx, t ->
                val ref = t.referenceNumber.ifBlank { "#${t.id}" }
                sb.appendLine(formatKeyValue("${idx + 1}. $ref", MoneyMath.formatWithCurrency(t.amount, t.currency), width))
                wrapLines("التاريخ: ${t.date} | المرسل: ${t.sender}", width).forEach { sb.appendLine(it) }
                val notePart = listOf(t.statement, t.notes).filter { it.isNotBlank() }.joinToString(" - ")
                if (notePart.isNotBlank()) {
                    wrapLines("البيان: $notePart", width).forEach { sb.appendLine(it) }
                }
                if (idx < transfers.lastIndex) sb.appendLine(divider(width, '.'))
            }
        }

        sb.appendLine(divider(width))
        sb.appendLine(centerLine("الإجماليات حسب العملة", width))
        val totals = CurrencyTotals.fromAmounts(transfers.map { it.amount to it.currency })
        totals.toDisplayLines(currencyConfig).forEach { (label, valText) ->
            sb.appendLine(formatKeyValue(label, valText, width))
        }
        appendAgencyFooter(sb, width)
        return sb.toString()
    }

    fun buildTransfersLedgerSummaryReceipt(
        transfers: List<MoneyTransfer>,
        periodLabel: String,
        recipientLabel: String,
        currencyConfig: CurrencyConfig,
        paperWidthMm: Int = 58
    ): String {
        val width = columnsForMm(paperWidthMm)
        val sb = StringBuilder()
        appendAgencyHeader(sb, "تقرير سجل الحوالات والتحويلات", width)
        sb.appendLine(formatKeyValue("الفترة", periodLabel, width))
        sb.appendLine(formatKeyValue("المستلم", recipientLabel, width))
        sb.appendLine(formatKeyValue("عدد الحوالات", "${transfers.size}", width))
        sb.appendLine(divider(width))

        if (transfers.isEmpty()) {
            sb.appendLine(centerLine("لا توجد حوالات مطابقة", width))
        } else {
            transfers.forEachIndexed { idx, t ->
                val ref = t.referenceNumber.ifBlank { "#${t.id}" }
                sb.appendLine(formatKeyValue("${idx + 1}. $ref", MoneyMath.formatWithCurrency(t.amount, t.currency), width))
                wrapLines("من: ${t.sender} -> إلى: ${t.receiver}", width).forEach { sb.appendLine(it) }
                wrapLines("تاريخ: ${t.date}", width).forEach { sb.appendLine(it) }
                if (t.statement.isNotBlank()) {
                    wrapLines("بيان: ${t.statement}", width).forEach { sb.appendLine(it) }
                }
                if (idx < transfers.lastIndex) sb.appendLine(divider(width, '.'))
            }
        }

        sb.appendLine(divider(width))
        sb.appendLine(centerLine("الإجماليات المفصولة حسب العملة", width))
        val totals = CurrencyTotals.fromAmounts(transfers.map { it.amount to it.currency })
        totals.toDisplayLines(currencyConfig).forEach { (label, valText) ->
            sb.appendLine(formatKeyValue(label, valText, width))
        }
        appendAgencyFooter(sb, width)
        return sb.toString()
    }
}
