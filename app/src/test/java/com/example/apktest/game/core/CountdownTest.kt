package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountdownTest {
    private val seed = 1234L

    @Test
    fun freshEngine_hasNoCountdownUntilStarted() {
        val engine = GameEngine(testPreset(), seed)
        assertEquals(0f, engine.countdownRemainingSeconds, 0.0001f)
        assertNull(engine.hudState().countdownRemainingSeconds)
    }

    @Test
    fun startCountdown_armsTheTimer() {
        val engine = GameEngine(testPreset(), seed)
        engine.startCountdown()
        assertEquals(GameEngine.COUNTDOWN_DEFAULT_SECONDS, engine.countdownRemainingSeconds, 0.0001f)
        assertEquals(
            GameEngine.COUNTDOWN_DEFAULT_SECONDS,
            engine.hudState().countdownRemainingSeconds!!,
            0.0001f
        )
    }

    /**
     * While counting down, [GameEngine.update] consumes the delta but
     * doesn't advance [GameEngine.elapsedSeconds] or move the player. This
     * lets the renderer overlay 3 / 2 / 1 / GO! while the maze sits
     * statically.
     */
    @Test
    fun countdown_freezesSimulationProgress() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        engine.startCountdown()

        val startPos = engine.player.position
        engine.update(1f)

        assertEquals(0f, engine.elapsedSeconds, 0.0001f)
        assertEquals(0, engine.steps)
        assertEquals(startPos, engine.player.position)
        assertTrue(engine.countdownRemainingSeconds > 1.9f)
    }

    /**
     * After the countdown elapses, subsequent [GameEngine.update] calls
     * advance the simulation normally again.
     */
    @Test
    fun countdown_releasesAfterDuration() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        engine.startCountdown()

        engine.update(GameEngine.COUNTDOWN_DEFAULT_SECONDS + 0.001f)
        // After the countdown, a follow-up tick should advance time/steps.
        engine.update(1f)
        assertTrue("elapsedSeconds should advance after countdown", engine.elapsedSeconds > 0f)
        assertTrue("steps should advance after countdown", engine.steps > 0)
    }

    /**
     * [GameEngine.restore] must not re-arm the countdown: resumed games
     * skip the 3-2-1 because the player already saw the layout.
     */
    @Test
    fun restore_doesNotReTriggerCountdown() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        engine.startCountdown()
        // Run past countdown so subsequent snapshot is mid-game.
        engine.update(GameEngine.COUNTDOWN_DEFAULT_SECONDS + 0.5f)
        val snap = engine.snapshot()

        val other = GameEngine(testPreset(), seed + 1)
        other.startCountdown()
        other.restore(snap)
        assertEquals(0f, other.countdownRemainingSeconds, 0.0001f)
    }

    private fun testPreset(): DifficultyPreset = DifficultyPreset(
        name = "Test",
        mazeWidth = 12,
        mazeHeight = 16,
        npcCount = 0,
        playerMovesPerSecond = 6f,
        npcMovesPerSecond = 1f,
        npcVisionRange = 3,
        balanceRule = NpcSpeedBalanceRule.NPC_MUST_BE_SLOWER_THAN_PLAYER,
        powerUpPickupLifetimeSeconds = 600f,
        powerUpRespawnIntervalSeconds = null,
        initialPowerUpTypes = emptyList(),
        powerUpExpirationStaggerSeconds = 0f
    )
}
