package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlayerAvoidanceTest {
    @Test
    fun bfsPolicy_avoidsAdjacentNpc() {
        // Open 5x5 grid; player at (0,0), exit at (4,0). Plain BFS would go
        // EAST first; place an NPC on (1,0) so the wrapper must pick a
        // different walkable direction.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = listOf(npc)
            )
        )

        assertNotNull(move)
        assertNotEquals(Direction.EAST, move)
        // The remaining safe direction in this configuration is NORTH (the
        // detour around the NPC).
        assertEquals(Direction.NORTH, move)
    }

    @Test
    fun wallFollower_picksSafeDirectionFromOrderedList() {
        // Left-hand wall follower facing EAST priority is [NORTH, EAST, SOUTH, WEST].
        // Place an NPC NORTH of the player so the policy falls through to its
        // next priority (EAST).
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(WallFollowerPolicy(leftHand = true))
        val player = Player(position = GridPos(2, 2), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(2, 3))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 4),
                npcs = listOf(npc)
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun randomMemory_avoidsNpcCells() {
        // Seeded RNG keeps the choice deterministic. Place an NPC on every
        // direction except WEST so the safe pick is unambiguous.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(RandomWalkMemoryPolicy(Random(42)))
        val player = Player(position = GridPos(2, 2), facing = Direction.EAST)
        val npcs = listOf(
            Npc(id = 1, position = GridPos(3, 2)), // EAST
            Npc(id = 2, position = GridPos(2, 3)), // NORTH
            Npc(id = 3, position = GridPos(2, 1))  // SOUTH
        )

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 4),
                npcs = npcs
            )
        )

        assertEquals(Direction.WEST, move)
    }

    @Test
    fun policiesIgnoreNpcsWhenInvisibilityActive() {
        // With INVISIBILITY active the wrapper is a pass-through; BFS' first
        // EAST step is returned even though an NPC sits on (1,0).
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = listOf(npc),
                playerInvisibleToNpcs = true
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun freezeOnly_currentCellIsDangerous_neighborsNot() {
        // FREEZE active -> NPC won't move; the player may safely step onto a
        // neighbour of an NPC. The NPC at (2,0) is still treated as deadly
        // (walking onto an NPC cell would lose once FREEZE expires), so the
        // wrapper routes around it. Either EAST (to (1,0)) or NORTH (to (0,1))
        // is a valid first step on an open 5x5 grid.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(2, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = listOf(npc),
                npcsFrozen = true
            )
        )

        assertNotNull(move)
        assertTrue("expected EAST or NORTH detour, got $move", move == Direction.EAST || move == Direction.NORTH)
    }

    @Test
    fun noSafeMove_fallsBackToInnerWhenPlayerCellAlsoRisky() {
        // Player in a 2-cell corridor with an NPC on the only walkable
        // neighbour. Maze walls are symmetric, so the NPC could move back
        // onto the player's cell next tick — i.e. player's cell is in the
        // risky set. The wrapper has no improvement to offer and falls
        // back to the inner BFS step rather than uselessly skipping.
        val maze = Maze(
            width = 2,
            height = 2,
            cells = IntArray(4) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(1, 0)
        )
        maze.removeWall(GridPos(0, 0), Direction.EAST)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(1, 0),
                npcs = listOf(npc)
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun bfsAvoidance_fallsBackWhenAlreadyInRiskyCell() {
        // Same setup as noSafeMove_fallsBackToInnerWhenPlayerCellAlsoRisky;
        // kept as a separate explicit assertion that the wrapper does not
        // return null when standing still confers no safety advantage.
        val maze = Maze(
            width = 2,
            height = 2,
            cells = IntArray(4) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(1, 0)
        )
        maze.removeWall(GridPos(0, 0), Direction.EAST)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(1, 0),
                npcs = listOf(npc)
            )
        )

        assertNotNull(move)
        assertEquals(Direction.EAST, move)
    }

    @Test
    fun bfsAvoidance_routesAroundNpcWithLongerPath() {
        // Player (0,0); exit (2,0). Direct path is EAST EAST. NPC on (1,0)
        // forces the avoidance-aware BFS to go NORTH-EAST-EAST-SOUTH; the
        // first step must be NORTH.
        val maze = Maze.openGrid(3, 2)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(2, 0),
                npcs = listOf(npc)
            )
        )

        assertEquals(Direction.NORTH, move)
    }

    @Test
    fun aStarAvoidance_picksNonNpcDirection() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(AStarExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = listOf(npc)
            )
        )

        assertNotEquals(Direction.EAST, move)
        assertNotNull(move)
    }

    @Test
    fun manualPolicy_isNotWrapped() {
        // PolicyFactory must leave MANUAL alone so the human player retains
        // full control (returning null lets GameEngine consume the manual
        // queue instead).
        val policy = PolicyFactory.player(PlayerPolicyType.MANUAL)
        assertTrue(policy is ManualPolicy)
    }

    @Test
    fun policyFactory_wrapsAllNonManualPolicies() {
        for (type in PlayerPolicyType.entries) {
            if (type == PlayerPolicyType.MANUAL) continue
            val policy = PolicyFactory.player(type)
            assertTrue("$type should be wrapped", policy is AvoidanceWrapperPolicy)
        }
    }
}
