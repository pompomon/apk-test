# Agent quickstart

> First 5 minutes for any AI coding agent landing in this repository.

## 1. Read in order

1. [`AGENTS.md`](../AGENTS.md) — project shape + hard rules.
2. The section of [`docs/lessons-learned.md`](lessons-learned.md) that matches the area you are about to change. (E.g. touching `GameEngine`? → §1 Persistence and §3 Cache invalidation.)
3. [`docs/architecture.md`](architecture.md) for the map.
4. [`docs/conventions.md`](conventions.md) and [`docs/testing.md`](testing.md) for style and test patterns.

## 2. Check validation readiness and record the baseline

Before implementation, record the starting commit (`git rev-parse HEAD`) and working-tree status (`git status --short`). Confirm JDK 17 and Android SDK platform 34 / build-tools 34.0.0 are available, with the SDK discoverable via `ANDROID_HOME` or `local.properties`. Run from the repository root:

```bash
java -version
./gradlew --version
./gradlew testDebugUnitTest
```

The test command checks plugin/dependency resolution as well as the JVM baseline; `--version` alone does not. Record the command, revision, exit result, and any failing test or first blocking error.

- **Tests failed:** distinguish a pre-existing failure from the requested bug. Fix only failures in scope; report unrelated failures before layering implementation on top.
- **Configuration/dependency access blocked:** no tests have passed or failed yet. Report the unresolved artifact/repository and stop substantial implementation until validation is available or the user explicitly agrees to proceed with the limitation. Do not change plugin versions, add mirrors, or disable checks just to make the sandbox pass.
- **CI evidence:** inspect recent workflow runs and the relevant job logs, and record their revision and URL. A green run on another revision is historical evidence, not validation of the current changes.
- **Documentation-only changes:** check documentation consistency and links; Android tests/builds are not required unless documentation-specific tests require them. If a baseline is attempted, still report its actual result.

## 3. Define scope and acceptance before editing

Name the feature or exact milestone and its reference, the target revision, what is in scope, and what must remain unchanged. Specify whether the task is **implementation**, **automated validation**, **manual acceptance**, or a combination.

Track implementation, automated checks, and manual acceptance separately. Inspect existing code and evidence before interpreting an unchecked milestone as missing implementation. Mark a milestone complete only when all its required acceptance criteria have evidence; unavailable device checks remain pending, not passed.

## 4. Batch and deduplicate review follow-ups

For review-driven work, collect the current review threads for the target PR before editing. Compare each finding with the current revision, tests, and previous replies; classify it as actionable, already addressed, or needing clarification. Group related actionable findings into a focused change with shared validation.

For already-addressed findings, cite the fixing commit and evidence rather than repeating the edit. Avoid duplicate replies when the thread already contains that evidence; reply to each remaining thread with its disposition and the verified commit. Do not assume an outdated thread is fixed without checking the code.

## 5. Identify the rules and state transitions your change touches

Skim [`lessons-learned.md`](lessons-learned.md) and list the rule numbers you'll need to comply with. Common cases:

| If you're changing… | At minimum, comply with… |
| --- | --- |
| Adding a field to `GameEngine` that affects gameplay | §1.1, §1.2 (snapshot + schema version) |
| Adding a `PowerUpType` variant | §4.1 (exhaustive `when`), §11.2 (metadata) |
| Touching `MazeRenderer` | §3.1, §3.3, §3.4 (cache + no allocation) |
| Touching `MainActivity` lifecycle | §2 (autosave executor) |
| Touching `Policies.kt` | §6 (avoidance wrapper semantics, tie-breakers) |
| Adding a setup option | §13 (Intent extras + Fragment args) |
| Writing an instrumented test | §8 (popover snapshot hooks, no Espresso popup matchers) |

For lifecycle, persistence, or UI changes, add applicable state transitions to the initial acceptance criteria:

- Pause/resume and finish/destroy, including late or repeated async callbacks.
- Save failure/retry, terminal WIN/LOSE transitions, and invalid or inconsistent restored state.
- Restart/new-session behavior, including clearing stale UI state without discarding valid gameplay state.

For each relevant transition, state the expected runtime, persisted, and UI outcome. Cover pure-Kotlin invariants with JVM tests; identify Android/device checks separately rather than treating unit tests as visual or lifecycle acceptance.

## 6. Write the regression test first

If you're fixing a bug, write the failing JVM unit test before the fix (§testing-cardinal-rule). Otherwise add a unit test alongside the change that asserts the new invariant.

## 7. Run the targeted test, then the whole suite

```bash
./gradlew :app:testDebugUnitTest --tests 'com.example.apktest.your.PackageTest'
./gradlew testDebugUnitTest
```

Instrumented tests are not normally part of the agent loop unless you change Android UI code.

Record baseline and post-change results separately. Use **passed**, **failed**, **blocked**, **not run**, or **not applicable (with reason)** for each check. Compilation, APK assembly, and JVM tests do not establish that a UI/device acceptance check passed.

## 8. Leave a precise handoff

End the session with:

- **Revision:** final commit and PR link, if one exists.
- **Completed scope:** what changed and which acceptance criteria are satisfied.
- **Validation actually run:** commands, tested revisions, results, and evidence links; distinguish local results from CI evidence.
- **Remaining acceptance:** pending emulator/device/visual checks, including the build/artifact and reproduction steps needed.
- **Blockers:** exact failure or missing access; say explicitly when no tests executed.
- **Next action:** the concrete step to resume, and who needs to provide access or perform manual verification if known.

Do not present implementation as fully accepted while required checks remain pending. Keep the handoff in the final response or existing task/PR discussion, not a new tracking file.

## 9. Prepare the PR description

Use the [PR template](../.github/PULL_REQUEST_TEMPLATE.md) for an existing or explicitly requested PR. Reference each `lessons-learned.md` rule your change touches. If your change establishes a *new* rule, append it to `lessons-learned.md` in the same change (see that doc's "How to update this document" section). Create a PR only when requested.
