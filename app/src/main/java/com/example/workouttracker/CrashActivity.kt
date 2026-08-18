package com.example.workouttracker

import android.app.Activity
import android.os.Bundle
import android.widget.ScrollView
import android.widget.TextView

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val stackTrace = intent.getStringExtra("EXTRA_STACK_TRACE") ?: "No stack trace"
        
        val textView = TextView(this).apply {
            text = "App Crashed!\n\nPlease share this log:\n\n$stackTrace"
            setPadding(32, 32, 32, 32)
            textSize = 12f
        }
        
        val scrollView = ScrollView(this).apply {
            addView(textView)
        }
        
        setContentView(scrollView)
    }
}
