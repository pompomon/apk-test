<!--
Thanks for contributing! Please skim docs/lessons-learned.md and tick the
applicable boxes below. Unticked items are fine if the rule doesn't apply
to your change.
-->

## Summary

<!-- One paragraph: what this PR does and why. -->

## Lessons-learned compliance

If your change touches an area below, confirm the corresponding rule. Cite
`docs/lessons-learned.md` rule numbers in your description (e.g. "see §1.1, §3.4").

- [ ] **Persistence / snapshot (§1)** — new gameplay state on `GameEngine` is added to `GameEngineSnapshot` (constructor + `toJson` + `fromJson` + `snapshot()` + `restore()`), and `SCHEMA_VERSION` is bumped. `GameEngineSnapshotSchemaCoverageTest` still passes.
- [ ] **Threading / lifecycle (§2)** — autosave path reuses the `MainActivity` executor; async callbacks are guarded against `RejectedExecutionException`; WIN/LOSE clears rather than saves.
- [ ] **Caching / rendering (§3)** — no per-frame allocation; `MazeRenderer` cache keyed on `Maze` identity *and* `Maze.revision`; renderers use `spawnedPowerUpsView` and precomputed `PowerUpIcons` maps.
- [ ] **Enums (§4)** — new enum variants handled in every `when`; no `else ->` to silence the warning; named constants used instead of magic numbers.
- [ ] **Determinism / RNG (§5)** — all RNG derived from `currentSeed`; independent streams via `xor` constants; tie-breakers are total orderings (risk → distance → ordinal → position → direction).
- [ ] **Policies (§6)** — `AvoidanceWrapperPolicy` pass-through branches preserve inner `nextMove`; deadly/risky targets pre-filtered before ranking.
- [ ] **Touch input (§7)** — libGDX surface interception lives in `MainActivity.dispatchTouchEvent` and never consumes the event.
- [ ] **Instrumented tests (§8)** — overlay assertions use `@VisibleForTesting` snapshot hooks, not Espresso `isPlatformPopup` / focus-root matchers.
- [ ] **Maze generation (§9)** — width/height rounded up to even; corridors 2 cells wide; exit placed pre-expansion.
- [ ] **Setup plumbing (§13)** — new setup options forwarded through both Intent extras and Fragment args.
- [ ] **Tests** — added a regression unit test that fails on the old code and passes on the fix. Asserts an invariant, not a statistical property.
- [ ] **Lessons-learned doc updated** if this PR establishes a new convention or surfaces a new recurring mistake (`docs/lessons-learned.md` → "How to update this document").

## Verification

```bash
./gradlew testDebugUnitTest
# (and ./gradlew connectedDebugAndroidTest if you touched Android UI)
```
