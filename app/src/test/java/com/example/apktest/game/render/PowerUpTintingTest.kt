package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PowerUpTintingTest {
    @Test
    fun tintStrengthAndAlphaConstants_stayInBlendRange() {
        assertTrue(PowerUpTinting.PLAYER_TINT_STRENGTH in 0f..1f)
        assertTrue(PowerUpTinting.MAZE_TINT_ALPHA in 0f..1f)
    }

    @Test
    fun singleTint_usesSameColorAcrossSprite() {
        val tint = arrayOf(Color(1f, 0f, 0f, 1f))
        val out = Color()

        PowerUpTinting.gradientTintForColumn(tint, tintCount = 1, column = 0, columns = 7, out = out)
        assertColor(out, r = 1f, g = 0f, b = 0f, a = 1f)

        PowerUpTinting.gradientTintForColumn(tint, tintCount = 1, column = 6, columns = 7, out = out)
        assertColor(out, r = 1f, g = 0f, b = 0f, a = 1f)
    }

    @Test
    fun multipleTints_formDeterministicColumnGradient() {
        val tints = arrayOf(
            Color(1f, 0f, 0f, 1f),
            Color(0f, 1f, 0f, 1f),
            Color(0f, 0f, 1f, 1f)
        )
        val out = Color()

        PowerUpTinting.gradientTintForColumn(tints, tintCount = 3, column = 0, columns = 3, out = out)
        assertColor(out, r = 1f, g = 0f, b = 0f, a = 1f)

        PowerUpTinting.gradientTintForColumn(tints, tintCount = 3, column = 1, columns = 3, out = out)
        assertColor(out, r = 0f, g = 1f, b = 0f, a = 1f)

        PowerUpTinting.gradientTintForColumn(tints, tintCount = 3, column = 2, columns = 3, out = out)
        assertColor(out, r = 0f, g = 0f, b = 1f, a = 1f)
    }

    @Test
    fun tintedColor_preservesBaseAlphaAndMovesTowardTint() {
        val base = Color(0.2f, 0.4f, 0.6f, 0.7f)
        val tint = arrayOf(Color(1f, 0.4f, 0f, 1f))
        val out = Color()

        PowerUpTinting.tintedColorForPixel(
            base = base,
            tintColors = tint,
            tintCount = 1,
            column = 0,
            columns = 7,
            out = out
        )

        assertTrue(out.r > base.r)
        assertEquals(base.g, out.g, 0.0001f)
        assertTrue(out.b < base.b)
        assertEquals(base.a, out.a, 0.0001f)
    }

    @Test
    fun gradientTint_rejectsTintCountBeyondProvidedColors() {
        try {
            PowerUpTinting.gradientTintForColumn(
                tintColors = arrayOf(Color.RED),
                tintCount = 2,
                column = 0,
                columns = 7,
                out = Color()
            )
            fail("Expected tintCount larger than tintColors.size to be rejected")
        } catch (e: IllegalArgumentException) {
            assertEquals("tintCount (2) must be <= tintColors.size (1)", e.message)
        }
    }

    private fun assertColor(color: Color, r: Float, g: Float, b: Float, a: Float) {
        assertEquals(r, color.r, 0.0001f)
        assertEquals(g, color.g, 0.0001f)
        assertEquals(b, color.b, 0.0001f)
        assertEquals(a, color.a, 0.0001f)
    }
}
