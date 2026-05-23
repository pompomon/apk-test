package com.example.apktest.game

import com.badlogic.gdx.ApplicationAdapter
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.game.core.PowerUpType
import com.example.apktest.game.render.MazeRenderer
import com.example.apktest.game.ui.HudState
import java.util.concurrent.atomic.AtomicReference

class MazeGame : ApplicationAdapter() {
    private val renderer = MazeRenderer()
    private val engine = GameEngine(difficultyPreset = DifficultyPresets.MEDIUM)
    private val commands = ArrayDeque<(GameEngine) -> Unit>()

    @Volatile
    private var lastHudState: HudState = engine.hudState()

    private var accumulator = 0f
    /**
     * Set to `true` from any thread after [restoreSnapshot] has been queued
     * so the next [render] tick can suppress the default "arm countdown for
     * a fresh game" behaviour. Resumed games skip the 3-2-1.
     */
    @Volatile
    private var restorePending = false
    private var initialCountdownArmed = false

    override fun render() {
        val ranCommand = drainCommands()
        if (!initialCountdownArmed && !restorePending) {
            engine.startCountdown()
            initialCountdownArmed = true
        }
        if (restorePending) {
            // Restore happened this frame; suppress the fresh-game countdown
            // permanently for this engine instance.
            initialCountdownArmed = true
            restorePending = false
        }

        val frameTime = com.badlogic.gdx.Gdx.graphics.deltaTime.coerceAtMost(0.25f)
        accumulator += frameTime

        var stepped = false
        while (accumulator >= FIXED_TIMESTEP) {
            engine.update(FIXED_TIMESTEP)
            accumulator -= FIXED_TIMESTEP
            stepped = true
        }

        renderer.render(engine)
        if (stepped || ranCommand) {
            lastHudState = engine.hudState()
        }
    }

    override fun resize(width: Int, height: Int) {
        renderer.resize(width, height)
    }

    override fun dispose() {
        renderer.dispose()
    }

    fun setPlayerPolicy(type: PlayerPolicyType) = enqueue { it.setPlayerPolicy(type) }

    fun setNpcPolicy(type: NpcPolicyType) = enqueue { it.setNpcPolicy(type) }

    fun setDifficulty(name: String) = enqueue { it.setDifficulty(DifficultyPresets.byName(name)) }

    /**
     * Adventure-mode entry point. Applies the per-maze configuration
     * (NPC count + per-NPC policy list + active player policy + difficulty)
     * on the GL thread, then restarts the engine with the supplied seed
     * so the next render tick spawns into the configured maze. Also
     * re-arms the 3-2-1 countdown so the player gets the usual
     * "assess the layout" window before NPCs start moving.
     */
    fun configureAdventureMaze(
        seed: Long,
        difficulty: String,
        playerPolicy: PlayerPolicyType,
        npcCount: Int,
        npcPolicies: List<NpcPolicyType>,
        startingPowerUp: PowerUpType? = null
    ) = enqueue { engine ->
        engine.applyDifficulty(DifficultyPresets.byName(difficulty))
        engine.setPlayerPolicy(playerPolicy)
        engine.configureAdventureMaze(npcCount, npcPolicies)
        engine.restart(seed)
        engine.applyStartingPowerUp(startingPowerUp)
        engine.startCountdown()
    }

    fun queueManualMove(direction: Direction) = enqueue { it.queueManualMove(direction) }

    fun togglePause() = enqueue { it.togglePause() }

    fun restart() = enqueue {
        it.restart()
        // Re-arm the countdown for the new run so the player gets the
        // 3-2-1 before NPCs start moving again.
        it.startCountdown()
    }

    /**
     * Replace engine state with the contents of [snapshot] on the next
     * render tick. Safe to call from any thread.
     */
    fun restoreSnapshot(snapshot: GameEngineSnapshot) {
        restorePending = true
        enqueue { it.restore(snapshot) }
    }

    /**
     * Captures the engine's current snapshot on the GL thread and returns
     * it via [callback]. The callback runs on the GL thread (next render
     * pass after the request is queued).
     */
    fun snapshotAsync(callback: (GameEngineSnapshot) -> Unit) = enqueue {
        callback(it.snapshot())
    }

    /**
     * Block-and-wait variant of [snapshotAsync] for callers that need the
     * snapshot synchronously (e.g., persistence before activity finish).
     * Times out after [SNAPSHOT_TIMEOUT_MS] to avoid wedging the UI thread
     * if the render loop is starved.
     */
    fun snapshotBlocking(timeoutMs: Long = SNAPSHOT_TIMEOUT_MS): GameEngineSnapshot? {
        val holder = AtomicReference<GameEngineSnapshot?>(null)
        val latch = java.util.concurrent.CountDownLatch(1)
        snapshotAsync {
            holder.set(it)
            latch.countDown()
        }
        return try {
            if (latch.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                holder.get()
            } else null
        } catch (_: InterruptedException) {
            // Preserve interrupt status for callers further up the stack and
            // signal "no snapshot available" rather than propagating the
            // checked exception (which would crash UI callers).
            Thread.currentThread().interrupt()
            null
        }
    }

    fun hudState(): HudState = lastHudState

    private fun enqueue(command: (GameEngine) -> Unit) {
        synchronized(commands) {
            commands.add(command)
        }
    }

    private fun drainCommands(): Boolean {
        val dequeuedCommands = synchronized(commands) {
            if (commands.isEmpty()) return false
            val queue = ArrayDeque<(GameEngine) -> Unit>(commands.size)
            while (commands.isNotEmpty()) {
                queue.add(commands.removeFirst())
            }
            queue
        }
        while (dequeuedCommands.isNotEmpty()) {
            dequeuedCommands.removeFirst().invoke(engine)
        }
        return true
    }

    companion object {
        private const val FIXED_TIMESTEP = 1f / 60f
        private const val SNAPSHOT_TIMEOUT_MS = 500L
    }
}
