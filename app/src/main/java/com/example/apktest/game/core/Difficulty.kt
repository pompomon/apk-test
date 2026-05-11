package com.example.apktest.game.core

data class DifficultyPreset(
    val name: String,
    val mazeWidth: Int,
    val mazeHeight: Int,
    val npcCount: Int,
    val playerMovesPerSecond: Float,
    val npcMovesPerSecond: Float,
    val npcVisionRange: Int,
    val balanceRule: NpcSpeedBalanceRule = NpcSpeedBalanceRule.NONE,
    val powerUpPickupLifetimeSeconds: Float = 10f,
    val powerUpRespawnIntervalSeconds: Float? = null,
    val initialPowerUpTypes: List<PowerUpType> = PowerUpType.entries
) {
    init {
        if (balanceRule == NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER) {
            require(npcMovesPerSecond < playerMovesPerSecond) {
                "NPC speed ($npcMovesPerSecond) must be lower than player speed ($playerMovesPerSecond) for $name."
            }
        }
    }
}

enum class NpcSpeedBalanceRule {
    NONE,
    NPC_MUST_BE_SLOWER_THAN_PLAYER
}

object DifficultyPresets {
    val EASY = DifficultyPreset(
        name = "Easy",
        mazeWidth = 14,
        mazeHeight = 20,
        npcCount = 1,
        playerMovesPerSecond = 4f,
        npcMovesPerSecond = 1f,
        npcVisionRange = 4,
        balanceRule = NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER,
        powerUpPickupLifetimeSeconds = 0f,
        powerUpRespawnIntervalSeconds = 15f
    )

    val MEDIUM = DifficultyPreset(
        name = "Medium",
        mazeWidth = 18,
        mazeHeight = 28,
        npcCount = 2,
        playerMovesPerSecond = 4.5f,
        npcMovesPerSecond = 1.5f,
        npcVisionRange = 5,
        balanceRule = NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER,
        powerUpPickupLifetimeSeconds = 10f,
        powerUpRespawnIntervalSeconds = null
    )

    val HARD = DifficultyPreset(
        name = "Hard",
        mazeWidth = 24,
        mazeHeight = 34,
        npcCount = 3,
        playerMovesPerSecond = 5f,
        npcMovesPerSecond = 3.6f,
        npcVisionRange = 6,
        balanceRule = NpcSpeedBalanceRule.NONE,
        powerUpPickupLifetimeSeconds = 5f,
        powerUpRespawnIntervalSeconds = null
    )

    val all = listOf(EASY, MEDIUM, HARD)

    fun byName(name: String): DifficultyPreset = all.firstOrNull { it.name == name } ?: MEDIUM
}
