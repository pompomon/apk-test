package com.example.apktest.ui

import com.example.apktest.R
import com.example.apktest.game.core.RunPerkCatalogue
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventurePerkTextTest {
    private val text = AdventurePerkText { id, args ->
        when (id) {
            R.string.adventure_perk_ready -> "ready"
            R.string.adventure_perk_used -> "used"
            R.string.adventure_perk_quick_feet_name -> "Quick Feet"
            R.string.adventure_perk_second_wind_name -> "Second Wind"
            R.string.adventure_perk_scope_this_run -> "this run"
            R.string.adventure_perk_scope_once_per_run -> "once per run"
            R.string.adventure_perk_tier_common -> "common"
            R.string.adventure_perk_tier_rare -> "rare"
            R.string.adventure_perk_summary_empty -> "empty"
            else -> "$id(${args.joinToString("|")})"
        }
    }

    @Test
    fun chooserAnnouncesResultingStackCapTierAndScope() {
        val choice = text.choice(RunPerkId.QUICK_FEET, listOf(RunPerkStack(RunPerkId.QUICK_FEET, 1)))
        assertTrue(choice.contains("Quick Feet|common|2|3|"))
        assertTrue(choice.contains("this run"))
        val charge = text.choice(RunPerkId.SECOND_WIND, emptyList())
        assertTrue(charge.contains("Second Wind|rare|1|1|"))
        assertTrue(charge.contains("once per run"))
    }

    @Test
    fun compactAndExpandedSummariesRetainConsumedChargeWithUsedLabel() {
        val ready = listOf(RunPerkStack(RunPerkId.QUICK_FEET, 2), RunPerkStack(RunPerkId.SECOND_WIND, 1))
        assertTrue(text.compact(ready).contains("Second Wind|ready"))
        val consumed = ready.map { if (it.id == RunPerkId.SECOND_WIND) it.copy(consumed = true) else it }
        assertTrue(text.compact(consumed).contains("Second Wind|used"))
        assertFalse(text.compact(consumed).contains("ready"))
        assertTrue(text.expanded(consumed).contains("Quick Feet|2|3"))
        assertTrue(text.expanded(consumed).contains("Second Wind|1|1"))
        assertTrue(text.expanded(consumed).contains("used"))
        assertTrue(text.expanded(consumed).contains("once per run"))
    }

    @Test
    fun emptyAndEveryAvailablePerkHaveResourceBackedCopy() {
        assertEquals("", text.compact(emptyList()))
        assertEquals("empty", text.expanded(emptyList()))
        for (id in RunPerkId.entries.filter { RunPerkCatalogue.definition(it).available }) {
            assertTrue(text.choice(id, emptyList()).isNotBlank())
            assertTrue(text.expanded(listOf(RunPerkStack(id, 1))).isNotBlank())
        }
    }
}
