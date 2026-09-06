package com.example.apktest.game.core

import org.junit.Assert.*
import org.junit.Test

class AdventureRouteEventsTest {
    @Test
    fun durableWinIsIdempotentAndEveryPhaseSurvivesResume() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        var controller = AdventureRunController(config, runSeed = 7L, routesEnabled = true)
        controller.prepareCurrentMaze()
        val win = controller.completeMaze(15f, 20)
        assertEquals(win, controller.completeMaze(100f, 100))
        assertEquals(RewardStage.WIN_ACKNOWLEDGEMENT, controller.state.pendingReward!!.stage)
        assertNull(controller.prepareCurrentMaze())
        controller = restore(controller, 7L)
        assertEquals(win, controller.completeMaze(100f, 100))
        assertFalse(controller.acknowledgeMazeWin(2))
        assertTrue(controller.acknowledgeMazeWin(1))
        assertFalse(controller.acknowledgeMazeWin(1))
        assertEquals(RewardStage.POWER_UP_CHOICE, controller.state.pendingReward!!.stage)
        controller = restore(controller, 7L)
        assertTrue(controller.chooseStartingPowerUp(1, controller.state.pendingReward!!.powerUpCandidates.first()))
        assertFalse(controller.chooseStartingPowerUp(1, PowerUpType.SHIELD))
        assertNotNull(controller.prepareCurrentMaze())
        assertEquals(15f, controller.state.totalElapsedSeconds, 0f)
        assertEquals(20, controller.state.totalSteps)
    }

    @Test
    fun invalidActionsAndLateEngineCallbacksCannotSkipOrMutateRewards() {
        val fixture = atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val controller = fixture.controller
        val before = AdventureRunStateSnapshot.fromState(controller.state, fixture.seed)
        val index = controller.state.currentMazeIndex
        assertFalse(controller.chooseRoute(index - 1, RouteEventGenerator.AMBUSH_SHORTCUT))
        assertFalse(controller.chooseRoute(index, "unknown_route"))
        assertFalse(controller.chooseStartingPowerUp(index, PowerUpType.SPEED_UP))
        assertFalse(controller.rerollStartingPowerUps(index))
        controller.onPlayerDied()
        controller.recordMidMazeSnapshot(GameEngine(DifficultyPresets.HARD, 1L).snapshot())
        assertEquals(before, AdventureRunStateSnapshot.fromState(controller.state, fixture.seed))
        assertTrue(controller.chooseRoute(index, RouteEventGenerator.AMBUSH_SHORTCUT))
        assertFalse(controller.chooseRoute(index, RouteEventGenerator.AMBUSH_SHORTCUT))
        val after = AdventureRunStateSnapshot.fromState(controller.state, fixture.seed)
        assertFalse(controller.chooseStartingPowerUp(index, PowerUpType.GHOST_MODE))
        assertFalse(controller.chooseStartingPowerUp(index - 1, controller.state.pendingReward!!.powerUpCandidates.first()))
        assertEquals(after, AdventureRunStateSnapshot.fromState(controller.state, fixture.seed))
    }

    @Test
    fun allFiveChoicesPersistExactOffersAndRetryEffectsEvenWhenFlagDisabled() {
        for (id in ROUTE_IDS) {
            val fixture = atRoute(id)
            var controller = fixture.controller
            val offered = controller.state.pendingReward
            controller = restore(controller, fixture.seed, routesEnabled = false)
            assertEquals(offered, controller.state.pendingReward)
            val index = controller.state.currentMazeIndex
            assertTrue(id, controller.chooseRoute(index, id))
            val afterChoice = controller.state.pendingReward
            controller = restore(controller, fixture.seed, routesEnabled = false)
            assertEquals(afterChoice, controller.state.pendingReward)
            assertNull(controller.prepareCurrentMaze())
            val powerUp = controller.state.pendingReward!!.powerUpCandidates.first()
            assertTrue(controller.chooseStartingPowerUp(index, powerUp))
            val startup = controller.prepareCurrentMaze()!!
            controller = restore(controller, fixture.seed, routesEnabled = false)
            assertEquals(startup, controller.prepareCurrentMaze())
            val lockedRoute = controller.state.activeRoute
            controller.onPlayerDied()
            assertEquals(lockedRoute, controller.state.activeRoute)
            assertEquals(startup, controller.prepareCurrentMaze())
            assertEquals(startup.npcCount, startup.npcPolicies.size)
            assertEquals(powerUp, startup.startingPowerUp)
        }
    }

    @Test
    fun quietReducesOnlyCurrentRewardAndNextMazeCount() {
        val fixture = atRoute(RouteEventGenerator.QUIET_CORRIDOR)
        val c = fixture.controller
        val index = c.state.currentMazeIndex
        val ordinary = c.state.pendingReward!!.powerUpCandidates
        assertTrue(c.chooseRoute(index, RouteEventGenerator.QUIET_CORRIDOR))
        assertEquals(ordinary.take(2), c.state.pendingReward!!.powerUpCandidates)
        assertEquals(-1, c.state.activeRoute!!.rewardOptionDelta)
        finishReward(c)
        assertEquals(c.config.npcCountForMaze(index + 1) - 1, c.prepareCurrentMaze()!!.npcCount)
        c.completeMaze()
        assertNull(c.state.activeRoute)
        assertEquals(3, c.state.pendingReward!!.powerUpCandidates.size)
    }

    @Test
    fun supplyUsesExactlyOneOrdinaryRewardChooser() {
        val fixture = atRoute(RouteEventGenerator.SUPPLY_CACHE)
        val c = fixture.controller
        val index = c.state.currentMazeIndex
        val ordinary = c.state.pendingReward!!.powerUpCandidates
        assertTrue(c.chooseRoute(index, RouteEventGenerator.SUPPLY_CACHE))
        assertEquals(ordinary, c.state.pendingReward!!.powerUpCandidates)
        assertEquals(RewardStage.POWER_UP_CHOICE, c.state.pendingReward!!.stage)
        finishReward(c)
        assertNull(c.state.pendingReward)
        assertEquals(c.config.npcCountForMaze(index + 1), c.prepareCurrentMaze()!!.npcCount)
    }

    @Test
    fun ambushPaysOnceOnTargetWinAndBankedRerollRoundTripsExactly() {
        val fixture = atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val c = fixture.controller
        assertTrue(c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.AMBUSH_SHORTCUT))
        finishReward(c)
        val spec = c.prepareCurrentMaze()!!
        assertEquals(c.config.npcCountForMaze(c.state.currentMazeIndex + 1) + 1, spec.npcCount)
        c.onPlayerDied()
        assertEquals(0, c.state.rewardRerolls)
        c.prepareCurrentMaze()
        c.completeMaze(20f, 30)
        assertEquals(1, c.state.rewardRerolls)
        c.completeMaze(20f, 30)
        assertEquals(1, c.state.rewardRerolls)
        val index = c.state.currentMazeIndex
        assertTrue(c.acknowledgeMazeWin(index))
        val restored = restore(c, fixture.seed)
        assertTrue(c.rerollStartingPowerUps(index))
        assertTrue(restored.rerollStartingPowerUps(index))
        assertEquals(c.state.pendingReward, restored.state.pendingReward)
        assertEquals(0, c.state.rewardRerolls)
        assertEquals(1, c.state.pendingReward!!.rerollIndex)
        val afterReroll = AdventureRunStateSnapshot.fromState(c.state, fixture.seed)
        assertEquals(afterReroll, AdventureRunStateSnapshot.fromJson(afterReroll.toJson()))
        assertFalse(c.rerollStartingPowerUps(index))
        assertEquals(afterReroll, AdventureRunStateSnapshot.fromState(c.state, fixture.seed))
    }

    @Test
    fun ambushCreditIsCappedAndCanBeSpentAtALaterReward() {
        val fixture = atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val c = fixture.controller
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.AMBUSH_SHORTCUT)
        finishReward(c)
        c.state.rewardRerolls = 1
        c.prepareCurrentMaze()
        c.completeMaze()
        assertEquals(1, c.state.rewardRerolls)
        finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(c.state.currentMazeIndex)
        if (c.state.pendingReward!!.stage == RewardStage.ROUTE_CHOICE) {
            c.chooseRoute(c.state.currentMazeIndex, c.state.pendingReward!!.routeChoices.first().id)
        }
        assertTrue(c.rerollStartingPowerUps(c.state.currentMazeIndex))
        assertEquals(0, c.state.rewardRerolls)
    }

    @Test
    fun cursedProgressIsAppliedBeforeThresholdAndResetsToZero() {
        val fixture = atRoute(RouteEventGenerator.CURSED_GATE)
        val c = fixture.controller
        assertEquals(2, c.state.winStreakSinceLastBonus)
        c.chooseRoute(2, RouteEventGenerator.CURSED_GATE)
        finishReward(c)
        assertEquals(30f, c.prepareCurrentMaze()!!.pickupLifetimeSeconds!!, 0f)
        val lives = c.state.livesRemaining
        val outcome = c.completeMaze()
        assertTrue(outcome.bonusLifeAwarded)
        assertEquals(lives + 1, c.state.livesRemaining)
        assertEquals(0, c.state.winStreakSinceLastBonus)
        assertEquals(outcome, c.completeMaze())
        finishReward(c)
        assertNull(c.prepareCurrentMaze()!!.pickupLifetimeSeconds)
    }

    @Test
    fun cursedRewardSurvivesRetryButDoesNotPayOnDeath() {
        val c = atRoute(RouteEventGenerator.CURSED_GATE).controller
        c.chooseRoute(2, RouteEventGenerator.CURSED_GATE)
        finishReward(c)
        val start = c.prepareCurrentMaze()
        c.onPlayerDied()
        assertEquals(0, c.state.winStreakSinceLastBonus)
        assertEquals(start, c.prepareCurrentMaze())
        assertFalse(c.completeMaze().bonusLifeAwarded)
        assertEquals(2, c.state.winStreakSinceLastBonus)
    }

    @Test
    fun scoutPreviewsExactFutureOfferRegardlessOfChoicesOrBalance() {
        val fixture = atRoute(RouteEventGenerator.SCOUT_MAP)
        var c = fixture.controller
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.SCOUT_MAP)
        val preview = c.state.pendingReward!!.preview!!
        assertEquals(c.state.currentMazeNpcCount, preview.npcCount)
        c = restore(c, fixture.seed)
        assertEquals(preview, c.state.pendingReward!!.preview)
        finishReward(c)
        assertNull(c.state.pendingReward)
        while (c.state.currentMazeIndex < preview.nextEventMazeIndex) {
            c.prepareCurrentMaze()
            c.completeMaze()
            if (c.state.currentMazeIndex < preview.nextEventMazeIndex) finishReward(c)
        }
        assertEquals(preview.categories, c.state.pendingReward!!.routeChoices.map { it.category })
        assertEquals(preview.nextEventMazeIndex, c.state.pendingReward!!.mazeIndexCompleted)
    }

    @Test
    fun cadencePersistsAndDeathsAndFinalWinsNeverOfferRoutes() {
        val c = AdventureRunController(AdventureConfig.forDifficulty(DifficultyPresets.EASY),
            runSeed = 11L, routesEnabled = true)
        assertEquals(2, c.state.nextRouteEventMazeIndex)
        while (c.state.status == AdventureStatus.IN_PROGRESS) {
            c.prepareCurrentMaze()
            val before = c.state.nextRouteEventMazeIndex
            if (c.state.currentMazeIndex == 0) {
                c.onPlayerDied()
                assertNull(c.state.pendingReward)
                assertEquals(before, c.state.nextRouteEventMazeIndex)
                c.prepareCurrentMaze()
            }
            val win = c.completeMaze()
            if (win.runComplete) {
                assertNull(c.state.pendingReward)
                assertNull(c.state.activeRoute)
                assertTrue(c.state.routeHistory.isEmpty())
                assertEquals(0, c.state.rewardRerolls)
            } else {
                val offered = c.state.pendingReward!!.routeChoices.isNotEmpty()
                assertEquals(win.mazeIndexCompleted == before, offered)
                if (offered) assertTrue(c.state.nextRouteEventMazeIndex - before in 2..3)
                assertEquals(c.state.nextRouteEventMazeIndex,
                    restore(c, 11L).state.nextRouteEventMazeIndex)
                finishReward(c)
            }
        }
    }

    @Test
    fun disabledDurableFlowPreservesBaselineMazeRewardsAndUnlocks() {
        DifficultyPresets.all.forEach { preset ->
            val config = AdventureConfig.forDifficulty(preset)
            val legacy = AdventureRunController(config, runSeed = 101L, routesEnabled = false)
            val durable = AdventureRunController(config, runSeed = 101L, routesEnabled = false)
            repeat(config.totalMazes) {
                assertEquals(legacy.prepareCurrentMaze(), durable.prepareCurrentMaze())
                val win = legacy.onMazeWon(1f, 10)
                assertEquals(win, durable.completeMaze(1f, 10))
                assertEquals(legacy.state.unlockedPlayerPolicies, durable.state.unlockedPlayerPolicies)
                if (!win.runComplete) {
                    assertTrue(durable.state.pendingReward!!.routeChoices.isEmpty())
                    legacy.applyStartingPowerUp(win.startingPowerUpCandidates.first())
                    finishReward(durable)
                }
            }
        }
    }

    @Test
    fun zeroNpcLockIsNotMistakenForAnUnpreparedMaze() {
        val config = AdventureConfig(DifficultyPresets.MEDIUM, 3, 7, 0)
        val c = AdventureRunController(config, runSeed = 5L, routesEnabled = true)
        val first = c.prepareCurrentMaze()!!
        assertEquals(0, first.npcCount)
        assertTrue(first.npcPolicies.isEmpty())
        assertEquals(first, c.prepareCurrentMaze())
        assertEquals(first, restore(c, 5L).prepareCurrentMaze())
        c.onPlayerDied()
        assertEquals(first, c.prepareCurrentMaze())
    }

    @Test
    fun quietCanLockZeroNpcsInCustomRunsAndResumeTheEmptyPolicyList() {
        val config = AdventureConfig(DifficultyPresets.MEDIUM, 3, 7, 0)
        for (seed in 0L..63L) {
            val c = AdventureRunController(config, runSeed = seed, routesEnabled = true)
            while (c.state.currentMazeIndex < 5) {
                c.prepareCurrentMaze()
                c.completeMaze()
                c.acknowledgeMazeWin(c.state.currentMazeIndex)
                if (c.state.pendingReward!!.routeChoices.any { it.id == RouteEventGenerator.QUIET_CORRIDOR }) {
                    c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.QUIET_CORRIDOR)
                    assertEquals(0, c.state.currentMazeNpcCount)
                    assertTrue(c.state.currentMazeNpcPolicies.isEmpty())
                    assertEquals(0, restore(c, seed).state.currentMazeNpcCount)
                    finishReward(c)
                    val startup = c.prepareCurrentMaze()!!
                    c.onPlayerDied()
                    assertEquals(startup, restore(c, seed).prepareCurrentMaze())
                    return
                }
                finishReward(c)
            }
        }
        fail("No Quiet Corridor fixture reached")
    }

    @Test
    fun terminalLossClearsAllRouteState() {
        val c = atRoute(RouteEventGenerator.AMBUSH_SHORTCUT).controller
        c.chooseRoute(c.state.currentMazeIndex, RouteEventGenerator.AMBUSH_SHORTCUT)
        finishReward(c)
        c.prepareCurrentMaze()
        c.state.livesRemaining = 1
        assertTrue(c.onPlayerDied().runOver)
        assertNull(c.state.activeRoute)
        assertNull(c.state.pendingReward)
        assertNull(c.state.currentMazeNpcCount)
        assertNull(c.state.pendingStartingPowerUp)
        assertTrue(c.state.routeHistory.isEmpty())
        assertEquals(0, c.state.rewardRerolls)
    }

    internal data class Fixture(val controller: AdventureRunController, val seed: Long)

    companion object {
        private val ROUTE_IDS = listOf(RouteEventGenerator.QUIET_CORRIDOR, RouteEventGenerator.AMBUSH_SHORTCUT,
            RouteEventGenerator.SUPPLY_CACHE, RouteEventGenerator.SCOUT_MAP, RouteEventGenerator.CURSED_GATE)

        internal fun atRoute(id: String): Fixture {
            val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(initialLives = 5)
            for (seed in 0L..63L) {
                val c = AdventureRunController(config, runSeed = seed, routesEnabled = true)
                c.prepareCurrentMaze()
                c.completeMaze()
                finishReward(c)
                c.prepareCurrentMaze()
                c.completeMaze()
                c.acknowledgeMazeWin(2)
                if (c.state.pendingReward!!.routeChoices.any { it.id == id }) return Fixture(c, seed)
            }
            error("Missing route fixture: $id")
        }

        internal fun finishReward(c: AdventureRunController) {
            val index = c.state.currentMazeIndex
            c.acknowledgeMazeWin(index)
            if (c.state.pendingReward!!.stage == RewardStage.ROUTE_CHOICE) {
                c.chooseRoute(index, c.state.pendingReward!!.routeChoices.first {
                    it.category != RouteEventCategory.RISKY
                }.id)
            }
            assertTrue(c.chooseStartingPowerUp(index, c.state.pendingReward!!.powerUpCandidates.first()))
        }

        internal fun restore(c: AdventureRunController, seed: Long, routesEnabled: Boolean = true): AdventureRunController {
            val snapshot = AdventureRunStateSnapshot.fromState(c.state, seed)
            val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson())
            assertNotNull(snapshot.toJson(), restored)
            assertEquals(snapshot, restored)
            return AdventureRunController(c.config, restored!!.toState(), seed, routesEnabled)
        }
    }
}
