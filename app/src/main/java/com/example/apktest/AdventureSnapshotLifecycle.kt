package com.example.apktest

/** Finishing suppresses presentation, not the final valid GL checkpoint. */
internal enum class AdventureSnapshotLifecycle(val canPersist: Boolean, val canPresent: Boolean) {
    FOREGROUND(true, true),
    BACKGROUND(true, false),
    FINISHING(true, false),
    DESTROYED(false, false);

    companion object {
        fun resolve(foreground: Boolean, finishing: Boolean, destroyed: Boolean): AdventureSnapshotLifecycle =
            when {
                destroyed -> DESTROYED
                finishing -> FINISHING
                foreground -> FOREGROUND
                else -> BACKGROUND
            }
    }
}
