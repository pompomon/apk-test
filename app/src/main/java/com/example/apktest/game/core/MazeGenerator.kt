package com.example.apktest.game.core

import kotlin.random.Random

object MazeGenerator {
    fun generate(width: Int, height: Int, seed: Long): Maze {
        val random = Random(seed)
        val cells = IntArray(width * height) { Maze.ALL_WALLS }

        val start = GridPos(0, 0)
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
            val maze = Maze(width, height, cells, start, start)
            maze.removeWall(current, direction)
            visited[next.y][next.x] = true
            stack.add(next)
        }

        val tempMaze = Maze(width, height, cells, start, start)
        val farthest = farthestNode(tempMaze, start)
        return Maze(width, height, cells, start, farthest)
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
}
