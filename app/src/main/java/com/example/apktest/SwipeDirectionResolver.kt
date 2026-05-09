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
        if (abs(absX - absY) <= DIAGONAL_TIE_EPSILON) return null
        if (absX > absY) {
            if (absX < minDistance || abs(velocityX) < minVelocity) return null
            return if (deltaX < 0f) Direction.WEST else Direction.EAST
        }
        if (absY < minDistance || abs(velocityY) < minVelocity) return null
        return if (deltaY < 0f) Direction.NORTH else Direction.SOUTH
    }

    // Treat near-equal axis deltas as diagonal ambiguity to avoid accidental direction picks.
    private const val DIAGONAL_TIE_EPSILON = 0.01f
}
