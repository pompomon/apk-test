# apk-test

A simple Android "Hello World" application demonstrating a minimal Android project with tests and a CI/CD build pipeline.

## Features

- **Hello World screen** – displays a centered "Hello World!" message
- **Unit tests** – run locally with `./gradlew testDebugUnitTest`
- **Instrumented tests** – run on a device/emulator with `./gradlew connectedDebugAndroidTest`
- **CI pipeline** – GitHub Actions workflow that builds an unsigned debug APK on every push/PR

## Prerequisites

- JDK 17
- Android SDK (API 34)

## Building

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug
```

The unsigned debug APK is output to `app/build/outputs/apk/debug/app-debug.apk`.

## CI / Build Pipeline

Every push and pull request to `main` triggers the [Android CI](.github/workflows/build.yml) workflow which:
1. Runs unit tests
2. Builds the debug APK
3. Uploads the APK as a downloadable GitHub Actions artifact