package com.example.apktest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdventureSaveSessionTest {
    @Test
    fun recreatedActivityRejectsOldQueuedSaveAndClear() {
        val old = AdventureSaveSession.open()
        val current = AdventureSaveSession.open()
        var obsoleteWriteRan = false
        assertFalse(old.write { obsoleteWriteRan = true; true })
        assertFalse(obsoleteWriteRan)
        assertTrue(current.write { true })
        old.close()
        assertTrue(current.write { true })
        current.close()
    }

    @Test
    fun closedActivityRejectsCallbacksAndFailedWritesStayFailed() {
        val session = AdventureSaveSession.open()
        assertFalse(session.write { false })
        session.close()
        var callbackRan = false
        assertFalse(session.write { callbackRan = true; true })
        assertFalse(callbackRan)
    }
}
