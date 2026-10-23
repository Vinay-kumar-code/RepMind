package com.example.workouttracker

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Workout types supported by the cardio tracking system.
 */
enum class CardioWorkoutType(val displayName: String) {
    WALKING("Walking"),
    RUNNING("Running")
}

/**
 * Live metrics emitted during a cardio workout (Samsung Health style).
 */
data class CardioMetrics(
    val steps: Int = 0,
    val distanceMeters: Double = 0.0,
    val durationSeconds: Long = 0,
    val paceMinPerKm: Double = 0.0,
    val speedKmPerH: Double = 0.0,
    val cadenceSpm: Int = 0,
    val caloriesBurned: Double = 0.0,
    val isActive: Boolean = false,
    val isPaused: Boolean = false
)

/**
 * Manages step counting and cardio metrics during active Walking or Running workouts.
 * Uses hardware [Sensor.TYPE_STEP_COUNTER] or [Sensor.TYPE_STEP_DETECTOR].
 *
 * Steps are ONLY counted when a workout is actively running and the app is active in foreground.
 */
class StepCounterManager(private val context: Context) : SensorEventListener {

    companion object {
        private val IST = ZoneId.of("Asia/Kolkata")

        // Average stride lengths in meters
        private const val STRIDE_WALKING_M = 0.762
        private const val STRIDE_RUNNING_M = 0.95

        // MET factors used for calorie estimation (Samsung Health style)
        private const val MET_WALKING = 3.5
        private const val MET_RUNNING = 8.0

        // Default user weight in kg
        private const val DEFAULT_WEIGHT_KG = 70.0

        // Timer tick interval
        private const val TIMER_INTERVAL_MS = 1000L
    }

    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val stepDetectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    // ── State flows ──────────────────────────────────────────────────────

    private val _metrics = MutableStateFlow(CardioMetrics())
    val metrics: StateFlow<CardioMetrics> = _metrics.asStateFlow()

    // ── Internal tracking state ──────────────────────────────────────────

    private var workoutType: CardioWorkoutType = CardioWorkoutType.WALKING
    private var baselineSteps: Int = -1
    private var accumulatedSteps: Int = 0
    private var accumulatedSeconds: Long = 0
    private var segmentStartMillis: Long = 0
    private var gpsDistanceMeters: Double = 0.0
    private var useGpsDistance: Boolean = false

    private var isActive: Boolean = false
    private var isPaused: Boolean = false

    private var timerJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    var startedAt: ZonedDateTime? = null
        private set

    // ── Public API ───────────────────────────────────────────────────────

    /** Returns `true` if the device has a step counter or step detector sensor. */
    fun hasSensor(): Boolean = stepCounterSensor != null || stepDetectorSensor != null

    /**
     * Start a new cardio workout of the given [type].
     * Resets all metrics and begins counting steps only while active.
     */
    fun start(type: CardioWorkoutType) {
        if (isActive) return

        workoutType = type
        baselineSteps = -1
        accumulatedSteps = 0
        accumulatedSeconds = 0
        gpsDistanceMeters = 0.0
        useGpsDistance = false
        segmentStartMillis = System.currentTimeMillis()
        isActive = true
        isPaused = false
        startedAt = ZonedDateTime.now(IST)

        _metrics.value = CardioMetrics(isActive = true)

        registerSensors()
        startTimer()
    }

    /**
     * Pause the current workout. Unregisters sensor listener to prevent battery drain
     * and stop counting steps during pause.
     */
    fun pause() {
        if (!isActive || isPaused) return

        isPaused = true
        accumulatedSeconds += currentSegmentSeconds()
        accumulatedSteps = _metrics.value.steps

        unregisterSensors()
        stopTimer()

        emitMetrics()
    }

    /**
     * Resume a paused workout. Re-registers the sensor listener.
     */
    fun resume() {
        if (!isActive || !isPaused) return

        isPaused = false
        baselineSteps = -1 // will be re-baselined on next sensor event
        segmentStartMillis = System.currentTimeMillis()

        registerSensors()
        startTimer()

        emitMetrics()
    }

    /**
     * Stop the workout and unregister sensor.
     */
    fun stop() {
        if (!isActive) return

        if (!isPaused) {
            accumulatedSeconds += currentSegmentSeconds()
            accumulatedSteps = _metrics.value.steps
        }

        isActive = false
        isPaused = false

        unregisterSensors()
        stopTimer()

        emitMetrics()
    }

    /**
     * Update distance using real-time GPS coordinates.
     */
    fun updateGpsDistance(distanceM: Double) {
        if (!isActive || isPaused) return
        gpsDistanceMeters = distanceM
        useGpsDistance = true
        emitMetrics()
    }

    /**
     * Cleanup resources.
     */
    fun destroy() {
        stop()
        scope.cancel()
    }

    // ── SensorEventListener ──────────────────────────────────────────────

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || !isActive || isPaused) return

        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                val totalDeviceSteps = event.values[0].toInt()
                if (baselineSteps < 0) {
                    baselineSteps = totalDeviceSteps
                }
                val segmentSteps = (totalDeviceSteps - baselineSteps).coerceAtLeast(0)
                val totalSteps = accumulatedSteps + segmentSteps
                emitMetrics(totalSteps)
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                if (stepCounterSensor == null && event.values.isNotEmpty() && event.values[0] == 1.0f) {
                    val totalSteps = _metrics.value.steps + 1
                    emitMetrics(totalSteps)
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }

    // ── Private helpers ──────────────────────────────────────────────────

    private fun registerSensors() {
        if (stepCounterSensor != null) {
            sensorManager.registerListener(
                this,
                stepCounterSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        } else if (stepDetectorSensor != null) {
            sensorManager.registerListener(
                this,
                stepDetectorSensor,
                SensorManager.SENSOR_DELAY_UI
            )
        }
    }

    private fun unregisterSensors() {
        sensorManager.unregisterListener(this)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(TIMER_INTERVAL_MS)
                if (isActive && !isPaused) {
                    emitMetrics()
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun currentSegmentSeconds(): Long =
        (System.currentTimeMillis() - segmentStartMillis) / 1000

    private fun totalElapsedSeconds(): Long =
        if (isPaused) accumulatedSeconds
        else accumulatedSeconds + currentSegmentSeconds()

    private fun emitMetrics(steps: Int? = null) {
        val currentSteps = steps ?: _metrics.value.steps
        val elapsed = totalElapsedSeconds()

        val strideLength = when (workoutType) {
            CardioWorkoutType.WALKING -> STRIDE_WALKING_M
            CardioWorkoutType.RUNNING -> STRIDE_RUNNING_M
        }
        val stepDistanceMeters = currentSteps * strideLength

        // Prefer GPS distance if available and non-zero, otherwise step-based distance
        val finalDistanceMeters = if (useGpsDistance && gpsDistanceMeters > 0.0) {
            gpsDistanceMeters
        } else {
            stepDistanceMeters
        }

        val distanceKm = finalDistanceMeters / 1000.0

        // Pace: minutes per km
        val paceMinPerKm = if (distanceKm > 0.02) {
            (elapsed / 60.0) / distanceKm
        } else {
            0.0
        }

        // Speed: km/h
        val speedKmPerH = if (elapsed > 2) {
            distanceKm / (elapsed / 3600.0)
        } else {
            0.0
        }

        // Cadence (steps per minute)
        val cadenceSpm = if (elapsed > 5) {
            ((currentSteps.toDouble() / elapsed) * 60.0).toInt()
        } else {
            0
        }

        // Calories burned (kcal)
        val metFactor = when (workoutType) {
            CardioWorkoutType.WALKING -> MET_WALKING
            CardioWorkoutType.RUNNING -> MET_RUNNING
        }
        // Formula: (MET * 3.5 * weightKg / 200) * durationMinutes
        val durationMinutes = elapsed / 60.0
        val caloriesBurned = (metFactor * 3.5 * DEFAULT_WEIGHT_KG / 200.0) * durationMinutes

        _metrics.value = CardioMetrics(
            steps = currentSteps,
            distanceMeters = finalDistanceMeters,
            durationSeconds = elapsed,
            paceMinPerKm = paceMinPerKm,
            speedKmPerH = speedKmPerH,
            cadenceSpm = cadenceSpm,
            caloriesBurned = caloriesBurned,
            isActive = isActive,
            isPaused = isPaused
        )
    }
}
