# 03 — Run Build Perks

## Problem statement

Adventure currently grants player-policy unlocks and one-maze starting power-ups, but choices do not yet form a persistent build identity. Run Build Perks add small, cumulative, run-long bonuses so players can develop a strategy over several mazes without overwhelming the mobile UI.

## Design goals

- Build identity: selected perks should make two runs feel different.
- Meaningful choices: each offer should contain distinct play styles, not three near-equivalent stat boosts.
- Low cognitive load: descriptions must be short and mechanically precise.
- Deterministic fairness: offer generation and stacking outcomes must be seed-locked and persisted.
- Compatibility: perks should enhance existing power-ups/effects rather than replacing them.

## Perk pool, tiers, and stacking rules

| Perk | Tier | Effect | Stack cap | Notes |
| --- | --- | --- | --- | --- |
| Quick Feet | Common | +5% player movement speed. | 3 | Cap at +15%; avoid trivializing NPC speed balance. |
| Longer Charge | Common | +1s timed power-up duration. | 3 | Applies to SPEED_UP, FREEZE, SHIELD, SLOW_TIME, MAGNET, INVISIBILITY, GHOST_MODE unless excluded. |
| Pocket Magnet | Common | +1 cell magnet pickup radius while MAGNET is active. | 2 | No effect without MAGNET; label clearly. |
| First Shield | Uncommon | Start each maze with a short SHIELD if no other starting power-up is pending. | 1 | Avoid stacking with selected starting SHIELD reward. |
| Scout Sense | Uncommon | Show next maze's NPC count and elite count before reward choice. | 1 | Best paired with Route Events/Elites. |
| Second Wind | Rare | Once per run, survive a capture with 1s freeze pulse and consume the perk. | 1 | Must be highly visible and logged. |
| Risk Dividend | Rare | Risky route completion offers one extra reward option. | 1 | Requires Route Events. |

Tier guidance:

- Common perks are reliable small stats or quality-of-life bonuses.
- Uncommon perks change planning or start conditions.
- Rare perks can be run-defining but should be limited to one copy.

## Offer generation algorithm

- Offer exactly 3 choices when at least 3 eligible perks exist; otherwise offer all eligible perks.
- Eligibility removes perks at stack cap and perks whose dependencies are disabled.
- Use an independent RNG derived from run seed, maze index, and reward count.
- Apply rarity weights after eligibility filtering:
  - Common: 70
  - Uncommon: 25
  - Rare: 5
- Anti-duplication:
  - Do not show a perk already chosen at cap.
  - Avoid showing the same perk in consecutive perk offers unless fewer than 3 alternatives exist.
  - Prefer category diversity: at most two pure stat perks in one offer.
- Persist the exact offer while the chooser is visible if process death could otherwise reroll choices.

Pseudocode:

```text
eligible = perks.filter(notAtCap && dependenciesEnabled)
weightedPool = expandByRarity(eligible)
choices = []
while choices.size < 3 and eligible remains:
  candidate = weightedDeterministicDraw(weightedPool)
  if candidate not duplicate and category cap ok:
    choices += candidate
  remove candidate from weightedPool for this offer
persist offer ids until player chooses or replay intentionally returns to previous maze
```

## Interaction rules with existing power-ups and effects

| Existing system | Perk interaction |
| --- | --- |
| Starting power-up reward | Perks that grant start-of-maze effects should not overwrite `pendingStartingPowerUp`; define precedence and show copy. |
| Timed effects | Duration perks adjust activation duration once, when the effect is applied, not every tick. |
| Instant effects | TELEPORT and BLAST usually should not receive duration bonuses. |
| MAGNET | Radius perks should use the same safety/reachability checks as current magnet collection. |
| SHIELD/FREEZE/INVISIBILITY | Avoid perk combinations that grant permanent safety; use duration caps and per-maze limits. |
| Automated policies | Movement-speed perks must not break policy tie-breaker determinism; they should affect cadence, not path ranking. |

## Data model and persistence updates

```kotlin
enum class RunPerkId { QUICK_FEET, LONGER_CHARGE, POCKET_MAGNET, FIRST_SHIELD, SCOUT_SENSE, SECOND_WIND, RISK_DIVIDEND }

enum class RunPerkTier { COMMON, UNCOMMON, RARE }

data class RunPerkDefinition(
    val id: RunPerkId,
    val label: String,
    val tier: RunPerkTier,
    val maxStacks: Int,
    val description: String
)

data class RunPerkStack(
    val id: RunPerkId,
    val stacks: Int,
    val consumed: Boolean = false
)

data class PendingPerkOffer(
    val mazeIndexCompleted: Int,
    val offeredPerks: List<RunPerkId>
)
```

`AdventureRunStateSnapshot` should add:

- `runPerks: List<RunPerkStack>`
- `pendingPerkOffer: PendingPerkOffer?` when a chooser can survive process death
- optional `perkOfferHistory: List<List<RunPerkId>>` or a compact recent-offer list for anti-duplication

If a perk adds active in-maze runtime state, persist it in `GameEngineSnapshot` too.

## Integration points

- `AdventureRunController`
  - Generate perk offers in the reward phase, likely after Route Events and before/after current unlock/power-up rewards depending on final cadence.
  - Apply selected stacks and expose derived effects in `MazeStartupSpec`.
- `GameEngine.configureAdventureMaze(...)`
  - Accept derived gameplay knobs such as player speed multiplier, power-up duration bonus, or start-of-maze shield request.
- `AdventureActivity`
  - Add perk chooser dialog.
  - Add active-perk summary to completion and pause/menu popovers.
- HUD/popover summaries
  - Show compact active perks: `Quick Feet x2`, `Longer Charge x1`, `Second Wind ready/used`.
- `PowerUps.kt` / power-up activation path
  - Apply duration/radius modifiers in one place so all collectors and tests share semantics.

## UX copy guidance

- Use player-facing verbs: “Move 5% faster”, “Power-ups last +1s”.
- Include stack state: “Stack 2/3”.
- Use duration scope labels: “This run”, “Each maze”, “Once per run”.
- Avoid hidden math: show exact caps and when a perk is consumed.
- Prefer one tradeoff per choice; do not combine a penalty and perk unless it is a Route Event.

## Telemetry and balance guardrails

| Event | Properties |
| --- | --- |
| `perk_offer_shown` | difficulty, mazeIndex, offeredPerkIds, currentStacks |
| `perk_chosen` | perkId, stackAfterChoice, alternatives, livesRemaining |
| `perk_effect_applied` | perkId, mazeIndex, affectedSystem, amount |
| `perk_consumed` | perkId, trigger, mazeIndex |
| `perk_run_outcome` | perkIds, stacks, completed, elapsedSeconds, deathsThisRun |

Guardrails:

- No single common perk should dominate choices above a target threshold once alternatives are available.
- Stacked movement and duration perks should not reduce Medium/Hard death rates enough to erase difficulty identity.
- Rare defensive perks should increase retry motivation without making final-maze losses feel arbitrary after consumption.

## Test plan

- Unit
  - Offer generation is deterministic for the same seed and history.
  - Rarity and anti-duplication rules produce valid offers under small pools.
  - Stack caps are enforced and persisted.
  - Duration and movement modifiers apply once in the intended path.
- Integration
  - Perk chooser appears at configured reward milestones.
  - Pause/resume during chooser does not reroll choices or lose committed stacks.
  - HUD/menu summary reflects active and consumed perks.
- Manual
  - Verify copy on small screens.
  - Play Easy/Medium/Hard smoke runs with max movement and max duration stacks.
  - Confirm “Second Wind” feedback is visible when consumed.

## Staged rollout

1. Add perk data definitions and tests with no offers enabled.
2. Enable common-only perks on Medium internal builds.
3. Add uncommon perks after HUD summary exists.
4. Add rare perks only after telemetry or manual balance confirms common/uncommon caps.
5. Roll back by disabling perk offer generation and ignoring derived effects; preserve saved stacks if schema-compatible, or bump `AdventureRunStateSnapshot` schema for incompatible changes.
