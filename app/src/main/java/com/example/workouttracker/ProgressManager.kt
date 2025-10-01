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
        val totalXp: Int
    )

    private val _dailyState = MutableStateFlow<DailyState?>(null)
    val dailyState: StateFlow<DailyState?> = _dailyState

    private var cachedGoals: DailyGoals = DailyGoals(10,10,60)

    private fun today(): String = LocalDate.now().toString()

    fun load(initialLevelInfo: LevelSystem.LevelInfo, totalXp: Int) {
        cachedGoals = DailyGoals(initialLevelInfo.pushGoal, initialLevelInfo.squatGoal, 60)
        scope.launch(Dispatchers.IO) { loadOrCreate(initialLevelInfo.level, totalXp) }
    }

    private suspend fun loadOrCreate(level: Int, totalXp: Int) {
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
        val recents = repo.getRecentDaily(30)
        var streak = 0
        var expected = LocalDate.now()
        for (dp in recents) {
            val d = LocalDate.parse(dp.date)
            if (d == expected) {
                if (dp.goalsMet) {
                    streak++
                    expected = expected.minusDays(1)
                } else break
            }
        }
        return streak
    }

    private fun publish(entity: DailyProgressEntity, level: Int, totalXp: Int, streak: Int) {
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

    fun updateGoals(levelInfo: LevelSystem.LevelInfo, totalXp: Int) {
        cachedGoals = DailyGoals(levelInfo.pushGoal, levelInfo.squatGoal, 60)
        scope.launch(Dispatchers.IO) {
            val date = today()
            val entity = repo.getDaily(date) ?: return@launch
            val streak = computeStreak()
            val updated = entity.copy(goalsMet = goalsMet(entity))
            publish(updated, levelInfo.level, totalXp, streak)
        }
    }

    fun recordPushups(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Int) {
        scope.launch(Dispatchers.IO) { mutateDay(pushupsDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }
    fun recordSquats(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Int) {
        scope.launch(Dispatchers.IO) { mutateDay(squatsDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }
    fun recordBicepLeft(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Int) {
        scope.launch(Dispatchers.IO) { mutateDay(bicepLeftDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }
    fun recordBicepRight(count: Int, levelInfo: LevelSystem.LevelInfo, totalXp: Int) {
        scope.launch(Dispatchers.IO) { mutateDay(bicepRightDelta = count, levelInfo = levelInfo, totalXp = totalXp) }
    }

    private suspend fun mutateDay(
        pushupsDelta: Int = 0,
        squatsDelta: Int = 0,
        bicepLeftDelta: Int = 0,
        bicepRightDelta: Int = 0,
        levelInfo: LevelSystem.LevelInfo,
        totalXp: Int
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
}
