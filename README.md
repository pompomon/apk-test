# apk-test

A simple Android "Hello World" application demonstrating a minimal Android project with instrumented tests and a CI build pipeline.

## Features

- **Hello World screen** – displays a centered "Hello World!" message
- **Instrumented tests** – run on a device/emulator with `./gradlew connectedDebugAndroidTest`
- **CI pipeline** – GitHub Actions workflow that runs instrumented tests and builds an installable debug APK on every push/PR to `main`

## Prerequisites

- JDK 17
- Android SDK (API 34)
- Android emulator or physical device (required for `connectedDebugAndroidTest`)

## Building

```bash
# Build installable debug APK (signed with the auto-generated debug keystore)
./gradlew assembleDebug
```

The debug APK is output to `app/build/outputs/apk/debug/app-debug.apk` and can be installed on a device or emulator with `adb install`.

## CI / Build Pipeline

Every push and pull request to `main` triggers the [Android CI](.github/workflows/build.yml) workflow which runs two sequential jobs:

**instrumented-test** (runs first)
1. Boots an Android emulator (API 29, x86_64)
2. Runs the instrumented test suite (`connectedDebugAndroidTest`)

**build** (runs only after `instrumented-test` succeeds)
1. Builds the installable debug APK (`assembleDebug`)
2. Uploads `app-debug.apk` as a downloadable GitHub Actions artifact