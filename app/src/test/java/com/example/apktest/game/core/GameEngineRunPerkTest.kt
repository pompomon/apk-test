package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineRunPerkTest {
    @Test
    fun quickFeetChangesOnlyPlayerCadenceAndMultipliesSpeedUp() {
        val plain = engine()
        val fast = engine(RunPerkEffects(quickFeetStacks = 3))
        plain.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        fast.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        plain.update(0.49f)
        fast.update(0.49f)
        assertEquals(1, plain.steps)
        assertEquals(2, fast.steps)
        assertEquals(4.6f, fast.hudState().playerSpeed, EPSILON)
        assertEquals(plain.hudState().npcSpeed, fast.hudState().npcSpeed, EPSILON)
        fast.applyStartingPowerUp(PowerUpType.SPEED_UP)
        assertEquals(9.2f, fast.hudState().playerSpeed, EPSILON)
    }

    @Test
    fun quickFeetAcceleratesManualCooldownWithoutChangingPathRanking() {
        val plain = engine()
        val fast = engine(RunPerkEffects(quickFeetStacks = 3))
        val path = plain.navigator.bfsPath(plain.player.position, plain.maze.exit)
        for (e in listOf(plain, fast)) {
            e.queueManualMove(direction(path[0], path[1]))
            e.queueManualMove(direction(path[1], path[2]))
            e.update(0.001f)
            e.update(0.23f)
        }
        assertEquals(path[1], plain.player.position)
        assertEquals(path[2], fast.player.position)
        for (policy in PlayerPolicyType.entries.filter { it != PlayerPolicyType.MANUAL }) {
            val normal = engine()
            val quicker = engine(RunPerkEffects(quickFeetStacks = 3))
            normal.setPlayerPolicy(policy)
            quicker.setPlayerPolicy(policy)
            repeat(5) {
                normal.update(1f / normal.hudState().playerSpeed + 0.00001f)
                quicker.update(1f / quicker.hudState().playerSpeed + 0.00001f)
                assertEquals("$policy path changed", normal.player.position, quicker.player.position)
            }
        }
    }

    @Test
    fun quickFeetDoesNotChangeAdventurerOrNpcMovement() {
        val plain = engine(npcCount = 1, adventurerCount = 1)
        val fast = engine(RunPerkEffects(quickFeetStacks = 3), npcCount = 1, adventurerCount = 1)
        repeat(5) {
            plain.update(0.1f)
            fast.update(0.1f)
        }
        assertEquals(plain.adventurers.map { it.position }, fast.adventurers.map { it.position })
        assertEquals(plain.npcs.map { it.position }, fast.npcs.map { it.position })
    }

    @Test
    fun longerChargeExtendsEveryPlayerCollectedTimedEffectOnceAndRefreshes() {
        for (type in PowerUpType.entries.filter { it.metadata.kind == PowerUpEffectKind.TIMED }) {
            val e = engine(RunPerkEffects(longerChargeStacks = 3))
            val events = mutableListOf<RunPerkEffectEvent>()
            e.onRunPerkEffectApplied = events::add
            collect(e, type)
            val duration = type.metadata.defaultDurationSeconds + 3f
            assertEquals(type.name, duration, remaining(e, type), EPSILON)
            e.update(0.25f)
            assertEquals(duration - 0.25f, remaining(e, type), EPSILON)
            collect(e, type)
            assertEquals(duration, remaining(e, type), EPSILON)
            assertEquals(
                listOf(
                    RunPerkEffectEvent(RunPerkId.LONGER_CHARGE, "power_up_duration_seconds", 3),
                    RunPerkEffectEvent(RunPerkId.LONGER_CHARGE, "power_up_duration_seconds", 3)
                ),
                events
            )
        }
    }

    @Test
    fun longerChargeDoesNotExtendStartingRewardsOrInstantEffects() {
        for (type in PowerUpType.entries) {
            val e = engine(RunPerkEffects(longerChargeStacks = 3))
            val events = mutableListOf<RunPerkEffectEvent>()
            e.onRunPerkEffectApplied = events::add
            e.applyStartingPowerUp(type)
            if (type.metadata.kind == PowerUpEffectKind.TIMED) {
                assertEquals(type.metadata.defaultDurationSeconds, remaining(e, type), EPSILON)
            } else {
                assertTrue(e.activePowerUps.isEmpty())
                collect(e, type)
                assertTrue(e.activePowerUps.isEmpty())
            }
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun longerChargeExcludesAdventurerEffectsIncludingSharedFreezeAndSlowTime() {
        for (type in PowerUpType.entries.filter { it.metadata.kind == PowerUpEffectKind.TIMED }) {
            val e = engine(RunPerkEffects(longerChargeStacks = 3), adventurerCount = 1)
            val events = mutableListOf<RunPerkEffectEvent>()
            e.onRunPerkEffectApplied = events::add
            val pos = GridPos(5, 5)
            installPickups(e, pickup(type, pos))
            e.simulateAdventurerArrivalForTest(0, pos)
            val snapshot = e.snapshot()
            val effects = if (type == PowerUpType.FREEZE || type == PowerUpType.SLOW_TIME) {
                snapshot.activeEffects
            } else snapshot.adventurerEffects.single().effects
            assertEquals(type.metadata.defaultDurationSeconds, effects.single().remainingSeconds!!, EPSILON)
            assertTrue(events.isEmpty())
        }
    }

    @Test
    fun longerChargeDoesNotExtendHostileFreezeOrGrantCollisionImmunity() {
        val e = engine(RunPerkEffects(longerChargeStacks = 3), npcCount = 1)
        val pos = GridPos(5, 5)
        installPickups(e, pickup(PowerUpType.FREEZE, pos))
        e.simulateNpcArrivalForTest(0, pos)
        assertEquals(5f, e.snapshot().npcInducedPlayerFreezeRemainingSeconds!!, EPSILON)
        assertFalse(e.isPlayerPowerUpTintActive(PowerUpType.FREEZE))
        val neighbor = e.maze.neighbors(e.player.position).first()
        e.npcs.single().position = neighbor
        e.update(1.001f)
        assertEquals(GameStatus.LOSE, e.status)
    }

    @Test
    fun magnetRadiusRequiresActiveMagnetAndCapsAtFourChebyshevCells() {
        for (stacks in 0..2) {
            val e = engine(RunPerkEffects(pocketMagnetStacks = stacks, longerChargeStacks = 2))
            e.player.position = GridPos(5, 5)
            val radius = 2 + stacks
            val inRange = GridPos(5 + radius, 5 + radius)
            val outOfRange = GridPos(5 + radius + 1, 5)
            installPickups(
                e, pickup(PowerUpType.SPEED_UP, inRange), pickup(PowerUpType.SHIELD, outOfRange)
            )
            e.update(0.01f)
            assertEquals(2, e.spawnedPowerUps.size)
            e.applyStartingPowerUp(PowerUpType.MAGNET)
            assertEquals(listOf(outOfRange), e.spawnedPowerUps.map { it.position })
            assertEquals(12f, remaining(e, PowerUpType.SPEED_UP), EPSILON)
            assertEquals(8f, remaining(e, PowerUpType.MAGNET), EPSILON)
        }
    }

    @Test
    fun pocketMagnetDoesNotChangeAdventurerMagnetRadiusOrPickupDuration() {
        val e = engine(RunPerkEffects(3, 3, 2), adventurerCount = 1)
        val center = GridPos(5, 5)
        val outside = GridPos(8, 5)
        installPickups(e, pickup(PowerUpType.MAGNET, center), pickup(PowerUpType.SPEED_UP, outside))
        e.simulateAdventurerArrivalForTest(0, center)
        assertEquals(listOf(outside), e.spawnedPowerUps.map { it.position })
        val effect = e.snapshot().adventurerEffects.single().effects.single()
        assertEquals(8f, effect.remainingSeconds!!, EPSILON)
    }

    @Test
    fun expandedMagnetPreservesTeleportOrderingAndStopsAtOldPosition() {
        val e = engine(RunPerkEffects(pocketMagnetStacks = 2), npcCount = 1)
        e.player.position = GridPos(5, 5)
        val oldPosition = e.player.position
        val farther = GridPos(9, 9)
        e.npcs.single().position = farther
        installPickups(
            e, pickup(PowerUpType.TELEPORT, GridPos(6, 5)), pickup(PowerUpType.SHIELD, farther)
        )
        e.applyStartingPowerUp(PowerUpType.MAGNET)
        assertNotEquals(oldPosition, e.player.position)
        assertEquals(listOf(farther), e.spawnedPowerUps.map { it.position })
        assertFalse(e.isPlayerPowerUpTintActive(PowerUpType.SHIELD))
    }

    @Test
    fun secondWindStopsRemainingManualAutoAdventurerAndNpcStepsInSameUpdate() {
        for (automated in listOf(false, true)) {
            val e = engine(RunPerkEffects(secondWindAvailable = true), 2, 1)
            val path = e.navigator.bfsPath(e.player.position, e.maze.exit)
            e.npcs[0].position = path[1]
            val untouchedNpc = e.npcs[1].position
            val untouchedAdventurer = e.adventurers.single().position
            if (automated) {
                e.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
                // The queued override forces the lethal step, then must not fall
                // through to further automated moves when the queue drains.
                e.queueManualMove(direction(path[0], path[1]))
            } else {
                e.queueManualMove(direction(path[0], path[1]))
                e.queueManualMove(direction(path[1], path[2]))
            }
            e.update(2f)
            assertEquals(GameStatus.RUNNING, e.status)
            assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
            assertEquals(path[1], e.player.position)
            assertEquals(1, e.steps)
            assertEquals(untouchedNpc, e.npcs[1].position)
            assertEquals(untouchedAdventurer, e.adventurers.single().position)
            assertFalse(e.runPerkEffects.secondWindAvailable)
            assertEquals(1f, remaining(e, PowerUpType.FREEZE), EPSILON)
        }
    }

    @Test
    fun secondWindStopsAutomaticLoopWhenStationaryPolicyMeetsCapture() {
        val e = engine(RunPerkEffects(secondWindAvailable = true), npcCount = 1)
        e.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        val policyField = GameEngine::class.java.getDeclaredField("playerPolicy")
        policyField.isAccessible = true
        var calls = 0
        policyField.set(e, object : PlayerPolicy {
            override fun nextMove(context: PlayerPolicyContext): Direction? {
                calls++
                return null
            }
        })
        e.npcs.single().position = e.player.position
        e.update(2f)
        assertEquals(1, calls)
        assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
    }

    @Test
    fun secondWindInterruptsNpcRosterBeforeAnotherNpcMoves() {
        val e = engine(RunPerkEffects(secondWindAvailable = true), npcCount = 2)
        e.npcs[0].position = e.maze.neighbors(e.player.position).first()
        val secondPosition = e.npcs[1].position
        e.update(1.01f)
        assertEquals(e.player.position, e.npcs[0].position)
        assertEquals(secondPosition, e.npcs[1].position)
        assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
        assertEquals(1f, remaining(e, PowerUpType.FREEZE), EPSILON)
    }

    @Test
    fun secondWindBarrierPreservesTimersInputsAndAllGameplayUntilMatchingAck() {
        val e = capturedEngine()
        val barrier = e.snapshot()
        val accumulators = accumulatorState(e)
        repeat(3) {
            e.update(100f)
            e.queueManualMove(Direction.EAST)
            e.queueManualMoveUntilBlocked(Direction.NORTH)
            e.togglePause()
            e.startCountdown()
            e.applyStartingPowerUp(PowerUpType.SPEED_UP)
            e.clearAdventureMazeConfig()
            e.configureAdventureMaze(0, emptyList())
            e.applyDifficulty(DifficultyPresets.EASY)
            e.setDifficulty(DifficultyPresets.HARD)
            e.setNpcPolicy(NpcPolicyType.PATROL_GUARD)
            e.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
            e.restart(777L)
            e.restore(barrier.copy(pendingConsumedRunPerk = null))
            assertEquals(barrier, e.snapshot())
            assertEquals(accumulators, accumulatorState(e))
        }
        assertFalse(e.acknowledgeRunPerkConsumption(e.currentSeed + 1, RunPerkId.SECOND_WIND))
        assertFalse(e.acknowledgeRunPerkConsumption(e.currentSeed, RunPerkId.QUICK_FEET))
        assertEquals(barrier, e.snapshot())
        assertTrue(e.acknowledgeRunPerkConsumption(e.currentSeed, RunPerkId.SECOND_WIND))
        assertFalse(e.acknowledgeRunPerkConsumption(e.currentSeed, RunPerkId.SECOND_WIND))
        assertFalse(e.runPerkEffects.secondWindAvailable)
        assertNull(e.pendingConsumedRunPerk)
        e.update(0.1f)
        assertEquals(barrier.elapsedSeconds + 0.1f, e.elapsedSeconds, EPSILON)
        assertEquals(0.9f, remaining(e, PowerUpType.FREEZE), EPSILON)
        e.restart(88L)
        assertFalse(e.runPerkEffects.secondWindAvailable)
    }

    @Test
    fun secondWindDoesNotConsumeOnExitOrExistingImmunity() {
        for (immunity in listOf(PowerUpType.SHIELD, PowerUpType.FREEZE, PowerUpType.INVISIBILITY)) {
            val e = engine(RunPerkEffects(secondWindAvailable = true), npcCount = 1)
            val destination = e.maze.neighbors(e.player.position).first()
            e.npcs.single().position = destination
            e.applyStartingPowerUp(immunity)
            e.queueManualMove(direction(e.player.position, destination))
            e.update(0.001f)
            assertNull(e.pendingConsumedRunPerk)
            assertTrue(e.runPerkEffects.secondWindAvailable)
            assertEquals(GameStatus.RUNNING, e.status)
        }
        val e = engine(RunPerkEffects(secondWindAvailable = true), npcCount = 1)
        val beforeExit = e.maze.neighbors(e.maze.exit).first()
        e.player.position = beforeExit
        e.npcs.single().position = e.maze.exit
        e.queueManualMove(direction(beforeExit, e.maze.exit))
        e.update(0.001f)
        assertEquals(GameStatus.WIN, e.status)
        assertNull(e.pendingConsumedRunPerk)
        assertTrue(e.runPerkEffects.secondWindAvailable)
    }

    @Test
    fun secondWindDoesNotPreventAdventurerExitLoss() {
        val e = engine(RunPerkEffects(secondWindAvailable = true), adventurerCount = 1)
        e.adventurers.single().position = e.maze.neighbors(e.maze.exit).first()
        e.update(0.4f)
        assertEquals(GameStatus.LOSE, e.status)
        assertNull(e.pendingConsumedRunPerk)
        assertTrue(e.runPerkEffects.secondWindAvailable)
    }

    @Test
    fun acknowledgedSecondWindCannotSaveASecondCaptureAfterPulseExpires() {
        val e = capturedEngine()
        assertTrue(e.acknowledgeRunPerkConsumption(e.currentSeed, RunPerkId.SECOND_WIND))
        e.update(1.001f)
        assertEquals(GameStatus.LOSE, e.status)
        assertNull(e.pendingConsumedRunPerk)
        assertFalse(e.runPerkEffects.secondWindAvailable)
    }

    @Test
    fun ghostModeDoesNotSuppressSecondWindCapture() {
        val e = engine(RunPerkEffects(secondWindAvailable = true), npcCount = 1)
        val target = e.maze.neighbors(e.player.position).first()
        e.npcs.single().position = target
        e.applyStartingPowerUp(PowerUpType.GHOST_MODE)
        e.queueManualMove(direction(e.player.position, target))
        e.update(0.001f)
        assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
    }

    @Test
    fun secondWindPreservesHostileFreezeAndIgnoresLongerChargeForPulse() {
        val e = engine(RunPerkEffects(longerChargeStacks = 3, secondWindAvailable = true), npcCount = 1)
        val pos = GridPos(5, 5)
        installPickups(e, pickup(PowerUpType.FREEZE, pos))
        e.simulateNpcArrivalForTest(0, pos)
        e.npcs.single().position = e.maze.neighbors(e.player.position).first()
        val events = mutableListOf<RunPerkEffectEvent>()
        e.onRunPerkEffectApplied = events::add
        e.update(1.01f)
        assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
        assertEquals(1f, remaining(e, PowerUpType.FREEZE), EPSILON)
        assertEquals(3.99f, e.snapshot().npcInducedPlayerFreezeRemainingSeconds!!, EPSILON)
        assertEquals(listOf(RunPerkEffectEvent(RunPerkId.SECOND_WIND, "freeze_pulse_seconds", 1)), events)
    }

    @Test
    fun neutralConfigPreservesClassicSimulationAndClearsOnDifficultyChange() {
        val plain = engine(npcCount = 1, adventurerCount = 1)
        val configured = engine(RunPerkEffects(3, 3, 2, true), 1, 1)
        configured.clearAdventureMazeConfig()
        assertEquals(RunPerkEffects(), configured.runPerkEffects)
        plain.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        configured.setPlayerPolicy(PlayerPolicyType.BFS_EXIT)
        repeat(20) {
            plain.update(0.05f)
            configured.update(0.05f)
            assertEquals(plain.snapshot(), configured.snapshot())
        }
        configured.configureAdventureMaze(1, emptyList(), runPerkEffects = RunPerkEffects(3, 3, 2, true))
        configured.applyDifficulty(DifficultyPresets.EASY)
        assertEquals(RunPerkEffects(), configured.runPerkEffects)
        assertEquals(4f, configured.hudState().playerSpeed, EPSILON)
    }

    @Test
    fun uniformNpcPolicySelectionClearsAdventurePerksAndRestoresClassicEffects() {
        val e = engine(RunPerkEffects(3, 3, 2, true), npcCount = 1)
        e.setNpcPolicy(NpcPolicyType.PATROL_GUARD)
        assertEquals(RunPerkEffects(), e.runPerkEffects)
        assertNull(e.npcCountOverride)
        assertNull(e.npcSpawnSpecs)
        assertEquals(4f, e.hudState().playerSpeed, EPSILON)
        collect(e, PowerUpType.SPEED_UP)
        assertEquals(10f, remaining(e, PowerUpType.SPEED_UP), EPSILON)
        assertEquals(8f, e.hudState().playerSpeed, EPSILON)
        e.restart(1234L)
        assertEquals(RunPerkEffects(), e.runPerkEffects)
        assertEquals(4f, e.hudState().playerSpeed, EPSILON)
    }

    private fun capturedEngine(): GameEngine {
        val e = engine(RunPerkEffects(2, 3, 1, true), npcCount = 1)
        val target = e.maze.neighbors(e.player.position).first()
        e.npcs.single().position = target
        e.queueManualMove(direction(e.player.position, target))
        e.update(0.001f)
        assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
        return e
    }

    private fun accumulatorState(engine: GameEngine): List<Any?> =
        listOf("playerAccumulator", "npcAccumulator", "powerUpRespawnAccumulator",
            "adventurerAccumulatorsById").map { name ->
            val field = GameEngine::class.java.getDeclaredField(name)
            field.isAccessible = true
            val value = field.get(engine)
            if (value is Map<*, *>) value.toMap() else value
        }

    private fun collect(engine: GameEngine, type: PowerUpType) {
        val target = engine.maze.neighbors(engine.player.position).first { it != engine.maze.exit }
        installPickups(engine, pickup(type, target))
        engine.queueManualMove(direction(engine.player.position, target))
        engine.update(0.001f)
    }

    private fun installPickups(engine: GameEngine, vararg pickups: GameEngineSnapshot.SpawnedPowerUpSnapshot) {
        engine.restore(engine.snapshot().copy(spawnedPowerUps = pickups.toList()))
    }

    private fun pickup(type: PowerUpType, pos: GridPos) =
        GameEngineSnapshot.SpawnedPowerUpSnapshot(type, pos.x, pos.y, 600f)

    private fun remaining(engine: GameEngine, type: PowerUpType): Float =
        engine.snapshot().activeEffects.single { it.type == type }.remainingSeconds!!

    private fun direction(from: GridPos, to: GridPos): Direction =
        requireNotNull(Direction.fromDelta(to.x - from.x, to.y - from.y))

    private fun engine(
        effects: RunPerkEffects = RunPerkEffects(),
        npcCount: Int = 0,
        adventurerCount: Int = 0
    ): GameEngine = GameEngine(
        DifficultyPreset(
            name = "RunPerksTest", mazeWidth = 14, mazeHeight = 16,
            npcCount = npcCount, playerMovesPerSecond = 4f, npcMovesPerSecond = 1f,
            npcVisionRange = 20, initialPowerUpTypes = emptyList(),
            powerUpPickupLifetimeSeconds = 600f, adventurerCount = adventurerCount,
            adventurerSpeedRatio = 0.8f, automaticPickupRadius = 0
        ),
        seed = 1234L
    ).also {
        if (effects != RunPerkEffects()) {
            it.configureAdventureMaze(npcCount, emptyList(), runPerkEffects = effects)
        }
    }

    companion object {
        private const val EPSILON = 0.0001f
    }
}
