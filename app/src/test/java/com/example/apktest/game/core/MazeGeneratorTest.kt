package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MazeGeneratorTest {
    @Test
    fun generatedMaze_isFullyReachableAndHasExitPath() {
        val maze = MazeGenerator.generate(width = 12, height = 16, seed = 42L)
        val navigator = MazeNavigator(maze)

        val reachable = bfsReachable(maze, maze.start)
        assertEquals(maze.width * maze.height, reachable.size)

        val path = navigator.bfsPath(maze.start, maze.exit)
        assertTrue(path.isNotEmpty())
        assertEquals(maze.start, path.first())
        assertEquals(maze.exit, path.last())
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
}
