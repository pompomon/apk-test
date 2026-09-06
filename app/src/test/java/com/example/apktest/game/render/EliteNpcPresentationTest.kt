package com.example.apktest.game.render

import com.example.apktest.game.core.EliteNpcModifier
import com.example.apktest.game.core.NpcPolicyType
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class EliteNpcPresentationTest {
    @Test
    fun everyModifierHasCompletePresentationAndSupportedPolicy() {
        for (modifier in EliteNpcModifier.entries) {
            assertTrue(modifier.id.isNotBlank())
            assertTrue(NpcPolicyType.entries.any { modifier.supports(it) })
            val pattern = EliteNpcIcons.patternFor(modifier)
            assertTrue(pattern.isNotEmpty())
            assertTrue(pattern[0].isNotEmpty())
            assertTrue(pattern.all { it.length == pattern[0].length })
            assertTrue(pattern.all { row -> row.all { it in "0AK" } })
            assertTrue(pattern.any { 'A' in it })
            assertTrue(pattern.any { 'K' in it })
        }
    }

    @Test
    fun geometryReplaysEveryBadgePixelInsideNpcBoundsWithoutCoveringItsFace() {
        for (modifier in EliteNpcModifier.entries) {
            val pattern = EliteNpcIcons.patternFor(modifier)
            val geometry = EliteNpcIcons.geometryFor(modifier)
            val count = pattern.sumOf { row -> row.count { it != '0' } }
            assertEquals(count, geometry.colorIndices.size)
            assertEquals(count * EliteNpcIcons.RECT_STRIDE, geometry.rects.size)
            var index = 0
            for (row in pattern.indices) {
                for (column in pattern[row].indices) {
                    val ch = pattern[row][column]
                    if (ch == '0') continue
                    val offset = index * EliteNpcIcons.RECT_STRIDE
                    val expected = floatArrayOf(
                        0.5f + column * 0.5f / pattern[row].length,
                        0.5f + row * 0.5f / pattern.size,
                        0.5f + (column + 1) * 0.5f / pattern[row].length,
                        0.5f + (row + 1) * 0.5f / pattern.size
                    )
                    assertArrayEquals(
                        expected,
                        geometry.rects.copyOfRange(offset, offset + EliteNpcIcons.RECT_STRIDE),
                        0.00001f
                    )
                    assertEquals(
                        if (ch == 'A') EliteNpcIcons.ACCENT_COLOR else EliteNpcIcons.OUTLINE_COLOR,
                        geometry.colorIndices[index]
                    )
                    index++
                }
            }
            assertTrue(geometry.rects.all { it in 0.5f..1f })
        }
    }

    @Test
    fun trackerHasSymmetricDiamondAndRadarCenterNotJustARecolor() {
        val pattern = EliteNpcIcons.patternFor(EliteNpcModifier.TRACKER)
        assertEquals('A', pattern[pattern.size / 2][pattern[0].length / 2])
        assertEquals("000K000", pattern.first())
        for (row in pattern.indices) {
            assertEquals(pattern[row].reversed(), pattern[row])
            assertEquals(pattern[row], pattern[pattern.lastIndex - row])
        }
    }

    @Test
    fun bothPalettesMatchSharedAccentMetadata() {
        for (modifier in EliteNpcModifier.entries) {
            val gdx = EliteNpcIcons.gdxColorsFor(modifier)
            val android = EliteNpcIcons.androidColorsFor(modifier)
            assertEquals(2, gdx.size)
            assertEquals(gdx.size, android.size)
            val (r, g, b) = modifier.accentRgb
            val accent = gdx[EliteNpcIcons.ACCENT_COLOR]
            assertEquals(r, accent.r, 0f)
            assertEquals(g, accent.g, 0f)
            assertEquals(b, accent.b, 0f)
            for (index in gdx.indices) {
                assertEquals(1f, gdx[index].a, 0f)
                assertEquals(255, android[index] ushr 24)
                assertEquals((gdx[index].r * 255).roundToInt(), android[index] ushr 16 and 255)
                assertEquals((gdx[index].g * 255).roundToInt(), android[index] ushr 8 and 255)
                assertEquals((gdx[index].b * 255).roundToInt(), android[index] and 255)
            }
        }
    }

    @Test
    fun repeatedLookupsReusePatternsGeometryAndPalettes() {
        for (modifier in EliteNpcModifier.entries) {
            val pattern = EliteNpcIcons.patternFor(modifier)
            val geometry = EliteNpcIcons.geometryFor(modifier)
            val gdx = EliteNpcIcons.gdxColorsFor(modifier)
            val android = EliteNpcIcons.androidColorsFor(modifier)
            repeat(100) {
                assertSame(pattern, EliteNpcIcons.patternFor(modifier))
                assertSame(geometry, EliteNpcIcons.geometryFor(modifier))
                assertSame(geometry.rects, EliteNpcIcons.geometryFor(modifier).rects)
                assertSame(geometry.colorIndices, EliteNpcIcons.geometryFor(modifier).colorIndices)
                assertSame(gdx, EliteNpcIcons.gdxColorsFor(modifier))
                assertSame(gdx[EliteNpcIcons.ACCENT_COLOR], EliteNpcIcons.gdxColorsFor(modifier)[1])
                assertSame(android, EliteNpcIcons.androidColorsFor(modifier))
            }
        }
    }

    @Test
    fun baseNpcPolicyPatternsAndPalettesRemainUnchangedAndCached() {
        assertSame(Sprites.monsterIdle, NpcIcons.pattern())
        for (policy in NpcPolicyType.entries) {
            val gdx = NpcIcons.gdxColorsFor(policy)
            val android = NpcIcons.androidColorsFor(policy)
            assertSame(Sprites.monsterPaletteFor(policy), gdx)
            assertSame(gdx, NpcIcons.gdxColorsFor(policy))
            assertSame(android, NpcIcons.androidColorsFor(policy))
            assertEquals(gdx.keys, android.keys)
            for ((ch, color) in gdx) {
                val argb = android.getValue(ch)
                assertEquals((color.r * 255).roundToInt(), argb ushr 16 and 255)
                assertEquals((color.g * 255).roundToInt(), argb ushr 8 and 255)
                assertEquals((color.b * 255).roundToInt(), argb and 255)
            }
        }
    }
}
