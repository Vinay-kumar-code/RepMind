package com.example.workouttracker.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.workouttracker.MainActivity

class HardAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val manager = HardAlarmManager(context)
        val config = manager.config.value

        if (!config.enabled && intent.action == HardAlarmManager.ACTION_ALARM_TRIGGER) {
            return
        }

        // Set ringing state and timestamp
        val triggerTs = System.currentTimeMillis()
        manager.setRingingState(true, triggerTs)

        // Start Foreground Alarm Service
        val serviceIntent = Intent(context, HardAlarmService::class.java).apply {
            action = HardAlarmService.ACTION_START_ALARM
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            ContextCompat.startForegroundService(context, serviceIntent)
        } else {
            context.startService(serviceIntent)
        }

        // Try to launch MainActivity with full screen intent
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("HARD_ALARM_RINGING", true)
        }
        context.startActivity(launchIntent)
    }
}
