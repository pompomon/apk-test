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
        assertTrue(names.all { it.startsWith("adventure_") })
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
        assertIllegalArgument {
            AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.RUN_STARTED,
                properties = mapOf(
                    AdventureTelemetryPropertyNames.DIFFICULTY to "medium",
                    AdventureTelemetryPropertyNames.MAZE_INDEX to "2"
                )
            )
        }
    }

    @Test
    fun event_rejectsUnsafeCatalogValuesAndBadEventPropertyPairs() {
        assertIllegalArgument {
            AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.REWARD_CHOSEN,
                properties = mapOf(
                    AdventureTelemetryPropertyNames.DIFFICULTY to "Medium",
                    AdventureTelemetryPropertyNames.MAZE_INDEX to "2",
                    AdventureTelemetryPropertyNames.REWARD_ID to "shield;drop table"
                )
            )
        }
        assertIllegalArgument {
            AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.RUN_STARTED,
                properties = mapOf(
                    AdventureTelemetryPropertyNames.DIFFICULTY to "Medium",
                    AdventureTelemetryPropertyNames.REWARD_ID to "shield"
                )
            )
        }
        assertIllegalArgument {
            AdventureTelemetryEvent(
                name = AdventureTelemetryEventNames.RUN_STARTED,
                properties = mapOf(
                    AdventureTelemetryPropertyNames.DIFFICULTY to "Medium",
                    AdventureTelemetryPropertyNames.COMPLETED to "yes"
                )
            )
        }
    }

    @Test
    fun routeEventApplied_acceptsDocumentedSchemaAndEmptyOfferLists() {
        val applied = AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.ROUTE_EVENT_APPLIED,
            properties = mapOf(
                AdventureTelemetryPropertyNames.NEXT_MAZE_INDEX to "3",
                AdventureTelemetryPropertyNames.CHOICE_ID to "quiet_corridor",
                AdventureTelemetryPropertyNames.NPC_COUNT_DELTA to "-1",
                AdventureTelemetryPropertyNames.REWARD_OPTION_DELTA to "-1",
                AdventureTelemetryPropertyNames.ELITE_REQUESTED to "false"
            )
        )
        val offers = AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.REWARD_OFFERED,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Medium",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "2",
                AdventureTelemetryPropertyNames.OFFERED_REWARD_IDS to "",
                AdventureTelemetryPropertyNames.OFFERED_CHOICE_IDS to "",
                AdventureTelemetryPropertyNames.OFFERED_CATEGORIES to ""
            )
        )

        assertEquals(
            mapOf(
                AdventureTelemetryPropertyNames.NEXT_MAZE_INDEX to "3",
                AdventureTelemetryPropertyNames.CHOICE_ID to "quiet_corridor",
                AdventureTelemetryPropertyNames.NPC_COUNT_DELTA to "-1",
                AdventureTelemetryPropertyNames.REWARD_OPTION_DELTA to "-1",
                AdventureTelemetryPropertyNames.ELITE_REQUESTED to "false"
            ),
            applied.properties
        )
        assertEquals("", offers.properties[AdventureTelemetryPropertyNames.OFFERED_REWARD_IDS])
    }

    @Test
    fun event_copiesPropertiesBeforeValidationAndExposure() {
        val source = mutableMapOf(
            AdventureTelemetryPropertyNames.DIFFICULTY to "Medium"
        )
        val event = AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.RUN_STARTED,
            properties = source
        )

        source[AdventureTelemetryPropertyNames.DIFFICULTY] = "Hard"
        source["run_seed"] = "123"

        assertEquals(
            mapOf(AdventureTelemetryPropertyNames.DIFFICULTY to "Medium"),
            event.properties
        )
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
