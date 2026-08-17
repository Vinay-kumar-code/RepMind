package com.example.workouttracker

/**
 * Level / Rank progression system.
 * Linear XP curve: 50 XP per level up to MAX_LEVEL.
 * Provides daily goal scaling and simple rank/title flavor text.
 */
object LevelSystem {
    data class LevelInfo(
        val level: Int,
        val currentXp: Float,
        val xpForCurrentLevel: Float,
        val xpForNextLevel: Float,
        val xpIntoLevel: Float,
        val xpNeededToLevel: Float,
        val progressPercent: Int,
        val pushGoal: Int,
        val squatGoal: Int,
        val rank: String,
        val title: String
    )

    private const val XP_PER_LEVEL = 50
    private const val MAX_LEVEL = 100

    private val rankBands = listOf(
        1..5 to "Novice",
        6..15 to "Apprentice",
        16..30 to "Adept",
        31..50 to "Elite",
        51..75 to "Master",
        76..90 to "Grandmaster",
        91..100 to "Legend"
    )

    private fun clampLevel(level: Int) = level.coerceIn(1, MAX_LEVEL)

    fun levelFromXp(totalXp: Float): LevelInfo {
        val rawLevel = (totalXp / XP_PER_LEVEL).toInt() + 1
        val level = clampLevel(rawLevel)
        val xpForCurrent = (level - 1) * XP_PER_LEVEL.toFloat()
        val xpForNext = level * XP_PER_LEVEL.toFloat()
        val into = totalXp - xpForCurrent
        val need = (xpForNext - xpForCurrent).coerceAtLeast(1f)
        val pct = ((into / need) * 100f).toInt().coerceIn(0, 100)

        // Daily goals scale linearly between (10,10) and (100,100)
        val fraction = (level - 1).toFloat() / (MAX_LEVEL - 1).coerceAtLeast(1)
        val pushGoal = (10 + fraction * (100 - 10)).toInt()
        val squatGoal = (10 + fraction * (100 - 10)).toInt()

        val rank = rankBands.firstOrNull { level in it.first }?.second ?: "Adventurer"
        val title = when {
            level >= 90 -> "Transcendent"
            level >= 75 -> "Mythic Athlete"
            level >= 50 -> "Champion"
            level >= 30 -> "Advanced"
            level >= 15 -> "Intermediate"
            level >= 6 -> "Rising"
            else -> "Beginner"
        }

        return LevelInfo(
            level = level,
            currentXp = totalXp,
            xpForCurrentLevel = xpForCurrent,
            xpForNextLevel = xpForNext,
            xpIntoLevel = into,
            xpNeededToLevel = xpForNext - totalXp,
            progressPercent = pct,
            pushGoal = pushGoal,
            squatGoal = squatGoal,
            rank = rank,
            title = title
        )
    }

    fun xpPerPushup(rates: XpRates? = null) = rates?.pushup ?: 0.1f // 10 pushups = 1 XP
    fun xpPerSquat(rates: XpRates? = null) = rates?.squat ?: 0.1f   // 10 squats = 1 XP
    fun xpPerBicepCurl(rates: XpRates? = null) = rates?.bicepCurl ?: (1f / 30f) // 30 curls (one arm) = 1 XP
    fun xpPerLunge(rates: XpRates? = null) = rates?.lunge ?: 0.15f // Harder than squat
    fun xpPerShoulderPress(rates: XpRates? = null) = rates?.shoulderPress ?: 0.1f
    fun xpPerJumpingJack(rates: XpRates? = null) = rates?.jumpingJack ?: 0.05f // 20 jacks = 1 XP
    fun xpPerPullup(rates: XpRates? = null) = rates?.pullup ?: 0.2f // 5 pullups = 1 XP

    fun xpForStandardExercise(exercise: String, reps: Int, rates: XpRates? = null): Float {
        val xpPerRep = when (exercise.uppercase()) {
            "PUSHUPS", "PUSHUP" -> xpPerPushup(rates)
            "SQUATS", "SQUAT" -> xpPerSquat(rates)
            "BICEP CURL - LEFT", "BICEP CURL - RIGHT", "BICEP_LEFT", "BICEP_RIGHT" -> xpPerBicepCurl(rates)
            "PULLUPS", "PULLUP" -> xpPerPullup(rates)
            "SHOULDER_PRESS", "SHOULDER PRESS" -> xpPerShoulderPress(rates)
            "JUMPING_JACKS", "JUMPING JACKS" -> xpPerJumpingJack(rates)
            "LUNGES", "LUNGE" -> xpPerLunge(rates)
            else -> rates?.manualRep ?: 0.1f // Default fallback
        }
        val mult = rates?.multiplier ?: 1.0f
        return reps * xpPerRep * 0.75f * mult
    }

    fun xpForManualReps(reps: Int, rates: XpRates? = null): Float {
        val rate = rates?.manualRep ?: 0.075f
        val mult = rates?.multiplier ?: 1.0f
        return reps * rate * mult
    }

    fun xpForManualDuration(minutes: Int, rates: XpRates? = null): Float {
        val rate = rates?.manualDurationPerMin ?: 0.15f
        val mult = rates?.multiplier ?: 1.0f
        return minutes * rate * mult
    }
}
