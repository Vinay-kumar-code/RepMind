package com.example.workouttracker.ui

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import com.example.workouttracker.PoseClassifier
import com.example.workouttracker.WorkoutEngine
import com.example.workouttracker.WorkoutEngine.Feedback
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun PreviewCameraView(
    engine: WorkoutEngine,
    modifier: Modifier = Modifier,
    performanceSettings: PerformanceSettings = PerformanceSettings(),
    showLandmarks: Boolean = false
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val poseClassifier = remember { PoseClassifier(context) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var landmarksState by remember { mutableStateOf<List<FloatArray>?>(null) }
    var feedbackState by remember { mutableStateOf<Feedback?>(null) }

    DisposableEffect(performanceSettings) {
        // If model size toggle were implemented we'd recreate classifier here.
        onDispose { }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            poseClassifier.close()
        }
    }

    Box(modifier = modifier) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
                val imageAnalysis = ImageAnalysis.Builder()
                    .setTargetResolution(Size(performanceSettings.targetWidth, performanceSettings.targetHeight))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                var frameCounter = 0
                var lastProcessTime = 0L
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    try {
                        frameCounter++
                        // Frame skip
                        if (performanceSettings.frameSkipModulo > 1 && frameCounter % performanceSettings.frameSkipModulo != 0) {
                            imageProxy.close(); return@setAnalyzer
                        }
                        val now = System.currentTimeMillis()
                        val minInterval = 1000L / performanceSettings.maxFps.coerceAtLeast(1)
                        if (now - lastProcessTime < minInterval) {
                            imageProxy.close(); return@setAnalyzer
                        }
                        lastProcessTime = now
                        analyzeImage(imageProxy, poseClassifier, engine) { lm ->
                            val fb = engine.getLastFeedback()
                            mainHandler.post {
                                landmarksState = lm
                                feedbackState = fb
                            }
                        }
                        if (frameCounter % 180 == 0) {
                            val rt = Runtime.getRuntime()
                            val used = (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024)
                            Log.d("PreviewCameraView", "MemUsedMB=$used fpsSetting=${performanceSettings.maxFps}")
                        }
                    } catch (oom: OutOfMemoryError) {
                        Log.e("PreviewCameraView", "OOM in analyzer, skipping frame", oom)
                        imageProxy.close()
                    }
                }
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("PreviewCameraView", "Camera binding failed", e)
                }
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        if (showLandmarks) {
            PoseOverlay(
                landmarks = landmarksState,
                feedback = feedbackState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
private fun analyzeImage(
    imageProxy: ImageProxy,
    poseClassifier: PoseClassifier,
    engine: WorkoutEngine,
    onResult: (List<FloatArray>?) -> Unit
) {
    try {
        val ts = System.currentTimeMillis()
        val landmarks = poseClassifier.detectForVideo(imageProxy, ts)
        engine.updateFromLandmarks(landmarks, ts)
        onResult(landmarks)
    } catch (e: Exception) {
        Log.e("PreviewCameraView", "Image analysis failed", e)
    } finally {
        imageProxy.close()
    }
}

data class PerformanceSettings(
    val frameSkipModulo: Int = 2,
    val maxFps: Int = 30,
    val targetWidth: Int = 640,
    val targetHeight: Int = 480,
    val showLandmarks: Boolean = false
)
