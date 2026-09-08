package com.example.apktest

import android.content.Intent
import android.view.View
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.view.ViewCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.game.core.AdventureRunState
import com.example.apktest.game.core.PlayerPolicyType
import com.example.apktest.ui.AdventureHudText
import com.example.apktest.ui.GameControlsPresentation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GameplayPresentationTest {
    @Test
    fun controlsExplainLockedAndPendingStatesWithoutChangingSelection() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(SetupActivity.EXTRA_PLAYER_POLICY, PlayerPolicyType.BFS_EXIT.name)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val auto = activity.findViewById<ToggleButton>(R.id.buttonAuto)
                val glide = activity.findViewById<ToggleButton>(R.id.buttonInertia)
                val hint = activity.findViewById<TextView>(R.id.controlHint)
                val presentation = GameControlsPresentation(activity)
                auto.isChecked = false
                auto.isEnabled = false
                presentation.update(automationAvailable = false)
                assertEquals(activity.getString(R.string.auto_locked), auto.text.toString())
                assertEquals(activity.getString(R.string.auto_locked_hint), hint.text.toString())
                assertEquals(hint.text.toString(), ViewCompat.getStateDescription(auto).toString())
                assertEquals(View.VISIBLE, hint.visibility)

                presentation.update(automationAvailable = true, decisionPending = true)
                assertEquals(activity.getString(R.string.auto_waiting), auto.text.toString())
                assertEquals(activity.getString(R.string.auto_waiting_hint), hint.text.toString())
                assertFalse(auto.isEnabled)
                assertFalse(auto.isChecked)

                auto.isEnabled = true
                auto.isChecked = true
                presentation.update(automationAvailable = true)
                assertEquals(activity.getString(R.string.auto_on), auto.text.toString())
                assertEquals(View.GONE, hint.visibility)
                assertTrue(glide.isChecked)
                assertEquals(activity.getString(R.string.glide_on), glide.text.toString())
                glide.performClick()
                assertEquals(activity.getString(R.string.glide_off), glide.text.toString())
            }
        }
    }

    @Test
    fun hudResourcesIdentifyCompletedMazesAndExplainBonusProgress() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = AdventureHudText { id, args -> context.getString(id, *args) }.format(
            AdventureRunState(
                difficultyName = "Medium",
                currentMazeIndex = 1,
                livesRemaining = 2,
                totalElapsedSeconds = 125f,
                totalSteps = 1234,
                winStreakSinceLastBonus = 1
            ),
            totalMazes = 7
        )
        assertEquals(context.getString(R.string.adventure_hud_primary, 2, 7, 2), text.primary)
        assertEquals(context.getString(R.string.adventure_hud_completed, "02:05", 1234), text.completedStats)
        assertEquals(context.getString(R.string.adventure_hud_streak, 1, 3), text.streak)
        assertEquals(context.getString(R.string.adventure_hud_streak_description, 1, 3), text.streakDescription)
    }
}
