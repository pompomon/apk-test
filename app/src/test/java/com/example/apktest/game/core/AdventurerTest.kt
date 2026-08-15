package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdventurerTest {
    @Test
    fun shippedDifficultiesSpawnOneAdventurerInClassicAndAdventure() {
        for (preset in DifficultyPresets.all) {
            val classic = GameEngine(preset, SEED)
            val adventure = GameEngine(preset, SEED)
            adventure.configureAdventureMaze(
                npcCount = preset.npcCount,
                policies = List(preset.npcCount) { NpcPolicyType.DIRECT_CHASE }
            )
            adventure.restart(SEED)

            assertEquals("${preset.name} Classic Adventurer count", 1, classic.adventurers.size)
            assertEquals("${preset.name} Adventure Adventurer count", 1, adventure.adventurers.size)
        }
    }

    @Test
    fun multipleAdventurersSpawnDeterministicallyAtClosestAvailableExitDistances() {
        val preset = adventurerPreset(adventurerCount = 3)
        val first = GameEngine(preset, SEED)
        val second = GameEngine(preset, SEED)

        assertEquals(3, first.adventurers.size)
        assertEquals(
            first.adventurers.map { it.position },
            second.adventurers.map { it.position }
        )
        val positions = first.adventurers.map { it.position }
        assertEquals(positions.size, positions.toSet().size)
        assertTrue(first.maze.start !in positions)
        assertTrue(first.maze.exit !in positions)

        val playerDistance = pathDistance(first, first.maze.start)
        val availableErrors = allCells(first.maze)
            .filter { it != first.maze.start && it != first.maze.exit }
            .map { abs(pathDistance(first, it) - playerDistance) }
            .sorted()
        val largestSelectedError = positions.maxOf {
            abs(pathDistance(first, it) - playerDistance)
        }
        assertTrue(
            "Spawned Adventurers should use the closest available exit-distance tier",
            largestSelectedError <= availableErrors[preset.adventurerCount - 1]
        )
    }

    @Test
    fun adventurerMovesOnlyAfterConfiguredSpeedInterval() {
        val preset = adventurerPreset(
            adventurerCount = 1,
            playerMovesPerSecond = 5f,
            adventurerSpeedRatio = 0.8f
        )
        val engine = GameEngine(preset, SEED)
        val start = engine.adventurers.single().position
        assertTrue(engine.navigator.bfsPath(start, engine.maze.exit).size > 2)

        engine.update(0.24f)
        assertEquals(start, engine.adventurers.single().position)

        engine.update(0.02f)
        assertNotEquals(start, engine.adventurers.single().position)
    }

    @Test
    fun adventurerDisappearsAfterReachingExitWithoutEndingPlayerRun() {
        val engine = GameEngine(adventurerPreset(adventurerCount = 1), SEED)
        val path = engine.navigator.bfsPath(engine.maze.start, engine.maze.exit)
        assertTrue(path.size >= 2)
        engine.adventurers.single().position = path[path.lastIndex - 1]

        engine.update(1f)

        assertTrue(engine.adventurers.isEmpty())
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun enemyArrivalEliminatesCaughtAdventurer() {
        val engine = GameEngine(
            adventurerPreset(adventurerCount = 1, npcCount = 1),
            SEED
        )
        val adventurerPosition = engine.adventurers.single().position

        engine.simulateNpcArrivalForTest(0, adventurerPosition)

        assertTrue(engine.adventurers.isEmpty())
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun directChaseTargetsCloserAdventurer() {
        val maze = Maze.openGrid(6, 6)
        val policy = DirectChasePolicy()
        val npc = Npc(id = 0, position = GridPos(0, 0))
        val context = NpcPolicyContext(
            maze = maze,
            navigator = MazeNavigator(maze),
            player = Player(GridPos(5, 0)),
            visionRange = 6,
            playerVisible = true,
            npcsFrozen = false,
            adventurers = listOf(Adventurer(id = 0, position = GridPos(0, 2)))
        )

        assertEquals(Direction.NORTH, policy.nextMove(npc, context))
    }

    @Test
    fun directChaseGivesPlayerPriorityWhenTargetDistancesTie() {
        val maze = Maze.openGrid(6, 6)
        val policy = DirectChasePolicy()
        val npc = Npc(id = 0, position = GridPos(2, 2))
        val context = NpcPolicyContext(
            maze = maze,
            navigator = MazeNavigator(maze),
            player = Player(GridPos(4, 2)),
            visionRange = 6,
            playerVisible = true,
            npcsFrozen = false,
            adventurers = listOf(Adventurer(id = 0, position = GridPos(2, 4)))
        )

        assertEquals(Direction.EAST, policy.nextMove(npc, context))
    }

    @Test
    fun invisiblePlayerDoesNotHideAdventurersFromEnemies() {
        val maze = Maze.openGrid(6, 6)
        val policy = DirectChasePolicy()
        val npc = Npc(id = 0, position = GridPos(0, 0))
        val context = NpcPolicyContext(
            maze = maze,
            navigator = MazeNavigator(maze),
            player = Player(GridPos(1, 0)),
            visionRange = 6,
            playerVisible = false,
            npcsFrozen = false,
            adventurers = listOf(Adventurer(id = 0, position = GridPos(0, 3)))
        )

        assertEquals(Direction.NORTH, policy.nextMove(npc, context))
    }

    @Test
    fun predictiveChaseUsesAdventurerFacingWhenPlayerIsInvisible() {
        val maze = Maze.openGrid(6, 6)
        val policy = PredictiveChasePolicy()
        val npc = Npc(id = 0, position = GridPos(0, 0))
        val context = NpcPolicyContext(
            maze = maze,
            navigator = MazeNavigator(maze),
            player = Player(GridPos(1, 0)),
            visionRange = 6,
            playerVisible = false,
            npcsFrozen = false,
            adventurers = listOf(
                Adventurer(
                    id = 0,
                    position = GridPos(0, 2),
                    facing = Direction.NORTH
                )
            )
        )

        assertEquals(Direction.NORTH, policy.nextMove(npc, context))
    }

    @Test
    fun patrolGuardDetectsAdventurerWhenPlayerIsInvisible() {
        val maze = Maze.openGrid(6, 6)
        val policy = PatrolGuardPolicy()
        val npc = Npc(id = 0, position = GridPos(0, 0))
        val adventurer = Adventurer(id = 0, position = GridPos(0, 2))
        val context = NpcPolicyContext(
            maze = maze,
            navigator = MazeNavigator(maze),
            player = Player(GridPos(1, 0)),
            visionRange = 3,
            playerVisible = false,
            npcsFrozen = false,
            adventurers = listOf(adventurer)
        )

        policy.nextMove(npc, context)

        assertEquals(NpcState.CHASE, npc.state)
        assertEquals(adventurer.position, npc.lastKnownPlayerPos)
    }

    private fun adventurerPreset(
        adventurerCount: Int,
        npcCount: Int = 0,
        playerMovesPerSecond: Float = 5f,
        adventurerSpeedRatio: Float = 0.9f
    ): DifficultyPreset = DifficultyPreset(
        name = "AdventurerTest",
        mazeWidth = 12,
        mazeHeight = 16,
        npcCount = npcCount,
        playerMovesPerSecond = playerMovesPerSecond,
        npcMovesPerSecond = 1f,
        npcVisionRange = 4,
        initialPowerUpTypes = emptyList(),
        adventurerCount = adventurerCount,
        adventurerSpeedRatio = adventurerSpeedRatio,
        adventurerPolicyType = PlayerPolicyType.BFS_EXIT
    )

    private fun pathDistance(engine: GameEngine, from: GridPos): Int {
        val path = engine.navigator.bfsPath(from, engine.maze.exit)
        assertTrue("Expected $from to reach the exit", path.isNotEmpty())
        return path.size - 1
    }

    private fun allCells(maze: Maze): List<GridPos> = buildList {
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) add(GridPos(x, y))
        }
    }

    private companion object {
        const val SEED = 73L
    }
}
