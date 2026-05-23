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
    fun mediumPowerUpPolicy_usesFortyFiveSecondLifetimeAndRespawns() {
        assertEquals(45f, DifficultyPresets.MEDIUM.powerUpPickupLifetimeSeconds, 0.0001f)
        val respawn = DifficultyPresets.MEDIUM.powerUpRespawnIntervalSeconds
        assertNotNull("Medium should respawn power-ups so the map stays populated", respawn)
        // Exact value pins the preset intent — see Difficulty.kt MEDIUM.
        assertEquals(20f, respawn!!, 0.0001f)
        // Medium must remain harder than Easy (which respawns every 12s).
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
    fun easyRespawnInterval_isTwelveSeconds() {
        assertEquals(12f, DifficultyPresets.EASY.powerUpRespawnIntervalSeconds!!, 0.0001f)
    }

    @Test
    fun hardUsesFortySecondLifetime() {
        assertEquals(40f, DifficultyPresets.HARD.powerUpPickupLifetimeSeconds, 0.0001f)
    }

    @Test
    fun hardRespawnsPowerUps_soLongerSessionsStayPopulated() {
        // Hard previously had no respawn; the short-session rebalance enables
        // it so a 2–3 minute Hard run still sees a second wave of pickups.
        val respawn = DifficultyPresets.HARD.powerUpRespawnIntervalSeconds
        assertNotNull("Hard should respawn power-ups after the short-session rebalance", respawn)
        assertEquals(25f, respawn!!, 0.0001f)
        // Hard must remain harder than Medium (slower respawn).
        val mediumRespawn = DifficultyPresets.MEDIUM.powerUpRespawnIntervalSeconds!!
        assertTrue(
            "Hard respawn interval should be slower (longer) than Medium",
            respawn > mediumRespawn
        )
    }

    @Test
    fun defaultPresets_useTenSecondExpirationStagger() {
        assertEquals(10f, DifficultyPresets.EASY.powerUpExpirationStaggerSeconds, 0.0001f)
        assertEquals(10f, DifficultyPresets.MEDIUM.powerUpExpirationStaggerSeconds, 0.0001f)
        assertEquals(10f, DifficultyPresets.HARD.powerUpExpirationStaggerSeconds, 0.0001f)
    }

    /**
     * Pins the short-session design intent: every preset's maze cell count
     * must stay at or below an upper bound so a typical single-maze Classic
     * session lands around 2–3 minutes. The bounds give headroom for
     * future small tweaks without churning this test on every nudge. See
     * docs/lessons-learned.md "Session-length tuning".
     */
    @Test
    fun presets_targetShortSessions_cellCountsBounded() {
        // The generator rounds preset dimensions up to even before building
        // the maze, so mirror that here to keep this guard accurate even if
        // a future preset tweak picks odd dimensions.
        fun roundUpToEven(value: Int): Int = if (value % 2 == 0) value else value + 1
        fun cells(p: DifficultyPreset): Int =
            roundUpToEven(p.mazeWidth) * roundUpToEven(p.mazeHeight)
        val easyCells = cells(DifficultyPresets.EASY)
        val mediumCells = cells(DifficultyPresets.MEDIUM)
        val hardCells = cells(DifficultyPresets.HARD)
        assertTrue("Easy maze cell count $easyCells must be <= 256", easyCells <= 256)
        assertTrue("Medium maze cell count $mediumCells must be <= 480", mediumCells <= 480)
        assertTrue("Hard maze cell count $hardCells must be <= 600", hardCells <= 600)
        // Strict ordering keeps the difficulty ladder intact.
        assertTrue("Easy cells ($easyCells) must be < Medium ($mediumCells)", easyCells < mediumCells)
        assertTrue("Medium cells ($mediumCells) must be < Hard ($hardCells)", mediumCells < hardCells)
    }
}
