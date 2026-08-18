package com.example.workouttracker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.workouttracker.db.SessionDatabase
import com.example.workouttracker.db.SessionRepository
import com.example.workouttracker.ui.WorkoutApp
import com.example.workouttracker.ui.theme.WorkoutTrackerTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            val stackTrace = android.util.Log.getStackTraceString(exception)
            android.util.Log.e("RepMindCrash", "Uncaught exception", exception)
            
            val intent = android.content.Intent(this, CrashActivity::class.java).apply {
                putExtra("EXTRA_STACK_TRACE", stackTrace)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)
            
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        try {
            enableEdgeToEdge()
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        super.onCreate(savedInstanceState)

        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }

        val db = try {
            SessionDatabase.getInstance(applicationContext)
        } catch (t: Throwable) {
            t.printStackTrace()
            null
        }
        val themePrefs = ThemePreferences(applicationContext)

        setContent {
            val themeMode by themePrefs.themeMode.collectAsState()
            val useMaterialYou by themePrefs.useMaterialYou.collectAsState()
            
            val darkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            WorkoutTrackerTheme(darkTheme = darkTheme, dynamicColor = useMaterialYou) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (db != null) {
                        val repo = remember { SessionRepository(db.sessionDao()) }
                        WorkoutApp(repo = repo, themePrefs = themePrefs)
                    }
                }
            }
        }
    }
}
