package com.example.apktest

import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.AdventureRunController
import com.example.apktest.game.core.AdventureRunState
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.PowerUpType
import com.example.apktest.game.core.RunPerkEffects
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkStack
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventurePerkSnapshotGuardTest {
    private val base = GameEngine(DifficultyPresets.MEDIUM, 29L).snapshot()
    private val ready = listOf(RunPerkStack(RunPerkId.SECOND_WIND, 1))
    private val used = ready.map { it.copy(consumed = true) }
    private val armed = base.copy(runPerkEffects = RunPerkEffects(secondWindAvailable = true))
    private val barrier = base.copy(
        pendingConsumedRunPerk = RunPerkId.SECOND_WIND,
        activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 1f))
    )

    @Test
    fun staleAutosaveCannotRearmConsumedPerkAfterCommit() {
        assertTrue(AdventurePerkSnapshotGuard.accepts(armed, base.seed, ready))
        assertFalse(AdventurePerkSnapshotGuard.accepts(armed, base.seed, used))
        assertTrue(AdventurePerkSnapshotGuard.accepts(base, base.seed, used))
        assertFalse(AdventurePerkSnapshotGuard.accepts(base, base.seed, ready))
    }

    @Test
    fun barrierReconcilesBothSidesOfCombinedConsumptionCommit() {
        assertTrue(AdventurePerkSnapshotGuard.accepts(barrier, base.seed, ready))
        assertTrue(AdventurePerkSnapshotGuard.accepts(barrier, base.seed, used))
        assertFalse(AdventurePerkSnapshotGuard.accepts(
            barrier.copy(status = GameStatus.PAUSED), base.seed, used
        ))
        assertFalse(AdventurePerkSnapshotGuard.accepts(barrier, base.seed, emptyList()))
        assertFalse(AdventurePerkSnapshotGuard.accepts(
            barrier.copy(status = GameStatus.LOSE), base.seed, ready
        ))
        assertFalse(AdventurePerkSnapshotGuard.accepts(
            barrier.copy(runPerkEffects = RunPerkEffects(secondWindAvailable = true)), base.seed, ready
        ))
    }

    @Test
    fun oldMazeAndOldStackCapturesCannotReplaceCurrentState() {
        val owned = listOf(RunPerkStack(RunPerkId.QUICK_FEET, 2))
        val matching = base.copy(runPerkEffects = RunPerkEffects(quickFeetStacks = 2))
        assertTrue(AdventurePerkSnapshotGuard.accepts(matching, base.seed, owned))
        assertFalse(AdventurePerkSnapshotGuard.accepts(base, base.seed, owned))
        assertFalse(AdventurePerkSnapshotGuard.accepts(matching, base.seed + 1, owned))
        assertFalse(AdventurePerkSnapshotGuard.accepts(matching, null, owned))
        assertTrue(AdventurePerkSnapshotGuard.accepts(base, base.seed, emptyList()))
    }

    @Test
    fun consumptionCannotSaveControllerChargeWithoutMatchingBarrierRoster() {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        val controller = AdventureRunController(
            config, AdventureRunState(config.difficulty.name, runPerks = ready), runSeed = 29L
        )
        val spec = controller.prepareCurrentMaze()!!
        val engine = GameEngine(spec.difficulty, spec.seed).apply {
            configureAdventureMaze(
                spec.npcCount, spec.npcPolicies, npcSpawnSpecs = spec.npcSpawnSpecs,
                runPerkEffects = spec.runPerkEffects
            )
            restart(spec.seed)
        }
        val snapshot = engine.snapshot().copy(
            runPerkEffects = spec.runPerkEffects.copy(secondWindAvailable = false),
            pendingConsumedRunPerk = RunPerkId.SECOND_WIND,
            activeEffects = barrier.activeEffects
        )
        assertTrue(AdventurePerkSnapshotGuard.canCommitConsumption(snapshot, controller.state))
        assertFalse(AdventurePerkSnapshotGuard.canCommitConsumption(
            snapshot.copy(npcCountOverride = spec.npcCount + 1), controller.state
        ))
        assertFalse(AdventurePerkSnapshotGuard.canCommitConsumption(
            snapshot.copy(activeEffects = emptyList()), controller.state
        ))
        assertFalse(controller.state.runPerks.single().consumed)
    }
}
