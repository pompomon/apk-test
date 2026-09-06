package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTrackerTest {
    private val tracker = NpcSpawnSpec(NpcPolicyType.PATROL_GUARD, EliteNpcModifier.TRACKER)

    @Test
    fun fullSpecsApplyBySpawnIndexWithoutChangingPositionsOrPickupRandomStream() {
        val specs = listOf(NpcSpawnSpec(NpcPolicyType.DIRECT_CHASE), tracker, NpcSpawnSpec(NpcPolicyType.PATROL_GUARD))
        for (seed in listOf(1L, 19L, 23L)) {
            val plain = GameEngine(DifficultyPresets.MEDIUM, seed)
            plain.configureAdventureMaze(3, specs.map { it.policyType })
            plain.restart(seed)
            val elite = GameEngine(DifficultyPresets.MEDIUM, seed)
            elite.configureAdventureMaze(3, specs.map { it.policyType }, npcSpawnSpecs = specs)
            elite.restart(seed)
            assertEquals(plain.npcs.map { it.position }, elite.npcs.map { it.position })
            assertEquals(plain.adventurers, elite.adventurers)
            assertEquals(plain.spawnedPowerUps, elite.spawnedPowerUps)
            assertEquals(specs, elite.npcs.map { NpcSpawnSpec(it.policyType, it.eliteModifier) })
            assertTrue(plain.npcs.all { it.eliteModifier == null })
        }
    }

    @Test
    fun explicitNullModifiersKeepClassicSpawnAndTickBehavior() {
        for (type in NpcPolicyType.entries) {
            val classic = GameEngine(DifficultyPresets.MEDIUM, 71L)
            classic.setNpcPolicy(type)
            classic.restart(71L)
            val explicit = GameEngine(DifficultyPresets.MEDIUM, 71L)
            explicit.setNpcPolicy(type)
            explicit.configureAdventureMaze(2, listOf(type, type), npcSpawnSpecs = List(2) { NpcSpawnSpec(type) })
            explicit.restart(71L)
            classic.applyStartingPowerUp(PowerUpType.INVISIBILITY)
            explicit.applyStartingPowerUp(PowerUpType.INVISIBILITY)
            repeat(30) {
                classic.update(0.1f)
                explicit.update(0.1f)
                assertEquals(classic.npcs, explicit.npcs)
                assertEquals(classic.adventurers, explicit.adventurers)
                assertEquals(classic.spawnedPowerUps, explicit.spawnedPowerUps)
                assertEquals(classic.status, explicit.status)
            }
        }
    }

    @Test
    fun legacyPolicyPaddingIsNormalizedIntoFullRestartSpecs() {
        val engine = GameEngine(DifficultyPresets.EASY, 1L)
        engine.setNpcPolicy(NpcPolicyType.PREDICTIVE_CHASE)
        engine.configureAdventureMaze(3, listOf(NpcPolicyType.PATROL_GUARD))
        assertEquals(
            listOf(NpcSpawnSpec(NpcPolicyType.PATROL_GUARD),
                NpcSpawnSpec(NpcPolicyType.PREDICTIVE_CHASE), NpcSpawnSpec(NpcPolicyType.PREDICTIVE_CHASE)),
            engine.npcSpawnSpecs
        )
        assertEquals(engine.npcSpawnSpecs!!.map { it.policyType }, engine.npcPolicies)
    }

    @Test
    fun fullSpecsAreCopiedAndMustExactlyMatchRequestedCount() {
        val engine = GameEngine(DifficultyPresets.EASY, 1L)
        val specs = mutableListOf(tracker)
        engine.configureAdventureMaze(1, emptyList(), npcSpawnSpecs = specs)
        specs.clear()
        assertEquals(listOf(tracker), engine.npcSpawnSpecs)
        for (count in listOf(0, 2)) {
            assertTrue(runCatching {
                engine.configureAdventureMaze(count, emptyList(), npcSpawnSpecs = listOf(tracker))
            }.exceptionOrNull() is IllegalArgumentException)
        }
        assertEquals(listOf(tracker), engine.npcSpawnSpecs)
    }

    @Test
    fun clearConfigDifficultyAndUniformPolicyClearActiveAndRestartModifiers() {
        val clearers: List<(GameEngine) -> Unit> = listOf(
            { it.clearAdventureMazeConfig() },
            { it.applyDifficulty(DifficultyPresets.HARD) },
            { it.setNpcPolicy(NpcPolicyType.PREDICTIVE_CHASE) }
        )
        for (clear in clearers) {
            val engine = GameEngine(DifficultyPresets.MEDIUM, 1L)
            engine.configureAdventureMaze(1, emptyList(), npcSpawnSpecs = listOf(tracker))
            engine.restart(1L)
            assertEquals(EliteNpcModifier.TRACKER, engine.npcs.single().eliteModifier)
            clear(engine)
            assertNull(engine.npcSpawnSpecs)
            assertNull(engine.npcPolicies)
            assertNull(engine.npcCountOverride)
            assertTrue(engine.npcs.all { it.eliteModifier == null })
            engine.restart(1L)
            assertEquals(engine.difficulty.npcCount, engine.npcs.size)
            assertTrue(engine.npcs.all { it.eliteModifier == null && it.policyType == engine.npcPolicyType })
        }
    }

    @Test
    fun restartClearsSearchAndEffectsButKeepsLockedTrackerAssignment() {
        val engine = engine()
        engine.npcs.single().apply {
            state = NpcState.SEARCH
            searchTicksRemaining = 3
            lastKnownPlayerPos = GridPos(2, 2)
        }
        engine.applyStartingPowerUp(PowerUpType.FREEZE)
        engine.restart(1L)
        assertEquals(listOf(tracker), engine.npcSpawnSpecs)
        assertEquals(EliteNpcModifier.TRACKER, engine.npcs.single().eliteModifier)
        assertEquals(NpcState.PATROL, engine.npcs.single().state)
        assertNull(engine.npcs.single().lastKnownPlayerPos)
        assertTrue(engine.activePowerUps.isEmpty())
    }

    @Test
    fun trackerUsesNormalNpcCadenceWithoutAdditionalMoves() {
        val engine = engine()
        engine.update(0.99f)
        assertEquals(GridPos(1, 1), engine.npcs.single().position)
        engine.update(0.02f)
        assertEquals(GridPos(2, 1), engine.npcs.single().position)
        assertEquals(1, engine.npcs.single().animationFrame)
        assertEquals(1.01f, engine.npcs.single().lastMoveAtSeconds, 0.0001f)
        engine.update(0.99f)
        assertEquals(GridPos(3, 1), engine.npcs.single().position)
    }

    @Test
    fun playerFreezeStopsTrackerAndDoesNotAdvanceItsState() {
        val engine = engine()
        engine.applyStartingPowerUp(PowerUpType.FREEZE)
        val before = engine.npcs.single().copy()
        repeat(20) { engine.update(0.1f) }
        assertEquals(before, engine.npcs.single())
        assertTrue(engine.isPlayerPowerUpTintActive(PowerUpType.FREEZE))
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun slowTimeHalvesTrackerCadenceWithoutChangingAcquisitionOrSpeedMetadata() {
        val engine = engine()
        engine.applyStartingPowerUp(PowerUpType.SLOW_TIME)
        assertEquals(0.5f, engine.hudState().npcSpeed, 0f)
        engine.update(1.99f)
        assertEquals(GridPos(1, 1), engine.npcs.single().position)
        engine.update(0.02f)
        assertEquals(GridPos(2, 1), engine.npcs.single().position)
        assertEquals(engine.player.position, engine.npcs.single().lastKnownPlayerPos)
        assertEquals(1f, engine.difficulty.npcMovesPerSecond, 0f)
    }

    @Test
    fun shieldProtectsFromTrackerButDoesNotHidePlayerOrStopPursuit() {
        val engine = engine()
        engine.player.position = GridPos(2, 1)
        engine.applyStartingPowerUp(PowerUpType.SHIELD)
        engine.update(1.01f)
        assertEquals(engine.player.position, engine.npcs.single().position)
        assertEquals(engine.player.position, engine.npcs.single().lastKnownPlayerPos)
        assertEquals(GameStatus.RUNNING, engine.status)

        val unshielded = engine()
        unshielded.player.position = GridPos(2, 1)
        unshielded.update(1.01f)
        assertEquals(GameStatus.LOSE, unshielded.status)
    }

    @Test
    fun npcPickedFreezeStillFreezesPlayerNotTrackerAndDoesNotGrantCollisionImmunity() {
        val engine = engine(listOf(PowerUpType.FREEZE))
        val pickup = engine.spawnedPowerUps.single()
        engine.simulateNpcArrivalForTest(0, pickup.position)
        engine.npcs.single().position = GridPos(1, 1)
        val playerBefore = engine.player.position
        engine.queueManualMove(Direction.NORTH)
        engine.update(1.01f)
        assertEquals(playerBefore, engine.player.position)
        assertEquals(GridPos(2, 1), engine.npcs.single().position)
        assertFalse(engine.isPlayerPowerUpTintActive(PowerUpType.FREEZE))
        assertEquals(PowerUpType.FREEZE, engine.npcMazeTintType)
        engine.player.position = GridPos(3, 1)
        engine.update(1f)
        assertEquals(GameStatus.LOSE, engine.status)
    }

    @Test
    fun npcPickedNonFreezePowerupsRemainUnconsumedForTracker() {
        for (type in PowerUpType.entries.filter { it != PowerUpType.FREEZE }) {
            val engine = engine(listOf(type))
            val pickup = engine.spawnedPowerUps.single()
            engine.simulateNpcArrivalForTest(0, pickup.position)
            assertEquals(listOf(pickup), engine.spawnedPowerUps)
            assertTrue(engine.activePowerUps.isEmpty())
            assertNull(engine.npcMazeTintType)
        }
    }

    private fun engine(powerups: List<PowerUpType> = emptyList()): GameEngine {
        val preset = DifficultyPresets.EASY.copy(
            name = "Tracker-Test", mazeWidth = 10, mazeHeight = 10,
            playerMovesPerSecond = 4f, npcMovesPerSecond = 1f,
            initialPowerUpTypes = powerups, powerUpRespawnIntervalSeconds = null,
            adventurerCount = 0
        )
        return GameEngine(preset, 1L).apply {
            configureAdventureMaze(1, emptyList(), npcSpawnSpecs = listOf(tracker))
            restart(1L)
            for (y in 0 until maze.height) {
                for (x in 0 until maze.width) {
                    maze.removeWall(GridPos(x, y), Direction.EAST)
                    maze.removeWall(GridPos(x, y), Direction.NORTH)
                }
            }
            player.position = GridPos(7, 1)
            npcs.single().position = GridPos(1, 1)
        }
    }
}
