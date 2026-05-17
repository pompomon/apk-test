package com.example.apktest.game.core

import androidx.annotation.VisibleForTesting
import com.example.apktest.game.ui.HudState
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

    private var random = Random(seed)
    // Independent RNG stream for NPC policy decisions (e.g. wander move under
    // INVISIBILITY). Derived from the same seed so behaviour stays
    // reproducible, but kept separate from `random` so power-up spawning and
    // other engine RNG consumers don't perturb the wander sequence.
    private var npcRandom = Random(seed xor NPC_RANDOM_SEED_MIX)
    private var playerPolicy: PlayerPolicy = PolicyFactory.player(playerPolicyType)
    private var npcPolicy: NpcPolicy = PolicyFactory.npc(npcPolicyType, npcRandom)

    private var playerAccumulator = 0f
    private var npcAccumulator = 0f
    private val manualQueue = ArrayDeque<Direction>()

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
        maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
        navigator = MazeNavigator(maze)
        player = Player(maze.start)
        npcs = mutableListOf()
        random = Random(seed)
        npcRandom = Random(seed xor NPC_RANDOM_SEED_MIX)
        npcPolicy = PolicyFactory.npc(npcPolicyType, npcRandom)
        status = GameStatus.RUNNING
        elapsedSeconds = 0f
        steps = 0
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
        difficulty = newDifficulty
        restart()
    }

    fun setPlayerPolicy(type: PlayerPolicyType) {
        playerPolicyType = type
        playerPolicy = PolicyFactory.player(type)
        manualQueue.clear()
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
        resetNpcPolicyState()
    }

    fun queueManualMove(direction: Direction) {
        if (playerPolicyType != PlayerPolicyType.MANUAL) return
        if (manualQueue.size >= MAX_MANUAL_QUEUE) {
            manualQueue.removeFirst()
        }
        manualQueue.addLast(direction)
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

        elapsedSeconds += deltaSeconds
        // Pause player time accumulation while the NPC-induced FREEZE is
        // active so the player can't be moved by manual input or by automated
        // policies, and so no burst of accumulated moves fires after thaw.
        // Mirrors the symmetric NPC accumulator gate below.
        if (!isPlayerFrozenByNpc()) {
            playerAccumulator += deltaSeconds
        }
        // Pause NPC time accumulation while FREEZE is active so freezing truly
        // pauses NPC movement. Preserve the existing accumulator value so any
        // partial progress made before FREEZE started is not discarded
        // (avoids forcing an extra full interval after FREEZE expires).
        if (!isEffectActive(PowerUpType.FREEZE)) {
            npcAccumulator += deltaSeconds
        }

        processPowerUpLifecycles(deltaSeconds)

        val playerInterval = 1f / effectivePlayerMovesPerSecond()
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
            val npcInterval = 1f / difficulty.npcMovesPerSecond
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
        npcSpeed = difficulty.npcMovesPerSecond,
        activePowerUps = activePowerUpSnapshots().map { snapshot ->
            val remaining = snapshot.remainingSeconds?.coerceAtLeast(0f)
            if (remaining == null) snapshot.type.label else "${snapshot.type.label} %.1fs".format(remaining)
        } + npcInducedFreezeHudEntries(),
        powerUpsOnMap = powerUpsByCell.size
    )

    private fun updatePlayer() {
        val requestedDirection = when (playerPolicyType) {
            PlayerPolicyType.MANUAL -> manualQueue.removeFirstOrNull()
            else -> playerPolicy.nextMove(
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
            )
        } ?: return

        // GHOST_MODE lets the player walk through walls; NPCs intentionally do
        // not gain this ability (NpcPolicyContext is unchanged) so the power-up
        // gives the player a distinct movement advantage rather than full
        // collision immunity (which is what INVISIBILITY/FREEZE provide).
        val nextPosition = player.position.moved(requestedDirection)
        val canTraverse = if (isEffectActive(PowerUpType.GHOST_MODE)) {
            maze.inBounds(nextPosition)
        } else {
            maze.canMove(player.position, requestedDirection)
        }
        if (!canTraverse) return

        player.position = nextPosition
        player.facing = requestedDirection
        player.animationFrame = (player.animationFrame + 1) % ANIMATION_FRAMES
        player.lastMoveAtSeconds = elapsedSeconds
        steps += 1
        collectPowerUpAtPlayer()
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
            val direction = npcPolicy.nextMove(npc, context) ?: return@forEach
            if (!maze.canMove(npc.position, direction)) return@forEach

            npc.position = npc.position.moved(direction)
            npc.facing = direction
            npc.animationFrame = (npc.animationFrame + 1) % ANIMATION_FRAMES
            npc.lastMoveAtSeconds = elapsedSeconds
            collectPowerUpAtNpc(npc)
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

        val collisionImmune = isEffectActive(PowerUpType.INVISIBILITY) || isEffectActive(PowerUpType.FREEZE)
        if (!collisionImmune && npcs.any { it.position == player.position }) {
            status = GameStatus.LOSE
        }
    }

    private fun spawnNpcs() {
        val reserved = setOf(maze.start, maze.exit)
        val candidates = ArrayList<GridPos>(maze.width * maze.height - reserved.size)
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos !in reserved) candidates += pos
            }
        }
        candidates.shuffle(random)
        val spawnCount = difficulty.npcCount.coerceAtMost(candidates.size)
        repeat(spawnCount) { index ->
            val candidate = candidates[index]
            val route = patrolRouteFrom(candidate)
            npcs += Npc(
                id = index,
                position = candidate,
                patrolRoute = route
            )
        }
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
        scheduleEasyModeRespawns(deltaSeconds)
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

    private fun scheduleEasyModeRespawns(deltaSeconds: Float) {
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
        when (powerUp.type) {
            PowerUpType.INVISIBILITY -> activateTimedEffect(PowerUpType.INVISIBILITY)
            PowerUpType.TELEPORT -> applyTeleport()
            PowerUpType.SPEED_UP -> activateTimedEffect(PowerUpType.SPEED_UP)
            PowerUpType.FREEZE -> activateTimedEffect(PowerUpType.FREEZE)
            PowerUpType.BLAST -> applyBlast()
            PowerUpType.GHOST_MODE -> activateTimedEffect(PowerUpType.GHOST_MODE)
        }
    }

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

    private fun isEffectActive(type: PowerUpType): Boolean {
        val effect = activeEffectsByType[type] ?: return false
        val endsAt = effect.endsAtSeconds ?: return true
        return elapsedSeconds < endsAt
    }

    private fun effectivePlayerMovesPerSecond(): Float {
        val speedMultiplier = if (isEffectActive(PowerUpType.SPEED_UP)) SPEED_UP_MULTIPLIER else 1f
        return difficulty.playerMovesPerSecond * speedMultiplier
    }

    companion object {
        private const val MAX_EXTRA_PATROL_WAYPOINTS = 2
        private const val MAX_MANUAL_QUEUE = 8
        private const val SPEED_UP_MULTIPLIER = 2f
        // Arbitrary mix constant so the NPC policy RNG stream is decoupled from
        // (but still deterministically derived from) the engine seed. Written
        // as a signed-Long literal because Kotlin `const val` initializers must
        // be compile-time constants (UInt/ULong .toLong() is not).
        private const val NPC_RANDOM_SEED_MIX: Long = -0x61C8864680B583EBL
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
