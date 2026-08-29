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

private const val EASY_DIFFICULTY = "Easy"
private const val MEDIUM_DIFFICULTY = "Medium"
private const val HARD_DIFFICULTY = "Hard"

private val CATALOGUE_ID = Regex("^[a-z][a-z0-9_]*$")
private val CATALOGUE_ID_LIST = Regex("^(?:[a-z][a-z0-9_]*)(?:,[a-z][a-z0-9_]*)*$")

private enum class AdventureTelemetryPropertyType {
    BOOLEAN,
    INTEGER,
    CATALOGUE_ID,
    CATALOGUE_ID_LIST,
    DIFFICULTY
}

private val propertyTypeByKey: Map<String, AdventureTelemetryPropertyType> = mapOf(
    AdventureTelemetryPropertyNames.DIFFICULTY to AdventureTelemetryPropertyType.DIFFICULTY,
    AdventureTelemetryPropertyNames.MAZE_INDEX to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.NEXT_MAZE_INDEX to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.TOTAL_MAZES to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.ELAPSED_SECONDS to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.TOTAL_ELAPSED_SECONDS to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.STEPS to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.TOTAL_STEPS to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.DEATHS_THIS_RUN to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.DEATH_COUNT_DELTA to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.LIVES_REMAINING to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.RETRY_NUMBER to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.COMPLETED to AdventureTelemetryPropertyType.BOOLEAN,
    AdventureTelemetryPropertyNames.REWARD_TYPE to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.REWARD_ID to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.OFFERED_REWARD_IDS to AdventureTelemetryPropertyType.CATALOGUE_ID_LIST,
    AdventureTelemetryPropertyNames.CHOICE_ID to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.OFFERED_CHOICE_IDS to AdventureTelemetryPropertyType.CATALOGUE_ID_LIST,
    AdventureTelemetryPropertyNames.CATEGORY to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.OFFERED_CATEGORIES to AdventureTelemetryPropertyType.CATALOGUE_ID_LIST,
    AdventureTelemetryPropertyNames.NPC_COUNT to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.NPC_COUNT_DELTA to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.REWARD_OPTION_DELTA to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.ELITE_REQUESTED to AdventureTelemetryPropertyType.BOOLEAN,
    AdventureTelemetryPropertyNames.NEXT_MAZE_WON to AdventureTelemetryPropertyType.BOOLEAN,
    AdventureTelemetryPropertyNames.MODIFIER_ID to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.ELITE_COUNT to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.PLAYER_POLICY to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.ACTIVE_POWER_UP to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.DEATH_CAUSE to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.PERK_ID to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.PERK_IDS to AdventureTelemetryPropertyType.CATALOGUE_ID_LIST,
    AdventureTelemetryPropertyNames.OFFERED_PERK_IDS to AdventureTelemetryPropertyType.CATALOGUE_ID_LIST,
    AdventureTelemetryPropertyNames.CURRENT_STACKS to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.STACK_AFTER_CHOICE to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.AFFECTED_SYSTEM to AdventureTelemetryPropertyType.CATALOGUE_ID,
    AdventureTelemetryPropertyNames.AMOUNT to AdventureTelemetryPropertyType.INTEGER,
    AdventureTelemetryPropertyNames.TRIGGER to AdventureTelemetryPropertyType.CATALOGUE_ID
)

private val allowedPropertiesByEvent: Map<String, Set<String>> = mapOf(
    AdventureTelemetryEventNames.RUN_STARTED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.TOTAL_MAZES,
        AdventureTelemetryPropertyNames.PLAYER_POLICY,
        AdventureTelemetryPropertyNames.RETRY_NUMBER
    ),
    AdventureTelemetryEventNames.RUN_COMPLETED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.TOTAL_MAZES,
        AdventureTelemetryPropertyNames.TOTAL_ELAPSED_SECONDS,
        AdventureTelemetryPropertyNames.TOTAL_STEPS,
        AdventureTelemetryPropertyNames.COMPLETED
    ),
    AdventureTelemetryEventNames.RUN_LOST to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.LIVES_REMAINING,
        AdventureTelemetryPropertyNames.DEATH_CAUSE,
        AdventureTelemetryPropertyNames.DEATHS_THIS_RUN
    ),
    AdventureTelemetryEventNames.RUN_ABANDONED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.ELAPSED_SECONDS,
        AdventureTelemetryPropertyNames.STEPS,
        AdventureTelemetryPropertyNames.RETRY_NUMBER
    ),
    AdventureTelemetryEventNames.SESSION_ENDED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.TOTAL_ELAPSED_SECONDS,
        AdventureTelemetryPropertyNames.TOTAL_STEPS,
        AdventureTelemetryPropertyNames.COMPLETED
    ),
    AdventureTelemetryEventNames.MAZE_STARTED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.TOTAL_MAZES,
        AdventureTelemetryPropertyNames.RETRY_NUMBER,
        AdventureTelemetryPropertyNames.NPC_COUNT
    ),
    AdventureTelemetryEventNames.MAZE_COMPLETED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.TOTAL_STEPS,
        AdventureTelemetryPropertyNames.ELAPSED_SECONDS,
        AdventureTelemetryPropertyNames.COMPLETED
    ),
    AdventureTelemetryEventNames.MAZE_FAILED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.DEATH_CAUSE,
        AdventureTelemetryPropertyNames.LIVES_REMAINING,
        AdventureTelemetryPropertyNames.RETRY_NUMBER
    ),
    AdventureTelemetryEventNames.MAZE_RETRIED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.RETRY_NUMBER,
        AdventureTelemetryPropertyNames.NEXT_MAZE_WON
    ),
    AdventureTelemetryEventNames.DEATH_CONTEXT to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.DEATH_CAUSE,
        AdventureTelemetryPropertyNames.LIVES_REMAINING,
        AdventureTelemetryPropertyNames.STEPS
    ),
    AdventureTelemetryEventNames.REWARD_OFFERED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.OFFERED_REWARD_IDS,
        AdventureTelemetryPropertyNames.OFFERED_CHOICE_IDS,
        AdventureTelemetryPropertyNames.OFFERED_CATEGORIES
    ),
    AdventureTelemetryEventNames.REWARD_CHOSEN to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.REWARD_ID,
        AdventureTelemetryPropertyNames.REWARD_TYPE,
        AdventureTelemetryPropertyNames.CHOICE_ID,
        AdventureTelemetryPropertyNames.CATEGORY
    ),
    AdventureTelemetryEventNames.ROUTE_EVENT_OFFERED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.OFFERED_CATEGORIES,
        AdventureTelemetryPropertyNames.OFFERED_CHOICE_IDS
    ),
    AdventureTelemetryEventNames.ROUTE_EVENT_CHOSEN to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.CHOICE_ID,
        AdventureTelemetryPropertyNames.CATEGORY
    ),
    AdventureTelemetryEventNames.ROUTE_EVENT_APPLIED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.CHOICE_ID,
        AdventureTelemetryPropertyNames.CATEGORY,
        AdventureTelemetryPropertyNames.AMOUNT
    ),
    AdventureTelemetryEventNames.ROUTE_EVENT_OUTCOME to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.CHOICE_ID,
        AdventureTelemetryPropertyNames.CATEGORY,
        AdventureTelemetryPropertyNames.NEXT_MAZE_WON,
        AdventureTelemetryPropertyNames.AMOUNT
    ),
    AdventureTelemetryEventNames.ELITE_MODIFIER_SPAWNED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.MODIFIER_ID,
        AdventureTelemetryPropertyNames.NPC_COUNT,
        AdventureTelemetryPropertyNames.ELITE_COUNT
    ),
    AdventureTelemetryEventNames.ELITE_MODIFIER_OUTCOME to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.MODIFIER_ID,
        AdventureTelemetryPropertyNames.ACTIVE_POWER_UP,
        AdventureTelemetryPropertyNames.PLAYER_POLICY,
        AdventureTelemetryPropertyNames.DEATH_CAUSE
    ),
    AdventureTelemetryEventNames.PERK_OFFER_SHOWN to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.OFFERED_PERK_IDS
    ),
    AdventureTelemetryEventNames.PERK_CHOSEN to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.PERK_ID,
        AdventureTelemetryPropertyNames.CURRENT_STACKS,
        AdventureTelemetryPropertyNames.STACK_AFTER_CHOICE
    ),
    AdventureTelemetryEventNames.PERK_EFFECT_APPLIED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.PERK_ID,
        AdventureTelemetryPropertyNames.AFFECTED_SYSTEM,
        AdventureTelemetryPropertyNames.AMOUNT,
        AdventureTelemetryPropertyNames.TRIGGER
    ),
    AdventureTelemetryEventNames.PERK_CONSUMED to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.MAZE_INDEX,
        AdventureTelemetryPropertyNames.PERK_ID,
        AdventureTelemetryPropertyNames.CURRENT_STACKS,
        AdventureTelemetryPropertyNames.TRIGGER
    ),
    AdventureTelemetryEventNames.PERK_RUN_OUTCOME to setOf(
        AdventureTelemetryPropertyNames.DIFFICULTY,
        AdventureTelemetryPropertyNames.TOTAL_MAZES,
        AdventureTelemetryPropertyNames.PERK_IDS,
        AdventureTelemetryPropertyNames.CURRENT_STACKS
    )
)

class AdventureTelemetryEvent(
    val name: String,
    properties: Map<String, String> = emptyMap()
) {
    val properties: Map<String, String> = validateEvent(name, properties)

    companion object {
        fun runStarted(
            difficulty: String,
            mazeIndex: Int? = null,
            retryNumber: Int? = null,
            playerPolicy: String? = null
        ): AdventureTelemetryEvent {
            val props = linkedMapOf<String, String>()
            props[AdventureTelemetryPropertyNames.DIFFICULTY] = difficulty
            mazeIndex?.let { props[AdventureTelemetryPropertyNames.MAZE_INDEX] = it.toString() }
            retryNumber?.let { props[AdventureTelemetryPropertyNames.RETRY_NUMBER] = it.toString() }
            playerPolicy?.let { props[AdventureTelemetryPropertyNames.PLAYER_POLICY] = it }
            return AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.RUN_STARTED,
                properties = props
            )
        }

        fun rewardChosen(
            difficulty: String,
            mazeIndex: Int,
            rewardId: String,
            rewardType: String? = null,
            choiceId: String? = null,
            category: String? = null
        ): AdventureTelemetryEvent {
            val props = linkedMapOf<String, String>()
            props[AdventureTelemetryPropertyNames.DIFFICULTY] = difficulty
            props[AdventureTelemetryPropertyNames.MAZE_INDEX] = mazeIndex.toString()
            props[AdventureTelemetryPropertyNames.REWARD_ID] = rewardId
            rewardType?.let { props[AdventureTelemetryPropertyNames.REWARD_TYPE] = it }
            choiceId?.let { props[AdventureTelemetryPropertyNames.CHOICE_ID] = it }
            category?.let { props[AdventureTelemetryPropertyNames.CATEGORY] = it }
            return AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.REWARD_CHOSEN,
                properties = props
            )
        }
    }

    override fun equals(other: Any?): Boolean =
        other is AdventureTelemetryEvent &&
            name == other.name &&
            properties == other.properties

    override fun hashCode(): Int = 31 * name.hashCode() + properties.hashCode()

    override fun toString(): String =
        "AdventureTelemetryEvent(name=$name, properties=$properties)"
}

private fun validateEvent(
    name: String,
    properties: Map<String, String>
): Map<String, String> {
    require(name in AdventureTelemetryEventNames.allSet) {
        "Unknown Adventure telemetry event: $name"
    }

    val sanitizedProperties = linkedMapOf<String, String>()
    for ((key, value) in properties) {
        require(key in AdventureTelemetryPropertyNames.allSet) {
            "Unknown Adventure telemetry property: $key"
        }
        require(key in allowedPropertiesByEvent.getValue(name)) {
            "Adventure event '$name' does not allow property '$key'"
        }
        val typedValue = validatePropertyValue(key, value)
        sanitizedProperties[key] = typedValue
    }

    return sanitizedProperties.toMap()
}

private fun validatePropertyValue(key: String, value: String): String {
    val trimmed = value.trim()
    val expectedType = propertyTypeByKey.getValue(key)
    return when (expectedType) {
        AdventureTelemetryPropertyType.BOOLEAN -> {
            require(trimmed in setOf("true", "false")) {
                "Property '$key' must be a boolean string ('true' or 'false')"
            }
            trimmed
        }
        AdventureTelemetryPropertyType.INTEGER -> {
            require(trimmed.all { it.isDigit() || it == '-' }) {
                "Property '$key' must be an integer"
            }
            // Reject values like "2.0" or leading + sign while keeping the canonical negative/positive range.
            require(trimmed.toIntOrNull() != null) {
                "Property '$key' must parse as an integer"
            }
            trimmed
        }
        AdventureTelemetryPropertyType.CATALOGUE_ID -> {
            require(trimmed.matches(CATALOGUE_ID)) {
                "Property '$key' must use a stable catalogue ID (lower_snake_case)"
            }
            trimmed
        }
        AdventureTelemetryPropertyType.CATALOGUE_ID_LIST -> {
            require(trimmed.matches(CATALOGUE_ID_LIST)) {
                "Property '$key' must be a comma-separated list of stable catalogue IDs"
            }
            trimmed
        }
        AdventureTelemetryPropertyType.DIFFICULTY -> {
            require(trimmed in setOf(EASY_DIFFICULTY, MEDIUM_DIFFICULTY, HARD_DIFFICULTY)) {
                "Property '$key' must be one of: Easy, Medium, Hard"
            }
            trimmed
        }
    }
}

fun interface AdventureTelemetrySink {
    fun record(event: AdventureTelemetryEvent)
}

object NoOpAdventureTelemetrySink : AdventureTelemetrySink {
    override fun record(event: AdventureTelemetryEvent) = Unit
}
