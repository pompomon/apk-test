<!--
Thanks for contributing! Please skim docs/lessons-learned.md and tick the
applicable boxes below. Explain inapplicable items as N/A; leave required
but unverified items pending rather than marking them complete.
-->

## Summary

<!-- One paragraph: what this PR does and why. -->

- **Task type:** implementation / automated validation / manual acceptance (select applicable).
- **Feature or milestone reference:**
- **Completed scope / out of scope:**
- **Definition of done:** required implementation, automated checks, and manual acceptance, tracked separately.
- **State-transition criteria:** applicable pause/resume, finish/late callbacks, save failure/retry, terminal/invalid states, and UI resets; or N/A with reason.

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
- [ ] **Agent workflow (§16)** — preflight recorded, review findings checked against the current revision, and implementation distinguished from acceptance; blockers and next action included below.
- [ ] **Lessons-learned doc updated** if this PR establishes a new convention or surfaces a new recurring mistake (`docs/lessons-learned.md` → "How to update this document").

## Verification

Record actual results, not just intended commands. Use **passed**, **failed**,
**blocked**, **not run**, or **N/A (with reason)**. For dependency/configuration
failures, state whether any tests executed. Keep baseline and post-change
evidence separate; identify CI run URLs and tested revisions.

| Check | Revision | Command / method | Result | Evidence / first blocker |
| --- | --- | --- | --- | --- |
| Environment preflight | | `java -version`, `./gradlew --version`; SDK availability | | |
| Baseline JVM tests | | `./gradlew testDebugUnitTest` | | |
| Post-change targeted / full JVM tests | | Record exact commands | | |
| APK build, if applicable | | `./gradlew assembleDebug` | | |
| Android UI instrumented tests, if applicable | | `./gradlew connectedDebugAndroidTest` | | |
| Documentation checks, if applicable | | Consistency and link checks | | |
| Manual acceptance, if required | | Device/Android version, artifact, steps | | |

Passing JVM tests or assembling an APK does not satisfy manual acceptance.
Documentation-only changes do not require Android tests/builds.

## Handoff

- **Final commit:**
- **Review follow-ups:** thread dispositions (addressed / already fixed / needs clarification), fixing commits, and evidence; avoid duplicate edits or replies.
- **Remaining acceptance / blockers:** list unchecked requirements and any missing access; do not mark the milestone complete while required acceptance is pending.
- **Next action:** concrete resume step, artifact/reproduction details, and responsible person if known.
