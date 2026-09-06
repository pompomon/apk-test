package com.example.apktest.game.core

import org.junit.Assert.*
import org.junit.Test

class RunPerksTest {
    @Test
    fun catalogueHasStableUniqueIdsExplicitCapsAndDeferredShield() {
        assertEquals(listOf("quick_feet", "longer_charge", "pocket_magnet", "first_shield",
            "scout_sense", "second_wind", "risk_dividend"), RunPerkId.entries.map { it.id })
        RunPerkId.entries.forEach { assertEquals(it, RunPerkId.fromId(it.id)) }
        assertNull(RunPerkId.fromId("QUICK_FEET"))
        assertEquals(listOf(70, 25, 5), RunPerkTier.entries.map { it.weight })
        assertEquals(listOf(3, 3, 2, 1, 1, 1, 1),
            RunPerkId.entries.map { RunPerkCatalogue.definition(it).maxStacks })
        assertFalse(RunPerkCatalogue.definition(RunPerkId.FIRST_SHIELD).available)
    }

    @Test
    fun effectsAreDerivedFromOwnedStacksAndConsumedOneShot() {
        val effects = RunPerkEffects.fromStacks(listOf(
            RunPerkStack(RunPerkId.QUICK_FEET, 3),
            RunPerkStack(RunPerkId.LONGER_CHARGE, 2),
            RunPerkStack(RunPerkId.POCKET_MAGNET, 2),
            RunPerkStack(RunPerkId.SECOND_WIND, 1)
        ))
        assertEquals(1.15f, effects.playerSpeedMultiplier, 0.00001f)
        assertEquals(2f, effects.durationBonusSeconds, 0f)
        assertEquals(2, effects.magnetRadiusBonus)
        assertTrue(effects.secondWindAvailable)
        assertFalse(RunPerkEffects.fromStacks(listOf(
            RunPerkStack(RunPerkId.SECOND_WIND, 1, consumed = true)
        )).secondWindAvailable)
        assertEquals(RunPerkEffects(), RunPerkEffects.fromStacks(emptyList()))
    }

    @Test
    fun invalidCapsDuplicatesAndConsumptionFailClosed() {
        for (invalid in listOf(
            listOf(RunPerkStack(RunPerkId.FIRST_SHIELD, 1)),
            listOf(RunPerkStack(RunPerkId.QUICK_FEET, 0)),
            listOf(RunPerkStack(RunPerkId.QUICK_FEET, 4)),
            listOf(RunPerkStack(RunPerkId.POCKET_MAGNET, 3)),
            listOf(RunPerkStack(RunPerkId.SECOND_WIND, 2)),
            listOf(RunPerkStack(RunPerkId.SCOUT_SENSE, 1, consumed = true)),
            listOf(RunPerkStack(RunPerkId.QUICK_FEET, 1), RunPerkStack(RunPerkId.QUICK_FEET, 1))
        )) {
            assertThrows(IllegalArgumentException::class.java) { RunPerkEffects.fromStacks(invalid) }
        }
        assertThrows(IllegalArgumentException::class.java) { RunPerkEffects(quickFeetStacks = -1) }
        assertThrows(IllegalArgumentException::class.java) { RunPerkEffects(longerChargeStacks = 4) }
        assertThrows(IllegalArgumentException::class.java) { RunPerkEffects(pocketMagnetStacks = 3) }
    }

    @Test
    fun weightedOffersAreReproducibleDistinctEligibleAndDiverse() {
        val allTiers = RunPerkTier.entries.toSet()
        repeat(128) { seed ->
            val generator = RunPerkGenerator(seed.toLong())
            val offer = generator.offer(3, 1, emptyList(), emptyList(), allTiers, true)!!
            assertEquals(offer, generator.offer(3, 1, emptyList(), emptyList(), allTiers, true))
            assertEquals(3, offer.choices.size)
            assertEquals(3, offer.choices.distinct().size)
            assertFalse(RunPerkId.FIRST_SHIELD in offer.choices)
            assertTrue(offer.choices.count {
                RunPerkCatalogue.definition(it).commonStatOrQualityOfLife
            } <= 2)
        }
    }

    @Test
    fun previousOffersAreAvoidedBeforeDiversityIsRelaxed() {
        val generator = RunPerkGenerator(71L)
        val previous = listOf(RunPerkId.SCOUT_SENSE, RunPerkId.SECOND_WIND, RunPerkId.RISK_DIVIDEND)
        val offer = generator.offer(3, 1, emptyList(), previous, RunPerkTier.entries.toSet(), true)!!
        assertEquals(setOf(RunPerkId.QUICK_FEET, RunPerkId.LONGER_CHARGE, RunPerkId.POCKET_MAGNET),
            offer.choices.toSet())
        val repeatable = generator.offer(5, 2, emptyList(), offer.choices, RunPerkTier.entries.toSet(), true)!!
        assertEquals(previous.toSet(), repeatable.choices.toSet())
    }

    @Test
    fun commonOnlyPoolRelaxesDiversityAndConsecutiveAvoidanceToFillOffer() {
        val generator = RunPerkGenerator(2L)
        val first = generator.offer(1, 0, emptyList(), emptyList())!!
        val next = generator.offer(3, 1, emptyList(), first.choices)!!
        assertEquals(3, first.choices.size)
        assertEquals(first.choices.toSet(), next.choices.toSet())
        assertTrue(next.choices.all { RunPerkCatalogue.definition(it).tier == RunPerkTier.COMMON })
    }

    @Test
    fun cappedAndConsumedPerksAndUnavailableDependenciesNeverEnterPool() {
        val generator = RunPerkGenerator(1L)
        val owned = listOf(
            RunPerkStack(RunPerkId.QUICK_FEET, 3),
            RunPerkStack(RunPerkId.LONGER_CHARGE, 3),
            RunPerkStack(RunPerkId.POCKET_MAGNET, 2),
            RunPerkStack(RunPerkId.SECOND_WIND, 1, consumed = true)
        )
        val offer = generator.offer(7, 3, owned, emptyList(), RunPerkTier.entries.toSet())!!
        assertEquals(listOf(RunPerkId.SCOUT_SENSE), offer.choices)
        assertNull(generator.offer(7, 3, owned + RunPerkStack(RunPerkId.SCOUT_SENSE, 1),
            emptyList(), RunPerkTier.entries.toSet()))
        assertNull(generator.offer(1, 0, emptyList(), emptyList(), emptySet(), true))
    }

    @Test
    fun generatorDetachesAllInputCollections() {
        val owned = mutableListOf(RunPerkStack(RunPerkId.QUICK_FEET, 1))
        val previous = mutableListOf(RunPerkId.LONGER_CHARGE)
        val tiers = RunPerkTier.entries.toMutableSet()
        val offer = RunPerkGenerator(5L).offer(3, 1, owned, previous, tiers, true)!!
        owned.clear()
        previous.clear()
        tiers.clear()
        assertEquals(listOf(RunPerkStack(RunPerkId.QUICK_FEET, 1)), offer.ownedAtOffer)
        assertEquals(listOf(RunPerkId.LONGER_CHARGE), offer.previousOffer)
        assertEquals(RunPerkTier.entries.toList(), offer.tiers)
    }
}
