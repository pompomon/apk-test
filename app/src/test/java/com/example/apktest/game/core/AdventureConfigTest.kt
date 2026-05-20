package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Test

class AdventureConfigTest {

    @Test
    fun forDifficulty_returnsEasyValues() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.EASY)
        assertEquals(5, c.initialLives)
        assertEquals(7, c.totalMazes)
        assertEquals(1, c.extraNpcsPerMaze)
    }

    @Test
    fun forDifficulty_returnsMediumValues() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        assertEquals(3, c.initialLives)
        assertEquals(11, c.totalMazes)
        assertEquals(2, c.extraNpcsPerMaze)
    }

    @Test
    fun forDifficulty_returnsHardValues() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        assertEquals(1, c.initialLives)
        assertEquals(13, c.totalMazes)
        assertEquals(3, c.extraNpcsPerMaze)
    }

    @Test
    fun npcCountForMaze_easyFirstMazeHasTwoNpcs() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.EASY)
        assertEquals(2, c.npcCountForMaze(1))
        assertEquals(3, c.npcCountForMaze(2))
        assertEquals(8, c.npcCountForMaze(7))
    }

    @Test
    fun npcCountForMaze_mediumFirstMazeHasThreeNpcs() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        assertEquals(3, c.npcCountForMaze(1))
        assertEquals(13, c.npcCountForMaze(11))
    }

    @Test
    fun npcCountForMaze_hardFirstMazeHasFourNpcs() {
        val c = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        assertEquals(4, c.npcCountForMaze(1))
        assertEquals(16, c.npcCountForMaze(13))
    }

    @Test
    fun forDifficulty_unknownPresetFallsBackToMedium() {
        val custom = DifficultyPresets.MEDIUM.copy(name = "Custom-Test-Preset")
        val c = AdventureConfig.forDifficulty(custom)
        assertEquals(3, c.initialLives)
        assertEquals(11, c.totalMazes)
        assertEquals(2, c.extraNpcsPerMaze)
        // Difficulty preserved so npcCountForMaze still uses the right preset.
        assertEquals(custom, c.difficulty)
    }
}
