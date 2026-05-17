package com.example.apktest.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import com.example.apktest.R

/**
 * Floating popover anchored to the in-maze hamburger button. Contains the game
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
        fun onBackToSetup()
    }

    private val statusText: TextView
    private val speedText: TextView
    private val powerUpText: TextView
    private val popup: PopupWindow

    init {
        val pad = dp(12f)
        val gap = dp(6f)

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            setBackgroundColor(ContextCompat.getColor(context, R.color.maze_menu_popover_background))
            // Reasonable minimum width so labels don't wrap awkwardly.
            minimumWidth = dp(220f)
        }

        root.addView(actionButton(R.string.pause_resume) { callbacks.onPauseResume() })
        root.addView(actionButton(R.string.restart) { callbacks.onRestart() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = gap
        })
        root.addView(actionButton(R.string.legend) { callbacks.onLegend() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = gap
        })
        root.addView(actionButton(R.string.back_to_setup) { callbacks.onBackToSetup() }.also {
            (it.layoutParams as LinearLayout.LayoutParams).topMargin = gap
        })

        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                maxOf(1, dp(1f))
            ).apply { topMargin = pad; bottomMargin = pad }
            setBackgroundColor(ContextCompat.getColor(context, R.color.maze_menu_popover_divider))
        }
        root.addView(divider)

        statusText = hudTextView(R.string.status_default, R.color.maze_status_text, 13f)
        speedText = hudTextView(R.string.speed_default, R.color.maze_speed_text, 12f)
        powerUpText = hudTextView(R.string.powerups_default, R.color.maze_speed_text, 12f).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(2f)
        }
        speedText.also { (it.layoutParams as LinearLayout.LayoutParams).topMargin = dp(2f) }

        root.addView(statusText)
        root.addView(speedText)
        root.addView(powerUpText)

        popup = PopupWindow(
            root,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            // Transparent background is required so the dismiss-on-outside-touch
            // behavior works on older Android versions.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            elevation = dp(8f).toFloat()
        }
    }

    val isShowing: Boolean
        get() = popup.isShowing

    fun setOnDismissListener(listener: () -> Unit) {
        popup.setOnDismissListener { listener() }
    }

    fun show(anchor: View) {
        if (popup.isShowing) return
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
        val button = Button(context).apply {
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

    private fun hudTextView(textRes: Int, colorRes: Int, sizeSp: Float): TextView {
        return TextView(context).apply {
            setText(textRes)
            setTextColor(ContextCompat.getColor(context, colorRes))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun dp(value: Float): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        value,
        context.resources.displayMetrics
    ).toInt()
}
