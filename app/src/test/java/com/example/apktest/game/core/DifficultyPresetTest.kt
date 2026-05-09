package com.example.apktest.game.core

import org.junit.Assert.assertTrue
import org.junit.Test

class DifficultyPresetTest {
    @Test
    fun easyAndMediumKeepNpcSlowerThanPlayer() {
        assertTrue(DifficultyPresets.EASY.npcMovesPerSecond < DifficultyPresets.EASY.playerMovesPerSecond)
        assertTrue(DifficultyPresets.MEDIUM.npcMovesPerSecond < DifficultyPresets.MEDIUM.playerMovesPerSecond)
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
