# ForeFlight Clone

A native Android application inspired by ForeFlight, built with Kotlin and osmdroid for offline maps and voyage recording.

## Features

*   **Offline Maps:** Add `.map` vector files to navigate without an internet connection.
*   **Voyage Recording:** Record your flights/voyages in the background. It tracks your speed, altitude, heading, climb, and climb angle.
*   **Global Notes:** Long-press anywhere on the map to add persistent notes and hazards. Toggle their visibility globally via the Global Notes menu.
*   **Saved Voyages:** Review and replay your past saved voyages on the map.

## Building the App

This project uses Gradle.

### Local Build
You can build the APK locally using the Gradle wrapper:
```bash
./gradlew assembleDebug
```
The resulting APK will be located in `app/build/outputs/apk/debug/`.

### GitHub Actions
This repository is configured with a GitHub Actions workflow. Every push to the `main` branch will automatically build the APK.
1. Navigate to the **Actions** tab on GitHub.
2. Select the latest build run.
3. Download the `app-debug` artifact from the summary page and install it on your Android device.

## Technologies Used
*   Kotlin
*   Android SDK
*   osmdroid (for map rendering)
