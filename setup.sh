#!/bin/bash

# Setup script for Workout Tracker Android App
# This script helps download the required MediaPipe model file

echo "🏋️ Workout Tracker Setup Script"
echo "================================"
echo ""

# Check if we're in the right directory
if [ ! -f "app/build.gradle" ]; then
    echo "❌ Error: Please run this script from the project root directory"
    echo "   (The directory containing app/build.gradle)"
    exit 1
fi

# Create assets directory if it doesn't exist
ASSETS_DIR="app/src/main/assets"
mkdir -p "$ASSETS_DIR"

echo "📁 Assets directory created: $ASSETS_DIR"

# Check if model file already exists
MODEL_FILE="$ASSETS_DIR/pose_landmarker_full.task"
if [ -f "$MODEL_FILE" ]; then
    echo "✅ MediaPipe model file already exists: $MODEL_FILE"
    echo "   File size: $(ls -lh "$MODEL_FILE" | awk '{print $5}')"
    read -p "Do you want to re-download it? (y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "Skipping download."
        echo ""
        echo "✅ Setup complete! You can now open the project in Android Studio."
        exit 0
    fi
fi

echo ""
echo "📥 Downloading MediaPipe pose_landmarker_full.task model..."
echo "   This may take a few minutes depending on your internet connection..."

# Download the model file
MODEL_URL="https://storage.googleapis.com/mediapipe-models/pose_landmarker/pose_landmarker_full/float16/latest/pose_landmarker_full.task"

if command -v curl >/dev/null 2>&1; then
    curl -L -o "$MODEL_FILE" "$MODEL_URL"
    DOWNLOAD_STATUS=$?
elif command -v wget >/dev/null 2>&1; then
    wget -O "$MODEL_FILE" "$MODEL_URL"
    DOWNLOAD_STATUS=$?
else
    echo "❌ Error: Neither curl nor wget found. Please install one of them or download manually."
    echo ""
    echo "Manual download instructions:"
    echo "1. Visit: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker"
    echo "2. Download: pose_landmarker_full.task"
    echo "3. Place it in: $MODEL_FILE"
    exit 1
fi

# Check if download was successful
if [ $DOWNLOAD_STATUS -eq 0 ] && [ -f "$MODEL_FILE" ]; then
    FILE_SIZE=$(ls -lh "$MODEL_FILE" | awk '{print $5}')
    echo "✅ Successfully downloaded MediaPipe model file"
    echo "   Location: $MODEL_FILE"
    echo "   Size: $FILE_SIZE"
else
    echo "❌ Error: Download failed. Please try again or download manually."
    echo ""
    echo "Manual download instructions:"
    echo "1. Visit: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker"
    echo "2. Download: pose_landmarker_full.task"
    echo "3. Place it in: $MODEL_FILE"
    exit 1
fi

echo ""
echo "🎉 Setup complete!"
echo ""
echo "Next steps:"
echo "1. Open Android Studio"
echo "2. Import this project"
echo "3. Sync Gradle files"
echo "4. Build and run on a physical device with camera"
echo ""
echo "⚠️  Important notes:"
echo "- Test on a physical device (emulator camera may not work well)"
echo "- Grant camera permissions when prompted"
echo "- Ensure good lighting for better pose detection"
echo ""
echo "Happy coding! 💪"
