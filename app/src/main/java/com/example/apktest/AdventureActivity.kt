package com.example.apktest

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.AdventureRunController
import com.example.apktest.game.core.AdventureRunStateSnapshot
import com.example.apktest.game.core.AdventureStatus
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.ui.LegendDialog
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
    private val autosaveExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private lateinit var controller: AdventureRunController
    private var runSeed: Long = 0L

    private lateinit var menuButton: ImageButton
    private lateinit var statusBar: TextView

    // Tracks the previous tick's engine status so we can detect WIN/LOSE
    // transitions exactly once and run the corresponding overlay flow.
    private var lastObservedStatus: GameStatus = GameStatus.RUNNING
    private var transitionPending: Boolean = false

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun adventureStatusBarTextForTesting(): CharSequence = statusBar.text

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun controllerForTesting(): AdventureRunController = controller

    private var swipeGestureDetector: GestureDetector? = null
    private var swipeGestureActive: Boolean = false
    private val gameHostWindowRect = android.graphics.Rect()
    private val overlayWindowRect = android.graphics.Rect()
    private val viewWindowLocation = IntArray(2)

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

        val root = findViewById<View>(R.id.adventureRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        statusBar = findViewById(R.id.adventureStatusBar)
        menuButton = findViewById(R.id.buttonMenu)

        val (initialController, initialSeed, isFreshStart) = loadOrBuildController(intent, savedInstanceState)
        controller = initialController
        runSeed = initialSeed

        // Always (re)create the GameFragment from the current controller
        // spec, including on activity recreation (savedInstanceState != null,
        // typically process-death). The FragmentManager would otherwise
        // restore the previous GameFragment with its original arguments,
        // which can be stale (wrong maze index / wrong mid-maze snapshot)
        // versus the persisted controller state we just loaded. Replacing
        // the fragment reconciles the engine/UI with controller.prepareCurrentMaze().
        val spec = controller.prepareCurrentMaze()
        if (spec == null) {
            // Defensive: terminal state recovered from store. Clear and bail.
            adventureStore.clear()
            returnToSetup()
            return
        }
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
                npcPolicies = spec.npcPolicies
            )
        }

        setupControls()
        setupSwipeControls()
        refreshStatusBar()

        if (isFreshStart) {
            // Eagerly persist the freshly-built run state with commit() so a
            // process death before the first autosave can't resurrect a
            // previous run (AdventureSetupActivity's clear() uses async
            // apply(), which is not flushed yet at this point).
            persistAdventureStateBlocking()
        }
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
                runSeed = saved.runSeed
            )
            return Triple(controller, saved.runSeed, false)
        }
        val difficultyName = intent.getStringExtra(AdventureSetupActivity.EXTRA_DIFFICULTY)
            ?: DifficultyPresets.EASY.name
        val config = AdventureConfig.forDifficulty(DifficultyPresets.byName(difficultyName))
        val seed = System.currentTimeMillis()
        return Triple(AdventureRunController(config = config, runSeed = seed), seed, true)
    }

    override fun exit() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        tickHandler.removeCallbacks(tickRunnable)
        tickHandler.postDelayed(tickRunnable, TICK_INTERVAL_MS)
    }

    override fun onPause() {
        tickHandler.removeCallbacks(tickRunnable)
        // Adventure runs that are already terminal (WON/LOST) clear the
        // store so the next launch doesn't try to "resume" a finished run.
        if (controller.state.status != AdventureStatus.IN_PROGRESS) {
            try {
                autosaveExecutor.execute { adventureStore.clearBlocking() }
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
        if (hud?.countdownRemainingSeconds != null) {
            controller.clearMidMazeSnapshot()
            persistAdventureStateAsync()
            super.onPause()
            return
        }
        if (frag != null && (status == GameStatus.RUNNING || status == GameStatus.PAUSED)) {
            frag.captureSnapshotAsync { engineSnapshot ->
                // captureSnapshotAsync's callback fires on the GL thread.
                // Hop back to the main thread before mutating the
                // controller (not thread-safe) and before reading state
                // into a serialisable snapshot.
                tickHandler.post {
                    try {
                        if (engineSnapshot.status == GameStatus.WIN ||
                            engineSnapshot.status == GameStatus.LOSE
                        ) {
                            // Don't persist terminal snapshots; relaunch
                            // would re-trigger the overlay flow.
                            controller.clearMidMazeSnapshot()
                        } else {
                            controller.recordMidMazeSnapshot(engineSnapshot)
                        }
                        persistAdventureStateAsync()
                    } catch (_: RejectedExecutionException) {
                        // Executor shut down between hop and persist.
                    }
                }
            }
        } else {
            // WIN/LOSE were handled by the engine before we got here
            // and the controller already transitioned via pollEngineStatus;
            // persist the run-level state without a mid-maze snapshot.
            controller.clearMidMazeSnapshot()
            persistAdventureStateAsync()
        }
        super.onPause()
    }

    override fun onDestroy() {
        autosaveExecutor.shutdown()
        super.onDestroy()
    }

    private fun persistAdventureStateAsync() {
        val snapshot = AdventureRunStateSnapshot.fromState(controller.state, runSeed)
        try {
            autosaveExecutor.execute { adventureStore.save(snapshot) }
        } catch (_: RejectedExecutionException) {
            // Executor shut down — accept the loss.
        }
    }

    private fun persistAdventureStateBlocking() {
        val snapshot = AdventureRunStateSnapshot.fromState(controller.state, runSeed)
        try {
            autosaveExecutor.submit { adventureStore.saveBlocking(snapshot) }
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
        findViewById<Button>(R.id.buttonUp).setOnClickListener { move(Direction.NORTH) }
        findViewById<Button>(R.id.buttonDown).setOnClickListener { move(Direction.SOUTH) }
        findViewById<Button>(R.id.buttonLeft).setOnClickListener { move(Direction.WEST) }
        findViewById<Button>(R.id.buttonRight).setOnClickListener { move(Direction.EAST) }
    }

    private fun showMenu() {
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
                entries[which].action()
            }
            .show()
    }

    private fun showSwitchPlayerStrategy() {
        val unlocked = controller.state.unlockedPlayerPolicies.toList()
        if (unlocked.size < 2) return
        val items = unlocked.map { it.label }.toTypedArray()
        val current = unlocked.indexOf(controller.state.currentPlayerPolicy).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.adventure_pick_player_strategy)
            .setSingleChoiceItems(items, current) { dialog, which ->
                val chosen = unlocked[which]
                if (controller.setCurrentPlayerPolicy(chosen)) {
                    gameFragment()?.setPlayerPolicy(chosen)
                    refreshStatusBar()
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun onPauseAndExit() {
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

    private fun move(direction: Direction) {
        gameFragment()?.queueManualMove(direction)
    }

    /** Polls the engine status to detect WIN/LOSE transitions exactly once. */
    private fun pollEngineStatus() {
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
            transitionPending = true
            when (status) {
                GameStatus.WIN -> handleMazeWon()
                GameStatus.LOSE -> handleMazeLost()
                else -> {}
            }
        }
        lastObservedStatus = status
    }

    private fun handleMazeWon() {
        // No engine pause needed: GameEngine.update() early-returns when
        // status != RUNNING, and we only get here after observing WIN.
        val outcome = controller.onMazeWon()
        // Intentionally do NOT persist controller state here. The controller
        // has advanced (mazeIndex / lives / streak), but if the run is
        // continuing the player still has to confirm the win dialog and,
        // when applicable, pick an unlock. Persisting now would mean a
        // process death while the dialogs are visible drops the unlock
        // choice while keeping the advanced state. Instead we persist
        // only after the user-driven follow-ups commit: [advanceToNextMaze]
        // (no-unlock continue) or [showUnlockChooser]'s positive button.
        // If the process dies before either fires, on resume the persisted
        // state is still at the previous maze and the player simply replays it.

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
            getString(
                R.string.adventure_run_complete_body,
                outcome.totalMazes, outcome.livesRemaining, livesWord
            ) + bonusMsg
        } else {
            (if (outcome.unlockAvailable)
                getString(R.string.adventure_unlock_prompt)
            else
                getString(R.string.adventure_no_unlock_available)) + bonusMsg
        }

        val builder = AlertDialog.Builder(this).setTitle(title).setMessage(body).setCancelable(false)
        if (outcome.runComplete) {
            // End of adventure — clear store, finish to setup screen.
            adventureStore.clear()
            builder.setPositiveButton(R.string.adventure_finish) { _, _ ->
                transitionPending = false
                lastObservedStatus = GameStatus.RUNNING
                returnToSetup()
            }
            builder.show()
            return
        }

        if (outcome.unlockAvailable) {
            // Present the unlock chooser as a follow-up dialog.
            builder.setPositiveButton(R.string.adventure_continue) { _, _ ->
                showUnlockChooser(outcome.unlockCandidates)
            }
        } else {
            builder.setPositiveButton(R.string.adventure_continue) { _, _ ->
                advanceToNextMaze()
            }
        }
        builder.show()
    }

    private fun showUnlockChooser(candidates: List<PlayerPolicyType>) {
        val items = candidates.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.adventure_unlock_prompt)
            .setCancelable(false)
            .setSingleChoiceItems(items, 0, null)
            .setPositiveButton(R.string.adventure_continue) { dialog, _ ->
                val listView = (dialog as AlertDialog).listView
                val picked = listView.checkedItemPosition.coerceAtLeast(0)
                controller.applyPolicyUnlock(candidates[picked])
                persistAdventureStateAsync()
                advanceToNextMaze()
            }
            .show()
    }

    private fun advanceToNextMaze() {
        val spec = controller.prepareCurrentMaze()
        if (spec == null) {
            // Defensive: controller already terminal, return to setup.
            adventureStore.clear()
            returnToSetup()
            return
        }
        gameFragment()?.configureAdventureMaze(
            seed = spec.seed,
            difficulty = spec.difficulty.name,
            playerPolicy = spec.playerPolicy,
            npcCount = spec.npcCount,
            npcPolicies = spec.npcPolicies
        )
        persistAdventureStateAsync()
        // transitionPending stays `true` until pollEngineStatus observes
        // a non-terminal status (i.e. the GL thread has applied the
        // restart command). This prevents the engine's still-WIN status
        // from re-triggering the win handler on the next poll.
        refreshStatusBar()
    }

    private fun handleMazeLost() {
        val outcome = controller.onPlayerDied()
        persistAdventureStateAsync()
        val title = if (outcome.runOver)
            getString(R.string.adventure_run_lost_title)
        else
            getString(R.string.adventure_caught_title, outcome.livesRemaining)
        val body = if (outcome.runOver)
            getString(
                R.string.adventure_run_lost_body,
                controller.state.currentMazeIndex + 1,
                controller.config.totalMazes
            )
        else
            ""
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setCancelable(false)
        if (body.isNotEmpty()) builder.setMessage(body)
        if (outcome.runOver) {
            adventureStore.clear()
            builder.setPositiveButton(R.string.adventure_finish) { _, _ ->
                transitionPending = false
                lastObservedStatus = GameStatus.RUNNING
                returnToSetup()
            }
        } else {
            builder.setPositiveButton(R.string.adventure_continue) { _, _ ->
                // Replay same maze (controller preserved seed + per-NPC policies).
                advanceToNextMaze()
            }
        }
        builder.show()
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
            state.livesRemaining,
            state.winStreakSinceLastBonus,
            AdventureConfig.STREAK_BONUS_THRESHOLD
        )
    }

    private fun gameFragment(): GameFragment? {
        return supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
    }

    private fun setupSwipeControls() {
        val viewConfig = ViewConfiguration.get(this)
        val minDistance = (viewConfig.scaledTouchSlop * SWIPE_DISTANCE_TOUCH_SLOP_MULTIPLIER).toFloat()
        val minVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
        swipeGestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true
                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val direction = SwipeDirectionResolver.resolve(
                        deltaX = e2.x - start.x,
                        deltaY = e2.y - start.y,
                        velocityX = velocityX,
                        velocityY = velocityY,
                        minDistance = minDistance,
                        minVelocity = minVelocity
                    ) ?: return false
                    move(direction)
                    return true
                }
            }
        )
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val detector = swipeGestureDetector
        if (detector != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeGestureActive = isEventInsideGameHost(ev)
                    if (swipeGestureActive) detector.onTouchEvent(ev)
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    if (swipeGestureActive) detector.onTouchEvent(ev)
                    swipeGestureActive = false
                }
                else -> if (swipeGestureActive) detector.onTouchEvent(ev)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isEventInsideGameHost(ev: MotionEvent): Boolean {
        val gameHost = findViewById<View>(R.id.fragmentGameHost) ?: return false
        if (!fillWindowRect(gameHost, gameHostWindowRect)) return false
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        if (!gameHostWindowRect.contains(x, y)) return false
        if (isInsideOverlay(R.id.buttonMenu, x, y)) return false
        if (isInsideOverlay(R.id.bottomControls, x, y)) return false
        return true
    }

    private fun isInsideOverlay(viewId: Int, x: Int, y: Int): Boolean {
        val overlay = findViewById<View>(viewId) ?: return false
        if (!fillWindowRect(overlay, overlayWindowRect)) return false
        return overlayWindowRect.contains(x, y)
    }

    private fun fillWindowRect(view: View, out: android.graphics.Rect): Boolean {
        if (view.width <= 0 || view.height <= 0) return false
        view.getLocationInWindow(viewWindowLocation)
        val left = viewWindowLocation[0]
        val top = viewWindowLocation[1]
        out.set(left, top, left + view.width, top + view.height)
        return true
    }

    companion object {
        private const val TICK_INTERVAL_MS = 200L
        private const val SWIPE_DISTANCE_TOUCH_SLOP_MULTIPLIER = 4
        private const val SAVE_TIMEOUT_MS = 750L
    }
}
