package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Test

class MazeTest {
    @Test
    fun removeWall_bumpsRevisionWhenWallExisted() {
        val maze = Maze(
            width = 2,
            height = 2,
            cells = IntArray(4) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(1, 1)
        )
        val before = maze.revision

        maze.removeWall(GridPos(0, 0), Direction.EAST)

        assertEquals(before + 1, maze.revision)
    }

    @Test
    fun removeWall_isNoOpWhenWallAlreadyRemoved() {
        val maze = Maze(
            width = 2,
            height = 2,
            cells = IntArray(4) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(1, 1)
        )
        maze.removeWall(GridPos(0, 0), Direction.EAST)
        val after = maze.revision

        maze.removeWall(GridPos(0, 0), Direction.EAST)

        assertEquals(after, maze.revision)
    }

    @Test
    fun removeWall_isNoOpWhenNeighbourOutOfBounds() {
        val maze = Maze(
            width = 2,
            height = 2,
            cells = IntArray(4) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(1, 1)
        )
        val before = maze.revision

        maze.removeWall(GridPos(0, 0), Direction.WEST)

        assertEquals(before, maze.revision)
    }
}
