package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureConfigTest {

    @Test
    fun forDifficulty_returnsEasyValues() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.EASY)
        assertEquals(5, c.initialLives)
        assertEquals(5, c.totalMazes)
        assertEquals(1, c.extraNpcsPerMaze)
    }

    @Test
    fun forDifficulty_returnsMediumValues() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        assertEquals(3, c.initialLives)
        assertEquals(7, c.totalMazes)
        assertEquals(2, c.extraNpcsPerMaze)
    }

    @Test
    fun forDifficulty_returnsHardValues() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        assertEquals(1, c.initialLives)
        assertEquals(9, c.totalMazes)
        assertEquals(3, c.extraNpcsPerMaze)
    }

    @Test
    fun npcCountForMaze_easyFirstMazeHasTwoNpcs() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.EASY)
        assertEquals(2, c.npcCountForMaze(1))
        assertEquals(3, c.npcCountForMaze(2))
        assertEquals(6, c.npcCountForMaze(5))
    }

    @Test
    fun npcCountForMaze_mediumFirstMazeHasThreeNpcs() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        assertEquals(3, c.npcCountForMaze(1))
        assertEquals(9, c.npcCountForMaze(7))
    }

    @Test
    fun npcCountForMaze_hardFirstMazeHasFourNpcs() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        assertEquals(4, c.npcCountForMaze(1))
        assertEquals(12, c.npcCountForMaze(9))
    }

    @Test
    fun forDifficulty_unknownPresetFallsBackToMedium() {
        val custom = DifficultyPresets.MEDIUM.copy(name = "Custom-Test-Preset")
        val c = AdventureConfig.forDifficulty(custom)
        assertEquals(3, c.initialLives)
        assertEquals(7, c.totalMazes)
        assertEquals(2, c.extraNpcsPerMaze)
        // Difficulty preserved so npcCountForMaze still uses the right preset.
        assertEquals(custom, c.difficulty)
    }

    /**
     * Pins the short-session design intent for Adventure runs: keeping
     * `totalMazes` bounded ensures a full run lands around 10–20 minutes
     * instead of the 30–60+ minute slog of the pre-rebalance values.
     * See docs/lessons-learned.md "Session-length tuning".
     */
    @Test
    fun totalMazesTargetShortRuns() {
        val easy = AdventureConfig.forDifficulty(DifficultyPresets.EASY)
        val medium = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        val hard = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        // Per-preset upper bounds give headroom for future small tweaks.
        assertTrue("Easy totalMazes ${easy.totalMazes} must be <= 6", easy.totalMazes <= 6)
        assertTrue("Medium totalMazes ${medium.totalMazes} must be <= 9", medium.totalMazes <= 9)
        assertTrue("Hard totalMazes ${hard.totalMazes} must be <= 11", hard.totalMazes <= 11)
        // Ladder ordering: harder presets have more mazes.
        assertTrue(
            "Easy mazes (${easy.totalMazes}) < Medium (${medium.totalMazes})",
            easy.totalMazes < medium.totalMazes
        )
        assertTrue(
            "Medium mazes (${medium.totalMazes}) < Hard (${hard.totalMazes})",
            medium.totalMazes < hard.totalMazes
        )
    }
}
