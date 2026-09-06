package com.example.apktest.game.core

import org.json.JSONArray
import org.json.JSONObject

/** Strict readers avoid org.json's silent string/decimal-to-integer coercion. */
internal fun JSONObject.requiredInt(key: String): Int {
    val value = get(key)
    require(value is Int || value is Long)
    val number = (value as Number).toLong()
    require(number in Int.MIN_VALUE..Int.MAX_VALUE)
    return number.toInt()
}

internal fun JSONObject.requiredLong(key: String): Long {
    val value = get(key)
    require(value is Int || value is Long)
    return (value as Number).toLong()
}

internal fun JSONObject.requiredFloat(key: String): Float {
    val value = get(key)
    require(value is Number)
    return value.toFloat().also { require(it.isFinite()) }
}

internal fun JSONObject.requiredString(key: String): String = get(key) as String

internal fun JSONObject.requiredBoolean(key: String): Boolean = get(key) as Boolean

internal fun JSONObject.requiredNullableInt(key: String): Int? {
    require(has(key))
    return if (isNull(key)) null else requiredInt(key)
}

private fun JSONObject.requiredNullableString(key: String): String? {
    require(has(key))
    return if (isNull(key)) null else requiredString(key)
}

internal object AdventureRouteSnapshotCodec {
    fun rewardToJson(reward: PendingAdventureReward): JSONObject = JSONObject().apply {
        put("mazeIndexCompleted", reward.mazeIndexCompleted)
        put("stage", reward.stage.name)
        put("routeChoices", JSONArray().apply {
            reward.routeChoices.forEach { put(choiceToJson(it)) }
        })
        put("powerUpCandidates", JSONArray().apply {
            reward.powerUpCandidates.forEach { put(it.name) }
        })
        put("selectedRouteId", reward.selectedRouteId ?: JSONObject.NULL)
        put("preview", reward.preview?.let { preview ->
            JSONObject().apply {
                put("nextEventMazeIndex", preview.nextEventMazeIndex)
                put("categories", JSONArray().apply { preview.categories.forEach { put(it.name) } })
                put("npcCount", preview.npcCount)
            }
        } ?: JSONObject.NULL)
        put("bonusLifeAwarded", reward.bonusLifeAwarded)
        put("rerollIndex", reward.rerollIndex)
    }

    fun rewardFromJson(obj: JSONObject): PendingAdventureReward = PendingAdventureReward(
        mazeIndexCompleted = obj.requiredInt("mazeIndexCompleted"),
        stage = RewardStage.valueOf(obj.requiredString("stage")),
        routeChoices = obj.getJSONArray("routeChoices").let { array ->
            List(array.length()) { choiceFromJson(array.getJSONObject(it)) }
        },
        powerUpCandidates = obj.getJSONArray("powerUpCandidates").let { array ->
            List(array.length()) { PowerUpType.valueOf(array.get(it) as String) }
        },
        selectedRouteId = obj.requiredNullableString("selectedRouteId"),
        preview = obj.get("preview").let { value ->
            if (value == JSONObject.NULL) null else (value as JSONObject).let { preview ->
                RoutePreview(
                    preview.requiredInt("nextEventMazeIndex"),
                    preview.getJSONArray("categories").let { array ->
                        List(array.length()) { RouteEventCategory.valueOf(array.get(it) as String) }
                    },
                    preview.requiredInt("npcCount")
                )
            }
        },
        bonusLifeAwarded = obj.requiredBoolean("bonusLifeAwarded"),
        rerollIndex = obj.requiredInt("rerollIndex")
    )

    fun activeToJson(route: PendingRouteEvent): JSONObject = JSONObject().apply {
        put("choiceId", route.choiceId)
        put("mazeIndexAppliedTo", route.mazeIndexAppliedTo)
        put("effects", effectsToJson(route.effects))
        put("npcCountDelta", route.npcCountDelta)
        put("npcCount", route.npcCount)
        put("rewardOptionDelta", route.rewardOptionDelta)
        put("pickupLifetimeSeconds", route.pickupLifetimeSeconds?.toDouble() ?: JSONObject.NULL)
    }

    fun activeFromJson(obj: JSONObject): PendingRouteEvent = PendingRouteEvent(
        choiceId = obj.requiredString("choiceId"),
        mazeIndexAppliedTo = obj.requiredInt("mazeIndexAppliedTo"),
        effects = effectsFromJson(obj.getJSONArray("effects")),
        npcCountDelta = obj.requiredInt("npcCountDelta"),
        npcCount = obj.requiredInt("npcCount"),
        rewardOptionDelta = obj.requiredInt("rewardOptionDelta"),
        pickupLifetimeSeconds = obj.get("pickupLifetimeSeconds").let {
            if (it == JSONObject.NULL) null else obj.requiredFloat("pickupLifetimeSeconds")
        }
    )

    fun historyToJson(history: List<RouteEventHistoryEntry>): JSONArray = JSONArray().apply {
        history.forEach { entry ->
            put(JSONObject().apply {
                put("mazeIndexCompleted", entry.mazeIndexCompleted)
                put("choiceId", entry.choiceId)
            })
        }
    }

    fun historyFromJson(array: JSONArray): List<RouteEventHistoryEntry> = List(array.length()) {
        array.getJSONObject(it).let { obj ->
            RouteEventHistoryEntry(obj.requiredInt("mazeIndexCompleted"), obj.requiredString("choiceId"))
        }
    }

    private fun choiceToJson(choice: RouteEventChoice): JSONObject = JSONObject().apply {
        put("id", choice.id)
        put("category", choice.category.name)
        put("effects", effectsToJson(choice.effects))
    }

    private fun choiceFromJson(obj: JSONObject): RouteEventChoice = RouteEventChoice(
        obj.requiredString("id"),
        RouteEventCategory.valueOf(obj.requiredString("category")),
        effectsFromJson(obj.getJSONArray("effects"))
    )

    private fun effectsToJson(effects: List<RouteEventEffect>): JSONArray = JSONArray().apply {
        effects.forEach { effect ->
            put(JSONObject().apply {
                put("type", effect.type.name)
                put("intValue", effect.intValue)
            })
        }
    }

    private fun effectsFromJson(array: JSONArray): List<RouteEventEffect> = List(array.length()) {
        array.getJSONObject(it).let { obj ->
            RouteEventEffect(RouteEventEffectType.valueOf(obj.requiredString("type")), obj.requiredInt("intValue"))
        }
    }

    fun isConsistent(snapshot: AdventureRunStateSnapshot, config: AdventureConfig): Boolean {
        val index = snapshot.currentMazeIndex
        val count = snapshot.currentMazeNpcCount
        val reward = snapshot.pendingReward
        val route = snapshot.activeRoute
        if (index !in 0..config.totalMazes) return false
        if (snapshot.winStreakSinceLastBonus !in 0 until AdventureConfig.STREAK_BONUS_THRESHOLD) return false
        if (snapshot.rewardRerolls !in 0..1) return false
        if (snapshot.routeEventOrdinal !in 0..config.totalMazes / 2) return false
        if (snapshot.nextRouteEventMazeIndex !in
            (2 + snapshot.routeEventOrdinal * 2)..(2 + snapshot.routeEventOrdinal * 3)) return false
        if (snapshot.currentMazeSeed == null) {
            if (count != null || snapshot.currentMazeNpcPolicies.isNotEmpty()) return false
        } else if (count == null || count < 0 || count != snapshot.currentMazeNpcPolicies.size) return false

        when (snapshot.status) {
            AdventureStatus.IN_PROGRESS -> if (index == config.totalMazes || snapshot.livesRemaining <= 0) return false
            AdventureStatus.WON -> if (index != config.totalMazes || snapshot.livesRemaining <= 0) return false
            AdventureStatus.LOST -> if (index == config.totalMazes || snapshot.livesRemaining != 0) return false
        }
        if (snapshot.status != AdventureStatus.IN_PROGRESS) {
            return reward == null && route == null && snapshot.routeHistory.isEmpty() &&
                snapshot.rewardRerolls == 0 && snapshot.routeEventOrdinal == 0 &&
                snapshot.currentMazeSeed == null && snapshot.pendingStartingPowerUp == null
        }
        if (snapshot.routeHistory.any {
            it.mazeIndexCompleted !in 2..index || RouteEventGenerator.choice(it.choiceId) == null
        }) return false
        if (snapshot.routeHistory.zipWithNext().any {
            it.second.mazeIndexCompleted - it.first.mazeIndexCompleted !in 2..3
        }) return false
        if (snapshot.routeHistory.firstOrNull()?.mazeIndexCompleted?.let { it != 2 } == true) return false
        if (snapshot.routeHistory.size + (if (reward?.routeChoices?.isNotEmpty() == true &&
                reward.selectedRouteId == null) 1 else 0) != snapshot.routeEventOrdinal) return false
        if (snapshot.routeHistory.lastOrNull()?.mazeIndexCompleted == index && route == null) return false
        val uncommittedOffer = reward?.routeChoices?.isNotEmpty() == true && reward.selectedRouteId == null
        val previousOffer = snapshot.routeHistory.lastOrNull()?.mazeIndexCompleted
        if (uncommittedOffer && (if (previousOffer == null) index != 2 else index - previousOffer !in 2..3)) {
            return false
        }
        val lastOffer = if (uncommittedOffer) index else previousOffer
        if (lastOffer != null && snapshot.nextRouteEventMazeIndex - lastOffer !in 2..3) return false

        if (route != null) {
            val known = RouteEventGenerator.choice(route.choiceId) ?: return false
            if (route.effects != known.effects || route.mazeIndexAppliedTo != index + 1 || count == null) return false
            if (route.npcCount != count || route.npcCount < 0) return false
            if (reward == null && snapshot.pendingStartingPowerUp == null) return false
            if (snapshot.routeHistory.lastOrNull() != RouteEventHistoryEntry(index, route.choiceId)) return false
            if (route.npcCountDelta != when (route.choiceId) {
                    RouteEventGenerator.QUIET_CORRIDOR -> -1
                    RouteEventGenerator.AMBUSH_SHORTCUT -> 1
                    else -> 0
                }) return false
            if (route.rewardOptionDelta != if (route.choiceId == RouteEventGenerator.QUIET_CORRIDOR) -1 else 0) {
                return false
            }
            if (route.choiceId == RouteEventGenerator.CURSED_GATE) {
                val lifetime = route.pickupLifetimeSeconds ?: return false
                if (!lifetime.isFinite() || lifetime < RouteEventGenerator.MIN_PICKUP_LIFETIME_SECONDS ||
                    config.difficulty.name == DifficultyPresets.EASY.name) return false
            } else if (route.pickupLifetimeSeconds != null) return false
            if (route.choiceId == RouteEventGenerator.AMBUSH_SHORTCUT && index + 1 == config.totalMazes) return false
        }
        if (reward == null) return true
        if (reward.mazeIndexCompleted != index || index !in 1 until config.totalMazes ||
            snapshot.pendingStartingPowerUp != null) return false
        if (reward.rerollIndex !in 0..1 || (reward.rerollIndex == 1 && snapshot.rewardRerolls != 0)) return false
        if (reward.powerUpCandidates.distinct().size != reward.powerUpCandidates.size ||
            PowerUpType.GHOST_MODE in reward.powerUpCandidates) return false
        val expectedCount = if (reward.selectedRouteId == RouteEventGenerator.QUIET_CORRIDOR) 2 else 3
        if (reward.powerUpCandidates.size != expectedCount) return false
        val choices = reward.routeChoices
        if (choices.isNotEmpty()) {
            if (choices.size !in 2..3 || choices.map { it.id }.distinct().size != choices.size ||
                choices.any { !RouteEventGenerator.isKnownChoice(it) } ||
                choices.all { it.category == RouteEventCategory.RISKY }) return false
            if (index < 2 || snapshot.nextRouteEventMazeIndex - index !in 2..3) return false
            if (choices.any {
                (it.id == RouteEventGenerator.SCOUT_MAP && snapshot.nextRouteEventMazeIndex >= config.totalMazes) ||
                    (it.id == RouteEventGenerator.AMBUSH_SHORTCUT && index + 1 == config.totalMazes) ||
                    (it.id == RouteEventGenerator.CURSED_GATE && config.difficulty.name == DifficultyPresets.EASY.name)
            }) return false
        }
        if (reward.selectedRouteId != null) {
            if (reward.stage != RewardStage.POWER_UP_CHOICE || route?.choiceId != reward.selectedRouteId ||
                choices.none { it.id == reward.selectedRouteId }) return false
        } else if (route != null || count != null) return false
        when (reward.stage) {
            RewardStage.WIN_ACKNOWLEDGEMENT -> if (reward.rerollIndex != 0) return false
            RewardStage.ROUTE_CHOICE -> if (choices.isEmpty() || reward.rerollIndex != 0) return false
            RewardStage.POWER_UP_CHOICE -> if (choices.isNotEmpty() && reward.selectedRouteId == null) return false
        }
        if (reward.selectedRouteId == RouteEventGenerator.SCOUT_MAP) {
            val preview = reward.preview ?: return false
            if (preview.nextEventMazeIndex != snapshot.nextRouteEventMazeIndex ||
                preview.nextEventMazeIndex !in (index + 2) until config.totalMazes ||
                preview.npcCount != count || preview.categories.size !in 2..3 ||
                preview.categories.all { it == RouteEventCategory.RISKY }) return false
        } else if (reward.preview != null) return false
        return true
    }
}
