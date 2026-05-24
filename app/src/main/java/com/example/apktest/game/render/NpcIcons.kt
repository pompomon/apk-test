package com.example.apktest.game.render

import com.example.apktest.game.core.NpcPolicyType

/**
 * Single source of truth for NPC sprite presentation outside the libGDX
 * renderer (currently: the legend dialog's [com.example.apktest.ui.NpcIconView]).
 *
 * The pixel pattern is shared with the in-game renderer ([Sprites.monsterIdle])
 * so the legend swatch is byte-for-byte the idle goblin frame. Per-policy
 * Android colors are derived from [NpcPolicyType.colorRgb] using the same
 * dark-shading factor as [Sprites.monsterPaletteFor], keeping the legend in
 * lock-step with what the player sees on the maze.
 */
object NpcIcons {
    // Shading factor must mirror PixelSprites.MONSTER_DARK_SHADE_FACTOR. We
    // duplicate the literal here (rather than exposing the constant) because
    // the libGDX Sprites object lives in a different module surface and
    // exposing internal palette math just for a 0.55f would invite drift.
    private const val DARK_SHADE_FACTOR = 0.55f

    /** Idle NPC pattern reused from the in-game renderer. */
    fun pattern(): Array<String> = Sprites.monsterIdle

    private val colorsByPolicy: Map<NpcPolicyType, Map<Char, Int>> =
        NpcPolicyType.entries.associateWith { buildColors(it) }

    /**
     * 0xAARRGGBB Android-friendly color map keyed by sprite character
     * (`'M'` body, `'D'` dark shading, `'W'` eye whites, `'E'` pupils),
     * matching [Sprites.monsterPaletteFor] for the same [type].
     */
    fun androidColorsFor(type: NpcPolicyType): Map<Char, Int> =
        colorsByPolicy.getValue(type)

    private fun buildColors(type: NpcPolicyType): Map<Char, Int> {
        val (r, g, b) = type.colorRgb
        val body = argb(r, g, b)
        val dark = argb(r * DARK_SHADE_FACTOR, g * DARK_SHADE_FACTOR, b * DARK_SHADE_FACTOR)
        return mapOf(
            'M' to body,
            'D' to dark,
            'W' to argb(0.96f, 0.96f, 0.98f),
            'E' to argb(0.05f, 0.05f, 0.08f)
        )
    }

    private fun argb(r: Float, g: Float, b: Float): Int {
        val rb = (r.coerceIn(0f, 1f) * 255f).toInt()
        val gb = (g.coerceIn(0f, 1f) * 255f).toInt()
        val bb = (b.coerceIn(0f, 1f) * 255f).toInt()
        return (0xFF shl 24) or (rb shl 16) or (gb shl 8) or bb
    }
}
