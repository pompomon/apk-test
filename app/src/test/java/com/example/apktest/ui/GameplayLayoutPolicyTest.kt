package com.example.apktest.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameplayLayoutPolicyTest {
    private val dimensions = GameplayLayoutDimensions(
        touchTarget = 48,
        buttonWidth = 84,
        buttonHeight = 56,
        buttonGap = 6,
        rowGap = 6,
        controlsPadding = 6,
        margin = 8,
        shortBodyHeight = 480,
        mazeMinWidth = 200,
        mazeMinHeight = 160
    )

    @Test
    fun narrowPortraitFitsAllThreeColumnsWithoutShrinkingTouchTargets() {
        val area = GameplaySafeArea(240, 480)
        val plan = GameplayLayoutPolicy.calculate(area, 80, 40, dimensions)
        assertFalse(plan.sideBySide)
        assertTrue(plan.controlsWidth + dimensions.margin * 2 <= area.width)
        assertTrue(plan.buttonWidth >= dimensions.touchTarget)
        assertTrue(plan.buttonHeight >= dimensions.touchTarget)
    }

    @Test
    fun shortWideWindowReservesFullMazeHeightBesideControls() {
        val area = GameplaySafeArea(568, 320)
        val plan = GameplayLayoutPolicy.calculate(area, 100, 36, dimensions)
        assertTrue(plan.sideBySide)
        assertTrue(plan.controlsWidth + dimensions.mazeMinWidth + dimensions.margin * 3 <= area.width)
        assertTrue(plan.buttonHeight * 2 + dimensions.controlsPadding * 2 +
            dimensions.rowGap + 36 + dimensions.margin * 2 <= area.height - 100)
    }

    @Test
    fun rotatingSameAvailableAreaReversesLayoutDecision() {
        val portrait = GameplayLayoutPolicy.calculate(GameplaySafeArea(320, 568), 80, 0, dimensions)
        val landscape = GameplayLayoutPolicy.calculate(GameplaySafeArea(568, 320), 80, 0, dimensions)
        val portraitAgain = GameplayLayoutPolicy.calculate(GameplaySafeArea(320, 568), 80, 0, dimensions)
        assertFalse(portrait.sideBySide)
        assertTrue(landscape.sideBySide)
        assertEquals(portrait, portraitAgain)
    }

    @Test
    fun wideButTallWindowKeepsControlsBelowMaze() {
        val plan = GameplayLayoutPolicy.calculate(GameplaySafeArea(1000, 800), 80, 0, dimensions)
        assertFalse(plan.sideBySide)
    }

    @Test
    fun sideLayoutNeedsRoomForMazeAndThreeMinimumTargets() {
        val plan = GameplayLayoutPolicy.calculate(GameplaySafeArea(390, 300), 80, 0, dimensions)
        assertFalse(plan.sideBySide)
        assertTrue(plan.controlsWidth + dimensions.margin * 2 <= 390)
    }

    @Test
    fun safeAreaSubtractsAllSystemEdgesAndCutoutBeforeChoosingLayout() {
        val area = GameplayLayoutPolicy.safeArea(568, 320, 128, 24, 64, 24)
        assertEquals(GameplaySafeArea(376, 272), area)
        assertFalse(GameplayLayoutPolicy.calculate(area, 80, 0, dimensions).sideBySide)
    }

    @Test
    fun shrinkingHeightAndShowingHintNeverMakesTargetsSmallerThan48() {
        val normal = GameplayLayoutPolicy.calculate(GameplaySafeArea(320, 480), 100, 0, dimensions)
        val cramped = GameplayLayoutPolicy.calculate(GameplaySafeArea(320, 300), 100, 60, dimensions)
        assertTrue(cramped.buttonHeight <= normal.buttonHeight)
        assertEquals(48, cramped.buttonHeight)
        assertEquals(normal.buttonWidth, cramped.buttonWidth)
    }

    @Test
    fun resourceChangesRecomputeButtonSizesAndPanelWidth() {
        val area = GameplaySafeArea(800, 400)
        val regular = GameplayLayoutPolicy.calculate(area, 80, 0, dimensions)
        val larger = GameplayLayoutPolicy.calculate(area, 80, 0, dimensions.copy(
            buttonWidth = 96, buttonHeight = 60, buttonGap = 8, controlsPadding = 8
        ))
        assertEquals(96, larger.buttonWidth)
        assertEquals(60, larger.buttonHeight)
        assertTrue(larger.controlsWidth > regular.controlsWidth)
    }

    @Test
    fun safeAreaCannotBecomeNegativeDuringWindowTransition() {
        assertEquals(GameplaySafeArea(0, 0),
            GameplayLayoutPolicy.safeArea(0, 0, 48, 24, 48, 24))
    }

    @Test
    fun largeFontLabelsKeepTheirRequiredHeightInsteadOfShrinkingForMazePreference() {
        val plan = GameplayLayoutPolicy.calculate(
            GameplaySafeArea(320, 568), 174, 62,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84)
        )
        assertFalse(plan.sideBySide)
        assertEquals(84, plan.buttonHeight)
    }

    @Test
    fun largeFontLandscapeUsesFullHeightControlsBesideReservedHeaderAndMaze() {
        val area = GameplaySafeArea(568, 272)
        val hintHeight = 62
        val plan = GameplayLayoutPolicy.calculate(area, 174, hintHeight,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84))
        assertTrue(plan.sideBySide)
        assertTrue(plan.controlsAlongsideHeader)
        assertEquals(84, plan.buttonHeight)
        assertTrue(plan.buttonHeight * 2 + dimensions.controlsPadding * 2 +
            dimensions.rowGap + hintHeight + dimensions.margin * 2 <= area.height)
    }

    @Test
    fun normalFontRestoresFullWidthHeaderAndControlsBelowIt() {
        val area = GameplaySafeArea(568, 320)
        val largeFont = GameplayLayoutPolicy.calculate(area, 174, 62,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84))
        val normalFont = GameplayLayoutPolicy.calculate(area, 80, 24, dimensions)
        assertTrue(largeFont.controlsAlongsideHeader)
        assertFalse(normalFont.controlsAlongsideHeader)
    }

    @Test
    fun oversizedPortraitHeaderReservesMazeAndLabelsRatherThanCoveringThem() {
        val area = GameplaySafeArea(320, 568)
        val hintHeight = 62
        val plan = GameplayLayoutPolicy.calculate(area, 1_000, hintHeight,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84))
        val controlsHeight = plan.buttonHeight * 2 + dimensions.controlsPadding * 2 +
            dimensions.rowGap + hintHeight
        assertTrue(plan.headerViewportHeight < 1_000)
        assertTrue(plan.headerViewportHeight >= dimensions.headerMinHeight)
        assertTrue(plan.headerViewportHeight + controlsHeight + dimensions.margin * 3 +
            dimensions.mazeMinHeight <= area.height)
    }

    @Test
    fun overflowingLandscapeHeaderKeepsMazeVisibleAlongsideLargeControls() {
        val area = GameplaySafeArea(568, 272)
        val plan = GameplayLayoutPolicy.calculate(area, 1_000, 62,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84))
        assertTrue(plan.controlsAlongsideHeader)
        assertTrue(plan.headerViewportHeight + dimensions.mazeMinHeight +
            dimensions.margin * 2 <= area.height)
    }

    @Test
    fun oversizedLocalizedLabelsCannotTakeTheHeaderAndMazesHalfOfSafeWidth() {
        for (area in listOf(GameplaySafeArea(568, 320), GameplaySafeArea(568, 272),
            GameplaySafeArea(569, 320))
        ) {
            val plan = GameplayLayoutPolicy.calculate(area, 1_000, 62,
                dimensions.copy(buttonWidth = 400, buttonHeight = 84, minimumButtonHeight = 84))
            assertTrue(plan.sideBySide)
            val mazeWidth = area.width - plan.controlsWidth - dimensions.margin * 3
            assertTrue("Maze must retain at least half the inset-adjusted width",
                mazeWidth * 2 >= area.width)
            assertTrue(plan.headerViewportHeight + dimensions.mazeMinHeight +
                dimensions.margin * 2 <= area.height)
            assertTrue(plan.buttonWidth >= dimensions.touchTarget)
        }
    }

    @Test
    fun wrappedPrimaryCanUseMoreHeaderHeightWithoutHidingMaze() {
        val area = GameplaySafeArea(568, 272)
        val plan = GameplayLayoutPolicy.calculate(area, 1_000, 62,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84, headerMinHeight = 120))
        assertEquals(120, plan.headerViewportHeight)
        assertTrue(area.height - plan.headerViewportHeight - dimensions.margin * 2 > 0)
    }

    @Test
    fun evenAnUnboundedPrimaryCannotConsumeTheWholeMazeRegion() {
        val area = GameplaySafeArea(568, 272)
        val plan = GameplayLayoutPolicy.calculate(area, 2_000, 62,
            dimensions.copy(buttonHeight = 84, minimumButtonHeight = 84, headerMinHeight = 1_000))
        assertTrue(area.height - plan.headerViewportHeight - dimensions.margin * 2 >=
            dimensions.touchTarget)
    }
}
