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
    // Per-type values are computed once per PowerUpType via the exhaustive
    // `when` builders below and then cached, so the per-frame in-game renderer
    // (MazeRenderer) and the legend icon view do not allocate new arrays /
    // perform map lookups on every call. The `when` expressions remain the
    // single source of truth and keep compile-time completeness checks.
    private val patternByType: Map<PowerUpType, Array<String>> =
        PowerUpType.entries.associateWith { buildPattern(it) }
    private val gdxColorByType: Map<PowerUpType, Color> =
        PowerUpType.entries.associateWith { buildGdxColor(it) }
    private val androidColorByType: Map<PowerUpType, Int> =
        PowerUpType.entries.associateWith { buildAndroidColor(it) }

    init {
        // Validate icon patterns once at class init so PixelSpriteRenderer.draw
        // and PowerUpIconView.onDraw stay allocation-free in the render loop.
        patternByType.forEach { (type, pattern) ->
            PixelSpriteRenderer.validatePattern(pattern, name = "powerUp:$type")
        }
    }

    fun patternFor(type: PowerUpType): Array<String> = patternByType.getValue(type)

    fun gdxColorFor(type: PowerUpType): Color = gdxColorByType.getValue(type)

    /**
     * 0xAARRGGBB color (Android-friendly) matching [gdxColorFor]. Kept in sync
     * with the libGDX palette above.
     */
    fun androidColorFor(type: PowerUpType): Int = androidColorByType.getValue(type)

    private fun buildPattern(type: PowerUpType): Array<String> = when (type) {
        PowerUpType.INVISIBILITY -> arrayOf(
            "00100",
            "01110",
            "11111",
            "01110",
            "00100"
        )
        PowerUpType.TELEPORT -> arrayOf(
            "11111",
            "10001",
            "10101",
            "10001",
            "11111"
        )
        PowerUpType.SPEED_UP -> arrayOf(
            "00110",
            "01110",
            "11111",
            "01110",
            "00110"
        )
        PowerUpType.FREEZE -> arrayOf(
            "10001",
            "01110",
            "11111",
            "01110",
            "10001"
        )
        PowerUpType.SHIELD -> arrayOf(
            "01110",
            "11111",
            "11011",
            "11111",
            "01110"
        )
        PowerUpType.SLOW_TIME -> arrayOf(
            "11111",
            "10100",
            "10111",
            "10001",
            "11111"
        )
        PowerUpType.MAGNET -> arrayOf(
            "10001",
            "10001",
            "11111",
            "11011",
            "10001"
        )
        PowerUpType.BLAST -> arrayOf(
            "10101",
            "11011",
            "11111",
            "11011",
            "10101"
        )
        // Ghost silhouette: rounded top, eye gaps, jagged bottom.
        PowerUpType.GHOST_MODE -> arrayOf(
            "01110",
            "11111",
            "10101",
            "11111",
            "10101"
        )
    }

    private fun buildGdxColor(type: PowerUpType): Color = when (type) {
        PowerUpType.INVISIBILITY -> Color(0.68f, 0.5f, 0.96f, 1f)
        PowerUpType.TELEPORT -> Color(0.25f, 0.86f, 0.96f, 1f)
        PowerUpType.SPEED_UP -> Color(1f, 0.91f, 0.3f, 1f)
        PowerUpType.FREEZE -> Color(0.63f, 0.9f, 1f, 1f)
        PowerUpType.SHIELD -> Color(0.35f, 0.62f, 1f, 1f)
        PowerUpType.SLOW_TIME -> Color(0.45f, 1f, 0.65f, 1f)
        PowerUpType.MAGNET -> Color(1f, 0.25f, 0.55f, 1f)
        PowerUpType.BLAST -> Color(1f, 0.45f, 0.2f, 1f)
        PowerUpType.GHOST_MODE -> Color(0.82f, 0.88f, 1f, 1f)
    }

    private fun buildAndroidColor(type: PowerUpType): Int = when (type) {
        PowerUpType.INVISIBILITY -> 0xFFAD80F5.toInt()
        PowerUpType.TELEPORT -> 0xFF40DBF5.toInt()
        PowerUpType.SPEED_UP -> 0xFFFFE84D.toInt()
        PowerUpType.FREEZE -> 0xFFA0E6FF.toInt()
        PowerUpType.SHIELD -> 0xFF599EFF.toInt()
        PowerUpType.SLOW_TIME -> 0xFF73FFA6.toInt()
        PowerUpType.MAGNET -> 0xFFFF408C.toInt()
        PowerUpType.BLAST -> 0xFFFF7333.toInt()
        PowerUpType.GHOST_MODE -> 0xFFD1E0FF.toInt()
    }
}
