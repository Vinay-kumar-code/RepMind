# Track Mate V2 (formerly RepMind) – AI-Powered Workout Tracker

Track Mate V2 is a modern Android workout tracking app that uses real-time AI pose detection to automatically count reps, score your form, and track your fitness progress over time. Built with Kotlin, Jetpack Compose, CameraX, and MediaPipe Tasks Vision.

## Features

### 🎯 AI-Powered Workout Tracking
- **Real-time Pose Detection** – MediaPipe Pose Landmarker analyzes your form in real-time
- **Automatic Rep Counting** – Counts reps automatically from camera input
- **Multi-Exercise Support** – Supports Pushups, Squats, Lunges, Bicep Curls (Left/Right), Shoulder Press, and Jumping Jacks
- **Form Quality Scoring** – AI evaluates your form and awards XP accordingly
- **Live Feedback** – See your rep count, XP, and status update in real-time
- **Auto Workout Mode** – Automatically starts session on first rep and auto-saves after inactivity

### 📊 Analytics & Progress Tracking
- **Workout History** – Detailed list of all past sessions with stats
- **Calendar Streak** – Visualize your workout consistency with a monthly calendar view
- **XP Stats** – Track XP earned Today, Past 7 Days, and Past 30 Days
- **Reps Trend Chart** – Line chart showing daily rep progression (last 14 days)
- **Activity Heatmap** – Visual representation of workout consistency
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
2. **Select Exercise** – Choose from the list of available exercises
3. **Auto Mode (Optional)** – Toggle "A" icon for auto-start/auto-save functionality
4. **Position Camera** – Ensure your body is fully visible in the camera frame
5. **Perform Reps** – The app automatically counts reps and scores form
6. **Track Progress** – Watch your rep count and XP increase in real-time
7. **Save Session** – Tap "Save Session" when you're done (or let Auto Mode handle it)
8. **View History** – Check the Profile screen for streaks, XP stats, and history

## Architecture & Components

### Core Components

#### 📹 **PoseClassifier** (`PoseClassifier.kt`)
- Wraps MediaPipe Tasks Vision Pose Landmarker
- Processes camera frames and extracts pose landmarks
- Manages MediaPipe lifecycle and resource cleanup
- Returns real-time pose data to the workout engine

#### 🏋️ **WorkoutEngine** (`WorkoutEngine.kt`)
- Pure Kotlin business logic (no Android dependencies)
- Analyzes joint angles (elbows, shoulders, knees, hips) to detect exercise phases
- Counts reps when complete down→up cycle is detected
- Scores form quality and computes XP per rep
- Provides callbacks for UI updates

#### 💾 **Data Layer** (Room Database)
- **SessionEntity** – Stores workout data (exercise, reps, duration, XP, timestamp)
- **SessionDatabase** – Room database configuration
- **SessionRepository** – Clean API for data operations (insert, query all sessions, XP stats)

#### 📊 **VisualizationComponents** (`VisualizationComponents.kt`)
- **CalendarView** – Custom Compose component for streak visualization
- **LineChart** – Canvas-based chart for rep trends
- **ContributionHeatMap** – Github-style activity grid

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
│   │   │       ├── WorkoutSessionScreen.kt     # Active session UI
│   │   │       ├── VisualizationComponents.kt  # Charts & Graphs
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
- Check that you're performing clear movements

## Contributing

Contributions are welcome! If you'd like to improve Track Mate V2:

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
