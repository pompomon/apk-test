package com.example.apktest

import com.example.apktest.game.core.AdventureRunState
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.RunPerkEffects
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkStack
import com.example.apktest.game.core.matchesAdventureMaze

/** Rejects an older GL capture that would re-arm a durably consumed run charge. */
internal object AdventurePerkSnapshotGuard {
    fun accepts(snapshot: GameEngineSnapshot, mazeSeed: Long?, perks: List<RunPerkStack>): Boolean {
        if (snapshot.seed != mazeSeed || !snapshot.hasValidRunPerkConfiguration()) return false
        fun stacks(id: RunPerkId) = perks.firstOrNull { it.id == id }?.stacks ?: 0
        val effects = snapshot.runPerkEffects
        if (effects.quickFeetStacks != stacks(RunPerkId.QUICK_FEET) ||
            effects.longerChargeStacks != stacks(RunPerkId.LONGER_CHARGE) ||
            effects.pocketMagnetStacks != stacks(RunPerkId.POCKET_MAGNET)) return false
        val secondWind = perks.firstOrNull { it.id == RunPerkId.SECOND_WIND }
        return if (snapshot.pendingConsumedRunPerk != null) {
            snapshot.pendingConsumedRunPerk == RunPerkId.SECOND_WIND &&
                secondWind != null && !effects.secondWindAvailable &&
                (snapshot.status == GameStatus.RUNNING || snapshot.status == GameStatus.PAUSED)
        } else {
            effects.secondWindAvailable == (secondWind != null && !secondWind.consumed)
        }
    }

    fun canCommitConsumption(snapshot: GameEngineSnapshot, state: AdventureRunState): Boolean =
        snapshot.pendingConsumedRunPerk == RunPerkId.SECOND_WIND &&
            accepts(snapshot, state.currentMazeSeed, state.runPerks) &&
            snapshot.matchesAdventureMaze(
                state.difficultyName, state.currentMazeSeed, state.currentMazeNpcCount,
                state.currentMazeNpcSpawnSpecs, state.activeRoute?.pickupLifetimeSeconds,
                RunPerkEffects.fromStacks(state.runPerks).copy(secondWindAvailable = false),
                secondWindConsumed = true
            )
}
