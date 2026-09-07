package com.example.apktest.telemetry

import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkStack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AdventurePerkTelemetryTest {
    private val offer = listOf(RunPerkId.SECOND_WIND, RunPerkId.QUICK_FEET, RunPerkId.LONGER_CHARGE)
    private val owned = listOf(
        RunPerkStack(RunPerkId.QUICK_FEET, 2),
        RunPerkStack(RunPerkId.SECOND_WIND, 1, consumed = true)
    )

    @Test
    fun allFiveEventsUseStableIdsAndIntegerAggregateStacks() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventurePerkTelemetry { events += it }
        telemetry.offered("Medium", 3, offer, owned)
        telemetry.chosen("Medium", 3, RunPerkId.QUICK_FEET, offer, owned, 2)
        telemetry.effectApplied("Medium", 4, RunPerkId.QUICK_FEET, "player_speed_percent", 10)
        telemetry.consumed("Medium", 4, RunPerkId.SECOND_WIND, owned)
        telemetry.outcome("Medium", 7, owned, true, 65.9f, 2)
        assertEquals(listOf(
            "adventure_perk_offer_shown", "adventure_perk_chosen", "adventure_perk_effect_applied",
            "adventure_perk_consumed", "adventure_perk_run_outcome"
        ), events.map { it.name })
        assertEquals(mapOf(
            "difficulty" to "Medium", "maze_index" to "3",
            "offered_perk_ids" to "second_wind,quick_feet,longer_charge", "current_stacks" to "3"
        ), events[0].properties)
        assertEquals("2", events[1].properties["stack_after_choice"])
        assertEquals("3", events[1].properties["current_stacks"])
        assertEquals("10", events[2].properties["amount"])
        assertEquals("capture", events[3].properties["trigger"])
        assertEquals("3", events[3].properties["current_stacks"])
        assertEquals(mapOf(
            "difficulty" to "Medium", "total_mazes" to "7", "perk_ids" to "quick_feet,second_wind",
            "current_stacks" to "3", "completed" to "true", "total_elapsed_seconds" to "65",
            "deaths_this_run" to "2"
        ), events[4].properties)
        assertFalse(events.any { event ->
            event.properties.keys.any { it.contains("seed") || it.contains("position") || it.contains("snapshot") }
        })
    }

    @Test
    fun effectsUseExplicitUnitsAndRejectArbitrarySystemsOrAmounts() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventurePerkTelemetry { events += it }
        telemetry.effectApplied("Hard", 2, RunPerkId.LONGER_CHARGE, "power_up_duration_seconds", 3)
        telemetry.effectApplied("Hard", 2, RunPerkId.POCKET_MAGNET, "magnet_radius_cells", 2)
        telemetry.effectApplied("Hard", 2, RunPerkId.SECOND_WIND, "freeze_pulse_seconds", 1)
        telemetry.effectApplied("Hard", 2, RunPerkId.RISK_DIVIDEND, "reward_options", 1)
        telemetry.effectApplied("Hard", 2, RunPerkId.SCOUT_SENSE, "reward_preview", 1)
        assertEquals(listOf("3", "2", "1", "1", "1"), events.map { it.properties["amount"] })
        telemetry.effectApplied("Hard", 2, RunPerkId.QUICK_FEET, "player_speed_percent", 500)
        telemetry.effectApplied("Hard", 2, RunPerkId.QUICK_FEET, "private_payload", 5)
        telemetry.effectApplied("Hard", 2, RunPerkId.FIRST_SHIELD, "freeze_pulse_seconds", 1)
        assertEquals(5, events.size)
    }

    @Test
    fun unsupportedDifficultiesEmptyBuildsAndUncommittedChargesEmitNothing() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventurePerkTelemetry { events += it }
        telemetry.offered("custom", 1, offer, owned)
        telemetry.offered("Medium", 1, emptyList(), owned)
        telemetry.chosen("Medium", 1, RunPerkId.POCKET_MAGNET, offer, owned, 2)
        telemetry.consumed("Medium", 1, RunPerkId.SECOND_WIND, owned.map { it.copy(consumed = false) })
        telemetry.outcome("Medium", 7, emptyList(), true, 1f, 0)
        assertTrue(events.isEmpty())
    }

    @Test
    fun invalidCountersClampAndOptionalSinkFailuresCannotAffectGameplay() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        AdventurePerkTelemetry { events += it }.outcome("Hard", -1, owned, false, Float.NaN, -1)
        assertEquals("1", events.single().properties["total_mazes"])
        assertEquals("0", events.single().properties["total_elapsed_seconds"])
        assertEquals("0", events.single().properties["deaths_this_run"])
        val broken = AdventurePerkTelemetry { throw IllegalStateException("offline") }
        broken.offered("Medium", 1, offer, owned)
        broken.chosen("Medium", 1, RunPerkId.QUICK_FEET, offer, owned, 1)
        broken.effectApplied("Medium", 1, RunPerkId.QUICK_FEET, "player_speed_percent", 10)
        broken.consumed("Medium", 1, RunPerkId.SECOND_WIND, owned)
        broken.outcome("Medium", 7, owned, true, 1f, 0)
        AdventurePerkTelemetry().offered("Medium", 1, offer, owned)
    }

    @Test
    fun idsAreLocaleIndependent() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            val events = mutableListOf<AdventureTelemetryEvent>()
            AdventurePerkTelemetry { events += it }.offered("Medium", 1, offer, emptyList())
            assertEquals("second_wind,quick_feet,longer_charge", events.single().properties["offered_perk_ids"])
        } finally {
            Locale.setDefault(previous)
        }
    }
}
