package com.example.apktest.ui

import androidx.annotation.StringRes
import com.example.apktest.R
import com.example.apktest.game.core.EliteNpcModifier

object EliteNpcResources {
    @StringRes
    fun nameFor(modifier: EliteNpcModifier): Int = when (modifier) {
        EliteNpcModifier.TRACKER -> R.string.adventure_elite_tracker_name
    }

    @StringRes
    fun descriptionFor(modifier: EliteNpcModifier): Int = when (modifier) {
        EliteNpcModifier.TRACKER -> R.string.adventure_elite_tracker_description
    }
}
