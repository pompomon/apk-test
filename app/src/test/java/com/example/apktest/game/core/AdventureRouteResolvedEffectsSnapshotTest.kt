package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureRouteResolvedEffectsSnapshotTest {
    @Test
    fun quietCannotPersistZeroHardNpcsEvenWithAMatchingEmptyLockedRoster() {
        val (c, seed) = atOffer(AdventureConfig.forDifficulty(DifficultyPresets.HARD), RouteEventGenerator.QUIET_CORRIDOR)
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.QUIET_CORRIDOR)
        val snapshot = validSnapshot(c, seed)
        assertEquals(1, snapshot.currentMazeNpcCount)
        val impossible = snapshot.copy(
            currentMazeNpcCount = 0,
            currentMazeNpcPolicies = emptyList(),
            activeRoute = snapshot.activeRoute!!.copy(npcCount = 0)
        )
        assertNull(AdventureRunStateSnapshot.fromJson(impossible.toJson()))
    }

    @Test
    fun ambushCountMustBeExactlyBaselinePlusOneRatherThanAnyPositiveCount() {
        val (c, seed) = atOffer(AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM), RouteEventGenerator.AMBUSH_SHORTCUT)
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.AMBUSH_SHORTCUT)
        val snapshot = validSnapshot(c, seed)
        for (count in listOf(1, 3, 100)) {
            val impossible = snapshot.copy(
                currentMazeNpcCount = count,
                currentMazeNpcPolicies = List(count) { NpcPolicyType.DIRECT_CHASE },
                activeRoute = snapshot.activeRoute!!.copy(npcCount = count)
            )
            assertNull(AdventureRunStateSnapshot.fromJson(impossible.toJson()))
        }
    }

    @Test
    fun cursedLifetimeMustMatchItsAdvertisedResolvedPenaltyExactly() {
        val (c, seed) = atOffer(AdventureConfig.forDifficulty(DifficultyPresets.HARD), RouteEventGenerator.CURSED_GATE)
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.CURSED_GATE)
        val snapshot = validSnapshot(c, seed)
        assertEquals(30f, snapshot.activeRoute!!.pickupLifetimeSeconds!!, 0f)
        for (lifetime in listOf(10f, 29f, 31f, 10_000f)) {
            val impossible = snapshot.copy(activeRoute = snapshot.activeRoute.copy(pickupLifetimeSeconds = lifetime))
            assertNull(AdventureRunStateSnapshot.fromJson(impossible.toJson()))
        }
    }

    @Test
    fun noReliefQuietAndInfiniteOrNoopCursedOffersAreRejectedBeforeSelection() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        val (c, seed) = atOffer(config, RouteEventGenerator.AMBUSH_SHORTCUT)
        val snapshot = validSnapshot(c, seed)
        assertEquals(2, snapshot.currentMazeIndex)
        val quiet = snapshot.copy(pendingReward = snapshot.pendingReward!!.copy(routeChoices = listOf(
            RouteEventGenerator.choice(RouteEventGenerator.QUIET_CORRIDOR)!!,
            RouteEventGenerator.choice(RouteEventGenerator.SUPPLY_CACHE)!!
        )))
        assertNull(AdventureRunStateSnapshot.fromJson(quiet.toJson()))
        val cursed = snapshot.copy(pendingReward = snapshot.pendingReward.copy(routeChoices = listOf(
            RouteEventGenerator.choice(RouteEventGenerator.CURSED_GATE)!!,
            RouteEventGenerator.choice(RouteEventGenerator.SUPPLY_CACHE)!!
        )))
        for (lifetime in listOf(0f, 5f, 10f, Float.POSITIVE_INFINITY)) {
            val custom = config.copy(difficulty = config.difficulty.copy(powerUpPickupLifetimeSeconds = lifetime))
            assertNull(AdventureRunStateSnapshot.fromJson(cursed.toJson(), custom))
        }
    }

    @Test
    fun customZeroNpcsAndLifetimeClampRoundTripOnlyWithTheirActualConfiguration() {
        val config = AdventureConfig(DifficultyPresets.MEDIUM, 3, 7, 0)
        val (c, seed) = atOffer(config, RouteEventGenerator.QUIET_CORRIDOR, config.totalMazes - 1)
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.QUIET_CORRIDOR)
        val snapshot = validSnapshot(c, seed)
        assertEquals(0, snapshot.currentMazeNpcCount)
        assertNull(AdventureRunStateSnapshot.fromJson(snapshot.toJson()))

        val finite = config.copy(difficulty = config.difficulty.copy(powerUpPickupLifetimeSeconds = 15f))
        val (cursed, cursedSeed) = atOffer(finite, RouteEventGenerator.CURSED_GATE)
        cursed.chooseRoute(cursed.state.currentMazeIndex, RouteEventGenerator.CURSED_GATE)
        val clamped = validSnapshot(cursed, cursedSeed)
        assertEquals(10f, clamped.activeRoute!!.pickupLifetimeSeconds!!, 0f)
        assertNull(AdventureRunStateSnapshot.fromJson(clamped.toJson()))
    }

    private fun validSnapshot(c: AdventureRunController, seed: Long): AdventureRunStateSnapshot {
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, seed)
        assertEquals(snapshot, AdventureRunStateSnapshot.fromJson(snapshot.toJson(), c.config))
        return snapshot
    }

    private fun atOffer(
        config: AdventureConfig,
        choiceId: String,
        lastMazeIndex: Int = 2
    ): Pair<AdventureRunController, Long> {
        for (seed in 0L..63L) {
            val c = AdventureRunController(config, runSeed = seed, routesEnabled = true)
            while (c.state.currentMazeIndex < minOf(config.totalMazes - 1, lastMazeIndex)) {
                assertNotNull(c.prepareCurrentMaze())
                c.completeMaze()
                c.acknowledgeMazeWin(c.state.currentMazeIndex)
                val reward = c.state.pendingReward!!
                if (reward.routeChoices.any { it.id == choiceId }) return c to seed
                if (reward.stage == RewardStage.ROUTE_CHOICE) {
                    c.chooseRoute(c.state.currentMazeIndex, reward.routeChoices.first().id)
                }
                assertTrue(c.chooseStartingPowerUp(c.state.currentMazeIndex, c.state.pendingReward!!.powerUpCandidates.first()))
            }
        }
        throw AssertionError("No fixture for $choiceId")
    }
}
