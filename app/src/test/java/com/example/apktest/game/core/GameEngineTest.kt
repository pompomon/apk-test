package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val seed = 1234L

    @Test
    fun update_doesNothingWhenPaused() {
        val engine = GameEngine(testPreset(), seed)
        engine.togglePause()
        assertEquals(GameStatus.PAUSED, engine.status)

        engine.update(5f)

        assertEquals(0f, engine.elapsedSeconds, 0.0001f)
        assertEquals(0, engine.steps)
    }

    @Test
    fun togglePause_switchesBetweenRunningAndPaused() {
        val engine = GameEngine(testPreset(), seed)
        assertEquals(GameStatus.RUNNING, engine.status)

        engine.togglePause()
        assertEquals(GameStatus.PAUSED, engine.status)

        engine.togglePause()
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun update_advancesElapsedTimeAndStepsWhenRunning() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)

        engine.update(1f)

        assertTrue(engine.elapsedSeconds > 0f)
        assertTrue(engine.steps > 0)
    }

    @Test
    fun queueManualMove_isIgnoredWhenPolicyIsNotManual() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        val before = engine.player.position

        repeat(50) { engine.queueManualMove(Direction.NORTH) }
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        engine.update(1f)

        assertEquals(before, engine.player.position)
    }

    @Test
    fun restart_withSameSeed_isDeterministic() {
        val a = GameEngine(testPreset(), seed)
        val b = GameEngine(testPreset(), seed)

        assertEquals(a.maze.start, b.maze.start)
        assertEquals(a.maze.exit, b.maze.exit)
        assertEquals(
            a.npcs.map { it.position },
            b.npcs.map { it.position }
        )
        assertEquals(
            a.spawnedPowerUps.map { it.position to it.type },
            b.spawnedPowerUps.map { it.position to it.type }
        )
    }

    @Test
    fun restart_withDifferentSeed_matchesExpectedDeterministicState() {
        val engine = GameEngine(testPreset(), seed)
        val expected = GameEngine(testPreset(), seed + 1)

        engine.restart(seed + 1)

        assertTrue(expected.maze.copyCells().contentEquals(engine.maze.copyCells()))
        assertEquals(expected.npcs.map { it.position }, engine.npcs.map { it.position })
        assertEquals(
            expected.spawnedPowerUps.map { it.position to it.type },
            engine.spawnedPowerUps.map { it.position to it.type }
        )
    }

    @Test
    fun initialSpawn_containsAtLeastOnePerConfiguredType() {
        val engine = GameEngine(testPreset(), seed)
        val spawnedTypes = engine.spawnedPowerUps.map { it.type }.toSet()
        assertEquals(PowerUpType.entries.toSet(), spawnedTypes)
    }

    @Test
    fun mediumPowerUps_despawnAfterLifetime() {
        val medium = testPreset(
            powerUpPickupLifetimeSeconds = 10f,
            powerUpRespawnIntervalSeconds = null
        )
        val engine = GameEngine(medium, seed)
        val before = engine.spawnedPowerUps.size

        engine.update(10.1f)

        assertTrue(before > 0)
        assertEquals(0, engine.spawnedPowerUps.size)
    }

    @Test
    fun easyPowerUps_respawnEveryInterval() {
        val easy = testPreset(
            powerUpPickupLifetimeSeconds = 0f,
            powerUpRespawnIntervalSeconds = 15f
        )
        val engine = GameEngine(easy, seed)
        val before = engine.spawnedPowerUps.size

        engine.update(15.1f)

        assertTrue(engine.spawnedPowerUps.size > before)
    }

    @Test
    fun hudState_reflectsPresetNpcSpeedAfterDifficultyChange() {
        val engine = GameEngine(testPreset(), seed)
        engine.setDifficulty(DifficultyPresets.EASY)
        assertEquals(DifficultyPresets.EASY.npcMovesPerSecond, engine.hudState().npcSpeed, 0.0001f)

        engine.setDifficulty(DifficultyPresets.MEDIUM)
        assertEquals(DifficultyPresets.MEDIUM.npcMovesPerSecond, engine.hudState().npcSpeed, 0.0001f)
    }

    @Test
    fun collectingSpeedUp_updatesHudSpeedAndExpires() {
        val engine = GameEngine(testPreset(), seed)
        val baseSpeed = engine.hudState().playerSpeed
        collectPowerUp(engine, PowerUpType.SPEED_UP)

        assertEquals(baseSpeed * 2f, engine.hudState().playerSpeed, 0.0001f)

        engine.update(PowerUpType.SPEED_UP.metadata.defaultDurationSeconds + 0.1f)
        assertEquals(baseSpeed, engine.hudState().playerSpeed, 0.0001f)
    }

    @Test
    fun invisibility_preventsLossOnNpcCollisionWhileActive() {
        val engine = GameEngine(testPreset(npcCount = 1), seed)
        collectPowerUp(engine, PowerUpType.INVISIBILITY)
        engine.player.position = engine.npcs.first().position

        engine.update(1f)

        assertNotEquals(GameStatus.LOSE, engine.status)
    }

    @Test
    fun freeze_preventsLossOnNpcCollisionWhileActive() {
        val engine = GameEngine(testPreset(npcCount = 1), seed)
        collectPowerUp(engine, PowerUpType.FREEZE)
        engine.player.position = engine.npcs.first().position

        engine.update(1f)

        assertNotEquals(GameStatus.LOSE, engine.status)
    }

    @Test
    fun blast_removesWallsAroundPlayerCell() {
        val blastPreset = testPreset(
            mazeWidth = 6,
            mazeHeight = 6,
            initialPowerUpTypes = listOf(PowerUpType.BLAST)
        )
        val engine = GameEngine(blastPreset, seed)
        val blast = engine.spawnedPowerUps.first { it.type == PowerUpType.BLAST }
        movePlayerTo(engine, blast.position)
        val center = engine.player.position

        Direction.entries.forEach { direction ->
            val neighbor = center.moved(direction)
            if (engine.maze.inBounds(neighbor)) {
                assertFalse(engine.maze.hasWall(center, direction))
            }
        }
    }

    @Test
    fun teleport_movesPlayerToDifferentCellWithExitPath() {
        val teleportPreset = testPreset(
            initialPowerUpTypes = listOf(PowerUpType.TELEPORT)
        )
        val engine = GameEngine(teleportPreset, seed)
        val from = engine.player.position
        val teleport = engine.spawnedPowerUps.first { it.type == PowerUpType.TELEPORT }

        movePlayerTo(engine, teleport.position)

        assertNotEquals(from, engine.player.position)
        assertTrue(engine.navigator.bfsPath(engine.player.position, engine.maze.exit).isNotEmpty())
    }

    @Test
    fun ghostMode_activatesTimedEffectForThreeSeconds() {
        val engine = GameEngine(testPreset(), seed)
        collectPowerUp(engine, PowerUpType.GHOST_MODE)

        val active = engine.activePowerUps.first { it.type == PowerUpType.GHOST_MODE }
        assertEquals(
            PowerUpType.GHOST_MODE.metadata.defaultDurationSeconds,
            (active.endsAtSeconds ?: 0f) - active.startedAtSeconds,
            0.0001f
        )
        assertEquals(3f, PowerUpType.GHOST_MODE.metadata.defaultDurationSeconds, 0.0001f)
    }

    @Test
    fun ghostMode_letsPlayerWalkThroughWalls() {
        val engine = GameEngine(testPreset(), seed)
        // Find a wall adjacent to a known cell and try to move through it.
        val origin = findCellWithAnyWall(engine.maze)
        engine.player.position = origin
        val blockedDirection = findBlockedDirection(engine.maze, origin)

        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        val stepsBefore = engine.steps

        // Without ghost mode the move is blocked.
        engine.queueManualMove(blockedDirection)
        engine.update(1f / engine.difficulty.playerMovesPerSecond + 0.001f)
        assertEquals(origin, engine.player.position)
        assertEquals(stepsBefore, engine.steps)

        // With ghost mode active, the same blocked move succeeds.
        collectPowerUp(engine, PowerUpType.GHOST_MODE)
        engine.player.position = origin
        val stepsBeforeGhost = engine.steps
        engine.queueManualMove(blockedDirection)
        engine.update(1f / engine.difficulty.playerMovesPerSecond + 0.001f)
        assertEquals(origin.moved(blockedDirection), engine.player.position)
        assertTrue(engine.steps > stepsBeforeGhost)
    }

    @Test
    fun ghostMode_expiresAfterDurationAndWallsBlockAgain() {
        val engine = GameEngine(testPreset(), seed)
        val origin = findCellWithAnyWall(engine.maze)
        val blockedDirection = findBlockedDirection(engine.maze, origin)

        collectPowerUp(engine, PowerUpType.GHOST_MODE)
        engine.player.position = origin
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        // Advance well past the 3-second ghost duration.
        engine.update(PowerUpType.GHOST_MODE.metadata.defaultDurationSeconds + 0.5f)
        engine.player.position = origin

        val stepsBefore = engine.steps
        engine.queueManualMove(blockedDirection)
        engine.update(1f / engine.difficulty.playerMovesPerSecond + 0.001f)
        assertEquals(origin, engine.player.position)
        assertEquals(stepsBefore, engine.steps)
    }

    @Test
    fun ghostMode_doesNotPreventLossOnNpcCollision() {
        val engine = GameEngine(testPreset(npcCount = 1), seed)
        collectPowerUp(engine, PowerUpType.GHOST_MODE)
        engine.player.position = engine.npcs.first().position

        engine.update(1f)

        assertEquals(GameStatus.LOSE, engine.status)
    }

    @Test
    fun player_animationFrameAdvancesOnSuccessfulMove() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        // Find any direction the player can move from its current cell.
        val direction = Direction.entries.first {
            engine.maze.canMove(engine.player.position, it)
        }
        val before = engine.player.animationFrame

        engine.queueManualMove(direction)
        engine.update(1f / engine.difficulty.playerMovesPerSecond + 0.001f)

        assertEquals((before + 1) % GameEngine.ANIMATION_FRAMES, engine.player.animationFrame)
        assertTrue(engine.player.lastMoveAtSeconds > 0f)
    }

    @Test
    fun player_animationFrameCyclesAcrossThreeMoves() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        val origin = engine.player.position
        // Drive the player along the BFS path to the exit so each move succeeds.
        val moveInterval = 1f / engine.difficulty.playerMovesPerSecond
        val frames = mutableListOf<Int>()
        var current = origin
        repeat(3) {
            val path = engine.navigator.bfsPath(current, engine.maze.exit)
            val next = path[1]
            val direction = requireNotNull(
                Direction.fromDelta(next.x - current.x, next.y - current.y)
            )
            engine.queueManualMove(direction)
            engine.update(moveInterval + 0.001f)
            frames += engine.player.animationFrame
            current = engine.player.position
        }

        assertEquals(listOf(1, 0, 1), frames)
    }

    @Test
    fun player_animationFrameDoesNotAdvanceOnBlockedMove() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        // Find a direction that is blocked by a wall (or the maze boundary).
        val blocked = Direction.entries.firstOrNull {
            !engine.maze.canMove(engine.player.position, it)
        } ?: return
        val frameBefore = engine.player.animationFrame
        val lastMoveBefore = engine.player.lastMoveAtSeconds

        engine.queueManualMove(blocked)
        engine.update(1f / engine.difficulty.playerMovesPerSecond + 0.001f)

        assertEquals(frameBefore, engine.player.animationFrame)
        assertEquals(lastMoveBefore, engine.player.lastMoveAtSeconds)
    }

    @Test
    fun npc_animationFrameAdvancesWhenItMoves() {
        // Use a preset with a single NPC and a player far away so DirectChase
        // produces a deterministic move every tick.
        val engine = GameEngine(testPreset(npcCount = 1), seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        val npc = engine.npcs.first()
        val frameBefore = npc.animationFrame
        val posBefore = npc.position

        // Advance enough time for the NPC to move at least once.
        engine.update(2f / engine.difficulty.npcMovesPerSecond + 0.001f)

        // Either the NPC moved (and animation advanced) or it had no valid move;
        // in the moved case the frame must have advanced.
        if (npc.position != posBefore) {
            assertNotEquals(frameBefore, npc.animationFrame)
            assertTrue(npc.lastMoveAtSeconds > 0f)
        }
    }

    @Test
    fun invisibility_doesNotFreezeNpcs() {
        val engine = GameEngine(testPreset(npcCount = 1), seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        collectPowerUp(engine, PowerUpType.INVISIBILITY)
        val before = engine.npcs.first().position

        // Run several NPC ticks while INVISIBILITY is active.
        engine.update(3f / engine.difficulty.npcMovesPerSecond + 0.001f)

        assertNotEquals(before, engine.npcs.first().position)
    }

    @Test
    fun freeze_freezesNpcs() {
        val engine = GameEngine(testPreset(npcCount = 1), seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        collectPowerUp(engine, PowerUpType.FREEZE)
        val before = engine.npcs.first().position

        // FREEZE duration is 5s; tick well within that window.
        engine.update(2f)

        assertEquals(before, engine.npcs.first().position)
    }

    private fun findCellWithAnyWall(maze: Maze): GridPos {
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos == maze.start || pos == maze.exit) continue
                val blocked = Direction.entries.firstOrNull { direction ->
                    val next = pos.moved(direction)
                    maze.inBounds(next) &&
                        next != maze.exit &&
                        next != maze.start &&
                        maze.hasWall(pos, direction)
                }
                if (blocked != null) return pos
            }
        }
        throw IllegalStateException("No interior wall found in maze ${maze.width}x${maze.height}")
    }

    private fun findBlockedDirection(maze: Maze, origin: GridPos): Direction {
        return Direction.entries.first { direction ->
            val next = origin.moved(direction)
            maze.inBounds(next) &&
                next != maze.exit &&
                next != maze.start &&
                maze.hasWall(origin, direction)
        }
    }

    private fun collectPowerUp(engine: GameEngine, type: PowerUpType) {
        val pickup = engine.spawnedPowerUps.first { it.type == type }
        movePlayerTo(engine, pickup.position)
    }

    private fun movePlayerTo(engine: GameEngine, target: GridPos) {
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        // Temporarily remove NPCs from the engine while navigating so that
        // GameEngine.update()'s in-loop end-condition checks cannot transition
        // to LOSE due to NPC behaviour driven by maze layout/seed variations
        // (e.g. start-corner randomization). NPCs are restored after navigation
        // so subsequent assertions (collision-immunity tests) still exercise
        // them.
        val savedNpcs = engine.npcs.toList()
        engine.npcs.clear()
        try {
            // Use a small timestep tied to the player move interval so navigation
            // does not unintentionally elapse enough simulated time to trigger
            // unrelated time-based mechanics (despawn/effect expiry/respawn).
            val moveInterval = 1f / engine.difficulty.playerMovesPerSecond
            val tickStep = moveInterval / 4f
            var totalTicks = 0
            // Re-plan a fresh BFS each step so picking up effects mid-walk
            // (e.g. TELEPORT relocating the player) does not strand the helper.
            while (engine.player.position != target && totalTicks < MAX_TOTAL_TICKS) {
                val path = engine.navigator.bfsPath(engine.player.position, target)
                require(path.isNotEmpty()) {
                    "No path from ${engine.player.position} to $target"
                }
                val next = path[1]
                val direction = Direction.fromDelta(
                    next.x - engine.player.position.x,
                    next.y - engine.player.position.y
                )
                requireNotNull(direction)
                engine.queueManualMove(direction)
                val before = engine.player.position
                var stepTicks = 0
                while (engine.player.position == before && stepTicks < MAX_TICKS_PER_STEP) {
                    engine.update(tickStep)
                    if (engine.status != GameStatus.RUNNING) {
                        throw IllegalStateException("Engine left RUNNING while navigating to target.")
                    }
                    stepTicks += 1
                }
                check(engine.player.position != before) {
                    "Player did not move from $before within $MAX_TICKS_PER_STEP ticks."
                }
                totalTicks += stepTicks
            }
            check(engine.player.position == target) {
                "Failed to reach $target within $MAX_TOTAL_TICKS ticks."
            }
        } finally {
            engine.npcs.addAll(savedNpcs)
        }
    }

    private companion object {
        private const val MAX_TICKS_PER_STEP = 64
        private const val MAX_TOTAL_TICKS = 4096
    }

    private fun testPreset(
        mazeWidth: Int = 14,
        mazeHeight: Int = 20,
        npcCount: Int = 0,
        powerUpPickupLifetimeSeconds: Float = 600f,
        powerUpRespawnIntervalSeconds: Float? = null,
        initialPowerUpTypes: List<PowerUpType> = PowerUpType.entries
    ): DifficultyPreset {
        return DifficultyPreset(
            name = "Test",
            mazeWidth = mazeWidth,
            mazeHeight = mazeHeight,
            npcCount = npcCount,
            playerMovesPerSecond = 6f,
            npcMovesPerSecond = 1f,
            npcVisionRange = 4,
            powerUpPickupLifetimeSeconds = powerUpPickupLifetimeSeconds,
            powerUpRespawnIntervalSeconds = powerUpRespawnIntervalSeconds,
            initialPowerUpTypes = initialPowerUpTypes
        )
    }
}
