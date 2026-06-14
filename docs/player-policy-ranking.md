# Automated player policy ranking plan

This document describes the automated player policies currently available in the game, the JVM development harness used to rank them, and the checked-in Adventure award order derived from that ranking.

## Scope and assumptions

- Rank only automated player policies returned by `automatedPlayerPolicies()`, which excludes `MANUAL`.
- Use `elapsedSeconds` from `GameEngine` as the primary "time to exit" metric because it already reflects movement speed, power-up effects, freeze effects, and NPC timing.
- Treat a run as successful only when `engine.status == GameStatus.WIN`.
- Treat `GameStatus.LOSE` and timeout runs as incomplete attempts. The default ranking is survival-first, so policies with more successful exits rank above faster policies that lose or time out more often.
- Keep the benchmark deterministic: every policy must be evaluated against the same difficulty, seed set, NPC policy set, and starting power-up rules.
- Do not use live Android UI timing for ranking. The ranking harness should live in pure game-core/JVM code so it can run in unit tests and CI.

## Checked-in Adventure award order

Adventure mode no longer samples policy unlock candidates randomly. Odd-numbered maze wins offer the first three still-locked policies from this checked-in order:

1. `BFS_EXIT` — BFS Exit
2. `ASTAR_EXIT` — A* Exit
3. `PLEDGE` — Pledge
4. `FLEE_TO_EXIT` — Flee + Drift to Exit
5. `WALL_LEFT` — Wall Left

The order is static runtime data in `adventureAwardPlayerPolicyRanking()` so unlock dialogs are deterministic and cheap. It is produced during development by running the JVM-only `PlayerPolicyRankingHarness` in the test source set, then checking in the resulting order. Runtime gameplay does not execute the benchmark harness.

## Current automated player policies

### Wall Left (`PlayerPolicyType.WALL_LEFT`)

`WALL_LEFT` wraps `WallFollowerPolicy(leftHand = true)` in `AvoidanceWrapperPolicy`.

The inner wall follower is a local maze-solving strategy. On each move it checks directions relative to the player's current facing in this order: left, straight, right, then back. It chooses the first walkable direction. This makes it simple and cheap, but it can take long detours in mazes where hugging the left wall is not close to the shortest path.

The avoidance wrapper adds shared automated-player behavior around the wall follower, in priority order:

- Prefer a one-step winning move onto the exit.
- Divert toward nearby power-ups within the difficulty's `automaticPickupRadius`, preferring non-risky detours but allowing a risky detour only when no non-risky, non-deadly regular move exists.
- When INVISIBILITY is active, NPC danger classification is skipped entirely and the wrapper reduces to pickup-seeking followed by a pass-through to the inner policy (NPCs cannot harm the player so no deadly or risky filtering applies).
- If no immediate winning exit move is available and INVISIBILITY is not active, reject cells currently occupied by NPCs as deadly.
- When FREEZE is active, only currently NPC-occupied cells are treated as deadly; risky-neighbor filtering is disabled because frozen NPCs cannot move into adjacent cells.
- Among the remaining moves (when neither INVISIBILITY nor FREEZE applies), prefer non-risky cells over cells adjacent to NPC movement options.
- Return `null` rather than stepping into a guaranteed NPC collision.

Expected ranking profile: usually robust in simple connected mazes but often slower than shortest-path policies because it does not plan globally.

### BFS Exit (`PlayerPolicyType.BFS_EXIT`)

`BFS_EXIT` wraps `BfsExitPolicy` in `AvoidanceWrapperPolicy`.

The inner BFS policy asks `context.navigator.bfsPath(context.player.position, context.exit)` for a shortest path in number of maze steps and moves one cell along that path. In an unweighted maze, BFS is optimal by step count.

The wrapper preserves the BFS result when no NPC or pickup special handling is needed. When NPCs create danger, the wrapper can request an avoidance-aware BFS path that blocks currently occupied NPC cells, then falls back to ranked moves if needed.

Expected ranking profile: should be one of the fastest policies by time to exit on static mazes because it follows a shortest step-count route. With NPCs and power-ups enabled, detours can make it differ from raw shortest path time.

### A* Exit (`PlayerPolicyType.ASTAR_EXIT`)

`ASTAR_EXIT` wraps `AStarExitPolicy` in `AvoidanceWrapperPolicy`.

The inner A* policy asks `context.navigator.aStarPath(context.player.position, context.exit)` and moves one cell along the returned path. The A* implementation uses Manhattan distance as its heuristic and unit edge costs, so it should find shortest paths like BFS while usually exploring fewer nodes.

The wrapper behavior is shared with BFS: winning moves take precedence, pickup detours can pre-empt the path, and NPC danger can trigger an avoidance-aware A* path or ranked fallback move.

Expected ranking profile: should usually tie BFS on path length and elapsed time because both return shortest paths in the same unit-cost maze. Differences, if any, are likely from NPC or power-up interactions after the first move rather than path search order; `aStarPath` uses a `PriorityQueue` keyed by total cost with no explicit secondary tie-breaker, so equal-priority expansion order is an implementation detail and should not be relied upon for deterministic ranking differences.

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

This is a complete tie-breaking cascade: compare each metric in order and only fall through to the next metric when the previous values tie. Use `PlayerPolicyType.ordinal` only after all numeric metrics tie. Policies with zero successful runs have undefined successful-time statistics; keep their median, mean, p90, and median-step values as `null` in the aggregate model and place them in a failed tier below every policy with at least one success. Inside that failed tier, sort by fewer losses, then fewer timeouts, then `PlayerPolicyType.ordinal`. This sort keeps "fast but frequently loses" policies below consistently successful policies. Adventure awards use this survival-first ordering. "Fastest successful wins" is interpreted within a success-rate tier: policies with better success rate rank first, and faster successful elapsed time breaks ties among policies with the same success rate.

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

## Simulation loop

The JVM-only development harness lives in `app/src/test/java/com/example/apktest/game/core/PlayerPolicyRankingHarness.kt`.

1. Define immutable configuration for the benchmark:
   - difficulties
   - NPC policies
   - seeds
   - maximum simulated seconds
   - timestep
   - optional starting power-up
2. For each `(difficulty, seed, npcPolicy, playerPolicy)`:
   - Create a fresh `GameEngine(difficulty, seed)`.
   - Set the NPC policy.
   - Set the player policy.
   - Apply the optional starting power-up.
   - Do not call `GameEngine.startCountdown()` — fresh construction, restart, and restore all leave `countdownRemainingSeconds` at `0f` automatically.
   - Step `update(dt)` until `GameStatus.WIN`, `GameStatus.LOSE`, or timeout.
   - Record status, elapsed seconds, and steps.
3. Choose a timestep that cannot skip over movement cadence unexpectedly:
   - `playerMovesPerSecond` and `npcMovesPerSecond` are base frequencies; the effective rates can be higher when a starting power-up is active (e.g., `SPEED_UP` multiplies player speed by `2f`).
   - Compute the effective player speed for this scenario: `val effectivePlayerSpeed = playerMovesPerSecond * (if (startingPowerUp == PowerUpType.SPEED_UP) 2f else 1f)`.
   - A safe default is `val dt = 1f / (maxOf(effectivePlayerSpeed, npcMovesPerSecond) * 4f)`.
   - The 4x oversampling keeps each simulation update at no more than one quarter of the fastest actor's effective movement interval, preventing coarse-grained power-up expiry or timing artifacts from skewing `elapsedSeconds` comparisons.
4. Aggregate all run results by policy.
5. Sort policies using the complete ranking metric cascade above, including the failed tier for policies with zero successful exits.
6. Copy the resulting order into `adventureAwardPlayerPolicyRanking()` after reviewing the harness output.

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

## Resolved decisions

- Ranking prioritizes survival first, then successful time.
- The first checked-in Adventure order is static and benchmark-produced during development.
- Runtime Adventure awards filter the checked-in order by locked policies; they do not run the benchmark and do not shuffle by `runSeed`.
- The harness default matrix includes Easy, Medium, Hard; all NPC policies; and a small curated seed set. Expand the seed set before using the harness output for larger balancing passes.
