package com.example.apktest

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.apktest.ui.GameplayLayout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class GameplayLayoutTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val controlIds = intArrayOf(
        R.id.buttonInertia, R.id.buttonUp, R.id.buttonAuto,
        R.id.buttonLeft, R.id.buttonDown, R.id.buttonRight
    )

    @After
    fun clearSavedGames() {
        GameStateStore(context).clearBlocking()
        AdventureStateStore(context).clearBlocking()
    }

    @Test
    fun bothLayoutsReflowWithoutOverlapAtNarrowWideAndLargeFontSizes() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (layout in intArrayOf(R.layout.activity_main, R.layout.activity_adventure)) {
                    for (fontScale in floatArrayOf(1f, 1.5f)) {
                        val root = fixture(activity, layout, fontScale)
                        val host = root.findViewById<View>(R.id.fragmentGameHost)
                        populateHeaderAndHint(root)
                        for ((width, height) in listOf(320 to 568, 568 to 320, 240 to 480, 800 to 400)) {
                            measure(root, width, height)
                            assertGeometry(root)
                            assertSame(host, root.findViewById<View>(R.id.fragmentGameHost))
                            val params = host.layoutParams as ConstraintLayout.LayoutParams
                            if (width > height) {
                                assertEquals(R.id.bottomControls, params.endToStart)
                            } else {
                                assertEquals(R.id.bottomControls, params.bottomToTop)
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun barsAndCutoutApplyOnceAndAllToggleStatesKeepTheirTouchTargets() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (layout in intArrayOf(R.layout.activity_main, R.layout.activity_adventure)) {
                    val root = fixture(activity, layout)
                    populateHeaderAndHint(root)
                    val left = pixels(root, 28)
                    val top = pixels(root, 24)
                    val right = pixels(root, 12)
                    val bottom = pixels(root, 24)
                    val insets = WindowInsetsCompat.Builder()
                        .setInsets(WindowInsetsCompat.Type.systemBars(), Insets.of(0, top, right, bottom))
                        .setInsets(WindowInsetsCompat.Type.displayCutout(), Insets.of(left, 0, 0, 0))
                        .build()
                    repeat(2) { ViewCompat.dispatchApplyWindowInsets(root, insets) }
                    assertEquals(left, root.paddingLeft)
                    assertEquals(top, root.paddingTop)
                    assertEquals(right, root.paddingRight)
                    assertEquals(bottom, root.paddingBottom)
                    measure(root, 568, 320)
                    assertGeometry(root)
                    val sizeBefore = controlIds.map { id ->
                        root.findViewById<View>(id).let { it.width to it.height }
                    }
                    for (checked in listOf(false, true)) {
                        for (enabled in listOf(false, true)) {
                            for (id in intArrayOf(R.id.buttonInertia, R.id.buttonAuto)) {
                                root.findViewById<ToggleButton>(id).apply {
                                    isChecked = checked
                                    isEnabled = enabled
                                }
                            }
                            measure(root, 568, 320)
                            assertGeometry(root)
                            assertEquals(sizeBefore, controlIds.map { id ->
                                root.findViewById<View>(id).let { it.width to it.height }
                            })
                            val glide = root.findViewById<ToggleButton>(R.id.buttonInertia)
                            assertEquals(root.context.getString(if (checked) {
                                R.string.glide_on
                            } else {
                                R.string.glide_off
                            }), glide.text.toString())
                        }
                    }
                }
            }
        }
    }

    @Test
    fun controlArtworkDistinguishesCheckedPressedFocusedAndDisabled() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = fixture(activity, R.layout.activity_main)
                measure(root, 320, 568)
                val control = root.findViewById<ToggleButton>(R.id.buttonInertia)
                val borderSample = root.resources
                    .getDimensionPixelSize(R.dimen.game_control_border_width)
                fun colors(vararg states: Int): Pair<Int, Int> {
                    val drawable = control.background.constantState!!
                        .newDrawable(root.resources).mutate()
                    drawable.state = states
                    drawable.setBounds(0, 0, control.width, control.height)
                    val bitmap = Bitmap.createBitmap(
                        control.width, control.height, Bitmap.Config.ARGB_8888
                    )
                    drawable.draw(Canvas(bitmap))
                    val result = bitmap.getPixel(control.width / 2, control.height / 2) to
                        bitmap.getPixel(borderSample, control.height / 2)
                    bitmap.recycle()
                    return result
                }
                val normal = colors(android.R.attr.state_enabled)
                val checked = colors(android.R.attr.state_enabled, android.R.attr.state_checked)
                val pressed = colors(android.R.attr.state_enabled, android.R.attr.state_pressed)
                val focused = colors(android.R.attr.state_enabled, android.R.attr.state_focused)
                val disabled = colors()
                assertEquals(root.context.getColor(R.color.maze_toggle_button_background_off), normal.first)
                assertEquals(root.context.getColor(R.color.maze_toggle_button_background_on), checked.first)
                assertEquals(root.context.getColor(R.color.game_control_border_checked), checked.second)
                assertEquals(normal.first, normal.second)
                assertEquals(root.context.getColor(R.color.game_control_pressed), pressed.first)
                assertEquals(root.context.getColor(R.color.game_control_pressed), focused.first)
                assertEquals(root.context.getColor(R.color.maze_toggle_button_background_disabled), disabled.first)
            }
        }
    }

    @Test
    fun fullFontScaleKeepsGlideAndLockedLabelsReadableWithoutShrinkingText() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                for (layout in intArrayOf(R.layout.activity_main, R.layout.activity_adventure)) {
                    val root = fixture(activity, layout, fontScale = 2f)
                    populateHeaderAndHint(root)
                    root.findViewById<ToggleButton>(R.id.buttonAuto).apply {
                        textOff = root.context.getString(R.string.auto_locked)
                        isChecked = false
                        text = textOff
                        isEnabled = false
                    }
                    for ((width, height) in listOf(320 to 568, 568 to 320, 568 to 272)) {
                        measure(root, width, height)
                        assertGeometry(root)
                        assertEssentialHeaderTextIsComplete(root)
                        if (layout == R.layout.activity_adventure) {
                            assertTrue("Maze progress and lives must remain visible together",
                                bounds(root, R.id.gameHeaderViewport)
                                    .contains(bounds(root, R.id.adventureStatusBar)))
                        }
                        if (width > height) {
                            val safeWidth = root.width - root.paddingLeft - root.paddingRight
                            assertTrue("Maze must retain at least half of the safe width",
                                root.findViewById<View>(R.id.fragmentGameHost).width * 2 >= safeWidth)
                            assertTrue("Header must retain a readable side-column width",
                                root.findViewById<View>(R.id.gameHeaderViewport).width * 2 >= safeWidth)
                        }
                        for (id in intArrayOf(R.id.buttonInertia, R.id.buttonAuto)) {
                            val toggle = root.findViewById<ToggleButton>(id)
                            assertEquals(
                                root.resources.getDimension(R.dimen.maze_dpad_button_text_size),
                                toggle.textSize, 0f
                            )
                            val textLayout = toggle.layout
                            assertNotNull(textLayout)
                            assertTrue("Label must fit without vertical clipping",
                                textLayout.height <= toggle.height -
                                    toggle.compoundPaddingTop - toggle.compoundPaddingBottom)
                            assertTrue("Label must fit within the allowed lines",
                                textLayout.lineCount <= toggle.maxLines)
                            assertEquals(toggle.text.length,
                                textLayout.getLineEnd(textLayout.lineCount - 1))
                            assertEquals(0, textLayout.getEllipsisCount(textLayout.lineCount - 1))
                        }
                    }
                }
            }
        }
    }

    @Test
    fun expandedHeaderScrollsInsteadOfTruncatingLivesTotalsOrStreak() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val root = fixture(activity, R.layout.activity_adventure, fontScale = 2f)
                populateHeaderAndHint(root)
                measure(root, 568, 272)
                assertGeometry(root)
                assertEssentialHeaderTextIsComplete(root)
                val viewport = root.findViewById<ScrollView>(R.id.gameHeaderViewport)
                assertTrue("Expanded header must offer scrolling", viewport.canScrollVertically(1))
                viewport.scrollTo(0, root.findViewById<View>(R.id.gameHeader).height)
                assertTrue("The end of the full header must be reachable", viewport.scrollY > 0)
                assertFalse(viewport.canScrollVertically(1))
                assertTrue(viewport.canScrollVertically(-1))
            }
        }
    }

    @Test
    fun setupThemesRetainActionBarAndExistingBackNavigation() {
        ActivityScenario.launch(SetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.supportActionBar)
                activity.findViewById<View>(R.id.buttonClassicMode).performClick()
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(View.VISIBLE,
                    activity.findViewById<View>(R.id.startMenuRootButtonColumn).visibility)
                assertFalse(activity.isFinishing)
            }
        }
        ActivityScenario.launch(AdventureSetupActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertNotNull(activity.supportActionBar)
                activity.findViewById<View>(R.id.buttonAdventureBack).performClick()
                assertTrue(activity.isFinishing)
            }
        }
    }

    @Test
    fun classicAndAdventureRotateInPlaceWithTheSameFragmentAndNoActionBar() {
        verifyRotation(MainActivity::class.java, R.id.root)
        verifyRotation(AdventureActivity::class.java, R.id.adventureRoot)
    }

    private fun <T : AppCompatActivity> verifyRotation(activityClass: Class<T>, rootId: Int) {
        ActivityScenario.launch<T>(Intent(context, activityClass)).use { scenario ->
            lateinit var originalActivity: T
            lateinit var originalHost: View
            lateinit var originalFragment: Fragment
            await(scenario) { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                activity.supportFragmentManager.findFragmentById(R.id.fragmentGameHost) != null &&
                    activity.findViewById<View>(R.id.fragmentGameHost).height > 0
            }
            scenario.onActivity { activity ->
                originalActivity = activity
                originalHost = activity.findViewById(R.id.fragmentGameHost)
                originalFragment = activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentGameHost)!!
                assertNull(activity.supportActionBar)
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            await(scenario) { activity ->
                val root = activity.findViewById<ConstraintLayout>(rootId)
                root.width > root.height && !root.isLayoutRequested &&
                    (originalHost.layoutParams as ConstraintLayout.LayoutParams)
                        .endToStart == R.id.bottomControls
            }
            scenario.onActivity { activity ->
                assertSame(originalActivity, activity)
                assertSame(originalHost, activity.findViewById<View>(R.id.fragmentGameHost))
                assertSame(originalFragment, activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentGameHost))
                assertGeometry(activity.findViewById(rootId))
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
            await(scenario) { activity ->
                val root = activity.findViewById<ConstraintLayout>(rootId)
                root.height > root.width && !root.isLayoutRequested &&
                    (originalHost.layoutParams as ConstraintLayout.LayoutParams)
                        .bottomToTop == R.id.bottomControls
            }
            scenario.onActivity { activity ->
                assertSame(originalActivity, activity)
                assertSame(originalFragment, activity.supportFragmentManager
                    .findFragmentById(R.id.fragmentGameHost))
                assertGeometry(activity.findViewById(rootId))
            }
        }
    }

    private fun fixture(
        activity: AppCompatActivity,
        layout: Int,
        fontScale: Float = 1f
    ): ConstraintLayout {
        val configuration = Configuration(context.resources.configuration).apply {
            this.fontScale = fontScale
        }
        // Use the application inflater so the fixture cannot adopt the activity's live Fragment.
        val themed = ContextThemeWrapper(
            context.createConfigurationContext(configuration), R.style.Theme_ApkTest_Gameplay
        )
        return (LayoutInflater.from(themed).inflate(layout, null) as ConstraintLayout).also {
            GameplayLayout.bind(activity, it)
        }
    }

    private fun populateHeaderAndHint(root: ConstraintLayout) {
        root.findViewById<TextView>(R.id.adventureStatusBar)?.text =
            root.context.getString(R.string.adventure_hud_primary, 7, 9, 3)
        root.findViewById<TextView>(R.id.adventureStats)?.text =
            root.context.getString(R.string.adventure_hud_completed, "12:34", 1234)
        root.findViewById<TextView>(R.id.adventureStreak)?.text =
            root.context.getString(R.string.adventure_hud_streak, 2, 3)
        root.findViewById<TextView>(R.id.adventurePerkStatusBar)?.apply {
            text = "Quick Feet ×2 · Second Wind ready"
            visibility = View.VISIBLE
        }
        root.findViewById<TextView>(R.id.controlHint).apply {
            setText(R.string.auto_locked_hint)
            visibility = View.VISIBLE
        }
    }

    private fun measure(root: ConstraintLayout, widthDp: Int, heightDp: Int) {
        val width = pixels(root, widthDp)
        val height = pixels(root, heightDp)
        // First measure exposes wrapped header/hint heights; the next settles their constraints.
        repeat(4) {
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, width, height)
            root.viewTreeObserver.dispatchOnGlobalLayout()
        }
    }

    private fun assertGeometry(root: ConstraintLayout) {
        val host = bounds(root, R.id.fragmentGameHost)
        val header = bounds(root, R.id.gameHeaderViewport)
        val headerContent = bounds(root, R.id.gameHeader)
        val menu = bounds(root, R.id.buttonMenu)
        val controls = bounds(root, R.id.bottomControls)
        val safe = Rect(root.paddingLeft, root.paddingTop,
            root.width - root.paddingRight, root.height - root.paddingBottom)
        assertTrue("Maze must remain visible: $host", host.width() > 0 && host.height() > 0)
        assertTrue("Header must fit safe area", safe.contains(header))
        assertTrue("Maze must fit safe area: $safe, $host", safe.contains(host))
        assertTrue("Controls must fit safe area: $safe, $controls", safe.contains(controls))
        assertTrue("Menu must stay in reserved header", header.contains(menu))
        assertFalse("Header cannot cover maze", Rect.intersects(header, host))
        assertFalse("Menu cannot cover maze", Rect.intersects(menu, host))
        assertFalse("Controls cannot cover maze", Rect.intersects(controls, host))
        assertFalse("Controls cannot cover header", Rect.intersects(controls, header))
        val minimum = root.resources.getDimensionPixelSize(R.dimen.game_touch_target_min)
        assertTrue(menu.width() >= minimum && menu.height() >= minimum)
        val targets = controlIds.map { id ->
            val target = root.findViewById<View>(id)
            assertTrue("$id must retain a 48dp touch target",
                target.width >= minimum && target.height >= minimum)
            assertNotNull(target.contentDescription)
            assertTrue(controls.contains(bounds(root, id)))
            if (id != R.id.buttonInertia && id != R.id.buttonAuto) {
                assertTrue(target is ImageButton)
                assertEquals(0f, target.rotation, 0f)
                assertNotNull((target as ImageButton).drawable)
            }
            bounds(root, id)
        }
        for (row in listOf(targets.take(3), targets.drop(3))) {
            assertEquals(row[0].top, row[1].top)
            assertEquals(row[1].top, row[2].top)
            assertTrue(row[0].right <= row[1].left && row[1].right <= row[2].left)
        }
        assertTrue(targets[0].bottom <= targets[3].top)
        assertTrue(targets[3].bottom <= bounds(root, R.id.controlHint).top ||
            root.findViewById<View>(R.id.controlHint).visibility == View.GONE)
        val statusIds = intArrayOf(
            R.id.gameplayTitle, R.id.adventureStatusBar, R.id.adventureStats,
            R.id.adventureStreak, R.id.adventurePerkStatusBar
        )
        statusIds.forEach { id ->
            val view = root.findViewById<View>(id)
            if (view != null && view.visibility == View.VISIBLE) {
                assertTrue("Status must stay in scrollable header content",
                    headerContent.contains(bounds(root, id)))
                assertFalse("Status cannot cover menu", Rect.intersects(bounds(root, id), menu))
            }
        }
    }

    private fun assertEssentialHeaderTextIsComplete(root: ConstraintLayout) {
        for (id in intArrayOf(R.id.gameplayTitle, R.id.adventureStatusBar,
            R.id.adventureStats, R.id.adventureStreak)
        ) {
            val text = root.findViewById<TextView>(id) ?: continue
            val layout = text.layout
            assertNotNull(layout)
            assertNull("Essential HUD text must not ellipsize", text.ellipsize)
            assertTrue("Essential HUD text must fit its measured view",
                layout.height <= text.height - text.compoundPaddingTop - text.compoundPaddingBottom)
            assertEquals(text.text.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) {
                assertEquals(0, layout.getEllipsisCount(line))
            }
        }
    }

    private fun bounds(root: ConstraintLayout, id: Int): Rect {
        val view = root.findViewById<View>(id)
        val rect = Rect(0, 0, view.width, view.height)
        root.offsetDescendantRectToMyCoords(view, rect)
        return rect
    }

    private fun pixels(root: View, dp: Int) =
        (dp * root.resources.displayMetrics.density).roundToInt()

    private fun <T : AppCompatActivity> await(scenario: ActivityScenario<T>, condition: (T) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 8_000L
        do {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(40L)
            instrumentation.waitForIdleSync()
        } while (SystemClock.uptimeMillis() < deadline)
        throw AssertionError("Gameplay layout did not settle before timeout")
    }
}
