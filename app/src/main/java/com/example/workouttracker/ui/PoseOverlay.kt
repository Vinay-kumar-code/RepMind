package com.example.workouttracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.WorkoutEngine
import kotlin.math.min

/**
 * Draws detected pose landmarks & connecting lines plus feedback text.
 * Landmarks are expected as normalized coordinates (0..1) in model (already mirrored for front camera).
 */
@Composable
fun PoseOverlay(
    landmarks: List<FloatArray>?,
    feedback: WorkoutEngine.Feedback?,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        if (landmarks != null && landmarks.isNotEmpty()) {
            // Define connections outside draw scope (no remember needed)
            val connections = listOf(
                11 to 12,
                11 to 13, 13 to 15,
                12 to 14, 14 to 16,
                11 to 23, 12 to 24,
                23 to 24
            )
            Canvas(modifier = Modifier.fillMaxSize()) {
                val lwBase = min(size.width, size.height) / 500f
                val jointRadius = 5f * lwBase
                val stroke = 3f * lwBase
                val validColor = Color(0xFF10B981)
                val warnColor = Color(0xFFF59E0B)
                val neutral = Color(0xFF60A5FA)
                val depthOk = feedback?.validDepth == true
                val rangeOk = feedback?.rangeOk == true
                val stage = feedback?.stage
                val lineColor = when {
                    stage == "bottom_hold" && !depthOk -> warnColor
                    stage == "bottom_hold" && depthOk -> validColor
                    stage == "ascent" && depthOk && rangeOk -> validColor
                    stage == "descent" -> neutral
                    else -> Color.White.copy(alpha = 0.85f)
                }
                connections.forEach { (a, b) ->
                    if (a < landmarks.size && b < landmarks.size) {
                        val la = landmarks[a]; val lb = landmarks[b]
                        if (la.size >= 2 && lb.size >= 2) {
                            drawLine(
                                color = lineColor,
                                start = androidx.compose.ui.geometry.Offset(la[0] * size.width, la[1] * size.height),
                                end = androidx.compose.ui.geometry.Offset(lb[0] * size.width, lb[1] * size.height),
                                strokeWidth = stroke,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }
                landmarks.forEachIndexed { idx, lm ->
                    if (lm.size >= 2) {
                        val visibility = lm.getOrNull(2) ?: 1f
                        if (visibility >= 0.5f) {
                            val c = if (idx in listOf(11,12,13,14,15,16)) lineColor else Color.White.copy(alpha = 0.5f)
                            drawCircle(
                                color = c,
                                radius = jointRadius,
                                center = androidx.compose.ui.geometry.Offset(lm[0] * size.width, lm[1] * size.height),
                                style = Stroke(width = jointRadius * 0.6f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = PathEffect.cornerPathEffect(2f))
                            )
                        }
                    }
                }
            }
        }

        // Feedback HUD - Removed as per user request for cleaner UI
        // The main HUD in WorkoutSessionScreen now handles the primary feedback.
        /*
        if (feedback != null) {
             // ... (legacy debug text code)
        }
        */
    }
}
