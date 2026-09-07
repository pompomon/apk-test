package com.example.apktest.game.core

import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AdventurePerkControllerTest {
    @Test
    fun offersOccurOnlyAfterNonFinalOddMazesAndDefaultRolloutStaysOff() {
        for (difficulty in DifficultyPresets.all) {
            val config = AdventureConfig.forDifficulty(difficulty)
            val off = AdventureRunController(config, runSeed = 41L)
            val enabled = AdventureRunController(config, runSeed = 41L, perksEnabled = true,
                perkTiers = RunPerkTier.entries.toSet())
            repeat(config.totalMazes) { i ->
                off.prepareCurrentMaze()
                enabled.prepareCurrentMaze()
                assertEquals(off.completeMaze().startingPowerUpCandidates,
                    enabled.completeMaze().startingPowerUpCandidates)
                assertNull(off.state.pendingReward?.perkOffer)
                val shouldOffer = i + 1 in RunPerkGenerator.OFFER_MAZES && i + 1 < config.totalMazes
                assertEquals(shouldOffer, enabled.state.pendingReward?.perkOffer != null)
                finishReward(off)
                finishReward(enabled)
            }
            assertEquals(AdventureStatus.WON, enabled.state.status)
            assertNull(enabled.prepareCurrentMaze())
        }
    }

    @Test
    fun completeAcknowledgeChooseAndDuplicateTapsStayOneTransaction() {
        val c = AdventureRunController(medium(), runSeed = 4L, perksEnabled = true)
        c.prepareCurrentMaze()
        val win = c.completeMaze(5f, 6)
        val pending = c.state.pendingReward!!
        assertEquals(win, c.completeMaze(100f, 200))
        assertEquals(pending, c.state.pendingReward)
        assertFalse(c.choosePerk(1, pending.perkOffer!!.choices.first()))
        assertFalse(c.chooseStartingPowerUp(1, pending.powerUpCandidates.first()))
        assertNull(c.prepareCurrentMaze())
        assertTrue(c.acknowledgeMazeWin(1))
        assertEquals(RewardStage.PERK_CHOICE, c.state.pendingReward!!.stage)
        val selected = pending.perkOffer.choices.first()
        assertFalse(c.choosePerk(2, selected))
        assertFalse(c.choosePerk(1, RunPerkId.FIRST_SHIELD))
        assertTrue(c.choosePerk(1, selected))
        assertFalse(c.choosePerk(1, selected))
        assertEquals(listOf(RunPerkStack(selected, 1)), c.state.runPerks)
        assertEquals(RewardStage.POWER_UP_CHOICE, c.state.pendingReward!!.stage)
        assertTrue(c.chooseStartingPowerUp(1, pending.powerUpCandidates.first()))
        assertFalse(c.chooseStartingPowerUp(1, pending.powerUpCandidates.first()))
        assertEquals(5f, c.state.totalElapsedSeconds, 0f)
        assertEquals(6, c.state.totalSteps)
        assertNotNull(c.prepareCurrentMaze())
    }

    @Test
    fun ownedEffectsSurviveRetryTerminalSummaryAndFlagRollbackButNotFreshRun() {
        val c = acquired(RunPerkId.QUICK_FEET)
        val initial = c.prepareCurrentMaze()!!
        assertEquals(1, initial.runPerkEffects.quickFeetStacks)
        c.onPlayerDied()
        assertEquals(initial, c.prepareCurrentMaze())
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, 0L)
        val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson())!!
        val rollback = AdventureRunController(c.config, restored.toState(), 0L,
            routesEnabled = false, elitesEnabled = false, perksEnabled = false, perkTiers = emptySet())
        assertEquals(initial, rollback.prepareCurrentMaze())
        while (rollback.state.status == AdventureStatus.IN_PROGRESS) rollback.onPlayerDied()
        assertEquals(c.state.runPerks, rollback.state.runPerks)
        assertNotNull(AdventureRunStateSnapshot.fromJson(
            AdventureRunStateSnapshot.fromState(rollback.state, 0L).toJson()))
        assertTrue(AdventureRunController(medium(), runSeed = 0L).state.runPerks.isEmpty())
    }

    @Test
    fun secondWindConsumptionIsIdempotentAndNeverReacquiredOrResetOnDeath() {
        val c = acquired(RunPerkId.SECOND_WIND)
        assertTrue(c.prepareCurrentMaze()!!.runPerkEffects.secondWindAvailable)
        assertFalse(c.consumePerk(RunPerkId.QUICK_FEET))
        assertTrue(c.consumePerk(RunPerkId.SECOND_WIND))
        assertTrue(c.consumePerk(RunPerkId.SECOND_WIND))
        assertFalse(c.prepareCurrentMaze()!!.runPerkEffects.secondWindAvailable)
        val lives = c.state.livesRemaining
        c.onPlayerDied()
        assertEquals(lives - 1, c.state.livesRemaining)
        assertFalse(c.prepareCurrentMaze()!!.runPerkEffects.secondWindAvailable)
        c.completeMaze()
        finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        assertNull(c.state.pendingReward!!.perkOffer)
        assertEquals(1, c.state.perkOfferOrdinal)
        assertEquals(listOf(RunPerkStack(RunPerkId.SECOND_WIND, 1, true)), c.state.runPerks)
    }

    @Test
    fun scoutChoiceLocksActualRosterWithoutRouteOrEliteFeature() {
        val config = medium().copy(
            difficulty = DifficultyPresets.MEDIUM.copy(mazeWidth = 4, mazeHeight = 4),
            baseNpcsPerMaze = 20
        )
        val c = AdventureRunController(config, runSeed = 7L, routesEnabled = false, elitesEnabled = false,
            perksEnabled = true, perkTiers = setOf(RunPerkTier.UNCOMMON))
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(1)
        assertNull(c.state.currentMazeSeed)
        assertTrue(c.choosePerk(1, RunPerkId.SCOUT_SENSE))
        val pending = c.state.pendingReward!!
        val preview = pending.scoutPreview!!
        val seed = c.state.currentMazeSeed!!
        val maze = MazeGenerator.generate(config.difficulty.mazeWidth, config.difficulty.mazeHeight, seed)
        val plan = NpcSpawnPlanner.plan(maze, MazeNavigator(maze), config.difficulty, Random(seed))
        assertEquals(minOf(c.state.currentMazeNpcCount!!, plan.candidates.size), preview.npcCount)
        assertTrue(preview.npcCount < c.state.currentMazeNpcCount!!)
        assertEquals(0, preview.eliteCount)
        val saved = AdventureRunStateSnapshot.fromState(c.state, 7L)
        assertEquals(saved, AdventureRunStateSnapshot.fromJson(saved.toJson(), config))
        finishReward(c)
        val start = c.prepareCurrentMaze()!!
        assertEquals(seed, start.seed)
        c.onPlayerDied()
        assertEquals(start, c.prepareCurrentMaze())
    }

    @Test
    fun ownedScoutWaitsForRouteThenDescribesItsLockedRoster() {
        val fixture = atRouteWithPerk(RunPerkId.SCOUT_SENSE, RouteEventGenerator.AMBUSH_SHORTCUT)
        val c = fixture.first
        assertEquals(RewardStage.ROUTE_CHOICE, c.state.pendingReward!!.stage)
        assertNull(c.state.pendingReward!!.scoutPreview)
        assertNull(c.state.currentMazeSeed)
        assertTrue(c.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT))
        val reward = c.state.pendingReward!!
        assertNotNull(reward.scoutPreview)
        assertEquals(c.state.currentMazeNpcCount, reward.scoutPreview!!.npcCount)
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, fixture.second)
        assertEquals(snapshot, AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun riskDividendPaysOnlyOnSuccessfulAffectedMazeAndRerollsKeepFourChoices() {
        for (route in listOf(RouteEventGenerator.AMBUSH_SHORTCUT, RouteEventGenerator.CURSED_GATE)) {
            val (c, seed) = atRouteWithPerk(RunPerkId.RISK_DIVIDEND, route)
            assertTrue(c.chooseRoute(2, route))
            assertEquals(3, c.state.pendingReward!!.powerUpCandidates.size)
            finishReward(c)
            c.prepareCurrentMaze()
            c.onPlayerDied()
            assertNull(c.state.pendingReward)
            assertEquals(route, c.state.activeRoute!!.choiceId)
            c.prepareCurrentMaze()
            val result = c.completeMaze()
            assertEquals(4, result.startingPowerUpCandidates.size)
            assertEquals(1, c.state.pendingReward!!.rewardOptionBonus)
            assertEquals(route, c.state.pendingReward!!.riskDividendRouteId)
            assertNull(c.state.activeRoute)
            c.acknowledgeMazeWin(3)
            val perk = c.state.pendingReward!!.perkOffer
            if (perk != null) c.choosePerk(3, perk.choices.first())
            val snapshot = AdventureRunStateSnapshot.fromState(c.state, seed)
            assertEquals(snapshot, AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
            if (route == RouteEventGenerator.AMBUSH_SHORTCUT) {
                assertTrue(c.rerollStartingPowerUps(3))
                assertEquals(4, c.state.pendingReward!!.powerUpCandidates.size)
                assertFalse(c.rerollStartingPowerUps(3))
            }
            finishReward(c)
            c.prepareCurrentMaze()
            c.completeMaze()
            assertEquals(0, c.state.pendingReward!!.rewardOptionBonus)
            assertEquals(3, c.state.pendingReward!!.powerUpCandidates.size)
        }
    }

    @Test
    fun gainingRiskDividendAfterTheRiskyWinCannotRetroactivelyPay() {
        val fixture = AdventureRouteEventsTest.atRoute(RouteEventGenerator.AMBUSH_SHORTCUT)
        val before = fixture.controller
        before.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT)
        finishReward(before)
        val c = AdventureRunController(before.config, before.state, fixture.seed, routesEnabled = true,
            perksEnabled = true, perkTiers = setOf(RunPerkTier.RARE))
        c.prepareCurrentMaze()
        c.completeMaze()
        assertEquals(0, c.state.pendingReward!!.rewardOptionBonus)
        c.acknowledgeMazeWin(3)
        assertTrue(c.choosePerk(3, RunPerkId.RISK_DIVIDEND))
        assertEquals(3, c.state.pendingReward!!.powerUpCandidates.size)
        assertEquals(0, c.state.pendingReward!!.rewardOptionBonus)
    }

    @Test
    fun pendingOfferIsRestoredWithoutConsultingCurrentFlagsOrTiers() {
        val c = AdventureRunController(medium(), runSeed = 123L, perksEnabled = true,
            perkTiers = RunPerkTier.entries.toSet(), routesEnabled = true)
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(1)
        val snapshot = AdventureRunStateSnapshot.fromState(c.state, 123L)
        val restored = AdventureRunStateSnapshot.fromJson(snapshot.toJson())!!
        val rollback = AdventureRunController(c.config, restored.toState(), restored.runSeed,
            routesEnabled = false, perksEnabled = false, perkTiers = emptySet())
        assertEquals(c.state.pendingReward, rollback.state.pendingReward)
        val chosen = rollback.state.pendingReward!!.perkOffer!!.choices.first()
        assertTrue(rollback.choosePerk(1, chosen))
        finishReward(rollback)
        assertEquals(listOf(RunPerkStack(chosen, 1)), rollback.state.runPerks)
    }

    @Test
    fun routeAndPerkAtSameCheckpointCannotBeChosenOutOfOrder() {
        val config = medium()
        val seed = (0L..100L).first { RouteEventGenerator(config, it).nextEventMazeIndex(2, 0) == 5 }
        val c = AdventureRunController(config, runSeed = seed, routesEnabled = true,
            perksEnabled = true, perkTiers = setOf(RunPerkTier.COMMON))
        repeat(4) {
            c.prepareCurrentMaze()
            c.completeMaze()
            finishReward(c)
        }
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(5)
        val reward = c.state.pendingReward!!
        assertEquals(RewardStage.ROUTE_CHOICE, reward.stage)
        val perk = reward.perkOffer!!.choices.first()
        assertFalse(c.choosePerk(5, perk))
        assertTrue(c.chooseRoute(5, reward.routeChoices.first().id))
        assertEquals(RewardStage.PERK_CHOICE, c.state.pendingReward!!.stage)
        val routeChosen = AdventureRunStateSnapshot.fromState(c.state, seed)
        assertEquals(routeChosen, AdventureRunStateSnapshot.fromJson(routeChosen.toJson()))
        assertFalse(c.chooseStartingPowerUp(5, c.state.pendingReward!!.powerUpCandidates.first()))
        assertTrue(c.choosePerk(5, perk))
        assertEquals(RewardStage.POWER_UP_CHOICE, c.state.pendingReward!!.stage)
        val perkChosen = AdventureRunStateSnapshot.fromState(c.state, seed)
        assertEquals(perkChosen, AdventureRunStateSnapshot.fromJson(perkChosen.toJson()))
        finishReward(c)
        assertNotNull(c.prepareCurrentMaze())
    }

    @Test
    fun controllerStopsOfferingAStackablePerkAtItsCap() {
        for (target in listOf(RunPerkId.QUICK_FEET, RunPerkId.LONGER_CHARGE, RunPerkId.POCKET_MAGNET)) {
            val c = AdventureRunController(AdventureConfig.forDifficulty(DifficultyPresets.HARD),
                runSeed = 5L, perksEnabled = true, perkTiers = setOf(RunPerkTier.COMMON))
            val cap = RunPerkCatalogue.definition(target).maxStacks
            repeat(8) {
                c.prepareCurrentMaze()
                c.completeMaze()
                c.acknowledgeMazeWin(it + 1)
                val reward = c.state.pendingReward!!
                if (reward.perkOffer != null) {
                    val count = c.state.runPerks.firstOrNull { stack -> stack.id == target }?.stacks ?: 0
                    if (count < cap) assertTrue(c.choosePerk(it + 1, target))
                    else {
                        assertFalse(target in reward.perkOffer.choices)
                        assertFalse(c.choosePerk(it + 1, target))
                    }
                }
                finishReward(c)
            }
            assertEquals(cap, c.state.runPerks.first { it.id == target }.stacks)
        }
    }

    @Test
    fun scoutAlreadyOwnedLocksBeforePerkStageEvenWhenNoRouteIsOffered() {
        val acquired = acquired(RunPerkId.SCOUT_SENSE)
        val c = AdventureRunController(acquired.config, acquired.state, 0L, routesEnabled = false,
            perksEnabled = true, perkTiers = setOf(RunPerkTier.COMMON))
        c.prepareCurrentMaze()
        c.completeMaze()
        c.acknowledgeMazeWin(2)
        assertNotNull(c.state.pendingReward!!.scoutPreview)
        finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        assertNull(c.state.currentMazeSeed)
        c.acknowledgeMazeWin(3)
        assertEquals(RewardStage.PERK_CHOICE, c.state.pendingReward!!.stage)
        val preview = c.state.pendingReward!!.scoutPreview!!
        assertEquals(c.state.currentMazeNpcCount, preview.npcCount)
        val saved = AdventureRunStateSnapshot.fromState(c.state, 0L)
        assertEquals(saved, AdventureRunStateSnapshot.fromJson(saved.toJson()))
    }

    @Test
    fun rewardOptionBonusComposesWithQuietWithoutAddingAnotherSupplyChooser() {
        // Exercise composition directly: today's two/three-maze route cadence does
        // not schedule a new route at the immediately following risky-maze payout.
        for (route in listOf(RouteEventGenerator.QUIET_CORRIDOR, RouteEventGenerator.SUPPLY_CACHE)) {
            val fixture = AdventureRouteEventsTest.atRoute(route)
            val c = fixture.controller
            c.state.pendingReward = c.state.pendingReward!!.copy(
                powerUpCandidates = c.sampleStartingPowerUps(4, 2),
                rewardOptionBonus = 1,
                riskDividendRouteId = RouteEventGenerator.AMBUSH_SHORTCUT
            )
            assertTrue(c.chooseRoute(2, route))
            val expected = if (route == RouteEventGenerator.QUIET_CORRIDOR) 3 else 4
            assertEquals(expected, c.state.pendingReward!!.powerUpCandidates.size)
            assertEquals(RewardStage.POWER_UP_CHOICE, c.state.pendingReward!!.stage)
            finishReward(c)
            assertNull(c.state.pendingReward)
            assertNotNull(c.prepareCurrentMaze())
        }
    }

    @Test
    fun riskDividendDoesNotPayForSupplyOrFinalMaze() {
        val (c, _) = atRouteWithPerk(RunPerkId.RISK_DIVIDEND, RouteEventGenerator.SUPPLY_CACHE)
        c.chooseRoute(2, RouteEventGenerator.SUPPLY_CACHE)
        finishReward(c)
        c.prepareCurrentMaze()
        c.completeMaze()
        assertEquals(0, c.state.pendingReward!!.rewardOptionBonus)
        assertEquals(3, c.state.pendingReward!!.powerUpCandidates.size)
        finishReward(c)
        while (c.state.currentMazeIndex < c.config.totalMazes - 1) {
            c.prepareCurrentMaze()
            c.completeMaze()
            finishReward(c)
        }
        c.prepareCurrentMaze()
        val result = c.completeMaze()
        assertTrue(result.runComplete)
        assertTrue(result.startingPowerUpCandidates.isEmpty())
        assertNull(c.state.pendingReward)
        assertTrue(c.state.runPerks.any { it.id == RunPerkId.RISK_DIVIDEND })
    }

    @Test
    fun scoutElitePreviewSurvivesDisablingBothGenerationFlags() {
        val fixture = (0L..256L).firstNotNullOfOrNull { seed ->
            val c = AdventureRunController(medium(), runSeed = seed, elitesEnabled = true,
                perksEnabled = true, perkTiers = setOf(RunPerkTier.UNCOMMON))
            c.prepareCurrentMaze()
            c.completeMaze()
            c.acknowledgeMazeWin(1)
            c.choosePerk(1, RunPerkId.SCOUT_SENSE)
            if (c.state.pendingReward!!.scoutPreview!!.eliteCount == 1) c to seed else null
        } ?: error("Missing deterministic Scout/Tracker fixture")
        val c = fixture.first
        val saved = AdventureRunStateSnapshot.fromState(c.state, fixture.second)
        val restored = AdventureRunStateSnapshot.fromJson(saved.toJson())!!
        val rollback = AdventureRunController(c.config, restored.toState(), fixture.second,
            elitesEnabled = false, perksEnabled = false)
        assertEquals(c.state.pendingReward!!.scoutPreview, rollback.state.pendingReward!!.scoutPreview)
        finishReward(c)
        finishReward(rollback)
        assertEquals(c.prepareCurrentMaze(), rollback.prepareCurrentMaze())
        assertEquals(1, rollback.state.currentMazeNpcSpawnSpecs.count { it.eliteModifier != null })
    }

    @Test
    fun disablingRoutesAndPerksDoesNotEraseAnAlreadyEarnedRiskDividend() {
        val (c, seed) = atRouteWithPerk(RunPerkId.RISK_DIVIDEND, RouteEventGenerator.AMBUSH_SHORTCUT)
        c.chooseRoute(2, RouteEventGenerator.AMBUSH_SHORTCUT)
        finishReward(c)
        val saved = AdventureRunStateSnapshot.fromState(c.state, seed)
        val restored = AdventureRunStateSnapshot.fromJson(saved.toJson())!!
        val rollback = AdventureRunController(c.config, restored.toState(), seed,
            routesEnabled = false, perksEnabled = false, perkTiers = emptySet())
        rollback.prepareCurrentMaze()
        rollback.completeMaze()
        assertEquals(4, rollback.state.pendingReward!!.powerUpCandidates.size)
        assertEquals(1, rollback.state.pendingReward!!.rewardOptionBonus)
        assertNull(rollback.state.pendingReward!!.perkOffer)
        val paid = AdventureRunStateSnapshot.fromState(rollback.state, seed)
        assertEquals(paid, AdventureRunStateSnapshot.fromJson(paid.toJson()))
    }

    companion object {
        internal fun medium(): AdventureConfig = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)

        internal fun finishReward(c: AdventureRunController) {
            var reward = c.state.pendingReward ?: return
            if (reward.stage == RewardStage.WIN_ACKNOWLEDGEMENT) c.acknowledgeMazeWin(reward.mazeIndexCompleted)
            reward = c.state.pendingReward!!
            if (reward.stage == RewardStage.ROUTE_CHOICE) c.chooseRoute(reward.mazeIndexCompleted, reward.routeChoices.first().id)
            reward = c.state.pendingReward!!
            if (reward.stage == RewardStage.PERK_CHOICE) c.choosePerk(reward.mazeIndexCompleted, reward.perkOffer!!.choices.first())
            reward = c.state.pendingReward!!
            assertTrue(c.chooseStartingPowerUp(reward.mazeIndexCompleted, reward.powerUpCandidates.first()))
        }

        internal fun acquired(id: RunPerkId, seed: Long = 0L, routes: Boolean = false): AdventureRunController {
            val c = AdventureRunController(medium(), runSeed = seed, routesEnabled = routes,
                perksEnabled = true, perkTiers = setOf(RunPerkCatalogue.definition(id).tier))
            c.prepareCurrentMaze()
            c.completeMaze()
            c.acknowledgeMazeWin(1)
            assertTrue(c.choosePerk(1, id))
            finishReward(c)
            return c
        }

        internal fun atRouteWithPerk(id: RunPerkId, route: String): Pair<AdventureRunController, Long> {
            for (seed in 0L..1_000L) {
                val c = acquired(id, seed, routes = true)
                c.prepareCurrentMaze()
                c.completeMaze()
                c.acknowledgeMazeWin(2)
                if (c.state.pendingReward!!.routeChoices.any { it.id == route }) return c to seed
            }
            error("No deterministic fixture for route $route")
        }
    }
}
