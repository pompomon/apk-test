package com.example.apktest.ui

import android.app.Activity
import android.graphics.Rect
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.example.apktest.R
import com.example.apktest.SwipeDirectionResolver
import com.example.apktest.game.core.Direction

class GameInputController(
    private val activity: Activity,
    private val moveUntilBlocked: (Direction) -> Unit,
    private val onSwipeResolved: () -> Unit = {}
) {
    private val dPadRepeatController = DPadRepeatController(move = moveUntilBlocked)
    private var swipeGestureDetector: GestureDetector? = null
    private var swipeGestureActive: Boolean = false
    private val gameHostWindowRect = Rect()
    private val overlayWindowRect = Rect()
    private val viewWindowLocation = IntArray(2)

    fun bindDirectionalControls() {
        dPadRepeatController.bind(activity.findViewById(R.id.buttonUp), Direction.NORTH)
        dPadRepeatController.bind(activity.findViewById(R.id.buttonDown), Direction.SOUTH)
        dPadRepeatController.bind(activity.findViewById(R.id.buttonLeft), Direction.WEST)
        dPadRepeatController.bind(activity.findViewById(R.id.buttonRight), Direction.EAST)
    }

    fun setupSwipeControls() {
        val viewConfig = ViewConfiguration.get(activity)
        val minDistance = (viewConfig.scaledTouchSlop * SWIPE_DISTANCE_TOUCH_SLOP_MULTIPLIER).toFloat()
        val minVelocity = viewConfig.scaledMinimumFlingVelocity.toFloat()
        swipeGestureDetector = GestureDetector(
            activity,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onFling(
                    e1: MotionEvent?,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val start = e1 ?: return false
                    val direction = SwipeDirectionResolver.resolve(
                        deltaX = e2.x - start.x,
                        deltaY = e2.y - start.y,
                        velocityX = velocityX,
                        velocityY = velocityY,
                        minDistance = minDistance,
                        minVelocity = minVelocity
                    ) ?: return false
                    onSwipeResolved()
                    moveUntilBlocked(direction)
                    return true
                }
            }
        )
    }

    fun stop() {
        dPadRepeatController.stop()
    }

    internal fun feedSwipeEventForTesting(event: MotionEvent) {
        swipeGestureDetector?.onTouchEvent(event)
    }

    internal fun isPointInsideGameHost(x: Float, y: Float): Boolean {
        val gameHost = activity.findViewById<View>(R.id.fragmentGameHost) ?: return false
        if (!fillWindowRect(gameHost, gameHostWindowRect)) return false
        val truncatedX = x.toInt()
        val truncatedY = y.toInt()
        if (!gameHostWindowRect.contains(truncatedX, truncatedY)) return false
        if (isInsideOverlay(R.id.buttonMenu, truncatedX, truncatedY)) return false
        if (isInsideOverlay(R.id.bottomControls, truncatedX, truncatedY)) return false
        return true
    }

    fun onDispatchTouchEvent(ev: MotionEvent) {
        val detector = swipeGestureDetector ?: return
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeGestureActive = isPointInsideGameHost(ev.x, ev.y)
                if (swipeGestureActive) {
                    detector.onTouchEvent(ev)
                }
            }
            MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_UP -> {
                if (swipeGestureActive) {
                    detector.onTouchEvent(ev)
                }
                swipeGestureActive = false
            }
            else -> {
                if (swipeGestureActive) {
                    detector.onTouchEvent(ev)
                }
            }
        }
    }

    private fun isInsideOverlay(viewId: Int, x: Int, y: Int): Boolean {
        val overlay = activity.findViewById<View>(viewId) ?: return false
        if (!fillWindowRect(overlay, overlayWindowRect)) return false
        return overlayWindowRect.contains(x, y)
    }

    private fun fillWindowRect(view: View, out: Rect): Boolean {
        if (view.width <= 0 || view.height <= 0) return false
        view.getLocationInWindow(viewWindowLocation)
        val left = viewWindowLocation[0]
        val top = viewWindowLocation[1]
        out.set(left, top, left + view.width, top + view.height)
        return true
    }

    companion object {
        private const val SWIPE_DISTANCE_TOUCH_SLOP_MULTIPLIER = 4
    }
}
