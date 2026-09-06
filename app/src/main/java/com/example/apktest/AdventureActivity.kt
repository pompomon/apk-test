package com.example.apktest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.ToggleButton
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.AdventureFeatureFlags
import com.example.apktest.game.core.AdventureRunController
import com.example.apktest.game.core.AdventureRunStateSnapshot
import com.example.apktest.game.core.AdventureStatus
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.game.core.PowerUpType
import com.example.apktest.game.core.MazeStartupSpec
import com.example.apktest.game.core.PendingAdventureReward
import com.example.apktest.game.core.RewardStage
import com.example.apktest.game.core.RouteEventCategory
import com.example.apktest.game.core.RouteEventGenerator
import com.example.apktest.game.core.automatedPlayerPolicies
import com.example.apktest.ui.GameInputController
import com.example.apktest.ui.LegendDialog
import com.example.apktest.ui.AdventureTimeFormatter
import com.example.apktest.telemetry.AdventureRouteTelemetry
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Adventure-mode host. Mirrors [MainActivity] in lifecycle / popover /
 * swipe handling, but layers an [AdventureRunController] on top of the
 * single-maze [com.example.apktest.game.MazeGame] to drive consecutive
 * mazes, lives, bonus-life awards, and per-maze randomised NPC policies.
 *
 * State is persisted via [AdventureStateStore], independent of the
 * single-maze [GameStateStore], so a saved Adventure run never shows up
 * as a single-maze Resume on the existing start menu and vice versa.
 */
class AdventureActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {

    private lateinit var adventureStore: AdventureStateStore
    private lateinit var saveSession: AdventureSaveSession
    private lateinit var bestStore: AdventureBestStore
    private val autosaveExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var controller: AdventureRunController
    private var runSeed: Long = 0L

    private lateinit var menuButton: ImageButton
    private lateinit var autoToggle: ToggleButton
    private lateinit var inertiaToggle: ToggleButton
    private lateinit var statusBar: TextView
    private val inputController = GameInputController(
        activity = this,
        moveUntilBlocked = { direction: Direction -> moveUntilBlocked(direction) }
    )

    // Tracks the previous tick's engine status so we can detect WIN/LOSE
    // transitions exactly once and run the corresponding overlay flow.
    private var lastObservedStatus: GameStatus = GameStatus.RUNNING
    private var transitionPending: Boolean = false
    private var autoMovementEnabled: Boolean = false
    private var inertiaMovementEnabled: Boolean = true
    private var selectedAutomatedPlayerPolicy: PlayerPolicyType? = null
    private var automatedPolicyPromptShown: Boolean = false
    private var automatedPolicyDialog: AlertDialog? = null
    private var rewardDialog: AlertDialog? = null
    private var foreground = false
    private var stateGeneration = 0L
    private var saveInFlight = false
    private var saveFailed = false
    private var failNextRewardSave = false
    private var pendingCommit: (() -> Unit)? = null
    private var afterResume: (() -> Unit)? = null
    private val routeTelemetry = AdventureRouteTelemetry()

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun adventureStatusBarTextForTesting(): CharSequence = statusBar.text

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun controllerForTesting(): AdventureRunController = controller

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun refreshAutoToggleForTesting() = refreshAutoToggle()

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isAutomatedPolicyDialogShowingForTesting(): Boolean =
        automatedPolicyDialog?.isShowing == true

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isInertiaToggleCheckedForTesting(): Boolean = inertiaToggle.isChecked

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun rewardStageForTesting(): RewardStage? = controller.state.pendingReward?.stage

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun rewardDialogForTesting(): AlertDialog? = rewardDialog

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun rewardOptionsForTesting(): List<String> {
        val adapter = rewardDialog?.listView?.adapter ?: return emptyList()
        return (0 until adapter.count).map { adapter.getItem(it).toString() }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun failNextRewardSaveForTesting() {
        failNextRewardSave = true
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun rewardSaveFailedForTesting(): Boolean = saveFailed

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun rewardDecisionReadyForTesting(): Boolean =
        pendingCommit == null && !saveInFlight && !saveFailed

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun handleCapturedSnapshotForTesting(snapshot: GameEngineSnapshot) =
        handleCapturedSnapshot(snapshot)

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tickRunnable = object : Runnable {
        override fun run() {
            pollEngineStatus()
            tickHandler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_adventure)

        adventureStore = AdventureStateStore(this)
        saveSession = AdventureSaveSession.open()
        bestStore = AdventureBestStore(this)

        val root = findViewById<View>(R.id.adventureRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        statusBar = findViewById(R.id.adventureStatusBar)
        menuButton = findViewById(R.id.buttonMenu)
        autoToggle = findViewById(R.id.buttonAuto)
        inertiaToggle = findViewById(R.id.buttonInertia)

        val (initialController, initialSeed, _) = loadOrBuildController(intent, savedInstanceState)
        controller = initialController
        runSeed = initialSeed
        restoreAutomationUiState(savedInstanceState)
        restoreInputUiState(savedInstanceState)

        setupControls()
        setupSwipeControls()
        refreshStatusBar()
        refreshAutoToggle()
        // Never allow a FragmentManager-restored maze to run while its
        // controller decisions are still waiting for a durable recommit.
        supportFragmentManager.findFragmentById(R.id.fragmentGameHost)?.let {
            supportFragmentManager.beginTransaction().remove(it).commitNow()
        }
        if (controller.state.pendingReward != null) {
            transitionPending = true
            commitTransition { showPendingReward() }
            return
        }
        val spec = controller.prepareCurrentMaze()
        if (spec == null) {
            // Defensive: terminal state recovered from store. Clear and bail.
            adventureStore.clear()
            returnToSetup()
            return
        }
        // SharedPreferences may expose a failed commit in its memory cache.
        // Recommit on recreation before starting GL or acknowledging a stage.
        transitionPending = true
        commitTransition {
            attachMaze(spec)
            refreshAutoToggle()
            promptForAutomatedPolicyIfNeeded()
        }
    }

    private fun attachMaze(spec: MazeStartupSpec) {
        // A new fragment cannot expose the previous maze's terminal HUD.
        // In particular a restored maze may win before our very first poll.
        lastObservedStatus = GameStatus.RUNNING
        transitionPending = false
        val fragment = GameFragment()
        val args = Bundle().apply {
            putString(GameFragment.ARG_PLAYER_POLICY, spec.playerPolicy.name)
            // Per-NPC list is applied via configureAdventureMaze after attach;
            // this is just the engine's default before the override lands.
            putString(
                GameFragment.ARG_NPC_POLICY,
                com.example.apktest.game.core.NpcPolicyType.DIRECT_CHASE.name
            )
            putString(GameFragment.ARG_DIFFICULTY, spec.difficulty.name)
            if (spec.midMazeSnapshot != null) {
                GameFragment.pendingResumeSnapshot = spec.midMazeSnapshot
                putString(
                    GameFragment.ARG_RESUME_SNAPSHOT_JSON,
                    spec.midMazeSnapshot.toJson()
                )
            }
        }
        fragment.arguments = args
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentGameHost, fragment)
            .commitNow()
        if (spec.midMazeSnapshot == null) {
            fragment.configureAdventureMaze(
                seed = spec.seed,
                difficulty = spec.difficulty.name,
                playerPolicy = spec.playerPolicy,
                npcCount = spec.npcCount,
                npcPolicies = spec.npcPolicies,
                startingPowerUp = spec.startingPowerUp,
                pickupLifetimeSeconds = spec.pickupLifetimeSeconds
            )
        }
    }

    private fun restoreAutomationUiState(savedInstanceState: Bundle?) {
        if (savedInstanceState != null) {
            autoMovementEnabled = savedInstanceState.getBoolean(KEY_AUTO_MOVEMENT_ENABLED, false)
            selectedAutomatedPlayerPolicy = savedInstanceState.getString(KEY_SELECTED_AUTO_POLICY)
                ?.let { name -> PlayerPolicyType.entries.firstOrNull { it.name == name } }
                ?.takeIf { it != PlayerPolicyType.MANUAL }
            automatedPolicyPromptShown = savedInstanceState.getBoolean(KEY_AUTO_PROMPT_SHOWN, false)
        } else {
            val currentPolicy = controller.state.currentPlayerPolicy
            val currentPolicyIsAutomated = currentPolicy != PlayerPolicyType.MANUAL
            autoMovementEnabled = currentPolicyIsAutomated
            selectedAutomatedPlayerPolicy = currentPolicy
                .takeIf { it != PlayerPolicyType.MANUAL }
                ?: controller.state.lastAutomatedPlayerPolicy
            // Backward-compat: older persisted runs won't have the prompt flag,
            // so if current policy is already automated we treat the prompt as
            // implicitly shown to avoid surprise prompts after upgrade.
            automatedPolicyPromptShown = controller.state.automatedPolicyPromptShown ||
                currentPolicyIsAutomated
        }
        validateAndUpdateSelectedAutomatedPolicy()
        controller.setAutomatedPolicyPromptShown(automatedPolicyPromptShown)
    }

    private fun restoreInputUiState(savedInstanceState: Bundle?) {
        inertiaMovementEnabled = savedInstanceState?.getBoolean(
            KEY_INERTIA_MOVEMENT_ENABLED,
            true
        ) ?: true
    }

    private fun loadOrBuildController(
        intent: Intent,
        savedInstanceState: Bundle?
    ): Triple<AdventureRunController, Long, Boolean> {
        // Always attempt to load a persisted run when the activity is being
        // recreated by the OS (savedInstanceState != null). Process death
        // recreates the activity with the original launch Intent, which for a
        // fresh-Start launch lacks EXTRA_RESUME — without this we'd build a
        // fresh controller and lose the in-flight run. Honour EXTRA_RESUME
        // for explicit Resume launches from setup as well.
        val explicitResume = intent.getBooleanExtra(AdventureSetupActivity.EXTRA_RESUME, false)
        val shouldTryLoad = explicitResume || savedInstanceState != null
        val saved = if (shouldTryLoad) adventureStore.load() else null
        if (saved != null) {
            val config = AdventureConfig.forDifficultyName(saved.difficultyName)
            val controller = AdventureRunController(
                config = config,
                initialState = saved.toState(),
                runSeed = saved.runSeed,
                routesEnabled = AdventureFeatureFlags.ROUTE_EVENTS_ENABLED &&
                    config.difficulty.name == DifficultyPresets.MEDIUM.name
            )
            return Triple(controller, saved.runSeed, false)
        }
        val difficultyName = intent.getStringExtra(AdventureSetupActivity.EXTRA_DIFFICULTY)
            ?: DifficultyPresets.EASY.name
        val config = AdventureConfig.forDifficulty(DifficultyPresets.byName(difficultyName))
        val seed = System.currentTimeMillis()
        return Triple(AdventureRunController(
            config = config,
            runSeed = seed,
            routesEnabled = AdventureFeatureFlags.ROUTE_EVENTS_ENABLED &&
                config.difficulty.name == DifficultyPresets.MEDIUM.name
        ), seed, true)
    }

    override fun exit() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        foreground = true
        val action = afterResume
        afterResume = null
        when {
            action != null -> action()
            saveFailed -> showSaveFailure()
            !saveInFlight && controller.state.pendingReward != null -> showPendingReward()
        }
        tickHandler.removeCallbacks(tickRunnable)
        tickHandler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onPause() {
        foreground = false
        tickHandler.removeCallbacks(tickRunnable)
        // Stop held D-pad repeats while the activity is backgrounded.
        inputController.stop()
        // Every reward transition is saved explicitly before its next screen.
        // Never let an old GL snapshot overwrite a pending decision.
        if (pendingCommit != null || controller.state.pendingReward != null) {
            super.onPause()
            return
        }
        // Adventure runs that are already terminal (WON/LOST) clear the
        // store so the next launch doesn't try to "resume" a finished run.
        if (controller.state.status != AdventureStatus.IN_PROGRESS) {
            try {
                autosaveExecutor.execute { saveSession.write { adventureStore.clearBlocking() } }
            } catch (_: RejectedExecutionException) {
                // Executor shut down — accept the loss.
            }
            super.onPause()
            return
        }
        val frag = gameFragment()
        val hud = frag?.hudState()
        val status = hud?.status
        // While the 3-2-1 countdown is active the engine snapshot
        // doesn't persist the countdown; treat as fresh-maze resume
        // by clearing the mid-maze snapshot instead of persisting a
        // snapshot that would skip the countdown on relaunch.
        // Use the blocking persist path here so the clear is durably
        // committed before onPause() returns; otherwise an older saved
        // state with a mid-maze snapshot can be resurrected if the
        // process is killed before an async apply() flushes.
        if (hud?.countdownRemainingSeconds != null) {
            controller.clearMidMazeSnapshot()
            persistAdventureStateBlocking()
            super.onPause()
            return
        }
        if (frag != null && (status == GameStatus.RUNNING || status == GameStatus.PAUSED)) {
            val generation = stateGeneration
            val mazeIndex = controller.state.currentMazeIndex
            val mazeSeed = controller.state.currentMazeSeed
            frag.captureSnapshotAsync { engineSnapshot ->
                // captureSnapshotAsync's callback fires on the GL thread.
                // Hop back to the main thread before mutating the
                // controller (not thread-safe) and before reading state
                // into a serialisable snapshot.
                tickHandler.post {
                    if (isDestroyed || generation != stateGeneration ||
                        mazeIndex != controller.state.currentMazeIndex ||
                        mazeSeed != controller.state.currentMazeSeed ||
                        engineSnapshot.seed != mazeSeed ||
                        controller.state.pendingReward != null || pendingCommit != null ||
                        controller.state.status != AdventureStatus.IN_PROGRESS
                    ) return@post
                    try {
                        handleCapturedSnapshot(engineSnapshot)
                    } catch (_: RejectedExecutionException) {
                        // Executor shut down between hop and persist.
                    }
                }
            }
        }
        super.onPause()
    }

    private fun handleCapturedSnapshot(engineSnapshot: GameEngineSnapshot) {
        when (engineSnapshot.status) {
            GameStatus.WIN, GameStatus.LOSE -> handleTerminalStatus(
                engineSnapshot.status,
                engineSnapshot.elapsedSeconds,
                engineSnapshot.steps
            )
            else -> {
                controller.recordMidMazeSnapshot(engineSnapshot)
                persistAdventureStateAsync()
            }
        }
    }

    override fun onDestroy() {
        inputController.stop()
        automatedPolicyDialog?.dismiss()
        automatedPolicyDialog = null
        rewardDialog?.dismiss()
        rewardDialog = null
        afterResume = null
        stateGeneration++
        saveSession.close()
        autosaveExecutor.shutdown()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_AUTO_MOVEMENT_ENABLED, autoMovementEnabled)
        outState.putBoolean(KEY_INERTIA_MOVEMENT_ENABLED, inertiaMovementEnabled)
        selectedAutomatedPlayerPolicy?.let { outState.putString(KEY_SELECTED_AUTO_POLICY, it.name) }
        outState.putBoolean(KEY_AUTO_PROMPT_SHOWN, automatedPolicyPromptShown)
        super.onSaveInstanceState(outState)
    }

    private fun persistAdventureStateAsync() {
        if (pendingCommit != null || controller.state.pendingReward != null ||
            controller.state.status != AdventureStatus.IN_PROGRESS) return
        val snapshot = AdventureRunStateSnapshot.fromState(controller.state, runSeed)
        try {
            autosaveExecutor.execute { saveSession.write { adventureStore.saveBlocking(snapshot) } }
        } catch (_: RejectedExecutionException) {
            // Executor shut down — accept the loss.
        }
    }

    private fun commitTransition(onCommitted: () -> Unit) {
        stateGeneration++
        pendingCommit = onCommitted
        automatedPolicyDialog?.dismiss()
        automatedPolicyDialog = null
        rewardDialog?.dismiss()
        rewardDialog = null
        refreshAutoToggle()
        savePendingTransition()
    }

    private fun savePendingTransition() {
        if (saveInFlight || pendingCommit == null) return
        saveFailed = false
        saveInFlight = true
        val generation = stateGeneration
        val snapshot = AdventureRunStateSnapshot.fromState(controller.state, runSeed)
        val failWrite = failNextRewardSave
        failNextRewardSave = false
        try {
            autosaveExecutor.execute {
                val saved = try {
                    !failWrite && saveSession.write {
                        if (snapshot.status == AdventureStatus.IN_PROGRESS) {
                            adventureStore.saveBlocking(snapshot)
                        } else {
                            adventureStore.clearBlocking()
                        }
                    }
                } catch (_: Exception) {
                    false
                }
                tickHandler.post {
                    if (isDestroyed || generation != stateGeneration) return@post
                    saveInFlight = false
                    if (saved) {
                        val action = pendingCommit
                        pendingCommit = null
                        if (foreground) action?.invoke() else afterResume = action
                    } else {
                        saveFailed = true
                        if (foreground) showSaveFailure()
                    }
                }
            }
        } catch (_: RejectedExecutionException) {
            saveInFlight = false
            saveFailed = true
            if (foreground && !isDestroyed) showSaveFailure()
        }
    }

    private fun showSaveFailure() {
        rewardDialog?.dismiss()
        rewardDialog = AlertDialog.Builder(this)
            .setTitle(R.string.adventure_save_failed_title)
            .setMessage(R.string.adventure_save_failed_body)
            .setCancelable(false)
            .setPositiveButton(R.string.adventure_save_retry) { _, _ -> savePendingTransition() }
            .show()
    }

    private fun persistAdventureStateBlocking() {
        val snapshot = AdventureRunStateSnapshot.fromState(controller.state, runSeed)
        try {
            autosaveExecutor.submit { saveSession.write { adventureStore.saveBlocking(snapshot) } }
                .get(SAVE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: RejectedExecutionException) {
            // Executor shut down — accept the loss rather than calling
            // saveBlocking() (which does SharedPreferences.commit() disk
            // I/O) on the calling thread, which may be the UI thread.
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (_: java.util.concurrent.ExecutionException) {
            // best-effort
        } catch (_: java.util.concurrent.TimeoutException) {
            // best-effort
        }
    }

    private fun setupControls() {
        menuButton.setOnClickListener { showMenu() }
        inertiaToggle.isChecked = inertiaMovementEnabled
        inertiaToggle.setOnClickListener {
            inertiaMovementEnabled = inertiaToggle.isChecked
        }
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
        val selected = validateAndUpdateSelectedAutomatedPolicy()
        if (selected != null) {
            applyAutomatedPlayerPolicy(selected)
        } else {
            autoMovementEnabled = false
            refreshAutoToggle()
            showAutomatedPolicySelector(revertToManualOnCancel = true)
        }
    }

    private fun disableAutomatedMovement() {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        autoMovementEnabled = false
        controller.setCurrentPlayerPolicy(PlayerPolicyType.MANUAL)
        gameFragment()?.setPlayerPolicy(PlayerPolicyType.MANUAL)
        persistAdventureStateAsync()
        refreshStatusBar()
        refreshAutoToggle()
    }

    private fun applyAutomatedPlayerPolicy(policy: PlayerPolicyType) {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        if (!controller.setCurrentPlayerPolicy(policy)) {
            autoMovementEnabled = false
            refreshAutoToggle()
            return
        }
        selectedAutomatedPlayerPolicy = policy
        autoMovementEnabled = true
        gameFragment()?.setPlayerPolicy(policy)
        persistAdventureStateAsync()
        refreshStatusBar()
        refreshAutoToggle()
    }

    private fun showAutomatedPolicySelector(revertToManualOnCancel: Boolean) {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        val policies = availableAutomatedPlayerPolicies()
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
        if (controller.state.pendingReward != null || pendingCommit != null) return
        val policies = availableAutomatedPlayerPolicies()
        if (automatedPolicyPromptShown || autoMovementEnabled || policies.isEmpty()) return
        if (gameFragment() == null) return
        automatedPolicyPromptShown = true
        controller.setAutomatedPolicyPromptShown(true)
        persistAdventureStateAsync()
        showAutomatedPolicySelector(revertToManualOnCancel = false)
    }

    private fun refreshAutoToggle() {
        val available = availableAutomatedPlayerPolicies()
        if (autoMovementEnabled && validateAndUpdateSelectedAutomatedPolicy() == null) {
            autoMovementEnabled = false
        }
        val deciding = controller.state.pendingReward != null || pendingCommit != null
        autoToggle.isEnabled = available.isNotEmpty() && !deciding
        menuButton.isEnabled = !deciding
        autoToggle.isChecked = autoMovementEnabled && autoToggle.isEnabled
    }

    private fun validateAndUpdateSelectedAutomatedPolicy(): PlayerPolicyType? {
        val availableSelectedPolicy = selectedAutomatedPlayerPolicy
            ?.takeIf { it in availableAutomatedPlayerPolicies() }
        selectedAutomatedPlayerPolicy = availableSelectedPolicy
        controller.setLastAutomatedPlayerPolicy(availableSelectedPolicy)
        return availableSelectedPolicy
    }

    private fun availableAutomatedPlayerPolicies(): List<PlayerPolicyType> =
        automatedPlayerPolicies(controller.state.unlockedPlayerPolicies)

    private fun showMenu() {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        // Lightweight menu with: Pause/Resume, Legend, Switch player strategy,
        // Pause & Exit. Restart is intentionally omitted in Adventure mode
        // because restarting the engine without going through the controller
        // would skip the lives/streak bookkeeping. Players who want to bail
        // can use Pause & Exit (autosaves) or finish the run.
        data class MenuEntry(val labelRes: Int, val action: () -> Unit)
        val entries = buildList {
            add(MenuEntry(R.string.pause_resume) { gameFragment()?.togglePause() })
            add(MenuEntry(R.string.legend) { LegendDialog.show(this@AdventureActivity) })
            // Only show the strategy switcher when the player has unlocked
            // more than just MANUAL.
            if (controller.state.unlockedPlayerPolicies.size > 1) {
                add(MenuEntry(R.string.adventure_pick_player_strategy) { showSwitchPlayerStrategy() })
            }
            add(MenuEntry(R.string.pause_and_exit) { onPauseAndExit() })
        }
        val items = entries.map { getString(it.labelRes) }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.adventure_menu_title)
            .setItems(items) { _, which ->
                if (controller.state.pendingReward == null && pendingCommit == null) {
                    entries[which].action()
                }
            }
            .show()
    }

    private fun showSwitchPlayerStrategy() {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        val unlocked = controller.state.unlockedPlayerPolicies.toList()
        if (unlocked.size < 2) return
        val items = unlocked.map { it.label }.toTypedArray()
        val current = unlocked.indexOf(controller.state.currentPlayerPolicy).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.adventure_pick_player_strategy)
            .setSingleChoiceItems(items, current) { dialog, which ->
                if (controller.state.pendingReward != null || pendingCommit != null) {
                    dialog.dismiss()
                    return@setSingleChoiceItems
                }
                val chosen = unlocked[which]
                if (controller.setCurrentPlayerPolicy(chosen)) {
                    if (chosen == PlayerPolicyType.MANUAL) {
                        autoMovementEnabled = false
                    } else {
                        selectedAutomatedPlayerPolicy = chosen
                        autoMovementEnabled = true
                    }
                    gameFragment()?.setPlayerPolicy(chosen)
                    persistAdventureStateAsync()
                    refreshStatusBar()
                    refreshAutoToggle()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun onPauseAndExit() {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        val frag = gameFragment()
        val hud = frag?.hudState()
        if (hud?.status == GameStatus.RUNNING) {
            frag?.togglePause()
        }
        // Capture mid-maze snapshot synchronously so the best-effort
        // blocking persist below has it before we finish(). Note that
        // persistAdventureStateBlocking() can time out and proceed
        // best-effort, so the save is not strictly guaranteed.
        val engineSnapshot = if (hud?.countdownRemainingSeconds == null) {
            frag?.captureSnapshot()
        } else null
        if (engineSnapshot != null && engineSnapshot.status != GameStatus.WIN
            && engineSnapshot.status != GameStatus.LOSE) {
            controller.recordMidMazeSnapshot(engineSnapshot)
        } else {
            controller.clearMidMazeSnapshot()
        }
        persistAdventureStateBlocking()
        val intent = Intent(this, SetupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun moveUntilBlocked(direction: Direction) {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        val fragment = gameFragment()
        if (inertiaMovementEnabled) {
            fragment?.queueManualMoveUntilBlocked(direction)
        } else {
            fragment?.queueManualMove(direction)
        }
    }

    /** Polls the engine status to detect WIN/LOSE transitions exactly once. */
    private fun pollEngineStatus() {
        if (controller.state.pendingReward != null || pendingCommit != null) return
        val hud = gameFragment()?.hudState() ?: return
        val status = hud.status
        refreshStatusBar()
        if (transitionPending) {
            // Wait until the GL thread has processed the restart command
            // and the engine is back in a non-terminal state before
            // accepting further transitions.
            if (status != GameStatus.WIN && status != GameStatus.LOSE) {
                transitionPending = false
                lastObservedStatus = status
            }
            return
        }
        if (status != lastObservedStatus &&
            (status == GameStatus.WIN || status == GameStatus.LOSE)) {
            handleTerminalStatus(status, hud.elapsedSeconds, hud.steps)
        }
        lastObservedStatus = status
    }

    private fun handleTerminalStatus(status: GameStatus, elapsedSeconds: Float, steps: Int) {
        transitionPending = true
        lastObservedStatus = status
        when (status) {
            GameStatus.WIN -> handleMazeWon(elapsedSeconds, steps)
            GameStatus.LOSE -> handleMazeLost(elapsedSeconds, steps)
            GameStatus.RUNNING, GameStatus.PAUSED -> error("Expected terminal game status")
        }
    }

    private fun handleMazeWon(elapsedSeconds: Float, steps: Int) {
        // No engine pause needed: GameEngine.update() early-returns when
        // status != RUNNING, and we only get here after observing WIN.
        val completedRoute = controller.state.activeRoute
        val outcome = controller.completeMaze(elapsedSeconds = elapsedSeconds, steps = steps)
        if (!outcome.runComplete) {
            commitTransition {
                completedRoute?.let {
                    routeTelemetry.outcome(it.choiceId, true, elapsedSeconds, steps, 0)
                }
                val pending = controller.state.pendingReward
                if (pending != null && pending.routeChoices.isNotEmpty()) {
                    routeTelemetry.offered(
                        controller.config.difficulty.name, pending.mazeIndexCompleted,
                        pending.routeChoices.map { it.id },
                        pending.routeChoices.map { it.category.name.lowercase(java.util.Locale.ROOT) }
                    )
                }
                showPendingReward()
            }
            return
        }

        val title = if (outcome.runComplete) {
            getString(R.string.adventure_run_complete_title)
        } else {
            getString(R.string.adventure_maze_won_title, outcome.mazeIndexCompleted, outcome.totalMazes)
        }
        val bonusMsg = if (outcome.bonusLifeAwarded) {
            "\n" + getString(R.string.adventure_bonus_life, outcome.livesRemaining)
        } else ""

        val body = if (outcome.runComplete) {
            val livesWord = if (outcome.livesRemaining == 1)
                getString(R.string.adventure_lives_singular) else getString(R.string.adventure_lives_plural)
            val bestResult = bestStore.recordCompletedRun(
                difficultyName = controller.config.difficulty.name,
                totalElapsedSeconds = outcome.totalElapsedSeconds
            )
            val bestMsg = if (bestResult.isNewBest) {
                getString(R.string.adventure_new_best_time)
            } else {
                val previousBestSeconds = checkNotNull(bestResult.previousBestSeconds) {
                    "Internal error: previousBestSeconds unexpectedly null when " +
                        "isNewBest=${bestResult.isNewBest}, " +
                        "currentTimeSeconds=${bestResult.currentTimeSeconds}"
                }
                getString(
                    R.string.adventure_best_time,
                    AdventureTimeFormatter.format(previousBestSeconds)
                )
            }
            getString(
                R.string.adventure_run_complete_body_stats,
                outcome.totalMazes,
                outcome.livesRemaining,
                livesWord,
                AdventureTimeFormatter.format(outcome.totalElapsedSeconds),
                outcome.totalSteps,
                outcome.deathsThisRun,
                bestMsg
            ) + bonusMsg
        } else getString(R.string.adventure_powerup_prompt) + bonusMsg

        val builder = AlertDialog.Builder(this).setTitle(title).setMessage(body).setCancelable(false)
        if (outcome.runComplete) {
            // End of adventure — clear store (blocking via autosave
            // executor so a process-kill before navigation can't
            // resurrect the finished run), then finish to setup screen.
            builder.setPositiveButton(R.string.adventure_finish) { _, _ ->
                // Keep [transitionPending] latched and leave [lastObservedStatus]
                // as the terminal WIN value so a stray tick before [onPause]
                // can't re-enter [handleMazeWon] and call
                // [controller.onMazeWon] a second time during teardown.
                returnToSetup()
            }
            commitTransition {
                completedRoute?.let {
                    routeTelemetry.outcome(it.choiceId, true, elapsedSeconds, steps, 0)
                }
                rewardDialog = builder.show()
            }
            return
        }
    }

    private fun showPendingReward() {
        if (!foreground || pendingCommit != null || isFinishing || isDestroyed) return
        val pending = controller.state.pendingReward ?: return
        val generation = stateGeneration
        rewardDialog?.dismiss()
        val mazeIndex = pending.mazeIndexCompleted
        val builder = AlertDialog.Builder(this).setCancelable(false)
        when (pending.stage) {
            RewardStage.WIN_ACKNOWLEDGEMENT -> {
                val bonus = if (pending.bonusLifeAwarded) {
                    "\n" + getString(R.string.adventure_bonus_life, controller.state.livesRemaining)
                } else ""
                builder.setTitle(getString(
                    R.string.adventure_maze_won_title, mazeIndex, controller.config.totalMazes
                )).setMessage(getString(R.string.adventure_powerup_prompt) + bonus)
                    .setPositiveButton(R.string.adventure_continue) { _, _ ->
                        if (!acceptRewardCallback(generation, pending)) return@setPositiveButton
                        if (controller.acknowledgeMazeWin(mazeIndex)) {
                            commitTransition { showPendingReward() }
                        }
                    }
            }
            RewardStage.ROUTE_CHOICE -> {
                val choices = pending.routeChoices
                val labels = choices.map { choice ->
                    getString(
                        R.string.adventure_route_choice_accessibility,
                        routeName(choice.id), routeCategory(choice.category), routeDescription(choice.id)
                    )
                }.toTypedArray()
                builder.setTitle(R.string.adventure_route_chooser_title)
                    .setSingleChoiceItems(labels, 0, null)
                    .setPositiveButton(R.string.adventure_continue) { dialog, _ ->
                        if (!acceptRewardCallback(generation, pending)) return@setPositiveButton
                        val index = (dialog as AlertDialog).listView.checkedItemPosition
                        val choice = choices.getOrNull(index) ?: return@setPositiveButton
                        if (controller.chooseRoute(mazeIndex, choice.id)) {
                            val route = controller.state.activeRoute
                            commitTransition {
                                routeTelemetry.chosen(
                                    controller.config.difficulty.name, mazeIndex, choice.id,
                                    choice.category.name.lowercase(java.util.Locale.ROOT),
                                    controller.state.livesRemaining, controller.state.deathsThisRun
                                )
                                route?.let {
                                    routeTelemetry.applied(
                                        it.mazeIndexAppliedTo, it.choiceId,
                                        it.npcCountDelta, it.rewardOptionDelta
                                    )
                                }
                                showPendingReward()
                            }
                        }
                    }
            }
            RewardStage.POWER_UP_CHOICE -> {
                val candidates = pending.powerUpCandidates
                val preview = pending.preview?.let {
                    getString(
                        R.string.adventure_route_preview,
                        it.npcCount, it.nextEventMazeIndex,
                        it.categories.joinToString(", ") { category -> routeCategory(category) }
                    ) + "\n"
                } ?: ""
                val title = if (pending.selectedRouteId == RouteEventGenerator.SUPPLY_CACHE) {
                    getString(R.string.adventure_route_supply_cache_name)
                } else getString(R.string.adventure_powerup_prompt)
                builder.setTitle(preview + title)
                    .setSingleChoiceItems(candidates.map { it.label }.toTypedArray(), 0, null)
                    .setPositiveButton(R.string.adventure_continue) { dialog, _ ->
                        if (!acceptRewardCallback(generation, pending)) return@setPositiveButton
                        val index = (dialog as AlertDialog).listView.checkedItemPosition
                        val choice = candidates.getOrNull(index) ?: return@setPositiveButton
                        if (controller.chooseStartingPowerUp(mazeIndex, choice)) {
                            advanceToNextMaze()
                        }
                    }
                if (controller.state.rewardRerolls > 0) {
                    builder.setNeutralButton(R.string.adventure_route_reroll) { _, _ ->
                        if (!acceptRewardCallback(generation, pending)) return@setNeutralButton
                        if (controller.rerollStartingPowerUps(mazeIndex)) {
                            commitTransition { showPendingReward() }
                        }
                    }
                }
            }
        }
        rewardDialog = builder.show()
    }

    private fun acceptRewardCallback(generation: Long, reward: PendingAdventureReward): Boolean =
        !isDestroyed && !isFinishing && pendingCommit == null &&
            generation == stateGeneration && controller.state.pendingReward == reward

    private fun routeCategory(category: RouteEventCategory): String = getString(when (category) {
        RouteEventCategory.SAFE -> R.string.adventure_route_category_safe
        RouteEventCategory.RISKY -> R.string.adventure_route_category_risky
        RouteEventCategory.UTILITY -> R.string.adventure_route_category_utility
    })

    private fun routeName(id: String): String = getString(when (id) {
        RouteEventGenerator.QUIET_CORRIDOR -> R.string.adventure_route_quiet_corridor_name
        RouteEventGenerator.AMBUSH_SHORTCUT -> R.string.adventure_route_ambush_shortcut_name
        RouteEventGenerator.SUPPLY_CACHE -> R.string.adventure_route_supply_cache_name
        RouteEventGenerator.SCOUT_MAP -> R.string.adventure_route_scout_map_name
        RouteEventGenerator.CURSED_GATE -> R.string.adventure_route_cursed_gate_name
        else -> error("Unknown route $id")
    })

    private fun routeDescription(id: String): String = getString(when (id) {
        RouteEventGenerator.QUIET_CORRIDOR -> R.string.adventure_route_quiet_corridor_description
        RouteEventGenerator.AMBUSH_SHORTCUT -> R.string.adventure_route_ambush_shortcut_description
        RouteEventGenerator.SUPPLY_CACHE -> R.string.adventure_route_supply_cache_description
        RouteEventGenerator.SCOUT_MAP -> R.string.adventure_route_scout_map_description
        RouteEventGenerator.CURSED_GATE -> R.string.adventure_route_cursed_gate_description
        else -> error("Unknown route $id")
    })

    private fun advanceToNextMaze() {
        if (pendingCommit != null) return
        val spec = controller.prepareCurrentMaze()
        if (spec == null) {
            // Defensive: controller already terminal, return to setup.
            adventureStore.clear()
            returnToSetup()
            return
        }
        commitTransition {
            val fragment = gameFragment()
            if (fragment == null) {
                attachMaze(spec)
            } else {
                fragment.configureAdventureMaze(
                    seed = spec.seed,
                    difficulty = spec.difficulty.name,
                    playerPolicy = spec.playerPolicy,
                    npcCount = spec.npcCount,
                    npcPolicies = spec.npcPolicies,
                    startingPowerUp = spec.startingPowerUp,
                    pickupLifetimeSeconds = spec.pickupLifetimeSeconds
                )
            }
            refreshStatusBar()
            refreshAutoToggle()
            promptForAutomatedPolicyIfNeeded()
        }
        // transitionPending stays `true` until pollEngineStatus observes
        // a non-terminal status (i.e. the GL thread has applied the
        // restart command). This prevents the engine's still-WIN status
        // from re-triggering the win handler on the next poll.
    }

    private fun handleMazeLost(elapsedSeconds: Float, steps: Int) {
        val route = controller.state.activeRoute
        val outcome = controller.onPlayerDied()
        val title = if (outcome.runOver)
            getString(R.string.adventure_run_lost_title)
        else
            getString(R.string.adventure_caught_title, outcome.livesRemaining)
        val body = if (outcome.runOver)
            getString(
                R.string.adventure_run_lost_body_stats,
                controller.state.currentMazeIndex + 1,
                controller.config.totalMazes,
                AdventureTimeFormatter.format(controller.state.totalElapsedSeconds),
                controller.state.totalSteps,
                controller.state.deathsThisRun
            )
        else
            ""
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setCancelable(false)
        if (body.isNotEmpty()) builder.setMessage(body)
        if (outcome.runOver) {
            // Run lost — clear store (blocking via autosave executor so
            // a process-kill before navigation can't resurrect the lost
            // run), then finish to setup screen.
            builder.setPositiveButton(R.string.adventure_finish) { _, _ ->
                // Keep [transitionPending] latched and leave [lastObservedStatus]
                // as the terminal LOSE value so a stray tick before [onPause]
                // can't re-enter [handleMazeLost] and call
                // [controller.onPlayerDied] a second time during teardown.
                returnToSetup()
            }
        } else {
            builder.setPositiveButton(R.string.adventure_continue) { _, _ ->
                // Replay same maze (controller preserved seed + per-NPC policies).
                advanceToNextMaze()
            }
        }
        commitTransition {
            route?.let {
                routeTelemetry.outcome(
                    it.choiceId, false, elapsedSeconds, steps, 1
                )
            }
            rewardDialog = builder.show()
        }
    }

    private fun returnToSetup() {
        val intent = Intent(this, SetupActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }
        startActivity(intent)
        finish()
    }

    private fun refreshStatusBar() {
        val state = controller.state
        val displayIndex = (state.currentMazeIndex + 1).coerceAtMost(controller.config.totalMazes)
        statusBar.text = getString(
            R.string.adventure_status_format,
            displayIndex,
            controller.config.totalMazes,
            AdventureTimeFormatter.format(state.totalElapsedSeconds),
            state.totalSteps,
            state.livesRemaining,
            state.winStreakSinceLastBonus,
            AdventureConfig.STREAK_BONUS_THRESHOLD
        )
        refreshAutoToggle()
    }

    private fun gameFragment(): GameFragment? {
        return supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
    }

    private fun setupSwipeControls() {
        inputController.setupSwipeControls()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        inputController.onDispatchTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    companion object {
        private const val TICK_INTERVAL_MS = 200L
        private const val SAVE_TIMEOUT_MS = 750L
        private const val KEY_AUTO_MOVEMENT_ENABLED = "autoMovementEnabled"
        private const val KEY_INERTIA_MOVEMENT_ENABLED = "inertiaMovementEnabled"
        private const val KEY_SELECTED_AUTO_POLICY = "selectedAutomatedPlayerPolicy"
        private const val KEY_AUTO_PROMPT_SHOWN = "automatedPolicyPromptShown"
    }
}
