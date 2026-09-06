package com.example.apktest.game.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureRouteCadenceSnapshotTest {
    @Test
    fun suppressedOfferSlotsPreserveCadenceWithoutInventingChoiceHistory() {
        val c = afterSuppressedEvent()
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, 5L)
        assertTrue(snapshot.routeHistory.isEmpty())
        assertEquals(snapshot, AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
        val invalid = JSONObject(snapshot.toJson()).put("nextRouteEventMazeIndex", 6).toString()
        assertNull(AdventureRunStateSnapshot.fromJson(invalid))
    }

    @Test
    fun laterOfferAfterASuppressedSlotRemainsDurableAndDoesNotGetStuck() {
        val original = afterSuppressedEvent()
        val snapshot = AdventureRunStateSnapshot.fromState(original.state, 5L)
        val c = AdventureRunController(original.config,
            AdventureRunStateSnapshot.fromJson(snapshot.toJson())!!.toState(), 5L, routesEnabled = true)
        finishReward(c)
        while (c.state.currentMazeIndex < 5) {
            c.prepareCurrentMaze()
            c.completeMaze()
            if (c.state.currentMazeIndex < 5) finishReward(c)
        }
        assertTrue(c.state.pendingReward!!.routeChoices.isNotEmpty())
        assertEquals(2, c.state.routeEventOrdinal)
        assertTrue(c.state.nextRouteEventMazeIndex - 5 in 2..3)
        assertNotNull(AdventureRunStateSnapshot.fromJson(AdventureRunStateSnapshot.fromState(c.state, 5L).toJson()))
        finishReward(c)
        assertEquals(5, c.state.routeHistory.single().mazeIndexCompleted)
        val selected = AdventureRunStateSnapshot.fromState(c.state, 5L)
        assertEquals(selected, AdventureRunStateSnapshot.fromJson(selected.toJson()))
        val duplicate = selected.copy(routeHistory = selected.routeHistory + selected.routeHistory)
        assertNull(AdventureRunStateSnapshot.fromJson(duplicate.toJson()))
    }

    @Test
    fun finalTargetRejectsBothOfferedAndCommittedCursedRoutes() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        for (seed in 0L..63L) {
            val c = AdventureRunController(config, runSeed = seed, routesEnabled = true)
            repeat(config.totalMazes - 1) {
                c.prepareCurrentMaze()
                c.completeMaze()
                if (c.state.currentMazeIndex < config.totalMazes - 1) finishReward(c)
            }
            if (c.state.pendingReward!!.routeChoices.isEmpty()) continue
            val snapshot = AdventureRunStateSnapshot.fromState(c.state, seed)
            assertNotNull(AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
            val cursed = RouteEventGenerator.choice(RouteEventGenerator.CURSED_GATE)!!
            val offered = snapshot.copy(pendingReward = snapshot.pendingReward!!.copy(
                routeChoices = listOf(RouteEventGenerator.choice(RouteEventGenerator.QUIET_CORRIDOR)!!, cursed)
            ))
            assertNull(AdventureRunStateSnapshot.fromJson(offered.toJson()))
            c.acknowledgeMazeWin(config.totalMazes - 1)
            assertTrue(c.chooseRoute(config.totalMazes - 1, RouteEventGenerator.SUPPLY_CACHE))
            val selected = AdventureRunStateSnapshot.fromState(c.state, seed)
            val committed = selected.copy(
                activeRoute = RouteEventGenerator(config, seed).resolve(cursed, config.totalMazes),
                routeHistory = selected.routeHistory.dropLast(1) +
                    RouteEventHistoryEntry(config.totalMazes - 1, cursed.id),
                pendingReward = selected.pendingReward!!.copy(
                    routeChoices = offered.pendingReward!!.routeChoices,
                    selectedRouteId = cursed.id
                )
            )
            assertNull(AdventureRunStateSnapshot.fromJson(committed.toJson()))
            return
        }
        throw AssertionError("No final-target route fixture reached")
    }

    private fun afterSuppressedEvent(): AdventureRunController {
        val c = AdventureRunController(AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM),
            runSeed = 5L, routesEnabled = false)
        c.prepareCurrentMaze()
        c.completeMaze()
        finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        c.state.routeEventOrdinal = 1
        c.state.nextRouteEventMazeIndex = 5
        return c
    }

    private fun finishReward(c: AdventureRunController) {
        val index = c.state.currentMazeIndex
        c.acknowledgeMazeWin(index)
        if (c.state.pendingReward!!.stage == RewardStage.ROUTE_CHOICE) {
            assertTrue(c.chooseRoute(index, c.state.pendingReward!!.routeChoices.first().id))
        }
        assertTrue(c.chooseStartingPowerUp(index, c.state.pendingReward!!.powerUpCandidates.first()))
    }
}
