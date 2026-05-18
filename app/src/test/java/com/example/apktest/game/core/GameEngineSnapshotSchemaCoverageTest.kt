package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CI enforcement test that fails loudly if a field on [GameEngineSnapshot]
 * is added without also being persisted in [GameEngineSnapshot.toJson] /
 * [GameEngineSnapshot.fromJson]. See `docs/lessons-learned.md` §1.
 *
 * Strategy: build two "witness" snapshots whose every property holds a
 * value distinct from the *other* witness (and distinct from the data
 * class' implicit default). Round-trip the first one through `toJson` /
 * `fromJson`, and assert each declared field survives unchanged. To
 * catch the "new field added with a default value, forgotten in both
 * `witnessSnapshot()` and `fromJson`" trap, we also assert each
 * reflected field has *different* values across the two witnesses —
 * this fails whenever a field is left unset in either witness (both
 * would silently take the same default), regardless of whether the
 * default is `null`, `0`, `false`, an enum value, a non-empty default,
 * or anything else.
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

        for (field in reflectedFields()) {
            field.isAccessible = true
            val originalValue = field.get(original)
            val restoredValue = field.get(restored!!)
            assertEquals(
                "GameEngineSnapshot field '${field.name}' did not round-trip through toJson/fromJson. " +
                    "If you just added '${field.name}', remember to (a) serialize it in toJson, " +
                    "(b) deserialize it in fromJson, (c) bump SCHEMA_VERSION, " +
                    "(d) update GameEngine.snapshot()/restore(), and " +
                    "(e) update witnessSnapshot()/alternateWitnessSnapshot() below with " +
                    "non-default witness values. See docs/lessons-learned.md §1.",
                originalValue,
                restoredValue
            )
        }
    }

    /**
     * Defends against a future contributor adding a constructor field with
     * a default value and forgetting to wire it into [witnessSnapshot] and
     * [alternateWitnessSnapshot]. If both witnesses inherit the same
     * default for a field, the round-trip test above silently passes
     * even when `toJson`/`fromJson` ignore the field — because the
     * restored default still equals the witness default. By asserting
     * each reflected field differs across two independently-constructed
     * witnesses, this test forces every new field to be deliberately set
     * in both, which in turn forces a careful audit of (de)serialization.
     */
    @Test
    fun everyDeclaredFieldHasDistinctValuesAcrossWitnesses() {
        val a = witnessSnapshot()
        val b = alternateWitnessSnapshot()
        for (field in reflectedFields()) {
            field.isAccessible = true
            val va = field.get(a)
            val vb = field.get(b)
            assertNotEquals(
                "GameEngineSnapshot field '${field.name}' has the same value in " +
                    "witnessSnapshot() and alternateWitnessSnapshot(). This makes the " +
                    "round-trip assertion vacuous: if toJson/fromJson omit '${field.name}', " +
                    "the restored value would silently equal the (shared) default. Set " +
                    "'${field.name}' to deliberately *different* values in the two " +
                    "witness builders. See docs/lessons-learned.md §1.",
                va,
                vb
            )
        }
    }

    /**
     * A Kotlin data class compiles every primary-constructor parameter
     * into a private instance field with the same name. Walk those
     * fields, skipping synthetic / static fields (e.g. `Companion`,
     * `$stable`) which are not constructor parameters.
     */
    private fun reflectedFields(): List<java.lang.reflect.Field> {
        val fields = GameEngineSnapshot::class.java.declaredFields
            .filter { f ->
                !java.lang.reflect.Modifier.isStatic(f.modifiers) && !f.isSynthetic
            }
        assertTrue(
            "Expected GameEngineSnapshot to expose declared instance fields for its " +
                "constructor parameters, but found none — has the data-class shape changed?",
            fields.isNotEmpty()
        )
        return fields
    }

    /**
     * Build a [GameEngineSnapshot] whose every field carries a value
     * distinguishable from the implicit data-class default *and* from
     * [alternateWitnessSnapshot]. When you add a new field, extend
     * both this builder and [alternateWitnessSnapshot] with values
     * that differ from each other.
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

    /**
     * Companion to [witnessSnapshot] — every field must hold a value
     * that differs from the corresponding field in [witnessSnapshot].
     * The schema-version is held constant (it's not user-state and
     * `fromJson` rejects mismatched versions outright).
     */
    private fun alternateWitnessSnapshot(): GameEngineSnapshot = GameEngineSnapshot(
        schemaVersion = GameEngineSnapshot.SCHEMA_VERSION,
        difficultyName = DifficultyPresets.EASY.name,
        playerPolicy = PlayerPolicyType.ASTAR_EXIT,
        npcPolicy = NpcPolicyType.PATROL_GUARD,
        seed = 0xC0FFEEL,
        status = GameStatus.PAUSED,
        elapsedSeconds = 99.0f,
        steps = 42,
        player = GameEngineSnapshot.PlayerSnapshot(x = 4, y = 6, facing = Direction.SOUTH),
        npcs = listOf(
            GameEngineSnapshot.NpcSnapshot(id = 3, x = 5, y = 7, facing = Direction.EAST)
        ),
        spawnedPowerUps = listOf(
            GameEngineSnapshot.SpawnedPowerUpSnapshot(
                type = PowerUpType.FREEZE,
                x = 2,
                y = 2,
                remainingSeconds = 8.0f
            ),
            GameEngineSnapshot.SpawnedPowerUpSnapshot(
                type = PowerUpType.BLAST,
                x = 3,
                y = 3,
                remainingSeconds = null
            )
        ),
        activeEffects = listOf(
            GameEngineSnapshot.ActiveEffectSnapshot(
                type = PowerUpType.FREEZE,
                remainingSeconds = 5.0f
            )
        ),
        npcInducedPlayerFreezeRemainingSeconds = null,
        manualQueue = listOf(Direction.NORTH),
        manualOverrideRemainingSeconds = 6.0f,
        removedWalls = listOf(
            GameEngineSnapshot.RemovedWallSnapshot(x = 1, y = 1, direction = Direction.SOUTH),
            GameEngineSnapshot.RemovedWallSnapshot(x = 2, y = 0, direction = Direction.WEST)
        )
    )
}
