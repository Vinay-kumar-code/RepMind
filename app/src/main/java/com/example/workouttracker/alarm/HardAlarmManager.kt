package com.example.workouttracker.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import com.example.workouttracker.WorkoutEngine.ExerciseType
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar

data class AlarmConfig(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 0,
    val requiredWorkouts: Map<String, Int> = mapOf("pushup" to 15, "squat" to 20),
    val isRinging: Boolean = false,
    val triggeredTimestamp: Long = 0L
) {
    val isEnabled: Boolean get() = enabled
    val targets: Map<String, Int> get() = requiredWorkouts
}

data class VerificationResult(
    val isPassed: Boolean,
    val remaining: Map<String, Int>,
    val completed: Map<String, Int>
) {
    val isCompleted: Boolean get() = isPassed
    val remainingTargets: Map<String, Int> get() = remaining
}

class HardAlarmManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("hard_alarm_prefs", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<AlarmConfig> = _config.asStateFlow()

    fun setAlarmTime(hour: Int, minute: Int) {
        updateConfig(_config.value.copy(hour = hour, minute = minute))
    }

    fun setAlarmEnabled(enabled: Boolean) {
        updateConfig(_config.value.copy(enabled = enabled))
    }

    fun updateTargets(targets: Map<String, Int>) {
        updateConfig(_config.value.copy(requiredWorkouts = targets))
    }

    companion object {
        private const val KEY_ENABLED = "alarm_enabled"
        private const val KEY_HOUR = "alarm_hour"
        private const val KEY_MINUTE = "alarm_minute"
        private const val KEY_WORKOUTS = "alarm_workouts"
        private const val KEY_IS_RINGING = "alarm_is_ringing"
        private const val KEY_TRIGGERED_TS = "alarm_triggered_ts"
        const val ACTION_ALARM_TRIGGER = "com.example.workouttracker.action.HARD_ALARM_TRIGGER"

        fun normalizeKey(name: String): String {
            return when (name.lowercase().trim().replace(" ", "_")) {
                "pushup", "pushups" -> "pushups"
                "squat", "squats" -> "squats"
                "plank", "planks" -> "plank"
                "crunch", "crunches" -> "crunches"
                "tricep_dip", "tricep_dips" -> "tricep_dips"
                "jumping_jack", "jumping_jacks" -> "jumping_jacks"
                "high_knee", "high_knees" -> "high_knees"
                "lateral_raise", "lateral_raises" -> "lateral_raises"
                "glute_bridge", "glute_bridges" -> "glute_bridges"
                "lunge", "lunges" -> "lunges"
                "shoulder_press", "shoulder_presses" -> "shoulder_press"
                "pullup", "pullups" -> "pullups"
                "leg_raise", "leg_raises" -> "leg_raises"
                else -> name.lowercase().trim().replace(" ", "_")
            }
        }
    }

    private fun loadConfig(): AlarmConfig {
        val enabled = prefs.getBoolean(KEY_ENABLED, false)
        val hour = prefs.getInt(KEY_HOUR, 7)
        val minute = prefs.getInt(KEY_MINUTE, 0)
        val jsonStr = prefs.getString(KEY_WORKOUTS, null)
        val workouts = if (jsonStr != null) {
            try {
                val json = JSONObject(jsonStr)
                val map = mutableMapOf<String, Int>()
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val normKey = normalizeKey(k)
                    map[normKey] = json.getInt(k)
                }
                map
            } catch (e: Exception) {
                mapOf("pushups" to 15, "squats" to 20)
            }
        } else {
            mapOf("pushups" to 15, "squats" to 20)
        }
        val isRinging = prefs.getBoolean(KEY_IS_RINGING, false)
        val ts = prefs.getLong(KEY_TRIGGERED_TS, 0L)

        return AlarmConfig(enabled, hour, minute, workouts, isRinging, ts)
    }

    fun updateConfig(newConfig: AlarmConfig) {
        val normalizedMap = mutableMapOf<String, Int>()
        newConfig.requiredWorkouts.forEach { (k, v) ->
            normalizedMap[normalizeKey(k)] = v
        }

        val json = JSONObject()
        normalizedMap.forEach { (k, v) -> json.put(k, v) }

        prefs.edit()
            .putBoolean(KEY_ENABLED, newConfig.enabled)
            .putInt(KEY_HOUR, newConfig.hour)
            .putInt(KEY_MINUTE, newConfig.minute)
            .putString(KEY_WORKOUTS, json.toString())
            .putBoolean(KEY_IS_RINGING, newConfig.isRinging)
            .putLong(KEY_TRIGGERED_TS, newConfig.triggeredTimestamp)
            .apply()

        val resolvedConfig = newConfig.copy(requiredWorkouts = normalizedMap)
        _config.value = resolvedConfig

        if (resolvedConfig.enabled) {
            scheduleNextAlarm(resolvedConfig.hour, resolvedConfig.minute)
        } else {
            cancelAlarm()
        }
    }

    fun setRingingState(isRinging: Boolean, triggeredTs: Long = System.currentTimeMillis()) {
        prefs.edit()
            .putBoolean(KEY_IS_RINGING, isRinging)
            .putLong(KEY_TRIGGERED_TS, if (isRinging) triggeredTs else 0L)
            .apply()
        _config.value = _config.value.copy(
            isRinging = isRinging,
            triggeredTimestamp = if (isRinging) triggeredTs else 0L
        )
    }

    fun scheduleNextAlarm(hour: Int, minute: Int) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, HardAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    fun cancelAlarm() {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, HardAlarmReceiver::class.java).apply {
                action = ACTION_ALARM_TRIGGER
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    suspend fun verifyWorkouts(repo: SessionRepository): VerificationResult = withContext(Dispatchers.IO) {
        val currentCfg = loadConfig()
        val triggerTs = currentCfg.triggeredTimestamp
        val required = currentCfg.requiredWorkouts

        if (required.isEmpty()) {
            return@withContext VerificationResult(isPassed = true, remaining = emptyMap(), completed = emptyMap())
        }

        val allSessions = repo.getAllSessions()
        
        // Filter sessions done AFTER alarm started ringing (if ringing) AND auto-tracked (isManual == false)
        val validSessions = allSessions.filter { session ->
            if (session.isManual) return@filter false
            if (triggerTs <= 0L) return@filter true // When testing manually in Settings, check all today's sessions
            val sessionTs = try {
                val instant = try {
                    OffsetDateTime.parse(session.timestampIso).toInstant()
                } catch (_: Exception) {
                    Instant.parse(session.timestampIso)
                }
                instant.toEpochMilli()
            } catch (_: Exception) { 0L }
            sessionTs >= triggerTs
        }

        val completedCounts = mutableMapOf<String, Int>()
        for (s in validSessions) {
            val key = normalizeKey(s.exercise)
            val current = completedCounts.getOrDefault(key, 0)
            completedCounts[key] = current + s.reps
        }

        val remaining = mutableMapOf<String, Int>()
        var allSatisfied = true

        for ((exercise, target) in required) {
            val exKey = normalizeKey(exercise)
            val doneCount = completedCounts.getOrDefault(exKey, 0)

            if (doneCount < target) {
                allSatisfied = false
                remaining[exKey] = target - doneCount
            } else {
                remaining[exKey] = 0
            }
        }

        VerificationResult(
            isPassed = allSatisfied,
            remaining = remaining,
            completed = completedCounts
        )
    }
}
