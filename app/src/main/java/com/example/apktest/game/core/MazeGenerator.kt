package com.example.apktest.game.core

import kotlin.random.Random

/**
 * Generates mazes where every walkable region is at least 2 cells wide.
 *
 * Approach: a "logical" maze is generated at half resolution using a randomized
 * depth-first backtracker, and then each logical cell is expanded into a 2x2
 * block in the final maze. Logical passages between neighbors are mapped to two
 * parallel openings in the final maze, so corridors are always 2 cells wide.
 *
 * Contract: the returned [Maze] always has even `width` and `height` (the input
 * dimensions are rounded up to the next even number if necessary). `maze.start`
 * is either `(0, 0)` or `(width - 1, 0)` — the top-left or top-right corner of
 * the chosen logical start block.
 */
object MazeGenerator {
    fun generate(width: Int, height: Int, seed: Long): Maze {
        val finalWidth = roundUpToEven(width)
        val finalHeight = roundUpToEven(height)
        require(finalWidth >= MIN_FINAL_DIMENSION && finalHeight >= MIN_FINAL_DIMENSION) {
            "Maze must be at least ${MIN_FINAL_DIMENSION}x${MIN_FINAL_DIMENSION} (after rounding up to even)."
        }

        val random = Random(seed)
        val logicalWidth = finalWidth / 2
        val logicalHeight = finalHeight / 2

        // Build the logical (half-resolution) perfect maze with a standard
        // randomized depth-first backtracker. The start is chosen between the
        // top-left and top-right corners so the final maze's start lands on a
        // top corner as well — preserving the existing top-corner contract.
        val logicalCells = IntArray(logicalWidth * logicalHeight) { Maze.ALL_WALLS }
        val logicalStart = if (random.nextBoolean()) GridPos(0, 0) else GridPos(logicalWidth - 1, 0)
        val logicalMaze = Maze(logicalWidth, logicalHeight, logicalCells, logicalStart, logicalStart)
        carveLogicalMaze(logicalMaze, random, logicalStart)
        val logicalExit = farthestNode(logicalMaze, logicalStart)

        // Map the logical maze to the final maze: each logical cell becomes a
        // 2x2 fully-open block, and each logical passage becomes a 2-wide
        // opening between blocks.
        val finalCells = IntArray(finalWidth * finalHeight) { Maze.ALL_WALLS }
        val finalStart = if (logicalStart.x == 0) GridPos(0, 0) else GridPos(finalWidth - 1, 0)
        val finalExit = GridPos(logicalExit.x * 2, logicalExit.y * 2)
        val finalMaze = Maze(finalWidth, finalHeight, finalCells, finalStart, finalExit)
        expandLogicalMazeIntoFinal(logicalMaze, finalMaze)
        return finalMaze
    }

    private fun carveLogicalMaze(maze: Maze, random: Random, start: GridPos) {
        val visited = Array(maze.height) { BooleanArray(maze.width) }
        val stack = ArrayDeque<GridPos>()
        stack.add(start)
        visited[start.y][start.x] = true

        while (stack.isNotEmpty()) {
            val current = stack.last()
            val candidates = Direction.entries
                .map { it to current.moved(it) }
                .filter { (_, pos) ->
                    pos.x in 0 until maze.width &&
                        pos.y in 0 until maze.height &&
                        !visited[pos.y][pos.x]
                }

            if (candidates.isEmpty()) {
                stack.removeLast()
                continue
            }

            val (direction, next) = candidates[random.nextInt(candidates.size)]
            maze.removeWall(current, direction)
            visited[next.y][next.x] = true
            stack.add(next)
        }
    }

    private fun expandLogicalMazeIntoFinal(logical: Maze, finalMaze: Maze) {
        for (ly in 0 until logical.height) {
            for (lx in 0 until logical.width) {
                val baseX = lx * 2
                val baseY = ly * 2
                val sw = GridPos(baseX, baseY)
                val se = GridPos(baseX + 1, baseY)
                val nw = GridPos(baseX, baseY + 1)

                // Open the interior of the 2x2 block: remove the east wall on
                // both rows and the north wall on both columns. removeWall is
                // idempotent so duplicates between neighboring blocks are safe.
                finalMaze.removeWall(sw, Direction.EAST)
                finalMaze.removeWall(nw, Direction.EAST)
                finalMaze.removeWall(sw, Direction.NORTH)
                finalMaze.removeWall(se, Direction.NORTH)

                // Mirror logical passages into 2-wide openings between blocks.
                // Only mirror EAST and NORTH here; the matching neighbor block
                // covers the opposite direction when iterated, but the helper
                // removes walls symmetrically anyway.
                val logicalPos = GridPos(lx, ly)
                if (logical.canMove(logicalPos, Direction.EAST)) {
                    finalMaze.removeWall(se, Direction.EAST)
                    finalMaze.removeWall(GridPos(baseX + 1, baseY + 1), Direction.EAST)
                }
                if (logical.canMove(logicalPos, Direction.NORTH)) {
                    finalMaze.removeWall(nw, Direction.NORTH)
                    finalMaze.removeWall(GridPos(baseX + 1, baseY + 1), Direction.NORTH)
                }
            }
        }
    }

    private fun farthestNode(maze: Maze, origin: GridPos): GridPos {
        val queue = ArrayDeque<GridPos>()
        val distance = mutableMapOf(origin to 0)
        queue.add(origin)

        var farthest = origin
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentDistance = distance.getValue(current)
            if (currentDistance > distance.getValue(farthest)) {
                farthest = current
            }
            for (neighbor in maze.neighbors(current)) {
                if (neighbor !in distance) {
                    distance[neighbor] = currentDistance + 1
                    queue.add(neighbor)
                }
            }
        }
        return farthest
    }

    private fun roundUpToEven(value: Int): Int = if (value % 2 == 0) value else value + 1

    private const val MIN_FINAL_DIMENSION = 4
}
