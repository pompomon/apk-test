package com.example.apktest.telemetry

import com.example.apktest.game.core.EliteNpcModifier
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.NpcSpawnSpec
import com.example.apktest.game.core.PlayerPolicyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AdventureEliteTelemetryTest {
    private val tracker = NpcSpawnSpec(NpcPolicyType.PATROL_GUARD, EliteNpcModifier.TRACKER)

    @Test
    fun spawnedReportsActualRosterAggregatedByModifier() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventureEliteTelemetry { events += it }
        telemetry.spawned("Hard", 5, listOf(tracker, NpcSpawnSpec(NpcPolicyType.DIRECT_CHASE), tracker),
            PlayerPolicyType.MANUAL)

        assertEquals(1, events.size)
        assertEquals(AdventureTelemetryEventNames.ELITE_MODIFIER_SPAWNED, events.single().name)
        assertEquals(mapOf(
            "difficulty" to "Hard", "maze_index" to "5", "modifier_id" to "tracker",
            "npc_count" to "3", "elite_count" to "2", "player_policy" to "manual"
        ), events.single().properties)
    }

    @Test
    fun normalAndEmptyRostersAndCustomDifficultyEmitNothing() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventureEliteTelemetry { events += it }
        telemetry.spawned("Medium", 1, emptyList(), PlayerPolicyType.MANUAL)
        telemetry.spawned("Medium", 1, listOf(NpcSpawnSpec(NpcPolicyType.PATROL_GUARD)),
            PlayerPolicyType.MANUAL)
        telemetry.spawned("custom", 1, listOf(tracker), PlayerPolicyType.MANUAL)
        telemetry.outcome("Medium", 1, emptySet(), false, 1f, 1, 1)
        assertTrue(events.isEmpty())
    }

    @Test
    fun outcomeDoesNotClaimThatAnEliteCausedDeath() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventureEliteTelemetry { events += it }
        telemetry.outcome("Medium", 2, setOf(EliteNpcModifier.TRACKER), false, 12.75f, 19, 2)
        val event = events.single()
        assertEquals(AdventureTelemetryEventNames.ELITE_MODIFIER_OUTCOME, event.name)
        assertEquals(mapOf(
            "difficulty" to "Medium", "maze_index" to "2", "modifier_id" to "tracker",
            "completed" to "false", "elapsed_seconds" to "12", "steps" to "19", "deaths_this_run" to "2"
        ), event.properties)
        assertFalse(event.properties.containsKey(AdventureTelemetryPropertyNames.DEATH_CAUSE))
    }

    @Test
    fun invalidCountersAreClampedAndSinkFailuresAreHarmless() {
        val events = mutableListOf<AdventureTelemetryEvent>()
        val telemetry = AdventureEliteTelemetry { events += it }
        telemetry.outcome("Hard", -1, setOf(EliteNpcModifier.TRACKER), true, Float.NaN, -1, -1)
        assertEquals("1", events.single().properties["maze_index"])
        assertEquals("0", events.single().properties["elapsed_seconds"])
        assertEquals("0", events.single().properties["steps"])
        assertEquals("0", events.single().properties["deaths_this_run"])

        val broken = AdventureEliteTelemetry { throw IllegalStateException("offline") }
        broken.spawned("Medium", 1, listOf(tracker), PlayerPolicyType.MANUAL)
        broken.outcome("Medium", 1, setOf(EliteNpcModifier.TRACKER), false, 1f, 1, 1)
        AdventureEliteTelemetry().spawned("Medium", 1, listOf(tracker), PlayerPolicyType.MANUAL)
    }

    @Test
    fun policyCatalogueIdsAreIndependentOfDeviceLocale() {
        val previous = Locale.getDefault()
        val events = mutableListOf<AdventureTelemetryEvent>()
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"))
            AdventureEliteTelemetry { events += it }
                .spawned("Medium", 1, listOf(tracker), PlayerPolicyType.BFS_EXIT)
            assertEquals("bfs_exit", events.single().properties["player_policy"])
        } finally {
            Locale.setDefault(previous)
        }
    }
}
