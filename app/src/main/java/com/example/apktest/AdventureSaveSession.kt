package com.example.apktest

import java.util.concurrent.atomic.AtomicLong

/**
 * Prevents work queued by a destroyed AdventureActivity from overwriting a
 * newer activity's decisions. Disk writes remain on the host's executor.
 */
internal class AdventureSaveSession private constructor(private val generation: Long) {
    fun write(action: () -> Boolean): Boolean = synchronized(writeLock) {
        if (currentGeneration.get() != generation) false else action()
    }

    fun close() {
        currentGeneration.compareAndSet(generation, generation + 1)
    }

    companion object {
        private val currentGeneration = AtomicLong()
        private val writeLock = Any()

        fun open(): AdventureSaveSession =
            AdventureSaveSession(currentGeneration.incrementAndGet())
    }
}
