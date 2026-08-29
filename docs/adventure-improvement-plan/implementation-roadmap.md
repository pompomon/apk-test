# Adventure improvement implementation roadmap

## Assumptions

- Estimates are implementation days for a small Android/Kotlin team after design approval.
- Work should preserve Adventure determinism and existing pause/resume behavior.
- JVM tests are required for new controller/state logic. Instrumented tests are required only for Android UI flows that cannot be validated through pure Kotlin.
- Documentation and telemetry scaffolding may merge before gameplay behavior is enabled.

## Sequenced delivery plan

| Phase | Estimate | Goal |
| --- | --- | --- |
| Quick Wins | 1-3 days | Prepare telemetry names, test fixtures, and UI strings without changing gameplay. |
| Phase 1 — Route Events | 4-7 days | Add choice-based branching at bounded intervals. |
| Phase 2 — Elite NPC Modifiers | 5-9 days | Add readable NPC behavior variety and renderer/legend support. |
| Phase 3 — Run Build Perks | 7-12 days | Add persistent build choices and summaries. |

## Quick Wins — docs/instrumentation scaffolding

### Tasks

- [ ] Add Adventure analytics event constants/placeholders and no-op sink interfaces if a telemetry layer is not present.
- [ ] Add seed-fixture tests around `AdventureRunController.prepareCurrentMaze()` and reward sequencing.
- [ ] Add string resources for route, elite, and perk terminology behind unused keys.
- [ ] Add internal feature flags/constants defaulting off for each feature.

### Dependencies

- Agreement on event names and privacy policy for any telemetry.
- Product/design sign-off on terminology.

### Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Telemetry scope grows beyond gameplay needs | Keep payloads aggregate and avoid personal data. |
| Unused scaffolding becomes dead code | Keep flags and constants small; land feature code soon after. |

### Acceptance criteria

- New constants compile and are covered by simple tests where useful.
- No gameplay behavior changes while flags are off.
- Existing JVM tests pass.

## Phase 1 — Route Events

### Tasks

- [ ] Define `RouteEventChoice`, `RouteEventEffect`, category/effect enums, and seeded offer generator.
- [ ] Extend `AdventureRunState` and `AdventureRunStateSnapshot` for pending effects/history.
- [ ] Add `WinOutcome` route-offer data or a dedicated pending-offer query.
- [ ] Insert the route-event chooser in `AdventureActivity` before existing reward dialogs.
- [ ] Apply selected route effects to `MazeStartupSpec` and next-maze setup.
- [ ] Add telemetry hooks and JVM tests for determinism, guardrails, and persistence.

### Dependencies

- Quick Wins feature flag and seed fixtures.
- UI copy for safe/risky/utility events.

### Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Process death during multi-dialog reward flow loses decisions | Persist only after all required choices commit, or persist exact pending offer IDs. |
| Risky route stacks with normal NPC ramp into unfair spikes | Centralize next-maze difficulty merge and cap after all modifiers are applied. |
| Event rewards duplicate existing reward screens | Suppress or merge duplicate reward choices in the controller before UI display. |

### Acceptance criteria

- Route events appear only on eligible non-final maze wins.
- Same seed and choice history reproduce identical offers and next-maze effects.
- Death replay preserves the chosen route effect.
- Snapshot round-trip covers all new route state.

## Phase 2 — Elite NPC Modifiers

### Tasks

- [ ] Define `EliteNpcModifier` metadata and initial `Tracker` modifier.
- [ ] Replace or supplement per-maze `List<NpcPolicyType>` with spawn specs carrying modifier metadata.
- [ ] Lock modifier assignments in `AdventureRunController` and persist in `AdventureRunStateSnapshot`.
- [ ] Persist in `GameEngineSnapshot` if modifiers affect paused active NPC state.
- [ ] Add modifier hooks in NPC target selection/movement logic.
- [ ] Add renderer accent and legend rows using precomputed lookup data.
- [ ] Add tests for null-modifier regression, deterministic assignment, snapshot round-trip, and mechanics.

### Dependencies

- Route Events if risky routes can request elites; otherwise standalone.
- Design approval for elite visual language.

### Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Visuals become hard to parse when policy and modifier both matter | Use base policy tint plus a consistent elite accent, and add legend explanations. |
| Existing NPC behavior regresses | Keep `null` modifier path byte-for-byte equivalent where possible and cover with regression tests. |
| New movement cadence creates unavoidable captures | Cap extra moves and evaluate terminal status after each move, matching existing game-loop discipline. |

### Acceptance criteria

- `Tracker` or first selected modifier is readable, deterministic, and capped by difficulty.
- Existing non-elite NPCs behave as before.
- Death replay and pause/resume preserve modifier assignments.
- Renderer/legend update handles all modifier enum values exhaustively.

## Phase 3 — Run Build Perks

### Tasks

- [ ] Define perk IDs, tiers, definitions, stack state, and offer-history model.
- [ ] Add deterministic three-choice offer generation with rarity weights and anti-duplication.
- [ ] Persist perk stacks and pending offers in `AdventureRunStateSnapshot`.
- [ ] Apply derived effects through `MazeStartupSpec`, `GameEngine.configureAdventureMaze(...)`, and power-up activation hooks.
- [ ] Add active-perk summaries to HUD/menu/completion surfaces.
- [ ] Add tests for offer generation, stack caps, effect application, persistence, and process-death behavior.

### Dependencies

- Reward-flow lessons from Route Events.
- HUD/popover copy pattern and icon/summary approach.
- Optional: Elite/Route telemetry for balance comparison.

### Risks and mitigations

| Risk | Mitigation |
| --- | --- |
| Stacking perks overpower Adventure difficulty | Use low caps, aggregate derived-effect clamps, and per-difficulty balance tests. |
| Players cannot understand accumulated effects | Add compact summary and scope language: “This run”, “Each maze”, “Once per run”. |
| Offer generation rerolls on resume | Persist exact pending offers while chooser is visible. |

### Acceptance criteria

- Perk offers contain valid, non-capped choices and are deterministic.
- Selected perks persist and stack only to defined caps.
- Perk effects interact with existing power-ups predictably.
- UI summaries explain active stacks and consumed one-shots.

## Suggested ownership split

| Area | Responsibilities |
| --- | --- |
| Gameplay | Controller/state models, seed-lock generation, modifier/perk mechanics, balance caps. |
| UI | Dialog sequencing, copy, HUD/menu summaries, legend/rendering updates. |
| QA | Seed fixtures, Easy/Medium/Hard smoke runs, death replay, pause/resume, process-death scenarios. |
| Analytics | Event names, privacy review, dashboards, A/B assignment and analysis. |

## Suggested PR slicing strategy

1. **Docs and scaffolding PR** — add this plan, event-name constants, flags defaulting off, and no gameplay changes.
2. **Route model PR** — pure Kotlin models/generator/snapshot tests; no UI enabled.
3. **Route UI/effects PR** — dialog flow and bounded effects behind flag.
4. **Elite metadata PR** — enum/metadata/render legend support with no spawned elites.
5. **First elite PR** — one modifier mechanic, deterministic assignment, tests, flag on for internal builds only.
6. **Perk model PR** — definitions, offer generator, persistence, tests.
7. **Perk effects/UI PRs** — one or two perks per PR, then HUD summaries and balance tuning.

Keep each PR reversible: flags off should restore current Adventure behavior, and schema bumps should intentionally clear incompatible in-progress runs rather than half-restoring stale state.
