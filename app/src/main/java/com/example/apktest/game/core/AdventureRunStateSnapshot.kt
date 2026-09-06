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
    val currentMazeNpcSpawnSpecs: List<NpcSpawnSpec>,
    val currentMazeSnapshot: GameEngineSnapshot?,
    val status: AdventureStatus,
    val lastAutomatedPlayerPolicy: PlayerPolicyType? = null,
    val automatedPolicyPromptShown: Boolean = false,
    val pendingStartingPowerUp: PowerUpType? = null,
    val totalElapsedSeconds: Float = 0f,
    val totalSteps: Int = 0,
    val deathsThisRun: Int = 0,
    val currentMazeNpcCount: Int? = currentMazeSeed?.let { currentMazeNpcSpawnSpecs.size },
    val pendingReward: PendingAdventureReward? = null,
    val activeRoute: PendingRouteEvent? = null,
    val routeHistory: List<RouteEventHistoryEntry> = emptyList(),
    val nextRouteEventMazeIndex: Int = RouteEventGenerator.FIRST_EVENT_MAZE_INDEX,
    val routeEventOrdinal: Int = 0,
    val rewardRerolls: Int = 0
) {
    val currentMazeNpcPolicies: List<NpcPolicyType>
        get() = currentMazeNpcSpawnSpecs.map { it.policyType }

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
        put(KEY_MAZE_NPC_SPAWN_SPECS, AdventureRouteSnapshotCodec.spawnSpecsToJson(currentMazeNpcSpawnSpecs))
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
        put(KEY_MAZE_NPC_COUNT, currentMazeNpcCount ?: JSONObject.NULL)
        put(KEY_PENDING_REWARD, pendingReward?.let { AdventureRouteSnapshotCodec.rewardToJson(it) } ?: JSONObject.NULL)
        put(KEY_ACTIVE_ROUTE, activeRoute?.let { AdventureRouteSnapshotCodec.activeToJson(it) } ?: JSONObject.NULL)
        put(KEY_ROUTE_HISTORY, AdventureRouteSnapshotCodec.historyToJson(routeHistory))
        put(KEY_NEXT_ROUTE_INDEX, nextRouteEventMazeIndex)
        put(KEY_ROUTE_ORDINAL, routeEventOrdinal)
        put(KEY_REWARD_REROLLS, rewardRerolls)
    }.toString()

    companion object {
        // v4 requires locked per-NPC policies and explicit nullable elite modifier IDs.
        // Older saves cannot establish a complete modifier assignment.
        // Route mechanics/balance changes need a schema bump: resolved effects are
        // checked against the supplied configuration, not silently reinterpreted.
        const val SCHEMA_VERSION = 4

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
        private const val KEY_MAZE_NPC_SPAWN_SPECS = "mazeNpcSpawnSpecs"
        private const val KEY_MAZE_SNAPSHOT = "mazeSnapshot"
        private const val KEY_STATUS = "status"
        private const val KEY_PENDING_POWERUP = "pendingPowerUp"
        private const val KEY_TOTAL_ELAPSED_SECONDS = "totalElapsedSeconds"
        private const val KEY_TOTAL_STEPS = "totalSteps"
        private const val KEY_DEATHS_THIS_RUN = "deathsThisRun"
        private const val KEY_MAZE_NPC_COUNT = "mazeNpcCount"
        private const val KEY_PENDING_REWARD = "pendingReward"
        private const val KEY_ACTIVE_ROUTE = "activeRoute"
        private const val KEY_ROUTE_HISTORY = "routeHistory"
        private const val KEY_NEXT_ROUTE_INDEX = "nextRouteEventMazeIndex"
        private const val KEY_ROUTE_ORDINAL = "routeEventOrdinal"
        private const val KEY_REWARD_REROLLS = "rewardRerolls"

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
                currentMazeNpcSpawnSpecs = state.currentMazeNpcSpawnSpecs.toList(),
                currentMazeSnapshot = state.currentMazeSnapshot,
                status = state.status,
                pendingStartingPowerUp = state.pendingStartingPowerUp,
                totalElapsedSeconds = state.totalElapsedSeconds,
                totalSteps = state.totalSteps,
                deathsThisRun = state.deathsThisRun,
                currentMazeNpcCount = state.currentMazeNpcCount,
                pendingReward = state.pendingReward?.detachedCopy(),
                activeRoute = state.activeRoute?.detachedCopy(),
                routeHistory = state.routeHistory.toList(),
                nextRouteEventMazeIndex = state.nextRouteEventMazeIndex,
                routeEventOrdinal = state.routeEventOrdinal,
                rewardRerolls = state.rewardRerolls
            )

        fun fromJson(json: String): AdventureRunStateSnapshot? = parseJson(json, null)

        /** Custom-run callers must supply their configuration rather than impersonating a shipped preset. */
        fun fromJson(json: String, config: AdventureConfig): AdventureRunStateSnapshot? = parseJson(json, config)

        private fun parseJson(json: String, expectedConfig: AdventureConfig?): AdventureRunStateSnapshot? {
            return try {
                val obj = JSONObject(json)
                val version = obj.requiredInt(KEY_VERSION)
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
                val mazeSpawnSpecs = AdventureRouteSnapshotCodec.spawnSpecsFromJson(
                    obj.getJSONArray(KEY_MAZE_NPC_SPAWN_SPECS)
                )
                val mazeSeed = if (obj.has(KEY_MAZE_SEED) && !obj.isNull(KEY_MAZE_SEED)) {
                    obj.requiredLong(KEY_MAZE_SEED)
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
                    PowerUpType.valueOf(obj.requiredString(KEY_PENDING_POWERUP))
                } else null

                val snapshot = AdventureRunStateSnapshot(
                    schemaVersion = version,
                    runSeed = obj.requiredLong(KEY_RUN_SEED),
                    difficultyName = obj.getString(KEY_DIFFICULTY),
                    currentMazeIndex = obj.requiredInt(KEY_MAZE_INDEX),
                    livesRemaining = obj.requiredInt(KEY_LIVES),
                    winStreakSinceLastBonus = obj.requiredInt(KEY_STREAK),
                    unlockedPlayerPolicies = distinctUnlocked,
                    currentPlayerPolicy = currentPolicy,
                    lastAutomatedPlayerPolicy = lastAutoPolicy,
                    automatedPolicyPromptShown = obj.optBoolean(KEY_AUTO_PROMPT_SHOWN, false),
                    currentMazeSeed = mazeSeed,
                    currentMazeNpcSpawnSpecs = mazeSpawnSpecs,
                    currentMazeSnapshot = mazeSnapshot,
                    status = AdventureStatus.valueOf(obj.getString(KEY_STATUS)),
                    pendingStartingPowerUp = pendingPowerUp,
                    // Strict reads: a valid v2 payload always writes these
                    // keys (see toJson). Missing or wrong-typed values throw
                    // and fail the load (caller clears the blob) rather than
                    // silently resuming with zeroed run stats, which would
                    // misrepresent already-earned time/steps/death counts.
                    totalElapsedSeconds = obj.requiredFloat(KEY_TOTAL_ELAPSED_SECONDS),
                    totalSteps = obj.requiredInt(KEY_TOTAL_STEPS),
                    deathsThisRun = obj.requiredInt(KEY_DEATHS_THIS_RUN),
                    currentMazeNpcCount = obj.requiredNullableInt(KEY_MAZE_NPC_COUNT),
                    pendingReward = obj.get(KEY_PENDING_REWARD).let {
                        if (it == JSONObject.NULL) null else AdventureRouteSnapshotCodec.rewardFromJson(it as JSONObject)
                    },
                    activeRoute = obj.get(KEY_ACTIVE_ROUTE).let {
                        if (it == JSONObject.NULL) null else AdventureRouteSnapshotCodec.activeFromJson(it as JSONObject)
                    },
                    routeHistory = AdventureRouteSnapshotCodec.historyFromJson(obj.getJSONArray(KEY_ROUTE_HISTORY)),
                    nextRouteEventMazeIndex = obj.requiredInt(KEY_NEXT_ROUTE_INDEX),
                    routeEventOrdinal = obj.requiredInt(KEY_ROUTE_ORDINAL),
                    rewardRerolls = obj.requiredInt(KEY_REWARD_REROLLS)
                )

                // Reject unknown difficulty names outright. The Adventure
                // UI only offers names from DifficultyPresets.all, and
                // [AdventureRunController] hard-requires `state.difficultyName`
                // to match its config's preset name. Accepting an unknown
                // name here would silently fall back to MEDIUM via
                // [DifficultyPresets.byName] in the host and then crash
                // the controller on construction; failing the load instead
                // lets the host gracefully start a fresh run.
                if (expectedConfig != null) {
                    if (expectedConfig.difficulty.name != snapshot.difficultyName) return null
                } else if (DifficultyPresets.all.none { it.name == snapshot.difficultyName }) return null
                if (snapshot.currentMazeIndex < 0) return null
                if (snapshot.livesRemaining < 0) return null
                if (snapshot.winStreakSinceLastBonus < 0) return null
                if (snapshot.totalElapsedSeconds < 0f || !snapshot.totalElapsedSeconds.isFinite()) return null
                if (snapshot.totalSteps < 0) return null
                if (snapshot.deathsThisRun < 0) return null
                val config = expectedConfig ?: AdventureConfig.forDifficultyName(snapshot.difficultyName)
                if (!AdventureRouteSnapshotCodec.isConsistent(snapshot, config)) return null
                if (mazeSnapshotJson != null && snapshot.pendingReward == null &&
                    snapshot.status == AdventureStatus.IN_PROGRESS && snapshot.currentMazeSeed == null) return null
                // MANUAL invariant was enforced above by re-adding it if absent.
                if (mazeSnapshot != null && !snapshot.matchesLockedMaze(mazeSnapshot)) {
                    snapshot.copy(currentMazeSnapshot = null)
                } else snapshot
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
        currentMazeNpcSpawnSpecs = currentMazeNpcSpawnSpecs.toList(),
        currentMazeSnapshot = currentMazeSnapshot,
        status = status,
        pendingStartingPowerUp = pendingStartingPowerUp,
        totalElapsedSeconds = totalElapsedSeconds,
        totalSteps = totalSteps,
        deathsThisRun = deathsThisRun,
        currentMazeNpcCount = currentMazeNpcCount,
        pendingReward = pendingReward?.detachedCopy(),
        activeRoute = activeRoute?.detachedCopy(),
        routeHistory = routeHistory.toList(),
        nextRouteEventMazeIndex = nextRouteEventMazeIndex,
        routeEventOrdinal = routeEventOrdinal,
        rewardRerolls = rewardRerolls
    )

    private fun matchesLockedMaze(engine: GameEngineSnapshot): Boolean =
        status == AdventureStatus.IN_PROGRESS && pendingReward == null &&
            engine.matchesAdventureMaze(difficultyName, currentMazeSeed, currentMazeNpcCount,
                currentMazeNpcSpawnSpecs, activeRoute?.pickupLifetimeSeconds)
}

internal fun GameEngineSnapshot.matchesAdventureMaze(
    difficulty: String,
    mazeSeed: Long?,
    npcCount: Int?,
    spawnSpecs: List<NpcSpawnSpec>,
    lifetime: Float?
): Boolean =
    mazeSeed != null && npcCount != null && (status == GameStatus.RUNNING || status == GameStatus.PAUSED) &&
        difficultyName == difficulty && seed == mazeSeed && npcCountOverride == npcCount &&
        npcCount == spawnSpecs.size && powerUpPickupLifetimeOverrideSeconds == lifetime &&
        npcSpawnSpecs == spawnSpecs && npcPolicies.size == npcs.size &&
        npcs.map { it.id }.distinct().size == npcs.size &&
        npcs.indices.all { index ->
            val npc = npcs[index]
            val expected = spawnSpecs.getOrNull(npc.id)
            expected != null && npcPolicies.getOrNull(npc.id) == expected.policyType &&
                npc.eliteModifier == expected.eliteModifier
        }
