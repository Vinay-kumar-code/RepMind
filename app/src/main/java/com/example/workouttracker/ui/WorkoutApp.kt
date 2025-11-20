package com.example.workouttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.workouttracker.*
import com.example.workouttracker.WorkoutEngine.ExerciseType
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.*
import java.time.format.TextStyle
import java.util.Locale

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutApp(repo: SessionRepository) {
    val nav = rememberNavController()
    val appScope = remember { CoroutineScope(Dispatchers.Main) }
    val context = LocalContext.current

    val progressManager = remember { ProgressManager(repo, appScope) }
    val dailyState by progressManager.dailyState.collectAsState()

    var profileXp by remember { mutableStateOf(0) }
    var levelInfo by remember { mutableStateOf(LevelSystem.levelFromXp(0)) }
    var showLevelUp by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Settings State
    var showLandmarks by remember { mutableStateOf(false) } // Default: Don't show

    // Rep tracking variables lifted outside listener so both listener and reset callback can access
    val engine = remember {
        var lastPush = 0
        var lastSquat = 0
        var sessionAddedXp = 0
        var lastBicepLeft = 0
        var lastBicepRight = 0
        lateinit var ref: WorkoutEngine
        ref = WorkoutEngine(object : WorkoutEngine.Listener {
            override fun onRepCountUpdated(reps: Int) {}
            override fun onXpUpdated(totalXp: Int) {}
            override fun onRepLogged(repIndex: Int, timestampMs: Long, xpEarned: Int) {
                when (ref.getExerciseType()) {
                    ExerciseType.PUSHUP -> {
                        val diff = repIndex - lastPush
                        if (diff > 0) { progressManager.recordPushups(diff, levelInfo, profileXp); lastPush = repIndex }
                    }
                    ExerciseType.SQUAT -> {
                        val diff = repIndex - lastSquat
                        if (diff > 0) { progressManager.recordSquats(diff, levelInfo, profileXp); lastSquat = repIndex }
                    }
                    ExerciseType.BICEP_LEFT -> {
                        val diff = repIndex - lastBicepLeft
                        if (diff > 0) { progressManager.recordBicepLeft(diff, levelInfo, profileXp); lastBicepLeft = repIndex }
                    }
                    ExerciseType.BICEP_RIGHT -> {
                        val diff = repIndex - lastBicepRight
                        if (diff > 0) { progressManager.recordBicepRight(diff, levelInfo, profileXp); lastBicepRight = repIndex }
                    }
                    else -> {} // Other exercises don't track specific daily rep counts yet
                }
                appScope.launch {
                    val sessionXp = ref.getTotalXp()
                    val delta = sessionXp - sessionAddedXp
                    if (delta > 0) {
                        sessionAddedXp = sessionXp
                        profileXp += delta
                        withContext(Dispatchers.IO) { repo.upsertProfile(profileXp) }
                        val newLevel = LevelSystem.levelFromXp(profileXp)
                        val leveled = newLevel.level > levelInfo.level
                        if (leveled) showLevelUp = true
                        levelInfo = newLevel
                        progressManager.updateGoals(newLevel, profileXp)
                    }
                }
            }
        })
        ref.addResetListener {
            lastPush = 0; lastSquat = 0; lastBicepLeft = 0; lastBicepRight = 0; sessionAddedXp = 0
        }
        ref
    }

    LaunchedEffect(Unit) {
        val prof = withContext(Dispatchers.IO) { repo.getProfile() }
        profileXp = prof?.totalXp ?: 0
        profileName = prof?.name ?: ""
        levelInfo = LevelSystem.levelFromXp(profileXp)
        progressManager.load(levelInfo, profileXp)
        startDestination = if (profileName.isBlank()) "onboarding" else "dashboard"
    }

    if (startDestination == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val bottomDestinations = listOf(
        BottomDest("dashboard", "Dashboard", Icons.Default.Home),
        BottomDest("workouts", "Workouts", Icons.Default.FitnessCenter),
        BottomDest("profile", "Profile", Icons.Default.Person),
        BottomDest("settings", "Settings", Icons.Default.Settings)
    )

    val hideBottomBarRoutes = setOf("session", "onboarding")
    val backStack by nav.currentBackStackEntryAsState()
    val showBottomBar = backStack?.destination?.route !in hideBottomBarRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val current = backStack?.destination?.route
                    bottomDestinations.forEach { dest ->
                        NavigationBarItem(
                            selected = current == dest.route,
                            onClick = { if (current != dest.route) nav.navigate(dest.route) { launchSingleTop = true; popUpTo("dashboard") { saveState = true } } },
                            icon = { Icon(dest.icon, contentDescription = dest.label) },
                            label = { Text(dest.label) }
                        )
                    }
                }
            }
        }
    ) { inner ->
        NavHost(navController = nav, startDestination = startDestination!!, modifier = Modifier.padding(inner)) {
            composable("onboarding") {
                OnboardingScreen(onContinue = { name ->
                    profileName = name
                    appScope.launch(Dispatchers.IO) { repo.updateName(name) }
                    nav.navigate("dashboard") { popUpTo("onboarding") { inclusive = true } }
                })
            }
            composable("dashboard") {
                DashboardScreen(
                    repo = repo,
                    engine = engine,
                    onStartWorkout = { nav.navigate("session") },
                    dailyState = dailyState,
                    levelInfo = levelInfo,
                    userName = profileName
                )
            }
            composable("workouts") { WorkoutsScreen(onStart = { type -> engine.setExerciseType(type); nav.navigate("session") }) }
            composable("profile") { 
                ProfileScreen(
                    name = profileName, 
                    levelInfo = levelInfo, 
                    xp = profileXp, 
                    dailyState = dailyState, 
                    repo = repo, 
                    onNameChange = { new -> profileName = new; appScope.launch(Dispatchers.IO) { repo.updateName(new) } }
                ) 
            }
            composable("settings") {
                SettingsScreen(
                    showLandmarks = showLandmarks,
                    onToggleLandmarks = { showLandmarks = it },
                    onExport = {
                        appScope.launch {
                            val sessions = withContext(Dispatchers.IO) { repo.getAllSessions() }
                            val csvHeader = "ID,Timestamp,Exercise,Reps,Duration(s),XP\n"
                            val csvBody = sessions.joinToString("\n") { 
                                "${it.id},${it.timestampIso},${it.exercise},${it.reps},${it.durationSeconds},${it.totalXp}" 
                            }
                            val csvContent = csvHeader + csvBody
                            
                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(android.content.Intent.EXTRA_SUBJECT, "RepMind Workout Data")
                                putExtra(android.content.Intent.EXTRA_TEXT, csvContent)
                            }
                            context.startActivity(android.content.Intent.createChooser(intent, "Export Workouts"))
                        }
                    }
                )
            }
            composable("session") {
                WorkoutSessionScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                    engine = engine,
                    performanceSettings = PerformanceSettings(showLandmarks = showLandmarks),
                    levelInfo = levelInfo,
                    dailyState = dailyState,
                    onExerciseChange = { engine.setExerciseType(it) }
                )
            }
        }
    }

    if (showLevelUp) {
        AlertDialog(onDismissRequest = { showLevelUp = false }, confirmButton = { TextButton({ showLevelUp = false }) { Text("Nice!") } }, title = { Text("Level Up") }, text = { Text("You reached Level ${levelInfo.level}") })
    }
}

// ---------------- Additional Screens ----------------
@Composable
private fun OnboardingScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(id = com.example.workouttracker.R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(id = com.example.workouttracker.R.string.welcome_sub))
        OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text(stringResource(id = com.example.workouttracker.R.string.enter_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onContinue(name.trim()) }, enabled = name.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text(stringResource(id = com.example.workouttracker.R.string.continue_label)) }
    }
}

@Composable
private fun WorkoutsScreen(onStart: (ExerciseType) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose Workout", style = MaterialTheme.typography.titleLarge)
        
        Text("Essentials", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        WorkoutCard("Pushups", "Upper body strength", ExerciseType.PUSHUP, onStart)
        WorkoutCard("Squats", "Lower body power", ExerciseType.SQUAT, onStart)
        WorkoutCard("Lunges", "Leg strength & balance", ExerciseType.LUNGES, onStart)
        
        Text("Arms & Core", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        WorkoutCard("Bicep Curl - Left", "Isolate left arm", ExerciseType.BICEP_LEFT, onStart)
        WorkoutCard("Bicep Curl - Right", "Isolate right arm", ExerciseType.BICEP_RIGHT, onStart)
        WorkoutCard("Shoulder Press", "Overhead strength", ExerciseType.SHOULDER_PRESS, onStart)
        
        Text("Cardio", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        WorkoutCard("Jumping Jacks", "Full body cardio", ExerciseType.JUMPING_JACKS, onStart)
    }
}

@Composable
private fun WorkoutCard(title: String, desc: String, type: ExerciseType, onStart: (ExerciseType) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onStart(type) }) {
        Column(Modifier.padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(showLandmarks: Boolean, onToggleLandmarks: (Boolean) -> Unit, onExport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Pose Detection", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Show Landmarks", style = MaterialTheme.typography.bodyLarge)
                        Text("Overlay skeleton on camera feed. Disable for cleaner view.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = showLandmarks, onCheckedChange = onToggleLandmarks)
                }
            }
        }
        
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Data Management", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export Workout Data (CSV)")
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    name: String,
    levelInfo: LevelSystem.LevelInfo,
    xp: Int,
    dailyState: ProgressManager.DailyState?,
    repo: SessionRepository,
    onNameChange: (String) -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(name) }
    
    // History Data Integration
    var sessions by remember { mutableStateOf<List<com.example.workouttracker.db.SessionEntity>>(emptyList()) }
    var lineChartData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var heatMapData by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { repo.getAllSessions() }.sortedByDescending { it.timestampIso }
        sessions = all
        
        // Process charts
        try {
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("MM-dd")
            val heatMapFormatter = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE
            
            val dailyReps = all.groupBy { 
                try {
                    val instant = try { OffsetDateTime.parse(it.timestampIso).toInstant() } catch (_: Exception) { Instant.parse(it.timestampIso) }
                    instant.atZone(ZoneId.systemDefault()).toLocalDate()
                } catch (e: Exception) { LocalDate.now() }
            }.mapValues { it.value.sumOf { s -> s.reps } }
            
            lineChartData = dailyReps.entries.sortedBy { it.key }
                .takeLast(14)
                .map { Pair(it.key.format(dateFormatter), it.value) }
                
            heatMapData = all.groupBy {
                 try {
                    val instant = try { OffsetDateTime.parse(it.timestampIso).toInstant() } catch (_: Exception) { Instant.parse(it.timestampIso) }
                    instant.atZone(ZoneId.systemDefault()).toLocalDate().format(heatMapFormatter)
                } catch (e: Exception) { LocalDate.now().toString() }
            }.mapValues { it.value.size }
        } catch (e: Exception) {}
    }

    Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        // Name & Level
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (editing) {
                    OutlinedTextField(value = temp, onValueChange = { temp = it.take(24) }, label = { Text("Name") })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { if (temp.trim().length>=2) { onNameChange(temp.trim()); editing=false } }) { Text("Save") }
                        OutlinedButton(onClick = { editing=false; temp=name }) { Text("Cancel") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(temp, style = MaterialTheme.typography.headlineSmall)
                        IconButton(onClick = { editing = true }) { Icon(Icons.Default.Edit, "Edit") }
                    }
                }
                Text("Level ${levelInfo.level} • ${levelInfo.rank}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                LinearProgressIndicator(progress = levelInfo.progressPercent/100f, modifier = Modifier.fillMaxWidth())
                Text("${levelInfo.currentXp} XP / ${levelInfo.xpForNextLevel} XP", style = MaterialTheme.typography.bodySmall)
            }
        }

        // Visualizations
        Text("Activity Heatmap", style = MaterialTheme.typography.titleMedium)
        ContributionHeatMap(heatMapData, Modifier.height(100.dp).padding(vertical = 8.dp))
        
        Text("Reps Trend (Last 14 Days)", style = MaterialTheme.typography.titleMedium)
        LineChart(lineChartData, Modifier.fillMaxWidth().height(200.dp))

        // History List
        Text("Recent History", style = MaterialTheme.typography.titleMedium)
        sessions.take(10).forEach { session ->
             val name = com.example.workouttracker.Utils.capitalize(session.exercise.replace("_", " "))
             ListItem(
                headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                supportingContent = { Text("${session.reps} reps • ${session.totalXp} XP • ${session.timestampIso.take(10)}") }
            )
             Divider()
        }
    }
}
