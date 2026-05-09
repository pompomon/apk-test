package com.example.apktest.game

import com.badlogic.gdx.ApplicationAdapter
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.game.render.MazeRenderer
import com.example.apktest.game.ui.HudState

class MazeGame : ApplicationAdapter() {
    private val renderer = MazeRenderer()
    private val engine = GameEngine(difficultyPreset = DifficultyPresets.MEDIUM)
    private val commands = ArrayDeque<(GameEngine) -> Unit>()

    @Volatile
    private var lastHudState: HudState = engine.hudState()

    private var accumulator = 0f

    override fun render() {
        drainCommands()

        val frameTime = com.badlogic.gdx.Gdx.graphics.deltaTime.coerceAtMost(0.25f)
        accumulator += frameTime

        var stepped = false
        while (accumulator >= FIXED_TIMESTEP) {
            engine.update(FIXED_TIMESTEP)
            accumulator -= FIXED_TIMESTEP
            stepped = true
        }

        renderer.render(engine)
        if (stepped) {
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

    fun queueManualMove(direction: Direction) = enqueue { it.queueManualMove(direction) }

    fun togglePause() = enqueue { it.togglePause() }

    fun restart() = enqueue { it.restart() }

    fun hudState(): HudState = lastHudState

    private fun enqueue(command: (GameEngine) -> Unit) {
        synchronized(commands) {
            commands.add(command)
        }
    }

    private fun drainCommands() {
        val dequeuedCommands = synchronized(commands) {
            if (commands.isEmpty()) return
            val queue = ArrayDeque<(GameEngine) -> Unit>(commands.size)
            while (commands.isNotEmpty()) {
                queue.add(commands.removeFirst())
            }
            queue
        }
        while (dequeuedCommands.isNotEmpty()) {
            dequeuedCommands.removeFirst().invoke(engine)
        }
    }

    companion object {
        private const val FIXED_TIMESTEP = 1f / 60f
    }
}
