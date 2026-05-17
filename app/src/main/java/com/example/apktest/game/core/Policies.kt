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
    val npcsFrozen: Boolean = false,
    /**
     * Spawned power-ups currently on the map. Empty by default for
     * back-compatibility with call sites that don't supply pickup state
     * (e.g. unit tests). Used by [AvoidanceWrapperPolicy] together with
     * [pickupRadius] to drive opportunistic pickup detours.
     */
    val spawnedPowerUps: Collection<SpawnedPowerUp> = emptyList(),
    /**
     * Chebyshev (king-move) cell radius around [player] within which
     * [AvoidanceWrapperPolicy] will divert one step to pick up a nearby
     * power-up. `0` (the default) disables pickup-seeking behaviour.
     */
    val pickupRadius: Int = 0
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
interface RankedPlayerPolicy : PlayerPolicy {
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

    internal fun visitCount(position: GridPos): Int = visitCounts.getOrDefault(position, 0)

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
 * Pickup-seeking (added):
 * Before delegating to the inner policy, the wrapper checks for any
 * [PlayerPolicyContext.spawnedPowerUps] within Chebyshev distance
 * [PlayerPolicyContext.pickupRadius] of the player. If a power-up is
 * reachable by a walkable path of length ≤ `pickupRadius`, the wrapper
 * diverts one step toward it. The detour is gated by the same safety
 * ordering used for the regular move selection:
 * - A move that lands on [PlayerPolicyContext.exit] always wins this tick
 *   and beats any pickup detour ([GameEngine] evaluates the win condition
 *   before NPC collision).
 * - Detour steps into a currently NPC-occupied cell (`deadly`) are never
 *   taken.
 * - Among otherwise-valid candidates, non-risky steps (cells not threatened
 *   by an NPC neighbour) are preferred over risky ones; ties are broken
 *   by graph distance, [PowerUpType.ordinal], pickup position, then
 *   direction for deterministic behaviour.
 * Pickup-seeking is disabled when `pickupRadius <= 0` (the default for
 * contexts that don't supply one).
 *
 * Behaviour:
 * - When [PlayerPolicyContext.playerInvisibleToNpcs] is true, NPC contact
 *   does not lose the game; deadly/risky sets are empty and the wrapper
 *   reduces to pickup-seeking + a pass-through to the inner policy.
 * - When [PlayerPolicyContext.npcsFrozen] is true only NPC current cells are
 *   treated as deadly; the player can safely step onto an NPC's neighbour.
 * - For [BfsExitPolicy] / [AStarExitPolicy] the wrapper first asks the
 *   navigator for an avoidance-aware path that excludes NPC cells, and
 *   prefers that step when it avoids both deadly *and* risky cells (or
 *   when it lands on the exit, which wins the game this tick).
 * - Otherwise the wrapper picks the highest-ranked safe candidate from
 *   [RankedPlayerPolicy.rankedMoves] (preserving wall-follower priority and
 *   random-memory visit-count ordering). A move that lands on the exit is
 *   always preferred, even if the exit is currently occupied by an NPC,
 *   since [GameEngine] evaluates the win condition before NPC collision.
 *   If every candidate enters a currently NPC-occupied (deadly) cell the
 *   wrapper returns `null` (skip the tick) — stepping onto an NPC is a
 *   guaranteed loss, whereas waiting is at worst the same outcome.
 */
class AvoidanceWrapperPolicy(internal val inner: PlayerPolicy) : PlayerPolicy {
    override fun nextMove(context: PlayerPolicyContext): Direction? {
        val deadly = computeDeadlyCells(context)
        val risky = computeRiskyCells(context, deadly)
        val from = context.player.position

        // Always ask the wrapped policy for candidates first so ranked
        // policies can update their per-tick state (e.g. Random+Memory visit
        // counters), even on ticks where pickup-seeking wins.
        val ranked = (inner as? RankedPlayerPolicy)?.rankedMoves(context)
            ?: listOfNotNull(inner.nextMove(context))
        if (ranked.isEmpty()) return null

        // A move that wins the game this tick is always preferred, even if
        // the exit cell is currently occupied by an NPC.
        val winning = ranked.firstOrNull { from.moved(it) == context.exit }
        if (winning != null) return winning

        val safeRanked = ranked.firstOrNull { from.moved(it) !in deadly && from.moved(it) !in risky }
        val hasSafeRegularMove = safeRanked != null

        // Pickup-seeking detour: pre-empts the inner policy when a nearby
        // power-up can be reached safely within the configured radius. The
        // helper itself defers to a winning move when one exists, so the
        // exit always beats any detour.
        pickupSeekingStep(context, deadly, risky)?.let { pickupStep ->
            // Keep the existing safety ordering: do not take a risky pickup
            // detour when a non-risky regular move exists this tick.
            val pickupDest = from.moved(pickupStep)
            if (pickupDest !in risky || !hasSafeRegularMove) return pickupStep
        }

        if (context.playerInvisibleToNpcs || context.npcs.isEmpty()) {
            return passThroughMove(context, ranked)
        }

        if (deadly.isEmpty() && risky.isEmpty()) {
            return passThroughMove(context, ranked)
        }

        // For exit-finding policies, try an avoidance-aware path first so the
        // single-tick choice still routes optimally toward the exit while
        // skirting NPC cells. We only commit to the path step when it is
        // strictly safe (avoids both deadly *and* risky), so that a
        // non-risky candidate from the ranked list can still win a tie.
        // Exception: if the path step lands on the exit, that move wins the
        // game this tick (GameEngine evaluates the win condition before NPC
        // collision), so take it unconditionally.
        val pathStep = avoidanceAwarePathStep(context, deadly)
        if (pathStep != null) {
            val dest = context.player.position.moved(pathStep)
            if (dest == context.exit) return pathStep
            if (dest !in deadly && dest !in risky) return pathStep
        }

        if (safeRanked != null) return safeRanked

        val nonDeadly = ranked.firstOrNull { from.moved(it) !in deadly }
        if (nonDeadly != null) return nonDeadly

        // Every walkable direction enters a deadly (currently NPC-occupied)
        // cell. Stepping onto an NPC is a guaranteed loss this tick, while
        // standing still is at worst the same outcome (the NPC may or may
        // not move onto the player's cell), so we always skip rather than
        // commit to a guaranteed loss.
        return null
    }

    private fun passThroughMove(
        context: PlayerPolicyContext,
        ranked: List<Direction>
    ): Direction? = when (inner) {
        is BfsExitPolicy -> nextDirection(
            context.player.position,
            context.navigator.bfsPath(context.player.position, context.exit)
        )
        is AStarExitPolicy -> nextDirection(
            context.player.position,
            context.navigator.aStarPath(context.player.position, context.exit)
        )
        else -> ranked.firstOrNull()
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

    /**
     * Returns a single-step direction that diverts the player toward the
     * nearest reachable spawned power-up within [PlayerPolicyContext.pickupRadius]
     * Chebyshev cells, or `null` to defer to the regular policy logic.
     *
     * Returns `null` when:
     * - pickup-seeking is disabled (`pickupRadius <= 0`) or there are no
     *   spawned power-ups in [context],
     * - a winning step (walkable adjacent direction onto [PlayerPolicyContext.exit])
     *   is available — taking the exit always beats picking up,
     * - no candidate pickup is reachable by a walkable path of length
     *   `1..pickupRadius` whose cells are not currently NPC-occupied
     *   (`deadly`).
     *
     * Risk classification searches for an avoidance-aware path per pickup:
     * a path that avoids both deadly *and* risky cells is preferred (and
     * classifies the pickup as non-risky); only if no such path exists
     * within the radius do we fall back to a deadly-avoiding (risky) path.
     * This prevents a pickup with multiple shortest paths from being
     * incorrectly skipped or mis-classified just because plain BFS happened
     * to pick a deadly/risky first step.
     *
     * Candidate ordering: non-risky first, then by graph distance ascending,
     * then by [PowerUpType.ordinal], pickup position, and direction for
     * deterministic tie-breaking.
     */
    private fun pickupSeekingStep(
        context: PlayerPolicyContext,
        deadly: Set<GridPos>,
        risky: Set<GridPos>
    ): Direction? {
        val radius = context.pickupRadius
        if (radius <= 0) return null
        if (context.spawnedPowerUps.isEmpty()) return null

        val from = context.player.position
        val exit = context.exit

        // A winning step beats any pickup detour. Defer to the regular
        // selection logic (which preserves the existing win-priority
        // semantics, including the "exit cell occupied by an NPC" case).
        for (direction in Direction.entries) {
            if (context.maze.canMove(from, direction) && from.moved(direction) == exit) {
                return null
            }
        }

        var bestDirection: Direction? = null
        var bestRisky = true
        var bestDistance = Int.MAX_VALUE
        var bestOrdinal = Int.MAX_VALUE
        var bestX = Int.MAX_VALUE
        var bestY = Int.MAX_VALUE
        var bestDirectionOrdinal = Int.MAX_VALUE

        val deadlyAndRisky: Set<GridPos> = if (risky.isEmpty()) deadly else deadly + risky

        for (pickup in context.spawnedPowerUps) {
            val pos = pickup.position
            if (pos == from || pos == exit) continue
            val chebyshev = maxOf(abs(pos.x - from.x), abs(pos.y - from.y))
            if (chebyshev > radius) continue

            // Prefer an avoidance-aware path that skirts both deadly and
            // risky cells. Only fall back to a deadly-avoiding path when no
            // fully-safe path of length ≤ radius exists. Risk classification
            // is based on whether the chosen first step itself lands in a
            // risky cell, so a pickup whose only adjacent approach is risky
            // is still classified as risky.
            var path = context.navigator.bfsPath(from, pos, deadlyAndRisky)
            var steps = path.size - 1
            if (steps <= 0 || steps > radius) {
                if (risky.isEmpty()) continue
                path = context.navigator.bfsPath(from, pos, deadly)
                steps = path.size - 1
                if (steps <= 0 || steps > radius) continue
            }

            val direction = nextDirection(from, path) ?: continue
            if (!context.maze.canMove(from, direction)) continue
            val dest = from.moved(direction)
            if (dest in deadly) continue
            val candidateRisky = dest in risky
            val candidateOrdinal = pickup.type.ordinal
            val candidateDirectionOrdinal = direction.ordinal
            val better = when {
                candidateRisky != bestRisky -> !candidateRisky
                steps != bestDistance -> steps < bestDistance
                candidateOrdinal != bestOrdinal -> candidateOrdinal < bestOrdinal
                pos.x != bestX -> pos.x < bestX
                pos.y != bestY -> pos.y < bestY
                candidateDirectionOrdinal != bestDirectionOrdinal ->
                    candidateDirectionOrdinal < bestDirectionOrdinal
                else -> false
            }
            if (bestDirection == null || better) {
                bestDirection = direction
                bestRisky = candidateRisky
                bestDistance = steps
                bestOrdinal = candidateOrdinal
                bestX = pos.x
                bestY = pos.y
                bestDirectionOrdinal = candidateDirectionOrdinal
            }
        }

        return bestDirection
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
