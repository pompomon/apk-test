package com.example.apktest.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AutomationControlStateTest {
    @Test
    fun lockedNeverLooksSelectedEvenWhenASelectionWasRestored() {
        for (pending in listOf(false, true)) {
            for (selected in listOf(false, true)) {
                assertEquals(
                    AutomationControlState.LOCKED,
                    AutomationControlState.resolve(false, pending, selected)
                )
            }
        }
    }

    @Test
    fun aPendingDecisionIsDistinctFromLockedAndUnchecked() {
        for (selected in listOf(false, true)) {
            assertEquals(
                AutomationControlState.WAITING,
                AutomationControlState.resolve(true, true, selected)
            )
        }
        assertEquals(AutomationControlState.OFF, AutomationControlState.resolve(true, false, false))
        assertEquals(AutomationControlState.ON, AutomationControlState.resolve(true, false, true))
    }
}
