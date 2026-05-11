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
    val metadata: PowerUpMetadata
) {
    INVISIBILITY(
        label = "Invisibility",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 5f,
            iconId = "invisibility",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    TELEPORT(
        label = "Teleport",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.INSTANT,
            defaultDurationSeconds = 0f,
            iconId = "teleport",
            stackPolicy = PowerUpStackPolicy.IGNORE_IF_ACTIVE
        )
    ),
    SPEED_UP(
        label = "Speed-up",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 10f,
            iconId = "speed_up",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    FREEZE(
        label = "Freeze",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.TIMED,
            defaultDurationSeconds = 5f,
            iconId = "freeze",
            stackPolicy = PowerUpStackPolicy.REFRESH_DURATION
        )
    ),
    BLAST(
        label = "Blast",
        metadata = PowerUpMetadata(
            kind = PowerUpEffectKind.INSTANT,
            defaultDurationSeconds = 0f,
            iconId = "blast",
            stackPolicy = PowerUpStackPolicy.IGNORE_IF_ACTIVE
        )
    ),
    GHOST_MODE(
        label = "Ghost Mode",
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
