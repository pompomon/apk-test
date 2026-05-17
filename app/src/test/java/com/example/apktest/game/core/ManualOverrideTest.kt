package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOverrideTest {
    private val seed = 1234L

    /**
     * Queueing a manual move under an automated policy applies that
     * direction on the next step, even though the policy would have chosen
     * a different one. The override window must arm itself (HUD value
     * positive).
     */
    @Test
    fun manualMoveUnderBfsPolicy_appliesManualDirectionAndArmsOverride() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        val start = engine.player.position

        // Pick a manual direction that is walkable from the start: prefer
        // NORTH so the test reads obviously, but fall back to any other
        // walkable neighbour if NORTH happens not to be reachable in this
        // seeded maze.
        val walkable = Direction.NORTH.takeIf { engine.maze.canMove(start, it) }
            ?: Direction.entries.firstOrNull { engine.maze.canMove(start, it) }
            ?: error("No walkable neighbour from start in seeded maze")
        engine.queueManualMove(walkable)

        // HUD reflects the override before any time has elapsed.
        val hud = engine.hudState()
        assertNotNull("manual override HUD entry should be set", hud.manualOverrideRemainingSeconds)
        assertTrue(
            "override should be ~MANUAL_OVERRIDE_DURATION_SECONDS",
            hud.manualOverrideRemainingSeconds!! > 2.9f
        )

        // Step the engine just long enough to consume one player tick.
        val tick = 1f / 6f + 0.001f
        engine.update(tick)
        assertEquals(start.moved(walkable), engine.player.position)
    }

    /**
     * Once the 3 s window expires, the next player tick is decided by the
     * active automated policy again (so the BFS direction is restored).
     */
    @Test
    fun overrideExpiresAfterDurationAndPolicyResumes() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        val start = engine.player.position
        val walkable = Direction.NORTH.takeIf { engine.maze.canMove(start, it) }
            ?: Direction.entries.firstOrNull { engine.maze.canMove(start, it) }
            ?: error("No walkable neighbour from start in seeded maze")
        engine.queueManualMove(walkable)

        // Use one large delta so elapsedSeconds is guaranteed to surpass
        // the override window even if the BFS player races to the exit
        // mid-tick and the engine early-returns on WIN. elapsedSeconds is
        // bumped unconditionally at the top of update().
        engine.update(GameEngine.MANUAL_OVERRIDE_DURATION_SECONDS + 0.5f)
        assertEquals(0f, engine.manualOverrideRemainingSeconds, 0.0001f)
        assertEquals(null, engine.hudState().manualOverrideRemainingSeconds)
    }

    /**
     * [GameEngine.restart] must clear the override so a fresh run can't
     * inherit a stale manual lockout.
     */
    @Test
    fun restartClearsOverride() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        engine.queueManualMove(Direction.NORTH)
        assertTrue(engine.manualOverrideRemainingSeconds > 0f)

        engine.restart(seed)
        assertEquals(0f, engine.manualOverrideRemainingSeconds, 0.0001f)
    }

    /**
     * Switching the player policy (e.g., from BFS to ASTAR_EXIT) should
     * also drop the override and clear the manual queue.
     */
    @Test
    fun setPlayerPolicyClearsOverride() {
        val engine = GameEngine(testPreset(), seed)
        engine.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        engine.queueManualMove(Direction.NORTH)
        assertTrue(engine.manualOverrideRemainingSeconds > 0f)

        engine.setPlayerPolicy(PlayerPolicyType.ASTAR_EXIT)
        assertEquals(0f, engine.manualOverrideRemainingSeconds, 0.0001f)
    }

    private fun assertNotNull(msg: String, value: Any?) {
        if (value == null) throw AssertionError(msg)
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
