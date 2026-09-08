package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.example.apktest.game.core.GameStatus
import kotlin.math.abs
import kotlin.math.min

internal class OverlayMessage(val text: String, val color: Color)

internal object OverlayPresentation {
    const val GLYPH_GAP_FACTOR = 0.18f
    const val HORIZONTAL_PADDING_FACTOR = 0.55f
    const val VERTICAL_PADDING_FACTOR = 0.50f
    const val VIEWPORT_INSET = 0.35f
    const val BORDER_THICKNESS = 0.06f
    const val COUNTDOWN_GLYPH_FACTOR = 0.15f
    const val END_GLYPH_FACTOR = 0.10f
    const val ARROW_LENGTH_FACTOR = 0.60f
    private const val ARROW_CARD_GAP_FACTOR = 0.14f

    private val win = OverlayMessage("YOU WIN!", ScenePalette.winText)
    private val lose = OverlayMessage("GAME OVER", ScenePalette.loseText)

    // Reuse complete messages, not a Pair and capturing colour lambda each frame.
    fun endMessage(status: GameStatus): OverlayMessage? = when (status) {
        GameStatus.WIN -> win
        GameStatus.LOSE -> lose
        GameStatus.RUNNING, GameStatus.PAUSED -> null
    }

    fun glyphSize(text: String, worldWidth: Float, worldHeight: Float, preferredFactor: Float): Float {
        val preferred = min(worldWidth, worldHeight) * preferredFactor
        val widthPerGlyph = backdropWidth(text, 1f)
        val availableWidth = (worldWidth - 2f * VIEWPORT_INSET).coerceAtLeast(0f)
        val availableHeight = (worldHeight - 2f * VIEWPORT_INSET).coerceAtLeast(0f)
        return min(preferred, min(availableWidth / widthPerGlyph, availableHeight / backdropHeight(1f)))
    }

    fun backdropWidth(text: String, glyphSize: Float): Float =
        PixelTextRenderer.textWidth(text, glyphSize, glyphSize * GLYPH_GAP_FACTOR) +
            2f * glyphSize * HORIZONTAL_PADDING_FACTOR

    fun backdropHeight(glyphSize: Float): Float =
        glyphSize * (1f + 2f * VERTICAL_PADDING_FACTOR)

    fun arrowCenterDistance(text: String, glyphSize: Float, dirX: Float, dirY: Float): Float {
        // A ray through the card's edge also keeps the wider GO! label clear.
        val horizontalDistance = if (dirX == 0f) Float.POSITIVE_INFINITY
            else backdropWidth(text, glyphSize) / (2f * abs(dirX))
        val verticalDistance = if (dirY == 0f) Float.POSITIVE_INFINITY
            else backdropHeight(glyphSize) / (2f * abs(dirY))
        return min(horizontalDistance, verticalDistance) +
            glyphSize * (ARROW_LENGTH_FACTOR / 2f + ARROW_CARD_GAP_FACTOR)
    }
}
