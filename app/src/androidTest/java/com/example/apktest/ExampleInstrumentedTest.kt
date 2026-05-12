package com.example.apktest

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.BuildConfig
import com.example.apktest.game.GameFragment
import java.util.concurrent.atomic.AtomicInteger
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
                    R.id.buttonPause,
                    R.id.buttonRestart,
                    R.id.buttonLegend,
                    R.id.buttonBackToSetup,
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
            val initialSwipes = AtomicInteger(0)
            // Wait for the game host to be laid out before dispatching swipes; on slower devices
            // the host can be attached but not yet measured immediately after pending transactions.
            waitForGameHostLaidOut(scenario)

            // Capture host bounds once on the UI thread; then dispatch each swipe in its own
            // onActivity block (with idle-syncs in between) so the velocity tracker sees each
            // fling as an independent gesture even on slow emulators.
            val centerX = AtomicInteger(0)
            val centerY = AtomicInteger(0)
            val horizontalSpan = AtomicInteger(0)
            val verticalSpan = AtomicInteger(0)
            scenario.onActivity { activity ->
                val gameHost = activity.findViewById<android.view.View>(R.id.fragmentGameHost)
                assertNotNull("Game host should be present", gameHost)
                attachedGameFragment(activity)
                assertTrue(
                    "Game host should be laid out before dispatching swipes",
                    gameHost.width > 0 && gameHost.height > 0
                )
                initialSwipes.set(activity.resolvedSwipeCount)

                val location = IntArray(2)
                gameHost.getLocationInWindow(location)
                centerX.set(location[0] + gameHost.width / 2)
                centerY.set(location[1] + gameHost.height / 2)
                horizontalSpan.set((gameHost.width * SWIPE_REL_SPAN).toInt())
                verticalSpan.set((gameHost.height * SWIPE_REL_SPAN).toInt())
            }

            val cx = centerX.get().toFloat()
            val cy = centerY.get().toFloat()
            val hs = horizontalSpan.get().toFloat()
            val vs = verticalSpan.get().toFloat()
            // Each swipe is dispatched in its own UI-thread frame and followed by an idle-sync,
            // so the GestureDetector / VelocityTracker can finish processing the previous fling
            // before the next ACTION_DOWN arrives.
            dispatchSwipeOnUiThread(scenario, cx, cy + vs / 2f, cx, cy - vs / 2f)
            dispatchSwipeOnUiThread(scenario, cx, cy - vs / 2f, cx, cy + vs / 2f)
            dispatchSwipeOnUiThread(scenario, cx + hs / 2f, cy, cx - hs / 2f, cy)
            dispatchSwipeOnUiThread(scenario, cx - hs / 2f, cy, cx + hs / 2f, cy)

            val finalSwipes = pollResolvedSwipes(scenario, initialSwipes.get())
            assertTrue(
                "At least one swipe should be resolved by the gesture detector",
                finalSwipes > initialSwipes.get()
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

    private fun dispatchSwipeOnUiThread(
        scenario: ActivityScenario<MainActivity>,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        scenario.onActivity { activity ->
            dispatchSwipe(activity, startX, startY, endX, endY)
        }
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }

    @Test
    fun mainActivity_swipeOnOverlayDoesNotTriggerMovement() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForGameHostLaidOut(scenario)
            val baseline = AtomicInteger(0)
            scenario.onActivity { activity ->
                attachedGameFragment(activity)
                baseline.set(activity.resolvedSwipeCount)
                // Dispatch swipes that start inside the top and bottom overlay regions; these
                // sit on top of fragmentGameHost in the layout, so without overlay-aware hit
                // testing they would otherwise be forwarded to the swipe gesture detector.
                dispatchSwipeInsideView(activity, R.id.topOverlay)
                dispatchSwipeInsideView(activity, R.id.bottomControls)
            }
            // Give the gesture detector a chance to (incorrectly) resolve swipes; if the
            // hit-test exclusion is correct, resolvedSwipeCount must remain unchanged.
            SystemClock.sleep(STEP_POLL_INTERVAL_MS * 4)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                assertEquals(
                    "Swipes starting on overlay UI must not trigger player moves",
                    baseline.get(),
                    activity.resolvedSwipeCount
                )
            }
        }
    }

    private fun dispatchSwipeInsideView(activity: MainActivity, viewId: Int) {
        val view = activity.findViewById<android.view.View>(viewId) ?: return
        if (view.width <= 0 || view.height <= 0) return
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val originX = location[0].toFloat()
        val originY = location[1].toFloat()
        val width = view.width.toFloat()
        val height = view.height.toFloat()
        val centerX = originX + width / 2f
        val centerY = originY + height / 2f
        // Use a smaller span clamped to the overlay's size so the entire swipe stays inside
        // the overlay region.
        val span = minOf(width, height) * 0.4f
        dispatchSwipe(activity, centerX - span / 2f, centerY, centerX + span / 2f, centerY)
        dispatchSwipe(activity, centerX, centerY - span / 2f, centerX, centerY + span / 2f)
    }

    private fun attachedGameFragment(activity: MainActivity): GameFragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
        return requireNotNull(fragment) { "Game fragment should be attached" }
    }

    private fun waitForGameHostLaidOut(scenario: ActivityScenario<MainActivity>) {
        var laidOut = false
        var attempts = 0
        while (attempts < MAX_LAYOUT_POLL_ATTEMPTS && !laidOut) {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val gameHost = activity.findViewById<android.view.View>(R.id.fragmentGameHost)
                laidOut = gameHost != null &&
                    androidx.core.view.ViewCompat.isLaidOut(gameHost) &&
                    gameHost.width > 0 &&
                    gameHost.height > 0
            }
            if (!laidOut) {
                SystemClock.sleep(LAYOUT_POLL_INTERVAL_MS)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            attempts++
        }
        assertTrue("Game host should be laid out within timeout", laidOut)
    }

    private fun pollResolvedSwipes(
        scenario: ActivityScenario<MainActivity>,
        initial: Int
    ): Int {
        var resolved = initial
        var attempts = 0
        while (attempts < MAX_STEP_POLL_ATTEMPTS && resolved <= initial) {
            scenario.onActivity { activity ->
                resolved = activity.resolvedSwipeCount
            }
            attempts++
            if (resolved <= initial && attempts < MAX_STEP_POLL_ATTEMPTS) {
                SystemClock.sleep(STEP_POLL_INTERVAL_MS)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
        }
        return resolved
    }

    private fun dispatchSwipe(
        activity: android.app.Activity,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val downTime = SystemClock.uptimeMillis()
        val events = mutableListOf<MotionEvent>()
        events += MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0)
        // Multiple intermediate move events spread over time so VelocityTracker can compute a fling.
        val moveSteps = 5
        val totalDurationMs = 100L
        for (i in 1..moveSteps) {
            val fraction = i.toFloat() / (moveSteps + 1)
            val x = startX + (endX - startX) * fraction
            val y = startY + (endY - startY) * fraction
            val eventTime = downTime + (totalDurationMs * fraction).toLong()
            events += MotionEvent.obtain(downTime, eventTime, MotionEvent.ACTION_MOVE, x, y, 0)
        }
        events += MotionEvent.obtain(
            downTime,
            downTime + totalDurationMs,
            MotionEvent.ACTION_UP,
            endX,
            endY,
            0
        )
        try {
            for (event in events) {
                activity.dispatchTouchEvent(event)
            }
        } finally {
            events.forEach { it.recycle() }
        }
    }

    companion object {
        // 40 attempts with up to 39 idle-sync waits provides a bounded but generous upper limit.
        private const val MAX_STEP_POLL_ATTEMPTS = 40
        private const val STEP_POLL_INTERVAL_MS = 50L
        // Swipe spans 60% of the host's measured dimension along the swipe axis.
        private const val SWIPE_REL_SPAN = 0.6f
        private const val MAX_LAYOUT_POLL_ATTEMPTS = 40
        private const val LAYOUT_POLL_INTERVAL_MS = 50L
    }
}
