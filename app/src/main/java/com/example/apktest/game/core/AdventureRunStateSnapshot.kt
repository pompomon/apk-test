package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure-data, serialisable snapshot of an [AdventureRunState], plus the
 * run seed. Round-tripped through [toJson] / [fromJson] for persistence
 * via [com.example.apktest.AdventureStateStore].
 *
 * Mirrors the conventions of [GameEngineSnapshot]: bumping
 * [SCHEMA_VERSION] in code transparently invalidates any stale payload
 * via the version check in [fromJson], which returns `null` for any
 * unreadable / out-of-range payload (Hard rule #9). Unknown enum values
 * in the unlocked-policies list or currentPlayerPolicy are tolerated:
 * removed entries are silently dropped, and an unreadable
 * currentPlayerPolicy falls back to MANUAL rather than failing the load.
 */
data class AdventureRunStateSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val runSeed: Long,
    val difficultyName: String,
    val currentMazeIndex: Int,
    val livesRemaining: Int,
    val winStreakSinceLastBonus: Int,
    val unlockedPlayerPolicies: List<PlayerPolicyType>,
    val currentPlayerPolicy: PlayerPolicyType,
    val currentMazeSeed: Long?,
    val currentMazeNpcPolicies: List<NpcPolicyType>,
    val currentMazeSnapshot: GameEngineSnapshot?,
    val status: AdventureStatus,
    val lastAutomatedPlayerPolicy: PlayerPolicyType? = null,
    val automatedPolicyPromptShown: Boolean = false,
    val pendingStartingPowerUp: PowerUpType? = null,
    val totalElapsedSeconds: Float = 0f,
    val totalSteps: Int = 0,
    val deathsThisRun: Int = 0
) {
    fun toJson(): String = JSONObject().apply {
        put(KEY_VERSION, schemaVersion)
        put(KEY_RUN_SEED, runSeed)
        put(KEY_DIFFICULTY, difficultyName)
        put(KEY_MAZE_INDEX, currentMazeIndex)
        put(KEY_LIVES, livesRemaining)
        put(KEY_STREAK, winStreakSinceLastBonus)
        put(KEY_UNLOCKED, JSONArray().apply { unlockedPlayerPolicies.forEach { put(it.name) } })
        put(KEY_CURRENT_POLICY, currentPlayerPolicy.name)
        if (lastAutomatedPlayerPolicy != null) {
            put(KEY_LAST_AUTO_POLICY, lastAutomatedPlayerPolicy.name)
        }
        put(KEY_AUTO_PROMPT_SHOWN, automatedPolicyPromptShown)
        if (currentMazeSeed != null) put(KEY_MAZE_SEED, currentMazeSeed)
        put(KEY_MAZE_NPC_POLICIES, JSONArray().apply {
            currentMazeNpcPolicies.forEach { put(it.name) }
        })
        if (currentMazeSnapshot != null) {
            // Embed the engine snapshot's JSON as a string so its own
            // schema version is preserved verbatim. Parsing on the way
            // back goes through GameEngineSnapshot.fromJson, which
            // does its own version + bounds validation.
            put(KEY_MAZE_SNAPSHOT, currentMazeSnapshot.toJson())
        }
        put(KEY_STATUS, status.name)
        if (pendingStartingPowerUp != null) {
            put(KEY_PENDING_POWERUP, pendingStartingPowerUp.name)
        }
        put(KEY_TOTAL_ELAPSED_SECONDS, totalElapsedSeconds.toDouble())
        put(KEY_TOTAL_STEPS, totalSteps)
        put(KEY_DEATHS_THIS_RUN, deathsThisRun)
    }.toString()

    companion object {
        // Intentionally kept at v1. The newer fields — the automation
        // fields (lastAutomatedPlayerPolicy, automatedPolicyPromptShown)
        // and the per-maze starting power-up state (pendingStartingPowerUp)
        // — are additive and backward-compatible:
        // older payloads simply omit them and fromJson falls back to the
        // defaults, while older builds ignore the unknown keys. Bumping this
        // version would invalidate every existing in-progress run via the
        // exact-match check in fromJson, so only bump it for a breaking
        // change that genuinely cannot be read by the previous schema.
        // v2: adds totalElapsedSeconds, totalSteps, deathsThisRun. This bump
        // is not strictly required for readability — v1 payloads simply omit
        // these keys and fromJson would default them to 0. It is an
        // intentional breaking bump: invalidating in-progress v1 runs is
        // preferred over silently resuming them with zeroed run stats, which
        // would misrepresent already-earned time/steps/death counts.
        const val SCHEMA_VERSION = 2

        private const val KEY_VERSION = "v"
        private const val KEY_RUN_SEED = "runSeed"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_MAZE_INDEX = "mazeIndex"
        private const val KEY_LIVES = "lives"
        private const val KEY_STREAK = "streak"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_CURRENT_POLICY = "currentPolicy"
        private const val KEY_LAST_AUTO_POLICY = "lastAutoPolicy"
        private const val KEY_AUTO_PROMPT_SHOWN = "autoPromptShown"
        private const val KEY_MAZE_SEED = "mazeSeed"
        private const val KEY_MAZE_NPC_POLICIES = "mazeNpcPolicies"
        private const val KEY_MAZE_SNAPSHOT = "mazeSnapshot"
        private const val KEY_STATUS = "status"
        private const val KEY_PENDING_POWERUP = "pendingPowerUp"
        private const val KEY_TOTAL_ELAPSED_SECONDS = "totalElapsedSeconds"
        private const val KEY_TOTAL_STEPS = "totalSteps"
        private const val KEY_DEATHS_THIS_RUN = "deathsThisRun"

        fun fromState(state: AdventureRunState, runSeed: Long): AdventureRunStateSnapshot =
            AdventureRunStateSnapshot(
                runSeed = runSeed,
                difficultyName = state.difficultyName,
                currentMazeIndex = state.currentMazeIndex,
                livesRemaining = state.livesRemaining,
                winStreakSinceLastBonus = state.winStreakSinceLastBonus,
                unlockedPlayerPolicies = state.unlockedPlayerPolicies.toList(),
                currentPlayerPolicy = state.currentPlayerPolicy,
                lastAutomatedPlayerPolicy = state.lastAutomatedPlayerPolicy,
                automatedPolicyPromptShown = state.automatedPolicyPromptShown,
                currentMazeSeed = state.currentMazeSeed,
                currentMazeNpcPolicies = state.currentMazeNpcPolicies,
                currentMazeSnapshot = state.currentMazeSnapshot,
                status = state.status,
                pendingStartingPowerUp = state.pendingStartingPowerUp,
                totalElapsedSeconds = state.totalElapsedSeconds,
                totalSteps = state.totalSteps,
                deathsThisRun = state.deathsThisRun
            )

        fun fromJson(json: String): AdventureRunStateSnapshot? {
            return try {
                val obj = JSONObject(json)
                val version = obj.optInt(KEY_VERSION, 0)
                if (version != SCHEMA_VERSION) return null
                // Silently drop unknown enum names from the unlocked-policies
                // list so legacy payloads that referenced now-removed entries
                // (e.g. RANDOM_MEMORY, WALL_RIGHT) still load instead of
                // discarding the entire run. We re-add MANUAL below to keep
                // the invariant that MANUAL is always available.
                val unlocked = obj.getJSONArray(KEY_UNLOCKED).let { arr ->
                    (0 until arr.length()).mapNotNull { i ->
                        runCatching { PlayerPolicyType.valueOf(arr.getString(i)) }.getOrNull()
                    }
                }.toMutableList()
                if (PlayerPolicyType.MANUAL !in unlocked) unlocked.add(0, PlayerPolicyType.MANUAL)
                val distinctUnlocked = unlocked.distinct()
                // Tolerate a removed currentPlayerPolicy by resetting to
                // MANUAL (always unlocked) rather than failing the load.
                val currentPolicy = runCatching {
                    PlayerPolicyType.valueOf(obj.getString(KEY_CURRENT_POLICY))
                }.getOrNull()
                    ?.takeIf { it in distinctUnlocked }
                    ?: PlayerPolicyType.MANUAL
                val lastAutoPolicy = if (obj.has(KEY_LAST_AUTO_POLICY) && !obj.isNull(KEY_LAST_AUTO_POLICY)) {
                    runCatching { PlayerPolicyType.valueOf(obj.getString(KEY_LAST_AUTO_POLICY)) }.getOrNull()
                        // The remembered auto policy is only valid when it is non-MANUAL and still unlocked.
                        ?.takeIf { it != PlayerPolicyType.MANUAL && it in distinctUnlocked }
                } else null
                val mazePolicies = if (obj.has(KEY_MAZE_NPC_POLICIES)) {
                    obj.getJSONArray(KEY_MAZE_NPC_POLICIES).let { arr ->
                        List(arr.length()) { i -> NpcPolicyType.valueOf(arr.getString(i)) }
                    }
                } else emptyList()
                val mazeSeed = if (obj.has(KEY_MAZE_SEED) && !obj.isNull(KEY_MAZE_SEED)) {
                    obj.getLong(KEY_MAZE_SEED)
                } else null
                val mazeSnapshotJson = if (obj.has(KEY_MAZE_SNAPSHOT) && !obj.isNull(KEY_MAZE_SNAPSHOT)) {
                    obj.getString(KEY_MAZE_SNAPSHOT)
                } else null
                // Engine snapshot is validated by GameEngineSnapshot.fromJson;
                // if validation fails (schema bump, corruption) we treat the
                // mid-maze snapshot as absent rather than rejecting the
                // entire adventure state — the player loses paused progress
                // but their run-level progress (lives, maze index, unlocks)
                // is preserved.
                val mazeSnapshot = mazeSnapshotJson?.let { GameEngineSnapshot.fromJson(it) }
                val pendingPowerUp = if (obj.has(KEY_PENDING_POWERUP) && !obj.isNull(KEY_PENDING_POWERUP)) {
                    runCatching { PowerUpType.valueOf(obj.getString(KEY_PENDING_POWERUP)) }.getOrNull()
                } else null

                val snapshot = AdventureRunStateSnapshot(
                    schemaVersion = version,
                    runSeed = obj.getLong(KEY_RUN_SEED),
                    difficultyName = obj.getString(KEY_DIFFICULTY),
                    currentMazeIndex = obj.getInt(KEY_MAZE_INDEX),
                    livesRemaining = obj.getInt(KEY_LIVES),
                    winStreakSinceLastBonus = obj.getInt(KEY_STREAK),
                    unlockedPlayerPolicies = distinctUnlocked,
                    currentPlayerPolicy = currentPolicy,
                    lastAutomatedPlayerPolicy = lastAutoPolicy,
                    automatedPolicyPromptShown = obj.optBoolean(KEY_AUTO_PROMPT_SHOWN, false),
                    currentMazeSeed = mazeSeed,
                    currentMazeNpcPolicies = mazePolicies,
                    currentMazeSnapshot = mazeSnapshot,
                    status = AdventureStatus.valueOf(obj.getString(KEY_STATUS)),
                    pendingStartingPowerUp = pendingPowerUp,
                    totalElapsedSeconds = obj.optDouble(KEY_TOTAL_ELAPSED_SECONDS, 0.0).toFloat(),
                    totalSteps = obj.optInt(KEY_TOTAL_STEPS, 0),
                    deathsThisRun = obj.optInt(KEY_DEATHS_THIS_RUN, 0)
                )

                // Reject unknown difficulty names outright. The Adventure
                // UI only offers names from DifficultyPresets.all, and
                // [AdventureRunController] hard-requires `state.difficultyName`
                // to match its config's preset name. Accepting an unknown
                // name here would silently fall back to MEDIUM via
                // [DifficultyPresets.byName] in the host and then crash
                // the controller on construction; failing the load instead
                // lets the host gracefully start a fresh run.
                if (DifficultyPresets.all.none { it.name == snapshot.difficultyName }) return null
                if (snapshot.currentMazeIndex < 0) return null
                if (snapshot.livesRemaining < 0) return null
                if (snapshot.winStreakSinceLastBonus < 0) return null
                if (snapshot.totalElapsedSeconds < 0f || !snapshot.totalElapsedSeconds.isFinite()) return null
                if (snapshot.totalSteps < 0) return null
                if (snapshot.deathsThisRun < 0) return null
                // MANUAL invariant was enforced above by re-adding it if absent.
                snapshot
            } catch (_: Exception) {
                // Same rationale as [GameEngineSnapshot.fromJson]: swallow
                // Exception but let Errors propagate.
                null
            }
        }
    }

    /**
     * Build a fresh [AdventureRunState] from this snapshot, suitable for
     * passing into [AdventureRunController].
     */
    fun toState(): AdventureRunState = AdventureRunState(
        difficultyName = difficultyName,
        currentMazeIndex = currentMazeIndex,
        livesRemaining = livesRemaining,
        winStreakSinceLastBonus = winStreakSinceLastBonus,
        unlockedPlayerPolicies = unlockedPlayerPolicies.toMutableList(),
        currentPlayerPolicy = currentPlayerPolicy,
        lastAutomatedPlayerPolicy = lastAutomatedPlayerPolicy,
        automatedPolicyPromptShown = automatedPolicyPromptShown,
        currentMazeSeed = currentMazeSeed,
        currentMazeNpcPolicies = currentMazeNpcPolicies,
        currentMazeSnapshot = currentMazeSnapshot,
        status = status,
        pendingStartingPowerUp = pendingStartingPowerUp,
        totalElapsedSeconds = totalElapsedSeconds,
        totalSteps = totalSteps,
        deathsThisRun = deathsThisRun
    )
}
