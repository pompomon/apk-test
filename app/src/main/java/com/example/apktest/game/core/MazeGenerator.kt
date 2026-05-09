package com.example.apktest.game.core

import kotlin.random.Random

object MazeGenerator {
    fun generate(width: Int, height: Int, seed: Long): Maze {
        val random = Random(seed)
        val cells = IntArray(width * height) { Maze.ALL_WALLS }

        val start = GridPos(0, 0)
        val maze = Maze(width, height, cells, start, start)
        val visited = Array(height) { BooleanArray(width) }
        val stack = ArrayDeque<GridPos>()

        stack.add(start)
        visited[start.y][start.x] = true

        while (stack.isNotEmpty()) {
            val current = stack.last()
            val candidates = Direction.entries
                .map { it to current.moved(it) }
                .filter { (_, pos) -> pos.x in 0 until width && pos.y in 0 until height && !visited[pos.y][pos.x] }

            if (candidates.isEmpty()) {
                stack.removeLast()
                continue
            }

            val (direction, next) = candidates[random.nextInt(candidates.size)]
            maze.removeWall(current, direction)
            visited[next.y][next.x] = true
            stack.add(next)
        }

        applyWideCorridorWidening(maze, random)
        val farthest = farthestNode(maze, start)
        return Maze(width, height, cells, start, farthest)
    }

    private fun applyWideCorridorWidening(maze: Maze, random: Random) {
        val area = maze.width * maze.height
        if (maze.width < MIN_WIDENING_DIMENSION || maze.height < MIN_WIDENING_DIMENSION || area < MIN_WIDENING_AREA) {
            return
        }

        val targetSegments = (area * WIDENING_RATIO).toInt()
            .coerceAtLeast(MIN_WIDE_SEGMENTS)
            .coerceAtMost(MAX_WIDE_SEGMENTS)
        val maxAttempts = targetSegments * WIDENING_ATTEMPT_FACTOR

        var createdSegments = 0
        repeat(maxAttempts) {
            if (createdSegments >= targetSegments) return

            val origin = GridPos(
                x = random.nextInt(maze.width),
                y = random.nextInt(maze.height)
            )
            val primaryDirection = Direction.entries
                .shuffled(random)
                .firstOrNull { maze.canMove(origin, it) }
                ?: return@repeat
            val forward = origin.moved(primaryDirection)

            val sideOptions = if (primaryDirection == Direction.NORTH || primaryDirection == Direction.SOUTH) {
                listOf(Direction.EAST, Direction.WEST)
            } else {
                listOf(Direction.NORTH, Direction.SOUTH)
            }
            val sideDirection = sideOptions[random.nextInt(sideOptions.size)]
            val sideOrigin = origin.moved(sideDirection)
            val sideForward = forward.moved(sideDirection)

            if (!maze.inBounds(sideOrigin) || !maze.inBounds(sideForward)) return@repeat

            val widenedBlock = setOf(origin, forward, sideOrigin, sideForward)
            if (widenedBlock.any { it == maze.start || it == maze.exit }) return@repeat
            if (widenedBlock.any { manhattanDistance(it, maze.start) <= 1 || manhattanDistance(it, maze.exit) <= 1 }) {
                return@repeat
            }

            val openingsBefore = countOpenings(maze, origin, forward, sideOrigin, primaryDirection, sideDirection)
            maze.removeWall(origin, sideDirection)
            maze.removeWall(forward, sideDirection)
            maze.removeWall(sideOrigin, primaryDirection)
            val openingsAfter = countOpenings(maze, origin, forward, sideOrigin, primaryDirection, sideDirection)

            if (openingsAfter > openingsBefore) {
                createdSegments += 1
            }
        }
    }

    private fun countOpenings(
        maze: Maze,
        origin: GridPos,
        forward: GridPos,
        sideOrigin: GridPos,
        primaryDirection: Direction,
        sideDirection: Direction
    ): Int {
        var openings = 0
        if (!maze.hasWall(origin, sideDirection)) openings += 1
        if (!maze.hasWall(forward, sideDirection)) openings += 1
        if (!maze.hasWall(sideOrigin, primaryDirection)) openings += 1
        return openings
    }

    private fun manhattanDistance(a: GridPos, b: GridPos): Int = kotlin.math.abs(a.x - b.x) + kotlin.math.abs(a.y - b.y)

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

    private const val MIN_WIDENING_DIMENSION = 4
    private const val MIN_WIDENING_AREA = 36
    private const val MIN_WIDE_SEGMENTS = 1
    private const val MAX_WIDE_SEGMENTS = 24
    private const val WIDENING_ATTEMPT_FACTOR = 12
    private const val WIDENING_RATIO = 0.03f
}
