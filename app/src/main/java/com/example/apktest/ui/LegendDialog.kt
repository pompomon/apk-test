package com.example.apktest.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.apktest.R
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PowerUpType

/**
 * Builds and shows the in-game legend dialog. Rows are generated from
 * [PowerUpType.entries] and [NpcPolicyType.entries] and each row reads its
 * `label` and `description` directly from the enum, which is the single
 * source of truth (shared with the HUD and renderer). Any new enum entry
 * therefore automatically appears in the legend without additional wiring or
 * string resources.
 */
object LegendDialog {
    fun show(context: Context) {
        val dialogPadding = dp(context, 16f)
        val rowPadding = dp(context, 8f)
        val sectionSpacing = dp(context, 12f)
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

        container.addView(sectionHeader(context, R.string.legend_section_powerups, topMargin = 0))
        PowerUpType.entries.forEachIndexed { index, type ->
            val row = newRow(context, rowPadding, isFirst = index == 0)
            row.addView(powerUpIcon(context, iconSize, type))
            row.addView(rowText(context, type.label, type.description))
            container.addView(row)
        }

        container.addView(sectionHeader(context, R.string.legend_section_npcs, topMargin = sectionSpacing))
        NpcPolicyType.entries.forEachIndexed { index, type ->
            val row = newRow(context, rowPadding, isFirst = index == 0)
            row.addView(npcIcon(context, iconSize, type))
            row.addView(rowText(context, type.label, type.description))
            container.addView(row)
        }

        AlertDialog.Builder(context)
            .setTitle(R.string.legend_title)
            .setView(scroll)
            .setPositiveButton(R.string.legend_close, null)
            .show()
    }

    private fun sectionHeader(context: Context, stringRes: Int, topMargin: Int): TextView {
        return TextView(context).apply {
            setText(stringRes)
            setTypeface(typeface, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { this.topMargin = topMargin }
        }
    }

    private fun newRow(context: Context, rowPadding: Int, isFirst: Boolean): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, if (isFirst) rowPadding / 2 else rowPadding, 0, rowPadding)
        }
    }

    private fun powerUpIcon(context: Context, iconSize: Int, type: PowerUpType): PowerUpIconView {
        return PowerUpIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setPowerUpType(type)
            // The adjacent TextView already announces the label and
            // description, so exclude the icon from accessibility to avoid
            // an unlabeled focusable element / duplicate announcement.
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun npcIcon(context: Context, iconSize: Int, type: NpcPolicyType): NpcIconView {
        return NpcIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setNpcPolicyType(type)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun rowText(context: Context, label: String, description: String): TextView {
        return TextView(context).apply {
            text = label + "\n" + description
            setPadding(dp(context, 12f), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        }
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }
}
