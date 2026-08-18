package com.example.workouttracker

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class HealthConnectManager(private val context: Context, private val repo: SessionRepository) {

    val sdkStatus: Int
        get() = try {
            HealthConnectClient.getSdkStatus(context)
        } catch (t: Throwable) {
            HealthConnectClient.SDK_UNAVAILABLE
        }

    val isSupported: Boolean
        get() = try {
            sdkStatus == HealthConnectClient.SDK_AVAILABLE
        } catch (t: Throwable) {
            false
        }

    fun getClient(): HealthConnectClient? {
        return try {
            if (isSupported) HealthConnectClient.getOrCreate(context) else null
        } catch (t: Throwable) {
            null
        }
    }

    val permissions by lazy {
        try {
            setOf(
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(StepsRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getReadPermission(ExerciseSessionRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(ExerciseSessionRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class),
                androidx.health.connect.client.permission.HealthPermission.getWritePermission(StepsRecord::class)
            )
        } catch (t: Throwable) {
            emptySet()
        }
    }

    suspend fun hasPermissions(): Boolean {
        val client = getClient() ?: return false
        return try {
            val granted = client.permissionController.getGrantedPermissions()
            granted.containsAll(permissions)
        } catch (e: Throwable) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getTodaySteps(): Long = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext 0L
        try {
            val startOfDay = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS).toInstant()
            val now = Instant.now()
            
            val response = client.readRecords(
                ReadRecordsRequest(
                    StepsRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(startOfDay, now)
                )
            )
            var totalSteps = 0L
            for (record in response.records) {
                totalSteps += record.count
            }
            totalSteps
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    suspend fun getLast7DaysSteps(): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Pair<String, Long>>()
        val today = LocalDate.now()
        val client = getClient()

        val allSessions = repo.getAllSessions()

        for (i in 6 downTo 0) {
            val day = today.minusDays(i.toLong())
            val label = day.format(DateTimeFormatter.ofPattern("EEE"))
            var daySteps = 0L

            if (client != null && hasPermissions()) {
                try {
                    val startOfDay = day.atStartOfDay(ZoneId.systemDefault()).toInstant()
                    val endOfDay = if (i == 0) Instant.now() else day.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()

                    val response = client.readRecords(
                        ReadRecordsRequest(
                            StepsRecord::class,
                            timeRangeFilter = TimeRangeFilter.between(startOfDay, endOfDay)
                        )
                    )
                    daySteps = response.records.sumOf { it.count }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            if (daySteps == 0L) {
                // Fallback to database step sessions
                val dayStr = day.toString()
                val dbSteps = allSessions.filter { s ->
                    s.exercise.equals("steps", ignoreCase = true) && s.timestampIso.startsWith(prefix = dayStr)
                }.sumOf { it.reps.toLong() }
                daySteps = dbSteps
            }

            list.add(Pair(label, daySteps))
        }
        list
    }

    suspend fun syncFromHealthConnect(rates: XpRates? = null): Result<Int> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext Result.failure(Exception("Health Connect not available"))
        try {
            val start = ZonedDateTime.now().minusDays(7).toInstant()
            val end = Instant.now()
            
            val response = client.readRecords(
                ReadRecordsRequest(
                    ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end)
                )
            )
            
            var importedCount = 0
            for (record in response.records) {
                val isoTime = record.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                
                val existing = repo.getAllSessions().find { it.timestampIso == isoTime }
                if (existing == null) {
                    val durationSecs = ChronoUnit.SECONDS.between(record.startTime, record.endTime).toFloat()
                    val durationMins = (durationSecs / 60).toInt()
                    
                    val exerciseName = when (record.exerciseType) {
                        ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING -> "Strength Training"
                        ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
                        ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Biking"
                        ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
                        else -> record.title ?: "Workout"
                    }
                    
                    var stepsDuringWorkout = 0L
                    if (record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING || 
                        record.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING || 
                        record.title == null) {
                        try {
                            val stepsResponse = client.readRecords(
                                ReadRecordsRequest(
                                    StepsRecord::class,
                                    timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime)
                                )
                            )
                            stepsDuringWorkout = stepsResponse.records.sumOf { it.count }
                        } catch (e: Exception) { e.printStackTrace() }
                    }
                    
                    val finalReps = if (stepsDuringWorkout > 0) stepsDuringWorkout.toInt() else 0
                    val xpEarned = if (finalReps > 0) LevelSystem.xpForManualReps(finalReps, rates) else LevelSystem.xpForManualDuration(durationMins, rates)
                    
                    val entity = SessionEntity(
                        timestampIso = isoTime,
                        exercise = exerciseName,
                        reps = finalReps,
                        durationSeconds = durationSecs,
                        totalXp = xpEarned,
                        syncedToNotion = false,
                        isManual = true
                    )
                    repo.insertSession(entity)
                    importedCount++
                    
                    val currentProf = repo.getProfile()
                    val currentXp = currentProf?.totalXp ?: 0f
                    repo.upsertProfile(currentXp + xpEarned)
                }
            }
            Result.success(importedCount)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    suspend fun syncToHealthConnect(): Result<Int> = withContext(Dispatchers.IO) {
        val client = getClient() ?: return@withContext Result.failure(Exception("Health Connect not available"))
        try {
            val unsynced = repo.getUnsyncedToHealthConnect()
            if (unsynced.isEmpty()) return@withContext Result.success(0)

            val recordsToInsert = mutableListOf<androidx.health.connect.client.records.Record>()
            val syncedIds = mutableListOf<Long>()

            for (session in unsynced) {
                // Parse the existing timestamp
                val startTime = try {
                    java.time.OffsetDateTime.parse(session.timestampIso).toInstant()
                } catch (e: Exception) {
                    Instant.parse(session.timestampIso)
                }
                
                val endTime = startTime.plusSeconds(session.durationSeconds.toLong().coerceAtLeast(1L))
                
                val hcExerciseType = when (session.exercise.lowercase()) {
                    "running" -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
                    "walking" -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
                    "biking", "cycling" -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
                    "swimming" -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
                    "pushups", "squats", "pullups", "lunges" -> ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS
                    else -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
                }

                val sessionRecord = ExerciseSessionRecord(
                    startTime = startTime,
                    startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                    endTime = endTime,
                    endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                    exerciseType = hcExerciseType,
                    title = "${session.exercise} - Track Mate"
                )
                
                recordsToInsert.add(sessionRecord)

                // Add ActiveCaloriesBurnedRecord
                // Approximation: 1 XP is rough equivalent to 1 active calorie, or fallback to duration
                val calories = (session.totalXp.toDouble()).coerceAtLeast(session.durationSeconds / 60.0 * 4.0)
                val caloriesRecord = ActiveCaloriesBurnedRecord(
                    startTime = startTime,
                    startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                    endTime = endTime,
                    endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                    energy = androidx.health.connect.client.units.Energy.kilocalories(calories)
                )
                recordsToInsert.add(caloriesRecord)

                // If running or walking, add StepsRecord based on reps
                if (hcExerciseType == ExerciseSessionRecord.EXERCISE_TYPE_RUNNING || hcExerciseType == ExerciseSessionRecord.EXERCISE_TYPE_WALKING) {
                    if (session.reps > 0) {
                        val stepsRecord = StepsRecord(
                            startTime = startTime,
                            startZoneOffset = ZoneId.systemDefault().rules.getOffset(startTime),
                            endTime = endTime,
                            endZoneOffset = ZoneId.systemDefault().rules.getOffset(endTime),
                            count = session.reps.toLong()
                        )
                        recordsToInsert.add(stepsRecord)
                    }
                }

                syncedIds.add(session.id)
            }

            if (recordsToInsert.isNotEmpty()) {
                client.insertRecords(recordsToInsert)
                repo.markSessionsSyncedToHealthConnect(syncedIds)
            }
            Result.success(recordsToInsert.size)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }
}
