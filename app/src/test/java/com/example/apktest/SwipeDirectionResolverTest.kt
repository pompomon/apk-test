package com.example.apktest

import com.example.apktest.game.core.Direction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeDirectionResolverTest {
    @Test
    fun resolve_returnsNorthForUpSwipe() {
        val direction = SwipeDirectionResolver.resolve(
            deltaX = 8f,
            deltaY = -120f,
            velocityX = 120f,
            velocityY = -1_000f,
            minDistance = 48f,
            minVelocity = 100f
        )

        assertEquals(Direction.NORTH, direction)
    }

    @Test
    fun resolve_returnsSouthForDownSwipe() {
        val direction = SwipeDirectionResolver.resolve(
            deltaX = -10f,
            deltaY = 140f,
            velocityX = -90f,
            velocityY = 1_300f,
            minDistance = 48f,
            minVelocity = 100f
        )

        assertEquals(Direction.SOUTH, direction)
    }

    @Test
    fun resolve_returnsWestForLeftSwipe() {
        val direction = SwipeDirectionResolver.resolve(
            deltaX = -180f,
            deltaY = 20f,
            velocityX = -1_200f,
            velocityY = 200f,
            minDistance = 48f,
            minVelocity = 100f
        )

        assertEquals(Direction.WEST, direction)
    }

    @Test
    fun resolve_returnsEastForRightSwipe() {
        val direction = SwipeDirectionResolver.resolve(
            deltaX = 180f,
            deltaY = -15f,
            velocityX = 1_250f,
            velocityY = -150f,
            minDistance = 48f,
            minVelocity = 100f
        )

        assertEquals(Direction.EAST, direction)
    }

    @Test
    fun resolve_returnsNullWhenBelowThresholds() {
        val direction = SwipeDirectionResolver.resolve(
            deltaX = 30f,
            deltaY = 8f,
            velocityX = 60f,
            velocityY = 20f,
            minDistance = 48f,
            minVelocity = 100f
        )

        assertNull(direction)
    }

    @Test
    fun resolve_returnsNullForDiagonalTie() {
        val direction = SwipeDirectionResolver.resolve(
            deltaX = 120f,
            deltaY = -120f,
            velocityX = 800f,
            velocityY = -800f,
            minDistance = 48f,
            minVelocity = 100f
        )

        assertNull(direction)
    }
}
