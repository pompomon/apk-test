package com.example.apktest.game.core

import kotlin.random.Random

/** Assigns modifiers without consuming either the base-policy or spawn-position RNG. */
internal object AdventureEliteAssignment {
    private const val ELITE_ASSIGNMENT_SEED_MIX = 0x51C8E49A73D206BL
    private const val ROLLOUT_MAX_ELITES = 1

    fun targetCap(config: AdventureConfig, mazeIndex1Based: Int, actualNpcCount: Int): Int {
        require(mazeIndex1Based in 1..config.totalMazes)
        require(actualNpcCount >= 0)
        val cap = when (config.difficulty.name) {
            DifficultyPresets.EASY.name -> if (mazeIndex1Based < 3) 0 else 1
            DifficultyPresets.HARD.name -> if (mazeIndex1Based < 5) 1 else 2
            else -> if (mazeIndex1Based == config.totalMazes) 2 else 1
        }
        val rosterLimit = if (config.difficulty.name == DifficultyPresets.HARD.name) {
            actualNpcCount / 2
        } else actualNpcCount
        return minOf(cap, rosterLimit)
    }

    fun rolloutBudget(
        config: AdventureConfig,
        mazeIndex1Based: Int,
        actualNpcCount: Int,
        routeChoiceId: String? = null
    ): Int {
        val cap = targetCap(config, mazeIndex1Based, actualNpcCount)
        // Count ramps, risky routes and the finale need separate balance approval.
        if (mazeIndex1Based == config.totalMazes ||
            (mazeIndex1Based > 1 && (mazeIndex1Based - 1) % 3 == 0) ||
            routeChoiceId == RouteEventGenerator.AMBUSH_SHORTCUT ||
            routeChoiceId == RouteEventGenerator.CURSED_GATE) return 0
        return minOf(ROLLOUT_MAX_ELITES, cap)
    }

    fun assign(
        config: AdventureConfig,
        mazeIndex1Based: Int,
        mazeSeed: Long,
        policies: List<NpcPolicyType>,
        plan: NpcSpawnPlanner.Plan,
        routeChoiceId: String? = null
    ): List<NpcSpawnSpec> {
        val actualCount = minOf(policies.size, plan.candidates.size)
        val budget = rolloutBudget(config, mazeIndex1Based, actualCount, routeChoiceId)
        val eligibleIds = (0 until actualCount).filter {
            EliteNpcModifier.TRACKER.supports(policies[it]) &&
                plan.candidates[it] in plan.eliteEligiblePositions
        }
        val selectedIds = eligibleIds.shuffled(Random(mazeSeed xor ELITE_ASSIGNMENT_SEED_MIX))
            .take(budget).toSet()
        return policies.mapIndexed { id, policy ->
            NpcSpawnSpec(policy, if (id in selectedIds) EliteNpcModifier.TRACKER else null)
        }
    }
}
