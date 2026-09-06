package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure-data snapshot of a [GameEngine]'s observable state, sufficient to
 * resume the game later. The maze layout is not stored verbatim because
 * [MazeGenerator] is deterministic given the seed (`difficultyName` plus
 * [seed] reproduce the same baseline maze on restore); only walls that
 * gameplay has permanently mutated (e.g., removed by a BLAST power-up)
 * are persisted in [removedWalls] and re-applied on restore so a resumed
 * game preserves any mid-run wall destruction. Per-frame transient
 * values such as accumulators and RNG state are intentionally omitted:
 * the engine is restored to a "ready to step" state and ticks resume
 * normally from there. This is acceptable for a save/resume feature and
 * keeps the persisted payload small and stable across engine refactors.
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
    val adventurers: List<AdventurerSnapshot> = emptyList(),
    /** Timed effects owned by individual surviving Adventurers. */
    val adventurerEffects: List<AdventurerEffectsSnapshot> = emptyList(),
    val spawnedPowerUps: List<SpawnedPowerUpSnapshot>,
    val activeEffects: List<ActiveEffectSnapshot>,
    /** Remaining seconds on an NPC-induced player freeze, or `null` if none. */
    val npcInducedPlayerFreezeRemainingSeconds: Float?,
    val manualQueue: List<Direction>,
    val manualOverrideRemainingSeconds: Float,
    /**
     * Walls that have been removed by gameplay (e.g., BLAST power-up)
     * relative to the baseline maze that [MazeGenerator] produces for
     * [seed]. Restored mazes re-apply these edges so wall mutations
     * survive a save/resume round-trip. Each entry is canonical (a wall
     * shared between two cells is recorded only once).
     */
    val removedWalls: List<RemovedWallSnapshot> = emptyList(),
    /**
     * Adventure-mode override of [DifficultyPreset.npcCount]. `null` for
     * single-maze runs (engine spawns `difficulty.npcCount` NPCs). When
     * non-null, restored engines re-install this override so a subsequent
     * `restart(seed)` re-spawns with the same NPC count.
     */
    val npcCountOverride: Int? = null,
    /**
     * Per-NPC `NpcPolicyType` keyed by spawn index ([com.example.apktest.game.core.Npc.id]).
     * Always populated by `GameEngine.snapshot()` with one entry per spawned
     * NPC: in single-maze runs every entry is the engine's configured uniform
     * `npcPolicyType` (uniform list), and in Adventure mode each entry is the
     * NPC's individually-assigned type. On restore, each NPC is re-assigned
     * its individual policy. Restart assignments live separately in
     * [npcSpawnSpecs], which can exceed the active roster's maze capacity.
     * An empty list is valid when zero
     * NPCs are spawned (e.g. `npcCountOverride = 0`, or no available spawn
     * candidates); otherwise this list has exactly one entry per spawned
     * NPC. Older schema versions that pre-date this field are rejected by
     * `fromJson`.
     */
    val npcPolicies: List<NpcPolicyType> = emptyList(),
    /** Full restart override by spawn id; explicit null preserves Classic policy selection. */
    val npcSpawnSpecs: List<NpcSpawnSpec>? = null,
    /** Finite-positive per-maze pickup lifetime; `null` uses the difficulty preset. */
    val powerUpPickupLifetimeOverrideSeconds: Float? = null,
    val runPerkEffects: RunPerkEffects = RunPerkEffects(),
    val pendingConsumedRunPerk: RunPerkId? = null
) {
    data class PlayerSnapshot(val x: Int, val y: Int, val facing: Direction)
    data class NpcSnapshot(
        val id: Int,
        val x: Int,
        val y: Int,
        val facing: Direction,
        val eliteModifier: EliteNpcModifier? = null
    )
    data class AdventurerSnapshot(val id: Int, val x: Int, val y: Int, val facing: Direction)
    data class AdventurerEffectsSnapshot(
        val adventurerId: Int,
        val effects: List<ActiveEffectSnapshot>
    )
    data class RemovedWallSnapshot(val x: Int, val y: Int, val direction: Direction)
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
     * spawned power-ups, and removed-wall cells) is inside the maze
     * bounds implied by [preset]. Used to reject corrupted or mismatched
     * snapshots before they can crash the engine via out-of-bounds
     * [Maze.hasWall] / [Maze.removeWall] calls.
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
        if (adventurers.any { !ok(it.x, it.y) }) return false
        if (spawnedPowerUps.any { !ok(it.x, it.y) }) return false
        if (removedWalls.any { !ok(it.x, it.y) }) return false
        return true
    }

    private fun roundUpToEven(value: Int): Int = if (value % 2 == 0) value else value + 1

    /** Shared by disk decoding and programmatic restore before engine state is changed. */
    internal fun hasValidNpcConfiguration(): Boolean {
        if ((npcCountOverride == null) != (npcSpawnSpecs == null)) return false
        if (npcSpawnSpecs == null && npcs.any { it.eliteModifier != null }) return false
        if (npcCountOverride != null && npcCountOverride < npcs.size) return false
        if (npcPolicies.size != npcs.size) return false
        val ids = npcs.map { it.id }
        if (ids.toSet().size != ids.size || ids.any { it !in npcs.indices }) return false
        if (npcs.any { it.eliteModifier?.supports(npcPolicies[it.id]) == false }) return false
        npcSpawnSpecs?.let { specs ->
            if (npcCountOverride == null || specs.size != npcCountOverride) return false
            if (specs.any { it.eliteModifier?.supports(it.policyType) == false }) return false
            if (npcs.any {
                    specs[it.id].policyType != npcPolicies[it.id] ||
                        specs[it.id].eliteModifier != it.eliteModifier
                }
            ) return false
        }
        return true
    }

    internal fun hasValidRunPerkConfiguration(): Boolean {
        if (runPerkEffects.quickFeetStacks !in 0..3 ||
            runPerkEffects.longerChargeStacks !in 0..3 ||
            runPerkEffects.pocketMagnetStacks !in 0..2
        ) return false
        if (pendingConsumedRunPerk == null) return true
        if (pendingConsumedRunPerk != RunPerkId.SECOND_WIND ||
            runPerkEffects.secondWindAvailable || status != GameStatus.RUNNING ||
            !elapsedSeconds.isFinite() || elapsedSeconds < 0f
        ) return false
        val pulse = activeEffects.filter { it.type == PowerUpType.FREEZE }
        return pulse.size == 1 && pulse.single().remainingSeconds == 1f
    }

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
                    put(KEY_ELITE_MODIFIER, n.eliteModifier?.id ?: JSONObject.NULL)
                })
            }
        })
        put(KEY_ADVENTURERS, JSONArray().apply {
            adventurers.forEach { adventurer ->
                put(JSONObject().apply {
                    put("id", adventurer.id)
                    put("x", adventurer.x)
                    put("y", adventurer.y)
                    put("facing", adventurer.facing.name)
                })
            }
        })
        put(KEY_ADVENTURER_EFFECTS, JSONArray().apply {
            adventurerEffects.forEach { owner ->
                put(JSONObject().apply {
                    put("id", owner.adventurerId)
                    put("effects", JSONArray().apply {
                        owner.effects.forEach { effect ->
                            put(JSONObject().apply {
                                put("type", effect.type.name)
                                if (effect.remainingSeconds != null) put("rem", effect.remainingSeconds.toDouble())
                            })
                        }
                    })
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
        put(KEY_REMOVED_WALLS, JSONArray().apply {
            removedWalls.forEach { w ->
                put(JSONObject().apply {
                    put("x", w.x); put("y", w.y); put("d", w.direction.name)
                })
            }
        })
        if (npcCountOverride != null) {
            put(KEY_NPC_COUNT_OVERRIDE, npcCountOverride)
        }
        put(KEY_NPC_POLICIES, JSONArray().apply {
            npcPolicies.forEach { put(it.name) }
        })
        put(KEY_NPC_SPAWN_SPECS, npcSpawnSpecs?.let { specs ->
            JSONArray().apply {
                specs.forEach { spec ->
                    put(JSONObject().apply {
                        put("policyType", spec.policyType.name)
                        put(KEY_ELITE_MODIFIER, spec.eliteModifier?.id ?: JSONObject.NULL)
                    })
                }
            }
        } ?: JSONObject.NULL)
        if (powerUpPickupLifetimeOverrideSeconds != null) {
            put(KEY_PICKUP_LIFETIME_OVERRIDE, powerUpPickupLifetimeOverrideSeconds.toDouble())
        }
        put(KEY_RUN_PERK_EFFECTS, JSONObject().apply {
            put("quickFeetStacks", runPerkEffects.quickFeetStacks)
            put("longerChargeStacks", runPerkEffects.longerChargeStacks)
            put("pocketMagnetStacks", runPerkEffects.pocketMagnetStacks)
            put("secondWindAvailable", runPerkEffects.secondWindAvailable)
        })
        put(KEY_PENDING_CONSUMED_PERK, pendingConsumedRunPerk?.id ?: JSONObject.NULL)
    }.toString()

    companion object {
        const val SCHEMA_VERSION = 8

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
        private const val KEY_ADVENTURERS = "adventurers"
        private const val KEY_ADVENTURER_EFFECTS = "adventurerEffects"
        private const val KEY_POWERUPS = "powerups"
        private const val KEY_EFFECTS = "effects"
        private const val KEY_NPC_FREEZE = "npcFreezeRem"
        private const val KEY_MANUAL_QUEUE = "manualQueue"
        private const val KEY_MANUAL_OVERRIDE = "manualOverrideRem"
        private const val KEY_REMOVED_WALLS = "removedWalls"
        private const val KEY_NPC_COUNT_OVERRIDE = "npcCountOverride"
        private const val KEY_NPC_POLICIES = "npcPolicies"
        private const val KEY_NPC_SPAWN_SPECS = "npcSpawnSpecs"
        private const val KEY_ELITE_MODIFIER = "eliteModifier"
        private const val KEY_PICKUP_LIFETIME_OVERRIDE = "powerUpPickupLifetimeOverrideSeconds"
        private const val KEY_RUN_PERK_EFFECTS = "runPerkEffects"
        private const val KEY_PENDING_CONSUMED_PERK = "pendingConsumedRunPerk"

        private fun readRunPerkEffects(obj: JSONObject): RunPerkEffects {
            fun stacks(key: String, max: Int): Int {
                val value = obj.get(key)
                require(value is Int || value is Long) { "Invalid perk stack count" }
                val count = (value as Number).toLong()
                require(count in 0L..max.toLong()) { "Perk stack count out of bounds" }
                return count.toInt()
            }
            require(obj.length() == 4) { "Unexpected run perk effects fields" }
            return RunPerkEffects(
                quickFeetStacks = stacks("quickFeetStacks", 3),
                longerChargeStacks = stacks("longerChargeStacks", 3),
                pocketMagnetStacks = stacks("pocketMagnetStacks", 2),
                secondWindAvailable = obj.get("secondWindAvailable") as? Boolean
                    ?: throw IllegalArgumentException("Invalid Second Wind availability")
            )
        }

        private fun readEliteModifier(obj: JSONObject): EliteNpcModifier? {
            require(obj.has(KEY_ELITE_MODIFIER)) { "Missing elite modifier" }
            if (obj.isNull(KEY_ELITE_MODIFIER)) return null
            val id = obj.get(KEY_ELITE_MODIFIER) as? String
                ?: throw IllegalArgumentException("Invalid elite modifier")
            return EliteNpcModifier.fromId(id)
                ?: throw IllegalArgumentException("Unknown elite modifier")
        }

        fun fromJson(json: String): GameEngineSnapshot? {
            return try {
                val obj = JSONObject(json)
                val version = obj.opt(KEY_VERSION) as? Int ?: return null
                if (version != SCHEMA_VERSION) return null
                val runPerkEffects = readRunPerkEffects(obj.getJSONObject(KEY_RUN_PERK_EFFECTS))
                if (!obj.has(KEY_PENDING_CONSUMED_PERK)) return null
                val pendingConsumedRunPerk = if (obj.isNull(KEY_PENDING_CONSUMED_PERK)) null else {
                    val id = obj.get(KEY_PENDING_CONSUMED_PERK) as? String ?: return null
                    RunPerkId.entries.firstOrNull { it.id == id } ?: return null
                }
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
                            facing = Direction.valueOf(n.getString("facing")),
                            eliteModifier = readEliteModifier(n)
                        )
                    }
                }
                val adventurers = obj.getJSONArray(KEY_ADVENTURERS).let { arr ->
                    List(arr.length()) { i ->
                        val adventurer = arr.getJSONObject(i)
                        AdventurerSnapshot(
                            id = adventurer.getInt("id"),
                            x = adventurer.getInt("x"),
                            y = adventurer.getInt("y"),
                            facing = Direction.valueOf(adventurer.getString("facing"))
                        )
                    }
                }
                val adventurerEffects = obj.getJSONArray(KEY_ADVENTURER_EFFECTS).let { arr ->
                    List(arr.length()) { i ->
                        val owner = arr.getJSONObject(i)
                        AdventurerEffectsSnapshot(
                            adventurerId = owner.getInt("id"),
                            effects = owner.getJSONArray("effects").let { effects ->
                                List(effects.length()) { j ->
                                    val effect = effects.getJSONObject(j)
                                    ActiveEffectSnapshot(
                                        type = PowerUpType.valueOf(effect.getString("type")),
                                        remainingSeconds = if (effect.has("rem")) {
                                            effect.getDouble("rem").toFloat()
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
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
                val removedWalls = if (obj.has(KEY_REMOVED_WALLS)) {
                    obj.getJSONArray(KEY_REMOVED_WALLS).let { arr ->
                        List(arr.length()) { i ->
                            val w = arr.getJSONObject(i)
                            RemovedWallSnapshot(
                                x = w.getInt("x"),
                                y = w.getInt("y"),
                                direction = Direction.valueOf(w.getString("d"))
                            )
                        }
                    }
                } else emptyList()
                val npcCountOverride = if (obj.has(KEY_NPC_COUNT_OVERRIDE)) {
                    obj.getInt(KEY_NPC_COUNT_OVERRIDE)
                } else null
                val npcPolicies = if (obj.has(KEY_NPC_POLICIES)) {
                    obj.getJSONArray(KEY_NPC_POLICIES).let { arr ->
                        List(arr.length()) { i -> NpcPolicyType.valueOf(arr.getString(i)) }
                    }
                } else emptyList()
                if (!obj.has(KEY_NPC_SPAWN_SPECS)) return null
                val npcSpawnSpecs = if (obj.isNull(KEY_NPC_SPAWN_SPECS)) null else {
                    obj.getJSONArray(KEY_NPC_SPAWN_SPECS).let { arr ->
                        List(arr.length()) { i ->
                            val spec = arr.getJSONObject(i)
                            NpcSpawnSpec(
                                policyType = NpcPolicyType.valueOf(spec.getString("policyType")),
                                eliteModifier = readEliteModifier(spec)
                            )
                        }
                    }
                }
                val pickupLifetimeOverride = if (
                    !obj.has(KEY_PICKUP_LIFETIME_OVERRIDE) || obj.isNull(KEY_PICKUP_LIFETIME_OVERRIDE)
                ) {
                    null
                } else {
                    val value = obj.get(KEY_PICKUP_LIFETIME_OVERRIDE) as? Number ?: return null
                    value.toFloat().also {
                        if (!it.isFinite() || it <= 0f) return null
                    }
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
                    adventurers = adventurers,
                    adventurerEffects = adventurerEffects,
                    spawnedPowerUps = powerUps,
                    activeEffects = effects,
                    npcInducedPlayerFreezeRemainingSeconds = if (obj.has(KEY_NPC_FREEZE)) {
                        obj.getDouble(KEY_NPC_FREEZE).toFloat()
                    } else null,
                    manualQueue = manualQueue,
                    manualOverrideRemainingSeconds = obj.optDouble(KEY_MANUAL_OVERRIDE, 0.0).toFloat(),
                    removedWalls = removedWalls,
                    npcCountOverride = npcCountOverride,
                    npcPolicies = npcPolicies,
                    npcSpawnSpecs = npcSpawnSpecs,
                    powerUpPickupLifetimeOverrideSeconds = pickupLifetimeOverride,
                    runPerkEffects = runPerkEffects,
                    pendingConsumedRunPerk = pendingConsumedRunPerk
                )
                val preset = snapshot.resolvePreset() ?: return null
                if (!snapshot.hasValidNpcConfiguration()) return null
                if (!snapshot.hasValidRunPerkConfiguration()) return null
                val adventurerIds = adventurers.map { it.id }
                if (adventurerIds.toSet().size != adventurerIds.size) return null
                if (adventurerIds.any { it !in 0 until preset.adventurerCount }) return null
                if (adventurerEffects.map { it.adventurerId }.toSet().size != adventurerEffects.size) return null
                if (adventurerEffects.any { it.adventurerId !in adventurerIds }) return null
                // Reject snapshots whose difficulty name doesn't match a
                // known preset exactly (DifficultyPresets.byName silently
                // falls back to MEDIUM, which would regenerate a
                // differently-sized maze on restore) and snapshots whose
                // persisted coordinates fall outside the preset's maze
                // bounds (e.g., corrupted blob) — installing either would
                // later crash the engine via out-of-bounds Maze.hasWall
                // calls.
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
