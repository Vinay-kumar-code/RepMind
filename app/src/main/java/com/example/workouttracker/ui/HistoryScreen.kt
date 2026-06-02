package com.example.workouttracker.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.Utils
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.clickable

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repo: SessionRepository, onBack: () -> Unit) {
    var allSessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    var selectedYear by remember { mutableStateOf(java.time.LocalDate.now().year) }
    var selectedMonth by remember { mutableStateOf(java.time.LocalDate.now().monthValue) }
    var expandedYear by remember { mutableStateOf(false) }
    var expandedMonth by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val sessions = withContext(Dispatchers.IO) { repo.getAllSessions() }
        allSessions = sessions.sortedByDescending { it.timestampIso }
        isLoading = false
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

            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (filteredSessions.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No workouts found for ${months[selectedMonth - 1]} $selectedYear", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(filteredSessions) { session ->
                        val name = Utils.capitalize(session.exercise.replace("_", " "))
                        val context = LocalContext.current
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { shareSessionImage(context, session) }
                        ) {
                            ListItem(
                                headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                                supportingContent = { 
                                    if (session.durationSeconds > 0) {
                                        Text("${(session.durationSeconds / 60).toInt()} mins • ${String.format(Locale.US, "%.2f", session.totalXp)} XP • ${session.timestampIso.take(16).replace("T", " ")}")
                                    } else {
                                        Text("${session.reps} reps • ${String.format(Locale.US, "%.2f", session.totalXp)} XP • ${session.timestampIso.take(16).replace("T", " ")}")
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
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
    
    val name = com.example.workouttracker.Utils.capitalize(session.exercise.replace("_", " "))
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
