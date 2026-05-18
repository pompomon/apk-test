# Testing patterns

The repository has two test source sets:

- **JVM unit tests** in `app/src/test/java/com/example/apktest/...` — run by `./gradlew testDebugUnitTest`. Fast, deterministic, the right loop for almost every agent change.
- **Instrumented (Android) tests** in `app/src/androidTest/java/com/example/apktest/...` — run on an emulator/device by `./gradlew connectedDebugAndroidTest`. CI uses `reactivecircus/android-emulator-runner` with API 29, x86_64, `Nexus 6` profile.

## Cardinal rule: every bug fix gets a regression unit test

Across PRs #3–#14, every accepted bug fix added a JVM test that fails on the broken code and passes on the fix. Existing examples:

| Bug class | Regression test |
| --- | --- |
| Maze layout non-deterministic | `MazeGeneratorTest.sameSeedProducesSameLayout` (PR #3) |
| Power-up despawn lifetime | `GameEngineTest.collectingSpeedUp_updatesHudSpeedAndExpires` (PR #4) |
| Freeze/invisibility immunity | `NpcFreezePickupTest` and `GameEngineTest.freeze_/invisibility_preventsLossOnNpcCollisionWhileActive` (PR #6) |
| Animation step wrap-around | `npcAnimationFrame_advancesOnMove` (PR #9) |
| Exit precedence over NPC | `PlayerAvoidanceTest.winningMove_takenEvenWhenExitOccupiedByNpc` (PR #10) |
| AvoidanceWrapper pass-through | `PlayerPickupSeekingTest.noNpcNoPickup_bfsNoPath_preservesInnerNull` etc. (PR #12) |
| Snapshot schema validation | `GameEngineSnapshotTest.fromJson_returnsNullForUnknownDifficultyName` etc. (PR #14) |
| `GameEngineSnapshot` field drift | `GameEngineSnapshotSchemaCoverageTest` (this is the CI enforcement test) |

When fixing a bug, add a test in the same style: name it after the *invariant* that was violated, not the implementation detail. Test files mirror the production package layout.

## Test invariants, not statistics

PR #3 explicitly removed a `differentSeedsProduceDifferentLayouts` test — it was flaky because two seeds *can* produce the same small maze by chance. The retained test, `sameSeedProducesSameLayout`, asserts a real invariant.

Good replacements for "different things look different":

- `generatedMaze_startIsAlwaysTopCornerAcrossSeeds` — asserts an invariant across **64 seeds** (PR #6).
- `oddDimensionsAreRoundedUpToEvenAndRemainFullyReachable` — asserts the documented generator rounding behaviour (PR #7).

## Useful JVM helpers and patterns

- **Seeded engine construction.** Pass `(DifficultyPresets.MEDIUM, seedLong)` to `GameEngine(…)` so the test reproduces a known maze.
- **Pin NPCs during navigation.** When a helper walks the player to a target cell, the NPC roster is cleared inside `try { … } finally { restore }` so collision rules don't sabotage the helper (PR #6).
- **Small timesteps.** When stepping the engine manually in a test, use small `dt` values tied to `1f / playerSpeed` so each `update(dt)` consumes at most one cell. Large timesteps accidentally trigger power-up despawns and other timed mechanics (PR #4).
- **Lift despawn intervals in tests** when the test path is long. The default 10s despawn was shorter than a 15.5s BFS path in PR #4; tests for despawn behaviour should set the relevant interval explicitly rather than rely on the default.
- **`org.json` on the JVM.** `app/build.gradle.kts` adds `testImplementation("org.json:json:20240303")` because `android.jar` ships the package as `Stub!`-throwing stubs. Don't remove it.

## Snapshot / schema CI enforcement

`GameEngineSnapshotSchemaCoverageTest` is a reflection-based JVM test that walks every property of `GameEngineSnapshot`'s primary constructor and verifies it round-trips through `toJson` / `fromJson`. The test enforces two invariants per field: (a) the witness value is **deliberately non-default** (non-null, non-zero, non-empty) so the round-trip assertion isn't vacuous, and (b) the value survives `toJson` → `fromJson` unchanged. If a field is added but missed in `toJson` / `fromJson`, or missed in `witnessSnapshot()`, the test fails with a clear message naming the property.

When you add a property to `GameEngineSnapshot`:

1. Add the property to `witnessSnapshot()` with a **deliberately non-default value** (non-null, non-zero, non-empty). Even if the property has a default, an unset witness will be flagged as "trivial" and the test will fail until you choose a meaningful witness value.
2. Bump `GameEngineSnapshot.SCHEMA_VERSION` and add the JSON key in `toJson` / `fromJson`.
3. Update `GameEngine.snapshot()` and `GameEngine.restore()` to feed/consume the new field.

## Instrumented test patterns (Android)

The CI emulator (`api-level: 29`, `arch: x86_64`) is the source of most historical flakes. Use these patterns instead of obvious-looking Espresso usage:

- **Menu popover assertions:** open the menu by `scenario.onActivity { buttonMenu.performClick() }` rather than Espresso `click()`, then inspect via `GameMenuPopover.textSnapshotForTesting()` (wrapped by `MainActivity.menuPopoverTextSnapshotForTesting()`) and `MainActivity.isMenuPopoverShowingForTesting()` `@VisibleForTesting` hooks. Do not rely on Espresso `isPlatformPopup()` / focus-root matchers — they race against the platform-popup focus transfer (PR #11, PR #12).
- **Synthetic swipes:** prefer the test-only `MainActivity.feedSwipeEventForTesting` hook over building `MotionEvent.obtain(...)` and dispatching through `Activity.dispatchTouchEvent` (synthetic events have `SOURCE_UNKNOWN` and are silently dropped by the InputDispatcher on the emulator). If you must use real input, `Instrumentation.sendPointerSync` with an explicit `SOURCE_TOUCHSCREEN` works. Wrap in a bounded retry loop (≤5 attempts) — one success is enough (PR #5, PR #7).
- **Layout readiness:** poll for layout (e.g. `waitForGameHostLaidOut(...)`) before injecting gestures, instead of `Thread.sleep`.
- **Fragment retrieval:** call `supportFragmentManager.executePendingTransactions()` before casting, to avoid a transient race after `setContentView`.
- **Observable counters:** prefer an integer counter exposed `@VisibleForTesting` (e.g. `resolvedSwipeCount`) over timing-dependent "did the engine advance?" assertions (PR #5).

## Running a focused subset locally

```bash
# Single test class
./gradlew :app:testDebugUnitTest --tests 'com.example.apktest.game.core.GameEngineSnapshotTest'

# Single test method
./gradlew :app:testDebugUnitTest --tests 'com.example.apktest.game.core.GameEngineSnapshotTest.roundTrip_yieldsEqualObservableState'
```

## CI

`.github/workflows/build.yml` runs three jobs on PRs to `main`:

1. `unit-test` — `./gradlew testDebugUnitTest`.
2. `instrumented-test` — emulator API 29, `./gradlew connectedDebugAndroidTest`.
3. `build` — `./gradlew assembleDebug` (depends on the other two).

When you change CI, remember `sdkmanager` is not on PATH; the workflow invokes it as `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager` (PR #1).
