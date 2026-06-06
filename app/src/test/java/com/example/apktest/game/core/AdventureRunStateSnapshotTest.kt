package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureRunStateSnapshotTest {

    private fun sampleState(): AdventureRunState = AdventureRunState(
        difficultyName = DifficultyPresets.MEDIUM.name,
        currentMazeIndex = 4,
        livesRemaining = 2,
        winStreakSinceLastBonus = 1,
        unlockedPlayerPolicies = mutableListOf(
            PlayerPolicyType.MANUAL,
            PlayerPolicyType.BFS_EXIT
        ),
        currentPlayerPolicy = PlayerPolicyType.BFS_EXIT,
        lastAutomatedPlayerPolicy = PlayerPolicyType.BFS_EXIT,
        automatedPolicyPromptShown = true,
        currentMazeSeed = 0xDEADBEEFL,
        currentMazeNpcPolicies = listOf(
            NpcPolicyType.DIRECT_CHASE,
            NpcPolicyType.PATROL_GUARD,
            NpcPolicyType.PREDICTIVE_CHASE
        ),
        currentMazeSnapshot = null,
        status = AdventureStatus.IN_PROGRESS
    )

    @Test
    fun toJsonFromJson_roundTripsAllScalarFields() {
        val snap = AdventureRunStateSnapshot.fromState(sampleState(), runSeed = 0x1234L)
        val restored = AdventureRunStateSnapshot.fromJson(snap.toJson())
        assertNotNull(restored)
        assertEquals(snap, restored)
    }

    @Test
    fun toJsonFromJson_roundTripsEmbeddedEngineSnapshot() {
        val engineSnapshot = GameEngineSnapshot(
            difficultyName = DifficultyPresets.MEDIUM.name,
            playerPolicy = PlayerPolicyType.BFS_EXIT,
            npcPolicy = NpcPolicyType.DIRECT_CHASE,
            seed = 7L,
            status = GameStatus.PAUSED,
            elapsedSeconds = 1.5f,
            steps = 3,
            player = GameEngineSnapshot.PlayerSnapshot(2, 2, Direction.EAST),
            npcs = listOf(GameEngineSnapshot.NpcSnapshot(0, 1, 1, Direction.WEST)),
            spawnedPowerUps = emptyList(),
            activeEffects = emptyList(),
            npcInducedPlayerFreezeRemainingSeconds = null,
            manualQueue = emptyList(),
            manualOverrideRemainingSeconds = 0f,
            npcPolicies = listOf(NpcPolicyType.DIRECT_CHASE)
        )
        val state = sampleState().apply { currentMazeSnapshot = engineSnapshot }
        val snap = AdventureRunStateSnapshot.fromState(state, runSeed = 0xCAFEL)
        val restored = AdventureRunStateSnapshot.fromJson(snap.toJson())
        assertNotNull(restored)
        assertNotNull(restored!!.currentMazeSnapshot)
        assertEquals(engineSnapshot.seed, restored.currentMazeSnapshot!!.seed)
        assertEquals(engineSnapshot.player, restored.currentMazeSnapshot!!.player)
    }

    @Test
    fun fromJson_returnsNullForWrongSchemaVersion() {
        val snap = AdventureRunStateSnapshot.fromState(sampleState(), runSeed = 1L)
        val tampered = snap.toJson().replace(
            "\"v\":${AdventureRunStateSnapshot.SCHEMA_VERSION}",
            "\"v\":${AdventureRunStateSnapshot.SCHEMA_VERSION + 99}"
        )
        assertNull(AdventureRunStateSnapshot.fromJson(tampered))
    }

    @Test
    fun fromJson_dropsUnknownEnumAndStillLoads() {
        val snap = AdventureRunStateSnapshot.fromState(sampleState(), runSeed = 1L)
        val tampered = snap.toJson().replace(
            "\"BFS_EXIT\"",
            "\"NO_SUCH_POLICY\""
        )
        val restored = AdventureRunStateSnapshot.fromJson(tampered)
        assertNotNull(restored)
        // Unknown enum silently dropped; MANUAL invariant preserved.
        assertEquals(listOf(PlayerPolicyType.MANUAL), restored!!.unlockedPlayerPolicies)
        // currentPolicy was BFS_EXIT (now unknown) → resets to MANUAL.
        assertEquals(PlayerPolicyType.MANUAL, restored.currentPlayerPolicy)
    }

    @Test
    fun fromJson_repairsMissingManualInUnlockedSet() {
        val invalid = AdventureRunStateSnapshot(
            runSeed = 1L,
            difficultyName = DifficultyPresets.MEDIUM.name,
            currentMazeIndex = 0,
            livesRemaining = 1,
            winStreakSinceLastBonus = 0,
            unlockedPlayerPolicies = listOf(PlayerPolicyType.BFS_EXIT),
            currentPlayerPolicy = PlayerPolicyType.BFS_EXIT,
            currentMazeSeed = null,
            currentMazeNpcPolicies = emptyList(),
            currentMazeSnapshot = null,
            status = AdventureStatus.IN_PROGRESS
        )
        val restored = AdventureRunStateSnapshot.fromJson(invalid.toJson())
        assertNotNull(restored)
        assertTrue(PlayerPolicyType.MANUAL in restored!!.unlockedPlayerPolicies)
        assertTrue(PlayerPolicyType.BFS_EXIT in restored.unlockedPlayerPolicies)
    }

    @Test
    fun fromJson_resetsCurrentPolicyToManualWhenNotInUnlocked() {
        val invalid = AdventureRunStateSnapshot(
            runSeed = 1L,
            difficultyName = DifficultyPresets.MEDIUM.name,
            currentMazeIndex = 0,
            livesRemaining = 1,
            winStreakSinceLastBonus = 0,
            unlockedPlayerPolicies = listOf(PlayerPolicyType.MANUAL),
            currentPlayerPolicy = PlayerPolicyType.BFS_EXIT,
            currentMazeSeed = null,
            currentMazeNpcPolicies = emptyList(),
            currentMazeSnapshot = null,
            status = AdventureStatus.IN_PROGRESS
        )
        val restored = AdventureRunStateSnapshot.fromJson(invalid.toJson())
        assertNotNull(restored)
        assertEquals(PlayerPolicyType.MANUAL, restored!!.currentPlayerPolicy)
    }

    @Test
    fun toJsonFromJson_roundTripsLastAutomatedPolicy() {
        val state = sampleState().apply {
            currentPlayerPolicy = PlayerPolicyType.MANUAL
            lastAutomatedPlayerPolicy = PlayerPolicyType.BFS_EXIT
        }
        val snap = AdventureRunStateSnapshot.fromState(state, runSeed = 2L)
        val restored = AdventureRunStateSnapshot.fromJson(snap.toJson())
        assertNotNull(restored)
        assertEquals(PlayerPolicyType.MANUAL, restored!!.currentPlayerPolicy)
        assertEquals(PlayerPolicyType.BFS_EXIT, restored.lastAutomatedPlayerPolicy)
        assertEquals(PlayerPolicyType.BFS_EXIT, restored.toState().lastAutomatedPlayerPolicy)
    }

    @Test
    fun toJsonFromJson_roundTripsAutomatedPromptShown() {
        val state = sampleState().apply { automatedPolicyPromptShown = true }
        val snap = AdventureRunStateSnapshot.fromState(state, runSeed = 3L)
        val restored = AdventureRunStateSnapshot.fromJson(snap.toJson())
        assertNotNull(restored)
        assertTrue(restored!!.automatedPolicyPromptShown)
        assertTrue(restored.toState().automatedPolicyPromptShown)
    }

    @Test
    fun fromJson_dropsLastAutomatedPolicyWhenNotUnlocked() {
        val invalid = AdventureRunStateSnapshot(
            runSeed = 1L,
            difficultyName = DifficultyPresets.MEDIUM.name,
            currentMazeIndex = 0,
            livesRemaining = 1,
            winStreakSinceLastBonus = 0,
            unlockedPlayerPolicies = listOf(PlayerPolicyType.MANUAL),
            currentPlayerPolicy = PlayerPolicyType.MANUAL,
            lastAutomatedPlayerPolicy = PlayerPolicyType.BFS_EXIT,
            currentMazeSeed = null,
            currentMazeNpcPolicies = emptyList(),
            currentMazeSnapshot = null,
            status = AdventureStatus.IN_PROGRESS
        )
        val restored = AdventureRunStateSnapshot.fromJson(invalid.toJson())
        assertNotNull(restored)
        assertNull(restored!!.lastAutomatedPlayerPolicy)
    }

    @Test
    fun fromJson_returnsNullForNegativeLives() {
        val invalid = AdventureRunStateSnapshot(
            runSeed = 1L,
            difficultyName = DifficultyPresets.MEDIUM.name,
            currentMazeIndex = 0,
            livesRemaining = -1,
            winStreakSinceLastBonus = 0,
            unlockedPlayerPolicies = listOf(PlayerPolicyType.MANUAL),
            currentPlayerPolicy = PlayerPolicyType.MANUAL,
            currentMazeSeed = null,
            currentMazeNpcPolicies = emptyList(),
            currentMazeSnapshot = null,
            status = AdventureStatus.IN_PROGRESS
        )
        assertNull(AdventureRunStateSnapshot.fromJson(invalid.toJson()))
    }

    @Test
    fun fromJson_returnsNullForGarbage() {
        assertNull(AdventureRunStateSnapshot.fromJson("{not json"))
        assertNull(AdventureRunStateSnapshot.fromJson("{}"))
    }

    @Test
    fun toJsonFromJson_roundTripsPendingStartingPowerUp() {
        val state = sampleState().apply { pendingStartingPowerUp = PowerUpType.TELEPORT }
        val snap = AdventureRunStateSnapshot.fromState(state, runSeed = 7L)
        val restored = AdventureRunStateSnapshot.fromJson(snap.toJson())!!
        assertEquals(PowerUpType.TELEPORT, restored.pendingStartingPowerUp)
    }

    @Test
    fun fromJson_treatsAbsentPendingPowerUpAsNull() {
        val snap = AdventureRunStateSnapshot.fromState(sampleState(), runSeed = 1L)
        val restored = AdventureRunStateSnapshot.fromJson(snap.toJson())!!
        assertNull(restored.pendingStartingPowerUp)
    }

    @Test
    fun controllerCanBeRehydratedFromSnapshotState() {
        val state = sampleState()
        val snap = AdventureRunStateSnapshot.fromState(state, runSeed = 0xBEEFL)
        val restoredState = AdventureRunStateSnapshot.fromJson(snap.toJson())!!.toState()
        val controller = AdventureRunController(
            AdventureConfig.forDifficultyName(restoredState.difficultyName),
            initialState = restoredState,
            runSeed = snap.runSeed
        )
        assertEquals(state.currentMazeIndex, controller.state.currentMazeIndex)
        assertEquals(state.livesRemaining, controller.state.livesRemaining)
        // prepareCurrentMaze returns the locked seed from state, not a new one.
        val spec = controller.prepareCurrentMaze()!!
        assertEquals(state.currentMazeSeed, spec.seed)
        assertEquals(state.currentMazeNpcPolicies, spec.npcPolicies)
    }
}
