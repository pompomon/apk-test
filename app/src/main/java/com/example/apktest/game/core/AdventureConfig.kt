package com.example.apktest.game.core

/**
 * Per-difficulty configuration for an Adventure run.
 *
 * A run consists of [totalMazes] consecutively-played mazes on the same
 * [difficulty]. The player starts with [initialLives] "chances" and earns
 * +1 every [STREAK_BONUS_THRESHOLD] consecutive wins without dying.
 *
 * NPC count for the n-th maze (1-based) ramps gently:
 * `baseNpcs + ((n - 1) / 3)` adds one extra NPC every 3 mazes (bumps at
 * mazes 4, 7, 10…), and the **last** maze always gets one more on top.
 *
 * Values are sourced from a small lookup table keyed by
 * [DifficultyPreset.name] (`Easy`/`Medium`/`Hard`). Custom presets fall
 * back to MEDIUM defaults so test-only / future presets remain playable.
 */
data class AdventureConfig(
    val difficulty: DifficultyPreset,
    val initialLives: Int,
    val totalMazes: Int,
    /**
     * Base NPC count used as the floor of [npcCountForMaze]. The formula
     * ramps `+1` every 3 mazes and adds another `+1` on the final maze.
     */
    val baseNpcsPerMaze: Int
) {
    init {
        require(initialLives >= 1) { "initialLives must be >= 1 (was $initialLives)" }
        require(totalMazes >= 1) { "totalMazes must be >= 1 (was $totalMazes)" }
        require(baseNpcsPerMaze >= 0) { "baseNpcsPerMaze must be >= 0 (was $baseNpcsPerMaze)" }
    }

    /**
     * NPC count for the [mazeIndex1Based]-th maze (1-based). Defined as
     * `baseNpcsPerMaze + ((mazeIndex1Based - 1) / 3)`, plus `+1` when
     * [mazeIndex1Based] equals [totalMazes] so the final maze is always
     * harder than the previous-tier formula would otherwise predict.
     */
    fun npcCountForMaze(mazeIndex1Based: Int): Int {
        require(mazeIndex1Based >= 1) { "mazeIndex1Based must be >= 1 (was $mazeIndex1Based)" }
        val rampBonus = (mazeIndex1Based - 1) / 3
        val lastMazeBonus = if (mazeIndex1Based == totalMazes) 1 else 0
        return baseNpcsPerMaze + rampBonus + lastMazeBonus
    }

    companion object {
        /** Awarded one bonus life every this many consecutive wins. */
        const val STREAK_BONUS_THRESHOLD = 3

        private val builtIn: Map<String, (DifficultyPreset) -> AdventureConfig> = mapOf(
            DifficultyPresets.EASY.name to { p ->
                AdventureConfig(p, initialLives = 5, totalMazes = 5, baseNpcsPerMaze = 1)
            },
            DifficultyPresets.MEDIUM.name to { p ->
                AdventureConfig(p, initialLives = 3, totalMazes = 7, baseNpcsPerMaze = 1)
            },
            DifficultyPresets.HARD.name to { p ->
                AdventureConfig(p, initialLives = 1, totalMazes = 9, baseNpcsPerMaze = 2)
            }
        )

        /**
         * Build a config for [preset]. Falls back to MEDIUM's rules
         * (3 lives / 7 mazes / +2 NPCs) for unknown preset names so
         * future or test-only presets remain playable in Adventure mode.
         */
        fun forDifficulty(preset: DifficultyPreset): AdventureConfig {
            val factory = builtIn[preset.name] ?: builtIn.getValue(DifficultyPresets.MEDIUM.name)
            return factory(preset)
        }

        /** Convenience for callers that only have the difficulty name. */
        fun forDifficultyName(name: String): AdventureConfig =
            forDifficulty(DifficultyPresets.byName(name))
    }
}
