package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineTest {
    private val seed = 1234L

    @Test
    fun update_doesNothingWhenPaused() {
        val engine = GameEngine(DifficultyPresets.EASY, seed)
        engine.togglePause()
        assertEquals(GameStatus.PAUSED, engine.status)

        engine.update(5f)

        assertEquals(0f, engine.elapsedSeconds, 0.0001f)
        assertEquals(0, engine.steps)
    }

    @Test
    fun togglePause_switchesBetweenRunningAndPaused() {
        val engine = GameEngine(DifficultyPresets.EASY, seed)
        assertEquals(GameStatus.RUNNING, engine.status)

        engine.togglePause()
        assertEquals(GameStatus.PAUSED, engine.status)

        engine.togglePause()
        assertEquals(GameStatus.RUNNING, engine.status)
    }

    @Test
    fun togglePause_blocksTimeAndStepAdvancement() {
        val engine = GameEngine(DifficultyPresets.EASY, seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        engine.togglePause()

        engine.update(2f)

        assertEquals(GameStatus.PAUSED, engine.status)
        assertEquals(0f, engine.elapsedSeconds, 0.0001f)
        assertEquals(0, engine.steps)
    }

    @Test
    fun update_advancesElapsedTimeAndStepsWhenRunning() {
        val engine = GameEngine(DifficultyPresets.EASY, seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)

        engine.update(1f)

        assertTrue(engine.elapsedSeconds > 0f)
        assertTrue(engine.steps > 0)
    }

    @Test
    fun queueManualMove_isIgnoredWhenPolicyIsNotManual() {
        val engine = GameEngine(DifficultyPresets.EASY, seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        val before = engine.player.position

        repeat(50) { engine.queueManualMove(Direction.NORTH) }
        // No update call: queued moves should have been discarded entirely.
        // Switching back to MANUAL must not replay the dropped inputs.
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        engine.update(1f)

        // Engine state must not have moved due to the dropped queued inputs.
        assertEquals(before, engine.player.position)
    }

    @Test
    fun restart_withSameSeed_isDeterministic() {
        val a = GameEngine(DifficultyPresets.EASY, seed)
        val b = GameEngine(DifficultyPresets.EASY, seed)

        assertEquals(a.maze.start, b.maze.start)
        assertEquals(a.maze.exit, b.maze.exit)
        assertEquals(
            a.npcs.map { it.position },
            b.npcs.map { it.position }
        )
    }

    @Test
    fun restart_withDifferentSeed_changesLayout() {
        val engine = GameEngine(DifficultyPresets.EASY, seed)
        val originalNpcs = engine.npcs.map { it.position }

        engine.restart(seed + 1)

        // Highly unlikely both NPC sets match for different seeds on the same preset grid.
        assertNotEquals(originalNpcs, engine.npcs.map { it.position })
    }
}
