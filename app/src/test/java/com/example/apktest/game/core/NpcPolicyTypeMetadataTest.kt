package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Guards the presentation metadata on [NpcPolicyType] so the legend and
 * renderer can rely on every entry having a non-blank label/description and a
 * distinct, in-range body color.
 */
class NpcPolicyTypeMetadataTest {
    @Test
    fun every_entry_has_non_blank_label_and_description() {
        for (type in NpcPolicyType.entries) {
            assertTrue("label blank for $type", type.label.isNotBlank())
            assertTrue("description blank for $type", type.description.isNotBlank())
        }
    }

    @Test
    fun color_components_are_in_unit_range() {
        for (type in NpcPolicyType.entries) {
            val (r, g, b) = type.colorRgb
            assertTrue("r out of range for $type: $r", r in 0f..1f)
            assertTrue("g out of range for $type: $g", g in 0f..1f)
            assertTrue("b out of range for $type: $b", b in 0f..1f)
        }
    }

    @Test
    fun colors_are_pairwise_distinct() {
        val minDistance = 0.2f
        val entries = NpcPolicyType.entries
        for (i in entries.indices) {
            for (j in i + 1 until entries.size) {
                val a = entries[i].colorRgb
                val b = entries[j].colorRgb
                val dr = a.first - b.first
                val dg = a.second - b.second
                val db = a.third - b.third
                val dist = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
                assertTrue(
                    "Colors for ${entries[i]} and ${entries[j]} too similar ($dist)",
                    dist >= minDistance
                )
            }
        }
    }

    @Test
    fun directChase_preserves_legacy_red_tint() {
        // Regression guard: DIRECT_CHASE must keep the original red goblin
        // tint so existing players don't see a sudden color shift on the
        // default NPC.
        val (r, g, b) = NpcPolicyType.DIRECT_CHASE.colorRgb
        assertEquals(0.78f, r, 0.0001f)
        assertEquals(0.18f, g, 0.0001f)
        assertEquals(0.20f, b, 0.0001f)
    }

    @Test
    fun colors_differ_from_player_blue_tunic() {
        // Hero tunic color is Color(0.22f, 0.34f, 0.78f, 1f) in PixelSprites.
        // Ensure no NPC body color collides with it within the same threshold.
        val playerR = 0.22f
        val playerG = 0.34f
        val playerB = 0.78f
        for (type in NpcPolicyType.entries) {
            val (r, g, b) = type.colorRgb
            val dr = r - playerR
            val dg = g - playerG
            val db = b - playerB
            val dist = sqrt((dr * dr + dg * dg + db * db).toDouble()).toFloat()
            assertNotEquals("$type body color equals player tunic", 0f, dist)
            assertTrue("$type body color too close to player tunic ($dist)", dist >= 0.2f)
        }
    }
}
