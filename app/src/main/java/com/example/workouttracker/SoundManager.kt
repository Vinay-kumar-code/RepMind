package com.example.workouttracker

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {
    private val toneGenerator: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_MUSIC, 100)
    } catch (e: Exception) {
        null
    }

    fun playRepSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
        } catch (_: Exception) {}
    }
    
    fun playGoodFormSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_PROP_ACK, 100)
        } catch (_: Exception) {}
    }

    fun playBadFormSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 200)
        } catch (_: Exception) {}
    }

    fun playMilestoneSound() {
        try {
            toneGenerator?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
        } catch (_: Exception) {}
    }

    fun release() {
        try {
            toneGenerator?.release()
        } catch (_: Exception) {}
    }
}
