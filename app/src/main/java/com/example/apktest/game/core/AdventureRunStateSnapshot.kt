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
 * unreadable / out-of-range / unknown-enum payload (Hard rule #9).
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
    val status: AdventureStatus
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
    }.toString()

    companion object {
        const val SCHEMA_VERSION = 1

        private const val KEY_VERSION = "v"
        private const val KEY_RUN_SEED = "runSeed"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_MAZE_INDEX = "mazeIndex"
        private const val KEY_LIVES = "lives"
        private const val KEY_STREAK = "streak"
        private const val KEY_UNLOCKED = "unlocked"
        private const val KEY_CURRENT_POLICY = "currentPolicy"
        private const val KEY_MAZE_SEED = "mazeSeed"
        private const val KEY_MAZE_NPC_POLICIES = "mazeNpcPolicies"
        private const val KEY_MAZE_SNAPSHOT = "mazeSnapshot"
        private const val KEY_STATUS = "status"

        fun fromState(state: AdventureRunState, runSeed: Long): AdventureRunStateSnapshot =
            AdventureRunStateSnapshot(
                runSeed = runSeed,
                difficultyName = state.difficultyName,
                currentMazeIndex = state.currentMazeIndex,
                livesRemaining = state.livesRemaining,
                winStreakSinceLastBonus = state.winStreakSinceLastBonus,
                unlockedPlayerPolicies = state.unlockedPlayerPolicies.toList(),
                currentPlayerPolicy = state.currentPlayerPolicy,
                currentMazeSeed = state.currentMazeSeed,
                currentMazeNpcPolicies = state.currentMazeNpcPolicies,
                currentMazeSnapshot = state.currentMazeSnapshot,
                status = state.status
            )

        fun fromJson(json: String): AdventureRunStateSnapshot? {
            return try {
                val obj = JSONObject(json)
                val version = obj.optInt(KEY_VERSION, 0)
                if (version != SCHEMA_VERSION) return null
                val unlocked = obj.getJSONArray(KEY_UNLOCKED).let { arr ->
                    List(arr.length()) { i -> PlayerPolicyType.valueOf(arr.getString(i)) }
                }
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

                val snapshot = AdventureRunStateSnapshot(
                    schemaVersion = version,
                    runSeed = obj.getLong(KEY_RUN_SEED),
                    difficultyName = obj.getString(KEY_DIFFICULTY),
                    currentMazeIndex = obj.getInt(KEY_MAZE_INDEX),
                    livesRemaining = obj.getInt(KEY_LIVES),
                    winStreakSinceLastBonus = obj.getInt(KEY_STREAK),
                    unlockedPlayerPolicies = unlocked,
                    currentPlayerPolicy = PlayerPolicyType.valueOf(obj.getString(KEY_CURRENT_POLICY)),
                    currentMazeSeed = mazeSeed,
                    currentMazeNpcPolicies = mazePolicies,
                    currentMazeSnapshot = mazeSnapshot,
                    status = AdventureStatus.valueOf(obj.getString(KEY_STATUS))
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
                if (PlayerPolicyType.MANUAL !in snapshot.unlockedPlayerPolicies) return null
                // Reject duplicates so a tampered payload like
                // `[MANUAL, MANUAL]` can't fake an extra unlocked slot
                // and trigger UI flows (e.g. the strategy switcher) that
                // assume distinct entries.
                if (snapshot.unlockedPlayerPolicies.distinct().size !=
                    snapshot.unlockedPlayerPolicies.size) return null
                if (snapshot.currentPlayerPolicy !in snapshot.unlockedPlayerPolicies) return null
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
        currentMazeSeed = currentMazeSeed,
        currentMazeNpcPolicies = currentMazeNpcPolicies,
        currentMazeSnapshot = currentMazeSnapshot,
        status = status
    )
}
