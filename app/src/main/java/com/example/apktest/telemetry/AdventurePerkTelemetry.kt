package com.example.apktest.telemetry

import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkStack
import kotlin.math.floor

/** Aggregate-only events. Decision/consumption callers dispatch after the durable save. */
class AdventurePerkTelemetry(
    private val sink: AdventureTelemetrySink = NoOpAdventureTelemetrySink
) {
    fun offered(difficulty: String, mazeIndex: Int, offered: List<RunPerkId>, owned: List<RunPerkStack>) {
        if (offered.isEmpty()) return
        dispatch(difficulty, AdventureTelemetryEventNames.PERK_OFFER_SHOWN) {
            mapOf(
                "difficulty" to difficulty, "maze_index" to mazeIndex.coerceAtLeast(1).toString(),
                "offered_perk_ids" to offered.joinToString(",") { it.id },
                "current_stacks" to totalStacks(owned)
            )
        }
    }

    fun chosen(
        difficulty: String,
        mazeIndex: Int,
        id: RunPerkId,
        offered: List<RunPerkId>,
        owned: List<RunPerkStack>,
        livesRemaining: Int
    ) {
        val stack = owned.firstOrNull { it.id == id } ?: return
        if (id !in offered) return
        dispatch(difficulty, AdventureTelemetryEventNames.PERK_CHOSEN) {
            mapOf(
                "difficulty" to difficulty, "maze_index" to mazeIndex.coerceAtLeast(1).toString(),
                "perk_id" to id.id, "offered_perk_ids" to offered.joinToString(",") { it.id },
                "current_stacks" to totalStacks(owned), "stack_after_choice" to stack.stacks.toString(),
                "lives_remaining" to livesRemaining.coerceAtLeast(0).toString()
            )
        }
    }

    /**
     * Amount is percentage points, bonus seconds, radius cells, or extra reward options.
     * Scout reports one locked next-maze preview, not NPC identities or positions.
     * The fixed perk/system pairs reject arbitrary caller-provided strings.
     */
    fun effectApplied(difficulty: String, mazeIndex: Int, id: RunPerkId, system: String, amount: Int) {
        val expected = when (id) {
            RunPerkId.QUICK_FEET -> Triple("player_speed_percent", setOf(5, 10, 15), "maze_start")
            RunPerkId.LONGER_CHARGE -> Triple("power_up_duration_seconds", setOf(1, 2, 3), "player_pickup")
            RunPerkId.POCKET_MAGNET -> Triple("magnet_radius_cells", setOf(1, 2), "maze_start")
            RunPerkId.FIRST_SHIELD -> return
            RunPerkId.SCOUT_SENSE -> Triple("reward_preview", setOf(1), "reward_choice")
            RunPerkId.SECOND_WIND -> Triple("freeze_pulse_seconds", setOf(1), "capture")
            RunPerkId.RISK_DIVIDEND -> Triple("reward_options", setOf(1), "risky_route_win")
        }
        if (system != expected.first || amount !in expected.second) return
        dispatch(difficulty, AdventureTelemetryEventNames.PERK_EFFECT_APPLIED) {
            mapOf(
                "difficulty" to difficulty, "maze_index" to mazeIndex.coerceAtLeast(1).toString(),
                "perk_id" to id.id, "affected_system" to system, "amount" to amount.toString(),
                "trigger" to expected.third
            )
        }
    }

    fun consumed(difficulty: String, mazeIndex: Int, id: RunPerkId, owned: List<RunPerkStack>) {
        if (id != RunPerkId.SECOND_WIND || owned.none { it.id == id && it.consumed }) return
        dispatch(difficulty, AdventureTelemetryEventNames.PERK_CONSUMED) {
            mapOf(
                "difficulty" to difficulty, "maze_index" to mazeIndex.coerceAtLeast(1).toString(),
                "perk_id" to id.id, "current_stacks" to totalStacks(owned), "trigger" to "capture"
            )
        }
    }

    fun outcome(
        difficulty: String,
        totalMazes: Int,
        owned: List<RunPerkStack>,
        completed: Boolean,
        elapsedSeconds: Float,
        deathsThisRun: Int
    ) {
        if (owned.isEmpty()) return
        dispatch(difficulty, AdventureTelemetryEventNames.PERK_RUN_OUTCOME) {
            val seconds = if (elapsedSeconds.isFinite()) {
                floor(elapsedSeconds.toDouble()).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
            } else 0
            mapOf(
                "difficulty" to difficulty, "total_mazes" to totalMazes.coerceAtLeast(1).toString(),
                "perk_ids" to owned.sortedBy { it.id.ordinal }.joinToString(",") { it.id.id },
                "current_stacks" to totalStacks(owned), "completed" to completed.toString(),
                "total_elapsed_seconds" to seconds.toString(),
                "deaths_this_run" to deathsThisRun.coerceAtLeast(0).toString()
            )
        }
    }

    private fun totalStacks(owned: List<RunPerkStack>): String =
        owned.sumOf { it.stacks.coerceAtLeast(0).toLong() }.coerceAtMost(Int.MAX_VALUE.toLong()).toString()

    private inline fun dispatch(difficulty: String, name: String, properties: () -> Map<String, String>) {
        if (DifficultyPresets.all.none { it.name == difficulty }) return
        try {
            sink.record(AdventureTelemetryEvent(name, properties()))
        } catch (_: Exception) {
            // An optional observer must never affect saving, acknowledging, or gameplay.
        }
    }
}
