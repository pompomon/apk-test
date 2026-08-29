package com.example.apktest.telemetry

/**
 * Stable wire names reserved for Adventure telemetry.
 *
 * No production sink is installed yet. Future integrations should emit only
 * these events and the aggregate properties in [AdventureTelemetryPropertyNames].
 */
object AdventureTelemetryEventNames {
    const val RUN_STARTED = "adventure_run_started"
    const val RUN_COMPLETED = "adventure_run_completed"
    const val RUN_LOST = "adventure_run_lost"
    const val RUN_ABANDONED = "adventure_run_abandoned"
    const val SESSION_ENDED = "adventure_session_ended"

    const val MAZE_STARTED = "adventure_maze_started"
    const val MAZE_COMPLETED = "adventure_maze_completed"
    const val MAZE_FAILED = "adventure_maze_failed"
    const val MAZE_RETRIED = "adventure_maze_retried"
    const val DEATH_CONTEXT = "adventure_death_context"

    const val REWARD_OFFERED = "adventure_reward_offered"
    const val REWARD_CHOSEN = "adventure_reward_chosen"

    const val ROUTE_EVENT_OFFERED = "adventure_route_event_offered"
    const val ROUTE_EVENT_CHOSEN = "adventure_route_event_chosen"
    const val ROUTE_EVENT_APPLIED = "adventure_route_event_applied"
    const val ROUTE_EVENT_OUTCOME = "adventure_route_event_outcome"

    const val ELITE_MODIFIER_SPAWNED = "adventure_elite_modifier_spawned"
    const val ELITE_MODIFIER_OUTCOME = "adventure_elite_modifier_outcome"

    const val PERK_OFFER_SHOWN = "adventure_perk_offer_shown"
    const val PERK_CHOSEN = "adventure_perk_chosen"
    const val PERK_EFFECT_APPLIED = "adventure_perk_effect_applied"
    const val PERK_CONSUMED = "adventure_perk_consumed"
    const val PERK_RUN_OUTCOME = "adventure_perk_run_outcome"

    val all: List<String> = listOf(
        RUN_STARTED,
        RUN_COMPLETED,
        RUN_LOST,
        RUN_ABANDONED,
        SESSION_ENDED,
        MAZE_STARTED,
        MAZE_COMPLETED,
        MAZE_FAILED,
        MAZE_RETRIED,
        DEATH_CONTEXT,
        REWARD_OFFERED,
        REWARD_CHOSEN,
        ROUTE_EVENT_OFFERED,
        ROUTE_EVENT_CHOSEN,
        ROUTE_EVENT_APPLIED,
        ROUTE_EVENT_OUTCOME,
        ELITE_MODIFIER_SPAWNED,
        ELITE_MODIFIER_OUTCOME,
        PERK_OFFER_SHOWN,
        PERK_CHOSEN,
        PERK_EFFECT_APPLIED,
        PERK_CONSUMED,
        PERK_RUN_OUTCOME
    )

    internal val allSet: Set<String> = all.toSet()
}

/**
 * Allowlisted aggregate property names for [AdventureTelemetryEvent].
 *
 * Raw seeds, positions, saved-state JSON, and user or device identifiers are
 * intentionally absent. Choice, reward, modifier, and perk values must be
 * stable catalogue IDs rather than player-entered text.
 */
object AdventureTelemetryPropertyNames {
    const val DIFFICULTY = "difficulty"
    const val MAZE_INDEX = "maze_index"
    const val NEXT_MAZE_INDEX = "next_maze_index"
    const val TOTAL_MAZES = "total_mazes"
    const val ELAPSED_SECONDS = "elapsed_seconds"
    const val TOTAL_ELAPSED_SECONDS = "total_elapsed_seconds"
    const val STEPS = "steps"
    const val TOTAL_STEPS = "total_steps"
    const val DEATHS_THIS_RUN = "deaths_this_run"
    const val DEATH_COUNT_DELTA = "death_count_delta"
    const val LIVES_REMAINING = "lives_remaining"
    const val RETRY_NUMBER = "retry_number"
    const val COMPLETED = "completed"

    const val REWARD_TYPE = "reward_type"
    const val REWARD_ID = "reward_id"
    const val OFFERED_REWARD_IDS = "offered_reward_ids"

    const val CHOICE_ID = "choice_id"
    const val OFFERED_CHOICE_IDS = "offered_choice_ids"
    const val CATEGORY = "category"
    const val OFFERED_CATEGORIES = "offered_categories"
    const val NPC_COUNT = "npc_count"
    const val NPC_COUNT_DELTA = "npc_count_delta"
    const val REWARD_OPTION_DELTA = "reward_option_delta"
    const val ELITE_REQUESTED = "elite_requested"
    const val NEXT_MAZE_WON = "next_maze_won"

    const val MODIFIER_ID = "modifier_id"
    const val ELITE_COUNT = "elite_count"
    const val PLAYER_POLICY = "player_policy"
    const val ACTIVE_POWER_UP = "active_power_up"
    const val DEATH_CAUSE = "death_cause"

    const val PERK_ID = "perk_id"
    const val PERK_IDS = "perk_ids"
    const val OFFERED_PERK_IDS = "offered_perk_ids"
    const val CURRENT_STACKS = "current_stacks"
    const val STACK_AFTER_CHOICE = "stack_after_choice"
    const val AFFECTED_SYSTEM = "affected_system"
    const val AMOUNT = "amount"
    const val TRIGGER = "trigger"

    val all: List<String> = listOf(
        DIFFICULTY,
        MAZE_INDEX,
        NEXT_MAZE_INDEX,
        TOTAL_MAZES,
        ELAPSED_SECONDS,
        TOTAL_ELAPSED_SECONDS,
        STEPS,
        TOTAL_STEPS,
        DEATHS_THIS_RUN,
        DEATH_COUNT_DELTA,
        LIVES_REMAINING,
        RETRY_NUMBER,
        COMPLETED,
        REWARD_TYPE,
        REWARD_ID,
        OFFERED_REWARD_IDS,
        CHOICE_ID,
        OFFERED_CHOICE_IDS,
        CATEGORY,
        OFFERED_CATEGORIES,
        NPC_COUNT,
        NPC_COUNT_DELTA,
        REWARD_OPTION_DELTA,
        ELITE_REQUESTED,
        NEXT_MAZE_WON,
        MODIFIER_ID,
        ELITE_COUNT,
        PLAYER_POLICY,
        ACTIVE_POWER_UP,
        DEATH_CAUSE,
        PERK_ID,
        PERK_IDS,
        OFFERED_PERK_IDS,
        CURRENT_STACKS,
        STACK_AFTER_CHOICE,
        AFFECTED_SYSTEM,
        AMOUNT,
        TRIGGER
    )

    internal val allSet: Set<String> = all.toSet()
}

data class AdventureTelemetryEvent(
    val name: String,
    val properties: Map<String, String> = emptyMap()
) {
    init {
        require(name in AdventureTelemetryEventNames.allSet) {
            "Unknown Adventure telemetry event: $name"
        }
        val unknownProperties = properties.keys - AdventureTelemetryPropertyNames.allSet
        require(unknownProperties.isEmpty()) {
            "Unknown Adventure telemetry properties: ${unknownProperties.sorted()}"
        }
    }
}

fun interface AdventureTelemetrySink {
    fun record(event: AdventureTelemetryEvent)
}

object NoOpAdventureTelemetrySink : AdventureTelemetrySink {
    override fun record(event: AdventureTelemetryEvent) = Unit
}
