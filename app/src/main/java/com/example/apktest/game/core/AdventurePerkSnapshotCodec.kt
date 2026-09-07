package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

internal object AdventurePerkSnapshotCodec {
    fun stacksToJson(stacks: List<RunPerkStack>): JSONArray = JSONArray().apply {
        stacks.forEach { stack ->
            put(JSONObject().apply {
                put("id", stack.id.id)
                put("stacks", stack.stacks)
                put("consumed", stack.consumed)
            })
        }
    }

    fun stacksFromJson(array: JSONArray): List<RunPerkStack> = List(array.length()) {
        val obj = array.getJSONObject(it)
        RunPerkStack(requireNotNull(RunPerkId.fromId(obj.requiredString("id"))),
            obj.requiredInt("stacks"), obj.requiredBoolean("consumed"))
    }

    fun idsToJson(ids: List<RunPerkId>): JSONArray = JSONArray().apply { ids.forEach { put(it.id) } }

    fun idsFromJson(array: JSONArray): List<RunPerkId> = List(array.length()) {
        requireNotNull(RunPerkId.fromId(array.get(it) as String))
    }

    fun offerToJson(offer: PendingPerkOffer): JSONObject = JSONObject().apply {
        put("mazeIndexCompleted", offer.mazeIndexCompleted)
        put("ordinal", offer.ordinal)
        put("choices", idsToJson(offer.choices))
        put("ownedAtOffer", stacksToJson(offer.ownedAtOffer))
        put("previousOffer", idsToJson(offer.previousOffer))
        put("tiers", JSONArray().apply { offer.tiers.forEach { put(it.name) } })
        put("routesEnabled", offer.routesEnabled)
    }

    fun offerFromJson(obj: JSONObject): PendingPerkOffer = PendingPerkOffer(
        obj.requiredInt("mazeIndexCompleted"), obj.requiredInt("ordinal"),
        idsFromJson(obj.getJSONArray("choices")), stacksFromJson(obj.getJSONArray("ownedAtOffer")),
        idsFromJson(obj.getJSONArray("previousOffer")),
        obj.getJSONArray("tiers").let { array ->
            List(array.length()) { RunPerkTier.valueOf(array.get(it) as String) }
        },
        obj.requiredBoolean("routesEnabled")
    )

    fun historyToJson(history: List<RunPerkHistoryEntry>): JSONArray = JSONArray().apply {
        history.forEach {
            put(JSONObject().apply {
                put("offer", offerToJson(it.offer))
                put("selectedPerkId", it.selectedPerkId.id)
            })
        }
    }

    fun historyFromJson(array: JSONArray): List<RunPerkHistoryEntry> = List(array.length()) {
        val obj = array.getJSONObject(it)
        RunPerkHistoryEntry(offerFromJson(obj.getJSONObject("offer")),
            requireNotNull(RunPerkId.fromId(obj.requiredString("selectedPerkId"))))
    }

    fun isConsistent(snapshot: AdventureRunStateSnapshot, config: AdventureConfig): Boolean {
        if (!RunPerkCatalogue.validStacks(snapshot.runPerks) ||
            snapshot.perkOfferOrdinal !in 0..RunPerkGenerator.OFFER_MAZES.size) return false
        val reward = snapshot.pendingReward
        val pending = reward?.perkOffer
        val selectedPerkId = reward?.selectedPerkId
        val unselected = pending != null && selectedPerkId == null
        if (snapshot.perkHistory.size + (if (unselected) 1 else 0) != snapshot.perkOfferOrdinal) return false
        if (snapshot.previousPerkOffer !=
            (pending ?: snapshot.perkHistory.lastOrNull()?.offer)?.choices.orEmpty()) return false
        var previousMaze = 0
        var previousOffer = emptyList<RunPerkId>()
        var owned = emptyList<RunPerkStack>()
        val generator = RunPerkGenerator(snapshot.runSeed)
        val offers = snapshot.perkHistory.map { it.offer } + if (unselected) listOf(pending!!) else emptyList()
        for ((ordinal, offer) in offers.withIndex()) {
            if (offer.ordinal != ordinal || offer.mazeIndexCompleted <= previousMaze ||
                offer.mazeIndexCompleted !in RunPerkGenerator.OFFER_MAZES ||
                offer.mazeIndexCompleted >= config.totalMazes ||
                offer.mazeIndexCompleted > snapshot.currentMazeIndex ||
                offer.previousOffer != previousOffer ||
                !compatibleOwnership(owned, offer.ownedAtOffer)) return false
            if (offer.tiers.isEmpty() || offer.tiers.distinct().size != offer.tiers.size) return false
            val expected = generator.offer(offer.mazeIndexCompleted, ordinal, offer.ownedAtOffer,
                offer.previousOffer, offer.tiers.toSet(), offer.routesEnabled)
            if (offer != expected) return false
            owned = offer.ownedAtOffer
            val selection = snapshot.perkHistory.getOrNull(ordinal)?.selectedPerkId
            if (selection != null) {
                if (selection !in offer.choices) return false
                owned = if (owned.none { it.id == selection }) owned + RunPerkStack(selection, 1)
                else owned.map { if (it.id == selection) it.copy(stacks = it.stacks + 1) else it }
            }
            previousMaze = offer.mazeIndexCompleted
            previousOffer = offer.choices
        }
        if (!compatibleOwnership(owned, snapshot.runPerks)) return false
        if (pending != null) {
            if (pending.mazeIndexCompleted != snapshot.currentMazeIndex) return false
            if (selectedPerkId != null &&
                snapshot.perkHistory.lastOrNull() != RunPerkHistoryEntry(pending, selectedPerkId)) return false
        } else if (reward != null && (reward.selectedPerkId != null ||
                snapshot.perkHistory.lastOrNull()?.offer?.mazeIndexCompleted == snapshot.currentMazeIndex)) return false

        if (reward == null) return true
        if (reward.selectedPerkId == RunPerkId.SECOND_WIND &&
            snapshot.runPerks.any { it.id == RunPerkId.SECOND_WIND && it.consumed }) return false
        when (reward.stage) {
            RewardStage.WIN_ACKNOWLEDGEMENT, RewardStage.ROUTE_CHOICE ->
                if (reward.selectedPerkId != null || reward.scoutPreview != null) return false
            RewardStage.PERK_CHOICE ->
                if (pending == null || reward.selectedPerkId != null || reward.rerollIndex != 0) return false
            RewardStage.POWER_UP_CHOICE ->
                if (pending != null && reward.selectedPerkId == null) return false
        }
        val shouldPreview = snapshot.runPerks.any { it.id == RunPerkId.SCOUT_SENSE } &&
            (reward.stage == RewardStage.PERK_CHOICE || reward.stage == RewardStage.POWER_UP_CHOICE)
        if (shouldPreview != (reward.scoutPreview != null)) return false
        if (shouldPreview) {
            val seed = snapshot.currentMazeSeed ?: return false
            val count = snapshot.currentMazeNpcCount ?: return false
            if (snapshot.activeRoute == null && count != config.npcCountForMaze(snapshot.currentMazeIndex + 1)) {
                return false
            }
            val maze = MazeGenerator.generate(config.difficulty.mazeWidth, config.difficulty.mazeHeight, seed)
            val plan = NpcSpawnPlanner.plan(maze, MazeNavigator(maze), config.difficulty, Random(seed))
            val actualSpecs = snapshot.currentMazeNpcSpawnSpecs.take(plan.candidates.size)
            if (reward.scoutPreview != PerkScoutPreview(actualSpecs.size,
                    actualSpecs.count { it.eliteModifier != null })) return false
        }
        val earnedRiskRoute = snapshot.routeHistory.lastOrNull {
            it.mazeIndexCompleted == reward.mazeIndexCompleted - 1
        }?.takeIf {
            RouteEventGenerator.choice(it.choiceId)?.category == RouteEventCategory.RISKY &&
                snapshot.perkHistory.any { history ->
                    history.selectedPerkId == RunPerkId.RISK_DIVIDEND &&
                        history.offer.mazeIndexCompleted < reward.mazeIndexCompleted
                }
        }?.choiceId
        if (reward.rewardOptionBonus != (if (earnedRiskRoute == null) 0 else 1) ||
            reward.riskDividendRouteId != earnedRiskRoute) return false
        return true
    }

    /** Consumption may advance between offers, but an expended one-shot never becomes available again. */
    private fun compatibleOwnership(previous: List<RunPerkStack>, next: List<RunPerkStack>): Boolean =
        RunPerkCatalogue.validStacks(next) && previous.size == next.size &&
            previous.zip(next).all { (before, after) ->
                before.id == after.id && before.stacks == after.stacks && (!before.consumed || after.consumed)
            }
}
