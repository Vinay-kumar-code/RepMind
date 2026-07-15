package com.example.workouttracker.db

class SessionRepository(private val dao: SessionDao) {
    
    suspend fun insertSession(session: SessionEntity): Long = dao.insert(session)
    
    suspend fun getAllSessions(): List<SessionEntity> = dao.getAll()
    
    suspend fun getSessionById(id: Long): SessionEntity? = dao.getById(id)
    
    suspend fun getUnsyncedSessions(): List<SessionEntity> = dao.getUnsyncedSessions()
    
    suspend fun markSessionsSynced(ids: List<Long>) = dao.markSessionsSynced(ids)

    suspend fun getUnsyncedToHealthConnect(): List<SessionEntity> = dao.getUnsyncedToHealthConnect()

    suspend fun markSessionsSyncedToHealthConnect(ids: List<Long>) = dao.markSessionsSyncedToHealthConnect(ids)
    
    suspend fun insertAllSessions(sessions: List<SessionEntity>) = dao.insertAll(sessions)
    
    suspend fun deleteSession(id: Long) = dao.deleteById(id)
    
    suspend fun getSessionCount(): Int = dao.getCount()
    
    suspend fun getTotalRepsForExercise(exercise: String): Int = dao.getTotalRepsForExercise(exercise) ?: 0
    
    suspend fun getBestSetForExercise(exercise: String): Int = dao.getBestSetForExercise(exercise) ?: 0
    
    suspend fun getTotalXp(): Float = dao.getTotalXp() ?: 0f
    
    suspend fun getXpBetween(start: String, end: String): Float = dao.getXpSumBetween(start, end) ?: 0f

    // Daily progress
    suspend fun upsertDaily(dp: DailyProgressEntity) = dao.upsertDaily(dp)
    suspend fun getDaily(date: String): DailyProgressEntity? = dao.getDaily(date)
    suspend fun getRecentDaily(limit: Int = 14): List<DailyProgressEntity> = dao.getRecentDaily(limit)

    // User profile
    suspend fun upsertProfile(totalXp: Float, name: String? = null) {
        val existing = dao.getProfile()
        val newName = name ?: existing?.name.orEmpty()
        val profile = existing?.copy(totalXp = totalXp, name = newName) 
            ?: UserProfileEntity(id = 1, totalXp = totalXp, name = newName)
        dao.upsertProfile(profile)
    }
    suspend fun updateName(name: String) {
        val existing = dao.getProfile()
        val xp = existing?.totalXp ?: 0f
        val profile = existing?.copy(name = name) ?: UserProfileEntity(id = 1, totalXp = xp, name = name)
        dao.upsertProfile(profile)
    }
    suspend fun updateCustomGoals(useCustom: Boolean, push: Int, squat: Int, bicep: Int) {
        val existing = dao.getProfile()
        val profile = existing?.copy(
            useCustomGoals = useCustom,
            customPushGoal = push,
            customSquatGoal = squat,
            customBicepGoal = bicep
        ) ?: UserProfileEntity(
            useCustomGoals = useCustom,
            customPushGoal = push,
            customSquatGoal = squat,
            customBicepGoal = bicep
        )
        dao.upsertProfile(profile)
    }
    
    suspend fun updateNotionKeys(apiKey: String, dbId: String) {
        val existing = dao.getProfile()
        val profile = existing?.copy(
            notionApiKey = apiKey,
            notionDbId = dbId
        ) ?: UserProfileEntity(
            notionApiKey = apiKey,
            notionDbId = dbId
        )
        dao.upsertProfile(profile)
    }
    
    suspend fun getProfile(): UserProfileEntity? = dao.getProfile()

    suspend fun resetAllProgress() {
        dao.deleteAllSessions()
        dao.deleteAllDailyProgress()
        val existing = dao.getProfile()
        if (existing != null) {
            dao.upsertProfile(existing.copy(totalXp = 0f))
        } else {
            dao.upsertProfile(UserProfileEntity(id = 1, totalXp = 0f, name = ""))
        }
    }
}
