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
    /**
     * Additional per-pickup delay (in seconds) applied to consecutive initial
     * power-up spawns so they don't all expire on the exact same tick. The
     * n-th initial pickup gets `lifetime + n * stagger` extra time on the map.
     * Ignored on presets with an infinite lifetime
     * (`powerUpPickupLifetimeSeconds <= 0f`).
     */
    val powerUpExpirationStaggerSeconds: Float = 10f,
    val powerUpRespawnIntervalSeconds: Float? = null,
    val initialPowerUpTypes: List<PowerUpType> = PowerUpType.entries,
    /**
     * Minimum Chebyshev-cell buffer around the direct start-to-exit path used
     * for initial NPC placement. `0` keeps NPCs off the direct path itself but
     * allows them next to it; larger values keep early spawns farther away.
     */
    val npcDirectPathSpawnBuffer: Int = 1,
    /**
     * Chebyshev (king-move) cell radius around the player within which
     * automated (non-`MANUAL`) player policies will divert one step to pick
     * up a nearby power-up, provided a walkable path of length ≤ this radius
     * exists and the detour is safe. `0` disables the behaviour (policies
     * will only collect a power-up by stepping onto it as part of their
     * normal route).
     */
    val automaticPickupRadius: Int = 1
) {
    init {
        if (balanceRule == NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER) {
            require(npcMovesPerSecond < playerMovesPerSecond) {
                "NPC speed ($npcMovesPerSecond) must be lower than player speed ($playerMovesPerSecond) for $name."
            }
        }
        require(automaticPickupRadius >= 0) {
            "automaticPickupRadius ($automaticPickupRadius) must be >= 0 for $name."
        }
        require(powerUpExpirationStaggerSeconds >= 0f) {
            "powerUpExpirationStaggerSeconds ($powerUpExpirationStaggerSeconds) must be >= 0 for $name."
        }
        require(npcDirectPathSpawnBuffer >= 0) {
            "npcDirectPathSpawnBuffer ($npcDirectPathSpawnBuffer) must be >= 0 for $name."
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
        mazeWidth = 12,
        mazeHeight = 16,
        npcCount = 1,
        playerMovesPerSecond = 4f,
        npcMovesPerSecond = 1f,
        npcVisionRange = 4,
        balanceRule = NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER,
        powerUpPickupLifetimeSeconds = 0f,
        powerUpRespawnIntervalSeconds = 12f,
        npcDirectPathSpawnBuffer = 2
    )

    val MEDIUM = DifficultyPreset(
        name = "Medium",
        mazeWidth = 16,
        mazeHeight = 24,
        npcCount = 2,
        playerMovesPerSecond = 4.5f,
        npcMovesPerSecond = 1.5f,
        npcVisionRange = 5,
        balanceRule = NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER,
        powerUpPickupLifetimeSeconds = 45f,
        powerUpRespawnIntervalSeconds = 20f,
        npcDirectPathSpawnBuffer = 1
    )

    val HARD = DifficultyPreset(
        name = "Hard",
        mazeWidth = 18,
        mazeHeight = 28,
        npcCount = 3,
        playerMovesPerSecond = 5f,
        npcMovesPerSecond = 3.6f,
        npcVisionRange = 6,
        balanceRule = NpcSpeedBalanceRule.NONE,
        powerUpPickupLifetimeSeconds = 40f,
        powerUpRespawnIntervalSeconds = 25f,
        npcDirectPathSpawnBuffer = 0
    )

    val all = listOf(EASY, MEDIUM, HARD)

    fun byName(name: String): DifficultyPreset = all.firstOrNull { it.name == name } ?: MEDIUM
}
