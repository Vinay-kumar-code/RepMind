package com.example.workouttracker.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.camera.core.CameraSelector
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
    var xp by remember { mutableStateOf(0f) }
    var sessionStartMs by remember { mutableStateOf(0L) }
    var isSessionActive by remember { mutableStateOf(false) }
    var exercise by remember { mutableStateOf(engine.getExerciseType()) }
    var showHistory by remember { mutableStateOf(false) }
    var showExerciseSheet by remember { mutableStateOf(false) }
    
    // Live feedback
    var feedback by remember { mutableStateOf(engine.getLastFeedback()) }
    var lastReps by remember { mutableStateOf(0) }
    var bestSet by remember { mutableStateOf(0) }

    var autoMode by remember { mutableStateOf(false) }
    var lastRepChangeTime by remember { mutableStateOf(System.currentTimeMillis()) }

    LaunchedEffect(exercise) {
        bestSet = repo.getBestSetForExercise(exercise.name.lowercase())
    }

    LaunchedEffect(Unit) {
        while (true) {
            val currentReps = engine.getReps()
            xp = engine.getTotalXp()
            feedback = engine.getLastFeedback()
            
            // Sound Logic
            if (currentReps > lastReps) {
                if (exercise == ExerciseType.PULLUP) {
                    if (feedback.rangeOk) soundManager.playGoodFormSound()
                    else soundManager.playBadFormSound()
                } else {
                    val isMilestone = (currentReps % 10 == 0)
                    if (isMilestone) soundManager.playMilestoneSound()
                }
                lastRepChangeTime = System.currentTimeMillis()
                
                // Auto Start
                if (autoMode && !isSessionActive) {
                    isSessionActive = true
                    sessionStartMs = System.currentTimeMillis()
                }
                
                lastReps = currentReps
            }
            
            // Auto Save Logic (30s inactivity)
            if (autoMode && isSessionActive && reps > 0 && (System.currentTimeMillis() - lastRepChangeTime > 30000)) {
                saveSession(repo, reps, xp, sessionStartMs, exercise)
                engine.reset()
                reps = 0; xp = 0f; lastReps = 0; isSessionActive = false; sessionStartMs = 0L
                bestSet = repo.getBestSetForExercise(exercise.name.lowercase())
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
        xp = 0f
        lastReps = 0
        isSessionActive = false
        sessionStartMs = 0L
    }
    
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
    val textColor = if (isDark) Color.White else Color.Black

    GlassBackground(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 16.dp, start = 16.dp, end = 16.dp), // Adjust top padding for edge-to-edge
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top Bar
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back Button
                GlassCard(modifier = Modifier.size(48.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.fillMaxSize()) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = textColor)
                    }
                }

                // Exercise Selector Button (Gradient Pill)
                val gradientBrush = Brush.horizontalGradient(
                    colors = listOf(Color(0xFFE94057), Color(0xFF8A2387))
                )
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(gradientBrush)
                        .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                ) {
                    TextButton(onClick = { showExerciseSheet = true }) {
                        Text(
                            exercise.name.replace("_", " "), 
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Icon(Icons.Default.ArrowDropDown, null, tint = Color.White)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassCard(modifier = Modifier.size(48.dp)) {
                        IconButton(onClick = { showHistory = true }, modifier = Modifier.fillMaxSize()) {
                            Icon(Icons.Default.History, "History", tint = textColor)
                        }
                    }
                    GlassCard(modifier = Modifier.size(48.dp)) {
                        IconToggleButton(
                            checked = autoMode,
                            onCheckedChange = { autoMode = it },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text(
                                "A", 
                                fontWeight = FontWeight.Bold, 
                                color = if (autoMode) MaterialTheme.colorScheme.primary else textColor
                            )
                        }
                    }
                }
            }

            // Camera Preview Area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f) // Takes up available upper space
                    .padding(bottom = 16.dp)
                    .shadow(16.dp, RoundedCornerShape(24.dp))
                    .clip(RoundedCornerShape(24.dp))
                    .border(2.dp, Color(0xFF8A2387).copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                val camSelector = if (exercise == ExerciseType.PULLUP) CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA
                key(camSelector) {
                    PreviewCameraView(
                        engine = engine,
                        modifier = Modifier.fillMaxSize(),
                        performanceSettings = performanceSettings,
                        showLandmarks = performanceSettings.showLandmarks,
                        cameraSelector = camSelector
                    )
                }
                
                // Top floating pills inside camera
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("⛶ Frame your body", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp)
                    }
                    Surface(
                        color = Color.Black.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text("☀ Good lighting", modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, fontSize = 12.sp)
                    }
                }

            // Center HUD: Rep Counter & Form Arc inside camera
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    val isPlank = exercise == ExerciseType.PLANK
                    val depth = if (isPlank) (feedback.formScore / 100f).coerceIn(0f, 1f) else (feedback.depthPercent / 100f).coerceIn(0f, 1f)
                    val isHoldingPlank = feedback.stage == "holding"
                    val rangeOk = if (isPlank) isHoldingPlank else feedback.rangeOk
                    val color = when {
                        isPlank && isHoldingPlank -> Color(0xFF00E676)
                        isPlank && feedback.stage == "misaligned" -> Color(0xFFFF9100)
                        rangeOk -> Color.Green
                        else -> Color.Yellow
                    }
                    
                    Canvas(modifier = Modifier.size(200.dp)) {
                        drawArc(
                            color = Color.White.copy(alpha = 0.3f),
                            startAngle = 135f,
                            sweepAngle = 270f,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                        drawArc(
                            color = color,
                            startAngle = 135f,
                            sweepAngle = 270f * depth,
                            useCenter = false,
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 16f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (isPlank) "${reps}s" else "$reps",
                            style = MaterialTheme.typography.displayLarge.copy(
                                fontSize = if (isPlank && reps >= 100) 56.sp else 72.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
                        Text(
                            text = if (isPlank) "HOLD TIME" else "REPS",
                            style = MaterialTheme.typography.labelLarge.copy(color = Color.White.copy(alpha = 0.8f))
                        )
                    }
                }
            }

            // Middle section (Go Deeper & Subtitle / Plank Form)
            val isPlank = exercise == ExerciseType.PLANK
            val isHoldingPlank = feedback.stage == "holding"
            val pillColor = when {
                isPlank && isHoldingPlank -> Color(0xFF4CAF50)
                isPlank && feedback.stage == "misaligned" -> Color(0xFFFF7043)
                isPlank -> Color(0xFF8A2387)
                feedback.rangeOk -> Color(0xFF4CAF50)
                else -> Color(0xFF8A2387)
            }
            val pillText = when {
                isPlank && isHoldingPlank -> "Holding Plank (${feedback.formScore}%)"
                isPlank && feedback.stage == "misaligned" -> "Align Body Straight!"
                isPlank -> "Get in Plank Position"
                feedback.rangeOk -> "Good Form!"
                else -> "Go Deeper!"
            }
            
            Box(
                modifier = Modifier
                    .offset(y = (-36).dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.horizontalGradient(listOf(pillColor.copy(alpha=0.8f), pillColor)))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = pillText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }

            Text(
                "Push your limits. Become stronger.",
                color = textColor.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.offset(y = (-16).dp)
            )

            // Streak & Best Set Cards
            Row(
                Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f).aspectRatio(1.2f)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2C1938),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🔥", fontSize = 20.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Streak", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("${dailyState?.streak ?: 0} days", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "Complete today's goals to build your streak.",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }

                GlassCard(modifier = Modifier.weight(1f).aspectRatio(1.2f)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF2C1938),
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("🏆", fontSize = 20.sp)
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Best Set", color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text(if (isPlank) "${bestSet}s" else "$bestSet reps", color = Color(0xFFA259FF), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.weight(1f))
                        Text(
                            "You've got this!\nBeat your best.",
                            color = textColor.copy(alpha = 0.6f),
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Bottom Controls
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reset
                GlassCard(modifier = Modifier.size(72.dp)) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        IconButton(
                            onClick = { engine.reset(); reps = 0; xp = 0f; lastReps = 0; isSessionActive = false; sessionStartMs = 0L },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, "Reset", tint = textColor)
                        }
                        Text("Reset", color = textColor, fontSize = 10.sp)
                    }
                }

                // Play/Pause/Save
                val playActionIcon = if (isSessionActive && reps > 0) Icons.Default.Save else if (isSessionActive) Icons.Default.Pause else Icons.Default.PlayArrow
                
                Box(
                    modifier = Modifier
                        .size(96.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFE94057), Color(0xFF8A2387))))
                        .border(1.dp, Color.White.copy(alpha=0.3f), RoundedCornerShape(32.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (!isSessionActive) {
                                sessionStartMs = System.currentTimeMillis()
                                isSessionActive = true
                            } else if (reps > 0) {
                                saveSession(repo, reps, xp, sessionStartMs, exercise)
                                engine.reset()
                                reps = 0; xp = 0f; lastReps = 0; isSessionActive = false; sessionStartMs = 0L
                            } else {
                                isSessionActive = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(playActionIcon, "Action", tint = Color.White, modifier = Modifier.size(48.dp))
                    }
                }
                
                // XP
                GlassCard(modifier = Modifier.size(72.dp)) {
                    Column(
                        Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("XP", color = textColor.copy(alpha=0.7f), fontSize = 10.sp)
                        Text(
                            String.format(java.util.Locale.US, "%.2f", xp), 
                            color = Color(0xFFFF7043), 
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
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
                        "Upper Body" to listOf(ExerciseType.SHOULDER_PRESS, ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT, ExerciseType.TRICEP_DIPS, ExerciseType.LATERAL_RAISES, ExerciseType.PULLUP),
                        "Core & Abs" to listOf(ExerciseType.PLANK, ExerciseType.CRUNCHES, ExerciseType.LEG_RAISES),
                        "Cardio & Agility" to listOf(ExerciseType.JUMPING_JACKS, ExerciseType.HIGH_KNEES)
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
                HistoryScreen(repo = repo, onBack = { showHistory = false })
            }
        }
    }
}

private fun saveSession(
    repo: SessionRepository,
    reps: Int,
    xp: Float,
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
