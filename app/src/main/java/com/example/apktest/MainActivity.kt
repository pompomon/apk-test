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
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.badlogic.gdx.backends.android.AndroidFragmentApplication
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.Direction
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.ui.GameMenuPopover
import com.example.apktest.ui.LegendDialog

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {
    private var menuPopover: GameMenuPopover? = null
    private lateinit var menuButton: ImageButton
    private lateinit var stateStore: GameStateStore

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal var resolvedSwipeCount: Int = 0
        private set

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun isMenuPopoverShowingForTesting(): Boolean = menuPopover?.isShowing == true

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun menuPopoverTextSnapshotForTesting(): List<String> =
        menuPopover?.textSnapshotForTesting().orEmpty()

    /**
     * Feeds a MotionEvent directly into the swipe gesture detector, bypassing
     * `dispatchTouchEvent`'s hit-testing. Used by instrumentation tests to verify swipe
     * resolution without depending on the emulator's input pipeline (which can drop synthetic
     * events) or the cross-app injection permission required by `Instrumentation.sendPointerSync`.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun feedSwipeEventForTesting(event: MotionEvent) {
        swipeGestureDetector?.onTouchEvent(event)
    }

    private var swipeGestureDetector: GestureDetector? = null
    private var swipeGestureActive: Boolean = false
    private val gameHostWindowRect = android.graphics.Rect()
    private val overlayWindowRect = android.graphics.Rect()
    private val viewWindowLocation = IntArray(2)

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

        if (savedInstanceState == null) {
            val fragment = GameFragment().apply {
                arguments = buildGameFragmentArgs(intent)
            }
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentGameHost, fragment)
                .commit()
        }

        setupControls()
        setupSwipeControls()
    }

    private fun buildGameFragmentArgs(intent: Intent): Bundle {
        // If the launcher asked us to resume, pull the saved snapshot's JSON
        // straight into the fragment args so the engine can restore it
        // before the first render tick. Falls back to a fresh game (using
        // the intent's policy/difficulty extras) when no snapshot exists.
        val resume = intent.getBooleanExtra(SetupActivity.EXTRA_RESUME, false)
        val resumeJson = if (resume) stateStore.loadRawJson() else null

        if (resumeJson != null) {
            return Bundle().apply {
                putString(GameFragment.ARG_RESUME_SNAPSHOT_JSON, resumeJson)
            }
        }

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
        return Bundle().apply {
            putString(GameFragment.ARG_PLAYER_POLICY, player.name)
            putString(GameFragment.ARG_NPC_POLICY, npc.name)
            putString(GameFragment.ARG_DIFFICULTY, difficulty)
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
        menuPopover?.dismiss()
        // Autosave so the user can resume after a process death, switching
        // away, or just to be safe before any incidental finish(). Skip
        // terminal states (WIN/LOSE) so re-launching doesn't drop the
        // player straight back into the end-of-game overlay.
        //
        // The snapshot capture is performed asynchronously on the GL
        // thread: blocking the UI thread here risks ANR / jank on slower
        // devices when the render loop is busy or starved. The state store
        // write is hopped onto a background thread for the same reason
        // (SharedPreferences#commit performs synchronous disk I/O).
        val hud = gameFragment()?.hudState()
        if (hud != null && (hud.status == GameStatus.RUNNING || hud.status == GameStatus.PAUSED)) {
            gameFragment()?.captureSnapshotAsync { snapshot ->
                Thread { stateStore.save(snapshot) }.start()
            }
        }
        super.onPause()
    }

    private fun setupControls() {
        menuButton.setOnClickListener { showMenuPopover() }

        findViewById<Button>(R.id.buttonUp).setOnClickListener { move(Direction.NORTH) }
        findViewById<Button>(R.id.buttonDown).setOnClickListener { move(Direction.SOUTH) }
        findViewById<Button>(R.id.buttonLeft).setOnClickListener { move(Direction.WEST) }
        findViewById<Button>(R.id.buttonRight).setOnClickListener { move(Direction.EAST) }
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
                    gameFragment()?.captureSnapshot()?.let { stateStore.save(it) }
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

    private fun move(direction: Direction) {
        gameFragment()?.queueManualMove(direction)
        refreshHudSnapshot()
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
                    resolvedSwipeCount++
                    move(direction)
                    return true
                }
            }
        )
    }

    // Routes touches that begin inside the game host through the swipe detector before the
    // regular dispatch path, so swipes are observed even when child views (e.g., the libGDX
    // SurfaceView inside fragmentGameHost) consume the events. Touches that start on overlay
    // UI (the hamburger button or D-pad) are NOT forwarded so taps/scrolls on controls don't
    // trigger unintended player moves. We never consume the event here so existing dispatch
    // behavior for child views and arrow-button controls is preserved.
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val detector = swipeGestureDetector
        if (detector != null) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    swipeGestureActive = isEventInsideGameHost(ev)
                    if (swipeGestureActive) {
                        detector.onTouchEvent(ev)
                    }
                }
                MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                    if (swipeGestureActive) {
                        detector.onTouchEvent(ev)
                    }
                    swipeGestureActive = false
                }
                else -> {
                    if (swipeGestureActive) {
                        detector.onTouchEvent(ev)
                    }
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isEventInsideGameHost(ev: MotionEvent): Boolean {
        val gameHost = findViewById<View>(R.id.fragmentGameHost) ?: return false
        if (!fillWindowRect(gameHost, gameHostWindowRect)) return false
        // MotionEvent.x/y in Activity.dispatchTouchEvent are window/decor-relative, so compare
        // against window-relative rects (getLocationInWindow + width/height) for consistent space.
        val x = ev.x.toInt()
        val y = ev.y.toInt()
        if (!gameHostWindowRect.contains(x, y)) return false
        // The fragment host now spans the full screen above the D-pad; exclude touches that
        // fall on the hamburger button (top-right overlay) and on the D-pad so taps/flings on
        // controls don't trigger unintended player moves.
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
        // 4x touch slop requires a deliberate drag, reducing accidental move input from taps/jitter.
        private const val SWIPE_DISTANCE_TOUCH_SLOP_MULTIPLIER = 4
    }
}
