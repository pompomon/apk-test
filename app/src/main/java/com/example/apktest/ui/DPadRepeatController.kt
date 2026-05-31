package com.example.apktest.ui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import com.example.apktest.game.core.Direction

class DPadRepeatController(
    private val move: (Direction) -> Unit,
    private val handler: Handler = Handler(Looper.getMainLooper())
) {
    private var repeatingDirection: Direction? = null
    private val repeatRunnable: Runnable = object : Runnable {
        override fun run() {
            val direction = repeatingDirection ?: return
            move(direction)
            handler.postDelayed(this, DPAD_REPEAT_INTERVAL_MS)
        }
    }

    fun bind(button: View, direction: Direction) {
        var suppressNextClickFromTouch = false
        button.setOnClickListener {
            if (suppressNextClickFromTouch) {
                suppressNextClickFromTouch = false
            } else {
                move(direction)
            }
        }
        button.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    suppressNextClickFromTouch = true
                    if (repeatingDirection != direction) {
                        stop()
                        start(direction)
                    }
                    false
                }
                MotionEvent.ACTION_UP -> {
                    stop()
                    handler.postDelayed(
                        { suppressNextClickFromTouch = false },
                        DPAD_CLICK_SUPPRESSION_RESET_DELAY_MS
                    )
                    false
                }
                MotionEvent.ACTION_CANCEL -> {
                    stop()
                    suppressNextClickFromTouch = false
                    false
                }
                else -> false
            }
        }
    }

    fun stop() {
        repeatingDirection = null
        handler.removeCallbacks(repeatRunnable)
    }

    private fun start(direction: Direction) {
        handler.removeCallbacks(repeatRunnable)
        repeatingDirection = direction
        move(direction)
        handler.postDelayed(repeatRunnable, DPAD_INITIAL_REPEAT_DELAY_MS)
    }

    companion object {
        // Shorter than Android key-repeat defaults so held D-pad input feels responsive in-game
        // while still leaving a small delay after the initial press to distinguish taps.
        private const val DPAD_INITIAL_REPEAT_DELAY_MS = 180L
        private const val DPAD_REPEAT_INTERVAL_MS = 90L
        private const val DPAD_CLICK_SUPPRESSION_RESET_DELAY_MS = 50L
    }
}
