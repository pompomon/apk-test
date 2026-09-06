package com.example.apktest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureSnapshotLifecycleTest {
    @Test
    fun finishingStillAcceptsFinalCheckpointButNeverPresentsOrAcknowledges() {
        for (foreground in listOf(true, false)) {
            val lifecycle = AdventureSnapshotLifecycle.resolve(foreground, finishing = true, destroyed = false)
            assertTrue(lifecycle.canPersist)
            assertFalse(lifecycle.canPresent)
        }
    }

    @Test
    fun destroyedHostRejectsLateCallbacksEvenIfForegroundFlagIsStale() {
        for (foreground in listOf(true, false)) {
            for (finishing in listOf(true, false)) {
                val lifecycle = AdventureSnapshotLifecycle.resolve(foreground, finishing, destroyed = true)
                assertFalse(lifecycle.canPersist)
                assertFalse(lifecycle.canPresent)
            }
        }
    }

    @Test
    fun backgroundSaveDefersPresentationUntilForeground() {
        val background = AdventureSnapshotLifecycle.resolve(false, false, false)
        assertTrue(background.canPersist)
        assertFalse(background.canPresent)
        val foreground = AdventureSnapshotLifecycle.resolve(true, false, false)
        assertTrue(foreground.canPersist)
        assertTrue(foreground.canPresent)
    }
}
