package com.example.apktest.ui

import com.example.apktest.R
import com.example.apktest.game.core.AdventureRunState
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.GameEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureHudTextTest {
    private val formatter = AdventureHudText { id, args ->
        val label = when (id) {
            R.string.adventure_hud_primary -> "primary"
            R.string.adventure_hud_completed -> "completed"
            R.string.adventure_hud_streak -> "streak"
            R.string.adventure_hud_streak_description -> "bonus explanation"
            else -> error("Unexpected HUD resource")
        }
        "$label:${args.joinToString("|")}"
    }

    @Test
    fun primaryShowsProgressAndLivesSeparatelyFromCompletedStatistics() {
        val state = AdventureRunState(
            difficultyName = "Medium",
            currentMazeIndex = 2,
            livesRemaining = 4,
            totalElapsedSeconds = 125f,
            totalSteps = 1234,
            winStreakSinceLastBonus = 2
        )
        val text = formatter.format(state, 7)
        assertEquals("primary:3|7|4", text.primary)
        assertEquals("completed:02:05|1234", text.completedStats)
        assertEquals("streak:2|3", text.streak)
        assertEquals("bonus explanation:2|3", text.streakDescription)
    }

    @Test
    fun resumedAttemptIsNotAddedToSettledTotalsAndRenderingDoesNotMutateState() {
        val engine = GameEngine(DifficultyPresets.MEDIUM, 71L)
        val snapshot = engine.snapshot().copy(elapsedSeconds = 83f, steps = 270)
        val state = AdventureRunState(
            difficultyName = "Medium",
            totalElapsedSeconds = 60f,
            totalSteps = 100,
            currentMazeSnapshot = snapshot
        )
        val before = state.copy()
        repeat(3) {
            assertEquals("completed:01:00|100", formatter.format(state, 7).completedStats)
        }
        assertEquals(before, state)
        state.currentMazeSnapshot = null
        assertEquals("completed:01:00|100", formatter.format(state, 7).completedStats)
    }

    @Test
    fun finalProgressIsClampedAndLargeTotalsAreNotTruncated() {
        val state = AdventureRunState(
            difficultyName = "Medium",
            currentMazeIndex = 7,
            totalElapsedSeconds = 6000f,
            totalSteps = Int.MAX_VALUE
        )
        assertEquals("primary:7|7|1", formatter.format(state, 7).primary)
        assertTrue(formatter.format(state, 7).completedStats.contains("100:00|${Int.MAX_VALUE}"))
    }
}
