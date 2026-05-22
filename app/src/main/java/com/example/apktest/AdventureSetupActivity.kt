package com.example.apktest

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.ui.LegendDialog

/**
 * Setup screen for Adventure mode. Lets the player pick a difficulty,
 * see the resulting run parameters (lives / mazes / NPC offset), and
 * either start a fresh run or resume a saved one. Reached from
 * [SetupActivity] via the "Adventure mode" button.
 */
class AdventureSetupActivity : AppCompatActivity() {
    private lateinit var adventureStore: AdventureStateStore
    private var selectedDifficultyName: String = DifficultyPresets.EASY.name

    private lateinit var buttonResume: Button
    private lateinit var buttonStart: Button
    private lateinit var buttonPickDifficulty: Button
    private lateinit var summaryText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_adventure_setup)

        adventureStore = AdventureStateStore(this)

        if (savedInstanceState != null) {
            savedInstanceState.getString(STATE_DIFFICULTY)?.let { name ->
                if (DifficultyPresets.all.any { it.name == name }) {
                    selectedDifficultyName = name
                }
            }
        }

        val root = findViewById<android.view.View>(R.id.adventureSetupRoot)
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

        buttonResume = findViewById(R.id.buttonAdventureResume)
        buttonStart = findViewById(R.id.buttonAdventureStart)
        buttonPickDifficulty = findViewById(R.id.buttonAdventurePickDifficulty)
        summaryText = findViewById(R.id.adventureSummary)
        val buttonLegend = findViewById<Button>(R.id.buttonAdventureLegend)
        val buttonBack = findViewById<Button>(R.id.buttonAdventureBack)

        refreshSelectionLabels()
        refreshResumeButtonState()

        buttonResume.setOnClickListener {
            val intent = Intent(this, AdventureActivity::class.java).apply {
                putExtra(EXTRA_RESUME, true)
                putExtra(EXTRA_DIFFICULTY, selectedDifficultyName)
            }
            startActivity(intent)
        }

        buttonStart.setOnClickListener {
            adventureStore.clear()
            refreshResumeButtonState()
            val intent = Intent(this, AdventureActivity::class.java).apply {
                putExtra(EXTRA_DIFFICULTY, selectedDifficultyName)
            }
            startActivity(intent)
        }

        buttonPickDifficulty.setOnClickListener { showDifficultyPicker() }
        buttonLegend.setOnClickListener { LegendDialog.show(this) }
        buttonBack.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        refreshResumeButtonState()
    }

    private fun refreshResumeButtonState() {
        buttonResume.isEnabled = adventureStore.hasRawState()
    }

    private fun refreshSelectionLabels() {
        buttonPickDifficulty.text = getString(R.string.menu_difficulty, selectedDifficultyName)
        val preset = DifficultyPresets.byName(selectedDifficultyName)
        val config = AdventureConfig.forDifficulty(preset)
        summaryText.text = getString(
            R.string.adventure_summary_format,
            config.initialLives,
            config.totalMazes,
            config.extraNpcsPerMaze
        )
    }

    private fun showDifficultyPicker() {
        val items = DifficultyPresets.all.map { it.name }.toTypedArray()
        val current = items.indexOf(selectedDifficultyName).coerceAtLeast(0)
        AlertDialog.Builder(this)
            .setTitle(R.string.menu_pick_difficulty)
            .setSingleChoiceItems(items, current) { dialog, which ->
                selectedDifficultyName = items[which]
                refreshSelectionLabels()
                dialog.dismiss()
            }
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE_DIFFICULTY, selectedDifficultyName)
    }

    companion object {
        const val EXTRA_DIFFICULTY = "extra_adventure_difficulty"
        const val EXTRA_RESUME = "extra_adventure_resume"
        private const val STATE_DIFFICULTY = "state_adventure_difficulty"
    }
}
