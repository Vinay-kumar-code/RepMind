package com.example.workouttracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.workouttracker.LevelSystem
import com.example.workouttracker.ProgressManager
import com.example.workouttracker.WorkoutEngine
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    repo: SessionRepository,
    engine: WorkoutEngine,
    onStartWorkout: () -> Unit,
    dailyState: ProgressManager.DailyState?,
    levelInfo: LevelSystem.LevelInfo,
    userName: String
) {
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { sessions = repo.getAllSessions().filter { it.exercise != "plank" }.sortedByDescending { it.timestampIso }.take(10) }
    }
    val listState = rememberLazyListState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("Dashboard") },
                actions = { IconButton(onClick = { /* settings placeholder */ }) { Icon(Icons.Default.Settings, contentDescription = "Settings") } },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Start Workout") },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                onClick = onStartWorkout
            )
        }
    ) { pad ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { GreetingHeader(userName, levelInfo, engine) }
            item { DailyGoalSection(dailyState) }
            item { StreakCard(dailyState?.streak ?: 0) }
            if (sessions.isNotEmpty()) {
                stickyHeader { Surface(color = MaterialTheme.colorScheme.background) { Text("Recent Sessions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top=8.dp, bottom=4.dp)) } }
                items(sessions) { s -> SessionListItem(s) }
            }
        }
    }
}

@Composable private fun GreetingHeader(name: String, info: LevelSystem.LevelInfo, engine: WorkoutEngine? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = if (name.isBlank()) "Welcome" else "Hi, ${name.split(' ').first()}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Level ${info.level} • ${info.rank}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        engine?.let { Text("Current: ${it.getExerciseType().name.lowercase().replaceFirstChar{c->c.uppercase()}}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
    }
}

@Composable private fun DailyGoalSection(daily: ProgressManager.DailyState?) {
    val pushPct = daily?.let { it.pushups.toFloat()/ it.goals.push } ?: 0f
    val squatPct = daily?.let { it.squats.toFloat()/ it.goals.squat } ?: 0f
    val bicepTotal = daily?.let { it.bicepLeft + it.bicepRight } ?: 0
    val bicepPct = daily?.let { bicepTotal.toFloat()/ it.goals.bicep } ?: 0f
    val overall = listOf(pushPct, squatPct, bicepPct).average().toFloat().coerceIn(0f,1f)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Daily Goal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        LinearProgressIndicator(progress = overall, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant)
        Text("${(overall*100).toInt()}% overall", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GoalLine("Pushups", daily?.pushups ?: 0, daily?.goals?.push ?: 0, MaterialTheme.colorScheme.primary)
        GoalLine("Squats", daily?.squats ?: 0, daily?.goals?.squat ?: 0, MaterialTheme.colorScheme.secondary)
        GoalLine("Bicep Curls", bicepTotal, daily?.goals?.bicep ?: 60, MaterialTheme.colorScheme.tertiary)
    }
}

@Composable private fun GoalLine(label: String, value: Int, goal: Int, color: Color) {
    val pct = if (goal>0) (value.toFloat()/goal).coerceIn(0f,1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text("$value / $goal", style = MaterialTheme.typography.labelSmall)
        }
        LinearProgressIndicator(progress = pct, modifier = Modifier.fillMaxWidth(), trackColor = MaterialTheme.colorScheme.surfaceVariant, color = color)
    }
}

@Composable private fun StreakCard(streak: Int) {
    val bg = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    Surface(shape = RoundedCornerShape(40.dp), color = bg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("${streak}-day streak", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(if (streak>0) "You're on a roll! Keep it up." else "Complete today's goals to start a streak.", style = MaterialTheme.typography.bodySmall) }
            Text("🔥", style = MaterialTheme.typography.headlineMedium)
        }
    }
}

@Composable private fun SessionListItem(s: SessionEntity) {
    ListItem(headlineContent = { Text("${s.exercise}  ${s.reps} reps  XP ${s.totalXp}") }, supportingContent = { Text(s.timestampIso.replace("T"," ").take(19)) })
    Divider()
}
