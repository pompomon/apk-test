package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MazeGeneratorTest {
    @Test
    fun generatedMaze_isFullyReachableAndHasExitPath() {
        val maze = MazeGenerator.generate(width = 12, height = 16, seed = 42L)
        val navigator = MazeNavigator(maze)
        assertStartIsTopCorner(maze)

        val reachable = bfsReachable(maze, maze.start)
        assertEquals(maze.width * maze.height, reachable.size)

        val path = navigator.bfsPath(maze.start, maze.exit)
        assertTrue(path.isNotEmpty())
        assertEquals(maze.start, path.first())
        assertEquals(maze.exit, path.last())
    }

    @Test
    fun generatedMaze_sameSeedProducesSameLayout() {
        val first = MazeGenerator.generate(width = 12, height = 16, seed = 42L)
        val second = MazeGenerator.generate(width = 12, height = 16, seed = 42L)
        assertStartIsTopCorner(first)
        assertStartIsTopCorner(second)

        assertTrue(first.copyCells().contentEquals(second.copyCells()))
        assertEquals(first.start, second.start)
        assertEquals(first.exit, second.exit)
    }

    @Test
    fun generatedMaze_containsWideCorridorSegments() {
        val maze = MazeGenerator.generate(width = 14, height = 20, seed = 42L)
        assertStartIsTopCorner(maze)
        assertTrue("Expected at least one widened corridor segment", hasWideCorridorSegment(maze))
    }

    @Test
    fun generatedMaze_startAlternatesBetweenTopCornersAcrossSeeds() {
        val starts = (1L..64L)
            .map { seed -> MazeGenerator.generate(width = 12, height = 16, seed = seed).start }
            .toSet()
        assertTrue(starts.contains(GridPos(0, 0)))
        assertTrue(starts.contains(GridPos(11, 0)))
    }

    private fun bfsReachable(maze: Maze, start: GridPos): Set<GridPos> {
        val visited = mutableSetOf<GridPos>()
        val queue = ArrayDeque<GridPos>()
        queue.add(start)
        visited.add(start)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            for (next in maze.neighbors(current)) {
                if (visited.add(next)) {
                    queue.add(next)
                }
            }
        }

        return visited
    }

    private fun hasWideCorridorSegment(maze: Maze): Boolean {
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val origin = GridPos(x, y)
                if (hasWideSegmentFrom(maze, origin, Direction.EAST)) return true
                if (hasWideSegmentFrom(maze, origin, Direction.NORTH)) return true
            }
        }
        return false
    }

    private fun hasWideSegmentFrom(maze: Maze, origin: GridPos, primaryDirection: Direction): Boolean {
        if (!maze.canMove(origin, primaryDirection)) return false
        val forward = origin.moved(primaryDirection)
        val sideOptions = if (primaryDirection == Direction.NORTH || primaryDirection == Direction.SOUTH) {
            listOf(Direction.EAST, Direction.WEST)
        } else {
            listOf(Direction.NORTH, Direction.SOUTH)
        }

        return sideOptions.any { sideDirection ->
            val sideOrigin = origin.moved(sideDirection)
            val sideForward = forward.moved(sideDirection)
            maze.inBounds(sideOrigin) &&
                maze.inBounds(sideForward) &&
                maze.canMove(origin, sideDirection) &&
                maze.canMove(forward, sideDirection) &&
                maze.canMove(sideOrigin, primaryDirection)
        }
    }

    private fun assertStartIsTopCorner(maze: Maze) {
        val topLeft = GridPos(0, 0)
        val topRight = GridPos(maze.width - 1, 0)
        assertTrue(maze.start == topLeft || maze.start == topRight)
    }
}
