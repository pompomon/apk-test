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
 * @property currentMazeNpcSpawnSpecs Per-NPC policy and modifier list for the
 *   currently-active maze (indexed by spawn `Npc.id`). Locked alongside
 *   [currentMazeSeed] so a death replay keeps the same NPC strategies and modifiers.
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
    var currentMazeNpcSpawnSpecs: List<NpcSpawnSpec> = emptyList(),
    var currentMazeSnapshot: GameEngineSnapshot? = null,
    var status: AdventureStatus = AdventureStatus.IN_PROGRESS,
    /**
     * Power-up the player chose to start the next maze with (granted after a
     * non-final maze win). Treated as locked per-maze state:
     * carried into every [AdventureRunController.prepareCurrentMaze] call
     * (so death replays of the same maze re-apply the same reward) and
     * cleared only when the host advances past the maze via
     * [AdventureRunController.onMazeWon]. Survives process death/restore
     * between persisting state and the GL thread actually applying the
     * power-up to a fresh engine instance.
     */
    var pendingStartingPowerUp: PowerUpType? = null,
    /** Accumulated elapsed seconds from all completed mazes this run. */
    var totalElapsedSeconds: Float = 0f,
    /** Accumulated step count from all completed mazes this run. */
    var totalSteps: Int = 0,
    /** Number of player deaths this run, including the run-ending death. */
    var deathsThisRun: Int = 0,
    var currentMazeNpcCount: Int? = currentMazeSeed?.let { currentMazeNpcSpawnSpecs.size },
    var pendingReward: PendingAdventureReward? = null,
    var activeRoute: PendingRouteEvent? = null,
    var routeHistory: List<RouteEventHistoryEntry> = emptyList(),
    var nextRouteEventMazeIndex: Int = RouteEventGenerator.FIRST_EVENT_MAZE_INDEX,
    var routeEventOrdinal: Int = 0,
    var rewardRerolls: Int = 0,
    var runPerks: List<RunPerkStack> = emptyList(),
    var previousPerkOffer: List<RunPerkId> = emptyList(),
    var perkOfferOrdinal: Int = 0,
    var perkHistory: List<RunPerkHistoryEntry> = emptyList()
) {
    val currentMazeNpcPolicies: List<NpcPolicyType>
        get() = currentMazeNpcSpawnSpecs.map { it.policyType }
}

/**
 * Returned from [AdventureRunController.prepareCurrentMaze] to describe
 * how the host should configure the next maze: the seed to use, the
 * desired NPC count, the per-NPC spawn specs, the player policy to apply,
 * the difficulty preset, and (if non-null) a mid-maze snapshot to restore
 * instead of starting fresh.
 */
data class MazeStartupSpec(
    val seed: Long,
    val difficulty: DifficultyPreset,
    val npcCount: Int,
    val npcSpawnSpecs: List<NpcSpawnSpec>,
    val playerPolicy: PlayerPolicyType,
    val midMazeSnapshot: GameEngineSnapshot?,
    /**
     * Power-up to activate at the very start of this maze (granted as the
     * reward for the previous even-maze win). Applied once by the host
     * after engine restart; `null` for mazes with no starting bonus.
     */
    val startingPowerUp: PowerUpType? = null,
    val pickupLifetimeSeconds: Float? = null,
    val runPerkEffects: RunPerkEffects = RunPerkEffects()
) {
    val npcPolicies: List<NpcPolicyType>
        get() = npcSpawnSpecs.map { it.policyType }
}

/**
 * Returned from [AdventureRunController.onMazeWon] to communicate what
 * the host should surface on the win overlay:
 * - the new lives count and whether a bonus life was just awarded,
 * - a list of [startingPowerUpCandidates] (3 deterministic non-GHOST
 *   [PowerUpType]s on every non-final maze win),
 * - the maze index just completed and total mazes for messaging.
 *
 * [startingPowerUpCandidates] is empty only on the final run-complete win.
 */
data class WinOutcome(
    val livesRemaining: Int,
    val bonusLifeAwarded: Boolean,
    val startingPowerUpCandidates: List<PowerUpType>,
    val mazeIndexCompleted: Int,
    val totalMazes: Int,
    val runComplete: Boolean,
    /** Accumulated elapsed seconds across all completed mazes this run. */
    val totalElapsedSeconds: Float,
    /** Accumulated step count across all completed mazes this run. */
    val totalSteps: Int,
    /** Number of player deaths so far this run. */
    val deathsThisRun: Int
) {
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
 * reward selection. No Android imports — fully JVM-testable.
 *
 * The controller is **not** thread-safe; callers should invoke it from a
 * single thread (the Android host's main/UI thread).
 */
class AdventureRunController(
    val config: AdventureConfig,
    initialState: AdventureRunState? = null,
    private val runSeed: Long = System.currentTimeMillis(),
    private val routesEnabled: Boolean = AdventureFeatureFlags.ROUTE_EVENTS_ENABLED &&
        config.difficulty.name == DifficultyPresets.MEDIUM.name,
    private val elitesEnabled: Boolean = AdventureFeatureFlags.ELITE_NPC_MODIFIERS_ENABLED &&
        (config.difficulty.name == DifficultyPresets.MEDIUM.name ||
            config.difficulty.name == DifficultyPresets.HARD.name),
    private val perksEnabled: Boolean = AdventureFeatureFlags.RUN_BUILD_PERKS_ENABLED &&
        config.difficulty.name == DifficultyPresets.MEDIUM.name,
    private val perkTiers: Set<RunPerkTier> = setOf(RunPerkTier.COMMON)
) {
    private val routeGenerator = RouteEventGenerator(config, runSeed)
    private val perkGenerator = RunPerkGenerator(runSeed)
    val state: AdventureRunState = initialState ?: AdventureRunState(
        difficultyName = config.difficulty.name,
        livesRemaining = config.initialLives
    )

    init {
        require(state.difficultyName == config.difficulty.name) {
            "State difficulty (${state.difficultyName}) does not match config (${config.difficulty.name})"
        }
        if (state.currentMazeIndex > 0) {
            unlockAllAutomatedPlayerPolicies()
        }
    }

    /**
     * Prepare the maze the player should now play. Locks [AdventureRunState.currentMazeSeed]
     * and [AdventureRunState.currentMazeNpcSpawnSpecs] on the first call for a
     * given maze (so a death replay returns the *same* spec); subsequent
     * calls before the next [onMazeWon] / [onPlayerDied] are idempotent.
     *
     * If the run is already terminal ([AdventureStatus.WON] / [AdventureStatus.LOST])
     * returns `null` — the host should show the terminal screen instead.
     */
    fun prepareCurrentMaze(): MazeStartupSpec? {
        if (state.status != AdventureStatus.IN_PROGRESS || state.pendingReward != null) return null
        if (state.currentMazeIndex >= config.totalMazes) {
            state.status = AdventureStatus.WON
            return null
        }
        lockCurrentMaze()
        return MazeStartupSpec(
            seed = state.currentMazeSeed!!,
            difficulty = config.difficulty,
            npcCount = state.currentMazeNpcCount!!,
            npcSpawnSpecs = state.currentMazeNpcSpawnSpecs.toList(),
            playerPolicy = state.currentPlayerPolicy,
            midMazeSnapshot = state.currentMazeSnapshot,
            startingPowerUp = state.pendingStartingPowerUp,
            pickupLifetimeSeconds = state.activeRoute?.pickupLifetimeSeconds,
            runPerkEffects = RunPerkEffects.fromStacks(state.runPerks)
        )
    }

    private fun lockCurrentMaze() {
        val target = state.currentMazeIndex + 1
        if (state.currentMazeSeed == null) state.currentMazeSeed = deriveMazeSeed(target)
        if (state.currentMazeNpcCount == null) {
            val count = state.activeRoute?.npcCount ?: config.npcCountForMaze(target)
            state.currentMazeNpcCount = count
            val rng = Random(deriveNpcPolicySeed(target))
            val pool = NpcPolicyType.entries
            val policies = List(count) { pool[rng.nextInt(pool.size)] }
            state.currentMazeNpcSpawnSpecs = if (elitesEnabled) {
                val seed = state.currentMazeSeed!!
                val maze = MazeGenerator.generate(config.difficulty.mazeWidth, config.difficulty.mazeHeight, seed)
                val plan = NpcSpawnPlanner.plan(maze, MazeNavigator(maze), config.difficulty, Random(seed))
                AdventureEliteAssignment.assign(config, target, seed, policies, plan, state.activeRoute?.choiceId)
            } else policies.map { NpcSpawnSpec(it) }
        }
    }

    /**
     * Durable host entry point. The old [onMazeWon] remains available to baseline
     * simulations that intentionally bypass reward dialogs. Repeated host win
     * notifications while any reward stage is pending cannot advance the run.
     */
    fun completeMaze(elapsedSeconds: Float = 0f, steps: Int = 0): WinOutcome {
        state.pendingReward?.let { return pendingWinOutcome(it) }
        val riskDividendRoute = earnedRiskDividendRoute()
        val outcome = onMazeWon(elapsedSeconds, steps)
        if (outcome.runComplete) return outcome
        var choices = emptyList<RouteEventChoice>()
        while (outcome.mazeIndexCompleted >= state.nextRouteEventMazeIndex) {
            val scheduledIndex = state.nextRouteEventMazeIndex
            if (routesEnabled && outcome.mazeIndexCompleted == scheduledIndex) {
                choices = routeGenerator.offer(scheduledIndex, state.routeEventOrdinal)
            }
            state.nextRouteEventMazeIndex = routeGenerator.nextEventMazeIndex(
                scheduledIndex, state.routeEventOrdinal
            )
            state.routeEventOrdinal += 1
        }
        val perkOffer = if (perksEnabled && outcome.mazeIndexCompleted in RunPerkGenerator.OFFER_MAZES) {
            perkGenerator.offer(outcome.mazeIndexCompleted, state.perkOfferOrdinal,
                state.runPerks, state.previousPerkOffer, perkTiers, routesEnabled)
        } else null
        if (perkOffer != null) {
            state.previousPerkOffer = perkOffer.choices.toList()
            state.perkOfferOrdinal += 1
        }
        state.pendingReward = PendingAdventureReward(
            mazeIndexCompleted = outcome.mazeIndexCompleted,
            stage = RewardStage.WIN_ACKNOWLEDGEMENT,
            routeChoices = choices,
            powerUpCandidates = outcome.startingPowerUpCandidates.toList(),
            bonusLifeAwarded = outcome.bonusLifeAwarded,
            perkOffer = perkOffer,
            rewardOptionBonus = if (riskDividendRoute != null) 1 else 0,
            riskDividendRouteId = riskDividendRoute
        )
        return outcome
    }

    fun acknowledgeMazeWin(mazeIndexCompleted: Int): Boolean {
        val reward = rewardAt(mazeIndexCompleted, RewardStage.WIN_ACKNOWLEDGEMENT) ?: return false
        state.pendingReward = if (reward.routeChoices.isEmpty()) {
            withScoutPreview(reward.copy(stage = nextRewardStage(reward)))
        } else reward.copy(stage = RewardStage.ROUTE_CHOICE)
        return true
    }

    fun chooseRoute(mazeIndexCompleted: Int, choiceId: String): Boolean {
        val reward = rewardAt(mazeIndexCompleted, RewardStage.ROUTE_CHOICE) ?: return false
        val choice = reward.routeChoices.firstOrNull { it.id == choiceId } ?: return false
        val route = routeGenerator.resolve(choice, state.currentMazeIndex + 1)
        state.activeRoute = route
        state.routeHistory = state.routeHistory + RouteEventHistoryEntry(mazeIndexCompleted, choiceId)
        lockCurrentMaze()
        val preview = if (choiceId == RouteEventGenerator.SCOUT_MAP) {
            RoutePreview(
                state.nextRouteEventMazeIndex,
                routeGenerator.offer(state.nextRouteEventMazeIndex, state.routeEventOrdinal)
                    .map { it.category },
                state.currentMazeNpcCount!!
            )
        } else null
        state.pendingReward = withScoutPreview(reward.copy(
            stage = nextRewardStage(reward),
            selectedRouteId = choiceId,
            powerUpCandidates = reward.powerUpCandidates.take(
                REWARD_SAMPLE_SIZE + route.rewardOptionDelta + reward.rewardOptionBonus),
            preview = preview
        ))
        return true
    }

    private fun nextRewardStage(reward: PendingAdventureReward): RewardStage =
        if (reward.perkOffer != null && reward.selectedPerkId == null) RewardStage.PERK_CHOICE
        else RewardStage.POWER_UP_CHOICE

    private fun withScoutPreview(reward: PendingAdventureReward): PendingAdventureReward {
        if (state.runPerks.none { it.id == RunPerkId.SCOUT_SENSE }) return reward
        lockCurrentMaze()
        val seed = state.currentMazeSeed!!
        val maze = MazeGenerator.generate(config.difficulty.mazeWidth, config.difficulty.mazeHeight, seed)
        val plan = NpcSpawnPlanner.plan(maze, MazeNavigator(maze), config.difficulty, Random(seed))
        val actualSpecs = state.currentMazeNpcSpawnSpecs.take(plan.candidates.size)
        return reward.copy(scoutPreview = PerkScoutPreview(
            npcCount = actualSpecs.size,
            eliteCount = actualSpecs.count { it.eliteModifier != null }
        ))
    }

    fun choosePerk(mazeIndexCompleted: Int, id: RunPerkId): Boolean {
        val reward = rewardAt(mazeIndexCompleted, RewardStage.PERK_CHOICE) ?: return false
        val offer = reward.perkOffer ?: return false
        if (id !in offer.choices || reward.selectedPerkId != null) return false
        val definition = RunPerkCatalogue.definition(id)
        val owned = state.runPerks.firstOrNull { it.id == id }
        if (!definition.available || (owned?.stacks ?: 0) >= definition.maxStacks) return false
        state.runPerks = if (owned == null) state.runPerks + RunPerkStack(id, 1)
        else state.runPerks.map { if (it.id == id) it.copy(stacks = it.stacks + 1) else it }
        state.perkHistory = state.perkHistory + RunPerkHistoryEntry(offer.detachedCopy(), id)
        state.pendingReward = withScoutPreview(reward.copy(
            selectedPerkId = id, stage = RewardStage.POWER_UP_CHOICE
        ))
        return true
    }

    /** The host persists this transition and the paused engine barrier together before resuming. */
    fun consumePerk(id: RunPerkId): Boolean {
        if (id != RunPerkId.SECOND_WIND) return false
        val perk = state.runPerks.firstOrNull { it.id == id } ?: return false
        if (perk.consumed) return true
        if (state.status != AdventureStatus.IN_PROGRESS || state.pendingReward != null ||
            state.currentMazeSeed == null) return false
        state.runPerks = state.runPerks.map { if (it.id == id) it.copy(consumed = true) else it }
        state.currentMazeSnapshot = null
        return true
    }

    fun chooseStartingPowerUp(mazeIndexCompleted: Int, type: PowerUpType): Boolean {
        val reward = rewardAt(mazeIndexCompleted, RewardStage.POWER_UP_CHOICE) ?: return false
        if (type !in reward.powerUpCandidates) return false
        state.pendingStartingPowerUp = type
        state.pendingReward = null
        return true
    }

    fun rerollStartingPowerUps(mazeIndexCompleted: Int): Boolean {
        val reward = rewardAt(mazeIndexCompleted, RewardStage.POWER_UP_CHOICE) ?: return false
        if (state.rewardRerolls <= 0 || reward.rerollIndex != 0) return false
        val rng = Random(derivePowerUpRewardSeed(mazeIndexCompleted) xor POWERUP_REROLL_SEED_MIX)
        val pool = PowerUpType.entries.filter { it != PowerUpType.GHOST_MODE }
        state.pendingReward = reward.copy(
            powerUpCandidates = pool.shuffled(rng).take(reward.powerUpCandidates.size),
            rerollIndex = reward.rerollIndex + 1
        )
        state.rewardRerolls -= 1
        return true
    }

    private fun rewardAt(index: Int, stage: RewardStage): PendingAdventureReward? =
        state.pendingReward?.takeIf {
            state.status == AdventureStatus.IN_PROGRESS && it.mazeIndexCompleted == index && it.stage == stage
        }

    private fun pendingWinOutcome(reward: PendingAdventureReward): WinOutcome = WinOutcome(
        state.livesRemaining, reward.bonusLifeAwarded, reward.powerUpCandidates.toList(),
        reward.mazeIndexCompleted, config.totalMazes, false, state.totalElapsedSeconds,
        state.totalSteps, state.deathsThisRun
    )

    /**
     * Record a maze win. Increments the maze index, advances the win
     * streak (awarding +1 life every [AdventureConfig.STREAK_BONUS_THRESHOLD]),
     * clears the locked per-maze fields so the next [prepareCurrentMaze]
     * draws a fresh seed and per-NPC spawn specs, clears any mid-maze
     * snapshot, and returns a [WinOutcome] describing the new state.
     *
     * Accumulates [elapsedSeconds] and [steps] (clamped to ≥ 0) into the
     * run totals. Time and steps are never accumulated on death.
     *
     * If this was the final maze sets [AdventureStatus.WON].
     */
    fun onMazeWon(elapsedSeconds: Float = 0f, steps: Int = 0): WinOutcome {
        state.pendingReward?.let { return pendingWinOutcome(it) }
        check(state.status == AdventureStatus.IN_PROGRESS) {
            "onMazeWon called in terminal state ${state.status}"
        }
        val sanitizedElapsed = if (elapsedSeconds.isFinite()) elapsedSeconds.coerceAtLeast(0f) else 0f
        state.totalElapsedSeconds += sanitizedElapsed
        state.totalSteps += steps.coerceAtLeast(0)
        val riskDividendRoute = earnedRiskDividendRoute()
        val newIndex = state.currentMazeIndex + 1
        state.currentMazeIndex = newIndex
        if (newIndex == 1) {
            unlockAllAutomatedPlayerPolicies()
        }
        val route = state.activeRoute?.takeIf { it.mazeIndexAppliedTo == newIndex }
        state.winStreakSinceLastBonus += 1 + (route?.effects?.firstOrNull {
            it.type == RouteEventEffectType.STREAK_PROGRESS_DELTA
        }?.intValue ?: 0)
        if (route?.effects?.any { it.type == RouteEventEffectType.REWARD_REROLL } == true) {
            state.rewardRerolls = 1
        }
        val bonus = state.winStreakSinceLastBonus >= AdventureConfig.STREAK_BONUS_THRESHOLD
        if (bonus) {
            state.livesRemaining += 1
            state.winStreakSinceLastBonus = 0
        }
        state.currentMazeSeed = null
        state.currentMazeNpcCount = null
        state.currentMazeNpcSpawnSpecs = emptyList()
        state.currentMazeSnapshot = null
        // Locked starting power-up is per-maze: clear once we advance past
        // the maze it was reserved for. Death replays keep it so the same
        // reward is re-applied on the retry.
        state.pendingStartingPowerUp = null
        state.activeRoute = null

        val runComplete = newIndex >= config.totalMazes
        if (runComplete) {
            state.status = AdventureStatus.WON
            clearTerminalRouteState()
        }

        val powerUpCandidates = if (runComplete) emptyList()
        else sampleStartingPowerUps(REWARD_SAMPLE_SIZE + if (riskDividendRoute != null) 1 else 0, newIndex)

        return WinOutcome(
            livesRemaining = state.livesRemaining,
            bonusLifeAwarded = bonus,
            startingPowerUpCandidates = powerUpCandidates,
            mazeIndexCompleted = newIndex,
            totalMazes = config.totalMazes,
            runComplete = runComplete,
            totalElapsedSeconds = state.totalElapsedSeconds,
            totalSteps = state.totalSteps,
            deathsThisRun = state.deathsThisRun
        )
    }

    private fun earnedRiskDividendRoute(): String? =
        state.activeRoute?.takeIf {
            it.mazeIndexAppliedTo == state.currentMazeIndex + 1 &&
                RouteEventGenerator.choice(it.choiceId)?.category == RouteEventCategory.RISKY &&
                state.runPerks.any { perk -> perk.id == RunPerkId.RISK_DIVIDEND }
        }?.choiceId

    /**
     * Deterministically samples up to [count] [PowerUpType]s (excluding
     * [PowerUpType.GHOST_MODE]) using an RNG derived from [runSeed] and
     * [mazeIndex1Based]. SHIELD, SLOW_TIME, and MAGNET are valid starting
     * rewards.
     */
    internal fun sampleStartingPowerUps(count: Int, mazeIndex1Based: Int): List<PowerUpType> {
        val pool = PowerUpType.entries.filter { it != PowerUpType.GHOST_MODE }
        if (pool.isEmpty() || count <= 0) return emptyList()
        val rng = Random(derivePowerUpRewardSeed(mazeIndex1Based))
        if (pool.size <= count) return pool.shuffled(rng)
        return pool.shuffled(rng).take(count)
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
     * [AdventureRunState.currentMazeSeed] and [AdventureRunState.currentMazeNpcSpawnSpecs]
     * are deliberately preserved so the same maze is replayed.
     *
     * Increments [AdventureRunState.deathsThisRun] on every death, including
     * the final run-ending death. Time and steps are never accumulated here.
     *
     * Transitions to [AdventureStatus.LOST] when lives reach zero.
     */
    fun onPlayerDied(): DeathOutcome {
        if (state.status != AdventureStatus.IN_PROGRESS || state.pendingReward != null) {
            return DeathOutcome(state.livesRemaining, state.status != AdventureStatus.IN_PROGRESS)
        }
        state.deathsThisRun += 1
        state.livesRemaining = (state.livesRemaining - 1).coerceAtLeast(0)
        state.winStreakSinceLastBonus = 0
        state.currentMazeSnapshot = null
        val runOver = state.livesRemaining <= 0
        if (runOver) {
            state.status = AdventureStatus.LOST
            state.currentMazeSeed = null
            state.currentMazeNpcCount = null
            state.currentMazeNpcSpawnSpecs = emptyList()
            state.pendingStartingPowerUp = null
            clearTerminalRouteState()
        }
        return DeathOutcome(livesRemaining = state.livesRemaining, runOver = runOver)
    }

    /**
     * Persist [engineSnapshot] as the mid-maze snapshot so a subsequent
     * resume can pick up exactly where the player paused. The host
     * typically calls this from `onPause` after capturing the snapshot
     * from the GL thread.
     */
    fun recordMidMazeSnapshot(engineSnapshot: GameEngineSnapshot) {
        if (state.status != AdventureStatus.IN_PROGRESS || state.pendingReward != null) return
        if (!engineSnapshot.matchesAdventureMaze(state.difficultyName, state.currentMazeSeed,
                state.currentMazeNpcCount, state.currentMazeNpcSpawnSpecs, state.activeRoute?.pickupLifetimeSeconds,
                RunPerkEffects.fromStacks(state.runPerks),
                state.runPerks.any { it.id == RunPerkId.SECOND_WIND && it.consumed })) return
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

    private fun unlockAllAutomatedPlayerPolicies() {
        automatedPlayerPolicies().forEach { policy ->
            if (policy !in state.unlockedPlayerPolicies) {
                state.unlockedPlayerPolicies.add(policy)
            }
        }
    }

    private fun clearTerminalRouteState() {
        state.pendingReward = null
        state.activeRoute = null
        state.routeHistory = emptyList()
        state.rewardRerolls = 0
        state.routeEventOrdinal = 0
        state.nextRouteEventMazeIndex = RouteEventGenerator.FIRST_EVENT_MAZE_INDEX
    }

    private fun deriveMazeSeed(mazeIndex1Based: Int): Long =
        runSeed xor (mazeIndex1Based.toLong() * MAZE_SEED_STRIDE) xor MAZE_SEED_MIX

    private fun deriveNpcPolicySeed(mazeIndex1Based: Int): Long =
        runSeed xor (mazeIndex1Based.toLong() * NPC_POLICY_SEED_STRIDE) xor NPC_POLICY_SEED_MIX

    private fun derivePowerUpRewardSeed(mazeIndex1Based: Int): Long =
        runSeed xor (mazeIndex1Based.toLong() * POWERUP_REWARD_SEED_STRIDE) xor POWERUP_REWARD_SEED_MIX

    companion object {
        // Arbitrary mix constants — chosen as signed-Long literals so they
        // compile-time-evaluate without UInt.toLong() (same constraint
        // documented in [GameEngine.NPC_RANDOM_SEED_MIX]).
        private const val MAZE_SEED_STRIDE: Long = 0x12B9B0A1CE4A11BL
        private const val MAZE_SEED_MIX: Long = -0x4F2C5D6E7A8B9C0DL
        private const val NPC_POLICY_SEED_STRIDE: Long = 0x6A09E667F3BCC908L
        private const val NPC_POLICY_SEED_MIX: Long = -0x123456789ABCDEFL
        private const val POWERUP_REWARD_SEED_STRIDE: Long = 0x243F6A8885A308D3L
        // Folds in REWARD_KIND_POWERUP (0x2020202020202020) from the original
        // deriveRewardSeed(mazeIndex1Based, REWARD_KIND_POWERUP) formula so that
        // the power-up candidate sequence is identical to the pre-refactor output.
        private const val POWERUP_REWARD_SEED_MIX: Long = -0x5E3B0C1D6E7F4051L
        private const val POWERUP_REROLL_SEED_MIX: Long = 0x73AE8150DB4906FL

        /** Maximum number of choices offered to the player on a non-final maze win. */
        const val REWARD_SAMPLE_SIZE = 3
    }
}
