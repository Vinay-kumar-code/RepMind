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
        fun onXpUpdated(totalXp: Float)
        fun onRepLogged(repIndex: Int, timestampMs: Long, xpEarned: Float)
        fun onFrameFeedback(feedback: Feedback) {}
    }

    enum class ExerciseType { 
        PUSHUP, SQUAT, BICEP_LEFT, BICEP_RIGHT, LUNGES, SHOULDER_PRESS, JUMPING_JACKS, PULLUP,
        LATERAL_RAISES, GLUTE_BRIDGES, CRUNCHES, TRICEP_DIPS, HIGH_KNEES, LEG_RAISES, PLANK,
        BURPEES, MOUNTAIN_CLIMBERS, RUSSIAN_TWISTS, CALF_RAISES
    }

    data class Feedback(
        val reps: Int,
        val stage: String,          // 'up' or 'down' or 'holding'
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

    // Config (defaults for pushup). Branches per exercise.
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
    private var pushupStartWristY = 0f
    private var pushupStartShoulderY = 0f

    // Bicep curl parameters
    private var curlDownAngle = 70f
    private var curlUpAngle = 150f
    private var minCurlAmplitude = 40f

    // Lunge parameters
    private var lungeDownAngle = 100f // Knee angle
    private var lungeUpAngle = 160f
    private var lungeStartHipY = 0f
    private var lungeMaxHipY = 0f

    // Squat displacement tracking
    private var squatStartHipY = 0f
    private var squatMaxHipY = 0f

    // Shoulder press parameters
    private var shoulderPressDownAngle = 75f // Elbow angle
    private var shoulderPressUpAngle = 150f

    // Plank & Jumping jacks
    private var plankThresholdDeg = 18f // Deviation from straight line
    private var jumpingJackArmAngle = 150f // Shoulder abduction

    // Pullup parameters
    private var pullupDownAngle = 150f
    private var pullupUpAngle = 90f
    private var pullupStartWristY = 0f
    private var pullupStartShoulderY = 0f
    private var pullupStartHipY = 0f
    private var pullupHasHips = false

    // Additional Workouts Parameters
    private var lateralRaiseDownAngle = 30f
    private var lateralRaiseUpAngle = 80f
    private var gluteBridgeDownAngle = 135f
    private var gluteBridgeUpAngle = 165f
    private var gluteBridgeStartHipY = 0f
    private var crunchDownAngle = 140f
    private var crunchUpAngle = 100f
    private var tricepDipDownAngle = 95f
    private var tricepDipUpAngle = 155f
    private var tricepDipStartWristY = 0f
    private var tricepDipStartShoulderY = 0f
    private var legRaiseDownAngle = 155f
    private var legRaiseUpAngle = 105f

    // Plank Hold State
    private var lastPlankSecondLoggedMs = 0L
    private var plankActiveHold = false
    private var highKneeActiveLeg = "none" // "none", "left", "right"

    // 4 New Workout Types State
    // Burpees
    private var burpeePhase = "standing" // "standing", "plank"
    private var burpeeStandingShoulderY = 0f
    private var burpeeStandingHipY = 0f

    // Mountain Climbers
    private var mountainClimberActiveLeg = "none" // "none", "left", "right"

    // Russian Twists
    private var russianTwistSide = "center" // "left", "right", "center"

    // Calf Raises
    private var calfRaiseBaselineShoulderY = Float.NaN
    private var calfRaiseMinKneeAngle = 155f
    private var minCalfRiseDelta = 0.022f

    private val leftAngles = ArrayDeque<Float>()
    private val rightAngles = ArrayDeque<Float>()

    private var stage = "up" // 'up' or 'down' or 'holding'
    private var reps = 0
    private var totalXp = 0f
    var xpRates: XpRates = XpRates()
    var xpMultiplier: Float
        get() = xpRates.multiplier
        set(value) { xpRates = xpRates.copy(multiplier = value) }
    private var lastRepTime = 0L
    private var lastTimestamp = 0L

    // Track min/max within current cycle for amplitude (for feedback only)
    private var cycleMinAngle = 180f
    private var cycleMaxAngle = 0f

    // Gating times
    private var bottomReachTime = 0L
    private var topReachTime = 0L

    private var exercise: ExerciseType = ExerciseType.PUSHUP

    private var lastFeedback: Feedback = Feedback(0, "up", 180f, 180f, 180f, 0, 0, false, false, 0f, true, exercise)

    // Add reset listeners
    private val resetCallbacks = mutableListOf<() -> Unit>()
    fun addResetListener(cb: () -> Unit) { resetCallbacks += cb }

    fun setExerciseType(type: ExerciseType) {
        if (exercise != type) {
            val oldXp = totalXp
            reset() // resets reps and totalXp
            totalXp = oldXp // keep totalXp across exercise switches
            exercise = type
            when(type) {
                ExerciseType.PUSHUP -> { downAngleThreshold = 100f; upAngleThreshold = 160f; minRepIntervalMs = 600L }
                ExerciseType.SQUAT -> { squatDownKneeAngle = 100f; squatUpKneeAngle = 170f; minRepIntervalMs = 700L }
                ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT -> {
                    curlDownAngle = 70f; curlUpAngle = 150f; minCurlAmplitude = 40f; minRepIntervalMs = 500L
                }
                ExerciseType.LUNGES -> { minRepIntervalMs = 800L }
                ExerciseType.SHOULDER_PRESS -> { minRepIntervalMs = 600L }
                ExerciseType.JUMPING_JACKS -> { minRepIntervalMs = 500L }
                ExerciseType.PULLUP -> { minRepIntervalMs = 800L }
                ExerciseType.LATERAL_RAISES -> { minRepIntervalMs = 600L }
                ExerciseType.GLUTE_BRIDGES -> { minRepIntervalMs = 700L }
                ExerciseType.CRUNCHES -> { minRepIntervalMs = 600L }
                ExerciseType.TRICEP_DIPS -> { minRepIntervalMs = 600L }
                ExerciseType.HIGH_KNEES -> { minRepIntervalMs = 350L }
                ExerciseType.LEG_RAISES -> { minRepIntervalMs = 800L }
                ExerciseType.PLANK -> { minRepIntervalMs = 1000L }
                ExerciseType.BURPEES -> { minRepIntervalMs = 1200L }
                ExerciseType.MOUNTAIN_CLIMBERS -> { minRepIntervalMs = 350L }
                ExerciseType.RUSSIAN_TWISTS -> { minRepIntervalMs = 400L }
                ExerciseType.CALF_RAISES -> { minRepIntervalMs = 600L }
            }
        }
    }
    fun getExerciseType() = exercise

    fun updateFromLandmarks(landmarks: List<FloatArray>?, timestampMs: Long = 0L) {
        if (landmarks == null || landmarks.size <= RIGHT_ANKLE) return
        val nowIst = if (timestampMs > 0L) timestampMs else currentIstMillis()
        val dt = if (lastTimestamp == 0L) 0L else (nowIst - lastTimestamp)
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
                ExerciseType.PULLUP -> processPullup(landmarks, nowIst)
                ExerciseType.LATERAL_RAISES -> processLateralRaises(landmarks, nowIst)
                ExerciseType.GLUTE_BRIDGES -> processGluteBridges(landmarks, nowIst)
                ExerciseType.CRUNCHES -> processCrunches(landmarks, nowIst)
                ExerciseType.TRICEP_DIPS -> processTricepDips(landmarks, nowIst)
                ExerciseType.HIGH_KNEES -> processHighKnees(landmarks, nowIst)
                ExerciseType.LEG_RAISES -> processLegRaises(landmarks, nowIst)
                ExerciseType.PLANK -> processPlank(landmarks, nowIst)
                ExerciseType.BURPEES -> processBurpees(landmarks, nowIst)
                ExerciseType.MOUNTAIN_CLIMBERS -> processMountainClimbers(landmarks, nowIst)
                ExerciseType.RUSSIAN_TWISTS -> processRussianTwists(landmarks, nowIst)
                ExerciseType.CALF_RAISES -> processCalfRaises(landmarks, nowIst)
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

        // Torso posture check: user must not be standing upright doing "air pushups"
        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        val lAnkVis = lm[LEFT_ANKLE][2] > VISIBILITY_THRESHOLD
        val rAnkVis = lm[RIGHT_ANKLE][2] > VISIBILITY_THRESHOLD

        val lShY = if (lVis) lm[LEFT_SHOULDER][1] else Float.NaN
        val rShY = if (rVis) lm[RIGHT_SHOULDER][1] else Float.NaN
        val shY = listOf(lShY, rShY).filter { !it.isNaN() }.average().toFloat()

        val hipY = listOf(
            if (lHipVis) lm[LEFT_HIP][1] else Float.NaN,
            if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN
        ).filter { !it.isNaN() }.average().toFloat()

        val ankY = listOf(
            if (lAnkVis) lm[LEFT_ANKLE][1] else Float.NaN,
            if (rAnkVis) lm[RIGHT_ANKLE][1] else Float.NaN
        ).filter { !it.isNaN() }.average().toFloat()

        val shX = listOf(
            if (lVis) lm[LEFT_SHOULDER][0] else Float.NaN,
            if (rVis) lm[RIGHT_SHOULDER][0] else Float.NaN
        ).filter { !it.isNaN() }.average().toFloat()

        val hipX = listOf(
            if (lHipVis) lm[LEFT_HIP][0] else Float.NaN,
            if (rHipVis) lm[RIGHT_HIP][0] else Float.NaN
        ).filter { !it.isNaN() }.average().toFloat()

        val isVerticalStanding = !hipY.isNaN() && !shY.isNaN() && (hipY - shY > 0.20f) &&
                                 (!ankY.isNaN() && ankY - hipY > 0.20f) &&
                                 (!shX.isNaN() && !hipX.isNaN() && abs(shX - hipX) < 0.20f)

        if (isVerticalStanding) {
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
        val currentAvg = listOf(smoothLeft, smoothRight).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, currentAvg)
        cycleMaxAngle = max(cycleMaxAngle, currentAvg)

        val bothDown = haveBoth && smoothLeft < downAngleThreshold && smoothRight < downAngleThreshold
        val bothUp = haveBoth && smoothLeft > upAngleThreshold && smoothRight > upAngleThreshold

        val lWrY = if (lVis) lm[LEFT_WRIST][1] else Float.NaN
        val rWrY = if (rVis) lm[RIGHT_WRIST][1] else Float.NaN
        val wrY = listOf(lWrY, rWrY).filter { !it.isNaN() }.average().toFloat()

        val now = ts

        if (bothDown && stage != "down") {
            stage = "down"
            bottomReachTime = now
            cycleMinAngle = currentAvg
            cycleMaxAngle = currentAvg
            pushupStartWristY = wrY
            pushupStartShoulderY = shY
        }

        if (bothUp && stage == "down") {
            val dwellOk = (now - bottomReachTime) >= bottomDwellMs
            val amplitude = cycleMaxAngle - cycleMinAngle
            val amplitudeOk = amplitude >= minPushAmplitudeDeg
            val symmetryOk = haveBoth && abs(smoothLeft - smoothRight) <= symmetryToleranceDeg
            val intervalOk = (now - lastRepTime) >= minRepIntervalMs
            // In true pushup, wrists remain anchored on floor/support (minimal vertical wrist migration)
            val handsAnchored = pushupStartWristY.isNaN() || wrY.isNaN() || abs(wrY - pushupStartWristY) <= 0.10f
            if (dwellOk && amplitudeOk && symmetryOk && intervalOk && handsAnchored) {
                incrementReps(now, 1f * LevelSystem.xpPerPushup(xpRates))
            }
            stage = "up"
            topReachTime = now
            cycleMinAngle = currentAvg
            cycleMaxAngle = currentAvg
        }

        // Single-arm fallback
        if (!haveBoth) {
            val arm = if (!smoothLeft.isNaN()) smoothLeft else smoothRight
            if (!arm.isNaN()) {
                val downThresh = downAngleThreshold - 5f
                val upThresh = upAngleThreshold - 5f
                val armDown = arm < downThresh
                val armUp = arm > upThresh
                if (armDown && stage != "down") {
                    stage = "down"; bottomReachTime = now; cycleMinAngle = arm; cycleMaxAngle = arm
                    pushupStartWristY = wrY; pushupStartShoulderY = shY
                }
                if (armUp && stage == "down") {
                    val dwellOk = (now - bottomReachTime) >= bottomDwellMs
                    val amplitude = cycleMaxAngle - cycleMinAngle
                    val amplitudeOk = amplitude >= (minPushAmplitudeDeg + 5f)
                    val intervalOk = (now - lastRepTime) >= (minRepIntervalMs + 150L)
                    val handsAnchored = pushupStartWristY.isNaN() || wrY.isNaN() || abs(wrY - pushupStartWristY) <= 0.10f
                    if (dwellOk && amplitudeOk && intervalOk && handsAnchored) {
                        incrementReps(now, 1f * LevelSystem.xpPerPushup(xpRates))
                    }
                    stage = "up"; topReachTime = now; cycleMinAngle = arm; cycleMaxAngle = arm
                }
            }
        }
        emitFeedback(smoothLeft, smoothRight, now)
    }

    // ----------------------- SQUAT -----------------------
    private fun processSquat(lm: List<FloatArray>, ts: Long) {
        val lKneeVis = visible(lm, LEFT_KNEE, LEFT_HIP, LEFT_ANKLE)
        val rKneeVis = visible(lm, RIGHT_KNEE, RIGHT_HIP, RIGHT_ANKLE)
        if (!lKneeVis && !rKneeVis) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lShVis = lm[LEFT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val rShVis = lm[RIGHT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val shY = listOf(if (lShVis) lm[LEFT_SHOULDER][1] else Float.NaN, if (rShVis) lm[RIGHT_SHOULDER][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        val lHipY = if (lKneeVis) lm[LEFT_HIP][1] else Float.NaN
        val rHipY = if (rKneeVis) lm[RIGHT_HIP][1] else Float.NaN
        val hipY = listOf(lHipY, rHipY).filter { !it.isNaN() }.average().toFloat()

        val lAnkY = if (lKneeVis) lm[LEFT_ANKLE][1] else Float.NaN
        val rAnkY = if (rKneeVis) lm[RIGHT_ANKLE][1] else Float.NaN
        val ankY = listOf(lAnkY, rAnkY).filter { !it.isNaN() }.average().toFloat()

        // Torso posture check: must be standing upright initially (shoulders above hips, hips above ankles)
        val isStanding = (hipY.isNaN() || shY.isNaN() || hipY - shY > 0.10f) &&
                         (ankY.isNaN() || hipY.isNaN() || ankY - hipY > 0.12f)
        if (!isStanding) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        var leftAngle = Float.NaN; var rightAngle = Float.NaN
        if (lKneeVis) { leftAngle = calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]); pushAngle(leftAngles, leftAngle) }
        if (rKneeVis) { rightAngle = calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]); pushAngle(rightAngles, rightAngle) }
        val smoothLeft = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothRight = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val haveBoth = !smoothLeft.isNaN() && !smoothRight.isNaN()
        val currentAvg = listOf(smoothLeft, smoothRight).filter { !it.isNaN() }.average().toFloat()
        cycleMinAngle = min(cycleMinAngle, currentAvg); cycleMaxAngle = max(cycleMaxAngle, currentAvg)

        if (!hipY.isNaN()) {
            squatMaxHipY = max(squatMaxHipY, hipY)
        }

        val now = ts
        if (haveBoth) {
            if (smoothLeft > squatUpKneeAngle && smoothRight > squatUpKneeAngle && stage != "down") {
                squatStartHipY = hipY
            }
            if (smoothLeft < squatDownKneeAngle && smoothRight < squatDownKneeAngle && stage != "down") {
                stage = "down"
                cycleMinAngle = currentAvg; cycleMaxAngle = currentAvg
                squatMaxHipY = hipY
            }
            if (smoothLeft > squatUpKneeAngle && smoothRight > squatUpKneeAngle && stage == "down") {
                // Verify hips actually dropped during descent (prevents sitting/kicking false reps)
                val hipDropped = squatStartHipY.isNaN() || (squatMaxHipY - squatStartHipY) >= 0.035f
                val amplitudeOk = (cycleMaxAngle - cycleMinAngle) >= 40f
                if (now - lastRepTime >= minRepIntervalMs && hipDropped && amplitudeOk) {
                    incrementReps(now, 1f * LevelSystem.xpPerSquat(xpRates))
                }
                stage = "up"
                squatStartHipY = hipY
            }
        } else {
            val knee = if (!smoothLeft.isNaN()) smoothLeft else smoothRight
            if (!knee.isNaN()) {
                if (knee > squatUpKneeAngle && stage != "down") {
                    squatStartHipY = hipY
                }
                if (knee < squatDownKneeAngle && stage != "down") {
                    stage = "down"; cycleMinAngle = knee; cycleMaxAngle = knee
                    squatMaxHipY = hipY
                }
                if (knee > squatUpKneeAngle && stage == "down") {
                    val hipDropped = squatStartHipY.isNaN() || (squatMaxHipY - squatStartHipY) >= 0.035f
                    val amplitudeOk = (cycleMaxAngle - cycleMinAngle) >= 40f
                    if (now - lastRepTime >= minRepIntervalMs && hipDropped && amplitudeOk) {
                        incrementReps(now, 1f * LevelSystem.xpPerSquat(xpRates))
                    }
                    stage = "up"
                    squatStartHipY = hipY
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

        // Elbow stability: elbow should remain below shoulder (not swinging wildly up like a front raise)
        if (lm[elbow][1] < lm[shoulder][1] - 0.05f) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val angle = calculateAngle(lm[shoulder], lm[elbow], lm[wrist])
        val buffer = if (left) leftAngles else rightAngles
        pushAngle(buffer, angle)
        val smooth = if (buffer.isEmpty()) angle else buffer.average().toFloat()

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
                incrementReps(now, LevelSystem.xpPerBicepCurl(xpRates))
            }
            stage = "up"
            topReachTime = now
            cycleMinAngle = smooth; cycleMaxAngle = smooth
        }
        if (left) emitFeedback(smooth, Float.NaN, now) else emitFeedback(Float.NaN, smooth, now)
    }

    // ----------------------- LUNGES -----------------------
    private fun processLunges(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lShVis = lm[LEFT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val rShVis = lm[RIGHT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val shY = listOf(if (lShVis) lm[LEFT_SHOULDER][1] else Float.NaN, if (rShVis) lm[RIGHT_SHOULDER][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        val lHipY = if (lVis) lm[LEFT_HIP][1] else Float.NaN
        val rHipY = if (rVis) lm[RIGHT_HIP][1] else Float.NaN
        val hipY = listOf(lHipY, rHipY).filter { !it.isNaN() }.average().toFloat()

        // Upright posture
        if (!shY.isNaN() && !hipY.isNaN() && (hipY - shY < 0.10f)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()

        val activeAngle = min(if (smoothL.isNaN()) 180f else smoothL, if (smoothR.isNaN()) 180f else smoothR)

        cycleMinAngle = min(cycleMinAngle, activeAngle)
        cycleMaxAngle = max(cycleMaxAngle, activeAngle)

        if (!hipY.isNaN()) {
            lungeMaxHipY = max(lungeMaxHipY, hipY)
        }

        if (activeAngle > lungeUpAngle && stage != "down") {
            lungeStartHipY = hipY
        }
        if (activeAngle < lungeDownAngle && stage != "down") {
            stage = "down"; bottomReachTime = ts; cycleMinAngle = activeAngle; cycleMaxAngle = activeAngle
            lungeMaxHipY = hipY
        }
        if (activeAngle > lungeUpAngle && stage == "down") {
            val hipDropped = lungeStartHipY.isNaN() || (lungeMaxHipY - lungeStartHipY) >= 0.035f
            if (ts - lastRepTime >= minRepIntervalMs && hipDropped) {
                incrementReps(ts, LevelSystem.xpPerLunge(xpRates))
            }
            stage = "up"
            lungeStartHipY = hipY
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- SHOULDER PRESS -----------------------
    private fun processShoulderPress(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lShY = if (lVis) lm[LEFT_SHOULDER][1] else Float.NaN
        val rShY = if (rVis) lm[RIGHT_SHOULDER][1] else Float.NaN
        val shY = listOf(lShY, rShY).filter { !it.isNaN() }.average().toFloat()

        val lWrY = if (lVis) lm[LEFT_WRIST][1] else Float.NaN
        val rWrY = if (rVis) lm[RIGHT_WRIST][1] else Float.NaN
        val wrY = listOf(lWrY, rWrY).filter { !it.isNaN() }.average().toFloat()

        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        val hipY = listOf(
            if (lHipVis) lm[LEFT_HIP][1] else Float.NaN,
            if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN
        ).filter { !it.isNaN() }.average().toFloat()

        // Wrists must be at shoulder height or above throughout (not down at hips/waist)
        val handsAtShouldersOrAbove = (wrY <= shY + 0.12f) && (hipY.isNaN() || wrY < hipY - 0.08f)
        if (!handsAtShouldersOrAbove) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_ELBOW], lm[LEFT_WRIST]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW], lm[RIGHT_WRIST]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        if (avg < shoulderPressDownAngle && stage != "down") {
            stage = "down"; cycleMinAngle = avg; cycleMaxAngle = avg
        }
        if (avg > shoulderPressUpAngle && stage == "down") {
            val pressedOverhead = wrY < shY - 0.10f
            if (ts - lastRepTime >= minRepIntervalMs && pressedOverhead) {
                incrementReps(ts, LevelSystem.xpPerShoulderPress(xpRates))
            }
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- JUMPING JACKS -----------------------
    private fun processJumpingJacks(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_WRIST, LEFT_HIP)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_WRIST, RIGHT_HIP)
        if (!lVis || !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val handsUp = lm[LEFT_WRIST][1] < lm[LEFT_SHOULDER][1] && lm[RIGHT_WRIST][1] < lm[RIGHT_SHOULDER][1]
        val handsDown = lm[LEFT_WRIST][1] > lm[LEFT_HIP][1] && lm[RIGHT_WRIST][1] > lm[RIGHT_HIP][1]

        val lAnkVis = lm[LEFT_ANKLE][2] > VISIBILITY_THRESHOLD
        val rAnkVis = lm[RIGHT_ANKLE][2] > VISIBILITY_THRESHOLD
        val ankleSpread = if (lAnkVis && rAnkVis) abs(lm[LEFT_ANKLE][0] - lm[RIGHT_ANKLE][0]) else Float.NaN
        val hipWidth = abs(lm[LEFT_HIP][0] - lm[RIGHT_HIP][0]).coerceAtLeast(0.12f)

        val legsSpread = !ankleSpread.isNaN() && ankleSpread > (hipWidth * 1.5f)
        val legsTogether = ankleSpread.isNaN() || ankleSpread < (hipWidth * 1.4f)

        if (handsDown && legsTogether && stage != "down") {
            stage = "down"
        }
        if (handsUp && (legsSpread || ankleSpread.isNaN()) && stage == "down") {
            if (ts - lastRepTime >= minRepIntervalMs) {
                incrementReps(ts, LevelSystem.xpPerJumpingJack(xpRates))
            }
            stage = "up"
        }
        emitFeedback(if (handsUp) 180f else 0f, Float.NaN, ts)
    }

    // ----------------------- PULLUP -----------------------
    private fun processPullup(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_ELBOW], lm[LEFT_WRIST]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW], lm[RIGHT_WRIST]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        val lWristY = if (lVis) lm[LEFT_WRIST][1] else Float.NaN
        val rWristY = if (rVis) lm[RIGHT_WRIST][1] else Float.NaN
        val currentWristY = listOf(lWristY, rWristY).filter { !it.isNaN() }.average().toFloat()

        val lShoulderY = if (lVis) lm[LEFT_SHOULDER][1] else Float.NaN
        val rShoulderY = if (rVis) lm[RIGHT_SHOULDER][1] else Float.NaN
        val currentShoulderY = listOf(lShoulderY, rShoulderY).filter { !it.isNaN() }.average().toFloat()

        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        val lHipY = if (lHipVis) lm[LEFT_HIP][1] else Float.NaN
        val rHipY = if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN
        val currentHipY = listOf(lHipY, rHipY).filter { !it.isNaN() }.average().toFloat()

        // Wrists must be anchored overhead above shoulders (Y is smaller overhead)
        val wristsAboveShoulders = currentWristY < (currentShoulderY - 0.02f)

        // Hanging position: arms extended (> pullupDownAngle) and wrists overhead
        if (wristsAboveShoulders && avg > pullupDownAngle && stage != "down") {
            stage = "down"
            cycleMinAngle = avg; cycleMaxAngle = avg
            pullupStartWristY = currentWristY
            pullupStartShoulderY = currentShoulderY
            if (!currentHipY.isNaN()) {
                pullupStartHipY = currentHipY
                pullupHasHips = true
            } else {
                pullupHasHips = false
            }
        }

        // Pulling up: elbow flexed (< pullupUpAngle) while in hanging stage
        if (avg < pullupUpAngle && stage == "down") {
            // Downward wrist motion during rep (wrists anchored to overhead bar):
            // In screen coordinates Y increases downward. If currentWristY > pullupStartWristY + 0.06f,
            // hands moved downward towards floor!
            val downwardWristMotion = currentWristY - pullupStartWristY
            val wristAnchored = downwardWristMotion <= 0.06f && (currentWristY < currentShoulderY)

            // Shoulders and hips must displace upward towards the wrists
            val shoulderRise = pullupStartShoulderY - currentShoulderY
            val hipRise = if (pullupHasHips && !currentHipY.isNaN()) pullupStartHipY - currentHipY else Float.NaN

            val shouldersDisplacedUp = shoulderRise >= 0.045f
            val hipsDisplacedUp = if (pullupHasHips && !hipRise.isNaN()) hipRise >= 0.035f else true

            // If hands move down while shoulders/hips remain static, reject the rep!
            val handsMovedDownCheat = downwardWristMotion > 0.06f || shoulderRise < 0.035f

            val intervalOk = (ts - lastRepTime) >= minRepIntervalMs

            if (intervalOk && wristAnchored && shouldersDisplacedUp && hipsDisplacedUp && !handsMovedDownCheat) {
                incrementReps(ts, LevelSystem.xpPerPullup(xpRates))
            }
            stage = "up"
        }

        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- LATERAL RAISES -----------------------
    private fun processLateralRaises(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_HIP, LEFT_SHOULDER, LEFT_ELBOW)
        val rVis = visible(lm, RIGHT_HIP, RIGHT_SHOULDER, RIGHT_ELBOW)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        // Arms must be extended at the elbow (not curling/pressing)
        val lElbowVis = visible(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
        val rElbowVis = visible(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
        val lElbowAngle = if (lElbowVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_ELBOW], lm[LEFT_WRIST]) else Float.NaN
        val rElbowAngle = if (rElbowVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW], lm[RIGHT_WRIST]) else Float.NaN
        val avgElbowAngle = listOf(lElbowAngle, rElbowAngle).filter { !it.isNaN() }.average().toFloat()

        if (!avgElbowAngle.isNaN() && avgElbowAngle < 125f) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_SHOULDER], lm[LEFT_ELBOW]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        if (avg < lateralRaiseDownAngle && stage != "down") {
            stage = "down"
            cycleMinAngle = avg; cycleMaxAngle = avg
        }
        if (avg > lateralRaiseUpAngle && stage == "down") {
            val amplitude = cycleMaxAngle - cycleMinAngle
            if (ts - lastRepTime >= minRepIntervalMs && amplitude >= 40f) {
                incrementReps(ts, LevelSystem.xpPerLateralRaise(xpRates))
            }
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- GLUTE BRIDGES -----------------------
    private fun processGluteBridges(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lHipY = if (lVis) lm[LEFT_HIP][1] else Float.NaN
        val rHipY = if (rVis) lm[RIGHT_HIP][1] else Float.NaN
        val hipY = listOf(lHipY, rHipY).filter { !it.isNaN() }.average().toFloat()

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_HIP], lm[LEFT_KNEE]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_HIP], lm[RIGHT_KNEE]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        if (avg < gluteBridgeDownAngle && stage != "down") {
            stage = "down"
            cycleMinAngle = avg; cycleMaxAngle = avg
            gluteBridgeStartHipY = hipY
        }
        if (avg > gluteBridgeUpAngle && stage == "down") {
            val hipElevated = gluteBridgeStartHipY.isNaN() || hipY.isNaN() || (gluteBridgeStartHipY - hipY) >= 0.03f
            if (ts - lastRepTime >= minRepIntervalMs && hipElevated) {
                incrementReps(ts, LevelSystem.xpPerGluteBridge(xpRates))
            }
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- CRUNCHES -----------------------
    private fun processCrunches(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_KNEE)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_HIP, RIGHT_KNEE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_HIP], lm[LEFT_KNEE]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_HIP], lm[RIGHT_KNEE]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        if (avg > crunchDownAngle && stage != "down") {
            stage = "down"
            cycleMinAngle = avg; cycleMaxAngle = avg
        }
        if (avg < crunchUpAngle && stage == "down") {
            val amplitude = cycleMaxAngle - cycleMinAngle
            if (ts - lastRepTime >= minRepIntervalMs && amplitude >= 30f) {
                incrementReps(ts, LevelSystem.xpPerCrunch(xpRates))
            }
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- TRICEP DIPS -----------------------
    private fun processTricepDips(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_ELBOW, LEFT_WRIST)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_ELBOW, RIGHT_WRIST)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lShY = if (lVis) lm[LEFT_SHOULDER][1] else Float.NaN
        val rShY = if (rVis) lm[RIGHT_SHOULDER][1] else Float.NaN
        val shY = listOf(lShY, rShY).filter { !it.isNaN() }.average().toFloat()

        val lWrY = if (lVis) lm[LEFT_WRIST][1] else Float.NaN
        val rWrY = if (rVis) lm[RIGHT_WRIST][1] else Float.NaN
        val wrY = listOf(lWrY, rWrY).filter { !it.isNaN() }.average().toFloat()

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_ELBOW], lm[LEFT_WRIST]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_ELBOW], lm[RIGHT_WRIST]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        if (avg < tricepDipDownAngle && stage != "down") {
            stage = "down"
            cycleMinAngle = avg; cycleMaxAngle = avg
            tricepDipStartWristY = wrY
            tricepDipStartShoulderY = shY
        }
        if (avg > tricepDipUpAngle && stage == "down") {
            val wristAnchored = tricepDipStartWristY.isNaN() || wrY.isNaN() || abs(wrY - tricepDipStartWristY) <= 0.08f
            val shoulderDisplaced = tricepDipStartShoulderY.isNaN() || shY.isNaN() || (tricepDipStartShoulderY - shY) >= 0.03f
            if (ts - lastRepTime >= minRepIntervalMs && wristAnchored && shoulderDisplaced) {
                incrementReps(ts, LevelSystem.xpPerTricepDip(xpRates))
            }
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- HIGH KNEES -----------------------
    private fun processHighKnees(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lKneeY = if (lVis) lm[LEFT_KNEE][1] else Float.NaN
        val lHipY = if (lVis) lm[LEFT_HIP][1] else Float.NaN
        val rKneeY = if (rVis) lm[RIGHT_KNEE][1] else Float.NaN
        val rHipY = if (rVis) lm[RIGHT_HIP][1] else Float.NaN

        val lKneeUp = lVis && (lKneeY <= lHipY + 0.08f)
        val rKneeUp = rVis && (rKneeY <= rHipY + 0.08f)

        if (lKneeUp && highKneeActiveLeg != "left") {
            if (ts - lastRepTime >= minRepIntervalMs) {
                incrementReps(ts, LevelSystem.xpPerHighKnee(xpRates))
                highKneeActiveLeg = "left"
            }
        } else if (rKneeUp && highKneeActiveLeg != "right") {
            if (ts - lastRepTime >= minRepIntervalMs) {
                incrementReps(ts, LevelSystem.xpPerHighKnee(xpRates))
                highKneeActiveLeg = "right"
            }
        } else if (!lKneeUp && !rKneeUp) {
            highKneeActiveLeg = "none"
        }

        emitFeedback(if (lKneeUp) 180f else 90f, if (rKneeUp) 180f else 90f, ts)
    }

    // ----------------------- LEG RAISES -----------------------
    private fun processLegRaises(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_ANKLE)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_HIP, RIGHT_ANKLE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        // Knees must be extended (not cycling/tucking)
        val lKneeVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rKneeVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        val lKneeAngle = if (lKneeVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]) else Float.NaN
        val rKneeAngle = if (rKneeVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]) else Float.NaN
        val avgKnee = listOf(lKneeAngle, rKneeAngle).filter { !it.isNaN() }.average().toFloat()
        if (!avgKnee.isNaN() && avgKnee < 140f) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_HIP], lm[LEFT_ANKLE]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_HIP], lm[RIGHT_ANKLE]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        cycleMinAngle = min(cycleMinAngle, avg)
        cycleMaxAngle = max(cycleMaxAngle, avg)

        if (avg > legRaiseDownAngle && stage != "down") {
            stage = "down"
            cycleMinAngle = avg; cycleMaxAngle = avg
        }
        if (avg < legRaiseUpAngle && stage == "down") {
            val amplitude = cycleMaxAngle - cycleMinAngle
            if (ts - lastRepTime >= minRepIntervalMs && amplitude >= 35f) {
                incrementReps(ts, LevelSystem.xpPerLegRaise(xpRates))
            }
            stage = "up"
        }
        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- PLANK -----------------------
    private fun processPlank(lm: List<FloatArray>, ts: Long) {
        val lVis = visible(lm, LEFT_SHOULDER, LEFT_HIP, LEFT_ANKLE)
        val rVis = visible(lm, RIGHT_SHOULDER, RIGHT_HIP, RIGHT_ANKLE)
        if (!lVis && !rVis) { emitFeedback(Float.NaN, Float.NaN, ts); return }

        val lAngle = if (lVis) calculateAngle(lm[LEFT_SHOULDER], lm[LEFT_HIP], lm[LEFT_ANKLE]) else Float.NaN
        val rAngle = if (rVis) calculateAngle(lm[RIGHT_SHOULDER], lm[RIGHT_HIP], lm[RIGHT_ANKLE]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()
        val avg = listOf(smoothL, smoothR).filter { !it.isNaN() }.average().toFloat()

        val deviation = abs(180f - avg)
        val isHorizontal = (lVis && abs(lm[LEFT_SHOULDER][1] - lm[LEFT_ANKLE][1]) < 0.35f) ||
                           (rVis && abs(lm[RIGHT_SHOULDER][1] - lm[RIGHT_ANKLE][1]) < 0.35f)

        val isGoodForm = deviation <= plankThresholdDeg && isHorizontal

        if (isGoodForm) {
            stage = "holding"
            plankActiveHold = true
            if (lastPlankSecondLoggedMs == 0L) {
                lastPlankSecondLoggedMs = ts
            } else if (ts - lastPlankSecondLoggedMs >= 1000L) {
                lastPlankSecondLoggedMs = ts
                incrementReps(ts, LevelSystem.xpPerPlankSecond(xpRates))
            }
        } else {
            stage = if (deviation > plankThresholdDeg) "misaligned" else "ready"
            plankActiveHold = false
            lastPlankSecondLoggedMs = ts
        }

        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- BURPEES -----------------------
    private fun processBurpees(lm: List<FloatArray>, ts: Long) {
        val lShVis = lm[LEFT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val rShVis = lm[RIGHT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        if ((!lShVis && !rShVis) || (!lHipVis && !rHipVis)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val shY = listOf(if (lShVis) lm[LEFT_SHOULDER][1] else Float.NaN, if (rShVis) lm[RIGHT_SHOULDER][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()
        val hipY = listOf(if (lHipVis) lm[LEFT_HIP][1] else Float.NaN, if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        val lKneeVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rKneeVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        val lKneeAngle = if (lKneeVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]) else Float.NaN
        val rKneeAngle = if (rKneeVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]) else Float.NaN
        val avgKneeAngle = listOf(lKneeAngle, rKneeAngle).filter { !it.isNaN() }.average().toFloat()

        // Standing upright: hips high, knees extended (> 145°), torso vertical (hipY > shY + 0.15f)
        val isStanding = (hipY - shY > 0.15f) && (avgKneeAngle.isNaN() || avgKneeAngle > 145f)

        // Floor / plank position: horizontal body (abs(shY - hipY) < 0.25f)
        val isPlank = abs(shY - hipY) < 0.25f

        val now = ts

        if (isStanding && burpeePhase != "plank") {
            burpeePhase = "standing"
            stage = "up"
            burpeeStandingShoulderY = shY
            burpeeStandingHipY = hipY
        } else if (isPlank && burpeePhase == "standing") {
            // Dropped into floor plank
            burpeePhase = "plank"
            stage = "down"
        } else if (isStanding && burpeePhase == "plank") {
            // Returned to jump/stand from floor plank
            val intervalOk = (now - lastRepTime) >= minRepIntervalMs
            if (intervalOk) {
                incrementReps(now, LevelSystem.xpPerBurpee(xpRates))
            }
            burpeePhase = "standing"
            stage = "up"
            burpeeStandingShoulderY = shY
            burpeeStandingHipY = hipY
        }

        emitFeedback(if (isStanding) 180f else 90f, Float.NaN, ts)
    }

    // ----------------------- MOUNTAIN CLIMBERS -----------------------
    private fun processMountainClimbers(lm: List<FloatArray>, ts: Long) {
        val lShVis = lm[LEFT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val rShVis = lm[RIGHT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        if ((!lShVis && !rShVis) || (!lHipVis && !rHipVis)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val shY = listOf(if (lShVis) lm[LEFT_SHOULDER][1] else Float.NaN, if (rShVis) lm[RIGHT_SHOULDER][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()
        val hipY = listOf(if (lHipVis) lm[LEFT_HIP][1] else Float.NaN, if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        // Must be in horizontal plank position (not standing upright!)
        val isHorizontalPlank = abs(shY - hipY) < 0.35f
        if (!isHorizontalPlank) {
            stage = "ready"
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lKneeVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rKneeVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        val lAngle = if (lKneeVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]) else Float.NaN
        val rAngle = if (rKneeVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]) else Float.NaN

        pushAngle(leftAngles, lAngle)
        pushAngle(rightAngles, rAngle)

        val smoothL = if (leftAngles.isEmpty()) Float.NaN else leftAngles.average().toFloat()
        val smoothR = if (rightAngles.isEmpty()) Float.NaN else rightAngles.average().toFloat()

        // Driving knee towards chest in plank: knee angle bends sharply (< 105f) while opposite leg extends (> 130f)
        val leftDriven = !smoothL.isNaN() && smoothL < 105f && (smoothR.isNaN() || smoothR > 130f)
        val rightDriven = !smoothR.isNaN() && smoothR < 105f && (smoothL.isNaN() || smoothL > 130f)
        val bothExtended = (!smoothL.isNaN() && smoothL > 140f) && (!smoothR.isNaN() && smoothR > 140f)

        val now = ts
        if (leftDriven && mountainClimberActiveLeg != "left") {
            if (now - lastRepTime >= minRepIntervalMs) {
                incrementReps(now, LevelSystem.xpPerMountainClimber(xpRates))
                mountainClimberActiveLeg = "left"
                stage = "holding"
            }
        } else if (rightDriven && mountainClimberActiveLeg != "right") {
            if (now - lastRepTime >= minRepIntervalMs) {
                incrementReps(now, LevelSystem.xpPerMountainClimber(xpRates))
                mountainClimberActiveLeg = "right"
                stage = "holding"
            }
        } else if (bothExtended) {
            mountainClimberActiveLeg = "none"
            stage = "ready"
        }

        emitFeedback(smoothL, smoothR, ts)
    }

    // ----------------------- RUSSIAN TWISTS -----------------------
    private fun processRussianTwists(lm: List<FloatArray>, ts: Long) {
        val lShVis = lm[LEFT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val rShVis = lm[RIGHT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        if ((!lShVis && !rShVis) || (!lHipVis && !rHipVis)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val shY = listOf(if (lShVis) lm[LEFT_SHOULDER][1] else Float.NaN, if (rShVis) lm[RIGHT_SHOULDER][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()
        val hipY = listOf(if (lHipVis) lm[LEFT_HIP][1] else Float.NaN, if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        // Seated V-sit posture check: hips are lower than shoulders (hipY > shY + 0.08f)
        val isSeated = (hipY - shY) > 0.08f
        if (!isSeated) {
            stage = "ready"
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val lWrVis = lm[LEFT_WRIST][2] > VISIBILITY_THRESHOLD
        val rWrVis = lm[RIGHT_WRIST][2] > VISIBILITY_THRESHOLD
        if (!lWrVis && !rWrVis) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val midHipX = listOf(if (lHipVis) lm[LEFT_HIP][0] else Float.NaN, if (rHipVis) lm[RIGHT_HIP][0] else Float.NaN).filter { !it.isNaN() }.average().toFloat()
        val wristX = listOf(if (lWrVis) lm[LEFT_WRIST][0] else Float.NaN, if (rWrVis) lm[RIGHT_WRIST][0] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        val deltaX = wristX - midHipX
        val threshold = 0.06f

        val now = ts
        if (deltaX < -threshold) {
            // Hands are on the left hip side
            if (russianTwistSide == "right") {
                if (now - lastRepTime >= minRepIntervalMs) {
                    incrementReps(now, LevelSystem.xpPerRussianTwist(xpRates))
                }
            }
            russianTwistSide = "left"
            stage = "down"
        } else if (deltaX > threshold) {
            // Hands are on the right hip side
            if (russianTwistSide == "left") {
                if (now - lastRepTime >= minRepIntervalMs) {
                    incrementReps(now, LevelSystem.xpPerRussianTwist(xpRates))
                }
            }
            russianTwistSide = "right"
            stage = "down"
        } else {
            stage = "ready"
        }

        emitFeedback(abs(deltaX) * 500f, Float.NaN, ts)
    }

    // ----------------------- CALF RAISES -----------------------
    private fun processCalfRaises(lm: List<FloatArray>, ts: Long) {
        val lShVis = lm[LEFT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val rShVis = lm[RIGHT_SHOULDER][2] > VISIBILITY_THRESHOLD
        val lHipVis = lm[LEFT_HIP][2] > VISIBILITY_THRESHOLD
        val rHipVis = lm[RIGHT_HIP][2] > VISIBILITY_THRESHOLD
        if ((!lShVis && !rShVis) || (!lHipVis && !rHipVis)) {
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        val shY = listOf(if (lShVis) lm[LEFT_SHOULDER][1] else Float.NaN, if (rShVis) lm[RIGHT_SHOULDER][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()
        val hipY = listOf(if (lHipVis) lm[LEFT_HIP][1] else Float.NaN, if (rHipVis) lm[RIGHT_HIP][1] else Float.NaN).filter { !it.isNaN() }.average().toFloat()

        // Posture: standing upright
        val isUpright = (hipY - shY) > 0.15f
        if (!isUpright) {
            calfRaiseBaselineShoulderY = Float.NaN
            emitFeedback(Float.NaN, Float.NaN, ts); return
        }

        // Knees MUST remain extended (knee angle > 155f). Squatting or knee bobbing resets!
        val lKneeVis = visible(lm, LEFT_HIP, LEFT_KNEE, LEFT_ANKLE)
        val rKneeVis = visible(lm, RIGHT_HIP, RIGHT_KNEE, RIGHT_ANKLE)
        val lKneeAngle = if (lKneeVis) calculateAngle(lm[LEFT_HIP], lm[LEFT_KNEE], lm[LEFT_ANKLE]) else Float.NaN
        val rKneeAngle = if (rKneeVis) calculateAngle(lm[RIGHT_HIP], lm[RIGHT_KNEE], lm[RIGHT_ANKLE]) else Float.NaN
        val avgKneeAngle = listOf(lKneeAngle, rKneeAngle).filter { !it.isNaN() }.average().toFloat()

        if (!avgKneeAngle.isNaN() && avgKneeAngle < calfRaiseMinKneeAngle) {
            stage = "ready"
            calfRaiseBaselineShoulderY = Float.NaN
            emitFeedback(avgKneeAngle, Float.NaN, ts); return
        }

        if (calfRaiseBaselineShoulderY.isNaN()) {
            calfRaiseBaselineShoulderY = shY
            stage = "down"
        } else {
            // Anchor to lowest position (largest Y in screen coordinates)
            if (shY > calfRaiseBaselineShoulderY) {
                calfRaiseBaselineShoulderY = calfRaiseBaselineShoulderY * 0.9f + shY * 0.1f
            }
        }

        val rise = calfRaiseBaselineShoulderY - shY // positive when elevated on toes
        val isRaised = rise >= minCalfRiseDelta
        val isLowered = rise < (minCalfRiseDelta * 0.4f)

        val now = ts
        if (isRaised && stage != "up") {
            stage = "up"
        }
        if (isLowered && stage == "up") {
            if (now - lastRepTime >= minRepIntervalMs) {
                incrementReps(now, LevelSystem.xpPerCalfRaise(xpRates))
            }
            stage = "down"
            calfRaiseBaselineShoulderY = shY
        }

        emitFeedback(avgKneeAngle, rise * 1000f, ts)
    }

    private fun visible(lm: List<FloatArray>, a: Int, b: Int, c: Int): Boolean =
        lm[a][2] > VISIBILITY_THRESHOLD && lm[b][2] > VISIBILITY_THRESHOLD && lm[c][2] > VISIBILITY_THRESHOLD

    private fun incrementReps(now: Long, baseXpGain: Float) {
        val xpGain = baseXpGain * xpMultiplier
        reps += 1; lastRepTime = now; totalXp += xpGain
        listener?.onRepLogged(reps, now, xpGain)
        listener?.onRepCountUpdated(reps)
        listener?.onXpUpdated(totalXp)
    }

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
            ExerciseType.PULLUP -> (((pullupDownAngle - avg) / (pullupDownAngle - pullupUpAngle).coerceAtLeast(1f) *100f).coerceIn(0f,100f)).toInt()
            ExerciseType.LATERAL_RAISES -> (((avg - lateralRaiseDownAngle) / (lateralRaiseUpAngle - lateralRaiseDownAngle).coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)).toInt()
            ExerciseType.GLUTE_BRIDGES -> (((avg - gluteBridgeDownAngle) / (gluteBridgeUpAngle - gluteBridgeDownAngle).coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)).toInt()
            ExerciseType.CRUNCHES -> (((crunchDownAngle - avg) / (crunchDownAngle - crunchUpAngle).coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)).toInt()
            ExerciseType.TRICEP_DIPS -> (((tricepDipUpAngle - avg) / (tricepDipUpAngle - tricepDipDownAngle).coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)).toInt()
            ExerciseType.HIGH_KNEES -> if (highKneeActiveLeg != "none") 100 else 0
            ExerciseType.LEG_RAISES -> (((legRaiseDownAngle - avg) / (legRaiseDownAngle - legRaiseUpAngle).coerceAtLeast(1f) * 100f).coerceIn(0f, 100f)).toInt()
            ExerciseType.PLANK -> ((1f - (abs(180f - avg) / 25f).coerceIn(0f, 1f)) * 100f).toInt()
            ExerciseType.BURPEES -> if (stage == "down") 100 else 0
            ExerciseType.MOUNTAIN_CLIMBERS -> if (mountainClimberActiveLeg != "none") 100 else 0
            ExerciseType.RUSSIAN_TWISTS -> if (russianTwistSide != "center") 100 else 0
            ExerciseType.CALF_RAISES -> if (stage == "up") 100 else 0
        }
        val amplitude = (cycleMaxAngle - cycleMinAngle).coerceAtLeast(0f)
        val validDepth = when(exercise) {
            ExerciseType.PUSHUP -> avg < downAngleThreshold
            ExerciseType.SQUAT, ExerciseType.LUNGES -> avg < squatDownKneeAngle
            ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT -> avg < curlDownAngle
            ExerciseType.SHOULDER_PRESS -> avg < shoulderPressDownAngle
            ExerciseType.JUMPING_JACKS -> avg > 150f
            ExerciseType.PULLUP -> avg < pullupUpAngle
            ExerciseType.LATERAL_RAISES -> avg > lateralRaiseUpAngle
            ExerciseType.GLUTE_BRIDGES -> avg > gluteBridgeUpAngle
            ExerciseType.CRUNCHES -> avg < crunchUpAngle
            ExerciseType.TRICEP_DIPS -> avg < tricepDipDownAngle
            ExerciseType.HIGH_KNEES -> highKneeActiveLeg != "none"
            ExerciseType.LEG_RAISES -> avg < legRaiseUpAngle
            ExerciseType.PLANK -> abs(180f - avg) <= plankThresholdDeg
            ExerciseType.BURPEES -> stage == "down"
            ExerciseType.MOUNTAIN_CLIMBERS -> mountainClimberActiveLeg != "none"
            ExerciseType.RUSSIAN_TWISTS -> russianTwistSide != "center"
            ExerciseType.CALF_RAISES -> stage == "up"
        }
        val repImminent = when(exercise) {
            ExerciseType.CALF_RAISES -> stage == "up"
            ExerciseType.BURPEES -> stage == "down"
            ExerciseType.MOUNTAIN_CLIMBERS -> mountainClimberActiveLeg != "none"
            ExerciseType.RUSSIAN_TWISTS -> russianTwistSide != "center"
            else -> (stage == "down" || stage == "holding")
        }
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
                ExerciseType.PULLUP -> amplitude >= 30f
                ExerciseType.LATERAL_RAISES, ExerciseType.GLUTE_BRIDGES, ExerciseType.CRUNCHES, ExerciseType.TRICEP_DIPS, ExerciseType.LEG_RAISES -> amplitude >= 20f
                ExerciseType.PLANK -> abs(180f - avg) <= plankThresholdDeg
                ExerciseType.BURPEES, ExerciseType.MOUNTAIN_CLIMBERS, ExerciseType.RUSSIAN_TWISTS, ExerciseType.CALF_RAISES -> true
                ExerciseType.JUMPING_JACKS, ExerciseType.HIGH_KNEES -> true
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

    fun reset() {
        reps = 0; totalXp = 0f; stage = "up"; lastRepTime = 0L
        leftAngles.clear(); rightAngles.clear(); cycleMinAngle = 180f; cycleMaxAngle = 0f
        bottomReachTime = 0L; topReachTime = 0L; lastTimestamp = 0L
        lastPlankSecondLoggedMs = 0L; plankActiveHold = false; highKneeActiveLeg = "none"
        pullupStartWristY = 0f; pullupStartShoulderY = 0f; pullupStartHipY = 0f; pullupHasHips = false
        pushupStartWristY = 0f; pushupStartShoulderY = 0f
        squatStartHipY = 0f; squatMaxHipY = 0f
        lungeStartHipY = 0f; lungeMaxHipY = 0f
        tricepDipStartWristY = 0f; tricepDipStartShoulderY = 0f
        gluteBridgeStartHipY = 0f
        burpeePhase = "standing"; burpeeStandingShoulderY = 0f; burpeeStandingHipY = 0f
        mountainClimberActiveLeg = "none"
        russianTwistSide = "center"
        calfRaiseBaselineShoulderY = Float.NaN
        lastFeedback = Feedback(0, "up", 180f, 180f, 180f, 0, 0, false, false, 0f, true, exercise)
        resetCallbacks.forEach { it.invoke() }
        listener?.onRepCountUpdated(reps); listener?.onXpUpdated(totalXp)
    }
}
