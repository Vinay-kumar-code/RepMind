# RepMind – AI-Powered Workout Tracker

RepMind is a modern Android workout tracking app that uses real-time AI pose detection to automatically count push-ups, score your form, and track your fitness progress over time. Built with Kotlin, Jetpack Compose, CameraX, and MediaPipe Tasks Vision.

## Features

### 🎯 AI-Powered Workout Tracking
- **Real-time Pose Detection** – MediaPipe Pose Landmarker analyzes your form in real-time
- **Automatic Rep Counting** – Counts push-ups automatically from camera input
- **Form Quality Scoring** – AI evaluates your form and awards XP accordingly
- **Live Feedback** – See your rep count, XP, and status update in real-time

### 📊 Analytics & Progress Tracking
- **Workout History** – Detailed list of all past sessions with stats
- **Activity Heatmap** – Visual representation of workout consistency (last 90 days)
- **Reps Trend Chart** – Line chart showing daily rep progression (last 14 days)
- **Total Statistics** – Lifetime sessions, total reps, and total XP earned

### 💎 Modern Android Stack
- **Jetpack Compose + Material 3** – Beautiful, modern UI
- **CameraX** – Smooth camera preview and frame processing
- **Room Database** – Local persistence of all workout sessions
- **Navigation Compose** – Seamless navigation between screens
- **Kotlin Coroutines** – Efficient background processing

## Requirements

- **Android Studio**: Hedgehog (2023.1.1) or newer
- **Android SDK**: 34
- **Minimum API Level**: 26 (Android 8.0)
- **Device**: Physical Android device with camera (strongly recommended)
- **Java**: JDK 17+ (bundled with Android Studio)

## MediaPipe Model Setup ⚠️

**Critical Step**: The app requires the MediaPipe model file to function.

1. **Download the model:**
   - Visit: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
   - Download `pose_landmarker_full.task` (full model)

2. **Add to project:**
   - Create folder: `app/src/main/assets/`
   - Place file as: `app/src/main/assets/pose_landmarker_full.task`

> Without this model file, pose detection will not work and the app cannot count reps.


## Quick Start

### 1. Clone & Open Project
```bash
git clone https://github.com/Vinay-kumar-code/RepMind.git
cd RepMind
```

Open in Android Studio: `File > Open` → Select the `RepMind` folder

### 2. Add MediaPipe Model
- Download `pose_landmarker_full.task` (see above)
- Place in `app/src/main/assets/pose_landmarker_full.task`

### 3. Build & Run
```bash
# Set JAVA_HOME (if needed)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"

# Build debug APK
.\gradlew.bat assembleDebug

# Or use Android Studio:
# Click Run (▶️) and select your connected device
```

**Debug APK Location:** `app/build/outputs/apk/debug/app-debug.apk`

### 4. Grant Permissions
- Allow camera access when prompted
- Position device so camera can see your upper body
- Start your first workout!

## How to Use

1. **Start Workout** – Tap "Start Session" on the main screen
2. **Position Camera** – Ensure your upper body is fully visible in the camera frame
3. **Perform Push-ups** – The app automatically counts reps and scores form
4. **Track Progress** – Watch your rep count and XP increase in real-time
5. **Save Session** – Tap "Save Session" when you're done
6. **View History** – Check the History screen for detailed stats, charts, and trends


## Architecture & Components

### Core Components

#### 📹 **PoseClassifier** (`PoseClassifier.kt`)
- Wraps MediaPipe Tasks Vision Pose Landmarker
- Processes camera frames and extracts pose landmarks
- Manages MediaPipe lifecycle and resource cleanup
- Returns real-time pose data to the workout engine

#### 🏋️ **WorkoutEngine** (`WorkoutEngine.kt`)
- Pure Kotlin business logic (no Android dependencies)
- Analyzes joint angles (elbows, shoulders) to detect push-up phases
- Counts reps when complete down→up cycle is detected
- Scores form quality and computes XP per rep
- Provides callbacks for UI updates

**Customize thresholds:**
```kotlin
val downThreshold = 90f   // Angle for "down" position
val upThreshold = 160f    // Angle for "up" position
```

**Adjust XP scoring:**
```kotlin
private fun computeXp(leftAngle: Float, rightAngle: Float): Int {
    // Custom scoring based on form symmetry and depth
}
```

#### 💾 **Data Layer** (Room Database)
- **SessionEntity** – Stores workout data (exercise, reps, duration, XP, timestamp)
- **SessionDatabase** – Room database configuration
- **SessionRepository** – Clean API for data operations (insert, query all sessions)

#### 📊 **HistoryScreen** (`HistoryScreen.kt`)
- Displays all past workout sessions (sorted newest first)
- Shows aggregate stats: total sessions, reps, XP
- **Activity Heatmap** – Visual calendar showing workout frequency
- **Reps Trend Chart** – Line graph of daily rep counts
- Session cards with detailed per-workout stats


## Project Structure

```
RepMind/
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── pose_landmarker_full.task       # MediaPipe model (add manually)
│   │   ├── java/com/example/workouttracker/
│   │   │   ├── MainActivity.kt                 # Entry point
│   │   │   ├── PoseClassifier.kt               # MediaPipe wrapper
│   │   │   ├── WorkoutEngine.kt                # Rep counting logic
│   │   │   ├── Utils.kt                        # Helper functions
│   │   │   ├── db/
│   │   │   │   ├── SessionDatabase.kt          # Room DB setup
│   │   │   │   ├── SessionEntity.kt            # Workout data model
│   │   │   │   └── SessionRepository.kt        # Data access layer
│   │   │   └── ui/
│   │   │       ├── WorkoutApp.kt               # Main workout screen
│   │   │       ├── HistoryScreen.kt            # Analytics & history
│   │   │       ├── PreviewCameraView.kt        # Camera preview
│   │   │       └── theme/                      # Material 3 theme
│   │   ├── res/                                # Resources
│   │   └── AndroidManifest.xml
│   └── build.gradle                            # Module dependencies
├── build.gradle                                # Project config
├── settings.gradle                             # Gradle settings
└── .gitignore                                  # Git ignore rules
```

## Tech Stack

| Component | Technology |
|-----------|-----------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material 3 |
| **Camera** | CameraX |
| **ML/Pose Detection** | MediaPipe Tasks Vision 0.20230731 |
| **Database** | Room (SQLite) |
| **Concurrency** | Kotlin Coroutines |
| **Navigation** | Navigation Compose 2.7.7 |
| **Build Tool** | Gradle 8.4.2 |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 34 (Android 14) |



## Troubleshooting

### Build Issues

**"JAVA_HOME is not set"**
```powershell
# Set JAVA_HOME to Android Studio's JBR
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
```

**"Conflicting import" or Kotlin compilation errors**
- Already fixed in the latest version
- Run `.\gradlew.bat clean build` if issues persist

**Gradle sync fails**
- Check internet connection (needs to download dependencies)
- Use Android Studio's "Invalidate Caches / Restart"

### Runtime Issues

**App crashes on startup**
- ⚠️ Ensure `pose_landmarker_full.task` is in `app/src/main/assets/`
- Grant camera permission when prompted
- Check logcat for error messages

**Camera preview is black / frozen**
- Grant camera permission in Android settings
- Test on a physical device (emulator camera is unreliable)
- Restart the app

**No pose detection / reps not counting**
- Verify model file is correctly placed
- Ensure good lighting conditions
- Position your full upper body in frame
- Keep camera stable (use a stand or tripod)
- Check that you're performing clear push-up movements

**Inaccurate rep counting**
- Adjust thresholds in `WorkoutEngine.kt`:
  ```kotlin
  val downThreshold = 90f   // Lower = stricter "down"
  val upThreshold = 160f    // Higher = stricter "up"
  ```
- Ensure consistent form (full extension at top, 90° at bottom)
- Improve lighting and reduce camera shake

### Performance Tips

- Use a physical device (Pixel, Samsung, etc.) for best results
- Close other apps using the camera
- Ensure good lighting (natural or bright indoor light)
- Keep the device stable during workouts
- Avoid rapid/jerky movements


## Dependencies

```gradle
// Core Android
implementation 'androidx.core:core-ktx:1.12.0'
implementation 'androidx.activity:activity-compose:1.8.2'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'

// Jetpack Compose
implementation platform('androidx.compose:compose-bom:2023.10.01')
implementation 'androidx.compose.ui:ui'
implementation 'androidx.compose.material3:material3'
implementation 'androidx.compose.material:material-icons-extended'

// CameraX
implementation 'androidx.camera:camera-core:1.3.0'
implementation 'androidx.camera:camera-camera2:1.3.0'
implementation 'androidx.camera:camera-lifecycle:1.3.0'
implementation 'androidx.camera:camera-view:1.3.0'

// MediaPipe
implementation 'com.google.mediapipe:tasks-vision:0.20230731'

// Room Database
implementation 'androidx.room:room-runtime:2.6.1'
implementation 'androidx.room:room-ktx:2.6.1'
ksp 'androidx.room:room-compiler:2.6.1'

// Navigation
implementation 'androidx.navigation:navigation-compose:2.7.7'

// Material Components
implementation 'com.google.android.material:material:1.11.0'
```

## Future Enhancements

Potential features for future versions:

- [ ] **Multiple Exercise Types** – Squats, planks, sit-ups, etc.
- [ ] **Workout Programs** – Pre-built routines and challenges
- [ ] **Cloud Sync** – Backup data across devices
- [ ] **Social Features** – Share progress, leaderboards, challenges
- [ ] **Voice Coaching** – Audio feedback during workouts
- [ ] **Advanced Form Analysis** – Detailed form breakdown and tips
- [ ] **Wearable Integration** – Sync with fitness trackers
- [ ] **Custom Goals** – Set daily/weekly targets
- [ ] **Export Data** – CSV/PDF reports of workout history

## Contributing

Contributions are welcome! If you'd like to improve RepMind:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## License

This project is for educational and personal use.  
MediaPipe and other libraries are subject to their respective licenses (Apache 2.0 for MediaPipe).

## Support

Having issues or questions?

1. Check the [Troubleshooting](#troubleshooting) section above
2. Verify the MediaPipe model file is correctly placed
3. Test on a physical device with good lighting
4. Open an issue on GitHub with:
   - Android version and device model
   - Steps to reproduce the issue
   - Logcat output (if applicable)

---

**Built with ❤️ by [Vinay Kumar](https://github.com/Vinay-kumar-code)**

*Transform your fitness journey with AI-powered workout tracking!*

