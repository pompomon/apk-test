package com.example.apktest.telemetry

import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.EliteNpcModifier
import com.example.apktest.game.core.NpcSpawnSpec
import com.example.apktest.game.core.PlayerPolicyType
import java.util.Locale
import kotlin.math.floor

/** Best-effort aggregate events; hosts call only after a saved attempt transition. */
class AdventureEliteTelemetry(
    private val sink: AdventureTelemetrySink = NoOpAdventureTelemetrySink
) {
    fun spawned(
        difficulty: String,
        mazeIndex: Int,
        npcs: List<NpcSpawnSpec>,
        playerPolicy: PlayerPolicyType
    ) {
        if (DifficultyPresets.all.none { it.name == difficulty }) return
        for (modifier in EliteNpcModifier.entries) {
            val count = npcs.count { it.eliteModifier == modifier }
            if (count == 0) continue
            dispatch {
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ELITE_MODIFIER_SPAWNED,
                    mapOf(
                        AdventureTelemetryPropertyNames.DIFFICULTY to difficulty,
                        AdventureTelemetryPropertyNames.MAZE_INDEX to mazeIndex.coerceAtLeast(1).toString(),
                        AdventureTelemetryPropertyNames.MODIFIER_ID to modifier.id,
                        AdventureTelemetryPropertyNames.NPC_COUNT to npcs.size.toString(),
                        AdventureTelemetryPropertyNames.ELITE_COUNT to count.toString(),
                        AdventureTelemetryPropertyNames.PLAYER_POLICY to playerPolicy.name.lowercase(Locale.ROOT)
                    )
                )
            }
        }
    }

    fun outcome(
        difficulty: String,
        mazeIndex: Int,
        modifiers: Set<EliteNpcModifier>,
        completed: Boolean,
        elapsedSeconds: Float,
        steps: Int,
        deathsThisRun: Int
    ) {
        if (DifficultyPresets.all.none { it.name == difficulty }) return
        for (modifier in EliteNpcModifier.entries) {
            if (modifier !in modifiers) continue
            dispatch {
                val seconds = if (elapsedSeconds.isFinite()) {
                    floor(elapsedSeconds.toDouble()).coerceIn(0.0, Int.MAX_VALUE.toDouble()).toInt()
                } else 0
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ELITE_MODIFIER_OUTCOME,
                    mapOf(
                        AdventureTelemetryPropertyNames.DIFFICULTY to difficulty,
                        AdventureTelemetryPropertyNames.MAZE_INDEX to mazeIndex.coerceAtLeast(1).toString(),
                        AdventureTelemetryPropertyNames.MODIFIER_ID to modifier.id,
                        AdventureTelemetryPropertyNames.COMPLETED to completed.toString(),
                        AdventureTelemetryPropertyNames.ELAPSED_SECONDS to seconds.toString(),
                        AdventureTelemetryPropertyNames.STEPS to steps.coerceAtLeast(0).toString(),
                        AdventureTelemetryPropertyNames.DEATHS_THIS_RUN to deathsThisRun.coerceAtLeast(0).toString()
                    )
                )
            }
        }
    }

    private inline fun dispatch(event: () -> AdventureTelemetryEvent) {
        try {
            sink.record(event())
        } catch (_: Exception) {
            // Optional telemetry must not interrupt gameplay.
        }
    }
}
