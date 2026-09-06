package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.example.apktest.game.core.NpcPolicyType
import kotlin.math.roundToInt

/**
 * Cached base-policy presentation for libGDX and the legend dialog's
 * [com.example.apktest.ui.NpcIconView]. [EliteNpcIcons] adds an independent badge
 * without replacing these policy colors.
 *
 * The pixel pattern is shared with the in-game renderer
 * ([Sprites.monsterIdle]), and dark-shading colors are sourced from
 * [Sprites.monsterPaletteFor] so the legend swatch is byte-for-byte the idle
 * goblin frame, tinted to match what the player sees on the maze.
 */
object NpcIcons {
    /** Idle NPC pattern reused from the in-game renderer. */
    fun pattern(): Array<String> = Sprites.monsterIdle

    private val gdxColorsByPolicy = Array(NpcPolicyType.entries.size) {
        Sprites.monsterPaletteFor(NpcPolicyType.entries[it])
    }
    private val colorsByPolicy = Array(NpcPolicyType.entries.size) {
        buildColors(NpcPolicyType.entries[it])
    }

    fun gdxColorsFor(type: NpcPolicyType): Map<Char, Color> = gdxColorsByPolicy[type.ordinal]

    /**
     * 0xAARRGGBB Android-friendly color map keyed by sprite character
     * (`'M'` body, `'D'` dark shading, `'W'` eye whites, `'E'` pupils),
     * matching [Sprites.monsterPaletteFor] for the same [type].
     */
    fun androidColorsFor(type: NpcPolicyType): Map<Char, Int> =
        colorsByPolicy[type.ordinal]

    private fun buildColors(type: NpcPolicyType): Map<Char, Int> {
        val (r, g, b) = type.colorRgb
        val palette = Sprites.monsterPaletteFor(type)
        val d = palette.getValue('D')
        val body = argb(r, g, b)
        val dark = argb(d.r, d.g, d.b)
        return mapOf(
            'M' to body,
            'D' to dark,
            'W' to argb(0.96f, 0.96f, 0.98f),
            'E' to argb(0.05f, 0.05f, 0.08f)
        )
    }

    private fun argb(r: Float, g: Float, b: Float): Int {
        val rb = (r.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val gb = (g.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        val bb = (b.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (rb shl 16) or (gb shl 8) or bb
    }
}
