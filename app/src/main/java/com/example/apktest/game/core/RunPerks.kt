package com.example.apktest.game.core

import kotlin.random.Random

enum class RunPerkId(val id: String) {
    QUICK_FEET("quick_feet"),
    LONGER_CHARGE("longer_charge"),
    POCKET_MAGNET("pocket_magnet"),
    FIRST_SHIELD("first_shield"),
    SCOUT_SENSE("scout_sense"),
    SECOND_WIND("second_wind"),
    RISK_DIVIDEND("risk_dividend");

    companion object {
        fun fromId(id: String): RunPerkId? = entries.firstOrNull { it.id == id }
    }
}

enum class RunPerkTier(val weight: Int) { COMMON(70), UNCOMMON(25), RARE(5) }

data class RunPerkStack(val id: RunPerkId, val stacks: Int, val consumed: Boolean = false)

data class RunPerkDefinition(
    val id: RunPerkId,
    val tier: RunPerkTier,
    val maxStacks: Int,
    val commonStatOrQualityOfLife: Boolean = false,
    val requiresRoutes: Boolean = false,
    val available: Boolean = true
)

object RunPerkCatalogue {
    private val definitions = RunPerkId.entries.associateWith { id ->
        when (id) {
            RunPerkId.QUICK_FEET -> RunPerkDefinition(id, RunPerkTier.COMMON, 3, commonStatOrQualityOfLife = true)
            RunPerkId.LONGER_CHARGE -> RunPerkDefinition(id, RunPerkTier.COMMON, 3, commonStatOrQualityOfLife = true)
            RunPerkId.POCKET_MAGNET -> RunPerkDefinition(id, RunPerkTier.COMMON, 2, commonStatOrQualityOfLife = true)
            RunPerkId.FIRST_SHIELD -> RunPerkDefinition(id, RunPerkTier.UNCOMMON, 1, available = false)
            RunPerkId.SCOUT_SENSE -> RunPerkDefinition(id, RunPerkTier.UNCOMMON, 1)
            RunPerkId.SECOND_WIND -> RunPerkDefinition(id, RunPerkTier.RARE, 1)
            RunPerkId.RISK_DIVIDEND -> RunPerkDefinition(id, RunPerkTier.RARE, 1, requiresRoutes = true)
        }
    }

    fun definition(id: RunPerkId): RunPerkDefinition = definitions.getValue(id)

    internal fun validStacks(stacks: List<RunPerkStack>): Boolean =
        stacks.map { it.id }.distinct().size == stacks.size && stacks.all {
            val definition = definition(it.id)
            definition.available && it.stacks in 1..definition.maxStacks &&
                (!it.consumed || it.id == RunPerkId.SECOND_WIND)
        }
}

data class RunPerkEffects(
    val quickFeetStacks: Int = 0,
    val longerChargeStacks: Int = 0,
    val pocketMagnetStacks: Int = 0,
    val secondWindAvailable: Boolean = false
) {
    init {
        require(quickFeetStacks in 0..RunPerkCatalogue.definition(RunPerkId.QUICK_FEET).maxStacks)
        require(longerChargeStacks in 0..RunPerkCatalogue.definition(RunPerkId.LONGER_CHARGE).maxStacks)
        require(pocketMagnetStacks in 0..RunPerkCatalogue.definition(RunPerkId.POCKET_MAGNET).maxStacks)
    }

    val playerSpeedMultiplier: Float get() = 1f + 0.05f * quickFeetStacks
    val durationBonusSeconds: Float get() = longerChargeStacks.toFloat()
    val magnetRadiusBonus: Int get() = pocketMagnetStacks

    companion object {
        fun fromStacks(stacks: List<RunPerkStack>): RunPerkEffects {
            require(RunPerkCatalogue.validStacks(stacks))
            return RunPerkEffects(
                quickFeetStacks = stacks.firstOrNull { it.id == RunPerkId.QUICK_FEET }?.stacks ?: 0,
                longerChargeStacks = stacks.firstOrNull { it.id == RunPerkId.LONGER_CHARGE }?.stacks ?: 0,
                pocketMagnetStacks = stacks.firstOrNull { it.id == RunPerkId.POCKET_MAGNET }?.stacks ?: 0,
                secondWindAvailable = stacks.any { it.id == RunPerkId.SECOND_WIND && !it.consumed }
            )
        }
    }
}

/** Generation inputs are saved so rollout changes cannot reinterpret an existing choice. */
data class PendingPerkOffer(
    val mazeIndexCompleted: Int,
    val ordinal: Int,
    val choices: List<RunPerkId>,
    val ownedAtOffer: List<RunPerkStack>,
    val previousOffer: List<RunPerkId>,
    val tiers: List<RunPerkTier>,
    val routesEnabled: Boolean
)

data class RunPerkHistoryEntry(val offer: PendingPerkOffer, val selectedPerkId: RunPerkId)

data class PerkScoutPreview(val npcCount: Int, val eliteCount: Int)

class RunPerkGenerator(private val runSeed: Long) {
    fun offer(
        mazeIndexCompleted: Int,
        ordinal: Int,
        owned: List<RunPerkStack>,
        previousOffer: List<RunPerkId>,
        tiers: Set<RunPerkTier> = setOf(RunPerkTier.COMMON),
        routesEnabled: Boolean = false
    ): PendingPerkOffer? {
        require(mazeIndexCompleted in OFFER_MAZES && ordinal >= 0)
        require(RunPerkCatalogue.validStacks(owned))
        val eligible = RunPerkId.entries.filter { id ->
            val definition = RunPerkCatalogue.definition(id)
            definition.available && definition.tier in tiers &&
                (!definition.requiresRoutes || routesEnabled) &&
                (owned.firstOrNull { it.id == id }?.stacks ?: 0) < definition.maxStacks
        }.toMutableList()
        if (eligible.isEmpty()) return null
        val rng = Random(runSeed xor OFFER_SEED_MIX xor
            mazeIndexCompleted.toLong() * MAZE_STRIDE xor ordinal.toLong() * ORDINAL_STRIDE)
        // Weighted permutation, not tier-first sampling: each remaining item has its tier's weight.
        val drawOrder = mutableListOf<RunPerkId>()
        while (eligible.isNotEmpty()) {
            var draw = rng.nextInt(eligible.sumOf { RunPerkCatalogue.definition(it).tier.weight })
            val index = eligible.indexOfFirst {
                draw -= RunPerkCatalogue.definition(it).tier.weight
                draw < 0
            }
            drawOrder += eligible.removeAt(index)
        }
        val selected = mutableListOf<RunPerkId>()
        fun fill(avoidPrevious: Boolean, enforceDiversity: Boolean) {
            for (id in drawOrder) {
                if (selected.size == OFFER_SIZE) break
                if (id in selected || (avoidPrevious && id in previousOffer)) continue
                if (enforceDiversity && RunPerkCatalogue.definition(id).commonStatOrQualityOfLife &&
                    selected.count { RunPerkCatalogue.definition(it).commonStatOrQualityOfLife } >= 2) continue
                selected += id
            }
        }
        fill(avoidPrevious = true, enforceDiversity = true)
        fill(avoidPrevious = true, enforceDiversity = false)
        fill(avoidPrevious = false, enforceDiversity = false)
        return PendingPerkOffer(mazeIndexCompleted, ordinal, selected.toList(), owned.toList(),
            previousOffer.toList(), RunPerkTier.entries.filter { it in tiers }, routesEnabled)
    }

    companion object {
        val OFFER_MAZES: Set<Int> = setOf(1, 3, 5, 7)
        const val OFFER_SIZE = 3
        private const val OFFER_SEED_MIX: Long = 0x4A719C35F16B02DL
        private const val MAZE_STRIDE: Long = 0x12B9B0A1CE4A11BL
        private const val ORDINAL_STRIDE: Long = 0x6A09E667F3BCC908L
    }
}

internal fun PendingPerkOffer.detachedCopy(): PendingPerkOffer = copy(
    choices = choices.toList(), ownedAtOffer = ownedAtOffer.toList(),
    previousOffer = previousOffer.toList(), tiers = tiers.toList()
)

internal fun RunPerkHistoryEntry.detachedCopy(): RunPerkHistoryEntry = copy(offer = offer.detachedCopy())
