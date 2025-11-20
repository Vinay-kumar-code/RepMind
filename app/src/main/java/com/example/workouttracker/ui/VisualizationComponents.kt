package com.example.workouttracker.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

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
