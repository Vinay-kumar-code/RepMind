package com.example.workouttracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repo: SessionRepository,
    onClose: () -> Unit
) {
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var totalStats by remember { mutableStateOf(Triple(0, 0, 0)) } // sessions, reps, xp

    LaunchedEffect(Unit) {
        try {
            val allSessions = withContext(Dispatchers.IO) {
                repo.getAllSessions()
            }
            // Sort by timestamp descending (most recent first)
            val sorted = allSessions.sortedByDescending { parseSessionEpoch(it.timestampIso) }
            sessions = sorted

            // Calculate totals
            val totalSessions = sorted.size
            val totalReps = sorted.sumOf { it.reps }
            val totalXp = sorted.sumOf { it.totalXp }
            totalStats = Triple(totalSessions, totalReps, totalXp)
            
        } catch (e: Exception) {
            // Handle error silently
        } finally {
            isLoading = false
        }
    }

    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Workout History",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = onClose) {
                    Text("Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stats summary
            if (!isLoading) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        StatItem("Sessions", totalStats.first.toString())
                        StatItem("Total Reps", totalStats.second.toString())
                        StatItem("Total XP", totalStats.third.toString())
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Session list
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (sessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No workout sessions yet.\nStart your first workout!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sessions) { session ->
                        SessionCard(session = session)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SessionCard(session: SessionEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = session.exercise.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatTimestamp(session.timestampIso),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Reps: ${session.reps}")
                Text(text = "XP: ${session.totalXp}")
                Text(text = "Duration: ${String.format("%.1f", session.durationSeconds)}s")
            }
        }
    }
}

private fun formatTimestamp(timestampIso: String): String {
    return try {
        val instant = Instant.parse(timestampIso)
        val localDateTime = instant.atZone(ZoneId.systemDefault()).toLocalDateTime()
        val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        localDateTime.format(formatter)
    } catch (e: Exception) {
        timestampIso.take(10) // Fallback to date part
    }
}

private fun parseSessionEpoch(ts: String): Long = try {
    try { OffsetDateTime.parse(ts).toInstant().toEpochMilli() } catch (_: DateTimeParseException) { Instant.parse(ts).toEpochMilli() }
} catch (_: Exception) { 0L }
