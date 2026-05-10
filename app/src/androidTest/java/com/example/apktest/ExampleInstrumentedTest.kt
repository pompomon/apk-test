package com.example.apktest

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.BuildConfig
import com.example.apktest.game.GameFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun mainActivity_displaysGameHostAndControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val viewIds = intArrayOf(
                    R.id.fragmentGameHost,
                    R.id.spinnerPlayerPolicy,
                    R.id.spinnerNpcPolicy,
                    R.id.spinnerDifficulty,
                    R.id.buttonApply,
                    R.id.buttonPause,
                    R.id.buttonRestart,
                    R.id.buttonUp,
                    R.id.buttonLeft,
                    R.id.buttonDown,
                    R.id.buttonRight
                )
                for (id in viewIds) {
                    val view = activity.findViewById<android.view.View>(id)
                    assertNotNull("View with id $id should be inflated", view)
                    assertEquals(
                        "View with id $id should be VISIBLE",
                        android.view.View.VISIBLE,
                        view.visibility
                    )
                }
            }
        }
    }

    @Test
    fun mainActivity_fragmentHostAcceptsSwipeAndControlsRemainVisible() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var initialSteps = 0
            scenario.onActivity { activity ->
                val gameHost = activity.findViewById<android.view.View>(R.id.fragmentGameHost)
                assertNotNull("Game host should be present", gameHost)
                val fragment = attachedGameFragment(activity)
                initialSteps = fragment.hudState()?.steps ?: 0

                dispatchSwipe(gameHost, startX = 200f, startY = 600f, endX = 200f, endY = 300f)
                dispatchSwipe(gameHost, startX = 200f, startY = 300f, endX = 200f, endY = 600f)
                dispatchSwipe(gameHost, startX = 300f, startY = 400f, endX = 50f, endY = 400f)
                dispatchSwipe(gameHost, startX = 50f, startY = 400f, endX = 300f, endY = 400f)
            }

            val stepsAfterSwipes = pollHudSteps(scenario, initialSteps)
            assertTrue(
                "At least one swipe should enqueue and apply a manual move",
                stepsAfterSwipes > initialSteps
            )

            scenario.onActivity { activity ->
                val controlIds = intArrayOf(
                    R.id.buttonUp,
                    R.id.buttonLeft,
                    R.id.buttonDown,
                    R.id.buttonRight
                )
                for (id in controlIds) {
                    val control = activity.findViewById<android.view.View>(id)
                    assertEquals(android.view.View.VISIBLE, control.visibility)
                    assertTrue(control.isEnabled)
                }
            }
        }
    }

    private fun attachedGameFragment(activity: MainActivity): GameFragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
        return requireNotNull(fragment) { "Game fragment should be attached" }
    }

    private fun pollHudSteps(
        scenario: ActivityScenario<MainActivity>,
        initialSteps: Int
    ): Int {
        var steps = initialSteps
        repeat(MAX_STEP_POLL_ATTEMPTS) {
            scenario.onActivity { activity ->
                val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
                steps = fragment?.hudState()?.steps ?: steps
            }
            if (steps > initialSteps) return steps
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
        return steps
    }

    private fun dispatchSwipe(
        target: android.view.View,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val downTime = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            startX,
            startY,
            0
        )
        val move = MotionEvent.obtain(
            downTime,
            downTime + 30L,
            MotionEvent.ACTION_MOVE,
            (startX + endX) / 2f,
            (startY + endY) / 2f,
            0
        )
        val up = MotionEvent.obtain(
            downTime,
            downTime + 60L,
            MotionEvent.ACTION_UP,
            endX,
            endY,
            0
        )
        try {
            target.dispatchTouchEvent(down)
            target.dispatchTouchEvent(move)
            target.dispatchTouchEvent(up)
        } finally {
            down.recycle()
            move.recycle()
            up.recycle()
        }
    }

    companion object {
        // 40 idle-sync polls provides a generous upper bound while keeping the test bounded.
        private const val MAX_STEP_POLL_ATTEMPTS = 40
    }
}
