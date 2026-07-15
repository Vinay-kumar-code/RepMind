package com.example.workouttracker

import com.example.workouttracker.db.DailyProgressEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * ProgressManager keeps track of today's goals, streak and live progress.
 * It persists per-day totals in Room (daily_progress table).
 */
class ProgressManager(private val repo: SessionRepository, private val scope: CoroutineScope) {
    data class DailyGoals(val push: Int, val squat: Int, val bicep: Int)
    data class DailyState(
        val date: String,
        val pushups: Int,
        val squats: Int,
        val bicepLeft: Int,
        val bicepRight: Int,
        val goalsMet: Boolean,
        val goals: DailyGoals,
        val streak: Int,
        val level: Int,
        val totalXp: Float
    )

    private val _dailyState = MutableStateFlow<DailyState?>(null)
    val dailyState: StateFlow<DailyState?> = _dailyState

    private var cachedGoals: DailyGoals = DailyGoals(10,10,60)

    private fun today(): String = LocalDate.now().toString()

    fun load(initialLevelInfo: LevelSystem.LevelInfo, totalXp: Float, profile: com.example.workouttracker.db.UserProfileEntity?) {
        updateCachedGoals(initialLevelInfo, profile)
        scope.launch(Dispatchers.IO) { loadOrCreate(initialLevelInfo.level, totalXp) }
    }

    private fun updateCachedGoals(levelInfo: LevelSystem.LevelInfo, profile: com.example.workouttracker.db.UserProfileEntity?) {
        if (profile?.useCustomGoals == true) {
            cachedGoals = DailyGoals(profile.customPushGoal, profile.customSquatGoal, profile.customBicepGoal)
        } else {
            cachedGoals = DailyGoals(levelInfo.pushGoal, levelInfo.squatGoal, 60)
        }
    }

    private suspend fun loadOrCreate(level: Int, totalXp: Float) {
        val date = today()
        val existing = repo.getDaily(date)
        val entity = existing ?: DailyProgressEntity(
            date = date,
            pushups = 0,
            squats = 0,
            plankSeconds = 0,
            bicepLeft = 0,
            bicepRight = 0,
            goalsMet = false,
            lastUpdatedIso = java.time.Instant.now().toString()
        ).also { repo.upsertDaily(it) }
        val streak = computeStreak()
        publish(entity, level, totalXp, streak)
    }

    private suspend fun computeStreak(): Int {
        val recents = repo.getRecentDaily(3650).sortedByDescending { it.date }
        var streak = 0
        var expected = LocalDate.now()
        var checkedToday = false

        for (dp in recents) {
            val d = LocalDate.parse(dp.date)
            if (!checkedToday && d == expected) {
                checkedToday = true
                if (dp.goalsMet) {
                    streak++
                    expected = expected.minusDays(1)
                } else {
                    // Today not met yet, streak depends on yesterday
                    expected = expected.minusDays(1)
                }
                continue
            } else if (!checkedToday && d.isBefore(expected)) {
                checkedToday = true
                expected = expected.minusDays(1)
            }

            if (d == expected) {
                if (dp.goalsMet) {
                    streak++
                    expected = expected.minusDays(1)
                } else break
            } else if (d.isBefore(expected)) {
                break
            }
        }
        return streak
    }

    private fun publish(entity: DailyProgressEntity, level: Int, totalXp: Float, streak: Int) {
        val met = entity.goalsMet
        _dailyState.value = DailyState(
            date = entity.date,
            pushups = entity.pushups,
            squats = entity.squats,
            bicepLeft = entity.bicepLeft,
            bicepRight = entity.bicepRight,
            goalsMet = met,
            goals = cachedGoals,
            streak = streak,
            level = level,
            totalXp = totalXp
        )
    }

    fun updateGoals(levelInfo: LevelSystem.LevelInfo, totalXp: Float, profile: com.example.workouttracker.db.UserProfileEntity?) {
        updateCachedGoals(levelInfo, profile)
        scope.launch(Dispatchers.IO) {
            val date = today()
            val entity = repo.getDaily(date) ?: return@launch
            val streak = computeStreak()
            val updated = entity.copy(goalsMet = goalsMet(entity))
            publish(updated, levelInfo.level, totalXp, streak)
        }
    }

    fun recordPushups(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Float) {
        scope.launch(Dispatchers.IO) { mutateDay(pushupsDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }
    fun recordSquats(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Float) {
        scope.launch(Dispatchers.IO) { mutateDay(squatsDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }
    fun recordBicepLeft(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Float) {
        scope.launch(Dispatchers.IO) { mutateDay(bicepLeftDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }
    fun recordBicepRight(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Float) {
        scope.launch(Dispatchers.IO) { mutateDay(bicepRightDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }

    private suspend fun mutateDay(
        pushupsDelta: Int = 0,
        squatsDelta: Int = 0,
        bicepLeftDelta: Int = 0,
        bicepRightDelta: Int = 0,
        levelInfo: LevelSystem.LevelInfo,
        totalXp: Float
    ) {
        val date = today()
        val existing = repo.getDaily(date) ?: DailyProgressEntity(
            date = date,
            pushups = 0,
            squats = 0,
            plankSeconds = 0,
            bicepLeft = 0,
            bicepRight = 0,
            goalsMet = false,
            lastUpdatedIso = java.time.Instant.now().toString()
        )
        val newPush = (existing.pushups + pushupsDelta).coerceAtLeast(0)
        val newSquat = (existing.squats + squatsDelta).coerceAtLeast(0)
        val newLeft = (existing.bicepLeft + bicepLeftDelta).coerceAtLeast(0)
        val newRight = (existing.bicepRight + bicepRightDelta).coerceAtLeast(0)
        val updatedEntity = existing.copy(
            pushups = newPush,
            squats = newSquat,
            bicepLeft = newLeft,
            bicepRight = newRight,
            goalsMet = goalsMet(existing.copy(
                pushups = newPush,
                squats = newSquat,
                bicepLeft = newLeft,
                bicepRight = newRight
            )),
            lastUpdatedIso = java.time.Instant.now().toString()
        )
        repo.upsertDaily(updatedEntity)
        val streak = computeStreak()
        publish(updatedEntity, levelInfo.level, totalXp, streak)
    }

    private fun goalsMet(dp: DailyProgressEntity): Boolean =
        dp.pushups >= cachedGoals.push &&
        dp.squats >= cachedGoals.squat &&
        (dp.bicepLeft + dp.bicepRight) >= cachedGoals.bicep

    suspend fun getWeeklyProgress(): List<Float> {
        val weekly = MutableList(7) { 0f }
        val recents = repo.getRecentDaily(7)
        val today = LocalDate.now()
        val startOfWeek = today.minusDays(today.dayOfWeek.value.toLong() - 1) // Monday

        for (dp in recents) {
            val d = LocalDate.parse(dp.date)
            // Determine index (0=Monday, 6=Sunday)
            val diff = d.toEpochDay() - startOfWeek.toEpochDay()
            if (diff in 0..6) {
                val pushPct = (dp.pushups.toFloat() / cachedGoals.push.coerceAtLeast(1)).coerceIn(0f, 1f)
                val squatPct = (dp.squats.toFloat() / cachedGoals.squat.coerceAtLeast(1)).coerceIn(0f, 1f)
                val bicepPct = ((dp.bicepLeft + dp.bicepRight).toFloat() / cachedGoals.bicep.coerceAtLeast(1)).coerceIn(0f, 1f)
                weekly[diff.toInt()] = listOf(pushPct, squatPct, bicepPct).average().toFloat()
            }
        }
        return weekly
    }

    suspend fun recalculateDailyProgress() {
        val allSessions = repo.getAllSessions()
        val dailyMap = mutableMapOf<String, DailyProgressEntity>()
        
        for (session in allSessions) {
            val date = sessionDate(session.timestampIso)
            
            val dp = dailyMap[date] ?: DailyProgressEntity(
                date = date,
                pushups = 0,
                squats = 0,
                plankSeconds = 0,
                bicepLeft = 0,
                bicepRight = 0,
                goalsMet = false,
                lastUpdatedIso = java.time.Instant.now().toString()
            )
            
            val ex = session.exercise.lowercase()
            var push = dp.pushups
            var squat = dp.squats
            var bLeft = dp.bicepLeft
            var bRight = dp.bicepRight
            
            when (ex) {
                "pushup", "pushups" -> push += session.reps
                "squat", "squats" -> squat += session.reps
                "bicep_left", "bicep curl - left", "bicep curls" -> bLeft += session.reps
                "bicep_right", "bicep curl - right" -> bRight += session.reps
            }
            
            dailyMap[date] = dp.copy(
                pushups = push,
                squats = squat,
                bicepLeft = bLeft,
                bicepRight = bRight
            )
        }
        
        val sortedDates = dailyMap.keys.sorted()
        var runningXp = 0f
        
        for (d in sortedDates) {
            val dp = dailyMap[d]!!
            // Calculate what the level was BEFORE this day's XP is fully applied, or after.
            // Let's use runningXp to find the goals for this specific day.
            val pastLevelInfo = LevelSystem.levelFromXp(runningXp)
            val pastGoals = DailyGoals(pastLevelInfo.pushGoal, pastLevelInfo.squatGoal, 60)
            
            // Allow custom goals if set, we'll use current custom goals as fallback for past
            val prof = repo.getProfile()
            val effectiveGoals = if (prof?.useCustomGoals == true) {
                DailyGoals(prof.customPushGoal, prof.customSquatGoal, prof.customBicepGoal)
            } else pastGoals
            
            val met = dp.pushups >= effectiveGoals.push &&
                      dp.squats >= effectiveGoals.squat &&
                      (dp.bicepLeft + dp.bicepRight) >= effectiveGoals.bicep
                      
            val updated = dp.copy(goalsMet = met)
            repo.upsertDaily(updated)
            
            // Add today's XP to running total for next days
            runningXp += allSessions.filter { sessionDate(it.timestampIso) == d }.sumOf { it.totalXp.toDouble() }.toFloat()
        }
        
        val date = today()
        val streak = computeStreak()
        val prof = repo.getProfile()
        val totalXp = prof?.totalXp ?: 0f
        val level = LevelSystem.levelFromXp(totalXp).level
        val todayDp = repo.getDaily(date) ?: DailyProgressEntity(
            date = date,
            goalsMet = false,
            lastUpdatedIso = java.time.Instant.now().toString()
        )
        publish(todayDp, level, totalXp, streak)
    }
    private fun sessionDate(iso: String): String {
        return try {
            val instant = try { java.time.OffsetDateTime.parse(iso).toInstant() } catch (_: Exception) { java.time.Instant.parse(iso) }
            instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate().toString()
        } catch (e: Exception) {
            java.time.LocalDate.now().toString()
        }
    }
}
