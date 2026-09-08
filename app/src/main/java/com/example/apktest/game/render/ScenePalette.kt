package com.example.apktest.game.render

import com.badlogic.gdx.graphics.Color

/** Shared, prebuilt scene accents; policy and power-up colours remain independent. */
internal object ScenePalette {
    val surround = Color(16f / 255f, 18f / 255f, 24f / 255f, 1f)
    val exitCyan = Color(0.29f, 0.86f, 0.90f, 1f)
    val overlayBackdrop = Color(0.035f, 0.045f, 0.065f, 1f)
    val overlayBorder = Color(0.28f, 0.35f, 0.43f, 1f)
    val countdownText = Color(0.94f, 0.96f, 1f, 1f)
    val winText = Color(0.88f, 0.98f, 0.92f, 1f)
    val loseText = Color(1f, 0.70f, 0.61f, 1f)

    const val EXIT_GLOW_ALPHA = 0.18f
    const val EXIT_GLOW_RADIUS = 0.62f
}
