# How to Build RepMind

## Prerequisites
- JDK 17 (which is set in the project options)
- Android SDK (configured in `local.properties`)

## Building the APK (Debug)

To build a debug APK that you can install on your phone immediately:

1.  **Open Terminal** in the project root (`c:\Users\vinay\OneDrive\Documents\Codes\RepMind`).
2.  **Run the Build Command**:
    ```powershell
    .\gradlew.bat assembleDebug
    ```
    *(Note: On Windows, use `.\gradlew.bat`. On Mac/Linux, use `./gradlew`)*

3.  **Locate the APK**:
    Once the build finishes successfully, you will find the APK file here:
    `app\build\outputs\apk\debug\app-debug.apk`

4.  **Install**:
    - Transfer this file to your phone via USB or Google Drive.
    - Or, if you have ADB installed and your phone connected:
      ```powershell
      adb install app\build\outputs\apk\debug\app-debug.apk
      ```

## Building for Release (Signed APK)
To build a version for the Play Store, you need to sign it.
1.  Generate a keystore file if you haven't already.
2.  Configure signing in `app/build.gradle`.
3.  Run:
    ```powershell
    .\gradlew.bat assembleRelease
    ```
