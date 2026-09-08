package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color

/**
 * Pixel-art floor tile rendered by [MazeRenderer] beneath the maze. The
 * tile is a 4×4 grid that tiles seamlessly: adjacent cells use the same
 * pattern indexed by global pixel coordinates `(cellX * size + col,
 * cellY * size + row)`, so the flecks remain aligned across cell borders.
 * The palette is intentionally low-saturation and darker than
 * both the warm/green wall bricks and any sprite/power-up colour so the
 * floor recedes visually.
 */
object FloorTextures {
    /** Side length (in pattern pixels) of one tile. Tiles seamlessly. */
    const val TILE_SIZE = 4

    val base: Color = Color(0.070f, 0.085f, 0.115f, 1f)
    val accent: Color = Color(0.085f, 0.103f, 0.136f, 1f)
    val highlight: Color = Color(0.077f, 0.094f, 0.125f, 1f)

    /**
     * 4×4 tile read top-down. 'b'=base, 'a'=accent dot, 'h'=mid highlight.
     * Staggered, low-contrast flecks avoid the repeating corner studs.
     * Keep twelve base pixels and two of each accent so the cached geometry
     * retains its four non-base rects per cell.
     */
    val pattern: Array<String> = arrayOf(
        "bbab",
        "hbbb",
        "bbbh",
        "babb"
    )

    /** Pre-resolved colour for `pattern[row][col]`: always one of [base], [accent], or [highlight]. */
    val pixelColors: Array<Array<Color>> = Array(TILE_SIZE) { row ->
        val rowPattern = pattern[row]
        Array(TILE_SIZE) { col ->
            when (rowPattern[col]) {
                'a' -> accent
                'h' -> highlight
                else -> base
            }
        }
    }
}
