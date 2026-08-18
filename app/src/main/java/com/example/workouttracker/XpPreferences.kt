package com.example.workouttracker

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class XpRates(
    val pushup: Float = 0.10f,
    val squat: Float = 0.10f,
    val bicepCurl: Float = 1f / 30f,
    val lunge: Float = 0.15f,
    val shoulderPress: Float = 0.10f,
    val jumpingJack: Float = 0.05f,
    val pullup: Float = 0.20f,
    val lateralRaise: Float = 0.08f,
    val gluteBridge: Float = 0.08f,
    val crunch: Float = 0.07f,
    val tricepDip: Float = 0.10f,
    val highKnee: Float = 0.03f,
    val legRaise: Float = 0.08f,
    val plank: Float = 0.005f, // Per second
    val manualRep: Float = 0.075f,
    val manualDurationPerMin: Float = 0.15f,
    val multiplier: Float = 1.0f
)

class XpPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("xp_preferences", Context.MODE_PRIVATE)

    private val _rates = MutableStateFlow(loadRates())
    val rates: StateFlow<XpRates> = _rates.asStateFlow()

    companion object {
        val DEFAULT_RATES = XpRates()
        private const val KEY_PUSHUP = "xp_pushup"
        private const val KEY_SQUAT = "xp_squat"
        private const val KEY_BICEP = "xp_bicep"
        private const val KEY_LUNGE = "xp_lunge"
        private const val KEY_SHOULDER_PRESS = "xp_shoulder_press"
        private const val KEY_JUMPING_JACK = "xp_jumping_jack"
        private const val KEY_PULLUP = "xp_pullup"
        private const val KEY_LATERAL_RAISE = "xp_lateral_raise"
        private const val KEY_GLUTE_BRIDGE = "xp_glute_bridge"
        private const val KEY_CRUNCH = "xp_crunch"
        private const val KEY_TRICEP_DIP = "xp_tricep_dip"
        private const val KEY_HIGH_KNEE = "xp_high_knee"
        private const val KEY_LEG_RAISE = "xp_leg_raise"
        private const val KEY_PLANK = "xp_plank"
        private const val KEY_MANUAL_REP = "xp_manual_rep"
        private const val KEY_MANUAL_DURATION = "xp_manual_duration"
        private const val KEY_MULTIPLIER = "xp_multiplier"
    }

    private fun loadRates(): XpRates {
        return XpRates(
            pushup = prefs.getFloat(KEY_PUSHUP, DEFAULT_RATES.pushup),
            squat = prefs.getFloat(KEY_SQUAT, DEFAULT_RATES.squat),
            bicepCurl = prefs.getFloat(KEY_BICEP, DEFAULT_RATES.bicepCurl),
            lunge = prefs.getFloat(KEY_LUNGE, DEFAULT_RATES.lunge),
            shoulderPress = prefs.getFloat(KEY_SHOULDER_PRESS, DEFAULT_RATES.shoulderPress),
            jumpingJack = prefs.getFloat(KEY_JUMPING_JACK, DEFAULT_RATES.jumpingJack),
            pullup = prefs.getFloat(KEY_PULLUP, DEFAULT_RATES.pullup),
            lateralRaise = prefs.getFloat(KEY_LATERAL_RAISE, DEFAULT_RATES.lateralRaise),
            gluteBridge = prefs.getFloat(KEY_GLUTE_BRIDGE, DEFAULT_RATES.gluteBridge),
            crunch = prefs.getFloat(KEY_CRUNCH, DEFAULT_RATES.crunch),
            tricepDip = prefs.getFloat(KEY_TRICEP_DIP, DEFAULT_RATES.tricepDip),
            highKnee = prefs.getFloat(KEY_HIGH_KNEE, DEFAULT_RATES.highKnee),
            legRaise = prefs.getFloat(KEY_LEG_RAISE, DEFAULT_RATES.legRaise),
            plank = prefs.getFloat(KEY_PLANK, DEFAULT_RATES.plank),
            manualRep = prefs.getFloat(KEY_MANUAL_REP, DEFAULT_RATES.manualRep),
            manualDurationPerMin = prefs.getFloat(KEY_MANUAL_DURATION, DEFAULT_RATES.manualDurationPerMin),
            multiplier = prefs.getFloat(KEY_MULTIPLIER, DEFAULT_RATES.multiplier)
        )
    }

    fun updateRates(newRates: XpRates) {
        prefs.edit()
            .putFloat(KEY_PUSHUP, newRates.pushup)
            .putFloat(KEY_SQUAT, newRates.squat)
            .putFloat(KEY_BICEP, newRates.bicepCurl)
            .putFloat(KEY_LUNGE, newRates.lunge)
            .putFloat(KEY_SHOULDER_PRESS, newRates.shoulderPress)
            .putFloat(KEY_JUMPING_JACK, newRates.jumpingJack)
            .putFloat(KEY_PULLUP, newRates.pullup)
            .putFloat(KEY_LATERAL_RAISE, newRates.lateralRaise)
            .putFloat(KEY_GLUTE_BRIDGE, newRates.gluteBridge)
            .putFloat(KEY_CRUNCH, newRates.crunch)
            .putFloat(KEY_TRICEP_DIP, newRates.tricepDip)
            .putFloat(KEY_HIGH_KNEE, newRates.highKnee)
            .putFloat(KEY_LEG_RAISE, newRates.legRaise)
            .putFloat(KEY_PLANK, newRates.plank)
            .putFloat(KEY_MANUAL_REP, newRates.manualRep)
            .putFloat(KEY_MANUAL_DURATION, newRates.manualDurationPerMin)
            .putFloat(KEY_MULTIPLIER, newRates.multiplier)
            .apply()
        _rates.value = newRates
    }

    fun resetToDefaults() {
        prefs.edit().clear().apply()
        _rates.value = DEFAULT_RATES
    }
}
