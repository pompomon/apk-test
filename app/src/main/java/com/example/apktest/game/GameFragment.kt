package com.example.apktest.game

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.game.ui.HudState

class GameFragment : AndroidFragmentApplication() {
    private var game: MazeGame? = null

    private var pendingPlayerPolicy: PlayerPolicyType = PlayerPolicyType.MANUAL
    private var pendingNpcPolicy: NpcPolicyType = NpcPolicyType.DIRECT_CHASE
    private var pendingDifficulty: String = DifficultyPresets.MEDIUM.name
    private var pendingSnapshot: GameEngineSnapshot? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let { args ->
            args.getString(ARG_PLAYER_POLICY)?.let { name ->
                pendingPlayerPolicy = PlayerPolicyType.entries.firstOrNull { it.name == name }
                    ?: pendingPlayerPolicy
            }
            args.getString(ARG_NPC_POLICY)?.let { name ->
                pendingNpcPolicy = NpcPolicyType.entries.firstOrNull { it.name == name }
                    ?: pendingNpcPolicy
            }
            args.getString(ARG_DIFFICULTY)?.let { pendingDifficulty = it }
            args.getString(ARG_RESUME_SNAPSHOT_JSON)?.let { json ->
                pendingSnapshot = GameEngineSnapshot.fromJson(json)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val config = AndroidApplicationConfiguration().apply {
            useCompass = false
            useAccelerometer = false
            useGyroscope = false
            useImmersiveMode = true
        }

        val gameInstance = MazeGame()
        game = gameInstance
        val snapshot = pendingSnapshot
        if (snapshot != null) {
            // Apply the snapshot before the first render so the engine boots
            // straight into the resumed state (and so the fresh-game
            // countdown is suppressed by MazeGame).
            gameInstance.restoreSnapshot(snapshot)
        } else {
            gameInstance.setPlayerPolicy(pendingPlayerPolicy)
            gameInstance.setNpcPolicy(pendingNpcPolicy)
            gameInstance.setDifficulty(pendingDifficulty)
        }

        return initializeForView(gameInstance, config)
    }

    override fun onDestroyView() {
        game = null
        super.onDestroyView()
    }

    fun setPlayerPolicy(type: PlayerPolicyType) {
        pendingPlayerPolicy = type
        game?.setPlayerPolicy(type)
    }

    fun setNpcPolicy(type: NpcPolicyType) {
        pendingNpcPolicy = type
        game?.setNpcPolicy(type)
    }

    fun setDifficulty(name: String) {
        pendingDifficulty = name
        game?.setDifficulty(name)
    }

    fun queueManualMove(direction: Direction) {
        game?.queueManualMove(direction)
    }

    fun togglePause() {
        game?.togglePause()
    }

    fun restart() {
        game?.restart()
    }

    /**
     * Captures the engine's current snapshot synchronously (blocking until
     * the render thread runs the request, with a short timeout). Returns
     * `null` if the game isn't initialised or the render thread is starved.
     */
    fun captureSnapshot(): GameEngineSnapshot? = game?.snapshotBlocking()

    fun hudState(): HudState? = game?.hudState()

    companion object {
        const val ARG_PLAYER_POLICY = "arg_player_policy"
        const val ARG_NPC_POLICY = "arg_npc_policy"
        const val ARG_DIFFICULTY = "arg_difficulty"
        const val ARG_RESUME_SNAPSHOT_JSON = "arg_resume_snapshot_json"
    }
}
