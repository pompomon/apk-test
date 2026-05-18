package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure-data snapshot of a [GameEngine]'s observable state, sufficient to
 * resume the game later. The maze itself is not stored because
 * [MazeGenerator] is deterministic given the seed (`difficultyName` plus
 * [seed] reproduce the same maze on restore). Per-frame transient values
 * such as accumulators and RNG state are intentionally omitted: the engine
 * is restored to a "ready to step" state and ticks resume normally from
 * there. This is acceptable for a save/resume feature and keeps the
 * persisted payload small and stable across engine refactors.
 */
data class GameEngineSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val difficultyName: String,
    val playerPolicy: PlayerPolicyType,
    val npcPolicy: NpcPolicyType,
    val seed: Long,
    val status: GameStatus,
    val elapsedSeconds: Float,
    val steps: Int,
    val player: PlayerSnapshot,
    val npcs: List<NpcSnapshot>,
    val spawnedPowerUps: List<SpawnedPowerUpSnapshot>,
    val activeEffects: List<ActiveEffectSnapshot>,
    /** Remaining seconds on an NPC-induced player freeze, or `null` if none. */
    val npcInducedPlayerFreezeRemainingSeconds: Float?,
    val manualQueue: List<Direction>,
    val manualOverrideRemainingSeconds: Float
) {
    data class PlayerSnapshot(val x: Int, val y: Int, val facing: Direction)
    data class NpcSnapshot(val id: Int, val x: Int, val y: Int, val facing: Direction)
    data class SpawnedPowerUpSnapshot(
        val type: PowerUpType,
        val x: Int,
        val y: Int,
        /** Seconds-from-now until expiry, or `null` for infinite. */
        val remainingSeconds: Float?
    )
    data class ActiveEffectSnapshot(
        val type: PowerUpType,
        /** Seconds-from-now until effect ends, or `null` for infinite. */
        val remainingSeconds: Float?
    )

    /**
     * Returns the [DifficultyPreset] whose [DifficultyPreset.name] exactly
     * matches [difficultyName], or `null` if the snapshot's difficulty does
     * not correspond to a known preset. Unlike
     * [DifficultyPresets.byName], this does **not** silently fall back to
     * `MEDIUM` — restoring with the wrong preset would regenerate a
     * differently-sized maze and could place persisted entities out of
     * bounds.
     */
    fun resolvePreset(): DifficultyPreset? =
        DifficultyPresets.all.firstOrNull { it.name == difficultyName }

    /**
     * Validates that every persisted grid coordinate (player, NPCs,
     * spawned power-ups) is inside the maze bounds implied by [preset].
     * Used to reject corrupted or mismatched snapshots before they can
     * crash the engine via out-of-bounds [Maze.hasWall] calls.
     *
     * The preset's [DifficultyPreset.mazeWidth]/[DifficultyPreset.mazeHeight]
     * are rounded up to the next even number to mirror
     * [MazeGenerator.generate], so a valid snapshot whose coordinates
     * fall inside the *actual* generated maze (which may be one cell
     * wider/taller than the preset for odd dimensions) is not
     * incorrectly rejected.
     */
    fun isWithinBounds(preset: DifficultyPreset): Boolean {
        val w = roundUpToEven(preset.mazeWidth)
        val h = roundUpToEven(preset.mazeHeight)
        fun ok(x: Int, y: Int): Boolean = x in 0 until w && y in 0 until h
        if (!ok(player.x, player.y)) return false
        if (npcs.any { !ok(it.x, it.y) }) return false
        if (spawnedPowerUps.any { !ok(it.x, it.y) }) return false
        return true
    }

    private fun roundUpToEven(value: Int): Int = if (value % 2 == 0) value else value + 1

    fun toJson(): String = JSONObject().apply {
        put(KEY_VERSION, schemaVersion)
        put(KEY_DIFFICULTY, difficultyName)
        put(KEY_PLAYER_POLICY, playerPolicy.name)
        put(KEY_NPC_POLICY, npcPolicy.name)
        put(KEY_SEED, seed)
        put(KEY_STATUS, status.name)
        put(KEY_ELAPSED, elapsedSeconds.toDouble())
        put(KEY_STEPS, steps)
        put(KEY_PLAYER, JSONObject().apply {
            put("x", player.x); put("y", player.y); put("facing", player.facing.name)
        })
        put(KEY_NPCS, JSONArray().apply {
            npcs.forEach { n ->
                put(JSONObject().apply {
                    put("id", n.id); put("x", n.x); put("y", n.y); put("facing", n.facing.name)
                })
            }
        })
        put(KEY_POWERUPS, JSONArray().apply {
            spawnedPowerUps.forEach { p ->
                put(JSONObject().apply {
                    put("type", p.type.name); put("x", p.x); put("y", p.y)
                    if (p.remainingSeconds != null) put("rem", p.remainingSeconds.toDouble())
                })
            }
        })
        put(KEY_EFFECTS, JSONArray().apply {
            activeEffects.forEach { e ->
                put(JSONObject().apply {
                    put("type", e.type.name)
                    if (e.remainingSeconds != null) put("rem", e.remainingSeconds.toDouble())
                })
            }
        })
        if (npcInducedPlayerFreezeRemainingSeconds != null) {
            put(KEY_NPC_FREEZE, npcInducedPlayerFreezeRemainingSeconds.toDouble())
        }
        put(KEY_MANUAL_QUEUE, JSONArray().apply {
            manualQueue.forEach { put(it.name) }
        })
        put(KEY_MANUAL_OVERRIDE, manualOverrideRemainingSeconds.toDouble())
    }.toString()

    companion object {
        const val SCHEMA_VERSION = 1

        private const val KEY_VERSION = "v"
        private const val KEY_DIFFICULTY = "difficulty"
        private const val KEY_PLAYER_POLICY = "playerPolicy"
        private const val KEY_NPC_POLICY = "npcPolicy"
        private const val KEY_SEED = "seed"
        private const val KEY_STATUS = "status"
        private const val KEY_ELAPSED = "elapsed"
        private const val KEY_STEPS = "steps"
        private const val KEY_PLAYER = "player"
        private const val KEY_NPCS = "npcs"
        private const val KEY_POWERUPS = "powerups"
        private const val KEY_EFFECTS = "effects"
        private const val KEY_NPC_FREEZE = "npcFreezeRem"
        private const val KEY_MANUAL_QUEUE = "manualQueue"
        private const val KEY_MANUAL_OVERRIDE = "manualOverrideRem"

        fun fromJson(json: String): GameEngineSnapshot? {
            return try {
                val obj = JSONObject(json)
                val version = obj.optInt(KEY_VERSION, 0)
                if (version != SCHEMA_VERSION) return null
                val player = obj.getJSONObject(KEY_PLAYER).let { p ->
                    PlayerSnapshot(
                        x = p.getInt("x"),
                        y = p.getInt("y"),
                        facing = Direction.valueOf(p.getString("facing"))
                    )
                }
                val npcs = obj.getJSONArray(KEY_NPCS).let { arr ->
                    List(arr.length()) { i ->
                        val n = arr.getJSONObject(i)
                        NpcSnapshot(
                            id = n.getInt("id"),
                            x = n.getInt("x"),
                            y = n.getInt("y"),
                            facing = Direction.valueOf(n.getString("facing"))
                        )
                    }
                }
                val powerUps = obj.getJSONArray(KEY_POWERUPS).let { arr ->
                    List(arr.length()) { i ->
                        val p = arr.getJSONObject(i)
                        SpawnedPowerUpSnapshot(
                            type = PowerUpType.valueOf(p.getString("type")),
                            x = p.getInt("x"),
                            y = p.getInt("y"),
                            remainingSeconds = if (p.has("rem")) p.getDouble("rem").toFloat() else null
                        )
                    }
                }
                val effects = obj.getJSONArray(KEY_EFFECTS).let { arr ->
                    List(arr.length()) { i ->
                        val e = arr.getJSONObject(i)
                        ActiveEffectSnapshot(
                            type = PowerUpType.valueOf(e.getString("type")),
                            remainingSeconds = if (e.has("rem")) e.getDouble("rem").toFloat() else null
                        )
                    }
                }
                val manualQueue = obj.getJSONArray(KEY_MANUAL_QUEUE).let { arr ->
                    List(arr.length()) { i -> Direction.valueOf(arr.getString(i)) }
                }
                val snapshot = GameEngineSnapshot(
                    schemaVersion = version,
                    difficultyName = obj.getString(KEY_DIFFICULTY),
                    playerPolicy = PlayerPolicyType.valueOf(obj.getString(KEY_PLAYER_POLICY)),
                    npcPolicy = NpcPolicyType.valueOf(obj.getString(KEY_NPC_POLICY)),
                    seed = obj.getLong(KEY_SEED),
                    status = GameStatus.valueOf(obj.getString(KEY_STATUS)),
                    elapsedSeconds = obj.getDouble(KEY_ELAPSED).toFloat(),
                    steps = obj.getInt(KEY_STEPS),
                    player = player,
                    npcs = npcs,
                    spawnedPowerUps = powerUps,
                    activeEffects = effects,
                    npcInducedPlayerFreezeRemainingSeconds = if (obj.has(KEY_NPC_FREEZE)) {
                        obj.getDouble(KEY_NPC_FREEZE).toFloat()
                    } else null,
                    manualQueue = manualQueue,
                    manualOverrideRemainingSeconds = obj.optDouble(KEY_MANUAL_OVERRIDE, 0.0).toFloat()
                )
                // Reject snapshots whose difficulty name doesn't match a
                // known preset exactly (DifficultyPresets.byName silently
                // falls back to MEDIUM, which would regenerate a
                // differently-sized maze on restore) and snapshots whose
                // persisted coordinates fall outside the preset's maze
                // bounds (e.g., corrupted blob) — installing either would
                // later crash the engine via out-of-bounds Maze.hasWall
                // calls.
                val preset = snapshot.resolvePreset() ?: return null
                if (!snapshot.isWithinBounds(preset)) return null
                snapshot
            } catch (_: Exception) {
                // Catch only Exception (JSON / enum / number-format / etc.)
                // so JVM Errors such as OutOfMemoryError or StackOverflowError
                // continue to propagate and remain diagnosable.
                null
            }
        }
    }
}
