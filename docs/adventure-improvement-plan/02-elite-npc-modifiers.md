# 02 — Elite NPC Modifiers

## Problem statement

Adventure difficulty currently increases mostly by adding NPCs. More enemies can raise pressure, but it can also make later mazes feel crowded and repetitive. Elite NPC Modifiers add small, readable behavior twists to selected NPCs so difficulty comes from novelty and tactical adaptation instead of pure count scaling.

## Design goals

- Novelty: each elite should change how the player reads the maze.
- Readability: the player must identify the modifier through color, icon, legend text, or a pre-maze summary.
- Fairness: modifiers cannot create unavoidable losses from spawn or invalidate deterministic death replays.
- Low implementation risk: start with hooks around existing `NpcPolicyType` behavior and movement cadence before introducing complex new AI.

## Elite modifier catalog

| Modifier | Mechanics | Counterplay | Notes |
| --- | --- | --- | --- |
| Tracker | On range-aware policies, +2 effective vision range and targets the visible player before any Adventurer, even when an Adventurer is closer. | Leave its effective vision, use invisibility/freeze, or let it chase you away from Adventurers. | Initially restricted to Patrol Guard, the current policy that uses `npcVisionRange`; Direct and Predictive have unlimited acquisition range and are ineligible. |
| Sprinter | Every N NPC moves, takes one extra move if still on a valid path; capped to avoid chain captures in one frame. | Watch cadence, use slow/freeze, route through chokepoints. | Needs careful timing tests. |
| Jammer | Power-ups within a small radius expire faster or cannot be magnet-pulled while the Jammer is nearby. | Lure away before collecting, prioritize Jammer avoidance. | Requires clear UI; defer if unclear. |
| Guardian | Prefers patrolling near the exit until the player is close, then chases. | Plan an approach, use blast/teleport. | Can reuse patrol/guard policy ideas. |
| Sentinel | Slower than normal but cannot be frozen for the full duration; freeze applies a reduced duration. | Avoid rather than disable. | High-risk because it changes power-up expectations; use later. |

Initial implementation recommendation: ship `Tracker`, then `Guardian`, then `Sprinter`; hold `Jammer` and `Sentinel` until UI clarity is proven.

### Phase 2 implementation contract

- Only `TRACKER` is implemented. Its stable saved/telemetry ID is `tracker`;
  Guardian, Sprinter, Jammer, Sentinel, and movement-cadence hooks remain deferred.
- Tracker applies only to Patrol Guard. It adds exactly two Manhattan-distance
  cells to acquisition range and prefers a reachable, visible, in-range player
  over a closer Adventurer. Otherwise normal Adventurer ranking applies,
  including invisibility and ID tie-breaking. No wall line-of-sight rule is added.
  Losing acquisition does not erase the guard's existing last-known target.
- Power-up effects, movement speed, collision rules, and patrol/search behavior
  are unchanged. Invisibility prevents new player acquisition; Freeze still
  stops the guard. There are no extra moves.
- `NpcSpawnSpec` is the canonical ordered policy/modifier assignment. The
  controller locks the complete requested roster with the maze seed and count,
  including when a route choice locks the next maze. Legacy policy-only APIs
  adapt to normal NPCs; policy-list properties are derived views.
- `NpcSpawnPlanner` shares the original placement shuffle between engine and
  controller without consuming additional spawn RNG. Assignment has its own
  seed mix. Only compatible IDs that actually spawn, lie outside the direct-path
  buffer, and are more than one Chebyshev cell from the player start are eligible.
  Insufficient candidates mean fewer elites, not relocated NPCs or rerolled policies.
- Production generation remains off. Enabling the elite gate exposes Medium/Hard
  only. The initial rollout budget is at most one Tracker, with no new elites on
  count-ramp mazes (4, 7, ...), the final maze, Ambush Shortcut, or Cursed Gate.
  Easy's target cap and the wider caps below are testable balance targets, not
  enabled exposure. Hard's half-roster cap uses the actual spawned count.
- Ambush Shortcut still adds exactly one NPC and never requests an elite.
  Route cadence, starting rewards, and NPC-count formulas are unchanged.
- Adventure schema **4** persists full locked spawn specs. Engine schema **7**
  persists each active NPC's nullable modifier plus the full restart override,
  including entries beyond current spawn capacity. These versions intentionally
  invalidate Adventure schema-3 and engine schema-6 saves, including Classic saves.
  Unknown modifier IDs are rejected, never silently changed to normal NPCs.
  An invalid embedded engine snapshot may be discarded to replay an otherwise
  valid locked Adventure maze.
- Flags gate new assignments only. Death replay and compatible saved locks
  retain modifiers with generation off; enabling cannot retrofit a locked maze.
  Snapshot matching checks policies/modifiers by spawn ID and the full restart
  roster. Restore retains the existing ready-to-step contract, not frame-identical
  RNG, accumulator, or patrol-state continuation.
- A shared shape-and-color accent preserves the policy tint in the maze and
  Android legend. The optional elite legend section lists only implemented active
  modifiers, including on resumed runs with generation disabled. Classic's
  default legend remains unchanged. Rendering lookups are precomputed.
- Spawn telemetry uses the actual roster after a durably saved fresh/retry start,
  not preparation queries, reward redraws, or mid-maze resume. Outcomes follow
  successful saves and describe exposure, not which NPC caused a capture.
  Delivery is best-effort through the no-op sink; no production analytics or
  modifier-specific death attribution is installed.

Internal dogfood, device/accessibility checks, copy/visual approval, and balance
review remain prerequisites for enabling generation or widening the rollout budget.

## Spawn and selection rules

- Elite generation must be deterministic from the Adventure run seed and maze index, using an independent seed mix.
- Lock each NPC's modifier assignment with the existing per-maze NPC policy lock so death replays preserve the same threats.
- Filter modifier candidates by policy compatibility before seeded selection;
  initially, assign Tracker only to `PATROL_GUARD`.
- Suggested baseline caps:
  - Easy: no elites before maze 3; max 1 elite.
  - Medium: max 1 elite until final maze; final may have 2 if NPC count allows.
  - Hard: max 1 elite early, 2 from maze 5 onward, never more than half of NPCs.
- Do not assign mutually confusing combinations in the first release; prefer one modifier per NPC.
- Do not spawn elites adjacent to the player start or in positions that violate existing spawn-buffer expectations.

## Difficulty scaling interactions and caps

| Interaction | Rule |
| --- | --- |
| NPC count ramp | Elite count should substitute for some count pressure when needed; avoid `+NPC` and `+elite` from separate systems on the same maze unless explicitly marked risky. |
| Route Events | A risky route may request one elite, but cap total elites after route and baseline scaling are merged. |
| Power-ups | Modifiers should respect existing SHIELD, FREEZE, INVISIBILITY, SLOW_TIME, MAGNET, BLAST, and GHOST_MODE semantics unless the modifier explicitly advertises an exception. |
| Adventurers | Targeting logic must remain deterministic when choosing between player and Adventurers. |
| Final maze | Final-maze bonus can include elite pressure, but completion rate should remain within the target range for the difficulty. |

## Implemented data model and API

- `EliteNpcModifier` contains only `TRACKER`, with stable ID, compatibility,
  range-bonus, and platform-neutral accent metadata.
- `NpcSpawnSpec` combines an immutable policy and optional modifier, rejecting
  incompatible pairs. Ordered lists use the NPC spawn ID as their index.
- `AdventureRunState.currentMazeNpcSpawnSpecs` and `MazeStartupSpec.npcSpawnSpecs`
  are canonical; their policy-only accessors are derived.
- `Npc.eliteModifier` carries the active assignment. Engine snapshots store both
  this per-NPC field and the complete restart specs, which may exceed spawn capacity.
- Android resource mappings own localized names and descriptions; snapshots never
  store localized text or Android resource IDs.

Tracker uses narrow Patrol Guard acquisition/ranking changes, not a generic
modifier event bus. Cadence hooks and a shared behavior interface remain deferred
until another approved modifier requires them.

## Rendering and legend updates

- Reuse the existing `NpcPolicyType` metadata pattern: label, description, and color are single sources of truth for renderer and legend.
- Render a small accent mark or outline for elite status rather than replacing the base policy tint; the player should read both “policy” and “modifier”.
- Precompute any elite color/icon lookup arrays like `PowerUpIcons`/NPC icon helpers to avoid per-frame allocation.
- Add a separate “Elite modifiers” section; Tracker's illustration uses Patrol Guard,
  the only compatible base policy.

## Integration points

- `AdventureRunController`
  - Generate deterministic `NpcSpawnSpec` values in `lockCurrentMaze()`, shared by
    preparation and route selection.
  - Store locked specs in run state for death replay.
- `GameEngine.configureAdventureMaze(...)`
  - Accept complete spawn specs in the existing atomic configuration flow;
    policy-only calls adapt to unmodified NPCs.
- `Policies.kt`
  - Apply Tracker only to Patrol Guard acquisition range and target preference.
- `GameEngineSnapshot`
  - Persist every active modifier and complete restart specs; require full specs
    whenever a count override is present.
- `AdventureRunStateSnapshot`
  - Persist per-maze locked specs and validate active assignments by NPC ID.
- `MazeRenderer`, `NpcIcons`, `LegendDialog`
  - Display modifier accent and explain mechanics.

## Telemetry and balancing dashboard suggestions

Canonical allowlisted events (the first two have no-op helpers; death attribution
remains deferred):

| Event | Properties |
| --- | --- |
| `adventure_elite_modifier_spawned` | `difficulty`, `maze_index`, `modifier_id`, `npc_count`, `elite_count`, `player_policy` |
| `adventure_elite_modifier_outcome` | `difficulty`, `maze_index`, `modifier_id`, `completed`, `elapsed_seconds`, `steps`, `deaths_this_run` |
| `adventure_death_context` | `difficulty`, `maze_index`, `modifier_id`, `death_cause`, `active_power_up` |

Only aggregate values and stable catalogue IDs are allowed. Do not include raw
or hashed seeds, positions, snapshot data, free-form text, or user/device
identifiers.

| Signal | Segment by |
| --- | --- |
| Win/loss rate on elite mazes | difficulty, maze index, modifier, NPC count |
| Death distance/time after elite first targets player | modifier, player policy, active power-up |
| Power-up pickup rate around Jammer-like effects | modifier, power-up type |
| Retry success after elite death | modifier, lives remaining |
| Completion rate with elites enabled | difficulty, elite cap version |

Dashboard guardrails:

- No modifier should raise next-maze death rate by more than an agreed threshold without increasing reward value.
- Easy modifier exposure should improve novelty without reducing first-run completion substantially.
- Hard mode may tolerate higher death rates if retry rate remains healthy.

## Test matrix

| Area | Tests |
| --- | --- |
| Determinism | Same run seed/maze index produces same policy and modifier list; death replay preserves it; snapshot round-trip preserves it. |
| Regression | Existing direct/predictive/patrol behavior remains unchanged when modifier is `null`. |
| Edge cases | Zero NPCs, fewer NPCs than elite cap, final maze, unknown/removed modifier in saved data. |
| Mechanics | Tracker is Patrol Guard-only, adds exactly two Manhattan cells, and prioritizes a reachable visible player; normal power-up, patrol/search, and Adventurer-selection behavior is retained. |
| Rendering | Adventure legend includes active implemented modifiers; Classic stays unchanged; renderer lookup handles every enum exhaustively without per-frame allocation. |

## Rollout, rollback, and risk mitigation

- Roll out one modifier at a time behind `AdventureFeatureFlags.ELITE_NPC_MODIFIERS_ENABLED`, which defaults off.
- Start with low-risk `Tracker` on Medium/Hard only.
- Keep all modifier assignment data additive and schema-versioned.
- Roll back by disabling new assignment generation; compatible locked assignments
  and their legend explanations remain active. If persisted shape or mechanics
  change incompatibly, bump the relevant snapshot schema to clear stale runs safely.
- Mitigate fairness risk with seed fixtures that reproduce representative hard cases and by capping modifier density independently from NPC count.
