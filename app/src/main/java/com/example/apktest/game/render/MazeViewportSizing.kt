package com.example.apktest.game.render

internal object MazeViewportSizing {
    const val WALL_THICKNESS = 0.18f
    const val OUTER_MARGIN = 0.30f

    // ExtendViewport may extend either axis, but must never crop the half-wall
    // outside the cell bounds on its limiting axis.
    fun minimumExtent(cells: Int): Float = cells + 2f * OUTER_MARGIN
}
