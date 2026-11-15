package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.Helpers
import java.util.Calendar

@Composable
fun ReportDateDialog(
    currentDate: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var reportDate by remember { mutableStateOf(currentDate) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "تعيين تاريخ وطباعة التقرير",
                color = PalWhitePure,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Right,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "قم بتعيين أو كتابة التاريخ والوقت المراد إثباته وطباعته على المستند أو كشف التقرير بالكامل وبشكل صحيح (يمكنك كتابة أي تاريخ أو فترة مخصصة):",
                    color = PalWhiteMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = reportDate,
                    onValueChange = { reportDate = it },
                    label = { Text("تاريخ وطباعة التقرير والعمليات", color = PalWhiteMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PalGreenNormal,
                        unfocusedBorderColor = PalBorderColor,
                        focusedTextColor = PalWhitePure,
                        unfocusedTextColor = PalWhiteSoft,
                        focusedContainerColor = PalBlackDark,
                        unfocusedContainerColor = PalBlackDark
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = PalIconUnified)
                    }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(reportDate) },
                colors = ButtonDefaults.buttonColors(containerColor = PalGreenNormal)
            ) {
                Text("تأكيد واعتماد التاريخ", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء", color = PalWhiteMuted)
            }
        },
        containerColor = PalBlackNormal,
        shape = RoundedCornerShape(14.dp)
    )
}

/**
 * A beautiful, highly-interactive unified report period and date picker selector component.
 * Supports: Specific Date (تحديد تاريخ), Specific Month (اختيار شهر), and Specific Year (اختيار سنة).
 */
@Composable
fun UnifiedPeriodSelector(
    modifier: Modifier = Modifier,
    initialType: String = "يوم", // "يوم", "شهر", "سنة"
    initialValue: String = Helpers.getCurrentDate(), // e.g. "2026-06-01" or "2026-06-01|2026-06-30"
    onPeriodChanged: (type: String, formattedValue: String) -> Unit
) {
    val initType = if (initialValue.startsWith("WEEK_FILTER|")) "أسبوع" else initialType
    var selectedType by remember { mutableStateOf(initType) } // "يوم", "أسبوع", "شهر", "سنة"
    
    // Parse range or single date
    val initStartDay: String
    val initEndDay: String
    if (initialValue.contains("|") && !initialValue.startsWith("WEEK_FILTER|")) {
        val parts = initialValue.split("|")
        initStartDay = parts.getOrElse(0) { Helpers.getCurrentDate() }
        initEndDay = parts.getOrElse(1) { Helpers.getCurrentDate() }
    } else {
        initStartDay = if (initialValue.startsWith("WEEK_FILTER|")) Helpers.getCurrentDate() else initialValue.ifEmpty { Helpers.getCurrentDate() }
        initEndDay = if (initialValue.startsWith("WEEK_FILTER|")) Helpers.getCurrentDate() else initialValue.ifEmpty { Helpers.getCurrentDate() }
    }
    
    var selectedDayStart by remember { mutableStateOf(initStartDay) }
    var selectedDayEnd by remember { mutableStateOf(initEndDay) }
    
    // Parse week filter specifically if present
    var initWeekNum = Helpers.getCurrentWeekOfMonth()
    var defaultMonth = "06"
    var defaultYear = "2026"
    
    if (initialValue.startsWith("WEEK_FILTER|")) {
        try {
            val parts = initialValue.split("|")
            if (parts.size >= 3) {
                val yearMonth = parts[1] // "yyyy-MM"
                val ymParts = yearMonth.split("-")
                defaultYear = ymParts.getOrNull(0) ?: "2026"
                defaultMonth = ymParts.getOrNull(1) ?: "06"
                initWeekNum = parts[2].toIntOrNull() ?: Helpers.getCurrentWeekOfMonth()
            }
        } catch (e: Exception) {}
    } else {
        defaultMonth = if (initialValue.length >= 7 && !initialValue.contains("|")) initialValue.substring(5, 7) else "06"
        defaultYear = if (initialValue.length >= 4) {
            if (initialValue.contains("|")) {
                initialValue.split("|")[0].substring(0, 4)
            } else {
                initialValue.substring(0, 4)
            }
        } else "2026"
    }
    
    var selectedMonth by remember { mutableStateOf(defaultMonth) }
    var selectedYearForMonth by remember { mutableStateOf(defaultYear) }
    var selectedWeekNum by remember { mutableStateOf(initWeekNum) }
    // Year Selection State ("2026")
    var selectedYearOnly by remember { mutableStateOf(defaultYear) }

    // Broadcaster
    val triggerChange = {
        val finalVal = when (selectedType) {
            "يوم" -> "$selectedDayStart|$selectedDayEnd"
            "أسبوع" -> "WEEK_FILTER|$selectedYearForMonth-$selectedMonth|$selectedWeekNum"
            "شهر" -> "$selectedYearForMonth-$selectedMonth"
            "سنة" -> selectedYearOnly
            else -> "$selectedDayStart|$selectedDayEnd"
        }
        onPeriodChanged(selectedType, finalVal)
    }

    LaunchedEffect(selectedType, selectedDayStart, selectedDayEnd, selectedMonth, selectedYearForMonth, selectedWeekNum, selectedYearOnly) {
        triggerChange()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PalBlackNormal)
            .border(BorderStroke(1.dp, PalBorderColor), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "تصفية الفترات وتقويم السجلات",
            color = PalWhitePure,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Right
        )

        // Quadruple Segmented Control
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("يوم", "أسبوع", "شهر", "سنة").forEach { type ->
                val isSel = selectedType == type
                val label = when (type) {
                    "يوم" -> "يومي (من-إلى)"
                    "أسبوع" -> "أسبوعي محدد"
                    "شهر" -> "شهر محدد"
                    "سنة" -> "سنة كاملة"
                    else -> type
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSel) PalGreenNormal else PalBlackLight)
                        .clickable { selectedType = type }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        color = if (isSel) Color.White else PalWhiteMuted,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Divider(color = PalBlackLight, thickness = 0.5.dp)

        // Sub-interfaces based on selection
        when (selectedType) {
            "أسبوع" -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val arabicMonths = arrayOf(
                        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                    )
                    Text("اختر الأسبوع من الشهر العربي:", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            Pair(1, "الأول (1-7)"),
                            Pair(2, "الثاني (8-14)"),
                            Pair(3, "الثالث (15-21)"),
                            Pair(4, "الرابع (22-28)"),
                            Pair(5, "الخامس (29+)")
                        ).forEach { (num, label) ->
                            val isSel = selectedWeekNum == num
                            val bg = if (isSel) PalGreenNormal else PalBlackLight
                            val fg = if (isSel) Color.White else Color.Gray
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(bg)
                                    .clickable { selectedWeekNum = num }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    color = fg,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("اختر الشهر العربي للبحث:", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        arabicMonths.forEachIndexed { index, mName ->
                            val mVal = (index + 1).toString().padStart(2, '0')
                            val isSel = selectedMonth == mVal
                            val bg = if (isSel) PalGreenNormal else PalBlackLight
                            val fg = if (isSel) Color.White else Color.Gray
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable { selectedMonth = mVal }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mName (${index + 1})",
                                    color = fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text("تعيين السنة للبحث:", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            val cur = selectedYearForMonth.toIntOrNull() ?: 2026
                            selectedYearForMonth = (cur - 1).toString()
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "السنة السابقة", tint = PalGreenLight)
                        }

                        OutlinedTextField(
                            value = selectedYearForMonth,
                            onValueChange = { selectedYearForMonth = it.take(4).filter { c -> c.isDigit() } },
                            label = { Text("السنة", color = PalWhiteMuted, fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenLight,
                                unfocusedBorderColor = PalBlackLight,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        IconButton(onClick = {
                            val cur = selectedYearForMonth.toIntOrNull() ?: 2026
                            selectedYearForMonth = (cur + 1).toString()
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "السنة التالية", tint = PalGreenLight)
                        }
                    }
                }
            }
            "يوم" -> {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Start Date
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("من تاريخ (السنة-الشهر-اليوم):", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val date = sdf.parse(selectedDayStart) ?: java.util.Date()
                                    val cal = Calendar.getInstance()
                                    cal.time = date
                                    cal.add(Calendar.DAY_OF_YEAR, -1)
                                    selectedDayStart = sdf.format(cal.time)
                                } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "السابق", tint = PalGreenLight)
                            }

                            OutlinedTextField(
                                value = selectedDayStart,
                                onValueChange = { selectedDayStart = it },
                                placeholder = { Text("yyyy-MM-dd", color = PalWhiteMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PalGreenLight,
                                    unfocusedBorderColor = PalBlackLight,
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )

                            IconButton(onClick = {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val date = sdf.parse(selectedDayStart) ?: java.util.Date()
                                    val cal = Calendar.getInstance()
                                    cal.time = date
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                    selectedDayStart = sdf.format(cal.time)
                                } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "التالي", tint = PalGreenLight)
                            }
                        }
                    }

                    // End Date
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("إلى تاريخ (السنة-الشهر-اليوم):", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(onClick = {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val date = sdf.parse(selectedDayEnd) ?: java.util.Date()
                                    val cal = Calendar.getInstance()
                                    cal.time = date
                                    cal.add(Calendar.DAY_OF_YEAR, -1)
                                    selectedDayEnd = sdf.format(cal.time)
                                } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "السابق", tint = PalGreenLight)
                            }

                            OutlinedTextField(
                                value = selectedDayEnd,
                                onValueChange = { selectedDayEnd = it },
                                placeholder = { Text("yyyy-MM-dd", color = PalWhiteMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PalGreenLight,
                                    unfocusedBorderColor = PalBlackLight,
                                    focusedTextColor = PalWhitePure,
                                    unfocusedTextColor = PalWhiteSoft
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 12.sp),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(6.dp)
                            )

                            IconButton(onClick = {
                                try {
                                    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                                    val date = sdf.parse(selectedDayEnd) ?: java.util.Date()
                                    val cal = Calendar.getInstance()
                                    cal.time = date
                                    cal.add(Calendar.DAY_OF_YEAR, 1)
                                    selectedDayEnd = sdf.format(cal.time)
                                } catch (e: Exception) {}
                            }) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "التالي", tint = PalGreenLight)
                            }
                        }
                    }
                }
            }
            "شهر" -> {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val arabicMonths = arrayOf(
                        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
                        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر"
                    )
                    Text("اختر الشهر العربي:", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    
                    // Horizontal scroll list of Arabic Month Names
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        arabicMonths.forEachIndexed { index, mName ->
                            val mVal = (index + 1).toString().padStart(2, '0')
                            val isSel = selectedMonth == mVal
                            val bg = if (isSel) PalGreenNormal else PalBlackLight
                            val fg = if (isSel) Color.White else Color.Gray
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bg)
                                    .clickable { selectedMonth = mVal }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$mName (${index + 1})",
                                    color = fg,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Text("تعيين السنة للبحث:", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Year decrement
                        IconButton(onClick = {
                            val cur = selectedYearForMonth.toIntOrNull() ?: 2026
                            selectedYearForMonth = (cur - 1).toString()
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "السنة السابقة", tint = PalGreenLight)
                        }

                        // Year Selector text field
                        OutlinedTextField(
                            value = selectedYearForMonth,
                            onValueChange = { selectedYearForMonth = it.take(4).filter { c -> c.isDigit() } },
                            label = { Text("السنة", color = PalWhiteMuted, fontSize = 9.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenLight,
                                unfocusedBorderColor = PalBlackLight,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        // Year increment
                        IconButton(onClick = {
                            val cur = selectedYearForMonth.toIntOrNull() ?: 2026
                            selectedYearForMonth = (cur + 1).toString()
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "السنة التالية", tint = PalGreenLight)
                        }
                    }
                }
            }
            "سنة" -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تعيين السنة (مثال: 2026):", color = PalWhiteSoft, fontSize = 10.sp, textAlign = TextAlign.Right, modifier = Modifier.fillMaxWidth())
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = {
                            val cur = selectedYearOnly.toIntOrNull() ?: 2026
                            selectedYearOnly = (cur - 1).toString()
                        }) {
                            Icon(Icons.Default.ChevronRight, contentDescription = "السنة السابقة", tint = PalGreenLight)
                        }

                        OutlinedTextField(
                            value = selectedYearOnly,
                            onValueChange = { selectedYearOnly = it.take(4).filter { c -> c.isDigit() } },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PalGreenLight,
                                unfocusedBorderColor = PalBlackLight,
                                focusedTextColor = PalWhitePure,
                                unfocusedTextColor = PalWhiteSoft
                            ),
                            textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(6.dp)
                        )

                        IconButton(onClick = {
                            val cur = selectedYearOnly.toIntOrNull() ?: 2026
                            selectedYearOnly = (cur + 1).toString()
                        }) {
                            Icon(Icons.Default.ChevronLeft, contentDescription = "السنة التالية", tint = PalGreenLight)
                        }
                    }
                }
            }
        }
    }
}
