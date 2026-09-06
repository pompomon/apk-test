package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureEliteAssignmentTest {
    @Test
    fun targetCapsRespectDifficultyProgressAndHardRosterHalf() {
        val cases = listOf(
            DifficultyPresets.EASY to listOf(0, 0, 1, 1, 1),
            DifficultyPresets.MEDIUM to listOf(1, 1, 1, 1, 1, 1, 2),
            DifficultyPresets.HARD to listOf(1, 1, 1, 1, 2, 2, 2, 2, 2)
        )
        for ((difficulty, caps) in cases) {
            val config = AdventureConfig.forDifficulty(difficulty)
            caps.forEachIndexed { index, cap ->
                for (actualCount in 0..8) {
                    val rosterLimit = if (difficulty == DifficultyPresets.HARD) actualCount / 2 else actualCount
                    assertEquals("${difficulty.name} maze ${index + 1}, count $actualCount",
                        minOf(cap, rosterLimit),
                        AdventureEliteAssignment.targetCap(config, index + 1, actualCount))
                }
            }
        }
    }

    @Test
    fun oneNpcCanBeTrackerOnMediumOrEligibleEasyButNotHard() {
        val position = GridPos(5, 5)
        val plan = NpcSpawnPlanner.Plan(listOf(position), setOf(position))
        for (difficulty in DifficultyPresets.all) {
            val config = AdventureConfig.forDifficulty(difficulty)
            val targetMaze = if (difficulty == DifficultyPresets.EASY) 3 else 1
            val assigned = AdventureEliteAssignment.assign(config, targetMaze, 123L,
                listOf(NpcPolicyType.PATROL_GUARD), plan)
            assertEquals(if (difficulty == DifficultyPresets.HARD) null else EliteNpcModifier.TRACKER,
                assigned.single().eliteModifier)
        }
    }

    @Test
    fun rolloutIsAtMostOneAndDoesNotStackWithCountRampsRiskyRoutesOrFinales() {
        val expected = listOf(
            DifficultyPresets.EASY to listOf(0, 0, 1, 0, 0),
            DifficultyPresets.MEDIUM to listOf(1, 1, 1, 0, 1, 1, 0),
            DifficultyPresets.HARD to listOf(1, 1, 1, 0, 1, 1, 0, 1, 0)
        )
        for ((difficulty, budgets) in expected) {
            val config = AdventureConfig.forDifficulty(difficulty)
            budgets.forEachIndexed { index, budget ->
                assertEquals(budget, AdventureEliteAssignment.rolloutBudget(config, index + 1, 8))
                for (route in listOf(RouteEventGenerator.AMBUSH_SHORTCUT, RouteEventGenerator.CURSED_GATE)) {
                    assertEquals(0, AdventureEliteAssignment.rolloutBudget(config, index + 1, 8, route))
                }
                for (route in listOf(RouteEventGenerator.SUPPLY_CACHE, RouteEventGenerator.SCOUT_MAP,
                    RouteEventGenerator.QUIET_CORRIDOR)) {
                    assertEquals(budget, AdventureEliteAssignment.rolloutBudget(config, index + 1, 8, route))
                }
            }
        }
    }

    @Test
    fun assignmentOnlyDecoratesActuallySpawnedEligiblePatrolIdsWithoutReordering() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        val policies = listOf(NpcPolicyType.DIRECT_CHASE, NpcPolicyType.PATROL_GUARD,
            NpcPolicyType.PATROL_GUARD, NpcPolicyType.PREDICTIVE_CHASE, NpcPolicyType.PATROL_GUARD)
        val cells = List(3) { GridPos(it, 5) }
        val plan = NpcSpawnPlanner.Plan(cells, setOf(cells[0], cells[2]))
        val assigned = AdventureEliteAssignment.assign(config, 2, 123L, policies, plan)

        assertEquals(policies, assigned.map { it.policyType })
        assertEquals(listOf(null, null, EliteNpcModifier.TRACKER, null, null),
            assigned.map { it.eliteModifier })
        assertEquals(assigned, AdventureEliteAssignment.assign(config, 2, 123L, policies, plan))
    }

    @Test
    fun capacityNotRequestedCountControlsBudgetAndUnspawnedSlotsStayPlain() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        val policies = List(8) { NpcPolicyType.PATROL_GUARD }
        val cells = listOf(GridPos(4, 4), GridPos(5, 4))
        for (capacity in 0..2) {
            val available = cells.take(capacity)
            val plan = NpcSpawnPlanner.Plan(available, available.toSet())
            val assigned = AdventureEliteAssignment.assign(config, 5, 19L, policies, plan)
            assertEquals(capacity / 2, assigned.count { it.eliteModifier != null })
            assertTrue(assigned.drop(capacity).all { it.eliteModifier == null })
        }
    }

    @Test
    fun noSafePositionsOrNoPatrolNeverRerollsBasePoliciesToFillBudget() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        val cells = List(8) { GridPos(it, 5) }
        for (policy in NpcPolicyType.entries) {
            val policies = List(cells.size) { policy }
            val plan = NpcSpawnPlanner.Plan(cells, if (policy == NpcPolicyType.PATROL_GUARD) {
                emptySet()
            } else cells.toSet())
            val assigned = AdventureEliteAssignment.assign(config, 2, 19L, policies, plan)
            assertEquals(policies.map { NpcSpawnSpec(it) }, assigned)
        }
    }
}
