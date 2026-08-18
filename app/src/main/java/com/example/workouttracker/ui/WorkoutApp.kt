package com.example.workouttracker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.workouttracker.R
import com.example.workouttracker.*
import com.example.workouttracker.WorkoutEngine.ExerciseType
import com.example.workouttracker.alarm.*
import com.example.workouttracker.db.SessionEntity
import com.example.workouttracker.db.SessionRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.time.*
import java.util.Locale

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutApp(repo: SessionRepository, themePrefs: ThemePreferences) {
    val nav = rememberNavController()
    val appScope = remember { CoroutineScope(Dispatchers.Main) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val streakPrefs = remember { StreakPreferences(context) }
    val hardAlarmManager = remember { HardAlarmManager(context) }
    val achievementManager = remember { AchievementManager(context) }
    val progressManager = remember { ProgressManager(repo, appScope, streakPrefs) }
    val manualWorkoutPrefs = remember { ManualWorkoutPreferences(context) }
    val xpPrefs = remember { XpPreferences(context) }
    
    val dailyState by progressManager.dailyState.collectAsState()
    val themeMode by themePrefs.themeMode.collectAsState()
    val useMaterialYou by themePrefs.useMaterialYou.collectAsState()
    val xpRates by xpPrefs.rates.collectAsState()
    val alarmConfig by hardAlarmManager.config.collectAsState()
    val streakInfo by streakPrefs.streakInfo.collectAsState()

    var profileXp by remember { mutableStateOf(0f) }
    var levelInfo by remember { mutableStateOf(LevelSystem.levelFromXp(0f)) }
    var showLevelUp by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var startDestination by remember { mutableStateOf<String?>(null) }
    
    var useCustomGoals by remember { mutableStateOf(false) }
    var customPushGoal by remember { mutableStateOf(10) }
    var customSquatGoal by remember { mutableStateOf(10) }
    var customBicepGoal by remember { mutableStateOf(60) }
    var notionApiKey by remember { mutableStateOf("") }
    var notionDbId by remember { mutableStateOf("") }

    // Settings State
    var showLandmarks by remember { mutableStateOf(false) }

    // Health Connect
    val healthConnectManager = remember { HealthConnectManager(context, repo) }
    var hcConnected by remember { mutableStateOf(false) }
    var hcStepsToday by remember { mutableStateOf(0L) }

    val hcPermissionLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { grantedMap ->
        val grantedCount = grantedMap.count { it.value }
        if (grantedCount > 0) {
            hcConnected = true
            appScope.launch(Dispatchers.IO) {
                try {
                    hcStepsToday = healthConnectManager.getTodaySteps()
                    healthConnectManager.syncFromHealthConnect(xpRates)
                    
                    val newProf = repo.getProfile()
                    withContext(Dispatchers.Main) {
                        profileXp = newProf?.totalXp ?: 0f
                        levelInfo = LevelSystem.levelFromXp(profileXp)
                        progressManager.recalculateDailyProgress()
                        progressManager.load(levelInfo, profileXp, newProf)
                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
            }
        }
    }

    // Auto refresh Health Connect permissions on app resume & schedule nightly step sync
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                StepSyncScheduler.scheduleDailySync(context)
                if (healthConnectManager.isSupported) {
                    appScope.launch(Dispatchers.IO) {
                        val hasPerms = healthConnectManager.hasPermissions()
                        val steps = if (hasPerms) healthConnectManager.getTodaySteps() else 0L
                        withContext(Dispatchers.Main) {
                            hcConnected = hasPerms
                            if (hasPerms) {
                                hcStepsToday = steps
                            }
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        try {
            StepSyncScheduler.scheduleDailySync(context)
            val currentProf = withContext(Dispatchers.IO) { repo.getProfile() }
            profileXp = currentProf?.totalXp ?: 0f
            levelInfo = LevelSystem.levelFromXp(profileXp)
            profileName = currentProf?.name ?: ""
            useCustomGoals = currentProf?.useCustomGoals ?: false
            customPushGoal = currentProf?.customPushGoal ?: 10
            customSquatGoal = currentProf?.customSquatGoal ?: 10
            customBicepGoal = currentProf?.customBicepGoal ?: 60
            notionApiKey = currentProf?.notionApiKey ?: ""
            notionDbId = currentProf?.notionDbId ?: ""
            progressManager.load(levelInfo, profileXp, currentProf)
            startDestination = if (profileName.isBlank()) "onboarding" else "dashboard"

            if (healthConnectManager.isSupported) {
                try {
                    hcConnected = healthConnectManager.hasPermissions()
                    if (hcConnected) {
                        hcStepsToday = healthConnectManager.getTodaySteps()
                        healthConnectManager.syncFromHealthConnect(xpRates)
                        if (notionApiKey.isNotEmpty() && notionDbId.isNotEmpty()) {
                            NotionSyncManager(repo).syncUnsyncedSessions(notionApiKey, notionDbId)
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            startDestination = "dashboard"
        }
    }

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
                    else -> {}
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

    // Keep engine's xpRates synced
    LaunchedEffect(xpRates) {
        engine.xpRates = xpRates
    }

    if (startDestination == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
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
        Column(Modifier.padding(inner).fillMaxSize()) {
            if (alarmConfig.isRinging) {
                Surface(
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth().clickable { nav.navigate("settings") }
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("🚨 HARD WORKOUT ALARM ACTIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Auto-track your target workouts to dismiss!", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        }
                        Button(
                            onClick = { nav.navigate("settings") },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFFD32F2F)),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Verify", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            NavHost(navController = nav, startDestination = startDestination!!, modifier = Modifier.weight(1f)) {
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
                        repo = repo, 
                        todaySteps = hcStepsToday,
                        isHealthConnectAvailable = healthConnectManager.isSupported,
                        isHealthConnectConnected = hcConnected,
                        healthConnectManager = healthConnectManager,
                        achievementManager = achievementManager,
                        streakPrefs = streakPrefs,
                        streakInfo = streakInfo,
                        onNameChange = { new -> profileName = new; appScope.launch(Dispatchers.IO) { repo.updateName(new) } },
                        onXpEarned = { newXp ->
                            profileXp = newXp
                            levelInfo = LevelSystem.levelFromXp(newXp)
                            progressManager.load(levelInfo, newXp, null)
                        },
                        onViewHistory = { nav.navigate("history") },
                        onStreakUpdated = {
                            appScope.launch(Dispatchers.IO) {
                                progressManager.recalculateDailyProgress()
                            }
                        }
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
                        themeMode = themeMode,
                        useMaterialYou = useMaterialYou,
                        isHealthConnectAvailable = healthConnectManager.isSupported,
                        isHealthConnectConnected = hcConnected,
                        hardAlarmManager = hardAlarmManager,
                        alarmConfig = alarmConfig,
                        streakPrefs = streakPrefs,
                        streakInfo = streakInfo,
                        repo = repo,
                    onConnectHealthConnect = { 
                        try {
                            hcPermissionLauncher.launch(healthConnectManager.permissions.toTypedArray())
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open Health Connect permissions: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    },
                    onSyncToHealthConnect = { showDialog -> 
                        appScope.launch(Dispatchers.IO) {
                            val res = healthConnectManager.syncToHealthConnect()
                            withContext(Dispatchers.Main) {
                                if (res.isSuccess) showDialog("Export Success", "Exported ${res.getOrNull()} sessions to Health Connect.")
                                else showDialog("Export Failed", res.exceptionOrNull()?.message ?: "Unknown error")
                            }
                        }
                    },
                    onSyncFromHealthConnect = { showDialog ->
                        appScope.launch(Dispatchers.IO) {
                            val res = healthConnectManager.syncFromHealthConnect(xpRates)
                            val newProf = repo.getProfile()
                            withContext(Dispatchers.Main) {
                                profileXp = newProf?.totalXp ?: 0f
                                levelInfo = LevelSystem.levelFromXp(profileXp)
                                progressManager.recalculateDailyProgress()
                                progressManager.load(levelInfo, profileXp, newProf)
                                if (res.isSuccess) {
                                    showDialog("Import Success", "Imported ${res.getOrNull()} sessions from Health Connect.")
                                } else {
                                    showDialog("Import Failed", res.exceptionOrNull()?.message ?: "Unknown error")
                                }
                            }
                        }
                    },
                    onThemeModeChange = { themePrefs.setThemeMode(it) },
                    onUseMaterialYouChange = { themePrefs.setUseMaterialYou(it) },
                    useCustomGoals = useCustomGoals,
                    customPushGoal = customPushGoal,
                    customSquatGoal = customSquatGoal,
                    customBicepGoal = customBicepGoal,
                    onSaveDailyGoals = { useCustom, push, squat, bicep ->
                        useCustomGoals = useCustom
                        customPushGoal = push
                        customSquatGoal = squat
                        customBicepGoal = bicep
                        appScope.launch(Dispatchers.IO) {
                            repo.updateCustomGoals(useCustom, push, squat, bicep)
                            val newProf = repo.getProfile()
                            withContext(Dispatchers.Main) {
                                progressManager.updateGoals(levelInfo, profileXp, newProf)
                                progressManager.recalculateDailyProgress()
                                Toast.makeText(context, "Daily goals updated", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    xpRates = xpRates,
                    onUpdateXpRates = { newRates ->
                        xpPrefs.updateRates(newRates)
                        Toast.makeText(context, "XP rates updated", Toast.LENGTH_SHORT).show()
                    },
                    onResetXpRates = {
                        xpPrefs.resetToDefaults()
                        Toast.makeText(context, "XP rates reset to default", Toast.LENGTH_SHORT).show()
                    },
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
                                    obj.put("isManual", s.isManual)
                                    jsonArray.put(obj)
                                }
                                context.contentResolver.openOutputStream(uri)?.use { out ->
                                    out.write(jsonArray.toString(2).toByteArray(Charsets.UTF_8))
                                }
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Exported successfully", Toast.LENGTH_SHORT).show()
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
                                        val s = SessionEntity(
                                            timestampIso = ts,
                                            exercise = obj.getString("exercise"),
                                            reps = obj.getInt("reps"),
                                            durationSeconds = obj.getDouble("durationSeconds").toFloat(),
                                            totalXp = obj.getDouble("totalXp").toFloat(),
                                            syncedToNotion = obj.optBoolean("syncedToNotion", false),
                                            isManual = obj.optBoolean("isManual", false)
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
                                        Toast.makeText(context, "Imported $imported workouts ($skipped skipped)", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "No new workouts to import ($skipped skipped)", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                                withContext(Dispatchers.Main) {
                                    Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
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
                    onSyncNotion = { onProgress, onShowDialog ->
                        val syncManager = NotionSyncManager(repo)
                        appScope.launch {
                            val result = syncManager.syncUnsyncedSessions(notionApiKey, notionDbId, onProgress)
                            if (result.isSuccess) {
                                onShowDialog("Sync Successful", "Synced ${result.getOrNull()} sessions to Notion.")
                            } else {
                                onShowDialog("Sync Failed", "${result.exceptionOrNull()?.message}")
                            }
                        }
                    },
                    onRetrieveNotion = { onProgress, onShowDialog ->
                        val syncManager = NotionSyncManager(repo)
                        appScope.launch {
                            val result = syncManager.retrieveAllSessions(notionApiKey, notionDbId, onProgress)
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
                    },
                    onStreakUpdated = {
                        appScope.launch(Dispatchers.IO) {
                            progressManager.recalculateDailyProgress()
                        }
                    }
                )
                
                if (showManualDialog) {
                    ManualEntryDialog(
                        manualWorkoutPrefs = manualWorkoutPrefs,
                        xpRates = xpRates,
                        onDismiss = { showManualDialog = false },
                        onSave = { exercise, reps, durationMins, isCustom, isDurationBased, dateMs ->
                            showManualDialog = false
                            val xpEarned = if (isCustom) {
                                if (isDurationBased) LevelSystem.xpForManualDuration(durationMins, xpRates) 
                                else LevelSystem.xpForManualReps(reps, xpRates)
                            } else {
                                if (isDurationBased) LevelSystem.xpForManualDuration(durationMins, xpRates)
                                else LevelSystem.xpForStandardExercise(exercise, reps, xpRates)
                            }
                            
                            appScope.launch(Dispatchers.IO) {
                                val isoTime = dateMs?.let { 
                                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME) 
                                } ?: OffsetDateTime.now().format(java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                                
                                val entity = SessionEntity(
                                    timestampIso = isoTime,
                                    exercise = exercise,
                                    reps = reps,
                                    durationSeconds = (durationMins * 60).toFloat(),
                                    totalXp = xpEarned,
                                    syncedToNotion = false,
                                    isManual = true
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
                                    Toast.makeText(context, "Added manual workout: $exercise", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
            composable("history") {
                HistoryScreen(
                    repo = repo,
                    progressManager = progressManager,
                    xpPrefs = xpPrefs,
                    onDataChanged = {
                        appScope.launch {
                            val newProf = withContext(Dispatchers.IO) { repo.getProfile() }
                            profileXp = newProf?.totalXp ?: 0f
                            levelInfo = LevelSystem.levelFromXp(profileXp)
                            progressManager.load(levelInfo, profileXp, newProf)
                        }
                    },
                    onBack = { nav.popBackStack() }
                )
            }
            composable("session") {
                WorkoutSessionScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                    engine = engine,
                    performanceSettings = PerformanceSettings(showLandmarks = showLandmarks),
                    dailyState = dailyState,
                    onExerciseChange = { engine.setExerciseType(it) }
                )
            }
        }
    }
}

    if (showLevelUp) {
        AlertDialog(
            onDismissRequest = { showLevelUp = false }, 
            confirmButton = { TextButton({ showLevelUp = false }) { Text("Nice!") } }, 
            title = { Text("Level Up 🎉") }, 
            text = { Text("You reached Level ${levelInfo.level} (${levelInfo.rank})!") }
        )
    }
}

// ---------------- Additional Screens ----------------
@Composable
private fun OnboardingScreen(onContinue: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(id = R.string.welcome_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(id = R.string.welcome_sub))
        OutlinedTextField(value = name, onValueChange = { name = it.take(24) }, label = { Text(stringResource(id = R.string.enter_name_hint)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onContinue(name.trim()) }, enabled = name.trim().length >= 2, modifier = Modifier.fillMaxWidth()) { Text(stringResource(id = R.string.continue_label)) }
    }
}

@Composable
private fun WorkoutsScreen(onStart: (ExerciseType) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 48.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MotivationCard()
        Text("Choose Workout", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        
        Text("Essentials & Strength", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        WorkoutCard("Pushups", "Upper body strength & chest focus", ExerciseType.PUSHUP, onStart)
        WorkoutCard("Squats", "Lower body explosive power & quads", ExerciseType.SQUAT, onStart)
        WorkoutCard("Lunges", "Leg strength, balance & hip mobility", ExerciseType.LUNGES, onStart)
        WorkoutCard("Glute Bridges", "Posterior chain & glute activation", ExerciseType.GLUTE_BRIDGES, onStart)
        
        Text("Arms & Upper Body", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        WorkoutCard("Bicep Curl - Left", "Isolate left arm bicep contraction", ExerciseType.BICEP_LEFT, onStart)
        WorkoutCard("Bicep Curl - Right", "Isolate right arm bicep contraction", ExerciseType.BICEP_RIGHT, onStart)
        WorkoutCard("Shoulder Press", "Overhead deltoid strength", ExerciseType.SHOULDER_PRESS, onStart)
        WorkoutCard("Lateral Raises", "Shoulder definition & side delts", ExerciseType.LATERAL_RAISES, onStart)
        WorkoutCard("Tricep Dips", "Tricep lockout & arm extension", ExerciseType.TRICEP_DIPS, onStart)
        WorkoutCard("Pullups", "Back lats and upper body pull", ExerciseType.PULLUP, onStart)
        
        Text("Core & Stability", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        WorkoutCard("Plank ⏱️", "Isometric hold duration & straight-line posture", ExerciseType.PLANK, onStart)
        WorkoutCard("Crunches", "Abdominal core compression", ExerciseType.CRUNCHES, onStart)
        WorkoutCard("Leg Raises", "Lower abs & hip flexor control", ExerciseType.LEG_RAISES, onStart)
        
        Text("Cardio & Agility", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
        WorkoutCard("High Knees", "High-intensity cardio & sprint power", ExerciseType.HIGH_KNEES, onStart)
        WorkoutCard("Jumping Jacks", "Full body rhythm & aerobic endurance", ExerciseType.JUMPING_JACKS, onStart)
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
    themeMode: Int,
    useMaterialYou: Boolean,
    isHealthConnectAvailable: Boolean,
    isHealthConnectConnected: Boolean,
    hardAlarmManager: HardAlarmManager,
    alarmConfig: AlarmConfig,
    streakPrefs: StreakPreferences,
    streakInfo: StreakInfo,
    repo: SessionRepository,
    onConnectHealthConnect: () -> Unit,
    onSyncToHealthConnect: (onShowDialog: (String, String) -> Unit) -> Unit,
    onSyncFromHealthConnect: (onShowDialog: (String, String) -> Unit) -> Unit,
    onThemeModeChange: (Int) -> Unit,
    onUseMaterialYouChange: (Boolean) -> Unit,
    useCustomGoals: Boolean,
    customPushGoal: Int,
    customSquatGoal: Int,
    customBicepGoal: Int,
    onSaveDailyGoals: (useCustom: Boolean, push: Int, squat: Int, bicep: Int) -> Unit,
    xpRates: XpRates,
    onUpdateXpRates: (XpRates) -> Unit,
    onResetXpRates: () -> Unit,
    notionApiKey: String,
    notionDbId: String,
    onToggleLandmarks: (Boolean) -> Unit, 
    onExportJson: (android.net.Uri) -> Unit, 
    onImportJson: (android.net.Uri) -> Unit,
    onOpenManualEntry: () -> Unit,
    onSaveNotionKeys: (String, String) -> Unit,
    onSyncNotion: (onProgress: (current: Int, total: Int) -> Unit, onShowDialog: (String, String) -> Unit) -> Unit,
    onRetrieveNotion: (onProgress: (fetchedCount: Int) -> Unit, onShowDialog: (String, String) -> Unit) -> Unit,
    onShareProfile: () -> Unit,
    onStreakUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialogTitle by remember { mutableStateOf<String?>(null) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }
    var showXpRatesDialog by remember { mutableStateOf(false) }

    // Alarm States
    var showAlarmTimeDialog by remember { mutableStateOf(false) }
    var editAlarmTargetKey by remember { mutableStateOf<String?>(null) }
    var editAlarmTargetLabel by remember { mutableStateOf("") }
    var isVerifyingAlarm by remember { mutableStateOf(false) }

    // Streak States
    var showManualStreakDialog by remember { mutableStateOf(false) }

    // Notion Sync States
    var isSyncingNotion by remember { mutableStateOf(false) }
    var syncProgressText by remember { mutableStateOf("") }
    var isRetrievingNotion by remember { mutableStateOf(false) }
    var retrieveProgressText by remember { mutableStateOf("") }

    if (editAlarmTargetKey != null) {
        val currentTargetVal = alarmConfig.targets[editAlarmTargetKey!!] ?: 15
        var targetText by remember(editAlarmTargetKey) { mutableStateOf(currentTargetVal.toString()) }
        AlertDialog(
            onDismissRequest = { editAlarmTargetKey = null },
            title = { Text("Edit $editAlarmTargetLabel Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Set required count (reps or hold seconds) to turn off the alarm:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { targetText = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Target Reps/Seconds") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val newVal = targetText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                    val newTargets = alarmConfig.targets.toMutableMap()
                    newTargets[editAlarmTargetKey!!] = newVal
                    hardAlarmManager.updateTargets(newTargets)
                    editAlarmTargetKey = null
                }) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { editAlarmTargetKey = null }) { Text("Cancel") }
            }
        )
    }

    if (dialogTitle != null && dialogMessage != null) {
        AlertDialog(
            onDismissRequest = { dialogTitle = null; dialogMessage = null },
            confirmButton = { TextButton(onClick = { dialogTitle = null; dialogMessage = null }) { Text("OK") } },
            title = { Text(dialogTitle!!) },
            text = { Text(dialogMessage!!) }
        )
    }

    if (showAlarmTimeDialog) {
        var hourText by remember { mutableStateOf(alarmConfig.hour.toString()) }
        var minuteText by remember { mutableStateOf(alarmConfig.minute.toString()) }
        AlertDialog(
            onDismissRequest = { showAlarmTimeDialog = false },
            title = { Text("Set Alarm Time") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Enter time in 24-hour format (e.g. 07:00 or 18:30):", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.take(2) },
                            label = { Text("Hour (0-23)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.take(2) },
                            label = { Text("Minute (0-59)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val h = (hourText.toIntOrNull() ?: 7).coerceIn(0, 23)
                        val m = (minuteText.toIntOrNull() ?: 0).coerceIn(0, 59)
                        hardAlarmManager.setAlarmTime(h, m)
                        showAlarmTimeDialog = false
                        Toast.makeText(context, String.format(Locale.US, "Alarm set for %02d:%02d", h, m), Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Set Time") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAlarmTimeDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showManualStreakDialog) {
        var streakValText by remember { mutableStateOf(streakInfo.streak.toString()) }
        AlertDialog(
            onDismissRequest = { showManualStreakDialog = false },
            title = { Text("Set Workout Streak") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Manually override or calibrate your current streak count:", style = MaterialTheme.typography.bodyMedium)
                    OutlinedTextField(
                        value = streakValText,
                        onValueChange = { streakValText = it },
                        label = { Text("Streak Days") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val count = (streakValText.toIntOrNull() ?: 0).coerceAtLeast(0)
                        streakPrefs.setManualStreak(count)
                        onStreakUpdated()
                        showManualStreakDialog = false
                        Toast.makeText(context, "Streak updated to $count days! 🔥", Toast.LENGTH_SHORT).show()
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (streakInfo.isManual) {
                        TextButton(onClick = {
                            streakPrefs.resetToAutoStreak(0)
                            onStreakUpdated()
                            showManualStreakDialog = false
                            Toast.makeText(context, "Reset to automatic streak calculation", Toast.LENGTH_SHORT).show()
                        }) { Text("Auto") }
                    }
                    OutlinedButton(onClick = { showManualStreakDialog = false }) { Text("Cancel") }
                }
            }
        )
    }

    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let { onExportJson(it) }
    }
    
    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { onImportJson(it) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).padding(top = 48.dp, bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        
        // Pose Detection
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

        // Hard Workout Alarm Card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (alarmConfig.isRinging) Color(0xFF3E1F1F) else MaterialTheme.colorScheme.surface
            ),
            border = if (alarmConfig.isRinging) androidx.compose.foundation.BorderStroke(2.dp, Color(0xFFEF5350)) else null
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Hard Workout Alarm 🚨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (alarmConfig.isRinging) {
                                Surface(
                                    color = Color.Red,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("RINGING", modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Text(
                            "Locks max volume and forces auto-tracked workouts to turn off.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = alarmConfig.isEnabled,
                        onCheckedChange = { hardAlarmManager.setAlarmEnabled(it) }
                    )
                }

                // Alarm Time Selector
                val timeStr = String.format(Locale.US, "%02d:%02d", alarmConfig.hour, alarmConfig.minute)
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Alarm Time", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(timeStr, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(onClick = { showAlarmTimeDialog = true }) {
                        Icon(Icons.Default.Schedule, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Change Time")
                    }
                }

                Divider()

                // Target Workouts Section
                Text("Required Auto-Tracking Targets:", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                
                val availableWorkouts = listOf(
                    Triple("pushups", "Pushups", 15),
                    Triple("squats", "Squats", 20),
                    Triple("plank", "Plank (Secs)", 45),
                    Triple("crunches", "Crunches", 20),
                    Triple("tricep_dips", "Tricep Dips", 15),
                    Triple("jumping_jacks", "Jumping Jacks", 30),
                    Triple("high_knees", "High Knees", 30),
                    Triple("lateral_raises", "Lateral Raises", 15),
                    Triple("glute_bridges", "Glute Bridges", 20),
                    Triple("lunges", "Lunges", 20),
                    Triple("shoulder_press", "Shoulder Press", 15),
                    Triple("pullups", "Pullups", 8),
                    Triple("leg_raises", "Leg Raises", 20)
                )

                availableWorkouts.forEach { (key, label, defaultCount) ->
                    val currentTarget = alarmConfig.targets[key] ?: defaultCount
                    val isChecked = alarmConfig.targets.containsKey(key)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    val newTargets = alarmConfig.targets.toMutableMap()
                                    if (checked) newTargets[key] = currentTarget
                                    else newTargets.remove(key)
                                    hardAlarmManager.updateTargets(newTargets)
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodyMedium)
                        }
                        if (isChecked) {
                            TextButton(onClick = {
                                editAlarmTargetKey = key
                                editAlarmTargetLabel = label
                            }) {
                                Text("$currentTarget target ✏️", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                // Verify Button
                Button(
                    onClick = {
                        isVerifyingAlarm = true
                        scope.launch(Dispatchers.IO) {
                            val result = hardAlarmManager.verifyWorkouts(repo)
                            withContext(Dispatchers.Main) {
                                isVerifyingAlarm = false
                                if (result.isCompleted) {
                                    val intent = Intent(context, HardAlarmService::class.java).apply {
                                        action = HardAlarmService.ACTION_STOP_ALARM
                                    }
                                    context.startService(intent)
                                    dialogTitle = "🎉 Workout Verified!"
                                    dialogMessage = "Awesome job! You successfully completed all required auto-tracked workouts. The alarm has been turned off."
                                } else {
                                    dialogTitle = "⚠️ Workout Incomplete"
                                    val details = result.remainingTargets.entries.joinToString("\n") {
                                        "• ${Utils.capitalize(it.key.replace("_", " "))}: ${it.value} remaining"
                                    }
                                    dialogMessage = "You haven't finished all required auto-tracked workouts after the alarm rang:\n\n$details\n\nGo to the Workouts tab, turn on the camera tracker, and complete them to stop the alarm!"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (alarmConfig.isRinging) Color(0xFFEF5350) else MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.CheckCircle, null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (alarmConfig.isRinging) "Verify Workouts & Stop Alarm" else "Test Workout Verification")
                }
            }
        }

        // Streak & Freeze Management Card
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Streak & Freeze Protection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Streaks never reset on goal changes. Earn 1 freeze token every 15 streak days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Current Streak", style = MaterialTheme.typography.bodySmall)
                        Text("🔥 ${streakInfo.streak} Days", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        if (streakInfo.isManual) {
                            Text("(Manual Override Active)", style = MaterialTheme.typography.labelSmall, color = Color(0xFFFFB300))
                        }
                    }
                    Button(onClick = { showManualStreakDialog = true }) {
                        Icon(Icons.Default.Edit, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Set Streak")
                    }
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Freeze Balance", style = MaterialTheme.typography.bodySmall)
                        Text("🛡️ ${streakInfo.availableFreezes} Available", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF42A5F5))
                    }
                    OutlinedButton(
                        onClick = {
                            val yesterday = LocalDate.now().minusDays(1)
                            val dialog = android.app.DatePickerDialog(
                                context,
                                { _, year, month, dayOfMonth ->
                                    val picked = LocalDate.of(year, month + 1, dayOfMonth).toString()
                                    val used = streakPrefs.useStreakFreeze(picked, streakInfo.streak)
                                    if (used) {
                                        onStreakUpdated()
                                        Toast.makeText(context, "Applied streak freeze for $picked! 🛡️", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Date $picked is already protected or invalid!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                yesterday.year,
                                yesterday.monthValue - 1,
                                yesterday.dayOfMonth
                            )
                            dialog.datePicker.maxDate = System.currentTimeMillis()
                            dialog.setTitle("Select Date to Protect with Freeze")
                            dialog.show()
                        },
                        enabled = streakInfo.availableFreezes > 0
                    ) {
                        Text("Use Freeze (Pick Date)")
                    }
                }
            }
        }
        
        // Appearance
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text("Theme Mode", style = MaterialTheme.typography.bodyLarge)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == 0, onClick = { onThemeModeChange(0) })
                        Text("System")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == 1, onClick = { onThemeModeChange(1) })
                        Text("Light")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = themeMode == 2, onClick = { onThemeModeChange(2) })
                        Text("Dark")
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Material You", style = MaterialTheme.typography.bodyLarge)
                        Text("Use system dynamic colors", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = useMaterialYou, onCheckedChange = onUseMaterialYouChange)
                }
            }
        }

        // Daily Goals Card
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Daily Goals", style = MaterialTheme.typography.titleMedium)
                
                var customEnabled by remember(useCustomGoals) { mutableStateOf(useCustomGoals) }
                var pushText by remember(customPushGoal) { mutableStateOf(customPushGoal.toString()) }
                var squatText by remember(customSquatGoal) { mutableStateOf(customSquatGoal.toString()) }
                var bicepText by remember(customBicepGoal) { mutableStateOf(customBicepGoal.toString()) }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Custom Daily Goals", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (customEnabled) "Manual target values per day." else "Goals scale automatically with your level.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = customEnabled, onCheckedChange = { customEnabled = it })
                }

                if (customEnabled) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pushText,
                            onValueChange = { pushText = it },
                            label = { Text("Pushups") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = squatText,
                            onValueChange = { squatText = it },
                            label = { Text("Squats") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = bicepText,
                            onValueChange = { bicepText = it },
                            label = { Text("Curls") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Button(
                    onClick = {
                        val push = pushText.toIntOrNull() ?: 10
                        val squat = squatText.toIntOrNull() ?: 10
                        val bicep = bicepText.toIntOrNull() ?: 60
                        onSaveDailyGoals(customEnabled, push, squat, bicep)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Save Daily Goals")
                }
            }
        }

        // XP Rates & Grind Card
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("XP Progression & Grind", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Customize XP earned per repetition or minute of workout.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pushups / Squats", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.2f", xpRates.pushup)} XP / rep", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Bicep Curls", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.3f", xpRates.bicepCurl)} XP / rep", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Pullups", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.2f", xpRates.pullup)} XP / rep", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Manual Duration", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.2f", xpRates.manualDurationPerMin)} XP / min", fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("XP Multiplier", style = MaterialTheme.typography.bodyMedium)
                    Text("${String.format(Locale.US, "%.1f", xpRates.multiplier)}x", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showXpRatesDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Edit Rates")
                    }
                    OutlinedButton(
                        onClick = onResetXpRates,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Reset Defaults")
                    }
                }
            }
        }
        
        // Health Connect
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Health Connect Integration", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(16.dp))
                if (!isHealthConnectAvailable) {
                    Text("Health Connect is not available on this device.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                } else if (isHealthConnectConnected) {
                    Text("Connected to Health Connect ✓", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { onSyncToHealthConnect { title, msg -> dialogTitle = title; dialogMessage = msg } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ArrowUpward, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Export to Health Connect")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onSyncFromHealthConnect { title, msg -> dialogTitle = title; dialogMessage = msg } }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.ArrowDownward, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Import from Health Connect")
                    }
                } else {
                    Text("Connect to Google Health Connect to sync your workouts and track daily steps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onConnectHealthConnect, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Favorite, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Connect Health Connect")
                    }
                }
            }
        }
        
        // Notion Integration
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
                
                // Notion Sync status / indicator
                if (isSyncingNotion) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = syncProgressText.ifBlank { "Syncing with Notion..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (isRetrievingNotion) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text(
                            text = retrieveProgressText.ifBlank { "Retrieving from Notion..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { 
                            isSyncingNotion = true
                            syncProgressText = "Preparing sync..."
                            onSyncNotion(
                                { current, total ->
                                    syncProgressText = "Syncing workout $current of $total..."
                                },
                                { title, msg ->
                                    isSyncingNotion = false
                                    syncProgressText = ""
                                    dialogTitle = title
                                    dialogMessage = msg
                                }
                            )
                        }, 
                        enabled = !isSyncingNotion && !isRetrievingNotion,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isSyncingNotion) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Syncing...")
                        } else {
                            Text("Sync to Notion")
                        }
                    }
                    Button(
                        onClick = { 
                            isRetrievingNotion = true
                            retrieveProgressText = "Fetching pages..."
                            onRetrieveNotion(
                                { count ->
                                    retrieveProgressText = "Fetched $count sessions..."
                                },
                                { title, msg ->
                                    isRetrievingNotion = false
                                    retrieveProgressText = ""
                                    dialogTitle = title
                                    dialogMessage = msg
                                }
                            )
                        }, 
                        enabled = !isSyncingNotion && !isRetrievingNotion,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isRetrievingNotion) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                            Text("Retrieving...")
                        } else {
                            Text("Retrieve")
                        }
                    }
                }
            }
        }
        
        // Data Management
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
                    Icon(Icons.Default.Refresh, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import Data (JSON)")
                }
            }
        }
        
        // Manual Entry
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
        
        // Profile Sharing
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
        
        Spacer(Modifier.height(32.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Track Mate V4", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Orchestrated By Vinay Kumar with love ❤️", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (showXpRatesDialog) {
        EditXpRatesDialog(
            currentRates = xpRates,
            onDismiss = { showXpRatesDialog = false },
            onSave = { newRates ->
                showXpRatesDialog = false
                onUpdateXpRates(newRates)
            }
        )
    }
}

@Composable
private fun EditXpRatesDialog(
    currentRates: XpRates,
    onDismiss: () -> Unit,
    onSave: (XpRates) -> Unit
) {
    var pushup by remember { mutableStateOf(currentRates.pushup.toString()) }
    var squat by remember { mutableStateOf(currentRates.squat.toString()) }
    var bicep by remember { mutableStateOf(String.format(Locale.US, "%.4f", currentRates.bicepCurl)) }
    var lunge by remember { mutableStateOf(currentRates.lunge.toString()) }
    var shoulderPress by remember { mutableStateOf(currentRates.shoulderPress.toString()) }
    var jumpingJack by remember { mutableStateOf(currentRates.jumpingJack.toString()) }
    var pullup by remember { mutableStateOf(currentRates.pullup.toString()) }
    var manualRep by remember { mutableStateOf(currentRates.manualRep.toString()) }
    var manualDur by remember { mutableStateOf(currentRates.manualDurationPerMin.toString()) }
    var multiplier by remember { mutableStateOf(currentRates.multiplier.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize XP Rates") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = multiplier,
                    onValueChange = { multiplier = it },
                    label = { Text("Global XP Multiplier (e.g. 1.0, 1.5)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pushup,
                    onValueChange = { pushup = it },
                    label = { Text("Pushup XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = squat,
                    onValueChange = { squat = it },
                    label = { Text("Squat XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = bicep,
                    onValueChange = { bicep = it },
                    label = { Text("Bicep Curl XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pullup,
                    onValueChange = { pullup = it },
                    label = { Text("Pullup XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = lunge,
                    onValueChange = { lunge = it },
                    label = { Text("Lunge XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = shoulderPress,
                    onValueChange = { shoulderPress = it },
                    label = { Text("Shoulder Press XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = jumpingJack,
                    onValueChange = { jumpingJack = it },
                    label = { Text("Jumping Jack XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualRep,
                    onValueChange = { manualRep = it },
                    label = { Text("Manual Reps XP / rep") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = manualDur,
                    onValueChange = { manualDur = it },
                    label = { Text("Manual Duration XP / min") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val newRates = XpRates(
                        pushup = pushup.toFloatOrNull() ?: currentRates.pushup,
                        squat = squat.toFloatOrNull() ?: currentRates.squat,
                        bicepCurl = bicep.toFloatOrNull() ?: currentRates.bicepCurl,
                        lunge = lunge.toFloatOrNull() ?: currentRates.lunge,
                        shoulderPress = shoulderPress.toFloatOrNull() ?: currentRates.shoulderPress,
                        jumpingJack = jumpingJack.toFloatOrNull() ?: currentRates.jumpingJack,
                        pullup = pullup.toFloatOrNull() ?: currentRates.pullup,
                        manualRep = manualRep.toFloatOrNull() ?: currentRates.manualRep,
                        manualDurationPerMin = manualDur.toFloatOrNull() ?: currentRates.manualDurationPerMin,
                        multiplier = multiplier.toFloatOrNull() ?: currentRates.multiplier
                    )
                    onSave(newRates)
                }
            ) {
                Text("Save Rates")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun ProfileScreen(
    name: String,
    levelInfo: LevelSystem.LevelInfo,
    repo: SessionRepository,
    todaySteps: Long,
    isHealthConnectAvailable: Boolean,
    isHealthConnectConnected: Boolean,
    healthConnectManager: HealthConnectManager,
    achievementManager: AchievementManager,
    streakPrefs: StreakPreferences,
    streakInfo: StreakInfo,
    onNameChange: (String) -> Unit,
    onXpEarned: (Float) -> Unit,
    onViewHistory: () -> Unit,
    onStreakUpdated: () -> Unit = {}
) {
    val context = LocalContext.current
    var editing by remember { mutableStateOf(false) }
    var temp by remember { mutableStateOf(name) }
    
    // History & Stats Data
    var sessions by remember { mutableStateOf<List<SessionEntity>>(emptyList()) }
    var lineChartData by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var steps7DaysData by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }
    var heatMapData by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var achievements by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    
    var todayXp by remember { mutableStateOf(0f) }
    var weekXp by remember { mutableStateOf(0f) }
    var monthXp by remember { mutableStateOf(0f) }
    var activeDates by remember { mutableStateOf<Set<String>>(emptySet()) }
    var achievementTab by remember { mutableStateOf(0) } // 0=All, 1=Long Term, 2=Weekly

    fun refreshAchievements(currentSessions: List<SessionEntity>) {
        achievements = achievementManager.computeAchievements(
            currentSessions,
            emptyList(),
            streakInfo.streak,
            todaySteps
        )
    }

    LaunchedEffect(Unit) {
        val all = withContext(Dispatchers.IO) { repo.getAllSessions() }.sortedByDescending { it.timestampIso }
        sessions = all
        
        // XP Stats
        val today = LocalDate.now()

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
            
            val dailyReps = all.filter { !it.exercise.equals("steps", ignoreCase = true) }
                .groupBy { parseDate(it.timestampIso) }
                .mapValues { it.value.sumOf { s -> s.reps } }
            
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

        // Load 7 days steps
        steps7DaysData = healthConnectManager.getLast7DaysSteps()

        // Load achievements
        refreshAchievements(all)
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

        // Streak & Freeze Protection Card
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("🔥 ${streakInfo.streak} Days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Surface(
                            color = Color(0xFF1E88E5).copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                "🛡️ ${streakInfo.availableFreezes} Freezes",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = Color(0xFF42A5F5),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Text("1 Freeze earned every 15 streak days", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedButton(
                    onClick = {
                        val yesterday = LocalDate.now().minusDays(1)
                        val dialog = android.app.DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                val picked = LocalDate.of(year, month + 1, dayOfMonth).toString()
                                val used = streakPrefs.useStreakFreeze(picked, streakInfo.streak)
                                if (used) {
                                    onStreakUpdated()
                                    Toast.makeText(context, "Streak freeze activated for $picked! 🛡️", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Date $picked is already protected or invalid!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            yesterday.year,
                            yesterday.monthValue - 1,
                            yesterday.dayOfMonth
                        )
                        dialog.datePicker.maxDate = System.currentTimeMillis()
                        dialog.setTitle("Select Date for Streak Freeze")
                        dialog.show()
                    },
                    enabled = streakInfo.availableFreezes > 0
                ) {
                    Text("Use Freeze")
                }
            }
        }

        // XP Stats Cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            XpStatCard("Today", todayXp, Modifier.weight(1f))
            XpStatCard("7 Days", weekXp, Modifier.weight(1f))
            XpStatCard("30 Days", monthXp, Modifier.weight(1f))
        }

        if (isHealthConnectAvailable && isHealthConnectConnected) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Today's Steps", style = MaterialTheme.typography.titleMedium)
                    Text("$todaySteps", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
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

        // Steps Trend (Last 7 Days)
        Text("Steps Trend (Last 7 Days)", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                StepsBarChart(data = steps7DaysData, modifier = Modifier.fillMaxWidth())
            }
        }

        // Accomplishments & Dopamine Achievements Section
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Accomplishments", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            val unlockedCount = achievements.count { it.isUnlocked }
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "$unlockedCount / ${achievements.size} Unlocked",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Tabs: Weekly, Long Term, Completed
        TabRow(
            selectedTabIndex = achievementTab,
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(selected = achievementTab == 0, onClick = { achievementTab = 0 }, text = { Text("Weekly") })
            Tab(selected = achievementTab == 1, onClick = { achievementTab = 1 }, text = { Text("Long Term") })
            Tab(selected = achievementTab == 2, onClick = { achievementTab = 2 }, text = { Text("Completed") })
        }

        val filteredAchievements = when (achievementTab) {
            0 -> achievements.filter { it.type == AchievementType.WEEKLY && !it.isClaimed }
            1 -> achievements.filter { it.type == AchievementType.LONG_TERM && !it.isClaimed }
            2 -> achievements.filter { it.isClaimed }
            else -> emptyList()
        }

        if (filteredAchievements.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        when (achievementTab) {
                            0 -> "🎉 All weekly accomplishments completed!"
                            1 -> "🏆 All long-term accomplishments completed!"
                            else -> "No completed accomplishments yet. Start working out to unlock them! 💪"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            filteredAchievements.forEach { ach ->
                val progress = if (ach.target > 0) (ach.current.toFloat() / ach.target).coerceIn(0f, 1f) else 0f
                val isClaimable = ach.isUnlocked && !ach.isClaimed

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isClaimable) Color(0xFF2E2210) else MaterialTheme.colorScheme.surface
                    ),
                    border = if (isClaimable) androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFFFFD54F)) else null
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (ach.isUnlocked) Color(0xFF3E2723) else Color.DarkGray.copy(alpha = 0.3f),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(ach.icon, fontSize = 20.sp)
                                    }
                                }
                                Column {
                                    Text(ach.title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                                    Text(ach.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            if (ach.isClaimed) {
                                Surface(
                                    color = Color(0xFF2E7D32).copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("CLAIMED ⭐", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF81C784), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            } else if (isClaimable) {
                                Button(
                                    onClick = {
                                        achievementManager.claimAchievement(ach, repo) { newXp ->
                                            onXpEarned(newXp)
                                            refreshAchievements(sessions)
                                            Toast.makeText(context, "🎉 ${ach.title} Claimed! +${ach.xpReward.toInt()} XP Earned!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300), contentColor = Color.Black),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("+${ach.xpReward.toInt()} XP ⚡", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else {
                                Surface(
                                    color = Color.Gray.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("+${ach.xpReward.toInt()} XP", modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }

                        // Progress bar
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = if (ach.isUnlocked) Color(0xFFFFB300) else MaterialTheme.colorScheme.primary,
                            trackColor = Color.Gray.copy(alpha = 0.2f)
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(ach.category, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                            Text("${ach.current} / ${ach.target}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // History List (Workouts only, excluding achievements)
        Text("Recent History", style = MaterialTheme.typography.titleMedium)
        sessions.filter { !it.exercise.startsWith("Achievement", ignoreCase = true) }.take(10).forEach { session ->
            val name = Utils.capitalize(session.exercise.replace("_", " "))
            ListItem(
                leadingContent = {
                    WorkoutSourceBadge(isManual = session.isManual, modifier = Modifier.size(32.dp))
                },
                headlineContent = { Text(name, fontWeight = FontWeight.Bold) },
                supportingContent = { 
                    val durationMins = (session.durationSeconds / 60).toInt()
                    val details = if (durationMins > 0 && session.reps > 0) {
                        "${session.reps} reps • $durationMins mins • ${String.format(Locale.US, "%.2f", session.totalXp)} XP • ${session.timestampIso.take(10)}"
                    } else if (durationMins > 0) {
                        "$durationMins mins • ${String.format(Locale.US, "%.2f", session.totalXp)} XP • ${session.timestampIso.take(10)}"
                    } else {
                        "${session.reps} reps • ${String.format(Locale.US, "%.2f", session.totalXp)} XP • ${session.timestampIso.take(10)}"
                    }
                    Text(details)
                }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryDialog(
    manualWorkoutPrefs: ManualWorkoutPreferences,
    xpRates: XpRates,
    onDismiss: () -> Unit,
    onSave: (exercise: String, reps: Int, durationMins: Int, isCustom: Boolean, isDurationBased: Boolean, dateMs: Long?) -> Unit
) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme() || MaterialTheme.colorScheme.background.red < 0.5f
    val textColor = if (isDark) Color.White else Color.Black
    
    val workoutTypes by manualWorkoutPrefs.workoutTypes.collectAsState()

    var selectedType by remember { mutableStateOf(workoutTypes.firstOrNull()?.name ?: "Pushups") }
    var customName by remember { mutableStateOf("") }
    var isDurationBased by remember { mutableStateOf(false) }
    var repsInput by remember { mutableStateOf("") }
    var durationIndex by remember { mutableStateOf(4) } // index for 10 mins
    var showManageTypesDialog by remember { mutableStateOf(false) }
    
    val durationOptions = listOf(1, 2, 3, 5, 10, 15, 20, 30, 45, 60)

    // Sync isDurationBased when selectedType changes
    LaunchedEffect(selectedType, workoutTypes) {
        val found = workoutTypes.find { it.name.equals(selectedType, ignoreCase = true) }
        if (found != null && selectedType != "Other") {
            isDurationBased = found.isDurationBased
        }
    }
    
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card(Modifier.fillMaxWidth().padding(16.dp)) {
            Column(
                Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Manual Entry", style = MaterialTheme.typography.headlineSmall, color = textColor, fontWeight = FontWeight.Bold)
                
                var expanded by remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(selectedType, color = textColor)
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            workoutTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { 
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(type.name)
                                            if (type.isCustom) {
                                                Spacer(Modifier.width(6.dp))
                                                Surface(
                                                    color = MaterialTheme.colorScheme.primaryContainer,
                                                    shape = MaterialTheme.shapes.extraSmall
                                                ) {
                                                    Text("Custom", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                                }
                                            }
                                        }
                                    }, 
                                    onClick = { 
                                        selectedType = type.name
                                        isDurationBased = type.isDurationBased
                                        expanded = false 
                                    }
                                )
                            }
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Other (Custom One-off)") },
                                onClick = { 
                                    selectedType = "Other"
                                    expanded = false 
                                }
                            )
                        }
                    }

                    IconButton(onClick = { showManageTypesDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Manage Types", tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                var showDatePicker by remember { mutableStateOf(false) }
                var selectedDateMs by remember { mutableStateOf<Long?>(null) }
                val datePickerState = rememberDatePickerState()
                
                if (showDatePicker) {
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = { 
                                selectedDateMs = datePickerState.selectedDateMillis 
                                showDatePicker = false 
                            }) { Text("OK") }
                        }
                    ) {
                        DatePicker(state = datePickerState)
                    }
                }
                
                val dateText = selectedDateMs?.let { 
                    Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                } ?: "Today"
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.DateRange, null)
                    Spacer(Modifier.width(8.dp))
                    Text(dateText, color = textColor)
                }
                
                val isOther = selectedType == "Other"
                
                if (isOther) {
                    OutlinedTextField(
                        value = customName, 
                        onValueChange = { customName = it }, 
                        label = { Text("Workout Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tracking Mode:", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { isDurationBased = false }, 
                            colors = ButtonDefaults.textButtonColors(contentColor = if (!isDurationBased) MaterialTheme.colorScheme.primary else Color.Gray)
                        ) {
                            Text("Reps")
                        }
                        TextButton(
                            onClick = { isDurationBased = true }, 
                            colors = ButtonDefaults.textButtonColors(contentColor = if (isDurationBased) MaterialTheme.colorScheme.primary else Color.Gray)
                        ) {
                            Text("Duration")
                        }
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
                        label = { Text("Reps") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
                
                val currentReps = repsInput.toIntOrNull() ?: 0
                val currentDur = durationOptions[durationIndex]
                val estimatedXp = if (isDurationBased) {
                    LevelSystem.xpForManualDuration(currentDur, xpRates)
                } else {
                    if (isOther) LevelSystem.xpForManualReps(currentReps, xpRates)
                    else LevelSystem.xpForStandardExercise(selectedType, currentReps, xpRates)
                }

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Estimated Reward:", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "+${String.format(Locale.US, "%.2f", estimatedXp)} XP",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = textColor.copy(alpha=0.7f)) }
                    Button(onClick = {
                        val finalName = if (isOther) customName.ifBlank { "Custom Workout" } else selectedType
                        val finalReps = if (isDurationBased) 0 else (repsInput.toIntOrNull() ?: 0)
                        val finalDuration = if (isDurationBased) durationOptions[durationIndex] else 0
                        onSave(finalName, finalReps, finalDuration, isOther, isDurationBased, selectedDateMs)
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }

    if (showManageTypesDialog) {
        ManageWorkoutTypesDialog(
            prefs = manualWorkoutPrefs,
            onDismiss = { showManageTypesDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageWorkoutTypesDialog(
    prefs: ManualWorkoutPreferences,
    onDismiss: () -> Unit
) {
    val types by prefs.workoutTypes.collectAsState()
    var newTypeName by remember { mutableStateOf("") }
    var newTypeDurationBased by remember { mutableStateOf(false) }
    var editingType by remember { mutableStateOf<ManualWorkoutType?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Manual Workout Types") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    "Custom types will only appear in the Manual Entry dropdown (not in camera workouts).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Add new type section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (editingType == null) "Add Custom Workout Type" else "Edit Custom Workout Type", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        OutlinedTextField(
                            value = newTypeName,
                            onValueChange = { newTypeName = it },
                            label = { Text("Type Name (e.g. Bench Press)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Tracking Mode:", modifier = Modifier.weight(1f), fontSize = 13.sp)
                            FilterChip(
                                selected = !newTypeDurationBased,
                                onClick = { newTypeDurationBased = false },
                                label = { Text("Reps") }
                            )
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = newTypeDurationBased,
                                onClick = { newTypeDurationBased = true },
                                label = { Text("Duration") }
                            )
                        }
                        Button(
                            onClick = {
                                if (newTypeName.isNotBlank()) {
                                    if (editingType == null) {
                                        prefs.addCustomType(newTypeName, newTypeDurationBased)
                                    } else {
                                        prefs.updateCustomType(editingType!!.id, newTypeName, newTypeDurationBased)
                                        editingType = null
                                    }
                                    newTypeName = ""
                                }
                            },
                            enabled = newTypeName.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (editingType == null) "Add Type" else "Update Type")
                        }
                        if (editingType != null) {
                            TextButton(
                                onClick = {
                                    editingType = null
                                    newTypeName = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Cancel Edit")
                            }
                        }
                    }
                }

                // Existing types list
                Text("Saved Workout Types", fontWeight = FontWeight.Bold)
                types.forEach { type ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(type.name, fontWeight = FontWeight.Medium)
                                if (type.isCustom) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = MaterialTheme.shapes.extraSmall
                                    ) {
                                        Text("Custom", fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
                                    }
                                }
                            }
                            Text(
                                if (type.isDurationBased) "Duration-based" else "Reps-based",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (type.isCustom) {
                            IconButton(onClick = {
                                editingType = type
                                newTypeName = type.name
                                newTypeDurationBased = type.isDurationBased
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                            }
                            IconButton(onClick = { prefs.deleteCustomType(type.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    Divider()
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

private fun shareProfileStats(
    context: Context,
    name: String,
    xp: Float,
    levelInfo: LevelSystem.LevelInfo,
    highestStreak: Int,
    currentStreak: Int
) {
    val width = 800
    val height = 800
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    
    // Gradient Background
    val paint = Paint().apply { isAntiAlias = true }
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
        textAlign = Paint.Align.CENTER
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
    canvas.drawText("Total XP: ${String.format(Locale.US, "%.2f", xp)}", width / 2f, 420f, paint)
    
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
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, "profile_share.png")
        val stream = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        stream.close()
        
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share Profile"))
    } catch (e: Exception) {
        e.printStackTrace()
    }
}
