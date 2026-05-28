package com.example.workouttracker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class ThemePreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE)

    private val _themeMode = MutableStateFlow(prefs.getInt("theme_mode", 0)) // 0=System, 1=Light, 2=Dark
    val themeMode: StateFlow<Int> = _themeMode

    private val _useMaterialYou = MutableStateFlow(prefs.getBoolean("use_material_you", true))
    val useMaterialYou: StateFlow<Boolean> = _useMaterialYou

    fun setThemeMode(mode: Int) {
        prefs.edit().putInt("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setUseMaterialYou(use: Boolean) {
        prefs.edit().putBoolean("use_material_you", use).apply()
        _useMaterialYou.value = use
    }
}
