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

    private fun collectPowerUp(engine: GameEngine, type: PowerUpType) {
        val pickup = engine.spawnedPowerUps.first { it.type == type }
        movePlayerTo(engine, pickup.position)
    }

    private fun movePlayerTo(engine: GameEngine, target: GridPos) {
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        val path = engine.navigator.bfsPath(engine.player.position, target)
        require(path.isNotEmpty()) { "No path from ${engine.player.position} to $target" }
        for (index in 1 until path.size) {
            val from = path[index - 1]
            val to = path[index]
            val direction = Direction.fromDelta(to.x - from.x, to.y - from.y)
            requireNotNull(direction)
            engine.queueManualMove(direction)
            engine.update(1f)
            if (engine.status != GameStatus.RUNNING) {
                throw IllegalStateException("Engine left RUNNING while navigating to target.")
            }
        }
    }

    private fun testPreset(
        mazeWidth: Int = 14,
        mazeHeight: Int = 20,
        npcCount: Int = 0,
        powerUpPickupLifetimeSeconds: Float = 10f,
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
