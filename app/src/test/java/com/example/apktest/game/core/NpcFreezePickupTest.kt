package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the "NPC-picked FREEZE freezes the player" mechanic.
 *
 * Symmetric to the existing player-picked FREEZE behaviour
 * (`GameEngineTest.freeze_freezesNpcs`): when any NPC arrives on a FREEZE
 * pickup, the *player* is the entity that can't move for the duration, while
 * NPCs continue moving normally. The NPC-induced freeze does NOT grant the
 * player collision immunity — being caught while frozen still ends the game.
 */
class NpcFreezePickupTest {
    private val seed = 1234L

    @Test
    fun npcArrivingOnFreeze_freezesPlayer_andConsumesPickup() {
        val engine = newEngine()
        val pickup = engine.spawnedPowerUps.first { it.type == PowerUpType.FREEZE }

        engine.simulateNpcArrivalForTest(0, pickup.position)

        // Pickup consumed from the map.
        assertFalse(
            "FREEZE pickup must be removed from map after NPC arrival",
            engine.spawnedPowerUps.any { it.position == pickup.position }
        )
        // HUD surfaces the new effect as "Frozen ..."
        assertTrue(
            "HUD should advertise NPC-induced FREEZE as a Frozen effect",
            engine.hudState().activePowerUps.any { it.startsWith("Frozen") }
        )

        // Player can't move while frozen.
        val playerBefore = engine.player.position
        val openDir = Direction.entries.first { engine.maze.canMove(playerBefore, it) }
        engine.queueManualMove(openDir)
        // Half a second at player speed 6/s would normally be 3 moves.
        engine.update(0.5f)
        assertEquals(
            "Player must not move while NPC-induced FREEZE is active",
            playerBefore,
            engine.player.position
        )
    }

    @Test
    fun npcInducedFreeze_doesNotPreventLossOnCollision() {
        val engine = newEngine()
        val pickup = engine.spawnedPowerUps.first { it.type == PowerUpType.FREEZE }
        engine.simulateNpcArrivalForTest(0, pickup.position)

        // Place the player on the NPC; tick at least one full NPC interval so
        // the NPC loop fires and evaluateEndConditions detects the collision.
        engine.player.position = engine.npcs[0].position
        engine.update(1f / engine.difficulty.npcMovesPerSecond + 0.01f)

        assertEquals(GameStatus.LOSE, engine.status)
    }

    @Test
    fun npcsKeepMovingWhilePlayerIsFrozenByNpcFreeze() {
        val engine = newEngine(npcCount = 1)
        val pickup = engine.spawnedPowerUps.first { it.type == PowerUpType.FREEZE }
        engine.simulateNpcArrivalForTest(0, pickup.position)

        val npcBefore = engine.npcs[0].position
        // Player stays at maze.start (default) so collision/WIN can't trigger
        // accidentally during the tick window.

        // Tick enough wall-clock for several NPC steps.
        engine.update(3f / engine.difficulty.npcMovesPerSecond + 0.01f)

        assertNotEquals(
            "NPCs must keep moving while NPC-induced FREEZE is active",
            npcBefore,
            engine.npcs[0].position
        )
    }

    @Test
    fun playerResumesMovingAfterFreezeExpires_withoutBurstingQueuedMoves() {
        val engine = newEngine()
        val pickup = engine.spawnedPowerUps.first { it.type == PowerUpType.FREEZE }
        engine.simulateNpcArrivalForTest(0, pickup.position)

        // Queue several manual moves while frozen — must NOT all fire when freeze
        // ends; the player accumulator is drained while frozen (mirror of NPC
        // accumulator gating during player-picked FREEZE).
        val openDir = Direction.entries.first { engine.maze.canMove(engine.player.position, it) }
        repeat(5) { engine.queueManualMove(openDir) }

        // Detach NPCs so wandering can't collide with the player or pick up
        // additional FREEZE pickups during the tick loop.
        engine.npcs.clear()

        val stepsBefore = engine.steps
        val freezeDuration = PowerUpType.FREEZE.metadata.defaultDurationSeconds
        // Step through freeze in small ticks so the accumulator-gating math is
        // exercised the same way a real render loop would feed deltas.
        val tickStep = 0.05f
        val tailSeconds = 0.3f
        var elapsed = 0f
        while (elapsed < freezeDuration + tailSeconds && engine.status == GameStatus.RUNNING) {
            engine.update(tickStep)
            elapsed += tickStep
        }

        assertFalse(
            "Frozen entry must be gone from HUD after expiration",
            engine.hudState().activePowerUps.any { it.startsWith("Frozen") }
        )

        // Only the moves whose post-thaw time slice fits should have fired (at
        // most ceil(tail * playerSpeed) + 1 for floating-point slack).
        val maxAllowed = (tailSeconds * engine.difficulty.playerMovesPerSecond).toInt() + 1
        val newSteps = engine.steps - stepsBefore
        assertTrue(
            "Player should not burst-consume queued moves after thaw (took $newSteps, allowed <= $maxAllowed)",
            newSteps <= maxAllowed
        )
    }

    @Test
    fun secondNpcPickupRefreshesFrozenRemainingTime() {
        val engine = newEngine(npcCount = 2)
        val firstFreeze = engine.spawnedPowerUps.first { it.type == PowerUpType.FREEZE }

        engine.simulateNpcArrivalForTest(0, firstFreeze.position)
        val initialRemaining = remainingFrozen(engine)
        assertTrue("First pickup should leave a positive remaining duration", initialRemaining > 0f)

        // Locate the second FREEZE pickup now so we have a stable reference
        // before advancing time.
        val secondFreeze = engine.spawnedPowerUps.firstOrNull { it.type == PowerUpType.FREEZE }
            ?: error("Expected a second FREEZE pickup on the map for refresh test")

        // Temporarily detach NPCs so the time-advance update doesn't have them
        // wander onto and accidentally collect the second FREEZE pickup. We
        // re-attach NPC #1 right before the second arrival.
        val savedNpcs = engine.npcs.toList()
        engine.npcs.clear()
        val freezeDuration = PowerUpType.FREEZE.metadata.defaultDurationSeconds
        engine.update(freezeDuration - 0.5f)
        engine.npcs.addAll(savedNpcs)

        val nearlyExpired = remainingFrozen(engine)
        assertTrue(
            "Frozen should be near expiration before refresh (was $nearlyExpired)",
            nearlyExpired in 0f..1f
        )

        engine.simulateNpcArrivalForTest(1, secondFreeze.position)
        val refreshed = remainingFrozen(engine)
        assertTrue(
            "Re-pickup must refresh FREEZE duration (was $nearlyExpired, now $refreshed)",
            refreshed > nearlyExpired
        )
    }

    // ---- helpers --------------------------------------------------------

    private fun newEngine(npcCount: Int = 1): GameEngine {
        val preset = DifficultyPreset(
            name = "TestNpcFreeze",
            mazeWidth = 14,
            mazeHeight = 20,
            npcCount = npcCount,
            playerMovesPerSecond = 6f,
            npcMovesPerSecond = 4f,
            npcVisionRange = 4,
            powerUpPickupLifetimeSeconds = 600f,
            powerUpExpirationStaggerSeconds = 0f,
            powerUpRespawnIntervalSeconds = null,
            // Need at least one FREEZE to exist on the map for these tests.
            initialPowerUpTypes = PowerUpType.entries +
                PowerUpType.FREEZE // extra FREEZE so the refresh test has a second one
        )
        val engine = GameEngine(preset, seed)
        engine.setPlayerPolicy(PlayerPolicyType.MANUAL)
        return engine
    }

    /** Parses the remaining seconds out of the HUD "Frozen X.Ys" entry, or 0 if absent. */
    private fun remainingFrozen(engine: GameEngine): Float {
        val label = engine.hudState().activePowerUps.firstOrNull { it.startsWith("Frozen") }
            ?: return 0f
        // Locale-tolerant parse (default-locale `%.1f` may emit a comma decimal).
        val tail = label.removePrefix("Frozen").trim().removeSuffix("s").replace(',', '.')
        return tail.toFloatOrNull() ?: 0f
    }
}
