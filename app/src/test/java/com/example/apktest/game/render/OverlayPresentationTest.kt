package com.example.apktest.game.render

import com.example.apktest.game.core.GameStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class OverlayPresentationTest {
    @Test
    fun terminalMessagesAreCachedAndNonterminalStatesHaveNoOverlay() {
        val win = OverlayPresentation.endMessage(GameStatus.WIN)!!
        val lose = OverlayPresentation.endMessage(GameStatus.LOSE)!!
        assertEquals("YOU WIN!", win.text)
        assertEquals("GAME OVER", lose.text)
        assertSame(ScenePalette.winText, win.color)
        assertSame(ScenePalette.loseText, lose.color)
        repeat(100) {
            assertSame(win, OverlayPresentation.endMessage(GameStatus.WIN))
            assertSame(lose, OverlayPresentation.endMessage(GameStatus.LOSE))
            assertNull(OverlayPresentation.endMessage(GameStatus.RUNNING))
            assertNull(OverlayPresentation.endMessage(GameStatus.PAUSED))
        }
    }

    @Test
    fun allOverlayCardsFitWithPaddingIncludingLongGameOverOnNarrowWorlds() {
        for ((width, height) in listOf(12.6f to 24f, 30f to 16.6f, 18.6f to 28.6f, 2.6f to 2.6f)) {
            for (text in listOf("3", "2", "1", "GO!", "YOU WIN!", "GAME OVER")) {
                val factor = if (text.length > 3) OverlayPresentation.END_GLYPH_FACTOR
                    else OverlayPresentation.COUNTDOWN_GLYPH_FACTOR
                val glyphSize = OverlayPresentation.glyphSize(text, width, height, factor)
                val cardWidth = OverlayPresentation.backdropWidth(text, glyphSize)
                val cardHeight = OverlayPresentation.backdropHeight(glyphSize)
                assertTrue(glyphSize > 0f)
                assertTrue("Clipped $text card", cardWidth <= width - 2f * OverlayPresentation.VIEWPORT_INSET + 0.00001f)
                assertTrue(cardHeight <= height - 2f * OverlayPresentation.VIEWPORT_INSET + 0.00001f)
                assertTrue(cardWidth > PixelTextRenderer.textWidth(text, glyphSize, glyphSize * OverlayPresentation.GLYPH_GAP_FACTOR))
                assertTrue(cardHeight > glyphSize)
            }
        }
    }

    @Test
    fun exitArrowStartsOutsideCountdownCardInAllDirectionsIncludingGo() {
        for (text in listOf("3", "2", "1", "GO!")) {
            for (x in -1..1) {
                for (y in -1..1) {
                    if (x == 0 && y == 0) continue
                    val length = sqrt((x * x + y * y).toFloat())
                    val dirX = x / length
                    val dirY = y / length
                    val glyphSize = 2f
                    val centerDistance = OverlayPresentation.arrowCenterDistance(text, glyphSize, dirX, dirY)
                    val tailDistance = centerDistance - glyphSize * OverlayPresentation.ARROW_LENGTH_FACTOR / 2f
                    val halfWidth = OverlayPresentation.backdropWidth(text, glyphSize) / 2f
                    val halfHeight = OverlayPresentation.backdropHeight(glyphSize) / 2f
                    assertTrue(abs(dirX * tailDistance) > halfWidth || abs(dirY * tailDistance) > halfHeight)
                }
            }
        }
    }
}
