package com.example.apktest.game.core

import androidx.annotation.VisibleForTesting
import com.example.apktest.game.ui.HudState
import kotlin.math.abs
import kotlin.random.Random

class GameEngine(
    difficultyPreset: DifficultyPreset = DifficultyPresets.MEDIUM,
    seed: Long = System.currentTimeMillis()
) {
    var difficulty: DifficultyPreset = difficultyPreset
        private set

    var playerPolicyType: PlayerPolicyType = PlayerPolicyType.MANUAL
        private set
    var npcPolicyType: NpcPolicyType = NpcPolicyType.DIRECT_CHASE
        private set

    var status: GameStatus = GameStatus.RUNNING
        private set

    var elapsedSeconds: Float = 0f
        private set
    var steps: Int = 0
        private set

    /**
     * Seconds remaining on the pre-game countdown (3 / 2 / 1 / GO). While
     * positive, [update] consumes the value but does not advance the
     * simulation. Starts at `0f` (no countdown); callers (e.g. [MazeGame])
     * arm it via [startCountdown] after construction or after [restart].
     */
    var countdownRemainingSeconds: Float = 0f
        private set

    /**
     * Seconds remaining on the post-countdown "GO!" flash. Set to
     * [COUNTDOWN_GO_FLASH_SECONDS] on the tick where [countdownRemainingSeconds]
     * transitions from positive to zero, and decayed by subsequent
     * [update] deltas. Renderers display the "GO!" overlay while this is
     * positive (the countdown numeric value itself is already zero by then).
     */
    var goFlashRemainingSeconds: Float = 0f
        private set

    /**
     * Seconds remaining on a manual-input override. While positive (and the
     * player policy is not [PlayerPolicyType.MANUAL]), [updatePlayer]
     * consumes queued manual directions instead of consulting the active
     * automated policy. See [queueManualMove].
     */
    val manualOverrideRemainingSeconds: Float
        get() = (manualOverrideUntilSeconds - elapsedSeconds).coerceAtLeast(0f)

    /** The seed used to generate the current [maze]. Exposed so snapshots can persist it. */
    var currentSeed: Long = seed
        private set

    var maze: Maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
        private set
    var navigator: MazeNavigator = MazeNavigator(maze)
        private set
    var player: Player = Player(maze.start)
        private set
    var npcs: MutableList<Npc> = mutableListOf()
        private set

    val spawnedPowerUps: List<SpawnedPowerUp>
        get() = powerUpsByCell.values.sortedBy { it.position.y * maze.width + it.position.x }
    /** Live, unsorted view used by the render loop to avoid per-frame allocation/sorting. */
    val spawnedPowerUpsView: Collection<SpawnedPowerUp>
        get() = powerUpsByCell.values
    val activePowerUps: List<ActivePowerUpEffect>
        get() = activeEffectsByType.values.sortedBy { it.type.ordinal }
    /** Allocation-free render query for player-collected timed power-up tints. */
    fun isPlayerPowerUpTintActive(type: PowerUpType): Boolean = isEffectActive(type)
    /** Power-up tint applied to the maze background when an NPC pickup affects the player. */
    val npcMazeTintType: PowerUpType?
        get() = if (isPlayerFrozenByNpc()) PowerUpType.FREEZE else null

    private var random = Random(seed)
    // Independent RNG stream for NPC policy decisions (e.g. wander move under
    // INVISIBILITY). Derived from the same seed so behaviour stays
    // reproducible, but kept separate from `random` so power-up spawning and
    // other engine RNG consumers don't perturb the wander sequence.
    private var npcRandom = Random(seed xor NPC_RANDOM_SEED_MIX)
    private var playerPolicy: PlayerPolicy = PolicyFactory.player(playerPolicyType)
    private var npcPolicy: NpcPolicy = PolicyFactory.npc(npcPolicyType, npcRandom)
    /**
     * Per-`NpcPolicyType` cache. In single-maze mode every NPC shares
     * [npcPolicy] (= the entry at [npcPolicyType]); in Adventure mode each
     * NPC may have its own [Npc.policyType], and we resolve the policy
     * instance through this cache so we don't allocate per tick.
     *
     * Each cached instance gets its own deterministic [Random] seeded from
     * [currentSeed] XOR-mixed with a per-type constant (see
     * [resolveNpcPolicy]) so per-type RNG stays reproducible for a given
     * engine seed and stays independent of the shared [npcRandom] stream
     * (Hard rule #4).
     */
    private val npcPolicyCache = mutableMapOf<NpcPolicyType, NpcPolicy>()

    /**
     * Adventure-mode override of [DifficultyPreset.npcCount]. When non-null
     * the next [spawnNpcs] call uses this value instead of the preset's
     * `npcCount`. Reset to `null` by [setDifficulty] so changing the
     * preset always returns to the preset-driven count.
     */
    var npcCountOverride: Int? = null
        private set

    /**
     * Adventure-mode override assigning a [NpcPolicyType] per NPC by spawn
     * index. When `null` every NPC uses the engine's configured
     * [npcPolicyType] (single-maze behaviour). When non-null
     * [spawnNpcs] sets each `Npc.policyType` from this list, padding with
     * [npcPolicyType] if the list is shorter than the spawn count.
     */
    var npcPolicies: List<NpcPolicyType>? = null
        private set

    private var playerAccumulator = 0f
    private var npcAccumulator = 0f
    private val manualQueue = ArrayDeque<Direction>()
    /**
     * Absolute [elapsedSeconds] timestamp at which the manual-input override
     * (see [queueManualMove]) expires. `<= elapsedSeconds` means the override
     * is inactive. Reset to `0f` in [restart] and [setPlayerPolicy].
     */
    private var manualOverrideUntilSeconds = 0f

    private val powerUpsByCell = mutableMapOf<GridPos, SpawnedPowerUp>()
    private val activeEffectsByType = mutableMapOf<PowerUpType, ActivePowerUpEffect>()
    /**
     * Tracks a FREEZE effect inflicted on the *player* by an NPC that picked
     * up a FREEZE power-up. Kept separate from [activeEffectsByType] so it
     * does not piggy-back on the player's collision immunity (a player frozen
     * by an NPC must still LOSE on contact).
     */
    private var npcInducedPlayerFreeze: ActivePowerUpEffect? = null
    private var powerUpRespawnAccumulator = 0f

    init {
        spawnNpcs()
        spawnInitialPowerUps()
    }

    fun restart(seed: Long = System.currentTimeMillis()) {
        currentSeed = seed
        maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
        navigator = MazeNavigator(maze)
        player = Player(maze.start)
        npcs = mutableListOf()
        random = Random(seed)
        npcRandom = Random(seed xor NPC_RANDOM_SEED_MIX)
        npcPolicy = PolicyFactory.npc(npcPolicyType, npcRandom)
        npcPolicyCache.clear()
        status = GameStatus.RUNNING
        elapsedSeconds = 0f
        steps = 0
        countdownRemainingSeconds = 0f
        goFlashRemainingSeconds = 0f
        manualOverrideUntilSeconds = 0f
        playerAccumulator = 0f
        npcAccumulator = 0f
        powerUpRespawnAccumulator = 0f
        powerUpsByCell.clear()
        activeEffectsByType.clear()
        npcInducedPlayerFreeze = null
        manualQueue.clear()
        playerPolicy.reset()
        npcPolicy.reset()
        spawnNpcs()
        spawnInitialPowerUps()
    }

    fun setDifficulty(newDifficulty: DifficultyPreset) {
        applyDifficulty(newDifficulty)
        restart()
    }

    /**
     * Apply [newDifficulty] without restarting. Intended for callers like
     * Adventure mode that already perform their own [restart] (with a
     * specific seed) immediately after configuring per-maze state — using
     * [setDifficulty] would queue an extra full restart/spawn pass on the
     * GL thread that the subsequent restart immediately discards.
     */
    fun applyDifficulty(newDifficulty: DifficultyPreset) {
        difficulty = newDifficulty
        // Changing difficulty discards any per-maze Adventure overrides so a
        // user who flips difficulty mid-session gets the preset's NPC count.
        npcCountOverride = null
        npcPolicies = null
    }

    fun setPlayerPolicy(type: PlayerPolicyType) {
        playerPolicyType = type
        playerPolicy = PolicyFactory.player(type)
        manualQueue.clear()
        manualOverrideUntilSeconds = 0f
        playerPolicy.reset()
    }

    private fun resetNpcPolicyState() {
        npcs.forEach { npc ->
            npc.state = NpcState.PATROL
            npc.lastKnownPlayerPos = null
            npc.searchTicksRemaining = 0
            npc.patrolIndex = 0
        }
    }

    fun setNpcPolicy(type: NpcPolicyType) {
        npcPolicyType = type
        npcPolicy = PolicyFactory.npc(type, npcRandom)
        npcPolicy.reset()
        npcPolicyCache.clear()
        // Selecting a new uniform policy at runtime is a single-maze
        // intent: discard any lingering Adventure per-NPC override so
        // a subsequent restart/spawnNpcs uses the freshly selected
        // policy instead of the persisted per-NPC list.
        npcCountOverride = null
        npcPolicies = null
        // Single-maze re-assignment: NPCs that were spawned with an
        // Adventure-supplied per-NPC policy follow the new uniform policy.
        npcs.forEach { it.policyType = type }
        resetNpcPolicyState()
    }

    /**
     * Adventure-mode entry point. Locks the NPC count and per-NPC policy
     * list for the *next* [restart] (or maze re-entry). Callers should
     * follow this with [restart] (or, on the host, allow the existing
     * restart path to run) so the new NPC spawn count and policies take
     * effect. Passing an empty [policies] list (or fewer than [npcCount])
     * falls back to the engine's configured [npcPolicyType] for any
     * unassigned NPC index.
     */
    fun configureAdventureMaze(npcCount: Int, policies: List<NpcPolicyType>) {
        require(npcCount >= 0) { "npcCount must be >= 0 (was $npcCount)" }
        npcCountOverride = npcCount
        npcPolicies = policies.toList()
    }

    /**
     * Clears any Adventure-mode overrides previously installed via
     * [configureAdventureMaze] so subsequent [restart] calls revert to the
     * preset's `npcCount` and the uniform [npcPolicyType].
     */
    fun clearAdventureMazeConfig() {
        npcCountOverride = null
        npcPolicies = null
    }

    /**
     * Adventure-mode hook: activate [type] as if the player had picked
     * it up off the maze the instant the maze started. Safe to call
     * after [restart] / [configureAdventureMaze] but before the first
     * [update] tick. Reuses the regular power-up activation pipeline so
     * timed effects (INVISIBILITY, SPEED_UP, FREEZE, SHIELD, SLOW_TIME,
     * MAGNET, GHOST_MODE) start
     * ticking from `t=0` and instant effects (TELEPORT, BLAST) apply
     * once. No-op when [type] is `null`.
     */
    fun applyStartingPowerUp(type: PowerUpType?) {
        if (type == null) return
        activatePlayerPowerUp(type)
        if (type == PowerUpType.MAGNET) {
            collectMagnetPowerUps()
        }
    }

    /**
     * Capture the engine's observable state as a serialisable
     * [GameEngineSnapshot]. RNG and per-frame accumulator state are
     * intentionally omitted; see the snapshot KDoc for rationale.
     */
    fun snapshot(): GameEngineSnapshot = GameEngineSnapshot(
        difficultyName = difficulty.name,
        playerPolicy = playerPolicyType,
        npcPolicy = npcPolicyType,
        seed = currentSeed,
        status = status,
        elapsedSeconds = elapsedSeconds,
        steps = steps,
        player = GameEngineSnapshot.PlayerSnapshot(player.position.x, player.position.y, player.facing),
        npcs = npcs.map {
            GameEngineSnapshot.NpcSnapshot(it.id, it.position.x, it.position.y, it.facing)
        },
        spawnedPowerUps = powerUpsByCell.values.map { p ->
            GameEngineSnapshot.SpawnedPowerUpSnapshot(
                type = p.type,
                x = p.position.x,
                y = p.position.y,
                remainingSeconds = p.expiresAtSeconds?.let { (it - elapsedSeconds).coerceAtLeast(0f) }
            )
        },
        activeEffects = activeEffectsByType.values.map { e ->
            GameEngineSnapshot.ActiveEffectSnapshot(
                type = e.type,
                remainingSeconds = e.endsAtSeconds?.let { (it - elapsedSeconds).coerceAtLeast(0f) }
            )
        },
        npcInducedPlayerFreezeRemainingSeconds = npcInducedPlayerFreeze?.endsAtSeconds
            ?.let { (it - elapsedSeconds).coerceAtLeast(0f) },
        manualQueue = manualQueue.toList(),
        manualOverrideRemainingSeconds = manualOverrideRemainingSeconds,
        removedWalls = computeRemovedWalls(),
        npcCountOverride = npcCountOverride,
        // Always persist the per-NPC policy list (indexed by spawn id /
        // [Npc.id]) so a resume on a snapshot taken mid-Adventure-maze
        // restores each NPC's individual policy. For single-maze runs this
        // is just a uniform list of [npcPolicyType] — small and harmless.
        npcPolicies = npcs.map { it.policyType }
    )

    /**
     * Returns the set of walls that gameplay has removed relative to the
     * baseline maze [MazeGenerator] produces for [currentSeed]. Each shared
     * wall is emitted once (using EAST for the cell with the smaller x and
     * NORTH for the cell with the smaller y) so the persisted list is
     * compact and round-trips through [restore] without duplicates.
     */
    private fun computeRemovedWalls(): List<GameEngineSnapshot.RemovedWallSnapshot> {
        val baseline = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, currentSeed)
        // Defensive: if the regenerated baseline ever stopped matching the
        // installed maze's dimensions, abandon the diff rather than risk
        // an indexing crash — restore will still produce a playable maze
        // from the seed, just without preserving mid-run wall mutations.
        if (baseline.width != maze.width || baseline.height != maze.height) return emptyList()
        val baselineCells = baseline.copyCells()
        val currentCells = maze.copyCells()
        val result = mutableListOf<GameEngineSnapshot.RemovedWallSnapshot>()
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val i = y * maze.width + x
                // Bits set in baseline but cleared in current = removed walls.
                val diff = baselineCells[i] and currentCells[i].inv()
                if (diff and Maze.WALL_EAST != 0 && x + 1 < maze.width) {
                    result.add(GameEngineSnapshot.RemovedWallSnapshot(x, y, Direction.EAST))
                }
                if (diff and Maze.WALL_NORTH != 0 && y + 1 < maze.height) {
                    result.add(GameEngineSnapshot.RemovedWallSnapshot(x, y, Direction.NORTH))
                }
            }
        }
        return result
    }

    /**
     * Restore engine state from a [GameEngineSnapshot]. Regenerates the
     * maze deterministically from [GameEngineSnapshot.seed], then overlays
     * the persisted positions, power-ups, and timers on top. The countdown
     * is **not** re-armed — resumed games skip the 3-2-1 since the player
     * already saw the layout before saving.
     */
    fun restore(snapshot: GameEngineSnapshot) {
        // Validate the snapshot before installing any state: an unknown
        // difficulty name would silently fall back to MEDIUM via
        // DifficultyPresets.byName and regenerate a differently-sized
        // maze, and out-of-bounds persisted coordinates would later
        // crash the engine through Maze.hasWall. Reject up-front so the
        // caller can fall back to a fresh game (and clear the stored
        // snapshot) instead of corrupting engine state.
        //
        // The engine's *currently-installed* difficulty is accepted as a
        // fallback when its name matches the snapshot's `difficultyName`
        // even if that name isn't in `DifficultyPresets.all` — this
        // supports test-only / future custom presets that aren't shipped
        // as built-ins. On-disk snapshots reach this path through
        // `fromJson` first, which still rejects unknown names outright.
        val preset = snapshot.resolvePreset()
            ?: difficulty.takeIf { it.name == snapshot.difficultyName }
            ?: throw IllegalArgumentException(
                "Unknown difficulty in snapshot: ${snapshot.difficultyName}"
            )
        require(snapshot.isWithinBounds(preset)) {
            "Snapshot contains positions outside maze bounds for preset ${preset.name}"
        }
        difficulty = preset
        playerPolicyType = snapshot.playerPolicy
        npcPolicyType = snapshot.npcPolicy
        playerPolicy = PolicyFactory.player(playerPolicyType)
        currentSeed = snapshot.seed
        maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, currentSeed)
        // Re-apply any walls that gameplay had removed (e.g., via BLAST)
        // before the snapshot was taken, so a resumed game preserves
        // mid-run wall destruction instead of restoring the pristine
        // seeded layout. Maze.removeWall is bounds-checked and idempotent.
        snapshot.removedWalls.forEach { w ->
            val pos = GridPos(w.x, w.y)
            if (maze.inBounds(pos)) maze.removeWall(pos, w.direction)
        }
        navigator = MazeNavigator(maze)
        random = Random(currentSeed)
        npcRandom = Random(currentSeed xor NPC_RANDOM_SEED_MIX)
        npcPolicy = PolicyFactory.npc(npcPolicyType, npcRandom)
        npcPolicyCache.clear()
        playerPolicy.reset()
        npcPolicy.reset()

        // Re-install Adventure-mode overrides (if any) from the snapshot.
        // These drive subsequent restarts so a paused-mid-maze resume that
        // later hits restart still gets the right NPC count + per-NPC
        // policies. The per-NPC `policyType` field below is what governs
        // the *current* spawned NPCs.
        //
        // Single-maze snapshots also persist `npcPolicies` (a uniform list
        // of `snapshot.npcPolicy`) so each NPC's `policyType` can be
        // restored individually. Re-installing that uniform list as an
        // override would then make a later [setNpcPolicy] ineffective on
        // restart, because [spawnNpcs] would keep preferring the persisted
        // list. Treat the snapshot as an Adventure override only when it
        // explicitly carries a count override or a non-uniform policy list.
        npcCountOverride = snapshot.npcCountOverride
        npcPolicies = snapshot.npcPolicies
            .takeIf { list ->
                list.isNotEmpty() && (
                    snapshot.npcCountOverride != null ||
                        list.any { it != snapshot.npcPolicy }
                )
            }

        status = snapshot.status
        elapsedSeconds = snapshot.elapsedSeconds
        steps = snapshot.steps
        countdownRemainingSeconds = 0f
        goFlashRemainingSeconds = 0f

        player = Player(
            position = GridPos(snapshot.player.x, snapshot.player.y),
            facing = snapshot.player.facing
        )
        npcs = snapshot.npcs.map { n ->
            Npc(
                id = n.id,
                position = GridPos(n.x, n.y),
                facing = n.facing,
                patrolRoute = patrolRouteFrom(GridPos(n.x, n.y)),
                // Restore each NPC's per-NPC policy, defaulting to the
                // engine-wide [npcPolicyType] when the snapshot did not
                // record one (single-maze snapshots).
                policyType = snapshot.npcPolicies.getOrNull(n.id) ?: npcPolicyType
            )
        }.toMutableList()

        powerUpsByCell.clear()
        snapshot.spawnedPowerUps.forEach { p ->
            val pos = GridPos(p.x, p.y)
            powerUpsByCell[pos] = SpawnedPowerUp(
                type = p.type,
                position = pos,
                spawnedAtSeconds = elapsedSeconds,
                expiresAtSeconds = p.remainingSeconds?.let { elapsedSeconds + it }
            )
        }
        activeEffectsByType.clear()
        snapshot.activeEffects.forEach { e ->
            activeEffectsByType[e.type] = ActivePowerUpEffect(
                type = e.type,
                startedAtSeconds = elapsedSeconds,
                endsAtSeconds = e.remainingSeconds?.let { elapsedSeconds + it }
            )
        }
        npcInducedPlayerFreeze = snapshot.npcInducedPlayerFreezeRemainingSeconds?.let { rem ->
            ActivePowerUpEffect(
                type = PowerUpType.FREEZE,
                startedAtSeconds = elapsedSeconds,
                endsAtSeconds = elapsedSeconds + rem
            )
        }
        manualQueue.clear()
        snapshot.manualQueue.forEach { manualQueue.addLast(it) }
        manualOverrideUntilSeconds = elapsedSeconds + snapshot.manualOverrideRemainingSeconds.coerceAtLeast(0f)
        playerAccumulator = 0f
        npcAccumulator = 0f
        powerUpRespawnAccumulator = 0f
    }

    /**
     * Accept a manual movement request while gameplay input is active. Requests
     * are queued regardless of the active [playerPolicyType] so the player can
     * nudge the character even under an automated policy. When the policy is not
     * [PlayerPolicyType.MANUAL], a successful queue arms a
     * [MANUAL_OVERRIDE_DURATION_SECONDS]-long override window during which
     * [updatePlayer] consumes the queued direction instead of asking the policy
     * for a move.
     */
    fun queueManualMove(direction: Direction) {
        if (!canAcceptManualInput()) {
            return
        }
        addManualMove(direction)
        armManualOverrideIfNeeded()
    }

    /**
     * Queue a directional manual run that continues until the next wall or
     * bounds edge. A fresh run replaces any pending manual inputs so a new
     * swipe / D-pad tap can turn the player before the previous run drains.
     */
    fun queueManualMoveUntilBlocked(direction: Direction) {
        if (!canAcceptManualInput()) {
            return
        }
        manualQueue.clear()
        var cursor = player.position
        while (manualQueue.size < MAX_MANUAL_QUEUE) {
            val next = cursor.moved(direction)
            if (!canPlayerTraverse(cursor, direction)) break
            addManualMove(direction)
            cursor = next
        }
        if (manualQueue.isEmpty()) return
        val movesPerSecond = effectivePlayerMovesPerSecond()
        val queuedRunDurationSeconds = if (movesPerSecond > 0f) {
            manualQueue.size / movesPerSecond
        } else {
            MANUAL_OVERRIDE_DURATION_SECONDS
        }
        armManualOverrideIfNeeded(queuedRunDurationSeconds)
    }

    /**
     * Arm (or re-arm) the pre-game countdown. While
     * [countdownRemainingSeconds] is positive, [update] consumes the value
     * from the supplied delta but does not advance simulation state. Called
     * by the game host after construction / restart so the player has time
     * to assess the layout before NPCs start moving.
     */
    fun startCountdown(seconds: Float = COUNTDOWN_DEFAULT_SECONDS) {
        countdownRemainingSeconds = seconds.coerceAtLeast(0f)
    }

    fun togglePause() {
        status = when (status) {
            GameStatus.RUNNING -> GameStatus.PAUSED
            GameStatus.PAUSED -> GameStatus.RUNNING
            else -> status
        }
    }

    fun update(deltaSeconds: Float) {
        if (status != GameStatus.RUNNING) return

        // Pre-game countdown: consume the delta but freeze the simulation so
        // the player has time to read the maze before NPCs move. We
        // intentionally also pause `elapsedSeconds` so power-up lifetimes
        // and HUD timers don't drain during the countdown.
        var effectiveDelta = deltaSeconds
        // Tracks whether [goFlashRemainingSeconds] was armed during *this*
        // update() call (i.e., the countdown finished on this very tick).
        // We skip decay in this case so the full
        // [COUNTDOWN_GO_FLASH_SECONDS] window survives the transition tick
        // regardless of how large the incoming delta or leftover delta is
        // (avoids the GO! frame being consumed by a single big delta).
        var goFlashArmedThisTick = false
        if (countdownRemainingSeconds > 0f) {
            val consumed = effectiveDelta.coerceAtMost(countdownRemainingSeconds)
            countdownRemainingSeconds -= consumed
            if (countdownRemainingSeconds < 0f) countdownRemainingSeconds = 0f
            effectiveDelta -= consumed
            // Arm the post-countdown "GO!" flash on the exact tick the
            // countdown reaches zero, so the renderer has a visible window
            // (~[COUNTDOWN_GO_FLASH_SECONDS]) to display "GO!" even though
            // [countdownRemainingSeconds] is already zero by this point.
            if (countdownRemainingSeconds == 0f) {
                goFlashRemainingSeconds = COUNTDOWN_GO_FLASH_SECONDS
                goFlashArmedThisTick = true
            }
            if (effectiveDelta <= 0f) return
        }
        if (goFlashRemainingSeconds > 0f && !goFlashArmedThisTick) {
            goFlashRemainingSeconds = (goFlashRemainingSeconds - effectiveDelta).coerceAtLeast(0f)
        }

        elapsedSeconds += effectiveDelta
        // Pause player time accumulation while the NPC-induced FREEZE is
        // active so the player can't be moved by manual input or by automated
        // policies, and so no burst of accumulated moves fires after thaw.
        // Mirrors the symmetric NPC accumulator gate below.
        if (!isPlayerFrozenByNpc()) {
            playerAccumulator += effectiveDelta
        }
        // Pause NPC time accumulation while FREEZE is active so freezing truly
        // pauses NPC movement. Preserve the existing accumulator value so any
        // partial progress made before FREEZE started is not discarded
        // (avoids forcing an extra full interval after FREEZE expires).
        if (!isEffectActive(PowerUpType.FREEZE)) {
            npcAccumulator += effectiveDelta
        }

        processPowerUpLifecycles(effectiveDelta)
        collectMagnetPowerUps()

        val playerInterval = 1f / effectivePlayerMovesPerSecond()
        processQueuedManualMoves(playerInterval)

        while (
            !isPlayerFrozenByNpc() &&
            playerAccumulator >= playerInterval &&
            status == GameStatus.RUNNING
        ) {
            playerAccumulator -= playerInterval
            updatePlayer()
            evaluateEndConditions()
        }

        if (!isEffectActive(PowerUpType.FREEZE)) {
            val npcInterval = 1f / effectiveNpcMovesPerSecond()
            while (npcAccumulator >= npcInterval && status == GameStatus.RUNNING) {
                npcAccumulator -= npcInterval
                updateNpcs()
                evaluateEndConditions()
            }
        }
    }

    fun hudState(): HudState = HudState(
        status = status,
        elapsedSeconds = elapsedSeconds,
        steps = steps,
        difficultyName = difficulty.name,
        playerPolicyLabel = playerPolicyType.label,
        npcPolicyLabel = npcPolicyType.label,
        playerSpeed = effectivePlayerMovesPerSecond(),
        npcSpeed = effectiveNpcMovesPerSecond(),
        activePowerUps = activePowerUpSnapshots().map { snapshot ->
            val remaining = snapshot.remainingSeconds?.coerceAtLeast(0f)
            if (remaining == null) snapshot.type.label else "${snapshot.type.label} %.1fs".format(remaining)
        } + npcInducedFreezeHudEntries() + manualOverrideHudEntries(),
        powerUpsOnMap = powerUpsByCell.size,
        countdownRemainingSeconds = countdownRemainingSeconds.takeIf { it > 0f },
        manualOverrideRemainingSeconds = manualOverrideRemainingSeconds.takeIf { it > 0f }
    )

    private fun updatePlayer() {
        val manualOverrideActive = elapsedSeconds < manualOverrideUntilSeconds
        // For MANUAL policy, only queued input drives movement. For an
        // automated policy with an active override, prefer the queued
        // manual direction if present, but fall back to the policy when
        // the queue is empty so a single nudge can't freeze the player
        // for the full override window.
        val queued = if (playerPolicyType == PlayerPolicyType.MANUAL || manualOverrideActive) {
            manualQueue.removeFirstOrNull()
        } else null
        val requestedDirection = queued
            ?: if (playerPolicyType == PlayerPolicyType.MANUAL) {
                return
            } else {
                playerPolicy.nextMove(
                    PlayerPolicyContext(
                        maze = maze,
                        navigator = navigator,
                        player = player,
                        exit = maze.exit,
                        npcs = npcs,
                        playerInvisibleToNpcs = isEffectActive(PowerUpType.INVISIBILITY),
                        npcsFrozen = isEffectActive(PowerUpType.FREEZE),
                        spawnedPowerUps = spawnedPowerUpsView,
                        pickupRadius = difficulty.automaticPickupRadius
                    )
                ) ?: return
            }

        attemptPlayerMove(requestedDirection)
    }

    private fun processQueuedManualMoves(playerInterval: Float) {
        val canConsumeManualInput = playerPolicyType == PlayerPolicyType.MANUAL ||
            elapsedSeconds < manualOverrideUntilSeconds
        if (!canAcceptManualInput() || !canConsumeManualInput || manualQueue.isEmpty()) {
            return
        }
        // Require positive accumulated player time so zero-delta updates cannot
        // consume queued manual moves and bypass the player-move cooldown.
        if (playerAccumulator <= 0f) {
            return
        }

        val direction = manualQueue.removeFirst()
        if (attemptPlayerMove(direction)) {
            playerAccumulator -= playerInterval
        }
        evaluateEndConditions()
    }

    private fun canAcceptManualInput(): Boolean {
        return status == GameStatus.RUNNING &&
            countdownRemainingSeconds <= 0f &&
            !isPlayerFrozenByNpc()
    }

    private fun addManualMove(direction: Direction) {
        if (manualQueue.size >= MAX_MANUAL_QUEUE) {
            manualQueue.removeFirst()
        }
        manualQueue.addLast(direction)
    }

    private fun armManualOverrideIfNeeded(
        minDurationSeconds: Float = 0f
    ) {
        if (playerPolicyType != PlayerPolicyType.MANUAL && manualQueue.isNotEmpty()) {
            manualOverrideUntilSeconds = elapsedSeconds +
                maxOf(MANUAL_OVERRIDE_DURATION_SECONDS, minDurationSeconds)
        }
    }

    private fun attemptPlayerMove(requestedDirection: Direction): Boolean {
        // GHOST_MODE lets the player walk through walls; NPCs intentionally do
        // not gain this ability (NpcPolicyContext is unchanged) so the power-up
        // gives the player a distinct movement advantage rather than full
        // collision immunity (which is what INVISIBILITY/FREEZE provide).
        if (!canPlayerTraverse(player.position, requestedDirection)) return false

        val nextPosition = player.position.moved(requestedDirection)
        player.position = nextPosition
        player.facing = requestedDirection
        player.animationFrame = (player.animationFrame + 1) % ANIMATION_FRAMES
        player.lastMoveAtSeconds = elapsedSeconds
        steps += 1
        collectPowerUpAtPlayer()
        collectMagnetPowerUps()
        return true
    }

    private fun canPlayerTraverse(position: GridPos, direction: Direction): Boolean {
        val nextPosition = position.moved(direction)
        return if (isEffectActive(PowerUpType.GHOST_MODE)) {
            maze.inBounds(nextPosition)
        } else {
            maze.canMove(position, direction)
        }
    }

    private fun updateNpcs() {
        val context = NpcPolicyContext(
            maze = maze,
            navigator = navigator,
            player = player,
            visionRange = difficulty.npcVisionRange,
            playerVisible = !isEffectActive(PowerUpType.INVISIBILITY),
            npcsFrozen = isEffectActive(PowerUpType.FREEZE)
        )

        npcs.forEach { npc ->
            val policy = resolveNpcPolicy(npc.policyType)
            val direction = policy.nextMove(npc, context) ?: return@forEach
            if (!maze.canMove(npc.position, direction)) return@forEach

            npc.position = npc.position.moved(direction)
            npc.facing = direction
            npc.animationFrame = (npc.animationFrame + 1) % ANIMATION_FRAMES
            npc.lastMoveAtSeconds = elapsedSeconds
            collectPowerUpAtNpc(npc)
        }
    }

    /**
     * Resolve (and cache) the [NpcPolicy] instance for [type]. For the
     * engine's configured [npcPolicyType] we always return the long-lived
     * [npcPolicy] so single-maze behaviour is byte-for-byte unchanged
     * (same RNG stream). For any other type — used only when an Adventure
     * maze assigns per-NPC policies — we lazily build and cache a fresh
     * instance with its own seeded RNG derived from [currentSeed] (Hard
     * rule #4) so the per-type stream is reproducible.
     */
    private fun resolveNpcPolicy(type: NpcPolicyType): NpcPolicy {
        if (type == npcPolicyType) return npcPolicy
        return npcPolicyCache.getOrPut(type) {
            val perTypeMix = NPC_RANDOM_SEED_MIX xor (type.ordinal + 1).toLong() * NPC_POLICY_TYPE_SEED_STRIDE
            PolicyFactory.npc(type, Random(currentSeed xor perTypeMix))
        }
    }

    /**
     * Power-ups picked up by NPCs. Currently only `FREEZE` is consumed — it
     * freezes the *player* (mirror image of player-picked FREEZE freezing
     * NPCs). Other types are intentionally not collected by NPCs.
     */
    private fun collectPowerUpAtNpc(npc: Npc) {
        val powerUp = powerUpsByCell[npc.position] ?: return
        if (powerUp.type != PowerUpType.FREEZE) return
        powerUpsByCell.remove(npc.position)
        activateNpcInducedPlayerFreeze()
    }

    /**
     * Test seam: simulate the given NPC arriving at [position]. Triggers the
     * same pickup-collection branch that runs after a normal NPC movement
     * step. Production code calls the private path via [updateNpcs].
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun simulateNpcArrivalForTest(npcIndex: Int, position: GridPos) {
        val npc = npcs[npcIndex]
        npc.position = position
        collectPowerUpAtNpc(npc)
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun clearNpcsForTest() {
        npcs.clear()
    }

    private fun activateNpcInducedPlayerFreeze() {
        val duration = PowerUpType.FREEZE.metadata.defaultDurationSeconds
        if (duration <= 0f) return
        val startedAt = npcInducedPlayerFreeze?.startedAtSeconds ?: elapsedSeconds
        // FREEZE uses REFRESH_DURATION stack policy: re-pickup extends the end
        // time but keeps the original start so HUD remaining-time math stays
        // monotonic.
        npcInducedPlayerFreeze = ActivePowerUpEffect(
            type = PowerUpType.FREEZE,
            startedAtSeconds = startedAt,
            endsAtSeconds = elapsedSeconds + duration
        )
    }

    private fun isPlayerFrozenByNpc(): Boolean {
        val effect = npcInducedPlayerFreeze ?: return false
        val endsAt = effect.endsAtSeconds ?: return true
        return elapsedSeconds < endsAt
    }

    private fun evaluateEndConditions() {
        if (player.position == maze.exit) {
            status = GameStatus.WIN
            return
        }

        val collisionImmune = isEffectActive(PowerUpType.INVISIBILITY) ||
            isEffectActive(PowerUpType.FREEZE) ||
            isEffectActive(PowerUpType.SHIELD)
        if (!collisionImmune && npcs.any { it.position == player.position }) {
            status = GameStatus.LOSE
        }
    }

    private fun spawnNpcs() {
        val candidates = npcSpawnCandidates()
        val requestedCount = npcCountOverride ?: difficulty.npcCount
        val spawnCount = requestedCount.coerceAtMost(candidates.size).coerceAtLeast(0)
        val policies = npcPolicies
        repeat(spawnCount) { index ->
            val candidate = candidates[index]
            val route = patrolRouteFrom(candidate)
            // Per-NPC policy: fall back to engine-wide [npcPolicyType] if no
            // override entry was supplied for this spawn index.
            val perNpcPolicy = policies?.getOrNull(index) ?: npcPolicyType
            npcs += Npc(
                id = index,
                position = candidate,
                patrolRoute = route,
                policyType = perNpcPolicy
            )
        }
    }

    private fun npcSpawnCandidates(): List<GridPos> {
        val reserved = setOf(maze.start, maze.exit)
        val shuffled = ArrayList<GridPos>(maze.width * maze.height - reserved.size)
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos !in reserved) shuffled += pos
            }
        }
        shuffled.shuffle(random)

        val directPath = navigator.bfsPath(maze.start, maze.exit)
        // Generated mazes are connected; keep a fallback for restored/test
        // mazes that may be malformed so spawning still produces NPCs.
        if (directPath.isEmpty()) return shuffled

        val bufferedPathCells = directPathBufferCells(directPath, difficulty.npcDirectPathSpawnBuffer)
        val preferred = shuffled.filter { pos -> pos !in bufferedPathCells }
        if (preferred.size == shuffled.size) return preferred

        val preferredSet = preferred.toSet()
        return preferred + shuffled.filter { it !in preferredSet }
    }

    private fun directPathBufferCells(directPath: List<GridPos>, buffer: Int): Set<GridPos> {
        val cells = mutableSetOf<GridPos>()
        directPath.forEach { pathCell ->
            for (dy in -buffer..buffer) {
                for (dx in -buffer..buffer) {
                    val pos = GridPos(pathCell.x + dx, pathCell.y + dy)
                    if (maze.inBounds(pos)) cells += pos
                }
            }
        }
        return cells
    }

    private fun patrolRouteFrom(origin: GridPos): List<GridPos> {
        val neighbors = maze.neighbors(origin)
        if (neighbors.isEmpty()) return listOf(origin)
        return listOf(origin) + neighbors.take(MAX_EXTRA_PATROL_WAYPOINTS)
    }

    private fun spawnInitialPowerUps() {
        val candidates = availablePowerUpSpawnCells().shuffled(random).toMutableList()
        difficulty.initialPowerUpTypes.forEachIndexed { index, type ->
            if (candidates.isEmpty()) return@forEachIndexed
            val cellIndex = random.nextInt(candidates.size)
            val position = candidates.removeAt(cellIndex)
            // Stagger initial expirations so a batch of pickups doesn't all
            // vanish on the same tick. Ignored on infinite-lifetime presets
            // (Easy) where `spawnPowerUp` will store a `null` expiry anyway.
            val extraDelay = index * difficulty.powerUpExpirationStaggerSeconds
            spawnPowerUp(type, position, extraDelay)
        }
    }

    private fun spawnPowerUp(
        type: PowerUpType,
        position: GridPos,
        extraDelaySeconds: Float = 0f
    ) {
        if (position in powerUpsByCell) return
        if (position == maze.start || position == maze.exit) return
        if (position == player.position) return
        if (npcs.any { it.position == position }) return
        val lifetime = difficulty.powerUpPickupLifetimeSeconds
        val expiresAt = if (lifetime > 0f) {
            elapsedSeconds + lifetime + extraDelaySeconds.coerceAtLeast(0f)
        } else {
            null
        }
        powerUpsByCell[position] = SpawnedPowerUp(
            type = type,
            position = position,
            spawnedAtSeconds = elapsedSeconds,
            expiresAtSeconds = expiresAt
        )
    }

    private fun processPowerUpLifecycles(deltaSeconds: Float) {
        expireTimedPowerUpsOnMap()
        expireActiveEffects()
        schedulePowerUpRespawns(deltaSeconds)
    }

    private fun expireTimedPowerUpsOnMap() {
        if (difficulty.powerUpPickupLifetimeSeconds <= 0f) return
        val expired = powerUpsByCell.values
            .filter { it.expiresAtSeconds != null && elapsedSeconds >= it.expiresAtSeconds }
            .map { it.position }
        expired.forEach { powerUpsByCell.remove(it) }
    }

    private fun expireActiveEffects() {
        val expired = activeEffectsByType.values
            .filter { it.endsAtSeconds != null && elapsedSeconds >= it.endsAtSeconds }
            .map { it.type }
        expired.forEach { activeEffectsByType.remove(it) }
        npcInducedPlayerFreeze?.let { effect ->
            val endsAt = effect.endsAtSeconds
            if (endsAt != null && elapsedSeconds >= endsAt) {
                npcInducedPlayerFreeze = null
            }
        }
    }

    private fun schedulePowerUpRespawns(deltaSeconds: Float) {
        val interval = difficulty.powerUpRespawnIntervalSeconds ?: return
        if (interval <= 0f) return

        powerUpRespawnAccumulator += deltaSeconds
        while (powerUpRespawnAccumulator >= interval) {
            powerUpRespawnAccumulator -= interval
            spawnRandomPowerUp()
        }
    }

    private fun spawnRandomPowerUp() {
        val candidates = availablePowerUpSpawnCells()
        if (candidates.isEmpty()) return
        val position = candidates[random.nextInt(candidates.size)]
        val type = PowerUpType.entries[random.nextInt(PowerUpType.entries.size)]
        spawnPowerUp(type, position)
    }

    private fun availablePowerUpSpawnCells(): List<GridPos> {
        val invalidCells = buildSet<GridPos> {
            add(maze.start)
            add(maze.exit)
            add(player.position)
            npcs.forEach { add(it.position) }
            powerUpsByCell.keys.forEach { add(it) }
        }
        val candidates = mutableListOf<GridPos>()
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos !in invalidCells) candidates += pos
            }
        }
        return candidates
    }

    private fun collectPowerUpAtPlayer() {
        val powerUp = powerUpsByCell.remove(player.position) ?: return
        activatePlayerPowerUp(powerUp.type)
    }

    private fun activatePlayerPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.INVISIBILITY -> activateTimedEffect(PowerUpType.INVISIBILITY)
            PowerUpType.TELEPORT -> applyTeleport()
            PowerUpType.SPEED_UP -> activateTimedEffect(PowerUpType.SPEED_UP)
            PowerUpType.FREEZE -> activateTimedEffect(PowerUpType.FREEZE)
            PowerUpType.SHIELD -> activateTimedEffect(PowerUpType.SHIELD)
            PowerUpType.SLOW_TIME -> activateTimedEffect(PowerUpType.SLOW_TIME)
            PowerUpType.MAGNET -> activateTimedEffect(PowerUpType.MAGNET)
            PowerUpType.BLAST -> applyBlast()
            PowerUpType.GHOST_MODE -> activateTimedEffect(PowerUpType.GHOST_MODE)
        }
    }

    private fun collectMagnetPowerUps() {
        if (!isEffectActive(PowerUpType.MAGNET)) return
        val playerPos = player.position
        val nearby = ArrayList<SpawnedPowerUp>()
        for (powerUp in powerUpsByCell.values) {
            if (chebyshevDistance(playerPos, powerUp.position) <= MAGNET_PICKUP_RADIUS) {
                nearby.add(powerUp)
            }
        }
        nearby.sortWith(
            compareBy<SpawnedPowerUp>(
                { chebyshevDistance(playerPos, it.position) },
                { it.type.ordinal },
                { it.position.x },
                { it.position.y }
            )
        )
        nearby.forEach { powerUp ->
            if (powerUpsByCell.remove(powerUp.position) != null) {
                activatePlayerPowerUp(powerUp.type)
                // Position-changing effects (e.g. TELEPORT) can relocate the
                // player mid-iteration; stop so we don't keep collecting
                // pickups that were only near the old position.
                if (player.position != playerPos) return
            }
        }
    }

    private fun chebyshevDistance(a: GridPos, b: GridPos): Int =
        maxOf(abs(a.x - b.x), abs(a.y - b.y))

    private fun activateTimedEffect(type: PowerUpType) {
        val duration = type.metadata.defaultDurationSeconds
        if (duration <= 0f) return

        val existing = activeEffectsByType[type]
        if (existing != null && type.metadata.stackPolicy == PowerUpStackPolicy.IGNORE_IF_ACTIVE) return

        activeEffectsByType[type] = ActivePowerUpEffect(
            type = type,
            startedAtSeconds = elapsedSeconds,
            endsAtSeconds = elapsedSeconds + duration
        )
    }

    private fun applyTeleport() {
        val candidates = mutableListOf<GridPos>()
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos == maze.exit || pos == player.position) continue
                if (npcs.any { it.position == pos }) continue
                if (navigator.bfsPath(pos, maze.exit).isNotEmpty()) {
                    candidates += pos
                }
            }
        }
        if (candidates.isEmpty()) return
        player.position = candidates[random.nextInt(candidates.size)]
        // If the teleport destination contains a spawned power-up, collect it
        // immediately so the pickup is not left "under" the player (which
        // otherwise would only collect on the next manual movement step).
        collectPowerUpAtPlayer()
    }

    private fun applyBlast() {
        Direction.entries.forEach { maze.removeWall(player.position, it) }
    }

    private fun activePowerUpSnapshots(): List<ActivePowerUpSnapshot> {
        return activeEffectsByType.values
            .sortedBy { it.type.ordinal }
            .map { effect ->
                val remaining = effect.endsAtSeconds?.let { it - elapsedSeconds }?.coerceAtLeast(0f)
                ActivePowerUpSnapshot(effect.type, remaining)
            }
    }

    /**
     * HUD entry shown when the player has been frozen by an NPC FREEZE
     * pickup. Distinct label from the regular FREEZE active effect so the
     * player can tell which side benefits.
     */
    private fun npcInducedFreezeHudEntries(): List<String> {
        val effect = npcInducedPlayerFreeze ?: return emptyList()
        val remaining = effect.endsAtSeconds?.let { it - elapsedSeconds }?.coerceAtLeast(0f)
        val label = "Frozen"
        return listOf(if (remaining == null) label else "%s %.1fs".format(label, remaining))
    }

    /**
     * Single-element HUD list (or empty) describing the active manual-input
     * override, so the popover surfaces "Manual Xs" alongside power-up
     * effects. Only emitted when the override is currently active.
     */
    private fun manualOverrideHudEntries(): List<String> {
        val remaining = manualOverrideRemainingSeconds
        if (remaining <= 0f) return emptyList()
        return listOf("Manual %.1fs".format(remaining))
    }

    private fun isEffectActive(type: PowerUpType): Boolean {
        val effect = activeEffectsByType[type] ?: return false
        val endsAt = effect.endsAtSeconds ?: return true
        return elapsedSeconds < endsAt
    }

    private fun effectivePlayerMovesPerSecond(): Float {
        val speedMultiplier = if (isEffectActive(PowerUpType.SPEED_UP)) SPEED_UP_MULTIPLIER else 1f
        return difficulty.playerMovesPerSecond * speedMultiplier
    }

    private fun effectiveNpcMovesPerSecond(): Float {
        val speedMultiplier = if (isEffectActive(PowerUpType.SLOW_TIME)) SLOW_TIME_NPC_MULTIPLIER else 1f
        return difficulty.npcMovesPerSecond * speedMultiplier
    }

    companion object {
        private const val MAX_EXTRA_PATROL_WAYPOINTS = 2
        private const val MAX_MANUAL_QUEUE = 64
        private const val SPEED_UP_MULTIPLIER = 2f
        private const val SLOW_TIME_NPC_MULTIPLIER = 0.5f
        private const val MAGNET_PICKUP_RADIUS = 2
        /** Default duration of the manual-input override (see [queueManualMove]). */
        const val MANUAL_OVERRIDE_DURATION_SECONDS = 3f
        /** Default duration of the pre-game countdown (see [startCountdown]). */
        const val COUNTDOWN_DEFAULT_SECONDS = 3f
        /** How long the post-countdown "GO!" flash stays visible. */
        const val COUNTDOWN_GO_FLASH_SECONDS = 0.6f
        // Arbitrary mix constant so the NPC policy RNG stream is decoupled from
        // (but still deterministically derived from) the engine seed. Written
        // as a signed-Long literal because Kotlin `const val` initializers must
        // be compile-time constants (UInt/ULong .toLong() is not).
        private const val NPC_RANDOM_SEED_MIX: Long = -0x61C8864680B583EBL

        /**
         * Stride between per-`NpcPolicyType` RNG streams in Adventure mode.
         * Multiplied by `(ordinal + 1)` and XOR-mixed with [NPC_RANDOM_SEED_MIX]
         * so each NPC policy type used in a maze gets a distinct deterministic
         * seed derived from [currentSeed].
         *
         * Written as a signed-Long literal because Kotlin `const val`
         * initializers must be compile-time constants (UInt/ULong .toLong()
         * is not). Value chosen to be coprime with small multipliers so
         * per-type seeds remain well-spread.
         */
        private const val NPC_POLICY_TYPE_SEED_STRIDE: Long = 0x12B9B0A1CE4A11BL
        /**
         * Number of distinct movement-step animation frames an entity cycles
         * through while moving. The renderer picks `stepFrames[animationFrame]`
         * directly, so this MUST be 2 to ensure consecutive moves alternate
         * the two step poses without a wraparound that visibly repeats one
         * pose. The idle frame is selected separately by elapsed-time
         * threshold and is not part of this counter.
         */
        const val ANIMATION_FRAMES = 2
        /**
         * Renderer-side idle threshold: entities that haven't moved for this many
         * seconds are drawn using the idle frame.
         */
        const val ANIMATION_IDLE_THRESHOLD_SECONDS = 0.15f
    }
}
