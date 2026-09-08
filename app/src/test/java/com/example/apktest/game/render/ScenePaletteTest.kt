package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class ScenePaletteTest {
    @Test
    fun viewportSurroundMatchesAndroidMazeRootBackground() {
        assertEquals(16f / 255f, ScenePalette.surround.r, 0f)
        assertEquals(18f / 255f, ScenePalette.surround.g, 0f)
        assertEquals(24f / 255f, ScenePalette.surround.b, 0f)
        assertEquals(1f, ScenePalette.surround.a, 0f)
    }

    @Test
    fun floorTextureStaysQuietAndKeepsFourCachedAccentsPerTile() {
        assertEquals(4, FloorTextures.TILE_SIZE)
        assertEquals(12, FloorTextures.pattern.sumOf { row -> row.count { it == 'b' } })
        assertEquals(2, FloorTextures.pattern.sumOf { row -> row.count { it == 'a' } })
        assertEquals(2, FloorTextures.pattern.sumOf { row -> row.count { it == 'h' } })
        val colors = listOf(FloorTextures.base, FloorTextures.accent, FloorTextures.highlight)
        for (color in colors) {
            assertEquals(1f, color.a, 0f)
            assertTrue("Floor accents should recede", contrast(color, FloorTextures.base) <= 1.10)
            assertTrue("Floor should remain dark", luminance(color) < 0.02)
        }
        for (row in FloorTextures.pattern.indices) {
            assertEquals(FloorTextures.TILE_SIZE, FloorTextures.pattern[row].length)
            for (column in FloorTextures.pattern[row].indices) {
                val expected = when (FloorTextures.pattern[row][column]) {
                    'b' -> FloorTextures.base
                    'a' -> FloorTextures.accent
                    'h' -> FloorTextures.highlight
                    else -> error("Unmapped floor pixel")
                }
                assertSame(expected, FloorTextures.pixelColors[row][column])
            }
        }
    }

    @Test
    fun warmWallsRemainBrighterThanEveryFloorTone() {
        for (floor in listOf(FloorTextures.base, FloorTextures.accent, FloorTextures.highlight)) {
            assertTrue("Brick faces need contrast", contrast(WallTextures.stoneMid, floor) >= 3.0)
            assertTrue("Wall edges need contrast", contrast(WallTextures.stoneLight, floor) >= 4.5)
            assertTrue(luminance(WallTextures.stoneDark) > luminance(floor))
        }
        for (stone in listOf(WallTextures.stoneDark, WallTextures.stoneMid, WallTextures.stoneLight)) {
            assertTrue("Stone stays warm", stone.r > stone.g && stone.g > stone.b)
        }
        assertTrue(luminance(WallTextures.stoneDark) < luminance(WallTextures.stoneMid))
        assertTrue(luminance(WallTextures.stoneMid) < luminance(WallTextures.stoneLight))
        assertTrue(luminance(WallTextures.mossLight) < luminance(WallTextures.stoneLight))
    }

    @Test
    fun exitUsesOneCyanAccentAndRestrainedGlow() {
        val palette = Sprites.doorPalette()
        assertEquals(setOf('F', 'P', 'L', 'E'), palette.keys)
        assertSame(ScenePalette.exitCyan, palette.getValue('F'))
        for (color in palette.values) {
            assertTrue("No competing warm or magenta portal tones", color.g > color.r && color.b >= color.g)
            assertEquals(1f, color.a, 0f)
        }
        assertTrue(contrast(palette.getValue('F'), palette.getValue('P')) >= 4.5)
        assertTrue(ScenePalette.EXIT_GLOW_ALPHA in 0.10f..0.22f)
        assertTrue(ScenePalette.EXIT_GLOW_RADIUS in 0.50f..0.70f)
    }

    @Test
    fun countdownAndBothTerminalMessagesHaveHighContrast() {
        for (text in listOf(ScenePalette.countdownText, ScenePalette.winText, ScenePalette.loseText)) {
            assertEquals(1f, text.a, 0f)
            assertTrue("Every overlay must be legible, including defeat", contrast(text, ScenePalette.overlayBackdrop) >= 7.0)
        }
        assertEquals(1f, ScenePalette.overlayBackdrop.a, 0f)
        assertTrue(contrast(ScenePalette.overlayBorder, ScenePalette.overlayBackdrop) >= 2.5)
        assertTrue(contrast(Sprites.heroPalette().getValue('C'), FloorTextures.accent) >= 10.0)
    }

    private fun contrast(a: Color, b: Color): Double {
        val aLuminance = luminance(a)
        val bLuminance = luminance(b)
        return (max(aLuminance, bLuminance) + 0.05) / (min(aLuminance, bLuminance) + 0.05)
    }

    private fun luminance(color: Color): Double =
        0.2126 * linear(color.r) + 0.7152 * linear(color.g) + 0.0722 * linear(color.b)

    private fun linear(channel: Float): Double = if (channel <= 0.04045f) {
        channel / 12.92
    } else {
        ((channel + 0.055) / 1.055).pow(2.4)
    }
}
