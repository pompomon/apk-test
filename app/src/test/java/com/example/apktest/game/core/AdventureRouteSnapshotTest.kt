package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdventureRouteSnapshotTest {
    @Test
    fun newFieldsAreRequiredAndDoNotCoerceInvalidTypes() {
        val snapshot = selectedRouteSnapshot(RouteEventGenerator.AMBUSH_SHORTCUT)
        for (key in listOf("mazeNpcCount", "pendingReward", "activeRoute", "routeHistory",
            "nextRouteEventMazeIndex", "routeEventOrdinal", "rewardRerolls")) {
            assertInvalid(snapshot) { remove(key) }
        }
        for (key in listOf("mazeNpcCount", "nextRouteEventMazeIndex", "routeEventOrdinal",
            "rewardRerolls", "mazeIndex", "lives", "totalSteps")) {
            for (value in listOf("1", 1.5, true, Long.MAX_VALUE)) {
                assertInvalid(snapshot) { put(key, value) }
            }
        }
        assertInvalid(snapshot) { put("runSeed", "1") }
        assertInvalid(snapshot) { put("v", 2) }
        assertInvalid(snapshot) { put("v", 3.5) }
    }

    @Test
    fun unknownPendingIdsEffectsPowerupsAndCategoriesAreRejected() {
        val snapshot = selectedRouteSnapshot(RouteEventGenerator.AMBUSH_SHORTCUT)
        assertInvalid(snapshot) { getJSONObject("activeRoute").put("choiceId", "future_route") }
        assertInvalid(snapshot) {
            getJSONObject("activeRoute").getJSONArray("effects").getJSONObject(0).put("type", "FUTURE_EFFECT")
        }
        assertInvalid(snapshot) {
            getJSONObject("activeRoute").getJSONArray("effects").getJSONObject(0).put("intValue", 200)
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONArray("routeChoices").getJSONObject(0).put("id", "unknown")
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONArray("routeChoices").getJSONObject(0).put("category", "NEW")
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONArray("routeChoices").getJSONObject(0)
                .getJSONArray("effects").getJSONObject(0).put("type", "FUTURE_EFFECT")
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONArray("powerUpCandidates").put(0, "GHOST_MODE")
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONArray("powerUpCandidates").put(0, "FUTURE_POWERUP")
        }
    }

    @Test
    fun inconsistentIndicesPhasesSelectionsAndLockedCountsAreRejected() {
        val snapshot = selectedRouteSnapshot(RouteEventGenerator.AMBUSH_SHORTCUT)
        assertInvalid(snapshot) { put("mazeIndex", 10) }
        assertInvalid(snapshot) { put("mazeNpcCount", -1) }
        assertInvalid(snapshot) { put("mazeNpcCount", snapshot.currentMazeNpcCount!! + 1) }
        assertInvalid(snapshot) { remove("mazeSeed") }
        assertInvalid(snapshot) { put("mazeNpcPolicies", JSONArray()) }
        assertInvalid(snapshot) { put("lives", 0) }
        assertInvalid(snapshot) { put("streak", AdventureConfig.STREAK_BONUS_THRESHOLD) }
        assertInvalid(snapshot) { put("status", AdventureStatus.WON.name) }
        assertInvalid(snapshot) { put("nextRouteEventMazeIndex", 2) }
        assertInvalid(snapshot) { put("routeEventOrdinal", -1) }
        assertInvalid(snapshot) { put("rewardRerolls", 2) }
        assertInvalid(snapshot) { getJSONObject("activeRoute").put("mazeIndexAppliedTo", 4) }
        assertInvalid(snapshot) { getJSONObject("activeRoute").put("npcCountDelta", 0) }
        assertInvalid(snapshot) { getJSONObject("activeRoute").put("npcCount", 0) }
        assertInvalid(snapshot) { getJSONObject("activeRoute").put("rewardOptionDelta", -1) }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("mazeIndexCompleted", 1) }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("stage", RewardStage.ROUTE_CHOICE.name) }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("stage", "UNKNOWN_STAGE") }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("selectedRouteId", "unoffered") }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("selectedRouteId", JSONObject.NULL) }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("rerollIndex", 2) }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("bonusLifeAwarded", "false") }
        assertInvalid(snapshot) { getJSONObject("pendingReward").getJSONArray("powerUpCandidates").remove(0) }
        assertInvalid(snapshot) {
            val powers = getJSONObject("pendingReward").getJSONArray("powerUpCandidates")
            powers.put(1, powers.get(0))
        }
        assertInvalid(snapshot) {
            getJSONArray("routeHistory").getJSONObject(0).put("mazeIndexCompleted", 1)
        }
        assertInvalid(snapshot) { put("pendingPowerUp", PowerUpType.SHIELD.name) }
    }

    @Test
    fun nestedRewardFieldsAndScoutPreviewAreStrict() {
        val snapshot = selectedRouteSnapshot(RouteEventGenerator.SCOUT_MAP)
        for (key in listOf("mazeIndexCompleted", "stage", "routeChoices", "powerUpCandidates",
            "selectedRouteId", "preview", "bonusLifeAwarded", "rerollIndex")) {
            assertInvalid(snapshot) { getJSONObject("pendingReward").remove(key) }
        }
        assertInvalid(snapshot) { getJSONObject("pendingReward").put("preview", JSONObject.NULL) }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONObject("preview").put("nextEventMazeIndex", 9)
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONObject("preview").put("npcCount", -1)
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONObject("preview").put("categories", JSONArray())
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONObject("preview").put("categories", JSONArray(listOf("UNKNOWN")))
        }
        assertInvalid(snapshot) {
            getJSONObject("pendingReward").getJSONObject("preview").put("categories",
                JSONArray(listOf("RISKY", "RISKY")))
        }
        assertInvalid(snapshot) {
            val categories = getJSONObject("pendingReward").getJSONObject("preview")
                .getJSONArray("categories")
            categories.put(0, if (categories.getString(0) == "SAFE") "UTILITY" else "SAFE")
        }
    }

    @Test
    fun activeEffectFieldsCannotBeLostOrSilentlyChanged() {
        val snapshot = selectedRouteSnapshot(RouteEventGenerator.CURSED_GATE)
        for (key in listOf("choiceId", "mazeIndexAppliedTo", "effects", "npcCountDelta", "npcCount",
            "rewardOptionDelta", "pickupLifetimeSeconds")) {
            assertInvalid(snapshot) { getJSONObject("activeRoute").remove(key) }
        }
        for (value in listOf(JSONObject.NULL, -1, 0, 9, "35", "NaN")) {
            assertInvalid(snapshot) { getJSONObject("activeRoute").put("pickupLifetimeSeconds", value) }
        }
        assertInvalid(snapshot) {
            getJSONObject("activeRoute").put("effects", JSONArray())
        }
    }

    @Test
    fun snapshotConversionsDetachEveryNewNestedCollection() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.SCOUT_MAP)
        val c = fixture.controller
        c.chooseRoute(2, RouteEventGenerator.SCOUT_MAP)
        c.state.pendingReward = c.state.pendingReward!!.let { reward ->
            reward.copy(
                routeChoices = reward.routeChoices.map { it.copy(effects = it.effects.toMutableList()) }.toMutableList(),
                powerUpCandidates = reward.powerUpCandidates.toMutableList(),
                preview = reward.preview!!.copy(categories = reward.preview.categories.toMutableList())
            )
        }
        c.state.activeRoute = c.state.activeRoute!!.let { it.copy(effects = it.effects.toMutableList()) }
        c.state.routeHistory = c.state.routeHistory.toMutableList()
        c.state.currentMazeNpcPolicies = c.state.currentMazeNpcPolicies.toMutableList()
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, fixture.seed)
        val original = snapshot.toJson()
        (c.state.pendingReward!!.routeChoices.first().effects as MutableList).clear()
        (c.state.pendingReward!!.routeChoices as MutableList).clear()
        (c.state.pendingReward!!.powerUpCandidates as MutableList).clear()
        (c.state.pendingReward!!.preview!!.categories as MutableList).clear()
        (c.state.activeRoute!!.effects as MutableList).clear()
        (c.state.routeHistory as MutableList).clear()
        (c.state.currentMazeNpcPolicies as MutableList).clear()
        assertEquals(original, snapshot.toJson())

        val mutable = snapshot.copy(
            routeHistory = snapshot.routeHistory.toMutableList(),
            activeRoute = snapshot.activeRoute!!.copy(effects = snapshot.activeRoute.effects.toMutableList()),
            pendingReward = snapshot.pendingReward!!.let { reward ->
                reward.copy(
                    routeChoices = reward.routeChoices.map { it.copy(effects = it.effects.toMutableList()) }.toMutableList(),
                    powerUpCandidates = reward.powerUpCandidates.toMutableList(),
                    preview = reward.preview!!.copy(categories = reward.preview.categories.toMutableList())
                )
            }
        )
        val state = mutable.toState()
        (mutable.pendingReward!!.routeChoices.first().effects as MutableList).clear()
        (mutable.pendingReward.routeChoices as MutableList).clear()
        (mutable.pendingReward.powerUpCandidates as MutableList).clear()
        (mutable.pendingReward.preview!!.categories as MutableList).clear()
        (mutable.activeRoute!!.effects as MutableList).clear()
        (mutable.routeHistory as MutableList).clear()
        assertEquals(original, AdventureRunStateSnapshot.fromState(state, fixture.seed).toJson())
    }

    @Test
    fun engineSnapshotsRestoreOnlyForTheMatchingLockedRouteMaze() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.CURSED_GATE)
        val c = fixture.controller
        c.chooseRoute(2, RouteEventGenerator.CURSED_GATE)
        AdventureRouteEventsTest.finishReward(c)
        val spec = c.prepareCurrentMaze()!!
        val engine = GameEngine(spec.difficulty, spec.seed)
        engine.configureAdventureMaze(spec.npcCount, spec.npcPolicies, spec.pickupLifetimeSeconds)
        engine.restart(spec.seed)
        c.recordMidMazeSnapshot(engine.snapshot())
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, fixture.seed)
        assertEquals(snapshot, AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
        c.recordMidMazeSnapshot(engine.snapshot().copy(seed = 10L))
        assertEquals(snapshot, AdventureRunStateSnapshot.fromState(c.state, fixture.seed))
        val fewerSpawnedNpcs = snapshot.copy(currentMazeSnapshot = engine.snapshot().copy(
            npcs = engine.snapshot().npcs.take(1),
            npcPolicies = engine.snapshot().npcPolicies.take(1)
        ))
        assertEquals(fewerSpawnedNpcs, AdventureRunStateSnapshot.fromJson(fewerSpawnedNpcs.toJson()))
        for (stale in listOf(
            engine.snapshot().copy(seed = 10L),
            engine.snapshot().copy(powerUpPickupLifetimeOverrideSeconds = null),
            engine.snapshot().copy(npcPolicies = emptyList()),
            engine.snapshot().copy(npcCountOverride = spec.npcCount + 1),
            engine.snapshot().copy(status = GameStatus.WIN),
            engine.snapshot().copy(schemaVersion = 1)
        )) {
            val restored = AdventureRunStateSnapshot.fromJson(snapshot.copy(currentMazeSnapshot = stale).toJson())
            assertNotNull(restored)
            assertNull(restored!!.currentMazeSnapshot)
            assertEquals(spec, AdventureRunController(c.config, restored.toState(), fixture.seed, false)
                .prepareCurrentMaze())
        }
    }

    @Test
    fun aCommittedRouteCannotDisappearWhileItsTargetMazeIsActive() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val c = fixture.controller
        c.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT)
        AdventureRouteEventsTest.finishReward(c)
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, fixture.seed)
        assertInvalid(snapshot) { put("activeRoute", JSONObject.NULL) }
        assertInvalid(snapshot) { remove("pendingPowerUp") }
    }

    @Test
    fun staleEngineWithoutALockedRestartIsRejectedButRewardPhaseIsRecoverable() {
        val c = AdventureRunController(AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM), runSeed = 5L)
        val engine = GameEngine(DifficultyPresets.MEDIUM, 5L).snapshot()
        val unprepared = AdventureRunStateSnapshot.fromState(c.state, 5L).copy(currentMazeSnapshot = engine)
        assertNull(AdventureRunStateSnapshot.fromJson(unprepared.toJson()))
        c.prepareCurrentMaze()
        c.completeMaze()
        val reward = AdventureRunStateSnapshot.fromState(c.state, 5L)
        val recovered = AdventureRunStateSnapshot.fromJson(reward.copy(currentMazeSnapshot = engine).toJson())
        assertEquals(reward, recovered)
    }

    private fun selectedRouteSnapshot(id: String): AdventureRunStateSnapshot {
        val fixture = AdventureRouteEventsTest.atRoute(id)
        assertTrue(fixture.controller.chooseRoute(2, id))
        val snapshot = AdventureRunStateSnapshot.fromState(fixture.controller.state, fixture.seed)
        assertNotNull(AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
        return snapshot
    }

    private fun assertInvalid(snapshot: AdventureRunStateSnapshot, mutate: JSONObject.() -> Unit) {
        val json = JSONObject(snapshot.toJson()).apply(mutate).toString()
        assertNull(json, AdventureRunStateSnapshot.fromJson(json))
    }
}
