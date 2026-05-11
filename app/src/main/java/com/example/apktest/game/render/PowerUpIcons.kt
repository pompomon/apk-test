package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.example.apktest.game.core.PowerUpType

/**
 * Single source of truth for pixel-art power-up icons. Both the in-game
 * renderer ([MazeRenderer]) and the Android legend view share these definitions
 * so they stay in sync when new power-ups are added.
 *
 * Patterns are read top-to-bottom (Android-native ordering). The libGDX
 * renderer flips Y when drawing via [PixelSpriteRenderer].
 */
object PowerUpIcons {
    private val patternsByType: Map<PowerUpType, Array<String>> = mapOf(
        PowerUpType.INVISIBILITY to arrayOf(
            "00100",
            "01110",
            "11111",
            "01110",
            "00100"
        ),
        PowerUpType.TELEPORT to arrayOf(
            "11111",
            "10001",
            "10101",
            "10001",
            "11111"
        ),
        PowerUpType.SPEED_UP to arrayOf(
            "00110",
            "01110",
            "11111",
            "01110",
            "00110"
        ),
        PowerUpType.FREEZE to arrayOf(
            "10001",
            "01110",
            "11111",
            "01110",
            "10001"
        ),
        PowerUpType.BLAST to arrayOf(
            "10101",
            "11011",
            "11111",
            "11011",
            "10101"
        ),
        // Ghost silhouette: rounded top, eye gaps, jagged bottom.
        PowerUpType.GHOST_MODE to arrayOf(
            "01110",
            "11111",
            "10101",
            "11111",
            "10101"
        )
    )

    private val gdxColorByType: Map<PowerUpType, Color> = mapOf(
        PowerUpType.INVISIBILITY to Color(0.68f, 0.5f, 0.96f, 1f),
        PowerUpType.TELEPORT to Color(0.25f, 0.86f, 0.96f, 1f),
        PowerUpType.SPEED_UP to Color(1f, 0.91f, 0.3f, 1f),
        PowerUpType.FREEZE to Color(0.63f, 0.9f, 1f, 1f),
        PowerUpType.BLAST to Color(1f, 0.45f, 0.2f, 1f),
        PowerUpType.GHOST_MODE to Color(0.82f, 0.88f, 1f, 1f)
    )

    fun patternFor(type: PowerUpType): Array<String> =
        patternsByType[type] ?: error("Missing pattern for $type")

    fun gdxColorFor(type: PowerUpType): Color =
        gdxColorByType[type] ?: error("Missing color for $type")

    /**
     * 0xAARRGGBB color (Android-friendly) matching [gdxColorFor]. Kept in sync
     * with the libGDX palette above.
     */
    fun androidColorFor(type: PowerUpType): Int = when (type) {
        PowerUpType.INVISIBILITY -> 0xFFAD80F5.toInt()
        PowerUpType.TELEPORT -> 0xFF40DBF5.toInt()
        PowerUpType.SPEED_UP -> 0xFFFFE84D.toInt()
        PowerUpType.FREEZE -> 0xFFA0E6FF.toInt()
        PowerUpType.BLAST -> 0xFFFF7333.toInt()
        PowerUpType.GHOST_MODE -> 0xFFD1E0FF.toInt()
    }
}
