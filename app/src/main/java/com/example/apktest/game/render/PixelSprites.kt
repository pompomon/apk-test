package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.glutils.ShapeRenderer
import com.example.apktest.game.core.NpcPolicyType

/**
 * Renders multi-color pixel-art sprites described by a rectangular character
 * grid and a character → [Color] palette. Each character corresponds to one
 * pixel of the sprite; `' '` (space) and `'0'` are reserved for "transparent"
 * pixels and are never drawn. All rows must share the same length; this is
 * the caller's contract and is validated once when a pattern is defined (see
 * the [Sprites] init block) so [draw] stays allocation-free in the render loop.
 *
 * The patterns are stored as rows read top-to-bottom so they read naturally in
 * code, and the renderer flips Y when drawing because [ShapeRenderer] uses
 * a Y-up coordinate system in this project.
 */
object PixelSpriteRenderer {
    private val tintScratch = Color()

    fun draw(
        shapes: ShapeRenderer,
        pattern: Array<String>,
        palette: Map<Char, Color>,
        centerX: Float,
        centerY: Float,
        size: Float,
        tintColors: Array<Color>? = null,
        tintCount: Int = 0
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
            for (col in 0 until cols) {
                val ch = rowPattern[col]
                if (ch == ' ' || ch == '0') continue
                val color = palette[ch] ?: continue
                if (tintColors != null && tintCount > 0) {
                    PowerUpTinting.tintedColorForPixel(
                        base = color,
                        tintColors = tintColors,
                        tintCount = tintCount,
                        column = col,
                        columns = cols,
                        out = tintScratch
                    )
                    shapes.color = tintScratch
                } else {
                    shapes.color = color
                }
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

    /**
     * Validates that every row of [pattern] shares the same length. Intended to
     * be called once from sprite/icon definitions (not from [draw]) so the per-
     * frame render path stays allocation-free.
     */
    fun validatePattern(pattern: Array<String>, name: String = "<unnamed>") {
        if (pattern.isEmpty()) return
        val cols = pattern[0].length
        for (i in 1 until pattern.size) {
            require(pattern[i].length == cols) {
                "Sprite pattern '$name' row $i has length ${pattern[i].length}, " +
                    "expected $cols."
            }
        }
    }
}

internal object PowerUpTinting {
    const val PLAYER_TINT_STRENGTH = 0.45f
    const val MAZE_TINT_ALPHA = 0.22f

    fun tintedColorForPixel(
        base: Color,
        tintColors: Array<Color>,
        tintCount: Int,
        column: Int,
        columns: Int,
        out: Color
    ): Color {
        val tint = gradientTintForColumn(
            tintColors = tintColors,
            tintCount = tintCount,
            column = column,
            columns = columns,
            out = out
        )
        val tintR = tint.r
        val tintG = tint.g
        val tintB = tint.b
        out.set(
            blend(base.r, tintR, PLAYER_TINT_STRENGTH),
            blend(base.g, tintG, PLAYER_TINT_STRENGTH),
            blend(base.b, tintB, PLAYER_TINT_STRENGTH),
            base.a
        )
        return out
    }

    fun gradientTintForColumn(
        tintColors: Array<Color>,
        tintCount: Int,
        column: Int,
        columns: Int,
        out: Color
    ): Color {
        require(tintCount > 0) { "tintCount must be positive" }
        require(tintCount <= tintColors.size) {
            "tintCount ($tintCount) must be <= tintColors.size (${tintColors.size})"
        }
        if (tintCount == 1 || columns <= 1) {
            return out.set(tintColors[0])
        }
        val maxSegmentIndex = tintCount - 2
        val scaled = column.coerceIn(0, columns - 1).toFloat() *
            (tintCount - 1).toFloat() / (columns - 1).toFloat()
        val left = scaled.toInt().coerceIn(0, maxSegmentIndex)
        val fraction = scaled - left
        val a = tintColors[left]
        val b = tintColors[left + 1]
        return out.set(
            blend(a.r, b.r, fraction),
            blend(a.g, b.g, fraction),
            blend(a.b, b.b, fraction),
            blend(a.a, b.a, fraction)
        )
    }

    private fun blend(from: Float, to: Float, amount: Float): Float =
        from + (to - from) * amount
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
    // Idle: legs together / boots planted.
    val heroIdle: Array<String> = arrayOf(
        "0HHHHH0",
        "HHHHHHH",
        "0SESES0",
        "0SSSSS0",
        "0HHHHH0",
        "0HHBHH0",
        "0B000B0"
    )
    // Step 1: left boot forward, right boot back.
    val heroStep1: Array<String> = arrayOf(
        "0HHHHH0",
        "HHHHHHH",
        "0SESES0",
        "0SSSSS0",
        "0HHHHH0",
        "0HHBHH0",
        "B0000B0"
    )
    // Step 2: right boot forward, left boot back.
    val heroStep2: Array<String> = arrayOf(
        "0HHHHH0",
        "HHHHHHH",
        "0SESES0",
        "0SSSSS0",
        "0HHHHH0",
        "0HHBHH0",
        "0B0000B"
    )
    /** Backwards-compatible alias for the legacy single-frame hero pattern. */
    val hero: Array<String> = heroIdle
    val heroFrames: Array<Array<String>> = arrayOf(heroIdle, heroStep1, heroStep2)

    // Monster: red goblin/ghost with white eyes and jagged bottom (7x7).
    // The default palette below preserves the legacy DIRECT_CHASE colors and is
    // exposed via [monsterPalette]; per-policy variants are built by
    // [monsterPaletteFor] so each NPC can be tinted to match its
    // [NpcPolicyType.colorRgb].
    private val monsterPalette = mapOf(
        'M' to Color(0.78f, 0.18f, 0.20f, 1f), // body
        'D' to Color(0.45f, 0.08f, 0.10f, 1f), // dark shading
        'W' to Color(0.96f, 0.96f, 0.98f, 1f), // eye whites
        'E' to Color(0.05f, 0.05f, 0.08f, 1f)  // pupils
    )
    // Constant shading factor used to derive the 'D' channel from the body
    // color for non-legacy policies: 0.45 / 0.78 ≈ 0.58 matches the original
    // DIRECT_CHASE palette (and 0.08 / 0.18 ≈ 0.44; we average toward the
    // brighter channel to avoid muddy dark shades on saturated hues).
    // DIRECT_CHASE itself is special-cased below to exactly reproduce the
    // legacy palette, so this factor is only used for other policies.
    private const val MONSTER_DARK_SHADE_FACTOR: Float = 0.55f
    // Eye-white and pupil colors are kept constant across policies so the
    // monster face remains readable regardless of body color.
    private val monsterEyeWhite = Color(0.96f, 0.96f, 0.98f, 1f)
    private val monsterPupil = Color(0.05f, 0.05f, 0.08f, 1f)
    // Cache per-policy palettes so per-frame rendering does not allocate.
    private val monsterPaletteByPolicy: Map<NpcPolicyType, Map<Char, Color>> =
        NpcPolicyType.entries.associateWith { buildMonsterPalette(it) }

    private fun buildMonsterPalette(type: NpcPolicyType): Map<Char, Color> {
        // Preserve the legacy DIRECT_CHASE palette exactly so existing
        // visuals and the [monsterPalette] back-compat alias do not shift.
        if (type == NpcPolicyType.DIRECT_CHASE) return monsterPalette
        val (r, g, b) = type.colorRgb
        val f = MONSTER_DARK_SHADE_FACTOR
        return mapOf(
            'M' to Color(r, g, b, 1f),
            'D' to Color(r * f, g * f, b * f, 1f),
            'W' to monsterEyeWhite,
            'E' to monsterPupil
        )
    }
    // Idle: symmetric jagged hem.
    val monsterIdle: Array<String> = arrayOf(
        "0DMMMD0",
        "DMMMMMD",
        "MWEMEWM",
        "MMMMMMM",
        "DMMMMMD",
        "DMDMDMD",
        "0D0M0D0"
    )
    // Step 1: hem sways one way.
    val monsterStep1: Array<String> = arrayOf(
        "0DMMMD0",
        "DMMMMMD",
        "MWEMEWM",
        "MMMMMMM",
        "DMMMMMD",
        "MDMDMDM",
        "D0M0D00"
    )
    // Step 2: hem sways the other way.
    val monsterStep2: Array<String> = arrayOf(
        "0DMMMD0",
        "DMMMMMD",
        "MWEMEWM",
        "MMMMMMM",
        "DMMMMMD",
        "MDMDMDM",
        "00D0M0D"
    )
    /** Backwards-compatible alias for the legacy single-frame monster pattern. */
    val monster: Array<String> = monsterIdle
    val monsterFrames: Array<Array<String>> = arrayOf(monsterIdle, monsterStep1, monsterStep2)

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
    /**
     * Per-policy NPC palette, derived from [NpcPolicyType.colorRgb]. Cached at
     * class init so calls are allocation-free in the render loop.
     */
    fun monsterPaletteFor(type: NpcPolicyType): Map<Char, Color> =
        monsterPaletteByPolicy.getValue(type)
    fun doorPalette(): Map<Char, Color> = doorPalette

    init {
        // Validate sprite grids once at class init so PixelSpriteRenderer.draw
        // stays allocation-free in the per-frame render loop.
        PixelSpriteRenderer.validatePattern(heroIdle, name = "heroIdle")
        PixelSpriteRenderer.validatePattern(heroStep1, name = "heroStep1")
        PixelSpriteRenderer.validatePattern(heroStep2, name = "heroStep2")
        PixelSpriteRenderer.validatePattern(monsterIdle, name = "monsterIdle")
        PixelSpriteRenderer.validatePattern(monsterStep1, name = "monsterStep1")
        PixelSpriteRenderer.validatePattern(monsterStep2, name = "monsterStep2")
        PixelSpriteRenderer.validatePattern(exitDoor, name = "exitDoor")
    }
}
