# Conventions

Distilled from PR #1–#14 review feedback and the existing codebase. When in doubt, follow what neighbouring files already do.

## Kotlin style

- **No magic numbers in renderers.** Use named constants such as `WallTextures.DIR_NORTH` / `DIR_WEST` / `DIR_SOUTH` / `DIR_EAST` instead of `0/1/2/3` (PR #8). Wall directions, animation frame counts (`ANIMATION_FRAMES = 2` in `MazeRenderer`), and seed-mixing salts (`NPC_RANDOM_SEED_MIX` in `GameEngine`) all have dedicated `private const val` declarations.
- **Exhaustive `when` over enums** is the norm for `PowerUpType`, `Direction`, `GameStatus`, `PlayerPolicyType`, `NpcPolicyType`. Don't add an `else ->` branch to suppress the warning — the warning is the point: it forces you to handle the new variant when an enum is extended (PR #4).
- **Prefer pre-computed lookup tables to per-call branches in hot paths.** `PowerUpIcons` builds `patternByType` / `gdxColorByType` / `androidColorByType` once per `PowerUpType.entries` using exhaustive `when` builders, then renderers index into them by ordinal. This combines compile-time exhaustiveness with allocation-free render-time access.
- **Catch `Exception`, not `Throwable`.** `GameEngineSnapshot.fromJson` catches `Exception` only so JVM `Error`s (`OutOfMemoryError`, `StackOverflowError`) remain diagnosable.
- **Comment the *why*, not the *what*.** Existing files (`GameEngineSnapshot.kt`, `MainActivity.kt`, `Policies.kt`) carry long doc-comments explaining the rationale for non-obvious decisions — match that level of detail when you make a similar non-obvious choice.

## Determinism & RNG

- All RNG must derive from `GameEngine.currentSeed` so a seed reproduces a run.
- When you need an independent stream (so unrelated systems don't perturb each other), `xor` the seed with a constant: e.g. `Random(seed xor NPC_RANDOM_SEED_MIX)` for NPC wander decisions (PR #9). Add a new constant with a clear name when introducing a new stream.
- **Never use `Math.random()` or unseeded `Random()` in game-core code.** Tests rely on reproducibility.

## Policy & pathfinding

- Wrappers around inner policies should be **pass-through preserving** when no special handling is needed. `AvoidanceWrapperPolicy` returns the inner `nextMove` unchanged when there is no NPC threat and no nearby pickup — it does not substitute a ranked fallback (PR #12).
- Pre-filter targets before ranking. `AvoidanceWrapperPolicy` ignores pickup-detour targets if the pickup cell is itself deadly or risky, *before* ranking paths (PR #12).
- **All tie-breakers across the codebase must be total orderings**, in this order: risk → path distance → enum ordinal → grid position → direction. This applies to pickup-detour candidates, BFS fallback moves, anywhere paths or cells are compared. Two different runs of the same seed must produce identical decisions.

## Persistence

- New observable state on `GameEngine` that affects gameplay must round-trip through `GameEngineSnapshot`. Update: the data class constructor, `toJson`, `fromJson`, `GameEngine.snapshot()`, `GameEngine.restore()`, and bump `SCHEMA_VERSION` so older saved blobs are rejected cleanly (PR #14).
- `GameEngineSnapshot.fromJson` returns `null` on any version / parse / bounds problem. `GameStateStore.load()` then clears the saved blob. **Do not bypass `load()` by reading raw JSON** to drive UI state like "Resume enabled?" — that was the original cause of a stale-blob bug (PR #14).
- `DifficultyPresets.byName(name)` silently falls back to `MEDIUM` for unknown names. The snapshot path explicitly does *not* use it (`resolvePreset()` returns `null` for unknown names), to avoid restoring into a differently-sized maze.

## Rendering / allocation discipline

- **Zero allocations per frame.** Use `GameEngine.spawnedPowerUpsView: Collection<SpawnedPowerUp>` (the live `values` view) instead of `spawnedPowerUps` (which sorts on each call). The sorted variant exists for tests only.
- Cache wall-brick geometry in `MazeRenderer`. Invalidate the cache on either `Maze` identity change or `Maze.revision` change — both are needed because `removeWall` mutates the same `Maze` instance (PR #9).

## Android host

- **Touch interception for the libGDX surface lives in `MainActivity.dispatchTouchEvent`.** The libGDX `SurfaceView` swallows events before any child-level listener can fire (PR #5). The dispatcher only **hit-tests** — it must return `super.dispatchTouchEvent(ev)` and never consume the event itself, so child views keep receiving input.
- **Autosave on `MainActivity` uses a single shared `ExecutorService`.** Don't `Thread { … }.start()` per `onPause`. Guard the `executor.execute { … }` block against `RejectedExecutionException` so callbacks queued near `onDestroy` don't crash the process (PR #14).
- **"Pause & Exit" *clears* the saved state when `status` is `WIN` or `LOSE`** instead of saving (PR #14). Match that pattern for any new terminal status.
- **Forward setup options through *both* Intent extras and Fragment arguments.** `SetupActivity` → `MainActivity` uses `EXTRA_PLAYER_POLICY` / `EXTRA_NPC_POLICY` / `EXTRA_DIFFICULTY`, then `MainActivity` → `GameFragment` uses `ARG_PLAYER_POLICY` / `ARG_NPC_POLICY` / `ARG_DIFFICULTY`. New setup options must follow both hops.

## Responsive UI

- All dynamic sizes (D-pad buttons, paddings) live in `res/values/dimens.xml` with overrides in `res/values-sw400dp/dimens.xml`. Don't hard-code `dp` in layouts (PR #6, PR #11).

## Testability / `@VisibleForTesting`

- Use `@VisibleForTesting(otherwise = VisibleForTesting.NONE)` to expose state snapshots for instrumented tests when the production-public surface would be polluted by them. Examples: `GameMenuPopover.textSnapshotForTesting()` (wrapped by `MainActivity.menuPopoverTextSnapshotForTesting()` and `MainActivity.isMenuPopoverShowingForTesting()`), swipe-feed hooks on `MainActivity` (PR #5, PR #11, PR #12).
- Prefer exposing a content / state snapshot over a "did this UI focus correctly?" assertion — Espresso focus assertions are flaky on CI emulators.

## Test dependencies

- JVM tests pull in `org.json:json:20240303` as `testImplementation` because `android.jar` ships `org.json` as `Stub!`-throwing stubs. Don't switch JSON libraries for the engine without updating this (or the snapshot tests will start failing locally as well). See `app/build.gradle.kts:84–90`.
