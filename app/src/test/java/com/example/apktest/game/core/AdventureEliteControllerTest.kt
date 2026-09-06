package com.example.apktest.game.core

import kotlin.math.abs
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Test

class AdventureEliteControllerTest {
    @Test
    fun enablingElitesPreservesBasePoliciesSeedsCountsAndRewardStreams() {
        for (difficulty in DifficultyPresets.all) {
            val config = AdventureConfig.forDifficulty(difficulty)
            for (seed in 0L..7L) {
                val disabled = AdventureRunController(config, runSeed = seed, elitesEnabled = false)
                val enabled = AdventureRunController(config, runSeed = seed, elitesEnabled = true)
                val repeated = AdventureRunController(config, runSeed = seed, elitesEnabled = true)
                repeat(config.totalMazes) {
                    val plain = disabled.prepareCurrentMaze()!!
                    val elite = enabled.prepareCurrentMaze()!!
                    assertEquals(plain, elite.copy(npcSpawnSpecs = plain.npcSpawnSpecs))
                    assertEquals(plain.npcPolicies, elite.npcPolicies)
                    assertTrue(plain.npcSpawnSpecs.all { it.eliteModifier == null })
                    assertEquals(elite, repeated.prepareCurrentMaze())
                    assertEquals(elite, enabled.prepareCurrentMaze())
                    assertEquals(disabled.onMazeWon(), enabled.onMazeWon())
                    repeated.onMazeWon()
                }
            }
        }
    }

    @Test
    fun everyGeneratedEliteIsAnActuallySpawnedPatrolOutsideUnsafeAndAdjacentCells() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(baseNpcsPerMaze = 8)
        for (seed in 0L..15L) {
            val c = AdventureRunController(config, runSeed = seed, elitesEnabled = true)
            repeat(config.totalMazes) { index ->
                val spec = c.prepareCurrentMaze()!!
                val engine = GameEngine(spec.difficulty, spec.seed)
                engine.configureAdventureMaze(spec.npcCount, spec.npcPolicies, npcSpawnSpecs = spec.npcSpawnSpecs)
                engine.restart(spec.seed)
                val plan = NpcSpawnPlanner.plan(engine.maze, engine.navigator, spec.difficulty, Random(spec.seed))
                assertEquals(plan.candidates.take(spec.npcCount), engine.npcs.map { it.position })
                assertEquals(spec.npcSpawnSpecs.take(engine.npcs.size),
                    engine.npcs.map { NpcSpawnSpec(it.policyType, it.eliteModifier) })
                val elites = engine.npcs.filter { it.eliteModifier != null }
                assertTrue(elites.size <= minOf(1, engine.npcs.size / 2))
                val path = engine.navigator.bfsPath(engine.maze.start, engine.maze.exit)
                for (npc in elites) {
                    assertEquals(NpcPolicyType.PATROL_GUARD, npc.policyType)
                    assertTrue(npc.position in plan.eliteEligiblePositions)
                    assertTrue(distance(npc.position, engine.maze.start) > 1)
                    assertTrue(path.all { distance(npc.position, it) > spec.difficulty.npcDirectPathSpawnBuffer })
                }
                if (index + 1 in listOf(4, 7, config.totalMazes)) assertTrue(elites.isEmpty())
                c.onMazeWon()
            }
        }
    }

    @Test
    fun deathAndFlagChangesPreserveTheEntireLockAndStartingRewardUntilWinOrLoss() {
        val (config, state) = lockedState()
        val c = AdventureRunController(config, state, 19L, elitesEnabled = false)
        val before = c.prepareCurrentMaze()!!
        assertEquals(1, before.npcSpawnSpecs.count { it.eliteModifier != null })
        assertFalse(c.onPlayerDied().runOver)
        assertEquals(before, c.prepareCurrentMaze())
        val restored = AdventureRunStateSnapshot.fromJson(
            AdventureRunStateSnapshot.fromState(c.state, 19L).toJson())!!
        val toggled = AdventureRunController(config, restored.toState(), 19L, elitesEnabled = true)
        assertEquals(before, toggled.prepareCurrentMaze())
        toggled.onMazeWon()
        assertNull(toggled.state.currentMazeSeed)
        assertNull(toggled.state.currentMazeNpcCount)
        assertTrue(toggled.state.currentMazeNpcSpawnSpecs.isEmpty())
        assertNull(toggled.state.pendingStartingPowerUp)
        assertTrue(c.onPlayerDied().runOver)
        assertNull(c.state.currentMazeSeed)
        assertNull(c.state.currentMazeNpcCount)
        assertTrue(c.state.currentMazeNpcSpawnSpecs.isEmpty())
    }

    @Test
    fun disabledPlainAndZeroCountLocksCannotGainElitesAfterFeatureToggle() {
        for (count in listOf(0, 4)) {
            val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(baseNpcsPerMaze = count)
            val c = AdventureRunController(config, runSeed = 19L, elitesEnabled = false)
            val spec = c.prepareCurrentMaze()!!
            assertEquals(count, spec.npcCount)
            val saved = AdventureRunStateSnapshot.fromState(c.state, 19L)
            val loaded = AdventureRunStateSnapshot.fromJson(saved.toJson(), config)!!
            // A changed future generator/configuration must not overwrite an existing empty lock.
            val enabled = AdventureRunController(config.copy(baseNpcsPerMaze = 8),
                loaded.toState(), 19L, elitesEnabled = true)
            assertEquals(spec, enabled.prepareCurrentMaze())
            assertTrue(enabled.state.currentMazeNpcSpawnSpecs.all { it.eliteModifier == null })
        }
    }

    @Test
    fun tinyMazeKeepsRequestedPoliciesButNeverDecoratesUnsafeFallbackSpawns() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(
            difficulty = DifficultyPresets.HARD.copy(mazeWidth = 4, mazeHeight = 4, npcDirectPathSpawnBuffer = 4),
            baseNpcsPerMaze = 20
        )
        val enabled = AdventureRunController(config, runSeed = 19L, elitesEnabled = true)
        val disabled = AdventureRunController(config, runSeed = 19L, elitesEnabled = false)
        val spec = enabled.prepareCurrentMaze()!!
        assertEquals(disabled.prepareCurrentMaze(), spec)
        assertEquals(20, spec.npcSpawnSpecs.size)
        assertTrue(spec.npcSpawnSpecs.all { it.eliteModifier == null })
        val engine = GameEngine(spec.difficulty, spec.seed)
        engine.configureAdventureMaze(spec.npcCount, spec.npcPolicies, npcSpawnSpecs = spec.npcSpawnSpecs)
        engine.restart(spec.seed)
        assertEquals(14, engine.npcs.size)
        assertEquals(spec.npcSpawnSpecs, engine.snapshot().npcSpawnSpecs)
    }

    @Test
    fun routeChoiceLocksElitesBeforeRewardAndFlagDisabledReloadPreservesThem() {
        val (c, seed) = atSupplyRouteWithElite()
        assertNull(c.prepareCurrentMaze())
        val locked = c.state.currentMazeNpcSpawnSpecs.toList()
        val saved = AdventureRunStateSnapshot.fromState(c.state, seed)
        val loaded = AdventureRunStateSnapshot.fromJson(saved.toJson(), c.config)!!
        val disabled = AdventureRunController(c.config, loaded.toState(), seed,
            routesEnabled = false, elitesEnabled = false)
        val reward = disabled.state.pendingReward!!
        assertTrue(disabled.chooseStartingPowerUp(reward.mazeIndexCompleted, reward.powerUpCandidates.first()))
        val prepared = disabled.prepareCurrentMaze()!!
        assertEquals(locked, prepared.npcSpawnSpecs)
        assertFalse(disabled.onPlayerDied().runOver)
        assertEquals(prepared, disabled.prepareCurrentMaze())
    }

    @Test
    fun riskyRouteSelectionSuppressesNewElitesBeforeTheMazeStarts() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD).copy(baseNpcsPerMaze = 8)
        for (routeId in listOf(RouteEventGenerator.AMBUSH_SHORTCUT, RouteEventGenerator.CURSED_GATE)) {
            val c = (0L..63L).firstNotNullOfOrNull { seed ->
                val candidate = AdventureRunController(config, runSeed = seed,
                    routesEnabled = true, elitesEnabled = true)
                candidate.onMazeWon()
                candidate.completeMaze()
                candidate.acknowledgeMazeWin(2)
                candidate.takeIf { it.chooseRoute(2, routeId) }
            }
            assertNotNull("No deterministic offer for $routeId", c)
            assertEquals(config.npcCountForMaze(3) + if (routeId == RouteEventGenerator.AMBUSH_SHORTCUT) 1 else 0,
                c!!.state.currentMazeNpcCount)
            assertTrue(c.state.currentMazeNpcSpawnSpecs.all { it.eliteModifier == null })
            assertNotNull(c.state.currentMazeSeed)
        }
    }

    @Test
    fun easyHasNoEarlyElitesEvenWhenInjectedOnAndDefaultGateNeverAddsThem() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.EASY).copy(baseNpcsPerMaze = 8)
        val injected = AdventureRunController(config, runSeed = 19L, elitesEnabled = true)
        val default = AdventureRunController(config, runSeed = 19L)
        repeat(config.totalMazes) { index ->
            val spec = injected.prepareCurrentMaze()!!
            if (index < 2) assertTrue(spec.npcSpawnSpecs.all { it.eliteModifier == null })
            assertTrue(default.prepareCurrentMaze()!!.npcSpawnSpecs.all { it.eliteModifier == null })
            injected.onMazeWon()
            default.onMazeWon()
        }
    }

    private fun atSupplyRouteWithElite(): Pair<AdventureRunController, Long> {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
            .copy(initialLives = 3, baseNpcsPerMaze = 8)
        for (seed in 0L..63L) {
            val c = AdventureRunController(config, runSeed = seed, routesEnabled = true, elitesEnabled = true)
            c.prepareCurrentMaze()
            c.onMazeWon()
            c.prepareCurrentMaze()
            c.completeMaze()
            c.acknowledgeMazeWin(2)
            if (!c.chooseRoute(2, RouteEventGenerator.SUPPLY_CACHE)) continue
            if (c.state.currentMazeNpcSpawnSpecs.any { it.eliteModifier != null }) return c to seed
        }
        throw AssertionError("No deterministic Supply fixture with an eligible Patrol")
    }

    private fun lockedState(): Pair<AdventureConfig, AdventureRunState> {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        return config to AdventureRunState(
            difficultyName = config.difficulty.name,
            currentMazeIndex = 1,
            livesRemaining = 2,
            currentMazeSeed = 93L,
            currentMazeNpcSpawnSpecs = listOf(NpcSpawnSpec(NpcPolicyType.DIRECT_CHASE),
                NpcSpawnSpec(NpcPolicyType.PATROL_GUARD, EliteNpcModifier.TRACKER)),
            pendingStartingPowerUp = PowerUpType.SHIELD
        )
    }

    private fun distance(a: GridPos, b: GridPos): Int = maxOf(abs(a.x - b.x), abs(a.y - b.y))
}
