package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/**
 * Tiny 5×5 pixel-art font that covers exactly the glyphs needed by the
 * countdown and end-of-game overlays: digits 1/2/3, the letters used in
 * "YOU WIN!" and "GAME OVER", and an exclamation mark / space. Glyphs are
 * stored top-down for readability; [drawCentered] flips Y when emitting rects
 * because `ShapeRenderer` is Y-up.
 *
 * Drawn via `ShapeRenderer.ShapeType.Filled` rects (one per non-`'.'`
 * pixel) so the renderer can stay allocation-free and avoid pulling in a
 * `BitmapFont` / `SpriteBatch` just for two overlays.
 */
object PixelTextRenderer {
    private const val GLYPH_ROWS = 5
    private const val GLYPH_COLS = 5

    private val glyphs: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(
            ".###.",
            "#..##",
            "#.#.#",
            "##..#",
            ".###."
        ),
        '1' to arrayOf(
            "..#..",
            ".##..",
            "..#..",
            "..#..",
            ".###."
        ),
        '2' to arrayOf(
            ".###.",
            "#...#",
            "..##.",
            ".#...",
            "#####"
        ),
        '3' to arrayOf(
            "####.",
            "....#",
            ".###.",
            "....#",
            "####."
        ),
        'A' to arrayOf(
            ".###.",
            "#...#",
            "#####",
            "#...#",
            "#...#"
        ),
        'E' to arrayOf(
            "#####",
            "#....",
            "####.",
            "#....",
            "#####"
        ),
        'G' to arrayOf(
            ".###.",
            "#....",
            "#.###",
            "#...#",
            ".###."
        ),
        'I' to arrayOf(
            ".###.",
            "..#..",
            "..#..",
            "..#..",
            ".###."
        ),
        'M' to arrayOf(
            "#...#",
            "##.##",
            "#.#.#",
            "#...#",
            "#...#"
        ),
        'N' to arrayOf(
            "#...#",
            "##..#",
            "#.#.#",
            "#..##",
            "#...#"
        ),
        'O' to arrayOf(
            ".###.",
            "#...#",
            "#...#",
            "#...#",
            ".###."
        ),
        'R' to arrayOf(
            "####.",
            "#...#",
            "####.",
            "#.#..",
            "#..##"
        ),
        'U' to arrayOf(
            "#...#",
            "#...#",
            "#...#",
            "#...#",
            ".###."
        ),
        'V' to arrayOf(
            "#...#",
            "#...#",
            "#...#",
            ".#.#.",
            "..#.."
        ),
        'W' to arrayOf(
            "#...#",
            "#...#",
            "#.#.#",
            "##.##",
            "#...#"
        ),
        'Y' to arrayOf(
            "#...#",
            ".#.#.",
            "..#..",
            "..#..",
            "..#.."
        ),
        '!' to arrayOf(
            "..#..",
            "..#..",
            "..#..",
            ".....",
            "..#.."
        ),
        ' ' to arrayOf(
            ".....",
            ".....",
            ".....",
            ".....",
            "....."
        )
    )

    /**
     * Width of one glyph in world units when drawn at [glyphSize].
     */
    fun glyphWidth(glyphSize: Float): Float = glyphSize

    /**
     * Total width of [text] when drawn at the given size + gap. Useful for
     * centring without rendering twice.
     */
    fun textWidth(text: String, glyphSize: Float, glyphGap: Float): Float {
        if (text.isEmpty()) return 0f
        return text.length * glyphSize + (text.length - 1) * glyphGap
    }

    /**
     * Draw [text] centered at world coordinates ([centerX], [centerY]) with
     * each character coloured according to [colorForIndex] (defaults to a
     * single [fallbackColor]). Characters not present in [glyphs] render as
     * blanks. Caller is responsible for `shapes.begin/end` so multiple
     * calls can share a `ShapeRenderer` batch.
     */
    fun drawCentered(
        shapes: ShapeRenderer,
        text: String,
        centerX: Float,
        centerY: Float,
        glyphSize: Float,
        glyphGap: Float,
        fallbackColor: Color,
        colorForIndex: ((Int) -> Color)? = null
    ) {
        if (text.isEmpty()) return
        val totalWidth = textWidth(text, glyphSize, glyphGap)
        val originX = centerX - totalWidth / 2f
        val originY = centerY - glyphSize / 2f
        val pixelSize = glyphSize / GLYPH_COLS

        text.forEachIndexed { index, ch ->
            val glyph = glyphs[ch.uppercaseChar()] ?: glyphs[' ']!!
            val color = colorForIndex?.invoke(index) ?: fallbackColor
            shapes.color = color
            val glyphOriginX = originX + index * (glyphSize + glyphGap)
            for (row in 0 until GLYPH_ROWS) {
                val rowPattern = glyph[row]
                for (col in 0 until GLYPH_COLS) {
                    if (rowPattern[col] == '#') {
                        shapes.rect(
                            glyphOriginX + col * pixelSize,
                            // Patterns are top-down but ShapeRenderer is Y-up.
                            originY + (GLYPH_ROWS - row - 1) * pixelSize,
                            pixelSize,
                            pixelSize
                        )
                    }
                }
            }
        }
    }
}
