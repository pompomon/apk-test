package com.example.apktest.game.core

import java.util.PriorityQueue
import kotlin.math.abs

class MazeNavigator(private val maze: Maze) {
    fun neighbors(pos: GridPos): List<GridPos> = maze.neighbors(pos)

    fun bfsPath(start: GridPos, goal: GridPos): List<GridPos> {
        if (start == goal) return listOf(start)
        val queue = ArrayDeque<GridPos>()
        val cameFrom = mutableMapOf<GridPos, GridPos?>()

        queue.add(start)
        cameFrom[start] = null

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current == goal) break

            for (neighbor in neighbors(current)) {
                if (neighbor !in cameFrom) {
                    cameFrom[neighbor] = current
                    queue.add(neighbor)
                }
            }
        }

        return reconstructPath(cameFrom, start, goal)
    }

    fun aStarPath(start: GridPos, goal: GridPos): List<GridPos> {
        if (start == goal) return listOf(start)

        val frontier = PriorityQueue(compareBy<Pair<GridPos, Int>> { it.second })
        val cameFrom = mutableMapOf<GridPos, GridPos?>()
        val cost = mutableMapOf(start to 0)

        frontier.add(start to 0)
        cameFrom[start] = null

        while (frontier.isNotEmpty()) {
            val current = frontier.poll().first
            if (current == goal) break

            for (neighbor in neighbors(current)) {
                val nextCost = cost.getValue(current) + 1
                if (nextCost < cost.getOrDefault(neighbor, Int.MAX_VALUE)) {
                    cost[neighbor] = nextCost
                    val priority = nextCost + manhattanDistance(neighbor, goal)
                    frontier.add(neighbor to priority)
                    cameFrom[neighbor] = current
                }
            }
        }

        return reconstructPath(cameFrom, start, goal)
    }

    private fun reconstructPath(
        cameFrom: Map<GridPos, GridPos?>,
        start: GridPos,
        goal: GridPos
    ): List<GridPos> {
        if (goal !in cameFrom) return emptyList()

        val path = mutableListOf<GridPos>()
        var current: GridPos? = goal
        while (current != null) {
            path.add(current)
            current = cameFrom[current]
        }

        return path.asReversed().takeIf { it.firstOrNull() == start } ?: emptyList()
    }

    private fun manhattanDistance(a: GridPos, b: GridPos): Int = abs(a.x - b.x) + abs(a.y - b.y)
}
