# AGENTS.md

> **Entry point for any AI coding agent (Copilot, Claude, etc.) working in this repository.** Read this first, then skim [`docs/lessons-learned.md`](docs/lessons-learned.md) for class-of-mistake recurring across previous PRs.

## Project at a glance

- Android 2D top-down maze game (`com.example.apktest`).
- **Kotlin** + libGDX (1.13.1) for rendering, AndroidX for the host UI.
- **JDK 17**, **compileSdk / targetSdk 34**, **minSdk 26**.
- Game logic lives in pure-Kotlin packages (no Android imports) so it can be unit-tested on the JVM.

## Commands you will use

```bash
# JVM unit tests (fast, runs in CI on every push)
./gradlew testDebugUnitTest

# Build a debug APK
./gradlew assembleDebug

# Instrumented tests (requires Android emulator / device)
./gradlew connectedDebugAndroidTest
```

JVM tests are the right loop for almost any agent-iteration change. Instrumented tests run in CI on an API-29 emulator.

## Where things live

| You're touching… | Look in… |
| --- | --- |
| Maze data / generation | `app/src/main/java/com/example/apktest/game/core/Maze*.kt` |
| Game loop / rules | `app/src/main/java/com/example/apktest/game/core/GameEngine.kt` |
| Player / NPC policies | `app/src/main/java/com/example/apktest/game/core/Policies.kt` |
| Power-ups | `app/src/main/java/com/example/apktest/game/core/PowerUps.kt` + `GameEngine` |
| Persistence / save & resume | `app/src/main/java/com/example/apktest/GameStateStore.kt` + `game/core/GameEngineSnapshot.kt` |
| libGDX rendering | `app/src/main/java/com/example/apktest/game/render/*.kt` |
| Android host / lifecycle / inputs | `app/src/main/java/com/example/apktest/MainActivity.kt`, `SetupActivity.kt`, `GameFragment.kt` |
| In-game UI overlays | `app/src/main/java/com/example/apktest/ui/*.kt` |
| Responsive sizing | `app/src/main/res/values/dimens.xml` + `values-sw400dp/dimens.xml` |
| JVM unit tests | `app/src/test/java/com/example/apktest/...` |
| Instrumented tests | `app/src/androidTest/java/com/example/apktest/...` |
| CI | `.github/workflows/build.yml` |

## Before you change code, read

1. [`docs/agent-quickstart.md`](docs/agent-quickstart.md) — first 5 minutes for an agent.
2. [`docs/architecture.md`](docs/architecture.md) — short map of process flow, snapshot lifecycle, policy hierarchy.
3. [`docs/conventions.md`](docs/conventions.md) — Kotlin / Android / libGDX conventions used here.
4. [`docs/testing.md`](docs/testing.md) — patterns for JVM and instrumented tests (and known emulator quirks).
5. [`docs/lessons-learned.md`](docs/lessons-learned.md) — **distilled review-feedback from PRs #1–#14, grouped by the area you're touching.** Highest-leverage doc in the repo.

## Hard rules (the ones agents keep breaking)

1. **If you add a field to `GameEngine` runtime state that affects gameplay, add it to `GameEngineSnapshot`** (constructor, `toJson`, `fromJson`, `GameEngine.snapshot()`, `GameEngine.restore()`) and bump `SCHEMA_VERSION`. The `GameEngineSnapshotSchemaCoverageTest` JVM test will fail loudly if you forget. (PR #14)
2. **If you add a new `PowerUpType` (or any other enum used in renderers/policies), every `when` over it must stay exhaustive.** Don't add an `else` branch to suppress the warning. (PR #4)
3. **Renderers must not allocate per frame.** Use `GameEngine.spawnedPowerUpsView` (live collection), pre-computed pattern/color arrays in `PowerUpIcons`, and the `Maze.revision` revision counter to invalidate cached geometry in `MazeRenderer`. (PR #4, PR #9)
4. **All RNG must be seeded from `currentSeed`.** Derive independent streams with `xor` of a constant (see `NPC_RANDOM_SEED_MIX` in `GameEngine`). No `Math.random()`, no unseeded `Random()`. (PR #9)
5. **Policy tie-breakers must be total orderings.** When ranking candidates (pickup detours, BFS fallbacks, etc.), follow: risk → path distance → enum ordinal → position → direction. (PR #12)
6. **Touch-event interception for the libGDX surface lives in `MainActivity.dispatchTouchEvent`, not on a child listener.** The libGDX `SurfaceView` consumes touches before any child-level listener fires. (PR #5)
7. **Instrumented test assertions about overlays use the `@VisibleForTesting` snapshot hooks** (`GameMenuPopover.snapshotForTests`, `MainActivity.openMenuForTesting`-style helpers). Do **not** use Espresso `isPlatformPopup()` / focus-root matchers — they are flaky on CI emulators. (PR #11, PR #12)
8. **Autosave threading: reuse the single executor on `MainActivity`.** Don't `Thread { … }.start()` per `onPause`. Guard async snapshot callbacks against `onDestroy` with try/catch around `RejectedExecutionException`. (PR #14)
9. **Schema validation in `GameEngineSnapshot.fromJson` returns `null` on mismatch/corruption** and the caller (`GameStateStore.load`) clears the saved blob. Never bypass `load()` by reading raw JSON to drive UI state. (PR #14)
10. **Every bug fix gets a JVM regression test** that fails on the broken code and passes on the fix. Test invariants, not statistical properties (no "different seeds produce different layouts"). (PR #3, multiple)

## How to update this guidance

When you encounter a *new* recurring class of mistake or establish a *new* convention, append an entry to [`docs/lessons-learned.md`](docs/lessons-learned.md) (see the "How to update this document" section at the bottom of that file). Keep `AGENTS.md` itself terse — it's an index, not a catalogue.
