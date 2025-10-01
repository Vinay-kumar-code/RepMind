package com.example.workouttracker

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.*

/**
 * Utility functions for the workout tracker app
 */
object Utils {
    
    /**
     * Format a timestamp ISO string to a human-readable format
     */
    fun formatTimestamp(timestampIso: String, pattern: String = "MMM dd, yyyy HH:mm"): String {
        return try {
            val instant = Instant.parse(timestampIso)
            val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
            val formatter = DateTimeFormatter.ofPattern(pattern)
            localDateTime.format(formatter)
        } catch (e: Exception) {
            timestampIso.take(10) // Fallback to date part
        }
    }
    
    /**
     * Calculate angle between three points (used for pose analysis)
     */
    fun calculateAngle(point1: FloatArray, point2: FloatArray, point3: FloatArray): Float {
        val ax = point1[0] - point2[0]
        val ay = point1[1] - point2[1]
        val cx = point3[0] - point2[0]
        val cy = point3[1] - point2[1]
        
        val dot = ax * cx + ay * cy
        val normA = sqrt(ax * ax + ay * ay)
        val normC = sqrt(cx * cx + cy * cy)
        
        val cos = (dot / (normA * normC + 1e-8f)).coerceIn(-1f, 1f)
        var angle = acos(cos) * (180f / PI.toFloat())
        
        if (angle > 180f) angle = 360f - angle
        return angle
    }
    
    /**
     * Calculate distance between two points
     */
    fun calculateDistance(point1: FloatArray, point2: FloatArray): Float {
        val dx = point1[0] - point2[0]
        val dy = point1[1] - point2[1]
        return sqrt(dx * dx + dy * dy)
    }
    
    /**
     * Normalize a value to a given range
     */
    fun normalize(value: Float, min: Float, max: Float): Float {
        return ((value - min) / (max - min)).coerceIn(0f, 1f)
    }
    
    /**
     * Apply moving average smoothing to a list of values
     */
    fun applyMovingAverage(values: List<Float>, windowSize: Int = 5): Float {
        if (values.isEmpty()) return 0f
        val window = values.takeLast(windowSize)
        return window.average().toFloat()
    }
    
    /**
     * Format duration in seconds to a readable string
     */
    fun formatDuration(seconds: Float): String {
        val totalSeconds = seconds.toInt()
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        
        return if (minutes > 0) {
            "${minutes}m ${remainingSeconds}s"
        } else {
            "${remainingSeconds}s"
        }
    }
    
    /**
     * Generate current ISO timestamp
     */
    fun getCurrentTimestampIso(): String {
        return Instant.now().toString()
    }
    
    /**
     * Validate pose landmarks array
     */
    fun isValidPoseLandmarks(landmarks: List<FloatArray>?): Boolean {
        return landmarks != null && 
               landmarks.size >= 17 && 
               landmarks.all { it.size >= 3 } // x, y, visibility
    }
}
