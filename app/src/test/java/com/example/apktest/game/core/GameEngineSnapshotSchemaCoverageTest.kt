package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.reflect.KProperty1
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.full.primaryConstructor

/**
 * CI enforcement test that fails loudly if a field on [GameEngineSnapshot]
 * is added without also being persisted in [GameEngineSnapshot.toJson] /
 * [GameEngineSnapshot.fromJson]. See `docs/lessons-learned.md` §1.
 *
 * Strategy: build a "witness" snapshot whose every property holds a value
 * distinct from the data class' implicit default (e.g. non-zero numbers,
 * non-empty collections), round-trip it through `toJson` / `fromJson`, and
 * assert each primary-constructor property survives unchanged. If a new
 * field is introduced and missed in (de)serialization, `fromJson` will
 * fall back to the constructor default and the per-property assertion
 * will fail naming the missing property.
 */
class GameEngineSnapshotSchemaCoverageTest {

    @Test
    fun everyConstructorPropertyRoundTripsThroughJson() {
        val original = witnessSnapshot()
        val json = original.toJson()
        val restored = GameEngineSnapshot.fromJson(json)
        assertNotNull(
            "fromJson returned null for a witness snapshot built against the current schema. " +
                "Either the witness needs updating after a SCHEMA_VERSION bump, or fromJson " +
                "rejects state that toJson can emit.",
            restored
        )

        val ctor = GameEngineSnapshot::class.primaryConstructor
            ?: error("GameEngineSnapshot must remain a data class with a primary constructor")
        val propsByName = GameEngineSnapshot::class.declaredMemberProperties
            .associateBy { it.name }

        // Every primary-constructor parameter must map to a declared property
        // (true for a Kotlin data class) and round-trip through JSON.
        for (param in ctor.parameters) {
            val name = param.name ?: continue
            @Suppress("UNCHECKED_CAST")
            val prop = propsByName[name] as? KProperty1<GameEngineSnapshot, Any?>
                ?: error(
                    "GameEngineSnapshot primary-constructor parameter '$name' has no matching " +
                        "declared property. Is the data-class shape still intact?"
                )
            val originalValue = prop.get(original)
            val restoredValue = prop.get(restored!!)
            assertEquals(
                "GameEngineSnapshot property '$name' did not round-trip through toJson/fromJson. " +
                    "If you just added '$name', remember to (a) serialize it in toJson, " +
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
