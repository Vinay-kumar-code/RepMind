@echo off
setlocal

echo 🏋️ Workout Tracker Setup Script
echo ================================
echo.

REM Check if we're in the right directory
if not exist "app\build.gradle" (
    echo ❌ Error: Please run this script from the project root directory
    echo    ^(The directory containing app\build.gradle^)
    pause
    exit /b 1
)

REM Create assets directory if it doesn't exist
set "ASSETS_DIR=app\src\main\assets"
if not exist "%ASSETS_DIR%" mkdir "%ASSETS_DIR%"

echo 📁 Assets directory created: %ASSETS_DIR%

REM Check if model file already exists
set "MODEL_FILE=%ASSETS_DIR%\pose_landmarker_full.task"
if exist "%MODEL_FILE%" (
    echo ✅ MediaPipe model file already exists: %MODEL_FILE%
    for %%I in ("%MODEL_FILE%") do echo    File size: %%~zI bytes
    set /p "REPLY=Do you want to re-download it? (y/N): "
    if /i not "%REPLY%"=="y" (
        echo Skipping download.
        echo.
        echo ✅ Setup complete! You can now open the project in Android Studio.
        pause
        exit /b 0
    )
)

echo.
echo 📥 Downloading MediaPipe pose_landmarker_full.task model...
echo    This may take a few minutes depending on your internet connection...

REM Download the model file using PowerShell
set "MODEL_URL=https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"

powershell -Command "try { Invoke-WebRequest -Uri '%MODEL_URL%' -OutFile '%MODEL_FILE%' -ErrorAction Stop; exit 0 } catch { Write-Host 'Download failed'; exit 1 }"

if %errorlevel% neq 0 (
    echo ❌ Error: Download failed. Please try again or download manually.
    echo.
    echo Manual download instructions:
    echo 1. Visit: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
    echo 2. Download: pose_landmarker_full.task
    echo 3. Place it in: %MODEL_FILE%
    pause
    exit /b 1
)

REM Check if download was successful
if exist "%MODEL_FILE%" (
    for %%I in ("%MODEL_FILE%") do set "FILE_SIZE=%%~zI"
    echo ✅ Successfully downloaded MediaPipe model file
    echo    Location: %MODEL_FILE%
    echo    Size: %FILE_SIZE% bytes
) else (
    echo ❌ Error: Download failed. Please try again or download manually.
    echo.
    echo Manual download instructions:
    echo 1. Visit: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
    echo 2. Download: pose_landmarker_full.task
    echo 3. Place it in: %MODEL_FILE%
    pause
    exit /b 1
)

echo.
echo 🎉 Setup complete!
echo.
echo Next steps:
echo 1. Open Android Studio
echo 2. Import this project
echo 3. Sync Gradle files
echo 4. Build and run on a physical device with camera
echo.
echo ⚠️  Important notes:
echo - Test on a physical device ^(emulator camera may not work well^)
echo - Grant camera permissions when prompted
echo - Ensure good lighting for better pose detection
echo.
echo Happy coding! 💪
pause
