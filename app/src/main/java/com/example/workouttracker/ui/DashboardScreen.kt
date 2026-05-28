package com.example.workouttracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.workouttracker.LevelSystem
import com.example.workouttracker.ProgressManager
import com.example.workouttracker.WorkoutEngine
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    repo: SessionRepository,
    engine: WorkoutEngine,
    onStartWorkout: () -> Unit,
    dailyState: ProgressManager.DailyState?,
    levelInfo: LevelSystem.LevelInfo,
    userName: String,
    onNavigateToSettings: () -> Unit,
    progressManager: ProgressManager
) {
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var weeklyProgress by remember { mutableStateOf<List<Float>>(List(7){0f}) }

    LaunchedEffect(dailyState) {
        withContext(Dispatchers.IO) {
            sessions = repo.getAllSessions().filter { it.exercise != "plank" }.sortedByDescending { it.timestampIso }.take(5)
            weeklyProgress = progressManager.getWeeklyProgress()
        }
    }

    GlassBackground {
        val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
        val textColor = if (isDark) Color.White else Color.Black
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 48.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Dashboard",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        Text(
                            text = "Track. Train. Transform.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFF8A00) // Accent color
                        )
                    }
                    GlassCard(modifier = Modifier.size(48.dp)) {
                        IconButton(onClick = onNavigateToSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings", tint = textColor)
                        }
                    }
                }
            }

            // Welcome Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFFFF8A00), Color(0xFFE52E71))
                            )
                        )
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Surface(
                                color = Color.Black.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    "Welcome back \uD83D\uDC4B",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (userName.isBlank()) "Hi, User" else "Hi, ${userName.split(' ').first()}",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Level ${levelInfo.level} • ${levelInfo.rank}", color = Color.White.copy(alpha=0.9f), fontSize=14.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Current: ${com.example.workouttracker.Utils.capitalize(engine.getExerciseType().name.replace("_"," ").lowercase())}", color = Color(0xFFFFE082), fontSize=14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        
                        // Star Badge
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color.White.copy(alpha=0.15f), shape = RoundedCornerShape(20.dp))
                                .border(1.dp, Color.White.copy(alpha=0.3f), RoundedCornerShape(20.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("⭐", fontSize = 36.sp)
                        }
                    }
                }
            }

            // Daily Goal Card
            item {
                GlassCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Daily Goal", color = textColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Surface(color = textColor.copy(alpha=0.1f), shape = RoundedCornerShape(8.dp)) {
                                Text("📅 Today", color = textColor.copy(alpha=0.8f), fontSize=12.sp, modifier = Modifier.padding(horizontal=8.dp, vertical=4.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            // Circular Progress
                            val pushPct = dailyState?.let { it.pushups.toFloat() / it.goals.push.coerceAtLeast(1) } ?: 0f
                            val squatPct = dailyState?.let { it.squats.toFloat() / it.goals.squat.coerceAtLeast(1) } ?: 0f
                            val bicepTotal = dailyState?.let { it.bicepLeft + it.bicepRight } ?: 0
                            val bicepPct = dailyState?.let { bicepTotal.toFloat() / it.goals.bicep.coerceAtLeast(1) } ?: 0f
                            val overall = listOf(pushPct, squatPct, bicepPct).average().toFloat().coerceIn(0f, 1f)

                            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawArc(
                                        color = Color.White.copy(alpha=0.1f),
                                        startAngle = -90f,
                                        sweepAngle = 360f,
                                        useCenter = false,
                                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                                    )
                                    drawArc(
                                        brush = Brush.sweepGradient(listOf(Color(0xFFFF8A00), Color(0xFFE52E71))),
                                        startAngle = -90f,
                                        sweepAngle = 360f * overall,
                                        useCenter = false,
                                        style = Stroke(width = 24f, cap = StrokeCap.Round)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${(overall * 100).toInt()}%", color = textColor, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                    Text("Overall", color = textColor.copy(alpha=0.7f), fontSize = 10.sp)
                                }
                            }

                            // Individual Bars
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                GoalBar("Pushups", dailyState?.pushups ?: 0, dailyState?.goals?.push ?: 0, Color(0xFF9575CD))
                                GoalBar("Squats", dailyState?.squats ?: 0, dailyState?.goals?.squat ?: 0, Color(0xFF4FC3F7))
                                GoalBar("Bicep Curls", bicepTotal, dailyState?.goals?.bicep ?: 0, Color(0xFFFFD54F))
                            }
                        }
                    }
                }
            }

            // Streak & Weekly Row
            item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Streak Card
                    GlassCard(modifier = Modifier.weight(1f).height(140.dp)) {
                        Row(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Box(modifier = Modifier.size(48.dp).background(textColor.copy(alpha=0.1f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                Text("🔥", fontSize = 24.sp)
                            }
                            Column {
                                Text("${dailyState?.streak ?: 0}-day streak", color = textColor, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Complete today's goals to start a streak.", color = textColor.copy(alpha=0.6f), fontSize = 11.sp, lineHeight = 14.sp)
                            }
                        }
                    }

                    // Weekly Progress
                    GlassCard(modifier = Modifier.weight(1f).height(140.dp)) {
                        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                            Text("Weekly Progress", color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(modifier = Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                                val todayIdx = LocalDate.now().dayOfWeek.value - 1
                                
                                weeklyProgress.forEachIndexed { index, pct ->
                                    val isToday = index == todayIdx
                                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(48.dp)
                                                .background(textColor.copy(alpha=0.1f), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.BottomCenter
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .fillMaxHeight(pct)
                                                    .background(
                                                        if (isToday) Brush.verticalGradient(listOf(Color(0xFFFF8A00), Color(0xFFE52E71))) 
                                                        else Brush.verticalGradient(listOf(Color(0xFF9575CD), Color(0xFF5E35B1))), 
                                                        RoundedCornerShape(6.dp)
                                                    )
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Text(labels[index], color = textColor.copy(alpha=0.6f), fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Consistency Motivation Card
            item {
                MotivationCard()
            }

            // Start Workout Button
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(Brush.horizontalGradient(listOf(Color(0xFFFF8A00), Color(0xFFE52E71))))
                        .clickable { onStartWorkout() },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(modifier = Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(48.dp).background(Color.White.copy(alpha=0.2f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Start Workout", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Let's get stronger today!", color = Color.White.copy(alpha=0.8f), fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GoalBar(label: String, value: Int, goal: Int, color: Color) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
    val textColor = if (isDark) Color.White else Color.Black

    val pct = if (goal > 0) (value.toFloat() / goal).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = textColor.copy(alpha=0.9f), fontSize = 12.sp)
            Text("$value / $goal", color = textColor.copy(alpha=0.7f), fontSize = 12.sp)
        }
        Box(modifier = Modifier.fillMaxWidth().height(6.dp).background(textColor.copy(alpha=0.1f), CircleShape)) {
            Box(modifier = Modifier.fillMaxWidth(pct).fillMaxHeight().background(color, CircleShape))
        }
    }
}
