package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyPresetTest {
    @Test
    fun easyAndMediumKeepNpcSlowerThanPlayer() {
        assertTrue(DifficultyPresets.EASY.npcMovesPerSecond < DifficultyPresets.EASY.playerMovesPerSecond)
        assertTrue(DifficultyPresets.MEDIUM.npcMovesPerSecond < DifficultyPresets.MEDIUM.playerMovesPerSecond)
    }

    @Test
    fun easyAndMediumNpcSpeedRatios_matchDesignTargets() {
        assertEquals(
            DifficultyPresets.EASY.playerMovesPerSecond / 4f,
            DifficultyPresets.EASY.npcMovesPerSecond,
            0.0001f
        )
        assertEquals(
            DifficultyPresets.MEDIUM.playerMovesPerSecond / 3f,
            DifficultyPresets.MEDIUM.npcMovesPerSecond,
            0.0001f
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun guardedDifficultyPreset_rejectsNpcSpeedAtOrAbovePlayerSpeed() {
        DifficultyPreset(
            name = "Invalid",
            mazeWidth = 10,
            mazeHeight = 10,
            npcCount = 1,
            playerMovesPerSecond = 4f,
            npcMovesPerSecond = 4f,
            npcVisionRange = 3,
            balanceRule = NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun difficultyPreset_rejectsNegativeAutomaticPickupRadius() {
        DifficultyPreset(
            name = "BadRadius",
            mazeWidth = 10,
            mazeHeight = 10,
            npcCount = 1,
            playerMovesPerSecond = 4f,
            npcMovesPerSecond = 1f,
            npcVisionRange = 3,
            automaticPickupRadius = -1
        )
    }

    @Test
    fun defaultPresets_useAutomaticPickupRadiusOfOne() {
        assertEquals(1, DifficultyPresets.EASY.automaticPickupRadius)
        assertEquals(1, DifficultyPresets.MEDIUM.automaticPickupRadius)
        assertEquals(1, DifficultyPresets.HARD.automaticPickupRadius)
    }

    @Test
    fun mediumPowerUpPolicy_usesOneTwentySecondLifetimeAndRespawns() {
        assertEquals(120f, DifficultyPresets.MEDIUM.powerUpPickupLifetimeSeconds, 0.0001f)
        val respawn = DifficultyPresets.MEDIUM.powerUpRespawnIntervalSeconds
        assertNotNull("Medium should respawn power-ups so the map stays populated", respawn)
        // Exact value pins the preset intent — see Difficulty.kt MEDIUM.
        assertEquals(30f, respawn!!, 0.0001f)
        // Medium must remain harder than Easy (which respawns every 15s).
        val easyRespawn = DifficultyPresets.EASY.powerUpRespawnIntervalSeconds
        assertNotNull(easyRespawn)
        assertTrue(
            "Medium respawn interval should be slower (longer) than Easy",
            respawn > easyRespawn!!
        )
    }

    @Test
    fun easyKeepsInfinitePowerUpLifetime() {
        // Easy mode treats `0f` as infinite: items spawned on the map should
        // never auto-expire so players can take their time gathering them.
        assertEquals(0f, DifficultyPresets.EASY.powerUpPickupLifetimeSeconds, 0.0001f)
    }

    @Test
    fun hardUsesSixtyFiveSecondLifetime() {
        assertEquals(65f, DifficultyPresets.HARD.powerUpPickupLifetimeSeconds, 0.0001f)
    }

    @Test
    fun defaultPresets_useTenSecondExpirationStagger() {
        assertEquals(10f, DifficultyPresets.EASY.powerUpExpirationStaggerSeconds, 0.0001f)
        assertEquals(10f, DifficultyPresets.MEDIUM.powerUpExpirationStaggerSeconds, 0.0001f)
        assertEquals(10f, DifficultyPresets.HARD.powerUpExpirationStaggerSeconds, 0.0001f)
    }
}
