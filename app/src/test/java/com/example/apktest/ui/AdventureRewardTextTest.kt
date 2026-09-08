package com.example.apktest.ui

import com.example.apktest.R
import com.example.apktest.game.core.PendingAdventureReward
import com.example.apktest.game.core.PendingPerkOffer
import com.example.apktest.game.core.PowerUpType
import com.example.apktest.game.core.RewardStage
import com.example.apktest.game.core.RouteEventGenerator
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkTier
import org.junit.Assert.assertEquals
import org.junit.Test

class AdventureRewardTextTest {
    private val reward = PendingAdventureReward(
        mazeIndexCompleted = 2,
        stage = RewardStage.WIN_ACKNOWLEDGEMENT,
        routeChoices = emptyList(),
        powerUpCandidates = listOf(PowerUpType.SHIELD)
    )
    private val route = RouteEventGenerator.choice(RouteEventGenerator.SUPPLY_CACHE)!!
    private val perk = PendingPerkOffer(
        3, 0, listOf(RunPerkId.QUICK_FEET), emptyList(), emptyList(),
        listOf(RunPerkTier.COMMON), routesEnabled = true
    )

    @Test
    fun routeWithoutPerkAnnouncesRouteBeforeStartingReward() {
        assertEquals(R.string.adventure_route_win_prompt,
            AdventureRewardText.winPrompt(reward.copy(routeChoices = listOf(route))))
    }

    @Test
    fun otherRewardCombinationsRetainTheirPrompts() {
        assertEquals(R.string.adventure_powerup_prompt, AdventureRewardText.winPrompt(reward))
        assertEquals(R.string.adventure_perk_win_prompt,
            AdventureRewardText.winPrompt(reward.copy(perkOffer = perk)))
        assertEquals(R.string.adventure_perk_route_win_prompt,
            AdventureRewardText.winPrompt(reward.copy(routeChoices = listOf(route), perkOffer = perk)))
    }
}
