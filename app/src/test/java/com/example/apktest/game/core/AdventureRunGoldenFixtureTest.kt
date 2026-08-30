package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Golden signatures for the Adventure controller's pre-feature behavior.
 *
 * These assertions intentionally use committed literal values rather than
 * comparing two executions of the same algorithm. Future route, elite, and
 * perk work can therefore detect accidental changes while feature gates are
 * disabled.
 */
class AdventureRunGoldenFixtureTest {

    @Test
    fun fixedSeeds_preserveCurrentPreparationAndRewardSequence() {
        GOLDEN_RUNS.forEach { fixture ->
            assertEquals(
                fixture.difficulty.name,
                fixture.expectedSignature,
                buildRunSignature(fixture.difficulty, fixture.runSeed)
            )
        }
    }

    @Test
    fun easyFixture_preservesIdempotencyDeathReplayRewardAndRehydration() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.EASY)
        val controller = AdventureRunController(config = config, runSeed = EASY_RUN_SEED)

        val firstPreparation = controller.prepareCurrentMaze()
        assertNotNull(firstPreparation)
        assertEquals(-5622680794312949107L, firstPreparation!!.seed)
        assertEquals(listOf(NpcPolicyType.PREDICTIVE_CHASE), firstPreparation.npcPolicies)
        assertEquals(firstPreparation, controller.prepareCurrentMaze())

        val death = controller.onPlayerDied()
        assertFalse(death.runOver)
        assertEquals(firstPreparation, controller.prepareCurrentMaze())

        val firstWin = controller.onMazeWon()
        assertEquals(
            listOf(PowerUpType.MAGNET, PowerUpType.FREEZE, PowerUpType.INVISIBILITY),
            firstWin.startingPowerUpCandidates
        )
        controller.applyStartingPowerUp(PowerUpType.MAGNET)
        val secondPreparation = controller.prepareCurrentMaze()!!
        assertEquals(-5583174335914499680L, secondPreparation.seed)
        assertEquals(PowerUpType.MAGNET, secondPreparation.startingPowerUp)
        assertEquals(listOf(NpcPolicyType.PREDICTIVE_CHASE), secondPreparation.npcPolicies)

        val persisted = AdventureRunStateSnapshot.fromState(controller.state, EASY_RUN_SEED)
        val restoredSnapshot = AdventureRunStateSnapshot.fromJson(persisted.toJson())
        assertNotNull(restoredSnapshot)
        val restoredController = AdventureRunController(
            config = config,
            initialState = restoredSnapshot!!.toState(),
            runSeed = restoredSnapshot.runSeed
        )
        assertEquals(secondPreparation, restoredController.prepareCurrentMaze())

        val replayDeath = restoredController.onPlayerDied()
        assertFalse(replayDeath.runOver)
        val replayPreparation = restoredController.prepareCurrentMaze()
        assertEquals(secondPreparation, replayPreparation)
        assertEquals(PowerUpType.MAGNET, replayPreparation!!.startingPowerUp)
    }

    private fun buildRunSignature(difficulty: DifficultyPreset, runSeed: Long): String {
        val controller = AdventureRunController(
            config = AdventureConfig.forDifficulty(difficulty),
            runSeed = runSeed
        )
        return buildString {
            appendLine(
                "initial|lives=${controller.state.livesRemaining}|" +
                    "unlocked=${controller.state.unlockedPlayerPolicies.names()}|" +
                    "status=${controller.state.status.name}"
            )
            repeat(controller.config.totalMazes) { zeroBasedIndex ->
                val mazeIndex = zeroBasedIndex + 1
                val startup = controller.prepareCurrentMaze()!!
                appendLine(
                    "start|$mazeIndex|seed=${startup.seed}|npcs=${startup.npcCount}|" +
                        "policies=${startup.npcPolicies.names()}|" +
                        "powerup=${startup.startingPowerUp?.name ?: "-"}"
                )

                val outcome = controller.onMazeWon(
                    elapsedSeconds = mazeIndex.toFloat(),
                    steps = mazeIndex * 10
                )
                appendLine(
                    "win|${outcome.mazeIndexCompleted}|bonus=${outcome.bonusLifeAwarded}|" +
                        "lives=${outcome.livesRemaining}|" +
                        "offers=${outcome.startingPowerUpCandidates.names()}|" +
                        "unlocked=${controller.state.unlockedPlayerPolicies.names()}|" +
                        "complete=${outcome.runComplete}|status=${controller.state.status.name}"
                )
                outcome.startingPowerUpCandidates.firstOrNull()?.let {
                    controller.applyStartingPowerUp(it)
                }
            }
            assertTrue(controller.state.currentMazeIndex == controller.config.totalMazes)
            assertEquals(AdventureStatus.WON, controller.state.status)
        }.trimEnd()
    }

    private fun Collection<Enum<*>>.names(): String = joinToString(",") { it.name }

    private data class GoldenRun(
        val difficulty: DifficultyPreset,
        val runSeed: Long,
        val expectedSignature: String
    )

    companion object {
        private const val EASY_RUN_SEED = 101L

        private val GOLDEN_RUNS = listOf(
            GoldenRun(
                difficulty = DifficultyPresets.EASY,
                runSeed = EASY_RUN_SEED,
                expectedSignature = """
                    initial|lives=5|unlocked=MANUAL|status=IN_PROGRESS
                    start|1|seed=-5622680794312949107|npcs=1|policies=PREDICTIVE_CHASE|powerup=-
                    win|1|bonus=false|lives=5|offers=MAGNET,FREEZE,INVISIBILITY|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|2|seed=-5583174335914499680|npcs=1|policies=PREDICTIVE_CHASE|powerup=MAGNET
                    win|2|bonus=false|lives=5|offers=BLAST,SPEED_UP,SHIELD|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|3|seed=-5525508206235123513|npcs=1|policies=DIRECT_CHASE|powerup=BLAST
                    win|3|bonus=true|lives=6|offers=TELEPORT,SLOW_TIME,MAGNET|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|4|seed=-5440965476687091718|npcs=2|policies=DIRECT_CHASE,PREDICTIVE_CHASE|powerup=TELEPORT
                    win|4|bonus=false|lives=6|offers=TELEPORT,SPEED_UP,SLOW_TIME|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|5|seed=-5401604158203410927|npcs=3|policies=PATROL_GUARD,PREDICTIVE_CHASE,PATROL_GUARD|powerup=TELEPORT
                    win|5|bonus=false|lives=6|offers=|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=true|status=WON
                """.trimIndent()
            ),
            GoldenRun(
                difficulty = DifficultyPresets.MEDIUM,
                runSeed = 202L,
                expectedSignature = """
                    initial|lives=3|unlocked=MANUAL|status=IN_PROGRESS
                    start|1|seed=-5622680794312949214|npcs=1|policies=PATROL_GUARD|powerup=-
                    win|1|bonus=false|lives=3|offers=TELEPORT,SPEED_UP,MAGNET|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|2|seed=-5583174335914499825|npcs=1|policies=PREDICTIVE_CHASE|powerup=TELEPORT
                    win|2|bonus=false|lives=3|offers=SLOW_TIME,MAGNET,SPEED_UP|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|3|seed=-5525508206235123608|npcs=1|policies=PREDICTIVE_CHASE|powerup=SLOW_TIME
                    win|3|bonus=true|lives=4|offers=TELEPORT,SPEED_UP,SHIELD|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|4|seed=-5440965476687091883|npcs=2|policies=DIRECT_CHASE,PATROL_GUARD|powerup=TELEPORT
                    win|4|bonus=false|lives=4|offers=MAGNET,INVISIBILITY,FREEZE|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|5|seed=-5401604158203410754|npcs=2|policies=DIRECT_CHASE,PREDICTIVE_CHASE|powerup=MAGNET
                    win|5|bonus=false|lives=4|offers=SPEED_UP,INVISIBILITY,TELEPORT|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|6|seed=-5199967976049105509|npcs=2|policies=DIRECT_CHASE,DIRECT_CHASE|powerup=SPEED_UP
                    win|6|bonus=true|lives=5|offers=SPEED_UP,SHIELD,BLAST|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|7|seed=-5124357678898609020|npcs=4|policies=PATROL_GUARD,PATROL_GUARD,PREDICTIVE_CHASE,PREDICTIVE_CHASE|powerup=SPEED_UP
                    win|7|bonus=false|lives=5|offers=|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=true|status=WON
                """.trimIndent()
            ),
            GoldenRun(
                difficulty = DifficultyPresets.HARD,
                runSeed = 303L,
                expectedSignature = """
                    initial|lives=1|unlocked=MANUAL|status=IN_PROGRESS
                    start|1|seed=-5622680794312948793|npcs=2|policies=PREDICTIVE_CHASE,PREDICTIVE_CHASE|powerup=-
                    win|1|bonus=false|lives=1|offers=BLAST,SPEED_UP,INVISIBILITY|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|2|seed=-5583174335914499862|npcs=2|policies=PREDICTIVE_CHASE,PATROL_GUARD|powerup=BLAST
                    win|2|bonus=false|lives=1|offers=BLAST,INVISIBILITY,MAGNET|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|3|seed=-5525508206235123315|npcs=2|policies=DIRECT_CHASE,PREDICTIVE_CHASE|powerup=BLAST
                    win|3|bonus=true|lives=2|offers=BLAST,FREEZE,SHIELD|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|4|seed=-5440965476687092048|npcs=3|policies=PATROL_GUARD,PATROL_GUARD,PATROL_GUARD|powerup=BLAST
                    win|4|bonus=false|lives=2|offers=TELEPORT,SLOW_TIME,FREEZE|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|5|seed=-5401604158203410597|npcs=3|policies=DIRECT_CHASE,DIRECT_CHASE,PATROL_GUARD|powerup=TELEPORT
                    win|5|bonus=false|lives=2|offers=FREEZE,BLAST,SPEED_UP|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|6|seed=-5199967976049105794|npcs=3|policies=PREDICTIVE_CHASE,PATROL_GUARD,PREDICTIVE_CHASE|powerup=FREEZE
                    win|6|bonus=true|lives=3|offers=SLOW_TIME,SHIELD,BLAST|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|7|seed=-5124357678898608799|npcs=4|policies=PREDICTIVE_CHASE,DIRECT_CHASE,PATROL_GUARD,PREDICTIVE_CHASE|powerup=SLOW_TIME
                    win|7|bonus=false|lives=3|offers=MAGNET,FREEZE,SLOW_TIME|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|8|seed=-5075703284026480124|npcs=4|policies=DIRECT_CHASE,DIRECT_CHASE,DIRECT_CHASE,PATROL_GUARD|powerup=MAGNET
                    win|8|bonus=false|lives=3|offers=SHIELD,SPEED_UP,BLAST|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=false|status=IN_PROGRESS
                    start|9|seed=-5018186692088181969|npcs=5|policies=PATROL_GUARD,PATROL_GUARD,PATROL_GUARD,DIRECT_CHASE,PATROL_GUARD|powerup=SHIELD
                    win|9|bonus=true|lives=4|offers=|unlocked=MANUAL,WALL_LEFT,BFS_EXIT,ASTAR_EXIT,PLEDGE,FLEE_TO_EXIT|complete=true|status=WON
                """.trimIndent()
            )
        )
    }
}
