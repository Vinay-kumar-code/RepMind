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
import androidx.compose.ui.graphics.Color
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
import java.time.format.DateTimeFormatter
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

    var profileXp by remember { mutableStateOf(0f) }
    var levelInfo by remember { mutableStateOf(LevelSystem.levelFromXp(0f)) }
    var showLevelUp by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var startDestination by remember { mutableStateOf<String?>(null) }

    // Settings State
    var showLandmarks by remember { mutableStateOf(false) } // Default: Don't show

    // Rep tracking variables lifted outside listener so both listener and reset callback can access
    val engine = remember {
        var lastPush = 0
        var lastSquat = 0
        var sessionAddedXp = 0f
        var lastBicepLeft = 0
        var lastBicepRight = 0
        lateinit var ref: WorkoutEngine
        ref = WorkoutEngine(object : WorkoutEngine.Listener {
            override fun onRepCountUpdated(reps: Int) {}
            override fun onXpUpdated(totalXp: Float) {}
            override fun onRepLogged(repIndex: Int, timestampMs: Long, xpEarned: Float) {
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
                    if (delta > 0f) {
                        sessionAddedXp = sessionXp
                        profileXp += delta
                        withContext(Dispatchers.IO) { repo.upsertProfile(profileXp) }
                        val newProf = withContext(Dispatchers.IO) { repo.getProfile() }
                        val newLevel = LevelSystem.levelFromXp(profileXp)
                        val leveled = newLevel.level > levelInfo.level
                        if (leveled) showLevelUp = true
                        levelInfo = newLevel
                        progressManager.updateGoals(newLevel, profileXp, newProf)
                    }
                }
            }
        })
        ref.addResetListener {
            lastPush = 0; lastSquat = 0; lastBicepLeft = 0; lastBicepRight = 0; sessionAddedXp = 0f
        }
        ref
    }

    var notionApiKey by remember { mutableStateOf("") }
    var notionDbId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val prof = withContext(Dispatchers.IO) { repo.getProfile() }
        profileXp = prof?.totalXp ?: 0f // Update totalXp to Float casted to Int or just handle it as Float? Wait, totalXp is Float now! Let's check profileXp
        // Wait, profileXp is Int in the app code, let's keep it Int.
        profileXp = (prof?.totalXp ?: 0f)
        profileName = prof?.name ?: ""
        notionApiKey = prof?.notionApiKey ?: ""
        notionDbId = prof?.notionDbId ?: ""
        levelInfo = LevelSystem.levelFromXp(profileXp)
        progressManager.load(levelInfo, profileXp, prof)
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
        contentWindowInsets = WindowInsets(0.dp),
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
                    userName = profileName,
                    onNavigateToSettings = { nav.navigate("settings") },
                    progressManager = progressManager
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
                    onNameChange = { new -> profileName = new; appScope.launch(Dispatchers.IO) { repo.updateName(new) } },
                    onViewHistory = { nav.navigate("history") }
                ) 
            }
            composable("settings") {
                var showManualDialog by remember { mutableStateOf(false) }
                var highestStreak by remember { mutableStateOf(0) }
                
                LaunchedEffect(Unit) {
                    val allDaily = withContext(Dispatchers.IO) { repo.getRecentDaily(10000).sortedBy { it.date } }
                    var maxS = 0
                    var currentS = 0
                    var lastDate: LocalDate? = null

                    for (dp in allDaily) {
                        if (dp.goalsMet) {
                            val d = LocalDate.parse(dp.date)
                            if (lastDate == null || d == lastDate.plusDays(1)) {
                                currentS++
                            } else if (d != lastDate) {
                                currentS = 1
                            }
                            if (currentS > maxS) maxS = currentS
                            lastDate = d
                        }
                    }
                    highestStreak = maxS
                }
                
                SettingsScreen(
                    showLandmarks = showLandmarks,
                    notionApiKey = notionApiKey,
                    notionDbId = notionDbId,
                    onToggleLandmarks = { showLandmarks = it },
                    onExportJson = { uri ->
                        appScope.launch(Dispatchers.IO) {
                            try {
                                val sessions = repo.getAllSessions()
                                val jsonArray = org.json.JSONArray()
                                sessions.forEach { s ->
                                    val obj = org.json.JSONObject()
                                    obj.put("id", s.id)
                                    obj.put("timestampIso", s.timestampIso)
                                    obj.put("exercise", s.exercise)
                                    obj.put("reps", s.reps)
                                    obj.put("durationSeconds", s.durationSeconds.toDouble())
                                    obj.put("totalXp", s.totalXp.toDouble())
                                    obj.put("syncedToNotion", s.syncedToNotion)
                                    jsonArray.put(obj)
                                }
                                context.contentResolver.openOutputStream(uri)?.use { out ->
                                    out.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                                }
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Exported successfully", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    },
                    onImportJson = { uri ->
                        appScope.launch(Dispatchers.IO) {
                            try {
                                val jsonString = context.contentResolver.openInputStream(uri)?.use { 
                                    it.readBytes().toString(Charsets.UTF_8)
                                } ?: ""
                                val jsonArray = org.json.JSONArray(jsonString)
                                val existing = repo.getAllSessions()
                                val existingMap = existing.associateBy { it.timestampIso }
                                
                                var imported = 0
                                var skipped = 0
                                var addedXp = 0f
                                
                                for (i in 0 until jsonArray.length()) {
                                    val obj = jsonArray.getJSONObject(i)
                                    val ts = obj.getString("timestampIso")
                                    if (existingMap.containsKey(ts)) {
                                        skipped++
                                    } else {
                                        val s = com.example.workouttracker.db.SessionEntity(
                                            timestampIso = ts,
                                            exercise = obj.getString("exercise"),
                                            reps = obj.getInt("reps"),
                                            durationSeconds = obj.getDouble("durationSeconds").toFloat(),
                                            totalXp = obj.getDouble("totalXp").toFloat(),
                                            syncedToNotion = obj.optBoolean("syncedToNotion", false)
                                        )
                                        repo.insertSession(s)
                                        addedXp += s.totalXp
                                        imported++
                                    }
                                }
                                
                                if (imported > 0) {
                                    val currentProf = repo.getProfile()
                                    val currentXp = currentProf?.totalXp ?: 0f
                                    repo.upsertProfile(currentXp + addedXp)
                                    progressManager.recalculateDailyProgress()
                                    
                                    withContext(Dispatchers.Main) {
                                        val newProf = withContext(Dispatchers.IO) { repo.getProfile() }
                                        profileXp = newProf?.totalXp ?: 0f
                                        levelInfo = LevelSystem.levelFromXp(profileXp)
                                        progressManager.load(levelInfo, profileXp, newProf)
                                        android.widget.Toast.makeText(context, "Imported $imported workouts ($skipped skipped)", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "No new workouts to import ($skipped skipped)", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Import failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    },
                    onOpenManualEntry = { showManualDialog = true },
                    onSaveNotionKeys = { key, db ->
                        notionApiKey = key
                        notionDbId = db
                        appScope.launch(Dispatchers.IO) { repo.updateNotionKeys(key, db) }
                    },
                    onSyncNotion = { onShowDialog ->
                        val syncManager = NotionSyncManager(repo)
                        appScope.launch {
                            val result = syncManager.syncUnsyncedSessions(notionApiKey, notionDbId)
                            if (result.isSuccess) {
                                onShowDialog("Sync Successful", "Synced ${result.getOrNull()} sessions to Notion.")
                            } else {
                                onShowDialog("Sync Failed", "${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    onRetrieveNotion = { onShowDialog ->
                        val syncManager = NotionSyncManager(repo)
                        appScope.launch {
                            val result = syncManager.retrieveAllSessions(notionApiKey, notionDbId)
                            if (result.isSuccess) {
                                val count = result.getOrNull() ?: 0
                                progressManager.recalculateDailyProgress()
                                val newProf = withContext(Dispatchers.IO) { repo.getProfile() }
                                profileXp = newProf?.totalXp ?: 0f
                                levelInfo = LevelSystem.levelFromXp(profileXp)
                                progressManager.load(levelInfo, profileXp, newProf)
                                onShowDialog("Retrieve Successful", "Retrieved $count sessions from Notion.")
                            } else {
                                onShowDialog("Retrieve Failed", "${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    onShareProfile = {
                        shareProfileStats(
                            context = context,
                            name = profileName.ifBlank { "Athlete" },
                            xp = profileXp,
                            levelInfo = levelInfo,
                            highestStreak = highestStreak,
                            currentStreak = dailyState?.streak ?: 0
                        )
                    }
                )
                
                if (showManualDialog) {
                    ManualEntryDialog(
                        onDismiss = { showManualDialog = false },
                        onSave = { exercise, reps, durationMins, isCustom, isDurationBased ->
                            showManualDialog = false
                            val xpEarned = if (isCustom) {
                                if (isDurationBased) LevelSystem.xpForManualDuration(durationMins) else LevelSystem.xpForManualReps(reps)
                            } else {
                                LevelSystem.xpForStandardExercise(exercise, reps)
                            }
                            
                            appScope.launch(Dispatchers.IO) {
                                val entity = com.example.workouttracker.db.SessionEntity(
                                    timestampIso = java.time.OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                                    exercise = exercise,
                                    reps = reps,
                                    durationSeconds = (durationMins * 60).toFloat(),
                                    totalXp = xpEarned,
                                    syncedToNotion = false
                                )
                                repo.insertSession(entity)
                                val currentProf = repo.getProfile()
                                val currentXp = currentProf?.totalXp ?: 0f
                                repo.upsertProfile(currentXp + xpEarned)
                                progressManager.recalculateDailyProgress()
                                
                                withContext(Dispatchers.Main) {
                                    val newProf = withContext(Dispatchers.IO) { repo.getProfile() }
                                    profileXp = newProf?.totalXp ?: 0f
                                    levelInfo = LevelSystem.levelFromXp(profileXp)
                                    progressManager.load(levelInfo, profileXp, newProf)
                                }
                            }
                        }
                    )
                }
            }
            composable("history") {
                HistoryScreen(repo = repo, onBack = { nav.popBackStack() })
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).padding(top = 48.dp, bottom = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MotivationCard()
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
private fun SettingsScreen(
    showLandmarks: Boolean, 
    notionApiKey: String,
    notionDbId: String,
    onToggleLandmarks: (Boolean) -> Unit, 
    onExportJson: (android.net.Uri) -> Unit, 
    onImportJson: (android.net.Uri) -> Unit,
    onOpenManualEntry: () -> Unit,
    onSaveNotionKeys: (String, String) -> Unit,
    onSyncNotion: (onShowDialog: (String, String) -> Unit) -> Unit,
    onRetrieveNotion: (onShowDialog: (String, String) -> Unit) -> Unit,
    onShareProfile: () -> Unit
) {
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    if (dialogTitle != null && dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogTitle = null; dialogMessage = null },
            confirmButton = { TextButton(onClick = { dialogTitle = null; dialogMessage = null }) { Text("OK") } },
            title = { Text(dialogTitle!!) },
            text = { Text(dialogMessage!!) }
        )
    }

    val exportLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onExportJson(it) }
    }
    
    val importLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportJson(it) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
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
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Notion Integration (Optional)", style = MaterialTheme.typography.titleMedium)
                
                val hasKeys = notionApiKey.isNotBlank() && notionDbId.isNotBlank()
                var isEditingKeys by remember { mutableStateOf(!hasKeys) }

                if (isEditingKeys) {
                    var tempKey by remember { mutableStateOf(notionApiKey) }
                    var tempDb by remember { mutableStateOf(notionDbId) }
                    
                    OutlinedTextField(
                        value = tempKey, 
                        onValueChange = { tempKey = it },
                        label = { Text("Integration Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = tempDb, 
                        onValueChange = { tempDb = it },
                        label = { Text("Database ID") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = { 
                        onSaveNotionKeys(tempKey, tempDb) 
                        isEditingKeys = false
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save Keys")
                    }
                } else {
                    OutlinedTextField(
                        value = "••••••••••••••••••••••••••••••", 
                        onValueChange = { },
                        label = { Text("Integration Token") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )
                    
                    OutlinedTextField(
                        value = "••••••••••••••••••••••••••••••", 
                        onValueChange = { },
                        label = { Text("Database ID") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = false
                    )

                    Button(onClick = { isEditingKeys = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Edit Keys")
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onSyncNotion { title, msg -> dialogTitle = title; dialogMessage = msg } }, modifier = Modifier.weight(1f)) {
                        Text("Sync to Notion")
                    }
                    Button(onClick = { onRetrieveNotion { title, msg -> dialogTitle = title; dialogMessage = msg } }, modifier = Modifier.weight(1f)) {
                        Text("Retrieve")
                    }
                }
            }
        }
        
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Data Management", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { exportLauncher.launch("RepMind_Backup.json") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Export Data (JSON)")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Refresh, null) // Needs an icon like Refresh or Download
                    Spacer(Modifier.width(8.dp))
                    Text("Import Data (JSON)")
                }
            }
        }
        
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Manual Entry", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = onOpenManualEntry, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Manual Workout Entry")
                }
            }
        }
        
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Profile Sharing", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { onShareProfile() }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Share, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Share Profile Stats")
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    name: String,
    levelInfo: LevelSystem.LevelInfo,
    xp: Float,
    dailyState: ProgressManager.DailyState?,
    repo: SessionRepository,
    onNameChange: (String) -> Unit,
    onViewHistory: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(name) }
    
    // History Data Integration
    var sessions by remember { mutableStateOf<List<com.example.workouttracker.db.SessionEntity>>(emptyList()) }
    var lineChartData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var heatMapData by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    
    var todayXp by remember { mutableStateOf(0f) }
    var weekXp by remember { mutableStateOf(0f) }
    var monthXp by remember { mutableStateOf(0f) }
    var activeDates by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { repo.getAllSessions() }.sortedByDescending { it.timestampIso }
        sessions = all
        
        // XP Stats
        val today = LocalDate.now()
        val todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val weekStart = today.minusDays(6)
        val monthStart = today.minusDays(29)

        fun parseDate(iso: String): LocalDate {
            return try {
                val instant = try { OffsetDateTime.parse(iso).toInstant() } catch (_: Exception) { Instant.parse(iso) }
                instant.atZone(ZoneId.systemDefault()).toLocalDate()
            } catch (e: Exception) { LocalDate.now() }
        }

        todayXp = all.filter { parseDate(it.timestampIso) == today }
            .sumOf { Math.round(it.totalXp * 100.0) / 100.0 }.toFloat()
        
        weekXp = all.filter { 
            val d = parseDate(it.timestampIso)
            d == today || d.isAfter(today.minusDays(7))
        }.sumOf { Math.round(it.totalXp * 100.0) / 100.0 }.toFloat()
        
        monthXp = all.filter { 
            val d = parseDate(it.timestampIso)
            d == today || d.isAfter(today.minusDays(30))
        }.sumOf { Math.round(it.totalXp * 100.0) / 100.0 }.toFloat()

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
            
            activeDates = heatMapData.keys
        } catch (e: Exception) {}
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        MotivationCard()
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

        // XP Stats Cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XpStatCard("Today", todayXp, Modifier.weight(1f))
            XpStatCard("7 Days", weekXp, Modifier.weight(1f))
            XpStatCard("30 Days", monthXp, Modifier.weight(1f))
        }

        // Visualizations
        Text("Calendar Streak", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Box(Modifier.padding(16.dp)) {
                val dayStatesMap = activeDates.associateWith { DayState.WORKOUT_DONE }
                CalendarView(dayStatesMap, null, {})
            }
        }
        
        Text("Reps Trend (Last 14 Days)", style = MaterialTheme.typography.titleMedium)
        LineChart(lineChartData, Modifier.fillMaxWidth().height(200.dp))

        // History List
        Text("Recent History", style = MaterialTheme.typography.titleMedium)
        sessions.take(10).forEach { session ->
             val name = com.example.workouttracker.Utils.capitalize(session.exercise.replace("_", " "))
             ListItem(
                headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                supportingContent = { Text("${session.reps} reps • ${String.format(Locale.US, "%.2f", session.totalXp)} XP • ${session.timestampIso.take(10)}") }
            )
             Divider()
        }
        Button(onClick = onViewHistory, modifier = Modifier.fillMaxWidth()) {
            Text("View Full History")
        }
    }
}

@Composable
fun XpStatCard(label: String, xp: Float, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(String.format(Locale.US, "%.2f", xp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text("XP", style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
fun ManualEntryDialog(
    onDismiss: () -> Unit,
    onSave: (exercise: String, reps: Int, durationMins: Int, isCustom: Boolean, isDurationBased: Boolean) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    
    var exerciseType by remember { mutableStateOf("Pushups") }
    var customName by remember { mutableStateOf("") }
    var isDurationBased by remember { mutableStateOf(false) }
    var repsInput by remember { mutableStateOf("") }
    var durationIndex by remember { mutableStateOf(4) } // index for 10 mins
    
    val durationOptions = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)
    val standardTypes = listOf("Pushups", "Squats", "Lunges", "Bicep Curl - Left", "Bicep Curl - Right", "Shoulder Press", "Jumping Jacks", "Pullups", "Other")
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Manual Entry", style = MaterialTheme.typography.headlineSmall, color = textColor, fontWeight = FontWeight.Bold)
                
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(exerciseType, color = textColor)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        standardTypes.forEach { type ->
                            DropdownMenuItem(text = { Text(type) }, onClick = { exerciseType = type; expanded = false })
                        }
                    }
                }
                
                val isCustom = exerciseType == "Other"
                
                if (isCustom) {
                    OutlinedTextField(
                        value = customName, 
                        onValueChange = { customName = it }, 
                        label = { Text("Workout Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tracking Mode:", modifier = Modifier.weight(1f))
                        TextButton(onClick = { isDurationBased = false }, colors = ButtonDefaults.textButtonColors(contentColor = if (!isDurationBased) MaterialTheme.colorScheme.primary else Color.Gray)) {
                            Text("Reps")
                        }
                        TextButton(onClick = { isDurationBased = true }, colors = ButtonDefaults.textButtonColors(contentColor = if (isDurationBased) MaterialTheme.colorScheme.primary else Color.Gray)) {
                            Text("Duration")
                        }
                    }
                    
                    if (isDurationBased) {
                        Text("Duration (Minutes)", color = textColor, style = MaterialTheme.typography.labelMedium)
                        var durationExpanded by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { durationExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                Text("${durationOptions[durationIndex]} mins", color = textColor)
                            }
                            DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                                durationOptions.forEachIndexed { index, mins ->
                                    DropdownMenuItem(text = { Text("$mins mins") }, onClick = { durationIndex = index; durationExpanded = false })
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = repsInput, 
                            onValueChange = { repsInput = it }, 
                            label = { Text("Total Reps") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = repsInput, 
                        onValueChange = { repsInput = it }, 
                        label = { Text("Reps") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                    )
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = textColor.copy(alpha=0.7f)) }
                    Button(onClick = {
                        val finalName = if (isCustom) customName.ifBlank { "Custom Workout" } else exerciseType
                        val finalReps = if (isCustom && isDurationBased) 0 else (repsInput.toIntOrNull() ?: 0)
                        val finalDuration = if (isCustom && isDurationBased) durationOptions[durationIndex] else 0
                        onSave(finalName, finalReps, finalDuration, isCustom, isDurationBased)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun shareProfileStats(
    context: android.content.Context,
    name: String,
    xp: Float,
    levelInfo: LevelSystem.LevelInfo,
    highestStreak: Int,
    currentStreak: Int
) {
    val width = 800
    val height = 800
    val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    
    // Gradient Background
    val paint = android.graphics.Paint().apply { isAntiAlias = true }
    val gradient = android.graphics.LinearGradient(
        0f, 0f, width.toFloat(), height.toFloat(),
        intArrayOf(android.graphics.Color.parseColor("#121212"), android.graphics.Color.parseColor("#2C2C2C")),
        null, android.graphics.Shader.TileMode.CLAMP
    )
    paint.shader = gradient
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.shader = null
    
    // Text Paint
    paint.apply {
        color = android.graphics.Color.WHITE
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 70f
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
    }
    
    canvas.drawText("RepMind Profile", width / 2f, 120f, paint)
    
    paint.textSize = 50f
    paint.color = android.graphics.Color.parseColor("#A259FF")
    canvas.drawText(name, width / 2f, 220f, paint)
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 45f
    paint.typeface = android.graphics.Typeface.DEFAULT
    canvas.drawText("Level ${levelInfo.level} • ${levelInfo.rank}", width / 2f, 320f, paint)
    
    paint.color = android.graphics.Color.LTGRAY
    paint.textSize = 40f
    canvas.drawText("Total XP: ${String.format(java.util.Locale.US, "%.2f", xp)}", width / 2f, 420f, paint)
    
    // Draw Stats Box
    paint.color = android.graphics.Color.parseColor("#333333")
    canvas.drawRoundRect(100f, 500f, width - 100f, 700f, 30f, 30f, paint)
    
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 35f
    canvas.drawText("Current Streak", width / 4f + 50f, 570f, paint)
    canvas.drawText("Highest Streak", 3 * width / 4f - 50f, 570f, paint)
    
    paint.textSize = 60f
    paint.color = android.graphics.Color.parseColor("#FFD700") // Gold
    canvas.drawText("$currentStreak", width / 4f + 50f, 650f, paint)
    canvas.drawText("$highestStreak", 3 * width / 4f - 50f, 650f, paint)
    
    try {
        val cachePath = java.io.File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = java.io.File(cachePath, "profile_share.png")
        val stream = java.io.FileOutputStream(file)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(android.content.Intent.EXTRA_STREAM, uri)
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(android.content.Intent.createChooser(intent, "Share Profile"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

