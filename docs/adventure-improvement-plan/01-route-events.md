# 01 — Route Events

## Problem statement

Adventure mode currently advances through a mostly fixed sequence: win a maze, receive either a player-policy unlock or a starting power-up choice, then enter the next maze. NPC count ramps, but the player has limited agency over the shape of the run. Route Events add lightweight branching decisions every 2-3 mazes so the player can choose safety, risk, or utility tradeoffs.

## Player experience goals

- Make the run feel less linear without adding heavy narrative or asset work.
- Give players understandable risk/reward decisions between maze attempts.
- Support comeback choices after deaths and optional greed choices when ahead.
- Keep choices short enough for mobile play: one title, one sentence, one mechanical summary.

## Functional design

- Trigger a route-event offer after maze wins on mazes 2, 4, 6, ... by default, with seeded jitter allowing a 2-3 maze cadence.
- Do not trigger on the final maze win.
- Present 2-3 route choices before the existing reward chooser:
  1. Player wins maze.
  2. `AdventureActivity` shows route-event dialog if eligible.
  3. Player chooses one route.
  4. Controller records and applies the route modifier.
  5. Existing unlock or starting-power-up chooser proceeds.
  6. Next maze starts with the chosen modifier applied.
- Each event affects either the next maze only or a clearly bounded run-level counter.

## Event taxonomy

| Category | Example | Effect | Intended emotion |
| --- | --- | --- | --- |
| Safe | Quiet Corridor | Next maze has `npcCount - 1`, minimum 1 on Medium/Hard and 0 only for test/custom configs. Reward options reduced by one if a reward follows. | Relief, recovery |
| Safe | Scout Map | Reveal upcoming route-event category and next-maze NPC count before choosing reward. | Planning |
| Risky | Ambush Shortcut | Next maze adds +1 NPC or one elite modifier; completing it grants an extra reward reroll. | Tension, greed |
| Risky | Cursed Gate | Next maze starts with shorter power-up pickup lifetime; completing it grants +1 life progress toward streak bonus. | High stakes |
| Utility | Supply Cache | Choose one starting power-up immediately; suppresses the normal even-maze power-up offer if both would occur. | Preparation |
| Utility | Training Room | Temporarily unlock one automated player policy for the next maze only. | Experimentation |

## Balancing rules and guardrails

- Never apply more than one route-event difficulty increase to the same maze.
- Safe choices cannot reduce final-maze pressure below the baseline previous-maze NPC count unless explicitly designed as an accessibility aid.
- Risky choices should compensate with bounded rewards, not permanent raw stat inflation.
- Utility choices should not duplicate existing reward screens in the same step; merge or suppress duplicate choices.
- On death replay, the selected route effect remains locked so the retry is deterministic and fair.
- On run loss or completion, all route-event state is cleared with the adventure save.

## Data model proposal

```kotlin
enum class RouteEventCategory { SAFE, RISKY, UTILITY }

enum class RouteEventEffectType {
    NPC_COUNT_DELTA,
    ELITE_MODIFIER_HINT,
    STARTING_POWER_UP_CHOICE,
    TEMPORARY_POLICY_UNLOCK,
    POWER_UP_LIFETIME_DELTA,
    REWARD_REROLL
}

data class RouteEventChoice(
    val id: String,
    val title: String,
    val category: RouteEventCategory,
    val description: String,
    val effects: List<RouteEventEffect>
)

data class RouteEventEffect(
    val type: RouteEventEffectType,
    val intValue: Int = 0,
    val policy: PlayerPolicyType? = null,
    val powerUp: PowerUpType? = null
)

data class PendingRouteEvent(
    val mazeIndexAppliedTo: Int,
    val choiceId: String,
    val effects: List<RouteEventEffect>
)
```

`ELITE_MODIFIER_HINT` is reserved for risky Ambush-style route choices that request one elite threat on the next maze while still deferring the concrete modifier assignment to the Elite NPC system's seeded selection rules.

Persistence changes:

- Add route history and pending route effect fields to `AdventureRunState` and `AdventureRunStateSnapshot`.
- Keep generated offers out of persistence unless a process can die while the offer dialog is visible. If so, persist the exact offered IDs plus selected index state.
- If route effects alter `GameEngine` runtime state beyond existing `configureAdventureMaze(...)` inputs, add those fields to `GameEngineSnapshot` and bump its schema.

## Integration points

- `AdventureRunController`
  - Add `routeEventOfferForCompletedMaze(outcome)` or fold into `onMazeWon` result.
  - Add `applyRouteEventChoice(choice)` before reward selection commits.
  - Include selected route effects when building `MazeStartupSpec`.
- `AdventureRunStateSnapshot`
  - Persist selected route effects and route history.
  - Validate unknown IDs by dropping pending effects only on schema-compatible migrations; otherwise bump schema.
- `AdventureActivity`
  - Insert route-event dialog before `showUnlockChooser(...)` / `showStartingPowerUpChooser(...)`.
  - Persist only after player commits all required choices, matching the current reward-dialog safety pattern.
- `GameFragment` / `GameEngine`
  - Prefer extending `MazeStartupSpec` first; only add engine APIs for effects that cannot be represented as NPC count, policy list, or starting power-up.

## UI/UX flow

```text
Maze 2 cleared!

Choose your route:
[Quiet Corridor]
  Fewer NPCs next maze, but one fewer reward option.
[Ambush Shortcut]
  One extra threat next maze. Complete it for a reward reroll.
[Supply Cache]
  Pick a starting power-up before the next maze.

Continue -> existing reward chooser -> countdown -> next maze
```

Copy rules:

- Mention duration: “next maze”, “this run”, or “once”.
- Show exact numbers when possible: “+1 NPC”, “-1 reward option”.
- Avoid hidden penalties.

## Telemetry events and A/B test plan

| Event | Properties |
| --- | --- |
| `route_event_offered` | difficulty, runSeedHash, mazeIndex, offeredChoiceIds, categories |
| `route_event_chosen` | difficulty, mazeIndex, choiceId, category, livesRemaining, deathsThisRun |
| `route_event_applied` | nextMazeIndex, choiceId, npcCountDelta, rewardDelta, eliteHint |
| `route_event_outcome` | choiceId, nextMazeWon, elapsedSeconds, steps, deathCountDelta |

A/B test:

- Control: current Adventure rewards only.
- Variant A: fixed route events after every 2 wins.
- Variant B: seeded 2-3 maze cadence with no back-to-back risky-only offers.
- Primary metric: continuation rate after eligible maze wins.
- Guardrail metrics: death rate on the next maze, run completion rate, median session length.

## Test plan

- Unit
  - Same run seed and maze index generate identical route offers.
  - Death replay preserves the selected pending route effect.
  - Route effects respect NPC-count floors and reward-option caps.
  - Snapshot round-trip preserves pending route effect and history.
- Integration
  - Win flow shows route event before reward dialog when eligible.
  - Process death during route choice restores or safely replays the prior maze without losing an uncommitted choice.
- Manual
  - Verify Easy never becomes a surprise difficulty spike.
  - Verify copy fits on small screens and Android back cannot skip required decisions.

## Rollout strategy and rollback plan

- Ship behind an internal feature flag or constant defaulting off until tests and copy are stable.
- Enable only on Medium for first dogfood pass; then Easy/Hard after balance review.
- Roll back by disabling offer generation and ignoring stored pending route effects on load; if stored state shape changed incompatibly, bump `AdventureRunStateSnapshot` schema to clear in-progress runs safely.

## Open questions

- Should route events replace some existing reward offers or always precede them?
- Should risky routes guarantee stronger rewards or only improve reward odds?
- Is there a future map screen, or should all route selection stay dialog-based for now?
- What telemetry sink, if any, should production builds use?
