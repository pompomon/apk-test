package com.example.apktest.game.core

enum class PowerUpEffectKind {
    TIMED,
    INSTANT,
    PERMANENT
}

enum class PowerUpStackPolicy {
    REFRESH_DURATION,
    IGNORE_IF_ACTIVE,
    STACK
}

data class PowerUpMetadata(
    val kind: PowerUpEffectKind,
    val defaultDurationSeconds: Float,
    val iconId: String,
    val stackPolicy: PowerUpStackPolicy
)

enum class PowerUpType(
    val label: String,
    val description: String,
    val metadata: PowerUpMetadata
) {
    INVISIBILITY(
        label = "Invisibility",
        description = "Hides the player from NPCs and prevents capture while active.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 5f,
            iconId = "invisibility",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    TELEPORT(
        label = "Teleport",
        description = "Instantly relocates the player to a random walkable cell that still has a path to the exit.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.INSTANT,
            defaultDurationSeconds = 0f,
            iconId = "teleport",
            stackPolicy = PowerUpStackPolicy.IGNORE_IF_ACTIVE
        )
    ),
    SPEED_UP(
        label = "Speed-up",
        description = "Doubles the player's movement speed for a short duration.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 10f,
            iconId = "speed_up",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    FREEZE(
        label = "Freeze",
        description = "Freezes all NPCs in place; the player cannot be caught while active.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 5f,
            iconId = "freeze",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    SHIELD(
        label = "Shield",
        description = "Prevents capture while active; NPCs still move, see, and chase the player.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 5f,
            iconId = "shield",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    SLOW_TIME(
        label = "Slow Time",
        description = "Temporarily slows NPC movement without fully freezing them.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 6f,
            iconId = "slow_time",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    MAGNET(
        label = "Magnet",
        description = "Automatically collects nearby power-up pickups for a short time.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 8f,
            iconId = "magnet",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    BLAST(
        label = "Blast",
        description = "Instantly removes the walls around the player's current cell.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.INSTANT,
            defaultDurationSeconds = 0f,
            iconId = "blast",
            stackPolicy = PowerUpStackPolicy.IGNORE_IF_ACTIVE
        )
    ),
    GHOST_MODE(
        label = "Ghost Mode",
        description = "Lets the player walk through walls for 3 seconds. Does not prevent capture.",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 3f,
            iconId = "ghost_mode",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    )
}

data class SpawnedPowerUp(
    val type: PowerUpType,
    val position: GridPos,
    val spawnedAtSeconds: Float,
    val expiresAtSeconds: Float?
)

data class ActivePowerUpEffect(
    val type: PowerUpType,
    val startedAtSeconds: Float,
    val endsAtSeconds: Float?
)

data class ActivePowerUpSnapshot(
    val type: PowerUpType,
    val remainingSeconds: Float?
)
