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
    fun generatedMaze_everyCorridorIsAtLeastTwoWide() {
        val maze = MazeGenerator.generate(width = 14, height = 20, seed = 42L)
        assertStartIsTopCorner(maze)
        assertEveryCorridorIsAtLeastTwoWide(maze)
        assertBlockBoundariesAreTwoWide(maze)
    }

    @Test
    fun generatedMaze_startIsAlwaysTopCornerAcrossSeeds() {
        // Assert only the invariant — every generated start is one of the two
        // top corners — so this test is robust to changes in the underlying
        // RNG algorithm. Distribution between the corners is a property of
        // MazeGenerator's implementation, not a contract we test here.
        (1L..64L).forEach { seed ->
            val maze = MazeGenerator.generate(width = 12, height = 16, seed = seed)
            assertStartIsTopCorner(maze)
        }
    }

    @Test
    fun generatedMaze_oddDimensionsAreRoundedUpToEvenAndRemainFullyReachable() {
        // Pass odd width and height; generator must round both up to even and
        // still produce a fully reachable maze with a valid exit path.
        val maze = MazeGenerator.generate(width = 11, height = 17, seed = 42L)
        assertEquals(12, maze.width)
        assertEquals(18, maze.height)
        assertStartIsTopCorner(maze)

        val reachable = bfsReachable(maze, maze.start)
        assertEquals(maze.width * maze.height, reachable.size)

        val navigator = MazeNavigator(maze)
        val path = navigator.bfsPath(maze.start, maze.exit)
        assertTrue(path.isNotEmpty())
        assertEquals(maze.start, path.first())
        assertEquals(maze.exit, path.last())
    }

    @Test
    fun generatedMaze_evenDimensionsArePreserved() {
        val maze = MazeGenerator.generate(width = 12, height = 16, seed = 42L)
        assertEquals(12, maze.width)
        assertEquals(16, maze.height)
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

    private fun assertEveryCorridorIsAtLeastTwoWide(maze: Maze) {
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val origin = GridPos(x, y)
                assertTrue(
                    "Cell $origin is not part of any fully-open 2x2 block",
                    isInsideAnyOpen2x2Block(maze, origin)
                )
            }
        }
    }

    private fun isInsideAnyOpen2x2Block(maze: Maze, cell: GridPos): Boolean {
        // The cell can be at any of the 4 corners of a 2x2 block; check each
        // candidate block by its south-west origin.
        val candidates = listOf(
            GridPos(cell.x, cell.y),
            GridPos(cell.x - 1, cell.y),
            GridPos(cell.x, cell.y - 1),
            GridPos(cell.x - 1, cell.y - 1)
        )
        return candidates.any { isOpen2x2Block(maze, it) }
    }

    private fun isOpen2x2Block(maze: Maze, sw: GridPos): Boolean {
        val se = GridPos(sw.x + 1, sw.y)
        val nw = GridPos(sw.x, sw.y + 1)
        val ne = GridPos(sw.x + 1, sw.y + 1)
        if (!maze.inBounds(sw) || !maze.inBounds(se) || !maze.inBounds(nw) || !maze.inBounds(ne)) return false
        // Block is open iff there is no wall between any pair of adjacent cells
        // inside the 2x2 region.
        return maze.canMove(sw, Direction.EAST) &&
            maze.canMove(nw, Direction.EAST) &&
            maze.canMove(sw, Direction.NORTH) &&
            maze.canMove(se, Direction.NORTH)
    }

    /**
     * Verifies that every passage between two 2x2 logical blocks is itself 2
     * cells wide. The generator lays out 2x2 blocks at even coordinates
     * `(2*lx, 2*ly)`, so block boundaries fall between odd/even column or row
     * pairs. For each such boundary, both parallel edges must be open or both
     * walled — otherwise a "wide" corridor could degenerate into a 1-wide
     * pinch and the 2-wide guarantee would be violated.
     */
    private fun assertBlockBoundariesAreTwoWide(maze: Maze) {
        // Horizontal block boundaries: between x = 2*lx + 1 and x = 2*lx + 2,
        // shared by the two rows y = 2*ly and y = 2*ly + 1.
        var lx = 0
        while (2 * lx + 2 < maze.width) {
            var ly = 0
            while (2 * ly + 1 < maze.height) {
                val low = GridPos(2 * lx + 1, 2 * ly)
                val high = GridPos(2 * lx + 1, 2 * ly + 1)
                val lowOpen = maze.canMove(low, Direction.EAST)
                val highOpen = maze.canMove(high, Direction.EAST)
                assertEquals(
                    "Horizontal boundary between blocks ($lx,$ly)-(${lx + 1},$ly) " +
                        "must be both open or both walled (low=$lowOpen, high=$highOpen).",
                    lowOpen,
                    highOpen
                )
                ly += 1
            }
            lx += 1
        }
        // Vertical block boundaries: between y = 2*ly + 1 and y = 2*ly + 2,
        // shared by the two columns x = 2*lx and x = 2*lx + 1.
        var ly = 0
        while (2 * ly + 2 < maze.height) {
            var lxv = 0
            while (2 * lxv + 1 < maze.width) {
                val left = GridPos(2 * lxv, 2 * ly + 1)
                val right = GridPos(2 * lxv + 1, 2 * ly + 1)
                val leftOpen = maze.canMove(left, Direction.NORTH)
                val rightOpen = maze.canMove(right, Direction.NORTH)
                assertEquals(
                    "Vertical boundary between blocks ($lxv,$ly)-($lxv,${ly + 1}) " +
                        "must be both open or both walled (left=$leftOpen, right=$rightOpen).",
                    leftOpen,
                    rightOpen
                )
                lxv += 1
            }
            ly += 1
        }
    }

    private fun assertStartIsTopCorner(maze: Maze) {
        val topLeft = GridPos(0, 0)
        val topRight = GridPos(maze.width - 1, 0)
        assertTrue(
            "Expected maze.start to be $topLeft or $topRight but was ${maze.start}",
            maze.start == topLeft || maze.start == topRight
        )
    }
}
