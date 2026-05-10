package com.example.apktest

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
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

class MainActivity : AppCompatActivity(), AndroidFragmentApplication.Callbacks {
    private lateinit var statusText: TextView
    private lateinit var speedText: TextView
    private lateinit var powerUpText: TextView

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal var resolvedSwipeCount: Int = 0
        private set

    private var swipeGestureDetector: GestureDetector? = null
    private var swipeGestureActive: Boolean = false
    private val gameHostWindowRect = android.graphics.Rect()

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
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentGameHost, GameFragment())
                .commit()
        }

        setupControls()
        setupSwipeControls()
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
        val playerSpinner = findViewById<Spinner>(R.id.spinnerPlayerPolicy)
        val npcSpinner = findViewById<Spinner>(R.id.spinnerNpcPolicy)
        val difficultySpinner = findViewById<Spinner>(R.id.spinnerDifficulty)

        playerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            PlayerPolicyType.entries.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        npcSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            NpcPolicyType.entries.map { it.label }
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        val difficulties = DifficultyPresets.all.map { it.name }
        difficultySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            difficulties
        ).apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val defaultDifficultyIndex = difficulties.indexOf(DifficultyPresets.MEDIUM.name)
            .takeIf { it >= 0 }
            ?: 0
        difficultySpinner.setSelection(defaultDifficultyIndex)

        findViewById<Button>(R.id.buttonApply).setOnClickListener {
            val fragment = gameFragment() ?: return@setOnClickListener
            val selectedPlayer = PlayerPolicyType.entries[playerSpinner.selectedItemPosition]
            val selectedNpc = NpcPolicyType.entries[npcSpinner.selectedItemPosition]
            val selectedDifficulty = difficultySpinner.selectedItem.toString()

            fragment.setPlayerPolicy(selectedPlayer)
            fragment.setNpcPolicy(selectedNpc)
            fragment.setDifficulty(selectedDifficulty)
            refreshHudSnapshot()
        }

        findViewById<Button>(R.id.buttonPause).setOnClickListener {
            gameFragment()?.togglePause()
            refreshHudSnapshot()
        }

        findViewById<Button>(R.id.buttonRestart).setOnClickListener {
            gameFragment()?.restart()
            refreshHudSnapshot()
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
        if (gameHost.width <= 0 || gameHost.height <= 0) return false
        gameHost.getGlobalVisibleRect(gameHostWindowRect)
        // ev coordinates from dispatchTouchEvent are in window/local-decor coords; getGlobalVisibleRect
        // returns window-relative coords for the unobscured visible portion of the view.
        return gameHostWindowRect.contains(ev.x.toInt(), ev.y.toInt())
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
