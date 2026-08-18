package com.example.workouttracker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class StreakInfo(
    val streak: Int,
    val availableFreezes: Int,
    val usedFreezes: Int,
    val frozenDates: Set<String>,
    val isManual: Boolean
)

class StreakPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("streak_preferences", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_MANUAL_STREAK = "manual_streak_val"
        private const val KEY_MANUAL_DATE = "manual_streak_date"
        private const val KEY_FROZEN_DATES = "frozen_dates_set"
        private const val KEY_USED_FREEZES = "used_freezes_count"
    }

    private val _streakInfo = MutableStateFlow(calculateStreakInfo(0))
    val streakInfo: StateFlow<StreakInfo> = _streakInfo.asStateFlow()

    fun getFrozenDates(): Set<String> {
        return prefs.getStringSet(KEY_FROZEN_DATES, emptySet()) ?: emptySet()
    }

    fun getUsedFreezes(): Int {
        return prefs.getInt(KEY_USED_FREEZES, 0)
    }

    fun getManualStreak(): Int? {
        val v = prefs.getInt(KEY_MANUAL_STREAK, -1)
        return if (v >= 0) v else null
    }

    fun getManualDate(): String? {
        return prefs.getString(KEY_MANUAL_DATE, null)
    }

    private fun calculateStreakInfo(calculatedStreak: Int): StreakInfo {
        val manual = getManualStreak()
        val manualDate = getManualDate()
        val frozen = getFrozenDates()
        val used = getUsedFreezes()

        val effectiveStreak = if (manual != null && manualDate != null) {
            try {
                val setDate = LocalDate.parse(manualDate)
                val today = LocalDate.now()
                val daysPassed = today.toEpochDay() - setDate.toEpochDay()
                if (daysPassed >= 0) (manual + daysPassed.toInt()).coerceAtLeast(0) else manual
            } catch (e: Exception) { manual }
        } else {
            calculatedStreak
        }

        val earnedFreezes = effectiveStreak / 15
        val available = (earnedFreezes - used).coerceAtLeast(0)

        return StreakInfo(
            streak = effectiveStreak,
            availableFreezes = available,
            usedFreezes = used,
            frozenDates = frozen,
            isManual = manual != null
        )
    }

    fun loadStreakInfo(calculatedStreak: Int): StreakInfo {
        val info = calculateStreakInfo(calculatedStreak)
        _streakInfo.value = info
        return info
    }

    fun setManualStreak(streak: Int) {
        prefs.edit()
            .putInt(KEY_MANUAL_STREAK, streak)
            .putString(KEY_MANUAL_DATE, LocalDate.now().toString())
            .apply()
        loadStreakInfo(streak)
    }

    fun resetToAutoStreak(calculatedStreak: Int) {
        prefs.edit()
            .remove(KEY_MANUAL_STREAK)
            .remove(KEY_MANUAL_DATE)
            .apply()
        loadStreakInfo(calculatedStreak)
    }

    fun useStreakFreeze(date: String, calculatedStreak: Int): Boolean {
        val info = calculateStreakInfo(calculatedStreak)
        if (info.availableFreezes <= 0) return false

        val frozen = getFrozenDates().toMutableSet()
        if (frozen.contains(date)) return false // already frozen

        frozen.add(date)

        prefs.edit()
            .putStringSet(KEY_FROZEN_DATES, frozen)
            .putInt(KEY_USED_FREEZES, info.usedFreezes + 1)
            .apply()

        loadStreakInfo(calculatedStreak)
        return true
    }
}
