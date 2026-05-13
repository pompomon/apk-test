package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color

/**
 * Pixel-art tile data used by [MazeRenderer] to texture maze walls with a
 * brown/green dungeon brick look (plain stone with sparse moss / bush
 * variants).
 *
 * Tiles are 4x8 character grids (4 rows = wall thickness, 8 cols = wall
 * length). [MazeRenderer] does its own per-pixel sampling — it picks a variant
 * per wall segment via [variantIndex], then draws one [com.badlogic.gdx.graphics.glutils.ShapeRenderer]
 * rect per non-transparent character. To keep the per-frame path cheap, the
 * resolved per-variant colours are exposed via [variantColors] alongside the
 * raw [variants] patterns so the renderer can build a static wall-geometry
 * cache once per maze instead of doing map lookups per pixel per frame.
 *
 * Wall thickness in world units is owned by the renderer (see
 * `MazeRenderer.WALL_THICKNESS`, currently 0.18 of a cell).
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

    /**
     * Pre-resolved [Color] per (variant, row, col), with `null` for transparent
     * characters. Indexed as `variantColors[variantIndex][row][col]`. Letting
     * the renderer skip map lookups makes the cache-build step branch-free per
     * pixel.
     */
    val variantColors: Array<Array<Array<Color?>>> = Array(variants.size) { vi ->
        val pat = variants[vi]
        Array(pat.size) { r ->
            val row = pat[r]
            Array(row.length) { c ->
                val ch = row[c]
                if (ch == ' ' || ch == '0') null else basePalette[ch]
            }
        }
    }

    /**
     * Named direction keys for [variantIndex]. Kept as small integer constants
     * so the hash stays an `Int` op, but call sites are self-documenting and
     * stable: changing a value here would change every maze's wall texture
     * (which is fine, but should be intentional).
     */
    const val DIR_NORTH = 0
    const val DIR_WEST = 1
    const val DIR_SOUTH = 2
    const val DIR_EAST = 3

    init {
        PixelSpriteRenderer.validatePattern(stoneTile, name = "stoneTile")
        PixelSpriteRenderer.validatePattern(stoneMossTile, name = "stoneMossTile")
        PixelSpriteRenderer.validatePattern(stoneBushTile, name = "stoneBushTile")
    }

    /**
     * Deterministic variant chooser keyed on (x, y, dir). Returns the index in
     * [variants]. ~80% plain stone, ~12% moss, ~8% bush. Same maze always
     * produces the same texture pattern (no per-frame allocation, no flicker).
     * Use the [DIR_NORTH] / [DIR_WEST] / [DIR_SOUTH] / [DIR_EAST] constants for
     * the `dir` argument.
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
