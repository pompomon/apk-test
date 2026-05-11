package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer

/**
 * Renders multi-color pixel-art sprites described by a square character grid
 * and a character → [Color] palette. Each character corresponds to one pixel of
 * the sprite; `' '` (space) and `'0'` are reserved for "transparent" pixels and
 * are never drawn.
 *
 * The patterns are stored as rows read top-to-bottom so they read naturally in
 * code, and the renderer flips Y when drawing because [ShapeRenderer] uses
 * a Y-up coordinate system in this project.
 */
object PixelSpriteRenderer {
    fun draw(
        shapes: ShapeRenderer,
        pattern: Array<String>,
        palette: Map<Char, Color>,
        centerX: Float,
        centerY: Float,
        size: Float
    ) {
        val rows = pattern.size
        if (rows == 0) return
        val cols = pattern[0].length
        if (cols == 0) return

        val pixelWidth = size / cols
        val pixelHeight = size / rows
        val originX = centerX - size / 2f
        val originY = centerY - size / 2f

        for (row in 0 until rows) {
            val rowPattern = pattern[row]
            for (col in 0 until cols.coerceAtMost(rowPattern.length)) {
                val ch = rowPattern[col]
                if (ch == ' ' || ch == '0') continue
                val color = palette[ch] ?: continue
                shapes.color = color
                shapes.rect(
                    originX + col * pixelWidth,
                    // Patterns are listed top-down but ShapeRenderer is Y-up.
                    originY + (rows - row - 1) * pixelHeight,
                    pixelWidth,
                    pixelHeight
                )
            }
        }
    }
}

/**
 * Pixel-art sprite definitions for in-game entities (player hero, NPC monster,
 * wooden-door exit). Kept as a small data-only object so the renderer reads
 * them by name without per-frame allocation.
 */
object Sprites {
    // Hero: friendly explorer with a hat, face and limbs (7x7).
    private val heroPalette = mapOf(
        'H' to Color(0.22f, 0.34f, 0.78f, 1f), // hat / tunic (deep blue)
        'S' to Color(0.97f, 0.83f, 0.68f, 1f), // skin
        'B' to Color(0.40f, 0.27f, 0.16f, 1f), // belt / boots
        'E' to Color(0.05f, 0.05f, 0.08f, 1f)  // eyes / outline
    )
    val hero: Array<String> = arrayOf(
        "0HHHHH0",
        "HHHHHHH",
        "0SESES0",
        "0SSSSS0",
        "0HHHHH0",
        "0HHBHH0",
        "0B000B0"
    )

    // Monster: red goblin/ghost with white eyes and jagged bottom (7x7).
    private val monsterPalette = mapOf(
        'M' to Color(0.78f, 0.18f, 0.20f, 1f), // body
        'D' to Color(0.45f, 0.08f, 0.10f, 1f), // dark shading
        'W' to Color(0.96f, 0.96f, 0.98f, 1f), // eye whites
        'E' to Color(0.05f, 0.05f, 0.08f, 1f)  // pupils
    )
    val monster: Array<String> = arrayOf(
        "0DMMMD0",
        "DMMMMMD",
        "MWEMEWM",
        "MMMMMMM",
        "DMMMMMD",
        "DMDMDMD",
        "0D0M0D0"
    )

    // Exit: wooden door with frame, planks, knob (7x7).
    private val doorPalette = mapOf(
        'F' to Color(0.32f, 0.20f, 0.10f, 1f), // dark frame
        'P' to Color(0.65f, 0.42f, 0.22f, 1f), // plank body
        'L' to Color(0.80f, 0.55f, 0.30f, 1f), // lighter wood-grain highlight
        'K' to Color(0.95f, 0.80f, 0.20f, 1f), // brass knob
        'E' to Color(0.10f, 0.06f, 0.04f, 1f)  // outline / hinges
    )
    val exitDoor: Array<String> = arrayOf(
        "FFFFFFF",
        "FPLPLPF",
        "FPPPPPF",
        "FPLPLPF",
        "FPPKPPF",
        "FPLPLPF",
        "EFFFFFE"
    )

    fun heroPalette(): Map<Char, Color> = heroPalette
    fun monsterPalette(): Map<Char, Color> = monsterPalette
    fun doorPalette(): Map<Char, Color> = doorPalette
}
