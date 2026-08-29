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
- `AdventureActivity` hosts overlays for maze win/loss, policy unlock choices, starting power-up choices, best-time completion, and restart/continue flow.
- `GameEngine.configureAdventureMaze(...)` applies per-maze NPC count and NPC policy overrides; `GameFragment.configureAdventureMaze(...)` also applies a chosen starting power-up before countdown.

The current entertainment loop is solid but mostly linear: maze count advances, NPC count ramps every three mazes plus a final-maze bonus, rewards alternate between player-policy unlocks and one starting power-up for the next maze.

## Prioritized backlog

| Priority | Feature | Impact | Effort | Risk | Dependencies | Owner | Status |
| --- | --- | --- | --- | --- | --- | --- | --- |
| P0 | Route Events | High: adds agency and run-to-run variety quickly | Medium | Medium: reward/difficulty balance | Adventure controller reward phase, overlays, snapshot schema | TBD | [ ] |
| P1 | Elite NPC Modifiers | High: improves moment-to-moment novelty and readable threats | Medium-High | Medium-High: fairness, renderer clarity, determinism | NPC metadata/policy hooks, `GameEngine` snapshots, legend | TBD | [ ] |
| P2 | Run Build Perks | High: adds long-term run identity and replay goals | High | High: stacking, persistence, balance interactions | Route/reward infrastructure, HUD/popover summaries, power-up metadata | TBD | [ ] |
| P0 | Instrumentation scaffolding | Medium: supports tuning and safe rollout | Low-Medium | Low: avoid collecting personally identifiable data | Event naming, optional analytics sink | TBD | [ ] |
| P0 | Balance test harness | Medium: keeps additions deterministic and bounded | Low | Low | JVM tests, seed fixtures | TBD | [ ] |

## Milestone plan

### Quick Wins

- [ ] Add telemetry/event-name constants or placeholders for Adventure decisions, outcomes, elapsed time, deaths, retries, and chosen rewards.
- [ ] Add deterministic JVM test fixtures for Adventure reward/maze preparation paths.
- [ ] Add UI copy placeholders for future choice dialogs and summaries.

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
| Maze-to-maze continuation rate | Shows whether reward/event screens encourage “one more maze” | `adventure_maze_completed`, `adventure_next_maze_started` |
| Retry rate after death | Measures frustration versus motivation | `adventure_maze_failed`, `adventure_maze_retried`, `adventure_run_abandoned` |
| Median session length and run elapsed time | Confirms target run length remains around the documented 10-20 minute range | `adventure_session_ended`, `adventure_run_completed` |
| Choice distribution and win rate by choice | Identifies dominant or trap choices | `route_event_chosen`, `perk_chosen`, `elite_modifier_spawned` |
| Death causes near modifiers/events | Highlights unfair modifier or route combinations | `adventure_death_context` |

## Assumptions

- Adventure remains deterministic: same run seed, maze index, choice history, and snapshot should reproduce the same next maze.
- New run-affecting state must be added to `AdventureRunStateSnapshot`; new `GameEngine` gameplay state must also round-trip through `GameEngineSnapshot` and bump its schema.
- UI work should reuse existing `AdventureActivity` dialog flow first; custom overlays can follow only if dialogs become limiting.
- Renderer additions must preserve the no-allocation-per-frame convention.

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
