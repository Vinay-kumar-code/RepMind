package com.example.workouttracker

import android.graphics.Bitmap
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer
import kotlin.math.max

/**
 * Lightweight YUV_420_888 -> ARGB converter (no RenderScript / deprecated APIs).
 * Adapted from common CameraX sample logic. Not the most optimized, but fine for per-frame pose inference.
 */
class YuvToRgbConverter {
    private var cachedWidth = -1
    private var cachedHeight = -1
    private lateinit var buffer: IntArray

    fun yuvToRgb(image: ImageProxy, output: Bitmap) {
        val width = image.width
        val height = image.height
        ensureBuffer(width, height)
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yRowStride = yPlane.rowStride
        val uvRowStride = uPlane.rowStride
        val uvPixelStride = uPlane.pixelStride

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        // Process each pixel
        var outIndex = 0
        for (row in 0 until height) {
            val yRowOffset = row * yRowStride
            val uvRow = (row / 2) * uvRowStride
            for (col in 0 until width) {
                val yIndex = yRowOffset + col
                val uvCol = (col / 2) * uvPixelStride
                val uvIndex = uvRow + uvCol

                val y = (yBuffer.readUnsigned(yIndex))
                val u = uBuffer.readUnsigned(uvIndex) - 128
                val v = vBuffer.readUnsigned(uvIndex) - 128

                // YUV to RGB conversion (BT.601)
                val yClamped = max(y - 16, 0)
                val c = 298 * yClamped
                val r = (c + 409 * v + 128) shr 8
                val g = (c - 100 * u - 208 * v + 128) shr 8
                val b = (c + 516 * u + 128) shr 8

                buffer[outIndex++] = 0xFF000000.toInt() or
                        (r.coerceIn(0,255) shl 16) or
                        (g.coerceIn(0,255) shl 8) or
                        (b.coerceIn(0,255))
            }
        }
        output.setPixels(buffer, 0, width, 0, 0, width, height)
    }

    private fun ensureBuffer(width: Int, height: Int) {
        if (width != cachedWidth || height != cachedHeight) {
            buffer = IntArray(width * height)
            cachedWidth = width
            cachedHeight = height
        }
    }

    private fun ByteBuffer.readUnsigned(index: Int): Int = (this.get(index).toInt() and 0xFF)
}
