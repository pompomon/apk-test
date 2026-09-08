package com.example.apktest.ui

import android.app.Activity
import android.view.View
import android.widget.TextView
import android.widget.ToggleButton
import androidx.core.view.ViewCompat
import com.example.apktest.R

/** Presentation only: the activities still own policy selection and input enablement. */
class GameControlsPresentation(private val activity: Activity) {
    private val auto = activity.findViewById<ToggleButton>(R.id.buttonAuto)
    private val glide = activity.findViewById<ToggleButton>(R.id.buttonInertia)
    private val hint = activity.findViewById<TextView>(R.id.controlHint)

    fun update(automationAvailable: Boolean, decisionPending: Boolean = false) {
        val state = AutomationControlState.resolve(automationAvailable, decisionPending, auto.isChecked)
        val offLabel = when (state) {
            AutomationControlState.LOCKED -> R.string.auto_locked
            AutomationControlState.WAITING -> R.string.auto_waiting
            AutomationControlState.OFF, AutomationControlState.ON -> R.string.auto_off
        }
        val explanation = when (state) {
            AutomationControlState.LOCKED -> activity.getString(R.string.auto_locked_hint)
            AutomationControlState.WAITING -> activity.getString(R.string.auto_waiting_hint)
            AutomationControlState.OFF, AutomationControlState.ON -> ""
        }
        updateToggle(auto, R.string.auto_on, offLabel)
        updateToggle(glide, R.string.glide_on, R.string.glide_off)
        val autoDescription = explanation.ifEmpty {
            activity.getString(if (auto.isChecked) R.string.control_state_on else R.string.control_state_off)
        }
        ViewCompat.setStateDescription(auto, autoDescription)
        ViewCompat.setStateDescription(
            glide,
            activity.getString(if (glide.isChecked) R.string.control_state_on else R.string.control_state_off)
        )
        if (hint.text.toString() != explanation) hint.text = explanation
        hint.visibility = if (explanation.isEmpty()) View.GONE else View.VISIBLE
        auto.tooltipText = explanation.ifEmpty { activity.getString(R.string.auto_move_description) }
    }

    private fun updateToggle(toggle: ToggleButton, onRes: Int, offRes: Int) {
        val on = activity.getString(onRes)
        val off = activity.getString(offRes)
        if (toggle.textOn != on) toggle.textOn = on
        if (toggle.textOff != off) toggle.textOff = off
        val text = if (toggle.isChecked) on else off
        if (toggle.text.toString() != text) toggle.text = text
    }
}
