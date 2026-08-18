package com.example.workouttracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Calendar

object StepSyncScheduler {
    const val ACTION_NIGHTLY_STEP_SYNC = "com.example.workouttracker.action.NIGHTLY_STEP_SYNC"
    private const val REQUEST_CODE = 3001

    fun scheduleDailySync(context: Context) {
        try {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, StepSyncReceiver::class.java).apply {
                action = ACTION_NIGHTLY_STEP_SYNC
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Target: 23:50:00 (11:50 PM) in Asia/Kolkata or local zone
            val zone = ZoneId.of("Asia/Kolkata")
            val now = ZonedDateTime.now(zone)
            var target = now.with(LocalTime.of(23, 50, 0, 0))
            if (now.isAfter(target)) {
                target = target.plusDays(1)
            }

            val triggerMillis = target.toInstant().toEpochMilli()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerMillis,
                    pendingIntent
                )
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }
}
