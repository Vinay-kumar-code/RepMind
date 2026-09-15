package com.example.workouttracker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.workouttracker.alarm.HardAlarmManager

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            // 1. Reschedule Hard Alarm if enabled
            try {
                val alarmManager = HardAlarmManager(context)
                val config = alarmManager.config.value
                if (config.enabled) {
                    alarmManager.scheduleNextAlarm(config.hour, config.minute)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Reschedule Nightly Step Sync
            try {
                StepSyncScheduler.scheduleDailySync(context)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
