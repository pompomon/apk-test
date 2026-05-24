package com.example.apktest.game.render

import com.example.apktest.game.core.NpcPolicyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonsterPaletteTest {
    @Test
    fun directChase_palette_matches_legacy_red() {
        val palette = Sprites.monsterPaletteFor(NpcPolicyType.DIRECT_CHASE)
        val body = palette.getValue('M')
        assertEquals(0.78f, body.r, 0.0001f)
        assertEquals(0.18f, body.g, 0.0001f)
        assertEquals(0.20f, body.b, 0.0001f)
    }

    @Test
    fun every_policy_has_full_palette_with_distinct_body_color() {
        val bodies = mutableListOf<Triple<Float, Float, Float>>()
        for (type in NpcPolicyType.entries) {
            val palette = Sprites.monsterPaletteFor(type)
            assertEquals("missing chars for $type", setOf('M', 'D', 'W', 'E'), palette.keys)
            val body = palette.getValue('M')
            bodies += Triple(body.r, body.g, body.b)
        }
        for (i in bodies.indices) {
            for (j in i + 1 until bodies.size) {
                assertNotEquals(
                    "Body colors for ${NpcPolicyType.entries[i]} and ${NpcPolicyType.entries[j]} are equal",
                    bodies[i],
                    bodies[j]
                )
            }
        }
    }

    @Test
    fun dark_shading_is_darker_than_body() {
        for (type in NpcPolicyType.entries) {
            val palette = Sprites.monsterPaletteFor(type)
            val body = palette.getValue('M')
            val dark = palette.getValue('D')
            val bodySum = body.r + body.g + body.b
            val darkSum = dark.r + dark.g + dark.b
            assertTrue(
                "Dark shading for $type ($darkSum) not darker than body ($bodySum)",
                darkSum < bodySum
            )
        }
    }

    @Test
    fun back_compat_alias_matches_direct_chase() {
        val legacy = Sprites.monsterPalette()
        val directChase = Sprites.monsterPaletteFor(NpcPolicyType.DIRECT_CHASE)
        for (ch in setOf('M', 'D', 'W', 'E')) {
            val a = legacy.getValue(ch)
            val b = directChase.getValue(ch)
            assertEquals("R differs for $ch", a.r, b.r, 0.0001f)
            assertEquals("G differs for $ch", a.g, b.g, 0.0001f)
            assertEquals("B differs for $ch", a.b, b.b, 0.0001f)
        }
    }
}
