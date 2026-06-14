package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Ignore
import org.junit.Test

class PlayerPolicyRankingHarnessTest {

    @Test
    fun benchmarkIncludesEveryAutomatedPolicyOncePerScenario() {
        val config = PlayerPolicyBenchmarkConfig(
            difficulties = listOf(DifficultyPresets.EASY),
            npcPolicies = listOf(NpcPolicyType.DIRECT_CHASE),
            seeds = listOf(1L),
            maximumSimulatedSeconds = 0f
        )

        val results = PlayerPolicyRankingHarness.benchmark(config)

        assertEquals(automatedPlayerPolicies().size, results.size)
        assertEquals(automatedPlayerPolicies().toSet(), results.map { it.policyType }.toSet())
    }

    @Ignore(
        "Development harness: run manually after policy changes, then copy the printed order " +
            "to adventureAwardPlayerPolicyRanking() in AutomatedPlayerPolicies.kt."
    )
    @Test
    fun printAdventureAwardPolicyRanking() {
        val ranking = PlayerPolicyRankingHarness.rank()
        println("Adventure award policy ranking:")
        ranking.forEachIndexed { index, result ->
            println(
                "${index + 1}. ${result.policyType.name}: " +
                    "success=${result.successfulRunCount}/${result.totalRuns}, " +
                    "median=${result.medianSuccessfulElapsedSeconds}, " +
                    "p90=${result.p90SuccessfulElapsedSeconds}, " +
                    "medianSteps=${result.medianSuccessfulStepCount}"
            )
        }
    }
}
