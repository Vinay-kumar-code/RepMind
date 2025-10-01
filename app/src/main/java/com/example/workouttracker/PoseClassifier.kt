package com.example.workouttracker

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

/**
 * PoseClassifier: synchronous per-frame pose landmark inference using MediaPipe PoseLandmarker.
 * Place the model file pose_landmarker_full.task inside app/src/main/assets/.
 */
class PoseClassifier(context: Context) {
    private val landmarker: PoseLandmarker
    private val yuvConverter = YuvToRgbConverter()

    init {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("pose_landmarker_full.task")
            .build()

        val options = PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setRunningMode(RunningMode.VIDEO) // allows detectForVideo
            .build()

        landmarker = PoseLandmarker.createFromOptions(context, options)
    }

    /**
     * Returns first detected pose landmarks as float[ x, y, visibility ].
     */
    fun detectForVideo(imageProxy: ImageProxy, timestampMs: Long): List<FloatArray>? {
        return try {
            val bitmap = imageProxyToBitmapCorrected(imageProxy)
            val mpImage = BitmapImageBuilder(bitmap).build()
            val result: PoseLandmarkerResult = landmarker.detectForVideo(mpImage, timestampMs)
            if (result.landmarks().isEmpty()) return null
            result.landmarks()[0].map { lm -> floatArrayOf(lm.x(), lm.y(), lm.visibility().orElse(0f)) }
        } catch (e: Exception) {
            Log.e("PoseClassifier", "Detection failed", e)
            null
        }
    }

    private fun imageProxyToBitmapCorrected(imageProxy: ImageProxy): Bitmap {
        val width = imageProxy.width
        val height = imageProxy.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        yuvConverter.yuvToRgb(imageProxy, bitmap)

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val matrix = Matrix().apply {
            postRotate(rotationDegrees.toFloat())
            // Mirror horizontally for front camera (we use DEFAULT_FRONT_CAMERA)
            postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    fun close() { landmarker.close() }
}
