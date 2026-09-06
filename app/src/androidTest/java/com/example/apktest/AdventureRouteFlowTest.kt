package com.example.apktest

import android.content.Intent
import android.os.SystemClock
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.AdventureConfig
import com.example.apktest.game.core.AdventureRunController
import com.example.apktest.game.core.AdventureRunStateSnapshot
import com.example.apktest.game.core.DifficultyPresets
import com.example.apktest.game.core.GameEngine
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.RewardStage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdventureRouteFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = AdventureStateStore(context)

    @After
    fun clearSavedRun() {
        store.clearBlocking()
    }

    @Test
    fun winAcknowledgementThenRouteSurvivesBackgroundAndRecreation() {
        val fixture = pendingOffer("supply_cache")
        assertTrue(store.saveBlocking(fixture))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            assertNoMaze(scenario)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            val offered = fixture.pendingReward!!.routeChoices
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            scenario.onActivity {
                assertEquals(offered, it.controllerForTesting().state.pendingReward!!.routeChoices)
                assertFalse(it.findViewById<android.widget.ToggleButton>(R.id.buttonAuto).isEnabled)
            }
            assertNoMaze(scenario)
            assertEquals(offered, store.load()!!.pendingReward!!.routeChoices)
        }
    }

    @Test
    fun supplyReplacesNormalChooserAndCommittedChoiceStartsOneMaze() {
        assertTrue(store.saveBlocking(pendingOffer("supply_cache")))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            chooseRoute(scenario, "supply_cache")
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            scenario.onActivity {
                assertEquals(3, it.rewardOptionsForTesting().size)
                assertEquals("supply_cache", it.controllerForTesting().state.pendingReward!!.selectedRouteId)
            }
            assertNoMaze(scenario)
            continueDialog(scenario)
            await(scenario) {
                it.controllerForTesting().state.pendingReward == null &&
                    it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) is GameFragment
            }
            val saved = store.load()!!
            assertNull(saved.pendingReward)
            assertNotNull(saved.pendingStartingPowerUp)
            assertEquals("supply_cache", saved.activeRoute!!.choiceId)
        }
    }

    @Test
    fun quietCorridorHasTwoRewardsAndExactSelectionStateRestores() {
        assertTrue(store.saveBlocking(pendingOffer("quiet_corridor")))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            chooseRoute(scenario, "quiet_corridor")
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            val saved = store.load()!!
            scenario.recreate()
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            scenario.onActivity {
                assertEquals(2, it.rewardOptionsForTesting().size)
                assertEquals(saved.pendingReward, it.controllerForTesting().state.pendingReward)
                assertEquals(saved.activeRoute, it.controllerForTesting().state.activeRoute)
            }
        }
    }

    @Test
    fun failedSaveCannotSkipDecisionAndRetryPersistsBeforeContinuing() {
        assertTrue(store.saveBlocking(pendingOffer("supply_cache")))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            scenario.onActivity { it.failNextRewardSaveForTesting() }
            continueDialog(scenario)
            await(scenario) {
                it.rewardSaveFailedForTesting() && it.rewardDialogForTesting()?.isShowing == true
            }
            assertEquals(RewardStage.WIN_ACKNOWLEDGEMENT, store.load()!!.pendingReward!!.stage)
            assertNoMaze(scenario)
            // The error dialog's positive button retries the same transition.
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            assertEquals(RewardStage.ROUTE_CHOICE, store.load()!!.pendingReward!!.stage)
            assertNoMaze(scenario)
        }
    }

    @Test
    fun queuedDuplicateContinueCannotAdvancePastRouteChoice() {
        val fixture = pendingOffer("supply_cache")
        assertTrue(store.saveBlocking(fixture))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            scenario.onActivity {
                val button = it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE)
                button.performClick()
                button.performClick()
            }
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            scenario.onActivity {
                assertEquals(fixture.currentMazeIndex, it.controllerForTesting().state.currentMazeIndex)
                assertEquals(fixture.totalSteps, it.controllerForTesting().state.totalSteps)
                assertNull(it.controllerForTesting().state.pendingReward!!.selectedRouteId)
            }
            assertNoMaze(scenario)
        }
    }

    @Test
    fun backCannotDismissRequiredRouteChoice() {
        assertTrue(store.saveBlocking(pendingOffer("supply_cache")))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            assertNoMaze(scenario)
            assertNull(store.load()!!.pendingReward!!.selectedRouteId)
        }
    }

    @Test
    fun explicitRelaunchResumesExactRouteWithoutReplayingWin() {
        val fixture = pendingOffer("scout_map")
        assertTrue(store.saveBlocking(fixture))
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
        }
        // A new ActivityScenario and explicit Resume exercise the disk path,
        // independently of the OS saved-instance-state Bundle.
        launchResume().use { scenario ->
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            scenario.onActivity {
                assertEquals(fixture.currentMazeIndex, it.controllerForTesting().state.currentMazeIndex)
                assertEquals(fixture.totalSteps, it.controllerForTesting().state.totalSteps)
                assertEquals(fixture.pendingReward!!.routeChoices,
                    it.controllerForTesting().state.pendingReward!!.routeChoices)
            }
            chooseRoute(scenario, "scout_map")
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            val preview = store.load()!!.pendingReward!!.preview
            assertNotNull(preview)
            scenario.recreate()
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            scenario.onActivity {
                assertEquals(preview, it.controllerForTesting().state.pendingReward!!.preview)
            }
        }
    }

    @Test
    fun capturedTerminalSnapshotPersistsWinTransitionBeforeResume() {
        launchResume().use { scenario ->
            await(scenario) {
                it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) is GameFragment &&
                    it.rewardDecisionReadyForTesting()
            }
            scenario.onActivity {
                val spec = it.controllerForTesting().prepareCurrentMaze()!!
                val snapshot = GameEngine(spec.difficulty, spec.seed).snapshot().copy(
                    status = GameStatus.WIN,
                    elapsedSeconds = 12f,
                    steps = 34
                )
                it.handleCapturedSnapshotForTesting(snapshot)
            }
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            val saved = store.load()!!
            assertEquals(1, saved.currentMazeIndex)
            assertEquals(12f, saved.totalElapsedSeconds)
            assertEquals(34, saved.totalSteps)
        }
    }

    @Test
    fun capturedTerminalSnapshotPersistsDeathTransitionBeforeResume() {
        launchResume().use { scenario ->
            await(scenario) {
                it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) is GameFragment &&
                    it.rewardDecisionReadyForTesting()
            }
            var initialLives = 0
            scenario.onActivity {
                initialLives = it.controllerForTesting().state.livesRemaining
                val spec = it.controllerForTesting().prepareCurrentMaze()!!
                val snapshot = GameEngine(spec.difficulty, spec.seed).snapshot().copy(
                    status = GameStatus.LOSE
                )
                it.handleCapturedSnapshotForTesting(snapshot)
            }
            await(scenario) {
                it.rewardDialogForTesting()?.isShowing == true &&
                    it.rewardDecisionReadyForTesting()
            }
            val saved = store.load()!!
            assertEquals(initialLives - 1, saved.livesRemaining)
            assertEquals(1, saved.deathsThisRun)
        }
    }

    private fun launchResume(): ActivityScenario<AdventureActivity> =
        ActivityScenario.launch(Intent(context, AdventureActivity::class.java).apply {
            putExtra(AdventureSetupActivity.EXTRA_RESUME, true)
        })

    private fun continueDialog(scenario: ActivityScenario<AdventureActivity>) {
        scenario.onActivity {
            it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
    }

    private fun chooseRoute(scenario: ActivityScenario<AdventureActivity>, id: String) {
        scenario.onActivity {
            val offer = it.controllerForTesting().state.pendingReward!!
            val index = offer.routeChoices.indexOfFirst { choice -> choice.id == id }
            assertTrue(index >= 0)
            it.rewardDialogForTesting()!!.listView.setItemChecked(index, true)
            it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
    }

    private fun assertNoMaze(scenario: ActivityScenario<AdventureActivity>) {
        scenario.onActivity {
            assertNull(it.supportFragmentManager.findFragmentById(R.id.fragmentGameHost))
        }
    }

    private fun awaitStage(scenario: ActivityScenario<AdventureActivity>, stage: RewardStage) {
        await(scenario) {
            it.rewardStageForTesting() == stage && it.rewardDialogForTesting()?.isShowing == true &&
                it.rewardDecisionReadyForTesting()
        }
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
        throw AssertionError("Adventure reward flow did not reach expected state")
    }

    private fun pendingOffer(routeId: String): AdventureRunStateSnapshot {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        for (seed in 0L..128L) {
            val controller = AdventureRunController(config, runSeed = seed, routesEnabled = true)
            repeat(config.totalMazes - 1) {
                controller.prepareCurrentMaze()
                controller.completeMaze(elapsedSeconds = 1f, steps = 1)
                val pending = controller.state.pendingReward!!
                if (pending.routeChoices.any { it.id == routeId }) {
                    return AdventureRunStateSnapshot.fromState(controller.state, seed)
                }
                controller.acknowledgeMazeWin(pending.mazeIndexCompleted)
                if (controller.state.pendingReward!!.stage == RewardStage.ROUTE_CHOICE) {
                    controller.chooseRoute(pending.mazeIndexCompleted, pending.routeChoices.first().id)
                }
                controller.chooseStartingPowerUp(
                    pending.mazeIndexCompleted, controller.state.pendingReward!!.powerUpCandidates.first()
                )
            }
        }
        throw AssertionError("No deterministic fixture for $routeId")
    }
}
