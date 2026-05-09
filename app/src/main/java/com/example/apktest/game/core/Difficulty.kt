package com.example.apktest.game.core

data class DifficultyPreset(
    val name: String,
    val mazeWidth: Int,
    val mazeHeight: Int,
    val npcCount: Int,
    val playerMovesPerSecond: Float,
    val npcMovesPerSecond: Float,
    val npcVisionRange: Int
)

object DifficultyPresets {
    val EASY = DifficultyPreset(
        name = "Easy",
        mazeWidth = 14,
        mazeHeight = 20,
        npcCount = 1,
        playerMovesPerSecond = 4f,
        npcMovesPerSecond = 2.2f,
        npcVisionRange = 4
    )

    val MEDIUM = DifficultyPreset(
        name = "Medium",
        mazeWidth = 18,
        mazeHeight = 28,
        npcCount = 2,
        playerMovesPerSecond = 4.5f,
        npcMovesPerSecond = 2.8f,
        npcVisionRange = 5
    )

    val HARD = DifficultyPreset(
        name = "Hard",
        mazeWidth = 24,
        mazeHeight = 34,
        npcCount = 3,
        playerMovesPerSecond = 5f,
        npcMovesPerSecond = 3.6f,
        npcVisionRange = 6
    )

    val all = listOf(EASY, MEDIUM, HARD)

    fun byName(name: String): DifficultyPreset = all.firstOrNull { it.name == name } ?: MEDIUM
}
