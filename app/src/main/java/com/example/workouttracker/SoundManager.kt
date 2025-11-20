package com.example.workouttracker

import android.media.AudioManager
import android.media.ToneGenerator

class SoundManager {
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

    fun playRepSound() {
        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
    }

    fun playMilestoneSound() {
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
    }

    fun release() {
        toneGenerator.release()
    }
}
