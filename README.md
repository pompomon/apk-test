# apk-test

A simple Android "Hello World" application demonstrating a minimal Android project with tests and a CI build pipeline.

## Features

- **Hello World screen** – displays a centered "Hello World!" message
- **Unit tests** – run locally with `./gradlew testDebugUnitTest`
- **Instrumented tests** – run on a device/emulator with `./gradlew connectedDebugAndroidTest`
- **CI pipeline** – GitHub Actions workflow that runs unit tests, instrumented tests, and builds an unsigned release APK on every push/PR

## Prerequisites

- JDK 17
- Android SDK (API 34)
- Android emulator or physical device (required for `connectedDebugAndroidTest`)

## Building

```bash
# Run unit tests
./gradlew testDebugUnitTest

# Build unsigned release APK
./gradlew assembleRelease
```

The unsigned release APK is output to `app/build/outputs/apk/release/app-release-unsigned.apk`.

## CI / Build Pipeline

Every push and pull request to `main` triggers the [Android CI](.github/workflows/build.yml) workflow which runs two parallel jobs:

**unit-test-and-build**
1. Runs unit tests (`testDebugUnitTest`)
2. Builds the unsigned release APK (`assembleRelease`)
3. Uploads `app-release-unsigned.apk` as a downloadable GitHub Actions artifact

**instrumented-test**
1. Boots an Android emulator (API 29, x86_64)
2. Runs the instrumented test suite (`connectedDebugAndroidTest`)