package com.example.apktest.game.ui

import com.example.apktest.game.core.GameStatus

data class HudState(
    val status: GameStatus,
    val elapsedSeconds: Float,
    val steps: Int,
    val difficultyName: String,
    val playerPolicyLabel: String,
    val npcPolicyLabel: String,
    val playerSpeed: Float,
    val npcSpeed: Float,
    val activePowerUps: List<String>,
    val powerUpsOnMap: Int,
    /** Seconds remaining on the pre-game countdown; `null` when not counting. */
    val countdownRemainingSeconds: Float? = null,
    /**
     * Seconds remaining on the manual-input override; `null` when no
     * override is active (either no recent manual input, or the policy is
     * already `MANUAL`).
     */
    val manualOverrideRemainingSeconds: Float? = null
)
