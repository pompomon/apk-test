package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color

/**
 * Pixel-art tile patterns used by [MazeRenderer] to texture the maze walls
 * with a brown stone-brick look, with occasional moss / bush variants for a
 * dungeon-y feel. Patterns are read top-to-bottom and rendered via
 * [PixelSpriteRenderer], so [MazeRenderer] does not need its own pattern
 * sampler.
 *
 * Each tile is an 8x4 grid: the wall segments drawn by the renderer are thin
 * strips along a cell edge (full length, ~15% of a cell thick), so a tile with
 * more columns than rows reads as a row of stone bricks. The renderer rotates
 * the patterns via the (centerX, centerY, size) call — vertical wall segments
 * pass the same 8x4 grid but with width/height swapped at the drawing call
 * site so the bricks read along the length of the wall in both orientations.
 */
object WallTextures {
    // Stone brick palette — brown/green dungeon look.
    val stoneDark = Color(0.18f, 0.12f, 0.08f, 1f)   // grout / shadow
    val stoneMid = Color(0.36f, 0.24f, 0.16f, 1f)    // brick body
    val stoneLight = Color(0.50f, 0.35f, 0.22f, 1f)  // highlight
    val mossDark = Color(0.15f, 0.32f, 0.12f, 1f)    // moss shadow
    val mossLight = Color(0.32f, 0.55f, 0.22f, 1f)   // moss / bush highlight

    private val basePalette: Map<Char, Color> = mapOf(
        '.' to stoneDark,
        'M' to stoneMid,
        'L' to stoneLight,
        'm' to mossDark,
        'B' to mossLight
    )

    // Plain stone bricks. Two-row brick pattern with offset between rows so
    // the joints are staggered. Top row is highlighted with 'L'.
    val stoneTile: Array<String> = arrayOf(
        "LLL.LLLL",
        "MMM.MMMM",
        "M.MMMM.M",
        "M.MMMM.M"
    )

    // Stone bricks with a small moss patch in the lower-left corner.
    val stoneMossTile: Array<String> = arrayOf(
        "LLL.LLLL",
        "MMM.MMMM",
        "m.MMMM.M",
        "mBMMMM.M"
    )

    // Stone bricks with a small bush growing on top in the middle.
    val stoneBushTile: Array<String> = arrayOf(
        "LLLBBLLL",
        "MMmBBmMM",
        "M.MMMM.M",
        "M.MMMM.M"
    )

    val variants: Array<Array<String>> = arrayOf(stoneTile, stoneMossTile, stoneBushTile)

    val palette: Map<Char, Color> = basePalette

    init {
        PixelSpriteRenderer.validatePattern(stoneTile, name = "stoneTile")
        PixelSpriteRenderer.validatePattern(stoneMossTile, name = "stoneMossTile")
        PixelSpriteRenderer.validatePattern(stoneBushTile, name = "stoneBushTile")
    }

    /**
     * Deterministic variant chooser keyed on (x, y, dir). Returns the index in
     * [variants]. ~80% plain stone, ~12% moss, ~8% bush. Same maze always
     * produces the same texture pattern (no per-frame allocation, no flicker).
     */
    fun variantIndex(x: Int, y: Int, dir: Int): Int {
        // Splitmix-style integer hash so adjacent cells don't correlate.
        var h = (x * 73856093) xor (y * 19349663) xor (dir * 83492791)
        h = h xor (h ushr 13)
        h *= 1274126177
        h = h xor (h ushr 16)
        val bucket = (h and 0x7FFFFFFF) % 100
        return when {
            bucket < 80 -> 0   // stoneTile
            bucket < 92 -> 1   // stoneMossTile
            else -> 2          // stoneBushTile
        }
    }
}
