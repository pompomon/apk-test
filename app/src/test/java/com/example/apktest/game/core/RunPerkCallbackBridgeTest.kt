package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RunPerkCallbackBridgeTest {
    @Test
    fun registrationBeforeRestoreNotifiesFirstFrameExactlyOnceWithDetachedSnapshot() {
        val e = GameEngine()
        val bridge = RunPerkCallbackBridge(e)
        val snapshots = mutableListOf<GameEngineSnapshot>()
        bridge.setCallbacks(snapshots::add, null)
        bridge.dispatch()
        val pending = pendingSnapshot()
        e.restore(pending)
        bridge.onRestored()
        repeat(100) {
            e.update(0.016f)
            bridge.dispatch()
        }
        assertEquals(listOf(pending), snapshots)
        e.npcs[0].position = e.maze.exit
        assertEquals(pending.npcs, snapshots.single().npcs)
        assertTrue(e.acknowledgeRunPerkConsumption(pending.seed, RunPerkId.SECOND_WIND))
        bridge.dispatch()
        assertEquals(1, snapshots.size)
    }

    @Test
    fun lateRegistrationAndReplacementRenotifyPendingButDetachedObserverNeverRuns() {
        val e = GameEngine()
        e.restore(pendingSnapshot())
        val bridge = RunPerkCallbackBridge(e)
        bridge.onRestored()
        val old = mutableListOf<GameEngineSnapshot>()
        val current = mutableListOf<GameEngineSnapshot>()
        bridge.setCallbacks(old::add, null)
        bridge.setCallbacks(current::add, null)
        bridge.dispatch()
        assertTrue(old.isEmpty())
        assertEquals(1, current.size)
        bridge.setCallbacks(old::add, null)
        bridge.dispatch()
        assertEquals(1, old.size)
        bridge.setCallbacks(null, null)
        bridge.dispatch()
        assertEquals(1, old.size)
        bridge.setCallbacks(current::add, null)
        bridge.dispose()
        bridge.dispatch()
        bridge.setCallbacks(old::add, null)
        bridge.dispatch()
        assertEquals(1, current.size)
        assertEquals(1, old.size)
    }

    @Test
    fun startupEffectsEmitOnceAndNeverReplayOnRestoreOrReregistration() {
        val e = GameEngine()
        val bridge = RunPerkCallbackBridge(e)
        e.configureAdventureMaze(2, emptyList(), runPerkEffects = RunPerkEffects(3, 2, 2, true))
        e.restart(55L)
        bridge.onMazeStarted()
        val events = mutableListOf<RunPerkEffectEvent>()
        bridge.setCallbacks(null, events::add)
        repeat(10) { bridge.dispatch() }
        assertEquals(
            listOf(
                RunPerkEffectEvent(RunPerkId.QUICK_FEET, "player_speed_percent", 15),
                RunPerkEffectEvent(RunPerkId.POCKET_MAGNET, "magnet_radius_cells", 2)
            ),
            events
        )
        bridge.setCallbacks(null, events::add)
        bridge.dispatch()
        e.restore(e.snapshot())
        bridge.onRestored()
        assertEquals(2, events.size)
        assertNull(e.pendingConsumedRunPerk)
    }

    @Test
    fun effectEventsUseCurrentObserverAndStopAfterDisposal() {
        val e = GameEngine()
        val bridge = RunPerkCallbackBridge(e)
        val first = mutableListOf<RunPerkEffectEvent>()
        val second = mutableListOf<RunPerkEffectEvent>()
        val event = RunPerkEffectEvent(RunPerkId.LONGER_CHARGE, "power_up_duration_seconds", 1)
        bridge.setCallbacks(null, first::add)
        bridge.setCallbacks(null, second::add)
        e.onRunPerkEffectApplied?.invoke(event)
        bridge.dispose()
        e.onRunPerkEffectApplied?.invoke(event)
        assertTrue(first.isEmpty())
        assertEquals(listOf(event), second)
    }

    @Test
    fun replacementAndDisposalCannotCompleteDuringAnInFlightCallback() {
        for (delivery in listOf("pickup", "startup", "consumption")) {
            for (dispose in listOf(false, true)) {
                val e = GameEngine()
                val bridge = RunPerkCallbackBridge(e)
                if (delivery == "consumption") e.restore(pendingSnapshot())
                if (delivery == "startup") {
                    e.configureAdventureMaze(2, emptyList(), runPerkEffects = RunPerkEffects(1))
                }
                val entered = CountDownLatch(1)
                val release = CountDownLatch(1)
                val observerChanged = CountDownLatch(1)
                val onCallback: () -> Unit = {
                    entered.countDown()
                    release.await(5, TimeUnit.SECONDS)
                }
                bridge.setCallbacks({ onCallback() }, { onCallback() })
                val emitter = Thread {
                    when (delivery) {
                        "startup" -> bridge.onMazeStarted()
                        "consumption" -> bridge.dispatch()
                        else -> e.onRunPerkEffectApplied?.invoke(
                            RunPerkEffectEvent(RunPerkId.LONGER_CHARGE, "power_up_duration_seconds", 1)
                        )
                    }
                }
                val replacement = Thread {
                    if (dispose) bridge.dispose() else bridge.setCallbacks(null, null)
                    observerChanged.countDown()
                }
                emitter.start()
                try {
                    assertTrue(entered.await(2, TimeUnit.SECONDS))
                    replacement.start()
                    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
                    while (replacement.state != Thread.State.BLOCKED &&
                        observerChanged.count > 0 && System.nanoTime() < deadline
                    ) {
                        Thread.yield()
                    }
                    assertEquals("$delivery dispose=$dispose", Thread.State.BLOCKED, replacement.state)
                    assertEquals(1L, observerChanged.count)
                } finally {
                    release.countDown()
                    emitter.join(2_000)
                    replacement.join(2_000)
                }
                assertEquals(0L, observerChanged.count)
                bridge.dispose()
            }
        }
    }

    private fun pendingSnapshot(): GameEngineSnapshot = GameEngine(
        DifficultyPresets.MEDIUM, 1234L
    ).snapshot().copy(
        runPerkEffects = RunPerkEffects(2, 1, 1, false),
        pendingConsumedRunPerk = RunPerkId.SECOND_WIND,
        activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 1f))
    )
}
