package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the Adventure-mode generalisation of [GameEngine] NPC spawning:
 *  - [GameEngine.configureAdventureMaze] overrides the preset's `npcCount`
 *  - per-NPC `policyType` reflects the supplied policy list (by spawn id)
 *  - Snapshot/restore round-trips the override + per-NPC policy list
 */
class GameEngineAdventureSpawnTest {

    private fun easyPreset() = DifficultyPresets.EASY

    @Test
    fun configureAdventureMazeOverridesNpcCount() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        // Easy normally spawns 1 NPC; override to 4.
        engine.configureAdventureMaze(
            npcCount = 4,
            policies = listOf(
                NpcPolicyType.DIRECT_CHASE,
                NpcPolicyType.PATROL_GUARD,
                NpcPolicyType.PREDICTIVE_CHASE,
                NpcPolicyType.PATROL_GUARD
            )
        )
        engine.restart(seed = 1L)
        assertEquals(4, engine.npcs.size)
        assertEquals(NpcPolicyType.DIRECT_CHASE, engine.npcs[0].policyType)
        assertEquals(NpcPolicyType.PATROL_GUARD, engine.npcs[1].policyType)
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, engine.npcs[2].policyType)
        assertEquals(NpcPolicyType.PATROL_GUARD, engine.npcs[3].policyType)
    }

    @Test
    fun configureAdventureMazeFallsBackToEngineNpcPolicyWhenListShort() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        engine.setNpcPolicy(NpcPolicyType.PREDICTIVE_CHASE)
        engine.configureAdventureMaze(
            npcCount = 3,
            policies = listOf(NpcPolicyType.DIRECT_CHASE) // shorter than count
        )
        engine.restart(seed = 1L)
        assertEquals(3, engine.npcs.size)
        assertEquals(NpcPolicyType.DIRECT_CHASE, engine.npcs[0].policyType)
        // Indexes 1 and 2 fall back to the engine's [npcPolicyType].
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, engine.npcs[1].policyType)
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, engine.npcs[2].policyType)
    }

    @Test
    fun clearAdventureMazeConfigRevertsToPresetCount() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        engine.configureAdventureMaze(npcCount = 5, policies = emptyList())
        engine.restart(seed = 1L)
        assertEquals(5, engine.npcs.size)

        engine.clearAdventureMazeConfig()
        engine.restart(seed = 1L)
        assertEquals(easyPreset().npcCount, engine.npcs.size)
    }

    @Test
    fun snapshotRoundTripsNpcCountOverrideAndPolicies() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        engine.configureAdventureMaze(
            npcCount = 3,
            policies = listOf(
                NpcPolicyType.PATROL_GUARD,
                NpcPolicyType.PREDICTIVE_CHASE,
                NpcPolicyType.DIRECT_CHASE
            )
        )
        engine.restart(seed = 1L)
        val snapshot = engine.snapshot()
        assertEquals(3, snapshot.npcCountOverride)
        assertEquals(3, snapshot.npcPolicies.size)
        assertEquals(NpcPolicyType.PATROL_GUARD, snapshot.npcPolicies[0])

        // Re-hydrate a fresh engine from the snapshot and verify per-NPC
        // policy types restored.
        val fresh = GameEngine(easyPreset(), seed = 999L)
        fresh.restore(snapshot)
        assertEquals(3, fresh.npcs.size)
        assertEquals(3, fresh.npcCountOverride)
        assertEquals(NpcPolicyType.PATROL_GUARD, fresh.npcs[0].policyType)
        assertEquals(NpcPolicyType.PREDICTIVE_CHASE, fresh.npcs[1].policyType)
        assertEquals(NpcPolicyType.DIRECT_CHASE, fresh.npcs[2].policyType)
    }

    @Test
    fun singleMazeEngineDefaultsLeaveNpcCountOverrideNull() {
        val engine = GameEngine(easyPreset(), seed = 1L)
        // No call to configureAdventureMaze — pure single-maze behaviour.
        assertEquals(null, engine.npcCountOverride)
        assertEquals(null, engine.npcPolicies)
        assertEquals(easyPreset().npcCount, engine.npcs.size)
        assertTrue(engine.npcs.all { it.policyType == engine.npcPolicyType })
    }

    @Test
    fun mediumPlacementKeepsInitialNpcsAwayFromDirectPathBuffer() {
        val seed = 19L
        val preset = placementPreset(
            name = "Placement-Medium",
            npcDirectPathSpawnBuffer = 1,
            npcCount = bufferedSpawnCellCount(seed, npcDirectPathSpawnBuffer = 1)
        )

        val engine = GameEngine(preset, seed)
        val directPath = engine.navigator.bfsPath(engine.maze.start, engine.maze.exit)
        val bufferedPathCells = directPathBufferCells(engine.maze, directPath, preset.npcDirectPathSpawnBuffer)

        assertTrue("expected NPCs for placement check", engine.npcs.isNotEmpty())
        assertTrue(engine.npcs.all { it.position !in bufferedPathCells })
    }

    @Test
    fun hardPlacementAllowsNpcsNearButNotOnDirectPath() {
        val seed = 19L
        val preset = placementPreset(
            name = "Placement-Hard",
            npcDirectPathSpawnBuffer = 0,
            npcCount = bufferedSpawnCellCount(seed, npcDirectPathSpawnBuffer = 0)
        )

        val engine = GameEngine(preset, seed)
        val directPath = engine.navigator.bfsPath(engine.maze.start, engine.maze.exit)
        val directPathCells = directPath.toSet()
        val nearPathCells = directPathBufferCells(engine.maze, directPath, buffer = 1) - directPathCells
        val npcPositions = engine.npcs.map { it.position }.toSet()

        assertTrue("expected cells next to the direct path for this seeded maze", nearPathCells.isNotEmpty())
        assertTrue("hard placement should allow NPCs next to the direct path", npcPositions.any { it in nearPathCells })
        assertFalse("hard placement should keep initial NPCs off the direct path", npcPositions.any { it in directPathCells })
    }

    @Test
    fun classicAndAdventureUseSamePlacementForSameDifficultyAndCount() {
        val seed = 23L
        val preset = placementPreset(
            name = "Placement-Parity",
            npcDirectPathSpawnBuffer = 0,
            npcCount = 4
        )
        val classic = GameEngine(preset, seed)
        val adventure = GameEngine(preset, seed)

        adventure.configureAdventureMaze(
            npcCount = preset.npcCount,
            policies = listOf(
                NpcPolicyType.DIRECT_CHASE,
                NpcPolicyType.PATROL_GUARD,
                NpcPolicyType.PREDICTIVE_CHASE,
                NpcPolicyType.DIRECT_CHASE
            )
        )
        adventure.restart(seed)

        assertEquals(classic.npcs.map { it.position }, adventure.npcs.map { it.position })
    }

    private fun placementPreset(
        name: String,
        npcDirectPathSpawnBuffer: Int,
        npcCount: Int
    ): DifficultyPreset = DifficultyPreset(
        name = name,
        mazeWidth = PLACEMENT_MAZE_WIDTH,
        mazeHeight = PLACEMENT_MAZE_HEIGHT,
        npcCount = npcCount,
        playerMovesPerSecond = 4f,
        npcMovesPerSecond = 1f,
        npcVisionRange = 4,
        powerUpPickupLifetimeSeconds = 600f,
        powerUpRespawnIntervalSeconds = null,
        initialPowerUpTypes = emptyList(),
        npcDirectPathSpawnBuffer = npcDirectPathSpawnBuffer
    )

    private fun bufferedSpawnCellCount(seed: Long, npcDirectPathSpawnBuffer: Int): Int {
        val maze = MazeGenerator.generate(PLACEMENT_MAZE_WIDTH, PLACEMENT_MAZE_HEIGHT, seed)
        val directPath = MazeNavigator(maze).bfsPath(maze.start, maze.exit)
        val bufferedPathCells = directPathBufferCells(maze, directPath, npcDirectPathSpawnBuffer)
        return allSpawnCells(maze).count { it !in bufferedPathCells }
    }

    private fun allSpawnCells(maze: Maze): List<GridPos> {
        val cells = mutableListOf<GridPos>()
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos != maze.start && pos != maze.exit) cells += pos
            }
        }
        return cells
    }

    private fun directPathBufferCells(maze: Maze, directPath: List<GridPos>, buffer: Int): Set<GridPos> {
        val cells = mutableSetOf<GridPos>()
        directPath.forEach { pathCell ->
            for (dy in -buffer..buffer) {
                for (dx in -buffer..buffer) {
                    val pos = GridPos(pathCell.x + dx, pathCell.y + dy)
                    if (maze.inBounds(pos)) cells += pos
                }
            }
        }
        return cells
    }

    private companion object {
        private const val PLACEMENT_MAZE_WIDTH = 8
        private const val PLACEMENT_MAZE_HEIGHT = 8
    }
}
