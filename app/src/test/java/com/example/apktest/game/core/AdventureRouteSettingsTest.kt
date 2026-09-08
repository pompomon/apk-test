package com.example.apktest.game.core

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AdventureRouteSettingsTest {
    @Test
    fun defaultOffAndInRunOptInUseExistingOffersOnEveryDifficulty() {
        for (difficulty in DifficultyPresets.all) {
            val config = AdventureConfig.forDifficulty(difficulty)
            val controller = AdventureRunController(config, runSeed = 7L)
            val enabledFromStart = AdventureRunController(config, runSeed = 7L, routesEnabled = true)
            assertFalse(controller.state.routeEventsEnabled)
            for (c in listOf(controller, enabledFromStart)) {
                c.prepareCurrentMaze()
                c.completeMaze()
                assertTrue(c.state.pendingReward!!.routeChoices.isEmpty())
                AdventureRouteEventsTest.finishReward(c)
            }
            val before = controller.prepareCurrentMaze()
            assertTrue(controller.setRouteEventsEnabled(true))
            assertEquals(before, controller.prepareCurrentMaze())
            enabledFromStart.prepareCurrentMaze()
            controller.completeMaze()
            enabledFromStart.completeMaze()
            assertTrue(controller.state.pendingReward!!.routeChoices.isNotEmpty())
            assertEquals(enabledFromStart.state.pendingReward, controller.state.pendingReward)
            assertEquals(enabledFromStart.state.nextRouteEventMazeIndex, controller.state.nextRouteEventMazeIndex)
        }
    }

    @Test
    fun enablingAfterSkippedCheckpointWaitsForOriginalScheduleAcrossResume() {
        var controller = AdventureRunController(
            AdventureConfig.forDifficulty(DifficultyPresets.HARD), runSeed = 5L
        )
        repeat(2) {
            controller.prepareCurrentMaze()
            controller.completeMaze()
            AdventureRouteEventsTest.finishReward(controller)
        }
        val next = controller.state.nextRouteEventMazeIndex
        val ordinal = controller.state.routeEventOrdinal
        assertTrue(next in 4..5)
        assertTrue(controller.setRouteEventsEnabled(true))
        controller = restore(controller, 5L)
        assertTrue(controller.state.routeEventsEnabled)
        assertNull(controller.state.pendingReward)
        assertEquals(next, controller.state.nextRouteEventMazeIndex)
        assertEquals(ordinal, controller.state.routeEventOrdinal)
        while (controller.state.currentMazeIndex < next) {
            controller.prepareCurrentMaze()
            controller.completeMaze()
            assertEquals(controller.state.currentMazeIndex == next,
                controller.state.pendingReward!!.routeChoices.isNotEmpty())
            AdventureRouteEventsTest.finishReward(controller)
        }
    }

    @Test
    fun disablingPreservesPendingChoiceLockedEffectsAndWinPayoffButStopsFutureOffers() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        var controller = fixture.controller
        val pending = controller.state.pendingReward
        val next = controller.state.nextRouteEventMazeIndex
        assertTrue(controller.setRouteEventsEnabled(false))
        assertEquals(pending, controller.state.pendingReward)
        assertTrue(controller.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT))
        AdventureRouteEventsTest.finishReward(controller)
        val startup = controller.prepareCurrentMaze()
        val route = controller.state.activeRoute
        controller = restore(controller, fixture.seed)
        assertFalse(controller.state.routeEventsEnabled)
        assertEquals(route, controller.state.activeRoute)
        assertEquals(startup, controller.prepareCurrentMaze())
        controller.onPlayerDied()
        assertEquals(route, controller.state.activeRoute)
        assertEquals(startup, controller.prepareCurrentMaze())
        assertEquals(0, controller.state.rewardRerolls)
        controller.completeMaze()
        assertEquals(1, controller.state.rewardRerolls)
        AdventureRouteEventsTest.finishReward(controller)
        while (controller.state.currentMazeIndex < next) {
            controller.prepareCurrentMaze()
            controller.completeMaze()
            assertTrue(controller.state.pendingReward!!.routeChoices.isEmpty())
            AdventureRouteEventsTest.finishReward(controller)
        }
        assertTrue(controller.state.nextRouteEventMazeIndex - next in 2..3)
        assertEquals(1, controller.state.routeHistory.size)
    }

    @Test
    fun togglingDoesNotChangePendingRewardOrCompletedRun() {
        val controller = AdventureRunController(
            AdventureConfig.forDifficulty(DifficultyPresets.EASY), runSeed = 7L
        )
        controller.prepareCurrentMaze()
        controller.completeMaze()
        val pending = controller.state.pendingReward
        assertTrue(controller.setRouteEventsEnabled(true))
        assertEquals(pending, controller.state.pendingReward)
        AdventureRouteEventsTest.finishReward(controller)
        while (controller.state.status == AdventureStatus.IN_PROGRESS) {
            controller.prepareCurrentMaze()
            if (!controller.completeMaze().runComplete) AdventureRouteEventsTest.finishReward(controller)
        }
        val before = AdventureRunStateSnapshot.fromState(controller.state, 7L)
        assertFalse(controller.setRouteEventsEnabled(false))
        assertEquals(before, AdventureRunStateSnapshot.fromState(controller.state, 7L))
    }

    @Test
    fun bothSettingsRoundTripWithoutAConstructorOverride() {
        for (enabled in listOf(false, true)) {
            val controller = AdventureRunController(
                AdventureConfig.forDifficulty(DifficultyPresets.EASY), runSeed = 7L
            )
            controller.prepareCurrentMaze()
            controller.setRouteEventsEnabled(enabled)
            val snapshot = AdventureRunStateSnapshot.fromState(controller.state, 7L)
            val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson())!!
            assertEquals(snapshot, restored)
            assertEquals(enabled, restored.toState().routeEventsEnabled)
            assertEquals(enabled, restore(controller, 7L).state.routeEventsEnabled)
        }
    }

    @Test
    fun schemaFiveMigratesWithSettingOffAndPreservesPendingAndActiveRoutes() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val controller = fixture.controller
        val offered = AdventureRunStateSnapshot.fromState(controller.state, fixture.seed)
        controller.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT)
        AdventureRouteEventsTest.finishReward(controller)
        controller.prepareCurrentMaze()
        val active = AdventureRunStateSnapshot.fromState(controller.state, fixture.seed)
        for (snapshot in listOf(offered, active)) {
            val legacy = JSONObject(snapshot.toJson()).apply {
                put("v", 5)
                remove("routeEventsEnabled")
            }
            val migrated = AdventureRunStateSnapshot.fromJson(legacy.toString())
            assertEquals(snapshot.copy(routeEventsEnabled = false), migrated)
            assertEquals(migrated, AdventureRunStateSnapshot.fromJson(migrated!!.toJson()))
            assertFalse(AdventureRunController(controller.config, migrated.toState(), fixture.seed)
                .state.routeEventsEnabled)
        }
    }

    @Test
    fun currentSchemaRejectsMissingOrNonBooleanSettingAndMigrationStillValidatesState() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val json = AdventureRunStateSnapshot.fromState(fixture.controller.state, fixture.seed).toJson()
        for (value in listOf("true", 1, JSONObject.NULL)) {
            assertNull(AdventureRunStateSnapshot.fromJson(
                JSONObject(json).put("routeEventsEnabled", value).toString()))
        }
        assertNull(AdventureRunStateSnapshot.fromJson(
            JSONObject(json).apply { remove("routeEventsEnabled") }.toString()))
        assertNull(AdventureRunStateSnapshot.fromJson(JSONObject(json).apply {
            put("v", 5)
            remove("routeEventsEnabled")
            put("rewardRerolls", 2)
        }.toString()))
    }

    private fun restore(controller: AdventureRunController, seed: Long): AdventureRunController {
        val snapshot = AdventureRunStateSnapshot.fromState(controller.state, seed)
        val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson(), controller.config)!!
        return AdventureRunController(controller.config, restored.toState(), seed)
    }
}
