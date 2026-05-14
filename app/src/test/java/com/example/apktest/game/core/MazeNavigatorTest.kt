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

    @Test
    fun bfsPath_blockedSetIsNeverTraversedExceptAsGoal() {
        val maze = Maze.openGrid(3, 3)
        val navigator = MazeNavigator(maze)

        val blocked = setOf(GridPos(1, 0), GridPos(1, 1))
        val path = navigator.bfsPath(GridPos(0, 0), GridPos(2, 2), blocked)

        assertTrue(path.isNotEmpty())
        // Walks around the blocked column, never through (1,0) or (1,1).
        for (cell in path.drop(1).dropLast(1)) {
            assertTrue("path should not traverse blocked cell $cell", cell !in blocked)
        }
    }

    @Test
    fun bfsPath_goalCanBeInsideBlockedSet() {
        val maze = Maze.openGrid(3, 3)
        val navigator = MazeNavigator(maze)

        val goal = GridPos(2, 0)
        val path = navigator.bfsPath(GridPos(0, 0), goal, setOf(goal))

        assertTrue(path.isNotEmpty())
        assertEquals(goal, path.last())
    }

    @Test
    fun bfsPath_returnsEmptyWhenAllRoutesBlocked() {
        // 3x2 corridor with walls between the two rows; only path between
        // (0,0) and (2,0) goes through (1,0). Blocking (1,0) leaves no path.
        val maze = Maze(
            width = 3,
            height = 2,
            cells = IntArray(6) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(2, 0)
        )
        maze.removeWall(GridPos(0, 0), Direction.EAST)
        maze.removeWall(GridPos(1, 0), Direction.EAST)
        val navigator = MazeNavigator(maze)

        val path = navigator.bfsPath(GridPos(0, 0), GridPos(2, 0), setOf(GridPos(1, 0)))

        assertTrue(path.isEmpty())
    }

    @Test
    fun aStarPath_blockedSetIsNeverTraversedExceptAsGoal() {
        val maze = Maze.openGrid(3, 3)
        val navigator = MazeNavigator(maze)

        val blocked = setOf(GridPos(1, 0), GridPos(1, 1))
        val path = navigator.aStarPath(GridPos(0, 0), GridPos(2, 2), blocked)

        assertTrue(path.isNotEmpty())
        for (cell in path.drop(1).dropLast(1)) {
            assertTrue("path should not traverse blocked cell $cell", cell !in blocked)
        }
    }

    @Test
    fun aStarPath_goalCanBeInsideBlockedSet() {
        val maze = Maze.openGrid(3, 3)
        val navigator = MazeNavigator(maze)

        val goal = GridPos(2, 0)
        val path = navigator.aStarPath(GridPos(0, 0), goal, setOf(goal))

        assertTrue(path.isNotEmpty())
        assertEquals(goal, path.last())
    }

    @Test
    fun aStarPath_returnsEmptyWhenAllRoutesBlocked() {
        val maze = Maze(
            width = 3,
            height = 2,
            cells = IntArray(6) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(2, 0)
        )
        maze.removeWall(GridPos(0, 0), Direction.EAST)
        maze.removeWall(GridPos(1, 0), Direction.EAST)
        val navigator = MazeNavigator(maze)

        val path = navigator.aStarPath(GridPos(0, 0), GridPos(2, 0), setOf(GridPos(1, 0)))

        assertTrue(path.isEmpty())
    }
}
