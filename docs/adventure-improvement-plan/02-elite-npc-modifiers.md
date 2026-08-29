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
| Tracker | +2 effective vision range and prefers the player over Adventurers in equal-distance target ties. | Break line/path distance with maze turns, use invisibility/freeze. | Good first modifier; builds on existing target selection. |
| Sprinter | Every N NPC moves, takes one extra move if still on a valid path; capped to avoid chain captures in one frame. | Watch cadence, use slow/freeze, route through chokepoints. | Needs careful timing tests. |
| Jammer | Power-ups within a small radius expire faster or cannot be magnet-pulled while the Jammer is nearby. | Lure away before collecting, prioritize Jammer avoidance. | Requires clear UI; defer if unclear. |
| Guardian | Prefers patrolling near the exit until the player is close, then chases. | Plan an approach, use blast/teleport. | Can reuse patrol/guard policy ideas. |
| Sentinel | Slower than normal but cannot be frozen for the full duration; freeze applies a reduced duration. | Avoid rather than disable. | High-risk because it changes power-up expectations; use later. |

Initial implementation recommendation: ship `Tracker`, then `Guardian`, then `Sprinter`; hold `Jammer` and `Sentinel` until UI clarity is proven.

## Spawn and selection rules

- Elite generation must be deterministic from the Adventure run seed and maze index, using an independent seed mix.
- Lock each NPC's modifier assignment with the existing per-maze NPC policy lock so death replays preserve the same threats.
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

## Data model and API proposal

```kotlin
enum class EliteNpcModifier(
    val label: String,
    val description: String,
    val colorAccentRgb: Triple<Float, Float, Float>
) {
    TRACKER(...),
    GUARDIAN(...),
    SPRINTER(...)
}

data class NpcSpawnSpec(
    val policyType: NpcPolicyType,
    val eliteModifier: EliteNpcModifier? = null
)
```

Possible implementation paths:

1. Minimal path: extend Adventure per-maze state from `List<NpcPolicyType>` to `List<NpcSpawnSpec>`.
2. Engine path: add `eliteModifier` metadata to `Npc` and teach policy execution to consult modifier hooks.
3. Snapshot path: persist modifier metadata in `GameEngineSnapshot.NpcSnapshot` if modifiers affect active in-maze behavior or must survive paused-mid-maze resume.

Policy/modifier hooks:

```kotlin
interface NpcModifierBehavior {
    fun adjustVisionRange(base: Int): Int = base
    fun adjustTargetRanking(...): TargetRanking = unchanged
    fun extraMoveBudget(...): Int = 0
    fun adjustPowerUpContext(...): PowerUpContext = unchanged
}
```

Keep hooks narrow and explicit; avoid a generic event bus until at least two modifiers need the same extension point.

## Rendering and legend updates

- Reuse the existing `NpcPolicyType` metadata pattern: label, description, and color are single sources of truth for renderer and legend.
- Render a small accent mark or outline for elite status rather than replacing the base policy tint; the player should read both “policy” and “modifier”.
- Precompute any elite color/icon lookup arrays like `PowerUpIcons`/NPC icon helpers to avoid per-frame allocation.
- Add legend rows such as `Direct Chase + Tracker` only if combinations are few; otherwise add a separate “Elite modifiers” legend section.

## Integration points

- `AdventureRunController`
  - Generate deterministic `NpcSpawnSpec` values during `prepareCurrentMaze()`.
  - Store locked specs in run state for death replay.
- `GameEngine.configureAdventureMaze(...)`
  - Either overload with spawn specs or add a parallel `configureAdventureNpcModifiers(...)` call.
- `Policies.kt`
  - Apply modifier hooks around target selection, vision range, movement cadence, or patrol behavior.
- `GameEngineSnapshot`
  - Persist active NPC modifiers if paused-mid-maze resume needs them.
- `AdventureRunStateSnapshot`
  - Persist per-maze locked modifier list and any route-requested elite pressure.
- `MazeRenderer`, `NpcIcons`, `LegendDialog`
  - Display modifier accent and explain mechanics.

## Telemetry and balancing dashboard suggestions

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
| Mechanics | Tracker range changes target acquisition; Guardian patrols near exit; Sprinter extra move respects cap and cannot move after terminal status. |
| Rendering | Legend includes every modifier; renderer lookup handles every enum exhaustively without per-frame allocation. |

## Rollout, rollback, and risk mitigation

- Roll out one modifier at a time behind a constant or remote-config placeholder.
- Start with low-risk `Tracker` on Medium/Hard only.
- Keep all modifier assignment data additive and schema-versioned.
- Roll back by setting elite cap to 0 and ignoring future generated modifiers; if persisted shape changes incompatibly, bump relevant snapshot schema to clear stale runs safely.
- Mitigate fairness risk with seed fixtures that reproduce representative hard cases and by capping modifier density independently from NPC count.
