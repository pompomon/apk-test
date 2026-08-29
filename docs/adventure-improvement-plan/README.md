# Adventure improvement plan

## Purpose and scope

This documentation set turns the Adventure-mode entertainment recommendations into implementation-ready plans. It covers three features only:

1. [Route Events](01-route-events.md) — choice-based branching every few mazes.
2. [Elite NPC Modifiers](02-elite-npc-modifiers.md) — behavior variety without relying only on NPC-count scaling.
3. [Run Build Perks](03-run-build-perks.md) — persistent mini-perks that shape a run.

Scope is planning, sequencing, validation, and rollback guidance. This folder does **not** change gameplay code.

## Current Adventure-mode state

Adventure mode is a run controller layered above the single-maze `GameEngine`:

- `AdventureConfig` defines difficulty-specific run length, lives, and NPC count scaling: Easy 5 mazes / 5 lives / base 1 NPC, Medium 7 / 3 / base 1, Hard 9 / 1 / base 2.
- `AdventureRunController` chains mazes, locks each maze seed and per-NPC policy list for deterministic retries, tracks lives, win streak, unlocked player policies, pending starting power-up rewards, run time, steps, and deaths.
- `AdventureRunStateSnapshot` persists run-level state separately from single-maze `GameEngineSnapshot` through `AdventureStateStore`.
- `AdventureActivity` hosts overlays for maze win/loss, automated-policy selection, starting power-up choices, best-time completion, and restart/continue flow.
- `GameEngine.configureAdventureMaze(...)` applies per-maze NPC count and NPC policy overrides; `GameFragment.configureAdventureMaze(...)` also applies a chosen starting power-up before countdown.

The current entertainment loop is solid but mostly linear: maze count advances, NPC count ramps every three mazes plus a final-maze bonus, every automated player policy unlocks after maze 1, and every non-final win offers one starting power-up for the next maze. Future phases preserve this baseline unless a separate reward-cadence change is designed and approved.

## Prioritized backlog

| Seq. | Priority | Feature | Impact | Effort | Risk | Dependencies | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | P0 | Instrumentation scaffolding | Medium: supports tuning and safe rollout | Low-Medium | Low: avoid collecting personally identifiable data | Event naming, optional analytics sink | TBD | [x] |
| 2 | P0 | Balance test harness | Medium: keeps additions deterministic and bounded | Low | Low | JVM tests, seed fixtures | TBD | [x] |
| 3 | P0 | Route Events | High: adds agency and run-to-run variety quickly | Medium | Medium: reward/difficulty balance | Adventure controller reward phase, overlays, snapshot schema | TBD | [ ] |
| 4 | P1 | Elite NPC Modifiers | High: improves moment-to-moment novelty and readable threats | Medium-High | Medium-High: fairness, renderer clarity, determinism | NPC metadata/policy hooks, `GameEngine` snapshots, legend | TBD | [ ] |
| 5 | P2 | Run Build Perks | High: adds long-term run identity and replay goals | High | High: stacking, persistence, balance interactions | Route/reward infrastructure, HUD/popover summaries, power-up metadata | TBD | [ ] |

## Milestone plan

### Quick Wins

- [x] Add telemetry/event-name constants and a no-op sink for Adventure decisions, outcomes, elapsed time, deaths, retries, and chosen rewards.
- [x] Add deterministic JVM golden fixtures for Adventure reward/maze preparation paths.
- [x] Add provisional UI copy placeholders for future choice dialogs and summaries.
- [x] Add default-off internal gates for Route Events, Elite NPC Modifiers, and Run Build Perks.

Quick Wins contracts:

- `AdventureTelemetryEventNames` and `AdventureTelemetryPropertyNames` are the canonical wire-name allowlists. `AdventureTelemetryEvent` rejects unknown property names, and `NoOpAdventureTelemetrySink` performs no I/O.
- Telemetry payloads may contain only aggregate gameplay values and stable catalogue IDs. Raw or hashed run seeds, coordinates, snapshots, free-form text, and user/device identifiers are excluded.
- `AdventureFeatureFlags.ROUTE_EVENTS_ENABLED`, `ELITE_NPC_MODIFIERS_ENABLED`, and `RUN_BUILD_PERKS_ENABLED` all default to `false`.
- Placeholder strings use `adventure_route_*`, `adventure_elite_*`, and `adventure_perk_*` keys. Their wording is provisional until product/design approval and they are not wired into gameplay.

### Phase 1 — Route Events

- [ ] Add route-event state, offer generation, choice application, and snapshot round-trip.
- [ ] Insert route-event overlay before the existing reward chooser on eligible maze wins.
- [ ] Gate event frequency to every 2-3 mazes and cap risk stacking.

### Phase 2 — Elite NPC Modifiers

- [ ] Add modifier metadata and deterministic assignment per maze.
- [ ] Wire modifiers into NPC behavior hooks and renderer/legend labeling.
- [ ] Cap elite density and verify fairness across Easy/Medium/Hard.

### Phase 3 — Run Build Perks

- [ ] Add persistent perk state, offer generation, and reward-phase selection.
- [ ] Apply perk effects through engine configuration and power-up timing hooks.
- [ ] Summarize active perks in HUD/menu popovers and completion screens.

## Definition of Done

| Feature | Done means |
| --- | --- |
| Route Events | Events appear on deterministic eligible maze wins; choices apply to the next maze or run state; state survives pause/resume; event rewards cannot stack into impossible difficulty; UI has clear copy and skip/continue semantics; JVM tests cover determinism, persistence, and guardrails. |
| Elite NPC Modifiers | Modifier assignment is seed-locked per maze; each modifier has readable behavior, icon/tint/legend support, and bounded difficulty impact; death replay and resume preserve modifiers; tests cover modifier mechanics, determinism, and snapshot compatibility. |
| Run Build Perks | Offers present three meaningful choices with anti-duplication and rarity weights; selected perks persist for the run; stacking caps are enforced; effects interact predictably with existing power-ups; HUD/popover copy explains active perks; tests cover offer generation, stacking, and persistence. |

## Success metrics and KPIs

Suggested metrics should be anonymous, aggregate, and optional until a telemetry sink exists.

| Metric | Why it matters | Placeholder event(s) |
| --- | --- | --- |
| Adventure start-to-completion rate by difficulty | Detects whether the run remains fair and motivating | `adventure_run_started`, `adventure_run_completed`, `adventure_run_lost` |
| Maze-to-maze continuation rate | Shows whether reward/event screens encourage “one more maze” | `adventure_maze_completed`, `adventure_maze_started` |
| Retry rate after death | Measures frustration versus motivation | `adventure_maze_failed`, `adventure_maze_retried`, `adventure_run_abandoned` |
| Median session length and run elapsed time | Confirms target run length remains around the documented 10-20 minute range | `adventure_session_ended`, `adventure_run_completed` |
| Choice distribution and win rate by choice | Identifies dominant or trap choices | `adventure_route_event_chosen`, `adventure_perk_chosen`, `adventure_elite_modifier_spawned` |
| Death causes near modifiers/events | Highlights unfair modifier or route combinations | `adventure_death_context` |

## Assumptions

- Adventure remains deterministic: same run seed, maze index, choice history, and snapshot should reproduce the same next maze.
- New run-affecting state must be added to `AdventureRunStateSnapshot`; new `GameEngine` gameplay state must also round-trip through `GameEngineSnapshot` and bump its schema.
- UI work should reuse existing `AdventureActivity` dialog flow first; custom overlays can follow only if dialogs become limiting.
- Renderer additions must preserve the no-allocation-per-frame convention.
- Quick Wins add contracts only: they do not emit telemetry, enable features, alter reward cadence, or change either snapshot schema.

## Detailed documents

- [01-route-events.md](01-route-events.md)
- [02-elite-npc-modifiers.md](02-elite-npc-modifiers.md)
- [03-run-build-perks.md](03-run-build-perks.md)
- [implementation-roadmap.md](implementation-roadmap.md)

## References

- Hunicke, LeBlanc, Zubek — [MDA: A Formal Approach to Game Design and Game Research](https://users.cs.northwestern.edu/~hunicke/MDA.pdf)
- Mega Crit — [Slay the Spire: How to Design Cards for an Engine-Building Roguelike](https://www.gdcvault.com/play/1026409/-Slay-the-Spire-How-to)
- Supergiant Games — [Designing for Action Roguelike in Hades](https://www.gdcvault.com/play/1027194/Designing-For-Action-Roguelike-in)
- Valve — [The AI Systems of Left 4 Dead](https://www.valvesoftware.com/publications/2009/ai_systems_of_l4d_mike_booth.pdf)
- The Level Design Book — [Combat Balance](https://book.leveldesignbook.com/process/combat/balance)
