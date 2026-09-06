package com.example.apktest

import android.content.Intent
import android.os.SystemClock
import android.view.KeyEvent
import android.widget.ImageButton
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
import com.example.apktest.game.core.GameEngineSnapshot
import com.example.apktest.game.core.GameStatus
import com.example.apktest.game.core.PowerUpType
import com.example.apktest.game.core.RewardStage
import com.example.apktest.game.core.RunPerkEffects
import com.example.apktest.game.core.RunPerkId
import com.example.apktest.game.core.RunPerkTier
import com.example.apktest.telemetry.AdventureTelemetryEvent
import com.example.apktest.telemetry.AdventureTelemetryEventNames
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdventurePerkFlowTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store get() = AdventureStateStore(context)

    @After
    fun clearSavedRun() {
        store.clearBlocking()
    }

    @Test
    fun routeThenPerkThenOnePowerRewardRestoresExactOffersAcrossRecreationAndDiskResume() {
        val fixture = pendingOffer(RunPerkId.QUICK_FEET, withRoute = true)
        assertTrue(store.saveBlocking(fixture))
        launch().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.ROUTE_CHOICE)
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.PERK_CHOICE)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            awaitStage(scenario, RewardStage.PERK_CHOICE)
            scenario.onActivity {
                assertEquals(fixture.pendingReward!!.perkOffer,
                    it.controllerForTesting().state.pendingReward!!.perkOffer)
                assertNull(fragment(it))
                assertTrue(it.rewardOptionsForTesting().all { option ->
                    option.contains(it.getString(R.string.adventure_perk_scope_this_run)) ||
                        option.contains(it.getString(R.string.adventure_perk_scope_once_per_run))
                })
            }
        }
        launch().use { scenario ->
            awaitStage(scenario, RewardStage.PERK_CHOICE)
            choosePerk(scenario, RunPerkId.QUICK_FEET)
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            scenario.onActivity {
                assertNull(fragment(it))
                assertEquals(RunPerkId.QUICK_FEET,
                    it.controllerForTesting().state.pendingReward!!.selectedPerkId)
            }
            val saved = store.load()!!
            scenario.recreate()
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            assertEquals(saved.pendingReward, store.load()!!.pendingReward)
            continueDialog(scenario)
            await(scenario) { fragment(it) != null && it.rewardDecisionReadyForTesting() }
            assertNull(store.load()!!.pendingReward)
            assertNotNull(store.load()!!.pendingStartingPowerUp)
        }
    }

    @Test
    fun saveFailureBackAndDuplicateButtonsCannotSkipOrDoubleChoosePerk() {
        val fixture = pendingOffer(RunPerkId.QUICK_FEET)
        assertTrue(store.saveBlocking(fixture))
        val events = mutableListOf<AdventureTelemetryEvent>()
        launch().use { scenario ->
            awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
            scenario.onActivity { it.setPerkTelemetrySinkForTesting { event -> events += event } }
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.PERK_CHOICE)
            val stacksBefore = store.load()!!.runPerks.sumOf { it.stacks }
            scenario.onActivity {
                it.failNextRewardSaveForTesting()
                val index = it.controllerForTesting().state.pendingReward!!.perkOffer!!.choices
                    .indexOf(RunPerkId.QUICK_FEET)
                it.rewardDialogForTesting()!!.listView.setItemChecked(index, true)
                val button = it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE)
                button.performClick()
                button.performClick()
            }
            await(scenario) { it.rewardSaveFailedForTesting() && it.rewardDialogForTesting()?.isShowing == true }
            assertEquals(RewardStage.PERK_CHOICE, store.load()!!.pendingReward!!.stage)
            assertEquals(stacksBefore, store.load()!!.runPerks.sumOf { it.stacks })
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            scenario.onActivity {
                assertTrue(it.rewardSaveFailedForTesting())
                assertNull(fragment(it))
                assertEquals(0, events.count { event -> event.name == AdventureTelemetryEventNames.PERK_CHOSEN })
            }
            continueDialog(scenario)
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            scenario.onActivity {
                assertEquals(stacksBefore + 1, it.controllerForTesting().state.runPerks.sumOf { perk -> perk.stacks })
                assertEquals(1, events.count { event -> event.name == AdventureTelemetryEventNames.PERK_CHOSEN })
                assertEquals(1, events.count { event -> event.name == AdventureTelemetryEventNames.PERK_OFFER_SHOWN })
            }
        }
    }

    @Test
    fun backCannotDismissPerkChooserAndScoutPreviewIsLockedBeforePowerChoice() {
        val fixture = pendingOffer(RunPerkId.SCOUT_SENSE)
        assertTrue(store.saveBlocking(fixture))
        launch().use { scenario ->
            reachPerkStage(scenario)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            awaitStage(scenario, RewardStage.PERK_CHOICE)
            choosePerk(scenario, RunPerkId.SCOUT_SENSE)
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            val preview = store.load()!!.pendingReward!!.scoutPreview!!
            scenario.recreate()
            awaitStage(scenario, RewardStage.POWER_UP_CHOICE)
            scenario.onActivity {
                assertEquals(preview, it.controllerForTesting().state.pendingReward!!.scoutPreview)
                assertTrue(it.rewardTitleForTesting().toString().contains(it.getString(
                    R.string.adventure_perk_scout_preview, preview.npcCount, preview.eliteCount
                )))
                assertNull(fragment(it))
            }
        }
    }

    @Test
    fun secondWindBarrierWaitsForCombinedSaveAndRejectsStaleAutosaveAfterAcknowledgement() {
        val fixture = playableFixture(RunPerkId.SECOND_WIND)
        val armed = fixture.currentMazeSnapshot!!
        val barrier = barrier(armed)
        assertTrue(store.saveBlocking(fixture))
        val events = mutableListOf<AdventureTelemetryEvent>()
        launch().use { scenario ->
            awaitMaze(scenario)
            scenario.onActivity {
                it.setPerkTelemetrySinkForTesting { event -> events += event }
                it.failNextRewardSaveForTesting()
                it.attachPerkBarrierForTesting(barrier)
            }
            await(scenario) { it.rewardSaveFailedForTesting() && it.rewardDialogForTesting()?.isShowing == true }
            scenario.onActivity {
                val live = fragment(it)!!.captureSnapshot()!!
                assertEquals(RunPerkId.SECOND_WIND, live.pendingConsumedRunPerk)
                assertEquals(barrier.elapsedSeconds, live.elapsedSeconds)
                assertFalse(live.runPerkEffects.secondWindAvailable)
                assertTrue(events.isEmpty())
            }
            assertFalse(store.load()!!.runPerks.first { it.id == RunPerkId.SECOND_WIND }.consumed)
            InstrumentationRegistry.getInstrumentation().sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            continueDialog(scenario)
            await(scenario) {
                it.rewardDecisionReadyForTesting() &&
                    fragment(it)?.captureSnapshot()?.pendingConsumedRunPerk == null &&
                    it.perkFeedbackForTesting().isNotEmpty()
            }
            val committed = store.load()!!
            assertTrue(committed.runPerks.first { it.id == RunPerkId.SECOND_WIND }.consumed)
            assertEquals(RunPerkId.SECOND_WIND, committed.currentMazeSnapshot!!.pendingConsumedRunPerk)
            assertEquals(fixture.livesRemaining, committed.livesRemaining)
            scenario.onActivity {
                fragment(it)!!.togglePause()
                it.handleCapturedSnapshotForTesting(armed)
                it.handleCapturedSnapshotForTesting(barrier)
                assertEquals(committed.runPerks, it.controllerForTesting().state.runPerks)
                assertEquals(committed.currentMazeSnapshot, it.controllerForTesting().state.currentMazeSnapshot)
                assertTrue(it.perkStatusTextForTesting().contains(it.getString(R.string.adventure_perk_used)))
                it.findViewById<ImageButton>(R.id.buttonMenu).performClick()
                assertTrue(it.menuTextForTesting().toString().contains(it.getString(R.string.adventure_perk_used)))
                assertEquals(1, events.count { event -> event.name == AdventureTelemetryEventNames.PERK_CONSUMED })
                assertEquals(1, events.count { event -> event.name == AdventureTelemetryEventNames.PERK_EFFECT_APPLIED })
            }
        }
    }

    @Test
    fun consumedPendingMarkerReconcilesOnRestoreBeforeGameplayWithoutRearming() {
        val fixture = playableFixture(RunPerkId.SECOND_WIND)
        val controller = controller(fixture)
        val barrier = barrier(fixture.currentMazeSnapshot!!)
        assertTrue(controller.consumePerk(RunPerkId.SECOND_WIND))
        controller.recordMidMazeSnapshot(barrier)
        val committed = AdventureRunStateSnapshot.fromState(controller.state, fixture.runSeed)
        assertNotNull(AdventureRunStateSnapshot.fromJson(committed.toJson()))
        assertTrue(store.saveBlocking(committed))
        launch().use { scenario ->
            await(scenario) {
                it.rewardDecisionReadyForTesting() && it.perkFeedbackForTesting().isNotEmpty() &&
                    fragment(it)?.captureSnapshot()?.pendingConsumedRunPerk == null
            }
            scenario.onActivity {
                assertFalse(fragment(it)!!.captureSnapshot()!!.runPerkEffects.secondWindAvailable)
                fragment(it)!!.togglePause()
                assertTrue(it.controllerForTesting().state.runPerks.first { perk ->
                    perk.id == RunPerkId.SECOND_WIND
                }.consumed)
            }
            await(scenario) { fragment(it)?.captureSnapshot()?.status == GameStatus.PAUSED }
            scenario.recreate()
            awaitMaze(scenario)
            scenario.onActivity {
                assertFalse(fragment(it)!!.captureSnapshot()!!.runPerkEffects.secondWindAvailable)
                assertEquals(fixture.deathsThisRun, it.controllerForTesting().state.deathsThisRun)
            }
        }
    }

    @Test
    fun lossShowsRetainedRunBuildAndEmitsOutcomeOnlyAfterClearSucceeds() {
        val fixture = playableFixture(RunPerkId.QUICK_FEET).copy(livesRemaining = 1)
        assertTrue(store.saveBlocking(fixture))
        val events = mutableListOf<AdventureTelemetryEvent>()
        launch().use { scenario ->
            awaitMaze(scenario)
            scenario.onActivity {
                assertEquals(RunPerkEffects.fromStacks(fixture.runPerks),
                    fragment(it)!!.captureSnapshot()!!.runPerkEffects)
                it.setPerkTelemetrySinkForTesting { event -> events += event }
                it.failNextRewardSaveForTesting()
                it.handleCapturedSnapshotForTesting(fixture.currentMazeSnapshot!!.copy(status = GameStatus.LOSE))
            }
            await(scenario) { it.rewardSaveFailedForTesting() && it.rewardDialogForTesting()?.isShowing == true }
            assertNotNull(store.load())
            scenario.onActivity { assertTrue(events.isEmpty()) }
            continueDialog(scenario)
            await(scenario) {
                it.rewardDecisionReadyForTesting() && it.rewardDialogForTesting()?.isShowing == true
            }
            assertNull(store.load())
            scenario.onActivity {
                assertTrue(it.rewardTextForTesting().toString().contains(
                    it.getString(R.string.adventure_perk_quick_feet_name)
                ))
                assertEquals(fixture.runPerks, it.controllerForTesting().state.runPerks)
                assertEquals(listOf(AdventureTelemetryEventNames.PERK_RUN_OUTCOME), events.map { event -> event.name })
                assertEquals("false", events.single().properties["completed"])
            }
        }
    }

    @Test
    fun systemBackAcceptsFinalGlCheckpointAfterFinishBegins() {
        val fixture = playableFixture(RunPerkId.QUICK_FEET)
        assertTrue(store.saveBlocking(fixture))
        launch().use { scenario ->
            awaitMaze(scenario)
            scenario.onActivity { fragment(it)!!.togglePause() }
            await(scenario) {
                (fragment(it)?.captureSnapshot()?.elapsedSeconds ?: 0f) >=
                    fixture.currentMazeSnapshot!!.elapsedSeconds + 0.1f
            }
            var latestElapsed = 0f
            scenario.onActivity {
                val latest = fragment(it)!!.captureSnapshot()!!
                latestElapsed = latest.elapsedSeconds
                it.onBackPressedDispatcher.onBackPressed()
                assertTrue(it.isFinishing)
                // Deterministically deliver the GL callback after Back marked the host
                // finishing, but before destruction invalidates this save session.
                it.handleCapturedSnapshotForTesting(latest)
                assertNull(it.rewardDialogForTesting())
                assertTrue(it.perkFeedbackForTesting().isEmpty())
            }
            assertTrue(store.load()!!.currentMazeSnapshot!!.elapsedSeconds >= latestElapsed)
        }
    }

    private fun barrier(snapshot: GameEngineSnapshot): GameEngineSnapshot = snapshot.copy(
        status = GameStatus.RUNNING,
        runPerkEffects = snapshot.runPerkEffects.copy(secondWindAvailable = false),
        pendingConsumedRunPerk = RunPerkId.SECOND_WIND,
        activeEffects = listOf(GameEngineSnapshot.ActiveEffectSnapshot(PowerUpType.FREEZE, 1f))
    )

    private fun playableFixture(id: RunPerkId): AdventureRunStateSnapshot {
        val fixture = pendingOffer(id)
        val controller = controller(fixture)
        resolveReward(controller, id)
        controller.setAutomatedPolicyPromptShown(true)
        controller.applyStartingPowerUp(null)
        val spec = controller.prepareCurrentMaze()!!
        val engine = GameEngine(spec.difficulty, spec.seed).apply {
            configureAdventureMaze(
                spec.npcCount, spec.npcPolicies, npcSpawnSpecs = spec.npcSpawnSpecs,
                pickupLifetimeSeconds = spec.pickupLifetimeSeconds, runPerkEffects = spec.runPerkEffects
            )
            restart(spec.seed)
            togglePause()
        }
        controller.recordMidMazeSnapshot(engine.snapshot())
        return AdventureRunStateSnapshot.fromState(controller.state, fixture.runSeed).also {
            assertNotNull(it.currentMazeSnapshot)
            assertNotNull(AdventureRunStateSnapshot.fromJson(it.toJson()))
        }
    }

    private fun controller(fixture: AdventureRunStateSnapshot) = AdventureRunController(
        AdventureConfig.forDifficultyName(fixture.difficultyName), fixture.toState(), fixture.runSeed,
        routesEnabled = true, perksEnabled = true, perkTiers = RunPerkTier.entries.toSet()
    )

    private fun pendingOffer(id: RunPerkId, withRoute: Boolean = false): AdventureRunStateSnapshot {
        val config = AdventureConfig.forDifficulty(DifficultyPresets.HARD)
        for (seed in 0L..512L) {
            val controller = AdventureRunController(
                config, runSeed = seed, routesEnabled = true, perksEnabled = true,
                perkTiers = RunPerkTier.entries.toSet()
            )
            repeat(config.totalMazes - 1) {
                controller.prepareCurrentMaze()
                controller.completeMaze(1f, 1)
                val reward = controller.state.pendingReward!!
                if (reward.perkOffer?.choices?.contains(id) == true &&
                    (!withRoute || reward.routeChoices.isNotEmpty())) {
                    controller.setAutomatedPolicyPromptShown(true)
                    return AdventureRunStateSnapshot.fromState(controller.state, seed).also {
                        assertNotNull(AdventureRunStateSnapshot.fromJson(it.toJson()))
                    }
                }
                resolveReward(controller, reward.perkOffer?.choices?.firstOrNull { it != id })
            }
        }
        throw AssertionError("No deterministic perk fixture for $id")
    }

    private fun resolveReward(controller: AdventureRunController, perk: RunPerkId?) {
        while (true) {
            val reward = controller.state.pendingReward ?: return
            val index = reward.mazeIndexCompleted
            when (reward.stage) {
                RewardStage.WIN_ACKNOWLEDGEMENT -> assertTrue(controller.acknowledgeMazeWin(index))
                RewardStage.ROUTE_CHOICE -> assertTrue(controller.chooseRoute(index, reward.routeChoices.first().id))
                RewardStage.PERK_CHOICE -> assertTrue(controller.choosePerk(index, perk ?: reward.perkOffer!!.choices.first()))
                RewardStage.POWER_UP_CHOICE -> assertTrue(controller.chooseStartingPowerUp(index, reward.powerUpCandidates.first()))
            }
        }
    }

    private fun launch(): ActivityScenario<AdventureActivity> =
        ActivityScenario.launch(Intent(context, AdventureActivity::class.java).apply {
            putExtra(AdventureSetupActivity.EXTRA_RESUME, true)
        })

    private fun reachPerkStage(scenario: ActivityScenario<AdventureActivity>) {
        awaitStage(scenario, RewardStage.WIN_ACKNOWLEDGEMENT)
        continueDialog(scenario)
        await(scenario) { it.rewardDecisionReadyForTesting() && it.rewardDialogForTesting()?.isShowing == true }
        scenario.onActivity {
            if (it.rewardStageForTesting() == RewardStage.ROUTE_CHOICE) {
                it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
            }
        }
        awaitStage(scenario, RewardStage.PERK_CHOICE)
    }

    private fun choosePerk(scenario: ActivityScenario<AdventureActivity>, id: RunPerkId) {
        scenario.onActivity {
            val index = it.controllerForTesting().state.pendingReward!!.perkOffer!!.choices.indexOf(id)
            assertTrue(index >= 0)
            it.rewardDialogForTesting()!!.listView.setItemChecked(index, true)
            it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
    }

    private fun continueDialog(scenario: ActivityScenario<AdventureActivity>) {
        scenario.onActivity {
            it.rewardDialogForTesting()!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        }
    }

    private fun fragment(activity: AdventureActivity): GameFragment? =
        activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment

    private fun awaitMaze(scenario: ActivityScenario<AdventureActivity>) {
        await(scenario) { it.rewardDecisionReadyForTesting() && fragment(it)?.captureSnapshot() != null }
    }

    private fun awaitStage(scenario: ActivityScenario<AdventureActivity>, stage: RewardStage) {
        await(scenario) {
            it.rewardDecisionReadyForTesting() && it.rewardStageForTesting() == stage &&
                it.rewardDialogForTesting()?.isShowing == true
        }
    }

    private fun await(scenario: ActivityScenario<AdventureActivity>, condition: (AdventureActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(20)
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Adventure perk flow did not reach expected state")
    }
}
