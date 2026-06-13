package com.example.apktest.game.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureRunControllerTest {

    private fun easyController(runSeed: Long = 42L) =
        AdventureRunController(AdventureConfig.forDifficulty(DifficultyPresets.EASY), runSeed = runSeed)

    private fun hardController(runSeed: Long = 42L) =
        AdventureRunController(AdventureConfig.forDifficulty(DifficultyPresets.HARD), runSeed = runSeed)

    @Test
    fun startsWithCorrectLivesAndOnlyManualUnlocked() {
        val c = easyController()
        assertEquals(5, c.state.livesRemaining)
        assertEquals(listOf(PlayerPolicyType.MANUAL), c.state.unlockedPlayerPolicies.toList())
        assertEquals(PlayerPolicyType.MANUAL, c.state.currentPlayerPolicy)
        assertEquals(AdventureStatus.IN_PROGRESS, c.state.status)
    }

    @Test
    fun prepareCurrentMaze_locksSeedAndPoliciesIdempotently() {
        val c = easyController()
        val s1 = c.prepareCurrentMaze()!!
        val s2 = c.prepareCurrentMaze()!!
        assertEquals(s1.seed, s2.seed)
        assertEquals(s1.npcPolicies, s2.npcPolicies)
        assertEquals(1, s1.npcCount) // Easy maze 1 → base 1 + ramp 0 = 1 (last-maze bonus only at maze 5)
        assertEquals(1, s1.npcPolicies.size)
    }

    @Test
    fun deathReplaysSameMazeWithSameSeedAndPolicies() {
        val c = easyController()
        val before = c.prepareCurrentMaze()!!
        val death = c.onPlayerDied()
        assertEquals(4, death.livesRemaining)
        assertFalse(death.runOver)
        val after = c.prepareCurrentMaze()!!
        assertEquals(before.seed, after.seed)
        assertEquals(before.npcPolicies, after.npcPolicies)
        assertEquals(before.npcCount, after.npcCount)
        assertEquals(0, c.state.currentMazeIndex) // index unchanged on death
    }

    @Test
    fun deathResetsWinStreak() {
        val c = easyController()
        c.prepareCurrentMaze()
        c.onMazeWon() // streak=1
        c.prepareCurrentMaze()
        c.onMazeWon() // streak=2
        assertEquals(2, c.state.winStreakSinceLastBonus)
        c.prepareCurrentMaze()
        c.onPlayerDied()
        assertEquals(0, c.state.winStreakSinceLastBonus)
    }

    @Test
    fun threeConsecutiveWinsGrantsBonusLife() {
        val c = easyController()
        val startLives = c.state.livesRemaining
        repeat(2) {
            c.prepareCurrentMaze()
            val w = c.onMazeWon()
            assertFalse(w.bonusLifeAwarded)
        }
        c.prepareCurrentMaze()
        val third = c.onMazeWon()
        assertTrue(third.bonusLifeAwarded)
        assertEquals(startLives + 1, c.state.livesRemaining)
        assertEquals(0, c.state.winStreakSinceLastBonus) // resets after bonus
    }

    @Test
    fun winAdvancesMazeIndexAndClearsLockedFields() {
        val c = easyController()
        c.prepareCurrentMaze()
        val seedBefore = c.state.currentMazeSeed
        assertNotNull(seedBefore)
        c.onMazeWon()
        assertEquals(1, c.state.currentMazeIndex)
        assertNull(c.state.currentMazeSeed)
        assertTrue(c.state.currentMazeNpcPolicies.isEmpty())
    }

    @Test
    fun nextMazeUsesDifferentSeedThanPrevious() {
        val c = easyController()
        val first = c.prepareCurrentMaze()!!
        c.onMazeWon()
        val second = c.prepareCurrentMaze()!!
        assertNotEquals(first.seed, second.seed)
    }

    @Test
    fun winningAllMazesTransitionsToWonStatus() {
        val c = hardController() // 1 life, 9 mazes
        repeat(9) {
            c.prepareCurrentMaze()
            c.onMazeWon()
        }
        assertEquals(AdventureStatus.WON, c.state.status)
        assertEquals(9, c.state.currentMazeIndex)
        // prepareCurrentMaze returns null when terminal
        assertNull(c.prepareCurrentMaze())
    }

    @Test
    fun runEndsOnLastLifeLoss() {
        val c = hardController() // 1 life
        c.prepareCurrentMaze()
        val outcome = c.onPlayerDied()
        assertTrue(outcome.runOver)
        assertEquals(0, c.state.livesRemaining)
        assertEquals(AdventureStatus.LOST, c.state.status)
        assertNull(c.prepareCurrentMaze())
    }

    @Test
    fun livesNeverGoNegative() {
        val c = hardController()
        c.prepareCurrentMaze()
        c.onPlayerDied()
        assertEquals(0, c.state.livesRemaining)
    }

    @Test
    fun applyPolicyUnlock_addsPolicyOncePool() {
        val c = easyController()
        assertTrue(c.applyPolicyUnlock(PlayerPolicyType.BFS_EXIT))
        assertTrue(PlayerPolicyType.BFS_EXIT in c.state.unlockedPlayerPolicies)
        // Idempotent
        assertFalse(c.applyPolicyUnlock(PlayerPolicyType.BFS_EXIT))
        assertEquals(2, c.state.unlockedPlayerPolicies.size)
    }

    @Test
    fun lockedPlayerPolicies_excludesUnlocked() {
        val c = easyController()
        c.applyPolicyUnlock(PlayerPolicyType.BFS_EXIT)
        val locked = c.lockedPlayerPolicies()
        assertFalse(PlayerPolicyType.MANUAL in locked)
        assertFalse(PlayerPolicyType.BFS_EXIT in locked)
        assertTrue(PlayerPolicyType.ASTAR_EXIT in locked)
    }

    @Test
    fun setCurrentPlayerPolicy_onlyAllowedIfUnlocked() {
        val c = easyController()
        assertFalse(c.setCurrentPlayerPolicy(PlayerPolicyType.BFS_EXIT))
        assertEquals(PlayerPolicyType.MANUAL, c.state.currentPlayerPolicy)
        c.applyPolicyUnlock(PlayerPolicyType.BFS_EXIT)
        assertTrue(c.setCurrentPlayerPolicy(PlayerPolicyType.BFS_EXIT))
        assertEquals(PlayerPolicyType.BFS_EXIT, c.state.currentPlayerPolicy)
    }

    @Test
    fun winOutcomeUnlockCandidatesEmptyWhenAllUnlocked() {
        val c = easyController()
        // Unlock everything except MANUAL (already)
        PlayerPolicyType.entries.filter { it != PlayerPolicyType.MANUAL }.forEach {
            c.applyPolicyUnlock(it)
        }
        c.prepareCurrentMaze()
        val w = c.onMazeWon()
        assertTrue(w.unlockCandidates.isEmpty())
        assertFalse(w.unlockAvailable)
    }

    @Test
    fun winOutcomeOnFinalMazeMarksRunComplete() {
        val c = hardController() // 9 mazes, 1 life
        repeat(8) {
            c.prepareCurrentMaze()
            c.onMazeWon()
        }
        c.prepareCurrentMaze()
        val w = c.onMazeWon()
        assertTrue(w.runComplete)
        assertEquals(9, w.mazeIndexCompleted)
        assertTrue(w.unlockCandidates.isEmpty()) // no unlock after final win
        assertTrue(w.startingPowerUpCandidates.isEmpty())
    }

    @Test
    fun oddMazeWinOffersUpToThreeLockedPolicies() {
        val c = easyController()
        c.prepareCurrentMaze()
        val w = c.onMazeWon() // maze 1 → odd
        assertTrue(w.unlockAvailable)
        assertTrue(w.startingPowerUpCandidates.isEmpty())
        assertEquals(
            adventureAwardPlayerPolicyRanking().take(AdventureRunController.REWARD_SAMPLE_SIZE),
            w.unlockCandidates
        )
        w.unlockCandidates.forEach { assertFalse(it in c.state.unlockedPlayerPolicies) }
    }

    @Test
    fun oddMazeWinSkipsUnlockedPoliciesAndPreservesRankingOrder() {
        val c = easyController()
        val ranking = adventureAwardPlayerPolicyRanking()
        c.applyPolicyUnlock(ranking.first())

        c.prepareCurrentMaze()
        val w = c.onMazeWon()

        assertEquals(
            ranking.drop(1).take(AdventureRunController.REWARD_SAMPLE_SIZE),
            w.unlockCandidates
        )
    }

    @Test
    fun evenMazeWinOffersThreeNonGhostPowerUps() {
        val c = easyController()
        c.prepareCurrentMaze(); c.onMazeWon() // 1 → odd
        c.prepareCurrentMaze()
        val w = c.onMazeWon() // maze 2 → even
        assertTrue(w.startingPowerUpAvailable)
        assertTrue(w.unlockCandidates.isEmpty())
        assertEquals(AdventureRunController.REWARD_SAMPLE_SIZE, w.startingPowerUpCandidates.size)
        assertFalse(PowerUpType.GHOST_MODE in w.startingPowerUpCandidates)
    }

    @Test
    fun applyStartingPowerUpFlowsThroughPrepareCurrentMaze() {
        val c = easyController()
        c.prepareCurrentMaze(); c.onMazeWon() // odd
        c.prepareCurrentMaze(); c.onMazeWon() // even
        c.applyStartingPowerUp(PowerUpType.TELEPORT)
        val spec = c.prepareCurrentMaze()!!
        assertEquals(PowerUpType.TELEPORT, spec.startingPowerUp)
        // Locked per-maze: re-entering prepareCurrentMaze still carries it,
        // so process-death-then-restore cannot lose the reward before the
        // engine actually applies it.
        assertEquals(PowerUpType.TELEPORT, c.state.pendingStartingPowerUp)
        val spec2 = c.prepareCurrentMaze()!!
        assertEquals(PowerUpType.TELEPORT, spec2.startingPowerUp)
        // Cleared when we advance past the maze it was reserved for.
        c.onMazeWon()
        assertNull(c.state.pendingStartingPowerUp)
        val spec3 = c.prepareCurrentMaze()!!
        assertNull(spec3.startingPowerUp)
    }

    @Test
    fun startingPowerUpSurvivesDeathReplayUntilMazeWon() {
        val c = easyController()
        c.prepareCurrentMaze(); c.onMazeWon() // odd
        c.prepareCurrentMaze(); c.onMazeWon() // even
        c.applyStartingPowerUp(PowerUpType.TELEPORT)
        val spec = c.prepareCurrentMaze()!!
        assertEquals(PowerUpType.TELEPORT, spec.startingPowerUp)
        // Player dies on the maze; the reward must be preserved so the
        // replay re-applies it.
        c.onPlayerDied()
        assertEquals(PowerUpType.TELEPORT, c.state.pendingStartingPowerUp)
        val replaySpec = c.prepareCurrentMaze()!!
        assertEquals(PowerUpType.TELEPORT, replaySpec.startingPowerUp)
    }

    @Test
    fun policyRewardCandidatesIgnoreRunSeed() {
        val c1 = easyController(runSeed = 1234L)
        val c2 = easyController(runSeed = 5678L)
        c1.prepareCurrentMaze(); c2.prepareCurrentMaze()
        val w1 = c1.onMazeWon()
        val w2 = c2.onMazeWon()
        assertEquals(w1.unlockCandidates, w2.unlockCandidates)
    }

    @Test
    fun adventureAwardRankingContainsEveryAutomatedPolicyOnceAndExcludesManual() {
        val ranking = adventureAwardPlayerPolicyRanking()

        assertFalse(PlayerPolicyType.MANUAL in ranking)
        assertEquals(automatedPlayerPolicies().size, ranking.size)
        assertEquals(automatedPlayerPolicies().toSet(), ranking.toSet())
    }

    @Test
    fun midMazeSnapshotIsClearedOnDeath() {
        val c = easyController()
        c.prepareCurrentMaze()
        // Use a hand-built minimal snapshot for the test (won't be installed
        // into a real engine; we just verify the controller clears it).
        val fakeSnapshot = GameEngineSnapshot(
            difficultyName = DifficultyPresets.EASY.name,
            playerPolicy = PlayerPolicyType.MANUAL,
            npcPolicy = NpcPolicyType.DIRECT_CHASE,
            seed = 1L,
            status = GameStatus.RUNNING,
            elapsedSeconds = 0f,
            steps = 0,
            player = GameEngineSnapshot.PlayerSnapshot(0, 0, Direction.NORTH),
            npcs = emptyList(),
            spawnedPowerUps = emptyList(),
            activeEffects = emptyList(),
            npcInducedPlayerFreezeRemainingSeconds = null,
            manualQueue = emptyList(),
            manualOverrideRemainingSeconds = 0f
        )
        c.recordMidMazeSnapshot(fakeSnapshot)
        assertNotNull(c.state.currentMazeSnapshot)
        c.onPlayerDied()
        assertNull(c.state.currentMazeSnapshot)
    }
}
