package com.example.apktest

import android.content.Intent
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.AdventureRunController
import com.example.apktest.game.core.AdventureRunStateSnapshot
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.RewardStage
import com.example.apktest.telemetry.AdventureTelemetryEvent
import com.example.apktest.telemetry.AdventureTelemetryEventNames
import com.example.apktest.ui.LegendDialog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdventureEliteFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = AdventureStateStore(context)

    @After
    fun clearSavedRun() {
        store.clearBlocking()
    }

    @Test
    fun savedElitesReachFreshFragmentAndSurviveRecreationWithGenerationDisabled() {
        val fixture = eliteFixture()
        assertTrue(store.saveBlocking(fixture))
        launchResume().use { scenario ->
            assertRoster(scenario, fixture)
            scenario.recreate()
            assertRoster(scenario, fixture)
            assertEquals(fixture.currentMazeNpcSpawnSpecs, store.load()!!.currentMazeNpcSpawnSpecs)
        }
    }

    @Test
    fun midMazeResumePreservesModifiersAndSkipsCountdown() {
        val fixture = eliteFixture()
        val engine = engineFor(fixture)
        engine.togglePause()
        assertTrue(store.saveBlocking(fixture.copy(currentMazeSnapshot = engine.snapshot())))
        launchResume().use { scenario ->
            val restored = assertRoster(scenario, fixture)
            assertEquals(GameStatus.PAUSED, restored.status)
            scenario.onActivity {
                val fragment = it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as GameFragment
                assertNull(fragment.hudState()!!.countdownRemainingSeconds)
            }
        }
    }

    @Test
    fun retryUsesTheSameSpecsOnTheExistingFragment() {
        val fixture = eliteFixture()
        assertTrue(store.saveBlocking(fixture))
        launchResume().use { scenario ->
            val started = assertRoster(scenario, fixture)
            val events = mutableListOf<AdventureTelemetryEvent>()
            var originalFragment: GameFragment? = null
            scenario.onActivity {
                it.setEliteTelemetrySinkForTesting { event -> events += event }
                originalFragment = it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as GameFragment
                it.handleCapturedSnapshotForTesting(started.copy(status = GameStatus.LOSE))
            }
            await(scenario) { it.rewardDialogForTesting()?.isShowing == true && it.rewardDecisionReadyForTesting() }
            scenario.onActivity {
                assertEquals(listOf(AdventureTelemetryEventNames.ELITE_MODIFIER_OUTCOME), events.map { event -> event.name })
            }
            assertEquals(fixture.currentMazeNpcSpawnSpecs, store.load()!!.currentMazeNpcSpawnSpecs)
            scenario.onActivity {
                it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            assertRoster(scenario, fixture)
            await(scenario) { events.any { it.name == AdventureTelemetryEventNames.ELITE_MODIFIER_SPAWNED } }
            scenario.onActivity {
                assertTrue(originalFragment === it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost))
                val before = events.toList()
                it.controllerForTesting().prepareCurrentMaze()
                it.controllerForTesting().prepareCurrentMaze()
                val modifiers = fixture.currentMazeNpcSpawnSpecs.mapNotNull { spec -> spec.eliteModifier }.toSet()
                LegendDialog.show(it, modifiers).dismiss()
                assertEquals(before, events)
                assertEquals(2, events.size)
            }
            assertEquals(fixture.deathsThisRun + 1, store.load()!!.deathsThisRun)
        }
    }

    @Test
    fun failedTerminalSaveCannotStartAnotherMazeAndStaleSnapshotCannotReplaceReward() {
        val fixture = eliteFixture()
        assertTrue(store.saveBlocking(fixture))
        launchResume().use { scenario ->
            val started = assertRoster(scenario, fixture)
            scenario.onActivity {
                it.failNextRewardSaveForTesting()
                it.handleCapturedSnapshotForTesting(started.copy(status = GameStatus.WIN))
            }
            await(scenario) { it.rewardSaveFailedForTesting() && it.rewardDialogForTesting()?.isShowing == true }
            scenario.onActivity {
                assertEquals(fixture.currentMazeIndex + 1, it.controllerForTesting().state.currentMazeIndex)
                it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
            await(scenario) {
                it.rewardStageForTesting() == RewardStage.WIN_ACKNOWLEDGEMENT &&
                    it.rewardDecisionReadyForTesting()
            }
            scenario.onActivity {
                it.controllerForTesting().recordMidMazeSnapshot(started)
                assertNull(it.controllerForTesting().state.currentMazeSnapshot)
            }
            scenario.recreate()
            await(scenario) {
                it.rewardStageForTesting() == RewardStage.WIN_ACKNOWLEDGEMENT &&
                    it.rewardDecisionReadyForTesting()
            }
            scenario.onActivity {
                assertNull(it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost))
                assertTrue(it.controllerForTesting().state.currentMazeNpcSpawnSpecs.isEmpty())
            }
        }
    }

    private fun assertRoster(
        scenario: ActivityScenario<AdventureActivity>,
        fixture: AdventureRunStateSnapshot
    ): GameEngineSnapshot {
        var captured: GameEngineSnapshot? = null
        await(scenario) { activity ->
            val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
            if (!activity.rewardDecisionReadyForTesting() || fragment == null) false else {
                captured = fragment.captureSnapshot()
                captured?.let { snapshot ->
                    snapshot.seed == fixture.currentMazeSeed &&
                        snapshot.npcSpawnSpecs == fixture.currentMazeNpcSpawnSpecs
                } == true
            }
        }
        val snapshot = checkNotNull(captured)
        assertNotNull(snapshot.npcs.firstOrNull { it.eliteModifier != null })
        snapshot.npcs.forEach {
            assertEquals(fixture.currentMazeNpcSpawnSpecs[it.id].eliteModifier, it.eliteModifier)
        }
        return snapshot
    }

    private fun launchResume(): ActivityScenario<AdventureActivity> =
        ActivityScenario.launch(Intent(context, AdventureActivity::class.java).apply {
            putExtra(AdventureSetupActivity.EXTRA_RESUME, true)
        })

    private fun eliteFixture(): AdventureRunStateSnapshot {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.MEDIUM)
        for (seed in 0L..64L) {
            val controller = AdventureRunController(config, runSeed = seed, elitesEnabled = true)
            repeat(4) {
                controller.prepareCurrentMaze()
                val won = controller.onMazeWon()
                controller.applyStartingPowerUp(won.startingPowerUpCandidates.first())
            }
            val spec = controller.prepareCurrentMaze()!!
            if (spec.npcSpawnSpecs.any { it.eliteModifier != null }) {
                controller.setAutomatedPolicyPromptShown(true)
                // Avoid an instant starting reward obscuring initial-roster assertions.
                controller.applyStartingPowerUp(null)
                val snapshot = AdventureRunStateSnapshot.fromState(controller.state, seed)
                assertNotNull(AdventureRunStateSnapshot.fromJson(snapshot.toJson()))
                assertFalse(snapshot.currentMazeNpcSpawnSpecs.all { it.eliteModifier != null })
                return snapshot
            }
        }
        throw AssertionError("Expected a safe deterministic Tracker fixture")
    }

    private fun engineFor(fixture: AdventureRunStateSnapshot): GameEngine =
        GameEngine(DifficultyPresets.MEDIUM, fixture.currentMazeSeed!!).apply {
            configureAdventureMaze(
                fixture.currentMazeNpcCount!!, fixture.currentMazeNpcPolicies,
                npcSpawnSpecs = fixture.currentMazeNpcSpawnSpecs
            )
            restart(fixture.currentMazeSeed!!)
        }

    private fun await(
        scenario: ActivityScenario<AdventureActivity>,
        condition: (AdventureActivity) -> Boolean
    ) {
        val deadline = SystemClock.uptimeMillis() + 5_000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Elite Adventure flow did not reach expected state")
    }
}
