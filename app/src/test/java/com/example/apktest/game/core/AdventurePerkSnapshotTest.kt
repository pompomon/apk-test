package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdventurePerkSnapshotTest {
    @Test
    fun schemaFiveRequiresEveryPerkFieldIncludingExplicitEmptyState() {
        val c = AdventureRunController(AdventurePerkControllerTest.medium(), runSeed = 0L)
        val empty = snapshot(c)
        assertEquals(5, AdventureRunStateSnapshot.SCHEMA_VERSION)
        assertEquals(empty, restore(empty))
        for (key in listOf("runPerks", "previousPerkOffer", "perkOfferOrdinal", "perkHistory")) {
            assertInvalid(empty) { remove(key) }
        }
        assertInvalid(empty) { put("v", 4) }
        for (value in listOf(-1, 5, "1", 1.5, true, Long.MAX_VALUE)) {
            assertInvalid(empty) { put("perkOfferOrdinal", value) }
        }
    }

    @Test
    fun everyRewardPhaseRoundTripsWithoutRerollingTheOffer() {
        val c = AdventureRunController(AdventurePerkControllerTest.medium(), runSeed = 0L,
            perksEnabled = true, perkTiers = RunPerkTier.entries.toSet())
        c.prepareCurrentMaze()
        c.completeMaze()
        assertEquals(snapshot(c), restore(snapshot(c)))
        c.acknowledgeMazeWin(1)
        assertEquals(RewardStage.PERK_CHOICE, c.state.pendingReward!!.stage)
        assertEquals(snapshot(c), restore(snapshot(c)))
        c.choosePerk(1, c.state.pendingReward!!.perkOffer!!.choices.first())
        assertEquals(snapshot(c), restore(snapshot(c)))
        AdventurePerkControllerTest.finishReward(c)
        assertEquals(snapshot(c), restore(snapshot(c)))
        c.prepareCurrentMaze()
        assertEquals(snapshot(c), restore(snapshot(c)))
    }

    @Test
    fun pendingRewardPerkFieldsAreStrictAndRequired() {
        val saved = pending()
        for (key in listOf("perkOffer", "selectedPerkId", "scoutPreview", "rewardOptionBonus", "riskDividendRouteId")) {
            assertInvalid(saved) { getJSONObject("pendingReward").remove(key) }
        }
        for (key in listOf("mazeIndexCompleted", "ordinal", "choices", "ownedAtOffer", "previousOffer", "tiers", "routesEnabled")) {
            assertInvalid(saved) { getJSONObject("pendingReward").getJSONObject("perkOffer").remove(key) }
        }
        assertInvalid(saved) { getJSONObject("pendingReward").put("perkOffer", JSONObject.NULL) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("selectedPerkId", "unknown") }
        assertInvalid(saved) { getJSONObject("pendingReward").put("selectedPerkId", "first_shield") }
        assertInvalid(saved) { getJSONObject("pendingReward").put("stage", "POWER_UP_CHOICE") }
        assertInvalid(saved) { getJSONObject("pendingReward").put("rerollIndex", 1) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("rewardOptionBonus", 1) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("riskDividendRouteId", "ambush_shortcut") }
    }

    @Test
    fun offerIdsCapsOrdinalHistoryAndGenerationContextCannotBeForged() {
        val saved = pending()
        assertInvalid(saved) {
            getJSONObject("pendingReward").getJSONObject("perkOffer").getJSONArray("choices").put(0, "unknown")
        }
        assertInvalid(saved) {
            getJSONObject("pendingReward").getJSONObject("perkOffer").getJSONArray("choices").put(0, "first_shield")
        }
        assertInvalid(saved) {
            val choices = getJSONObject("pendingReward").getJSONObject("perkOffer").getJSONArray("choices")
            choices.put(1, choices.get(0))
        }
        assertInvalid(saved) { put("previousPerkOffer", JSONArray()) }
        assertInvalid(saved) { put("perkOfferOrdinal", 0) }
        for (value in listOf(2, 7, "1", 1.5)) {
            assertInvalid(saved) { getJSONObject("pendingReward").getJSONObject("perkOffer").put("mazeIndexCompleted", value) }
        }
        assertInvalid(saved) { getJSONObject("pendingReward").getJSONObject("perkOffer").put("ordinal", 1) }
        assertInvalid(saved) {
            getJSONObject("pendingReward").getJSONObject("perkOffer").put("previousOffer", JSONArray(listOf("quick_feet")))
        }
        assertInvalid(saved) {
            getJSONObject("pendingReward").getJSONObject("perkOffer").put("ownedAtOffer",
                JSONArray().put(stackJson(RunPerkId.QUICK_FEET)))
        }
        for (tiers in listOf(emptyList(), listOf("FUTURE"), listOf("COMMON", "COMMON"), listOf("RARE"))) {
            assertInvalid(saved) {
                getJSONObject("pendingReward").getJSONObject("perkOffer").put("tiers", JSONArray(tiers))
            }
        }
        assertInvalid(saved) {
            getJSONObject("pendingReward").getJSONObject("perkOffer").put("routesEnabled", "false")
        }
    }

    @Test
    fun ownedIdsStacksAndConsumptionAreValidatedAgainstAcquisitionHistory() {
        val saved = snapshot(AdventurePerkControllerTest.acquired(RunPerkId.QUICK_FEET))
        assertEquals(saved, restore(saved))
        assertInvalid(saved) { getJSONArray("runPerks").put(stackJson(RunPerkId.QUICK_FEET)) }
        assertInvalid(saved) { getJSONArray("runPerks").put(stackJson(RunPerkId.FIRST_SHIELD)) }
        assertInvalid(saved) { getJSONArray("runPerks").getJSONObject(0).put("id", "QUICK_FEET") }
        assertInvalid(saved) { getJSONArray("runPerks").getJSONObject(0).put("consumed", true) }
        assertInvalid(saved) { getJSONArray("runPerks").getJSONObject(0).remove("consumed") }
        assertInvalid(saved) { getJSONArray("runPerks").getJSONObject(0).put("consumed", "false") }
        for (stacks in listOf(0, -1, 2, 4, 1.5, "1")) {
            assertInvalid(saved) { getJSONArray("runPerks").getJSONObject(0).put("stacks", stacks) }
        }
        assertInvalid(saved) { put("perkHistory", JSONArray()) }
        assertInvalid(saved) { getJSONArray("perkHistory").put(getJSONArray("perkHistory").get(0)) }
        assertInvalid(saved) { getJSONArray("perkHistory").getJSONObject(0).put("selectedPerkId", "second_wind") }
    }

    @Test
    fun selectedPerkAndOwnershipCannotDisappearFromPowerUpStage() {
        val c = AdventureRunController(AdventurePerkControllerTest.medium(), runSeed = 0L, perksEnabled = true)
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(1)
        c.choosePerk(1, RunPerkId.QUICK_FEET)
        val saved = snapshot(c)
        assertInvalid(saved) { getJSONObject("pendingReward").put("selectedPerkId", JSONObject.NULL) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("perkOffer", JSONObject.NULL) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("stage", "PERK_CHOICE") }
        assertInvalid(saved) {
            getJSONObject("pendingReward").put("perkOffer", JSONObject.NULL).put("selectedPerkId", JSONObject.NULL)
        }
        assertInvalid(saved) { put("runPerks", JSONArray()) }
    }

    @Test
    fun scoutPreviewRequiresOwnedScoutAndExactLockedActualCounts() {
        val c = AdventureRunController(AdventurePerkControllerTest.medium(), runSeed = 0L,
            perksEnabled = true, perkTiers = setOf(RunPerkTier.UNCOMMON))
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(1)
        c.choosePerk(1, RunPerkId.SCOUT_SENSE)
        val saved = snapshot(c)
        assertEquals(saved, restore(saved))
        assertInvalid(saved) { getJSONObject("pendingReward").put("scoutPreview", JSONObject.NULL) }
        assertInvalid(saved) { remove("mazeSeed") }
        assertInvalid(saved) { put("mazeNpcCount", JSONObject.NULL) }
        assertInvalid(saved) { getJSONObject("pendingReward").getJSONObject("scoutPreview").put("npcCount", 999) }
        assertInvalid(saved) { getJSONObject("pendingReward").getJSONObject("scoutPreview").put("eliteCount", 1) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("stage", "WIN_ACKNOWLEDGEMENT") }
        val notScout = pending()
        assertInvalid(notScout) {
            getJSONObject("pendingReward").put("scoutPreview", JSONObject().put("npcCount", 1).put("eliteCount", 0))
        }
    }

    @Test
    fun riskDividendRewardRetainsProvenanceAndCannotBeAddedToOrdinaryRewards() {
        val (c, seed) = AdventurePerkControllerTest.atRouteWithPerk(
            RunPerkId.RISK_DIVIDEND, RouteEventGenerator.AMBUSH_SHORTCUT)
        c.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT)
        AdventurePerkControllerTest.finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        val saved = AdventureRunStateSnapshot.fromState(c.state, seed)
        assertEquals(saved, restore(saved))
        assertInvalid(saved) { getJSONObject("pendingReward").put("rewardOptionBonus", 0) }
        assertInvalid(saved) {
            getJSONObject("pendingReward").apply {
                put("rewardOptionBonus", 0)
                put("riskDividendRouteId", JSONObject.NULL)
                getJSONArray("powerUpCandidates").remove(3)
            }
        }
        assertInvalid(saved) { getJSONObject("pendingReward").put("rewardOptionBonus", 2) }
        assertInvalid(saved) { getJSONObject("pendingReward").put("riskDividendRouteId", "supply_cache") }
        assertInvalid(saved) { getJSONObject("pendingReward").put("riskDividendRouteId", JSONObject.NULL) }
        assertInvalid(saved) { put("routeHistory", JSONArray()) }
        assertInvalid(saved) { getJSONArray("perkHistory").getJSONObject(0).put("selectedPerkId", "second_wind") }
        assertInvalid(pending()) {
            val reward = getJSONObject("pendingReward")
            reward.put("rewardOptionBonus", 1)
            reward.put("riskDividendRouteId", "ambush_shortcut")
            val powers = reward.getJSONArray("powerUpCandidates")
            powers.put(PowerUpType.entries.first { power ->
                power != PowerUpType.GHOST_MODE && (0 until powers.length()).none { powers.getString(it) == power.name }
            }.name)
        }
    }

    @Test
    fun midMazeSnapshotsMustMatchEveryDerivedEffect() {
        val c = AdventurePerkControllerTest.acquired(RunPerkId.QUICK_FEET)
        val engine = engine(c)
        val correct = engine.snapshot()
        c.recordMidMazeSnapshot(correct)
        assertEquals(correct, c.state.currentMazeSnapshot)
        val saved = snapshot(c)
        assertEquals(saved, restore(saved))
        for (effects in listOf(RunPerkEffects(), RunPerkEffects(quickFeetStacks = 2),
            RunPerkEffects(quickFeetStacks = 1, longerChargeStacks = 1),
            RunPerkEffects(quickFeetStacks = 1, pocketMagnetStacks = 1),
            RunPerkEffects(quickFeetStacks = 1, secondWindAvailable = true))) {
            val stale = correct.copy(runPerkEffects = effects)
            c.recordMidMazeSnapshot(stale)
            assertEquals(correct, c.state.currentMazeSnapshot)
            assertNull(restore(saved.copy(currentMazeSnapshot = stale))!!.currentMazeSnapshot)
        }
    }

    @Test
    fun consumptionMustPrecedeRecordingBarrierAndStaleAvailableSnapshotCannotReplenishIt() {
        val c = AdventurePerkControllerTest.acquired(RunPerkId.SECOND_WIND)
        val available = engine(c).snapshot()
        val barrier = barrier(available)
        c.recordMidMazeSnapshot(barrier)
        assertNull(c.state.currentMazeSnapshot)
        assertTrue(c.consumePerk(RunPerkId.SECOND_WIND))
        c.recordMidMazeSnapshot(barrier)
        assertEquals(barrier, c.state.currentMazeSnapshot)
        val saved = snapshot(c)
        assertEquals(saved, restore(saved))
        c.recordMidMazeSnapshot(available)
        assertEquals(barrier, c.state.currentMazeSnapshot)
        val resumed = restore(saved.copy(currentMazeSnapshot = available))!!
        assertNull(resumed.currentMazeSnapshot)
        assertTrue(resumed.runPerks.single().consumed)
        val restart = AdventureRunController(c.config, resumed.toState(), 0L).prepareCurrentMaze()!!
        assertFalse(restart.runPerkEffects.secondWindAvailable)
    }

    @Test
    fun validPendingMarkerReconcilesUnconsumedControllerButNeverAnUnrelatedMaze() {
        val c = AdventurePerkControllerTest.acquired(RunPerkId.SECOND_WIND)
        val available = engine(c).snapshot()
        val saved = snapshot(c).copy(currentMazeSnapshot = barrier(available))
        val restored = restore(saved)!!
        assertTrue(restored.runPerks.single().consumed)
        assertEquals(saved.currentMazeSnapshot, restored.currentMazeSnapshot)
        val stale = restore(saved.copy(currentMazeSnapshot = barrier(available).copy(seed = 91L)))!!
        assertNull(stale.currentMazeSnapshot)
        assertFalse(stale.runPerks.single().consumed)
    }

    @Test
    fun corruptEmbeddedBarrierFallsBackToRestartWithConsumedStateIntact() {
        val c = AdventurePerkControllerTest.acquired(RunPerkId.SECOND_WIND)
        val marker = barrier(engine(c).snapshot())
        c.consumePerk(RunPerkId.SECOND_WIND)
        c.recordMidMazeSnapshot(marker)
        val saved = snapshot(c)
        for (corrupt in listOf(
            marker.copy(schemaVersion = 7),
            marker.copy(activeEffects = emptyList()),
            marker.copy(activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 10f))),
            marker.copy(pendingConsumedRunPerk = RunPerkId.FIRST_SHIELD),
            marker.copy(runPerkEffects = RunPerkEffects(secondWindAvailable = true))
        )) {
            val restored = restore(saved.copy(currentMazeSnapshot = corrupt))!!
            assertNull(restored.currentMazeSnapshot)
            assertTrue(restored.runPerks.single().consumed)
        }
        c.onPlayerDied()
        assertFalse(c.prepareCurrentMaze()!!.runPerkEffects.secondWindAvailable)
    }

    @Test
    fun markerWithoutOwnedSecondWindCannotManufactureOwnership() {
        val c = AdventureRunController(AdventurePerkControllerTest.medium(), runSeed = 0L)
        val marker = barrier(engine(c).snapshot())
        c.recordMidMazeSnapshot(marker)
        assertNull(c.state.currentMazeSnapshot)
        val restored = restore(snapshot(c).copy(currentMazeSnapshot = marker))!!
        assertNull(restored.currentMazeSnapshot)
        assertTrue(restored.runPerks.isEmpty())
    }

    @Test
    fun snapshotAndStateConversionsDetachPerksOffersAndEveryHistoryCollection() {
        val c = AdventurePerkControllerTest.acquired(RunPerkId.QUICK_FEET)
        c.prepareCurrentMaze()
        c.completeMaze()
        AdventurePerkControllerTest.finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        c.state.runPerks = c.state.runPerks.toMutableList()
        c.state.previousPerkOffer = c.state.previousPerkOffer.toMutableList()
        c.state.perkHistory = c.state.perkHistory.map { it.copy(offer = mutableOffer(it.offer)) }.toMutableList()
        c.state.pendingReward = c.state.pendingReward!!.let { it.copy(perkOffer = mutableOffer(it.perkOffer!!)) }
        val saved = snapshot(c)
        val original = saved.toJson()
        clearOffer(c.state.pendingReward!!.perkOffer!!)
        c.state.perkHistory.forEach { clearOffer(it.offer) }
        (c.state.perkHistory as MutableList).clear()
        (c.state.runPerks as MutableList).clear()
        (c.state.previousPerkOffer as MutableList).clear()
        assertEquals(original, saved.toJson())
        val mutable = saved.copy(
            runPerks = saved.runPerks.toMutableList(),
            previousPerkOffer = saved.previousPerkOffer.toMutableList(),
            perkHistory = saved.perkHistory.map { it.copy(offer = mutableOffer(it.offer)) }.toMutableList(),
            pendingReward = saved.pendingReward!!.let { it.copy(perkOffer = mutableOffer(it.perkOffer!!)) }
        )
        val state = mutable.toState()
        clearOffer(mutable.pendingReward!!.perkOffer!!)
        mutable.perkHistory.forEach { clearOffer(it.offer) }
        (mutable.perkHistory as MutableList).clear()
        (mutable.runPerks as MutableList).clear()
        (mutable.previousPerkOffer as MutableList).clear()
        assertEquals(original, snapshot(AdventureRunController(AdventurePerkControllerTest.medium(), state, 0L)).toJson())
    }

    private fun mutableOffer(offer: PendingPerkOffer): PendingPerkOffer = offer.copy(
        choices = offer.choices.toMutableList(), ownedAtOffer = offer.ownedAtOffer.toMutableList(),
        previousOffer = offer.previousOffer.toMutableList(), tiers = offer.tiers.toMutableList()
    )

    private fun clearOffer(offer: PendingPerkOffer) {
        (offer.choices as MutableList).clear()
        (offer.ownedAtOffer as MutableList).clear()
        (offer.previousOffer as MutableList).clear()
        (offer.tiers as MutableList).clear()
    }

    private fun pending(): AdventureRunStateSnapshot {
        val c = AdventureRunController(AdventurePerkControllerTest.medium(), runSeed = 0L, perksEnabled = true)
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(1)
        return snapshot(c).also { assertNotNull(restore(it)) }
    }

    private fun engine(c: AdventureRunController): GameEngine {
        val spec = c.prepareCurrentMaze()!!
        return GameEngine(spec.difficulty, spec.seed).apply {
            configureAdventureMaze(spec.npcCount, spec.npcPolicies, spec.pickupLifetimeSeconds,
                spec.npcSpawnSpecs, spec.runPerkEffects)
            restart(spec.seed)
        }
    }

    private fun barrier(available: GameEngineSnapshot): GameEngineSnapshot = available.copy(
        runPerkEffects = available.runPerkEffects.copy(secondWindAvailable = false),
        pendingConsumedRunPerk = RunPerkId.SECOND_WIND,
        activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 1f))
    )

    private fun snapshot(c: AdventureRunController): AdventureRunStateSnapshot =
        AdventureRunStateSnapshot.fromState(c.state, 0L)

    private fun restore(snapshot: AdventureRunStateSnapshot): AdventureRunStateSnapshot? =
        AdventureRunStateSnapshot.fromJson(snapshot.toJson())

    private fun assertInvalid(saved: AdventureRunStateSnapshot, mutate: JSONObject.() -> Unit) {
        val json = JSONObject(saved.toJson()).apply(mutate).toString()
        assertNull(json, AdventureRunStateSnapshot.fromJson(json))
    }

    private fun stackJson(id: RunPerkId): JSONObject =
        JSONObject().put("id", id.id).put("stacks", 1).put("consumed", false)
}
