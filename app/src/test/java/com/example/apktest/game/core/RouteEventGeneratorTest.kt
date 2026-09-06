package com.example.apktest.game.core

import org.junit.Assert.*
import org.junit.Test

class RouteEventGeneratorTest {
    @Test
    fun offersAreDeterministicBoundedDistinctAndNeverRiskyOnly() {
        DifficultyPresets.all.forEach { difficulty ->
            val config = AdventureConfig.forDifficulty(difficulty)
            repeat(64) { seed ->
                val generator = RouteEventGenerator(config, seed.toLong())
                var index = 2
                var ordinal = 0
                while (index < config.totalMazes) {
                    val choices = generator.offer(index, ordinal)
                    assertEquals(choices, RouteEventGenerator(config, seed.toLong()).offer(index, ordinal))
                    assertTrue(choices.size in 2..3)
                    assertEquals(choices.size, choices.map { it.id }.distinct().size)
                    assertTrue(choices.any { it.category != RouteEventCategory.RISKY })
                    assertTrue(choices.all { RouteEventGenerator.isKnownChoice(it) })
                    val next = generator.nextEventMazeIndex(index, ordinal)
                    assertTrue(next - index in 2..3)
                    index = next
                    ordinal++
                }
                assertTrue(generator.offer(config.totalMazes, ordinal).isEmpty())
                assertTrue(generator.offer(1, 0).isEmpty())
            }
        }
    }

    @Test
    fun filteringOmitsImpossiblePayoffsAndEasyLifetimePenalty() {
        DifficultyPresets.all.forEach { difficulty ->
            val config = AdventureConfig.forDifficulty(difficulty)
            repeat(32) { seed ->
                val generator = RouteEventGenerator(config, seed.toLong())
                val finalTarget = generator.offer(config.totalMazes - 1, 1)
                assertFalse(finalTarget.any { it.id == RouteEventGenerator.AMBUSH_SHORTCUT })
                assertFalse(finalTarget.any { it.id == RouteEventGenerator.SCOUT_MAP })
                if (difficulty == DifficultyPresets.EASY) {
                    assertFalse(generator.offer(2, 0).any { it.id == RouteEventGenerator.CURSED_GATE })
                    assertFalse(finalTarget.any { it.id == RouteEventGenerator.CURSED_GATE })
                }
                if (difficulty != DifficultyPresets.HARD) {
                    assertFalse(generator.offer(2, 0).any { it.id == RouteEventGenerator.QUIET_CORRIDOR })
                }
            }
        }
    }

    @Test
    fun quietRespectsFinalPressureAndCustomZeroFloor() {
        val quiet = RouteEventGenerator.choice(RouteEventGenerator.QUIET_CORRIDOR)!!
        for (baseCount in 0..2) {
            for (total in 3..12) {
                val config = AdventureConfig(DifficultyPresets.MEDIUM, 3, total, baseCount)
                val resolved = RouteEventGenerator(config, 1L).resolve(quiet, total)
                val actual = config.npcCountForMaze(total) + resolved.npcCountDelta
                assertTrue(actual >= config.npcCountForMaze(total - 1))
                assertTrue(actual >= if (baseCount == 0) 0 else 1)
            }
        }
        val custom = AdventureConfig(DifficultyPresets.MEDIUM, 3, 7, 0)
        val resolved = RouteEventGenerator(custom, 1L).resolve(quiet, 5)
        assertEquals(0, custom.npcCountForMaze(5) + resolved.npcCountDelta)
    }

    @Test
    fun cursedClampsFiniteLifetimeAndDoesNotOfferANoopPenalty() {
        val cursed = RouteEventGenerator.choice(RouteEventGenerator.CURSED_GATE)!!
        val config = AdventureConfig(DifficultyPresets.MEDIUM.copy(powerUpPickupLifetimeSeconds = 15f), 3, 7, 1)
        assertEquals(10f, RouteEventGenerator(config, 0).resolve(cursed, 3).pickupLifetimeSeconds!!, 0f)
        for (lifetime in listOf(0f, -1f, 10f, Float.POSITIVE_INFINITY)) {
            val infinite = config.copy(difficulty = config.difficulty.copy(powerUpPickupLifetimeSeconds = lifetime))
            repeat(16) { seed ->
                assertFalse(RouteEventGenerator(infinite, seed.toLong()).offer(2, 0)
                    .any { it.id == RouteEventGenerator.CURSED_GATE })
            }
        }
    }
}
