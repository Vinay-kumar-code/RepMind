package com.example.workouttracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.example.workouttracker.LevelSystem
import com.example.workouttracker.ProgressManager
import com.example.workouttracker.Utils
import com.example.workouttracker.XpPreferences
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale

@Composable
fun WorkoutSourceBadge(isManual: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .background(
                color = if (isManual) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.primaryContainer,
                shape = CircleShape
            )
            .border(
                width = 1.5.dp,
                color = if (isManual) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.primary,
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (isManual) "M" else "A",
            color = if (isManual) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repo: SessionRepository,
    progressManager: ProgressManager? = null,
    xpPrefs: XpPreferences? = null,
    onDataChanged: (() -> Unit)? = null,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    
    var allSessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedYear by remember { mutableStateOf(java.time.LocalDate.now().year) }
    var selectedMonth by remember { mutableStateOf(java.time.LocalDate.now().monthValue) }
    var expandedYear by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }

    var editingSession by remember { mutableStateOf<SessionEntity?>(null) }
    var deletingSession by remember { mutableStateOf<SessionEntity?>(null) }

    fun refreshSessions() {
        coroutineScope.launch {
            val sessions = withContext(Dispatchers.IO) { repo.getAllSessions() }
            allSessions = sessions.filter { !it.exercise.startsWith("Achievement", ignoreCase = true) }
                .sortedByDescending { it.timestampIso }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshSessions()
    }

    val availableYears = remember(allSessions) {
        if (allSessions.isEmpty()) listOf(java.time.LocalDate.now().year)
        else {
            allSessions.mapNotNull {
                try {
                    val instant = try { OffsetDateTime.parse(it.timestampIso).toInstant() } catch (_: Exception) { Instant.parse(it.timestampIso) }
                    instant.atZone(ZoneId.systemDefault()).year
                } catch (e: Exception) { null }
            }.distinct().sortedDescending()
        }
    }

    val months = (1..12).map { java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault()) }

    val filteredSessions = remember(allSessions, selectedYear, selectedMonth) {
        allSessions.filter {
            try {
                val instant = try { OffsetDateTime.parse(it.timestampIso).toInstant() } catch (_: Exception) { Instant.parse(it.timestampIso) }
                val zdt = instant.atZone(ZoneId.systemDefault())
                zdt.year == selectedYear && zdt.monthValue == selectedMonth
            } catch (e: Exception) { false }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Full History") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Month Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedMonth = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(months[selectedMonth - 1])
                    }
                    DropdownMenu(expanded = expandedMonth, onDismissRequest = { expandedMonth = false }) {
                        months.forEachIndexed { index, monthName ->
                            DropdownMenuItem(text = { Text(monthName) }, onClick = { selectedMonth = index + 1; expandedMonth = false })
                        }
                    }
                }

                // Year Dropdown
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedYear = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("$selectedYear")
                    }
                    DropdownMenu(expanded = expandedYear, onDismissRequest = { expandedYear = false }) {
                        availableYears.forEach { year ->
                            DropdownMenuItem(text = { Text("$year") }, onClick = { selectedYear = year; expandedYear = false })
                        }
                    }
                }
            }

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkoutSourceBadge(isManual = false, modifier = Modifier.size(24.dp))
                    Text("Auto Tracked (A)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkoutSourceBadge(isManual = true, modifier = Modifier.size(24.dp))
                    Text("Manual Entry (M)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (filteredSessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No workouts found for ${months[selectedMonth - 1]} $selectedYear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(filteredSessions, key = { it.id }) { session ->
                        val name = Utils.capitalize(session.exercise.replace("_", " "))
                        Card(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            ListItem(
                                leadingContent = {
                                    WorkoutSourceBadge(isManual = session.isManual)
                                },
                                headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                                supportingContent = { 
                                    val durationMins = (session.durationSeconds / 60).toInt()
                                    val details = if (durationMins > 0 && session.reps > 0) {
                                        "${session.reps} reps • $durationMins mins • ${String.format(Locale.US, "%.2f", session.totalXp)} XP"
                                    } else if (durationMins > 0) {
                                        "$durationMins mins • ${String.format(Locale.US, "%.2f", session.totalXp)} XP"
                                    } else {
                                        "${session.reps} reps • ${String.format(Locale.US, "%.2f", session.totalXp)} XP"
                                    }
                                    val dateStr = session.timestampIso.take(16).replace("T", " ")
                                    Text("$details\n$dateStr")
                                },
                                trailingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { editingSession = session }) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Workout")
                                        }
                                        IconButton(onClick = { shareSessionImage(context, session) }) {
                                            Icon(Icons.Default.Share, contentDescription = "Share")
                                        }
                                        IconButton(onClick = { deletingSession = session }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Workout Dialog
    editingSession?.let { session ->
        EditWorkoutDialog(
            session = session,
            xpPrefs = xpPrefs,
            onDismiss = { editingSession = null },
            onSave = { updatedSession ->
                editingSession = null
                coroutineScope.launch(Dispatchers.IO) {
                    repo.updateSession(updatedSession)
                    
                    // Recalculate total XP
                    val all = repo.getAllSessions()
                    val totalXp = all.sumOf { it.totalXp.toDouble() }.toFloat()
                    val profile = repo.getProfile()
                    repo.upsertProfile(totalXp, profile?.name)
                    
                    // Recalculate daily progress
                    progressManager?.recalculateDailyProgress()
                    
                    withContext(Dispatchers.Main) {
                        refreshSessions()
                        onDataChanged?.invoke()
                        Toast.makeText(context, "Workout updated successfully", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    // Delete Confirmation Dialog
    deletingSession?.let { session ->
        AlertDialog(
            onDismissRequest = { deletingSession = null },
            title = { Text("Delete Workout") },
            text = { Text("Are you sure you want to delete this ${Utils.capitalize(session.exercise.replace("_", " "))} workout? This will recompute your XP and daily streak.") },
            confirmButton = {
                Button(
                    onClick = {
                        val idToDelete = session.id
                        deletingSession = null
                        coroutineScope.launch(Dispatchers.IO) {
                            repo.deleteSession(idToDelete)
                            
                            val all = repo.getAllSessions()
                            val totalXp = all.sumOf { it.totalXp.toDouble() }.toFloat()
                            val profile = repo.getProfile()
                            repo.upsertProfile(totalXp, profile?.name)
                            
                            progressManager?.recalculateDailyProgress()
                            
                            withContext(Dispatchers.Main) {
                                refreshSessions()
                                onDataChanged?.invoke()
                                Toast.makeText(context, "Workout deleted", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSession = null }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditWorkoutDialog(
    session: SessionEntity,
    xpPrefs: XpPreferences?,
    onDismiss: () -> Unit,
    onSave: (SessionEntity) -> Unit
) {
    var exerciseName by remember { mutableStateOf(session.exercise) }
    var repsText by remember { mutableStateOf(session.reps.toString()) }
    var durationMinsText by remember { mutableStateOf(((session.durationSeconds / 60).toInt()).toString()) }
    var xpText by remember { mutableStateOf(String.format(Locale.US, "%.2f", session.totalXp)) }
    var timestampIso by remember { mutableStateOf(session.timestampIso) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState()

    if (showDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { ms ->
                        val localDate = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()
                        // Keep current time if possible
                        try {
                            val odt = OffsetDateTime.parse(timestampIso)
                            val updatedOdt = odt.withYear(localDate.year).withMonth(localDate.monthValue).withDayOfMonth(localDate.dayOfMonth)
                            timestampIso = updatedOdt.format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        } catch (e: Exception) {
                            timestampIso = localDate.atStartOfDay(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                        }
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Workout") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = { exerciseName = it },
                    label = { Text("Exercise Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = repsText,
                        onValueChange = { repsText = it },
                        label = { Text("Reps") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = durationMinsText,
                        onValueChange = { durationMinsText = it },
                        label = { Text("Duration (mins)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = xpText,
                        onValueChange = { xpText = it },
                        label = { Text("XP Earned") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedButton(
                        onClick = {
                            val r = repsText.toIntOrNull() ?: 0
                            val d = durationMinsText.toIntOrNull() ?: 0
                            val currentRates = xpPrefs?.rates?.value
                            val calcXp = if (r > 0) {
                                LevelSystem.xpForStandardExercise(exerciseName, r, currentRates)
                            } else {
                                LevelSystem.xpForManualDuration(d, currentRates)
                            }
                            xpText = String.format(Locale.US, "%.2f", calcXp)
                        }
                    ) {
                        Text("Calc XP")
                    }
                }

                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Date: ${timestampIso.take(10)}")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalReps = repsText.toIntOrNull() ?: 0
                    val finalDurationMins = durationMinsText.toIntOrNull() ?: 0
                    val finalXp = xpText.toFloatOrNull() ?: session.totalXp
                    val updated = session.copy(
                        exercise = exerciseName.trim().ifBlank { session.exercise },
                        reps = finalReps,
                        durationSeconds = (finalDurationMins * 60).toFloat(),
                        totalXp = finalXp,
                        timestampIso = timestampIso
                    )
                    onSave(updated)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun shareSessionImage(context: Context, session: SessionEntity) {
    val width = 800
    val height = 600
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    canvas.drawColor(android.graphics.Color.parseColor("#121212"))
    
    val paint = Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 80f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }
    
    val name = Utils.capitalize(session.exercise.replace("_", " "))
    canvas.drawText("RepMind", width / 2f, 120f, paint)
    
    paint.textSize = 60f
    paint.color = android.graphics.Color.parseColor("#A259FF")
    canvas.drawText(name, width / 2f, 250f, paint)
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 50f
    canvas.drawText("${session.reps} Reps", width / 2f, 350f, paint)
    
    paint.textSize = 40f
    paint.color = android.graphics.Color.LTGRAY
    if (session.durationSeconds > 0) {
        canvas.drawText("Duration: ${(session.durationSeconds / 60).toInt()} mins", width / 2f, 430f, paint)
    }
    canvas.drawText("XP Earned: ${String.format(Locale.US, "%.2f", session.totalXp)}", width / 2f, 490f, paint)
    
    paint.textSize = 30f
    canvas.drawText(session.timestampIso.take(10), width / 2f, 560f, paint)
    
    try {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "workout_share.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Workout"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
