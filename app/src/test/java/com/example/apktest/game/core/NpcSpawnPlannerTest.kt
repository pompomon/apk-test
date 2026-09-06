package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class NpcSpawnPlannerTest {
    @Test
    fun planningConsumesOnlyTheLegacyShuffleAndPreservesAllCandidates() {
        DifficultyPresets.all.forEach { difficulty ->
            for (seed in 0L..15L) {
                val maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
                val actualRandom = Random(seed)
                val expectedRandom = Random(seed)
                val expected = mutableListOf<GridPos>()
                for (y in 0 until maze.height) {
                    for (x in 0 until maze.width) {
                        val cell = GridPos(x, y)
                        if (cell != maze.start && cell != maze.exit) expected += cell
                    }
                }
                expected.shuffle(expectedRandom)
                val plan = NpcSpawnPlanner.plan(maze, MazeNavigator(maze), difficulty, actualRandom)
                assertEquals(expected.toSet(), plan.candidates.toSet())
                assertEquals(expected.size, plan.candidates.size)
                assertEquals(expectedRandom.nextLong(), actualRandom.nextLong())
                assertEquals(plan, NpcSpawnPlanner.plan(maze, MazeNavigator(maze), difficulty, Random(seed)))
            }
        }
    }

    @Test
    fun eliteEligibilityIsStrictEvenWhenOrdinaryPlacementFallsBack() {
        DifficultyPresets.all.forEach { difficulty ->
            for (seed in 0L..15L) {
                val maze = MazeGenerator.generate(difficulty.mazeWidth, difficulty.mazeHeight, seed)
                val navigator = MazeNavigator(maze)
                val path = navigator.bfsPath(maze.start, maze.exit)
                val plan = NpcSpawnPlanner.plan(maze, navigator, difficulty, Random(seed))
                plan.eliteEligiblePositions.forEach { cell ->
                    assertTrue(cell in plan.candidates)
                    assertTrue(chebyshev(cell, maze.start) > 1)
                    assertTrue(path.all { chebyshev(cell, it) > difficulty.npcDirectPathSpawnBuffer })
                }
                assertFalse(maze.start in plan.candidates)
                assertFalse(maze.exit in plan.candidates)
            }
        }
    }

    @Test
    fun noSafeCellsAndUnreachableExitHaveNoEliteCandidates() {
        val maze = Maze.openGrid(4, 4)
        val difficulty = DifficultyPresets.MEDIUM.copy(npcDirectPathSpawnBuffer = 10)
        val plan = NpcSpawnPlanner.plan(maze, MazeNavigator(maze), difficulty, Random(1L))
        assertTrue(plan.candidates.isNotEmpty())
        assertTrue(plan.eliteEligiblePositions.isEmpty())

        val disconnected = Maze(4, 4, IntArray(16) { Maze.ALL_WALLS }, GridPos(0, 0), GridPos(3, 3))
        val fallback = NpcSpawnPlanner.plan(disconnected, MazeNavigator(disconnected), difficulty, Random(1L))
        assertEquals(14, fallback.candidates.size)
        assertTrue(fallback.eliteEligiblePositions.isEmpty())
    }

    private fun chebyshev(a: GridPos, b: GridPos): Int = maxOf(abs(a.x - b.x), abs(a.y - b.y))
}
