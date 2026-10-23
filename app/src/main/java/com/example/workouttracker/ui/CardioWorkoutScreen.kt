package com.example.workouttracker.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.workouttracker.CardioMetrics
import com.example.workouttracker.CardioWorkoutType
import com.example.workouttracker.LevelSystem
import com.example.workouttracker.StepCounterManager
import com.example.workouttracker.XpRates
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardioWorkoutScreen(
    workoutType: CardioWorkoutType,
    stepCounterManager: StepCounterManager,
    xpRates: XpRates? = null,
    mapsApiKey: String = "",
    onOpenSettings: (() -> Unit)? = null,
    onSessionSaved: (steps: Int, distanceMeters: Double, durationSeconds: Long, caloriesBurned: Double, xpEarned: Float) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(mapsApiKey) {
        if (mapsApiKey.isNotBlank()) {
            try {
                context.applicationInfo.metaData?.putString("com.google.android.geo.API_KEY", mapsApiKey)
            } catch (_: Exception) {}
        }
    }

    val metrics by stepCounterManager.metrics.collectAsState()

    // ── Location Tracking State ──────────────────────────────────────────
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var hasActivityRecognitionPermission by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) == PackageManager.PERMISSION_GRANTED
            } else true
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasActivityRecognitionPermission = permissions[Manifest.permission.ACTIVITY_RECOGNITION] == true
        }
    }

    // Request permissions on entry
    LaunchedEffect(Unit) {
        val neededPermissions = mutableListOf<String>()
        if (!hasLocationPermission) {
            neededPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            neededPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasActivityRecognitionPermission) {
            neededPermissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        if (neededPermissions.isNotEmpty()) {
            permissionLauncher.launch(neededPermissions.toTypedArray())
        }
    }

    // Route points tracked via GPS
    val routePoints = remember { mutableStateListOf<LatLng>() }
    var currentLatLng by remember { mutableStateOf<LatLng?>(null) }
    var gpsTotalDistanceMeters by remember { mutableStateOf(0.0) }

    // Maps camera
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(12.9716, 77.5946), 16f) // default
    }

    // Fused Location Client
    val fusedLocationClient = remember {
        LocationServices.getFusedLocationProviderClient(context)
    }

    val locationCallback = remember {
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                val location = result.lastLocation ?: return
                val latLng = LatLng(location.latitude, location.longitude)
                currentLatLng = latLng

                if (metrics.isActive && !metrics.isPaused) {
                    if (routePoints.isEmpty()) {
                        routePoints.add(latLng)
                    } else {
                        val lastPoint = routePoints.last()
                        val results = FloatArray(1)
                        android.location.Location.distanceBetween(
                            lastPoint.latitude, lastPoint.longitude,
                            latLng.latitude, latLng.longitude,
                            results
                        )
                        val deltaMeters = results[0].toDouble()
                        // Filter out GPS drift jitter
                        if (deltaMeters >= 1.5) {
                            routePoints.add(latLng)
                            gpsTotalDistanceMeters += deltaMeters
                            stepCounterManager.updateGpsDistance(gpsTotalDistanceMeters)
                        }
                    }

                    // Smoothly animate map camera to follow user
                    coroutineScope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(latLng))
                    }
                }
            }
        }
    }

    // Manage Location Updates with Lifecycle & Workout Active State
    @SuppressLint("MissingPermission")
    fun startLocationUpdates() {
        if (!hasLocationPermission) return
        try {
            val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2500L)
                .setMinUpdateIntervalMillis(1500L)
                .setMinUpdateDistanceMeters(1.5f)
                .build()

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

            // Also get immediate last location to focus map
            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                if (loc != null) {
                    val latLng = LatLng(loc.latitude, loc.longitude)
                    currentLatLng = latLng
                    coroutineScope.launch {
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(latLng, 16.5f)
                    }
                }
            }
        } catch (e: Exception) {
            // Permission or service unavailable
        }
    }

    fun stopLocationUpdates() {
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (_: Exception) {}
    }

    // Lifecycle observer to stop/start updates when app is active
    DisposableEffect(lifecycleOwner, hasLocationPermission) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (hasLocationPermission) {
                    startLocationUpdates()
                }
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                if (!metrics.isActive) {
                    stopLocationUpdates()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (hasLocationPermission) {
            startLocationUpdates()
        }

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            stopLocationUpdates()
            if (!metrics.isActive) {
                stepCounterManager.destroy()
            }
        }
    }

    // ── Dialog State ─────────────────────────────────────────────────────
    var showSummaryDialog by remember { mutableStateOf(false) }
    var showDiscardConfirmDialog by remember { mutableStateOf(false) }

    val accentColor = if (workoutType == CardioWorkoutType.WALKING) Color(0xFF10B981) else Color(0xFFFF5722)
    val workoutIcon = if (workoutType == CardioWorkoutType.WALKING) Icons.Default.DirectionsWalk else Icons.Default.DirectionsRun

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(workoutIcon, contentDescription = null, tint = accentColor)
                        Text(
                            text = workoutType.displayName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        if (metrics.isActive) {
                            Surface(
                                color = if (metrics.isPaused) Color(0xFFFFA000) else accentColor,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = if (metrics.isPaused) "PAUSED" else "ACTIVE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (metrics.isActive) {
                            showDiscardConfirmDialog = true
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (currentLatLng != null) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                cameraPositionState.animate(
                                    CameraUpdateFactory.newLatLngZoom(currentLatLng!!, 17f)
                                )
                            }
                        }) {
                            Icon(Icons.Default.MyLocation, contentDescription = "Center Map", tint = accentColor)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Permission banner if not granted
            if (!hasLocationPermission) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Location permission needed",
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Text(
                                "RepMind needs location to track your route and distance.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Button(
                            onClick = {
                                permissionLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.ACCESS_FINE_LOCATION,
                                        Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Enable")
                        }
                    }
                }
            }

            val isMapsKeyMissing = mapsApiKey.isBlank() && com.example.workouttracker.BuildConfig.MAPS_API_KEY.isBlank()
            if (isMapsKeyMissing) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Google Maps API key not set. Set it in Settings.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        if (onOpenSettings != null) {
                            TextButton(onClick = onOpenSettings) {
                                Text("Settings", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // ── Top Half: Google Map Route Tracker ────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(0.95f)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    properties = MapProperties(
                        isMyLocationEnabled = hasLocationPermission,
                        mapType = MapType.NORMAL
                    ),
                    uiSettings = MapUiSettings(
                        myLocationButtonEnabled = false,
                        zoomControlsEnabled = false,
                        compassEnabled = true
                    )
                ) {
                    // Draw Polyline route
                    if (routePoints.size > 1) {
                        Polyline(
                            points = routePoints,
                            color = accentColor,
                            width = 14f
                        )
                    }

                    // Start marker
                    if (routePoints.isNotEmpty()) {
                        Marker(
                            state = MarkerState(position = routePoints.first()),
                            title = "Start",
                            icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                        )
                    }
                }

                // Floating Step Count Badge on Map
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    shape = RoundedCornerShape(16.dp),
                    shadowElevation = 6.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(workoutIcon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                        Column {
                            Text(
                                text = String.format(Locale.US, "%,d", metrics.steps),
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "STEPS",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Bottom Half: Samsung Health Style Key Metrics Dashboard ───
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.05f),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Primary Metric: Distance & Duration
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Distance (km)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val km = metrics.distanceMeters / 1000.0
                            Text(
                                text = String.format(Locale.US, "%.2f", km),
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Distance (km)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        // Divider
                        Box(
                            modifier = Modifier
                                .height(44.dp)
                                .width(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )

                        // Duration
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val totalSec = metrics.durationSeconds
                            val h = totalSec / 3600
                            val m = (totalSec % 3600) / 60
                            val s = totalSec % 60
                            val timeStr = if (h > 0) {
                                String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
                            } else {
                                String.format(Locale.US, "%02d:%02d", m, s)
                            }
                            Text(
                                text = timeStr,
                                fontSize = 34.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Duration",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Secondary Metrics Grid (Samsung Health style: Pace, Speed, Calories, Cadence)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Average Pace
                        MetricItem(
                            icon = Icons.Default.Timer,
                            label = "Avg Pace",
                            value = formatPace(metrics.paceMinPerKm),
                            unit = "/km"
                        )

                        // Speed
                        MetricItem(
                            icon = Icons.Default.Speed,
                            label = "Speed",
                            value = String.format(Locale.US, "%.1f", metrics.speedKmPerH),
                            unit = "km/h"
                        )

                        // Calories
                        MetricItem(
                            icon = Icons.Default.LocalFireDepartment,
                            label = "Calories",
                            value = String.format(Locale.US, "%.0f", metrics.caloriesBurned),
                            unit = "kcal"
                        )

                        // Cadence
                        MetricItem(
                            icon = Icons.Default.Straighten,
                            label = "Cadence",
                            value = "${metrics.cadenceSpm}",
                            unit = "spm"
                        )
                    }

                    // ── Workout Controls ──────────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!metrics.isActive) {
                            // Big Start Button
                            Button(
                                onClick = {
                                    routePoints.clear()
                                    gpsTotalDistanceMeters = 0.0
                                    stepCounterManager.start(workoutType)
                                    startLocationUpdates()
                                },
                                modifier = Modifier
                                    .fillMaxWidth(0.7f)
                                    .height(58.dp),
                                shape = RoundedCornerShape(29.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                                    Text(
                                        "START ${workoutType.displayName.uppercase()}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp
                                    )
                                }
                            }
                        } else {
                            // Active: Pause / Resume + Finish buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (!metrics.isPaused) {
                                    // Pause Button
                                    OutlinedButton(
                                        onClick = { stepCounterManager.pause() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .padding(end = 8.dp),
                                        shape = RoundedCornerShape(27.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFFA000))
                                    ) {
                                        Icon(Icons.Default.Pause, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("PAUSE", fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    // Resume Button
                                    Button(
                                        onClick = { stepCounterManager.resume() },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(54.dp)
                                            .padding(end = 8.dp),
                                        shape = RoundedCornerShape(27.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                                    ) {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("RESUME", fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Stop / Finish Button
                                Button(
                                    onClick = {
                                        stepCounterManager.stop()
                                        stopLocationUpdates()
                                        showSummaryDialog = true
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(54.dp)
                                        .padding(start = 8.dp),
                                    shape = RoundedCornerShape(27.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935))
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = null)
                                    Spacer(Modifier.width(6.dp))
                                    Text("FINISH", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Workout Summary Dialog ───────────────────────────────────────────
    if (showSummaryDialog) {
        val earnedXp = LevelSystem.xpForCardio(
            workoutType = workoutType,
            steps = metrics.steps,
            distanceMeters = metrics.distanceMeters,
            durationSeconds = metrics.durationSeconds,
            rates = xpRates
        )

        AlertDialog(
            onDismissRequest = { /* Force explicit user choice */ },
            icon = {
                Icon(
                    workoutIcon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = {
                Text(
                    text = "${workoutType.displayName} Complete!",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Awesome effort! Here are your workout stats:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SummaryRow("Total Steps", String.format(Locale.US, "%,d", metrics.steps))
                            SummaryRow("Distance", String.format(Locale.US, "%.2f km", metrics.distanceMeters / 1000.0))
                            SummaryRow("Duration", formatDuration(metrics.durationSeconds))
                            SummaryRow("Calories Burned", String.format(Locale.US, "%.0f kcal", metrics.caloriesBurned))
                            SummaryRow("Average Pace", formatPace(metrics.paceMinPerKm) + " /km")
                            Divider(modifier = Modifier.padding(vertical = 4.dp))
                            SummaryRow("XP Earned", String.format(Locale.US, "+%.1f XP", earnedXp), highlight = true)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showSummaryDialog = false
                        onSessionSaved(
                            metrics.steps,
                            metrics.distanceMeters,
                            metrics.durationSeconds,
                            metrics.caloriesBurned,
                            earnedXp
                        )
                        Toast.makeText(context, "Workout saved! +${String.format(Locale.US, "%.1f", earnedXp)} XP", Toast.LENGTH_SHORT).show()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                ) {
                    Text("Save Workout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSummaryDialog = false
                        onBack()
                    }
                ) {
                    Text("Discard", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    // ── Discard Confirmation Dialog ──────────────────────────────────────
    if (showDiscardConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmDialog = false },
            title = { Text("Leave Workout?") },
            text = { Text("Your ongoing workout session will be stopped and discarded.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardConfirmDialog = false
                        stepCounterManager.stop()
                        stopLocationUpdates()
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Leave & Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmDialog = false }) {
                    Text("Stay")
                }
            }
        )
    }
}

@Composable
private fun MetricItem(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        Text(unit, fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun SummaryRow(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            fontSize = 13.sp,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (highlight) FontWeight.Bold else FontWeight.Normal
        )
        Text(
            value,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatPace(paceMinPerKm: Double): String {
    if (paceMinPerKm <= 0.0 || paceMinPerKm > 60.0) return "--'--\""
    val minutes = paceMinPerKm.toInt()
    val seconds = ((paceMinPerKm - minutes) * 60).toInt()
    return String.format(Locale.US, "%d'%02d\"", minutes, seconds)
}

private fun formatDuration(totalSec: Long): String {
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    val s = totalSec % 60
    return if (h > 0) {
        String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.US, "%02d:%02d", m, s)
    }
}
