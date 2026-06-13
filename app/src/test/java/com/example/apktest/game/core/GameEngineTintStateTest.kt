package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTintStateTest {
    @Test
    fun playerTintQuery_tracksOnlyActiveTimedPowerUps() {
        val engine = GameEngine(testPreset(initialPowerUpTypes = emptyList()), seed = 1234L)

        engine.applyStartingPowerUp(PowerUpType.SPEED_UP)
        engine.applyStartingPowerUp(PowerUpType.BLAST)

        assertTrue(engine.isPlayerPowerUpTintActive(PowerUpType.SPEED_UP))
        assertFalse(engine.isPlayerPowerUpTintActive(PowerUpType.BLAST))
        assertFalse(engine.isPlayerPowerUpTintActive(PowerUpType.SHIELD))
    }

    @Test
    fun playerTintQuery_tracksMultipleTimedPowerUps() {
        val engine = GameEngine(testPreset(initialPowerUpTypes = emptyList()), seed = 1234L)

        engine.applyStartingPowerUp(PowerUpType.SPEED_UP)
        engine.applyStartingPowerUp(PowerUpType.SHIELD)

        assertTrue(engine.isPlayerPowerUpTintActive(PowerUpType.SPEED_UP))
        assertTrue(engine.isPlayerPowerUpTintActive(PowerUpType.SHIELD))
    }

    @Test
    fun npcMazeTint_tracksNpcInducedFreezeLifetime() {
        val engine = GameEngine(
            testPreset(
                npcCount = 1,
                initialPowerUpTypes = listOf(PowerUpType.FREEZE)
            ),
            seed = 1234L
        )
        val freezePickup = engine.spawnedPowerUps.first { it.type == PowerUpType.FREEZE }

        engine.simulateNpcArrivalForTest(0, freezePickup.position)

        assertEquals(PowerUpType.FREEZE, engine.npcMazeTintType)

        engine.clearNpcsForTest()
        engine.update(PowerUpType.FREEZE.metadata.defaultDurationSeconds + 0.01f)

        assertNull(engine.npcMazeTintType)
    }

    private fun testPreset(
        npcCount: Int = 0,
        initialPowerUpTypes: List<PowerUpType>
    ): DifficultyPreset = DifficultyPreset(
        name = "TintStateTest",
        mazeWidth = 14,
        mazeHeight = 20,
        npcCount = npcCount,
        playerMovesPerSecond = 6f,
        npcMovesPerSecond = 4f,
        npcVisionRange = 4,
        powerUpPickupLifetimeSeconds = 600f,
        powerUpExpirationStaggerSeconds = 0f,
        powerUpRespawnIntervalSeconds = null,
        initialPowerUpTypes = initialPowerUpTypes
    )
}
