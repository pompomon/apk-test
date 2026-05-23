package com.example.apktest.game.core

/**
 * Per-difficulty configuration for an Adventure run.
 *
 * A run consists of [totalMazes] consecutively-played mazes on the same
 * [difficulty]. The player starts with [initialLives] "chances" and earns
 * +1 every [STREAK_BONUS_THRESHOLD] consecutive wins without dying. The
 * number of NPCs on the n-th maze (1-based) is `n + [extraNpcsPerMaze]`.
 *
 * Values are sourced from a small lookup table keyed by
 * [DifficultyPreset.name] (`Easy`/`Medium`/`Hard`). Custom presets fall
 * back to MEDIUM defaults so test-only / future presets remain playable.
 */
data class AdventureConfig(
    val difficulty: DifficultyPreset,
    val initialLives: Int,
    val totalMazes: Int,
    val extraNpcsPerMaze: Int
) {
    init {
        require(initialLives >= 1) { "initialLives must be >= 1 (was $initialLives)" }
        require(totalMazes >= 1) { "totalMazes must be >= 1 (was $totalMazes)" }
        require(extraNpcsPerMaze >= 0) { "extraNpcsPerMaze must be >= 0 (was $extraNpcsPerMaze)" }
    }

    /**
     * NPC count for the [mazeIndex1Based]-th maze (1-based). Defined as
     * `mazeIndex1Based + extraNpcsPerMaze` so the first maze on Easy has
     * 2 NPCs (1 + 1), the first on Medium has 3 (1 + 2), etc.
     */
    fun npcCountForMaze(mazeIndex1Based: Int): Int {
        require(mazeIndex1Based >= 1) { "mazeIndex1Based must be >= 1 (was $mazeIndex1Based)" }
        return mazeIndex1Based + extraNpcsPerMaze
    }

    companion object {
        /** Awarded one bonus life every this many consecutive wins. */
        const val STREAK_BONUS_THRESHOLD = 3

        private val builtIn: Map<String, (DifficultyPreset) -> AdventureConfig> = mapOf(
            DifficultyPresets.EASY.name to { p ->
                AdventureConfig(p, initialLives = 5, totalMazes = 5, extraNpcsPerMaze = 1)
            },
            DifficultyPresets.MEDIUM.name to { p ->
                AdventureConfig(p, initialLives = 3, totalMazes = 7, extraNpcsPerMaze = 2)
            },
            DifficultyPresets.HARD.name to { p ->
                AdventureConfig(p, initialLives = 1, totalMazes = 9, extraNpcsPerMaze = 3)
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
