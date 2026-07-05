package com.example.apktest

import android.content.Context
import java.util.Locale

/**
 * Result returned from [AdventureBestStore.recordCompletedRun].
 *
 * @property previousBestSeconds The previous best time for this difficulty,
 *   or `null` if this was the first completed run.
 * @property currentTimeSeconds The time just recorded.
 * @property isNewBest `true` if [currentTimeSeconds] is now the best time.
 */
data class BestTimeResult(
    val previousBestSeconds: Float?,
    val currentTimeSeconds: Float,
    val isNewBest: Boolean
)

/**
 * Persists the best completed Adventure run time per difficulty using
 * [android.content.SharedPreferences]. Active run stats live in
 * [AdventureStateStore]; this store is kept separate so personal bests
 * survive across multiple Adventure runs and are never cleared by
 * [AdventureStateStore.clear].
 *
 * Only completed (won) runs update the best time — lost runs must never
 * call [recordCompletedRun].
 */
class AdventureBestStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Returns the best completed time in seconds for [difficultyName],
     * or `null` if no run has been completed for that difficulty yet.
     */
    fun bestTimeSeconds(difficultyName: String): Float? {
        return readStoredBest(prefKey(difficultyName))
    }

    /**
     * Reads the stored best time for [key], or `null` if it is absent or
     * corrupt. SharedPreferences values can become type-corrupted
     * across restores/downgrades, in which case [android.content.SharedPreferences.getFloat]
     * throws [ClassCastException]; treat that as absent/corrupt rather than
     * crashing. A stored value that is non-finite (NaN/Infinity) or negative
     * is also treated as corrupt/absent, since it would break best-time
     * comparisons and prevent recording any future best.
     */
    private fun readStoredBest(key: String): Float? {
        if (!prefs.contains(key)) return null
        val stored = try {
            prefs.getFloat(key, Float.MAX_VALUE)
        } catch (_: ClassCastException) {
            // Type-corrupted value: drop the key so subsequent reads don't
            // keep paying the cost of throwing, letting the preference
            // self-heal immediately.
            prefs.edit().remove(key).apply()
            return null
        }
        if (!stored.isFinite() || stored < 0f) {
            // Non-finite/negative stored value is unusable; remove it so it
            // is treated as absent and a future best can be recorded.
            prefs.edit().remove(key).apply()
            return null
        }
        return stored
    }

    /**
     * Records a completed run time for [difficultyName]. Replaces the
     * stored best only if [totalElapsedSeconds] is strictly faster (lower).
     * The first completed run always becomes the best.
     *
     * Returns a [BestTimeResult] describing what changed.
     */
    fun recordCompletedRun(
        difficultyName: String,
        totalElapsedSeconds: Float
    ): BestTimeResult {
        val key = prefKey(difficultyName)
        // Sanitize the incoming time so non-finite/negative values never
        // get persisted (they would poison formatting and future
        // comparisons).
        val sanitizedTime =
            if (totalElapsedSeconds.isFinite()) totalElapsedSeconds.coerceAtLeast(0f) else 0f
        val previous = readStoredBest(key)
        val isNewBest = previous == null || sanitizedTime < previous
        if (isNewBest) {
            // Commit synchronously: this write happens at run-complete, so
            // persisting immediately keeps the best reliable even if the
            // process is killed right after finishing.
            prefs.edit().putFloat(key, sanitizedTime).commit()
        }
        return BestTimeResult(
            previousBestSeconds = previous,
            currentTimeSeconds = sanitizedTime,
            isNewBest = isNewBest
        )
    }

    companion object {
        internal const val PREFS_NAME = "adventure_bests"

        /** Safe SharedPreferences key derived from the difficulty name. */
        internal fun prefKey(difficultyName: String): String =
            "best_time_${difficultyName.lowercase(Locale.US)}"
    }
}
