package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PledgeAndFleePolicyTest {

    private fun playerContext(
        maze: Maze,
        player: Player,
        exit: GridPos,
        npcs: List<Npc> = emptyList()
    ): PlayerPolicyContext = PlayerPolicyContext(
        maze = maze,
        navigator = MazeNavigator(maze),
        player = player,
        exit = exit,
        npcs = npcs
    )

    // ---------------- PledgePolicy ----------------

    @Test
    fun pledge_walksStraightAlongReferenceDirectionWhenWalkable() {
        // Open grid: reference toward exit (EAST), no obstacles → should pick EAST.
        val maze = Maze.openGrid(4, 1)
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val policy = PledgePolicy()

        val move = policy.rankedMoves(playerContext(maze, player, exit = GridPos(3, 0))).first()
        assertEquals(Direction.EAST, move)
    }

    @Test
    fun pledge_entersWallFollowWhenReferenceBlocked() {
        // 3x3 grid all walls. Open NORTH out of (0,0) only; exit east forces ref=EAST.
        val maze = Maze(
            width = 3,
            height = 3,
            cells = IntArray(9) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(2, 0)
        )
        maze.removeWall(GridPos(0, 0), Direction.NORTH)
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val policy = PledgePolicy()

        // ref=EAST is blocked; the only walkable neighbour is NORTH so policy
        // must enter wall-follow mode and pick NORTH.
        val move = policy.rankedMoves(playerContext(maze, player, exit = GridPos(2, 0))).first()
        assertEquals(Direction.NORTH, move)
    }

    @Test
    fun pledge_exitsWallFollowWhenReferenceBecomesWalkableEvenIfPickedDiffers() {
        // Regression for the wall-follow exit condition: it must drop the
        // wall-follow state when rotation returns to 0 and the reference
        // direction is walkable again, independent of which direction the
        // wall-follower itself picked on that tick.
        //
        // 4x4 grid, all walls. Carve a detour around a SOUTH-blocked start:
        //   (1,1)-N->(1,2)-E->(2,2)-S->(2,1)     and     (2,2)-N->(2,3)
        // ref = SOUTH (exit at (1,0)). Initial facing EAST.
        val maze = Maze(
            width = 4,
            height = 4,
            cells = IntArray(16) { Maze.ALL_WALLS },
            start = GridPos(1, 1),
            exit = GridPos(1, 0)
        )
        maze.removeWall(GridPos(1, 1), Direction.NORTH) // (1,1) <-> (1,2)
        maze.removeWall(GridPos(1, 2), Direction.EAST)  // (1,2) <-> (2,2)
        maze.removeWall(GridPos(2, 2), Direction.NORTH) // (2,2) <-> (2,3)
        maze.removeWall(GridPos(2, 2), Direction.SOUTH) // (2,2) <-> (2,1)

        val policy = PledgePolicy()

        // Tick 1: at (1,1) facing EAST. ref=SOUTH blocked; only NORTH walkable.
        // Wall-follow enters; picked=NORTH (left of EAST); rotation=-1.
        val p1 = Player(position = GridPos(1, 1), facing = Direction.EAST)
        assertEquals(Direction.NORTH, policy.rankedMoves(playerContext(maze, p1, GridPos(1, 0))).first())

        // Tick 2: at (1,2) facing NORTH. Walkable={EAST, SOUTH}.
        // picked=EAST (right of NORTH); rotation returns to 0. ref=SOUTH is
        // walkable but picked != ref. Both old and new code pick EAST here.
        val p2 = Player(position = GridPos(1, 2), facing = Direction.NORTH)
        assertEquals(Direction.EAST, policy.rankedMoves(playerContext(maze, p2, GridPos(1, 0))).first())

        // Tick 3: at (2,2) facing EAST. Walkable={NORTH, WEST, SOUTH}.
        // With the fix, wall-follow was cleared at the end of tick 2 (rotation=0
        // and ref walkable), so the straight-walk branch fires and picks ref=SOUTH.
        // Without the fix, wall-follow would still be active (because picked!=ref
        // back in tick 2) and would pick NORTH (left of EAST).
        val p3 = Player(position = GridPos(2, 2), facing = Direction.EAST)
        assertEquals(Direction.SOUTH, policy.rankedMoves(playerContext(maze, p3, GridPos(1, 0))).first())
    }

    @Test
    fun pledge_resetClearsWallFollowState() {
        val maze = Maze(
            width = 3,
            height = 3,
            cells = IntArray(9) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(2, 0)
        )
        maze.removeWall(GridPos(0, 0), Direction.NORTH)

        val policy = PledgePolicy()
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        policy.rankedMoves(playerContext(maze, player, exit = GridPos(2, 0)))
        policy.reset()

        // After reset, an open grid with ref=EAST walkable should pick EAST.
        val open = Maze.openGrid(4, 1)
        val openPlayer = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val move = policy.rankedMoves(playerContext(open, openPlayer, exit = GridPos(3, 0))).first()
        assertEquals(Direction.EAST, move)
    }

    // ---------------- FleeToExitPolicy ----------------

    @Test
    fun fleeToExit_prefersDirectionAwayFromNearestNpc() {
        // 5x1 corridor. Player in middle, NPC immediately east → flee west.
        val maze = Maze.openGrid(5, 1)
        val player = Player(position = GridPos(2, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(3, 0))
        val policy = FleeToExitPolicy()

        val move = policy.rankedMoves(playerContext(maze, player, GridPos(4, 0), listOf(npc))).first()
        assertEquals(Direction.WEST, move)
    }

    @Test
    fun fleeToExit_breaksNpcDistanceTieByExitDistance() {
        // Open grid. NPC equidistant from both candidate destinations after the
        // step (Chebyshev tie); the move that reduces exit BFS distance wins.
        val maze = Maze.openGrid(5, 5)
        val player = Player(position = GridPos(2, 2), facing = Direction.EAST)
        // NPC at (0, 0); from (3,2) and (2,3) Chebyshev distance is 3 in both cases.
        val npc = Npc(id = 1, position = GridPos(0, 0))
        val exit = GridPos(4, 2) // EAST step gets closer than NORTH step.
        val policy = FleeToExitPolicy()

        val move = policy.rankedMoves(playerContext(maze, player, exit, listOf(npc))).first()
        assertEquals(Direction.EAST, move)
    }

    @Test
    fun fleeToExit_returnsEmptyWhenNoMovesAvailable() {
        val maze = Maze(
            width = 1,
            height = 1,
            cells = IntArray(1) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(0, 0)
        )
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val policy = FleeToExitPolicy()

        assertTrue(policy.rankedMoves(playerContext(maze, player, GridPos(0, 0))).isEmpty())
    }

    // ---------- AvoidanceWrapperPolicy interaction ----------

    @Test
    fun avoidanceWrapper_overridesPledgeStepIntoNpcOccupiedCell() {
        // Open grid. Pledge would walk EAST toward the exit, but an NPC sits
        // exactly on the EAST neighbour. Wrapper must pick a non-deadly move.
        val maze = Maze.openGrid(4, 3)
        val player = Player(position = GridPos(0, 1), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 1))
        val wrapped = AvoidanceWrapperPolicy(PledgePolicy())

        val move = wrapped.nextMove(playerContext(maze, player, GridPos(3, 1), listOf(npc)))
        assertNotNull(move)
        assertNotEquals(Direction.EAST, move)
    }

    @Test
    fun avoidanceWrapper_overridesFleeToExitStepIntoNpcOccupiedCell() {
        val maze = Maze.openGrid(4, 3)
        val player = Player(position = GridPos(0, 1), facing = Direction.EAST)
        // NPC on the most-preferred FLEE_TO_EXIT step (EAST toward exit).
        val npc = Npc(id = 1, position = GridPos(1, 1))
        val wrapped = AvoidanceWrapperPolicy(FleeToExitPolicy())

        val move = wrapped.nextMove(playerContext(maze, player, GridPos(3, 1), listOf(npc)))
        assertNotNull(move)
        assertNotEquals(Direction.EAST, move)
    }
}
