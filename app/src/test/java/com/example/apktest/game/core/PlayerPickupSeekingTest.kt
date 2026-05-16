package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.random.Random

class PlayerPickupSeekingTest {

    private fun spawnedPowerUp(
        type: PowerUpType,
        position: GridPos
    ): SpawnedPowerUp = SpawnedPowerUp(
        type = type,
        position = position,
        spawnedAtSeconds = 0f,
        expiresAtSeconds = null
    )

    @Test
    fun bfsPolicy_divertsForAdjacentPowerUp() {
        // Player (0,0), exit (4,0): BFS would step EAST. Place a power-up
        // NORTH (in range, Chebyshev 1) so the wrapper diverts to grab it.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.NORTH, move)
    }

    @Test
    fun wallBlockedPowerUp_isNotPickedUp() {
        // 2x2 maze, player at (0,0), only the EAST passage is open.
        // A power-up at (0,1) is Chebyshev-adjacent but unreachable in one
        // step because the NORTH wall blocks the path. The wrapper must
        // fall through to the inner policy (BFS toward the exit at (1,0)
        // -> EAST).
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
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(1, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun radiusZero_disablesPickupSeeking() {
        // Same setup as the adjacent-pickup test, but pickupRadius = 0
        // (the back-compat default). The wrapper must ignore the pickup
        // and let BFS step EAST toward the exit.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 0
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun radiusTwo_divertsForPickupTwoCellsAway() {
        // Player (0,0), exit (4,0). Power-up at (0,2): Chebyshev 2, graph 2.
        // With radius=2 the wrapper diverts NORTH (toward the pickup) on
        // this tick.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 2))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 2
            )
        )

        assertEquals(Direction.NORTH, move)
    }

    @Test
    fun radiusOne_ignoresPickupTwoCellsAway() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 2))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun approachStepBlockedByNpc_skipsPickup() {
        // Power-up at (0,1); only approach step is NORTH onto (0,1) — which
        // is currently occupied by an NPC (deadly). The wrapper must not
        // suicide for the pickup; instead it falls through to safe BFS.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))
        val npc = Npc(id = 1, position = GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = listOf(npc),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertNotEquals(Direction.NORTH, move)
        assertEquals(Direction.EAST, move)
    }

    @Test
    fun riskyPickup_doesNotBeatSafeRegularMove() {
        // Player (0,0); single power-up NORTH at (0,1). An NPC at (0,2)
        // makes (0,1) risky (NPC could step south next tick). EAST remains a
        // safe regular move toward the exit, so the wrapper should not take
        // the risky detour.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))
        val npc = Npc(id = 1, position = GridPos(0, 2))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = listOf(npc),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun nonRiskyPickupPreferredOverRiskyPickup() {
        // Two pickups at equal distance from player (1,1). Power-up NORTH at
        // (1,2) is non-risky; SOUTH at (1,0) is risky because of an NPC at
        // (2,0). The non-risky pickup should be preferred.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(1, 1), facing = Direction.EAST)
        val pickupNorth = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(1, 2))
        val pickupSouth = spawnedPowerUp(PowerUpType.FREEZE, GridPos(1, 0))
        val npc = Npc(id = 1, position = GridPos(2, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 4),
                npcs = listOf(npc),
                spawnedPowerUps = listOf(pickupNorth, pickupSouth),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.NORTH, move)
    }

    @Test
    fun winningMove_beatsPickupDetour() {
        // Player at (0,0), exit at (1,0): an adjacent winning step is
        // available. A power-up at (0,1) must be ignored in favour of the
        // exit so the wrapped BfsExitPolicy can claim the win this tick.
        val maze = Maze.openGrid(2, 2)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(1, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun pickupOnExitCell_isIgnored() {
        // A spawned power-up that happens to share the exit cell must not
        // be treated as a detour target — it will be collected naturally
        // when the player wins.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(4, 0))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        // BFS would step EAST toward the exit; no detour to the exit cell.
        assertEquals(Direction.EAST, move)
    }

    @Test
    fun noPickups_returnsInnerPolicyMove() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = emptyList(),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.EAST, move)
    }

    @Test
    fun manualPolicy_remainsNullEvenWithNearbyPickup() {
        // ManualPolicy is not wrapped; it should still return null (deferring
        // to the engine's manual queue) regardless of context state.
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = PolicyFactory.player(PlayerPolicyType.MANUAL)
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertNull(move)
    }

    @Test
    fun randomMemory_pickupDetourStillUpdatesVisitCounts() {
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val inner = RandomWalkMemoryPolicy(Random(42))
        val policy = AvoidanceWrapperPolicy(inner)
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val pickup = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 1))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(pickup),
                pickupRadius = 1
            )
        )

        assertEquals(Direction.NORTH, move)
        assertEquals(1, inner.visitCount(GridPos(0, 0)))
    }

    @Test
    fun nearerPickupPreferredOverFarther() {
        // Two pickups: (0,1) at graph distance 1, (0,2) at graph distance 2.
        // With radius=2 the wrapper must pick the nearer one (NORTH step).
        val maze = Maze.openGrid(5, 5)
        val navigator = MazeNavigator(maze)
        val policy = AvoidanceWrapperPolicy(BfsExitPolicy())
        val player = Player(position = GridPos(0, 0), facing = Direction.EAST)
        val near = spawnedPowerUp(PowerUpType.FREEZE, GridPos(0, 1))
        val far = spawnedPowerUp(PowerUpType.SPEED_UP, GridPos(0, 2))

        val move = policy.nextMove(
            PlayerPolicyContext(
                maze = maze,
                navigator = navigator,
                player = player,
                exit = GridPos(4, 0),
                npcs = emptyList(),
                spawnedPowerUps = listOf(far, near),
                pickupRadius = 2
            )
        )

        assertNotNull(move)
        assertEquals(Direction.NORTH, move)
    }
}
