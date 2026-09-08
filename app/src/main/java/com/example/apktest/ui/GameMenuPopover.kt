package com.example.apktest.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.example.apktest.R

/**
 * Floating popover anchored to the reserved header's menu button. Contains the game
 * actions (Pause/Resume, Restart, Legend, Back to Setup) plus the live HUD text
 * (status, speed, power-ups). The HUD views are kept internal to this class;
 * the host activity updates the displayed values via [updateHud] and can choose
 * to call it only while the popover is visible.
 */
class GameMenuPopover(
    private val context: Context,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onPauseResume()
        fun onRestart()
        fun onLegend()
        /**
         * Pause the game, persist a snapshot, and return to the start menu.
         * Replaces the older "Back to Setup" action; the host should call
         * `togglePause()` only if the engine is currently RUNNING, capture
         * the snapshot, save it, then finish the activity.
         */
        fun onPauseAndExit()
    }

    private val statusText: TextView
    private val speedText: TextView
    private val powerUpText: TextView
    private val popup: PopupWindow
    private val visibleFrame = Rect()

    init {
        val pad = dimension(R.dimen.maze_menu_content_padding)
        val gap = dimension(R.dimen.maze_menu_content_gap)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ContextCompat.getColor(context, R.color.maze_menu_popover_background))
        }

        root.addView(actionButton(R.string.pause_resume) { callbacks.onPauseResume() })
        root.addView(actionButton(R.string.restart) { callbacks.onRestart() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = gap
        })
        root.addView(actionButton(R.string.legend) { callbacks.onLegend() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = gap
        })
        root.addView(actionButton(R.string.pause_and_exit) { callbacks.onPauseAndExit() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = gap
        })

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dimension(R.dimen.maze_menu_divider_height).coerceAtLeast(1)
            ).apply { topMargin = pad; bottomMargin = pad }
            setBackgroundColor(ContextCompat.getColor(context, R.color.maze_menu_popover_divider))
        }
        root.addView(divider)

        statusText = hudTextView(R.string.status_default, R.color.maze_status_text, R.dimen.maze_menu_status_text_size)
        speedText = hudTextView(R.string.speed_default, R.color.maze_speed_text, R.dimen.maze_menu_detail_text_size)
        powerUpText = hudTextView(R.string.powerups_default, R.color.maze_speed_text, R.dimen.maze_menu_detail_text_size).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dimension(R.dimen.maze_menu_text_gap)
        }
        speedText.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dimension(R.dimen.maze_menu_text_gap) }

        root.addView(statusText)
        root.addView(speedText)
        root.addView(powerUpText)

        val scroll = ScrollView(context).apply {
            addView(root)
        }
        popup = PopupWindow(
            scroll,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            // Transparent background is required so the dismiss-on-outside-touch
            // behavior works on older Android versions.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dimension(R.dimen.maze_menu_elevation).toFloat()
        }
    }

    val isShowing: Boolean
        get() = popup.isShowing

    fun setOnDismissListener(listener: () -> Unit) {
        popup.setOnDismissListener { listener() }
    }

    fun show(anchor: View) {
        if (popup.isShowing) return
        anchor.getWindowVisibleDisplayFrame(visibleFrame)
        val width = minOf(dimension(R.dimen.maze_menu_content_width), visibleFrame.width()).coerceAtLeast(1)
        val height = popup.getMaxAvailableHeight(anchor).coerceAtLeast(1)
        popup.contentView.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        )
        popup.width = width
        popup.height = popup.contentView.measuredHeight.coerceAtMost(height)
        // Anchor below the hamburger button, right-aligned to it.
        popup.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    fun dismiss() {
        if (popup.isShowing) popup.dismiss()
    }

    fun updateHud(status: CharSequence, speed: CharSequence, powerUps: CharSequence) {
        statusText.text = status
        speedText.text = speed
        powerUpText.text = powerUps
    }

    /**
     * Returns a snapshot of all TextView/Button text currently present in the popover content
     * view, collected in traversal order from the view hierarchy.
     */
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun textSnapshotForTesting(): List<String> {
        val texts = mutableListOf<String>()
        collectText(rootView = popup.contentView, out = texts)
        return texts
    }

    private fun collectText(rootView: View, out: MutableList<String>) {
        when (rootView) {
            is TextView -> out.add(rootView.text.toString())
            is ViewGroup -> {
                for (index in 0 until rootView.childCount) {
                    collectText(rootView.getChildAt(index), out)
                }
            }
        }
    }

    private fun actionButton(textRes: Int, onClick: () -> Unit): Button {
        val button = AppCompatButton(context, null, R.attr.mazeMenuActionButtonStyle).apply {
            setText(textRes)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener {
                onClick()
                dismiss()
            }
        }
        return button
    }

    private fun hudTextView(textRes: Int, colorRes: Int, sizeRes: Int): TextView {
        return TextView(context).apply {
            setText(textRes)
            setTextColor(ContextCompat.getColor(context, colorRes))
            setTextSize(TypedValue.COMPLEX_UNIT_PX, context.resources.getDimension(sizeRes))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dimension(resource: Int): Int = context.resources.getDimensionPixelSize(resource)
}
