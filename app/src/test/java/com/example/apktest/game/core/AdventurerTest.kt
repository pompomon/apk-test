package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun multipleAdventurersSpawnDeterministicallyFromFarthestChebyshevShortlist() {
        val preset = adventurerPreset(adventurerCount = 3)
        val first = GameEngine(preset, SEED)
        val second = GameEngine(preset, SEED)

        assertEquals(3, first.adventurers.size)
        assertEquals(
            first.adventurers.map { it.position },
            second.adventurers.map { it.position }
        )
        val positions = first.adventurers.sortedBy { it.id }.map { it.position }
        assertEquals(positions.size, positions.toSet().size)
        assertTrue(first.maze.start !in positions)
        assertTrue(first.maze.exit !in positions)

        // Each Adventurer is placed independently: it must land within the 5
        // farthest-by-Chebyshev-distance eligible cells remaining at the time
        // it was chosen (earlier Adventurers' cells already removed from the
        // pool).
        val eligible = eligibleAdventurerSpawnCells(first, preset)
        val chosenSoFar = mutableSetOf<GridPos>()
        for (position in positions) {
            val shortlist = eligible
                .filterNot { it in chosenSoFar }
                .sortedWith(
                    compareByDescending<GridPos> { chebyshevDistance(it, first.maze.start) }
                        .thenBy { it.x }
                        .thenBy { it.y }
                )
                .take(5)
            assertTrue(
                "Adventurer spawn $position should be among the 5 farthest eligible " +
                    "cells from the player start at the time it was placed",
                position in shortlist
            )
            chosenSoFar += position
        }
    }

    @Test
    fun singleAdventurerSpawnsAmongTheFiveFarthestEligibleCells() {
        val preset = adventurerPreset(adventurerCount = 1)
        val engine = GameEngine(preset, SEED)

        val eligible = eligibleAdventurerSpawnCells(engine, preset)
        val shortlist = eligible
            .sortedWith(
                compareByDescending<GridPos> { chebyshevDistance(it, engine.maze.start) }
                    .thenBy { it.x }
                    .thenBy { it.y }
            )
            .take(5)

        assertTrue(
            "Adventurer spawn ${engine.adventurers.single().position} should be among the " +
                "5 farthest eligible cells from the player start",
            engine.adventurers.single().position in shortlist
        )
    }

    @Test
    fun adventurerSpawnsWithinPlayerExitChebyshevDistanceTolerance() {
        val engine = GameEngine(adventurerPreset(adventurerCount = 3), SEED)
        val playerExitDistance = chebyshevDistance(engine.maze.start, engine.maze.exit)

        assertTrue(engine.adventurers.isNotEmpty())
        engine.adventurers.forEach { adventurer ->
            val adventurerExitDistance = chebyshevDistance(adventurer.position, engine.maze.exit)
            assertTrue(
                "Adventurer ${adventurer.id} exit-distance difference must be at most " +
                    ADVENTURER_EXIT_DISTANCE_TOLERANCE,
                abs(adventurerExitDistance - playerExitDistance) <=
                    ADVENTURER_EXIT_DISTANCE_TOLERANCE
            )
        }
    }

    @Test
    fun adventurerSpawn_usesAllAvailableCandidatesWhenFewerThanFiveRemain() {
        val preset = adventurerPreset(
            adventurerCount = 5,
            mazeWidth = 4,
            mazeHeight = 6,
            adventurerPlayerSpawnBuffer = 4
        )
        val engine = GameEngine(preset, seed = 0L)
        val eligible = eligibleAdventurerSpawnCells(engine, preset)

        assertEquals(4, eligible.size)
        assertEquals(eligible.toSet(), engine.adventurers.map { it.position }.toSet())
    }

    @Test
    fun adventurerSpawn_leavesRosterEmptyWhenNoCandidateIsEligible() {
        val preset = adventurerPreset(
            adventurerCount = 1,
            mazeWidth = 4,
            mazeHeight = 6,
            adventurerPlayerSpawnBuffer = 100
        )
        val engine = GameEngine(preset, seed = 0L)

        assertTrue(eligibleAdventurerSpawnCells(engine, preset).isEmpty())
        assertTrue(engine.adventurers.isEmpty())
    }

    @Test
    fun adventurerSpawn_respectsConfiguredPlayerChebyshevBuffer() {
        val preset = adventurerPreset(adventurerCount = 1, adventurerPlayerSpawnBuffer = 3)
        val engine = GameEngine(preset, SEED)

        assertTrue(
            chebyshevDistance(engine.maze.start, engine.adventurers.single().position) >=
                preset.adventurerPlayerSpawnBuffer
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
    fun adventurerReachingExitCausesPlayerLoss() {
        val engine = GameEngine(adventurerPreset(adventurerCount = 1), SEED)
        val path = engine.navigator.bfsPath(engine.maze.start, engine.maze.exit)
        assertTrue(path.size >= 2)
        val adventurer = engine.adventurers.single()
        adventurer.position = path[path.lastIndex - 1]

        engine.update(1f)

        assertEquals(engine.maze.exit, adventurer.position)
        assertTrue(engine.adventurers.isEmpty())
        assertEquals(GameStatus.LOSE, engine.status)
    }

    @Test
    fun adventurerExitWithNpcPresentStillCausesPlayerLoss() {
        val engine = GameEngine(adventurerPreset(adventurerCount = 1), SEED)
        val path = engine.navigator.bfsPath(engine.maze.start, engine.maze.exit)
        assertTrue(path.size >= 2)
        val adventurer = engine.adventurers.single()
        adventurer.position = path[path.lastIndex - 1]
        engine.npcs += Npc(id = 0, position = engine.maze.exit)

        engine.update(1f)

        assertEquals(engine.maze.exit, adventurer.position)
        assertTrue(engine.adventurers.isEmpty())
        assertEquals(GameStatus.LOSE, engine.status)
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
    fun adventurerPickup_personalProtectionEffectsPreventCapture() {
        for (type in listOf(PowerUpType.SHIELD, PowerUpType.INVISIBILITY)) {
            val engine = GameEngine(
                adventurerPreset(
                    adventurerCount = 1,
                    npcCount = 1,
                    initialPowerUpTypes = listOf(type)
                ),
                SEED
            )
            val pickup = engine.spawnedPowerUps.single()

            engine.simulateAdventurerArrivalForTest(0, pickup.position)
            engine.simulateNpcArrivalForTest(0, engine.adventurers.single().position)

            assertFalse(engine.spawnedPowerUps.any { it.position == pickup.position })
            assertTrue(engine.isAdventurerPowerUpTintActive(0, type))
            assertEquals(1, engine.adventurers.size)
        }
    }

    @Test
    fun adventurerPickup_freezeAndSlowTimeApplyGloballyToNpcs() {
        for (type in listOf(PowerUpType.FREEZE, PowerUpType.SLOW_TIME)) {
            val engine = GameEngine(
                adventurerPreset(
                    adventurerCount = 1,
                    npcCount = 1,
                    initialPowerUpTypes = listOf(type)
                ),
                SEED
            )

            engine.simulateAdventurerArrivalForTest(0, engine.spawnedPowerUps.single().position)

            assertTrue(
                "$type picked up by an Adventurer must use the global NPC effect pipeline",
                engine.activePowerUps.any { it.type == type }
            )
        }
    }

    @Test
    fun npcFreezePickup_stopsAdventurersAsWellAsPlayer() {
        val engine = GameEngine(
            adventurerPreset(
                adventurerCount = 1,
                npcCount = 1,
                initialPowerUpTypes = listOf(PowerUpType.FREEZE)
            ),
            SEED
        )
        val before = engine.adventurers.single().position
        val playerBefore = engine.player.position

        engine.simulateNpcArrivalForTest(0, engine.spawnedPowerUps.single().position)
        engine.update(1f)

        assertEquals(before, engine.adventurers.single().position)
        assertEquals(playerBefore, engine.player.position)
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

    @Test
    fun patrolGuardFiltersVisionRangeBeforeChoosingNearestPathTarget() {
        val maze = Maze(
            width = 2,
            height = 3,
            cells = IntArray(6) { Maze.ALL_WALLS },
            start = GridPos(0, 0),
            exit = GridPos(1, 0)
        )
        val npcPosition = GridPos(0, 0)
        val playerPosition = GridPos(1, 0)
        val adventurerPosition = GridPos(0, 2)
        maze.removeWall(npcPosition, Direction.NORTH)
        maze.removeWall(GridPos(0, 1), Direction.NORTH)
        maze.removeWall(GridPos(0, 1), Direction.EAST)
        maze.removeWall(GridPos(1, 1), Direction.SOUTH)
        val npc = Npc(id = 0, position = npcPosition)
        val context = NpcPolicyContext(
            maze = maze,
            navigator = MazeNavigator(maze),
            player = Player(playerPosition),
            visionRange = 1,
            playerVisible = true,
            npcsFrozen = false,
            adventurers = listOf(Adventurer(id = 0, position = adventurerPosition))
        )

        PatrolGuardPolicy().nextMove(npc, context)

        assertEquals(NpcState.CHASE, npc.state)
        assertEquals(playerPosition, npc.lastKnownPlayerPos)
    }

    private fun adventurerPreset(
        adventurerCount: Int,
        mazeWidth: Int = 12,
        mazeHeight: Int = 16,
        npcCount: Int = 0,
        playerMovesPerSecond: Float = 5f,
        adventurerSpeedRatio: Float = 0.9f,
        adventurerPlayerSpawnBuffer: Int = 2,
        initialPowerUpTypes: List<PowerUpType> = emptyList()
    ): DifficultyPreset = DifficultyPreset(
        name = "AdventurerTest",
        mazeWidth = mazeWidth,
        mazeHeight = mazeHeight,
        npcCount = npcCount,
        playerMovesPerSecond = playerMovesPerSecond,
        npcMovesPerSecond = 1f,
        npcVisionRange = 4,
        initialPowerUpTypes = initialPowerUpTypes,
        adventurerCount = adventurerCount,
        adventurerSpeedRatio = adventurerSpeedRatio,
        adventurerPolicyType = PlayerPolicyType.BFS_EXIT,
        adventurerPlayerSpawnBuffer = adventurerPlayerSpawnBuffer
    )

    private fun eligibleAdventurerSpawnCells(
        engine: GameEngine,
        preset: DifficultyPreset
    ): List<GridPos> {
        val playerExitDistance = chebyshevDistance(engine.maze.start, engine.maze.exit)
        val npcPositions = engine.npcs.mapTo(mutableSetOf()) { it.position }
        return allCells(engine.maze).filter {
            it != engine.maze.start &&
                it != engine.maze.exit &&
                it !in npcPositions &&
                chebyshevDistance(it, engine.maze.start) >= preset.adventurerPlayerSpawnBuffer &&
                abs(chebyshevDistance(it, engine.maze.exit) - playerExitDistance) <=
                ADVENTURER_EXIT_DISTANCE_TOLERANCE &&
                engine.navigator.bfsPath(it, engine.maze.exit).isNotEmpty()
        }
    }

    private fun allCells(maze: Maze): List<GridPos> = buildList {
        for (y in 0 until maze.height) {
            for (x in 0 until maze.width) add(GridPos(x, y))
        }
    }

    private fun chebyshevDistance(a: GridPos, b: GridPos): Int =
        maxOf(abs(a.x - b.x), abs(a.y - b.y))

    private companion object {
        const val SEED = 73L
        const val ADVENTURER_EXIT_DISTANCE_TOLERANCE = 2
    }
}
