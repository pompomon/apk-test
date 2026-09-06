package com.example.apktest

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

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

    @Test
    fun recreatedActivityWaitsForInFlightWriteBeforeOpeningSession() {
        val old = AdventureSaveSession.open()
        val writeStarted = CountDownLatch(1)
        val releaseWrite = CountDownLatch(1)
        val openStarted = CountDownLatch(1)
        val openFinished = CountDownLatch(1)
        var writeFinished = false
        lateinit var current: AdventureSaveSession
        val writer = thread {
            old.write {
                writeStarted.countDown()
                releaseWrite.await()
                writeFinished = true
                true
            }
        }
        assertTrue(writeStarted.await(1, TimeUnit.SECONDS))
        val opener = thread {
            openStarted.countDown()
            current = AdventureSaveSession.open()
            openFinished.countDown()
        }
        assertTrue(openStarted.await(1, TimeUnit.SECONDS))
        try {
            assertFalse(openFinished.await(100, TimeUnit.MILLISECONDS))
        } finally {
            releaseWrite.countDown()
        }
        writer.join()
        opener.join()
        assertTrue(writeFinished)
        assertTrue(current.write { true })
        current.close()
    }
}
