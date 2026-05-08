package com.example.apktest.game.core

import org.junit.Assert.assertNotNull
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
                visionRange = 5
            )
        )

        assertNotNull(move)
        assertTrue(move == Direction.EAST || move == Direction.NORTH)
    }
}
