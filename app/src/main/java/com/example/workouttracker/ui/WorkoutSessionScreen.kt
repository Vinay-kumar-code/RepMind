package com.example.workouttracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workouttracker.SoundManager
import com.example.workouttracker.WorkoutEngine
import com.example.workouttracker.WorkoutEngine.ExerciseType
import com.example.workouttracker.LevelSystem
import com.example.workouttracker.ProgressManager
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WorkoutSessionScreen(
    repo: SessionRepository,
    onBack: () -> Unit,
    engine: WorkoutEngine,
    performanceSettings: PerformanceSettings,
    levelInfo: LevelSystem.LevelInfo,
    dailyState: ProgressManager.DailyState?,
    onExerciseChange: (ExerciseType) -> Unit
) {
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    val soundManager = remember { SoundManager() }
    DisposableEffect(Unit) {
        onDispose { soundManager.release() }
    }

    var reps by remember { mutableStateOf(0) }
    var xp by remember { mutableStateOf(0) }
    var sessionStartMs by remember { mutableStateOf(0L) }
    var isSessionActive by remember { mutableStateOf(false) }
    var exercise by remember { mutableStateOf(engine.getExerciseType()) }
    var showHistory by remember { mutableStateOf(false) }
    var showExerciseSheet by remember { mutableStateOf(false) }
    
    // Live feedback
    var feedback by remember { mutableStateOf(engine.getLastFeedback()) }
    var lastReps by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val currentReps = engine.getReps()
            xp = engine.getTotalXp()
            feedback = engine.getLastFeedback()
            
            // Sound Logic
            if (currentReps > lastReps) {
                val isMilestone = (currentReps % 10 == 0)
                if (isMilestone) soundManager.playMilestoneSound()
                lastReps = currentReps
            }
            reps = currentReps
            delay(50) // Faster polling for smooth UI
        }
    }

    fun applyExercise(newType: ExerciseType) {
        exercise = newType
        onExerciseChange(newType)
        engine.reset()
        reps = 0
        xp = 0
        lastReps = 0
        isSessionActive = false
        sessionStartMs = 0L
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // 1. Camera Preview (Full Screen)
        PreviewCameraView(
            engine = engine,
            modifier = Modifier.fillMaxSize(),
            performanceSettings = performanceSettings,
            showLandmarks = performanceSettings.showLandmarks
        )

        // 2. HUD Overlay
        Column(
            Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top Bar
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                }

                // Exercise Selector Button
                FilledTonalButton(
                    onClick = { showExerciseSheet = true },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
                    )
                ) {
                    Text(exercise.name.replace("_", " "), style = MaterialTheme.typography.titleMedium)
                    Icon(Icons.Default.ArrowDropDown, null)
                }

                IconButton(
                    onClick = { showHistory = true },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = Color.Black.copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Default.History, "History", tint = Color.White)
                }
            }

            Spacer(Modifier.weight(1f))

            // Center HUD: Rep Counter & Form Arc
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Form Arc (Canvas)
                val depth = feedback.depthPercent / 100f
                val rangeOk = feedback.rangeOk
                val color = if (rangeOk) Color.Green else Color.Yellow
                
                Canvas(modifier = Modifier.size(200.dp)) {
                    drawArc(
                        color = Color.White.copy(alpha = 0.3f),
                        startAngle = 135f,
                        sweepAngle = 270f,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 20f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                    drawArc(
                        color = color,
                        startAngle = 135f,
                        sweepAngle = 270f * depth,
                        useCenter = false,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 20f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$reps",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontSize = 80.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                    Text(
                        text = "REPS",
                        style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.8f))
                    )
                }
            }
            
            // Feedback Text
            AnimatedVisibility(
                visible = feedback.stage == "down" || !feedback.rangeOk,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Surface(
                    color = if (feedback.rangeOk) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text(
                        text = if (feedback.rangeOk) "Good Depth!" else "Go Deeper!",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom Controls
            Row(
                Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset
                IconButton(
                    onClick = { engine.reset(); reps = 0; xp = 0; lastReps = 0; isSessionActive = false; sessionStartMs = 0L },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Default.Refresh, "Reset")
                }

                // Play/Pause/Save
                FloatingActionButton(
                    onClick = {
                        if (!isSessionActive) {
                            sessionStartMs = System.currentTimeMillis()
                            isSessionActive = true
                        } else if (reps > 0) {
                            saveSession(repo, reps, xp, sessionStartMs, exercise)
                            engine.reset()
                            reps = 0; xp = 0; lastReps = 0; isSessionActive = false; sessionStartMs = 0L
                        } else {
                            isSessionActive = false // Pause? Or just stop empty session
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(72.dp)
                ) {
                    Icon(
                        imageVector = if (isSessionActive && reps > 0) Icons.Default.Save else if (isSessionActive) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Action",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                // Stats Mini-View
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("XP", style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Text("$xp", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Exercise Selection Sheet
        if (showExerciseSheet) {
            ModalBottomSheet(onDismissRequest = { showExerciseSheet = false }) {
                Column(Modifier.padding(16.dp).verticalScroll(rememberScrollState())) {
                    Text("Select Workout", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 16.dp))
                    
                    val categories = mapOf(
                        "Essentials" to listOf(ExerciseType.PUSHUP, ExerciseType.SQUAT, ExerciseType.LUNGES),
                        "Arms & Core" to listOf(ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT, ExerciseType.SHOULDER_PRESS),
                        "Flexibility & Cardio" to listOf(ExerciseType.JUMPING_JACKS)
                    )
                    
                    categories.forEach { (category, types) ->
                        Text(category, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(vertical = 8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            types.forEach { type ->
                                FilterChip(
                                    selected = exercise == type,
                                    onClick = { applyExercise(type); showExerciseSheet = false },
                                    label = { Text(com.example.workouttracker.Utils.capitalize(type.name.replace("_", " ").lowercase())) }
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }

        if (showHistory) {
            ModalBottomSheet(onDismissRequest = { showHistory = false }) {
                HistoryScreen(repo = repo, onClose = { showHistory = false })
            }
        }
    }
}

// Helper for capitalization
// fun String.capitalize() = replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

private fun saveSession(
    repo: SessionRepository,
    reps: Int,
    xp: Int,
    sessionStartMs: Long,
    exercise: ExerciseType
) {
    val istZone = ZoneId.of("Asia/Kolkata")
    val now = ZonedDateTime.now(istZone)
    val currentTs = now.toInstant().toEpochMilli()
    val durationSecs = if (sessionStartMs > 0) ((currentTs - sessionStartMs) / 1000f) else 0f
    val timestampIso = now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
    val session = com.example.workouttracker.db.SessionEntity(
        timestampIso = timestampIso,
        exercise = exercise.name.lowercase(),
        reps = reps,
        durationSeconds = durationSecs,
        totalXp = xp
    )
    CoroutineScope(Dispatchers.IO).launch { repo.insertSession(session) }
}
