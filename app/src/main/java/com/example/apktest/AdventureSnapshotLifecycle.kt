package com.example.apktest

/** Finishing suppresses presentation, not the final valid GL checkpoint. */
internal enum class AdventureSnapshotLifecycle(
    val canPersist: Boolean,
    val canPresent: Boolean,
    val requiresBlockingPersist: Boolean
) {
    FOREGROUND(true, true, false),
    BACKGROUND(true, false, false),
    FINISHING(true, false, true),
    DESTROYED(false, false, false);

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
