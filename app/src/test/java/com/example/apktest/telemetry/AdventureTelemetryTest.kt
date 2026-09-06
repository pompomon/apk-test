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
    fun eliteEvents_acceptDocumentedSchemas() {
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.ELITE_MODIFIER_SPAWNED,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Hard",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "4",
                AdventureTelemetryPropertyNames.MODIFIER_ID to "swift",
                AdventureTelemetryPropertyNames.NPC_COUNT to "3",
                AdventureTelemetryPropertyNames.ELITE_COUNT to "1",
                AdventureTelemetryPropertyNames.PLAYER_POLICY to "a_star"
            )
        )
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.ELITE_MODIFIER_OUTCOME,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Hard",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "4",
                AdventureTelemetryPropertyNames.MODIFIER_ID to "swift",
                AdventureTelemetryPropertyNames.COMPLETED to "false",
                AdventureTelemetryPropertyNames.ELAPSED_SECONDS to "42",
                AdventureTelemetryPropertyNames.STEPS to "18",
                AdventureTelemetryPropertyNames.DEATHS_THIS_RUN to "2"
            )
        )
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.DEATH_CONTEXT,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Hard",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "4",
                AdventureTelemetryPropertyNames.MODIFIER_ID to "swift",
                AdventureTelemetryPropertyNames.DEATH_CAUSE to "npc_collision",
                AdventureTelemetryPropertyNames.ACTIVE_POWER_UP to "invisibility"
            )
        )
    }

    @Test
    fun routeAndPerkChoices_acceptDocumentedSchemas() {
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.ROUTE_EVENT_CHOSEN,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Medium",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "2",
                AdventureTelemetryPropertyNames.CHOICE_ID to "quiet_corridor",
                AdventureTelemetryPropertyNames.CATEGORY to "safe",
                AdventureTelemetryPropertyNames.LIVES_REMAINING to "2",
                AdventureTelemetryPropertyNames.DEATHS_THIS_RUN to "1"
            )
        )
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.PERK_CHOSEN,
            properties = mapOf(
                AdventureTelemetryPropertyNames.PERK_ID to "steady_hands",
                AdventureTelemetryPropertyNames.STACK_AFTER_CHOICE to "2",
                AdventureTelemetryPropertyNames.OFFERED_PERK_IDS to "steady_hands,quick_step",
                AdventureTelemetryPropertyNames.LIVES_REMAINING to "2"
            )
        )
    }

    @Test
    fun perkOfferAndOutcome_acceptDocumentedSchemas() {
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.PERK_OFFER_SHOWN,
            properties = mapOf(
                AdventureTelemetryPropertyNames.DIFFICULTY to "Easy",
                AdventureTelemetryPropertyNames.MAZE_INDEX to "2",
                AdventureTelemetryPropertyNames.OFFERED_PERK_IDS to "steady_hands,quick_step",
                AdventureTelemetryPropertyNames.CURRENT_STACKS to "1"
            )
        )
        AdventureTelemetryEvent(
            name = AdventureTelemetryEventNames.PERK_RUN_OUTCOME,
            properties = mapOf(
                AdventureTelemetryPropertyNames.PERK_IDS to "steady_hands,quick_step",
                AdventureTelemetryPropertyNames.CURRENT_STACKS to "2",
                AdventureTelemetryPropertyNames.COMPLETED to "true",
                AdventureTelemetryPropertyNames.TOTAL_ELAPSED_SECONDS to "600",
                AdventureTelemetryPropertyNames.DEATHS_THIS_RUN to "1"
            )
        )
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

    @Test
    fun routeHelpers_recordOnlyDocumentedPropertiesWithStableIds() {
        val sink = RecordingAdventureTelemetrySink()
        val telemetry = AdventureRouteTelemetry(sink)

        telemetry.offered("Medium", 2, listOf("QUIET_CORRIDOR", "guarded_cache"), listOf("SAFE", "risk"))
        telemetry.chosen("Medium", 2, "QUIET_CORRIDOR", "SAFE", lives = 2, deaths = 1)
        telemetry.applied(3, "QUIET_CORRIDOR", npcCountDelta = -1, rewardOptionDelta = -1)
        telemetry.outcome("QUIET_CORRIDOR", won = true, elapsedSeconds = 42.9f, steps = 18, deathCountDelta = 1)

        assertEquals(
            listOf(
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ROUTE_EVENT_OFFERED,
                    mapOf(
                        "difficulty" to "Medium",
                        "maze_index" to "2",
                        "offered_choice_ids" to "quiet_corridor,guarded_cache",
                        "offered_categories" to "safe,risk"
                    )
                ),
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ROUTE_EVENT_CHOSEN,
                    mapOf(
                        "difficulty" to "Medium",
                        "maze_index" to "2",
                        "choice_id" to "quiet_corridor",
                        "category" to "safe",
                        "lives_remaining" to "2",
                        "deaths_this_run" to "1"
                    )
                ),
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ROUTE_EVENT_APPLIED,
                    mapOf(
                        "next_maze_index" to "3",
                        "choice_id" to "quiet_corridor",
                        "npc_count_delta" to "-1",
                        "reward_option_delta" to "-1",
                        "elite_requested" to "false"
                    )
                ),
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ROUTE_EVENT_OUTCOME,
                    mapOf(
                        "choice_id" to "quiet_corridor",
                        "next_maze_won" to "true",
                        "elapsed_seconds" to "42",
                        "steps" to "18",
                        "death_count_delta" to "1"
                    )
                )
            ),
            sink.events
        )
    }

    @Test
    fun routeHelpers_supportAllShippedDifficultiesAndSkipCustomDifficulties() {
        val sink = RecordingAdventureTelemetrySink()
        val telemetry = AdventureRouteTelemetry(sink)

        for (difficulty in listOf("Easy", "Medium", "Hard", "Custom", "medium", "")) {
            telemetry.offered(difficulty, 1, emptyList(), emptyList())
            telemetry.chosen(difficulty, 1, "quiet_corridor", "safe", lives = 1, deaths = 0)
        }

        assertEquals(6, sink.events.size)
        assertEquals(
            listOf("Easy", "Easy", "Medium", "Medium", "Hard", "Hard"),
            sink.events.map { it.properties.getValue("difficulty") }
        )
        assertTrue(
            sink.events.filter { it.name == AdventureTelemetryEventNames.ROUTE_EVENT_OFFERED }.all {
                it.properties.getValue("offered_choice_ids").isEmpty() &&
                    it.properties.getValue("offered_categories").isEmpty()
            }
        )
    }

    @Test
    fun routeHelpers_clampCountersButPreserveSignedEffectDeltas() {
        val sink = RecordingAdventureTelemetrySink()
        val telemetry = AdventureRouteTelemetry(sink)

        telemetry.offered("Easy", Int.MIN_VALUE, emptyList(), emptyList())
        telemetry.chosen("Easy", 0, "quiet_corridor", "safe", lives = Int.MIN_VALUE, deaths = -1)
        telemetry.applied(0, "quiet_corridor", npcCountDelta = Int.MIN_VALUE, rewardOptionDelta = Int.MAX_VALUE)
        telemetry.outcome("quiet_corridor", won = false, elapsedSeconds = -1f, steps = -1, deathCountDelta = -1)

        assertEquals("1", sink.events[0].properties.getValue("maze_index"))
        assertEquals("1", sink.events[1].properties.getValue("maze_index"))
        assertEquals("0", sink.events[1].properties.getValue("lives_remaining"))
        assertEquals("0", sink.events[1].properties.getValue("deaths_this_run"))
        assertEquals("1", sink.events[2].properties.getValue("next_maze_index"))
        assertEquals(Int.MIN_VALUE.toString(), sink.events[2].properties.getValue("npc_count_delta"))
        assertEquals(Int.MAX_VALUE.toString(), sink.events[2].properties.getValue("reward_option_delta"))
        assertEquals("false", sink.events[3].properties.getValue("next_maze_won"))
        assertEquals("0", sink.events[3].properties.getValue("elapsed_seconds"))
        assertEquals("0", sink.events[3].properties.getValue("steps"))
        assertEquals("0", sink.events[3].properties.getValue("death_count_delta"))
    }

    @Test
    fun routeOutcome_handlesNonFiniteOverflowAndFractionalElapsedSeconds() {
        val sink = RecordingAdventureTelemetrySink()
        val telemetry = AdventureRouteTelemetry(sink)
        val cases = listOf(
            Float.NaN to 0,
            Float.POSITIVE_INFINITY to 0,
            Float.NEGATIVE_INFINITY to 0,
            -Float.MAX_VALUE to 0,
            -0.1f to 0,
            0f to 0,
            Float.MIN_VALUE to 0,
            0.99f to 0,
            1.99f to 1,
            42.9f to 42,
            Int.MAX_VALUE.toFloat() to Int.MAX_VALUE,
            Float.MAX_VALUE to Int.MAX_VALUE
        )

        for ((seconds, expected) in cases) {
            telemetry.outcome("quiet_corridor", true, seconds, Int.MAX_VALUE, Int.MAX_VALUE)
            assertEquals(expected.toString(), sink.events.last().properties.getValue("elapsed_seconds"))
            assertEquals(Int.MAX_VALUE.toString(), sink.events.last().properties.getValue("steps"))
            assertEquals(Int.MAX_VALUE.toString(), sink.events.last().properties.getValue("death_count_delta"))
        }
        assertEquals(cases.size, sink.events.size)
    }

    @Test
    fun routeHelpers_isolateSinkFailuresAndContinueDispatching() {
        val attemptedNames = mutableListOf<String>()
        val telemetry = AdventureRouteTelemetry { event ->
            attemptedNames += event.name
            throw IllegalStateException("Sink unavailable")
        }

        telemetry.offered("Hard", 2, listOf("quiet_corridor"), listOf("safe"))
        telemetry.chosen("Hard", 2, "quiet_corridor", "safe", lives = 1, deaths = 0)
        telemetry.applied(3, "quiet_corridor", npcCountDelta = -1, rewardOptionDelta = 0)
        telemetry.outcome("quiet_corridor", won = false, elapsedSeconds = Float.NaN, steps = 0, deathCountDelta = 1)

        assertEquals(
            listOf(
                AdventureTelemetryEventNames.ROUTE_EVENT_OFFERED,
                AdventureTelemetryEventNames.ROUTE_EVENT_CHOSEN,
                AdventureTelemetryEventNames.ROUTE_EVENT_APPLIED,
                AdventureTelemetryEventNames.ROUTE_EVENT_OUTCOME
            ),
            attemptedNames
        )
    }

    @Test(expected = AssertionError::class)
    fun routeHelpers_doNotSwallowJvmErrors() {
        val telemetry = AdventureRouteTelemetry { throw AssertionError("Fatal sink failure") }

        telemetry.applied(2, "quiet_corridor", npcCountDelta = 0, rewardOptionDelta = 0)
    }

    @Test
    fun routeHelpers_dropInvalidIdsWithoutWeakeningStrictEventValidation() {
        val sink = RecordingAdventureTelemetrySink()
        val telemetry = AdventureRouteTelemetry(sink)

        telemetry.offered("Easy", 1, listOf("quiet_corridor,guarded_cache"), listOf("safe"))
        telemetry.chosen("Easy", 1, "quiet_corridor", "player-entered text", lives = 1, deaths = 0)
        telemetry.applied(2, "bad;id", npcCountDelta = 0, rewardOptionDelta = 0)
        telemetry.outcome("", won = true, elapsedSeconds = 1f, steps = 1, deathCountDelta = 0)

        assertTrue(sink.events.isEmpty())
        for ((property, value) in listOf("choice_id" to "QUIET_CORRIDOR", "elapsed_seconds" to "NaN")) {
            assertIllegalArgument {
                AdventureTelemetryEvent(
                    AdventureTelemetryEventNames.ROUTE_EVENT_OUTCOME,
                    mapOf(property to value)
                )
            }
        }
        telemetry.applied(2, "quiet_corridor", npcCountDelta = 0, rewardOptionDelta = 0)
        assertEquals(1, sink.events.size)
    }

    @Test
    fun routeHelpers_defaultToNoOpSink() {
        val telemetry = AdventureRouteTelemetry()

        telemetry.offered("Easy", 1, listOf("quiet_corridor"), listOf("safe"))
        telemetry.chosen("Easy", 1, "quiet_corridor", "safe", lives = 1, deaths = 0)
        telemetry.applied(2, "quiet_corridor", npcCountDelta = 0, rewardOptionDelta = 0)
        telemetry.outcome("quiet_corridor", won = true, elapsedSeconds = 1f, steps = 1, deathCountDelta = 0)
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
