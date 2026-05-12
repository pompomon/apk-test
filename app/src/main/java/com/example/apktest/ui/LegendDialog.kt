package com.example.apktest.ui

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.apktest.R
import com.example.apktest.game.core.PowerUpType

/**
 * Builds and shows the power-up legend dialog. Rows are generated from
 * [PowerUpType.entries] and each row reads `label` and `description` directly
 * from the enum, which is the single source of truth (shared with the HUD).
 * Any new [PowerUpType] therefore automatically appears in the legend without
 * additional wiring or string resources.
 */
object LegendDialog {
    fun show(context: Context) {
        val dialogPadding = dp(context, 16f)
        val rowPadding = dp(context, 8f)
        val iconSize = dp(context, 40f)

        val scroll = ScrollView(context).apply {
            isFillViewport = true
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dialogPadding, dialogPadding, dialogPadding, dialogPadding)
        }
        scroll.addView(
            container,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        PowerUpType.entries.forEachIndexed { index, type ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, if (index == 0) 0 else rowPadding, 0, rowPadding)
            }

            val icon = PowerUpIconView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                setPowerUpType(type)
                // The adjacent TextView already announces the label and
                // description, so exclude the icon from accessibility to avoid
                // an unlabeled focusable element / duplicate announcement.
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            row.addView(icon)

            val text = TextView(context).apply {
                text = type.label + "\n" + type.description
                setPadding(dp(context, 12f), 0, 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            row.addView(text)
            container.addView(row)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.legend_title)
            .setView(scroll)
            .setPositiveButton(R.string.legend_close, null)
            .show()
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }
}
