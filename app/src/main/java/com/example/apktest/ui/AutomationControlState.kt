package com.example.apktest.ui

enum class AutomationControlState {
    LOCKED, WAITING, OFF, ON;

    companion object {
        fun resolve(available: Boolean, decisionPending: Boolean, selected: Boolean): AutomationControlState =
            when {
                !available -> LOCKED
                decisionPending -> WAITING
                selected -> ON
                else -> OFF
            }
    }
}
