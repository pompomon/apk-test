package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdventureEliteSnapshotTest {
    @Test
    fun schemaFourWritesRequiredSpecsWithStableIdsAndExplicitNulls() {
        val snapshot = lockedSnapshot()
        val obj = JSONObject(snapshot.toJson())
        assertEquals(4, obj.getInt("v"))
        assertFalse(obj.has("mazeNpcPolicies"))
        val specs = obj.getJSONArray("mazeNpcSpawnSpecs")
        assertEquals("PATROL_GUARD", specs.getJSONObject(0).getString("policyType"))
        assertEquals("tracker", specs.getJSONObject(0).getString("eliteModifier"))
        assertTrue(specs.getJSONObject(1).has("eliteModifier"))
        assertTrue(specs.getJSONObject(1).isNull("eliteModifier"))
        assertEquals(snapshot, AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
        assertEquals(snapshot.currentMazeNpcSpawnSpecs, snapshot.toState().currentMazeNpcSpawnSpecs)
        assertEquals(snapshot.currentMazeNpcSpawnSpecs.map { it.policyType }, snapshot.currentMazeNpcPolicies)
        assertInvalid(snapshot) { put("v", 3) }
    }

    @Test
    fun singleTrackerLocksAreValidOnMediumAndEligibleEasyButNotHard() {
        for (difficulty in DifficultyPresets.all) {
            val snapshot = lockedSnapshot().copy(
                difficultyName = difficulty.name,
                currentMazeIndex = if (difficulty == DifficultyPresets.EASY) 2 else 0,
                currentMazeNpcCount = 1,
                currentMazeNpcSpawnSpecs = listOf(NpcSpawnSpec(NpcPolicyType.PATROL_GUARD,
                    EliteNpcModifier.TRACKER))
            )
            val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson())
            if (difficulty == DifficultyPresets.HARD) assertNull(restored) else assertEquals(snapshot, restored)
        }
    }

    @Test
    fun missingUnknownWrongTypedAndIncompatibleSpawnSpecsRejectTheWholeRun() {
        val snapshot = lockedSnapshot()
        assertInvalid(snapshot) { remove("mazeNpcSpawnSpecs") }
        for (value in listOf(JSONObject.NULL, "[]", 3, true, JSONObject())) {
            assertInvalid(snapshot) { put("mazeNpcSpawnSpecs", value) }
        }
        for (value in listOf(JSONObject.NULL, "PATROL_GUARD", 3, true, JSONArray())) {
            assertInvalid(snapshot) { getJSONArray("mazeNpcSpawnSpecs").put(0, value) }
        }
        for (key in listOf("policyType", "eliteModifier")) {
            assertInvalid(snapshot) { getJSONArray("mazeNpcSpawnSpecs").getJSONObject(0).remove(key) }
            for (value in listOf(1, false, JSONArray(), JSONObject())) {
                assertInvalid(snapshot) { getJSONArray("mazeNpcSpawnSpecs").getJSONObject(0).put(key, value) }
            }
        }
        for (policy in listOf("FUTURE_POLICY", "", "DIRECT_CHASE", "PREDICTIVE_CHASE")) {
            assertInvalid(snapshot) { getJSONArray("mazeNpcSpawnSpecs").getJSONObject(0).put("policyType", policy) }
        }
        assertInvalid(snapshot) {
            getJSONArray("mazeNpcSpawnSpecs").getJSONObject(0).put("policyType", JSONObject.NULL)
        }
        for (modifier in listOf("future_modifier", "TRACKER", "")) {
            assertInvalid(snapshot) {
                getJSONArray("mazeNpcSpawnSpecs").getJSONObject(0).put("eliteModifier", modifier)
            }
        }
    }

    @Test
    fun seedCountSpecsAndEliteBudgetsMustFormOneConsistentLock() {
        val snapshot = lockedSnapshot()
        assertInvalid(snapshot) { remove("mazeSeed") }
        assertInvalid(snapshot) { put("mazeNpcCount", JSONObject.NULL) }
        assertInvalid(snapshot) { put("mazeNpcCount", -1) }
        assertInvalid(snapshot) { put("mazeNpcCount", snapshot.currentMazeNpcCount!! - 1) }
        assertInvalid(snapshot) { put("mazeNpcCount", snapshot.currentMazeNpcCount!! + 1) }
        assertInvalid(snapshot) { getJSONArray("mazeNpcSpawnSpecs").remove(0) }
        assertInvalid(snapshot) {
            remove("mazeSeed")
            put("mazeNpcCount", JSONObject.NULL)
        }
        assertInvalid(snapshot) {
            getJSONArray("mazeNpcSpawnSpecs").getJSONObject(3).put("eliteModifier", "tracker")
        }
        assertInvalid(snapshot) { put("mazeIndex", 3) } // Count-ramp maze 4.
        assertInvalid(snapshot) { put("mazeIndex", 8) } // Final maze.
        assertInvalid(snapshot) { put("difficulty", DifficultyPresets.EASY.name) } // Before maze 3.

        val unprepared = AdventureRunStateSnapshot.fromState(
            AdventureRunState(DifficultyPresets.HARD.name), 5L)
        assertInvalid(unprepared) { remove("mazeNpcSpawnSpecs") }
        assertEquals(unprepared, AdventureRunStateSnapshot.fromJson(unprepared.toJson()))
    }

    @Test
    fun riskyRouteLocksCannotContainElites() {
        for (routeId in listOf(RouteEventGenerator.AMBUSH_SHORTCUT, RouteEventGenerator.CURSED_GATE)) {
            val fixture = AdventureRouteEventsTest.atRoute(routeId)
            val c = fixture.controller
            assertTrue(c.chooseRoute(2, routeId))
            val snapshot = AdventureRunStateSnapshot.fromState(c.state, fixture.seed)
            assertInvalid(snapshot) {
                getJSONArray("mazeNpcSpawnSpecs").getJSONObject(0)
                    .put("policyType", NpcPolicyType.PATROL_GUARD.name).put("eliteModifier", "tracker")
            }
        }
    }

    @Test
    fun loadAndReprepareUsePersistedAssignmentRatherThanCurrentSeedGeneratorOrSpawnSafety() {
        val snapshot = lockedSnapshot().copy(runSeed = Long.MIN_VALUE)
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(
            difficulty = DifficultyPresets.HARD.copy(npcDirectPathSpawnBuffer = 1000),
            baseNpcsPerMaze = 0
        )
        val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson(), config)!!
        assertEquals(snapshot, restored)
        for (enabled in listOf(false, true)) {
            val c = AdventureRunController(config, restored.toState(), restored.runSeed, elitesEnabled = enabled)
            val spec = c.prepareCurrentMaze()!!
            assertEquals(snapshot.currentMazeSeed, spec.seed)
            assertEquals(snapshot.currentMazeNpcSpawnSpecs, spec.npcSpawnSpecs)
        }
    }

    @Test
    fun partialReorderedRosterMatchesByNpcIdAndRetainsFullRestartAssignment() {
        val snapshot = partialEngineSnapshot()
        assertTrue(snapshot.currentMazeSnapshot!!.matchesAdventureMaze(snapshot.difficultyName,
            snapshot.currentMazeSeed, snapshot.currentMazeNpcCount, snapshot.currentMazeNpcSpawnSpecs, null))
        assertNotNull(GameEngineSnapshot.fromJson(snapshot.currentMazeSnapshot.toJson()))
        val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson())!!
        assertNotNull(restored.currentMazeSnapshot)
        assertEquals(snapshot.currentMazeSnapshot.npcs.associateBy { it.id },
            restored.currentMazeSnapshot!!.npcs.associateBy { it.id })
        assertEquals(snapshot.currentMazeSnapshot.npcPolicies, restored.currentMazeSnapshot.npcPolicies)
        assertEquals(snapshot.currentMazeNpcSpawnSpecs, restored.currentMazeSnapshot.npcSpawnSpecs)
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        val c = AdventureRunController(config, restored.toState(), restored.runSeed, elitesEnabled = false)
        c.recordMidMazeSnapshot(snapshot.currentMazeSnapshot)
        assertEquals(snapshot.currentMazeSnapshot, c.state.currentMazeSnapshot)
    }

    @Test
    fun missingFullRestartOverrideDiscardsCapacityTruncatedSnapshotWithoutLosingLockedTail() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(
            difficulty = DifficultyPresets.HARD.copy(mazeWidth = 4, mazeHeight = 4),
            baseNpcsPerMaze = 20
        )
        val controller = AdventureRunController(config, runSeed = 19L, elitesEnabled = false)
        val spec = controller.prepareCurrentMaze()!!
        val engine = GameEngine(spec.difficulty, spec.seed)
        engine.configureAdventureMaze(spec.npcCount, spec.npcPolicies, npcSpawnSpecs = spec.npcSpawnSpecs)
        engine.restart(spec.seed)
        val truncated = engine.snapshot().copy(npcSpawnSpecs = null)
        assertEquals(14, truncated.npcs.size)
        assertEquals(20, spec.npcSpawnSpecs.size)
        controller.recordMidMazeSnapshot(truncated)
        assertNull(controller.state.currentMazeSnapshot)

        val saved = AdventureRunStateSnapshot.fromState(controller.state, 19L)
            .copy(currentMazeSnapshot = truncated)
        val restored = AdventureRunStateSnapshot.fromJson(saved.toJson(), config)!!
        assertNull(restored.currentMazeSnapshot)
        assertEquals(spec.npcSpawnSpecs, restored.currentMazeNpcSpawnSpecs)
        val replay = AdventureRunController(config, restored.toState(), restored.runSeed, elitesEnabled = false)
        assertEquals(spec, replay.prepareCurrentMaze())
    }

    @Test
    fun mismatchedActiveOrFullRestartModifiersDiscardOnlyEmbeddedEngineProgress() {
        val snapshot = partialEngineSnapshot()
        val valid = snapshot.currentMazeSnapshot!!
        val modifiedFull = snapshot.currentMazeNpcSpawnSpecs.toMutableList().apply {
            this[3] = NpcSpawnSpec(NpcPolicyType.PREDICTIVE_CHASE)
        }
        val wrongActive = valid.npcs.map { it.copy(eliteModifier = null) }
        val wrongId = valid.npcs.map { it.copy(id = it.id + 4) }
        val staleSnapshots = listOf(
            valid.copy(npcs = wrongActive),
            valid.copy(npcSpawnSpecs = null),
            valid.copy(npcSpawnSpecs = modifiedFull),
            valid.copy(npcSpawnSpecs = valid.npcSpawnSpecs!!.dropLast(1)),
            valid.copy(npcs = wrongId),
            valid.copy(npcs = listOf(valid.npcs.first(), valid.npcs.first())),
            valid.copy(npcPolicies = valid.npcPolicies.reversed()),
            valid.copy(seed = valid.seed + 1),
            valid.copy(npcCountOverride = valid.npcCountOverride!! + 1)
        )
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        for (stale in staleSnapshots) {
            val c = AdventureRunController(config, snapshot.toState(), snapshot.runSeed, elitesEnabled = false)
            c.recordMidMazeSnapshot(stale)
            assertEquals(valid, c.state.currentMazeSnapshot)
            val restored = AdventureRunStateSnapshot.fromJson(snapshot.copy(currentMazeSnapshot = stale).toJson())!!
            assertNull(restored.currentMazeSnapshot)
            assertEquals(snapshot.currentMazeNpcSpawnSpecs, restored.currentMazeNpcSpawnSpecs)
            assertEquals(snapshot.livesRemaining, restored.livesRemaining)
            assertEquals(snapshot.pendingStartingPowerUp, restored.pendingStartingPowerUp)
            val replay = AdventureRunController(config, restored.toState(), restored.runSeed, elitesEnabled = false)
            assertEquals(snapshot.currentMazeNpcSpawnSpecs, replay.prepareCurrentMaze()!!.npcSpawnSpecs)
        }
    }

    private fun lockedSnapshot(): AdventureRunStateSnapshot = AdventureRunStateSnapshot.fromState(
        AdventureRunState(
            difficultyName = DifficultyPresets.HARD.name,
            currentMazeIndex = 1,
            livesRemaining = 3,
            currentMazeSeed = 1234L,
            currentMazeNpcSpawnSpecs = listOf(
                NpcSpawnSpec(NpcPolicyType.PATROL_GUARD, EliteNpcModifier.TRACKER),
                NpcSpawnSpec(NpcPolicyType.DIRECT_CHASE),
                NpcSpawnSpec(NpcPolicyType.PREDICTIVE_CHASE),
                NpcSpawnSpec(NpcPolicyType.PATROL_GUARD)
            ),
            pendingStartingPowerUp = PowerUpType.SHIELD
        ),
        runSeed = 19L
    )

    private fun partialEngineSnapshot(): AdventureRunStateSnapshot {
        val locked = lockedSnapshot()
        val engine = GameEngine(DifficultyPresets.HARD, locked.currentMazeSeed!!)
        engine.configureAdventureMaze(locked.currentMazeNpcCount!!, locked.currentMazeNpcPolicies,
            npcSpawnSpecs = locked.currentMazeNpcSpawnSpecs)
        engine.restart(locked.currentMazeSeed)
        val full = engine.snapshot()
        return locked.copy(currentMazeSnapshot = full.copy(
            npcs = listOf(full.npcs[1], full.npcs[0]),
            npcPolicies = full.npcPolicies.take(2)
        ))
    }

    private fun assertInvalid(snapshot: AdventureRunStateSnapshot, mutate: JSONObject.() -> Unit) {
        val json = JSONObject(snapshot.toJson()).apply(mutate).toString()
        assertNull(json, AdventureRunStateSnapshot.fromJson(json))
    }
}
