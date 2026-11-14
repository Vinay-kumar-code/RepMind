package com.example.workouttracker

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class WorkoutEngineTest {

    private lateinit var engine: WorkoutEngine
    private var repsLogged = 0
    private var lastXpEarned = 0f
    private var simulatedTime = 1000L

    @Before
    fun setUp() {
        repsLogged = 0
        lastXpEarned = 0f
        simulatedTime = 1000L
        engine = WorkoutEngine(object : WorkoutEngine.Listener {
            override fun onRepCountUpdated(reps: Int) {
                repsLogged = reps
            }
            override fun onXpUpdated(totalXp: Float) {}
            override fun onRepLogged(repIndex: Int, timestampMs: Long, xpEarned: Float) {
                lastXpEarned = xpEarned
            }
        })
    }

    /**
     * Helper to create 33 MediaPipe landmarks initialized to zeroes.
     */
    private fun createEmptyLandmarks(): MutableList<FloatArray> {
        return MutableList(33) { floatArrayOf(0.5f, 0.5f, 0.9f) }
    }

    /**
     * Feed landmarks for multiple frames to flush the 5-frame moving average buffer and advance time.
     */
    private fun feed(lm: List<FloatArray>, frames: Int = 5, stepMs: Long = 100L) {
        repeat(frames) {
            simulatedTime += stepMs
            engine.updateFromLandmarks(lm, simulatedTime)
        }
    }

    // =========================================================================
    // 1. PULLUP TESTS (False positive rejection & True rep acceptance)
    // =========================================================================

    @Test
    fun testPullup_FalseRep_HandsMovingDownWhileBodyStatic_IsRejected() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.PULLUP)
        val lm = createEmptyLandmarks()

        // Standing upright on ground:
        // Wrists overhead: Y = 0.10
        // Shoulders: Y = 0.35
        // Hips: Y = 0.60
        // Elbows straight (180 deg)
        lm[11] = floatArrayOf(0.45f, 0.35f, 0.95f) // Left Shoulder
        lm[12] = floatArrayOf(0.55f, 0.35f, 0.95f) // Right Shoulder
        lm[13] = floatArrayOf(0.45f, 0.22f, 0.95f) // Left Elbow
        lm[14] = floatArrayOf(0.55f, 0.22f, 0.95f) // Right Elbow
        lm[15] = floatArrayOf(0.45f, 0.10f, 0.95f) // Left Wrist
        lm[16] = floatArrayOf(0.55f, 0.10f, 0.95f) // Right Wrist
        lm[23] = floatArrayOf(0.45f, 0.60f, 0.95f) // Left Hip
        lm[24] = floatArrayOf(0.55f, 0.60f, 0.95f) // Right Hip

        // Hanging position (wrists overhead, straight arms)
        feed(lm, frames = 5)
        assertEquals("Hanging should set down stage", "down", engine.getLastFeedback().stage)

        // Cheat motion: User pulls hands DOWN towards chest (wrists go from 0.10 to 0.40)
        // Shoulders and hips stay static at 0.35 and 0.60!
        lm[13] = floatArrayOf(0.35f, 0.35f, 0.95f) // Elbows bent (< 90 deg)
        lm[14] = floatArrayOf(0.65f, 0.35f, 0.95f)
        lm[15] = floatArrayOf(0.45f, 0.40f, 0.95f) // Wrists moved DOWN to chest!
        lm[16] = floatArrayOf(0.55f, 0.40f, 0.95f)
        // Shoulders static: 0.35
        // Hips static: 0.60

        // Hands moved down, shoulders/hips static
        feed(lm, frames = 5, stepMs = 200L)

        // Must NOT count rep!
        assertEquals("False pullup where hands move down must be rejected", 0, engine.getReps())
    }

    @Test
    fun testPullup_TrueRep_WristsAnchoredAndBodyPullsUp_IsAccepted() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.PULLUP)
        val lm = createEmptyLandmarks()

        // Hanging on bar:
        // Wrists anchored overhead on bar: Y = 0.10
        // Shoulders: Y = 0.35
        // Hips: Y = 0.60
        // Arms extended (straight: shoulder(0.35) -> elbow(0.22) -> wrist(0.10))
        lm[11] = floatArrayOf(0.45f, 0.35f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.35f, 0.95f)
        lm[13] = floatArrayOf(0.45f, 0.22f, 0.95f)
        lm[14] = floatArrayOf(0.55f, 0.22f, 0.95f)
        lm[15] = floatArrayOf(0.45f, 0.10f, 0.95f)
        lm[16] = floatArrayOf(0.55f, 0.10f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.60f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.60f, 0.95f)

        // Frame: Hanging
        feed(lm, frames = 5)
        assertEquals("down", engine.getLastFeedback().stage)

        // True pullup:
        // Wrists remain anchored to the bar: Y stays ~0.10
        // Shoulders pull up towards wrists: Y moves from 0.35 to 0.16 (rise = 0.19!)
        // Hips pull up towards wrists: Y moves from 0.60 to 0.40 (rise = 0.20!)
        // Elbow angle flexes < 90 deg:
        // Shoulder (0.45, 0.16), Elbow (0.35, 0.16), Wrist (0.45, 0.10)
        lm[11] = floatArrayOf(0.45f, 0.16f, 0.95f) // Shoulders pulled UP
        lm[12] = floatArrayOf(0.55f, 0.16f, 0.95f)
        lm[13] = floatArrayOf(0.35f, 0.16f, 0.95f) // Elbows flexed < 90 deg
        lm[14] = floatArrayOf(0.65f, 0.16f, 0.95f)
        lm[15] = floatArrayOf(0.45f, 0.10f, 0.95f) // Wrists anchored on bar!
        lm[16] = floatArrayOf(0.55f, 0.10f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.40f, 0.95f) // Hips pulled UP
        lm[24] = floatArrayOf(0.55f, 0.40f, 0.95f)

        // Feed 5 frames at top position (advances total time > 800ms min interval)
        feed(lm, frames = 5, stepMs = 200L)

        // Must count 1 rep!
        assertEquals("True pullup with anchored wrists and rising body must be counted", 1, engine.getReps())
    }

    // =========================================================================
    // 2. SQUAT TESTS
    // =========================================================================

    @Test
    fun testSquat_FalseRep_SittingAndSwingingLegs_IsRejected() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.SQUAT)
        val lm = createEmptyLandmarks()

        // Sitting on chair:
        // Shoulders Y=0.30, Hips Y=0.55 (static!)
        // Knees at Y=0.55
        lm[11] = floatArrayOf(0.45f, 0.30f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.30f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.55f, 0.95f) // Hips fixed at 0.55
        lm[24] = floatArrayOf(0.55f, 0.55f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.55f, 0.95f) // Knees
        lm[26] = floatArrayOf(0.55f, 0.55f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.85f, 0.95f) // Ankles down (knee bent ~90 deg)
        lm[28] = floatArrayOf(0.55f, 0.85f, 0.95f)

        feed(lm, frames = 5)

        // User kicks legs out straight: knee angle becomes 180, but hips did not drop!
        lm[27] = floatArrayOf(0.45f, 0.55f, 0.95f) // Ankles lifted
        lm[28] = floatArrayOf(0.55f, 0.55f, 0.95f)
        feed(lm, frames = 5, stepMs = 200L)

        assertEquals("Sitting and swinging legs must not count as squat", 0, engine.getReps())
    }

    @Test
    fun testSquat_TrueRep_HipsDropAndRise_IsAccepted() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.SQUAT)
        val lm = createEmptyLandmarks()

        // Standing straight:
        // Shoulders: 0.20, Hips: 0.45, Knees: 0.70, Ankles: 0.95 (knee angle 180 deg)
        lm[11] = floatArrayOf(0.45f, 0.20f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.20f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.45f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.45f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.70f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.70f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.95f, 0.95f)
        lm[28] = floatArrayOf(0.55f, 0.95f, 0.95f)

        feed(lm, frames = 5)
        assertEquals("up", engine.getLastFeedback().stage)

        // Squat down:
        // Hips drop from 0.45 to 0.65 (drop = 0.20 >= 0.035f)
        // Knee bends to 90 deg: Hip(0.45, 0.65), Knee(0.30, 0.65), Ankle(0.30, 0.90)
        // Vector knee-to-hip = (0.15, 0), knee-to-ankle = (0, 0.25) -> exactly 90 deg (< 100 deg)!
        lm[23] = floatArrayOf(0.45f, 0.65f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.65f, 0.95f)
        lm[25] = floatArrayOf(0.30f, 0.65f, 0.95f)
        lm[26] = floatArrayOf(0.70f, 0.65f, 0.95f)
        lm[27] = floatArrayOf(0.30f, 0.90f, 0.95f)
        lm[28] = floatArrayOf(0.70f, 0.90f, 0.95f)

        feed(lm, frames = 5, stepMs = 100L)
        assertEquals("down", engine.getLastFeedback().stage)

        // Stand back up to 180 deg:
        lm[23] = floatArrayOf(0.45f, 0.45f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.45f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.70f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.70f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.95f, 0.95f)
        lm[28] = floatArrayOf(0.55f, 0.95f, 0.95f)

        feed(lm, frames = 5, stepMs = 200L)

        assertEquals("True squat must count 1 rep", 1, engine.getReps())
    }

    // =========================================================================
    // 3. PUSHUP TESTS
    // =========================================================================

    @Test
    fun testPushup_FalseRep_StandingAirPushups_IsRejected() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.PUSHUP)
        val lm = createEmptyLandmarks()

        // Standing upright vertically:
        // Shoulders Y=0.20, Hips Y=0.50, Ankles Y=0.85
        lm[11] = floatArrayOf(0.45f, 0.20f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.20f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.50f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.50f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.85f, 0.95f)
        lm[28] = floatArrayOf(0.55f, 0.85f, 0.95f)

        // Elbows bent in air (< 90 deg)
        lm[13] = floatArrayOf(0.35f, 0.20f, 0.95f)
        lm[14] = floatArrayOf(0.65f, 0.20f, 0.95f)
        lm[15] = floatArrayOf(0.45f, 0.20f, 0.95f)
        lm[16] = floatArrayOf(0.55f, 0.20f, 0.95f)

        feed(lm, frames = 5)

        // Arms pushed forward in air
        lm[13] = floatArrayOf(0.45f, 0.20f, 0.95f)
        lm[14] = floatArrayOf(0.55f, 0.20f, 0.95f)
        lm[15] = floatArrayOf(0.45f, 0.20f, 0.95f)
        lm[16] = floatArrayOf(0.55f, 0.20f, 0.95f)

        feed(lm, frames = 5, stepMs = 200L)
        assertEquals("Standing air pushups must be rejected", 0, engine.getReps())
    }

    // =========================================================================
    // 4. NEW WORKOUT: BURPEES
    // =========================================================================

    @Test
    fun testBurpees_FullTransitionCycle_CountsRep() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.BURPEES)
        val lm = createEmptyLandmarks()

        // 1. Standing upright:
        // Shoulders Y=0.25, Hips Y=0.55, Knees Y=0.75, Ankles Y=0.95 (knees straight > 150 deg)
        lm[11] = floatArrayOf(0.45f, 0.25f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.25f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.55f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.55f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.75f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.75f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.95f, 0.95f)
        lm[28] = floatArrayOf(0.55f, 0.95f, 0.95f)

        feed(lm, frames = 5)
        assertEquals("up", engine.getLastFeedback().stage)

        // 2. Drop into floor plank:
        // Body horizontal: Shoulders Y=0.70, Hips Y=0.72 (diff < 0.25)
        lm[11] = floatArrayOf(0.25f, 0.70f, 0.95f)
        lm[12] = floatArrayOf(0.25f, 0.70f, 0.95f)
        lm[23] = floatArrayOf(0.55f, 0.72f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.72f, 0.95f)
        lm[27] = floatArrayOf(0.85f, 0.75f, 0.95f)
        lm[28] = floatArrayOf(0.85f, 0.75f, 0.95f)

        feed(lm, frames = 5, stepMs = 200L)
        assertEquals("down", engine.getLastFeedback().stage)

        // 3. Jump back up to standing:
        lm[11] = floatArrayOf(0.45f, 0.25f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.25f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.55f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.55f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.75f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.75f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.95f, 0.95f)
        lm[28] = floatArrayOf(0.55f, 0.95f, 0.95f)

        feed(lm, frames = 5, stepMs = 300L) // Total interval > 1200ms
        assertEquals("Completed burpee must count 1 rep", 1, engine.getReps())
    }

    // =========================================================================
    // 5. NEW WORKOUT: MOUNTAIN CLIMBERS
    // =========================================================================

    @Test
    fun testMountainClimbers_AlternatingKneesInPlank_CountsReps() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.MOUNTAIN_CLIMBERS)
        val lm = createEmptyLandmarks()

        // Plank position (horizontal body):
        // Shoulders Y=0.70, Hips Y=0.72
        lm[11] = floatArrayOf(0.25f, 0.70f, 0.95f)
        lm[12] = floatArrayOf(0.25f, 0.70f, 0.95f)
        lm[23] = floatArrayOf(0.55f, 0.72f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.72f, 0.95f)

        // Both legs extended initially (180 deg):
        lm[25] = floatArrayOf(0.70f, 0.72f, 0.95f)
        lm[26] = floatArrayOf(0.70f, 0.72f, 0.95f)
        lm[27] = floatArrayOf(0.85f, 0.72f, 0.95f)
        lm[28] = floatArrayOf(0.85f, 0.72f, 0.95f)

        feed(lm, frames = 5)

        // Left knee drives forward under chest:
        // Left knee bent sharply to 90 deg (< 105 deg):
        // Hip(0.55, 0.72), Knee(0.42, 0.72), Ankle(0.42, 0.85) -> 90 deg
        lm[25] = floatArrayOf(0.42f, 0.72f, 0.95f)
        lm[27] = floatArrayOf(0.42f, 0.85f, 0.95f)
        feed(lm, frames = 5, stepMs = 100L)
        assertEquals("Left knee drive must count 1 rep", 1, engine.getReps())

        // Reset to extended
        lm[25] = floatArrayOf(0.70f, 0.72f, 0.95f)
        lm[27] = floatArrayOf(0.85f, 0.72f, 0.95f)
        feed(lm, frames = 5, stepMs = 100L)

        // Right knee drives forward
        lm[26] = floatArrayOf(0.42f, 0.72f, 0.95f)
        lm[28] = floatArrayOf(0.42f, 0.85f, 0.95f)
        feed(lm, frames = 5, stepMs = 100L)
        assertEquals("Right knee drive must count 2nd rep", 2, engine.getReps())
    }

    // =========================================================================
    // 6. NEW WORKOUT: RUSSIAN TWISTS
    // =========================================================================

    @Test
    fun testRussianTwists_SeatedTwistSideToSide_CountsReps() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.RUSSIAN_TWISTS)
        val lm = createEmptyLandmarks()

        // Seated V-sit:
        // Shoulders: Y=0.35, Hips: Y=0.55 (hipY > shY + 0.08)
        // Mid Hip X = 0.50
        lm[11] = floatArrayOf(0.45f, 0.35f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.35f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.55f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.55f, 0.95f)

        // Wrists on Left side: X = 0.38 (< 0.50 - 0.06)
        lm[15] = floatArrayOf(0.38f, 0.50f, 0.95f)
        lm[16] = floatArrayOf(0.38f, 0.50f, 0.95f)

        feed(lm, frames = 5, stepMs = 100L)
        assertEquals("Initial left side sets down stage", "down", engine.getLastFeedback().stage)

        // Wrists twist across to Right side: X = 0.62 (> 0.50 + 0.06)
        lm[15] = floatArrayOf(0.62f, 0.50f, 0.95f)
        lm[16] = floatArrayOf(0.62f, 0.50f, 0.95f)

        feed(lm, frames = 5, stepMs = 100L) // Total time > 400ms
        assertEquals("Twisting from left to right counts 1 rep", 1, engine.getReps())

        // Twist back to Left side: X = 0.38
        lm[15] = floatArrayOf(0.38f, 0.50f, 0.95f)
        lm[16] = floatArrayOf(0.38f, 0.50f, 0.95f)

        feed(lm, frames = 5, stepMs = 100L) // Total time > 400ms
        assertEquals("Twisting from right to left counts 2nd rep", 2, engine.getReps())
    }

    // =========================================================================
    // 7. NEW WORKOUT: CALF RAISES
    // =========================================================================

    @Test
    fun testCalfRaises_ElevateHeelsWithStraightKnees_CountsRep() {
        engine.setExerciseType(WorkoutEngine.ExerciseType.CALF_RAISES)
        val lm = createEmptyLandmarks()

        // Standing upright, straight knees:
        // Shoulders Y=0.25, Hips Y=0.50, Knees Y=0.72, Ankles Y=0.95
        lm[11] = floatArrayOf(0.45f, 0.25f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.25f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.50f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.50f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.72f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.72f, 0.95f)
        lm[27] = floatArrayOf(0.45f, 0.95f, 0.95f)
        lm[28] = floatArrayOf(0.55f, 0.95f, 0.95f)

        // Baseline frame (feet flat)
        feed(lm, frames = 5, stepMs = 100L)

        // Raise up on toes: entire body rises up (Shoulders rise by 0.035 from 0.25 to 0.215)
        // Knees remain completely straight (180 deg)
        lm[11] = floatArrayOf(0.45f, 0.215f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.215f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.465f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.465f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.685f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.685f, 0.95f)

        feed(lm, frames = 5, stepMs = 100L)
        assertEquals("up", engine.getLastFeedback().stage)

        // Lower back down to floor
        lm[11] = floatArrayOf(0.45f, 0.25f, 0.95f)
        lm[12] = floatArrayOf(0.55f, 0.25f, 0.95f)
        lm[23] = floatArrayOf(0.45f, 0.50f, 0.95f)
        lm[24] = floatArrayOf(0.55f, 0.50f, 0.95f)
        lm[25] = floatArrayOf(0.45f, 0.72f, 0.95f)
        lm[26] = floatArrayOf(0.55f, 0.72f, 0.95f)

        feed(lm, frames = 5, stepMs = 150L) // Total interval > 600ms
        assertEquals("Calf raise rep must be counted", 1, engine.getReps())
    }

    // =========================================================================
    // 8. LEVEL SYSTEM XP TESTS
    // =========================================================================

    @Test
    fun testLevelSystem_NewExercisesXpCalculation() {
        val rates = XpRates()
        assertEquals(0.25f, LevelSystem.xpPerBurpee(rates), 0.001f)
        assertEquals(0.04f, LevelSystem.xpPerMountainClimber(rates), 0.001f)
        assertEquals(0.05f, LevelSystem.xpPerRussianTwist(rates), 0.001f)
        assertEquals(0.05f, LevelSystem.xpPerCalfRaise(rates), 0.001f)

        // Test xpForStandardExercise
        val burpeeXp = LevelSystem.xpForStandardExercise("BURPEES", 10, rates)
        assertEquals(10 * 0.25f * 0.75f, burpeeXp, 0.001f)

        val climberXp = LevelSystem.xpForStandardExercise("MOUNTAIN_CLIMBERS", 20, rates)
        assertEquals(20 * 0.04f * 0.75f, climberXp, 0.001f)

        val twistXp = LevelSystem.xpForStandardExercise("RUSSIAN_TWISTS", 20, rates)
        assertEquals(20 * 0.05f * 0.75f, twistXp, 0.001f)

        val calfXp = LevelSystem.xpForStandardExercise("CALF_RAISES", 20, rates)
        assertEquals(20 * 0.05f * 0.75f, calfXp, 0.001f)
    }
}
