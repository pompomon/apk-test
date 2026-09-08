package com.example.apktest.game.render

import com.example.apktest.game.core.DifficultyPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class MazeViewportSizingTest {
    @Test
    fun limitingExtentIncludesBothOuterHalfWallsAndBreathingRoom() {
        for (cells in listOf(1, 2, 12, 16, 18, 24, 28)) {
            val extent = MazeViewportSizing.minimumExtent(cells)
            val origin = (extent - cells) / 2f
            assertTrue("Left/bottom wall must not be clipped", origin > MazeViewportSizing.WALL_THICKNESS / 2f)
            assertTrue("Right/top wall must not be clipped", origin + cells + MazeViewportSizing.WALL_THICKNESS / 2f < extent)
            assertEquals(MazeViewportSizing.OUTER_MARGIN, origin, 0.00001f)
            assertTrue("Margin should not shrink the maze excessively", origin in 0.2f..0.5f)
        }
    }

    @Test
    fun extendedPortraitLandscapeAndSquareWorldsRetainMarginForAllPresets() {
        for (preset in DifficultyPresets.all) {
            val minWidth = MazeViewportSizing.minimumExtent(preset.mazeWidth)
            val minHeight = MazeViewportSizing.minimumExtent(preset.mazeHeight)
            for ((screenWidth, screenHeight) in listOf(320 to 640, 640 to 320, 600 to 600, 800 to 1280)) {
                val worldUnitsPerPixel = max(minWidth / screenWidth, minHeight / screenHeight)
                val worldWidth = worldUnitsPerPixel * screenWidth
                val worldHeight = worldUnitsPerPixel * screenHeight
                assertTrue((worldWidth - preset.mazeWidth) / 2f >= MazeViewportSizing.OUTER_MARGIN - 0.00001f)
                assertTrue((worldHeight - preset.mazeHeight) / 2f >= MazeViewportSizing.OUTER_MARGIN - 0.00001f)
            }
        }
    }
}
