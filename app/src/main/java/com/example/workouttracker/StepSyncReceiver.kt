package com.example.workouttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.workouttracker.db.SessionDatabase
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class StepSyncReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = SessionDatabase.getInstance(context)
                val repo = SessionRepository(db.sessionDao())
                val hcManager = HealthConnectManager(context, repo)
                val xpPrefs = XpPreferences(context)
                val rates = xpPrefs.rates.value

                if (hcManager.isSupported && hcManager.hasPermissions()) {
                    val todaySteps = hcManager.getTodaySteps()
                    if (todaySteps > 0) {
                        val istZone = ZoneId.of("Asia/Kolkata")
                        val now = ZonedDateTime.now(istZone)
                        val timestampIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        val todayStr = now.toLocalDate().toString()

                        // Check if steps were already logged for today
                        val allSessions = repo.getAllSessions()
                        val existingStepSession = allSessions.find { session ->
                            session.exercise.equals("steps", ignoreCase = true) &&
                            session.timestampIso.startsWith(todayStr)
                        }

                        val xpEarned = LevelSystem.xpForManualReps(todaySteps.toInt(), rates)

                        if (existingStepSession == null) {
                            val entity = SessionEntity(
                                timestampIso = timestampIso,
                                exercise = "Steps",
                                reps = todaySteps.toInt(),
                                durationSeconds = 0f,
                                totalXp = xpEarned,
                                syncedToNotion = false,
                                isManual = true
                            )
                            repo.insertSession(entity)

                            // Update profile XP
                            val currentProf = repo.getProfile()
                            val currentXp = currentProf?.totalXp ?: 0f
                            repo.upsertProfile(currentXp + xpEarned)
                        } else {
                            // Update existing step session
                            val diffXp = xpEarned - existingStepSession.totalXp
                            val updated = existingStepSession.copy(
                                reps = todaySteps.toInt(),
                                totalXp = xpEarned,
                                syncedToNotion = false
                            )
                            repo.updateSession(updated)

                            if (diffXp > 0) {
                                val currentProf = repo.getProfile()
                                val currentXp = currentProf?.totalXp ?: 0f
                                repo.upsertProfile(currentXp + diffXp)
                            }
                        }

                        // Trigger Notion Sync if configured
                        val profile = repo.getProfile()
                        if (profile != null && profile.notionApiKey.isNotBlank() && profile.notionDbId.isNotBlank()) {
                            try {
                                val notionSync = NotionSyncManager(repo)
                                notionSync.syncUnsyncedSessions(profile.notionApiKey, profile.notionDbId)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                // Reschedule for next night
                StepSyncScheduler.scheduleDailySync(context)
                pendingResult.finish()
            }
        }
    }
}
