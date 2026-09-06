package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GameEnginePickupLifetimeTest {
    private val seed = 4321L
    private val preset = DifficultyPresets.MEDIUM.copy(
        name = "PickupLifetime",
        npcCount = 0,
        adventurerCount = 0,
        initialPowerUpTypes = listOf(PowerUpType.INVISIBILITY, PowerUpType.SPEED_UP, PowerUpType.SHIELD),
        powerUpExpirationStaggerSeconds = 5f,
        powerUpRespawnIntervalSeconds = null
    )

    @Test
    fun overrideAppliesToInitialPickupsAndPreservesStaggeredExpiration() {
        val engine = configuredEngine()
        val initial = engine.spawnedPowerUps.sortedBy { it.expiresAtSeconds }

        assertEquals(listOf(10f, 15f, 20f), initial.map { it.expiresAtSeconds })
        advance(engine, 9.75f)
        assertEquals(initial.toSet(), engine.spawnedPowerUps.toSet())
        advance(engine, 0.25f)
        assertEquals(initial.drop(1).toSet(), engine.spawnedPowerUps.toSet())
        advance(engine, 5f)
        assertEquals(listOf(initial.last()), engine.spawnedPowerUps)
        advance(engine, 5f)
        assertTrue(engine.spawnedPowerUps.isEmpty())
    }

    @Test
    fun respawnsUseOverrideWithoutInitialSpawnStagger() {
        val engine = configuredEngine(preset.copy(powerUpRespawnIntervalSeconds = 25f))

        advance(engine, 24.75f)
        assertTrue(engine.spawnedPowerUps.isEmpty())
        advance(engine, 0.25f)
        val respawn = engine.spawnedPowerUps.single()
        assertEquals(25f, respawn.spawnedAtSeconds, 0f)
        assertEquals(35f, requireNotNull(respawn.expiresAtSeconds), 0f)
        advance(engine, 9.75f)
        assertEquals(listOf(respawn), engine.spawnedPowerUps)
        advance(engine, 0.25f)
        assertTrue(engine.spawnedPowerUps.isEmpty())
    }

    @Test
    fun finiteOverrideExpiresPickupsEvenWhenPresetLifetimeIsInfinite() {
        val engine = configuredEngine(preset.copy(powerUpPickupLifetimeSeconds = 0f))

        advance(engine, 20f)

        assertTrue(engine.spawnedPowerUps.isEmpty())
        assertEquals(10f, requireNotNull(engine.powerUpPickupLifetimeOverrideSeconds), 0f)
    }

    @Test
    fun nullOverridePreservesInfiniteEasyInitialPickupsAndRespawns() {
        val easy = DifficultyPresets.EASY.copy(npcCount = 0, adventurerCount = 0)
        val classic = GameEngine(easy, seed)
        val adventure = GameEngine(easy, seed)
        adventure.configureAdventureMaze(0, emptyList())
        adventure.restart(seed)

        assertNull(classic.powerUpPickupLifetimeOverrideSeconds)
        assertNull(adventure.powerUpPickupLifetimeOverrideSeconds)
        assertEquals(classic.spawnedPowerUps, adventure.spawnedPowerUps)
        val initial = adventure.spawnedPowerUps
        advance(adventure, 60f)
        assertTrue(adventure.spawnedPowerUps.containsAll(initial))
        assertTrue(adventure.spawnedPowerUps.size > initial.size)
        assertTrue(adventure.spawnedPowerUps.all { it.expiresAtSeconds == null })
    }

    @Test
    fun defaultConfigurationMatchesClassicFinitePickupLifetimes() {
        val classic = GameEngine(preset, seed)
        val adventure = GameEngine(preset, seed)
        adventure.configureAdventureMaze(0, emptyList())
        adventure.restart(seed)

        assertNull(adventure.powerUpPickupLifetimeOverrideSeconds)
        assertEquals(classic.spawnedPowerUps, adventure.spawnedPowerUps)
        assertEquals(
            listOf(45f, 50f, 55f),
            adventure.spawnedPowerUps.mapNotNull { it.expiresAtSeconds }.sorted()
        )
    }

    @Test
    fun pickupOverrideDoesNotChangeCollectedEffectDuration() {
        val engine = configuredEngine(
            preset.copy(initialPowerUpTypes = listOf(PowerUpType.SPEED_UP)),
            lifetime = 1f
        )
        val pickup = engine.spawnedPowerUps.single()
        val direction = Direction.entries.first {
            val from = pickup.position.moved(it.opposite())
            engine.maze.inBounds(from) && engine.maze.canMove(from, it)
        }
        engine.player.position = pickup.position.moved(direction.opposite())
        engine.queueManualMove(direction)
        engine.update(0.25f)

        val effect = engine.activePowerUps.single()
        assertEquals(PowerUpType.SPEED_UP, effect.type)
        assertEquals(
            PowerUpType.SPEED_UP.metadata.defaultDurationSeconds,
            requireNotNull(effect.endsAtSeconds) - effect.startedAtSeconds,
            0f
        )
        advance(engine, 1f)
        assertTrue(engine.isPlayerPowerUpTintActive(PowerUpType.SPEED_UP))
        advance(engine, 9f)
        assertFalse(engine.isPlayerPowerUpTintActive(PowerUpType.SPEED_UP))
    }

    @Test
    fun restartRetainsLifetimeOverrideAndResetsPickupTimers() {
        val engine = configuredEngine()
        val initial = engine.spawnedPowerUps
        advance(engine, 15f)
        engine.applyStartingPowerUp(PowerUpType.SPEED_UP)

        engine.restart(seed)

        assertEquals(10f, requireNotNull(engine.powerUpPickupLifetimeOverrideSeconds), 0f)
        assertEquals(0f, engine.elapsedSeconds, 0f)
        assertEquals(initial, engine.spawnedPowerUps)
        assertTrue(engine.activePowerUps.isEmpty())
    }

    @Test
    fun applyingDifficultyClearsLifetimeOverrideBeforeRestart() {
        val engine = configuredEngine()

        engine.applyDifficulty(preset)
        assertNull(engine.powerUpPickupLifetimeOverrideSeconds)
        engine.restart(seed)

        assertEquals(GameEngine(preset, seed).spawnedPowerUps, engine.spawnedPowerUps)
    }

    @Test
    fun clearingAdventureConfigRestoresInfinitePresetLifetime() {
        val infinite = preset.copy(powerUpPickupLifetimeSeconds = 0f)
        val engine = configuredEngine(infinite)

        engine.clearAdventureMazeConfig()
        engine.restart(seed)

        assertNull(engine.powerUpPickupLifetimeOverrideSeconds)
        assertTrue(engine.spawnedPowerUps.all { it.expiresAtSeconds == null })
    }

    @Test
    fun configuringNextMazeWithoutOverrideRestoresPresetLifetime() {
        val engine = configuredEngine()

        engine.configureAdventureMaze(0, emptyList())
        engine.restart(seed)

        assertNull(engine.powerUpPickupLifetimeOverrideSeconds)
        assertEquals(GameEngine(preset, seed).spawnedPowerUps, engine.spawnedPowerUps)
    }

    @Test
    fun changingNpcStrategyDoesNotDiscardPickupLifetimeOverride() {
        val engine = configuredEngine()

        engine.setNpcPolicy(NpcPolicyType.PATROL_GUARD)
        engine.restart(seed)

        assertEquals(10f, requireNotNull(engine.powerUpPickupLifetimeOverrideSeconds), 0f)
        assertEquals(
            listOf(10f, 15f, 20f),
            engine.spawnedPowerUps.mapNotNull { it.expiresAtSeconds }.sorted()
        )
    }

    @Test
    fun invalidOverridesAreRejectedWithoutChangingConfiguration() {
        val engine = configuredEngine()
        val before = engine.snapshot()
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                engine.configureAdventureMaze(3, listOf(NpcPolicyType.PATROL_GUARD), invalid)
            }
            assertEquals(before, engine.snapshot())
        }
    }

    @Test
    fun restoredOverrideGovernsExpirationRespawnsAndRestartWithoutReapplyingStartingEffect() {
        val original = GameEngine(DifficultyPresets.MEDIUM, seed)
        original.configureAdventureMaze(0, emptyList(), pickupLifetimeSeconds = 35f)
        original.restart(seed)
        original.adventurers.clear()
        original.applyStartingPowerUp(PowerUpType.SPEED_UP)
        advance(original, 3f)
        val snapshot = requireNotNull(GameEngineSnapshot.fromJson(original.snapshot().toJson()))
        val restored = GameEngine(DifficultyPresets.EASY, seed + 1)

        restored.restore(snapshot)

        assertEquals(snapshot, restored.snapshot())
        assertEquals(35f, requireNotNull(restored.powerUpPickupLifetimeOverrideSeconds), 0f)
        assertEquals(7f, requireNotNull(restored.snapshot().activeEffects.single().remainingSeconds), 0f)
        advance(restored, 7f)
        assertTrue(restored.activePowerUps.isEmpty())
        advance(restored, 13f)
        val respawn = restored.spawnedPowerUps.single { it.spawnedAtSeconds == 23f }
        assertEquals(58f, requireNotNull(respawn.expiresAtSeconds), 0f)
        val firstPickup = snapshot.spawnedPowerUps.single { it.remainingSeconds == 32f }
        advance(restored, 12f)
        assertFalse(restored.spawnedPowerUps.any { it.position == GridPos(firstPickup.x, firstPickup.y) })

        restored.restart(seed)

        assertEquals(35f, requireNotNull(restored.powerUpPickupLifetimeOverrideSeconds), 0f)
        assertEquals(35f, restored.spawnedPowerUps.mapNotNull { it.expiresAtSeconds }.min(), 0f)
        assertTrue(restored.activePowerUps.isEmpty())
    }

    @Test
    fun restoringNullOverrideClearsPreviousMazeOverride() {
        val original = GameEngine(DifficultyPresets.EASY, seed)
        val restored = configuredEngine()

        restored.restore(original.snapshot())

        assertNull(restored.powerUpPickupLifetimeOverrideSeconds)
        assertEquals(original.spawnedPowerUps, restored.spawnedPowerUps)
    }

    @Test
    fun restoreRejectsInvalidOverrideBeforeInstallingAnySnapshotState() {
        val engine = configuredEngine()
        val before = engine.snapshot()
        val other = GameEngine(DifficultyPresets.EASY, seed + 1).snapshot()
        for (invalid in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                engine.restore(other.copy(powerUpPickupLifetimeOverrideSeconds = invalid))
            }
            assertEquals(before, engine.snapshot())
        }
    }

    private fun configuredEngine(
        difficulty: DifficultyPreset = preset,
        lifetime: Float = 10f
    ): GameEngine = GameEngine(difficulty, seed).apply {
        configureAdventureMaze(0, emptyList(), pickupLifetimeSeconds = lifetime)
        restart(seed)
    }

    private fun advance(engine: GameEngine, seconds: Float) {
        repeat((seconds * 4).toInt()) { engine.update(0.25f) }
        assertEquals(GameStatus.RUNNING, engine.status)
    }
}
