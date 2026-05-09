package com.example.apktest

import com.example.apktest.game.core.Direction
import kotlin.math.abs

object SwipeDirectionResolver {
    fun resolve(
        deltaX: Float,
        deltaY: Float,
        velocityX: Float,
        velocityY: Float,
        minDistance: Float,
        minVelocity: Float
    ): Direction? {
        val absX = abs(deltaX)
        val absY = abs(deltaY)
        val dominantDelta = maxOf(absX, absY)
        if (dominantDelta == 0f) return null
        val axisDifferenceRatio = abs(absX - absY) / dominantDelta
        if (axisDifferenceRatio <= DIAGONAL_TIE_RATIO_THRESHOLD) return null
        if (absX > absY) {
            if (absX < minDistance || abs(velocityX) < minVelocity) return null
            return if (deltaX < 0f) Direction.WEST else Direction.EAST
        }
        if (absY < minDistance || abs(velocityY) < minVelocity) return null
        return if (deltaY < 0f) Direction.NORTH else Direction.SOUTH
    }

    // Reject swipes when x/y deltas are within 10% of each other (diagonal ambiguity).
    private const val DIAGONAL_TIE_RATIO_THRESHOLD = 0.10f
}
