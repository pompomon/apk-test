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
    val npcs: List<Npc>,
    /**
     * True when the [PowerUpType.INVISIBILITY] effect is active for the
     * player. NPCs cannot end the game on contact while this is true, so
     * automatic-avoidance wrappers treat the maze as if no NPCs were present.
     */
    val playerInvisibleToNpcs: Boolean = false,
    /**
     * True when the [PowerUpType.FREEZE] effect is active. NPCs do not move
     * this tick, so only their *current* cells are dangerous; the player can
     * safely step onto any walkable neighbour of an NPC.
     */
    val npcsFrozen: Boolean = false
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

/**
 * Optional extension implemented by non-manual player policies that can
 * enumerate their move candidates in preference order. Used by
 * [AvoidanceWrapperPolicy] to pick the highest-ranked candidate that does
 * not step into an NPC. Implementations must update any per-tick internal
 * state (e.g. visit counters) the same way [PlayerPolicy.nextMove] would,
 * since the wrapper invokes either [rankedMoves] *or* [PlayerPolicy.nextMove]
 * — never both — for a given tick.
 */
internal interface RankedPlayerPolicy : PlayerPolicy {
    fun rankedMoves(context: PlayerPolicyContext): List<Direction>
}

interface NpcPolicy {
    fun nextMove(npc: Npc, context: NpcPolicyContext): Direction?
    fun reset() {}
}

class ManualPolicy : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? = null
}

class RandomWalkMemoryPolicy(private val random: Random = Random.Default) : RankedPlayerPolicy {
    private val visitCounts = mutableMapOf<GridPos, Int>()

    override fun nextMove(context: PlayerPolicyContext): Direction? =
        rankedMoves(context).firstOrNull()

    override fun rankedMoves(context: PlayerPolicyContext): List<Direction> {
        visitCounts[context.player.position] = visitCounts.getOrDefault(context.player.position, 0) + 1

        val options = Direction.entries.filter { context.maze.canMove(context.player.position, it) }
        if (options.isEmpty()) return emptyList()

        // Group walkable directions by destination visit count and emit the
        // groups in ascending order. Each group is shuffled with the policy's
        // seeded RNG so behaviour is deterministic for a given seed and ties
        // within a group are broken uniformly (matching the previous
        // single-pick behaviour of [nextMove]).
        val grouped = options.groupBy { direction ->
            val destination = context.player.position.moved(direction)
            visitCounts.getOrDefault(destination, 0)
        }
        return grouped.entries
            .sortedBy { it.key }
            .flatMap { it.value.shuffled(random) }
    }

    override fun reset() {
        visitCounts.clear()
    }
}

class WallFollowerPolicy(private val leftHand: Boolean) : RankedPlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? =
        rankedMoves(context).firstOrNull()

    override fun rankedMoves(context: PlayerPolicyContext): List<Direction> {
        val facing = context.player.facing
        val ordered = if (leftHand) {
            listOf(facing.left(), facing, facing.right(), facing.opposite())
        } else {
            listOf(facing.right(), facing, facing.left(), facing.opposite())
        }
        return ordered.filter { context.maze.canMove(context.player.position, it) }
    }
}

class BfsExitPolicy : RankedPlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        val path = context.navigator.bfsPath(context.player.position, context.exit)
        return nextDirection(context.player.position, path)
    }

    override fun rankedMoves(context: PlayerPolicyContext): List<Direction> =
        rankedExitMoves(context) { start, goal -> context.navigator.bfsPath(start, goal) }
}

class AStarExitPolicy : RankedPlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        val path = context.navigator.aStarPath(context.player.position, context.exit)
        return nextDirection(context.player.position, path)
    }

    override fun rankedMoves(context: PlayerPolicyContext): List<Direction> =
        rankedExitMoves(context) { start, goal -> context.navigator.aStarPath(start, goal) }
}

/**
 * Decorates an [inner] [PlayerPolicy] with automatic single-step NPC
 * avoidance: directions that would step into a cell currently occupied by
 * an NPC are filtered out, with a softer preference against directions
 * adjacent to an NPC (cells the NPC could move into next NPC tick).
 *
 * Behaviour:
 * - When [PlayerPolicyContext.playerInvisibleToNpcs] is true, the wrapper is
 *   a pass-through (NPC contact does not lose the game under INVISIBILITY).
 * - When [PlayerPolicyContext.npcsFrozen] is true only NPC current cells are
 *   treated as deadly; the player can safely step onto an NPC's neighbour.
 * - For [BfsExitPolicy] / [AStarExitPolicy] the wrapper first asks the
 *   navigator for an avoidance-aware path that excludes NPC cells, and
 *   prefers that step when one exists.
 * - Otherwise the wrapper picks the highest-ranked safe candidate from
 *   [RankedPlayerPolicy.rankedMoves] (preserving wall-follower priority and
 *   random-memory visit-count ordering). If no candidate avoids deadly
 *   cells the wrapper returns `null` (skip the tick) — unless the player
 *   is *already* in a next-step-danger cell, in which case standing still
 *   provides no safety benefit and the inner policy's choice is returned.
 */
class AvoidanceWrapperPolicy(internal val inner: PlayerPolicy) : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        if (context.playerInvisibleToNpcs || context.npcs.isEmpty()) {
            return inner.nextMove(context)
        }

        val deadly = computeDeadlyCells(context)
        val risky = computeRiskyCells(context, deadly)

        if (deadly.isEmpty() && risky.isEmpty()) {
            return inner.nextMove(context)
        }

        // For exit-finding policies, try an avoidance-aware path first so the
        // single-tick choice still routes optimally toward the exit while
        // skirting NPC cells. If the avoidance-aware path is blocked we fall
        // through to the ranked-candidate logic below.
        val pathStep = avoidanceAwarePathStep(context, deadly)
        if (pathStep != null) {
            val dest = context.player.position.moved(pathStep)
            if (dest !in deadly) return pathStep
        }

        val ranked = (inner as? RankedPlayerPolicy)?.rankedMoves(context)
            ?: listOfNotNull(inner.nextMove(context))

        if (ranked.isEmpty()) return null

        val from = context.player.position
        val safe = ranked.firstOrNull { from.moved(it) !in deadly && from.moved(it) !in risky }
        if (safe != null) return safe

        val nonDeadly = ranked.firstOrNull { from.moved(it) !in deadly }
        if (nonDeadly != null) return nonDeadly

        // Every walkable direction enters a deadly cell. Standing still is
        // strictly safer than stepping into an NPC, *unless* an NPC could
        // reach the player's current cell next tick anyway — in which case
        // there is nothing to gain by waiting and we let the inner policy
        // dictate the move.
        return if (from in risky) ranked.first() else null
    }

    override fun reset() {
        inner.reset()
    }

    private fun avoidanceAwarePathStep(
        context: PlayerPolicyContext,
        deadly: Set<GridPos>
    ): Direction? {
        val from = context.player.position
        val path = when (inner) {
            is BfsExitPolicy -> context.navigator.bfsPath(from, context.exit, deadly)
            is AStarExitPolicy -> context.navigator.aStarPath(from, context.exit, deadly)
            else -> return null
        }
        return nextDirection(from, path)
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
        PlayerPolicyType.RANDOM_MEMORY -> AvoidanceWrapperPolicy(RandomWalkMemoryPolicy())
        PlayerPolicyType.WALL_LEFT -> AvoidanceWrapperPolicy(WallFollowerPolicy(leftHand = true))
        PlayerPolicyType.WALL_RIGHT -> AvoidanceWrapperPolicy(WallFollowerPolicy(leftHand = false))
        PlayerPolicyType.BFS_EXIT -> AvoidanceWrapperPolicy(BfsExitPolicy())
        PlayerPolicyType.ASTAR_EXIT -> AvoidanceWrapperPolicy(AStarExitPolicy())
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
 * Builds a preference-ordered list of walkable directions for an exit-finding
 * policy. The path's own next step is first; remaining walkable directions
 * follow, sorted by manhattan distance from the resulting cell to the exit
 * so the [AvoidanceWrapperPolicy] still tends toward the exit when the
 * primary path step is unsafe.
 */
private fun rankedExitMoves(
    context: PlayerPolicyContext,
    pathFinder: (GridPos, GridPos) -> List<GridPos>
): List<Direction> {
    val from = context.player.position
    val pathStep = nextDirection(from, pathFinder(from, context.exit))
    val walkable = Direction.entries.filter { context.maze.canMove(from, it) }
    if (walkable.isEmpty()) return emptyList()
    val others = walkable
        .filter { it != pathStep }
        .sortedBy { manhattanDistance(from.moved(it), context.exit) }
    return if (pathStep != null && pathStep in walkable) listOf(pathStep) + others else others
}

/**
 * Cells the player must not enter this tick: the current position of every
 * NPC. Returns an empty set when the player is invisible to NPCs (no
 * collision can end the game).
 */
private fun computeDeadlyCells(context: PlayerPolicyContext): Set<GridPos> {
    if (context.playerInvisibleToNpcs || context.npcs.isEmpty()) return emptySet()
    val cells = HashSet<GridPos>(context.npcs.size)
    for (npc in context.npcs) cells.add(npc.position)
    return cells
}

/**
 * Cells the player should avoid stepping into because an NPC could move
 * onto them on the next NPC tick. Computed as a one-step horizon over each
 * NPC's walkable neighbours; we deliberately do not model individual NPC
 * policies. Cells already in [deadly] are excluded so the wrapper can
 * differentiate "currently occupied" from "potentially occupied next tick".
 * Empty when [PlayerPolicyContext.npcsFrozen] is true (NPCs cannot move).
 */
private fun computeRiskyCells(
    context: PlayerPolicyContext,
    deadly: Set<GridPos>
): Set<GridPos> {
    if (context.playerInvisibleToNpcs || context.npcsFrozen || context.npcs.isEmpty()) return emptySet()
    val maze = context.maze
    val cells = HashSet<GridPos>()
    for (npc in context.npcs) {
        for (direction in Direction.entries) {
            if (!maze.canMove(npc.position, direction)) continue
            val neighbour = npc.position.moved(direction)
            if (neighbour in deadly) continue
            cells.add(neighbour)
        }
    }
    return cells
}

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
