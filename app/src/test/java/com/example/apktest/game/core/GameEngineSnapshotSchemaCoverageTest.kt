package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
            val restoredValue = field.get(restored!!)
            assertEquals(
                "GameEngineSnapshot field '${field.name}' did not round-trip through toJson/fromJson. " +
                    "If you just added '${field.name}', remember to (a) serialize it in toJson, " +
                    "(b) deserialize it in fromJson, (c) bump SCHEMA_VERSION, " +
                    "(d) update GameEngine.snapshot()/restore(), and " +
                    "(e) update witnessSnapshot() below if the existing witness value matches the default. " +
                    "See docs/lessons-learned.md §1.",
                originalValue,
                restoredValue
            )
        }
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
