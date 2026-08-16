package com.example.apktest.game.render

import kotlin.math.roundToInt

/**
 * Android-friendly Adventurer icon data shared with the libGDX renderer.
 */
object AdventurerIcons {
    fun pattern(): Array<String> = Sprites.adventurerIdle

    private val androidColors: Map<Char, Int> = Sprites.adventurerPalette()
        .mapValues { (_, color) -> argb(color.r, color.g, color.b) }

    fun colors(): Map<Char, Int> = androidColors

    private fun argb(r: Float, g: Float, b: Float): Int {
        val red = (r.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val green = (g.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val blue = (b.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
    }
}
