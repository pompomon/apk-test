package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color

/**
 * Pixel-art floor tile rendered by [MazeRenderer] beneath the maze. The
 * tile is a 4×4 grid that tiles seamlessly: adjacent cells use the same
 * pattern indexed by global pixel coordinates `(cellX * size + col,
 * cellY * size + row)`, so the brick-mortar joints line up across cell
 * borders. The palette is intentionally low-saturation and darker than
 * both the warm/green wall bricks and any sprite/power-up colour so the
 * floor recedes visually.
 */
object FloorTextures {
    /** Side length (in pattern pixels) of one tile. Tiles seamlessly. */
    const val TILE_SIZE = 4

    val base: Color = Color(0.10f, 0.12f, 0.16f, 1f)
    val accent: Color = Color(0.16f, 0.18f, 0.22f, 1f)
    val highlight: Color = Color(0.13f, 0.15f, 0.19f, 1f)

    /**
     * 4×4 tile read top-down. 'b'=base, 'a'=accent dot, 'h'=mid highlight.
     * The dot in the corner becomes a repeating "stud" across the floor;
     * the diagonal `h` cells suggest subtle bevelling without competing
     * with sprite shapes for the eye.
     */
    val pattern: Array<String> = arrayOf(
        "abbb",
        "bhbb",
        "bbbh",
        "bbba"
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
