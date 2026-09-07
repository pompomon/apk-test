package com.example.apktest.game.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEngineRunPerkSnapshotTest {
    @Test
    fun neutralSnapshotsExplicitlyPersistNeutralEffectsAndNullMarker() {
        val e = GameEngine(DifficultyPresets.MEDIUM, 1234L)
        val snapshot = e.snapshot()
        assertEquals(8, snapshot.schemaVersion)
        assertEquals(RunPerkEffects(), snapshot.runPerkEffects)
        assertNull(snapshot.pendingConsumedRunPerk)
        val json = JSONObject(snapshot.toJson())
        assertEquals(0, json.getJSONObject("runPerkEffects").getInt("quickFeetStacks"))
        assertFalse(json.getJSONObject("runPerkEffects").getBoolean("secondWindAvailable"))
        assertTrue(json.has("pendingConsumedRunPerk"))
        assertTrue(json.isNull("pendingConsumedRunPerk"))
        assertEquals(snapshot, GameEngineSnapshot.fromJson(snapshot.toJson()))
    }

    @Test
    fun passiveEffectsRoundTripAndInstallBeforeRestartRewardsCountdown() {
        val e = GameEngine(DifficultyPresets.MEDIUM, 1234L)
        e.configureAdventureMaze(2, emptyList(), runPerkEffects = RunPerkEffects(3, 2, 1, true))
        e.restart(1234L)
        e.applyStartingPowerUp(PowerUpType.SPEED_UP)
        e.startCountdown()
        assertEquals(4.5f * 2.3f, e.hudState().playerSpeed, 0.0001f)
        e.update(2f)
        assertEquals(10f, e.snapshot().activeEffects.single().remainingSeconds!!, 0.0001f)
        val decoded = GameEngineSnapshot.fromJson(e.snapshot().toJson())!!
        val restored = GameEngine()
        restored.restore(decoded)
        assertEquals(e.runPerkEffects, restored.runPerkEffects)
        assertEquals(e.snapshot(), restored.snapshot())
        assertEquals(0f, restored.countdownRemainingSeconds, 0f)
    }

    @Test
    fun pendingConsumptionRoundTripsWithPulseAndRestoresBlockedWithoutCountdownOrRegrant() {
        val e = capturedEngine()
        val snapshot = e.snapshot()
        val json = JSONObject(snapshot.toJson())
        assertEquals("second_wind", json.getString("pendingConsumedRunPerk"))
        val decoded = GameEngineSnapshot.fromJson(json.toString())!!
        assertEquals(snapshot, decoded)
        val restored = GameEngine()
        restored.restore(decoded)
        restored.update(600f)
        restored.startCountdown()
        assertEquals(decoded, restored.snapshot())
        assertEquals(0f, restored.countdownRemainingSeconds, 0f)
        assertFalse(restored.runPerkEffects.secondWindAvailable)
        assertTrue(restored.acknowledgeRunPerkConsumption(decoded.seed, RunPerkId.SECOND_WIND))
        assertFalse(restored.acknowledgeRunPerkConsumption(decoded.seed, RunPerkId.SECOND_WIND))
        restored.restart(decoded.seed)
        assertFalse(restored.runPerkEffects.secondWindAvailable)
        assertNull(restored.pendingConsumedRunPerk)
        assertTrue(restored.activePowerUps.isEmpty())
    }

    @Test
    fun pendingPulseRemainsExactlyOneSecondAcrossFloatExponentBoundaries() {
        for (elapsed in listOf(127.1f, 511.1f, 2047.1f)) {
            val e = GameEngine(DifficultyPresets.MEDIUM, 1234L)
            e.configureAdventureMaze(2, emptyList(), runPerkEffects = RunPerkEffects(
                longerChargeStacks = 3, secondWindAvailable = true
            ))
            e.restore(e.snapshot().copy(
                elapsedSeconds = elapsed - 0.125f,
                spawnedPowerUps = emptyList()
            ))
            val target = e.maze.neighbors(e.player.position).first()
            e.npcs[0].position = target
            e.queueManualMove(requireNotNull(Direction.fromDelta(
                target.x - e.player.position.x, target.y - e.player.position.y
            )))
            e.update(0.125f)
            assertEquals(elapsed, e.elapsedSeconds, 0f)
            assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
            val rawPulse = e.activePowerUps.single { it.type == PowerUpType.FREEZE }
            assertTrue("The witness must exercise Float rounding", rawPulse.endsAtSeconds!! - elapsed != 1f)
            val snapshot = e.snapshot()
            assertEquals(1f, snapshot.activeEffects.single().remainingSeconds!!, 0f)
            assertTrue(snapshot.hasValidRunPerkConfiguration())
            val decoded = GameEngineSnapshot.fromJson(snapshot.toJson())!!
            assertEquals(snapshot, decoded)
            val restored = GameEngine()
            restored.restore(decoded)
            restored.update(100f)
            assertEquals(decoded, restored.snapshot())
            assertTrue(restored.acknowledgeRunPerkConsumption(decoded.seed, RunPerkId.SECOND_WIND))
            restored.update(1f)
            assertFalse(restored.isPlayerPowerUpTintActive(PowerUpType.FREEZE))
            assertFalse(restored.runPerkEffects.secondWindAvailable)
        }
    }

    @Test
    fun nestedEffectFieldsRequireExactIntegersAndBooleanWithinCaps() {
        val snapshot = GameEngine().snapshot()
        for ((field, cap) in listOf("quickFeetStacks" to 3, "longerChargeStacks" to 3, "pocketMagnetStacks" to 2)) {
            for (invalid in listOf(-1, cap + 1, 1.5, "1", true, JSONObject.NULL, Long.MAX_VALUE)) {
                val json = JSONObject(snapshot.toJson())
                json.getJSONObject("runPerkEffects").put(field, invalid)
                assertNull("$field=$invalid", GameEngineSnapshot.fromJson(json.toString()))
            }
            val missing = JSONObject(snapshot.toJson())
            missing.getJSONObject("runPerkEffects").remove(field)
            assertNull(GameEngineSnapshot.fromJson(missing.toString()))
        }
        for (invalid in listOf(0, 1, "true", JSONObject.NULL)) {
            val json = JSONObject(snapshot.toJson())
            json.getJSONObject("runPerkEffects").put("secondWindAvailable", invalid)
            assertNull(GameEngineSnapshot.fromJson(json.toString()))
        }
        for (field in listOf("runPerkEffects", "pendingConsumedRunPerk")) {
            val missing = JSONObject(snapshot.toJson())
            missing.remove(field)
            assertNull(GameEngineSnapshot.fromJson(missing.toString()))
        }
        val nullEffects = JSONObject(snapshot.toJson()).put("runPerkEffects", JSONObject.NULL)
        assertNull(GameEngineSnapshot.fromJson(nullEffects.toString()))
        val extra = JSONObject(snapshot.toJson())
        extra.getJSONObject("runPerkEffects").put("speedMultiplier", 99)
        assertNull(GameEngineSnapshot.fromJson(extra.toString()))
    }

    @Test
    fun pendingMarkerRejectsUnknownWrongTypeAndInconsistentConsumption() {
        val snapshot = capturedEngine().snapshot()
        for (invalid in listOf("SECOND_WIND", "quick_feet", "future_perk", 4, true)) {
            val json = JSONObject(snapshot.toJson()).put("pendingConsumedRunPerk", invalid)
            assertNull(GameEngineSnapshot.fromJson(json.toString()))
        }
        val invalidSnapshots = listOf(
            snapshot.copy(runPerkEffects = snapshot.runPerkEffects.copy(secondWindAvailable = true)),
            snapshot.copy(status = GameStatus.PAUSED),
            snapshot.copy(status = GameStatus.LOSE),
            snapshot.copy(activeEffects = emptyList()),
            snapshot.copy(activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 5f))),
            snapshot.copy(activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 1.0000076f))),
            snapshot.copy(activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 0.9999695f))),
            snapshot.copy(activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, null))),
            snapshot.copy(activeEffects = snapshot.activeEffects + snapshot.activeEffects),
            snapshot.copy(pendingConsumedRunPerk = RunPerkId.FIRST_SHIELD)
        )
        for (invalid in invalidSnapshots) {
            assertNull(GameEngineSnapshot.fromJson(invalid.toJson()))
            val untouched = GameEngine(DifficultyPresets.MEDIUM, 654L)
            val before = untouched.snapshot()
            try {
                untouched.restore(invalid)
                throw AssertionError("Invalid programmatic restore was accepted")
            } catch (_: IllegalArgumentException) {
                assertEquals(before, untouched.snapshot())
            }
        }
    }

    @Test
    fun oldSchemaIsRejectedInsteadOfSilentlyDroppingPerks() {
        for (invalid in listOf(7, 8.5, "8", true, JSONObject.NULL)) {
            val json = JSONObject(GameEngine().snapshot().toJson()).put("v", invalid)
            assertNull(GameEngineSnapshot.fromJson(json.toString()))
        }
    }

    @Test
    fun nestedEffectWitnessesAreNonVacuousForEveryField() {
        val a = RunPerkEffects(1, 2, 1, true)
        val b = RunPerkEffects(3, 1, 2, false)
        for (effects in listOf(a, b)) {
            val snapshot = GameEngine().snapshot().copy(runPerkEffects = effects)
            val restored = GameEngineSnapshot.fromJson(snapshot.toJson())
            assertNotNull(restored)
            assertEquals(effects, restored!!.runPerkEffects)
        }
        val fields = RunPerkEffects::class.java.declaredFields.filter {
            !it.isSynthetic && !java.lang.reflect.Modifier.isStatic(it.modifiers)
        }
        for (field in fields) {
            field.isAccessible = true
            assertTrue("Missing distinct nested witness: ${field.name}", field.get(a) != field.get(b))
        }
    }

    private fun capturedEngine(): GameEngine = GameEngine(DifficultyPresets.MEDIUM, 1234L).also { e ->
        e.configureAdventureMaze(2, emptyList(), runPerkEffects = RunPerkEffects(3, 3, 2, true))
        e.restore(e.snapshot().copy(spawnedPowerUps = emptyList()))
        val neighbor = e.maze.neighbors(e.player.position).first()
        e.npcs[0].position = neighbor
        e.queueManualMove(requireNotNull(Direction.fromDelta(
            neighbor.x - e.player.position.x, neighbor.y - e.player.position.y
        )))
        e.update(0.001f)
        assertEquals(RunPerkId.SECOND_WIND, e.pendingConsumedRunPerk)
    }
}
