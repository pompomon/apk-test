# apk-test

A lightweight Android 2D top-down maze game using libGDX rendering.

## Features

- Full-screen responsive maze renderer (libGDX + Android host UI overlay)
- Random solvable maze generation with deterministic seed support in core logic
- Mixed-width maze generation that introduces sparse 2-cell-wide corridor regions
- Player control modes:
  - Manual movement
  - Random walk with memory
  - Wall follower (left/right)
  - BFS exit solver
  - A* exit solver
- NPC behavior modes:
  - Direct chase
  - Predictive chase
  - Patrol/guard with alert-search transitions
- Difficulty presets that scale maze size, NPC count, and movement speed
- Easy/Medium balancing keeps NPC movement slower than player so escapes are possible
- Unit tests for maze generation/pathfinding/policies
- Instrumented UI smoke test for game host and controls

## Prerequisites

- JDK 17
- Android SDK (API 34)
- Android emulator or physical device for instrumented tests

## Build and test

```bash
# JVM unit tests
./gradlew testDebugUnitTest

# Build debug APK
./gradlew assembleDebug

# Instrumented tests (requires emulator/device)
./gradlew connectedDebugAndroidTest
```

Debug APK output:

`app/build/outputs/apk/debug/app-debug.apk`
