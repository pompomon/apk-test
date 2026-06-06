# Automated player policy ranking plan

This document describes the automated player policies currently available in the game and proposes a detailed implementation plan for ranking them by time to exit.

## Scope and assumptions

- Rank only automated player policies returned by `automatedPlayerPolicies()`, which excludes `MANUAL`.
- Use `elapsedSeconds` from `GameEngine` as the primary "time to exit" metric because it already reflects movement speed, power-up effects, freeze effects, and NPC timing.
- Treat a run as successful only when `GameEngine.status == WIN`.
- Treat `LOSE` and timeout runs as incomplete attempts. The default ranking is survival-first, so policies with more successful exits rank above faster policies that lose or time out more often.
- Keep the benchmark deterministic: every policy must be evaluated against the same difficulty, seed set, NPC policy set, and starting power-up rules.
- Do not use live Android UI timing for ranking. The ranking harness should live in pure game-core/JVM code so it can run in unit tests and CI.

## Current automated player policies

### Wall Left (`PlayerPolicyType.WALL_LEFT`)

`WALL_LEFT` wraps `WallFollowerPolicy(leftHand = true)` in `AvoidanceWrapperPolicy`.

The inner wall follower is a local maze-solving strategy. On each move it checks directions relative to the player's current facing in this order: left, straight, right, then back. It chooses the first walkable direction. This makes it simple and cheap, but it can take long detours in mazes where hugging the left wall is not close to the shortest path.

The avoidance wrapper adds shared automated-player behavior around the wall follower:

- Prefer a one-step winning move onto the exit.
- Divert toward nearby safe power-ups within the difficulty's `automaticPickupRadius`.
- Avoid cells currently occupied by NPCs.
- Prefer non-risky cells over cells adjacent to NPC movement options.
- Return `null` rather than stepping into a guaranteed NPC collision.

Expected ranking profile: usually robust in simple connected mazes but often slower than shortest-path policies because it does not plan globally.

### BFS Exit (`PlayerPolicyType.BFS_EXIT`)

`BFS_EXIT` wraps `BfsExitPolicy` in `AvoidanceWrapperPolicy`.

The inner BFS policy asks `MazeNavigator.bfsPath(player.position, exit)` for a shortest path in number of maze steps and moves one cell along that path. In an unweighted maze, BFS is optimal by step count.

The wrapper preserves the BFS result when no NPC or pickup special handling is needed. When NPCs create danger, the wrapper can request an avoidance-aware BFS path that blocks currently occupied NPC cells, then falls back to ranked safe moves if needed.

Expected ranking profile: should be one of the fastest policies by time to exit on static mazes because it follows a shortest step-count route. With NPCs and power-ups enabled, detours can make it differ from raw shortest path time.

### A* Exit (`PlayerPolicyType.ASTAR_EXIT`)

`ASTAR_EXIT` wraps `AStarExitPolicy` in `AvoidanceWrapperPolicy`.

The inner A* policy asks `MazeNavigator.aStarPath(player.position, exit)` and moves one cell along the returned path. The A* implementation uses Manhattan distance as its heuristic and unit edge costs, so it should find shortest paths like BFS while usually exploring fewer nodes.

The wrapper behavior is shared with BFS: winning moves take precedence, safe pickup detours can pre-empt the path, and NPC danger can trigger an avoidance-aware A* path or safe fallback move.

Expected ranking profile: should usually tie BFS on path length and elapsed time because both return shortest paths in the same unit-cost maze. Differences, if any, should come from deterministic tie-breaking in path search order or NPC/power-up interactions after the first move.

### Pledge (`PlayerPolicyType.PLEDGE`)

`PLEDGE` wraps `PledgePolicy` in `AvoidanceWrapperPolicy`.

The inner Pledge policy chooses a reference direction that best aligns the player-to-exit vector, preferring the dominant axis and using north/south on ties. It walks straight in that reference direction when possible. When blocked, it switches to left-hand wall following and tracks net rotation. Once the net rotation unwinds to zero and the reference direction is walkable again, it resumes straight movement.

The policy is stateful. Its `reset()` clears the reference direction, rotation count, following mode, and cached follow-facing. For benchmark consistency, prefer constructing a fresh `GameEngine` and policy instance per scenario instead of relying on reset calls between scenarios; fresh construction eliminates state-leak risk and matches how each policy would start in a new run.

Expected ranking profile: often faster than plain wall following when the exit lies broadly in the reference direction, but slower than BFS/A* because it does not compute a global shortest path.

### Flee + Drift to Exit (`PlayerPolicyType.FLEE_TO_EXIT`)

`FLEE_TO_EXIT` wraps `FleeToExitPolicy` in `AvoidanceWrapperPolicy`.

The inner policy ranks every walkable direction by:

1. Maximizing Chebyshev distance from the nearest NPC after the step.
2. Minimizing BFS distance to the exit.
3. Breaking ties by `Direction.ordinal`.

It caches a BFS distance field from the exit and invalidates the cache when the maze instance, maze revision, or exit changes. This makes repeated ranking cheap while still reacting to wall changes such as BLAST power-up effects.

The wrapper still owns hard safety rules. It blocks guaranteed NPC collisions, prefers non-risky moves, handles pickup detours, and prioritizes immediate exit wins.

Expected ranking profile: may be slower than BFS/A* in low-risk mazes because it optimizes survival distance first. It may outperform shortest-path policies in dangerous scenarios if avoiding NPC pressure prevents losses.

## Ranking metric

Use a two-level result model:

1. **Per run result**
   - `policyType`
   - `difficultyName`
   - `npcPolicyType`
   - `seed`
   - `status`
   - `elapsedSeconds`
   - `steps`
   - timeout flag
2. **Aggregate policy result**
   - successful run count
   - loss count
   - timeout count
   - success rate
   - median successful elapsed seconds
   - mean successful elapsed seconds
   - p90 successful elapsed seconds
   - median successful step count

Primary sort order:

1. Higher success rate.
2. Lower median successful elapsed seconds.
3. Lower p90 successful elapsed seconds.
4. Lower median successful step count.
5. Stable tie-break by `PlayerPolicyType.ordinal`.

This is a complete tie-breaking cascade: compare each metric in order and only fall through to the next metric when the previous values tie. Use `PlayerPolicyType.ordinal` only after all numeric metrics tie. Policies with zero successful runs have undefined successful-time statistics; keep their median, mean, p90, and median-step values as `null` in the aggregate model and place them in a failed tier below every policy with at least one success. Inside that failed tier, sort by fewer losses, then fewer timeouts, then `PlayerPolicyType.ordinal`. This sort keeps "fast but frequently loses" policies below consistently successful policies. If the product goal is pure speed regardless of loss rate, make the sort configurable but keep this survival-first ordering as the default.

## Benchmark scenario matrix

Start with a small deterministic matrix that is cheap enough for JVM tests:

- Difficulties: Easy, Medium, Hard.
- Player policies: all policies from `automatedPlayerPolicies()` in `AutomatedPlayerPolicies.kt`.
- NPC policies: Direct Chase, Predictive, Patrol/Guard.
- Seeds: a fixed curated list, for example 50-100 seeds that exercise different maze layouts.
- Starting power-up: none by default. If ranking must account for optional run-start power-up effects, add one separate full benchmark matrix per `PowerUpType` so each starting boost is measured independently.

Keep every policy evaluation on a given scenario identical except for the player policy:

- Same `DifficultyPreset`.
- Same seed.
- Same NPC policy.
- Same power-up configuration.
- Same timeout.
- Same update timestep.

## Simulation loop plan

1. Add a pure game-core ranking API, for example under `app/src/main/java/com/example/apktest/game/core/`.
2. Define immutable configuration for the benchmark:
   - difficulties
   - NPC policies
   - seeds
   - maximum simulated seconds
   - timestep
   - optional starting power-up
3. For each `(difficulty, seed, npcPolicy, playerPolicy)`:
   - Create a fresh `GameEngine(difficulty, seed)`.
   - Set the NPC policy.
   - Set the player policy.
   - Apply the optional starting power-up.
   - Ensure countdown is not armed.
   - Step `update(dt)` until `WIN`, `LOSE`, or timeout.
   - Record status, elapsed seconds, and steps.
4. Choose a timestep that cannot skip over movement cadence unexpectedly:
   - `playerMovesPerSecond` and `npcMovesPerSecond` are frequencies, so convert the fastest frequency into a small substep period.
   - A safe default is `1f / (max(playerMovesPerSecond, npcMovesPerSecond) * 4f)`.
   - The 4x oversampling keeps each simulation update at no more than one quarter of the fastest actor's movement interval, reducing coarse-grained timeout and power-up lifecycle artifacts while still letting `GameEngine.update()` process its normal movement accumulators.
5. Aggregate all run results by policy.
6. Sort policies using the complete ranking metric cascade above, including the failed tier for policies with zero successful exits.
7. Expose the result as a pure Kotlin value object that tests, debug UI, or future tooling can render.

## Validation plan

Add focused JVM unit tests rather than statistical/flaky assertions:

- `automatedPlayerPolicies_excludesManual` if not already covered.
- Every policy returned by `automatedPlayerPolicies()` is included in the benchmark exactly once per scenario.
- Ranking uses the same scenario count for every automated policy.
- A simple open-grid/no-NPC fixture ranks BFS and A* as successful and tied on elapsed time or steps.
- Losses and timeouts sort after successful policies when success rates differ.
- Aggregate ordering is deterministic when metrics tie.
- Stateful policies use fresh construction per scenario, not reset calls between benchmark runs.

Run:

```bash
./gradlew testDebugUnitTest
```

Build if production code is added:

```bash
./gradlew assembleDebug
```

## Reporting plan

The first implementation can expose rankings through tests or a debug-only function. If the app needs player-facing rankings later, add UI only after the core ranking output is stable:

1. Convert aggregate results into a small display model.
2. Add a developer/debug screen or menu entry.
3. Include each policy's label, rank, success rate, median time, and median steps.
4. Keep detailed benchmark seed data out of the normal UI unless needed for diagnostics.

## Open questions

- Should ranking prioritize success rate before time, or should it be pure fastest successful time?
- Should rankings include active NPCs and power-ups, or should the first version measure only static-maze time to exit?
- Should rankings be computed offline and checked in, or computed at runtime/debug time from the current code?
- How many seeds should be considered representative for each difficulty?
