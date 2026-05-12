package com.example.apktest

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.ui.LegendDialog

/**
 * Initial screen where the player picks player policy, NPC policy, and
 * difficulty before starting the game. The selections are passed as intent
 * extras to [MainActivity], which then configures the game accordingly.
 */
class SetupActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_setup)

        val root = findViewById<android.view.View>(R.id.setupRoot)
        // Capture the layout-defined padding once so the inset listener does
        // not compound padding when re-dispatched (e.g., on config changes).
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

        findViewById<Button>(R.id.buttonLegend).setOnClickListener {
            LegendDialog.show(this)
        }

        findViewById<Button>(R.id.buttonStart).setOnClickListener {
            val selectedPlayer = PlayerPolicyType.entries[playerSpinner.selectedItemPosition]
            val selectedNpc = NpcPolicyType.entries[npcSpinner.selectedItemPosition]
            val selectedDifficulty = difficultySpinner.selectedItem.toString()

            val intent = Intent(this, MainActivity::class.java).apply {
                putExtra(EXTRA_PLAYER_POLICY, selectedPlayer.name)
                putExtra(EXTRA_NPC_POLICY, selectedNpc.name)
                putExtra(EXTRA_DIFFICULTY, selectedDifficulty)
            }
            startActivity(intent)
        }
    }

    companion object {
        const val EXTRA_PLAYER_POLICY = "extra_player_policy"
        const val EXTRA_NPC_POLICY = "extra_npc_policy"
        const val EXTRA_DIFFICULTY = "extra_difficulty"
    }
}
