package com.example.workouttracker

import android.content.Context
import android.content.SharedPreferences
import com.example.workouttracker.db.DailyProgressEntity
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

enum class AchievementType {
    LONG_TERM, WEEKLY
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val category: String,
    val type: AchievementType,
    val target: Int,
    val current: Int,
    val isUnlocked: Boolean,
    val isClaimed: Boolean,
    val xpReward: Float
)

class AchievementManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("achievements_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CLAIMED_IDS = "claimed_achievement_ids"
    }

    private fun getClaimedIds(): Set<String> {
        return prefs.getStringSet(KEY_CLAIMED_IDS, emptySet()) ?: emptySet()
    }

    fun claimAchievement(achievement: Achievement, repo: SessionRepository, onClaimed: (Float) -> Unit) {
        val claimed = getClaimedIds().toMutableSet()
        if (claimed.add(achievement.id)) {
            prefs.edit().putStringSet(KEY_CLAIMED_IDS, claimed).apply()
            
            CoroutineScope(Dispatchers.IO).launch {
                val profile = repo.getProfile()
                val currentXp = profile?.totalXp ?: 0f
                val newXp = currentXp + achievement.xpReward
                repo.upsertProfile(newXp)

                // Log SessionEntity for Notion synchronization
                val istZone = ZoneId.of("Asia/Kolkata")
                val now = ZonedDateTime.now(istZone)
                val timestampIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

                val sessionEntity = SessionEntity(
                    timestampIso = timestampIso,
                    exercise = "Achievement: ${achievement.title}",
                    reps = achievement.target,
                    durationSeconds = 0f,
                    totalXp = achievement.xpReward,
                    syncedToNotion = false,
                    isManual = true,
                    syncedToHealthConnect = true // Do not sync achievement as physical workout to Health Connect
                )
                repo.insertSession(sessionEntity)

                // Sync to Notion if credentials are present
                if (profile != null && profile.notionApiKey.isNotBlank() && profile.notionDbId.isNotBlank()) {
                    try {
                        val notionSync = NotionSyncManager(repo)
                        notionSync.syncUnsyncedSessions(profile.notionApiKey, profile.notionDbId)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                withContext(Dispatchers.Main) {
                    onClaimed(newXp)
                }
            }
        }
    }

    fun computeAchievements(
        sessions: List<SessionEntity>,
        recentDaily: List<DailyProgressEntity>,
        currentStreak: Int,
        todaySteps: Long
    ): List<Achievement> {
        val claimed = getClaimedIds()
        val today = LocalDate.now()

        fun parseDate(iso: String): LocalDate {
            return try {
                val instant = try { OffsetDateTime.parse(iso).toInstant() } catch (_: Exception) { java.time.Instant.parse(iso) }
                instant.atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (e: Exception) { LocalDate.now() }
        }

        // Filter out achievement sessions when calculating stats
        val workoutSessions = sessions.filter { !it.exercise.startsWith("Achievement", ignoreCase = true) }

        // Totals across all workout sessions
        var totalPushups = 0
        var totalSquats = 0
        var totalPullups = 0
        var totalPlankSeconds = 0
        var totalDips = 0
        var totalCrunches = 0
        var totalLateralRaises = 0
        var totalGluteBridges = 0
        var totalHighKnees = 0
        var totalLegRaises = 0
        var totalJumpingJacks = 0
        var totalBicepCurls = 0
        var totalShoulderPress = 0
        var totalLunges = 0
        var totalStepsAllTime = 0L
        var totalMinutesAllTime = 0

        for (s in workoutSessions) {
            val ex = s.exercise.lowercase()
            totalMinutesAllTime += (s.durationSeconds / 60).toInt()
            when {
                ex.contains("pushup") -> totalPushups += s.reps
                ex.contains("squat") -> totalSquats += s.reps
                ex.contains("pullup") -> totalPullups += s.reps
                ex.contains("plank") -> totalPlankSeconds += s.reps
                ex.contains("dip") -> totalDips += s.reps
                ex.contains("crunch") || ex.contains("situp") -> totalCrunches += s.reps
                ex.contains("lateral") -> totalLateralRaises += s.reps
                ex.contains("glute") || ex.contains("bridge") -> totalGluteBridges += s.reps
                ex.contains("high_knee") || ex.contains("knee") -> totalHighKnees += s.reps
                ex.contains("leg_raise") -> totalLegRaises += s.reps
                ex.contains("jack") -> totalJumpingJacks += s.reps
                ex.contains("bicep") || ex.contains("curl") -> totalBicepCurls += s.reps
                ex.contains("shoulder") || ex.contains("press") -> totalShoulderPress += s.reps
                ex.contains("lunge") -> totalLunges += s.reps
                ex.contains("step") -> totalStepsAllTime += s.reps
            }
        }
        totalStepsAllTime += todaySteps
        val totalSessionsCount = workoutSessions.size
        val totalXpAllTime = workoutSessions.sumOf { it.totalXp.toDouble() }.toInt()

        // Weekly Reps & Stats (last 7 days)
        val weeklySessions = workoutSessions.filter {
            val d = parseDate(it.timestampIso)
            d == today || d.isAfter(today.minusDays(7))
        }
        val weeklyReps = weeklySessions.filter { !it.exercise.equals("steps", ignoreCase = true) }.sumOf { it.reps }
        val weeklyUniqueWorkoutDays = weeklySessions.map { parseDate(it.timestampIso) }.distinct().size
        val weeklyPlankSeconds = weeklySessions.filter { it.exercise.lowercase().contains("plank") }.sumOf { it.reps }
        val weeklyPushups = weeklySessions.filter { it.exercise.lowercase().contains("pushup") }.sumOf { it.reps }
        val weeklySquats = weeklySessions.filter { it.exercise.lowercase().contains("squat") }.sumOf { it.reps }
        val weeklySteps = weeklySessions.filter { it.exercise.lowercase().contains("step") }.sumOf { it.reps.toLong() } + todaySteps

        // Single day records
        val repsByDay = workoutSessions.filter { !it.exercise.equals("steps", ignoreCase = true) }
            .groupBy { parseDate(it.timestampIso) }
            .mapValues { it.value.sumOf { s -> s.reps } }
        val maxRepsSingleDay = repsByDay.values.maxOrNull() ?: 0

        // Max single session records
        val maxPlankSingleSession = workoutSessions.filter { it.exercise.lowercase().contains("plank") }
            .maxOfOrNull { it.reps } ?: 0
        val maxPushupSingleSession = workoutSessions.filter { it.exercise.lowercase().contains("pushup") }
            .maxOfOrNull { it.reps } ?: 0
        val maxSquatSingleSession = workoutSessions.filter { it.exercise.lowercase().contains("squat") }
            .maxOfOrNull { it.reps } ?: 0
        val maxPullupSingleSession = workoutSessions.filter { it.exercise.lowercase().contains("pullup") }
            .maxOfOrNull { it.reps } ?: 0

        val exercisesToday = workoutSessions.filter { parseDate(it.timestampIso) == today }
            .map { it.exercise.lowercase() }
            .distinct().size

        val achievements = mutableListOf<Achievement>()

        fun add(
            id: String,
            title: String,
            description: String,
            icon: String,
            category: String,
            type: AchievementType,
            target: Int,
            current: Int,
            xpReward: Float
        ) {
            val isUnlocked = current >= target
            val isClaimed = claimed.contains(id)
            achievements.add(
                Achievement(
                    id = id,
                    title = title,
                    description = description,
                    icon = icon,
                    category = category,
                    type = type,
                    target = target,
                    current = current.coerceAtMost(target),
                    isUnlocked = isUnlocked,
                    isClaimed = isClaimed,
                    xpReward = xpReward
                )
            )
        }

        // ==================== LONG-TERM ACHIEVEMENTS (Scaled 5 to 50 XP) ====================
        // Pushups
        add("pushup_50", "Pushup Recruit", "Complete 50 total pushups", "🔰", "Upper Body", AchievementType.LONG_TERM, 50, totalPushups, 5f)
        add("pushup_100", "Century Pushups", "Complete 100 total pushups", "🥇", "Upper Body", AchievementType.LONG_TERM, 100, totalPushups, 15f)
        add("pushup_500", "Pushup Gladiator", "Complete 500 total pushups", "⚔️", "Upper Body", AchievementType.LONG_TERM, 500, totalPushups, 30f)
        add("pushup_1000", "Pushup Legend", "Complete 1,000 total pushups", "👑", "Upper Body", AchievementType.LONG_TERM, 1000, totalPushups, 45f)
        add("pushup_2500", "Pushup Immortal", "Complete 2,500 total pushups", "⚡", "Upper Body", AchievementType.LONG_TERM, 2500, totalPushups, 50f)

        // Squats
        add("squat_50", "Squat Apprentice", "Complete 50 total squats", "🔰", "Lower Body", AchievementType.LONG_TERM, 50, totalSquats, 5f)
        add("squat_100", "Squat Centurion", "Complete 100 total squats", "🦵", "Lower Body", AchievementType.LONG_TERM, 100, totalSquats, 15f)
        add("squat_500", "Leg Day Titan", "Complete 500 total squats", "🏋️", "Lower Body", AchievementType.LONG_TERM, 500, totalSquats, 30f)
        add("squat_1000", "Iron Quads", "Complete 1,000 total squats", "🏔️", "Lower Body", AchievementType.LONG_TERM, 1000, totalSquats, 45f)
        add("squat_2500", "Colossus of Squats", "Complete 2,500 total squats", "🗿", "Lower Body", AchievementType.LONG_TERM, 2500, totalSquats, 50f)

        // Pullups
        add("pullup_10", "Pullup Novice", "Complete 10 total pullups", "🧗", "Upper Body", AchievementType.LONG_TERM, 10, totalPullups, 10f)
        add("pullup_25", "Pullup Pioneer", "Complete 25 total pullups", "🦅", "Upper Body", AchievementType.LONG_TERM, 25, totalPullups, 20f)
        add("pullup_50", "Pullup Master", "Complete 50 total pullups", "🥋", "Upper Body", AchievementType.LONG_TERM, 50, totalPullups, 35f)
        add("pullup_100", "Pullup Ascendant", "Complete 100 total pullups", "🕊️", "Upper Body", AchievementType.LONG_TERM, 100, totalPullups, 45f)
        add("pullup_250", "Apex Climber", "Complete 250 total pullups", "🌌", "Upper Body", AchievementType.LONG_TERM, 250, totalPullups, 50f)

        // Plank Core
        add("plank_120", "Core Ignition", "Hold 120 total seconds of plank", "🪵", "Core", AchievementType.LONG_TERM, 120, totalPlankSeconds, 10f)
        add("plank_300", "Iron Core", "Hold 300 total seconds of plank", "🛡️", "Core", AchievementType.LONG_TERM, 300, totalPlankSeconds, 20f)
        add("plank_600", "Diamond Shield", "Hold 600 total seconds of plank", "💎", "Core", AchievementType.LONG_TERM, 600, totalPlankSeconds, 30f)
        add("plank_1800", "Unbreakable Core", "Hold 1,800 total seconds (30 mins) of plank", "⚡", "Core", AchievementType.LONG_TERM, 1800, totalPlankSeconds, 45f)
        add("plank_3600", "One-Hour Plank Master", "Accumulate 3,600 seconds (1 hr) of plank", "⏳", "Core", AchievementType.LONG_TERM, 3600, totalPlankSeconds, 50f)

        // Upper Body & Arms
        add("bicep_100", "Gun Show", "Complete 100 total bicep curls", "💪", "Upper Body", AchievementType.LONG_TERM, 100, totalBicepCurls, 10f)
        add("bicep_500", "Arm Cannons", "Complete 500 total bicep curls", "💣", "Upper Body", AchievementType.LONG_TERM, 500, totalBicepCurls, 25f)
        add("dips_50", "Dip Trainee", "Complete 50 tricep dips", "🧱", "Upper Body", AchievementType.LONG_TERM, 50, totalDips, 10f)
        add("dips_200", "Dip Destroyer", "Complete 200 tricep dips", "💥", "Upper Body", AchievementType.LONG_TERM, 200, totalDips, 25f)
        add("lateral_100", "Shoulder Sculptor", "Complete 100 lateral raises", "🦅", "Upper Body", AchievementType.LONG_TERM, 100, totalLateralRaises, 15f)
        add("lateral_300", "Wingspan King", "Complete 300 lateral raises", "🪽", "Upper Body", AchievementType.LONG_TERM, 300, totalLateralRaises, 30f)
        add("shoulder_100", "Overhead Power", "Complete 100 shoulder presses", "🏋️‍♂️", "Upper Body", AchievementType.LONG_TERM, 100, totalShoulderPress, 15f)

        // Core & Lower Body
        add("crunch_100", "Six-Pack Architect", "Complete 100 crunches", "🧘", "Core", AchievementType.LONG_TERM, 100, totalCrunches, 10f)
        add("crunch_500", "Abs of Steel", "Complete 500 crunches", "🧇", "Core", AchievementType.LONG_TERM, 500, totalCrunches, 25f)
        add("glute_100", "Bridge Builder", "Complete 100 glute bridges", "🌉", "Lower Body", AchievementType.LONG_TERM, 100, totalGluteBridges, 10f)
        add("glute_300", "Glute Powerhouse", "Complete 300 glute bridges", "🍑", "Lower Body", AchievementType.LONG_TERM, 300, totalGluteBridges, 25f)
        add("legraise_100", "Lower Core Commander", "Complete 100 leg raises", "📐", "Core", AchievementType.LONG_TERM, 100, totalLegRaises, 15f)
        add("lunges_100", "Stride Master", "Complete 100 lunges", "🏃‍♂️", "Lower Body", AchievementType.LONG_TERM, 100, totalLunges, 15f)

        // Cardio & Steps
        add("jacks_200", "Jumping Dynamo", "Complete 200 jumping jacks", "⚡", "Cardio", AchievementType.LONG_TERM, 200, totalJumpingJacks, 15f)
        add("knees_200", "High Knee Sprinter", "Complete 200 high knees", "🌪️", "Cardio", AchievementType.LONG_TERM, 200, totalHighKnees, 15f)
        add("steps_25k", "Trail Blazer", "Walk 25,000 total steps", "👟", "Cardio", AchievementType.LONG_TERM, 25000, totalStepsAllTime.toInt(), 15f)
        add("steps_50k", "Pathfinder", "Walk 50,000 total steps", "🧭", "Cardio", AchievementType.LONG_TERM, 50000, totalStepsAllTime.toInt(), 25f)
        add("steps_100k", "Globe Trotter", "Walk 100,000 total steps", "🏃", "Cardio", AchievementType.LONG_TERM, 100000, totalStepsAllTime.toInt(), 40f)
        add("steps_250k", "Marathon Master", "Walk 250,000 total steps", "🌍", "Cardio", AchievementType.LONG_TERM, 250000, totalStepsAllTime.toInt(), 50f)

        // Streak & Dedication
        add("streak_3", "First Spark", "Reach a 3-day workout streak", "✨", "Streak", AchievementType.LONG_TERM, 3, currentStreak, 5f)
        add("streak_7", "Week of Fire", "Reach a 7-day workout streak", "🔥", "Streak", AchievementType.LONG_TERM, 7, currentStreak, 15f)
        add("streak_14", "Fortnight of Steel", "Reach a 14-day workout streak", "⚔️", "Streak", AchievementType.LONG_TERM, 14, currentStreak, 25f)
        add("streak_30", "Iron Discipline", "Reach a 30-day workout streak", "🏆", "Streak", AchievementType.LONG_TERM, 30, currentStreak, 40f)
        add("streak_60", "Streak Sentinel", "Reach a 60-day workout streak", "🛡️", "Streak", AchievementType.LONG_TERM, 60, currentStreak, 45f)
        add("streak_100", "Century Streak Club", "Reach an elite 100-day workout streak", "💯", "Streak", AchievementType.LONG_TERM, 100, currentStreak, 50f)

        // Experience & Milestones
        add("sessions_10", "Consistent Grinder", "Log 10 workout sessions", "📝", "Milestone", AchievementType.LONG_TERM, 10, totalSessionsCount, 10f)
        add("sessions_50", "Gym Regular", "Log 50 workout sessions", "🏢", "Milestone", AchievementType.LONG_TERM, 50, totalSessionsCount, 25f)
        add("sessions_100", "Fitness Devotee", "Log 100 workout sessions", "⛩️", "Milestone", AchievementType.LONG_TERM, 100, totalSessionsCount, 45f)
        add("xp_1000", "XP Prodigy", "Earn 1,000 total XP", "🌟", "Milestone", AchievementType.LONG_TERM, 1000, totalXpAllTime, 20f)
        add("xp_5000", "XP Overlord", "Earn 5,000 total XP", "🪐", "Milestone", AchievementType.LONG_TERM, 5000, totalXpAllTime, 50f)
        add("time_60m", "Hour of Power", "Accumulate 60 minutes of total workout time", "⏱️", "Milestone", AchievementType.LONG_TERM, 60, totalMinutesAllTime, 15f)
        add("time_300m", "Time Champion", "Accumulate 300 minutes (5 hrs) of workout time", "⏳", "Milestone", AchievementType.LONG_TERM, 300, totalMinutesAllTime, 40f)

        // ==================== WEEKLY & SHORT-TERM ACHIEVEMENTS (Scaled 5 to 45 XP) ====================
        // Weekly Reps & Volume
        add("weekly_reps_100", "Weekly Starter", "Complete 100 total reps in the past 7 days", "🎯", "Weekly", AchievementType.WEEKLY, 100, weeklyReps, 10f)
        add("weekly_reps_250", "Weekly Warrior", "Complete 250 total reps in the past 7 days", "⚡", "Weekly", AchievementType.WEEKLY, 250, weeklyReps, 25f)
        add("weekly_reps_500", "Weekly Beast", "Complete 500 total reps in the past 7 days", "🦁", "Weekly", AchievementType.WEEKLY, 500, weeklyReps, 40f)

        // Weekly Frequency
        add("weekly_days_3", "Three-Day Focus", "Work out on at least 3 distinct days in the last 7 days", "🗓️", "Weekly", AchievementType.WEEKLY, 3, weeklyUniqueWorkoutDays, 10f)
        add("weekly_days_5", "Consistency King", "Work out on at least 5 distinct days in the last 7 days", "📅", "Weekly", AchievementType.WEEKLY, 5, weeklyUniqueWorkoutDays, 25f)
        add("weekly_days_7", "Perfect Week", "Work out every day across the last 7 days", "👑", "Weekly", AchievementType.WEEKLY, 7, weeklyUniqueWorkoutDays, 45f)

        // Weekly Specifics
        add("weekly_plank_180", "Weekly Iron Plank", "Hold 180 seconds of plank in the past 7 days", "🪵", "Weekly", AchievementType.WEEKLY, 180, weeklyPlankSeconds, 15f)
        add("weekly_push_100", "Weekly Century Push", "Complete 100 pushups in the past 7 days", "🥇", "Weekly", AchievementType.WEEKLY, 100, weeklyPushups, 20f)
        add("weekly_squat_100", "Weekly Century Squat", "Complete 100 squats in the past 7 days", "🦵", "Weekly", AchievementType.WEEKLY, 100, weeklySquats, 20f)
        add("weekly_steps_35k", "35K Weekly Steps", "Walk 35,000 steps over the last 7 days", "👟", "Weekly", AchievementType.WEEKLY, 35000, weeklySteps.toInt(), 25f)

        // Daily Feats & Records
        add("daily_50", "Daily Half-Centurion", "Complete 50 reps in a single day", "🎖️", "Daily", AchievementType.WEEKLY, 50, maxRepsSingleDay, 5f)
        add("daily_100", "Daily Centurion", "Complete 100 reps in a single day", "🌟", "Daily", AchievementType.WEEKLY, 100, maxRepsSingleDay, 15f)
        add("daily_200", "Daily Double Centurion", "Complete 200 reps in a single day", "💥", "Daily", AchievementType.WEEKLY, 200, maxRepsSingleDay, 30f)
        add("daily_steps_5k", "5K Starter", "Reach 5,000 steps today", "🚶", "Daily", AchievementType.WEEKLY, 5000, todaySteps.toInt(), 5f)
        add("daily_steps_10k", "10K Club", "Reach 10,000 steps today", "👟", "Daily", AchievementType.WEEKLY, 10000, todaySteps.toInt(), 15f)
        add("daily_steps_15k", "15K Champion", "Reach 15,000 steps today", "🚀", "Daily", AchievementType.WEEKLY, 15000, todaySteps.toInt(), 25f)
        add("daily_variety_3", "Triple Threat", "Log 3 different exercise types in one day", "🎯", "Daily", AchievementType.WEEKLY, 3, exercisesToday, 10f)
        add("daily_variety_5", "Quintessential Athlete", "Log 5 different exercise types in one day", "🌈", "Daily", AchievementType.WEEKLY, 5, exercisesToday, 25f)

        // Single Session Unbroken Feats
        add("plank_hold_45", "45-Second Lock", "Hold a 45-second plank in a single session", "⏱️", "Core", AchievementType.WEEKLY, 45, maxPlankSingleSession, 10f)
        add("plank_hold_60", "One-Minute Hero", "Hold a 60-second plank in a single session", "⚡", "Core", AchievementType.WEEKLY, 60, maxPlankSingleSession, 20f)
        add("plank_hold_120", "Two-Minute Titan", "Hold a 120-second plank in a single session", "🛡️", "Core", AchievementType.WEEKLY, 120, maxPlankSingleSession, 35f)
        add("pushup_set_25", "Unbroken Pushups", "Complete 25 pushups in a single session", "⚔️", "Upper Body", AchievementType.WEEKLY, 25, maxPushupSingleSession, 15f)
        add("squat_set_30", "Unbroken Squats", "Complete 30 squats in a single session", "🦵", "Lower Body", AchievementType.WEEKLY, 30, maxSquatSingleSession, 15f)
        add("pullup_set_10", "Unbroken Pullups", "Complete 10 pullups in a single session", "🧗", "Upper Body", AchievementType.WEEKLY, 10, maxPullupSingleSession, 20f)

        return achievements
    }
}
