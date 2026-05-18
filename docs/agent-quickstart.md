# Agent quickstart

> First 5 minutes for any AI coding agent landing in this repository.

## 1. Read in order

1. [`AGENTS.md`](../AGENTS.md) — project shape + hard rules.
2. The section of [`docs/lessons-learned.md`](lessons-learned.md) that matches the area you are about to change. (E.g. touching `GameEngine`? → §1 Persistence and §3 Cache invalidation.)
3. [`docs/architecture.md`](architecture.md) for the map.
4. [`docs/conventions.md`](conventions.md) and [`docs/testing.md`](testing.md) for style and test patterns.

## 2. Confirm green baseline

```bash
./gradlew testDebugUnitTest
```

This must pass before you start. If it doesn't, that's the first thing to fix or report — don't layer your changes on a broken baseline.

## 3. Identify the rules your change touches

Skim [`lessons-learned.md`](lessons-learned.md) and list the rule numbers you'll need to comply with. Common cases:

| If you're changing… | At minimum, comply with… |
| --- | --- |
| Adding a field to `GameEngine` that affects gameplay | §1.1, §1.2 (snapshot + schema version) |
| Adding a `PowerUpType` variant | §4.1 (exhaustive `when`), §11.2 (metadata) |
| Touching `MazeRenderer` | §3.1, §3.3, §3.4 (cache + no allocation) |
| Touching `MainActivity` lifecycle | §2 (autosave executor) |
| Touching `Policies.kt` | §6 (avoidance wrapper semantics, tie-breakers) |
| Adding a setup option | §13 (Intent extras + Fragment args) |
| Writing an instrumented test | §8 (popover snapshot hooks, no Espresso popup matchers) |

## 4. Write the regression test first

If you're fixing a bug, write the failing JVM unit test before the fix (§testing-cardinal-rule). Otherwise add a unit test alongside the change that asserts the new invariant.

## 5. Run the targeted test, then the whole suite

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.apktest.your.PackageTest'
./gradlew testDebugUnitTest
```

Instrumented tests are not normally part of the agent loop unless you change Android UI code.

## 6. Open the PR

Use the [PR template](../.github/PULL_REQUEST_TEMPLATE.md). Reference each `lessons-learned.md` rule your change touches. If your change establishes a *new* rule, append it to `lessons-learned.md` in the same PR (see that doc's "How to update this document" section).
