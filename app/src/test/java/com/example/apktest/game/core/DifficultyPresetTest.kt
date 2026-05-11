package com.example.apktest.game.core

import org.junit.Assert.assertEquals
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
}
