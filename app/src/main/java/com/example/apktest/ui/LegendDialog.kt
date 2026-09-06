package com.example.apktest.ui

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import androidx.appcompat.app.AlertDialog
import com.example.apktest.R
import com.example.apktest.game.core.EliteNpcModifier
import com.example.apktest.game.core.NpcPolicyType
import com.example.apktest.game.core.PowerUpType

/**
 * Builds and shows the in-game legend dialog. Rows are generated from
 * [PowerUpType.entries] and [NpcPolicyType.entries] and each enum-backed row
 * reads its `label` and `description` from the shared source of truth.
 * Elite rows are added only for modifiers present in the active Adventure maze.
 */
object LegendDialog {
    fun show(
        context: Context,
        eliteModifiers: Set<EliteNpcModifier> = emptySet()
    ): AlertDialog {
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

        container.addView(sectionHeader(context, R.string.legend_section_adventurers, topMargin = sectionSpacing))
        val adventurerRow = newRow(context, rowPadding, isFirst = true)
        adventurerRow.addView(adventurerIcon(context, iconSize))
        adventurerRow.addView(
            rowText(
                context,
                context.getString(R.string.legend_adventurer_label),
                context.getString(R.string.legend_adventurer_description)
            )
        )
        container.addView(adventurerRow)

        container.addView(sectionHeader(context, R.string.legend_section_npcs, topMargin = sectionSpacing))
        NpcPolicyType.entries.forEachIndexed { index, type ->
            val row = newRow(context, rowPadding, isFirst = index == 0)
            row.addView(npcIcon(context, iconSize, type))
            row.addView(rowText(context, type.label, type.description))
            container.addView(row)
        }

        if (eliteModifiers.isNotEmpty()) {
            container.addView(
                sectionHeader(context, R.string.adventure_elite_summary_title, topMargin = sectionSpacing)
            )
            var isFirst = true
            for (modifier in EliteNpcModifier.entries) {
                if (modifier !in eliteModifiers) continue
                val row = newRow(context, rowPadding, isFirst)
                isFirst = false
                val policy = NpcPolicyType.entries.first { modifier.supports(it) }
                row.addView(npcIcon(context, iconSize, policy, modifier))
                row.addView(
                    rowText(
                        context,
                        context.getString(EliteNpcResources.nameFor(modifier)),
                        context.getString(EliteNpcResources.descriptionFor(modifier))
                    )
                )
                container.addView(row)
            }
        }

        return AlertDialog.Builder(context)
            .setTitle(R.string.legend_title)
            .setView(scroll)
            .setPositiveButton(R.string.legend_close, null)
            .show()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun textSnapshotForTesting(dialog: AlertDialog): List<String> {
        val texts = mutableListOf<String>()
        dialog.window?.decorView?.let { collectText(it, texts) }
        return texts
    }

    private fun collectText(view: View, texts: MutableList<String>) {
        when (view) {
            is TextView -> texts.add(view.text.toString())
            is ViewGroup -> {
                for (index in 0 until view.childCount) {
                    collectText(view.getChildAt(index), texts)
                }
            }
        }
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

    private fun npcIcon(
        context: Context,
        iconSize: Int,
        type: NpcPolicyType,
        eliteModifier: EliteNpcModifier? = null
    ): NpcIconView {
        return NpcIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
            setNpcPolicyType(type, eliteModifier)
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
    }

    private fun adventurerIcon(context: Context, iconSize: Int): AdventurerIconView {
        return AdventurerIconView(context).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
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
