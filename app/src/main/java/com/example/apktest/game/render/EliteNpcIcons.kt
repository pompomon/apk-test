package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.example.apktest.game.core.EliteNpcModifier
import kotlin.math.roundToInt

/**
 * The badge occupies the lower-right quarter of the NPC, leaving its policy
 * tint and face readable. Both renderers replay the same top-down, normalized
 * rectangles; only libGDX inverts Y. All returned arrays are shared, read-only.
 */
object EliteNpcIcons {
    const val RECT_STRIDE = 4
    const val LEFT = 0
    const val TOP = 1
    const val RIGHT = 2
    const val BOTTOM = 3
    const val OUTLINE_COLOR = 0
    const val ACCENT_COLOR = 1
    private const val BADGE_ORIGIN = 0.5f
    private const val BADGE_SIZE = 0.5f

    class Geometry internal constructor(
        val rects: FloatArray,
        val colorIndices: IntArray
    )

    private val patterns = Array(EliteNpcModifier.entries.size) {
        buildPattern(EliteNpcModifier.entries[it])
    }
    private val geometries = Array(patterns.size) { buildGeometry(patterns[it]) }
    private val gdxColors = Array(EliteNpcModifier.entries.size) {
        val (r, g, b) = EliteNpcModifier.entries[it].accentRgb
        arrayOf(Color(0.03f, 0.03f, 0.08f, 1f), Color(r, g, b, 1f))
    }
    private val androidColors = Array(gdxColors.size) { modifier ->
        IntArray(gdxColors[modifier].size) { index ->
            val color = gdxColors[modifier][index]
            (0xFF shl 24) or
                ((color.r * 255f).roundToInt() shl 16) or
                ((color.g * 255f).roundToInt() shl 8) or
                (color.b * 255f).roundToInt()
        }
    }

    fun patternFor(modifier: EliteNpcModifier): Array<String> = patterns[modifier.ordinal]
    fun geometryFor(modifier: EliteNpcModifier): Geometry = geometries[modifier.ordinal]
    fun gdxColorsFor(modifier: EliteNpcModifier): Array<Color> = gdxColors[modifier.ordinal]
    fun androidColorsFor(modifier: EliteNpcModifier): IntArray = androidColors[modifier.ordinal]

    private fun buildPattern(modifier: EliteNpcModifier): Array<String> = when (modifier) {
        EliteNpcModifier.TRACKER -> arrayOf(
            "000K000",
            "00KAK00",
            "0KAKAK0",
            "KAKAKAK",
            "0KAKAK0",
            "00KAK00",
            "000K000"
        )
    }

    private fun buildGeometry(pattern: Array<String>): Geometry {
        PixelSpriteRenderer.validatePattern(pattern, "elite badge")
        val count = pattern.sumOf { row -> row.count { it != '0' } }
        val rects = FloatArray(count * RECT_STRIDE)
        val colorIndices = IntArray(count)
        val pixelWidth = BADGE_SIZE / pattern[0].length
        val pixelHeight = BADGE_SIZE / pattern.size
        var index = 0
        for (row in pattern.indices) {
            for (col in pattern[row].indices) {
                val ch = pattern[row][col]
                if (ch == '0') continue
                val offset = index * RECT_STRIDE
                rects[offset + LEFT] = BADGE_ORIGIN + col * pixelWidth
                rects[offset + TOP] = BADGE_ORIGIN + row * pixelHeight
                rects[offset + RIGHT] = BADGE_ORIGIN + (col + 1) * pixelWidth
                rects[offset + BOTTOM] = BADGE_ORIGIN + (row + 1) * pixelHeight
                colorIndices[index++] = when (ch) {
                    'K' -> OUTLINE_COLOR
                    'A' -> ACCENT_COLOR
                    else -> error("Unknown elite badge pixel: $ch")
                }
            }
        }
        return Geometry(rects, colorIndices)
    }
}
