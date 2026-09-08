package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PowerUpType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class SpritePatternsTest {
    @Test
    fun allEntityFramesAndExitHaveCompleteRectangularPalettes() {
        for (frame in Sprites.heroFrames) assertComplete(frame, Sprites.heroPalette())
        for (frame in Sprites.adventurerFrames) assertComplete(frame, Sprites.adventurerPalette())
        for (policy in NpcPolicyType.entries) {
            for (frame in Sprites.monsterFrames) assertComplete(frame, NpcIcons.gdxColorsFor(policy))
        }
        assertComplete(Sprites.exitDoor, Sprites.doorPalette())
    }

    @Test
    fun playerAndAdventurerRemainDifferentSilhouettesInEveryAnimationCombination() {
        for (hero in Sprites.heroFrames) {
            for (adventurer in Sprites.adventurerFrames) {
                var differentPixels = 0
                for (row in hero.indices) {
                    for (column in hero[row].indices) {
                        if (opaque(hero[row][column]) != opaque(adventurer[row][column])) differentPixels++
                    }
                }
                assertTrue("Identity must not rely on blue versus green", differentPixels >= 10)
                assertTrue(hero[1].count { opaque(it) } > adventurer[1].count { opaque(it) })
                assertTrue(hero.any { 'C' in it })
            }
        }
        assertNotEquals(Sprites.adventurerIdle.last(), Sprites.adventurerStep1.last())
        assertNotEquals(Sprites.adventurerStep1.last(), Sprites.adventurerStep2.last())
    }

    @Test
    fun adventurerLegendReusesTheIdlePatternAndMatchingCachedColors() {
        val pattern = AdventurerIcons.pattern()
        val colors = AdventurerIcons.colors()
        assertSame(Sprites.adventurerIdle, pattern)
        assertSame(Sprites.adventurerFrames[0], pattern)
        assertEquals(Sprites.adventurerPalette().keys, colors.keys)
        for ((pixel, color) in Sprites.adventurerPalette()) {
            val androidColor = colors.getValue(pixel)
            assertEquals(255, androidColor ushr 24)
            assertEquals((color.r * 255f).roundToInt(), androidColor ushr 16 and 255)
            assertEquals((color.g * 255f).roundToInt(), androidColor ushr 8 and 255)
            assertEquals((color.b * 255f).roundToInt(), androidColor and 255)
        }
        repeat(100) {
            assertSame(pattern, AdventurerIcons.pattern())
            assertSame(colors, AdventurerIcons.colors())
            assertSame(Sprites.heroFrames[0], Sprites.hero)
            assertSame(Sprites.heroPalette(), Sprites.heroPalette())
            assertSame(Sprites.adventurerPalette(), Sprites.adventurerPalette())
            assertSame(Sprites.doorPalette(), Sprites.doorPalette())
        }
    }

    @Test
    fun everyWallVariantAndPowerUpRetainsCompleteCachedPixels() {
        for (index in WallTextures.variants.indices) {
            val pattern = WallTextures.variants[index]
            assertEquals(4, pattern.size)
            for (row in pattern.indices) {
                assertEquals(8, pattern[row].length)
                for (column in pattern[row].indices) {
                    assertSame(
                        WallTextures.palette.getValue(pattern[row][column]),
                        WallTextures.variantColors[index][row][column]
                    )
                }
            }
        }
        for (type in PowerUpType.entries) {
            val pattern = PowerUpIcons.patternFor(type)
            assertEquals(5, pattern.size)
            assertTrue(pattern.all { row -> row.length == 5 && row.all { it == '0' || it == '1' } })
            assertTrue(pattern.any { '1' in it })
            assertSame(pattern, PowerUpIcons.patternFor(type))
            assertSame(PowerUpIcons.gdxColorFor(type), PowerUpIcons.gdxColorFor(type))
        }
    }

    private fun assertComplete(pattern: Array<String>, palette: Map<Char, Color>) {
        assertEquals(7, pattern.size)
        assertTrue(pattern.all { it.length == 7 })
        assertTrue(pattern.any { row -> row.any { opaque(it) } })
        for (row in pattern) {
            for (pixel in row) {
                if (!opaque(pixel)) continue
                assertTrue("Missing palette colour for $pixel", palette.containsKey(pixel))
                val color = palette.getValue(pixel)
                assertTrue(color.r in 0f..1f && color.g in 0f..1f && color.b in 0f..1f)
                assertEquals(1f, color.a, 0f)
            }
        }
    }

    private fun opaque(pixel: Char): Boolean = pixel != '0' && pixel != ' '
}
