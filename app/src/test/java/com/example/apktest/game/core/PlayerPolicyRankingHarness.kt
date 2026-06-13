package com.example.apktest.game.core

import kotlin.math.ceil
import kotlin.math.max

data class PlayerPolicyBenchmarkConfig(
    val difficulties: List<DifficultyPreset> = DifficultyPresets.all,
    val npcPolicies: List<NpcPolicyType> = NpcPolicyType.entries.toList(),
    val seeds: List<Long> = listOf(
        7L,
        19L,
        31L,
        43L,
        59L,
        73L,
        97L,
        127L
    ),
    val maximumSimulatedSeconds: Float = 300f,
    val startingPowerUp: PowerUpType? = null
)

data class PlayerPolicyRunResult(
    val policyType: PlayerPolicyType,
    val difficultyName: String,
    val npcPolicyType: NpcPolicyType,
    val seed: Long,
    val status: GameStatus,
    val elapsedSeconds: Float,
    val steps: Int,
    val timedOut: Boolean
)

data class PlayerPolicyAggregateResult(
    val policyType: PlayerPolicyType,
    val totalRuns: Int,
    val successfulRunCount: Int,
    val lossCount: Int,
    val timeoutCount: Int,
    val successRate: Float,
    val medianSuccessfulElapsedSeconds: Float?,
    val meanSuccessfulElapsedSeconds: Float?,
    val p90SuccessfulElapsedSeconds: Float?,
    val medianSuccessfulStepCount: Float?
)

/**
 * JVM-only development harness for producing the checked-in Adventure award
 * policy ranking. It is intentionally test-source code, not runtime app code.
 */
object PlayerPolicyRankingHarness {
    fun benchmark(config: PlayerPolicyBenchmarkConfig = PlayerPolicyBenchmarkConfig()): List<PlayerPolicyRunResult> =
        buildList {
            for (difficulty in config.difficulties) {
                for (npcPolicy in config.npcPolicies) {
                    for (seed in config.seeds) {
                        for (playerPolicy in automatedPlayerPolicies()) {
                            add(runScenario(config, difficulty, npcPolicy, seed, playerPolicy))
                        }
                    }
                }
            }
        }

    fun rank(config: PlayerPolicyBenchmarkConfig = PlayerPolicyBenchmarkConfig()): List<PlayerPolicyAggregateResult> =
        rank(benchmark(config))

    fun rank(results: List<PlayerPolicyRunResult>): List<PlayerPolicyAggregateResult> =
        automatedPlayerPolicies()
            .map { policy ->
                aggregate(policy, results.filter { it.policyType == policy })
            }
            .sortedWith(::comparePolicyAggregates)

    private fun runScenario(
        config: PlayerPolicyBenchmarkConfig,
        difficulty: DifficultyPreset,
        npcPolicy: NpcPolicyType,
        seed: Long,
        playerPolicy: PlayerPolicyType
    ): PlayerPolicyRunResult {
        val engine = GameEngine(difficulty, seed)
        engine.setNpcPolicy(npcPolicy)
        engine.setPlayerPolicy(playerPolicy)
        engine.applyStartingPowerUp(config.startingPowerUp)

        val effectivePlayerSpeed = difficulty.playerMovesPerSecond *
            if (config.startingPowerUp == PowerUpType.SPEED_UP) 2f else 1f
        val dt = 1f / (max(effectivePlayerSpeed, difficulty.npcMovesPerSecond) * 4f)

        while (engine.status == GameStatus.RUNNING &&
            engine.elapsedSeconds < config.maximumSimulatedSeconds
        ) {
            engine.update(dt)
        }

        val timedOut = engine.status == GameStatus.RUNNING
        return PlayerPolicyRunResult(
            policyType = playerPolicy,
            difficultyName = difficulty.name,
            npcPolicyType = npcPolicy,
            seed = seed,
            status = engine.status,
            elapsedSeconds = engine.elapsedSeconds,
            steps = engine.steps,
            timedOut = timedOut
        )
    }

    private fun aggregate(
        policy: PlayerPolicyType,
        runs: List<PlayerPolicyRunResult>
    ): PlayerPolicyAggregateResult {
        val successes = runs.filter { it.status == GameStatus.WIN && !it.timedOut }
        val successfulTimes = successes.map { it.elapsedSeconds }
        val successfulSteps = successes.map { it.steps.toFloat() }
        return PlayerPolicyAggregateResult(
            policyType = policy,
            totalRuns = runs.size,
            successfulRunCount = successes.size,
            lossCount = runs.count { it.status == GameStatus.LOSE },
            timeoutCount = runs.count { it.timedOut },
            successRate = if (runs.isEmpty()) 0f else successes.size.toFloat() / runs.size.toFloat(),
            medianSuccessfulElapsedSeconds = median(successfulTimes),
            meanSuccessfulElapsedSeconds = mean(successfulTimes),
            p90SuccessfulElapsedSeconds = percentile(successfulTimes, 0.90f),
            medianSuccessfulStepCount = median(successfulSteps)
        )
    }

    private fun comparePolicyAggregates(
        left: PlayerPolicyAggregateResult,
        right: PlayerPolicyAggregateResult
    ): Int {
        compareValues(right.successRate, left.successRate).takeIf { it != 0 }?.let { return it }
        if (left.successfulRunCount > 0 || right.successfulRunCount > 0) {
            compareNullableFloats(
                left.medianSuccessfulElapsedSeconds,
                right.medianSuccessfulElapsedSeconds
            ).takeIf { it != 0 }?.let { return it }
            compareNullableFloats(
                left.p90SuccessfulElapsedSeconds,
                right.p90SuccessfulElapsedSeconds
            ).takeIf { it != 0 }?.let { return it }
            compareNullableFloats(
                left.medianSuccessfulStepCount,
                right.medianSuccessfulStepCount
            ).takeIf { it != 0 }?.let { return it }
        } else {
            compareValues(left.lossCount, right.lossCount).takeIf { it != 0 }?.let { return it }
            compareValues(left.timeoutCount, right.timeoutCount).takeIf { it != 0 }?.let { return it }
        }
        return compareValues(left.policyType.ordinal, right.policyType.ordinal)
    }

    private fun compareNullableFloats(left: Float?, right: Float?): Int =
        compareValues(left ?: Float.POSITIVE_INFINITY, right ?: Float.POSITIVE_INFINITY)

    private fun median(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            (sorted[mid - 1] + sorted[mid]) / 2f
        }
    }

    private fun mean(values: List<Float>): Float? =
        if (values.isEmpty()) null else values.sum() / values.size.toFloat()

    private fun percentile(values: List<Float>, percentile: Float): Float? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val index = (ceil(percentile * sorted.size).toInt() - 1)
            .coerceIn(0, sorted.lastIndex)
        return sorted[index]
    }
}
