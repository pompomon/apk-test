package com.example.apktest.ui

import com.example.apktest.R
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.AdventureRunState

/** Displays settled maze totals, never adding the current attempt a second time. */
class AdventureHudText(private val string: (Int, Array<out Any>) -> String) {
    data class Text(
        val primary: String,
        val completedStats: String,
        val streak: String,
        val streakDescription: String
    )

    fun format(state: AdventureRunState, totalMazes: Int): Text {
        val index = (state.currentMazeIndex + 1).coerceAtMost(totalMazes)
        return Text(
            string(R.string.adventure_hud_primary, arrayOf(index, totalMazes, state.livesRemaining)),
            string(
                R.string.adventure_hud_completed,
                arrayOf(AdventureTimeFormatter.format(state.totalElapsedSeconds), state.totalSteps)
            ),
            string(
                R.string.adventure_hud_streak,
                arrayOf(state.winStreakSinceLastBonus, AdventureConfig.STREAK_BONUS_THRESHOLD)
            ),
            string(
                R.string.adventure_hud_streak_description,
                arrayOf(state.winStreakSinceLastBonus, AdventureConfig.STREAK_BONUS_THRESHOLD)
            )
        )
    }
}
