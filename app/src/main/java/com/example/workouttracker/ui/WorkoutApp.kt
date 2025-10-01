package com.example.workouttracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    val progressManager = remember { ProgressManager(repo, appScope) }
    val dailyState by progressManager.dailyState.collectAsState()

    var profileXp by remember { mutableStateOf(0) }
    var levelInfo by remember { mutableStateOf(LevelSystem.levelFromXp(0)) }
    var showLevelUp by remember { mutableStateOf(false) }
    var profileName by remember { mutableStateOf("") }
    var startDestination by remember { mutableStateOf<String?>(null) }

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
        BottomDest("progress", "Progress", Icons.Default.BarChart),
        BottomDest("profile", "Profile", Icons.Default.Person)
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
            composable("progress") { ProgressScreen(repo = repo) }
            composable("profile") { ProfileScreen(name = profileName, levelInfo = levelInfo, xp = profileXp, dailyState = dailyState, repo = repo, onNameChange = { new -> profileName = new; appScope.launch(Dispatchers.IO) { repo.updateName(new) } }) }
            composable("session") {
                WorkoutSessionScreen(
                    repo = repo,
                    onBack = { nav.popBackStack() },
                    engine = engine,
                    performanceSettings = PerformanceSettings(),
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
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Choose Workout", style = MaterialTheme.typography.titleLarge)
        ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onStart(ExerciseType.PUSHUP) }) { Column(Modifier.padding(24.dp)) { Text("Pushups", style = MaterialTheme.typography.titleMedium); Text("Upper body strength") } }
        ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onStart(ExerciseType.SQUAT) }) { Column(Modifier.padding(24.dp)) { Text("Squats", style = MaterialTheme.typography.titleMedium); Text("Lower body power") } }
        ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onStart(ExerciseType.BICEP_LEFT) }) { Column(Modifier.padding(24.dp)) { Text("Bicep Curl - Left", style = MaterialTheme.typography.titleMedium); Text("Isolate left arm") } }
        ElevatedCard(modifier = Modifier.fillMaxWidth().clickable { onStart(ExerciseType.BICEP_RIGHT) }) { Column(Modifier.padding(24.dp)) { Text("Bicep Curl - Right", style = MaterialTheme.typography.titleMedium); Text("Isolate right arm") } }
    }
}

@Composable
private fun ProgressScreen(repo: SessionRepository) {
    var sessions by remember { mutableStateOf(listOf<com.example.workouttracker.db.SessionEntity>()) }
    LaunchedEffect(Unit) { sessions = withContext(Dispatchers.IO) { repo.getAllSessions() }.sortedByDescending { it.timestampIso }.take(50) }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text("Recent Sessions", style = MaterialTheme.typography.titleLarge) }
        items(sessions) { s -> ListItem(headlineContent = { Text("${s.exercise}  ${s.reps} reps") }, supportingContent = { Text(s.timestampIso.replace("T"," ").take(19)) }) }
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
    var monthDays by remember { mutableStateOf(emptyList<LocalDate>()) }
    var monthFirstWeekOffset by remember { mutableStateOf(0) }
    var goalDates by remember { mutableStateOf(setOf<LocalDate>()) }
    var longestStreak by remember { mutableStateOf(0) }
    var currentStreak by remember { mutableStateOf(dailyState?.streak ?: 0) }

    val today = remember { LocalDate.now(ZoneId.of("Asia/Kolkata")) }
    var displayYearMonth by remember { mutableStateOf(YearMonth.of(today.year, today.month)) }

    LaunchedEffect(displayYearMonth) {
        val first = displayYearMonth.atDay(1)
        val length = displayYearMonth.lengthOfMonth()
        monthDays = (0 until length).map { first.plusDays(it.toLong()) }
        monthFirstWeekOffset = (first.dayOfWeek.value % 7)
        val recents = withContext(Dispatchers.IO) { repo.getRecentDaily(370) }
        val met = recents.filter { it.goalsMet }.map { LocalDate.parse(it.date) }.toSet()
        goalDates = met
        // Current streak always computed relative to today (not the viewed month)
        var cur = 0; var d = today
        while (d in met) { cur++; d = d.minusDays(1) }
        currentStreak = cur
        val metSorted = met.sorted()
        var best = 0; var run = 0; var prev: LocalDate? = null
        for (day in metSorted) {
            if (prev == null || day == prev.plusDays(1)) run++ else run = 1
            if (run > best) best = run
            prev = day
        }
        longestStreak = best
    }

    val minYearMonth = remember { YearMonth.of(today.year, Month.JANUARY) }
    val maxYearMonth = remember { YearMonth.of(today.year, today.month) }
    val canPrev = displayYearMonth > minYearMonth
    val canNext = displayYearMonth < maxYearMonth

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        // Name edit section
        if (editing) {
            OutlinedTextField(value = temp, onValueChange = { temp = it.take(24) }, label = { Text("Name") })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (temp.trim().length>=2) { onNameChange(temp.trim()); editing=false } }) { Text("Save") }
                OutlinedButton(onClick = { editing=false; temp=name }) { Text("Cancel") }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(temp, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { editing = true }) { Text("Edit") }
            }
        }
        // Level / XP section
        Text("Level ${levelInfo.level}  ${levelInfo.rank}")
        LinearProgressIndicator(progress = levelInfo.progressPercent/100f, modifier = Modifier.fillMaxWidth())
        Text("XP: $xp  •  Next Level: ${levelInfo.xpNeededToLevel} XP")
        Text("Current streak: ${currentStreak} day${if (currentStreak==1) "" else "s"}", style = MaterialTheme.typography.bodyMedium)
        Text("Longest streak (last 12 months): $longestStreak day${if (longestStreak==1) "" else "s"}", style = MaterialTheme.typography.labelSmall)

        // Month navigation header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (canPrev) displayYearMonth = displayYearMonth.minusMonths(1) }, enabled = canPrev) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Previous Month")
            }
            Text(displayYearMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " ${displayYearMonth.year}", style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = { if (canNext) displayYearMonth = displayYearMonth.plusMonths(1) }, enabled = canNext) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Next Month")
            }
        }

        CalendarMonthGrid(
            monthDays = monthDays,
            firstOffset = monthFirstWeekOffset,
            goalDates = goalDates,
            today = today
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AssistChip(onClick = {}, label = { Text("Goal Met") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primaryContainer))
            AssistChip(onClick = {}, label = { Text("Today") })
            AssistChip(onClick = {}, label = { Text("Missed") }, colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant))
        }
    }
}

@Composable
private fun CalendarMonthGrid(
    monthDays: List<LocalDate>,
    firstOffset: Int,
    goalDates: Set<LocalDate>,
    today: LocalDate
) {
    val dayLabels = listOf("S","M","T","W","T","F","S")
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            dayLabels.forEach { d -> Text(d, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
        }
        val totalCells = firstOffset + monthDays.size
        val rows = (totalCells + 6) / 7
        var dayIndex = 0
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val cell = r*7 + c
                    if (cell < firstOffset || dayIndex >= monthDays.size) {
                        Box(Modifier.weight(1f).aspectRatio(1f)) {}
                    } else {
                        val date = monthDays[dayIndex++]
                        val met = date in goalDates
                        val isToday = date == today
                        val bg = when {
                            met -> MaterialTheme.colorScheme.primaryContainer
                            isToday -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                        val fg = if (met) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .padding(2.dp),
                            tonalElevation = if (isToday) 2.dp else 0.dp,
                            shape = MaterialTheme.shapes.small,
                            color = bg
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.labelMedium, color = fg, fontWeight = if (met) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }
}
