package com.example.apktest.telemetry

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AdventureTelemetryTest {

    @Test
    fun eventNames_areUniqueLowerSnakeCaseConstants() {
        val names = AdventureTelemetryEventNames.all
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.all { it.matches(LOWER_SNAKE_CASE) })
    }

    @Test
    fun propertyNames_areUniqueLowerSnakeCaseAndExcludeSensitiveFields() {
        val names = AdventureTelemetryPropertyNames.all
        assertEquals(names.size, names.distinct().size)
        assertTrue(names.all { it.matches(LOWER_SNAKE_CASE) })
        assertTrue(
            names.none {
                it in setOf(
                    "run_seed",
                    "run_seed_hash",
                    "position",
                    "coordinates",
                    "saved_state_json",
                    "user_id",
                    "device_id"
                )
            }
        )
    }

    @Test
    fun event_rejectsNamesAndPropertiesOutsideAllowlists() {
        assertIllegalArgument {
            AdventureTelemetryEvent(name = "adventure_unknown")
        }
        assertIllegalArgument {
            AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.RUN_STARTED,
                properties = mapOf("run_seed" to "123")
            )
        }
    }

    @Test
    fun noOpAndRecordingSinks_shareTheSameContract() {
        val event = AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.REWARD_CHOSEN,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Medium",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "2",
                AdventureTelemetryPropertyNames.REWARD_ID to "shield"
            )
        )

        NoOpAdventureTelemetrySink.record(event)
        val recordingSink = RecordingAdventureTelemetrySink()
        recordingSink.record(event)

        assertEquals(listOf(event), recordingSink.events)
    }

    private fun assertIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }

    private class RecordingAdventureTelemetrySink : AdventureTelemetrySink {
        val events = mutableListOf<AdventureTelemetryEvent>()

        override fun record(event: AdventureTelemetryEvent) {
            events += event
        }
    }

    companion object {
        private val LOWER_SNAKE_CASE = Regex("[a-z][a-z0-9]*(?:_[a-z0-9]+)*")
    }
}
