package com.example.apktest

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        val root = findViewById<android.view.View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        statusText = findViewById(R.id.textStatus)
        speedText = findViewById(R.id.textSpeed)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentGameHost, GameFragment())
                .commit()
        }

        setupControls()
    }

    override fun exit() {
        finish()
    }

    override fun onResume() {
        super.onResume()
        refreshHudSnapshot()
    }

    private fun setupControls() {
        val playerSpinner = findViewById<Spinner>(R.id.spinnerPlayerPolicy)
        val npcSpinner = findViewById<Spinner>(R.id.spinnerNpcPolicy)
        val difficultySpinner = findViewById<Spinner>(R.id.spinnerDifficulty)

        playerSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            PlayerPolicyType.entries.map { it.label }
        )

        npcSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            NpcPolicyType.entries.map { it.label }
        )

        val difficulties = DifficultyPresets.all.map { it.name }
        difficultySpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            difficulties
        )
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
}
