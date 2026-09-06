package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTrackerSnapshotTest {
    private val tracker = NpcSpawnSpec(NpcPolicyType.PATROL_GUARD, EliteNpcModifier.TRACKER)

    @Test
    fun mixedRosterRoundTripsActiveModifiersAndFullRestartAssignmentById() {
        val engine = engine()
        engine.npcs.reverse()
        val snapshot = engine.snapshot()
        assertEquals(listOf(NpcPolicyType.DIRECT_CHASE, NpcPolicyType.PATROL_GUARD), snapshot.npcPolicies)
        assertEquals(EliteNpcModifier.TRACKER, snapshot.npcs.first { it.id == 1 }.eliteModifier)
        val decoded = GameEngineSnapshot.fromJson(snapshot.toJson())!!
        val restored = GameEngine(DifficultyPresets.MEDIUM, 99L)
        restored.restore(decoded)
        assertEquals(snapshot, restored.snapshot())
        restored.restart(snapshot.seed)
        assertEquals(snapshot.npcSpawnSpecs, restored.npcs.map { NpcSpawnSpec(it.policyType, it.eliteModifier) })
    }

    @Test
    fun capacityTruncationDoesNotDropFullRestartSpecsDuringSaveRestoreOrRestart() {
        val preset = DifficultyPresets.EASY
        val count = preset.mazeWidth * preset.mazeHeight + 3
        val specs = List(count) { if (it % 2 == 0) tracker else NpcSpawnSpec(NpcPolicyType.PREDICTIVE_CHASE) }
        val engine = GameEngine(preset, 1L)
        engine.configureAdventureMaze(count, emptyList(), npcSpawnSpecs = specs)
        engine.restart(1L)
        assertTrue(engine.npcs.size < count)
        assertEquals(specs.take(engine.npcs.size), engine.npcs.map { NpcSpawnSpec(it.policyType, it.eliteModifier) })
        val saved = GameEngineSnapshot.fromJson(engine.snapshot().toJson())!!
        val restored = GameEngine(preset, 99L)
        restored.restore(saved)
        assertEquals(specs, restored.npcSpawnSpecs)
        restored.restart(1L)
        assertEquals(specs, restored.npcSpawnSpecs)
        assertEquals(engine.npcs, restored.npcs)
        assertEquals(saved.npcCountOverride, restored.npcCountOverride)
    }

    @Test
    fun classicNullOverrideDoesNotBecomeAnAccidentalRestartOverride() {
        val engine = GameEngine(DifficultyPresets.MEDIUM, 1L)
        engine.setNpcPolicy(NpcPolicyType.PATROL_GUARD)
        val snapshot = engine.snapshot()
        val json = JSONObject(snapshot.toJson())
        assertTrue(json.has("npcSpawnSpecs"))
        assertTrue(json.isNull("npcSpawnSpecs"))
        val restored = GameEngine(DifficultyPresets.MEDIUM, 9L)
        restored.restore(GameEngineSnapshot.fromJson(json.toString())!!)
        assertNull(restored.npcSpawnSpecs)
        assertNull(restored.npcPolicies)
        restored.setNpcPolicy(NpcPolicyType.PREDICTIVE_CHASE)
        restored.restart(1L)
        assertTrue(restored.npcs.all { it.policyType == NpcPolicyType.PREDICTIVE_CHASE && it.eliteModifier == null })
    }

    @Test
    fun requiredNullAndModifierKeysCannotBeOmitted() {
        val snapshot = engine().snapshot()
        for (edit in listOf<(JSONObject) -> Unit>(
            { it.remove("npcSpawnSpecs") },
            { it.getJSONArray("npcs").getJSONObject(0).remove("eliteModifier") },
            { it.getJSONArray("npcs").getJSONObject(1).remove("eliteModifier") },
            { it.getJSONArray("npcSpawnSpecs").getJSONObject(0).remove("eliteModifier") },
            { it.getJSONArray("npcSpawnSpecs").getJSONObject(1).remove("eliteModifier") },
            { it.put("v", 6) }
        )) {
            val json = JSONObject(snapshot.toJson())
            edit(json)
            assertNull(GameEngineSnapshot.fromJson(json.toString()))
        }
    }

    @Test
    fun unknownMalformedOrIncompatibleActiveAndRestartModifiersAreRejected() {
        val snapshot = engine().snapshot()
        for (arrayKey in listOf("npcs", "npcSpawnSpecs")) {
            for (invalid in listOf<Any>("TRACKER", "future-elite", "", 1, true, JSONObject(), JSONArray())) {
                val json = JSONObject(snapshot.toJson())
                json.getJSONArray(arrayKey).getJSONObject(1).put("eliteModifier", invalid)
                assertNull("$arrayKey/$invalid", GameEngineSnapshot.fromJson(json.toString()))
            }
            val incompatible = JSONObject(snapshot.toJson())
            incompatible.getJSONArray(arrayKey).getJSONObject(0).put("eliteModifier", "tracker")
            assertNull(GameEngineSnapshot.fromJson(incompatible.toString()))
        }
    }

    @Test
    fun malformedFullOverrideAndDisagreementAreRejected() {
        val snapshot = engine().snapshot()
        val edits: List<(JSONObject) -> Unit> = listOf(
            { it.put("npcSpawnSpecs", "tracker") },
            { it.put("npcSpawnSpecs", JSONObject.NULL) },
            { it.put("npcSpawnSpecs", JSONObject()) },
            { it.put("npcSpawnSpecs", JSONArray()) },
            { it.getJSONArray("npcSpawnSpecs").remove(1) },
            { it.getJSONArray("npcSpawnSpecs").put(it.getJSONArray("npcSpawnSpecs").getJSONObject(0)) },
            { it.getJSONArray("npcSpawnSpecs").getJSONObject(1).put("eliteModifier", JSONObject.NULL) },
            { it.getJSONArray("npcs").getJSONObject(1).put("eliteModifier", JSONObject.NULL) },
            { it.getJSONArray("npcSpawnSpecs").getJSONObject(0).put("policyType", "PATROL_GUARD") },
            { it.getJSONArray("npcPolicies").put(0, "PREDICTIVE_CHASE") },
            { it.remove("npcCountOverride") },
            { it.put("npcCountOverride", -1) },
            { it.put("npcCountOverride", 3) },
            { it.getJSONArray("npcs").getJSONObject(1).put("id", 0) },
            { it.getJSONArray("npcs").getJSONObject(1).put("id", 2) },
            { it.getJSONArray("npcs").getJSONObject(1).put("id", -1) }
        )
        for ((index, edit) in edits.withIndex()) {
            val json = JSONObject(snapshot.toJson())
            edit(json)
            assertNull("Mutation $index", GameEngineSnapshot.fromJson(json.toString()))
        }
    }

    @Test
    fun programmaticRestoreValidatesNewAssignmentsBeforeMutatingEngine() {
        val snapshot = engine().snapshot()
        val invalidSnapshots = listOf(
            snapshot.copy(schemaVersion = 6),
            snapshot.copy(npcCountOverride = -1),
            snapshot.copy(npcSpawnSpecs = emptyList()),
            snapshot.copy(npcCountOverride = 1),
            snapshot.copy(npcCountOverride = null),
            snapshot.copy(npcSpawnSpecs = null),
            snapshot.copy(npcSpawnSpecs = null, npcCountOverride = null),
            snapshot.copy(npcPolicies = emptyList()),
            snapshot.copy(npcs = snapshot.npcs.map { it.copy(id = 0) }),
            snapshot.copy(npcs = snapshot.npcs.map { it.copy(id = it.id + 1) }),
            snapshot.copy(npcs = snapshot.npcs.map { it.copy(eliteModifier = EliteNpcModifier.TRACKER) }),
            snapshot.copy(npcs = snapshot.npcs.map { it.copy(eliteModifier = null) }),
            snapshot.copy(npcSpawnSpecs = listOf(NpcSpawnSpec(NpcPolicyType.DIRECT_CHASE), NpcSpawnSpec(NpcPolicyType.PATROL_GUARD)))
        )
        val target = GameEngine(DifficultyPresets.HARD, 99L)
        val before = target.snapshot()
        for (invalid in invalidSnapshots) {
            assertTrue(runCatching { target.restore(invalid) }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, target.snapshot())
        }
    }

    @Test
    fun zeroNpcFullOverrideIsDistinctFromClassicNullAndSurvivesRestore() {
        val engine = GameEngine(DifficultyPresets.EASY, 1L)
        engine.configureAdventureMaze(0, emptyList(), npcSpawnSpecs = emptyList())
        engine.restart(1L)
        val snapshot = GameEngineSnapshot.fromJson(engine.snapshot().toJson())
        assertNotNull(snapshot)
        assertEquals(emptyList<NpcSpawnSpec>(), snapshot!!.npcSpawnSpecs)
        engine.restore(snapshot)
        engine.restart(2L)
        assertTrue(engine.npcs.isEmpty())
    }

    @Test
    fun adventureCountWithoutFullRestartRosterIsRejectedEvenForAllNormalNpcs() {
        val engine = GameEngine(DifficultyPresets.MEDIUM, 1L)
        engine.configureAdventureMaze(3, listOf(NpcPolicyType.PATROL_GUARD))
        engine.restart(1L)
        val invalid = engine.snapshot().copy(npcSpawnSpecs = null)
        assertNull(GameEngineSnapshot.fromJson(invalid.toJson()))
        assertTrue(runCatching { engine.restore(invalid) }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun eliteActiveRosterCannotMasqueradeAsClassicWithoutRestartAssignments() {
        val invalid = engine().snapshot().copy(npcCountOverride = null, npcSpawnSpecs = null)
        assertNull(GameEngineSnapshot.fromJson(invalid.toJson()))
        assertTrue(runCatching { engine().restore(invalid) }.exceptionOrNull() is IllegalArgumentException)
    }

    private fun engine() = GameEngine(DifficultyPresets.MEDIUM, 1L).apply {
        configureAdventureMaze(
            2, emptyList(),
            npcSpawnSpecs = listOf(NpcSpawnSpec(NpcPolicyType.DIRECT_CHASE), tracker)
        )
        restart(1L)
    }
}
