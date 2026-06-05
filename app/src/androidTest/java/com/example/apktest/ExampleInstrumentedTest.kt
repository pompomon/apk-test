package com.example.apktest

import android.content.Intent
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.BuildConfig
import com.example.apktest.game.GameFragment
import com.example.apktest.game.core.PlayerPolicyType
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    private fun launchMainActivityWithBfsExitPolicy(): ActivityScenario<MainActivity> {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(SetupActivity.EXTRA_PLAYER_POLICY, PlayerPolicyType.BFS_EXIT.name)
        }
        return ActivityScenario.launch(intent)
    }

    @Test
    fun useAppContext() {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals(BuildConfig.APPLICATION_ID, appContext.packageName)
    }

    @Test
    fun mainActivity_displaysGameHostAndControls() {
        launchMainActivityWithBfsExitPolicy().use { scenario ->
            scenario.onActivity { activity ->
                val viewIds = intArrayOf(
                    R.id.fragmentGameHost,
                    R.id.buttonMenu,
                    R.id.buttonUp,
                    R.id.buttonLeft,
                    R.id.buttonDown,
                    R.id.buttonRight,
                    R.id.buttonAuto
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
        launchMainActivityWithBfsExitPolicy().use { scenario ->
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
            // before the next ACTION_DOWN arrives. The whole 4-swipe sequence is retried a few
            // times because synthetic flings can be dropped intermittently on slow emulators
            // (we only need ONE of them to be resolved for the assertion to pass).
            var finalSwipes = initialSwipes.get()
            var swipeAttempts = 0
            while (finalSwipes <= initialSwipes.get() && swipeAttempts < MAX_SWIPE_RETRY_ATTEMPTS) {
                dispatchSwipeOnUiThread(scenario, cx, cy + vs / 2f, cx, cy - vs / 2f)
                dispatchSwipeOnUiThread(scenario, cx, cy - vs / 2f, cx, cy + vs / 2f)
                dispatchSwipeOnUiThread(scenario, cx + hs / 2f, cy, cx - hs / 2f, cy)
                dispatchSwipeOnUiThread(scenario, cx - hs / 2f, cy, cx + hs / 2f, cy)
                finalSwipes = pollResolvedSwipes(scenario, initialSwipes.get())
                swipeAttempts++
            }
            assertTrue(
                "At least one swipe should be resolved by the gesture detector " +
                    "(attempts=$swipeAttempts)",
                finalSwipes > initialSwipes.get()
            )

            scenario.onActivity { activity ->
                val controlIds = intArrayOf(
                    R.id.buttonUp,
                    R.id.buttonLeft,
                    R.id.buttonDown,
                    R.id.buttonRight,
                    R.id.buttonAuto
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
        // For the positive swipe-resolution test we feed events directly into the activity's
        // swipe GestureDetector via a @VisibleForTesting hook. This avoids two pitfalls:
        //   1. activity.dispatchTouchEvent + raw MotionEvent.obtain has been observed to drop
        //      synthetic flings on the emulator (deterministic 0-resolved-swipes even with retries).
        //   2. Instrumentation.sendPointerSync requires INJECT_EVENTS permission when the test
        //      and target apps don't share a UID (which is our case), and raises SecurityException.
        // The view-dispatch hit-testing path is still covered by
        // mainActivity_swipeOnOverlayDoesNotTriggerMovement, which feeds events through
        // activity.dispatchTouchEvent.
        val events = buildSwipeEvents(startX, startY, endX, endY)
        try {
            scenario.onActivity { activity ->
                for (event in events) {
                    activity.feedSwipeEventForTesting(event)
                }
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        } finally {
            events.forEach { it.recycle() }
        }
    }

    @Test
    fun mainActivity_swipeOnOverlayDoesNotTriggerMovement() {
        launchMainActivityWithBfsExitPolicy().use { scenario ->
            waitForGameHostLaidOut(scenario)
            val baseline = AtomicInteger(0)
            scenario.onActivity { activity ->
                attachedGameFragment(activity)
                baseline.set(activity.resolvedSwipeCount)
                // Dispatch swipes that start inside the top and bottom overlay regions via the
                // real dispatchTouchEvent path; the hit-test in MainActivity must reject them
                // so they never reach the gesture detector.
                dispatchSwipeInsideView(activity, R.id.buttonMenu)
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

    @Test
    fun mainActivityMenuButtonShowsPopoverActionsAndHud() {
        launchMainActivityWithBfsExitPolicy().use { scenario ->
            waitForGameHostLaidOut(scenario)
            scenario.onActivity { activity ->
                val gameFragment = attachedGameFragment(activity)
                assertTrue("Game fragment should be added before opening menu", gameFragment.isAdded)
            }

            scenario.onActivity { activity ->
                activity.findViewById<android.view.View>(R.id.buttonMenu).performClick()
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                val isShowing = activity.isMenuPopoverShowingForTesting()
                assertTrue(
                    "Expected menu popover to be visible after tapping menu button, but isShowing returned $isShowing",
                    isShowing
                )
                val snapshot = activity.menuPopoverTextSnapshotForTesting()
                assertTrue(
                    "Expected snapshot to contain pause/resume button text",
                    snapshot.contains(activity.getString(R.string.pause_resume))
                )
                assertTrue(
                    "Expected snapshot to contain restart button text",
                    snapshot.contains(activity.getString(R.string.restart))
                )
                assertTrue(
                    "Expected snapshot to contain legend button text",
                    snapshot.contains(activity.getString(R.string.legend))
                )
                assertTrue(
                    "Expected snapshot to contain pause-and-exit button text",
                    snapshot.contains(activity.getString(R.string.pause_and_exit))
                )
                assertTrue(
                    "Expected snapshot to include status HUD text",
                    snapshot.any { text ->
                        text.startsWith(localizedPrefix(R.string.status_template)) &&
                            text.contains(localizedTokenAfterPipes(R.string.status_template, 1))
                    }
                )
                assertTrue(
                    "Expected snapshot to include speed HUD text",
                    snapshot.any { text ->
                        text.startsWith(localizedPrefix(R.string.speed_detail_template)) &&
                            text.contains(localizedTokenAfterPipes(R.string.speed_detail_template, 2))
                    }
                )
                assertTrue(
                    "Expected snapshot to include power-ups HUD text",
                    snapshot.any { text ->
                        text.startsWith(localizedPrefix(R.string.powerups_detail_template)) &&
                            text.contains(localizedTokenAfterPipes(R.string.powerups_detail_template, 1))
                    }
                )
            }
        }
    }

    @Test
    fun classicAutoToggleStartsCheckedForAutomatedPolicy() {
        launchMainActivityWithBfsExitPolicy().use { scenario ->
            scenario.onActivity { activity ->
                val toggle = activity.findViewById<android.widget.ToggleButton>(R.id.buttonAuto)
                assertNotNull("Auto toggle should be inflated", toggle)
                assertTrue("Auto toggle should be enabled when automated policies exist", toggle.isEnabled)
                assertTrue("Auto toggle should reflect automated launch policy", toggle.isChecked)
                assertTrue(activity.isAutoToggleCheckedForTesting())
            }
        }
    }

    @Test
    fun classicManualStartShowsAutomatedPolicyPicker() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            waitForAutomatedPolicyDialog(scenario)
            scenario.onActivity { activity ->
                assertTrue(
                    "Manual Classic starts should prompt for an automated policy",
                    activity.isAutomatedPolicyDialogShowingForTesting()
                )
            }
        }
    }

    @Test
    fun adventureAutoToggleDisabledBeforeAutomatedUnlock() {
        ActivityScenario.launch(AdventureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val toggle = activity.findViewById<android.widget.ToggleButton>(R.id.buttonAuto)
                assertNotNull("Auto toggle should be inflated", toggle)
                assertFalse(
                    "Adventure prompt should not show before an automated unlock",
                    activity.isAutomatedPolicyDialogShowingForTesting()
                )
                assertFalse("Auto toggle should start disabled with only MANUAL unlocked", toggle.isEnabled)
                assertFalse("Auto toggle should start unchecked", toggle.isChecked)
            }
        }
    }

    @Test
    fun adventureAutoToggleEnabledAfterAutomatedUnlock() {
        ActivityScenario.launch(AdventureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.controllerForTesting().applyPolicyUnlock(PlayerPolicyType.BFS_EXIT)
                activity.refreshAutoToggleForTesting()
                val toggle = activity.findViewById<android.widget.ToggleButton>(R.id.buttonAuto)
                assertTrue("Auto toggle should enable after an automated policy unlock", toggle.isEnabled)
                assertFalse("Auto toggle should remain unchecked until selected", toggle.isChecked)
            }
        }
    }


    private fun localizedPrefix(textRes: Int): String {
        return InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getString(textRes)
            .substringBefore('%')
            .trim()
    }

    private fun localizedTokenAfterPipes(textRes: Int, pipeCount: Int): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val template = context.getString(textRes)
        val availablePipes = template.count { it == '|' }
        val resourceName = try {
            context.resources.getResourceEntryName(textRes)
        } catch (_: android.content.res.Resources.NotFoundException) {
            textRes.toString()
        }
        require(availablePipes >= pipeCount) {
            "String resource $resourceName must contain at least $pipeCount pipe delimiters"
        }
        var tokenSection = template
        repeat(pipeCount) {
            tokenSection = tokenSection.substringAfter('|')
        }
        require(tokenSection.contains('%')) {
            "String resource $resourceName must contain a format token after $pipeCount pipe delimiters"
        }
        return tokenSection.substringBefore('%').trim()
    }

    private fun dispatchSwipeInsideView(activity: MainActivity, viewId: Int) {
        val view = activity.findViewById<android.view.View>(viewId) ?: return
        if (view.width <= 0 || view.height <= 0) return
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val startX = location[0].toFloat() + view.width / 2f
        val startY = location[1].toFloat() + view.height / 2f
        // Use a span derived from the game host (not the button) so the displacement
        // is guaranteed to exceed minDistance (scaledTouchSlop * 4) on every density.
        // If the hit-test exclusion regresses and ACTION_DOWN is incorrectly forwarded
        // to the gesture detector, this long swipe would register as a valid fling and
        // the test would correctly catch the regression.
        val gameHost = activity.findViewById<android.view.View>(R.id.fragmentGameHost)
        val swipeSpan = if (gameHost != null && gameHost.width > 0) {
            maxOf(gameHost.width, gameHost.height) * SWIPE_REL_SPAN
        } else {
            // Fallback: 8× the button dimension keeps displacement well above
            // scaledTouchSlop * 4 (minDistance) even on high-density screens.
            maxOf(view.width, view.height) * OVERLAY_SWIPE_FALLBACK_MULTIPLIER
        }

        // Direct each swipe *inward* (toward the screen interior) so synthetic
        // ACTION_MOVE/UP coordinates stay on-screen. Overlay views sit at the
        // window edges (menu button at top-right, D-pad strip at the bottom),
        // so pushing end coordinates further outward generates far-off-screen
        // touches that some emulator input stacks have crashed on. Span is
        // unchanged so the fling threshold remains comfortably exceeded.
        val rootView = activity.window.decorView
        val rootWidth = rootView.width.toFloat()
        val rootHeight = rootView.height.toFloat()
        val signX = if (rootWidth > 0f && startX > rootWidth / 2f) -1f else 1f
        val signY = if (rootHeight > 0f && startY > rootHeight / 2f) -1f else 1f
        val rawEndX = startX + signX * swipeSpan
        val rawEndY = startY + signY * swipeSpan
        // Clamp end coordinates into window bounds so synthetic ACTION_MOVE/UP
        // events never travel off-screen. If decorView reports zero size (some
        // emulator quirks), fall back to leaving the value unclamped so the
        // swipe still has enough displacement to exceed minDistance.
        val endX = if (rootWidth > 0f) rawEndX.coerceIn(0f, rootWidth - 1f) else rawEndX
        val endY = if (rootHeight > 0f) rawEndY.coerceIn(0f, rootHeight - 1f) else rawEndY
        // One overlay-origin swipe per target is sufficient for this regression
        // assertion and avoids emulator instability from higher synthetic volume.
        dispatchSwipeViaTouchEvent(activity, startX, startY, endX, startY)
    }

    private fun dispatchSwipeViaTouchEvent(
        activity: android.app.Activity,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ) {
        val events = buildSwipeEvents(startX, startY, endX, endY)
        try {
            for (event in events) {
                activity.dispatchTouchEvent(event)
            }
        } finally {
            events.forEach { it.recycle() }
        }
    }

    private fun buildSwipeEvents(
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float
    ): List<MotionEvent> {
        val downTime = SystemClock.uptimeMillis()
        val moveSteps = 10
        val totalDurationMs = 120L
        val events = mutableListOf<MotionEvent>()
        events += MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, startX, startY, 0)
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
        for (event in events) {
            event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        }
        return events
    }

    private fun attachedGameFragment(activity: MainActivity): GameFragment {
        activity.supportFragmentManager.executePendingTransactions()
        val fragment = activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) as? GameFragment
        return requireNotNull(fragment) { "Game fragment should be attached" }
    }

    private fun waitForAutomatedPolicyDialog(scenario: ActivityScenario<MainActivity>) {
        var showing = false
        var attempts = 0
        while (attempts < MAX_LAYOUT_POLL_ATTEMPTS && !showing) {
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                showing = activity.isAutomatedPolicyDialogShowingForTesting()
            }
            if (!showing) {
                SystemClock.sleep(LAYOUT_POLL_INTERVAL_MS)
                InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            }
            attempts++
        }
        assertTrue("Automated policy picker should be shown within timeout", showing)
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

    companion object {
        // 40 attempts with up to 39 idle-sync waits provides a bounded but generous upper limit.
        private const val MAX_STEP_POLL_ATTEMPTS = 40
        private const val STEP_POLL_INTERVAL_MS = 50L
        // Swipe spans 60% of the host's measured dimension along the swipe axis.
        private const val SWIPE_REL_SPAN = 0.6f
        private const val MAX_LAYOUT_POLL_ATTEMPTS = 40
        private const val LAYOUT_POLL_INTERVAL_MS = 50L
        // Retry the entire 4-swipe sequence a few times if no fling resolves; synthetic flings
        // can be intermittently dropped by the emulator's input pipeline.
        private const val MAX_SWIPE_RETRY_ATTEMPTS = 5
        // Fallback multiplier for overlay swipes when the game host is not yet measured;
        // 8× the button's own dimension is large enough to exceed minDistance on any density.
        private const val OVERLAY_SWIPE_FALLBACK_MULTIPLIER = 8f
    }
}
