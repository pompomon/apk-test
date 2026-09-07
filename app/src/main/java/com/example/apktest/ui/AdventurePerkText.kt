package com.example.apktest.ui

import com.example.apktest.R
import com.example.apktest.game.core.RunPerkCatalogue
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkStack
import com.example.apktest.game.core.RunPerkTier

/** Resource-backed copy, kept outside the renderer and rebuilt only on run transitions. */
class AdventurePerkText(private val string: (Int, Array<out Any>) -> String) {
    fun choice(id: RunPerkId, owned: List<RunPerkStack>): String {
        val definition = RunPerkCatalogue.definition(id)
        val nextStack = (owned.firstOrNull { it.id == id }?.stacks ?: 0) + 1
        return text(
            R.string.adventure_perk_accessibility,
            text(name(id)), text(tier(definition.tier)), nextStack, definition.maxStacks,
            text(description(id)) + " " + text(scope(id))
        )
    }

    fun compact(perks: List<RunPerkStack>): String = perks.joinToString(" · ") { perk ->
        if (perk.id == RunPerkId.SECOND_WIND) {
            text(R.string.adventure_perk_charge_summary, text(name(perk.id)), charge(perk))
        } else {
            text(R.string.adventure_perk_compact_stack, text(name(perk.id)), perk.stacks)
        }
    }

    fun expanded(perks: List<RunPerkStack>): String = if (perks.isEmpty()) {
        text(R.string.adventure_perk_summary_empty)
    } else {
        perks.joinToString("\n\n") { perk ->
            val definition = RunPerkCatalogue.definition(perk.id)
            val heading = text(
                R.string.adventure_perk_stack_summary,
                text(name(perk.id)), perk.stacks, definition.maxStacks
            )
            val status = if (perk.id == RunPerkId.SECOND_WIND) " · " + charge(perk) else ""
            "$heading$status\n${text(tier(definition.tier))} · ${text(scope(perk.id))}\n" +
                text(description(perk.id))
        }
    }

    private fun charge(perk: RunPerkStack): String = text(
        if (perk.consumed) R.string.adventure_perk_used else R.string.adventure_perk_ready
    )

    private fun text(id: Int, vararg args: Any): String = string(id, args)

    private fun tier(tier: RunPerkTier): Int = when (tier) {
        RunPerkTier.COMMON -> R.string.adventure_perk_tier_common
        RunPerkTier.UNCOMMON -> R.string.adventure_perk_tier_uncommon
        RunPerkTier.RARE -> R.string.adventure_perk_tier_rare
    }

    private fun scope(id: RunPerkId): Int = when (id) {
        RunPerkId.QUICK_FEET, RunPerkId.LONGER_CHARGE, RunPerkId.POCKET_MAGNET,
        RunPerkId.SCOUT_SENSE, RunPerkId.RISK_DIVIDEND -> R.string.adventure_perk_scope_this_run
        RunPerkId.FIRST_SHIELD -> R.string.adventure_perk_scope_each_maze
        RunPerkId.SECOND_WIND -> R.string.adventure_perk_scope_once_per_run
    }

    private fun name(id: RunPerkId): Int = when (id) {
        RunPerkId.QUICK_FEET -> R.string.adventure_perk_quick_feet_name
        RunPerkId.LONGER_CHARGE -> R.string.adventure_perk_longer_charge_name
        RunPerkId.POCKET_MAGNET -> R.string.adventure_perk_pocket_magnet_name
        RunPerkId.FIRST_SHIELD -> R.string.adventure_perk_first_shield_name
        RunPerkId.SCOUT_SENSE -> R.string.adventure_perk_scout_sense_name
        RunPerkId.SECOND_WIND -> R.string.adventure_perk_second_wind_name
        RunPerkId.RISK_DIVIDEND -> R.string.adventure_perk_risk_dividend_name
    }

    private fun description(id: RunPerkId): Int = when (id) {
        RunPerkId.QUICK_FEET -> R.string.adventure_perk_quick_feet_description
        RunPerkId.LONGER_CHARGE -> R.string.adventure_perk_longer_charge_description
        RunPerkId.POCKET_MAGNET -> R.string.adventure_perk_pocket_magnet_description
        RunPerkId.FIRST_SHIELD -> R.string.adventure_perk_first_shield_description
        RunPerkId.SCOUT_SENSE -> R.string.adventure_perk_scout_sense_description
        RunPerkId.SECOND_WIND -> R.string.adventure_perk_second_wind_description
        RunPerkId.RISK_DIVIDEND -> R.string.adventure_perk_risk_dividend_description
    }
}
