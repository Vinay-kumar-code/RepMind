package com.example.workouttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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

@OptIn(ExperimentalMaterial3Api::class)
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
    // Keep screen on while this composable is in composition
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    var reps by remember { mutableStateOf(0) }
    var xp by remember { mutableStateOf(0) }
    var sessionStartMs by remember { mutableStateOf(0L) }
    var isSessionActive by remember { mutableStateOf(false) }
    var exercise by remember { mutableStateOf(engine.getExerciseType()) }
    var showHistory by remember { mutableStateOf(false) }
    val historySheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Live feedback from engine
    var feedback by remember { mutableStateOf(engine.getLastFeedback()) }

    LaunchedEffect(Unit) {
        while (true) {
            reps = engine.getReps()
            xp = engine.getTotalXp() // session integer XP
            feedback = engine.getLastFeedback()
            delay(250)
        }
    }

    fun applyExercise(newType: ExerciseType) {
        exercise = newType
        onExerciseChange(newType)
        engine.reset()
        reps = 0
        xp = 0
        isSessionActive = false
        sessionStartMs = 0L
    }

    // Approximate fractional XP progress (not yet granted to profile)
    val perRepXp = when (exercise) {
        ExerciseType.PUSHUP -> LevelSystem.xpPerPushup()
        ExerciseType.SQUAT -> LevelSystem.xpPerSquat()
        ExerciseType.BICEP_LEFT, ExerciseType.BICEP_RIGHT -> LevelSystem.xpPerBicepCurl()
    }
    val fractionalProgress = (reps * perRepXp) - xp

    // Replace BottomSheetScaffold floatingActionButton usage: first build sheet + preview inside Box
    Box(Modifier.fillMaxSize()) {
        val scaffoldState = rememberBottomSheetScaffoldState()
        BottomSheetScaffold(
            scaffoldState = scaffoldState,
            sheetPeekHeight = 140.dp,
            sheetDragHandle = { BottomSheetDefaults.DragHandle() },
            sheetContent = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Top Row: Back + Exercise selector + Reset + History
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledTonalButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("Back")
                        }
                        // Exercise selection chips
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExerciseType.values().forEach { type ->
                                FilterChip(
                                    selected = exercise == type,
                                    onClick = { applyExercise(type) },
                                    label = { Text(type.name.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                        IconButton(onClick = { engine.reset(); sessionStartMs = 0L; isSessionActive = false; reps = 0; xp = 0 }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset")
                        }
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = "History")
                        }
                    }
                    // Stats
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Reps", style = MaterialTheme.typography.labelSmall); Text(reps.toString(), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) }
                        Column { Text("XP", style = MaterialTheme.typography.labelSmall); Text(xp.toString(), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
                        Column { Text("Stage", style = MaterialTheme.typography.labelSmall); Text(feedback.stage, style = MaterialTheme.typography.titleMedium) }
                        Column { Text("Depth%", style = MaterialTheme.typography.labelSmall); Text(feedback.depthPercent.toString(), style = MaterialTheme.typography.titleMedium) }
                    }
                    // Show fractional XP progress if any
                    if (fractionalProgress > 0f) {
                        LinearProgressIndicator(
                            progress = (fractionalProgress / 1f).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("Next XP: ${(fractionalProgress * 100).toInt()}%", style = MaterialTheme.typography.labelSmall)
                    }
                    // Feedback chips
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AssistChip(onClick = {}, label = { Text("Amplitude ${feedback.amplitudeAngleDelta.toInt()}°") })
                        AssistChip(onClick = {}, label = { Text(if (feedback.rangeOk) "Range OK" else "More Depth") }, colors = AssistChipDefaults.assistChipColors())
                        if (feedback.repImminent) {
                            AssistChip(onClick = {}, label = { Text("Rep...") })
                        }
                    }
                    // Daily goals inline (compact)
                    dailyState?.let { ds ->
                        LinearProgressIndicator(
                            progress = (ds.pushups.toFloat() / ds.goals.push).coerceIn(0f,1f),
                            modifier = Modifier.fillMaxWidth(),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Daily P ${ds.pushups}/${ds.goals.push}  S ${ds.squats}/${ds.goals.squat}  B ${(ds.bicepLeft+ds.bicepRight)}/${ds.goals.bicep}",
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            },
        ) { inner ->
            // Camera preview background
            PreviewCameraView(
                engine = engine,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(inner),
                performanceSettings = performanceSettings
            )
        }

        // Overlay FAB bottom end
        ExtendedFloatingActionButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 180.dp), // above sheet
            onClick = {
                if (!isSessionActive) {
                    sessionStartMs = System.currentTimeMillis(); isSessionActive = true
                } else if (reps > 0) {
                    saveSession(repo, reps, xp, sessionStartMs, exercise)
                    engine.reset(); sessionStartMs = 0L; isSessionActive = false; reps = 0; xp = 0
                }
            },
            icon = {
                Icon(
                    when {
                        !isSessionActive -> Icons.Default.PlayArrow
                        isSessionActive && reps > 0 -> Icons.Default.Save
                        else -> Icons.Default.Pause
                    }, contentDescription = null
                )
            },
            text = { Text(
                when {
                    !isSessionActive -> "Start"
                    isSessionActive && reps > 0 -> "Save"
                    else -> "Active"
                }
            ) }
        )

        if (showHistory) {
            ModalBottomSheet(
                onDismissRequest = { showHistory = false },
                sheetState = historySheetState,
                windowInsets = WindowInsets(0,0,0,0)
            ) {
                HistoryScreen(repo = repo, onClose = { showHistory = false })
            }
        }
    }
}

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
        exercise = when (exercise) {
            ExerciseType.PUSHUP -> "pushups"
            ExerciseType.SQUAT -> "squats"
            ExerciseType.BICEP_LEFT -> "bicep_left"
            ExerciseType.BICEP_RIGHT -> "bicep_right"
        },
        reps = reps,
        durationSeconds = durationSecs,
        totalXp = xp
    )
    CoroutineScope(Dispatchers.IO).launch { repo.insertSession(session) }
}
