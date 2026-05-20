package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Adventure-mode generalisation of [GameEngine] NPC spawning:
 *  - [GameEngine.configureAdventureMaze] overrides the preset's `npcCount`
 *  - per-NPC `policyType` reflects the supplied policy list (by spawn id)
 *  - Snapshot/restore round-trips the override + per-NPC policy list
 */
class GameEngineAdventureSpawnTest {

    private fun easyPreset() = DifficultyPresets.EASY

    @Test
    fun configureAdventureMazeOverridesNpcCount() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        // Easy normally spawns 1 NPC; override to 4.
        engine.configureAdventureMaze(
            npcCount = 4,
            policies = listOf(
                NpcPolicyType.DIRECT_CHASE,
                NpcPolicyType.PATROL_GUARD,
                NpcPolicyType.PREDICTIVE_CHASE,
                NpcPolicyType.PATROL_GUARD
            )
        )
        engine.restart(seed = 1L)
        assertEquals(4, engine.npcs.size)
        assertEquals(NpcPolicyType.DIRECT_CHASE, engine.npcs[0].policyType)
        assertEquals(NpcPolicyType.PATROL_GUARD, engine.npcs[1].policyType)
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, engine.npcs[2].policyType)
        assertEquals(NpcPolicyType.PATROL_GUARD, engine.npcs[3].policyType)
    }

    @Test
    fun configureAdventureMazeFallsBackToEngineNpcPolicyWhenListShort() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        engine.setNpcPolicy(NpcPolicyType.PREDICTIVE_CHASE)
        engine.configureAdventureMaze(
            npcCount = 3,
            policies = listOf(NpcPolicyType.DIRECT_CHASE) // shorter than count
        )
        engine.restart(seed = 1L)
        assertEquals(3, engine.npcs.size)
        assertEquals(NpcPolicyType.DIRECT_CHASE, engine.npcs[0].policyType)
        // Indexes 1 and 2 fall back to the engine's [npcPolicyType].
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, engine.npcs[1].policyType)
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, engine.npcs[2].policyType)
    }

    @Test
    fun clearAdventureMazeConfigRevertsToPresetCount() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        engine.configureAdventureMaze(npcCount = 5, policies = emptyList())
        engine.restart(seed = 1L)
        assertEquals(5, engine.npcs.size)

        engine.clearAdventureMazeConfig()
        engine.restart(seed = 1L)
        assertEquals(easyPreset().npcCount, engine.npcs.size)
    }

    @Test
    fun snapshotRoundTripsNpcCountOverrideAndPolicies() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        engine.configureAdventureMaze(
            npcCount = 3,
            policies = listOf(
                NpcPolicyType.PATROL_GUARD,
                NpcPolicyType.PREDICTIVE_CHASE,
                NpcPolicyType.DIRECT_CHASE
            )
        )
        engine.restart(seed = 1L)
        val snapshot = engine.snapshot()
        assertEquals(3, snapshot.npcCountOverride)
        assertEquals(3, snapshot.npcPolicies.size)
        assertEquals(NpcPolicyType.PATROL_GUARD, snapshot.npcPolicies[0])

        // Re-hydrate a fresh engine from the snapshot and verify per-NPC
        // policy types restored.
        val fresh = GameEngine(easyPreset(), seed = 999L)
        fresh.restore(snapshot)
        assertEquals(3, fresh.npcs.size)
        assertEquals(3, fresh.npcCountOverride)
        assertEquals(NpcPolicyType.PATROL_GUARD, fresh.npcs[0].policyType)
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, fresh.npcs[1].policyType)
        assertEquals(NpcPolicyType.DIRECT_CHASE, fresh.npcs[2].policyType)
    }

    @Test
    fun singleMazeEngineDefaultsLeaveNpcCountOverrideNull() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        // No call to configureAdventureMaze — pure single-maze behaviour.
        assertEquals(null, engine.npcCountOverride)
        assertEquals(null, engine.npcPolicies)
        assertEquals(easyPreset().npcCount, engine.npcs.size)
        assertTrue(engine.npcs.all { it.policyType == engine.npcPolicyType })
    }
}
