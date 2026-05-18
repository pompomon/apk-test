# GitHub Copilot instructions for `apk-test`

> Terse, Copilot-specific entry point. The authoritative content lives in [`AGENTS.md`](../AGENTS.md) and the [`docs/`](../docs/) folder — please read those.

## Project

- Android 2D maze game; Kotlin + libGDX. JDK 17, compileSdk 34, minSdk 26.
- Pure-Kotlin game core under `app/src/main/java/com/example/apktest/game/core/` (JVM-testable).

## Commands

```bash
./gradlew testDebugUnitTest      # JVM unit tests (use this loop)
./gradlew assembleDebug          # build APK
./gradlew connectedDebugAndroidTest  # instrumented (needs emulator)
```

## Must-read before changing code

1. [`AGENTS.md`](../AGENTS.md) — entry point + hard rules.
2. [`docs/lessons-learned.md`](../docs/lessons-learned.md) — recurring mistakes from PRs #1–#14, grouped by area. **Read the section that matches the area you're touching.**
3. [`docs/conventions.md`](../docs/conventions.md), [`docs/testing.md`](../docs/testing.md), [`docs/architecture.md`](../docs/architecture.md) for details.

## Hard rules (mirrors `AGENTS.md`)

1. New gameplay state on `GameEngine` → add to `GameEngineSnapshot` + bump `SCHEMA_VERSION`. (PR #14)
2. New `PowerUpType` (or any enum used in renderers/policies) → every `when` must stay exhaustive; no `else` to silence. (PR #4)
3. Renderers don't allocate per frame. Use `spawnedPowerUpsView`, pre-computed `PowerUpIcons` maps, `Maze.revision` for cache keying. (PR #4, #9)
4. RNG seeded from `GameEngine.currentSeed`; derive independent streams via `xor` constants. (PR #9)
5. Policy tie-breakers are total orderings: risk → distance → ordinal → position → direction. (PR #12)
6. libGDX touch interception: `MainActivity.dispatchTouchEvent`, never a child listener. (PR #5)
7. Instrumented overlay assertions: use `@VisibleForTesting` snapshot hooks, not Espresso platform-popup matchers. (PR #11, #12)
8. Autosave executor on `MainActivity` is shared; guard async callbacks against `RejectedExecutionException`. (PR #14)
9. `GameEngineSnapshot.fromJson` returns `null` on mismatch — never bypass it. (PR #14)
10. Every bug fix gets a regression unit test. Test invariants, not statistical properties. (PR #3)

## PR descriptions

When opening a PR, reference any rule above the change touches (e.g. *"Bumps `SCHEMA_VERSION` to 3 and adds round-trip coverage — see hard rule #1"*). Use the [PR template](PULL_REQUEST_TEMPLATE.md).

## When you encounter a new recurring mistake

Append it to [`docs/lessons-learned.md`](../docs/lessons-learned.md) following the format described in that file's "How to update this document" section. Keep this file and `AGENTS.md` as indexes only.
