package com.example.apktest.game.core

import kotlin.math.abs
import kotlin.random.Random

enum class PlayerPolicyType(val label: String) {
    MANUAL("Manual"),
    RANDOM_MEMORY("Random+Memory"),
    WALL_LEFT("Wall Left"),
    WALL_RIGHT("Wall Right"),
    BFS_EXIT("BFS Exit"),
    ASTAR_EXIT("A* Exit")
}

enum class NpcPolicyType(val label: String) {
    DIRECT_CHASE("Direct Chase"),
    PREDICTIVE_CHASE("Predictive"),
    PATROL_GUARD("Patrol/Guard")
}

data class PlayerPolicyContext(
    val maze: Maze,
    val navigator: MazeNavigator,
    val player: Player,
    val exit: GridPos,
    val npcs: List<Npc>
)

data class NpcPolicyContext(
    val maze: Maze,
    val navigator: MazeNavigator,
    val player: Player,
    val visionRange: Int,
    val playerVisible: Boolean,
    val npcsFrozen: Boolean
)

interface PlayerPolicy {
    fun nextMove(context: PlayerPolicyContext): Direction?
    fun reset() {}
}

interface NpcPolicy {
    fun nextMove(npc: Npc, context: NpcPolicyContext): Direction?
    fun reset() {}
}

class ManualPolicy : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? = null
}

class RandomWalkMemoryPolicy(private val random: Random = Random.Default) : PlayerPolicy {
    private val visitCounts = mutableMapOf<GridPos, Int>()

    override fun nextMove(context: PlayerPolicyContext): Direction? {
        visitCounts[context.player.position] = visitCounts.getOrDefault(context.player.position, 0) + 1

        val options = Direction.entries.filter { context.maze.canMove(context.player.position, it) }
        if (options.isEmpty()) return null

        val scored = options.groupBy { direction ->
            val destination = context.player.position.moved(direction)
            visitCounts.getOrDefault(destination, 0)
        }
        val minVisits = requireNotNull(scored.keys.minOrNull())
        val bestOptions = scored[minVisits].orEmpty()
        return bestOptions[random.nextInt(bestOptions.size)]
    }

    override fun reset() {
        visitCounts.clear()
    }
}

class WallFollowerPolicy(private val leftHand: Boolean) : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        val facing = context.player.facing
        val ordered = if (leftHand) {
            listOf(facing.left(), facing, facing.right(), facing.opposite())
        } else {
            listOf(facing.right(), facing, facing.left(), facing.opposite())
        }
        return ordered.firstOrNull { context.maze.canMove(context.player.position, it) }
    }
}

class BfsExitPolicy : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        val path = context.navigator.bfsPath(context.player.position, context.exit)
        return nextDirection(context.player.position, path)
    }
}

class AStarExitPolicy : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        val path = context.navigator.aStarPath(context.player.position, context.exit)
        return nextDirection(context.player.position, path)
    }
}

class DirectChasePolicy(private val random: Random = Random.Default) : NpcPolicy {
    override fun nextMove(npc: Npc, context: NpcPolicyContext): Direction? {
        if (context.npcsFrozen) return null
        if (!context.playerVisible) return wanderMove(npc, context.maze, random)
        val path = context.navigator.bfsPath(npc.position, context.player.position)
        return nextDirection(npc.position, path)
    }
}

class PredictiveChasePolicy(private val random: Random = Random.Default) : NpcPolicy {
    override fun nextMove(npc: Npc, context: NpcPolicyContext): Direction? {
        if (context.npcsFrozen) return null
        if (!context.playerVisible) return wanderMove(npc, context.maze, random)
        var projected = context.player.position
        repeat(PREDICTION_STEPS) {
            val next = projected.moved(context.player.facing)
            if (context.maze.inBounds(next) && context.maze.canMove(projected, context.player.facing)) {
                projected = next
            }
        }

        val path = context.navigator.aStarPath(npc.position, projected)
        return nextDirection(npc.position, path)
            ?: nextDirection(npc.position, context.navigator.bfsPath(npc.position, context.player.position))
    }

    companion object {
        private const val PREDICTION_STEPS = 2
    }
}

class PatrolGuardPolicy : NpcPolicy {
    override fun nextMove(npc: Npc, context: NpcPolicyContext): Direction? {
        if (context.npcsFrozen) return null
        val playerDistance = manhattanDistance(npc.position, context.player.position)

        if (context.playerVisible && playerDistance <= context.visionRange) {
            npc.state = NpcState.CHASE
            npc.lastKnownPlayerPos = context.player.position
            npc.searchTicksRemaining = DEFAULT_SEARCH_TICKS
        }

        return when (npc.state) {
            NpcState.CHASE -> {
                val target = npc.lastKnownPlayerPos ?: context.player.position
                val path = context.navigator.aStarPath(npc.position, target)
                if (path.isEmpty() || npc.position == target) {
                    npc.state = NpcState.SEARCH
                    npc.searchTicksRemaining = DEFAULT_SEARCH_TICKS
                    null
                } else {
                    nextDirection(npc.position, path)
                }
            }
            NpcState.SEARCH -> {
                npc.searchTicksRemaining -= 1
                if (npc.searchTicksRemaining <= 0) {
                    npc.state = NpcState.PATROL
                    npc.lastKnownPlayerPos = null
                    return patrolMove(npc, context)
                }
                val target = npc.lastKnownPlayerPos
                if (target == null) {
                    npc.state = NpcState.PATROL
                    patrolMove(npc, context)
                } else {
                    nextDirection(npc.position, context.navigator.bfsPath(npc.position, target))
                }
            }
            NpcState.PATROL -> patrolMove(npc, context)
        }
    }

    private fun patrolMove(npc: Npc, context: NpcPolicyContext): Direction? {
        if (npc.patrolRoute.isEmpty()) {
            return Direction.entries.firstOrNull { context.maze.canMove(npc.position, it) }
        }

        val target = npc.patrolRoute[npc.patrolIndex % npc.patrolRoute.size]
        if (npc.position == target) {
            npc.patrolIndex = (npc.patrolIndex + 1) % npc.patrolRoute.size
        }

        val nextTarget = npc.patrolRoute[npc.patrolIndex % npc.patrolRoute.size]
        return nextDirection(npc.position, context.navigator.bfsPath(npc.position, nextTarget))
    }

    companion object {
        private const val DEFAULT_SEARCH_TICKS = 5
    }
}

object PolicyFactory {
    fun player(type: PlayerPolicyType): PlayerPolicy = when (type) {
        PlayerPolicyType.MANUAL -> ManualPolicy()
        PlayerPolicyType.RANDOM_MEMORY -> RandomWalkMemoryPolicy()
        PlayerPolicyType.WALL_LEFT -> WallFollowerPolicy(leftHand = true)
        PlayerPolicyType.WALL_RIGHT -> WallFollowerPolicy(leftHand = false)
        PlayerPolicyType.BFS_EXIT -> BfsExitPolicy()
        PlayerPolicyType.ASTAR_EXIT -> AStarExitPolicy()
    }

    /**
     * Builds an [NpcPolicy]. Callers should pass a deterministic [random] so
     * NPC behaviour under INVISIBILITY (which falls back to a random wander)
     * remains reproducible for a given engine seed; the default [Random.Default]
     * is only intended for ad-hoc construction in tests/tools.
     */
    fun npc(type: NpcPolicyType, random: Random = Random.Default): NpcPolicy = when (type) {
        NpcPolicyType.DIRECT_CHASE -> DirectChasePolicy(random)
        NpcPolicyType.PREDICTIVE_CHASE -> PredictiveChasePolicy(random)
        NpcPolicyType.PATROL_GUARD -> PatrolGuardPolicy()
    }
}

private fun nextDirection(from: GridPos, path: List<GridPos>): Direction? {
    if (path.size < 2) return null
    val step = path[1]
    return Direction.fromDelta(step.x - from.x, step.y - from.y)
}

private fun manhattanDistance(a: GridPos, b: GridPos): Int = abs(a.x - b.x) + abs(a.y - b.y)

/**
 * Picks a random walkable neighbour direction for [npc], avoiding an immediate
 * reversal (`npc.facing.opposite()`) when at least one other walkable
 * direction exists. Used by chase policies when the player is invisible so
 * NPCs wander instead of standing still (FREEZE remains the only effect that
 *  truly stops NPCs). Implementation is allocation-free: it iterates
 * [Direction.entries] once into a shared single-thread scratch array
 * (the engine update loop is strictly single-threaded), suitable for the
 * per-tick NPC loop.
 */
private fun wanderMove(npc: Npc, maze: Maze, random: Random): Direction? {
    val all = Direction.entries
    val pool = WANDER_POOL_SCRATCH
    val reverse = npc.facing.opposite()
    var total = 0
    var nonReverse = 0
    for (i in all.indices) {
        val direction = all[i]
        if (!maze.canMove(npc.position, direction)) continue
        pool[total] = direction
        total++
        if (direction != reverse) nonReverse++
    }
    if (total == 0) return null
    return if (nonReverse > 0) {
        // Pick uniformly from the non-reversal subset by rejection-sampling
        // within the small (<=4) pool — still allocation-free.
        var pick = random.nextInt(nonReverse)
        for (i in 0 until total) {
            val d = pool[i]!!
            if (d == reverse) continue
            if (pick == 0) return d
            pick--
        }
        pool[0]!! // unreachable but keeps the compiler happy
    } else {
        pool[random.nextInt(total)]!!
    }
}

// Per-thread scratch pool; the NPC update loop runs on the GL thread and is
// strictly single-threaded, so a shared array is safe and avoids a per-call
// allocation. Sized to the maximum possible walkable neighbours (4).
private val WANDER_POOL_SCRATCH: Array<Direction?> = arrayOfNulls(4)
