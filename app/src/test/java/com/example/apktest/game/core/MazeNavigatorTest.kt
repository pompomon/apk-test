package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MazeNavigatorTest {
    @Test
    fun bfsAndAStar_findValidPathsOnOpenGrid() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)

        val bfs = navigator.bfsPath(GridPos(0, 0), GridPos(4, 4))
        val astar = navigator.aStarPath(GridPos(0, 0), GridPos(4, 4))

        assertTrue(bfs.isNotEmpty())
        assertTrue(astar.isNotEmpty())
        assertEquals(9, bfs.size)
        assertEquals(9, astar.size)
        assertEquals(GridPos(0, 0), bfs.first())
        assertEquals(GridPos(4, 4), bfs.last())
    }
}
