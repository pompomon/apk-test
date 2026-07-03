package com.example.apktest

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.Button
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.ui.LegendDialog

/**
 * Start menu: presents Adventure and Classic as the top-level choices.
 * Classic-specific actions live in a one-level-deep panel so mode choice
 * remains the first decision on launch.
 */
class SetupActivity : AppCompatActivity() {
    private lateinit var stateStore: GameStateStore

    private var selectedPlayerPolicy: PlayerPolicyType = PlayerPolicyType.MANUAL
    private var selectedNpcPolicy: NpcPolicyType = NpcPolicyType.DIRECT_CHASE
    private var selectedDifficultyName: String = DifficultyPresets.MEDIUM.name

    private lateinit var buttonResume: Button
    private lateinit var buttonPickDifficulty: Button
    private lateinit var buttonPickPlayerStrategy: Button
    private lateinit var buttonPickNpcStrategy: Button
    private lateinit var startMenuRootButtonColumn: View
    private lateinit var startMenuClassicButtonColumn: View

    // When the Classic panel is showing, a system Back press should return
    // to the root menu rather than finishing the activity, so Classic behaves
    // like a real one-level-deep submenu.
    private val classicBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            showRootMenu()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        stateStore = GameStateStore(this)

        onBackPressedDispatcher.addCallback(this, classicBackCallback)

        // Restore selections across process death / config change so the
        // user doesn't lose their picks if the OS recreates this activity.
        if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_PLAYER_POLICY)?.let { name ->
                selectedPlayerPolicy = PlayerPolicyType.entries
                    .firstOrNull { it.name == name } ?: selectedPlayerPolicy
            }
            savedInstanceState.getString(STATE_NPC_POLICY)?.let { name ->
                selectedNpcPolicy = NpcPolicyType.entries
                    .firstOrNull { it.name == name } ?: selectedNpcPolicy
            }
            savedInstanceState.getString(STATE_DIFFICULTY)?.let { name ->
                if (DifficultyPresets.all.any { it.name == name }) {
                    selectedDifficultyName = name
                }
            }
        }

        val root = findViewById<android.view.View>(R.id.setupRoot)
        val initialPaddingLeft = root.paddingLeft
        val initialPaddingTop = root.paddingTop
        val initialPaddingRight = root.paddingRight
        val initialPaddingBottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                initialPaddingLeft + bars.left,
                initialPaddingTop + bars.top,
                initialPaddingRight + bars.right,
                initialPaddingBottom + bars.bottom
            )
            insets
        }

        buttonResume = findViewById(R.id.buttonResume)
        buttonPickDifficulty = findViewById(R.id.buttonPickDifficulty)
        buttonPickPlayerStrategy = findViewById(R.id.buttonPickPlayerStrategy)
        buttonPickNpcStrategy = findViewById(R.id.buttonPickNpcStrategy)
        startMenuRootButtonColumn = findViewById(R.id.startMenuRootButtonColumn)
        startMenuClassicButtonColumn = findViewById(R.id.startMenuClassicButtonColumn)
        val buttonStart = findViewById<Button>(R.id.buttonStart)
        val buttonAdventure = findViewById<Button>(R.id.buttonAdventureMode)
        val buttonClassic = findViewById<Button>(R.id.buttonClassicMode)
        val buttonLegend = findViewById<Button>(R.id.buttonLegend)
        val buttonClassicBack = findViewById<Button>(R.id.buttonClassicBack)

        refreshSelectionLabels()
        refreshResumeButtonState()
        val showingClassicMenu = savedInstanceState
            ?.getBoolean(STATE_SHOWING_CLASSIC_MENU, false) ?: false
        if (showingClassicMenu) {
            showClassicMenu()
        } else {
            showRootMenu()
        }

        buttonResume.setOnClickListener {
            // Also forward the currently selected difficulty/player/npc so
            // that if the saved snapshot is missing/stale by the time
            // MainActivity loads it, the fallback fresh game uses the
            // user's current start-menu selections instead of hard-coded
            // defaults.
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_RESUME, true)
                putExtra(EXTRA_PLAYER_POLICY, selectedPlayerPolicy.name)
                putExtra(EXTRA_NPC_POLICY, selectedNpcPolicy.name)
                putExtra(EXTRA_DIFFICULTY, selectedDifficultyName)
            }
            startActivity(intent)
        }

        buttonStart.setOnClickListener {
            // New Game discards any saved snapshot so the next Resume click
            // doesn't bring back the previous run.
            stateStore.clear()
            refreshResumeButtonState()
            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_POLICY, selectedPlayerPolicy.name)
                putExtra(EXTRA_NPC_POLICY, selectedNpcPolicy.name)
                putExtra(EXTRA_DIFFICULTY, selectedDifficultyName)
            }
            startActivity(intent)
        }

        buttonPickDifficulty.setOnClickListener { cycleDifficulty() }
        buttonPickPlayerStrategy.setOnClickListener { cyclePlayerStrategy() }
        buttonPickNpcStrategy.setOnClickListener { cycleNpcStrategy() }
        buttonLegend.setOnClickListener { LegendDialog.show(this) }
        buttonAdventure.setOnClickListener {
            startActivity(Intent(this, AdventureSetupActivity::class.java))
        }
        buttonClassic.setOnClickListener { showClassicMenu() }
        buttonClassicBack.setOnClickListener { showRootMenu() }
    }

    override fun onResume() {
        super.onResume()
        // Saved-state availability can change while this activity is in the
        // back stack (e.g., the user paused & exited from MainActivity).
        refreshResumeButtonState()
    }

    private fun refreshResumeButtonState() {
        // Cheap presence check only — full validation / stale-blob
        // cleanup happens lazily inside MainActivity when the user
        // actually taps Resume, so we don't parse JSON on the UI thread
        // every time this start menu is shown.
        buttonResume.isEnabled = stateStore.hasRawState()
    }

    private fun refreshSelectionLabels() {
        buttonPickDifficulty.text = getString(R.string.menu_difficulty, selectedDifficultyName)
        buttonPickPlayerStrategy.text =
            getString(R.string.menu_player_strategy, selectedPlayerPolicy.label)
        buttonPickNpcStrategy.text =
            getString(R.string.menu_npc_strategy, selectedNpcPolicy.label)
    }

    private fun showRootMenu() {
        setClassicMenuVisible(false)
    }

    private fun showClassicMenu() {
        setClassicMenuVisible(true)
    }

    private fun setClassicMenuVisible(visible: Boolean) {
        startMenuRootButtonColumn.visibility = if (visible) GONE else VISIBLE
        startMenuClassicButtonColumn.visibility = if (visible) VISIBLE else GONE
        classicBackCallback.isEnabled = visible
    }

    private fun cycleDifficulty() {
        val presets = DifficultyPresets.all
        val current = presets.indexOfFirst { it.name == selectedDifficultyName }
        selectedDifficultyName = presets[(current + 1) % presets.size].name
        refreshSelectionLabels()
    }

    private fun cyclePlayerStrategy() {
        val entries = PlayerPolicyType.entries
        val current = entries.indexOf(selectedPlayerPolicy)
        selectedPlayerPolicy = entries[(current + 1) % entries.size]
        refreshSelectionLabels()
    }

    private fun cycleNpcStrategy() {
        val entries = NpcPolicyType.entries
        val current = entries.indexOf(selectedNpcPolicy)
        selectedNpcPolicy = entries[(current + 1) % entries.size]
        refreshSelectionLabels()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_PLAYER_POLICY, selectedPlayerPolicy.name)
        outState.putString(STATE_NPC_POLICY, selectedNpcPolicy.name)
        outState.putString(STATE_DIFFICULTY, selectedDifficultyName)
        outState.putBoolean(
            STATE_SHOWING_CLASSIC_MENU,
            startMenuClassicButtonColumn.visibility == VISIBLE
        )
    }

    companion object {
        const val EXTRA_PLAYER_POLICY = "extra_player_policy"
        const val EXTRA_NPC_POLICY = "extra_npc_policy"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
        const val EXTRA_RESUME = "extra_resume"

        private const val STATE_PLAYER_POLICY = "state_player_policy"
        private const val STATE_NPC_POLICY = "state_npc_policy"
        private const val STATE_DIFFICULTY = "state_difficulty"
        private const val STATE_SHOWING_CLASSIC_MENU = "state_showing_classic_menu"
    }
}
