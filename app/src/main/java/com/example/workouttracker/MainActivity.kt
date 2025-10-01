package com.example.workouttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.workouttracker.db.SessionDatabase
import com.example.workouttracker.db.SessionRepository
import com.example.workouttracker.ui.WorkoutApp
import com.example.workouttracker.ui.theme.WorkoutTrackerTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // Handle permission denied
                finish()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        // Initialize DB (ensure created)
        val db = SessionDatabase.getInstance(applicationContext)

        setContent {
            WorkoutTrackerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val repo = remember { SessionRepository(db.sessionDao()) }
                    WorkoutApp(repo)
                }
            }
        }
    }
}
