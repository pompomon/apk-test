package com.example.apktest.ui

import java.util.Locale

/**
 * Formats a floating-point elapsed-seconds value as a compact MM:SS string
 * for display in the Adventure status bar and summary dialogs.
 */
object AdventureTimeFormatter {
    fun format(seconds: Float): String {
        val totalSeconds = seconds.toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val secs = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, secs)
    }
}
