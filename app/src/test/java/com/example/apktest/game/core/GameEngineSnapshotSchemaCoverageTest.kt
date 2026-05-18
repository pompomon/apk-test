package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CI enforcement test that fails loudly if a field on [GameEngineSnapshot]
 * is added without also being persisted in [GameEngineSnapshot.toJson] /
 * [GameEngineSnapshot.fromJson]. See `docs/lessons-learned.md` §1.
 *
 * Strategy: build a "witness" snapshot whose every property holds a value
 * distinct from the data class' implicit default (e.g. non-zero numbers,
 * non-empty collections), round-trip it through `toJson` / `fromJson`, and
 * assert each declared field survives unchanged. If a new field is added
 * to the data class but missed in (de)serialization, `fromJson` will fall
 * back to the constructor default and the per-field assertion will fail
 * naming the offending field.
 *
 * Uses [java.lang.reflect] only — no `kotlin.reflect.full` dependency —
 * so the test runs with the default Android `kotlin-stdlib` test classpath
 * (no `kotlin-reflect` artifact required).
 */
class GameEngineSnapshotSchemaCoverageTest {

    @Test
    fun everyDeclaredFieldRoundTripsThroughJson() {
        val original = witnessSnapshot()
        val json = original.toJson()
        val restored = GameEngineSnapshot.fromJson(json)
        assertNotNull(
            "fromJson returned null for a witness snapshot built against the current schema. " +
                "Either the witness needs updating after a SCHEMA_VERSION bump, or fromJson " +
                "rejects state that toJson can emit.",
            restored
        )

        // A Kotlin data class compiles every primary-constructor parameter into
        // a private instance field with the same name. Walk those fields and
        // verify each round-trips. Skip synthetic / static fields (e.g.
        // `Companion`, `$stable`) which are not constructor parameters.
        val fields = GameEngineSnapshot::class.java.declaredFields
            .filter { f ->
                !java.lang.reflect.Modifier.isStatic(f.modifiers) && !f.isSynthetic
            }
        assertTrue(
            "Expected GameEngineSnapshot to expose declared instance fields for its " +
                "constructor parameters, but found none — has the data-class shape changed?",
            fields.isNotEmpty()
        )

        for (field in fields) {
            field.isAccessible = true
            val originalValue = field.get(original)

            // Guard against a witness that silently inherits a default value.
            // If a future contributor adds a field with a default (e.g.
            // `val newFlag: Boolean = false`) and forgets to (a) wire it into
            // toJson/fromJson and (b) extend witnessSnapshot(), then both
            // `originalValue` and `restoredValue` will be the same default —
            // and the round-trip assertion below would pass spuriously.
            // Refuse "trivial" witness values to force witnessSnapshot() to be
            // updated whenever a new field is introduced.
            val triviality = trivialityDescription(originalValue)
            assertNull(
                "GameEngineSnapshot field '${field.name}' has a trivial witness value " +
                    "($triviality) in witnessSnapshot(). This makes the round-trip " +
                    "assertion vacuous: if toJson/fromJson omit '${field.name}', the " +
                    "restored value would silently equal the default. Set " +
                    "'${field.name}' to a deliberately non-default value in " +
                    "witnessSnapshot(). See docs/lessons-learned.md §1.",
                triviality
            )

            val restoredValue = field.get(restored!!)
            assertEquals(
                "GameEngineSnapshot field '${field.name}' did not round-trip through toJson/fromJson. " +
                    "If you just added '${field.name}', remember to (a) serialize it in toJson, " +
                    "(b) deserialize it in fromJson, (c) bump SCHEMA_VERSION, " +
                    "(d) update GameEngine.snapshot()/restore(), and " +
                    "(e) update witnessSnapshot() below with a non-default witness value. " +
                    "See docs/lessons-learned.md §1.",
                originalValue,
                restoredValue
            )
        }
    }

    /**
     * Returns a short human-readable description of why [value] is a "trivial"
     * (default-equivalent) witness, or `null` when [value] is deliberately
     * non-default and therefore a meaningful witness. Trivial values are:
     * `null`, numeric zero, `false`, empty strings, and empty collections /
     * maps / arrays — i.e. anything that a forgotten `fromJson` branch would
     * also produce by accident.
     */
    private fun trivialityDescription(value: Any?): String? = when {
        value == null -> "null"
        value is Boolean && !value -> "false"
        value is Number && value.toDouble() == 0.0 -> "zero ($value)"
        value is CharSequence && value.isEmpty() -> "empty string"
        value is Collection<*> && value.isEmpty() -> "empty collection"
        value is Map<*, *> && value.isEmpty() -> "empty map"
        value is Array<*> && value.isEmpty() -> "empty array"
        else -> null
    }


    /**
     * Build a [GameEngineSnapshot] whose every field carries a value
     * distinguishable from the implicit data-class default. When you add a
     * new field, extend this builder with a non-default witness value.
     */
    private fun witnessSnapshot(): GameEngineSnapshot = GameEngineSnapshot(
        schemaVersion = GameEngineSnapshot.SCHEMA_VERSION,
        difficultyName = DifficultyPresets.MEDIUM.name,
        playerPolicy = PlayerPolicyType.BFS_EXIT,
        npcPolicy = NpcPolicyType.PREDICTIVE_CHASE,
        seed = 0x5EEDL,
        status = GameStatus.RUNNING,
        elapsedSeconds = 12.5f,
        steps = 7,
        player = GameEngineSnapshot.PlayerSnapshot(x = 3, y = 5, facing = Direction.NORTH),
        npcs = listOf(
            GameEngineSnapshot.NpcSnapshot(id = 1, x = 2, y = 4, facing = Direction.SOUTH),
            GameEngineSnapshot.NpcSnapshot(id = 2, x = 6, y = 8, facing = Direction.WEST)
        ),
        spawnedPowerUps = listOf(
            GameEngineSnapshot.SpawnedPowerUpSnapshot(
                type = PowerUpType.INVISIBILITY,
                x = 1,
                y = 1,
                remainingSeconds = 4.25f
            )
        ),
        activeEffects = listOf(
            GameEngineSnapshot.ActiveEffectSnapshot(
                type = PowerUpType.INVISIBILITY,
                remainingSeconds = 2.5f
            )
        ),
        npcInducedPlayerFreezeRemainingSeconds = 1.75f,
        manualQueue = listOf(Direction.EAST, Direction.SOUTH),
        manualOverrideRemainingSeconds = 3.0f,
        removedWalls = listOf(
            GameEngineSnapshot.RemovedWallSnapshot(x = 0, y = 0, direction = Direction.EAST)
        )
    )
}
