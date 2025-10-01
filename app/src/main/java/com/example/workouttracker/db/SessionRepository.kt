package com.example.workouttracker.db

class SessionRepository(private val dao: SessionDao) {
    
    suspend fun insertSession(session: SessionEntity): Long = dao.insert(session)
    
    suspend fun getAllSessions(): List<SessionEntity> = dao.getAll()
    
    suspend fun getSessionById(id: Long): SessionEntity? = dao.getById(id)
    
    suspend fun deleteSession(id: Long) = dao.deleteById(id)
    
    suspend fun getSessionCount(): Int = dao.getCount()
    
    suspend fun getTotalRepsForExercise(exercise: String): Int = dao.getTotalRepsForExercise(exercise) ?: 0
    
    suspend fun getTotalXp(): Int = dao.getTotalXp() ?: 0

    // Daily progress
    suspend fun upsertDaily(dp: DailyProgressEntity) = dao.upsertDaily(dp)
    suspend fun getDaily(date: String): DailyProgressEntity? = dao.getDaily(date)
    suspend fun getRecentDaily(limit: Int = 14): List<DailyProgressEntity> = dao.getRecentDaily(limit)

    // User profile
    suspend fun upsertProfile(totalXp: Int, name: String? = null) {
        val existing = dao.getProfile()
        dao.upsertProfile(UserProfileEntity(id = 1, totalXp = totalXp, name = name ?: existing?.name.orEmpty()))
    }
    suspend fun updateName(name: String) {
        val existing = dao.getProfile()
        val xp = existing?.totalXp ?: 0
        dao.upsertProfile(UserProfileEntity(id = 1, totalXp = xp, name = name))
    }
    suspend fun getProfile(): UserProfileEntity? = dao.getProfile()
}
