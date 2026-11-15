package com.example.util

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.widget.Toast
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

data class ParsedReceipt(
    val titleLines: List<String>,
    val metaFields: List<Pair<String, String>>,
    val itemRows: List<Triple<String, String, String>>, // Item, Qty, Total
    val statementRows: List<List<String>>, // [Date, Details]
    val totalsFields: List<Pair<String, String>>,
    val footerLines: List<String>
)

object Helpers {

    fun stripFlags(s: String): String {
        return s.replace("🇾🇪", "").replace("🇸🇦", "").trim()
    }

    fun getCurrentDate(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        return sdf.format(Date())
    }

    fun formatDateTo29June2026(date: Date): String {
        val cal = Calendar.getInstance()
        cal.time = date
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val year = cal.get(Calendar.YEAR)
        val monthIndex = cal.get(Calendar.MONTH) // 0-indexed
        val arabicMonths = arrayOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        val monthName = arabicMonths[monthIndex]
        return "$day-$monthName-$year"
    }

    fun formatDateTo29June2026(dateTimeStr: String): String {
        if (dateTimeStr.isEmpty()) return ""
        return try {
            val arabicMonths = arrayOf(
                "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
            )
            if (arabicMonths.any { dateTimeStr.contains(it) }) {
                val datePart = if (dateTimeStr.contains(" ")) {
                    dateTimeStr.split(" ")[0]
                } else {
                    dateTimeStr
                }
                return datePart
            }

            var cleanStr = dateTimeStr.trim()
            if (cleanStr.contains(" ")) {
                cleanStr = cleanStr.split(" ")[0]
            }
            val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd")
            var parsedDate: Date? = null
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    parsedDate = sdf.parse(cleanStr)
                    if (parsedDate != null) break
                } catch (ignored: Exception) {}
            }
            if (parsedDate == null) {
                try {
                    val fullSdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                    parsedDate = fullSdf.parse(dateTimeStr)
                } catch (e: Exception) {}
            }
            if (parsedDate != null) {
                formatDateTo29June2026(parsedDate)
            } else {
                dateTimeStr
            }
        } catch (e: Exception) {
            dateTimeStr
        }
    }

    fun formatDateTime12Hour(date: Date): String {
        val datePart = formatDateTo29June2026(date)
        val rSdf = SimpleDateFormat("hh:mm", Locale.US)
        val amPmSdf = SimpleDateFormat("a", Locale.US)
        val amPmRaw = amPmSdf.format(date).uppercase()
        val amPmText = if (amPmRaw.contains("AM")) "ص" else "م"
        return "$datePart ${rSdf.format(date)} $amPmText"
    }

    fun getCurrentDateTime(): String {
        return formatDateTime12Hour(Date())
    }

    fun getCurrentWeekOfMonth(): Int {
        val day = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        return when {
            day <= 7 -> 1
            day <= 14 -> 2
            day <= 21 -> 3
            day <= 28 -> 4
            else -> 5
        }
    }

    fun extractActualPaymentNotes(paymentMethod: String, bankName: String): String {
        try {
            if (!paymentMethod.contains("سداد")) return ""
            val openParen = paymentMethod.lastIndexOf('(')
            val closeParen = paymentMethod.lastIndexOf(')')
            if (openParen == -1 || closeParen == -1 || closeParen <= openParen) return ""
            val inside = paymentMethod.substring(openParen + 1, closeParen).trim()
            val parts = inside.split(" - ").map { it.trim() }
            if (parts.size <= 1) return ""
            val contentParts = parts.dropLast(1)
            val notesParts = contentParts.filter { 
                it != "نقداً" && 
                !it.startsWith("البنك:") && 
                it != bankName && 
                !it.startsWith("ضمار")
            }
            return notesParts.joinToString(" - ")
        } catch (e: Exception) {
            return ""
        }
    }

    fun extractSupplierStatementNotes(paymentMethod: String, bankName: String): String {
        val dates = mutableListOf<String>()
        try {
            val segments = paymentMethod.split("|").map { it.trim() }
            for (segment in segments) {
                if (segment.contains("سداد")) {
                    val firstOpen = segment.indexOf('(')
                    val lastClose = segment.lastIndexOf(')')
                    if (firstOpen != -1 && lastClose != -1 && lastClose > firstOpen) {
                        val inside = segment.substring(firstOpen + 1, lastClose).trim()
                        val parts = inside.split(" - ").map { it.trim() }
                        if (parts.isNotEmpty()) {
                            val lastPart = parts.last()
                            if (lastPart.isNotEmpty()) {
                                dates.add(lastPart)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // ignore
        }
        
        val cleanBank = bankName.trim()
        val uniqueDates = dates.distinct()
        
        return when {
            cleanBank.isNotEmpty() && uniqueDates.isNotEmpty() -> {
                "البنك المحول: $cleanBank\nتاريخ السداد: ${uniqueDates.joinToString("، ")}"
            }
            cleanBank.isNotEmpty() -> {
                "البنك المحول: $cleanBank"
            }
            uniqueDates.isNotEmpty() -> {
                "تاريخ السداد: ${uniqueDates.joinToString("، ")}"
            }
            else -> ""
        }
    }

    fun formatStringDateTime12Hour(dateTimeStr: String): String {
        if (dateTimeStr.isEmpty()) return ""
        val arabicMonths = arrayOf(
            "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
            "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
        )
        if (arabicMonths.any { dateTimeStr.contains(it) }) {
            return dateTimeStr
        }
        return try {
            if (dateTimeStr.contains("ص") || dateTimeStr.contains("م")) {
                return dateTimeStr
            }
            var parsedDate: Date? = null
            val patterns = listOf(
                "yyyy-MM-dd HH:mm",
                "yyyy-MM-dd hh:mm a",
                "yyyy/MM/dd HH:mm",
                "yyyy/MM/dd hh:mm a",
                "yyyy-MM-dd"
            )
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US)
                    parsedDate = sdf.parse(dateTimeStr)
                    if (parsedDate != null) break
                } catch (ignored: Exception) {}
            }
            if (parsedDate != null) {
                formatDateTime12Hour(parsedDate)
            } else {
                dateTimeStr
            }
        } catch (e: Exception) {
            dateTimeStr
        }
    }

    fun parseToDateSafe(dateStr: String): Date {
        if (dateStr.isEmpty()) return Date(0)
        val patterns = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy-MM-dd",
            "yyyy/MM/dd HH:mm",
            "yyyy/MM/dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                val d = sdf.parse(dateStr.trim())
                if (d != null) return d
            } catch (ignored: Exception) {}
        }
        try {
            val parts = dateStr.trim().split(" ")
            val datePart = parts[0]
            val dateSubParts = datePart.split("-")
            if (dateSubParts.size == 3) {
                val day = dateSubParts[0].toIntOrNull() ?: 1
                val monthName = dateSubParts[1]
                val year = dateSubParts[2].toIntOrNull() ?: 2026
                val arabicMonths = listOf(
                    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                )
                val monthIndex = arabicMonths.indexOf(monthName)
                val cal = Calendar.getInstance()
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, if (monthIndex != -1) monthIndex else 0)
                cal.set(Calendar.DAY_OF_MONTH, day)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                if (parts.size >= 2) {
                    val timePart = parts[1]
                    val timeSub = timePart.split(":")
                    if (timeSub.size == 2) {
                        var hour = timeSub[0].toIntOrNull() ?: 0
                        val min = timeSub[1].toIntOrNull() ?: 0
                        if (parts.size >= 3) {
                            val amPm = parts[2]
                            if (amPm.contains("م") && hour < 12) hour += 12
                            if (amPm.contains("ص") && hour == 12) hour = 0
                        }
                        cal.set(Calendar.HOUR_OF_DAY, hour)
                        cal.set(Calendar.MINUTE, min)
                    }
                }
                return cal.time
            }
        } catch (e: Exception) {}
        return Date(0)
    }

    fun getWeekStartAndEnd(yearMonth: String, weekNum: Int): Pair<String, String> {
        try {
            val parts = yearMonth.split("-")
            val year = parts[0].toInt()
            val month = parts[1].toInt()
            val startDay = when (weekNum) {
                1 -> 1
                2 -> 8
                3 -> 15
                4 -> 22
                5 -> 29
                else -> 1
            }
            val endDay = when (weekNum) {
                1 -> 7
                2 -> 14
                3 -> 21
                4 -> 28
                5 -> {
                    val cal = Calendar.getInstance()
                    cal.set(Calendar.YEAR, year)
                    cal.set(Calendar.MONTH, month - 1)
                    cal.getActualMaximum(Calendar.DAY_OF_MONTH)
                }
                else -> 7
            }
            val startStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, startDay)
            val endStr = String.format(Locale.US, "%04d-%02d-%02d", year, month, endDay)
            return Pair(startStr, endStr)
        } catch (e: Exception) {
            return Pair("$yearMonth-01", "$yearMonth-07")
        }
    }

    fun isWithinLast7Days(dateStr: String): Boolean {
        return try {
            val datePart = if (dateStr.length >= 10) dateStr.substring(0, 10) else dateStr
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val dateValue = sdf.parse(datePart) ?: return false
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_YEAR, -7)
            val boundary = cal.time
            !dateValue.before(boundary)
        } catch (e: Exception) {
            false
        }
    }

    fun parseAnyDateToYmd(dateStr: String): Triple<Int, Int, Int>? {
        if (dateStr.isEmpty()) return null
        return try {
            var cleanStr = dateStr.trim()
            // Clean up Arabic/English commas
            cleanStr = cleanStr.replace("،", " ").replace(",", " ")
            
            val arabicMonths = arrayOf(
                "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
            )
            
            // Check if it's the Arabic format like "29-يونيو-2026" or "29 يونيو 2026"
            var foundMonthIdx = -1
            for (i in arabicMonths.indices) {
                if (cleanStr.contains(arabicMonths[i])) {
                    foundMonthIdx = i
                    break
                }
            }
            
            if (foundMonthIdx != -1) {
                // Split by spaces, hyphens, and slashes
                val parts = cleanStr.split("-", " ", "/").filter { it.isNotEmpty() }
                val month = foundMonthIdx + 1
                if (parts.size >= 3) {
                    var day = 1
                    var year = 2026
                    val p0 = parts[0].toIntOrNull()
                    val p2 = parts[2].toIntOrNull()
                    if (parts[0].length == 4) {
                        year = p0 ?: 2026
                        day = p2 ?: 1
                    } else {
                        day = p0 ?: 1
                        year = p2 ?: 2026
                    }
                    return Triple(year, month, day)
                } else if (parts.size == 2) {
                    var year = 2026
                    val p0 = parts[0].toIntOrNull()
                    val p1 = parts[1].toIntOrNull()
                    if (parts[0].length == 4) {
                        year = p0 ?: 2026
                    } else if (parts[1].length == 4) {
                        year = p1 ?: 2026
                    } else {
                        year = p0 ?: p1 ?: 2026
                    }
                    return Triple(year, month, 1)
                } else {
                    val todayYear = Calendar.getInstance().get(Calendar.YEAR)
                    return Triple(todayYear, month, 1)
                }
            }
            
            // If it's standard format like "2026-06-29" or "2026/06/29"
            var datePart = if (cleanStr.contains(" ")) cleanStr.split(" ")[0] else cleanStr
            val parts = datePart.split("-", "/").filter { it.isNotEmpty() }
            if (parts.size >= 3) {
                if (parts[0].length == 4) { // yyyy-MM-dd
                    val year = parts[0].toIntOrNull() ?: 2026
                    val month = parts[1].toIntOrNull() ?: 6
                    val day = parts[2].toIntOrNull() ?: 1
                    return Triple(year, month, day)
                } else if (parts[2].length == 4) { // dd-MM-yyyy
                    val day = parts[0].toIntOrNull() ?: 1
                    val month = parts[1].toIntOrNull() ?: 6
                    val year = parts[2].toIntOrNull() ?: 2026
                    return Triple(year, month, day)
                }
            } else if (parts.size == 2) {
                val p0 = parts[0].toIntOrNull()
                val p1 = parts[1].toIntOrNull()
                if (parts[0].length == 4) { // yyyy-MM
                    val year = p0 ?: 2026
                    val month = p1 ?: 6
                    return Triple(year, month, 1)
                } else if (parts[1].length == 4) { // MM-yyyy
                    val month = p0 ?: 6
                    val year = p1 ?: 2026
                    return Triple(year, month, 1)
                }
            } else if (parts.size == 1) {
                val year = parts[0].toIntOrNull()
                if (year != null && year in 1900..2100) {
                    return Triple(year, 1, 1)
                }
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun standardizeDateToYmd(dateStr: String): String {
        if (dateStr.isEmpty()) return ""
        val triple = parseAnyDateToYmd(dateStr) ?: return ""
        return String.format(Locale.US, "%04d-%02d-%02d", triple.first, triple.second, triple.third)
    }

    fun formatCustomPeriodToArabic(type: String, value: String): String {
        if (value.isEmpty()) return ""
        if (value.startsWith("WEEK_FILTER|")) {
            return try {
                val parts = value.split("|")
                val yearMonth = parts[1] // "2026-07"
                val weekNum = parts[2].toIntOrNull() ?: 1
                val weekNames = arrayOf("", "الأسبوع الأول", "الأسبوع الثاني", "الأسبوع الثالث", "الأسبوع الرابع", "الأسبوع الخامس")
                val weekStr = weekNames.getOrElse(weekNum) { "الأسبوع $weekNum" }
                
                val mParts = yearMonth.split("-")
                val year = mParts.getOrNull(0) ?: ""
                val monthNum = mParts.getOrNull(1)?.toIntOrNull() ?: 1
                val arabicMonths = arrayOf(
                    "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                    "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                )
                val monthName = arabicMonths.getOrElse(monthNum - 1) { "يناير" }
                val dateRange = getWeekStartAndEnd(yearMonth, weekNum)
                "$weekStr لـ $monthName $year (من ${dateRange.first} إلى ${dateRange.second})"
            } catch (e: Exception) {
                "تصفية أسبوعية مخصصة"
            }
        }
        return try {
            val cleanType = type.trim()
            when {
                cleanType == "يوم" || cleanType == "date" || cleanType == "range" -> {
                    if (value.contains("|")) {
                        val parts = value.split("|")
                        val start = formatDateTo29June2026(parts.getOrElse(0) { "" })
                        val end = formatDateTo29June2026(parts.getOrElse(1) { "" })
                        "من $start إلى $end"
                    } else {
                        formatDateTo29June2026(value)
                    }
                }
                cleanType == "شهر" || cleanType == "month" -> {
                    val cleanVal = value.trim()
                    val arabicMonths = arrayOf(
                        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                    )
                    // If the value is like "2026-06"
                    if (cleanVal.contains("-")) {
                        val parts = cleanVal.split("-")
                        val year = parts.getOrNull(0) ?: "2026"
                        val monthNum = parts.getOrNull(1)?.toIntOrNull() ?: 1
                        val monthName = arabicMonths.getOrElse(monthNum - 1) { "يناير" }
                        "شهر $monthName-$year"
                    } else {
                        // Check if value already has Arabic month name
                        var foundMonth = -1
                        for (i in arabicMonths.indices) {
                            if (cleanVal.contains(arabicMonths[i])) {
                                foundMonth = i
                                break
                            }
                        }
                        if (foundMonth != -1) {
                            cleanVal
                        } else {
                            val monthNum = cleanVal.toIntOrNull() ?: 1
                            val monthName = arabicMonths.getOrElse(monthNum - 1) { "يناير" }
                            "شهر $monthName"
                        }
                    }
                }
                cleanType == "سنة" || cleanType == "year" -> {
                    "سنة $value"
                }
                else -> value
            }
        } catch (e: Exception) {
            value
        }
    }

    fun ymdToCalendar(ymd: Triple<Int, Int, Int>): java.util.Calendar {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.YEAR, ymd.first)
        cal.set(java.util.Calendar.MONTH, ymd.second - 1)
        cal.set(java.util.Calendar.DAY_OF_MONTH, ymd.third)
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal
    }

    fun matchesPeriod(
        dateStr: String,
        period: String, // "يومي", "أسبوعي" (or "اسبوعي"), "شهري", "سنوي", "تقويم مخصص", "حسب اليوم", "حسب الشهر", "حسب السنة", "الكل"
        customValue: String = "", // e.g., "2026-06-30" or "2026-06" or "2026" or "2026-06-01|2026-06-30"
        customType: String = "" // "date", "month", "year" or determined automatically
    ): Boolean {
        val cleanPeriod = period.trim()
        if (cleanPeriod == "الكل" || cleanPeriod == "الكل سجلات" || cleanPeriod == "جميع الأوقات" || cleanPeriod.isEmpty()) return true
        
        val itemYmd = parseAnyDateToYmd(dateStr) ?: return false
        val todayStr = getCurrentDate() // "yyyy-MM-dd"
        val todayYmd = parseAnyDateToYmd(todayStr) ?: return false

        // Check if weekly filter
        if (cleanPeriod == "أسبوعي" || cleanPeriod == "اسبوعي" || cleanPeriod == "أسبوعية" || cleanPeriod == "اسبوعية" || cleanPeriod == "الاسبوعية") {
            val weekFilter = if (customValue.startsWith("WEEK_FILTER|")) {
                customValue
            } else {
                "WEEK_FILTER|${todayStr.substring(0, 7)}|${getCurrentWeekOfMonth()}"
            }
            try {
                val parts = weekFilter.split("|")
                if (parts.size >= 3) {
                    val yearMonth = parts[1] // "yyyy-MM"
                    val weekNum = parts[2].toIntOrNull() ?: 1
                    val itemYmdStr = String.format(Locale.US, "%04d-%02d-%02d", itemYmd.first, itemYmd.second, itemYmd.third)
                    val (startStr, endStr) = getWeekStartAndEnd(yearMonth, weekNum)
                    return itemYmdStr >= startStr && itemYmdStr <= endStr
                }
                return false
            } catch (e: Exception) {
                return false
            }
        }

        return when (cleanPeriod) {
            "يومي", "اليومية", "يومية" -> {
                itemYmd.first == todayYmd.first && itemYmd.second == todayYmd.second && itemYmd.third == todayYmd.third
            }
            "شهري", "الشهرية", "شهرية" -> {
                itemYmd.first == todayYmd.first && itemYmd.second == todayYmd.second
            }
            "سنوي", "السنوية", "سنوية" -> {
                itemYmd.first == todayYmd.first
            }
            "تقويم مخصص", "حسب اليوم", "حسب الشهر", "حسب السنة" -> {
                if (customValue.startsWith("WEEK_FILTER|")) {
                    try {
                        val parts = customValue.split("|")
                        if (parts.size >= 3) {
                            val yearMonth = parts[1] // "yyyy-MM"
                            val weekNum = parts[2].toIntOrNull() ?: 1
                            val itemYmdStr = String.format(Locale.US, "%04d-%02d-%02d", itemYmd.first, itemYmd.second, itemYmd.third)
                            val (startStr, endStr) = getWeekStartAndEnd(yearMonth, weekNum)
                            return itemYmdStr >= startStr && itemYmdStr <= endStr
                        }
                        false
                    } catch (e: Exception) {
                        false
                    }
                } else if (customValue.contains("|")) {
                    val parts = customValue.split("|")
                    val startStr = parts.getOrNull(0) ?: ""
                    val endStr = parts.getOrNull(1) ?: ""
                    val startYmd = parseAnyDateToYmd(startStr) ?: todayYmd
                    val endYmd = parseAnyDateToYmd(endStr) ?: todayYmd
                    
                    val itemCal = ymdToCalendar(itemYmd).timeInMillis
                    val startCal = ymdToCalendar(startYmd).timeInMillis
                    val endCal = ymdToCalendar(endYmd).timeInMillis
                    
                    itemCal in startCal..endCal
                } else {
                    val type = if (customType.isNotEmpty()) {
                        customType
                    } else {
                        when {
                            customValue.length == 4 -> "year"
                            customValue.length == 7 -> "month"
                            else -> "date"
                        }
                    }
                    val targetYmd = parseAnyDateToYmd(customValue) ?: todayYmd
                    when (type) {
                        "date", "يوم", "range" -> {
                            itemYmd.first == targetYmd.first && itemYmd.second == targetYmd.second && itemYmd.third == targetYmd.third
                        }
                        "month", "شهر" -> {
                            itemYmd.first == targetYmd.first && itemYmd.second == targetYmd.second
                        }
                        "year", "سنة" -> {
                            itemYmd.first == targetYmd.first
                        }
                        else -> true
                    }
                }
            }
            else -> true
        }
    }

    fun isBeforePeriod(
        dateStr: String,
        period: String,
        customValue: String = "",
        customType: String = ""
    ): Boolean {
        val cleanPeriod = period.trim()
        if (cleanPeriod == "الكل" || cleanPeriod == "الكل سجلات" || cleanPeriod == "جميع الأوقات") return false
        val itemYmd = parseAnyDateToYmd(dateStr) ?: return false
        
        val todayStr = getCurrentDate() // "yyyy-MM-dd"
        val todayYmd = parseAnyDateToYmd(todayStr) ?: return false
        
        return when (cleanPeriod) {
            "يومي", "اليومية", "يومية" -> {
                val todayCal = ymdToCalendar(todayYmd)
                val itemCal = ymdToCalendar(itemYmd)
                itemCal.timeInMillis < todayCal.timeInMillis
            }
            "أسبوعي", "اسبوعي", "أسبوعية", "اسبوعية", "الاسبوعية" -> {
                val weekFilter = if (customValue.startsWith("WEEK_FILTER|")) {
                    customValue
                } else {
                    "WEEK_FILTER|${todayStr.substring(0, 7)}|${getCurrentWeekOfMonth()}"
                }
                try {
                    val parts = weekFilter.split("|")
                    if (parts.size >= 3) {
                        val yearMonth = parts[1] // "yyyy-MM"
                        val weekNum = parts[2].toIntOrNull() ?: 1
                        
                        val itemYmdStr = String.format(Locale.US, "%04d-%02d-%02d", itemYmd.first, itemYmd.second, itemYmd.third)
                        val (startStr, _) = getWeekStartAndEnd(yearMonth, weekNum)
                        itemYmdStr < startStr
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }
            "شهري", "الشهرية", "شهرية" -> {
                itemYmd.first < todayYmd.first || (itemYmd.first == todayYmd.first && itemYmd.second < todayYmd.second)
            }
            "سنوي", "السنوية", "سنوية" -> {
                itemYmd.first < todayYmd.first
            }
            "تقويم مخصص", "حسب اليوم", "حسب الشهر", "حسب السنة" -> {
                if (customValue.startsWith("WEEK_FILTER|")) {
                    try {
                        val parts = customValue.split("|")
                        if (parts.size >= 3) {
                            val yearMonth = parts[1] // "yyyy-MM"
                            val weekNum = parts[2].toIntOrNull() ?: 1
                            
                            val itemYmdStr = String.format(Locale.US, "%04d-%02d-%02d", itemYmd.first, itemYmd.second, itemYmd.third)
                            val (startStr, _) = getWeekStartAndEnd(yearMonth, weekNum)
                            itemYmdStr < startStr
                        } else {
                            false
                        }
                    } catch (e: Exception) {
                        false
                    }
                } else if (customValue.contains("|")) {
                    val parts = customValue.split("|")
                    val startStr = parts.getOrNull(0) ?: ""
                    val startYmd = parseAnyDateToYmd(startStr) ?: todayYmd
                    
                    val itemCal = ymdToCalendar(itemYmd).timeInMillis
                    val startCal = ymdToCalendar(startYmd).timeInMillis
                    
                    itemCal < startCal
                } else {
                    val type = if (customType.isNotEmpty()) {
                        customType
                    } else {
                        when {
                            customValue.length == 4 -> "year"
                            customValue.length == 7 -> "month"
                            else -> "date"
                        }
                    }
                    
                    val targetYmd = parseAnyDateToYmd(customValue) ?: todayYmd
                    when (type) {
                        "date", "يوم", "range" -> {
                            val targetCal = ymdToCalendar(targetYmd).timeInMillis
                            val itemCal = ymdToCalendar(itemYmd).timeInMillis
                            itemCal < targetCal
                        }
                        "month", "شهر" -> {
                            itemYmd.first < targetYmd.first || (itemYmd.first == targetYmd.first && itemYmd.second < targetYmd.second)
                        }
                        "year", "سنة" -> {
                            itemYmd.first < targetYmd.first
                        }
                        else -> false
                    }
                }
            }
            else -> false
        }
    }

    fun formatMoney(amount: Double): String {
        return String.format(Locale.US, "%,.0f ر.ي", amount)
    }

    fun formatMoneyClean(amount: Double): String {
        return String.format(Locale.US, "%,.0f", amount)
    }

    fun formatWithCurrency(amount: Double, currency: String?): String {
        val num = formatMoneyClean(amount)
        if (currency == null) return "$num ر.ي"
        val c = currency.trim()
        return when {
            c.contains("يمني") || c.contains("ر.ي") || c.contains("YER") -> "$num ر.ي"
            c.contains("سعودي") || c.contains("ر.س") || c.contains("SAR") -> "$num ر.س"
            c.startsWith("ريال") -> {
                if (c.contains("يمني")) "$num ر.ي"
                else if (c.contains("سعودي")) "$num ر.س"
                else "$num ر.ي"
            }
            c.isEmpty() -> "$num ر.ي"
            else -> "$num $c"
        }
    }

    fun formatQuantity(qty: Double): String {
        return if (qty % 1.0 == 0.0) {
            qty.toInt().toString()
        } else {
            val formatted = String.format(Locale.US, "%.3f", qty)
            formatted.replace(Regex("\\.?0+$"), "")
        }
    }

    fun formatQuantityWithUnit(qty: Double): String {
        val intQty = qty.toInt()
        val isInteger = qty % 1.0 == 0.0
        val unit = if (isInteger && intQty in 3..10) "حبات" else "حبة"
        return "${formatQuantity(qty)} $unit"
    }

    fun shareViaWhatsApp(context: Context, phone: String, message: String) {
        try {
            val cleanPhone = if (phone.isNotEmpty()) {
                if (phone.startsWith("+")) phone else "+967$phone"
            } else ""
            
            val intent = Intent(Intent.ACTION_VIEW).apply {
                val url = if (cleanPhone.isNotEmpty()) {
                    "whatsapp://send?phone=$cleanPhone&text=${Uri.encode(message)}"
                } else {
                    "whatsapp://send?text=${Uri.encode(message)}"
                }
                data = Uri.parse(url)
                setPackage("com.whatsapp")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Secondary check for WhatsApp Business
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    val cleanPhone = if (phone.isNotEmpty()) {
                        if (phone.startsWith("+")) phone else "+967$phone"
                    } else ""
                    val url = if (cleanPhone.isNotEmpty()) {
                        "whatsapp://send?phone=$cleanPhone&text=${Uri.encode(message)}"
                    } else {
                        "whatsapp://send?text=${Uri.encode(message)}"
                    }
                    data = Uri.parse(url)
                    setPackage("com.whatsapp.w4b")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                try {
                    // Third attempt: normal web URL redirection
                    val cleanPhone = if (phone.isNotEmpty()) {
                        if (phone.startsWith("+")) phone else "+967$phone"
                    } else ""
                    val intent = Intent(Intent.ACTION_VIEW)
                    val url = "https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}"
                    intent.data = Uri.parse(url)
                    context.startActivity(intent)
                } catch (e3: Exception) {
                    // Fallback general share
                    shareText(context, message)
                }
            }
        }
    }

    fun cleanSmsText(text: String): String {
        // Strip all emojis and special symbols using Unicode blocks and regex
        val regex = Regex("[\\p{So}\\p{Cn}\\uD83C-\\uDBFF\\uDC00-\uDFFF\\u2700-\\u27BF\\u2600-\\u26FF]")
        return text.replace(regex, "")
            .replace("👈", " - ")
            .replace("• •", "•")
            .trim()
    }

    fun shareViaSMS(context: Context, phone: String, message: String) {
        val cleanMsg = cleanSmsText(message)
        try {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:${phone.trim()}")
                putExtra("sms_body", cleanMsg)
                putExtra(Intent.EXTRA_TEXT, cleanMsg)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("sms:${phone.trim()}")
                    putExtra("sms_body", cleanMsg)
                    putExtra(Intent.EXTRA_TEXT, cleanMsg)
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                shareText(context, cleanMsg)
            }
        }
    }

    fun shareGroupSMS(context: Context, phones: List<String>, message: String) {
        val cleanMsg = cleanSmsText(message)
        try {
            val validPhones = phones.map { it.trim() }.filter { it.isNotEmpty() }
            if (validPhones.isEmpty()) {
                val intent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("smsto:")
                    putExtra("sms_body", cleanMsg)
                    putExtra(Intent.EXTRA_TEXT, cleanMsg)
                }
                context.startActivity(intent)
                return
            }
            
            // Build Uri with commas/semicolons depending on systems
            val separator = if (android.os.Build.MANUFACTURER.lowercase().contains("samsung")) ";" else ","
            val joinedPhones = validPhones.joinToString(separator)
            
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$joinedPhones")
                putExtra("sms_body", cleanMsg)
                putExtra(Intent.EXTRA_TEXT, cleanMsg)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            shareText(context, cleanMsg)
        }
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "مشاركة الفاتورة"))
    }

    // Helper to parse totals label and value into 4 professional accounting columns: البيان, الإجمالي, القيمة, الحساب
    fun parseToFourColumns(label: String, valText: String): List<String> {
        val cleanLabel = label.replace("💰", "").replace("💔", "").replace("💸", "").trim()
        val cleanValText = valText.replace("💰", "").replace("💔", "").replace("💸", "").trim()

        var bayan = cleanLabel
        var ejmali = "إجمالي عام"
        var qima = cleanValText
        var hesab = "حساب الصندوق"

        val currency = if (cleanValText.contains("سعودي") || cleanLabel.contains("سعودي") || cleanValText.contains("ر.س") || cleanLabel.contains("ر.س")) {
            "ريال سعودي"
        } else if (cleanValText.contains("يمني") || cleanLabel.contains("يمني") || cleanValText.contains("ر.ي") || cleanLabel.contains("ر.ي")) {
            "ريال يمني"
        } else if (cleanValText.contains("كجم")) {
            "كجم"
        } else if (cleanValText.contains("حبات")) {
            "حبات"
        } else if (cleanValText.contains("حبة")) {
            "حبة"
        } else {
            "حبة / كجم"
        }

        hesab = currency

        val cleanVal = cleanValText
            .replace("ريال يمني", "")
            .replace("ريال سعودي", "")
            .replace("ر.ي", "")
            .replace("ر.س", "")
            .replace("حبات", "")
            .replace("حبة", "")
            .replace("كجم", "")
            .trim()
        
        qima = cleanVal

        if (cleanLabel.contains("صافي أرباح") || cleanLabel.contains("صافي الأرباح") || cleanLabel.contains("صافي الربح") || cleanLabel.contains("صافي ربح") || cleanLabel.contains("الربح")) {
            bayan = "صافي الربح الفعلي"
            ejmali = "الأرباح الفعلية"
        } else if (cleanLabel.contains("صافي الخسارة") || cleanLabel.contains("صافي خسارة") || cleanLabel.contains("الخسارة")) {
            bayan = "صافي الخسارة الفعلية"
            ejmali = "الخسائر الفعلية"
        } else if (cleanLabel.contains("إجمالي المبيعات") || cleanLabel.contains("المبيعات")) {
            bayan = "المبيعات"
            ejmali = "إجمالي المبيعات"
        } else if (cleanLabel.contains("إجمالي المشتريات") || cleanLabel.contains("المشتريات")) {
            bayan = "المشتريات"
            ejmali = "إجمالي الشراء"
        } else if (cleanLabel.contains("المصاريف") || cleanLabel.contains("مصروفات")) {
            bayan = "المصاريف التشغيلية"
            ejmali = "إجمالي المصروفات"
        } else if (cleanLabel.contains("ديون العملاء")) {
            bayan = "ديون العملاء"
            ejmali = "إجمالي ذمم مدينة"
        } else if (cleanLabel.contains("تحصيل")) {
            bayan = "التحصيلات والسدادات"
            ejmali = "إجمالي المقبوضات"
        } else if (cleanLabel.contains("ديون الموردين")) {
            bayan = "ديون الموردين"
            ejmali = "إجمالي ذمم دائنة"
        } else if (cleanLabel.contains("الدفعات المسددة للموردين")) {
            bayan = "سداد الموردين"
            ejmali = "إجمالي المدفوعات"
        } else if (cleanLabel.contains("حوالات") || cleanLabel.contains("الحوالات")) {
            bayan = "حوالات مالية"
            ejmali = "إجمالي الحوالات"
        } else {
            bayan = cleanLabel
                .replace("الريال اليمني", "")
                .replace("الريال السعودي", "")
                .replace("بالريال اليمني", "")
                .replace("بالريال السعودي", "")
                .trim()
            ejmali = "المجموع"
        }

        return listOf(bayan, ejmali, qima, hesab)
    }

    data class PdfBrandingMetadata(
        val agencyTitle: String,
        val tagline: String,
        val ownerLine: String,
        val contactLine: String,
        val commercialLine: String,
        val footerText: String,
        val compactFooterText: String
    )

    fun buildPdfBrandingMetadata(): PdfBrandingMetadata {
        val stripFlags = { s: String -> s.replace("🇾🇪", "").replace("🇸🇦", "").trim() }
        val bAgencyTitle = stripFlags(com.example.util.BrandingManager.resolvedAgencyName())
        val bTagline = stripFlags(com.example.util.BrandingManager.agencyTagline.trim())
        val bOwnerName = stripFlags(com.example.util.BrandingManager.resolvedOwnerName())
        val bPhone = stripFlags(com.example.util.BrandingManager.phone.trim())
        val bAddress = stripFlags(com.example.util.BrandingManager.address.trim())
        val bAdditionalContact = stripFlags(com.example.util.BrandingManager.additionalContact.trim())
        val bCommercial = stripFlags(com.example.util.BrandingManager.commercialDetails.trim())
        val bProdName = stripFlags(com.example.util.BrandingManager.appName.ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR })

        val contactLine = listOfNotNull(
            bPhone.takeIf { it.isNotEmpty() }?.let { "هاتف: $it" },
            bAddress.takeIf { it.isNotEmpty() },
            bAdditionalContact.takeIf { it.isNotEmpty() }
        ).joinToString(" | ")

        val footerText = listOfNotNull(
            "$bAgencyTitle © ${Calendar.getInstance().get(Calendar.YEAR)}",
            bOwnerName.takeIf { it.isNotEmpty() }?.let { "المالك: $it" },
            bPhone.takeIf { it.isNotEmpty() }?.let { "هاتف: $it" }
        ).joinToString(" - ") + " ($bProdName)"

        return PdfBrandingMetadata(
            agencyTitle = bAgencyTitle,
            tagline = bTagline,
            ownerLine = if (bOwnerName.isNotEmpty()) "صاحب الوكالة: $bOwnerName" else "",
            contactLine = contactLine,
            commercialLine = bCommercial,
            footerText = footerText,
            compactFooterText = "$bAgencyTitle • $bProdName ©"
        )
    }

    // Modern Arabic PDF Generator and Native Printer
    fun generatePdfAndShare(
        context: Context,
        title: String,
        headers: List<String>,
        rows: List<List<String>>,
        totals: Map<String, String>,
        customDate: String? = null,
        reportPeriod: String? = null,
        shareToWhatsApp: Boolean = false,
        receiverName: String? = null,
        recipientInfo: String? = null
    ) {
        val stripFlags = { s: String -> s.replace("🇾🇪", "").replace("🇸🇦", "").trim() }
        val title = stripFlags(title)
        val headers = headers.map { stripFlags(it) }
        val rows = rows.map { row -> row.map { stripFlags(it) } }
        val customDate = customDate?.let { stripFlags(it) }
        val reportPeriod = reportPeriod?.let { stripFlags(it) }
        val cleanRecipientInfo = recipientInfo?.let { stripFlags(it) }
        try {
            val mappedTotals = totals.mapKeys { stripFlags(it.key) }.mapValues { stripFlags(it.value) }
                .filter { (label, value) -> label.isNotEmpty() && value.trim().isNotEmpty() }
            val nonZeroTotals = mappedTotals.filter { (_, value) ->
                val cleanVal = value.trim()
                val isZero = cleanVal == "0" || cleanVal == "0.0" || cleanVal == "0.00" ||
                        cleanVal == "0 ر.ي" || cleanVal == "0-ر.ي" || cleanVal == "0-ر.س" ||
                        cleanVal == "0 ر.س" ||
                        cleanVal == "0.0/0.0" ||
                        cleanVal == "0 حبة" || cleanVal == "0.0 حبة" ||
                        cleanVal == "0.00 ر.ي" || cleanVal == "0.00-ر.ي" || cleanVal == "0.00-ر.س" || cleanVal == "0.00 ر.س"
                !isZero
            }
            val cleanTotals = if (nonZeroTotals.isNotEmpty()) nonZeroTotals else mappedTotals
            val pdfDocument = PdfDocument()
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create() // A4 Size
            var page = pdfDocument.startPage(pageInfo)
            var canvas: Canvas = page.canvas

            val pdfColors = com.example.ui.theme.AppThemeManager.getPdfColors(context)

            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 9.5f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }

            val titlePaint = TextPaint().apply {
                color = Color.parseColor(pdfColors.primaryDarkHex) // Dynamic Brand Title Color
                textSize = 15f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }

            val headerPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 10f
                isFakeBoldText = true
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
            }

            val subtitlePaint = TextPaint().apply {
                color = Color.parseColor(pdfColors.accentHex) // Dynamic Brand Accent Color
                textSize = 11f
                isAntiAlias = true
                textAlign = Paint.Align.RIGHT
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor(pdfColors.borderHex)
                style = Paint.Style.STROKE
                strokeWidth = 0.8f
            }

            val headerBgPaint = Paint().apply {
                color = Color.parseColor(pdfColors.primaryDarkHex) // Dynamic Table Header Background
                style = Paint.Style.FILL
            }

            val rowBgPaint = Paint().apply {
                color = Color.parseColor(pdfColors.rowAlternateHex) // Dynamic Alternating Row Background
                style = Paint.Style.FILL
            }

            val leftLimit = 35f
            val startX = 560f
            val tableWidth = startX - leftLimit // 525f

            // Dynamic custom column widths allocator based on headers, actual row values, and content lengths
            val colWidths = FloatArray(headers.size)
            if (headers.isNotEmpty()) {
                val rawWidths = FloatArray(headers.size)
                for (i in headers.indices) {
                    var maxW = textPaint.measureText(headers[i])
                    for (row in rows) {
                        if (i < row.size) {
                            val cellText = row[i]
                            val lines = cellText.split("\n")
                            for (line in lines) {
                                val w = textPaint.measureText(line)
                                if (w > maxW) {
                                    maxW = w
                                }
                            }
                        }
                    }
                    // Add horizontal padding (16dp safety spacing)
                    rawWidths[i] = maxW + 20f
                }

                val minColWidth = 45f
                val totalRequiredMin = headers.size * minColWidth
                val actualMinColWidth = if (totalRequiredMin > tableWidth) {
                    tableWidth / headers.size
                } else {
                    minColWidth
                }

                val cappedWidths = FloatArray(headers.size)
                for (i in headers.indices) {
                    val capped = rawWidths[i].coerceIn(actualMinColWidth, 260f)
                    cappedWidths[i] = capped
                }

                val sumCapped = cappedWidths.sum()
                if (sumCapped > 0f) {
                    for (i in headers.indices) {
                        colWidths[i] = (cappedWidths[i] / sumCapped) * tableWidth
                    }
                } else {
                    for (i in headers.indices) {
                        colWidths[i] = tableWidth / headers.size
                    }
                }
            }

            fun getColWidth(index: Int): Float {
                if (index < 0 || index >= colWidths.size) return tableWidth / headers.size
                return colWidths[index]
            }

            fun getCellRightX(index: Int): Float {
                var sum = 0f
                for (k in 0 until index) {
                    sum += getColWidth(k)
                }
                return startX - sum
            }

            val logoBitmap = try {
                if (com.example.util.BrandingManager.isLogoEnabled) {
                    com.example.util.BrandingManager.getCustomLogoBitmap(context)
                } else {
                    null
                }
            } catch (e: Exception) {
                null
            }

            // Custom local helper to draw main header on page 1 and return bottom Y coordinate
            fun drawMainHeader(can: Canvas): Float {
                can.drawRect(leftLimit, 25f, startX, 31f, Paint().apply { color = Color.parseColor(pdfColors.primaryDarkHex); style = Paint.Style.FILL })
                can.drawRect(leftLimit, 31f, startX, 33f, Paint().apply { color = Color.parseColor(pdfColors.goldHex); style = Paint.Style.FILL })

                val centerX = (startX + leftLimit) / 2

                // 1. Centered Basmala
                val basmalaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(pdfColors.primaryDarkHex)
                    textSize = 13.5f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                can.drawText("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", centerX, 52f, basmalaPaint)

                // 2. Agency Logo on top right (only if custom agency logo was uploaded)
                val hasLogo = logoBitmap != null
                if (hasLogo && logoBitmap != null) {
                    val destRect = RectF(startX - 65f, 38f, startX, 103f)
                    can.drawBitmap(logoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }

                // 3. Centered Dynamic Agency Details
                val pdfMeta = buildPdfBrandingMetadata()
                val displayDate = customDate ?: getCurrentDateTime()

                val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(pdfColors.primaryDarkHex)
                    textSize = 12f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 9.5f
                    isFakeBoldText = false
                    textAlign = Paint.Align.CENTER
                }

                var currentTextY = 72f
                can.drawText(pdfMeta.agencyTitle, centerX, currentTextY, centerTitlePaint)
                currentTextY += 15f

                if (pdfMeta.tagline.isNotEmpty()) {
                    can.drawText(pdfMeta.tagline, centerX, currentTextY, centerTextPaint.apply { isFakeBoldText = false; textSize = 9f; color = Color.DKGRAY })
                    centerTextPaint.color = Color.BLACK
                    currentTextY += 14f
                }

                if (pdfMeta.ownerLine.isNotEmpty()) {
                    can.drawText(pdfMeta.ownerLine, centerX, currentTextY, centerTextPaint.apply { isFakeBoldText = true; textSize = 9.5f })
                    currentTextY += 14f
                }

                if (pdfMeta.contactLine.isNotEmpty()) {
                    can.drawText(pdfMeta.contactLine, centerX, currentTextY, centerTextPaint.apply { isFakeBoldText = false; textSize = 8.8f })
                    currentTextY += 13f
                }

                if (pdfMeta.commercialLine.isNotEmpty()) {
                    can.drawText(pdfMeta.commercialLine, centerX, currentTextY, centerTextPaint.apply { isFakeBoldText = false; textSize = 8.5f })
                    currentTextY += 13f
                }

                can.drawText("تاريخ الطباعة: $displayDate", centerX, currentTextY, centerTextPaint.apply { isFakeBoldText = false; textSize = 9f })
                currentTextY += 14f

                can.drawText("نوع المستند: $title", centerX, currentTextY, centerTextPaint.apply { isFakeBoldText = true; textSize = 9.5f })
                currentTextY += 14f

                if (receiverName != null && receiverName.trim().isNotEmpty()) {
                    centerTextPaint.color = Color.parseColor(pdfColors.accentHex)
                    centerTextPaint.isFakeBoldText = true
                    centerTextPaint.textSize = 10f
                    can.drawText("مستلم مخزون القات: ${stripFlags(receiverName)}", centerX, currentTextY, centerTextPaint)
                    centerTextPaint.color = Color.BLACK
                    currentTextY += 14f
                }

                if (!cleanRecipientInfo.isNullOrEmpty()) {
                    centerTextPaint.color = Color.parseColor(pdfColors.accentHex)
                    centerTextPaint.isFakeBoldText = true
                    centerTextPaint.textSize = 9.5f
                    can.drawText(cleanRecipientInfo, centerX, currentTextY, centerTextPaint)
                    centerTextPaint.color = Color.BLACK
                    currentTextY += 14f
                }

                if (reportPeriod != null && reportPeriod.isNotEmpty()) {
                    centerTextPaint.color = Color.parseColor(pdfColors.primaryDarkHex)
                    centerTextPaint.isFakeBoldText = true
                    centerTextPaint.textSize = 9f
                    can.drawText("الفترة المحددة للتقرير: $reportPeriod", centerX, currentTextY, centerTextPaint)
                    centerTextPaint.color = Color.BLACK
                    currentTextY += 12f
                }

                return maxOf(currentTextY + 4f, if (hasLogo) 114f else 110f)
            }

            // Custom local helper to draw subsequent page headers
            fun drawSubsequentHeader(can: Canvas, pageNum: Int) {
                can.drawRect(leftLimit, 25f, startX, 30f, Paint().apply { color = Color.parseColor(pdfColors.primaryDarkHex); style = Paint.Style.FILL })
                can.drawRect(leftLimit, 30f, startX, 32f, Paint().apply { color = Color.parseColor(pdfColors.goldHex); style = Paint.Style.FILL })

                val centerX = (startX + leftLimit) / 2
                val basmalaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(pdfColors.primaryDarkHex)
                    textSize = 10f
                    isFakeBoldText = true
                    textAlign = Paint.Align.CENTER
                }
                can.drawText("بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ", centerX, 44f, basmalaPaint)

                val hasLogo = logoBitmap != null
                if (hasLogo && logoBitmap != null) {
                    val destRect = RectF(startX - 25f, 34f, startX, 59f)
                    can.drawBitmap(logoBitmap, null, destRect, Paint(Paint.FILTER_BITMAP_FLAG))
                }

                val textEndX = if (hasLogo) startX - 30f else startX
                val bAgencyTitle = stripFlags(com.example.util.BrandingManager.resolvedAgencyName())
                can.drawText("تابع: $title - $bAgencyTitle", textEndX, 56f, titlePaint.apply { textSize = 9f; textAlign = Paint.Align.RIGHT })
                can.drawText("صفحة رقم: $pageNum", leftLimit, 56f, textPaint.apply { isFakeBoldText = true; textSize = 8.5f; textAlign = Paint.Align.LEFT })
                textPaint.textAlign = Paint.Align.RIGHT // Restore alignment

                can.drawLine(leftLimit, 64f, startX, 64f, borderPaint)
            }

            // Custom helper to draw table headers
            fun drawTableHeader(can: Canvas, startY: Float) {
                can.drawRect(leftLimit, startY, startX, startY + 24f, headerBgPaint)
                for (i in headers.indices) {
                    val headerText = headers[i]
                    val cellWidth = getColWidth(i)
                    val cellRightX = getCellRightX(i)
                    val drawX = cellRightX - 6f
                    
                    val paintCopy = TextPaint(headerPaint)
                    val maxCellWidth = cellWidth - 12f
                    var textWidth = paintCopy.measureText(headerText)
                    while (textWidth > maxCellWidth && paintCopy.textSize > 8f) {
                        paintCopy.textSize -= 0.5f
                        textWidth = paintCopy.measureText(headerText)
                    }
                    can.drawText(headerText, drawX, startY + 16f, paintCopy)
                }
                can.drawRect(leftLimit, startY, startX, startY + 24f, borderPaint)
            }

            fun wrapTextToLines(text: String, paint: Paint, maxWidth: Float): List<String> {
                val result = mutableListOf<String>()
                val paragraphs = text.split("\n")
                for (paragraph in paragraphs) {
                    if (paragraph.isEmpty()) {
                        result.add("")
                        continue
                    }
                    val words = paragraph.split(Regex("\\s+")).filter { it.isNotEmpty() }
                    if (words.isEmpty()) {
                        result.add("")
                        continue
                    }
                    var currentLine = StringBuilder()
                    for (word in words) {
                        val testLine = if (currentLine.isEmpty()) word else "${currentLine} ${word}"
                        val testWidth = paint.measureText(testLine)
                        if (testWidth <= maxWidth) {
                            currentLine = StringBuilder(testLine)
                        } else {
                            if (currentLine.isNotEmpty()) {
                                result.add(currentLine.toString())
                                currentLine = StringBuilder(word)
                            } else {
                                var tempWord = word
                                while (tempWord.isNotEmpty()) {
                                    val count = paint.breakText(tempWord, true, maxWidth, null)
                                    if (count <= 0) break
                                    result.add(tempWord.substring(0, count))
                                    tempWord = tempWord.substring(count)
                                }
                            }
                        }
                    }
                    if (currentLine.isNotEmpty()) {
                        result.add(currentLine.toString())
                    }
                }
                return result
            }

            // Safe multi-line drawing function to prevent any text overlaps and wrap long texts beautifully
            fun drawCellTextMultiLine(
                can: Canvas,
                text: String,
                rightX: Float,
                startY: Float,
                maxHeight: Float,
                paint: TextPaint,
                cellWidth: Float
            ) {
                val paintCopy = TextPaint(paint)
                val maxCellWidth = cellWidth - 12f
                val finalLines = wrapTextToLines(text, paintCopy, maxCellWidth)
                
                var currentY = startY
                val lineSpacing = paintCopy.textSize + 2.5f
                
                for (index in finalLines.indices) {
                    if (currentY + lineSpacing > startY + maxHeight) {
                        if (index > 0) {
                            val lastIdx = index - 1
                            val lastLine = finalLines[lastIdx]
                            val truncatedLine = if (lastLine.length > 3) lastLine.substring(0, lastLine.length - 3) + "..." else "..."
                            can.drawText(truncatedLine, rightX - 6f, currentY - lineSpacing, paintCopy)
                        }
                        break
                    }
                    can.drawText(finalLines[index], rightX - 6f, currentY, paintCopy)
                    currentY += lineSpacing
                }
            }

            fun getRequiredRowHeight(row: List<String>, paint: TextPaint): Float {
                var maxLines = 1
                for (i in row.indices) {
                    if (i >= headers.size) break
                    val cellText = row[i]
                    val cellWidth = getColWidth(i)
                    val maxCellWidth = cellWidth - 12f
                    val linesCount = wrapTextToLines(cellText, paint, maxCellWidth).size
                    if (linesCount > maxLines) {
                        maxLines = linesCount
                    }
                }
                val lineSpacing = paint.textSize + 2.5f
                return (maxLines * lineSpacing) + 12f // Add top/bottom padding
            }

            // Draw first page header
            var y = drawMainHeader(canvas)
            canvas.drawLine(leftLimit, y, startX, y, borderPaint)
            y += 12f

            // Start drawing table
            drawTableHeader(canvas, y)
            y += 24f // Header row height

            var alternating = false

            val header2Text = headers.getOrNull(2) ?: ""
            val isWeightCol = header2Text.contains("وزن") || header2Text.contains("الوزن")

            var currentGroupDate: String? = null
            var currentGroupTopY: Float = 0f
            var currentGroupBottomY: Float = 0f
            var currentGroupWeightText: String = ""

            for (rowIndex in rows.indices) {
                val row = rows[rowIndex]
                if (row.isNotEmpty() && row[0].startsWith("SECTION_HEADER|")) {
                    val headerText = row[0].substringAfter("SECTION_HEADER|")
                    val secHeight = 26f
                    if (y + secHeight > 760f) {
                        canvas.drawLine(leftLimit, 785f, startX, 785f, borderPaint)
                        canvas.drawText("صفحة $pageNumber", leftLimit + 10f, 800f, textPaint.apply { textSize = 8f; textAlign = Paint.Align.LEFT })
                        textPaint.textAlign = Paint.Align.RIGHT
                        val bAgencyTitle = stripFlags(com.example.util.BrandingManager.resolvedAgencyName())
                        val bProdName = stripFlags(com.example.util.BrandingManager.appName.ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR })
                        canvas.drawText("$bAgencyTitle • $bProdName ©", startX - 10f, 800f, textPaint.apply { textSize = 8f; color = Color.GRAY })
                        pdfDocument.finishPage(page)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawSubsequentHeader(canvas, pageNumber)
                        y = 75f
                        drawTableHeader(canvas, y)
                        y += 24f
                    }
                    val bgPaint = Paint().apply {
                        color = Color.parseColor(pdfColors.subHeaderBgHex)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(leftLimit, y, startX, y + secHeight, bgPaint)
                    canvas.drawLine(leftLimit, y, startX, y, borderPaint)
                    canvas.drawLine(leftLimit, y + secHeight, startX, y + secHeight, borderPaint)
                    canvas.drawLine(leftLimit, y, leftLimit, y + secHeight, borderPaint)
                    canvas.drawLine(startX, y, startX, y + secHeight, borderPaint)
                    val paintCopy = Paint(textPaint).apply {
                        isFakeBoldText = true
                        textSize = 10.5f
                        color = Color.parseColor(pdfColors.primaryDarkHex)
                        textAlign = Paint.Align.CENTER
                    }
                    val centerX = (startX + leftLimit) / 2f
                    canvas.drawText(headerText, centerX, y + 17f, paintCopy)
                    y += secHeight
                    continue
                }

                if (row.isNotEmpty() && row[0].startsWith("SECTION_TOTALS|")) {
                    val totalsText = row[0].substringAfter("SECTION_TOTALS|")
                    val secHeight = 26f
                    if (y + secHeight > 760f) {
                        canvas.drawLine(leftLimit, 785f, startX, 785f, borderPaint)
                        canvas.drawText("صفحة $pageNumber", leftLimit + 10f, 800f, textPaint.apply { textSize = 8f; textAlign = Paint.Align.LEFT })
                        textPaint.textAlign = Paint.Align.RIGHT
                        val bAgencyTitle = stripFlags(com.example.util.BrandingManager.resolvedAgencyName())
                        val bProdName = stripFlags(com.example.util.BrandingManager.appName.ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR })
                        canvas.drawText("$bAgencyTitle • $bProdName ©", startX - 10f, 800f, textPaint.apply { textSize = 8f; color = Color.GRAY })
                        pdfDocument.finishPage(page)

                        pageNumber++
                        pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(pageInfo)
                        canvas = page.canvas
                        drawSubsequentHeader(canvas, pageNumber)
                        y = 75f
                        drawTableHeader(canvas, y)
                        y += 24f
                    }
                    val bgPaint = Paint().apply {
                        color = Color.parseColor(pdfColors.totalRowBgHex)
                        style = Paint.Style.FILL
                    }
                    canvas.drawRect(leftLimit, y, startX, y + secHeight, bgPaint)
                    canvas.drawLine(leftLimit, y, startX, y, borderPaint)
                    canvas.drawLine(leftLimit, y + secHeight, startX, y + secHeight, borderPaint)
                    canvas.drawLine(leftLimit, y, leftLimit, y + secHeight, borderPaint)
                    canvas.drawLine(startX, y, startX, y + secHeight, borderPaint)
                    val paintCopy = Paint(textPaint).apply {
                        isFakeBoldText = true
                        textSize = 10f
                        color = Color.parseColor(pdfColors.accentHex)
                        textAlign = Paint.Align.RIGHT
                    }
                    canvas.drawText(totalsText, startX - 10f, y + 17f, paintCopy)
                    y += secHeight
                    continue
                }

                val autoRowHeight = getRequiredRowHeight(row, textPaint)
                val rowDate = standardizeDateToYmd(row[0])

                // Dynamic page-break if drawing exceeds page margin
                if (y + autoRowHeight > 760f) {
                    // Draw centered weight on current page before finishing the page
                    if (isWeightCol && currentGroupDate != null) {
                        val rightCol2 = getCellRightX(2)
                        val leftCol2 = getCellRightX(3)
                        val centerX = (rightCol2 + leftCol2) / 2f
                        val centerY = (currentGroupTopY + y) / 2f
                        val paintCopy = Paint(textPaint).apply {
                            isFakeBoldText = true
                            textSize = 9.5f
                            textAlign = Paint.Align.CENTER
                            color = Color.parseColor(pdfColors.primaryDarkHex)
                        }
                        val textY = centerY + (paintCopy.textSize / 3f)
                        canvas.drawText(currentGroupWeightText, centerX, textY, paintCopy)
                    }

                    // Draw bottom footer for the completed page
                    canvas.drawLine(leftLimit, 785f, startX, 785f, borderPaint)
                    canvas.drawText("صفحة $pageNumber", leftLimit + 10f, 800f, textPaint.apply { textSize = 8f; textAlign = Paint.Align.LEFT })
                    textPaint.textAlign = Paint.Align.RIGHT // reset
                    val bAgencyTitle = stripFlags(com.example.util.BrandingManager.resolvedAgencyName())
                    val bProdName = stripFlags(com.example.util.BrandingManager.appName.ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR })
                    canvas.drawText("$bAgencyTitle • $bProdName ©", startX - 10f, 800f, textPaint.apply { textSize = 8f; color = Color.GRAY })
                    
                    pdfDocument.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                    page = pdfDocument.startPage(pageInfo)
                    canvas = page.canvas

                    drawSubsequentHeader(canvas, pageNumber)
                    y = 75f
                    drawTableHeader(canvas, y)
                    y += 24f

                    // Reset group tracking on the new page
                    if (isWeightCol) {
                        currentGroupDate = rowDate
                        currentGroupTopY = y
                        currentGroupWeightText = ""
                        for (k in rowIndex until rows.size) {
                            if (standardizeDateToYmd(rows[k][0]) != rowDate) break
                            if (rows[k][2].isNotEmpty()) {
                                currentGroupWeightText = rows[k][2]
                                break
                            }
                        }
                        if (currentGroupWeightText.isEmpty()) {
                            currentGroupWeightText = "-"
                        }
                    }
                }

                // If not just handled by page-break block, initialize new group
                if (isWeightCol && (currentGroupDate == null || rowDate != currentGroupDate)) {
                    currentGroupDate = rowDate
                    currentGroupTopY = y
                    currentGroupWeightText = ""
                    for (k in rowIndex until rows.size) {
                        if (standardizeDateToYmd(rows[k][0]) != rowDate) break
                        if (rows[k][2].isNotEmpty()) {
                            currentGroupWeightText = rows[k][2]
                            break
                        }
                    }
                    if (currentGroupWeightText.isEmpty()) {
                        currentGroupWeightText = "-"
                    }
                }
                currentGroupBottomY = y + autoRowHeight

                // Drawing alternating background
                if (alternating) {
                    canvas.drawRect(leftLimit, y, startX, y + autoRowHeight, rowBgPaint)
                }
                alternating = !alternating

                // Draw cell contents (skip weight column index 2 if isWeightCol)
                for (i in row.indices) {
                    if (i >= headers.size) break // Safety safeguard
                    if (i == 2 && isWeightCol) continue // Skip weight column, drawn once per group!
                    val cellText = row[i]
                    val cellWidth = getColWidth(i)
                    val cellRightX = getCellRightX(i)
                    drawCellTextMultiLine(canvas, cellText, cellRightX, y + 14f, autoRowHeight - 4f, textPaint, cellWidth)
                }

                // Draw perfect cell borders
                val nextRow = rows.getOrNull(rowIndex + 1)
                val isSameGroupAsNext = nextRow != null &&
                        standardizeDateToYmd(row[0]) == standardizeDateToYmd(nextRow[0])
                
                val nextRowHeight = if (nextRow != null) getRequiredRowHeight(nextRow, textPaint) else 0f
                val willPageBreak = (y + autoRowHeight + nextRowHeight > 760f)
                val skipBottomLineForCol2 = isWeightCol && isSameGroupAsNext && !willPageBreak

                if (skipBottomLineForCol2) {
                    // Draw bottom line skipping col 2
                    val rightCol2 = getCellRightX(2)
                    canvas.drawLine(rightCol2, y + autoRowHeight, startX, y + autoRowHeight, borderPaint)
                    val leftCol2 = getCellRightX(3)
                    canvas.drawLine(leftLimit, y + autoRowHeight, leftCol2, y + autoRowHeight, borderPaint)
                } else {
                    // Draw full bottom line
                    canvas.drawLine(leftLimit, y + autoRowHeight, startX, y + autoRowHeight, borderPaint)
                }

                // Draw vertical column dividers
                for (i in 1 until headers.size) {
                    val lineX = getCellRightX(i)
                    canvas.drawLine(lineX, y, lineX, y + autoRowHeight, borderPaint)
                }

                // Draw outer vertical borders
                canvas.drawLine(leftLimit, y, leftLimit, y + autoRowHeight, borderPaint)
                canvas.drawLine(startX, y, startX, y + autoRowHeight, borderPaint)

                // Draw centered weight text if end of group on page
                val isGroupEndOnPage = (rowIndex == rows.size - 1) ||
                        (standardizeDateToYmd(rows.getOrNull(rowIndex + 1)?.get(0) ?: "") != currentGroupDate) ||
                        willPageBreak

                if (isWeightCol && isGroupEndOnPage) {
                    val rightCol2 = getCellRightX(2)
                    val leftCol2 = getCellRightX(3)
                    val centerX = (rightCol2 + leftCol2) / 2f
                    val centerY = (currentGroupTopY + currentGroupBottomY) / 2f
                    val paintCopy = Paint(textPaint).apply {
                        isFakeBoldText = true
                        textSize = 9.5f
                        textAlign = Paint.Align.CENTER
                        color = Color.parseColor(pdfColors.primaryDarkHex)
                    }
                    val textY = centerY + (paintCopy.textSize / 3f)
                    canvas.drawText(currentGroupWeightText, centerX, textY, paintCopy)

                    currentGroupDate = null // Reset group tracking
                }

                y += autoRowHeight
            }

            // Draw totals section with check to wrap if overflow
            val totalsHeight = ((cleanTotals.size + 1) * 24f) + 35f
            if (y + totalsHeight > 760f) {
                // Page-break
                canvas.drawLine(leftLimit, 785f, startX, 785f, borderPaint)
                canvas.drawText("صفحة $pageNumber", leftLimit + 10f, 800f, textPaint.apply { textSize = 8f; textAlign = Paint.Align.LEFT })
                textPaint.textAlign = Paint.Align.RIGHT // reset
                val bAgencyTitle = stripFlags(com.example.util.BrandingManager.resolvedAgencyName())
                val bProdName = stripFlags(com.example.util.BrandingManager.appName.ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR })
                canvas.drawText("$bAgencyTitle • $bProdName ©", startX - 10f, 800f, textPaint.apply { textSize = 8f; color = Color.GRAY })

                pdfDocument.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas

                drawSubsequentHeader(canvas, pageNumber)
                y = 75f
            }

            // Render beautifully aligned totals box as a structured table with 4 columns: البيان, الإجمالي, القيمة, الحساب
            y += 12f
            val headerRowHeight = 24f
            val totalRowHeight = 22f
            
            val tColWidths = floatArrayOf(
                tableWidth * 0.35f, // البيان
                tableWidth * 0.25f, // الإجمالي
                tableWidth * 0.20f, // القيمة
                tableWidth * 0.20f  // الحساب
            )
            
            fun getTCellRightX(index: Int): Float {
                var sum = 0f
                for (k in 0 until index) {
                    if (k < tColWidths.size) {
                        sum += tColWidths[k]
                    }
                }
                return startX - sum
            }

            // Draw Totals Header
            canvas.drawRect(leftLimit, y, startX, y + headerRowHeight, headerBgPaint)
            val headerTextPaint = TextPaint(headerPaint).apply {
                textSize = 9.5f
                isFakeBoldText = true
            }
            
            val tHeaders = listOf("البيان", "الإجمالي", "القيمة", "الحساب")
            for (i in tHeaders.indices) {
                val tHeader = tHeaders[i]
                val cellWidth = tColWidths[i]
                val cellRightX = getTCellRightX(i)
                val drawX = cellRightX - 6f
                
                val paintCopy = TextPaint(headerTextPaint)
                if (i >= 2) {
                    paintCopy.textAlign = Paint.Align.CENTER
                    val drawCenterX = cellRightX - (cellWidth / 2f)
                    canvas.drawText(tHeader, drawCenterX, y + 16f, paintCopy)
                } else {
                    paintCopy.textAlign = Paint.Align.RIGHT
                    canvas.drawText(tHeader, drawX, y + 16f, paintCopy)
                }
            }
            
            // Draw borders for header
            canvas.drawRect(leftLimit, y, startX, y + headerRowHeight, borderPaint)
            for (i in 1 until tHeaders.size) {
                val lineX = getTCellRightX(i)
                canvas.drawLine(lineX, y, lineX, y + headerRowHeight, borderPaint)
            }
            
            var totalsY = y + headerRowHeight
            
            val totalLabelPaint = TextPaint(textPaint).apply {
                isFakeBoldText = true
                textSize = 9f
                color = Color.BLACK
                textAlign = Paint.Align.RIGHT
            }
            
            for ((label, valText) in cleanTotals) {
                // Background fill
                canvas.drawRect(leftLimit, totalsY, startX, totalsY + totalRowHeight, Paint().apply {
                    color = Color.parseColor(pdfColors.totalRowBgHex)
                    style = Paint.Style.FILL
                })
                
                val tCells = parseToFourColumns(label, valText)
                
                // Draw texts
                for (i in tCells.indices) {
                    if (i >= tColWidths.size) break
                    val cellText = tCells[i]
                    val cellWidth = tColWidths[i]
                    val cellRightX = getTCellRightX(i)
                    
                    val cellPaint = TextPaint(totalLabelPaint)
                    if (i == 2) {
                        cellPaint.color = Color.parseColor(pdfColors.accentHex) // Dynamic Accent for numerical values
                    } else if (i == 3) {
                        cellPaint.color = Color.parseColor(pdfColors.primaryDarkHex) // Dynamic Primary for Accounts/Currencies
                    }
                    
                    if (i >= 2) {
                        cellPaint.textAlign = Paint.Align.CENTER
                        val drawCenterX = cellRightX - (cellWidth / 2f)
                        canvas.drawText(cellText, drawCenterX, totalsY + 15f, cellPaint)
                    } else {
                        cellPaint.textAlign = Paint.Align.RIGHT
                        canvas.drawText(cellText, cellRightX - 6f, totalsY + 15f, cellPaint)
                    }
                }
                
                // Draw borders
                canvas.drawRect(leftLimit, totalsY, startX, totalsY + totalRowHeight, borderPaint)
                for (i in 1 until tHeaders.size) {
                    val lineX = getTCellRightX(i)
                    canvas.drawLine(lineX, totalsY, lineX, totalsY + totalRowHeight, borderPaint)
                }
                
                totalsY += totalRowHeight
            }
            y = totalsY + 20f

            // Clean final footer
            val footerY = 800f
            canvas.drawLine(leftLimit, footerY - 14f, startX, footerY - 14f, borderPaint)
            canvas.drawText("صفحة $pageNumber", leftLimit + 10f, footerY, textPaint.apply {
                color = Color.DKGRAY
                textSize = 8f
                isFakeBoldText = false
                textAlign = Paint.Align.LEFT
            })
            val footerMeta = buildPdfBrandingMetadata()
            canvas.drawText(footerMeta.footerText, startX - 10f, footerY, textPaint.apply {
                color = Color.parseColor(pdfColors.primaryDarkHex)
                textSize = 8.5f
                isFakeBoldText = true
                textAlign = Paint.Align.RIGHT
            })

            pdfDocument.finishPage(page)

            // Creating a perfectly descriptive Arabic File Name based on the document's Arabic title prefix!
            val fileDate = if (customDate != null && customDate.length >= 10) {
                customDate.substring(0, 10).replace("/", "-")
            } else {
                getCurrentDate()
            }
            val bAppName = com.example.util.BrandingManager.appName.ifEmpty { com.example.util.BrandingManager.PRODUCT_NAME_AR }
            val bAgencyName = footerMeta.agencyTitle
            val sanitizedTitle = title
                .replace("$bAppName $bAgencyName بجميع أنواعها •", "")
                .replace("$bAppName $bAgencyName بجميع أنواعها", "")
                .replace("$bAppName $bAgencyName •", "")
                .replace("$bAppName $bAgencyName", "")
                .replace("$bAppName بجميع أنواعها •", "")
                .replace("$bAppName بجميع أنواعها", "")
                .replace("$bAppName للقات", "")
                .replace("$bAppName •", "")
                .replace(bAppName, "")
                .replace(bAgencyName, "")
                .replace(Regex("[#\\\\/:*?\"<>|]"), "")
                .trim()
                .replace("\\s+".toRegex(), "_")
            val fileName = if (sanitizedTitle.startsWith("تقرير_") || sanitizedTitle.startsWith("كشف_")) {
                "${sanitizedTitle}_${fileDate}.pdf"
            } else {
                "تقرير_${sanitizedTitle}_${fileDate}.pdf"
            }

            val filepath = (context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.filesDir).apply {
                if (!exists()) mkdirs()
            }
            val file = File(filepath, fileName)
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            pdfDocument.close()
            outputStream.flush()
            outputStream.close()

            // Function to trigger sharing as fallback
            fun triggerShareFallback() {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(Intent.createChooser(intent, "تصدير ومشاركة PDF"))
                    Toast.makeText(context, "تم تصدير وحفظ الطبعة كملف PDF ومشاركته بنجاح: $fileName", Toast.LENGTH_LONG).show()
                } catch (shareErr: Exception) {
                    Toast.makeText(context, "فشل تصدير ومشاركة PDF: ${shareErr.message}", Toast.LENGTH_LONG).show()
                }
            }

            // Check if specifically sharing to WhatsApp
            if (shareToWhatsApp) {
                try {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/pdf"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setPackage("com.whatsapp")
                    }
                    context.startActivity(intent)
                    Toast.makeText(context, "تم تصدير الطبعة والمشاركة عبر واتساب بنجاح: $fileName", Toast.LENGTH_LONG).show()
                } catch (shareErr: Exception) {
                    triggerShareFallback()
                }
                return
            }

            // START NATIVE ANDROID SYSTEM PRINT DIALOG!
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            if (printManager != null) {
                val bAgencyJob = com.example.util.BrandingManager.resolvedAgencyName()
                val jobName = "${bAgencyJob.replace(" ", "_")}_${sanitizedTitle}"
                val printAdapter = object : PrintDocumentAdapter() {
                    override fun onLayout(
                        oldAttributes: PrintAttributes?,
                        newAttributes: PrintAttributes?,
                        cancellationSignal: CancellationSignal?,
                        callback: LayoutResultCallback?,
                        extras: Bundle?
                    ) {
                        try {
                            if (cancellationSignal?.isCanceled == true) {
                                callback?.onLayoutCancelled()
                                return
                            }
                            val info = PrintDocumentInfo.Builder(fileName)
                                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                                .build()
                            callback?.onLayoutFinished(info, true)
                        } catch (ex: Exception) {
                            callback?.onLayoutFailed(ex.toString())
                        }
                    }

                    override fun onWrite(
                        pages: Array<out android.print.PageRange>?,
                        destination: ParcelFileDescriptor?,
                        cancellationSignal: CancellationSignal?,
                        callback: WriteResultCallback?
                    ) {
                        var input: FileInputStream? = null
                        var output: FileOutputStream? = null
                        try {
                            input = FileInputStream(file)
                            output = FileOutputStream(destination?.fileDescriptor)
                            val buf = ByteArray(1024)
                            var bytesRead: Int
                            while (input.read(buf).also { bytesRead = it } > 0) {
                                output.write(buf, 0, bytesRead)
                            }
                            callback?.onWriteFinished(arrayOf(android.print.PageRange.ALL_PAGES))
                        } catch (e: Exception) {
                            callback?.onWriteFailed(e.toString())
                        } finally {
                            try { input?.close() } catch (ignored: Exception) {}
                            try { output?.close() } catch (ignored: Exception) {}
                        }
                    }
                }
                
                try {
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                    Toast.makeText(context, "تم حفظ المستند وبدء عملية الطباعة لمستند: $fileName", Toast.LENGTH_SHORT).show()
                } catch (printEx: Exception) {
                    // Fallback to sharing if native print dialog trigger fails
                    triggerShareFallback()
                }
            } else {
                // No print manager available: fallback to share
                triggerShareFallback()
            }
        } catch (e: Exception) {
            Toast.makeText(context, "فشل تصدير وطباعة PDF: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    fun backupDatabase(context: Context): Boolean {
        try {
            com.example.data.AppDatabase.checkpoint()
            val dbFile = context.getDatabasePath("tofan_al_aqsa_qat_db")
            val dbWal = File(dbFile.path + "-wal")
            val dbShm = File(dbFile.path + "-shm")

            val backupDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return false
            if (!backupDir.exists()) {
                backupDir.mkdirs()
            }
            val backupDb = File(backupDir, "qat_ledger_db_backup.db")
            val backupWal = File(backupDir, "qat_ledger_db_backup.db-wal")
            val backupShm = File(backupDir, "qat_ledger_db_backup.db-shm")

            if (dbFile.exists()) {
                dbFile.inputStream().use { input ->
                    backupDb.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }
            if (dbWal.exists()) {
                dbWal.inputStream().use { input ->
                    backupWal.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else if (backupWal.exists()) {
                backupWal.delete()
            }
            if (dbShm.exists()) {
                dbShm.inputStream().use { input ->
                    backupShm.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            } else if (backupShm.exists()) {
                backupShm.delete()
            }

            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                backupDb
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "تصدير وحفظ النسخة الاحتياطية"))
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    fun restoreDatabase(context: Context, backupUri: Uri): Boolean {
        try {
            try {
                com.example.data.AppDatabase.closeDatabase()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            val dbFile = context.getDatabasePath("tofan_al_aqsa_qat_db")
            val dbWal = File(dbFile.path + "-wal")
            val dbShm = File(dbFile.path + "-shm")

            val tempFile = File.createTempFile("db_restore", null, context.cacheDir)
            context.contentResolver.openInputStream(backupUri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            if (!tempFile.exists() || tempFile.length() == 0L) {
                return false
            }

            if (dbFile.exists()) dbFile.delete()
            if (dbWal.exists()) dbWal.delete()
            if (dbShm.exists()) dbShm.delete()

            tempFile.copyTo(dbFile, overwrite = true)
            tempFile.delete()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}

class BluetoothPrinterManager private constructor(private val context: Context) {

    var connectedDeviceName: String = "غير متصل"
        private set

    var connectedDeviceAddress: String = ""
        private set

    var currentPrinterSize: Int = 58 // 58mm or 80mm
        set

    fun savePrinterConfig(size: Int, deviceAddress: String) {
        currentPrinterSize = if (size >= 80) 80 else 58
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        val editor = prefs.edit().putInt("size", currentPrinterSize)
        if (deviceAddress.isNotBlank()) {
            connectedDeviceAddress = deviceAddress
            editor.putString("address", deviceAddress)
        }
        editor.apply()
    }

    init {
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        currentPrinterSize = if (prefs.getInt("size", 58) >= 80) 80 else 58
        val savedAddr = prefs.getString("address", "").orEmpty()
        val savedName = prefs.getString("name", "").orEmpty()
        connectedDeviceAddress = savedAddr
        connectedDeviceName = when {
            savedName.isNotBlank() && savedAddr.isNotBlank() -> "$savedName ($savedAddr)"
            savedAddr.isNotBlank() -> savedAddr
            else -> "غير متصل"
        }
    }

    fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getBondedPrinters(): List<Pair<String, String>> {
        if (!hasBluetoothPermission()) return emptyList()
        return try {
            val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
            if (adapter == null || !adapter.isEnabled) {
                emptyList()
            } else {
                adapter.bondedDevices
                    ?.map { device -> (device.name ?: "جهاز بلوتوث") to device.address }
                    ?.sortedByDescending { (name, addr) -> getDeviceTypeNameAndIsPrinter(name, addr).second }
                    ?: emptyList()
            }
        } catch (_: SecurityException) {
            emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun getDeviceTypeNameAndIsPrinter(name: String, address: String): Pair<String, Boolean> {
        val lowerName = name.lowercase()
        if (lowerName.contains("طابعة") || lowerName.contains("printer") ||
            lowerName.contains("mtp") || lowerName.contains("pos") ||
            lowerName.contains("thermal") || lowerName.contains("xprinter") ||
            lowerName.contains("rpp") || lowerName.contains("zkteco") ||
            lowerName.contains("bixolon") || lowerName.contains("epson")
        ) {
            return Pair("طابعة حرارية مدعومة", true)
        }

        try {
            if (hasBluetoothPermission() && address.isNotBlank()) {
                val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
                val device = adapter?.getRemoteDevice(address)
                val bClass = device?.bluetoothClass
                if (bClass != null) {
                    val major = bClass.majorDeviceClass
                    val deviceClassVal = bClass.deviceClass

                    if (deviceClassVal == 1664 || major == android.bluetooth.BluetoothClass.Device.Major.IMAGING) {
                        return Pair("طابعة حرارية", true)
                    }

                    when (major) {
                        android.bluetooth.BluetoothClass.Device.Major.PHONE -> return Pair("هاتف محمول ذكي", false)
                        android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> return Pair("كمبيوتر / لابتوب", false)
                        android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> return Pair("سماعة رأس / جهاز وسائط", false)
                        android.bluetooth.BluetoothClass.Device.Major.TOY -> return Pair("جهاز ترفيهي", false)
                        android.bluetooth.BluetoothClass.Device.Major.HEALTH -> return Pair("جهاز صحي", false)
                        android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL -> return Pair("جهاز إدخال طرفي", false)
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }

        if (lowerName.contains("phone") || lowerName.contains("galaxy") || lowerName.contains("iphone") || lowerName.contains("redmi") || lowerName.contains("pixel") || lowerName.contains("huawei")) {
            return Pair("هاتف محمول", false)
        }
        if (lowerName.contains("headset") || lowerName.contains("earbud") || lowerName.contains("headphone") || lowerName.contains("speaker") || lowerName.contains("sound") || lowerName.contains("pod") || lowerName.contains("buds") || lowerName.contains("سماعة")) {
            return Pair("سماعة رأس أو مكبر صوت", false)
        }
        if (lowerName.contains("macbook") || lowerName.contains("laptop") || lowerName.contains("pc") || lowerName.contains("computer") || lowerName.contains("desktop") || lowerName.contains("dell") || lowerName.contains("لابتوب")) {
            return Pair("جهاز كمبيوتر / لابتوب", false)
        }
        if (lowerName.contains("tv") || lowerName.contains("firestick") || lowerName.contains("chromecast") || lowerName.contains("display")) {
            return Pair("شاشة تلفاز ذكية", false)
        }
        if (lowerName.contains("watch") || lowerName.contains("band") || lowerName.contains("fitbit")) {
            return Pair("ساعة ذكية / سوار رياضي", false)
        }

        return Pair("جهاز بلوتوث مقترن (قد يدعم SPP)", true)
    }

    fun connectPrinter(name: String, address: String, onComplete: (Boolean) -> Unit) {
        connectedDeviceAddress = address
        connectedDeviceName = "$name ($address)"
        val prefs = context.getSharedPreferences("printer_prefs", Context.MODE_PRIVATE)
        prefs.edit().putString("address", address).putString("name", name).apply()
        onComplete(true)
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun printEscPosReceipt(
        receiptText: String,
        deviceAddress: String = connectedDeviceAddress,
        onFinished: (success: Boolean, message: String) -> Unit
    ) {
        if (!hasBluetoothPermission()) {
            onFinished(false, "يرجى منح صلاحية البلوتوث للاتصال بالطابعة.")
            return
        }
        val adapter = android.bluetooth.BluetoothAdapter.getDefaultAdapter()
        if (adapter == null || !adapter.isEnabled) {
            onFinished(false, "البلوتوث غير مفعل أو غير مدعوم على هذا الجهاز.")
            return
        }
        if (deviceAddress.isBlank()) {
            onFinished(false, "لم يتم تحديد عنوان طابعة بلوتوث مقترنة.")
            return
        }

        Thread {
            var socket: android.bluetooth.BluetoothSocket? = null
            try {
                val device = adapter.getRemoteDevice(deviceAddress)
                val sppUuid = java.util.UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
                socket = device.createRfcommSocketToServiceRecord(sppUuid)
                adapter.cancelDiscovery()
                socket.connect()

                var formattedPayload = ""
                virtualPrint(receiptText) { built ->
                    formattedPayload = built
                }

                val out = socket.outputStream
                // ESC/POS Initialize + UTF-8/Arabic print bytes + feed + partial cut
                out.write(byteArrayOf(0x1B, 0x40))
                out.write(formattedPayload.toByteArray(Charsets.UTF_8))
                out.write("\n\n\n".toByteArray(Charsets.UTF_8))
                out.write(byteArrayOf(0x1D, 0x56, 0x42, 0x00))
                out.flush()
                onFinished(true, "تم إرسال السند إلى الطابعة بنجاح.")
            } catch (e: Exception) {
                onFinished(false, "تعذر الإرسال للطابعة: ${e.message ?: "فشل الاتصال عبر البلوتوث"}")
            } finally {
                try {
                    socket?.close()
                } catch (_: Exception) {
                }
            }
        }.start()
    }

    fun centerText(text: String, width: Int): String {
        val clean = text.trim()
        if (clean.length >= width) return clean
        val spaces = (width - clean.length) / 2
        return " ".repeat(spaces) + clean + " ".repeat(width - clean.length - spaces)
    }

    fun formatDualColumn(left: String, right: String, width: Int): String {
        val leftClean = left.trim()
        val rightClean = right.trim()
        val totalLen = leftClean.length + rightClean.length
        if (totalLen >= width) {
            return "$rightClean\n$leftClean"
        }
        val spaces = width - totalLen
        return leftClean + " ".repeat(spaces) + rightClean
    }

    fun formatThreeColumns(left: String, middle: String, right: String, wLeft: Int, wMiddle: Int, wRight: Int): String {
        val l = left.trim().padEnd(wLeft, ' ').take(wLeft)
        
        val mTrim = middle.trim()
        val m = if (mTrim.length < wMiddle) {
            val start = (wMiddle - mTrim.length) / 2
            val end = wMiddle - mTrim.length - start
            " ".repeat(start) + mTrim + " ".repeat(end)
        } else {
            mTrim.take(wMiddle)
        }
        
        val r = right.trim().padStart(wRight, ' ').take(wRight)
        
        return l + m + r
    }

    fun wrapText(text: String, width: Int): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""
        for (word in words) {
            if (currentLine.isEmpty()) {
                currentLine = word
            } else if (currentLine.length + 1 + word.length <= width) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        return lines
    }

    fun drawGridTable(
        colWidths: List<Int>,
        headers: List<String>,
        rows: List<List<String>>
    ): String {
        val sb = java.lang.StringBuilder()
        
        // 1. Draw top border: ┌───┬───┐
        sb.append("┌")
        for (i in colWidths.indices) {
            sb.append("─".repeat(colWidths[i]))
            if (i < colWidths.size - 1) sb.append("┬")
        }
        sb.append("┐\n")
        
        // 2. Draw headers: │ Col1 │ Col2 │
        if (headers.isNotEmpty()) {
            val wrappedHeaders = headers.mapIndexed { idx, h -> wrapTextToWidth(h, colWidths[idx]) }
            val maxHeaderLines = wrappedHeaders.maxOfOrNull { it.size } ?: 1
            for (lineIdx in 0 until maxHeaderLines) {
                sb.append("│")
                for (colIdx in colWidths.indices) {
                    val line = wrappedHeaders[colIdx].getOrNull(lineIdx) ?: ""
                    val padded = padTextCenter(line, colWidths[colIdx])
                    sb.append(padded).append("│")
                }
                sb.append("\n")
            }
            
            // Draw header separator: ├───┼───┤
            sb.append("├")
            for (i in colWidths.indices) {
                sb.append("─".repeat(colWidths[i]))
                if (i < colWidths.size - 1) sb.append("┼")
            }
            sb.append("┤\n")
        }
        
        // 3. Draw rows: │ Cell1 │ Cell2 │
        for (rowIdx in rows.indices) {
            val row = rows[rowIdx]
            val wrappedCells = colWidths.mapIndexed { colIdx, width ->
                val cellText = row.getOrNull(colIdx) ?: ""
                wrapTextToWidth(cellText, width)
            }
            val maxCellLines = wrappedCells.maxOfOrNull { it.size } ?: 1
            for (lineIdx in 0 until maxCellLines) {
                sb.append("│")
                for (colIdx in colWidths.indices) {
                    val line = wrappedCells[colIdx].getOrNull(lineIdx) ?: ""
                    val padded = if (colWidths.size > 1 && colIdx == colWidths.size - 1) {
                        padTextRight(line, colWidths[colIdx])
                    } else if (colIdx == 0) {
                        padTextLeft(line, colWidths[colIdx])
                    } else {
                        padTextCenter(line, colWidths[colIdx])
                    }
                    sb.append(padded).append("│")
                }
                sb.append("\n")
            }
            
            // Draw middle row separator: ├──────┼──────┤
            if (rowIdx < rows.size - 1) {
                sb.append("├")
                for (i in colWidths.indices) {
                    sb.append("─".repeat(colWidths[i]))
                    if (i < colWidths.size - 1) sb.append("┼")
                }
                sb.append("┤\n")
            }
        }
        
        // 4. Draw bottom border: └───┴───┘
        sb.append("└")
        for (i in colWidths.indices) {
            sb.append("─".repeat(colWidths[i]))
            if (i < colWidths.size - 1) sb.append("┴")
        }
        sb.append("┘\n")
        
        return sb.toString()
    }

    fun padTextRight(text: String, width: Int): String {
        val clean = text.trim()
        if (clean.length >= width) return clean.take(width)
        return " ".repeat(width - clean.length) + clean
    }

    fun padTextLeft(text: String, width: Int): String {
        val clean = text.trim()
        if (clean.length >= width) return clean.take(width)
        return clean + " ".repeat(width - clean.length)
    }

    fun padTextCenter(text: String, width: Int): String {
        val clean = text.trim()
        if (clean.length >= width) return clean.take(width)
        val spaces = width - clean.length
        val left = spaces / 2
        val right = spaces - left
        return " ".repeat(left) + clean + " ".repeat(right)
    }

    fun wrapTextToWidth(text: String, width: Int): List<String> {
        val lines = text.split("\n")
        val result = mutableListOf<String>()
        for (line in lines) {
            val clean = line.trim()
            if (clean.isEmpty()) {
                result.add("")
                continue
            }
            if (clean.length <= width) {
                result.add(clean)
                continue
            }
            
            var current = ""
            val words = clean.split(" ")
            for (word in words) {
                if (word.isEmpty()) continue
                if (word.length > width) {
                    if (current.isNotEmpty()) {
                        result.add(current)
                        current = ""
                    }
                    var temp = word
                    while (temp.length > width) {
                        result.add(temp.substring(0, width))
                        temp = temp.substring(width)
                    }
                    current = temp
                } else if (current.isEmpty()) {
                    current = word
                } else if (current.length + 1 + word.length <= width) {
                    current += " $word"
                } else {
                    result.add(current)
                    current = word
                }
            }
            if (current.isNotEmpty()) {
                result.add(current)
            }
        }
        return result
    }

    fun filterReceiptTextByCurrency(rawText: String): String {
        val lines = rawText.split("\n")
        
        var hasNonZeroYer = false
        var hasNonZeroSar = false
        
        var hasAnyYerMention = false
        var hasAnySarMention = false
        
        val yerKeywords = listOf("يمني", "ريال يمني", "🇾🇪")
        val sarKeywords = listOf("سعودي", "ريال سعودي", "🇸🇦")
        
        fun extractNumbers(text: String): List<Double> {
            val result = mutableListOf<Double>()
            val regex = Regex("-?\\+?\\b\\d{1,3}(?:,\\d{3})*(?:\\.\\d+)?\\b|-?\\+?\\b\\d+(?:\\.\\d+)?\\b")
            val matches = regex.findAll(text)
            for (match in matches) {
                try {
                    val cleanNum = match.value.replace(",", "")
                    val value = cleanNum.toDouble()
                    result.add(value)
                } catch (e: Exception) {
                    // Ignore
                }
            }
            return result
        }

        for (line in lines) {
            val hasYerKey = yerKeywords.any { line.contains(it) }
            val hasSarKey = sarKeywords.any { line.contains(it) }
            
            if (hasYerKey) {
                hasAnyYerMention = true
                val numbers = extractNumbers(line)
                if (numbers.any { it > 0.0 }) {
                    hasNonZeroYer = true
                }
            }
            if (hasSarKey) {
                hasAnySarMention = true
                val numbers = extractNumbers(line)
                if (numbers.any { it > 0.0 }) {
                    hasNonZeroSar = true
                }
            }
        }
        
        if (hasAnyYerMention && hasAnySarMention) {
            if (hasNonZeroYer && !hasNonZeroSar) {
                val filteredLines = lines.filter { line ->
                    val hasSarKey = sarKeywords.any { line.contains(it) }
                    val hasYerKey = yerKeywords.any { line.contains(it) }
                    
                    if (hasSarKey && !hasYerKey) {
                        false
                    } else if (hasSarKey && hasYerKey) {
                        val numbers = extractNumbers(line)
                        numbers.any { it > 0.0 }
                    } else {
                        true
                    }
                }
                return filteredLines.joinToString("\n")
            } else if (hasNonZeroSar && !hasNonZeroYer) {
                val filteredLines = lines.filter { line ->
                    val hasSarKey = sarKeywords.any { line.contains(it) }
                    val hasYerKey = yerKeywords.any { line.contains(it) }
                    
                    if (hasYerKey && !hasSarKey) {
                        false
                    } else if (hasYerKey && hasSarKey) {
                        val numbers = extractNumbers(line)
                        numbers.any { it > 0.0 }
                    } else {
                        true
                    }
                }
                return filteredLines.joinToString("\n")
            }
        }
        
        return rawText
    }

    fun parseThermalReceipt(rawText: String): ParsedReceipt {
        val filteredText = filterReceiptTextByCurrency(rawText)
        val lines = filteredText.split("\n")
        
        val titleLines = mutableListOf<String>()
        val metaFields = mutableListOf<Pair<String, String>>()
        val itemRows = mutableListOf<Triple<String, String, String>>() // Item Name, Qty, Total
        val totalsFields = mutableListOf<Pair<String, String>>()
        val footerLines = mutableListOf<String>()
        val statementRows = mutableListOf<List<String>>() 
        
        var hasSeenListOrTable = false
        
        var currentGroupTitle = ""
        var currentGroupDate = ""
        val currentGroupDetails = mutableListOf<String>()
        
        fun commitCurrentGroup() {
            if (currentGroupTitle.isNotEmpty()) {
                val fullDetailsBlock = if (currentGroupDetails.isNotEmpty()) {
                    currentGroupTitle + "\n" + currentGroupDetails.joinToString("\n")
                } else {
                    currentGroupTitle
                }
                statementRows.add(listOf(currentGroupDate, fullDetailsBlock))
                currentGroupTitle = ""
                currentGroupDate = ""
                currentGroupDetails.clear()
            }
        }
        
        val itemRegex = Regex("^(.+?)\\s+x\\s+(.+?)\\s*=\\s*(.+)$")
        val reportOpRegex = Regex("^(\\d+)\\.\\s+\\[(.+?)\\]\\s+-\\s+#(.+)$")
        val supplierMovRegex = Regex("^•?\\s*(📥 سند توريد|🔄 سند مرتجع|سند توريد|سند مرتجع|سند)\\s+#(\\d+)\\s+بتاريخ\\s+(.+?):?$")
        val customerMovRegex = Regex("^•?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2})\\s*-\\s*(.+)$")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            val isSep = trimmed.all { it == '-' || it == '=' || it == '*' || it == '_' || it == '─' || it == '═' } || 
                        trimmed.contains("=====") || trimmed.contains("-----")
            if (isSep) {
                commitCurrentGroup()
                continue
            }
            
            val cleanLine = trimmed
                .replace("💰", "")
                .replace("💔", "")
                .replace("💸", "")
                .replace("📈", "")
                .replace("📉", "")
                .replace("📊", "")
                .replace("📝", "")
                .replace("👤", "")
                .replace("💵", "")
                .replace("🇱🇧", "")
                .replace("🇸🇦", "")
                .replace("🇾🇪", "")
                .replace("🇵🇸", "")
                .replace("🖨️", "")
                .replace("✔️", "")
                .trim()
                
            if (cleanLine.isEmpty()) continue
            
            val opMatch = reportOpRegex.find(cleanLine)
            if (opMatch != null) {
                commitCurrentGroup()
                hasSeenListOrTable = true
                val type = opMatch.groupValues[2].trim()
                val docId = opMatch.groupValues[3].trim()
                currentGroupTitle = "[$type] #$docId"
                continue
            }
            
            val supMatch = supplierMovRegex.find(cleanLine)
            if (supMatch != null) {
                commitCurrentGroup()
                hasSeenListOrTable = true
                currentGroupTitle = "${supMatch.groupValues[1]} #${supMatch.groupValues[2]}"
                val rawDate = supMatch.groupValues[3].trim()
                currentGroupDate = if (rawDate.contains(" ")) rawDate.split(" ")[0] else rawDate
                continue
            }
            
            val custMatch = customerMovRegex.find(cleanLine)
            if (custMatch != null) {
                commitCurrentGroup()
                hasSeenListOrTable = true
                val date = custMatch.groupValues[1].trim()
                val details = custMatch.groupValues[2].trim()
                statementRows.add(listOf(date, details))
                continue
            }
            
            val cleanItemLine = cleanLine.replace(Regex("^[•\\-*🔸🔹▪]\\s*"), "").trim()
            val itemMatch = itemRegex.find(cleanItemLine)
            if (itemMatch != null) {
                val name = itemMatch.groupValues[1].trim()
                val qty = itemMatch.groupValues[2].trim().replace("حبة", "").replace("كجم", "").trim()
                val total = itemMatch.groupValues[3].trim()
                
                if (currentGroupTitle.isNotEmpty()) {
                    currentGroupDetails.add("• $name x $qty = $total")
                } else {
                    hasSeenListOrTable = true
                    itemRows.add(Triple(name, qty, total))
                }
                continue
            }
            
            if (cleanLine.contains(":") && !cleanLine.startsWith("http")) {
                val parts = cleanLine.split(":", limit = 2)
                val key = parts[0].replace(Regex("^[•\\-*🔸🔹▪]\\s*"), "").trim()
                val value = parts[1].trim()
                
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    val isGroupDetailKey = key.contains("التفاصيل") || key.contains("القيود") || 
                                           key.contains("القيمة المالية") || key.contains("التاريخ والوقت") || 
                                           key.contains("القيمة") || key.contains("المسدد") || 
                                           key.contains("المتبقي") || key.contains("تفاصيل الضمار") || 
                                           key.contains("الملاحظات")
                                           
                    if (currentGroupTitle.isNotEmpty() && isGroupDetailKey) {
                        if (key.contains("التاريخ والوقت") || key.contains("تاريخ")) {
                            currentGroupDate = if (value.contains(" ")) value.split(" ")[0] else value
                        }
                        currentGroupDetails.add("• $key: $value")
                        continue
                    }
                    
                    commitCurrentGroup()
                    
                    val isTotalsKeyword = key.contains("إجمالي") || key.contains("الصافي") || key.contains("المدفوع") || 
                                          key.contains("المتبقي") || key.contains("رصيد") || key.contains("الديون") || 
                                          key.contains("الخلاصة") || key.contains("صافي الربح") || key.contains("صافي الخسارة") ||
                                          key.contains("التحصيلات") || key.contains("حالة الحساب") || key.contains("ريال يمني") ||
                                          key.contains("ريال سعودي") || key.contains("الرصيد الكلي") || key.contains("مشتريات") ||
                                          key.contains("مسدد") || key.contains("ضمار")
                                          
                    if (hasSeenListOrTable || isTotalsKeyword) {
                        totalsFields.add(key to value)
                    } else {
                        metaFields.add(key to value)
                    }
                    continue
                }
            }
            
            val cleanFallbackLine = cleanLine.replace(Regex("^[•\\-*🔸🔹▪]\\s*"), "").trim()
            if (currentGroupTitle.isNotEmpty() && (trimmed.startsWith(" ") || trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("🔸") || trimmed.startsWith("🔹"))) {
                currentGroupDetails.add("• $cleanFallbackLine")
                continue
            }
            
            commitCurrentGroup()
            
            val isFooterKeyword = cleanLine.contains("صاحب الوكالة") || cleanLine.contains("أخوكم") || 
                                  cleanLine.contains("توقيع") || cleanLine.contains("نشكركم") || 
                                  cleanLine.contains("نرجو") || cleanLine.contains("شاكرين")
                                  
            if (isFooterKeyword) {
                footerLines.add(cleanLine)
            } else if (!hasSeenListOrTable) {
                titleLines.add(cleanLine)
            } else {
                footerLines.add(cleanLine)
            }
        }
        
        commitCurrentGroup()
        
        return ParsedReceipt(
            titleLines = titleLines,
            metaFields = metaFields,
            itemRows = itemRows,
            statementRows = statementRows,
            totalsFields = totalsFields,
            footerLines = footerLines
        )
    }

    fun formatThermalReceipt(rawText: String, printerWidth: Int): String {
        val parsed = parseThermalReceipt(rawText)
        val sb = java.lang.StringBuilder()
        
        for (line in parsed.titleLines) {
            sb.append(centerText(line, printerWidth)).append("\n")
        }
        sb.append("\n")
        
        for (field in parsed.metaFields) {
            sb.append(formatDualColumn(field.second, field.first, printerWidth)).append("\n")
        }
        sb.append("\n")
        
        if (parsed.itemRows.isNotEmpty()) {
            sb.append(formatDualColumn("الصافي", "اسم الصنف والكمية", printerWidth)).append("\n")
            sb.append(".".repeat(printerWidth)).append("\n")
            for (item in parsed.itemRows) {
                val left = item.third
                val right = "${item.first} (x${item.second})"
                sb.append(formatDualColumn(left, right, printerWidth)).append("\n")
            }
            sb.append("\n")
        }
        
        if (parsed.statementRows.isNotEmpty()) {
            sb.append(formatDualColumn("العملية والتفاصيل", "التاريخ", printerWidth)).append("\n")
            sb.append(".".repeat(printerWidth)).append("\n")
            for (row in parsed.statementRows) {
                val date = row[0].ifEmpty { "-" }
                val details = row[1]
                val detailLines = details.split("\n")
                sb.append(formatDualColumn(detailLines.getOrElse(0) { "" }, date, printerWidth)).append("\n")
                for (i in 1 until detailLines.size) {
                    sb.append(padTextRight(detailLines[i], printerWidth)).append("\n")
                }
            }
            sb.append("\n")
        }
        
        if (parsed.totalsFields.isNotEmpty()) {
            sb.append(".".repeat(printerWidth)).append("\n")
            for (field in parsed.totalsFields) {
                sb.append(formatDualColumn(field.second, field.first, printerWidth)).append("\n")
            }
            sb.append("\n")
        }
        
        for (line in parsed.footerLines) {
            if (line.contains("توقيع")) {
                sb.append(formatDualColumn("توقيع المحاسب: ........", "توقيع المستلم: ........", printerWidth)).append("\n")
            } else {
                sb.append(centerText(line, printerWidth)).append("\n")
            }
        }
        
        return sb.toString()
    }

    fun formatThermalReceiptOld(rawText: String, printerWidth: Int): String {
        val lines = rawText.split("\n")
        
        val titleLines = mutableListOf<String>()
        val metaFields = mutableListOf<Pair<String, String>>()
        val itemRows = mutableListOf<Triple<String, String, String>>() // Item Name, Qty, Total
        val totalsFields = mutableListOf<Pair<String, String>>()
        val footerLines = mutableListOf<String>()
        
        // This list holds elements for the structured statement table
        // each row contains: listOf(date/doc, details)
        val statementRows = mutableListOf<List<String>>() 
        
        var hasSeenListOrTable = false
        
        // We can group complex operations or movements here
        var currentGroupTitle = ""
        var currentGroupDate = ""
        val currentGroupDetails = mutableListOf<String>()
        
        fun commitCurrentGroup() {
            if (currentGroupTitle.isNotEmpty()) {
                val fullDetailsBlock = if (currentGroupDetails.isNotEmpty()) {
                    currentGroupTitle + "\n" + currentGroupDetails.joinToString("\n")
                } else {
                    currentGroupTitle
                }
                statementRows.add(listOf(currentGroupDate, fullDetailsBlock))
                currentGroupTitle = ""
                currentGroupDate = ""
                currentGroupDetails.clear()
            }
        }
        
        val itemRegex = Regex("^(.+?)\\s+x\\s+(.+?)\\s*=\\s*(.+)$")
        // To match Report Ops: "1. [مبيعات] - #25"
        val reportOpRegex = Regex("^(\\d+)\\.\\s+\\[(.+?)\\]\\s+-\\s+#(.+)$")
        // To match Supplier Movements: "• 📥 سند توريد #12 بتاريخ 2026-07-09 13:20:"
        val supplierMovRegex = Regex("^•?\\s*(📥 سند توريد|🔄 سند مرتجع|سند توريد|سند مرتجع|سند)\\s+#(\\d+)\\s+بتاريخ\\s+(.+?):?$")
        // To match Customer Movements: "• 2026-07-09 - فاتورة بقيمة ..."
        val customerMovRegex = Regex("^•?\\s*(\\d{4}-\\d{2}-\\d{2}|\\d{2}-\\d{2})\\s*-\\s*(.+)$")
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            
            // If it's a separator line, use it to commit any open group and skip
            val isSep = trimmed.all { it == '-' || it == '=' || it == '*' || it == '_' } || 
                        trimmed.contains("=====") || trimmed.contains("-----")
            if (isSep) {
                commitCurrentGroup()
                continue
            }
            
            // Clean the line of emoji and bullets for sub-parsing but preserve the text structure
            val cleanLine = trimmed
                .replace("💰", "")
                .replace("💔", "")
                .replace("💸", "")
                .replace("📈", "")
                .replace("📉", "")
                .replace("📊", "")
                .replace("📝", "")
                .replace("👤", "")
                .replace("💵", "")
                .replace("🇸🇦", "")
                .replace("🇾🇪", "")
                .replace("🇵🇸", "")
                .replace("🖨️", "")
                .replace("✔️", "")
                .trim()
                
            if (cleanLine.isEmpty()) continue
            
            // 1. Check if it's a Report Op Header: e.g. "1. [مبيعات] - #25"
            val opMatch = reportOpRegex.find(cleanLine)
            if (opMatch != null) {
                commitCurrentGroup()
                hasSeenListOrTable = true
                val type = opMatch.groupValues[2].trim()
                val docId = opMatch.groupValues[3].trim()
                currentGroupTitle = "[$type] #$docId"
                continue
            }
            
            // 2. Check if it's a Supplier Movement Header: e.g. "• 📥 سند توريد #12 بتاريخ 2026-07-09 13:20:"
            val supMatch = supplierMovRegex.find(cleanLine)
            if (supMatch != null) {
                commitCurrentGroup()
                hasSeenListOrTable = true
                currentGroupTitle = "${supMatch.groupValues[1]} #${supMatch.groupValues[2]}"
                val rawDate = supMatch.groupValues[3].trim()
                currentGroupDate = if (rawDate.contains(" ")) rawDate.split(" ")[0] else rawDate
                continue
            }
            
            // 3. Check if it's a Customer Movement: e.g. "• 2026-07-09 - فاتورة بقيمة ..."
            val custMatch = customerMovRegex.find(cleanLine)
            if (custMatch != null) {
                commitCurrentGroup()
                hasSeenListOrTable = true
                val date = custMatch.groupValues[1].trim()
                val details = custMatch.groupValues[2].trim()
                statementRows.add(listOf(date, details))
                continue
            }
            
            // 4. Check if it's a structured Item Row: e.g. "تفاح x 5 حبة = 500 ريال"
            // We strip any bullet symbols or indents from cleanLine before checking itemRegex
            val cleanItemLine = cleanLine.replace(Regex("^[•\\-*🔸🔹▪]\\s*"), "").trim()
            val itemMatch = itemRegex.find(cleanItemLine)
            if (itemMatch != null) {
                val name = itemMatch.groupValues[1].trim()
                val qty = itemMatch.groupValues[2].trim().replace("حبة", "").replace("كجم", "").trim()
                val total = itemMatch.groupValues[3].trim()
                
                if (currentGroupTitle.isNotEmpty()) {
                    // If we have an active group (e.g., supplier movement details list), append as sub-detail
                    currentGroupDetails.add("• $name x $qty = $total")
                } else {
                    hasSeenListOrTable = true
                    itemRows.add(Triple(name, qty, total))
                }
                continue
            }
            
            // 5. Check if it's a Key-Value field (contains ":")
            if (cleanLine.contains(":") && !cleanLine.startsWith("http")) {
                val parts = cleanLine.split(":", limit = 2)
                val key = parts[0].replace(Regex("^[•\\-*🔸🔹▪]\\s*"), "").trim()
                val value = parts[1].trim()
                
                if (key.isNotEmpty() && value.isNotEmpty()) {
                    // Check if it's a transaction sub-detail belonging to current active group
                    val isGroupDetailKey = key.contains("التفاصيل") || key.contains("القيود") || 
                                           key.contains("القيمة المالية") || key.contains("التاريخ والوقت") || 
                                           key.contains("القيمة") || key.contains("المسدد") || 
                                           key.contains("المتبقي") || key.contains("تفاصيل الضمار") || 
                                           key.contains("الملاحظات")
                                           
                    if (currentGroupTitle.isNotEmpty() && isGroupDetailKey) {
                        if (key.contains("التاريخ والوقت") || key.contains("تاريخ")) {
                            currentGroupDate = if (value.contains(" ")) value.split(" ")[0] else value
                        }
                        currentGroupDetails.add("• $key: $value")
                        continue
                    }
                    
                    // Otherwise, commit current group and parse as global key-value
                    commitCurrentGroup()
                    
                    val isTotalsKeyword = key.contains("إجمالي") || key.contains("الصافي") || key.contains("المدفوع") || 
                                          key.contains("المتبقي") || key.contains("رصيد") || key.contains("الديون") || 
                                          key.contains("الخلاصة") || key.contains("صافي الربح") || key.contains("صافي الخسارة") ||
                                          key.contains("التحصيلات") || key.contains("حالة الحساب") || key.contains("ريال يمني") ||
                                          key.contains("ريال سعودي") || key.contains("الرصيد الكلي") || key.contains("مشتريات") ||
                                          key.contains("مسدد") || key.contains("ضمار")
                                          
                    if (hasSeenListOrTable || isTotalsKeyword) {
                        totalsFields.add(key to value)
                    } else {
                        metaFields.add(key to value)
                    }
                    continue
                }
            }
            
            // 6. Default fallback line
            // If it starts with bullet or indent, and we have an active group, add to group details
            val cleanFallbackLine = cleanLine.replace(Regex("^[•\\-*🔸🔹▪]\\s*"), "").trim()
            if (currentGroupTitle.isNotEmpty() && (trimmed.startsWith(" ") || trimmed.startsWith("•") || trimmed.startsWith("-") || trimmed.startsWith("🔸") || trimmed.startsWith("🔹"))) {
                currentGroupDetails.add("• $cleanFallbackLine")
                continue
            }
            
            commitCurrentGroup()
            
            val isFooterKeyword = cleanLine.contains("صاحب الوكالة") || cleanLine.contains("أخوكم") || 
                                  cleanLine.contains("توقيع") || cleanLine.contains("نشكركم") || 
                                  cleanLine.contains("نرجو") || cleanLine.contains("شاكرين")
                                  
            if (isFooterKeyword) {
                footerLines.add(cleanLine)
            } else if (!hasSeenListOrTable) {
                titleLines.add(cleanLine)
            } else {
                footerLines.add(cleanLine)
            }
        }
        
        commitCurrentGroup()
        
        val sb = java.lang.StringBuilder()
        
        // 1. Draw Title Block
        if (titleLines.isNotEmpty()) {
            sb.append("╔").append("═".repeat(printerWidth - 2)).append("╗\n")
            for (t in titleLines) {
                sb.append("║").append(padTextCenter(t, printerWidth - 2)).append("║\n")
            }
            sb.append("╚").append("═".repeat(printerWidth - 2)).append("╝\n\n")
        }
        
        // 2. Draw Metadata Table
        if (metaFields.isNotEmpty()) {
            val c1 = if (printerWidth == 32) 15 else 22
            val c2 = if (printerWidth == 32) 14 else 23
            val tableRows = metaFields.map { listOf(it.second, it.first) }
            
            sb.append("┌").append("─".repeat(printerWidth - 2)).append("┐\n")
            sb.append("│").append(padTextCenter("تفاصيل وبيانات المستند", printerWidth - 2)).append("│\n")
            sb.append(drawGridTable(listOf(c1, c2), emptyList(), tableRows))
            sb.append("\n")
        }
        
        // 3. Draw Items Table
        if (itemRows.isNotEmpty()) {
            val c1 = if (printerWidth == 32) 13 else 23  // Item Name
            val c2 = if (printerWidth == 32) 5 else 7    // Qty
            val c3 = if (printerWidth == 32) 10 else 14  // Total
            
            val tableHeaders = listOf(
                if (printerWidth == 32) "الإجمالي" else "الإجمالي الكلي",
                if (printerWidth == 32) "الكمية" else "الكمية/حبة",
                if (printerWidth == 32) "الصنف" else "اسم الصنف والبيان"
            )
            val tableRows = itemRows.map { listOf(it.third, it.second, it.first) }
            
            sb.append(drawGridTable(listOf(c3, c2, c1), tableHeaders, tableRows))
            sb.append("\n")
        }
        
        // 4. Draw Statement Table (2-column layout for perfect readability)
        if (statementRows.isNotEmpty()) {
            val c1 = if (printerWidth == 32) 21 else 33  // Details
            val c2 = if (printerWidth == 32) 8 else 12   // Date
            
            val tableHeaders = listOf(
                if (printerWidth == 32) "العملية والتفاصيل" else "تفاصيل وقيود الحركة المالية",
                if (printerWidth == 32) "التاريخ" else "تاريخ الحركة"
            )
            
            // Map rows: Column 0 (Details), Column 1 (Date)
            val tableRows = statementRows.map { listOf(it[1], it[0].ifEmpty { "-" }) }
            sb.append(drawGridTable(listOf(c1, c2), tableHeaders, tableRows))
            sb.append("\n")
        }
        
        // 5. Draw Totals Table
        if (totalsFields.isNotEmpty()) {
            val c1 = if (printerWidth == 32) 14 else 20  // Value
            val c2 = if (printerWidth == 32) 15 else 25  // Key
            
            val tableHeaders = listOf(
                if (printerWidth == 32) "المبلغ" else "المبلغ المالي",
                if (printerWidth == 32) "البيان المالي" else "ملخص الحساب المحاسبي"
            )
            val tableRows = totalsFields.map { listOf(it.second, it.first) }
            
            sb.append("┌").append("─".repeat(printerWidth - 2)).append("┐\n")
            sb.append("│").append(padTextCenter("ملخص الحسابات والإجماليات والوفورات", printerWidth - 2)).append("│\n")
            sb.append(drawGridTable(listOf(c1, c2), tableHeaders, tableRows))
            sb.append("\n")
        }
        
        // 6. Draw Footer Signature and Lines
        if (footerLines.isNotEmpty()) {
            sb.append("-".repeat(printerWidth)).append("\n")
            for (f in footerLines) {
                if (f.contains("توقيع")) {
                    val formattedSignature = formatDualColumn("توقيع المحاسب: ........", "توقيع المستلم: ........", printerWidth)
                    sb.append(formattedSignature).append("\n")
                } else {
                    sb.append(padTextCenter(f, printerWidth)).append("\n")
                }
            }
        }
        
        return sb.toString()
    }

    fun virtualPrint(receiptText: String, onFinished: (String) -> Unit) {
        // Build virtual receipt with centralized AgencyProfile header & footer
        val widthChars = if (currentPrinterSize == 58) 32 else 48
        val lineSeparator = "-".repeat(widthChars)

        val formattedBody = formatThermalReceipt(receiptText, widthChars)

        val fullReceipt = buildString {
            if (com.example.util.BrandingManager.isHeaderEnabled) {
                appendLine(lineSeparator)
                com.example.util.BrandingManager.buildThermalHeaderLines().forEach { hLine ->
                    appendLine(centerText(hLine, widthChars))
                }
                appendLine(lineSeparator)
            } else {
                appendLine(lineSeparator)
            }
            appendLine(centerText("تاريخ السند: ${Helpers.getCurrentDateTime()}", widthChars))
            appendLine(lineSeparator)
            appendLine(formattedBody)
            appendLine(lineSeparator)
            if (com.example.util.BrandingManager.isFooterEnabled) {
                com.example.util.BrandingManager.buildThermalFooterLines().forEach { fLine ->
                    appendLine(centerText(fLine, widthChars))
                }
                appendLine(lineSeparator)
            }
        }
        onFinished(fullReceipt)
    }

    companion object {
        @Volatile
        private var INSTANCE: BluetoothPrinterManager? = null

        fun getInstance(context: Context): BluetoothPrinterManager {
            return INSTANCE ?: synchronized(this) {
                val instance = BluetoothPrinterManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }
}
