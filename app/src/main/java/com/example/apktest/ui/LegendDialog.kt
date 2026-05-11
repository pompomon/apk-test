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
 * Builds and shows the power-up legend dialog. The contents are generated
 * programmatically from [PowerUpType.entries] so any new power-up added to the
 * enum automatically appears in the legend.
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
            }
            row.addView(icon)

            val text = TextView(context).apply {
                val labelRes = labelStringRes(type)
                val descRes = descriptionStringRes(type)
                text = context.getString(labelRes) + "\n" + context.getString(descRes)
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

    private fun labelStringRes(type: PowerUpType): Int = when (type) {
        PowerUpType.INVISIBILITY -> R.string.powerup_label_invisibility
        PowerUpType.TELEPORT -> R.string.powerup_label_teleport
        PowerUpType.SPEED_UP -> R.string.powerup_label_speed_up
        PowerUpType.FREEZE -> R.string.powerup_label_freeze
        PowerUpType.BLAST -> R.string.powerup_label_blast
        PowerUpType.GHOST_MODE -> R.string.powerup_label_ghost_mode
    }

    private fun descriptionStringRes(type: PowerUpType): Int = when (type) {
        PowerUpType.INVISIBILITY -> R.string.powerup_desc_invisibility
        PowerUpType.TELEPORT -> R.string.powerup_desc_teleport
        PowerUpType.SPEED_UP -> R.string.powerup_desc_speed_up
        PowerUpType.FREEZE -> R.string.powerup_desc_freeze
        PowerUpType.BLAST -> R.string.powerup_desc_blast
        PowerUpType.GHOST_MODE -> R.string.powerup_desc_ghost_mode
    }

    private fun dp(context: Context, value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value,
            context.resources.displayMetrics
        ).toInt()
    }
}
