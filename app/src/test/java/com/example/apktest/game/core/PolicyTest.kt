package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PolicyTest {
    @Test
    fun bfsPolicy_movesTowardExit() {
        val maze = Maze.openGrid(4, 4)
        val navigator = MazeNavigator(maze)
        val policy = BfsExitPolicy()
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(3, 3),
                npcs = emptyList()
            )
        )

        assertNotNull(move)
        assertTrue(move == Direction.EAST || move == Direction.NORTH)
    }

    @Test
    fun directChasePolicy_movesTowardPlayer() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = DirectChasePolicy()
        val player = Player(position = GridPos(4, 4), facing = Direction.WEST)
        val npc = Npc(id = 1, position = GridPos(2, 2))

        val move = policy.nextMove(
            npc,
            NpcPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                visionRange = 5,
                playerVisible = true,
                npcsFrozen = false
            )
        )

        assertNotNull(move)
        assertTrue(move == Direction.EAST || move == Direction.NORTH)
    }

    @Test
    fun predictiveChasePolicy_movesTowardPlayerInOpenGrid() {
        val maze = Maze.openGrid(6, 6)
        val navigator = MazeNavigator(maze)
        val policy = PredictiveChasePolicy()
        val player = Player(position = GridPos(4, 4), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(1, 1))

        val move = policy.nextMove(
            npc,
            NpcPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                visionRange = 5,
                playerVisible = true,
                npcsFrozen = false
            )
        )

        assertNotNull(move)
        // Projection moves player further east; NPC should head east or north toward it.
        assertTrue(move == Direction.EAST || move == Direction.NORTH)
    }

    @Test
    fun predictiveChasePolicy_blockedProjectionFallsBackToPlayerPath() {
        // Wall the player into a single cell so projection cannot advance.
        val maze = Maze(
            width = 3,
            height = 2,
            cells = IntArray(6) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(2, 0)
        )
        // Open path between (0,0)-(1,0) only.
        maze.removeWall(GridPos(0, 0), Direction.EAST)
        val navigator = MazeNavigator(maze)
        val policy = PredictiveChasePolicy()
        // Player is isolated in (2,0), facing EAST (cannot move east, hits boundary).
        val player = Player(position = GridPos(2, 0), facing = Direction.EAST)
        val npc = Npc(id = 1, position = GridPos(0, 0))

        // A* to the projected (still (2,0)) target is unreachable; fallback BFS to player
        // is also unreachable, so policy must return null without crashing.
        val move = policy.nextMove(
            npc,
            NpcPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                visionRange = 1,
                playerVisible = true,
                npcsFrozen = false
            )
        )
        assertNull(move)
    }

    @Test
    fun patrolGuardPolicy_entersChaseWhenPlayerWithinVision() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = PatrolGuardPolicy()
        val player = Player(position = GridPos(2, 0), facing = Direction.WEST)
        val npc = Npc(id = 1, position = GridPos(0, 0))

        policy.nextMove(
            npc,
            NpcPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                visionRange = 3,
                playerVisible = true,
                npcsFrozen = false
            )
        )

        assertEquals(NpcState.CHASE, npc.state)
        assertEquals(GridPos(2, 0), npc.lastKnownPlayerPos)
    }

    @Test
    fun patrolGuardPolicy_chaseTransitionsToSearchAtLastKnownPosition() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = PatrolGuardPolicy()
        val target = GridPos(2, 0)
        val npc = Npc(
            id = 1,
            position = target,
            state = NpcState.CHASE,
            lastKnownPlayerPos = target,
            searchTicksRemaining = 1
        )
        // Player out of vision so CHASE is not retriggered this tick.
        val player = Player(position = GridPos(4, 4), facing = Direction.WEST)

        policy.nextMove(
            npc,
            NpcPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                visionRange = 1,
                playerVisible = true,
                npcsFrozen = false
            )
        )

        assertEquals(NpcState.SEARCH, npc.state)
    }

    @Test
    fun patrolGuardPolicy_searchCountdownReturnsToPatrol() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = PatrolGuardPolicy()
        val npc = Npc(
            id = 1,
            position = GridPos(0, 0),
            state = NpcState.SEARCH,
            lastKnownPlayerPos = GridPos(2, 2),
            searchTicksRemaining = 1
        )
        val player = Player(position = GridPos(4, 4), facing = Direction.WEST)
        val context = NpcPolicyContext(
            maze = maze,
            navigator = navigator,
            player = player,
            visionRange = 1,
            playerVisible = true,
            npcsFrozen = false
        )

        // Single tick brings searchTicksRemaining to 0 and transitions to PATROL.
        policy.nextMove(npc, context)

        assertEquals(NpcState.PATROL, npc.state)
        assertNull(npc.lastKnownPlayerPos)
    }

    @Test
    fun patrolGuardPolicy_ignoresPlayerWhenInvisible() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = PatrolGuardPolicy()
        val player = Player(position = GridPos(2, 0), facing = Direction.WEST)
        val npc = Npc(id = 1, position = GridPos(0, 0))

        policy.nextMove(
            npc,
            NpcPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                visionRange = 3,
                playerVisible = false,
                npcsFrozen = false
            )
        )

        assertEquals(NpcState.PATROL, npc.state)
        assertNull(npc.lastKnownPlayerPos)
    }
}
