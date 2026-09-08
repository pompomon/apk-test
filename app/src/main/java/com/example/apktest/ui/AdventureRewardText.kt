package com.example.apktest.ui

import com.example.apktest.R
import com.example.apktest.game.core.PendingAdventureReward

internal object AdventureRewardText {
    fun winPrompt(reward: PendingAdventureReward): Int = when {
        reward.routeChoices.isNotEmpty() && reward.perkOffer != null -> R.string.adventure_perk_route_win_prompt
        reward.routeChoices.isNotEmpty() -> R.string.adventure_route_win_prompt
        reward.perkOffer != null -> R.string.adventure_perk_win_prompt
        else -> R.string.adventure_powerup_prompt
    }
}
