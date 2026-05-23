# Architecture map

A short map for agents. Pointers into the code, not duplication of it.

## Process / activity flow

```
SetupActivity  ── Intent extras ──▶  MainActivity  ── Fragment args ──▶  GameFragment
   (LAUNCHER)     EXTRA_PLAYER_POLICY                 ARG_PLAYER_POLICY     │
                  EXTRA_NPC_POLICY                    ARG_NPC_POLICY        │
                  EXTRA_DIFFICULTY                    ARG_DIFFICULTY        ▼
                                                                       MazeGame (libGDX ApplicationListener)
                                                                            │
                                                                            ▼
                                                                        GameEngine ◀──▶ GameEngineSnapshot
                                                                            │
                                                                            ▼
                                                                        MazeRenderer
```

- `SetupActivity` is the launcher (`AndroidManifest.xml`). It collects difficulty, player policy and NPC policy and passes them as Intent extras to `MainActivity`.
- `MainActivity` hosts a single `GameFragment` and the Android-side UI (D-pad, HUD overlay, hamburger menu via `GameMenuPopover`).
- `GameFragment` constructs `MazeGame`, which owns the `GameEngine` and the `MazeRenderer`.
- All actual gameplay rules live in `GameEngine` (pure Kotlin, no Android imports). It is fully unit-testable on the JVM.

**Rule:** any new "setup" option must be plumbed through *both* `SetupActivity` Intent extras *and* `GameFragment.ARG_*` arguments — see PR #2 / PR #7.

## Game tick / end-condition flow

`GameEngine.update(dt)`:

1. Drain `manualQueue` (if `manualOverrideRemainingSeconds > 0` or policy is `MANUAL`).
2. Advance player by one step if their accumulator says so → `evaluateEndConditions()` (WIN if on exit; LOSE if sharing a cell with an NPC and no FREEZE/INVISIBILITY).
3. Advance each NPC by one step if its accumulator says so → `evaluateEndConditions()` again.
4. Tick power-up spawning, despawn timers, and active-effect timers.

**Rule:** end conditions are evaluated *after each movement step*, not once per `update()`. See `GameEngine.update` / `evaluateEndConditions`.

## Maze model

- `MazeGenerator.generate(w, h, seed)` produces a deterministic, perfect-then-widened maze.
  - Width and height are **rounded up to the next even number**.
  - Generator produces a half-resolution maze, then expands each cell into a 2×2 open block — corridors are always 2 cells wide.
  - `maze.start` is randomly chosen between the two top corners using the seeded `Random`.
  - The exit is placed *before* the 2×2 expansion to guarantee a clean position (PR #3).
- `Maze` exposes a monotonic `revision` counter that is incremented by `removeWall` whenever a wall actually changes (e.g. via BLAST power-up).
- `MazeRenderer` caches wall brick geometry and invalidates it when `Maze` identity *or* `Maze.revision` changes.

## Save / resume lifecycle (snapshot)

- `GameStateStore` persists a JSON-serialized `GameEngineSnapshot` in `SharedPreferences`. Validated load only — never read raw JSON to drive UI.
- `MainActivity.onPause` writes a snapshot via a **shared single-thread `ExecutorService`**. "Pause & Exit" *clears* the saved state when status is `WIN` or `LOSE` instead of saving.
- `GameEngineSnapshot.fromJson` returns `null` on:
  - schema-version mismatch (`SCHEMA_VERSION` is currently `3`),
  - unknown `difficultyName` (does **not** silently fall back to MEDIUM the way `DifficultyPresets.byName` does),
  - any persisted coordinate (player, NPCs, spawned power-ups, or `removedWalls` cell) falling outside the maze bounds implied by the preset (rounded up to even, like the generator),
  - JSON / enum-value parse errors.
- The snapshot persists `removedWalls` — walls destroyed during gameplay — so restore re-applies them on the regenerated baseline maze.
- The snapshot also persists Adventure-mode overrides — `npcCountOverride` (replaces the preset's `npcCount`) and `npcPolicies` (per-NPC policy by spawn id) — so a paused-mid-maze resume re-spawns the same set of NPCs with the same per-NPC strategies.

## Adventure mode

Adventure mode is a thin "run controller" layered above the single-maze engine. The engine handles one maze at a time; the controller chains mazes and tracks run-level state (lives, win streak, unlocks).

```
SetupActivity ──▶ AdventureSetupActivity ──▶ AdventureActivity ──▶ GameFragment ──▶ MazeGame ──▶ GameEngine
                                                  │
                                                  ▼
                                       AdventureRunController ◀──▶ AdventureRunStateSnapshot
                                                  │
                                                  ▼
                                       AdventureStateStore (separate SharedPreferences file)
```

- **`AdventureConfig`** (`game/core/AdventureConfig.kt`): per-difficulty rules — Easy 5 lives / 5 mazes / base 1 NPC, Medium 3 / 7 / base 1, Hard 1 / 9 / base 2. `npcCountForMaze(mazeIndex1Based)` = `baseNpcsPerMaze + ((mazeIndex1Based - 1) / 3)` plus +1 on the final maze. Unknown presets fall back to Medium rules.
- **`AdventureRunController`** (`game/core/AdventureRunController.kt`): pure-Kotlin state transitions (no Android imports → fully JVM-testable). Locks `currentMazeSeed` + `currentMazeNpcPolicies` on first `prepareCurrentMaze()` per maze so a death replay returns the *same* spec; clears them on win; awards +1 life every 3 consecutive wins (resets streak on death OR on bonus); manages the unlocked-policies pool starting with only `MANUAL`.
- **`AdventureRunStateSnapshot`** (`game/core/AdventureRunStateSnapshot.kt`): JSON, schema-versioned, validates the MANUAL-always-unlocked invariant. Embeds a `GameEngineSnapshot` for paused-mid-maze resume.
- **`AdventureStateStore`** (`AdventureStateStore.kt`): sibling of `GameStateStore` but in its own SharedPreferences file (`adventure_state`) so a saved adventure never appears as a single-maze Resume on the main start menu, and vice versa.
- **`AdventureActivity`** mirrors `MainActivity`'s lifecycle / popover / swipe handling, polls engine status every 200ms to detect `WIN`/`LOSE`, runs the win/lose/unlock-chooser overlays, and persists via a per-activity single-thread autosave executor (guarded against `RejectedExecutionException` per Hard rule #8).
- **`GameEngine.configureAdventureMaze(npcCount, policies)`** sets `npcCountOverride` + `npcPolicies`. Per-NPC `policyType` is resolved through a per-type policy cache with deterministic seeded RNG (`NPC_POLICY_TYPE_SEED_STRIDE`) — single-maze runs still go through the long-lived `npcPolicy` instance so behaviour is byte-for-byte unchanged.

**Hard rule (Adventure):** every per-maze NPC policy assignment is locked into `AdventureRunState.currentMazeNpcPolicies` on first entry so death replays use the same set; reloading an in-progress run preserves the locked list verbatim regardless of any future change to the derivation function.

## Player policy hierarchy

The active player policy is selected on `MainActivity` start. `AvoidanceWrapperPolicy` wraps automated policies (BFS, A\*, wall-follower, etc.) and adds:

1. If a pickup is within Chebyshev radius `pickupRadius` *and* the pickup cell is not deadly or risky, divert one step toward it.
2. Otherwise run the inner policy's `nextMove`. If the inner result is safe (no NPC threat, or INVISIBILITY active), **return it unchanged** — do not substitute a ranked fallback.
3. If the inner result is unsafe, fall back to the avoidance-ranking BFS.

Tie-breaker order across all ranking sites: **risk → path distance → `PowerUpType.ordinal` → pickup position → direction**.

## NPC behaviour

- NPC policies live in `Policies.kt` alongside player policies.
- NPC wander RNG uses an independent stream `npcRandom = Random(seed xor NPC_RANDOM_SEED_MIX)` so it stays reproducible even if power-up spawning consumes more or fewer numbers from the main `random`.

## Rendering

- libGDX `ExtendViewport`. `MazeRenderer` centers the maze both horizontally (`mazeOriginX`) and vertically (`mazeOriginY`) inside the viewport.
- Wall textures are addressed by named constants `WallTextures.DIR_NORTH / WEST / SOUTH / EAST` — never by `0/1/2/3`.
- Power-up patterns/colors are pre-computed once per `PowerUpType` via an exhaustive `when` builder in `PowerUpIcons`, then cached. Renderers index by ordinal — no map lookup, no allocation per frame.
- NPC step animation has `ANIMATION_FRAMES = 2` (idle + step) so the modulo arithmetic does not wrap mid-stride.

## Android host & input

- D-pad sizing uses `maze_dpad_*` dimens with `values-sw400dp` overrides for narrow vs. wider phones.
- Swipe gestures are detected in `MainActivity.dispatchTouchEvent` (parent dispatch) — the libGDX `SurfaceView` would otherwise consume them. The dispatcher hit-tests then **never consumes** the event (returns `super.dispatchTouchEvent(ev)`).
- The hamburger menu is `GameMenuPopover`, exposing `textSnapshotForTesting()` (`@VisibleForTesting`) — wrapped by `MainActivity.menuPopoverTextSnapshotForTesting()` and `MainActivity.isMenuPopoverShowingForTesting()` — so instrumented tests can inspect contents without depending on Espresso platform-popup focus.
