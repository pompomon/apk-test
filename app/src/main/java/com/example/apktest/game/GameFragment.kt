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
            // Only consume a resume snapshot if MainActivity explicitly
            // marked this fragment instance as a resume by attaching
            // ARG_RESUME_SNAPSHOT_JSON. Prefer the in-memory parsed
            // handoff (set by MainActivity when the user taps Resume)
            // so we don't re-parse the JSON the state store already
            // parsed for validation. Fall back to parsing the JSON in
            // args for process-death recreations where the static field
            // has been wiped but the Bundle survives.
            val resumeJson = args.getString(ARG_RESUME_SNAPSHOT_JSON)
            if (resumeJson != null) {
                val handoff = pendingResumeSnapshot
                pendingResumeSnapshot = null
                pendingSnapshot = handoff ?: GameEngineSnapshot.fromJson(resumeJson)
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

    /**
     * Best-effort async snapshot: posts a request to the GL thread and
     * invokes [callback] there when the snapshot is ready. Returns
     * immediately without blocking the caller (intended for lifecycle
     * callbacks like [android.app.Activity.onPause] where blocking the UI
     * thread on the render loop risks ANR / jank). Returns `false` if the
     * game isn't initialised and the request couldn't be queued.
     */
    fun captureSnapshotAsync(callback: (GameEngineSnapshot) -> Unit): Boolean {
        val g = game ?: return false
        g.snapshotAsync(callback)
        return true
    }

    fun hudState(): HudState? = game?.hudState()

    companion object {
        const val ARG_PLAYER_POLICY = "arg_player_policy"
        const val ARG_NPC_POLICY = "arg_npc_policy"
        const val ARG_DIFFICULTY = "arg_difficulty"
        const val ARG_RESUME_SNAPSHOT_JSON = "arg_resume_snapshot_json"

        /**
         * In-memory handoff for an already-parsed resume snapshot. Set by
         * [com.example.apktest.MainActivity] just before attaching this
         * fragment so we can skip re-parsing the same JSON the state
         * store already parsed for validation. Consumed (and cleared)
         * exactly once in [onCreate]. The arg-based JSON path remains
         * the durable fallback for process-death restoration where this
         * field is wiped but the fragment's Bundle survives.
         *
         * Reads and writes are expected to happen on the main thread
         * (Activity / Fragment lifecycle callbacks); [Volatile] is used
         * only for visibility safety, not to support concurrent access.
         */
        @JvmStatic
        @Volatile
        var pendingResumeSnapshot: GameEngineSnapshot? = null
    }
}
