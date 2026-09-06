package com.example.apktest.game.core

import kotlin.random.Random

enum class RouteEventCategory { SAFE, RISKY, UTILITY }

enum class RouteEventEffectType {
    NPC_COUNT_DELTA,
    STARTING_POWER_UP_CHOICE,
    POWER_UP_LIFETIME_DELTA,
    REWARD_REROLL,
    REWARD_OPTION_COUNT_DELTA,
    STREAK_PROGRESS_DELTA,
    NEXT_ROUTE_PREVIEW
}

data class RouteEventEffect(val type: RouteEventEffectType, val intValue: Int = 0)

data class RouteEventChoice(
    val id: String,
    val category: RouteEventCategory,
    val effects: List<RouteEventEffect>
)

enum class RewardStage { WIN_ACKNOWLEDGEMENT, ROUTE_CHOICE, POWER_UP_CHOICE }

data class RoutePreview(
    /** Completed-maze index at which the previewed offer will appear (1-based). */
    val nextEventMazeIndex: Int,
    val categories: List<RouteEventCategory>,
    /** Locked NPC count in the maze immediately following the Scout choice. */
    val npcCount: Int
)

data class PendingAdventureReward(
    val mazeIndexCompleted: Int,
    val stage: RewardStage,
    val routeChoices: List<RouteEventChoice>,
    val powerUpCandidates: List<PowerUpType>,
    val selectedRouteId: String? = null,
    val preview: RoutePreview? = null,
    val bonusLifeAwarded: Boolean = false,
    val rerollIndex: Int = 0
)

/** Resolved, per-maze effects; independent of the current rollout flag. */
data class PendingRouteEvent(
    val choiceId: String,
    val mazeIndexAppliedTo: Int,
    val effects: List<RouteEventEffect>,
    val npcCountDelta: Int,
    val npcCount: Int,
    val rewardOptionDelta: Int,
    val pickupLifetimeSeconds: Float?
)

data class RouteEventHistoryEntry(val mazeIndexCompleted: Int, val choiceId: String)

/**
 * The offer stream depends only on the seed, ordinal and fixed run configuration.
 * In particular, neither earlier choices nor reroll balance can invalidate a Scout preview.
 */
class RouteEventGenerator(private val config: AdventureConfig, private val runSeed: Long) {
    fun nextEventMazeIndex(mazeIndexCompleted: Int, ordinal: Int): Int =
        mazeIndexCompleted + 2 + Random(runSeed xor CADENCE_SALT xor ordinal.toLong() * ORDINAL_STRIDE)
            .nextInt(2)

    fun offer(mazeIndexCompleted: Int, ordinal: Int): List<RouteEventChoice> {
        if (mazeIndexCompleted < FIRST_EVENT_MAZE_INDEX || mazeIndexCompleted >= config.totalMazes) {
            return emptyList()
        }
        val nextEvent = nextEventMazeIndex(mazeIndexCompleted, ordinal)
        val pool = catalogue.filter { choice ->
            when (choice.id) {
                QUIET_CORRIDOR -> quietNpcDelta(mazeIndexCompleted + 1) < 0
                AMBUSH_SHORTCUT -> mazeIndexCompleted + 1 < config.totalMazes
                SUPPLY_CACHE -> true
                SCOUT_MAP -> nextEvent < config.totalMazes
                CURSED_GATE -> config.difficulty.name != DifficultyPresets.EASY.name &&
                    config.difficulty.powerUpPickupLifetimeSeconds.isFinite() &&
                    config.difficulty.powerUpPickupLifetimeSeconds > MIN_PICKUP_LIFETIME_SECONDS
                else -> error("Unknown route ${choice.id}")
            }
        }
        check(pool.size >= 2) { "An eligible event needs at least two meaningful routes" }
        val rng = Random(runSeed xor OFFER_SALT xor ordinal.toLong() * ORDINAL_STRIDE xor
            mazeIndexCompleted.toLong() * MAZE_STRIDE)
        val shuffled = pool.shuffled(rng)
        val count = (2 + rng.nextInt(2)).coerceAtMost(shuffled.size)
        val selected = shuffled.take(count).toMutableList()
        if (selected.all { it.category == RouteEventCategory.RISKY }) {
            selected[selected.lastIndex] = shuffled.first { it.category != RouteEventCategory.RISKY }
        }
        return selected.map { it.detachedCopy() }
    }

    fun resolve(choice: RouteEventChoice, mazeIndexAppliedTo: Int): PendingRouteEvent {
        require(isKnownChoice(choice))
        require(mazeIndexAppliedTo in 1..config.totalMazes)
        val npcDelta = when (choice.id) {
            QUIET_CORRIDOR -> quietNpcDelta(mazeIndexAppliedTo)
            AMBUSH_SHORTCUT -> 1
            else -> 0
        }
        return PendingRouteEvent(
            choiceId = choice.id,
            mazeIndexAppliedTo = mazeIndexAppliedTo,
            effects = choice.effects.toList(),
            npcCountDelta = npcDelta,
            npcCount = config.npcCountForMaze(mazeIndexAppliedTo) + npcDelta,
            rewardOptionDelta = if (choice.id == QUIET_CORRIDOR) -1 else 0,
            pickupLifetimeSeconds = if (choice.id == CURSED_GATE) {
                (config.difficulty.powerUpPickupLifetimeSeconds - CURSED_LIFETIME_PENALTY_SECONDS)
                    .coerceAtLeast(MIN_PICKUP_LIFETIME_SECONDS)
            } else null
        )
    }

    private fun quietNpcDelta(target: Int): Int {
        val baseline = config.npcCountForMaze(target)
        val minimum = if (config.baseNpcsPerMaze == 0) 0 else 1
        val floor = if (target == config.totalMazes && target > 1) {
            maxOf(minimum, config.npcCountForMaze(target - 1))
        } else minimum
        return (baseline - 1).coerceAtLeast(floor) - baseline
    }

    companion object {
        const val FIRST_EVENT_MAZE_INDEX = 2
        const val QUIET_CORRIDOR = "quiet_corridor"
        const val AMBUSH_SHORTCUT = "ambush_shortcut"
        const val SUPPLY_CACHE = "supply_cache"
        const val SCOUT_MAP = "scout_map"
        const val CURSED_GATE = "cursed_gate"
        const val MIN_PICKUP_LIFETIME_SECONDS = 10f
        const val CURSED_LIFETIME_PENALTY_SECONDS = 10f

        private const val CADENCE_SALT = 0x1325A793B91E713L
        private const val OFFER_SALT = 0x689CAB13F720456L
        private const val ORDINAL_STRIDE = 0x243F6A8885A308D3L
        private const val MAZE_STRIDE = 0x12B9B0A1CE4A11BL

        private val catalogue = listOf(
            RouteEventChoice(QUIET_CORRIDOR, RouteEventCategory.SAFE, listOf(
                RouteEventEffect(RouteEventEffectType.NPC_COUNT_DELTA, -1),
                RouteEventEffect(RouteEventEffectType.REWARD_OPTION_COUNT_DELTA, -1)
            )),
            RouteEventChoice(AMBUSH_SHORTCUT, RouteEventCategory.RISKY, listOf(
                RouteEventEffect(RouteEventEffectType.NPC_COUNT_DELTA, 1),
                RouteEventEffect(RouteEventEffectType.REWARD_REROLL, 1)
            )),
            RouteEventChoice(SUPPLY_CACHE, RouteEventCategory.UTILITY, listOf(
                RouteEventEffect(RouteEventEffectType.STARTING_POWER_UP_CHOICE)
            )),
            RouteEventChoice(SCOUT_MAP, RouteEventCategory.SAFE, listOf(
                RouteEventEffect(RouteEventEffectType.NEXT_ROUTE_PREVIEW)
            )),
            RouteEventChoice(CURSED_GATE, RouteEventCategory.RISKY, listOf(
                RouteEventEffect(RouteEventEffectType.POWER_UP_LIFETIME_DELTA, -10),
                RouteEventEffect(RouteEventEffectType.STREAK_PROGRESS_DELTA, 1)
            ))
        )

        internal fun choice(id: String): RouteEventChoice? =
            catalogue.firstOrNull { it.id == id }?.detachedCopy()

        internal fun isKnownChoice(choice: RouteEventChoice): Boolean =
            catalogue.any { it == choice }
    }
}

internal fun RouteEventChoice.detachedCopy(): RouteEventChoice = copy(effects = effects.toList())

internal fun PendingAdventureReward.detachedCopy(): PendingAdventureReward = copy(
    routeChoices = routeChoices.map { it.detachedCopy() },
    powerUpCandidates = powerUpCandidates.toList(),
    preview = preview?.copy(categories = preview.categories.toList())
)

internal fun PendingRouteEvent.detachedCopy(): PendingRouteEvent = copy(effects = effects.toList())
