# Lessons learned

Distilled from PR #1–#14 review feedback. Read the section that matches the area you're about to change. **If a rule below applies to your change, mention it in your PR description.**

Each entry follows: **Rule** — *what goes wrong* — *how to avoid it* — citation.

---

## 1. Persistence & save/resume snapshots

> Cited PRs: #14. Owner files: `app/src/main/java/com/example/apktest/game/core/GameEngineSnapshot.kt`, `GameEngine.snapshot()` / `GameEngine.restore()`, `app/src/main/java/com/example/apktest/GameStateStore.kt`, `app/src/main/java/com/example/apktest/MainActivity.kt`.

1. **Every persistable field added to `GameEngine` runtime state must be added to `GameEngineSnapshot`.** When `removedWalls` (cells whose walls had been blasted) was first added it was missing from the snapshot, so resumed games silently lost destroyed walls. Update *all* of: data class constructor, `toJson`, `fromJson`, `GameEngine.snapshot()`, `GameEngine.restore()`. The CI test `GameEngineSnapshotSchemaCoverageTest` will fail loudly if a field is missing. (PR #14)
2. **Bump `GameEngineSnapshot.SCHEMA_VERSION` on any structural change** and have `fromJson` return `null` for mismatched versions. Stale blobs from older versions then load as "no saved state" rather than restoring half-populated games. (PR #14)
3. **Don't bypass the validated `load()` path** — never call `GameStateStore.loadRawJson()` (or equivalent) to decide whether to enable a "Resume" button. The validation + self-clearing behaviour only runs through `load()`. (PR #14)
4. **`DifficultyPresets.byName` silently falls back to `MEDIUM` for unknown names.** Snapshot restore must *not* use it — use `GameEngineSnapshot.resolvePreset()` which returns `null` for unknown names, otherwise a resumed game gets a differently-sized maze and out-of-bounds entities. (PR #14)
5. **Bounds-validate every persisted coordinate** against the preset's maze size (rounded up to even, matching `MazeGenerator.generate`'s rounding). `GameEngineSnapshot.isWithinBounds(preset)` is the canonical implementation. (PR #14)
6. **Snapshot is the runtime contract, not just data.** When you persist `removedWalls`, also implement `GameEngine.computeRemovedWalls()` (diff current vs. baseline) and `restore()` (re-apply on baseline). Skipping either half makes the field cosmetic. (PR #14)
7. **Don't persist transient UI overlay state.** `MainActivity.onPause` skips writing a snapshot during the pre-game countdown, so resuming during the 3-2-1 doesn't snapshot a half-armed state. Anything that exists only for visual transitions should be re-derived on resume. (PR #14)
8. **Serialize policy/difficulty redundantly into Fragment args**, in addition to the snapshot JSON, so a process death between `onPause` and the snapshot read still recovers the user's setup choice. (PR #14)
9. **Catch `Exception`, not `Throwable`.** `GameEngineSnapshot.fromJson` swallows JSON/enum/number-format issues but lets `OutOfMemoryError` and `StackOverflowError` propagate. (PR #14)

## 2. Threading & Android lifecycle

> Cited PRs: #14. Owner file: `MainActivity.kt`.

1. **Reuse one `ExecutorService` for autosave.** Don't create a `Thread { … }.start()` per `onPause`. The current pattern is a single-thread `ExecutorService` created in `onCreate` and shut down in `onDestroy`. (PR #14)
2. **Guard `executor.execute { … }` against `RejectedExecutionException`.** The GL thread can push a snapshot job after `onDestroy` has shut the executor down; the catch-and-ignore path prevents a crash. (PR #14)
3. **Re-check `snapshot.status` inside the async callback.** A win/lose transition can race the persist. The async block must check current status and clear instead of save when appropriate. (PR #14)
4. **"Pause & Exit" clears saved state when `status` is `WIN` or `LOSE`** instead of saving. Match this for any new terminal status. (PR #14)
5. **Bounded `Future.get(timeout)` on exit paths.** When `onPause` is followed by an `Activity.finish()` cascade, an unbounded `get()` can hang the UI thread. Use a small timeout and ignore the timeout (the work has already been queued). (PR #14)

## 3. Cache invalidation & rendering performance

> Cited PRs: #4, #9. Owner files: `app/src/main/java/com/example/apktest/game/render/MazeRenderer.kt`, `PowerUpIcons.kt`, `app/src/main/java/com/example/apktest/game/core/Maze.kt`.

1. **`MazeRenderer` caches wall-brick geometry; invalidate on both `Maze` identity *and* `Maze.revision` change.** A BLAST power-up calls `Maze.removeWall` on the *same* Maze instance, so identity-only checks miss the change. (PR #9)
2. **`Maze.revision` is incremented only when `removeWall` actually changes a wall** — calls that try to remove a wall that's already absent must not increment it (otherwise the cache invalidates needlessly). (PR #9)
3. **No allocation in the render loop.** Use `GameEngine.spawnedPowerUpsView: Collection<SpawnedPowerUp>` (live `values` view) in renderers; reserve the allocating, sorted `spawnedPowerUps` property for tests. (PR #4)
4. **No `map.getValue(enum)` in render hot paths.** Pre-compute per-`PowerUpType` patterns and colours once via exhaustive `when` builders in `PowerUpIcons`, then index by ordinal. This keeps compile-time exhaustiveness while removing per-frame map lookups. (PR #4)
5. **Animation frame counters must not wrap mid-step.** `ANIMATION_FRAMES = 2` (idle + step) so the modulo arithmetic in step counters alternates cleanly. (PR #9)
6. **Renderer centers the maze** both horizontally and vertically inside the libGDX `ExtendViewport` via `mazeOriginX` / `mazeOriginY`. Don't hard-code `0, 0`. (PR #6, PR #9)

## 4. Enums, exhaustiveness, and named constants

> Cited PRs: #4, #8.

1. **Every `when` over `PowerUpType` (or any closed enum used in renderers/policies) must be exhaustive — no `else ->`.** Adding a new variant should be a compile error in every `when`, forcing the implementer to consider each render/policy site. (PR #4)
2. **Wall directions are named constants `WallTextures.DIR_NORTH / WEST / SOUTH / EAST`**, not bare `0/1/2/3`. Same principle for any numeric axis with meaning. (PR #8)

## 5. Determinism & RNG

> Cited PRs: #3, #9, #12.

1. **All RNG must be seeded from `GameEngine.currentSeed`.** No `Math.random()`, no unseeded `Random()` in game-core code. (PR #9)
2. **Use independent seeded streams for independent concerns.** `npcRandom = Random(seed xor NPC_RANDOM_SEED_MIX)` keeps NPC wander reproducible even when other systems (power-up spawning) consume more or fewer numbers from the main `random`. Add a new `*_SEED_MIX` constant when introducing a new stream. (PR #9)
3. **Tie-breakers must be total orderings.** Pickup-detour candidates rank by **risk → path distance → `PowerUpType.ordinal` → pickup position → direction**. Two runs with the same seed and inputs must produce identical decisions. (PR #12)
4. **`maze.start` is randomly chosen between two top corners using the seeded RNG.** Tests assert it's one of the corners, not which one. (PR #6)

## 6. Player policy wrappers & pathfinding

> Cited PRs: #10, #12. Owner file: `Policies.kt`.

1. **`AvoidanceWrapperPolicy` must preserve inner BFS/A\* `nextMove` semantics in pass-through branches** (no NPC threat, INVISIBILITY active, no risk). Do not substitute a ranked fallback there — it changes behaviour for the simple case. (PR #12)
2. **Pre-filter deadly/risky pickup-detour targets before ranking.** If the pickup cell itself is deadly or risky, drop it from the candidate set entirely — don't let it survive into the ranking and win on distance. (PR #12)
3. **Pickup detour activates only within Chebyshev radius `pickupRadius`.** Drawn from `PlayerPolicyContext`. (PR #12)
4. **Winning moves take precedence over avoidance.** When the exit is one step away, take it even if an NPC sits on it — avoidance must not block a win-condition move. (PR #10)
5. **NPC wander uses single-pass rejection sampling into a shared scratch array.** Don't allocate per call; reuse a member-level array. (PR #9)

## 7. Android input dispatch (libGDX SurfaceView)

> Cited PRs: #5, #7.

1. **Swipe / gesture interception lives in `MainActivity.dispatchTouchEvent`.** The libGDX `SurfaceView` consumes touches before any child-level `OnTouchListener` would fire. (PR #5)
2. **The dispatcher must never consume the event.** Hit-test, then return `super.dispatchTouchEvent(ev)` so the child views (D-pad, etc.) still receive their input. (PR #5)
3. **Synthetic `MotionEvent.obtain(...)` events get `SOURCE_UNKNOWN` and are dropped by the InputDispatcher on the emulator.** For real input use `Instrumentation.sendPointerSync` with explicit `SOURCE_TOUCHSCREEN`; for tests prefer the `@VisibleForTesting` `feedSwipeEventForTesting` bypass. Wrap synthetic-input tests in a bounded retry loop (≤5 attempts). (PR #5, PR #7)

## 8. Instrumented test stability

> Cited PRs: #5, #7, #11, #12.

1. **Open the menu via `scenario.onActivity { buttonMenu.performClick() }`**, not Espresso `click()`. Espresso clicks race against view layout on emulator startup. (PR #12)
2. **Inspect popover contents via `@VisibleForTesting` snapshot hooks** (`GameMenuPopover.snapshotForTests`, `MainActivity.menuPopoverForTesting`), not Espresso `isPlatformPopup()` / focus-root matchers. The popover-focus transition is flaky under automation. (PR #11, PR #12)
3. **Prefer integer "observable counters" (`@VisibleForTesting(NONE)`) to timing assertions.** PR #5 replaced "did the engine advance?" assertions with a `resolvedSwipeCount` counter on the UI thread.
4. **Poll for layout readiness** (`waitForGameHostLaidOut(...)`) before injecting gestures rather than `Thread.sleep`. (PR #5)
5. **`supportFragmentManager.executePendingTransactions()` before casting `findFragmentById`** to avoid the transient race after `setContentView`. (PR #5)
6. **Locale-independent string matching.** Popover assertion fixtures take a string prefix from the template, not the fully-formatted localised string. (PR #11)

## 9. Maze generation invariants

> Cited PRs: #3, #6, #7. Owner file: `MazeGenerator.kt`.

1. **Width and height are rounded up to even** because the generator builds a half-resolution maze then expands each logical cell into a 2×2 open block. Validators (`GameEngineSnapshot.isWithinBounds`) must mirror this rounding. (PR #7)
2. **Corridors are always 2 cells wide.** Don't assume 1-cell BFS distances when writing pathfinding tests — generate the maze and read distances from it. (PR #7)
3. **The exit is placed *before* the 2×2 expansion.** Don't recompute exit position after expansion or you may land on a wall. (PR #3)
4. **`maze.start` is one of the two top corners (`(0,0)` or `(w-1,0)`), chosen by the seeded RNG.** Tests assert the invariant across many seeds, not the specific corner. (PR #6)

## 10. Difficulty presets

> Cited PRs: #3, plus stored memories.

1. **Easy NPC speed = player / 4; Medium NPC speed = player / 3.** Concrete values: Easy `(4.0, 1.0)`, Medium `(4.5, 1.5)`. Easy/Medium balancing keeps escape possible. (PR #3)
2. **Easy difficulty keeps infinite power-up lifetime** (PR #13). If you add a per-difficulty timing knob, follow the existing pattern in `Difficulty.kt`.
3. **NPC-picked FREEZE freezes the player.** The freeze pickup is symmetric: whichever entity picks it up, the *other* entity is frozen. (PR #13)

## 11. Power-up lifecycle

> Cited PRs: #4, #13.

1. **Pickup despawn intervals must exceed the longest BFS test path** through the maze. A 10s despawn was shorter than a 15.5s BFS path in PR #4. Either bump the test preset to 600s for non-despawn tests, or override the interval explicitly for despawn-specific tests. (PR #4)
2. **Each `PowerUpType` declares its `kind` (TIMED / INSTANT / PERMANENT), default duration, icon, and stack policy** via `PowerUpMetadata`. New variants must populate all four. (PR #4)
3. **Active-effect timers are decoupled from spawn timers.** When you add an effect, make sure you tick the right collection (`activeEffectsByType` vs. `powerUpsByCell`). (PR #4)

## 12. UI / responsive sizing

> Cited PRs: #6, #11.

1. **All dynamic sizes (D-pad, paddings) live in `res/values/dimens.xml`** with overrides in `res/values-sw400dp/dimens.xml` for wider screens. Don't hard-code `dp` in layout XML. (PR #6)
2. **The top button strip was replaced by a hamburger menu (`GameMenuPopover`)** so the maze can fill the screen. New top-level controls should go into the popover, not a new button bar. (PR #11)

## 13. Setup → MainActivity plumbing

> Cited PRs: #2, #7.

1. **Any new setup option must be plumbed through both Intent extras and Fragment args** — `SetupActivity` uses `EXTRA_PLAYER_POLICY` / `EXTRA_NPC_POLICY` / `EXTRA_DIFFICULTY`; `MainActivity` forwards as `ARG_PLAYER_POLICY` / `ARG_NPC_POLICY` / `ARG_DIFFICULTY` to `GameFragment`. (PR #7)
2. **`SetupActivity` (not `MainActivity`) is the LAUNCHER.** Check `AndroidManifest.xml` before adding a new entry-point intent filter.

## 14. CI / build

> Cited PRs: #1.

1. **`sdkmanager` is not on `PATH` in GitHub Actions.** Invoke it as `$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager`. The existing workflow `.github/workflows/build.yml` does this. (PR #1)
2. **JVM tests need `org.json:json` because `android.jar` ships `Stub!` stubs.** Already in `app/build.gradle.kts` as `testImplementation`. Don't remove it.
3. **Instrumented tests run on API 29, x86_64, Nexus 6** via `reactivecircus/android-emulator-runner`. Most historical flakes track back to this emulator — see [`testing.md`](testing.md).

---

## How to update this document

When you find a *new* recurring mistake (you fixed the same class of bug twice, or a reviewer pointed out a non-obvious convention), append an entry here. Format:

1. **Find the right section.** Use an existing one if your rule fits; add a new section only for a genuinely new area.
2. **Add a numbered entry** in the format: **Rule (one sentence)** — *what goes wrong if you don't follow it (one sentence)* — *how to avoid it / pointer to canonical implementation* — **(PR #N)** citation.
3. **Cite the PR number** that established the rule. Multiple PRs can be cited.
4. **Keep entries to 3–6 lines.** If you need more, link out to a longer explainer file under `docs/` rather than ballooning this list.
5. **Update the "hard rules" list in [`AGENTS.md`](../AGENTS.md) and [`.github/copilot-instructions.md`](../.github/copilot-instructions.md)** only if the rule is one of the top ~10 most-broken ones. Otherwise leave those indexes terse.
6. **If your rule is mechanically enforceable, add a JVM test for it.** The model is `GameEngineSnapshotSchemaCoverageTest` — a reflection-based test that fails clearly if a future change skips the convention. Document the test in [`testing.md`](testing.md).

The goal is for each future agent iteration to start by reading this file and avoid *all* of the historical mistake categories on its first try.
