# Workout Tracker - MediaPipe Pose Detection App

A modern Android workout tracking application built with Kotlin, Jetpack Compose, and MediaPipe for real-time pose detection. Track your pushups with AI-powered form analysis and persistent session logging.

## Features

- **Real-time Pose Detection**: Uses MediaPipe to detect and analyze pushup form
- **Smart Rep Counting**: Automatic rep counting with form quality scoring
- **XP System**: Earn experience points based on exercise form quality
- **Session Tracking**: Persistent storage of workout sessions with Room database
- **Modern UI**: Beautiful Material 3 design with Jetpack Compose
- **Session History**: View past workout sessions with detailed statistics

## Prerequisites

Before importing this project into Android Studio, you need to:

### 1. Download MediaPipe Model File

Download the `pose_landmarker_full.task` file from MediaPipe:
- Visit: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
- Download the `pose_landmarker_full.task` model file
- Place it in: `app/src/main/assets/pose_landmarker_full.task`

**Important**: The app will not work without this model file!

### 2. System Requirements

- Android Studio Hedgehog (2023.1.1) or newer
- Android SDK 34
- Minimum Android API 24 (Android 7.0)
- Device with front-facing camera
- Recommended: Physical device for testing (camera functionality)

## Setup Instructions

### 1. Import Project
1. Open Android Studio
2. Select "Open an existing project"
3. Navigate to the project folder and select it
4. Wait for Gradle sync to complete

### 2. Add MediaPipe Model
1. Create the `assets` folder if it doesn't exist: `app/src/main/assets/`
2. Download `pose_landmarker_full.task` from MediaPipe
3. Place the file in the assets folder

### 3. Sync and Build
1. Click "Sync Project with Gradle Files"
2. Build the project (Build → Make Project)
3. Run on a physical device with camera

## Project Structure

```
app/
├── src/main/
│   ├── assets/
│   │   └── pose_landmarker_full.task          # MediaPipe model (you need to add this)
│   ├── java/com/example/workouttracker/
│   │   ├── MainActivity.kt                     # Main activity
│   │   ├── PoseClassifier.kt                   # MediaPipe wrapper
│   │   ├── WorkoutEngine.kt                    # Exercise logic
│   │   ├── Utils.kt                            # Utility functions
│   │   ├── db/
│   │   │   ├── SessionDatabase.kt              # Room database
│   │   │   └── SessionRepository.kt            # Data repository
│   │   └── ui/
│   │       ├── WorkoutApp.kt                   # Main UI screen
│   │       ├── HistoryScreen.kt                # Session history
│   │       ├── PreviewCameraView.kt            # Camera preview
│   │       └── theme/                          # Compose theme
│   ├── res/                                    # Resources
│   └── AndroidManifest.xml
├── build.gradle                                # Module build config
└── proguard-rules.pro                          # ProGuard rules
```

## Key Components

### PoseClassifier
- Wraps MediaPipe PoseLandmarker for real-time pose detection
- Converts camera frames to pose landmarks
- Handles MediaPipe lifecycle management

### WorkoutEngine
- Pure Kotlin class for exercise logic
- Implements rep counting and form scoring
- Provides callback interface for UI updates
- Smoothing algorithms for stable detection

### Room Database
- Persistent storage for workout sessions
- SessionEntity for data structure
- Repository pattern for data access

### Jetpack Compose UI
- Modern declarative UI with Material 3
- Camera preview with overlay
- Real-time statistics display
- Session history with statistics

## Usage

1. **Grant Camera Permission**: Allow camera access when prompted
2. **Start Session**: Tap "Start Session" to begin tracking
3. **Position Camera**: Hold device so front camera can see your upper body
4. **Perform Pushups**: App will automatically count reps and score form
5. **Save Session**: Tap "Save Session" when finished
6. **View History**: Tap history icon to see past sessions

## Customization

### Adjust Detection Thresholds
Edit `WorkoutEngine.kt`:
```kotlin
val downThreshold = 90f  // Angle for "down" position
val upThreshold = 160f   // Angle for "up" position
```

### Modify XP Scoring
Edit the `computeXp()` function in `WorkoutEngine.kt`:
```kotlin
private fun computeXp(left: Float, right: Float): Int {
    // Custom scoring logic here
}
```

### Add New Exercises
1. Extend `WorkoutEngine` with new detection logic
2. Add exercise types to database schema
3. Update UI for exercise selection

## Troubleshooting

### Common Issues

**App crashes on startup**
- Ensure `pose_landmarker_full.task` is in `app/src/main/assets/`
- Check camera permissions are granted

**No pose detection**
- Ensure good lighting conditions
- Position yourself fully in camera frame
- Check camera is not blocked

**Poor rep counting accuracy**
- Ensure proper pushup form
- Adjust detection thresholds in `WorkoutEngine.kt`
- Check camera stability

### Performance Tips

- Use physical device for testing (emulator camera may not work well)
- Ensure good lighting for better pose detection
- Keep camera stable during workout
- Close other camera apps before using

## Dependencies

Key libraries used:
- **MediaPipe Tasks Vision**: Pose detection
- **Jetpack Compose**: Modern UI framework
- **Room Database**: Local data persistence
- **CameraX**: Camera functionality
- **Material 3**: Design system

## License

This project is for educational purposes. MediaPipe is subject to Apache 2.0 license.

## Support

For issues or questions:
1. Check the troubleshooting section above
2. Verify MediaPipe model file is correctly placed
3. Ensure all permissions are granted
4. Test on physical device with camera

## Future Enhancements

Potential improvements:
- Multiple exercise types (squats, planks, etc.)
- Workout programs and routines
- Social features and challenges
- Exercise form feedback
- Voice coaching
- Wearable integration
