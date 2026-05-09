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
    val powerUpsOnMap: Int
)
