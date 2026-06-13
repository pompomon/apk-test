package com.example.apktest.game.core

import kotlin.random.Random

/**
 * Terminal state of an Adventure run.
 *
 * The run is created in [IN_PROGRESS]; transitions to [WON] when the
 * player completes the last maze, or [LOST] when the player dies on a
 * maze with no lives remaining.
 */
enum class AdventureStatus {
    IN_PROGRESS,
    WON,
    LOST
}

/**
 * Mutable state of an in-flight Adventure run. Lives outside the
 * single-maze [GameEngine] because it spans multiple maze instances;
 * intentionally a plain data container (no behaviour) so it can be
 * round-tripped through JSON via [AdventureRunStateSnapshot].
 *
 * State transitions go through [AdventureRunController] — callers should
 * not mutate these fields directly.
 *
 * @property currentMazeIndex 0-based index of the current maze. Equal to
 *   [AdventureConfig.totalMazes] after the final win; the run's [status]
 *   becomes [AdventureStatus.WON] at that point.
 * @property winStreakSinceLastBonus Count of consecutive wins toward the
 *   next +1 life bonus. Resets to 0 on death OR when a bonus is awarded.
 * @property currentMazeSeed Seed used to generate the *currently-active*
 *   maze. Locked on first [AdventureRunController.prepareCurrentMaze]
 *   call so a death replay uses the same maze layout. Cleared on win
 *   so the next maze regenerates fresh.
 * @property currentMazeNpcPolicies Per-NPC `NpcPolicyType` list for the
 *   currently-active maze (indexed by spawn `Npc.id`). Locked alongside
 *   [currentMazeSeed] so a death replay keeps the same NPC strategies.
 * @property currentMazeSnapshot In-flight engine snapshot from a
 *   paused-mid-maze save. Restored verbatim into the engine on the next
 *   resume. Cleared on death (the replay starts from the maze's locked
 *   initial layout, not from where the player died) and on win.
 */
data class AdventureRunState(
    val difficultyName: String,
    var currentMazeIndex: Int = 0,
    var livesRemaining: Int = 1,
    var winStreakSinceLastBonus: Int = 0,
    var unlockedPlayerPolicies: MutableList<PlayerPolicyType> = mutableListOf(PlayerPolicyType.MANUAL),
    var currentPlayerPolicy: PlayerPolicyType = PlayerPolicyType.MANUAL,
    var lastAutomatedPlayerPolicy: PlayerPolicyType? = null,
    var automatedPolicyPromptShown: Boolean = false,
    var currentMazeSeed: Long? = null,
    var currentMazeNpcPolicies: List<NpcPolicyType> = emptyList(),
    var currentMazeSnapshot: GameEngineSnapshot? = null,
    var status: AdventureStatus = AdventureStatus.IN_PROGRESS,
    /**
     * Power-up the player chose to start the next maze with (granted as an
     * even-numbered-maze-win reward). Treated as locked per-maze state:
     * carried into every [AdventureRunController.prepareCurrentMaze] call
     * (so death replays of the same maze re-apply the same reward) and
     * cleared only when the host advances past the maze via
     * [AdventureRunController.onMazeWon]. Survives process death/restore
     * between persisting state and the GL thread actually applying the
     * power-up to a fresh engine instance.
     */
    var pendingStartingPowerUp: PowerUpType? = null
)

/**
 * Returned from [AdventureRunController.prepareCurrentMaze] to describe
 * how the host should configure the next maze: the seed to use, the
 * desired NPC count, the per-NPC policy list, the player policy to apply,
 * the difficulty preset, and (if non-null) a mid-maze snapshot to restore
 * instead of starting fresh.
 */
data class MazeStartupSpec(
    val seed: Long,
    val difficulty: DifficultyPreset,
    val npcCount: Int,
    val npcPolicies: List<NpcPolicyType>,
    val playerPolicy: PlayerPolicyType,
    val midMazeSnapshot: GameEngineSnapshot?,
    /**
     * Power-up to activate at the very start of this maze (granted as the
     * reward for the previous even-maze win). Applied once by the host
     * after engine restart; `null` for mazes with no starting bonus.
     */
    val startingPowerUp: PowerUpType? = null
)

/**
 * Returned from [AdventureRunController.onMazeWon] to communicate what
 * the host should surface on the win overlay:
 * - the new lives count and whether a bonus life was just awarded,
 * - either a list of [unlockCandidates] (up to 3 locked
 *   [PlayerPolicyType]s from the checked-in Adventure ranking on
 *   odd-numbered maze wins), or
 * - a list of [startingPowerUpCandidates] (3 random non-GHOST
 *   [PowerUpType]s on even-numbered maze wins),
 * - the maze index just completed and total mazes for messaging.
 *
 * Exactly one of [unlockCandidates] / [startingPowerUpCandidates] will
 * be populated on a non-terminal win (and both are empty on the final
 * run-complete win or when there are no locked policies left to offer).
 */
data class WinOutcome(
    val livesRemaining: Int,
    val bonusLifeAwarded: Boolean,
    val unlockCandidates: List<PlayerPolicyType>,
    val startingPowerUpCandidates: List<PowerUpType>,
    val mazeIndexCompleted: Int,
    val totalMazes: Int,
    val runComplete: Boolean
) {
    val unlockAvailable: Boolean get() = unlockCandidates.isNotEmpty() && !runComplete
    val startingPowerUpAvailable: Boolean
        get() = startingPowerUpCandidates.isNotEmpty() && !runComplete
}

/**
 * Returned from [AdventureRunController.onPlayerDied].
 */
data class DeathOutcome(
    val livesRemaining: Int,
    val runOver: Boolean
)

/**
 * Pure-Kotlin controller for an Adventure run. Owns an [AdventureRunState]
 * and exposes deterministic transitions for maze entry, win, death, and
 * policy unlocks. No Android imports — fully JVM-testable.
 *
 * The controller is **not** thread-safe; callers should invoke it from a
 * single thread (the Android host's main/UI thread).
 */
class AdventureRunController(
    val config: AdventureConfig,
    initialState: AdventureRunState? = null,
    private val runSeed: Long = System.currentTimeMillis()
) {
    val state: AdventureRunState = initialState ?: AdventureRunState(
        difficultyName = config.difficulty.name,
        livesRemaining = config.initialLives
    )

    init {
        require(state.difficultyName == config.difficulty.name) {
            "State difficulty (${state.difficultyName}) does not match config (${config.difficulty.name})"
        }
    }

    /**
     * Prepare the maze the player should now play. Locks [AdventureRunState.currentMazeSeed]
     * and [AdventureRunState.currentMazeNpcPolicies] on the first call for a
     * given maze (so a death replay returns the *same* spec); subsequent
     * calls before the next [onMazeWon] / [onPlayerDied] are idempotent.
     *
     * If the run is already terminal ([AdventureStatus.WON] / [AdventureStatus.LOST])
     * returns `null` — the host should show the terminal screen instead.
     */
    fun prepareCurrentMaze(): MazeStartupSpec? {
        if (state.status != AdventureStatus.IN_PROGRESS) return null
        if (state.currentMazeIndex >= config.totalMazes) {
            state.status = AdventureStatus.WON
            return null
        }
        val mazeIndex1Based = state.currentMazeIndex + 1
        val npcCount = config.npcCountForMaze(mazeIndex1Based)

        // Lock seed + per-NPC policy list on first entry so a death replay
        // returns the same maze layout and same NPC strategies. We derive
        // both from the run seed mixed with the maze index so reproducing
        // the run from scratch yields the same per-maze choices.
        if (state.currentMazeSeed == null) {
            state.currentMazeSeed = deriveMazeSeed(mazeIndex1Based)
        }
        if (state.currentMazeNpcPolicies.isEmpty()) {
            // First entry for this maze: draw a deterministic per-NPC policy
            // list from the run seed. Once locked, the list is preserved
            // verbatim on rehydration so a death replay reuses identical
            // NPC strategies even if [npcCount] disagrees with the stored
            // list length (e.g. a JSON tampering or schema-evolution edge).
            val rng = Random(deriveNpcPolicySeed(mazeIndex1Based))
            val pool = NpcPolicyType.entries
            state.currentMazeNpcPolicies = List(npcCount) { pool[rng.nextInt(pool.size)] }
        }
        return MazeStartupSpec(
            seed = state.currentMazeSeed!!,
            difficulty = config.difficulty,
            npcCount = npcCount,
            npcPolicies = state.currentMazeNpcPolicies,
            playerPolicy = state.currentPlayerPolicy,
            midMazeSnapshot = state.currentMazeSnapshot,
            startingPowerUp = state.pendingStartingPowerUp
        )
    }

    /**
     * Record a maze win. Increments the maze index, advances the win
     * streak (awarding +1 life every [AdventureConfig.STREAK_BONUS_THRESHOLD]),
     * clears the locked per-maze fields so the next [prepareCurrentMaze]
     * draws a fresh seed and per-NPC policies, clears any mid-maze
     * snapshot, and returns a [WinOutcome] describing the new state.
     *
     * If this was the final maze sets [AdventureStatus.WON].
     */
    fun onMazeWon(): WinOutcome {
        check(state.status == AdventureStatus.IN_PROGRESS) {
            "onMazeWon called in terminal state ${state.status}"
        }
        val newIndex = state.currentMazeIndex + 1
        state.currentMazeIndex = newIndex
        state.winStreakSinceLastBonus += 1
        val bonus = state.winStreakSinceLastBonus >= AdventureConfig.STREAK_BONUS_THRESHOLD
        if (bonus) {
            state.livesRemaining += 1
            state.winStreakSinceLastBonus = 0
        }
        state.currentMazeSeed = null
        state.currentMazeNpcPolicies = emptyList()
        state.currentMazeSnapshot = null
        // Locked starting power-up is per-maze: clear once we advance past
        // the maze it was reserved for. Death replays keep it so the same
        // reward is re-applied on the retry.
        state.pendingStartingPowerUp = null

        val runComplete = newIndex >= config.totalMazes
        if (runComplete) state.status = AdventureStatus.WON

        // Per-parity reward: odd-numbered maze wins (1, 3, 5…) unlock a
        // new player policy; even-numbered maze wins (2, 4, 6…) grant a
        // starting power-up for the next maze.
        val isOdd = newIndex % 2 == 1
        val unlockCandidates = if (runComplete || !isOdd) emptyList()
        else sampleLockedPlayerPolicies(REWARD_SAMPLE_SIZE)
        val powerUpCandidates = if (runComplete || isOdd) emptyList()
        else sampleStartingPowerUps(REWARD_SAMPLE_SIZE, newIndex)

        return WinOutcome(
            livesRemaining = state.livesRemaining,
            bonusLifeAwarded = bonus,
            unlockCandidates = unlockCandidates,
            startingPowerUpCandidates = powerUpCandidates,
            mazeIndexCompleted = newIndex,
            totalMazes = config.totalMazes,
            runComplete = runComplete
        )
    }

    /**
     * Returns up to [count] still-locked [PlayerPolicyType]s from the
     * checked-in Adventure award ranking. Policy rewards intentionally do not
     * use [runSeed]: every run offers the fastest successful locked policies
     * first, according to the offline JVM ranking harness.
     */
    internal fun sampleLockedPlayerPolicies(count: Int): List<PlayerPolicyType> =
        if (count <= 0) emptyList()
        else adventureAwardPlayerPolicyRanking()
            .filter { it !in state.unlockedPlayerPolicies }
            .take(count)

    /**
     * Deterministically samples up to [count] [PowerUpType]s (excluding
     * [PowerUpType.GHOST_MODE]) using an RNG derived from [runSeed] and
     * [mazeIndex1Based]. SHIELD, SLOW_TIME, and MAGNET are valid starting
     * rewards.
     */
    internal fun sampleStartingPowerUps(count: Int, mazeIndex1Based: Int): List<PowerUpType> {
        val pool = PowerUpType.entries.filter { it != PowerUpType.GHOST_MODE }
        if (pool.isEmpty() || count <= 0) return emptyList()
        val rng = Random(deriveRewardSeed(mazeIndex1Based, REWARD_KIND_POWERUP))
        if (pool.size <= count) return pool.shuffled(rng)
        return pool.shuffled(rng).take(count)
    }

    /** PlayerPolicyTypes not yet unlocked, in declaration order. */
    fun lockedPlayerPolicies(): List<PlayerPolicyType> =
        PlayerPolicyType.entries.filter { it !in state.unlockedPlayerPolicies }

    /**
     * Add [choice] to the unlocked policy set. No-op if [choice] is
     * already unlocked. Returns `true` if the unlock was applied,
     * `false` otherwise.
     */
    fun applyPolicyUnlock(choice: PlayerPolicyType): Boolean {
        if (choice in state.unlockedPlayerPolicies) return false
        state.unlockedPlayerPolicies.add(choice)
        return true
    }

    /**
     * Switch the currently-selected player policy. Allowed only if
     * [type] has been unlocked. Returns `true` on success. When [type] is
     * an automated (non-MANUAL) policy, it is also recorded as the
     * run's [AdventureRunState.lastAutomatedPlayerPolicy] so a later toggle
     * back to Auto can restore it.
     */
    fun setCurrentPlayerPolicy(type: PlayerPolicyType): Boolean {
        if (type !in state.unlockedPlayerPolicies) return false
        state.currentPlayerPolicy = type
        if (type != PlayerPolicyType.MANUAL) {
            state.lastAutomatedPlayerPolicy = type
        }
        return true
    }

    /**
     * Record the most-recently used automated policy, or `null` to clear it
     * when the previously-remembered policy is no longer available. Kept on
     * the controller so the host UI never mutates [AdventureRunState]
     * directly. Any value that violates the persistence invariant enforced by
     * [AdventureRunStateSnapshot.fromJson] (must be non-MANUAL and unlocked)
     * is treated as a clear, so the stored value never gets silently dropped
     * on reload.
     */
    fun setLastAutomatedPlayerPolicy(type: PlayerPolicyType?) {
        state.lastAutomatedPlayerPolicy = type?.takeIf {
            it != PlayerPolicyType.MANUAL && it in state.unlockedPlayerPolicies
        }
    }

    /**
     * Update whether the one-time automated-policy picker has already been
     * shown for this run.
     */
    fun setAutomatedPolicyPromptShown(shown: Boolean) {
        state.automatedPolicyPromptShown = shown
    }

    /**
     * Record a player death on the current maze. Decrements lives, resets
     * the win-streak counter, and clears the mid-maze snapshot so the
     * replay starts from the locked initial layout. Locked
     * [AdventureRunState.currentMazeSeed] and [AdventureRunState.currentMazeNpcPolicies]
     * are deliberately preserved so the same maze is replayed.
     *
     * Transitions to [AdventureStatus.LOST] when lives reach zero.
     */
    fun onPlayerDied(): DeathOutcome {
        check(state.status == AdventureStatus.IN_PROGRESS) {
            "onPlayerDied called in terminal state ${state.status}"
        }
        state.livesRemaining = (state.livesRemaining - 1).coerceAtLeast(0)
        state.winStreakSinceLastBonus = 0
        state.currentMazeSnapshot = null
        val runOver = state.livesRemaining <= 0
        if (runOver) state.status = AdventureStatus.LOST
        return DeathOutcome(livesRemaining = state.livesRemaining, runOver = runOver)
    }

    /**
     * Persist [engineSnapshot] as the mid-maze snapshot so a subsequent
     * resume can pick up exactly where the player paused. The host
     * typically calls this from `onPause` after capturing the snapshot
     * from the GL thread.
     */
    fun recordMidMazeSnapshot(engineSnapshot: GameEngineSnapshot) {
        state.currentMazeSnapshot = engineSnapshot
    }

    /** Discard any persisted mid-maze snapshot (e.g., on Restart). */
    fun clearMidMazeSnapshot() {
        state.currentMazeSnapshot = null
    }

    /**
     * Record [type] as the power-up to activate at the start of the next
     * maze. Persists as locked per-maze state across
     * [prepareCurrentMaze] re-entries and across process death/restore,
     * and is consumed (cleared) by [onMazeWon] when the run advances past
     * the maze it was reserved for. Passing `null` clears any previously
     * pending starting power-up. Returns the previously pending power-up
     * (or `null` if none) so the caller can react to an overwrite if
     * needed.
     */
    fun applyStartingPowerUp(type: PowerUpType?): PowerUpType? {
        val previous = state.pendingStartingPowerUp
        state.pendingStartingPowerUp = type
        return previous
    }

    private fun deriveMazeSeed(mazeIndex1Based: Int): Long =
        runSeed xor (mazeIndex1Based.toLong() * MAZE_SEED_STRIDE) xor MAZE_SEED_MIX

    private fun deriveNpcPolicySeed(mazeIndex1Based: Int): Long =
        runSeed xor (mazeIndex1Based.toLong() * NPC_POLICY_SEED_STRIDE) xor NPC_POLICY_SEED_MIX

    private fun deriveRewardSeed(mazeIndex1Based: Int, kind: Long): Long =
        runSeed xor (mazeIndex1Based.toLong() * REWARD_SEED_STRIDE) xor REWARD_SEED_MIX xor kind

    companion object {
        // Arbitrary mix constants — chosen as signed-Long literals so they
        // compile-time-evaluate without UInt.toLong() (same constraint
        // documented in [GameEngine.NPC_RANDOM_SEED_MIX]).
        private const val MAZE_SEED_STRIDE: Long = 0x12B9B0A1CE4A11BL
        private const val MAZE_SEED_MIX: Long = -0x4F2C5D6E7A8B9C0DL
        private const val NPC_POLICY_SEED_STRIDE: Long = 0x6A09E667F3BCC908L
        private const val NPC_POLICY_SEED_MIX: Long = -0x123456789ABCDEFL
        private const val REWARD_SEED_STRIDE: Long = 0x243F6A8885A308D3L
        private const val REWARD_SEED_MIX: Long = -0x7E1B2C3D4E5F6071L
        // Power-up rewards still use seeded sampling; policy rewards use the
        // checked-in Adventure ranking instead.
        private const val REWARD_KIND_POWERUP: Long = 0x2020202020202020L

        /** Maximum number of choices offered to the player on a non-final maze win. */
        const val REWARD_SAMPLE_SIZE = 3
    }
}
