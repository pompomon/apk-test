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
import android.widget.TextView
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
import com.example.apktest.ui.LegendDialog

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {
    private lateinit var statusText: TextView
    private lateinit var speedText: TextView
    private lateinit var powerUpText: TextView

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal var resolvedSwipeCount: Int = 0
        private set

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

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        statusText = findViewById(R.id.textStatus)
        speedText = findViewById(R.id.textSpeed)
        powerUpText = findViewById(R.id.textPowerUps)

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
        // Resolve selections from the launching intent; fall back to safe
        // defaults so the activity remains usable if launched directly.
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
        refreshHudSnapshot()
        hudHandler.removeCallbacks(hudRefreshRunnable)
        hudHandler.postDelayed(hudRefreshRunnable, HUD_REFRESH_INTERVAL_MS)
    }

    override fun onPause() {
        hudHandler.removeCallbacks(hudRefreshRunnable)
        super.onPause()
    }

    private fun setupControls() {
        findViewById<Button>(R.id.buttonPause).setOnClickListener {
            gameFragment()?.togglePause()
            refreshHudSnapshot()
        }

        findViewById<Button>(R.id.buttonRestart).setOnClickListener {
            gameFragment()?.restart()
            refreshHudSnapshot()
        }

        findViewById<Button>(R.id.buttonLegend).setOnClickListener {
            LegendDialog.show(this)
        }

        findViewById<Button>(R.id.buttonBackToSetup).setOnClickListener {
            val intent = Intent(this, SetupActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            startActivity(intent)
            finish()
        }

        findViewById<Button>(R.id.buttonUp).setOnClickListener { move(Direction.NORTH) }
        findViewById<Button>(R.id.buttonDown).setOnClickListener { move(Direction.SOUTH) }
        findViewById<Button>(R.id.buttonLeft).setOnClickListener { move(Direction.WEST) }
        findViewById<Button>(R.id.buttonRight).setOnClickListener { move(Direction.EAST) }
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
    // UI (spinners/buttons) are NOT forwarded so scrolls/flings on controls don't trigger
    // unintended player moves. We never consume the event here so existing dispatch behavior
    // for child views and arrow-button controls is preserved.
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
        // The fragment host spans the full screen and is overlapped by the top/bottom overlays;
        // exclude touches that fall on those overlay regions so swipes/flings on spinners and
        // arrow buttons don't trigger unintended player moves.
        if (isInsideOverlay(R.id.topOverlay, x, y)) return false
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
        val hud = gameFragment()?.hudState() ?: return
        statusText.text = getString(
            R.string.status_template,
            displayStatus(hud.status),
            hud.steps,
            hud.elapsedSeconds
        )
        speedText.text = getString(
            R.string.speed_detail_template,
            hud.playerSpeed,
            hud.npcSpeed,
            hud.playerPolicyLabel,
            hud.npcPolicyLabel
        )
        powerUpText.text = getString(
            R.string.powerups_detail_template,
            hud.activePowerUps.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: getString(R.string.powerups_none),
            hud.powerUpsOnMap
        )
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
