package com.example.apktest.game.core

import kotlin.math.abs
import kotlin.random.Random

/**
 * Shared initial placement for the engine and Adventure's assignment lock.
 * Uses exactly the legacy shuffle so inspecting elite eligibility cannot change
 * NPC placement or consume additional numbers from the engine's spawn RNG.
 */
internal object NpcSpawnPlanner {
    data class Plan(
        val candidates: List<GridPos>,
        val eliteEligiblePositions: Set<GridPos>
    )

    fun plan(maze: Maze, navigator: MazeNavigator, difficulty: DifficultyPreset, random: Random): Plan {
        val reserved = setOf(maze.start, maze.exit)
        val shuffled = ArrayList<GridPos>(maze.width * maze.height - reserved.size)
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) {
                val pos = GridPos(x, y)
                if (pos !in reserved) shuffled += pos
            }
        }
        shuffled.shuffle(random)
        val directPath = navigator.bfsPath(maze.start, maze.exit)
        // No elites on malformed/disconnected mazes; ordinary spawning keeps its fallback.
        if (directPath.isEmpty()) return Plan(shuffled, emptySet())

        val bufferedPathCells = mutableSetOf<GridPos>()
        val buffer = difficulty.npcDirectPathSpawnBuffer
        directPath.forEach { cell ->
            for (dy in -buffer..buffer) {
                for (dx in -buffer..buffer) {
                    val pos = GridPos(cell.x + dx, cell.y + dy)
                    if (maze.inBounds(pos)) bufferedPathCells += pos
                }
            }
        }
        val preferred = shuffled.filter { it !in bufferedPathCells }
        val preferredSet = preferred.toSet()
        val candidates = if (preferred.size == shuffled.size) preferred else
            preferred + shuffled.filter { it !in preferredSet }
        val eligible = preferred.filterTo(mutableSetOf()) {
            maxOf(abs(it.x - maze.start.x), abs(it.y - maze.start.y)) > 1
        }
        return Plan(candidates, eligible)
    }
}
