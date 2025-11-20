package com.example.workouttracker

import kotlin.math.*
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * WorkoutEngine: pure Kotlin class implementing rep detection, smoothing, scoring (XP), and event callbacks.
 * It is intentionally independent of Android UI so it can be unit-tested.
 */
class WorkoutEngine(private val listener: Listener? = null) {
    interface Listener {
        fun onRepCountUpdated(reps: Int)
        fun onXpUpdated(totalXp: Int)
        fun onRepLogged(repIndex: Int, timestampMs: Long, xpEarned: Int)
        fun onFrameFeedback(feedback: Feedback) {}
    }

    enum class ExerciseType { PUSHUP, SQUAT, BICEP_LEFT, BICEP_RIGHT, LUNGES, SHOULDER_PRESS, JUMPING_JACKS }

    data class Feedback(
        val reps: Int,
        val stage: String,          // 'up' or 'down'
        val leftAngle: Float,
        val rightAngle: Float,
        val avgAngle: Float,
        val depthPercent: Int,
        val formScore: Int,
        val validDepth: Boolean,
        val repImminent: Boolean,
        val amplitudeAngleDelta: Float,
        val rangeOk: Boolean,
        val exercise: ExerciseType
    )

    private companion object {
        const val LEFT_SHOULDER = 11; const val RIGHT_SHOULDER = 12
        const val LEFT_ELBOW = 13; const val RIGHT_ELBOW = 14
        const val LEFT_WRIST = 15; const val RIGHT_WRIST = 16
        const val LEFT_HIP = 23; const val RIGHT_HIP = 24
        const val LEFT_KNEE = 25; const val RIGHT_KNEE = 26
        const val LEFT_ANKLE = 27; const val RIGHT_ANKLE = 28
        const val VISIBILITY_THRESHOLD = 0.5f
    }

    // Config (defaults for pushup). Will branch per exercise.
    private var downAngleThreshold = 100f
    private var upAngleThreshold = 160f
    private var squatDownKneeAngle = 100f
    private var squatUpKneeAngle = 170f
    private var smoothingWindow = 5
    private var minRepIntervalMs = 600L

    // Pushup refinement parameters
    private var minPushAmplitudeDeg = 35f           // minimal elbow angle excursion required
    private var bottomDwellMs = 120L                // must stay at bottom at least this long before counting
    private var symmetryToleranceDeg = 15f          // max difference between left/right at top/bottom

    // Bicep curl parameters
    private var curlDownAngle = 70f
    private var curlUpAngle = 150f
    private var minCurlAmplitude = 40f

    // New Exercise Parameters
    private var lungeDownAngle = 100f // Knee angle
    private var lungeUpAngle = 160f
    private var shoulderPressDownAngle = 70f // Elbow angle
    private var shoulderPressUpAngle = 150f
    private var plankThresholdDeg = 15f // Deviation from straight line
    private var cobraThresholdDeg = 130f // Hip extension angle
    private var jumpingJackArmAngle = 150f // Shoulder abduction

    private val leftAngles = ArrayDeque<Float>()
    private val rightAngles = ArrayDeque<Float>()

    private var stage = "up" // 'up' or 'down'
    private var reps = 0
    private var totalXp = 0
    private var xpRemainder = 0f // accumulate fractional XP (PERSISTED across resets to avoid losing partial progress)
    private var lastRepTime = 0L
    private var lastTimestamp = 0L

    // Track min/max within current cycle for amplitude (for feedback only)
    private var cycleMinAngle = 180f
    private var cycleMaxAngle = 0f

    // Pushup gating times
    private var bottomReachTime = 0L
    private var topReachTime = 0L

    private var exercise: ExerciseType = ExerciseType.PUSHUP

    private var lastFeedback: Feedback = Feedback(0,"up",180f,180f,180f,0,0,false,false,0f,true, exercise)

    // Add reset listeners
    private val resetCallbacks = mutableListOf<() -> Unit>()
    fun addResetListener(cb: () -> Unit) { resetCallbacks += cb }

    fun setExerciseType(type: ExerciseType) {
        if (exercise != type) {
            // Keep fractional XP remainder so partial work on previous exercise contributes later.
            val keepRemainder = xpRemainder
            reset() // will reset reps & integer session xp but we'll restore remainder
            xpRemainder = keepRemainder
            exercise = type
            when(type) {
                ExerciseType.PUSHUP -> { downAngleThreshold = 100f; upAngleThreshold = 160f; minRepIntervalMs = 600L }
                ExerciseType.SQUAT -> { // use knee angle thresholds
                    squatDownKneeAngle = 100f // knee angle smaller = deeper squat; treat <100 as down
                    squatUpKneeAngle = 170f
                    minRepIntervalMs = 700L
                }
                ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT -> {
                    curlDownAngle = 70f; curlUpAngle = 150f; minCurlAmplitude = 40f; minRepIntervalMs = 500L
                }
                ExerciseType.LUNGES -> { minRepIntervalMs = 800L }
                ExerciseType.SHOULDER_PRESS -> { minRepIntervalMs = 600L }
                ExerciseType.JUMPING_JACKS -> { minRepIntervalMs = 500L }
            }
        }
    }
    fun getExerciseType() = exercise

    fun updateFromLandmarks(landmarks: List<FloatArray>?, @Suppress("UNUSED_PARAMETER") timestampMs: Long) {
        if (landmarks == null || landmarks.size <= RIGHT_ANKLE) return
        val nowIst = currentIstMillis()
        val dt = if (lastTimestamp==0L) 0L else (nowIst - lastTimestamp)
        lastTimestamp = nowIst
        try {
            when(exercise) {
                ExerciseType.PUSHUP -> processPushup(landmarks, nowIst)
                ExerciseType.SQUAT -> processSquat(landmarks, nowIst)
                ExerciseType.BICEP_LEFT -> processBicepCurl(landmarks, nowIst, true)
                ExerciseType.BICEP_RIGHT -> processBicepCurl(landmarks, nowIst, false)
                ExerciseType.LUNGES -> processLunges(landmarks, nowIst)
                ExerciseType.SHOULDER_PRESS -> processShoulderPress(landmarks, nowIst)
                ExerciseType.JUMPING_JACKS -> processJumpingJacks(landmarks, nowIst)
            }
        } catch (_: Exception) {}
    }

    // ----------------------- PUSHUP -----------------------
    private fun processPushup(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_ELBOW, LEFT_SHOULDER, LEFT_WRIST)
        val rVis = visible(lm, RIGHT_ELBOW, RIGHT_SHOULDER, RIGHT_WRIST)
        if (!lVis && !rVis) {
            emitFeedback(Float.NaN, Float.NaN, ts)
            return
        }
        var leftAngle = Float.NaN; var rightAngle = Float.NaN
        if (lVis) {
            leftAngle = calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_ELBOW], lm[LEFT_WRIST]); pushAngle(leftAngles, leftAngle)
        }
        if (rVis) {
            rightAngle = calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW], lm[RIGHT_WRIST]); pushAngle(rightAngles, rightAngle)
        }
        val smoothLeft = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothRight = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val haveBoth = !smoothLeft.isNaN() && !smoothRight.isNaN()
        val currentAvg = listOf(smoothLeft, smoothRight).filter{!it.isNaN()}.average().toFloat()

        // Update cycle amplitude tracking while in down phase movement
        cycleMinAngle = min(cycleMinAngle, currentAvg)
        cycleMaxAngle = max(cycleMaxAngle, currentAvg)

        val bothDown = haveBoth && smoothLeft < downAngleThreshold && smoothRight < downAngleThreshold
        val bothUp = haveBoth && smoothLeft > upAngleThreshold && smoothRight > upAngleThreshold

        val now = ts

        if (bothDown && stage != "down") {
            // Entering bottom phase
            stage = "down"
            bottomReachTime = now
            cycleMinAngle = currentAvg
            cycleMaxAngle = currentAvg
        }

        if (bothUp && stage == "down") {
            val dwellOk = (now - bottomReachTime) >= bottomDwellMs
            val amplitude = cycleMaxAngle - cycleMinAngle
            val amplitudeOk = amplitude >= minPushAmplitudeDeg
            val symmetryOk = haveBoth && abs(smoothLeft - smoothRight) <= symmetryToleranceDeg
            val intervalOk = (now - lastRepTime) >= minRepIntervalMs
            if (dwellOk && amplitudeOk && symmetryOk && intervalOk) {
                incrementReps(now, 1f * LevelSystem.xpPerPushup())
            }
            stage = "up"
            topReachTime = now
            // Prepare for next cycle amplitude tracking
            cycleMinAngle = currentAvg
            cycleMaxAngle = currentAvg
        }

        // Single-arm fallback (stricter to avoid false positives)
        if (!haveBoth) {
            val arm = if (!smoothLeft.isNaN()) smoothLeft else smoothRight
            if (!arm.isNaN()) {
                val downThresh = downAngleThreshold - 5f
                val upThresh = upAngleThreshold - 5f
                val armDown = arm < downThresh
                val armUp = arm > upThresh
                if (armDown && stage != "down") {
                    stage = "down"; bottomReachTime = now; cycleMinAngle = arm; cycleMaxAngle = arm
                }
                if (armUp && stage == "down") {
                    val dwellOk = (now - bottomReachTime) >= bottomDwellMs
                    val amplitude = cycleMaxAngle - cycleMinAngle
                    val amplitudeOk = amplitude >= (minPushAmplitudeDeg + 5f) // stricter single arm
                    val intervalOk = (now - lastRepTime) >= (minRepIntervalMs + 150L)
                    if (dwellOk && amplitudeOk && intervalOk) incrementReps(now, 1f * LevelSystem.xpPerPushup())
                    stage = "up"; topReachTime = now; cycleMinAngle = arm; cycleMaxAngle = arm
                }
            }
        }
        emitFeedback(smoothLeft, smoothRight, now)
    }

    // ----------------------- SQUAT (unchanged) -----------------------
    private fun processSquat(lm: List<FloatArray>, ts: Long) {
        if (!visible(lm, LEFT_KNEE, LEFT_HIP, LEFT_ANKLE) && !visible(lm, RIGHT_KNEE, RIGHT_HIP, RIGHT_ANKLE)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }
        val lVis = visible(lm, LEFT_KNEE, LEFT_HIP, LEFT_ANKLE)
        val rVis = visible(lm, RIGHT_KNEE, RIGHT_HIP, RIGHT_ANKLE)
        var leftAngle = Float.NaN; var rightAngle = Float.NaN
        if (lVis) { leftAngle = calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]); pushAngle(leftAngles, leftAngle) }
        if (rVis) { rightAngle = calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]); pushAngle(rightAngles, rightAngle) }
        val smoothLeft = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothRight = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val haveBoth = !smoothLeft.isNaN() && !smoothRight.isNaN()
        val currentAvg = listOf(smoothLeft, smoothRight).filter{!it.isNaN()}.average().toFloat()
        cycleMinAngle = min(cycleMinAngle, currentAvg); cycleMaxAngle = max(cycleMaxAngle, currentAvg)
        val now = ts
        if (haveBoth) {
            if (smoothLeft < squatDownKneeAngle && smoothRight < squatDownKneeAngle && stage != "down") { stage="down"; cycleMinAngle=currentAvg; cycleMaxAngle=currentAvg }
            if (smoothLeft > squatUpKneeAngle && smoothRight > squatUpKneeAngle && stage=="down") {
                if (now - lastRepTime >= minRepIntervalMs) incrementReps(now, 1f * LevelSystem.xpPerSquat())
                stage = "up"
            }
        } else {
            val knee = if (!smoothLeft.isNaN()) smoothLeft else smoothRight
            if (!knee.isNaN()) {
                if (knee < squatDownKneeAngle && stage != "down") { stage = "down"; cycleMinAngle=knee; cycleMaxAngle=knee }
                if (knee > squatUpKneeAngle && stage == "down") {
                    if (now - lastRepTime >= minRepIntervalMs) incrementReps(now, 1f * LevelSystem.xpPerSquat())
                    stage = "up"
                }
            }
        }
        emitFeedback(smoothLeft, smoothRight, now)
    }

    // ----------------------- BICEP CURL -----------------------
    private fun processBicepCurl(lm: List<FloatArray>, ts: Long, left: Boolean) {
        val shoulder = if (left) LEFT_SHOULDER else RIGHT_SHOULDER
        val elbow = if (left) LEFT_ELBOW else RIGHT_ELBOW
        val wrist = if (left) LEFT_WRIST else RIGHT_WRIST
        if (!(lm[shoulder][2] > VISIBILITY_THRESHOLD && lm[elbow][2] > VISIBILITY_THRESHOLD && lm[wrist][2] > VISIBILITY_THRESHOLD)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }
        val angle = calculateAngle(lm[shoulder], lm[elbow], lm[wrist])
        val buffer = if (left) leftAngles else rightAngles
        pushAngle(buffer, angle)
        val smooth = if (buffer.isEmpty()) angle else buffer.average().toFloat()
        // Track amplitude
        cycleMinAngle = min(cycleMinAngle, smooth)
        cycleMaxAngle = max(cycleMaxAngle, smooth)
        val now = ts
        val isDown = smooth < curlDownAngle
        val isUp = smooth > curlUpAngle
        if (isDown && stage != "down") {
            stage = "down"
            bottomReachTime = now
            cycleMinAngle = smooth; cycleMaxAngle = smooth
        }
        if (isUp && stage == "down") {
            val dwellOk = (now - bottomReachTime) >= 80L
            val amp = cycleMaxAngle - cycleMinAngle
            val ampOk = amp >= minCurlAmplitude
            val intervalOk = (now - lastRepTime) >= minRepIntervalMs
            if (dwellOk && ampOk && intervalOk) {
                incrementReps(now, LevelSystem.xpPerBicepCurl())
            }
            stage = "up"
            topReachTime = now
            cycleMinAngle = smooth; cycleMaxAngle = smooth
        }
        // For feedback we reuse left/right channels so UI can show values
        if (left) emitFeedback(smooth, Float.NaN, now) else emitFeedback(Float.NaN, smooth, now)
    }

    // ----------------------- NEW EXERCISES -----------------------

    private fun processLunges(lm: List<FloatArray>, ts: Long) {
        // Similar to squat but check for one leg bent significantly more or alternating
        // For simplicity, we track the "working" leg (the one bending more)
        val lVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]) else Float.NaN
        
        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)
        
        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        
        // Use the "deeper" leg to drive the rep count
        val activeAngle = min(if(smoothL.isNaN()) 180f else smoothL, if(smoothR.isNaN()) 180f else smoothR)
        
        cycleMinAngle = min(cycleMinAngle, activeAngle)
        cycleMaxAngle = max(cycleMaxAngle, activeAngle)
        
        if (activeAngle < lungeDownAngle && stage != "down") {
            stage = "down"; bottomReachTime = ts; cycleMinAngle = activeAngle; cycleMaxAngle = activeAngle
        }
        if (activeAngle > lungeUpAngle && stage == "down") {
            if (ts - lastRepTime >= minRepIntervalMs) incrementReps(ts, LevelSystem.xpPerLunge())
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    private fun processShoulderPress(lm: List<FloatArray>, ts: Long) {
        // Elbow angle: Shoulder-Elbow-Wrist. 
        // Down: Elbows bent (< 70-90). Up: Elbows extended (> 150).
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_ELBOW], lm[LEFT_WRIST]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW], lm[RIGHT_WRIST]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter{!it.isNaN()}.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        // Shoulder press: Start "Down" (hands at shoulders), push "Up"
        // But our stage logic usually assumes "up" is start. Let's say "down" is the contracted phase (hands low).
        // Actually standard press starts hands low (Down phase) -> Push Up -> Return Down.
        // So if angle < 90 (Down), then angle > 160 (Up).
        
        if (avg < shoulderPressDownAngle && stage != "down") {
             stage = "down"; cycleMinAngle = avg; cycleMaxAngle = avg
        }
        if (avg > shoulderPressUpAngle && stage == "down") {
             if (ts - lastRepTime >= minRepIntervalMs) incrementReps(ts, LevelSystem.xpPerShoulderPress())
             stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // Plank and Cobra removed
    /*
    private fun processPlank(lm: List<FloatArray>, ts: Long) { ... }
    private fun processCobra(lm: List<FloatArray>, ts: Long) { ... }
    */

    private fun processJumpingJacks(lm: List<FloatArray>, ts: Long) {
        // Arms go up (Shoulder-Elbow-Wrist angle? Or Shoulder Abduction?)
        // Let's use Wrist Y vs Shoulder Y.
        // And Ankle spread (Left Ankle X - Right Ankle X).
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_WRIST, LEFT_HIP)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_WRIST, RIGHT_HIP)
        if (!lVis || !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }
        
        // Check if hands are above shoulders
        val handsUp = lm[LEFT_WRIST][1] < lm[LEFT_SHOULDER][1] && lm[RIGHT_WRIST][1] < lm[RIGHT_SHOULDER][1]
        val handsDown = lm[LEFT_WRIST][1] > lm[LEFT_HIP][1] && lm[RIGHT_WRIST][1] > lm[RIGHT_HIP][1]
        
        // We can also check leg spread, but hands are reliable enough for simple logic.
        
        if (handsDown && stage != "down") {
            stage = "down" // Started/Reset
        }
        if (handsUp && stage == "down") {
            if (ts - lastRepTime >= minRepIntervalMs) incrementReps(ts, LevelSystem.xpPerJumpingJack())
            stage = "up"
        }
        // Dummy angle for feedback
        emitFeedback(if(handsUp) 180f else 0f, Float.NaN, ts)
    }

    private fun visible(lm: List<FloatArray>, a: Int, b: Int, c: Int): Boolean =
        lm[a][2] > VISIBILITY_THRESHOLD && lm[b][2] > VISIBILITY_THRESHOLD && lm[c][2] > VISIBILITY_THRESHOLD

    private fun incrementReps(now: Long, xpGain: Float) {
        reps += 1; lastRepTime = now; addXp(xpGain)
        listener?.onRepLogged(reps, now, xpGain.toInt())
        listener?.onRepCountUpdated(reps)
        listener?.onXpUpdated(totalXp)
    }
    private fun addXp(delta: Float) { xpRemainder += delta; if (xpRemainder >= 1f) { val inc = xpRemainder.toInt(); totalXp += inc; xpRemainder -= inc } }

    private fun emitFeedback(left: Float, right: Float, ts: Long) {
        val validLeft = !left.isNaN(); val validRight = !right.isNaN()
        val avg = when {
            validLeft && validRight -> (left + right) / 2f
            validLeft -> left
            validRight -> right
            else -> 180f
        }
        val depthPercent = when(exercise) {
            ExerciseType.PUSHUP -> (((upAngleThreshold - avg) / (upAngleThreshold - downAngleThreshold).coerceAtLeast(1f) * 100f).coerceIn(0f,100f)).toInt()
            ExerciseType.SQUAT, ExerciseType.LUNGES -> (((squatUpKneeAngle - avg) / (squatUpKneeAngle - squatDownKneeAngle).coerceAtLeast(1f) *100f).coerceIn(0f,100f)).toInt()
            ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT, ExerciseType.SHOULDER_PRESS -> (((curlUpAngle - avg) / (curlUpAngle - curlDownAngle).coerceAtLeast(1f) *100f).coerceIn(0f,100f)).toInt()
            ExerciseType.JUMPING_JACKS -> if (avg > 90) 100 else 0
            else -> 0
        }
        val amplitude = (cycleMaxAngle - cycleMinAngle).coerceAtLeast(0f)
        val validDepth = when(exercise) {
            ExerciseType.PUSHUP -> avg < downAngleThreshold
            ExerciseType.SQUAT, ExerciseType.LUNGES -> avg < squatDownKneeAngle
            ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT -> avg < curlDownAngle
            ExerciseType.SHOULDER_PRESS -> avg < shoulderPressDownAngle
            ExerciseType.JUMPING_JACKS -> avg > 150f
            else -> false
        }
        val repImminent = (stage=="down")
        lastFeedback = Feedback(
            reps = reps,
            stage = stage,
            leftAngle = if (validLeft) left else 180f,
            rightAngle = if (validRight) right else 180f,
            avgAngle = avg,
            depthPercent = depthPercent,
            formScore = depthPercent,
            validDepth = validDepth,
            repImminent = repImminent,
            amplitudeAngleDelta = amplitude,
            rangeOk = when(exercise){
                ExerciseType.PUSHUP -> amplitude >= minPushAmplitudeDeg
                ExerciseType.SQUAT, ExerciseType.LUNGES -> amplitude >= 15f
                ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT, ExerciseType.SHOULDER_PRESS -> amplitude >= minCurlAmplitude
                else -> true
            },
            exercise = exercise
        )
        listener?.onFrameFeedback(lastFeedback)
    }

    private fun pushAngle(buffer: ArrayDeque<Float>, angle: Float) { if (!angle.isNaN()) { if (buffer.size >= smoothingWindow) buffer.removeFirst(); buffer.addLast(angle) } }

    private fun calculateAngle(a: FloatArray, b: FloatArray, c: FloatArray): Float {
        val ax = a[0] - b[0]; val ay = a[1] - b[1]
        val cx = c[0] - b[0]; val cy = c[1] - b[1]
        val dot = ax * cx + ay * cy
        val normA = sqrt(ax * ax + ay * ay)
        val normC = sqrt(cx * cx + cy * cy)
        val cos = (dot / (normA * normC + 1e-8f)).coerceIn(-1f, 1f)
        var angle = acos(cos) * (180f / Math.PI.toFloat())
        if (angle > 180f) angle = 360f - angle
        return angle
    }

    private fun currentIstMillis(): Long = ZonedDateTime.now(ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()

    fun getReps() = reps
    fun getTotalXp() = totalXp
    fun getLastFeedback(): Feedback = lastFeedback
    fun getXpRemainder(): Float = xpRemainder

    fun reset() {
        // NOTE: xpRemainder intentionally NOT reset so partial progress carries over between sessions/exercise switches.
        reps = 0; totalXp = 0; stage = "up"; lastRepTime = 0L
        leftAngles.clear(); rightAngles.clear(); cycleMinAngle = 180f; cycleMaxAngle = 0f
        bottomReachTime = 0L; topReachTime = 0L; lastTimestamp = 0L
        lastFeedback = Feedback(0,"up",180f,180f,180f,0,0,false,false,0f,true, exercise)
        resetCallbacks.forEach { it.invoke() }
        listener?.onRepCountUpdated(reps); listener?.onXpUpdated(totalXp)
    }
}
