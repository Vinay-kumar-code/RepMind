package com.example.workouttracker.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import java.util.Locale

enum class DayState {
    NONE, WORKOUT_DONE, GOAL_MET
}

@Composable
fun LineChart(
    data: List<Pair<String, Int>>, // Date (MM-DD), Value
    modifier: Modifier = Modifier,
    lineColor: Color = MaterialTheme.colorScheme.primary
) {
    if (data.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) { Text("No Data") }
        return
    }

    val maxValue = data.maxOf { it.second }
    val points = data.map { it.second }

    Canvas(modifier = modifier.padding(16.dp)) {
        val width = size.width
        val height = size.height
        val xStep = width / (data.size - 1).coerceAtLeast(1)
        val yStep = if (maxValue > 0) height / maxValue else 0f

        // Draw Grid
        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, height), Offset(width, height))
        drawLine(Color.Gray.copy(alpha = 0.3f), Offset(0f, 0f), Offset(0f, height))

        // Draw Line
        for (i in 0 until points.size - 1) {
            val x1 = i * xStep
            val y1 = height - (points[i] * yStep)
            val x2 = (i + 1) * xStep
            val y2 = height - (points[i + 1] * yStep)

            drawLine(
                color = lineColor,
                start = Offset(x1, y1),
                end = Offset(x2, y2),
                strokeWidth = 8f
            )
            drawCircle(lineColor, 8f, Offset(x1, y1))
        }
        // Last circle
        val lastX = (points.size - 1) * xStep
        val lastY = height - (points.last() * yStep)
        drawCircle(lineColor, 8f, Offset(lastX, lastY))

        // Draw Labels (simplified)
        val textPaint = Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 30f
            textAlign = Paint.Align.CENTER
        }
        
        data.forEachIndexed { index, pair ->
            if (data.size < 10 || index % (data.size / 5) == 0) {
                val x = index * xStep
                drawContext.canvas.nativeCanvas.drawText(pair.first, x, height + 40f, textPaint)
            }
        }
    }
}

@Composable
fun ContributionHeatMap(
    data: Map<String, Int>, // Date (YYYY-MM-DD) -> Count
    modifier: Modifier = Modifier
) {
    // Simple 7xN grid for last 3 months?
    // Or just last 28 days (4 weeks)
    val days = 28
    val today = LocalDate.now()
    val formatter = DateTimeFormatter.ISO_LOCAL_DATE
    
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        for (w in 3 downTo 0) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (d in 6 downTo 0) {
                    val date = today.minusDays((w * 7 + d).toLong())
                    val dateStr = date.format(formatter)
                    val count = data[dateStr] ?: 0
                    val color = when {
                        count == 0 -> Color.Gray.copy(alpha = 0.2f)
                        count < 10 -> Color(0xFF9BE9A8) // Light Green
                        count < 30 -> Color(0xFF40C463)
                        count < 60 -> Color(0xFF30A14E)
                        else -> Color(0xFF216E39) // Dark Green
                    }
                    
                    Box(
                        Modifier
                            .size(20.dp)
                            .background(color, androidx.compose.foundation.shape.RoundedCornerShape(4.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun CalendarView(
    dayStates: Map<String, DayState>,
    selectedDate: String?,
    onDateSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val yearMonth = YearMonth.from(today)
    val daysInMonth = yearMonth.lengthOfMonth()
    val firstDayOfWeek = yearMonth.atDay(1).dayOfWeek.value % 7 // 0=Sunday, 1=Monday...
    
    Column(modifier.fillMaxWidth()) {
        // Month Header
        Text(
            text = "${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${today.year}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // Days Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("S", "M", "T", "W", "T", "F", "S").forEach { 
                Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.width(32.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        
        Spacer(Modifier.height(8.dp))
        
        // Days Grid
        var currentDay = 1
        var currentWeekDay = 0
        
        // Initial padding
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            // Fill empty slots
            for (i in 0 until firstDayOfWeek) {
                Spacer(Modifier.width(32.dp))
                currentWeekDay++
            }
            
            while (currentDay <= daysInMonth) {
                if (currentWeekDay > 6) {
                    currentWeekDay = 0
                    // End of row
                    }
                if (currentWeekDay == 0 && currentDay > 1) {
                     // New Row logic is hard in a single loop with Compose Rows.
                     // Better to use a Grid or nested Columns/Rows.
                     // Let's use a simple flow logic or just fixed rows.
                }
                // Wait, standard Row/Column approach is tricky for calendar.
                // Let's restart the loop structure.
                break 
            }
        }
        
        // Better approach: Calculate rows
        val totalSlots = firstDayOfWeek + daysInMonth
        val rows = (totalSlots + 6) / 7
        
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0..6) {
                    val dayNum = row * 7 + col - firstDayOfWeek + 1
                    if (dayNum in 1..daysInMonth) {
                        val dateStr = LocalDate.of(today.year, today.month, dayNum).format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val state = dayStates[dateStr] ?: DayState.NONE
                        val isToday = dayNum == today.dayOfMonth
                        val isSelected = dateStr == selectedDate
                        
                        val bgColor = when (state) {
                            DayState.GOAL_MET -> MaterialTheme.colorScheme.primary
                            DayState.WORKOUT_DONE -> MaterialTheme.colorScheme.tertiaryContainer
                            DayState.NONE -> if (isToday) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        }
                        
                        val textColor = when (state) {
                            DayState.GOAL_MET -> MaterialTheme.colorScheme.onPrimary
                            DayState.WORKOUT_DONE -> MaterialTheme.colorScheme.onTertiaryContainer
                            DayState.NONE -> MaterialTheme.colorScheme.onSurface
                        }
                        
                        val mod = Modifier.size(36.dp).background(color = bgColor, shape = CircleShape)
                        val finalMod = if (isSelected) mod.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else mod
                        
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = finalMod.clickable { onDateSelected(dateStr) }
                        ) {
                            Text(
                                text = "$dayNum",
                                color = textColor,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    } else {
                        Spacer(Modifier.width(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
