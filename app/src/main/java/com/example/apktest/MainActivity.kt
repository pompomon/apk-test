package com.example.apktest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ToggleButton
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.game.core.automatedPlayerPolicies
import com.example.apktest.ui.GameMenuPopover
import com.example.apktest.ui.GameInputController
import com.example.apktest.ui.LegendDialog
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {
    private var menuPopover: GameMenuPopover? = null
    private lateinit var menuButton: ImageButton
    private lateinit var autoToggle: ToggleButton
    private lateinit var stateStore: GameStateStore
    private val autosaveExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val inputController = GameInputController(
        activity = this,
        moveUntilBlocked = { direction: Direction -> moveUntilBlocked(direction) },
        onSwipeResolved = { resolvedSwipeCount++ }
    )
    private var autoMovementEnabled: Boolean = false
    private var selectedAutomatedPlayerPolicy: PlayerPolicyType? = null
    private var automatedPolicyPromptShown: Boolean = false
    private var automatedPolicyDialog: AlertDialog? = null

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal var resolvedSwipeCount: Int = 0
        private set

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isMenuPopoverShowingForTesting(): Boolean = menuPopover?.isShowing == true

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun menuPopoverTextSnapshotForTesting(): List<String> =
        menuPopover?.textSnapshotForTesting().orEmpty()

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isAutomatedPolicyDialogShowingForTesting(): Boolean =
        automatedPolicyDialog?.isShowing == true

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isAutoToggleCheckedForTesting(): Boolean = autoToggle.isChecked

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isSwipeStartInsideGameHostForTesting(x: Float, y: Float): Boolean =
        inputController.isPointInsideGameHost(x, y)

    /**
     * Feeds a MotionEvent directly into the swipe gesture detector, bypassing
     * `dispatchTouchEvent`'s hit-testing. Used by instrumentation tests to verify swipe
     * resolution without depending on the emulator's input pipeline (which can drop synthetic
     * events) or the cross-app injection permission required by `Instrumentation.sendPointerSync`.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun feedSwipeEventForTesting(event: MotionEvent) {
        inputController.feedSwipeEventForTesting(event)
    }

    private val hudHandler = Handler(Looper.getMainLooper())
    private val hudRefreshRunnable = object : Runnable {
        override fun run() {
            refreshHudSnapshot()
            hudHandler.postDelayed(this, HUD_REFRESH_INTERVAL_MS)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        stateStore = GameStateStore(this)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        menuButton = findViewById(R.id.buttonMenu)
        autoToggle = findViewById(R.id.buttonAuto)

        // Load the saved snapshot at most once here and share it between the
        // auto-toggle initialization and buildGameFragmentArgs() so a cold
        // resume doesn't re-parse the JSON (and repeat the stale-blob clearing
        // side effect) twice.
        val resume = savedInstanceState == null &&
            intent.getBooleanExtra(SetupActivity.EXTRA_RESUME, false)
        val resumeSnapshot = if (resume) stateStore.load() else null

        restoreAutomationUiState(savedInstanceState, intent, resumeSnapshot)

        if (savedInstanceState == null) {
            val fragment = GameFragment().apply {
                arguments = buildGameFragmentArgs(intent, resumeSnapshot)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentGameHost, fragment)
                .commitNow()
        }

        setupControls()
        setupSwipeControls()
        refreshAutoToggle()
        // The Classic GameFragment is attached synchronously via commitNow()
        // above, so the prompt can run inline; no need to defer to the event
        // queue (which could race the transaction).
        promptForAutomatedPolicyIfNeeded()
    }

    private fun restoreAutomationUiState(
        savedInstanceState: Bundle?,
        intent: Intent,
        resumeSnapshot: GameEngineSnapshot?
    ) {
        if (savedInstanceState != null) {
            autoMovementEnabled = savedInstanceState.getBoolean(KEY_AUTO_MOVEMENT_ENABLED, false)
            selectedAutomatedPlayerPolicy = savedInstanceState.getString(KEY_SELECTED_AUTO_POLICY)
                ?.let { name -> PlayerPolicyType.entries.firstOrNull { it.name == name } }
                ?.takeIf { it != PlayerPolicyType.MANUAL }
            automatedPolicyPromptShown = savedInstanceState.getBoolean(KEY_AUTO_PROMPT_SHOWN, false)
            return
        }
        val initialPolicy = getInitialPlayerPolicyFromIntentOrStore(intent, resumeSnapshot)
        autoMovementEnabled = initialPolicy != PlayerPolicyType.MANUAL
        selectedAutomatedPlayerPolicy = initialPolicy.takeIf { it != PlayerPolicyType.MANUAL }
        // Only fresh runs that start in MANUAL should show the one-time picker.
        // Resumed runs and starts that already chose an automated policy should
        // treat the prompt as shown to avoid unexpected interruptions on
        // recreation/toggle flows.
        automatedPolicyPromptShown =
            intent.getBooleanExtra(SetupActivity.EXTRA_RESUME, false) ||
            initialPolicy != PlayerPolicyType.MANUAL
    }

    private fun getInitialPlayerPolicyFromIntentOrStore(
        intent: Intent,
        resumeSnapshot: GameEngineSnapshot?
    ): PlayerPolicyType {
        val intentPolicy = enumOrDefault(
            intent.getStringExtra(SetupActivity.EXTRA_PLAYER_POLICY),
            PlayerPolicyType.MANUAL
        )
        val resume = intent.getBooleanExtra(SetupActivity.EXTRA_RESUME, false)
        if (resume) {
            return resumeSnapshot?.playerPolicy ?: intentPolicy
        }
        return intentPolicy
    }

    private fun buildGameFragmentArgs(intent: Intent, resumeSnapshot: GameEngineSnapshot?): Bundle {
        // If the launcher asked us to resume, the saved snapshot has already
        // been loaded+validated once in onCreate and is passed in here, so we
        // hand the parsed object directly to the fragment (avoiding a redundant
        // JSON parse on the GL thread).
        // We still embed the JSON in args so a process-death recreation
        // — where the static handoff is wiped but the fragment Bundle
        // survives — can restore from the bundle. The user's selected
        // policy/difficulty are always included in the args alongside
        // the snapshot so that, if a later recreation cannot parse the
        // serialized snapshot (schema bump, corruption), GameFragment
        // starts a fresh game with the user's selections rather than
        // its hard-coded defaults. Falls back to a fresh game using
        // those same selections when no valid snapshot exists.
        val player = enumOrDefault(
            intent.getStringExtra(SetupActivity.EXTRA_PLAYER_POLICY),
            PlayerPolicyType.MANUAL
        )
        val npc = enumOrDefault(
            intent.getStringExtra(SetupActivity.EXTRA_NPC_POLICY),
            NpcPolicyType.DIRECT_CHASE
        )
        val difficulty = intent.getStringExtra(SetupActivity.EXTRA_DIFFICULTY)
            ?: DifficultyPresets.MEDIUM.name

        // Always carry the user's selected policy/difficulty in the
        // fragment args, even on the resume path. If a process-death
        // recreation later wipes the static snapshot handoff and the
        // serialized JSON in the bundle can't be parsed (schema bump,
        // corruption), GameFragment will still start a fresh game using
        // the user's selections rather than its hard-coded defaults.
        return Bundle().apply {
            putString(GameFragment.ARG_PLAYER_POLICY, player.name)
            putString(GameFragment.ARG_NPC_POLICY, npc.name)
            putString(GameFragment.ARG_DIFFICULTY, difficulty)
            if (resumeSnapshot != null) {
                GameFragment.pendingResumeSnapshot = resumeSnapshot
                putString(GameFragment.ARG_RESUME_SNAPSHOT_JSON, resumeSnapshot.toJson())
            }
        }
    }

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E {
        if (name == null) return default
        return enumValues<E>().firstOrNull { it.name == name } ?: default
    }

    override fun exit() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        // No need to start the HUD ticker until the popover is opened; HUD text
        // is only visible inside the popover now.
    }

    override fun onPause() {
        hudHandler.removeCallbacks(hudRefreshRunnable)
        inputController.stop()
        menuPopover?.dismiss()
        // Autosave so the user can resume after a process death, switching
        // away, or just to be safe before any incidental finish(). Skip
        // terminal states (WIN/LOSE) so re-launching doesn't drop the
        // player straight back into the end-of-game overlay.
        //
        // The snapshot capture is performed asynchronously on the GL
        // thread: blocking the UI thread here risks ANR / jank on slower
        // devices when the render loop is busy or starved. The state
        // store write is hopped onto a background executor so we never
        // touch the prefs editor on the UI thread either (even though
        // GameStateStore.save uses apply(), the JSON serialisation
        // itself is non-trivial for larger snapshots).
        val hud = gameFragment()?.hudState()
        val status = hud?.status
        // While the pre-game 3-2-1 countdown is active the engine is
        // RUNNING but the simulation is frozen, and snapshots don't
        // persist the countdown state (restore zeroes it). Saving here
        // would resume into an immediately-live game on relaunch,
        // skipping the countdown the player saw. Treat this as a
        // fresh-game resume by clearing any prior snapshot instead.
        if (hud?.countdownRemainingSeconds != null &&
            (status == GameStatus.RUNNING || status == GameStatus.PAUSED)) {
            // Bounded-wait clear so the removal is durably flushed before
            // we return from onPause(); a fire-and-forget execute() could
            // be lost if the process is killed shortly after pausing,
            // leaving an older snapshot on disk that would resume into a
            // live game and skip the countdown the player just saw.
            clearSavedStateBlocking()
            super.onPause()
            return
        }
        if (status == GameStatus.RUNNING || status == GameStatus.PAUSED) {
            gameFragment()?.captureSnapshotAsync { snapshot ->
                // captureSnapshotAsync's callback runs on the GL thread and may
                // fire after onDestroy() has shut the executor down. Guard so a
                // late snapshot doesn't crash with RejectedExecutionException.
                //
                // Re-check the snapshot's status here: the HUD reading above
                // is a UI-thread sample taken before the GL-thread capture,
                // and the game can transition to WIN/LOSE in between. If
                // that happens, clear any prior saved state instead of
                // persisting a terminal snapshot (which would re-launch the
                // user straight back into the end-of-game overlay).
                try {
                    if (snapshot.status == GameStatus.WIN || snapshot.status == GameStatus.LOSE) {
                        autosaveExecutor.execute { stateStore.clearBlocking() }
                    } else {
                        autosaveExecutor.execute {
                            stateStore.save(snapshot)
                        }
                    }
                } catch (_: RejectedExecutionException) {
                    // Executor already shut down — drop the late autosave.
                }
            }
        } else if (status == GameStatus.WIN || status == GameStatus.LOSE) {
            // Clear any prior snapshot so Resume can't drop the user back into
            // a stale mid-run state after a completed game. Use the
            // bounded-wait commit()-based helper so the removal is durably
            // flushed to disk before we return from onPause(); a
            // fire-and-forget execute() could be lost if the process is
            // killed shortly after pausing and resurrect a finished game
            // on relaunch.
            clearSavedStateBlocking()
        }
        super.onPause()
    }

    override fun onDestroy() {
        inputController.stop()
        automatedPolicyDialog?.dismiss()
        automatedPolicyDialog = null
        autosaveExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AUTO_MOVEMENT_ENABLED, autoMovementEnabled)
        selectedAutomatedPlayerPolicy?.let { outState.putString(KEY_SELECTED_AUTO_POLICY, it.name) }
        outState.putBoolean(KEY_AUTO_PROMPT_SHOWN, automatedPolicyPromptShown)
        super.onSaveInstanceState(outState)
    }

    private fun setupControls() {
        menuButton.setOnClickListener { showMenuPopover() }
        autoToggle.setOnClickListener {
            if (autoToggle.isChecked) {
                enableAutomatedMovementOrSelect()
            } else {
                disableAutomatedMovement()
            }
        }

        inputController.bindDirectionalControls()
    }

    private fun enableAutomatedMovementOrSelect() {
        val selected = selectedAutomatedPlayerPolicy
        if (selected != null && selected in automatedPlayerPolicies()) {
            applyAutomatedPlayerPolicy(selected)
        } else {
            autoMovementEnabled = false
            refreshAutoToggle()
            showAutomatedPolicySelector(revertToManualOnCancel = true)
        }
    }

    private fun disableAutomatedMovement() {
        autoMovementEnabled = false
        gameFragment()?.setPlayerPolicy(PlayerPolicyType.MANUAL)
        refreshAutoToggle()
    }

    private fun applyAutomatedPlayerPolicy(policy: PlayerPolicyType) {
        selectedAutomatedPlayerPolicy = policy
        autoMovementEnabled = true
        gameFragment()?.setPlayerPolicy(policy)
        refreshAutoToggle()
    }

    private fun showAutomatedPolicySelector(revertToManualOnCancel: Boolean) {
        val policies = automatedPlayerPolicies()
        if (policies.isEmpty()) {
            autoMovementEnabled = false
            refreshAutoToggle()
            return
        }
        val items = policies.map { it.label }.toTypedArray()
        val checked = selectedAutomatedPlayerPolicy
            ?.let { policies.indexOf(it) }
            ?.takeIf { it >= 0 }
            ?: -1
        automatedPolicyDialog = AlertDialog.Builder(this)
            .setTitle(R.string.pick_automated_player_strategy)
            .setSingleChoiceItems(items, checked) { dialog, which ->
                applyAutomatedPlayerPolicy(policies[which])
                dialog.dismiss()
            }
            .setOnCancelListener {
                if (revertToManualOnCancel) disableAutomatedMovement()
                refreshAutoToggle()
            }
            .show()
    }

    private fun promptForAutomatedPolicyIfNeeded() {
        if (automatedPolicyPromptShown || autoMovementEnabled || automatedPlayerPolicies().isEmpty()) return
        if (gameFragment() == null) return
        automatedPolicyPromptShown = true
        showAutomatedPolicySelector(revertToManualOnCancel = false)
    }

    private fun refreshAutoToggle() {
        autoToggle.isEnabled = automatedPlayerPolicies().isNotEmpty()
        autoToggle.isChecked = autoMovementEnabled && autoToggle.isEnabled
    }

    private fun showMenuPopover() {
        val popover = menuPopover ?: GameMenuPopover(
            this,
            object : GameMenuPopover.Callbacks {
                override fun onPauseResume() {
                    gameFragment()?.togglePause()
                    refreshHudSnapshot()
                }

                override fun onRestart() {
                    gameFragment()?.restart()
                    refreshHudSnapshot()
                }

                override fun onLegend() {
                    LegendDialog.show(this@MainActivity)
                }

                override fun onPauseAndExit() {
                    // Pause first so the snapshot reflects PAUSED status;
                    // captureSnapshot() blocks briefly on the GL thread.
                    val hud = gameFragment()?.hudState()
                    if (hud?.status == GameStatus.RUNNING) {
                        gameFragment()?.togglePause()
                    }
                    val status = hud?.status
                    // While the pre-game 3-2-1 countdown is active, the
                    // engine is RUNNING but the simulation is frozen and
                    // snapshots don't persist the countdown state
                    // (restore zeroes it). Persisting here would resume
                    // straight into a live game on relaunch, skipping
                    // the countdown the player saw. Treat this as a
                    // fresh-game resume by clearing any prior snapshot.
                    if (hud?.countdownRemainingSeconds != null &&
                        (status == GameStatus.RUNNING || status == GameStatus.PAUSED)) {
                        clearSavedStateBlocking()
                    } else if (status == GameStatus.RUNNING || status == GameStatus.PAUSED) {
                        val snapshot = gameFragment()?.captureSnapshot()
                        // Re-check the post-capture status: the game may have
                        // transitioned to WIN/LOSE between the UI-thread HUD
                        // read above and the synchronous capture below. In
                        // that case fall through to the clear path so a
                        // terminal snapshot is never persisted (and Resume
                        // can't resurrect a completed run on relaunch).
                        val postStatus = snapshot?.status
                        if (snapshot != null &&
                            postStatus != GameStatus.WIN &&
                            postStatus != GameStatus.LOSE) {
                            // Use the commit()-based path off the UI thread so the
                            // snapshot is durably on disk before we finish() —
                            // apply()'s deferred write could otherwise be lost when
                            // the activity exits immediately after.
                            try {
                                autosaveExecutor.submit {
                                    stateStore.saveBlocking(snapshot)
                                }.get(PAUSE_EXIT_SAVE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                            } catch (_: RejectedExecutionException) {
                                // Fall back to a direct synchronous commit on the UI
                                // thread if the executor is unavailable.
                                stateStore.saveBlocking(snapshot)
                            } catch (_: InterruptedException) {
                                Thread.currentThread().interrupt()
                            } catch (_: java.util.concurrent.ExecutionException) {
                                // Surface no further action; save failure is best-effort.
                            } catch (_: java.util.concurrent.TimeoutException) {
                                // Executor backed up or disk I/O stalled; avoid
                                // ANR by giving up the wait. The submitted task
                                // will still complete in the background if it
                                // can, and a stale snapshot is preferable to a
                                // frozen UI on exit.
                            }
                        } else if (postStatus == GameStatus.WIN || postStatus == GameStatus.LOSE) {
                            clearSavedStateBlocking()
                        }
                    } else if (status == GameStatus.WIN || status == GameStatus.LOSE) {
                        clearSavedStateBlocking()
                    }
                    val intent = Intent(this@MainActivity, SetupActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(intent)
                    finish()
                }
            }
        ).also {
            it.setOnDismissListener {
                hudHandler.removeCallbacks(hudRefreshRunnable)
            }
            menuPopover = it
        }
        if (popover.isShowing) return
        popover.show(menuButton)
        refreshHudSnapshot()
        hudHandler.removeCallbacks(hudRefreshRunnable)
        hudHandler.postDelayed(hudRefreshRunnable, HUD_REFRESH_INTERVAL_MS)
    }

    private fun clearSavedStateBlocking() {
        // Use commit()-based clear off the UI thread (with the same
        // bounded-wait pattern as the save path) so the removal is
        // durably on disk before we finish() and Resume can't resurrect
        // a completed run on relaunch.
        try {
            autosaveExecutor.submit {
                stateStore.clearBlocking()
            }.get(PAUSE_EXIT_SAVE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            stateStore.clearBlocking()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: java.util.concurrent.ExecutionException) {
            // Best-effort; nothing further to do.
        } catch (_: java.util.concurrent.TimeoutException) {
            // Avoid ANR by giving up the wait; submitted
            // task will still complete in the background.
        }
    }

    private fun moveUntilBlocked(direction: Direction) {
        gameFragment()?.queueManualMoveUntilBlocked(direction)
        refreshHudSnapshot()
    }

    private fun setupSwipeControls() {
        inputController.setupSwipeControls()
    }

    // Routes touches that begin inside the game host through the swipe detector before the
    // regular dispatch path, so swipes are observed even when child views (e.g., the libGDX
    // SurfaceView inside fragmentGameHost) consume the events. Touches that start on overlay
    // UI (the hamburger button or D-pad) are NOT forwarded so taps/scrolls on controls don't
    // trigger unintended player moves. We never consume the event here so existing dispatch
    // behavior for child views and arrow-button controls is preserved.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        inputController.onDispatchTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun refreshHudSnapshot() {
        val popover = menuPopover ?: return
        if (!popover.isShowing) return
        val hud = gameFragment()?.hudState() ?: return
        val status = getString(
            R.string.status_template,
            displayStatus(hud.status),
            hud.steps,
            hud.elapsedSeconds
        )
        val speed = getString(
            R.string.speed_detail_template,
            hud.playerSpeed,
            hud.npcSpeed,
            hud.playerPolicyLabel,
            hud.npcPolicyLabel
        )
        val powerUps = getString(
            R.string.powerups_detail_template,
            hud.activePowerUps.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: getString(R.string.powerups_none),
            hud.powerUpsOnMap
        )
        popover.updateHud(status, speed, powerUps)
    }

    private fun gameFragment(): GameFragment? {
        return supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
    }

    private fun displayStatus(status: GameStatus): String = when (status) {
        GameStatus.RUNNING -> getString(R.string.status_running)
        GameStatus.PAUSED -> getString(R.string.status_paused)
        GameStatus.WIN -> getString(R.string.status_win)
        GameStatus.LOSE -> getString(R.string.status_lose)
    }

    companion object {
        private const val HUD_REFRESH_INTERVAL_MS = 250L
        // Upper bound on how long Pause & Exit will wait for the snapshot
        // commit() to flush before giving up to avoid an ANR.
        private const val PAUSE_EXIT_SAVE_TIMEOUT_MS = 750L
        private const val KEY_AUTO_MOVEMENT_ENABLED = "autoMovementEnabled"
        private const val KEY_SELECTED_AUTO_POLICY = "selectedAutomatedPlayerPolicy"
        private const val KEY_AUTO_PROMPT_SHOWN = "automatedPolicyPromptShown"
    }
}
